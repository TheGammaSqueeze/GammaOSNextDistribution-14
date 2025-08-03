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
package com.android.tradefed.suite.checker;

import static org.junit.Assert.assertEquals;

import com.android.tradefed.device.TestDevice;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.suite.checker.StatusCheckerResult.CheckStatus;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IDeviceTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Functional tests for the {@link ITestDevice} user management APIs */
@RunWith(DeviceJUnit4ClassRunner.class)
public class ActivityStatusCheckerFuncTest implements IDeviceTest {
    private TestDevice mTestDevice;
    private ActivityStatusChecker mChecker;

    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = (TestDevice) device;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    @Before
    public void setUp() throws Exception {
        // Ensure at set-up that the device is available.
        mTestDevice.waitForDeviceAvailable();
        mChecker = new ActivityStatusChecker();
    }

    /** Tests the postExecutionCheck method which calls private method isFrontActivityLauncher */
    @Test
    public void testPostExecutionCheck() throws Exception {
        assertEquals(CheckStatus.SUCCESS, mChecker.postExecutionCheck(mTestDevice).getStatus());
    }
}
