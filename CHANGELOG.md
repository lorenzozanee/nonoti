# Changelog

## 0.1.2 - 2026-09-08

### Changed

- Renamed user-facing branding and Gradle project display name to NoNoTi.
- Replaced normal and rounded launcher icons with the updated NoNoTi artwork.
- Request Android-owned permission surfaces on first launch; Setup remains available for permissions that are still missing.
- Made compatibility self-test optional for starting Focus.
- Keep an active Focus session in a foreground service so removing the recent-task card does not stop notification capture.

### Validation and limitations

- Unit tests, Android lint, and Debug build passed.
- API 37 emulator validation confirmed first-run permissions, Focus without compatibility self-test, recent-task removal, notification capture, release summary, and foreground-service shutdown.
- Android force-stop remains outside the guarantee because it disables the app's services, receivers, and alarms.

## 0.1.1 - 2026-09-08

### Changed

- Removed the app title bar and setup/readiness card from Focus for a cleaner home screen.
- Moved required permissions and the compatibility self-test into a setup dialog that returns whenever incomplete setup re-enters the foreground.
- Defaulted notification capture to all ordinary apps while calls and alarms remain blocked during Focus.
- Added persistent light/dark appearance switching with dark mode as the default.
- Added complete Simplified Chinese resources while continuing to follow the system language.

### Validation and limitations

- Unit tests, Android lint, Debug/Release builds, and six focused UI tests passed.
- The UI flow was manually checked on an Android 14 Google API emulator.
- Physical OEM behavior remains unverified.
- This release provides an unsigned Release APK. It must be signed before installation.

## 0.1.0 - 2026-09-08

Initial preview release for Android 13 and later.

### Added

- Focus, Box, and Settings with a minimal Jetpack Compose interface.
- Daily schedules, cross-midnight blocks, Start now, and continuous session extension.
- Local notification snapshots, deduplication, retention, and source-app navigation.
- Notification snoozing, app-owned Do Not Disturb rules, and Quiet Release.
- Emergency Access, permission checks, device self-test, and fail-open recovery.
- Unit tests, device interaction tests, and recovery/Doze verification scripts.
- Apache-2.0 license.

### Fixed

- Notification cancellation and group-summary handling.
- Listener shutdown, stale Zen state, and interrupted session recovery.
- Daylight-saving time boundaries.
- Activation rollback incorrectly posting a completion summary.
- Summary persistence order and repeated alerts when updating an existing summary.

### Validation and limitations

- Earlier 22-test suites passed on API 33, 34, 35, 36, and 37 emulators.
- Current production revision passed 23 tests on API 33 and 22 tests plus the summary retry test on API 37.
- Unit tests, Android lint, and Debug/Release builds passed.
- Physical OEM devices and real messaging, phone, and alarm applications remain unverified. Universal Android compatibility is not claimed.
- Exactly-once audible summary delivery across process death and notification dismissal remains unverified.
- This release provides an unsigned Release APK. It must be signed before installation. No release signing key has been created.

See [verification details](docs/testing/results/2026-09-08-release-recheck.md).
