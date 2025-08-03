/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.tradefed.result;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.BugreportCollector.Filter;
import com.android.tradefed.result.BugreportCollector.Freq;
import com.android.tradefed.result.BugreportCollector.Noun;
import com.android.tradefed.result.BugreportCollector.Predicate;
import com.android.tradefed.result.BugreportCollector.Relation;
import com.android.tradefed.result.BugreportCollector.SubPredicate;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Unit tests for {@link BugreportCollector} */
@RunWith(JUnit4.class)
public class BugreportCollectorTest {
    private BugreportCollector mCollector = null;
    @Mock ITestDevice mMockDevice;
    @Mock ITestInvocationListener mMockListener;
    private InputStreamSource mBugreportISS = null;
    @Captor ArgumentCaptor<HashMap<String, Metric>> mTestCapture;
    @Captor ArgumentCaptor<HashMap<String, Metric>> mRunCapture;

    private static final String TEST_KEY = "key";
    private static final String RUN_KEY = "key2";
    private static final String BUGREPORT_STRING = "This is a bugreport\nYeah!\n";
    private static final String STACK_TRACE = "boo-hoo";

    private static class BugreportISS implements InputStreamSource {
        @Override
        public InputStream createInputStream() {
            return new ByteArrayInputStream(BUGREPORT_STRING.getBytes());
        }

        @Override
        public void close() {
            // ignore
        }

        @Override
        public long size() {
            return BUGREPORT_STRING.getBytes().length;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mBugreportISS = new BugreportISS();

        when(mMockDevice.getBugreport()).thenReturn(mBugreportISS);
        mCollector = new BugreportCollector(mMockListener, mMockDevice);
    }

    @Test
    public void testCreatePredicate() throws Exception {
        Predicate foo = new Predicate(Relation.AFTER, Freq.EACH, Noun.TESTCASE);
        assertEquals("AFTER_EACH_TESTCASE", foo.toString());
    }

    @Test
    public void testPredicateEquals() throws Exception {
        Predicate foo = new Predicate(Relation.AFTER, Freq.EACH, Noun.TESTCASE);
        Predicate bar = new Predicate(Relation.AFTER, Freq.EACH, Noun.TESTCASE);
        Predicate baz = new Predicate(Relation.AFTER, Freq.EACH, Noun.INVOCATION);

        assertTrue(foo.equals(bar));
        assertTrue(bar.equals(foo));
        assertFalse(foo.equals(baz));
        assertFalse(baz.equals(bar));
    }

    @Test
    public void testPredicatePartialMatch() throws Exception {
        Predicate shortP = new Predicate(Relation.AFTER, Freq.EACH, Noun.INVOCATION);
        Predicate longP =
                new Predicate(
                        Relation.AFTER, Freq.EACH, Noun.INVOCATION, Filter.WITH_ANY, Noun.TESTCASE);
        assertTrue(longP.partialMatch(shortP));
        assertTrue(shortP.partialMatch(longP));
    }

    @Test
    public void testPredicateFullMatch() throws Exception {
        Predicate shortP = new Predicate(Relation.AFTER, Freq.EACH, Noun.INVOCATION);
        Predicate longP =
                new Predicate(
                        Relation.AFTER, Freq.EACH, Noun.INVOCATION, Filter.WITH_ANY, Noun.TESTCASE);
        Predicate longP2 =
                new Predicate(
                        Relation.AFTER, Freq.EACH, Noun.INVOCATION, Filter.WITH_ANY, Noun.TESTCASE);
        assertFalse(longP.fullMatch(shortP));
        assertFalse(shortP.fullMatch(longP));

        assertTrue(longP.fullMatch(longP2));
        assertTrue(longP2.fullMatch(longP));
    }

