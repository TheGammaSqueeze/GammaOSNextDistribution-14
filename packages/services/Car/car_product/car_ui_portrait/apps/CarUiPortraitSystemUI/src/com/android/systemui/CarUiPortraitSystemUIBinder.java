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

package com.android.systemui;

import com.android.systemui.car.displayarea.CarDisplayAreaModule;
import com.android.systemui.car.displayarea.DisplayAreaComponent;
import com.android.systemui.car.qc.CarUiPortraitQuickControlsModule;
import com.android.systemui.car.statusicon.ui.ReadOnlyStatusIconModule;
import com.android.systemui.car.systembar.CarUiPortraitSystemBarModule;
import com.android.systemui.car.window.ExtendedOverlayWindowModule;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

/** Binder for AAECarSystemUI specific {@link CoreStartable} modules and components. */
@Module(includes = {ExtendedOverlayWindowModule.class, CarDisplayAreaModule.class,
        CarUiPortraitSystemBarModule.class, ReadOnlyStatusIconModule.class,
        CarUiPortraitQuickControlsModule.class})
abstract class CarUiPortraitSystemUIBinder extends CarSystemUIBinder {

    /** Inject into ClusterDisplayController. */
    @Binds
    @IntoMap
    @ClassKey(DisplayAreaComponent.class)
    public abstract CoreStartable bindDisplayAreaComponent(DisplayAreaComponent sysui);
}
