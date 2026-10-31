package id.tapmoney.nfc.parser

import android.nfc.Tag
import android.nfc.tech.IsoDep
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.CardIssuer
import id.tapmoney.model.CardTransactionItem
import id.tapmoney.nfc.ApduChannel
import id.tapmoney.nfc.TxDates
import id.tapmoney.nfc.extractDigitsFromBcd
import id.tapmoney.nfc.hexToBytes
import id.tapmoney.nfc.isSuccess
import id.tapmoney.nfc.payload
import id.tapmoney.nfc.selectApplet
import id.tapmoney.nfc.toHexString
import java.nio.ByteBuffer
import java.util.Date

class FlazzParser : EmoneyParser {
    override val parserName: String = "BCA Flazz"

    private val flazzGen2Aid = byteArrayOf(
        0xA0.toByte(), 0x00, 0x00, 0x00, 0x18, 0x0F, 0x00, 0x00, 0x01, 0x80.toByte(), 0x01
    )
    private val flazzGen1Aid = byteArrayOf(
        0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x86.toByte(), 0x98.toByte(), 0x07, 0x01
    )

    override fun canHandle(tag: Tag): Boolean = tag.techList.contains(IsoDep::class.java.name)

    override fun parse(tag: Tag, channel: ApduChannel?, log: StringBuilder): CardBalanceResult? {
        val apdu = channel ?: return null
        return parseChannel(apdu, tag.id.toHexString(), log)
    }

    internal fun parseChannel(channel: ApduChannel, uidHex: String, log: StringBuilder): CardBalanceResult? {
        if (selectApplet(channel, flazzGen2Aid)) {
            log.append("Detected: BCA Flazz Gen 2\n")
            return parseGen2(channel, uidHex, log)
        }

        if (selectApplet(channel, flazzGen1Aid)) {
            log.append("Detected: BCA Flazz Gen 1 (Terenkripsi)\n")
            return CardBalanceResult(
                cardType = "BCA Flazz (Terenkripsi)",
                issuer = CardIssuer.BCA,
                cardNumber = uidHex,
                cardId = uidHex,
                balance = 0L,
                balanceKnown = false,
                debugLog = log.toString()
            )
        }

        return null
    }

    private fun parseGen2(channel: ApduChannel, uidHex: String, log: StringBuilder): CardBalanceResult? {
        val cmdUuuu = byteArrayOf(0x00, 0xA4.toByte(), 0x01, 0x00, 0x02, 0x02, 0x00)
        val respUuuu = channel.transceive(cmdUuuu)
        if (!respUuuu.isSuccess()) return null

        val cmdBbbb = byteArrayOf(0x00, 0xB0.toByte(), 0x81.toByte(), 0x00, 0x8E.toByte())
        val respBbbb = channel.transceive(cmdBbbb)
        if (!respBbbb.isSuccess()) return null

        val isGen2 = respBbbb.size > 4 && respBbbb[3] == 0x00.toByte()
        val cardTypeName = if (isGen2) "BCA Flazz Gen 2" else "BCA Flazz Gen 1"

        // Card number (PAN)
        var cardNumber = uidHex
        try {
            val respTrack2 = channel.transceive("00B2010C00".hexToBytes())
            if (respTrack2.isSuccess()) {
                val candidate = extractDigitsFromBcd(respTrack2.payload(), setOf("6013", "6019"))
                if (candidate != null) cardNumber = candidate
            }
        } catch (_: Exception) {}

        // Balance
        var balance: Long? = null
        try {
            val cmdDddd = byteArrayOf(0x80.toByte(), 0x32, 0x00, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00)
            val respDddd = channel.transceive(cmdDddd)
            if (respDddd.size >= 5) {
                balance = ((respDddd[1].toLong() and 0xFF) shl 16) or
                        ((respDddd[2].toLong() and 0xFF) shl 8) or
                        (respDddd[3].toLong() and 0xFF)
                log.append("Flazz 8032 balance: $balance\n")
            }
        } catch (e: Exception) {
            log.append("Flazz 8032 err: ${e.message}\n")
        }

        if (balance == null) {
            try {
                val respBal = channel.transceive("805C000204".hexToBytes())
                if (respBal.isSuccess() && respBal.size >= 5) {
                    balance = ((respBal[0].toLong() and 0xFF) shl 16) or
                            ((respBal[1].toLong() and 0xFF) shl 8) or
                            (respBal[2].toLong() and 0xFF)
                    log.append("Flazz 805C balance: $balance\n")
                }
            } catch (e: Exception) {
                log.append("Flazz 805C err: ${e.message}\n")
            }
        }

        val history = readHistory(channel, log)

        return CardBalanceResult(
            cardType = cardTypeName,
            issuer = CardIssuer.BCA,
            cardNumber = cardNumber,
            cardId = uidHex,
            balance = balance ?: 0L,
            history = history,
            balanceKnown = balance != null,
            debugLog = log.toString()
        )
    }

