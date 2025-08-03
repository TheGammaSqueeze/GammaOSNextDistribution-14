/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.app.Instrumentation;
import android.device.collectors.annotations.MetricOption;
import android.device.collectors.annotations.OptionClass;
import android.device.collectors.util.SendToInstrumentation;
import android.os.Bundle;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Android Unit Tests for {@link BaseMetricListener}.
 */
@RunWith(AndroidJUnit4.class)
public class BaseMetricListenerInstrumentedTest {

    private static final String RUN_START_KEY = "run_start_key";
    private static final String RUN_END_KEY = "run_end_key";
    private static final String RUN_START_VALUE = "run_start_value";
    private static final String RUN_END_VALUE = "run_end_value";
    private static final String TEST_START_KEY = "test_start_key";
    private static final String TEST_END_KEY = "test_end_key";
    private static final String TEST_START_VALUE = "test_start_value";
    private static final String TEST_END_VALUE = "test_end_value";
    private static final String SKIP_METRICS_OPTION = "skip-metrics";
    private BaseMetricListener mListener;
    private Instrumentation mMockInstrumentation;

    @Before
    public void setUp() {
        mMockInstrumentation = Mockito.mock(Instrumentation.class);
        mListener = createWithArgs(null);
        mListener.setInstrumentation(mMockInstrumentation);
    }

    private BaseMetricListener createWithArgs(Bundle args) {
        if (args == null) {
            args = new Bundle();
        }
        return new BaseMetricListener(args) {
            private boolean mSuppressMetrics = false;

            @Override
            public void onTestStart(DataRecord testData, Description description) {
                // In this test check that a new DataRecord is passed to testStart each time.
                assertFalse(testData.hasMetrics());
                if (!mSuppressMetrics) {
                    testData.addStringMetric(
                            TEST_START_KEY, TEST_START_VALUE + description.getMethodName());
                }
            }

            @Override
            public void onTestEnd(DataRecord testData, Description description) {
                if (!mSuppressMetrics) {
                    testData.addStringMetric(
                            TEST_END_KEY, TEST_END_VALUE + description.getMethodName());
                }
            }

            @Override
            public void onTestRunStart(DataRecord runData, Description description) {
                assertFalse(runData.hasMetrics());
                if (!mSuppressMetrics) {
                    runData.addStringMetric(RUN_START_KEY, RUN_START_VALUE);
                }
            }

            @Override
            public void onTestRunEnd(DataRecord runData, Result result) {
                if (!mSuppressMetrics) {
                    runData.addStringMetric(RUN_END_KEY, RUN_END_VALUE);
                }
            }

            @Override
            public void setupAdditionalArgs() {
                // We test this method by checking if metrics are suppressed when the skip metrics
                // option is set.
                mSuppressMetrics =
                        Boolean.parseBoolean(getArgsBundle().getString(SKIP_METRICS_OPTION));
            }
        };
    }

    /**
     * When metrics are logged during a test, expect them to be added to the bundle.
     */
    @MetricOption(group = "testGroup,testGroup1")
    @Test
    public void testReportMetrics() throws Exception {
        Description runDescription = Description.createSuiteDescription("run");
        mListener.testRunStarted(runDescription);
        Description testDescription = Description.createTestDescription("class", "method");
        mListener.testStarted(testDescription);
        mListener.testFinished(testDescription);
        mListener.testRunFinished(new Result());
        // AJUR runner is then gonna call instrumentationRunFinished
        Bundle resultBundle = new Bundle();
        mListener.instrumentationRunFinished(System.out, resultBundle, new Result());

        // Check that the in progress status contains the metrics.
        ArgumentCaptor<Bundle> capture = ArgumentCaptor.forClass(Bundle.class);
        Mockito.verify(mMockInstrumentation)
                .sendStatus(Mockito.eq(
                        SendToInstrumentation.INST_STATUS_IN_PROGRESS), capture.capture());
        List<Bundle> capturedBundle = capture.getAllValues();
        assertEquals(1, capturedBundle.size());
        Bundle check = capturedBundle.get(0);
        assertEquals(TEST_START_VALUE + "method", check.getString(TEST_START_KEY));
        assertEquals(TEST_END_VALUE + "method", check.getString(TEST_END_KEY));
        assertEquals(2, check.size());

        // Check that final bundle contains run results
        assertEquals(RUN_START_VALUE, resultBundle.getString(RUN_START_KEY));
        assertEquals(RUN_END_VALUE, resultBundle.getString(RUN_END_KEY));
        assertEquals(2, resultBundle.size());
    }

