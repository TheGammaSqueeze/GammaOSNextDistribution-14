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
package com.android.tradefed.device.connection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.connection.DefaultConnection.ConnectionBuilder;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link AdbTcpConnection}. */
@RunWith(JUnit4.class)
public class AdbTcpConnectionTest {

    private static final String MOCK_DEVICE_SERIAL = "localhost:1234";

    private AdbTcpConnection mConnection;
    @Mock ITestDevice mMockDevice;
    @Mock IRunUtil mMockRunUtil;
    @Mock IBuildInfo mMockBuildInfo;
    @Mock ITestLogger mMockLogger;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockDevice.getSerialNumber()).thenReturn(MOCK_DEVICE_SERIAL);

        mConnection =
                new AdbTcpConnection(
                        new ConnectionBuilder(
                                mMockRunUtil, mMockDevice, mMockBuildInfo, mMockLogger));
    }

    /** Test {@link AdbTcpConnection#adbTcpConnect(String, String)} in a success case. */
    @Test
    public void testAdbConnect() {
        CommandResult adbResult = new CommandResult();
        adbResult.setStatus(CommandStatus.SUCCESS);
        adbResult.setStdout("connected to");
        CommandResult adbResultConfirmation = new CommandResult();
        adbResultConfirmation.setStatus(CommandStatus.SUCCESS);
        adbResultConfirmation.setStdout("already connected to localhost:1234");
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("adb"),
                        Mockito.eq("connect"),
                        Mockito.eq(MOCK_DEVICE_SERIAL)))
                .thenReturn(adbResult);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("adb"),
                        Mockito.eq("connect"),
                        Mockito.eq(MOCK_DEVICE_SERIAL)))
                .thenReturn(adbResultConfirmation);

        assertTrue(mConnection.adbTcpConnect("localhost", "1234"));
    }

    /** Test {@link AdbTcpConnection#adbTcpConnect(String, String)} in a failure case. */
    @Test
    public void testAdbConnect_fails() {
        CommandResult adbResult = new CommandResult();
        adbResult.setStatus(CommandStatus.SUCCESS);
        adbResult.setStdout("cannot connect");
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("adb"),
                        Mockito.eq("connect"),
                        Mockito.eq(MOCK_DEVICE_SERIAL)))
                .thenReturn(adbResult);

        assertFalse(mConnection.adbTcpConnect("localhost", "1234"));
        verify(mMockRunUtil, times(AdbTcpConnection.MAX_RETRIES))
                .runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("adb"),
                        Mockito.eq("connect"),
                        Mockito.eq(MOCK_DEVICE_SERIAL));
        verify(mMockRunUtil, times(AdbTcpConnection.MAX_RETRIES)).sleep(Mockito.anyLong());
    }

    /**
     * Test {@link AdbTcpConnection#adbTcpConnect(String, String)} in a case where adb connect
     * always return connect success (never really connected so confirmation: "already connected"
     * fails.
     */
    @Test
    public void testAdbConnect_fails_confirmation() {
        CommandResult adbResult = new CommandResult();
        adbResult.setStatus(CommandStatus.SUCCESS);
        adbResult.setStdout("connected to");
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("adb"),
                        Mockito.eq("connect"),
                        Mockito.eq(MOCK_DEVICE_SERIAL)))
                .thenReturn(adbResult);

        assertFalse(mConnection.adbTcpConnect("localhost", "1234"));
        verify(mMockRunUtil, times(AdbTcpConnection.MAX_RETRIES * 2))
                .runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("adb"),
                        Mockito.eq("connect"),
                        Mockito.eq(MOCK_DEVICE_SERIAL));
        verify(mMockRunUtil, times(AdbTcpConnection.MAX_RETRIES)).sleep(Mockito.anyLong());
    }

    /** Test {@link AdbTcpConnection#adbTcpDisconnect(String, String)}. */
    @Test
    public void testAdbDisconnect() {
        CommandResult adbResult = new CommandResult();
        adbResult.setStatus(CommandStatus.SUCCESS);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("adb"),
                        Mockito.eq("disconnect"),
                        Mockito.eq(MOCK_DEVICE_SERIAL)))
                .thenReturn(adbResult);

        assertTrue(mConnection.adbTcpDisconnect("localhost", "1234"));
    }

    /** Test {@link AdbTcpConnection#adbTcpDisconnect(String, String)} in a failure case. */
    @Test
    public void testAdbDisconnect_fails() {
        CommandResult adbResult = new CommandResult();
        adbResult.setStatus(CommandStatus.FAILED);
        when(mMockRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("adb"),
                        Mockito.eq("disconnect"),
                        Mockito.eq(MOCK_DEVICE_SERIAL)))
                .thenReturn(adbResult);

        assertFalse(mConnection.adbTcpDisconnect("localhost", "1234"));
    }

    @Test
    public void testCheckSerial() {
        assertEquals("localhost", mConnection.getHostName(MOCK_DEVICE_SERIAL));
        assertEquals("1234", mConnection.getPortNum(MOCK_DEVICE_SERIAL));
    }

    @Test
    public void testCheckSerial_invalid() {
        try {
            mConnection.getHostName("wrongserial");
        } catch (RuntimeException e) {
            // expected
            return;
        }
        fail("Wrong Serial should throw a RuntimeException");
    }

    /** Reject placeholder style device */
    @Test
    public void testCheckSerial_placeholder() {
        try {
            mConnection.getHostName("gce-device:3");
        } catch (RuntimeException e) {
            // expected
            return;
        }
        fail("Wrong Serial should throw a RuntimeException");
    }
}
