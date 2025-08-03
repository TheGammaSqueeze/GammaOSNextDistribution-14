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

package com.android.cts.devicepolicy;

import static com.android.cts.devicepolicy.metrics.DevicePolicyEventLogVerifier.assertMetricsLogged;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import android.platform.test.annotations.AsbSecurityTest;
import android.platform.test.annotations.FlakyTest;
import android.platform.test.annotations.LargeTest;
import android.stats.devicepolicy.EventId;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.cts.devicepolicy.metrics.DevicePolicyEventWrapper;
import com.android.tradefed.log.LogUtil.CLog;

import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Set of tests for Device Owner use cases.
 */
public final class DeviceOwnerTest extends BaseDeviceOwnerTest {

    private static final String MANAGED_PROFILE_PKG = "com.android.cts.managedprofile";
    private static final String MANAGED_PROFILE_APK = "CtsManagedProfileApp.apk";
    private static final String MANAGED_PROFILE_ADMIN =
            MANAGED_PROFILE_PKG + ".BaseManagedProfileTest$BasicAdminReceiver";

    private static final String INTENT_RECEIVER_APK = "CtsIntentReceiverApp.apk";

    private static final String SIMPLE_APP_APK ="CtsSimpleApp.apk";
    private static final String SIMPLE_APP_PKG = "com.android.cts.launcherapps.simpleapp";
    private static final String SIMPLE_APP_ACTIVITY = SIMPLE_APP_PKG + ".SimpleActivity";

    private static final String WIFI_CONFIG_CREATOR_PKG =
            "com.android.cts.deviceowner.wificonfigcreator";
    private static final String WIFI_CONFIG_CREATOR_APK = "CtsWifiConfigCreator.apk";

    private static final String TEST_APP_APK = "CtsEmptyTestApp.apk";
    private static final String TEST_APP_PKG = "android.packageinstaller.emptytestapp.cts";
    private static final String TEST_APP_LOCATION = "/data/local/tmp/cts/packageinstaller/";

    private static final String ARG_NETWORK_LOGGING_BATCH_COUNT = "batchCount";
    private static final String ARG_PID_BEFORE_STOP = "pidOfSimpleapp";

    private static final int TYPE_NONE = 0;

    /**
     * Copied from {@link android.app.admin.SystemUpdatePolicy}
     */
    private static final int TYPE_INSTALL_AUTOMATIC = 1;
    private static final int TYPE_INSTALL_WINDOWED = 2;
    private static final int TYPE_POSTPONE = 3;

    /**
     * Copied from {@link android.app.admin.DevicePolicyManager}
     */
    private static final String GLOBAL_SETTING_AUTO_TIME = "auto_time";
    private static final String GLOBAL_SETTING_AUTO_TIME_ZONE = "auto_time_zone";
    private static final String GLOBAL_SETTING_DATA_ROAMING = "data_roaming";
    private static final String GLOBAL_SETTING_USB_MASS_STORAGE_ENABLED =
            "usb_mass_storage_enabled";

    @Test
    public void testProxyStaticProxyTest() throws Exception {
        executeDeviceOwnerTest("proxy.StaticProxyTest");
    }

    @Test
    public void testProxyPacProxyTest() throws Exception {
        assumeFalse("Test does not apply to WearOS", mIsWatch);
        executeDeviceOwnerTest("proxy.PacProxyTest");
    }

    @Test
    public void testCreateAndManageUser_MaxUsers() throws Exception {
        assumeCanCreateOneManagedUser();

        int maxUsers = getDevice().getMaxNumberOfUsersSupported();

        // System user is already there, so we can create up to maxUsers - 1.
        int existingUsers = 1;

        // On headless user mode, current user is also there
        if (isHeadlessSystemUserMode()) {
            existingUsers++;
        }

        CLog.d("testCreateAndManageUser_MaxUsers(): maxUxers=%d, existingUsers=%d", maxUsers,
                existingUsers);

        for (int i = 0; i < maxUsers - existingUsers; i++) {
            executeCreateAndManageUserTest("testCreateAndManageUser");
        }
        // The next createAndManageUser should return USER_OPERATION_ERROR_MAX_USERS.
        executeCreateAndManageUserTest("testCreateAndManageUser_MaxUsers");
    }

    /**
     * Test creating an user using the DevicePolicyManager's createAndManageUser.
     * {@link android.app.admin.DevicePolicyManager#getSecondaryUsers} is tested.
     */
    @Test
    public void testCreateAndManageUser_GetSecondaryUsers() throws Exception {
        assumeCanCreateOneManagedUser();

        executeCreateAndManageUserTest("testCreateAndManageUser_GetSecondaryUsers");
    }

    /**
     * Test creating an user using the DevicePolicyManager's createAndManageUser method and switch
     * to the user.
     * {@link android.app.admin.DevicePolicyManager#switchUser} is tested.
     */
    @FlakyTest(bugId = 131743223)
    @Test
    public void testCreateAndManageUser_SwitchUser() throws Exception {
        assumeCanStartNewUser();

        executeCreateAndManageUserTest("testCreateAndManageUser_SwitchUser");
    }

    /**
     * Test creating an user using the DevicePolicyManager's createAndManageUser method and switch
     * to the user to test stop user while target user is in foreground.
     * {@link android.app.admin.DevicePolicyManager#stopUser} is tested.
     */
    @Test
    public void testCreateAndManageUser_CannotStopCurrentUser() throws Exception {
        assumeCanStartNewUser();

        executeCreateAndManageUserTest("testCreateAndManageUser_CannotStopCurrentUser");
    }

