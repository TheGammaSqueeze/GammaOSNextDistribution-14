/*
 * GammaBrowser - a lightweight, controller-first WebView browser for the GammaOS
 * Nano XMB "Internet Browser" / "Internet Search" items. Touch is supported too,
 * but the chrome is built for the gamepad: every control is focusable for d-pad
 * traversal and the face/shoulder buttons drive back/forward/reload/address.
 */
package com.gammaos.browser;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;

public class MainActivity extends Activity {

    private static final String HOME_URL = "https://www.google.com";

    private WebView mWeb;
    private EditText mAddress;
    private ProgressBar mProgress;
    private ImageButton mBack, mForward, mReload;
    private boolean mRetriedOnce = false;   // one-shot retry on a cold-start network error

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mWeb = findViewById(R.id.webview);
        mAddress = findViewById(R.id.address);
        mProgress = findViewById(R.id.progress);
        mBack = findViewById(R.id.btn_back);
        mForward = findViewById(R.id.btn_forward);
        mReload = findViewById(R.id.btn_reload);

        configureWebView();

        mBack.setOnClickListener(v -> { if (mWeb.canGoBack()) mWeb.goBack(); });
        mForward.setOnClickListener(v -> { if (mWeb.canGoForward()) mWeb.goForward(); });
        mReload.setOnClickListener(v -> mWeb.reload());
        findViewById(R.id.btn_go).setOnClickListener(v -> commitAddress());

        mAddress.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN)) {
                commitAddress();
                return true;
            }
            return false;
        });

        if (savedInstanceState != null) {
            mWeb.restoreState(savedInstanceState);
        } else {
            loadFromIntent(getIntent());
        }
        // Start with the page focused so the d-pad drives the content; the user
        // reaches the toolbar by pressing Up at the top or the address button (X).
        mWeb.requestFocus();
    }

    private void configureWebView() {
        android.webkit.WebSettings s = mWeb.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setMediaPlaybackRequiresUserGesture(true);

        mWeb.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest req) {
                Uri uri = req.getUrl();
                String scheme = uri.getScheme();
                if (scheme != null && (scheme.equals("http") || scheme.equals("https"))) {
                    return false;   // keep web navigation inside the WebView
                }
                // Hand non-web schemes (mailto:, tel:, intent:, market:, ...) to the system.
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
                return true;
            }
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (!mAddress.hasFocus()) mAddress.setText(url);
                mProgress.setVisibility(View.VISIBLE);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                if (!mAddress.hasFocus()) mAddress.setText(url);
                mProgress.setVisibility(View.GONE);
                mBack.setEnabled(mWeb.canGoBack());
                mForward.setEnabled(mWeb.canGoForward());
            }
            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest req,
                                        android.webkit.WebResourceError err) {
                // In nano minimal_boot the WebView network stack can come up before
                // connectivity settles, so the very first load may fail with
                // ERR_SOCKET_NOT_CONNECTED / ERR_INTERNET_DISCONNECTED. Retry the main
                // frame once after a short delay so the first open is clean.
                if (req.isForMainFrame() && !mRetriedOnce) {
                    int c = err.getErrorCode();
                    if (c == ERROR_CONNECT || c == ERROR_HOST_LOOKUP || c == ERROR_IO
                            || c == ERROR_TIMEOUT) {
                        mRetriedOnce = true;
                        final String u = req.getUrl().toString();
                        view.postDelayed(() -> { if (mWeb != null) mWeb.loadUrl(u); }, 1500);
                    }
                }
            }
        });

        mWeb.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                mProgress.setProgress(newProgress);
                mProgress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (!TextUtils.isEmpty(title)) setTitle(title);
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        loadFromIntent(intent);
    }

    private void loadFromIntent(Intent intent) {
        String url = null;
        // nano launches us via the LAUNCHER intent (no data) and hands the URL through
        // a prop, since it must exit/release the DRM display to make us visible.
        String fromProp = android.os.SystemProperties.get("sys.gammaos.nano.browser_url", "");
        if (!TextUtils.isEmpty(fromProp)) {
            url = fromProp;
            // Best-effort one-shot clear; nano overwrites it on every launch anyway, and
            // the set may be denied by sepolicy (do not let that crash the browser).
            try { android.os.SystemProperties.set("sys.gammaos.nano.browser_url", ""); } catch (Exception ignored) {}
        }
        if (url == null && intent != null) {
            if (Intent.ACTION_WEB_SEARCH.equals(intent.getAction())) {
                String q = intent.getStringExtra(SearchManager.QUERY);
                if (!TextUtils.isEmpty(q)) url = HOME_URL + "/search?q=" + Uri.encode(q);
            }
            if (url == null) url = intent.getStringExtra("com.gammaos.browser.extra.URL");
            if (url == null) url = intent.getDataString();
        }
        if (TextUtils.isEmpty(url)) url = HOME_URL;
        mRetriedOnce = false;   // allow one cold-start retry for this load
        mWeb.loadUrl(url);
    }

    // Turn the address-bar text into a URL: a bare query becomes a Google search,
    // a host-like token gets https://, an explicit scheme is kept as typed.
    private void commitAddress() {
        String text = mAddress.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        String url;
        if (text.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
            url = text;
        } else if (!text.contains(" ") && text.contains(".")) {
            url = "https://" + text;
        } else {
            url = HOME_URL + "/search?q=" + Uri.encode(text);
        }
        hideKeyboard();
        mWeb.requestFocus();
        mRetriedOnce = false;
        mWeb.loadUrl(url);
    }

    private void focusAddress() {
        mAddress.requestFocus();
        mAddress.selectAll();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(mAddress, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(mAddress.getWindowToken(), 0);
    }

    // Controller-first key handling. Face/shoulder buttons drive the browser even
    // when the WebView has focus; the d-pad still does spatial navigation.
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_BUTTON_Y:           // reload
                    mWeb.reload();
                    return true;
                case KeyEvent.KEYCODE_BUTTON_X:           // jump to the address bar
                    focusAddress();
                    return true;
                case KeyEvent.KEYCODE_BUTTON_L1:
                    if (mWeb.canGoBack()) mWeb.goBack();
                    return true;
                case KeyEvent.KEYCODE_BUTTON_R1:
                    if (mWeb.canGoForward()) mWeb.goForward();
                    return true;
                case KeyEvent.KEYCODE_BUTTON_START:
                    focusAddress();
                    return true;
                case KeyEvent.KEYCODE_BUTTON_B:           // gamepad B = back/exit
                    handleBack();
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void handleBack() {
        if (mAddress.hasFocus()) { hideKeyboard(); mWeb.requestFocus(); return; }
        if (mWeb.canGoBack()) { mWeb.goBack(); return; }
        finish();   // returns to the XMB
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mWeb.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mWeb.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mWeb.onResume();
    }

    @Override
    protected void onDestroy() {
        if (mWeb != null) {
            mWeb.loadUrl("about:blank");
            mWeb.destroy();
            mWeb = null;
        }
        super.onDestroy();
    }
}
