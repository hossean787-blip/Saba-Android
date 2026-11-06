package ir.saba.invoice.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.data.identifiers.SuraIdentifier
import ir.saba.invoice.data.identifiers.SuraIdentifierStore
import ir.saba.invoice.data.identifiers.SuraSavedIdentifierStore
import ir.saba.invoice.security.AccountScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SuraIdentifierStoreTest {

    @Test fun bookmarksAreIsolatedByOwnerAndTaxpayerAndPersistAcrossReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val accountA = AccountScope.of("88888888-8888-4888-8888-888888888888")
        val accountB = AccountScope.of("99999999-9999-4999-8999-999999999999")
        val fileA = "sura_identifier_saved_" + accountA.storageKey + ".db"
        val fileB = "sura_identifier_saved_" + accountB.storageKey + ".db"
        context.deleteDatabase(fileA)
        context.deleteDatabase(fileB)
        val sample = SuraIdentifier("1234567890123", "خدمت آزمایشی", "10", "مشمول",
            "شناسه اختصاصی خدمت", "", "test")
        try {
            SuraSavedIdentifierStore(context, accountA).use { a ->
                SuraSavedIdentifierStore(context, accountB).use { b ->
                    a.save(101, sample)
                    a.save(102, sample.copy(description = "شرح مؤدی دوم"))
                    b.save(101, sample.copy(description = "شرح حساب دوم"))
                    assertEquals("خدمت آزمایشی", a.forTaxpayer(101).single().description)
                    assertEquals("شرح مؤدی دوم", a.forTaxpayer(102).single().description)
                    assertEquals("شرح حساب دوم", b.forTaxpayer(101).single().description)
                    a.remove(101, sample.id)
                    assertTrue(a.forTaxpayer(101).isEmpty())
                    assertEquals(1, a.forTaxpayer(102).size)
                    assertEquals(1, b.forTaxpayer(101).size)
                }
            }
            SuraSavedIdentifierStore(context, accountA).use { a ->
                assertEquals(1, a.forTaxpayer(102).size)
                assertTrue(a.forTaxpayer(101).isEmpty())
            }
        } finally {
            context.deleteDatabase(fileA)
            context.deleteDatabase(fileB)
        }
    }

    @Test fun ftsAndPersianDigitPrefixSearchUseTheInstalledCatalog() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.filesDir, "sura_identifiers.db")
        file.delete()
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
                db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT)")
                db.execSQL("INSERT INTO metadata VALUES ('row_count','1')")
                db.execSQL("CREATE TABLE sources (source_id INTEGER PRIMARY KEY, code TEXT)")
                db.execSQL("INSERT INTO sources VALUES (1,'test')")
                db.execSQL("CREATE TABLE catalog (id TEXT,description TEXT,vat TEXT,taxable TEXT,kind TEXT,expiration_date TEXT,source_id INTEGER)")
                db.execSQL("INSERT INTO catalog VALUES ('1234567890123','خدمات حمل و نقل','10','مشمول','شناسه عمومی خدمت','',1)")
                db.execSQL("CREATE VIRTUAL TABLE catalog_fts USING fts4(search_text, tokenize=unicode61)")
                db.execSQL("INSERT INTO catalog_fts(docid,search_text) VALUES (1,'خدمات حمل و نقل')")
            }
            val catalog = SuraIdentifierStore(context)
            assertEquals(1L, catalog.count())
            assertEquals("1234567890123", catalog.search("۱۲۳۴", null, null).single().id)
            assertEquals("1234567890123", catalog.search("حمل", "خدمت", "عمومی").single().id)
            assertTrue(catalog.search("حمل", "کالا", null).isEmpty())
        } finally {
            file.delete()
        }
    }

    @Test fun normalizationKeepsIdentifierDigitsAndNormalizesArabicCharacters() {
        assertEquals("123", SuraIdentifierStore.normalize("۱۲٣"))
        assertEquals("کالا", SuraIdentifierStore.normalize("كالا"))
    }
}
