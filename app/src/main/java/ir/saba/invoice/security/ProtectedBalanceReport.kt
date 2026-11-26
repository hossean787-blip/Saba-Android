package ir.saba.invoice.security

internal enum class ProtectedBalanceOutcome { VERIFIED, REJECTED, RATE_LIMITED, SESSION_INVALID, UNAVAILABLE, UNSUPPORTED }
internal data class ProtectedBalanceReport(val outcome:ProtectedBalanceOutcome,val remaining:Long?=null) {
    fun displayText():String = when(outcome) {
        ProtectedBalanceOutcome.VERIFIED -> "استعلام محافظت‌شده تأیید شد. اعتبار مؤدی: ${remaining}؛ هیچ اعتباری مصرف نشد."
        ProtectedBalanceOutcome.REJECTED -> "مدرک اصالت این درخواست پذیرفته نشد؛ استعلام امن انجام نشد."
        ProtectedBalanceOutcome.RATE_LIMITED -> "محدودیت زمانی بررسی اصالت؛ حداقل ۳۰ ثانیه بعد دوباره تلاش کنید."
        ProtectedBalanceOutcome.SESSION_INVALID -> "نشست ورود معتبر نیست؛ دوباره وارد حساب شوید."
        ProtectedBalanceOutcome.UNAVAILABLE -> "استعلام امن تأیید نشد؛ اینترنت یا سرویس را بررسی کنید."
        ProtectedBalanceOutcome.UNSUPPORTED -> "دستگاه از این بررسی پشتیبانی نمی‌کند."
    }
}
internal fun protectedBalanceResult(code:Int,mode:String,remaining:Any?,error:String):ProtectedBalanceReport {
    val amount=when(remaining) {
        is Int -> remaining.toLong()
        is Long -> remaining
        else -> null
    }
    val outcome=when {
        code==200 && mode=="enforce" && amount!=null && amount in 0..9_007_199_254_740_991L && error.isBlank() -> ProtectedBalanceOutcome.VERIFIED
        code==401 -> ProtectedBalanceOutcome.SESSION_INVALID
        code==429 -> ProtectedBalanceOutcome.RATE_LIMITED
        code==403 && error in setOf("integrity_required","integrity_rejected","challenge_invalid_or_used") -> ProtectedBalanceOutcome.REJECTED
        else -> ProtectedBalanceOutcome.UNAVAILABLE
    }
    return ProtectedBalanceReport(outcome,amount.takeIf { outcome==ProtectedBalanceOutcome.VERIFIED })
}
