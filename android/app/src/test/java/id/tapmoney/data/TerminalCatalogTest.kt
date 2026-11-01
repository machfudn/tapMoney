package id.tapmoney.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalCatalogTest {

    @Test
    fun testUserAliasTakesPrecedence() {
        val userAliases = mapOf("01A4F29B" to "Indomaret Tebet Barat")
        val resolved = TerminalCatalog.resolveLocation("01A4F29B", userAliases, "Tidak dikenali")
        assertEquals("Indomaret Tebet Barat", resolved)
    }

    @Test
    fun testKnownCatalogResolution() {
        val stnBogor = TerminalCatalog.resolveLocation("0101", emptyMap(), "Tidak dikenali")
        assertEquals("Stasiun Bogor", stnBogor)

        val tollCikarang = TerminalCatalog.resolveLocation("00000001", emptyMap(), "Tidak dikenali")
        assertEquals("Gerbang Tol Cikarang Utama", tollCikarang)

        val stnManggaBesar = TerminalCatalog.resolveLocation("0117", emptyMap(), "Tidak dikenali")
        assertEquals("Stasiun Mangga Besar", stnManggaBesar)
    }

    @Test
    fun testRawHexMaskedAsUnknown() {
        val resolved = TerminalCatalog.resolveLocation("A1B2C3D4", emptyMap(), "Tidak dikenali")
        assertEquals("Tidak dikenali", resolved)

        val resolvedEnglish = TerminalCatalog.resolveLocation("90FFAA12", emptyMap(), "Unknown")
        assertEquals("Unknown", resolvedEnglish)

        val resolvedSpacesHex = TerminalCatalog.resolveLocation("20202020", emptyMap(), "Tidak dikenali")
        assertEquals("Tidak dikenali", resolvedSpacesHex)

        // HT0 / ht0 terminal hardware codes masked as unknown
        val resolvedHt0 = TerminalCatalog.resolveLocation("HT0", emptyMap(), "Tidak dikenali")
        assertEquals("Tidak dikenali", resolvedHt0)

        val resolvedHt0Lower = TerminalCatalog.resolveLocation("ht0", emptyMap(), "Tidak dikenali")
        assertEquals("Tidak dikenali", resolvedHt0Lower)

        val resolvedHt0Prefix = TerminalCatalog.resolveLocation("HT0194", emptyMap(), "Tidak dikenali")
        assertEquals("Tidak dikenali", resolvedHt0Prefix)
    }

    @Test
    fun testPreDecodedHumanNamePreserved() {
        val resolved = TerminalCatalog.resolveLocation("Stasiun Sudirman", emptyMap(), "Tidak dikenali")
        assertEquals("Stasiun Sudirman", resolved)

        val retail = TerminalCatalog.resolveLocation("Alfamart Veteran", emptyMap(), "Tidak dikenali")
        assertEquals("Alfamart Veteran", retail)
    }

    @Test
    fun testIsDecodedHumanNameValidation() {
        assertTrue(TerminalCatalog.isDecodedHumanName("Stasiun Tebet"))
        assertTrue(TerminalCatalog.isDecodedHumanName("Indomaret Point"))
        assertTrue(TerminalCatalog.isDecodedHumanName("Gerbang Tol Kuningan 1"))

        assertFalse(TerminalCatalog.isDecodedHumanName(null))
        assertFalse(TerminalCatalog.isDecodedHumanName(""))
        assertFalse(TerminalCatalog.isDecodedHumanName("A1B2C3D4"))
        assertFalse(TerminalCatalog.isDecodedHumanName("01A4F29B"))
        assertFalse(TerminalCatalog.isDecodedHumanName("Station 0x9999"))
        assertFalse(TerminalCatalog.isDecodedHumanName("HT0"))
        assertFalse(TerminalCatalog.isDecodedHumanName("ht0"))
        assertFalse(TerminalCatalog.isDecodedHumanName("   "))
    }
}
