# Microband

<p align="center">
  <img src="artwork/microband-icon-concept.png" alt="Microband icon" width="160">
</p>

<p align="center">
  An unofficial, open-source Android companion app for Microsoft Band 2.
</p>

<p align="center">
  <img alt="Android 12+" src="https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white">
  <img alt="License GPL-3.0" src="https://img.shields.io/badge/License-GPL--3.0-blue.svg">
</p>

Microband brings Microsoft Band 2 back online on modern Android devices without Microsoft Health, a Microsoft account, analytics, or a replacement cloud service. It communicates directly with the Band over Bluetooth and keeps companion data on the phone.

> [!IMPORTANT]
> Microband is an independent community project. It is not affiliated with, endorsed by, or supported by Microsoft. Microsoft, Microsoft Band, and Microsoft Health are trademarks of Microsoft Corporation.

## Project status

Microband is early-stage software built around reverse-engineered interoperability information. Core setup and everyday companion features work on physical Band 2 hardware, but releases should still be treated as experimental.

| Feature | Status |
| --- | --- |
| Android companion-device association | Working |
| Classic Bluetooth RFCOMM connection | Working |
| Band 2 identification and diagnostics | Working |
| First-run Band setup/OOBE completion | Working |
| UTC, local time, and timezone sync | Working |
| Native Messages and Calls forwarding | Experimental |
| Band notification replies | Experimental |
| Gemini-powered Cortana voice bridge | Experimental |
| Notification filters and privacy controls | Working |
| Theme colors | Working |
| Me Tile wallpaper | Experimental |
| Verified Band 2 firmware update | Experimental |
| Built-in tile enable/disable and ordering | Experimental |
| Daily steps, calories, distance, floors, elevation, and UV | Experimental |
| Latest run/workout/sleep | Experimental |
| Long-term activity and sleep archive import | Planned |
| Health Connect export | Planned |

Microsoft Band 1 is not currently supported.

## Features

- Finds new and already-paired devices named `MSFT Band 2 xx:xx`.
- Associates through Android's secure Companion Device Manager.
- Uses the public SDP-based RFCOMM API—no root access or hidden Bluetooth APIs.
- Inspects PCB, firmware, application, setup, and clock state.
- Safely resumes and completes first-run Band 2 setup.
- Synchronizes both UTC and displayed local time.
- Forwards selected Android notifications into the Band's native Messages tile and sends phone-call lifecycle updates to its Calls tile, with persistent filters, duplicate suppression, rate limiting, locked-phone privacy, and automatic reconnection.
- Maintains the Band's separate reply/voice RFCOMM service, forwards Band replies through the originating Android notification's direct-reply action, and optionally routes Cortana audio or notification dictation through Gemini.
- Reads daily steps, calories, distance, floors, elevation, and UV plus the latest run, exercise, and sleep summaries directly from the Band; daily readings build private week and month charts on the phone.
- Changes the Band's six-color theme using an expanded preset palette or a custom hue, saturation, and brightness picker.
- Center-crops photos locally into the Band 2's 310 × 128 Me Tile format.
- Enables, disables, and reorders the Band's built-in tiles while preserving the essential Me and Settings tiles.
- Installs the archived Band 2 `2.0.5202.0` firmware using an exact size and SHA-256 allowlist, battery preflight, updater recovery path, and post-reboot verification.
- Provides opt-in protocol diagnostics with private notification and wallpaper payloads redacted.

## Requirements

- Microsoft Band 2
- Android 12 or newer (API 31+)
- Bluetooth and Nearby devices access
- Notification access, only if notification forwarding is wanted
- A user-supplied Gemini API key, only if the optional Cortana bridge is wanted

Development requires JDK 17 and an Android SDK containing API 37.

## Installation

