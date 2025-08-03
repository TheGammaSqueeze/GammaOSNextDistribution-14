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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.util.RunUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Unit Tests for {@link BackgroundDeviceAction}. */
@RunWith(JUnit4.class)
public class BackgroundDeviceActionTest {

    private static final String MOCK_DEVICE_SERIAL = "serial";
    private static final int SHORT_WAIT_TIME_MS = 100;
    private static final int LONG_WAIT_TIME_MS = 200;
    private static final long JOIN_WAIT_TIME_MS = 5000;

    @Mock IShellOutputReceiver mMockReceiver;
    @Mock IDevice mMockIDevice;
    @Mock ITestDevice mMockTestDevice;

    private BackgroundDeviceAction mBackgroundAction;

    private TestDeviceState mDeviceState = TestDeviceState.ONLINE;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mDeviceState = TestDeviceState.ONLINE;

        when(mMockIDevice.getSerialNumber()).thenReturn(MOCK_DEVICE_SERIAL);

        when(mMockTestDevice.getSerialNumber()).thenReturn(MOCK_DEVICE_SERIAL);
        when(mMockTestDevice.getIDevice()).thenReturn(mMockIDevice);
    }

    /**
     * test {@link BackgroundDeviceAction#run()} should properly run and stop following the thread
     * life cycle
     */
    @Test
    public void testBackgroundActionComplete() throws Exception {
        String action = "";
        when(mMockTestDevice.getDeviceState()).thenReturn(TestDeviceState.ONLINE);

        mBackgroundAction =
                new BackgroundDeviceAction(action, "desc", mMockTestDevice, mMockReceiver, 0);
        mBackgroundAction.start();
        RunUtil.getDefault().sleep(SHORT_WAIT_TIME_MS);
        assertTrue(mBackgroundAction.isAlive());
        mBackgroundAction.cancel();
        mBackgroundAction.join(JOIN_WAIT_TIME_MS);
        assertFalse(mBackgroundAction.isAlive());
        mBackgroundAction.interrupt();
    }

    /**
     * test {@link BackgroundDeviceAction#run()} if shell throw an exception, thread will not
     * terminate but will go through {@link BackgroundDeviceAction#waitForDeviceRecovery(String)}
     */
    @Test
    public void testBackgroundAction_shellException() throws Exception {
        String action = "";
        when(mMockTestDevice.getDeviceState()).thenReturn(mDeviceState);
        doThrow(new IOException())
                .when(mMockIDevice)
                .executeShellCommand(
                        Mockito.eq(action),
                        Mockito.same(mMockReceiver),
                        Mockito.anyLong(),
                        Mockito.eq(TimeUnit.MILLISECONDS));

        mBackgroundAction =
                new BackgroundDeviceAction(action, "desc", mMockTestDevice, mMockReceiver, 0) {
                    @Override
                    protected void waitForDeviceRecovery(String exceptionType) {
                        mDeviceState = TestDeviceState.NOT_AVAILABLE;
                    }

                    @Override
                    public synchronized boolean isCancelled() {
                        return super.isCancelled();
                    }
                };
        mBackgroundAction.start();
        RunUtil.getDefault().sleep(LONG_WAIT_TIME_MS);
        assertTrue(mBackgroundAction.isAlive());
        mBackgroundAction.cancel();
        mBackgroundAction.join(JOIN_WAIT_TIME_MS);
        assertFalse(mBackgroundAction.isAlive());
        assertEquals(TestDeviceState.NOT_AVAILABLE, mDeviceState);
        mBackgroundAction.interrupt();
    }

    /**
     * test {@link BackgroundDeviceAction#waitForDeviceRecovery(String)} should not block if device
     * is online.
     */
    @Test
    public void testwaitForDeviceRecovery_online() throws Exception {
        String action = "";
        when(mMockTestDevice.getDeviceState()).thenReturn(mDeviceState);

        mBackgroundAction =
                new BackgroundDeviceAction(action, "desc", mMockTestDevice, mMockReceiver, 0);
        Thread test =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                mBackgroundAction.waitForDeviceRecovery("IOException");
                            }
                        });
        test.setName(getClass().getCanonicalName() + "#testwaitForDeviceRecovery_online");
        test.start();
        // Specify a timeout for join, not to be stuck if broken.
        test.join(JOIN_WAIT_TIME_MS);
        assertFalse(test.isAlive());
        test.interrupt();
    }

    /**
     * test {@link BackgroundDeviceAction#waitForDeviceRecovery(String)} should block if device is
     * offline.
     */
    @Test
    public void testwaitForDeviceRecovery_blockOffline() throws Exception {
        String action = "";
        mDeviceState = TestDeviceState.NOT_AVAILABLE;
        when(mMockTestDevice.getDeviceState()).thenReturn(mDeviceState);

        mBackgroundAction =
                new BackgroundDeviceAction(action, "desc", mMockTestDevice, mMockReceiver, 0);
        Thread test =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                mBackgroundAction.waitForDeviceRecovery("IOException");
                            }
                        });
        test.setName(getClass().getCanonicalName() + "#testwaitForDeviceRecovery_blockOffline");
        test.start();
        // Specify a timeout for join, not to be stuck.
        test.join(LONG_WAIT_TIME_MS);
        // Thread should still be alive.
        assertTrue(test.isAlive());
        mBackgroundAction.cancel();
        test.interrupt();
    }
}
