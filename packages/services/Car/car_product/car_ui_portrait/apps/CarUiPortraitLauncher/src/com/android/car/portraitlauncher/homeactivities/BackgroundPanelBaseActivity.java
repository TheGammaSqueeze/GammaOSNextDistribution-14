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

package com.android.car.portraitlauncher.homeactivities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Insets;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.android.car.portraitlauncher.R;

/**
 * Used as the static wallpaper in base layer. This activity is at the bottom of the base layer
 * stack and is visible when there is no other base layer application is running.
 */
public class BackgroundPanelBaseActivity extends AppCompatActivity {
    private static final String TAG = BackgroundPanelBaseActivity.class.getSimpleName();

    private ViewGroup mContainer;

    private final View.OnApplyWindowInsetsListener mOnApplyWindowInsetsListener = (v, insets) -> {
        int insetTypes = WindowInsets.Type.systemBars();
        Insets appliedInsets = insets.getInsets(insetTypes);
        v.setPadding(appliedInsets.left, /* top= */ 0, appliedInsets.right, /* bottom= */ 0);
        if (mContainer != null) {
            mContainer.setPadding(appliedInsets.left, appliedInsets.top, appliedInsets.right,
                    appliedInsets.bottom);
        } else {
            Log.e(TAG, "Container is null");
        }
        return insets.inset(appliedInsets);
    };

    /** Creates an intent that can be used to launch this activity. */
    public static Intent createIntent(Context context) {
        Intent intent = new Intent(context, BackgroundPanelBaseActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.car_ui_portrait_background_panel_base);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), /* decorFitsSystemWindows= */ false);
        mContainer = findViewById(R.id.container);
        getWindow().getDecorView().getRootView().setOnApplyWindowInsetsListener(
                mOnApplyWindowInsetsListener);
    }

    @SuppressWarnings("MissingSuperCall")
    @Override
    public void onBackPressed() {
        // no-op - this activity should always be present and not closed on back press.
    }
}
