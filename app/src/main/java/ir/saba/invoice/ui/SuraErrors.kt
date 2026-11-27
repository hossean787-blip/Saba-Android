package ir.saba.invoice.ui

import org.json.JSONObject
import java.net.HttpURLConnection
import java.io.ByteArrayOutputStream

/** Whitelisted public diagnostics: raw upstream text and secrets are never echoed. */
internal object SuraErrors {
    private val known=mapOf(
        "user_already_exists" to "این ایمیل قبلاً ثبت شده است؛ از صفحه ورود استفاده کنید.",
        "weak_password" to "رمز عبور شرایط امنیتی سرور را ندارد؛ رمز قوی‌تری انتخاب کنید.",
        "email_not_confirmed" to "ایمیل هنوز تأیید نشده است؛ صندوق ایمیل را بررسی کنید.",
        "legal_consent_required" to "نسخه جاری قوانین را بپذیرید؛ در صورت تداوم برنامه را به‌روز کنید.",
        "unauthorized" to "نشست ورود معتبر نیست؛ دوباره وارد حساب شوید.",
        "unverified_account" to "ایمیل حساب تأیید نشده است.",
        "integrity_required" to "این عملیات به تأیید اصالت نسخه رسمی نیاز دارد.",
        "integrity_rejected" to "مدرک اصالت درخواست تأیید نشد؛ عملیات مالی انجام نشد.",
        "challenge_invalid_or_used" to "چالش امنیتی معتبر نیست؛ همان درخواست را مجدداً بررسی کنید.",
        "integrity_unsupported" to "این دستگاه از حفاظت مالی سخت‌افزاری پشتیبانی نمی‌کند؛ با پشتیبانی تماس بگیرید.",
        "integrity_unavailable" to "نتیجه درخواست مالی نامشخص است؛ فقط همان درخواست را پیگیری کنید.",
        "integrity_pilot_only" to "مسیر مالی محافظت‌شده هنوز برای این حساب فعال نیست.",
        "rate_limited" to "محدودیت زمانی بررسی اصالت؛ حداقل ۳۰ ثانیه بعد همان درخواست را پیگیری کنید.",
        "forbidden" to "دسترسی کافی برای این عملیات ندارید.",
        "invalid_taxpayer" to "شناسه مؤدی معتبر نیست؛ مؤدی را دوباره انتخاب کنید.",
        "invalid_quantity" to "تعداد اعتبار خارج از محدوده مجاز است.",
        "invalid_transfer" to "مقدار یا مشخصات انتقال معتبر نیست.",
        "insufficient_credit" to "اعتبار کافی وجود ندارد.",
        "insufficient_balance" to "موجودی کافی نیست.",
        "insufficient_owner_credit" to "موجودی کیف‌پول مالک برای انتقال کافی نیست.",
        "insufficient_taxpayer_credit" to "اعتبار مؤدی برای صدور کافی نیست.",
        "balance_changed" to "موجودی تغییر کرده است؛ مجدداً استعلام بگیرید.",
        "invalid_receipt" to "شماره پیگیری، تاریخ یا فایل رسید معتبر نیست.",
        "invalid_image" to "تصویر رسید معتبر یا در محدوده حجم مجاز نیست.",
        "invalid_code" to "کد هدیه معتبر نیست.",
        "already_redeemed" to "هدیه قبلاً برای این حساب یا دستگاه دریافت شده است.",
        "invalid_device" to "شناسه دستگاه تأیید نشد.",
        "invalid_invoice_key" to "شناسه درخواست صورتحساب معتبر نیست.",
        "monthly_not_enabled" to "فروش اشتراک ماهانه فعال نیست.",
        "monthly_upgrade_required" to "برای خرید اشتراک ماهانه، برنامه را به نسخه جدید ارتقا دهید.",
        "no_monthly_credit" to "اشتراک فعال یا اعتبار ماهانه کافی نیست.",
        "card_not_enabled" to "فروش کارت‌به‌کارت فعال نیست.",
        "price_changed" to "قیمت تغییر کرده است؛ دوباره استعلام بگیرید.",
        "price_unavailable" to "تعرفه در سرور در دسترس نیست.",
        "invalid_total" to "مبلغ سفارش معتبر نیست.",
        "invalid_plan" to "نوع خرید یا تعداد اعتبار معتبر نیست.",
        "offer_unavailable" to "این طرح دیگر فعال نیست یا مشخصات آن تغییر کرده است؛ فهرست طرح‌ها را به‌روزرسانی کنید.",
        "not_found" to "رکورد یافت نشد یا به این حساب تعلق ندارد.",
        "state_conflict" to "وضعیت درخواست تغییر کرده است؛ اطلاعات را تازه کنید.",
        "duplicate_payment" to "پرداخت یا رسید قبلاً تأیید شده است؛ دوباره پرداخت نکنید.",
        "bank_confirmation_required" to "تطبیق مبلغ و شماره مرجع بانکی الزامی است.",
        "invalid_review" to "اطلاعات بررسی رسید معتبر نیست.",
        "idempotency_conflict" to "شناسه درخواست با اطلاعات دیگری استفاده شده است؛ همان درخواست را بررسی کنید.",
        "invalid_request" to "ساختار درخواست معتبر نیست.",
        "not_configured" to "تنظیمات سرویس روی سرور کامل نیست.",
        "service_unavailable" to "سرویس موقتاً در دسترس نیست.",
        "database_failed" to "عملیات پایگاه داده تأیید نشد.",
        "invalid_response" to "ساختار پاسخ سرور معتبر نیست.",
        "consume_unavailable" to "مصرف اعتبار تأیید نشد؛ همان درخواست را پیگیری کنید.",
        "balance_unavailable" to "استعلام موجودی تأیید نشد.",
        "redeem_failed" to "ثبت هدیه تأیید نشد؛ مانده را استعلام کنید.",
        "promo_configuration_error" to "تنظیمات هدیه ناقص است."
    )
    fun message(status:Int, code:String?=null):String {
        val safe=code?.takeIf{known.containsKey(it)}
        val text=safe?.let{known[it]} ?: when(status){
            400 -> "اطلاعات درخواست معتبر نیست؛ فرم را بررسی کنید."
            401 -> "نشست ورود معتبر نیست؛ دوباره وارد شوید."
            403 -> "دسترسی کافی برای این عملیات ندارید."
            404 -> "اطلاعات یا سرویس یافت نشد."
            409 -> "وضعیت درخواست تغییر کرده است؛ دوباره استعلام بگیرید."
            413 -> "حجم فایل یا درخواست بیش از حد مجاز است."
            422 -> "اطلاعات درخواست شرایط لازم را ندارد."
            408,504 -> "مهلت پاسخ سرور پایان یافت؛ نتیجه همان درخواست را بررسی کنید."
            429 -> "تعداد درخواست‌ها بیش از حد مجاز است؛ کمی بعد تلاش کنید."
            in 500..599 -> "اختلال موقت سرور؛ نتیجه درخواست را دوباره بررسی کنید."
            else -> "پاسخ سرویس قابل تأیید نیست؛ با پشتیبانی تماس بگیرید."
        }
        return "$text [${safe ?: "HTTP-$status"}]"
    }
    fun readCode(c:HttpURLConnection):String?=runCatching {
        val raw=c.errorStream?.use{ input ->
            val out=ByteArrayOutputStream();val b=ByteArray(1024)
            while(true){val n=input.read(b);if(n<0)break;require(out.size()+n<=16384);out.write(b,0,n)}
            out.toString("UTF-8")
        }?:return@runCatching null
        JSONObject(raw).optString("error").takeIf{known.containsKey(it)}
    }.getOrNull()
    fun transport(e:Throwable):String? {
        if(e is kotlinx.coroutines.CancellationException)throw e
        if(e is SuraServiceException || e is CreditHttpException || e is ir.saba.invoice.auth.AuthGatewayException)return e.message
        return when(e){
            is java.net.UnknownHostException -> "نام سرور قابل دسترسی نیست؛ اینترنت و DNS را بررسی کنید. [NET-DNS]"
            is java.net.SocketTimeoutException -> "زمان پاسخ پایان یافت؛ اگر عملیات مالی است همان درخواست را پیگیری کنید. [NET-TIMEOUT]"
            is javax.net.ssl.SSLException -> "اتصال امن تأیید نشد؛ تاریخ دستگاه و شبکه را بررسی کنید. [NET-TLS]"
            is java.net.ConnectException -> "اتصال به سرور برقرار نشد؛ اینترنت را بررسی کنید. [NET-CONNECT]"
            is java.io.FileNotFoundException -> "فایل پیدا نشد یا مجوز خواندن ندارد. [FILE-NOT-FOUND]"
            is SecurityException -> "مجوز دسترسی کافی نیست. [ACCESS-DENIED]"
            is android.database.sqlite.SQLiteFullException -> "فضای ذخیره کافی نیست؛ فضای دستگاه را آزاد کنید. [DB-FULL]"
            is android.database.sqlite.SQLiteConstraintException -> "اطلاعات با محدودیت ثبت سازگار نیست. [DB-CONSTRAINT]"
            is android.database.sqlite.SQLiteDatabaseCorruptException -> "بانک اطلاعاتی آسیب دیده است؛ داده را حذف نکنید و با پشتیبانی تماس بگیرید. [DB-CORRUPT]"
            is android.database.sqlite.SQLiteException -> "خواندن یا ذخیره اطلاعات محلی ناموفق بود. [DB-IO]"
            is org.json.JSONException -> "ساختار پاسخ یا فایل معتبر نیست. [DATA-FORMAT]"
            is java.io.IOException -> "خواندن اطلاعات یا ارتباط ناموفق بود؛ شبکه و فایل را بررسی کنید. [IO-ERROR]"
            else -> null
        }
    }
    fun describe(e:Throwable,fallback:String)=transport(e)?:fallback
}
internal class SuraServiceException(status:Int,code:String?=null):IllegalStateException(SuraErrors.message(status,code))
