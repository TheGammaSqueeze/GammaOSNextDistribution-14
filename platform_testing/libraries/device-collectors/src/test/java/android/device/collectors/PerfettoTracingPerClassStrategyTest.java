/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.device.collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.annotation.SuppressLint;
import android.app.Instrumentation;
import android.os.Bundle;

import androidx.test.runner.AndroidJUnit4;

import com.android.helpers.PerfettoHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

/**
 * Android Unit tests for {@link PerfettoTracingPerClassStrategy}.
 *
 * <p>To run: atest
 * CollectorDeviceLibTest:android.device.collectors.PerfettoTracingPerClassStrategyTest
 */
@RunWith(AndroidJUnit4.class)
public class PerfettoTracingPerClassStrategyTest {
    private Description mRunDesc;
    private Description mTest1Desc;
    private Description mTest2Desc;
    private Description mTest3Desc;
    private DataRecord mDataRecord;
    @Spy private PerfettoHelper mPerfettoHelper;
    @Mock private Instrumentation mInstrumentation;

    @Mock private PerfettoTracingStrategy.WakeLockContext mWakeLockContext;
    @Mock private PerfettoTracingStrategy.WakeLockAcquirer mWakelLockAcquirer;
    @Mock private PerfettoTracingStrategy.WakeLockReleaser mWakeLockReleaser;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mRunDesc = Description.createSuiteDescription("run");
        mTest1Desc = Description.createTestDescription("run", "test1");
        mTest2Desc = Description.createTestDescription("run", "test2");
        mTest3Desc = Description.createTestDescription("run2", "test2");
        mDataRecord = new DataRecord();
        doAnswer(
                        invocation -> {
                            Runnable runnable = invocation.getArgument(0);
                            runnable.run();
                            return null;
                        })
                .when(mWakeLockContext)
                .run(any());
    }

    @SuppressLint("VisibleForTests")
    private PerfettoTracingStrategy initStrategy(Bundle b) {
        mRunDesc = Description.createSuiteDescription("run");
        mTest1Desc = Description.createTestDescription("run", "test1");
        mTest2Desc = Description.createTestDescription("run", "test2");
        mDataRecord = new DataRecord();
        PerfettoTracingStrategy strategy =
                spy(
                        new PerfettoTracingPerClassStrategy(
                                mPerfettoHelper,
                                mInstrumentation,
                                mWakeLockContext,
                                () -> null,
                                mWakelLockAcquirer,
                                mWakeLockReleaser));

        strategy.setup(b);
        return strategy;
    }

    /** Verify perfetto start collection on first test */
    @Test
    public void testPerfettoTraceStartOnFirstTestStart() {
        Bundle b = new Bundle();
        PerfettoTracingStrategy strategy = initStrategy(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());
        // Test run start behavior
        strategy.testRunStart(mDataRecord, mRunDesc);
        verify(mPerfettoHelper, times(0)).startCollecting();
        strategy.testStart(mDataRecord, mTest1Desc);
        verify(mPerfettoHelper, times(1)).startCollecting();
    }

    /** Verify perfetto start collection only once per class */
    @Test
    public void testPerfettoTraceStartOncePerClass() {
        Bundle b = new Bundle();
        PerfettoTracingStrategy strategy = initStrategy(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());
        // Test run start behavior
        strategy.testRunStart(mDataRecord, mRunDesc);
        verify(mPerfettoHelper, times(0)).startCollecting();
        strategy.testStart(mDataRecord, mTest1Desc);
        strategy.testEnd(mDataRecord, mTest1Desc);
        strategy.testStart(mDataRecord, mTest2Desc);
        strategy.testEnd(mDataRecord, mTest2Desc);
        verify(mPerfettoHelper, times(1)).startCollecting();
    }

    /** Verify perfetto start collection only once per class */
    @Test
    public void testPerfettoTraceStartInClassChange() {
        Bundle b = new Bundle();
        PerfettoTracingStrategy strategy = initStrategy(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());
        // Test run start behavior
        strategy.testRunStart(mDataRecord, mRunDesc);
        strategy.testStart(mDataRecord, mTest1Desc);
        strategy.testEnd(mDataRecord, mTest1Desc);
        verify(mPerfettoHelper, times(0)).stopCollecting(anyLong(), anyString());
        strategy.testStart(mDataRecord, mTest3Desc);
        verify(mPerfettoHelper, times(1)).stopCollecting(anyLong(), anyString());
        verify(mPerfettoHelper, times(2)).startCollecting();
    }

    /** Verify perfetto start collection only once per class */
    @Test
    public void testPerfettoTraceEndsOnRunEnds() {
        Bundle b = new Bundle();
        PerfettoTracingStrategy strategy = initStrategy(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());
        // Test run start behavior
        strategy.testRunStart(mDataRecord, mRunDesc);
        strategy.testStart(mDataRecord, mTest1Desc);
        strategy.testEnd(mDataRecord, mTest1Desc);
        verify(mPerfettoHelper, times(0)).stopCollecting(anyLong(), anyString());
        strategy.testRunEnd(mDataRecord, new Result());
        verify(mPerfettoHelper, times(1)).stopCollecting(anyLong(), anyString());
    }

    /** Verify instrumentation metrics are not reported on test end */
    @Test
    public void testDoNotReportMetricOnTest() {
        Bundle b = new Bundle();
        PerfettoTracingStrategy strategy = initStrategy(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());
        DataRecord dataRecord = spy(new DataRecord());
        // Test run start behavior
        strategy.testRunStart(dataRecord, mRunDesc);
        strategy.testStart(dataRecord, mTest1Desc);
        strategy.testEnd(dataRecord, mTest1Desc);
        verify(dataRecord, times(0)).addStringMetric(anyString(), anyString());
    }

    /** Verify instrumentation metrics are reported only on run end */
    @Test
    public void testReportMetricOnRunEndOnly() {
        Bundle b = new Bundle();
        PerfettoTracingStrategy strategy = initStrategy(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());
        DataRecord dataRecord = spy(new DataRecord());
        // Test run start behavior
        strategy.testRunStart(dataRecord, mRunDesc);
        strategy.testStart(dataRecord, mTest1Desc);
        strategy.testEnd(dataRecord, mTest1Desc);
        strategy.testRunEnd(dataRecord, new Result());
        verify(dataRecord, times(1)).addStringMetric(anyString(), anyString());
    }

    /** Verify perfetto start collection only once per class */
    @Test
    public void testReportMultipleMetricsOnRunEnd() {
        Bundle b = new Bundle();
        PerfettoTracingStrategy strategy = initStrategy(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());
        DataRecord dataRecord = spy(new DataRecord());
        // Test run start behavior
        strategy.testRunStart(dataRecord, mRunDesc);
        strategy.testStart(dataRecord, mTest1Desc);
        strategy.testEnd(dataRecord, mTest1Desc);
        strategy.testStart(dataRecord, mTest3Desc);
        strategy.testEnd(dataRecord, mTest3Desc);
        verify(dataRecord, times(0)).addStringMetric(anyString(), anyString());
        strategy.testRunEnd(dataRecord, new Result());
        verify(dataRecord, times(2)).addStringMetric(anyString(), anyString());
    }
}
