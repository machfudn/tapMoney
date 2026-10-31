package id.tapmoney.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import id.tapmoney.model.CardBalanceResult
import id.tapmoney.model.CardIssuer
import id.tapmoney.model.StoredCard

class CardDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "tap_money.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_SAVED_CARDS = "saved_cards"
        private const val COL_CARD_NUMBER = "card_number"
        private const val COL_CARD_TYPE = "card_type"
        private const val COL_ISSUER = "issuer"
        private const val COL_BALANCE = "balance"
        private const val COL_TIMESTAMP = "timestamp"

        private const val TABLE_LOCATION_ALIASES = "location_aliases"
        private const val COL_TERMINAL_ID = "terminal_id"
        private const val COL_ALIAS_NAME = "alias_name"

        @Volatile
        private var instance: CardDatabase? = null

        fun getInstance(context: Context): CardDatabase {
            return instance ?: synchronized(this) {
                instance ?: CardDatabase(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_SAVED_CARDS (
                $COL_CARD_NUMBER TEXT PRIMARY KEY,
                $COL_CARD_TYPE TEXT NOT NULL,
                $COL_ISSUER TEXT NOT NULL,
                $COL_BALANCE INTEGER NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_LOCATION_ALIASES (
                $COL_TERMINAL_ID TEXT PRIMARY KEY,
                $COL_ALIAS_NAME TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SAVED_CARDS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LOCATION_ALIASES")
        onCreate(db)
    }

    fun saveCard(card: CardBalanceResult) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_CARD_NUMBER, card.cardNumber)
            put(COL_CARD_TYPE, card.cardType)
            put(COL_ISSUER, card.issuer.name)
            put(COL_BALANCE, card.balance)
            put(COL_TIMESTAMP, card.timestamp)
        }
        db.insertWithOnConflict(TABLE_SAVED_CARDS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getSavedCards(): List<StoredCard> {
        val list = mutableListOf<StoredCard>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SAVED_CARDS,
            arrayOf(COL_CARD_NUMBER, COL_CARD_TYPE, COL_ISSUER, COL_BALANCE, COL_TIMESTAMP),
            null, null, null, null,
            "$COL_TIMESTAMP DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val num = it.getString(it.getColumnIndexOrThrow(COL_CARD_NUMBER))
                val type = it.getString(it.getColumnIndexOrThrow(COL_CARD_TYPE))
                val issuerStr = it.getString(it.getColumnIndexOrThrow(COL_ISSUER))
                val balance = it.getLong(it.getColumnIndexOrThrow(COL_BALANCE))
                val ts = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP))

                val issuer = try {
                    CardIssuer.valueOf(issuerStr)
                } catch (e: Exception) {
                    CardIssuer.UNKNOWN
                }
                list.add(StoredCard(num, type, issuer, balance, ts))
            }
        }
        return list
    }

    fun deleteCard(cardNumber: String) {
        writableDatabase.delete(TABLE_SAVED_CARDS, "$COL_CARD_NUMBER = ?", arrayOf(cardNumber))
    }

    fun clearAllSavedCards() {
        writableDatabase.delete(TABLE_SAVED_CARDS, null, null)
    }

    fun getLocationAliases(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val db = readableDatabase
        val cursor = db.query(TABLE_LOCATION_ALIASES, arrayOf(COL_TERMINAL_ID, COL_ALIAS_NAME), null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                val tid = it.getString(it.getColumnIndexOrThrow(COL_TERMINAL_ID))
                val name = it.getString(it.getColumnIndexOrThrow(COL_ALIAS_NAME))
                map[tid] = name
            }
        }
        return map
    }

    fun setLocationAlias(terminalId: String, name: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TERMINAL_ID, terminalId)
            put(COL_ALIAS_NAME, name)
        }
        db.insertWithOnConflict(TABLE_LOCATION_ALIASES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
