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
    val checksum: String? = null,
    val isNewer: Boolean,
    val isPrerelease: Boolean = false
)

data class DualReleaseResult(
    val stable: AppReleaseInfo?,
    val beta: AppReleaseInfo?,
    val currentVersion: String
) {
    val hasAnyUpdate: Boolean
        get() = (stable?.isNewer == true) || (beta?.isNewer == true)
}

sealed interface UpdateCheckResult {
    data class Success(val dual: DualReleaseResult) : UpdateCheckResult
    data class UpdateAvailable(val info: AppReleaseInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)

        // SemVer: 1.1.14 (stable) is higher than 1.1.14-beta.1
        if (preRelease == null && other.preRelease != null) return 1
        if (preRelease != null && other.preRelease == null) return -1
        if (preRelease == null && other.preRelease == null) return 0

        return comparePreRelease(preRelease!!, other.preRelease!!)
    }

    companion object {
        fun parse(ver: String): SemVer? {
            val clean = ver.trim().removePrefix("v").removePrefix("V")
            if (clean.isBlank()) return null

            val hyphenIndex = clean.indexOf('-')
            val numPart = if (hyphenIndex != -1) clean.substring(0, hyphenIndex) else clean
            val preReleasePart = if (hyphenIndex != -1 && hyphenIndex + 1 < clean.length) {
                clean.substring(hyphenIndex + 1)
            } else null

            val parts = numPart.split(".")
            if (parts.size < 2) return null
            val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: return null
            val patch = parts.getOrNull(2)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            return SemVer(major, minor, patch, preReleasePart)
        }

        private fun comparePreRelease(p1: String, p2: String): Int {
            val parts1 = p1.split(".")
            val parts2 = p2.split(".")
            val maxLen = maxOf(parts1.size, parts2.size)
            for (i in 0 until maxLen) {
                val s1 = parts1.getOrNull(i)
                val s2 = parts2.getOrNull(i)
                if (s1 == null) return -1
                if (s2 == null) return 1
                val n1 = s1.toIntOrNull()
                val n2 = s2.toIntOrNull()
                if (n1 != null && n2 != null) {
                    if (n1 != n2) return n1.compareTo(n2)
                } else {
                    val cmp = s1.compareTo(s2, ignoreCase = true)
                    if (cmp != 0) return cmp
                }
            }
            return 0
        }
    }
}

object AppUpdateChecker {

    private const val GITHUB_RELEASES_URL =
        "https://api.github.com/repos/machfudn/tapMoney/releases?per_page=15"

    fun parseSemVer(ver: String): Triple<Int, Int, Int>? {
        val s = SemVer.parse(ver) ?: return null
        return Triple(s.major, s.minor, s.patch)
    }

    fun isVersionNewer(remoteTag: String, currentVersion: String): Boolean {
        val remote = SemVer.parse(remoteTag) ?: return false
        val current = SemVer.parse(currentVersion) ?: return false
        return remote > current
    }

    fun localizeReleaseNotes(rawNotes: String, isEnglish: Boolean): String {
        if (rawNotes.isBlank()) return ""
        val langTag = if (isEnglish) "en" else "id"
        val regex = Regex("""<!--\s*lang:$langTag\s*-->([\s\S]*?)<!--\s*/lang:$langTag\s*-->""", RegexOption.IGNORE_CASE)
        val match = regex.find(rawNotes)?.groupValues?.getOrNull(1)?.trim()
        if (!match.isNullOrBlank()) return match

        val headerPattern = if (isEnglish) {
            Regex("""(?i)(?:###?\s*(?:🇬🇧|English|Release Notes)[\s\S]*?)(?=###?\s*(?:🇮🇩|Bahasa Indonesia|Ringkasan Rilis|Informasi Berkas|File Information)|$)""")
        } else {
            Regex("""(?i)(?:###?\s*(?:🇮🇩|Bahasa Indonesia|Ringkasan Rilis)[\s\S]*?)(?=###?\s*(?:🇬🇧|English|Release Notes|Informasi Berkas|File Information)|$)""")
        }
        val headerMatch = headerPattern.find(rawNotes)?.value?.trim()
        if (!headerMatch.isNullOrBlank()) return headerMatch

        return rawNotes.trim()
    }

