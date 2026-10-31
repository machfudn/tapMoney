package id.tapmoney.nfc.parser

import android.nfc.Tag
import android.nfc.tech.NfcF
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.CardIssuer
import id.tapmoney.model.CardTransactionItem
import id.tapmoney.nfc.ApduChannel
import id.tapmoney.nfc.TxDates
import id.tapmoney.nfc.toHexString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar

class FelicaParser : EmoneyParser {
    override val parserName: String = "FeliCa (KMT / MRTJ / Suica)"
    override val category: id.tapmoney.model.CardCategory = id.tapmoney.model.CardCategory.DOMESTIC_TRANSIT

    override fun canHandle(tag: Tag): Boolean = tag.techList.contains(NfcF::class.java.name)

    override fun parse(tag: Tag, channel: ApduChannel?, log: StringBuilder): CardBalanceResult? {
        val nfcF = NfcF.get(tag) ?: return null
        return try {
            nfcF.timeout = 3000
            nfcF.connect()
            log.append("Connected to NfcF / FeliCa\n")

            val idm = tag.id
            val idmHex = idm.toHexString()

            // 1. Probe KMT Commuter Line (Service 0x1017 & 0x300B)
            val kmtResult = probeKmtReal(nfcF, idm, idmHex, log)
            if (kmtResult != null) return kmtResult

            // 2. Probe MRT Jakarta (Service 0x10D7 & 0x100B)
            val mrtjResult = probeMrtjReal(nfcF, idm, idmHex, log)
            if (mrtjResult != null) return mrtjResult

            // 3. Probe Suica / PASMO / Japan Transit (Service 0x090F)
            val transitResult = probeFeliCaTransit(nfcF, idm, idmHex, log)
            if (transitResult != null) return transitResult

            // 4. Probe Rakuten Edy (Service 0x1317 & 0x110B)
            val edyResult = probeEdy(nfcF, idm, idmHex, log)
            if (edyResult != null) return edyResult

            CardBalanceResult(
                cardType = "FeliCa Transit",
                issuer = CardIssuer.KCI,
                cardNumber = idmHex,
                cardId = idmHex,
                balance = 0L,
                balanceKnown = false,
                debugLog = log.toString()
            )
        } catch (e: Exception) {
            log.append("FeliCa err: ${e.message}\n")
            null
        } finally {
            try { if (nfcF.isConnected) nfcF.close() } catch (_: Exception) {}
        }
    }

    private fun probeKmtReal(nfcF: NfcF, idm: ByteArray, idmHex: String, log: StringBuilder): CardBalanceResult? {
        return try {
            // Service 0x1017: Balance (little-endian: 0x17, 0x10)
            val balanceCmd = buildReadWithoutEncryptionCmd(idm, byteArrayOf(0x17.toByte(), 0x10.toByte()), 1)
            val balanceResp = nfcF.transceive(balanceCmd)
            if (balanceResp.size < 29 || balanceResp[10] != 0.toByte() || balanceResp[11] != 0.toByte()) {
                return null
            }

            val bData = balanceResp.sliceArray(13 until 29)
            val balance = ((bData[3].toLong() and 0xFF) shl 24) or
                    ((bData[2].toLong() and 0xFF) shl 16) or
                    ((bData[1].toLong() and 0xFF) shl 8) or
                    (bData[0].toLong() and 0xFF)
            val lastAmount = ((bData[7].toLong() and 0xFF) shl 24) or
                    ((bData[6].toLong() and 0xFF) shl 16) or
                    ((bData[5].toLong() and 0xFF) shl 8) or
                    (bData[4].toLong() and 0xFF)

            // Service 0x300B: Card ID (little-endian: 0x0B, 0x30)
            var cardNumber = idmHex
            try {
                val idCmd = buildReadWithoutEncryptionCmd(idm, byteArrayOf(0x0B.toByte(), 0x30.toByte()), 1)
                val idResp = nfcF.transceive(idCmd)
                if (idResp.size >= 29 && idResp[10] == 0.toByte() && idResp[11] == 0.toByte()) {
                    val rawStr = idResp.sliceArray(13 until 29).decodeToString().trim { it <= ' ' || it == '\u0000' }
                    if (rawStr.isNotBlank()) cardNumber = rawStr
                }
            } catch (_: Exception) {}

            // Service 0x200F: History trips (little-endian: 0x0F, 0x20)
            val history = mutableListOf<CardTransactionItem>()
            try {
                val histCmd = buildReadWithoutEncryptionCmd(idm, byteArrayOf(0x0F.toByte(), 0x20.toByte()), 5)
                val histResp = nfcF.transceive(histCmd)
                if (histResp.size > 12 && histResp[10] == 0.toByte() && histResp[11] == 0.toByte()) {
                    val numBlocks = histResp[12].toInt() and 0xFF
                    var offset = 13
                    for (i in 0 until numBlocks) {
                        if (offset + 16 <= histResp.size) {
                            val block = histResp.sliceArray(offset until offset + 16)
                            if (block[0] != 0.toByte()) {
                                val datePair = id.tapmoney.nfc.parser.transit.KmtDateDecoder.decode(block, 0)
                                val txAmount = ((block[7].toLong() and 0xFF) shl 24) or
                                        ((block[6].toLong() and 0xFF) shl 16) or
                                        ((block[5].toLong() and 0xFF) shl 8) or
                                        (block[4].toLong() and 0xFF)
                                val stnCode = ((block[9].toInt() and 0xFF) shl 8) or (block[8].toInt() and 0xFF)
                                val stnName = id.tapmoney.nfc.parser.transit.KmtStationCatalog.getStationName(stnCode)
                                val processType = block[12].toInt() and 0xFF
                                val typeLabel = when (processType) {
                                    1 -> "Kereta"
                                    2 -> "POS / Toko"
                                    else -> "Top-up / Loket"
                                }

                                if (datePair != null) {
                                    history.add(
                                        CardTransactionItem(
                                            type = typeLabel,
                                            amount = txAmount,
                                            balance = null,
                                            date = datePair.first,
                                            time = datePair.second,
                                            terminalId = stnName
                                        )
                                    )
                                }
                            }
                            offset += 16
                        }
                    }
                }
            } catch (e: Exception) {
                log.append("KMT history read err: ${e.message}\n")
            }

            log.append("KMT balance: $balance, card: $cardNumber\n")

            CardBalanceResult(
                cardType = "KMT Commuter Line",
                issuer = CardIssuer.KCI,
                cardNumber = cardNumber,
                cardId = idmHex,
                balance = balance,
                lastAmount = lastAmount,
                history = history,
                balanceKnown = true,
                debugLog = log.toString()
            )
        } catch (e: Exception) {
            log.append("KMT probe err: ${e.message}\n")
            null
        }
    }

