package ir.saba.invoice.ui
import org.junit.Assert.*
import org.junit.Test
class SuraErrorsTest {
 @Test fun distinctKnownReasons(){
    assertTrue(SuraErrors.message(409,"duplicate_payment").contains("قبلاً"))
    assertTrue(SuraErrors.message(403,"forbidden").contains("دسترسی"))
    assertTrue(SuraErrors.message(409,"price_changed").contains("قیمت"))
 }
 @Test fun neverEchoUnknownServerText(){
    assertFalse(SuraErrors.message(500,"secret_token").contains("secret_token"))
    assertTrue(SuraErrors.message(500,"secret_token").contains("HTTP-500"))
 }
 @Test fun timeoutDoesNotExposeException(){
    val message=SuraErrors.describe(java.net.SocketTimeoutException("secret_token"),"fallback")
    assertTrue(message.contains("NET-TIMEOUT"));assertFalse(message.contains("secret_token"))
 }
 @Test fun cancellationPropagates(){
    try{SuraErrors.describe(kotlinx.coroutines.CancellationException(),"fallback");fail("cancel swallowed")}catch(_:kotlinx.coroutines.CancellationException){}
 }
}
