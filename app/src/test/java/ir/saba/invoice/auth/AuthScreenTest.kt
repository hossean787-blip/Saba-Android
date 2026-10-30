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

    @Test fun biometricEntryPointIsOptionalAndCallsHandler() {
        var taps = 0
        compose.setContent {
            AuthTheme {
                OwnerLoginScreen({ _, _ -> }, {}, {}, biometricEnabled = true,
                    onBiometricLogin = { taps++ })
            }
        }
        compose.onNodeWithText("ورود با اثر انگشت").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, taps) }
    }

    @Test fun busyLoginDisablesNavigationAndSubmission() {
        compose.setContent { AuthTheme { OwnerLoginScreen({ _, _ -> }, {}, {}, busy = true) } }
        compose.onNodeWithText("ایجاد حساب کاربری").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("ایمیل").assertIsNotEnabled()
    }

    private fun waitForField(label: String) {
        compose.waitUntil(5000) { compose.onAllNodesWithText(label, substring = false).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    @Test fun registrationStartsWithOnlyNameAndNeverSubmitsEmptyForm() {
        compose.setContent { AuthTheme { RegistrationScreen({}, {}) } }
        compose.onNodeWithText("نام", substring = false).assertExists()
        compose.onNodeWithText("نام خانوادگی").assertDoesNotExist()
        compose.onNodeWithText("ایمیل", substring = false).assertDoesNotExist()
        compose.onNodeWithText("ساخت حساب کاربری").performScrollTo().assertIsNotEnabled()
    }

    @Test fun revealedFieldsStayVisibleWhenEarlierValueIsCleared() {
        compose.setContent { AuthTheme { RegistrationScreen({}, {}) } }
        compose.onNodeWithText("نام", substring = false).performTextInput("Ali")
        waitForField("نام خانوادگی")
        compose.onNodeWithText("نام", substring = false).assertIsFocused().performTextClearance()
        compose.onNodeWithText("نام خانوادگی").assertExists()
        compose.onNodeWithText("ساخت حساب کاربری").performScrollTo().assertIsNotEnabled()
    }

    @Test fun matchingPasswordsRemainInFormWhenGatewayRejectsRegistration() {
        val error = androidx.compose.runtime.mutableStateOf<String?>(null)
        compose.setContent { AuthTheme {
            RegistrationScreen(
                onSubmit = { error.value = "خطای سرور آزمایشی" },
                onSignIn = {},
                serverError = error.value
            )
        } }
        compose.onNodeWithText("نام", substring = false).performTextInput("Ali")
        waitForField("نام خانوادگی")
        compose.onNodeWithText("نام خانوادگی").performScrollTo().performTextInput("Ahmadi")
        waitForField("ایمیل")
        compose.onNodeWithText("ایمیل", substring = false).performScrollTo().performTextInput("owner@example.com")
        waitForField("رمز عبور ثابت")
        compose.onNodeWithText("رمز عبور ثابت").performScrollTo().performTextInput("password123")
        waitForField("تکرار رمز عبور")
        compose.onNodeWithText("تکرار رمز عبور").performScrollTo().performTextInput("password123")
        compose.onNodeWithTag("passwordsMatch").assertExists()
        compose.onNodeWithText("ساخت حساب کاربری").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("registrationServerError").assertTextContains("خطای سرور آزمایشی")
        compose.onNodeWithTag("passwordsMatch").assertExists()
        compose.onNodeWithText("ساخت حساب کاربری").assertIsEnabled()
    }

    @Test fun differentConfirmationHasItsOwnErrorAndCannotSubmit() {
        compose.setContent { AuthTheme { RegistrationScreen({}, {}) } }
        compose.onNodeWithText("نام", substring = false).performTextInput("Ali")
        waitForField("نام خانوادگی")
        compose.onNodeWithText("نام خانوادگی").performScrollTo().performTextInput("Ahmadi")
        waitForField("ایمیل")
        compose.onNodeWithText("ایمیل", substring = false).performScrollTo().performTextInput("owner@example.com")
        waitForField("رمز عبور ثابت")
        compose.onNodeWithText("رمز عبور ثابت").performScrollTo().performTextInput("password123")
        waitForField("تکرار رمز عبور")
        compose.onNodeWithText("تکرار رمز عبور").performScrollTo().performTextInput("password124")
        compose.onNodeWithText("تکرار رمز عبور مطابقت ندارد.").assertExists()
        compose.onNodeWithText("ساخت حساب کاربری").performScrollTo().assertIsNotEnabled()
    }

    @Test fun registrationSubmitsMatchingPasswords() {
        var received: RegistrationInput? = null
        compose.setContent { AuthTheme { RegistrationScreen({ received = it }, {}) } }
        compose.onNodeWithText("نام", substring = false).performScrollTo().performTextInput("Ali")
        waitForField("نام خانوادگی")
        compose.onNodeWithText("نام خانوادگی").performScrollTo().performTextInput("Ahmadi")
        waitForField("ایمیل")
        compose.onNodeWithText("ایمیل", substring = false).performScrollTo().performTextInput("owner@example.com")
        waitForField("رمز عبور ثابت")
        compose.onNodeWithText("رمز عبور ثابت").performScrollTo().performTextInput("password123")
        waitForField("تکرار رمز عبور")
        compose.onNodeWithText("تکرار رمز عبور").performScrollTo().performTextInput("password123")
        compose.onNodeWithText("ساخت حساب کاربری").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("owner@example.com", received!!.email)
            assertEquals("", received!!.nationalId)
        }
    }
}
