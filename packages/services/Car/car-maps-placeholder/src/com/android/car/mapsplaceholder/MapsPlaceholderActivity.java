/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.car.mapsplaceholder;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * An activity that notifies the user that no maps application has been installed.
 */
public class MapsPlaceholderActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.maps_placeholder_activity);

        if (getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT) {
            showTransparentStatusBar(getWindow());
            findViewById(android.R.id.content).getRootView()
                    .setOnApplyWindowInsetsListener((view, insets) -> {
                        Insets inset = insets.getInsets(WindowInsets.Type.systemOverlays());
                        View contentView = findViewById(R.id.aosp_nav);
                        contentView.setPadding(0, inset.top, 0, inset.bottom);
                        return insets;
                    });
        }
    }

    /** Configures the window to render behind a transparent status bar. */
    public static void showTransparentStatusBar(Window window) {
        updateWindowFlagsToSetStatusbarColor(window);
        window.setStatusBarColor(
                window.getContext().getResources().getColor(android.R.color.transparent));
    }

    private static void updateWindowFlagsToSetStatusbarColor(Window window) {
        WindowCompat.setDecorFitsSystemWindows(window, false);
        WindowInsetsControllerCompat controller =  WindowCompat
                .getInsetsController(window, window.getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT);
        // Set up window flags to enable setting the status bar color.
        // Note that setting FLAG_DRAWS_SYSTEM_BAR_BACKGROUND does not automatically make the
        // navigation bar transparent in cars.

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
    }
}
