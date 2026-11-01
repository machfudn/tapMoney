package id.tapmoney.data

/**
 * Offline catalog and resolver for transit, toll, and merchant Terminal IDs.
 * Prevents raw undecoded hex codes from leaking into the UI.
 */
object TerminalCatalog {
    private val KNOWN_TERMINALS: Map<String, String> = mapOf(
        // Commuter Line / KMT Stations
        "0001" to "Stasiun Tanah Abang",
        "0101" to "Stasiun Bogor",
        "0102" to "Stasiun Cilebut",
        "0103" to "Stasiun Bojonggede",
        "0104" to "Stasiun Citayam",
        "0105" to "Stasiun Depok",
        "0106" to "Stasiun Depok Baru",
        "0107" to "Stasiun Pondok Cina",
        "0108" to "Stasiun Univ. Indonesia",
        "0109" to "Stasiun Univ. Pancasila",
        "010A" to "Stasiun Lenteng Agung",
        "010B" to "Stasiun Tanjung Barat",
        "010C" to "Stasiun Pasar Minggu",
        "010D" to "Stasiun Pasar Minggu Baru",
        "010E" to "Stasiun Duren Kalibata",
        "010F" to "Stasiun Cawang",
        "0110" to "Stasiun Tebet",
        "0111" to "Stasiun Manggarai",
        "0112" to "Stasiun Cikini",
        "0113" to "Stasiun Gondangdia",
        "0114" to "Stasiun Gambir",
        "0115" to "Stasiun Juanda",
        "0116" to "Stasiun Sawah Besar",
        "0117" to "Stasiun Mangga Besar",
        "0118" to "Stasiun Jayakarta",
        "0119" to "Stasiun Jakarta Kota",
        "0201" to "Stasiun Bekasi",
        "0202" to "Stasiun Kranji",
        "0203" to "Stasiun Cakung",
        "0204" to "Stasiun Klender Baru",
        "0205" to "Stasiun Buaran",
        "0206" to "Stasiun Klender",
        "0207" to "Stasiun Jatinegara",
        "0208" to "Stasiun Cikarang",
        "0301" to "Stasiun Rangkasbitung",
        "0310" to "Stasiun Palmerah",
        "0401" to "Stasiun Tangerang",

        // Toll Gates Jabodetabek
        "00000001" to "Gerbang Tol Cikarang Utama",
        "00000002" to "Gerbang Tol Cilandak Utama",
        "00000003" to "Gerbang Tol Semanggi",
        "00000004" to "Gerbang Tol Senayan",
        "00000005" to "Gerbang Tol Tomang",
        "00000006" to "Gerbang Tol Kapuk",
        "00000007" to "Gerbang Tol Halim",
        "00000008" to "Gerbang Tol Cibubur",

        // Transit & Merchant Common Codes
        "IDM" to "Indomaret",
        "ALF" to "Alfamart",
        "SPBU" to "SPBU Pertamina",
        "TJ" to "TransJakarta Busway",
        "MRT" to "MRT Jakarta",
        "LRT" to "LRT Jabodebek"
    )

    /**
     * Checks if a string represents a verified human-readable name,
     * as opposed to an undecoded hex string, unmapped code, or blank.
     */
    fun isDecodedHumanName(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()
        if (trimmed.length < 2) return false

        if (trimmed.equals("Tidak dikenali", ignoreCase = true) || trimmed.equals("Unknown", ignoreCase = true)) {
            return false
        }

        // Only accept explicitly recognized transit station names
        return id.tapmoney.nfc.parser.transit.KmtStationCatalog.isKnownStation(trimmed)
    }

    /**
     * Resolves a terminal ID into a human-friendly location string.
     * Hierarchy:
     * 1. User private alias (highest precedence)
     * 2. Static / Synced terminal catalog mapping
     * 3. Pre-decoded human name (e.g. from KMT station catalog)
     * 4. Fallback [unknownLabel] (strictly hides raw hex)
     */
    fun resolveLocation(
        terminalId: String?,
        userAliases: Map<String, String> = emptyMap(),
        unknownLabel: String = "Tidak dikenali"
    ): String {
        if (terminalId.isNullOrBlank()) return unknownLabel

        val trimmed = terminalId.trim()

        // 1. User private alias
        val userAlias = userAliases[trimmed]?.trim()
        if (!userAlias.isNullOrBlank()) return userAlias

        // 2. Known static catalog
        val fromCatalog = KNOWN_TERMINALS[trimmed] ?: KNOWN_TERMINALS[trimmed.uppercase()]
        if (!fromCatalog.isNullOrBlank()) return fromCatalog

        // Handheld terminal hardware prefix (e.g. HT0) -> Mask as unknown
        if (trimmed.startsWith("HT0", ignoreCase = true)) return unknownLabel

        // 3. Pre-decoded readable name
        if (isDecodedHumanName(trimmed)) return trimmed

        // 4. Undecoded hex / unknown code -> Never display raw code
        return unknownLabel
    }
}
