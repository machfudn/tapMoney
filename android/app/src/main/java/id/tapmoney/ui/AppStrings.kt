package id.tapmoney.ui

import android.content.Context
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
    val locationHeader: String
    val editLocationAction: String
    val save: String
    val cancel: String
    val locationSavedToast: String
    val locationUnknown: String

    val cardNumberLabel: String
    val cardNumberCopiedToast: String
    val readyToScan: String
    val statusRead: String
    val toggleMaskCardNumber: String
    val toggleHideBalance: String
    val receiptCopiedToast: String
    val defaultCardLabel: String

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

    val capBalanceHistory: String
    val capBalanceOnly: String
    val capBalanceHistoryStation: String
    val capBalanceLastTx: String
    val capSgdBalanceHistory: String
    val capJpyBalanceHistory: String
    val capKrwBalance: String
    val capCnyBalance: String
    val capAudBalance: String

    val statusNoHardware: String
    val statusNfcDisabled: String
    val statusSensorReady: String
    val statusReadingCard: String
    val statusReadFailed: String
    val statusReadSuccessPrefix: String

    val secAppUpdate: String
    val appUpdateSubtitle: String
    val checkUpdateButton: String
    val checkingUpdate: String
    val updateAvailableTitle: String
    val updateUpToDateTitle: String
    val updateUpToDateMessage: String
    val updateErrorTitle: String
    val downloadApkButton: String
    val updateButton: String
    val openInBrowserButton: String
    val downloadStartedToast: String
    val downloadFailedToast: String
    val changelogHeader: String
    val currentVersionLabel: String
    val latestVersionLabel: String
    val fileSizeLabel: String
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
    override val viewAll = "Lihat Semua"
    override val showLess = "Lihat Lebih Sedikit"
    override val topUp = "Top Up"
    override val payment = "Pembayaran"
    override val endingBalance = "Saldo Akhir: "
    override val tapTimePrefix = "Tap: "

    override val editLocationTitle = "Ubah Nama Lokasi"
    override val terminalIdLabel = "Terminal ID: "
    override val locationInputLabel = "Nama Lokasi / Merchant"
    override val locationHeader = "Lokasi"
    override val editLocationAction = "Ubah Lokasi"
    override val save = "Simpan"
    override val cancel = "Batal"
    override val locationSavedToast = "Nama lokasi disimpan"
    override val locationUnknown = "Tidak dikenali"

    override val cardNumberLabel = "Nomor kartu"
    override val cardNumberCopiedToast = "Nomor kartu disalin"
    override val readyToScan = "Siap Memindai Kartu"
    override val statusRead = "TERBACA"
    override val toggleMaskCardNumber = "Sensor nomor kartu"
    override val toggleHideBalance = "Sembunyikan saldo"
    override val receiptCopiedToast = "Struk disalin ke clipboard"
    override val defaultCardLabel = "KARTU NFC"

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

    override val capBalanceHistory = "Saldo + riwayat"
    override val capBalanceOnly = "Saldo"
    override val capBalanceHistoryStation = "Saldo + riwayat + stasiun"
    override val capBalanceLastTx = "Saldo + transaksi terakhir"
    override val capSgdBalanceHistory = "Saldo SGD + riwayat"
    override val capJpyBalanceHistory = "Saldo JPY + riwayat"
    override val capKrwBalance = "Saldo KRW"
    override val capCnyBalance = "Saldo CNY"
    override val capAudBalance = "Saldo AUD"

    override val statusNoHardware = "Perangkat tidak memiliki hardware NFC"
    override val statusNfcDisabled = "NFC mati. Silakan aktifkan di Pengaturan."
    override val statusSensorReady = "Sensor siap • Tempelkan kartu di belakang ponsel"
    override val statusReadingCard = "Membaca kartu uang elektronik..."
    override val statusReadFailed = "Gagal membaca kartu atau kartu terlepas terlalu cepat"
    override val statusReadSuccessPrefix = "Berhasil membaca: "

    override val secAppUpdate = "Pembaruan Aplikasi"
    override val appUpdateSubtitle = "Periksa rilis terbaru Tap Money langsung dari repositori resmi GitHub."
    override val checkUpdateButton = "Periksa Pembaruan"
    override val checkingUpdate = "Memeriksa pembaruan..."
    override val updateAvailableTitle = "Pembaruan Tersedia"
    override val updateUpToDateTitle = "Versi Terbaru"
    override val updateUpToDateMessage = "Aplikasi sudah menggunakan versi terbaru (%s)."
    override val updateErrorTitle = "Gagal Memeriksa Pembaruan"
    override val downloadApkButton = "Unduh APK"
    override val updateButton = "Perbarui"
    override val openInBrowserButton = "Buka di Browser"
    override val downloadStartedToast = "Memulai pengunduhan APK..."
    override val downloadFailedToast = "Gagal memulai pengunduhan APK"
    override val changelogHeader = "Ringkasan Rilis"
    override val currentVersionLabel = "Versi Saat Ini"
    override val latestVersionLabel = "Versi Terbaru"
    override val fileSizeLabel = "Ukuran File"
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
    override val topUp = "Top Up"
    override val payment = "Payment"
    override val endingBalance = "Ending Balance: "
    override val tapTimePrefix = "Tap: "

    override val editLocationTitle = "Edit Location Name"
    override val terminalIdLabel = "Terminal ID: "
    override val locationInputLabel = "Location / Merchant Name"
    override val locationHeader = "Location"
    override val editLocationAction = "Edit Location"
    override val save = "Save"
    override val cancel = "Cancel"
    override val locationSavedToast = "Location name saved"
    override val locationUnknown = "Unknown"

    override val cardNumberLabel = "Card number"
    override val cardNumberCopiedToast = "Card number copied"
    override val readyToScan = "Ready to Scan Card"
    override val statusRead = "READ"
    override val toggleMaskCardNumber = "Mask card number"
    override val toggleHideBalance = "Hide balance"
    override val receiptCopiedToast = "Receipt copied to clipboard"
    override val defaultCardLabel = "NFC CARD"

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

    override val capBalanceHistory = "Balance + history"
    override val capBalanceOnly = "Balance"
    override val capBalanceHistoryStation = "Balance + history + station"
    override val capBalanceLastTx = "Balance + last transaction"
    override val capSgdBalanceHistory = "SGD balance + history"
    override val capJpyBalanceHistory = "JPY balance + history"
    override val capKrwBalance = "KRW balance"
    override val capCnyBalance = "CNY balance"
    override val capAudBalance = "AUD balance"

    override val statusNoHardware = "Device does not have NFC hardware"
    override val statusNfcDisabled = "NFC is disabled. Please enable it in Settings."
    override val statusSensorReady = "Sensor ready • Tap card on the back of phone"
    override val statusReadingCard = "Reading electronic money card..."
    override val statusReadFailed = "Failed to read card or card removed too quickly"
    override val statusReadSuccessPrefix = "Successfully read: "

    override val secAppUpdate = "App Updates"
    override val appUpdateSubtitle = "Check for the latest Tap Money release directly from the official GitHub repository."
    override val checkUpdateButton = "Check for Updates"
    override val checkingUpdate = "Checking for updates..."
    override val updateAvailableTitle = "Update Available"
    override val updateUpToDateTitle = "Up to Date"
    override val updateUpToDateMessage = "You are already using the latest version (%s)."
    override val updateErrorTitle = "Update Check Failed"
    override val downloadApkButton = "Download APK"
    override val updateButton = "Update"
    override val openInBrowserButton = "Open in Browser"
    override val downloadStartedToast = "Starting APK download..."
    override val downloadFailedToast = "Failed to start APK download"
    override val changelogHeader = "Release Summary"
    override val currentVersionLabel = "Current Version"
    override val latestVersionLabel = "Latest Version"
    override val fileSizeLabel = "File Size"
}

