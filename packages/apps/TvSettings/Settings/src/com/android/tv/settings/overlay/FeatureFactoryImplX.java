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

package com.android.tv.settings.overlay;

import androidx.annotation.Keep;

import com.android.tv.settings.basic.BasicModeFeatureProvider;
import com.android.tv.settings.basic.BasicModeFeatureProviderImplX;
import com.android.tv.settings.startup.StartupVerificationFeatureProvider;
import com.android.tv.settings.startup.StartupVerificationFeatureProviderImplX;

/** Implementation of {@FeatureFactory} that can be utilized with resource overlay. */
@Keep
public class FeatureFactoryImplX extends FeatureFactoryImplTwoPanel {

    protected static final String TAG = "FeatureFactoryImplX";

    @Override
    public BasicModeFeatureProvider getBasicModeFeatureProvider() {
        return new BasicModeFeatureProviderImplX();
    }

    @Override
    public StartupVerificationFeatureProvider getStartupVerificationFeatureProvider() {
        return new StartupVerificationFeatureProviderImplX();
    }
}
