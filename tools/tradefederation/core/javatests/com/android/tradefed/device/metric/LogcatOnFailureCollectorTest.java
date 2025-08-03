/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.device.metric;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.device.CollectingByteOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ILogcatReceiver;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.NullDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.IRunUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;

/** Unit tests for {@link LogcatOnFailureCollector}. */
@RunWith(JUnit4.class)
public class LogcatOnFailureCollectorTest {

    private static final String LOGCAT_MOCK_CONTENT =
            "========== beginning of new [logcat] output ==========\n"
                    + "08-22 18:07:39.018  1927  1927 V GraphicsEnvironment: Currently";

    private TestableLogcatOnFailureCollector mCollector;
    @Mock ITestInvocationListener mMockListener;
    @Mock ITestDevice mMockDevice;
    @Mock ITestDevice mNullMockDevice;

    private ITestInvocationListener mTestListener;
    private IInvocationContext mContext;
    @Mock ILogcatReceiver mMockReceiver;
    @Mock IRunUtil mMockRunUtil;

    private class TestableLogcatOnFailureCollector extends LogcatOnFailureCollector {

        public boolean mOnTestStartCalled = false;
        public boolean mOnTestFailCalled = false;

        @Override
        public void onTestStart(DeviceMetricData testData) {
            super.onTestStart(testData);
            mOnTestStartCalled = true;
        }

        @Override
        public void onTestFail(DeviceMetricData testData, TestDescription test)
                throws DeviceNotAvailableException {
            super.onTestFail(testData, test);
            mOnTestFailCalled = true;
        }

        @Override
        ILogcatReceiver createLogcatReceiver(ITestDevice device) {
            return mMockReceiver;
        }

        @Override
        CollectingByteOutputReceiver createLegacyCollectingReceiver() {
            return new CollectingByteOutputReceiver() {
                @Override
                public byte[] getOutput() {
                    return LOGCAT_MOCK_CONTENT.getBytes();
                }
            };
        }

        @Override
        IRunUtil getRunUtil() {
            return mMockRunUtil;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mCollector = new TestableLogcatOnFailureCollector();
        mContext = new InvocationContext();
        mContext.addAllocatedDevice(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockDevice);
        mContext.addAllocatedDevice("second_null_device", mNullMockDevice);

        when(mMockDevice.getSerialNumber()).thenReturn("serial");
        when(mMockDevice.getIDevice()).thenReturn(mock(IDevice.class));
        when(mMockDevice.getDeviceState()).thenReturn(TestDeviceState.ONLINE);

        when(mNullMockDevice.getIDevice()).thenReturn(new NullDevice("null-dev"));
    }

    @Test
    public void testCollect() throws Exception {
        when(mMockDevice.getApiLevel()).thenReturn(20);

        TestDescription test = new TestDescription("class", "test");

        // Buffer at testRunStarted
        when(mMockReceiver.getLogcatData())
                .thenReturn(new ByteArrayInputStreamSource("aaa".getBytes()));
        // Buffer to be logged
        when(mMockReceiver.getLogcatData(Mockito.anyInt(), Mockito.eq(3)))
                .thenReturn(new ByteArrayInputStreamSource(LOGCAT_MOCK_CONTENT.getBytes()));

        mTestListener = mCollector.init(mContext, mMockListener);
        mTestListener.testRunStarted("runName", 1);
        mTestListener.testStarted(test);
        mTestListener.testFailed(test, "I failed");
        mTestListener.testEnded(test, new HashMap<String, Metric>());
        mTestListener.testRunEnded(0L, new HashMap<String, Metric>());

        verify(mMockReceiver).start();
        verify(mMockReceiver).clear();
        verify(mMockReceiver).stop();
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("runName"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener).testStarted(Mockito.eq(test), Mockito.anyLong());
        verify(mMockListener).testFailed(Mockito.eq(test), (String) Mockito.any());
        verify(mMockListener)
                .testEnded(
                        Mockito.eq(test),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener).testRunEnded(0L, new HashMap<String, Metric>());
        verify(mMockListener)
                .testLog(
                        Mockito.eq("class#test-serial-logcat-on-failure"),
                        Mockito.eq(LogDataType.LOGCAT),
                        Mockito.any());
        // Ensure the callback went through
        assertTrue(mCollector.mOnTestStartCalled);
        assertTrue(mCollector.mOnTestFailCalled);
    }

