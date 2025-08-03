/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tradefed.device;

import static org.junit.Assert.assertNull;

import com.android.ddmlib.IDevice;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link RemoteAndroidDevice}. */
@RunWith(JUnit4.class)
public class RemoteAndroidDeviceTest {

    @Mock IDevice mMockIDevice;
    @Mock IDeviceStateMonitor mMockStateMonitor;
    @Mock IDeviceMonitor mMockDvcMonitor;
    @Mock IDeviceRecovery mMockRecovery;
    private RemoteAndroidDevice mTestDevice;

    /**
     * A {@link TestDevice} that is suitable for running tests against
     */
    private class TestableRemoteAndroidDevice extends RemoteAndroidDevice {
        public TestableRemoteAndroidDevice() {
            super(mMockIDevice, mMockStateMonitor, mMockDvcMonitor);
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTestDevice = new TestableRemoteAndroidDevice();
    }

    @Test
    public void testGetMacAddress() {
        assertNull(mTestDevice.getMacAddress());
    }
}
