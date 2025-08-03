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

package com.android.systemui.go;

import com.android.systemui.dagger.DefaultActivityBinder;
import com.android.systemui.dagger.DefaultBroadcastReceiverBinder;
import com.android.systemui.dagger.DefaultServiceBinder;
import com.android.systemui.dagger.DependencyProvider;
import com.android.systemui.dagger.SysUIComponent;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.SystemUIModule;
import com.android.systemui.keyguard.dagger.KeyguardModule;
import com.android.systemui.keyguard.CustomizationProvider;
import com.android.systemui.recents.RecentsModule;
import com.android.systemui.scene.SceneContainerFrameworkModule;
import com.android.systemui.statusbar.dagger.CentralSurfacesModule;
import com.android.systemui.statusbar.NotificationInsetsModule;
import com.android.systemui.statusbar.QsFrameTranslateModule;

import dagger.Subcomponent;

/**
 * Dagger Subcomponent for Core System UI on Android Go.
 */
@SysUISingleton
@Subcomponent(modules = {
        DependencyProvider.class,
        SystemUIModule.class,
        DefaultActivityBinder.class,
        DefaultBroadcastReceiverBinder.class,
        DefaultServiceBinder.class,
        SystemUIGoCoreStartableModule.class,
        KeyguardModule.class,
        RecentsModule.class,
        SceneContainerFrameworkModule.class,
        CentralSurfacesModule.class,
        NotificationInsetsModule.class,
        QsFrameTranslateModule.class,
        SystemUIGoModule.class})
public interface SystemUIGoComponent extends SysUIComponent {

    /**
     * Builder for a SysUIComponent.
     */
    @Subcomponent.Builder
    interface Builder extends SysUIComponent.Builder {
        SystemUIGoComponent build();
    }

    /**
     * Member injection into the supplied argument.
     */
    void inject(CustomizationProvider customizationProvider);
}
