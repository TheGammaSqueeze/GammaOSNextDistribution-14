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

package com.android.tv.settings.basic;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;

/** Implementation of {@link BasicModeFeatureProvider} */
public class BasicModeFeatureProviderImpl implements BasicModeFeatureProvider {

    protected static final String TAG = "BasicModeFeature";

    @Override
    public boolean isBasicMode(@NonNull Context context) {
        return false;
    }

    @Override
    public void startBasicModeExitActivity(@NonNull Activity activity) {
        // no-op
    }

    @Override
    public void startBasicModeInternetBlock(@NonNull Activity activity) {
        // no-op
    }

    @Override
    public boolean isStoreDemoMode(@NonNull Context context) {
        return false;
    }
}
