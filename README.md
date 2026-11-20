<div align="center">

<img src="app/src/main/res/drawable/linkpad_icon_minimal_1795097404067.jpg" alt="LinkPad Logo" width="112" />

# LinkPad

**Low-latency wireless gamepad & 4-player couch receiver for Android and Windows PC.**

[![Latest Release](https://img.shields.io/github/v/release/Nimen-star/linkpad-app?display_name=tag&style=for-the-badge&color=1F6FEB)](https://github.com/Nimen-star/linkpad-app/releases/latest)
[![CI Build](https://img.shields.io/github/actions/workflow/status/Nimen-star/linkpad-app/ci.yml?branch=main&style=for-the-badge&label=CI%20Build)](https://github.com/Nimen-star/linkpad-app/actions/workflows/ci.yml)
[![Platform](https://img.shields.io/badge/Android_7.0%2B_%7C_Windows_10%2F11-2EA043?style=for-the-badge)](#downloads)
[![License](https://img.shields.io/github/license/Nimen-star/linkpad-app?style=for-the-badge&color=6E7681)](LICENSE)

[Downloads](#downloads) • [Connection Modes](#connection-modes) • [Features](#features) • [Quick Start](#quick-start) • [Building & Releases](#building--releases)

</div>

---

## Downloads

Get the latest compiled Android APK and Windows PC executables directly from **[GitHub Releases](https://github.com/Nimen-star/linkpad-app/releases/latest)**:

| Artifact | Platform | Notes |
| --- | --- | --- |
| **`LinkPad-Android.apk`** | Android 7.0+ (API 24+) | Controller & 4-Player Host app (also pre-exported in [`releases/LinkPad-Android.apk`](releases/LinkPad-Android.apk)) |
| **`LinkPadHostSetup.exe`** | Windows 10 / 11 | Single-file Windows setup wizard with Start Menu shortcut & uninstaller |
| **`LinkPadHost.exe`** | Windows 10 / 11 | Standalone portable native .NET 4.8 executable |

> Need a preview build before tagging a release? Download **`ci-linkpad-android-apk`** or **`ci-linkpad-windows-host`** from the latest run on the [Actions tab](https://github.com/Nimen-star/linkpad-app/actions).

---

## Connection Modes

LinkPad adapts to whatever screen or device you are playing on:

| Target Device | Transport | Virtual Controller Behaviour | Requires Root / IME Switch? |
| --- | --- | --- | --- |
| **Windows 10 / 11 PC** | Wi-Fi LAN (`UDP 47850`) | Native **LinkPad P1–P4** Xbox 360 XInput gamepads via `ViGEmBus` (or Keyboard scancode mode) | No |
| **Android TV / Phone / Browser** | **Bluetooth OS Gamepad** (Bluetooth HID Profile) | Standard OS-level HID Gamepad (`Report ID 1`: 16 buttons, D-Pad hat, L/R sticks, L2/R2) | **No** (no Gboard switch, no Accessibility service) |
| **LinkPad Android Host** | Wi-Fi LAN or Bluetooth RFCOMM | Up to **4 phones** connected to one Host phone with live slot assignment & telemetry | No |

---

## Features

- **120 Hz Low-Latency Input Protocol** — Compact 16-byte binary frames with multicast UDP host discovery (`239.255.77.77:47851`) and manual IP fallback.
- **Native PC Controller Identity** — `LinkPadHost.cs` installs zero Python dependencies and shows up cleanly as **`LinkPad P1` – `LinkPad P4`** with interactive Windows tray/desktop controls and Trusted Device favorites.
- **Bluetooth OS Gamepad (HID)** — Pairs once via standard Android Bluetooth HID without switching keyboards or enabling invasive Accessibility services, working across native games, emulators, and browser gamepad testers.
- **Dual Layouts & Customization** — Switch seamlessly between **Dual Analog Stick** and **Retro Arcade** pad layouts with customizable deadzone, opacity, vibration feedback, and optional gyro/accelerometer tilt.
- **Privacy & Pairing Guardrails** —
  - **Tap-to-Reveal IP Chips** keep your local IP masked (`•••.•••.•••.•••`) until tapped.
  - **6-Digit Pairing Codes** + real SDP service probing verify genuine LinkPad Hosts before pairing.
  - **Zero Invasive Permissions** — No Accessibility Service, no Input Method Service, no broad storage permissions.

---

## Quick Start

### 1. Play on Windows PC
1. Download and run **`LinkPadHostSetup.exe`** (or portable **`LinkPadHost.exe`**) on your Windows PC.
2. For full Xbox 360 controller emulation (`XInput`), install the [ViGEmBus Driver](https://github.com/nefarius/ViGEmBus/releases) when prompted (Keyboard mode works without drivers).
3. Install **`LinkPad-Android.apk`** on your Android phone, open **Connect to Host**, and select your PC or enter the 6-digit pairing code.

### 2. Play on Android TV, Tablet, Phone, or Browser
1. Open **Bluetooth OS Gamepad** inside LinkPad on your controller phone.
2. Pair your controller phone with the target Android TV / Phone / PC in system Bluetooth settings.
3. Tap the paired target inside LinkPad (`OS Controller`) — inputs register immediately as a standard hardware gamepad without changing keyboard input methods.

### 3. Host a 4-Player Session on Android
1. Open **Host Mode** on the primary Android device.
2. Share the 6-digit pairing code (and tap the IP badge if entering manually over Wi-Fi).
3. Up to 4 players (`P1`–`P4`) join simultaneously over Wi-Fi or Bluetooth.

---

## Building & Releases

### Android App (`app/`)
```bash
# Decode debug keystore if not present locally
base64 --decode debug.keystore.base64 > debug.keystore

# Run unit tests & build APK
gradle :app:testDebugUnitTest :app:assembleDebug
```

### Windows PC Host (`pc-host/`)
- Open `pc-host/LinkPadHost.csproj` in Visual Studio / MSBuild (`.NET Framework 4.8`), or double-click `pc-host/Start_LinkPadHost.bat` on Windows to compile and launch directly with built-in `csc.exe`.
- Build the Windows setup wizard with Inno Setup 6:
  ```powershell
  msbuild pc-host\LinkPadHost.csproj /t:Restore,Build /p:Configuration=Release
  iscc /DBuildOutputDir="..\bin\Release\net48" pc-host\installer\LinkPadHost.iss
  ```

### Automated GitHub Releases (`.apk` + `.exe`)
The workflow in [`.github/workflows/release.yml`](.github/workflows/release.yml) automatically builds **`LinkPad-Android.apk`**, **`LinkPadHostSetup.exe`**, and **`LinkPadHost.exe`**, generates SHA-256 checksums, and attaches all binaries to a GitHub Release whenever:
1. You push to `main` (creates/updates the release matching `versionName` in `app/build.gradle.kts`, e.g. `v1.3.0`),
2. You push a version tag (e.g. `git tag v1.3.0 && git push origin v1.3.0`), **or**
3. You click **Actions → Build & Publish LinkPad Releases → Run workflow** on GitHub.

> **Optional Release Signing (Recommended for Public Distribution):**
> To sign `LinkPad-Android.apk` with your own upload key instead of the debug keystore (eliminating Android Play Protect unknown-signer warnings), add these three repository secrets under **Settings → Secrets and variables → Actions**:
> - `KEYSTORE_BASE64` — Base64 string of your `.jks` keystore (`base64 -w 0 my-upload-key.jks`) with key alias `upload`
> - `STORE_PASSWORD` — Keystore password
> - `KEY_PASSWORD` — Key password

---

## Repository Structure

```text
├── app/                     # Kotlin + Jetpack Compose Android app (Controller, Host & BT HID)
├── pc-host/                 # Native C# (.NET 4.8) Windows PC Host & Inno Setup installer script
│   ├── LinkPadHost.cs
│   ├── LinkPadHost.csproj
│   └── installer/LinkPadHost.iss
├── releases/                # Pre-built Android APK & release notes
└── .github/workflows/       # Automated CI builds & GitHub Release publisher (APK + EXE)
```

---

## License

Licensed under the [MIT License](LICENSE).