    fun splitJsonObjects(jsonArrayStr: String): List<String> {
        val trimmed = jsonArrayStr.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        val list = mutableListOf<String>()
        var depth = 0
        var inString = false
        var escape = false
        var startIndex = -1

        for (i in trimmed.indices) {
            val c = trimmed[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\') {
                if (inString) escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') {
                    if (depth == 0) startIndex = i
                    depth++
                } else if (c == '}') {
                    depth--
                    if (depth == 0 && startIndex != -1) {
                        list.add(trimmed.substring(startIndex, i + 1))
                        startIndex = -1
                    }
                }
            }
        }
        return list
    }

    fun parseReleaseJson(json: String, currentVersion: String): AppReleaseInfo? {
        val tagRegex = Regex(""""tag_name"\s*:\s*"([^"]+)"""")
        val nameRegex = Regex(""""name"\s*:\s*"([^"]+)"""")
        val urlRegex = Regex(""""html_url"\s*:\s*"([^"]+)"""")
        val prereleaseRegex = Regex(""""prerelease"\s*:\s*(true|false)""")

        val tagName = tagRegex.find(json)?.groupValues?.getOrNull(1) ?: return null
        val releaseTitle = nameRegex.find(json)?.groupValues?.getOrNull(1) ?: tagName
        val htmlUrl = urlRegex.find(json)?.groupValues?.getOrNull(1) ?: ""
        val isPrerelease = prereleaseRegex.find(json)?.groupValues?.getOrNull(1)?.toBoolean() ?: false

        val body = extractJsonStringField(json, "body") ?: ""
        val cleanVersion = tagName.removePrefix("v").removePrefix("V")
        val isNewer = isVersionNewer(tagName, currentVersion)

        val shaRegex = Regex("""(?i)(?:sha-?256|checksum)\s*[:=]\s*([a-fA-F0-9]{64})""")
        val checksum = shaRegex.find(body)?.groupValues?.getOrNull(1)
            ?: Regex("""\b([a-fA-F0-9]{64})\b""").find(body)?.groupValues?.getOrNull(1)

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
            checksum = checksum,
            isNewer = isNewer,
            isPrerelease = isPrerelease
        )
    }

    fun parseReleasesJson(json: String, currentVersion: String): DualReleaseResult {
        val trimmed = json.trim()
        val objects = if (trimmed.startsWith("[")) splitJsonObjects(trimmed) else listOf(trimmed)
        var latestStable: AppReleaseInfo? = null
        var latestBeta: AppReleaseInfo? = null

        val draftRegex = Regex(""""draft"\s*:\s*(true|false)""")
        val prereleaseRegex = Regex(""""prerelease"\s*:\s*(true|false)""")

        for (obj in objects) {
            val isDraft = draftRegex.find(obj)?.groupValues?.getOrNull(1)?.toBoolean() ?: false
            if (isDraft) continue

            val isPrerelease = prereleaseRegex.find(obj)?.groupValues?.getOrNull(1)?.toBoolean() ?: false
            val parsed = parseReleaseJson(obj, currentVersion) ?: continue

            if (isPrerelease) {
                if (latestBeta == null) {
                    latestBeta = parsed.copy(isPrerelease = true)
                }
            } else {
                if (latestStable == null) {
                    latestStable = parsed.copy(isPrerelease = false)
                }
            }

            if (latestStable != null && latestBeta != null) break
        }

        return DualReleaseResult(
            stable = latestStable,
            beta = latestBeta,
            currentVersion = currentVersion
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
            val url = URL(GITHUB_RELEASES_URL)
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

                val dual = parseReleasesJson(json, currentVersion)
                UpdateCheckResult.Success(dual)
            } else if (responseCode == 404) {
                UpdateCheckResult.Success(DualReleaseResult(null, null, currentVersion))
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
