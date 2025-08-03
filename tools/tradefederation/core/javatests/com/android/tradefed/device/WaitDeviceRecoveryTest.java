/*
 * Copyright (C) 2010 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.android.ddmlib.IDevice;
import com.android.helper.aoa.UsbDevice;
import com.android.helper.aoa.UsbException;
import com.android.helper.aoa.UsbHelper;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import com.google.common.util.concurrent.SettableFuture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Unit tests for {@link WaitDeviceRecovery}. */
@RunWith(JUnit4.class)
public class WaitDeviceRecoveryTest {

    @Mock IRunUtil mMockRunUtil;
    private WaitDeviceRecovery mRecovery;
    @Mock IDeviceStateMonitor mMockMonitor;
    @Mock IDevice mMockDevice;
    private UsbHelper mMockUsbHelper;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mMockUsbHelper = Mockito.mock(UsbHelper.class);
        mRecovery =
                new WaitDeviceRecovery() {
                    @Override
                    protected IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    UsbHelper getUsbHelper() {
                        return mMockUsbHelper;
                    }
                };

        when(mMockMonitor.getSerialNumber()).thenReturn("serial");
    }

    /**
     * Test {@link WaitDeviceRecovery#recoverDevice(IDeviceStateMonitor, boolean)} when devices
     * comes back online on its own accord.
     */
    @Test
    public void testRecoverDevice_success() throws DeviceNotAvailableException {
        // expect initial sleep

        when(mMockMonitor.getDeviceState()).thenReturn(TestDeviceState.NOT_AVAILABLE);
        when(mMockMonitor.waitForDeviceOnline(Mockito.anyLong())).thenReturn(mMockDevice);
        when(mMockMonitor.waitForDeviceShell(Mockito.anyLong())).thenReturn(true);
        when(mMockMonitor.waitForDeviceAvailable(Mockito.anyLong())).thenReturn(mMockDevice);
        when(mMockMonitor.waitForDeviceOnline(Mockito.anyLong())).thenReturn(mMockDevice);

        mRecovery.recoverDevice(mMockMonitor, false);

        verify(mMockRunUtil).sleep(Mockito.anyLong());
        verify(mMockMonitor).waitForDeviceBootloaderStateUpdate();
    }

    /**
     * Test {@link WaitDeviceRecovery#recoverDevice(IDeviceStateMonitor, boolean)} when device is
     * not available.
     */
    @Test
    public void testRecoverDevice_unavailable() {
        // expect initial sleep

        when(mMockMonitor.getDeviceState()).thenReturn(TestDeviceState.NOT_AVAILABLE);
        when(mMockMonitor.waitForDeviceOnline(Mockito.anyLong())).thenReturn(null);

        try {
            mRecovery.recoverDevice(mMockMonitor, false);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }

        verify(mMockRunUtil).sleep(Mockito.anyLong());
        verify(mMockMonitor).waitForDeviceBootloaderStateUpdate();
    }

    @Test
    public void testRecoverDevice_unavailable_recovers() throws Exception {
        // expect initial sleep

        when(mMockMonitor.getDeviceState()).thenReturn(TestDeviceState.NOT_AVAILABLE);
        when(mMockMonitor.waitForDeviceOnline(Mockito.anyLong())).thenReturn(null);

        UsbDevice mockDevice = Mockito.mock(UsbDevice.class);
        doReturn(mockDevice).when(mMockUsbHelper).getDevice("serial");
        when(mMockMonitor.waitForDeviceAvailable()).thenReturn(mMockDevice);
        when(mMockMonitor.waitForDeviceOnline(Mockito.anyLong())).thenReturn(mMockDevice);

        // Device recovers successfully
        mRecovery.recoverDevice(mMockMonitor, false);

        verify(mMockRunUtil).sleep(Mockito.anyLong());
        verify(mMockMonitor).waitForDeviceBootloaderStateUpdate();

        verify(mockDevice).reset();
    }

    @Test
    public void testRecoverDevice_unavailable_recovery_fail() throws Exception {
        when(mMockMonitor.getDeviceState()).thenReturn(TestDeviceState.RECOVERY);
        when(mMockMonitor.waitForDeviceOnline(Mockito.anyLong())).thenReturn(null);

        try {
            mRecovery.recoverDevice(mMockMonitor, false);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
        verify(mMockMonitor, times(2)).getDeviceState();
        verify(mMockRunUtil).sleep(Mockito.anyLong());
        verify(mMockMonitor).waitForDeviceBootloaderStateUpdate();
    }

    @Test
    public void testRecoverDevice_unavailable_fastboot() throws Exception {
        // expect initial sleep

        when(mMockMonitor.getDeviceState()).thenReturn(TestDeviceState.FASTBOOT);
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        // expect reboot
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("fastboot"),
                        Mockito.eq("-s"),
                        Mockito.eq("serial"),
                        Mockito.eq("reboot")))
                .thenReturn(result);

        when(mMockMonitor.getFastbootSerialNumber()).thenReturn("serial");
        when(mMockMonitor.waitForDeviceOnline(Mockito.anyLong())).thenReturn(null);

        try {
            mRecovery.recoverDevice(mMockMonitor, false);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }

        verify(mMockRunUtil).sleep(Mockito.anyLong());
        verify(mMockMonitor).waitForDeviceBootloaderStateUpdate();
    }

    /**
     * Test {@link WaitDeviceRecovery#recoverDevice(IDeviceStateMonitor, boolean)} when device is
     * not responsive.
     */
    @Test
    public void testRecoverDevice_unresponsive() throws Exception {
        // expect initial sleep

        when(mMockMonitor.getDeviceState()).thenReturn(TestDeviceState.ONLINE);
        when(mMockMonitor.waitForDeviceOnline(Mockito.anyLong())).thenReturn(mMockDevice);
        when(mMockMonitor.waitForDeviceShell(Mockito.anyLong())).thenReturn(true);
        when(mMockMonitor.waitForDeviceAvailable(Mockito.anyLong())).thenReturn(null);

        try {
            mRecovery.recoverDevice(mMockMonitor, false);
            fail("DeviceUnresponsiveException not thrown");
        } catch (DeviceUnresponsiveException e) {
            // expected
        }

        verify(mMockRunUtil).sleep(Mockito.anyLong());
        verify(mMockMonitor).waitForDeviceBootloaderStateUpdate();
        verify(mMockDevice).reboot((String) Mockito.isNull());
    }

    /**
     * Test {@link WaitDeviceRecovery#recoverDevice(IDeviceStateMonitor, boolean)} when USB cannot
     * be reset.
     */
    @Test
    public void testRecoverDevice_unavailable_usb_exception() throws Exception {
        when(mMockMonitor.getDeviceState()).thenReturn(TestDeviceState.ONLINE);
        when(mMockMonitor.waitForDeviceOnline(Mockito.anyLong())).thenReturn(null);
        when(mMockUsbHelper.getDevice(Mockito.anyString())).thenThrow(new UsbException("test"));

        try {
            mRecovery.recoverDevice(mMockMonitor, false);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
        verify(mMockRunUtil).sleep(Mockito.anyLong());
        verify(mMockMonitor).waitForDeviceBootloaderStateUpdate();
        verify(mMockUsbHelper).getDevice(Mockito.anyString());
    }

    /**
     * Test {@link WaitDeviceRecovery#recoverDevice(IDeviceStateMonitor, boolean)} when device is in
     * fastboot.
     */
    @Test
    public void testRecoverDevice_fastboot() throws DeviceNotAvailableException {
        // expect initial sleep

        when(mMockMonitor.getDeviceState()).thenReturn(TestDeviceState.FASTBOOT);
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        // expect reboot
        when(mMockMonitor.getFastbootSerialNumber()).thenReturn("serial");
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("fastboot"),
                        Mockito.eq("-s"),
                        Mockito.eq("serial"),
                        Mockito.eq("reboot")))
                .thenReturn(result);
        when(mMockMonitor.waitForDeviceOnline(Mockito.anyLong())).thenReturn(mMockDevice);
        when(mMockMonitor.waitForDeviceShell(Mockito.anyLong())).thenReturn(true);
        when(mMockMonitor.waitForDeviceAvailable(Mockito.anyLong())).thenReturn(mMockDevice);
        when(mMockMonitor.waitForDeviceOnline(Mockito.anyLong())).thenReturn(mMockDevice);

        mRecovery.recoverDevice(mMockMonitor, false);

        verify(mMockRunUtil).sleep(Mockito.anyLong());
        verify(mMockMonitor).waitForDeviceBootloaderStateUpdate();
    }

    /**
     * Test {@link WaitDeviceRecovery#recoverDeviceBootloader(IDeviceStateMonitor)} when device is
     * already in bootloader
     */
    @Test
    public void testRecoverDeviceBootloader_fastboot() throws DeviceNotAvailableException {

        // expect reboot
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("fastboot"),
                        Mockito.eq("-s"),
                        Mockito.eq("serial"),
                        Mockito.eq("reboot-bootloader")))
                .thenReturn(new CommandResult(CommandStatus.SUCCESS));
        when(mMockMonitor.waitForDeviceNotAvailable(Mockito.anyLong())).thenReturn(Boolean.TRUE);
        when(mMockMonitor.waitForDeviceBootloader(Mockito.anyLong())).thenReturn(Boolean.TRUE);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("fastboot"),
                        Mockito.eq("-s"),
                        Mockito.eq("serial"),
                        Mockito.eq("getvar"),
                        Mockito.eq("product")))
                .thenReturn(new CommandResult(CommandStatus.SUCCESS));

        mRecovery.recoverDeviceBootloader(mMockMonitor);
        verify(mMockMonitor, times(2)).waitForDeviceBootloader(Mockito.anyLong());
        verify(mMockRunUtil).sleep(Mockito.anyLong());
    }

    /**
     * Test {@link WaitDeviceRecovery#recoverDeviceBootloader(IDeviceStateMonitor)} when device is
     * unavailable but comes back to bootloader on its own
     */
    @Test
    public void testRecoverDeviceBootloader_unavailable() throws DeviceNotAvailableException {

        when(mMockMonitor.waitForDeviceBootloader(Mockito.anyLong())).thenReturn(Boolean.FALSE);
        when(mMockMonitor.getDeviceState()).thenReturn(TestDeviceState.NOT_AVAILABLE);
        // expect reboot
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("fastboot"),
                        Mockito.eq("-s"),
                        Mockito.eq("serial"),
                        Mockito.eq("reboot-bootloader")))
                .thenReturn(new CommandResult(CommandStatus.SUCCESS));
        when(mMockMonitor.waitForDeviceNotAvailable(Mockito.anyLong())).thenReturn(Boolean.TRUE);
        when(mMockMonitor.waitForDeviceBootloader(Mockito.anyLong())).thenReturn(Boolean.TRUE);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("fastboot"),
                        Mockito.eq("-s"),
                        Mockito.eq("serial"),
                        Mockito.eq("getvar"),
                        Mockito.eq("product")))
                .thenReturn(new CommandResult(CommandStatus.SUCCESS));

        mRecovery.recoverDeviceBootloader(mMockMonitor);
        verify(mMockMonitor, times(2)).waitForDeviceBootloader(Mockito.anyLong());
        verify(mMockRunUtil).sleep(Mockito.anyLong());
    }

    /**
     * Test {@link WaitDeviceRecovery#recoverDeviceBootloader(IDeviceStateMonitor)} when device is
     * online when bootloader is expected
     */
    @Test
    public void testRecoverDeviceBootloader_online() throws Exception {

        when(mMockMonitor.waitForDeviceBootloader(Mockito.anyLong()))
                .thenReturn(Boolean.FALSE, Boolean.TRUE);
        when(mMockMonitor.getDeviceState()).thenReturn(TestDeviceState.ONLINE);
        when(mMockMonitor.waitForDeviceOnline(Mockito.anyLong())).thenReturn(mMockDevice);

        mRecovery.recoverDeviceBootloader(mMockMonitor);

        verify(mMockRunUtil).sleep(Mockito.anyLong());
        verify(mMockDevice).reboot("bootloader");
    }

    /**
     * Test {@link WaitDeviceRecovery#recoverDeviceBootloader(IDeviceStateMonitor)} when device is
     * initially unavailable, then comes online when bootloader is expected
     */
    @Test
    public void testRecoverDeviceBootloader_unavailable_online() throws Exception {

        when(mMockMonitor.waitForDeviceBootloader(Mockito.anyLong()))
                .thenReturn(Boolean.FALSE, Boolean.FALSE, Boolean.TRUE);
        when(mMockMonitor.getDeviceState())
                .thenReturn(TestDeviceState.NOT_AVAILABLE, TestDeviceState.ONLINE);
        when(mMockMonitor.waitForDeviceOnline(Mockito.anyLong())).thenReturn(mMockDevice);

        mRecovery.recoverDeviceBootloader(mMockMonitor);

        verify(mMockRunUtil).sleep(Mockito.anyLong());
        verify(mMockDevice).reboot("bootloader");
    }

    /**
     * Test {@link WaitDeviceRecovery#recoverDeviceBootloader(IDeviceStateMonitor)} when device is
     * unavailable
     */
    @Test
    public void testRecoverDeviceBootloader_unavailable_failure() throws Exception {

        when(mMockMonitor.waitForDeviceBootloader(Mockito.anyLong())).thenReturn(Boolean.FALSE);
        when(mMockMonitor.getDeviceState()).thenReturn(TestDeviceState.NOT_AVAILABLE);

        try {
            mRecovery.recoverDeviceBootloader(mMockMonitor);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }

        verify(mMockRunUtil).sleep(Mockito.anyLong());
    }

    /**
     * Test {@link WaitDeviceRecovery#checkMinBatteryLevel(IDevice)} throws an exception if battery
     * level is not readable.
     */
    @Test
    public void testCheckMinBatteryLevel_unreadable() throws Exception {
        OptionSetter setter = new OptionSetter(mRecovery);
        setter.setOptionValue("min-battery-after-recovery", "50");
        SettableFuture<Integer> future = SettableFuture.create();
        future.set(null);
        when(mMockDevice.getBattery()).thenReturn(future);
        when(mMockDevice.getSerialNumber()).thenReturn("SERIAL");

        try {
            mRecovery.checkMinBatteryLevel(mMockDevice);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException expected) {
            assertEquals("Cannot read battery level but a min is required", expected.getMessage());
        }
    }

    /**
     * Test {@link WaitDeviceRecovery#checkMinBatteryLevel(IDevice)} throws an exception if battery
     * level is below the minimal expected.
     */
    @Test
    public void testCheckMinBatteryLevel_belowLevel() throws Exception {
        OptionSetter setter = new OptionSetter(mRecovery);
        setter.setOptionValue("min-battery-after-recovery", "50");
        SettableFuture<Integer> future = SettableFuture.create();
        future.set(49);
        when(mMockDevice.getBattery()).thenReturn(future);
        when(mMockDevice.getSerialNumber()).thenReturn("SERIAL");

        try {
            mRecovery.checkMinBatteryLevel(mMockDevice);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException expected) {
            assertEquals("After recovery, device battery level 49 is lower than required minimum "
                    + "50", expected.getMessage());
        }
    }

    /**
     * Test {@link WaitDeviceRecovery#checkMinBatteryLevel(IDevice)} returns without exception when
     * battery level after recovery is above or equals minimum expected.
     */
    @Test
    public void testCheckMinBatteryLevel() throws Exception {
        OptionSetter setter = new OptionSetter(mRecovery);
        setter.setOptionValue("min-battery-after-recovery", "50");
        SettableFuture<Integer> future = SettableFuture.create();
        future.set(50);
        when(mMockDevice.getBattery()).thenReturn(future);

        mRecovery.checkMinBatteryLevel(mMockDevice);
    }
}
