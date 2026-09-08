# nonoti recovery and Doze matrix

The following commands were run against the same debug APK on each AVD:

```bash
scripts/verify-recovery.sh <emulator-serial>
scripts/verify-doze.sh <emulator-serial>
```

| API | AVD | Recovery | Doze + lock screen |
|---|---|---|---|
| 33 | `Nonoti_Small_API33` | passed | passed |
| 34 | `Nonoti_Pixel7_API34` | passed | passed |
| 35 | `OPPO_Reno10` | passed | passed |
| 36 | `medium_phone` | passed | passed |
| 37 | `Nonoti_Pixel9_API37` | passed | passed |

Recovery checks use real system state changes for process reconstruction, `POST_NOTIFICATIONS` revocation, exact-alarm revocation, and Notification Access revocation. Each scenario verifies that an active Focus does not remain falsely active or leave nonoti's own Zen rule enabled.

The Doze check locks the display, forces deep Doze, posts a notification from the test-only source package, wakes the device, and verifies one persisted Box record while Focus and the own Zen rule remain active.

These are Google AOSP/Google APIs or Google Play images. The API 35 `OPPO_Reno10` profile is a generic hardware/API approximation and does not validate ColorOS behavior, vendor battery policies, audio hardware, telephony, or physical alarms.
