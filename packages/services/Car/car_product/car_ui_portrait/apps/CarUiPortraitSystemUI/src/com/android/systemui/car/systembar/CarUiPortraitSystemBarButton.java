/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.car.systembar;

import static com.android.car.caruiportrait.common.service.CarUiPortraitService.INTENT_EXTRA_FG_TASK_VIEW_READY;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.REQUEST_FROM_LAUNCHER;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.systemui.R;

/**
 * CarUiPortraitSystemBarButton is an extension of {@link CarSystemBarButton} that disables itself
 * until it receives a signal from launcher that tasks views are ready.
 */
public class CarUiPortraitSystemBarButton extends CarSystemBarButton {

    private static final String TAG = "CarUiPortraitSystemBarButton";
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;

    // this is static so that we can save its state when configuration changes
    private static boolean sTaskViewReady = false;

    public CarUiPortraitSystemBarButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        logIfDebuggable("CarUiPortraitSystemBarButton");

        // disable button by default
        super.setDisabled(/* disabled= */ true, getDisabledRunnable(context));

        BroadcastReceiver taskViewReadyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.hasExtra(INTENT_EXTRA_FG_TASK_VIEW_READY)) {
                    boolean taskViewReady = intent.getBooleanExtra(
                            INTENT_EXTRA_FG_TASK_VIEW_READY, /* defaultValue= */ false);
                    sTaskViewReady = taskViewReady;
                    if (sTaskViewReady) {
                        logIfDebuggable("Foreground task view ready");
                    }
                    setDisabled(!taskViewReady, getDisabledRunnable(context));
                }
            }
        };
        context.registerReceiverForAllUsers(taskViewReadyReceiver,
                new IntentFilter(REQUEST_FROM_LAUNCHER), null, null, Context.RECEIVER_EXPORTED);
    }

    private static void logIfDebuggable(String message) {
        if (DEBUG) {
            Log.d(TAG, message);
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setDisabled(!sTaskViewReady, getDisabledRunnable(getContext()));
    }

    @Override
    public void setDisabled(boolean disabled, @Nullable Runnable runnable) {
        // do not externally control disable state until taskview is ready
        if (!sTaskViewReady) {
            return;
        }

        super.setDisabled(disabled, runnable);
    }

    private Runnable getDisabledRunnable(Context context) {
        return () -> Toast.makeText(context, R.string.task_view_not_ready_message,
                Toast.LENGTH_LONG).show();
    }
}
