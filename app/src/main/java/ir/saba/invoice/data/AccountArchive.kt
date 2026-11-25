package ir.saba.invoice.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import ir.saba.invoice.security.AccountScope
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Deliberately scoped logical archive; never a copy of login keys or live balances. */
internal object AccountArchiveCrypto {
    const val LIMIT = 16 * 1024 * 1024
    private val magic = "SURA-BACKUP-1".toByteArray(Charsets.US_ASCII)
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private fun key(password: CharArray, salt: ByteArray): ByteArray {
        require(password.size in 12..128) { "رمز پشتیبان باید ۱۲ تا ۱۲۸ نویسه باشد." }
        // PBKDF2-HMAC-SHA1 is available on Android API 23; high work factor,
        // random salt and AES-256-GCM. Format version fixes the algorithm.
        val spec = PBEKeySpec(password,salt,1_300_000,256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    fun seal(plain: ByteArray, password: CharArray): ByteArray {
        require(plain.size in 1..LIMIT) { "حجم داده از سقف ۱۶ مگابایت بیشتر است." }
        val random=SecureRandom()
        val salt=ByteArray(SALT_SIZE).also(random::nextBytes)
        val iv=ByteArray(IV_SIZE).also(random::nextBytes)
        val header=magic+salt+iv
        val k=key(password,salt)
        return try {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(k,"AES"),GCMParameterSpec(128,iv))
            cipher.updateAAD(header)
            header+cipher.doFinal(plain)
        } finally { k.fill(0) }
    }
    fun open(blob: ByteArray,password: CharArray): ByteArray {
        val n=magic.size+SALT_SIZE+IV_SIZE
        require(blob.size in (n+17)..(LIMIT+n+16) &&
            blob.copyOfRange(0,magic.size).contentEquals(magic)) { "فرمت یا حجم فایل پشتیبان معتبر نیست." }
        val salt=blob.copyOfRange(magic.size,magic.size+SALT_SIZE)
        val iv=blob.copyOfRange(magic.size+SALT_SIZE,n)
        val k=key(password,salt)
        return try {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(k,"AES"),GCMParameterSpec(128,iv))
            cipher.updateAAD(blob.copyOfRange(0,n))
            cipher.doFinal(blob.copyOfRange(n,blob.size))
        } finally { k.fill(0) }
    }
    fun read(input: InputStream): ByteArray {
        val output=ByteArrayOutputStream()
        val chunk=ByteArray(8192)
        while(true) {
            val n=input.read(chunk)
            if(n<0) break
            require(output.size()+n <= LIMIT+128) { "فایل بیش از اندازه مجاز است." }
            output.write(chunk,0,n)
        }
        return output.toByteArray()
    }
}

internal data class ArchiveCounts(val taxpayers:Int,val customers:Int,val invoices:Int)
internal data class ArchiveCreditSnapshot(val owner:Long,val gift:Long,val taxpayers:Map<Long,Long>,val checkedAt:Long) {
    init {
        require(owner in 0..9_007_199_254_740_991L && gift in 0..9_007_199_254_740_991L && checkedAt>0)
        require(taxpayers.all { (id,balance)->id in 1..9_007_199_254_740_991L && balance in 0..9_007_199_254_740_991L })
    }
    fun toJson()=JSONObject().put("owner",owner).put("gift",gift).put("checkedAt",checkedAt)
        .put("taxpayers",JSONObject().apply { taxpayers.forEach { (id,balance)->put(id.toString(),balance) } })
}
internal data class ArchiveRestoreResult(val added:ArchiveCounts,val credit:ArchiveCreditSnapshot)


/** Non-destructive merge for the same account. Existing rows and live credit always win.
 * Newly recovered invoice rows are historical; imported taxpayers require fiscal reconciliation.
 * Credentials, request claims, local balance caches and attachment binaries are excluded. */
