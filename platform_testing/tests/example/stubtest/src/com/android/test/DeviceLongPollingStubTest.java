/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.test;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class DeviceLongPollingStubTest extends BaseHostJUnit4Test {

    @Option(name = "duration", description = "Device long polling duration in seconds.")
    private int mLongPollingDurationSec = 2 * 60 * 60;

    @Option(name = "connection-check-interval", description = "Interval for adb connection checks.")
    private int mConnectionCheckIntervalSec = 60;

    @Test
    public void testDeviceLongPolling() throws DeviceNotAvailableException {
        for (int i = 0; i < mLongPollingDurationSec / mConnectionCheckIntervalSec; i++) {
            getDevice().executeShellCommand("log connecting_to_device");
            RunUtil.getDefault().sleep(mConnectionCheckIntervalSec * 1000);
        }
    }
}
