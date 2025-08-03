/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tradefed.testtype;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IDeviceMonitor;
import com.android.tradefed.device.IDeviceStateMonitor;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.util.IRunUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Unit tests for {@link DeviceBatteryLevelChecker}. */
@RunWith(JUnit4.class)
public class DeviceBatteryLevelCheckerTest {

    private DeviceBatteryLevelChecker mChecker = null;
    private ITestDevice mDevice = null;
    private TestInformation mTestInfo = null;
    @Mock ITestDevice mFakeTestDevice;
    public AtomicInteger mBatteryLevel = new AtomicInteger(10);
    private TestDescription mTestDescription = new TestDescription("BatteryCharging", "charge");
    private TestDescription mTestDescription2 = new TestDescription("BatteryCharging", "speed");

    @Mock IDevice mMockIDevice;
    @Mock IDeviceStateMonitor mMockStateMonitor;
    @Mock IDeviceMonitor mMockDvcMonitor;
    @Mock ITestInvocationListener mMockListener;

    private boolean mFirstCall = true;

    /** A {@link TestDevice} that is suitable for running tests against */
    private class TestableTestDevice extends TestDevice {
        public TestableTestDevice() {
            super(mMockIDevice, mMockStateMonitor, mMockDvcMonitor);
        }

        @Override
        public String getSerialNumber() {
            return mFakeTestDevice.getSerialNumber();
        }

        @Override
        public void stopLogcat() {
            mFakeTestDevice.stopLogcat();
        }

        @Override
        public String executeShellCommand(String command) throws DeviceNotAvailableException {
            return mFakeTestDevice.executeShellCommand(command);
        }

        @Override
        public void executeShellCommand(
                final String command,
                final IShellOutputReceiver receiver,
                final long maxTimeToOutputShellResponse,
                final TimeUnit timeUnit,
                final int retryAttempts)
                throws DeviceNotAvailableException {
            String bugreport = "bugreport";
            if (command.equals(bugreport)) {
                receiver.addOutput(bugreport.getBytes(), 0, bugreport.length());
            }
        }

        @Override
        public boolean logBugreport(String dataName, ITestLogger listener) {
            return mFakeTestDevice.logBugreport(dataName, listener);
        }

        @Override
        public Integer getBattery() {
            return mBatteryLevel.get() == -99 ? null : mBatteryLevel.get();
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mDevice = new TestableTestDevice();
        mChecker =
                new DeviceBatteryLevelChecker() {
                    @Override
                    IRunUtil getRunUtil() {
                        return mock(IRunUtil.class);
                    }

                    @Override
                    long getCurrentTimeMs() {
                        if (mFirstCall) {
                            mFirstCall = false;
                            return 0L;
                        }
                        return 10L;
                    }
                };
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device", mDevice);
        mTestInfo = TestInformation.newBuilder().setInvocationContext(context).build();
        when(mFakeTestDevice.getSerialNumber()).thenReturn("SERIAL");
        when(mMockIDevice.supportsFeature(IDevice.Feature.SHELL_V2)).thenReturn(true);
    }