    /**
     * Test creating an user using the DevicePolicyManager's createAndManageUser method and start
     * the user in background to test APIs on that user.
     * {@link android.app.admin.DevicePolicyManager#startUserInBackground} is tested.
     */
    @Test
    public void testCreateAndManageUser_StartInBackground() throws Exception {
        assumeCanStartNewUser();

        executeCreateAndManageUserTest("testCreateAndManageUser_StartInBackground");
    }

    /**
     * Test creating an user using the DevicePolicyManager's createAndManageUser method and start
     * the user in background to test APIs on that user.
     * {@link android.app.admin.DevicePolicyManager#startUserInBackground} is tested.
     */
    @Test
    public void testCreateAndManageUser_StartInBackground_MaxRunningUsers() throws Exception {
        assumeCanStartNewUser();

        int maxUsers = getDevice().getMaxNumberOfUsersSupported();
        int maxRunningUsers = getDevice().getMaxNumberOfRunningUsersSupported();

        // Primary user is already running, so we can create and start up to minimum of above - 1.
        int usersToCreateAndStart = Math.min(maxUsers, maxRunningUsers) - 1;

        // On headless user mode, system user is also running
        if (isHeadlessSystemUserMode()) {
            usersToCreateAndStart--;
        }

        CLog.d("testCreateAndManageUser_StartInBackground_MaxRunningUsers(): maxUxers=%d, "
                + "maxRunningUsers=%d, usersToCreateAndStart=%d", maxUsers, maxRunningUsers,
                usersToCreateAndStart);
        for (int i = 0; i < usersToCreateAndStart; i++) {
            executeCreateAndManageUserTest("testCreateAndManageUser_StartInBackground");
        }

        if (maxUsers > maxRunningUsers) {
            // The next startUserInBackground should return USER_OPERATION_ERROR_MAX_RUNNING_USERS.
            executeCreateAndManageUserTest(
                    "testCreateAndManageUser_StartInBackground_MaxRunningUsers");
        } else {
            // The next createAndManageUser should return USER_OPERATION_ERROR_MAX_USERS.
            executeCreateAndManageUserTest("testCreateAndManageUser_MaxUsers");
        }
    }

    /**
     * Test creating an user using the DevicePolicyManager's createAndManageUser method and start
     * the user in background to test APIs on that user.
     * {@link android.app.admin.DevicePolicyManager#stopUser} is tested.
     */
    @Test
    public void testCreateAndManageUser_StopUser() throws Exception {
        assumeCanStartNewUser();

        executeCreateAndManageUserTest("testCreateAndManageUser_StopUser");
        assertNewUserStopped();
    }

    /**
     * Test creating an ephemeral user using the DevicePolicyManager's createAndManageUser method
     * and start the user in background, user is then stopped. The user should be removed
     * automatically even when DISALLOW_REMOVE_USER is set.
     */
    @Test
    public void testCreateAndManageUser_StopEphemeralUser_DisallowRemoveUser() throws Exception {
        assumeCanStartNewUser();

        executeCreateAndManageUserTest(
                "testCreateAndManageUser_StopEphemeralUser_DisallowRemoveUser");
        assertEquals(0, getUsersCreatedByTests().size());
    }

    /**
     * Test creating an user using the DevicePolicyManager's createAndManageUser method, affiliate
     * the user and start the user in background to test APIs on that user.
     * {@link android.app.admin.DevicePolicyManager#logoutUser} is tested.
     */
    @Test
    public void testCreateAndManageUser_LogoutUser() throws Exception {
        assumeCanStartNewUser();

        executeCreateAndManageUserTest("testCreateAndManageUser_LogoutUser");
        assertNewUserStopped();
    }

    /**
     * Tests creating a user using the DevicePolicyManager's createAndManageUser method, affiliates
     * the user and starts the user in background to test APIs on that user.
     *
     * <p>{@link android.app.admin.DevicePolicyManager#logoutUser} (system API version) is tested.
     */
    @Test
    public void testCreateAndManageUser_LogoutUser_systemApi() throws Exception {
        assumeCanStartNewUser();

        executeCreateAndManageUserTest("testCreateAndManageUser_LogoutUser_systemApi");
        assertNewUserStopped();
    }

    /**
     * Test creating an user using the DevicePolicyManager's createAndManageUser method, affiliate
     * the user and start the user in background to test APIs on that user.
     * {@link android.app.admin.DevicePolicyManager#isAffiliatedUser} is tested.
     */
    @Test
    public void testCreateAndManageUser_Affiliated() throws Exception {
        assumeCanStartNewUser();

        executeCreateAndManageUserTest("testCreateAndManageUser_Affiliated");
    }

    /**
     * Test creating an ephemeral user using the DevicePolicyManager's createAndManageUser method,
     * affiliate the user and start the user in background to test APIs on that user.
     * {@link android.app.admin.DevicePolicyManager#isEphemeralUser} is tested.
     */
    @Test
    public void testCreateAndManageUser_Ephemeral() throws Exception {
        assumeCanStartNewUser();

        executeCreateAndManageUserTest("testCreateAndManageUser_Ephemeral");

        List<Integer> newUsers = getUsersCreatedByTests();
        assertEquals(1, newUsers.size());
        int newUserId = newUsers.get(0);

        // Get the flags of the new user and check the user is ephemeral.
        int flags = getUserFlags(newUserId);
        assertEquals("Ephemeral flag must be set", FLAG_EPHEMERAL, flags & FLAG_EPHEMERAL);
    }

    /**
     * Test creating an user using the DevicePolicyManager's createAndManageUser method, affiliate
     * the user and start the user in background to test APIs on that user.
     * {@link android.app.admin.DevicePolicyManager#LEAVE_ALL_SYSTEM_APPS_ENABLED} is tested.
     */
    @Test
   public void testCreateAndManageUser_LeaveAllSystemApps() throws Exception {
        assumeCanStartNewUser();

        executeCreateAndManageUserTest("testCreateAndManageUser_LeaveAllSystemApps");
    }

