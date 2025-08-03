/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tradefed.testtype.suite;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Unit tests for {@link com.android.tradefed.testtype.suite.TestFailureListener} */
@RunWith(JUnit4.class)
public class TestFailureListenerTest {

    private TestFailureListener mFailureListener;
    @Mock ITestInvocationListener mMockListener;
    @Mock ITestDevice mMockDevice;
    private List<ITestDevice> mListDevice;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mListDevice = new ArrayList<>();
        mListDevice.add(mMockDevice);
        when(mMockDevice.getSerialNumber()).thenReturn("SERIAL");
        // Create base failure listener with all option ON and default logcat size.
        mFailureListener = new TestFailureListener(mListDevice, true, true);
        mFailureListener.setLogger(mMockListener);
    }

    /** Test that on testFailed all the collection are triggered. */
    @Test
    public void testTestFailed() throws Exception {
        TestDescription testId = new TestDescription("com.fake", "methodfake");
        final String trace = "oups it failed";
        // Bugreport routine - testLog is internal to it.
        when(mMockDevice.logBugreport(Mockito.any(), Mockito.any())).thenReturn(true);
        // Reboot routine
        when(mMockDevice.getProperty(Mockito.eq("ro.build.type"))).thenReturn("userdebug");

        mFailureListener.testStarted(testId);
        mFailureListener.testFailed(testId, trace);
        mFailureListener.testEnded(testId, new HashMap<String, Metric>());

        verify(mMockDevice).reboot();
    }

    /** Test when a test failure occurs and it is a user build, no reboot is attempted. */
    @Test
    public void testTestFailed_userBuild() throws Exception {
        mFailureListener = new TestFailureListener(mListDevice, false, true);
        mFailureListener.setLogger(mMockListener);
        final String trace = "oups it failed";
        TestDescription testId = new TestDescription("com.fake", "methodfake");
        when(mMockDevice.getProperty(Mockito.eq("ro.build.type"))).thenReturn("user");

        mFailureListener.testStarted(testId);
        mFailureListener.testFailed(testId, trace);
        mFailureListener.testEnded(testId, new HashMap<String, Metric>());

        verify(mMockDevice).getProperty(Mockito.eq("ro.build.type"));
    }

    /**
     * Test when a test failure occurs during a multi device run. Each device should capture the
     * logs.
     */
    @Test
    public void testFailed_multiDevice() throws Exception {
        ITestDevice device2 = mock(ITestDevice.class);
        mListDevice.add(device2);
        mFailureListener = new TestFailureListener(mListDevice, false, true);
        mFailureListener.setLogger(mMockListener);
        final String trace = "oups it failed";
        TestDescription testId = new TestDescription("com.fake", "methodfake");
        when(mMockDevice.getProperty(Mockito.eq("ro.build.type"))).thenReturn("debug");
        when(device2.getSerialNumber()).thenReturn("SERIAL2");
        when(device2.getProperty(Mockito.eq("ro.build.type"))).thenReturn("debug");

        mFailureListener.testStarted(testId);
        mFailureListener.testFailed(testId, trace);
        mFailureListener.testEnded(testId, new HashMap<String, Metric>());
        InOrder inOrder = Mockito.inOrder(mMockDevice, device2);
        inOrder.verify(mMockDevice).getProperty(Mockito.eq("ro.build.type"));
        inOrder.verify(mMockDevice).reboot();
        inOrder.verify(device2).reboot();
    }
}
