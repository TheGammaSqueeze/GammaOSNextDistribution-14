/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link AtraceCollector}, */
@RunWith(JUnit4.class)
public final class AtraceCollectorTest {
    @Mock ITestDevice mMockDevice;
    private AtraceCollector mAtrace;
    private OptionSetter mOptionSetter;
    @Mock ITestInvocationListener mMockTestLogger;
    @Mock IInvocationContext mMockInvocationContext;
    @Mock IRunUtil mMockRunUtil;
    private File mDummyBinary;
    private File mDummyMetricPng;
    private File mDummyMetricText;
    private static final String M_DEFAULT_LOG_PATH = "/data/local/tmp/atrace.atr";
    private static final String M_SERIAL_NO = "12349876";
    private static final String M_CATEGORIES = "tisket tasket brisket basket";
    private static final String M_TRACE_PATH_NAME = "/tmp/traces.txt";

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mAtrace = new AtraceCollector();
        mOptionSetter = new OptionSetter(mAtrace);
        mOptionSetter.setOptionValue("categories", M_CATEGORIES);

        when(mMockDevice.pullFile((String) Mockito.any())).thenReturn(new File(M_TRACE_PATH_NAME));
        when(mMockDevice.getSerialNumber()).thenReturn(M_SERIAL_NO);

        when(mMockInvocationContext.getDevices()).thenReturn(Arrays.asList(mMockDevice));

