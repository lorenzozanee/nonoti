# Journey: Focus compatibility self-test

Device: `Nonoti_Pixel9_API37`, Android API 37, Google APIs ARM64 emulator.

## Results

### Action: Install the current debug APK and grant required access ✅

- Installed the debug APK.
- Granted Notification Access, Do Not Disturb access, exact alarms, and post notifications.

### Action: Open nonoti and verify readiness ✅

- Notification Access, Do Not Disturb, Automatic Zen Rule, exact alarms, and summary notifications displayed `Ready`.
- Compatibility self-test displayed `Needs access`.

### Action: Run the 30-second compatibility test ✅

- Started the controlled test from the compatibility dialog.

### Action: Verify Focus lock during the test ✅

- The screen displayed `Focus active` and the end time.
- Box navigation and message content were unavailable.
- `dumpsys notification` reported the nonoti rule as `STATE_TRUE`.

### Action: Verify Quiet Release and confirmation ✅

- After the Focus boundary and release window, the dialog displayed `Test completed. Confirm what you observed.`
- The original controlled notification returned under the active DND policy without a visible heads-up interruption during observation.

### Action: Confirm this device ✅

- After confirmation, the readiness card displayed `Ready for Focus` and Start now became available.

### Action: Verify the captured notification in Box ✅

- Box displayed `nonoti compatibility test` and `This notification should be captured without an alert.`

### Action: Verify Zen cleanup and one summary ✅

- `dumpsys notification` reported `mZenMode=ZEN_MODE_OFF` and the nonoti rule as `STATE_FALSE`.
- Exactly one business notification titled `Focus finished` was present. Android's automatic group summary was excluded from the business-notification count.

## Scope

This journey proves the controlled emulator flow. Sound, vibration, OEM heads-up behavior, phone calls, alarms, Doze, and vendor background restrictions still require a real-device release matrix.
