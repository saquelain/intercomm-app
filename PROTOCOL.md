# RideComm messages

Every phone in a ride (Android app or web page) talks to the others through LiveKit **text streams**
(JSON, one object per message) and **byte streams** (files), each on a topic. The app and the web page
must send and understand exactly the same messages, so riders on both can ride together. When you add
or change a message, change both `app/` and `docs/ride/index.html`, and update this file.

- A ride is the LiveKit room `ride-<CODE>` (6 characters from `ABCDEFGHJKMNPQRSTUVWXYZ23456789`).
- A rider's **identity** is a stable per-install id (app: `Prefs.deviceId`, a UUID; web: `web-<uuid>` in
  localStorage `rc-web-id`), so rejoining replaces the old connection and per-rider settings survive.
- `from` below is the sender's identity, given by LiveKit (not in the JSON).
- Times (`at`) are wall-clock epoch milliseconds. Positions are WGS84 degrees.
- Messages with no `to` go to everyone; replies to one rider use LiveKit `destinationIdentities`.
- Unknown fields are ignored, so fields can be added safely. Unknown `t` values are ignored.

## Token server

`POST https://cloud-api.livekit.io/api/v2/sandbox/connection-details` with header
`X-Sandbox-ID: ridecomm-xnnn94` and body
`{ "room_name": "ride-ABCDEF", "participant_name": "Asha", "participant_identity": "<id>" }` →
`{ "server_url", "participant_token" }` (snake_case). The private server in `server/token-worker` uses
the same format plus header `X-RideComm-Key: <group key>`.

## `rc-vote`: votes and quick messages

| `t` | Fields | Meaning |
|---|---|---|
| `start` | `id`, `kind` (`BREAK` / `FUEL` / `FOOD`), `name`, `at`, `riders` | a vote opens (45 s) |
| `cast` | `id`, `name`, `yes` | a ballot; every phone tallies and announces the result itself |
| `say` | `msg` (`SLOW_DOWN` / `WAIT`), `name` | quick message; old ones with `late: true` are ignored |
| `catchup` | `since` | "I was offline since…": others reply with the still-open vote only |

## `rc-sos`

| `t` | Fields | Meaning |
|---|---|---|
| `sos` | `name`, `at` (start of this SOS), `crash`, `lat`?, `lon`?, `info`? | SOS; a second `sos` with the same `at` updates the position (no second alarm) |
| `ok` | `name` | I'm OK: stop alarms |
| `catchup` | `since` | sent after a drop; riders with an active SOS re-send it to that rider |

`info` (emergency info, only if the rider allows it): `{ "blood": "O+", "medical": "…", "contact": "Ammi", "phone": "+91…" }`,
every field optional; receivers cut `blood`/`contact`/`phone` to 40 and `medical` to 200 characters.

## `rc-rider`: rider status

| `t` | Fields | Meaning |
|---|---|---|
| `bye` | – | leaving on purpose (so others say "left", not "dropped out") |
| `battery` | `level` (0–100), `charging` | sent when it changes by 5%, and to each rider who joins |
| `call` | `on` | on / off a phone call (also sent to new joiners) |

## `rc-loc`: group map (only with Group map on)

| `t` | Fields | Meaning |
|---|---|---|
| none or `pos` | `name`, `lat`, `lon`, `at`, `spd`? (km/h), `hdg`? (degrees) | my position, every 5 s moving / 15 s slow; dropped after 2 min silence |
| `regroup` | `id`, `lat`, `lon`, `label`, `name`, `by`, `at`, `arrived` (ids) | the regroup point (newest `at` wins, then highest `id`) |
| `regroup-clear` | `id`, `name` | cleared |
| `arrived` | `id`, `on` | I'm at / left the regroup point (within 150 m; leave at 400 m) |
| `sync` | – | asks for the current regroup point (the setter, or the lowest id present, answers) |

## `rc-hazard`: road hazards (only with Hazard alerts on)

| `t` | Fields | Meaning |
|---|---|---|
| `hazard` | `id`, `kind`, `lat`, `lon`, `by`, `name`, `at` | marked (or re-confirmed: same `id`, newer `at`) |
| `hazards` | `list`: array of `hazard` objects | sent to a joiner / in reply to `sync`: each rider sends only the hazards they marked |
| `hazard-clear` | `id` | removed |
| `sync` | – | asks everyone for their hazards |

`kind`: `POTHOLE` (kept 2 h), `SPEED_BREAKER` (2 h), `SLIPPERY` (2 h), `POLICE` (30 min), `ACCIDENT` (1 h),
`ANIMAL` (20 min). Receivers warn once at 40–500 m when it's within 45° of their direction of travel.

