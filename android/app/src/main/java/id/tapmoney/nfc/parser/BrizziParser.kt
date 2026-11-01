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
    private val brizziSelectHistory = byteArrayOf(0x90.toByte(), 0x5A, 0x00, 0x00, 0x03, 0x02, 0x00, 0x00, 0x00)

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

        // Execute DESFire 3DES Mutual Authentication if 16-digit PAN is available
        if (cardNumber.length == 16 && cardNumber.all { it.isDigit() }) {
            authenticateBrizzi(channel, cardNumber, uidHex, log)
        }

        var balance = extractBalance(channel, log)
        val history = readHistory(channel, log)

        if (balance == null && history.isNotEmpty()) {
            val latest = history.firstOrNull { it.balance != null }
            if (latest?.balance != null) {
                balance = latest.balance
                log.append("Brizzi balance recovered from latest history record: $balance\n")
            }
        } else if (balance == 0L && history.isNotEmpty()) {
            val latestWithBal = history.firstOrNull { it.balance != null && it.balance > 0L }
            if (latestWithBal?.balance != null) {
                balance = latestWithBal.balance
                log.append("Brizzi non-zero balance recovered from history: $balance\n")
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

    private fun authenticateBrizzi(channel: ApduChannel, pan: String, uidHex: String, log: StringBuilder): Boolean {
        try {
            // Select App 010000 before initiating challenge
            channel.transceive(brizziSelect)

            // 1. Request challenge from card (Native Authenticate Key 0: 90 0A 00 00 01 00 00)
            val authNativeCmd = byteArrayOf(0x90.toByte(), 0x0A, 0x00, 0x00, 0x01, 0x00, 0x00)
            var respAuth = channel.transceive(authNativeCmd)

            if (!respAuth.hasStatus(0x91, 0xAF)) {
                // Fallback to ISO Authenticate Key 0: 90 1A 00 00 01 00 00
                val authIsoCmd = byteArrayOf(0x90.toByte(), 0x1A, 0x00, 0x00, 0x01, 0x00, 0x00)
                respAuth = channel.transceive(authIsoCmd)
            }

            if (!respAuth.hasStatus(0x91, 0xAF) || respAuth.payload().size < 8) {
                log.append("Brizzi auth challenge request unhandled: ${respAuth.toHexString()}\n")
                return false
            }

            val rndBEnc = respAuth.payload().sliceArray(0 until 8).toHexString()

            // 2. Compute 3DES session key and encrypted challenge response token
            val responsePayload = BrizziCrypto.computeAuthResponse(pan, uidHex, rndBEnc)
            if (responsePayload == null) {
                log.append("Brizzi 3DES response computation failed\n")
                return false
            }

            // 3. Send challenge response: 90 AF 00 00 10 [token 16 bytes] 00
            val sendRespCmd = byteArrayOf(0x90.toByte(), 0xAF.toByte(), 0x00, 0x00, 0x10) +
                    responsePayload.hexToBytes() +
                    byteArrayOf(0x00)

            val finalResp = channel.transceive(sendRespCmd)
            if (finalResp.isDesfireSuccess() || finalResp.hasStatus(0x91, 0x00) || finalResp.hasStatus(0x90, 0x00)) {
                log.append("Brizzi 3DES mutual authentication SUCCESS\n")
                return true
            } else {
                log.append("Brizzi 3DES auth response returned: ${finalResp.toHexString()}\n")
                return false
            }
        } catch (e: Exception) {
            log.append("Brizzi auth error: ${e.message}\n")
            return false
        }
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
        // Probe file 0x01 first (canonical in eSaldo Info), then 0x00
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

        for (fileNo in listOf(0x01, 0x00, 0x02)) {
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

        // 1. App 020000 (canonical history AID in eSaldo Info)
        // 2. App 010000 (active application)
        // 3. Fallbacks: 160000, 030000
        val targetApps: List<ByteArray?> = listOf(
            brizziSelectHistory,
            null, // Stay in active App 010000
            brizziSelect,
            byteArrayOf(0x90.toByte(), 0x5A, 0x00, 0x00, 0x03, 0x16, 0x00, 0x00, 0x00),
            byteArrayOf(0x90.toByte(), 0x5A, 0x00, 0x00, 0x03, 0x03, 0x00, 0x00, 0x00)
        )

        for (appCmd in targetApps) {
            if (appCmd != null) {
                val appResp = channel.transceive(appCmd)
                if (!appResp.isDesfireSuccess() && !appResp.isSuccess()) {
                    continue
                }
            }

            // File 0x01 is the cyclic transaction file
            val candidateFiles = listOf(0x01, 0x00, 0x02, 0x03)

            for (recFile in candidateFiles) {
                try {
                    val readCommands = listOf(
                        // 13-byte standard ReadRecords (all records)
                        byteArrayOf(0x90.toByte(), 0xBB.toByte(), 0x00, 0x00, 0x07, recFile.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
                        // 12-byte ReadRecords without Le
                        byteArrayOf(0x90.toByte(), 0xBB.toByte(), 0x00, 0x00, 0x07, recFile.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
                        // 13-byte ReadRecords (up to 10 records)
                        byteArrayOf(0x90.toByte(), 0xBB.toByte(), 0x00, 0x00, 0x07, recFile.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x00, 0x00, 0x00),
                        // Fallback: ReadData standard file
                        byteArrayOf(0x90.toByte(), 0xBD.toByte(), 0x00, 0x00, 0x07, recFile.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
                    )

                    for (cmdStart in readCommands) {
                        val respStart = channel.transceive(cmdStart)
                        val sb = StringBuilder()

                        if (respStart.isDesfireSuccess() || respStart.hasStatus(0x91, 0xAF)) {
                            sb.append(respStart.payload().toHexString())
                            var more = respStart.hasStatus(0x91, 0xAF)
                            var guard = 0
                            while (more && guard++ < 32) {
                                val cmdNext = byteArrayOf(0x90.toByte(), 0xAF.toByte(), 0x00, 0x00, 0x00)
                                val nextResp = channel.transceive(cmdNext)
                                if (nextResp.size >= 2 && (nextResp[nextResp.size - 2].toInt() and 0xFF) == 0x91) {
                                    more = nextResp.hasStatus(0x91, 0xAF)
                                    sb.append(nextResp.payload().toHexString())
                                } else break
                            }
                        }

                        val rawLog = sb.toString()
                        if (rawLog.length >= 64) {
                            val parsed = parseBrizziRecords(rawLog)
                            if (parsed.isNotEmpty()) {
                                history.addAll(parsed)
                                log.append("Brizzi history found in file $recFile: ${parsed.size} records\n")
                                break
                            }
                        }
                    }

                    if (history.isNotEmpty()) break
                } catch (e: Exception) {
                    log.append("Brizzi log file $recFile err: ${e.message}\n")
                }
            }

            if (history.isNotEmpty()) break
        }

        return history
            .distinctBy { "${it.date}_${it.time}_${it.amount}_${it.type}" }
            .sortedWith(compareByDescending<CardTransactionItem> { "${it.date} ${it.time}" })
    }

    private fun parseBrizziRecords(rawLog: String): List<CardTransactionItem> {
        val list = mutableListOf<CardTransactionItem>()
        val recordLen = 64
        val totalRecords = rawLog.length / recordLen

        for (idx in 0 until totalRecords) {
            val i = idx * recordLen
            val record = rawLog.substring(i, i + recordLen)

            // eSaldo Info canonical mapping:
            // 32..38: Date ddMMyy
            // 38..42: Time HHmm (or 38..44 HHmmss)
            val sub3238 = record.substring(32, 38)
            val sub3844 = record.substring(38, 44)

            // Primary: sub3238 is date (ddMMyy), sub3844 is time (HHmmss)
            // Fallback: sub3844 is date, sub3238 is time
            val dateObj = parseDateCandidate(dateStr = sub3238, timeStr = sub3844)
                ?: parseDateCandidate(dateStr = sub3844, timeStr = sub3238)
                ?: continue

            val typeHex = record.substring(44, 46)
            val amountHex = record.substring(46, 52)
            val balHex4 = if (record.length >= 60) record.substring(52, 60) else ""
            val balHex3 = if (record.length >= 58) record.substring(52, 58) else ""
            val tidHex = if (record.length >= 12) record.substring(4, 12) else null

            val amount = reverseHex(amountHex).toLongOrNull(16) ?: 0L
            val isTopUp = typeHex.equals("EC", ignoreCase = true) ||
                    typeHex.equals("02", ignoreCase = true)

            val b4 = if (balHex4.length == 8) reverseHex(balHex4).toLongOrNull(16) else null
            val b3 = if (balHex3.length == 6) reverseHex(balHex3).toLongOrNull(16) else null
            val txBal = when {
                b4 != null && b4 in 0..20_000_000L -> b4
                b3 != null && b3 in 0..20_000_000L -> b3
                else -> null
            }

            if (amount > 0 || txBal != null) {
                list.add(
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
        return list
    }

    private fun parseDateCandidate(dateStr: String, timeStr: String): java.util.Date? {
        if (dateStr == "000000" || timeStr.startsWith("000000")) return null

        val tStr = if (timeStr.length >= 6) timeStr.substring(0, 6) else timeStr.padEnd(6, '0')

        // 1. Try BCD decimal ddMMyy & HHmmss
        try {
            val d = dateStr.substring(0, 2).toInt()
            val m = dateStr.substring(2, 4).toInt() - 1
            val y = dateStr.substring(4, 6).toInt() + 2000
            val h = tStr.substring(0, 2).toInt()
            val min = tStr.substring(2, 4).toInt()
            val s = tStr.substring(4, 6).toInt()
            if (m in 0..11 && d in 1..31 && h in 0..23 && min in 0..59 && s in 0..59) {
                val cal = java.util.Calendar.getInstance(TxDates.JAKARTA)
                cal.set(y, m, d, h, min, s)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                return cal.time
            }
        } catch (_: Exception) {}

        // 2. Try Hex base 16
        try {
            val d = Integer.parseInt(dateStr.substring(0, 2), 16)
            val m = Integer.parseInt(dateStr.substring(2, 4), 16) - 1
            val y = Integer.parseInt(dateStr.substring(4, 6), 16) + 2000
            val h = Integer.parseInt(tStr.substring(0, 2), 16)
            val min = Integer.parseInt(tStr.substring(2, 4), 16)
            val s = Integer.parseInt(tStr.substring(4, 6), 16)
            if (m in 0..11 && d in 1..31 && h in 0..23 && min in 0..59 && s in 0..59) {
                val cal = java.util.Calendar.getInstance(TxDates.JAKARTA)
                cal.set(y, m, d, h, min, s)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                return cal.time
            }
        } catch (_: Exception) {}

        // 3. Try BCD yyMMdd & HHmmss
        try {
            val y = dateStr.substring(0, 2).toInt() + 2000
            val m = dateStr.substring(2, 4).toInt() - 1
            val d = dateStr.substring(4, 6).toInt()
            val h = tStr.substring(0, 2).toInt()
            val min = tStr.substring(2, 4).toInt()
            val s = tStr.substring(4, 6).toInt()
            if (m in 0..11 && d in 1..31 && h in 0..23 && min in 0..59 && s in 0..59) {
                val cal = java.util.Calendar.getInstance(TxDates.JAKARTA)
                cal.set(y, m, d, h, min, s)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                return cal.time
            }
        } catch (_: Exception) {}

        return null
    }
}
