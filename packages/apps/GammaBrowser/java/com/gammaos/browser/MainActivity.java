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
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
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

    private static final String HOME_URL = "https://www.google.com";
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
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setMediaPlaybackRequiresUserGesture(true);
        if (mDesktop) s.setUserAgentString(DESKTOP_UA);

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
                updateStar();
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

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (kc) {
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
        mWeb.onPause();
        writeStore();          // flush any history accumulated since the last write
    }

    @Override
    protected void onResume() {
        super.onResume();
        mWeb.onResume();
    }

    @Override
    protected void onDestroy() {
        writeStore();
        if (mWeb != null) {
            mWeb.loadUrl("about:blank");
            mWeb.destroy();
            mWeb = null;
        }
        super.onDestroy();
    }
}
