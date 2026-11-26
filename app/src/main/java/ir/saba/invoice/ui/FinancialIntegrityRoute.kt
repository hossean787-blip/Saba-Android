package ir.saba.invoice.ui

/** Retry only a definite pre-mutation refusal. A timeout, 409, 429, 5xx, or other
 * denial is not evidence that the previous request did not execute. */
internal suspend fun <T> financialIntegrityRoute(legacy:suspend ()->T,protected:suspend ()->T):T {
    try { return legacy() }
    catch(e:CreditHttpException) {
        if(e.status!=403 || e.code!="integrity_required") throw e
        return protected()
    }
}
