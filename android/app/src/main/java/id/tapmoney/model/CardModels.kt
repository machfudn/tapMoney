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
    val type: String,          // "Top up" or "Payment"
    val amount: Long,
    val balance: Long? = null,
    val date: String,          // YYYY-MM-DD
    val time: String,          // HH:mm:ss
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
    val timestamp: Long
)

data class UserSettings(
    val maskCardNumber: Boolean = false,
    val hideBalance: Boolean = false,
    val darkTheme: Boolean? = null // null = system, true = dark, false = light
)
