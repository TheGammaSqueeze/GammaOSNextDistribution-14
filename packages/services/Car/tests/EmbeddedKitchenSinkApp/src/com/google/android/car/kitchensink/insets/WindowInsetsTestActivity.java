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

package com.google.android.car.kitchensink.insets;

import static android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import androidx.annotation.Nullable;

import com.google.android.car.kitchensink.R;

/**
 * {@link WindowInsetsTestActivity} shows usage of {@link WindowInsetsController} api's to hide
 * systems bars to present the activity in full screen.
 */
public final class WindowInsetsTestActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSysUI();

        setContentView(R.layout.fullscreen_activity);
        findViewById(R.id.cancel_button).setOnClickListener(l -> finish());
    }

    private void hideSysUI() {
        View decorView = getWindow().getDecorView();
        WindowInsetsController windowInsetsController = decorView.getWindowInsetsController();
        windowInsetsController.hide(WindowInsets.Type.systemBars());
        windowInsetsController.setSystemBarsBehavior(BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }
}
