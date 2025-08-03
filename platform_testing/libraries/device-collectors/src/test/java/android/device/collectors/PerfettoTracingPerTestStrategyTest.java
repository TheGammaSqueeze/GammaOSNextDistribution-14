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
import org.junit.runner.notification.Failure;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.HashMap;
import java.util.Map;

/**
 * Android Unit tests for {@link PerfettoTracingPerTestStrategy}.
 *
 * <p>To run: atest
 * CollectorDeviceLibTest:android.device.collectors.PerfettoTracingPerRunStrategyTest
 */
@RunWith(AndroidJUnit4.class)
public class PerfettoTracingPerTestStrategyTest {
    private Description mRunDesc;
    private Description mTest1Desc;
    private Description mTest2Desc;
    private DataRecord mDataRecord;
    private static final Description FAKE_TEST_DESCRIPTION =
            Description.createTestDescription("class", "method");

    private Map<String, Integer> mInvocationCount;
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
        mInvocationCount = new HashMap<>();
        PerfettoTracingStrategy strategy =
                spy(
                        new PerfettoTracingPerTestStrategy(
                                mPerfettoHelper,
                                mInstrumentation,
                                mInvocationCount,
                                mWakeLockContext,
                                () -> null,
                                mWakelLockAcquirer,
                                mWakeLockReleaser));

        strategy.setup(b);
        return strategy;
    }

    /** Verify perfetto start collection on run start multiple times */
    @Test
    public void testPerfettoTraceStartOnTestStart() {
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

    /** Verify perfetto start collection on run start */
    @Test
    public void testPerfettoTraceStartOnTestStartMultiple() {
        Bundle b = new Bundle();
        PerfettoTracingStrategy strategy = initStrategy(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());
        // Test run start behavior
        strategy.testRunStart(mDataRecord, mRunDesc);
        verify(mPerfettoHelper, times(0)).startCollecting();
        strategy.testStart(mDataRecord, mTest1Desc);
        strategy.testEnd(mDataRecord, mTest1Desc);
        verify(mPerfettoHelper, times(1)).startCollecting();
        strategy.testStart(mDataRecord, mTest2Desc);
        verify(mPerfettoHelper, times(2)).startCollecting();
    }

    /** Verify perfetto stop collection on run end */
    @Test
    public void testPerfettoTraceEndOnRunEnd() {
        Bundle b = new Bundle();
        PerfettoTracingStrategy strategy = initStrategy(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());
        // Test run start behavior
        strategy.testRunStart(mDataRecord, mRunDesc);
        strategy.testStart(mDataRecord, mTest1Desc);
        verify(mPerfettoHelper, times(1)).startCollecting();
        strategy.testEnd(mDataRecord, mTest1Desc);
        verify(mPerfettoHelper, times(1)).stopCollecting(anyLong(), anyString());
        strategy.testRunEnd(mDataRecord, new Result());
        verify(mPerfettoHelper, times(1)).stopCollecting(anyLong(), anyString());
    }

    /** Verify perfetto stop collection on run end multiple times */
    @Test
    public void testPerfettoTraceEndOnRunEndMultiple() {
        Bundle b = new Bundle();
        PerfettoTracingStrategy strategy = initStrategy(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());
        // Test run start behavior
        strategy.testRunStart(mDataRecord, mRunDesc);
        strategy.testStart(mDataRecord, mTest1Desc);
        strategy.testEnd(mDataRecord, mTest1Desc);
        strategy.testStart(mDataRecord, mTest2Desc);
        verify(mPerfettoHelper, times(2)).startCollecting();
        strategy.testEnd(mDataRecord, mTest2Desc);
        verify(mPerfettoHelper, times(2)).stopCollecting(anyLong(), anyString());
        strategy.testRunEnd(mDataRecord, new Result());
        verify(mPerfettoHelper, times(2)).stopCollecting(anyLong(), anyString());
    }

    /*
     * Verify perfetto does not start collection on test start
     */
    @Test
    public void testPerfettoTraceDoesNotStartOnRunStart() {
        Bundle b = new Bundle();
        PerfettoTracingStrategy strategy = initStrategy(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());
        // Test run start behavior
        strategy.testRunStart(mDataRecord, mRunDesc);
        verify(mPerfettoHelper, times(0)).stopCollecting(anyLong(), anyString());
        strategy.testRunEnd(mDataRecord, new Result());
        verify(mPerfettoHelper, times(0)).stopCollecting(anyLong(), anyString());
    }

    /*
     * Verify perfetto does not stop collection on failure
     */
    @Test
    public void testPerfettoTraceDoesNotStopOnFailure() {
        Bundle b = new Bundle();
        PerfettoTracingStrategy strategy = initStrategy(b);
        doReturn(false).when(mPerfettoHelper).startCollecting();
        // Test run start behavior
        strategy.testRunStart(mDataRecord, mRunDesc);
        strategy.testStart(mDataRecord, mTest1Desc);

        Failure failureDesc = new Failure(FAKE_TEST_DESCRIPTION, new Exception());
        strategy.testFail(mDataRecord, mTest1Desc, failureDesc);
        verify(mPerfettoHelper, times(1)).startCollecting();
        verify(mPerfettoHelper, times(0)).stopCollecting(anyLong(), anyString());
    }
}
