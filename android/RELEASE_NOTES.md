* **Optimalisasi Pembacaan Riwayat BCA Flazz (Gen 1 & Gen 2)**:
  * **Slicing SFI 15-Byte**: Ekstraksi transparent EF SFI 4 & SFI 5 dilakukan bertahap per 15-byte (`Le = 0x0F`, 16 chunk) untuk mencegah penolakan frame 60-byte (`0x3C`) pada varian chip Flazz tertentu, kemudian direkonstruksi menjadi record 60-byte utuh.
  * **Circular Log DESFire Gen 2**: Inisiasi APDU dan ekspansi loop circular record `90 32 03 00 01 [rec] 20` hingga 256 record dinamis dengan penanganan index 0/1 fallback.
  * **Penggabungan Multi-Sumber Riwayat**: Menggabungkan seluruh transaksi dari SFI dan DESFire tanpa saling menimpa, deduplikasi berbasis timestamp dan nominal, serta penyelarasan urutan kronologis mundur (strictly descending).
  * **Kalkulasi Nominal & Saldo**: Implementasi formula complement 24-bit (`16777216 - amountRaw`) dan resolusi saldo akhir pada record transaksi DESFire 32-byte.
* **Otentikasi 3DES DESFire BRI BRIZZI**:
  * **Autentikasi Mutual Native**: Implementasi kriptografi 3DES DESede CBC & DES ECB menggunakan `javax.crypto.Cipher` native Android tanpa dependensi library eksternal.
  * **Akses Purse & Riwayat Mutasi**: Pembacaan saldo purse terlindungi serta ekstraksi mutasi cyclic pada direktori aplikasi `020000` dan fallback `010000`.
* **Deteksi Status Sensor NFC Real-Time**:
  * Pendaftaran `BroadcastReceiver` dinamis pada `MainActivity` untuk mendengarkan perubahan status adapter sistem (`NfcAdapter.ACTION_ADAPTER_STATE_CHANGED`).
* **Informasi Berkas & Checksum SHA-256**:
  * Penambahan kartu Informasi Berkas pada dialog pembaruan yang menampilkan nama berkas APK, ukuran berkas, dan nilai checksum SHA-256 dengan tombol sekali sentuh untuk menyalin ke papan klip.
  * Workflow CI/CD otomatis menghitung `sha256sum` APK rilis dan mengunggah berkas `TapMoney-v*.apk.sha256`.
* **Renderer Markdown Ringkasan Rilis**:
  * Kotak ringkasan rilis kini mendukung parsing dan perenderan format Markdown (`.md`) secara penuh (judul `###`, daftar poin berulang `*` / `-`, teks tebal `**`, dan kode inline `` ` ``).
* **Tampilan Riwayat Mutasi Ringkas (Toggle 3 Transaksi)**:
  * Menampilkan 3 riwayat transaksi teratas secara default untuk menjaga antarmuka tetap rapi dengan tombol aksi beralih antara "Lihat Semua" dan "Lihat Lebih Sedikit".
* **Penanganan Lokasi Tidak Terdaftar**:
  * Pengetatan resolusi nama lokasi (`TerminalCatalog` & `KmtStationCatalog`): kode perangkat mentah otomatis dimasking menjadi label standar "Tidak dikenali" (ID) / "Unknown" (EN).
* **Penyempurnaan Visual & Kontras Tombol**:
  * Warna teks, ikon putar, dan indikator loading pada tombol "Periksa Pembaruan" di tab Pengaturan diselaraskan menjadi putih (`#FFFFFF`) untuk keterbacaan optimal.
* **Sinkronisasi Dwibahasa Penuh (ID & EN)**:
  * Memastikan seluruh 134 key kamus bahasa pada `id.json` dan `en.json` tersinkronisasi 100% untuk semua antarmuka, dialog, dan ekspor struk.
