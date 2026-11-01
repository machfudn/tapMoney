package id.tapmoney.nfc.parser

import android.nfc.Tag
import android.nfc.tech.IsoDep
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.CardIssuer
import id.tapmoney.model.CardTransactionItem
import id.tapmoney.nfc.ApduChannel
import id.tapmoney.nfc.TxDates
import id.tapmoney.nfc.hexToBytes
import id.tapmoney.nfc.isSuccess
import id.tapmoney.nfc.payload
import id.tapmoney.nfc.reverseHex
import id.tapmoney.nfc.selectApplet
import id.tapmoney.nfc.toHexString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar

class MandiriParser : EmoneyParser {
    override val parserName: String = "Mandiri e-Money"

    private val standardAid = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x18, 0x30, 0x00, 0x01)
    private val fallbackAid = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01)

    override fun canHandle(tag: Tag): Boolean = tag.techList.contains(IsoDep::class.java.name)

    override fun parse(tag: Tag, channel: ApduChannel?, log: StringBuilder): CardBalanceResult? {
        val apdu = channel ?: return null
        return parseChannel(apdu, tag.id.toHexString(), log)
    }

    internal fun parseChannel(channel: ApduChannel, uidHex: String, log: StringBuilder): CardBalanceResult? {
        val selectOk = selectApplet(channel, standardAid) || selectApplet(channel, fallbackAid)
        if (!selectOk) return null

        log.append("Detected: Mandiri e-Money\n")

        var cardNumber = uidHex
        try {
            val respPan = channel.transceive("00B300003F".hexToBytes())
            val panData = respPan.payload()
            if (panData.size >= 8) {
                val candidate = panData.copyOfRange(0, 8).toHexString()
                if (candidate.length == 16 && candidate.all { it.isDigit() }) {
                    cardNumber = candidate
                }
            }
        } catch (_: Exception) {}

        if (cardNumber == uidHex) {
            try {
                val respRec = channel.transceive("00B2010400".hexToBytes())
                if (respRec.isSuccess() && respRec.payload().size >= 8) {
                    val p = respRec.payload()
                    val c1 = p.copyOfRange(0, 8).toHexString()
                    val c2 = if (p.size >= 16) p.copyOfRange(8, 16).toHexString() else ""
                    if (c1.length == 16 && c1.all { it.isDigit() } && c1.startsWith("60")) {
                        cardNumber = c1
                    } else if (c2.length == 16 && c2.all { it.isDigit() } && c2.startsWith("60")) {
                        cardNumber = c2
                    }
                }
            } catch (_: Exception) {}
        }

        val balance = readBalance(channel, log)
        val history = readHistory(channel, cardNumber, log)

        return CardBalanceResult(
            cardType = "Mandiri e-Money",
            issuer = CardIssuer.MANDIRI,
            cardNumber = cardNumber,
            cardId = uidHex,
            balance = balance ?: 0L,
            history = history,
            balanceKnown = balance != null,
            debugLog = log.toString()
        )
    }

    private fun readBalance(channel: ApduChannel, log: StringBuilder): Long? {
        try {
            val cmdBalRecord = byteArrayOf(0x00, 0xB2.toByte(), 0x01, 0x04, 0x00)
            val respRec = channel.transceive(cmdBalRecord)
            if (respRec.isSuccess() && respRec.payload().size >= 16) {
                val bal = ByteBuffer.wrap(respRec.sliceArray(12..15)).int.toLong() and 0xFFFFFFFFL
                log.append("Mandiri balance from SFI 4 rec 1: $bal\n")
                return bal
            }
        } catch (e: Exception) {
            log.append("Mandiri rec1 balance err: ${e.message}\n")
        }

        try {
            val cmdBalDirect = "00B500000A".hexToBytes()
            val respDirect = channel.transceive(cmdBalDirect)
            if (respDirect.isSuccess() && respDirect.payload().size >= 4) {
                val bal = ByteBuffer.wrap(respDirect.sliceArray(0..3)).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                log.append("Mandiri balance from 00B5: $bal\n")
                return bal
            }
        } catch (e: Exception) {
            log.append("Mandiri 00B5 balance err: ${e.message}\n")
        }

        return null
    }

    private fun readHistory(channel: ApduChannel, cardNumber: String, log: StringBuilder): List<CardTransactionItem> {
        val history = mutableListOf<CardTransactionItem>()

        // 1. New card format via 00D1
        try {
            val newLogBuilder = StringBuilder()
            for (i in 0 until 32) {
                val cmd = byteArrayOf(0x00, 0xD1.toByte(), i.toByte(), 0x00, 0x00)
                val resp = channel.transceive(cmd)
                if (!resp.isSuccess()) break
                val data = resp.payload()
                if (data.all { it == 0.toByte() }) break
                newLogBuilder.append(data.toHexString())
            }

            val newLogStr = newLogBuilder.toString()
            if (newLogStr.isNotEmpty() && newLogStr.length % 48 == 0) {
                for (j in 0 until newLogStr.length step 48) {
                    val record = newLogStr.substring(j, j + 48)
                    val dateObj = TxDates.parseBcdDateTime(record.substring(0, 6), record.substring(6, 12)) ?: continue
                    val terminalIdHex = record.substring(14, 28)
                    val terminalIdAscii = try {
                        val bytes = terminalIdHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                        String(bytes, Charsets.US_ASCII).replace(Regex("[^\\x20-\\x7E]"), "").trim()
                    } catch (_: Exception) { "" }
                    val location = if (terminalIdAscii.length >= 3) terminalIdAscii else terminalIdHex

                    val typeInt = record.substring(28, 32).toIntOrNull() ?: 0
                    val isTopUp = typeInt == 100
                    val amount = reverseHex(record.substring(32, 40)).toLongOrNull(16) ?: 0L

                    if (amount > 0) {
                        history.add(
                            CardTransactionItem(
                                type = if (isTopUp) "Top up" else "Payment",
                                amount = amount,
                                balance = null,
                                date = TxDates.formatDate(dateObj),
                                time = TxDates.formatTime(dateObj),
                                terminalId = location.takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            log.append("Mandiri 00D1 hist err: ${e.message}\n")
        }

        if (history.isNotEmpty()) {
            return history.distinctBy { "${it.date}_${it.time}_${it.amount}" }
        }

        // 2. SFI history via 00B2 rec sfi 00
        val isNewCard = cardNumber.startsWith("603298") || cardNumber.startsWith("603299")
        val sfi = if (isNewCard) "24" else "44"
        for (rec in 1..30) {
            val cmd = String.format("00B2%02X%s00", rec, sfi).hexToBytes()
            try {
                val resp = channel.transceive(cmd)
                if (resp.size < 2) break
                val sw1 = resp[resp.size - 2].toInt() and 0xFF
                val sw2 = resp[resp.size - 1].toInt() and 0xFF
                if (sw1 == 0x6A && sw2 == 0x83) break
                if (!resp.isSuccess()) break

                if (resp.size >= 24) {
                    val rawDate = resp.sliceArray(0 until 6).toHexString()
                    if (rawDate != "000000000000" && rawDate != "FFFFFFFFFFFF") {
                        val dateObj = TxDates.parseBcdDateTime(rawDate.substring(0, 6), rawDate.substring(6, 12))
                        val typeCode = (resp[6].toInt() and 0xFF) or ((resp[7].toInt() and 0xFF) shl 8)
                        val isTopUp = typeCode == 100 || typeCode == 256 || typeCode == 336
                        val amount = ((resp[8].toLong() and 0xFF)) or
                                ((resp[9].toLong() and 0xFF) shl 8) or
                                ((resp[10].toLong() and 0xFF) shl 16) or
                                ((resp[11].toLong() and 0xFF) shl 24)
                        val restBal = ((resp[12].toLong() and 0xFF)) or
                                ((resp[13].toLong() and 0xFF) shl 8) or
                                ((resp[14].toLong() and 0xFF) shl 16) or
                                ((resp[15].toLong() and 0xFF) shl 24)
                        val tid = resp.sliceArray(16 until 20).toHexString()

                        if (dateObj != null && (amount > 0 || restBal > 0)) {
                            history.add(
                                CardTransactionItem(
                                    type = if (isTopUp) "Top up" else "Payment",
                                    amount = amount,
                                    balance = restBal,
                                    date = TxDates.formatDate(dateObj),
                                    time = TxDates.formatTime(dateObj),
                                    terminalId = tid
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                break
            }
        }

        if (history.isNotEmpty()) {
            return history.distinctBy { "${it.date}_${it.time}_${it.amount}" }
        }

        // 3. Old Mandiri format
        for (i in 0..9) {
            val cmd = byteArrayOf(0x00, 0xB2.toByte(), i.toByte(), 0x00, 0x00)
            try {
                val resp = channel.transceive(cmd)
                if (!resp.isSuccess() || resp.size < 32) continue
                val recordData = resp.sliceArray(0 until 30)
                fun bcd(b: Byte) = Integer.parseInt(byteArrayOf(b).toHexString(), 10)
                val cal = Calendar.getInstance(TxDates.JAKARTA)
                val m = bcd(recordData[1]) - 1
                val d = bcd(recordData[2])
                cal.set(
                    bcd(recordData[0]) + 2000,
                    if (m < 0) 0 else m,
                    if (d < 1) 1 else d,
                    bcd(recordData[3]),
                    bcd(recordData[4]),
                    bcd(recordData[5])
                )
                cal.set(Calendar.MILLISECOND, 0)

                val amount = ByteBuffer.wrap(recordData.sliceArray(10..13)).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
                val isTopUp = recordData.sliceArray(18..29).toHexString().contains("64")

                if (amount > 0) {
                    history.add(
                        CardTransactionItem(
                            type = if (isTopUp) "Top up" else "Payment",
                            amount = amount,
                            balance = null,
                            date = TxDates.formatDate(cal.time),
                            time = TxDates.formatTime(cal.time),
                            terminalId = null
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        return history.distinctBy { "${it.date}_${it.time}_${it.amount}" }
    }
}
