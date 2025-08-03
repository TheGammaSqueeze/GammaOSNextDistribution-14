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

package com.android.adservices.service.measurement.access;

import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_UNSET;

import android.adservices.common.AdServicesStatusUtils;
import android.annotation.NonNull;
import android.content.Context;

import java.util.function.Supplier;

/** Resolves the kill switch status. */
public class KillSwitchAccessResolver implements IAccessResolver {
    private static final String ERROR_MESSAGE = "Measurement API is disabled by kill-switch.";
    private final boolean mEnabled;
    public KillSwitchAccessResolver(@NonNull Supplier<Boolean> enabledSupplier) {
        mEnabled = enabledSupplier.get();
    }

    @Override
    public AccessInfo getAccessInfo(@NonNull Context context) {
        return new AccessInfo(!mEnabled, FAILURE_REASON_UNSET);
    }

    @NonNull
    @Override
    @AdServicesStatusUtils.StatusCode
    public int getErrorStatusCode() {
        return AdServicesStatusUtils.STATUS_KILLSWITCH_ENABLED;
    }

    @NonNull
    @Override
    public String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}