    @Test
    public void testCreateAndManageUser_SkipSetupWizard() throws Exception {
        assumeCanCreateOneManagedUser();

        executeCreateAndManageUserTest("testCreateAndManageUser_SkipSetupWizard");
    }

    @Test
    public void testCreateAndManageUser_AddRestrictionSet() throws Exception {
        assumeCanCreateOneManagedUser();

        executeCreateAndManageUserTest("testCreateAndManageUser_AddRestrictionSet");
    }

    @Test
    public void testCreateAndManageUser_RemoveRestrictionSet() throws Exception {
        assumeCanCreateOneManagedUser();

        executeCreateAndManageUserTest("testCreateAndManageUser_RemoveRestrictionSet");
    }

    @FlakyTest(bugId = 126955083)
    @Test
    public void testUserAddedOrRemovedBroadcasts() throws Exception {
        assumeCanCreateOneManagedUser();

        executeCreateAndManageUserTest("testUserAddedOrRemovedBroadcasts");
    }

    @Test
    public void testUserSession() throws Exception {
        executeDeviceOwnerTest("UserSessionTest");
    }

    @Test
    public void testNetworkLoggingWithTwoUsers() throws Exception {
        assumeCanCreateAdditionalUsers(1);

        final int userId = createUser();
        try {
            // The feature can be enabled, but in a "paused" state. Attempting to retrieve logs
            // should throw security exception.
            executeDeviceTestMethod(".NetworkLoggingTest",
                    "testRetrievingNetworkLogsThrowsSecurityException");
        } finally {
            removeUser(userId);
        }
    }

    @FlakyTest(bugId = 137092833)
    @Test
    public void testNetworkLoggingWithSingleUser() throws Exception {
        executeDeviceTestMethod(".NetworkLoggingTest", "testProvidingWrongBatchTokenReturnsNull");
        executeDeviceTestMethod(".NetworkLoggingTest", "testNetworkLoggingAndRetrieval",
                Collections.singletonMap(ARG_NETWORK_LOGGING_BATCH_COUNT, Integer.toString(1)));
    }

    @Test
    public void testNetworkLogging_multipleBatches() throws Exception {
        executeDeviceTestMethod(".NetworkLoggingTest", "testNetworkLoggingAndRetrieval",
                Collections.singletonMap(ARG_NETWORK_LOGGING_BATCH_COUNT, Integer.toString(2)));
    }

    @LargeTest
    @Test
    public void testNetworkLogging_rebootResetsId() throws Exception {
        // First batch: retrieve and verify the events.
        executeDeviceTestMethod(".NetworkLoggingTest", "testNetworkLoggingAndRetrieval",
                Collections.singletonMap(ARG_NETWORK_LOGGING_BATCH_COUNT, Integer.toString(1)));
        // Reboot the device, so the security event IDs are re-set.
        rebootAndWaitUntilReady();
        // Make sure BOOT_COMPLETED is completed before proceeding.
        waitForBroadcastIdle();
        // First batch after reboot: retrieve and verify the events.
        executeDeviceTestMethod(".NetworkLoggingTest", "testNetworkLoggingAndRetrieval",
                Collections.singletonMap(ARG_NETWORK_LOGGING_BATCH_COUNT, Integer.toString(1)));
    }


    @Test
    public void testSetAffiliationId_IllegalArgumentException() throws Exception {
        executeDeviceTestMethod(".AffiliationTest", "testSetAffiliationId_null");
        executeDeviceTestMethod(".AffiliationTest", "testSetAffiliationId_containsEmptyString");
    }

    @Test
    @Ignore("b/145932189")
    public void testSetSystemUpdatePolicyLogged() throws Exception {
        assertMetricsLogged(getDevice(), () -> {
            executeDeviceTestMethod(".SystemUpdatePolicyTest", "testSetAutomaticInstallPolicy");
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_SYSTEM_UPDATE_POLICY_VALUE)
                    .setAdminPackageName(DEVICE_OWNER_PKG)
                    .setInt(TYPE_INSTALL_AUTOMATIC)
                    .build());
        assertMetricsLogged(getDevice(), () -> {
            executeDeviceTestMethod(".SystemUpdatePolicyTest", "testSetWindowedInstallPolicy");
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_SYSTEM_UPDATE_POLICY_VALUE)
                    .setAdminPackageName(DEVICE_OWNER_PKG)
                    .setInt(TYPE_INSTALL_WINDOWED)
                    .build());
        assertMetricsLogged(getDevice(), () -> {
            executeDeviceTestMethod(".SystemUpdatePolicyTest", "testSetPostponeInstallPolicy");
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_SYSTEM_UPDATE_POLICY_VALUE)
                    .setAdminPackageName(DEVICE_OWNER_PKG)
                    .setInt(TYPE_POSTPONE)
                    .build());
        assertMetricsLogged(getDevice(), () -> {
            executeDeviceTestMethod(".SystemUpdatePolicyTest", "testSetEmptytInstallPolicy");
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_SYSTEM_UPDATE_POLICY_VALUE)
                    .setAdminPackageName(DEVICE_OWNER_PKG)
                    .setInt(TYPE_NONE)
                    .build());
    }

    @FlakyTest(bugId = 127101449)
    @Test
    public void testWifiConfigLockdown() throws Exception {
        assumeHasWifiFeature();

        try (LocationModeSetter locationModeSetter = new LocationModeSetter(getDevice())) {
            installAppAsUser(WIFI_CONFIG_CREATOR_APK, mPrimaryUserId);
            locationModeSetter.setLocationEnabled(true);
            executeDeviceOwnerTest("WifiConfigLockdownTest");
        } finally {
            getDevice().uninstallPackage(WIFI_CONFIG_CREATOR_PKG);
        }
    }

