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

package com.android.systemui.car.window;

import com.android.systemui.car.hvac.AutoDismissHvacPanelOverlayViewMediator;
import com.android.systemui.car.loading.LoadingViewMediator;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

/** Lists additional {@link OverlayViewMediator} that apply to the CarUiPortraitSystemUI. */
@Module
public abstract class ExtendedOverlayWindowModule {

    /** Injects RearViewCameraViewMediator. */
    @Binds
    @IntoMap
    @ClassKey(AutoDismissHvacPanelOverlayViewMediator.class)
    public abstract OverlayViewMediator bindAutoDismissHvacPanelViewMediator(
            AutoDismissHvacPanelOverlayViewMediator overlayViewsMediator);

    /** Injects LoadingViewMediator. */
    @Binds
    @IntoMap
    @ClassKey(LoadingViewMediator.class)
    public abstract OverlayViewMediator bindLoadingViewMediator(
            LoadingViewMediator overlayViewsMediator);
}
