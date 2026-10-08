# RideComm

Group intercom app for bike riders (Android). Riders join a ride with a 6-letter code and talk to each
other over mobile internet through their helmet headset.

Working on the code (or picking up in a new Claude chat)? Start with [CLAUDE.md](CLAUDE.md),
[HANDOFF.md](HANDOFF.md) (status, what's next, how to ask) and [PROTOCOL.md](PROTOCOL.md) (messages
between phones).

## Status

| Step | Feature | State |
|---|---|---|
| 1 | Group voice call (4–6 riders), background audio, Bluetooth headset, auto-reconnect | ✅ Built |
| 2 | Shared music from one "DJ" phone, music ducks when someone talks | ✅ Built |
| 3 | Glove-friendly floating slide panel over other apps | ✅ Built |
| 4 | Quick votes (break, fuel, food…) with spoken results | ✅ Built |
| 5 | SOS / caution stop with alarm, location and SMS fallback | ✅ Built |

## How it works

- **Voice:** [LiveKit](https://livekit.io) (WebRTC). Opus at ~24 kbps with DTX (near zero data while silent) and
  RED (redundancy for patchy highway networks). WebRTC noise suppression, echo cancellation and auto gain are on.
- **Wind noise filter:** a noise gate on the mic (before it's sent) lets sound through only while you speak.
  Each 10 ms is split into a voice band (300–3400 Hz) and a low band (< 250 Hz); wind and engine roar are mostly
  low, speech mostly in the voice band, so the gate opens only for voice-like sound, holds 0.5 s so word endings
  aren't cut and fades in/out over 5 ms. Silence also lets DTX send almost nothing, saving data, and wind no
  longer makes you show as "talking" or duck everyone's music. Settings → Wind noise filter: Off / Low / Medium
  (default) / High; applies immediately, even mid-ride.
  Low / Medium / High are presets; **Fine-tune and test** adds four sliders, each re-running the test recording live:
  voice sensitivity (how quiet a voice still gets through), wind rejection (how strictly deep rumble counts as wind),
  keep sending after you stop (0.15–1.5 s) and noise reduction (from a little quieter to complete silence, which
  keeps some natural background). Changed values show as "Custom".
  Settings → Wind noise filter → **Fine-tune and test** shows it at work. Off a ride, record 8 seconds (talk, then blow on
  the mic) and see two waveforms, "your mic heard" (green = voice sent, amber = noise blocked) and "the group
  hears", with playback of both, and totals for voice sent, noise blocked and how much quieter the noise got.
  Switching Low/Medium/High re-runs the same recording instantly. On a ride it shows live meters (mic, sent,
  voice level against the sensitivity line, wind rumble) and totals for the ride, and the mic button's label
  says "wind blocked" while it's filtering.
- **Background:** a foreground service keeps the call alive with the screen off or Maps open; the notification has
  Mute and Leave buttons.
- **Dead zones:** if the connection drops, the app keeps retrying (up to every 15 s) until the rider leaves.
- **Catch-up after a drop:** when you're back online you hear "Back online" and only what still needs you: a vote
  that's still open ("While you were offline, Rahul wants a break", and you can still vote) and any SOS that's still
  active. Finished votes and old quick messages aren't replayed. An SOS you sent without internet goes to the group
  the moment you're back.
- **Data saver** (Settings: Off / Auto (default) / Always): voice drops from 24 to 12 kbps (half the data, still
  clear; switched on the live call without a gap) and shared songs aren't downloaded. Auto does this only after 5 s
  of a poor connection and goes back to normal after 30 s of a good one; the paused song then downloads by itself.
  Songs are the big data user (4–8 MB each); voice is about 10 MB an hour.
- **Phone calls:** a regular call doesn't end the ride. Your ride mic goes off for the call, the group hears
  "Rahul is on a phone call" (and sees it on the card), and your mic comes back when the call ends.
- **One rider dropping never affects the others:** everyone connects to the ride server separately, so the rest
  keep talking while one rider reconnects.
- **Rider alerts:** spoken heads-ups so nobody has to look: "Rahul joined the ride", "Rahul dropped out" (signal
  lost; a rider who taps Leave says bye first, so the others hear "left the ride" instead), "Rahul is back", and
  "Amit's phone battery is at 15 percent" (once each at 20%, 10% and 5%, not while charging). Phones share their
  battery level only when it changes by 5%. Low batteries (30% or less) also show on rider cards. Switchable in
  Settings.
- **Voice commands (Android 13+):** say "RideComm" and then a command: break / fuel / food (start a vote), yes / no,
  slow down, wait for me, mute / unmute, next song, music off / on, who's here, battery, speed, where is everyone, regroup here, SOS, cancel. The call
  already owns the mic, so each burst of your speech (found with the same voice detector as the wind filter, with
  0.3 s of lead-in, at most 5 s) is cut from the call's own mic stream, converted to 16 kHz and piped to the phone's
  speech recogniser (on-device when available, nudged towards these words). Only speech that starts with
  "RideComm" (or how recognisers tend to spell it: "ride comm", "ride calm"…) does anything, and each command is
  confirmed by voice. The group still hears you say it. Switchable in Settings.
- **Speed, distance and ride updates:** my own GPS (nothing shared) gives speed now, distance and riding time on the
  ride screen. GPS drift while parked and impossible jumps are filtered out. **Speed alert** (Settings, off by
  default) says "Speed 96" in the headset after 3 s over your limit (30–160 km/h), at most every 30 s.
  **Ride updates** speak "42.3 kilometres. 1 hour 10 minutes riding. Average 56." every 15/30 min or 10/25 km.
  "RideComm, speed" gives the same on demand.
- **Group map** (Settings → Group map, **off by default**): with it on, your position is shared with the ride
  every 5 s while moving (15 s when slow or saving data). The map (OpenStreetMap, no API key; darkened on the
  phone to match the app, with a light option for bright sun) shows every rider's photo, name and direction,
  you, and how far each one is ("Rahul · 2.5 km behind", tap to jump to them). You hear when someone falls 1 km
  behind and when they're back. **Regroup point:** long-press the map (or "Regroup at my location", or say
  "RideComm, regroup here"), pick a name (Petrol pump, Dhaba, Toll plaza…) and everyone hears "Amit set a regroup
  point: Petrol pump, 5.4 kilometres ahead", sees it on the map and the ride screen, and gets one-tap Navigate
  (Google Maps directions). Riders hear "Regroup point in 800 metres", arrivals are counted ("2 of 4 here"),
  "Everyone is at the regroup point" is announced, and the point clears itself 2 minutes later. Riders who join or
  reconnect get the current point; iPhone riders in the browser get it with a Navigate button too. "RideComm,
  where is everyone" reads out each rider's distance. With the setting off, nothing is shared or announced.
  Map tiles are cached (up to 60 MB), so roads seen before load without data.
- **Quick rejoin:** if a ride ends without the rider leaving (app closed, phone restarted or out of battery), the
  home screen offers "Back to ride CODE?" with a one-tap Rejoin for 12 hours. The join card also lists up to three
  recent ride codes from the past week ("Ride again"); one tap joins that ride again.
- **Audio route:** Bluetooth headset → wired headset → speaker → earpiece, switched automatically.
- **Music:** the DJ picks songs from their phone. Each song file is sent once to every rider over LiveKit data
  streams (the next song is sent ahead while the current one plays), and every phone plays it locally, kept in
  sync by small "playing at position X" messages. Because playback is local, music keeps going through dead zones
  and each phone turns it down to 25% within ~0.2 s whenever anyone talks, then fades it back ~1 s after.

- **Spotify / YouTube Music / any music app:** a call normally takes full audio focus, which pauses other
  music apps. RideComm keeps them playing instead and turns them down (Android audio-focus ducking) whenever
  anyone talks or an announcement plays. For the same songs on every phone use Spotify Jam. Can be switched back
  to "pause other music" in Settings.
- **Floating ride button:** during a ride, a round button sits on the screen edge over any app (needs
  "Display over other apps"). Tap it to open the options, then tap an option (or anywhere in its direction);
  tap empty space to close. Sliding straight off the button and lifting also works. Options are picked by
  direction, not exact position, so it works with gloves; the phone buzzes when an option is highlighted and a
  voice confirms the action. Long-press to drag the button; it snaps to the nearest edge. A short slide reaches the inner ring (mute, music, Yes/No when
  a vote is open); a long slide reaches the outer ring (start a vote, quick messages).
- **Votes & quick messages:** ☕ Break? ⛽ Fuel? 🍔 Food? start a group vote: everyone hears "Rahul wants a break.
  Slide to vote." and answers from the floating button, the notification or the ride screen. Every phone tallies
  the ballots itself and announces the result. A vote ends early once either side has a majority of the riders,
  otherwise after everyone voted or 45 s (then decided by the votes cast). 🐢 Slow down and ✋ Wait for me are
  announced to everyone without a vote. Music turns down while announcements play.
- **Group tracking (beta, off by default):** each phone shares its GPS position with the ride every 15 s. Rider cards show distance and whether they're ahead or behind; tap to open their spot in Maps.
  When a rider drifts more than 1 km away everyone hears "Rahul is 1.2 kilometers behind" (again at each further
  kilometre), and "Rahul is back with the group" once they're within 500 m.
- **Crash detection:** during a ride the accelerometer watches for a hard impact (over 4 g) followed by the
  phone lying still for 10 s; with GPS it also requires that the rider was moving and has stopped. It then starts
  a 15-second SOS countdown ("Crash detected", tap anywhere to cancel); others hear "Rahul may have crashed".
  A cancelled alarm mutes detection for a minute. Can be turned off in Settings.
- **Profile:** name and photo are set once (first run, then Settings). Photos are shared with the ride and shown
  on rider cards and, while another app is open, as small semi-transparent bubbles of whoever is talking at the
  top-left corner (they ignore touches, so Maps underneath stays usable).
- **Floating button:** long-press and drag it to move it; drag it onto the ✕ at the bottom to hide it until you
  next open RideComm (or turn it off for good in Settings).
- **Headset button:** the helmet headset's play/pause button controls the ride: 1 press mute/unmute, 2 presses
  next song (DJ) or music off/on, 3 presses SOS countdown; each confirmed by voice. Android routes the button to the
  app that last played media, so while Spotify plays it may control Spotify instead. Switchable in Settings.
- **SOS:** 🚨 SOS (ride screen or outer ring of the floating button) starts a 5-second countdown — tap anywhere
  to cancel. Then every rider gets a siren in the headset (music muted), a spoken "S O S. Rahul needs help.
  1.2 kilometers away.", a full-screen red alert over other apps and a high-priority notification, both with
  Open map. The sender's last known location goes out at once, followed by a fresh GPS fix. Without internet,
  the Messages app opens with the SOS text (with a maps link) to the emergency numbers set in Settings, ready to
  send with one tap (sending silently needs SEND_SMS, which Play Protect blocks for sideloaded apps). "I'm OK" stops everyone's
  alarm.
- **Emergency info** (Settings): blood group, allergies / conditions and an emergency contact. Kept on the phone
  and sent only with my SOS ("Send with my SOS" can turn that off). While my SOS is on, my screen shows a big
  "For anyone helping" card (name, blood group, medical notes) with Call contact and Call 112 buttons, for a
  passer-by or medic holding my phone. The group's SOS alert shows the same details and a Call button, and the
  no-internet SOS text message includes them.
- **Hazard alerts** (Settings, on by default): tap "Mark a road hazard" (or say "RideComm, pothole" / police /
  speed breaker / slippery / accident / cows…) to mark Pothole, Speed breaker, Slippery road, Police check,
  Accident or Animal on the road where you are. Riders ahead of it stay quiet; riders behind hear "Amit marked:
  Pothole, 1.2 kilometers ahead" and then "Pothole in 300 meters" once, when it's 40–500 m ahead on their road
  (within 45° of their direction). Hazards show on the ride screen (nearest first, with distance and age) and on
  the group map, and expire by themselves (police after 30 min, animals 20, accidents an hour, the rest 2 hours).
  Marking the same kind at the same spot (80 m) confirms it instead of adding another. Only the hazard's spot is
  shared, so it works without the Group map.
- **Lead & sweep:** tap any rider to make them the lead (rides first) or the sweep (rides last); everyone sees
  LEAD / SWEEP badges, on the map too. With the Group map on, positions are compared along the lead's or
  sweep's direction of travel: a rider 300 m ahead of the lead hears "You're ahead of the lead, Amit" (and the lead
  hears "Rahul is ahead of you"); 500 m behind the sweep, the rider and the sweep hear it. Each alert is spoken
  once until that rider is back in place. "Lead & sweep alerts" is switchable in Settings.
