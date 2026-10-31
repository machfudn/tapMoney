package id.tapmoney.nfc.parser

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.CardIssuer
import id.tapmoney.nfc.ApduChannel
import id.tapmoney.nfc.toHexString

class MifareLegacyParser : EmoneyParser {
    override val parserName: String = "Mifare Classic (Legacy)"

    override fun canHandle(tag: Tag): Boolean = tag.techList.contains(MifareClassic::class.java.name)

    override fun parse(tag: Tag, channel: ApduChannel?, log: StringBuilder): CardBalanceResult? {
        val mfc = MifareClassic.get(tag) ?: return null
        val uidHex = tag.id.toHexString()
        return try {
            mfc.timeout = 3000
            mfc.connect()
            val typeStr = when (mfc.type) {
                MifareClassic.TYPE_CLASSIC -> "Classic"
                MifareClassic.TYPE_PLUS -> "Plus"
                MifareClassic.TYPE_PRO -> "Pro"
                else -> "Unknown"
            }
            val info = "Mifare $typeStr ${mfc.size}b (${mfc.sectorCount} Sectors)"
            log.append("Detected: $info\n")

            CardBalanceResult(
                cardType = "Mifare Classic (Legacy)",
                issuer = CardIssuer.UNKNOWN,
                cardNumber = uidHex,
                cardId = uidHex,
                balance = 0L,
                balanceKnown = false,
                debugLog = log.toString()
            )
        } catch (e: Exception) {
            log.append("Mifare Classic err: ${e.message}\n")
            CardBalanceResult(
                cardType = "Mifare Classic (Legacy)",
                issuer = CardIssuer.UNKNOWN,
                cardNumber = uidHex,
                cardId = uidHex,
                balance = 0L,
                balanceKnown = false,
                debugLog = log.toString()
            )
        } finally {
            try { if (mfc.isConnected) mfc.close() } catch (_: Exception) {}
        }
    }
}
