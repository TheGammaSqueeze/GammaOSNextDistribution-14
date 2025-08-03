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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.platform.test.annotations.LargeTest;
import android.stats.devicepolicy.EventId;

import com.android.cts.devicepolicy.metrics.DevicePolicyEventWrapper;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RunInterruptedException;
import com.android.tradefed.util.RunUtil;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Set of tests for Managed Profile use cases.
 */
public final class ManagedProfileTest extends BaseManagedProfileTest {

    private static final String DEVICE_OWNER_PKG = "com.android.cts.deviceowner";
    private static final String DEVICE_OWNER_APK = "CtsDeviceOwnerApp.apk";
    private static final String DEVICE_OWNER_ADMIN =
            DEVICE_OWNER_PKG + ".BaseDeviceOwnerTest$BasicAdminReceiver";

    @Test
    public void testManagedProfileSetup() throws Exception {
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG, MANAGED_PROFILE_PKG + ".ManagedProfileSetupTest",
                mProfileUserId);
    }

    /**
     * Verify that removing a managed profile will remove all networks owned by that profile.
     */
    @Test
    public void testProfileWifiCleanup() throws Exception {
        assumeHasWifiFeature();

        try (LocationModeSetter locationModeSetter = new LocationModeSetter(getDevice())) {
            locationModeSetter.setLocationEnabled(true);
            runDeviceTestsAsUser(
                    MANAGED_PROFILE_PKG, ".WifiTest", "testRemoveWifiNetworkIfExists",
                    mParentUserId);

            runDeviceTestsAsUser(
                    MANAGED_PROFILE_PKG, ".WifiTest", "testAddWifiNetwork", mProfileUserId);

            // Now delete the user - should undo the effect of testAddWifiNetwork.
            removeUser(mProfileUserId);
            runDeviceTestsAsUser(
                    MANAGED_PROFILE_PKG, ".WifiTest", "testWifiNetworkDoesNotExist",
                    mParentUserId);
        }
    }

    @Test
    public void testSettingsIntents() throws Exception {
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".SettingsIntentsTest",
                mProfileUserId);
    }

    /** Tests for the API helper class. */
    @Test
    public void testCurrentApiHelper() throws Exception {
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CurrentApiHelperTest",
                mProfileUserId);
    }

    /** Test: unsupported public APIs are disabled on a parent profile. */
    @Test
    public void testParentProfileApiDisabled() throws Exception {
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ParentProfileTest",
                "testParentProfileApiDisabled", mProfileUserId);
    }

    @Test
    public void testCannotCallMethodsOnParentProfile() throws Exception {
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ParentProfileTest",
                "testCannotWipeParentProfile", mProfileUserId);

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ParentProfileTest",
                "testCannotCallSetDefaultSmsApplicationOnParentProfile", mProfileUserId);
    }

    // TODO: This test is not specific to managed profiles, but applies to multi-user in general.
    // Move it to a MultiUserTest class when there is one. Should probably move
    // SetPolicyActivity to a more generic apk too as it might be useful for different kinds
    // of tests (same applies to ComponentDisablingActivity).
    @Test
    public void testNoDebuggingFeaturesRestriction() throws Exception {
        // If adb is running as root, then the adb uid is 0 instead of SHELL_UID,
        // so the DISALLOW_DEBUGGING_FEATURES restriction does not work and this test
        // fails.
        if (getDevice().isAdbRoot()) {
            CLog.logAndDisplay(LogLevel.WARN,
                    "Cannot test testNoDebuggingFeaturesRestriction() in eng/userdebug build");
            return;
        }
        String restriction = "no_debugging_features";  // UserManager.DISALLOW_DEBUGGING_FEATURES

        changeUserRestrictionOrFail(restriction, true, mProfileUserId);


        // This should now fail, as the shell is not available to start activities under a different
        // user once the restriction is in place.
        String addRestrictionCommandOutput =
                changeUserRestriction(restriction, true, mProfileUserId);
        assertTrue(
                "Expected SecurityException when starting the activity "
                        + addRestrictionCommandOutput,
                addRestrictionCommandOutput.contains("SecurityException"));
    }

    @Test
    public void testOrganizationInfo() throws Exception {
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".OrganizationInfoTest",
                "testDefaultOrganizationColor", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".OrganizationInfoTest",
                "testDefaultOrganizationNameIsNull", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".OrganizationInfoTest",
                mProfileUserId);
        assertMetricsLogged(getDevice(), () -> {
            runDeviceTestsAsUser(
                    MANAGED_PROFILE_PKG, MANAGED_PROFILE_PKG + ".OrganizationInfoTest",
                    "testSetOrganizationColor", mProfileUserId);
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_ORGANIZATION_COLOR_VALUE)
                .setAdminPackageName(MANAGED_PROFILE_PKG)
                .build());
    }

    @Test
    public void testDevicePolicyManagerParentSupport() throws Exception {
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG, ".DevicePolicyManagerParentSupportTest", mProfileUserId);
    }

    @Test
    public void testCannotSetProfileOwnerAgain() throws Exception {
        // verify that we can't set the same admin receiver as profile owner again
        assertFalse(setProfileOwner(
                MANAGED_PROFILE_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mProfileUserId,
                /*expectFailure*/ true));

        // verify that we can't set a different admin receiver as profile owner
        installAppAsUser(DEVICE_OWNER_APK, mProfileUserId);
        assertFalse(setProfileOwner(DEVICE_OWNER_PKG + "/" + DEVICE_OWNER_ADMIN, mProfileUserId,
                /*expectFailure*/ true));
    }

    @LargeTest
    @Test
    public void testCannotSetDeviceOwnerWhenProfilePresent() throws Exception {
        try {
            installAppAsUser(DEVICE_OWNER_APK, mParentUserId);
            assertFalse(setDeviceOwner(DEVICE_OWNER_PKG + "/" + DEVICE_OWNER_ADMIN, mParentUserId,
                    /*expectFailure*/ true));
        } finally {
            // make sure we clean up in case we succeeded in setting the device owner
            removeAdmin(DEVICE_OWNER_PKG + "/" + DEVICE_OWNER_ADMIN, mParentUserId);
            getDevice().uninstallPackage(DEVICE_OWNER_PKG);
        }
    }

    @Test
    public void testNfcRestriction() throws Exception {
        assumeHasNfcFeatures();

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NfcTest",
                "testNfcShareEnabled", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NfcTest",
                "testNfcShareEnabled", mParentUserId);

        changeUserRestrictionOrFail("no_outgoing_beam" /* UserManager.DISALLOW_OUTGOING_BEAM */,
                true, mProfileUserId);

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NfcTest",
                "testNfcShareDisabled", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NfcTest",
                "testNfcShareEnabled", mParentUserId);
    }

    @Test
    public void testPhoneAccountVisibility() throws Exception {
        assumeHasTelephonyAndConnectionServiceFeatures();

        try {
            // Register phone account in parent user.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                    "testRegisterPhoneAccount",
                    mParentUserId);
            // The phone account should not be visible in managed user.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                    "testPhoneAccountNotRegistered",
                    mProfileUserId);
        } finally {
            // Unregister the phone account.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                    "testUnregisterPhoneAccount",
                    mParentUserId);
        }

        try {
            // Register phone account in profile user.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                    "testRegisterPhoneAccount",
                    mProfileUserId);
            // The phone account should not be visible in parent user.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                    "testPhoneAccountNotRegistered",
                    mParentUserId);
        } finally {
            // Unregister the phone account.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                    "testUnregisterPhoneAccount",
                    mProfileUserId);
        }
    }

    @LargeTest
    @Test
    public void testManagedCall() throws Exception {
        assumeHasTelephonyAndConnectionServiceFeatures();

        getDevice().executeShellCommand("telecom set-default-dialer " + MANAGED_PROFILE_PKG);

        // Place a outgoing call through work phone account using TelecomManager and verify the
        // call is inserted properly.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testOutgoingCallUsingTelecomManager",
                mProfileUserId);
        // Make sure the call is not inserted into parent user.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testEnsureCallNotInserted",
                mParentUserId);

        // Place a outgoing call through work phone account using ACTION_CALL and verify the call
        // is inserted properly.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testOutgoingCallUsingActionCall",
                mProfileUserId);
        // Make sure the call is not inserted into parent user.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testEnsureCallNotInserted",
                mParentUserId);

        // Add an incoming call with parent user's phone account and verify the call is inserted
        // properly.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testIncomingCall",
                mProfileUserId);
        // Make sure the call is not inserted into parent user.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testEnsureCallNotInserted",
                mParentUserId);

        // Add an incoming missed call with parent user's phone account and verify the call is
        // inserted properly.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testIncomingMissedCall",
                mProfileUserId);
        // Make sure the call is not inserted into parent user.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testEnsureCallNotInserted",
                mParentUserId);
    }

    @Test
    public void testTrustAgentInfo() throws Exception {
        assumeHasSecureLockScreenFeature();

        // Set and get trust agent config using child dpm instance.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".TrustAgentInfoTest",
                "testSetAndGetTrustAgentConfiguration_child",
                mProfileUserId);
        // Set and get trust agent config using parent dpm instance.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".TrustAgentInfoTest",
                "testSetAndGetTrustAgentConfiguration_parent",
                mProfileUserId);
        // Unified case
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".TrustAgentInfoTest",
                "testSetTrustAgentConfiguration_bothHaveTrustAgentConfigAndUnified",
                mProfileUserId);
        // Non-unified case
        try {
            changeUserCredential(TEST_PASSWORD, null, mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".TrustAgentInfoTest",
                    "testSetTrustAgentConfiguration_bothHaveTrustAgentConfigAndNonUnified",
                    mProfileUserId);
        } finally {
            changeUserCredential(null, TEST_PASSWORD, mProfileUserId);
        }
    }

    @Test
    public void testProfileOwnerOnPersonalDeviceCannotGetDeviceIdentifiers() throws Exception {
        // The Profile Owner should have access to all device identifiers.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".DeviceIdentifiersTest",
                "testProfileOwnerOnPersonalDeviceCannotGetDeviceIdentifiers", mProfileUserId);
    }

    @Test
    public void testSetProfileNameLogged() throws Exception {
        assertMetricsLogged(getDevice(), () -> {
            runDeviceTestsAsUser(
                    MANAGED_PROFILE_PKG, MANAGED_PROFILE_PKG + ".DevicePolicyLoggingTest",
                    "testSetProfileNameLogged", mProfileUserId);
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_PROFILE_NAME_VALUE)
                .setAdminPackageName(MANAGED_PROFILE_PKG)
                .build());
    }

    @Test
    public void testCanGetWorkShortcutIconDrawableFromPersonalProfile()
            throws DeviceNotAvailableException {
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".LauncherAppsTest",
                "addDynamicShortcuts", mProfileUserId);
        try {
            Map<String, String> params = new HashMap<>();
            params.put("otherProfileUserId", String.valueOf(mProfileUserId));
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".LauncherAppsTest",
                    "shortcutIconDrawable_currentToOtherProfile_withUsersFullPermission_isNotNull",
                    mPrimaryUserId, params);
        } finally {
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".LauncherAppsTest",
                    "removeAllDynamicShortcuts", mProfileUserId);
        }
    }

    @Test
    public void testCanGetPersonalShortcutIconDrawableFromWorkProfile()
            throws DeviceNotAvailableException {
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".LauncherAppsTest",
                "addDynamicShortcuts", mPrimaryUserId);
        try {
            Map<String, String> params = new HashMap<>();
            params.put("otherProfileUserId", String.valueOf(mPrimaryUserId));
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".LauncherAppsTest",
                    "shortcutIconDrawable_currentToOtherProfile_withUsersFullPermission_isNotNull",
                    mProfileUserId, params);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".LauncherAppsTest",
                    "shortcutIconDrawable_currentToOtherProfile_withoutUsersFullPermission_isNull",
                    mProfileUserId, params);
        } finally {
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".LauncherAppsTest",
                    "removeAllDynamicShortcuts", mPrimaryUserId);
        }
    }

    @Test
    public void testCanGetProfiles() throws Exception {
        // getAllProfiles should contain both the primary and profile
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".UserManagerTest",
                "testGetAllProfiles", mPrimaryUserId);

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".UserManagerTest",
                "testGetAllProfiles", mProfileUserId);

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".UserManagerTest",
                "testIsProfileReturnsFalse_runAsPrimary", mPrimaryUserId);

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".UserManagerTest",
                "testIsProfileReturnsTrue_runAsProfile", mProfileUserId);
    }

    @Test
    public void testCanCreateProfile() throws Exception {
        // remove pre-created profile
        removeUser(mProfileUserId);

        // create profile from installed app
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".UserManagerTest",
                "testCreateProfile_managedProfile", mPrimaryUserId);
    }

    @Test
    public void testResolverActivityLaunchedFromPersonalProfileWithSelectedWorkTab()
            throws Exception {
        installAppAsUser(SHARING_APP_1_APK, mPrimaryUserId);
        installAppAsUser(SHARING_APP_2_APK, mPrimaryUserId);
        installAppAsUser(SHARING_APP_1_APK, mProfileUserId);
        installAppAsUser(SHARING_APP_2_APK, mProfileUserId);
        try {
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileSharingTest",
                    "addCrossProfileIntents", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileSharingTest",
                    "startSwitchToOtherProfileIntent", mPrimaryUserId);
            assertResolverActivityInForeground(mPrimaryUserId);
        } finally {
            pressHome();
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileSharingTest",
                    "clearCrossProfileIntents", mProfileUserId);
        }
    }

    @Test
    public void testResolverActivityLaunchedFromWorkProfileWithSelectedPersonalTab()
            throws Exception {
        installAppAsUser(SHARING_APP_1_APK, mPrimaryUserId);
        installAppAsUser(SHARING_APP_2_APK, mPrimaryUserId);
        installAppAsUser(SHARING_APP_1_APK, mProfileUserId);
        installAppAsUser(SHARING_APP_2_APK, mProfileUserId);
        try {
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileSharingTest",
                    "addCrossProfileIntents", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileSharingTest",
                    "startSwitchToOtherProfileIntent", mProfileUserId);

            // TODO(b/223178698): Investigate potential increase in latency
            RunUtil.getDefault().sleep(30000);

            assertResolverActivityInForeground(mProfileUserId);
        } finally {
            pressHome();
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileSharingTest",
                    "clearCrossProfileIntents", mProfileUserId);
        }
    }

    @Test
    public void testChooserActivityLaunchedFromPersonalProfileWithSelectedWorkTab()
            throws Exception {
        installAppAsUser(SHARING_APP_1_APK, mPrimaryUserId);
        installAppAsUser(SHARING_APP_2_APK, mPrimaryUserId);
        installAppAsUser(SHARING_APP_1_APK, mProfileUserId);
        installAppAsUser(SHARING_APP_2_APK, mProfileUserId);
        try {
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileSharingTest",
                    "addCrossProfileIntents", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileSharingTest",
                    "startSwitchToOtherProfileIntent_chooser", mPrimaryUserId);
            assertChooserActivityInForeground(mPrimaryUserId);
        } finally {
            pressHome();
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileSharingTest",
                    "clearCrossProfileIntents", mProfileUserId);
        }
    }

    private void pressHome() throws Exception {
        executeShellCommand("input keyevent KEYCODE_HOME");
    }

    private void assertChooserActivityInForeground(int userId)
            throws Exception {
        try {
            assertActivityInForeground("android/com.android.internal.app.ChooserActivity", userId);
        } catch (AssertionError e) {
            CLog.v("ChooserActivity is not the default: " + e);
            assertActivityInForeground(resolveActivity("android.intent.action.CHOOSER"), userId);
        }
    }

    private void assertResolverActivityInForeground(int userId)
            throws Exception {
        try {
            assertActivityInForeground("android/com.android.internal.app.ResolverActivity", userId);
        } catch (AssertionError e) {
            CLog.v("ResolverActivity is not the default: " + e);
            assertActivityInForeground(getCustomResolverActivity(), userId);
        }
    }

    private void assertActivityInForeground(String fullActivityName, int userId)
            throws DeviceNotAvailableException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() <= deadline) {
            String commandOutput = getDevice().executeShellCommand(
                    "dumpsys activity activities | grep Resumed:");
            if (commandOutput.contains("u" + userId + " " + fullActivityName)) {
                return;
            }
            try {
                RunUtil.getDefault().sleep(100);
            } catch (RunInterruptedException e){
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
        fail("Activity " + fullActivityName + " didn't become foreground");
    }

    private void changeUserRestrictionOrFail(String key, boolean value, int userId)
            throws DeviceNotAvailableException {
        changeUserRestrictionOrFail(key, value, userId, MANAGED_PROFILE_PKG);
    }

    private String changeUserRestriction(String key, boolean value, int userId)
            throws DeviceNotAvailableException {
        return changeUserRestriction(key, value, userId, MANAGED_PROFILE_PKG);
    }

    private String resolveActivity(String action) throws Exception  {
        // Template output of 'pm resolve-activity -a {action} --brief' :
        // priority=0 preferredOrder=0 match=0x0 specificIndex=-1 isDefault=false
        // com.bar/.foo or com.bar/com.bar.foo
        final String[] outputs = getDevice().executeShellCommand(
                    "pm resolve-activity -a " + action + " --brief").split("\n");

        assertTrue("Result of shell command: 'pm resolve-activity -a " + action
                   + " --brief' is " + outputs[0], outputs.length >= 2);

        return outputs[1];
    }

    private String getCustomResolverActivity() throws Exception {
        final String[] outputs = getDevice().executeShellCommand(
                "cmd overlay lookup android android:string/config_customResolverActivity")
                .split("\n");

        String customResolverActivity = resolveActivity("android.intent.action.SEND");
        if (outputs != null && outputs.length >= 1 && outputs[0] != null && !outputs[0].isEmpty()) {
            customResolverActivity = outputs[0];
        }
        return customResolverActivity;
    }
}
