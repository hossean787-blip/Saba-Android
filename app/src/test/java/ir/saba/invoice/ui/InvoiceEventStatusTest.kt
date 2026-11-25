package ir.saba.invoice.ui
import ir.saba.invoice.data.*
import org.junit.Assert.*
import org.junit.Test

class InvoiceEventStatusTest {
    private val invoice=Invoice(id=1,companyId=2,customerId=3,invoiceNumber="100",issueDate="1405/09/04",totalAmountRial=22000,taxAmountRial=2000,status="FINALIZED")
    private val receipt=OfficialSubmission(requestId="test",ownerId="owner",companyId=2,invoiceId=1,fiscalId="ABC123",taxId="test",payload="{}",payloadHash="test",preparedAt=1,protocol="LEGACY")
    @Test fun directSuccessDoesNotRequireV2ReceiptOrUnlockReference() {
        val row=receipt.copy(state="ACCEPTED",referenceNumber="ref",lastError=OfficialReferenceEvidence.LEGACY_UNBOUND)
        assertEquals(InvoiceEventStatus.SUCCESS,invoiceEventStatus(invoice,row))
        assertFalse(OfficialReferenceEvidence.eligible(row))
        assertEquals(InvoiceEventStatus.CHECK,invoiceEventStatus(invoice,row.copy(referenceNumber=null)))
        assertEquals(InvoiceEventStatus.CHECK,invoiceEventStatus(invoice,row.copy(lastError=null)))
    }
    @Test fun distinguishRejectedPendingUnknownAndLocal() {
        assertEquals(InvoiceEventStatus.REJECTED,invoiceEventStatus(invoice,receipt.copy(state="REJECTED")))
        assertEquals(InvoiceEventStatus.PENDING,invoiceEventStatus(invoice,receipt.copy(state="SUBMITTED")))
        assertEquals(InvoiceEventStatus.CHECK,invoiceEventStatus(invoice,receipt.copy(state="UNKNOWN")))
        assertEquals(InvoiceEventStatus.UNSENT,invoiceEventStatus(invoice,null))
        assertEquals(InvoiceEventStatus.UNSENT,invoiceEventStatus(invoice,receipt.copy(state="RESERVED")))
        assertEquals(InvoiceEventStatus.DRAFT,invoiceEventStatus(invoice.copy(status="DRAFT"),receipt.copy(state="RESERVED")))
        assertEquals(InvoiceEventStatus.UNSENT,invoiceEventStatus(invoice,receipt.copy(state="PREPARED",lastError="preflight failed")))
        assertEquals(InvoiceEventStatus.DRAFT,invoiceEventStatus(invoice.copy(status="DRAFT"),null))
        assertEquals(InvoiceEventStatus.UNSENT,invoiceEventStatus(invoice,receipt.copy(companyId=99,state="REJECTED")))
    }
    @Test fun allEventStatusesOpenSamePdfPreview() {
        for(status in InvoiceEventStatus.values()) assertTrue(invoiceEventOpensPreview(status))
    }
    @Test fun previewActionsAreSafeForEachState() {
        assertTrue(canEditInvoicePreview(invoice,null))
        assertTrue(canEditInvoicePreview(invoice.copy(status="DRAFT"),null))
        assertEquals("ارسال به سامانه مؤدیان",invoicePreviewAction(invoice,null))
        assertTrue(canEditInvoicePreview(invoice,receipt.copy(state="REJECTED")))
        assertNull(invoicePreviewAction(invoice,receipt.copy(state="REJECTED")))
        for(state in listOf("SUBMITTED","SENDING","UNKNOWN")) {
            assertFalse(canEditInvoicePreview(invoice,receipt.copy(state=state)))
            assertEquals("استعلام وضعیت ارسال",invoicePreviewAction(invoice,receipt.copy(state=state)))
        }
        val accepted=receipt.copy(state="ACCEPTED",referenceNumber="ref",lastError=OfficialReferenceEvidence.LEGACY_UNBOUND)
        assertFalse(canEditInvoicePreview(invoice,accepted))
        assertNull(invoicePreviewAction(invoice,accepted))
        assertNull(invoicePreviewAction(invoice.copy(trackingCode="old"),null))
        assertFalse(canEditInvoicePreview(invoice.copy(trackingCode="old"),null))
        assertTrue(canEditInvoicePreview(invoice,receipt.copy(state="RESERVED")))
    }
    @Test fun titleUsesOnlyMatchingCustomer() {
        assertEquals("صورتحساب مشتری آزمایشی",invoiceEventTitle(invoice,listOf(Customer(id=3,companyId=2,name="مشتری آزمایشی"))))
        assertEquals("صورتحساب مشتری نامشخص",invoiceEventTitle(invoice,listOf(Customer(id=3,companyId=99,name="نام دیگر"))))
    }
}
