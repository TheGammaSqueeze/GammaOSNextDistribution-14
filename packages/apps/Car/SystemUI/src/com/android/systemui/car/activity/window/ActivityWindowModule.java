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
package com.android.systemui.car.activity.window;

import android.annotation.Nullable;
import android.car.app.CarTaskViewControllerHostLifecycle;
import android.content.Context;

import com.android.systemui.R;
import com.android.systemui.car.displaycompat.ToolbarController;
import com.android.systemui.car.displaycompat.ToolbarControllerImpl;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

/**
 * Dagger injection module for {@link ActivityWindowManager}
 */
@Module
public abstract class ActivityWindowModule {
    /**
     * Injects ActivityWindowController.
     */
    @Binds
    public abstract ActivityWindowController bindActivityWindowController(
            ActivityWindowControllerImpl activityWindowController);

    @Provides
    static CarTaskViewControllerHostLifecycle provideCarTaskViewControllerHostLifecycle() {
        return new CarTaskViewControllerHostLifecycle();
    }

    /**
     * Injects ToolbarController
     */
    @Nullable
    @Provides
    static ToolbarController providesToolbarController(Context context,
            ToolbarControllerImpl impl) {
        if (context.getResources()
                .getInteger(R.integer.config_showDisplayCompatToolbarOnSystemBar) == 0) {
            return null;
        } else {
            return impl;
        }
    }
}
