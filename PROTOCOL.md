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
