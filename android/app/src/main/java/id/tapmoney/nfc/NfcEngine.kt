package id.tapmoney.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.NfcF
import android.util.Log
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.CardIssuer
import id.tapmoney.model.CardTransactionItem
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object NfcEngine {
    private const val TAG = "NfcEngine"

    fun readCard(tag: Tag): CardBalanceResult? {
        val techList = tag.techList.toList()
        val uidHex = bytesToHex(tag.id)

        if (techList.contains(IsoDep::class.java.name)) {
            val isoDep = IsoDep.get(tag) ?: return null
            try {
                isoDep.timeout = 5000
                isoDep.connect()

                // 1. Mandiri e-Money
                val mandiriResult = probeMandiri(isoDep, uidHex)
                if (mandiriResult != null) return mandiriResult

                // 2. BCA Flazz (Gen 1 & Gen 2)
                val flazzResult = probeFlazz(isoDep, uidHex)
                if (flazzResult != null) return flazzResult

                // 3. BRI Brizzi
                val brizziResult = probeBrizzi(isoDep, uidHex)
                if (brizziResult != null) return brizziResult

                // 4. BNI TapCash
                val tapCashResult = probeTapCash(isoDep, uidHex)
                if (tapCashResult != null) return tapCashResult

                // 5. Bank DKI JakCard
                val jakCardResult = probeJakCard(isoDep, uidHex)
                if (jakCardResult != null) return jakCardResult

                // 6. Bank Mega MegaCash
                val megaResult = probeMegaCash(isoDep, uidHex)
                if (megaResult != null) return megaResult

                // Unknown ISO-DEP tags are not successful card reads.
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Error probing IsoDep", e)
                return null
            } finally {
                try {
                    if (isoDep.isConnected) isoDep.close()
                } catch (ignored: Exception) {}
            }
        } else if (techList.contains(NfcF::class.java.name)) {
            val nfcF = NfcF.get(tag) ?: return null
            try {
                nfcF.timeout = 3000
                nfcF.connect()
                return probeKmt(nfcF, uidHex)
            } catch (e: Exception) {
                Log.e(TAG, "Error probing NfcF (KMT)", e)
                return null
            } finally {
                try {
                    if (nfcF.isConnected) nfcF.close()
                } catch (ignored: Exception) {}
            }
        }

        return null
    }

    // --- MANDIRI E-MONEY ---
    private fun probeMandiri(isoDep: IsoDep, uidHex: String): CardBalanceResult? {
        val apduSelect = hexToBytes("00A40400080000000000000001")
        val respSelect = isoDep.transceive(apduSelect)
        if (!isSuccess(respSelect)) return null

        val apduPan = hexToBytes("00B300003F")
        val respPan = isoDep.transceive(apduPan)
        val panData = responseData(respPan)
        if (panData.size < 8) return null
        val cardNumber = bytesToHex(panData.copyOfRange(0, 8))

        val apduBalance = hexToBytes("00B500000A")
        val respBalance = isoDep.transceive(apduBalance)
        if (!isSuccess(respBalance) || respBalance.size < 6) return null
        var balance = 0L
        if (respBalance.size >= 4) {
            balance = ((respBalance[0].toLong() and 0xFF)) or
                    ((respBalance[1].toLong() and 0xFF) shl 8) or
                    ((respBalance[2].toLong() and 0xFF) shl 16) or
                    ((respBalance[3].toLong() and 0xFF) shl 24)
        }

        // Mandiri history records (records 1..30)
        // Check SFI: new cards (prefix 603298, 603299) use SFI 24, older use 44
        val history = mutableListOf<CardTransactionItem>()
        val isNewCard = cardNumber.startsWith("603298") || cardNumber.startsWith("603299")
        val sfi = if (isNewCard) "24" else "44"

        for (rec in 1..30) {
            val apduRec = hexToBytes(String.format("00B2%02X%s00", rec, sfi))
            try {
                val resp = isoDep.transceive(apduRec)
                if (resp == null || resp.size < 2) break
                val sw1 = resp[resp.size - 2].toInt() and 0xFF
                val sw2 = resp[resp.size - 1].toInt() and 0xFF
                // 6A 83 = Record not found, stop immediately to prevent card drop
                if (sw1 == 0x6A && sw2 == 0x83) break
                if (!isSuccess(resp)) break

                if (resp.size >= 24) {
                    val rawDate = bytesToHex(resp.sliceArray(0 until 6)) // ddMMyyHHmmss
                    if (rawDate != "000000000000" && rawDate != "FFFFFFFFFFFF") {
                        val dd = rawDate.substring(0, 2)
                        val MM = rawDate.substring(2, 4)
                        val yy = rawDate.substring(4, 6)
                        val hh = rawDate.substring(6, 8)
                        val mm = rawDate.substring(8, 10)
                        val ss = rawDate.substring(10, 12)
                        val dateStr = "20$yy-$MM-$dd"
                        val timeStr = "$hh:$mm:$ss"

                        val typeCode = (resp[6].toInt() and 0xFF) or ((resp[7].toInt() and 0xFF) shl 8)
                        val isTopUp = typeCode == 100 || typeCode == 256 || typeCode == 336
                        val typeStr = if (isTopUp) "Top up" else "Payment"

                        val amount = ((resp[8].toLong() and 0xFF)) or
                                ((resp[9].toLong() and 0xFF) shl 8) or
                                ((resp[10].toLong() and 0xFF) shl 16) or
                                ((resp[11].toLong() and 0xFF) shl 24)

                        val restBalance = ((resp[12].toLong() and 0xFF)) or
                                ((resp[13].toLong() and 0xFF) shl 8) or
                                ((resp[14].toLong() and 0xFF) shl 16) or
                                ((resp[15].toLong() and 0xFF) shl 24)

                        val tid = bytesToHex(resp.sliceArray(16 until 20))

                        if (amount > 0 || restBalance > 0) {
                            history.add(
                                CardTransactionItem(
                                    type = typeStr,
                                    amount = amount,
                                    balance = restBalance,
                                    date = dateStr,
                                    time = timeStr,
                                    terminalId = tid
                                )
                            )
                        }
                    }
                }
            } catch (ignored: Exception) {
                break
            }
        }

        return CardBalanceResult(
            cardType = "Mandiri e-Money",
            issuer = CardIssuer.MANDIRI,
            cardNumber = cardNumber,
            cardId = uidHex,
            balance = balance,
            history = history
        )
    }

    // --- BCA FLAZZ ---
    private fun probeFlazz(isoDep: IsoDep, uidHex: String): CardBalanceResult? {
        val apduSelect = hexToBytes("00A404000BA00000180F0000018001")
        val respSelect = isoDep.transceive(apduSelect)
        if (!isSuccess(respSelect)) return null

        val respTrack2 = try { isoDep.transceive(hexToBytes("00B2010C00")) } catch (_: Exception) { return null }
        if (!isSuccess(respTrack2)) return null
        val cardNumber = extractDigitsFromBcd(responseData(respTrack2), setOf("6013", "6019")) ?: return null

        var balance = 0L
        val apduLiveBalance = hexToBytes("805C000204")
        try {
            val respBal = isoDep.transceive(apduLiveBalance)
            if (isSuccess(respBal) && respBal.size >= 5) {
                balance = ((respBal[0].toLong() and 0xFF) shl 16) or
                        ((respBal[1].toLong() and 0xFF) shl 8) or
                        (respBal[2].toLong() and 0xFF)
            }
        } catch (ignored: Exception) {}

        val history = mutableListOf<CardTransactionItem>()
        val baseDate1980 = 315532800000L // 1980-01-01 UTC in ms
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Jakarta") }
        val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Jakarta") }

        // Attempt Gen 2 History reading first
        var foundGen2 = false
        for (rec in 1..10) {
            val apduRec = hexToBytes(String.format("90BD000007%02X00000020000000", rec))
            try {
                val resp = isoDep.transceive(apduRec)
                if (resp == null || resp.size < 2) break
                val sw1 = resp[resp.size - 2].toInt() and 0xFF
                if (sw1 != 0x90 && sw1 != 0x91) break

                val hex = bytesToHex(resp)
                if (hex.length >= 64) {
                    foundGen2 = true
                    // Flazz Gen 2 (32 bytes = 64 hex chars)
                    val typeHex = hex.substring(0, 2)
                    val isTopUp = typeHex == "01" || typeHex == "02"
                    val typeStr = if (isTopUp) "Top up" else "Payment"

                    val secHex = hex.substring(8, 16)
                    val sec = secHex.toLongOrNull(16) ?: 0L
                    val txDateMs = baseDate1980 + (sec * 1000)
                    val dateStr = sdfDate.format(Date(txDateMs))
                    val timeStr = sdfTime.format(Date(txDateMs))

                    val amtHex = hex.substring(24, 32)
                    val amount = amtHex.toLongOrNull(16) ?: 0L

                    val balHex = if (hex.length >= 50) hex.substring(44, 50) else "0"
                    val restBal = balHex.toLongOrNull(16)

                    val tid = if (hex.length >= 24) hex.substring(16, 24) else null

                    if (amount > 0 || (restBal != null && restBal > 0)) {
                        history.add(
                            CardTransactionItem(
                                type = typeStr,
                                amount = amount,
                                balance = restBal,
                                date = dateStr,
                                time = timeStr,
                                terminalId = tid
                            )
                        )
                    }
                }
            } catch (ignored: Exception) {
                break
            }
        }

        // Fallback for Flazz Gen 1 if Gen 2 history yielded no records
        if (!foundGen2) {
            for (rec in 1..10) {
                val fallbackRec = hexToBytes(String.format("00B2%02X0C00", rec))
                try {
                    val resp = isoDep.transceive(fallbackRec)
                    if (resp == null || !isSuccess(resp)) break
                    val hex = bytesToHex(resp)
                    if (hex.length >= 32) {
                        val amtHex = hex.substring(12, 18)
                        val amount = amtHex.toLongOrNull(16) ?: 0L
                        if (amount > 0) {
                            history.add(
                                CardTransactionItem(
                                    type = "Transaction",
                                    amount = amount,
                                    balance = null,
                                    date = sdfDate.format(Date()),
                                    time = sdfTime.format(Date()),
                                    terminalId = null
                                )
                            )
                        }
                    }
                } catch (ignored: Exception) {
                    break
                }
            }
        }

        return CardBalanceResult(
            cardType = "BCA Flazz",
            issuer = CardIssuer.BCA,
            cardNumber = cardNumber,
            cardId = uidHex,
            balance = balance,
            history = history
        )
    }

    // --- BRI BRIZZI ---
    private fun probeBrizzi(isoDep: IsoDep, uidHex: String): CardBalanceResult? {
        val apduSelect = hexToBytes("905A00000301000000")
        val respSelect = isoDep.transceive(apduSelect)
        if (!isSuccess(respSelect)) return null

        var cardNumber: String? = null
        var balance = 0L

        val apduInfo = hexToBytes("90BD0000070000000017000000")
        try {
            val respInfo = isoDep.transceive(apduInfo)
            if (isSuccess(respInfo) && respInfo.size >= 30) {
                val info = reverseHex(responseData(respInfo))
                if (info.length >= 22) cardNumber = info.substring(6, 22)

                val balanceResponse = isoDep.transceive(hexToBytes("906C0000010000"))
                if (isSuccess(balanceResponse)) {
                    val balanceHex = reverseHex(responseData(balanceResponse)).take(8)
                    balance = balanceHex.toLongOrNull(16) ?: 0L
                }
            }
        } catch (ignored: Exception) {}

        val history = mutableListOf<CardTransactionItem>()
        for (rec in 1..10) {
            val apduRec = hexToBytes(String.format("90BD000007%02X00000020000000", rec))
            try {
                val resp = isoDep.transceive(apduRec)
                if (resp == null || resp.size < 2) break
                val sw1 = resp[resp.size - 2].toInt() and 0xFF
                if (sw1 != 0x90 && sw1 != 0x91) break

                if (resp.size >= 32) {
                    val hex = bytesToHex(resp)
                    val dateHex = hex.substring(32, 42) // BCD yyyyMMddHHmmss
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
            } catch (ignored: Exception) {
                break
            }
        }

        return CardBalanceResult(
            cardType = "BRI Brizzi",
            issuer = CardIssuer.BRI,
            cardNumber = cardNumber ?: return null,
            cardId = uidHex,
            balance = balance,
            history = history
        )
    }

    // --- BNI TAPCASH ---
    private fun probeTapCash(isoDep: IsoDep, uidHex: String): CardBalanceResult? {
        val select = isoDep.transceive(hexToBytes("00A4040008A000424E49100001"))
        if (!isSuccess(select)) return null

        val read = isoDep.transceive(hexToBytes("90320300"))
        if (!isSuccess(read)) return null
        val data = responseData(read)
        if (data.size < 16) return null
        val cardNumber = bytesToHex(data.copyOfRange(8, 16))
        if (cardNumber.all { it == '0' || it == 'F' }) return null
        val balance = ((data[2].toLong() and 0xFF) shl 16) or
                ((data[3].toLong() and 0xFF) shl 8) or
                (data[4].toLong() and 0xFF)

        // BNI TapCash Transaction History Parsing (Replicated from eSaldo Info decompiled getHistory/tapcashTransaksi)
        val history = mutableListOf<CardTransactionItem>()
        val baseDate1995 = 788893200L // 1995-01-01 UTC in seconds
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Jakarta") }
        val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Jakarta") }

        try {
            // Read first block up to 15 records (15 * 16 = 240 bytes)
            // Command format: 90 32 03 00 02 00 F0
            val histCmd1 = byteArrayOf(0x90.toByte(), 0x32, 0x03, 0x00, 0x02, 0x00, 0xF0.toByte())
            val histResp1 = isoDep.transceive(histCmd1)
            if (isSuccess(histResp1)) {
                val histData = responseData(histResp1)
                for (offset in 0 until histData.size step 16) {
                    if (offset + 16 > histData.size) break
                    val record = histData.sliceArray(offset until offset + 16)
                    val typeByte = record[0].toInt() and 0xFF
                    val isPayment = typeByte == 1
                    val isTopUp = typeByte == 3 || typeByte == 4 || typeByte == 6
                    if (!isPayment && !isTopUp) continue

                    val rawAmt = ((record[1].toInt() and 0xFF) shl 16) or
                            ((record[2].toInt() and 0xFF) shl 8) or
                            (record[3].toInt() and 0xFF)
                    val signedAmt = if ((record[1].toInt() and 0x80) != 0) rawAmt or (-0x1000000) else rawAmt
                    val amount = Math.abs(signedAmt.toLong())

                    val sec = ((record[4].toLong() and 0xFF) shl 24) or
                            ((record[5].toLong() and 0xFF) shl 16) or
                            ((record[6].toLong() and 0xFF) shl 8) or
                            (record[7].toLong() and 0xFF)
                    val txDateMs = (sec + baseDate1995) * 1000L

                    if (amount > 0 && txDateMs > 0) {
                        history.add(
                            CardTransactionItem(
                                type = if (isPayment) "Payment" else "Top up",
                                amount = amount,
                                balance = null,
                                date = sdfDate.format(Date(txDateMs)),
                                time = sdfTime.format(Date(txDateMs)),
                                terminalId = null
                            )
                        )
                    }
                }
            }
        } catch (ignored: Exception) {}

        return CardBalanceResult(
            cardType = "BNI TapCash",
            issuer = CardIssuer.BNI,
            cardNumber = cardNumber,
            cardId = uidHex,
            balance = balance,
            history = history
        )
    }

    // --- BANK DKI JAKCARD ---
    private fun probeJakCard(isoDep: IsoDep, uidHex: String): CardBalanceResult? {
        // eSaldo Info Jcard implementation: command 1 (select/read pan), command 2 (balance)
        // Command 1 selects AID and reads Track/PAN ending in 9000
        val apdu1 = hexToBytes("00A4040007A0000000030002")
        val resp1 = try { isoDep.transceive(apdu1) } catch (_: Exception) { return null }
        if (!isSuccess(resp1)) return null

        val apduPan = try { isoDep.transceive(hexToBytes("00B2010C00")) } catch (_: Exception) { return null }
        if (!isSuccess(apduPan)) return null
        val panHex = bytesToHex(responseData(apduPan))
        val cardNumber = if (panHex.length >= 32) panHex.substring(16, 32) else null
        if (cardNumber == null || !cardNumber.all { it.isDigit() }) return null

        val apduBalance = try { isoDep.transceive(hexToBytes("805C000004")) } catch (_: Exception) { return null }
        if (!isSuccess(apduBalance)) return null
        val balHex = bytesToHex(responseData(apduBalance))
        val balance = balHex.take(8).toLongOrNull(16) ?: 0L

        return CardBalanceResult(
            cardType = "Bank DKI JakCard",
            issuer = CardIssuer.DKI,
            cardNumber = cardNumber,
            cardId = uidHex,
            balance = balance
        )
    }

    // --- BANK MEGA MEGACASH ---
    private fun probeMegaCash(isoDep: IsoDep, uidHex: String): CardBalanceResult? {
        val select = isoDep.transceive(hexToBytes("00A4040007A0000000030002"))
        if (!isSuccess(select)) return null
        val balanceResponse = isoDep.transceive(hexToBytes("805C000004"))
        if (!isSuccess(balanceResponse)) return null
        val balanceData = responseData(balanceResponse)
        if (balanceData.size < 4) return null
        val numberResponse = try { isoDep.transceive(hexToBytes("00B2010C00")) } catch (_: Exception) { return null }
        if (!isSuccess(numberResponse)) return null
        val cardNumber = extractDigitsFromBcd(responseData(numberResponse), setOf("6019")) ?: return null
        val balance = ((balanceData[0].toLong() and 0xFF) shl 24) or
                ((balanceData[1].toLong() and 0xFF) shl 16) or
                ((balanceData[2].toLong() and 0xFF) shl 8) or
                (balanceData[3].toLong() and 0xFF)
        return CardBalanceResult("Bank Mega MegaCash", CardIssuer.MEGA, cardNumber, uidHex, balance)
    }

    // --- KMT COMMUTER LINE (NfcF) ---
    private fun probeKmt(nfcF: NfcF, uidHex: String): CardBalanceResult? {
        return try {
            val req = byteArrayOf(
                0x10, 0x06,
                0, 0, 0, 0, 0, 0, 0, 0,
                0x01, 0x0F, 0x22, 0x01, 0x80.toByte(), 0x00
            )
            val idm = nfcF.tag.id
            if (idm.size < 8) return null
            System.arraycopy(idm, 0, req, 2, 8)
            val resp = nfcF.transceive(req)
            if (resp.size < 16 || resp[10] != 0.toByte() || resp[11] != 0.toByte()) return null
            val blockOffset = 13
            val balance = (resp[blockOffset].toLong() and 0xFF) or
                    ((resp[blockOffset + 1].toLong() and 0xFF) shl 8) or
                    ((resp[blockOffset + 2].toLong() and 0xFF) shl 16)
            CardBalanceResult("KMT Commuter Line", CardIssuer.KCI, bytesToHex(idm), uidHex, balance)
        } catch (_: Exception) {
            null
        }
    }

    fun isSuccess(resp: ByteArray?): Boolean {
        if (resp == null || resp.size < 2) return false
        val sw1 = resp[resp.size - 2].toInt() and 0xFF
        val sw2 = resp[resp.size - 1].toInt() and 0xFF
        return (sw1 == 0x90 || sw1 == 0x91) && sw2 == 0x00
    }

    private fun responseData(resp: ByteArray): ByteArray = resp.copyOf(resp.size - 2)

    private fun extractDigitsFromBcd(data: ByteArray, prefixes: Set<String> = emptySet()): String? {
        for (offset in data.indices) {
            val end = minOf(data.size, offset + 10)
            val digits = buildString {
                for (index in offset until end) {
                    val value = data[index].toInt() and 0xFF
                    val hi = value ushr 4
                    val lo = value and 0x0F
                    if (hi > 9 || lo > 9) break
                    append(hi).append(lo)
                }
            }
            if (digits.length >= 16) {
                val candidate = digits.take(16)
                if (prefixes.isEmpty() || prefixes.any(candidate::startsWith)) return candidate
            }
        }
        return null
    }

    private fun reverseHex(bytes: ByteArray): String = bytesToHex(bytes.reversedArray())

    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789ABCDEF"
        val result = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val i = b.toInt() and 0xFF
            result.append(hexChars[i ushr 4])
            result.append(hexChars[i and 0x0F])
        }
        return result.toString()
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace("[^0-9A-Fa-f]".toRegex(), "")
        require(clean.length % 2 == 0) { "Hex APDU must contain complete bytes" }
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            result[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }
}
