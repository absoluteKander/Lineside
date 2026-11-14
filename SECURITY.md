# Security and privacy: how the claims are checked

[README.md](README.md) says what Lineside promises. This file is about how those promises are
kept true as the code changes, and how to report it when one isn't.

## The rule: check the code, not the comment

A comment, a README sentence or this file can describe something that was true when written and
has quietly stopped being true. Before relying on a claim about what Lineside does, check it
against the current source, and where possible against the running app. The checks below exist so
that the most important claims are enforced by something other than prose.

## What is enforced mechanically

- **No network.** `app/build.gradle.kts` registers `check<Variant>NoInternet`, which parses the
  merged manifest of each build variant and fails the build if `INTERNET` or
  `ACCESS_NETWORK_STATE` is present. `assemble<Variant>` depends on it. The manifest also removes
  both permissions with `tools:node="remove"` so a library cannot add them.
- **No SMS in the Play build.** `READ_SMS` lives only in `app/src/foss/AndroidManifest.xml`.
  `BuildFlavorTest` asserts the `READS_SMS` build flag matches the flavor; the Play manifest can be
  inspected with `aapt dump permissions` after a build.
- **No call log.** No `READ_CALL_LOG` anywhere. Calls are seen through `CallScreeningService`,
  which receives each call's number as it starts and nothing historical.
- **Backup off.** `android:allowBackup="false"` in the manifest.

## What is narrowed by code, and must stay narrowed

Android's SMS permission is coarse: while held, the app could read any message. Lineside's
narrowing lives in `app/src/main/java/app/lineside/Texts.kt`:

- the only query is against the SMS inbox, by a switched-on number's key, newer than the time that
  number was switched on;
- every look writes a `TextLog` row that Settings shows the user;
- `reconcile()` hands the permission back when no number anywhere is switched on.

Any change to that file should keep all three, and the access log should keep describing what the
code actually does.

## What is deliberately not done

No call recording or transcription, no automatic import of message bodies without a per-number
switch, no `RECEIVE_SMS`, no notification-listener access, no `FLAG_SECURE` on windows (it would
also block the user's own screenshots).

## Reporting

If you find a way for data to leave the device through Lineside, a way to read messages outside the
switched-on numbers, or a lock bypass, open a GitHub issue marked **security**, or, if it needs to
stay private until fixed, use GitHub's private vulnerability reporting on this repository.
