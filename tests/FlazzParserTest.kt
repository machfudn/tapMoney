package id.tapmoney.nfc.parser

import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.CardIssuer
import id.tapmoney.model.CardTransactionItem
import id.tapmoney.nfc.ApduChannel
import id.tapmoney.nfc.hexToBytes
import id.tapmoney.nfc.toHexString
import id.tapmoney.util.ReceiptFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
class FlazzParserTest {

    @Test
    fun testFlazzGen2SfiAndDesfireHistoryMergedAndBackpropagated() {
        val parser = FlazzParser()
        val log = StringBuilder()

        val selectGen2Aid = "00A404000BA0000000180F000001800100"
        val cmdUuuu = "00A40100020200"
        val cmdBbbb = "00B081008E"
        val cmdDddd = "803200030400000000"

        // Build 60-byte SFI record
        // byte 0: 0x04 (Payment)
        // bytes 6..8: 0x00, 0x27, 0x10 -> amount 10,000
        // bytes 30..37: ASCII "INDOMARE"
        // bytes 38..41: epoch 1980 seconds = 1391961600 (0x52F7A200)
        val sfiRecord = ByteArray(60)
        sfiRecord[0] = 0x04
        ByteBuffer.wrap(sfiRecord, 1, 4).putInt(40_000)
        sfiRecord[6] = 0x00.toByte()
        sfiRecord[7] = 0x27.toByte()
        sfiRecord[8] = 0x10.toByte()
        System.arraycopy("INDOMARE".toByteArray(Charsets.US_ASCII), 0, sfiRecord, 30, 8)
        ByteBuffer.wrap(sfiRecord, 38, 4).putInt(1391961600)

        // Build 32-byte DESFire record
        // byte 0: 0x04 (Payment)
        // bytes 1..3: 16777216 - 5000 = 16772216 = 0xFFEC78
        // bytes 4..7: epoch 1980 seconds = 1391961600 (same second as SFI record, tests dedup non-collision)
        // bytes 14..21: ASCII "ALFAMART"
        // bytes 22..24: raw balance 45,000 = 0x00AFC8 (endBal = |45000 - 5000| = 40000)
        val desfireRecord = ByteArray(32)
        desfireRecord[0] = 0x04
        desfireRecord[1] = 0xFF.toByte()
        desfireRecord[2] = 0xEC.toByte()
        desfireRecord[3] = 0x78.toByte()
        ByteBuffer.wrap(desfireRecord, 4, 4).putInt(1391961600)
        System.arraycopy("ALFAMART".toByteArray(Charsets.US_ASCII), 0, desfireRecord, 14, 8)
        desfireRecord[22] = 0x00
        desfireRecord[23] = 0xAF.toByte()
        desfireRecord[24] = 0xC8.toByte()

        val mockChannel = ApduChannel { cmd ->
            val hex = cmd.toHexString()
            when {
                hex == selectGen2Aid -> "9000".hexToBytes()
                hex == cmdUuuu -> "9000".hexToBytes()
                hex == cmdBbbb -> {
                    // Track 2: ;6019123456789012=2512101000000000?9000
                    val track2 = ";6019123456789012=2512101000000000?".toByteArray(Charsets.US_ASCII)
                    track2 + "9000".hexToBytes()
                }
                hex == cmdDddd -> {
                    // Balance: 50,000 = 0x00C350
                    byteArrayOf(0x00, 0x00, 0xC3.toByte(), 0x50.toByte(), 0x90.toByte(), 0x00)
                }
                hex == "0000400B000004020000" -> "9000".hexToBytes()
                // SFI 4 (0x84) 15-byte chunk reading
                hex.startsWith("00B084") -> {
                    val p2 = cmd[3].toInt() and 0xFF
                    if (p2 < 60) {
                        sfiRecord.sliceArray(p2 until p2 + 15) + "9000".hexToBytes()
                    } else {
                        "6A82".hexToBytes() // EOF
                    }
                }
                // SFI 5 (0x85)
                hex.startsWith("00B085") -> "6A82".hexToBytes()
                // DESFire flh3 init
                hex == "9032030000" -> "9000".hexToBytes()
                // DESFire record 0
                hex == "90320300010020" -> desfireRecord + "9000".hexToBytes()
                // DESFire record 1 (EOF)
                hex == "90320300010120" -> "6A83".hexToBytes()
                else -> "6A82".hexToBytes()
            }
        }

        val result = parser.parseChannel(mockChannel, "04AABBCCDDEEFF", log)
        assertNotNull("Result should not be null", result)
        assertEquals("6019123456789012", result?.cardNumber)
        assertEquals(50_000L, result?.balance)

        val history = result?.history ?: emptyList()
        // Both SFI record and DESFire record should be present despite identical timestamp (composite dedup)
        assertEquals("Expected 2 merged transaction items", 2, history.size)
        assertTrue("Contains Indomaret terminal", history.any { it.terminalId == "INDOMARE" && it.amount == 10_000L })
        assertTrue("Contains Alfamart terminal", history.any { it.terminalId == "ALFAMART" && it.amount == 5_000L })
    }

