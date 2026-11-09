package ir.sabagostar.suramanager;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.WindowInsets;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Dedicated SURA manager: local trusted UI, strictly allow-listed production API. */
public final class AdminActivity extends Activity {
    private static final String APP_HOST = "appassets.androidplatform.net";
    private static final String API_HOST = "fiiitzfbdjzpmepzjsjw.supabase.co";
    private static final String ENTRY = "https://" + APP_HOST + "/assets/admin/index.html";
    private static final int SAVE_FILE = 1024;
    private static final int MAX_EXPORT_BYTES = 10 * 1024 * 1024;
    private WebView web;
    private byte[] pendingBytes;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Color.rgb(13,17,23));
        getWindow().setNavigationBarColor(Color.rgb(13,17,23));
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(13,17,23));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        boolean isDebuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        WebView.setWebContentsDebuggingEnabled(isDebuggable);
        web.addJavascriptInterface(new ExportBridge(), "SuraNative");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new SecureClient());
        if (Build.VERSION.SDK_INT >= 35) {
            web.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bar = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bar.left, bar.top, bar.right, bar.bottom);
                return insets;
            });
        }
        setContentView(web);
        web.loadUrl(ENTRY);
    }

    private final class SecureClient extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return !isAppUri(request.getUrl());
        }
        @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if ("https".equals(uri.getScheme()) && APP_HOST.equals(uri.getHost())) {
                String path = uri.getEncodedPath();
                if ("/assets/admin/index.html".equals(path)) return asset("admin/index.html", "text/html");
                if ("/assets/admin/reports.js".equals(path)) return asset("admin/reports.js", "application/javascript");
                return denied();
            }
            if ("https".equals(uri.getScheme()) && API_HOST.equals(uri.getHost()) && uri.getPort() == -1) {
                return null; // Android WebView performs TLS verification for the trusted Supabase host.
            }
            return denied();
        }
        @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel(); // Never bypass a certificate error, including in debug builds.
        }
        private WebResourceResponse asset(String path, String type) {
            try {
                InputStream in = getAssets().open(path);
                WebResourceResponse r = new WebResourceResponse(type, "UTF-8", in);
                r.setResponseHeaders(java.util.Collections.singletonMap("Cache-Control", "no-store"));
                return r;
            } catch (IOException e) { return denied(); }
        }
        private WebResourceResponse denied() {
            return new WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden",
                java.util.Collections.emptyMap(), new java.io.ByteArrayInputStream(new byte[0]));
        }
    }
    private static boolean isAppUri(Uri uri) {
        return "https".equals(uri.getScheme()) && APP_HOST.equals(uri.getHost()) && uri.getPort() == -1;
    }

    public final class ExportBridge {
        @JavascriptInterface public void saveFile(String name, String mime, String contentBase64) {
            if (name == null || mime == null || contentBase64 == null) return;
            if (!name.matches("sura-report-[0-9]{4}-[0-9]{2}-[0-9]{2}\\.(csv|xlsx)")) return;
            String expected = name.endsWith(".csv") ? "text/csv" :
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            if (!expected.equals(mime)) return;
            if (contentBase64.length() > MAX_EXPORT_BYTES * 4L / 3 + 64 || !contentBase64.matches("[A-Za-z0-9+/=]+")) return;
            final byte[] bytes;
            try { bytes = Base64.decode(contentBase64, Base64.DEFAULT); }
            catch (IllegalArgumentException e) { return; }
            if (bytes.length == 0 || bytes.length > MAX_EXPORT_BYTES) return;
            if (name.endsWith(".xlsx") && (bytes.length < 4 || bytes[0] != 80 || bytes[1] != 75 || bytes[2] != 3 || bytes[3] != 4)) return;
            runOnUiThread(() -> {
                if (pendingBytes != null || isFinishing()) return;
                pendingBytes = bytes;
                Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                save.setType(expected);
                save.addCategory(Intent.CATEGORY_OPENABLE);
                save.putExtra(Intent.EXTRA_TITLE, name);
                try { startActivityForResult(save, SAVE_FILE); }
                catch (ActivityNotFoundException e) { pendingBytes = null; alert("برنامه ذخیره فایل یافت نشد"); }
            });
        }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SAVE_FILE) return;
        byte[] bytes = pendingBytes;
        pendingBytes = null;
        if (resultCode != RESULT_OK || bytes == null || data == null || data.getData() == null) return;
        try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            if (out == null) throw new IOException("No output stream");
            out.write(bytes);
            alert("گزارش با موفقیت ذخیره شد");
        } catch (IOException e) { alert("خطا در ذخیره گزارش"); }
    }
    private void alert(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override public void onBackPressed() {
        if (web == null) { super.onBackPressed(); return; }
        web.evaluateJavascript("(function(){if(document.body.classList.contains('drawer-open')){closeDrawer();return true;}if(document.getElementById('modal-overlay')?.classList.contains('open')){closeModal();return true;}if(typeof currentPage!=='undefined'&&currentPage!=='dashboard'&&document.getElementById('app').style.display!=='none'){showPage('dashboard');return true;}return false;})()", result -> {
            if (!"true".equals(result)) finish();
        });
    }
    @Override protected void onPause() { if (web != null) web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onDestroy() {
        if (web != null) { web.removeJavascriptInterface("SuraNative"); web.destroy(); web = null; }
        pendingBytes = null;
        super.onDestroy();
    }
}