    /**
     * Test that only included group are running collection.
     */
    @MetricOption(group = "testGroup")
    @Test
    public void testReportMetrics_withIncludeFilters() throws Exception {
        Bundle args = new Bundle();
        args.putString(BaseMetricListener.INCLUDE_FILTER_GROUP_KEY, "group1,group2");
        mListener = createWithArgs(args);
        mListener.setInstrumentation(mMockInstrumentation);

        Description runDescription = Description.createSuiteDescription("run");
        mListener.testRunStarted(runDescription);
        Description testDescription = Description.createTestDescription("class", "method",
                new TestAnnotation("group1"));
        mListener.testStarted(testDescription);
        mListener.testFinished(testDescription);
        // A second test case that will not be included
        Description testDescription2 = Description.createTestDescription("class", "method2",
                new TestAnnotation("group3"));
        mListener.testStarted(testDescription2);
        mListener.testFinished(testDescription2);
        // A third test case that will be included
        Description testDescription3 = Description.createTestDescription("class", "method3",
                new TestAnnotation("group2"));
        mListener.testStarted(testDescription3);
        mListener.testFinished(testDescription3);

        // Check that the in progress status contains the metrics.
        ArgumentCaptor<Bundle> capture = ArgumentCaptor.forClass(Bundle.class);
        Mockito.verify(mMockInstrumentation, Mockito.times(2))
                .sendStatus(Mockito.eq(
                        SendToInstrumentation.INST_STATUS_IN_PROGRESS), capture.capture());
        // Check that only method2 did not generate any metrics since it was filtered.
        List<Bundle> capturedBundle = capture.getAllValues();
        assertEquals(2, capturedBundle.size());
        Bundle check = capturedBundle.get(0);
        assertEquals(TEST_START_VALUE + "method", check.getString(TEST_START_KEY));
        assertEquals(TEST_END_VALUE + "method", check.getString(TEST_END_KEY));
        assertEquals(2, check.size());
        // Got a call from the method3
        Bundle check2 = capturedBundle.get(1);
        assertEquals(TEST_START_VALUE + "method3", check2.getString(TEST_START_KEY));
        assertEquals(TEST_END_VALUE + "method3", check2.getString(TEST_END_KEY));
        assertEquals(2, check2.size());
    }

    /**
     * Test that only included group are running collection, even if some method have multiple
     * groups, if any of its group is included, the method is included.
     */
    @MetricOption(group = "testGroup")
    @Test
    public void testReportMetrics_withIncludeFilters_multiGroup() throws Exception {
        Bundle args = new Bundle();
        args.putString(BaseMetricListener.INCLUDE_FILTER_GROUP_KEY, "group4");
        mListener = createWithArgs(args);
        mListener.setInstrumentation(mMockInstrumentation);

        Description runDescription = Description.createSuiteDescription("run");
        mListener.testRunStarted(runDescription);
        Description testDescription = Description.createTestDescription("class", "method",
                new TestAnnotation("group1"));
        mListener.testStarted(testDescription);
        mListener.testFinished(testDescription);
        // A second test case that will not be included
        Description testDescription2 = Description.createTestDescription("class", "method2",
                new TestAnnotation("group3,group4"));
        mListener.testStarted(testDescription2);
        mListener.testFinished(testDescription2);
        // A third test case that will be included
        Description testDescription3 = Description.createTestDescription("class", "method3",
                new TestAnnotation("group2"));
        mListener.testStarted(testDescription3);
        mListener.testFinished(testDescription3);

        // Check that the in progress status contains the metrics.
        ArgumentCaptor<Bundle> capture = ArgumentCaptor.forClass(Bundle.class);
        Mockito.verify(mMockInstrumentation, Mockito.times(1))
                .sendStatus(Mockito.eq(
                        SendToInstrumentation.INST_STATUS_IN_PROGRESS), capture.capture());
        // Check that only method2 did generate metrics since it was included.
        List<Bundle> capturedBundle = capture.getAllValues();
        assertEquals(1, capturedBundle.size());
        Bundle check = capturedBundle.get(0);
        // Got call from method2
        assertEquals(TEST_START_VALUE + "method2", check.getString(TEST_START_KEY));
        assertEquals(TEST_END_VALUE + "method2", check.getString(TEST_END_KEY));
        assertEquals(2, check.size());
    }

