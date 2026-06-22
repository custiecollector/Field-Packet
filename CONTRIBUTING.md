# Contributing to FieldPacket

FieldPacket is intentionally small, offline-first, and Android-standalone.

## Development constraints

Do not add these without explicit project approval:

- `INTERNET` permission
- cloud, accounts, telemetry, analytics, or ads
- contacts, SMS, phone, location, Bluetooth, USB, camera, advertising ID, or account permissions
- background microphone listener or foreground microphone service
- persistent audio capture/storage in the normal app flow
- direct serial, USB, Bluetooth, or network device control

## Development setup

Prerequisites:

- JDK 17 available through `JAVA_HOME` or `PATH`
- Android SDK with platform/build tools installed
- `ANDROID_HOME` and `ANDROID_SDK_ROOT` set
- Gradle 8.10.x available as `gradle`

Build before submitting changes:

```bash
gradle --no-daemon clean assembleRelease
```

Inspect built APK permissions and signing before sharing an artifact:

```bash
APK=app/build/outputs/apk/release/app-release.apk
BUILD_TOOLS="${ANDROID_HOME}/build-tools/35.0.0"
"${BUILD_TOOLS}/aapt" dump permissions "$APK"
"${BUILD_TOOLS}/apksigner" verify --verbose --print-certs "$APK"
```

Expected permission posture: `android.permission.RECORD_AUDIO` may appear; `android.permission.INTERNET` must not appear.

## Documentation rules

- Keep public docs focused on product behavior, build instructions, privacy posture, and contribution rules.
- Do not commit personal notes, local paths, credentials, signing material, generated binaries, or release artifacts.
- Distinguish implemented app behavior from external hardware behavior.

## License note

FieldPacket uses the Apache License, Version 2.0. Do not import third-party code or assets unless their license is compatible with Apache-2.0.
