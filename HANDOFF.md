# RideComm handoff: where we are, how to carry on

Last updated 8 Oct 2026, after CI build #36. Read `CLAUDE.md` first (how to build, test and work with
the owner); this file is the project's story so far and what's next.

## Starting a new chat (for the owner)

Open a new Claude Code session on `saquelain/intercomm-app`. Claude reads `CLAUDE.md` by itself; point
it at this file too. Copy one of these and fill in the blanks:

**Next feature**
> Read CLAUDE.md and HANDOFF.md. Continue RideComm from where we left off. Next I want: ____.
> Build it in the app and the web page, with a switch in Settings, then give me the APK and web links.

**Bug or change from a ride**
> Read CLAUDE.md and HANDOFF.md. On my phone (Samsung, Android __ / iPhone web), when I ____ I expected
> ____ but got ____. Screenshot attached.

**Ideas**
> Read CLAUDE.md and HANDOFF.md. What are the most useful features to add next? Give me options to pick.

Tips that worked well in the first chat:
- Attach a screenshot whenever something looks wrong; say which phone and whether it's the app or
  the web page.
- Short replies are fine: "Working fine. Next feature?", "3, 4, 6", "keep it off by default".
- Ask for "the APK link" or "the web url" any time.
- To pick up mid-feature in a new chat, say what was being built and paste Claude's last message.

## Links

- APK: https://github.com/saquelain/intercomm-app/releases/download/latest/RideComm.apk
  (`RideComm-32bit.apk` for old phones, `RideComm-unshrunk.apk` fallback). Each push makes a new build.
- Web ride for iPhone: https://saquelain.github.io/intercomm-app/ride/ (`?code=ABCDEF`)
- Invite links: `https://saquelain.github.io/intercomm-app/join/?code=ABCDEF` (app on Android, web on iPhone)
- LiveKit Cloud sandbox token server ID: `ridecomm-xnnn94` (built into the APK via the
  `LIVEKIT_TOKEN_SERVER_ID` secret; the web page uses it directly).

## What's built

Everything below is in both the Android app and the web page unless it says "app only". "Owner-tested"
means the owner reported it working on a real phone; the rest passed unit tests, screenshots and the
fake-LiveKit web tests but has not been confirmed on a ride yet.

