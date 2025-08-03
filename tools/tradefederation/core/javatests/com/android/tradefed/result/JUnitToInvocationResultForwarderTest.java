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
package com.android.tradefed.result;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.testtype.DeviceTestCase;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;

/** Unit tests for {@link JUnitToInvocationResultForwarder}. */
@RunWith(JUnit4.class)
public class JUnitToInvocationResultForwarderTest {

    @Mock ITestInvocationListener mListener;
    private JUnitToInvocationResultForwarder mForwarder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mForwarder = new JUnitToInvocationResultForwarder(mListener);
    }

    /** Inherited test annotation to ensure we properly collected it. */
    @Target(ElementType.METHOD)
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyCustomAnnotation {}

    /** Base test class with a test method annotated with an inherited annotation. */
    public class BaseTestClass extends TestCase {
        @MyCustomAnnotation
        public void testbaseWithAnnotation() {}
    }

    /** Extension of the base test class. */
    public class InheritingClass extends BaseTestClass {}

    /**
     * Test method for {@link JUnitToInvocationResultForwarder#addFailure(junit.framework.Test,
     * AssertionFailedError)}.
     */
    @Test
    public void testAddFailure() {
        final AssertionFailedError a = new AssertionFailedError();

        DeviceTestCase test = new DeviceTestCase();
        test.setName("testAddFailure");
        mForwarder.addFailure(test, a);

        verify(mListener)
                .testFailed(
                        Mockito.eq(
                                new TestDescription(
                                        DeviceTestCase.class.getName(), "testAddFailure")),
                        (String) Mockito.any());
    }

    /** Test method for {@link JUnitToInvocationResultForwarder#endTest(junit.framework.Test)}. */
    @Test
    public void testEndTest() {
        HashMap<String, Metric> emptyMap = new HashMap<>();

        DeviceTestCase test = new DeviceTestCase();
        test.setName("testEndTest");

        mForwarder.endTest(test);

        verify(mListener)
                .testEnded(
                        Mockito.eq(
                                new TestDescription(DeviceTestCase.class.getName(), "testEndTest")),
                        Mockito.eq(emptyMap));
    }

    /** Test method for {@link JUnitToInvocationResultForwarder#startTest(junit.framework.Test)}. */
    @Test
    public void testStartTest() {

        DeviceTestCase test = new DeviceTestCase();
        test.setName("testStartTest");

        mForwarder.startTest(test);

        verify(mListener)
                .testStarted(
                        Mockito.eq(
                                new TestDescription(
                                        DeviceTestCase.class.getName(), "testStartTest")));
    }

    /**
     * Test method for {@link JUnitToInvocationResultForwarder#startTest(junit.framework.Test)} when
     * the test method is inherited.
     */
    @Test
    public void testStartTest_annotations() {
        ArgumentCaptor<TestDescription> capture = ArgumentCaptor.forClass(TestDescription.class);

        InheritingClass test = new InheritingClass();
        test.setName("testbaseWithAnnotation");

        mForwarder.startTest(test);

        verify(mListener).testStarted(capture.capture());
        TestDescription desc = capture.getValue();
        assertEquals(InheritingClass.class.getName(), desc.getClassName());
        assertEquals("testbaseWithAnnotation", desc.getTestName());
        assertEquals(1, desc.getAnnotations().size());
        // MyCustomAnnotation is inherited
        assertTrue(desc.getAnnotations().iterator().next() instanceof MyCustomAnnotation);
    }
}
