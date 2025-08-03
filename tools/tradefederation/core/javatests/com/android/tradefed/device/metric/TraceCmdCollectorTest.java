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

package com.android.tradefed.device.metric;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Unit tests for {@link TraceCmdCollector}, */
@RunWith(JUnit4.class)
public final class TraceCmdCollectorTest {
    @Mock ITestDevice mMockDevice;
    private TraceCmdCollector mTraceCmd;
    private OptionSetter mOptionSetter;
    @Mock ITestInvocationListener mMockTestLogger;
    @Mock IInvocationContext mMockInvocationContext;
    private String mDefaultLogPath = "/data/local/tmp/atrace.dat";
    private String mTraceCmdPath = "/data/local/tmp/trace-cmd";
    private String mSerialNo = "12349876";
    private String mCategories = "freq batterystats";
    private String mTraceCmdOptions = "-e chamomille -e chrysanthemum";

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mTraceCmd = new TraceCmdCollector();
        mOptionSetter = new OptionSetter(mTraceCmd);
        mOptionSetter.setOptionValue("categories", mCategories);

        when(mMockInvocationContext.getDevices()).thenReturn(Arrays.asList(mMockDevice));

        mTraceCmd.init(mMockInvocationContext, mMockTestLogger);
    }

    /**
     * Test {@link TraceCmdCollector#onTestStart(DeviceMetricData)} to see if trace collection
     * started with trace-cmd and atrace correctly.
     *
     * <p>Expect that atrace and trace-cmd commands were both issued with their appropriate
     * arguments.
     */
    @Test
    public void testStartsAtraceAndTraceCmdOptions() throws Exception {
        mOptionSetter.setOptionValue("trace-cmd-binary", mTraceCmdPath);
        mOptionSetter.setOptionValue("trace-cmd-recording-args", mTraceCmdOptions);

        mTraceCmd.onTestStart(new DeviceMetricData(mMockInvocationContext));
        verify(mMockDevice, times(1))
                .executeShellCommand(
                        Mockito.eq("atrace --async_start -z " + mCategories),
                        Mockito.any(),
                        Mockito.eq(1L),
                        Mockito.any(),
                        Mockito.eq(1));
        verify(mMockDevice, times(1))
                .executeShellCommand(
                        Mockito.eq("chmod +x " + mTraceCmdPath),
                        Mockito.any(),
                        Mockito.eq(1L),
                        Mockito.any(),
                        Mockito.eq(1));
    }

    /**
     * Test {@link TraceCmdCollector#onTestStart(DeviceMetricData)} to see if trace collection
     * failure is handled correctly.
     *
     * <p>Expect that atrace continues on if trace-cmd cant be found on the device.
     */
    @Test
    public void testStartsAtraceAndTraceCmdFails() throws Exception {
        doThrow(new DeviceNotAvailableException("test", "serial"))
                .when(mMockDevice)
                .executeShellCommand(
                        (String) Mockito.any(),
                        Mockito.any(),
                        Mockito.eq(1L),
                        Mockito.any(),
                        Mockito.eq(1));

        mOptionSetter.setOptionValue("trace-cmd-binary", mTraceCmdPath);

        try {
            mTraceCmd.onTestStart(new DeviceMetricData(mMockInvocationContext));
            fail("Should have thrown an exception.");
        } catch (DeviceNotAvailableException expected) {
            // Expected
        }
    }

    /**
     * Test {@link TraceCmdCollector#onTestEnd(DeviceMetricData, Map)} to see if trace-cmd
     * collection stopped correctly.
     *
     * <p>Expect that trace-cmd was stopped, the trace file was pulled from device to host and the
     * trace file removed from device.
     */
    @Test
    public void testStopsTraceCmdDuringTearDown() throws Exception {
        // wait won't work, as trace-cmd was ran with nohup in a different session.
        TestDescription test = new TestDescription("class", "test");
        when(mMockDevice.pullFile(Mockito.eq(mDefaultLogPath))).thenReturn(new File("/tmp/potato"));

        mOptionSetter.setOptionValue("trace-cmd-binary", "trc");
        mTraceCmd.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, Metric>(), test);
        verify(mMockDevice, times(1))
                .executeShellCommand(
                        Mockito.eq(
                                "for PID in $(pidof trace-cmd); do while kill -s sigint $PID; do"
                                        + " sleep 0.3; done; done;"),
                        Mockito.any(),
                        Mockito.eq(60L),
                        Mockito.any(),
                        Mockito.eq(1));
        verify(mMockDevice, times(1)).pullFile(Mockito.eq(mDefaultLogPath));
    }

    /**
     * Test {@link TraceCmdCollector#onTestEnd(DeviceMetricData, Map)} to see that it uploads its
     * file correctly when using trace-cmd.
     *
     * <p>Expect that the log is uploaded with the proper filename and LogDataType.
     */
    @Test
    public void testUploadslogWithRawKernelBuffer() throws Exception {
        TestDescription test = new TestDescription("class", "test");
        when(mMockDevice.pullFile((String) Mockito.any())).thenReturn(new File("/tmp/potato"));
        when(mMockDevice.getSerialNumber()).thenReturn(mSerialNo);

        mOptionSetter.setOptionValue("trace-cmd-binary", "trace-cmd");
        mTraceCmd.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, Metric>(), test);

        verify(mMockTestLogger, times(1))
                .testLog(
                        Mockito.eq("atrace_class#test" + mSerialNo + "_"),
                        Mockito.eq(LogDataType.KERNEL_TRACE),
                        Mockito.any());
    }
}