- **Push to talk** (Settings → Talk mode: Open mic / Push to talk): the mic sends silence until you hold the
  big button (green while talking). A quick tap keeps it open hands-free until the next tap (closes itself after
  2 minutes); the headset button and the floating button start and stop talking too, with "Talk" / "Over"
  spoken. Switching happens instantly, without re-connecting the mic. Voice commands still work.
- **Rider volume:** tap a rider to set how loud you hear them (20–200%, only on your phone, remembered for the
  next ride) or mute them for yourself, which also stops downloading their voice.
- **Night mode** (Settings: Off / Auto / On): the whole app turns dim red (greyscale tinted red, so bright and
  dark stay apart) with the screen dimmed, and spoken alerts play at about half volume. Auto works out sunset
  and sunrise from the phone's location (or 6:30 pm–6 am without one).

## iPhone and browser riders

iPhones can't install the APK, so there's a browser version at
`https://saquelain.github.io/intercomm-app/ride/?code=CODE` (`docs/ride/index.html`). Invite links open it
automatically on iPhone ("Join the ride"); on Android they still open the app. It joins the same LiveKit ride and
speaks the app's message formats, so web and app riders ride together: talk and listen, the rider list (with
photos, mic and phone-call status, low batteries), Break/Fuel/Food votes, quick messages, SOS with location both
ways and "I'm OK", spoken alerts, catch-up on open votes and active SOS after a drop, and automatic rejoin. Shared music isn't played in the
browser (it tells the DJ not to send it the song files). On iPhone, keep the page open: Safari pauses the mic when
the screen locks or another app is in front.

