<div align="center">

<img src="android/app/src/main/res/drawable/ic_launcher.png" alt="Logo Tap Money" width="96" height="96" />

# Tap Money

[![Unduh APK Rilis](https://img.shields.io/badge/Download-Release%20APK-success?logo=android&logoColor=white)](https://github.com/machfudn/tapMoney/releases/latest)
[![Android CI Release](https://github.com/machfudn/tapMoney/actions/workflows/build-release-apk.yml/badge.svg)](https://github.com/machfudn/tapMoney/actions/workflows/build-release-apk.yml)
[![Version](https://img.shields.io/badge/version-v1.1.13-blue.svg)](https://github.com/machfudn/tapMoney/releases/latest)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg?logo=kotlin)](#)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.10.01-4285F4.svg?logo=android)](#)
[![Target SDK](https://img.shields.io/badge/targetSdk-35%20(Android%2015)-green.svg)](#)
[![Min SDK](https://img.shields.io/badge/minSdk-24%20(Android%207.0)-orange.svg)](#)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Aplikasi Android native murni (100% Kotlin & Jetpack Compose) untuk membaca saldo riil, nomor kartu fisik (PAN), dan mutasi transaksi chip uang elektronik berbasis NFC (ISO-DEP & FeliCa).

> 📲 **Unduh APK Rilis Siap Pakai**: Dapatkan versi rilis resmi bertanda tangan terbaru di [GitHub Releases](https://github.com/machfudn/tapMoney/releases/latest). Langsung pasang di smartphone Android Anda tanpa perlu kompilasi kode sumber.

---

[English Documentation (README.en.md)](README.en.md)

---

</div>

## Daftar Isi
- [Ringkasan](#ringkasan)
- [Fitur Utama](#fitur-utama)
- [Daftar Kartu yang Didukung & Segmentasi](#daftar-kartu-yang-didukung--segmentasi)
- [Arsitektur & Struktur Proyek](#arsitektur--struktur-proyek)
- [Spesifikasi Teknis & Parsing APDU](#spesifikasi-teknis--parsing-apdu)
- [Persyaratan Perangkat](#persyaratan-perangkat)
- [Unduh & Pemasangan APK Rilis](#unduh--pemasangan-apk-rilis)
- [Petunjuk Penggunaan](#petunjuk-penggunaan)
- [Author](#author)

---

## Ringkasan

**Tap Money** adalah aplikasi mobile Android native modern yang dikembangkan secara penuh menggunakan bahasa **Kotlin 2.0.21** dan toolkit UI **Jetpack Compose**. Berbeda dari aplikasi pemindai tag standar yang hanya membaca serial UID kartu, aplikasi ini melakukan komunikasi duplex langsung ke mikrokontroler chip fisik menggunakan protokol **ISO-DEP (ISO 7816-4)** dan **FeliCa (JIS 6319-4)** untuk mengekstrak saldo riil, nomor kartu fisik (PAN 16-digit murni), serta log mutasi transaksi on-chip kartu uang elektronik perbankan dan transit Indonesia.

Seluruh data hasil pemindaian dan penamaan terminal disimpan secara lokal di perangkat pengguna menggunakan **SQLite database bawaan Android** (`tap_money.db`), memastikan privasi pengguna terjaga penuh, bebas pelacakan, dan berfungsi optimal secara offline-first tanpa memerlukan koneksi internet.

> [!IMPORTANT]
> Seluruh transmisi APDU ke chip kartu bersifat **read-only** (hanya baca). Aplikasi ini tidak merusak, memanipulasi, maupun mengubah saldo kartu fisik.

---

## Fitur Utama

- **100% Native Kotlin**: Dibangun tanpa framework hybrid (bebas dari Capacitor, Cordova, maupun Webview).
- **Pembacaan Chip Riil**: Mengirimkan instruksi APDU biner langsung ke mikrokontroler smartcard untuk mengambil saldo riil dan nomor kartu fisik asli.
- **Dukungan Multi-Protokol**:
  - **ISO-DEP (ISO 7816-4)**: Mandiri e-Money, BCA Flazz, BRI Brizzi, BNI TapCash, Bank Mega MegaCash, Bank DKI JakCard.
  - **NfcF / FeliCa (JIS 6319-4)**: Kartu Multi Trip (KMT) KAI Commuter, MRT Jakarta, Suica, PASMO, ICOCA.
- **Ekstraksi Nomor Kartu (PAN 16 Digit Murni)**: Mengambil nomor kartu fisik aktual dari ISO/IEC 7813 Track 2 ASCII, record BCD, maupun DESFire info file tanpa menghasilkan nomor dummy acak.
- **Log Mutasi Transaksi On-Chip**: Mengekstrak detail transaksi riil (tipe mutasi, nominal, sisa saldo setelah transaksi, tanggal, jam, dan Terminal ID).
- **Infinite Scroll & Pengurutan Ketat Descending**: Seluruh transaksi chip ditampilkan dalam satu tampilan bergulir tanpa batas (tanpa batasan default 3 item) dan selalu terurut descending (`yyyy-MM-dd HH:mm:ss`).
- **Pengelompokan Tanggal Dwibahasa**: Mutasi dikelompokkan secara visual per tanggal kalender dengan pelokalan format tanggal Bahasa Indonesia dan English.
- **Ekspor Dokumen & Cetak Struk**:
  - **Dokumen PDF A4 Neobrutalisme**: Menghasilkan berkas PDF A4 beresolusi tinggi dengan tabel transaksi dan badge status saldo.
  - **Cetak Struk Thermal Bluetooth**: Utilitas formatting struk 32-kolom monospaced ESC/POS 58mm untuk printer thermal POS.
  - **Salin & Bagikan**: Fitur salin ringkasan transaksi ke clipboard dan integrasi share sheet sistem.
- **Resolusi Terminal & Lokasi Offline 3 Lapis**: Kamus luring cerdas untuk otomatis menerjemahkan Terminal ID ke nama gerbang tol, stasiun KRL/MRT/LRT, TransJakarta, dan ritel; dengan penyaringan otomatis kode acak seperti `HT0`.
- **Internasionalisasi Dwibahasa (i18n)**: Dukungan penuh Bahasa Indonesia dan English menggunakan arsitektur JSON mandiri (`assets/lang/id.json` & `en.json`) dengan dropdown selektor bahasa interaktif di Pengaturan.
- **Desain Neobrutalisme Modern**: Tampilan kontras tinggi dengan garis border hitam tegas (`#111827`), drop-shadow solid (`neoCard`), palet warna resmi kartu, dan Dark Mode kontras tinggi.
- **Umpan Balik Haptik & Audio**: Getaran haptic feedback responsif dan notifikasi audio sesaat setelah kartu berhasil teridentifikasi.

---

## Daftar Kartu yang Didukung & Segmentasi

### 1. Perbankan Indonesia (Domestik)
| Kartu | Penerbit | Protokol NFC | Saldo Riil | Mutasi Transaksi | Deteksi Nomor Kartu (PAN) |
| :--- | :--- | :--- | :---: | :---: | :---: |
| **Mandiri e-Money** | Bank Mandiri | ISO-DEP (ISO 7816-4) | Ya | Ya (Scan 64 blok 00D1 + SFI Cyclic 1..30) | Ya (`6032...`) |
| **BCA Flazz (Gen 1 & 2)** | Bank BCA | PBOC / DESFire | Ya | Ya (Gen 2 Log Decode epoch 1980 WIB) | Ya (`6013...` / `6019...`) |
| **BNI TapCash** | Bank BNI | ISO-DEP (CEPAS variant) | Ya | Ya (Epoch 1995 Descending) | Ya (`7546...`) |
| **BRI Brizzi** | Bank BRI | Mifare DESFire EV1 | Ya (Multi-file + Recovery) | Ya (DESFire Cyclic Record 32-byte BCD) | Ya (`5221...` / `6013...` / `5379...`) |
| **Bank DKI JakCard** | Bank DKI | ISO-DEP | Ya | Ya | Ya |
| **Bank Mega MegaCash** | Bank Mega | ISO-DEP | Ya | Parsial | Ya (`6020...`) |

### 2. Transit Domestik
| Kartu | Penerbit | Protokol NFC | Saldo Riil | Mutasi Transaksi | Pemetaan Stasiun |
| :--- | :--- | :--- | :---: | :---: | :---: |
| **KMT Commuter Line** | PT Kereta Commuter Indonesia | NfcF / FeliCa (0x90B7) | Ya | Ya (Epoch Jakarta 2000) | Ya (Katalog Stasiun Jabodetabek) |
| **MRT Jakarta (MRTJ)** | PT MRT Jakarta | NfcF / FeliCa (0x9373) | Ya | Ya (Transaksi Terakhir) | Ya |

### 3. Transit Regional / Internasional (Kategori A - Terbuka Penuh)
| Kartu | Negara / Wilayah | Protokol NFC | Saldo Riil | Mutasi Transaksi |
| :--- | :--- | :--- | :---: | :---: |
| **CEPAS (EZ-Link / NETS FlashPay)** | Singapura | ISO-DEP (CLA 0x90) | Ya (Cents SGD) | Ya |
| **Suica / PASMO / ICOCA** | Jepang | FeliCa (Service 0x090F) | Ya (Yen JPY) | Ya (20 blok riwayat) |
| **Rakuten Edy** | Jepang | FeliCa (Service 0x1317 & 0x110B) | Ya (Yen JPY) | Ya |
| **T-Money & Cashbee** | Korea Selatan | ISO-DEP (AID D4100000030001 / KSX6924) | Ya (KRW Won) | Ya |
| **China Transit (T-Union / PBOC)** | Tiongkok | PBOC 2.0/3.0 (AID A000000632010105) | Ya (Cents CNY) | Ya |
| **Sydney Opal** | Australia | MIFARE DESFire EV1 (App 0x314553 File 0x07) | Ya (Cents AUD) | Mode & Status Transit |

---

## Arsitektur & Struktur Proyek

Struktur paket kode sumber Kotlin:

```text
tapMoney/
├── android/
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml              # Manifest, izin NFC, VIBRATE, & BLUETOOTH
│   │   │   ├── assets/lang/                     # Kamus dwibahasa JSON mandiri
│   │   │   │   ├── id.json                      # Terjemahan Bahasa Indonesia
│   │   │   │   └── en.json                      # Terjemahan English
│   │   │   ├── java/id/tapmoney/
│   │   │   │   ├── MainActivity.kt              # Controller utama, lifecycle NFC, haptik, & state
│   │   │   │   ├── data/
│   │   │   │   │   ├── CardDatabase.kt          # Database SQLite lokal (kartu tersimpan & alias terminal)
│   │   │   │   │   └── TerminalCatalog.kt       # Katalog offline stasiun kereta, gerbang tol, & ritel
│   │   │   │   ├── model/
│   │   │   │   │   └── CardModels.kt            # Data classes (CardBalanceResult, CardTransactionItem)
│   │   │   │   ├── nfc/
│   │   │   │   │   ├── ApduChannel.kt           # Abstraksi transmisi APDU ISO-DEP
│   │   │   │   │   ├── CardParser.kt            # Dispatcher registry parsing kartu
│   │   │   │   │   ├── NfcEngine.kt             # Hardware NFC ReaderCallback & tag router
│   │   │   │   │   ├── TxDates.kt               # Decoder tanggal BCD, epoch 1980 WIB, & epoch 1995
│   │   │   │   │   └── parser/
│   │   │   │   │       ├── BrizziParser.kt      # Parser BRI Brizzi DESFire EV1 (PAN, Saldo LE, BCD Log)
│   │   │   │   │       ├── CepasParser.kt       # Parser CEPAS Singapore (EZ-Link / FlashPay)
│   │   │   │   │       ├── ChinaTransitParser.kt # Parser T-Union / PBOC Tiongkok
│   │   │   │   │       ├── FelicaParser.kt      # Parser Suica, PASMO, ICOCA, Edy Jepang
│   │   │   │   │       ├── FlazzParser.kt       # Parser BCA Flazz Gen 1 & 2 (Track 2, epoch 1980 WIB)
│   │   │   │   │       ├── JakCardParser.kt     # Parser Bank DKI JakCard
│   │   │   │   │       ├── MandiriParser.kt     # Parser Mandiri e-Money (64 blok 00D1 & SFI 1..30)
│   │   │   │   │       ├── MegaCashParser.kt    # Parser Bank Mega MegaCash
│   │   │   │   │       ├── OpalParser.kt        # Parser Sydney Opal Australia
│   │   │   │   │       ├── TapCashParser.kt     # Parser BNI TapCash (epoch 1995 descending)
│   │   │   │   │       ├── TMoneyParser.kt      # Parser Telkom T-Money & T-Money Korea Selatan
│   │   │   │   │       └── transit/
│   │   │   │   │           ├── KmtDateDecoder.kt    # Decoder epoch Jakarta 2000 KMT Commuter
│   │   │   │   │           └── KmtStationCatalog.kt # Database stasiun KRL Jabodetabek
│   │   │   │   ├── ui/
│   │   │   │   │   ├── AppStrings.kt            # Engine i18n JSON mandiri tanpa dependensi runtime
│   │   │   │   │   ├── MainScreen.kt            # Layar utama Jetpack Compose, kartu aktif & infinite scroll
│   │   │   │   │   ├── NeobrutalismComponents.kt # Komponen neobrutalisme, palet warna, & kartu
│   │   │   │   │   └── SettingsScreen.kt        # Layar penuh pengaturan, dropdown bahasa, tema, & switch
│   │   │   │   └── util/
│   │   │   │       ├── BluetoothPrinter.kt      # Driver printer Bluetooth ESC/POS 58mm
│   │   │   │       ├── ReceiptExporter.kt       # Generator dokumen PDF A4 Neobrutalisme
│   │   │   │       └── ReceiptFormatter.kt      # Formatter struk thermal monospaced 32 kolom
│   │   │   └── res/
│   │   │       ├── drawable/                    # Vektor logo perbankan, brand kartu, & ikon launcher
│   │   │       └── values/                      # Definisi tema sistem, warna, & strings fallback
│   │   └── build.gradle                         # Konfigurasi build module, Compose compiler, & dependensi
│   ├── build.gradle                             # Root Gradle buildscript (Kotlin 2.0.21, AGP 8.13.0)
│   ├── settings.gradle                          # Pengaturan modul Gradle
│   ├── version.properties                       # Konfigurasi versi dinamis rilis (v1.1.13 / 10113)
│   ├── RELEASE_NOTES.md                         # Catatan rilis v1.1.13
│   └── gradle.properties                        # Optimasi JVM & compiler arguments
├── tests/
│   ├── AppUpdateCheckerTest.kt                  # Pengujian parser update dwibahasa & semver
│   └── FlazzParserTest.kt                       # Suite unit test parsing APDU & engine kartu
├── LICENSE                                      # GNU General Public License v3.0
├── README.md                                    # Dokumentasi Bahasa Indonesia
└── README.en.md                                 # English Documentation
```

---

## Spesifikasi Teknis & Parsing APDU

### 1. Pembacaan Saldo & Mutasi BCA Flazz
- **Get Balance**: `80 5C 00 02 04` (3-byte Big-Endian unsigned integer):
  $$\text{Saldo Flazz} = (B_1 \times 2^{16}) + (B_2 \times 2^8) + B_3$$
- **Nomor Kartu Gen 2**: Membaca ISO/IEC 7813 Track 2 via `00 B0 81 00 8E`. Substring 16 karakter pertama merupakan nomor PAN fisik kartu (`6013...` / `6019...`).
- **Timestamp Mutasi Gen 2**: Offset 4-byte unsigned integer sejak epoch basis 1 Januari 1980 00:00:00 WIB (UTC+7 / `Asia/Jakarta`).

### 2. Mandiri e-Money APDU Sequence
- **Select AID**: `00 A4 04 00 08 00 00 00 00 00 00 00 01`
- **Read Card Number (PAN)**: `00 B3 00 00 3F`
- **Read Balance**: `00 B5 00 00 0A` (4-byte Little-Endian integer):
  $$\text{Saldo e-Money} = B_0 + (B_1 \times 2^8) + (B_2 \times 2^{16}) + (B_3 \times 2^{24})$$
- **Deep History Scan**: Pembacaan 64 blok file biner `00 D1 [block] 00 00` dipadukan dengan SFI cyclic record `00 B2 [rec] 24 00` & `44 00` (record 1..30) dengan deduplikasi identik.

### 3. BRI Brizzi DESFire EV1 APDU
- **Select Application**: `90 5A 00 00 03 01 00 00 00` (AID `010000`, alternatif `160000`).
- **Read PAN**: `90 BD 00 00 07 00 00 00 00 17 00 00 00` (File 0 / 1). 16 digit PAN BCD diekstrak dari offset substring hex 6..22 dan divalidasi terhadap prefix BIN Brizzi resmi.
- **Read Balance**: DESFire Get Value `90 6C 00 00 01 [File] 00` (4-byte Little-Endian integer). Jika Get Value terproteksi, nilai saldo otomatis dipulihkan dari sisa saldo transaksi on-chip terbaru.
- **Read History**: DESFire Read Records `90 BB 00 00 07 01 00 00 00 00 00 00 00` + chaining `90 AF 00 00 00`. Setiap record 32-byte (64 karakter hex):
  - Offset hex 32..37: Tanggal BCD `ddMMyy`
  - Offset hex 38..43: Waktu BCD `HHmmss`
  - Offset hex 44..45: Tipe transaksi (`EB` = Pembayaran, `EC` = Top Up)
  - Offset hex 46..51: Nominal transaksi (3-byte Little-Endian)
  - Offset hex 52..59: Sisa saldo transaksi (4-byte Little-Endian)

### 4. BNI TapCash Epoch Calculation
Waktu mutasi dihitung dari selisih detik basis epoch 1 Januari 1995 UTC ($788.893.200\text{ detik}$ sejak Unix epoch):
$$T_{\text{ms}} = (S_{\text{raw}} \times 1000) + 788.893.200.000$$

### 5. KMT Commuter Line (FeliCa JIS 6319-4)
Membaca block memory tanpa enkripsi (*Read Without Encryption*) pada Service Code `0x220F` block `0x8000`:
- **Panjang Paket**: 16 byte
- **Command Code**: `0x06`
- **Saldo**: 3-byte Little-Endian unsigned integer pada byte ke-13, 14, dan 15.

---

## Persyaratan Perangkat

- **Sistem Operasi**: Android 7.0 (Nougat / API 24) hingga Android 15 (Vanilla Ice Cream / API 35).
- **Perangkat Keras**: Smartphone Android fisik yang memiliki chip dan antena NFC aktif.
- **Izin Aplikasi**:
  - `android.permission.NFC`: Akses duplex hardware NFC membaca tag ISO-DEP & FeliCa.
  - `android.permission.VIBRATE`: Umpan balik haptik konfirmasi pembacaan kartu sukses/gagal.
  - `android.permission.BLUETOOTH` / `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN`: Akses opsional pairing printer thermal 58mm untuk cetak struk.

---

## Unduh & Pemasangan APK Rilis

1. **Unduh APK Resmi**: Kunjungi halaman [GitHub Releases](https://github.com/machfudn/tapMoney/releases/latest) dan unduh berkas `TapMoney-v*.apk`.
2. **Pemasangan di Smartphone**:
   - Buka file APK dari panel notifikasi unduhan atau pengelola berkas (*File Manager*).
   - Jika Android menampilkan peringatan keamanan instalasi dari luar Google Play, ketuk **Pengaturan (Settings)** lalu aktifkan **Izinkan dari sumber ini (Allow from this source)**.
   - Ketuk **Instal (Install)** dan tunggu proses hingga selesai.
3. **Integritas & Keamanan Rilis**:
   - Seluruh rilis APK resmi dikompilasi, diuji, dan ditandatangani secara otomatis melalui pipeline aman GitHub Actions CI/CD ([`build-release-apk.yml`](.github/workflows/build-release-apk.yml)).
   - Bebas pelacak (*no trackers*), bebas iklan, dan berfungsi 100% offline tanpa login.

---

## Petunjuk Penggunaan

1. **Aktifkan NFC**: Buka Pengaturan Cepat (*Quick Settings*) Android atau menu *Settings > Connected devices > Connection preferences > NFC*, lalu pastikan sakelar **NFC** dalam keadaan aktif.
2. **Buka Aplikasi**: Jalankan aplikasi **Tap Money**.
3. **Tempelkan Kartu**: Dekatkan kartu uang elektronik Anda (Flazz, e-Money, TapCash, Brizzi, JakCard, KMT, dll.) ke bagian belakang bodi ponsel (posisi sensor antena NFC ponsel biasanya berada di sekitar modul kamera belakang atau area tengah atas).
4. **Tahan Posisi**: Tahan kartu menempel selama 1–2 detik hingga ponsel bergetar atau berbunyi konfirmasi. Saldo riil, nomor fisik kartu (PAN 16-digit), bank penerbit, dan mutasi transaksi on-chip akan langsung ditampilkan di layar.
5. **Simpan, Ekspor, atau Cetak**:
   - Berikan nama alias terminal transaksi secara kustom bila diperlukan.
   - Ketuk tombol simpan untuk mencatat riwayat kartu ke database lokal perangkat.
   - Ekspor ringkasan kartu dan mutasi ke berkas dokumen PDF A4 Neobrutalisme atau cetak struk via printer thermal Bluetooth 58mm.

---

## Author

**Machfudin**  
- Software Engineer  
- GitHub: [@machfudn](https://github.com/machfudn)
