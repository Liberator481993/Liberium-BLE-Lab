# Liberium BLE Lab

Native Android utility built with Kotlin and Jetpack Compose.

## Sections
- Overview and live status.
- BLE Scanner: nearby advertisements, RSSI and record size.
- Packet Library: built-in educational BLE profiles.
- Advertising Lab: short, low-power, non-connectable test broadcasts with automatic timeout.
- Universal Remote: offline infrared control UI for supported TV/projector profiles.

## Offline TV / projector remote
The remote uses Android's `ConsumerIrManager`; it does not connect the TV to Wi-Fi or require a shared network. It requires a phone with a built-in IR emitter or a compatible external IR accessory. The current starter profiles include basic Samsung and LG NEC-style commands (power, volume and mute). Other brands and exact model variants need verified command tables and device testing. The app detects whether the phone reports an IR emitter and explains when hardware is unavailable.

## BLE behavior and safety
BLE advertising is one-way broadcast; it does not establish a connection and cannot force another phone to open a full-screen popup or display a user-selected image. Mobile operating systems control their own pairing and notification UI. This project focuses on scanning, inspecting, and controlled testing of standard BLE advertisements; it does not flood nearby devices or impersonate OS pairing prompts.

## Build
GitHub Actions builds a debug APK and uploads it as an artifact.
