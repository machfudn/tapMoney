package id.tapmoney.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val downloadUrl: String?,
    val apkName: String?,
    val apkSize: Long?,
    val isNewer: Boolean
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val info: AppReleaseInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

object AppUpdateChecker {

    private const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/machfudn/tapMoney/releases/latest"

    fun parseSemVer(ver: String): Triple<Int, Int, Int>? {
        val clean = ver.trim().removePrefix("v").removePrefix("V")
        val parts = clean.split(".")
        if (parts.size < 2) return null
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val patch = parts.getOrNull(2)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        return Triple(major, minor, patch)
    }

    fun isVersionNewer(remoteTag: String, currentVersion: String): Boolean {
        val remote = parseSemVer(remoteTag) ?: return false
        val current = parseSemVer(currentVersion) ?: return false

        if (remote.first != current.first) {
            return remote.first > current.first
        }
        if (remote.second != current.second) {
            return remote.second > current.second
        }
        return remote.third > current.third
    }

    fun parseReleaseJson(json: String, currentVersion: String): AppReleaseInfo? {
        val tagRegex = Regex(""""tag_name"\s*:\s*"([^"]+)"""")
        val nameRegex = Regex(""""name"\s*:\s*"([^"]+)"""")
        val urlRegex = Regex(""""html_url"\s*:\s*"([^"]+)"""")

        val tagName = tagRegex.find(json)?.groupValues?.getOrNull(1) ?: return null
        val releaseTitle = nameRegex.find(json)?.groupValues?.getOrNull(1) ?: tagName
        val htmlUrl = urlRegex.find(json)?.groupValues?.getOrNull(1) ?: ""

        val body = extractJsonStringField(json, "body") ?: ""
        val cleanVersion = tagName.removePrefix("v").removePrefix("V")
        val isNewer = isVersionNewer(tagName, currentVersion)

        val asset = extractApkAsset(json)

        return AppReleaseInfo(
            tagName = tagName,
            versionName = cleanVersion,
            releaseTitle = releaseTitle,
            releaseNotes = body,
            htmlUrl = htmlUrl,
            downloadUrl = asset.first,
            apkName = asset.second,
            apkSize = asset.third,
            isNewer = isNewer
        )
    }

    private fun extractJsonStringField(json: String, key: String): String? {
        val regex = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"")
        val match = regex.find(json) ?: return null
        val raw = match.groupValues.getOrNull(1) ?: return null
        return raw
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\\"", "\"")
    }

    private fun extractApkAsset(json: String): Triple<String?, String?, Long?> {
        val assetBlockRegex = Regex("""\{[^{}]*"browser_download_url"[^{}]*\}""")
        val urlRegex = Regex(""""browser_download_url"\s*:\s*"([^"]+\.apk)"""")
        val nameRegex = Regex(""""name"\s*:\s*"([^"]+\.apk)"""")
        val sizeRegex = Regex(""""size"\s*:\s*(\d+)""")

        for (match in assetBlockRegex.findAll(json)) {
            val block = match.value
            val urlMatch = urlRegex.find(block)
            if (urlMatch != null) {
                val apkUrl = urlMatch.groupValues.getOrNull(1)
                val apkName = nameRegex.find(block)?.groupValues?.getOrNull(1) ?: "TapMoney-latest.apk"
                val apkSize = sizeRegex.find(block)?.groupValues?.getOrNull(1)?.toLongOrNull()
                return Triple(apkUrl, apkName, apkSize)
            }
        }
        return Triple(null, null, null)
    }

    suspend fun checkForUpdates(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(GITHUB_LATEST_RELEASE_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "TapMoneyApp")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val json = reader.readText()
                reader.close()

                val info = parseReleaseJson(json, currentVersion)
                if (info != null) {
                    if (info.isNewer) {
                        UpdateCheckResult.UpdateAvailable(info)
                    } else {
                        UpdateCheckResult.UpToDate(currentVersion)
                    }
                } else {
                    UpdateCheckResult.Error("Format rilis tidak dapat dibaca")
                }
            } else if (responseCode == 404) {
                UpdateCheckResult.UpToDate(currentVersion)
            } else {
                UpdateCheckResult.Error("HTTP $responseCode saat menghubungi GitHub")
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.localizedMessage ?: "Gagal terhubung ke GitHub")
        } finally {
            connection?.disconnect()
        }
    }
}