    /**
     * Execute WifiSetHttpProxyTest as device owner.
     */
    @Test
    public void testWifiSetHttpProxyTest() throws Exception {
        assumeHasWifiFeature();
        try (LocationModeSetter locationModeSetter = new LocationModeSetter(getDevice())) {
            locationModeSetter.setLocationEnabled(true);
            executeDeviceOwnerTest("WifiSetHttpProxyTest");
        }
    }

    @Test
    public void testCannotSetDeviceOwnerAgain() throws Exception {
        // verify that we can't set the same admin receiver as device owner again
        assertFalse(setDeviceOwner(
                DEVICE_OWNER_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mPrimaryUserId,
                /*expectFailure*/ true));

        // verify that we can't set a different admin receiver as device owner
        try {
            installAppAsUser(MANAGED_PROFILE_APK, mPrimaryUserId);
            assertFalse(setDeviceOwner(
                    MANAGED_PROFILE_PKG + "/" + MANAGED_PROFILE_ADMIN, mPrimaryUserId,
                    /*expectFailure*/ true));
        } finally {
            // Remove the device owner in case the test fails.
            removeAdmin(MANAGED_PROFILE_PKG + "/" + MANAGED_PROFILE_ADMIN, mPrimaryUserId);
            getDevice().uninstallPackage(MANAGED_PROFILE_PKG);
        }
    }

    // Execute HardwarePropertiesManagerTest as a device owner.
    @Test
    public void testHardwarePropertiesManagerAsDeviceOwner() throws Exception {

        executeDeviceTestMethod(".HardwarePropertiesManagerTest", "testHardwarePropertiesManager");
    }

    // Execute VrTemperatureTest as a device owner.
    @Test
    public void testVrTemperaturesAsDeviceOwner() throws Exception {

        executeDeviceTestMethod(".VrTemperatureTest", "testVrTemperatures");
    }

    @Test
    public void testIsManagedDeviceProvisioningAllowed() throws Exception {
        // This case runs when DO is provisioned
        // mHasFeature == true and provisioned, can't provision DO again.
        executeDeviceTestMethod(".PreDeviceOwnerTest", "testIsProvisioningAllowedFalse");
    }

    @FlakyTest(bugId = 137096267)
    @Test
    public void testAdminActionBookkeeping() throws Exception {
        if (isHeadlessSystemUserMode()) {
            // TODO(b/176993670):ALLOW_TEST_API_ACCESS is needed by DevicePolicyManagerWrapper to
            // access test apis
            allowTestApiAccess(DEVICE_OWNER_PKG);
            // This test has to run as system user since the test will get KeyStore instance for
            // current user.
            executeDeviceOwnerTestMethod(".AdminActionBookkeepingTest",
                    "testGetPolicyInstalledCaCerts");
        } else {
            // This test will be skipped for headless system user mode since headless system user
            // does not have IME.
            executeDeviceTestMethod(".AdminActionBookkeepingTest",
                    "testGetPolicyInstalledCaCerts");
        }

        executeDeviceTestMethod(".AdminActionBookkeepingTest",
                "testRetrieveSecurityLogs");
        executeDeviceTestMethod(".AdminActionBookkeepingTest",
                "testGetLastNetworkLogRetrievalTime");
        executeDeviceTestMethod(".AdminActionBookkeepingTest",
                "testDeviceOwnerOrganizationName");
        executeDeviceTestMethod(".AdminActionBookkeepingTest",
                "testIsDeviceManaged");

        assertMetricsLogged(getDevice(), () -> {
            executeDeviceTestMethod(".AdminActionBookkeepingTest", "testRetrieveSecurityLogs");
        }, new DevicePolicyEventWrapper.Builder(EventId.RETRIEVE_SECURITY_LOGS_VALUE)
                .setAdminPackageName(DEVICE_OWNER_PKG)
                .build(),
        new DevicePolicyEventWrapper.Builder(EventId.RETRIEVE_PRE_REBOOT_SECURITY_LOGS_VALUE)
                .setAdminPackageName(DEVICE_OWNER_PKG)
                .build());
    }

    @Test
    public void testBluetoothRestriction() throws Exception {
        executeDeviceOwnerTest("BluetoothRestrictionTest");
    }

    @Test
    public void testSetLocationEnabled() throws Exception {
        // Currently this method is only available for device owners, so on headless system user
        // it must run on system user.
        executeDeviceOwnerTestOnDeviceOwnerUser("SetLocationEnabledTest");
    }

    /**
     *  Only allow provisioning flow to be disabled if Android TV device
     */
    @Test
    public void testAllowProvisioningProperty() throws Exception {
        boolean isProvisioningAllowedForNormalUsers =
                getBooleanSystemProperty("ro.config.allowuserprovisioning", true);
        assertTrue(isProvisioningAllowedForNormalUsers || isTv());
    }

    @Test
    public void testDisallowFactoryReset() throws Exception {
        if (isHeadlessSystemUserMode()) {
            // Need to access dpm.getPolicyExemptApps() which is a TestApi.
            allowTestApiAccess(DEVICE_OWNER_PKG);
        }

        int adminVersion = 24;
        // NOTE: the restriction must be set on primary user as it will launch SetPolicyActivity,
        // but the admin must be installed on USER_SYSTEM, otherwise wipeData() on headless system
        // user mode would wipe the current user (instead of factory resetting the device)
        changeUserRestrictionOrFail("no_factory_reset", true, mPrimaryUserId, DEVICE_OWNER_PKG);
        int adminUserId = USER_SYSTEM;

        String deviceAdminPkg = DeviceAdminHelper.getDeviceAdminApkPackage(adminVersion);
        String deviceAdminReceiver = DeviceAdminHelper.getAdminReceiverComponent(adminVersion);
        try {
            installAppAsUser(DeviceAdminHelper.getDeviceAdminApkFileName(adminVersion),
                    adminUserId);
            setDeviceAdmin(deviceAdminReceiver, adminUserId);
            runDeviceTestsAsUser(
                    deviceAdminPkg,
                    DeviceAdminHelper.getDeviceAdminJavaPackage() + ".WipeDataTest",
                    "testWipeDataThrowsSecurityException", adminUserId);
        } finally {
            removeAdmin(deviceAdminReceiver, adminUserId);
            getDevice().uninstallPackage(deviceAdminPkg);
        }
    }

