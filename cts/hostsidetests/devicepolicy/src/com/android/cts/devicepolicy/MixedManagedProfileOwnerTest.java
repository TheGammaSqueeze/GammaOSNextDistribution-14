/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.platform.test.annotations.FlakyTest;
import android.platform.test.annotations.LargeTest;
import android.stats.devicepolicy.EventId;

import com.android.cts.devicepolicy.DeviceAdminFeaturesCheckerRule.RequiresAdditionalFeatures;
import com.android.cts.devicepolicy.annotations.LockSettingsTest;
import com.android.cts.devicepolicy.annotations.PermissionsTest;
import com.android.cts.devicepolicy.metrics.DevicePolicyEventLogVerifier;
import com.android.cts.devicepolicy.metrics.DevicePolicyEventWrapper;

import org.junit.Test;

/**
 * Set of tests for managed profile owner use cases that also apply to device owners.
 * Tests that should be run identically in both cases are added in DeviceAndProfileOwnerTest.
 */
// We need managed users to be supported in order to create a profile of the user owner.
@RequiresAdditionalFeatures({FEATURE_MANAGED_USERS})
public final class MixedManagedProfileOwnerTest extends DeviceAndProfileOwnerTest {

    private static final String CLEAR_PROFILE_OWNER_NEGATIVE_TEST_CLASS =
            DEVICE_ADMIN_PKG + ".ClearProfileOwnerNegativeTest";

    private static final String DELEGATION_NETWORK_LOGGING = "delegation-network-logging";

