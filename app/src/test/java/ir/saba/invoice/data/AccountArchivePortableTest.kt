package ir.saba.invoice.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.security.AccountScope
import ir.saba.invoice.security.TaxpayerKeyVault
import ir.saba.invoice.data.identifiers.SuraIdentifier
import ir.saba.invoice.data.identifiers.SuraSavedIdentifierStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.KeyPairGenerator
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class AccountArchivePortableTest {
    @Test fun fiscalKeyIsReencryptedForTargetDeviceAndCreditJournalIsNotApplied()=runBlocking {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val account=AccountScope.of("11111111-1111-4111-8111-222222222222")
        val prefs=ctx.getSharedPreferences("sura_tax_credentials_${account.storageKey}",Context.MODE_PRIVATE)
        val sourceVault=TaxpayerKeyVault {SecretKeySpec(ByteArray(32){1},"AES")}
        val targetVault=TaxpayerKeyVault {SecretKeySpec(ByteArray(32){2},"AES")}
        val source=AccountArchivePortable(ctx,account,sourceVault)
        val target=AccountArchivePortable(ctx,account,targetVault)
        val key=KeyPairGenerator.getInstance("RSA").apply {initialize(2048)}.generateKeyPair().private.encoded
        val pem="-----BEGIN PRIVATE KEY-----\n"+Base64.getEncoder().encodeToString(key)+"\n-----END PRIVATE KEY-----"
        val db=Room.inMemoryDatabaseBuilder(ctx,SabaDatabase::class.java).build()
        try {
            prefs.edit().putString("pending_company_7",sourceVault.encryptPending(account,7,pem)).commit()
            val row=JSONObject().put("companyId",7).put("fiscalMemoryId","A11216")
                .put("logoUri","").put("encryptedPrivateKey",JSONObject.NULL)
            val assets=JSONObject()
            source.captureRow("moadian_details",row,assets)
            assertEquals(pem,assets.getJSONObject("keys").getJSONObject("7").getString("pending"))
            prefs.edit().remove("pending_company_7").commit()
            val id=db.companyDao().insert(Company(id=7,purchaserId=account.ownerId,name="Test",nationalId="123"))
            db.moadianDetailsDao().upsert(MoadianDetails(id,fiscalMemoryId="A11216",inactive=true))
            val identifiers=JSONArray().put(JSONObject().put("company",7).put("id","1234567890123")
                .put("description","test").put("vat","0").put("taxable","").put("kind","").put("expiry","").put("source","manual"))
            assets.put("identifiers",identifiers).put("profile",JSONObject())
                .put("journals",JSONObject().put("quota_wallets",JSONArray().put(JSONObject().put("available",80))))
            target.restoreAccount(assets,db,setOf(7L))
            val stored=prefs.getString("pending_company_7",null)!!
            assertArrayEquals(key,targetVault.decryptPending(account,7,stored))
            assertNull(db.quotaDao().wallet(account.ownerId))
            assertTrue(db.moadianDetailsDao().byCompany(7)!!.inactive)
            SuraSavedIdentifierStore(ctx,account).use {assertEquals("1234567890123",it.forTaxpayer(7).single().id)}
            assertTrue(target.history().isNotEmpty())
            target.restoreAccount(assets,db,setOf(7L))
            assertEquals(stored,prefs.getString("pending_company_7",null))
        } finally {
            prefs.edit().clear().commit();db.close()
            ctx.deleteDatabase("sura_identifier_saved_${account.storageKey}.db")
            File(ctx.filesDir,"archive-history/${account.storageKey}").deleteRecursively()
        }
    }
    @Test fun unreadableLogoRejectsInsteadOfDroppingData() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val account=AccountScope.of("11111111-1111-4111-8111-333333333333")
        val source=AccountArchivePortable(ctx,account)
        val row=JSONObject().put("companyId",7).put("fiscalMemoryId","A11216")
            .put("logoUri",File(ctx.filesDir,"missing.png").absolutePath).put("encryptedPrivateKey",JSONObject.NULL)
        try {source.captureRow("moadian_details",row,JSONObject());fail("missing logo")}
        catch(_:IllegalArgumentException){}
    }
}
