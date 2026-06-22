# Changelog

This project keeps a concise record of user-facing changes.

## 0.1.13

- Initial GitHub release of FieldPacket for offline field-radio packet utilities.
- Includes FP1 compose/decode, offline browser decode, APRS/TNC2 and AX.25 helpers, Bell 202 AFSK transmit/receive tools, KISS/TNC helpers, PCM/WAV import, reusable message templates, and app-private operational presets.
- Maintains the offline Android permission posture: no `INTERNET` permission, no cloud/accounts/telemetry/ads/device-control permissions, and `RECORD_AUDIO` only when the user starts live receive.
