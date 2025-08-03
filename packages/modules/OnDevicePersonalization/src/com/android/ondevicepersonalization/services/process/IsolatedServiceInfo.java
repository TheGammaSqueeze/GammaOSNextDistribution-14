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

package com.android.ondevicepersonalization.services.process;

import static android.adservices.ondevicepersonalization.OnDevicePersonalizationException.ERROR_ISOLATED_SERVICE_FAILED;

import android.adservices.ondevicepersonalization.aidl.IIsolatedService;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;

import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.ondevicepersonalization.libraries.plugin.PluginController;
import com.android.ondevicepersonalization.services.OdpServiceException;

import java.util.Objects;

/** Wraps an instance of a loaded isolated service */
public class IsolatedServiceInfo {
    private final long mStartTimeMillis;
    @NonNull private final ComponentName mComponentName;
    @Nullable private final PluginController mPluginController;
    @Nullable private final AbstractServiceBinder<IIsolatedService> mIsolatedServiceBinder;

    IsolatedServiceInfo(
            long startTimeMillis,
            @NonNull ComponentName componentName,
            @Nullable PluginController pluginController,
            @Nullable AbstractServiceBinder<IIsolatedService> isolatedServiceBinder)
            throws OdpServiceException {
        mStartTimeMillis = startTimeMillis;
        mComponentName = Objects.requireNonNull(componentName);
        mPluginController = pluginController;
        mIsolatedServiceBinder = isolatedServiceBinder;

        // TO-DO (323882182): Granular isolated servce failures.
        if ((mPluginController != null && mIsolatedServiceBinder != null)
                || (mPluginController == null && mIsolatedServiceBinder == null)) {
            throw new OdpServiceException(ERROR_ISOLATED_SERVICE_FAILED);
        }
    }

    PluginController getPluginController() {
        return mPluginController;
    }

    AbstractServiceBinder<IIsolatedService> getIsolatedServiceBinder() {
        return mIsolatedServiceBinder;
    }

    /** Returns the service start time. */
    public long getStartTimeMillis() {
        return mStartTimeMillis;
    }

    /** Returns the ComponentName to be loaded. */
    @NonNull public ComponentName getComponentName() {
        return mComponentName;
    }
}
