package id.tapmoney.nfc

import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.CardIssuer
import id.tapmoney.nfc.parser.BrizziParser
import id.tapmoney.nfc.parser.FlazzParser
import id.tapmoney.nfc.parser.JakCardParser
import id.tapmoney.nfc.parser.MandiriParser
import id.tapmoney.nfc.parser.MegaCashParser
import id.tapmoney.nfc.parser.TapCashParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun String.hex(): ByteArray =
    replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

class CardParserTest {

    private class FakeCard(private vararg val rules: Pair<String, String>) : ApduChannel {
        override fun transceive(command: ByteArray): ByteArray {
            val hex = command.joinToString("") { "%02X".format(it) }
            val rule = rules.firstOrNull { hex.startsWith(it.first) }
                ?: return byteArrayOf(0x6A, 0x82.toByte())
            return rule.second.hex()
        }
    }

    // --- MANDIRI E-MONEY ---

    private fun mandiriRecord(balance: Int): String =
        "00".repeat(12) + "%08X".format(balance) + "9000"

    @Test
    fun `mandiri reads balance from record 1 SFI 4`() {
        val card = FakeCard(
            "00A40400" to "9000",
            "00B20104" to mandiriRecord(125_000)
        )
        val data = MandiriParser().parseChannel(card, "AABBCCDD", StringBuilder())
        assertNotNull(data)
        assertEquals(125_000L, data!!.balance)
        assertTrue(data.balanceKnown)
        assertEquals("Mandiri e-Money", data.cardType)
        assertEquals(CardIssuer.MANDIRI, data.issuer)
    }

    @Test
    fun `mandiri fallback 00B5 reads little endian`() {
        val card = FakeCard(
            "00A40400" to "9000",
            "00B50000" to "50C300009000" // 50,000 in little-endian uint32
        )
        val data = MandiriParser().parseChannel(card, "AABBCCDD", StringBuilder())
        assertNotNull(data)
        assertEquals(50_000L, data!!.balance)
        assertTrue(data.balanceKnown)
    }

    @Test
    fun `mandiri returns null if applet missing`() {
        val card = FakeCard()
        val data = MandiriParser().parseChannel(card, "AABBCCDD", StringBuilder())
        assertNull(data)
    }

    // --- BCA FLAZZ ---

    @Test
    fun `flazz gen 2 reads balance via 8032`() {
        val card = FakeCard(
            "00A40400" to "9000",
            "00A40100" to "9000",
            "00B08100" to "00000000009000", // isGen2 = true (byte 3 == 0x00)
            "80320003" to "0001E2409000" // 123,456
        )
        val data = FlazzParser().parseChannel(card, "AABBCCDD", StringBuilder())
        assertNotNull(data)
        assertEquals("BCA Flazz Gen 2", data!!.cardType)
        assertEquals(123_456L, data.balance)
        assertTrue(data.balanceKnown)
    }

    @Test
    fun `flazz gen 1 legacy applet detected as encrypted`() {
        val card = FakeCard(
            "00A4040009" to "9000"
        )
        val data = FlazzParser().parseChannel(card, "AABBCCDD", StringBuilder())
        assertNotNull(data)
        assertEquals("BCA Flazz (Terenkripsi)", data!!.cardType)
        assertFalse(data.balanceKnown)
    }

    // --- BRI BRIZZI ---

    @Test
    fun `brizzi accepts DESFire status word 9100 on select`() {
        val card = FakeCard(
            "905A0000" to "9100",
            "906C0000" to "50C300009100" // 50,000 in little-endian
        )
        val data = BrizziParser().parseChannel(card, "AABBCCDD", StringBuilder())
        assertNotNull(data)
        assertEquals("BRI Brizzi", data!!.cardType)
        assertEquals(50_000L, data.balance)
        assertTrue(data.balanceKnown)
    }

    @Test
    fun `brizzi rejects non 9100 balance response`() {
        val card = FakeCard(
            "905A0000" to "9100",
            "906C0000" to "0000000091AE"
        )
        val data = BrizziParser().parseChannel(card, "AABBCCDD", StringBuilder())
        assertNotNull(data)
        assertFalse(data!!.balanceKnown)
    }

    // --- BNI TAPCASH ---

    @Test
    fun `tapcash reads 3 byte balance`() {
        val card = FakeCard(
            "00A40400" to "9000",
            "90320300" to "000001E2409000" // 123,456
        )
        val data = TapCashParser().parseChannel(card, "AABBCCDD", StringBuilder())
        assertNotNull(data)
        assertEquals(123_456L, data!!.balance)
        assertTrue(data.balanceKnown)
    }

    @Test
    fun `tapcash decodes log record relative to 1995 epoch`() {
        val record = ByteArray(16)
        record[0] = 0x01 // Payment
        record[1] = 0x00
        record[2] = 0x30
        record[3] = 0xD4.toByte() // 12,500
        // seconds = 1,000,000,000 (~31 years after 1995)
        val sec = 1_000_000_000L
        record[4] = ((sec shr 24) and 0xFF).toByte()
        record[5] = ((sec shr 16) and 0xFF).toByte()
        record[6] = ((sec shr 8) and 0xFF).toByte()
        record[7] = (sec and 0xFF).toByte()

        val item = TapCashParser.decodeRecord(record, 788893200000L)
        assertNotNull(item)
        assertEquals(12_500L, item!!.amount)
        assertEquals("Payment", item.type)
    }