    @Test
    fun testFlazzLedgerBackpropagationContinuity() {
        val parser = FlazzParser()
        val log = StringBuilder()

        val selectGen2Aid = "00A404000BA0000000180F000001800100"
        val cmdUuuu = "00A40100020200"
        val cmdBbbb = "00B081008E"
        val cmdDddd = "803200030400000000"

        // Build SFI record with unknown balance (record[1..4] = 0xFFFFFFFF)
        val sfiRecord = ByteArray(60)
        sfiRecord[0] = 0x04 // Payment
        ByteBuffer.wrap(sfiRecord, 1, 4).putInt(-1) // Invalid raw balance -> null
        sfiRecord[6] = 0x13.toByte()
        sfiRecord[7] = 0x88.toByte() // 5,000 amount
        System.arraycopy("TOLLGATE".toByteArray(Charsets.US_ASCII), 0, sfiRecord, 30, 8)
        ByteBuffer.wrap(sfiRecord, 38, 4).putInt(1391961600)

        val mockChannel = ApduChannel { cmd ->
            val hex = cmd.toHexString()
            when {
                hex == selectGen2Aid -> "9000".hexToBytes()
                hex == cmdUuuu -> "9000".hexToBytes()
                hex == cmdBbbb -> ";6019999988887777=2512101000000000?".toByteArray(Charsets.US_ASCII) + "9000".hexToBytes()
                hex == cmdDddd -> byteArrayOf(0x00, 0x00, 0x75.toByte(), 0x30.toByte(), 0x90.toByte(), 0x00) // current balance = 30,000
                hex == "0000400B000004020000" -> "9000".hexToBytes()
                hex.startsWith("00B084") -> {
                    val p2 = cmd[3].toInt() and 0xFF
                    if (p2 < 60) sfiRecord.sliceArray(p2 until p2 + 15) + "9000".hexToBytes() else "6A82".hexToBytes()
                }
                else -> "6A82".hexToBytes()
            }
        }

        val result = parser.parseChannel(mockChannel, "04112233445566", log)
        assertNotNull(result)
        assertEquals(30_000L, result?.balance)
        val history = result?.history ?: emptyList()
        assertEquals(1, history.size)
        // With ledger backpropagation, sorted[0].balance is anchored from currentBalance (30,000)
        assertEquals(30_000L, history[0].balance)
    }

    @Test
    fun testFlazzGen1FallbackCyclicRecord() {
        val parser = FlazzParser()
        val log = StringBuilder()

        val selectGen2Aid = "00A404000BA0000000180F000001800100"
        val cmdUuuu = "00A40100020200"
        val cmdBbbb = "00B081008E"
        val cmdDddd = "803200030400000000"

        // Gen 1 cyclic record hex (34 bytes: 32 bytes data + 2 bytes SW):
        // 0..6: Date BCD "240215" (24 Feb 2015)
        // 6..12: Time BCD "123000" (12:30:00)
        // 12..18: Amount hex "002710" (10,000)
        // Remaining bytes padding (23 bytes) + 9000 (2 bytes) = 34 bytes
        val cyclicRecordHex = "24021512300000271000000000000000000000000000000000000000000000009000"
        val mockChannel = ApduChannel { cmd ->
            val hex = cmd.toHexString()
            when {
                hex == selectGen2Aid -> "9000".hexToBytes()
                hex == cmdUuuu -> "9000".hexToBytes()
                hex == cmdBbbb -> ";6019555566667777=2512101000000000?".toByteArray(Charsets.US_ASCII) + "9000".hexToBytes()
                hex == cmdDddd -> byteArrayOf(0x00, 0x00, 0x27.toByte(), 0x10.toByte(), 0x90.toByte(), 0x00)
                // SFI and DESFire fail
                hex.startsWith("00B084") || hex.startsWith("00B085") || hex.startsWith("9032") -> "6A82".hexToBytes()
                // Gen 1 fallback: 00 B2 01 0C 00
                hex == "00B2010C00" -> cyclicRecordHex.hexToBytes()
                hex == "00B2020C00" -> "6A83".hexToBytes()
                else -> "6A82".hexToBytes()
            }
        }

        val result = parser.parseChannel(mockChannel, "04998877665544", log)
        assertNotNull(result)
        val history = result?.history ?: emptyList()
        assertEquals(1, history.size)
        assertEquals("Payment", history[0].type)
        assertEquals(10_000L, history[0].amount)
    }

