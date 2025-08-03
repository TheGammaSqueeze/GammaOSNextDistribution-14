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

package android.ext.services.hosttests;

import static com.android.adservices.common.AndroidSdk.PRE_T;
import static com.android.adservices.common.TestDeviceHelper.ACTION_BOOT_COMPLETED;
import static com.android.adservices.common.TestDeviceHelper.enableComponent;
import static com.android.adservices.common.TestDeviceHelper.isActiveReceiver;
import static com.android.adservices.common.TestDeviceHelper.runShellCommand;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.adservices.common.AdServicesHostSideFlagsSetterRule;
import com.android.adservices.common.AdServicesHostSideTestCase;
import com.android.adservices.common.BackgroundLogReceiver;
import com.android.adservices.common.HostSideSdkLevelSupportRule;
import com.android.adservices.common.RequiresSdkLevelAtLeastT;
import com.android.adservices.common.RequiresSdkRange;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.PackageInfo;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class AdServicesFilesCleanupBootCompleteReceiverHostTest
        extends AdServicesHostSideTestCase {
    private static final String EXTSERVICES_PACKAGE_SUFFIX = "android.ext.services";
    private static final String CLEANUP_RECEIVER_CLASS_NAME =
            "android.ext.services.common.AdServicesFilesCleanupBootCompleteReceiver";
    private static final String LOGCAT_COMMAND = "logcat -s extservices";
    private static final String RECEIVER_DISABLED_LOG_TEXT =
            "Disabled AdServices files cleanup receiver";
    private static final String RECEIVER_EXECUTED_LOG_TEXT = "AdServices files cleanup receiver";

    private String mAdServicesFilePath;
    private String mExtServicesPackageName;

    @Rule
    public final HostSideSdkLevelSupportRule sdkLevelSupportRule =
            HostSideSdkLevelSupportRule.forAnyLevel();

    @Rule
    public final AdServicesHostSideFlagsSetterRule flags =
            AdServicesHostSideFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    .setLogcatTag("extservices", "VERBOSE");

    @Before
    public void setUp() throws Exception {
        ITestDevice device = getDevice();

        // Enabling the boot-completed receiver throws a SecurityException unless adb runs as root
        Assume.assumeTrue("Needs adb root to enable the receiver", device.enableAdbRoot());

        logDeviceMetadata();

        // Find the extservices package
        PackageInfo extServicesPackage =
                device.getAppPackageInfos().stream()
                        .filter(s -> s.getPackageName().endsWith(EXTSERVICES_PACKAGE_SUFFIX))
                        .findFirst()
                        .orElse(null);
        assertWithMessage("ExtServices package").that(extServicesPackage).isNotNull();
        mExtServicesPackageName = extServicesPackage.getPackageName();

        // Put some data in the ExtServices apk
        mAdServicesFilePath =
                String.format(
                        "/data/user/%d/%s/adservices_data.txt",
                        device.getCurrentUser(), extServicesPackage.getPackageName());
        runShellCommand("echo \"Hello\" > %s", mAdServicesFilePath);
        assertWithMessage("%s exists", mAdServicesFilePath)
                .that(device.doesFileExist(mAdServicesFilePath))
                .isTrue();
    }

    @After
    public void tearDown() throws Exception {
        if (mDevice != null) {
            if (mAdServicesFilePath != null && mDevice.doesFileExist(mAdServicesFilePath)) {
                mDevice.deleteFile(mAdServicesFilePath);
            }

            mDevice.disableAdbRoot();
        }
    }

    @Test
    @RequiresSdkRange(atMost = PRE_T, reason = "Testing functionality that only runs on S-")
    public void testReceiver_doesNotExecuteOnSMinus() throws Exception {
        ITestDevice device = getDevice();

        // TODO(b/297207132) - use a rule instead of this shell command
        // Enable the flag that the receiver checks. By default, the flag is enabled in the binary,
        // so it's enough to just delete the flag override, if any.
        device.executeShellCommand(
                "device_config delete adservices extservices_adservices_data_cleanup_enabled");

        // Reboot, wait, and verify logs.
        verifyReceiverDidNotExecute(device);

        // Verify that adservices files are still present.
        assertWithMessage("%s exists", mAdServicesFilePath)
                .that(device.doesFileExist(mAdServicesFilePath))
                .isTrue();
    }

    @Test
    @RequiresSdkLevelAtLeastT(reason = "Testing functionality that only runs on T+")
    public void testReceiver_deletesFiles() throws Exception {
        ITestDevice device = getDevice();

        // Re-enable the cleanup receiver in case it's been disabled due to a prior run
        enableReceiver(device);

        // Enable the flag that the receiver checks. By default, the flag is enabled in the binary,
        // so it's enough to just delete the flag override, if any.
        device.executeShellCommand(
                "device_config delete adservices extservices_adservices_data_cleanup_enabled");

        // Reboot, wait, and verify logs.
        verifyReceiverExecuted(device);

        // Verify that all adservices files were deleted.
        assertWithMessage("%s exists", mAdServicesFilePath)
                .that(device.doesFileExist(mAdServicesFilePath))
                .isFalse();

        String lsCommand =
                String.format(
                        "ls /data/user/%d/%s -R", device.getCurrentUser(), mExtServicesPackageName);
        String lsOutput = device.executeShellCommand(lsCommand).toLowerCase(Locale.ROOT);
        assertWithMessage("Output of %s", lsCommand).that(lsOutput).doesNotContain("adservices");

        // Verify that after a reboot the receiver does not execute
        verifyReceiverDidNotExecute(device);
    }

    @Test
    @RequiresSdkLevelAtLeastT(reason = "Testing functionality that only runs on T+")
    public void testReceiver_doesNotExecuteIfFlagDisabled() throws Exception {
        ITestDevice device = getDevice();

        // Re-enable the cleanup receiver in case it's been disabled due to a prior run
        enableReceiver(device);

        // Disable the flag that the receiver checks
        device.executeShellCommand(
                "device_config put adservices extservices_adservices_data_cleanup_enabled false");

        // Verify that after a reboot the receiver executes but doesn't disable itself
        BackgroundLogReceiver logcatReceiver =
                rebootDeviceAndCollectLogs(device, RECEIVER_DISABLED_LOG_TEXT);
        Pattern errorPattern = Pattern.compile(makePattern(RECEIVER_DISABLED_LOG_TEXT));
        assertWithMessage("Presence of log indicating receiver disabled itself")
                .that(logcatReceiver.patternMatches(errorPattern))
                .isFalse();

        // Verify that the file is still there and that the receiver didn't delete it.
        assertWithMessage("%s exists", mAdServicesFilePath)
                .that(device.doesFileExist(mAdServicesFilePath))
                .isTrue();
    }

    private void verifyReceiverExecuted(ITestDevice device)
            throws DeviceNotAvailableException, InterruptedException {
        BackgroundLogReceiver logcatReceiver =
                rebootDeviceAndCollectLogs(device, RECEIVER_DISABLED_LOG_TEXT);
        Pattern errorPattern = Pattern.compile(makePattern(RECEIVER_DISABLED_LOG_TEXT));

        // Verify the receiver is no longer enabled
        assertWithMessage("Proof that receiver disabled itself")
                .that(logcatReceiver.patternMatches(errorPattern)
                        || !isActiveReceiver(
                        ACTION_BOOT_COMPLETED, mExtServicesPackageName,
                        CLEANUP_RECEIVER_CLASS_NAME))
                .isTrue();
    }

    private void verifyReceiverDidNotExecute(ITestDevice device)
            throws DeviceNotAvailableException, InterruptedException {
        BackgroundLogReceiver logcatReceiver =
                rebootDeviceAndCollectLogs(device, RECEIVER_EXECUTED_LOG_TEXT);

        Pattern errorPattern = Pattern.compile(makePattern(RECEIVER_EXECUTED_LOG_TEXT));
        assertWithMessage("Presence of log indicating receiver was invoked")
                .that(logcatReceiver.patternMatches(errorPattern))
                .isFalse();
    }

    private Predicate<String[]> stopIfTextOccurs(String toMatch) {
        return (s) -> Arrays.stream(s).anyMatch(t -> t.contains(toMatch));
    }

    private BackgroundLogReceiver rebootDeviceAndCollectLogs(ITestDevice device, String text)
            throws DeviceNotAvailableException, InterruptedException {
        // reboot the device
        device.reboot();
        device.waitForDeviceAvailable();

        flags.setLogcatTag("extservices", "VERBOSE");

        // Start log collection
        BackgroundLogReceiver logcatReceiver =
                new BackgroundLogReceiver.Builder()
                        .setDevice(device)
                        .setLogCatCommand(LOGCAT_COMMAND)
                        .setEarlyStopCondition(stopIfTextOccurs(text))
                        .build();

        // Collect logs for up to 5 minutes
        boolean isEarlyExit = logcatReceiver.collectLogs(/* timeoutMilliseconds= */ 5 * 60 * 1000);

        CLog.d("Finished collecting logs. Early exit = %s, collected logs = %s", isEarlyExit,
                logcatReceiver.getCollectedLogs());

        return logcatReceiver;
    }

    private String makePattern(String text) {
        return ".*" + text + ".*";
    }

    private void enableReceiver(ITestDevice device) throws DeviceNotAvailableException {
        String status = enableComponent(mExtServicesPackageName, CLEANUP_RECEIVER_CLASS_NAME,
                device.getCurrentUser());
        assertWithMessage("Receiver was successfully enabled")
                .that(status)
                .contains("new state: enabled");
    }

    private void logDeviceMetadata() {
        String apexVersion = runShellCommand(
                "pm list packages --apex-only --show-versioncode extservices").trim();
        String apkVersion = runShellCommand(
                "pm list packages --show-versioncode ext.services").trim();
        String privAppName = runShellCommand(
                "ls /apex/com.android.extservices/priv-app").trim();
        CLog.d("ExtServices apex version = <%s>, apk version = <%s>, priv-app = <%s>", apexVersion,
                apkVersion, privAppName);
    }
}
