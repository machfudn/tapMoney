package id.tapmoney.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.CardIssuer
import id.tapmoney.model.CardTransactionItem
import id.tapmoney.model.StoredCard

class CardDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_NAME = "tap_money.db"
        private const val DATABASE_VERSION = 2

        private const val TABLE_SAVED_CARDS = "saved_cards"
        private const val COL_CARD_NUMBER = "card_number"
        private const val COL_CARD_TYPE = "card_type"
        private const val COL_ISSUER = "issuer"
        private const val COL_BALANCE = "balance"
        private const val COL_TIMESTAMP = "timestamp"

        private const val TABLE_TRANSACTIONS = "card_transactions"
        private const val COL_ID = "id"
        private const val COL_TX_TYPE = "tx_type"
        private const val COL_AMOUNT = "amount"
        private const val COL_TX_BALANCE = "tx_balance"
        private const val COL_DATE = "tx_date"
        private const val COL_TIME = "tx_time"
        private const val COL_TERMINAL_ID = "terminal_id"

        private const val TABLE_LOCATION_ALIASES = "location_aliases"
        private const val COL_ALIAS_NAME = "alias_name"

        @Volatile private var instance: CardDatabase? = null
        fun getInstance(context: Context): CardDatabase = instance ?: synchronized(this) {
            instance ?: CardDatabase(context.applicationContext).also { instance = it }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_SAVED_CARDS (
                $COL_CARD_NUMBER TEXT PRIMARY KEY,
                $COL_CARD_TYPE TEXT NOT NULL,
                $COL_ISSUER TEXT NOT NULL,
                $COL_BALANCE INTEGER NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE $TABLE_TRANSACTIONS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CARD_NUMBER TEXT NOT NULL,
                $COL_TX_TYPE TEXT NOT NULL,
                $COL_AMOUNT INTEGER NOT NULL,
                $COL_TX_BALANCE INTEGER,
                $COL_DATE TEXT NOT NULL,
                $COL_TIME TEXT NOT NULL,
                $COL_TERMINAL_ID TEXT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE $TABLE_LOCATION_ALIASES (
                $COL_TERMINAL_ID TEXT PRIMARY KEY,
                $COL_ALIAS_NAME TEXT NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_TRANSACTIONS (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_CARD_NUMBER TEXT NOT NULL,
                    $COL_TX_TYPE TEXT NOT NULL,
                    $COL_AMOUNT INTEGER NOT NULL,
                    $COL_TX_BALANCE INTEGER,
                    $COL_DATE TEXT NOT NULL,
                    $COL_TIME TEXT NOT NULL,
                    $COL_TERMINAL_ID TEXT
                )
            """.trimIndent())
        }
    }

    @Synchronized
    fun saveCard(card: CardBalanceResult) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(COL_CARD_NUMBER, card.cardNumber)
                put(COL_CARD_TYPE, card.cardType)
                put(COL_ISSUER, card.issuer.name)
                put(COL_BALANCE, card.balance)
                put(COL_TIMESTAMP, card.timestamp)
            }
            db.insertWithOnConflict(TABLE_SAVED_CARDS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.delete(TABLE_TRANSACTIONS, "$COL_CARD_NUMBER = ?", arrayOf(card.cardNumber))
            card.history.forEach { tx ->
                db.insert(TABLE_TRANSACTIONS, null, ContentValues().apply {
                    put(COL_CARD_NUMBER, card.cardNumber)
                    put(COL_TX_TYPE, tx.type)
                    put(COL_AMOUNT, tx.amount)
                    tx.balance?.let { put(COL_TX_BALANCE, it) }
                    put(COL_DATE, tx.date)
                    put(COL_TIME, tx.time)
                    tx.terminalId?.let { put(COL_TERMINAL_ID, it) }
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getSavedCards(): List<StoredCard> {
        val list = mutableListOf<StoredCard>()
        readableDatabase.query(TABLE_SAVED_CARDS, null, null, null, null, null, "$COL_TIMESTAMP DESC").use { cursor ->
            while (cursor.moveToNext()) {
                val number = cursor.getString(cursor.getColumnIndexOrThrow(COL_CARD_NUMBER))
                val issuer = runCatching { CardIssuer.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(COL_ISSUER))) }
                    .getOrDefault(CardIssuer.UNKNOWN)
                list += StoredCard(
                    cardNumber = number,
                    cardType = cursor.getString(cursor.getColumnIndexOrThrow(COL_CARD_TYPE)),
                    issuer = issuer,
                    balance = cursor.getLong(cursor.getColumnIndexOrThrow(COL_BALANCE)),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP)),
                    history = getTransactions(number)
                )
            }
        }
        return list
    }

    private fun getTransactions(cardNumber: String): List<CardTransactionItem> {
        val list = mutableListOf<CardTransactionItem>()
        readableDatabase.query(TABLE_TRANSACTIONS, null, "$COL_CARD_NUMBER = ?", arrayOf(cardNumber), null, null, "$COL_ID DESC").use { cursor ->
            while (cursor.moveToNext()) {
                val balanceIndex = cursor.getColumnIndexOrThrow(COL_TX_BALANCE)
                list += CardTransactionItem(
                    type = cursor.getString(cursor.getColumnIndexOrThrow(COL_TX_TYPE)),
                    amount = cursor.getLong(cursor.getColumnIndexOrThrow(COL_AMOUNT)),
                    balance = if (cursor.isNull(balanceIndex)) null else cursor.getLong(balanceIndex),
                    date = cursor.getString(cursor.getColumnIndexOrThrow(COL_DATE)),
                    time = cursor.getString(cursor.getColumnIndexOrThrow(COL_TIME)),
                    terminalId = cursor.getString(cursor.getColumnIndexOrThrow(COL_TERMINAL_ID))
                )
            }
        }
        return list
    }

    fun deleteCard(cardNumber: String) {
        writableDatabase.delete(TABLE_TRANSACTIONS, "$COL_CARD_NUMBER = ?", arrayOf(cardNumber))
        writableDatabase.delete(TABLE_SAVED_CARDS, "$COL_CARD_NUMBER = ?", arrayOf(cardNumber))
    }

    fun clearAllSavedCards() {
        writableDatabase.delete(TABLE_TRANSACTIONS, null, null)
        writableDatabase.delete(TABLE_SAVED_CARDS, null, null)
    }

    fun getLocationAliases(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        readableDatabase.query(TABLE_LOCATION_ALIASES, arrayOf(COL_TERMINAL_ID, COL_ALIAS_NAME), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) map[cursor.getString(0)] = cursor.getString(1)
        }
        return map
    }

    fun setLocationAlias(terminalId: String, name: String) {
        writableDatabase.insertWithOnConflict(TABLE_LOCATION_ALIASES, null, ContentValues().apply {
            put(COL_TERMINAL_ID, terminalId)
            put(COL_ALIAS_NAME, name)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
}