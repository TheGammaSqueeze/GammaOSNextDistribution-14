package com.gammaos.secondaryhome;

import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManagerGlobal;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.window.ScreenCapture;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The control centre itself: three swipeable pages, the dashboard (brightness, control tiles,
 * volume, thermal and performance), the application grid (apps open on this display) and the
 * screen options (double tap to wake, idle screen timeout), with the bottom screen sleep (the
 * same 0.6 s backlight fade as the nano CC, from the Sleep tile or after the idle timeout).
 *
 * It is a plain view so two hosts can show it: the secondary home activity when nothing else
 * runs on the bottom panel, and {@link OverlayService}, which floats it over a running bottom
 * app in a system overlay window so that app never leaves the resumed state (an activity on
 * top of it would pause it: SmartTube stopped its video every time the centre came up).
 */
final class CcPanel extends FrameLayout implements DashboardView.Host {
    private static final String TAG = "SecondaryHome";

    /** What the host does for the panel. */
    interface Shell {
        /** Start an application on this panel's display. */
        void startOnOwnDisplay(Intent intent);
        /** An application was started from the grid: an overlay host hides itself. */
        void onAppStarted();
    }

    static final int PAGE_DASHBOARD = 0;
    static final int PAGE_APPS = 1;
    static final int PAGE_OPTIONS = 2;

    private static final long POLL_MS = 500;
    private static final long FADE_MS = 600;
    private static final long DOUBLE_TAP_MS = 450;

    private final Context mCtx;
    private final Shell mShell;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private HandlerThread mPollThread;
    private Handler mPoll;
    private boolean mResumed;

    private final CcState mState;
    private final CcPager mPager;
    private final DashboardView mDashboard;
    private final OptionsView mOptions;
    private RecyclerView mGrid;
    private GridLayoutManager mGridLayout;
    private final List<AppEntry> mApps = new ArrayList<>();
    private AppsAdapter mAdapter;
    private volatile boolean mLoading = false;
    private float mGridUnit = 1f;

    // ---- bottom screen sleep (NanoMenu::ccBeginSleep / ccUpdateSleep / ccRestoreBacklightIfSlept) ----
    private boolean mSleeping;          // target state: true = asleep or dimming
    private float mRamp = 1f;           // 1 = full brightness, 0 = off
    private int mSleepFromBri = 128;    // level restored on wake
    private ValueAnimator mFade;
    private long mLastTouchMs;
    private long mWakeTapMs;
    private boolean mConsumeGesture;

    // ---- the app running on this panel besides the home ("Close App" tile) ----
    private volatile String mBottomApp;

    // ---- an app presenting on this display (sys.gammaos.presentation.<id>, set by WM) ----
    // The resting home activity stays resumed under a dual screen app's Presentation window
    // and cannot see it; its idle sleep then dimmed the panel to 0 in the middle of a game.
    private final boolean mOverlayHost;   // floated over the app on purpose: may idle-sleep
    private static final long PRESENT_WATCH_MS = 1000;

