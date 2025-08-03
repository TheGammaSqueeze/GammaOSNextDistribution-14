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

package com.android.systemui.car.statusicon.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import com.android.systemui.R;
import com.android.systemui.car.drivemode.DriveModeThemeSwitcher;
import com.android.systemui.car.statusicon.StatusIconController;
import com.android.systemui.dagger.qualifiers.Main;

import javax.inject.Inject;

import dagger.Lazy;

/**
 * A controller for the Drive Mode status icon.
 */
public class DriveModeStatusIconController extends StatusIconController {

    private final Drawable mDriveModeDrawable;
    private String mDriveModeContentDescription;

    @Inject
    DriveModeStatusIconController(Context context, @Main Resources resources,
            Lazy<DriveModeThemeSwitcher> driveModeThemeSwitcherLazy) {
        mDriveModeDrawable = resources.getDrawable(R.drawable.car_ic_drive_mode,
                context.getTheme());
        mDriveModeContentDescription = resources.getString(
                R.string.status_icon_drive_mode);

        //Initializes the ThemeSwitcher only if the icon is added to the status bar
        driveModeThemeSwitcherLazy.get();

        updateStatus();
    }

    @Override
    protected void updateStatus() {
        setIconDrawableToDisplay(mDriveModeDrawable);
        setIconContentDescription(mDriveModeContentDescription);
        onStatusUpdated();
    }

    @Override
    protected int getPanelContentLayout() {
        return R.layout.qc_drive_mode_panel;
    }

    @Override
    protected int getId() {
        return R.id.qc_drive_mode_status_icon;
    }
}
