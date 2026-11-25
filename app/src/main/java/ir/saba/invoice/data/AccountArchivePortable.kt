package ir.saba.invoice.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import ir.saba.invoice.data.identifiers.SuraIdentifier
import ir.saba.invoice.data.identifiers.SuraSavedIdentifierStore
import ir.saba.invoice.security.AccountScope
import ir.saba.invoice.security.TaxpayerIdentity
import ir.saba.invoice.security.TaxpayerKeyVault
import ir.saba.invoice.security.TaxpayerPrivateKey
import ir.saba.invoice.security.TaxpayerSigningCertificate
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import kotlinx.coroutines.flow.first

/** Portable account data. Never export auth tokens, passwords or the biometric vault. */
internal class AccountArchivePortable(private val context:Context,private val account:AccountScope,
    private val vault:TaxpayerKeyVault=TaxpayerKeyVault()) {
    private val enc=Base64.getEncoder()
    private val dec=Base64.getDecoder()
    private val prefs=context.getSharedPreferences("sura_tax_credentials_${account.storageKey}",Context.MODE_PRIVATE)
    private val profile=context.getSharedPreferences("sura_owner_profile_local_v2",Context.MODE_PRIVATE)
    private val display=context.getSharedPreferences("display",Context.MODE_PRIVATE)
    private fun sha(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun bucket(a:JSONObject,k:String)=a.optJSONObject(k) ?: JSONObject().also { a.put(k,it) }
    private fun read(input:InputStream,max:Int):ByteArray {
        val out=java.io.ByteArrayOutputStream()
        val buf=ByteArray(8192)
        while(true) {
            val n=input.read(buf);if(n<0)break
            require(out.size()+n<=max) {"حجم فایل پیوست بیش از حد مجاز است."}
            out.write(buf,0,n)
        }
        return out.toByteArray().also { require(it.isNotEmpty()) {"فایل پیوست خالی است."} }
    }
    private fun putAsset(a:JSONObject,b:ByteArray):String {
        val hash=sha(b);val files=bucket(a,"files")
        if(!files.has(hash)) {
            val total=a.optLong("bytes")+b.size
            require(total<=20L*1024*1024) {"مجموع پیوست‌ها بیش از ۲۰ مگابایت است؛ پشتیبان ناقص ذخیره نمی‌شود."}
            a.put("bytes",total);files.put(hash,enc.encodeToString(b))
        }
        return hash
    }
    private fun getAsset(a:JSONObject,hash:String,max:Int):ByteArray {
        require(hash.matches(Regex("[0-9a-f]{64}")))
        val text=a.getJSONObject("files").getString(hash)
        require(text.length<=((max.toLong()+2)/3)*4)
        return dec.decode(text).also {require(it.size in 1..max && sha(it)==hash)}
    }
    private fun writeFile(folder:File,name:String,b:ByteArray):File {
        require(folder.mkdirs()||folder.isDirectory)
        val file=File(folder,name)
        if(file.exists()) {
            require(file.isFile && file.length()==b.size.toLong() && sha(file.readBytes())==sha(b))
            return file
        }
        val temp=File.createTempFile("sura-", ".partial",folder)
        try {
            temp.outputStream().use {it.write(b);it.fd.sync()}
            require(temp.renameTo(file)) {"ذخیره فایل بازیابی‌شده ناموفق بود."}
            return file
        } finally {temp.delete()}
    }
    private fun keyText(b:ByteArray):String=try {
        ("-----BEGIN PRIVATE KEY-----\n"+enc.encodeToString(b)+"\n-----END PRIVATE KEY-----").also {
            TaxpayerPrivateKey.parse(it)
        }
    } finally {b.fill(0)}

    fun captureRow(table:String,row:JSONObject,a:JSONObject) {
        if(table=="moadian_details") {
            val id=row.getLong("companyId")
            val fiscal=row.getString("fiscalMemoryId")
            val logo=row.getString("logoUri")
            if(logo.isNotBlank()) {
                val file=File(logo).canonicalFile
                require(file.parentFile==File(context.filesDir,"taxpayer-logos").canonicalFile && file.isFile) {"لوگو قابل خواندن نیست."}
                bucket(a,"logos").put(id.toString(),putAsset(a,file.inputStream().use {read(it,5*1024*1024)}))
            }
            val keys=JSONObject().put("fiscalMemoryId",fiscal)
            prefs.getString("pending_company_$id",null)?.let {
                keys.put("pending",keyText(vault.decryptPending(account,id,it)))
            }
            prefs.getString("company_$id",null)?.let {
                val record=JSONObject(it);require(record.getInt("version")==2)
                val bound=record.getString("fiscalMemoryId")
                keys.put("boundFiscal",bound).put("bound",keyText(vault.decrypt(TaxpayerIdentity(account,id,bound),record.getString("key"))))
                    .put("certificate",record.getString("certificate"))
            }
            if(!row.isNull("encryptedPrivateKey")) keys.put("legacy",keyText(vault.decrypt(id,row.getString("encryptedPrivateKey"))))
            if(keys.length()>1) bucket(a,"keys").put(id.toString(),keys)
        }
        if(table=="invoices") {
            val d=InvoiceLocalDetails.fromJson(row.getString("localDetailsJson"))
            val refs=JSONArray()
            d.documents.forEach { ref ->
                val b=requireNotNull(context.contentResolver.openInputStream(Uri.parse(ref.uri))) {"پیوست قابل خواندن نیست."}
                    .use {read(it,InvoiceDocumentRef.MAX_BYTES.toInt())}
                require(b.size.toLong()==ref.sizeBytes) {"اندازه پیوست تغییر کرده؛ ابتدا سند را بررسی کنید."}
                refs.put(JSONObject().put("hash",putAsset(a,b)).put("name",ref.name).put("mime",ref.mimeType).put("size",ref.sizeBytes))
            }
            if(refs.length()>0) bucket(a,"documents").put(row.getLong("id").toString(),refs)
            bucket(a,"invoiceStatus").put(row.getLong("id").toString(),row.getString("status"))
        }
        if(table=="official_submissions") bucket(a,"submissionEvidence").put(row.getString("requestId"),
            JSONObject().put("state",row.getString("state")).put("receipt",row.opt("signedReceipt")?:JSONObject.NULL)
                .put("message",row.opt("lastError")?:JSONObject.NULL))
    }
    suspend fun captureAccount(ids:Set<Long>,a:JSONObject,journals:JSONObject):JSONObject {
        val rows=JSONArray()
        SuraSavedIdentifierStore(context,account).use { store ->
            for(id in ids.sorted()) store.forTaxpayer(id).forEach { v ->
                rows.put(JSONObject().put("company",id).put("id",v.id).put("description",v.description)
                    .put("vat",v.vat).put("taxable",v.taxable).put("kind",v.kind).put("expiry",v.expirationDate).put("source",v.source))
            }
        }
        val local=JSONObject()
        for(k in listOf("name","mobile")) profile.getString("${k}_${account.storageKey}",null)?.let {local.put(k,it)}
        return a.put("identifiers",rows).put("journals",journals).put("profile",local)
            .put("fontScale",SabaPreferences(context).fontScale.first().toDouble())
            .put("selectedTaxpayer",display.getLong("active_taxpayer_${account.storageKey}",-1))
    }
    fun validate(a:JSONObject,ids:Set<Long>) {
        val keys=a.optJSONObject("keys")?:JSONObject()
        for(id in keys.keys()) {
            require(id.toLongOrNull() in ids)
            val row=keys.getJSONObject(id)
            for(k in listOf("pending","legacy","bound")) if(row.has(k)) TaxpayerPrivateKey.parse(row.getString(k))
            if(row.has("bound")) {
                val key=TaxpayerPrivateKey.parse(row.getString("bound"))
                val cert=TaxpayerSigningCertificate.parse(dec.decode(row.getString("certificate")))
                val challenge="SURA archive check".toByteArray()
                val proof=Signature.getInstance("SHA256withRSA").run {initSign(key);update(challenge);sign()}
                require(Signature.getInstance("SHA256withRSA").run {initVerify(cert.publicKey);update(challenge);verify(proof)})
                TaxpayerIdentity(account,id.toLong(),row.getString("boundFiscal"))
            }
        }
        val files=a.optJSONObject("files")?:JSONObject()
        var total=0L
        for(hash in files.keys()) {total+=getAsset(a,hash,InvoiceDocumentRef.MAX_BYTES.toInt()).size;require(total<=20L*1024*1024)}
        val logos=a.optJSONObject("logos")?:JSONObject()
        for(id in logos.keys()) {
            require(id.toLongOrNull() in ids)
            val b=getAsset(a,logos.getString(id),5*1024*1024)
            val opts=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true}
            android.graphics.BitmapFactory.decodeByteArray(b,0,b.size,opts)
            require(opts.outWidth in 1..8192 && opts.outHeight in 1..8192)
        }
        val documents=a.optJSONObject("documents")?:JSONObject()
        for(id in documents.keys()) {
            require((id.toLongOrNull()?:0)>0)
            val refs=documents.getJSONArray(id);require(refs.length()<=InvoiceDocumentRef.MAX_FILES)
            for(i in 0 until refs.length()) {
                val d=refs.getJSONObject(i)
                require(getAsset(a,d.getString("hash"),InvoiceDocumentRef.MAX_BYTES.toInt()).size.toLong()==d.getLong("size"))
                InvoiceDocumentRef("content://archive/validation",d.getString("name"),d.getString("mime"),d.getLong("size")).validated()
            }
        }
        val rows=a.getJSONArray("identifiers");require(rows.length()<=100_000)
        for(i in 0 until rows.length()) {
            val v=rows.getJSONObject(i)
            require(v.getLong("company") in ids && v.getString("id").matches(Regex("[0-9]{13,14}")))
            require(listOf("description","vat","taxable","kind","expiry","source").all {v.getString(it).length<=4096})
        }
        val profile=a.getJSONObject("profile")
        require(profile.keys().asSequence().all {it in setOf("name","mobile") && profile.getString(it).length<=256})
    }
    fun restoreRow(table:String,row:JSONObject,a:JSONObject) {
        if(table=="moadian_details") {
            val hash=a.optJSONObject("logos")?.optString(row.getLong("companyId").toString()).orEmpty()
            if(hash.isNotBlank()) row.put("logoUri",writeFile(File(context.filesDir,"taxpayer-logos"),
                "restored-${account.storageKey}-$hash.image",getAsset(a,hash,5*1024*1024)).absolutePath)
        }
        if(table=="invoices") {
            val d=InvoiceLocalDetails.fromJson(row.getString("localDetailsJson"))
            val refs=a.optJSONObject("documents")?.optJSONArray(row.getLong("id").toString())?:JSONArray()
            require(refs.length()==d.documents.size)
            val documents=(0 until refs.length()).map { i ->
                val ref=refs.getJSONObject(i);val hash=ref.getString("hash")
                val file=writeFile(File(context.filesDir,"archive-assets/${account.storageKey}"),hash,getAsset(a,hash,InvoiceDocumentRef.MAX_BYTES.toInt()))
                val uri=FileProvider.getUriForFile(context,context.packageName+".archivefiles",file)
                InvoiceDocumentRef(uri.toString(),ref.getString("name"),ref.getString("mime"),ref.getLong("size")).validated()
            }
            row.put("localDetailsJson",d.copy(documents=documents).toJson())
        }
    }
    /** Existing data wins. No stale balance, claim or send state is replayed. */
    suspend fun restoreAccount(a:JSONObject,db:SabaDatabase,ids:Set<Long>) {
        validate(a,ids)
        val keys=a.optJSONObject("keys")?:JSONObject()
        for(id in keys.keys()) {
            val n=id.toLong();require(db.companyDao().byId(n)?.purchaserId==account.ownerId)
            val detail=requireNotNull(db.moadianDetailsDao().byCompany(n))
            val row=keys.getJSONObject(id)
            if(detail.fiscalMemoryId!=row.getString("fiscalMemoryId") || prefs.contains("company_$id") || prefs.contains("pending_company_$id")) continue
            val edit=prefs.edit()
            if(row.has("bound")) {
                val identity=TaxpayerIdentity(account,n,row.getString("boundFiscal"))
                val cert=TaxpayerSigningCertificate.parse(dec.decode(row.getString("certificate")))
                edit.putString("company_$id",JSONObject().put("version",2).put("fiscalMemoryId",identity.fiscalMemoryId)
                    .put("key",vault.encrypt(identity,row.getString("bound"))).put("certificate",enc.encodeToString(cert.encoded)).toString())
            }
            if(row.has("pending")||row.has("legacy")) edit.putString("pending_company_$id",vault.encryptPending(account,n,
                row.getString(if(row.has("pending"))"pending" else "legacy")))
            require(edit.commit()) {"ذخیره کلید بازیابی‌شده ناموفق بود؛ همان فایل را دوباره وارد کنید."}
        }
        val rows=a.getJSONArray("identifiers")
        SuraSavedIdentifierStore(context,account).use { store ->
            for((id,items) in (0 until rows.length()).map {rows.getJSONObject(it)}.groupBy {it.getLong("company")}) {
                require(db.companyDao().byId(id)?.purchaserId==account.ownerId)
                items.map {v->SuraIdentifier(v.getString("id"),v.getString("description"),v.getString("vat"),
                    v.getString("taxable"),v.getString("kind"),v.getString("expiry"),v.getString("source"))}
                    .chunked(1000).forEach {store.importBatch(id,it)}
            }
        }
        val local=a.getJSONObject("profile");val edit=profile.edit()
        for(k in listOf("name","mobile")) {
            val key="${k}_${account.storageKey}"
            if(!profile.contains(key)&&local.has(k))edit.putString(key,local.getString(k))
        }
        require(edit.commit())
        val audit=JSONObject().put("journals",a.getJSONObject("journals"))
            .put("invoiceStatus",a.optJSONObject("invoiceStatus")?:JSONObject())
            .put("submissionEvidence",a.optJSONObject("submissionEvidence")?:JSONObject())
            .put("fontScale",a.optDouble("fontScale",1.0)).put("selectedTaxpayer",a.optLong("selectedTaxpayer",-1))
        val b=audit.toString().toByteArray(Charsets.UTF_8)
        writeFile(File(context.filesDir,"archive-history/${account.storageKey}"),sha(b)+".json",b)
    }
    fun history():List<File> = File(context.filesDir,"archive-history/${account.storageKey}")
        .listFiles { f -> f.isFile && f.name.matches(Regex("[0-9a-f]{64}\\.json")) }
        ?.sortedByDescending {it.lastModified()}.orEmpty()
}
