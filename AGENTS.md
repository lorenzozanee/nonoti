# Repository Guidelines

This directory is an early-stage Android application. It currently contains product assets but no Gradle project, source code, tests, or local Git history. Do not describe planned commands or dependencies as operational until they exist.

## Project structure and module organization

- `icons/` contains the square and rounded launcher-icon sources.
- `../../docs/elementary/nonoti/nonoti v0.1.0 产品定义.md` is the product specification and behavioral source of truth.
- `.serena/` contains local agent metadata; it is not application code.

When scaffolding begins, use one `app` module with feature-first packages under `app/src/main/java/`: `focus/`, `box/`, `settings/`, and `notifications/`. Put unit tests in `app/src/test/`, device and Compose UI tests in `app/src/androidTest/`, and Android resources in `app/src/main/res/`.

## Architecture overview

Follow unidirectional data flow:

```text
Compose UI -> ViewModel (StateFlow) -> Repository -> Room/DataStore/platform services
```

Keep notification listening, DND rules, alarms, and recovery logic behind explicit service or repository boundaries. Composables render state and emit events; they must not access databases or Android services directly.

## Build, test, and development commands

No Gradle wrapper exists yet. After the project is scaffolded, these should be the canonical tasks:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Start the available emulator with `android emulator start OPPO_Reno10`, then confirm connectivity with `adb devices`. Update this section if generated Gradle or CI tasks differ.

## Coding style and naming conventions

Use Kotlin with four-space indentation and Kotlin official formatting. Prefer immutable state and small, focused files. Use `PascalCase` for types and composables, `camelCase` for functions and properties, and descriptive suffixes such as `FocusScreen`, `BoxViewModel`, `NotificationRepository`, and `FocusUiState`. Keep user-facing text in resources.

## Testing guidelines

No testing framework is configured yet; derive it from Gradle when added. Name unit tests `ClassNameTest` and instrumented tests `ClassNameInstrumentedTest`. Cover time boundaries, cross-midnight schedules, notification deduplication, permission revocation, process recovery, and Quiet Release. Device tests must report the Android version and ROM used.

## Commit and pull request guidelines

There is no local history to infer a convention from. Use concise imperative commits, preferably `feat:`, `fix:`, `test:`, or `docs:` prefixes. Pull requests must summarize behavior changes, link the relevant requirement, list exact verification commands, and include screenshots for UI changes. Call out permission, notification, privacy, and OEM-specific risks.

## Security and privacy

Never log notification contents or commit credentials or signing material. Keep Box data local, exclude it from backups, and do not add network access without explicit product approval.
