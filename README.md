# Lineside

Lineside keeps track of the calls you're waiting on: the clinic, the lab, the insurance claim, the
contractor, anyone who might call back. Each one is a **loop**. A loop holds that matter's calls,
voicemails, texts and your own notes on one timeline, and tells you whose move it is. When a loop's
number calls, your phone's call screen shows the loop's name and status. During or just after the
call, a small pill lets you jot a note without leaving the call. When the matter is done, you close
the loop and everything in it is removed, or archive it in case they come back.

Lineside is not a contacts app and not a CRM. It is for the dozen numbers in your life that matter
for a few weeks and then don't.

This project was written entirely by Claude, an AI model made by Anthropic, at the direction of
Absolute Candor, who specified the behavior, reviewed the result, and made every product and
privacy decision described below. Where this document says "the app does X," that claim has been
checked against the source, not assumed from a comment. What has and hasn't been verified is set
out honestly in [Testing and verification](#testing-and-verification).

**Status: pre-release.** Not yet on Google Play or F-Droid. Onboarding, backup and export are not
built yet; see [What's not done](#whats-not-done).

## What it will never do

- **No internet.** The app declares no network permission. The build fails if `INTERNET` or
  `ACCESS_NETWORK_STATE` ever appears in either flavor's merged manifest (`check<Variant>NoInternet`
  runs as part of every `assemble`). Nothing you put in Lineside can leave your phone through
  Lineside.
- **No account, no login, no analytics, no crash reporting.**
- **No call log access.** Lineside never reads your phone's call history. It can only notice a call
  as it happens, through Android's call-screening role, and only if you give it that role.
- **No recording, no transcription.** Audio is never stored. A voicemail shared into Lineside keeps
  its text; the audio file is named but never opened.
- **Every permission is optional.** With none of them, you add loops, calls, notes and texts yourself.

## How things get in

The rule throughout: **words only enter Lineside when you put them there, at that moment.** A
permission granted months ago is never taken as consent for content captured later.

- **Notes** you type, on a loop or on the pill during a call.
- **Calls:** if you give Lineside the call-screening role, it sees each call's number as the call
  starts (nothing else: no audio, no history) and logs calls from a loop's numbers to that loop.
  With Phone-state access it also records when a call was answered and how long it lasted.
- **Voicemails and texts, by sharing.** Share a voicemail or a message from any app to Lineside.
  A screen shows exactly what arrived, which number it came from (if any), and which loop it will
  go on, before anything is saved. Works in both builds.
- **Texts, from the phone's messages (`foss` build only).** Behind two switches, one for the loop
  and one for each number, both off by default. Android's SMS permission is asked for when the first
  number is switched on, not before. Lineside reads only received texts, only from switched-on
  numbers, only newer than the moment each was switched on, and only when opened or once a day
  (it never asks for `RECEIVE_SMS`). Every look is written to a **text access log** you can read in
  Settings. When no number anywhere is switched on, Lineside hands the permission back to Android
  (`revokeSelfPermissionsOnKill`), and Android asks again next time, which is how you can tell it was
  really given back. You can also pick individual texts by hand from the last 90 days. This cannot
  see RCS chats; share those instead.

## Keeping it private on the phone

- **App lock**, on by default wherever the phone has a screen lock. Lineside asks for your
  fingerprint, face or PIN through Android's own prompt when opened after a while away; nothing is
  stored by Lineside. Turning it off is a deliberate choice in Settings.
- **Discreet call screen** (optional): the call screen shows only "Call back" or "Waiting", never a
  loop's topic or who within it is calling.
- **Backup is off** (`allowBackup="false"`). A user-chosen, explained backup is planned, never a
  silent one.
- **Closing a loop deletes it**: its calls, notes, texts, numbers and its entry in your contacts.
  Archiving keeps it out of the way instead, and Lineside stops recognising its numbers until you
  restore it.
- **Lineside is not your medical record.** The originals stay with your provider and with your
  phone's own apps. Lineside holds the notes you chose to keep about them.

## Permissions, exactly

| Permission | Asked | Used for |
|---|---|---|
| Call-screening role | Optional, when you turn on "Notice calls" | The number of each call as it starts. Never blocks, silences or alters a call. |
| `READ_CONTACTS`, `WRITE_CONTACTS` | Optional | A local, never-syncing account that shows a loop's name and status on the call screen, and lets Lineside stay out of calls from your permanent contacts. |
| `READ_PHONE_STATE` | Optional | When a call is answered and when it ends: note timestamps and call length. |
| `POST_NOTIFICATIONS` | Optional | The lock-screen "tap to jot a note" during a call, and the opt-in quiet-loop reminder. |
| `SYSTEM_ALERT_WINDOW` | Optional | The pill during and just after calls. |
| `READ_SMS` (`foss` build only) | Optional, at the first switched-on number; handed back when unused | Texts from switched-on numbers, as above. Lineside holds no `SEND_SMS`; Android's prompt still says "send and view" because that is the wording for the whole SMS group. |
| `USE_BIOMETRIC` | Granted at install, no prompt | The app lock's use of your phone's screen lock. |
| `READ_SYNC_SETTINGS`, `WRITE_SYNC_SETTINGS` | Granted at install, no prompt | Marking Lineside's local contacts account as never syncing. |

## Builds

| Build | Where | Difference |
|---|---|---|
| `foss` | GitHub releases, F-Droid (planned) | Can add texts from a loop's numbers, as described above |
| `play` | Google Play (planned), for a small price | No SMS permission at all; texts come in through the share sheet |

The two builds are otherwise the same code. The Play price pays for the work and the convenience
of store updates; nothing is held back from the free build.

```sh
mise exec -- ./gradlew assembleFossDebug assemblePlayDebug testFossDebugUnitTest testPlayDebugUnitTest
```

Toolchain: AGP 8.11, Gradle 8.13, Kotlin 2.2, Room 2.7, compile/target SDK 36, min SDK 31
(Android 12). Fonts: Barlow Condensed and JetBrains Mono, both under the SIL Open Font License
(see `licenses/`).

## Testing and verification

- **Unit tests** cover the parts that decide things: number matching, loop status, timeline
  grouping (threads, attached notes, folded calls), what a shared voicemail or text turns into,
  the date parser behind "Add to calendar", archive search, and the flavor split. They run in CI on
  every push.
- **The no-internet guard** is a build task, not a test: it reads each variant's merged manifest and
  fails the build on a network permission.
- **Checked by hand on an Android 16 emulator and on one Pixel running GrapheneOS**, not by
  automated UI tests: the pill during incoming and outgoing calls, the lock-screen notification,
  post-call logging, the share sheet with a real voicemail from the phone app, the texts switches,
  the permission hand-back (confirmed by Android's prompt reappearing and by `dumpsys package`),
  archiving and restoring, restore-on-call, and the app lock.
- **Not yet verified:** stock Android 16 (a Pixel on stock Android is next); the `play` flavor on a
  real device; Google Voice's share format; behaviour on Android 12, where the permission cannot be
  handed back automatically (Settings explains how to remove it by hand).
- **Known limits:** while SMS access is held, nothing in Android stops the app from reading other
  messages; the narrowing is Lineside's own code, the access log, and this source. Android's
  Recents may show a thumbnail of the last screen while the app lock is engaged.

## What's not done

Onboarding (which will explain everything above, in the app, before any permission is asked for),
a user-chosen backup, an encrypted export you can open on a computer, and the store listings.

## License

Copyright (C) 2026 Absolute Candor.
Lineside is free software under the [GNU General Public License v3 or later](LICENSE). You may
build it, change it and share it under the same terms. Fonts are under the SIL Open Font License.
