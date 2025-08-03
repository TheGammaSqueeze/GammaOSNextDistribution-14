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

package com.android.systemui.car.statusicon.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import com.android.systemui.R;
import com.android.systemui.car.statusicon.StatusIconController;
import com.android.systemui.dagger.qualifiers.Main;

import javax.inject.Inject;

/**
 * A controller for Display status icon.
 */
public class DisplayStatusIconController extends StatusIconController {

    private final Drawable mDisplayBrightnessDrawable;
    private String mDisplayBrightnessContentDescription;

    @Inject
    DisplayStatusIconController(Context context, @Main Resources resources) {
        mDisplayBrightnessDrawable = resources.getDrawable(R.drawable.car_ic_brightness,
                context.getTheme());
        mDisplayBrightnessContentDescription = resources.getString(
                R.string.status_icon_display_status);
        updateStatus();
    }

    @Override
    protected void updateStatus() {
        setIconDrawableToDisplay(mDisplayBrightnessDrawable);
        setIconContentDescription(mDisplayBrightnessContentDescription);
        onStatusUpdated();
    }

    @Override
    protected int getPanelContentLayout() {
        return R.layout.qc_display_panel;
    }

    @Override
    protected int getId() {
        return R.id.qc_display_status_icon;
    }
}
