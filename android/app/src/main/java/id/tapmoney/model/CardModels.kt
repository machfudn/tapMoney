package id.tapmoney.model

enum class CardIssuer(val displayName: String) {
    MANDIRI("Mandiri e-Money"),
    BCA("BCA Flazz"),
    BRI("BRI Brizzi"),
    BNI("BNI TapCash"),
    DKI("Bank DKI JakCard"),
    KCI("KMT Commuter Line"),
    MEGA("Bank Mega MegaCash"),
    UNKNOWN("Kartu Elektronik")
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
    val timestamp: Long = System.currentTimeMillis()
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
    val darkTheme: Boolean? = null
)