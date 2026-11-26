package ir.saba.invoice.security

/** Observation only; never use this client cache as authorization. A rejected proof
 * is not necessarily a counterfeit APK (hardware, trust and boot policy can also reject it). */
internal enum class IntegrityOutcome {
    VERIFIED, PROOF_REJECTED, RATE_LIMITED, UNAVAILABLE, SESSION_INVALID, UNSUPPORTED
}
internal data class IntegrityReport(
    val outcome:IntegrityOutcome,
    val checkedAt:Long,
    val lastVerifiedAt:Long?=null,
    val reused:Boolean=false
) {
    fun displayText():String {
        fun time(ms:Long)=java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.SHORT,java.text.DateFormat.MEDIUM).format(java.util.Date(ms))
        return when(outcome) {
            IntegrityOutcome.VERIFIED -> "اصالت برنامه و دستگاه تأیید شد. آخرین تأیید: "+time(checkedAt)+
                if(reused) " (نتیجه معتبر اخیر؛ بررسی دوباره ضروری نیست.)" else ""
            IntegrityOutcome.PROOF_REJECTED -> "مدرک اصالت در بررسی جدید پذیرفته نشد؛ این نتیجه به‌تنهایی نشانه جعلی بودن برنامه نیست."
            IntegrityOutcome.RATE_LIMITED -> "بررسی مجدد موقتاً محدود است؛ حداقل ۳۰ ثانیه بعد دوباره بررسی کنید."
            IntegrityOutcome.UNAVAILABLE -> "امکان بررسی اصالت فعلاً فراهم نیست؛ شبکه، سرور یا قابلیت امنیتی دستگاه را بررسی کنید."
            IntegrityOutcome.SESSION_INVALID -> "نشست ورود معتبر نیست؛ دوباره وارد حساب شوید."
            IntegrityOutcome.UNSUPPORTED -> "این دستگاه یا نسخه برنامه از آزمون فعلی پشتیبانی نمی‌کند."
        } + (if(outcome in setOf(IntegrityOutcome.RATE_LIMITED,IntegrityOutcome.UNAVAILABLE) && lastVerifiedAt!=null)
            "\nآخرین تأیید موفق: "+time(lastVerifiedAt)+"؛ وضعیت جدید هنوز مشخص نیست." else "")
    }
}
internal fun integrityHttpOutcome(code:Int,status:String):IntegrityOutcome=when {
    code==200 && status=="verified" -> IntegrityOutcome.VERIFIED
    code==422 && status=="not_verified" -> IntegrityOutcome.PROOF_REJECTED
    code==429 -> IntegrityOutcome.RATE_LIMITED
    code==401 -> IntegrityOutcome.SESSION_INVALID
    else -> IntegrityOutcome.UNAVAILABLE
}

/** Memory only, monotonic cooldown, isolated by hash of current authenticated session.
 * Reuse only a positive observation for 30 seconds; later inconclusive attempts retain
 * the historical timestamp, never claim a fresh verification. */
internal class IntegrityObservationState {
    private var session:String?=null
    private var report:IntegrityReport?=null
    private var recordedAt:Long=0
    fun recent(sessionHash:String,elapsed:Long):IntegrityReport? {
        if(session!=sessionHash){session=sessionHash;report=null;recordedAt=0}
        val prior=report?:return null
        return if(prior.outcome==IntegrityOutcome.VERIFIED && elapsed>=recordedAt &&
            elapsed-recordedAt<30_000L) prior.copy(reused=true) else null
    }
    fun record(sessionHash:String,outcome:IntegrityOutcome,elapsed:Long,wall:Long):IntegrityReport {
        recent(sessionHash,elapsed) // also resets history on session change
        val last=when(outcome) {
            IntegrityOutcome.VERIFIED -> wall
            IntegrityOutcome.RATE_LIMITED,IntegrityOutcome.UNAVAILABLE -> report?.lastVerifiedAt
            else -> null
        }
        return IntegrityReport(outcome,wall,last).also {report=it;recordedAt=elapsed}
    }
}
