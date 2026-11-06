<!-- lang:id -->
### 🇮🇩 Ringkasan Rilis Beta / Pre-Release (v1.1.14-beta)
* **Perbaikan Saldo Akhir Scanner Kartu Flazz**:
  * Memperbaiki masalah saldo akhir transaksi Top Up yang sebelumnya tidak muncul pada riwayat transaksi.
  * Rekonsiliasi riwayat transaksi dua arah (*bidirectional ledger reconciliation*) untuk menjaga kontinuitas saldo dan mencegah saldo menjadi kosong atau negatif pada riwayat non-kontigu.
  * Mempertahankan saldo mentah chip Flazz (SFI 5 dan DESFire) agar tidak tertimpa nilai tidak valid.
  * Normalisasi deteksi tipe transaksi Top Up agar konsisten di seluruh antarmuka aplikasi.
* **Kanal Pembaruan Dual-Channel & Penyegaran UI/UX Modal**:
  * Dialog pembaruan baru dengan tab pemilihan rilis Stabil dan rilis Beta / Pre-Release langsung di Pengaturan.
  * Desain modal responsif dengan header tetap (*pinned sticky header*), konten bergulir terpadu (*single smooth scroll*), dan tombol aksi bawah tetap (*pinned sticky footer*) yang mencegah tampilan bertumpuk atau saling menimpa.
  * Tombol tutup cepat (`X`) di pojok kanan atas serta navigasi tab saluran dengan dot indikator rilis baru.
  * Chip rincian berkas ringkas dengan ukuran APK dan hash SHA-256 tersingkat yang mendukung salin satu-klik ke papan klip.
  * Dukungan SemVer lengkap untuk mendeteksi rilis beta dan pre-release dari repositori GitHub.

<!-- lang:en -->
### 🇬🇧 Beta / Pre-Release Notes (v1.1.14-beta)
* **Flazz Card Scanner Ending Balance Fixes**:
  * Resolved missing ending balance on Top Up transactions in transaction history.
  * Implemented bidirectional ledger reconciliation to preserve balance continuity without corrupting non-contiguous transaction records.
  * Preserved raw on-chip balance from Flazz SFI 5 and DESFire reload logs.
  * Normalized Top Up transaction type matching across UI widgets.
* **Dual-Channel In-App Update & Modal UI/UX Redesign**:
  * New update dialog in Settings supporting both Stable and Beta / Pre-Release channels.
  * Responsive dialog architecture featuring a pinned sticky header, a single unified scrollable body, and a pinned sticky footer to eliminate layout overlapping and cramped nested boxes.
  * Quick-dismiss close button (`X`) and sleek channel tabs with new-release status indicator dots.
  * Compact file details chip with APK size and a truncated SHA-256 fingerprint with 1-click copy-to-clipboard functionality.
  * Full SemVer support for beta tag tracking and pre-release updates from GitHub.
