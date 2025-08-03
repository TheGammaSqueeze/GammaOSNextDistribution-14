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

package com.android.systemui.car.systembar;

import android.content.Context;

import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.dagger.CarSysUIDynamicOverride;
import com.android.systemui.car.statusbar.UserNameViewController;
import com.android.systemui.car.statusicon.StatusIconPanelViewController;
import com.android.systemui.car.statusicon.ui.DistantDisplayStatusIconController;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.settings.UserTracker;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

import javax.inject.Provider;

/**
 * Dagger injection module for {@link CarSystemBar} in CarDistantDisplaySystemUI.
 */
@Module(includes = {CarSystemBarModule.class})
public class DistantDisplaySystemBarModule {

    @SysUISingleton
    @Provides
    @CarSysUIDynamicOverride
    static CarSystemBarController provideCarSystemBarController(Context context,
            UserTracker userTracker,
            CarSystemBarViewFactory carSystemBarViewFactory,
            CarServiceProvider carServiceProvider,
            ButtonSelectionStateController buttonSelectionStateController,
            Lazy<UserNameViewController> userNameViewControllerLazy,
            Lazy<MicPrivacyChipViewController> micPrivacyChipViewControllerLazy,
            Lazy<CameraPrivacyChipViewController> cameraPrivacyChipViewControllerLazy,
            ButtonRoleHolderController buttonRoleHolderController,
            SystemBarConfigs systemBarConfigs,
            Provider<StatusIconPanelViewController.Builder> panelControllerBuilderProvider,
            DistantDisplayStatusIconController distantDisplayStatusIconController) {
        return new DistantDisplayCarSystemBarController(context, userTracker,
                carSystemBarViewFactory, carServiceProvider, buttonSelectionStateController,
                userNameViewControllerLazy,
                micPrivacyChipViewControllerLazy, cameraPrivacyChipViewControllerLazy,
                buttonRoleHolderController, systemBarConfigs, panelControllerBuilderProvider,
                distantDisplayStatusIconController);
    }
}
