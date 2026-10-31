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
import id.tapmoney.nfc.selectApplet
import id.tapmoney.nfc.toHexString
import java.util.Calendar
import java.util.Date

class TapCashParser : EmoneyParser {
    override val parserName: String = "BNI TapCash"

    private val tapCashAid = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
    private val bniAid = byteArrayOf(0xA0.toByte(), 0x00, 0x42, 0x4E, 0x49, 0x10, 0x00, 0x01)

    override fun canHandle(tag: Tag): Boolean = tag.techList.contains(IsoDep::class.java.name)

    override fun parse(tag: Tag, channel: ApduChannel?, log: StringBuilder): CardBalanceResult? {
        val apdu = channel ?: return null
        return parseChannel(apdu, tag.id.toHexString(), log)
    }

    internal fun parseChannel(channel: ApduChannel, uidHex: String, log: StringBuilder): CardBalanceResult? {
        val selectOk = selectApplet(channel, tapCashAid) || selectApplet(channel, bniAid)
        if (!selectOk) return null

        log.append("Detected: BNI TapCash\n")

        var cardNumber = uidHex
        var balance: Long? = null

        try {
            val cmdBal = "9032030000".hexToBytes()
            var respBal = channel.transceive(cmdBal)
            if (!respBal.isSuccess()) {
                respBal = channel.transceive("90320300".hexToBytes())
            }

            if (respBal.isSuccess() && respBal.size >= 5) {
                balance = ((respBal[2].toLong() and 0xFF) shl 16) or
                        ((respBal[3].toLong() and 0xFF) shl 8) or
                        (respBal[4].toLong() and 0xFF)
                log.append("TapCash balance: $balance\n")

                val payload = respBal.payload()
                if (payload.size >= 16) {
                    val candidate = payload.copyOfRange(8, 16).toHexString()
                    if (!candidate.all { it == '0' || it == 'F' }) {
                        cardNumber = candidate
                    }
                }
            }
        } catch (e: Exception) {
            log.append("TapCash balance err: ${e.message}\n")
        }

        val history = readHistory(channel, log)

        return CardBalanceResult(
            cardType = "BNI TapCash",
            issuer = CardIssuer.BNI,
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
        val epoch1995 = Calendar.getInstance(TxDates.JAKARTA).apply {
            set(1995, 0, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // 1. Single record iteration 9032010001{rec}00
        for (i in 1..10) {
            val cmd = byteArrayOf(0x90.toByte(), 0x32, 0x01, 0x00, 0x01, i.toByte(), 0x00)
            try {
                val resp = channel.transceive(cmd)
                if (!resp.isSuccess() || resp.size < 18) break
                val record = resp.sliceArray(0 until 16)
                val tx = decodeRecord(record, epoch1995)
                if (tx != null) history.add(tx)
            } catch (_: Exception) {
                break
            }
        }

        if (history.isNotEmpty()) {
            return history.distinctBy { "${it.date}_${it.time}_${it.amount}" }
        }

        // 2. Batch read 903203000200F0 fallback
        try {
            val cmdBatch = byteArrayOf(0x90.toByte(), 0x32, 0x03, 0x00, 0x02, 0x00, 0xF0.toByte())
            val resp = channel.transceive(cmdBatch)
            if (resp.isSuccess()) {
                val data = resp.payload()
                for (offset in 0 until data.size step 16) {
                    if (offset + 16 > data.size) break
                    val record = data.sliceArray(offset until offset + 16)
                    val tx = decodeRecord(record, epoch1995)
                    if (tx != null) history.add(tx)
                }
            }
        } catch (e: Exception) {
            log.append("TapCash batch hist err: ${e.message}\n")
        }

        return history.distinctBy { "${it.date}_${it.time}_${it.amount}" }
    }

    companion object {
        internal fun decodeRecord(record: ByteArray, epoch1995Ms: Long): CardTransactionItem? {
            if (record.size < 8) return null
            val typeByte = record[0].toInt() and 0xFF
            val isPayment = typeByte == 1
            val isTopUp = typeByte == 3 || typeByte == 4 || typeByte == 6
            if (!isPayment && !isTopUp) return null

            val rawAmt = ((record[1].toInt() and 0xFF) shl 16) or
                    ((record[2].toInt() and 0xFF) shl 8) or
                    (record[3].toInt() and 0xFF)
            val signedAmt = if ((record[1].toInt() and 0x80) != 0) rawAmt or (-0x1000000) else rawAmt
            val amount = Math.abs(signedAmt.toLong())

            val sec = ((record[4].toLong() and 0xFF) shl 24) or
                    ((record[5].toLong() and 0xFF) shl 16) or
                    ((record[6].toLong() and 0xFF) shl 8) or
                    (record[7].toLong() and 0xFF)
            val txDateMs = epoch1995Ms + sec * 1000L
            val dateObj = Date(txDateMs)

            val terminalIdHex = if (record.size >= 14) {
                record.sliceArray(8 until 14).toHexString()
            } else null

            return CardTransactionItem(
                type = if (isTopUp) "Top up" else "Payment",
                amount = amount,
                balance = null,
                date = TxDates.formatDate(dateObj),
                time = TxDates.formatTime(dateObj),
                terminalId = terminalIdHex
            )
        }
    }
}
