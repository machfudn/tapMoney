package id.tapmoney.ui

import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

interface AppStrings {
    val tabChipHistory: String
    val tabSavedCards: String
    val clear: String

    val noChipTransactionsTitle: String
    val noChipTransactionsCardSubtitle: String
    val noChipTransactionsIdleSubtitle: String
    val noSavedCardsTitle: String
    val noSavedCardsSubtitle: String
    val nfcDisabled: String
    val enable: String

    val share: String
    val copy: String
    val exportPdf: String
    val print: String
    val closeCard: String
    val settings: String
    val currentBalanceTitle: String

    val active: String
    val delete: String
    val viewAll: String
    val showLess: String
    val topUp: String
    val payment: String
    val endingBalance: String
    val tapTimePrefix: String

    val editLocationTitle: String
    val terminalIdLabel: String
    val locationInputLabel: String
    val save: String
    val cancel: String
    val locationSavedToast: String
    val locationUnknown: String

    val receiptTitle: String
    val receiptCard: String
    val receiptNumber: String
    val receiptBalance: String
    val receiptLastTx: String
    val receiptDateTime: String
    val receiptTerminal: String
    val receiptShareTitle: String

    val settingsTitle: String
    val tabScanning: String
    val tabPrivacy: String
    val tabCardsData: String

    val secSensorFeedback: String
    val soundTitle: String
    val soundSubtitle: String
    val hapticsTitle: String
    val hapticsSubtitle: String
    val speakTitle: String
    val speakSubtitle: String
    val secScanCategories: String
    val catBankingTitle: String
    val catBankingSubtitle: String
    val catDomesticTransitTitle: String
    val catDomesticTransitSubtitle: String
    val catRegionalTransitTitle: String
    val catRegionalTransitSubtitle: String

    val secTheme: String
    val themeSubtitle: String
    val themeSystem: String
    val themeLight: String
    val themeDark: String
    val secLanguage: String
    val languageSubtitle: String
    val langSystem: String
    val langIndonesian: String
    val langEnglish: String
    val secCardPrivacy: String
    val maskPanTitle: String
    val maskPanSubtitle: String
    val hideBalanceTitle: String
    val hideBalanceSubtitle: String
    val txColorsTitle: String
    val txColorsSubtitle: String
    val secStorage: String
    val autoSaveTitle: String
    val autoSaveSubtitle: String

    val secSupportedCards: String
    val catBankingHeader: String
    val catDomesticTransitHeader: String
    val catRegionalTransitHeader: String
    val secLocalStorage: String
    val localStorageSubtitle: String
    val deleteAllDataButton: String
    val deleteConfirmTitle: String
    val deleteConfirmMessage: String
    val deleteConfirmButton: String
    val dataClearedToast: String
}

object AppStringsId : AppStrings {
    override val tabChipHistory = "RIWAYAT CHIP"
    override val tabSavedCards = "KARTU TERSIMPAN"
    override val clear = "Bersihkan"

    override val noChipTransactionsTitle = "Tidak ada transaksi chip"
    override val noChipTransactionsCardSubtitle = "Kartu ini tidak memiliki catatan riwayat transaksi di dalam chip."
    override val noChipTransactionsIdleSubtitle = "Tempelkan kartu fisik uang elektronik untuk membaca log transaksi on-chip."
    override val noSavedCardsTitle = "Belum ada kartu tersimpan"
    override val noSavedCardsSubtitle = "Setiap kartu yang dipindai akan otomatis dicatat di sini untuk referensi cepat."
    override val nfcDisabled = "NFC Mati • Nyalakan sensor"
    override val enable = "Aktifkan"

    override val share = "Bagikan"
    override val copy = "Salin"
    override val exportPdf = "Ekspor PDF"
    override val print = "Cetak"
    override val closeCard = "Tutup Kartu"
    override val settings = "Pengaturan"
    override val currentBalanceTitle = "SALDO SAAT INI"

    override val active = "AKTIF"
    override val delete = "Hapus"
    override val viewAll = "View All"
    override val showLess = "Show Less"
    override val topUp = "Top up"
    override val payment = "Payment"
    override val endingBalance = "Saldo Akhir: "
    override val tapTimePrefix = "Tap: "

    override val editLocationTitle = "Ubah Nama Lokasi"
    override val terminalIdLabel = "Terminal ID: "
    override val locationInputLabel = "Nama Lokasi / Merchant"
    override val save = "Simpan"
    override val cancel = "Batal"
    override val locationSavedToast = "Nama lokasi disimpan"
    override val locationUnknown = "Tidak dikenali"

