package ir.saba.invoice.security

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AccountScopeTest {
    private val owner = "11111111-1111-4111-8111-111111111111"
    private fun token(payload: String) = "e30." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray()) + ".signature"
    @Test fun derivesStorageIdentityFromSubjectRatherThanEmailOrTokenRotation() {
        val first = AccountScope.fromAuthenticatedToken(token("""{"sub":"$owner","email":"one@example.com"}"""))
        val second = AccountScope.fromAuthenticatedToken(token("""{"sub":"$owner","email":"two@example.com","exp":42}"""))
        assertEquals(first, second)
        assertEquals(first.databaseName, second.databaseName)
    }
    @Test fun differentOwnersHaveDifferentStorageNamespaces() {
        assertNotEquals(AccountScope.of(owner).databaseName, AccountScope.of("22222222-2222-4222-8222-222222222222").databaseName)
        assertFalse(AccountScope.of(owner).databaseName.contains(owner))
    }
    @Test fun missingSubjectNeverFallsBackToSharedOwner() {
        for (value in listOf("", "access", token("{}"), token("""{"sub":"owner"}"""))) {
            assertThrows(IllegalArgumentException::class.java) { AccountScope.fromAuthenticatedToken(value) }
        }
    }
}
