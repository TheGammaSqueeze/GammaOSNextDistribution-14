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

import static com.android.cts.devicepolicy.metrics.DevicePolicyEventLogVerifier.assertMetricsLogged;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.FlakyTest;
import android.platform.test.annotations.LargeTest;
import android.platform.test.annotations.RequiresDevice;
import android.stats.devicepolicy.EventId;

import com.android.cts.devicepolicy.DeviceAdminFeaturesCheckerRule.TemporarilyIgnoreOnHeadlessSystemUserMode;
import com.android.cts.devicepolicy.annotations.LockSettingsTest;
import com.android.cts.devicepolicy.metrics.DevicePolicyEventWrapper;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RunUtil;

import com.google.common.collect.ImmutableMap;

import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Set of tests for use cases that apply to profile and device owner.
 * This class is the base class of MixedProfileOwnerTest, MixedDeviceOwnerTest and
 * MixedManagedProfileOwnerTest and is abstract to avoid running spurious tests.
 *
 * NOTE: Not all tests are executed in the subclasses. Sometimes, if a test is not applicable to
 * a subclass, they override it with an empty method.
 */
public abstract class DeviceAndProfileOwnerTest extends BaseDevicePolicyTest {

    public static final String DEVICE_ADMIN_PKG = "com.android.cts.deviceandprofileowner";
    public static final String DEVICE_ADMIN_APK = "CtsDeviceAndProfileOwnerApp.apk";
    protected static final String ADMIN_RECEIVER_TEST_CLASS
            = ".BaseDeviceAdminTest$BasicAdminReceiver";
    protected static final String DEVICE_ADMIN_COMPONENT_FLATTENED =
            DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS;

    protected static final String IS_SYSTEM_USER_PARAM = "isSystemUser";

    protected static final String INTENT_RECEIVER_PKG = "com.android.cts.intent.receiver";
    protected static final String INTENT_RECEIVER_APK = "CtsIntentReceiverApp.apk";

    private static final String INTENT_SENDER_PKG = "com.android.cts.intent.sender";
    private static final String INTENT_SENDER_APK = "CtsIntentSenderApp.apk";

    private static final String PERMISSIONS_APP_PKG = "com.android.cts.permissionapp";
    private static final String PERMISSIONS_APP_APK = "CtsPermissionApp.apk";

    private static final String SIMPLE_PRE_M_APP_PKG = "com.android.cts.launcherapps.simplepremapp";
    private static final String SIMPLE_PRE_M_APP_APK = "CtsSimplePreMApp.apk";

    public static final String CERT_INSTALLER_PKG = "com.android.cts.certinstaller";
    public static final String CERT_INSTALLER_APK = "CtsCertInstallerApp.apk";

    protected static final String DELEGATE_APP_PKG = "com.android.cts.delegate";
    protected static final String DELEGATE_APP_APK = "CtsDelegateApp.apk";
    private static final String DELEGATION_CERT_INSTALL = "delegation-cert-install";
    private static final String DELEGATION_CERT_SELECTION = "delegation-cert-selection";

    protected static final String TEST_APP_APK = "CtsSimpleApp.apk";
    private static final String TEST_APP_PKG = "com.android.cts.launcherapps.simpleapp";
    protected static final String TEST_APP_LOCATION = "/data/local/tmp/";

    protected static final String PACKAGE_INSTALLER_PKG = "com.android.cts.packageinstaller";
    protected static final String PACKAGE_INSTALLER_APK = "CtsPackageInstallerApp.apk";

    private static final String VPN_APP_PKG = "com.android.cts.vpnfirewall";
    private static final String VPN_APP_APK = "CtsVpnFirewallApp.apk";
    private static final String VPN_APP_API23_APK = "CtsVpnFirewallAppApi23.apk";
    private static final String VPN_APP_API24_APK = "CtsVpnFirewallAppApi24.apk";
    private static final String VPN_APP_NOT_ALWAYS_ON_APK = "CtsVpnFirewallAppNotAlwaysOn.apk";

    private static final String DISALLOW_REMOVE_USER = "no_remove_user";

    private static final String AUTOFILL_APP_PKG = "com.android.cts.devicepolicy.autofillapp";
    private static final String AUTOFILL_APP_APK = "CtsDevicePolicyAutofillApp.apk";

    private static final String CONTENT_CAPTURE_APP_PKG = "com.android.cts.devicepolicy.contentcaptureapp";
    private static final String CONTENT_CAPTURE_APP_APK = "CtsDevicePolicyContentCaptureApp.apk";

    private static final String CONTENT_CAPTURE_SERVICE_PKG = "com.android.cts.devicepolicy.contentcaptureservice";
    private static final String CONTENT_CAPTURE_SERVICE_APK = "CtsDevicePolicyContentCaptureService.apk";

    protected static final String ASSIST_APP_PKG = "com.android.cts.devicepolicy.assistapp";
    protected static final String ASSIST_APP_APK = "CtsDevicePolicyAssistApp.apk";

    private static final String METERED_DATA_APP_PKG
            = "com.android.cts.devicepolicy.meteredtestapp";
    private static final String METERED_DATA_APP_APK = "CtsMeteredDataTestApp.apk";

    // For testing key pair grants since they are per-uid
    private static final String SHARED_UID_APP1_APK = "SharedUidApp1.apk";
    private static final String SHARED_UID_APP2_APK = "SharedUidApp2.apk";

    private static final String ENABLED_NOTIFICATION_POLICY_ACCESS_PACKAGES
            = "enabled_notification_policy_access_packages";

    protected static final String ASSIST_INTERACTION_SERVICE =
            ASSIST_APP_PKG + "/.MyInteractionService";

    private static final String RESTRICT_BACKGROUND_GET_CMD =
        "cmd netpolicy get restrict-background";
    private static final String RESTRICT_BACKGROUND_ON_CMD =
        "cmd netpolicy set restrict-background true";
    private static final String RESTRICT_BACKGROUND_OFF_CMD =
        "cmd netpolicy set restrict-background false";

    // The following constants were copied from DevicePolicyManager
    private static final int PASSWORD_QUALITY_COMPLEX = 0x60000;
    private static final int KEYGUARD_DISABLE_FEATURES_NONE = 0;
    private static final int KEYGUARD_DISABLE_FINGERPRINT = 1 << 5;
    private static final int KEYGUARD_DISABLE_TRUST_AGENTS = 1 << 4;
    private static final int KEYGUARD_DISABLE_SECURE_CAMERA = 1 << 1;
    private static final String DISALLOW_CONFIG_LOCATION = "no_config_location";
    private static final String DISALLOW_ADJUST_VOLUME = "no_adjust_volume";
    private static final String DISALLOW_AUTOFILL = "no_autofill";
    private static final String SKIP_FIRST_USE_HINTS = "skip_first_use_hints";
    private static final String DEFAULT_INPUT_METHOD = "default_input_method";
    private static final int PERMISSION_POLICY_PROMPT = 0;
    private static final int PERMISSION_POLICY_AUTO_GRANT = 1;
    private static final int PERMISSION_POLICY_AUTO_DENY = 2;
    private static final int PERMISSION_GRANT_STATE_DEFAULT = 0;
    private static final int PERMISSION_GRANT_STATE_GRANTED = 1;
    private static final int PERMISSION_GRANT_STATE_DENIED = 2;
    private static final String PARAM_APP_TO_ENABLE = "app_to_enable";
    public static final String RESOLVE_ACTIVITY_CMD = "cmd package resolve-activity --brief --user %d %s | tail -n 1";