    @Test
    @AsbSecurityTest(cveBugId = 173421434)
    public void testDeviceOwnerCanGetDeviceIdentifiers() throws Exception {
        // The Device Owner should have access to all device identifiers.

        executeDeviceOwnerTestMethod(".DeviceIdentifiersTest",
                "testDeviceOwnerCanGetDeviceIdentifiersWithPermission");
    }

    @Test
    public void testPackageInstallCache() throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        final File apk = buildHelper.getTestFile(TEST_APP_APK);
        try {
            getDevice().uninstallPackage(TEST_APP_PKG);
            assertTrue(getDevice().pushFile(apk, TEST_APP_LOCATION + apk.getName()));

            // Install the package in primary user
            runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".PackageInstallTest",
                    "testPackageInstall", mPrimaryUserId);
            assertMetricsLogged(getDevice(), () -> {
                runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".PackageInstallTest",
                        "testKeepPackageCache", mPrimaryUserId);
            }, new DevicePolicyEventWrapper.Builder(EventId.SET_KEEP_UNINSTALLED_PACKAGES_VALUE)
                    .setAdminPackageName(DEVICE_OWNER_PKG)
                    .setBoolean(false)
                    .setStrings(TEST_APP_PKG)
                    .build());

            // Remove the package in primary user
            runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".PackageInstallTest",
                    "testPackageUninstall", mPrimaryUserId);

            assertMetricsLogged(getDevice(), () -> {
                // Should be able to enable the cached package in primary user
                runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".PackageInstallTest",
                        "testInstallExistingPackage", mPrimaryUserId);
            }, new DevicePolicyEventWrapper.Builder(EventId.INSTALL_EXISTING_PACKAGE_VALUE)
                    .setAdminPackageName(DEVICE_OWNER_PKG)
                    .setBoolean(false)
                    .setStrings(TEST_APP_PKG)
                    .build());
        } finally {
            String command = "rm " + TEST_APP_LOCATION + apk.getName();
            getDevice().executeShellCommand(command);
            getDevice().uninstallPackage(TEST_APP_PKG);
        }
    }

    @LargeTest
    @Test
    public void testPackageInstallCache_multiUser() throws Exception {
        assumeCanCreateAdditionalUsers(1);

        final int userId = createAffiliatedSecondaryUser();
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        final File apk = buildHelper.getTestFile(TEST_APP_APK);
        try {
            getDevice().uninstallPackage(TEST_APP_PKG);
            assertTrue(getDevice().pushFile(apk, TEST_APP_LOCATION + apk.getName()));

            // Install the package in primary user
            runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".PackageInstallTest",
                    "testPackageInstall", mPrimaryUserId);

            // Should be able to enable the package in secondary user
            runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".PackageInstallTest",
                    "testInstallExistingPackage", userId);

            // Remove the package in both user
            runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".PackageInstallTest",
                    "testPackageUninstall", mPrimaryUserId);
            runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".PackageInstallTest",
                    "testPackageUninstall", userId);

            // Install the package in secondary user
            runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".PackageInstallTest",
                    "testPackageInstall", userId);

            // Should be able to enable the package in primary user
            runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".PackageInstallTest",
                    "testInstallExistingPackage", mPrimaryUserId);

            // Keep the package in cache
            runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".PackageInstallTest",
                    "testKeepPackageCache", mPrimaryUserId);

            // Remove the package in both user
            runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".PackageInstallTest",
                    "testPackageUninstall", mPrimaryUserId);
            runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".PackageInstallTest",
                    "testPackageUninstall", userId);

            // Should be able to enable the cached package in both users
            runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".PackageInstallTest",
                    "testInstallExistingPackage", userId);
            runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".PackageInstallTest",
                    "testInstallExistingPackage", mPrimaryUserId);
        } finally {
            String command = "rm " + TEST_APP_LOCATION + apk.getName();
            getDevice().executeShellCommand(command);
            getDevice().uninstallPackage(TEST_APP_PKG);
        }
    }

    @Test
    public void testSensorToggleRestriction() throws Exception {
        executeDeviceOwnerTest("SensorToggleRestrictionTest");
    }

    @Test
    public void testOverrideApn() throws Exception {
        assumeHasTelephonyFeature();

        executeDeviceOwnerTest("OverrideApnTest");
    }

    @FlakyTest(bugId = 134487729)
    @Test
    public void testPrivateDnsPolicy() throws Exception {
        executeDeviceOwnerTest("PrivateDnsPolicyTest");
    }

    @Test
    public void testSetKeyguardDisabledLogged() throws Exception {
        assertMetricsLogged(getDevice(), () -> {
            executeDeviceTestMethod(".DevicePolicyLoggingTest", "testSetKeyguardDisabledLogged");
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_KEYGUARD_DISABLED_VALUE)
                .setAdminPackageName(DEVICE_OWNER_PKG)
                .build());
    }

    @Test
    public void testSetStatusBarDisabledLogged() throws Exception {
        assumeFalse("Test does not apply to WearOS", mIsWatch);
        assertMetricsLogged(getDevice(), () -> {
            executeDeviceTestMethod(".DevicePolicyLoggingTest", "testSetStatusBarDisabledLogged");
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_STATUS_BAR_DISABLED_VALUE)
                    .setAdminPackageName(DEVICE_OWNER_PKG)
                    .setBoolean(true)
                    .build(),
            new DevicePolicyEventWrapper.Builder(EventId.SET_STATUS_BAR_DISABLED_VALUE)
                    .setAdminPackageName(DEVICE_OWNER_PKG)
                    .setBoolean(true)
                    .build());
    }

    @Test
    public void testSetGlobalSettingLogged() throws Exception {
        assertMetricsLogged(getDevice(), () -> {
            executeDeviceTestMethod(".DevicePolicyLoggingTest", "testSetGlobalSettingLogged");
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_GLOBAL_SETTING_VALUE)
                .setAdminPackageName(DEVICE_OWNER_PKG)
                .setStrings(GLOBAL_SETTING_AUTO_TIME, "1")
                .build(),
        new DevicePolicyEventWrapper.Builder(EventId.SET_GLOBAL_SETTING_VALUE)
                .setAdminPackageName(DEVICE_OWNER_PKG)
                .setStrings(GLOBAL_SETTING_AUTO_TIME_ZONE, "1")
                .build(),
        new DevicePolicyEventWrapper.Builder(EventId.SET_GLOBAL_SETTING_VALUE)
                .setAdminPackageName(DEVICE_OWNER_PKG)
                .setStrings(GLOBAL_SETTING_DATA_ROAMING, "1")
                .build(),
        new DevicePolicyEventWrapper.Builder(EventId.SET_GLOBAL_SETTING_VALUE)
                .setAdminPackageName(DEVICE_OWNER_PKG)
                .setStrings(GLOBAL_SETTING_USB_MASS_STORAGE_ENABLED, "1")
                .build());
    }

    @Test
    public void testSetUserControlDisabledPackages_singleUser_reboot_verifyPackageNotStopped()
            throws Exception {
        try {
            installAppAsUser(SIMPLE_APP_APK, mPrimaryUserId);
            startProtectedPackage(mPrimaryUserId);
            // Set the package under test as a protected package.
            executeDeviceTestMethod(".UserControlDisabledPackagesTest",
                    "testSetUserControlDisabledPackages");

            // Reboot and verify protected packages are persisted
            rebootAndWaitUntilReady();

            // The simple app package seems to be set into stopped state on reboot.
            // Launch the activity again to get it out of stopped state on the primary user.
            startProtectedPackage(mPrimaryUserId);
            // Try to force-stop the package under test on the primary user.
            tryForceStoppingProtectedPackage(mPrimaryUserId, /* canUserStopPackage= */ false);
        } finally {
            // Clear the protected packages so that the package under test can be force-stopped.
            runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".UserControlDisabledPackagesTest",
                    "testClearSetUserControlDisabledPackages", mPrimaryUserId);
            tryForceStoppingProtectedPackage(mPrimaryUserId, /* canUserStopPackage= */ true);

            // Removal of the installed simple app on the primary user is done in tear down.
        }
    }

    @Test
    public void testSetUserControlDisabledPackages_singleUser_reboot_verifyPackageNotFgsStopped()
            throws Exception {
        try {
            installAppAsUser(SIMPLE_APP_APK, mPrimaryUserId);
            startProtectedPackage(mPrimaryUserId);
            // Set the package under test as a protected package.
            executeDeviceTestMethod(".UserControlDisabledPackagesTest",
                    "testSetUserControlDisabledPackages");

            // Reboot and verify protected packages are persisted
            rebootAndWaitUntilReady();

            // The simple app package seems to be set into stopped state on reboot.
            // Launch the activity again to get it out of stopped state on the primary user.
            startProtectedPackage(mPrimaryUserId);
            // Try to task-manager stop the package under test on the primary user.
            tryFgsStoppingProtectedPackage(mPrimaryUserId, /* canUserStopPackage= */ false);
        } finally {
            // Clear the protected packages so that the package under test can be stopped.
            runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".UserControlDisabledPackagesTest",
                    "testClearSetUserControlDisabledPackages", mPrimaryUserId);
            tryFgsStoppingProtectedPackage(mPrimaryUserId, /* canUserStopPackage= */ true);

            // Removal of the installed simple app on the primary user is done in tear down.
        }
    }

    @Test
    @Ignore("b/230738884")
    public void testSetUserControlDisabledPackages_multiUser_reboot_verifyPackageNotStopped()
            throws Exception {
        assumeCanCreateAdditionalUsers(1);
        final int userId = createUser();

        String stopBgUsersOnSwitchValue = getStopBgUsersOnSwitchProperty();
        try {
            // Set it to zero otherwise test will crash on automotive when switching users
            setStopBgUsersOnSwitchProperty("0");
            try {
                installAppAsUser(SIMPLE_APP_APK, userId);
                switchUser(userId);
                startProtectedPackage(userId);
                // Set the package under test as a protected package.
                runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".UserControlDisabledPackagesTest",
                        "testSetUserControlDisabledPackages", mPrimaryUserId);

                // Reboot and verify protected packages are persisted.
                CLog.i("Reboot");
                rebootAndWaitUntilReady();
                CLog.i("Device is ready");

                if (isHeadlessSystemUserMode()) {
                    // Device stars on last user, so we need to explicitly start the user running
                    // the tests
                    startUser(mPrimaryUserId);
                } else {
                    // Device starts on the primary user and not on the last user (i.e. the created
                    // user) before the reboot occurred.
                    switchUser(userId);
                }

                // The simple app package seems to be set into stopped state on reboot.
                // Launch the activity again to get it out of stopped state for the created user.
                startProtectedPackage(userId);
                // Try to force-stop the package under test on the created user.
                tryForceStoppingProtectedPackage(userId, /* canUserStopPackage= */ false);
            } finally {
                // Clear the protected packages so that the package under test can be force-stopped.
                runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".UserControlDisabledPackagesTest",
                        "testClearSetUserControlDisabledPackages", mPrimaryUserId);
                tryForceStoppingProtectedPackage(userId, /* canUserStopPackage= */ true);

                // Removal of the created user and the installed simple app on the created user are
                // done in tear down.
            }
        } finally {
            setStopBgUsersOnSwitchProperty(stopBgUsersOnSwitchValue);
        }
    }

    @Test
    @Ignore("b/204508654")
    public void testSetUserControlDisabledPackages_multiUser_reboot_verifyPackageNotFgsStopped()
            throws Exception {
        assumeCanCreateAdditionalUsers(1);
        final int userId = createUser();

        String stopBgUsersOnSwitchValue = getStopBgUsersOnSwitchProperty();
        try {
            // Set it to zero otherwise test will crash on automotive when switching users
            setStopBgUsersOnSwitchProperty("0");
            try {
                installAppAsUser(SIMPLE_APP_APK, userId);
                switchUser(userId);
                startProtectedPackage(userId);
                // Set the package under test as a protected package.
                runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".UserControlDisabledPackagesTest",
                        "testSetUserControlDisabledPackages", mPrimaryUserId);

                // Reboot and verify protected packages are persisted.
                CLog.i("Reboot");
                rebootAndWaitUntilReady();
                CLog.i("Device is ready");

                if (isHeadlessSystemUserMode()) {
                    // Device stars on last user, so we need to explicitly start the user running
                    // the tests
                    startUser(mPrimaryUserId);
                } else {
                    // Device starts on the primary user and not on the last user (i.e. the created
                    // user) before the reboot occurred.
                    switchUser(userId);
                }

                // The simple app package seems to be set into stopped state on reboot.
                // Launch the activity again to get it out of stopped state for the created user.
                startProtectedPackage(userId);
                // Try to force-stop the package under test on the created user.
                tryFgsStoppingProtectedPackage(userId, /* canUserStopPackage= */ false);
            } finally {
                // Clear the protected packages so that the package under test can be force-stopped.
                runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".UserControlDisabledPackagesTest",
                        "testClearSetUserControlDisabledPackages", mPrimaryUserId);
                tryFgsStoppingProtectedPackage(userId, /* canUserStopPackage= */ true);

                // Removal of the created user and the installed simple app on the created user are
                // done in tear down.
            }
        } finally {
            setStopBgUsersOnSwitchProperty(stopBgUsersOnSwitchValue);
        }
    }

    /**
     * Helper when testing {@link DevicePolicyManager#setUserControlDisabledPackages} API that
     * starts the activity for the package that is under test for a given user.
     * @param userId The user Id to start the package for
     */
    private void startProtectedPackage(int userId) throws Exception {
        // Launch the app once before starting the test.
        startActivityAsUser(userId, SIMPLE_APP_PKG, SIMPLE_APP_ACTIVITY);
        executeDeviceTestMethod(".UserControlDisabledPackagesTest",
                "testLaunchActivity");
    }

    /**
     * Helper when testing {@link DevicePolicyManager#setUserControlDisabledPackages} API that
     * attempts to force-stop the protected package under test for a given user.
     * @param userId The user Id to stop the package for
     * @param canUserStopPackage Whether the user can force stop the protected package
     */
    private void tryForceStoppingProtectedPackage(int userId, boolean canUserStopPackage)
            throws Exception {
        forceStopPackageForUser(SIMPLE_APP_PKG, userId);
        if (canUserStopPackage) {
            executeDeviceTestMethod(".UserControlDisabledPackagesTest",
                    "testForceStopWithUserControlEnabled");
        } else {
            executeDeviceTestMethod(".UserControlDisabledPackagesTest",
                    "testForceStopWithUserControlDisabled");
        }
    }

    /**
     * Helper when testing {@link DevicePolicyManager#setUserControlDisabledPackages} API that
     * attempts to apply the "task-manager" FGS stop operation to the protected package under test
     * for a given user.
     * @param userId The user Id to stop the package for
     * @param canUserStopPackage Whether the user should be able to stop the app
     */
    private void tryFgsStoppingProtectedPackage(int userId, boolean canUserStopPackage)
            throws Exception {
        String pid = executeShellCommand(String.format("pidof %s", SIMPLE_APP_PKG)).trim();
        fgsStopPackageForUser(SIMPLE_APP_PKG, userId);
        if (canUserStopPackage) {
            executeDeviceTestMethod(".UserControlDisabledPackagesTest",
                    "testFgsStopWithUserControlEnabled",
                     Collections.singletonMap(ARG_PID_BEFORE_STOP, pid));
        } else {
            executeDeviceTestMethod(".UserControlDisabledPackagesTest",
                    "testFgsStopWithUserControlDisabled",
                     Collections.singletonMap(ARG_PID_BEFORE_STOP, pid));
        }
    }

    @Test
    public void testDevicePolicySafetyCheckerIntegration_allOperations() throws Exception {
        executeDeviceTestMethod(".DevicePolicySafetyCheckerIntegrationTest", "testAllOperations");
    }

    @Test
    public void testDevicePolicySafetyCheckerIntegration_unsafeStateException() throws Exception {
        executeDeviceTestMethod(".DevicePolicySafetyCheckerIntegrationTest",
                "testUnsafeStateException");
    }

    @Test
    public void testDevicePolicySafetyCheckerIntegration_onOperationSafetyStateChanged()
            throws Exception {
        executeDeviceTestMethod(".DevicePolicySafetyCheckerIntegrationTest",
                "testOnOperationSafetyStateChanged");
    }

    @Test
    public void testListForegroundAffiliatedUsers_notDeviceOwner() throws Exception {
        if (!removeAdmin(DEVICE_OWNER_COMPONENT, mDeviceOwnerUserId)) {
            fail("Failed to remove device owner for user " + mDeviceOwnerUserId);
        }

        executeDeviceTestMethod(".PreDeviceOwnerTest",
                "testListForegroundAffiliatedUsers_notDeviceOwner");
    }

    @Test
    public void testListForegroundAffiliatedUsers_onlyForegroundUser() throws Exception {
        executeDeviceTestMethod(".ListForegroundAffiliatedUsersTest",
                "testListForegroundAffiliatedUsers_onlyForegroundUser");
    }

    @Test
    public void testListForegroundAffiliatedUsers_onlyForegroundUserCalledByDeviceOwner()
            throws Exception {
        assumeHeadlessSystemUserMode("redundant, same as "
                + "testListForegroundAffiliatedUsers_onlyForegroundUser");

        // Must temporarily revoke permission to make sure internal checks are allowing it to be
        // called by profile owner
        revokePermission(DEVICE_OWNER_PKG, PERMISSION_INTERACT_ACROSS_USERS, mDeviceOwnerUserId);

        try {
            executeDeviceOwnerTestMethod(".ListForegroundAffiliatedUsersTest",
                    "testListForegroundAffiliatedUsers_onlyForegroundUser");
        } finally {
            grantPermission(DEVICE_OWNER_PKG, PERMISSION_INTERACT_ACROSS_USERS, mDeviceOwnerUserId,
                    /* reason= */ null);
        }
    }

    @Test
    public void testListForegroundAffiliatedUsers_extraUser() throws Exception {
        assumeCanCreateAdditionalUsers(1);
        createAffiliatedSecondaryUser();

        executeDeviceTestMethod(".ListForegroundAffiliatedUsersTest",
                "testListForegroundAffiliatedUsers_onlyForegroundUser");
    }

    @Test
    public void testListForegroundAffiliatedUsers_notAffiliated() throws Exception {
        assumeCanCreateAdditionalUsers(1);
        int userId = createUser();
        switchUser(userId);

        executeListForegroundAffiliatedUsersTest("testListForegroundAffiliatedUsers_empty");
    }

    @Test
    public void testListForegroundAffiliatedUsers_affiliated() throws Exception {
        assumeCanCreateAdditionalUsers(1);
        int userId = createAffiliatedSecondaryUser();
        switchUser(userId);

        executeListForegroundAffiliatedUsersTest(
                "testListForegroundAffiliatedUsers_onlyForegroundUser");
    }

    @Test
    public void testWifiNetworkConfigurationWithoutFineLocationPermission() throws Exception {
        executeShellCommand("pm revoke --user %d %s android.permission.ACCESS_FINE_LOCATION",
                mPrimaryUserId, DEVICE_OWNER_PKG);

        executeDeviceOwnerTest("WifiNetworkConfigurationWithoutFineLocationPermissionTest");
    }

    private int createAffiliatedSecondaryUser() throws Exception {
        final int userId = createUser();
        installAppAsUser(INTENT_RECEIVER_APK, userId);
        // For headless system user mode, after DO is setup, PO is already
        // set on the secondary user. Meanwhile, it requires additional permission while
        // using DevicePolicyManagerWrapper while using DPM APIs from secondary user.
        if (!isHeadlessSystemUserMode()) {
            installAppAsUser(DEVICE_OWNER_APK, userId);
            setProfileOwnerOrFail(DEVICE_OWNER_COMPONENT, userId);
        } else {
            grantDpmWrapperPermissions(DEVICE_OWNER_PKG, userId);
        }
        wakeupAndDismissKeyguard();

        // Setting the same affiliation ids on both users
        CLog.d("createAffiliatedSecondaryUser(): deviceOwnerId=" + mDeviceOwnerUserId
                + ", primaryUserId=" + mPrimaryUserId + ", newUserId=" + userId);
        affiliateUsers(DEVICE_OWNER_PKG, mDeviceOwnerUserId, userId);

        return userId;
    }

    private void executeDeviceTestMethod(String className, String testName,
            Map<String, String> params) throws Exception {
        runDeviceTestsAsUser(DEVICE_OWNER_PKG, className, testName,
                /* deviceOwnerUserId */ mPrimaryUserId, params);
    }

    private void executeCreateAndManageUserTest(String testMethod) throws Exception {
        // These test must be run on device owner user, as it's the only user that's guaranteed  to
        // be always running (otherwise, the test case would crash on headless system user mode if
        // the current user is switched out)
        // NOTE: there's a setStopBgUsersOnSwitchProperty() method now that would avoid the crash,
        // but it's not worth to change these tests as they will be migrated to the new infra
        executeDeviceOwnerTestMethod(".CreateAndManageUserTest", testMethod);
    }

    private void executeListForegroundAffiliatedUsersTest(String testMethod) throws Exception {
        // These test must be run on device owner user, as it's the only user that's guaranteed  to
        // be always running (otherwise, the test case would crash on headless system user mode if
        // the current user is switched out)
        // NOTE: there's a setStopBgUsersOnSwitchProperty() method now that would avoid the crash,
        // but it's not worth to change these tests as they will be migrated to the new infra
        executeDeviceOwnerTestMethod(".ListForegroundAffiliatedUsersTest", testMethod);
    }

    private void assertNewUserStopped() throws Exception {
        List<Integer> newUsers = getUsersCreatedByTests();
        assertEquals(1, newUsers.size());
        int newUserId = newUsers.get(0);

        assertFalse(getDevice().isUserRunning(newUserId));
    }
}
