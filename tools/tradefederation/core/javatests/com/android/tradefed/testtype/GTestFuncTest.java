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

import static org.mockito.Mockito.verify;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.HashMap;

/** Functional tests for {@link InstrumentationTest}. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class GTestFuncTest implements IDeviceTest {

    private GTest mGTest = null;
    @Mock ITestInvocationListener mMockListener;
    private TestInformation mTestInfo;
    private ITestDevice mDevice;

    // Native test app constants
    public static final String NATIVE_TESTAPP_GTEST_CLASSNAME = "TradeFedNativeAppTest";
    public static final String NATIVE_TESTAPP_MODULE_NAME = "tfnativetests";
    public static final String NATIVE_TESTAPP_GTEST_CRASH_METHOD = "testNullPointerCrash";
    public static final String NATIVE_TESTAPP_GTEST_TIMEOUT_METHOD = "testInfiniteLoop";
    public static final int NATIVE_TESTAPP_TOTAL_TESTS = 2;

    private static final String NATIVE_SAMPLETEST_MODULE_NAME = "tfnativetestsamplelibtests";

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

        mDevice.waitForDeviceAvailable();

        mGTest = new GTest();
        mGTest.setDevice(getDevice());

        mTestInfo = TestInformation.newBuilder().build();
    }

    /** Test normal run of the sample native test project (7 tests, one of which is a failure). */
    @SuppressWarnings("unchecked")
    @Test
    public void testRun() throws DeviceNotAvailableException {
        HashMap<String, Metric> emptyMap = new HashMap<>();
        mGTest.setModuleName(NATIVE_SAMPLETEST_MODULE_NAME);
        CLog.i("testRun");

        String[][] allTests = {
            {"FibonacciTest", "testRecursive_One"},
            {"FibonacciTest", "testRecursive_Ten"},
            {"FibonacciTest", "testIterative_Ten"},
            {"CelsiusToFahrenheitTest", "testNegative"},
            {"CelsiusToFahrenheitTest", "testPositive"},
            {"FahrenheitToCelsiusTest", "testExactFail"},
            {"FahrenheitToCelsiusTest", "testApproximatePass"},
        };

        mGTest.run(mTestInfo, mMockListener);

        verify(mMockListener).testRunStarted(NATIVE_SAMPLETEST_MODULE_NAME, 7);

        for (String[] test : allTests) {
            String testClass = test[0];
            String testName = test[1];
            TestDescription id = new TestDescription(testClass, testName);

            verify(mMockListener).testStarted(id);
            if (testName.endsWith("Fail")) {
                verify(mMockListener).testFailed(Mockito.eq(id), Mockito.isA(String.class));
            }
            verify(mMockListener).testEnded(id, emptyMap);
        }

        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), (HashMap<String, Metric>) Mockito.any());
    }

    /**
     * Helper to run tests in the Native Test App.
     *
     * @param testId the {%link TestDescription} of the test to run
     */
    private void doNativeTestAppRunSingleTestFailure(TestDescription testId) {
        mGTest.setModuleName(NATIVE_TESTAPP_MODULE_NAME);
    }

    /**
     * Helper to run tests in the Native Test App.
     *
     * @param testId the {%link TestDescription} of the test to run
     */
    private void verifyNativeTestAppRunSingleTestFailure(TestDescription testId) {
        HashMap<String, Metric> emptyMap = new HashMap<>();
        verify(mMockListener).testRunStarted(NATIVE_TESTAPP_MODULE_NAME, 1);
        verify(mMockListener).testStarted(Mockito.eq(testId));
        verify(mMockListener).testFailed(Mockito.eq(testId), Mockito.isA(String.class));
        verify(mMockListener).testEnded(Mockito.eq(testId), Mockito.eq(emptyMap));
        verify(mMockListener).testRunFailed(Mockito.<String>anyObject());
        verify(mMockListener)
                .testRunEnded(Mockito.anyLong(), Mockito.<HashMap<String, Metric>>anyObject());
    }

    /** Test run scenario where test process crashes while trying to access NULL ptr. */
    @Test
    public void testRun_testCrash() throws DeviceNotAvailableException {
        CLog.i("testRun_testCrash");
        TestDescription testId =
                new TestDescription(
                        NATIVE_TESTAPP_GTEST_CLASSNAME, NATIVE_TESTAPP_GTEST_CRASH_METHOD);
        doNativeTestAppRunSingleTestFailure(testId);
        // Set GTest to only run the crash test
        mGTest.addIncludeFilter(NATIVE_TESTAPP_GTEST_CRASH_METHOD);

        mGTest.run(mTestInfo, mMockListener);

        verifyNativeTestAppRunSingleTestFailure(testId);
    }

    /** Test run scenario where device reboots during test run. */
    @Test
    public void testRun_deviceReboot() throws Exception {
        CLog.i("testRun_deviceReboot");

        TestDescription testId =
                new TestDescription(
                        NATIVE_TESTAPP_GTEST_CLASSNAME, NATIVE_TESTAPP_GTEST_TIMEOUT_METHOD);

        doNativeTestAppRunSingleTestFailure(testId);

        // Set GTest to only run the crash test
        mGTest.addIncludeFilter(NATIVE_TESTAPP_GTEST_TIMEOUT_METHOD);

        // fork off a thread to do the reboot
        Thread rebootThread =
                new Thread() {
                    @Override
                    public void run() {
                        // wait for test run to begin
                        try {
                            Thread.sleep(500);
                            Runtime.getRuntime()
                                    .exec(
                                            String.format(
                                                    "adb -s %s reboot",
                                                    getDevice().getIDevice().getSerialNumber()));
                        } catch (InterruptedException e) {
                            CLog.w("interrupted");
                        } catch (IOException e) {
                            CLog.w("IOException when rebooting");
                        }
                    }
                };
        rebootThread.start();
        mGTest.run(mTestInfo, mMockListener);
        getDevice().waitForDeviceAvailable();
        verifyNativeTestAppRunSingleTestFailure(testId);
    }

    /** Test run scenario where test timesout. */
    @Test
    public void testRun_timeout() throws Exception {
        CLog.i("testRun_timeout");

        TestDescription testId =
                new TestDescription(
                        NATIVE_TESTAPP_GTEST_CLASSNAME, NATIVE_TESTAPP_GTEST_TIMEOUT_METHOD);
        // set max time to a small amount to reduce this test's execution time
        mGTest.setMaxTestTimeMs(100);
        doNativeTestAppRunSingleTestFailure(testId);

        // Set GTest to only run the timeout test
        mGTest.addIncludeFilter(NATIVE_TESTAPP_GTEST_TIMEOUT_METHOD);

        mGTest.run(mTestInfo, mMockListener);
        getDevice().waitForDeviceAvailable();
        verifyNativeTestAppRunSingleTestFailure(testId);
    }
}
