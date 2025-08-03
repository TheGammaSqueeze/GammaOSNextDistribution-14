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

package android.cts.statsdatom.lib;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.service.battery.BatteryServiceDumpProto;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.CollectingByteOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ResultForwarder;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.util.Pair;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;

import java.io.FileNotFoundException;
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Contains utility functions for interacting with the device.
 * Largely copied from incident's ProtoDumpTestCase.
 */
public final class DeviceUtils {
    public static final String STATSD_ATOM_TEST_APK = "CtsStatsdAtomApp.apk";
    public static final String STATSD_ATOM_TEST_PKG = "com.android.server.cts.device.statsdatom";

    private static final String TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner";

    private static final String KEY_ACTION = "action";

    // feature names
    public static final String FEATURE_WATCH = "android.hardware.type.watch";

    public static final String DUMP_BATTERY_CMD = "dumpsys battery";

    /**
     * Runs device side tests.
     *
     * @param device Can be retrieved by running getDevice() in a class that extends DeviceTestCase
     * @param pkgName Test package name, such as "com.android.server.cts.statsdatom"
     * @param testClassName Test class name which can either be a fully qualified name or "." + a
     *     class name; if null, all test in the package will be run
     * @param testMethodName Test method name; if null, all tests in class or package will be run
     * @return {@link TestRunResult} of this invocation
     * @throws DeviceNotAvailableException
     */
    public static @Nonnull TestRunResult runDeviceTests(ITestDevice device, String pkgName,
            @Nullable String testClassName, @Nullable String testMethodName)
            throws DeviceNotAvailableException {
        return internalRunDeviceTests(device, pkgName, testClassName, testMethodName, null);
    }

    /**
     * Runs device side tests.
     *
     * @param device Can be retrieved by running getDevice() in a class that extends DeviceTestCase
     * @param pkgName Test package name, such as "com.android.server.cts.statsdatom"
     * @param testClassName Test class name which can either be a fully qualified name or "." + a
     *     class name; if null, all test in the package will be run
     * @param testMethodName Test method name; if null, all tests in class or package will be run
     * @param listener Listener for test results from the test invocation.
     * @return {@link TestRunResult} of this invocation
     * @throws DeviceNotAvailableException
     */
    public static @Nonnull TestRunResult runDeviceTests(ITestDevice device, String pkgName,
            @Nullable String testClassName, @Nullable String testMethodName,
            ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        return internalRunDeviceTests(device, pkgName, testClassName, testMethodName, listener);
    }