    override val receiptTitle = "=== STRUK SALDO TAPMONEY ==="
    override val receiptCard = "Kartu   : "
    override val receiptNumber = "Nomor   : "
    override val receiptBalance = "Saldo   : "
    override val receiptLastTx = "Transaksi terakhir: "
    override val receiptDateTime = "Tanggal/Jam: "
    override val receiptTerminal = "Terminal: "
    override val receiptShareTitle = "Bagikan Struk"

    override val settingsTitle = "PENGATURAN"
    override val tabScanning = "PEMINDAIAN"
    override val tabPrivacy = "PRIVASI"
    override val tabCardsData = "KARTU & DATA"

    override val secSensorFeedback = "Sensor & Respon"
    override val soundTitle = "Suara pembacaan"
    override val soundSubtitle = "Bunyikan nada beep saat chip kartu berhasil dibaca"
    override val hapticsTitle = "Getar respon"
    override val hapticsSubtitle = "Getar haptik sesaat ketika kartu terdeteksi sensor"
    override val speakTitle = "Ucapkan nominal"
    override val speakSubtitle = "Bacakan sisa saldo kartu setelah pemindaian selesai"
    override val secScanCategories = "Kategori Chip yang Dipindai"
    override val catBankingTitle = "Perbankan Indonesia"
    override val catBankingSubtitle = "Mandiri e-Money, BCA Flazz, Brizzi BRI, TapCash BNI, JakCard, MegaCash"
    override val catDomesticTransitTitle = "Transit Domestik"
    override val catDomesticTransitSubtitle = "KMT Commuter Line & MRT Jakarta (MRTJ)"
    override val catRegionalTransitTitle = "Transit Regional / Global"
    override val catRegionalTransitSubtitle = "CEPAS Singapura (EZ-Link/NETS) & Suica/IC Jepang"

    override val secTheme = "Tema Tampilan"
    override val themeSubtitle = "Pilih tema warna aplikasi"
    override val themeSystem = "Sistem"
    override val themeLight = "Terang"
    override val themeDark = "Gelap"
    override val secLanguage = "Bahasa Aplikasi"
    override val languageSubtitle = "Pilih bahasa tampilan antarmuka aplikasi"
    override val langSystem = "Sistem"
    override val langIndonesian = "Indonesia"
    override val langEnglish = "English"
    override val secCardPrivacy = "Privasi Kartu & Saldo"
    override val maskPanTitle = "Sensor nomor kartu"
    override val maskPanSubtitle = "Sembunyikan digit tengah nomor kartu (PAN masking)"
    override val hideBalanceTitle = "Sembunyikan saldo"
    override val hideBalanceSubtitle = "Samarkan nominal saldo di layar utama"
    override val txColorsTitle = "Warna status transaksi"
    override val txColorsSubtitle = "Gunakan warna hijau untuk top up dan merah untuk pembayaran"
    override val secStorage = "Penyimpanan Riwayat"
    override val autoSaveTitle = "Simpan otomatis"
    override val autoSaveSubtitle = "Simpan kartu dan mutasi transaksi chip ke database lokal perangkat"

    override val secSupportedCards = "Daftar Kartu yang Didukung"
    override val catBankingHeader = "Perbankan Indonesia"
    override val catDomesticTransitHeader = "Transit Domestik"
    override val catRegionalTransitHeader = "Transit Regional / Global"
    override val secLocalStorage = "Penyimpanan Lokal Perangkat"
    override val localStorageSubtitle = "Hapus seluruh riwayat pembacaan kartu dan log transaksi yang tersimpan di dalam memori database lokal perangkat."
    override val deleteAllDataButton = "Hapus Semua Data Tersimpan"
    override val deleteConfirmTitle = "Hapus Semua Data?"
    override val deleteConfirmMessage = "Semua kartu yang tersimpan beserta seluruh riwayat transaksi on-chip akan dihapus permanen dari perangkat ini."
    override val deleteConfirmButton = "Hapus"
    override val dataClearedToast = "Semua data kartu telah dihapus"
}

object AppStringsEn : AppStrings {
    override val tabChipHistory = "CHIP HISTORY"
    override val tabSavedCards = "SAVED CARDS"
    override val clear = "Clear"

    override val noChipTransactionsTitle = "No chip transactions"
    override val noChipTransactionsCardSubtitle = "This card does not have any on-chip transaction logs."
    override val noChipTransactionsIdleSubtitle = "Tap a physical electronic money card to read on-chip transaction logs."
    override val noSavedCardsTitle = "No saved cards yet"
    override val noSavedCardsSubtitle = "Every scanned card will automatically be recorded here for quick reference."
    override val nfcDisabled = "NFC Off • Turn on sensor"
    override val enable = "Enable"

