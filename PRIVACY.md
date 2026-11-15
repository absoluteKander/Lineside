# Lineside privacy policy

Effective 15 November 2026. Lineside is made by Absolute Candor.

**Short version: Lineside collects nothing. Everything you put in it stays on your phone, and
Absolute Candor never receives any of it.**

## What Lineside can't do

- **It can't use the internet.** The app does not have Android's internet permission, so it cannot
  send anything anywhere: not to us, not to anyone. The build is checked for this automatically.
- **No account, no login, no analytics, no advertising, no crash reporting**, and no third-party
  code that does any of those.
- **It never reads your call log**, and never records or transcribes calls.

## What stays on your phone

Lineside keeps what you put in it, in its own private storage on your phone:

- the loops you create: a name, an optional topic, and phone numbers you enter;
- notes you type;
- calls from a loop's numbers, if you allow Lineside to notice calls: the number, the time, and
  (with phone-state access) when the call was answered and how long it lasted;
- voicemails and texts you choose to share into Lineside (their text only; audio is never kept);
- in the version built from source (not the Google Play version): texts from numbers you
  individually switch on, and a log of every time Lineside looked at your messages.

When you close a loop, everything in it is deleted. Archived loops stay until you delete them.

## Permissions, and what each is for

All are optional. With none of them, you add calls and notes yourself.

| Permission | Used for |
|---|---|
| Call screening role | Seeing the number of each call as it starts, to match it to a loop. Lineside never blocks, silences or labels calls. |
| Contacts | A local, never-syncing contact for each loop, so your phone app shows the loop's name and status when they call; and staying out of the way for people already in your contacts. |
| Phone state | When a call is answered and when it ends. |
| Notifications | A "tap to jot a note" during calls answered on the lock screen, and an optional reminder about quiet loops. |
| Display over other apps | The note pill during and just after calls. |
| Biometric / screen lock | The app lock, using your phone's own screen lock. Lineside stores nothing about it. |

The Google Play version of Lineside does not request access to your text messages.

## Copies you make

- **Android backup** is off for Lineside unless you switch it on. When it's on, Lineside allows its
  data into your phone's backup only if that backup is end-to-end encrypted with your screen lock,
  or during a direct transfer to a new phone. That backup is run by your phone's backup service
  (for example Google or Seedvault) under their terms, not by us; we cannot read it.
- **Encrypted export** is a file you create and save where you choose, protected by a passphrase
  that Lineside does not keep.

## Children

Lineside is not directed at children.

## Changes

If this policy changes, the new version will be published at this address with a new effective
date. The history of every change is visible in this repository.

## Contact

Questions about this policy: open an issue at
<https://github.com/absoluteKander/Lineside/issues>.
