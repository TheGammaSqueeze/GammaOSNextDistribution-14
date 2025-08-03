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
package com.android.tradefed.util.sl4a;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.IRunUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;

/** Test class for {@link Sl4aClient}. */
@RunWith(JUnit4.class)
public class Sl4aClientTest {

    private static final String DEVICE_SERIAL = "54321";
    private Sl4aClient mClient = null;
    private FakeSocketServerHelper mDeviceServer;
    @Mock ITestDevice mMockDevice;
    @Mock IRunUtil mMockRunUtil;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);

        mDeviceServer = new FakeSocketServerHelper();
        mClient =
                new Sl4aClient(mMockDevice, mDeviceServer.getPort(), mDeviceServer.getPort()) {
                    @Override
                    protected IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    protected void startEventDispatcher() throws DeviceNotAvailableException {
                        // ignored
                    }
                };
    }

    @After
    public void tearDown() throws IOException {
        if (mDeviceServer != null) {
            mDeviceServer.close();
        }
    }

    /** Test for {@link Sl4aClient#isSl4ARunning()} when sl4a is running. */
    @Test
    public void testIsSl4ARunning() throws DeviceNotAvailableException {
        when(mMockDevice.executeShellCommand(Sl4aClient.IS_SL4A_RUNNING_CMD))
                .thenReturn(
                        "system    3968   452 1127644  49448 epoll_wait   ae1217f8 S "
                                + "com.googlecode.android_scripting");
        when(mMockDevice.executeShellCommand(Sl4aClient.IS_SL4A_RUNNING_CMD_OLD))
                .thenReturn(
                        "system    3968   452 1127644  49448 epoll_wait   ae1217f8 S "
                                + "com.googlecode.android_scripting");

        Assert.assertTrue(mClient.isSl4ARunning());
    }

    /** Test for {@link Sl4aClient#isSl4ARunning()} when sl4a is not running. */
    @Test
    public void testIsSl4ARunning_notRunning() throws DeviceNotAvailableException {
        when(mMockDevice.executeShellCommand(Sl4aClient.IS_SL4A_RUNNING_CMD)).thenReturn("");
        when(mMockDevice.executeShellCommand(Sl4aClient.IS_SL4A_RUNNING_CMD_OLD)).thenReturn("");

        Assert.assertFalse(mClient.isSl4ARunning());
    }

    /** Test for {@link Sl4aClient#startSl4A()} when sl4a does not starts properly. */
    @Test
    public void testStartSl4A_notRunning() throws DeviceNotAvailableException {
        final String cmd = String.format(Sl4aClient.SL4A_LAUNCH_CMD, mDeviceServer.getPort());
        when(mMockDevice.executeShellCommand(cmd)).thenReturn("");
        when(mMockDevice.executeShellCommand(Sl4aClient.IS_SL4A_RUNNING_CMD)).thenReturn("");
        when(mMockDevice.executeShellCommand(Sl4aClient.IS_SL4A_RUNNING_CMD_OLD)).thenReturn("");

        try {
            mClient.startSl4A();
            Assert.fail("Should have thrown an exception");
        } catch (RuntimeException expected) {
            // expected
        }
        verify(mMockRunUtil, times(1)).sleep(Mockito.anyLong());
    }

    /** Helper to set the mocks and expectation to starts SL4A. */
    private void setupStartExpectation() throws DeviceNotAvailableException {
        final String cmd = String.format(Sl4aClient.SL4A_LAUNCH_CMD, mDeviceServer.getPort());
        when(mMockDevice.getSerialNumber()).thenReturn(DEVICE_SERIAL);
        when(mMockDevice.executeShellCommand(cmd)).thenReturn("");
        when(mMockDevice.executeShellCommand(Sl4aClient.IS_SL4A_RUNNING_CMD))
                .thenReturn(
                        "system    3968   452 1127644  49448 epoll_wait   ae1217f8 S "
                                + "com.googlecode.android_scripting");
        when(mMockDevice.executeShellCommand(Sl4aClient.IS_SL4A_RUNNING_CMD_OLD))
                .thenReturn(
                        "system    3968   452 1127644  49448 epoll_wait   ae1217f8 S "
                                + "com.googlecode.android_scripting");
        when(mMockDevice.executeShellCommand(Sl4aClient.STOP_SL4A_CMD)).thenReturn("");
        when(mMockDevice.executeAdbCommand(
                        "forward",
                        "tcp:" + mDeviceServer.getPort(),
                        "tcp:" + mDeviceServer.getPort()))
                .thenReturn("");
        when(mMockDevice.executeAdbCommand("forward", "--list")).thenReturn("");
        when(mMockDevice.executeAdbCommand("forward", "--remove", "tcp:" + mDeviceServer.getPort()))
                .thenReturn("");
    }

    /** Test for {@link Sl4aClient#startSl4A()} when sl4a does starts properly. */
    @Test
    public void testStartSl4A() throws DeviceNotAvailableException {
        mDeviceServer.start();
        setupStartExpectation();

        try {
            mClient.startSl4A();
        } finally {
            mClient.close();
        }
        verify(mMockRunUtil, times(1)).sleep(Mockito.anyLong());
    }

    /**
     * Test for {@link Sl4aClient#rpcCall(String, Object...)} and the response parsing for a boolean
     * result.
     */
    @Test
    public void testRpcCall_booleanResponse() throws DeviceNotAvailableException, IOException {
        mDeviceServer.start();
        setupStartExpectation();

        try {
            mClient.startSl4A();
            Object rep = mClient.rpcCall("getBoolean", false);
            Assert.assertEquals(true, rep);
        } finally {
            mClient.close();
        }
        verify(mMockRunUtil, times(1)).sleep(Mockito.anyLong());
    }

    /**
     * Test for {@link Sl4aClient#startSL4A(ITestDevice, File)} throws an exception if sl4a apk
     * provided does not exist.
     */
    @Test
    public void testCreateSl4aClient() throws Exception {
        final String fakePath = "/fake/random/path";

        try {
            Sl4aClient.startSL4A(mMockDevice, new File(fakePath));
            fail("Should have thrown an exception");
        } catch (RuntimeException expected) {
            assertEquals(
                    String.format("Sl4A apk '%s' was not found.", fakePath), expected.getMessage());
        }
    }
}