    private int mParentUserId = -1;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        removeTestUsers();
        mParentUserId = mPrimaryUserId;
        createManagedProfile();
    }

    private void createManagedProfile() throws Exception {
        mUserId = createManagedProfile(mParentUserId);
        switchUser(mParentUserId);
        startUserAndWait(mUserId);

        installAppAsUser(DEVICE_ADMIN_APK, mUserId);
        setProfileOwnerOrFail(DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mUserId);
        startUserAndWait(mUserId);
    }

    @Override
    public void tearDown() throws Exception {
        removeUser(mUserId);

        super.tearDown();
    }

    // Most tests for this class are defined in DeviceAndProfileOwnerTest

    @FlakyTest
    @Test
    public void testScreenCaptureDisabled_assist_allowedPrimaryUser() throws Exception {
        // disable screen capture in profile
        executeDeviceTestMethod(".ScreenCaptureDisabledTest", "testSetScreenCaptureDisabled_true");
        try {
            // Install and enable assistant in personal side.
            installAppAsUser(ASSIST_APP_APK, mParentUserId);
            waitForBroadcastIdle();
            setVoiceInteractionService(ASSIST_INTERACTION_SERVICE);

            // Start an activity in parent user.
            installAppAsUser(DEVICE_ADMIN_APK, mParentUserId);
            waitForBroadcastIdle();
            startSimpleActivityAsUser(mParentUserId);

            // Verify assistant app can't take a screenshot.
            runDeviceTestsAsUser(
                    DEVICE_ADMIN_PKG,
                    ".AssistScreenCaptureDisabledTest",
                    "testScreenCapturePossible_assist",
                    mPrimaryUserId);
        } finally {
            // enable screen capture in profile
            executeDeviceTestMethod(
                    ".ScreenCaptureDisabledTest",
                    "testSetScreenCaptureDisabled_false");
            clearVoiceInteractionService();
        }
    }

    @Override
    @Test
    public void testDisallowSetWallpaper_allowed() throws Exception {
        // Managed profile doesn't have wallpaper.
    }

    @Override
    @Test
    public void testAudioRestriction() throws Exception {
        // DISALLOW_UNMUTE_MICROPHONE and DISALLOW_ADJUST_VOLUME can only be set by device owners
        // and profile owners on the primary user.
    }

    @Test
    public void testSetGetPreferentialNetworkServiceStatus() throws Exception {
        executeDeviceTestMethod(".PreferentialNetworkServiceStatusTest",
                "testGetSetPreferentialNetworkServiceStatus");
    }

    @Test
    public void testSetPreferentialNetworkServiceStatusLogged() throws Exception {
        DevicePolicyEventLogVerifier.assertMetricsLogged(getDevice(), () -> {
            executeDeviceTestMethod(".DevicePolicyLoggingTest",
                    "testSetPreferentialNetworkServiceEnabledLogged");
        }, new DevicePolicyEventWrapper.Builder(
                EventId.SET_PREFERENTIAL_NETWORK_SERVICE_ENABLED_VALUE)
                .setBoolean(true)
                .build(),
        new DevicePolicyEventWrapper.Builder(
                EventId.SET_PREFERENTIAL_NETWORK_SERVICE_ENABLED_VALUE)
                .setBoolean(false)
                .build());
    }

    /** VPN tests don't require physical device for managed profile, thus overriding. */
    @FlakyTest
    @Override
    @Test
    public void testAlwaysOnVpn() throws Exception {
        super.testAlwaysOnVpn();
    }

    /** VPN tests don't require physical device for managed profile, thus overriding. */
    @Override
    @Test
    public void testAlwaysOnVpnLockDown() throws Exception {
        super.testAlwaysOnVpnLockDown();
    }

    /** VPN tests don't require physical device for managed profile, thus overriding. */
    @Override
    @LargeTest
    @Test
    public void testAlwaysOnVpnAcrossReboot() throws Exception {
        super.testAlwaysOnVpnAcrossReboot();
    }

    /** VPN tests don't require physical device for managed profile, thus overriding. */
    @Override
    @Test
    public void testAlwaysOnVpnPackageUninstalled() throws Exception {
        super.testAlwaysOnVpnPackageUninstalled();
    }

    /** VPN tests don't require physical device for managed profile, thus overriding. */
    @Override
    @Test
    public void testAlwaysOnVpnUnsupportedPackage() throws Exception {
        super.testAlwaysOnVpnUnsupportedPackage();
    }

    /** VPN tests don't require physical device for managed profile, thus overriding. */
    @Override
    @Test
    public void testAlwaysOnVpnUnsupportedPackageReplaced() throws Exception {
        super.testAlwaysOnVpnUnsupportedPackageReplaced();
    }

    @Override
    @Test
    public void testSetSystemSetting() {
        // Managed profile owner cannot set currently allowlisted system settings.
    }

    @Test
    public void testCannotClearProfileOwner() throws Exception {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, CLEAR_PROFILE_OWNER_NEGATIVE_TEST_CLASS, mUserId);
    }

    @Test
    public void testDelegatedCertInstallerDeviceIdAttestation() throws Exception {
        setUpDelegatedCertInstallerAndRunTests(() -> {
            runDeviceTestsAsUser("com.android.cts.certinstaller",
                    ".DelegatedDeviceIdAttestationTest",
                    "testGenerateKeyPairWithDeviceIdAttestationExpectingFailure", mUserId);
            // Positive test case lives in
            // OrgOwnedProfileOwnerTest#testDelegatedCertInstallerDeviceIdAttestation
        });
    }

    @Test
    public void testDeviceIdAttestationForProfileOwner() throws Exception {
        // Test that Device ID attestation for the profile owner does not work without grant.
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DeviceIdAttestationTest",
                "testFailsWithoutProfileOwnerIdsGrant", mUserId);
        // Positive test case lives in
        // OrgOwnedProfileOwnerTest#testDeviceIdAttestationForProfileOwner
    }

    @Test
    @Override
    public void testSetKeyguardDisabledSecureCameraLogged() {
        // Managed profiles are not allowed to set keyguard disabled secure camera
    }

    @FlakyTest
    @Override
    @Test
    public void testDelegatedCertInstaller() throws Exception {
        super.testDelegatedCertInstaller();
    }

    @FlakyTest
    @Override
    @Test
    public void testPackageInstallUserRestrictions() throws Exception {
        super.testPackageInstallUserRestrictions();
    }

    @FlakyTest
    @Override
    @Test
    public void testScreenCaptureDisabled_assist() throws Exception {
        super.testScreenCaptureDisabled_assist();
    }

    @Override
    @PermissionsTest
    @Test
    public void testPermissionPolicy() throws Exception {
        super.testPermissionPolicy();
    }

    @FlakyTest
    @Override
    @Test
    public void testSetMeteredDataDisabledPackages() throws Exception {
        super.testSetMeteredDataDisabledPackages();
    }

    @Override
    @PermissionsTest
    @Test
    public void testPermissionAppUpdate() throws Exception {
        super.testPermissionAppUpdate();
    }

    @Override
    @PermissionsTest
    @Test
    public void testPermissionGrantPreMApp() throws Exception {
        super.testPermissionGrantPreMApp();
    }

    @Override
    @PermissionsTest
    @Test
    public void testPermissionGrantOfDisallowedPermissionWhileOtherPermIsGranted()
            throws Exception {
        super.testPermissionGrantOfDisallowedPermissionWhileOtherPermIsGranted();
    }

    @Override
    @Test
    public void testLockTaskAfterReboot() {
        // Managed profiles are not allowed to use lock task
    }

    @Override
    @Test
    public void testLockTaskAfterReboot_tryOpeningSettings() {
        // Managed profiles are not allowed to use lock task
    }

    @Override
    @LockSettingsTest
    @Test
    public void testSecondaryLockscreen() throws Exception {
        // Managed profiles cannot have secondary lockscreens set.
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
}
