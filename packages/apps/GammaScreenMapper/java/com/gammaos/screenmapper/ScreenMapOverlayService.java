package com.gammaos.screenmapper;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.TaskStackListener;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemProperties;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

import java.util.List;

public class ScreenMapOverlayService extends Service {
    private static final String TAG = "ScreenMapOverlay";
    private static final String CHANNEL_ID = "screen_mapper";

    public static final String EXTRA_MODE = "mode";
    public static final int MODE_EDIT = 0;
    public static final int MODE_PLAY = 1;

    private WindowManager mWindowManager;
    private ScreenMapEditorView mEditorView;
    private boolean mEditorMode = false;
    private String mCurrentOverlayPkg;
    private Handler mHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();

        // Register for foreground app changes
        try {
            ActivityTaskManager.getService().registerTaskStackListener(mTaskStackListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register task stack listener", e);
        }

        // Start periodic foreground check as safety net
        mHandler.postDelayed(mForegroundChecker, 2000);
    }

    private final TaskStackListener mTaskStackListener = new TaskStackListener() {
        @Override
        public void onTaskStackChanged() {
            mHandler.post(() -> checkForegroundApp());
            mHandler.postDelayed(() -> checkForegroundApp(), 300);
        }

        @Override
        public void onTaskMovedToFront(ActivityManager.RunningTaskInfo info) {
            mHandler.post(() -> checkForegroundApp());
            mHandler.postDelayed(() -> checkForegroundApp(), 300);
        }
    };

    private final Runnable mForegroundChecker = new Runnable() {
        @Override
        public void run() {
            checkForegroundApp();
            mHandler.postDelayed(this, 2000);
        }
    };

    private boolean isMappingEnabled() {
        // Check persistent property (survives reboot) OR volatile property
        return "1".equals(SystemProperties.get("persist.gammaos.screenmap.enabled", "0"))
            || "1".equals(SystemProperties.get("sys.gammaos.screenmap.active", "0"));
    }

    private void checkForegroundApp() {
        // Don't show overlays if the mapping tile is disabled
        if (!isMappingEnabled() && !mEditorMode) {
            if (mEditorView != null) {
                removeOverlay();
                mCurrentOverlayPkg = null;
            }
            return;
        }

        String fgPkg = getForegroundPackage();
        if (fgPkg == null || fgPkg.isEmpty()) return;
        // Don't count ourselves as the foreground app
        if (fgPkg.equals(getPackageName())) return;

        if (fgPkg.equals(mCurrentOverlayPkg) && mEditorView != null) return;

        if (ScreenMapConfig.exists(fgPkg)) {
            // This app has a saved config — show play-mode overlay
            if (!fgPkg.equals(mCurrentOverlayPkg) || mEditorView == null) {
                removeOverlay();
                showOverlay(fgPkg, MODE_PLAY);
            }
        } else {
            // No config for this app — hide overlay
            if (mEditorView != null && !mEditorMode) {
                removeOverlay();
                mCurrentOverlayPkg = null;
            }
        }
    }

    private String getForegroundPackage() {
        // First try the property (set by GammapadVibrationBridge)
        String pkg = SystemProperties.get("sys.gammaos.gamepad.fg_pkg", "");
        if (!pkg.isEmpty()) return pkg;

        // Fallback: query ActivityTaskManager
        try {
            List<ActivityManager.RunningTaskInfo> tasks =
                    ActivityTaskManager.getService().getTasks(1, false, false, -1);
            if (tasks != null && !tasks.isEmpty() && tasks.get(0).topActivity != null) {
                return tasks.get(0).topActivity.getPackageName();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get foreground task", e);
        }
        return "";
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start as foreground service with tap-to-edit notification
        Intent editIntent = new Intent();
        editIntent.setClassName(getPackageName(),
                "com.gammaos.screenmapper.ScreenMapOverlayService");
        editIntent.putExtra(EXTRA_MODE, MODE_EDIT);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getForegroundService(
                this, 0, editIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
                        | android.app.PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
        startForeground(1, notification);

        int mode = MODE_PLAY;
        if (intent != null) {
            mode = intent.getIntExtra(EXTRA_MODE, MODE_PLAY);
        }

        if (mode == MODE_EDIT) {
            // User triggered editor via long-press
            String fgPkg = getForegroundPackage();
            if (fgPkg.isEmpty()) {
                Log.w(TAG, "No foreground package for editor");
                return START_STICKY;
            }

            if (mEditorView != null && fgPkg.equals(mCurrentOverlayPkg)) {
                // Already showing for this app — switch to editor mode
                mEditorView.setEditorMode(true);
                updateWindowFlags(true);
                mEditorMode = true;
            } else {
                // Show new editor overlay
                removeOverlay();
                showOverlay(fgPkg, MODE_EDIT);
            }
        } else {
            // Play mode — just ensure service is running, checkForegroundApp will handle overlay
            checkForegroundApp();
        }

        return START_STICKY;
    }

    private void showOverlay(String pkg, int mode) {
        mCurrentOverlayPkg = pkg;
        mEditorMode = (mode == MODE_EDIT);

        mEditorView = new ScreenMapEditorView(this, pkg);
        mEditorView.setEditorMode(mEditorMode);
        mEditorView.setOnCloseListener(() -> {
            removeOverlay();
            mCurrentOverlayPkg = null;
            mEditorMode = false;
        });
        mEditorView.setOnSaveListener(() -> {
            // Switch to play mode after save
            mEditorView.setEditorMode(false);
            updateWindowFlags(false);
            mEditorMode = false;
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                getWindowFlags(mEditorMode),
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;

        try {
            mWindowManager.addView(mEditorView, params);
            Log.i(TAG, "Overlay shown for " + pkg + " mode="
                    + (mEditorMode ? "edit" : "play"));
        } catch (Exception e) {
            Log.e(TAG, "Failed to add overlay", e);
        }
    }

    private int getWindowFlags(boolean editorMode) {
        if (editorMode) {
            return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        } else {
            return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        }
    }

    private void updateWindowFlags(boolean editorMode) {
        if (mEditorView == null) return;
        WindowManager.LayoutParams params =
                (WindowManager.LayoutParams) mEditorView.getLayoutParams();
        params.flags = getWindowFlags(editorMode);
        try {
            mWindowManager.updateViewLayout(mEditorView, params);
        } catch (Exception e) {
            Log.e(TAG, "Failed to update window flags", e);
        }
    }

    private void removeOverlay() {
        if (mEditorView != null) {
            try {
                mWindowManager.removeView(mEditorView);
            } catch (Exception e) {
                Log.e(TAG, "Failed to remove overlay", e);
            }
            mEditorView = null;
        }
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacks(mForegroundChecker);
        try {
            ActivityTaskManager.getService().unregisterTaskStackListener(mTaskStackListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister task stack listener", e);
        }
        removeOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);
    }
}
