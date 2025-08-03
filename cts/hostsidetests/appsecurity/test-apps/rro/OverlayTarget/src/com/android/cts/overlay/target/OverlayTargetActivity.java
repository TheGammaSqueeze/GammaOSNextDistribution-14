/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.overlay.target;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.BiConsumer;

/**
 * A test activity to verify that the assets paths configuration changes are received if the
 * overlay targeting state is changed.
 */
public class OverlayTargetActivity extends Activity {
    private BiConsumer<OverlayTargetActivity, Configuration> mConfigurationChangedCallback;

    /**
     * A boolean value to determine whether a stub service can be started when the activity
     * is launched.
     */
    public static final String EXTRA_START_SERVICE =
            "com.android.cts.overlay.intent.extra.START_SERVICE";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keepScreenOn();
        if (savedInstanceState == null) {
            startServiceIfNecessary(getIntent());
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        final BiConsumer<OverlayTargetActivity, Configuration> callback =
                mConfigurationChangedCallback;
        if (callback != null) {
            callback.accept(this, newConfig);
        }
    }

    /** Registers the callback of onConfigurationChanged. */
    public void setConfigurationChangedCallback(
            BiConsumer<OverlayTargetActivity, Configuration> callbacks) {
        mConfigurationChangedCallback = callbacks;
    }

    private void keepScreenOn() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setTurnScreenOn(true);
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km != null) {
            km.requestDismissKeyguard(this, null);
        }
    }

    private void startServiceIfNecessary(Intent intent) {
        if (intent == null) {
            return;
        }
        final boolean startService = intent.getBooleanExtra(EXTRA_START_SERVICE, false);
        if (!startService) {
            return;
        }
        final Intent serviceIntent = new Intent(this, OverlayTargetService.class);
        startService(serviceIntent);
    }
}
