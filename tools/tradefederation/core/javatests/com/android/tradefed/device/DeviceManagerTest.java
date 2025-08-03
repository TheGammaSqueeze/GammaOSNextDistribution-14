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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.config.IGlobalConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.IManagedTestDevice.DeviceEventResponse;
import com.android.tradefed.host.HostOptions;
import com.android.tradefed.host.IHostOptions;
import com.android.tradefed.log.ILogRegistry.EventType;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.ZipUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link DeviceManager}. */
@RunWith(JUnit4.class)
public class DeviceManagerTest {

    private static final String DEVICE_SERIAL = "serial";
    private static final String MAC_ADDRESS = "FF:FF:FF:FF:FF:FF";
    private static final String SIM_STATE = "READY";
    private static final String SIM_OPERATOR = "operator";

    @Mock IAndroidDebugBridge mMockAdbBridge;
    @Mock IDevice mMockIDevice;
    @Mock IDeviceStateMonitor mMockStateMonitor;
    @Mock IRunUtil mMockRunUtil;
    @Mock IHostOptions mMockHostOptions;
    @Mock IManagedTestDevice mMockTestDevice;
    private IManagedTestDeviceFactory mMockDeviceFactory;
    @Mock IGlobalConfiguration mMockGlobalConfig;
    private DeviceSelectionOptions mDeviceSelections;

    /**
     * a reference to the DeviceManager's IDeviceChangeListener. Used for triggering device
     * connection events
     */
    private IDeviceChangeListener mDeviceListener;

    static class MockProcess extends Process {
        /** {@inheritDoc} */
        @Override
        public void destroy() {
            // ignore
        }

        /** {@inheritDoc} */
        @Override
        public int exitValue() {
            return 0;
        }

        /** {@inheritDoc} */
        @Override
        public InputStream getErrorStream() {
            return null;
        }

        /** {@inheritDoc} */
        @Override
        public InputStream getInputStream() {
            return null;
        }

        /** {@inheritDoc} */
        @Override
        public OutputStream getOutputStream() {
            return null;
        }