        mAtrace.init(mMockInvocationContext, mMockTestLogger);
        mDummyBinary = File.createTempFile("tmp", "bin");
        mDummyBinary.setExecutable(true);
        mDummyMetricPng = File.createTempFile("tmp", ".png");
        mDummyMetricText = File.createTempFile("tmp", ".txt");
    }

    @After
    public void tearDown() throws Exception {
        mDummyMetricText.delete();
        mDummyMetricPng.delete();
        mDummyBinary.delete();
    }

    /**
     * Test {@link AtraceCollector#onTestStart(DeviceMetricData)} to see if atrace collection
     * started correctly.
     *
     * <p>Expect that atrace was started in async mode with compression on.
     */
    @Test
    public void testStartsAtraceOnSetupNoOptions() throws Exception {

        mAtrace.onTestStart(new DeviceMetricData(mMockInvocationContext));
        verify(mMockDevice, times(1))
                .executeShellCommand(
                        Mockito.eq("atrace --async_start -z " + M_CATEGORIES),
                        Mockito.any(),
                        Mockito.eq(1L),
                        Mockito.any(),
                        Mockito.eq(1));
    }

    /**
     * Test {@link AtraceCollector#onTestStart(DeviceMetricData)} to see if atrace collection
     * started correctly when the compress-dump option is false.
     *
     * <p>Expect that atrace was started in async mode with compression off.
     */
    @Test
    public void testStartsAtraceOnSetupNoCompression() throws Exception {

        mOptionSetter.setOptionValue("compress-dump", "false");
        mAtrace.onTestStart(new DeviceMetricData(mMockInvocationContext));
        verify(mMockDevice, times(1))
                .executeShellCommand(
                        Mockito.eq("atrace --async_start " + M_CATEGORIES),
                        Mockito.any(),
                        Mockito.eq(1L),
                        Mockito.any(),
                        Mockito.eq(1));
    }

    /**
     * Test {@link AtraceCollector#onTestStart(DeviceMetricData)} to see if atrace collection
     * started correctly with some tracing categories.
     *
     * <p>Expect that supplied categories options were included in the command when starting atrace.
     */
    @Test
    public void testStartsAtraceOnSetupCategoriesOption() throws Exception {

        mAtrace.onTestStart(new DeviceMetricData(mMockInvocationContext));
        verify(mMockDevice, times(1))
                .executeShellCommand(
                        Mockito.eq("atrace --async_start -z " + M_CATEGORIES),
                        Mockito.any(),
                        Mockito.eq(1L),
                        Mockito.any(),
                        Mockito.eq(1));
    }

    /**
     * Test {@link AtraceCollector#onTestStart(DeviceMetricData)} to see if atrace collection
     * started correctly with multiple tracing categories.
     *
     * <p>Expect that supplied categories options were included in the command when starting atrace.
     */
    @Test
    public void testStartsAtraceOnSetupMultipleCategoriesOption() throws Exception {
        String freqCategory = "freq";
        String schedCategory = "sched";
        String expectedCategories = M_CATEGORIES + " " + freqCategory + " " + schedCategory;

        mOptionSetter.setOptionValue("categories", freqCategory);
        mOptionSetter.setOptionValue("categories", schedCategory);
        mAtrace.onTestStart(new DeviceMetricData(mMockInvocationContext));
        verify(mMockDevice, times(1))
                .executeShellCommand(
                        Mockito.eq("atrace --async_start -z " + expectedCategories),
                        Mockito.any(),
                        Mockito.eq(1L),
                        Mockito.any(),
                        Mockito.eq(1));
    }

    /**
     * Test {@link AtraceCollector#onTestStart(DeviceMetricData)} to see if atrace collection
     * started with no tracing categories does not do anything.
     *
     * <p>Expect that no commands are issued to the device when no categories are set
     */
    @Test
    public void testStartsAtraceWithNoCategoriesOption() throws Exception {
        doThrow(new Error("should not be called"))
                .when(mMockDevice)
                .executeShellCommand(
                        (String) Mockito.any(),
                        Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.any(),
                        Mockito.anyInt());

        AtraceCollector atrace = new AtraceCollector();
        atrace.onTestStart(new DeviceMetricData(mMockInvocationContext));
        atrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, Metric>());
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see if atrace collection
     * stopped correctly.
     *
     * <p>Expect that atrace command was stopped, the trace file was pulled from device to host and
     * the trace file removed from device.
     */
    @Test
    public void testStopsAtraceDuringTearDown() throws Exception {
        TestDescription test = new TestDescription("class", "test");
        when(mMockDevice.pullFile(Mockito.eq(M_DEFAULT_LOG_PATH)))
                .thenReturn(new File("/tmp/potato"));

        mAtrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, Metric>(), test);
        verify(mMockDevice, times(1))
                .executeShellCommand(
                        Mockito.eq("atrace --async_stop -z -c -o " + M_DEFAULT_LOG_PATH),
                        Mockito.any(),
                        Mockito.eq(300L),
                        Mockito.any(),
                        Mockito.eq(1));
        verify(mMockDevice, times(1)).pullFile(Mockito.eq(M_DEFAULT_LOG_PATH));
        verify(mMockDevice).deleteFile(M_DEFAULT_LOG_PATH);
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see if atrace collection
     * stopped correctly when preserve-ondevice-log is set.
     *
     * <p>Expect that atrace command was stopped, the trace file was pulled from device to host and
     * the trace file was not removed from the device.
     */
    @Test
    public void testPreserveFileOnDeviceOption() throws Exception {
        TestDescription test = new TestDescription("class", "test");
        when(mMockDevice.pullFile(Mockito.eq(M_DEFAULT_LOG_PATH)))
                .thenReturn(new File("/tmp/potato"));

        mOptionSetter.setOptionValue("preserve-ondevice-log", "true");
        mAtrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, Metric>(), test);
        verify(mMockDevice, times(1))
                .executeShellCommand(
                        Mockito.eq("atrace --async_stop -z -c -o " + M_DEFAULT_LOG_PATH),
                        Mockito.any(),
                        Mockito.eq(300L),
                        Mockito.any(),
                        Mockito.eq(1));
        verify(mMockDevice, times(1)).pullFile(Mockito.eq(M_DEFAULT_LOG_PATH));
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see that it throws an
     * exception if the atrace file could not be collected.
     *
     * <p>Expect that DeviceNotAvailableException is thrown when the file returned is null.
     */
    @Test
    public void testLogPullFail() throws Exception {
        TestDescription test = new TestDescription("class", "test");
        when(mMockDevice.pullFile((String) Mockito.any())).thenReturn(null);

        mAtrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, Metric>(), test);
        verify(mMockDevice, times(1)).pullFile((String) Mockito.any());
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see that it uploads its file
     * correctly with compression on.
     *
     * <p>Expect that testLog is called with the proper filename and LogDataType.
     */
    @Test
    public void testUploadsLogWithCompression() throws Exception {
        TestDescription test = new TestDescription("class", "test");
        when(mMockDevice.pullFile((String) Mockito.any())).thenReturn(new File("/tmp/potato"));
        when(mMockDevice.getSerialNumber()).thenReturn(M_SERIAL_NO);

        mAtrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, Metric>(), test);

        verify(mMockTestLogger, times(1))
                .testLog(
                        Mockito.eq("atrace_class#test" + M_SERIAL_NO + "_"),
                        Mockito.eq(LogDataType.ATRACE),
                        Mockito.any());
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see that it uploads its file
     * correctly with compression off.
     *
     * <p>Expect that testLog is called with the proper filename and LogDataType.
     */
    @Test
    public void testUploadsLogWithoutCompression() throws Exception {
        TestDescription test = new TestDescription("class", "test");
        when(mMockDevice.pullFile((String) Mockito.any())).thenReturn(new File("/tmp/potato"));
        when(mMockDevice.getSerialNumber()).thenReturn(M_SERIAL_NO);

        mOptionSetter.setOptionValue("compress-dump", "false");
        mAtrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, Metric>(), test);

        verify(mMockTestLogger, times(1))
                .testLog(
                        Mockito.eq("atrace_class#test" + M_SERIAL_NO + "_"),
                        Mockito.eq(LogDataType.TEXT),
                        Mockito.any());
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see that each device uploads
     * a log
     *
     * <p>Expect that testLog is called for each device.
     */
    @Test
    public void testMultipleDeviceBehavior() throws Exception {
        int num_devices = 3;
        List<ITestDevice> devices = new ArrayList<ITestDevice>();
        for (int i = 0; i < num_devices; i++) {
            ITestDevice device = mock(ITestDevice.class);
            when(device.getSerialNumber()).thenReturn(M_SERIAL_NO);
            when(device.pullFile((String) Mockito.any())).thenReturn(new File("/tmp/potato"));

            devices.add(device);
        }
        IInvocationContext mockInvocationContext = mock(IInvocationContext.class);
        when(mockInvocationContext.getDevices()).thenReturn(devices);

        AtraceCollector atrace = new AtraceCollector();
        OptionSetter optionSetter = new OptionSetter(atrace);
        optionSetter.setOptionValue("categories", M_CATEGORIES);
        TestDescription test = new TestDescription("class", "test");
        atrace.init(mockInvocationContext, mMockTestLogger);
        atrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, Metric>(), test);

        verify(mMockTestLogger, times(num_devices))
                .testLog((String) Mockito.any(), Mockito.eq(LogDataType.ATRACE), Mockito.any());
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see that it can run trace
     * post-processing commands on the log file
     *
     * <p>Expect that the executable is invoked and testLog is called for the trace data process.
     */
    @Test
    public void testExecutesPostProcessPar() throws Exception {
        TestDescription test = new TestDescription("class", "test");
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setStdout("stdout");
        commandResult.setStderr("stderr");
        when(mMockRunUtil.runTimedCmd(
                        Mockito.eq(60L),
                        Mockito.eq(mDummyBinary.getAbsolutePath()),
                        Mockito.eq("-i"),
                        Mockito.eq(M_TRACE_PATH_NAME),
                        Mockito.eq("--switch1")))
                .thenReturn(commandResult);

        // test
        mOptionSetter.setOptionValue("post-process-binary", mDummyBinary.getAbsolutePath());
        mOptionSetter.setOptionValue("post-process-input-file-key", "TRACEF");
        mOptionSetter.setOptionValue("post-process-args", "-i");
        mOptionSetter.setOptionValue("post-process-args", "TRACEF");
        mOptionSetter.setOptionValue("post-process-args", "--switch1");
        mOptionSetter.setOptionValue("post-process-timeout", "60");
        mAtrace.setRunUtil(mMockRunUtil);
        mAtrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, Metric>(), test);

        verify(mMockTestLogger, times(1))
                .testLog(
                        Mockito.eq("atrace_class#test" + M_SERIAL_NO + "_"),
                        Mockito.eq(LogDataType.ATRACE),
                        Mockito.any());
        verify(mMockRunUtil, times(1))
                .runTimedCmd(
                        Mockito.eq(60L),
                        Mockito.eq(mDummyBinary.getAbsolutePath()),
                        Mockito.eq("-i"),
                        Mockito.eq(M_TRACE_PATH_NAME),
                        Mockito.eq("--switch1"));
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see that it can run trace
     * post-processing commands on the log file for executables who don't have keyed input.
     *
     * <p>Expect that the executable is invoked and testLog is called for the trace data process.
     */
    @Test
    public void testExecutesPostProcessParDifferentFormat() throws Exception {
        TestDescription test = new TestDescription("class", "test");
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setStdout("stdout");
        commandResult.setStderr("stderr");
        when(mMockRunUtil.runTimedCmd(
                        Mockito.eq(60L),
                        Mockito.eq(mDummyBinary.getAbsolutePath()),
                        Mockito.eq(M_TRACE_PATH_NAME),
                        Mockito.eq("--switch1")))
                .thenReturn(commandResult);

        mOptionSetter.setOptionValue("post-process-binary", mDummyBinary.getAbsolutePath());
        mOptionSetter.setOptionValue("post-process-input-file-key", "TRACEF");
        mOptionSetter.setOptionValue("post-process-args", "TRACEF");
        mOptionSetter.setOptionValue("post-process-args", "--switch1");
        mOptionSetter.setOptionValue("post-process-timeout", "60");
        mAtrace.setRunUtil(mMockRunUtil);
        mAtrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, Metric>(), test);

        verify(mMockTestLogger, times(1))
                .testLog(
                        Mockito.eq("atrace_class#test" + M_SERIAL_NO + "_"),
                        Mockito.eq(LogDataType.ATRACE),
                        Mockito.any());
        verify(mMockRunUtil, times(1))
                .runTimedCmd(
                        Mockito.eq(60L),
                        Mockito.eq(mDummyBinary.getAbsolutePath()),
                        Mockito.eq(M_TRACE_PATH_NAME),
                        Mockito.eq("--switch1"));
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see that it can run a
     * post-processing command that makes no output on stderr.
     *
     * <p>Expect that the executable is invoked and testLog is called for the trace data process.
     */
    @Test
    public void testExecutesPostProcessParNoStderr() throws Exception {
        TestDescription test = new TestDescription("class", "test");
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setStdout("stdout");
        when(mMockRunUtil.runTimedCmd(
                        Mockito.eq(180000L),
                        Mockito.eq(mDummyBinary.getAbsolutePath()),
                        Mockito.eq("-i"),
                        Mockito.eq(M_TRACE_PATH_NAME),
                        Mockito.eq("--switch1")))
                .thenReturn(commandResult);

        mOptionSetter.setOptionValue("post-process-binary", mDummyBinary.getAbsolutePath());
        mOptionSetter.setOptionValue("post-process-input-file-key", "TRACEF");
        mOptionSetter.setOptionValue("post-process-args", "-i");
        mOptionSetter.setOptionValue("post-process-args", "TRACEF");
        mOptionSetter.setOptionValue("post-process-args", "--switch1");
        mOptionSetter.setOptionValue("post-process-timeout", "3m");
        mAtrace.setRunUtil(mMockRunUtil);
        mAtrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, Metric>(), test);

        verify(mMockTestLogger, times(1))
                .testLog(
                        Mockito.eq("atrace_class#test" + M_SERIAL_NO + "_"),
                        Mockito.eq(LogDataType.ATRACE),
                        Mockito.any());
        verify(mMockRunUtil, times(1))
                .runTimedCmd(
                        Mockito.eq(180000L),
                        Mockito.eq(mDummyBinary.getAbsolutePath()),
                        Mockito.eq("-i"),
                        Mockito.eq(M_TRACE_PATH_NAME),
                        Mockito.eq("--switch1"));
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see that a failing
     * post-processing command is not fatal.
     *
     * <p>Expect that the executable is invoked and testLog is called for the trace data process.
     */
    @Test
    public void testExecutesPostProcessParFailed() throws Exception {
        TestDescription test = new TestDescription("class", "test");
        CommandResult commandResult = new CommandResult(CommandStatus.FAILED);
        commandResult.setStderr("stderr");

        when(mMockRunUtil.runTimedCmd(
                        Mockito.eq(60000L),
                        Mockito.eq(mDummyBinary.getAbsolutePath()),
                        Mockito.eq("-i"),
                        Mockito.eq(M_TRACE_PATH_NAME),
                        Mockito.eq("--switch1")))
                .thenReturn(commandResult);

        mOptionSetter.setOptionValue("post-process-binary", mDummyBinary.getAbsolutePath());
        mOptionSetter.setOptionValue("post-process-input-file-key", "TRACEF");
        mOptionSetter.setOptionValue("post-process-args", "-i");
        mOptionSetter.setOptionValue("post-process-args", "TRACEF");
        mOptionSetter.setOptionValue("post-process-args", "--switch1");
        mOptionSetter.setOptionValue("post-process-timeout", "1m");
        mAtrace.setRunUtil(mMockRunUtil);
        mAtrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, Metric>(), test);

        verify(mMockTestLogger, times(1))
                .testLog(
                        Mockito.eq("atrace_class#test" + M_SERIAL_NO + "_"),
                        Mockito.eq(LogDataType.ATRACE),
                        Mockito.any());
        verify(mMockRunUtil, times(1))
                .runTimedCmd(
                        Mockito.eq(60000L),
                        Mockito.eq(mDummyBinary.getAbsolutePath()),
                        Mockito.eq("-i"),
                        Mockito.eq(M_TRACE_PATH_NAME),
                        Mockito.eq("--switch1"));
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see that a timeout of the
     * post-processing command is not fatal.
     *
     * <p>Expect that timeout is not fatal.
     */
    @Test
    public void testExecutesPostProcessParTimeout() throws Exception {
        TestDescription test = new TestDescription("class", "test");
        CommandResult commandResult = new CommandResult(CommandStatus.TIMED_OUT);

        when(mMockRunUtil.runTimedCmd(
                        Mockito.eq(3661000L),
                        Mockito.eq(mDummyBinary.getAbsolutePath()),
                        Mockito.eq("-i"),
                        Mockito.eq(M_TRACE_PATH_NAME),
                        Mockito.eq("--switch1")))
                .thenReturn(commandResult);

        mOptionSetter.setOptionValue("post-process-binary", mDummyBinary.getAbsolutePath());
        mOptionSetter.setOptionValue("post-process-input-file-key", "TRACEF");
        mOptionSetter.setOptionValue("post-process-args", "-i");
        mOptionSetter.setOptionValue("post-process-args", "TRACEF");
        mOptionSetter.setOptionValue("post-process-args", "--switch1");
        mOptionSetter.setOptionValue("post-process-timeout", "1h1m1s");
        mAtrace.setRunUtil(mMockRunUtil);
        mAtrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, Metric>(), test);

        verify(mMockTestLogger, times(1))
                .testLog(
                        Mockito.eq("atrace_class#test" + M_SERIAL_NO + "_"),
                        Mockito.eq(LogDataType.ATRACE),
                        Mockito.any());
        verify(mMockRunUtil, times(1))
                .runTimedCmd(
                        Mockito.eq(3661000L),
                        Mockito.eq(mDummyBinary.getAbsolutePath()),
                        Mockito.eq("-i"),
                        Mockito.eq(M_TRACE_PATH_NAME),
                        Mockito.eq("--switch1"));
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see that metrics indicated
     * by the post-processing command are also uploaded.
     *
     * <p>Expect that testLog is called for the files indicated by the tool in the regex pattern.
     */
    @Test
    public void testProcessesMetricOutput() throws Exception {
        TestDescription test = new TestDescription("class", "test");
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setStdout("line1\nt:" + mDummyMetricPng.getAbsolutePath());
        commandResult.setStderr("stderr");
        when(mMockRunUtil.runTimedCmd(Mockito.anyLong(), Mockito.any())).thenReturn(commandResult);

        mOptionSetter.setOptionValue("post-process-binary", mDummyBinary.getAbsolutePath());
        mOptionSetter.setOptionValue("post-process-output-file-regex", "t:(.*)");

        mAtrace.setRunUtil(mMockRunUtil);
        mAtrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, Metric>(), test);

        verify(mMockTestLogger, times(1))
                .testLog(
                        Mockito.eq("atrace_class#test" + M_SERIAL_NO + "_"),
                        Mockito.eq(LogDataType.ATRACE),
                        Mockito.any());
        verify(mMockRunUtil, times(1)).runTimedCmd(Mockito.anyLong(), Mockito.any());
        verify(mMockTestLogger)
                .testLog(
                        Mockito.eq(FileUtil.getBaseName(mDummyMetricPng.getName())),
                        Mockito.eq(LogDataType.PNG),
                        Mockito.any());
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see that a malformed option
     * still uploads the postprocessing outputs.
     *
     * <p>Expect that testLog is called for stderr and stdout.
     */
    @Test
    public void testProcessesMetricOutputWithMalformedRegex() throws Exception {
        TestDescription test = new TestDescription("class", "test");
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setStdout("text\nt:" + mDummyMetricPng.getAbsolutePath());
        commandResult.setStderr("stderr");
        when(mMockRunUtil.runTimedCmd(Mockito.anyLong(), Mockito.any())).thenReturn(commandResult);

        mOptionSetter.setOptionValue("post-process-binary", mDummyBinary.getAbsolutePath());
        mOptionSetter.setOptionValue("post-process-output-file-regex", "t:.*");

        mAtrace.setRunUtil(mMockRunUtil);
        mAtrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, Metric>(), test);

        verify(mMockTestLogger, times(1))
                .testLog(
                        Mockito.eq("atrace_class#test" + M_SERIAL_NO + "_"),
                        Mockito.eq(LogDataType.ATRACE),
                        Mockito.any());
        verify(mMockRunUtil, times(1)).runTimedCmd(Mockito.anyLong(), Mockito.any());
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see that a if the metric
     * file is not found, the postprocessing output is still uploaded.
     *
     * <p>Expect that testLog is called for stdout.
     */
    @Test
    public void testProcessesMetricOutputWithFileNotFound() throws Exception {
        TestDescription test = new TestDescription("class", "test");
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setStdout("text\nt:/file/not/found.txt");
        when(mMockRunUtil.runTimedCmd(Mockito.anyLong(), Mockito.any())).thenReturn(commandResult);

        mOptionSetter.setOptionValue("post-process-binary", mDummyBinary.getAbsolutePath());
        mOptionSetter.setOptionValue("post-process-output-file-regex", "t:(.*)");

        mAtrace.setRunUtil(mMockRunUtil);
        mAtrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, Metric>(), test);

        verify(mMockTestLogger, times(1))
                .testLog(
                        Mockito.eq("atrace_class#test" + M_SERIAL_NO + "_"),
                        Mockito.eq(LogDataType.ATRACE),
                        Mockito.any());
        verify(mMockRunUtil, times(1)).runTimedCmd(Mockito.anyLong(), Mockito.any());
    }

    /**
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see that metrics indicated
     * by the post-processing command are also uploaded.
     *
     * <p>Expect that testLog is called for the files indicated by the tool in the regex pattern.
     */
    @Test
    public void testProcessesMetricOutputTwoKeys() throws Exception {
        TestDescription test = new TestDescription("class", "test");
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setStdout(
                "line1\nt:"
                        + mDummyMetricPng.getAbsolutePath()
                        + "\nZAZ:"
                        + mDummyMetricText.getAbsolutePath());
        commandResult.setStderr("stderr");
        when(mMockRunUtil.runTimedCmd(Mockito.anyLong(), Mockito.any())).thenReturn(commandResult);

        mOptionSetter.setOptionValue("post-process-binary", mDummyBinary.getAbsolutePath());
        mOptionSetter.setOptionValue("post-process-output-file-regex", "t:(.*)");
        mOptionSetter.setOptionValue("post-process-output-file-regex", "ZAZ:(.*)");

        mAtrace.setRunUtil(mMockRunUtil);
        mAtrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, Metric>(), test);

        verify(mMockTestLogger, times(1))
                .testLog(
                        Mockito.eq("atrace_class#test" + M_SERIAL_NO + "_"),
                        Mockito.eq(LogDataType.ATRACE),
                        Mockito.any());
        verify(mMockTestLogger, times(1))
                .testLog(
                        Mockito.eq(FileUtil.getBaseName(mDummyMetricPng.getName())),
                        Mockito.eq(LogDataType.PNG),
                        Mockito.any());
        verify(mMockTestLogger, times(1))
                .testLog(
                        Mockito.eq(FileUtil.getBaseName(mDummyMetricText.getName())),
                        Mockito.eq(LogDataType.TEXT),
                        Mockito.any());
        verify(mMockRunUtil, times(1)).runTimedCmd(Mockito.anyLong(), Mockito.any());
    }

    /**
     * Test {@link AtraceCollector#onTestStart(DeviceMetricData)} to see if atrace collection
     * is not started when skip-atrace-start option is enabled. Assumption is that the
     * boot tracing is enabled via bootloader option.
     * Test {@link AtraceCollector#onTestEnd(DeviceMetricData, Map)} to see if atrace collection
     * stopped correctly.
     *
     * <p>
     */
    @Test
    public void testSkipAtraceStartOption() throws Exception {
        TestDescription test = new TestDescription("class", "test");
        when(mMockDevice.pullFile(Mockito.eq(M_DEFAULT_LOG_PATH)))
                .thenReturn(new File("/tmp/potato"));

        mOptionSetter.setOptionValue("skip-atrace-start", "true");
        mOptionSetter.setOptionValue("atrace-on-boot", "true");

        mAtrace.onTestStart(new DeviceMetricData(mMockInvocationContext));
        // Start trace should not be called because the assumption is that
        // tracing is enabled via bootloader trace option.
        verify(mMockDevice, times(0))
                .executeShellCommand(
                        Mockito.eq("atrace --async_start -z " + M_CATEGORIES),
                        Mockito.any(),
                        Mockito.eq(1L),
                        Mockito.any(),
                        Mockito.eq(1));

        mAtrace.onTestEnd(
                new DeviceMetricData(mMockInvocationContext), new HashMap<String, Metric>(), test);
        verify(mMockDevice, times(1))
                .executeShellCommand(
                        Mockito.eq("atrace --async_stop -z -c -o " + M_DEFAULT_LOG_PATH),
                        Mockito.any(),
                        Mockito.eq(300L),
                        Mockito.any(),
                        Mockito.eq(1));
        verify(mMockDevice, times(1)).pullFile(Mockito.eq(M_DEFAULT_LOG_PATH));
    }

}
