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
    override val parserName: String = "FeliCa / KMT Commuter Line"

    override fun canHandle(tag: Tag): Boolean = tag.techList.contains(NfcF::class.java.name)

    override fun parse(tag: Tag, channel: ApduChannel?, log: StringBuilder): CardBalanceResult? {
        val nfcF = NfcF.get(tag) ?: return null
        return try {
            nfcF.timeout = 3000
            nfcF.connect()
            log.append("Connected to NfcF / FeliCa\n")

            val idm = tag.id
            val idmHex = idm.toHexString()

            // 1. Probe KMT Commuter Line specific command
            val kmtResult = probeKmt(nfcF, idm, idmHex, log)
            if (kmtResult != null) return kmtResult

            // 2. Probe FeliCa Service 0x090F (Transit / Suica / Pasmo)
            val transitResult = probeFeliCaTransit(nfcF, idm, idmHex, log)
            if (transitResult != null) return transitResult

            CardBalanceResult(
                cardType = "FeliCa Card",
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

    private fun probeKmt(nfcF: NfcF, idm: ByteArray, idmHex: String, log: StringBuilder): CardBalanceResult? {
        return try {
            val req = byteArrayOf(
                0x10, 0x06,
                0, 0, 0, 0, 0, 0, 0, 0,
                0x01, 0x0F, 0x22, 0x01, 0x80.toByte(), 0x00
            )
            if (idm.size < 8) return null
            System.arraycopy(idm, 0, req, 2, 8)
            val resp = nfcF.transceive(req)
            if (resp.size < 16 || resp[10] != 0.toByte() || resp[11] != 0.toByte()) return null

            val blockOffset = 13
            val balance = (resp[blockOffset].toLong() and 0xFF) or
                    ((resp[blockOffset + 1].toLong() and 0xFF) shl 8) or
                    ((resp[blockOffset + 2].toLong() and 0xFF) shl 16)
            log.append("KMT balance: $balance\n")

            CardBalanceResult(
                cardType = "KMT Commuter Line",
                issuer = CardIssuer.KCI,
                cardNumber = idmHex,
                cardId = idmHex,
                balance = balance,
                balanceKnown = true,
                debugLog = log.toString()
            )
        } catch (e: Exception) {
            log.append("KMT probe err: ${e.message}\n")
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
                    cardType = "FeliCa (Transit/KMT)",
                    issuer = CardIssuer.KCI,
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