    /** A test to verify that invalid predicates are rejected */
    @Test
    public void testInvalidPredicate() throws Exception {
        SubPredicate[][] predicates =
                new SubPredicate[][] {
                    // AT_START_OF (Freq) FAILED_(Noun)
                    {Relation.AT_START_OF, Freq.EACH, Noun.FAILED_TESTCASE},
                    {Relation.AT_START_OF, Freq.EACH, Noun.FAILED_TESTRUN},
                    {Relation.AT_START_OF, Freq.EACH, Noun.FAILED_INVOCATION},
                    {Relation.AT_START_OF, Freq.FIRST, Noun.FAILED_TESTCASE},
                    {Relation.AT_START_OF, Freq.FIRST, Noun.FAILED_TESTRUN},
                    {Relation.AT_START_OF, Freq.FIRST, Noun.FAILED_INVOCATION},
                    // (Relation) FIRST [FAILED_]INVOCATION
                    {Relation.AT_START_OF, Freq.FIRST, Noun.INVOCATION},
                    {Relation.AT_START_OF, Freq.FIRST, Noun.FAILED_INVOCATION},
                    {Relation.AFTER, Freq.FIRST, Noun.INVOCATION},
                    {Relation.AFTER, Freq.FIRST, Noun.FAILED_INVOCATION},
                };

        for (SubPredicate[] pred : predicates) {
            try {
                assertEquals(3, pred.length);
                new Predicate((Relation) pred[0], (Freq) pred[1], (Noun) pred[2]);
                fail(
                        String.format(
                                "Expected IllegalArgumentException for invalid predicate [%s %s"
                                        + " %s]",
                                pred[0], pred[1], pred[2]));
            } catch (IllegalArgumentException e) {
                // expected
                // FIXME: validate message
            }
        }
    }

    /** Make sure that BugreportCollector passes events through to its child listener */
    @Test
    public void testPassThrough() throws Exception {
        injectTestRun("runName", "testName", "value");

        InOrder testOrder = inOrder(mMockListener);
        verifyListenerTestRunExpectations(mMockListener, "runName", "testName", testOrder);

        assertEquals(
                "value", mTestCapture.getValue().get("key").getMeasurements().getSingleString());
        assertEquals(
                "value", mRunCapture.getValue().get("key2").getMeasurements().getSingleString());
    }

    @Test
    public void testTestFailed() throws Exception {
        Predicate pred = new Predicate(Relation.AFTER, Freq.EACH, Noun.FAILED_TESTCASE);
        mCollector.addPredicate(pred);

        injectTestRun("runName1", "testName1", "value", true /*failed*/);
        injectTestRun("runName2", "testName2", "value", true /*failed*/);

        InOrder testOrder = inOrder(mMockListener);

        verifyListenerTestRunExpectations(
                mMockListener, "runName1", "testName1", true /*failed*/, testOrder);
        verify(mMockListener)
                .testLog(
                        Mockito.contains("bug-FAILED-FooTest__testName1."),
                        Mockito.eq(LogDataType.BUGREPORT),
                        Mockito.eq(mBugreportISS));
        verifyListenerTestRunExpectations(
                mMockListener, "runName2", "testName2", true /*failed*/, testOrder);
        verify(mMockListener)
                .testLog(
                        Mockito.contains("bug-FAILED-FooTest__testName2."),
                        Mockito.eq(LogDataType.BUGREPORT),
                        Mockito.eq(mBugreportISS));

        assertEquals(
                "value", mTestCapture.getValue().get("key").getMeasurements().getSingleString());
        assertEquals(
                "value", mRunCapture.getValue().get("key2").getMeasurements().getSingleString());
        verify(mMockDevice, times(2)).waitForDeviceOnline(Mockito.anyLong());
    }

    @Test
    public void testTestEnded() throws Exception {
        Predicate pred = new Predicate(Relation.AFTER, Freq.EACH, Noun.TESTCASE);
        mCollector.addPredicate(pred);

        injectTestRun("runName1", "testName1", "value");
        injectTestRun("runName2", "testName2", "value");

        InOrder testOrder = inOrder(mMockListener);

        verifyListenerTestRunExpectations(mMockListener, "runName1", "testName1", testOrder);
        verify(mMockListener)
                .testLog(
                        Mockito.contains("bug-FooTest__testName1."),
                        Mockito.eq(LogDataType.BUGREPORT),
                        Mockito.eq(mBugreportISS));
        verifyListenerTestRunExpectations(mMockListener, "runName2", "testName2", testOrder);
        verify(mMockListener)
                .testLog(
                        Mockito.contains("bug-FooTest__testName2."),
                        Mockito.eq(LogDataType.BUGREPORT),
                        Mockito.eq(mBugreportISS));

        assertEquals(
                "value", mTestCapture.getValue().get("key").getMeasurements().getSingleString());
        assertEquals(
                "value", mRunCapture.getValue().get("key2").getMeasurements().getSingleString());
        verify(mMockDevice, times(2)).waitForDeviceOnline(Mockito.anyLong());
    }

