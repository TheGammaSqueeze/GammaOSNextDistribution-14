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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tradefed.device.cloud.NestedRemoteDevice;
import com.android.tradefed.util.IRunUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit Tests for {@link ManagedTestDeviceFactory} */
@RunWith(JUnit4.class)
public class ManagedTestDeviceFactoryTest {

    private ManagedTestDeviceFactory mFactory;
    @Mock IDeviceManager mMockDeviceManager;
    @Mock IDeviceMonitor mMockDeviceMonitor;
    @Mock IRunUtil mMockRunUtil;
    @Mock IDevice mMockIDevice;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mFactory = new ManagedTestDeviceFactory(true, mMockDeviceManager, mMockDeviceMonitor) ;
    }

    @Test
    public void testIsSerialTcpDevice() {
        String input = "127.0.0.1:5555";
        assertTrue(ManagedTestDeviceFactory.isTcpDeviceSerial(input));
    }

    @Test
    public void testIsSerialTcpDevice_localhost() {
        String input = "localhost:54014";
        assertTrue(ManagedTestDeviceFactory.isTcpDeviceSerial(input));
    }

    @Test
    public void testIsSerialTcpDevice_notTcp() {
        String input = "00bf84d7d084cc84";
        assertFalse(ManagedTestDeviceFactory.isTcpDeviceSerial(input));
    }

    @Test
    public void testIsSerialTcpDevice_malformedPort() {
        String input = "127.0.0.1:999989";
        assertFalse(ManagedTestDeviceFactory.isTcpDeviceSerial(input));
    }

    @Test
    public void testIsSerialTcpDevice_nohost() {
        String input = ":5555";
        assertFalse(ManagedTestDeviceFactory.isTcpDeviceSerial(input));
    }

    @Test
    public void testNestedDevice() throws Exception {
        when(mMockDeviceManager.isFileSystemMountCheckEnabled()).thenReturn(false);
        when(mMockDeviceManager.getFastbootPath()).thenReturn("fastboot");
        mFactory =
                new ManagedTestDeviceFactory(true, mMockDeviceManager, mMockDeviceMonitor) {
                    @Override
                    protected boolean isRemoteEnvironment() {
                        return true;
                    }
                };
        when(mMockIDevice.getSerialNumber()).thenReturn("127.0.0.1:6520");
        when(mMockIDevice.getState()).thenReturn(DeviceState.ONLINE);

        IManagedTestDevice result = mFactory.createDevice(mMockIDevice);
        assertTrue(result instanceof NestedRemoteDevice);
    }
}
