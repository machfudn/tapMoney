<div align="center">

<img src="android/app/src/main/res/drawable/ic_launcher.png" alt="Tap Money Logo" width="96" height="96" />

# Tap Money

[![Android CI Release](https://github.com/machfudn/tap-money-info/actions/workflows/build-release-apk.yml/badge.svg)](https://github.com/machfudn/tap-money-info/actions/workflows/build-release-apk.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg?logo=kotlin)](#)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.10.01-4285F4.svg?logo=android)](#)
[![Target SDK](https://img.shields.io/badge/targetSdk-35%20(Android%2015)-green.svg)](#)
[![Min SDK](https://img.shields.io/badge/minSdk-24%20(Android%207.0)-orange.svg)](#)
[![License](https://img.shields.io/badge/license-MIT-black.svg)](#)

A pure native Android (100% Kotlin & Jetpack Compose) electronic money NFC reader for extracting real stored balance, physical card PAN, and on-chip mutation histories.

---

[Dokumentasi Bahasa Indonesia (README.md)](README.md)

---

</div>

## Table of Contents
- [Overview](#overview)
- [Key Features](#key-features)
- [Supported Cards](#supported-cards)
- [Architecture & Source Tree](#architecture--source-tree)
- [Technical Specifications & APDU Decoding](#technical-specifications--apdu-decoding)
- [Prerequisites & Development Setup](#prerequisites--development-setup)
- [Build & Run](#build--run)
- [Release Build & APK Signing](#release-build--apk-signing)
- [Author](#author)

---

## Overview

**Tap Money** is a modern, 100% native Android mobile application built using **Kotlin 2.0.21** and **Jetpack Compose**. Unlike simplistic tag scanner apps that merely read serial UID numbers, Tap Money establishes direct duplex communication with physical smartcard chips using the **ISO-DEP (ISO 7816-4)** and **FeliCa (JIS 6319-4)** protocols to read real live balances, physical card identifiers (PAN), and transaction logs from Indonesian banking cards.

All data is stored locally in an on-device **SQLite database** (`tap_money.db`), providing zero tracking, full offline-first functionality, and strict data privacy.

> [!NOTE]
> All APDU transceiver interactions are strictly **read-only**. The application does not write or tamper with card balances or cryptographic keys.

---

## Key Features

- **Pure Native Kotlin**: Built entirely with Kotlin 2.0 and Jetpack Compose without webview wrappers or hybrid bridges.
- **Physical Chip Interrogation**: Executes low-level APDU binary commands directly against smartcard microcontrollers.
- **Dual Communication Standards**:
  - **ISO-DEP (ISO 7816-4)**: Mandiri e-Money, BCA Flazz, BRI Brizzi, BNI TapCash, Bank Mega MegaCash, Bank DKI JakCard.
  - **NfcF / FeliCa (JIS 6319-4)**: KAI Commuter Multi Trip Card (KMT).
- **On-Chip Transaction History**: Decodes real on-chip records (transaction type, debited/credited amount, remaining balance, timestamp, and Terminal ID).
- **Grouped Date Presentation**: Neatly groups transaction lists by calendar date with Indonesian Rupiah currency formatting (`id-ID`).
- **Offline-First Persistence**: Retains scanned cards and custom terminal labels in a local SQLite database.
- **Terminal Alias Management**: In-app prompt to assign readable names to numeric terminal identifiers (e.g. toll gates, bus stops).
- **Neobrutalist Visual Identity**: High-contrast UI featuring bold black outlines, solid offset shadows, vivid accent colors, and comprehensive Dark Mode support.
- **Haptic & Audio Signals**: Immediate haptic vibration and audio feedback upon successful card read.

---

## Supported Cards & Segmentation

### 1. Indonesian Banking (Domestic)
| Card | Issuer | Protocol | Real Balance | Mutation History | Physical Card Number |
| :--- | :--- | :--- | :---: | :---: | :---: |
| **Mandiri e-Money** | Bank Mandiri | ISO-DEP (ISO 7816-4) | Yes | Yes (Records 1–10) | Yes (`6032...`) |
| **BCA Flazz (Gen 1 & 2)** | Bank BCA | PBOC / DESFire | Yes | Yes (Gen 2 Log Decode) | Yes (`6013...` / `6019...`) |
| **BNI TapCash** | Bank BNI | ISO-DEP (CEPAS variant) | Yes | Yes (1995 Epoch) | Yes (`7546...`) |
| **BRI Brizzi** | Bank BRI | Mifare DESFire | Yes | Yes (Multi-frame Chaining) | Yes (`5058...`) |
| **Bank DKI JakCard** | Bank DKI | ISO-DEP | Yes | Yes | Yes |
| **Bank Mega MegaCash** | Bank Mega | ISO-DEP | Yes | Partial | Yes (`6020...`) |

### 2. Domestic Transit
| Card | Issuer | Protocol | Real Balance | Mutation History | Station Catalog |
| :--- | :--- | :--- | :---: | :---: | :---: |
| **KMT Commuter Line** | PT Kereta Commuter Indonesia | NfcF / FeliCa (0x90B7) | Yes | Yes (Jakarta 2000 Epoch) | Yes (Jabodetabek Stations) |
| **MRT Jakarta (MRTJ)** | PT MRT Jakarta | NfcF / FeliCa (0x9373) | Yes | Yes (Last Transaction) | Yes |

### 3. Regional / International Transit (Category A - Fully Open)
| Card | Region | Protocol | Real Balance | Mutation History |
| :--- | :--- | :--- | :---: | :---: |
| **CEPAS (EZ-Link / NETS FlashPay)** | Singapore | ISO-DEP (CLA 0x90) | Yes (SGD Cents) | Yes |
| **Suica / PASMO / ICOCA** | Japan | FeliCa (Service 0x090F) | Yes (JPY Yen) | Yes (20 blocks) |
| **Rakuten Edy** | Japan | FeliCa (Service 0x1317 & 0x110B) | Yes (JPY Yen) | Yes |
| **T-Money & Cashbee** | South Korea | ISO-DEP (AID D4100000030001 / KSX6924) | Yes (KRW Won) | Yes |
| **China Transit (T-Union / PBOC)** | China | PBOC 2.0/3.0 (AID A000000632010105) | Yes (CNY Cents) | Yes |
| **Sydney Opal** | Australia | MIFARE DESFire EV1 (App 0x314553 File 0x07) | Yes (AUD Cents) | Mode & Transit Status |

---

## Architecture & Source Tree

The codebase is structured under modular Kotlin packages:

```text
tap-money-info/
├── android/
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml          # Activity, NFC, and vibration permissions
│   │   │   ├── java/id/tapmoney/
│   │   │   │   ├── MainActivity.kt          # ReaderCallback, lifecycle, and state coordination
│   │   │   │   ├── nfc/
│   │   │   │   │   └── NfcEngine.kt         # Low-level APDU dispatcher & binary decoders
│   │   │   │   ├── model/
│   │   │   │   │   └── CardModels.kt        # Card and transaction data models
│   │   │   │   ├── data/
│   │   │   │   │   └── CardDatabase.kt      # Local SQLite database helper
│   │   │   │   └── ui/
│   │   │   │       ├── MainScreen.kt        # Jetpack Compose UI layout and state flows
│   │   │   │       └── NeobrutalismComponents.kt # Neobrutalist design modifiers & styling
│   │   │   └── res/
│   │   │       ├── drawable/                # Bank logos, card backgrounds, and iconography
│   │   │       └── values/                  # Color palettes and theme declarations
│   │   └── build.gradle                     # App Gradle build configuration (Compose, Java 21)
│   ├── build.gradle                         # Root project buildscript
│   ├── settings.gradle                      # Gradle settings
│   └── gradle.properties                    # JVM options and Kotlin arguments
├── README.md                                # Indonesian Documentation
└── README.en.md                             # English Documentation
```

---

## Technical Specifications & APDU Decoding

### 1. BCA Flazz Balance Calculation
Uses the PBOC Get Balance command:
```text
80 5C 00 02 04
```
Calculates balance from 3 bytes Big-Endian integer data:
$$\text{Balance} = (B_1 \times 2^{16}) + (B_2 \times 2^8) + B_3$$

### 2. Mandiri e-Money APDU Sequence
- **Select AID**: `00 A4 04 00 08 00 00 00 00 00 00 00 01`
- **Read PAN**: `00 B3 00 00 3F`
- **Read Balance**: `00 B5 00 00 0A` (4-byte Little-Endian unsigned integer)
$$\text{Balance} = B_0 + (B_1 \times 2^8) + (B_2 \times 2^{16}) + (B_3 \times 2^{24})$$

### 3. BNI TapCash Timestamp Calculation
Decodes seconds offset against the base epoch of January 1, 1995 UTC ($788,893,200\text{ seconds}$ from Unix epoch):
$$T_{\text{ms}} = (S_{\text{raw}} \times 1000) + 788,893,200,000$$

### 4. KMT Commuter Line (FeliCa JIS 6319-4)
Executes *Read Without Encryption* command (`0x06`) targeting Service Code `0x220F` block `0x8000`. Balance is parsed as a Little-Endian 3-byte integer at byte offsets 13, 14, and 15.

---

## Prerequisites & Development Setup

- **Android Studio**: Android Studio Koala (2024.1.1) or higher.
- **JDK**: Java Development Kit 21 (LTS).
- **Target OS**: Android 7.0 (API 24) through Android 15 (API 35).
- **Hardware**: NFC-enabled Android physical smartphone.

---

## Build & Run

### 1. Navigate to Android Workspace
```bash
cd tap-money-info/android
```

### 2. Compile Debug APK
```bash
./gradlew assembleDebug
```
The resulting APK will be saved at:
```text
android/app/build/outputs/apk/debug/app-debug.apk
```

### 3. Install to Connected Device
Ensure USB Debugging is enabled on your Android smartphone:
```bash
./gradlew installDebug
```

---

## Release Build & APK Signing

Compile the release variant:
```bash
./gradlew assembleRelease
```

Sign the output package using `apksigner`:
```bash
apksigner sign --ks release.keystore --ks-pass pass:YOUR_PASSWORD --out TapMoney-v${VERSION_NAME}.apk android/app/build/outputs/apk/release/app-release-unsigned.apk
```

Verify package integrity:
```bash
apksigner verify --verbose TapMoneyInfo.apk
```

---

## Author

**Machfudin**  
- System Architect & Lead Developer  
- GitHub: [@machfudn](https://github.com/machfudn)
