# Microband

Microband is a local-first, native Android companion app for Microsoft Band 2. It targets Android 16 and Android 17 and does not depend on Microsoft Health, a Microsoft account, analytics, or a custom cloud backend.

## Current milestone

The first implementation focuses on the hardware-critical path:

- Android Companion Device Manager association
- discovery of `MSFT Band 2 xx:xx` advertisements and already-paired Band 2 devices
- public SDP-based RFCOMM connection
- one-byte command framing and exact response reads
- Band status packet parsing
- hardware/PCB identification
- firmware application, OOBE state, Band time, and profile reads
- safety-gated, resumable Band 2 OOBE completion
- opt-in Android notification forwarding with persistent category filters, duplicate suppression, rate limiting, and locked-phone privacy
- a hardware test notification using the Band 2 notification protocol
- Band theme colors and locally cropped 310 × 128 Me Tile wallpapers
- local opt-in protocol metadata logging
- native Jetpack Compose + Material 3 UI

Health history sync and Health Connect export remain post-connection milestones.

## Build

Use JDK 17 and an Android SDK containing API 37:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Hardware testing

A physical Microsoft Band 2 is required. Before finishing first-run setup, Microband verifies the PCB ID, confirms the main firmware app is running, reads the current OOBE state, and refuses to mutate already-configured or unsupported devices.

Protocol behavior was independently implemented from public interoperability facts and compared against the MIT-licensed `libmsftband` reference and documented community behavior. No Python code is embedded in the Android app.
