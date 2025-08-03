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
package com.android.tradefed.device.recovery;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tradefed.command.ICommandScheduler;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceManager.FastbootDevice;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.IManagedTestDevice;
import com.android.tradefed.device.ITestDevice;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

/** Unit tests for {@link RunConfigDeviceRecovery}. */
@RunWith(JUnit4.class)
public class RunConfigDeviceRecoveryTest {

    private RunConfigDeviceRecovery mRecoverer;
    private IManagedTestDevice mMockTestDevice;
    private IDevice mMockIDevice;
    private IDeviceManager mMockDeviceManager;
    private ICommandScheduler mMockScheduler;

    @Before
    public void setup() throws Exception {
        mMockTestDevice = mock(IManagedTestDevice.class);
        mMockIDevice = mock(IDevice.class);
        mMockDeviceManager = mock(IDeviceManager.class);
        mMockScheduler = mock(ICommandScheduler.class);
        when(mMockTestDevice.getSerialNumber()).thenReturn("serial");
        when(mMockTestDevice.getIDevice()).thenReturn(mMockIDevice);
        mRecoverer =
                new RunConfigDeviceRecovery() {
                    @Override
                    protected IDeviceManager getDeviceManager() {
                        return mMockDeviceManager;
                    }

                    @Override
                    protected ICommandScheduler getCommandScheduler() {
                        return mMockScheduler;
                    }
                };
        OptionSetter setter = new OptionSetter(mRecoverer);
        setter.setOptionValue("recovery-config-name", "empty");
    }

    @Test
    public void testRecoverDevice_allocated() {
        List<IManagedTestDevice> devices = new ArrayList<>();
        devices.add(mMockTestDevice);
        when(mMockTestDevice.getAllocationState()).thenReturn(DeviceAllocationState.Allocated);
        mRecoverer.recoverDevices(devices);
    }

    @Test
    public void testRecoverDevice_offline() throws Exception {
        List<IManagedTestDevice> devices = new ArrayList<>();
        devices.add(mMockTestDevice);
        when(mMockTestDevice.getAllocationState()).thenReturn(DeviceAllocationState.Available);
        ITestDevice device = mock(ITestDevice.class);
        when(mMockDeviceManager.forceAllocateDevice("serial")).thenReturn(device);

        mRecoverer.recoverDevices(devices);

        verify(mMockScheduler).execCommand(Mockito.any(), Mockito.eq(device), Mockito.any());
    }

    /** Test that FastbootDevice are considered for recovery. */
    @Test
    public void testRecoverDevice_fastboot() throws Exception {
        Mockito.reset(mMockTestDevice);
        when(mMockTestDevice.getIDevice()).thenReturn(new FastbootDevice("serial"));
        when(mMockTestDevice.getSerialNumber()).thenReturn("serial");
        List<IManagedTestDevice> devices = new ArrayList<>();
        devices.add(mMockTestDevice);
        when(mMockTestDevice.getAllocationState()).thenReturn(DeviceAllocationState.Available);
        ITestDevice device = mock(ITestDevice.class);
        when(mMockDeviceManager.forceAllocateDevice("serial")).thenReturn(device);

        mRecoverer.recoverDevices(devices);

        verify(mMockScheduler).execCommand(Mockito.any(), Mockito.eq(device), Mockito.any());
    }

    @Test
    public void testRecoverDevice_run() throws Exception {
        List<IManagedTestDevice> devices = new ArrayList<>();
        devices.add(mMockTestDevice);
        when(mMockTestDevice.getAllocationState()).thenReturn(DeviceAllocationState.Available);

        ITestDevice device = mock(ITestDevice.class);
        when(mMockDeviceManager.forceAllocateDevice("serial")).thenReturn(device);

        ArgumentCaptor<String[]> captured = ArgumentCaptor.forClass(String[].class);

        mRecoverer.recoverDevices(devices);

        verify(mMockScheduler).execCommand(Mockito.any(), Mockito.eq(device), captured.capture());

        String[] args = captured.getValue();
        assertEquals("empty", args[0]);
    }
}
