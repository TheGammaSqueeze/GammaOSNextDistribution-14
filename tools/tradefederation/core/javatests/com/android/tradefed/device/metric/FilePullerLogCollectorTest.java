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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
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
import java.util.HashMap;
import java.util.Map;

/** Unit tests for {@link FilePullerLogCollector}. */
@RunWith(JUnit4.class)
public class FilePullerLogCollectorTest {

    private FilePullerLogCollector mCollector;
    @Mock ITestInvocationListener mMockListener;
    private IInvocationContext mContext;
    @Mock ITestDevice mMockDevice;
    @Mock IDevice mMockIDevice;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mContext = new InvocationContext();
        mContext.addAllocatedDevice(ConfigurationDef.DEFAULT_DEVICE_NAME, mMockDevice);
        mCollector = new FilePullerLogCollector();
        OptionSetter setter = new OptionSetter(mCollector);
        setter.setOptionValue("pull-pattern-keys", "log.*");
        when(mMockDevice.getCurrentUser()).thenReturn(0);
    }

    /** Test that metrics and files are logged but nothing is pulled since it's a stub device. */
    @Test
    public void testSkipStub() throws Exception {
        ITestInvocationListener listener = mCollector.init(mContext, mMockListener);
        TestDescription test = new TestDescription("class", "test");
        Map<String, String> metrics = new HashMap<>();
        metrics.put("log1", "/data/local/tmp/log1.txt");
        metrics.put("another_metrics", "57");

        ArgumentCaptor<HashMap<String, Metric>> capture = ArgumentCaptor.forClass(HashMap.class);

        when(mMockDevice.getIDevice()).thenReturn(new StubDevice("serial"));

        listener.testRunStarted("runName", 1);
        listener.testStarted(test, 0L);
        listener.testEnded(test, 50L, TfMetricProtoUtil.upgradeConvert(metrics));
        listener.testRunEnded(100L, new HashMap<String, Metric>());

        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("runName"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener).testStarted(test, 0L);
        verify(mMockListener).testEnded(Mockito.eq(test), Mockito.eq(50L), capture.capture());
        verify(mMockListener).testRunEnded(100L, new HashMap<String, Metric>());
        HashMap<String, Metric> metricCaptured = capture.getValue();
        assertEquals(
                "57", metricCaptured.get("another_metrics").getMeasurements().getSingleString());
        assertEquals(
                "/data/local/tmp/log1.txt",
                metricCaptured.get("log1").getMeasurements().getSingleString());
    }

    /**
     * Test that if the pattern of a metric match the requested pattern we attemp to pull it as a
     * log file.
     */
    @Test
    public void testPullAndLog() throws Exception {
        when(mMockDevice.getDeviceState()).thenReturn(TestDeviceState.ONLINE);
        ITestInvocationListener listener = mCollector.init(mContext, mMockListener);
        TestDescription test = new TestDescription("class", "test");
        Map<String, String> metrics = new HashMap<>();
        metrics.put("log1", "/data/local/tmp/log1.txt");
        metrics.put("another_metrics", "57");

        ArgumentCaptor<HashMap<String, Metric>> capture = ArgumentCaptor.forClass(HashMap.class);

        when(mMockDevice.getIDevice()).thenReturn(mMockIDevice);
        when(mMockDevice.pullFile(Mockito.eq("/data/local/tmp/log1.txt"), Mockito.eq(0)))
                .thenReturn(new File("file"));

        listener.testRunStarted("runName", 1);
        listener.testStarted(test, 0L);
        listener.testEnded(test, 50L, TfMetricProtoUtil.upgradeConvert(metrics));
        listener.testRunEnded(100L, new HashMap<String, Metric>());

        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("runName"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener).testStarted(test, 0L);
        verify(mMockDevice).deleteFile("/data/local/tmp/log1.txt");
        verify(mMockListener)
                .testLog(Mockito.eq("file"), Mockito.eq(LogDataType.TEXT), Mockito.any());
        verify(mMockListener).testEnded(Mockito.eq(test), Mockito.eq(50L), capture.capture());
        verify(mMockListener).testRunEnded(100L, new HashMap<String, Metric>());
        HashMap<String, Metric> metricCaptured = capture.getValue();
        assertEquals(
                "57", metricCaptured.get("another_metrics").getMeasurements().getSingleString());
        assertEquals(
                "/data/local/tmp/log1.txt",
                metricCaptured.get("log1").getMeasurements().getSingleString());
    }

    /**
     * Test that if the pattern of a metric match the requested pattern but we don't collect on test
     * cases then nothing is done.
     */
    @Test
    public void testSkipTestCollection() throws Exception {
        OptionSetter setter = new OptionSetter(mCollector);
        setter.setOptionValue("collect-on-run-ended-only", "true");
        ITestInvocationListener listener = mCollector.init(mContext, mMockListener);
        TestDescription test = new TestDescription("class", "test");
        Map<String, String> metrics = new HashMap<>();
        metrics.put("log1", "/data/local/tmp/log1.txt");
        metrics.put("another_metrics", "57");

        ArgumentCaptor<HashMap<String, Metric>> capture = ArgumentCaptor.forClass(HashMap.class);

        listener.testStarted(test, 0L);
        listener.testEnded(test, 50L, TfMetricProtoUtil.upgradeConvert(metrics));

        verify(mMockListener).testStarted(test, 0L);
        verify(mMockListener).testEnded(Mockito.eq(test), Mockito.eq(50L), capture.capture());
        HashMap<String, Metric> metricCaptured = capture.getValue();
        assertEquals(
                "57", metricCaptured.get("another_metrics").getMeasurements().getSingleString());
        assertEquals(
                "/data/local/tmp/log1.txt",
                metricCaptured.get("log1").getMeasurements().getSingleString());
    }

    /** Test that the post processor is called on any pulled files. */
    @Test
    public void testPostProcessFiles() throws Exception {
        when(mMockDevice.getDeviceState()).thenReturn(TestDeviceState.ONLINE);
        PostProcessingFilePullerLogCollector collector = new PostProcessingFilePullerLogCollector();
        OptionSetter setter = new OptionSetter(collector);
        setter.setOptionValue("pull-pattern-keys", "log.*");

        ITestInvocationListener listener = collector.init(mContext, mMockListener);
        TestDescription test = new TestDescription("class", "test");
        Map<String, String> metrics = new HashMap<>();
        metrics.put("log1", "/data/local/tmp/log1.txt");

        ArgumentCaptor<HashMap<String, Metric>> capture = ArgumentCaptor.forClass(HashMap.class);

        when(mMockDevice.getIDevice()).thenReturn(mMockIDevice);
        when(mMockDevice.pullFile(Mockito.eq("/data/local/tmp/log1.txt"), Mockito.eq(0)))
                .thenReturn(new File("file"));

        listener.testRunStarted("runName", 1);
        listener.testStarted(test, 0L);
        listener.testEnded(test, 50L, TfMetricProtoUtil.upgradeConvert(metrics));
        listener.testRunEnded(100L, new HashMap<String, Metric>());

        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("runName"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener).testStarted(test, 0L);
        verify(mMockDevice).deleteFile("/data/local/tmp/log1.txt");
        verify(mMockListener)
                .testLog(Mockito.eq("file"), Mockito.eq(LogDataType.TEXT), Mockito.any());
        verify(mMockListener).testEnded(Mockito.eq(test), Mockito.eq(50L), capture.capture());
        verify(mMockListener).testRunEnded(100L, new HashMap<String, Metric>());

        // Assert the post processor was called and completed.
        assertTrue(collector.isPostProcessed());
    }

    /** Test the compress directory option. */
    @Test
    public void testCompressDirectoryBeforeUpload() throws Exception {
        when(mMockDevice.getDeviceState()).thenReturn(TestDeviceState.ONLINE);
        PostProcessingFilePullerLogCollector collector = new PostProcessingFilePullerLogCollector();
        OptionSetter setter = new OptionSetter(collector);
        setter.setOptionValue("directory-keys", "data/local/tmp");
        setter.setOptionValue("compress-directories", "true");

        ITestInvocationListener listener = collector.init(mContext, mMockListener);
        TestDescription test = new TestDescription("class", "test");
        Map<String, String> metrics = new HashMap<>();
        metrics.put("log1", "data/local/tmp");

        ArgumentCaptor<HashMap<String, Metric>> capture = ArgumentCaptor.forClass(HashMap.class);

        when(mMockDevice.getIDevice()).thenReturn(mMockIDevice);
        when(mMockDevice.pullDir(Mockito.any(), Mockito.any())).thenReturn(true);

        // Verify logging is only done for compressed file which file name starts with last folder
        // name.

        listener.testRunStarted("runName", 1);
        listener.testStarted(test, 0L);
        listener.testEnded(test, 50L, TfMetricProtoUtil.upgradeConvert(metrics));
        listener.testRunEnded(100L, new HashMap<String, Metric>());

        verify(mMockListener)
                .testRunStarted(
                        Mockito.eq("runName"), Mockito.eq(1), Mockito.eq(0), Mockito.anyLong());
        verify(mMockListener).testStarted(test, 0L);
        verify(mMockDevice).deleteFile("data/local/tmp");
        verify(mMockListener)
                .testLog(Mockito.startsWith("tmp"), Mockito.eq(LogDataType.ZIP), Mockito.any());
        verify(mMockListener).testEnded(Mockito.eq(test), Mockito.eq(50L), capture.capture());
        verify(mMockListener).testRunEnded(100L, new HashMap<String, Metric>());

        // Assert the post processor was called and completed for the compressed zip file.
        assertTrue(collector.isPostProcessed());
    }

    private static class PostProcessingFilePullerLogCollector extends FilePullerLogCollector {
        private boolean mIsPostProcessed = false;

        @Override
        protected void postProcessMetricFile(
                String key, File metricFile, DeviceMetricData runData) {
            mIsPostProcessed = true;
        }

        public boolean isPostProcessed() {
            return mIsPostProcessed;
        }
    }
}
