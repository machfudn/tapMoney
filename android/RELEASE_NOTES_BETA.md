<!-- lang:id -->
### 🇮🇩 Ringkasan Rilis Beta / Pre-Release (v1.1.14-beta)
* **Perbaikan Saldo Akhir Scanner Kartu Flazz**:
  * Memperbaiki masalah saldo akhir transaksi Top Up yang sebelumnya tidak muncul pada riwayat transaksi.
  * Rekonsiliasi riwayat transaksi dua arah (*bidirectional ledger reconciliation*) untuk menjaga kontinuitas saldo dan mencegah saldo menjadi kosong atau negatif pada riwayat non-kontigu.
  * Mempertahankan saldo mentah chip Flazz (SFI 5 dan DESFire) agar tidak tertimpa nilai tidak valid.
  * Normalisasi deteksi tipe transaksi Top Up agar konsisten di seluruh antarmuka aplikasi.
* **Kanal Pembaruan Dual-Channel**:
  * Dialog pembaruan baru dengan tab pemilihan rilis Stabil dan rilis Beta / Pre-Release langsung di Pengaturan.
  * Dukungan SemVer lengkap untuk mendeteksi build beta dan pre-release dari repositori GitHub.
<!-- /lang:id -->

<!-- lang:en -->
### 🇬🇧 Beta / Pre-Release Notes (v1.1.14-beta)
* **Flazz Card Scanner Ending Balance Fixes**:
  * Resolved missing ending balance on Top Up transactions in transaction history.
  * Implemented bidirectional ledger reconciliation to preserve balance continuity without corrupting non-contiguous transaction records.
  * Preserved raw on-chip balance from Flazz SFI 5 and DESFire reload logs.
  * Normalized Top Up transaction type matching across UI widgets.
* **Dual-Channel In-App Update**:
  * New update dialog in Settings supporting both Stable and Beta / Pre-Release channels.
  * Full SemVer support for beta tag tracking and pre-release updates from GitHub.
<!-- /lang:en -->
