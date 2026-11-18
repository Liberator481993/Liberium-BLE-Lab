# Liberium BLE Lab

Native Android BLE testing toolkit built with Kotlin and Jetpack Compose.

## Current foundation
- BLE scanner with live RSSI and advertising record size.
- Local packet/profile library with educational UUID and payload examples.
- BLE advertising test mode with low transmit power, balanced interval, non-connectable frames, and a 30-second automatic timeout.
- Dark technical UI with Overview, Scanner, Library, and Advertising Lab sections.
- Runtime permission handling for Android 12+ Bluetooth permissions.

## Build
GitHub Actions builds a debug APK and uploads it as an artifact.

## Hardware notes
BLE scanning and advertising depend on the phone's Bluetooth controller and firmware. The app reports unsupported features and Android callback errors. It does not impersonate platform notification frames or run uncontrolled high-rate advertising.

## Roadmap
- Packet inspector with byte-level AD structure decoding.
- Import/export of user-created, standards-compliant BLE profiles.
- GATT service and characteristic explorer for devices the user is authorized to inspect.
- Live event log, session statistics, and theme customization.