    /**
     * If the API level support of the device is lower than a threshold we fall back to a different
     * collection for the logcat.
     */
    @Test
    public void testCollect_legacy() throws Exception {
        when(mMockDevice.getApiLevel()).thenReturn(18);

        TestDescription test = new TestDescription("class", "test");

        mTestListener = mCollector.init(mContext, mMockListener);
        mTestListener.testRunStarted("runName", 1);
        mTestListener.testStarted(test);
        mTestListener.testFailed(test, "I failed");
        mTestListener.testEnded(test, new HashMap<String, Metric>());
        mTestListener.testRunEnded(0L, new HashMap<String, Metric>());

        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("runName"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener).testStarted(Mockito.eq(test), Mockito.anyLong());
        verify(mMockListener).testFailed(Mockito.eq(test), (String) Mockito.any());
        verify(mMockListener)
                .testEnded(
                        Mockito.eq(test),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener).testRunEnded(0L, new HashMap<String, Metric>());
        verify(mMockDevice).executeShellCommand(Mockito.eq("logcat -b all -t 5000"), Mockito.any());
        verify(mMockListener)
                .testLog(
                        Mockito.eq("class#test-serial-logcat-on-failure"),
                        Mockito.eq(LogDataType.LOGCAT),
                        Mockito.any());
        // Ensure the callback went through
        assertTrue(mCollector.mOnTestStartCalled);
        assertTrue(mCollector.mOnTestFailCalled);
    }

    @Test
    public void testCollect_noRuns() throws Exception {
        // If there was no runs, nothing should be done.

        mTestListener = mCollector.init(mContext, mMockListener);

        assertFalse(mCollector.mOnTestStartCalled);
        assertFalse(mCollector.mOnTestFailCalled);
    }

    @Test
    public void testCollect_multiRun() throws Exception {
        when(mMockDevice.getApiLevel()).thenReturn(20);

        TestDescription test = new TestDescription("class", "test");
        TestDescription test2 = new TestDescription("class2", "test2");

        // Buffer at testRunStarted
        when(mMockReceiver.getLogcatData())
                .thenReturn(
                        new ByteArrayInputStreamSource("aaa".getBytes()),
                        new ByteArrayInputStreamSource("aaa".getBytes()));
        // Buffer to be logged
        when(mMockReceiver.getLogcatData(Mockito.anyInt(), Mockito.eq(3)))
                .thenReturn(
                        new ByteArrayInputStreamSource(LOGCAT_MOCK_CONTENT.getBytes()),
                        new ByteArrayInputStreamSource(LOGCAT_MOCK_CONTENT.getBytes()));

        mTestListener = mCollector.init(mContext, mMockListener);
        mTestListener.testRunStarted("runName", 1);
        mTestListener.testStarted(test);
        mTestListener.testFailed(test, "I failed");
        mTestListener.testEnded(test, new HashMap<String, Metric>());
        mTestListener.testRunEnded(0L, new HashMap<String, Metric>());
        // Second run
        mTestListener.testRunStarted("runName2", 1);
        mTestListener.testStarted(test2);
        mTestListener.testFailed(test2, "I failed");
        mTestListener.testEnded(test2, new HashMap<String, Metric>());
        mTestListener.testRunEnded(0L, new HashMap<String, Metric>());
        verify(mMockReceiver, times(2)).start();
        verify(mMockReceiver, times(2)).clear();
        verify(mMockReceiver, times(2)).stop();
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("runName"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener).testStarted(Mockito.eq(test), Mockito.anyLong());
        verify(mMockListener).testFailed(Mockito.eq(test), (String) Mockito.any());
        verify(mMockListener)
                .testEnded(
                        Mockito.eq(test),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        // verify testRunEnded for both runs with times(2)
        verify(mMockListener, times(2)).testRunEnded(0L, new HashMap<String, Metric>());
        verify(mMockListener)
                .testLog(
                        Mockito.eq("class#test-serial-logcat-on-failure"),
                        Mockito.eq(LogDataType.LOGCAT),
                        Mockito.any());
        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("runName2"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener).testStarted(Mockito.eq(test2), Mockito.anyLong());
        verify(mMockListener).testFailed(Mockito.eq(test2), (String) Mockito.any());
        verify(mMockListener)
                .testEnded(
                        Mockito.eq(test2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener)
                .testLog(
                        Mockito.eq("class2#test2-serial-logcat-on-failure"),
                        Mockito.eq(LogDataType.LOGCAT),
                        Mockito.any());
        // Ensure the callback went through
        assertTrue(mCollector.mOnTestStartCalled);
        assertTrue(mCollector.mOnTestFailCalled);
    }
}
