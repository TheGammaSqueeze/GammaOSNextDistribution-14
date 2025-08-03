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
package android.device.collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
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
 * Android Unit tests for {@link PerfettoListener}.
 *
 * <p>To run: atest CollectorDeviceLibTest:android.device.collectors.PerfettoListenerTest
 */
@SuppressLint("VisibleForTests")
@RunWith(AndroidJUnit4.class)
public class PerfettoListenerTest {

    // A {@code Description} to pass when faking a test run start call.
    private static final Description FAKE_DESCRIPTION = Description.createSuiteDescription("run");

    private static final Description FAKE_TEST_DESCRIPTION = Description
            .createTestDescription("class", "method");

    private Description mRunDesc;
    private Description mTest1Desc;
    private Description mTest2Desc;
    private PerfettoListener mListener;
    @Mock private Instrumentation mInstrumentation;
    private Map<String, Integer> mInvocationCount;
    private DataRecord mDataRecord;

    @Spy private PerfettoHelper mPerfettoHelper;

    @Mock private PerfettoTracingStrategy.WakeLockContext mWakeLockContext;
    @Mock private PerfettoTracingStrategy.WakeLockAcquirer mWakelLockAcquirer;
    @Mock private PerfettoTracingStrategy.WakeLockReleaser mWakeLockReleaser;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mRunDesc = Description.createSuiteDescription("run");
        mTest1Desc = Description.createTestDescription("run", "test1");
        mTest2Desc = Description.createTestDescription("run", "test2");
        doAnswer(
                        invocation -> {
                            Runnable runnable = invocation.getArgument(0);
                            runnable.run();
                            return null;
                        })
                .when(mWakeLockContext)
                .run(any());
    }

    private PerfettoTracingStrategy initStrategy(Bundle b) {
        if (Boolean.parseBoolean(b.getString(PerfettoListener.COLLECT_PER_RUN))) {
            return new PerfettoTracingPerRunStrategy(
                    mPerfettoHelper,
                    mInstrumentation,
                    mWakeLockContext,
                    () -> null,
                    mWakelLockAcquirer,
                    mWakeLockReleaser);
        } else if (Boolean.parseBoolean(b.getString(PerfettoListener.COLLECT_PER_CLASS))) {
            return new PerfettoTracingPerClassStrategy(
                    mPerfettoHelper,
                    mInstrumentation,
                    mWakeLockContext,
                    () -> null,
                    mWakelLockAcquirer,
                    mWakeLockReleaser);
        }

        return new PerfettoTracingPerTestStrategy(
                mPerfettoHelper,
                mInstrumentation,
                mInvocationCount,
                mWakeLockContext,
                () -> null,
                mWakelLockAcquirer,
                mWakeLockReleaser);
    }

    private PerfettoListener initListener(Bundle b) {
        mInvocationCount = new HashMap<>();

        PerfettoListener listener = spy(new PerfettoListener(b, initStrategy(b)));

        mDataRecord = listener.createDataRecord();
        listener.setInstrumentation(mInstrumentation);
        return listener;
    }

    /*
     * Verify perfetto start and stop collection methods called exactly once for single test.
     */
    @Test
    public void testPerfettoPerTestSuccessFlow() throws Exception {
        Bundle b = new Bundle();
        mListener = initListener(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());
        // Test run start behavior
        mListener.testRunStarted(mRunDesc);

        // Test test start behavior
        mListener.testStarted(mTest1Desc);
        verify(mPerfettoHelper, times(1)).startCollecting();
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        verify(mPerfettoHelper, times(1))
                .stopCollecting(
                        anyLong(),
                        eq(
                                "/sdcard/test_results/run_test1/"
                                        + "PerfettoTracingPerTestStrategy/"
                                        + "perfetto_run_test1-1.perfetto-trace"));
    }

    /*
     * Verify stop collecting called exactly once when the test failed and the
     * skip test failure mmetrics is enabled.
     */
    @Test
    public void testPerfettoPerTestFailureFlowDefault() throws Exception {
        Bundle b = new Bundle();
        b.putString(PerfettoTracingStrategy.SKIP_TEST_FAILURE_METRICS, "false");
        mListener = initListener(b);

        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());
        // Test run start behavior
        mListener.testRunStarted(mRunDesc);

        // Test test start behavior
        mListener.testStarted(mTest1Desc);
        verify(mPerfettoHelper, times(1)).startCollecting();

        // Test fail behaviour
        Failure failureDesc = new Failure(FAKE_TEST_DESCRIPTION,
                new Exception());
        mListener.onTestFail(mDataRecord, mTest1Desc, failureDesc);
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        verify(mPerfettoHelper, times(1)).stopCollecting(anyLong(), anyString());

    }

    /*
     * Verify stop perfetto called exactly once when the test failed and the
     * skip test failure metrics is enabled.
     */
    @Test
    public void testPerfettoPerTestFailureFlowWithSkipMmetrics() throws Exception {
        Bundle b = new Bundle();
        b.putString(PerfettoTracingStrategy.SKIP_TEST_FAILURE_METRICS, "true");
        mListener = initListener(b);

        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopPerfetto(anyInt());
        // Test run start behavior
        mListener.testRunStarted(mRunDesc);

        // Test test start behavior
        mListener.testStarted(mTest1Desc);
        verify(mPerfettoHelper, times(1)).startCollecting();

        // Test fail behaviour
        Failure failureDesc = new Failure(FAKE_TEST_DESCRIPTION,
                new Exception());
        mListener.onTestFail(mDataRecord, mTest1Desc, failureDesc);
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        verify(mPerfettoHelper, times(1)).stopPerfetto(anyInt());

    }

    /*
     * Verify the default time to wait before starting the perfetto trace.
     */
    @Test
    public void testPerfettoDefaultStartWaitTime() throws Exception {
        Bundle b = new Bundle();
        mListener = initListener(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());
        // Test run start behavior
        mListener.testRunStarted(mRunDesc);

        // Test test start behavior
        long startTime = System.currentTimeMillis();
        mListener.testStarted(mTest1Desc);
        long endTime = System.currentTimeMillis();
        verify(mPerfettoHelper, times(1)).startCollecting();
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        verify(mPerfettoHelper, times(1)).stopCollecting(anyLong(), anyString());
        // Test for wait time of 3 secs before starting the trace.
        assertTrue(
                (endTime - startTime)
                        >= Long.parseLong(PerfettoTracingStrategy.DEFAULT_START_WAIT_TIME_MSECS));
    }

    /*
     * Verify the custom time to wait before starting the perfetto trace.
     */
    @Test
    public void testPerfettoCustomStartWaitTime() throws Exception {

        Bundle b = new Bundle();
        b.putString(PerfettoTracingStrategy.PERFETTO_START_WAIT_TIME_ARG, "10000");
        mListener = initListener(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());
        // Test run start behavior
        mListener.testRunStarted(mRunDesc);

        // Test test start behavior
        long startTime = System.currentTimeMillis();
        mListener.testStarted(mTest1Desc);
        long endTime = System.currentTimeMillis();
        verify(mPerfettoHelper, times(1)).startCollecting();
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        verify(mPerfettoHelper, times(1)).stopCollecting(anyLong(), anyString());
        assertTrue((endTime - startTime) >= 10000);
    }

    /*
     * Verify perfetto start and stop collection methods called exactly once for test run.
     * and not during each test method.
     */
    @Test
    public void testPerfettoPerRunSuccessFlow() throws Exception {
        Bundle b = new Bundle();
        b.putString(PerfettoListener.COLLECT_PER_RUN, "true");
        mListener = initListener(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());

        // Test run start behavior
        mListener.testRunStarted(FAKE_DESCRIPTION);
        verify(mPerfettoHelper, times(1)).startCollecting();
        mListener.testStarted(mTest1Desc);
        verify(mPerfettoHelper, times(1)).startCollecting();
        mListener.testFinished(mTest1Desc);
        verify(mPerfettoHelper, times(0)).stopCollecting(anyLong(), anyString());
        mListener.testRunFinished(new Result());
        verify(mPerfettoHelper, times(1)).stopCollecting(anyLong(), anyString());
    }

    @Test
    public void testRunWithWakeLockHoldsAndReleasesAWakelock() {
        Bundle b = new Bundle();
        mListener = initListener(b);
        mListener.runWithWakeLock(() -> {});
        verify(mWakelLockAcquirer, times(1)).acquire(any());
        verify(mWakeLockReleaser, times(1)).release(any());
    }

    @Test
    public void testRunWithWakeLockHoldsAndReleasesAWakelockWhenThereIsAnException() {
        Bundle b = new Bundle();
        mListener = initListener(b);
        try {
            mListener.runWithWakeLock(
                    () -> {
                        throw new RuntimeException("thrown on purpose");
                    });
        } catch (RuntimeException expected) {
            verify(mWakelLockAcquirer, times(1)).acquire(any());
            verify(mWakeLockReleaser, times(1)).release(any());
            return;
        }

        fail();
    }

    /*
     * Verify no wakelock is held and released when option is disabled (per run case).
     */
    @Test
    public void testPerfettoDoesNotHoldWakeLockPerRun() throws Exception {
        Bundle b = new Bundle();
        b.putString(PerfettoListener.COLLECT_PER_RUN, "true");
        mListener = initListener(b);

        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());

        // Test run start behavior
        mListener.onTestRunStart(mListener.createDataRecord(), FAKE_DESCRIPTION);
        mListener.testStarted(mTest1Desc);
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        mListener.onTestRunEnd(mListener.createDataRecord(), new Result());
        verify(mWakeLockContext, never()).run(any());
    }

    /*
     * Verify no wakelock is held and released when option is disabled (per test case).
     */
    @Test
    public void testPerfettoDoesNotHoldWakeLockPerTest() throws Exception {
        Bundle b = new Bundle();
        mListener = initListener(b);

        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());

        // Test run start behavior
        mListener.onTestRunStart(mListener.createDataRecord(), FAKE_DESCRIPTION);
        mListener.testStarted(mTest1Desc);
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        mListener.onTestRunEnd(mListener.createDataRecord(), new Result());
        verify(mWakeLockContext, never()).run(any());
    }

    /*
     * Verify a wakelock is held and released onTestRunStart when enabled.
     */
    @Test
    public void testHoldWakeLockOnTestRunStart() throws Exception {
        Bundle b = new Bundle();
        b.putString(PerfettoTracingStrategy.HOLD_WAKELOCK_WHILE_COLLECTING, "true");
        b.putString(PerfettoListener.COLLECT_PER_RUN, "true");
        mListener = initListener(b);

        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());

        // Test run start behavior
        mListener.testRunStarted(FAKE_DESCRIPTION);
        // Verify wakelock was held and released
        verify(mWakeLockContext, times(1)).run(any());
    }

    /*
     * Verify a wakelock is held and released on onTestStart.
     */
    @Test
    public void testHoldWakeLockOnTestStart() throws Exception {
        Bundle b = new Bundle();
        b.putString(PerfettoTracingStrategy.HOLD_WAKELOCK_WHILE_COLLECTING, "true");
        mListener = initListener(b);

        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());

        // Test run start behavior
        mListener.testRunStarted(FAKE_DESCRIPTION);
        // There shouldn't be a wakelock held/released onTestRunStart
        verify(mWakeLockContext, times(0)).run(any());
        mListener.testStarted(mTest1Desc);
        // There should be a wakelock held/released onTestStart
        verify(mWakeLockContext, times(1)).run(any());
    }

    /*
     * Verify wakelock is held and released in onTestEnd when option is enabled.
     */
    @Test
    public void testHoldWakeLockOnTestEnd() throws Exception {
        Bundle b = new Bundle();
        b.putString(PerfettoTracingStrategy.HOLD_WAKELOCK_WHILE_COLLECTING, "true");
        mListener = initListener(b);

        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());

        // Test run start behavior
        mListener.testRunStarted(FAKE_DESCRIPTION);
        mListener.testStarted(mTest1Desc);
        // There is one wakelock after the test/run starts
        verify(mWakeLockContext, times(1)).run(any());
        mListener.testFinished(mTest1Desc);
        // There should be a wakelock held and released onTestEnd
        verify(mWakeLockContext, times(2)).run(any());
        mListener.testRunFinished(new Result());
        // There shouldn't be more wakelocks are held onTestRunEnd
        verify(mWakeLockContext, times(2)).run(any());
    }

    /*
     * Verify wakelock is held and released in onTestRunEnd when option is enabled.
     */
    @Test
    public void testHoldWakeLockOnTestRunEnd() throws Exception {
        Bundle b = new Bundle();
        b.putString(PerfettoTracingStrategy.HOLD_WAKELOCK_WHILE_COLLECTING, "true");
        b.putString(PerfettoListener.COLLECT_PER_RUN, "true");
        mListener = initListener(b);

        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());

        // Test run start behavior
        mListener.testRunStarted(FAKE_DESCRIPTION);
        mListener.testStarted(mTest1Desc);
        // There is one wakelock after the test/run start
        verify(mWakeLockContext, times(1)).run(any());

        mListener.testFinished(mTest1Desc);
        // There shouldn't be a wakelock held/released onTestEnd.
        verify(mWakeLockContext, times(1)).run(any());
        mListener.testRunFinished(new Result());
        // There should be a new wakelock held/released onTestRunEnd.
        verify(mWakeLockContext, times(2)).run(any());
    }

    /*
     * Verify stop is not called if Perfetto start did not succeed.
     */
    @Test
    public void testPerfettoPerRunFailureFlow() throws Exception {
        Bundle b = new Bundle();
        b.putString(PerfettoListener.COLLECT_PER_RUN, "true");
        mListener = initListener(b);
        doReturn(false).when(mPerfettoHelper).startCollecting();

        // Test run start behavior
        mListener.testRunStarted(FAKE_DESCRIPTION);
        verify(mPerfettoHelper, times(1)).startCollecting();
        mListener.testRunFinished(new Result());
        verify(mPerfettoHelper, times(0)).stopCollecting(anyLong(), anyString());
    }

    /*
     * Verify perfetto stop is not invoked if start did not succeed.
     */
    @Test
    public void testPerfettoStartFailureFlow() throws Exception {
        Bundle b = new Bundle();
        mListener = initListener(b);
        doReturn(false).when(mPerfettoHelper).startCollecting();

        // Test run start behavior
        mListener.testRunStarted(mRunDesc);

        // Test test start behavior
        mListener.testStarted(mTest1Desc);
        verify(mPerfettoHelper, times(1)).startCollecting();
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        verify(mPerfettoHelper, times(0)).stopCollecting(anyLong(), anyString());
    }

    /*
     * Verify test method invocation count is updated successfully based on the number of times the
     * test method is invoked.
     */
    @Test
    public void testPerfettoInvocationCount() throws Exception {
        Bundle b = new Bundle();
        mListener = initListener(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());

        // Test run start behavior
        mListener.testRunStarted(mRunDesc);

        // Test1 invocation 1 start behavior
        mListener.testStarted(mTest1Desc);
        verify(mPerfettoHelper, times(1)).startCollecting();
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        verify(mPerfettoHelper, times(1)).stopCollecting(anyLong(), anyString());

        // Test1 invocation 2 start behaviour
        mListener.testStarted(mTest1Desc);
        verify(mPerfettoHelper, times(2)).startCollecting();
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        verify(mPerfettoHelper, times(2)).stopCollecting(anyLong(), anyString());

        // Test2 invocation 1 start behaviour
        mListener.testStarted(mTest2Desc);
        verify(mPerfettoHelper, times(3)).startCollecting();
        mDataRecord = mListener.createDataRecord();
        mListener.onTestEnd(mDataRecord, mTest2Desc);
        verify(mPerfettoHelper, times(3)).stopCollecting(anyLong(), anyString());

        // Check if the test count is incremented properly.
        assertEquals(2, (int) mInvocationCount.get(mListener.getTestFileName(mTest1Desc)));
        assertEquals(1, (int) mInvocationCount.get(mListener.getTestFileName(mTest2Desc)));

    }

    /*
     * Verify perfetto start and stop collection methods called when the text
     * proto config option is enabled
     */
    @Test
    public void testPerfettoSuccessFlowWithTextConfig() throws Exception {
        Bundle b = new Bundle();
        b.putString(PerfettoTracingStrategy.PERFETTO_CONFIG_TEXT_PROTO, "true");
        mListener = initListener(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());
        // Test run start behavior
        mListener.testRunStarted(mRunDesc);

        // Test tetest start behavior
        mListener.testStarted(mTest1Desc);
        verify(mPerfettoHelper, times(1)).startCollecting();
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        verify(mPerfettoHelper, times(1)).stopCollecting(anyLong(), anyString());

    }

    /*
     * Verify spaces in the test description are replaced with special character. Test description
     * is used for creating the perfetto file, if the spaces are not replaced then tradefed content
     * provider will throw an error for the files with spaces.
     */
    @Test
    public void testPerfettoTestNameWithSpaces() throws Exception {
        Bundle b = new Bundle();
        mListener = initListener(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());
        // Test run start behavior
        mListener.testRunStarted(mRunDesc);

        Description mTest1DescWithSpaces = Description.createTestDescription("run  123",
                "test1 456");
        // Test test start behavior
        mListener.testStarted(mTest1DescWithSpaces);
        verify(mPerfettoHelper, times(1)).startCollecting();
        mListener.onTestEnd(mDataRecord, mTest1DescWithSpaces);
        verify(mPerfettoHelper, times(1))
                .stopCollecting(
                        anyLong(),
                        eq(
                                "/sdcard/test_results/run#123_test1#456/"
                                        + "PerfettoTracingPerTestStrategy/"
                                        + "perfetto_run#123_test1#456-1.perfetto-trace"));
    }
}
