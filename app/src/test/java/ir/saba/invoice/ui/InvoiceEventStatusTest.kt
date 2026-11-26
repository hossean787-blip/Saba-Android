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
            assertNull(invoicePreviewAction(invoice,receipt.copy(state=state,protocol="V2")))
        }
        val accepted=receipt.copy(state="ACCEPTED",referenceNumber="ref",lastError=OfficialReferenceEvidence.LEGACY_UNBOUND)
        assertFalse(canEditInvoicePreview(invoice,accepted))
        assertNull(invoicePreviewAction(invoice,accepted))
        assertNull(invoicePreviewAction(invoice.copy(trackingCode="old"),null))
        assertFalse(canEditInvoicePreview(invoice.copy(trackingCode="old"),null))
        assertTrue(canEditInvoicePreview(invoice,receipt.copy(state="RESERVED")))
    }
    @Test fun correctionRequiresSameCompanyAcceptedOriginalAndDirectProtocol() {
        val accepted=receipt.copy(state="ACCEPTED",referenceNumber="ref",lastError=OfficialReferenceEvidence.LEGACY_BOUND)
        assertTrue(canStartCorrection(invoice,accepted))
        assertTrue(canStartCorrection(invoice,accepted.copy(lastError=OfficialReferenceEvidence.LEGACY_UNBOUND)))
        assertFalse(canStartCorrection(invoice,accepted.copy(state="UNKNOWN")))
        assertFalse(canStartCorrection(invoice,accepted.copy(protocol="V2")))
        assertFalse(canStartCorrection(invoice,accepted.copy(companyId=99)))
        assertFalse(canStartCorrection(invoice.copy(subject="CORRECTION"),accepted))
        assertFalse(canStartCorrection(invoice.copy(status="DRAFT"),accepted))
    }
    @Test fun correctionPickerIncludesAcceptedUnboundAndExcludesOtherBuyersOwnersAndPending() {
        val accepted=receipt.copy(state="ACCEPTED",referenceNumber="ref",lastError=OfficialReferenceEvidence.LEGACY_UNBOUND)
        val foreignBuyer=invoice.copy(id=2,customerId=4)
        val foreignOwner=invoice.copy(id=3)
        val pending=invoice.copy(id=4)
        val all=listOf(invoice,foreignBuyer,foreignOwner,pending)
        val receipts=mapOf(1L to accepted,2L to accepted.copy(invoiceId=2),
            3L to accepted.copy(invoiceId=3,ownerId="other"),4L to accepted.copy(invoiceId=4,state="SUBMITTED"))
        assertEquals(listOf(invoice),correctionCandidates(all,receipts,"owner",2,3))
        assertTrue(correctionCandidates(all,receipts,"owner",99,3).isEmpty())
    }
    @Test fun correctionLinkReflectsRegistrationWithoutInventingLegalCancellation() {
        val parent=receipt.copy(state="ACCEPTED",referenceNumber="ref",lastError=OfficialReferenceEvidence.LEGACY_BOUND)
        val child=invoice.copy(id=5,subject="CORRECTION",referenceTaxId=parent.taxId,invoiceNumber="2000000001")
        val childReceipt=parent.copy(invoiceId=5,requestId="child",taxId="childTax",referenceNumber="childRef")
        val all=listOf(invoice,child)
        assertEquals("اصلاحیه ارسال نشده؛ مرجع تغییر نکرده",correctionLink(invoice,all,mapOf(1L to parent))?.label)
        assertEquals("اصلاحیه در انتظار نتیجه؛ ابطال تأیید نشده",correctionLink(invoice,all,mapOf(1L to parent,5L to childReceipt.copy(state="SUBMITTED")))?.label)
        assertEquals("اصلاحیه ناموفق؛ مرجع تغییر نکرده",correctionLink(invoice,all,mapOf(1L to parent,5L to childReceipt.copy(state="REJECTED")))?.label)
        assertEquals("اصلاحیه ثبت شده؛ تعیین تکلیف مرجع در کارپوشه",correctionLink(invoice,all,mapOf(1L to parent,5L to childReceipt))?.label)
        assertNull(correctionLink(invoice,listOf(invoice,child.copy(companyId=99)),mapOf(1L to parent)))
        assertNull(correctionLink(invoice,listOf(invoice,child.copy(referenceTaxId="wrong")),mapOf(1L to parent)))
        assertEquals(InvoiceEventStatus.SUCCESS,invoiceEventStatus(invoice,parent))
    }
    @Test fun cancellationEligibilityAndLinkedStatusNeverInventFinalLegalAbtal() {
        val accepted=receipt.copy(state="ACCEPTED",referenceNumber="ref",lastError=OfficialReferenceEvidence.LEGACY_BOUND)
        assertTrue(canDraftOriginalCancellation(invoice,accepted,listOf(invoice)))
        assertTrue(canDraftOriginalCancellation(invoice.copy(subject="CORRECTION"),accepted,listOf(invoice)))
        assertFalse(canDraftOriginalCancellation(invoice.copy(subject="CANCELLATION"),accepted,listOf(invoice)))
        assertFalse(canDraftOriginalCancellation(invoice,accepted.copy(state="SUBMITTED"),listOf(invoice)))
        val child=invoice.copy(id=5,subject="CANCELLATION",referenceTaxId=accepted.taxId,invoiceNumber="2000000002")
        val childRow=accepted.copy(invoiceId=5,requestId="child",taxId="childTax",referenceNumber="childRef")
        assertFalse(canDraftOriginalCancellation(invoice,accepted,listOf(invoice,child)))
        assertEquals("ابطالی در انتظار نتیجه؛ ابطال قطعی نیست",cancellationLink(invoice,listOf(invoice,child),mapOf(1L to accepted,5L to childRow.copy(state="SUBMITTED")))?.label)
        assertTrue(cancellationLink(invoice,listOf(invoice,child),mapOf(1L to accepted,5L to childRow))!!.label.contains("کارپوشه"))
        assertEquals(InvoiceEventStatus.SUCCESS,invoiceEventStatus(invoice,accepted))
    }
    @Test fun titleUsesOnlyMatchingCustomer() {
        assertEquals("صورتحساب مشتری آزمایشی",invoiceEventTitle(invoice,listOf(Customer(id=3,companyId=2,name="مشتری آزمایشی"))))
        assertEquals("صورتحساب مشتری نامشخص",invoiceEventTitle(invoice,listOf(Customer(id=3,companyId=99,name="نام دیگر"))))
    }
}
