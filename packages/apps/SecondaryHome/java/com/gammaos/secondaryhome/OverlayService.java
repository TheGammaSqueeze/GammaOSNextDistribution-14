package com.gammaos.secondaryhome;

import android.app.ActivityOptions;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.IBinder;
import android.os.SystemProperties;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;

/**
 * Floats the control centre over an application running on the bottom panel, in a system
 * overlay window on that display, so the application underneath stays resumed (it only loses
 * window focus while the centre is up). The pad's mode button toggles it through
 * PhoneWindowManager (ACTION_TOGGLE); the app this process runs as system, so the window type
 * needs no app op. Nothing here runs in nano mode, where the nano binary owns the panel.
 */
public final class OverlayService extends Service {
    private static final String TAG = "SecondaryHome";

    public static final String ACTION_SHOW = "com.gammaos.secondaryhome.SHOW_OVERLAY";
    public static final String ACTION_HIDE = "com.gammaos.secondaryhome.HIDE_OVERLAY";
    public static final String ACTION_TOGGLE = "com.gammaos.secondaryhome.TOGGLE_OVERLAY";

    private WindowManager mWm;
    private Context mWinCtx;
    private CcPanel mPanel;
    private int mDisplayId = -1;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent != null ? intent.getAction() : null;
        if (ACTION_SHOW.equals(action)) show();
        else if (ACTION_HIDE.equals(action)) hide();
        else if (ACTION_TOGGLE.equals(action)) {
            if (mPanel != null) hide();
            else show();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        hide();
        super.onDestroy();
    }

    private int bottomDisplayId() {
        final int id = SystemProperties.getInt("persist.gammaos.nano.cc.bottomdisplay", -1);
        if (id >= 0) return id;
        // Without the property: the first display that is not the default one.
        final DisplayManager dm = getSystemService(DisplayManager.class);
        for (Display d : dm.getDisplays()) {
            if (d.getDisplayId() != Display.DEFAULT_DISPLAY) return d.getDisplayId();
        }
        return Display.DEFAULT_DISPLAY;
    }

    private void show() {
        if (mPanel != null) return;
        final int displayId = bottomDisplayId();
        final Display display = getSystemService(DisplayManager.class).getDisplay(displayId);
        if (display == null) {
            Log.w(TAG, "overlay: display " + displayId + " is gone");
            stopSelf();
            return;
        }
        try {
            final Context winCtx = createWindowContext(display,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
            mWinCtx = new ContextThemeWrapper(winCtx, R.style.Theme_SecondaryHome);
            mWm = winCtx.getSystemService(WindowManager.class);
            mDisplayId = displayId;
            mPanel = new CcPanel(mWinCtx, displayId, true, mShell);
            final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    // Touch only, like the nano control centre: a focusable window would make
                    // the bottom display the focused one on the first tap and every pad key
                    // after that would leave the game on the top screen.
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.OPAQUE);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.setTitle("SecondaryHomeOverlay");
            lp.setFitInsetsTypes(0);
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            mWm.addView(mPanel, lp);
            mPanel.resume();
            Log.i(TAG, "overlay: control centre over display " + displayId);
        } catch (Throwable t) {
            Log.w(TAG, "overlay: cannot show the control centre", t);
            mPanel = null;
            stopSelf();
        }
    }

    private void hide() {
        final CcPanel p = mPanel;
        if (p == null) return;
        mPanel = null;
        try {
            p.pause();
            p.destroy();
            mWm.removeViewImmediate(p);
        } catch (Throwable t) {
            Log.w(TAG, "overlay: remove failed", t);
        }
        Log.i(TAG, "overlay: control centre hidden");
        stopSelf();
    }

    private final CcPanel.Shell mShell = new CcPanel.Shell() {
        @Override
        public void startOnOwnDisplay(Intent intent) {
            final ActivityOptions opts = ActivityOptions.makeBasic();
            opts.setLaunchDisplayId(mDisplayId);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent, opts.toBundle());
        }

        @Override
        public void onAppStarted() {
            hide();
        }
    };
}