internal class AccountArchive(
    private val db:SabaDatabase, private val account:AccountScope,
    private val checkSession:()->Unit,
    private val reserveTaxpayerIds:(Long)->Unit
) {
    private val tables=listOf("companies","moadian_details","customers","customer_profiles",
        "invoices","invoice_lines","official_submissions")
    private val ownerTables=mapOf("companies" to "purchaserId","official_submissions" to "ownerId")
    private fun columns(sql:SupportSQLiteDatabase,table:String):List<String> =
        sql.query("SELECT * FROM `$table` LIMIT 0").use { it.columnNames.toList() }
    private fun sanitize(table:String,row:JSONObject,restore:Boolean) {
        ownerTables[table]?.let { require(row.getString(it)==account.ownerId) { "فایل متعلق به این حساب نیست." } }
        if(table=="moadian_details") {
            row.put("encryptedPrivateKey",JSONObject.NULL)
            row.put("keyCheckStatus","UNCHECKED")
            row.put("logoUri","")
            row.put("certificateExpiry","")
            // A stale fiscal sequence must be reconciled before new issuance.
            if(restore) row.put("inactive",1)
        }
        if(table=="invoices" && restore) {
            row.put("status","RESTORED")
            val details=InvoiceLocalDetails.fromJson(row.getString("localDetailsJson"))
            row.put("localDetailsJson",details.copy(documents=emptyList()).toJson())
        }
        if(table=="official_submissions" && restore) {
            val historicalState=row.getString("state")
            row.put("state","RESTORED")
            row.put("lastError","سابقه پشتیبان؛ وضعیت هنگام ذخیره: "+historicalState.take(30))
            // Never import an assertion as currently verified reference evidence.
            row.put("signedReceipt",JSONObject.NULL)
        }
    }
    suspend fun export(password:CharArray,credit:ArchiveCreditSnapshot?=null):ByteArray {
        checkSession()
        val plain=db.withTransaction {
            checkSession()
            val sql=db.openHelper.writableDatabase
            val data=JSONObject()
            var count=0
            var estimated=0L
            for(table in tables) {
                val rows=JSONArray()
                sql.query("SELECT * FROM `$table`").use { c ->
                    while(c.moveToNext()) {
                        require(++count<=100_000) { "تعداد ردیف‌های پشتیبان بیش از حد مجاز است." }
                        val row=JSONObject()
                        for(i in 0 until c.columnCount) {
                            val name=c.getColumnName(i)
                            val value:Any=when(c.getType(i)) {
                                Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                                Cursor.FIELD_TYPE_STRING -> c.getString(i)
                                else -> error("نوع داده پشتیبان پشتیبانی نمی‌شود.")
                            }
                            row.put(name,value)
                            estimated+=name.length.toLong()*3+value.toString().length.toLong()*6+16
                            require(estimated<=AccountArchiveCrypto.LIMIT.toLong()*2) { "حجم پشتیبان از سقف مجاز بیشتر است." }
                        }
                        sanitize(table,row,false)
                        rows.put(row)
                    }
                }
                data.put(table,rows)
            }
            val ids=data.getJSONArray("companies").let { a -> (0 until a.length()).map { a.getJSONObject(it).getLong("id") }.toSet() }
            if(credit!=null) require(credit.taxpayers.keys==ids) { "فهرست مؤدیان تغییر کرده است؛ دوباره پشتیبان بگیرید." }
            JSONObject().put("format",2).put("schema",sql.version)
                .put("owner",account.ownerId).put("createdAt",System.currentTimeMillis())
                .put("creditSnapshot",credit?.toJson() ?: JSONObject.NULL)
                .put("tables",data).toString().toByteArray(Charsets.UTF_8)
        }
        return try { AccountArchiveCrypto.seal(plain,password) } finally { plain.fill(0) }
    }
    private fun decode(blob:ByteArray,password:CharArray):JSONObject {
        checkSession()
        val plain=AccountArchiveCrypto.open(blob,password)
        val root=try { JSONObject(String(plain,Charsets.UTF_8)) } finally { plain.fill(0) }
        require(root.getInt("format") in 1..2 && root.getString("owner")==account.ownerId) { "فایل یا مالک پشتیبان معتبر نیست." }
        val data=root.getJSONObject("tables")
        require(data.keys().asSequence().toSet()==tables.toSet()) { "ساختار فایل پشتیبان معتبر نیست." }
        require(tables.sumOf { data.getJSONArray(it).length() }<=100_000) { "تعداد ردیف‌ها بیش از سقف مجاز است." }
        return root
    }
    private fun archivedIds(data:JSONObject):Set<Long> {
        val companies=data.getJSONArray("companies")
        val ids=mutableSetOf<Long>()
        for(i in 0 until companies.length()) {
            val c=companies.getJSONObject(i)
            require(c.getString("purchaserId")==account.ownerId && c.getLong("id") in 1..9_007_199_254_740_991L)
            require(ids.add(c.getLong("id")))
        }
        return ids
    }
    /** Credits are fetched from an authenticated, read-only server operation.
     * The snapshot inside the file is NEVER used to authorize spending or update balances. */
    suspend fun restoreWithCredit(blob:ByteArray,password:CharArray,
        readCredit:suspend(Set<Long>)->ArchiveCreditSnapshot):ArchiveRestoreResult {
        val root=decode(blob,password)
        val ids=archivedIds(root.getJSONObject("tables"))
        val local=db.companyDao().all().also { require(it.all { c->c.purchaserId==account.ownerId }) }.map { it.id }.toSet()
        val credit=readCredit(ids+local)
        require(credit.taxpayers.keys==ids+local) { "استعلام تمام مؤدیان از سرور کامل نشد." }
        checkSession()
        return ArchiveRestoreResult(merge(root),credit)
    }
    // Compatibility entry for isolated archive unit tests; production restore always uses restoreWithCredit.
    internal suspend fun restore(blob:ByteArray,password:CharArray):ArchiveCounts=merge(decode(blob,password))
    private fun existing(sql:SupportSQLiteDatabase,table:String,key:String,id:Any):JSONObject? {
        return sql.query("SELECT * FROM `$table` WHERE `$key`=?",arrayOf(id)).use { c ->
            if(!c.moveToFirst()) null else JSONObject().apply {
                for(i in 0 until c.columnCount) put(c.getColumnName(i),when(c.getType(i)) {
                    Cursor.FIELD_TYPE_NULL->JSONObject.NULL
                    Cursor.FIELD_TYPE_INTEGER->c.getLong(i)
                    Cursor.FIELD_TYPE_STRING->c.getString(i)
                    else->error("Unsupported row")
                })
            }
        }
    }
    private fun same(a:JSONObject,b:JSONObject,vararg keys:String) =
        keys.all { a.opt(it)?.toString()==b.opt(it)?.toString() }
    private suspend fun merge(root:JSONObject):ArchiveCounts {
        val data=root.getJSONObject("tables")
        val ids=archivedIds(data)
        return db.withTransaction {
            checkSession()
            val sql=db.openHelper.writableDatabase
            require(root.getInt("schema")==sql.version) { "نسخه پایگاه داده فایل با برنامه یکسان نیست." }
            val customerOwners=mutableMapOf<Long,Long>()
            val invoiceOwners=mutableMapOf<Long,Long>()
            val existingInvoices=mutableSetOf<Long>()
            val newCompanies=mutableSetOf<Long>()
            val primary=mapOf("companies" to "id","moadian_details" to "companyId","customers" to "id",
                "customer_profiles" to "customerId","invoices" to "id","invoice_lines" to "id","official_submissions" to "requestId")
            var companiesAdded=0;var customersAdded=0;var invoicesAdded=0
            if(ids.isNotEmpty()) reserveTaxpayerIds(ids.maxOrNull()!!)
            for(table in tables) {
                val rows=data.getJSONArray(table)
                val cols=columns(sql,table)
                val seen=mutableSetOf<String>()
                for(i in 0 until rows.length()) {
                    val row=rows.getJSONObject(i)
                    require(row.keys().asSequence().toSet()==cols.toSet()) { "ستون‌های فایل پشتیبان معتبر نیست." }
                    val key=primary.getValue(table)
                    val id=row.get(key)
                    require(id is String || (id is Number && id.toLong()>0))
                    require(seen.add(id.toString())) { "شناسه تکراری در فایل پشتیبان." }
                    if(row.has("companyId")) require(row.getLong("companyId") in ids)
                    when(table) {
                        "customers" -> require(customerOwners.put(row.getLong("id"),row.getLong("companyId"))==null)
                        "customer_profiles" -> require(customerOwners[row.getLong("customerId")]==row.getLong("companyId"))
                        "invoices" -> {
                            require(customerOwners[row.getLong("customerId")]==row.getLong("companyId"))
                            require(invoiceOwners.put(row.getLong("id"),row.getLong("companyId"))==null)
                        }
                        "invoice_lines" -> require(invoiceOwners.containsKey(row.getLong("invoiceId")))
                        "official_submissions" -> require(invoiceOwners[row.getLong("invoiceId")]==row.getLong("companyId"))
                    }
                    ownerTables[table]?.let { require(row.getString(it)==account.ownerId) }
                    val old=existing(sql,table,key,id)
                    if(old!=null) {
                        val compatible=when(table) {
                            "companies" -> same(old,row,"purchaserId","nationalId")
                            "customers" -> same(old,row,"companyId") &&
                                (old.optString("nationalId").isBlank() || row.optString("nationalId").isBlank() || same(old,row,"nationalId"))
                            "customer_profiles" -> same(old,row,"companyId")
                            "invoices" -> same(old,row,"companyId","customerId","invoiceNumber")
                            "invoice_lines" -> row.getLong("invoiceId") in existingInvoices && same(old,row,"invoiceId")
                            "official_submissions" -> same(old,row,"ownerId","companyId","invoiceId","fiscalId","taxId","payloadHash")
                            else -> true
                        }
                        require(compatible) { "تداخل شناسه با اطلاعات فعلی؛ هیچ داده‌ای جایگزین نشد." }
                        if(table=="invoices") existingInvoices.add(row.getLong("id"))
                        // Existing records (including deleted/changed lines, send state, keys and serials)
                        // always win; archive cannot roll them back.
                        continue
                    }
                    if(table=="invoice_lines" && row.getLong("invoiceId") in existingInvoices) continue
                    if(table=="official_submissions" && row.getLong("invoiceId") in existingInvoices) {
                        val invoiceId=row.getLong("invoiceId")
                        val active=db.officialSubmissionDao().forInvoice(invoiceId)
                        require(active==null) { "درخواست ارسال دیگری برای این شماره ثبت شده است؛ بازیابی برای جلوگیری از تداخل متوقف شد." }
                        // Do not allow a local draft with missing submission history to be posted a second time.
                        sql.execSQL("UPDATE invoices SET status='RESTORED' WHERE id=?",arrayOf(invoiceId))
                    }
                    sanitize(table,row,true)
                    val values=ContentValues()
                    for(col in cols) when(val v=row.get(col)) {
                        JSONObject.NULL -> values.putNull(col)
                        is String -> values.put(col,v)
                        is Int -> values.put(col,v)
                        is Long -> values.put(col,v)
                        else -> error("نوع ستون در فایل معتبر نیست.")
                    }
                    check(sql.insert(table,SQLiteDatabase.CONFLICT_ABORT,values)!=-1L)
                    when(table) {
                        "companies"->{companiesAdded++;newCompanies.add(row.getLong("id"))}
                        "customers"->customersAdded++
                        "invoices"->invoicesAdded++
                    }
                }
            }
            for(id in newCompanies) {
                if(db.moadianDetailsDao().byCompany(id)==null)
                    db.moadianDetailsDao().upsert(MoadianDetails(companyId=id,inactive=true))
            }
            sql.query("PRAGMA foreign_key_check").use { check(!it.moveToFirst()) }
            checkSession()
            // No writes to any quota, wallet, credit claim, or server endpoint.
            ArchiveCounts(companiesAdded,customersAdded,invoicesAdded)
        }
    }

}
