package ir.saba.invoice.security

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator

class ScopedTaxpayerKeyVaultTest {
    @Test fun identicalLocalTaxpayerIdsCannotDecryptAcrossOwnersOrFiscalMemories() {
        val wrapping = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val vault = TaxpayerKeyVault { wrapping }
        val a = AccountScope.of("11111111-1111-4111-8111-111111111111")
        val b = AccountScope.of("22222222-2222-4222-8222-222222222222")
        val scope = TaxpayerIdentity(a,1,"A11216")
        val pem = TaxpayerKeyGenerator.generate().privatePem
        val envelope = vault.encrypt(scope,pem)
        assertTrue(vault.decrypt(scope,envelope).isNotEmpty())
        for (other in listOf(TaxpayerIdentity(b,1,"A11216"),scope.copy(companyId=2),scope.copy(fiscalMemoryId="ABC123"))) {
            assertThrows(Exception::class.java) { vault.decrypt(other,envelope) }
        }
    }
}