    @Test
    fun testFlazzScenario38kPayment30kBalancesReconciled() {
        val parser = FlazzParser()
        val log = StringBuilder()

        // SFI 4 record 0 (newer): Payment Rp 30,000 at time T2 (seconds = 1391962000)
        val sfiRec0 = ByteArray(60)
        sfiRec0[0] = 0x04 // Payment
        sfiRec0[6] = 0x00
        sfiRec0[7] = 0x75.toByte()
        sfiRec0[8] = 0x30.toByte() // 30,000
        System.arraycopy("INDOMARE".toByteArray(Charsets.US_ASCII), 0, sfiRec0, 30, 8)
        ByteBuffer.wrap(sfiRec0, 38, 4).putInt(1391962000)

        // SFI 4 record 1 (older): Payment Rp 5,000 at time T1 (seconds = 1391961000)
        val sfiRec1 = ByteArray(60)
        sfiRec1[0] = 0x04 // Payment
        sfiRec1[6] = 0x00
        sfiRec1[7] = 0x13.toByte()
        sfiRec1[8] = 0x88.toByte() // 5,000
        System.arraycopy("ALFAMART".toByteArray(Charsets.US_ASCII), 0, sfiRec1, 30, 8)
        ByteBuffer.wrap(sfiRec1, 38, 4).putInt(1391961000)

        val mockChannel = ApduChannel { cmd ->
            val hex = cmd.toHexString()
            when {
                hex == "00A404000BA0000000180F000001800100" -> "9000".hexToBytes()
                hex == "00A40100020200" -> "9000".hexToBytes()
                hex == "00B081008E" -> ";6019111122223333=2512101000000000?".toByteArray(Charsets.US_ASCII) + "9000".hexToBytes()
                hex == "803200030400000000" -> {
                    // Card current balance = Rp 8,000 (0x00001F40)
                    byteArrayOf(0x00, 0x00, 0x1F.toByte(), 0x40.toByte(), 0x90.toByte(), 0x00)
                }
                hex == "0000400B000004020000" -> "9000".hexToBytes()
                hex.startsWith("00B084") -> {
                    val p2 = cmd[3].toInt() and 0xFF
                    val sfiBuffer = sfiRec0 + sfiRec1
                    if (p2 + 15 <= sfiBuffer.size) {
                        sfiBuffer.sliceArray(p2 until p2 + 15) + "9000".hexToBytes()
                    } else {
                        "6A82".hexToBytes()
                    }
                }
                else -> "6A82".hexToBytes()
            }
        }

        val result = parser.parseChannel(mockChannel, "04AABBCCDDEEFF", log)
        assertNotNull(result)
        assertEquals(8_000L, result?.balance)

        val history = result?.history ?: emptyList()
        assertEquals(2, history.size)
        // Latest transaction (history[0]): Payment 30,000 with ending balance = 8,000 (card current balance)
        assertEquals(30_000L, history[0].amount)
        assertEquals(8_000L, history[0].balance)

        // Previous transaction (history[1]): Ending balance backpropagated to 8,000 + 30,000 = 38,000
        assertEquals(5_000L, history[1].amount)
        assertEquals(38_000L, history[1].balance)

        // Verify ReceiptFormatter parity
        val resolved = id.tapmoney.util.ReceiptFormatter.resolveEndingBalances(result!!)
        assertEquals(8_000L, resolved[0].balance)
        assertEquals(38_000L, resolved[1].balance)
    }