    // --- BANK DKI JAKCARD ---

    @Test
    fun `jakcard variant 1 reads 904C balance`() {
        val card = FakeCard(
            "00A404000E" to "9000",
            "904C0000" to "0001E2409000" // 123,456
        )
        val data = JakCardParser().parseChannel(card, "AABBCCDD", StringBuilder())
        assertNotNull(data)
        assertEquals("Bank DKI JakCard", data!!.cardType)
        assertEquals(123_456L, data.balance)
        assertTrue(data.balanceKnown)
    }

    @Test
    fun `jakcard variant 2 reads 805C balance`() {
        val card = FakeCard(
            "00A4040007" to "9000",
            "00B2010C" to "000000000000000012345678901234569000",
            "805C0000" to "000027109000" // 10,000
        )
        val data = JakCardParser().parseChannel(card, "AABBCCDD", StringBuilder())
        assertNotNull(data)
        assertEquals(10_000L, data!!.balance)
        assertTrue(data.balanceKnown)
    }

    // --- BANK MEGA MEGACASH ---

    @Test
    fun `megacash reads balance on prefix 6019`() {
        val card = FakeCard(
            "00A40400" to "9000",
            "00B2010C" to "60191234567890129000",
            "805C0000" to "000186A09000" // 100,000
        )
        val data = MegaCashParser().parseChannel(card, "AABBCCDD", StringBuilder())
        assertNotNull(data)
        assertEquals("Bank Mega MegaCash", data!!.cardType)
        assertEquals(100_000L, data.balance)
        assertTrue(data.balanceKnown)
    }

    // --- CEPAS (EZ-LINK / NETS) ---

    @Test
    fun `cepas reads balance and can from purse 3`() {
        // Purse response: 16 bytes. Offset 2..4: balance in cents (e.g. 1500 cents = $15.00 -> 0x0005DC)
        // Offset 8..15: CAN "1111222233334444"
        val purseResp = "01000005DC00000011112222333344449000"
        val card = FakeCard(
            "9032030000" to purseResp
        )
        val data = id.tapmoney.nfc.parser.CepasParser().parseChannel(
            card,
            "AABBCCDD",
            StringBuilder()
        )
        assertNotNull(data)
        assertEquals(1500L, data!!.balance)
        assertEquals(CardIssuer.CEPAS, data.issuer)
        assertEquals("1111222233334444", data.cardNumber)
    }

    // --- T-MONEY (KOREA) ---

    @Test
    fun `tmoney reads balance 50000 krw`() {
        val card = FakeCard(
            "00A4040007D410000003000100" to "9000",
            "904C000004" to "0000C3509000" // 0x0000C350 = 50,000 KRW
        )
        val data = id.tapmoney.nfc.parser.TMoneyParser().parseChannel(
            card,
            "DEADBEEF",
            StringBuilder()
        )
        assertNotNull(data)
        assertEquals(50000L, data!!.balance)
        assertEquals(CardIssuer.TMONEY, data.issuer)
    }

    // --- CHINA PBOC / T-UNION ---

    @Test
    fun `china transit reads balance 50 cny`() {
        val card = FakeCard(
            "00A4040008A00000063201010500" to "9000",
            "805C000204" to "000013889000" // 0x1388 = 5000 cents = 50.00 CNY
        )
        val data = id.tapmoney.nfc.parser.ChinaTransitParser().parseChannel(
            card,
            "DEADBEEF",
            StringBuilder()
        )
        assertNotNull(data)
        assertEquals(5000L, data!!.balance)
        assertEquals(CardIssuer.CHINA_TRANSIT, data.issuer)
    }

    // --- SYDNEY OPAL ---

    @Test
    fun `opal reads application and file 7 data`() {
        // 16 bytes dummy file 7 response with 9100
        val rawData = "000000000000000000000000000000009100"
        val card = FakeCard(
            "905A00000353453100" to "9100",
            "90BD0000070700000010000000" to rawData
        )
        val data = id.tapmoney.nfc.parser.OpalParser().parseChannel(
            card,
            "DEADBEEF",
            StringBuilder()
        )
        assertNotNull(data)
        assertEquals(CardIssuer.OPAL, data!!.issuer)
        assertEquals(0L, data.balance)
    }

    // --- SANITY CHECK ---

    @Test
    fun `balance outside plausible limits marked unknown`() {
        val absurd = CardBalanceResult("BRI Brizzi", CardIssuer.BRI, "1234", "1234", 1_354_956_800L)
        assertFalse(CardParser.sanityCheck(absurd).balanceKnown)

        val negative = CardBalanceResult("Mandiri e-Money", CardIssuer.MANDIRI, "1234", "1234", -5L)
        assertFalse(CardParser.sanityCheck(negative).balanceKnown)

        val valid = CardBalanceResult("Mandiri e-Money", CardIssuer.MANDIRI, "1234", "1234", 125_000L)
        assertTrue(CardParser.sanityCheck(valid).balanceKnown)
    }
}