    private fun readHistory(channel: ApduChannel, log: StringBuilder): List<CardTransactionItem> {
        val history = mutableListOf<CardTransactionItem>()

        // 1. Kue-reader Flh4 & Flh5 file reading
        try {
            val cmdCccc = byteArrayOf(0x00, 0x00, 0x40, 0x0B, 0x00, 0x00, 0x04, 0x02, 0x00, 0x00)
            channel.transceive(cmdCccc)

            val cmdFlh4 = byteArrayOf(0x00, 0xB0.toByte(), 0x84.toByte(), 0x00, 0x3C)
            val cmdFlh5 = byteArrayOf(0x00, 0xB0.toByte(), 0x85.toByte(), 0x00, 0x3C)
            val rawLogs = mutableListOf<ByteArray>()

            for (i in 0 until 16) {
                cmdFlh4[3] = (i * 15).toByte()
                val resp = channel.transceive(cmdFlh4)
                if (resp.isSuccess() && resp.size >= 62) {
                    rawLogs.add(resp.sliceArray(0 until resp.size - 2))
                } else break
            }

            for (i in 0 until 16) {
                cmdFlh5[3] = (i * 15).toByte()
                val resp = channel.transceive(cmdFlh5)
                if (resp.isSuccess() && resp.size >= 62) {
                    rawLogs.add(resp.sliceArray(0 until resp.size - 2))
                } else break
            }

            for (record in rawLogs) {
                if (record.size >= 60) {
                    val typeInt = ((record[0].toInt() and 0xFF) shl 8) or (record[1].toInt() and 0xFF)
                    val isPayment = typeInt == 1024
                    val amountBytes = byteArrayOf(0x00, record[6], record[7], record[8])
                    val amount = ByteBuffer.wrap(amountBytes).int.toLong() and 0xFFFFFFFFL

                    val timeBytes = byteArrayOf(record[38], record[39], record[40], record[41])
                    val timeSecs = ByteBuffer.wrap(timeBytes).int.toLong() and 0xFFFFFFFFL
                    val dateObj = Date(timeSecs * 1000L)

                    val locBytes = record.sliceArray(30..37)
                    val terminalIdHex = locBytes.toHexString()
                    val locationAscii = String(locBytes, Charsets.US_ASCII).replace("\u0000", "").trim()
                    val terminalName = if (locationAscii.length >= 2) locationAscii else terminalIdHex

                    if (amount > 0) {
                        history.add(
                            CardTransactionItem(
                                type = if (isPayment) "Payment" else "Top up",
                                amount = amount,
                                balance = null,
                                date = TxDates.formatDate(dateObj),
                                time = TxDates.formatTime(dateObj),
                                terminalId = terminalName
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            log.append("Flazz file log err: ${e.message}\n")
        }

        if (history.isNotEmpty()) {
            return history.distinctBy { "${it.date}_${it.time}_${it.amount}" }
        }

        // 2. DESFire Gen 2 fallback
        val baseDate1980 = 315532800000L
        for (rec in 1..10) {
            val apduRec = String.format("90BD000007%02X00000020000000", rec).hexToBytes()
            try {
                val resp = channel.transceive(apduRec)
                if (resp.size < 2) break
                val sw1 = resp[resp.size - 2].toInt() and 0xFF
                if (sw1 != 0x90 && sw1 != 0x91) break

                val hex = resp.toHexString()
                if (hex.length >= 64) {
                    val typeHex = hex.substring(0, 2)
                    val isTopUp = typeHex == "01" || typeHex == "02"
                    val secHex = hex.substring(8, 16)
                    val sec = secHex.toLongOrNull(16) ?: 0L
                    val txDateMs = baseDate1980 + (sec * 1000)
                    val dateObj = Date(txDateMs)

                    val amtHex = hex.substring(24, 32)
                    val amount = amtHex.toLongOrNull(16) ?: 0L
                    val balHex = if (hex.length >= 50) hex.substring(44, 50) else "0"
                    val restBal = balHex.toLongOrNull(16)
                    val tid = if (hex.length >= 24) hex.substring(16, 24) else null

                    if (amount > 0 || (restBal != null && restBal > 0)) {
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
            } catch (_: Exception) {
                break
            }
        }

        if (history.isNotEmpty()) {
            return history.distinctBy { "${it.date}_${it.time}_${it.amount}" }
        }

        // 3. Gen 1 history fallback
        for (rec in 1..10) {
            val fallbackRec = String.format("00B2%02X0C00", rec).hexToBytes()
            try {
                val resp = channel.transceive(fallbackRec)
                if (!resp.isSuccess()) break
                val hex = resp.toHexString()
                if (hex.length >= 32) {
                    val amtHex = hex.substring(12, 18)
                    val amount = amtHex.toLongOrNull(16) ?: 0L
                    if (amount > 0) {
                        val now = Date()
                        history.add(
                            CardTransactionItem(
                                type = "Payment",
                                amount = amount,
                                balance = null,
                                date = TxDates.formatDate(now),
                                time = TxDates.formatTime(now),
                                terminalId = null
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                break
            }
        }

        return history.distinctBy { "${it.date}_${it.time}_${it.amount}" }
    }
}