    @Test
    fun testFlazzSameTimestampSameAmountDifferentTerminalNotDeduplicated() {
        val parser = FlazzParser()
        val log = StringBuilder()

        // SFI 4 record 0: Payment Rp 15,000 at time T (seconds = 1391961600) on TERMINAL_A
        val sfiRec0 = ByteArray(60)
        sfiRec0[0] = 0x04
        sfiRec0[6] = 0x00
        sfiRec0[7] = 0x3A.toByte()
        sfiRec0[8] = 0x98.toByte() // 15,000
        System.arraycopy("TERMAAAA".toByteArray(Charsets.US_ASCII), 0, sfiRec0, 30, 8)
        ByteBuffer.wrap(sfiRec0, 38, 4).putInt(1391961600)

        // SFI 4 record 1: Payment Rp 15,000 at same time T on TERMINAL_B
        val sfiRec1 = ByteArray(60)
        sfiRec1[0] = 0x04
        sfiRec1[6] = 0x00
        sfiRec1[7] = 0x3A.toByte()
        sfiRec1[8] = 0x98.toByte() // 15,000
        System.arraycopy("TERMBBBB".toByteArray(Charsets.US_ASCII), 0, sfiRec1, 30, 8)
        ByteBuffer.wrap(sfiRec1, 38, 4).putInt(1391961600)

        val mockChannel = ApduChannel { cmd ->
            val hex = cmd.toHexString()
            when {
                hex == "00A404000BA0000000180F000001800100" -> "9000".hexToBytes()
                hex == "00A40100020200" -> "9000".hexToBytes()
                hex == "00B081008E" -> ";6019111122223333=2512101000000000?".toByteArray(Charsets.US_ASCII) + "9000".hexToBytes()
                hex == "803200030400000000" -> byteArrayOf(0x00, 0x00, 0x50.toByte(), 0x00.toByte(), 0x90.toByte(), 0x00)
                hex == "0000400B000004020000" -> "9000".hexToBytes()
                hex.startsWith("00B084") -> {
                    val p2 = cmd[3].toInt() and 0xFF
                    val sfiBuffer = sfiRec0 + sfiRec1
                    if (p2 + 15 <= sfiBuffer.size) {
                        sfiBuffer.sliceArray(p2 until p2 + 15) + "9000".hexToBytes()
                    } else {
                        "6A82".hexToBytes()
                    }
                }
                else -> "6A82".hexToBytes()
            }
        }

        val result = parser.parseChannel(mockChannel, "04AABBCCDDEEFF", log)
        assertNotNull(result)
        val history = result?.history ?: emptyList()
        // Both records must be preserved despite identical date, time, and amount
        assertEquals(2, history.size)
        assertTrue(history.any { it.terminalId == "TERMAAAA" && it.amount == 15_000L })
        assertTrue(history.any { it.terminalId == "TERMBBBB" && it.amount == 15_000L })
    }

