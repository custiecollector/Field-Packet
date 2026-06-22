# Security policy

FieldPacket is early field-radio packet tooling, not externally audited security software.

## Supported versions

Security fixes apply to the current release and the main branch.

## Reporting issues

Use GitHub vulnerability reporting when available. For non-sensitive bugs, open a regular issue.

Do not post exploit details, signing material, or sensitive operational details in public issues.

## Security and privacy boundaries

Expected security/privacy posture:

- no `INTERNET` permission
- no cloud, accounts, telemetry, analytics, or ads
- no stored audio in normal operation
- microphone starts only after an explicit user action
- generated audio, live receive audio, and imports are processed in memory
- release signing material is never committed

Before sharing an APK, verify the built artifact rather than relying on source inspection alone:

```bash
"${ANDROID_HOME}/build-tools/35.0.0/aapt" dump permissions app/build/outputs/apk/release/app-release.apk
"${ANDROID_HOME}/build-tools/35.0.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

If a build unexpectedly includes network, location, Bluetooth, USB, contacts, SMS, phone, camera, account, advertising, or telemetry permissions, treat it as a release blocker.
