# FieldPacket privacy

FieldPacket is designed as an offline Android packet utility.

## Network and accounts

FieldPacket does not request `android.permission.INTERNET`. It has no account login, cloud sync, telemetry, analytics, advertising, remote crash reporting, or server dependency.

The app also does not request contacts, SMS, phone, location, Bluetooth, USB, camera, advertising ID, or account permissions.

## Microphone

FieldPacket requests `android.permission.RECORD_AUDIO` only for live microphone APRS/AFSK receive. The microphone is not started on app launch. It starts only after the user taps the live RX control and grants permission.

Live receive audio is processed in memory and cleared when receive stops. FieldPacket does not write live microphone audio files.

## Audio generation and imports

Generated test tones and AFSK transmit samples are in-memory buffers. They are not saved as audio files.

PCM/WAV imports are opened through Android's system file picker after the user chooses a file. Imported bytes are read for decode in memory only and are not copied into app storage.

## Local app data

FieldPacket may store these small settings in Android app-private preferences:

- one reusable custom message template
- one operational preset for radio controls, selected transport profile, APRS address fields, and raw PCM sample rate

The operational preset does not store packet body text, audio, imported file bytes, hardware sessions, account data, or network data.

## Backup and transfer

The Android manifest disables backup and points to data-extraction rules that exclude shared preferences, files, and databases from cloud backup and device transfer.

## External hardware

KISS/TNC, SDR, cable, microphone, and radio behavior depends on the user's chosen external equipment and audio path. FieldPacket does not add serial, USB, Bluetooth, or network device control.
