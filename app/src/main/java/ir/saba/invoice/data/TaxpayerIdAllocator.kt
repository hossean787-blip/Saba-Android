package ir.saba.invoice.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Device-wide IDs preserve the existing server owner/local-taxpayer mapping across account databases. */
class TaxpayerIdAllocator(context: Context) : SQLiteOpenHelper(context.applicationContext, "sura_local_taxpayer_ids.db", null, 1), java.io.Closeable {
    private val appContext = context.applicationContext
    override fun onCreate(db: SQLiteDatabase) {
        val names = appContext.databaseList().filter {
            it == "saba_local.db" || it.matches(Regex("sura_account_[0-9a-f]{64}\\.db"))
        }
        val legacyMax = names.maxOfOrNull { name ->
            SQLiteDatabase.openDatabase(appContext.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { source ->
                source.rawQuery("SELECT COALESCE(MAX(id),0) FROM companies", null).use { cursor ->
                    check(cursor.moveToFirst()); cursor.getLong(0)
                }
            }
        } ?: 0L
        require(legacyMax in 0 until MAX_ID)
        db.execSQL("CREATE TABLE taxpayer_sequence (id INTEGER PRIMARY KEY CHECK(id=1), next_id INTEGER NOT NULL)")
        db.execSQL("INSERT INTO taxpayer_sequence(id,next_id) VALUES(1,?)", arrayOf(legacyMax + 1))
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unsupported ID registry version")
    fun reserve(): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val next = db.rawQuery("SELECT next_id FROM taxpayer_sequence WHERE id=1",null).use {
                check(it.moveToFirst()); it.getLong(0)
            }
            require(next in 1..MAX_ID) { "ظرفیت شناسه مؤدی محلی تمام شده است." }
            db.execSQL("UPDATE taxpayer_sequence SET next_id=? WHERE id=1",arrayOf(next+1))
            db.setTransactionSuccessful()
            return next
        } finally { db.endTransaction() }
    }
    companion object { private const val MAX_ID = 9_007_199_254_740_991L }
}
