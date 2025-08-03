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

import static com.android.cts.devicepolicy.DeviceAdminFeaturesCheckerRule.FEATURE_MANAGED_USERS;
import static com.android.cts.devicepolicy.DeviceAndProfileOwnerTest.DEVICE_ADMIN_COMPONENT_FLATTENED;
import static com.android.cts.devicepolicy.metrics.DevicePolicyEventLogVerifier.assertMetricsLogged;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.platform.test.annotations.FlakyTest;
import android.platform.test.annotations.LargeTest;
import android.stats.devicepolicy.EventId;

import com.android.cts.devicepolicy.DeviceAdminFeaturesCheckerRule.RequiresAdditionalFeatures;
import com.android.cts.devicepolicy.metrics.DevicePolicyEventWrapper;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.RunUtil;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * Tests for organization-owned Profile Owner.
 */
// We need managed users to be supported in order to create a profile of the user owner.
@RequiresAdditionalFeatures({FEATURE_MANAGED_USERS})
public final class OrgOwnedProfileOwnerTest extends BaseDevicePolicyTest {
    private static final String DEVICE_ADMIN_PKG = DeviceAndProfileOwnerTest.DEVICE_ADMIN_PKG;
    private static final String DEVICE_ADMIN_APK = DeviceAndProfileOwnerTest.DEVICE_ADMIN_APK;
    private static final String CERT_INSTALLER_PKG = DeviceAndProfileOwnerTest.CERT_INSTALLER_PKG;
    private static final String CERT_INSTALLER_APK = DeviceAndProfileOwnerTest.CERT_INSTALLER_APK;
    private static final String DELEGATE_APP_PKG = DeviceAndProfileOwnerTest.DELEGATE_APP_PKG;
    private static final String DELEGATE_APP_APK = DeviceAndProfileOwnerTest.DELEGATE_APP_APK;
    private static final String LOG_TAG_PROFILE_OWNER = "profile-owner";

    private static final String ADMIN_RECEIVER_TEST_CLASS =
            DeviceAndProfileOwnerTest.ADMIN_RECEIVER_TEST_CLASS;
    private static final String ACTION_WIPE_DATA =
            "com.android.cts.deviceandprofileowner.WIPE_DATA";

    private static final String TEST_APP_APK = "CtsSimpleApp.apk";
    private static final String TEST_APP_PKG = "com.android.cts.launcherapps.simpleapp";
    private static final String TEST_IME_APK = "TestIme.apk";
    private static final String TEST_IME_PKG = "com.android.cts.testime";
    private static final String TEST_IME_COMPONENT = TEST_IME_PKG + "/.TestIme";
    private static final String SIMPLE_SMS_APP_PKG = "android.telephony.cts.sms.simplesmsapp";
    private static final String SIMPLE_SMS_APP_APK = "SimpleSmsApp.apk";
    public static final String SUSPENSION_CHECKER_CLASS =
            "com.android.cts.suspensionchecker.ActivityLaunchTest";

    private static final String USER_IS_NOT_STARTED = "User is not started";
    private static final long USER_STOP_TIMEOUT_SEC = 60;

    protected int mUserId;
    private static final String DISALLOW_CONFIG_LOCATION = "no_config_location";
    private static final String CALLED_FROM_PARENT = "calledFromParent";

    @Rule
    public DeviceJUnit4ClassRunner.TestLogData mLogger = new DeviceJUnit4ClassRunner.TestLogData();

    @Override
    public void setUp() throws Exception {
        super.setUp();

        removeTestUsers();
        createManagedProfile();
    }

    private void createManagedProfile() throws Exception {
        mUserId = createManagedProfile(mPrimaryUserId);
        switchUser(mPrimaryUserId);
        startUserAndWait(mUserId);

        installAppAsUser(DEVICE_ADMIN_APK, mUserId);
        setProfileOwnerOrFail(DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mUserId);
        startUserAndWait(mUserId);
        restrictManagedProfileRemoval();
    }

    @Override
    public void tearDown() throws Exception {
        // Managed profile and other test users will be removed by BaseDevicePolicyTest.tearDown()
        super.tearDown();
    }

    private void restrictManagedProfileRemoval() throws DeviceNotAvailableException {
            getDevice().executeShellCommand(
                    String.format("dpm mark-profile-owner-on-organization-owned-device --user %d '%s'",
                            mUserId, DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS));
    }

