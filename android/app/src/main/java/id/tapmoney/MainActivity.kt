package id.tapmoney

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
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
import androidx.lifecycle.lifecycleScope
import id.tapmoney.data.CardDatabase
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.StoredCard
import id.tapmoney.model.UserSettings
import id.tapmoney.nfc.NfcEngine
import id.tapmoney.ui.MainScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var db: CardDatabase
    private var toneGenerator: ToneGenerator? = null

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

        loadSettings()
        loadData()

        setContent {
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
                }
            )
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
        } catch (ignored: Exception) {}
    }

    private fun checkNfcStatus() {
        val adapter = nfcAdapter
        nfcEnabledState.value = adapter != null && adapter.isEnabled
        if (adapter == null) {
            statusMessageState.value = "Perangkat tidak memiliki hardware NFC"
        } else if (!adapter.isEnabled) {
            statusMessageState.value = "NFC mati. Silakan aktifkan di Pengaturan."
        } else {
            statusMessageState.value = "Sensor siap • Tempelkan kartu di belakang ponsel"
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

        runOnUiThread {
            statusMessageState.value = "Membaca kartu uang elektronik..."
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val result = NfcEngine.readCard(tag)
            withContext(Dispatchers.Main) {
                if (result != null) {
                    triggerHapticAndSound()
                    if (settingsState.value.autoSaveHistory) db.saveCard(result)
                    currentCardState.value = result
                    statusMessageState.value = "Berhasil membaca: ${result.cardType}"
                    loadData()
                } else {
                    statusMessageState.value = "Gagal membaca kartu atau kartu terlepas terlalu cepat"
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
            val cards = db.getSavedCards()
            val aliases = db.getLocationAliases()
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
        val themeVal = sp.getString("theme", "system")
        val dark = when (themeVal) {
            "dark" -> true
            "light" -> false
            else -> null
        }
        settingsState.value = UserSettings(
            enableSound = sound,
            enableHaptics = haptics,
            maskCardNumber = mask,
            autoSaveHistory = autoSave,
            hideBalance = hideBal,
            darkTheme = dark
        )
    }

    private fun updateSettings(newSettings: UserSettings) {
        settingsState.value = newSettings
        val sp = getSharedPreferences("tap_money_prefs", Context.MODE_PRIVATE)
        sp.edit().apply {
            putBoolean("enable_sound", newSettings.enableSound)
            putBoolean("enable_haptics", newSettings.enableHaptics)
            putBoolean("mask_card_number", newSettings.maskCardNumber)
            putBoolean("auto_save_history", newSettings.autoSaveHistory)
            putBoolean("hide_balance", newSettings.hideBalance)
            val themeStr = when (newSettings.darkTheme) {
                true -> "dark"
                false -> "light"
                else -> "system"
            }
            putString("theme", themeStr)
            apply()
        }
    }
}
