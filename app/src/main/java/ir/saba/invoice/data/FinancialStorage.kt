package ir.saba.invoice.data

import android.content.Context
import android.database.Cursor
import android.system.Os
import android.system.OsConstants
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import ir.saba.invoice.BuildConfig
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest

/** One production path for all private financial SQLite files. The public catalog is separate.
 * No destructive Room migration, corruption-handler deletion, empty-key fallback or downgrade. */
internal object FinancialStorage {
    private val namePattern=Regex("(sura_account_[0-9a-f]{64}|sura_identifier_saved_[0-9a-f]{64}|saba_local|sura_local_taxpayer_ids)\\.db")
    private val plainHeader="SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private val preserve=DatabaseErrorHandler { _, error -> throw error }
    private val lock=Any()
    private fun fixture()=BuildConfig.DEBUG&&BuildConfig.SURA_PLAINTEXT_JVM_FIXTURES
    private fun valid(name:String){require(namePattern.matches(name))}
    private fun isPlain(file:File):Boolean {
        require(file.isFile&&file.length()>=16) {"فایل بانک اطلاعات ناقص است."}
        val bytes=ByteArray(16)
        java.io.DataInputStream(file.inputStream()).use {it.readFully(bytes)}
        return bytes.contentEquals(plainHeader)
    }
    internal fun syncDirectory(dir:File) {
        val fd=Os.open(dir.absolutePath,OsConstants.O_RDONLY or OsConstants.O_DIRECTORY,0)
        try {Os.fsync(fd)} finally {Os.close(fd)}
    }
    private fun syncFile(file:File)=RandomAccessFile(file,"rw").use {it.fd.sync()}
    private fun scalar(db:SupportSQLiteDatabase,query:String):Long=db.query(query).use {check(it.moveToFirst());it.getLong(0)}
    private fun checkIntegrity(db:SupportSQLiteDatabase) {
        db.query("PRAGMA integrity_check").use {check(it.moveToFirst()&&it.getString(0)=="ok"&&!it.moveToNext())}
        db.query("PRAGMA foreign_key_check").use {check(!it.moveToFirst())}
    }
    private fun quote(s:String)="\"" + s.replace("\"","\"\"") + "\""
    private fun digest(db:SupportSQLiteDatabase):ByteArray {
        val md=MessageDigest.getInstance("SHA-256")
        fun number(n:Long){md.update(ByteBuffer.allocate(8).putLong(n).array())}
        fun bytes(b:ByteArray){number(b.size.toLong());md.update(b)}
        fun rows(sql:String) {
            db.query(sql).use { c ->
                number(c.columnCount.toLong())
                c.columnNames.forEach {bytes(it.toByteArray(Charsets.UTF_8))}
                var count=0L
                while(c.moveToNext()) {
                    count++
                    md.update(0x7f.toByte())
                    for(i in 0 until c.columnCount) {
                        val type=c.getType(i);md.update(type.toByte())
                        when(type) {
                            Cursor.FIELD_TYPE_NULL -> Unit
                            Cursor.FIELD_TYPE_INTEGER -> number(c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> number(java.lang.Double.doubleToLongBits(c.getDouble(i)))
                            Cursor.FIELD_TYPE_STRING -> bytes(c.getString(i).toByteArray(Charsets.UTF_8))
                            Cursor.FIELD_TYPE_BLOB -> bytes(c.getBlob(i))
                            else -> error("Unknown SQLite storage class")
                        }
                    }
                }
                number(count)
            }
        }
        number(scalar(db,"PRAGMA user_version"));number(scalar(db,"PRAGMA application_id"))
        number(scalar(db,"PRAGMA auto_vacuum"))
        rows("SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name")
        val tables=db.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use {c ->
            buildList {while(c.moveToNext())add(c.getString(0))}
        }
        for(table in tables) {
            val q=quote(table);bytes(table.toByteArray(Charsets.UTF_8))
            val names=db.query("SELECT * FROM "+q+" LIMIT 0").use {it.columnNames.toList()}
            check(names.isNotEmpty())
            rows("SELECT * FROM "+q+" ORDER BY "+names.joinToString(","){quote(it)+" COLLATE BINARY"})
        }
        return md.digest()
    }
    private fun open(file:File,password:ByteArray,flags:Int)=SQLiteDatabase.openDatabase(
        file.absolutePath,password,null,flags,preserve,null)
    private fun finishJournals(file:File) {
        for(suffix in listOf("-wal","-shm","-journal")) {
            val side=File(file.path+suffix)
            check(!side.exists()||side.length()==0L) {"فایل جانبی بانک هنوز فعال است."}
            if(side.exists())check(side.delete())
        }
    }
    private fun stageFile(file:File)=File(file.path+".sura-cipher-stage")
    private fun clearStage(stage:File) {
        // Only generated staging files are removed; never a live database or its sidecars.
        for(suffix in listOf("","-wal","-shm","-journal")) {
            val f=File(stage.path+suffix);if(f.exists())check(f.delete())
        }
    }
    private fun convert(file:File,key:ByteArray,beforeReplace:()->Unit) {
        val dir=requireNotNull(file.parentFile)
        val stage=stageFile(file)
        clearStage(stage)
        check(dir.usableSpace>file.length()*2+8L*1024*1024) {"فضای آزاد برای مهاجرت امن کافی نیست."}
        try {
            var expected:ByteArray?=null
            open(file,ByteArray(0),SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY).use {source ->
                source.query("PRAGMA wal_checkpoint(TRUNCATE)").use {check(it.moveToFirst()&&it.getInt(0)==0)}
                source.query("PRAGMA journal_mode=DELETE").use {check(it.moveToFirst()&&it.getString(0).equals("delete",true))}
                source.execSQL("PRAGMA synchronous=FULL")
                source.execSQL("PRAGMA temp_store=MEMORY")
                checkIntegrity(source)
                val version=scalar(source,"PRAGMA user_version");check(version>0)
                val appId=scalar(source,"PRAGMA application_id")
                val vacuum=scalar(source,"PRAGMA auto_vacuum")
                expected=digest(source)
                source.execSQL("ATTACH DATABASE ? AS sura_cipher KEY ?",arrayOf(stage.absolutePath,String(key,Charsets.US_ASCII)))
                try {
                    source.execSQL("PRAGMA sura_cipher.auto_vacuum="+vacuum)
                    source.execSQL("PRAGMA sura_cipher.synchronous=FULL")
                    source.query("SELECT sqlcipher_export('sura_cipher')").use {check(it.moveToFirst())}
                    source.execSQL("PRAGMA sura_cipher.user_version="+version)
                    source.execSQL("PRAGMA sura_cipher.application_id="+appId)
                } finally {source.execSQL("DETACH DATABASE sura_cipher")}
            }
            finishJournals(file)
            check(stage.isFile&&!isPlain(stage))
            open(stage,key,SQLiteDatabase.OPEN_READWRITE).use {target ->
                target.query("PRAGMA journal_mode=DELETE").use {check(it.moveToFirst()&&it.getString(0).equals("delete",true))}
                target.execSQL("PRAGMA synchronous=FULL");target.execSQL("PRAGMA temp_store=MEMORY")
                checkIntegrity(target)
                check(MessageDigest.isEqual(requireNotNull(expected),digest(target))) {"اطلاعات یا ساختار بانک مقصد مطابق مبدأ نیست."}
            }
            finishJournals(stage)
            syncFile(stage);syncDirectory(dir)
            beforeReplace() // injectable only for synthetic interruption tests
            // Single same-directory atomic replacement. No interval with a missing original.
            Os.rename(stage.absolutePath,file.absolutePath)
            syncDirectory(dir)
            check(!isPlain(file))
        } finally {
            // On pre-replace failure the untouched source remains authoritative.
            // After replacement no stale source is retained or reopened.
            clearStage(stage)
        }
    }
    private fun prepare(context:Context,name:String,beforeReplace:()->Unit={}):ByteArray=synchronized(lock) {
        valid(name);System.loadLibrary("sqlcipher")
        val file=context.getDatabasePath(name)
        val vault=FinancialDatabaseKey(context,name)
        val hasFile=file.exists()
        val plaintext=hasFile&&isPlain(file)
        check(hasFile||(!vault.expectsDatabase()&&!stageFile(file).exists()&&listOf("-wal","-shm","-journal").none {File(file.path+it).exists()})) {"بانک اطلاعات حذف یا مفقود شده؛ بازیابی لازم است."}
        check(!plaintext||!vault.isEstablished()) {"بازگشت به بانک بدون رمزگذاری مجاز نیست."}
        val key=vault.openOrCreate(hasFile&&!plaintext)
        try {
            if(plaintext){vault.markMigrationStarted();convert(file,key,beforeReplace)}
            if(hasFile) {
                open(file,key,SQLiteDatabase.OPEN_READONLY).use {db ->
                    db.query("SELECT count(*) FROM sqlite_master").use {check(it.moveToFirst())}
                }
                vault.markEstablished()
            }
            key
        } catch(e:Throwable) {key.fill(0);throw e}
    }
    fun prepareExisting(context:Context) {
        if(fixture())return
        for(name in context.databaseList().filter {namePattern.matches(it)}.sorted())prepare(context,name).fill(0)
    }
    /** Narrow internal test seam: production calls never supply an interruption callback. */
    internal fun migrateForDeviceTest(context:Context,name:String,beforeReplace:()->Unit) {
        prepare(context,name,beforeReplace).fill(0)
    }
    fun factory():SupportSQLiteOpenHelper.Factory {
        if(fixture())return FrameworkSQLiteOpenHelperFactory()
        return SupportSQLiteOpenHelper.Factory { config ->
            val name=requireNotNull(config.name);valid(name)
            val context=config.context.applicationContext
            val key=prepare(context,name)
            val vault=FinancialDatabaseKey(context,name)
            // SQLCipher's default corruption handler can remove a damaged DB. Use an explicit
            // preserve-only handler and minimum version 0 (no destructive recreation).
            object:SQLiteOpenHelper(context,name,key,null,config.callback.version,0,preserve,null,false) {
                override fun onConfigure(db:SQLiteDatabase) {
                    db.execSQL("PRAGMA temp_store=MEMORY")
                    config.callback.onConfigure(db)
                }
                override fun onCreate(db:SQLiteDatabase)=config.callback.onCreate(db)
                override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int)=config.callback.onUpgrade(db,oldVersion,newVersion)
                override fun onDowngrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int)=config.callback.onDowngrade(db,oldVersion,newVersion)
                override fun onOpen(db:SQLiteDatabase) {
                    config.callback.onOpen(db)
                    vault.markEstablished()
                }
            }
        }
    }
    fun maxCompanyId(context:Context,name:String):Long {
        valid(name)
        if(fixture())return android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use {db ->
            db.rawQuery("SELECT COALESCE(MAX(id),0) FROM companies",null).use {check(it.moveToFirst());it.getLong(0)}
        }
        val key=prepare(context,name)
        return try {open(context.getDatabasePath(name),key,SQLiteDatabase.OPEN_READONLY).use {
            scalar(it,"SELECT COALESCE(MAX(id),0) FROM companies")
        }} finally {key.fill(0)}
    }
}
