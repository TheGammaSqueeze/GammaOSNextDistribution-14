/*
 * GammaBrowser - a lightweight, controller-first WebView browser for the GammaOS
 * Nano XMB "Internet Browser" / "Internet Search" items. Touch is supported too,
 * but the chrome is built for the gamepad: every control is focusable for d-pad
 * traversal and the face/shoulder buttons drive back/forward/reload/address.
 *
 * Polish layer: a persistent bookmarks + history store with a controller-navigable
 * XMB-styled overlay panel (SELECT or the menu button), a toolbar star to bookmark
 * the current page, and a desktop-site toggle. State lives in getFilesDir()/browser.json.
 */
package com.gammaos.browser;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    // Search engines, mirrored from nano's kNanoSearchEngines. The XMB Internet
    // Search engine picker writes persist.gammaos.nano.search_engine; honour it here
    // so an address-bar query and the default home use the engine the user chose.
    private static final String[][] ENGINES = {
        {"google",     "https://www.google.com/search?q=%s",           "https://www.google.com"},
        {"bing",       "https://www.bing.com/search?q=%s",             "https://www.bing.com"},
        {"duckduckgo", "https://duckduckgo.com/?q=%s",                 "https://duckduckgo.com"},
        {"brave",      "https://search.brave.com/search?q=%s",         "https://search.brave.com"},
        {"startpage",  "https://www.startpage.com/sp/search?query=%s", "https://www.startpage.com"},
        {"ecosia",     "https://www.ecosia.org/search?q=%s",           "https://www.ecosia.org"},
    };
    // A common desktop Chrome UA so sites serve their full-width layout when the
    // user toggles "Desktop site" (the gamepad-mouse makes desktop layouts usable).
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/120.0.0.0 Safari/537.36";
    private static final int HISTORY_MAX = 300;

    private WebView mWeb;
    private EditText mAddress;
    private ProgressBar mProgress;
    private ImageButton mBack, mForward, mReload, mStar, mMenu;
    private boolean mRetriedOnce = false;   // one-shot retry on a cold-start network error

    // Bookmarks / history overlay
    private ViewGroup mContent;
    private FrameLayout mPanel;
    private ListView mList;
    private TextView mEmpty;
    private Button mTabBookmarks, mTabHistory, mBtnDesktop;
    private ArrayAdapter<Entry> mAdapter;
    private final List<Entry> mShown = new ArrayList<>();   // backs the visible list
    private boolean mPanelOpen = false;
    private boolean mShowingBookmarks = true;

    // Persistent state
    private final List<Entry> mBookmarks = new ArrayList<>();
    private final List<Entry> mHistory = new ArrayList<>();
    private boolean mDesktop = false;

    // Live page identity (for bookmarking / history)
    private String mCurrentUrl = "";
    private String mCurrentTitle = "";

    // ---- In-app gamepad mouse mode ----
    private CursorView mCursor;
    private InputMethodManager mImm;            // cached
    private boolean mCursorMode = false;        // user's Y toggle (persists across IME)
    private boolean mImeUp = false;             // a page text field has the IME up
    private boolean mPaused = false;            // activity backgrounded (keep renderer idle)
    private int mEdgeTick = 0;                  // throttles edge-scroll JS injection
    private float mCx, mCy;                     // cursor position, WebView-local px
    private float mSaveCx, mSaveCy;             // remembered across an IME session
    private float mStickX, mStickY;             // left analog stick (deadzoned), -1..1
    private boolean mLeftHeld, mRightHeld, mUpHeld, mDownHeld;  // d-pad held flags (key path)
    private int mHatX, mHatY;                   // d-pad delivered as a hat axis (motion path)
    private int mKeyDx, mKeyDy;                 // {-1,0,1} combined d-pad direction
    private Choreographer mChoreo;
    private Choreographer.FrameCallback mFrameCb;
    private boolean mFrameScheduled = false;
    private long mLastFrameNanos = 0L;
    private float mDensity = 1f;
    private float mMoveAccumS = 0f;             // seconds the cursor has moved continuously
    private final int[] mLocWeb = new int[2];
    private final int[] mLocRoot = new int[2];
    // Cursor travel accelerates: slow at first for precise aiming at small links, then
    // ramps up while a direction is held so crossing the screen is still quick.
    private static final float CURSOR_SPEED_MIN_DP_S = 300f;
    private static final float CURSOR_SPEED_MAX_DP_S = 1050f;
    private static final float CURSOR_ACCEL_RAMP_S   = 0.55f;   // time to reach max speed
    private static final float STICK_DEADZONE    = 0.18f;
    private static final float EDGE_MARGIN_DP    = 42f;     // edge band that scrolls
    private static final int   EDGE_SCROLL_CSS   = 22;      // CSS px per frame at the edge
    // The page focusin/focusout bridge is the source of truth for the IME on the
    // leanback fullscreen-extract keyboard (where WindowInsets.ime() is unreliable).
    private static final String EDITABLE_BRIDGE_JS =
        "(function(){if(window.__gbHook)return;window.__gbHook=1;" +
        "var T={text:1,search:1,url:1,email:1,tel:1,password:1,number:1," +
        "'datetime-local':1,date:1,time:1,month:1,week:1};" +
        "function ed(e){if(!e)return false;if(e.isContentEditable)return true;" +
        "var g=e.tagName?e.tagName.toUpperCase():'';" +
        "if(g==='TEXTAREA')return !e.disabled&&!e.readOnly;" +
        "if(g==='INPUT'){var t=(e.type||'text').toLowerCase();return T[t]===1&&!e.disabled&&!e.readOnly;}" +
        "return false;}" +
        "document.addEventListener('focusin',function(e){if(ed(e.target)){try{Android.onEditableFocus();}catch(_){}}},true);" +
        "document.addEventListener('focusout',function(e){if(ed(e.target)){try{Android.onEditableBlur();}catch(_){}}},true);" +
        "if(document.activeElement&&ed(document.activeElement)){try{Android.onEditableFocus();}catch(_){}}" +
        "})();";

    /** A bookmark or history entry: a page title over its URL. */
    private static final class Entry {
        String title;
        String url;
        long ts;
        Entry(String t, String u, long s) { title = t; url = u; ts = s; }
    }

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
        mStar = findViewById(R.id.btn_star);
        mMenu = findViewById(R.id.btn_menu);

        mContent = findViewById(R.id.content);
        mPanel = findViewById(R.id.panel);
        mList = findViewById(R.id.list);
        mEmpty = findViewById(R.id.empty);
        mTabBookmarks = findViewById(R.id.tab_bookmarks);
        mTabHistory = findViewById(R.id.tab_history);
        mBtnDesktop = findViewById(R.id.btn_desktop);

        mCursor = findViewById(R.id.cursor);
        mImm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        mDensity = getResources().getDisplayMetrics().density;
        mChoreo = Choreographer.getInstance();
        mFrameCb = this::onCursorFrame;

        loadStore();           // before configureWebView so the desktop-UA choice applies
        configureWebView();
        setupPanel();

        mBack.setOnClickListener(v -> { if (mWeb.canGoBack()) mWeb.goBack(); });
        mForward.setOnClickListener(v -> { if (mWeb.canGoForward()) mWeb.goForward(); });
        mReload.setOnClickListener(v -> mWeb.reload());
        findViewById(R.id.btn_go).setOnClickListener(v -> commitAddress());
        mStar.setOnClickListener(v -> toggleBookmark());
        mMenu.setOnClickListener(v -> openPanel());

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
        updateStar();
    }

    private void configureWebView() {
        android.webkit.WebSettings s = mWeb.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);            // localStorage, small + needed
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setMediaPlaybackRequiresUserGesture(true);
        // No GMS -> Safe Browsing can't do real checks anyway; off saves its init + memory.
        try { s.setSafeBrowsingEnabled(false); } catch (Exception ignored) {}
        if (mDesktop) s.setUserAgentString(DESKTOP_UA);

        // Let lmkd reclaim the (heavy) renderer process when the WebView is invisible
        // (the nano launcher takes the display when backgrounded). onRenderProcessGone
        // rebuilds the WebView so the app never crashes when the renderer is reaped.
        try { mWeb.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true); }
        catch (Exception ignored) {}

        // Page->app bridge: report when a page text field gains/loses focus so we can
        // raise the IME and pause/resume mouse mode. Two no-arg void methods only.
        mWeb.addJavascriptInterface(new EditableBridge(this), "Android");

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
                mCurrentUrl = url != null ? url : "";
                if (!mAddress.hasFocus()) mAddress.setText(url);
                mProgress.setVisibility(View.VISIBLE);
                // A new document means the old field's focusout will never arrive: clear
                // any stale IME state so it does not suppress the cursor on the new page.
                if (mImeUp) { mImeUp = false; onImeDismissed(); }
                updateStar();
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                mCurrentUrl = url != null ? url : "";
                if (!mAddress.hasFocus()) mAddress.setText(url);
                mProgress.setVisibility(View.GONE);
                mBack.setEnabled(mWeb.canGoBack());
                mForward.setEnabled(mWeb.canGoForward());
                String title = view.getTitle();
                mCurrentTitle = !TextUtils.isEmpty(title) ? title : url;
                addHistory(url, mCurrentTitle);
                if (url != null && (url.startsWith("https://") || url.startsWith("http://")))
                    view.evaluateJavascript(EDITABLE_BRIDGE_JS, null);
                updateStar();
            }
            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // The renderer was reaped (usually by lmkd while backgrounded). A dead
                // WebView can never be reused: tear it down and rebuild, then reload.
                if (mWeb != view) return true;
                // A reaped renderer can leave IME state latched (focusout never arrives
                // on a fresh WebView) which would deaden the controller; clear it.
                mImeUp = false;
                ViewGroup parent = (ViewGroup) view.getParent();
                int idx = parent != null ? parent.indexOfChild(view) : -1;
                if (parent != null) parent.removeView(view);
                view.destroy();
                String last = mCurrentUrl;
                mWeb = new WebView(MainActivity.this);
                if (parent != null) parent.addView(mWeb, idx,
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
                configureWebView();
                mWeb.requestFocus();
                // A fresh WebView starts with timers running; if we were backgrounded
                // (the reason the renderer was reaped), keep the new one idle too.
                if (mPaused) { mWeb.onPause(); mWeb.pauseTimers(); }
                if (!TextUtils.isEmpty(last) && !"about:blank".equals(last)) {
                    mRetriedOnce = false;
                    mWeb.loadUrl(last);
                }
                if (mCursorMode) mWeb.post(() -> {
                    if (!mCursorMode) return;            // re-center over the new view
                    mCx = Math.max(1, mWeb.getWidth())  * 0.5f;
                    mCy = Math.max(1, mWeb.getHeight()) * 0.5f;
                    positionCursor();
                });
                return true;   // handled: do not let the framework kill the app
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
                if (!TextUtils.isEmpty(title)) {
                    setTitle(title);
                    mCurrentTitle = title;
                    // A title often arrives after onPageFinished; keep the freshest
                    // history entry's title in sync.
                    if (!mHistory.isEmpty() && sameUrl(mHistory.get(0).url, mCurrentUrl))
                        mHistory.get(0).title = title;
                }
            }
        });
    }

    // ---- Bookmarks / history overlay ----------------------------------------

    private void setupPanel() {
        mAdapter = new ArrayAdapter<Entry>(this, 0, mShown) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView;
                if (row == null)
                    row = LayoutInflater.from(getContext()).inflate(R.layout.row_entry, parent, false);
                Entry e = getItem(position);
                TextView t = row.findViewById(R.id.row_title);
                TextView u = row.findViewById(R.id.row_url);
                t.setText(e != null && !TextUtils.isEmpty(e.title) ? e.title
                        : (e != null ? e.url : ""));
                u.setText(e != null ? e.url : "");
                return row;
            }
        };
        mList.setAdapter(mAdapter);
        mList.setOnItemClickListener((parent, view, pos, id) -> openEntry(pos));

        mTabBookmarks.setOnClickListener(v -> showTab(true));
        mTabHistory.setOnClickListener(v -> showTab(false));
        mBtnDesktop.setOnClickListener(v -> toggleDesktop());
        mBtnDesktop.setText(mDesktop ? R.string.desktop_on : R.string.desktop_off);
        // Tapping the dimmed scrim (outside the card) closes the panel; the card
        // itself is clickable so its background taps do not bubble up.
        mPanel.setOnClickListener(v -> closePanel());
    }

    private void openPanel() {
        if (mPanelOpen) return;
        if (mCursorMode) setCursorMode(false);   // one overlay at a time
        mPanelOpen = true;
        showTab(mShowingBookmarks);
        mPanel.setVisibility(View.VISIBLE);
        // Keep d-pad focus inside the overlay (do not let it escape to the WebView
        // and toolbar behind the dimmed scrim).
        mContent.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (!mShown.isEmpty()) { mList.requestFocus(); mList.setSelection(0); }
        else mTabBookmarks.requestFocus();
    }

    private void closePanel() {
        if (!mPanelOpen) return;
        mPanelOpen = false;
        mPanel.setVisibility(View.GONE);
        mContent.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        mWeb.requestFocus();
    }

    private void showTab(boolean bookmarks) {
        mShowingBookmarks = bookmarks;
        refreshList();
        int accent = getColor(R.color.xmb_accent);
        int dim = getColor(R.color.xmb_hint);
        mTabBookmarks.setTextColor(bookmarks ? accent : dim);
        mTabHistory.setTextColor(bookmarks ? dim : accent);
    }

    private void refreshList() {
        List<Entry> src = mShowingBookmarks ? mBookmarks : mHistory;
        mShown.clear();
        mShown.addAll(src);
        mAdapter.notifyDataSetChanged();
        boolean empty = mShown.isEmpty();
        mEmpty.setText(mShowingBookmarks ? R.string.empty_bookmarks : R.string.empty_history);
        mEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        mList.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (!empty) mList.setSelection(0);
    }

    private void openEntry(int pos) {
        if (pos < 0 || pos >= mShown.size()) return;
        String url = mShown.get(pos).url;
        closePanel();
        if (!TextUtils.isEmpty(url)) {
            mRetriedOnce = false;
            mWeb.loadUrl(url);
        }
    }

    private void openSelectedEntry() {
        int pos = mList.getSelectedItemPosition();
        if (pos == ListView.INVALID_POSITION && mShown.size() > 0) pos = 0;
        openEntry(pos);
    }

    private void deleteSelected() {
        int pos = mList.getSelectedItemPosition();
        if (pos == ListView.INVALID_POSITION || pos < 0 || pos >= mShown.size()) return;
        Entry e = mShown.get(pos);
        List<Entry> src = mShowingBookmarks ? mBookmarks : mHistory;
        src.remove(e);
        writeStore();
        refreshList();
        if (!mShown.isEmpty()) mList.setSelection(Math.min(pos, mShown.size() - 1));
        if (mShowingBookmarks) updateStar();
    }

    private void onPanelConfirm() {
        View f = getCurrentFocus();
        if (f != null && f != mList && (f instanceof Button || f instanceof ImageButton)) {
            f.performClick();
            return;
        }
        openSelectedEntry();
    }

    // ---- Bookmarks ----------------------------------------------------------

    private void toggleBookmark() {
        String url = mCurrentUrl;
        if (TextUtils.isEmpty(url) || url.equals("about:blank")) return;
        int idx = indexOfUrl(mBookmarks, url);
        if (idx >= 0) {
            mBookmarks.remove(idx);
            toast(getString(R.string.unbookmarked));
        } else {
            String title = !TextUtils.isEmpty(mCurrentTitle) ? mCurrentTitle : url;
            mBookmarks.add(0, new Entry(title, url, now()));
            toast(getString(R.string.bookmarked));
        }
        writeStore();
        updateStar();
        if (mPanelOpen && mShowingBookmarks) refreshList();
    }

    private void updateStar() {
        boolean on = indexOfUrl(mBookmarks, mCurrentUrl) >= 0;
        mStar.setImageResource(on ? R.drawable.ic_star_filled : R.drawable.ic_star);
    }

    // ---- History ------------------------------------------------------------

    private void addHistory(String url, String title) {
        if (TextUtils.isEmpty(url) || url.equals("about:blank") || url.startsWith("data:")) return;
        int idx = indexOfUrl(mHistory, url);
        if (idx >= 0) mHistory.remove(idx);          // move existing to the front
        mHistory.add(0, new Entry(!TextUtils.isEmpty(title) ? title : url, url, now()));
        while (mHistory.size() > HISTORY_MAX) mHistory.remove(mHistory.size() - 1);
        if (mPanelOpen && !mShowingBookmarks) refreshList();
        // Persist eagerly: in the nano DRM setup the app may be torn down without a
        // clean onPause/onDestroy when the launcher reclaims the display, so we cannot
        // rely on a deferred flush. The store is a small JSON, written once per page.
        writeStore();
    }

    // ---- Desktop site -------------------------------------------------------

    private void toggleDesktop() {
        mDesktop = !mDesktop;
        mWeb.getSettings().setUserAgentString(mDesktop ? DESKTOP_UA : null);
        mBtnDesktop.setText(mDesktop ? R.string.desktop_on : R.string.desktop_off);
        writeStore();
        mWeb.reload();
        toast(mBtnDesktop.getText().toString());
    }

    // ---- Address bar --------------------------------------------------------

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
                if (!TextUtils.isEmpty(q)) url = searchUrl(q);
            }
            if (url == null) url = intent.getStringExtra("com.gammaos.browser.extra.URL");
            if (url == null) url = intent.getDataString();
        }
        if (TextUtils.isEmpty(url)) url = engineHome();
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
            url = searchUrl(text);
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

    // ---- Key handling -------------------------------------------------------
    // Controller-first. Face/shoulder buttons drive the browser even when the
    // WebView has focus; the d-pad still does spatial navigation. When the panel
    // is open it gets first crack at the buttons.
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int kc = event.getKeyCode();
        if (mPanelOpen) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (kc) {
                    case KeyEvent.KEYCODE_BUTTON_B:
                    case KeyEvent.KEYCODE_BACK:
                    case KeyEvent.KEYCODE_BUTTON_SELECT:
                        closePanel();
                        return true;
                    case KeyEvent.KEYCODE_BUTTON_A:
                        onPanelConfirm();
                        return true;
                    case KeyEvent.KEYCODE_BUTTON_X:
                        deleteSelected();
                        return true;
                    case KeyEvent.KEYCODE_BUTTON_Y:
                        toggleDesktop();
                        return true;
                    case KeyEvent.KEYCODE_BUTTON_L1:
                        showTab(true);
                        return true;
                    case KeyEvent.KEYCODE_BUTTON_R1:
                        showTab(false);
                        return true;
                    default:
                        break;
                }
            }
            // d-pad navigation and the rest go to the focused panel view
            return super.dispatchKeyEvent(event);
        }

        // While a page text field has the IME up, let the framework/IME own every key
        // (typing + leanback keyboard navigation). The cursor resumes on dismiss.
        // Y is always an escape hatch so a stuck IME state can never deaden input.
        if (mImeUp) {
            if (kc == KeyEvent.KEYCODE_BUTTON_Y && event.getAction() == KeyEvent.ACTION_DOWN) {
                dismissPageIme();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        // Mouse mode: the cursor is driven here; the WebView gets no nav/face keys.
        if (mCursorMode) {
            final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
            switch (kc) {
                case KeyEvent.KEYCODE_BUTTON_Y:
                    if (down) toggleCursorMode();        // Y exits mouse mode
                    return true;
                case KeyEvent.KEYCODE_BUTTON_A:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (down) cursorClick(false);        // left click
                    return true;
                case KeyEvent.KEYCODE_BUTTON_B:
                    if (down) cursorClick(true);         // right click (contextmenu)
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:  mLeftHeld  = down; recomputeKeyDir(); startCursorLoop(); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: mRightHeld = down; recomputeKeyDir(); startCursorLoop(); return true;
                case KeyEvent.KEYCODE_DPAD_UP:    mUpHeld    = down; recomputeKeyDir(); startCursorLoop(); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:  mDownHeld  = down; recomputeKeyDir(); startCursorLoop(); return true;
                case KeyEvent.KEYCODE_BUTTON_SELECT:
                    if (down) { setCursorMode(false); openPanel(); }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_L1:
                    if (down && mWeb.canGoBack()) mWeb.goBack();
                    return true;
                case KeyEvent.KEYCODE_BUTTON_R1:
                    if (down && mWeb.canGoForward()) mWeb.goForward();
                    return true;
                case KeyEvent.KEYCODE_BUTTON_X:
                case KeyEvent.KEYCODE_BUTTON_START:
                    if (down) { setCursorMode(false); focusAddress(); }
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (down) setCursorMode(false);      // hardware back exits mode first
                    return true;
                default:
                    return true;                          // swallow the rest; no spatial nav
            }
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (kc) {
                case KeyEvent.KEYCODE_BUTTON_Y:           // toggle mouse mode
                    toggleCursorMode();
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
                case KeyEvent.KEYCODE_BUTTON_SELECT:      // open bookmarks / history
                    openPanel();
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

    @Override
    public boolean onGenericMotionEvent(MotionEvent ev) {
        // Read the left analog stick (and hat axis) for cursor movement. On this device
        // the stick is mapped to the d-pad (handled as keys), but this keeps the mode
        // portable to pads that report a real analog axis.
        if (mCursorMode && !mImeUp
                && (ev.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && ev.getAction() == MotionEvent.ACTION_MOVE) {
            float x = ev.getAxisValue(MotionEvent.AXIS_X);
            float y = ev.getAxisValue(MotionEvent.AXIS_Y);
            mStickX = Math.abs(x) > STICK_DEADZONE ? x : 0f;
            mStickY = Math.abs(y) > STICK_DEADZONE ? y : 0f;
            // The d-pad may arrive as a hat axis instead of (or in addition to) DPAD
            // keys; recomputeKeyDir combines both without double-counting.
            float hx = ev.getAxisValue(MotionEvent.AXIS_HAT_X);
            float hy = ev.getAxisValue(MotionEvent.AXIS_HAT_Y);
            mHatX = hx > 0.5f ? 1 : (hx < -0.5f ? -1 : 0);
            mHatY = hy > 0.5f ? 1 : (hy < -0.5f ? -1 : 0);
            recomputeKeyDir();
            startCursorLoop();
            return true;
        }
        return super.onGenericMotionEvent(ev);
    }

    private void handleBack() {
        if (mPanelOpen) { closePanel(); return; }
        if (mAddress.hasFocus()) { hideKeyboard(); mWeb.requestFocus(); return; }
        if (mWeb.canGoBack()) { mWeb.goBack(); return; }
        finish();   // returns to the XMB
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    // ---- In-app gamepad mouse mode ------------------------------------------

    // The system GammaPad daemon can draw its own OS-level cursor; never run two
    // cursors at once. If the system mouse is active, our in-app mode stays off.
    private boolean systemMouseActive() {
        try { return android.os.SystemProperties.getInt("sys.gammaos.gamepad.mouse_active", 0) != 0; }
        catch (Exception e) { return false; }
    }

    private void toggleCursorMode() {
        if (mCursorMode) { setCursorMode(false); return; }
        if (systemMouseActive()) { toast(getString(R.string.system_mouse_on)); return; }
        if (mPanelOpen || mAddress.hasFocus()) return;
        setCursorMode(true);
    }

    private void setCursorMode(boolean on) {
        mCursorMode = on;
        clearHeldKeys();
        mStickX = mStickY = 0f;
        if (on) {
            int w = Math.max(1, mWeb.getWidth()), h = Math.max(1, mWeb.getHeight());
            mCx = w * 0.5f; mCy = h * 0.5f;
            mCursor.setVisibility(View.VISIBLE);
            positionCursor();
            startCursorLoop();
            toast(getString(R.string.cursor_on));
        } else {
            stopCursorLoop();
            mCursor.setVisibility(View.GONE);
            toast(getString(R.string.cursor_off));
        }
    }

    // Combine the d-pad delivered as keys (held flags) and as a hat axis (mHatX/Y);
    // clamp so the two never add up past 1 (no double speed) and a centered hat never
    // cancels a held key.
    private void recomputeKeyDir() {
        int x = (mRightHeld ? 1 : 0) - (mLeftHeld ? 1 : 0) + mHatX;
        int y = (mDownHeld ? 1 : 0) - (mUpHeld ? 1 : 0) + mHatY;
        mKeyDx = Math.max(-1, Math.min(1, x));
        mKeyDy = Math.max(-1, Math.min(1, y));
    }

    private void clearHeldKeys() {
        mLeftHeld = mRightHeld = mUpHeld = mDownHeld = false;
        mHatX = mHatY = 0;
        mKeyDx = mKeyDy = 0;
        mMoveAccumS = 0f;
    }

    private void startCursorLoop() {
        if (mFrameScheduled || !mCursorMode || mImeUp) return;
        mLastFrameNanos = 0L;
        mFrameScheduled = true;
        mChoreo.postFrameCallback(mFrameCb);
    }

    private void stopCursorLoop() {
        if (!mFrameScheduled) return;
        mFrameScheduled = false;
        mChoreo.removeFrameCallback(mFrameCb);
    }

    // Per-frame cursor integration. Re-posts only while input is active, so the loop
    // costs nothing once the stick/d-pad rests (and nothing at all when mode is off).
    private void onCursorFrame(long frameNanos) {
        mFrameScheduled = false;
        if (!mCursorMode || mImeUp) return;
        float dt = (mLastFrameNanos == 0L) ? (1f / 60f)
                 : Math.min(0.05f, (frameNanos - mLastFrameNanos) / 1e9f);
        mLastFrameNanos = frameNanos;

        float ix = mKeyDx + mStickX;
        float iy = mKeyDy + mStickY;
        float mag = (float) Math.hypot(ix, iy);
        boolean moving = mag > 0.001f;
        if (mag > 1f) { ix /= mag; iy /= mag; }

        // Accelerate the longer a direction is held; reset the instant it rests.
        mMoveAccumS = moving ? (mMoveAccumS + dt) : 0f;
        float ramp = Math.min(1f, mMoveAccumS / CURSOR_ACCEL_RAMP_S);
        float speedDpS = CURSOR_SPEED_MIN_DP_S + (CURSOR_SPEED_MAX_DP_S - CURSOR_SPEED_MIN_DP_S) * ramp;

        if (moving) {
            float speed = speedDpS * mDensity;
            int w = Math.max(1, mWeb.getWidth()), h = Math.max(1, mWeb.getHeight());
            mCx = Math.max(0, Math.min(w - 1, mCx + ix * speed * dt));
            mCy = Math.max(0, Math.min(h - 1, mCy + iy * speed * dt));
            positionCursor();
            edgeScroll(ix, iy, w, h);
        }
        if (mCursorMode && !mImeUp && moving) {
            mFrameScheduled = true;
            mChoreo.postFrameCallback(mFrameCb);
        }
    }

    // Place the arrow so its hotspot (top-left, 0,0) sits at WebView-local (mCx,mCy).
    // setX/setY are in the cursor's parent space, so measure the WebView relative to
    // that same parent (the root FrameLayout the cursor lives in), not the decor view.
    private void positionCursor() {
        View ref = (View) mCursor.getParent();
        if (ref == null) return;
        mWeb.getLocationInWindow(mLocWeb);
        ref.getLocationInWindow(mLocRoot);
        mCursor.setX(mLocWeb[0] - mLocRoot[0] + mCx);
        mCursor.setY(mLocWeb[1] - mLocRoot[1] + mCy);
    }

    // Scroll the page (or the scrollable element under the cursor) when the cursor is
    // pinned at an edge and the input is still pushing that way.
    private void edgeScroll(float ix, float iy, int w, int h) {
        int m = (int) (EDGE_MARGIN_DP * mDensity);
        int dx = 0, dy = 0;
        if (mCx <= m && ix < 0)             dx = -EDGE_SCROLL_CSS;
        else if (mCx >= w - 1 - m && ix > 0) dx = EDGE_SCROLL_CSS;
        if (mCy <= m && iy < 0)             dy = -EDGE_SCROLL_CSS;
        else if (mCy >= h - 1 - m && iy > 0) dy = EDGE_SCROLL_CSS;
        // Throttle the JS injection (its DOM walk is non-trivial) to every 3rd frame;
        // scale the step up to keep the same scroll rate.
        if (dx != 0 || dy != 0) {
            if (++mEdgeTick % 3 == 0) injectScroll(mCx, mCy, dx * 3, dy * 3);
        } else {
            mEdgeTick = 0;
        }
    }

    private void cursorClick(boolean rightClick) {
        if (rightClick) { injectContextMenu(mCx, mCy); return; }
        long t = SystemClock.uptimeMillis();
        sendTouch(MotionEvent.ACTION_DOWN, mCx, mCy, t, t);
        sendTouch(MotionEvent.ACTION_UP, mCx, mCy, t, t + 20);
        // If the tap focused a text field, the page bridge fires onEditableFocus()
        // and we suspend for the IME there.
    }

    private void sendTouch(int action, float x, float y, long downTime, long eventTime) {
        float lx = Math.max(0, Math.min(Math.max(0, mWeb.getWidth() - 1), x));
        float ly = Math.max(0, Math.min(Math.max(0, mWeb.getHeight() - 1), y));
        MotionEvent e = MotionEvent.obtain(downTime, eventTime, action, lx, ly, 0);
        e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        mWeb.dispatchTouchEvent(e);
        e.recycle();
    }

    // Right click: dispatch a DOM contextmenu at the cursor (device px -> CSS px via dpr).
    private void injectContextMenu(float lx, float ly) {
        String js =
            "(function(px,py){var d=window.devicePixelRatio||1;var x=px/d,y=py/d;" +
            "var el=document.elementFromPoint(x,y);if(!el)return;" +
            "el.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true," +
            "view:window,button:2,buttons:2,clientX:x,clientY:y}));})(" + lx + "," + ly + ");";
        mWeb.evaluateJavascript(js, null);
    }

    // Edge scroll: scroll the nearest scrollable ancestor under the cursor, else the page.
    private void injectScroll(float lx, float ly, int dx, int dy) {
        String js =
            "(function(px,py,dx,dy){var d=window.devicePixelRatio||1;var x=px/d,y=py/d;" +
            "var el=document.elementFromPoint(x,y);" +
            "function sc(n){while(n&&n!==document.body&&n!==document.documentElement){var s=getComputedStyle(n);" +
            "if(((s.overflowY==='auto'||s.overflowY==='scroll')&&n.scrollHeight>n.clientHeight)||" +
            "((s.overflowX==='auto'||s.overflowX==='scroll')&&n.scrollWidth>n.clientWidth))return n;n=n.parentElement;}return null;}" +
            "var t=sc(el);if(t){t.scrollLeft+=dx;t.scrollTop+=dy;}else{window.scrollBy(dx,dy);}" +
            "})(" + lx + "," + ly + "," + dx + "," + dy + ");";
        mWeb.evaluateJavascript(js, null);
    }

    // Page -> app bridge: two no-arg void methods only (safe minimal surface). Calls
    // arrive on a binder thread, so hop to the UI thread.
    private static final class EditableBridge {
        private final MainActivity host;
        EditableBridge(MainActivity h) { host = h; }
        @JavascriptInterface public void onEditableFocus() { host.runOnUiThread(host::onWebEditableFocus); }
        @JavascriptInterface public void onEditableBlur()  { host.runOnUiThread(host::onWebEditableBlur); }
    }

    private void onWebEditableFocus() {
        if (mImeUp) return;
        mImeUp = true;
        if (mCursorMode) {
            mSaveCx = mCx; mSaveCy = mCy;     // remember where to resume
            stopCursorLoop();
            // Drop any held d-pad/stick state: while the IME owns input the release
            // events bypass us, so without this the cursor would drift on resume.
            clearHeldKeys();
            mStickX = mStickY = 0f;
            mCursor.setVisibility(View.GONE);
        }
        showImeForWeb();
    }

    private void onWebEditableBlur() {
        onImeDismissed();
    }

    // Force the page IME away (Y escape hatch). Clears the latched state and resumes.
    private void dismissPageIme() {
        if (mImm != null) mImm.hideSoftInputFromWindow(mWeb.getWindowToken(), 0);
        mWeb.evaluateJavascript("if(document.activeElement)document.activeElement.blur();", null);
        onImeDismissed();
    }

    // Resume cursor mode after the page IME goes away (driven by focusout, by
    // onWindowFocusChanged for the leanback fullscreen IME, and by a page navigation).
    private void onImeDismissed() {
        if (!mImeUp) return;
        mImeUp = false;
        // Do not resume the cursor if focus moved to the address bar (its own IME).
        if (mCursorMode && !mPanelOpen && !mAddress.hasFocus() && hasWindowFocus()) {
            mCx = mSaveCx; mCy = mSaveCy;
            mCursor.setVisibility(View.VISIBLE);
            positionCursor();
            startCursorLoop();
        }
    }

    private void showImeForWeb() {
        mWeb.setFocusable(true);
        mWeb.setFocusableInTouchMode(true);
        mWeb.requestFocus();
        if (mImm == null) return;
        try { mImm.restartInput(mWeb); } catch (Exception ignored) {}
        mWeb.post(() -> {
            if (mWeb != null && mWeb.hasWindowFocus())
                mImm.showSoftInput(mWeb, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && mImeUp) {
            // The leanback fullscreen IME is a separate window; regaining focus while
            // we think the IME is up means it just closed.
            onImeDismissed();
        } else if (!hasFocus) {
            stopCursorLoop();                 // don't tick while the display is taken
        } else if (mCursorMode && !mImeUp) {
            startCursorLoop();
        }
    }

    // ---- Persistence --------------------------------------------------------

    private File storeFile() {
        return new File(getFilesDir(), "browser.json");
    }

    private void loadStore() {
        File f = storeFile();
        if (!f.exists()) return;
        try {
            byte[] buf = new byte[(int) f.length()];
            try (RandomAccessFile raf = new RandomAccessFile(f, "r")) { raf.readFully(buf); }
            JSONObject root = new JSONObject(new String(buf, StandardCharsets.UTF_8));
            mDesktop = root.optBoolean("desktop", false);
            readEntries(root.optJSONArray("bookmarks"), mBookmarks);
            readEntries(root.optJSONArray("history"), mHistory);
        } catch (Exception ignored) {
            // Corrupt store: start clean rather than crash.
            mBookmarks.clear();
            mHistory.clear();
        }
    }

    private static void readEntries(JSONArray arr, List<Entry> out) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String u = o.optString("u", "");
            if (TextUtils.isEmpty(u)) continue;
            out.add(new Entry(o.optString("t", u), u, o.optLong("ts", 0)));
        }
    }

    private void writeStore() {
        try {
            JSONObject root = new JSONObject();
            root.put("desktop", mDesktop);
            root.put("bookmarks", entriesToJson(mBookmarks));
            root.put("history", entriesToJson(mHistory));
            byte[] data = root.toString().getBytes(StandardCharsets.UTF_8);
            // Atomic-ish: write a temp file then rename over the real one.
            File f = storeFile();
            File tmp = new File(f.getParentFile(), "browser.json.tmp");
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(data);
                fos.getFD().sync();
            }
            if (!tmp.renameTo(f)) {
                // Fallback: overwrite in place.
                try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(data); }
                tmp.delete();
            }
        } catch (Exception ignored) {}
    }

    private static JSONArray entriesToJson(List<Entry> in) throws Exception {
        JSONArray arr = new JSONArray();
        for (Entry e : in) {
            JSONObject o = new JSONObject();
            o.put("t", e.title);
            o.put("u", e.url);
            o.put("ts", e.ts);
            arr.put(o);
        }
        return arr;
    }

    // ---- Small helpers ------------------------------------------------------

    private static String[] engineRow() {
        String key = "google";
        try {
            key = android.os.SystemProperties.get("persist.gammaos.nano.search_engine", "google");
        } catch (Exception ignored) {}
        for (String[] e : ENGINES) if (e[0].equals(key)) return e;
        return ENGINES[0];
    }

    private static String searchUrl(String query) {
        return engineRow()[1].replace("%s", Uri.encode(query));
    }

    private static String engineHome() {
        return engineRow()[2];
    }

    private static boolean sameUrl(String a, String b) {
        return a != null && a.equals(b);
    }

    private static int indexOfUrl(List<Entry> list, String url) {
        if (TextUtils.isEmpty(url)) return -1;
        for (int i = 0; i < list.size(); i++)
            if (url.equals(list.get(i).url)) return i;
        return -1;
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ---- Lifecycle ----------------------------------------------------------

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mWeb.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPaused = true;
        stopCursorLoop();
        mWeb.onPause();
        mWeb.pauseTimers();    // process-wide: idle the renderer so it is reclaimable
        writeStore();          // flush any history accumulated since the last write
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPaused = false;
        mWeb.onResume();
        mWeb.resumeTimers();   // must mirror pauseTimers
        if (mCursorMode && systemMouseActive()) setCursorMode(false);   // system cursor won
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (mWeb != null && level >= TRIM_MEMORY_BACKGROUND) {
            mWeb.clearCache(false);   // drop the in-RAM resource cache, keep the disk cache
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mWeb != null) mWeb.clearCache(false);
    }

    @Override
    protected void onDestroy() {
        writeStore();
        stopCursorLoop();
        if (mWeb != null) {
            try { mWeb.removeJavascriptInterface("Android"); } catch (Exception ignored) {}
            ViewGroup p = (ViewGroup) mWeb.getParent();
            if (p != null) p.removeView(mWeb);   // detach before destroy (avoids a leak warning)
            mWeb.loadUrl("about:blank");
            mWeb.destroy();
            mWeb = null;
        }
        super.onDestroy();
    }
}
