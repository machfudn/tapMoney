package id.tapmoney.util

import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.CardIssuer
import id.tapmoney.model.CardTransactionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptFormatterTest {

    private fun sampleCard(
        history: List<CardTransactionItem> = emptyList(),
        balance: Long = 125000L
    ) = CardBalanceResult(
        cardType = "Mandiri e-Money",
        issuer = CardIssuer.MANDIRI,
        cardNumber = "6032980012345678",
        balance = balance,
        history = history,
        timestamp = 1793500000000L // arbitrary fixed epoch
    )

    @Test
    fun testReceiptLineWidthNeverExceeds32() {
        val transactions = listOf(
            CardTransactionItem(
                type = "Payment",
                amount = 15000,
                balance = 110000,
                date = "28/10/2026",
                time = "14:20:00",
                terminalId = "TERMINAL_SUPER_LONG_NAME_TEST"
            ),
            CardTransactionItem(
                type = "Top up",
                amount = 100000,
                balance = 125000,
                date = "29/10/2026",
                time = "09:15:00",
                terminalId = "ATM_MANDIRI_102"
            )
        )
        val card = sampleCard(history = transactions)
        val receiptId = ReceiptFormatter.formatTextReceipt(card, isEnglish = false, lineWidth = 32)

        val lines = receiptId.split("\n")
        lines.forEachIndexed { index, line ->
            assertTrue(
                "Line $index exceeds 32 chars (${line.length}): '$line'",
                line.length <= 32
            )
        }
    }

    @Test
    fun testReceiptIndonesianContent() {
        val card = sampleCard()
        val text = ReceiptFormatter.formatTextReceipt(card, isEnglish = false)

        assertTrue(text.contains("TAPMONEY INFO"))
        assertTrue(text.contains("STRUK INFORMASI SALDO"))
        assertTrue(text.contains("SALDO SAAT INI:"))
        assertTrue(text.contains("Rp 125.000") || text.contains("125.000"))
        assertTrue(text.contains("RIWAYAT TRANSAKSI:"))
        assertTrue(text.contains("TERIMA KASIH"))
    }

    @Test
    fun testReceiptEnglishContent() {
        val card = sampleCard()
        val text = ReceiptFormatter.formatTextReceipt(card, isEnglish = true)

        assertTrue(text.contains("TAPMONEY INFO"))
        assertTrue(text.contains("OFFICIAL BALANCE RECEIPT"))
        assertTrue(text.contains("CURRENT BALANCE:"))
        assertTrue(text.contains("RECENT TRANSACTIONS:"))
        assertTrue(text.contains("THANK YOU"))
    }

    @Test
    fun testReceiptEmptyHistoryHandling() {
        val card = sampleCard(history = emptyList())
        val textId = ReceiptFormatter.formatTextReceipt(card, isEnglish = false)
        val textEn = ReceiptFormatter.formatTextReceipt(card, isEnglish = true)

        assertTrue(textId.contains("(Tidak ada riwayat)"))
        assertTrue(textEn.contains("(No history recorded)"))
    }

    @Test
    fun testReceiptTransactionSigns() {
        val history = listOf(
            CardTransactionItem("Payment", 25000, 100000, "01/11", "10:00"),
            CardTransactionItem("Top up", 50000, 150000, "01/11", "09:00")
        )
        val card = sampleCard(history = history)
        val text = ReceiptFormatter.formatTextReceipt(card, isEnglish = false)

        assertTrue(text.contains("- Rp"))
        assertTrue(text.contains("+ Rp"))
    }

    @Test
    fun testEscPosBinaryCommands() {
        val card = sampleCard()
        val payload = ReceiptFormatter.buildEscPosPayload(card, isEnglish = false)

        assertTrue("Payload should not be empty", payload.isNotEmpty())

        // ESC @ -> 0x1B, 0x40 (Init)
        assertEquals(0x1B.toByte(), payload[0])
        assertEquals(0x40.toByte(), payload[1])

        // Verify string content inside binary stream
        val stringContent = String(payload, Charsets.UTF_8)
        assertTrue(stringContent.contains("TAPMONEY INFO"))
        assertTrue(stringContent.contains("STRUK INFORMASI SALDO"))
        assertTrue(stringContent.contains("TERIMA KASIH"))

        // Verify trailing paper cut: GS V B 0 -> 0x1D, 0x56, 0x42, 0x00
        val len = payload.size
        assertEquals(0x1D.toByte(), payload[len - 4])
        assertEquals(0x56.toByte(), payload[len - 3])
        assertEquals(0x42.toByte(), payload[len - 2])
        assertEquals(0x00.toByte(), payload[len - 1])
    }

    @Test
    fun testTwoColumnAlignment() {
        val line = ReceiptFormatter.twoColumn("Label", "Value", 32)
        assertEquals(32, line.length)
        assertTrue(line.startsWith("Label"))
        assertTrue(line.endsWith("Value"))
    }

    @Test
    fun testCenterAlignment() {
        val centered = ReceiptFormatter.center("TEST", 10)
        assertEquals(10, centered.length)
        assertEquals("   TEST   ", centered)
    }
}