    private static final String NOT_CALLED_FROM_PARENT = "notCalledFromParent";

    // ID of the user all tests are run as. For device owner this will be the current user, for
    // profile owner it is the user id of the created profile.
    protected int mUserId;

    @Override
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(DEVICE_ADMIN_PKG);
        getDevice().uninstallPackage(PERMISSIONS_APP_PKG);
        getDevice().uninstallPackage(SIMPLE_PRE_M_APP_PKG);
        getDevice().uninstallPackage(CERT_INSTALLER_PKG);
        getDevice().uninstallPackage(DELEGATE_APP_PKG);
        getDevice().uninstallPackage(VPN_APP_PKG);
        getDevice().uninstallPackage(VPN_APP_API23_APK);
        getDevice().uninstallPackage(VPN_APP_API24_APK);
        getDevice().uninstallPackage(VPN_APP_NOT_ALWAYS_ON_APK);
        getDevice().uninstallPackage(INTENT_RECEIVER_PKG);
        getDevice().uninstallPackage(INTENT_SENDER_PKG);
        getDevice().uninstallPackage(AUTOFILL_APP_PKG);
        getDevice().uninstallPackage(CONTENT_CAPTURE_SERVICE_PKG);
        getDevice().uninstallPackage(CONTENT_CAPTURE_APP_PKG);
        getDevice().uninstallPackage(METERED_DATA_APP_PKG);
        getDevice().uninstallPackage(TEST_APP_PKG);

        // Press the HOME key to close any alart dialog that may be shown.
        getDevice().executeShellCommand("input keyevent 3");

