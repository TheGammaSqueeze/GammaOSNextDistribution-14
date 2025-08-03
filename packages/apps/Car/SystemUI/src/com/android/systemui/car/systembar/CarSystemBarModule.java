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
import android.content.res.Resources;

import com.android.systemui.CoreStartable;
import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.dagger.CarSysUIDynamicOverride;
import com.android.systemui.car.statusbar.UserNameViewController;
import com.android.systemui.car.statusicon.StatusIconPanelViewController;
import com.android.systemui.car.systembar.element.CarSystemBarElementController;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener;

import dagger.Binds;
import dagger.BindsOptionalOf;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.Multibinds;

import java.util.Map;
import java.util.Optional;

import javax.inject.Provider;

/**
 * Dagger injection module for {@link CarSystemBar}.
 *
 * This module includes the non-@Inject classes used as part of the {@link CarSystemBar}, allowing
 * extensions of SystemUI to override and provide their own implementations without replacing the
 * default system bar class.
 */
@Module
public abstract class CarSystemBarModule {
    /**
     * TODO(): b/260206944,
     * @return CarSystemBarMediator for SecondaryMUMDSystemUI which blocks CarSystemBar#start()
     * util RROs are applied, otherwise return CarSystemBar
     */
    @Provides
    @IntoMap
    @ClassKey(CarSystemBar.class)
    static CoreStartable bindCarSystemBarStartable(
            Lazy<CarSystemBar> systemBarService,
            Lazy<CarSystemBarMediator> applyRROService,
            @Main Resources resources) {
        if ((CarSystemUIUserUtil.isSecondaryMUMDSystemUI()
                || CarSystemUIUserUtil.isMUPANDSystemUI())
                && resources.getBoolean(R.bool.config_enableSecondaryUserRRO)) {
            return applyRROService.get();
        }
        return systemBarService.get();
    }

    @Provides
    @IntoSet
    static ConfigurationListener provideCarSystemBarConfigListener(
            Lazy<CarSystemBar> systemBarService,
            Lazy<CarSystemBarMediator> applyRROService,
            @Main Resources resources) {
        if ((CarSystemUIUserUtil.isSecondaryMUMDSystemUI()
                || CarSystemUIUserUtil.isMUPANDSystemUI())
                && resources.getBoolean(R.bool.config_enableSecondaryUserRRO)) {
            return applyRROService.get();
        }
        return systemBarService.get();
    }

    @BindsOptionalOf
    @CarSysUIDynamicOverride
    abstract ButtonSelectionStateListener optionalButtonSelectionStateListener();

    @SysUISingleton
    @Provides
    static ButtonSelectionStateListener provideButtonSelectionStateListener(@CarSysUIDynamicOverride
            Optional<ButtonSelectionStateListener> overrideButtonSelectionStateListener,
            ButtonSelectionStateController controller) {
        if (overrideButtonSelectionStateListener.isPresent()) {
            return overrideButtonSelectionStateListener.get();
        }
        return new ButtonSelectionStateListener(controller);
    }

    @BindsOptionalOf
    @CarSysUIDynamicOverride
    abstract ButtonSelectionStateController optionalButtonSelectionStateController();

    @SysUISingleton
    @Provides
    static ButtonSelectionStateController provideButtonSelectionStateController(Context context,
            @CarSysUIDynamicOverride Optional<ButtonSelectionStateController> controller) {
        if (controller.isPresent()) {
            return controller.get();
        }
        return new ButtonSelectionStateController(context);
    }

    @BindsOptionalOf
    @CarSysUIDynamicOverride
    abstract CarSystemBarController optionalCarSystemBarController();

    @SysUISingleton
    @Provides
    static CarSystemBarController provideCarSystemBarController(
            @CarSysUIDynamicOverride Optional<CarSystemBarController> carSystemBarController,
            Context context,
            UserTracker userTracker,
            CarSystemBarViewFactory carSystemBarViewFactory,
            CarServiceProvider carServiceProvider,
            ButtonSelectionStateController buttonSelectionStateController,
            Lazy<UserNameViewController> userNameViewControllerLazy,
            Lazy<MicPrivacyChipViewController> micPrivacyChipViewControllerLazy,
            Lazy<CameraPrivacyChipViewController> cameraPrivacyChipViewControllerLazy,
            ButtonRoleHolderController buttonRoleHolderController,
            SystemBarConfigs systemBarConfigs,
            Provider<StatusIconPanelViewController.Builder> panelControllerBuilderProvider) {
        if (carSystemBarController.isPresent()) {
            return carSystemBarController.get();
        }
        return new CarSystemBarController(context, userTracker, carSystemBarViewFactory,
                carServiceProvider, buttonSelectionStateController, userNameViewControllerLazy,
                micPrivacyChipViewControllerLazy, cameraPrivacyChipViewControllerLazy,
                buttonRoleHolderController, systemBarConfigs, panelControllerBuilderProvider);
    }

    // CarSystemBarElements

    /** Empty set for CarSystemBarElements. */
    @Multibinds
    abstract Map<Class<?>, CarSystemBarElementController.Factory> bindEmptyElementFactoryMap();

    /** Injects CarSystemBarPanelButtonViewController */
    @Binds
    @IntoMap
    @ClassKey(CarSystemBarPanelButtonViewController.class)
    public abstract CarSystemBarElementController.Factory bindSystemBarPanelButtonController(
            CarSystemBarPanelButtonViewController.Factory factory);
}