## `rc-role`: lead & sweep

| `t` | Fields | Meaning |
|---|---|---|
| `roles` | `lead`?, `sweep`? (identities), `at`, `by`, `name` | the current roles; newest `at` wins, ties by highest `by` |
| `sync` | – | asks for the roles (the setter, or the lowest id present, answers) |

## `rc-whisper`: talk to one rider

| `t` | Fields | Meaning |
|---|---|---|
| `whisper` | `to`, `toName`, `name`, `on`, `at` | `on: true` every 2 s while I talk only to `to`; `on: false` when I let go |

Sent to everyone. Every phone except `to`'s sets the sender's voice to silent while it's on, then 0.7 s more
(the last words are still arriving); without a repeat for 5 s it ends by itself. The sender's mic opens 0.4 s
after the first message. Phones always honour others' whispers, whatever their own setting.

## `rc-dest`: shared destination (only with Shared destination on)

| `t` | Fields | Meaning |
|---|---|---|
| `dest` | `id`, `lat`, `lon`, `label`, `name`, `by`, `at` | where the group is heading (newest `at` wins, then highest `id`) |
| `dest-clear` | `id`, `name` | cleared |
| `sync` | – | asks for it (the setter, or the lowest id present, answers; also sent to joiners) |

## `rc-home`: home safe

| `t` | Fields | Meaning |
|---|---|---|
| `home` | `name` | I got home |

A rider who already left can join for a moment as `<their id>~home` to send `home`; identities ending in
`~home` are never shown as riders or announced as joining or leaving, and their `home` counts for `<id>`.

## Private ride server and invite links

With rides locked to the group, invite links carry the group key after the hash:
`…/join/?code=ABCDEF#k=<url-encoded key>`. The invite page passes it on to the app (`ridecomm://join/ABCDEF?k=…`)
or the web page (`…/ride/?code=ABCDEF#k=…`), which save it. The server answers CORS preflights so the web page
can use it.

## `rc-music` (text) and `rc-song` (bytes): shared music

| `t` | Fields | Meaning |
|---|---|---|
| `now` | `id`, `title`, `dj`, `playing`, `pos` (ms), `at` | what's playing and where, for sync |
| `stop` | – | music stopped |
| `sync` | – | asks the DJ for `now` |
| `need` | `id` | "send me this song file" |
| `saving` | `on` | "don't send me song files" (data saver; the web page always sends `on: true`) |

Song files go on byte-stream topic `rc-song` (app only).

## `rc-avatar` (bytes): profile photo

A JPEG (about 192×192, centre-cropped) sent as a byte stream with `mimeType: image/jpeg` to everyone on
connect and to each rider who joins.

## Family watching (watch page)

Family at home open `https://saquelain.github.io/intercomm-app/watch/?code=ABCDEF` (plus `#k=<key>` for rides
locked to the group). The page joins the ride as identity `watch-<uuid>` (localStorage `rc-watch-id`) with the
name the family member typed (default "Family"), never publishes a mic and subscribes to no audio. On connect
it sends `rc-loc {t:"sync"}`, `rc-dest {t:"sync"}` and `rc-sos {t:"catchup", since: 0}`.

- Identities starting with `watch-` are **not riders**: never listed, counted, announced, asked to vote, sent
  `onRiderJoined` data, or considered for "the lowest id present answers". Riders show them as
  "Family watching: Ammi".
- **Each rider decides** with Settings → "Family can watch" (off by default). When it's off and a watcher is
  in the ride, that rider's own `rc-loc` positions and `rc-sos` `sos` messages go only to the riders (LiveKit
  `destinationIdentities`; nothing is sent if there are no riders). When it's on, they go to everyone.
  Everything else (regroup, destination, `ok`, votes) is sent as before.

## Ride plans (in invite links)

A plan travels in the invite link's hash, so it never reaches a server:
`…/join/?code=ABCDEF#p=<plan>` (with `&k=<key>` when locked). `<plan>` is base64url (no padding) of the
UTF-8 JSON

`{ "v": 1, "title": "Sunday Lonavala ride", "at": <start, epoch ms>, "meet": <place>, "stops": [<place>…], "dest": <place>?, "by": "Asha" }`

where a place is `{ "name": "Shell pump", "lat": 18.52, "lon": 73.85 }`. `title` and names are cut to 60
characters, at most 5 stops; unknown fields are ignored. The invite page shows the plan, offers "Add to
calendar" (an .ics file with a reminder an hour before) and passes it on: app `ridecomm://join/ABCDEF?p=…&k=…`,
web page `…/ride/?code=ABCDEF#p=…&k=…`. Each phone keeps its upcoming plans until 12 hours after the start.

