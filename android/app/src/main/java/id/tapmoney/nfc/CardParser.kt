package id.tapmoney.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.nfc.parser.BrizziParser
import id.tapmoney.nfc.parser.EmoneyParser
import id.tapmoney.nfc.parser.FelicaParser
import id.tapmoney.nfc.parser.FlazzParser
import id.tapmoney.nfc.parser.JakCardParser
import id.tapmoney.nfc.parser.MandiriParser
import id.tapmoney.nfc.parser.MegaCashParser
import id.tapmoney.nfc.parser.MifareLegacyParser
import id.tapmoney.nfc.parser.TapCashParser

object CardParser {
    private const val TAG = "CardParser"
    const val MAX_PLAUSIBLE_BALANCE = 20_000_000L

    private val defaultParsers: List<EmoneyParser> = listOf(
        FelicaParser(),
        MandiriParser(),
        FlazzParser(),
        BrizziParser(),
        TapCashParser(),
        JakCardParser(),
        MegaCashParser(),
        MifareLegacyParser()
    )

    private val customParsers = mutableListOf<EmoneyParser>()

    fun registerParser(parser: EmoneyParser) {
        customParsers.add(parser)
    }

    private var sharedIsoDep: IsoDep? = null
    private var currentTag: Tag? = null

    private fun getSharedIsoDep(tag: Tag): IsoDep? {
        if (currentTag != tag || sharedIsoDep?.isConnected != true) {
            sharedIsoDep = IsoDep.get(tag)
            try {
                if (sharedIsoDep?.isConnected == false) {
                    sharedIsoDep?.timeout = 5000
                    sharedIsoDep?.connect()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Shared IsoDep connect error: ${e.message}")
            }
            currentTag = tag
        }
        return sharedIsoDep
    }

    private fun closeSharedIsoDep() {
        try { sharedIsoDep?.close() } catch (_: Exception) {}
        sharedIsoDep = null
        currentTag = null
    }

    data class Outcome(val card: CardBalanceResult?, val techList: List<String>, val log: String)

    fun parse(tag: Tag): CardBalanceResult? = parseDetailed(tag).card

    @Synchronized
    fun parseDetailed(tag: Tag): Outcome {
        val techList = tag.techList.map { it.substringAfterLast(".") }
        val sb = StringBuilder()
        sb.append("Tech: ${techList.joinToString()}\n")

        val allParsers = customParsers + defaultParsers
        val hasIsoDep = tag.techList.contains(IsoDep::class.java.name)
        val isoDepChannel = if (hasIsoDep) getSharedIsoDep(tag)?.asChannel() else null

        try {
            for (parser in allParsers) {
                if (parser is MifareLegacyParser && sharedIsoDep?.isConnected == true) {
                    closeSharedIsoDep()
                }

                if (parser.canHandle(tag)) {
                    val result = parser.parse(tag, isoDepChannel, sb)
                    if (result != null) {
                        Log.d(TAG, "Recognized: ${result.cardType}")
                        return Outcome(sanityCheck(result), techList, sb.toString())
                    }
                }
            }

            Log.d(TAG, "Unrecognized card")
            return Outcome(null, techList, sb.toString())
        } finally {
            closeSharedIsoDep()
        }
    }

    internal fun sanityCheck(card: CardBalanceResult): CardBalanceResult {
        return if (card.balanceKnown && (card.balance < 0 || card.balance > MAX_PLAUSIBLE_BALANCE)) {
            card.copy(
                balanceKnown = false,
                debugLog = card.debugLog + "\nBalance outside plausible limits: ${card.balance}"
            )
        } else {
            card
        }
    }
}
