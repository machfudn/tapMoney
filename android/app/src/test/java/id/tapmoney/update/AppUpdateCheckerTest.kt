package id.tapmoney.update

import org.junit.Assert.*
import org.junit.Test

class AppUpdateCheckerTest {

    @Test
    fun `isVersionNewer correctly detects newer SemVer`() {
        assertTrue(AppUpdateChecker.isVersionNewer("v1.1.9", "1.1.8"))
        assertTrue(AppUpdateChecker.isVersionNewer("v1.2.0", "1.1.8"))
        assertTrue(AppUpdateChecker.isVersionNewer("v2.0.0", "1.1.8"))
        assertTrue(AppUpdateChecker.isVersionNewer("1.1.9", "1.1.8"))
        assertTrue(AppUpdateChecker.isVersionNewer("v1.10.0", "1.9.9"))
    }

    @Test
    fun `isVersionNewer returns false for equal or older versions`() {
        assertFalse(AppUpdateChecker.isVersionNewer("v1.1.8", "1.1.8"))
        assertFalse(AppUpdateChecker.isVersionNewer("1.1.8", "1.1.8"))
        assertFalse(AppUpdateChecker.isVersionNewer("v1.1.7", "1.1.8"))
        assertFalse(AppUpdateChecker.isVersionNewer("v1.0.9", "1.1.0"))
        assertFalse(AppUpdateChecker.isVersionNewer("invalid", "1.1.8"))
    }

    @Test
    fun `parseReleaseJson parses release metadata and apk asset`() {
        val sampleJson = """
        {
            "tag_name": "v1.1.9",
            "name": "TapMoney v1.1.9",
            "body": "* Perbaikan performa\n* Dukungan kartu baru",
            "html_url": "https://github.com/machfudn/tap-money-info/releases/tag/v1.1.9",
            "assets": [
                {
                    "name": "TapMoney-v1.1.9.apk",
                    "size": 15728640,
                    "browser_download_url": "https://github.com/machfudn/tap-money-info/releases/download/v1.1.9/TapMoney-v1.1.9.apk"
                },
                {
                    "name": "checksums.txt",
                    "size": 128,
                    "browser_download_url": "https://github.com/machfudn/tap-money-info/releases/download/v1.1.9/checksums.txt"
                }
            ]
        }
        """.trimIndent()

        val release = AppUpdateChecker.parseReleaseJson(sampleJson, currentVersion = "1.1.8")
        assertNotNull(release)
        assertEquals("v1.1.9", release!!.tagName)
        assertEquals("1.1.9", release.versionName)
        assertEquals("TapMoney v1.1.9", release.releaseTitle)
        assertEquals("https://github.com/machfudn/tap-money-info/releases/tag/v1.1.9", release.htmlUrl)
        assertEquals("https://github.com/machfudn/tap-money-info/releases/download/v1.1.9/TapMoney-v1.1.9.apk", release.downloadUrl)
        assertEquals("TapMoney-v1.1.9.apk", release.apkName)
        assertEquals(15728640L, release.apkSize)
        assertTrue(release.isNewer)
    }

    @Test
    fun `parseReleaseJson returns null on missing tag_name`() {
        val badJson = """{"name": "TapMoney"}"""
        val release = AppUpdateChecker.parseReleaseJson(badJson, currentVersion = "1.1.8")
        assertNull(release)
    }

    @Test
    fun `parseReleaseJson flags isNewer false when version is same or older`() {
        val sameJson = """{"tag_name": "v1.1.8", "name": "v1.1.8"}"""
        val release = AppUpdateChecker.parseReleaseJson(sameJson, currentVersion = "1.1.8")
        assertNotNull(release)
        assertFalse(release!!.isNewer)
    }
}
