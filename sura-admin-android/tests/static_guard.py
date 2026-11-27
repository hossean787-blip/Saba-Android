from pathlib import Path
import re, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
android='{http://schemas.android.com/apk/res/android}'
app=manifest.find('application')
assert app.get(android+'allowBackup')=='false'
assert app.get(android+'usesCleartextTraffic')=='false'
assert {p.get(android+'name') for p in manifest.findall('uses-permission')}=={'android.permission.INTERNET','android.permission.USE_BIOMETRIC'}
assert (ROOT/'app/src/main/assets/admin/index.html').exists()
html=(ROOT/'app/src/main/assets/admin/index.html').read_text(encoding='utf8')
java=(ROOT/'app/src/main/java/ir/sabagostar/suramanager/AdminActivity.java').read_text(encoding='utf8')
js=(ROOT/'app/src/main/assets/admin/reports.js').read_text(encoding='utf8')
manager=(ROOT/'app/src/main/assets/admin/manager.js').read_text(encoding='utf8')
manager_source=(ROOT/'sources/manager.source.js').read_text(encoding='utf8')
source_html=(ROOT/'sources/index.source.html').read_text(encoding='utf8')
assert 'script-src \'self\' \'unsafe-inline\'' in html
assert 'connect-src https://fiiitzfbdjzpmepzjsjw.supabase.co' in html
assert 'https://cdnjs' not in html and 'https://fonts.googleapis.com' not in html
assert '<script src="/assets/admin/reports.js"></script>' in html
assert '<script src="/assets/admin/manager.js"></script>' in html
assert 'SuraReports.makeXlsx' in html and 'SuraReports.download' in html
import re
inline_scripts = re.findall(r'<script>([\s\S]*?)</script>', html)
assert len(inline_scripts) == 1
for js_source in [inline_scripts[0],js,manager]:
    assert '?.' not in js_source and '??' not in js_source, 'Old Android WebViews cannot parse modern optional syntax'
assert 'sb_publishable_' in html
assert not re.search(r'(?:eyJhbGci|sb_secret_|SUPABASE_SERVICE_ROLE_KEY)\S{15,}',html)
assert 'setAllowFileAccess(false)' in java and 'setAllowContentAccess(false)' in java
assert 'SslErrorHandler' in java and 'handler.cancel()' in java
assert 'FLAG_SECURE' in java and 'setAcceptThirdPartyCookies(web, false)' in java
assert '"https".equals(uri.getScheme()) && API_HOST.equals(uri.getHost())' in java
assert 'ACTION_CREATE_DOCUMENT' in java
assert 'zipFiles' in js and 'makeXlsx' in js
assert 'renderDirectory' in manager and 'managerFetch' in manager
assert 'admin/manager.js' in java
assert 'versionCode = 11' in (ROOT/'app/build.gradle.kts').read_text()
assert 'class="mobile-bottom-nav"' in html and 'data-page="dashboard"' in html
assert 'safe-area-inset-bottom' in html and 'min-width:620px' in html
assert 'openSelectedGift' in manager and 'saveSelectedGift' in manager
assert 'owner-search' in (ROOT/'backend/sura-manager-api.ts').read_text(encoding='utf8')
assert 'user_metadata' in (ROOT/'backend/sura-manager-api.ts').read_text(encoding='utf8')
assert 'searchGiftOwners' in manager_source and 'searchGiftOwners' in manager
assert 'نام و نام خانوادگی' in manager_source and 'ایمیل مالک' in manager_source
assert '--sura-system-bottom-inset' in source_html and '--sura-system-bottom-inset' in html
assert 'applySystemBottomInset' in java and 'view.setPadding(bar.left, bar.top, bar.right, 0)' in java

assert 'await appendSalesOffers(c);' in html and 'await appendSalesOffers(c);' in source_html
assert 'Live sales offers' in manager and 'Live sales offers' in manager_source
assert 'admin-offer-save' in manager_source and 'admin-offers' in manager_source
assert 'admin-offer-save' in manager and 'admin-offers' in manager
assert 'offerId' in (ROOT.parent/'supabase/functions/sura-card-transfer/index.ts').read_text(encoding='utf8')
assert 'sura_sales_offers' in (ROOT.parent/'supabase/migrations/20261126225201_sura_live_sales_offers.sql').read_text(encoding='utf8')
purchase=(ROOT.parent/'app/src/main/java/ir/saba/invoice/ui/CreditPurchaseScreen.kt').read_text(encoding='utf8')
assert 'CardTransferApi.call(accessToken,"offers")' in purchase and 'طرح‌های فروش سورا' in purchase
assert 'kind=="offer"' in (ROOT.parent/'app/src/main/java/ir/saba/invoice/ui/CardTransferPanel.kt').read_text(encoding='utf8')

print('PASS: isolated application, secure WebView, CSP, live sales offers and file export')

vault=(ROOT/'app/src/main/java/ir/sabagostar/suramanager/ManagerLoginVault.java').read_text(encoding='utf8')
assert 'AndroidKeyStore' in vault and 'AES/GCM/NoPadding' in vault and 'setUserAuthenticationRequired(true)' in vault
assert 'setUserAuthenticationParameters(0,KeyProperties.AUTH_BIOMETRIC_STRONG)' in vault and 'BiometricPrompt.CryptoObject' in vault
assert 'setInvalidatedByBiometricEnrollment(true)' in vault
assert 'saveVerifiedLogin(email,pass,me,fromBio,rememberEmail,rememberBio)' in html
assert 'if(nativeVault())return;' in html and 'setSavePassword(false)' in java
assert 'id="bio-login"' in html and 'id="forget-login"' in html
print('PASS: biometric vault source guards and native session restore isolation')

assert 'await appendMonthlyPrice(c);' not in html, 'Subscription sale must remain hidden in manager UI'
