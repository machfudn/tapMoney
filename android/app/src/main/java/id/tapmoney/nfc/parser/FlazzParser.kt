package id.tapmoney.nfc.parser

import android.nfc.Tag
import android.nfc.tech.IsoDep
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.CardIssuer
import id.tapmoney.model.CardTransactionItem
import id.tapmoney.nfc.ApduChannel
import id.tapmoney.nfc.TxDates
import id.tapmoney.nfc.extractDigitsFromAscii
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
            log.append("Detected: BCA Flazz\n")
            return parseGen2(channel, uidHex, log)
        }

        if (selectApplet(channel, flazzGen1Aid)) {
            log.append("Detected: BCA Flazz (Terenkripsi)\n")
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

        val cardTypeName = "BCA Flazz"

        // Card number (PAN)
        var cardNumber = uidHex
        try {
            val bbbbPayload = respBbbb.payload()
            val flazzBins = setOf("6019", "6013", "6011", "6274")

            // 1. Primary: Decompiled eSaldo Info Track 2 extraction (ASCII ;<PAN>=)
            var candidate = extractTrack2PanFromBbbb(bbbbPayload)

            // 2. Secondary: BCD / ASCII scanners
            if (candidate == null) {
                candidate = extractDigitsFromBcd(bbbbPayload, flazzBins)
                    ?: extractDigitsFromAscii(bbbbPayload, flazzBins)
                    ?: extractDigitsFromBcd(bbbbPayload)
                    ?: extractDigitsFromAscii(bbbbPayload)
            }

            // 3. Fallback: Track 2 EMV record read
            if (candidate == null) {
                val respTrack2 = channel.transceive("00B2010C00".hexToBytes())
                if (respTrack2.isSuccess()) {
                    candidate = extractTrack2PanFromBbbb(respTrack2.payload())
                        ?: extractDigitsFromBcd(respTrack2.payload(), flazzBins)
                        ?: extractDigitsFromAscii(respTrack2.payload(), flazzBins)
                        ?: extractDigitsFromBcd(respTrack2.payload())
                }
            }

            // 4. Fallback: Secondary SFI probes
            if (candidate == null) {
                val probes = listOf("00B0820020", "00B0800020", "00B2011400", "00B2010400")
                for (probe in probes) {
                    val respProbe = channel.transceive(probe.hexToBytes())
                    if (respProbe.isSuccess() && respProbe.payload().size >= 8) {
                        candidate = extractTrack2PanFromBbbb(respProbe.payload())
                            ?: extractDigitsFromBcd(respProbe.payload(), flazzBins)
                            ?: extractDigitsFromAscii(respProbe.payload(), flazzBins)
                        if (candidate != null) break
                    }
                }
            }

            // 5. Fallback: GPN EMV Debit applet probe
            if (candidate == null) {
                val emvSelect = channel.transceive("00A4040007A0000000030002".hexToBytes())
                if (emvSelect.isSuccess()) {
                    val respEmvTrack2 = channel.transceive("00B2010C00".hexToBytes())
                    if (respEmvTrack2.isSuccess()) {
                        candidate = extractTrack2PanFromBbbb(respEmvTrack2.payload())
                            ?: extractDigitsFromBcd(respEmvTrack2.payload(), flazzBins)
                            ?: extractDigitsFromAscii(respEmvTrack2.payload(), flazzBins)
                    }
                }
            }

            if (candidate != null && candidate.length in 15..19 && candidate.all { it.isDigit() }) {
                cardNumber = candidate
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

        val history = readHistory(channel, log, balance)

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

    private fun readHistory(channel: ApduChannel, log: StringBuilder, currentBalance: Long? = null): List<CardTransactionItem> {
        val history = mutableListOf<CardTransactionItem>()

        // 1. SFI 4 (0x84) & SFI 5 (0x85) Binary Log Reading
        try {
            val cmdCccc = byteArrayOf(0x00, 0x00, 0x40, 0x0B, 0x00, 0x00, 0x04, 0x02, 0x00, 0x00)
            channel.transceive(cmdCccc)

            val sfis = listOf(0x84, 0x85)
            for (sfi in sfis) {
                val records = mutableListOf<ByteArray>()

                // Primary: 15-byte slices extraction (eSaldo Info parity, avoids Le 60-byte rejection)
                val sfiBuffer = mutableListOf<Byte>()
                val cmd15 = byteArrayOf(0x00, 0xB0.toByte(), sfi.toByte(), 0x00, 0x0F)
                for (chunk in 0 until 16) {
                    cmd15[3] = (chunk * 15).toByte()
                    val resp = channel.transceive(cmd15)
                    if (resp.isSuccess() && resp.size >= 17) {
                        sfiBuffer.addAll(resp.sliceArray(0 until 15).toList())
                    } else {
                        break
                    }
                }
                val chunked = sfiBuffer.chunked(60).filter { it.size == 60 }
                for (c in chunked) {
                    records.add(c.toByteArray())
                }

                // Fallback: If 15-byte slices yielded nothing, attempt direct 60-byte reading
                if (records.isEmpty()) {
                    val cmd60 = byteArrayOf(0x00, 0xB0.toByte(), sfi.toByte(), 0x00, 0x3C)
                    for (rec in 0 until 16) {
                        val offset = rec * 60
                        if (offset > 240) break
                        cmd60[3] = offset.toByte()
                        val resp = channel.transceive(cmd60)
                        if (resp.isSuccess() && resp.size >= 62) {
                            records.add(resp.sliceArray(0 until 60))
                        } else {
                            break
                        }
                    }
                }

                for (record in records) {
                    if (record.size >= 60) {
                        val isPayment = (record[0].toInt() and 0xFF) == 0x04 ||
                                (((record[0].toInt() and 0xFF) shl 8) or (record[1].toInt() and 0xFF)) == 1024

                        val amountBytes = byteArrayOf(0x00, record[6], record[7], record[8])
                        val amount = ByteBuffer.wrap(amountBytes).int.toLong() and 0xFFFFFFFFL

                        // Ending balance decoding from bytes 1..4 (big endian)
                        val balRaw = ByteBuffer.wrap(record.sliceArray(1..4)).int.toLong() and 0xFFFFFFFFL
                        val endingBalance = if (isPayment) balRaw else (balRaw + amount)
                        val validBalance = if (endingBalance in 0L..100_000_000L) endingBalance else null

                        val timeBytes = byteArrayOf(record[38], record[39], record[40], record[41])
                        val timeSecs = ByteBuffer.wrap(timeBytes).int.toLong() and 0xFFFFFFFFL
                        if (timeSecs <= 0L || amount <= 0L) continue

                        val dateObj = TxDates.parseFlazzSeconds(timeSecs) ?: Date(timeSecs * 1000L)

                        val locBytes = record.sliceArray(30..37)
                        val terminalIdHex = locBytes.toHexString()
                        val locationAscii = String(locBytes, Charsets.US_ASCII).replace("\u0000", "").trim()
                        val terminalName = if (locationAscii.length >= 2) locationAscii else terminalIdHex

                        history.add(
                            CardTransactionItem(
                                type = if (isPayment) "Payment" else "Top up",
                                amount = amount,
                                balance = validBalance,
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

        // 2. DESFire Gen 2 circular log reading (combined with SFI history, up to 256 records)
        try {
            channel.transceive(byteArrayOf(0x90.toByte(), 0x32, 0x03, 0x00, 0x00))

            for (rec in 0 until 256) {
                val cmd32 = byteArrayOf(0x90.toByte(), 0x32, 0x03, 0x00, 0x01, rec.toByte(), 0x20)
                try {
                    var resp = channel.transceive(cmd32)
                    if (resp.size < 34 || !resp.isSuccess()) {
                        val cmdBd = String.format("90BD000007%02X00000020000000", rec).hexToBytes()
                        resp = channel.transceive(cmdBd)
                    }
                    if (resp.size < 34 || !resp.isSuccess()) {
                        if (rec == 0) continue
                        break
                    }

                    val hex = resp.toHexString()
                    val typeHex = hex.substring(0, 2)
                    val isPayment = typeHex == "04"
                    val secHex = hex.substring(8, 16)
                    val sec = secHex.toLongOrNull(16) ?: 0L
                    val dateObj = if (sec > 0) TxDates.parseFlazzSeconds(sec) else null

                    val amtRaw = hex.substring(2, 8).toLongOrNull(16) ?: 0L
                    val amount = if (isPayment && amtRaw > 0) (16777216L - amtRaw) else amtRaw

                    val balHex = if (hex.length >= 50) hex.substring(44, 50) else "0"
                    val balRaw = balHex.toLongOrNull(16) ?: 0L
                    val endBal = if (isPayment) Math.abs(balRaw - amount) else (balRaw + amount)
                    val validBal = if (endBal in 0L..100_000_000L) endBal else null

                    val tidHex = if (hex.length >= 44) hex.substring(28, 44) else null
                    val tidAscii = tidHex?.chunked(2)?.mapNotNull {
                        val code = it.toIntOrNull(16) ?: return@mapNotNull null
                        if (code in 32..126) code.toChar() else null
                    }?.joinToString("")?.trim()

                    if (dateObj != null && amount in 1..100_000_000L) {
                        history.add(
                            CardTransactionItem(
                                type = if (isPayment) "Payment" else "Top up",
                                amount = amount,
                                balance = validBal,
                                date = TxDates.formatDate(dateObj),
                                time = TxDates.formatTime(dateObj),
                                terminalId = if (!tidAscii.isNullOrBlank()) tidAscii else tidHex
                            )
                        )
                    }
                } catch (_: Exception) {
                    if (rec == 0) continue
                    break
                }
            }
        } catch (e: Exception) {
            log.append("Flazz DESFire circular log err: ${e.message}\n")
        }

        // 3. Gen 1 cyclic record fallback (if SFI and DESFire returned no records)
        if (history.isEmpty()) {
            for (rec in 1..16) {
                val fallbackRec = String.format("00B2%02X0C00", rec).hexToBytes()
                try {
                    var resp = channel.transceive(fallbackRec)
                    if (!resp.isSuccess() || resp.size < 34) {
                        val fallbackRec2 = String.format("00B2%02X2400", rec).hexToBytes()
                        resp = channel.transceive(fallbackRec2)
                    }
                    if (!resp.isSuccess()) break
                    val hex = resp.toHexString()
                    if (hex.length >= 32) {
                        val amtHex = hex.substring(12, 18)
                        val amount = amtHex.toLongOrNull(16) ?: 0L
                        val dateObj = TxDates.parseBcdDateTime(hex.substring(0, 6), hex.substring(6, 12))
                            ?: TxDates.parseHexDateTime(hex.substring(0, 6), hex.substring(6, 12))
                            ?: Date()
                        if (amount > 0) {
                            history.add(
                                CardTransactionItem(
                                    type = "Payment",
                                    amount = amount,
                                    balance = null,
                                    date = TxDates.formatDate(dateObj),
                                    time = TxDates.formatTime(dateObj),
                                    terminalId = null
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }

        val sorted = history
            .distinctBy { "${it.date}_${it.time}_${it.amount}_${it.type}" }
            .sortedWith(compareByDescending<CardTransactionItem> { "${it.date} ${it.time}" })
            .toMutableList()

        // Continuous ledger balance consistency: propagate backwards if any balance is missing
        if (sorted.isNotEmpty()) {
            if (sorted[0].balance == null && currentBalance != null && currentBalance > 0L) {
                sorted[0] = sorted[0].copy(balance = currentBalance)
            }
            for (i in 1 until sorted.size) {
                if (sorted[i].balance == null) {
                    val prev = sorted[i - 1]
                    if (prev.balance != null) {
                        val computed = if (prev.type.equals("Payment", ignoreCase = true) || prev.type.equals("Pembayaran", ignoreCase = true)) {
                            prev.balance!! + prev.amount
                        } else {
                            prev.balance!! - prev.amount
                        }
                        if (computed in 0L..100_000_000L) {
                            sorted[i] = sorted[i].copy(balance = computed)
                        }
                    }
                }
            }
        }

        return sorted
    }

    /**
     * Extracts 16-digit PAN from Flazz Track 2 ASCII pattern (decompiled from eSaldo Info).
     * Format inside bbbb() payload: ';<PAN>='
     */
    private fun extractTrack2PanFromBbbb(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val bArr2 = ByteArray(payload.size)
        var i8 = 0
        for (b in payload) {
            val i10 = b.toInt() and 0xFF
            if (i10 in 32..127) {
                bArr2[i8++] = b
            } else {
                bArr2[i8++] = 46.toByte() // '.'
            }
        }
        val str = String(bArr2, 0, i8, Charsets.US_ASCII)
        val semi = str.indexOf(';')
        if (semi != -1) {
            val eq = str.indexOf('=', semi)
            if (eq != -1 && eq > semi + 1) {
                val candidate = str.substring(semi + 1, eq).trim()
                if (candidate.length in 15..19 && candidate.all { it.isDigit() }) {
                    return candidate
                }
            }
        }
        val regex = Regex(";?([0-9]{16})=")
        val match = regex.find(str)
        if (match != null) {
            return match.groupValues[1]
        }
        return null
    }
}
