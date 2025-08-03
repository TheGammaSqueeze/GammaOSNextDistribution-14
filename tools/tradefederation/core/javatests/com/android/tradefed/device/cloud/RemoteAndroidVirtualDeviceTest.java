/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.device.cloud;

import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.device.IDeviceRecovery;
import com.android.tradefed.device.IDeviceStateMonitor;
import com.android.tradefed.device.TestDevice;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link RemoteAndroidVirtualDevice}. */
@RunWith(JUnit4.class)
public class RemoteAndroidVirtualDeviceTest {

    private static final String MOCK_DEVICE_SERIAL = "localhost:1234";

    @Mock IDevice mMockIDevice;
    @Mock ITestLogger mTestLogger;
    @Mock IDeviceStateMonitor mMockStateMonitor;
    @Mock IRunUtil mMockRunUtil;
    @Mock IDeviceMonitor mMockDvcMonitor;
    @Mock IDeviceRecovery mMockRecovery;
    private RemoteAndroidVirtualDevice mTestDevice;

    /** A {@link TestDevice} that is suitable for running tests against */
    private class TestableRemoteAndroidVirtualDevice extends RemoteAndroidVirtualDevice {
        public TestableRemoteAndroidVirtualDevice() {
            super(mMockIDevice, mMockStateMonitor, mMockDvcMonitor);
            mOptions = new TestDeviceOptions();
            // Disable connection to test the internal codepath
            mOptions.setUseConnection(false);
        }

        @Override
        protected IRunUtil getRunUtil() {
            return mMockRunUtil;
        }

        @Override
        public IDevice getIDevice() {
            return mMockIDevice;
        }

        @Override
        public DeviceDescriptor getDeviceDescriptor() {
            DeviceDescriptor desc =
                    new DeviceDescriptor(
                            "", false, DeviceAllocationState.Allocated, "", "", "", "", "");
            return desc;
        }

        @Override
        public String getSerialNumber() {
            return MOCK_DEVICE_SERIAL;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockIDevice.getSerialNumber()).thenReturn(MOCK_DEVICE_SERIAL);

        // A TestDevice with a no-op recoverDevice() implementation
        mTestDevice = new TestableRemoteAndroidVirtualDevice();
        mTestDevice.setRecovery(mMockRecovery);

        try {
            GlobalConfiguration.createGlobalConfiguration(new String[] {"empty"});
        } catch (IllegalStateException e) {
            // Ignore reinit
        }
    }

    @After
    public void tearDown() {
        FileUtil.deleteFile(mTestDevice.getExecuteShellCommandLog());
    }

    /** Test setAvdInfo() */
    @Test
    public void testSetGceAvdInfo() throws Exception {
        
    }
}
