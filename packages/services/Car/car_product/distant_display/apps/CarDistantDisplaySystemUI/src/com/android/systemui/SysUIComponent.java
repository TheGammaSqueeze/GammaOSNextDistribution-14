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

import com.android.systemui.dagger.DependencyProvider;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.SystemUIModule;
import com.android.systemui.scene.ShadelessSceneContainerFrameworkModule;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;

import dagger.BindsInstance;
import dagger.Subcomponent;

import java.util.Optional;

/**
 * Dagger Subcomponent for Core SysUI.
 */
@SysUISingleton
@Subcomponent(modules = {
        CarComponentBinder.class,
        DependencyProvider.class,
        SystemUIModule.class,
        CarSystemUIModule.class,
        CarSystemUICoreStartableModule.class,
        SystemUIBinder.class,
        ShadelessSceneContainerFrameworkModule.class})
public interface SysUIComponent extends CarSysUIComponent {
    /**
     * Builder for a CarSysUIComponent.
     */
    @Subcomponent.Builder
    interface Builder extends CarSysUIComponent.Builder {
        /**
         * sets RootTaskDisplayAreaOrganizer.
         */
        @BindsInstance
        SysUIComponent.Builder setRootTaskDisplayAreaOrganizer(
                Optional<RootTaskDisplayAreaOrganizer> r);

        /**
         * Builds CarUiDistantDisplaySysUIComponent
         */
        SysUIComponent build();
    }
}