    @Test
    fun testFlazzMultiSourceMergedSfiDesfireGen1AllPresent() {
        val parser = FlazzParser()
        val log = StringBuilder()

        // 1. SFI record: Payment Rp 10,000 at epoch 1391963000
        val sfiRecord = ByteArray(60)
        sfiRecord[0] = 0x04
        sfiRecord[6] = 0x00
        sfiRecord[7] = 0x27.toByte()
        sfiRecord[8] = 0x10.toByte() // 10,000
        System.arraycopy("SFITERM1".toByteArray(Charsets.US_ASCII), 0, sfiRecord, 30, 8)
        ByteBuffer.wrap(sfiRecord, 38, 4).putInt(1391963000)

        // 2. DESFire record: 32 bytes split in 2 frames (tests 91 AF continuation)
        // Frame 1: 16 bytes + 91 AF
        // Frame 2: 16 bytes + 91 00
        val desfirePayload = ByteArray(32)
        desfirePayload[0] = 0x04 // Payment
        // Amount: 20,000 -> amtRaw = 16777216 - 20000 = 16757216 = 0xFFB1E0
        desfirePayload[1] = 0xFF.toByte()
        desfirePayload[2] = 0xB1.toByte()
        desfirePayload[3] = 0xE0.toByte()
        ByteBuffer.wrap(desfirePayload, 4, 4).putInt(1391962000)
        System.arraycopy("DESFTERM".toByteArray(Charsets.US_ASCII), 0, desfirePayload, 14, 8)
        val frame1 = desfirePayload.sliceArray(0 until 16) + byteArrayOf(0x91.toByte(), 0xAF.toByte())
        val frame2 = desfirePayload.sliceArray(16 until 32) + byteArrayOf(0x91.toByte(), 0x00.toByte())

        // 3. Gen 1 cyclic record: Payment Rp 5,000 (hex 00 13 88)
        val cyclicRecordHex = "24021510000000138800000000000000000000000000000000000000000000009000"

        val mockChannel = ApduChannel { cmd ->
            val hex = cmd.toHexString()
            when {
                hex == "00A404000BA0000000180F000001800100" -> "9000".hexToBytes()
                hex == "00A40100020200" -> "9000".hexToBytes()
                hex == "00B081008E" -> ";6019123456789012=2512101000000000?".toByteArray(Charsets.US_ASCII) + "9000".hexToBytes()
                hex == "803200030400000000" -> byteArrayOf(0x00, 0x00, 0x60.toByte(), 0x00.toByte(), 0x90.toByte(), 0x00)
                hex == "0000400B000004020000" -> "9000".hexToBytes()
                // SFI
                hex.startsWith("00B084") -> {
                    val p2 = cmd[3].toInt() and 0xFF
                    if (p2 < 60) sfiRecord.sliceArray(p2 until p2 + 15) + "9000".hexToBytes() else "6A82".hexToBytes()
                }
                hex.startsWith("00B085") -> "6A82".hexToBytes()
                // DESFire
                hex == "9032030000" -> "9000".hexToBytes()
                hex == "90320300010020" -> frame1
                hex == "90AF000000" -> frame2
                // Next DESFire records fail (consecutive failure tolerance triggers loop break)
                hex.startsWith("9032") || hex.startsWith("90BB") -> "6A82".hexToBytes()
                // Gen 1 cyclic
                hex == "00B2010C00" -> cyclicRecordHex.hexToBytes()
                hex == "00B2020C00" -> "6A83".hexToBytes()
                else -> "6A82".hexToBytes()
            }
        }

        val result = parser.parseChannel(mockChannel, "04AABBCCDDEEFF", log)
        assertNotNull(result)
        val history = result?.history ?: emptyList()
        // All 3 sources (SFI, DESFire, Gen 1) must be present in merged history!
        assertEquals(3, history.size)
        assertTrue("SFI item present", history.any { it.terminalId == "SFITERM1" && it.amount == 10_000L })
        assertTrue("DESFire item present", history.any { it.terminalId == "DESFTERM" && it.amount == 20_000L })
        assertTrue("Gen 1 cyclic item present", history.any { it.terminalId == "REC-1" && it.amount == 5_000L })
    }

