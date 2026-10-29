package ir.saba.invoice.auth

/**
 * Pure validation rules for the owner registration form.
 * Server must independently enforce identity uniqueness, OTP verification and rate limits.
 */
data class RegistrationInput(
    val firstName: String,
    val lastName: String,
    val nationalId: String,
    val mobile: String,
    val email: String,
    val password: String,
    val passwordConfirmation: String
)

object RegistrationValidator {
    fun normalizeDigits(value: String): String = buildString {
        for (char in value.trim()) append(
            when (char) {
                in '۰'..'۹' -> ('0'.code + char.code - '۰'.code).toChar()
                in '٠'..'٩' -> ('0'.code + char.code - '٠'.code).toChar()
                else -> char
            }
        )
    }

    fun validNationalId(raw: String): Boolean {
        val id = normalizeDigits(raw)
        if (!id.matches(Regex("[0-9]{10}")) || id.toSet().size == 1) return false
        val sum = (0..8).sumOf { (id[it] - '0') * (10 - it) }
        val remainder = sum % 11
        val expected = if (remainder < 2) remainder else 11 - remainder
        return id[9] - '0' == expected
    }

    fun validMobile(raw: String): Boolean =
        normalizeDigits(raw).matches(Regex("09[0-9]{9}"))

    fun validEmail(raw: String): Boolean {
        val email = raw.trim()
        val local = email.substringBefore("@")
        return local.length in 1..64 && !local.startsWith(".") && !local.endsWith(".") && !local.contains("..") && email.length <= 254 && email.matches(
            Regex("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+")
        )
    }

    fun emailPasswordErrors(input: RegistrationInput): Set<String> = buildSet {
        if (input.firstName.trim().length !in 1..100) add("firstName")
        if (input.lastName.trim().length !in 1..100) add("lastName")
        if (!validEmail(input.email)) add("email")
        if (input.password.length !in 8..128 || input.password.isBlank()) add("password")
        if (input.password != input.passwordConfirmation) add("passwordConfirmation")
    }

    fun errors(input: RegistrationInput): Set<String> = buildSet {
        if (input.firstName.isBlank()) add("firstName")
        if (input.lastName.isBlank()) add("lastName")
        if (!validNationalId(input.nationalId)) add("nationalId")
        if (!validMobile(input.mobile)) add("mobile")
        if (!validEmail(input.email)) add("email")
        if (input.password.length < 8) add("password")
        if (input.password != input.passwordConfirmation) add("passwordConfirmation")
    }
}

enum class LoginMethod { PASSWORD, SMS_OTP }

object OtpPolicy {
    const val CODE_LENGTH = 6
    const val VALIDITY_SECONDS = 120L

    fun isExpired(issuedAtEpochSeconds: Long, nowEpochSeconds: Long): Boolean =
        nowEpochSeconds >= issuedAtEpochSeconds + VALIDITY_SECONDS

    fun validFormat(code: String): Boolean =
        RegistrationValidator.normalizeDigits(code).matches(Regex("[0-9]{6}"))
}
