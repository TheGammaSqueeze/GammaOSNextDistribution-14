/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.adpf.cts;

import static android.adpf.common.ADPFHintSessionConstants.BASELINE_KEY;
import static android.adpf.common.ADPFHintSessionConstants.ERROR_MARGIN;
import static android.adpf.common.ADPFHintSessionConstants.HEAVY_LOAD_KEY;
import static android.adpf.common.ADPFHintSessionConstants.TRANSITION_LOAD_KEY;
import static android.adpf.common.ADPFHintSessionConstants.IS_HINT_SESSION_SUPPORTED_KEY;
import static android.adpf.common.ADPFHintSessionConstants.LIGHT_LOAD_KEY;
import static android.adpf.common.ADPFHintSessionConstants.MINIMUM_VALID_SDK;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestMetrics;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;


import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Test to check the ADPF hint session implementation.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class ADPFHintSessionHostJUnit4Test extends BaseHostJUnit4Test {
    private static final String PACKAGE_APK = "CtsADPFHintSessionDeviceApp.apk";
    private static final String TEST_PACKAGE_NAME = "android.adpf.hintsession.app";
    private static final String ADPF_DEVICE_TEST_CLASS = "ADPFHintSessionDeviceTest";

    @Rule
    public TestMetrics mMetrics = new TestMetrics();
    private ITestDevice mDevice;

    @Before
    public void setUp() throws Exception {
        mDevice = getDevice();
        final String[] abis = getProperty("ro.product.cpu.abilist").split(",");
        boolean supported = false;
        for (String abi : abis) {
            if (abi.toLowerCase().startsWith("arm")) {
                supported = true;
                break;
            }
        }
        assumeTrue("Test skipped as no ARM based ABI is supported", supported);
    }

    private String getProperty(String prop) throws Exception {
        return mDevice.executeShellCommand("getprop " + prop).replace("\n", "");
    }

    private void checkMinSdkVersion() throws Exception {
        String sdkAsString = getProperty("ro.build.version.sdk");
        int sdk = Integer.parseInt(sdkAsString);
        assumeTrue("Test requires sdk >= " + MINIMUM_VALID_SDK
                        + " while test device has sdk = " + sdk,
                sdk >= MINIMUM_VALID_SDK);
    }

    private static long getMedian(long[] numbers) {
        long[] copy = numbers.clone();
        Arrays.sort(copy);

        return copy[copy.length / 2];
    }

    private static boolean isRoughlyEqual(long lhs, long rhs) {
        return Math.abs(lhs - rhs) < ERROR_MARGIN * (lhs + rhs) / 2.0;
    }

    private static boolean isLess(long lhs, long rhs) {
        return lhs < rhs && !isRoughlyEqual(lhs, rhs);
    }

    private static boolean isGreater(long lhs, long rhs) {
        return isLess(rhs, lhs);
    }

    private static final String TAG = android.adpf.cts
            .ADPFHintSessionHostJUnit4Test.class.getSimpleName();



    /**
     * This tests the ADPF hint session app behavior under various target states,
     * to validate that the load matches what would be expected for a system with
     * those demands. Higher-load tests with lower targets should have smaller durations,
     * because they require more resources to complete the same work in less time.
     * Conversely, lower-load tests with longer targets should have larger durations,
     * since fewer resources are needed to complete their work by the target time.
     */
    @Test
    public void testAdpfHintSession() throws Exception {
        checkMinSdkVersion();
        installPackage(PACKAGE_APK);
        // wake up and unlock the device, otherwise the device test may crash on drawing GL
        mDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        mDevice.executeShellCommand("input keyevent KEYCODE_MENU");
        mDevice.executeShellCommand("wm dismiss-keyguard");
        runDeviceTests(TEST_PACKAGE_NAME, TEST_PACKAGE_NAME + "." + ADPF_DEVICE_TEST_CLASS);
        final TestDescription testDesc = new TestDescription(
                    TEST_PACKAGE_NAME + "." + ADPF_DEVICE_TEST_CLASS, "testAdpfHintSession"
        );
        final TestRunResult runResult = getLastDeviceRunResults();
        final TestResult result = runResult.getTestResults().get(testDesc);
        assertNotNull("Result object was null.", result);
        assertNotNull("No metrics were returned.", result.getMetrics());
        String isSupportedStr = result.getMetrics().get(IS_HINT_SESSION_SUPPORTED_KEY);
        assertNotNull("ADPF support was not specified.", isSupportedStr);

        if (!Boolean.parseBoolean(isSupportedStr)) {
            Log.i(TAG, "ADPF is not supported on this device, skipping test");
            return;
        }

        if (result.getStatus() != TestStatus.PASSED) {
            String message = result.getFailure().getErrorMessage();
            fail("Test failed" + (message == null ? "!" : " with error: " + message));
        }

        Map<String, String> metrics = result.getMetrics();
        HashMap<String, Long> testMedians = new HashMap<>();

        for (Map.Entry<String, String> entry : metrics.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith("_durations")) {
                String testName = key.substring(0, key.lastIndexOf("_"));
                long[] numbers = Arrays.stream(entry.getValue().split(","))
                        .mapToLong(Long::parseLong).toArray();
                Long median = getMedian(numbers);
                testMedians.put(testName, median);
                Log.e(TAG, "Median of " + testName + " is: " + median.toString());
                Log.e(TAG, "Target of " + testName + " is: " + metrics.get(testName + "_target"));
            }
        }

        assertTrue("Baseline test was not run!", testMedians.containsKey(BASELINE_KEY));
        long baseline = testMedians.get(BASELINE_KEY);
        // This test validates that the light load is not any worse than baseline
        if (testMedians.containsKey(LIGHT_LOAD_KEY)) {
            assertTrue("Low-load case ran faster than baseline!",
                    !isLess(testMedians.get(LIGHT_LOAD_KEY), baseline));
        }
        // This test validates that the heavy load case runs faster than baseline
        if (testMedians.containsKey(HEAVY_LOAD_KEY)) {
            assertTrue("High-load case was not faster than baseline!",
                    isLess(testMedians.get(HEAVY_LOAD_KEY), baseline));
        }
        /**
         * This test validates that the heavy load case runs faster than the
         * light-load case when the workload needs to ramp up, and vice-versa,
         * that the light-load case runs slower than the heavy-load case when the
         * workload ramps back down.
         */
        if (testMedians.containsKey(TRANSITION_LOAD_KEY + "_1")) {
            assertTrue("High-load case was not faster than previous low-load case!",
                    isGreater(testMedians.get(TRANSITION_LOAD_KEY + "_1"),
                              testMedians.get(TRANSITION_LOAD_KEY + "_2")));
            assertTrue("Low-load case was not slower than previous high-load case!",
                    isLess(testMedians.get(TRANSITION_LOAD_KEY + "_2"),
                           testMedians.get(TRANSITION_LOAD_KEY + "_3")));
        }
    }

}
