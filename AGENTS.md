# AGENTS.md

## Cursor Cloud specific instructions

### Repositories

- **Openterface_KeyCmd_Android** — Native Android app (Java, Gradle). This is the main buildable project.
- **dev-keycmd** — Empty placeholder repo (no buildable content).

### Environment

- **JDK 21** is required (OpenJDK, already in base image at `/usr/lib/jvm/java-21-openjdk-amd64`)
- **Android SDK** at `/opt/android-sdk` with `platforms;android-35`, `build-tools;35.0.0`, `platform-tools`
- Environment variables must be set: `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT` (the update script handles this)

### Build & Test Commands

| Task | Command |
|------|---------|
| Debug APK build | `./gradlew assembleDebug` |
| Lint | `./gradlew lintDebug` |
| Unit tests | `./gradlew testDebugUnitTest` |
| Clean | `./gradlew clean` |

All Gradle commands run from `/agent/repos/Openterface_KeyCmd_Android`.

### Non-obvious notes

- **Lint exits non-zero** due to ~149 pre-existing errors (primarily `NewApi` calls). This is expected; the project has no `lint-baseline.xml` configured yet.
- **5 pre-existing unit test failures** in `GamepadLayoutPresetBackgroundCodecTest` and `GamepadLayoutPresetDocumentTest` — these are in the repo before any changes and are not caused by environment issues.
- **Robolectric setup**: The build script has custom Gradle tasks (`prepareRobolectricMavenLocal`) that stage instrumented Android JARs for offline unit tests. This runs automatically as part of `testDebugUnitTest`.
- **No emulator/device needed for unit tests** — Robolectric handles Android framework mocking.
- **Source compatibility is Java 8** (`sourceCompatibility = 1.8`) even though JDK 21 is used for compilation. This triggers deprecation warnings from javac that are safe to ignore.
- **Release builds require signing secrets** (keystore, passwords). Debug builds work without them.
- The `gradlew` wrapper auto-downloads Gradle 8.9 on first run if not cached.
