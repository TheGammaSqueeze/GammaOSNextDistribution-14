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

package com.android.systemui.car.qc;

import com.android.car.qc.provider.BaseLocalQCProvider;
import com.android.systemui.car.privacy.CameraQcPanel;
import com.android.systemui.car.privacy.MicQcPanel;
import com.android.systemui.car.statusicon.ui.MobileSignalStatusIconController;
import com.android.systemui.car.statusicon.ui.WifiSignalStatusIconController;
import com.android.systemui.car.systembar.element.CarSystemBarElementController;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

/**
 * Dagger injection module for {@link SystemUIQCViewController}
 */
@Module
public abstract class QuickControlsModule {
    /** Injects ProfileSwitcher. */
    @Binds
    @IntoMap
    @ClassKey(ProfileSwitcher.class)
    public abstract BaseLocalQCProvider bindProfileSwitcher(
            ProfileSwitcher profileSwitcher);

    /** Injects MicQCPanel. */
    @Binds
    @IntoMap
    @ClassKey(MicQcPanel.class)
    public abstract BaseLocalQCProvider bindMicQcPanel(
            MicQcPanel micQcPanel);

    /** Injects CameraQcPanel. */
    @Binds
    @IntoMap
    @ClassKey(CameraQcPanel.class)
    public abstract BaseLocalQCProvider bindCameraQcPanel(
            CameraQcPanel micQcPanel);

    /** Injects DriveModeQcPanel. */
    @Binds
    @IntoMap
    @ClassKey(DriveModeQcPanel.class)
    public abstract BaseLocalQCProvider bindDriveModeQcPanel(
            DriveModeQcPanel driveModeQcPanel);

    /** Injects MobileSignalStatusIconController. */
    @Binds
    @IntoMap
    @ClassKey(MobileSignalStatusIconController.class)
    public abstract CarSystemBarElementController.Factory bindMobileSignalStatusIconController(
            MobileSignalStatusIconController.Factory mobileSignalStatusIconController);

    /** Injects WifiSignalStatusIconController. */
    @Binds
    @IntoMap
    @ClassKey(WifiSignalStatusIconController.class)
    public abstract CarSystemBarElementController.Factory bindWifiSignalStatusIconController(
            WifiSignalStatusIconController.Factory wifiSignalStatusIconController);

    /** Injects SystemUIQCViewController. */
    @Binds
    @IntoMap
    @ClassKey(SystemUIQCViewController.class)
    public abstract CarSystemBarElementController.Factory bindQCViewControllerFactory(
            SystemUIQCViewController.Factory factory);

    /** Injects QCFooterButtonController. */
    @Binds
    @IntoMap
    @ClassKey(QCFooterButtonController.class)
    public abstract CarSystemBarElementController.Factory bindQCFooterButtonControllerFactory(
            QCFooterButtonController.Factory factory);

    /** Injects QCFooterViewController. */
    @Binds
    @IntoMap
    @ClassKey(QCFooterViewController.class)
    public abstract CarSystemBarElementController.Factory bindQCFooterViewControllerFactory(
            QCFooterViewController.Factory factory);

    /** Injects QCLogoutButtonController. */
    @Binds
    @IntoMap
    @ClassKey(QCLogoutButtonController.class)
    public abstract CarSystemBarElementController.Factory bindQCLogoutButtonControllerFactory(
            QCLogoutButtonController.Factory factory);

    /** Injects QCScreenOffButtonController. */
    @Binds
    @IntoMap
    @ClassKey(QCScreenOffButtonController.class)
    public abstract CarSystemBarElementController.Factory bindQCScreenOffButtonControllerFactory(
            QCScreenOffButtonController.Factory factory);

    /** Injects QCUserPickerButtonController. */
    @Binds
    @IntoMap
    @ClassKey(QCUserPickerButtonController.class)
    public abstract CarSystemBarElementController.Factory bindQCUserPickerButtonControllerFactory(
            QCUserPickerButtonController.Factory factory);
}