    /**
     * @param ownDisplay the display this panel is shown on
     * @param overlayHost true when floated over a running app by {@link OverlayService}
     */
    CcPanel(Context ctx, int ownDisplay, boolean overlayHost, Shell shell) {
        super(ctx);
        mCtx = ctx;
        mShell = shell;
        mOverlayHost = overlayHost;
        mState = new CcState(ctx);
        resolveDisplays(ownDisplay);

        mPager = new CcPager(ctx);
        mPager.setBackgroundColor(CcDraw.BG);
        mDashboard = new DashboardView(ctx, mState, this);
        mOptions = new OptionsView(ctx);
        mPager.addView(mDashboard);
        mPager.addView(buildAppsPage());
        mPager.addView(mOptions);
        mPager.setOverlay(new CcPager.Overlay() {
            @Override public boolean isOpen() { return mDashboard.pickerOpen(); }
            @Override public void draw(android.graphics.Canvas c, int w, int h) {
                mDashboard.drawPickerOverlay(c, w, h);
            }
        });
        mPager.setListener(page -> {
            if (page == PAGE_APPS) focusFirst();
            else mPager.getChildAt(page).invalidate();
        });
        addView(mPager, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setFocusable(true);
        setFocusableInTouchMode(true);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        loadApps();
    }

    // ------------------------------------------------------------------ host lifecycle

    void resume() {
        mResumed = true;
        mLastTouchMs = SystemClock.uptimeMillis();   // a freshly shown centre never instantly auto-sleeps
        startPolling();
        loadApps();
        mPager.getChildAt(mPager.getPage()).invalidate();
    }

    void pause() {
        mResumed = false;
        stopPolling();
        mDashboard.closePicker();
        // Whoever gets this panel next must not inherit a dark backlight.
        restoreBacklightIfSlept();
    }

    void destroy() {
        restoreBacklightIfSlept();
        stopPolling();
    }

    /** Home pressed on this display: show the dashboard, like the nano CC. */
    void showDashboard() {
        mPager.setPage(PAGE_DASHBOARD, true);
    }

    private void resolveDisplays(int ownId) {
        int content = Display.DEFAULT_DISPLAY;
        if (ownId == Display.DEFAULT_DISPLAY) {
            final DisplayManager dm = mCtx.getSystemService(DisplayManager.class);
            for (Display d : dm.getDisplays()) {
                if (d.getDisplayId() != ownId) { content = d.getDisplayId(); break; }
            }
        }
        mState.ownDisplay = ownId;
        mState.contentDisplay = content;
    }

    // ------------------------------------------------------------------ stats polling

    private final Runnable mPollTick = new Runnable() {
        @Override
        public void run() {
            try {
                mState.poll();
                mBottomApp = findBottomApp();
            } catch (Throwable t) {
                Log.w(TAG, "poll failed", t);
            }
            mMain.post(mUiTick);
            if (mPoll != null) mPoll.postDelayed(this, POLL_MS);
        }
    };

    /** Package of the app presenting on this display, or empty. */
    private String presentingApp() {
        return android.os.SystemProperties.get(
                "sys.gammaos.presentation." + mState.ownDisplay, "");
    }

    /** The home is not what the panel shows: an app presents over it. */
    private boolean coveredByPresentation() {
        return !mOverlayHost && !presentingApp().isEmpty();
    }

    // While the home sleeps the panel no stats poll runs, so this light watch brings the
    // backlight back the moment a dual screen app starts presenting over the dark panel.
    private final Runnable mPresentWatch = new Runnable() {
        @Override
        public void run() {
            if (!mSleeping || mOverlayHost) return;
            if (!presentingApp().isEmpty()) {
                wake();
                return;
            }
            mMain.postDelayed(this, PRESENT_WATCH_MS);
        }
    };

    private final Runnable mUiTick = () -> {
        if (!mResumed) return;
        // idle auto-sleep, same gate as the nano CC (awake, not dimming, timeout passed);
        // never while an app presents over this home, the panel is that app's then
        final int idleMs = OptionsView.sleepTimeoutMs();
        if (idleMs > 0 && !mSleeping && !coveredByPresentation()
                && SystemClock.uptimeMillis() - mLastTouchMs >= idleMs) {
            beginSleep();
        }
        if (coveredByPresentation()) mLastTouchMs = SystemClock.uptimeMillis();
        final int page = mPager.getPage();
        if (page == PAGE_DASHBOARD) mDashboard.invalidate();
        else if (page == PAGE_OPTIONS) mOptions.invalidate();
    };

    private void startPolling() {
        if (mPollThread != null) return;
        mPollThread = new HandlerThread("SecondaryHome-cc");
        mPollThread.start();
        mPoll = new Handler(mPollThread.getLooper());
        mPoll.post(mPollTick);
    }

    private void stopPolling() {
        if (mPollThread == null) return;
        mPoll.removeCallbacksAndMessages(null);
        mPoll = null;
        mPollThread.quitSafely();
        mPollThread = null;
        mMain.removeCallbacks(mUiTick);
    }

    // ------------------------------------------------------------------ bottom screen sleep

    @Override
    public boolean isScreenSleeping() {
        return mSleeping;
    }

    @Override
    public void toggleScreenSleep() {
        if (mSleeping) wake();
        else beginSleep();
    }

    @Override
    public void onSliderBrightness(int bottomLevel) {
        mSleepFromBri = bottomLevel;
    }

    private void beginSleep() {
        if (mSleeping) return;
        if (mRamp >= 1f) {
            mSleepFromBri = CcState.readInt(mState.blBri(true), 128);
            if (mSleepFromBri < 8) mSleepFromBri = 128;   // never capture an already-off value
        }
        mSleeping = true;
        mDashboard.closePicker();
        // the panel is dark: no stats work while slept, only the touch path stays live
        fadeTo(0f, this::stopPolling);
        mDashboard.invalidate();
        if (!mOverlayHost) {
            mMain.removeCallbacks(mPresentWatch);
            mMain.postDelayed(mPresentWatch, PRESENT_WATCH_MS);
        }
    }

    private void wake() {
        if (!mSleeping) return;
        mSleeping = false;
        mMain.removeCallbacks(mPresentWatch);
        mLastTouchMs = SystemClock.uptimeMillis();
        if (mResumed) startPolling();
        fadeTo(1f, null);
        mDashboard.invalidate();
    }

    private void fadeTo(float target, Runnable done) {
        if (mFade != null) mFade.cancel();
        final float from = mRamp;
        mFade = ValueAnimator.ofFloat(from, target);
        mFade.setDuration((long) (FADE_MS * Math.abs(target - from)));
        mFade.setInterpolator(new LinearInterpolator());
        mFade.addUpdateListener(a -> {
            mRamp = (float) a.getAnimatedValue();
            CcState.writeInt(mState.blBri(true), Math.round(mSleepFromBri * mRamp));
        });
        mFade.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean mCancelled;
            @Override public void onAnimationCancel(android.animation.Animator a) { mCancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (!mCancelled && done != null) done.run();
            }
        });
        mFade.start();
    }

    /** Snap the bottom backlight back if slept or mid-fade, and clear the sleep state. */
    private void restoreBacklightIfSlept() {
        if (!mSleeping && mRamp >= 1f) return;
        if (mFade != null) mFade.cancel();
        int restore = mSleepFromBri;
        if (restore < 8) restore = CcState.readInt(mState.blMax(true), 128);
        CcState.writeInt(mState.blBri(true), restore);
        mSleeping = false;
        mRamp = 1f;
        mMain.removeCallbacks(mPresentWatch);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        final int action = e.getActionMasked();
        final long now = SystemClock.uptimeMillis();
        mLastTouchMs = now;   // any touch, held or moving, keeps the panel awake
        if (action == MotionEvent.ACTION_DOWN) {
            mConsumeGesture = false;
            // A touch wakes the slept screen and is consumed. With Double Tap to Wake the first
            // tap only arms a 450 ms window and the second one wakes.
            if (mSleeping) {
                boolean wakeNow = true;
                if (OptionsView.doubleTapWake()) {
                    wakeNow = mWakeTapMs > 0 && now - mWakeTapMs <= DOUBLE_TAP_MS;
                    mWakeTapMs = wakeNow ? 0 : now;
                }
                mConsumeGesture = true;
                if (wakeNow) wake();
                return true;
            }
        }
        if (mConsumeGesture) return true;
        return super.dispatchTouchEvent(e);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        mLastTouchMs = SystemClock.uptimeMillis();
        if (mSleeping) {
            // any button wakes the panel; the press itself does nothing else
            if (e.getAction() == KeyEvent.ACTION_UP) wake();
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_BUTTON_L1:
                    mPager.setPage(mPager.getPage() - 1, true);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_R1:
                    mPager.setPage(mPager.getPage() + 1, true);
                    return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    // ------------------------------------------------------------------ close app / screenshot

    /**
     * The app (anything but this home) with a task on this display, or the one presenting on
     * it from the other screen, or null. Poll thread.
     */
    private String findBottomApp() {
        final String presenting = presentingApp();
        if (!presenting.isEmpty()) return presenting;
        try {
            final ActivityManager am = mCtx.getSystemService(ActivityManager.class);
            final String self = mCtx.getPackageName();
            for (ActivityManager.RunningTaskInfo t : am.getRunningTasks(32)) {
                if (t.displayId != mState.ownDisplay) continue;
                final ComponentName base = t.baseActivity != null ? t.baseActivity : t.topActivity;
                if (base != null && !self.equals(base.getPackageName())) return base.getPackageName();
            }
        } catch (Throwable t) {
            Log.w(TAG, "task query failed", t);
        }
        return null;
    }

    @Override
    public String bottomApp() {
        return mBottomApp;
    }

    @Override
    public void closeBottomApp() {
        final String pkg = mBottomApp;
        if (pkg == null) return;
        try {
            mCtx.getSystemService(ActivityManager.class).forceStopPackage(pkg);
        } catch (Throwable t) {
            Log.w(TAG, "force-stop " + pkg + " failed", t);
        }
        mBottomApp = null;
        mLastTouchMs = SystemClock.uptimeMillis();
    }

    /** Capture the CONTENT panel (the other screen), not this one, into Pictures/Screenshots. */
    @Override
    public void takeScreenshot() {
        final int display = mState.contentDisplay;
        new Thread(() -> {
            boolean ok = false;
            try {
                final ScreenCapture.SynchronousScreenCaptureListener l =
                        ScreenCapture.createSyncCaptureListener();
                WindowManagerGlobal.getWindowManagerService().captureDisplay(display, null, l);
                final ScreenCapture.ScreenshotHardwareBuffer buf = l.getBuffer();
                if (buf != null) {
                    final Bitmap hw = buf.asBitmap();
                    final Bitmap bmp = hw.copy(Bitmap.Config.ARGB_8888, false);
                    hw.recycle();
                    ok = savePng(bmp);
                    bmp.recycle();
                }
            } catch (Throwable t) {
                Log.w(TAG, "screenshot of display " + display + " failed", t);
            }
            final boolean saved = ok;
            mMain.post(() -> Toast.makeText(mCtx, saved ? R.string.screenshot_saved
                    : R.string.screenshot_failed, Toast.LENGTH_SHORT).show());
        }, "SecondaryHome-shot").start();
    }

    private boolean savePng(Bitmap bmp) throws Exception {
        final String name = "Screenshot_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date()) + ".png";
        final ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
        v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/Screenshots");
        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        final ContentResolver cr = mCtx.getContentResolver();
        final Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (uri == null) return false;
        try (OutputStream o = cr.openOutputStream(uri)) {
            if (o == null || !bmp.compress(Bitmap.CompressFormat.PNG, 100, o)) {
                cr.delete(uri, null, null);
                return false;
            }
        }
        v.clear();
        v.put(MediaStore.MediaColumns.IS_PENDING, 0);
        cr.update(uri, v, null, null);
        return true;
    }

    // ------------------------------------------------------------------ applications page

    private View buildAppsPage() {
        final LayoutInflater inf = LayoutInflater.from(mCtx);
        final View page = inf.inflate(R.layout.activity_home, mPager, false);
        mGrid = page.findViewById(R.id.appGrid);
        mGridLayout = new GridLayoutManager(mCtx, 5);
        mGrid.setLayoutManager(mGridLayout);
        mAdapter = new AppsAdapter();
        mGrid.setAdapter(mAdapter);
        // Scale the grid like the nano CC: 640x480 design space fitted to the panel, 128-unit
        // cells (5 columns at 4:3), so any panel size and aspect gets the same look.
        page.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            final int w = r - l, h = b - t;
            if (w <= 0 || h <= 0) return;
            final float u = Math.min(w / CcDraw.DW, h / CcDraw.DH);
            final int span = Math.max(2, Math.min(8, Math.round(w / u / 128f)));
            final TextView header = page.findViewById(R.id.header);
            header.setTextSize(TypedValue.COMPLEX_UNIT_PX, 11f * u);
            header.setPadding(Math.round(10 * u), Math.round(8 * u), 0, Math.round(10 * u));
            // keep the last row clear of the page dots
            mGrid.setPadding(0, 0, 0, Math.round(30 * u));
            if (span != mGridLayout.getSpanCount() || Math.abs(u - mGridUnit) > 0.001f) {
                mGridUnit = u;
                mGrid.post(() -> {
                    mGridLayout.setSpanCount(span);
                    mAdapter.notifyDataSetChanged();
                });
            }
        });
        return page;
    }

    private void loadApps() {
        if (mLoading) return;
        mLoading = true;
        new Thread(() -> {
            final List<AppEntry> loaded = queryLaunchers();
            mMain.post(() -> {
                mApps.clear();
                mApps.addAll(loaded);
                mAdapter.notifyDataSetChanged();
                if (mPager.getPage() == PAGE_APPS) focusFirst();
                mLoading = false;
            });
        }, "SecondaryHome-load").start();
    }

    private List<AppEntry> queryLaunchers() {
        final List<AppEntry> out = new ArrayList<>();
        final PackageManager pm = mCtx.getPackageManager();
        final Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
        if (ris == null) return out;
        final String self = mCtx.getPackageName();
        for (ResolveInfo ri : ris) {
            if (ri.activityInfo == null) continue;
            final String pkg = ri.activityInfo.packageName;
            if (self.equals(pkg)) continue;
            final CharSequence lc = ri.loadLabel(pm);
            final String label = lc != null ? lc.toString() : pkg;
            Drawable icon;
            try {
                icon = ri.loadIcon(pm);
            } catch (Throwable t) {
                icon = null;
            }
            out.add(new AppEntry(label, pkg, ri.activityInfo.name, icon));
        }
        Collections.sort(out, (a, b) -> a.label.toLowerCase(Locale.ROOT)
                .compareTo(b.label.toLowerCase(Locale.ROOT)));
        return out;
    }

    private void focusFirst() {
        mGrid.post(() -> {
            final RecyclerView.ViewHolder vh = mGrid.findViewHolderForAdapterPosition(0);
            if (vh != null) vh.itemView.requestFocus();
            else mGrid.requestFocus();
        });
    }

    private void launch(AppEntry entry) {
        try {
            final Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(new ComponentName(entry.pkg, entry.activity));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            mShell.startOnOwnDisplay(i);
            mShell.onAppStarted();
        } catch (Throwable t) {
            Toast.makeText(mCtx, mCtx.getString(R.string.launch_failed, entry.label),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private static final class AppEntry {
        final String label;
        final String pkg;
        final String activity;
        final Drawable icon;

        AppEntry(String label, String pkg, String activity, Drawable icon) {
            this.label = label;
            this.pkg = pkg;
            this.activity = activity;
            this.icon = icon;
        }
    }

    private final class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_home_app, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            final AppEntry e = mApps.get(position);
            final float u = mGridUnit;
            // the nano CC cell: 104-unit rows, 56-unit icon, 11-unit label, 8-unit side margins
            final ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
            lp.height = Math.round(92 * u);
            lp.setMargins(Math.round(8 * u), Math.round(2 * u), Math.round(8 * u), Math.round(10 * u));
            holder.itemView.setLayoutParams(lp);
            final ViewGroup.LayoutParams ip = holder.icon.getLayoutParams();
            ip.width = ip.height = Math.round(56 * u);
            holder.icon.setLayoutParams(ip);
            holder.label.setTextSize(TypedValue.COMPLEX_UNIT_PX, 11f * u);
            holder.label.setText(e.label);
            if (e.icon != null) holder.icon.setImageDrawable(e.icon);
            else holder.icon.setImageResource(android.R.drawable.sym_def_app_icon);
            holder.itemView.setOnClickListener(v -> launch(e));
        }

        @Override
        public int getItemCount() {
            return mApps.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView label;

            VH(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.appIcon);
                label = itemView.findViewById(R.id.appLabel);
            }
        }
    }
}
