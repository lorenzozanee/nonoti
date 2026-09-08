# Release-path recheck

Changed `AndroidFocusPlatform.kt`:

- Activation persistence rollback disables the app-owned Zen rule and cancels the session alarms without posting a completion summary.
- Summary dispatch precedes the persisted sent marker so a process interruption before dispatch does not permanently suppress the summary.
- Updates to an existing summary use `setOnlyAlertOnce(true)`.

Verification:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
bash scripts/verify-emulator.sh emulator-5554
```

Build and local checks succeeded. The API 37 instrumentation run passed 22 tests in 186.484 seconds.

Device fingerprint: `google/sdk_gphone64_arm64/emu64a:17/CE2A.260420.019/15611780:userdebug/dev-keys`.

Debug APK SHA-256: `6466bb3b1e37ab74e8cac1f14064bf0c7530af4d05977ff0cbedff682161c470`.

Unsigned release APK SHA-256: `cbec3b5e2e32cb74159fa4bf853b11f8206846518931f26802dfc79df5da4cd3`.

The previous five-device matrix covers API 33-37 before these release-path changes. API 33 and API 37 were rerun for this production revision. These tests do not inject a process interruption between notification dispatch and database persistence, nor do they establish exactly-once audible delivery across dismissal and process death.

## Summary retry regression

Added `summaryRetryUpdatesOneNotificationWithoutRepeatingAlert` to `CompatibilityFlowInstrumentedTest.kt`. It posts a summary, retries with an updated count, verifies one active summary with the new text, and verifies `FLAG_ONLY_ALERT_ONCE`.

- API 37: the new targeted test passed, in addition to the 22-test run above.
- API 33: the complete expanded suite passed 23 tests in 186.314 seconds using `bash scripts/verify-emulator.sh emulator-5556`.
- API 33 fingerprint: `google/sdk_gphone64_arm64/emu64a:13/TE1A.240213.009/12342917:userdebug/dev-keys`.
- `./gradlew :app:lintDebug` passed after adding the test.

The retry test verifies Android notification identity, updated content, and alert configuration. It does not measure physical sound or vibration.

Physical OEM hardware and actual messaging, telephony, and alarm application validation remain outstanding. Emulator results do not establish universal Android compatibility.
