<div align="center">

<img src="android/app/src/main/res/drawable/ic_launcher.png" alt="Tap Money Logo" width="96" height="96" />

# Tap Money

[![Android CI Release](https://github.com/machfudn/tapMoney/actions/workflows/build-release-apk.yml/badge.svg)](https://github.com/machfudn/tapMoney/actions/workflows/build-release-apk.yml)
[![Version](https://img.shields.io/badge/version-v1.1.10-blue.svg)](#)
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

**Tap Money** is a modern, 100% native Android mobile application built using **Kotlin 2.0.21** and **Jetpack Compose**. Unlike simplistic tag scanner apps that merely read serial UID numbers, Tap Money establishes direct duplex communication with physical smartcard chips using the **ISO-DEP (ISO 7816-4)** and **FeliCa (JIS 6319-4)** protocols to read real live balances, physical card identifiers (genuine 16-digit PAN), and on-chip transaction mutation logs from Indonesian banking and transit smartcards.

All data is stored locally in an on-device **SQLite database** (`tap_money.db`), providing zero tracking, full offline-first functionality, and strict data privacy.

> [!NOTE]
> All APDU transceiver interactions are strictly **read-only**. The application does not write or tamper with card balances or cryptographic keys.

---

## Key Features

- **Pure Native Kotlin**: Built entirely with Kotlin 2.0 and Jetpack Compose without webview wrappers or hybrid bridges.
- **Physical Chip Interrogation**: Executes low-level APDU binary commands directly against smartcard microcontrollers for accurate stored balances.
- **Dual Communication Standards**:
  - **ISO-DEP (ISO 7816-4)**: Mandiri e-Money, BCA Flazz, BRI Brizzi, BNI TapCash, Bank Mega MegaCash, Bank DKI JakCard.
  - **NfcF / FeliCa (JIS 6319-4)**: KAI Commuter Multi Trip Card (KMT), MRT Jakarta, Suica, PASMO, ICOCA.
- **Genuine 16-Digit PAN Extraction**: Decodes authentic card PAN numbers from ISO/IEC 7813 Track 2 ASCII, BCD records, and DESFire info files without arbitrary dummy digits.
- **On-Chip Transaction Mutation History**: Decodes real on-chip records (transaction type, debited/credited amount, remaining balance, timestamp, and Terminal ID).
- **Infinite Scroll & Strictly Descending Sorting**: Seamless unpaginated scrolling for full chip transaction logs, sorted in strict descending order (`yyyy-MM-dd HH:mm:ss`).
- **Bilingual Grouped Date Presentation**: Neatly groups transaction lists by calendar date with localized date headers in Indonesian and English.
- **Document Export & Receipt Printing**:
  - **Neobrutalist A4 PDF Documents**: Generates high-resolution vector PDF export summaries with status badges and transaction tables.
  - **58mm Bluetooth Thermal Receipts**: Monospaced 32-column ESC/POS receipt formatter for portable POS thermal printers.
  - **Clipboard Copy & Share**: Instant text summary clipboard export and Android system share intent integration.
- **3-Tier Offline Terminal & Location Catalog**: Offline catalog mapping Terminal IDs to toll gates, KRL/MRT/LRT stations, TransJakarta stops, and retail stores, with automatic masking for internal codes like `HT0`.
- **Self-Contained Bilingual Localization (i18n)**: Indonesian and English support via asset JSON dictionaries (`assets/lang/id.json` & `en.json`) with interactive dropdown language selector in Settings.
- **Neobrutalist Visual Identity**: High-contrast UI featuring bold black outlines (`#111827`), solid offset shadows (`neoCard`), authentic corporate card brand palettes, and high-contrast Dark Mode.
- **Haptic & Audio Signals**: Responsive haptic feedback and distinct notification sounds upon successful card detection.

---

## Supported Cards & Segmentation

### 1. Indonesian Banking (Domestic)
| Card | Issuer | Protocol | Real Balance | Mutation History | Physical Card Number (PAN) |
| :--- | :--- | :--- | :---: | :---: | :---: |
| **Mandiri e-Money** | Bank Mandiri | ISO-DEP (ISO 7816-4) | Yes | Yes (Scan 64 blocks 00D1 + SFI Cyclic 1..30) | Yes (`6032...`) |
| **BCA Flazz (Gen 1 & 2)** | Bank BCA | PBOC / DESFire | Yes | Yes (Gen 2 Log Decode epoch 1980 WIB) | Yes (`6013...` / `6019...`) |
| **BNI TapCash** | Bank BNI | ISO-DEP (CEPAS variant) | Yes | Yes (1995 Epoch Descending) | Yes (`7546...`) |
| **BRI Brizzi** | Bank BRI | Mifare DESFire EV1 | Yes (Multi-file + Recovery) | Yes (DESFire Cyclic Record 32-byte BCD) | Yes (`5221...` / `6013...` / `5379...`) |
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
tapMoney/
├── android/
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml              # Manifest, NFC, VIBRATE, & BLUETOOTH permissions
│   │   │   ├── assets/lang/                     # Standalone bilingual JSON assets
│   │   │   │   ├── id.json                      # Indonesian translations
│   │   │   │   └── en.json                      # English translations
│   │   │   ├── java/id/tapmoney/
│   │   │   │   ├── MainActivity.kt              # Controller, ReaderCallback, lifecycle, haptics
│   │   │   │   ├── data/
│   │   │   │   │   ├── CardDatabase.kt          # SQLite database (saved cards & terminal aliases)
│   │   │   │   │   └── TerminalCatalog.kt       # Offline toll gate, railway station, & retail catalog
│   │   │   │   ├── model/
│   │   │   │   │   └── CardModels.kt            # Data models (CardBalanceResult, CardTransactionItem)
│   │   │   │   ├── nfc/
│   │   │   │   │   ├── ApduChannel.kt           # ISO-DEP APDU transceiver abstraction
│   │   │   │   │   ├── CardParser.kt            # Card parsing registry & dispatcher engine
│   │   │   │   │   ├── NfcEngine.kt             # Hardware ReaderCallback & tag routing
│   │   │   │   │   ├── TxDates.kt               # BCD, epoch 1980 WIB, & epoch 1995 decoders
│   │   │   │   │   └── parser/
│   │   │   │   │       ├── BrizziParser.kt      # BRI Brizzi DESFire EV1 parser (PAN, LE Bal, BCD log)
│   │   │   │   │       ├── CepasParser.kt       # Singapore CEPAS (EZ-Link / NETS FlashPay)
│   │   │   │   │       ├── ChinaTransitParser.kt # China T-Union / PBOC parser
│   │   │   │   │       ├── FelicaParser.kt      # Japan Suica, PASMO, ICOCA, Edy parser
│   │   │   │   │       ├── FlazzParser.kt       # BCA Flazz Gen 1 & 2 (Track 2, epoch 1980 WIB)
│   │   │   │   │       ├── JakCardParser.kt     # Bank DKI JakCard parser
│   │   │   │   │       ├── MandiriParser.kt     # Mandiri e-Money (64 blocks 00D1 & SFI 1..30)
│   │   │   │   │       ├── MegaCashParser.kt    # Bank Mega MegaCash parser
│   │   │   │   │       ├── OpalParser.kt        # Sydney Opal parser
│   │   │   │   │       ├── TapCashParser.kt     # BNI TapCash (1995 epoch descending)
│   │   │   │   │       ├── TMoneyParser.kt      # Telkom T-Money & South Korea T-Money parser
│   │   │   │   │       └── transit/
│   │   │   │   │           ├── KmtDateDecoder.kt    # KMT Jakarta 2000 epoch decoder
│   │   │   │   │           └── KmtStationCatalog.kt # Jabodetabek commuter station database
│   │   │   │   ├── ui/
│   │   │   │   │   ├── AppStrings.kt            # Standalone i18n parser without runtime JSON deps
│   │   │   │   │   ├── MainScreen.kt            # Compose UI layout, active cards, infinite scroll
│   │   │   │   │   ├── NeobrutalismComponents.kt # Neobrutalist design modifiers, palettes, styling
│   │   │   │   │   └── SettingsScreen.kt        # Full settings tab, language selector, theme, switches
│   │   │   │   └── util/
│   │   │   │       ├── BluetoothPrinter.kt      # 58mm ESC/POS Bluetooth thermal printer driver
│   │   │   │       ├── ReceiptExporter.kt       # Neobrutalist A4 PDF document generator
│   │   │   │       └── ReceiptFormatter.kt      # Monospaced 32-column thermal receipt formatter
│   │   │   └── res/
│   │   │       ├── drawable/                    # Bank vector logos, brand palettes, & icons
│   │   │       └── values/                      # System themes, colors, & fallback strings
│   │   └── build.gradle                         # App Gradle build configuration (Compose, Java 21)
│   ├── build.gradle                             # Root project buildscript (Kotlin 2.0.21, AGP 8.13.0)
│   ├── settings.gradle                          # Gradle settings
│   ├── version.properties                       # Dynamic release version config (v1.1.10 / 10110)
│   ├── RELEASE_NOTES.md                         # Release notes for v1.1.10
│   └── gradle.properties                        # JVM options and Kotlin compiler flags
├── README.md                                    # Indonesian Documentation
└── README.en.md                                 # English Documentation
```

---

## Technical Specifications & APDU Decoding

### 1. BCA Flazz Balance & Transaction Calculation
- **Get Balance**: `80 5C 00 02 04` (3-byte Big-Endian unsigned integer):
  $$\text{Balance} = (B_1 \times 2^{16}) + (B_2 \times 2^8) + B_3$$
- **Physical PAN (Gen 2)**: Reads ISO/IEC 7813 Track 2 via `00 B0 81 00 8E`. The initial 16 characters yield the card's physical PAN (`6013...` / `6019...`).
- **Mutation Timestamp (Gen 2)**: 4-byte unsigned integer offset relative to January 1, 1980 00:00:00 WIB (UTC+7 / `Asia/Jakarta`).

### 2. Mandiri e-Money APDU Sequence
- **Select AID**: `00 A4 04 00 08 00 00 00 00 00 00 00 01`
- **Read PAN**: `00 B3 00 00 3F`
- **Read Balance**: `00 B5 00 00 0A` (4-byte Little-Endian unsigned integer):
  $$\text{Balance} = B_0 + (B_1 \times 2^8) + (B_2 \times 2^{16}) + (B_3 \times 2^{24})$$
- **Deep Mutation Scan**: Interrogates 64 binary file blocks `00 D1 [block] 00 00` combined with cyclic SFI records `00 B2 [rec] 24 00` & `44 00` (records 1..30) with deduplication.

### 3. BRI Brizzi DESFire EV1 APDU
- **Select Application**: `90 5A 00 00 03 01 00 00 00` (AID `010000`, alternative `160000`).
- **Read PAN**: `90 BD 00 00 07 00 00 00 00 17 00 00 00` (File 0 / 1). 16-digit BCD PAN extracted from hex substring indices 6..22 and validated against official Brizzi BIN prefixes.
- **Read Balance**: DESFire Get Value `90 6C 00 00 01 [File] 00` (4-byte Little-Endian integer). If protected, balance auto-recovers from the latest on-chip transaction remaining balance.
- **Read History**: DESFire Read Records `90 BB 00 00 07 01 00 00 00 00 00 00 00` + chaining `90 AF 00 00 00`. Each record is 32 bytes (64 hex characters):
  - Chars 32..37: BCD Date `ddMMyy`
  - Chars 38..43: BCD Time `HHmmss`
  - Chars 44..45: Transaction type code (`EB` = Payment, `EC` = Top Up)
  - Chars 46..51: Transaction amount (3-byte Little-Endian)
  - Chars 52..59: Remaining transaction balance (4-byte Little-Endian)

### 4. BNI TapCash Timestamp Calculation
Decodes seconds offset against the base epoch of January 1, 1995 UTC ($788,893,200\text{ seconds}$ from Unix epoch):
$$T_{\text{ms}} = (S_{\text{raw}} \times 1000) + 788,893,200,000$$

### 5. KMT Commuter Line (FeliCa JIS 6319-4)
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
cd tapMoney/android
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
apksigner sign --ks release.keystore --ks-pass pass:YOUR_PASSWORD --out TapMoney-v1.1.10.apk android/app/build/outputs/apk/release/app-release-unsigned.apk
```

Verify signature integrity:
```bash
apksigner verify --verbose TapMoney-v1.1.10.apk
```

---

## Author

**Machfudin**  
- Software Engineer  
- GitHub: [@machfudn](https://github.com/machfudn)
