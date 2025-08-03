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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.tradefed.util.RunUtil;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/** Unit tests for {@link DeviceStateMonitorTest}. */
@RunWith(JUnit4.class)
public class DeviceStateMonitorTest {
    private static final int WAIT_TIMEOUT_NOT_REACHED_MS = 500;
    private static final int WAIT_TIMEOUT_REACHED_MS = 100;
    private static final int WAIT_STATE_CHANGE_MS = 50;
    private static final int POLL_TIME_MS = 10;

    private static final String SERIAL_NUMBER = "1";
    @Mock IDevice mMockDevice;
    private DeviceStateMonitor mMonitor;
    @Mock IDeviceManager mMockMgr;
    private AtomicBoolean mAtomicBoolean = new AtomicBoolean(false);
    private String mStubValue = "not found";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mStubValue = "not found";

        when(mMockMgr.isFileSystemMountCheckEnabled()).thenReturn(false);
        mMockMgr.addFastbootListener(any());
        mMockMgr.removeFastbootListener(any());

        when(mMockDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);

        mMonitor = new DeviceStateMonitor(mMockMgr, mMockDevice, true);
    }

    /** Test {@link DeviceStateMonitor#waitForDeviceOnline()} when device is already online */
    @Test
    public void testWaitForDeviceOnline_alreadyOnline() {
        assertEquals(mMockDevice, mMonitor.waitForDeviceOnline());
    }

    /** Test {@link DeviceStateMonitor#waitForDeviceOnline()} when device becomes online */
    @Test
    public void testWaitForDeviceOnline() {
        mMonitor =
                new DeviceStateMonitor(mMockMgr, mMockDevice, true) {
                    @Override
                    public TestDeviceState getDeviceState() {
                        if (mAtomicBoolean.get()) {
                            return TestDeviceState.ONLINE;
                        } else {
                            mAtomicBoolean.set(true);
                            return TestDeviceState.NOT_AVAILABLE;
                        }
                    }
                };
        assertEquals(mMockDevice, mMonitor.waitForDeviceOnline(50));
    }

    /**
     * Test {@link DeviceStateMonitor#waitForDeviceOnline()} when device does not becomes online
     * within allowed time
     */
    @Test
    public void testWaitForDeviceOnline_timeout() {
        mMonitor.setState(TestDeviceState.NOT_AVAILABLE);
        assertNull(mMonitor.waitForDeviceOnline(WAIT_TIMEOUT_REACHED_MS));
    }

    /** Test {@link DeviceStateMonitor#isAdbTcp()} with a USB serial number. */
    @Test
    public void testIsAdbTcp_usb() {
        IDevice mockDevice = mock(IDevice.class);
        when(mockDevice.getSerialNumber()).thenReturn("2345asdf");
        when(mockDevice.getState()).thenReturn(DeviceState.ONLINE);

        DeviceStateMonitor monitor = new DeviceStateMonitor(mMockMgr, mockDevice, true);
        assertFalse(monitor.isAdbTcp());
    }

    /** Test {@link DeviceStateMonitor#isAdbTcp()} with a TCP serial number. */
    @Test
    public void testIsAdbTcp_tcp() {
        IDevice mockDevice = mock(IDevice.class);
        when(mockDevice.getSerialNumber()).thenReturn("192.168.1.1:5555");
        when(mockDevice.getState()).thenReturn(DeviceState.ONLINE);

        DeviceStateMonitor monitor = new DeviceStateMonitor(mMockMgr, mockDevice, true);
        assertTrue(monitor.isAdbTcp());
    }

    /**
     * Test {@link DeviceStateMonitor#waitForDeviceNotAvailable(long)} when device is already
     * offline
     */
    @Test
    public void testWaitForDeviceOffline_alreadyOffline() {
        mMonitor.setState(TestDeviceState.NOT_AVAILABLE);
        boolean res = mMonitor.waitForDeviceNotAvailable(WAIT_TIMEOUT_NOT_REACHED_MS);
        assertTrue(res);
    }

    /**
     * Test {@link DeviceStateMonitor#waitForDeviceNotAvailable(long)} when device becomes offline
     */
    @Test
    public void testWaitForDeviceOffline() {
        mMonitor =
                new DeviceStateMonitor(mMockMgr, mMockDevice, true) {
                    @Override
                    public TestDeviceState getDeviceState() {
                        if (mAtomicBoolean.get()) {
                            return TestDeviceState.NOT_AVAILABLE;
                        } else {
                            mAtomicBoolean.set(true);
                            return TestDeviceState.ONLINE;
                        }
                    }
                };
        boolean res = mMonitor.waitForDeviceNotAvailable(WAIT_TIMEOUT_NOT_REACHED_MS);
        assertTrue(res);
    }

    /**
     * Test {@link DeviceStateMonitor#waitForDeviceNotAvailable(long)} when device doesn't become
     * offline
     */
    @Test
    public void testWaitForDeviceOffline_timeout() {
        mMonitor.setState(TestDeviceState.ONLINE);
        boolean res = mMonitor.waitForDeviceNotAvailable(WAIT_TIMEOUT_REACHED_MS);
        assertFalse(res);
    }

    /** Test {@link DeviceStateMonitor#waitForDeviceShell(long)} when shell is available. */
    @Test
    public void testWaitForShellAvailable() throws Exception {
        when(mMockDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);
        mMonitor =
                new DeviceStateMonitor(mMockMgr, mMockDevice, true) {
                    @Override
                    protected CollectingOutputReceiver createOutputReceiver() {
                        return new CollectingOutputReceiver() {
                            @Override
                            public String getOutput() {
                                return "uid=0(root)";
                            }
                        };
                    }
                };
        boolean res = mMonitor.waitForDeviceShell(WAIT_TIMEOUT_NOT_REACHED_MS);
        assertTrue(res);
        verify(mMockDevice, times(1))
                .executeShellCommand(
                        (String) any(),
                        (CollectingOutputReceiver) any(),
                        anyLong(),
                        eq(TimeUnit.MILLISECONDS));
    }

    /** Test {@link DeviceStateMonitor#waitForDeviceShell(long)} when shell become available. */
    @Test
    public void testWaitForShell_becomeAvailable() throws Exception {
        when(mMockDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);
        mMockDevice.executeShellCommand(
                (String) any(),
                (CollectingOutputReceiver) any(),
                anyInt(),
                eq(TimeUnit.MILLISECONDS));

        mMonitor =
                new DeviceStateMonitor(mMockMgr, mMockDevice, true) {
                    @Override
                    protected CollectingOutputReceiver createOutputReceiver() {
                        return new CollectingOutputReceiver() {
                            @Override
                            public String getOutput() {
                                if (mAtomicBoolean.get()) {
                                    return "uid=0(root)";
                                }
                                return "not found";
                            }
                        };
                    }

                    @Override
                    protected long getCheckPollTime() {
                        return POLL_TIME_MS;
                    }
                };
        Thread test =
                new Thread() {
                    @Override
                    public void run() {
                        RunUtil.getDefault().sleep(WAIT_STATE_CHANGE_MS);
                        mAtomicBoolean.set(true);
                    }
                };
        test.setName(getClass().getCanonicalName() + "#testWaitForShell_becomeAvailable");
        test.start();
        boolean res = mMonitor.waitForDeviceShell(WAIT_TIMEOUT_NOT_REACHED_MS);
        assertTrue(res);
        test.join();
    }

    /**
     * Test {@link DeviceStateMonitor#waitForDeviceShell(long)} when shell never become available.
     */
    @Test
    public void testWaitForShell_timeout() throws Exception {
        when(mMockDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);
        mMockDevice.executeShellCommand(
                (String) any(),
                (CollectingOutputReceiver) any(),
                anyInt(),
                eq(TimeUnit.MILLISECONDS));

        mMonitor =
                new DeviceStateMonitor(mMockMgr, mMockDevice, true) {
                    @Override
                    protected CollectingOutputReceiver createOutputReceiver() {
                        return new CollectingOutputReceiver() {
                            @Override
                            public String getOutput() {
                                return mStubValue;
                            }
                        };
                    }

                    @Override
                    protected long getCheckPollTime() {
                        return POLL_TIME_MS;
                    }
                };
        boolean res = mMonitor.waitForDeviceShell(WAIT_TIMEOUT_REACHED_MS);
        assertFalse(res);
    }

    /** Test {@link DeviceStateMonitor#waitForBootComplete(long)} when boot is already complete. */
    @Test
    public void testWaitForBootComplete() throws Exception {
        IDevice mFakeDevice =
                new StubDevice("serial") {
                    @Override
                    public void executeShellCommand(
                            String command,
                            IShellOutputReceiver receiver,
                            long maxTimeToOutputResponse,
                            TimeUnit maxTimeUnits)
                            throws TimeoutException, AdbCommandRejectedException,
                                    ShellCommandUnresponsiveException, IOException {
                        String res = "1\n";
                        receiver.addOutput(res.getBytes(), 0, res.length());
                    }
                };
        mMonitor = new DeviceStateMonitor(mMockMgr, mFakeDevice, true);
        boolean res = mMonitor.waitForBootComplete(WAIT_TIMEOUT_NOT_REACHED_MS);
        assertTrue(res);
    }

    /**
     * Test {@link DeviceStateMonitor#waitForBootComplete(long)} when the shell command returns
     * warnings. This happens on some specialized devices such as Microdroid.
     */
    @Test
    public void testWaitForBootComplete_warnings() throws Exception {
        IDevice mFakeDevice =
                new StubDevice("serial") {
                    @Override
                    public void executeShellCommand(
                            String command,
                            IShellOutputReceiver receiver,
                            long maxTimeToOutputResponse,
                            TimeUnit maxTimeUnits)
                            throws TimeoutException, AdbCommandRejectedException,
                                    ShellCommandUnresponsiveException, IOException {
                        String res =
                                "warning: Not a fatal error #1\n"
                                        + "warning: Not a fatal error #2\n"
                                        + "warning: Not a fatal error #3\n"
                                        + "1\n";
                        receiver.addOutput(res.getBytes(), 0, res.length());
                    }
                };
        mMonitor = new DeviceStateMonitor(mMockMgr, mFakeDevice, true);
        boolean res = mMonitor.waitForBootComplete(WAIT_TIMEOUT_NOT_REACHED_MS);
        assertTrue(res);
    }

    /**
     * Test {@link DeviceStateMonitor#waitForBootComplete(long)} when boot complete status change.
     */
    @Test
    public void testWaitForBoot_becomeComplete() throws Exception {
        IDevice mFakeDevice =
                new StubDevice("serial") {

                    @Override
                    public void executeShellCommand(
                            String command,
                            IShellOutputReceiver receiver,
                            long maxTimeToOutputResponse,
                            TimeUnit maxTimeUnits)
                            throws TimeoutException, AdbCommandRejectedException,
                                    ShellCommandUnresponsiveException, IOException {
                        String res = "";
                        if (mAtomicBoolean.get()) {
                            res = "1\n";
                        } else {
                            res = "0\n";
                        }
                        receiver.addOutput(res.getBytes(), 0, res.length());
                    }
                };
        mMonitor =
                new DeviceStateMonitor(mMockMgr, mFakeDevice, true) {
                    @Override
                    protected long getCheckPollTime() {
                        return POLL_TIME_MS;
                    }
                };
        Thread test =
                new Thread() {
                    @Override
                    public void run() {
                        RunUtil.getDefault().sleep(WAIT_STATE_CHANGE_MS);
                        mAtomicBoolean.set(true);
                    }
                };
        test.setName(getClass().getCanonicalName() + "#testWaitForBoot_becomeComplete");
        test.start();
        boolean res = mMonitor.waitForBootComplete(WAIT_TIMEOUT_NOT_REACHED_MS);
        assertTrue(res);
    }

    /** Test {@link DeviceStateMonitor#waitForBootComplete(long)} when boot complete timeout. */
    @Test
    public void testWaitForBoot_timeout() throws Exception {
        mStubValue = "0";
        IDevice mFakeDevice =
                new StubDevice("serial") {
                    @Override
                    public ListenableFuture<String> getSystemProperty(String name) {
                        SettableFuture<String> f = SettableFuture.create();
                        f.set(mStubValue);
                        return f;
                    }
                };
        mMonitor =
                new DeviceStateMonitor(mMockMgr, mFakeDevice, true) {
                    @Override
                    protected long getCheckPollTime() {
                        return POLL_TIME_MS;
                    }
                };
        boolean res = mMonitor.waitForBootComplete(WAIT_TIMEOUT_REACHED_MS);
        assertFalse(res);
    }

    /**
     * Test {@link DeviceStateMonitor#waitForPmResponsive(long)} when package manager is already
     * responsive.
     */
    @Test
    public void testWaitForPmResponsive() throws Exception {
        when(mMockDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);

        mMonitor =
                new DeviceStateMonitor(mMockMgr, mMockDevice, true) {
                    @Override
                    protected CollectingOutputReceiver createOutputReceiver() {
                        return new CollectingOutputReceiver() {
                            @Override
                            public String getOutput() {
                                return "package:com.android.awesomeclass";
                            }
                        };
                    }
                };
        boolean res = mMonitor.waitForPmResponsive(WAIT_TIMEOUT_NOT_REACHED_MS);
        assertTrue(res);
        verify(mMockDevice, times(1))
                .executeShellCommand(
                        (String) any(),
                        (CollectingOutputReceiver) any(),
                        anyLong(),
                        eq(TimeUnit.MILLISECONDS));
    }

    /**
     * Test {@link DeviceStateMonitor#waitForPmResponsive(long)} when package manager becomes
     * responsive
     */
    @Test
    public void testWaitForPm_becomeResponsive() throws Exception {
        when(mMockDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);

        mMonitor =
                new DeviceStateMonitor(mMockMgr, mMockDevice, true) {
                    @Override
                    protected CollectingOutputReceiver createOutputReceiver() {
                        return new CollectingOutputReceiver() {
                            @Override
                            public String getOutput() {
                                if (mAtomicBoolean.get()) {
                                    return "package:com.android.awesomeclass";
                                }
                                return "not found";
                            }
                        };
                    }

                    @Override
                    protected long getCheckPollTime() {
                        return POLL_TIME_MS;
                    }
                };
        Thread test =
                new Thread() {
                    @Override
                    public void run() {
                        RunUtil.getDefault().sleep(WAIT_STATE_CHANGE_MS);
                        mAtomicBoolean.set(true);
                    }
                };
        test.setName(getClass().getCanonicalName() + "#testWaitForPm_becomeResponsive");
        test.start();
        boolean res = mMonitor.waitForPmResponsive(WAIT_TIMEOUT_NOT_REACHED_MS);
        assertTrue(res);
        test.join();
    }

    /**
     * Test {@link DeviceStateMonitor#waitForPmResponsive(long)} when package manager check timeout
     * before becoming responsive.
     */
    @Test
    public void testWaitForPm_timeout() throws Exception {
        when(mMockDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);

        mMonitor =
                new DeviceStateMonitor(mMockMgr, mMockDevice, true) {
                    @Override
                    protected CollectingOutputReceiver createOutputReceiver() {
                        return new CollectingOutputReceiver() {
                            @Override
                            public String getOutput() {
                                return mStubValue;
                            }
                        };
                    }

                    @Override
                    protected long getCheckPollTime() {
                        return POLL_TIME_MS;
                    }
                };
        boolean res = mMonitor.waitForPmResponsive(WAIT_TIMEOUT_REACHED_MS);
        assertFalse(res);
    }

    /** Test {@link DeviceStateMonitor#getMountPoint(String)} return the cached mount point. */
    @Test
    public void testgetMountPoint() throws Exception {
        String expectedMountPoint = "NOT NULL";
        when(mMockDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);
        when(mMockDevice.getMountPoint((String) any())).thenReturn(expectedMountPoint);

        mMonitor = new DeviceStateMonitor(mMockMgr, mMockDevice, true);
        assertEquals(expectedMountPoint, mMonitor.getMountPoint(""));
    }

    /**
     * Test {@link DeviceStateMonitor#getMountPoint(String)} return the mount point that is not
     * cached.
     */
    @Test
    public void testgetMountPoint_nonCached() throws Exception {
        when(mMockDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);
        when(mMockDevice.getMountPoint((String) any())).thenReturn(null);
        mMockDevice.executeShellCommand((String) any(), (CollectingOutputReceiver) any());

        mMonitor =
                new DeviceStateMonitor(mMockMgr, mMockDevice, true) {
                    @Override
                    protected CollectingOutputReceiver createOutputReceiver() {
                        return new CollectingOutputReceiver() {
                            @Override
                            public String getOutput() {
                                return "NONCACHED";
                            }
                        };
                    }
                };
        assertEquals("NONCACHED", mMonitor.getMountPoint(""));
    }

    /**
     * Test {@link DeviceStateMonitor#waitForStoreMount(long)} when mount point is already mounted
     */
    @Test
    public void testWaitForStoreMount() throws Exception {
        when(mMockDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);

        mMonitor =
                new DeviceStateMonitor(mMockMgr, mMockDevice, true) {
                    @Override
                    protected CollectingOutputReceiver createOutputReceiver() {
                        return new CollectingOutputReceiver() {
                            @Override
                            public String getOutput() {
                                return "number 10 one";
                            }
                        };
                    }

                    @Override
                    protected long getCurrentTime() {
                        return 10;
                    }

                    @Override
                    public String getMountPoint(String mountName) {
                        return "";
                    }
                };
        boolean res = mMonitor.waitForStoreMount(WAIT_TIMEOUT_NOT_REACHED_MS);
        assertTrue(res);
    }

    /**
     * Test {@link DeviceStateMonitor#waitForStoreMount(long)} when mount point return permission
     * denied should return directly false.
     */
    @Test
    public void testWaitForStoreMount_permDenied() throws Exception {
        when(mMockDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);

        Function<Integer, DeviceStateMonitor> creator =
                (Integer count) ->
                        new DeviceStateMonitor(mMockMgr, mMockDevice, true) {
                            private int mCount = count;

                            @Override
                            protected CollectingOutputReceiver createOutputReceiver() {
                                String output =
                                        --mCount >= 0
                                                ? "/system/bin/sh: cat: /sdcard/1459376318045:"
                                                        + " Permission denied"
                                                : "number 10 one";
                                return new CollectingOutputReceiver() {
                                    @Override
                                    public String getOutput() {
                                        return output;
                                    }
                                };
                            }

                            @Override
                            protected long getCurrentTime() {
                                return 10;
                            }

                            @Override
                            protected long getCheckPollTime() {
                                // Set retry interval to 0 so #waitForStoreMount won't fail due to
                                // timeout
                                return 0;
                            }

                            @Override
                            public String getMountPoint(String mountName) {
                                return "";
                            }
                        };

        // 'Permission denied' is never returned. #waitForStoreMount should return true.
        mMonitor = creator.apply(0);
        assertTrue(mMonitor.waitForStoreMount(WAIT_TIMEOUT_NOT_REACHED_MS));
        // 'Permission denied' is returned once. #waitForStoreMount should return true
        // since we retry once when 'Permission denied' is returned.
        mMonitor = creator.apply(1);
        assertTrue(mMonitor.waitForStoreMount(WAIT_TIMEOUT_NOT_REACHED_MS));
        // 'Permission denied' is returned twice. #waitForStoreMount should return false
        // since the 2nd retry on 'Permission denied' still fails.
        mMonitor = creator.apply(2);
        assertFalse(mMonitor.waitForStoreMount(WAIT_TIMEOUT_NOT_REACHED_MS));
    }

    /** Test {@link DeviceStateMonitor#waitForStoreMount(long)} when mount point become available */
    @Test
    public void testWaitForStoreMount_becomeAvailable() throws Exception {
        when(mMockDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);

        mMonitor =
                new DeviceStateMonitor(mMockMgr, mMockDevice, true) {
                    @Override
                    protected CollectingOutputReceiver createOutputReceiver() {
                        return new CollectingOutputReceiver() {
                            @Override
                            public String getOutput() {
                                return "number 10 one";
                            }
                        };
                    }

                    @Override
                    protected long getCurrentTime() {
                        return 10;
                    }

                    @Override
                    protected long getCheckPollTime() {
                        return POLL_TIME_MS;
                    }

                    @Override
                    public String getMountPoint(String mountName) {
                        if (mAtomicBoolean.get()) {
                            return "NOT NULL";
                        }
                        return null;
                    }
                };
        Thread test =
                new Thread() {
                    @Override
                    public void run() {
                        RunUtil.getDefault().sleep(WAIT_STATE_CHANGE_MS);
                        mAtomicBoolean.set(true);
                    }
                };
        test.setName(getClass().getCanonicalName() + "#testWaitForStoreMount_becomeAvailable");
        test.start();
        boolean res = mMonitor.waitForStoreMount(WAIT_TIMEOUT_NOT_REACHED_MS);
        assertTrue(res);
        test.join();
    }

    /**
     * Test {@link DeviceStateMonitor#waitForStoreMount(long)} when mount point is available and the
     * output of the check (string in the file) become valid.
     */
    @Test
    public void testWaitForStoreMount_outputBecomeValid() throws Exception {
        when(mMockDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);

        mMonitor =
                new DeviceStateMonitor(mMockMgr, mMockDevice, true) {
                    @Override
                    protected CollectingOutputReceiver createOutputReceiver() {
                        return new CollectingOutputReceiver() {
                            @Override
                            public String getOutput() {
                                if (mAtomicBoolean.get()) {
                                    return "number 10 one";
                                }
                                return "INVALID";
                            }
                        };
                    }

                    @Override
                    protected long getCurrentTime() {
                        return 10;
                    }

                    @Override
                    protected long getCheckPollTime() {
                        return POLL_TIME_MS;
                    }

                    @Override
                    public String getMountPoint(String mountName) {
                        return "NOT NULL";
                    }
                };
        Thread test =
                new Thread() {
                    @Override
                    public void run() {
                        RunUtil.getDefault().sleep(WAIT_STATE_CHANGE_MS);
                        mAtomicBoolean.set(true);
                    }
                };
        test.setName(getClass().getCanonicalName() + "#testWaitForStoreMount_outputBecomeValid");
        test.start();
        boolean res = mMonitor.waitForStoreMount(WAIT_TIMEOUT_NOT_REACHED_MS);
        assertTrue(res);
        test.join();
    }

    /** Test {@link DeviceStateMonitor#waitForStoreMount(long)} when mount point check timeout */
    @Test
    public void testWaitForStoreMount_timeout() throws Exception {
        mStubValue = null;
        when(mMockDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);

        mMonitor =
                new DeviceStateMonitor(mMockMgr, mMockDevice, true) {
                    @Override
                    protected long getCheckPollTime() {
                        return POLL_TIME_MS;
                    }

                    @Override
                    public String getMountPoint(String mountName) {
                        return mStubValue;
                    }
                };
        boolean res = mMonitor.waitForStoreMount(WAIT_TIMEOUT_REACHED_MS);
        assertFalse(res);
    }

    /**
     * Test {@link DeviceStateMonitor#waitForDeviceAvailable(long)} wait for device available when
     * device is already available.
     */
    @Test
    public void testWaitForDeviceAvailable() throws Exception {
        when(mMockDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);

        mMonitor =
                new DeviceStateMonitor(mMockMgr, mMockDevice, true) {
                    @Override
                    public IDevice waitForDeviceOnline(long waitTime) {
                        return mMockDevice;
                    }

                    @Override
                    public boolean waitForBootComplete(long waitTime) {
                        return true;
                    }

                    @Override
                    protected boolean waitForPmResponsive(long waitTime) {
                        return true;
                    }

                    @Override
                    protected boolean waitForStoreMount(long waitTime) {
                        return true;
                    }
                };
        assertEquals(mMockDevice, mMonitor.waitForDeviceAvailable(WAIT_TIMEOUT_NOT_REACHED_MS));
    }

    @Test
    public void testWaitForDeviceAvailable_mounted() throws Exception {
        when(mMockDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);
        when(mMockDevice.getMountPoint((String) any())).thenReturn("/sdcard");
        doAnswer(
                        invocation -> {
                            CollectingOutputReceiver stat =
                                    (CollectingOutputReceiver) invocation.getArguments()[1];
                            String statOutput = "65735546\n"; // Fuse magic number
                            stat.addOutput(statOutput.getBytes(), 0, statOutput.length());
                            return null;
                        })
                .when(mMockDevice)
                .executeShellCommand(
                        eq("stat -f -c \"%t\" /sdcard"),
                        any(),
                        anyLong(),
                        eq(TimeUnit.MILLISECONDS));
        String[] input = new String[1];
        doAnswer(
                        invocation -> {
                            input[0] = (String) invocation.getArguments()[0];
                            return null;
                        })
                .when(mMockDevice)
                .executeShellCommand(contains("echo"), any(), anyLong(), eq(TimeUnit.MILLISECONDS));
        doAnswer(
                        invocation -> {
                            CollectingOutputReceiver output =
                                    (CollectingOutputReceiver) invocation.getArguments()[1];
                            output.addOutput(input[0].getBytes(), 0, input[0].length());
                            return null;
                        })
                .when(mMockDevice)
                .executeShellCommand(contains("cat"), any(), anyLong(), eq(TimeUnit.MILLISECONDS));
        mMockDevice.executeShellCommand(
                contains("rm"), any(), anyLong(), eq(TimeUnit.MILLISECONDS));

        reset(mMockMgr);
        when(mMockMgr.isFileSystemMountCheckEnabled()).thenReturn(true);

        mMonitor =
                new DeviceStateMonitor(mMockMgr, mMockDevice, true) {
                    @Override
                    public IDevice waitForDeviceOnline(long waitTime) {
                        return mMockDevice;
                    }

                    @Override
                    public boolean waitForBootComplete(long waitTime) {
                        return true;
                    }

                    @Override
                    protected boolean waitForPmResponsive(long waitTime) {
                        return true;
                    }
                };
        assertEquals(mMockDevice, mMonitor.waitForDeviceAvailable(WAIT_TIMEOUT_NOT_REACHED_MS));
    }

    /** Test {@link DeviceStateMonitor#waitForDeviceAvailable(long)} when device is not online. */
    @Test
    public void testWaitForDeviceAvailable_notOnline() throws Exception {
        when(mMockDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);

        mMonitor =
                new DeviceStateMonitor(mMockMgr, mMockDevice, true) {
                    @Override
                    public IDevice waitForDeviceOnline(long waitTime) {
                        return null;
                    }
                };
        assertNull(mMonitor.waitForDeviceAvailable(WAIT_TIMEOUT_NOT_REACHED_MS));
    }

    /**
     * Test {@link DeviceStateMonitor#waitForDeviceAvailable(long)} when device boot is not
     * complete.
     */
    @Test
    public void testWaitForDeviceAvailable_notBootComplete() throws Exception {
        when(mMockDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);

        mMonitor =
                new DeviceStateMonitor(mMockMgr, mMockDevice, true) {
                    @Override
                    public IDevice waitForDeviceOnline(long waitTime) {
                        return mMockDevice;
                    }

                    @Override
                    public boolean waitForBootComplete(long waitTime) {
                        return false;
                    }
                };
        assertNull(mMonitor.waitForDeviceAvailable(WAIT_TIMEOUT_REACHED_MS));
    }

    /** Test {@link DeviceStateMonitor#waitForDeviceAvailable(long)} when pm is not responsive. */
    @Test
    public void testWaitForDeviceAvailable_pmNotResponsive() throws Exception {
        when(mMockDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);

        mMonitor =
                new DeviceStateMonitor(mMockMgr, mMockDevice, true) {
                    @Override
                    public IDevice waitForDeviceOnline(long waitTime) {
                        return mMockDevice;
                    }

                    @Override
                    public boolean waitForBootComplete(long waitTime) {
                        return true;
                    }

                    @Override
                    protected boolean waitForPmResponsive(long waitTime) {
                        return false;
                    }
                };
        assertNull(mMonitor.waitForDeviceAvailable(WAIT_TIMEOUT_REACHED_MS));
    }

    /**
     * Test {@link DeviceStateMonitor#waitForDeviceAvailable(long)} when mount point is not ready
     */
    @Test
    public void testWaitForDeviceAvailable_notMounted() throws Exception {
        when(mMockDevice.getState()).thenReturn(DeviceState.ONLINE);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);

        mMonitor =
                new DeviceStateMonitor(mMockMgr, mMockDevice, true) {
                    @Override
                    public IDevice waitForDeviceOnline(long waitTime) {
                        return mMockDevice;
                    }

                    @Override
                    public boolean waitForBootComplete(long waitTime) {
                        return true;
                    }

                    @Override
                    protected boolean waitForPmResponsive(long waitTime) {
                        return true;
                    }

                    @Override
                    protected boolean waitForStoreMount(long waitTime) {
                        return false;
                    }
                };
        assertNull(mMonitor.waitForDeviceAvailable(WAIT_TIMEOUT_REACHED_MS));
    }

    /** Test {@link DeviceStateMonitor#waitForDeviceInSideload(long)} */
    @Test
    public void testWaitForDeviceInSideload() throws Exception {
        when(mMockDevice.getState()).thenReturn(DeviceState.SIDELOAD);
        when(mMockDevice.getSerialNumber()).thenReturn(SERIAL_NUMBER);

        mMonitor = new DeviceStateMonitor(mMockMgr, mMockDevice, true);
        assertTrue(mMonitor.waitForDeviceInSideload(WAIT_TIMEOUT_NOT_REACHED_MS));
    }
}
