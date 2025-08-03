/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.cts;

import static com.android.adservices.common.TestDeviceHelper.runShellCommand;
import static com.android.adservices.service.FlagsConstants.KEY_APPSEARCH_WRITER_ALLOW_LIST_OVERRIDE;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_TOPICS_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_APPSEARCH_CONSENT_DATA;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH;

import static com.google.common.truth.Truth.assertWithMessage;

import android.cts.statsdatom.lib.DeviceUtils;

import com.android.adservices.common.AdServicesHostSideDeviceSupportedRule;
import com.android.adservices.common.AdServicesHostSideFlagsSetterRule;
import com.android.adservices.common.AdServicesHostSideTestCase;
import com.android.adservices.common.BackgroundLogReceiver;
import com.android.adservices.common.HostSideSdkLevelSupportRule;
import com.android.adservices.common.TestDeviceHelper;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import com.google.common.truth.Expect;
import com.google.common.truth.StringSubject;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Test to check that consent is migrated correctly from AppSearch
 *
 * <p>When this test builds, it also builds {@link AppSearchWriterActivity} into an APK which it
 * then installed at runtime and started. The activity simply called getTopics service which trigger
 * the log event, and then gets uninstalled.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class AppSearchDataMigrationHostTest extends AdServicesHostSideTestCase {
    private static final long BOOT_COMPLETED_TIMEOUT = 60_000L;
    private static final long LOG_RECEIVER_TIMEOUT_MS = 10_000L;
    private static final int ACTIVITY_LAUNCH_TIMEOUT_MS = 5_000;

    private static final String PACKAGE = "com.android.adservices.cts";
    private static final String CLASS = "AppSearchWriterActivity";
    private static final String ADSERVICES_APK_PACKAGE_NAME_SUFFIX = "android.adservices.api";

    private int mCurrentUser = 0;
    private String mAdServicesPackageName = null;

    @Rule(order = 0)
    public final HostSideSdkLevelSupportRule sdkLevel = HostSideSdkLevelSupportRule.forAtLeastT();

    @Rule(order = 1)
    public final AdServicesHostSideDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesHostSideDeviceSupportedRule();

    @Rule(order = 2)
    public final AdServicesHostSideFlagsSetterRule flags =
            AdServicesHostSideFlagsSetterRule.forCompatModeEnabledTests()
                    // Top-level feature flags
                    .setAdServicesEnabled(true)
                    // Topics feature flags for calling the Topics API
                    .setTopicsKillSwitch(false)
                    .setMddBackgroundTaskKillSwitch(true)
                    .setFlag(KEY_DISABLE_TOPICS_ENROLLMENT_CHECK, true)
                    // Measurement feature flags for calling the Measurement API
                    .setMsmtApiAppAllowList(PACKAGE)
                    .setMsmtWebContextClientAllowList(PACKAGE)
                    .setFlag(KEY_MEASUREMENT_KILL_SWITCH, false)
                    // AppSearch feature flags
                    .setFlag(KEY_ENABLE_APPSEARCH_CONSENT_DATA, true)
                    .setFlag(KEY_APPSEARCH_WRITER_ALLOW_LIST_OVERRIDE, PACKAGE)
                    // Logcat tags
                    .setAllLogcatTags()
                    .setLogcatTag("AppSearchWriterActivity", "VERBOSE");

    @Rule(order = 3)
    public final Expect expect = Expect.create();

    @Before
    public void setUp() throws Exception {
        // Enabling the boot-completed receiver throws a SecurityException unless adb runs as root
        Assume.assumeTrue("Needs adb root to enable the receiver", mDevice.enableAdbRoot());

        mCurrentUser = mDevice.getCurrentUser();
        mAdServicesPackageName = getAdServicesPackageName();
        assertWithMessage("AdServices package name").that(mAdServicesPackageName).isNotNull();

        // Delete existing consent data that was previously migrated/set.
        deletePreExistingData();

        mDevice.reboot();
        mDevice.waitForBootComplete(BOOT_COMPLETED_TIMEOUT);
        mDevice.waitForDeviceAvailable();
        mDevice.enableAdbRoot();
    }

    @After
    public void tearDown() {
        deletePreExistingData();
    }

    /**
     * Reboot the device at the end of this test suite
     *
     * @throws DeviceNotAvailableException if the device is not available
     */
    @AfterClass
    public static void classTearDown() throws DeviceNotAvailableException {
        TestDeviceHelper.getTestDevice().reboot();
    }

    @Test
    public void testAppSearchConsentMigration() throws Exception {
        // Wait a few seconds for the device to get to the home screen
        TimeUnit.SECONDS.sleep(5);

        // Need to re-set these system properties because the reboot in the setup method causes them
        // to be cleared.
        flags.setFlag(KEY_DISABLE_TOPICS_ENROLLMENT_CHECK, true)
                .setLogcatTag("adservices", "VERBOSE")
                .setLogcatTag("AppSearchWriterActivity", "VERBOSE");

        String msmtSuccessMsg = "AppSearchWriterActivity: GetMeasurementStatus API call succeeded";
        String msmtFailureMsg = "AppSearchWriterActivity: GetMeasurementStatus API call failed";
        Predicate<String[]> apiCompletedLogPresentPredicate =
                s ->
                        Arrays.stream(s)
                                .anyMatch(
                                        t ->
                                                t.contains(msmtSuccessMsg)
                                                        || t.contains(msmtFailureMsg));

        // Instantiate and start background log collection
        BackgroundLogReceiver receiver =
                new BackgroundLogReceiver.Builder()
                        .setDevice(mDevice)
                        .setLogCatCommand("logcat -s AppSearchWriterActivity,adservices")
                        .setEarlyStopCondition(apiCompletedLogPresentPredicate)
                        .build();

        receiver.startBackgroundCollection();

        // Start the test helper app that will write consent data and trigger a migration
        DeviceUtils.runActivity(
                mDevice,
                PACKAGE,
                CLASS,
                /* actionKey= */ "user-id",
                /* actionValue= */ Integer.toString(mCurrentUser),
                ACTIVITY_LAUNCH_TIMEOUT_MS);

        receiver.waitForLogs(LOG_RECEIVER_TIMEOUT_MS);

        List<String> logs = receiver.getCollectedLogs();
        CLog.d("Collected logs: %s", logs);

        // Verify that the logs contain the appropriate success messages
        String migrationMsg = "Finished migrating Consent from AppSearch to PPAPI + System Service";
        expect.withMessage("Migration completed")
                .that(logs.stream().anyMatch(s -> s.contains(migrationMsg)))
                .isTrue();
        expect.withMessage("GetMeasurementStatus API succeeded")
                .that(logs.stream().anyMatch(s -> s.contains(msmtSuccessMsg)))
                .isTrue();

        // Validate that the Consent xml file in system server contains the migrated values
        String filePath = getSystemServerConsentXmlPath();
        assertWithMessage("ConsentManagerStorageIdentifier.xml exists")
                .that(mDevice.doesFileExist(filePath))
                .isTrue();

        String consentFileContents = mDevice.pullFileContents(filePath);
        StringSubject subj = expect.withMessage("Consent xml contents").that(consentFileContents);
        // ConsentApiType values are defined in ConsentParcel.java
        subj.contains(getXmlString("CONSENT_API_TYPE_2", true)); // Topics consent
        subj.contains(getXmlString("CONSENT_API_TYPE_3", false)); // Fledge consent
        subj.contains(getXmlString("CONSENT_API_TYPE_4", true)); // Measurement consent

        // Validate the blocked topics
        List<Integer> blockedTopics = getBlockedTopics();
        expect.withMessage("Blocked topics").that(blockedTopics).containsExactly(10004, 10410);
    }

    private void deletePreExistingData() {
        if (mAdServicesPackageName == null) {
            return;
        }

        // Stop the adservices process to remove any locks on the files
        runShellCommand("am force-stop %s", mAdServicesPackageName);

        // Delete the files marking that consent has already migrated
        runShellCommand(
                "rm -f /data/user/%d/%s/shared_prefs/PPAPI_*",
                mCurrentUser, mAdServicesPackageName);

        // Delete the files storing the consent in the PPAPI data directory
        runShellCommand(
                "rm -f /data/user/%d/%s/files/ConsentManagerStorageIdentifier.xml",
                mCurrentUser, mAdServicesPackageName);

        // Delete the files storing the consent in system server
        runShellCommand("rm -f /data/system/adservices/%d/consent/*", mCurrentUser);

        // Delete the database storing the blocked topics in system server
        runShellCommand("rm -f /data/system/adservices_topics.db*");
    }

    private String getAdServicesPackageName() throws DeviceNotAvailableException {
        return mDevice.getInstalledPackageNames().stream()
                .filter(s -> s.endsWith(ADSERVICES_APK_PACKAGE_NAME_SUFFIX))
                .findFirst()
                .orElse(null);
    }

    private String getXmlString(String key, boolean value) {
        return String.format("<boolean name=\"%s\" value=\"%s\" />", key, value);
    }

    private String getSystemServerConsentXmlPath() {
        return String.format(
                "/data/system/adservices/%d/consent/ConsentManagerStorageIdentifier.xml",
                mCurrentUser);
    }

    private List<Integer> getBlockedTopics() {
        String query =
                String.format("Select topic from blocked_topics where user=%s", mCurrentUser);
        String result = runShellCommand("sqlite3 /data/system/adservices_topics.db \"%s\"", query);
        if (result == null || result.isBlank()) {
            return List.of();
        }

        return Arrays.stream(result.split("\n"))
                .map(String::trim)
                .map(Integer::valueOf)
                .collect(Collectors.toList());
    }
}
