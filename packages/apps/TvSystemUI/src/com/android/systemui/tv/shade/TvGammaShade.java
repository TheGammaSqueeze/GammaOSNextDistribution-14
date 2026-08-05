/*
 * Copyright (C) 2026 The GammaOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.tv.shade;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.StatusBarNotification;
import android.service.quicksettings.Tile;
import android.util.Log;
import android.view.Choreographer;
import android.view.Display;
import android.view.Gravity;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.android.systemui.CoreStartable;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.qs.pipeline.domain.interactor.CurrentTilesInteractor;
import com.android.systemui.shared.system.InputChannelCompat;
import com.android.systemui.shared.system.InputMonitorCompat;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.NotificationListener;
import com.android.systemui.tv.res.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

/**
 * GammaOS pull-down shade for the TV/desktop build, styled to feel like the real Quick Settings
 * shade: an invisible top-edge strip on each display opens a panel that follows the finger 1:1,
 * flings open or dismisses on release, and hosts the REAL QS tiles (the exact tiles QuickSettings
 * would show, including gammasecondarydisplay = "turn off the top display") rendered as simplified,
 * swipeable, paged tiles, plus brightness/volume sliders and the active notifications. KEYCODE_ALL_APPS
 * toggles it (routed from PhoneWindowManager as a broadcast). Everything but the tiny trigger strips
 * is built on demand.
 */
