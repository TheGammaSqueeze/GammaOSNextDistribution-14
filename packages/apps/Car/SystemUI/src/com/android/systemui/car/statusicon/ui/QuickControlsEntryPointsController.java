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

import android.annotation.ArrayRes;
import android.annotation.LayoutRes;
import android.content.Context;
import android.content.res.Resources;

import com.android.systemui.R;
import com.android.systemui.car.statusicon.StatusIconController;
import com.android.systemui.car.statusicon.StatusIconGroupContainerController;
import com.android.systemui.car.statusicon.StatusIconPanelViewController;
import com.android.systemui.dagger.qualifiers.Main;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * A controller for Quick Controls Entry Points
 */
public class QuickControlsEntryPointsController extends StatusIconGroupContainerController {
    @Inject
    QuickControlsEntryPointsController(
            Context context,
            @Main Resources resources,
            Map<Class<?>, Provider<StatusIconController>> iconControllerCreators,
            Provider<StatusIconPanelViewController.Builder> panelControllerBuilderProvider) {
        super(context, resources, iconControllerCreators, panelControllerBuilderProvider);
    }

    @Override
    @ArrayRes
    protected int getStatusIconControllersStringArray() {
        return R.array.config_quickControlsEntryPointIconControllers;
    }

    @Override
    @LayoutRes
    public int getButtonViewLayout(boolean isVertical) {
        return isVertical ? R.layout.car_qc_entry_points_button_vertical
                : R.layout.car_qc_entry_points_button_horizontal;
    }
}
