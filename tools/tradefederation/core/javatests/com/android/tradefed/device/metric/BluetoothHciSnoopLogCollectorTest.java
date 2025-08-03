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
package com.android.tradefed.device.metric;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

@RunWith(JUnit4.class)
public final class BluetoothHciSnoopLogCollectorTest {
    private BluetoothHciSnoopLogCollector mCollector;
    @Mock ITestInvocationListener mMockListener;
    private IInvocationContext mContext;
    @Mock ITestDevice mMockDevice;
    @Mock IDevice mMockIDevice;
    private List<ITestDevice> mDevices;
    private ITestInvocationListener listener;

    private static final String TEST_RUN_NAME = "runName";
    private static final int TEST_RUN_COUNT = 1;
    private static final long TEST_START_TIME = 0;
    private static final long TEST_END_TIME = 50;
    private static final long TEST_RUN_END_TIME = 100;
    private static final String PULL_DIRECTORY = "/data/misc/bluetooth/testreporting";
    private static final String PULL_DIRECTORY_BASENAME = "testreporting";

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mContext = Mockito.spy(new InvocationContext());
        mMockDevice = Mockito.mock(ITestDevice.class);
        mDevices = List.of(mMockDevice);
        doReturn(mDevices).when(mContext).getDevices();

        mContext.addAllocatedDevice(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockDevice);
        when(mContext.getDevices()).thenReturn(mDevices);
        mCollector = Mockito.spy(new BluetoothHciSnoopLogCollector());
        doNothing()
                .when(mCollector)
                .executeShellCommand(Mockito.any(ITestDevice.class), Mockito.anyString());
        doReturn("/data/misc/bluetooth/testreporting").when(mCollector).getReportingDir();
        when(mMockDevice.getCurrentUser()).thenReturn(0);
        when(mMockDevice.getIDevice()).thenReturn(mMockIDevice);
        when(mMockDevice.setProperty(Mockito.anyString(), Mockito.anyString())).thenReturn(true);
        when(mMockDevice.executeShellV2Command(Mockito.anyString()))
                .thenReturn(new CommandResult(CommandStatus.SUCCESS));
        when(mMockDevice.getDeviceState()).thenReturn(TestDeviceState.ONLINE);

