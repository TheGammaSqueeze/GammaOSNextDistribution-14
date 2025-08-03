/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.portraitlauncher.calmmode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.android.car.portraitlauncher.R;

public class CalmModeActivity extends FragmentActivity {
    private static final String TAG = CalmModeActivity.class.getSimpleName();
    private static final boolean DEBUG = Build.isDebuggable();

    private final BroadcastReceiver mCloseSystemDialogsReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                        if (DEBUG) {
                            Log.v(TAG, "Received ACTION_CLOSE_SYSTEM_DIALOGS broadcast:"
                                            + " intent=" + intent + ", user=" + context.getUser());
                        }
                        finish();
                        return;
                    }
                    if (DEBUG) {
                        Log.w(TAG, "Unexpected intent " + intent);
                    }
                }
            };

    private final View.OnApplyWindowInsetsListener mOnApplyWindowInsetsListener =
            (v, insets) -> {
                int insetTypes = WindowInsets.Type.systemBars();
                Insets appliedInsets = insets.getInsets(insetTypes);
                v.setPadding(
                        appliedInsets.left, appliedInsets.top,
                        appliedInsets.right, /* bottom= */ 0);
                return insets.inset(appliedInsets);
            };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDecorFitsSystemWindows(/* decorFitsSystemWindows= */ false);
        setContentView(R.layout.calm_mode_fragment);
        getWindow()
                .getDecorView()
                .getRootView()
                .setOnApplyWindowInsetsListener(mOnApplyWindowInsetsListener);
        registerBroadcastReceiver();
        setContentView(R.layout.calm_mode_activity);
    }

    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mCloseSystemDialogsReceiver, filter, /* broadcastPermission= */ null,
                /* scheduler= */ null, Context.RECEIVER_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mCloseSystemDialogsReceiver);
    }
}
