package ir.saba.invoice.ui
import org.junit.Assert.*
import org.junit.Test
class PurchaseQuoteTest {
 @Test fun singleQuoteBoundToSelection(){
   val q=PurchaseQuote.validate("single",2,200000,20000,220000,true)
   assertTrue(q.matches("single",2L));assertFalse(q.matches("single",1L));assertFalse(q.matches("monthly",20L))
 }
 @Test fun monthlyRemainsDisabledWhenServerDisablesIt(){
   val q=PurchaseQuote.validate("monthly",20,1500000,150000,1650000,false)
   assertFalse(q.enabled);assertTrue(q.matches("monthly",20L))
 }
 @Test fun mismatchedVatOrPlanRejected(){
   assertTrue(runCatching{PurchaseQuote.validate("single",2,200000,10000,210000,true)}.isFailure)
   assertTrue(runCatching{PurchaseQuote.validate("monthly",19,100000,10000,110000,true)}.isFailure)
 }

 @Test fun offerQuoteIsBoundToOfferAndCredits(){
   val q=PurchaseQuote.validate("offer",25,2000000,200000,2200000,true)
   assertTrue(q.matches("offer",25L))
   assertFalse(q.matches("offer",20L))
   assertFalse(q.matches("single",25L))
 }
 @Test fun offerQuoteRejectsInvalidVatAndUnitCount(){
   assertTrue(runCatching{PurchaseQuote.validate("offer",0,100000,10000,110000,true)}.isFailure)
   assertTrue(runCatching{PurchaseQuote.validate("offer",10,100000,5000,105000,true)}.isFailure)
 }
}