        super.tearDown();
    }

    @Test
    public void testApplicationRestrictionIsRestricted() throws Exception {
        installAppAsUser(DELEGATE_APP_APK, mUserId);
        runDeviceTestsAsUser(DELEGATE_APP_PKG, ".AppRestrictionsIsCallerDelegateHelper",
            "testAssertCallerIsNotApplicationRestrictionsManagingPackage", mUserId);
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".ApplicationRestrictionsIsCallerDelegateHelper",
            "testSetApplicationRestrictionsManagingPackageToDelegate", mUserId);
        runDeviceTestsAsUser(DELEGATE_APP_PKG, ".AppRestrictionsIsCallerDelegateHelper",
            "testAssertCallerIsApplicationRestrictionsManagingPackage", mUserId);
    }

    protected void installDelegateApp() throws Exception {
        installAppAsUser(DELEGATE_APP_APK, mUserId);
    }

    @Test
    public void testDelegationCertSelection() throws Exception {
        installAppAsUser(CERT_INSTALLER_APK, mUserId);
        setDelegatedScopes(CERT_INSTALLER_PKG, Arrays.asList(
                DELEGATION_CERT_INSTALL, DELEGATION_CERT_SELECTION));

        assertMetricsLogged(getDevice(), () -> {
                runDeviceTestsAsUser(CERT_INSTALLER_PKG, ".CertSelectionDelegateTest", mUserId);
        }, new DevicePolicyEventWrapper.Builder(EventId.CHOOSE_PRIVATE_KEY_ALIAS_VALUE)
                .setAdminPackageName(CERT_INSTALLER_PKG)
                .setBoolean(true)
                .build());
    }

    /**
     * Require a device for tests that use the network stack. Headless Androids running in
     * data centres might need their network rules un-tampered-with in order to keep the ADB / VNC
     * connection alive.
     *
     * This is only a problem on device owner / profile owner running on USER_SYSTEM, because
     * network rules for this user will affect UID 0.
     */
    @RequiresDevice
    @Test
    public void testAlwaysOnVpn() throws Exception {
        assumeIsNotWatch();
        int userId = getUserIdForAlwaysOnVpnTests();
        installAppAsUser(VPN_APP_APK, userId);
        executeDeviceTestClassNoRestrictBackground(".AlwaysOnVpnTest", userId);
    }

    protected int getUserIdForAlwaysOnVpnTests() {
        return mUserId;
    }

    @RequiresDevice
    @Test
    public void testAlwaysOnVpnLockDown() throws Exception {
        assumeIsNotWatch();
        int userId = getUserIdForAlwaysOnVpnTests();
        installAppAsUser(VPN_APP_APK, userId);
        try {
            executeDeviceTestMethod(".AlwaysOnVpnMultiStageTest", "testAlwaysOnSet", userId);
            forceStopPackageForUser(VPN_APP_PKG, userId);
            executeDeviceTestMethod(".AlwaysOnVpnMultiStageTest", "testNetworkBlocked", userId);
        } finally {
            executeDeviceTestMethod(".AlwaysOnVpnMultiStageTest", "testCleanup", userId);
        }
    }

    @RequiresDevice
    @Test
    public void testAlwaysOnVpnAcrossReboot() throws Exception {
        assumeIsNotWatch();
        int userId = getUserIdForAlwaysOnVpnTests();
        try {
            installAppAsUser(VPN_APP_APK, userId);
            waitForBroadcastIdle();
            executeDeviceTestMethod(".AlwaysOnVpnMultiStageTest", "testAlwaysOnSetWithAllowlist",
                    userId);
            rebootAndWaitUntilReady();
            // Make sure profile user initialization is complete before proceeding.
            waitForBroadcastIdle();
            executeDeviceTestMethod(".AlwaysOnVpnMultiStageTest", "testAlwaysOnSetAfterReboot",
                    userId);
        } finally {
            executeDeviceTestMethod(".AlwaysOnVpnMultiStageTest", "testCleanup", userId);
        }
    }

    @RequiresDevice
    @Test
    public void testAlwaysOnVpnPackageUninstalled() throws Exception {
        assumeIsNotWatch();
        int userId = getUserIdForAlwaysOnVpnTests();
        installAppAsUser(VPN_APP_APK, userId);
        try {
            executeDeviceTestMethod(".AlwaysOnVpnMultiStageTest", "testAlwaysOnSet", userId);
            getDevice().uninstallPackage(VPN_APP_PKG);
            executeDeviceTestMethod(".AlwaysOnVpnMultiStageTest", "testAlwaysOnVpnDisabled",
                    userId);
            executeDeviceTestMethod(".AlwaysOnVpnMultiStageTest", "testSetNonExistingPackage",
                    userId);
        } finally {
            executeDeviceTestMethod(".AlwaysOnVpnMultiStageTest", "testCleanup", userId);
        }
    }

    @RequiresDevice
    @Test
    public void testAlwaysOnVpnUnsupportedPackage() throws Exception {
        assumeIsNotWatch();
        int userId = getUserIdForAlwaysOnVpnTests();
        try {
            // Target SDK = 23: unsupported
            installAppAsUser(VPN_APP_API23_APK, userId);
            executeDeviceTestMethod(".AlwaysOnVpnUnsupportedTest", "testSetUnsupportedVpnAlwaysOn",
                    userId);

            // Target SDK = 24: supported
            installAppAsUser(VPN_APP_API24_APK, userId);
            executeDeviceTestMethod(".AlwaysOnVpnUnsupportedTest", "testSetSupportedVpnAlwaysOn",
                    userId);
            executeDeviceTestMethod(".AlwaysOnVpnUnsupportedTest", "testClearAlwaysOnVpn", userId);

            // Explicit opt-out: unsupported
            installAppAsUser(VPN_APP_NOT_ALWAYS_ON_APK, userId);
            executeDeviceTestMethod(".AlwaysOnVpnUnsupportedTest", "testSetUnsupportedVpnAlwaysOn",
                    userId);
        } finally {
            executeDeviceTestMethod(".AlwaysOnVpnUnsupportedTest", "testClearAlwaysOnVpn", userId);
        }
    }

    @RequiresDevice
    @Test
    public void testAlwaysOnVpnUnsupportedPackageReplaced() throws Exception {
        assumeIsNotWatch();
        int userId = getUserIdForAlwaysOnVpnTests();
        try {
            // Target SDK = 24: supported
            executeDeviceTestMethod(".AlwaysOnVpnUnsupportedTest", "testAssertNoAlwaysOnVpn",
                    userId);
            installAppAsUser(VPN_APP_API24_APK, userId);
            executeDeviceTestMethod(".AlwaysOnVpnUnsupportedTest", "testSetSupportedVpnAlwaysOn",
                    userId);
            // Update the app to target higher API level, but with manifest opt-out
            installAppAsUser(VPN_APP_NOT_ALWAYS_ON_APK, userId);
            // wait for the app update install completed, ready to be tested
            waitForBroadcastIdle();
            executeDeviceTestMethod(".AlwaysOnVpnUnsupportedTest", "testAssertNoAlwaysOnVpn",
                    userId);
        } finally {
            executeDeviceTestMethod(".AlwaysOnVpnUnsupportedTest", "testClearAlwaysOnVpn", userId);
        }
    }

    @RequiresDevice
    @Test
    public void testAlwaysOnVpnPackageLogged() throws Exception {
        assumeIsNotWatch();
        int userId = getUserIdForAlwaysOnVpnTests();
        // Will be uninstalled in tearDown().
        installAppAsUser(VPN_APP_APK, userId);
        assertMetricsLogged(getDevice(), () -> {
            executeDeviceTestMethod(".AlwaysOnVpnUnsupportedTest", "testSetSupportedVpnAlwaysOn",
                    userId);
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_ALWAYS_ON_VPN_PACKAGE_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .setStrings(VPN_APP_PKG)
                    .setBoolean(true)
                    .setInt(0)
                    .build());
    }

    @Test
    public void testPermissionPolicy() throws Exception {
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest",
                "testPermissionPolicyAutoDeny_permissionLocked");
        executeDeviceTestMethod(".PermissionsTest",
                "testPermissionPolicyAutoGrant_permissionLocked");
    }

    @Test
    public void testPermissionGrantOfDisallowedPermissionWhileOtherPermIsGranted()
            throws Exception {
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest",
                "testPermissionGrantStateDenied_otherPermissionIsGranted");
    }

    @Test
    @FlakyTest(bugId = 205194911)
    public void testPermissionPrompts() throws Exception {
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest", "testPermissionPrompts");
    }

    @Test
    public void testPermissionAppUpdate() throws Exception {
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest", "testPermissionGrantStateDenied");
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest", "testCannotRequestPermission");

        assertNull(getDevice().uninstallPackage(PERMISSIONS_APP_PKG));
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest", "testPermissionGrantStateGranted");
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest", "testCanRequestPermission");

        assertNull(getDevice().uninstallPackage(PERMISSIONS_APP_PKG));
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest", "testPermissionPolicyAutoDeny");
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest", "testCannotRequestPermission");

        assertNull(getDevice().uninstallPackage(PERMISSIONS_APP_PKG));
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest", "testPermissionPolicyAutoGrant");
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest", "testCanRequestPermission");
    }

    @Test
    public void testPermissionGrantPreMApp() throws Exception {
        installAppAsUser(SIMPLE_PRE_M_APP_APK, mUserId);

        if (isHeadlessSystemUserMode()) {
            installAppAsUser(SIMPLE_PRE_M_APP_APK, mDeviceOwnerUserId);
        }
        executeDeviceTestMethod(".PermissionsTest", "testPermissionGrantState_preMApp");
    }

    @Test
    public void testScreenCaptureDisabled_assist() throws Exception {
        try {
            // Install and enable assistant, notice that profile can't have assistant.
            installAppAsUser(ASSIST_APP_APK, mPrimaryUserId);
            waitForBroadcastIdle();
            setVoiceInteractionService(ASSIST_INTERACTION_SERVICE);
            setScreenCaptureDisabled_assist(mUserId, true /* disabled */);
        } finally {
            setScreenCaptureDisabled_assist(mUserId, false /* disabled */);
            clearVoiceInteractionService();
        }
    }

    @TemporarilyIgnoreOnHeadlessSystemUserMode(bugId = "197859595",
            reason = "Will be migrated to new test infra")
    @Test
    public void testDelegatedCertInstaller() throws Exception {
        installAppAsUser(CERT_INSTALLER_APK, mUserId);

        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DelegatedCertInstallerTest", mUserId);
        assertMetricsLogged(getDevice(), () -> {
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DelegatedCertInstallerTest",
                    "testInstallKeyPair", mUserId);
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_CERT_INSTALLER_PACKAGE_VALUE)
                .setAdminPackageName(DEVICE_ADMIN_PKG)
                .setStrings(CERT_INSTALLER_PKG)
                .build());
    }

    public interface DelegatedCertInstallerTestAction {
        void run() throws Exception;
    }

    protected void setUpDelegatedCertInstallerAndRunTests(DelegatedCertInstallerTestAction test)
            throws Exception {
        installAppAsUser(CERT_INSTALLER_APK, mUserId);

        try {
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DelegatedCertInstallerHelper",
                    "testManualSetCertInstallerDelegate", mUserId);

            test.run();
        } finally {
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DelegatedCertInstallerHelper",
                    "testManualClearCertInstallerDelegate", mUserId);
        }
    }

    // This test currently duplicates the testDelegatedCertInstaller, with one difference:
    // The Delegated cert installer app is called directly rather than via intents from
    // the DelegatedCertinstallerTest.
    @TemporarilyIgnoreOnHeadlessSystemUserMode(bugId = "197859595",
            reason = "Will be migrated to new test infra")
    @Test
    public void testDelegatedCertInstallerDirectly() throws Exception {
        assumeTrue(mHasAttestation);

        setUpDelegatedCertInstallerAndRunTests(() ->
            runDeviceTestsAsUser("com.android.cts.certinstaller",
                    ".DirectDelegatedCertInstallerTest", mUserId));
    }

    // This test generates a key pair and validates that an app can be silently granted
    // access to it.
    @TemporarilyIgnoreOnHeadlessSystemUserMode(bugId = "197859595",
            reason = "Will be migrated to new test infra")
    @Test
    public void testSetKeyGrant() throws Exception {
        assumeTrue(mHasAttestation);

        // Install an app
        installAppAsUser(CERT_INSTALLER_APK, mUserId);

        try {
            // First, generate a key and grant the cert installer access to it.
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DelegatedCertInstallerHelper",
                    "testManualGenerateKeyAndGrantAccess", mUserId);
            // Test the key is usable.
            runDeviceTestsAsUser("com.android.cts.certinstaller",
                    ".PreSelectedKeyAccessTest", "testAccessingPreSelectedAliasExpectingSuccess",
                    mUserId);
            // Remove the grant
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DelegatedCertInstallerHelper",
                    "testManualRemoveKeyGrant", mUserId);
            // Run another test to make sure the app no longer has access to the key.
            runDeviceTestsAsUser("com.android.cts.certinstaller",
                    ".PreSelectedKeyAccessTest", "testAccessingPreSelectedAliasWithoutGrant", mUserId);
        } finally {
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DelegatedCertInstallerHelper",
                    "testManualClearGeneratedKey", mUserId);
        }
    }

    // Runs test with admin privileges. The test methods set all the tested restrictions
    // inside. But these restrictions must have no effect on the device/profile owner behavior.
    @Test
    public void testDisallowSetWallpaper_allowed() throws Exception {
        if (!hasService("wallpaper")) {
            CLog.d("testDisallowSetWallpaper_allowed(): device does not support wallpapers");
            return;
        }
        executeDeviceTestMethod(".CustomizationRestrictionsTest",
                "testDisallowSetWallpaper_allowed");
    }

    @Test
    public void testDisallowAutofill_allowed() throws Exception {
        boolean hasAutofill = hasDeviceFeature("android.software.autofill");
        if (!hasAutofill) {
          return;
        }
        installAppAsUser(AUTOFILL_APP_APK, mUserId);

        executeDeviceTestMethod(".AutofillRestrictionsTest",
                "testDisallowAutofill_allowed");
    }

    @Test
    public void testDisallowContentCapture_allowed() throws Exception {
        boolean hasContentCapture = hasService("content_capture");
        if (!hasContentCapture) {
            return;
        }
        installAppAsUser(CONTENT_CAPTURE_SERVICE_APK, mUserId);
        installAppAsUser(CONTENT_CAPTURE_APP_APK, mUserId);

        setDefaultContentCaptureServiceEnabled(false);
        try {
            executeDeviceTestMethod(".ContentCaptureRestrictionsTest",
                    "testDisallowContentCapture_allowed");
        } finally {
            setDefaultContentCaptureServiceEnabled(true);
        }
    }

    private void setDefaultContentCaptureServiceEnabled(boolean enabled)
            throws Exception {
        CLog.d("setDefaultServiceEnabled(" + mUserId + "): " + enabled);
        getDevice().executeShellCommand(
                "cmd content_capture set default-service-enabled " + mUserId + " " + enabled);
    }

    @Test
    public void testSetMeteredDataDisabledPackages() throws Exception {
        assumeHasWifiFeature();
        assumeIsNotWatch();

        installAppAsUser(METERED_DATA_APP_APK, mUserId);

        try (LocationModeSetter locationModeSetter = new LocationModeSetter(getDevice())) {
            locationModeSetter.setLocationEnabled(true);
            executeDeviceTestClass(".MeteredDataRestrictionTest");
        }
    }

    @Test
    public void testPackageInstallUserRestrictions() throws Exception {
        boolean mIsWatch = hasDeviceFeature("android.hardware.type.watch");
        if (mIsWatch) {
            return;
        }
        // UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES
        final String DISALLOW_INSTALL_UNKNOWN_SOURCES = "no_install_unknown_sources";
        // UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY
        final String DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY =
                "no_install_unknown_sources_globally";
        final String SECURE_SETTING_CATEGORY = "secure";
        final String GLOBAL_SETTING_CATEGORY = "global";
        final File apk = mBuildHelper.getTestFile(TEST_APP_APK);

        // Needed to access dpm.getPolicyExemptApps()
        allowTestApiAccess(DEVICE_ADMIN_PKG);

        try {
            // Install the test and prepare the test apk.
            installAppAsUser(PACKAGE_INSTALLER_APK, mUserId);
            assertTrue(getDevice().pushFile(apk, TEST_APP_LOCATION + apk.getName()));
            setInstallPackageAppOps(PACKAGE_INSTALLER_PKG, true, mUserId);

            // Add restrictions and test if we can install the apk.
            getDevice().uninstallPackage(TEST_APP_PKG);
            changeUserRestrictionOrFail(DISALLOW_INSTALL_UNKNOWN_SOURCES, true, mUserId);
            runDeviceTestsAsUser(PACKAGE_INSTALLER_PKG, ".ManualPackageInstallTest",
                    "testManualInstallBlocked", mUserId);

            // Clear restrictions and test if we can install the apk.
            changeUserRestrictionOrFail(DISALLOW_INSTALL_UNKNOWN_SOURCES, false, mUserId);

            // Add global restriction and test if we can install the apk.
            getDevice().uninstallPackage(TEST_APP_PKG);
            changeUserRestrictionOrFail(DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY, true, mUserId);
            runDeviceTestsAsUser(PACKAGE_INSTALLER_PKG, ".ManualPackageInstallTest",
                    "testManualInstallBlocked", mUserId);

            // Clear global restriction and test if we can install the apk.
            changeUserRestrictionOrFail(DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY, false, mUserId);
            setInstallPackageAppOps(PACKAGE_INSTALLER_PKG, true, mUserId);
            runDeviceTestsAsUser(PACKAGE_INSTALLER_PKG, ".ManualPackageInstallTest",
                    "testManualInstallSucceeded", mUserId);
        } finally {
            setInstallPackageAppOps(PACKAGE_INSTALLER_PKG, false, mUserId);
            String command = "rm " + TEST_APP_LOCATION + apk.getName();
            getDevice().executeShellCommand(command);
            getDevice().uninstallPackage(TEST_APP_PKG);
            getDevice().uninstallPackage(PACKAGE_INSTALLER_PKG);
        }
    }

    @Test
    public void testAudioRestriction() throws Exception {
        // This package may need to toggle zen mode for this test, so allow it to do so.
        allowNotificationPolicyAccess(DEVICE_ADMIN_PKG, mUserId);
        try {
            executeDeviceTestClass(".AudioRestrictionTest");
        } finally {
            disallowNotificationPolicyAccess(DEVICE_ADMIN_PKG, mUserId);
        }
    }

    @Test
    public void testDisallowAdjustVolumeMutedLogged() throws Exception {
        assertMetricsLogged(getDevice(), () -> {
            executeDeviceTestMethod(".DevicePolicyLoggingTest",
                    "testDisallowAdjustVolumeMutedLogged");
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_MASTER_VOLUME_MUTED_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .setBoolean(true)
                    .build(),
            new DevicePolicyEventWrapper.Builder(EventId.SET_MASTER_VOLUME_MUTED_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .setBoolean(false)
                    .build());
    }

    @LargeTest
    @Test
    public void testLockTaskAfterReboot() throws Exception {
        try {
            // Just start kiosk mode
            executeDeviceTestMethod(
                    ".LockTaskHostDrivenTest", "testStartLockTask_noAsserts");

            // Reboot while in kiosk mode and then unlock the device
            rebootAndWaitUntilReady();

            // Check that kiosk mode is working and can't be interrupted
            executeDeviceTestMethod(".LockTaskHostDrivenTest",
                    "testLockTaskIsActiveAndCantBeInterrupted");
        } finally {
            executeDeviceTestMethod(".LockTaskHostDrivenTest", "testCleanupLockTask_noAsserts");
        }
    }

    @LargeTest
    @Test
    @Ignore("Ignored while migrating to new infrastructure b/175377361")
    public void testLockTaskAfterReboot_tryOpeningSettings() throws Exception {
        try {
            // Just start kiosk mode
            executeDeviceTestMethod(
                    ".LockTaskHostDrivenTest", "testStartLockTask_noAsserts");

            // Reboot while in kiosk mode and then unlock the device
            rebootAndWaitUntilReady();

            // Wait for the LockTask starting
            waitForBroadcastIdle();

            // Make sure that the LockTaskUtilityActivityIfWhitelisted was started.
            executeDeviceTestMethod(".LockTaskHostDrivenTest", "testLockTaskIsActive");

            // Try to open settings via adb
            executeShellCommand("am start -a android.settings.SETTINGS");

            // Check again
            executeDeviceTestMethod(".LockTaskHostDrivenTest",
                    "testLockTaskIsActiveAndCantBeInterrupted");
        } finally {
            executeDeviceTestMethod(".LockTaskHostDrivenTest", "testCleanupLockTask_noAsserts");
        }
    }

    @FlakyTest(bugId = 141314026)
    @Test
    public void testSuspendPackage() throws Exception {
        CLog.i("runTestSuspendPackage() on user %d", mUserId);

        installAppAsUser(INTENT_SENDER_APK, mUserId);
        installAppAsUser(INTENT_RECEIVER_APK, mUserId);
        assertMetricsLogged(getDevice(), () -> {
            // Suspend a testing package.
            executeDeviceTestMethod(".SuspendPackageTest", "testSetPackagesSuspended");
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_PACKAGES_SUSPENDED_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .setStrings(INTENT_RECEIVER_PKG)
                    .setBoolean(false)
                    .build());
        // Verify that the package is suspended from the PREVIOUS test and that the dialog is shown
        executeSuspendPackageTestMethod("testPackageSuspended");

        // Undo the suspend.
        executeDeviceTestMethod(".SuspendPackageTest", "testSetPackagesNotSuspended");
        // Verify that the package is not suspended from the PREVIOUS test and that the app launches
        executeSuspendPackageTestMethod("testPackageNotSuspended");

        // Verify we cannot suspend not suspendable packages.
        executeDeviceTestMethod(".SuspendPackageTest", "testSuspendNotSuspendablePackages");
    }

    @FlakyTest(bugId = 141314026)
    @Test
    public void testSuspendPackageWithPackageManager() throws Exception {
        CLog.i("runTestSuspendPackageWithPackageManager() on user %d", mUserId);

        installAppAsUser(INTENT_SENDER_APK, mUserId);
        installAppAsUser(INTENT_RECEIVER_APK, mUserId);
        // Suspend a testing package with the PackageManager
        executeDeviceTestMethod(".SuspendPackageTest",
                "testSetPackagesSuspendedWithPackageManager");
        // Verify that the package is suspended from the PREVIOUS test and that the dialog is shown
        executeSuspendPackageTestMethod("testPackageSuspendedWithPackageManager");

        // Undo the suspend.
        executeDeviceTestMethod(".SuspendPackageTest",
                "testSetPackagesNotSuspendedWithPackageManager");
        // Verify that the package is not suspended from the PREVIOUS test and that the app launches
        executeSuspendPackageTestMethod("testPackageNotSuspended");
    }
    @Test
    public void testRequiredStrongAuthTimeout() throws Exception {
        assumeHasSecureLockScreenFeature();

        executeDeviceTestClass(".RequiredStrongAuthTimeoutTest");
    }

    @Test
    public void testPasswordSufficientInitially() throws Exception {
        executeDeviceTestClass(".PasswordSufficientInitiallyTest");
    }

    @Test
    public void testGetCurrentFailedPasswordAttempts() throws Exception {
        assumeHasSecureLockScreenFeature();

        final String wrongPassword = TEST_PASSWORD + "5";

        changeUserCredential(TEST_PASSWORD, null /*oldCredential*/, mUserId);
        try {
            // Test that before trying an incorrect password there are 0 failed attempts.
            executeDeviceTestMethod(".GetCurrentFailedPasswordAttemptsTest",
                    "testNoFailedPasswordAttempts");
            // Try an incorrect password.
            assertFalse(verifyUserCredentialIsCorrect(wrongPassword, mUserId));
            // Test that now there is one failed attempt.
            executeDeviceTestMethod(".GetCurrentFailedPasswordAttemptsTest",
                    "testOneFailedPasswordAttempt");
            // Try an incorrect password.
            assertFalse(verifyUserCredentialIsCorrect(wrongPassword, mUserId));
            // Test that now there are two failed attempts.
            executeDeviceTestMethod(".GetCurrentFailedPasswordAttemptsTest",
                    "testTwoFailedPasswordAttempts");
            // TODO: re-enable the test below when b/110945754 is fixed.
            // Try the correct password and check the failed attempts number has been reset to 0.
            // assertTrue(verifyUserCredentialIsCorrect(testPassword, mUserId));
            // executeDeviceTestMethod(".GetCurrentFailedPasswordAttemptsTest",
            //         "testNoFailedPasswordAttempts");
        } finally {
            changeUserCredential(null /*newCredential*/, TEST_PASSWORD, mUserId);
        }
    }

    @Test
    public void testPasswordExpiration() throws Exception {
        assumeHasSecureLockScreenFeature();

        executeDeviceTestClass(".PasswordExpirationTest");
    }

    @Test
    public void testGetPasswordExpiration() throws Exception {
        assumeHasSecureLockScreenFeature();

        executeDeviceTestMethod(".GetPasswordExpirationTest",
                "testGetPasswordExpiration");
        try {
            executeDeviceTestMethod(".GetPasswordExpirationTest",
                    "testGetPasswordExpirationUpdatedAfterPasswordReset_beforeReset");
            // Wait for 20 seconds so we can make sure that the expiration date is refreshed later.
            RunUtil.getDefault().sleep(20000);
            changeUserCredential(TEST_PASSWORD, null, mUserId);
            executeDeviceTestMethod(".GetPasswordExpirationTest",
                    "testGetPasswordExpirationUpdatedAfterPasswordReset_afterReset");
        } finally {
            changeUserCredential(null, TEST_PASSWORD, mUserId);
        }
    }

    @Test
    public void testPasswordQualityWithoutSecureLockScreen() throws Exception {
        assumeDoesNotHaveSecureLockScreenFeature();

        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".UnavailableSecureLockScreenTest", mUserId);
    }

    @Test
    public void testSetSystemSetting() throws Exception {
        executeDeviceTestClass(".SetSystemSettingTest");
    }

    @TemporarilyIgnoreOnHeadlessSystemUserMode(bugId = "197859595",
            reason = "Will be migrated to new test infra")
    @Test
    public void testKeyManagement() throws Exception {
        installAppAsUser(SHARED_UID_APP1_APK, mUserId);
        installAppAsUser(SHARED_UID_APP2_APK, mUserId);

        executeDeviceTestClass(".KeyManagementTest");
    }

    @TemporarilyIgnoreOnHeadlessSystemUserMode(bugId = "197859595",
            reason = "Will be migrated to new test infra")
    @Test
    // TODO(b/198408853): Migrate
    public void testGenerateKeyPairLogged() throws Exception {
        assumeTrue(mHasAttestation);

        assertMetricsLogged(getDevice(), () -> {
                executeDeviceTestMethod(
                        ".KeyManagementTest", "testCanGenerateKeyPairWithKeyAttestation");
                }, new DevicePolicyEventWrapper.Builder(EventId.GENERATE_KEY_PAIR_VALUE)
                .setAdminPackageName(DEVICE_ADMIN_PKG)
                .setBoolean(false)
                .setInt(0)
                .setStrings("RSA", "notCredentialManagementApp")
                .build(),
                new DevicePolicyEventWrapper.Builder(EventId.GENERATE_KEY_PAIR_VALUE)
                .setAdminPackageName(DEVICE_ADMIN_PKG)
                .setBoolean(false)
                .setInt(0)
                .setStrings("EC", "notCredentialManagementApp")
                .build());

    }

    @Test
    public void testSetKeyPairCertificateLogged() throws Exception {
        assertMetricsLogged(getDevice(), () -> {
                executeDeviceTestMethod(".KeyManagementTest", "testCanSetKeyPairCert");
                }, new DevicePolicyEventWrapper.Builder(EventId.SET_KEY_PAIR_CERTIFICATE_VALUE)
                .setAdminPackageName(DEVICE_ADMIN_PKG)
                .setBoolean(false)
                .setStrings("notCredentialManagementApp")
                .build());
    }

    @Test
    public void testPermittedInputMethods() throws Exception {
        executeDeviceTestMethod(".InputMethodsTest", "testPermittedInputMethodsThrowsIfWrongAdmin");
        assertMetricsLogged(getDevice(), () -> {
            executeDeviceTestMethod(".InputMethodsTest", "testPermittedInputMethods");
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_PERMITTED_INPUT_METHODS_VALUE)
                .setAdminPackageName(DEVICE_ADMIN_PKG)
                .setStrings(NOT_CALLED_FROM_PARENT, new String[0])
                .build(),
        new DevicePolicyEventWrapper.Builder(EventId.SET_PERMITTED_INPUT_METHODS_VALUE)
                .setAdminPackageName(DEVICE_ADMIN_PKG)
                .setStrings(NOT_CALLED_FROM_PARENT, new String[0])
                .build(),
        new DevicePolicyEventWrapper.Builder(EventId.SET_PERMITTED_INPUT_METHODS_VALUE)
                .setAdminPackageName(DEVICE_ADMIN_PKG)
                .setStrings(NOT_CALLED_FROM_PARENT, "com.google.pkg.one", "com.google.pkg.two")
                .build());
    }

    @Test
    public void testSetKeyguardDisabledFeaturesLogged() throws Exception {
        assertMetricsLogged(getDevice(), () -> {
            executeDeviceTestMethod(
                    ".DevicePolicyLoggingTest", "testSetKeyguardDisabledFeaturesLogged");
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_KEYGUARD_DISABLED_FEATURES_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .setInt(KEYGUARD_DISABLE_FEATURES_NONE)
                    .setStrings(NOT_CALLED_FROM_PARENT)
                    .build(),
            new DevicePolicyEventWrapper.Builder(EventId.SET_KEYGUARD_DISABLED_FEATURES_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .setInt(KEYGUARD_DISABLE_FINGERPRINT)
                    .setStrings(NOT_CALLED_FROM_PARENT)
                    .build(),
            new DevicePolicyEventWrapper.Builder(EventId.SET_KEYGUARD_DISABLED_FEATURES_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .setInt(KEYGUARD_DISABLE_TRUST_AGENTS)
                    .setStrings(NOT_CALLED_FROM_PARENT)
                    .build(),
            new DevicePolicyEventWrapper.Builder(EventId.SET_KEYGUARD_DISABLED_FEATURES_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .setInt(KEYGUARD_DISABLE_FEATURES_NONE)
                    .setStrings(NOT_CALLED_FROM_PARENT)
                    .build());
    }

    @Test
    public void testSetKeyguardDisabledSecureCameraLogged() throws Exception {
        assertMetricsLogged(getDevice(), () -> {
            executeDeviceTestMethod(
                    ".DevicePolicyLoggingTest", "testSetKeyguardDisabledSecureCameraLogged");
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_KEYGUARD_DISABLED_FEATURES_VALUE)
                .setAdminPackageName(DEVICE_ADMIN_PKG)
                .setInt(KEYGUARD_DISABLE_SECURE_CAMERA)
                .setStrings(NOT_CALLED_FROM_PARENT)
                .build());
    }

    @Test
    public void testSetUserRestrictionLogged() throws Exception {
        assertMetricsLogged(getDevice(), () -> {
            executeDeviceTestMethod(
                    ".DevicePolicyLoggingTest", "testSetUserRestrictionLogged");
        }, new DevicePolicyEventWrapper.Builder(EventId.ADD_USER_RESTRICTION_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .setStrings(DISALLOW_CONFIG_LOCATION, NOT_CALLED_FROM_PARENT)
                    .build(),
            new DevicePolicyEventWrapper.Builder(EventId.REMOVE_USER_RESTRICTION_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .setStrings(DISALLOW_CONFIG_LOCATION, NOT_CALLED_FROM_PARENT)
                    .build(),
            new DevicePolicyEventWrapper.Builder(EventId.ADD_USER_RESTRICTION_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .setStrings(DISALLOW_ADJUST_VOLUME, NOT_CALLED_FROM_PARENT)
                    .build(),
            new DevicePolicyEventWrapper.Builder(EventId.REMOVE_USER_RESTRICTION_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .setStrings(DISALLOW_ADJUST_VOLUME, NOT_CALLED_FROM_PARENT)
                    .build(),
            new DevicePolicyEventWrapper.Builder(EventId.ADD_USER_RESTRICTION_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .setStrings(DISALLOW_AUTOFILL, NOT_CALLED_FROM_PARENT)
                    .build(),
            new DevicePolicyEventWrapper.Builder(EventId.REMOVE_USER_RESTRICTION_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .setStrings(DISALLOW_AUTOFILL, NOT_CALLED_FROM_PARENT)
                    .build()
        );
    }

    @Test
    public void testSetSecureSettingLogged() throws Exception {
        assertMetricsLogged(getDevice(), () -> {
            executeDeviceTestMethod(
                    ".DevicePolicyLoggingTest", "testSetSecureSettingLogged");
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_SECURE_SETTING_VALUE)
                .setAdminPackageName(DEVICE_ADMIN_PKG)
                .setStrings(SKIP_FIRST_USE_HINTS, "1")
                .build(),
            new DevicePolicyEventWrapper.Builder(EventId.SET_SECURE_SETTING_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .setStrings(SKIP_FIRST_USE_HINTS, "0")
                    .build(),
            new DevicePolicyEventWrapper.Builder(EventId.SET_SECURE_SETTING_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .setStrings(DEFAULT_INPUT_METHOD, "com.example.input")
                    .build()
                    ,
            new DevicePolicyEventWrapper.Builder(EventId.SET_SECURE_SETTING_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .setStrings(DEFAULT_INPUT_METHOD)
                    .build());
    }

    @Test
    public void testSetPermissionPolicyLogged() throws Exception {
        assertMetricsLogged(getDevice(), () -> {
            executeDeviceTestMethod(
                    ".DevicePolicyLoggingTest", "testSetPermissionPolicyLogged");
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_PERMISSION_POLICY_VALUE)
                .setAdminPackageName(DEVICE_ADMIN_PKG)
                .setInt(PERMISSION_POLICY_AUTO_DENY)
                .setBoolean(false)
                .build(),
        new DevicePolicyEventWrapper.Builder(EventId.SET_PERMISSION_POLICY_VALUE)
                .setAdminPackageName(DEVICE_ADMIN_PKG)
                .setInt(PERMISSION_POLICY_AUTO_GRANT)
                .setBoolean(false)
                .build(),
        new DevicePolicyEventWrapper.Builder(EventId.SET_PERMISSION_POLICY_VALUE)
                .setAdminPackageName(DEVICE_ADMIN_PKG)
                .setInt(PERMISSION_POLICY_PROMPT)
                .setBoolean(false)
                .build());
    }

    @Test
    public void testSetPermissionGrantStateLogged() throws Exception {
        installAppPermissionAppAsUser();
        assertMetricsLogged(getDevice(), () -> {
            executeDeviceTestMethod(
                    ".DevicePolicyLoggingTest", "testSetPermissionGrantStateLogged");
        }, new DevicePolicyEventWrapper.Builder(EventId.SET_PERMISSION_GRANT_STATE_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .setInt(PERMISSION_GRANT_STATE_GRANTED)
                    .setBoolean(false)
                    .setStrings("android.permission.READ_CONTACTS")
                    .build(),
            new DevicePolicyEventWrapper.Builder(EventId.SET_PERMISSION_GRANT_STATE_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .setInt(PERMISSION_GRANT_STATE_DENIED)
                    .setBoolean(false)
                    .setStrings("android.permission.READ_CONTACTS")
                    .build(),
            new DevicePolicyEventWrapper.Builder(EventId.SET_PERMISSION_GRANT_STATE_VALUE)
                    .setAdminPackageName(DEVICE_ADMIN_PKG)
                    .setInt(PERMISSION_GRANT_STATE_DEFAULT)
                    .setBoolean(false)
                    .setStrings("android.permission.READ_CONTACTS")
                    .build());
    }

    @Test
    public void testEnableSystemAppWithIntentLogged() throws Exception {
        final String systemPackageToEnable = getLaunchableSystemPackage();
        if (systemPackageToEnable == null) {
            return;
        }
        final Map<String, String> params =
                ImmutableMap.of(PARAM_APP_TO_ENABLE, systemPackageToEnable);
        assertMetricsLogged(getDevice(), () -> {
            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DevicePolicyLoggingTest",
                    "testEnableSystemAppWithIntentLogged", mUserId, params);
        }, new DevicePolicyEventWrapper.Builder(EventId.ENABLE_SYSTEM_APP_WITH_INTENT_VALUE)
                .setAdminPackageName(DEVICE_ADMIN_PKG)
                .setBoolean(false)
                .setStrings("android.intent.action.MAIN")
                .build());
    }

    @Test
    public void testIsDeviceOrganizationOwnedWithManagedProfile() throws Exception {
        executeDeviceTestMethod(".DeviceOwnershipTest",
                "testCallingIsOrganizationOwnedWithManagedProfileExpectingFalse");
    }

    @LockSettingsTest
    @Test
    public void testSecondaryLockscreen() throws Exception {
        executeDeviceTestClass(".SecondaryLockscreenTest");
    }

    private String getLaunchableSystemPackage() throws DeviceNotAvailableException {
        final List<String> enabledSystemPackageNames = getEnabledSystemPackageNames();
        for (String enabledSystemPackage : enabledSystemPackageNames) {
            final String result = getDevice().executeShellCommand(
                    String.format(RESOLVE_ACTIVITY_CMD, mUserId, enabledSystemPackage));
            if (!result.contains("No activity found")) {
                return enabledSystemPackage;
            }
        }
        return null;
    }

    private List<String> getEnabledSystemPackageNames() throws DeviceNotAvailableException {
        final String commandResult =
                getDevice().executeShellCommand("pm list packages -e -s --user " + mUserId);
        final int prefixLength = "package:".length();
        return new ArrayList<>(Arrays.asList(commandResult.split("\n")))
                .stream()
                .map(line -> line.substring(prefixLength))
                .collect(Collectors.toList());
    }

    @Test
    public void testAdminControlOverSensorPermissionGrantsDefault() throws Exception {
        // By default, admin should not be able to grant sensors-related permissions.
        executeDeviceTestMethod(".SensorPermissionGrantTest",
                "testAdminCannotGrantSensorsPermission");
    }

    /**
     * Executes a test class on device. Prior to running, turn off background data usage
     * restrictions, and restore the original restrictions after the test.
     */
    private void executeDeviceTestClassNoRestrictBackground(String className, int userId)
            throws Exception {
        boolean originalRestriction = ensureRestrictBackgroundPolicyOff();
        try {
            executeDeviceTestClass(className, userId);
        } finally {
            // if the test throws exception, still restore the policy
            restoreRestrictBackgroundPolicyTo(originalRestriction);
        }
    }

    protected void executeDeviceTestClass(String className) throws Exception {
        executeDeviceTestMethod(className, /* testName= */ null);
    }

    protected void executeDeviceTestClass(String className, int userId) throws Exception {
        executeDeviceTestMethod(className, /* testName= */ null, userId);
    }

    protected void executeDeviceTestMethod(String className, String testName) throws Exception {
        executeDeviceTestMethod(className, testName, /* params= */ new HashMap<>());
    }

    protected void executeDeviceTestMethod(String className, String testName, int userId)
            throws Exception {
        executeDeviceTestMethod(className, testName, userId, /* params= */ new HashMap<>());
    }

    protected void executeDeviceTestMethod(String className, String testName,
            Map<String, String> params) throws Exception {
        executeDeviceTestMethod(className, testName, mUserId, params);
    }

    protected void executeDeviceTestMethod(String className, String testName, int userId,
            Map<String, String> params) throws Exception {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, className, testName, userId, params);
    }

    protected void installAppPermissionAppAsUser()
            throws FileNotFoundException, DeviceNotAvailableException {
        installAppPermissionAppAsUser(mUserId);
    }

    protected final void installAppPermissionAppAsUser(int userId)
            throws FileNotFoundException, DeviceNotAvailableException {
        installAppAsUser(PERMISSIONS_APP_APK, false, userId);
    }

    private void executeSuspendPackageTestMethod(String testName) throws Exception {
        runDeviceTestsAsUser(INTENT_SENDER_PKG, ".SuspendPackageTest", testName, mUserId);
    }

    private void changeUserRestrictionOrFail(String key, boolean value, int userId)
            throws DeviceNotAvailableException {
        changeUserRestrictionOrFail(key, value, userId, DEVICE_ADMIN_PKG);
    }

    protected void setDelegatedScopes(String packageName, List<String> scopes)
            throws DeviceNotAvailableException {
        final String packageNameExtra = "--es extra-package-name " + packageName;
        String scopesExtra = "";
        if (scopes != null && scopes.size() > 0) {
            scopesExtra = "--esa extra-scopes-list " + scopes.get(0);
            for (int i = 1; i < scopes.size(); ++i) {
                scopesExtra += "," + scopes.get(i);
            }
        }
        final String extras = packageNameExtra + " " + scopesExtra;

        changePolicyOrFail("set-delegated-scopes", extras, mUserId);
    }

    private void setInstallPackageAppOps(String packageName, boolean allowed, int userId)
            throws DeviceNotAvailableException {
        String command = "appops set --user " + userId + " " + packageName + " " +
                "REQUEST_INSTALL_PACKAGES "
                + (allowed ? "allow" : "default");
        CLog.d("Output for command " + command + ": " + getDevice().executeShellCommand(command));
    }

    private void changePolicyOrFail(String command, String extras, int userId)
            throws DeviceNotAvailableException {
        changePolicyOrFail(command, extras, userId, DEVICE_ADMIN_PKG);
    }

    /**
     * Start SimpleActivity synchronously in a particular user.
     */
    protected void startSimpleActivityAsUser(int userId) throws Exception {
        installAppAsUser(TEST_APP_APK, /* grantPermissions */ true, /* dontKillApp */ true, userId);
        startActivityAsUser(userId, TEST_APP_PKG, TEST_APP_PKG + ".SimpleActivity");
    }

    protected void setScreenCaptureDisabled_assist(int userId, boolean disabled) throws Exception {
        // Set the policy.
        String testMethodName = disabled
                ? "testSetScreenCaptureDisabled_true"
                : "testSetScreenCaptureDisabled_false";
        executeDeviceTestMethod(".ScreenCaptureDisabledTest", testMethodName);
        testMethodName = disabled
                ? "testScreenCaptureImpossible_assist"
                : "testScreenCapturePossible_assist";

        // Check whether the VoiceInteractionService can retrieve the screenshot.
        installAppAsUser(DEVICE_ADMIN_APK, mPrimaryUserId);

        if (userId == mPrimaryUserId) {
            // If testing for user-0, also make sure the existing screen can't be captured.
            runDeviceTestsAsUser(
                    DEVICE_ADMIN_PKG,
                    ".AssistScreenCaptureDisabledTest",
                    testMethodName,
                    mPrimaryUserId);
        }

        // Make sure the foreground activity is from the target user.
        startSimpleActivityAsUser(userId);

        runDeviceTestsAsUser(
                DEVICE_ADMIN_PKG,
                ".AssistScreenCaptureDisabledTest",
                testMethodName,
                mPrimaryUserId);
    }

    /**
     * Allows packageName to manage notification policy configuration, which
     * includes toggling zen mode.
     */
    private void allowNotificationPolicyAccess(String packageName, int userId)
            throws DeviceNotAvailableException {
        List<String> enabledPackages = getEnabledNotificationPolicyPackages(userId);
        if (!enabledPackages.contains(packageName)) {
            enabledPackages.add(packageName);
            setEnabledNotificationPolicyPackages(enabledPackages, userId);
        }
    }

    /**
     * Disallows packageName to manage notification policy configuration, which
     * includes toggling zen mode.
     */
    private void disallowNotificationPolicyAccess(String packageName, int userId)
            throws DeviceNotAvailableException {
        List<String> enabledPackages = getEnabledNotificationPolicyPackages(userId);
        if (enabledPackages.contains(packageName)) {
            enabledPackages.remove(packageName);
            setEnabledNotificationPolicyPackages(enabledPackages, userId);
        }
    }

    private void setEnabledNotificationPolicyPackages(List<String> packages, int userId)
            throws DeviceNotAvailableException {
        getDevice().setSetting(userId, "secure", ENABLED_NOTIFICATION_POLICY_ACCESS_PACKAGES,
                String.join(":", packages));
    }

    private List<String> getEnabledNotificationPolicyPackages(int userId)
            throws DeviceNotAvailableException {
        String settingValue = getDevice().getSetting(userId, "secure",
                ENABLED_NOTIFICATION_POLICY_ACCESS_PACKAGES);
        if (settingValue == null) {
            return new ArrayList<String>();
        }
        return new ArrayList<String>(Arrays.asList(settingValue.split(":|\n")));
    }

    protected void setVoiceInteractionService(String componentName)
            throws DeviceNotAvailableException {
        getDevice().setSetting(
                mPrimaryUserId, "secure", "voice_interaction_service", componentName);
        getDevice().setSetting(mPrimaryUserId, "secure", "assist_structure_enabled", "1");
        getDevice().setSetting(mPrimaryUserId, "secure", "assist_screenshot_enabled", "1");
    }

    protected void clearVoiceInteractionService() throws DeviceNotAvailableException {
        getDevice().executeShellCommand("settings delete secure voice_interaction_service");
    }

    /**
     * Ensure that restrict background policy is off.
     * Returns the original status of restrict background policy.
     */
    private boolean ensureRestrictBackgroundPolicyOff() throws Exception {
        String restriction = getDevice().executeShellCommand(RESTRICT_BACKGROUND_GET_CMD);
        if (restriction.contains("enabled")) {
            getDevice().executeShellCommand(RESTRICT_BACKGROUND_OFF_CMD);
            return true;
        }
        return false;
    }

    private void restoreRestrictBackgroundPolicyTo(boolean restricted) throws Exception {
        getDevice().executeShellCommand(
                restricted ? RESTRICT_BACKGROUND_ON_CMD : RESTRICT_BACKGROUND_OFF_CMD);
    }

    private void assumeIsNotWatch() throws Exception {
        assumeFalse("is watch", hasDeviceFeature("android.hardware.type.watch"));
    }
}
