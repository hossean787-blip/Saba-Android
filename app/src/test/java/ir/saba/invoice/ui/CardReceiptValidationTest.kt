package ir.saba.invoice.ui
import org.junit.Assert.*
import org.junit.Test
class CardReceiptValidationTest {
 @Test fun normalizesPersianAndArabicDates(){
   assertEquals("1405/09/04",formatReceiptDate("۱۴۰۵/۰۹/۰۴"))
   assertEquals("1405/09/04",formatReceiptDate("١٤٠٥٠٩٠٤"))
 }
 @Test fun receiptRequiresAllFields(){
   assertNotNull(receiptIssue("","1405/09/04",true))
   assertNotNull(receiptIssue("1234","",true))
   assertNotNull(receiptIssue("1234","1405/09/04",false))
   assertNull(receiptIssue("1234","1405/09/04",true))
 }
 @Test fun invalidDatesRejected(){
   assertNotNull(receiptIssue("1234","1405/13/04",true))
   assertNotNull(receiptIssue("1234","1405/07/31",true))
 }
}