## Ride history

Kept only on each phone (app: SQLite `rides.db`; web: localStorage `rc-web-rides`, newest 30). Routes are
stored as Google encoded polylines (5 decimals). Nothing is sent to anyone unless the rider shares the
summary picture.

## `rc-fuel`: low fuel (only with Fuel & food finder on)

| `t` | Fields | Meaning |
|---|---|---|
| `low` | `name`, `on` | I'm low on fuel (`on: true`; riders hear "Asha is low on fuel") or I've filled up (`on: false`) |

Each phone finds petrol pumps itself; nothing about places is sent to the group.

## Places ahead and rain (no messages; same rules in app and web)

- **Search**: Google Places (New) Nearby Search when the Maps key allows it (`gas_station`;
  `restaurant`+`cafe`; `car_repair`), otherwise OpenStreetMap Overpass (`amenity=fuel`;
  `amenity~restaurant|fast_food|cafe|food_court`; `shop~car_repair|motorcycle|motorcycle_repair|tyres`), tried on
  overpass-api.de, overpass.kumi.systems, maps.mail.ru in turn. Radius 20 km (food 10 km). Google: one search
  around me (3 km) plus one centred 9 km ahead (9 km), ranked by distance, 20 results each.
- **Direction of travel**: GPS bearing while moving ≥ 10 km/h (kept 5 min after stopping), else the bearing to
  the shared destination, else none ("nearest", any direction).
- **Ahead**: a place is ahead when it's within 40° of the direction of travel, or within 400 m and 90°.
  Ranked by `along + 2 × across` (metres along the direction, plus twice the sideways distance).
  "On your left/right" when it's 30 m or more to the side and under 2 km away.
- **Low fuel**: picks the best pump ahead; says it ("Nearest petrol pump ahead: Indian Oil, 6 kilometres"),
  then "Petrol pump in 2 kilometres" and "Petrol pump in 500 metres, on your left" (with a buzz). A pump
  behind you (more than 100° off and over 150 m away) is skipped for the next. Searches again after 8 km or
  when nothing is ahead (at most once a minute). Ends by itself after 90 s stopped within 120 m of a pump,
  or with "Got fuel".
- **Rain**: every 15 minutes while riding, Open-Meteo for here and for 25 km ahead (or the destination if
  closer), coordinates rounded to 0.01°: `minutely_15=precipitation&hourly=precipitation_probability`. Rain is
  expected when a 15-minute step in the next 2 hours has ≥ 0.3 mm and that hour's probability is ≥ 40 %.
  Not said if it's already raining here; the same alert isn't repeated within 45 minutes.

## Points & badges (Settings → "Points & badges", on by default)

Each phone keeps its own **score book**, one entry per ride (app: `score.json` in the app's files; web:
localStorage `rc-web-score`, newest 1000). It is separate from Ride history: deleting rides keeps the points.
An entry is kept when the ride is (300 m or 5 minutes) and holds plain facts; points are always worked out
from the facts with the rules below, so app and web agree:

`{ "id": "<ride id>", "at": <start ms>, "km": 42.3, "min": <minutes moving>, "others": <other riders seen>,
"names": ["Amit", …up to 8], "breaks": <stops of 10 min or more>, "hz": <hazards I marked>, "lead": true,
"sweep": false, "home": true }`

| Points | For |
|---|---|
| 1 per km | distance (whole km) |
| 10 per other rider, up to 5 | riding together (rides of 5 km or more) |
| 15 per break, up to 3 | stops of 10 minutes or more, on rides with an hour or more of riding |
| 10 per hazard, up to 5 | hazards I marked for the group (a new spot, not re-marking one) |
| 25 | leading the group, 25 for riding sweep (rides of 5 km or more) |
| 15 | "I'm home safe" (in the ride, at home by itself, or a later check-in for my last ride if it started within 24 h) |

Never for speed. **Levels** by all-time points: Rookie 0, Rider 200, Road Buddy 600, Explorer 1,500, Road
Captain 3,000, Road King 6,000, Legend 12,000.

**Badges** (all-time): First ride (1 ride), 10 rides, 50 rides, Century (100 km in one ride), Long haul (300 km
in one ride), 1,000 / 5,000 / 10,000 km club (total), Pack ride (5 riders or more: 4 others), Hazard spotter
(10 hazards), Road leader (lead on 5 rides), Sweep hero (sweep on 5 rides), Home safe (10 check-ins), Smart
rider (a break on 10 rides of an hour or more), Early bird (a ride started between 4 and 6 am), Every week
(rides of 5 km or more in 4 weeks in a row, weeks start on Monday). A badge's date is the ride that earned it.

