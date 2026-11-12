from pathlib import Path
import re, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
android='{http://schemas.android.com/apk/res/android}'
app=manifest.find('application')
assert app.get(android+'allowBackup')=='false'
assert app.get(android+'usesCleartextTraffic')=='false'
assert len(manifest.findall('uses-permission'))==1
assert manifest.findall('uses-permission')[0].get(android+'name')=='android.permission.INTERNET'
assert (ROOT/'app/src/main/assets/admin/index.html').exists()
html=(ROOT/'app/src/main/assets/admin/index.html').read_text(encoding='utf8')
java=(ROOT/'app/src/main/java/ir/sabagostar/suramanager/AdminActivity.java').read_text(encoding='utf8')
js=(ROOT/'app/src/main/assets/admin/reports.js').read_text(encoding='utf8')
manager=(ROOT/'app/src/main/assets/admin/manager.js').read_text(encoding='utf8')
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
assert 'versionCode = 4' in (ROOT/'app/build.gradle.kts').read_text()
print('PASS: isolated application, secure WebView, CSP, no private API key, file export')
