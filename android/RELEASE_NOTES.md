* **Pembaruan Penuh Parsing BRI BRIZZI (DESFire EV1/EV2)**:
  * **Nomor Kartu (PAN)**: Ekstraksi 16 digit PAN fisik murni dari File 0 / File 1 DESFire (`90 BD`) dengan validasi prefix BIN resmi BRIZZI (`522184`, `601301`, `5379`, `507`).
  * **Saldo Riil**: Pembacaan saldo akurat multi-file via DESFire Get Value `90 6C` (4-byte Little Endian integer) serta mekanisme auto-recovery otomatis dari sisa saldo transaksi on-chip terbaru jika perintah Get Value terproteksi.
  * **Mutasi Transaksi**: Ekstraksi log transaksi DESFire linear cyclic record 32-byte (`90 BB` + multi-frame chaining `90 AF`) dengan decoding tanggal BCD `ddMMyy`, waktu BCD `HHmmss`, nominal transaksi (3-byte Little Endian), sisa saldo transaksi (4-byte Little Endian), dan terminal ID.
* **Perbaikan BCA Flazz (Gen 1 & Gen 2)**:
  * **Timestamp Mutasi**: Rekonstruksi waktu transaksi akurat menggunakan basis epoch 1 Januari 1980 WIB (Asia/Jakarta, UTC+7).
  * **Nomor Kartu**: Ekstraksi 16 digit PAN fisik melalui ISO/IEC 7813 Track 2 ASCII (`00 B0 81 00 8E`) untuk Flazz Gen 2 dan penandaan transparan hardware UID untuk kartu terproteksi.
* **Pemindaian Mendalam Mandiri e-Money**:
  * Pemindaian 64 blok file biner `00 D1` dipadukan dengan SFI cyclic record (`1..30`) untuk menangkap seluruh riwayat transaksi tersimpan, dilengkapi deduplikasi record identik.
* **BNI TapCash Terurut Descending**:
  * Parsing mutasi transaksi berbasis epoch 1995 dengan pengurutan strictly descending (terbaru di posisi teratas).
* **Pengalaman Riwayat Mutasi Modern**:
  * **Infinite Scroll**: Seluruh riwayat transaksi chip ditampilkan langsung tanpa pemotongan default 3 baris dan tanpa toggle.
  * **Pengurutan Ketat Descending**: Seluruh mutasi transaksi tersusun secara kronologis menurun (`yyyy-MM-dd HH:mm:ss`).
  * **Pengelompokan Tanggal Dwibahasa**: Pengelompokan visual per tanggal kalender dengan pelokalan Bahasa Indonesia dan English.
* **Ekspor & Cetak Struk Transaksi**:
  * **Ekspor PDF A4 Neobrutalisme**: Dokumen PDF berkualitas tinggi dengan border tegas, badge ringkasan saldo, dan tabel mutasi via Canvas native Android.
  * **Cetak Struk Thermal Bluetooth**: Utilitas struk monospaced 32 kolom ESC/POS 58mm untuk printer kasir thermal portable.
  * **Salin & Bagikan**: Opsi salin ringkasan transaksi ke clipboard dan integrasi share intent ke aplikasi lain.
* **Internasionalisasi Mandiri (i18n)**:
  * Kamus dwibahasa (Bahasa Indonesia & English) berbasis file JSON terpisah di assets (`lang/id.json` & `lang/en.json`) dengan custom lightweight parser tanpa dependensi runtime `org.json.JSONObject`.
  * Dropdown selektor bahasa interaktif berdesain neobrutalisme pada tab Pengaturan.
* **Katalog Terminal & Lokasi Offline 3 Lapis**:
  * Pemetaan otomatis Terminal ID ke gerbang tol, stasiun KRL/MRT/LRT, TransJakarta, dan merchant ritel via `TerminalCatalog`.
  * Penyaringan kode internal acak `HT0`/`ht0` menjadi "Tidak dikenali" (ID) / "Unknown" (EN).
  * Dukungan alias lokasi kustom pengguna yang tersimpan di SQLite lokal.
* **Penyempurnaan UI Neobrutalisme & Tema**:
  * Skema kartu identitas brand resmi dengan logo vektor di pojok kanan atas menggantikan bitmap kartu penuh.
  * Palet Dark Mode kontras tinggi dengan track switch biru (`#0284C7` & `#38BDF8`).
  * Kontras tinggi untuk kartu aktif yang tersimpan (background `#1E293B`, border `#00A3FF`, teks `#FFFFFF`).