    @Test
    public void testCanRelinquishControlOverDevice() throws Exception {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".LockScreenInfoTest", "testSetAndGetLockInfo",
                mUserId);

        removeOrgOwnedProfile();
        assertHasNoUser(mUserId);

        try {
            installAppAsUser(DEVICE_ADMIN_APK, /* userId= */ 0);
            assertTrue(setDeviceOwner(DEVICE_ADMIN_COMPONENT_FLATTENED,
                    /* userId= */ 0, /*expectFailure*/false));
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".LockScreenInfoTest", "testLockInfoIsNull",
                    /* userId= */ 0);
        } finally {
            removeAdmin(DEVICE_ADMIN_COMPONENT_FLATTENED, /* userId= */ 0);
            getDevice().uninstallPackage(DEVICE_ADMIN_PKG);
        }
    }

    @Test
    public void testLockScreenInfo() throws Exception {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".LockScreenInfoTest", mUserId);
    }

    @Test
    public void testProfileOwnerCanGetDeviceIdentifiers() throws Exception {
        // The Profile Owner should have access to all device identifiers.
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DeviceIdentifiersTest",
                "testProfileOwnerCanGetDeviceIdentifiersWithPermission", mUserId);
    }

    @Test
    public void testDevicePolicyManagerParentSupport() throws Exception {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".OrgOwnedProfileOwnerParentTest", mUserId);
    }

    @Test
    public void testUserRestrictionSetOnParentLogged() throws Exception {
        assertMetricsLogged(getDevice(), () -> {
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DevicePolicyLoggingParentTest",
                    "testUserRestrictionLogged", mUserId);
                }, new DevicePolicyEventWrapper.Builder(EventId.ADD_USER_RESTRICTION_VALUE)
                        .setAdminPackageName(DEVICE_ADMIN_PKG)
                        .setStrings(DISALLOW_CONFIG_LOCATION, CALLED_FROM_PARENT)
                        .build(),
                new DevicePolicyEventWrapper.Builder(EventId.REMOVE_USER_RESTRICTION_VALUE)
                        .setAdminPackageName(DEVICE_ADMIN_PKG)
                        .setStrings(DISALLOW_CONFIG_LOCATION, CALLED_FROM_PARENT)
                        .build());
    }

    @Test
    public void testUserRestrictionsSetOnParentAreNotPersisted() throws Exception {
        assumeCanCreateAdditionalUsers(1);

        installAppAsUser(DEVICE_ADMIN_APK, mPrimaryUserId);
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UserRestrictionsParentTest",
                "testAddUserRestrictionDisallowConfigDateTime_onParent", mUserId);
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UserRestrictionsParentTest",
                "testHasUserRestrictionDisallowConfigDateTime", mPrimaryUserId);
        removeOrgOwnedProfile();
        assertHasNoUser(mUserId);

        // User restrictions are not persist after organization-owned profile owner is removed
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UserRestrictionsParentTest",
                "testUserRestrictionDisallowConfigDateTimeIsNotPersisted", mPrimaryUserId);
    }

    @Test
    public void testPerProfileUserRestrictionOnParent() throws Exception {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UserRestrictionsParentTest",
                "testPerProfileUserRestriction_onParent", mUserId);
    }

    @Test
    public void testPerDeviceUserRestrictionOnParent() throws Exception {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UserRestrictionsParentTest",
                "testPerDeviceUserRestriction_onParent", mUserId);
    }

    @Test
    public void testCameraDisabledOnParentIsEnforced() throws Exception {
        installAppAsUser(DEVICE_ADMIN_APK, mPrimaryUserId);
        try {
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UserRestrictionsParentTest",
                    "testAddUserRestrictionCameraDisabled_onParent", mUserId);
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UserRestrictionsParentTest",
                    "testCannotOpenCamera", mPrimaryUserId);
        } finally {
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UserRestrictionsParentTest",
                    "testRemoveUserRestrictionCameraEnabled_onParent", mUserId);
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UserRestrictionsParentTest",
                    "testCanOpenCamera", mPrimaryUserId);
        }
    }

    @Test
    public void testCameraDisabledOnParentLogged() throws Exception {
        assertMetricsLogged(getDevice(), () -> {
                    runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DevicePolicyLoggingParentTest",
                            "testCameraDisabledLogged", mUserId);
                }, new DevicePolicyEventWrapper.Builder(EventId.SET_CAMERA_DISABLED_VALUE)
                        .setAdminPackageName(DEVICE_ADMIN_PKG)
                        .setBoolean(true)
                        .setStrings(CALLED_FROM_PARENT)
                        .build(),
                new DevicePolicyEventWrapper.Builder(EventId.SET_CAMERA_DISABLED_VALUE)
                        .setAdminPackageName(DEVICE_ADMIN_PKG)
                        .setBoolean(false)
                        .setStrings(CALLED_FROM_PARENT)
                        .build());
    }

    @Test
    public void testSecurityLogging() throws Exception {
        installAppAsUser(DEVICE_ADMIN_APK, mPrimaryUserId);
        testSecurityLoggingOnWorkProfile(DEVICE_ADMIN_PKG, ".SecurityLoggingTest");
    }

    @Test
    public void testSecurityLoggingDelegate() throws Exception {
        installAppAsUser(DELEGATE_APP_APK, mUserId);
        installAppAsUser(DEVICE_ADMIN_APK, mPrimaryUserId);
        try {
            runDeviceTestsAsUser(DELEGATE_APP_PKG, ".SecurityLoggingDelegateTest",
                    "testCannotAccessApis", mUserId);
            // Set security logging delegate
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".SecurityLoggingTest",
                    "testSetDelegateScope_delegationSecurityLogging", mUserId);

            testSecurityLoggingOnWorkProfile(DELEGATE_APP_PKG,
                    ".SecurityLoggingDelegateTest");
        } finally {
            // Remove security logging delegate
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".SecurityLoggingTest",
                    "testSetDelegateScope_noDelegation", mUserId);
        }
    }

    private void testSecurityLoggingOnWorkProfile(String packageName, String testClassName)
            throws Exception {
        // Backup stay awake setting because testGenerateLogs() will turn it off.
        final String stayAwake = getDevice().getSetting("global", "stay_on_while_plugged_in");
        try {
            // Turn logging on.
            runDeviceTestsAsUser(packageName, testClassName,
                    "testEnablingSecurityLogging", mUserId);

            // Ensure user is initialized before rebooting, otherwise it won't start.
            waitForUserInitialized(mUserId);
            // Wait until idle so that the flag is persisted to disk.
            waitForBroadcastIdle();
            // Reboot to ensure ro.organization_owned is set to true in logd and logging is on.
            rebootAndWaitUntilReady();
            waitForUserUnlock(mUserId);

            // Generate various types of events on device side and check that they are logged.
            runDeviceTestsAsUser(packageName, testClassName, "testGenerateLogs", mUserId);
            getDevice().executeShellCommand("whoami"); // Generate adb command securty event
            runDeviceTestsAsUser(packageName, testClassName, "testVerifyGeneratedLogs", mUserId);

            // Immediately attempting to fetch events again should fail.
            runDeviceTestsAsUser(packageName, testClassName,
                    "testSecurityLoggingRetrievalRateLimited", mUserId);
        } finally {
            // Turn logging off.
            runDeviceTestsAsUser(packageName, testClassName,
                    "testDisablingSecurityLogging", mUserId);
            // Restore stay awake setting.
            if (stayAwake != null) {
                getDevice().setSetting("global", "stay_on_while_plugged_in", stayAwake);
            }
        }
    }

    private void waitForUserInitialized(int userId) throws Exception {
        final long start = System.nanoTime();
        final long deadline = start + TimeUnit.MINUTES.toNanos(5);
        while ((getUserFlags(userId) & FLAG_INITIALIZED) == 0) {
            if (System.nanoTime() > deadline) {
                fail("Timed out waiting for user to become initialized");
            }
            RunUtil.getDefault().sleep(100);
        }
    }

    @LargeTest
    @Test
    @Ignore("b/145932189")
    public void testSystemUpdatePolicy() throws Exception {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".systemupdate.SystemUpdatePolicyTest", mUserId);
    }

    @Test
    public void testInstallUpdate() throws Exception {
        pushUpdateFileToDevice("notZip.zi");
        pushUpdateFileToDevice("empty.zip");
        pushUpdateFileToDevice("wrongPayload.zip");
        pushUpdateFileToDevice("wrongHash.zip");
        pushUpdateFileToDevice("wrongSize.zip");
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".systemupdate.InstallUpdateTest", mUserId);
    }

    @Test
    public void testIsDeviceOrganizationOwnedWithManagedProfile() throws Exception {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DeviceOwnershipTest",
                "testCallingIsOrganizationOwnedWithManagedProfileExpectingTrue",
                mUserId);

        installAppAsUser(DEVICE_ADMIN_APK, mPrimaryUserId);
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DeviceOwnershipTest",
                "testCallingIsOrganizationOwnedWithManagedProfileExpectingTrue",
                mPrimaryUserId);
    }

    @Test
    public void testCommonCriteriaMode() throws Exception {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".CommonCriteriaModeTest", mUserId);
    }

    private void removeOrgOwnedProfile() throws Exception {
        sendWipeProfileBroadcast(mUserId);
        waitUntilUserRemoved(mUserId);
    }

    private void sendWipeProfileBroadcast(int userId) throws Exception {
        final String cmd = "am broadcast --receiver-foreground --user " + userId
                + " -a " + ACTION_WIPE_DATA
                + " com.android.cts.deviceandprofileowner/.WipeDataReceiver";
        getDevice().executeShellCommand(cmd);
    }

    private void setPersonalAppsSuspended(boolean suspended) throws DeviceNotAvailableException {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".PersonalAppsSuspensionTest",
                suspended ? "testSuspendPersonalApps" : "testUnsuspendPersonalApps", mUserId);
    }

    private void addSmsRole(String app, int userId) throws Exception {
        executeShellCommand(String.format(
                "cmd role add-role-holder --user %d android.app.role.SMS %s", userId, app));
    }

    private void removeSmsRole(String app, int userId) throws Exception {
        executeShellCommand(String.format(
                "cmd role remove-role-holder --user %d android.app.role.SMS %s", userId, app));
    }

    @Test
    public void testPersonalAppsSuspensionIme() throws Exception {
        installAppAsUser(TEST_IME_APK, mPrimaryUserId);
        setupIme(TEST_IME_COMPONENT, mPrimaryUserId);
        setPersonalAppsSuspended(true);
        // Active IME should not be suspended.
        assertCanStartPersonalApp(TEST_IME_PKG, true);
        setPersonalAppsSuspended(false);
    }

    @Test
    public void testPermittedInputMethods() throws Exception {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".InputMethodsTest", mUserId);
    }

    @Test
    public void testPermittedInputMethodsLogged() throws Exception {
        assertMetricsLogged(getDevice(), () ->
                        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".InputMethodsTest",
                                "testPermittedInputMethodsOnParent", mUserId),
                new DevicePolicyEventWrapper.Builder(EventId.SET_PERMITTED_INPUT_METHODS_VALUE)
                        .setAdminPackageName(DEVICE_ADMIN_PKG)
                        .setStrings(CALLED_FROM_PARENT, new String[0])
                        .build(),
                new DevicePolicyEventWrapper.Builder(EventId.SET_PERMITTED_INPUT_METHODS_VALUE)
                        .setAdminPackageName(DEVICE_ADMIN_PKG)
                        .setStrings(CALLED_FROM_PARENT, new String[0])
                        .build());
    }

    private void setupIme(String imeComponent, int userId) throws Exception {
        // Wait until IMS service is registered by the system.
        waitForOutput("Failed waiting for IME to become available",
                String.format("ime list --user %d -s -a", userId),
                s -> s.contains(imeComponent), 100 /* seconds */);

        executeShellCommand("ime enable " + imeComponent);
        executeShellCommand("ime set " + imeComponent);
    }

    private void assertCanStartPersonalApp(String packageName, boolean canStart)
            throws DeviceNotAvailableException {
        runDeviceTestsAsUser(packageName, "com.android.cts.suspensionchecker.ActivityLaunchTest",
                canStart ? "testCanStartActivity" : "testCannotStartActivity", mPrimaryUserId);
    }

    private void assertHasNoUser(int userId) throws DeviceNotAvailableException {
        int numWaits = 0;
        final int MAX_NUM_WAITS = 15;
        while (listUsers().contains(userId) && (numWaits < MAX_NUM_WAITS)) {
            RunUtil.getDefault().sleep(1000);
            numWaits += 1;
        }

        assertThat(listUsers()).doesNotContain(userId);
    }

    private void setPoAsUser(int userId) throws Exception {
        installAppAsUser(DEVICE_ADMIN_APK, true, true, userId);
        assertTrue("Failed to set profile owner",
                setProfileOwner(DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS,
                        userId, /* expectFailure */ false));
    }

    @Test
    public void testSetPersonalAppsSuspendedLogged() throws Exception {
        assertMetricsLogged(getDevice(), () -> {
                    runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DevicePolicyLoggingTest",
                            "testSetPersonalAppsSuspendedLogged", mUserId);
                }, new DevicePolicyEventWrapper.Builder(EventId.SET_PERSONAL_APPS_SUSPENDED_VALUE)
                        .setAdminPackageName(DEVICE_ADMIN_PKG)
                        .setBoolean(true)
                        .build(),
                new DevicePolicyEventWrapper.Builder(EventId.SET_PERSONAL_APPS_SUSPENDED_VALUE)
                        .setAdminPackageName(DEVICE_ADMIN_PKG)
                        .setBoolean(false)
                        .build());
    }

    @Test
    public void testSetManagedProfileMaximumTimeOffLogged() throws Exception {
        assertMetricsLogged(getDevice(), () -> {
                    runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".PersonalAppsSuspensionTest",
                            "testSetManagedProfileMaximumTimeOff", mUserId);
                }, new DevicePolicyEventWrapper.Builder(
                        EventId.SET_MANAGED_PROFILE_MAXIMUM_TIME_OFF_VALUE)
                        .setAdminPackageName(DEVICE_ADMIN_PKG)
                        .setTimePeriod(123456789)
                        .build(),
                new DevicePolicyEventWrapper.Builder(
                        EventId.SET_MANAGED_PROFILE_MAXIMUM_TIME_OFF_VALUE)
                        .setAdminPackageName(DEVICE_ADMIN_PKG)
                        .setTimePeriod(0)
                        .build());
    }

    @Test
    public void testWorkProfileMaximumTimeOff_complianceRequiredBroadcastDefault()
            throws Exception {
        installAppAsUser(DEVICE_ADMIN_APK, mPrimaryUserId);
        // Very long timeout, won't be triggered
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".PersonalAppsSuspensionTest",
                "testSetManagedProfileMaximumTimeOff1Year", mUserId);

        try {
            toggleQuietMode(true);
            waitForBroadcastIdle();
            toggleQuietMode(false);
            // Ensure the DPC has handled the broadcast
            waitForBroadcastIdle();
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".PersonalAppsSuspensionTest",
                    "testComplianceAcknowledgementRequiredReceived", mUserId);

            // Ensure that the default onComplianceAcknowledgementRequired acknowledged compliance.
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".PersonalAppsSuspensionTest",
                    "testComplianceAcknowledgementNotRequired", mUserId);

        } finally {
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".PersonalAppsSuspensionTest",
                    "testClearComplianceSharedPreference", mUserId);
        }
    }

    @Test
    public void testWorkProfileMaximumTimeOff_complianceRequiredBroadcastOverride()
            throws Exception {
        installAppAsUser(DEVICE_ADMIN_APK, mPrimaryUserId);
        // Very long timeout, won't be triggered
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".PersonalAppsSuspensionTest",
                "testSetManagedProfileMaximumTimeOff1Year", mUserId);
        // Set shared preference that instructs the receiver to NOT call default implementation.
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".PersonalAppsSuspensionTest",
                "testSetOverrideOnComplianceAcknowledgementRequired", mUserId);

        try {
            toggleQuietMode(true);
            waitForBroadcastIdle();
            toggleQuietMode(false);
            // Ensure the DPC has handled the broadcast
            waitForBroadcastIdle();
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".PersonalAppsSuspensionTest",
                    "testComplianceAcknowledgementRequiredReceived", mUserId);

            // Ensure compliance wasn't acknowledged automatically, acknowledge explicitly.
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".PersonalAppsSuspensionTest",
                    "testAcknowledgeCompliance", mUserId);
        } finally {
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".PersonalAppsSuspensionTest",
                    "testClearComplianceSharedPreference", mUserId);
        }
    }

    @Test
    public void testDelegatedCertInstallerDeviceIdAttestation() throws Exception {
        installAppAsUser(CERT_INSTALLER_APK, mUserId);

        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DelegatedCertInstallerHelper",
                "testManualSetCertInstallerDelegate", mUserId);

        runDeviceTestsAsUser(CERT_INSTALLER_PKG, ".DelegatedDeviceIdAttestationTest",
                "testGenerateKeyPairWithDeviceIdAttestationExpectingSuccess", mUserId);
    }

    @Test
    public void testDeviceIdAttestationForProfileOwner() throws Exception {
        // Test that Device ID attestation works for org-owned profile owner.
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DeviceIdAttestationTest",
                "testSucceedsWithProfileOwnerIdsGrant", mUserId);

    }

    @Test
    public void testNetworkLogging() throws Exception {
        installAppAsUser(DEVICE_ADMIN_APK, mPrimaryUserId);
        testNetworkLoggingOnWorkProfile(DEVICE_ADMIN_PKG, ".NetworkLoggingTest");
    }

    @Test
    public void testNetworkLoggingDelegate() throws Exception {
        installAppAsUser(DELEGATE_APP_APK, mUserId);
        installAppAsUser(DEVICE_ADMIN_APK, mPrimaryUserId);
        try {
            runDeviceTestsAsUser(DELEGATE_APP_PKG, ".WorkProfileNetworkLoggingDelegateTest",
                    "testCannotAccessApis", mUserId);
            // Set network logging delegate
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".NetworkLoggingTest",
                    "testSetDelegateScope_delegationNetworkLogging", mUserId);

            testNetworkLoggingOnWorkProfile(DELEGATE_APP_PKG,
                    ".WorkProfileNetworkLoggingDelegateTest");
        } finally {
            // Remove network logging delegate
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".NetworkLoggingTest",
                    "testSetDelegateScope_noDelegation", mUserId);
        }
    }

    private void testNetworkLoggingOnWorkProfile(String packageName, String testClassName)
            throws Exception {
        try {
            // Turn network logging on.
            runDeviceTestsAsUser(packageName, testClassName,
                    "testSetNetworkLogsEnabled_true", mUserId);

            // Connect to websites from work profile, should be logged.
            runDeviceTestsAsUser(packageName, testClassName,
                    "testConnectToWebsites_shouldBeLogged", mUserId);
            // Connect to websites from personal profile, should not be logged.
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".NetworkLoggingTest",
                    "testConnectToWebsites_shouldNotBeLogged", mPrimaryUserId);

            // Verify all work profile network logs have been received.
            runDeviceTestsAsUser(packageName, testClassName,
                    "testRetrieveNetworkLogs_forceNetworkLogs_receiveNetworkLogs", mUserId);
        } finally {
            // Turn network logging off.
            runDeviceTestsAsUser(packageName, testClassName,
                    "testSetNetworkLogsEnabled_false", mUserId);
        }
    }

    @Test
    public void testNetworkLoggingLogged() throws Exception {
        installAppAsUser(DEVICE_ADMIN_APK, mPrimaryUserId);
        assertMetricsLogged(getDevice(), () -> {
            testNetworkLoggingOnWorkProfile(DEVICE_ADMIN_PKG, ".NetworkLoggingTest");
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_NETWORK_LOGGING_ENABLED_VALUE)
                .setAdminPackageName(DEVICE_ADMIN_PKG)
                .setBoolean(false)
                .setInt(1)
                .setStrings(LOG_TAG_PROFILE_OWNER)
                .build(),
           new DevicePolicyEventWrapper.Builder(EventId.RETRIEVE_NETWORK_LOGS_VALUE)
                .setAdminPackageName(DEVICE_ADMIN_PKG)
                .setBoolean(false)
                .setStrings(LOG_TAG_PROFILE_OWNER)
                .build(),
           new DevicePolicyEventWrapper.Builder(EventId.SET_NETWORK_LOGGING_ENABLED_VALUE)
                .setAdminPackageName(DEVICE_ADMIN_PKG)
                .setBoolean(false)
                .setInt(0)
                .setStrings(LOG_TAG_PROFILE_OWNER)
                .build());
    }

    private void toggleQuietMode(boolean quietModeEnable) throws Exception {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".PersonalAppsSuspensionTest",
                quietModeEnable ? "testEnableQuietMode" : "testDisableQuietMode", mPrimaryUserId);

        boolean keepProfilesRunning = executeShellCommand("dumpsys device_policy")
                .contains("Keep profiles running: true");
        if (!keepProfilesRunning) {
            if (quietModeEnable) {
                waitForUserStopped(mUserId);
            } else {
                waitForUserUnlock(mUserId);
            }
        }
    }

    private void setAndStartLauncher(String component) throws Exception {
        String output = getDevice().executeShellCommand(String.format(
                "cmd package set-home-activity --user %d %s", mPrimaryUserId, component));
        assertTrue("failed to set home activity", output.contains("Success"));
        executeShellCommand("am start -W -n " + component);
    }

    private void waitForUserStopped(int userId) throws Exception {
        waitForOutput("User is not unlocked.",
                String.format("am get-started-user-state %d", userId),
                s -> s.startsWith(USER_IS_NOT_STARTED), USER_STOP_TIMEOUT_SEC);
    }
}
