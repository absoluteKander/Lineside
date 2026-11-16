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

**Status: 1.0.0, source only.** Build it yourself (see [Builds](#builds)); no app store, no
published APKs. Privacy policy: [PRIVACY.md](PRIVACY.md).

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
- **Android backup is off unless you switch it on**, and even then Lineside hands its data only to a
  backup that is end-to-end encrypted with your screen lock (Google's backup reports this; so can
  Seedvault), or to a direct phone-to-phone transfer. Otherwise Android's backup gets nothing. On a
  restored phone, Lineside drops what belonged to the old one: contact ids, message ids, and the
  texts switches, which are consent given there.
- **Encrypted export**: one AES-256 ZIP file with every loop, archived ones too, and the text access
  log, as a page you can read in a browser and as JSON that Lineside can import again. You choose
  where it goes; Lineside suggests a five-word passphrase (EFF wordlist) or takes one of your own,
  and never keeps it. Opens with 7-Zip, PeaZip or Keka; the unzip built into Windows and macOS can't
  open AES ZIPs. Importing adds loops and leaves alone any that are already there.
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

## The first run

Before anything else, Lineside walks through what it does and doesn't do: no internet, no account,
no call log, no recording; a practice loop on the real loop card (held in memory, never saved); what
gets in and how, including what Android's own permission prompts will say; each permission with
its reason; the app lock, presented on, with turning it off asking a second time; the backup choice
and the export; the quiet-loop reminder, off unless chosen; and the maker's other app, with no
store link and nothing that leaves Lineside. It can be replayed from Settings. On a fresh install
the app lock waits until the introduction is done; there is nothing to protect before then.

## Builds

| Build | Where | Difference |
|---|---|---|
| `foss` | Build it yourself from this source | Can add texts from a loop's numbers, as described above |
| `play` | Build it yourself from this source | No SMS permission at all; texts come in through the share sheet. Shaped for app-store rules. |

The two builds are otherwise the same code. No APKs are published.

```sh
mise exec -- ./gradlew assembleFossDebug assemblePlayDebug testFossDebugUnitTest testPlayDebugUnitTest
```

### Signing a release

Release builds are signed only if a `keystore.properties` file exists at the project root. It is
listed in `.gitignore` and must never be committed:

```properties
storeFile=/absolute/path/to/upload-key.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Then `./gradlew assembleFossRelease` (or `assemblePlayRelease`) writes a signed APK under
`app/build/outputs/apk/`. The no-internet check runs for App Bundles as well as APKs.

Toolchain: AGP 8.11, Gradle 8.13, Kotlin 2.2, Room 2.7, compile/target SDK 36, min SDK 31
(Android 12). The export uses zip4j (Apache-2.0). Fonts: Barlow Condensed and JetBrains Mono, both
under the SIL Open Font License; passphrase words from the EFF large wordlist, CC BY 3.0 US (see
`licenses/`).

## Testing and verification

- **Unit tests** cover the parts that decide things: number matching, loop status, timeline
  grouping (threads, attached notes, folded calls), what a shared voicemail or text turns into,
  the date parser behind "Add to calendar", archive search, the flavor split, and the export: its
  JSON round trip, that phone-bound ids and texts switches never leave, the page escaping what was
  typed, a wrong passphrase, and files Lineside didn't make. They run in CI on
  every push.
- **The no-internet guard** is a build task, not a test: it reads each variant's merged manifest and
  fails the build on a network permission.
- **Checked by hand on an Android 16 emulator and on one Pixel running GrapheneOS**, not by
  automated UI tests: the pill during incoming and outgoing calls, the lock-screen notification,
  post-call logging, the share sheet with a real voicemail from the phone app, the texts switches,
  the permission hand-back (confirmed by Android's prompt reappearing and by `dumpsys package`),
  archiving and restoring, restore-on-call, the app lock, the first-run introduction, and export and import (the exported file
  was also opened with libarchive, a separate AES-ZIP implementation). Android backup was checked
  with the emulator's local transport: refused while switched off, refused while switched on but
  unencrypted, accepted when the transport reports end-to-end encryption, and a restore cleared the
  old phone's ids and texts switches.
- **Not yet verified:** stock Android 16 (a Pixel on stock Android is next); the `play` flavor on a
  real device; backup through Google's and Seedvault's real transports; Google Voice's share format; behaviour on Android 12, where the permission cannot be
  handed back automatically (Settings explains how to remove it by hand).
- **Known limits:** while SMS access is held, nothing in Android stops the app from reading other
  messages; the narrowing is Lineside's own code, the access log, and this source. Android's
  Recents may show a thumbnail of the last screen while the app lock is engaged.

## What's not done

Automatic expiry of archived loops, and automated UI tests (everything on screen is checked by hand,
as above).

## License

Copyright (C) 2026 Absolute Candor.
Lineside is free software under the [GNU General Public License v3 or later](LICENSE). You may
build it, change it and share it under the same terms. Fonts are under the SIL Open Font License.
