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

package com.android.systemui.car.volume;

/** Dagger module for code in the car/volume package. */

import android.content.Context;

import com.android.systemui.CoreStartable;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.plugins.VolumeDialog;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.volume.VolumeComponent;
import com.android.systemui.volume.VolumeDialogComponent;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;

/** Dagger module for code in car/volume. */
@Module
public interface CarVolumeModule {
    /** Starts VolumeUI.  */
    @Binds
    @IntoMap
    @ClassKey(VolumeUI.class)
    CoreStartable bindVolumeUIStartable(VolumeUI impl);

    /** Listen to config changes for VolumeUI. */
    @Binds
    @IntoSet
    ConfigurationController.ConfigurationListener bindVolumeUIConfigChanges(VolumeUI impl);

    /** */
    @Binds
    VolumeComponent provideVolumeComponent(VolumeDialogComponent volumeDialogComponent);

    /** */
    @Provides
    static VolumeDialog provideVolumeDialog(Context context,
            CarServiceProvider carServiceProvider,
            ConfigurationController configurationController,
            UserTracker userTracker) {
        return new CarVolumeDialogImpl(context, carServiceProvider, configurationController,
                userTracker);
    }
}
