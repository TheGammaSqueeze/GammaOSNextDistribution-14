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

package com.android.ondevicepersonalization.services;


import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationDebugService;
import android.annotation.NonNull;
import android.content.Context;

import com.android.ondevicepersonalization.services.util.DebugUtils;

import java.util.Objects;

/**
 * Service that provides test and debug APIs.
 */
public class OnDevicePersonalizationDebugServiceDelegate
        extends IOnDevicePersonalizationDebugService.Stub {
    @NonNull private final Context mContext;

    public OnDevicePersonalizationDebugServiceDelegate(@NonNull Context context) {
        mContext = Objects.requireNonNull(context);
    }

    @Override
    public boolean isEnabled() {
        return DebugUtils.isDeveloperModeEnabled(mContext);
    }
}
