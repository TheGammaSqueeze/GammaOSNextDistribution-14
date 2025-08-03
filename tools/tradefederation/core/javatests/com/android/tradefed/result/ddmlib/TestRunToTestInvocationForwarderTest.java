/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.result.ddmlib;

import static org.mockito.Mockito.verify;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestLifeCycleReceiver;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;

/** Run unit tests {@link TestRunToTestInvocationForwarder}. */
@RunWith(JUnit4.class)
public class TestRunToTestInvocationForwarderTest {

    private static final String RUN_NAME = "run";

    private TestRunToTestInvocationForwarder mForwarder;
    @Mock ITestLifeCycleReceiver mMockListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mForwarder = new TestRunToTestInvocationForwarder(mMockListener);
    }

    @Test
    public void testForwarding() {
        TestIdentifier tid1 = new TestIdentifier("class", "test1");
        TestDescription td1 = new TestDescription(tid1.getClassName(), tid1.getTestName());
        TestIdentifier tid2 = new TestIdentifier("class", "test2");
        TestDescription td2 = new TestDescription(tid2.getClassName(), tid2.getTestName());

        mForwarder.testRunStarted(RUN_NAME, 2);

        mForwarder.testStarted(tid1);
        mForwarder.testFailed(tid1, "I failed");
        mForwarder.testEnded(tid1, new HashMap<>());

        mForwarder.testStarted(tid2);
        mForwarder.testFailed(tid2, "I failed");
        mForwarder.testEnded(tid2, new HashMap<>());

        mForwarder.testRunEnded(500L, new HashMap<>());

        verify(mMockListener).testRunStarted(RUN_NAME, 2);
        verify(mMockListener).testStarted(td1);
        verify(mMockListener).testFailed(td1, FailureDescription.create("I failed"));
        verify(mMockListener).testEnded(td1, new HashMap<String, Metric>());
        verify(mMockListener).testStarted(td2);
        verify(mMockListener).testFailed(td2, FailureDescription.create("I failed"));
        verify(mMockListener).testEnded(td2, new HashMap<String, Metric>());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    @Test
    public void testForwarding_null() {
        TestIdentifier tid1 = new TestIdentifier("class", "test1");
        TestDescription td1 = new TestDescription(tid1.getClassName(), tid1.getTestName());
        TestIdentifier tid2 = new TestIdentifier("class", "null");

        // Second bad method is not propagated, instead we fail the run
        FailureDescription expectedFailure =
                FailureDescription.create(
                        String.format(
                                TestRunToTestInvocationForwarder.ERROR_MESSAGE_FORMAT
                                        + " Stack:I failed",
                                tid2.getTestName(),
                                tid2),
                        FailureStatus.TEST_FAILURE);

        mForwarder.testRunStarted(RUN_NAME, 2);

        mForwarder.testStarted(tid1);
        mForwarder.testFailed(tid1, "I failed");
        mForwarder.testEnded(tid1, new HashMap<>());

        mForwarder.testStarted(tid2);
        mForwarder.testFailed(tid2, "I failed");
        mForwarder.testEnded(tid2, new HashMap<>());

        mForwarder.testRunEnded(500L, new HashMap<>());

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted(RUN_NAME, 2);
        inOrder.verify(mMockListener).testStarted(td1);
        inOrder.verify(mMockListener).testFailed(td1, FailureDescription.create("I failed"));
        inOrder.verify(mMockListener).testEnded(td1, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).testRunFailed(expectedFailure);
        inOrder.verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testForwarding_null_ignored() {
        TestIdentifier tid1 = new TestIdentifier("class", "test1");
        TestDescription td1 = new TestDescription(tid1.getClassName(), tid1.getTestName());
        TestIdentifier tid2 = new TestIdentifier("class", "null");
        TestDescription td2 = new TestDescription(tid2.getClassName(), tid2.getTestName());

        mForwarder.testRunStarted(RUN_NAME, 2);

        mForwarder.testStarted(tid1);
        mForwarder.testFailed(tid1, "I failed");
        mForwarder.testEnded(tid1, new HashMap<>());

        mForwarder.testStarted(tid2);
        mForwarder.testIgnored(tid2);
        mForwarder.testEnded(tid2, new HashMap<>());

        mForwarder.testRunEnded(500L, new HashMap<>());

        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted(RUN_NAME, 2);
        inOrder.verify(mMockListener).testStarted(td1);
        inOrder.verify(mMockListener).testFailed(td1, FailureDescription.create("I failed"));
        inOrder.verify(mMockListener).testEnded(td1, new HashMap<String, Metric>());
        inOrder.verify(mMockListener).testStarted(td2);
        inOrder.verify(mMockListener).testIgnored(td2);
        inOrder.verify(mMockListener).testEnded(td2, new HashMap<String, Metric>());
        inOrder.verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testForwarding_initError() {
        TestIdentifier tid1 = new TestIdentifier("class", "test1");
        TestDescription td1 = new TestDescription(tid1.getClassName(), tid1.getTestName());
        TestIdentifier tid2 = new TestIdentifier("class", "initializationError");

        // Second bad method is not propagated, instead we fail the run
        FailureDescription expectedFailure =
                FailureDescription.create(
                        String.format(
                                TestRunToTestInvocationForwarder.ERROR_MESSAGE_FORMAT
                                        + " Stack:I failed",
                                tid2.getTestName(),
                                tid2),
                        FailureStatus.TEST_FAILURE);

        mForwarder.testRunStarted(RUN_NAME, 2);

        mForwarder.testStarted(tid1);
        mForwarder.testFailed(tid1, "I failed");
        mForwarder.testEnded(tid1, new HashMap<>());

        mForwarder.testStarted(tid2);
        mForwarder.testFailed(tid2, "I failed");
        mForwarder.testEnded(tid2, new HashMap<>());

        mForwarder.testRunEnded(500L, new HashMap<>());

        verify(mMockListener).testRunStarted(RUN_NAME, 2);
        verify(mMockListener).testStarted(td1);
        verify(mMockListener).testFailed(td1, FailureDescription.create("I failed"));
        verify(mMockListener).testEnded(td1, new HashMap<String, Metric>());
        verify(mMockListener).testRunFailed(expectedFailure);
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }
}
