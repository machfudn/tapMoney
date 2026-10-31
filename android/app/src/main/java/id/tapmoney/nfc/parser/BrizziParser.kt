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

    override fun canHandle(tag: Tag): Boolean = tag.techList.contains(IsoDep::class.java.name)

    override fun parse(tag: Tag, channel: ApduChannel?, log: StringBuilder): CardBalanceResult? {
        val apdu = channel ?: return null
        return parseChannel(apdu, tag.id.toHexString(), log)
    }

    internal fun parseChannel(channel: ApduChannel, uidHex: String, log: StringBuilder): CardBalanceResult? {
        val respSelect = channel.transceive(brizziSelect)
        if (!respSelect.isDesfireSuccess() && !respSelect.isSuccess()) {
            return null
        }

        log.append("Detected: BRI Brizzi\n")

        var cardNumber = uidHex
        try {
            val apduInfo = "90BD0000070000000017000000".hexToBytes()
            val respInfo = channel.transceive(apduInfo)
            if (respInfo.isSuccess() && respInfo.size >= 30) {
                val info = reverseHex(respInfo.payload().toHexString())
                if (info.length >= 22) cardNumber = info.substring(6, 22)
            }
        } catch (_: Exception) {}

        var balance: Long? = null
        try {
            val cmdBal = byteArrayOf(0x90.toByte(), 0x6C, 0x00, 0x00, 0x01, 0x00, 0x00)
            val respBal = channel.transceive(cmdBal)
            if (respBal.isDesfireSuccess() && respBal.payload().size >= 4) {
                balance = ByteBuffer.wrap(respBal.sliceArray(0..3)).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                log.append("Brizzi balance: $balance\n")
            }
        } catch (e: Exception) {
            log.append("Brizzi balance err: ${e.message}\n")
        }

        val history = readHistory(channel, log)

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

    private fun readHistory(channel: ApduChannel, log: StringBuilder): List<CardTransactionItem> {
        val history = mutableListOf<CardTransactionItem>()

        // 1. Multi-frame DESFire log
        try {
            val cmdStart = byteArrayOf(0x90.toByte(), 0xBB.toByte(), 0x00, 0x00, 0x07, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
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
            if (rawLog.isNotEmpty() && rawLog.length % 64 == 0) {
                for (i in 0 until rawLog.length step 64) {
                    val record = rawLog.substring(i, i + 64)
                    val timeHex = record.substring(32, 38)
                    val dateHex = record.substring(38, 44)
                    val typeHex = record.substring(44, 46)
                    val amountHex = record.substring(46, 52)

                    val dateObj = TxDates.parseHexDateTime(dateHex, timeHex) ?: continue
                    val amount = reverseHex(amountHex).toLongOrNull(16) ?: 0L
                    val isTopUp = typeHex.equals("EC", ignoreCase = true) || typeHex.equals("ED", ignoreCase = true)

                    if (amount > 0) {
                        history.add(
                            CardTransactionItem(
                                type = if (isTopUp) "Top up" else "Payment",
                                amount = amount,
                                balance = null,
                                date = TxDates.formatDate(dateObj),
                                time = TxDates.formatTime(dateObj),
                                terminalId = null
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            log.append("Brizzi frame log err: ${e.message}\n")
        }

        if (history.isNotEmpty()) {
            return history.distinctBy { "${it.date}_${it.time}_${it.amount}" }
        }

        // 2. DESFire record read fallback
        for (rec in 1..10) {
            val apduRec = String.format("90BD000007%02X00000020000000", rec).hexToBytes()
            try {
                val resp = channel.transceive(apduRec)
                if (resp.size < 2) break
                val sw1 = resp[resp.size - 2].toInt() and 0xFF
                if (sw1 != 0x90 && sw1 != 0x91) break

                if (resp.size >= 32) {
                    val hex = resp.toHexString()
                    val dateHex = hex.substring(32, 42)
                    if (dateHex.all { it.isDigit() }) {
                        val year = dateHex.substring(0, 4)
                        val mon = dateHex.substring(4, 6)
                        val day = dateHex.substring(6, 8)
                        val hr = if (dateHex.length >= 10) dateHex.substring(8, 10) else "00"
                        val min = if (dateHex.length >= 12) dateHex.substring(10, 12) else "00"
                        val sec = if (dateHex.length >= 14) dateHex.substring(12, 14) else "00"

                        val amt = ((resp[14].toLong() and 0xFF)) or
                                ((resp[15].toLong() and 0xFF) shl 8) or
                                ((resp[16].toLong() and 0xFF) shl 16)
                        val tid = hex.substring(4, 12)

                        if (amt > 0) {
                            history.add(
                                CardTransactionItem(
                                    type = "Payment",
                                    amount = amt,
                                    balance = null,
                                    date = "$year-$mon-$day",
                                    time = "$hr:$min:$sec",
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

        return history.distinctBy { "${it.date}_${it.time}_${it.amount}" }
    }
}
