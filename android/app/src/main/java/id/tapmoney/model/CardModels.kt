package id.tapmoney.model

enum class CardCategory(val label: String) {
    DOMESTIC_BANKING("Perbankan Indonesia"),
    DOMESTIC_TRANSIT("Transit Domestik"),
    REGIONAL_TRANSIT("Transit Regional / Internasional")
}

enum class CardIssuer(val displayName: String, val category: CardCategory = CardCategory.DOMESTIC_BANKING) {
    MANDIRI("Mandiri e-Money", CardCategory.DOMESTIC_BANKING),
    BCA("BCA Flazz", CardCategory.DOMESTIC_BANKING),
    BRI("BRI Brizzi", CardCategory.DOMESTIC_BANKING),
    BNI("BNI TapCash", CardCategory.DOMESTIC_BANKING),
    DKI("Bank DKI JakCard", CardCategory.DOMESTIC_BANKING),
    MEGA("Bank Mega MegaCash", CardCategory.DOMESTIC_BANKING),
    KCI("KMT Commuter Line", CardCategory.DOMESTIC_TRANSIT),
    MRTJ("MRT Jakarta", CardCategory.DOMESTIC_TRANSIT),
    CEPAS("CEPAS (EZ-Link / NETS)", CardCategory.REGIONAL_TRANSIT),
    SUICA("Suica / PASMO / IC", CardCategory.REGIONAL_TRANSIT),
    EDY("Rakuten Edy", CardCategory.REGIONAL_TRANSIT),
    TMONEY("T-Money / Cashbee", CardCategory.REGIONAL_TRANSIT),
    CHINA_TRANSIT("China T-Union / PBOC", CardCategory.REGIONAL_TRANSIT),
    OPAL("Sydney Opal", CardCategory.REGIONAL_TRANSIT),
    UNKNOWN("Kartu Elektronik", CardCategory.DOMESTIC_BANKING)
}

data class CardTransactionItem(
    val type: String,
    val amount: Long,
    val balance: Long? = null,
    val date: String,
    val time: String,
    val terminalId: String? = null
)

data class CardBalanceResult(
    val cardType: String,
    val issuer: CardIssuer,
    val cardNumber: String,
    val cardId: String? = null,
    val balance: Long,
    val lastAmount: Long = 0,
    val history: List<CardTransactionItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val balanceKnown: Boolean = true,
    val debugLog: String = ""
)

data class StoredCard(
    val cardNumber: String,
    val cardType: String,
    val issuer: CardIssuer,
    val balance: Long,
    val timestamp: Long,
    val history: List<CardTransactionItem> = emptyList()
)

data class UserSettings(
    val enableSound: Boolean = true,
    val enableHaptics: Boolean = true,
    val maskCardNumber: Boolean = true,
    val autoSaveHistory: Boolean = true,
    val hideBalance: Boolean = false,
    val speakAmount: Boolean = false,
    val showTransactionColors: Boolean = true,
    val scanDomesticBanking: Boolean = true,
    val scanDomesticTransit: Boolean = true,
    val scanRegionalTransit: Boolean = true,
    val darkTheme: Boolean? = null
)