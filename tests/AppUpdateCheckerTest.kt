package id.tapmoney.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun testSemVerParsingAndComparison() {
        val vStable = SemVer.parse("1.1.13")
        assertNotNull(vStable)
        assertEquals(1, vStable?.major)
        assertEquals(1, vStable?.minor)
        assertEquals(13, vStable?.patch)
        assertNull(vStable?.preRelease)

        val vBeta1 = SemVer.parse("v1.1.14-beta.1")
        assertNotNull(vBeta1)
        assertEquals(1, vBeta1?.major)
        assertEquals(1, vBeta1?.minor)
        assertEquals(14, vBeta1?.patch)
        assertEquals("beta.1", vBeta1?.preRelease)

        val vBeta2 = SemVer.parse("1.1.14-beta.2")
        val vNextStable = SemVer.parse("1.1.14")

        // Beta 1 is newer than older stable 1.1.13
        assertTrue(vBeta1!! > vStable!!)
        // Beta 2 is newer than Beta 1
        assertTrue(vBeta2!! > vBeta1)
        // Final stable 1.1.14 is newer than Beta 2
        assertTrue(vNextStable!! > vBeta2)
        // Older stable is not newer than beta
        assertFalse(vStable > vBeta1)
    }

    @Test
    fun testIsVersionNewerWithPreRelease() {
        assertTrue("Beta 1 harus lebih baru dari stable lama", AppUpdateChecker.isVersionNewer("v1.1.14-beta.1", "1.1.13"))
        assertTrue("Beta 2 harus lebih baru dari Beta 1", AppUpdateChecker.isVersionNewer("v1.1.14-beta.2", "1.1.14-beta.1"))
        assertTrue("Rilis stabil harus lebih baru dari Beta", AppUpdateChecker.isVersionNewer("v1.1.14", "1.1.14-beta.2"))
        assertFalse("Versi sama tidak boleh dianggap lebih baru", AppUpdateChecker.isVersionNewer("v1.1.13", "1.1.13"))
        assertFalse("Versi lama tidak boleh dianggap lebih baru", AppUpdateChecker.isVersionNewer("v1.1.12", "1.1.13"))
    }

    @Test
    fun testParseReleasesJsonDualChannel() {
        val sampleReleasesJson = """
            [
              {
                "tag_name": "v1.1.14-beta.1",
                "name": "TapMoney v1.1.14 (Beta)",
                "draft": false,
                "prerelease": true,
                "html_url": "https://github.com/machfudn/tapMoney/releases/tag/v1.1.14-beta.1",
                "body": "### 🇮🇩 Ringkasan Rilis Beta\n* Fitur uji coba\nSHA-256: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "assets": [
                  {
                    "name": "TapMoney-v1.1.14-beta.1.apk",
                    "browser_download_url": "https://github.com/machfudn/tapMoney/releases/download/v1.1.14-beta.1/TapMoney-v1.1.14-beta.1.apk",
                    "size": 5242880
                  }
                ]
              },
              {
                "tag_name": "v1.1.13",
                "name": "TapMoney v1.1.13",
                "draft": false,
                "prerelease": false,
                "html_url": "https://github.com/machfudn/tapMoney/releases/tag/v1.1.13",
                "body": "### 🇮🇩 Ringkasan Rilis\n* Versi stabil\nSHA-256: abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                "assets": [
                  {
                    "name": "TapMoney-v1.1.13.apk",
                    "browser_download_url": "https://github.com/machfudn/tapMoney/releases/download/v1.1.13/TapMoney-v1.1.13.apk",
                    "size": 5120000
                  }
                ]
              }
            ]
        """.trimIndent()

        val dual = AppUpdateChecker.parseReleasesJson(sampleReleasesJson, currentVersion = "1.1.13")

        assertNotNull("Rilis stabil harus terdeteksi", dual.stable)
        assertEquals("1.1.13", dual.stable?.versionName)
        assertFalse("Rilis stabil tidak boleh berstatus prerelease", dual.stable?.isPrerelease ?: true)
        assertFalse("Versi stabil sama dengan terpasang", dual.stable?.isNewer ?: true)

        assertNotNull("Rilis beta harus terdeteksi", dual.beta)
        assertEquals("1.1.14-beta.1", dual.beta?.versionName)
        assertTrue("Rilis beta harus berstatus prerelease", dual.beta?.isPrerelease ?: false)
        assertTrue("Rilis beta lebih baru dari versi 1.1.13", dual.beta?.isNewer ?: false)
        assertTrue("Dual result harus menyatakan ada update", dual.hasAnyUpdate)
    }
}
