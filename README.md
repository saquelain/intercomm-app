# RideComm

Group intercom app for bike riders (Android). Riders join a ride with a 6-letter code and talk to each
other over mobile internet through their helmet headset.

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
- **Background:** a foreground service keeps the call alive with the screen off or Maps open; the notification has
  Mute and Leave buttons.
- **Dead zones:** if the connection drops, the app keeps retrying (up to every 15 s) until the rider leaves.
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
- **Group tracking:** each phone shares its GPS position with the ride every 15 s (can be turned off in
  Settings). Rider cards show distance and whether they're ahead or behind; tap to open their spot in Maps.
  When a rider drifts more than 1 km away everyone hears "Rahul is 1.2 kilometers behind" (again at each further
  kilometre), and "Rahul is back with the group" once they're within 500 m.
- **SOS:** 🚨 SOS (ride screen or outer ring of the floating button) starts a 5-second countdown — tap anywhere
  to cancel. Then every rider gets a siren in the headset (music muted), a spoken "S O S. Rahul needs help.
  1.2 kilometers away.", a full-screen red alert over other apps and a high-priority notification, both with
  Open map. The sender's last known location goes out at once, followed by a fresh GPS fix. Without internet,
  the Messages app opens with the SOS text (with a maps link) to the emergency numbers set in Settings, ready to
  send with one tap (sending silently needs SEND_SMS, which Play Protect blocks for sideloaded apps). "I'm OK" stops everyone's
  alarm.

## One-time setup

1. Create a free project at [cloud.livekit.io](https://cloud.livekit.io).
2. In the project's **Settings**, switch on **Development token server** and copy the **Token server ID**.
3. Either:
   - add it as a GitHub Actions secret named `LIVEKIT_TOKEN_SERVER_ID` (repo → Settings → Secrets and variables →
     Actions), so every APK has it built in, **or**
   - paste it in the app under **Settings** on each phone.

> The development token server is fine for a private group but lets anyone with the ID join rooms.
> Before sharing the app publicly we'll switch to our own token endpoint.

## Getting the APK

Every push builds an APK on GitHub Actions. Download `RideComm.apk` from the
[`latest` release](../../releases/tag/latest) on your phone and install it (allow "Install unknown apps" for your
browser once). Builds are signed with the same test key, so new versions install over old ones.

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
# APK: app/build/outputs/apk/release/app-release.apk
```

Requires JDK 17+ and the Android SDK (platform 35).
