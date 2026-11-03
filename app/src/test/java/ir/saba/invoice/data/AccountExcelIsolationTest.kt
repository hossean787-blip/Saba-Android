package ir.saba.invoice.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class AccountExcelIsolationTest {
    @Test fun exportFiltersOwnersAndImportNeverAssignsSharedOwner()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),SabaDatabase::class.java).build()
        try {
            db.companyDao().insert(Company(purchaserId="account-a",name="الف",nationalId="1"))
            val foreign=db.companyDao().insert(Company(purchaserId="account-b",name="ب",nationalId="2"))
            val service=ExcelTransferService(db,"account-a"){100}
            val rows=ExcelWorkbook.read(service.export("taxpayers",null))
            assertEquals(2,rows.size);assertEquals("الف",rows[1][0])
            val incoming=ExcelWorkbook.write(ExcelTransferService.taxpayerHeaders,listOf(listOf("جدید","3")))
            assertEquals(1,service.import("taxpayers",null,incoming))
            assertEquals("account-a",db.companyDao().byId(100)!!.purchaserId)
            try {service.export("customers",foreign);fail("Expected foreign owner rejection")}catch(_:IllegalArgumentException){}
        }finally{db.close()}
    }
}