    /**
     * Test that only not excluded group are running collection.
     */
    @MetricOption(group = "testGroup")
    @Test
    public void testReportMetrics_withExcludeFilters() throws Exception {
        Bundle args = new Bundle();
        args.putString(BaseMetricListener.EXCLUDE_FILTER_GROUP_KEY, "group1,group2");
        mListener = createWithArgs(args);
        mListener.setInstrumentation(mMockInstrumentation);

        Description runDescription = Description.createSuiteDescription("run");
        mListener.testRunStarted(runDescription);
        // A first test case that will not be included
        Description testDescription = Description.createTestDescription("class", "method",
                new TestAnnotation("group1"));
        mListener.testStarted(testDescription);
        mListener.testFinished(testDescription);
        // A second test case that will run
        Description testDescription2 = Description.createTestDescription("class", "method2",
                new TestAnnotation("group3"));
        mListener.testStarted(testDescription2);
        mListener.testFinished(testDescription2);
        // A third test case that will not be included
        Description testDescription3 = Description.createTestDescription("class", "method3",
                new TestAnnotation("group2"));
        mListener.testStarted(testDescription3);
        mListener.testFinished(testDescription3);

        // Check that the in progress status contains the metrics.
        ArgumentCaptor<Bundle> capture = ArgumentCaptor.forClass(Bundle.class);
        Mockito.verify(mMockInstrumentation, Mockito.times(1))
                .sendStatus(Mockito.eq(
                        SendToInstrumentation.INST_STATUS_IN_PROGRESS), capture.capture());
        // Check that only method2 generates some metrics
        List<Bundle> capturedBundle = capture.getAllValues();
        assertEquals(1, capturedBundle.size());
        Bundle check = capturedBundle.get(0);
        assertEquals(TEST_START_VALUE + "method2", check.getString(TEST_START_KEY));
        assertEquals(TEST_END_VALUE + "method2", check.getString(TEST_END_KEY));
        assertEquals(2, check.size());
    }

    /**
     * Test that when both filters are present, exclude filters have priority.
     */
    @MetricOption(group = "testGroup")
    @Test
    public void testReportMetrics_withBothFilters() throws Exception {
        Bundle args = new Bundle();
        args.putString(BaseMetricListener.EXCLUDE_FILTER_GROUP_KEY, "group1,group2");
        args.putString(BaseMetricListener.INCLUDE_FILTER_GROUP_KEY, "group2,group3");
        mListener = createWithArgs(args);
        mListener.setInstrumentation(mMockInstrumentation);

        Description runDescription = Description.createSuiteDescription("run");
        mListener.testRunStarted(runDescription);
        // A first test case that will not be included
        Description testDescription = Description.createTestDescription("class", "method",
                new TestAnnotation("group1"));
        mListener.testStarted(testDescription);
        mListener.testFinished(testDescription);
        // A second test case that will run
        Description testDescription2 = Description.createTestDescription("class", "method2",
                new TestAnnotation("group3"));
        mListener.testStarted(testDescription2);
        mListener.testFinished(testDescription2);
        // A third test case that will not be included
        Description testDescription3 = Description.createTestDescription("class", "method3",
                new TestAnnotation("group2"));
        mListener.testStarted(testDescription3);
        mListener.testFinished(testDescription3);

        // Check that the in progress status contains the metrics.
        ArgumentCaptor<Bundle> capture = ArgumentCaptor.forClass(Bundle.class);
        Mockito.verify(mMockInstrumentation, Mockito.times(1))
                .sendStatus(Mockito.eq(
                        SendToInstrumentation.INST_STATUS_IN_PROGRESS), capture.capture());
        // Check that only method2 generates some metrics
        List<Bundle> capturedBundle = capture.getAllValues();
        assertEquals(1, capturedBundle.size());
        Bundle check = capturedBundle.get(0);
        assertEquals(TEST_START_VALUE + "method2", check.getString(TEST_START_KEY));
        assertEquals(TEST_END_VALUE + "method2", check.getString(TEST_END_KEY));
        assertEquals(2, check.size());
    }

