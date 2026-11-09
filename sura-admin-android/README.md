# سورا مدیر | SURA Manager — اپ مستقل اندروید

**نسخه:** `0.1.0-beta` — شناسه بسته `ir.sabagostar.suramanager`، مستقل از اپ مشتری `ir.saba.invoice`.

## اتصال

- رابط فعلی سورا در `app/src/main/assets/admin/index.html` همراه با خروجی گزارش آفلاین در `reports.js` قرار گرفته است.
- سرویس رسمی **همان پروژه سورا**: `https://fiiitzfbdjzpmepzjsjw.supabase.co`.
- کلید `sb_publishable_...` عمومی است؛ **هیچ کلید service_role یا رمز مدیریتی در APK نیست**.
- مدیر با حساب فعلی Supabase Auth وارد می‌شود؛ `sura-admin-api` هویت و سطح دسترسی `sura_admin_roles` را روی سرور بررسی می‌کند.
- اپ فقط `appassets.androidplatform.net` (محتوای داخلی) و دامنه اصلی Supabase را در WebView بارگذاری می‌کند؛ درخواست HTTP، میزبان دیگر و خطای TLS مسدود است.
- `FLAG_SECURE` برای محافظت از محتوا، عدم پشتیبان‌گیری خودکار و رد کوکی شخص ثالث فعال است.

## امکانات

داشبورد، کاربران/مودیان، کیف پول و انتقال، اصلاح اعتبار با ممیزی، بسته/قیمت، پرداخت‌ها، گزارش ریالی، هدیه اعتبار، تنظیمات و نقش‌ها. خروجی Excel واقعی (`.xlsx`) و CSV بدون وابستگی به CDN انجام می‌شود؛ Android Save As از Storage Access Framework استفاده می‌کند.

## ساخت

در ریشه مخزن اصلی، با JDK 17 و Android SDK 35:

```bash
./gradlew --no-daemon -p sura-admin-android :app:assembleDebug
```

APK آزمایشی در `sura-admin-android/app/build/outputs/apk/debug/app-debug.apk` ساخته می‌شود. کلید Debug مناسب انتشار تجاری نیست و امضای مختلف ممکن است مانع نصب ارتقایی شود.

مسیر ساخت ابری: `.github/workflows/sura-manager-android.yml`؛ پس از موفقیت، artifact با نام `SURA-Manager-debug-APK` ایجاد می‌شود.

## سازگاری WebView قدیمی

فایل‌های خام رابط در `sources/` نگهداری شده‌اند و کدهایی که در APK قرار می‌گیرند به سطح جاوااسکریپت **ES2017** تبدیل شده‌اند تا WebViewهای قدیمی‌تر مانند Chrome/WebView 74 اندروید ۱۰ نیز بتوانند آن‌ها را اجرا کنند. برای اعمال تغییرات به سورس HTML/JS، ابتدا `npm install` و سپس `npm run web:build` را اجرا کنید؛ فایل‌های تولیدی زیر `app/src/main/assets/admin/` را ثبت کنید.

## آزمون‌ها و محدودیت انتشار

```bash
python3 tests/static_guard.py
node tests/report_zip_test.cjs
python3 tests/browser_smoke.py   # نیازمند Playwright و Chromium؛ با داده شبیه‌سازی‌شده
```

آزمون مرورگر **به معنای نصب روی دستگاه یا ورود واقعی مدیر نیست**. پیش از انتشار تجاری، ورود با حساب واقعی مدیر، انتقال آزمایشی کنترل‌شده با بازگشت داده، تست APK در دستگاه هدف، ارزیابی WebView و بررسی امنیت تولید انجام شود. برای تغییر قیمت یا موجودی کاربران واقعی از داده آزمایشی استفاده نشود.

## استقلال

این برنامه داخل پوشه جدا نگهداری می‌شود، به‌هیچ‌وجه کد اپ اصلی `:app` را تغییر نمی‌دهد، و شناسه نصب جداگانه دارد. ویرایش کد پنل مبنا فقط با انتقال تغییر تأییدشده انجام شود.
