<div align="center">

<img src="android/app/src/main/res/drawable/ic_launcher.png" alt="Logo Tap Money Info" width="96" height="96" />

# Tap Money Info

[![Android CI Release](https://github.com/machfudn/tap-money-info/actions/workflows/build-release-apk.yml/badge.svg)](https://github.com/machfudn/tap-money-info/actions/workflows/build-release-apk.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg?logo=kotlin)](#)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.10.01-4285F4.svg?logo=android)](#)
[![Target SDK](https://img.shields.io/badge/targetSdk-35%20(Android%2015)-green.svg)](#)
[![Min SDK](https://img.shields.io/badge/minSdk-24%20(Android%207.0)-orange.svg)](#)
[![License](https://img.shields.io/badge/license-MIT-black.svg)](#)

Aplikasi Android native murni (100% Kotlin & Jetpack Compose) untuk membaca saldo riil, nomor kartu fisik (PAN), dan mutasi transaksi chip uang elektronik berbasis NFC (ISO-DEP & FeliCa).

---

[English Documentation (README.en.md)](README.en.md)

---

</div>

## Daftar Isi
- [Ringkasan](#ringkasan)
- [Fitur Utama](#fitur-utama)
- [Daftar Kartu yang Didukung](#daftar-kartu-yang-didukung)
- [Arsitektur & Struktur Proyek](#arsitektur--struktur-proyek)
- [Spesifikasi Teknis & Parsing APDU](#spesifikasi-teknis--parsing-apdu)
- [Prasyarat Sistem](#prasyarat-sistem)
- [Panduan Build & Menjalankan](#panduan-build--menjalankan)
- [Build Rilis & Signing APK](#build-rilis--signing-apk)
- [Author](#author)

---

## Ringkasan

**Tap Money Info** adalah aplikasi mobile Android native modern yang dikembangkan secara penuh menggunakan bahasa **Kotlin 2.0.21** dan toolkit UI **Jetpack Compose**. Berbeda dari aplikasi tag scanner standar yang hanya membaca identitas UID kartu, aplikasi ini melakukan komunikasi duplex langsung ke mikrokontroler chip fisik menggunakan protokol **ISO-DEP (ISO 7816-4)** dan **FeliCa (JIS 6319-4)** untuk mengekstrak saldo riil serta log transaksi terakhir yang tersimpan di dalam kartu uang elektronik perbankan Indonesia.

Seluruh data hasil pemindaian dan penamaan terminal disimpan secara lokal di perangkat pengguna menggunakan **SQLite database bawaan Android** (`tap_money.db`), memastikan privasi pengguna tetap aman dan aplikasi berfungsi optimal secara offline-first tanpa koneksi internet.

> [!IMPORTANT]
> Seluruh transmisi APDU ke chip kartu bersifat **read-only** (hanya baca). Aplikasi ini tidak merusak, memanipulasi, maupun mengubah saldo kartu fisik.

---

## Fitur Utama

- **100% Native Kotlin**: Dibangun tanpa framework hybrid (bebas dari Capacitor, Cordova, maupun Webview).
- **Pembacaan Chip Riil**: Mengirimkan instruksi APDU biner untuk mengambil saldo aktual dan nomor kartu dari sektor chip kartu.
- **Dukungan Multi-Protokol**:
  - **ISO-DEP (ISO 7816-4)**: Mandiri e-Money, BCA Flazz, BRI Brizzi, BNI TapCash, Bank Mega MegaCash, Bank DKI JakCard.
  - **NfcF / FeliCa (JIS 6319-4)**: Kartu Multi Trip (KMT) KAI Commuter.
- **Log Riwayat Transaksi On-Chip**: Mengekstrak riwayat transaksi (tipe transaksi, nominal, sisa saldo, tanggal, jam, dan Terminal ID).
- **Pengelompokan Mutasi per Tanggal**: Daftar transaksi dikelompokkan secara visual berdasarkan tanggal dengan format Rupiah (`id-ID`).
- **Penyimpanan Lokal Offline-First**: Kartu yang berhasil dibaca disimpan ke database SQLite lokal agar dapat ditinjau kembali kapan saja.
- **Editor Alias Lokasi Terminal**: Memungkinkan pengguna memberi nama manual pada kode Terminal ID (misalnya gerbang tol, halte BRT, stasiun kereta).
- **Desain Neobrutalist**: Tampilan kontras tinggi dengan garis border hitam tegas, bayangan solid (*solid offset*), palet warna tegas (Kuning, Biru, Hijau, Merah), serta dukungan tema gelap (*Dark Mode*).
- **Umpan Balik Haptik & Audio**: Getaran haptic feedback dan nada notifikasi audio sesaat setelah kartu berhasil terdeteksi.

---

## Daftar Kartu yang Didukung

| Kartu | Penerbit | Protokol NFC | Saldo Riil | Mutasi Transaksi | Deteksi Nomor Kartu (PAN) |
| :--- | :--- | :--- | :---: | :---: | :---: |
| **Mandiri e-Money** | Bank Mandiri | ISO-DEP (ISO 7816-4) | Ya | Ya (Record 1–10) | Ya (`6032...`) |
| **BCA Flazz (Gen 1 & 2)** | Bank BCA | PBOC / DESFire | Ya | Ya (Binary & DESFire) | Ya (`6013...` / `6019...`) |
| **BNI TapCash** | Bank BNI | ISO-DEP | Ya | Ya (Epoch 1995) | Ya (`7546...`) |
| **BRI Brizzi** | Bank BRI | Mifare DESFire | Ya | Ya (BCD Format) | Ya (`5058...`) |
| **KMT Commuter Line** | PT Kereta Commuter Indonesia | NfcF / FeliCa (0x220F) | Ya | Ya | Ya (`1002...`) |
| **Bank DKI JakCard** | Bank DKI | ISO-DEP | Ya | Ya | Ya |
| **Bank Mega MegaCash** | Bank Mega | ISO-DEP | Ya | Parsial | Ya (`6020...`) |

---

## Arsitektur & Struktur Proyek

Struktur direktori kode sumber native Android:

```text
tap-money-info/
├── android/
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml          # Konfigurasi Activity, izin NFC, VIBRATE, & singleTask
│   │   │   ├── java/id/tapmoney/
│   │   │   │   ├── MainActivity.kt          # Controller utama, NFC ReaderCallback, haptik, & state
│   │   │   │   ├── nfc/
│   │   │   │   │   └── NfcEngine.kt         # Engine transmisi APDU biner & parser protokol chip
│   │   │   │   ├── model/
│   │   │   │   │   └── CardModels.kt        # Data classes (CardBalanceResult, CardTransactionItem, dsb.)
│   │   │   │   ├── data/
│   │   │   │   │   └── CardDatabase.kt      # SQLiteOpenHelper untuk penyimpanan kartu & alias lokasi
│   │   │   │   └── ui/
│   │   │   │       ├── MainScreen.kt        # Screen utama Compose, scanner status, tabs, & modal
│   │   │   │       └── NeobrutalismComponents.kt # Modifiers styling neobrutalis, palet, & formatter
│   │   │   └── res/
│   │   │       ├── drawable/                # Asset latar kartu, logo bank, & ikon aplikasi
│   │   │       └── values/                  # Definisi styles & colors
│   │   └── build.gradle                     # Konfigurasi build module, Compose compiler, & dependensi
│   ├── build.gradle                         # Root Gradle script (Kotlin 2.0.21, AGP 8.13.0)
│   ├── settings.gradle                      # Pengaturan project Gradle
│   └── gradle.properties                    # Pengaturan memori JVM & opsi compiler
├── README.md                                # Dokumentasi Bahasa Indonesia
└── README.en.md                             # English Documentation
```

---

## Spesifikasi Teknis & Parsing APDU

### 1. Pembacaan Saldo BCA Flazz (PBOC Command)
Menggunakan perintah standar PBOC Get Balance:
```text
80 5C 00 02 04
```
Hasil parsing biner 3-byte Big-Endian:
$$\text{Saldo Flazz} = (B_1 \times 2^{16}) + (B_2 \times 2^8) + B_3$$

### 2. Mandiri e-Money APDU Sequence
Mengirimkan urutan APDU command:
- **Select AID**: `00 A4 04 00 08 00 00 00 00 00 00 00 01`
- **Read Card Number**: `00 B3 00 00 3F`
- **Read Balance**: `00 B5 00 00 0A` (4 byte Little-Endian integer)
$$\text{Saldo e-Money} = B_0 + (B_1 \times 2^8) + (B_2 \times 2^{16}) + (B_3 \times 2^{24})$$

### 3. BNI TapCash Epoch Calculation
Waktu mutasi dihitung dari selisih detik basis epoch 1 Januari 1995 UTC ($788.893.200\text{ detik}$ sejak Unix epoch):
$$T_{\text{ms}} = (S_{\text{raw}} \times 1000) + 788.893.200.000$$

### 4. KMT Commuter Line (FeliCa JIS 6319-4)
Membaca block memory tanpa enkripsi (*Read Without Encryption*) pada Service Code `0x220F` block `0x8000`:
- **Panjang Paket**: 16 byte
- **Command Code**: `0x06`
- **Saldo**: 3 byte Little-Endian unsigned integer pada byte ke-13, 14, dan 15.

---

## Prasyarat Sistem

- **Android Studio**: Versi Koala (2024.1.1) atau versi lebih baru.
- **JDK**: Java Development Kit 21 (LTS).
- **Target OS**: Android 7.0 (API 24) hingga Android 15 (API 35).
- **Perangkat**: Ponsel fisik Android dengan fitur sensor NFC aktif.

---

## Panduan Build & Menjalankan

### 1. Navigasi ke Folder Android
```bash
cd tap-money-info/android
```

### 2. Kompilasi Debug APK
```bash
./gradlew assembleDebug
```
File APK debug berada di:
```text
android/app/build/outputs/apk/debug/app-debug.apk
```

### 3. Instal ke Perangkat Android
Hubungkan ponsel via kabel USB dengan USB Debugging aktif:
```bash
./gradlew installDebug
```

---

## Build Rilis & Signing APK

Kompilasi varian release:
```bash
./gradlew assembleRelease
```

Tandatangani file APK menggunakan `apksigner`:
```bash
apksigner sign --ks release.keystore --ks-pass pass:YOUR_PASSWORD --out TapMoney-v${VERSION_NAME}.apk android/app/build/outputs/apk/release/app-release-unsigned.apk
```

Verifikasi integritas tanda tangan:
```bash
apksigner verify --verbose TapMoneyInfo.apk
```

---

## Author

**Machfudin**  
- System Architect & Lead Developer  
- GitHub: [@machfudn](https://github.com/machfudn)

