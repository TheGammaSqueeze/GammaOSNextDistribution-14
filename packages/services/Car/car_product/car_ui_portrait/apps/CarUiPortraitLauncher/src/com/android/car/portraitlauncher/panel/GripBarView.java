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

package com.android.car.portraitlauncher.panel;

import static com.android.car.portraitlauncher.homeactivities.CarUiPortraitHomeScreen.sendVirtualBackPress;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.Car;
import android.car.content.pm.CarPackageManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.hardware.input.InputManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import com.android.car.portraitlauncher.R;

/** The grip bar used to drag a TaskViewPanel */
public class GripBarView extends RelativeLayout {

    private static final String TAG = GripBarView.class.getName();

    @Nullable
    private CarPackageManager mCarPackageManager = null;
    @Nullable
    private InputManager mInputManager;

    @Nullable
    private View mBackButton = null;
    @Nullable
    private View mDisplayCompatToolbar = null;

    public GripBarView(Context context) {
        this(context, null);
    }

    public GripBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GripBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public GripBarView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initView();
    }

    private void initView() {
        // TODO: (b/307556334) move this to a central place.
        Car.createCar(getContext(), /* handler= */ null,
                Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (car, ready) -> {
                    if (!ready) {
                        Log.w(TAG, "CarService is not ready.");
                        return;
                    }

                    mCarPackageManager = car.getCarManager(CarPackageManager.class);
                    mInputManager = getContext().getSystemService(InputManager.class);

                    inflate(getContext(), R.layout.car_ui_portrait_grip_bar_layout, this);
                    mDisplayCompatToolbar = findViewById(R.id.displaycompat_toolbar);
                    mBackButton = findViewById(R.id.back_button);
                    mBackButton.setOnClickListener(v -> {
                        sendVirtualBackPress();
                    });
                });
    }

    /** Refreshes the view according to the given {@link Resources.Theme}. */
    public void refresh(Resources.Theme theme) {
        Drawable background = getResources().getDrawable(R.drawable.grip_bar_background, theme);
        findViewById(R.id.grip_bar_handle).setBackground(background);

        int displayCompatToolbarBackground = getResources().getColor(R.color.car_background, theme);
        findViewById(R.id.displaycompat_toolbar)
                .setBackgroundColor(displayCompatToolbarBackground);

        ImageButton backButton = findViewById(R.id.back_button);
        ImageButton fullscreenButton = findViewById(R.id.fullscreen_btn);

        Drawable roundBackground = getResources().getDrawable(R.drawable.ic_round_bg, theme);
        backButton.setBackground(roundBackground);
        fullscreenButton.setBackground(roundBackground);

        Drawable backArrowIcon = getResources().getDrawable(R.drawable.ic_arrow_back_32, theme);
        backButton.setImageDrawable(backArrowIcon);

        Drawable fullscreenIcon = getResources().getDrawable(R.drawable.ic_fullscreen_32, theme);
        fullscreenButton.setImageDrawable(fullscreenIcon);
    }

    /** Update the view according to the given {@link ComponentName} */
    public void update(@NonNull ComponentName componentName) {
        try {
            if (mCarPackageManager != null
                    && mCarPackageManager.requiresDisplayCompat(componentName.getPackageName())) {
                showToolbar();
                return;
            }
        } catch (NameNotFoundException e) { /* ignore */}
        hideToolbar();
    }

    private void showToolbar() {
        mDisplayCompatToolbar.setVisibility(View.VISIBLE);
    }

    private void hideToolbar() {
        mDisplayCompatToolbar.setVisibility(View.GONE);
    }
}
