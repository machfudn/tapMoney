package id.tapmoney

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.bluetooth.BluetoothAdapter
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import id.tapmoney.data.CardDatabase
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.StoredCard
import id.tapmoney.model.UserSettings
import id.tapmoney.nfc.NfcEngine
import id.tapmoney.ui.LocalAppStrings
import id.tapmoney.ui.MainScreen
import id.tapmoney.ui.getAppStrings
import id.tapmoney.ui.resolveIsEnglish
import id.tapmoney.util.BluetoothPrinter
import id.tapmoney.util.ReceiptExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var db: CardDatabase
    private var toneGenerator: ToneGenerator? = null
    private var textToSpeech: TextToSpeech? = null

    private var currentCardState = mutableStateOf<CardBalanceResult?>(null)
    private var savedCardsState = mutableStateOf<List<StoredCard>>(emptyList())
    private var locationAliasesState = mutableStateOf<Map<String, String>>(emptyMap())
    private var settingsState = mutableStateOf(UserSettings())
    private var nfcEnabledState = mutableStateOf(true)
    private var statusMessageState = mutableStateOf("Sensor siap • Tempelkan kartu di belakang ponsel")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        db = CardDatabase.getInstance(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        } catch (ignored: Exception) {}
        try {
            textToSpeech = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) textToSpeech?.language = java.util.Locale("id", "ID")
            }
        } catch (ignored: Exception) {}

        loadSettings()
        loadData()
        checkNfcStatus()

        setContent {
            val appStrings = getAppStrings(settingsState.value.appLanguage, this)
            CompositionLocalProvider(LocalAppStrings provides appStrings) {
                MainScreen(
                    currentCard = currentCardState.value,
                    savedCards = savedCardsState.value,
                    locationAliases = locationAliasesState.value,
                    settings = settingsState.value,
                    nfcEnabled = nfcEnabledState.value,
                    statusMessage = statusMessageState.value,
                    onSettingsChanged = { updateSettings(it) },
                    onClearCurrentCard = { currentCardState.value = null },
                    onDeleteSavedCard = { cardNumber ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            db.deleteCard(cardNumber)
                            withContext(Dispatchers.Main) {
                                if (currentCardState.value?.cardNumber == cardNumber) currentCardState.value = null
                                loadData()
                            }
                        }
                    },
                    onClearAllData = {
                        lifecycleScope.launch(Dispatchers.IO) {
                            db.clearAllSavedCards()
                            withContext(Dispatchers.Main) {
                                currentCardState.value = null
                                loadData()
                            }
                        }
                    },
                    onSaveLocationAlias = { tid, name ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            db.setLocationAlias(tid, name)
                            val aliases = db.getLocationAliases()
                            withContext(Dispatchers.Main) { locationAliasesState.value = aliases }
                        }
                    },
                    onSelectSavedCard = { saved ->
                        currentCardState.value = CardBalanceResult(
                            cardType = saved.cardType,
                            issuer = saved.issuer,
                            cardNumber = saved.cardNumber,
                            cardId = null,
                            balance = saved.balance,
                            history = saved.history,
                            timestamp = saved.timestamp
                        )
                    },
                    onExportPdf = { card ->
                        val isEn = resolveIsEnglish(settingsState.value.appLanguage)
                        val uri = ReceiptExporter.createPdf(this, card, isEn)
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, appStrings.receiptShareTitle))
                    },
                    onPrintReceipt = { card ->
                        val isEn = resolveIsEnglish(settingsState.value.appLanguage)
                        val adapter = BluetoothAdapter.getDefaultAdapter()
                        lifecycleScope.launch(Dispatchers.IO) {
                            val result = BluetoothPrinter.printPaired(adapter, card, isEn)
                            withContext(Dispatchers.Main) {
                                statusMessageState.value = result.fold(
                                    { if (isEn) "Receipt sent to $it" else "Struk terkirim ke $it" },
                                    { if (isEn) "Print failed: ${it.message ?: "printer unavailable"}" else "Cetak gagal: ${it.message ?: "printer tidak tersedia"}" }
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkNfcStatus()
        enableReaderMode()
    }

    override fun onPause() {
        super.onPause()
        disableReaderMode()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            toneGenerator?.release()
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (ignored: Exception) {}
    }

    private fun checkNfcStatus() {
        val adapter = nfcAdapter
        val strings = getAppStrings(settingsState.value.appLanguage, this)
        nfcEnabledState.value = adapter != null && adapter.isEnabled
        if (adapter == null) {
            statusMessageState.value = strings.statusNoHardware
        } else if (!adapter.isEnabled) {
            statusMessageState.value = strings.statusNfcDisabled
        } else {
            statusMessageState.value = strings.statusSensorReady
        }
    }

    private fun enableReaderMode() {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return

        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

        try {
            adapter.enableReaderMode(this, this, flags, null)
        } catch (ignored: Exception) {}
    }

    private fun disableReaderMode() {
        try {
            nfcAdapter?.disableReaderMode(this)
        } catch (ignored: Exception) {}
    }

    override fun onTagDiscovered(tag: Tag?) {
        if (tag == null) return

        val strings = getAppStrings(settingsState.value.appLanguage, this)
        runOnUiThread {
            statusMessageState.value = strings.statusReadingCard
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val result = NfcEngine.readCard(tag, settingsState.value)
            val isEn = resolveIsEnglish(settingsState.value.appLanguage)
            withContext(Dispatchers.Main) {
                if (result != null) {
                    triggerHapticAndSound()
                    if (settingsState.value.speakAmount) {
                        val speechText = if (isEn) "Balance ${result.balance}" else "Saldo ${result.balance} rupiah"
                        textToSpeech?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "balance")
                    }
                    if (settingsState.value.autoSaveHistory) db.saveCard(result)
                    currentCardState.value = result
                    statusMessageState.value = "${strings.statusReadSuccessPrefix}${result.cardType}"
                    loadData()
                } else {
                    statusMessageState.value = strings.statusReadFailed
                }
            }
        }
    }

    private fun triggerHapticAndSound() {
        if (!settingsState.value.enableHaptics && !settingsState.value.enableSound) return
        if (settingsState.value.enableHaptics) try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(120)
                }
            }
        } catch (ignored: Exception) {}

        if (settingsState.value.enableSound) try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (ignored: Exception) {}
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val cards = runCatching { db.getSavedCards() }.getOrDefault(emptyList())
            val aliases = runCatching { db.getLocationAliases() }.getOrDefault(emptyMap())
            withContext(Dispatchers.Main) {
                savedCardsState.value = cards
                locationAliasesState.value = aliases
            }
        }
    }

    private fun loadSettings() {
        val sp = getSharedPreferences("tap_money_prefs", Context.MODE_PRIVATE)
        val sound = sp.getBoolean("enable_sound", true)
        val haptics = sp.getBoolean("enable_haptics", true)
        val mask = sp.getBoolean("mask_card_number", true)
        val autoSave = sp.getBoolean("auto_save_history", true)
        val hideBal = sp.getBoolean("hide_balance", false)
        val speakAmount = sp.getBoolean("speak_amount", false)
        val showTransactionColors = sp.getBoolean("transaction_colors", true)
        val scanDomesticBanking = sp.getBoolean("scan_domestic_banking", true)
        val scanDomesticTransit = sp.getBoolean("scan_domestic_transit", true)
        val scanRegionalTransit = sp.getBoolean("scan_regional_transit", true)
        val themeVal = sp.getString("theme", "system")
        val dark = when (themeVal) {
            "dark" -> true
            "light" -> false
            else -> null
        }
        val langVal = sp.getString("app_language", "system")
        val appLanguage = when (langVal) {
            "id" -> "id"
            "en" -> "en"
            else -> null
        }
        val isEn = resolveIsEnglish(appLanguage)
        try {
            textToSpeech?.language = if (isEn) java.util.Locale.US else java.util.Locale("id", "ID")
        } catch (_: Exception) {}

        settingsState.value = UserSettings(
            enableSound = sound,
            enableHaptics = haptics,
            maskCardNumber = mask,
            autoSaveHistory = autoSave,
            hideBalance = hideBal,
            speakAmount = speakAmount,
            showTransactionColors = showTransactionColors,
            scanDomesticBanking = scanDomesticBanking,
            scanDomesticTransit = scanDomesticTransit,
            scanRegionalTransit = scanRegionalTransit,
            darkTheme = dark,
            appLanguage = appLanguage
        )
    }

    private fun updateSettings(newSettings: UserSettings) {
        settingsState.value = newSettings
        val isEn = resolveIsEnglish(newSettings.appLanguage)
        try {
            textToSpeech?.language = if (isEn) java.util.Locale.US else java.util.Locale("id", "ID")
        } catch (_: Exception) {}

        val sp = getSharedPreferences("tap_money_prefs", Context.MODE_PRIVATE)
        sp.edit().apply {
            putBoolean("enable_sound", newSettings.enableSound)
            putBoolean("enable_haptics", newSettings.enableHaptics)
            putBoolean("mask_card_number", newSettings.maskCardNumber)
            putBoolean("auto_save_history", newSettings.autoSaveHistory)
            putBoolean("hide_balance", newSettings.hideBalance)
            putBoolean("speak_amount", newSettings.speakAmount)
            putBoolean("transaction_colors", newSettings.showTransactionColors)
            putBoolean("scan_domestic_banking", newSettings.scanDomesticBanking)
            putBoolean("scan_domestic_transit", newSettings.scanDomesticTransit)
            putBoolean("scan_regional_transit", newSettings.scanRegionalTransit)
            val themeStr = when (newSettings.darkTheme) {
                true -> "dark"
                false -> "light"
                else -> "system"
            }
            putString("theme", themeStr)
            val langStr = when (newSettings.appLanguage) {
                "id" -> "id"
                "en" -> "en"
                else -> "system"
            }
            putString("app_language", langStr)
            apply()
        }
    }
}
