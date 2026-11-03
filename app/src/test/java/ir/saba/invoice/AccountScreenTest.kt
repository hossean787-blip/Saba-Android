package ir.saba.invoice

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.security.AccountScope
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import ir.saba.invoice.ui.AccountScreen
import ir.saba.invoice.ui.AppSettingsScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AccountScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun accountActionsAreTwoColumnsAndTransferAndLogoutAreFunctional() {
        var transfers = 0
        var logouts = 0
        compose.setContent { MaterialTheme {
            AccountScreen("11111111-1111-4111-8111-111111111111",3, 7, false, "مالک آزمایشی", "owner@example.com",
                { transfers++ }, { logouts++ })
        } }
        val personal = compose.onNodeWithTag("personalInfo").assertExists()
            .fetchSemanticsNode().boundsInRoot.top
        val password = compose.onNodeWithTag("changePassword").assertExists()
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue("Profile appears above action grid", personal < password)
        val mobile = compose.onNodeWithTag("changeMobile").assertExists()
            .fetchSemanticsNode().boundsInRoot.top
        val transfer = compose.onNodeWithTag("transferCharge").assertExists()
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue("Mobile appears before charge transfer", mobile < transfer)
        compose.onNodeWithText("تنظیمات برنامه").assertDoesNotExist()
        compose.onNodeWithTag("transferCharge").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, transfers) }
        compose.onNodeWithTag("accountLogout").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, logouts) }
    }

    @Test fun verifiedProfileIsShownAndUnavailableBackendActionsDontPretendSuccess() {
        compose.setContent { MaterialTheme {
            AccountScreen("11111111-1111-4111-8111-111111111111",3, 7, false, "مالک آزمایشی", "owner@example.com", {}, {})
        } }
        compose.onNodeWithTag("personalInfo").performScrollTo().performClick()
        compose.onNodeWithText("ویرایش مشخصات مالک").assertExists()
        compose.onNodeWithText("ایمیل ورود: owner@example.com").assertExists()
        compose.onNodeWithText("انصراف").performClick()
        compose.onNodeWithTag("changePassword").performClick()
        compose.onAllNodesWithText("تغییر رمز عبور").assertCountEquals(2)
        compose.onNodeWithText("این خدمت هنوز در سرور متصل نشده است", substring = true).assertExists()
        compose.onNodeWithText("متوجه شدم").performClick()
        compose.onNodeWithTag("purchaseCharge").assertDoesNotExist()
    }

    @Test fun applicationSettingsRemainAccessibleAsSeparateScreen() {
        var returns = 0
        var scaleChanges = 0
        compose.setContent { MaterialTheme {
            AppSettingsScreen(1f, false, { scaleChanges++ }, {}, {}, true, {}, { returns++ })
        } }
        compose.onNodeWithText("تنظیمات نمایش").assertExists()
        compose.onNodeWithText("ورود با اثر انگشت").assertExists()
        compose.onNodeWithText("غیرفعال‌کردن ورود با اثر انگشت").assertExists()
        compose.onNodeWithText("بازگشت به حساب کاربری").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, returns) }
    }
    @Test fun reusedEmailDoesNotShareLocalContactProfileAcrossOwners() {
        val a=AccountScope.of("11111111-1111-4111-8111-111111111111")
        val b=AccountScope.of("22222222-2222-4222-8222-222222222222")
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs=context.getSharedPreferences("sura_owner_profile_local_v2",0)
        prefs.edit().putString("name_${a.storageKey}","نام خصوصی حساب اول").commit()
        var owner by mutableStateOf(a.ownerId)
        try {
            compose.setContent {MaterialTheme {AccountScreen(owner,0,0,false,null,"same@example.com",{},{})}}
            compose.onNodeWithText("نام خصوصی حساب اول").assertExists()
            compose.runOnIdle {owner=b.ownerId}
            compose.onNodeWithText("نام خصوصی حساب اول").assertDoesNotExist()
            compose.onNodeWithText("مالک حساب سورا").assertExists()
        } finally {prefs.edit().clear().commit()}
    }

}
