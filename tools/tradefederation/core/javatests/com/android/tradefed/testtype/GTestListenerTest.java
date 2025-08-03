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
package com.android.tradefed.testtype;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FailureDescription;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;

/** Unit tests for {@link GTestListener}. */
@RunWith(JUnit4.class)
public class GTestListenerTest {
    @Mock ITestInvocationListener mMockListener;

    /** Helper to initialize the various EasyMocks we'll need. */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    /** Verify test passes without duplicate tests */
    @Test
    public void testNoDuplicateTests() {
        String moduleName = "testNoDuplicateTest";
        String testClass = "testClass";
        String testName1 = "testName1";
        TestDescription testId1 = new TestDescription(testClass, testName1);
        String testName2 = "testName2";
        TestDescription testId2 = new TestDescription(testClass, testName2);

        HashMap<String, Metric> emptyMap = new HashMap<>();
        GTestListener listener = new GTestListener(mMockListener);
        listener.testRunStarted(moduleName, 2);
        listener.testStarted(testId1);
        listener.testEnded(testId1, emptyMap);
        listener.testStarted(testId2);
        listener.testEnded(testId2, emptyMap);
        listener.testRunEnded(0, emptyMap);

        verify(mMockListener).testRunStarted(Mockito.eq(moduleName), Mockito.eq(2));
        verify(mMockListener).testStarted(Mockito.eq(testId1), Mockito.anyLong());
        verify(mMockListener)
                .testEnded(
                        Mockito.eq(testId1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener).testStarted(Mockito.eq(testId2), Mockito.anyLong());
        verify(mMockListener)
                .testEnded(
                        Mockito.eq(testId2),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Verify test with duplicate tests fails if option enabled */
    @Test
    public void testDuplicateTestsFailsWithOptionEnabled() {
        String moduleName = "testWithDuplicateTests";
        String testClass = "testClass";
        String testName1 = "testName1";
        String duplicateTestsMessage = "1 tests ran more than once. Full list:";
        TestDescription testId1 = new TestDescription(testClass, testName1);

        ArgumentCaptor<FailureDescription> capturedFailureDescription =
                ArgumentCaptor.forClass(FailureDescription.class);

        HashMap<String, Metric> emptyMap = new HashMap<>();
        GTestListener listener = new GTestListener(mMockListener);
        listener.testRunStarted(moduleName, 2);
        listener.testStarted(testId1);
        listener.testEnded(testId1, emptyMap);
        listener.testStarted(testId1);
        listener.testEnded(testId1, emptyMap);
        listener.testRunEnded(0, emptyMap);

        verify(mMockListener).testRunStarted(Mockito.eq(moduleName), Mockito.eq(2));
        verify(mMockListener, times(2)).testStarted(Mockito.eq(testId1), Mockito.anyLong());
        verify(mMockListener, times(2))
                .testEnded(
                        Mockito.eq(testId1),
                        Mockito.anyLong(),
                        Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener).testRunFailed(capturedFailureDescription.capture());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        FailureDescription failureDescription = capturedFailureDescription.getValue();
        assertTrue(failureDescription.getErrorMessage().contains(duplicateTestsMessage));
        assertTrue(FailureStatus.TEST_FAILURE.equals(failureDescription.getFailureStatus()));
    }
}
