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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.proto.TfMetricProtoUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link DeviceTestCase}. */
@RunWith(JUnit4.class)
public class DeviceTestCaseTest {

    public static class MockTest extends DeviceTestCase {

        public void test1() {
            // test adding a metric during the test.
            addTestMetric("test", "value");
        }

        public void test2() {}
    }

    @MyAnnotation1
    public static class MockAnnotatedTest extends DeviceTestCase {

        @MyAnnotation1
        public void test1() {}

        @MyAnnotation2
        public void test2() {}
    }

    /** A test class that illustrate duplicate names but from only one real test. */
    public static class DuplicateTest extends DeviceTestCase {

        public void test1() {
            test1("");
        }

        private void test1(String arg) {
            assertTrue(arg.isEmpty());
        }

        public void test2() {}
    }

    /** Simple Annotation class for testing */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyAnnotation1 {}

    /** Simple Annotation class for testing */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyAnnotation2 {}

    public static class MockAbortTest extends DeviceTestCase {

        private static final String EXCEP_MSG = "failed";
        private static final String FAKE_SERIAL = "fakeserial";

        public void test1() throws DeviceNotAvailableException {
            throw new DeviceNotAvailableException(EXCEP_MSG, FAKE_SERIAL);
        }
    }

    private TestInformation mTestInfo;

    @Before
    public void setUp() {
        mTestInfo = TestInformation.newBuilder().build();
    }

