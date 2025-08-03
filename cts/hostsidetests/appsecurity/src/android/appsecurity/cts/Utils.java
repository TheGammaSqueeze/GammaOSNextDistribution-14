/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.appsecurity.cts;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.util.RunUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class Utils {
    private static final String LOG_TAG = Utils.class.getSimpleName();

    public static final int USER_SYSTEM = 0;

    static final String PKG = "com.android.cts.splitapp";
    static final String CLASS = PKG + ".SplitAppTest";

    static final String APK = "CtsSplitApp.apk";

    static final String APK_mdpi = "CtsSplitApp_mdpi-v4.apk";
    static final String APK_hdpi = "CtsSplitApp_hdpi-v4.apk";
    static final String APK_xhdpi = "CtsSplitApp_xhdpi-v4.apk";
    static final String APK_xxhdpi = "CtsSplitApp_xxhdpi-v4.apk";

    static final String APK_x86 = "CtsSplitApp_x86.apk";
    static final String APK_x86_64 = "CtsSplitApp_x86_64.apk";
    static final String APK_armeabi_v7a = "CtsSplitApp_armeabi-v7a.apk";
    static final String APK_armeabi = "CtsSplitApp_armeabi.apk";
    static final String APK_arm64_v8a = "CtsSplitApp_arm64-v8a.apk";
    static final String APK_mips64 = "CtsSplitApp_mips64.apk";
    static final String APK_mips = "CtsSplitApp_mips.apk";

    static final HashMap<String, String> ABI_TO_APK = new HashMap<>();

    static {
        ABI_TO_APK.put("x86", APK_x86);
        ABI_TO_APK.put("x86_64", APK_x86_64);
        ABI_TO_APK.put("armeabi-v7a", APK_armeabi_v7a);
        ABI_TO_APK.put("armeabi", APK_armeabi);
        ABI_TO_APK.put("arm64-v8a", APK_arm64_v8a);
        ABI_TO_APK.put("mips64", APK_mips64);
        ABI_TO_APK.put("mips", APK_mips);
    }

    public static void runDeviceTestsAsCurrentUser(ITestDevice device, String packageName,
            String testClassName, String testMethodName) throws DeviceNotAvailableException {
        runDeviceTests(device, packageName, testClassName, testMethodName, device.getCurrentUser(),
                null);
    }

    public static void runDeviceTestsAsCurrentUser(ITestDevice device, String packageName,
            String testClassName, String testMethodName, Map<String, String> testArgs)
                    throws DeviceNotAvailableException {
        runDeviceTests(device, packageName, testClassName, testMethodName, device.getCurrentUser(),
                testArgs);
    }

    public static void runDeviceTests(ITestDevice device, String packageName, String testClassName,
            String testMethodName) throws DeviceNotAvailableException {
        runDeviceTests(device, packageName, testClassName, testMethodName, USER_SYSTEM, null);
    }

    public static void runDeviceTests(ITestDevice device, String packageName, String testClassName,
            String testMethodName, Map<String, String> testArgs)
                    throws DeviceNotAvailableException {
        runDeviceTests(device, packageName, testClassName, testMethodName, USER_SYSTEM, testArgs);
    }

    public static void runDeviceTests(ITestDevice device, String packageName, String testClassName,
            String testMethodName, int userId) throws DeviceNotAvailableException {
        runDeviceTests(device, packageName, testClassName, testMethodName, userId, null);
    }

    public static void runDeviceTests(ITestDevice device, String packageName, String testClassName,
            String testMethodName, int userId, Map<String, String> testArgs)
                    throws DeviceNotAvailableException {
        // 60 min timeout per test by default
        runDeviceTests(device, packageName, testClassName, testMethodName, userId, testArgs,
                60L, TimeUnit.MINUTES);
    }

    public static void runDeviceTests(ITestDevice device, String packageName, String testClassName,
            String testMethodName, int userId, Map<String, String> testArgs, long timeout,
            TimeUnit unit)
                    throws DeviceNotAvailableException {
        if (testClassName != null && testClassName.startsWith(".")) {
            testClassName = packageName + testClassName;
        }
        RemoteAndroidTestRunner testRunner = new RemoteAndroidTestRunner(packageName,
                "androidx.test.runner.AndroidJUnitRunner", device.getIDevice());
        // timeout_msec is the timeout per test for instrumentation
        long testTimeoutMs = unit.toMillis(timeout);
        testRunner.addInstrumentationArg("timeout_msec", Long.toString(testTimeoutMs));
        // Similar logic as InstrumentationTest to ensure on host-side level that no-hanging can happen
        long maxTimeToOutputMs = testTimeoutMs + testTimeoutMs / 10;
        testRunner.setMaxTimeToOutputResponse(maxTimeToOutputMs, TimeUnit.MILLISECONDS);

        if (testClassName != null && testMethodName != null) {
            testRunner.setMethodName(testClassName, testMethodName);
        } else if (testClassName != null) {
            testRunner.setClassName(testClassName);
        }

        if (testArgs != null && testArgs.size() > 0) {
            for (String name : testArgs.keySet()) {
                final String value = testArgs.get(name);
                testRunner.addInstrumentationArg(name, value);
            }
        }
        final CollectingTestListener listener = new CollectingTestListener();
        device.runInstrumentationTestsAsUser(testRunner, userId, listener);

        final TestRunResult result = listener.getCurrentRunResults();
        if (result.isRunFailure()) {
            throw new AssertionError("Failed to successfully run device tests for "
                    + result.getName() + ": " + result.getRunFailureMessage());
        }
        if (result.getNumTests() == 0) {
            throw new AssertionError("No tests were run on the device");
        }
        if (result.hasFailedTests()) {
            // build a meaningful error message
            StringBuilder errorBuilder = new StringBuilder("on-device tests failed:\n");
            for (Map.Entry<TestDescription, TestResult> resultEntry :
                result.getTestResults().entrySet()) {
                if (!resultEntry.getValue().getStatus().equals(TestStatus.PASSED)) {
                    errorBuilder.append(resultEntry.getKey().toString());
                    errorBuilder.append(":\n");
                    errorBuilder.append(resultEntry.getValue().getStackTrace());
                }
            }
            throw new AssertionError(errorBuilder.toString());
        }
    }

    /**
     * Prepare and return a single user relevant for testing.
     */
    public static int[] prepareSingleUser(ITestDevice device)
            throws DeviceNotAvailableException {
        return prepareMultipleUsers(device, 1);
    }

    /**
     * Prepare and return two users relevant for testing.
     */
    public static int[] prepareMultipleUsers(ITestDevice device)
            throws DeviceNotAvailableException {
        return prepareMultipleUsers(device, 2);
    }

    /**
     * Prepare and return multiple users relevant for testing.
     */
    public static int[] prepareMultipleUsers(ITestDevice device, int maxUsers)
            throws DeviceNotAvailableException {
        final int[] userIds = getAllUsers(device);
        int currentUserId = device.getCurrentUser();
        for (int i = 1; i < userIds.length; i++) {
            if (i < maxUsers) {
                device.startUser(userIds[i], true);
            } else if (userIds[i] != currentUserId) {
                device.stopUser(userIds[i], true, true);
            }
        }
        if (userIds.length > maxUsers) {
            return Arrays.copyOf(userIds, maxUsers);
        } else {
            return userIds;
        }
    }

    public static int[] getAllUsers(ITestDevice device)
            throws DeviceNotAvailableException {
        Integer primary = device.getPrimaryUserId();
        if (device.isHeadlessSystemUserMode()
                && primary == USER_SYSTEM
                && !device.canSwitchToHeadlessSystemUser()) {
            primary = device.getMainUserId();
        }
        if (primary == null) {
            primary = USER_SYSTEM;
        }
        int[] users = new int[] { primary };
        for (Integer user : device.listUsers()) {
            if ((user != USER_SYSTEM) && !Objects.equals(user, primary)) {
                users = Arrays.copyOf(users, users.length + 1);
                users[users.length - 1] = user;
            }
        }
        return users;
    }

    public static void waitForBootCompleted(ITestDevice device) throws Exception {
        for (int i = 0; i < 45; i++) {
            if (isBootCompleted(device)) {
                Log.d(LOG_TAG, "Yay, system is ready!");
                // or is it really ready?
                // guard against potential USB mode switch weirdness at boot
                RunUtil.getDefault().sleep(10 * 1000);
                return;
            }
            Log.d(LOG_TAG, "Waiting for system ready...");
            // For low performance devices
            RunUtil.getDefault().sleep(10*1000);
        }
        throw new AssertionError("System failed to become ready!");
    }

    private static boolean isBootCompleted(ITestDevice device) throws Exception {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        try {
            device.getIDevice().executeShellCommand("getprop sys.boot_completed", receiver);
        } catch (AdbCommandRejectedException e) {
            // do nothing: device might be temporarily disconnected
            Log.d(LOG_TAG, "Ignored AdbCommandRejectedException while `getprop sys.boot_completed`");
        }
        String output = receiver.getOutput();
        if (output != null) {
            output = output.trim();
        }
        return "1".equals(output);
    }

}
