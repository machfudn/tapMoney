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

class ChinaTransitParser : EmoneyParser {
    override val parserName: String = "China Transit / T-Union / PBOC"
    override val category: CardCategory = CardCategory.REGIONAL_TRANSIT

    override fun canHandle(tag: Tag): Boolean = true

    override fun parse(tag: Tag, channel: ApduChannel?, log: StringBuilder): CardBalanceResult? {
        if (channel == null) return null
        return parseChannel(channel, tag.id.toHexString(), log)
    }

    internal fun parseChannel(channel: ApduChannel, uidHex: String, log: StringBuilder): CardBalanceResult? {
        val targets = listOf(
            Pair("A000000632010105", "T-Union China Transit"),
            Pair("A00000000386980701", "Beijing Municipal / City Union"),
            Pair("A000000003869807", "Shanghai / Shenzhen Tong")
        )

        for ((aidHex, name) in targets) {
            val lenHex = (aidHex.length / 2).toString(16).padStart(2, '0')
            val selectAid = "00A40400${lenHex}${aidHex}00".hexToBytes()
            val selectResp = channel.transceive(selectAid)
            if (selectResp.isSuccess() || selectResp.hasStatus(0x90, 0x00)) {
                log.append("Selected $name ($aidHex)\n")

                // PBOC Read Electronic Purse (EP) Balance: 80 5C 00 02 04
                var balResp = channel.transceive(byteArrayOf(
                    0x80.toByte(), 0x5C.toByte(), 0x00.toByte(), 0x02.toByte(), 0x04.toByte()
                ))

                // If EP fails, try Electronic Deposit (ED) Balance: 80 5C 00 01 04
                if (!balResp.isSuccess() && !balResp.hasStatus(0x90, 0x00)) {
                    balResp = channel.transceive(byteArrayOf(
                        0x80.toByte(), 0x5C.toByte(), 0x00.toByte(), 0x01.toByte(), 0x04.toByte()
                    ))
                }

                if (balResp.size >= 4 && (balResp.isSuccess() || balResp.hasStatus(0x90, 0x00))) {
                    val rawBalance = ((balResp[0].toLong() and 0xFF) shl 24) or
                            ((balResp[1].toLong() and 0xFF) shl 16) or
                            ((balResp[2].toLong() and 0xFF) shl 8) or
                            (balResp[3].toLong() and 0xFF)

                    log.append("$name balance: $rawBalance cents\n")

                    // Attempt to read Card Number from record 1 (SFI 0x15 or 0x19)
                    var cardNumber = uidHex
                    try {
                        val readRec = "00B201C800".hexToBytes()
                        val recResp = channel.transceive(readRec)
                        if (recResp.isSuccess() && recResp.size >= 20) {
                            val hexPan = recResp.sliceArray(10 until 20).toHexString()
                            if (hexPan.isNotBlank() && !hexPan.startsWith("0000")) {
                                cardNumber = hexPan
                            }
                        }
                    } catch (_: Exception) {}

                    return CardBalanceResult(
                        cardType = name,
                        issuer = CardIssuer.CHINA_TRANSIT,
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
