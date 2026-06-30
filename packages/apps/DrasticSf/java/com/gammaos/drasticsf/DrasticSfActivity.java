/*
 * Copyright (C) 2026 GammaOS
 *
 * DrasticSfActivity: the SurfaceFlinger host for drastic-nano.
 *
 * On a DRM-direct device the home owns the panel, so a raw SurfaceFlinger layer
 * from the drastic-nano binary never shows. This activity is an ordinary app
 * whose SurfaceView SurfaceFlinger composites normally, so it hands its Surface
 * to the drastic-nano binary (which runs as root in its own process) over a
 * tiny binder transaction, and drastic renders into THAT surface. The frame
 * therefore reaches the panel like any app. This activity starts the binary
 * (start_sf) and watches the service, finishing when drastic exits.
 */
package com.gammaos.drasticsf;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

public class DrasticSfActivity extends Activity implements SurfaceHolder.Callback {
    // Must match SfDisplayBackend.cpp (the service name + interface token).
    private static final String SERVICE = "drastic.sf.surface";
    private static final String IFACE = "com.gammaos.drasticsf.IDrasticSurface";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mProvide = this::tryProvideSurface;
    private final Runnable mPoll = this::poll;
    private final Runnable mSplashTick = this::splashTick;
    // A no-op Back callback. drastic-nano reads the controller directly and owns
    // exit (short Back = its overlay, long-press Back = clean save+quit). If we let
    // the activity's default Back finish us, the SurfaceView is torn down while the
    // drastic PROCESS keeps running -> a black screen. So we swallow Back here and
    // let drastic decide; when drastic exits, poll() finishes us back to the menu.
    private final OnBackInvokedCallback mNoOpBack = () -> { /* swallow Back */ };
    private Surface mSurface;
    private boolean mProvided = false;
    private boolean mStartedBinary = false;
    private boolean mSawRunning = false;
    private int mGoneTicks = 0;
    // Full-screen "Loading..." cover shown over the SurfaceView until drastic
    // raises sys.gammaos.drastic_nano.rendering at its first present, so the cold
    // start (a few seconds of blank surface) reads as loading, not a black hang.
    private TextView mSplash;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        SurfaceView sv = new SurfaceView(this);
        // Opaque RGBX, matching the EGL config drastic-nano uses for the window
        // surface (getEglConfig wantAlpha=false).
        sv.getHolder().setFormat(PixelFormat.RGBX_8888);
        sv.getHolder().addCallback(this);
        sv.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        // SurfaceView (zOrderOnTop false, default) sits behind the window; the
        // splash is an opaque sibling drawn over it, so it cleanly covers the
        // blank surface during the cold start and is set GONE once drastic renders.
        FrameLayout root = new FrameLayout(this);
        root.addView(sv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        mSplash = new TextView(this);
        mSplash.setText("Loading...");
        mSplash.setTextColor(Color.WHITE);
        mSplash.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        mSplash.setGravity(Gravity.CENTER);
        mSplash.setBackgroundColor(Color.BLACK);
        root.addView(mSplash, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        // Swallow the predictive-back gesture/button so the framework never
        // finishes us on Back (the log shows it registers a default finish
        // callback). drastic-nano handles Back itself via raw evdev.
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, mNoOpBack);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Swallow Back at the key path too (controller Back). drastic-nano reads
        // the same evdev independently, so its short-press/long-press-exit logic
        // still runs; we just must not let Android finish the activity.
        if (keyCode == KeyEvent.KEYCODE_BACK) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true;
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // Consume; see mNoOpBack. Deliberately do NOT call super (which finishes).
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSurface = holder.getSurface();
        mProvided = false;
        tryProvideSurface();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // The SurfaceView surface can be recreated/reconfigured while the display
        // hands off from DRM to SurfaceFlinger (the takeover relayout). Re-hand the
        // current surface to drastic-nano whenever that happens; the native side
        // dedups by the underlying producer, so a no-change callback is harmless,
        // but a genuinely new producer gets re-adopted instead of leaving drastic
        // rendering into the dead pre-takeover surface (a black panel).
        mSurface = holder.getSurface();
        mProvided = false;
        tryProvideSurface();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mSurface = null;
        mHandler.removeCallbacks(mProvide);
    }