    private static @Nonnull TestRunResult internalRunDeviceTests(ITestDevice device, String pkgName,
            @Nullable String testClassName, @Nullable String testMethodName,
            @Nullable ITestInvocationListener otherListener)
            throws DeviceNotAvailableException {
        if (testClassName != null && testClassName.startsWith(".")) {
            testClassName = pkgName + testClassName;
        }

        RemoteAndroidTestRunner testRunner = new RemoteAndroidTestRunner(
                pkgName, TEST_RUNNER, device.getIDevice());
        if (testClassName != null && testMethodName != null) {
            testRunner.setMethodName(testClassName, testMethodName);
        } else if (testClassName != null) {
            testRunner.setClassName(testClassName);
        }

        CollectingTestListener collectingTestListener = new CollectingTestListener();
        ITestInvocationListener combinedLister;
        if (otherListener != null) {
            combinedLister = new ResultForwarder(otherListener, collectingTestListener);
        } else {
            combinedLister = collectingTestListener;
        }

        assertThat(device.runInstrumentationTests(testRunner, combinedLister)).isTrue();

        final TestRunResult result = collectingTestListener.getCurrentRunResults();
        if (result.isRunFailure()) {
            throw new Error("Failed to successfully run device tests for "
                    + result.getName() + ": " + result.getRunFailureMessage());
        }
        if (result.getNumTests() == 0) {
            throw new Error("No tests were run on the device");
        }
        if (result.hasFailedTests()) {
            StringBuilder errorBuilder = new StringBuilder("On-device tests failed:\n");
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
        return result;
    }

    /**
     * Runs device side tests from the com.android.server.cts.device.statsdatom package.
     */
    public static @Nonnull TestRunResult runDeviceTestsOnStatsdApp(ITestDevice device,
            @Nullable String testClassName, @Nullable String testMethodName)
            throws DeviceNotAvailableException {
        return runDeviceTests(device, STATSD_ATOM_TEST_PKG, testClassName, testMethodName);
    }

    /**
     * Install the statsdatom CTS app to the device.
     */
    public static void installStatsdTestApp(ITestDevice device, IBuildInfo ctsBuildInfo)
            throws FileNotFoundException, DeviceNotAvailableException {
        installTestApp(device, STATSD_ATOM_TEST_APK, STATSD_ATOM_TEST_PKG, ctsBuildInfo);
    }

    /**
     * Install a test app to the device.
     */
    public static void installTestApp(ITestDevice device, String apkName, String pkgName,
            IBuildInfo ctsBuildInfo) throws FileNotFoundException, DeviceNotAvailableException {
        CLog.d("Installing app " + apkName);
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(ctsBuildInfo);
        final String result = device.installPackage(
                buildHelper.getTestFile(apkName), /*reinstall=*/true, /*grantPermissions=*/true);
        assertWithMessage("Failed to install " + apkName + ": " + result).that(result).isNull();
        allowBackgroundServices(device, pkgName);
    }

    /**
     * Required to successfully start a background service from adb, starting in O.
     */
    private static void allowBackgroundServices(ITestDevice device, String pkgName)
            throws DeviceNotAvailableException {
        String cmd = "cmd deviceidle tempwhitelist " + pkgName;
        device.executeShellCommand(cmd);
    }

    /**
     * Uninstall the statsdatom CTS app from the device.
     */
    public static void uninstallStatsdTestApp(ITestDevice device) throws Exception {
        uninstallTestApp(device, STATSD_ATOM_TEST_PKG);
    }

    /**
     * Uninstall the test app from the device.
     */
    public static void uninstallTestApp(ITestDevice device, String pkgName) throws Exception {
        device.uninstallPackage(pkgName);
    }

    /**
     * Run an adb shell command on device and parse the results as a proto of a given type.
     *
     * @param device Device to run cmd on
     * @param parser Protobuf parser object, which can be retrieved by running MyProto.parser()
     * @param extensionRegistry ExtensionRegistry containing extensions that should be parsed
     * @param cmd The adb shell command to run (e.g. "cmd stats update config")
     *
     * @throws DeviceNotAvailableException
     * @throws InvalidProtocolBufferException Occurs if there was an error parsing the proto. Note
     *     that a 0 length buffer is not necessarily an error.
     * @return Proto of specified type
     */
    public static <T extends MessageLite> T getShellCommandOutput(@Nonnull ITestDevice device,
            Parser<T> parser, ExtensionRegistry extensionRegistry, String cmd)
            throws DeviceNotAvailableException, InvalidProtocolBufferException {
        final CollectingByteOutputReceiver receiver = new CollectingByteOutputReceiver();
        device.executeShellCommand(cmd, receiver);
        try {
            return parser.parseFrom(receiver.getOutput(), extensionRegistry);
        } catch (Exception ex) {
            CLog.d("Error parsing " + parser.getClass().getCanonicalName() + " for cmd " + cmd);
            throw ex;
        }
    }

    public static <T extends MessageLite> T getShellCommandOutput(
            @Nonnull ITestDevice device, Parser<T> parser, String cmd)
            throws DeviceNotAvailableException, InvalidProtocolBufferException {
        return getShellCommandOutput(device, parser, ExtensionRegistry.getEmptyRegistry(), cmd);
    }

    /**
     * Returns the UID of the host, which should always either be AID_SHELL (2000) or AID_ROOT (0).
     */
    public static int getHostUid(ITestDevice device) throws DeviceNotAvailableException {
        String uidString = "";
        try {
            uidString = device.executeShellCommand("id -u");
            return Integer.parseInt(uidString.trim());
        } catch (NumberFormatException ex) {
            CLog.e("Failed to get host's uid via shell command. Found " + uidString);
            // Fall back to alternative method...
            if (device.isAdbRoot()) {
                return 0;
            } else {
                return 2000; // SHELL
            }
        }
    }

    /**
     * Returns the UID of the statsdatom CTS test app.
     */
    public static int getStatsdTestAppUid(ITestDevice device) throws DeviceNotAvailableException {
        return getAppUid(device, STATSD_ATOM_TEST_PKG);
    }

    /**
     * Returns the UID of the test app for the current user.
     */
    public static int getAppUid(ITestDevice device, String pkgName)
            throws DeviceNotAvailableException {
        int currentUser = device.getCurrentUser();
        return getAppUidForUser(device, pkgName, currentUser);
    }

    /**
     * Returns the UID of the test app for the given user.
     */
    public static int getAppUidForUser(ITestDevice device, String pkgName, int userId)
            throws DeviceNotAvailableException {
        String uidLine = device.executeShellCommand("cmd package list packages -U --user "
                + userId + " " + pkgName);
        String[] uidLineArr = uidLine.split(":");

        // Package uid is located at index 2.
        assertThat(uidLineArr.length).isGreaterThan(2);
        int appUid = Integer.parseInt(uidLineArr[2].trim());
        assertThat(appUid).isGreaterThan(10000);
        return appUid;
    }

    /**
     * Determines if the device has the given features.
     *
     * @param feature name of the feature (e.g. "android.hardware.bluetooth")
     */
    public static boolean hasFeature(ITestDevice device, String feature) throws Exception {
        final String features = device.executeShellCommand("pm list features");
        StringTokenizer featureToken = new StringTokenizer(features, "\n");

        while(featureToken.hasMoreTokens()) {
            if (("feature:" + feature).equals(featureToken.nextToken())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Runs an activity in a particular app.
     */
    public static void runActivity(ITestDevice device, String pkgName, String activity,
            @Nullable String actionKey, @Nullable String actionValue) throws Exception {
        runActivity(device, pkgName, activity, actionKey, actionValue,
                AtomTestUtils.WAIT_TIME_LONG);
    }

    /**
     * Runs an activity in a particular app for a certain period of time.
     *
     * @param pkgName name of package that contains the Activity
     * @param activity name of the Activity class
     * @param actionKey key of extra data that is passed to the Activity via an Intent
     * @param actionValue value of extra data that is passed to the Activity via an Intent
     * @param waitTimeMs duration that the activity runs for
     */
    public static void runActivity(ITestDevice device, String pkgName, String activity,
            @Nullable String actionKey, @Nullable String actionValue, long waitTimeMs)
            throws Exception {
        try (AutoCloseable a = withActivity(device, pkgName, activity, actionKey, actionValue)) {
            RunUtil.getDefault().sleep(waitTimeMs);
        }
    }

    /**
     * Starts the specified activity and returns an {@link AutoCloseable} that stops the activity
     * when closed.
     *
     * <p>Example usage:
     * <pre>
     *     try (AutoClosable a = withActivity("activity", "action", "action-value")) {
     *         doStuff();
     *     }
     * </pre>
     */
    public static AutoCloseable withActivity(ITestDevice device, String pkgName, String activity,
            @Nullable String actionKey, @Nullable String actionValue) throws Exception {
        String intentString;
        if (actionKey != null && actionValue != null) {
            intentString = actionKey + " " + actionValue;
        } else {
            intentString = null;
        }

        String cmd = "am start -n " + pkgName + "/." + activity;
        if (intentString != null) {
            cmd += " -e " + intentString;
        }
        device.executeShellCommand(cmd);

        return () -> {
            device.executeShellCommand("am force-stop " + pkgName);
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        };
    }

    public static void setChargingState(ITestDevice device, int state) throws Exception {
        device.executeShellCommand("cmd battery set status " + state);
    }

    public static void unplugDevice(ITestDevice device) throws Exception {
        // On batteryless devices on Android P or above, the 'unplug' command
        // alone does not simulate the really unplugged state.
        //
        // This is because charging state is left as "unknown". Unless a valid
        // state like 3 = BatteryManager.BATTERY_STATUS_DISCHARGING is set,
        // framework does not consider the device as running on battery.
        setChargingState(device, 3);
        device.executeShellCommand("cmd battery unplug");
    }

    public static void plugInAc(ITestDevice device) throws Exception {
        device.executeShellCommand("cmd battery set ac 1");
    }

    public static void turnScreenOn(ITestDevice device) throws Exception {
        device.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        device.executeShellCommand("wm dismiss-keyguard");
    }

    public static void turnScreenOff(ITestDevice device) throws Exception {
        device.executeShellCommand("input keyevent KEYCODE_SLEEP");
    }

    public static void turnBatteryStatsAutoResetOn(ITestDevice device) throws Exception {
        device.executeShellCommand("dumpsys batterystats enable no-auto-reset");
    }

    public static void turnBatteryStatsAutoResetOff(ITestDevice device) throws Exception {
        device.executeShellCommand("dumpsys batterystats enable no-auto-reset");
    }

    public static void flushBatteryStatsHandlers(ITestDevice device) throws Exception {
        // Dumping batterystats will flush everything in the batterystats handler threads.
        device.executeShellCommand("dumpsys batterystats");
    }

    public static boolean hasBattery(ITestDevice device) throws Exception {
        try {
            BatteryServiceDumpProto batteryProto = getShellCommandOutput(device, BatteryServiceDumpProto.parser(),
                    String.join(" ", DUMP_BATTERY_CMD, "--proto"));
            LogUtil.CLog.d("Got battery service dump:\n " + batteryProto.toString());
            return batteryProto.getIsPresent();
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            LogUtil.CLog.e("Failed to dump batteryservice proto");
            throw (e);
        }
    }

    public static void resetBatteryStatus(ITestDevice device) throws Exception {
        device.executeShellCommand("cmd battery reset");
    }

    public static String getProperty(ITestDevice device, String prop) throws Exception {
        return device.executeShellCommand("getprop " + prop).replace("\n", "");
    }

    public static String getDeviceConfigFeature(ITestDevice device, String namespace,
            String key) throws Exception {
        return device.executeShellCommand(
                "device_config get " + namespace + " " + key).replace("\n", "");
    }

    public static String putDeviceConfigFeature(ITestDevice device, String namespace,
            String key, String value) throws Exception {
        return device.executeShellCommand(
                "device_config put " + namespace + " " + key + " " + value).replace("\n", "");
    }

    public static String deleteDeviceConfigFeature(ITestDevice device, String namespace,
            String key) throws Exception {
        return device.executeShellCommand(
                "device_config delete " + namespace + " " + key).replace("\n", "");
    }

    public static boolean isDebuggable(ITestDevice device) throws Exception {
        return Integer.parseInt(getProperty(device, "ro.debuggable")) == 1;
    }

    public static boolean checkDeviceFor(ITestDevice device, String methodName) throws Exception {
        try {
            runDeviceTestsOnStatsdApp(device, ".Checkers", methodName);
            // Test passes, meaning that the answer is true.
            LogUtil.CLog.d(methodName + "() indicates true.");
            return true;
        } catch (AssertionError e) {
            // Method is designed to fail if the answer is false.
            LogUtil.CLog.d(methodName + "() indicates false.");
            return false;
        }
    }

    /** Make the test app standby-active so it can run syncs and jobs immediately. */
    public static void allowImmediateSyncs(ITestDevice device) throws Exception {
        device.executeShellCommand("am set-standby-bucket "
                + DeviceUtils.STATSD_ATOM_TEST_PKG + " active");
    }

    /**
     * Runs a (background) service to perform the given action.
     * @param actionValue the action code constants indicating the desired action to perform.
     */
    public static void executeBackgroundService(ITestDevice device, String actionValue)
            throws Exception {
        executeServiceAction(device, "StatsdCtsBackgroundService", actionValue);
    }

    /**
     * Runs the specified statsd package service to perform the given action.
     * @param actionValue the action code constants indicating the desired action to perform.
     */
    public static void executeServiceAction(ITestDevice device, String service, String actionValue)
            throws Exception {
        allowBackgroundServices(device);
        device.executeShellCommand(String.format(
                "am startservice -n '%s/.%s' -e %s %s",
                STATSD_ATOM_TEST_PKG, service,
                KEY_ACTION, actionValue));
    }

    public static void rebootDeviceAndWaitUntilReady(ITestDevice device) throws Exception {
        device.rebootUntilOnline();
        // Wait for 3 mins.
        assertWithMessage("Device failed to boot")
            .that(device.waitForBootComplete(180_000)).isTrue();
        assertWithMessage("Stats service failed to start")
            .that(waitForStatsServiceStart(device, 60_000)).isTrue();
        RunUtil.getDefault().sleep(2_000);
    }

    private static boolean waitForStatsServiceStart(ITestDevice device, long waitTime)
            throws Exception {
        LogUtil.CLog.i("Waiting %d ms for stats service to start", waitTime);
        int counter = 1;
        long startTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startTime) < waitTime) {
            if ("running".equals(getProperty(device, "init.svc.statsd"))) {
                return true;
            }
            RunUtil.getDefault().sleep(Math.min(200 * counter, 2_000));
            counter++;
        }
        LogUtil.CLog.w("Stats service did not start after %d ms", waitTime);
        return false;
    }

    /**
     * Required to successfully start a background service from adb in Android O.
     */
    private static void allowBackgroundServices(ITestDevice device) throws Exception {
        device.executeShellCommand(String.format(
                "cmd deviceidle tempwhitelist %s", STATSD_ATOM_TEST_PKG));
    }

    /**
     * Returns the kernel major version as a pair of ints.
     */
    public static Pair<Integer, Integer> getKernelVersion(ITestDevice device)
            throws Exception {
        String[] version = device.executeShellCommand("uname -r").split("\\.");
        if (version.length < 2) {
              throw new RuntimeException("Could not parse kernel version");
        }
        return Pair.create(Integer.parseInt(version[0]), Integer.parseInt(version[1]));
    }

    /** Returns if the device kernel version >= input kernel version. */
    public static boolean isKernelGreaterEqual(ITestDevice device, Pair<Integer, Integer> version)
            throws Exception {
        Pair<Integer, Integer> kernelVersion = getKernelVersion(device);
        return kernelVersion.first > version.first
                || (Objects.equals(kernelVersion.first, version.first)
                && kernelVersion.second >= version.second);
    }

    private DeviceUtils() {}
}