    /** Test that the parsing of the skip metrics option in setUpAdditionalArgs() is executed. */
    @MetricOption(group = "testGroup")
    @Test
    public void testAdditionalArgs() throws Exception {
        Bundle args = new Bundle();
        args.putString(SKIP_METRICS_OPTION, String.valueOf(true));
        mListener = createWithArgs(args);
        mListener.setInstrumentation(mMockInstrumentation);

        Description runDescription = Description.createSuiteDescription("run");
        mListener.testRunStarted(runDescription);
        Description testDescription = Description.createTestDescription("class", "method");
        mListener.testStarted(testDescription);
        mListener.testFinished(testDescription);
        mListener.testRunFinished(new Result());
        // AJUR runner is then gonna call instrumentationRunFinished
        Bundle resultBundle = new Bundle();
        mListener.instrumentationRunFinished(System.out, resultBundle, new Result());

        Mockito.verify(mMockInstrumentation, Mockito.never())
                .sendStatus(Mockito.anyInt(), Mockito.any());

        // We have skipped reporting metrics, so no run metrics should be in the bundle either.
        assertEquals(0, resultBundle.size());
    }

    @MetricOption(group = "testGroup")
    @Test
    public void testSetUpAndCleanUpWithTestCycle() throws Exception {
        // We use this bundle to mimic device state. We modify it in setUp() and
        // check that this is maintained throughout the test. We remove the
        // modification during cleanUp() and check if the bundle has indeed
        // become empty again.
        Bundle data = new Bundle();
        final String arg = "arg";
        final String value = "value";

        BaseMetricListener listener =
                new BaseMetricListener() {
                    public static final String SETUP_ARG = "arg";
                    private static final String SETUP_VALUE = "value";

                    @Override
                    protected void onSetUp() {
                        data.putString(arg, value);
                    }

                    @Override
                    protected void onCleanUp() {
                        data.remove(arg);
                    }

                    @Override
                    public void onTestRunStart(DataRecord runData, Description description) {
                        assertEquals(value, data.getString(arg));
                    }

                    @Override
                    public void onTestRunEnd(DataRecord runData, Result result) {
                        assertEquals(value, data.getString(arg));
                    }

                    @Override
                    public void onTestStart(DataRecord testData, Description description) {
                        assertEquals(value, data.getString(arg));
                    }

                    @Override
                    public void onTestEnd(DataRecord testData, Description description) {
                        assertEquals(value, data.getString(arg));
                    }
                };

        Description runDescription = Description.createSuiteDescription("run");
        Description test1Description = Description.createTestDescription("class", "method1");
        Description test2Description = Description.createTestDescription("class", "method2");
        // Simulate a test cycle.
        listener.testRunStarted(runDescription);
        listener.testStarted(test1Description);
        listener.testFinished(test1Description);
        listener.testStarted(test2Description);
        listener.testFinished(test2Description);
        listener.testRunFinished(new Result());
        listener.instrumentationRunFinished(System.out, new Bundle(), new Result());

        assertFalse(data.containsKey(arg));
    }

    @MetricOption(group = "testGroup")
    @Test
    public void testSetUpAndCleanUpWithCallbacks() throws Exception {
        // We use this bundle to mimic device state. We modify it in setUp() and
        // check that this is maintained throughout the test. We remove the
        // modification during cleanUp() and check if the bundle has indeed
        // become empty again.
        Bundle data = new Bundle();
        final String arg = "arg";
        final String value = "value";

        BaseMetricListener listener =
                new BaseMetricListener() {
                    public static final String SETUP_ARG = "arg";
                    private static final String SETUP_VALUE = "value";

                    @Override
                    protected void onSetUp() {
                        data.putString(arg, value);
                    }

                    @Override
                    protected void onCleanUp() {
                        data.remove(arg);
                    }
                };

        listener.setUp();
        assertEquals(value, data.getString(arg));
        listener.cleanUp();
        assertFalse(data.containsKey(arg));
    }

    /**
     * Test annotation that allows to instantiate {@link MetricOption} for testing purpose.
     */
    public static class TestAnnotation implements MetricOption {
        private String mGroup;

        public TestAnnotation(String group) {
            mGroup = group;
        }

