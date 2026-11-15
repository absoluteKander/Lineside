# Google Play listing

Text and answers for the Play Console. The screenshots are in `screenshots/` (1080×2160, made on
an emulator with made-up loops and 555 numbers). The feature graphic and icon are in `../art/`.

## Store listing

**App name** (30 max): Lineside

**Short description** (80 max):
Temporary contacts for the calls you're waiting on. No internet, no account.

**Full description** (4000 max):

Waiting on a call from the clinic, the lab, the insurance claim or the contractor? Lineside keeps
track of it.

Each matter is a loop. A loop holds that matter's calls, voicemails, texts and your own notes on one
timeline, and tells you whose move it is: Call back, or Waiting. When it's done, close the loop and
everything in it is gone. If they might come back, archive it instead.

WHEN THEY CALL
• Your phone's call screen shows the loop's name and status, so you know what it's about before
you answer.
• A small pill during and just after the call lets you jot a note without leaving the call.
• Calls from a loop's numbers are logged to it by themselves.

EVERYTHING STAYS ON YOUR PHONE
• Lineside has no internet access at all. It can't send anything anywhere.
• No account, no login, no ads, no analytics, no crash reporting.
• It never reads your call log, and never records or transcribes a call.
• Voicemails and texts get in only when you share them to Lineside, and you see exactly what will
be added first.
• App lock with your phone's own fingerprint, face or PIN, on by default.
• A discreet mode for the call screen, so a ringing phone doesn't show what the call is about.

YOUR COPIES, YOUR CHOICE
• Android backup is off unless you choose it, and then only into a backup encrypted with your
screen lock.
• Export everything to one encrypted file you can open on a computer, or import on another phone.

Every permission is optional. With none of them, you add calls and notes yourself.

Lineside is free software under the GNU GPL v3. The source is at
github.com/absoluteKander/Lineside. Buying it here pays for the work and gets you updates through
Google Play.

Lineside isn't your medical record: the originals stay with your provider. Lineside holds the notes
you chose to keep about them.

**Category:** Productivity
**Tags:** (pick from Play's list) Productivity, Organizer
**Contact email:** your developer contact email (Play requires one; it is shown publicly)
**Website:** https://github.com/absoluteKander/Lineside
**Privacy policy:** https://github.com/absoluteKander/Lineside/blob/main/PRIVACY.md

## App content answers

- **Privacy policy:** the URL above.
- **Ads:** No.
- **App access:** All functionality is available without special access (no login).
- **Content rating questionnaire:** Utility/Productivity; no violence, sexual content, gambling,
  drugs, user-generated content shared with others, or location sharing. Expected: Everyone / PEGI 3.
- **Target audience:** 18 and over (it's for adults managing calls; not designed for children).
- **News app:** No.
- **Health apps declaration:** No health features. (It keeps notes about calls; it does not
  measure, track or advise on health.)
- **Financial features:** None.
- **Government app:** No.

## Data safety

Google defines "collected" as data sent off the device. Lineside cannot send data off the device.

- **Does your app collect or share any of the required user data types?** No.
- **Is all of the user data collected by your app encrypted in transit?** Not applicable (nothing
  is collected).
- **Do you provide a way for users to request that their data is deleted?** Not applicable;
  everything is on the device, and closing a loop or uninstalling deletes it.

Android's own backup and the user's export are actions the user takes with their own copy;
Google's guidance excludes them from "collection".

## Permission notes for review

None of Lineside's Play permissions need a declaration form:

- No SMS or call-log permissions (the Play build has none; the build checks it).
- Call screening is a role the user grants in Android's own dialog; Lineside never blocks calls.
- `SYSTEM_ALERT_WINDOW` is granted by the user in Android settings, for the in-call note pill.
- No foreground services (WorkManager's is removed from the manifest).

## Release

- Build: `./gradlew bundlePlayRelease` with `keystore.properties` present (see README, "Signing").
- Output: `app/build/outputs/bundle/playRelease/app-play-release.aab`.
- Version: 1.0.0 (versionCode 1). Every later upload needs a higher versionCode.
