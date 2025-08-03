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
package com.android.tradefed.testtype;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.FileListingService;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link NativeBenchmarkTest}. */
@RunWith(JUnit4.class)
public class NativeBenchmarkTestTest {

    private NativeBenchmarkTest mBenchmark;
    @Mock ITestInvocationListener mListener;
    @Mock ITestDevice mDevice;
    private TestInformation mTestInfo;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mBenchmark = new NativeBenchmarkTest();

        when(mDevice.getSerialNumber()).thenReturn("SERIAL");
        mTestInfo = TestInformation.newBuilder().build();
    }

    @Test
    public void testRun_noDevice() throws DeviceNotAvailableException {
        try {
            mBenchmark.run(mTestInfo, mListener);
            fail("An exception should have been thrown");
        } catch (IllegalArgumentException expected) {
            assertEquals("Device has not been set", expected.getMessage());
        }
    }

    @Test
    public void testGetTestPath() {
        String res = mBenchmark.getTestPath();
        assertEquals(NativeBenchmarkTest.DEFAULT_TEST_PATH, res);
    }

    @Test
    public void testGetTestPath_withModule() throws Exception {
        OptionSetter setter = new OptionSetter(mBenchmark);
        setter.setOptionValue("benchmark-module-name", "TEST");
        String res = mBenchmark.getTestPath();
        String expected =
                String.format(
                        "%s%s%s",
                        NativeBenchmarkTest.DEFAULT_TEST_PATH,
                        FileListingService.FILE_SEPARATOR,
                        "TEST");
        assertEquals(expected, res);
    }

    @Test
    public void testRun_noFileEntry() throws DeviceNotAvailableException {
        mBenchmark.setDevice(mDevice);
        when(mDevice.getFileEntry(any(String.class))).thenReturn(null);

        mBenchmark.run(mTestInfo, mListener);
        verify(mDevice).getFileEntry(any(String.class));
    }

    @Test
    public void testRun_setMaxFrequency() throws Exception {
        mBenchmark =
                new NativeBenchmarkTest() {
                    @Override
                    protected void doRunAllTestsInSubdirectory(
                            IFileEntry rootEntry,
                            ITestDevice testDevice,
                            ITestInvocationListener listener)
                            throws DeviceNotAvailableException {
                        // empty on purpose
                    }
                };
        mBenchmark.setDevice(mDevice);
        OptionSetter setter = new OptionSetter(mBenchmark);
        setter.setOptionValue("max-cpu-freq", "true");
        IFileEntry fakeEntry = mock(IFileEntry.class);
        when(mDevice.getFileEntry(any(String.class))).thenReturn(fakeEntry);
        when(mDevice.executeShellCommand(
                        eq(
                                "cat "
                                        + "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq > "
                                        + "/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq")))
                .thenReturn("");
        when(mDevice.executeShellCommand(
                        eq(
                                "cat "
                                        + "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq > "
                                        + "/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq")))
                .thenReturn("");

        mBenchmark.run(mTestInfo, mListener);
        verify(mDevice, times(2)).executeShellCommand(any(String.class));
    }

    @Test
    public void testRun_doRunAllTestsInSubdirectory() throws Exception {
        final String fakeRunName = "RUN_NAME";
        final String fakeFullPath = "/path/" + fakeRunName;
        mBenchmark.setDevice(mDevice);
        IFileEntry fakeEntry = mock(IFileEntry.class);
        when(fakeEntry.isDirectory()).thenReturn(false);
        when(fakeEntry.getName()).thenReturn(fakeRunName);
        when(fakeEntry.getFullEscapedPath()).thenReturn(fakeFullPath);
        when(mDevice.getFileEntry(any(String.class))).thenReturn(fakeEntry);
        when(mDevice.executeShellCommand(eq("chmod 755 " + fakeFullPath))).thenReturn("");

        mBenchmark.run(mTestInfo, mListener);
        verify(mDevice, times(1))
                .executeShellCommand(
                        eq(fakeFullPath + " -n 1000 -d 0.000000 -c 1 -s 1"),
                        any(),
                        anyLong(),
                        eq(TimeUnit.MILLISECONDS),
                        eq(0));
        verify(mListener, times(1)).testRunStarted(eq(fakeRunName), anyInt());
        verify(mListener, times(1)).testRunEnded(anyLong(), Mockito.<HashMap<String, Metric>>any());
    }
}