class JsonBackedAppStrings(
    private val data: Map<String, String>,
    private val fallback: AppStrings = AppStringsId
) : AppStrings {
    private fun get(key: String, default: String): String = data[key]?.takeIf { it.isNotBlank() } ?: default

    override val tabChipHistory get() = get("tabChipHistory", fallback.tabChipHistory)
    override val tabSavedCards get() = get("tabSavedCards", fallback.tabSavedCards)
    override val clear get() = get("clear", fallback.clear)

    override val noChipTransactionsTitle get() = get("noChipTransactionsTitle", fallback.noChipTransactionsTitle)
    override val noChipTransactionsCardSubtitle get() = get("noChipTransactionsCardSubtitle", fallback.noChipTransactionsCardSubtitle)
    override val noChipTransactionsIdleSubtitle get() = get("noChipTransactionsIdleSubtitle", fallback.noChipTransactionsIdleSubtitle)
    override val noSavedCardsTitle get() = get("noSavedCardsTitle", fallback.noSavedCardsTitle)
    override val noSavedCardsSubtitle get() = get("noSavedCardsSubtitle", fallback.noSavedCardsSubtitle)
    override val nfcDisabled get() = get("nfcDisabled", fallback.nfcDisabled)
    override val enable get() = get("enable", fallback.enable)

    override val share get() = get("share", fallback.share)
    override val copy get() = get("copy", fallback.copy)
    override val exportPdf get() = get("exportPdf", fallback.exportPdf)
    override val print get() = get("print", fallback.print)
    override val closeCard get() = get("closeCard", fallback.closeCard)
    override val settings get() = get("settings", fallback.settings)
    override val currentBalanceTitle get() = get("currentBalanceTitle", fallback.currentBalanceTitle)

    override val active get() = get("active", fallback.active)
    override val delete get() = get("delete", fallback.delete)
    override val viewAll get() = get("viewAll", fallback.viewAll)
    override val showLess get() = get("showLess", fallback.showLess)
    override val topUp get() = get("topUp", fallback.topUp)
    override val payment get() = get("payment", fallback.payment)
    override val endingBalance get() = get("endingBalance", fallback.endingBalance)
    override val tapTimePrefix get() = get("tapTimePrefix", fallback.tapTimePrefix)

    override val editLocationTitle get() = get("editLocationTitle", fallback.editLocationTitle)
    override val terminalIdLabel get() = get("terminalIdLabel", fallback.terminalIdLabel)
    override val locationInputLabel get() = get("locationInputLabel", fallback.locationInputLabel)
    override val locationHeader get() = get("locationHeader", fallback.locationHeader)
    override val editLocationAction get() = get("editLocationAction", fallback.editLocationAction)
    override val save get() = get("save", fallback.save)
    override val cancel get() = get("cancel", fallback.cancel)
    override val locationSavedToast get() = get("locationSavedToast", fallback.locationSavedToast)
    override val locationUnknown get() = get("locationUnknown", fallback.locationUnknown)

    override val cardNumberLabel get() = get("cardNumberLabel", fallback.cardNumberLabel)
    override val cardNumberCopiedToast get() = get("cardNumberCopiedToast", fallback.cardNumberCopiedToast)
    override val readyToScan get() = get("readyToScan", fallback.readyToScan)
    override val statusRead get() = get("statusRead", fallback.statusRead)
    override val toggleMaskCardNumber get() = get("toggleMaskCardNumber", fallback.toggleMaskCardNumber)
    override val toggleHideBalance get() = get("toggleHideBalance", fallback.toggleHideBalance)
    override val receiptCopiedToast get() = get("receiptCopiedToast", fallback.receiptCopiedToast)
    override val defaultCardLabel get() = get("defaultCardLabel", fallback.defaultCardLabel)

    override val receiptTitle get() = get("receiptTitle", fallback.receiptTitle)
    override val receiptCard get() = get("receiptCard", fallback.receiptCard)
    override val receiptNumber get() = get("receiptNumber", fallback.receiptNumber)
    override val receiptBalance get() = get("receiptBalance", fallback.receiptBalance)
    override val receiptLastTx get() = get("receiptLastTx", fallback.receiptLastTx)
    override val receiptDateTime get() = get("receiptDateTime", fallback.receiptDateTime)
    override val receiptTerminal get() = get("receiptTerminal", fallback.receiptTerminal)
    override val receiptShareTitle get() = get("receiptShareTitle", fallback.receiptShareTitle)

    override val settingsTitle get() = get("settingsTitle", fallback.settingsTitle)
    override val tabScanning get() = get("tabScanning", fallback.tabScanning)
    override val tabPrivacy get() = get("tabPrivacy", fallback.tabPrivacy)
    override val tabCardsData get() = get("tabCardsData", fallback.tabCardsData)

    override val secSensorFeedback get() = get("secSensorFeedback", fallback.secSensorFeedback)
    override val soundTitle get() = get("soundTitle", fallback.soundTitle)
    override val soundSubtitle get() = get("soundSubtitle", fallback.soundSubtitle)
    override val hapticsTitle get() = get("hapticsTitle", fallback.hapticsTitle)
    override val hapticsSubtitle get() = get("hapticsSubtitle", fallback.hapticsSubtitle)
    override val speakTitle get() = get("speakTitle", fallback.speakTitle)
    override val speakSubtitle get() = get("speakSubtitle", fallback.speakSubtitle)
    override val secScanCategories get() = get("secScanCategories", fallback.secScanCategories)
    override val catBankingTitle get() = get("catBankingTitle", fallback.catBankingTitle)
    override val catBankingSubtitle get() = get("catBankingSubtitle", fallback.catBankingSubtitle)
    override val catDomesticTransitTitle get() = get("catDomesticTransitTitle", fallback.catDomesticTransitTitle)
    override val catDomesticTransitSubtitle get() = get("catDomesticTransitSubtitle", fallback.catDomesticTransitSubtitle)
    override val catRegionalTransitTitle get() = get("catRegionalTransitTitle", fallback.catRegionalTransitTitle)
    override val catRegionalTransitSubtitle get() = get("catRegionalTransitSubtitle", fallback.catRegionalTransitSubtitle)

    override val secTheme get() = get("secTheme", fallback.secTheme)
    override val themeSubtitle get() = get("themeSubtitle", fallback.themeSubtitle)
    override val themeSystem get() = get("themeSystem", fallback.themeSystem)
    override val themeLight get() = get("themeLight", fallback.themeLight)
    override val themeDark get() = get("themeDark", fallback.themeDark)
    override val secLanguage get() = get("secLanguage", fallback.secLanguage)
    override val languageSubtitle get() = get("languageSubtitle", fallback.languageSubtitle)
    override val langSystem get() = get("langSystem", fallback.langSystem)
    override val langIndonesian get() = get("langIndonesian", fallback.langIndonesian)
    override val langEnglish get() = get("langEnglish", fallback.langEnglish)
    override val secCardPrivacy get() = get("secCardPrivacy", fallback.secCardPrivacy)
    override val maskPanTitle get() = get("maskPanTitle", fallback.maskPanTitle)
    override val maskPanSubtitle get() = get("maskPanSubtitle", fallback.maskPanSubtitle)
    override val hideBalanceTitle get() = get("hideBalanceTitle", fallback.hideBalanceTitle)
    override val hideBalanceSubtitle get() = get("hideBalanceSubtitle", fallback.hideBalanceSubtitle)
    override val txColorsTitle get() = get("txColorsTitle", fallback.txColorsTitle)
    override val txColorsSubtitle get() = get("txColorsSubtitle", fallback.txColorsSubtitle)
    override val secStorage get() = get("secStorage", fallback.secStorage)
    override val autoSaveTitle get() = get("autoSaveTitle", fallback.autoSaveTitle)
    override val autoSaveSubtitle get() = get("autoSaveSubtitle", fallback.autoSaveSubtitle)

    override val secSupportedCards get() = get("secSupportedCards", fallback.secSupportedCards)
    override val catBankingHeader get() = get("catBankingHeader", fallback.catBankingHeader)
    override val catDomesticTransitHeader get() = get("catDomesticTransitHeader", fallback.catDomesticTransitHeader)
    override val catRegionalTransitHeader get() = get("catRegionalTransitHeader", fallback.catRegionalTransitHeader)
    override val secLocalStorage get() = get("secLocalStorage", fallback.secLocalStorage)
    override val localStorageSubtitle get() = get("localStorageSubtitle", fallback.localStorageSubtitle)
    override val deleteAllDataButton get() = get("deleteAllDataButton", fallback.deleteAllDataButton)
    override val deleteConfirmTitle get() = get("deleteConfirmTitle", fallback.deleteConfirmTitle)
    override val deleteConfirmMessage get() = get("deleteConfirmMessage", fallback.deleteConfirmMessage)
    override val deleteConfirmButton get() = get("deleteConfirmButton", fallback.deleteConfirmButton)
    override val dataClearedToast get() = get("dataClearedToast", fallback.dataClearedToast)

    override val capBalanceHistory get() = get("capBalanceHistory", fallback.capBalanceHistory)
    override val capBalanceOnly get() = get("capBalanceOnly", fallback.capBalanceOnly)
    override val capBalanceHistoryStation get() = get("capBalanceHistoryStation", fallback.capBalanceHistoryStation)
    override val capBalanceLastTx get() = get("capBalanceLastTx", fallback.capBalanceLastTx)
    override val capSgdBalanceHistory get() = get("capSgdBalanceHistory", fallback.capSgdBalanceHistory)
    override val capJpyBalanceHistory get() = get("capJpyBalanceHistory", fallback.capJpyBalanceHistory)
    override val capKrwBalance get() = get("capKrwBalance", fallback.capKrwBalance)
    override val capCnyBalance get() = get("capCnyBalance", fallback.capCnyBalance)
    override val capAudBalance get() = get("capAudBalance", fallback.capAudBalance)

    override val statusNoHardware get() = get("statusNoHardware", fallback.statusNoHardware)
    override val statusNfcDisabled get() = get("statusNfcDisabled", fallback.statusNfcDisabled)
    override val statusSensorReady get() = get("statusSensorReady", fallback.statusSensorReady)
    override val statusReadingCard get() = get("statusReadingCard", fallback.statusReadingCard)
    override val statusReadFailed get() = get("statusReadFailed", fallback.statusReadFailed)
    override val statusReadSuccessPrefix get() = get("statusReadSuccessPrefix", fallback.statusReadSuccessPrefix)

    override val secAppUpdate get() = get("secAppUpdate", fallback.secAppUpdate)
    override val appUpdateSubtitle get() = get("appUpdateSubtitle", fallback.appUpdateSubtitle)
    override val checkUpdateButton get() = get("checkUpdateButton", fallback.checkUpdateButton)
    override val checkingUpdate get() = get("checkingUpdate", fallback.checkingUpdate)
    override val updateAvailableTitle get() = get("updateAvailableTitle", fallback.updateAvailableTitle)
    override val updateUpToDateTitle get() = get("updateUpToDateTitle", fallback.updateUpToDateTitle)
    override val updateUpToDateMessage get() = get("updateUpToDateMessage", fallback.updateUpToDateMessage)
    override val updateErrorTitle get() = get("updateErrorTitle", fallback.updateErrorTitle)
    override val downloadApkButton get() = get("downloadApkButton", fallback.downloadApkButton)
    override val updateButton get() = get("updateButton", fallback.updateButton)
    override val openInBrowserButton get() = get("openInBrowserButton", fallback.openInBrowserButton)
    override val downloadStartedToast get() = get("downloadStartedToast", fallback.downloadStartedToast)
    override val downloadFailedToast get() = get("downloadFailedToast", fallback.downloadFailedToast)
    override val changelogHeader get() = get("changelogHeader", fallback.changelogHeader)
    override val currentVersionLabel get() = get("currentVersionLabel", fallback.currentVersionLabel)
    override val latestVersionLabel get() = get("latestVersionLabel", fallback.latestVersionLabel)
    override val fileSizeLabel get() = get("fileSizeLabel", fallback.fileSizeLabel)
}

