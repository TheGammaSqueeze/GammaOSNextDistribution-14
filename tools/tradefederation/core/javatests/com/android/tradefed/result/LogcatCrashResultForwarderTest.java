/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
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

/** Unit tests for {@link LogcatCrashResultForwarder}. */
@RunWith(JUnit4.class)
public class LogcatCrashResultForwarderTest {
    private LogcatCrashResultForwarder mReporter;
    @Mock ITestInvocationListener mMockListener;
    @Mock ITestDevice mMockDevice;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockDevice.getDeviceState()).thenReturn(TestDeviceState.ONLINE);
    }

    /** Test if a crash is detected but no crash is found in the logcat. */
    @Test
    @SuppressWarnings("MustBeClosedChecker")
    public void testCaptureTestCrash_noCrashInLogcat() {
        mReporter = new LogcatCrashResultForwarder(mMockDevice, mMockListener);
        TestDescription test = new TestDescription("com.class", "test");

        when(mMockDevice.getLogcatSince(0L))
                .thenReturn(new ByteArrayInputStreamSource("".getBytes()));

        mReporter.testStarted(test, 0L);
        mReporter.testFailed(test, "instrumentation failed. reason: 'Process crashed.'");
        mReporter.testEnded(test, 5L, new HashMap<String, Metric>());

        verify(mMockListener).testStarted(test, 0L);
        verify(mMockListener)
                .testFailed(
                        test,
                        FailureDescription.create(
                                        "instrumentation failed. reason: 'Process crashed.'")
                                .setErrorIdentifier(DeviceErrorIdentifier.INSTRUMENTATION_CRASH));
        verify(mMockListener).testEnded(test, 5L, new HashMap<String, Metric>());
    }

    /**
     * Test that if a crash is detected and found in the logcat, we add the extracted information to
     * the failure.
     */
    @Test
    @SuppressWarnings("MustBeClosedChecker")
    public void testCaptureTestCrash_oneCrashingLogcat() {
        mReporter = new LogcatCrashResultForwarder(mMockDevice, mMockListener);
        TestDescription test = new TestDescription("com.class", "test");

        String logcat =
                "03-20 09:57:36.709 11 11 E AndroidRuntime: FATAL EXCEPTION: Thread-2\n"
                        + "03-20 09:57:36.709 11 11 E AndroidRuntime: Process: android.gesture.cts"
                        + ", PID: 11034\n"
                        + "03-20 09:57:36.709 11 11 E AndroidRuntime: java.lang.RuntimeException:"
                        + " Runtime\n"
                        + "03-20 09:57:36.709 11 11 E AndroidRuntime:    at android.GestureTest$1"
                        + ".run(GestureTest.java:52)\n"
                        + "03-20 09:57:36.709 11 11 E AndroidRuntime:    at java.lang.Thread.run"
                        + "(Thread.java:764)\n"
                        + "03-20 09:57:36.711 11 11 I TestRunner: started: testGetStrokesCount"
                        + "(android.gesture.cts.GestureTest)\n";

        when(mMockDevice.getLogcatSince(0L))
                .thenReturn(new ByteArrayInputStreamSource(logcat.getBytes()));

        mReporter.testStarted(test, 0L);
        mReporter.testFailed(test, "instrumentation failed. reason: 'Process crashed.'");
        mReporter.testEnded(test, 5L, new HashMap<String, Metric>());
        mReporter.testRunFailed("Something went wrong.");

        verify(mMockListener).testStarted(test, 0L);
        // Some crash was added to the failure
        ArgumentCaptor<FailureDescription> captured_1 =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mMockListener).testFailed(Mockito.eq(test), captured_1.capture());
        verify(mMockListener).testEnded(test, 5L, new HashMap<String, Metric>());
        // If a run failure follows, expect it to contain the additional stack too.
        ArgumentCaptor<FailureDescription> captured_2 =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mMockListener).testRunFailed(captured_2.capture());
        assertTrue(
                captured_1
                        .getValue()
                        .getErrorMessage()
                        .contains(
                                "instrumentation failed. reason: 'Process crashed.'\n"
                                        + "Java Crash Messages sorted from most recent:\n"
                                        + "Runtime"));
        assertTrue(
                FailureStatus.SYSTEM_UNDER_TEST_CRASHED.equals(
                        captured_1.getValue().getFailureStatus()));
        assertTrue(
                captured_2
                        .getValue()
                        .getErrorMessage()
                        .contains(
                                "Something went wrong.\n"
                                        + "Java Crash Messages sorted from most recent:\n"
                                        + "Runtime"));
    }

    /**
     * Test that if a crash is not detected at testFailed but later found at testRunFailed, we still
     * add the extracted information to the failure.
     */
    @Test
    @SuppressWarnings("MustBeClosedChecker")
    public void testCaptureTestCrash_oneCrashingLogcatAfterTestEnded() {
        mReporter = new LogcatCrashResultForwarder(mMockDevice, mMockListener);
        TestDescription test = new TestDescription("com.class", "test");

        String logcat =
                "03-20 09:57:36.709 11 11 E AndroidRuntime: FATAL EXCEPTION: Thread-2\n"
                        + "03-20 09:57:36.709 11 11 E AndroidRuntime: Process: android.gesture.cts"
                        + ", PID: 11034\n"
                        + "03-20 09:57:36.709 11 11 E AndroidRuntime: java.lang.RuntimeException:"
                        + " Runtime\n"
                        + "03-20 09:57:36.709 11 11 E AndroidRuntime:    at android.GestureTest$1"
                        + ".run(GestureTest.java:52)\n"
                        + "03-20 09:57:36.709 11 11 E AndroidRuntime:    at java.lang.Thread.run"
                        + "(Thread.java:764)\n"
                        + "03-20 09:57:36.711 11 11 I TestRunner: started: testGetStrokesCount"
                        + "(android.gesture.cts.GestureTest)\n";

        when(mMockDevice.getLogcatSince(0L))
                .thenReturn(new ByteArrayInputStreamSource(logcat.getBytes()));

        mReporter.testStarted(test, 0L);
        mReporter.testFailed(test, "Something went wrong.");
        mReporter.testEnded(test, 5L, new HashMap<String, Metric>());
        mReporter.testRunFailed("instrumentation failed. reason: 'Process crashed.'");

        verify(mMockListener).testStarted(test, 0L);
        // No crash added at the point of testFailed.
        verify(mMockListener).testFailed(test, FailureDescription.create("Something went wrong."));
        verify(mMockListener).testEnded(test, 5L, new HashMap<String, Metric>());
        // If a run failure comes with a crash detected, expect it to contain the additional stack.
        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mMockListener).testRunFailed(captured.capture());
        assertTrue(
                captured.getValue()
                        .getErrorMessage()
                        .contains(
                                "instrumentation failed. reason: 'Process crashed.'\n"
                                        + "Java Crash Messages sorted from most recent:\n"
                                        + "Runtime"));
    }

    /**
     * Test that if several crashes are detected and they are the same repeated stack, then we
     * ignore the duplicate for readability.
     */
    @Test
    @SuppressWarnings("MustBeClosedChecker")
    public void testCaptureTestCrash_duplicateStack() {
        mReporter = new LogcatCrashResultForwarder(mMockDevice, mMockListener);
        TestDescription test = new TestDescription("com.class", "test");

        String logcat =
                "04-25 09:55:47.799  wifi  64  82 E AndroidRuntime: java.lang.Exception: test\n"
                        + "04-25 09:55:47.799  wifi  64  82 E AndroidRuntime: "
                        + "\tat class.method1(Class.java:1)\n"
                        + "04-25 09:55:47.799  wifi  64  82 E AndroidRuntime: "
                        + "\tat class.method2(Class.java:2)\n"
                        + "04-25 09:55:47.799  wifi  65  90 E AndroidRuntime: "
                        + "java.lang.Exception: test\n"
                        + "04-25 09:55:47.799  wifi  65  90 E AndroidRuntime: "
                        + "\tat class.method1(Class.java:1)\n"
                        + "04-25 09:55:47.799  wifi  65  90 E AndroidRuntime: "
                        + "\tat class.method2(Class.java:2)\n";

        when(mMockDevice.getLogcatSince(0L))
                .thenReturn(new ByteArrayInputStreamSource(logcat.getBytes()));

        mReporter.testStarted(test, 0L);
        mReporter.testFailed(test, "Something went wrong.");
        mReporter.testEnded(test, 5L, new HashMap<String, Metric>());
        mReporter.testRunFailed("instrumentation failed. reason: 'Process crashed.'");

        verify(mMockListener).testStarted(test, 0L);
        // No crash added at the point of testFailed.
        verify(mMockListener).testFailed(test, FailureDescription.create("Something went wrong."));
        verify(mMockListener).testEnded(test, 5L, new HashMap<String, Metric>());
        // If a run failure comes with a crash detected, expect it to contain the additional stack.
        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);
        verify(mMockListener).testRunFailed(captured.capture());
        assertTrue(
                captured.getValue()
                        .getErrorMessage()
                        .contains(
                                "instrumentation failed. reason: 'Process crashed.'"
                                        + "\nJava Crash Messages sorted from most recent:\ntest"
                                        + "\njava.lang.Exception: test\n"
                                        + "\tat class.method1(Class.java:1)\n"
                                        + "\tat class.method2(Class.java:2)"));
    }

    /** Test that test-timeout tests have failure status TIMED_OUT. */
    @Test
    @SuppressWarnings("MustBeClosedChecker")
    public void testTestTimedOutTests() {
        String trace =
                "org.junit.runners.model.TestTimedOutException: "
                        + "test timed out after 1000 milliseconds";
        mReporter = new LogcatCrashResultForwarder(mMockDevice, mMockListener);
        TestDescription test = new TestDescription("com.class", "test");

        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);

        mReporter.testStarted(test, 0L);
        mReporter.testFailed(test, trace);
        mReporter.testEnded(test, 5L, new HashMap<String, Metric>());

        verify(mMockListener).testStarted(test, 0L);
        verify(mMockListener).testFailed(Mockito.eq(test), captured.capture());
        verify(mMockListener).testEnded(test, 5L, new HashMap<String, Metric>());
        assertTrue(captured.getValue().getErrorMessage().contains(trace));
        assertTrue(FailureStatus.TIMED_OUT.equals(captured.getValue().getFailureStatus()));
    }

    /** Test that shell-timeout tests have failure status TIMED_OUT. */
    @Test
    @SuppressWarnings("MustBeClosedChecker")
    public void testShellTimedOutTests() {
        String trace =
                "Test failed to run to completion. "
                        + " Reason: 'Failed to receive adb shell test output within 3000 ms. "
                        + "Test may have timed out, or adb connection to device became "
                        + "unresponsive'. Check device logcat for details";
        mReporter = new LogcatCrashResultForwarder(mMockDevice, mMockListener);
        TestDescription test = new TestDescription("com.class", "test");

        ArgumentCaptor<FailureDescription> captured =
                ArgumentCaptor.forClass(FailureDescription.class);

        mReporter.testStarted(test, 0L);
        mReporter.testFailed(test, trace);
        mReporter.testEnded(test, 5L, new HashMap<String, Metric>());

        verify(mMockListener).testStarted(test, 0L);
        verify(mMockListener).testFailed(Mockito.eq(test), captured.capture());
        verify(mMockListener).testEnded(test, 5L, new HashMap<String, Metric>());
        assertTrue(captured.getValue().getErrorMessage().contains(trace));
        assertTrue(FailureStatus.TIMED_OUT.equals(captured.getValue().getFailureStatus()));
    }
}
