* **Dekode Nomor Kartu 16 Digit Murni (Zero Dummy)**:
  * Ekstraksi nomor kartu fisik (PAN) dari ISO/IEC 7813 Track 2 ASCII untuk BCA Flazz Gen 2 (`00B081008E`), decoding BCD untuk Mandiri e-Money (`00B300003F` & fallback SFI 4 record 1), endianness decoding BRI Brizzi, BNI TapCash payload BCD, serta FeliCa KMT Commuter Line Service `0x300B`.
  * Kartu terenkripsi perbankan (seperti Flazz Gen 1) secara transparan dilabeli sebagai hardware UID chip (`UID: XX:XX:XX:XX`), tanpa nomor palsu.
* **Resolusi Lokasi Terminal 3 Lapis & Sensor Raw Hex**:
  * Pemetaan stasiun KRL Commuter Line, MRT Jakarta, LRT, dan gerbang tol Jabodetabek via `TerminalCatalog`.
  * Terminal ID tak terdaftar otomatis disaring menjadi fallback bersih "Tidak dikenali" (ID) / "Unknown" (EN) di seluruh antarmuka, struk thermal, dan dokumen PDF.
* **Layar Penuh Pengaturan Neobrutalisme**:
  * Layar penuh 3 tab: PEMINDAIAN (haptik & TTS), PRIVASI (bahasa & audit data), dan KARTU & DATA (katalog kartu inline).
  * Live theme switcher (Sistem, Terang, Gelap) dengan border hitam tebal `#111827` dan solid drop-shadow `neoCard`.
* **Ekspor Struk Thermal Bluetooth 32 Kolom & Dokumen PDF A4**:
  * Utilitas formatting struk 32-kolom monospaced ESC/POS (`ReceiptFormatter`) dengan timestamp WIB.
  * Generator PDF A4 Neobrutalisme (`ReceiptExporter`) dengan badge header, ringkasan saldo, dan badge transaksi berwarna.
* **Internasionalisasi Dwibahasa (i18n)**:
  * Dukungan dwibahasa penuh (Bahasa Indonesia & English) via `AppStrings` serta penyesuaian TTS otomatis.
* **Interaksi Privasi & Visual Kartu Minimalis**:
  * Eliminasi aset grafis kartu berukuran penuh, digantikan blok warna identitas korporat masing-masing penerbit kartu dengan logo resmi di pojok kanan atas.
  * Toggle sensor nomor kartu dan saldo interaktif langsung pada kartu utama.
