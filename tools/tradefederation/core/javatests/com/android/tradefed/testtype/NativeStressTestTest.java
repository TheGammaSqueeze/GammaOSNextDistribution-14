/*
 * Copyright (C) 2010 The Android Open Source Project
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

import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IShellOutputReceiver;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link NativeStressTest}. */
@RunWith(JUnit4.class)
public class NativeStressTestTest {

    private static final String RUN_NAME = "run-name";
    @Mock ITestInvocationListener mMockListener;
    private ArgumentCaptor<HashMap<String, Metric>> mCapturedMetricMap;
    private NativeStressTest mNativeTest;
    @Mock ITestDevice mMockDevice;
    @Mock IFileEntry mMockStressFile;
    private TestInformation mTestInfo;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mNativeTest = new NativeStressTest();

        mCapturedMetricMap = ArgumentCaptor.forClass(HashMap.class);

        when(mMockDevice.getFileEntry((String) Mockito.any())).thenReturn(mMockStressFile);
        when(mMockStressFile.isDirectory()).thenReturn(Boolean.FALSE);
        when(mMockStressFile.getName()).thenReturn(RUN_NAME);
        when(mMockStressFile.getFullEscapedPath()).thenReturn(RUN_NAME);

        mNativeTest.setDevice(mMockDevice);
        when(mMockDevice.getSerialNumber()).thenReturn("serial");
        when(mMockDevice.executeShellCommand(Mockito.contains("chmod"))).thenReturn("");
        mTestInfo = TestInformation.newBuilder().build();
    }

    private void verifyMocks() {
        // expect this call
        verify(mMockListener).testRunStarted(RUN_NAME, 0);
        verify(mMockListener).testRunEnded(Mockito.anyLong(), mCapturedMetricMap.capture());
    }

    /** Test a run where --iterations has not been specified. */
    @Test
    public void testRun_missingIterations() throws DeviceNotAvailableException {
        try {
            mNativeTest.run(mTestInfo, mMockListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /** Test a run with default values. */
    @Test
    public void testRun() throws DeviceNotAvailableException {
        mNativeTest.setNumIterations(100);

        mNativeTest.run(mTestInfo, mMockListener);

        verify(mMockDevice)
                .executeShellCommand(
                        Mockito.contains("-s 0 -e 99"),
                        (IShellOutputReceiver) Mockito.any(),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());
        verifyMocks();
    }

    /** Test a stress test execution with two runs. */
    @Test
    public void testRun_twoRuns() throws DeviceNotAvailableException {
        mNativeTest.setNumIterations(100);
        mNativeTest.setNumRuns(2);

        mNativeTest.run(mTestInfo, mMockListener);

        verify(mMockDevice)
                .executeShellCommand(
                        Mockito.contains("-s 0 -e 99"),
                        (IShellOutputReceiver) Mockito.any(),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());
        verify(mMockDevice)
                .executeShellCommand(
                        Mockito.contains("-s 100 -e 199"),
                        (IShellOutputReceiver) Mockito.any(),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());
        verifyMocks();
    }

    /** Test that stress test results are still reported even if device becomes not available */
    @Test
    public void testRun_deviceNotAvailable() throws DeviceNotAvailableException {
        mNativeTest.setNumIterations(100);
        doThrow(new DeviceNotAvailableException("test", "serial"))
                .when(mMockDevice)
                .executeShellCommand(
                        Mockito.contains("-s 0 -e 99"),
                        (IShellOutputReceiver) Mockito.any(),
                        Mockito.anyLong(),
                        (TimeUnit) Mockito.any(),
                        Mockito.anyInt());

        try {
            mNativeTest.run(mTestInfo, mMockListener);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }

        verifyMocks();
    }
}
