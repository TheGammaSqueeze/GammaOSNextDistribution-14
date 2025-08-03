/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.cts.devicepolicy;

import static android.stats.devicepolicy.EventId.ADD_CROSS_PROFILE_WIDGET_PROVIDER_VALUE;
import static android.stats.devicepolicy.EventId.REMOVE_CROSS_PROFILE_WIDGET_PROVIDER_VALUE;
import static android.stats.devicepolicy.EventId.SET_CROSS_PROFILE_CALENDAR_PACKAGES_VALUE;
import static android.stats.devicepolicy.EventId.SET_CROSS_PROFILE_PACKAGES_VALUE;
import static android.stats.devicepolicy.EventId.SET_INTERACT_ACROSS_PROFILES_APP_OP_VALUE;

import static com.android.cts.devicepolicy.metrics.DevicePolicyEventLogVerifier.assertMetricsLogged;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.FlakyTest;

import com.android.cts.devicepolicy.metrics.DevicePolicyEventWrapper;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import com.google.common.collect.Sets;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ManagedProfileCrossProfileTest extends BaseManagedProfileTest {
    private static final String NOTIFICATION_APK = "CtsNotificationSenderApp.apk";
    private static final String WIDGET_PROVIDER_APK = "CtsWidgetProviderApp.apk";
    private static final String WIDGET_PROVIDER_PKG = "com.android.cts.widgetprovider";
    private static final String PARAM_PROFILE_ID = "profile-id";
    private static final String ACTION_CAN_INTERACT_ACROSS_PROFILES_CHANGED =
            "android.content.pm.action.CAN_INTERACT_ACROSS_PROFILES_CHANGED";

    /** From {@code android.app.AppOpsManager#MODE_DEFAULT}. */
    private static final int MODE_DEFAULT = 3;

    // The apps whose app-ops are maintained and unset are defined by the device-side test.
    private static final Set<String> UNSET_CROSS_PROFILE_PACKAGES =
            Sets.newHashSet(
                    TEST_APP_3_PKG,
                    TEST_APP_4_PKG);
    private static final Set<String> MAINTAINED_CROSS_PROFILE_PACKAGES =
            Sets.newHashSet(
                    TEST_APP_1_PKG,
                    TEST_APP_2_PKG);

    // The apps whose app-ops are maintained and unset are defined by
    // testSetCrossProfilePackages_resetsAppOps_noAsserts on the device-side.
    private static final Set<String> UNSET_CROSS_PROFILE_PACKAGES_2 =
            Sets.newHashSet(
                    TEST_APP_4_PKG);
    private static final Set<String> MAINTAINED_CROSS_PROFILE_PACKAGES_2 =
            Sets.newHashSet(
                    TEST_APP_1_PKG,
                    TEST_APP_2_PKG,
                    TEST_APP_3_PKG);

    @FlakyTest
    @Test
    public void testCrossProfileContent() throws Exception {

        // Storage permission shouldn't be granted, we check if missing permissions are respected
        // in ContentTest#testSecurity.
        installAppAsUser(INTENT_SENDER_APK, false /* grantPermissions */, USER_ALL);
        installAppAsUser(INTENT_RECEIVER_APK, USER_ALL);

        // Test from parent to managed
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testRemoveAllFilters", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testAddManagedCanAccessParentFilters", mProfileUserId);
        runDeviceTestsAsUser(INTENT_SENDER_PKG, ".ContentTest", mParentUserId);

        // Test from managed to parent
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testRemoveAllFilters", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testAddParentCanAccessManagedFilters", mProfileUserId);
        runDeviceTestsAsUser(INTENT_SENDER_PKG, ".ContentTest", mProfileUserId);
    }

    @FlakyTest
    @Test
    public void testCrossProfileNotificationListeners_EmptyAllowlist() throws Exception {

        installAppAsUser(NOTIFICATION_APK, USER_ALL);

        // Profile owner in the profile sets an empty allowlist
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NotificationListenerTest",
                "testSetEmptyAllowlist", mProfileUserId,
                Collections.singletonMap(PARAM_PROFILE_ID, Integer.toString(mProfileUserId)));
        // Listener outside the profile can only see personal notifications.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NotificationListenerTest",
                "testCannotReceiveProfileNotifications", mParentUserId,
                Collections.singletonMap(PARAM_PROFILE_ID, Integer.toString(mProfileUserId)));
    }

    @Test
    public void testCrossProfileNotificationListeners_NullAllowlist() throws Exception {

        installAppAsUser(NOTIFICATION_APK, USER_ALL);

        // Profile owner in the profile sets a null allowlist
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NotificationListenerTest",
                "testSetNullAllowlist", mProfileUserId,
                Collections.singletonMap(PARAM_PROFILE_ID, Integer.toString(mProfileUserId)));
        // Listener outside the profile can see profile and personal notifications
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NotificationListenerTest",
                "testCanReceiveNotifications", mParentUserId,
                Collections.singletonMap(PARAM_PROFILE_ID, Integer.toString(mProfileUserId)));
    }

    @Test
    public void testCrossProfileNotificationListeners_InAllowlist() throws Exception {

        installAppAsUser(NOTIFICATION_APK, USER_ALL);

        // Profile owner in the profile adds listener to the allowlist
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NotificationListenerTest",
                "testAddListenerToAllowlist", mProfileUserId,
                Collections.singletonMap(PARAM_PROFILE_ID, Integer.toString(mProfileUserId)));
        // Listener outside the profile can see profile and personal notifications
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NotificationListenerTest",
                "testCanReceiveNotifications", mParentUserId,
                Collections.singletonMap(PARAM_PROFILE_ID, Integer.toString(mProfileUserId)));
    }

    @Test
    public void testCrossProfileNotificationListeners_setAndGet() throws Exception {
        installAppAsUser(NOTIFICATION_APK, USER_ALL);

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NotificationListenerTest",
                "testSetAndGetPermittedCrossProfileNotificationListeners", mProfileUserId,
                Collections.singletonMap(PARAM_PROFILE_ID, Integer.toString(mProfileUserId)));
    }

    @FlakyTest
    @Test
    public void testCrossProfileCopyPaste() throws Exception {
        installAppAsUser(INTENT_RECEIVER_APK, USER_ALL);
        installAppAsUser(INTENT_SENDER_APK, USER_ALL);

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testAllowCrossProfileCopyPaste", mProfileUserId);
        // Test that managed can see what is copied in the parent.
        testCrossProfileCopyPasteInternal(mParentUserId, mProfileUserId, true);
        // Test that the parent can see what is copied in managed.
        testCrossProfileCopyPasteInternal(mProfileUserId, mParentUserId, true);

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testDisallowCrossProfileCopyPaste", mProfileUserId);
        // Test that managed can still see what is copied in the parent.
        testCrossProfileCopyPasteInternal(mParentUserId, mProfileUserId, true);
        // Test that the parent cannot see what is copied in managed.
        testCrossProfileCopyPasteInternal(mProfileUserId, mParentUserId, false);
    }

    private void testCrossProfileCopyPasteInternal(
            int sourceUserId, int targetUserId, boolean shouldSucceed)
            throws DeviceNotAvailableException {
        final String testAddTargetCanAccessSource = (sourceUserId == mParentUserId)
                ? "testAddManagedCanAccessParentFilters"
                : "testAddParentCanAccessManagedFilters";
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testRemoveAllFilters", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                testAddTargetCanAccessSource, mProfileUserId);
        runDeviceTestsAsUser(INTENT_SENDER_PKG, ".CopyPasteTest",
                "testCopyInitialText", targetUserId);
        runDeviceTestsAsUser(INTENT_SENDER_PKG, ".CopyPasteTest",
                "testCopyNewText", sourceUserId);
        if (shouldSucceed) {
            runDeviceTestsAsUser(INTENT_SENDER_PKG, ".CopyPasteTest",
                    "testClipboardHasNewText", targetUserId);
        } else {
            runDeviceTestsAsUser(INTENT_SENDER_PKG, ".CopyPasteTest",
                    "testClipboardHasInitialTextOrNull", targetUserId);
        }
    }

    @FlakyTest
    @Test
    public void testCrossProfileWidgets() throws Exception {
        try {
            installAppAsUser(WIDGET_PROVIDER_APK, USER_ALL);
            getDevice().executeShellCommand("appwidget grantbind --user " + mParentUserId
                    + " --package " + WIDGET_PROVIDER_PKG);
            setIdleAllowlist(WIDGET_PROVIDER_PKG, true);
            startWidgetHostService();
            RunUtil.getDefault().sleep(500);

            String commandOutput = changeCrossProfileWidgetForUser(WIDGET_PROVIDER_PKG,
                    "add-cross-profile-widget", mProfileUserId);
            assertTrue("Command was expected to succeed " + commandOutput,
                    commandOutput.contains("Status: ok"));

            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileWidgetTest",
                    "testCrossProfileWidgetProviderAdded", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG,
                    ".CrossProfileWidgetPrimaryUserTest",
                    "testHasCrossProfileWidgetProvider_true", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG,
                    ".CrossProfileWidgetPrimaryUserTest",
                    "testHostReceivesWidgetUpdates_true", mParentUserId);

            commandOutput = changeCrossProfileWidgetForUser(WIDGET_PROVIDER_PKG,
                    "remove-cross-profile-widget", mProfileUserId);
            assertTrue("Command was expected to succeed " + commandOutput,
                    commandOutput.contains("Status: ok"));

            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileWidgetTest",
                    "testCrossProfileWidgetProviderRemoved", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG,
                    ".CrossProfileWidgetPrimaryUserTest",
                    "testHasCrossProfileWidgetProvider_false", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG,
                    ".CrossProfileWidgetPrimaryUserTest",
                    "testHostReceivesWidgetUpdates_false", mParentUserId);
        } finally {
            changeCrossProfileWidgetForUser(WIDGET_PROVIDER_PKG, "remove-cross-profile-widget",
                    mProfileUserId);
            getDevice().uninstallPackage(WIDGET_PROVIDER_PKG);
        }
    }

    @FlakyTest
    @Test
    public void testCrossProfileWidgetsLogged() throws Exception {
        try {
            installAppAsUser(WIDGET_PROVIDER_APK, USER_ALL);
            getDevice().executeShellCommand("appwidget grantbind --user " + mParentUserId
                    + " --package " + WIDGET_PROVIDER_PKG);
            setIdleAllowlist(WIDGET_PROVIDER_PKG, true);
            startWidgetHostService();

            assertMetricsLogged(getDevice(), () -> {
                changeCrossProfileWidgetForUser(WIDGET_PROVIDER_PKG,
                        "add-cross-profile-widget", mProfileUserId);
                changeCrossProfileWidgetForUser(WIDGET_PROVIDER_PKG,
                        "remove-cross-profile-widget", mProfileUserId);
            }, new DevicePolicyEventWrapper
                        .Builder(ADD_CROSS_PROFILE_WIDGET_PROVIDER_VALUE)
                        .setAdminPackageName(MANAGED_PROFILE_PKG)
                        .build(),
                new DevicePolicyEventWrapper
                        .Builder(REMOVE_CROSS_PROFILE_WIDGET_PROVIDER_VALUE)
                        .setAdminPackageName(MANAGED_PROFILE_PKG)
                        .build());
        } finally {
            changeCrossProfileWidgetForUser(WIDGET_PROVIDER_PKG, "remove-cross-profile-widget",
                    mProfileUserId);
            getDevice().uninstallPackage(WIDGET_PROVIDER_PKG);
        }
    }

    @Test
    public void testCrossProfileCalendarPackage() throws Exception {
        assertMetricsLogged(getDevice(), () -> {
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileCalendarTest",
                    "testCrossProfileCalendarPackage", mProfileUserId);
        }, new DevicePolicyEventWrapper.Builder(SET_CROSS_PROFILE_CALENDAR_PACKAGES_VALUE)
                    .setAdminPackageName(MANAGED_PROFILE_PKG)
                    .setStrings(MANAGED_PROFILE_PKG)
                    .build());
    }

    @Test
    public void testSetCrossProfilePackages_notProfileOwner_throwsSecurityException()
            throws Exception {
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG,
                ".CrossProfileTest",
                "testSetCrossProfilePackages_notProfileOwner_throwsSecurityException",
                mProfileUserId);
    }

    @Test
    public void testGetCrossProfilePackages_notProfileOwner_throwsSecurityException()
            throws Exception {
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG,
                ".CrossProfileTest",
                "testGetCrossProfilePackages_notProfileOwner_throwsSecurityException",
                mProfileUserId);
    }

    @Test
    public void testGetCrossProfilePackages_notSet_returnsEmpty()
            throws Exception {
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG,
                ".CrossProfileTest",
                "testGetCrossProfilePackages_notSet_returnsEmpty",
                mProfileUserId);
    }

    @Test
    public void testGetCrossProfilePackages_whenSetTwice_returnsLatestNotConcatenated()
            throws Exception {
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG,
                ".CrossProfileTest",
                "testGetCrossProfilePackages_whenSetTwice_returnsLatestNotConcatenated",
                mProfileUserId);
    }

    @Test
    public void testGetCrossProfilePackages_whenSet_returnsEqual()
            throws Exception {
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG,
                ".CrossProfileTest",
                "testGetCrossProfilePackages_whenSet_returnsEqual",
                mProfileUserId);
    }

    @Test
    public void testSetCrossProfilePackages_isLogged() throws Exception {
        installAllTestApps();
        assertMetricsLogged(
                getDevice(),
                () -> runWorkProfileDeviceTest(
                        ".CrossProfileTest", "testSetCrossProfilePackages_noAsserts"),
                new DevicePolicyEventWrapper.Builder(SET_CROSS_PROFILE_PACKAGES_VALUE)
                        .setAdminPackageName(MANAGED_PROFILE_PKG)
                        .setStrings(TEST_APP_1_PKG)
                        .build());
    }

    @Test
    public void testSetCrossProfilePackages_resetsAppOps() throws Exception {
        installAllTestApps();
        runWorkProfileDeviceTest(
                ".CrossProfileTest",
                "testSetCrossProfilePackages_firstTime_doesNotResetAnyAppOps");
        runWorkProfileDeviceTest(
                ".CrossProfileTest",
                "testSetCrossProfilePackages_unchanged_doesNotResetAnyAppOps");
        runWorkProfileDeviceTest(
                ".CrossProfileTest",
                "testSetCrossProfilePackages_noPackagesUnset_doesNotResetAnyAppOps");
        runWorkProfileDeviceTest(
                ".CrossProfileTest",
                "testSetCrossProfilePackages_somePackagesUnset_doesNotResetAppOpsIfStillSet");
        runWorkProfileDeviceTest(
                ".CrossProfileTest",
                "testSetCrossProfilePackages_resetsAppOpOfUnsetPackages");
        runWorkProfileDeviceTest(
                ".CrossProfileTest",
                "testSetCrossProfilePackages_resetsAppOpOfUnsetPackagesOnOtherProfile");
    }

    private void setupLogcatForTest() throws Exception {
        // Clear and increase logcat buffer size because the test is reading from it.
        final String clearLogcatCommand = "logcat -c";
        getDevice().executeShellCommand(clearLogcatCommand);
        final String increaseLogcatBufferCommand = "logcat -G 16M";
        getDevice().executeShellCommand(increaseLogcatBufferCommand);
    }

    /** Assumes that logcat is clear before running the test. */
    private void assertTestAppsReceivedCanInteractAcrossProfilesChangedBroadcast(
            Set<String> packageNames)
            throws Exception {
        for (String packageName : packageNames) {
            assertTrue(didTestAppReceiveCanInteractAcrossProfilesChangedBroadcast(
                    packageName, mProfileUserId));
            assertTrue(didTestAppReceiveCanInteractAcrossProfilesChangedBroadcast(
                    packageName, mParentUserId));
        }
    }

    /** Assumes that logcat is clear before running the test. */
    private void assertTestAppsDidNotReceiveCanInteractAcrossProfilesChangedBroadcast(
            Set<String> packageNames)
            throws Exception {
        for (String packageName : packageNames) {
            assertFalse(didTestAppReceiveCanInteractAcrossProfilesChangedBroadcast(
                    packageName, mProfileUserId));
            assertFalse(didTestAppReceiveCanInteractAcrossProfilesChangedBroadcast(
                    packageName, mParentUserId));
        }
    }

    /** Assumes that logcat is clear before running the test. */
    private boolean didTestAppReceiveCanInteractAcrossProfilesChangedBroadcast(
            String packageName, int userId)
            throws Exception {
        // The expected string is defined in the broadcast receiver of the test apps to be
        // packageName#action#userId.
        final String expectedSubstring =
                packageName + "#" + ACTION_CAN_INTERACT_ACROSS_PROFILES_CHANGED + "#" + userId;
        return readLogcat().contains(expectedSubstring);
    }

    @Test
    public void testSetCrossProfilePackages_resetsAppOps_isLogged() throws Exception {
        installAllTestApps();
        assertMetricsLogged(
                getDevice(),
                () -> runWorkProfileDeviceTest(
                        ".CrossProfileTest", "testSetCrossProfilePackages_resetsAppOps_noAsserts"),
                new DevicePolicyEventWrapper.Builder(SET_INTERACT_ACROSS_PROFILES_APP_OP_VALUE)
                        .setStrings(TEST_APP_4_PKG)
                        .setInt(MODE_DEFAULT)
                        .setBoolean(true) // cross-profile manifest attribute
                        .build());
    }

    @Test
    public void testSetCrossProfilePackages_killsApps() throws Exception {
        installAllTestApps();
        launchAllTestAppsInBothProfiles();
        Map<String, List<String>> maintainedPackagesPids = getPackagesPids(
                MAINTAINED_CROSS_PROFILE_PACKAGES_2);
        Map<String, List<String>> unsetPackagesPids = getPackagesPids(
                UNSET_CROSS_PROFILE_PACKAGES_2);

        runWorkProfileDeviceTest(
                ".CrossProfileTest",
                "testSetCrossProfilePackages_resetsAppOps_noAsserts");

        for (String packageName : MAINTAINED_CROSS_PROFILE_PACKAGES_2) {
            assertAppRunningInBothProfiles(packageName, maintainedPackagesPids.get(packageName));
        }
        for (String packageName : UNSET_CROSS_PROFILE_PACKAGES_2) {
            assertAppKilledInBothProfiles(packageName, unsetPackagesPids.get(packageName));
        }
    }

    private Map<String, List<String>> getPackagesPids(Set<String> packages) throws Exception {
        Map<String, List<String>> pids = new HashMap<>();
        for (String packageName : packages) {
            pids.put(packageName, Arrays.asList(getAppPid(packageName).split(" ")));
        }
        return pids;
    }

    private void launchAllTestAppsInBothProfiles() throws Exception {
        launchAllTestAppsForUser(mParentUserId);
        launchAllTestAppsForUser(mProfileUserId);
    }

    private void launchAllTestAppsForUser(int userId) throws Exception {
        final String testActivity = "android.app.Activity";
        startActivityAsUser(userId, TEST_APP_1_PKG, testActivity);
        startActivityAsUser(userId, TEST_APP_2_PKG, testActivity);
        startActivityAsUser(userId, TEST_APP_3_PKG, testActivity);
        startActivityAsUser(userId, TEST_APP_4_PKG, testActivity);
    }

    private void assertAppRunningInBothProfiles(String packageName, List<String> pids)
            throws Exception {
        Set<String> currentPids = new HashSet<>(
                Arrays.asList(getAppPid(packageName).split(" ")));
        assertThat(currentPids).containsAtLeastElementsIn(pids);
    }

    private void assertAppKilledInBothProfiles(String packageName,  List<String> pids)
            throws Exception {
        Set<String> currentPids = new HashSet<>(
                Arrays.asList(getAppPid(packageName).split(" ")));
        assertThat(currentPids).containsNoneIn(pids);
    }

    private String getAppPid(String packageName) throws Exception {
        return getDevice().executeShellCommand(String.format("pidof %s", packageName)).trim();
    }

    private void setIdleAllowlist(String packageName, boolean enabled)
            throws DeviceNotAvailableException {
        String command = "cmd deviceidle whitelist " + (enabled ? "+" : "-") + packageName;
        LogUtil.CLog.d("Output for command " + command + ": "
                + getDevice().executeShellCommand(command));
    }

    private String changeCrossProfileWidgetForUser(String packageName, String command, int userId)
            throws DeviceNotAvailableException {
        String adbCommand = "am start -W --user " + userId
                + " -c android.intent.category.DEFAULT "
                + " --es extra-command " + command
                + " --es extra-package-name " + packageName
                + " " + MANAGED_PROFILE_PKG + "/.SetPolicyActivity";
        String commandOutput = getDevice().executeShellCommand(adbCommand);
        LogUtil.CLog.d("Output for command " + adbCommand + ": " + commandOutput);
        return commandOutput;
    }

    private void startWidgetHostService() throws Exception {
        String command = "am startservice --user " + mParentUserId
                + " -a " + WIDGET_PROVIDER_PKG + ".REGISTER_CALLBACK "
                + "--ei user-extra " + getUserSerialNumber(mProfileUserId)
                + " " + WIDGET_PROVIDER_PKG + "/.SimpleAppWidgetHostService";
        LogUtil.CLog.d("Output for command " + command + ": "
                + getDevice().executeShellCommand(command));
    }

    private void installAllTestApps() throws Exception {
        installAppAsUser(TEST_APP_1_APK, USER_ALL);
        installAppAsUser(TEST_APP_2_APK, USER_ALL);
        installAppAsUser(TEST_APP_3_APK, USER_ALL);
        installAppAsUser(TEST_APP_4_APK, USER_ALL);
    }

    private void runWorkProfileDeviceTest(String className, String methodName) throws Exception {
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG,
                className,
                methodName,
                mProfileUserId);
    }

    private String readLogcat() throws Exception {
        getDevice().stopLogcat();
        final String logcat;
        try (InputStreamSource logcatStream = getDevice().getLogcat()) {
            logcat = StreamUtil.getStringFromSource(logcatStream);
        }
        getDevice().startLogcat();
        return logcat;
    }
}
