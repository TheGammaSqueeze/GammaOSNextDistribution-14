/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package android.systemui.cts;

import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.view.View;

/**
 * A test tile that logs everything that happens to it.
 * The tests will manipulate the state of the QS tile through ADB and verify
 * the correct callbacks actually happened.
 */
public class TestTileService extends TileService {
    protected final String TAG = getClass().getSimpleName();

    public static final String SHOW_DIALOG = "android.sysui.testtile.action.SHOW_DIALOG";
    public static final String START_ACTIVITY = "android.sysui.testtile.action.START_ACTIVITY";
    public static final String START_ACTIVITY_WITH_PENDING_INTENT =
            "android.sysui.testtile.action.START_ACTIVITY_WITH_PENDING_INTENT";
    public static final String SET_PENDING_INTENT =
            "android.sysui.testtile.action.SET_PENDING_INTENT";
    public static final String SET_NULL_PENDING_INTENT =
            "android.sysui.testtile.action.SET_NULL_PENDING_INTENT";

    public static final String TEST_PREFIX = "TileTest_";

    private PendingIntent mPendingIntent;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, TEST_PREFIX + "onCreate");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, TEST_PREFIX + "onDestroy");
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        Log.i(TAG, TEST_PREFIX + "onTileAdded");
        super.onTileAdded();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        Log.i(TAG, TEST_PREFIX + "onTileRemoved");
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        Log.i(TAG, TEST_PREFIX + "onStartListening");
        IntentFilter filter = new IntentFilter(SHOW_DIALOG);
        filter.addAction(START_ACTIVITY);
        filter.addAction(START_ACTIVITY_WITH_PENDING_INTENT);
        filter.addAction(SET_PENDING_INTENT);
        filter.addAction(SET_NULL_PENDING_INTENT);
        registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);

        // Set up some initial good state.
        Tile tile = getQsTile();
        tile.setLabel(TAG);
        tile.setContentDescription("CTS Test Tile");
        tile.setIcon(Icon.createWithResource(this, android.R.drawable.ic_secure));
        tile.setState(Tile.STATE_ACTIVE);
        tile.updateTile();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        Log.i(TAG, TEST_PREFIX + "onStopListening");
        unregisterReceiver(mReceiver);
    }

    @Override
    public void onClick() {
        super.onClick();
        Log.i(TAG, TEST_PREFIX + "onClick");
        Log.i(TAG, TEST_PREFIX + "is_secure_" + super.isSecure());
        Log.i(TAG, TEST_PREFIX + "is_locked_" + super.isLocked());
        super.unlockAndRun(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, TEST_PREFIX + "unlockAndRunRun");
            }
        });
    }

    private void handleStartActivity() {
        Log.i(TAG, TEST_PREFIX + "handleStartActivity");
        try {
            super.startActivityAndCollapse(new Intent(this, TestActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (UnsupportedOperationException e) {
            Log.i(TAG, TEST_PREFIX + "UnsupportedOperationException");
        }
    }

    private void handleStartActivityWithPendingIntent() {
        Log.i(TAG, TEST_PREFIX + "handleStartActivityWithPendingIntent");
        super.startActivityAndCollapse(getValidPendingIntent());
    }

    private void handleSetPendingIntent(boolean isValid) {
        Log.i(TAG, TEST_PREFIX + "handleSetPendingIntent");
        super.getQsTile().setActivityLaunchForClick(isValid ? getValidPendingIntent() : null);
        super.getQsTile().updateTile();
    }

    private PendingIntent getValidPendingIntent() {
        if (mPendingIntent == null) {
            mPendingIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, TestActivity.class), PendingIntent.FLAG_IMMUTABLE);
        }
        return mPendingIntent;
    }

    private void handleShowDialog() {
        Log.i(TAG, TEST_PREFIX + "handleShowDialog");
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(new FocusView(this, dialog));
        try {
            super.showDialog(dialog);
        } catch (Exception e) {
            Log.i(TAG, TEST_PREFIX + "onWindowAddFailed", e);
        }
    }

    public static class TestActivity extends Activity {
        private static final String TAG = "TestTileService";

        @Override
        public void onResume() {
            super.onResume();
            Log.i(TAG, TEST_PREFIX + "TestActivity#onResume");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
            }
            finish();
        }
    }

    private class FocusView extends View {
        private final Dialog mDialog;

        public FocusView(Context context, Dialog dialog) {
            super(context);
            mDialog = dialog;
        }

        @Override
        public void onWindowFocusChanged(boolean hasWindowFocus) {
            Log.i(TAG, TEST_PREFIX + "onWindowFocusChanged_" + hasWindowFocus);
            if (hasWindowFocus) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        mDialog.dismiss();
                    }
                });
            }
            super.onWindowFocusChanged(hasWindowFocus);
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(SHOW_DIALOG)) {
                handleShowDialog();
            } else if (intent.getAction().equals(START_ACTIVITY)) {
                handleStartActivity();
            } else if (intent.getAction().equals(START_ACTIVITY_WITH_PENDING_INTENT)) {
                handleStartActivityWithPendingIntent();
            } else if (intent.getAction().equals(SET_PENDING_INTENT)) {
                handleSetPendingIntent(true);
            } else if (intent.getAction().equals(SET_NULL_PENDING_INTENT)) {
                handleSetPendingIntent(false);
            }
        }
    };
}