    /** Verify that calling run on a DeviceTestCase will run all test methods. */
    @Test
    public void testRun_suite() throws Exception {
        MockTest test = new MockTest();

        ITestInvocationListener listener = mock(ITestInvocationListener.class);

        final TestDescription test1 = new TestDescription(MockTest.class.getName(), "test1");
        final TestDescription test2 = new TestDescription(MockTest.class.getName(), "test2");

        Map<String, String> metrics = new HashMap<>();
        metrics.put("test", "value");

        test.run(mTestInfo, listener);

        verify(listener).testRunStarted(MockTest.class.getName(), 2);
        verify(listener).testStarted(test1);
        verify(listener).testEnded(test1, TfMetricProtoUtil.upgradeConvert(metrics));
        verify(listener).testStarted(test2);
        verify(listener).testEnded(test2, new HashMap<String, Metric>());
        verify(listener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /**
     * Verify that calling run on a {@link DeviceTestCase} will only run methods included by
     * filtering.
     */
    @Test
    public void testRun_includeFilter() throws Exception {
        MockTest test = new MockTest();
        test.addIncludeFilter("com.android.tradefed.testtype.DeviceTestCaseTest$MockTest#test1");
        ITestInvocationListener listener = mock(ITestInvocationListener.class);

        final TestDescription test1 = new TestDescription(MockTest.class.getName(), "test1");

        Map<String, String> metrics = new HashMap<>();
        metrics.put("test", "value");

        test.run(mTestInfo, listener);

        verify(listener).testRunStarted(MockTest.class.getName(), 1);
        verify(listener).testStarted(test1);
        verify(listener).testEnded(test1, TfMetricProtoUtil.upgradeConvert(metrics));
        verify(listener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /**
     * Verify that calling run on a {@link DeviceTestCase} will not run methods excluded by
     * filtering.
     */
    @Test
    public void testRun_excludeFilter() throws Exception {
        MockTest test = new MockTest();
        test.addExcludeFilter("com.android.tradefed.testtype.DeviceTestCaseTest$MockTest#test1");
        ITestInvocationListener listener = mock(ITestInvocationListener.class);

        final TestDescription test2 = new TestDescription(MockTest.class.getName(), "test2");

        test.run(mTestInfo, listener);

        verify(listener).testRunStarted(MockTest.class.getName(), 1);
        verify(listener).testStarted(test2);
        verify(listener).testEnded(test2, new HashMap<String, Metric>());
        verify(listener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /**
     * Verify that calling run on a {@link DeviceTestCase} only runs AnnotatedElements included by
     * filtering.
     */
    @Test
    public void testRun_includeAnnotationFiltering() throws Exception {
        MockAnnotatedTest test = new MockAnnotatedTest();
        test.addIncludeAnnotation("com.android.tradefed.testtype.DeviceTestCaseTest$MyAnnotation1");
        test.addExcludeAnnotation("com.android.tradefed.testtype.DeviceTestCaseTest$MyAnnotation2");
        ITestInvocationListener listener = mock(ITestInvocationListener.class);

        final TestDescription test1 =
                new TestDescription(MockAnnotatedTest.class.getName(), "test1");

        test.run(mTestInfo, listener);

        verify(listener).testRunStarted(MockAnnotatedTest.class.getName(), 1);
        verify(listener).testStarted(test1);
        verify(listener).testEnded(test1, new HashMap<String, Metric>());
        verify(listener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Verify that we properly carry the annotations of the methods. */
    @Test
    public void testRun_checkAnnotation() throws Exception {
        MockAnnotatedTest test = new MockAnnotatedTest();
        ITestInvocationListener listener = mock(ITestInvocationListener.class);

        ArgumentCaptor<TestDescription> capture = ArgumentCaptor.forClass(TestDescription.class);

        test.run(mTestInfo, listener);

        verify(listener).testRunStarted(MockAnnotatedTest.class.getName(), 2);
        verify(listener, times(2)).testStarted(capture.capture());
        verify(listener, times(2))
                .testEnded(capture.capture(), Mockito.<HashMap<String, Metric>>any());
        verify(listener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());

        List<TestDescription> descriptions = capture.getAllValues();
        // Ensure we properly capture the annotations for both methods.
        for (TestDescription desc : descriptions) {
            assertFalse(desc.getAnnotations().isEmpty());
        }
    }

    /**
     * Verify that calling run on a {@link DeviceTestCase} does not run AnnotatedElements excluded
     * by filtering.
     */
    @Test
    public void testRun_excludeAnnotationFiltering() throws Exception {
        MockAnnotatedTest test = new MockAnnotatedTest();
        test.addExcludeAnnotation("com.android.tradefed.testtype.DeviceTestCaseTest$MyAnnotation2");
        ITestInvocationListener listener = mock(ITestInvocationListener.class);

        final TestDescription test1 =
                new TestDescription(MockAnnotatedTest.class.getName(), "test1");

        test.run(mTestInfo, listener);

        verify(listener).testRunStarted(MockAnnotatedTest.class.getName(), 1);
        verify(listener).testStarted(test1);
        verify(listener).testEnded(test1, new HashMap<String, Metric>());
        verify(listener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Regression test to verify a single test can still be run. */
    @Test
    public void testRun_singleTest() throws DeviceNotAvailableException {
        MockTest test = new MockTest();
        test.setName("test1");

        ITestInvocationListener listener = mock(ITestInvocationListener.class);

        final TestDescription test1 = new TestDescription(MockTest.class.getName(), "test1");

        Map<String, String> metrics = new HashMap<>();
        metrics.put("test", "value");

        test.run(mTestInfo, listener);

        verify(listener).testRunStarted(MockTest.class.getName(), 1);
        verify(listener).testStarted(test1);
        verify(listener).testEnded(test1, TfMetricProtoUtil.upgradeConvert(metrics));
        verify(listener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Verify that a device not available exception is thrown up. */
    @Test
    public void testRun_deviceNotAvail() {
        MockAbortTest test = new MockAbortTest();
        // create a mock ITestInvocationListener, because results are easier to verify
        ITestInvocationListener listener = mock(ITestInvocationListener.class);

        final TestDescription test1 = new TestDescription(MockAbortTest.class.getName(), "test1");

        try {
            test.run(mTestInfo, listener);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }

        verify(listener).testRunStarted(MockAbortTest.class.getName(), 1);
        verify(listener).testStarted(test1);
        verify(listener).testFailed(Mockito.eq(test1), Mockito.contains(MockAbortTest.EXCEP_MSG));
        verify(listener).testEnded(test1, new HashMap<String, Metric>());
        verify(listener).testRunFailed(Mockito.contains(MockAbortTest.EXCEP_MSG));
        verify(listener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /**
     * Test success case for {@link DeviceTestCase#run(TestInformation, ITestInvocationListener)} in
     * collector mode, where test to run is a {@link TestCase}
     */
    @Test
    public void testRun_testcaseCollectMode() throws Exception {
        ITestInvocationListener listener = mock(ITestInvocationListener.class);
        MockTest test = new MockTest();
        test.setCollectTestsOnly(true);

        test.run(mTestInfo, listener);

        verify(listener).testRunStarted((String) Mockito.any(), Mockito.eq(2));
        verify(listener, times(2)).testStarted(Mockito.any());
        verify(listener, times(2)).testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        verify(listener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /**
     * Test success case for {@link DeviceTestCase#run(TestInformation, ITestInvocationListener)} in
     * collector mode, where test to run is a {@link TestCase}
     */
    @Test
    public void testRun_testcaseCollectMode_singleMethod() throws Exception {
        ITestInvocationListener listener = mock(ITestInvocationListener.class);
        MockTest test = new MockTest();
        test.setName("test1");
        test.setCollectTestsOnly(true);

        test.run(mTestInfo, listener);

        verify(listener).testRunStarted((String) Mockito.any(), Mockito.eq(1));
        verify(listener).testStarted(Mockito.any());
        verify(listener).testEnded(Mockito.any(), Mockito.<HashMap<String, Metric>>any());
        verify(listener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /**
     * Test that when a test class has some private method with a method name we properly ignore it
     * and only consider the actual real method that can execute in the filtering.
     */
    @Test
    public void testRun_duplicateName() throws Exception {
        DuplicateTest test = new DuplicateTest();
        ITestInvocationListener listener = mock(ITestInvocationListener.class);

        final TestDescription test1 = new TestDescription(DuplicateTest.class.getName(), "test1");
        final TestDescription test2 = new TestDescription(DuplicateTest.class.getName(), "test2");

        test.run(mTestInfo, listener);

        verify(listener).testRunStarted(DuplicateTest.class.getName(), 2);
        verify(listener).testStarted(test1);
        verify(listener).testEnded(test1, new HashMap<String, Metric>());
        verify(listener).testStarted(test2);
        verify(listener).testEnded(test2, new HashMap<String, Metric>());
        verify(listener).testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }
}