        listener = mCollector.init(mContext, mMockListener);
    }

    /**
     * Test that if the pattern of a metric match the requested pattern we attempt to pull it as a
     * log file.
     */
    @Test
    public void testPullFileAndLog() throws Exception {
        // Pattern of file(s)/dir(s) to pull.
        String pullPatternKey = "log1";
        String pullPatternPath = "/data/local/tmp/log1.txt";

        // Set up the metric collector's param.
        OptionSetter setter = new OptionSetter(mCollector);
        setter.setOptionValue("pull-pattern-keys", pullPatternKey);
        setter.setOptionValue("clean-up", "true");
        setter.setOptionValue("collect-on-run-ended-only", "false");

        HashMap<String, Metric> metrics = new HashMap<>();
        metrics.put(pullPatternKey, TfMetricProtoUtil.stringToMetric(pullPatternPath));
        metrics.put("another_metrics", TfMetricProtoUtil.stringToMetric("57"));

        ArgumentCaptor<HashMap<String, Metric>> capture = ArgumentCaptor.forClass(HashMap.class);

        when(mMockDevice.pullFile(Mockito.eq(pullPatternPath), Mockito.eq(0)))
                .thenReturn(new File("file"));

        TestDescription test = new TestDescription("class", "test");
        listener.testRunStarted(TEST_RUN_NAME, TEST_RUN_COUNT);
        listener.testStarted(test, TEST_START_TIME);
        listener.testEnded(test, TEST_END_TIME, metrics);
        listener.testRunEnded(TEST_RUN_END_TIME, metrics);

        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(TEST_RUN_NAME),
                        Mockito.eq(TEST_RUN_COUNT),
                        Mockito.eq(0),
                        Mockito.anyLong());
        verify(mMockListener).testStarted(test, TEST_START_TIME);
        verify(mMockDevice).deleteFile(pullPatternPath);
        verify(mMockDevice).pullFile(Mockito.eq(pullPatternPath), Mockito.anyInt());
        verify(mCollector).retrieveFile(Mockito.any(), Mockito.anyString(), Mockito.anyInt());
        verify(mMockListener)
                .testEnded(Mockito.eq(test), Mockito.eq(TEST_END_TIME), capture.capture());
        verify(mMockListener).testRunEnded(TEST_RUN_END_TIME, metrics);
        HashMap<String, Metric> metricCaptured = capture.getValue();
        assertEquals(
                "57", metricCaptured.get("another_metrics").getMeasurements().getSingleString());
        assertEquals(
                pullPatternPath,
                metricCaptured.get(pullPatternKey).getMeasurements().getSingleString());
    }

    /** Test that we attempt to pull metrics (in the watched directory) as a log file. */
    @Test
    public void testPullDirMultipleSnoopLogs() throws Exception {
        // Dir to pull.
        String pullDirectoryPath = "/tmp/my/dir";

        // Set up the metric collector's param.
        OptionSetter setter = new OptionSetter(mCollector);
        setter.setOptionValue("directory-keys", pullDirectoryPath);
        setter.setOptionValue("clean-up", "true");
        setter.setOptionValue("collect-on-run-ended-only", "false");

        HashMap<String, Metric> metrics = new HashMap<>();
        metrics.put("another_metrics", TfMetricProtoUtil.stringToMetric("57"));

        ArgumentCaptor<HashMap<String, Metric>> capture = ArgumentCaptor.forClass(HashMap.class);

        // Inject a file into the temp destination directory on the host, to simulate the behaviour
        // in FilePullerDeviceMetricCollector.pullMetricDirectory().
        doAnswer(
                        invocation -> {
                            String keyDirectory = (String) invocation.getArgument(0);
                            File tmpDestDir = (File) invocation.getArgument(1);
                            Path logFileInTmpDestDir =
                                    Files.createTempFile(
                                            tmpDestDir.toPath(), "class-test-", ".log");

                            return true;
                        })
                .when(mMockDevice)
                .pullDir(Mockito.anyString(), Mockito.any(File.class));

        listener.testRunStarted(TEST_RUN_NAME, TEST_RUN_COUNT);

        TestDescription test1 = new TestDescription("class", "test1");
        listener.testStarted(test1, TEST_START_TIME);
        listener.testEnded(test1, TEST_END_TIME, metrics);

        TestDescription test2 = new TestDescription("class", "test2");
        listener.testStarted(test2, TEST_START_TIME);
        listener.testEnded(test2, TEST_END_TIME, metrics);

        TestDescription test3 = new TestDescription("class", "test3");
        listener.testStarted(test3, TEST_START_TIME);
        listener.testEnded(test3, TEST_END_TIME, metrics);

        listener.testRunEnded(TEST_RUN_END_TIME, metrics);

        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq(TEST_RUN_NAME),
                        Mockito.eq(TEST_RUN_COUNT),
                        Mockito.eq(0),
                        Mockito.anyLong());

        verify(mMockListener).testStarted(test1, TEST_START_TIME);
        verify(mMockListener).testStarted(test2, TEST_START_TIME);
        verify(mMockListener).testStarted(test3, TEST_START_TIME);
        verify(mMockListener, times(3))
                .testLog(Mockito.anyString(), Mockito.eq(LogDataType.BT_SNOOP_LOG), Mockito.any());
        verify(mMockListener)
                .testEnded(Mockito.eq(test1), Mockito.eq(TEST_END_TIME), capture.capture());
        verify(mMockListener)
                .testEnded(Mockito.eq(test2), Mockito.eq(TEST_END_TIME), capture.capture());
        verify(mMockListener)
                .testEnded(Mockito.eq(test3), Mockito.eq(TEST_END_TIME), capture.capture());
        verify(mMockListener).testRunEnded(TEST_RUN_END_TIME, metrics);
        HashMap<String, Metric> metricCaptured = capture.getValue();
        assertEquals(
                "57", metricCaptured.get("another_metrics").getMeasurements().getSingleString());
    }
}
