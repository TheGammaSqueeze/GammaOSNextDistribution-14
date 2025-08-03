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

package android.tools.collectors;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Instrumentation;
import android.device.collectors.DataRecord;
import android.device.collectors.PerfettoListener;
import android.device.collectors.PerfettoTracingPerClassStrategy;
import android.device.collectors.PerfettoTracingPerRunStrategy;
import android.device.collectors.PerfettoTracingPerTestStrategy;
import android.device.collectors.PerfettoTracingStrategy;
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

import java.io.IOException;

/**
 * Android Unit tests for {@link DefaultUITraceListener}.
 *
 * <p>To run: atest CollectorDeviceLibTest:android.tools.collectors.DefaultUITraceListenerTest
 */
@RunWith(AndroidJUnit4.class)
public class DefaultUITraceListenerTest {
    // A {@code Description} to pass when faking a test run start call.
    private static final Description FAKE_DESCRIPTION = Description.createSuiteDescription("run");

    private static final Description FAKE_TEST_DESCRIPTION =
            Description.createTestDescription("class", "method");

    private Description mRunDesc;
    private Description mTest1Desc;
    private Description mTest2Desc;
    private DefaultUITraceListener mListener;
    @Mock private Instrumentation mInstrumentation;
    private DataRecord mDataRecord;

    @Spy private PerfettoHelper mPerfettoHelper;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        mPerfettoHelper.stopPerfettoProcesses(mPerfettoHelper.getPerfettoPids());
        mRunDesc = Description.createSuiteDescription("run");
        mTest1Desc = Description.createTestDescription("run", "test1");
        mTest2Desc = Description.createTestDescription("run2", "test2");
    }

    private PerfettoTracingStrategy initStrategy(Bundle b) {
        if (Boolean.parseBoolean(b.getString(PerfettoListener.COLLECT_PER_RUN))) {
            return new PerfettoTracingPerRunStrategy(mPerfettoHelper, mInstrumentation);
        } else if (Boolean.parseBoolean(b.getString(PerfettoListener.COLLECT_PER_CLASS))) {
            return new PerfettoTracingPerClassStrategy(mPerfettoHelper, mInstrumentation);
        }

        return new PerfettoTracingPerTestStrategy(mPerfettoHelper, mInstrumentation);
    }

    private DefaultUITraceListener initListener(Bundle b) {
        DefaultUITraceListener listener = spy(new DefaultUITraceListener(b, initStrategy(b)));

        mDataRecord = new DataRecord();
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

        // Test the test start behavior
        mListener.testStarted(mTest1Desc);
        verify(mPerfettoHelper, times(1)).startCollecting();
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        verify(mPerfettoHelper, times(1))
                .stopCollecting(
                        anyLong(),
                        eq(
                                "/sdcard/test_results/run_test1/PerfettoTracingPerTestStrategy/"
                                        + "uiTrace_run_test1-1.perfetto-trace"));
    }

    /*
     * Verify stop collecting called exactly once when the test failed and the
     * skip test failure metrics is enabled.
     */
    @Test
    public void testPerfettoPerTestFailureFlowDefault() throws Exception {
        Bundle b = new Bundle();
        b.putString(PerfettoTracingStrategy.SKIP_TEST_FAILURE_METRICS, "false");
        b.putString(PerfettoListener.COLLECT_PER_RUN, "false");
        mListener = initListener(b);

        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());
        // Test run start behavior
        mListener.testRunStarted(mRunDesc);

        // Test the test start behavior
        mListener.testStarted(mTest1Desc);
        verify(mPerfettoHelper, times(1)).startCollecting();

        // Test fail behaviour
        Failure failureDesc = new Failure(FAKE_TEST_DESCRIPTION, new Exception());
        mListener.onTestFail(mDataRecord, mTest1Desc, failureDesc);
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        verify(mPerfettoHelper, times(1)).stopCollecting(anyLong(), anyString());
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

        // Test the test start behavior
        mListener.testStarted(mTest1Desc);
        verify(mPerfettoHelper, times(1)).startCollecting();
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        verify(mPerfettoHelper, times(0)).stopCollecting(anyLong(), anyString());
    }

    /*
     * Verify perfetto stop is invoked once per class.
     */
    @Test
    public void testPerfettoClassFlowOneClassOneTest() throws Exception {
        Bundle b = new Bundle();
        b.putString(PerfettoListener.COLLECT_PER_CLASS, "true");
        mListener = initListener(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());

        // Test run start behavior
        mListener.testRunStarted(FAKE_DESCRIPTION);
        // Test multiple tests per fun
        mListener.testStarted(mTest1Desc);
        verify(mPerfettoHelper, times(1)).startCollecting();
        mListener.testFinished(mTest1Desc);
        verify(mPerfettoHelper, times(0)).stopCollecting(anyLong(), anyString());
        mListener.testRunFinished(new Result());
        verify(mPerfettoHelper, times(1)).stopCollecting(anyLong(), anyString());
    }

    /*
     * Verify perfetto stop is invoked once per class.
     */
    @Test
    public void testPerfettoClassFlowOneClassMultipleTests() throws Exception {
        Bundle b = new Bundle();
        b.putString(PerfettoListener.COLLECT_PER_CLASS, "true");
        mListener = initListener(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());

        // Test run start behavior
        mListener.testRunStarted(FAKE_DESCRIPTION);
        // Test multiple tests per fun
        mListener.testStarted(mTest1Desc);
        verify(mPerfettoHelper, times(1)).startCollecting();
        mListener.testFinished(mTest1Desc);
        mListener.testStarted(mTest1Desc);
        mListener.testFinished(mTest1Desc);
        verify(mPerfettoHelper, times(0)).stopCollecting(anyLong(), anyString());
        mListener.testRunFinished(new Result());
        verify(mPerfettoHelper, times(1)).stopCollecting(anyLong(), anyString());
    }

    /*
     * Verify perfetto stop is invoked once per class.
     */
    @Test
    public void testPerfettoClassFlowMultipleClasses() throws Exception {
        Bundle b = new Bundle();
        b.putString(PerfettoListener.COLLECT_PER_CLASS, "true");
        mListener = initListener(b);
        doReturn(true).when(mPerfettoHelper).startCollecting();
        doReturn(true).when(mPerfettoHelper).stopCollecting(anyLong(), anyString());

        // Test run start behavior
        mListener.testRunStarted(FAKE_DESCRIPTION);
        // Test multiple tests per fun
        mListener.testStarted(mTest1Desc);
        verify(mPerfettoHelper, times(1)).startCollecting();
        mListener.testFinished(mTest1Desc);
        verify(mPerfettoHelper, times(0)).stopCollecting(anyLong(), anyString());
        mListener.testStarted(mTest2Desc);
        verify(mPerfettoHelper, times(1)).stopCollecting(anyLong(), anyString());
        verify(mPerfettoHelper, times(2)).startCollecting();
        mListener.testFinished(mTest2Desc);
        mListener.testStarted(mTest2Desc);
        mListener.testFinished(mTest2Desc);
        verify(mPerfettoHelper, times(1)).stopCollecting(anyLong(), anyString());
        verify(mPerfettoHelper, times(2)).startCollecting();
        mListener.testRunFinished(new Result());
        verify(mPerfettoHelper, times(2)).stopCollecting(anyLong(), anyString());
    }
}