@SysUISingleton
public class TvGammaShade implements CoreStartable, CommandQueue.Callbacks,
        NotificationListener.NotificationHandler {

    private static final String TAG = "TvGammaShade";
    static final String ACTION_TOGGLE = "com.gammaos.action.TOGGLE_SHADE";

    // GammaOS split-brightness: when on, show a second brightness bar for the secondary display,
    // mirroring what the real QS panel does (QSPanelController).
    private static final String PROP_SPLIT_BRIGHTNESS =
            "persist.gammaos.multidisplay.split_brightness";
    // The secondary panel's brightness in split mode is driven by this raw 1..255 override prop
    // (system_server ignores mirrored writes to the secondary backlight), matching BrightnessController.
    private static final String PROP_SPLIT_BRIGHTNESS_D1_OVERRIDE =
            "sys.gammaos.multidisplay.split_brightness.d1.override";
    private static final String PROP_SPLIT_BRIGHTNESS_D1_CUR =
            "sys.gammaos.multidisplay.split_brightness.d1.cur";

    private static final int COLS = 4;
    private static final int ROWS = 2;
    private static final int TILES_PER_PAGE = COLS * ROWS;

    private final Context mContext;
    private final CommandQueue mCommandQueue;
    private final NotificationListener mNotificationListener;
    private final CurrentTilesInteractor mTilesInteractor;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final Object mTileToken = new Object();

    private DisplayManager mDisplayManager;

    // Per-display top-edge gesture monitors (spy on touches; pilfer only on a real pull-down, so
    // apps keep their top-edge touches - the way SystemUI's edge gestures work).
    private final Map<Integer, InputMonitorCompat> mMonitors = new HashMap<>();
    private final Map<Integer, InputChannelCompat.InputEventReceiver> mReceivers = new HashMap<>();
    private int mTopEdgePx = 48;
    private int mTouchSlop = 16;

    // Gesture state for the open-pull.
    private boolean mGestureAllow;
    private boolean mGesturePilfered;
    private float mGestureDownX;
    private float mGestureDownY;
    private float mGestureDragStartY;

    // Open-panel state (single panel, on whichever display was used).
    private View mRoot;          // full-screen scrim
    private LinearLayout mPanel; // the sliding content
    private WindowManager mPanelWm;
    private Context mPanelCtx;
    private int mPanelDisplayId = -1;
    private LinearLayout mNotifContainer;
    private boolean mNotifAttached;
    private float mPanelH = 1f;

    // Split-brightness: poll while open so the 2nd bar appears/disappears live when the tile toggles.
    private Boolean mSplitApplied;
    private boolean mSplitPollRunning;
    private final Runnable mSplitPoll = new Runnable() {
        @Override
        public void run() {
            if (!mSplitPollRunning || mRoot == null) return;
            applySplitBrightness(mRoot, mPanelDisplayId);
            mMain.postDelayed(this, 300);
        }
    };

    // Live QS tiles bound while open.
    private final Map<QSTile, TileHolder> mTileViews = new HashMap<>();
    private final List<QSTile> mBoundTiles = new ArrayList<>();
    private final Map<QSTile, QSTile.Callback> mTileCallbacks = new HashMap<>();

    // Drag state.
    private VelocityTracker mVt;
    private float mDownRawY;
    private float mDownTy;
    private boolean mDragging;
    private boolean mDragMoved;
    private ValueAnimator mSettle;
    private float mDensity = 2f;

    private BroadcastReceiver mToggleReceiver;

    @Inject
    public TvGammaShade(Context context, CommandQueue commandQueue,
            NotificationListener notificationListener,
            CurrentTilesInteractor tilesInteractor) {
        mContext = context;
        mCommandQueue = commandQueue;
        mNotificationListener = notificationListener;
        mTilesInteractor = tilesInteractor;
    }

    @Override
    public void start() {
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mDensity = mContext.getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();
        mTopEdgePx = (int) dp(40);
        mCommandQueue.addCallback(this);
        registerToggleReceiver();
        setupMonitors();
        // A secondary panel (e.g. the RG DS bottom screen) can be added after we start, so keep
        // the per-display monitors in sync as displays come and go - otherwise the shade would
        // only ever work on whichever displays existed at boot.
        if (mDisplayManager != null) {
            try {
                mDisplayManager.registerDisplayListener(mDisplayListener, mMain);
            } catch (Throwable t) {
                Log.w(TAG, "could not register display listener", t);
            }
        }
    }

    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                    setupMonitor(displayId);
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                    teardownMonitor(displayId);
                }

                @Override
                public void onDisplayChanged(int displayId) {
                }
            };

    private void teardownMonitor(int displayId) {
        final InputChannelCompat.InputEventReceiver recv = mReceivers.remove(displayId);
        if (recv != null) {
            try { recv.dispose(); } catch (Throwable ignored) {}
        }
        final InputMonitorCompat mon = mMonitors.remove(displayId);
        if (mon != null) {
            try { mon.dispose(); } catch (Throwable ignored) {}
        }
        if (mPanelDisplayId == displayId) {
            closeImmediate();
        }
    }

    private void registerToggleReceiver() {
        mToggleReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                mMain.post(TvGammaShade.this::toggleShade);
            }
        };
        try {
            mContext.registerReceiver(mToggleReceiver, new IntentFilter(ACTION_TOGGLE),
                    Context.RECEIVER_EXPORTED);
        } catch (Throwable t) {
            Log.w(TAG, "could not register toggle receiver", t);
        }
    }

    // ---------------------------------------------------------------------
    // Per-display top-edge gesture monitors (spy + pilfer, like SystemUI edge gestures)
    // ---------------------------------------------------------------------

    private void setupMonitors() {
        if (mDisplayManager == null) return;
        for (Display d : mDisplayManager.getDisplays()) {
            setupMonitor(d.getDisplayId());
        }
    }

    private void setupMonitor(int displayId) {
        if (mMonitors.containsKey(displayId)) return;
        try {
            final InputMonitorCompat mon = new InputMonitorCompat("gamma-shade", displayId);
            final InputChannelCompat.InputEventReceiver recv = mon.getInputReceiver(
                    Looper.getMainLooper(), Choreographer.getInstance(),
                    ev -> onGestureEvent(displayId, mon, ev));
            mMonitors.put(displayId, mon);
            mReceivers.put(displayId, recv);
        } catch (Throwable t) {
            Log.w(TAG, "could not create gesture monitor on display " + displayId, t);
        }
    }

    /**
     * Spy on touches for a display. Ordinary touches are NOT consumed (apps keep their top-edge
     * touches); only when a deliberate downward swipe starts at the very top edge, with the shade
     * closed, do we pilfer the gesture and drive the panel down 1:1 with the finger.
     */
    private void onGestureEvent(int displayId, InputMonitorCompat mon, InputEvent ev) {
        if (!(ev instanceof MotionEvent)) return;
        final MotionEvent me = (MotionEvent) ev;
        switch (me.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mGesturePilfered = false;
                mGestureAllow = mPanel == null && me.getY() <= mTopEdgePx;
                mGestureDownX = me.getRawX();
                mGestureDownY = me.getRawY();
                if (mVt != null) { mVt.recycle(); mVt = null; }
                if (mGestureAllow) {
                    mVt = VelocityTracker.obtain();
                    mVt.addMovement(me);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mGesturePilfered) {
                    if (mVt != null) mVt.addMovement(me);
                    setTy(-mPanelH + (me.getRawY() - mGestureDragStartY));
                } else if (mGestureAllow) {
                    if (mVt != null) mVt.addMovement(me);
                    final float dy = me.getRawY() - mGestureDownY;
                    final float dx = Math.abs(me.getRawX() - mGestureDownX);
                    if (dy < 0 || dx > dy) {
                        mGestureAllow = false;   // upward / horizontal -> leave it to the app
                    } else if (dy > mTouchSlop && dy > dx) {
                        cancelSettle();
                        if (openShade(displayId, false)) {
                            mon.pilferPointers();
                            mGesturePilfered = true;
                            mGestureDragStartY = me.getRawY();
                            setTy(-mPanelH + (me.getRawY() - mGestureDragStartY));
                        } else {
                            mGestureAllow = false;
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mGesturePilfered) {
                    float vy = 0f;
                    if (mVt != null) {
                        mVt.addMovement(me);
                        mVt.computeCurrentVelocity(1000);
                        vy = mVt.getYVelocity();
                    }
                    settle(commitOpen(vy), vy);
                }
                if (mVt != null) { mVt.recycle(); mVt = null; }
                mGestureAllow = false;
                mGesturePilfered = false;
                break;
        }
    }

    /**
     * Finger-follow drag for the open shade. Attached both to the panel (empty areas) and to the
     * full-screen scrim, so an up-swipe from ANYWHERE (including the bottom of the screen) drags the
     * shade 1:1 with the finger and dismisses it by fling or position, exactly like the real shade.
     * When {@code tapCloses} is true (the scrim), a tap that never turns into a drag dismisses too.
     */
    private void beginShadeDrag(MotionEvent ev) {
        cancelSettle();
        if (mVt != null) { mVt.recycle(); }
        mVt = VelocityTracker.obtain();
        mVt.addMovement(ev);
        mDownRawY = ev.getRawY();
        mDownTy = getTy();
        mDragging = true;
        mDragMoved = false;
    }

    private boolean onShadeDragTouch(MotionEvent ev, boolean tapCloses) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                beginShadeDrag(ev);
                return true;
            case MotionEvent.ACTION_MOVE:
                // The custom root can hand us a gesture already in progress (it intercepted a drag
                // that began over a child), so start tracking here if we never saw the DOWN.
                if (!mDragging) beginShadeDrag(ev);
                if (mVt != null) mVt.addMovement(ev);
                final float dy = ev.getRawY() - mDownRawY;
                if (Math.abs(dy) > mTouchSlop) mDragMoved = true;
                setTy(mDownTy + dy);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!mDragging) return false;
                float vy = 0f;
                if (mVt != null) {
                    mVt.addMovement(ev);
                    mVt.computeCurrentVelocity(1000);
                    vy = mVt.getYVelocity();
                    mVt.recycle();
                    mVt = null;
                }
                mDragging = false;
                if (!mDragMoved && tapCloses) {
                    // Tap on the dim area: dismiss.
                    closeShade();
                } else {
                    settle(commitOpen(vy), vy);
                }
                return true;
        }
        return false;
    }

    /** Stock rule: fast fling (>=250dp/s) commits by direction; slow release commits by position. */
    private boolean commitOpen(float vy) {
        final float minFling = 250f * mDensity;
        if (Math.abs(vy) < minFling) return frac() > 0.5f;
        return vy > 0f;
    }

    private float getTy() {
        return mPanel != null ? mPanel.getTranslationY() : -mPanelH;
    }

    private float frac() {
        return 1f + getTy() / mPanelH;
    }

    private void setTy(float ty) {
        if (mPanel == null) return;
        if (ty < -mPanelH) ty = -mPanelH;
        if (ty > 0f) ty = 0f;
        mPanel.setTranslationY(ty);
        final float f = 1f + ty / mPanelH;
        if (mRoot != null && mRoot.getBackground() != null) {
            mRoot.getBackground().setAlpha((int) (f * 102f));   // #66 dim scaled by openness
        }
    }

    private void settle(boolean open, float vy) {
        if (mPanel == null) return;
        final float target = open ? 0f : -mPanelH;
        final float from = getTy();
        final float dist = Math.abs(target - from);
        if (dist < 1f) {
            setTy(target);
            if (!open) removePanel();
            return;
        }
        final float v = Math.max(Math.abs(vy), 250f * mDensity);
        long dur = (long) Math.min(0.75f * dist / v * 1000f, open ? 480f : 360f);
        if (dur < 120L) dur = 120L;
        mSettle = ValueAnimator.ofFloat(from, target);
        mSettle.setDuration(dur);
        mSettle.setInterpolator(new PathInterpolator(0f, 0f, 0.2f, 1f)); // decel, stock feel
        mSettle.addUpdateListener(a -> setTy((float) a.getAnimatedValue()));
        mSettle.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (!open) removePanel();
            }
        });
        mSettle.start();
    }

    private void cancelSettle() {
        if (mSettle != null) {
            mSettle.cancel();
            mSettle = null;
        }
    }

    // ---------------------------------------------------------------------
    // Panel lifecycle
    // ---------------------------------------------------------------------

    /** Add the panel (hidden above the top). Returns false on failure. */
    private boolean openShade(int displayId, boolean animateOpen) {
        if (mPanel != null) {
            if (mPanelDisplayId == displayId) {
                if (animateOpen) settle(true, 0f);
                return true;
            }
            closeImmediate();
        }
        final Display display = mDisplayManager != null
                ? mDisplayManager.getDisplay(displayId) : null;
        if (display == null) return false;
        mPanelCtx = mContext.createDisplayContext(display);
        mPanelWm = mPanelCtx.getSystemService(WindowManager.class);
        if (mPanelWm == null) { mPanelCtx = null; return false; }
        mPanelDisplayId = displayId;

        mRoot = LayoutInflater.from(mPanelCtx).inflate(R.layout.tv_gamma_shade, null);
        mRoot.setFocusableInTouchMode(true);
        mRoot.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP
                    && (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE)) {
                closeShade();
                return true;
            }
            return false;
        });
        // The custom root intercepts a vertical drag anywhere (notification list, the blank area
        // below it, the dim scrim, the very bottom edge) so an up-swipe/flick dismisses the shade
        // and follows the finger; a tap on the dim area closes.
        if (mRoot instanceof GammaShadeRoot) {
            ((GammaShadeRoot) mRoot).setDragListener(ev -> onShadeDragTouch(ev, true));
        }
        mPanel = mRoot.findViewById(R.id.shade_panel);

        wireControls(mRoot, mPanelCtx, displayId);

        // Measure the (fixed-height) panel so we know how far to slide it.
        final int wPx = mPanelCtx.getResources().getDisplayMetrics().widthPixels;
        mPanel.measure(View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        mPanelH = Math.max(1, mPanel.getMeasuredHeight());
        mPanel.setTranslationY(-mPanelH);
        if (mRoot.getBackground() != null) mRoot.getBackground().setAlpha(0);

        WindowManager.LayoutParams lp = panelParams(
                WindowManager.LayoutParams.TYPE_NOTIFICATION_SHADE);
        try {
            mPanelWm.addView(mRoot, lp);
        } catch (Throwable t) {
            try {
                mPanelWm.addView(mRoot, panelParams(
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY));
            } catch (Throwable t2) {
                Log.w(TAG, "could not add shade panel", t2);
                mRoot = null; mPanel = null; mPanelWm = null; mPanelCtx = null; mPanelDisplayId = -1;
                return false;
            }
        }
        mRoot.requestFocus();
        ensureNotifHandler();
        rebuildNotifications();
        if (animateOpen) settle(true, 0f);
        return true;
    }

    private WindowManager.LayoutParams panelParams(int type) {
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP;
        lp.setTitle("GammaShade");
        return lp;
    }

    private void closeShade() {
        if (mPanel == null) return;
        cancelSettle();
        settle(false, 0f);
    }

    private void closeImmediate() {
        cancelSettle();
        removePanel();
    }

    private void removePanel() {
        mSplitPollRunning = false;
        mMain.removeCallbacks(mSplitPoll);
        mSplitApplied = null;
        unbindTiles();
        if (mRoot != null && mPanelWm != null) {
            try {
                mPanelWm.removeView(mRoot);
            } catch (Throwable ignored) {
            }
        }
        mRoot = null;
        mPanel = null;
        mPanelWm = null;
        mPanelCtx = null;
        mPanelDisplayId = -1;
        mNotifContainer = null;
    }

    private void toggleShade() {
        if (mPanel != null && frac() > 0.5f) {
            closeShade();
        } else if (mPanel != null) {
            settle(true, 0f);
        } else {
            openShade(Display.DEFAULT_DISPLAY, true);
        }
    }

    private void ensureNotifHandler() {
        if (mNotifAttached) return;
        try {
            mNotificationListener.addNotificationHandler(this);
            mNotifAttached = true;
        } catch (Throwable t) {
            Log.w(TAG, "could not attach notification handler", t);
        }
    }

    // ---------------------------------------------------------------------
    // Controls
    // ---------------------------------------------------------------------

    private void wireControls(View root, Context ctx, int displayId) {
        buildQsPager(ctx, root);

        mNotifContainer = root.findViewById(R.id.notif_container);
        final View clearAll = root.findViewById(R.id.notif_clear_all);
        if (clearAll != null) {
            clearAll.setOnClickListener(v -> {
                try {
                    mNotificationListener.cancelAllNotifications();
                } catch (Throwable ignored) {
                }
                mMain.postDelayed(this::rebuildNotifications, 150);
            });
        }

        // Brightness. The primary bar drives this display; the secondary bar (split mode only) is
        // managed by applySplitBrightness, which is polled so it appears/disappears live when the
        // Split Brightness tile is toggled while the shade is open.
        wireBrightnessSlider(root.findViewById(R.id.brightness), displayId);
        mSplitApplied = null;
        applySplitBrightness(root, displayId);
        mSplitPollRunning = true;
        mMain.removeCallbacks(mSplitPoll);
        mMain.postDelayed(mSplitPoll, 300);

        final SeekBar vol = root.findViewById(R.id.volume);
        if (vol != null) {
            final AudioManager am = ctx.getSystemService(AudioManager.class);
            if (am != null) {
                final int stream = AudioManager.STREAM_MUSIC;
                vol.setMax(am.getStreamMaxVolume(stream));
                vol.setProgress(am.getStreamVolume(stream));
                vol.setOnSeekBarChangeListener(new SimpleSeek() {
                    @Override
                    public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                        if (!fromUser) return;
                        try {
                            am.setStreamVolume(stream, p, 0);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        }
    }

    /** Wire one brightness bar to a specific display, initialised from its current brightness. */
    private void wireBrightnessSlider(SeekBar sb, int displayId) {
        if (sb == null) return;
        final DisplayManager dm = mDisplayManager;
        sb.setMax(1000);
        float cur = 0.5f;
        if (dm != null) {
            try {
                float b = dm.getBrightness(displayId);
                if (b >= 0f && b <= 1f) cur = b;
            } catch (Throwable ignored) {
            }
        }
        sb.setProgress((int) (clamp01(cur) * 1000f));
        sb.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser || dm == null) return;
                float val = 0.02f + 0.98f * (p / 1000f);
                try {
                    dm.setBrightness(displayId, clamp01(val));
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /**
     * Show/hide + wire the secondary brightness bar according to split-brightness state. Called on
     * open and polled, so the bar appears/disappears immediately when the Split Brightness tile is
     * toggled while the shade is open. No-op when the state has not changed.
     */
    private void applySplitBrightness(View root, int displayId) {
        final boolean split = SystemProperties.getBoolean(PROP_SPLIT_BRIGHTNESS, false);
        if (mSplitApplied != null && mSplitApplied == split) return;
        mSplitApplied = split;
        final View row = root.findViewById(R.id.brightness_secondary_row);
        if (row == null) return;
        if (!split) {
            row.setVisibility(View.GONE);
            return;
        }
        row.setVisibility(View.VISIBLE);
        wireSecondarySplitSlider(root.findViewById(R.id.brightness_secondary));
    }

    /**
     * The secondary panel's bar in split mode. system_server ignores mirrored/global brightness
     * writes to the secondary backlight and only honours the d1.override raw (1..255) value, so we
     * write that directly (matching BrightnessController), not DisplayManager.setBrightness().
     */
    private void wireSecondarySplitSlider(SeekBar sb) {
        if (sb == null) return;
        sb.setMax(1000);
        int cur = SystemProperties.getInt(PROP_SPLIT_BRIGHTNESS_D1_CUR, 128);
        if (cur < 1) cur = 1;
        if (cur > 255) cur = 255;
        sb.setProgress(Math.round(cur / 255f * 1000f));
        sb.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser) return;
                float val = 0.02f + 0.98f * (p / 1000f);
                int v = Math.round(clamp01(val) * 255f);
                if (v < 1) v = 1;
                if (v > 255) v = 255;
                try {
                    SystemProperties.set(PROP_SPLIT_BRIGHTNESS_D1_OVERRIDE, Integer.toString(v));
                    SystemProperties.reportSyspropChanged();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    // ---------------------------------------------------------------------
    // Quick-setting tiles (the REAL live tiles, rendered simplified, paged)
    // ---------------------------------------------------------------------

    private void buildQsPager(Context ctx, View root) {
        final ViewPager2 pager = root.findViewById(R.id.qs_pager);
        final LinearLayout dots = root.findViewById(R.id.qs_dots);
        if (pager == null) return;

        List<QSTile> tiles;
        try {
            tiles = new ArrayList<>(mTilesInteractor.getCurrentQSTiles());
        } catch (Throwable t) {
            Log.w(TAG, "could not read QS tiles", t);
            tiles = new ArrayList<>();
        }

        final List<View> pages = new ArrayList<>();
        for (int i = 0; i < tiles.size(); i += TILES_PER_PAGE) {
            final List<QSTile> pageTiles = tiles.subList(i,
                    Math.min(i + TILES_PER_PAGE, tiles.size()));
            pages.add(buildQsPage(ctx, pageTiles));
        }
        if (pages.isEmpty()) {
            pages.add(new View(ctx));
        }

        pager.setAdapter(new PageAdapter(ctx, pages));
        pager.setOffscreenPageLimit(1);

        // Page dots
        if (dots != null && pages.size() > 1) {
            dots.removeAllViews();
            for (int i = 0; i < pages.size(); i++) {
                final View dot = new View(ctx);
                final LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                        (int) dp(6), (int) dp(6));
                dlp.leftMargin = (int) dp(3);
                dlp.rightMargin = (int) dp(3);
                dot.setLayoutParams(dlp);
                dot.setBackgroundResource(R.drawable.gamma_shade_grabber);
                dot.setAlpha(i == 0 ? 1f : 0.35f);
                dots.addView(dot);
            }
            pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    for (int i = 0; i < dots.getChildCount(); i++) {
                        dots.getChildAt(i).setAlpha(i == position ? 1f : 0.35f);
                    }
                }
            });
        }

        // Bind live state for each tile.
        for (QSTile tile : tiles) {
            final TileHolder h = mTileViews.get(tile);
            if (h == null) continue;
            try {
                tile.setListening(mTileToken, true);
            } catch (Throwable ignored) {
            }
            final QSTile.Callback cb = new QSTile.Callback() {
                @Override
                public void onStateChanged(QSTile.State state) {
                    mMain.post(() -> renderTile(tile));
                }
            };
            try {
                tile.addCallback(cb);
            } catch (Throwable ignored) {
            }
            mTileCallbacks.put(tile, cb);
            mBoundTiles.add(tile);
            renderTile(tile);
        }
    }

    private View buildQsPage(Context ctx, List<QSTile> pageTiles) {
        final LayoutInflater inf = LayoutInflater.from(ctx);
        final LinearLayout page = new LinearLayout(ctx);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        for (int r = 0; r < ROWS; r++) {
            final LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            final LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            page.addView(row, rlp);
            for (int c = 0; c < COLS; c++) {
                final int idx = r * COLS + c;
                final LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                clp.setMargins((int) dp(4), (int) dp(4), (int) dp(4), (int) dp(4));
                if (idx < pageTiles.size()) {
                    final QSTile tile = pageTiles.get(idx);
                    final View tv = inf.inflate(R.layout.tv_gamma_shade_tile, row, false);
                    tv.setLayoutParams(clp);
                    final TileHolder h = new TileHolder(
                            tv, tv.findViewById(R.id.tile_icon), tv.findViewById(R.id.tile_label));
                    tv.setOnClickListener(v -> {
                        try { tile.click(null); } catch (Throwable ignored) {}
                    });
                    tv.setOnLongClickListener(v -> {
                        try { tile.longClick(null); } catch (Throwable ignored) {}
                        closeShade();
                        return true;
                    });
                    mTileViews.put(tile, h);
                    row.addView(tv);
                } else {
                    final View spacer = new View(ctx);
                    spacer.setLayoutParams(clp);
                    row.addView(spacer);
                }
            }
        }
        return page;
    }

    private void renderTile(QSTile tile) {
        final TileHolder h = mTileViews.get(tile);
        if (h == null) return;
        QSTile.State s;
        try {
            s = tile.getState();
        } catch (Throwable t) {
            return;
        }
        if (s == null) return;
        h.label.setText(s.label != null ? s.label : "");
        QSTile.Icon ic = s.icon;
        if (ic == null && s.iconSupplier != null) {
            try { ic = s.iconSupplier.get(); } catch (Throwable ignored) {}
        }
        if (ic != null) {
            try {
                final Drawable d = ic.getDrawable(mPanelCtx != null ? mPanelCtx : mContext);
                h.icon.setImageDrawable(d);
            } catch (Throwable ignored) {
            }
        }
        final boolean active = s.state == Tile.STATE_ACTIVE;
        final boolean unavailable = s.state == Tile.STATE_UNAVAILABLE;
        h.view.setActivated(active);
        h.view.setAlpha(unavailable ? 0.4f : 1f);
        h.view.setEnabled(!unavailable);
    }

    private void unbindTiles() {
        for (QSTile tile : mBoundTiles) {
            final QSTile.Callback cb = mTileCallbacks.get(tile);
            try {
                if (cb != null) tile.removeCallback(cb);
            } catch (Throwable ignored) {
            }
            try {
                tile.setListening(mTileToken, false);
            } catch (Throwable ignored) {
            }
        }
        mBoundTiles.clear();
        mTileCallbacks.clear();
        mTileViews.clear();
    }

    private static final class TileHolder {
        final View view;
        final ImageView icon;
        final TextView label;

        TileHolder(View view, ImageView icon, TextView label) {
            this.view = view;
            this.icon = icon;
            this.label = label;
        }
    }

    private static final class PageAdapter extends RecyclerView.Adapter<PageAdapter.VH> {
        private final Context mCtx;
        private final List<View> mPages;

        PageAdapter(Context ctx, List<View> pages) {
            mCtx = ctx;
            mPages = pages;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final android.widget.FrameLayout c = new android.widget.FrameLayout(mCtx);
            c.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return new VH(c);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            final android.widget.FrameLayout c = (android.widget.FrameLayout) holder.itemView;
            c.removeAllViews();
            final View page = mPages.get(position);
            if (page.getParent() instanceof ViewGroup) {
                ((ViewGroup) page.getParent()).removeView(page);
            }
            c.addView(page);
        }

        @Override
        public int getItemCount() {
            return mPages.size();
        }

        static final class VH extends RecyclerView.ViewHolder {
            VH(@NonNull View itemView) {
                super(itemView);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Notifications
    // ---------------------------------------------------------------------

    private void rebuildNotifications() {
        if (mNotifContainer == null || mRoot == null) return;
        mNotifContainer.removeAllViews();
        StatusBarNotification[] active;
        try {
            active = mNotificationListener.getActiveNotifications();
        } catch (Throwable t) {
            active = null;
        }
        final LayoutInflater inf = LayoutInflater.from(
                mPanelCtx != null ? mPanelCtx : mContext);
        final View empty = mRoot.findViewById(R.id.notif_empty);
        int shown = 0;
        if (active != null) {
            for (StatusBarNotification sbn : active) {
                if (sbn == null || sbn.getNotification() == null) continue;
                final View row = inf.inflate(R.layout.tv_gamma_shade_notif, mNotifContainer, false);
                bindNotif(row, sbn);
                mNotifContainer.addView(row);
                shown++;
            }
        }
        if (empty != null) {
            empty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void bindNotif(View row, StatusBarNotification sbn) {
        final android.app.Notification n = sbn.getNotification();
        final CharSequence title = n.extras != null
                ? n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE) : null;
        final CharSequence text = n.extras != null
                ? n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT) : null;

        final TextView tvTitle = row.findViewById(R.id.n_title);
        final TextView tvText = row.findViewById(R.id.n_text);
        final ImageView icon = row.findViewById(R.id.n_icon);
        if (tvTitle != null) tvTitle.setText(title != null ? title : sbn.getPackageName());
        if (tvText != null) {
            tvText.setText(text != null ? text : "");
            tvText.setVisibility(text != null && text.length() > 0 ? View.VISIBLE : View.GONE);
        }
        if (icon != null) {
            try {
                android.graphics.drawable.Icon small = n.getSmallIcon();
                icon.setImageDrawable(small != null ? small.loadDrawable(mContext) : null);
            } catch (Throwable ignored) {
            }
        }
        final PendingIntent ci = n.contentIntent;
        row.setOnClickListener(v -> {
            if (ci != null) {
                try { ci.send(); } catch (Throwable ignored) {}
            }
            if (sbn.isClearable()) {
                try { mNotificationListener.cancelNotification(sbn.getKey()); } catch (Throwable ignored) {}
            }
            closeShade();
        });
    }

    // ---------------------------------------------------------------------
    // CommandQueue.Callbacks
    // ---------------------------------------------------------------------

    @Override
    public void animateExpandNotificationsPanel() {
        mMain.post(() -> openShade(Display.DEFAULT_DISPLAY, true));
    }

    @Override
    public void animateExpandSettingsPanel(String subPanel) {
        mMain.post(() -> openShade(Display.DEFAULT_DISPLAY, true));
    }

    @Override
    public void animateCollapsePanels(int flags, boolean force) {
        mMain.post(this::closeShade);
    }

    // ---------------------------------------------------------------------
    // NotificationListener.NotificationHandler
    // ---------------------------------------------------------------------

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        mMain.post(this::rebuildNotifications);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap) {
        mMain.post(this::rebuildNotifications);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap, int reason) {
        mMain.post(this::rebuildNotifications);
    }

    @Override
    public void onNotificationRankingUpdate(RankingMap rankingMap) {
    }

    @Override
    public void onNotificationsInitialized() {
        mMain.post(this::rebuildNotifications);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private float dp(float v) {
        return v * mDensity;
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