**This year**: rides, km, riding hours, points, longest ride, km per month, the riders ridden with most, and
the week streak (weeks in a row up to this one or last week with a ride of 5 km or more).

### `rc-score`: points in the ride (only with Points & badges on)

| `t` | Fields | Meaning |
|---|---|---|
| `score` | `name`, `level` (0–6), `ride` (points this ride so far), `year` (points this year, this ride included) | My points |

Sent to everyone on joining (and after a drop), to a rider who joins, and every 2 minutes when `ride` changed.
Riders show a "Ride points" leaderboard of the riders present. A phone with the setting off sends nothing and
shows nothing.

## My garage (Settings → "My garage", on by default; no messages)

Kept only on each phone (app: Prefs `garage`; web: localStorage `rc-web-garage`), one JSON object:

`{ "bikes": [ { "id": "…", "name": "Classic 350", "reg": "MH12AB1234", "odo": 12000, "added": 480.5,
"items": [ { "id": "…", "name": "Oil change", "km": 3000, "months": 6, "lastKm": 12000, "lastAt": <ms> } ],
"insurance": <ms or 0>, "puc": <ms or 0> } ], "current": "<bike id>", "licence": <ms or 0> }`

- **Odometer** = `odo` (what the rider typed) + `added` (km ridden with RideComm since, from my own GPS; each ride
  of 300 m or more adds its distance to the current bike when it ends). Typing a new odometer sets `added` to 0.
- **A new bike** gets Oil change (3,000 km or 6 months), Chain clean & lube (600 km), Air filter (10,000 km) and
  General service (5,000 km or 12 months), counted from when the bike was added. "Done" sets `lastKm` to the
  odometer and `lastAt` to now. `km` or `months` 0 means not by that measure.
- **Service status**: km left = `lastKm + km − odometer`, days left to `lastAt + months`. Due when either is ≤ 0;
  soon when km left ≤ 10 % of `km` (at least 100 km) or days left ≤ 14. "Oil change due", "Oil change in 240 km",
  "Oil change in 12 days" (whichever comes first).
- **Documents** (insurance and PUC per bike, licence per rider): expired when the date has passed, soon within 30
  days. "Insurance expired 3 days ago", "PUC ends in 12 days" / "ends tomorrow" / "ends today".
- **Reminders**: starting a ride says the first thing that's due or expired ("Reminder: oil change is due on your
  Classic 350"). The app also notifies at 9 am 30 days, 7 days and on the day a document ends; the web page offers
  "Add to calendar" for a document instead.

## Ride Wrapped (Settings → "Ride Wrapped", on by default; no messages)

A year in review made on the phone from the Points & badges score book (so it needs Points & badges on) and, for
the route pictures, the ride history. The year shown is this year, or last year until 15 January. It opens from
the Points & badges page ("Your 2026 Wrapped") and, from 1 December to 15 January, from a card on the home / join
screen. It needs at least one ride that year. Slides, tap to go on:

1. **Distance**: total km, compared: "That's like riding Mumbai to Goa 1.5 times" using the longest of Mumbai–Pune
   150, Delhi–Jaipur 280, Delhi–Manali 540, Mumbai–Goa 590, Delhi–Leh 1,000, Kashmir–Kanyakumari 3,700 km that the
   total covers at least once (else "N % of the way from Mumbai to Pune"), and "X % of the way around the Earth"
   (40,075 km, one decimal under 10 %).
2. **Rides and time**: rides, hours riding (moving minutes / 60, rounded down), the favourite day (most rides by
   weekday; ties go to the earlier day from Monday) and the usual start ("around 6 am": the most common start hour).
3. **Longest ride**: km, date, who came (names), and its route when the history still has it (same start ± 1 min).
4. **Best month**: the month with the most km, with the 12-month bars.
5. **Your crew**: how many different riders, and the top 3 by rides together.
6. **Badges and points**: badges earned that year, points that year, the level now.
7. **Rider type**, the first that fits: Long hauler (average 150 km a ride or more), Early bird (30 % of rides start
   before 7 am), Road captain (lead on 30 % of rides), Guardian (sweep rides + hazards marked ≥ 30 % of rides),
   Pack rider (3 other riders a ride on average or more), Weekend warrior (70 % of rides on Saturday or Sunday),
   else Explorer.
8. **Every route**: the year's routes from the history as small drawings (skipped when there are none).
9. **Share**: a 1080 × 1920 picture with the year's numbers, rider type and routes.