| Feature | Where | Status |
|---|---|---|
| Group voice call over LiveKit (Opus 24 kbps, DTX, RED), auto-reconnect through dead zones | app + web | owner-tested |
| Glass UI, ride code, invite links, quick rejoin, recent rides | app (+ web join) | owner-tested |
| Shared music: DJ sends songs, every phone plays locally, ducks for voices; Spotify keeps playing ducked | app only (web just shows "X is playing") | owner-tested |
| Floating ride button over other apps (slide menu, drag onto ✕ to hide), who's-talking overlay | app only | owner-tested |
| Votes (Break / Fuel / Food) and quick messages (Slow down, Wait for me) | app + web | owner-tested |
| SOS (countdown, siren, location, I'm OK), SMS fallback without internet | app (+ web without SMS) | owner-tested |
| Crash detection → SOS countdown | app only | built, not seen in a real crash |
| Helmet headset button (1 mute, 2 music, 3 SOS) | app only | built |
| Wind noise gate with presets, fine-tune sliders and a visual before/after test | app only | owner-tested |
| Rider alerts (joined, dropped, back, low battery, on a phone call) | app + web | owner-tested |
| Voice commands ("RideComm, break / pothole / where is everyone …"), Android 13+ | app only | owner-tested |
| Speed alert, ride updates, speed/distance/time row | app only | owner-tested |
| Data saver (Off / Auto / Always), data used on screen | app only | built |
| Catch-up after a drop (only open votes and active SOS) | app + web | owner-tested |
| Phone calls don't end the ride (mic off during the call) | app only | built |
| Profile photos | app + web | owner-tested |
| Group map (off by default): live map, distances, separation alerts, regroup points with Navigate | app + web | owner-tested (screenshot from a ride) |
| My own photo on the map ("You") | app + web | build #36, new |
| Hazard alerts: mark pothole / speed breaker / slippery / police / accident / animal; "Pothole in 300 meters" | app + web | built, needs a road test |
| Lead & sweep: badges; lead/sweep alerts with Group map on | app + web | badges seen working; alerts need a road test |
| Push to talk (hold, or tap to lock; headset and floating button toggle) | app + web | built |
| Per-rider volume 20–200% and Mute for me | app (web: mute; slider not on iPhone) | built |
| Night mode Off / Auto / On (red screen, quieter voice; Auto from sunset) | app + web | built |
| Emergency info (blood group, medical, contact) with SOS + helper card with Call 112 | app + web | built |

Chronology (git log): step 1 voice call → music → floating button → votes → SOS → glass redesign →
group tracking → Spotify ducking → crash detection, photos → invite links → private ride server →
headset button → wind gate → R8 + split APKs → rejoin → rider alerts → voice commands → visual wind test →
fine-tune + drag-to-hide → speed & trip → data saver, catch-up, phone calls → web version for iPhone →
group map + regroup → web photo/map/settings → hazards, lead & sweep, PTT, volume, night, emergency info
(app, then web) → own photo on the map.

## Ideas not built yet (from the last "what next" list)

- **Ride summary card**: route on a map, distance, time, top/average speed, stops; share as an image.
- **Fuel range reminder**: set the bike's range; warn at 80% and offer a Fuel vote.
- **Ride planner**: start time, meeting point and planned stops shared before the ride, with reminders.
- **Rider chat log**: scrollable history of the ride's messages, votes and alerts.
- **Private ride server** (`server/token-worker`, deploy workflow ready): locks rides to the group with a
  group key. Important before sharing the app widely, because the sandbox token server lets anyone with
  the ID join. Needs the owner's Cloudflare and LiveKit keys as repo secrets.
- Smaller: a "Hazard" option on the floating button, voice commands on the web page, emergency info on
  the lock screen, night mode for the floating overlays, Play Store release (needs a signing setup and
  privacy policy).

Ideas looked at and dropped: Bluetooth or Wi-Fi Direct voice instead of the internet (10–100 m range,
phones can't do a mesh group call; voice is only ~10 MB an hour anyway).

## Known limits

- iPhone: Safari pauses the mic and location when the screen locks or another app is in front; keep the
  page open ("Keep screen on" is on by default). Safari can't change one rider's volume (mute works).
- The sandbox token server is open to anyone with its ID (see Private ride server above).
- SOS by SMS opens Messages with the text ready; the rider taps Send (silent SMS needs SEND_SMS, which
  Play Protect blocks for sideloaded apps).
- The web page only follows location for hazards after someone marks one (to avoid a location prompt
  for everyone), and only on the phone itself.
- Nothing can be tested against real LiveKit from the cloud container; every feature is tested with unit
  tests, screenshots and the fake-LiveKit web tests (`tools/webtest/`), then by the owner on a ride.

## Setting up a fresh cloud container

1. Gradle mirror (Maven Central rate-limits the container): create
   `~/.gradle/init.d/mirror.init.gradle.kts` with
   ```kotlin
   // Local-only: route Maven Central through Google's mirror (repo.maven.apache.org rate-limits this container).
   val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
   beforeSettings {
       pluginManagement.repositories { maven(mirror) }
       dependencyResolutionManagement.repositories { maven(mirror) }
   }
   allprojects {
       buildscript.repositories { maven(mirror) }
   }
   ```
2. `./gradlew testDebugUnitTest` should pass (about 150 tests).
3. Web tests: see `tools/webtest/README.md`.
4. GitHub access goes through the GitHub MCP tools (no `gh` CLI in the container).
