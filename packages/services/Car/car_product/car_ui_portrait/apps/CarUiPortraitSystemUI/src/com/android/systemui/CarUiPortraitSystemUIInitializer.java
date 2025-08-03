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

import android.content.Context;
import android.os.UserHandle;

import com.android.systemui.dagger.GlobalRootComponent;
import com.android.systemui.dagger.SysUIComponent;
import com.android.systemui.dagger.WMComponent;
import com.android.systemui.wmshell.CarUiPortraitWMComponent;

import java.util.Optional;


/**
 * Class factory to provide AAECarSystemUI specific SystemUI components.
 */
public class CarUiPortraitSystemUIInitializer extends CarSystemUIInitializer {
    private final boolean mRegisterCarSystemUIProxy;
    public CarUiPortraitSystemUIInitializer(Context context) {
        super(context);
        mRegisterCarSystemUIProxy = context.getResources().getBoolean(
                R.bool.config_registerCarSystemUIProxy);
    }

    @Override
    protected GlobalRootComponent.Builder getGlobalRootComponentBuilder() {
        return DaggerCarUiPortraitGlobalRootComponent.builder();
    }

    @Override
    protected SysUIComponent.Builder prepareSysUIComponentBuilder(
            SysUIComponent.Builder sysUIBuilder, WMComponent wm) {
        CarUiPortraitWMComponent carWm = (CarUiPortraitWMComponent) wm;
        boolean isSystemUser = UserHandle.myUserId() == UserHandle.USER_SYSTEM;

        if (mRegisterCarSystemUIProxy && isSystemUser) {
            carWm.getCarSystemUIProxy();
        }
        return ((CarUiPortraitSysUIComponent.Builder) sysUIBuilder).setRootTaskDisplayAreaOrganizer(
                isSystemUser ? Optional.of(carWm.getRootTaskDisplayAreaOrganizer())
                        : Optional.empty())
                .setCarUiPortraitDisplaySystemBarsController(
                        carWm.getCarUiPortraitDisplaySystemBarsController())
                        .setMDSystemBarsController(carWm.getMDSystemBarController());
    }
}