    // Hand our Surface to the drastic-nano binder service. The binary publishes
    // the service shortly after it starts (onResume fires start_sf), so retry
    // until it is up.
    private void tryProvideSurface() {
        if (mProvided || mSurface == null || !mSurface.isValid()) return;
        IBinder b = ServiceManager.getService(SERVICE);
        if (b == null) {
            mHandler.postDelayed(mProvide, 200);
            return;
        }
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(IFACE);
            mSurface.writeToParcel(data, 0);
            b.transact(IBinder.FIRST_CALL_TRANSACTION, data, null, IBinder.FLAG_ONEWAY);
            mProvided = true;
        } catch (RemoteException e) {
            mHandler.postDelayed(mProvide, 200);
        } finally {
            data.recycle();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // We are the foreground app now (the framework launched us via launch_app
        // and the home has done its DRM->SurfaceFlinger handoff, exactly like any
        // app). Start the drastic-nano binary in SF mode; it renders into our
        // SurfaceView surface, which we hand over once it is created. SELinux is
        // permissive on these builds so the app may set this property.
        if (!mStartedBinary) {
            mStartedBinary = true;
            // Clear any stale "rendering" from a prior session BEFORE starting the
            // binary, so the splash stays up until THIS session's first frame.
            SystemProperties.set("sys.gammaos.drastic_nano.rendering", "0");
            SystemProperties.set("sys.gammaos.drastic_nano.start_sf", "1");
        }
        // Keep dismissing the splash across any pause/resume that happens while the
        // game is still loading (the tick stops itself once the splash is gone).
        if (mSplash != null) {
            mHandler.removeCallbacks(mSplashTick);
            mHandler.postDelayed(mSplashTick, 150);
        }
        mHandler.removeCallbacks(mPoll);
        mHandler.postDelayed(mPoll, 1000);
    }

    // Drop the loading splash the moment drastic reports its first present. Polled
    // fast (150 ms) so the blank-to-game transition has no perceptible overlap; the
    // session poll below stays at 1 s since exit timing is not latency-sensitive.
    private void splashTick() {
        if (mSplash == null) return;
        if ("1".equals(SystemProperties.get("sys.gammaos.drastic_nano.rendering", "0"))) {
            mSplash.setVisibility(View.GONE);
            mSplash = null;
            return;
        }
        mHandler.postDelayed(mSplashTick, 150);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mPoll);
        mHandler.removeCallbacks(mSplashTick);
    }

    // Finish once drastic-nano's SESSION has come up and then ended. We watch the
    // binary's own signal sys.gammaos.drastic_nano.session (set to 1 by drastic at
    // startup, cleared to 0 by the init session_done trigger when it exits), NOT
    // init.svc.drastic-nano: the init service state reads transiently "stopped"
    // during the start/finish handshake and was finishing us MID-GAME, which tore
    // down the SurfaceView and left drastic rendering into a dead window (a black
    // screen). The session signal only drops when drastic genuinely ends its
    // session, so it never blanks a running game. This only affects WHEN we
    // finish; it has no effect on the launch/render path.
    private void poll() {
        String session = SystemProperties.get("sys.gammaos.drastic_nano.session", "0");
        if ("1".equals(session)) {
            mSawRunning = true;
            mGoneTicks = 0;
        } else if (mSawRunning) {
            if (++mGoneTicks >= 3) {
                handDisplayBackToOverlay();
                finish();
                return;
            }
        }
        mHandler.postDelayed(mPoll, 1000);
    }

    // drastic has exited (e.g. long-press Back save+quit). Hand the panel back to
    // the XMB overlay. In overlay-home mode the framework's normal app-exit cleanup
    // is SKIPPED -- it keys off the launched app's PROCESS dying, but our process
    // lingers cached after the activity finishes -- so sys.gammaos.nano.app_launched
    // would stay 1 and the overlay would sit parked (hidden) forever, thinking the
    // game is still running. Clear it and raise the overlay so the XMB comes back.
    private void handDisplayBackToOverlay() {
        SystemProperties.set("sys.gammaos.nano.app_launched", "0");
        SystemProperties.set("sys.gammaos.nano.show_overlay", "1");
    }
}
