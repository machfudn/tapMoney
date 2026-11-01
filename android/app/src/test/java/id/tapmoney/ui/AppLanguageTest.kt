package id.tapmoney.ui

import id.tapmoney.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppLanguageTest {

    @Test
    fun `default user settings has null appLanguage following system`() {
        val settings = UserSettings()
        assertNull(settings.appLanguage)
    }

    @Test
    fun `resolveIsEnglish returns true for en and false for id`() {
        assertTrue(resolveIsEnglish("en"))
        assertFalse(resolveIsEnglish("id"))
    }

    @Test
    fun `resolveIsEnglish falls back to system locale when null`() {
        val prevLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertTrue(resolveIsEnglish(null))

            Locale.setDefault(Locale("id", "ID"))
            assertFalse(resolveIsEnglish(null))
        } finally {
            Locale.setDefault(prevLocale)
        }
    }

    @Test
    fun `getAppStrings returns expected strings for Indonesian and English`() {
        val idStrings = getAppStrings("id")
        assertEquals("RIWAYAT CHIP", idStrings.tabChipHistory)
        assertEquals("KARTU TERSIMPAN", idStrings.tabSavedCards)
        assertEquals("PENGATURAN", idStrings.settingsTitle)
        assertEquals("Bahasa Aplikasi", idStrings.secLanguage)
        assertEquals("Sistem", idStrings.langSystem)
        assertEquals("Indonesia", idStrings.langIndonesian)
        assertEquals("English", idStrings.langEnglish)
        assertEquals("Lihat Semua", idStrings.viewAll)
        assertEquals("Lebih Sedikit", idStrings.showLess)
        assertEquals("Top Up", idStrings.topUp)
        assertEquals("Pembayaran", idStrings.payment)
        assertEquals("Lokasi", idStrings.locationHeader)

        val enStrings = getAppStrings("en")
        assertEquals("CHIP HISTORY", enStrings.tabChipHistory)
        assertEquals("SAVED CARDS", enStrings.tabSavedCards)
        assertEquals("SETTINGS", enStrings.settingsTitle)
        assertEquals("App Language", enStrings.secLanguage)
        assertEquals("System", enStrings.langSystem)
        assertEquals("Indonesia", enStrings.langIndonesian)
        assertEquals("English", enStrings.langEnglish)
        assertEquals("View All", enStrings.viewAll)
        assertEquals("Show Less", enStrings.showLess)
        assertEquals("Top Up", enStrings.topUp)
        assertEquals("Payment", enStrings.payment)
        assertEquals("Location", enStrings.locationHeader)
    }

    @Test
    fun `JsonBackedAppStrings correctly overrides string keys from json map`() {
        val mockData = mapOf(
            "viewAll" to "Tampilkan Semua",
            "payment" to "Transaksi Debit"
        )
        val jsonStrings = JsonBackedAppStrings(mockData, AppStringsId)
        assertEquals("Tampilkan Semua", jsonStrings.viewAll)
        assertEquals("Transaksi Debit", jsonStrings.payment)
        assertEquals("Lebih Sedikit", jsonStrings.showLess) // fallback
    }

    @Test
    fun `id and en json asset files are valid and contain matching keys`() {
        val idFile = java.io.File("src/main/assets/lang/id.json")
        val enFile = java.io.File("src/main/assets/lang/en.json")
        assertTrue("id.json should exist", idFile.exists())
        assertTrue("en.json should exist", enFile.exists())

        val idMap = JsonI18nLoader.parseJsonString(idFile.readText())
        val enMap = JsonI18nLoader.parseJsonString(enFile.readText())

        assertEquals("Lihat Semua", idMap["viewAll"])
        assertEquals("View All", enMap["viewAll"])
        assertEquals("Pembayaran", idMap["payment"])
        assertEquals("Payment", enMap["payment"])
        assertEquals("Lokasi", idMap["locationHeader"])
        assertEquals("Location", enMap["locationHeader"])
        assertTrue("id.json should have at least 50 keys", idMap.size >= 50)
        assertTrue("en.json should have at least 50 keys", enMap.size >= 50)
    }
}
