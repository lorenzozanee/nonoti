# nonoti v0.1.0 emulator matrix

Command used for each device:

```bash
scripts/verify-emulator.sh <emulator-serial>
```

The script builds and installs both APKs, resets the app-owned DND rule, grants test capabilities, runs the instrumentation suite, and fails unless the runner prints `OK`.

| Android | AVD | Image | Result |
|---|---|---|---|
| API 33 | `Nonoti_Small_API33` | Google APIs ARM64 | 22/22 passed |
| API 34 | `Nonoti_Pixel7_API34` | Google APIs ARM64 | 22/22 passed |
| API 35 | `OPPO_Reno10` | Google APIs ARM64 | 22/22 passed |
| API 36 | `medium_phone` | Google Play ARM64, release keys | 22/22 passed |
| API 37 | `Nonoti_Pixel9_API37` | Google APIs ARM64 | 22/22 passed |

Covered by the device suite:

- Controlled notification capture, snooze, Quiet Release, Zen activation/deactivation, and exactly one nonoti summary.
- Quiet Release timeout persistence, user-disabled Zen recovery without re-enabling it, and wall-clock change release.
- Room persistence across database reopen, cross-process notification update deduplication, notification cancel behavior, retention, active-session retention, group-summary replacement, and focus-session state.
- Minute-precision Focus controls, Start now end-time control, long-press deletion, privacy disclosure, and primary navigation shell.

The external notification publisher is test-only (`com.example.nonoti.test`). It exercises the real `NotificationListenerService` callback path without adding network access or components to the production APK.

Additional scripts:

```bash
scripts/verify-recovery.sh <emulator-serial>
scripts/verify-doze.sh <emulator-serial>
```

`verify-recovery.sh` covers process reconstruction, notification permission revocation, exact-alarm revocation, and Notification Access revocation. `verify-doze.sh` locks the screen, forces deep Doze, posts an external notification, and verifies the persisted Box count after wake-up. Both scripts passed on API 33, 34, 35, 36, and 37.

On API 33-34, Android does not expose per-rule condition state. nonoti therefore refuses to start Focus while another DND rule is already active and fails open after a full process restart; this keeps activation evidence fail-closed instead of treating global DND as proof that nonoti is active. An active in-process Session can still be extended without toggling Zen again.

Visual checks completed on a small API 33 viewport:

- Portrait and landscape.
- 1.5 font scale with scrolling.
- Light and dark themes.
- No text overlap or inaccessible bottom navigation was observed.

## Remaining release evidence

Emulators do not emulate vendor notification timing, vibration hardware, telephony, real alarms, or OEM background restrictions. Universal compatibility is therefore enforced by the in-app device/ROM/version self-test instead of assumed globally. At least one Android 13+ physical OEM device remains required before a production release claim.
