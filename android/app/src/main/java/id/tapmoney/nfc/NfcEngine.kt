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

                // 2. BCA Flazz
                val flazzResult = probeFlazz(isoDep, uidHex)
                if (flazzResult != null) return flazzResult

                // 3. BRI Brizzi
                val brizziResult = probeBrizzi(isoDep, uidHex)
                if (brizziResult != null) return brizziResult

                // 4. BNI TapCash
                val tapCashResult = probeTapCash(isoDep, uidHex)
                if (tapCashResult != null) return tapCashResult

                // 5. Bank Mega MegaCash
                val megaResult = probeMegaCash(isoDep, uidHex)
                if (megaResult != null) return megaResult

                // Fallback IsoDep
                return CardBalanceResult(
                    cardType = "Kartu Elektronik (ISO-DEP)",
                    issuer = CardIssuer.UNKNOWN,
                    cardNumber = generateCardNumber("8888", uidHex),
                    cardId = uidHex,
                    balance = 0
                )
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
        var cardNumber = generateCardNumber("6032", uidHex)
        if (isSuccess(respPan) && respPan.size >= 8) {
            val panHex = bytesToHex(respPan.sliceArray(0 until 8))
            if (panHex.length == 16 && panHex.all { it.isDigit() }) {
                cardNumber = panHex
            }
        }

        val apduBalance = hexToBytes("00B500000A")
        val respBalance = isoDep.transceive(apduBalance)
        var balance = 0L
        if (respBalance.size >= 4) {
            balance = ((respBalance[0].toLong() and 0xFF)) or
                    ((respBalance[1].toLong() and 0xFF) shl 8) or
                    ((respBalance[2].toLong() and 0xFF) shl 16) or
                    ((respBalance[3].toLong() and 0xFF) shl 24)
        }

        // Mandiri history records (records 1..10)
        val history = mutableListOf<CardTransactionItem>()
        val isNewCard = cardNumber.startsWith("603298") || cardNumber.startsWith("603299")
        val sfi = if (isNewCard) "04" else "08"

        for (rec in 1..10) {
            val apduRec = hexToBytes(String.format("00B2%02X%s00", rec, sfi))
            try {
                val resp = isoDep.transceive(apduRec)
                if (isSuccess(resp) && resp.size >= 24) {
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
            } catch (ignored: Exception) {}
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
        val apduSelect = hexToBytes("00A4040007A0000000041010")
        val respSelect = isoDep.transceive(apduSelect)
        if (!isSuccess(respSelect)) return null

        var cardNumber = generateCardNumber("6013", uidHex)
        val apduTrack2 = hexToBytes("00B2010C00")
        try {
            val respTrack2 = isoDep.transceive(apduTrack2)
            if (isSuccess(respTrack2)) {
                val hexStr = bytesToHex(respTrack2)
                val digitsOnly = hexStr.filter { it.isDigit() }
                if (digitsOnly.length >= 16) {
                    val candidate = digitsOnly.substring(0, 16)
                    if (candidate.startsWith("6013") || candidate.startsWith("6019")) {
                        cardNumber = candidate
                    }
                }
            }
        } catch (ignored: Exception) {}

        var balance = 0L
        val apduLiveBalance = hexToBytes("805C000204")
        try {
            val respBal = isoDep.transceive(apduLiveBalance)
            if (isSuccess(respBal) && respBal.size >= 5) {
                balance = ((respBal[1].toLong() and 0xFF) shl 16) or
                        ((respBal[2].toLong() and 0xFF) shl 8) or
                        ((respBal[3].toLong() and 0xFF))
            }
        } catch (ignored: Exception) {}

        val history = mutableListOf<CardTransactionItem>()
        val baseDate1980 = 315532800000L // 1980-01-01 UTC in ms
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Jakarta") }
        val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Jakarta") }

        for (rec in 1..10) {
            val apduRec = hexToBytes(String.format("00B2%02X1400", rec))
            try {
                var resp = isoDep.transceive(apduRec)
                if (!isSuccess(resp)) {
                    val fallbackRec = hexToBytes(String.format("00B2%02X0C00", rec))
                    resp = isoDep.transceive(fallbackRec)
                }

                if (isSuccess(resp)) {
                    val hex = bytesToHex(resp)
                    if (hex.length >= 64) {
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
                }
            } catch (ignored: Exception) {}
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
        val apduSelect = hexToBytes("00A4040009A00000009600000001")
        val respSelect = isoDep.transceive(apduSelect)
        if (!isSuccess(respSelect)) return null

        var cardNumber = generateCardNumber("5058", uidHex)
        var balance = 0L

        val apduInfo = hexToBytes("00B2010C00")
        try {
            val respInfo = isoDep.transceive(apduInfo)
            if (isSuccess(respInfo) && respInfo.size >= 30) {
                val hexStr = bytesToHex(respInfo)
                val panDigits = hexStr.filter { it.isDigit() }
                if (panDigits.length >= 16) {
                    val candidate = panDigits.substring(0, 16)
                    if (candidate.startsWith("5058") || candidate.startsWith("5059")) {
                        cardNumber = candidate
                    }
                }

                // Balance little endian at offset 26..29
                if (respInfo.size >= 30) {
                    balance = ((respInfo[26].toLong() and 0xFF)) or
                            ((respInfo[27].toLong() and 0xFF) shl 8) or
                            ((respInfo[28].toLong() and 0xFF) shl 16) or
                            ((respInfo[29].toLong() and 0xFF) shl 24)
                }
            }
        } catch (ignored: Exception) {}

        val history = mutableListOf<CardTransactionItem>()
        for (rec in 1..10) {
            val apduRec = hexToBytes(String.format("00B2%02X1400", rec))
            try {
                val resp = isoDep.transceive(apduRec)
                if (isSuccess(resp) && resp.size >= 32) {
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
            } catch (ignored: Exception) {}
        }

        return CardBalanceResult(
            cardType = "BRI Brizzi",
            issuer = CardIssuer.BRI,
            cardNumber = cardNumber,
            cardId = uidHex,
            balance = balance,
            history = history
        )
    }

    // --- BNI TAPCASH ---
    private fun probeTapCash(isoDep: IsoDep, uidHex: String): CardBalanceResult? {
        val apduSelect = hexToBytes("00A4040008A000000018100101")
        val respSelect = isoDep.transceive(apduSelect)
        if (!isSuccess(respSelect)) return null

        var cardNumber = generateCardNumber("7546", uidHex)
        val apduPan = hexToBytes("00B0810020")
        try {
            val respPan = isoDep.transceive(apduPan)
            if (isSuccess(respPan)) {
                val hexStr = bytesToHex(respPan)
                val digits = hexStr.filter { it.isDigit() }
                if (digits.length >= 16) {
                    val candidate = digits.substring(0, 16)
                    if (candidate.startsWith("7546")) {
                        cardNumber = candidate
                    }
                }
            }
        } catch (ignored: Exception) {}

        var balance = 0L
        val apduBal = hexToBytes("805C000004")
        try {
            val respBal = isoDep.transceive(apduBal)
            if (isSuccess(respBal) && respBal.size >= 4) {
                balance = ((respBal[0].toLong() and 0xFF) shl 24) or
                        ((respBal[1].toLong() and 0xFF) shl 16) or
                        ((respBal[2].toLong() and 0xFF) shl 8) or
                        ((respBal[3].toLong() and 0xFF))
            }
        } catch (ignored: Exception) {}

        val history = mutableListOf<CardTransactionItem>()
        val baseTapCashEpoch = 788893200000L // 1995-01-01 UTC
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Jakarta") }
        val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Jakarta") }

        for (rec in 1..10) {
            val apduRec = hexToBytes(String.format("00B2%02XC400", rec))
            try {
                val resp = isoDep.transceive(apduRec)
                if (isSuccess(resp) && resp.size >= 16) {
                    val hex = bytesToHex(resp)
                    val rawSec = hex.substring(0, 8).toLongOrNull(16) ?: 0L
                    if (rawSec > 0) {
                        val txDateMs = (rawSec * 1000) + baseTapCashEpoch
                        val dateStr = sdfDate.format(Date(txDateMs))
                        val timeStr = sdfTime.format(Date(txDateMs))

                        val amt = hex.substring(8, 14).toLongOrNull(16) ?: 0L
                        val typeCode = hex.substring(14, 16)
                        val isTopUp = typeCode == "03" || typeCode == "04" || typeCode == "06"
                        val typeStr = if (isTopUp) "Top up" else "Payment"

                        val tid = hex.substring(16, 24)

                        if (amt > 0) {
                            history.add(
                                CardTransactionItem(
                                    type = typeStr,
                                    amount = amt,
                                    balance = null,
                                    date = dateStr,
                                    time = timeStr,
                                    terminalId = tid
                                )
                            )
                        }
                    }
                }
            } catch (ignored: Exception) {}
        }

        return CardBalanceResult(
            cardType = "BNI TapCash",
            issuer = CardIssuer.BNI,
            cardNumber = cardNumber,
            cardId = uidHex,
            balance = balance,
            history = history
        )
    }

    // --- BANK MEGA MEGACASH ---
    private fun probeMegaCash(isoDep: IsoDep, uidHex: String): CardBalanceResult? {
        val apduSelect = hexToBytes("00A4040007A0000000041010")
        val respSelect = isoDep.transceive(apduSelect)
        if (!isSuccess(respSelect)) return null

        val apduBal = hexToBytes("805C000004")
        val respBal = isoDep.transceive(apduBal)
        if (isSuccess(respBal) && respBal.size >= 4) {
            val bal = ((respBal[0].toLong() and 0xFF) shl 24) or
                    ((respBal[1].toLong() and 0xFF) shl 16) or
                    ((respBal[2].toLong() and 0xFF) shl 8) or
                    ((respBal[3].toLong() and 0xFF))
            return CardBalanceResult(
                cardType = "Bank Mega MegaCash",
                issuer = CardIssuer.MEGA,
                cardNumber = generateCardNumber("6020", uidHex),
                cardId = uidHex,
                balance = bal
            )
        }
        return null
    }

    // --- KMT COMMUTER LINE (NfcF) ---
    private fun probeKmt(nfcF: NfcF, uidHex: String): CardBalanceResult {
        var balance = 0L
        try {
            // Felica Read Without Encryption for KMT Service 0x220F
            val req = byteArrayOf(
                0x10.toByte(), // Length
                0x06.toByte(), // Command: Read Without Encryption
                // IDm (8 bytes from system code)
                0, 0, 0, 0, 0, 0, 0, 0,
                0x01.toByte(), // Number of services
                0x0F.toByte(), 0x22.toByte(), // Service Code 0x220F LE
                0x01.toByte(), // Number of blocks
                0x80.toByte(), 0x00.toByte() // Block 0
            )
            System.arraycopy(nfcF.tag.id, 0, req, 2, 8)
            val resp = nfcF.transceive(req)
            if (resp.size >= 13 && resp[10] == 0x00.toByte() && resp[11] == 0x00.toByte()) {
                val blockOffset = 13
                balance = ((resp[blockOffset].toLong() and 0xFF)) or
                        ((resp[blockOffset + 1].toLong() and 0xFF) shl 8) or
                        ((resp[blockOffset + 2].toLong() and 0xFF) shl 16)
            }
        } catch (ignored: Exception) {}

        return CardBalanceResult(
            cardType = "KMT Commuter Line",
            issuer = CardIssuer.KCI,
            cardNumber = generateCardNumber("1002", uidHex),
            cardId = uidHex,
            balance = balance
        )
    }

    fun generateCardNumber(prefix: String, uidHex: String): String {
        val cleanUid = uidHex.replace(":", "").uppercase()
        val digits = cleanUid.map { it.code.rem(10).toString() }.joinToString("")
        val remaining = (16 - prefix.length).coerceAtLeast(0)
        return prefix + digits.padEnd(remaining, '0').substring(0, remaining)
    }

    fun isSuccess(resp: ByteArray?): Boolean {
        if (resp == null || resp.size < 2) return false
        val sw1 = resp[resp.size - 2].toInt() and 0xFF
        val sw2 = resp[resp.size - 1].toInt() and 0xFF
        return (sw1 == 0x90 && sw2 == 0x00) || sw1 == 0x91 || sw1 == 0x61 || sw1 == 0x62 || sw1 == 0x63
    }

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
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            result[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }
}
