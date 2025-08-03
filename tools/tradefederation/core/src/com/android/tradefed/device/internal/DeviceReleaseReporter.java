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

package com.android.tradefed.device.internal;

import com.android.tradefed.command.ICommandScheduler.IScheduledInvocationListener;
import com.android.tradefed.device.FreeDeviceState;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.service.TradefedFeatureClient;

import com.google.common.annotations.VisibleForTesting;
import com.proto.tradefed.feature.FeatureResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Device release reporter that handles communicating with the parent process the device(s) to be
 * released.
 */
public final class DeviceReleaseReporter implements IScheduledInvocationListener {

    private final TradefedFeatureClient mTradefedFeatureClient;

    public DeviceReleaseReporter() {
        this(new TradefedFeatureClient());
    }

    @VisibleForTesting
    DeviceReleaseReporter(TradefedFeatureClient featureClient) {
        mTradefedFeatureClient = featureClient;
    }

    @Override
    public void invocationInitiated(IInvocationContext context) {
        // Not implemented
    }

    @Override
    public void releaseDevices(
            IInvocationContext context, Map<ITestDevice, FreeDeviceState> devicesStates) {
        try {
            Map<String, String> args = new LinkedHashMap<>();
            for (Entry<ITestDevice, FreeDeviceState> entry : devicesStates.entrySet()) {
                args.put(context.getDeviceName(entry.getKey()), entry.getValue().name());
            }
            FeatureResponse response =
                    mTradefedFeatureClient.triggerFeature(
                            EarlyDeviceReleaseFeature.EARLY_DEVICE_RELEASE_FEATURE_NAME, args);
            if (response.hasErrorInfo()) {
                CLog.e("Feature Response Error: " + response.getErrorInfo());
                return;
            }
        } finally {
            mTradefedFeatureClient.close();
        }
    }

    @Override
    public void invocationComplete(
            IInvocationContext iInvocationContext, Map<ITestDevice, FreeDeviceState> map) {
        // Not implemented.
    }
}