        @Override
        public String group() {
            return mGroup;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return MetricOption.class;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TestAnnotation)) {
                return false;
            }
            TestAnnotation o = (TestAnnotation) other;
            return Objects.equals(mGroup, o.mGroup);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mGroup);
        }
    }

    /**
     * Test listener with an {@link OptionClass} specified to be used for arguments testing.
     */
    @OptionClass(alias = "test-alias-class")
    public static class TestListener extends BaseMetricListener {

        public TestListener(Bundle b) {
            super(b);
        }
    }

    /**
     * Test when the listener does not have an {@link OptionClass} specified, option with alias
     * are filtered.
     */
    @MetricOption(group = "testGroup")
    @Test
    public void testArgsAlias_noOptionClass() throws Exception {
        Bundle args = new Bundle();
        args.putString("optionalias:optionname", "optionvalue");
        args.putString("noalias", "noaliasvalue");
        mListener = createWithArgs(args);
        mListener.setInstrumentation(mMockInstrumentation);
        Description runDescription = Description.createSuiteDescription("run");
        mListener.testRunStarted(runDescription);
        Bundle filteredArgs = mListener.getArgsBundle();
        assertFalse(filteredArgs.containsKey("optionalias:optionname"));
        assertFalse(filteredArgs.containsKey("optionname"));
        assertTrue(filteredArgs.containsKey("noalias"));
    }

    /**
     * Test when a listener does have an {@link OptionClass} specified, in that case only options
     * matching the alias or with no alias are preserved.
     */
    @MetricOption(group = "testGroup")
    @Test
    public void testArgsAlias_optionClass() throws Exception {
        Bundle args = new Bundle();
        args.putString("test-alias-class:optionname", "optionvalue");
        args.putString("noalias", "noaliasvalue");
        args.putString("anotheralias:optionname2", "value");
        TestListener listener = new TestListener(args);
        listener.setInstrumentation(mMockInstrumentation);
        Description runDescription = Description.createSuiteDescription("run");
        listener.testRunStarted(runDescription);
        Bundle filteredArgs = listener.getArgsBundle();
        assertTrue(filteredArgs.containsKey("noalias"));
        assertTrue(filteredArgs.containsKey("optionname"));

        assertFalse(filteredArgs.containsKey("test-alias-class:optionname"));
        assertFalse(filteredArgs.containsKey("anotheralias:optionname2"));
        assertFalse(filteredArgs.containsKey("optionname2"));
    }

    /**
     * Report the metrics for the same method name called multiple times.
     */
    @MetricOption(group = "testGroup")
    @Test
    public void testReportMetricsSameMethodName() throws Exception {
        Description runDescription = Description.createSuiteDescription("run");
        mListener.testRunStarted(runDescription);
        Description testDescription = Description.createTestDescription("class", "method");
        mListener.testStarted(testDescription);
        mListener.testFinished(testDescription);
        mListener.testStarted(testDescription);
        mListener.testFinished(testDescription);
        mListener.testRunFinished(new Result());
        // AJUR runner is then gonna call instrumentationRunFinished
        Bundle resultBundle = new Bundle();
        mListener.instrumentationRunFinished(System.out, resultBundle, new Result());

        // Check that the in progress status contains the metrics.
        ArgumentCaptor<Bundle> capture = ArgumentCaptor.forClass(Bundle.class);
        Mockito.verify(mMockInstrumentation, Mockito.times(2))
                .sendStatus(Mockito.eq(
                        SendToInstrumentation.INST_STATUS_IN_PROGRESS), capture.capture());

        List<Bundle> capturedBundle = capture.getAllValues();
        assertEquals(2, capturedBundle.size());
        Bundle check = capturedBundle.get(0);
        assertEquals(TEST_START_VALUE + "method", check.getString(TEST_START_KEY));
        assertEquals(TEST_END_VALUE + "method", check.getString(TEST_END_KEY));
        assertEquals(2, check.size());

        Bundle check2 = capturedBundle.get(1);
        assertEquals(TEST_START_VALUE + "method", check2.getString(TEST_START_KEY));
        assertEquals(TEST_END_VALUE + "method", check2.getString(TEST_END_KEY));
        assertEquals(2, check2.size());

        // Check that final bundle contains run results
        assertEquals(RUN_START_VALUE, resultBundle.getString(RUN_START_KEY));
        assertEquals(RUN_END_VALUE, resultBundle.getString(RUN_END_KEY));
        assertEquals(2, resultBundle.size());
    }


    /**
     * Metric collection happens only during given intervals.
     */
    @MetricOption(group = "testGroup")
    @Test
    public void testSameMethodNameWithValidIntervalOption() throws Exception {
        Bundle args = new Bundle();
        args.putString(BaseMetricListener.COLLECT_ITERATION_INTERVAL, "2");
        mListener = createWithArgs(args);
        mListener.setInstrumentation(mMockInstrumentation);

        Description runDescription = Description.createSuiteDescription("run");
        mListener.testRunStarted(runDescription);
        Description testDescription = Description.createTestDescription("class", "method");
        mListener.testStarted(testDescription);
        mListener.testFinished(testDescription);
        mListener.testStarted(testDescription);
        mListener.testFinished(testDescription);
        mListener.testStarted(testDescription);
        mListener.testFinished(testDescription);
        mListener.testStarted(testDescription);
        mListener.testFinished(testDescription);
        mListener.testRunFinished(new Result());
        // AJUR runner is then gonna call instrumentationRunFinished
        Bundle resultBundle = new Bundle();
        mListener.instrumentationRunFinished(System.out, resultBundle, new Result());

        // Check that the in progress status contains the metrics only for the 2nd and
        // 4th iteration with the same test name.
        ArgumentCaptor<Bundle> capture = ArgumentCaptor.forClass(Bundle.class);
        Mockito.verify(mMockInstrumentation, Mockito.times(2))
                .sendStatus(Mockito.eq(
                        SendToInstrumentation.INST_STATUS_IN_PROGRESS), capture.capture());

        List<Bundle> capturedBundle = capture.getAllValues();
        assertEquals(2, capturedBundle.size());
        Bundle check = capturedBundle.get(0);
        assertEquals(TEST_START_VALUE + "method", check.getString(TEST_START_KEY));
        assertEquals(TEST_END_VALUE + "method", check.getString(TEST_END_KEY));
        assertEquals(2, check.size());

        Bundle check2 = capturedBundle.get(1);
        assertEquals(TEST_START_VALUE + "method", check2.getString(TEST_START_KEY));
        assertEquals(TEST_END_VALUE + "method", check2.getString(TEST_END_KEY));
        assertEquals(2, check2.size());

        // Check that final bundle contains run results
        assertEquals(RUN_START_VALUE, resultBundle.getString(RUN_START_KEY));
        assertEquals(RUN_END_VALUE, resultBundle.getString(RUN_END_KEY));
        assertEquals(2, resultBundle.size());
    }

    /**
     * Metric collection does not happen on the skipped iterations.
     */
    @MetricOption(group = "testGroup")
    @Test
    public void testSameMethodNameWithSkipIterationOption() throws Exception {
        Bundle args = new Bundle();
        args.putString(BaseMetricListener.SKIP_METRIC_UNTIL_ITERATION, "2");
        mListener = createWithArgs(args);
        mListener.setInstrumentation(mMockInstrumentation);

        // Skip until iteration is set to 2.
        // Metric will not be collected for 1st and 2nd iterations.
        Description runDescription = Description.createSuiteDescription("run");
        mListener.testRunStarted(runDescription);
        Description testDescription = Description.createTestDescription("class", "method");
        mListener.testStarted(testDescription);
        mListener.testFinished(testDescription);
        mListener.testStarted(testDescription);
        mListener.testFinished(testDescription);
        mListener.testStarted(testDescription);
        mListener.testFinished(testDescription);
        mListener.testStarted(testDescription);
        mListener.testFinished(testDescription);
        mListener.testStarted(testDescription);
        mListener.testFinished(testDescription);
        mListener.testRunFinished(new Result());
        // AJUR runner is then gonna call instrumentationRunFinished
        Bundle resultBundle = new Bundle();
        mListener.instrumentationRunFinished(System.out, resultBundle, new Result());

        // Check instrumentation status inprogress called only 3 times.
        ArgumentCaptor<Bundle> capture = ArgumentCaptor.forClass(Bundle.class);
        Mockito.verify(mMockInstrumentation, Mockito.times(3))
                .sendStatus(Mockito.eq(
                        SendToInstrumentation.INST_STATUS_IN_PROGRESS), capture.capture());

        List<Bundle> capturedBundle = capture.getAllValues();
        assertEquals(3, capturedBundle.size());
        Bundle check = capturedBundle.get(0);
        assertEquals(TEST_START_VALUE + "method", check.getString(TEST_START_KEY));
        assertEquals(TEST_END_VALUE + "method", check.getString(TEST_END_KEY));
        assertEquals(2, check.size());

        Bundle check2 = capturedBundle.get(1);
        assertEquals(TEST_START_VALUE + "method", check2.getString(TEST_START_KEY));
        assertEquals(TEST_END_VALUE + "method", check2.getString(TEST_END_KEY));
        assertEquals(2, check2.size());

        Bundle check3 = capturedBundle.get(2);
        assertEquals(TEST_START_VALUE + "method", check3.getString(TEST_START_KEY));
        assertEquals(TEST_END_VALUE + "method", check3.getString(TEST_END_KEY));
        assertEquals(2, check2.size());

        // Check that final bundle contains run results
        assertEquals(RUN_START_VALUE, resultBundle.getString(RUN_START_KEY));
        assertEquals(RUN_END_VALUE, resultBundle.getString(RUN_END_KEY));
        assertEquals(2, resultBundle.size());
    }

    /**
     * Metric collection happens on all the iteration if the interval is
     * invalid (i.e less than 1).
     */
    @MetricOption(group = "testGroup")
    @Test
    public void testSameMethodNameWithInvalidIntervalNumber() throws Exception {
        Bundle args = new Bundle();
        args.putString(BaseMetricListener.COLLECT_ITERATION_INTERVAL, "0");
        mListener = createWithArgs(args);
        mListener.setInstrumentation(mMockInstrumentation);

        Description runDescription = Description.createSuiteDescription("run");
        mListener.testRunStarted(runDescription);
        Description testDescription = Description.createTestDescription("class", "method");
        mListener.testStarted(testDescription);
        mListener.testFinished(testDescription);
        mListener.testStarted(testDescription);
        mListener.testFinished(testDescription);
        mListener.testRunFinished(new Result());
        // AJUR runner is then gonna call instrumentationRunFinished
        Bundle resultBundle = new Bundle();
        mListener.instrumentationRunFinished(System.out, resultBundle, new Result());

        // Check that the in progress status contains the metrics only for the 2nd and
        // 4th iteration with the same test name.
        ArgumentCaptor<Bundle> capture = ArgumentCaptor.forClass(Bundle.class);
        Mockito.verify(mMockInstrumentation, Mockito.times(2))
                .sendStatus(Mockito.eq(
                        SendToInstrumentation.INST_STATUS_IN_PROGRESS), capture.capture());

        List<Bundle> capturedBundle = capture.getAllValues();
        assertEquals(2, capturedBundle.size());
        Bundle check = capturedBundle.get(0);
        assertEquals(TEST_START_VALUE + "method", check.getString(TEST_START_KEY));
        assertEquals(TEST_END_VALUE + "method", check.getString(TEST_END_KEY));
        assertEquals(2, check.size());

        Bundle check2 = capturedBundle.get(1);
        assertEquals(TEST_START_VALUE + "method", check2.getString(TEST_START_KEY));
        assertEquals(TEST_END_VALUE + "method", check2.getString(TEST_END_KEY));
        assertEquals(2, check2.size());

        // Check that final bundle contains run results
        assertEquals(RUN_START_VALUE, resultBundle.getString(RUN_START_KEY));
        assertEquals(RUN_END_VALUE, resultBundle.getString(RUN_END_KEY));
        assertEquals(2, resultBundle.size());
    }

    /** Test that the report as instrumentation result option works. */
    @MetricOption(group = "testGroup")
    @Test
    public void testReportAsInstrumentationResultsIfEnabled() throws Exception {
        mListener.setReportAsInstrumentationResults(true);

        Description runDescription = Description.createSuiteDescription("run");
        mListener.testRunStarted(runDescription);
        Description testDescription = Description.createTestDescription("class", "method");
        mListener.testStarted(testDescription);
        mListener.testFinished(testDescription);
        mListener.testRunFinished(new Result());
        // AJUR runner is then gonna call instrumentationRunFinished
        Bundle resultBundle = new Bundle();
        mListener.instrumentationRunFinished(System.out, resultBundle, new Result());

        // Check that results are reported via Instrumentation.addResults().
        ArgumentCaptor<Bundle> capture = ArgumentCaptor.forClass(Bundle.class);
        Mockito.verify(mMockInstrumentation, Mockito.times(1)).addResults(capture.capture());
        Bundle addedResult = capture.getValue();
        assertTrue(addedResult.containsKey(TEST_END_KEY));
        assertEquals(TEST_END_VALUE + "method", addedResult.getString(TEST_END_KEY));

        // Rather than Instrumentation.sendStatus().
        Mockito.verify(mMockInstrumentation, Mockito.never())
                .sendStatus(
                        Mockito.eq(SendToInstrumentation.INST_STATUS_IN_PROGRESS),
                        Mockito.any(Bundle.class));
    }
}
