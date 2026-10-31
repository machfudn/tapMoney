package id.tapmoney.nfc.parser

import android.nfc.Tag
import android.nfc.tech.IsoDep
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.CardIssuer
import id.tapmoney.nfc.ApduChannel
import id.tapmoney.nfc.extractDigitsFromBcd
import id.tapmoney.nfc.hexToBytes
import id.tapmoney.nfc.isSuccess
import id.tapmoney.nfc.payload
import id.tapmoney.nfc.selectApplet
import id.tapmoney.nfc.toHexString

class MegaCashParser : EmoneyParser {
    override val parserName: String = "Bank Mega MegaCash"

    private val megaAid = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x00, 0x02)

    override fun canHandle(tag: Tag): Boolean = tag.techList.contains(IsoDep::class.java.name)

    override fun parse(tag: Tag, channel: ApduChannel?, log: StringBuilder): CardBalanceResult? {
        val apdu = channel ?: return null
        return parseChannel(apdu, tag.id.toHexString(), log)
    }

    internal fun parseChannel(channel: ApduChannel, uidHex: String, log: StringBuilder): CardBalanceResult? {
        if (!selectApplet(channel, megaAid)) return null

        var cardNumber: String? = null
        try {
            val numberResp = channel.transceive("00B2010C00".hexToBytes())
            if (numberResp.isSuccess()) {
                cardNumber = extractDigitsFromBcd(numberResp.payload(), setOf("6019"))
            }
        } catch (_: Exception) {}

        if (cardNumber == null) return null

        log.append("Detected: Bank Mega MegaCash\n")

        var balance: Long? = null
        try {
            val balanceResp = channel.transceive("805C000004".hexToBytes())
            if (balanceResp.isSuccess()) {
                val data = balanceResp.payload()
                if (data.size >= 4) {
                    balance = ((data[0].toLong() and 0xFF) shl 24) or
                            ((data[1].toLong() and 0xFF) shl 16) or
                            ((data[2].toLong() and 0xFF) shl 8) or
                            (data[3].toLong() and 0xFF)
                }
            }
        } catch (e: Exception) {
            log.append("MegaCash balance err: ${e.message}\n")
        }

        return CardBalanceResult(
            cardType = "Bank Mega MegaCash",
            issuer = CardIssuer.MEGA,
            cardNumber = cardNumber,
            cardId = uidHex,
            balance = balance ?: 0L,
            balanceKnown = balance != null,
            debugLog = log.toString()
        )
    }
}