    private fun probeMrtjReal(nfcF: NfcF, idm: ByteArray, idmHex: String, log: StringBuilder): CardBalanceResult? {
        return try {
            // MRTJ Service 0x10D7: Balance (little-endian: 0xD7, 0x10)
            val balanceCmd = buildReadWithoutEncryptionCmd(idm, byteArrayOf(0xD7.toByte(), 0x10.toByte()), 1)
            val resp = nfcF.transceive(balanceCmd)
            if (resp.size < 29 || resp[10] != 0.toByte() || resp[11] != 0.toByte()) {
                return null
            }

            val bData = resp.sliceArray(13 until 29)
            // Balance is 4-byte little-endian at offset 0
            val balance = ((bData[3].toLong() and 0xFF) shl 24) or
                    ((bData[2].toLong() and 0xFF) shl 16) or
                    ((bData[1].toLong() and 0xFF) shl 8) or
                    (bData[0].toLong() and 0xFF)
            // Last transaction is 4-byte little-endian at offset 4
            val lastAmount = ((bData[7].toLong() and 0xFF) shl 24) or
                    ((bData[6].toLong() and 0xFF) shl 16) or
                    ((bData[5].toLong() and 0xFF) shl 8) or
                    (bData[4].toLong() and 0xFF)

            // Service 0x100B: Card ID (little-endian: 0x0B, 0x10)
            var cardNumber = idmHex
            try {
                val idCmd = buildReadWithoutEncryptionCmd(idm, byteArrayOf(0x0B.toByte(), 0x10.toByte()), 1)
                val idResp = nfcF.transceive(idCmd)
                if (idResp.size >= 29 && idResp[10] == 0.toByte() && idResp[11] == 0.toByte()) {
                    val rawStr = idResp.sliceArray(13 until 29).decodeToString().trim { it <= ' ' || it == '\u0000' }
                    if (rawStr.isNotBlank()) cardNumber = rawStr
                }
            } catch (_: Exception) {}

            log.append("MRTJ balance: $balance\n")

            CardBalanceResult(
                cardType = "MRT Jakarta",
                issuer = CardIssuer.MRTJ,
                cardNumber = cardNumber,
                cardId = idmHex,
                balance = balance,
                lastAmount = lastAmount,
                history = emptyList(),
                balanceKnown = true,
                debugLog = log.toString()
            )
        } catch (e: Exception) {
            log.append("MRTJ probe err: ${e.message}\n")
            null
        }
    }