Download the latest preview APK from [GitHub Releases](https://github.com/SAM2WOW/Microband/releases), or build from source:

```bash
git clone https://github.com/SAM2WOW/Microband.git
cd Microband
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
git clone https://github.com/SAM2WOW/Microband.git
Set-Location Microband
.\gradlew.bat assembleDebug
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`. Install it with Android Studio or ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## First setup

1. Charge the Band and keep it close to the phone.
2. Open Microband and grant Nearby devices access.
3. Tap **Find my Band** and select the Band in Android's device picker.
4. Confirm the Bluetooth pairing code on both devices if prompted.
5. Tap **Connect**.
6. If the Band is factory-reset, follow **Finish setup**. Microband checks the device model and current OOBE state before sending setup commands.
7. Open **Notifications** to choose which alerts may appear on the Band.
8. Open **Personalize** to select a preset or custom theme color, choose a Me Tile wallpaper, manage built-in tiles, or view the Band's supported Watch Mode options.

To try the experimental voice bridge, open **Settings → Gemini on Cortana**, enter your own Gemini API key, and enable the switch. The key is encrypted with Android Keystore. Band microphone audio is sent to Google's Gemini API only after you explicitly start Cortana or dictation on the Band.

Microband's notification listener runs without keeping the app screen open. On phones with aggressive power management, open **Notifications → Allow background operation**, disable battery optimization for Microband, and allow background activity or Auto-start if the manufacturer provides those controls.

If the Band was previously paired with another phone, remove that pairing from the Band before trying again.

## Privacy

Microband is local-first by design:

- no Microsoft account;
- no analytics or telemetry;
- no custom backend;
- no background cloud upload;
- no advertising SDK;
- notification access is optional;
- notification bodies are never persisted in protocol logs;
- selected wallpaper pixels are processed locally and redacted from protocol logs.

The optional Gemini voice bridge is the sole exception to local-only processing: when enabled and invoked, it sends the current Band microphone recording to the Google Gemini API. It is disabled by default, requires the user's own API key, and never writes voice audio to app storage.

## Firmware safety

Microband can install only the known Band 2 `2.0.5202.0` archive image. It verifies the exact byte length and SHA-256 checksum, requires at least 50% battery, keeps a foreground service and wake lock during transfer, handles the Band's updater stages, and checks device identity, installed version, and firmware assets after reboot.

Firmware installation remains inherently risky: interruption, radio failure, power loss, or an unexpected hardware state can permanently damage a Band. The app requires an explicit responsibility warning before starting. Keep the Band charging and the phone nearby until verification completes. Never alter the allowlisted package or bypass the checks.

## Building and testing

Run unit tests, lint, and a debug build:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The unit tests cover packet framing, status parsing, time conversion, timezone payloads, notification encoding and classification, health decoding, tile-list encoding, personalization colors, and association matching.

A physical Band 2 is required to validate Bluetooth discovery, RFCOMM behavior, OOBE, notification display, health sync, themes, wallpapers, tile changes, and firmware operations.

### Signed release builds

Pushing a `v*` tag runs `.github/workflows/release.yml`, which builds a minified `assembleRelease` APK and publishes it as a GitHub release. The release signing key never lives in the repo: the workflow decodes it from the `MICROBAND_RELEASE_KEYSTORE_BASE64` repository secret and passes the store/key passwords and alias through `MICROBAND_RELEASE_STORE_PASSWORD`, `MICROBAND_RELEASE_KEY_ALIAS`, and `MICROBAND_RELEASE_KEY_PASSWORD`. Without those secrets (e.g. a local `assembleRelease`), the release build type is left unsigned.

## Architecture

```text
Jetpack Compose UI
        │
        ▼
MicrobandViewModel
        │
        ├── BandAssociationManager
        ├── BandConnectionManager
        ├── NotificationListenerService
        └── MicrobandPreferences / Room
                    │
                    ▼
              BandProtocol
                    │
                    ▼
        RFCOMM command + push transports
```

`BandConnectionManager` is the single owner of the active Band transport. Protocol commands are serialized so screens and background notification forwarding do not create competing Bluetooth sockets.

## Contributing

Issues, protocol observations, documentation improvements, and pull requests are welcome.

1. Fork the repository.
2. Create a focused branch: `git switch -c feature/short-description`.
3. Keep hardware mutations safety-gated and Band 2-specific.
4. Add tests for packet formats and pure mapping logic.
5. Run `testDebugUnitTest`, `lintDebug`, and `assembleDebug`.
6. Open a pull request explaining what was tested on real hardware.

Please never include Bluetooth addresses, notification contents, health data, firmware files, signing keys, or other private device data in issues or commits.

## Protocol references and acknowledgements

Microband is an independent Android implementation informed by publicly available interoperability research and community projects, including:

- [libmsftband](https://github.com/ksiazkowicz/libmsftband)
- [msband-lib-9th](https://github.com/MicrosoftBandDev/msband-lib-9th)
- [MicrosoftBandDev/band-sdk](https://github.com/MicrosoftBandDev/band-sdk)
- [MicrosoftBandDev/companion-app](https://github.com/MicrosoftBandDev/companion-app)

These projects have different licenses. Contributors must respect the license and attribution requirements of any source they consult and should document the provenance of newly added protocol behavior.

## License

Microband is licensed under the [GNU General Public License v3.0](LICENSE).

You may use, study, modify, and redistribute it under the terms of that license. Distributed modified versions must make their corresponding source available under GPL-3.0.
