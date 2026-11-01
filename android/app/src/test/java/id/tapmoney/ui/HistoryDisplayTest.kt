package id.tapmoney.ui

import id.tapmoney.model.CardTransactionItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryDisplayTest {

    private fun mockTx(date: String, time: String, amount: Long) = CardTransactionItem(
        type = "Payment",
        amount = amount,
        balance = 100_000L,
        date = date,
        time = time,
        terminalId = "TERM01"
    )

    @Test
    fun `initial display slices to first 3 items`() {
        val history = listOf(
            mockTx("2026-05-01", "10:00:00", 15_000),
            mockTx("2026-05-01", "09:00:00", 25_000),
            mockTx("2026-04-30", "14:00:00", 10_000),
            mockTx("2026-04-29", "18:00:00", 50_000),
            mockTx("2026-04-28", "08:30:00", 20_000)
        )

        val initialItems = history.take(3)
        assertEquals(3, initialItems.size)

        val groups = groupHistoryByDate(initialItems)
        val totalGroupedCount = groups.sumOf { it.transactions.size }
        assertEquals(3, totalGroupedCount)
        assertEquals(2, groups.size)
        assertEquals("2026-05-01", groups[0].date)
        assertEquals(2, groups[0].transactions.size)
        assertEquals("2026-04-30", groups[1].date)
        assertEquals(1, groups[1].transactions.size)
    }

    @Test
    fun `view all shows all history items`() {
        val history = listOf(
            mockTx("2026-05-01", "10:00:00", 15_000),
            mockTx("2026-05-01", "09:00:00", 25_000),
            mockTx("2026-04-30", "14:00:00", 10_000),
            mockTx("2026-04-29", "18:00:00", 50_000),
            mockTx("2026-04-28", "08:30:00", 20_000)
        )

        val allGroups = groupHistoryByDate(history)
        val totalGroupedCount = allGroups.sumOf { it.transactions.size }
        assertEquals(5, totalGroupedCount)
        assertEquals(4, allGroups.size)
    }

    @Test
    fun `history with 3 or fewer items does not truncate`() {
        val history = listOf(
            mockTx("2026-05-01", "10:00:00", 15_000),
            mockTx("2026-05-01", "09:00:00", 25_000)
        )

        val initialItems = history.take(3)
        assertEquals(2, initialItems.size)
        val groups = groupHistoryByDate(initialItems)
        assertEquals(2, groups.sumOf { it.transactions.size })
    }

    @Test
    fun `formatCardNumber pure 16 digits outputs all numbers with space chunking`() {
        val result = formatCardNumber("6019001234567890", mask = false)
        assertEquals("6019 0012 3456 7890", result)
    }

    @Test
    fun `formatCardNumber pure 16 digits masked masks middle 8 digits`() {
        val result = formatCardNumber("6019001234567890", mask = true)
        assertEquals("6019 **** **** 7890", result)
    }

    @Test
    fun `formatCardNumber hardware UID with hex letters outputs UID prefix and colon format`() {
        val result = formatCardNumber("04A1B2C3", mask = false)
        assertEquals("UID: 04:A1:B2:C3", result)
    }
}
