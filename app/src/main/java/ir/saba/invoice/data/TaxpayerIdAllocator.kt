package ir.saba.invoice.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper

/** Device-wide IDs preserve the existing server owner/local-taxpayer mapping across account databases. */
class TaxpayerIdAllocator(context: Context) : java.io.Closeable {
    private val appContext=context.applicationContext
    private val helper=FinancialStorage.factory().create(SupportSQLiteOpenHelper.Configuration.builder(appContext)
        .name("sura_local_taxpayer_ids.db").callback(object:SupportSQLiteOpenHelper.Callback(1) {
        override fun onCreate(db:SupportSQLiteDatabase) {
        val names = appContext.databaseList().filter {
            it == "saba_local.db" || it.matches(Regex("sura_account_[0-9a-f]{64}\\.db"))
        }
        val legacyMax = names.maxOfOrNull { name ->
            FinancialStorage.maxCompanyId(appContext,name)
        } ?: 0L
        require(legacyMax in 0 until MAX_ID)
        db.execSQL("CREATE TABLE taxpayer_sequence (id INTEGER PRIMARY KEY CHECK(id=1), next_id INTEGER NOT NULL)")
        db.execSQL("INSERT INTO taxpayer_sequence(id,next_id) VALUES(1,?)", arrayOf(legacyMax + 1))
    }
        override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unsupported ID registry version")
        }).build())
    private val writableDatabase get()=helper.writableDatabase
    override fun close()=helper.close()
    fun reserve(): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val next = db.query("SELECT next_id FROM taxpayer_sequence WHERE id=1").use {
                check(it.moveToFirst()); it.getLong(0)
            }
            require(next in 1..MAX_ID) { "ظرفیت شناسه مؤدی محلی تمام شده است." }
            db.execSQL("UPDATE taxpayer_sequence SET next_id=? WHERE id=1",arrayOf(next+1))
            db.setTransactionSuccessful()
            return next
        } finally { db.endTransaction() }
    }
    /** Reserve the imported high-water mark before restoring explicit IDs. */
    fun advancePast(id: Long) {
        require(id in 0..MAX_ID)
        writableDatabase.execSQL("UPDATE taxpayer_sequence SET next_id=MAX(next_id,?) WHERE id=1", arrayOf(id+1))
    }
    companion object { private const val MAX_ID = 9_007_199_254_740_991L }
}
