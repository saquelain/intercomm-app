# Putting RideComm on the Play Store

Everything the app side needs is ready: it targets Android 16 (API 36, required for new apps since
31 August 2026), CI builds a Play bundle (`.aab`) signed with your private upload key, the privacy
policy is live at https://saquelain.github.io/intercomm-app/privacy/ (after publishing the web), and the
pictures are in `design/play/`. What's left is done by the owner in Google's and GitHub's websites.

## 1. Google Play developer account (one time, about $25)

Sign up at https://play.google.com/console with the Google account that should own the app. Google
checks your identity (a few days). New personal accounts must run a **closed test with at least
12 testers for 14 days** before the app can go public: your riding group is perfect for this.

## 2. Upload key (keeps updates yours)

The upload key proves updates come from you. Keep it private and backed up; if it's ever lost, Play
support can reset it (Play App Signing keeps the real app key safe at Google).

Make one (or ask Claude to make it and send you the file):

```
keytool -genkeypair -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Then add four **repository secrets** (GitHub → repo → Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | the file as base64 (`base64 -w0 upload.jks`) |
| `UPLOAD_KEYSTORE_PASSWORD` | the keystore password |
| `UPLOAD_KEY_ALIAS` | `upload` |
| `UPLOAD_KEY_PASSWORD` | the key password |

From the next push, each CI run has a download **RideComm-play-N** (on the run's page under
Artifacts) holding `RideComm-play-N.aab`. It's not put on the public release page.

## 3. Create the app in Play Console

- App name **RideComm**, default language English (India), App, Free.
- **App signing**: let Google manage it (Play App Signing) and upload the first `.aab` to a testing track.
- **Google Maps**: in Google Cloud → Credentials → the Android key, add a second SHA-1: Play Console →
  Test and release → App integrity → **App signing key certificate SHA-1** (package `com.ridecomm.app`).
  Without it, the map is blank in the Play version.
- Phones with the sideloaded APK must uninstall it once before installing from Play (different signature).

## 4. Store listing (copy and paste)

**Short description (80):** Group intercom for bike riders: talk, group map, SOS and ride history.

**Full description:**

> RideComm turns your group's phones into a bike intercom. Everyone talks hands-free over the internet,
> with a wind-noise filter that only sends your voice, and music that ducks when someone speaks.
>
> • Group voice for 4–6 riders, with automatic reconnect through dead zones
> • Push to talk or open mic, talk to just one rider, per-rider volume
> • Votes for a break, fuel or food, and quick "Slow down" / "Wait for me" messages
> • Group map with every rider, regroup points, lead & sweep alerts
> • Shared destination with one Navigate button, rain alerts, low-fuel guide to the next petrol pump ahead
> • Hazard alerts: mark a pothole and riders behind hear "Pothole in 300 metres"
> • SOS with your location and emergency info, crash detection, "Home safe" check-in
> • Ride planner with reminders, ride history with your route and a picture to share
> • Family can follow the ride on a live map (only if you allow it)
> • iPhone riders join from the browser, no app needed
>
> No accounts, no ads. Your location is only shared when you switch a feature on.

**Graphics:** `design/play/icon_512.png` (app icon), `design/play/feature_graphic_1024x500.jpg`,
and phone screenshots `design/play/*.jpg` (1080 × 2160).

**Category:** Maps & Navigation (or Communication). **Contact email:** yours. **Privacy policy:**
https://saquelain.github.io/intercomm-app/privacy/

## 5. App content forms (answers)

- **Ads:** No ads.
- **Target audience:** 18 and over.
- **Data safety:**
  - Location (approximate and precise): collected, shared (with other riders in the ride, and family if
    the rider allows), for app functionality; optional; not stored on a server.
  - Audio (voice): collected and shared with riders in the ride, for app functionality; not stored.
  - Photos (profile photo) and name: shared with riders in the ride; optional.
  - Data is encrypted in transit; no account, so no deletion request is needed (everything else stays on
    the phone and is deleted with the app).
- **Foreground service permissions** (Play asks for a short description and a video link):
  - *Microphone*: "Keeps the group voice call going while the screen is off or another app (navigation)
    is open during a ride. Shown as an ongoing notification; ends when the rider leaves the ride."
  - *Location*: "Shares the rider's position with their group (Group map) and keeps speed, distance,
    hazard and SOS location working while riding with the screen off."
  - Video: a short screen recording of starting a ride, locking the screen and talking.
- **Display over other apps** (floating ride button): explain it's a one-tap ride menu over the
  navigation app; it's optional and asked for in the app.
- **Content rating:** questionnaire → no violence etc.; users can communicate with each other (voice).

## 6. Things to check before going public

- Ride server: switch from the open LiveKit sandbox to the private ride server (see README "Private ride
  server"), or anyone who knows the token server ID could join rides.
- Open-Meteo (rain alerts) is free for non-commercial use: fine for a free app without ads.
