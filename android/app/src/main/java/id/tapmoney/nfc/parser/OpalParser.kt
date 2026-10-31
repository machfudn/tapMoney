package id.tapmoney.nfc.parser

import android.nfc.Tag
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.CardCategory
import id.tapmoney.model.CardIssuer
import id.tapmoney.nfc.ApduChannel
import id.tapmoney.nfc.hasStatus
import id.tapmoney.nfc.hexToBytes
import id.tapmoney.nfc.isDesfireSuccess
import id.tapmoney.nfc.isSuccess
import id.tapmoney.nfc.toHexString

class OpalParser : EmoneyParser {
    override val parserName: String = "Sydney Opal (Australia)"
    override val category: CardCategory = CardCategory.REGIONAL_TRANSIT

    override fun canHandle(tag: Tag): Boolean = true

    override fun parse(tag: Tag, channel: ApduChannel?, log: StringBuilder): CardBalanceResult? {
        if (channel == null) return null
        return parseChannel(channel, tag.id.toHexString(), log)
    }

    internal fun parseChannel(channel: ApduChannel, uidHex: String, log: StringBuilder): CardBalanceResult? {
        // DESFire Select Application 0x314553 ("SE1")
        // Try little-endian 53 45 31, then big-endian 31 45 53
        val selectCmds = listOf(
            "905A00000353453100".hexToBytes(),
            "905A00000331455300".hexToBytes()
        )

        var selected = false
        for (cmd in selectCmds) {
            val resp = channel.transceive(cmd)
            if (resp.isDesfireSuccess() || resp.isSuccess() || resp.hasStatus(0x90, 0x00)) {
                selected = true
                break
            }
        }

        if (!selected) return null

        log.append("Selected Opal application (0x314553)\n")

        // Read Standard Data File 0x07 (offset 0, length 16)
        // DESFire ReadData command wrapped in ISO-DEP APDU:
        // CLA 0x90, INS 0xBD, P1 0x00, P2 0x00, Lc 0x07, Data: [File 0x07, Offset 00 00 00, Length 10 00 00], Le 0x00
        val readFileCmd = byteArrayOf(
            0x90.toByte(), 0xBD.toByte(), 0x00, 0x00, 0x07,
            0x07, 0x00, 0x00, 0x00, 0x10, 0x00, 0x00, 0x00
        )
        val resp = channel.transceive(readFileCmd)
        if (resp.size < 16) return null

        // If response has trailing status 0x91 0x00 or 0x90 0x00, strip it if size > 16
        val fileData = if (resp.size >= 18 && (resp.isDesfireSuccess() || resp.isSuccess())) {
            resp.sliceArray(0 until 16)
        } else if (resp.size == 16) {
            resp
        } else {
            resp.sliceArray(0 until minOf(16, resp.size))
        }

        return try {
            val reversed = fileData.copyOf(16)
            reversed.reverse()

            val iRawBalance = getBitsFromBuffer(reversed, 54, 21)
            val signBit = 1 shl 20
            val balanceCents = if ((iRawBalance and signBit) != 0) {
                (iRawBalance - (1 shl 21)).toLong()
            } else {
                iRawBalance.toLong()
            }

            val lastDigit = getBitsFromBuffer(reversed, 92, 4)
            val serialNumber = getBitsFromBuffer(reversed, 96, 32).toLong() and 0xFFFFFFFFL
            val opalNumber = 3085220000000000L + (serialNumber * 10L) + lastDigit

            log.append("Opal balance: $balanceCents cents, card: $opalNumber\n")

            CardBalanceResult(
                cardType = "Sydney Opal",
                issuer = CardIssuer.OPAL,
                cardNumber = opalNumber.toString(),
                cardId = uidHex,
                balance = balanceCents,
                history = emptyList(),
                balanceKnown = true,
                debugLog = log.toString()
            )
        } catch (e: Exception) {
            log.append("Opal parse err: ${e.message}\n")
            null
        }
    }

    private fun getBitsFromBuffer(buffer: ByteArray, iStartBit: Int, iLength: Int): Int {
        val iEndBit = iStartBit + iLength - 1
        val iSByte = iStartBit / 8
        val iSBit = iStartBit % 8
        val iEByte = iEndBit / 8
        val iEBit = iEndBit % 8

        return if (iSByte == iEByte) {
            ((buffer[iEByte].toInt() and 0xFF) shr (7 - iEBit)) and (0xFF shr (8 - iLength))
        } else {
            var uRet = ((buffer[iSByte].toInt() and 0xFF and (0xFF shr iSBit)) shl (((iEByte - iSByte - 1) * 8) + (iEBit + 1)))
            for (i in iSByte + 1 until iEByte) {
                uRet = uRet or ((buffer[i].toInt() and 0xFF) shl (((iEByte - i - 1) * 8) + (iEBit + 1)))
            }
            uRet = uRet or ((buffer[iEByte].toInt() and 0xFF) shr (7 - iEBit))
            uRet
        }
    }
}
