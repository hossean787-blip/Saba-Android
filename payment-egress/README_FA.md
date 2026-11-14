# مسیر IP ثابت پرداخت زرین‌پال — سورا

**بخش قابل نصب، اما هنوز روی سرور واقعی فعال نشده است.** این ماژول هیچ هزینه یا درگاهی را به‌صورت خودکار ایجاد نمی‌کند.

## ضرورت
از شهریور ۱۴۰۵ زرین‌پال برای ساخت تراکنش IP مجاز اعلام کرده است. Supabase Edge Functions خروجی IP ثابت ندارد؛ بنابراین `merchant_configured=true` به معنای آمادگی برای فروش نیست.

## معماری
Android -> Supabase Auth/Edge Function -> HTTPS reverse proxy روی VPS با IP ثابت -> زرین‌پال. تأیید پرداخت و افزایش اعتبار همچنان صرفاً داخل سرور اصلی سورا و تراکنش اتمی PostgreSQL انجام می‌شود. سرور proxy هیچ دیتابیس اعتبار ندارد.

## اجرای سرور ثابت
1. VPS با IPv4 عمومی ثابت و دامنه HTTPS واقعی تهیه و IP آن را در پیشخان زرین‌پال مجاز کنید. هیچ خریدی بدون تأیید مالک مجاز نیست.
2. Node 22 یا جدیدتر را نصب کنید، فایل `gateway_proxy.mjs` را در مسیر محافظت‌شده روی VPS قرار دهید.
3. سه متغیر محیطی `SURA_EGRESS_SECRET` (رشته تصادفی دست‌کم ۳۲ کاراکتر)، `ZARINPAL_MERCHANT_ID`، `SURA_ALLOWED_CALLBACK` را فقط در سرویس امن سرور تنظیم کنید؛ مقدار callback دقیق: `https://fiiitzfbdjzpmepzjsjw.supabase.co/functions/v1/sura-zarinpal/callback`.
4. `node payment-egress/gateway_proxy.mjs` روی `127.0.0.1:8787` اجرا شود. روی Nginx TLS، فقط مسیر `/gateway/` را به پورت محلی 8787 فوروارد کنید. پورت داخلی را به عموم باز نکنید. گواهی معتبر TLS و rate limit اضافه کنید.
5. متغیرهای محافظت‌شده `SURA_PAYMENT_EGRESS_URL=https://payments.example.com/` و `SURA_PAYMENT_EGRESS_SECRET` با همان رمز را در Edge Functions پروژه سورا تنظیم کنید. هرگز رمز را در APK، GitHub یا اسناد عمومی ذخیره نکنید.
6. آزمون بدون پرداخت: `node --test payment-egress/gateway_proxy.test.mjs` و `deno check backend/supabase/functions/sura-zarinpal/index.ts`.
7. تراکنش کم‌مبلغ واقعی، callback، verify 100/101، استعلام پس از بازکردن دوباره برنامه و جلوگیری از شارژ تکراری باید با رضایت مالک آزمایش شوند.

**Fail closed:** اگر فقط یکی از تنظیمات proxy پر شود یا URL ناامن باشد، تابع پرداخت خطا می‌دهد و به مسیر بدون IP ثابت برنمی‌گردد. اگر هر دو خالی باشند، برای سازگاری با نسخه‌های قدیمی مسیر مستقیم فعلی استفاده می‌شود؛ آن مسیر لزوماً از الزامات IP زرین‌پال عبور نمی‌کند.

مراجع: https://supabase.com/kb/troubleshooting/why-supabase-edge-functions-cannot-provide-static-egress-ips-for-whitelisting-3d78b0