    @Test
    fun testReceiptFormatterUnknownBalancePreserved() {
        val card = CardBalanceResult(
            cardType = "BCA Flazz (Terenkripsi)",
            issuer = CardIssuer.BCA,
            cardNumber = "04AABBCCDD",
            balance = 0L,
            balanceKnown = false,
            history = listOf(
                CardTransactionItem(
                    type = "Payment",
                    amount = 5_000L,
                    balance = null,
                    date = "15/02/2024",
                    time = "12:00:00"
                )
            )
        )
        val resolved = ReceiptFormatter.resolveEndingBalances(card)
        assertEquals(1, resolved.size)
        assertNull(resolved[0].balance)
    }
    @Test
    fun testFlazzTopUpInSfi5EndingBalancePreserved() {
        val parser = FlazzParser()
        val log = StringBuilder()

        // SFI 4: Payment Rp 20,000 at T2 (seconds = 1391962000)
        val sfiPayment = ByteArray(60)
        sfiPayment[0] = 0x04 // Payment
        ByteBuffer.wrap(sfiPayment, 1, 4).putInt(100_000) // Balance after payment = 100,000
        sfiPayment[6] = 0x00
        sfiPayment[7] = 0x4E.toByte()
        sfiPayment[8] = 0x20.toByte() // 20,000
        System.arraycopy("PAYTERM1".toByteArray(Charsets.US_ASCII), 0, sfiPayment, 30, 8)
        ByteBuffer.wrap(sfiPayment, 38, 4).putInt(1391962000)

        // SFI 5: Top Up Rp 100,000 at T1 (seconds = 1391961000) with raw chip balance = 120,000
        val sfiTopUp = ByteArray(60)
        sfiTopUp[0] = 0x01 // Top up (non-0x04)
        ByteBuffer.wrap(sfiTopUp, 1, 4).putInt(120_000) // Raw balance = 120,000
        sfiTopUp[6] = 0x01
        sfiTopUp[7] = 0x86.toByte()
        sfiTopUp[8] = 0xA0.toByte() // 100,000
        System.arraycopy("ATM BCA ".toByteArray(Charsets.US_ASCII), 0, sfiTopUp, 30, 8)
        ByteBuffer.wrap(sfiTopUp, 38, 4).putInt(1391961000)

        val mockChannel = ApduChannel { cmd ->
            val hex = cmd.toHexString()
            when {
                hex == "00A404000BA0000000180F000001800100" -> "9000".hexToBytes()
                hex == "00A40100020200" -> "9000".hexToBytes()
                hex == "00B081008E" -> ";6019333344445555=2512101000000000?".toByteArray(Charsets.US_ASCII) + "9000".hexToBytes()
                hex == "803200030400000000" -> byteArrayOf(0x00, 0x01, 0x86.toByte(), 0xA0.toByte(), 0x90.toByte(), 0x00) // current balance = 100,000
                hex == "0000400B000004020000" -> "9000".hexToBytes()
                hex.startsWith("00B084") -> {
                    val p2 = cmd[3].toInt() and 0xFF
                    if (p2 < 60) sfiPayment.sliceArray(p2 until p2 + 15) + "9000".hexToBytes() else "6A82".hexToBytes()
                }
                hex.startsWith("00B085") -> {
                    val p2 = cmd[3].toInt() and 0xFF
                    if (p2 < 60) sfiTopUp.sliceArray(p2 until p2 + 15) + "9000".hexToBytes() else "6A82".hexToBytes()
                }
                else -> "6A82".hexToBytes()
            }
        }

        val result = parser.parseChannel(mockChannel, "04AABBCCDDEEFF", log)
        assertNotNull(result)
        assertEquals(100_000L, result?.balance)
        val history = result?.history ?: emptyList()
        assertEquals(2, history.size)

        // Index 0: Payment 20,000 at T2 -> ending balance 100,000
        assertEquals("Payment", history[0].type)
        assertEquals(20_000L, history[0].amount)
        assertEquals(100_000L, history[0].balance)

        // Index 1: Top up 100,000 at T1 -> ending balance 120,000 (100,000 + 20,000)
        assertEquals("Top up", history[1].type)
        assertEquals(100_000L, history[1].amount)
        assertEquals(120_000L, history[1].balance)
    }

