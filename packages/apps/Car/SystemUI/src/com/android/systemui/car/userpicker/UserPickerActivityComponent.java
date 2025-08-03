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

package com.android.systemui.car.userpicker;

import android.content.Context;

import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.settings.DisplayTracker;

import dagger.BindsInstance;
import dagger.Component;

/**
 * Injects dependencies for {@link UserPickerActivity} that has {@link UserPickerScope}.
 */
@UserPickerScope
@Component
public interface UserPickerActivityComponent {

    /**
     * Builder for UserPickerActivityComponent
     */
    @Component.Builder
    interface Builder {
        @BindsInstance
        Builder context(Context context);

        @BindsInstance
        Builder carServiceProvider(CarServiceProvider carServiceProvider);

        @BindsInstance
        Builder displayTracker(DisplayTracker displayTracker);

        @BindsInstance
        Builder userPickerSharedState(UserPickerSharedState userPickerSharedState);

        UserPickerActivityComponent build();
    }

    /**
     * required to provide a link to DialogManager
     */
    DialogManager dialogManager();

    /**
     * required to provide a link to SnackbarManager
     */
    SnackbarManager snackbarManager();

    /**
     * required to provide a link to UserPickerController
     */
    UserPickerController userPickerController();
}
