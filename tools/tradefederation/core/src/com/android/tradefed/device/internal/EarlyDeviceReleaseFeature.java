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
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.service.internal.IRemoteScheduledListenersFeature;
import com.android.tradefed.testtype.ITestInformationReceiver;

import com.proto.tradefed.feature.FeatureRequest;
import com.proto.tradefed.feature.FeatureResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Feature server implementation for early device release. */
public class EarlyDeviceReleaseFeature
        implements ITestInformationReceiver, IRemoteScheduledListenersFeature {

    public static final String EARLY_DEVICE_RELEASE_FEATURE_NAME = "earlyDeviceRelease";

    private TestInformation mTestInformation;
    private List<IScheduledInvocationListener> mScheduledInvocationListeners;

    @Override
    public String getName() {
        return EARLY_DEVICE_RELEASE_FEATURE_NAME;
    }

    @Override
    public void setTestInformation(TestInformation testInformation) {
        mTestInformation = testInformation;
    }

    @Override
    public TestInformation getTestInformation() {
        return mTestInformation;
    }

    @Override
    public void setListeners(List<IScheduledInvocationListener> listeners) {
        mScheduledInvocationListeners = listeners;
    }

    @Override
    public List<IScheduledInvocationListener> getListeners() {
        return mScheduledInvocationListeners;
    }

    @Override
    public FeatureResponse execute(FeatureRequest featureRequest) {
        Map<String, String> deviceStatusMap = featureRequest.getArgsMap();
        Map<ITestDevice, FreeDeviceState> deviceStates = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<String, String> entry : deviceStatusMap.entrySet()) {
            ITestDevice device = mTestInformation.getContext().getDevice(entry.getKey());
            if (device == null) {
                device = mTestInformation.getContext().getDevices().get(index);
            }
            deviceStates.put(device, FreeDeviceState.valueOf(entry.getValue()));
            index++;
        }
        mTestInformation.getContext().markReleasedEarly();
        for (IScheduledInvocationListener listener : mScheduledInvocationListeners) {
            listener.releaseDevices(mTestInformation.getContext(), deviceStates);
        }

        return FeatureResponse.newBuilder().build();
    }
}
