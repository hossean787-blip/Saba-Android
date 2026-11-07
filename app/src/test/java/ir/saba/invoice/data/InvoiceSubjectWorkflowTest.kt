package ir.saba.invoice.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.domain.InvoiceSubject
import ir.saba.invoice.security.AccountScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InvoiceSubjectWorkflowTest {
    private val account = AccountScope.of("11111111-1111-4111-8111-111111111111")
    private val taxId = "A11216" + "0".repeat(16)
    private fun lines() = listOf(
        InvoiceLine(invoiceId = 0, description = "خدمت", itemCode = "1234567890123",
            quantity = 1, unitPriceRial = 1000, taxRatePercent = 10, unitCode = "1627")
    )
    private suspend fun expectInvalid(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected validation failure")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun acceptedSameBuyerOriginalAllowsLocalCorrectionAndImmutableDraftRecovery() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SabaDatabase::class.java).build()
        try {
            val company = db.companyDao().insert(Company(
                purchaserId = account.ownerId, name = "فروشنده", nationalId = "1001"))
            val buyer = db.customerDao().insert(Customer(companyId = company, name = "خریدار"))
            val other = db.customerDao().insert(Customer(companyId = company, name = "خریدار دیگر"))
            val repository = InvoiceRepository(db)
            val originalId = repository.saveDraft(company, buyer, "11", "1405/08/12", lines(),
                serverPromoConfirmed = true)
            repository.finalizeDraft(company, originalId)

            // A record in the outbox is not enough; it must be ACCEPTED with a verified-receipt slot.
            db.officialSubmissionDao().insert(OfficialSubmission(
                requestId = UUID.randomUUID().toString(),
                ownerId = account.ownerId, companyId = company, invoiceId = originalId,
                fiscalId = "A11216", taxId = taxId,
                payload = "{}", payloadHash = "hash", preparedAt = 100,
                state = "ACCEPTED", referenceNumber = "valid-reference", signedReceipt = "receipt"
            ))
            awaitInvalidReferences(db, repository, company, buyer, other)
            val coordinator = InvoiceCreationCoordinator(db, account) { _, _, _ -> }
            val snapshot = InvoiceCreationSnapshot(
                buyer, "12", "1405/08/13", lines(), subject = InvoiceSubject.CORRECTION,
                referenceTaxId = taxId)
            val pending = coordinator.prepare(company, snapshot, UUID.randomUUID().toString(),
                InvoiceCreditKind.PAID)
            assertEquals(snapshot, coordinator.snapshot(pending))
            val id = coordinator.finish(pending.requestId)
            val draft = repository.detailsForCompany(company, id)
            assertEquals(InvoiceSubject.CORRECTION, draft.invoice.subject)
            assertEquals(taxId, draft.invoice.referenceTaxId)
            assertEquals(originalId, db.officialSubmissionDao().forCompany(company).single().invoiceId)
            repository.finalizeDraft(company, id)
            assertEquals("FINALIZED", db.invoiceDao().byId(id)?.status)
            expectInvalid {
                // The same accepted reference must not have two simultaneously active corrections.
                repository.saveDraft(company, buyer, "17", "1405/08/14", lines(),
                    serverPromoConfirmed = true, subject = InvoiceSubject.CORRECTION,
                    referenceTaxId = taxId)
            }
            try {
                InvoiceSubject.requireSupportedForOfficialSubmission(db.invoiceDao().byId(id)!!)
                fail("Corrective payload must not be submitted as an original tax invoice")
            } catch (_: IllegalArgumentException) {}
            assertNotNull(db.quotaDao().useForInvoice(id))
        } finally {
            db.close()
        }
    }

    private suspend fun awaitInvalidReferences(
        db: SabaDatabase, repository: InvoiceRepository,
        company: Long, buyer: Long, otherBuyer: Long
    ) {
        expectInvalid {
            repository.saveDraft(company, buyer, "13", "1405/08/13", lines(),
                serverPromoConfirmed = true, subject = InvoiceSubject.CORRECTION,
                referenceTaxId = null)
        }
        expectInvalid {
            repository.saveDraft(company, otherBuyer, "14", "1405/08/13", lines(),
                serverPromoConfirmed = true, subject = InvoiceSubject.CORRECTION,
                referenceTaxId = taxId)
        }
        expectInvalid {
            // A correction cannot replace the original product or service identifier.
            repository.saveDraft(company, buyer, "18", "1405/08/13",
                lines().map { it.copy(itemCode = "9999999999999") },
                serverPromoConfirmed = true, subject = InvoiceSubject.CORRECTION,
                referenceTaxId = taxId)
        }
        expectInvalid {
            repository.saveDraft(company, buyer, "15", "1405/08/13", lines(),
                serverPromoConfirmed = true, subject = InvoiceSubject.ORIGINAL,
                referenceTaxId = taxId)
        }
        val foreignCompany = db.companyDao().insert(Company(
            purchaserId = account.ownerId, name = "مودی دیگر", nationalId = "2002"))
        val foreignBuyer = db.customerDao().insert(Customer(companyId = foreignCompany, name = "مشتری"))
        expectInvalid {
            repository.saveDraft(foreignCompany, foreignBuyer, "16", "1405/08/13", lines(),
                serverPromoConfirmed = true, subject = InvoiceSubject.CORRECTION,
                referenceTaxId = taxId)
        }
        assertTrue(db.invoiceDao().forCompany(company).size == 1)
        assertTrue(db.invoiceDao().forCompany(foreignCompany).isEmpty())
    }

    @Test fun noAcceptedReferenceNeverAuthorizesRemoteCreditConsumption() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SabaDatabase::class.java).build()
        try {
            val company = db.companyDao().insert(Company(
                purchaserId = account.ownerId, name = "مودی", nationalId = "999"))
            val buyer = db.customerDao().insert(Customer(companyId = company, name = "مشتری"))
            var debits = 0
            val coordinator = InvoiceCreationCoordinator(db, account) { _, _, _ -> debits++ }
            expectInvalid {
                coordinator.prepare(company, InvoiceCreationSnapshot(
                    buyer, "11", "1405/08/12", lines(),
                    subject = InvoiceSubject.CORRECTION, referenceTaxId = taxId),
                    UUID.randomUUID().toString(), InvoiceCreditKind.PAID)
            }
            assertEquals(0, debits)
            assertTrue(db.invoiceDao().forCompany(company).isEmpty())
            assertNull(coordinator.pending(company))
        } finally {
            db.close()
        }
    }
}