    @Test
    public void testWaitForDevice() throws Exception {
        Predicate pred = new Predicate(Relation.AFTER, Freq.EACH, Noun.TESTCASE);
        mCollector.addPredicate(pred);
        mCollector.setDeviceWaitTime(1);

        injectTestRun("runName1", "testName1", "value");
        injectTestRun("runName2", "testName2", "value");

        InOrder testOrder = inOrder(mMockListener);

        verify(mMockDevice, times(2)).waitForDeviceOnline(1000); // Once per ending test method
        verifyListenerTestRunExpectations(mMockListener, "runName1", "testName1", testOrder);
        verify(mMockListener)
                .testLog(
                        Mockito.contains("bug-FooTest__testName1."),
                        Mockito.eq(LogDataType.BUGREPORT),
                        Mockito.eq(mBugreportISS));
        verifyListenerTestRunExpectations(mMockListener, "runName2", "testName2", testOrder);
        verify(mMockListener)
                .testLog(
                        Mockito.contains("bug-FooTest__testName2."),
                        Mockito.eq(LogDataType.BUGREPORT),
                        Mockito.eq(mBugreportISS));

        assertEquals(
                "value", mTestCapture.getValue().get("key").getMeasurements().getSingleString());
        assertEquals(
                "value", mRunCapture.getValue().get("key2").getMeasurements().getSingleString());
    }

    @Test
    public void testTestEnded_firstCase() throws Exception {
        Predicate pred = new Predicate(Relation.AFTER, Freq.FIRST, Noun.TESTCASE);
        mCollector.addPredicate(pred);

        injectTestRun("runName1", "testName1", "value");
        injectTestRun("runName2", "testName2", "value");

        InOrder testOrder = inOrder(mMockListener);

        verifyListenerTestRunExpectations(mMockListener, "runName1", "testName1", testOrder);
        verify(mMockListener)
                .testLog(
                        Mockito.contains("bug-FooTest__testName1."),
                        Mockito.eq(LogDataType.BUGREPORT),
                        Mockito.eq(mBugreportISS));
        verifyListenerTestRunExpectations(mMockListener, "runName2", "testName2", testOrder);
        verify(mMockListener)
                .testLog(
                        Mockito.contains("bug-FooTest__testName2."),
                        Mockito.eq(LogDataType.BUGREPORT),
                        Mockito.eq(mBugreportISS));

        assertEquals(
                "value", mTestCapture.getValue().get("key").getMeasurements().getSingleString());
        assertEquals(
                "value", mRunCapture.getValue().get("key2").getMeasurements().getSingleString());
        verify(mMockDevice, times(2)).waitForDeviceOnline(Mockito.anyLong());
    }

    @Test
    public void testTestEnded_firstRun() throws Exception {
        Predicate pred = new Predicate(Relation.AFTER, Freq.FIRST, Noun.TESTRUN);
        mCollector.addPredicate(pred);

        injectTestRun("runName", "testName", "value");
        injectTestRun("runName2", "testName2", "value");

        InOrder testOrder = inOrder(mMockListener);

        verify(mMockDevice).waitForDeviceOnline(Mockito.anyLong());
        // Note: only one testLog
        verifyListenerTestRunExpectations(mMockListener, "runName", "testName", testOrder);
        verify(mMockListener)
                .testLog(
                        Mockito.contains(pred.toString()),
                        Mockito.eq(LogDataType.BUGREPORT),
                        Mockito.eq(mBugreportISS));
        verifyListenerTestRunExpectations(mMockListener, "runName2", "testName2", testOrder);

        assertEquals(
                "value", mTestCapture.getValue().get("key").getMeasurements().getSingleString());
        assertEquals(
                "value", mRunCapture.getValue().get("key2").getMeasurements().getSingleString());
    }

