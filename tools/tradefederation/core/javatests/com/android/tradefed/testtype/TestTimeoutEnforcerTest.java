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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link TestTimeoutEnforcer}. */
@RunWith(JUnit4.class)
public class TestTimeoutEnforcerTest {

    private TestTimeoutEnforcer mEnforcer;
    @Mock ITestInvocationListener mListener;
    private TestDescription mTest = new TestDescription("class", "test");

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mEnforcer = new TestTimeoutEnforcer(500L, TimeUnit.MILLISECONDS, mListener);
    }

    @Test
    public void testNoTimeout() {

        mEnforcer.testStarted(mTest, 0L);
        mEnforcer.testEnded(mTest, 250L, new HashMap<String, Metric>());

        verify(mListener).testStarted(mTest, 0L);
        verify(mListener).testEnded(mTest, 250L, new HashMap<String, Metric>());
    }

    @Test
    public void testTimeout() {

        mEnforcer.testStarted(mTest, 0L);
        mEnforcer.testEnded(mTest, 550L, new HashMap<String, Metric>());

        verify(mListener).testStarted(mTest, 0L);
        verify(mListener)
                .testFailed(
                        mTest,
                        FailureDescription.create(
                                "class#test took 550 ms while timeout is 500 ms",
                                FailureStatus.TIMED_OUT));
        verify(mListener).testEnded(mTest, 550L, new HashMap<String, Metric>());
    }

    @Test
    public void testFailedTest() {

        mEnforcer.testStarted(mTest, 0L);
        mEnforcer.testFailed(mTest, "i failed");
        mEnforcer.testEnded(mTest, 550L, new HashMap<String, Metric>());

        verify(mListener).testStarted(mTest, 0L);
        verify(mListener).testFailed(mTest, "i failed");
        verify(mListener).testEnded(mTest, 550L, new HashMap<String, Metric>());
    }
}