        /** {@inheritDoc} */
        @Override
        public int waitFor() throws InterruptedException {
            return 0;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        doAnswer(
                        invocation -> {
                            Object arg0 = invocation.getArgument(0);
                            mDeviceListener = (IDeviceChangeListener) arg0;
                            return null;
                        })
                .when(mMockAdbBridge)
                .addDeviceChangeListener((IDeviceChangeListener) Mockito.any());

        mMockDeviceFactory =
                new ManagedTestDeviceFactory(false, null, null) {
                    @Override
                    public IManagedTestDevice createDevice(IDevice idevice) {
                        mMockTestDevice.setIDevice(idevice);
                        return mMockTestDevice;
                    }

                    @Override
                    protected CollectingOutputReceiver createOutputReceiver() {
                        return new CollectingOutputReceiver() {
                            @Override
                            public String getOutput() {
                                return "/system/bin/pm";
                            }
                        };
                    }

                    @Override
                    public void setFastbootEnabled(boolean enable) {
                        // ignore
                    }
                };

        when(mMockGlobalConfig.getHostOptions()).thenReturn(new HostOptions());

        when(mMockIDevice.getSerialNumber()).thenReturn(DEVICE_SERIAL);
        when(mMockStateMonitor.getSerialNumber()).thenReturn(DEVICE_SERIAL);

        when(mMockIDevice.isEmulator()).thenReturn(Boolean.FALSE);
        when(mMockTestDevice.getMacAddress()).thenReturn(MAC_ADDRESS);
        when(mMockTestDevice.getSimState()).thenReturn(SIM_STATE);
        when(mMockTestDevice.getSimOperator()).thenReturn(SIM_OPERATOR);
        final ArgumentCaptor<IDevice> capturedIDevice = ArgumentCaptor.forClass(IDevice.class);
        doNothing().when(mMockTestDevice).setIDevice(capturedIDevice.capture());

        when(mMockTestDevice.getIDevice())
                .thenAnswer(
                        invocation -> {
                            return capturedIDevice.getValue();
                        });
        when(mMockTestDevice.getSerialNumber())
                .thenAnswer(
                        invocation -> {
                            return capturedIDevice.getValue().getSerialNumber();
                        });
        when(mMockTestDevice.getMonitor()).thenReturn(mMockStateMonitor);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(), (String) Mockito.any(), (String) Mockito.any()))
                .thenReturn(new CommandResult());
        when(mMockRunUtil.runTimedCmdSilently(
                        Mockito.anyLong(), (String) Mockito.any(), (String) Mockito.any()))
                .thenReturn(new CommandResult());
        // Avoid any issue related to env. variable.
        mDeviceSelections =
                new DeviceSelectionOptions() {
                    @Override
                    public String fetchEnvironmentVariable(String name) {
                        return null;
                    }
                };
        when(mMockGlobalConfig.getDeviceRequirements()).thenReturn(mDeviceSelections);
    }

    private DeviceManager createDeviceManager(
            List<IDeviceMonitor> deviceMonitors, IDevice... devices) {
        DeviceManager mgr = createDeviceManagerNoInit();
        mgr.init(null, deviceMonitors, mMockDeviceFactory);
        for (IDevice device : devices) {
            mDeviceListener.deviceConnected(device);
        }
        return mgr;
    }

    private DeviceManager createDeviceManagerNoInit() {

        DeviceManager mgr =
                new DeviceManager() {
                    @Override
                    IAndroidDebugBridge createAdbBridge() {
                        return mMockAdbBridge;
                    }

                    @Override
                    void startFastbootMonitor() {}

                    @Override
                    void startDeviceRecoverer() {}

                    @Override
                    void logDeviceEvent(EventType event, String serial) {}

                    @Override
                    IDeviceStateMonitor createStateMonitor(IDevice device) {
                        return mMockStateMonitor;
                    }

                    @Override
                    IGlobalConfiguration getGlobalConfig() {
                        return mMockGlobalConfig;
                    }

                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    IHostOptions getHostOptions() {
                        return mMockHostOptions;
                    }
                };
        mgr.setSynchronousMode(true);
        mgr.setMaxEmulators(0);
        mgr.setMaxNullDevices(0);
        mgr.setMaxTcpDevices(0);
        mgr.setMaxGceDevices(0);
        mgr.setMaxRemoteDevices(0);
        return mgr;
    }

    /**
     * Test @link DeviceManager#allocateDevice()} when a IDevice is present on DeviceManager
     * creation.
     */
    @Test
    public void testAllocateDevice() {
        setCheckAvailableDeviceExpectations();
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));

        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        assertNotNull(manager.allocateDevice(mDeviceSelections));
    }

    /**
     * Test {@link DeviceManager#allocateDevice(IDeviceSelection, boolean)} when device is returned.
     */
    @Test
    public void testAllocateDevice_match() {
        mDeviceSelections.addSerial(DEVICE_SERIAL);
        setCheckAvailableDeviceExpectations();
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.EXPLICIT_ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));

        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        assertEquals(mMockTestDevice, manager.allocateDevice(mDeviceSelections, false));
    }

    /**
     * Test that when allocating a fake device we create a placeholder then delete it at the end.
     */
    @Test
    public void testAllocateDevice_match_temporary() {
        mDeviceSelections.setNullDeviceRequested(true);
        mDeviceSelections.addSerial(DEVICE_SERIAL);
        // Force create a device
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.FORCE_AVAILABLE))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Available, true));
        // Device get allocated
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.EXPLICIT_ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));

        mMockTestDevice.stopLogcat();

        // De-allocate
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.FREE_UNKNOWN))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Unknown, true));

        DeviceManager manager = createDeviceManager(null);
        assertEquals(mMockTestDevice, manager.allocateDevice(mDeviceSelections, true));
        String serial = mMockTestDevice.getSerialNumber();
        assertTrue(serial.startsWith("null-device-temp-"));

        // Release device
        manager.freeDevice(mMockTestDevice, FreeDeviceState.AVAILABLE);
        // Check that temp device was deleted.
        DeviceSelectionOptions validation = new DeviceSelectionOptions();
        validation.setNullDeviceRequested(true);
        validation.addSerial(serial);
        // If we request the particular null-device again it doesn't exists.
        assertNull(manager.allocateDevice(validation, false));
    }

    /**
     * Test {@link DeviceManager#allocateDevice(IDeviceSelection, boolean)} when stub emulator is
     * requested.
     */
    @Test
    public void testAllocateDevice_stubEmulator() {
        mDeviceSelections.setStubEmulatorRequested(true);
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.FORCE_AVAILABLE))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Available, true));
        when(mMockIDevice.isEmulator()).thenReturn(Boolean.TRUE);
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));

        DeviceManager mgr = createDeviceManagerNoInit();
        mgr.setMaxEmulators(1);
        mgr.init(null, null, mMockDeviceFactory);
        assertNotNull(mgr.allocateDevice(mDeviceSelections, false));
    }

    /** Test that when a zipped fastboot file is provided we unpack it and use it. */
    @Test
    public void testUnpackZippedFastboot() throws Exception {
        File tmpDir = FileUtil.createTempDir("fake-fastbootdir");
        File fastboot = new File(tmpDir, "fastboot");
        FileUtil.writeToFile("TEST", fastboot);
        File zipDir = ZipUtil.createZip(tmpDir);

        DeviceManager mgr = createDeviceManagerNoInit();
        try {
            OptionSetter setter = new OptionSetter(mgr);
            setter.setOptionValue("fastboot-path", zipDir.getAbsolutePath());
            mgr.init(null, null, mMockDeviceFactory);
            assertTrue(mgr.getFastbootPath().contains("fastboot"));
            assertEquals("TEST", FileUtil.readStringFromFile(new File(mgr.getFastbootPath())));
        } finally {
            FileUtil.recursiveDelete(tmpDir);
            FileUtil.deleteFile(zipDir);
            mgr.terminate();
        }
    }

    /** Test freeing an emulator */
    @Test
    public void testFreeDevice_emulator() throws DeviceNotAvailableException {
        mDeviceSelections.setStubEmulatorRequested(true);
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.FORCE_AVAILABLE))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Available, true));
        when(mMockIDevice.isEmulator()).thenReturn(Boolean.TRUE);
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));

        when(mMockTestDevice.executeAdbCommand("emu", "kill")).thenReturn("");
        when(mMockTestDevice.getEmulatorProcess()).thenReturn(new MockProcess());
        when(mMockTestDevice.waitForDeviceNotAvailable(Mockito.anyLong())).thenReturn(Boolean.TRUE);
        when(mMockTestDevice.waitForDeviceNotAvailable(Mockito.anyLong())).thenReturn(Boolean.TRUE);
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.FREE_AVAILABLE))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Available, true));
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));

        DeviceManager manager = createDeviceManagerNoInit();
        manager.setMaxEmulators(1);
        manager.init(null, null, mMockDeviceFactory);
        IManagedTestDevice emulator =
                (IManagedTestDevice) manager.allocateDevice(mDeviceSelections, false);
        assertNotNull(emulator);
        // a freed 'unavailable' emulator should be returned to the available
        // queue.
        manager.freeDevice(emulator, FreeDeviceState.UNAVAILABLE);
        // ensure device can be allocated again
        assertNotNull(manager.allocateDevice(mDeviceSelections, false));

        verify(mMockTestDevice).stopLogcat();
        verify(mMockTestDevice).stopEmulatorOutput();
    }

    /**
     * Test {@link DeviceManager#allocateDevice(IDeviceSelection, boolean)} when a null device is
     * requested.
     */
    @Test
    public void testAllocateDevice_nullDevice() {
        mDeviceSelections.setNullDeviceRequested(true);
        when(mMockIDevice.isEmulator()).thenReturn(Boolean.FALSE);
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.FORCE_AVAILABLE))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Available, true));
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));

        DeviceManager mgr = createDeviceManagerNoInit();
        mgr.setMaxNullDevices(1);
        mgr.init(null, null, mMockDeviceFactory);
        ITestDevice device = mgr.allocateDevice(mDeviceSelections, false);
        assertNotNull(device);
        assertTrue(device.getIDevice() instanceof NullDevice);
    }

    /** Test {@link DeviceManager#forceAllocateDevice(String)} when device is unknown */
    @Test
    public void testForceAllocateDevice() {

        DeviceManager manager = createDeviceManager(null);
        assertNull(manager.forceAllocateDevice("unknownserial"));
    }

    /** Test {@link DeviceManager#forceAllocateDevice(String)} when device is available */
    @Test
    public void testForceAllocateDevice_available() {
        setCheckAvailableDeviceExpectations();
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.FORCE_ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));

        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        assertNotNull(manager.forceAllocateDevice(DEVICE_SERIAL));
    }

    /** Test {@link DeviceManager#forceAllocateDevice(String)} when device is already allocated */
    @Test
    public void testForceAllocateDevice_alreadyAllocated() {
        setCheckAvailableDeviceExpectations();
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.FORCE_ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, false));

        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        assertNotNull(manager.allocateDevice(mDeviceSelections));
        assertNull(manager.forceAllocateDevice(DEVICE_SERIAL));
    }

    /** Test method for {@link DeviceManager#freeDevice(ITestDevice, FreeDeviceState)}. */
    @Test
    public void testFreeDevice() {
        setCheckAvailableDeviceExpectations();
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.FREE_AVAILABLE))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Available, true));

        DeviceManager manager = createDeviceManager(null);
        mDeviceListener.deviceConnected(mMockIDevice);
        assertNotNull(manager.allocateDevice(mDeviceSelections));
        manager.freeDevice(mMockTestDevice, FreeDeviceState.AVAILABLE);

        verify(mMockTestDevice).stopLogcat();
    }

    /**
     * Verified that {@link DeviceManager#freeDevice(ITestDevice, FreeDeviceState)} ignores a call
     * with a device that has not been allocated.
     */
    @Test
    public void testFreeDevice_noop() {
        setCheckAvailableDeviceExpectations();
        IManagedTestDevice testDevice = mock(IManagedTestDevice.class);
        IDevice mockIDevice = mock(IDevice.class);
        when(testDevice.getIDevice()).thenReturn(mockIDevice);
        when(mockIDevice.isEmulator()).thenReturn(Boolean.FALSE);

        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        manager.freeDevice(testDevice, FreeDeviceState.AVAILABLE);
    }

    /**
     * Verified that {@link DeviceManager} calls {@link IManagedTestDevice#setIDevice(IDevice)} when
     * DDMS allocates a new IDevice on connection.
     */
    @Test
    public void testSetIDevice() {
        setCheckAvailableDeviceExpectations();
        when(mMockTestDevice.getAllocationState()).thenReturn(DeviceAllocationState.Available);
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.DISCONNECTED))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, false));
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.CONNECTED_ONLINE))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, false));
        IDevice newMockDevice = mock(IDevice.class);
        when(newMockDevice.getSerialNumber()).thenReturn(DEVICE_SERIAL);
        when(newMockDevice.getState()).thenReturn(DeviceState.ONLINE);

        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        ITestDevice device = manager.allocateDevice(mDeviceSelections);
        assertNotNull(device);
        // now trigger a device disconnect + reconnection
        mDeviceListener.deviceDisconnected(mMockIDevice);
        mDeviceListener.deviceConnected(newMockDevice);
        assertEquals(newMockDevice, device.getIDevice());

        verify(mMockTestDevice, times(1)).setDeviceState(TestDeviceState.NOT_AVAILABLE);
        verify(mMockTestDevice, times(2)).setDeviceState(TestDeviceState.ONLINE);
    }

    /**
     * Test {@link DeviceManager#allocateDevice()} when {@link DeviceManager#init()} has not been
     * called.
     */
    @Test
    public void testAllocateDevice_noInit() {
        try {
            createDeviceManagerNoInit().allocateDevice(mDeviceSelections);
            fail("IllegalStateException not thrown when manager has not been initialized");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    /** Test {@link DeviceManager#init(IDeviceSelection, List)} with a global exclusion filter */
    @Test
    public void testInit_excludeDevice() throws Exception {
        when(mMockIDevice.getState()).thenReturn(DeviceState.ONLINE);

        DeviceEventResponse der =
                new DeviceEventResponse(DeviceAllocationState.Checking_Availability, true);
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.CONNECTED_ONLINE)).thenReturn(der);
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.AVAILABLE_CHECK_IGNORED))
                .thenReturn(null);
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Ignored, false));

        DeviceManager manager = createDeviceManagerNoInit();
        DeviceSelectionOptions excludeFilter = new DeviceSelectionOptions();
        excludeFilter.addExcludeSerial(mMockIDevice.getSerialNumber());
        manager.init(excludeFilter, null, mMockDeviceFactory);
        mDeviceListener.deviceConnected(mMockIDevice);
        assertEquals(1, manager.getDeviceList().size());
        assertNull(manager.allocateDevice(mDeviceSelections));
        verify(mMockTestDevice, times(1)).setDeviceState(TestDeviceState.ONLINE);
    }

    /** Test {@link DeviceManager#init(IDeviceSelection, List)} with a global inclusion filter */
    @Test
    public void testInit_includeDevice() throws Exception {
        IDevice excludedDevice = mock(IDevice.class);
        when(excludedDevice.getSerialNumber()).thenReturn("excluded");
        when(excludedDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockIDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(excludedDevice.isEmulator()).thenReturn(Boolean.FALSE);

        DeviceEventResponse der =
                new DeviceEventResponse(DeviceAllocationState.Checking_Availability, true);
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.CONNECTED_ONLINE)).thenReturn(der);
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.AVAILABLE_CHECK_IGNORED))
                .thenReturn(null);
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Ignored, false));

        DeviceManager manager = createDeviceManagerNoInit();
        mDeviceSelections.addSerial(mMockIDevice.getSerialNumber());
        manager.init(mDeviceSelections, null, mMockDeviceFactory);
        mDeviceListener.deviceConnected(excludedDevice);
        assertEquals(1, manager.getDeviceList().size());
        // ensure excludedDevice cannot be allocated
        assertNull(manager.allocateDevice());

        verify(mMockTestDevice).setDeviceState(TestDeviceState.ONLINE);
    }

    /** Verified that a disconnected device state gets updated */
    @Test
    public void testSetState_disconnected() {
        setCheckAvailableDeviceExpectations();
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.DISCONNECTED))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, false));

        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        assertEquals(mMockTestDevice, manager.allocateDevice(mDeviceSelections));
        mDeviceListener.deviceDisconnected(mMockIDevice);

        verify(mMockTestDevice).setDeviceState(TestDeviceState.NOT_AVAILABLE);
    }

    /** Verified that a offline device state gets updated */
    @Test
    public void testSetState_offline() {
        setCheckAvailableDeviceExpectations();
        when(mMockTestDevice.getAllocationState()).thenReturn(DeviceAllocationState.Allocated);
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));

        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.STATE_CHANGE_OFFLINE))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Unavailable, true));

        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        assertEquals(mMockTestDevice, manager.allocateDevice(mDeviceSelections));
        IDevice newDevice = mock(IDevice.class);
        when(newDevice.getSerialNumber()).thenReturn(DEVICE_SERIAL);
        when(newDevice.getState()).thenReturn(DeviceState.OFFLINE);

        mDeviceListener.deviceChanged(newDevice, IDevice.CHANGE_STATE);

        verify(mMockTestDevice).setDeviceState(TestDeviceState.NOT_AVAILABLE);

        verify(newDevice, times(2)).getState();
    }

    // TODO: add test for fastboot state changes

    /** Test normal success case for {@link DeviceManager#connectToTcpDevice(String)} */
    @Test
    public void testConnectToTcpDevice() throws Exception {
        final String ipAndPort = "ip:5555";
        setConnectToTcpDeviceExpectations(ipAndPort);

        DeviceManager manager = createDeviceManager(null);
        IManagedTestDevice device = (IManagedTestDevice) manager.connectToTcpDevice(ipAndPort);
        assertNotNull(device);

        verify(mMockTestDevice).waitForDeviceOnline();
    }

    /**
     * Test a {@link DeviceManager#connectToTcpDevice(String)} call where device is already
     * allocated
     */
    @Test
    public void testConnectToTcpDevice_alreadyAllocated() throws Exception {
        final String ipAndPort = "ip:5555";
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.FORCE_ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, false));
        CommandResult connectResult = new CommandResult(CommandStatus.SUCCESS);
        connectResult.setStdout(String.format("connected to %s", ipAndPort));
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("adb"),
                        Mockito.eq("connect"),
                        Mockito.eq(ipAndPort)))
                .thenReturn(connectResult);

        when(mMockTestDevice.getAllocationState()).thenReturn(DeviceAllocationState.Allocated);

        DeviceManager manager = createDeviceManager(null);
        IManagedTestDevice device = (IManagedTestDevice) manager.connectToTcpDevice(ipAndPort);
        assertNotNull(device);
        // now attempt to re-allocate
        assertNull(manager.connectToTcpDevice(ipAndPort));

        verify(mMockTestDevice).waitForDeviceOnline();
    }

    /** Test {@link DeviceManager#connectToTcpDevice(String)} where device does not appear on adb */
    @Test
    public void testConnectToTcpDevice_notOnline() throws Exception {
        final String ipAndPort = "ip:5555";
        setConnectToTcpDeviceExpectations(ipAndPort);
        doThrow(new DeviceNotAvailableException("test", "serial"))
                .when(mMockTestDevice)
                .waitForDeviceOnline();

        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.FREE_UNKNOWN))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Unknown, false));

        DeviceManager manager = createDeviceManager(null);
        assertNull(manager.connectToTcpDevice(ipAndPort));
        // verify device is not in list
        assertEquals(0, manager.getDeviceList().size());

        verify(mMockTestDevice).stopLogcat();
    }

    /** Test {@link DeviceManager#connectToTcpDevice(String)} where the 'adb connect' call fails. */
    @Test
    public void testConnectToTcpDevice_connectFailed() throws Exception {
        final String ipAndPort = "ip:5555";

        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.FORCE_ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.FREE_UNKNOWN))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Unknown, true));
        CommandResult connectResult = new CommandResult(CommandStatus.SUCCESS);
        connectResult.setStdout(String.format("failed to connect to %s", ipAndPort));
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("adb"),
                        Mockito.eq("connect"),
                        Mockito.eq(ipAndPort)))
                .thenReturn(connectResult);

        DeviceManager manager = createDeviceManager(null);
        assertNull(manager.connectToTcpDevice(ipAndPort));
        // verify device is not in list
        assertEquals(0, manager.getDeviceList().size());
        verify(mMockRunUtil, times(3)).sleep(Mockito.anyLong());
        verify(mMockRunUtil, times(3))
                .runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("adb"),
                        Mockito.eq("connect"),
                        Mockito.eq(ipAndPort));
        verify(mMockTestDevice).stopLogcat();
    }

    /** Test normal success case for {@link DeviceManager#disconnectFromTcpDevice(ITestDevice)} */
    @Test
    public void testDisconnectFromTcpDevice() throws Exception {
        final String ipAndPort = "ip:5555";
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.FREE_UNKNOWN))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Unknown, true));
        setConnectToTcpDeviceExpectations(ipAndPort);
        when(mMockTestDevice.switchToAdbUsb()).thenReturn(Boolean.TRUE);

        DeviceManager manager = createDeviceManager(null);
        assertNotNull(manager.connectToTcpDevice(ipAndPort));
        manager.disconnectFromTcpDevice(mMockTestDevice);
        // verify device is not in allocated or available list
        assertEquals(0, manager.getDeviceList().size());

        verify(mMockTestDevice).waitForDeviceOnline();
        verify(mMockTestDevice).stopLogcat();
    }

    /** Test normal success case for {@link DeviceManager#reconnectDeviceToTcp(ITestDevice)}. */
    @Test
    public void testReconnectDeviceToTcp() throws Exception {
        final String ipAndPort = "ip:5555";
        // use the mMockTestDevice as the initially connected to usb device
        setCheckAvailableDeviceExpectations();
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        when(mMockTestDevice.switchToAdbTcp()).thenReturn(ipAndPort);
        setConnectToTcpDeviceExpectations(ipAndPort);

        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        assertEquals(mMockTestDevice, manager.allocateDevice(mDeviceSelections));
        assertNotNull(manager.reconnectDeviceToTcp(mMockTestDevice));

        verify(mMockTestDevice).waitForDeviceOnline();
    }

    /**
     * Test {@link DeviceManager#reconnectDeviceToTcp(ITestDevice)} when tcp connected device does
     * not come online.
     */
    @Test
    public void testReconnectDeviceToTcp_notOnline() throws Exception {
        final String ipAndPort = "ip:5555";
        // use the mMockTestDevice as the initially connected to usb device
        setCheckAvailableDeviceExpectations();
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        when(mMockTestDevice.switchToAdbTcp()).thenReturn(ipAndPort);
        setConnectToTcpDeviceExpectations(ipAndPort);
        doThrow(new DeviceNotAvailableException("test", "serial"))
                .when(mMockTestDevice)
                .waitForDeviceOnline();
        // expect recover to be attempted on usb device

        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.FREE_UNKNOWN))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Unknown, true));

        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        assertEquals(mMockTestDevice, manager.allocateDevice(mDeviceSelections));
        assertNull(manager.reconnectDeviceToTcp(mMockTestDevice));
        // verify only usb device is in list
        assertEquals(1, manager.getDeviceList().size());

        verify(mMockTestDevice).recoverDevice();
        verify(mMockTestDevice).stopLogcat();
    }

    /** Basic test for {@link DeviceManager#sortDeviceList(List)} */
    @Test
    public void testSortDeviceList() {
        DeviceDescriptor availDevice1 = createDeviceDesc("aaa", DeviceAllocationState.Available);
        DeviceDescriptor availDevice2 = createDeviceDesc("bbb", DeviceAllocationState.Available);
        DeviceDescriptor allocatedDevice = createDeviceDesc("ccc", DeviceAllocationState.Allocated);
        List<DeviceDescriptor> deviceList =
                ArrayUtil.list(availDevice1, availDevice2, allocatedDevice);
        List<DeviceDescriptor> sortedList = DeviceManager.sortDeviceList(deviceList);
        assertEquals(allocatedDevice, sortedList.get(0));
        assertEquals(availDevice1, sortedList.get(1));
        assertEquals(availDevice2, sortedList.get(2));
    }

    /** Helper method to create a {@link DeviceDescriptor} using only serial and state. */
    private DeviceDescriptor createDeviceDesc(String serial, DeviceAllocationState state) {
        return new DeviceDescriptor(serial, false, state, null, null, null, null, null);
    }

    /**
     * Set expectations for a successful {@link DeviceManager#connectToTcpDevice(String)}
     * call.
     *
     * @param ipAndPort the ip and port of the device
     * @throws DeviceNotAvailableException
     */
    private void setConnectToTcpDeviceExpectations(final String ipAndPort)
            throws DeviceNotAvailableException {
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.FORCE_ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        mMockTestDevice.setRecovery((IDeviceRecovery) Mockito.any());
        CommandResult connectResult = new CommandResult(CommandStatus.SUCCESS);
        connectResult.setStdout(String.format("connected to %s", ipAndPort));
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("adb"),
                        Mockito.eq("connect"),
                        Mockito.eq(ipAndPort)))
                .thenReturn(connectResult);
    }

    /**
     * Configure expectations for a {@link
     * DeviceManager#checkAndAddAvailableDevice(IManagedTestDevice)} call for an online device
     */
    @SuppressWarnings("javadoc")
    private void setCheckAvailableDeviceExpectations() {
        setCheckAvailableDeviceExpectations(mMockIDevice);
    }

    private void setCheckAvailableDeviceExpectations(IDevice iDevice) {
        when(mMockIDevice.isEmulator()).thenReturn(Boolean.FALSE);
        when(iDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockStateMonitor.waitForDeviceShell(Mockito.anyLong())).thenReturn(Boolean.TRUE);
        when(mMockTestDevice.getDeviceState()).thenReturn(TestDeviceState.ONLINE);
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.CONNECTED_ONLINE))
                .thenReturn(
                        new DeviceEventResponse(DeviceAllocationState.Checking_Availability, true));
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.AVAILABLE_CHECK_PASSED))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Available, true));
    }

    /** Test freeing a tcp device, it must return to an unavailable status */
    @Test
    public void testFreeDevice_tcpDevice() {
        mDeviceSelections.setTcpDeviceRequested(true);
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.FORCE_AVAILABLE))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Available, true));
        when(mMockIDevice.isEmulator()).thenReturn(Boolean.FALSE);
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));

        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.ALLOCATE_REQUEST))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Allocated, true));
        when(mMockTestDevice.getDeviceState()).thenReturn(TestDeviceState.NOT_AVAILABLE);
        when(mMockTestDevice.handleAllocationEvent(DeviceEvent.FREE_UNKNOWN))
                .thenReturn(new DeviceEventResponse(DeviceAllocationState.Available, true));

        DeviceManager manager = createDeviceManagerNoInit();
        manager.setMaxTcpDevices(1);
        manager.init(null, null, mMockDeviceFactory);
        IManagedTestDevice tcpDevice =
                (IManagedTestDevice) manager.allocateDevice(mDeviceSelections, false);
        assertNotNull(tcpDevice);
        // a freed 'unavailable' emulator should be returned to the available
        // queue.
        manager.freeDevice(tcpDevice, FreeDeviceState.UNAVAILABLE);
        // ensure device can be allocated again
        ITestDevice tcp = manager.allocateDevice(mDeviceSelections, false);
        assertNotNull(tcp);
        assertTrue(tcp.getDeviceState() == TestDeviceState.NOT_AVAILABLE);
        verify(mMockTestDevice, times(2)).getDeviceState();
        verify(mMockTestDevice).stopLogcat();
        verify(mMockTestDevice).setDeviceState(TestDeviceState.NOT_AVAILABLE);
    }

    /**
     * Test freeing a device that was unable but showing in adb devices. Device will become
     * Unavailable but still seen by the DeviceManager.
     */
    @Test
    public void testFreeDevice_unavailable() {
        when(mMockIDevice.isEmulator()).thenReturn(Boolean.FALSE);
        when(mMockIDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockStateMonitor.waitForDeviceShell(Mockito.anyLong())).thenReturn(Boolean.TRUE);

        CommandResult stubAdbDevices = new CommandResult(CommandStatus.SUCCESS);
        stubAdbDevices.setStdout("List of devices attached\nserial\toffline\n");
        when(mMockRunUtil.runTimedCmd(Mockito.anyLong(), Mockito.eq("adb"), Mockito.eq("devices")))
                .thenReturn(stubAdbDevices);

        IManagedTestDevice testDevice = new TestDevice(mMockIDevice, mMockStateMonitor, null);
        DeviceManager manager = createDeviceManagerNoInit();
        manager.init(
                null,
                null,
                new ManagedTestDeviceFactory(false, null, null) {
                    @Override
                    public IManagedTestDevice createDevice(IDevice idevice) {
                        mMockTestDevice.setIDevice(idevice);
                        return testDevice;
                    }

                    @Override
                    protected CollectingOutputReceiver createOutputReceiver() {
                        return new CollectingOutputReceiver() {
                            @Override
                            public String getOutput() {
                                return "/system/bin/pm";
                            }
                        };
                    }

                    @Override
                    public void setFastbootEnabled(boolean enable) {
                        // ignore
                    }
                });

        mDeviceListener.deviceConnected(mMockIDevice);

        IManagedTestDevice device = (IManagedTestDevice) manager.allocateDevice(mDeviceSelections);
        assertNotNull(device);
        // device becomes unavailable
        device.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        // a freed 'unavailable' device becomes UNAVAILABLE state
        manager.freeDevice(device, FreeDeviceState.UNAVAILABLE);
        // ensure device cannot be allocated again
        ITestDevice device2 = manager.allocateDevice(mDeviceSelections);
        assertNull(device2);

        verify(mMockStateMonitor).setState(TestDeviceState.NOT_AVAILABLE);

        // We still have the device in the list
        assertEquals(1, manager.getDeviceList().size());
    }

    /** Ensure that an unavailable device in recovery mode is released properly. */
    @Test
    public void testFreeDevice_recovery() {
        when(mMockIDevice.isEmulator()).thenReturn(Boolean.FALSE);
        when(mMockIDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockStateMonitor.waitForDeviceShell(Mockito.anyLong())).thenReturn(Boolean.TRUE);

        CommandResult stubAdbDevices = new CommandResult(CommandStatus.SUCCESS);
        stubAdbDevices.setStdout("List of devices attached\nserial\trecovery\n");
        when(mMockRunUtil.runTimedCmd(Mockito.anyLong(), Mockito.eq("adb"), Mockito.eq("devices")))
                .thenReturn(stubAdbDevices);

        IManagedTestDevice testDevice = new TestDevice(mMockIDevice, mMockStateMonitor, null);
        DeviceManager manager = createDeviceManagerNoInit();
        manager.init(
                null,
                null,
                new ManagedTestDeviceFactory(false, null, null) {
                    @Override
                    public IManagedTestDevice createDevice(IDevice idevice) {
                        mMockTestDevice.setIDevice(idevice);
                        return testDevice;
                    }

                    @Override
                    protected CollectingOutputReceiver createOutputReceiver() {
                        return new CollectingOutputReceiver() {
                            @Override
                            public String getOutput() {
                                return "/system/bin/pm";
                            }
                        };
                    }

                    @Override
                    public void setFastbootEnabled(boolean enable) {
                        // ignore
                    }
                });

        mDeviceListener.deviceConnected(mMockIDevice);

        IManagedTestDevice device = (IManagedTestDevice) manager.allocateDevice(mDeviceSelections);
        assertNotNull(device);
        // Device becomes unavailable
        device.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        // A freed 'unavailable' device becomes UNAVAILABLE state
        manager.freeDevice(device, FreeDeviceState.UNAVAILABLE);
        // Ensure device cannot be allocated again
        ITestDevice device2 = manager.allocateDevice(mDeviceSelections);
        assertNull(device2);

        verify(mMockStateMonitor).setState(TestDeviceState.NOT_AVAILABLE);

        // We still have the device in the list because device is not lost.
        assertEquals(1, manager.getDeviceList().size());
    }

    /**
     * Test that when freeing an Unavailable device that is not in 'adb devices' we correctly remove
     * it from our tracking list.
     */
    @Test
    public void testFreeDevice_unknown() {
        when(mMockIDevice.isEmulator()).thenReturn(Boolean.FALSE);
        when(mMockIDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockStateMonitor.waitForDeviceShell(Mockito.anyLong())).thenReturn(Boolean.TRUE);

        CommandResult stubAdbDevices = new CommandResult(CommandStatus.SUCCESS);
        // device serial is not in the list
        stubAdbDevices.setStdout("List of devices attached\n");
        when(mMockRunUtil.runTimedCmd(Mockito.anyLong(), Mockito.eq("adb"), Mockito.eq("devices")))
                .thenReturn(stubAdbDevices);

        IManagedTestDevice testDevice = new TestDevice(mMockIDevice, mMockStateMonitor, null);
        DeviceManager manager = createDeviceManagerNoInit();
        manager.init(
                null,
                null,
                new ManagedTestDeviceFactory(false, null, null) {
                    @Override
                    public IManagedTestDevice createDevice(IDevice idevice) {
                        mMockTestDevice.setIDevice(idevice);
                        return testDevice;
                    }

                    @Override
                    protected CollectingOutputReceiver createOutputReceiver() {
                        return new CollectingOutputReceiver() {
                            @Override
                            public String getOutput() {
                                return "/system/bin/pm";
                            }
                        };
                    }

                    @Override
                    public void setFastbootEnabled(boolean enable) {
                        // ignore
                    }
                });

        mDeviceListener.deviceConnected(mMockIDevice);

        IManagedTestDevice device = (IManagedTestDevice) manager.allocateDevice(mDeviceSelections);
        assertNotNull(device);
        // device becomes unavailable
        device.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        // a freed 'unavailable' device becomes UNAVAILABLE state
        manager.freeDevice(device, FreeDeviceState.UNAVAILABLE);
        // ensure device cannot be allocated again
        ITestDevice device2 = manager.allocateDevice(mDeviceSelections);
        assertNull(device2);

        verify(mMockStateMonitor).setState(TestDeviceState.NOT_AVAILABLE);

        // We have 0 device in the list since it was removed
        assertEquals(0, manager.getDeviceList().size());
    }

    /**
     * Test that when freeing an Unavailable device that is not in 'adb devices' we correctly remove
     * it from our tracking list even if its serial is a substring of another serial.
     */
    @Test
    public void testFreeDevice_unknown_subName() {
        when(mMockIDevice.isEmulator()).thenReturn(Boolean.FALSE);
        when(mMockIDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockStateMonitor.waitForDeviceShell(Mockito.anyLong())).thenReturn(Boolean.TRUE);

        CommandResult stubAdbDevices = new CommandResult(CommandStatus.SUCCESS);
        // device serial is not in the list
        stubAdbDevices.setStdout("List of devices attached\n2serial\tdevice\n");
        when(mMockRunUtil.runTimedCmd(Mockito.anyLong(), Mockito.eq("adb"), Mockito.eq("devices")))
                .thenReturn(stubAdbDevices);

        IManagedTestDevice testDevice = new TestDevice(mMockIDevice, mMockStateMonitor, null);
        DeviceManager manager = createDeviceManagerNoInit();
        manager.init(
                null,
                null,
                new ManagedTestDeviceFactory(false, null, null) {
                    @Override
                    public IManagedTestDevice createDevice(IDevice idevice) {
                        mMockTestDevice.setIDevice(idevice);
                        return testDevice;
                    }

                    @Override
                    protected CollectingOutputReceiver createOutputReceiver() {
                        return new CollectingOutputReceiver() {
                            @Override
                            public String getOutput() {
                                return "/system/bin/pm";
                            }
                        };
                    }

                    @Override
                    public void setFastbootEnabled(boolean enable) {
                        // ignore
                    }
                });

        mDeviceListener.deviceConnected(mMockIDevice);

        IManagedTestDevice device = (IManagedTestDevice) manager.allocateDevice(mDeviceSelections);
        assertNotNull(device);
        // device becomes unavailable
        device.setDeviceState(TestDeviceState.NOT_AVAILABLE);
        // a freed 'unavailable' device becomes UNAVAILABLE state
        manager.freeDevice(device, FreeDeviceState.UNAVAILABLE);
        // ensure device cannot be allocated again
        ITestDevice device2 = manager.allocateDevice(mDeviceSelections);
        assertNull(device2);

        verify(mMockStateMonitor).setState(TestDeviceState.NOT_AVAILABLE);

        // We have 0 device in the list since it was removed
        assertEquals(0, manager.getDeviceList().size());
    }

    /** Helper to set the expectation when a {@link DeviceDescriptor} is expected. */
    private void setDeviceDescriptorExpectation(boolean cached) {
        DeviceDescriptor descriptor =
                new DeviceDescriptor(
                        "serial",
                        null,
                        false,
                        DeviceState.ONLINE,
                        DeviceAllocationState.Available,
                        TestDeviceState.ONLINE,
                        "hardware_test",
                        "product_test",
                        "sdk",
                        "bid_test",
                        null,
                        "50",
                        "class",
                        MAC_ADDRESS,
                        SIM_STATE,
                        SIM_OPERATOR,
                        false,
                        null,
                        null,
                        null);
        if (cached) {
            when(mMockTestDevice.getCachedDeviceDescriptor(false)).thenReturn(descriptor);
        } else {
            when(mMockTestDevice.getDeviceDescriptor(false)).thenReturn(descriptor);
        }
    }

    /** Test that {@link DeviceManager#listAllDevices()} returns a list with all devices. */
    @Test
    public void testListAllDevices() throws Exception {
        setCheckAvailableDeviceExpectations();
        setDeviceDescriptorExpectation(true);

        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        List<DeviceDescriptor> res = manager.listAllDevices();
        assertEquals(1, res.size());
        assertEquals("[serial hardware_test:product_test bid_test]", res.get(0).toString());
        assertEquals(MAC_ADDRESS, res.get(0).getMacAddress());
        assertEquals(SIM_STATE, res.get(0).getSimState());
        assertEquals(SIM_OPERATOR, res.get(0).getSimOperator());
    }

    /**
     * Test {@link DeviceManager#getDeviceDescriptor(String)} returns the device with the given
     * serial.
     */
    @Test
    public void testGetDeviceDescriptor() throws Exception {
        setCheckAvailableDeviceExpectations();
        setDeviceDescriptorExpectation(false);

        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        DeviceDescriptor res = manager.getDeviceDescriptor(mMockIDevice.getSerialNumber());
        assertEquals("[serial hardware_test:product_test bid_test]", res.toString());
        assertEquals(MAC_ADDRESS, res.getMacAddress());
        assertEquals(SIM_STATE, res.getSimState());
        assertEquals(SIM_OPERATOR, res.getSimOperator());
    }

    /**
     * Test that {@link DeviceManager#getDeviceDescriptor(String)} returns null if there are no
     * devices with the given serial.
     */
    @Test
    public void testGetDeviceDescriptor_noMatch() throws Exception {
        setCheckAvailableDeviceExpectations();

        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        DeviceDescriptor res = manager.getDeviceDescriptor("nomatch");
        assertNull(res);
    }

    /**
     * Test that {@link DeviceManager#displayDevicesInfo(PrintWriter, boolean)} properly print out
     * the device info.
     */
    @Test
    public void testDisplayDevicesInfo() throws Exception {
        setCheckAvailableDeviceExpectations();
        setDeviceDescriptorExpectation(true);

        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(out);
        manager.displayDevicesInfo(pw, false);
        pw.flush();

        assertEquals(
                "Serial  State   Allocation  Product        Variant       Build     Battery  \n"
                    + "serial  ONLINE  Available   hardware_test  product_test  bid_test  50      "
                    + " \n",
                out.toString());
    }

    /**
     * Test that {@link DeviceManager#shouldAdbBridgeBeRestarted()} properly reports the flag state
     * based on if it was requested or not.
     */
    @Test
    public void testAdbBridgeFlag() throws Exception {
        setCheckAvailableDeviceExpectations();

        DeviceManager manager = createDeviceManager(null, mMockIDevice);

        assertFalse(manager.shouldAdbBridgeBeRestarted());
        manager.stopAdbBridge();
        assertTrue(manager.shouldAdbBridgeBeRestarted());
        manager.restartAdbBridge();
        assertFalse(manager.shouldAdbBridgeBeRestarted());
    }

    /**
     * Test that when a {@link IDeviceMonitor} is available in {@link DeviceManager} it properly
     * goes through its life cycle.
     */
    @Test
    public void testDeviceMonitorLifeCycle() throws Exception {
        IDeviceMonitor mockMonitor = mock(IDeviceMonitor.class);
        List<IDeviceMonitor> monitors = new ArrayList<>();
        monitors.add(mockMonitor);
        setCheckAvailableDeviceExpectations();

        DeviceManager manager = createDeviceManager(monitors, mMockIDevice);
        manager.terminateDeviceMonitor();

        verify(mockMonitor).setDeviceLister(Mockito.any());
        verify(mockMonitor).run();
        verify(mockMonitor).stop();
    }

    /** Ensure that restarting adb bridge doesn't restart {@link IDeviceMonitor}. */
    @Test
    public void testDeviceMonitorLifeCycleWhenAdbRestarts() throws Exception {
        IDeviceMonitor mockMonitor = mock(IDeviceMonitor.class);
        List<IDeviceMonitor> monitors = new ArrayList<>();
        monitors.add(mockMonitor);
        setCheckAvailableDeviceExpectations();

        DeviceManager manager = createDeviceManager(monitors, mMockIDevice);
        manager.stopAdbBridge();
        manager.restartAdbBridge();
        manager.terminateDeviceMonitor();

        verify(mockMonitor).setDeviceLister(Mockito.any());
        verify(mockMonitor).run();
        verify(mockMonitor).stop();
    }

    /** Test the command fails without execution when the device is not available. */
    @Test
    public void testExecCmdOnAvailableDevice_deviceNotAvailable() {
        setCheckAvailableDeviceExpectations();
        when(mMockTestDevice.getAllocationState()).thenReturn(DeviceAllocationState.Allocated);

        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        CommandResult res =
                manager.executeCmdOnAvailableDevice(
                        mMockTestDevice.getSerialNumber(), "mock cmd", 1, TimeUnit.SECONDS);
        assertEquals(CommandStatus.FAILED, res.getStatus());
        assertEquals(
                "The device 'serial' is not available to execute the command", res.getStderr());
    }

    /** Test the command fails with long timeout. */
    @Test
    public void testExecCmdOnAvailableDevice_longRunCmd() throws DeviceNotAvailableException {
        setCheckAvailableDeviceExpectations();
        when(mMockTestDevice.getAllocationState()).thenReturn(DeviceAllocationState.Available);
        when(mMockTestDevice.executeShellV2Command("foo", 2, TimeUnit.SECONDS))
                .thenReturn(new CommandResult(CommandStatus.SUCCESS));

        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        CommandResult res =
                manager.executeCmdOnAvailableDevice(
                        mMockTestDevice.getSerialNumber(), "mock cmd", 2, TimeUnit.SECONDS);
        assertEquals(res.getStatus(), CommandStatus.FAILED);
        assertEquals(res.getStderr(), "The maximum timeout value is 1000 ms, but got 2000 ms.");
    }

    /** Test the command success. */
    @Test
    public void testExecCmdOnAvailableDevice_success() throws DeviceNotAvailableException {
        setCheckAvailableDeviceExpectations();
        when(mMockTestDevice.getAllocationState()).thenReturn(DeviceAllocationState.Available);
        when(mMockTestDevice.executeShellV2Command("foo", 1, TimeUnit.SECONDS))
                .thenReturn(
                        new CommandResult() {
                            @Override
                            public CommandStatus getStatus() {
                                return CommandStatus.SUCCESS;
                            }

                            @Override
                            public String getStdout() {
                                return "bar";
                            }
                        });

        DeviceManager manager = createDeviceManager(null, mMockIDevice);
        CommandResult res =
                manager.executeCmdOnAvailableDevice(
                        mMockTestDevice.getSerialNumber(), "foo", 1, TimeUnit.SECONDS);
        assertEquals(CommandStatus.SUCCESS, res.getStatus());
        assertEquals("bar", res.getStdout());
    }
}