    @Test
    public void testNull() throws Exception {
        expectBattLevel(null);

        mChecker.run(mTestInfo, mMockListener);
        // expect this to return immediately without throwing an exception.  Should log a warning.
        verify(mMockListener).testRunStarted("BatteryCharging", 2);
        verify(mMockListener).testStarted(mTestDescription);
        verify(mMockListener)
                .testFailed(
                        mTestDescription,
                        FailureDescription.create("Failed to determine battery level"));
        verify(mMockListener).testEnded(mTestDescription, new HashMap<String, Metric>());
        verify(mMockListener).testStarted(mTestDescription2);
        FailureDescription failure = FailureDescription.create("No battery charge information");
        failure.setFailureStatus(FailureStatus.NOT_EXECUTED);
        verify(mMockListener).testFailed(mTestDescription2, failure);
        verify(mMockListener).testEnded(mTestDescription2, new HashMap<String, Metric>());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testNormal() throws Exception {
        expectBattLevel(45);

        mChecker.run(mTestInfo, mMockListener);

        verify(mMockListener).testRunStarted("BatteryCharging", 2);
        verify(mMockListener).testStarted(mTestDescription);
        verify(mMockListener).testEnded(mTestDescription, new HashMap<String, Metric>());
        verify(mMockListener).testStarted(mTestDescription2);
        verify(mMockListener).testIgnored(mTestDescription2);
        verify(mMockListener).testEnded(mTestDescription2, new HashMap<String, Metric>());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Low battery with a resume level very low to check a resume if some level are reached. */
    @Test
    public void testLow() throws Exception {
        expectBattLevel(5);
        when(mFakeTestDevice.executeShellCommand("svc power stayon false")).thenReturn("");
        when(mFakeTestDevice.executeShellCommand("settings put system screen_off_timeout 1000"))
                .thenReturn("");
        when(mFakeTestDevice.logBugreport(
                        Mockito.eq("low-charging-speed-bugreport"), Mockito.any()))
                .thenReturn(true);

        mChecker.setResumeLevel(5);
        mChecker.run(mTestInfo, mMockListener);

        verify(mMockListener).testRunStarted("BatteryCharging", 2);
        verify(mMockListener).testStarted(mTestDescription);
        verify(mMockListener).testEnded(mTestDescription, new HashMap<String, Metric>());
        verify(mMockListener).testStarted(mTestDescription2);
        verify(mMockListener)
                .testFailed(Mockito.eq(mTestDescription2), Mockito.<FailureDescription>any());
        verify(mMockListener).testEnded(mTestDescription2, new HashMap<String, Metric>());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mFakeTestDevice, times(1)).stopLogcat();
    }

    /** Battery is low, device idles and battery gets high again. */
    @Test
    public void testLow_becomeHigh() throws Exception {
        expectBattLevel(5);
        when(mFakeTestDevice.executeShellCommand("svc power stayon false")).thenReturn("");
        when(mFakeTestDevice.executeShellCommand("settings put system screen_off_timeout 1000"))
                .thenReturn("");
        Thread raise =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Thread.sleep(100);
                                    expectBattLevel(85);
                                } catch (Exception e) {
                                    CLog.e(e);
                                }
                            }
                        });
        raise.start();
        mChecker.run(mTestInfo, mMockListener);

        verify(mMockListener).testRunStarted("BatteryCharging", 2);
        verify(mMockListener).testStarted(mTestDescription);
        verify(mMockListener).testEnded(mTestDescription, new HashMap<String, Metric>());
        verify(mMockListener).testStarted(mTestDescription2);
        verify(mMockListener).testEnded(mTestDescription2, new HashMap<String, Metric>());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mFakeTestDevice, times(1)).stopLogcat();
    }

    /** Battery is low, device idles and battery gets null, break the loop. */
    @Test
    public void testLow_becomeNull() throws Exception {
        expectBattLevel(5);
        when(mFakeTestDevice.executeShellCommand("svc power stayon false")).thenReturn("");
        when(mFakeTestDevice.executeShellCommand("settings put system screen_off_timeout 1000"))
                .thenReturn("");
        Thread raise =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Thread.sleep(50);
                                    expectBattLevel(null);
                                } catch (Exception e) {
                                    CLog.e(e);
                                }
                            }
                        });
        raise.start();
        mChecker.run(mTestInfo, mMockListener);

        verify(mMockListener).testRunStarted("BatteryCharging", 2);
        verify(mMockListener).testStarted(mTestDescription);
        verify(mMockListener)
                .testFailed(
                        mTestDescription,
                        FailureDescription.create("Failed to read battery level"));
        verify(mMockListener).testEnded(mTestDescription, new HashMap<String, Metric>());
        verify(mMockListener).testStarted(mTestDescription2);
        FailureDescription failure = FailureDescription.create("No battery charge information");
        failure.setFailureStatus(FailureStatus.NOT_EXECUTED);
        verify(mMockListener).testFailed(mTestDescription2, failure);
        verify(mMockListener).testEnded(mTestDescription2, new HashMap<String, Metric>());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        verify(mFakeTestDevice, times(1)).stopLogcat();
    }

    private void expectBattLevel(Integer level) throws Exception {
        if (level == null) {
            level = -99;
        }
        mBatteryLevel.set(level);
    }
}
