package com.gammaos.secondaryhome;

import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.window.ScreenCapture;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
 * Home of the secondary (bottom) display in normal Android mode. It carries the nano Control
 * Centre over to Android: three swipeable pages, the dashboard (brightness, control tiles,
 * volume, thermal and performance), the application grid (apps open on this display) and the
 * screen options (double tap to wake, idle screen timeout). The bottom screen sleeps with the
 * same 0.6 s backlight fade as the nano CC, from the Sleep tile or after the idle timeout.
 */
public class SecondaryHomeActivity extends AppCompatActivity implements DashboardView.Host {
    private static final String TAG = "SecondaryHome";
    private static final int PAGE_DASHBOARD = 0, PAGE_APPS = 1, PAGE_OPTIONS = 2;
    private static final long POLL_MS = 500;
    private static final long FADE_MS = 600;
    private static final long DOUBLE_TAP_MS = 450;

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private HandlerThread mPollThread;
    private Handler mPoll;
    private boolean mResumed;

    private CcState mState;
    private CcPager mPager;
    private DashboardView mDashboard;
    private OptionsView mOptions;
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

    // ---- the app this home launched on its own panel ("Close App" tile) ----
    private String mLaunchedPkg;
    private volatile String mBottomApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mState = new CcState(this);
        resolveDisplays();

        mPager = new CcPager(this);
        mPager.setBackgroundColor(CcDraw.BG);
        mDashboard = new DashboardView(this, mState, this);
        mOptions = new OptionsView(this);
        mPager.addView(mDashboard);
        mPager.addView(buildAppsPage());
        mPager.addView(mOptions);
        mPager.setListener(page -> {
            if (page == PAGE_APPS) focusFirst();
            else mPager.getChildAt(page).invalidate();
        });
        setContentView(mPager);

        loadApps();
    }

    @Override
    protected void onDestroy() {
        restoreBacklightIfSlept();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mResumed = true;
        resolveDisplays();
        mLastTouchMs = SystemClock.uptimeMillis();   // a freshly shown home never instantly auto-sleeps
        startPolling();
        loadApps();
        mPager.getChildAt(mPager.getPage()).invalidate();
    }

    @Override
    protected void onPause() {
        mResumed = false;
        stopPolling();
        // Whoever gets this panel next must not inherit a dark backlight.
        restoreBacklightIfSlept();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed on this display: show the dashboard, like the nano CC.
        if (mPager != null) mPager.setPage(PAGE_DASHBOARD, true);
    }

    private void resolveDisplays() {
        final Display own = getDisplay();
        final int ownId = own != null ? own.getDisplayId() : Display.DEFAULT_DISPLAY;
        int content = Display.DEFAULT_DISPLAY;
        if (ownId == Display.DEFAULT_DISPLAY) {
            final DisplayManager dm = getSystemService(DisplayManager.class);
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

    private final Runnable mUiTick = () -> {
        if (!mResumed) return;
        // idle auto-sleep, same gate as the nano CC (awake, not dimming, timeout passed)
        final int idleMs = OptionsView.sleepTimeoutMs();
        if (idleMs > 0 && !mSleeping && SystemClock.uptimeMillis() - mLastTouchMs >= idleMs) {
            beginSleep();
        }
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
        // the panel is dark: no stats work while slept, only the touch path stays live
        fadeTo(0f, this::stopPolling);
        mDashboard.invalidate();
    }

    private void wake() {
        if (!mSleeping) return;
        mSleeping = false;
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
    }

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

    /** The app this home launched, while it still has a task on this display. Poll thread. */
    private String findBottomApp() {
        final String pkg = mLaunchedPkg;
        if (pkg == null) return null;
        try {
            final ActivityManager am = getSystemService(ActivityManager.class);
            for (ActivityManager.RunningTaskInfo t : am.getRunningTasks(32)) {
                if (t.displayId != mState.ownDisplay) continue;
                final ComponentName base = t.baseActivity != null ? t.baseActivity : t.topActivity;
                if (base != null && pkg.equals(base.getPackageName())) return pkg;
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
            getSystemService(ActivityManager.class).forceStopPackage(pkg);
        } catch (Throwable t) {
            Log.w(TAG, "force-stop " + pkg + " failed", t);
        }
        mBottomApp = null;
        mLaunchedPkg = null;
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
            mMain.post(() -> Toast.makeText(this, saved ? R.string.screenshot_saved
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
        final ContentResolver cr = getContentResolver();
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
        final LayoutInflater inf = LayoutInflater.from(this);
        final View page = inf.inflate(R.layout.activity_home, mPager, false);
        mGrid = page.findViewById(R.id.appGrid);
        mGridLayout = new GridLayoutManager(this, 5);
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
        final PackageManager pm = getPackageManager();
        final Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
        if (ris == null) return out;
        final String self = getPackageName();
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
            // startActivity from this activity keeps the launch on this (the secondary) display
            startActivity(i);
            mLaunchedPkg = entry.pkg;
        } catch (Throwable t) {
            Toast.makeText(this, getString(R.string.launch_failed, entry.label),
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
