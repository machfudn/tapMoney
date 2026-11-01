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

        val enStrings = getAppStrings("en")
        assertEquals("CHIP HISTORY", enStrings.tabChipHistory)
        assertEquals("SAVED CARDS", enStrings.tabSavedCards)
        assertEquals("SETTINGS", enStrings.settingsTitle)
        assertEquals("App Language", enStrings.secLanguage)
        assertEquals("System", enStrings.langSystem)
        assertEquals("Indonesia", enStrings.langIndonesian)
        assertEquals("English", enStrings.langEnglish)
    }
}
