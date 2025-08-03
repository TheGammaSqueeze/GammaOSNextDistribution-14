/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Instrumentation;
import android.os.Bundle;

import androidx.test.runner.AndroidJUnit4;

import com.android.helpers.HeapDumpHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link HeapDumpListener} specific behavior. */
@RunWith(AndroidJUnit4.class)
public final class HeapDumpListenerTest {

    // A {@code Description} to pass when faking a test run start call.
    private static final Description RUN_DESCRIPTION = Description.createSuiteDescription("run");
    private static final Description TEST_DESCRIPTION_1 =
            Description.createTestDescription("run", "test_one");
    private static final Description TEST_DESCRIPTION_2 =
            Description.createTestDescription("run", "test_two");

    @Mock private HeapDumpHelper mHelper;
    @Mock private Instrumentation mInstrumentation;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    /** Test heapdump collection enabled for all iterations*/
    @Test
    public void testHeapCollectionEnableAllIterations() throws Exception {
        Bundle enableAllBundle = new Bundle();
        enableAllBundle.putString(HeapDumpListener.ITERATION_ALL_ENABLE, String.valueOf(true));
        HeapDumpListener collector = new HeapDumpListener(enableAllBundle, mHelper);
        collector.setInstrumentation(mInstrumentation);

        collector.testRunStarted(RUN_DESCRIPTION);
        collector.testStarted(TEST_DESCRIPTION_1);
        verify(mHelper, times(1)).startCollecting("run_test_one_1");
    }

    /** Test heapdump collection force disable for all iterations.*/
    @Test
    public void testHeapCollectionEnableAllFlagDisabled() throws Exception {
        Bundle disableAllBundle = new Bundle();
        disableAllBundle.putString(HeapDumpListener.ITERATION_ALL_ENABLE, String.valueOf(false));
        HeapDumpListener collector = new HeapDumpListener(disableAllBundle, mHelper);
        collector.setInstrumentation(mInstrumentation);

        collector.testRunStarted(RUN_DESCRIPTION);
        collector.testStarted(TEST_DESCRIPTION_1);
        verify(mHelper, times(0)).startCollecting("run_test_one_1");
    }

    /** Test heapdump collection disabled for all iterations by default.*/
    @Test
    public void testHeapCollectionEnableAllDisabledByDefault() throws Exception {
        Bundle defaultDisabledAllBundle = new Bundle();
        HeapDumpListener collector = new HeapDumpListener(defaultDisabledAllBundle, mHelper);
        collector.setInstrumentation(mInstrumentation);

        collector.testRunStarted(RUN_DESCRIPTION);
        collector.testStarted(TEST_DESCRIPTION_1);
        verify(mHelper, times(0)).startCollecting("run_test_one_1");
    }

    /** Test heapdump collection enabled only for the 2nd and 3rd iterations.*/
    @Test
    public void testHeapCollectionSpecificIterations() throws Exception {
        Bundle enableSpecificIterationsBundle = new Bundle();
        enableSpecificIterationsBundle.putString(
                HeapDumpListener.ENABLE_ITERATION_IDS, "2,3");
        HeapDumpListener collector = new HeapDumpListener(enableSpecificIterationsBundle, mHelper);
        collector.setInstrumentation(mInstrumentation);

        collector.testRunStarted(RUN_DESCRIPTION);
        collector.testStarted(TEST_DESCRIPTION_1);
        collector.testStarted(TEST_DESCRIPTION_1);
        collector.testStarted(TEST_DESCRIPTION_1);
        verify(mHelper, times(1)).startCollecting("run_test_one_2");
        verify(mHelper, times(1)).startCollecting("run_test_one_3");
    }

    /** Test heapdump collection enabled only for the 2nd and 3rd iterations.*/
    @Test
    public void testHeapCollectionWithProcessNames() throws Exception {
        Bundle enableSpecificIterationsBundle = new Bundle();
        enableSpecificIterationsBundle.putString(
                HeapDumpListener.ENABLE_ITERATION_IDS, "2,3");
        enableSpecificIterationsBundle.putString(
                HeapDumpListener.PROCESS_NAMES_KEY, "system_server");
        HeapDumpListener collector = new HeapDumpListener(enableSpecificIterationsBundle, mHelper);
        collector.setInstrumentation(mInstrumentation);

        collector.testRunStarted(RUN_DESCRIPTION);
        collector.testStarted(TEST_DESCRIPTION_1);
        collector.testStarted(TEST_DESCRIPTION_1);
        collector.testStarted(TEST_DESCRIPTION_1);
        verify(mHelper, times(1)).setUp(HeapDumpListener.DEFAULT_OUTPUT_DIR,  "system_server");
        verify(mHelper, times(1)).startCollecting("run_test_one_2");
        verify(mHelper, times(1)).startCollecting("run_test_one_3");
    }

    /** Test heapdump collection enabled only for the 2nd iterations during multiple tests.*/
    @Test
    public void testHeapCollectionSpecificIterationsMultipleTests() throws Exception {
        Bundle enableSpecificIterationsBundle = new Bundle();
        enableSpecificIterationsBundle.putString(
                HeapDumpListener.ENABLE_ITERATION_IDS, "2");
        HeapDumpListener collector = new HeapDumpListener(enableSpecificIterationsBundle, mHelper);
        collector.setInstrumentation(mInstrumentation);

        collector.testRunStarted(RUN_DESCRIPTION);
        collector.testStarted(TEST_DESCRIPTION_1);
        collector.testStarted(TEST_DESCRIPTION_1);
        collector.testStarted(TEST_DESCRIPTION_2);
        collector.testStarted(TEST_DESCRIPTION_2);
        verify(mHelper, times(1)).startCollecting("run_test_one_2");
        verify(mHelper, times(1)).startCollecting("run_test_two_2");
    }

