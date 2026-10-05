<div align="center">

<img src="assets/branding/kvieta-mark.svg" alt="Kvieta logo" width="132" />

# Kvieta Mobil

### All in good time.

A calm, local-first Android companion for Kvieta on Windows.

[**Türkçe**](docs/README.tr.md)

![Android](https://img.shields.io/badge/Android-native-87946B?style=flat-square&labelColor=292B26)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-87946B?style=flat-square&labelColor=292B26)
![Compose](https://img.shields.io/badge/UI-Jetpack_Compose-C9B98E?style=flat-square&labelColor=292B26)
![Privacy](https://img.shields.io/badge/privacy-local--first-87946B?style=flat-square&labelColor=292B26)
![Status](https://img.shields.io/badge/status-Alpha_1.1-C9B98E?style=flat-square&labelColor=292B26)
![License](https://img.shields.io/badge/license-MIT-87946B?style=flat-square&labelColor=292B26)

</div>

Kvieta Mobil is the native Android companion for [Kvieta for Windows](https://github.com/Rel0adediso/Kvieta-app). It brings screen-time awareness, remote session management, and extra-time approvals directly to your pocket while keeping your data and policies on your own devices. No cloud account required.

## Download Kvieta Mobil Alpha 1.1

[**Download Kvieta Mobil APK for Android**](https://github.com/Rel0adediso/kvieta-mobile/releases/download/alpha-1.1/kvieta-mobile-alpha-1.1.apk)

Compatible with Android 8.0 (API 26) and newer. Built with Jetpack Compose and Material 3, adhering faithfully to Kvieta's olive-green and warm stone visual language.

The release notes, APK checksums, and assets are available on the [Kvieta Mobil Alpha 1.1 release page](https://github.com/Rel0adediso/kvieta-mobile/releases/tag/alpha-1.1).

> **Important:** Kvieta Mobil is an optional companion. All core screen time limits, schedules, and application rules run securely on the Windows PC.

## Choose how you interact with your PC

| Feature | Role | Experience |
|---|---|---|
| **Remote Session Control** | Stepping away from your desk | Instantly lock your PC or start a calm rest break with one tap. |
| **Extra-Time Approvals** | Responding to session requests | Approve or decline extensions from your phone with biometrics or PC PIN. |
| **Live Usage Dashboard** | Keeping screen time visible | Glance at remaining daily minutes, current focus mode, and category breakdown. |
| **Web Guard Management** | Distraction-free browsing | Remotely adjust domain restrictions and supervision rules. |

## What Kvieta Mobil can do

| | |
|---|---|
| **Observe usage** | Real-time session status, remaining daily time, and top application usage breakdown. |
| **Remote control** | One-tap session lock, rest break trigger, and session resumption without sitting at your PC. |
| **Approve requests** | Biometric fingerprint verification or Windows administrator PIN (PBKDF2-SHA256) validation. |
| **Pair seamlessly** | Built-in camera scanner for desktop QR enrollment over local Wi-Fi or encrypted relay. |
| **Protect privacy** | Device secrets secured in Android Keystore; end-to-end authenticated requests and zero cloud storage. |
| **Adapt calmly** | Seamless light and dark mode, dynamic system insets, and gentle haptic feedback. |

## How pairing works

1. Open **Kvieta for Windows** on your PC and click **Telefon Bağlama** (Phone Pairing).
2. Open **Kvieta Mobil** on your Android device and tap **Scan QR Code**.
3. Point your camera at the screen to establish a verified connection over Wi-Fi or secure encrypted relay.
4. Your phone is now paired. You can revoke access at any time from your PC.

## New in Kvieta Mobil Alpha 1.1

- **Biometric or PIN Verification**: Approve extra-time requests using your phone's fingerprint sensor or your PC PIN.
- **Kvieta-Themed PIN Dialog**: Calm, custom PIN entry dialog with responsive error handling and smooth transitions.
- **Fast QR Code Scanner**: Instant camera scanning with flashlight support and fallback manual code entry.
- **Remote Lock & Break Synchronization**: Accelerated state updates and real-time session feedback.
- **Web Guard Remote Supervision**: Remotely inspect and configure site filtering rules.

## Private by design

Kvieta Mobil connects directly to your Windows computer over the local network or through a private, end-to-end encrypted relay. No accounts, no third-party telemetry, and no centralized databases. Secret keys never leave your phone's hardware Keystore.

## Project status

**Kvieta Mobil Alpha 1.1 is the current companion preview.**

- Native Android source built with modern Kotlin and Jetpack Compose.
- Automated unit tests, TLS pinning validation, Keystore cryptography, and UI interaction checks.
- Compatible with [Kvieta for Windows Alpha 6.4](https://github.com/Rel0adediso/Kvieta-app/releases/tag/kvieta-alpha-6.4).

## Build from source

Requirements: JDK 17 and Android SDK Platform 36.

```powershell
.\gradlew.bat :app:assembleDebug
```

Run test suite and lint checks:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

<details>
<summary><strong>Project structure</strong></summary>

| Component | Responsibility |
|---|---|
| `ui/` | Jetpack Compose screens: connection, dashboard, approval dialogs, and themes |
| `connection/` | Local HTTPS & encrypted relay transport, pairing flow, and state sync |
| `security/` | Keystore key management, PIN verification (PBKDF2-SHA256), and biometric authentication |
| `widget/` | Android home screen glanceable session widget |

</details>

## Security boundary

Kvieta Mobil acts as an authorized companion to Kvieta for Windows. Approvals are cryptographically signed, challenge-response verified, and origin-checked by the Windows PC. Physical possession of the phone and biometrics or PIN is required to issue approvals.

## Documentation

- [Türkçe kullanım ve geliştirici rehberi](docs/README.tr.md)
- [Kvieta for Windows repository](https://github.com/Rel0adediso/Kvieta-app)
- [Official Website](https://kvieta.app)

## Development approach

**Human-directed product · AI-assisted development.** Product direction, UX decisions, and testing are led by [Rel0adediso](https://github.com/Rel0adediso). Architecture, implementation, and test development are carried out with AI assistance.

Kvieta Mobil is open-source software released under the [MIT License](LICENSE).

---

<div align="center">

**Kvieta Mobil** · *All in good time.*

</div>
