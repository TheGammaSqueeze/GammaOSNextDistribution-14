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

package com.android.systemui;

import android.app.Activity;

import com.android.systemui.car.userpicker.UserPickerActivity;
import com.android.systemui.dagger.DefaultActivityBinder;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

/**
 * Provides all injectable activities related to Car
 */
@Module
public abstract class CarSystemUIActivityBinder extends DefaultActivityBinder {
    /** Inject into UserPickerActivity. */
    @Binds
    @IntoMap
    @ClassKey(UserPickerActivity.class)
    public abstract Activity bindUserPickerActivity(UserPickerActivity activity);
}