    @Test
    public void testTestRunEnded() throws Exception {
        Predicate pred = new Predicate(Relation.AFTER, Freq.EACH, Noun.TESTRUN);
        mCollector.addPredicate(pred);

        injectTestRun("runName", "testName", "value");

        InOrder testOrder = inOrder(mMockListener);

        verify(mMockDevice).waitForDeviceOnline(Mockito.anyLong());
        verifyListenerTestRunExpectations(mMockListener, "runName", "testName", testOrder);
        verify(mMockListener)
                .testLog(
                        Mockito.contains(pred.toString()),
                        Mockito.eq(LogDataType.BUGREPORT),
                        Mockito.eq(mBugreportISS));

        assertEquals(
                "value", mTestCapture.getValue().get("key").getMeasurements().getSingleString());
        assertEquals(
                "value", mRunCapture.getValue().get("key2").getMeasurements().getSingleString());
    }

    @Test
    public void testDescriptiveName() throws Exception {
        final String normalName = "AT_START_OF_FIRST_TESTCASE";
        final String descName = "custom_descriptive_name";

        mCollector.grabBugreport(normalName);
        mCollector.setDescriptiveName(descName);
        mCollector.grabBugreport(normalName);

        verify(mMockListener)
                .testLog(
                        Mockito.contains(normalName),
                        Mockito.eq(LogDataType.BUGREPORT),
                        Mockito.eq(mBugreportISS));
        verify(mMockListener)
                .testLog(
                        Mockito.contains(descName),
                        Mockito.eq(LogDataType.BUGREPORT),
                        Mockito.eq(mBugreportISS));

        verify(mMockDevice, times(2)).waitForDeviceOnline(Mockito.anyLong());
    }

    /**
     * Injects a single test run with 1 passed test into the {@link CollectingTestListener} under
     * test
     *
     * @return the {@link TestDescription} of added test
     */
    private TestDescription injectTestRun(String runName, String testName, String metricValue) {
        return injectTestRun(runName, testName, metricValue, false);
    }

    /**
     * Injects a single test run with 1 passed test into the {@link CollectingTestListener} under
     * test
     *
     * @return the {@link TestDescription} of added test
     */
    private TestDescription injectTestRun(
            String runName, String testName, String metricValue, boolean shouldFail) {
        Map<String, String> runMetrics = new HashMap<String, String>(1);
        runMetrics.put(RUN_KEY, metricValue);
        Map<String, String> testMetrics = new HashMap<String, String>(1);
        testMetrics.put(TEST_KEY, metricValue);

        mCollector.testRunStarted(runName, 1);
        final TestDescription test = new TestDescription("FooTest", testName);
        mCollector.testStarted(test);
        if (shouldFail) {
            mCollector.testFailed(test, STACK_TRACE);
        }
        mCollector.testEnded(test, TfMetricProtoUtil.upgradeConvert(testMetrics));
        mCollector.testRunEnded(0, TfMetricProtoUtil.upgradeConvert(runMetrics));
        return test;
    }

    private void verifyListenerTestRunExpectations(
            ITestInvocationListener listener, String runName, String testName, InOrder io) {
        verifyListenerTestRunExpectations(listener, runName, testName, false, io);
    }

    private void verifyListenerTestRunExpectations(
            ITestInvocationListener listener,
            String runName,
            String testName,
            boolean shouldFail,
            InOrder io) {
        io.verify(listener).testRunStarted(Mockito.eq(runName), Mockito.eq(1));
        final TestDescription test = new TestDescription("FooTest", testName);
        io.verify(listener).testStarted(Mockito.eq(test));
        if (shouldFail) {
            io.verify(listener).testFailed(Mockito.eq(test), Mockito.eq(STACK_TRACE));
        }
        io.verify(listener).testEnded(Mockito.eq(test), mTestCapture.capture());
        io.verify(listener).testRunEnded(Mockito.anyLong(), mRunCapture.capture());
    }
}