The gear button opens **Settings**, saved in the browser and changeable any time, even mid-ride:

- **Profile photo**: picked from the phone, cropped to a small square and shown to everyone, on the app too.
- **Group map** (off by default): shares the iPhone's location with the app riders and shows everyone on a live
  map (Leaflet + OpenStreetMap, dark or light). Long-press the map, or tap "Regroup at my location", to set a
  regroup point. Riders get "Navigate" directions, arrival counts and the "Everyone is at the regroup point" alert.
  Switching it off stops sharing straight away.
- **Spoken alerts**, **Vibration** and **Keep screen on**, each on by default.
- **Hazard alerts** and **Lead & sweep alerts** (on by default), the same as in the app: mark a hazard with one
  tap, hear "Pothole in 300 meters", and tap a rider to make them lead or sweep. To warn about hazards the page
  follows the iPhone's location only on the iPhone itself, and only once someone has marked one.
- **Talk mode:** Open mic or Push to talk (hold the mic button, or tap to lock it open).
- **Night mode:** Off / Auto / On, dim red screen and quieter alerts.
- **Emergency info:** blood group, allergies and a contact, sent with your SOS and shown on your screen for helpers.
- Tapping a rider also offers **Mute for me** and, in browsers that allow it, a volume slider (iPhone Safari
  can't turn a single voice down, so there it's mute only).

