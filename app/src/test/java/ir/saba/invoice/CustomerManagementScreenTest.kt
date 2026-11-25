package ir.saba.invoice

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.data.*
import ir.saba.invoice.ui.CustomerManagementScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class CustomerManagementScreenTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var db:SabaDatabase
    private var company=0L
    private var buyer=0L
    private var other=0L
    private var rows by mutableStateOf(emptyList<Customer>())
    private val notices=mutableListOf<String>()
    @Before fun setup()=runBlocking{
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),SabaDatabase::class.java).build()
        company=db.companyDao().let{it.insert(Company(purchaserId="11111111-1111-4111-8111-111111111111",name="شرکت",nationalId="1"))}
        buyer=db.customerDao().insert(Customer(companyId=company,name="خریدار آزمایشی",nationalId="1234567890"))
        other=db.customerDao().insert(Customer(companyId=company,name="مشتری دوم",nationalId="9876543210"))
        db.customerProfileDao().upsert(CustomerProfile(customerId=buyer,companyId=company,mobile="09120000000",nationality="IR"))
        rows=db.customerDao().forCompany(company)
    }
    private fun open(){
        compose.setContent{MaterialTheme{CustomerManagementScreen(db,company,rows,onChanged={rows=db.customerDao().forCompany(company)},onNotice={notices.add(it)},onEditing={})}}
        compose.waitUntil(15000){compose.onAllNodesWithText("خریدار آزمایشی").fetchSemanticsNodes().isNotEmpty() && runCatching{compose.onNodeWithText("ثبت").assertIsEnabled()}.isSuccess}
        compose.waitForIdle()
    }
    @Test fun listActionsAndSearch(){
        open()
        compose.onNodeWithText("ثبت").assertExists()
        compose.onNodeWithText("ویرایش").assertExists()
        compose.onNodeWithText("حذف").assertExists()
        compose.onNodeWithText("جستجوی مشتریان").performTextInput("۱۲۳۴۵۶۷۸۹۰")
        compose.onNodeWithText("خریدار آزمایشی").assertExists()
        compose.onNodeWithText("مشتری دوم").assertDoesNotExist()
        compose.onNodeWithText("خریدار آزمایشی").performClick()
        compose.onNodeWithText("انتخاب‌شده").assertExists()
        compose.onNodeWithText("ویرایش").performClick()
        compose.onNodeWithText("ویرایش مشخصات").assertExists()
        compose.onNodeWithText("خریدار آزمایشی").assertExists()
    }
    @Test fun deletionIsConfirmedAndPreservesCustomerAndProfile(){
        open()
        compose.onNodeWithText("خریدار آزمایشی").performClick()
        compose.onNodeWithText("حذف").performClick()
        compose.onNodeWithText("حذف مشتری").assertExists()
        compose.onNodeWithText("انصراف").performClick()
        runBlocking{assertTrue(db.customerProfileDao().byCustomer(buyer)!!.active)}
        compose.onNodeWithText("حذف").performClick()
        compose.onNodeWithText("تأیید غیرفعال‌سازی").performClick()
        compose.waitUntil(15000){compose.onAllNodesWithText("غیرفعال").fetchSemanticsNodes().isNotEmpty()}
        runBlocking{
            assertNotNull(db.customerDao().byId(buyer))
            assertFalse(db.customerProfileDao().byCustomer(buyer)!!.active)
            assertEquals("09120000000",db.customerProfileDao().byCustomer(buyer)!!.mobile)
            assertNotNull(db.customerDao().byId(other))
        }
    }
}
