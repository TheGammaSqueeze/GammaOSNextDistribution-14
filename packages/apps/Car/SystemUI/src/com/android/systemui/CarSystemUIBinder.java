/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.systemui.car.keyguard.CarKeyguardModule;
import com.android.systemui.car.notification.CarNotificationModule;
import com.android.systemui.car.qc.QuickControlsModule;
import com.android.systemui.car.statusicon.ui.QuickControlsEntryPointsModule;
import com.android.systemui.car.systembar.CarSystemBarModule;
import com.android.systemui.car.window.OverlayWindowModule;
import com.android.systemui.recents.RecentsModule;
import com.android.systemui.statusbar.dagger.CentralSurfacesDependenciesModule;
import com.android.systemui.statusbar.notification.dagger.NotificationsModule;
import com.android.systemui.statusbar.notification.row.NotificationRowModule;

import dagger.Module;

/** Binder for car specific {@link CoreStartable} modules. */
@Module(includes = {RecentsModule.class, CentralSurfacesDependenciesModule.class,
        NotificationsModule.class, NotificationRowModule.class, CarKeyguardModule.class,
        OverlayWindowModule.class, CarNotificationModule.class, QuickControlsModule.class,
        QuickControlsEntryPointsModule.class, CarSystemBarModule.class})
public abstract class CarSystemUIBinder {
}
