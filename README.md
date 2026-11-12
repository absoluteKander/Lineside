# Lineside

Lineside keeps track of the calls you're waiting on: the doctor's office, the lab, the insurance
claim, anyone who might call back. Each one is a **loop**. When a loop calls, your phone shows its
status right on the call screen. During or after the call you can jot a note. When it's resolved,
you close the loop and it's gone.

## What it will never do
- **No internet.** The app has no network permission, and the build fails if one ever appears.
- **No account, no login.**
- **No call log access.** Lineside only notices a call as it happens, and only keeps what you save.
- **Every permission is optional.** Without any of them, you add loops and notes yourself.

Your data is yours: export it any time as an encrypted file you can open on a computer or
import on your next phone.

## Builds
| Build | Where | Difference |
|---|---|---|
| `foss` | GitHub, F-Droid | Can show text messages from a loop's number in its timeline |
| `play` | Google Play | Texts come in through the share sheet instead (Play policy) |

```sh
mise exec -- ./gradlew assembleFossDebug assemblePlayDebug
```

## License
Copyright (C) 2026 Absolute Candor.
Lineside is free software under the [GNU General Public License v3 or later](LICENSE).
