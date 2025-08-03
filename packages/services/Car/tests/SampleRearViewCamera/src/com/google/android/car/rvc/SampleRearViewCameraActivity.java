/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.google.android.car.rvc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.LinearLayout;

// The class is based on BasicGLSurfaceView in development/samples.
public class SampleRearViewCameraActivity extends RearViewCameraActivityBase {
    private static final String TAG = SampleRearViewCameraActivity.class.getSimpleName();

    private BasicGLSurfaceView mEvsView;
    private ViewGroup mRootView;
    private LinearLayout mPreviewContainer;

    private boolean mUseSystemWindow;
    private boolean mHideSystemBar;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        registerBroadcastReceiver();
        parseExtra(getIntent());

        mEvsView = new BasicGLSurfaceView(getApplication());
        mRootView = (ViewGroup) LayoutInflater.from(this).inflate(
                R.layout.evs_preview_activity, /* root= */ null);
        mPreviewContainer = mRootView.findViewById(R.id.evs_preview_container);
        LinearLayout.LayoutParams viewParam = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1.0f
        );
        mEvsView.setLayoutParams(viewParam);
        mPreviewContainer.addView(mEvsView, 0);
        View closeButton = mRootView.findViewById(R.id.close_button);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> finish());
        } else {
            Log.e(TAG, "Can't find the close button");
        }

        int width = WindowManager.LayoutParams.MATCH_PARENT;
        int height = WindowManager.LayoutParams.MATCH_PARENT;
        if (mUseSystemWindow) {
            width = getResources().getDimensionPixelOffset(R.dimen.camera_preview_width);
            height = getResources().getDimensionPixelOffset(R.dimen.camera_preview_height);
        }
        // Checks WindowManagerPolicy.getWindowLayerFromTypeLw to get the z-order per Window type.
        // Chose TYPE_VOLUME_OVERLAY because it has the relative high-z order and doesn't conflict
        // with the existing UI.
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, height,
                /* type= */ 2020,  // TYPE_VOLUME_OVERLAY
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;
        params.dimAmount = getResources().getFloat(R.dimen.config_cameraBackgroundScrim);

        if (mUseSystemWindow) {
            WindowManager wm = getSystemService(WindowManager.class);
            wm.addView(mRootView, params);
        } else {
            setContentView(mRootView, params);
        }
        if (mHideSystemBar) {
            hideSystemBars();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        parseExtra(intent);
    }

    private void parseExtra(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.i(TAG, "No extra, intent=" + intent);
            return;
        }
        mUseSystemWindow = extras.getBoolean("use_system_window");
        mHideSystemBar = extras.getBoolean("hide_system_bar");
    }

    private void hideSystemBars() {
        WindowInsetsController windowInsetsController = mEvsView.getWindowInsetsController();
        if (windowInsetsController == null) {
            Log.e(TAG, "Can't find WindowInsetsController");
            return;
        }
        // Configure the behavior of the hidden system bars
        windowInsetsController.setSystemBarsBehavior(
                windowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        // Hide both the status bar and the navigation bar
        windowInsetsController.hide(WindowInsets.Type.systemBars());
        Log.i(TAG, "Hides System Bars");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mUseSystemWindow) {
            WindowManager wm = getSystemService(WindowManager.class);
            wm.removeView(mRootView);
        }
        unregisterReceiver(mCloseSystemDialogsReceiver);
    }

    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        // Need to register the receiver for all users, because we want to receive the Intent after
        // the user is changed.
        registerReceiverForAllUsers(mCloseSystemDialogsReceiver, filter, null, null);
    }

    private final BroadcastReceiver mCloseSystemDialogsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                Log.i(TAG, "Received ACTION_CLOSE_SYSTEM_DIALOGS broadcast: intent=" + intent
                        + ", user=" + context.getUser());
                finish();
            } else {
                Log.e(TAG, "Unexpected intent " + intent);
            }
        }
    };
}