    private fun probeFeliCaTransit(nfcF: NfcF, idm: ByteArray, idmHex: String, log: StringBuilder): CardBalanceResult? {
        return try {
            val serviceCode = byteArrayOf(0x0F.toByte(), 0x09.toByte())
            val cmd = buildReadWithoutEncryptionCmd(idm, serviceCode, 10)
            val resp = nfcF.transceive(cmd)

            if (resp.size > 12 && resp[10] == 0x00.toByte() && resp[11] == 0x00.toByte()) {
                val numBlocks = resp[12].toInt() and 0xFF
                val history = mutableListOf<CardTransactionItem>()
                var currentBalance: Long? = null

                var offset = 13
                for (i in 0 until numBlocks) {
                    if (offset + 16 <= resp.size) {
                        val block = resp.sliceArray(offset until offset + 16)
                        val bal = (ByteBuffer.wrap(block.sliceArray(10..11)).order(ByteOrder.LITTLE_ENDIAN).short.toLong() and 0xFFFFL)
                        if (currentBalance == null) {
                            currentBalance = bal
                        }

                        val dateRaw = ByteBuffer.wrap(block.sliceArray(4..5)).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                        val year = (dateRaw shr 9) + 2000
                        val month = (dateRaw shr 5) and 0x0F
                        val day = dateRaw and 0x1F

                        val cal = Calendar.getInstance(TxDates.JAKARTA)
                        cal.set(year, if (month > 0) month - 1 else 0, if (day > 0) day else 1, 0, 0, 0)
                        cal.set(Calendar.MILLISECOND, 0)

                        history.add(
                            CardTransactionItem(
                                type = "Transit",
                                amount = bal,
                                balance = bal,
                                date = TxDates.formatDate(cal.time),
                                time = "00:00:00",
                                terminalId = null
                            )
                        )
                        offset += 16
                    }
                }

                CardBalanceResult(
                    cardType = "Suica / PASMO / IC",
                    issuer = CardIssuer.SUICA,
                    cardNumber = idmHex,
                    cardId = idmHex,
                    balance = currentBalance ?: 0L,
                    history = history,
                    balanceKnown = currentBalance != null,
                    debugLog = log.toString()
                )
            } else null
        } catch (e: Exception) {
            log.append("FeliCa transit err: ${e.message}\n")
            null
        }
    }

    private fun probeEdy(nfcF: NfcF, idm: ByteArray, idmHex: String, log: StringBuilder): CardBalanceResult? {
        return try {
            // Service 0x1317: Balance (little-endian: 0x17, 0x13)
            val balanceCmd = buildReadWithoutEncryptionCmd(idm, byteArrayOf(0x17.toByte(), 0x13.toByte()), 1)
            val resp = nfcF.transceive(balanceCmd)
            if (resp.size < 29 || resp[10] != 0.toByte() || resp[11] != 0.toByte()) {
                return null
            }

            val bData = resp.sliceArray(13 until 29)
            // Balance is 4-byte little-endian at offset 0
            val balance = ((bData[3].toLong() and 0xFF) shl 24) or
                    ((bData[2].toLong() and 0xFF) shl 16) or
                    ((bData[1].toLong() and 0xFF) shl 8) or
                    (bData[0].toLong() and 0xFF)

            // Service 0x110B: ID (little-endian: 0x0B, 0x11)
            var cardNumber = idmHex
            try {
                val idCmd = buildReadWithoutEncryptionCmd(idm, byteArrayOf(0x0B.toByte(), 0x11.toByte()), 1)
                val idResp = nfcF.transceive(idCmd)
                if (idResp.size >= 29 && idResp[10] == 0.toByte() && idResp[11] == 0.toByte()) {
                    val rawCan = idResp.sliceArray(15 until 23).toHexString()
                    if (rawCan.isNotBlank()) cardNumber = rawCan
                }
            } catch (_: Exception) {}

            log.append("Rakuten Edy balance: $balance, card: $cardNumber\n")

            CardBalanceResult(
                cardType = "Rakuten Edy",
                issuer = CardIssuer.EDY,
                cardNumber = cardNumber,
                cardId = idmHex,
                balance = balance,
                history = emptyList(),
                balanceKnown = true,
                debugLog = log.toString()
            )
        } catch (e: Exception) {
            log.append("Edy probe err: ${e.message}\n")
            null
        }
    }

    private fun buildReadWithoutEncryptionCmd(idm: ByteArray, serviceCode: ByteArray, numBlocks: Int): ByteArray {
        val size = 14 + (numBlocks * 2)
        val buffer = ByteBuffer.allocate(size)
        buffer.put(size.toByte())
        buffer.put(0x06.toByte())
        buffer.put(idm)
        buffer.put(1.toByte())
        buffer.put(serviceCode)
        buffer.put(numBlocks.toByte())
        for (i in 0 until numBlocks) {
            buffer.put(0x80.toByte())
            buffer.put(i.toByte())
        }
        return buffer.array()
    }
}
