* **Pembaruan Parser BRI BRIZZI (DESFire EV1/EV2)**:
  * **Pembacaan Saldo Handal**: Memprioritaskan File `0x00` pada perintah Get Value `90 6C 00 00 01 00 00` serta pre-read File `0x03` (`90 BD 00 00 07 03 ...`) sesuai implementasi referensi `rijalmyd/prepaidcard_reader` dan `muhakmal/osp-kue-reader`.
  * **Pembacaan Riwayat Mutasi**: Pembacaan log mutasi (`90 BB` File 01) langsung dieksekusi di bawah aplikasi aktif `010000` dengan multi-frame chaining `90 AF` tanpa pengalihan paksa aplikasi.
  * **Koreksi Offset & Penanggalan Record 32-Byte**: Memperbaiki pemetaan byte waktu (`timeHex` byte 16..18 / index 32..38) dan tanggal (`dateHex` byte 19..21 / index 38..44), serta menambahkan dukungan decoding ganda format BCD desimal dan Hex byte (basis 16).
* **Deteksi Status Sensor NFC Real-Time**:
  * Pendaftaran `BroadcastReceiver` dinamis pada `MainActivity` untuk mendengarkan perubahan status adapter sistem (`NfcAdapter.ACTION_ADAPTER_STATE_CHANGED`).
  * Perubahan status NFC on/off melalui Quick Settings atau setelan sistem langsung terdeteksi seketika tanpa perlu membuka ulang aplikasi.
* **Tampilan Riwayat Mutasi Ringkas (Toggle 3 Transaksi)**:
  * Menampilkan 3 riwayat transaksi teratas secara default untuk menjaga antarmuka tetap rapi.
  * Tombol aksi bergaya neobrutalisme interaktif untuk beralih antara "Lihat Semua" dan "Lihat Lebih Sedikit".
* **Penanganan Lokasi Tidak Terdaftar**:
  * Pengetatan resolusi nama lokasi (`TerminalCatalog` & `KmtStationCatalog`): kode perangkat mentah/undecoded otomatis dimasking menjadi label standar "Tidak dikenali" (ID) / "Unknown" (EN).
* **Penyempurnaan Visual & Kontras Tombol**:
  * Warna teks, ikon putar, dan indikator loading pada tombol "Periksa Pembaruan" di tab Pengaturan diselaraskan menjadi putih (`#FFFFFF`) untuk keterbacaan optimal.
* **Informasi Berkas & Checksum SHA-256**:
  * Penambahan kartu Informasi Berkas pada dialog pembaruan yang menampilkan nama berkas APK, ukuran berkas, dan nilai checksum SHA-256 dengan tombol sekali sentuh untuk menyalin ke papan klip.
  * Workflow CI/CD otomatis menghitung `sha256sum` APK rilis dan mengunggah berkas `TapMoney-v*.apk.sha256`.
* **Renderer Markdown Ringkasan Rilis**:
  * Kotak ringkasan rilis kini mendukung parsing dan perenderan format Markdown (`.md`) secara penuh (judul `###`, daftar poin berulang `*` / `-`, teks tebal `**`, dan kode inline `` ` ``).
* **Sinkronisasi Dwibahasa Penuh (ID & EN)**:
  * Memastikan seluruh 134 key kamus bahasa pada `id.json` dan `en.json` tersinkronisasi 100% untuk semua komponen antarmuka, dialog, dan ekspor struk.