    @Test
    fun testFlazzDesfireTopUpEndingBalanceDecoded() {
        val parser = FlazzParser()
        val log = StringBuilder()

        // DESFire Top Up record: byte 0 = 0x01 (not 0x04)
        // bytes 1..3: amount 50,000 = 0x00C350
        // bytes 4..7: epoch 1391961600
        // bytes 14..21: "ATMBCA01"
        // bytes 22..24: raw balance 75,000 = 0x0124F8
        val desfireRecord = ByteArray(32)
        desfireRecord[0] = 0x01 // Top up
        desfireRecord[1] = 0x00
        desfireRecord[2] = 0xC3.toByte()
        desfireRecord[3] = 0x50.toByte() // 50,000
        ByteBuffer.wrap(desfireRecord, 4, 4).putInt(1391961600)
        System.arraycopy("ATMBCA01".toByteArray(Charsets.US_ASCII), 0, desfireRecord, 14, 8)
        desfireRecord[22] = 0x01
        desfireRecord[23] = 0x24.toByte()
        desfireRecord[24] = 0xF8.toByte() // 75,000

        val mockChannel = ApduChannel { cmd ->
            val hex = cmd.toHexString()
            when {
                hex == "00A404000BA0000000180F000001800100" -> "9000".hexToBytes()
                hex == "00A40100020200" -> "9000".hexToBytes()
                hex == "00B081008E" -> ";6019444455556666=2512101000000000?".toByteArray(Charsets.US_ASCII) + "9000".hexToBytes()
                hex == "803200030400000000" -> byteArrayOf(0x00, 0x01, 0x24.toByte(), 0xF8.toByte(), 0x90.toByte(), 0x00) // balance 75,000
                hex.startsWith("00B084") || hex.startsWith("00B085") -> "6A82".hexToBytes()
                hex == "9032030000" -> "9000".hexToBytes()
                hex == "90320300010020" -> desfireRecord + "9000".hexToBytes()
                hex == "90320300010120" -> "6A83".hexToBytes()
                else -> "6A82".hexToBytes()
            }
        }

        val result = parser.parseChannel(mockChannel, "04AABBCCDDEEFF", log)
        assertNotNull(result)
        val history = result?.history ?: emptyList()
        assertEquals(1, history.size)
        assertEquals("Top up", history[0].type)
        assertEquals(50_000L, history[0].amount)
        assertEquals(75_000L, history[0].balance)
    }

    @Test
    fun testFlazzDiscontinuousHistoryTopUpPreservesRawChipBalance() {
        val card = CardBalanceResult(
            cardType = "BCA Flazz",
            issuer = CardIssuer.BCA,
            cardNumber = "6019123412341234",
            balance = 8_000L,
            balanceKnown = true,
            history = listOf(
                // Recent payment: 10,000
                CardTransactionItem(
                    type = "Payment",
                    amount = 10_000L,
                    balance = null,
                    date = "2026-11-03",
                    time = "15:00:00",
                    terminalId = "PARK01"
                ),
                // Old Top Up: 100,000 with chip ending balance 150,000 from months ago (intermediate payments purged from card)
                CardTransactionItem(
                    type = "Top up",
                    amount = 100_000L,
                    balance = 150_000L,
                    date = "2026-08-01",
                    time = "10:00:00",
                    terminalId = "ATM BCA"
                ),
                // Older payment: 20,000
                CardTransactionItem(
                    type = "Payment",
                    amount = 20_000L,
                    balance = null,
                    date = "2026-07-20",
                    time = "09:00:00",
                    terminalId = "TOLL01"
                )
            )
        )

        val resolved = ReceiptFormatter.resolveEndingBalances(card)
        assertEquals(3, resolved.size)

        // 1. Recent payment anchored to current balance (8,000)
        assertEquals(8_000L, resolved[0].balance)

        // 2. Top Up raw chip balance (150,000) MUST NOT be wiped or overridden with impossible 18,000 (8k + 10k)
        assertEquals(150_000L, resolved[1].balance)

        // 3. Older payment backpropagated from Top Up anchor: 150,000 - 100,000 = 50,000
        assertEquals(50_000L, resolved[2].balance)
    }

    @Test
    fun testReceiptFormatterTopUpForwardPropagationRecovery() {
        val card = CardBalanceResult(
            cardType = "BCA Flazz",
            issuer = CardIssuer.BCA,
            cardNumber = "6019123412341234",
            balance = 8_000L,
            balanceKnown = true,
            history = listOf(
                // Recent payment with gap
                CardTransactionItem(
                    type = "Payment",
                    amount = 10_000L,
                    balance = null,
                    date = "2026-11-03",
                    time = "15:00:00",
                    terminalId = "PARK01"
                ),
                // Top up with unknown chip balance
                CardTransactionItem(
                    type = "Top up",
                    amount = 100_000L,
                    balance = null,
                    date = "2026-08-01",
                    time = "10:00:00",
                    terminalId = "ATM BCA"
                ),
                // Older payment with known balance 30,000
                CardTransactionItem(
                    type = "Payment",
                    amount = 20_000L,
                    balance = 30_000L,
                    date = "2026-07-20",
                    time = "09:00:00",
                    terminalId = "TOLL01"
                )
            )
        )

        val resolved = ReceiptFormatter.resolveEndingBalances(card)
        assertEquals(3, resolved.size)
        assertEquals(8_000L, resolved[0].balance)
        // Recovered via forward propagation from older payment (30,000 + 100,000 = 130,000)
        assertEquals(130_000L, resolved[1].balance)
        assertEquals(30_000L, resolved[2].balance)
    }

