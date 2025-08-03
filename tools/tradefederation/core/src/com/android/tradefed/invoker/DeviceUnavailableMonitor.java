/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tradefed.invoker;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;

/** This listener attempts to capture a test case level DNAE only. */
public final class DeviceUnavailableMonitor implements ITestInvocationListener {

    private boolean mInvocationFailed = false;
    private DeviceNotAvailableException mUnavailableException = null;
    private String mSerial = null;

    @Override
    public void invocationStarted(IInvocationContext context) {
        for (ITestDevice device : context.getDevices()) {
            if (!(device.getIDevice() instanceof StubDevice)) {
                mSerial = device.getSerialNumber();
            }
        }
    }

    @Override
    public void testRunFailed(FailureDescription failure) {
        if (mUnavailableException != null || mInvocationFailed) {
            return;
        }
        mUnavailableException = analyzeFailure(failure);
    }

    @Override
    public void testFailed(TestDescription test, FailureDescription failure) {
        if (mUnavailableException != null || mInvocationFailed) {
            return;
        }
        mUnavailableException = analyzeFailure(failure);
    }

    @Override
    public void invocationFailed(FailureDescription failure) {
        mInvocationFailed = true;
        if (mUnavailableException != null) {
          return;
        }
        mUnavailableException = analyzeFailure(failure);
    }

    @Override
    public void invocationFailed(Throwable cause) {
        mInvocationFailed = true;
    }

    /** Returns the exception if any was captured. */
    public DeviceNotAvailableException getUnavailableException() {
        return mUnavailableException;
    }

    private DeviceNotAvailableException analyzeFailure(FailureDescription failure) {
        if (failure.getCause() != null
                && failure.getCause() instanceof DeviceNotAvailableException) {
            return (DeviceNotAvailableException) failure.getCause();
        } else if (failure.getFailureStatus() != null
                && FailureStatus.LOST_SYSTEM_UNDER_TEST.equals(failure.getFailureStatus())) {
            return new DeviceNotAvailableException(failure.getErrorMessage(), mSerial);
        } else if (failure.getErrorIdentifier() != null) {
            if (DeviceErrorIdentifier.DEVICE_UNAVAILABLE.equals(failure.getErrorIdentifier())) {
                return new DeviceNotAvailableException(failure.getErrorMessage(), mSerial);
            } else if (DeviceErrorIdentifier.DEVICE_UNRESPONSIVE.equals(
                    failure.getErrorIdentifier())) {
                return new DeviceUnresponsiveException(failure.getErrorMessage(), mSerial);
            }
        }
        return null;
    }
}
