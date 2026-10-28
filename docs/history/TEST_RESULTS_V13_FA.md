# گزارش آزمون نسخه ۱۳ صبا

تاریخ اجرا: 2026-10-24

## آزمون اجراشده

فرمان:

```bash
bash tools/run-domain-smoke-tests.sh
```

نتیجه واقعی اجرا:

```text
PASS: net/tax/total
PASS: HALF_UP tax rounding
PASS: multiply overflow rejected
PASS: zero quantity rejected
PASS: annual unlimited license accepted
PASS: monthly fixed count rejected
PASS: zero fixed count rejected
PASS: transfer decreases master balance
PASS: allocation over purchased total rejected
PASS: quota sum overflow rejected
PASS: consume only selected company
RESULT: 11/11 passed
```

این آزمون‌ها با Kotlin/JVM مستقل اجرا شده‌اند. آن‌ها تست اندروید، Room یا شبکه نیستند.

## بررسی ساخت اندروید

در محیط اجرای فعلی:
- `gradle` نصب/قابل‌شناسایی نیست.
- متغیر `ANDROID_HOME` تنظیم نشده است.
- بنابراین کامپایل پروژه اندروید، تولید APK و تست‌های JUnit/Room قابل اجرا نبودند.

## مواردی که همچنان باید حل شوند

1. فراهم‌کردن JDK 17، Gradle Wrapper سازگار و Android SDK.
2. اجرای `testDebugUnitTest` و `assembleDebug` در محیط سازگار.
3. رفع خطاهای واقعی کامپایل/تست که فقط پس از اجرای Gradle قابل تعیین هستند.
4. افزودن تست یکپارچه Room برای تراکنش انتقال سهمیه و نهایی‌سازی فاکتور.
5. اتصال رسمی سامانه مؤدیان و کنترل مجوز تجاری سمت سرور هنوز پیاده‌سازی نشده است.

## وضعیت تایید

نسخه ۱۳ فقط از نظر آزمون مستقل منطق و سلامت فایل ZIP بررسی شده است. «کامپایل‌شده»، «آزمایش‌شده روی گوشی» یا «آماده فروش» نیست.
