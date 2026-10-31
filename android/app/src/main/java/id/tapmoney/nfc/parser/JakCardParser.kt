package id.tapmoney.nfc.parser

import android.nfc.Tag
import android.nfc.tech.IsoDep
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.CardIssuer
import id.tapmoney.nfc.ApduChannel
import id.tapmoney.nfc.hexToBytes
import id.tapmoney.nfc.isSuccess
import id.tapmoney.nfc.payload
import id.tapmoney.nfc.toHexString
import java.nio.ByteBuffer

class JakCardParser : EmoneyParser {
    override val parserName: String = "Bank DKI JakCard"

    private val jakCardAid1 = "00A404000E315449432E4943415348303101010000".hexToBytes()
    private val jakCardAid2 = "00A4040007A000000003000200".hexToBytes()

    override fun canHandle(tag: Tag): Boolean = tag.techList.contains(IsoDep::class.java.name)

    override fun parse(tag: Tag, channel: ApduChannel?, log: StringBuilder): CardBalanceResult? {
        val apdu = channel ?: return null
        return parseChannel(apdu, tag.id.toHexString(), log)
    }

    internal fun parseChannel(channel: ApduChannel, uidHex: String, log: StringBuilder): CardBalanceResult? {
        // Variant 1: 1TIC.ICASH01
        try {
            val resp1 = channel.transceive(jakCardAid1)
            if (resp1.isSuccess()) {
                log.append("Detected: Bank DKI JakCard (1TIC.ICASH01)\n")
                var balance: Long? = null
                val cmdBal = byteArrayOf(0x90.toByte(), 0x4C, 0x00, 0x00, 0x04)
                val respBal = channel.transceive(cmdBal)
                if (respBal.isSuccess() && respBal.size >= 6) {
                    balance = ByteBuffer.wrap(respBal.sliceArray(0..3)).int.toLong() and 0xFFFFFFFFL
                    log.append("JakCard 904C balance: $balance\n")
                }
                return CardBalanceResult(
                    cardType = "Bank DKI JakCard",
                    issuer = CardIssuer.DKI,
                    cardNumber = uidHex,
                    cardId = uidHex,
                    balance = balance ?: 0L,
                    balanceKnown = balance != null,
                    debugLog = log.toString()
                )
            }
        } catch (e: Exception) {
            log.append("JakCard v1 err: ${e.message}\n")
        }

        // Variant 2: A0000000030002
        try {
            val resp2 = channel.transceive(jakCardAid2)
            if (resp2.isSuccess()) {
                var cardNumber: String? = null
                try {
                    val apduPan = channel.transceive("00B2010C00".hexToBytes())
                    if (apduPan.isSuccess()) {
                        val panHex = apduPan.payload().toHexString()
                        if (panHex.length >= 32) {
                            val candidate = panHex.substring(16, 32)
                            if (candidate.all { it.isDigit() }) cardNumber = candidate
                        }
                    }
                } catch (_: Exception) {}

                // If this is Bank Mega card (starts with 6019), let MegaCashParser handle it
                if (cardNumber != null && cardNumber.startsWith("6019")) {
                    return null
                }

                log.append("Detected: Bank DKI JakCard (A0000000030002)\n")
                var balance: Long? = null
                try {
                    val respBal = channel.transceive("805C000004".hexToBytes())
                    if (respBal.isSuccess()) {
                        val balHex = respBal.payload().toHexString()
                        balance = balHex.take(8).toLongOrNull(16)
                    }
                } catch (e: Exception) {
                    log.append("JakCard v2 bal err: ${e.message}\n")
                }

                return CardBalanceResult(
                    cardType = "Bank DKI JakCard",
                    issuer = CardIssuer.DKI,
                    cardNumber = cardNumber ?: uidHex,
                    cardId = uidHex,
                    balance = balance ?: 0L,
                    balanceKnown = balance != null,
                    debugLog = log.toString()
                )
            }
        } catch (e: Exception) {
            log.append("JakCard v2 err: ${e.message}\n")
        }

        return null
    }
}
