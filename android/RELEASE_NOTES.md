* **Perbaikan Riwayat Transaksi & Saldo Akhir BCA Flazz (Gen 1 & Gen 2)**:
  * **Offset Baca Record SFI**: Memperbaiki pembacaan cyclic log record SFI 04 (`0x84`) dan SFI 05 (`0x85`) dengan kalkulasi stepping offset tepat 60-byte (`rec * 60`), serta mekanisme fallback perakitan potongan 15-byte jika pembacaan langsung 60-byte ditolak chip.
  * **Ekstraksi Saldo Akhir**: Parsing saldo akhir mutasi (`balRaw`) pada byte 1..4 record secara presisi untuk tipe Payment maupun Top Up.
  * **Kontinuitas Buku Besar (Ledger Consistency)**: Rekonsiliasi otomatis saldo akhir transaksi secara mundur/maju dari saldo aktif kartu jika chip versi lama tidak memuat saldo langsung pada log transaksi.
* **Standarisasi Saldo Akhir pada Dokumen Struk & Ekspor**:
  * **Ekspor PDF A4**: Mengubah label kolom `SISA SALDO` menjadi `SALDO AKHIR` (`ENDING BAL`) serta memastikan seluruh baris terisi nilai saldo akhir riil.
  * **Cetak Struk Thermal Bluetooth (ESC/POS)**: Mengubah label `Sisa Saldo` menjadi `Saldo Akhir` (`Ending Bal`) pada format monospaced 32 kolom.
  * **Salin & Bagikan Struk**: Memperbarui teks struk ringkasan clipboard dan share intent menggunakan label `Saldo Akhir`.
  * **Tampilan Riwayat Chip**: Memastikan informasi `Saldo Akhir: Rp ...` tampil konsisten pada setiap item mutasi kartu.
* **Penyelarasan Identitas Proyek & Branding**:
  * Menstandarkan penamaan aplikasi menjadi **Tap Money** (`tapMoney`), menghapus akhiran redundant "Info" pada judul struk, header PDF, dan release workflow.
  * Pembaruan endpoint repositori resmi ke `https://github.com/machfudn/tapMoney`.
* **Optimasi Performa & Perampingan Repositori**:
  * Pembersihan seluruh test suite dan dependensi unit test/instrumentation test yang tidak diperlukan untuk menjaga ukuran repositori tetap ringan dan build lebih cepat.
