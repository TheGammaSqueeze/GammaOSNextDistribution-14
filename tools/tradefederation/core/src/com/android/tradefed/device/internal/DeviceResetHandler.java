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

import com.android.annotations.VisibleForTesting;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.NativeDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.error.IHarnessException;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.logger.CurrentInvocation;
import com.android.tradefed.invoker.logger.CurrentInvocation.IsolationGrade;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.InfraErrorIdentifier;
import com.android.tradefed.service.TradefedFeatureClient;
import com.android.tradefed.util.SerializationUtil;

import com.proto.tradefed.feature.FeatureResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility handling generically device resetting. This is meant to only be used internally to the
 * test harness. This shouldn't be called during a test.
 */
public class DeviceResetHandler {

    private final TradefedFeatureClient mClient;
    private final IInvocationContext mContext;

    public DeviceResetHandler() {
        this(new TradefedFeatureClient(), CurrentInvocation.getInvocationContext());
    }

    public DeviceResetHandler(IInvocationContext context) {
        this(new TradefedFeatureClient(), context);
    }

    @VisibleForTesting
    DeviceResetHandler(TradefedFeatureClient client, IInvocationContext context) {
        mClient = client;
        mContext = context;
    }

    /**
     * Calls reset of the given device.
     *
     * @param device The device to reset.
     * @return True if reset was successful, false otherwise.
     * @throws DeviceNotAvailableException
     */
    public boolean resetDevice(ITestDevice device) throws DeviceNotAvailableException {
        if (device.getIDevice() instanceof StubDevice) {
            CLog.d("Device '%s' is a stub device. skipping reset.", device.getSerialNumber());
            return true;
        }
        FeatureResponse response;
        try {
            Map<String, String> args = new HashMap<>();
            args.put(DeviceResetFeature.DEVICE_NAME, mContext.getDeviceName(device));
            response = mClient.triggerFeature(DeviceResetFeature.DEVICE_RESET_FEATURE_NAME, args);
            CLog.d("Response from reset request: %s", response.getResponse());
        } finally {
            mClient.close();
        }
        if (response.hasErrorInfo()) {
            String trace = response.getErrorInfo().getErrorTrace();
            // Handle if it's an exception error.
            Object o = null;
            try {
                o = SerializationUtil.deserialize(trace);
                CLog.e("Reset failed: %s", o);
            } catch (IOException | RuntimeException e) {
                CLog.e(e);
            }
            if (o instanceof DeviceNotAvailableException) {
                throw (DeviceNotAvailableException) o;
            } else if (o instanceof IHarnessException) {
                IHarnessException exception = (IHarnessException) o;
                throw new HarnessRuntimeException(
                        "Exception while resetting the device.", exception);
            } else if (o instanceof Exception) {
                throw new HarnessRuntimeException(
                        "Exception while resetting the device.",
                        (Exception) o, InfraErrorIdentifier.UNDETERMINED);
            }
            return false;
        }
        if (device instanceof NativeDevice) {
            ((NativeDevice) device).resetContentProviderSetup();
        }
        CurrentInvocation.setModuleIsolation(IsolationGrade.FULLY_ISOLATED);
        CurrentInvocation.setRunIsolation(IsolationGrade.FULLY_ISOLATED);

        // Save powerwash performance data
        Pattern durationPattern = Pattern.compile("Powerwash\\sfinished\\sin (\\d+)\\sms");
        Matcher matcher;
        matcher = durationPattern.matcher(response.getResponse());
        if (matcher.find()) {
            InvocationMetricLogger.addInvocationMetrics(InvocationMetricKey.DEVICE_RESET_COUNT, 1);
            InvocationMetricLogger.addInvocationMetrics(
                    InvocationMetricKey.DEVICE_POWERWASH_DURATIONS, matcher.group(1));
        }
        return true;
    }
}
