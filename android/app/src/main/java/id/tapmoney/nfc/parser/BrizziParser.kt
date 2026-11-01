package id.tapmoney.nfc.parser

import android.nfc.Tag
import android.nfc.tech.IsoDep
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.CardIssuer
import id.tapmoney.model.CardTransactionItem
import id.tapmoney.nfc.ApduChannel
import id.tapmoney.nfc.TxDates
import id.tapmoney.nfc.hasStatus
import id.tapmoney.nfc.hexToBytes
import id.tapmoney.nfc.isDesfireSuccess
import id.tapmoney.nfc.isSuccess
import id.tapmoney.nfc.payload
import id.tapmoney.nfc.reverseHex
import id.tapmoney.nfc.toHexString
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BrizziParser : EmoneyParser {
    override val parserName: String = "BRI Brizzi"

    private val brizziSelect = byteArrayOf(0x90.toByte(), 0x5A, 0x00, 0x00, 0x03, 0x01, 0x00, 0x00, 0x00)
    private val brizziSelectAlt = byteArrayOf(0x90.toByte(), 0x5A, 0x00, 0x00, 0x03, 0x16, 0x00, 0x00, 0x00)

    override fun canHandle(tag: Tag): Boolean = tag.techList.contains(IsoDep::class.java.name)

    override fun parse(tag: Tag, channel: ApduChannel?, log: StringBuilder): CardBalanceResult? {
        val apdu = channel ?: return null
        return parseChannel(apdu, tag.id.toHexString(), log)
    }

    internal fun parseChannel(channel: ApduChannel, uidHex: String, log: StringBuilder): CardBalanceResult? {
        var respSelect = channel.transceive(brizziSelect)
        if (!respSelect.isDesfireSuccess() && !respSelect.isSuccess()) {
            respSelect = channel.transceive(brizziSelectAlt)
            if (!respSelect.isDesfireSuccess() && !respSelect.isSuccess()) {
                return null
            }
        }

        log.append("Detected: BRI Brizzi\n")

        val cardNumber = extractCardNumber(channel, log) ?: uidHex
        var balance = extractBalance(channel, log)
        val history = readHistory(channel, log)

        if ((balance == null || balance == 0L) && history.isNotEmpty()) {
            val latestWithBal = history.firstOrNull { it.balance != null && it.balance > 0L }
            if (latestWithBal?.balance != null) {
                balance = latestWithBal.balance
                log.append("Brizzi balance recovered from latest history record: $balance\n")
            }
        }

        return CardBalanceResult(
            cardType = "BRI Brizzi",
            issuer = CardIssuer.BRI,
            cardNumber = cardNumber,
            cardId = uidHex,
            balance = balance ?: 0L,
            history = history,
            balanceKnown = balance != null,
            debugLog = log.toString()
        )
    }

    private fun extractCardNumber(channel: ApduChannel, log: StringBuilder): String? {
        val probeApdus = listOf(
            "90BD0000070000000017000000".hexToBytes(),
            "90BD0000070000000020000000".hexToBytes(),
            "90BD0000070100000017000000".hexToBytes(),
            "90BD0000070100000020000000".hexToBytes()
        )

        for (apdu in probeApdus) {
            try {
                val resp = channel.transceive(apdu)
                if ((resp.isDesfireSuccess() || resp.isSuccess()) && resp.size >= 11) {
                    val hex = resp.toHexString()
                    val payloadHex = resp.payload().toHexString()

                    if (hex.length >= 22) {
                        val cand = hex.substring(6, 22)
                        if (cand.length == 16 && cand.all { it.isDigit() } && isValidBrizziPan(cand)) {
                            log.append("Brizzi PAN from offset 6..22: $cand\n")
                            return cand
                        }
                    }

                    if (payloadHex.length >= 22) {
                        val cand = payloadHex.substring(6, 22)
                        if (cand.length == 16 && cand.all { it.isDigit() } && isValidBrizziPan(cand)) {
                            log.append("Brizzi PAN from payload 6..22: $cand\n")
                            return cand
                        }
                    }

                    val panFound = findPanInHex(payloadHex) ?: findPanInHex(hex)
                    if (panFound != null) {
                        log.append("Brizzi PAN found in scan: $panFound\n")
                        return panFound
                    }

                    val rev = reverseHex(payloadHex)
                    if (rev.length >= 22) {
                        val candRev = rev.substring(6, 22)
                        if (candRev.length == 16 && candRev.all { it.isDigit() } && isValidBrizziPan(candRev)) {
                            log.append("Brizzi PAN from reversed payload: $candRev\n")
                            return candRev
                        }
                    }
                }
            } catch (e: Exception) {
                log.append("Brizzi PAN probe err: ${e.message}\n")
            }
        }
        return null
    }

    private fun isValidBrizziPan(pan: String): Boolean {
        if (pan.length != 16 || !pan.all { it.isDigit() }) return false
        val bins = listOf("522184", "601301", "5379", "507", "6013", "5221")
        return bins.any { pan.startsWith(it) } || pan.startsWith("5") || pan.startsWith("6")
    }

    private fun findPanInHex(hex: String): String? {
        val bins = listOf("522184", "601301", "5379", "507", "6013", "5221")
        for (bin in bins) {
            val idx = hex.indexOf(bin)
            if (idx >= 0 && idx + 16 <= hex.length) {
                val cand = hex.substring(idx, idx + 16)
                if (cand.all { it.isDigit() }) return cand
            }
        }
        return null
    }

    private fun extractBalance(channel: ApduChannel, log: StringBuilder): Long? {
        val fileCandidates = listOf(0x01, 0x00, 0x02, 0x03)
        for (fileNo in fileCandidates) {
            try {
                val cmdBal = byteArrayOf(0x90.toByte(), 0x6C, 0x00, 0x00, 0x01, fileNo.toByte(), 0x00)
                val respBal = channel.transceive(cmdBal)
                if (respBal.isDesfireSuccess() && respBal.payload().size >= 4) {
                    val le = ByteBuffer.wrap(respBal.sliceArray(0..3)).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                    if (le in 0..20_000_000L) {
                        log.append("Brizzi balance from GetValue file $fileNo (LE): $le\n")
                        return le
                    }
                    val be = ByteBuffer.wrap(respBal.sliceArray(0..3)).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
                    if (be in 0..20_000_000L) {
                        log.append("Brizzi balance from GetValue file $fileNo (BE): $be\n")
                        return be
                    }
                }
            } catch (_: Exception) {}
        }

        for (fileNo in listOf(0x01, 0x02)) {
            try {
                val cmdRead = byteArrayOf(0x90.toByte(), 0xBD.toByte(), 0x00, 0x00, 0x07, fileNo.toByte(), 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00)
                val respRead = channel.transceive(cmdRead)
                if ((respRead.isDesfireSuccess() || respRead.isSuccess()) && respRead.payload().size >= 4) {
                    val amt = ByteBuffer.wrap(respRead.sliceArray(0..3)).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                    if (amt in 0..20_000_000L) {
                        log.append("Brizzi balance from ReadData file $fileNo: $amt\n")
                        return amt
                    }
                }
            } catch (_: Exception) {}
        }

        return null
    }

    private fun readHistory(channel: ApduChannel, log: StringBuilder): List<CardTransactionItem> {
        val history = mutableListOf<CardTransactionItem>()
        val recordFiles = listOf(0x01, 0x02, 0x03)

        for (recFile in recordFiles) {
            try {
                val cmdStart = byteArrayOf(0x90.toByte(), 0xBB.toByte(), 0x00, 0x00, 0x07, recFile.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
                val respStart = channel.transceive(cmdStart)
                val sb = StringBuilder()

                if (respStart.isDesfireSuccess() || respStart.hasStatus(0x91, 0xAF)) {
                    sb.append(respStart.payload().toHexString())
                    var more = respStart.hasStatus(0x91, 0xAF)
                    var guard = 0
                    while (more && guard++ < 32) {
                        val cmdNext = byteArrayOf(0x90.toByte(), 0xAF.toByte(), 0x00, 0x00, 0x00)
                        val nextResp = channel.transceive(cmdNext)
                        if (nextResp.size >= 2 && nextResp[nextResp.size - 2] == 0x91.toByte()) {
                            more = nextResp.hasStatus(0x91, 0xAF)
                            sb.append(nextResp.payload().toHexString())
                        } else break
                    }
                }

                val rawLog = sb.toString()
                if (rawLog.length >= 64) {
                    val totalRecords = rawLog.length / 64
                    for (idx in 0 until totalRecords) {
                        val i = idx * 64
                        val record = rawLog.substring(i, i + 64)
                        val dateHex = record.substring(32, 38)
                        val timeHex = record.substring(38, 44)
                        val typeHex = record.substring(44, 46)
                        val amountHex = record.substring(46, 52)
                        val balHex4 = if (record.length >= 60) record.substring(52, 60) else ""
                        val balHex3 = if (record.length >= 58) record.substring(52, 58) else ""
                        val tidHex = if (record.length >= 12) record.substring(4, 12) else null

                        val dateObj = TxDates.parseBcdDateTime(dateHex, timeHex)
                            ?: TxDates.parseHexDateTime(dateHex, timeHex)
                            ?: continue

                        val amount = reverseHex(amountHex).toLongOrNull(16) ?: 0L
                        val isTopUp = typeHex.equals("EC", ignoreCase = true) || typeHex.equals("ED", ignoreCase = true)

                        val b4 = if (balHex4.length == 8) reverseHex(balHex4).toLongOrNull(16) else null
                        val b3 = if (balHex3.length == 6) reverseHex(balHex3).toLongOrNull(16) else null
                        val txBal = when {
                            b4 != null && b4 in 0..20_000_000L -> b4
                            b3 != null && b3 in 0..20_000_000L -> b3
                            else -> null
                        }

                        if (amount > 0 || txBal != null) {
                            history.add(
                                CardTransactionItem(
                                    type = if (isTopUp) "Top up" else "Payment",
                                    amount = amount,
                                    balance = txBal,
                                    date = TxDates.formatDate(dateObj),
                                    time = TxDates.formatTime(dateObj),
                                    terminalId = tidHex?.takeIf { it.isNotBlank() && it != "00000000" }
                                )
                            )
                        }
                    }
                }

                if (history.isNotEmpty()) {
                    break
                }
            } catch (e: Exception) {
                log.append("Brizzi log file $recFile err: ${e.message}\n")
            }
        }

        return history
            .distinctBy { "${it.date}_${it.time}_${it.amount}_${it.type}" }
            .sortedWith(compareByDescending<CardTransactionItem> { "${it.date} ${it.time}" })
    }
}
