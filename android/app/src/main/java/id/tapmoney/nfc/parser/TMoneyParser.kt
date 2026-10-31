package id.tapmoney.nfc.parser

import android.nfc.Tag
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.CardCategory
import id.tapmoney.model.CardIssuer
import id.tapmoney.nfc.ApduChannel
import id.tapmoney.nfc.hasStatus
import id.tapmoney.nfc.hexToBytes
import id.tapmoney.nfc.isSuccess
import id.tapmoney.nfc.toHexString

class TMoneyParser : EmoneyParser {
    override val parserName: String = "T-Money / Cashbee (Korea)"
    override val category: CardCategory = CardCategory.REGIONAL_TRANSIT

    override fun canHandle(tag: Tag): Boolean = true

    override fun parse(tag: Tag, channel: ApduChannel?, log: StringBuilder): CardBalanceResult? {
        if (channel == null) return null
        return parseChannel(channel, tag.id.toHexString(), log)
    }

    internal fun parseChannel(channel: ApduChannel, uidHex: String, log: StringBuilder): CardBalanceResult? {
        // Known AIDs:
        // 1. T-Money: D4 10 00 00 03 00 01
        // 2. Cashbee: D4 10 00 00 14 00 01
        // 3. One Card All Pass (KSX6924): A0 00 00 02 15 01 00
        val targets = listOf(
            Pair("D4100000030001", "T-Money"),
            Pair("D4100000140001", "Cashbee"),
            Pair("A0000002150100", "One Card All Pass")
        )

        for ((aidHex, name) in targets) {
            val selectAid = "00A4040007${aidHex}00".hexToBytes()
            val selectResp = channel.transceive(selectAid)
            if (selectResp.isSuccess() || selectResp.hasStatus(0x90, 0x00)) {
                log.append("Selected $name application ($aidHex)\n")

                // Command GET BALANCE: 90 4C 00 00 04
                val getBalanceCmd = byteArrayOf(
                    0x90.toByte(), 0x4C.toByte(), 0x00.toByte(), 0x00.toByte(), 0x04.toByte()
                )
                var balResp = channel.transceive(getBalanceCmd)
                if (!balResp.isSuccess() && !balResp.hasStatus(0x90, 0x00)) {
                    // Try CLA 0x00: 00 4C 00 00 04
                    balResp = channel.transceive(byteArrayOf(0x00, 0x4C.toByte(), 0x00, 0x00, 0x04))
                }

                if (balResp.size >= 4 && (balResp.isSuccess() || balResp.hasStatus(0x90, 0x00))) {
                    val rawBalance = ((balResp[0].toLong() and 0xFF) shl 24) or
                            ((balResp[1].toLong() and 0xFF) shl 16) or
                            ((balResp[2].toLong() and 0xFF) shl 8) or
                            (balResp[3].toLong() and 0xFF)

                    log.append("$name balance raw: $rawBalance KRW\n")

                    // Attempt to read Card Number from SFI 0x02 or 0x04
                    var cardNumber = uidHex
                    try {
                        val readNumCmd = "00B0820000".hexToBytes()
                        val numResp = channel.transceive(readNumCmd)
                        if (numResp.isSuccess() && numResp.size >= 8) {
                            val hexCard = numResp.sliceArray(0 until minOf(8, numResp.size - 2)).toHexString()
                            if (hexCard.isNotBlank()) cardNumber = hexCard
                        }
                    } catch (_: Exception) {}

                    return CardBalanceResult(
                        cardType = name,
                        issuer = CardIssuer.TMONEY,
                        cardNumber = cardNumber,
                        cardId = uidHex,
                        balance = rawBalance,
                        history = emptyList(),
                        balanceKnown = true,
                        debugLog = log.toString()
                    )
                }
            }
        }
        return null
    }
}
