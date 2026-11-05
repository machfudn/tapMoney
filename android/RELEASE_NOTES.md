<!-- lang:id -->
### 🇮🇩 Ringkasan Rilis (v1.1.13)
* **Sinkronisasi Instan Status Adapter NFC**:
  * Menangkap broadcast status sistem NFC via `RECEIVER_EXPORTED` pada lifecycle `onStart`/`onStop` untuk merespons aktivasi toggle Quick Settings secara langsung.
  * Mengaktifkan kembali reader mode secara instan saat Quick Settings ditutup menggunakan hook `onWindowFocusChanged`.
  * Menyediakan polling fallback coroutine ticker 1 detik untuk mengeliminasi jeda deteksi saat aplikasi aktif di foreground.
* **Rekonsiliasi Ledger Saldo Mundur Riwayat Flazz**:
  * Mengaitkan saldo akhir transaksi mutasi terbaru langsung dengan saldo riil kartu saat ini (*current balance anchor*).
  * Menghitung saldo akhir historis secara deterministik (*continuous backward ledger*) melintasi transaksi pembayaran dan isi ulang tanpa nilai null.
  * Memperluas pembacaan record DESFire dengan fallback `0xBB`, penanganan frame lanjutan `91 AF`, toleransi data parsial corrupt, dan penggabungan cyclic record Gen 1.
  * Memperhalus deduplikasi composite key dengan menyertakan Terminal ID guna menjaga transaksi bernominal dan berwaktu sama pada merchant berbeda.
* **Peningkatan UI Riwayat Transaksi & Visualisasi Saldo**:
  * Menambahkan indikator jumlah mutasi fisik kartu yang berhasil dibaca chip.
  * Menampilkan total item transaksi pada tombol interaktif tampilkan semua riwayat mutasi.
  * Format saldo akhir transaksi dalam tipografi monospaced tebal untuk keterbacaan kontras tinggi.
* **Tata Kelola Proyek & Lisensi**:
  * Menerapkan lisensi resmi copyleft GNU General Public License v3.0 (GPLv3) untuk melindungi algoritma dekoding APDU dan integritas kode sumber terbuka.
  * Memperbarui dokumentasi dwibahasa (README.md & README.en.md) berorientasi rilis langsung (*release-first*).
<!-- /lang:id -->

<!-- lang:en -->
### 🇬🇧 Release Notes (v1.1.13)
* **Instant NFC Adapter Status Sync**:
  * Captures system NFC broadcast events via `RECEIVER_EXPORTED` across `onStart`/`onStop` lifecycle to immediately reflect Quick Settings toggles.
  * Reactivates NFC reader mode immediately when Quick Settings shade closes via `onWindowFocusChanged`.
  * Provides a 1-second coroutine ticker polling fallback while activity is in foreground to eliminate toggle detection latency.
* **Continuous Backward Ledger Reconciliation for Flazz**:
  * Anchors the latest mutation ending balance directly to the card's real-time current balance (*current balance anchor*).
  * Deterministically reconstructs historical ending balances (*continuous backward ledger*) across debit payments and top-ups without null values.
  * Expands DESFire record retrieval with `0xBB` fallback, `91 AF` continuation frames, corrupt record tolerance, and unconditional Gen 1 cyclic merges.
  * Refines composite deduplication by incorporating Terminal ID so simultaneous identical transactions at distinct merchants are preserved.
* **Transaction UI Enhancements & Balance Contrast**:
  * Adds visible on-chip physical transaction count indicators.
  * Shows total mutation items on the interactive "View All History" toggle button.
  * Formats transaction ending balances in bold monospaced typography for high readability.
* **Project Governance & Licensing**:
  * Officially applies the copyleft GNU General Public License v3.0 (GPLv3) to protect card APDU algorithms and open-source integrity.
  * Refreshes bilingual documentation (README.md & README.en.md) with a release-first orientation.
<!-- /lang:en -->
