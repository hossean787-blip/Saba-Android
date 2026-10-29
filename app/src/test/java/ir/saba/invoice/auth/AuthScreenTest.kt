package ir.saba.invoice.auth

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AuthScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loginRequiresValidEmailAndPassword() {
        var submitted = false
        compose.setContent { AuthTheme { OwnerLoginScreen({ _, _ -> submitted = true }, {}, {}) } }
        compose.onNodeWithText("ورود با رمز ثابت").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("ایمیل").performTextInput("owner@example.com")
        compose.onNodeWithText("رمز عبور ثابت").performTextInput("password123")
        compose.onNodeWithText("ورود با رمز ثابت").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(submitted) }
    }

    @Test fun busyLoginDisablesNavigationAndSubmission() {
        compose.setContent { AuthTheme { OwnerLoginScreen({ _, _ -> }, {}, {}, busy = true) } }
        compose.onNodeWithText("ثبت‌نام مالک جدید").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("ایمیل").assertIsNotEnabled()
    }

    @Test fun invalidRegistrationShowsFieldErrors() {
        var submitted = false
        compose.setContent { AuthTheme { RegistrationScreen({ submitted = true }, {}) } }
        compose.onNodeWithText("ساخت حساب کاربری").performScrollTo().performClick()
        compose.onNodeWithText("نشانی ایمیل معتبر وارد کنید.").assertExists()
        compose.onNodeWithText("نام را وارد کنید (حداکثر ۱۰۰ نویسه).").assertExists()
        compose.runOnIdle { assertFalse(submitted) }
    }

    @Test fun registrationSubmitsMatchingPasswords() {
        var received: RegistrationInput? = null
        compose.setContent { AuthTheme { RegistrationScreen({ received = it }, {}) } }
        compose.onNodeWithText("نام", substring = false).performScrollTo().performTextInput("Ali")
        compose.onNodeWithText("نام خانوادگی").performScrollTo().performTextInput("Ahmadi")
        compose.onNodeWithText("ایمیل", substring = false).performScrollTo().performTextInput("owner@example.com")
        compose.onNodeWithText("رمز عبور ثابت").performScrollTo().performTextInput("password123")
        compose.onNodeWithText("تکرار رمز عبور").performScrollTo().performTextInput("password123")
        compose.onNodeWithText("ساخت حساب کاربری").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("owner@example.com", received!!.email)
            assertEquals("", received!!.nationalId)
        }
    }
}