object JsonI18nLoader {
    private val cache = mutableMapOf<String, Map<String, String>>()
    private val kvRegex = """"([^"\\]*(?:\\.[^"\\]*)*)"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""".toRegex()

    fun parseJsonString(jsonStr: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (match in kvRegex.findAll(jsonStr)) {
            val key = match.groupValues[1]
            val value = match.groupValues[2]
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
            map[key] = value
        }
        return map
    }

    fun load(context: Context?, langCode: String): Map<String, String> {
        if (context == null) return emptyMap()
        cache[langCode]?.let { return it }
        val loaded = try {
            context.assets.open("lang/$langCode.json").bufferedReader().use { reader ->
                parseJsonString(reader.readText())
            }
        } catch (e: Exception) {
            emptyMap()
        }
        cache[langCode] = loaded
        return loaded
    }
}

fun resolveIsEnglish(appLanguage: String?): Boolean {
    return when (appLanguage) {
        "en" -> true
        "id" -> false
        else -> Locale.getDefault().language.startsWith("en", ignoreCase = true)
    }
}

fun getAppStrings(appLanguage: String?, context: Context? = null): AppStrings {
    val langCode = if (resolveIsEnglish(appLanguage)) "en" else "id"
    val jsonMap = JsonI18nLoader.load(context, langCode)
    val fallback = if (langCode == "en") AppStringsEn else AppStringsId
    return if (jsonMap.isNotEmpty()) JsonBackedAppStrings(jsonMap, fallback) else fallback
}

val LocalAppStrings = compositionLocalOf<AppStrings> { AppStringsId }
