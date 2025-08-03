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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import com.android.tradefed.TestAppConstants;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.RecoveryMode;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestStatus;
import com.android.tradefed.util.RunUtil;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.HashMap;

/** Functional tests for {@link InstrumentationTest}. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class InstrumentationTestFuncTest implements IDeviceTest, ITestInformationReceiver {

    private static final long SHELL_TIMEOUT = 2500;
    private static final int TEST_TIMEOUT = 2000;
    private static final long WAIT_FOR_DEVICE_AVAILABLE = 5 * 60 * 1000;

    private ITestDevice mDevice;
    private TestInformation mTestInfo;

    /** The {@link InstrumentationTest} under test */
    private InstrumentationTest mInstrumentationTest;

    @Mock ITestInvocationListener mMockListener;

    @Override
    public void setTestInformation(TestInformation testInformation) {
        mTestInfo = testInformation;
    }

    @Override
    public TestInformation getTestInformation() {
        return mTestInfo;
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mInstrumentationTest = new InstrumentationTest();
        mInstrumentationTest.setPackageName(TestAppConstants.TESTAPP_PACKAGE);
        mInstrumentationTest.setDevice(getDevice());
        // use no timeout by default
        mInstrumentationTest.setShellTimeout(-1);
        // set to no rerun by default
        mInstrumentationTest.setRerunMode(false);

        getDevice().disableKeyguard();
    }

    /** Test normal run scenario with a single passed test result. */
    @Ignore
    @Test
    public void testRun() throws DeviceNotAvailableException {
        CLog.i("testRun");
        TestDescription expectedTest =
                new TestDescription(
                        TestAppConstants.TESTAPP_CLASS, TestAppConstants.PASSED_TEST_METHOD);
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.PASSED_TEST_METHOD);
        mInstrumentationTest.setTestTimeout(TEST_TIMEOUT);
        mInstrumentationTest.setShellTimeout(SHELL_TIMEOUT);

        mInstrumentationTest.run(getTestInformation(), mMockListener);
        InOrder inOrder = Mockito.inOrder(mMockListener);
        inOrder.verify(mMockListener).testRunStarted(TestAppConstants.TESTAPP_PACKAGE, 1);
        inOrder.verify(mMockListener).testStarted(Mockito.eq(expectedTest));
        inOrder.verify(mMockListener)
                .testEnded(Mockito.eq(expectedTest), Mockito.<HashMap<String, Metric>>any());
        inOrder.verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());

        verify(mMockListener).testRunStarted(TestAppConstants.TESTAPP_PACKAGE, 1);
        verify(mMockListener).testStarted(Mockito.eq(expectedTest));
        verify(mMockListener)
                .testEnded(Mockito.eq(expectedTest), Mockito.<HashMap<String, Metric>>any());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
    }

    /** Test normal run scenario with a single failed test result. */
    @Ignore
    @Test
    public void testRun_testFailed() throws DeviceNotAvailableException {
        CLog.i("testRun_testFailed");
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.FAILED_TEST_METHOD);
        mInstrumentationTest.setTestTimeout(TEST_TIMEOUT);
        mInstrumentationTest.setShellTimeout(SHELL_TIMEOUT);
        String[] error = new String[1];
        error[0] = null;
        mInstrumentationTest.run(
                getTestInformation(),
                new ITestInvocationListener() {
                    @Override
                    public void testFailed(TestDescription test, String trace) {
                        error[0] = trace;
                    }
                });
        assertNotNull("testFailed was not called", error[0]);
        assertTrue(error[0].contains("junit.framework.AssertionFailedError: test failed"));
    }

    /** Test run scenario where test process crashes. */
    @Ignore
    @Test
    public void testRun_testCrash() throws DeviceNotAvailableException {
        CLog.i("testRun_testCrash");
        TestDescription expectedTest =
                new TestDescription(
                        TestAppConstants.TESTAPP_CLASS, TestAppConstants.CRASH_TEST_METHOD);
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.CRASH_TEST_METHOD);
        mInstrumentationTest.setTestTimeout(TEST_TIMEOUT);
        mInstrumentationTest.setShellTimeout(SHELL_TIMEOUT);

        try {
            mInstrumentationTest.run(getTestInformation(), mMockListener);
            InOrder inOrder = Mockito.inOrder(mMockListener);
            inOrder.verify(mMockListener).testRunStarted(TestAppConstants.TESTAPP_PACKAGE, 1);
            inOrder.verify(mMockListener).testStarted(Mockito.eq(expectedTest));
            if (getDevice().getApiLevel() <= 23) {
                // Before N handling of instrumentation crash is slightly different.
                inOrder.verify(mMockListener)
                        .testFailed(Mockito.eq(expectedTest), Mockito.contains("RuntimeException"));
                inOrder.verify(mMockListener)
                        .testEnded(Mockito.eq(expectedTest), Mockito.<HashMap<String, Metric>>any());
                inOrder.verify(mMockListener)
                        .testRunFailed(
                                Mockito.eq(
                                        "Instrumentation run failed due to"
                                            + " 'java.lang.RuntimeException'"));
            } else {
                inOrder.verify(mMockListener)
                        .testFailed(Mockito.eq(expectedTest), Mockito.contains("Process crashed."));
                inOrder.verify(mMockListener)
                        .testEnded(Mockito.eq(expectedTest), Mockito.<HashMap<String, Metric>>any());
                inOrder.verify(mMockListener)
                        .testRunFailed(
                                Mockito.eq("Instrumentation run failed due to 'Process crashed.'"));
            }
            inOrder.verify(mMockListener)
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());

            verify(mMockListener).testRunStarted(TestAppConstants.TESTAPP_PACKAGE, 1);
            verify(mMockListener).testStarted(Mockito.eq(expectedTest));
            verify(mMockListener)
                    .testFailed(Mockito.eq(expectedTest), Mockito.contains("RuntimeException"));
            verify(mMockListener)
                    .testEnded(Mockito.eq(expectedTest), Mockito.<HashMap<String, Metric>>any());
            verify(mMockListener)
                    .testRunFailed(
                            Mockito.eq(
                                    "Instrumentation run failed due to"
                                        + " 'java.lang.RuntimeException'"));
            verify(mMockListener)
                    .testFailed(Mockito.eq(expectedTest), Mockito.contains("Process crashed."));
            verify(mMockListener)
                    .testEnded(Mockito.eq(expectedTest), Mockito.<HashMap<String, Metric>>any());
            verify(mMockListener)
                    .testRunFailed(
                            Mockito.eq("Instrumentation run failed due to 'Process crashed.'"));
            verify(mMockListener)
                    .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>any());
        } finally {
            getDevice().waitForDeviceAvailable();
        }
    }

    /** Test run scenario where test run hangs indefinitely, and times out. */
    @Ignore
    @Test
    public void testRun_testTimeout() throws DeviceNotAvailableException {
        CLog.i("testRun_testTimeout");
        RecoveryMode initMode = getDevice().getRecoveryMode();
        getDevice().setRecoveryMode(RecoveryMode.NONE);
        try {
            mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
            mInstrumentationTest.setMethodName(TestAppConstants.TIMEOUT_TEST_METHOD);
            mInstrumentationTest.setShellTimeout(SHELL_TIMEOUT);
            mInstrumentationTest.setTestTimeout(TEST_TIMEOUT);

            String[] error = new String[1];
            error[0] = null;
            mInstrumentationTest.run(
                    getTestInformation(),
                    new ITestInvocationListener() {
                        @Override
                        public void testFailed(TestDescription test, String trace) {
                            error[0] = trace;
                        }
                    });
            assertEquals(
                    "Test failed to run to completion. Reason: 'Failed to receive adb shell test"
                        + " output within 2500 ms. Test may have timed out, or adb connection to"
                        + " device became unresponsive'. Check device logcat for details",
                    error[0]);
        } finally {
            getDevice().setRecoveryMode(initMode);
            RunUtil.getDefault().sleep(500);
        }
    }

    /** Test run scenario where device reboots during test run. */
    @Ignore
    @Test
    public void testRun_deviceReboot() throws Exception {
        CLog.i("testRun_deviceReboot");
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.TIMEOUT_TEST_METHOD);
        mInstrumentationTest.setShellTimeout(0);
        mInstrumentationTest.setTestTimeout(0);
        // Set a max timeout to avoid hanging forever for safety
        // OptionSetter setter = new OptionSetter(mInstrumentationTest);
        // setter.setOptionValue("max-timeout", "600000");

        // fork off a thread to do the reboot
        Thread rebootThread =
                new Thread() {
                    @Override
                    public void run() {
                        // wait for test run to begin
                        try {
                            // Give time to the instrumentation to start
                            Thread.sleep(2000);
                            getDevice().reboot();
                        } catch (InterruptedException e) {
                            CLog.w("interrupted");
                        } catch (DeviceNotAvailableException dnae) {
                            CLog.w("Device did not come back online after reboot");
                        }
                    }
                };
        rebootThread.setName("InstrumentationTestFuncTest#testRun_deviceReboot");
        rebootThread.start();
        try {
            String[] error = new String[1];
            error[0] = null;
            mInstrumentationTest.run(
                    getTestInformation(),
                    new ITestInvocationListener() {
                        @Override
                        public void testRunFailed(String errorMessage) {
                            error[0] = errorMessage;
                        }
                    });
            assertEquals("Test run failed to complete. Expected 1 tests, received 0", error[0]);
        } catch (DeviceUnresponsiveException expected) {
            // expected
        } finally {
            rebootThread.join(WAIT_FOR_DEVICE_AVAILABLE);
            getDevice().waitForDeviceAvailable();
        }
    }

    /** Test that when a max-timeout is set the instrumentation is stopped. */
    @Ignore
    @Test
    public void testRun_maxTimeout() throws Exception {
        CLog.i("testRun_maxTimeout");
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.TIMEOUT_TEST_METHOD);
        mInstrumentationTest.setShellTimeout(0);
        mInstrumentationTest.setTestTimeout(0);
        OptionSetter setter = new OptionSetter(mInstrumentationTest);
        setter.setOptionValue("max-timeout", "5000");
        final String[] called = new String[1];
        called[0] = null;
        mInstrumentationTest.run(
                getTestInformation(),
                new ITestInvocationListener() {
                    @Override
                    public void testRunFailed(String errorMessage) {
                        called[0] = errorMessage;
                    }
                });
        assertEquals(
                "com.android.ddmlib.TimeoutException: executeRemoteCommand timed out after 5000ms",
                called[0]);
    }

    /** Test run scenario where device runtime resets during test run. */
    @Test
    @Ignore
    public void testRun_deviceRuntimeReset() throws Exception {
        CLog.i("testRun_deviceRuntimeReset");
        mInstrumentationTest.setShellTimeout(SHELL_TIMEOUT);
        mInstrumentationTest.setTestTimeout(TEST_TIMEOUT);
        mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.TIMEOUT_TEST_METHOD);

        // fork off a thread to do the runtime reset
        Thread resetThread =
                new Thread() {
                    @Override
                    public void run() {
                        // wait for test run to begin
                        try {
                            Thread.sleep(1000);
                            Runtime.getRuntime()
                                    .exec(
                                            String.format(
                                                    "adb -s %s shell stop",
                                                    getDevice().getIDevice().getSerialNumber()));
                            Thread.sleep(500);
                            Runtime.getRuntime()
                                    .exec(
                                            String.format(
                                                    "adb -s %s shell start",
                                                    getDevice().getIDevice().getSerialNumber()));
                        } catch (InterruptedException e) {
                            CLog.w("interrupted");
                        } catch (IOException e) {
                            CLog.w("IOException when rebooting");
                        }
                    }
                };
        resetThread.setName("InstrumentationTestFuncTest#testRun_deviceRuntimeReset");
        resetThread.start();
        try {
            String[] error = new String[1];
            error[0] = null;
            mInstrumentationTest.run(
                    getTestInformation(),
                    new ITestInvocationListener() {
                        @Override
                        public void testRunFailed(String errorMessage) {
                            error[0] = errorMessage;
                        }
                    });
            assertEquals(
                    "Failed to receive adb shell test output within 120000 ms. Test may have "
                            + "timed out, or adb connection to device became unresponsive",
                    error[0]);
        } finally {
            resetThread.join(WAIT_FOR_DEVICE_AVAILABLE);
            RunUtil.getDefault().sleep(5000);
            getDevice().waitForDeviceAvailable();
        }
    }

    /**
     * Test running all the tests with rerun on. At least one method will cause run to stop
     * (currently TIMEOUT_TEST_METHOD and CRASH_TEST_METHOD). Verify that results are recorded for
     * all tests in the suite.
     */
    @Ignore
    @Test
    public void testRun_rerun() throws Exception {
        CLog.i("testRun_rerun");
        // run all tests in class
        RecoveryMode initMode = getDevice().getRecoveryMode();
        getDevice().setRecoveryMode(RecoveryMode.NONE);
        try {
            OptionSetter setter = new OptionSetter(mInstrumentationTest);
            setter.setOptionValue("collect-tests-timeout", Long.toString(SHELL_TIMEOUT));
            mInstrumentationTest.setClassName(TestAppConstants.TESTAPP_CLASS);
            mInstrumentationTest.setRerunMode(true);
            mInstrumentationTest.setShellTimeout(SHELL_TIMEOUT);
            mInstrumentationTest.setTestTimeout(TEST_TIMEOUT);
            CollectingTestListener listener = new CollectingTestListener();
            mInstrumentationTest.run(getTestInformation(), listener);
            assertEquals(TestAppConstants.TOTAL_TEST_CLASS_TESTS, listener.getNumTotalTests());
            assertEquals(
                    TestAppConstants.TOTAL_TEST_CLASS_PASSED_TESTS,
                    listener.getNumTestsInState(TestStatus.PASSED));
        } finally {
            getDevice().setRecoveryMode(initMode);
        }
    }

    /**
     * Test a run that crashes when collecting tests.
     *
     * <p>Expect run to proceed, but be reported as a run failure
     */
    @Ignore
    @Test
    public void testRun_rerunCrash() throws Exception {
        CLog.i("testRun_rerunCrash");
        mInstrumentationTest.setClassName(TestAppConstants.CRASH_ON_INIT_TEST_CLASS);
        mInstrumentationTest.setMethodName(TestAppConstants.CRASH_ON_INIT_TEST_METHOD);
        mInstrumentationTest.setRerunMode(false);
        mInstrumentationTest.setShellTimeout(SHELL_TIMEOUT);
        mInstrumentationTest.setTestTimeout(TEST_TIMEOUT);
        CollectingTestListener listener = new CollectingTestListener();
        mInstrumentationTest.run(getTestInformation(), listener);
        assertEquals(0, listener.getNumTotalTests());
        assertNotNull(listener.getCurrentRunResults());
        assertEquals(TestAppConstants.TESTAPP_PACKAGE, listener.getCurrentRunResults().getName());
        assertTrue(listener.getCurrentRunResults().isRunFailure());
        assertTrue(listener.getCurrentRunResults().isRunComplete());
    }

    /**
     * Test a run that hangs when collecting tests.
     *
     * <p>Expect a run failure to be reported
     */
    @Ignore
    @Test
    public void testRun_rerunHang() throws Exception {
        CLog.i("testRun_rerunHang");
        RecoveryMode initMode = getDevice().getRecoveryMode();
        getDevice().setRecoveryMode(RecoveryMode.NONE);
        try {
            OptionSetter setter = new OptionSetter(mInstrumentationTest);
            setter.setOptionValue("collect-tests-timeout", Long.toString(SHELL_TIMEOUT));
            mInstrumentationTest.setClassName(TestAppConstants.HANG_ON_INIT_TEST_CLASS);
            mInstrumentationTest.setRerunMode(false);
            mInstrumentationTest.setShellTimeout(SHELL_TIMEOUT);
            mInstrumentationTest.setTestTimeout(TEST_TIMEOUT);
            CollectingTestListener listener = new CollectingTestListener();
            mInstrumentationTest.run(getTestInformation(), listener);
            assertEquals(0, listener.getNumTotalTests());
            assertEquals(
                    TestAppConstants.TESTAPP_PACKAGE, listener.getCurrentRunResults().getName());
            assertTrue(listener.getCurrentRunResults().isRunFailure());
            assertTrue(listener.getCurrentRunResults().isRunComplete());
        } finally {
            getDevice().setRecoveryMode(initMode);
        }
    }
}
