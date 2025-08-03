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

import com.android.systemui.car.statusicon.StatusIconController;
import com.android.systemui.car.systembar.element.CarSystemBarElementController;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

/**
 * Dagger injection module for {@link QuickControlsEntryPointsController}
 */
@Module
public abstract class QuickControlsEntryPointsModule {

    /** Injects BluetoothStatusIconController. */
    @Binds
    @IntoMap
    @ClassKey(BluetoothStatusIconController.class)
    public abstract StatusIconController bindBluetoothStatusIconController(
            BluetoothStatusIconController bluetoothStatusIconController);

    /** Injects SignalStatusIconController. */
    @Binds
    @IntoMap
    @ClassKey(SignalStatusIconController.class)
    public abstract StatusIconController bindSignalStatusIconController(
            SignalStatusIconController signalStatusIconController);

    /** Injects DisplayStatusIconController. */
    @Binds
    @IntoMap
    @ClassKey(DisplayStatusIconController.class)
    public abstract StatusIconController bindDisplayStatusIconController(
            DisplayStatusIconController displayStatusIconController);

    /** Injects LocationStatusIconController. */
    @Binds
    @IntoMap
    @ClassKey(LocationStatusIconController.class)
    public abstract CarSystemBarElementController.Factory bindLocationStatusIconController(
            LocationStatusIconController.Factory locationStatusIconController);

    /** Injects PhoneCallStatusIconController. */
    @Binds
    @IntoMap
    @ClassKey(PhoneCallStatusIconController.class)
    public abstract CarSystemBarElementController.Factory bindPhoneCallStatusIconController(
            PhoneCallStatusIconController.Factory phoneCallStatusIconController);

    /** Injects ThemeSwitchStatusIconController. */
    @Binds
    @IntoMap
    @ClassKey(DriveModeStatusIconController.class)
    public abstract StatusIconController bindDriveModeStatusIconController(
            DriveModeStatusIconController driveModeStatusIconController);

    /** Injects MediaVolumeStatusIconController. */
    @Binds
    @IntoMap
    @ClassKey(MediaVolumeStatusIconController.class)
    public abstract StatusIconController bindMediaVolumeStatusIconController(
            MediaVolumeStatusIconController mediaVolumeStatusIconController);

    /** Injects QuickControlsStatusIconListController. */
    @Binds
    @IntoMap
    @ClassKey(QuickControlsStatusIconListController.class)
    public abstract StatusIconController bindQuickControlsStatusIconListController(
            QuickControlsStatusIconListController quickControlsStatusIconListController);

}