    /** Test heapdump collection enabled for all the iterations and overrides the
     * individual iteration id flag.*/
    @Test
    public void testEnableAllOverrideIndvidualIterationIdFlag() throws Exception {
        Bundle enableSpecificIterationsBundle = new Bundle();
        enableSpecificIterationsBundle.putString(
                HeapDumpListener.ENABLE_ITERATION_IDS, "2");
        enableSpecificIterationsBundle.putString(
                HeapDumpListener.ITERATION_ALL_ENABLE, String.valueOf(true));
        HeapDumpListener collector = new HeapDumpListener(enableSpecificIterationsBundle, mHelper);
        collector.setInstrumentation(mInstrumentation);

        collector.testRunStarted(RUN_DESCRIPTION);
        collector.testStarted(TEST_DESCRIPTION_1);
        collector.testStarted(TEST_DESCRIPTION_1);
        collector.testStarted(TEST_DESCRIPTION_2);
        collector.testStarted(TEST_DESCRIPTION_2);
        verify(mHelper, times(1)).startCollecting("run_test_one_1");
        verify(mHelper, times(1)).startCollecting("run_test_one_2");
        verify(mHelper, times(1)).startCollecting("run_test_two_1");
        verify(mHelper, times(1)).startCollecting("run_test_two_2");
    }

    /** Test to verify the iteration separator is handled from the test class name.*/
    @Test
    public void testHeapCollectionTestWithIterationSeparator() throws Exception {
        Bundle enableAllBundle = new Bundle();
        enableAllBundle.putString(HeapDumpListener.ITERATION_ALL_ENABLE, String.valueOf(true));
        HeapDumpListener collector = new HeapDumpListener(enableAllBundle, mHelper);
        collector.setInstrumentation(mInstrumentation);

        collector.testRunStarted(RUN_DESCRIPTION);
        collector.testStarted(Description.createTestDescription("run$1", "test_two"));
        verify(mHelper, times(1)).startCollecting("run_test_two_1");
    }

    /** Test to verify native heap dump flag is parsed correctly. */
    @Test
    public void testNativeHeapDumpFlag() throws Exception {
        Bundle nativeHeapDumpBundle = new Bundle();
        nativeHeapDumpBundle.putString(
                HeapDumpListener.ENABLE_NATIVE_HEAPDUMP, String.valueOf(true));
        HeapDumpListener collector = new HeapDumpListener(nativeHeapDumpBundle, mHelper);
        collector.setInstrumentation(mInstrumentation);

        collector.testRunStarted(RUN_DESCRIPTION);
        collector.testStarted(Description.createTestDescription("run$1", "test_two"));
        verify(mHelper, times(1)).enableNativeHeapDump();
    }

    /** Test to verify per test run heapdump collection. */
    @Test
    public void testHeapCollectionOnlyForTestRun() throws Exception {
        Bundle collectPerRunBundle = new Bundle();
        collectPerRunBundle.putString(HeapDumpListener.COLLECT_PER_RUN, "true");
        HeapDumpListener collector = new HeapDumpListener(collectPerRunBundle, mHelper);
        collector.setInstrumentation(mInstrumentation);
        collector.testRunStarted(RUN_DESCRIPTION);
        verify(mHelper, times(1)).startCollecting("heapdump_1");
    }

    /** Test to verify per test run heap dump collection flag overrides the per test method flag.*/
    @Test
    public void testHeapCollectionForTestRunOverridesPerTest() throws Exception {
        Bundle collectPerRunBundle = new Bundle();
        collectPerRunBundle.putString(HeapDumpListener.COLLECT_PER_RUN, "true");
        collectPerRunBundle.putString(HeapDumpListener.ITERATION_ALL_ENABLE, String.valueOf(true));
        HeapDumpListener collector = new HeapDumpListener(collectPerRunBundle, mHelper);
        collector.setInstrumentation(mInstrumentation);
        collector.testRunStarted(RUN_DESCRIPTION);
        collector.testStarted(Description.createTestDescription("run$1", "test_two"));
        verify(mHelper, times(1)).startCollecting("heapdump_1");
        verify(mHelper, times(0)).startCollecting("run_test_two_1");
    }

    /** Test to verify per test run heap dump collection flag overrides heapdump collection on
     * specific iteration id's.*/
    @Test
    public void testHeapCollectionForTestRunOverridesPerTestIterations() throws Exception {
        Bundle collectPerRunBundle = new Bundle();
        collectPerRunBundle.putString(HeapDumpListener.COLLECT_PER_RUN, "true");
        collectPerRunBundle.putString(HeapDumpListener.ENABLE_ITERATION_IDS, "1");
        HeapDumpListener collector = new HeapDumpListener(collectPerRunBundle, mHelper);
        collector.setInstrumentation(mInstrumentation);
        collector.testRunStarted(RUN_DESCRIPTION);
        collector.testStarted(Description.createTestDescription("run$1", "test_two"));
        verify(mHelper, times(1)).startCollecting("heapdump_1");
        verify(mHelper, times(0)).startCollecting("run_test_two_1");
    }
}