The ride screen's mic button glows with your voice level, so you can see the mic is working.

## One-time setup

1. Create a free project at [cloud.livekit.io](https://cloud.livekit.io).
2. In the project's **Settings**, switch on **Development token server** and copy the **Token server ID**.
3. Either:
   - add it as a GitHub Actions secret named `LIVEKIT_TOKEN_SERVER_ID` (repo → Settings → Secrets and variables →
     Actions), so every APK has it built in, **or**
   - paste it in the app under **Settings** on each phone.

> The development token server is fine for a private group but lets anyone with the ID join rooms.
> Before sharing the app publicly we'll switch to our own token endpoint.

## Private ride server (optional, not set up)

The LiveKit development token server lets anyone who has its ID join any ride. The private ride server
(`server/token-worker`, a Cloudflare Worker) only gives a ride pass to riders who send the **group key**, and only
for one ride room (no listing other rides).

One-time setup:

1. Create a free account at [dash.cloudflare.com](https://dash.cloudflare.com). Note your **Account ID**
   (right sidebar of Workers & Pages). Create an **API token** with the "Edit Cloudflare Workers" template.
2. In LiveKit Cloud → Settings → **Keys**, note the **WebSocket URL**, **API key** and **API secret**.
3. Pick a group key (a long passphrase) to share privately with your riders.
4. Add GitHub repository secrets (Settings → Secrets and variables → Actions): `CLOUDFLARE_API_TOKEN`,
   `CLOUDFLARE_ACCOUNT_ID`, `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`, `GROUP_SECRET`.
5. Run **Actions → Deploy ride server → Run workflow**. The log prints the worker address
   (`https://ridecomm-token.<you>.workers.dev`). Add it as the `RIDE_SERVER_URL` secret and rebuild the APK
   (or paste it in the app's Settings).
6. Each rider enters the group key in Settings. Then switch off the development token server in LiveKit Cloud.

## Getting the APK

Invite links (`https://saquelain.github.io/intercomm-app/join/?code=…`) need GitHub Pages: Settings → Pages →
Deploy from a branch → this branch, `/docs` folder.

Every push builds an APK on GitHub Actions. Download `RideComm.apk` from the
[`latest` release](../../releases/tag/latest) on your phone and install it (allow "Install unknown apps" for your
browser once). Builds are signed with the same test key, so new versions install over old ones.

- `RideComm.apk` (~16 MB): 64-bit phones, i.e. nearly every phone from 2017 on. Shrunk with R8, which cut the
  app's code from 34 MB to 4 MB; most of what's left is LiveKit's WebRTC voice engine (12 MB).
- `RideComm-32bit.apk` (~11 MB): only if Android says RideComm.apk "isn't compatible" with your phone.
- `RideComm-unshrunk.apk` (~27 MB): the same app without R8 shrinking. Install it over RideComm.apk if the
  shrunk build crashes or misbehaves, and share the crash report so it can be fixed.

The ride screen shows how much mobile data RideComm has used since the ride started (voice, music, photos).

## Design

Glassmorphism on a dark night background: translucent "frosted" cards with a light sheen and thin
bright rims over soft violet, pink, orange and cyan glows; big rounded, glove-friendly controls.
Icons are [Material Symbols Rounded](https://fonts.google.com/icons) (Apache 2.0) and the typeface is
[Outfit](https://fonts.google.com/specimen/Outfit) (SIL OFL 1.1).

Screenshots of the main screens render on the JVM, no phone needed:

```sh
./gradlew recordRoborazziRelease   # PNGs in app/screenshots/
```

## Building locally

```sh
./gradlew assembleRelease -PlivekitTokenServerId=<your id>
# APKs: app/build/outputs/apk/release/app-arm64-v8a-release.apk (and -armeabi-v7a-)
```

Requires JDK 17+ and the Android SDK (platform 35).
