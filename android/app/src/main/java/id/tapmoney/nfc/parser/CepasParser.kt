package id.tapmoney.nfc.parser

import android.nfc.Tag
import android.nfc.tech.IsoDep
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.CardIssuer
import id.tapmoney.model.CardTransactionItem
import id.tapmoney.nfc.ApduChannel
import id.tapmoney.nfc.TxDates
import id.tapmoney.nfc.hasStatus
import id.tapmoney.nfc.isSuccess
import id.tapmoney.nfc.toHexString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CepasParser : EmoneyParser {
    override val parserName: String = "CEPAS (EZ-Link / NETS)"
    override val category: id.tapmoney.model.CardCategory = id.tapmoney.model.CardCategory.REGIONAL_TRANSIT

    override fun canHandle(tag: Tag): Boolean = tag.techList.contains(IsoDep::class.java.name)

    override fun parse(tag: Tag, channel: ApduChannel?, log: StringBuilder): CardBalanceResult? {
        if (channel == null) return null
        return parseChannel(channel, tag.id.toHexString(), log)
    }

    internal fun parseChannel(channel: ApduChannel, uidHex: String, log: StringBuilder): CardBalanceResult? {
        val purseIds = intArrayOf(0x03, 0x02, 0x01)
        for (purseId in purseIds) {
            val purseCmd = byteArrayOf(0x90.toByte(), 0x32.toByte(), purseId.toByte(), 0x00.toByte(), 0x00.toByte())
            val resp = channel.transceive(purseCmd)
            if (resp.size >= 16 && (resp.isSuccess() || resp.hasStatus(0x90, 0x00))) {
                log.append("CEPAS Purse $purseId response: ${resp.toHexString()}\n")

                // Balance is 3 bytes big-endian at offset 2..4 (in cents)
                val rawBalance = ((resp[2].toLong() and 0xFF) shl 16) or
                        ((resp[3].toLong() and 0xFF) shl 8) or
                        (resp[4].toLong() and 0xFF)

                // CAN (Card Access Number) is usually at bytes 8..15 (8 bytes hex/BCD)
                val canBytes = resp.sliceArray(8 until minOf(16, resp.size))
                val can = canBytes.toHexString()

                // Read transaction history
                val history = readHistory(channel, purseId, log)

                return CardBalanceResult(
                    cardType = "CEPAS (EZ-Link / NETS)",
                    issuer = CardIssuer.CEPAS,
                    cardNumber = can,
                    cardId = uidHex,
                    balance = rawBalance,
                    history = history,
                    balanceKnown = true,
                    debugLog = log.toString()
                )
            }
        }
        return null
    }

    private fun readHistory(channel: ApduChannel, purseId: Int, log: StringBuilder): List<CardTransactionItem> {
        val list = mutableListOf<CardTransactionItem>()
        try {
            val histCmd = byteArrayOf(0x90.toByte(), 0x32.toByte(), purseId.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte())
            val resp = channel.transceive(histCmd)
            if (resp.size >= 18 && resp.hasStatus(0x90, 0x00)) {
                val data = resp.sliceArray(0 until resp.size - 2)
                val recordSize = 16
                val count = data.size / recordSize
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TxDates.JAKARTA }
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = TxDates.JAKARTA }

                for (i in 0 until count) {
                    val offset = i * recordSize
                    val amount = ((data[offset + 4].toLong() and 0xFF) shl 16) or
                            ((data[offset + 5].toLong() and 0xFF) shl 8) or
                            (data[offset + 6].toLong() and 0xFF)
                    val txType = if (data[offset + 7].toInt() == 0x01) "Transit" else "Top-up"

                    val date = dateFormat.format(Date())
                    val time = timeFormat.format(Date())
                    list.add(
                        CardTransactionItem(
                            type = txType,
                            amount = amount,
                            balance = null,
                            date = date,
                            time = time,
                            terminalId = "CEPAS"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            log.append("CEPAS history err: ${e.message}\n")
        }
        return list
    }
}
