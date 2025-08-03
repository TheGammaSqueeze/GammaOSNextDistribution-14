/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Instrumentation;
import android.os.Bundle;
import android.util.ArrayMap;

import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.helpers.SimpleperfHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

/**
 * Android Unit tests for {@link SimpleperfListener}.
 *
 * <p>To run: atest CollectorDeviceLibTest:android.device.collectors.SimpleperfListenerTest
 */
@RunWith(AndroidJUnit4.class)
public class SimpleperfListenerTest {

    // A {@code Description} to pass when faking a test run start call.
    private static final Description FAKE_DESCRIPTION = Description.createSuiteDescription("run");

    private static final Description FAKE_TEST_DESCRIPTION =
            Description.createTestDescription("class", "method");

    private Description mRunDesc;
    private Description mTest1Desc;
    private Description mTest2Desc;
    private Description mTest3Desc;
    private SimpleperfListener mListener;
    @Mock private Instrumentation mInstrumentation;
    @Mock private UiDevice mUiDevice;
    private Map<String, Integer> mInvocationCount;
    private DataRecord mDataRecord;

    private SimpleperfHelper mSimpleperfHelper;
    private SimpleperfHelper mSimpleperfHelperVisibleUidevice;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mSimpleperfHelper = spy(new SimpleperfHelper());
        mSimpleperfHelperVisibleUidevice = spy(new SimpleperfHelper(mUiDevice));
        mRunDesc = Description.createSuiteDescription("run");
        mTest1Desc = Description.createTestDescription("run", "test1");
        mTest2Desc = Description.createTestDescription("run", "test2");
        mTest3Desc = Description.createTestDescription("run", "test3");
    }

    private SimpleperfListener initListener(Bundle b) {
        mInvocationCount = new HashMap<>();

        SimpleperfListener listener =
                spy(new SimpleperfListener(b, mSimpleperfHelper, mInvocationCount));

        mDataRecord = listener.createDataRecord();
        listener.setInstrumentation(mInstrumentation);
        return listener;
    }

    private SimpleperfListener initListener(Bundle b, SimpleperfHelper mHelper) {
        mInvocationCount = new HashMap<>();

        SimpleperfListener listener = spy(new SimpleperfListener(b, mHelper, mInvocationCount));

        mDataRecord = listener.createDataRecord();
        listener.setInstrumentation(mInstrumentation);
        return listener;
    }

    private void testSingleRecordCallsWithUiDevice() throws Exception {
        verify(mSimpleperfHelperVisibleUidevice, times(1)).getPID("surfaceflinger");
        verify(mSimpleperfHelperVisibleUidevice, times(1))
                .startCollecting(eq("record"), eq(" -e instructions -p 680"));
        verify(mUiDevice, times(1))
                .executeShellCommand(
                        "simpleperf record -o /data/local/tmp/perf.data  -e"
                                + " instructions -p 680");
    }

    private void testRecordCallsWithUiDevice() throws Exception {
        verify(mSimpleperfHelperVisibleUidevice, times(1)).getPID("surfaceflinger");
        verify(mSimpleperfHelperVisibleUidevice, times(1)).getPID("system_server");
        verify(mSimpleperfHelperVisibleUidevice, times(1))
                .startCollecting(
                        eq("record"),
                        eq(
                                "-g --post-unwind=yes -f 500 -a --exclude-perf -e"
                                        + " instructions,cpu-cycles -p 1696,680"));
        verify(mUiDevice, times(1))
                .executeShellCommand(
                        "simpleperf record -o /data/local/tmp/perf.data -g --post-unwind=yes -f"
                                + " 500 -a --exclude-perf -e instructions,cpu-cycles -p 1696,680");
    }

    private void testSampleReport() {
        Map<String, String> processes =
                Map.of(
                        "surfaceflinger", "680",
                        "system_server", "1696");

        Map<String /*key*/, String /*eventCount*/> metrics = new ArrayMap<>();
        for (Map.Entry<String, String> process : processes.entrySet()) {
            metrics.putAll(
                    mSimpleperfHelper.getSimpleperfReport(
                            "/data/local/tmp/simpleperf/testdata/simpleperf_record_sample.data",
                            process,
                            Map.of(
                                    "android::Parcel::writeInt32(int)",
                                    "writeInt32",
                                    "android::SurfaceFlinger::commit(long, long, long)",
                                    "commit",
                                    "android::SurfaceFlinger::composite(",
                                    "composite"),
                            10));
        }
        // cherry-pick a few metrics to test
        assertEquals(metrics.get("surfaceflinger-instructions"), "110712160");
        assertEquals(metrics.get("surfaceflinger-composite-cpu-cycles-count"), "2043342");
        assertEquals(metrics.get("surfaceflinger-writeInt32-instructions-percentage"), "0.74");
        assertEquals(metrics.get("system_server-cpu-cycles"), "908094716");
    }

    /*
     * Verify simpleperf start and stop collection methods called exactly once for single test.
     */
    @Test
    public void testSimpleperfPerTestSuccessFlow() throws Exception {
        Bundle b = new Bundle();
        mListener = initListener(b);
        doReturn(true).when(mSimpleperfHelper).startCollecting(anyString(), anyString());
        doReturn(true).when(mSimpleperfHelper).stopCollecting(anyString());
        // Test run start behavior
        mListener.testRunStarted(mRunDesc);

        // Test test start behavior
        mListener.testStarted(mTest1Desc);
        verify(mSimpleperfHelper, times(1)).startCollecting(anyString(), anyString());
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        verify(mSimpleperfHelper, times(1)).stopCollecting(anyString());
    }

    /*
     * Verify stop collecting called exactly once when the test failed and the
     * skip test failure metrics is enabled.
     */
    @Test
    public void testSimpleperfPerTestFailureFlowDefault() throws Exception {
        Bundle b = new Bundle();
        b.putString(SimpleperfListener.SKIP_TEST_FAILURE_METRICS, "false");
        mListener = initListener(b);

        doReturn(true).when(mSimpleperfHelper).startCollecting(anyString(), anyString());
        doReturn(true).when(mSimpleperfHelper).stopCollecting(anyString());
        // Test run start behavior
        mListener.testRunStarted(mRunDesc);

        // Test test start behavior
        mListener.testStarted(mTest1Desc);
        verify(mSimpleperfHelper, times(1)).startCollecting(anyString(), anyString());

        // Test fail behaviour
        Failure failureDesc = new Failure(FAKE_TEST_DESCRIPTION, new Exception());
        mListener.onTestFail(mDataRecord, mTest1Desc, failureDesc);
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        verify(mSimpleperfHelper, times(1)).stopCollecting(anyString());
    }

    /*
     * Verify stop simpleperf called exactly once when the test failed and the
     * skip test failure metrics is enabled.
     */
    @Test
    public void testSimpleperfPerTestFailureFlowWithSkipMmetrics() throws Exception {
        Bundle b = new Bundle();
        b.putString(SimpleperfListener.SKIP_TEST_FAILURE_METRICS, "true");
        mListener = initListener(b);

        doReturn(true).when(mSimpleperfHelper).startCollecting(anyString(), anyString());
        doReturn(true).when(mSimpleperfHelper).stopSimpleperf();
        // Test run start behavior
        mListener.testRunStarted(mRunDesc);

        // Test test start behavior
        mListener.testStarted(mTest1Desc);
        verify(mSimpleperfHelper, times(1)).startCollecting(anyString(), anyString());

        // Test fail behaviour
        Failure failureDesc = new Failure(FAKE_TEST_DESCRIPTION, new Exception());
        mListener.onTestFail(mDataRecord, mTest1Desc, failureDesc);
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        verify(mSimpleperfHelper, times(1)).stopSimpleperf();
    }

    /*
     * Verify simpleperf start and stop collection methods called exactly once for test run.
     * and not during each test method.
     */
    @Test
    public void testSimpleperfPerRunSuccessFlow() throws Exception {
        Bundle b = new Bundle();
        b.putString(SimpleperfListener.COLLECT_PER_RUN, "true");
        mListener = initListener(b);
        doReturn(true).when(mSimpleperfHelper).startCollecting(anyString(), anyString());
        doReturn(true).when(mSimpleperfHelper).stopCollecting(anyString());

        // Test run start behavior
        mListener.onTestRunStart(mListener.createDataRecord(), FAKE_DESCRIPTION);
        verify(mSimpleperfHelper, times(1)).startCollecting(anyString(), anyString());
        mListener.testStarted(mTest1Desc);
        verify(mSimpleperfHelper, times(1)).startCollecting(anyString(), anyString());
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        verify(mSimpleperfHelper, times(0)).stopCollecting(anyString());
        mListener.onTestRunEnd(mListener.createDataRecord(), new Result());
        verify(mSimpleperfHelper, times(1)).stopCollecting(anyString());
    }

    /*
     * Verify simpleperf starts and records only one process and event correctly.
     */
    @Test
    public void testSimpleperfRecordSingleProcessEvent() throws Exception {
        Bundle b = new Bundle();
        b.putString(SimpleperfListener.PROCESSES, "surfaceflinger");
        b.putString(SimpleperfListener.ARGUMENTS, "");
        b.putString(SimpleperfListener.COLLECT_PER_RUN, "true");
        b.putString(
                SimpleperfListener.REPORT_SYMBOLS, "android::SurfaceFlinger::commit(long, long,");
        b.putString(SimpleperfListener.EVENTS, "instructions");
        doReturn("680").when(mUiDevice).executeShellCommand(eq("pidof surfaceflinger"));
        doReturn("").when(mUiDevice).executeShellCommand(eq("pidof simpleperf"));

        mListener = initListener(b, mSimpleperfHelperVisibleUidevice);
        mListener.testRunStarted(mRunDesc);
        testSingleRecordCallsWithUiDevice();
    }

    /*
     * Verify simpleperf starts and records specific processes and events per test run.
     */
    @Test
    public void testSimpleperfPerRunRecordMultipleProcessEvents() throws Exception {
        Bundle b = new Bundle();
        b.putString(SimpleperfListener.PROCESSES, "surfaceflinger, system_server");
        b.putString(SimpleperfListener.COLLECT_PER_RUN, "true");
        b.putString(
                SimpleperfListener.REPORT_SYMBOLS,
                "android::Parcel::writeInt32(int); android::SurfaceFlinger::commit(long, long,"
                        + " long); android::SurfaceFlinger::composite(long, long)");
        b.putString(SimpleperfListener.EVENTS, "instructions, cpu-cycles");
        doReturn("680").when(mUiDevice).executeShellCommand(eq("pidof surfaceflinger"));
        doReturn("1696").when(mUiDevice).executeShellCommand(eq("pidof system_server"));
        doReturn("").when(mUiDevice).executeShellCommand(eq("pidof simpleperf"));

        mListener = initListener(b, mSimpleperfHelperVisibleUidevice);
        mListener.testRunStarted(mRunDesc);
        testRecordCallsWithUiDevice();
    }

    /*
     * Verify simpleperf starts and records specific processes and events during each test.
     */
    @Test
    public void testSimpleperfPerTestRecordMultipleProcessEvents() throws Exception {
        Bundle b = new Bundle();
        b.putString(SimpleperfListener.PROCESSES, "surfaceflinger, system_server");
        b.putString(SimpleperfListener.COLLECT_PER_RUN, "false");
        b.putString(
                SimpleperfListener.REPORT_SYMBOLS,
                "android::Parcel::writeInt32(int); android::SurfaceFlinger::commit(long, long,"
                        + " long); android::SurfaceFlinger::composite(long, long)");
        b.putString(SimpleperfListener.EVENTS, "instructions, cpu-cycles");
        doReturn("680").when(mUiDevice).executeShellCommand(eq("pidof surfaceflinger"));
        doReturn("1696").when(mUiDevice).executeShellCommand(eq("pidof system_server"));
        doReturn("").when(mUiDevice).executeShellCommand(eq("pidof simpleperf"));

        mListener = initListener(b, mSimpleperfHelperVisibleUidevice);
        mListener.testRunStarted(mRunDesc);
        mListener.testStarted(mTest3Desc);
        testRecordCallsWithUiDevice();
    }

    /*
     * Verify simpleperf start and stop and reports specific processes and events that were recorded
     * per test run.
     */
    @Test
    public void testSimpleperfPerRunReport() throws Exception {
        Bundle b = new Bundle();
        b.putString(SimpleperfListener.PROCESSES, "surfaceflinger,system_server");
        b.putString(SimpleperfListener.COLLECT_PER_RUN, "true");
        b.putString(SimpleperfListener.REPORT, "true");
        b.putString(
                SimpleperfListener.REPORT_SYMBOLS,
                "android::Parcel::writeInt32(int); android::SurfaceFlinger::commit(long, long,"
                        + " long); android::SurfaceFlinger::composite(long, long)");
        b.putString(SimpleperfListener.EVENTS, "instructions,cpu-cycles");
        mListener = initListener(b, mSimpleperfHelperVisibleUidevice);
        doReturn("680").when(mUiDevice).executeShellCommand(eq("pidof surfaceflinger"));
        doReturn("1696").when(mUiDevice).executeShellCommand(eq("pidof system_server"));
        doReturn("").when(mUiDevice).executeShellCommand(eq("pidof simpleperf"));
        doReturn(true)
                .when(mSimpleperfHelperVisibleUidevice)
                .startCollecting(anyString(), anyString());

        mListener.testRunStarted(mRunDesc);
        verify(mSimpleperfHelperVisibleUidevice, times(2)).getPID(anyString());
        verify(mSimpleperfHelperVisibleUidevice, times(1))
                .startCollecting(anyString(), anyString());
        mListener.onTestRunEnd(mListener.createDataRecord(), new Result());
        verify(mSimpleperfHelperVisibleUidevice, times(1)).stopCollecting(anyString());
        verify(mSimpleperfHelperVisibleUidevice, times(2))
                .getSimpleperfReport(anyString(), any(), any(), anyInt());
        testSampleReport();
    }

    /*
     * Verify simpleperf start and stop and reports specific processes and events that were recorded
     * per test function.
     */
    @Test
    public void testSimpleperfPerTestReport() throws Exception {
        Bundle b = new Bundle();
        b.putString(SimpleperfListener.PROCESSES, "surfaceflinger,system_server");
        b.putString(SimpleperfListener.COLLECT_PER_RUN, "false");
        b.putString(SimpleperfListener.REPORT, "true");
        b.putString(
                SimpleperfListener.REPORT_SYMBOLS,
                "writeInt32;android::Parcel::writeInt32(int);commit;android::SurfaceFlinger::commit(long,"
                    + " long, long);composite;android::SurfaceFlinger::composite(long, long)");
        b.putString(SimpleperfListener.EVENTS, "instructions,cpu-cycles");
        mListener = initListener(b, mSimpleperfHelperVisibleUidevice);
        doReturn("680").when(mUiDevice).executeShellCommand(eq("pidof surfaceflinger"));
        doReturn("1696").when(mUiDevice).executeShellCommand(eq("pidof system_server"));
        doReturn("").when(mUiDevice).executeShellCommand(eq("pidof simpleperf"));
        doReturn(true)
                .when(mSimpleperfHelperVisibleUidevice)
                .startCollecting(anyString(), anyString());

        mListener.testRunStarted(mRunDesc);
        mListener.testStarted(mTest1Desc);
        verify(mSimpleperfHelperVisibleUidevice, times(2)).getPID(anyString());
        verify(mSimpleperfHelperVisibleUidevice, times(1))
                .startCollecting(anyString(), anyString());
        mListener.onTestEnd(mListener.createDataRecord(), mTest3Desc);
        verify(mSimpleperfHelperVisibleUidevice, times(1)).stopCollecting(anyString());
        verify(mSimpleperfHelperVisibleUidevice, times(2))
                .getSimpleperfReport(anyString(), any(), any(), anyInt());
        testSampleReport();
    }

    /*
     * Verify stop is not called if Simpleperf start did not succeed.
     */
    @Test
    public void testSimpleperfPerRunFailureFlow() throws Exception {
        Bundle b = new Bundle();
        b.putString(SimpleperfListener.COLLECT_PER_RUN, "true");
        mListener = initListener(b);
        doReturn(false).when(mSimpleperfHelper).startCollecting(anyString(), anyString());

        // Test run start behavior
        mListener.onTestRunStart(mListener.createDataRecord(), FAKE_DESCRIPTION);
        verify(mSimpleperfHelper, times(1)).startCollecting(anyString(), anyString());
        mListener.onTestRunEnd(mListener.createDataRecord(), new Result());
        verify(mSimpleperfHelper, times(0)).stopCollecting(anyString());
    }

    /*
     * Verify simpleperf stop is not invoked if start did not succeed.
     */
    @Test
    public void testSimpleperfStartFailureFlow() throws Exception {
        Bundle b = new Bundle();
        mListener = initListener(b);
        doReturn(false).when(mSimpleperfHelper).startCollecting(anyString(), anyString());

        // Test run start behavior
        mListener.testRunStarted(mRunDesc);

        // Test test start behavior
        mListener.testStarted(mTest1Desc);
        verify(mSimpleperfHelper, times(1)).startCollecting(anyString(), anyString());
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        verify(mSimpleperfHelper, times(0)).stopCollecting(anyString());
    }

    /*
     * Verify test method invocation count is updated successfully based on the number of times the
     * test method is invoked.
     */
    @Test
    public void testSimpleperfInvocationCount() throws Exception {
        Bundle b = new Bundle();
        mListener = initListener(b);
        doReturn(true).when(mSimpleperfHelper).startCollecting(anyString(), anyString());
        doReturn(true).when(mSimpleperfHelper).stopCollecting(anyString());

        // Test run start behavior
        mListener.testRunStarted(mRunDesc);

        // Test1 invocation 1 start behavior
        mListener.testStarted(mTest1Desc);
        verify(mSimpleperfHelper, times(1)).startCollecting(anyString(), anyString());
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        verify(mSimpleperfHelper, times(1)).stopCollecting(anyString());

        // Test1 invocation 2 start behaviour
        mListener.testStarted(mTest1Desc);
        verify(mSimpleperfHelper, times(2)).startCollecting(anyString(), anyString());
        mListener.onTestEnd(mDataRecord, mTest1Desc);
        verify(mSimpleperfHelper, times(2)).stopCollecting(anyString());

        // Test2 invocation 1 start behaviour
        mListener.testStarted(mTest2Desc);
        verify(mSimpleperfHelper, times(3)).startCollecting(anyString(), anyString());
        mDataRecord = mListener.createDataRecord();
        mListener.onTestEnd(mDataRecord, mTest2Desc);
        verify(mSimpleperfHelper, times(3)).stopCollecting(anyString());

        // Check if the test count is incremented properly.
        assertEquals(2, (int) mInvocationCount.get(mListener.getTestFileName(mTest1Desc)));
        assertEquals(1, (int) mInvocationCount.get(mListener.getTestFileName(mTest2Desc)));
    }

    /*
     * Verify we can skip record and only report metrics.
     */
    @Test
    public void testSimpleperfSkipRecord() throws Exception {
        Bundle b = new Bundle();
        b.putString(SimpleperfListener.PROCESSES, "surfaceflinger,system_server");
        b.putString(SimpleperfListener.COLLECT_PER_RUN, "false");
        b.putString(SimpleperfListener.REPORT, "true");
        b.putString(SimpleperfListener.RECORD, "false");
        b.putString(
                SimpleperfListener.REPORT_SYMBOLS,
                "writeInt32;android::Parcel::writeInt32(int);"
                        + "commit;android::SurfaceFlinger::commit(long,"
                        + " long, long);composite;android::SurfaceFlinger::composite(long, long)");
        b.putString(SimpleperfListener.EVENTS, "instructions,cpu-cycles");
        mListener = initListener(b, mSimpleperfHelperVisibleUidevice);
        doReturn("680").when(mUiDevice).executeShellCommand(eq("pidof surfaceflinger"));
        doReturn("1696").when(mUiDevice).executeShellCommand(eq("pidof system_server"));
        doReturn("").when(mUiDevice).executeShellCommand(eq("pidof simpleperf"));
        doReturn(true)
                .when(mSimpleperfHelperVisibleUidevice)
                .startCollecting(anyString(), anyString());
        doReturn(true).when(mSimpleperfHelperVisibleUidevice).copyFileOutput(anyString());

        mListener.testRunStarted(mRunDesc);
        mListener.testStarted(mTest1Desc);
        verify(mSimpleperfHelperVisibleUidevice, times(2)).getPID(anyString());
        verify(mSimpleperfHelperVisibleUidevice, never()).startCollecting(anyString(), anyString());
        mListener.onTestEnd(mListener.createDataRecord(), mTest3Desc);
        verify(mSimpleperfHelperVisibleUidevice, never()).stopCollecting(anyString());
        verify(mSimpleperfHelperVisibleUidevice, times(1)).copyFileOutput(anyString());
        verify(mSimpleperfHelperVisibleUidevice, times(2))
                .getSimpleperfReport(anyString(), any(), any(), anyInt());
        testSampleReport();
    }
}