    @Test
    fun testFlazzGen2Exact28ItemsDeduplicationAndAuthenticBalances() {
        val parser = FlazzParser()
        val log = StringBuilder()

        // SFI 4: 4 records (offsets 0, 60, 120, 180)
        val sfiBuffer = ByteArray(240)
        for (i in 0 until 4) {
            val offset = i * 60
            sfiBuffer[offset] = 0x04 // Payment
            ByteBuffer.wrap(sfiBuffer, offset + 1, 4).putInt(50_000 - i * 5_000) // chip balance
            sfiBuffer[offset + 6] = 0x00
            sfiBuffer[offset + 7] = 0x13.toByte()
            sfiBuffer[offset + 8] = 0x88.toByte() // 5,000
            val term = String.format("TERM%04d", i)
            System.arraycopy(term.toByteArray(Charsets.US_ASCII), 0, sfiBuffer, offset + 30, 8)
            ByteBuffer.wrap(sfiBuffer, offset + 38, 4).putInt(1391960000 + (30 - i) * 100)
        }

        // DESFire: 28 records. Records 0..3 match SFI records, records 4..27 are older transactions.
        val desfireRecords = mutableListOf<ByteArray>()
        for (i in 0 until 28) {
            val rec = ByteArray(32)
            rec[0] = 0x04 // Payment
            // Amount: 5,000 -> 16777216 - 5000 = 16772216 = 0xFFEC78
            rec[1] = 0xFF.toByte()
            rec[2] = 0xEC.toByte()
            rec[3] = 0x78.toByte()
            ByteBuffer.wrap(rec, 4, 4).putInt(1391960000 + (30 - i) * 100)
            val term = String.format("TERM%04d", i)
            System.arraycopy(term.toByteArray(Charsets.US_ASCII), 0, rec, 14, 8)
            // Balance before: endBal + 5000
            val endBal = 50_000 - i * 1_000
            val balBefore = endBal + 5_000
            rec[22] = ((balBefore shr 16) and 0xFF).toByte()
            rec[23] = ((balBefore shr 8) and 0xFF).toByte()
            rec[24] = (balBefore and 0xFF).toByte()
            desfireRecords.add(rec)
        }

        val mockChannel = ApduChannel { cmd ->
            val hex = cmd.toHexString()
            when {
                hex == "00A404000BA0000000180F000001800100" -> "9000".hexToBytes()
                hex == "00A40100020200" -> "9000".hexToBytes()
                hex == "00B081008E" -> ";6019888899990000=2512101000000000?".toByteArray(Charsets.US_ASCII) + "9000".hexToBytes()
                hex == "803200030400000000" -> byteArrayOf(0x00, 0x00, 0xC3.toByte(), 0x50.toByte(), 0x90.toByte(), 0x00) // 50,000
                hex == "0000400B000004020000" -> "9000".hexToBytes()
                hex.startsWith("00B084") -> {
                    val p2 = cmd[3].toInt() and 0xFF
                    if (p2 < 240) sfiBuffer.sliceArray(p2 until p2 + 15) + "9000".hexToBytes() else "6A82".hexToBytes()
                }
                hex.startsWith("00B085") -> "6A82".hexToBytes()
                hex == "9032030000" -> "9000".hexToBytes()
                hex.startsWith("9032030001") -> {
                    val recIdx = cmd[5].toInt() and 0xFF
                    if (recIdx < 28) desfireRecords[recIdx] + "9000".hexToBytes() else "6A83".hexToBytes()
                }
                else -> "6A82".hexToBytes()
            }
        }

        val result = parser.parseChannel(mockChannel, "04AABBCCDDEEFF", log)
        assertNotNull(result)
        val history = result?.history ?: emptyList()

        // SFI 4 records and matching DESFire records MUST be deduplicated -> exactly 28 items, NOT 32!
        assertEquals(28, history.size)
        assertTrue("All balances are positive and valid", history.all { it.balance != null && it.balance!! > 0L })
        assertTrue("Latest balance matches chip balance 50,000", history[0].balance == 50_000L)
    }
}
