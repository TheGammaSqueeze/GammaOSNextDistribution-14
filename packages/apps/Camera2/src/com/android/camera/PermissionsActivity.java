package com.android.camera;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import com.android.camera.app.CameraServicesImpl;
import com.android.camera.debug.Log;
import com.android.camera.settings.Keys;
import com.android.camera.settings.SettingsManager;
import com.android.camera.util.QuickActivity;
import com.android.camera2.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity that shows permissions request dialogs and handles lack of critical permissions.
 * TODO: Convert PermissionsActivity into a dialog to be emitted from
 * CameraActivity as not to have to restart CameraActivity from
 * scratch.
 */
public class PermissionsActivity extends QuickActivity {
    private static final Log.Tag TAG = new Log.Tag("PermissionsActivity");

    private static final int PERMISSION_REQUEST_CODE = 1;

    private SettingsManager mSettingsManager;

    /**
     * Close activity when secure app passes lock screen or screen turns
     * off.
     */
    private final BroadcastReceiver mShutdownReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          Log.v(TAG, "received intent, finishing: " + intent.getAction());
          finish();
        }
    };

    @Override
    protected void onCreateTasks(Bundle savedInstanceState) {
        setContentView(R.layout.permissions);
        mSettingsManager = CameraServicesImpl.instance().getSettingsManager();

        // Filter for screen off so that we can finish permissions activity
        // when screen is off.
        IntentFilter filter_screen_off = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mShutdownReceiver, filter_screen_off);

        // Filter for phone unlock so that we can finish permissions activity
        // via this UI path:
        //    1. from secure lock screen, user starts secure camera
        //    2. user presses home button
        //    3. user unlocks phone
        IntentFilter filter_user_unlock = new IntentFilter(Intent.ACTION_USER_PRESENT);
        registerReceiver(mShutdownReceiver, filter_user_unlock);

        Window win = getWindow();
        if (isKeyguardLocked()) {
            win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        } else {
            win.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
    }

    @Override
    protected void onResumeTasks() {
        checkPermissions();
    }

    @Override
    protected void onDestroyTasks() {
        Log.v(TAG, "onDestroy: unregistering receivers");
        unregisterReceiver(mShutdownReceiver);
    }

    private void checkPermissions() {
        List<String> permissions = new ArrayList<>();

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA);
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }

        if (mSettingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL, Keys.KEY_RECORD_LOCATION)
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!permissions.isEmpty()) {
            if (!isKeyguardLocked() && !mSettingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL,
                    Keys.KEY_HAS_SEEN_PERMISSIONS_DIALOGS)) {
                Log.v(TAG, "requestPermissions count: " + permissions.size());
                requestPermissions(permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            } else {
                // Permissions dialog has already been shown, or we're on
                // lockscreen, and we're still missing permissions.
                handlePermissionsFailure();
            }
        } else {
            handlePermissionsSuccess();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        Log.v(TAG, "onPermissionsResult counts: " + permissions.length + ":" + grantResults.length);
        mSettingsManager.set(
                SettingsManager.SCOPE_GLOBAL,
                Keys.KEY_HAS_SEEN_PERMISSIONS_DIALOGS,
                true);

        boolean missingCriticalPermissions = false;
        for (int i = 0; i < permissions.length; i++) {
            String permission = permissions[i];
            int result = grantResults[i];
            // Show fail dialog if critical permissions are not granted
            if (Manifest.permission.CAMERA.equals(permission)
                    && result == PackageManager.PERMISSION_DENIED) {
                handlePermissionsFailure();
                missingCriticalPermissions = true;
            } else if (Manifest.permission.RECORD_AUDIO.equals(permission)
                    && result == PackageManager.PERMISSION_DENIED) {
                handlePermissionsFailure();
                missingCriticalPermissions = true;
            }
        }
        if (!missingCriticalPermissions) {
            handlePermissionsSuccess();
        }
    }

    private void handlePermissionsSuccess() {
        Intent intent = new Intent(this, CameraActivity.class);
        startActivity(intent);
        finish();
    }

    private void handlePermissionsFailure() {
        new AlertDialog.Builder(this).setTitle(getResources().getString(R.string.camera_error_title))
                .setMessage(getResources().getString(R.string.error_permissions))
                .setCancelable(false)
                .setOnKeyListener(new Dialog.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            finish();
                        }
                        return true;
                    }
                })
                .setPositiveButton(getResources().getString(R.string.dialog_dismiss),
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
    }
}