    override val share = "Share"
    override val copy = "Copy"
    override val exportPdf = "Export PDF"
    override val print = "Print"
    override val closeCard = "Close Card"
    override val settings = "Settings"
    override val currentBalanceTitle = "CURRENT BALANCE"

    override val active = "ACTIVE"
    override val delete = "Delete"
    override val viewAll = "View All"
    override val showLess = "Show Less"
    override val topUp = "Top up"
    override val payment = "Payment"
    override val endingBalance = "Ending Balance: "
    override val tapTimePrefix = "Tap: "

    override val editLocationTitle = "Edit Location Name"
    override val terminalIdLabel = "Terminal ID: "
    override val locationInputLabel = "Location / Merchant Name"
    override val save = "Save"
    override val cancel = "Cancel"
    override val locationSavedToast = "Location name saved"
    override val locationUnknown = "Unknown"

    override val receiptTitle = "=== TAPMONEY BALANCE RECEIPT ==="
    override val receiptCard = "Card    : "
    override val receiptNumber = "Number  : "
    override val receiptBalance = "Balance : "
    override val receiptLastTx = "Last Transaction: "
    override val receiptDateTime = "Date/Time: "
    override val receiptTerminal = "Terminal: "
    override val receiptShareTitle = "Share Receipt"

    override val settingsTitle = "SETTINGS"
    override val tabScanning = "SCANNING"
    override val tabPrivacy = "PRIVACY"
    override val tabCardsData = "CARDS & DATA"

    override val secSensorFeedback = "Sensor & Feedback"
    override val soundTitle = "Beep sound"
    override val soundSubtitle = "Play beep sound when card chip is read successfully"
    override val hapticsTitle = "Haptic feedback"
    override val hapticsSubtitle = "Brief vibration when card is detected by sensor"
    override val speakTitle = "Speak balance"
    override val speakSubtitle = "Read remaining card balance aloud after scanning"
    override val secScanCategories = "Scanned Chip Categories"
    override val catBankingTitle = "Indonesian Banking"
    override val catBankingSubtitle = "Mandiri e-Money, BCA Flazz, Brizzi BRI, TapCash BNI, JakCard, MegaCash"
    override val catDomesticTransitTitle = "Domestic Transit"
    override val catDomesticTransitSubtitle = "KMT Commuter Line & MRT Jakarta (MRTJ)"
    override val catRegionalTransitTitle = "Regional / Global Transit"
    override val catRegionalTransitSubtitle = "Singapore CEPAS (EZ-Link/NETS) & Japan Suica/IC"

    override val secTheme = "Display Theme"
    override val themeSubtitle = "Choose application color theme"
    override val themeSystem = "System"
    override val themeLight = "Light"
    override val themeDark = "Dark"
    override val secLanguage = "App Language"
    override val languageSubtitle = "Select application interface language"
    override val langSystem = "System"
    override val langIndonesian = "Indonesia"
    override val langEnglish = "English"
    override val secCardPrivacy = "Card & Balance Privacy"
    override val maskPanTitle = "Mask card number"
    override val maskPanSubtitle = "Hide middle digits of card number (PAN masking)"
    override val hideBalanceTitle = "Hide balance"
    override val hideBalanceSubtitle = "Disguise balance amount on main screen"
    override val txColorsTitle = "Transaction status colors"
    override val txColorsSubtitle = "Use green for top up and red for payment"
    override val secStorage = "History Storage"
    override val autoSaveTitle = "Auto-save"
    override val autoSaveSubtitle = "Save cards and on-chip transactions to device local database"

    override val secSupportedCards = "Supported Card Catalog"
    override val catBankingHeader = "Indonesian Banking"
    override val catDomesticTransitHeader = "Domestic Transit"
    override val catRegionalTransitHeader = "Regional / Global Transit"
    override val secLocalStorage = "Device Local Storage"
    override val localStorageSubtitle = "Delete all card reading history and transaction logs stored in device local database."
    override val deleteAllDataButton = "Delete All Saved Data"
    override val deleteConfirmTitle = "Delete All Data?"
    override val deleteConfirmMessage = "All saved cards and their on-chip transaction logs will be permanently erased from this device."
    override val deleteConfirmButton = "Delete"
    override val dataClearedToast = "All card data has been deleted"
}

fun resolveIsEnglish(appLanguage: String?): Boolean {
    return when (appLanguage) {
        "en" -> true
        "id" -> false
        else -> Locale.getDefault().language.startsWith("en", ignoreCase = true)
    }
}

fun getAppStrings(appLanguage: String?): AppStrings {
    return if (resolveIsEnglish(appLanguage)) AppStringsEn else AppStringsId
}

val LocalAppStrings = compositionLocalOf<AppStrings> { AppStringsId }
