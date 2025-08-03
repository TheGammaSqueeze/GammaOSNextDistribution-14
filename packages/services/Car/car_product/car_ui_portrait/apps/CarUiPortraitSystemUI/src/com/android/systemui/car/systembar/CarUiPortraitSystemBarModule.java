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

import android.content.Context;
import android.content.res.Configuration;

import com.android.systemui.car.dagger.CarSysUIDynamicOverride;
import com.android.systemui.car.displayarea.CarDisplayAreaController;
import com.android.systemui.dagger.SysUISingleton;

import dagger.Module;
import dagger.Provides;

/**
 * Dagger injection module for {@link CarSystemBar} in CarUiPortraitSystemUI.
 */
@Module(includes = {CarSystemBarModule.class})
public abstract class CarUiPortraitSystemBarModule {
    @SysUISingleton
    @Provides
    @CarSysUIDynamicOverride
    static ButtonSelectionStateListener provideButtonSelectionStateListener(Context context,
            ButtonSelectionStateController buttonSelectionStateController,
            CarDisplayAreaController displayAreaController) {
        if (context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            return new ButtonSelectionStateListener(buttonSelectionStateController);
        }

        return new CarUiPortraitButtonSelectionStateListener(context,
                buttonSelectionStateController);
    }

    @SysUISingleton
    @Provides
    @CarSysUIDynamicOverride
    static ButtonSelectionStateController provideButtonSelectionStateController(Context context) {
        if (context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            return new ButtonSelectionStateController(context);
        }

        return new CarUiPortraitButtonSelectionStateController(context);
    }
}
