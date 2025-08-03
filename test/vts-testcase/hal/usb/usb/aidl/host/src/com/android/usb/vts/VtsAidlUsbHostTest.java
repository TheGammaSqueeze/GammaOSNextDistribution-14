/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tests.usbport;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;
import com.android.tradefed.util.RunInterruptedException;
import com.android.tradefed.util.RunUtil;
import com.google.common.base.Strings;

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class VtsAidlUsbHostTest extends BaseHostJUnit4Test {
    public static final String TAG = VtsAidlUsbHostTest.class.getSimpleName();

    private static final String HAL_SERVICE = "android.hardware.usb.IUsb/default";
    private static final long CONN_TIMEOUT = 5000;

    private static boolean mHasService;

    private ITestDevice mDevice;
    private AtomicBoolean mReconnected = new AtomicBoolean(false);

    @Before
    public void setUp() {
        mDevice = getDevice();
    }

    @BeforeClassWithInfo
    public static void beforeClassWithDevice(TestInformation testInfo) throws Exception {
        String serviceFound =
                testInfo.getDevice()
                        .executeShellCommand(String.format("dumpsys -l | grep \"%s\"", HAL_SERVICE))
                        .trim();
        mHasService = !Strings.isNullOrEmpty(serviceFound);
    }

    @Test
    public void testResetUsbPort() throws Exception {
        Assume.assumeTrue(
                String.format("The device doesn't have service %s", HAL_SERVICE), mHasService);
        Assert.assertNotNull("Target device does not exist", mDevice);

        String portResult, content;
        String deviceSerialNumber = mDevice.getSerialNumber();
        HashSet<String> noSupportCases =
                    new HashSet<>(Arrays.asList("No USB ports",
                        "There is no available reset USB port"));

        CLog.i("testResetUsbPort on device [%s]", deviceSerialNumber);

        new Thread(new Runnable() {
            public void run() {
                try {
                    mDevice.waitForDeviceNotAvailable(CONN_TIMEOUT);
                    RunUtil.getDefault().sleep(500);
                    mDevice.waitForDeviceAvailable(CONN_TIMEOUT);
                    mReconnected.set(true);
                } catch (DeviceNotAvailableException dnae) {
                    CLog.e("Device is not available");
                } catch (RunInterruptedException ie) {
                    CLog.w("Thread.sleep interrupted");
                }
            }
        }).start();

        RunUtil.getDefault().sleep(100);
        String cmd = "svc usb resetUsbPort";
        CLog.i("Invoke shell command [" + cmd + "]");
        long startTime = System.currentTimeMillis();
        portResult = mDevice.executeShellCommand(cmd);
        content = portResult.trim();

        if (portResult != null && (noSupportCases.contains(content))) {
            CLog.i("portResult: %s", portResult);
            return;
        }

        RunUtil.getDefault().sleep(100);
        while (!mReconnected.get() && System.currentTimeMillis() - startTime < CONN_TIMEOUT) {
            RunUtil.getDefault().sleep(300);
        }

        Assert.assertTrue("usb not reconnect", mReconnected.get());
    }
}
