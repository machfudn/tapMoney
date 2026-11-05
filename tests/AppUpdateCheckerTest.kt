package id.tapmoney.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    private val sampleBilingualNotes = """
        <!-- lang:id -->
        ### 🇮🇩 Ringkasan Rilis (v1.1.13)
        * Perbaikan koneksi NFC
        * Sinkronisasi status cepat
        <!-- /lang:id -->

        <!-- lang:en -->
        ### 🇬🇧 Release Notes (v1.1.13)
        * NFC connection improvements
        * Fast status synchronization
        <!-- /lang:en -->
    """.trimIndent()

    @Test
    fun testLocalizeReleaseNotesIndonesian() {
        val result = AppUpdateChecker.localizeReleaseNotes(sampleBilingualNotes, isEnglish = false)
        assertTrue("Harus memuat heading bahasa Indonesia", result.contains("🇮🇩 Ringkasan Rilis"))
        assertTrue("Harus memuat poin bahasa Indonesia", result.contains("Perbaikan koneksi NFC"))
        assertTrue("Tidak boleh memuat teks bahasa Inggris", !result.contains("Release Notes (v1.1.13)"))
    }

    @Test
    fun testLocalizeReleaseNotesEnglish() {
        val result = AppUpdateChecker.localizeReleaseNotes(sampleBilingualNotes, isEnglish = true)
        assertTrue("Must contain English heading", result.contains("🇬🇧 Release Notes"))
        assertTrue("Must contain English points", result.contains("NFC connection improvements"))
        assertTrue("Must not contain Indonesian text", !result.contains("Ringkasan Rilis (v1.1.13)"))
    }

    @Test
    fun testLocalizeReleaseNotesFallbackHeaderWithoutTags() {
        val notesWithoutTags = """
            ### 🇮🇩 Bahasa Indonesia
            * Fitur lokal baru
            ### 🇬🇧 English
            * New local feature
        """.trimIndent()

        val idResult = AppUpdateChecker.localizeReleaseNotes(notesWithoutTags, isEnglish = false)
        assertTrue("Fallback heading Indonesia", idResult.contains("Fitur lokal baru"))

        val enResult = AppUpdateChecker.localizeReleaseNotes(notesWithoutTags, isEnglish = true)
        assertTrue("Fallback heading English", enResult.contains("New local feature"))
    }

    @Test
    fun testLocalizeReleaseNotesLegacyPlainTextFallback() {
        val legacyNotes = "* Peningkatan kestabilan aplikasi umum."
        val result = AppUpdateChecker.localizeReleaseNotes(legacyNotes, isEnglish = true)
        assertEquals("Teks rilis lama tanpa tag harus dikembalikan utuh", legacyNotes, result)
    }

    @Test
    fun testLocalizeReleaseNotesBlank() {
        assertEquals("", AppUpdateChecker.localizeReleaseNotes("", isEnglish = false))
        assertEquals("", AppUpdateChecker.localizeReleaseNotes("   ", isEnglish = true))
    }
}
