package ir.saba.invoice.auth

import org.junit.Assert.*
import org.junit.Test

class RegistrationValidationTest {
    @Test fun validNationalIdAndPersianDigits() {
        assertTrue(RegistrationValidator.validNationalId("۰۰۸۴۵۷۵۹۴۸"))
        assertFalse(RegistrationValidator.validNationalId("۰۰۰۰۰۰۰۰۰۰"))
        assertFalse(RegistrationValidator.validNationalId("0084575949"))
    }

    @Test fun mobileAndEmailRequired() {
        assertTrue(RegistrationValidator.validMobile("۰۹۱۲۱۲۳۴۵۶۷"))
        assertFalse(RegistrationValidator.validMobile("09123"))
        assertTrue(RegistrationValidator.validEmail("owner@example.com"))
        assertFalse(RegistrationValidator.validEmail(""))
        assertFalse(RegistrationValidator.validEmail("invalid@"))
    }

    @Test fun passwordConfirmationIsRequired() {
        val form = RegistrationInput("Ali", "Ahmadi", "0084575948", "09121234567", "owner@example.com", "password123", "different")
        assertTrue(RegistrationValidator.errors(form).contains("passwordConfirmation"))
    }

    @Test fun otpExpiresAtExactlyTwoMinutes() {
        assertFalse(OtpPolicy.isExpired(1000, 1119))
        assertTrue(OtpPolicy.isExpired(1000, 1120))
        assertTrue(OtpPolicy.validFormat("۱۲۳۴۵۶"))
        assertFalse(OtpPolicy.validFormat("12345"))
    }
}
