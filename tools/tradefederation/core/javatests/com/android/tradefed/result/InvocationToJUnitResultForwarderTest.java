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

import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;

import junit.framework.TestListener;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;

/** Unit tests for {@link InvocationToJUnitResultForwarder}. */
@RunWith(JUnit4.class)
public class InvocationToJUnitResultForwarderTest {

    private static final String TEST_NAME = "testName";
    private static final String CLASS_NAME = "className";
    @Mock TestListener mJUnitListener;
    private InvocationToJUnitResultForwarder mTestForwarder;
    private TestDescription mTestIdentifier;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mTestForwarder = new InvocationToJUnitResultForwarder(mJUnitListener);
        mTestIdentifier = new TestDescription(CLASS_NAME, TEST_NAME);
    }

    /**
     * Simple test for {@link InvocationToJUnitResultForwarder#testEnded(TestDescription, HashMap)}.
     *
     * <p>Verifies that data put into TestIdentifier is forwarded in correct format
     */
    @Test
    public void testTestEnded() {

        mTestForwarder.testEnded(mTestIdentifier, new HashMap<String, Metric>());
        // TODO: check format
    }

    // TODO: add more tests
}
