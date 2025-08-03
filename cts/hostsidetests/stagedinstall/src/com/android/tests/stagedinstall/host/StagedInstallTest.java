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

package com.android.tests.stagedinstall.host;

import static com.android.cts.shim.lib.ShimPackage.SHIM_APEX_PACKAGE_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import android.cts.install.lib.host.InstallUtilsHost;
import android.platform.test.annotations.LargeTest;

import com.android.apex.ApexInfo;
import com.android.apex.XmlParser;
import com.android.ddmlib.Log;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;


import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(DeviceJUnit4ClassRunner.class)
public class StagedInstallTest extends BaseHostJUnit4Test {

    private static final String TAG = "StagedInstallTest";

    private static final String PACKAGE_NAME = "com.android.tests.stagedinstall";

    private static final String BROADCAST_RECEIVER_COMPONENT = PACKAGE_NAME + "/"
            + PACKAGE_NAME + ".LauncherActivity";

    private final InstallUtilsHost mHostUtils = new InstallUtilsHost(this);

    private String mDefaultLauncher = null;

    @Rule
    public final FailedTestLogHook mFailedTestLogHook = new FailedTestLogHook(this);

    /**
     * Runs the given phase of a test by calling into the device.
     * Throws an exception if the test phase fails.
     * <p>
     * For example, <code>runPhase("testInstallStagedApkCommit");</code>
     */
    private void runPhase(String phase) throws Exception {
        assertThat(runDeviceTests(PACKAGE_NAME,
                "com.android.tests.stagedinstall.StagedInstallTest",
                phase)).isTrue();
    }

    // We do not assert the success of cleanup phase since it might fail due to flaky reasons.
    private void cleanUp() throws Exception {
        try {
            runDeviceTests(PACKAGE_NAME,
                    "com.android.tests.stagedinstall.StagedInstallTest",
                    "cleanUp");
        } catch (AssertionError e) {
            Log.e(TAG, e);
        }
    }

    @Before
    public void setUp() throws Exception {
        cleanUp();
        mHostUtils.uninstallShimApexIfNecessary();
        storeDefaultLauncher();
    }

    @After
    public void tearDown() throws Exception {
        cleanUp();
        mHostUtils.uninstallShimApexIfNecessary();
        setDefaultLauncher(mDefaultLauncher);
    }

    /**
     * Tests for staged install involving only one apk.
     */
    @Test
    @LargeTest
    public void testInstallStagedApk() throws Exception {
        assumeSystemUser();

        setDefaultLauncher(BROADCAST_RECEIVER_COMPONENT);
        runPhase("testInstallStagedApk_Commit");
        getDevice().reboot();
        runPhase("testInstallStagedApk_VerifyPostReboot");
        runPhase("testInstallStagedApk_AbandonSessionIsNoop");
    }

    @Test
    public void testFailInstallIfNoPermission() throws Exception {
        runPhase("testFailInstallIfNoPermission");
    }

    @Test
    @LargeTest
    public void testAbandonStagedApkBeforeReboot() throws Exception {
        runPhase("testAbandonStagedApkBeforeReboot_CommitAndAbandon");
        getDevice().reboot();
        runPhase("testAbandonStagedApkBeforeReboot_VerifyPostReboot");
    }

    @Test
    @LargeTest
    public void testAbandonStagedApkBeforeReady() throws Exception {
        runPhase("testAbandonStagedApkBeforeReady_CommitAndAbandon");
        getDevice().reboot();
        runPhase("testAbandonStagedApkBeforeReady_VerifyPostReboot");
    }

    @Test
    public void testStageAnotherSessionImmediatelyAfterAbandon() throws Exception {
        runPhase("testStageAnotherSessionImmediatelyAfterAbandon");
    }

    @Test
    public void testStageAnotherSessionImmediatelyAfterAbandonMultiPackage() throws Exception {
        runPhase("testStageAnotherSessionImmediatelyAfterAbandonMultiPackage");
    }

    @Test
    public void testNoSessionUpdatedBroadcastSentForStagedSessionAbandon() throws Exception {
        runPhase("testNoSessionUpdatedBroadcastSentForStagedSessionAbandon");
    }

    @Test
    @LargeTest
    public void testInstallMultipleStagedApks() throws Exception {
        assumeSystemUser();

        setDefaultLauncher(BROADCAST_RECEIVER_COMPONENT);
        runPhase("testInstallMultipleStagedApks_Commit");
        getDevice().reboot();
        runPhase("testInstallMultipleStagedApks_VerifyPostReboot");
    }

    private void assumeSystemUser() throws Exception {
        String systemUser = "0";
        assumeThat("Current user is not system user",
                getDevice().executeShellCommand("am get-current-user").trim(), equalTo(systemUser));
    }

    @Test
    public void testGetActiveStagedSessions() throws Exception {
        assumeTrue("Device does not support file-system checkpoint",
                mHostUtils.isCheckpointSupported());

        runPhase("testGetActiveStagedSessions");
    }

    /**
     * Verifies that active staged session fulfils conditions stated at
     * {@link PackageInstaller.SessionInfo#isStagedSessionActive}
     */
    @Test
    public void testIsStagedSessionActive() throws Exception {
        runPhase("testIsStagedSessionActive");
    }

    @Test
    public void testGetActiveStagedSessionsNoSessionActive() throws Exception {
        runPhase("testGetActiveStagedSessionsNoSessionActive");
    }

    @Test
    public void testGetActiveStagedSessions_MultiApkSession() throws Exception {
        assumeTrue("Device does not support file-system checkpoint",
                mHostUtils.isCheckpointSupported());

        runPhase("testGetActiveStagedSessions_MultiApkSession");
    }

    @Test
    public void testStagedInstallDowngrade_DowngradeNotRequested_Fails() throws Exception {
        runPhase("testStagedInstallDowngrade_DowngradeNotRequested_Fails_Commit");
    }

    @Test
    @LargeTest
    public void testStagedInstallDowngrade_DowngradeRequested_DebugBuild() throws Exception {
        assumeThat(getDevice().getBuildFlavor(), not(endsWith("-user")));

        runPhase("testStagedInstallDowngrade_DowngradeRequested_Commit");
        getDevice().reboot();
        runPhase("testStagedInstallDowngrade_DowngradeRequested_DebugBuild_VerifyPostReboot");
    }

    @Test
    public void testStagedInstallDowngrade_DowngradeRequested_UserBuild() throws Exception {
        assumeThat(getDevice().getBuildFlavor(), endsWith("-user"));
        assumeFalse("Device is debuggable", isDebuggable());

        runPhase("testStagedInstallDowngrade_DowngradeRequested_Fails_Commit");
    }

    @Test
    public void testShimApexShouldPreInstalledIfUpdatingApexIsSupported() throws Exception {
        final ITestDevice.ApexInfo shimApex = mHostUtils.getShimApex().orElseThrow(
                () -> new AssertionError("Can't find " + SHIM_APEX_PACKAGE_NAME)
        );
        assertThat(shimApex.versionCode).isEqualTo(1);
    }

    @Test
    @LargeTest
    public void testInstallStagedApex() throws Exception {
        setDefaultLauncher(BROADCAST_RECEIVER_COMPONENT);
        runPhase("testInstallStagedApex_Commit");
        getDevice().reboot();
        runPhase("testInstallStagedApex_VerifyPostReboot");
    }

    @Test
    // Don't mark as @LargeTest since we want at least one test to install apex during pre-submit.
    public void testInstallStagedApexAndApk() throws Exception {
        assumeSystemUser();

        setDefaultLauncher(BROADCAST_RECEIVER_COMPONENT);
        runPhase("testInstallStagedApexAndApk_Commit");
        getDevice().reboot();
        runPhase("testInstallStagedApexAndApk_VerifyPostReboot");
    }

    @Test
    public void testsFailsNonStagedApexInstall() throws Exception {
        runPhase("testsFailsNonStagedApexInstall");
    }

    @Test
    public void testInstallStagedNonPreInstalledApex_Fails() throws Exception {
        runPhase("testInstallStagedNonPreInstalledApex_Fails");
    }

    @Test
    public void testInstallStagedDifferentPackageNameWithInstalledApex_Fails() throws Exception {
        runPhase("testInstallStagedDifferentPackageNameWithInstalledApex_Fails");
    }

    @Test
    @LargeTest
    public void testStageApkWithSameNameAsApexShouldFail() throws Exception {
        runPhase("testStageApkWithSameNameAsApexShouldFail_Commit");
        getDevice().reboot();
        runPhase("testStageApkWithSameNameAsApexShouldFail_VerifyPostReboot");
    }

    @Test
    public void testNonStagedInstallApkWithSameNameAsApexShouldFail() throws Exception {
        runPhase("testNonStagedInstallApkWithSameNameAsApexShouldFail");
    }

    @Test
    @LargeTest
    public void testStagedInstallDowngradeApex_DowngradeNotRequested_Fails() throws Exception {
        installV3Apex();
        runPhase("testStagedInstallDowngradeApex_DowngradeNotRequested_Fails_Commit");
        getDevice().reboot();
        runPhase("testStagedInstallDowngradeApex_DowngradeNotRequested_Fails_VerifyPostReboot");
    }

    @Test
    @LargeTest
    public void testStagedInstallDowngradeApex_DowngradeRequested_DebugBuild() throws Exception {
        assumeThat(getDevice().getBuildFlavor(), not(endsWith("-user")));

        installV3Apex();
        runPhase("testStagedInstallDowngradeApex_DowngradeRequested_DebugBuild_Commit");
        getDevice().reboot();
        runPhase("testStagedInstallDowngradeApex_DowngradeRequested_DebugBuild_VerifyPostReboot");
    }

    @Test
    @LargeTest
    public void testStagedInstallDowngradeApex_DowngradeRequested_UserBuild_Fails()
            throws Exception {
        assumeThat(getDevice().getBuildFlavor(), endsWith("-user"));
        assumeFalse("Device is debuggable", isDebuggable());

        installV3Apex();
        runPhase("testStagedInstallDowngradeApex_DowngradeRequested_UserBuild_Fails_Commit");
        getDevice().reboot();
        runPhase("testStagedInstallDowngradeApex_DowngradeRequested_UserBuild_Fails_"
                + "VerifyPostReboot");
    }

    @Test
    @LargeTest
    public void testStagedInstallDowngradeApexToSystemVersion_DebugBuild() throws Exception {
        assumeThat(getDevice().getBuildFlavor(), not(endsWith("-user")));

        installV2Apex();
        runPhase("testStagedInstallDowngradeApexToSystemVersion_DebugBuild_Commit");
        getDevice().reboot();
        runPhase("testStagedInstallDowngradeApexToSystemVersion_DebugBuild_VerifyPostReboot");
    }

    @Test
    @LargeTest
    public void testInstallStagedApex_SameGrade() throws Exception {
        installV3Apex();
        ApexInfo shim1 =
                readApexInfoList().stream()
                        .filter(a -> a.getModuleName().equals(SHIM_APEX_PACKAGE_NAME))
                        .filter(ApexInfo::getIsActive)
                        .findAny()
                        .orElseThrow(() ->
                                new AssertionError(
                                        "No active version of " + SHIM_APEX_PACKAGE_NAME
                                                + " found in /apex/apex-info-list.xml"));

        installV3Apex();
        ApexInfo shim2 =
                readApexInfoList().stream()
                        .filter(a -> a.getModuleName().equals(SHIM_APEX_PACKAGE_NAME))
                        .filter(ApexInfo::getIsActive)
                        .findAny()
                        .orElseThrow(() ->
                                new AssertionError(
                                        "No active version of " + SHIM_APEX_PACKAGE_NAME
                                                + " found in /apex/apex-info-list.xml"));
        assertThat(shim1.getLastUpdateMillis()).isNotEqualTo(shim2.getLastUpdateMillis());
    }

    @Test
    @LargeTest
    public void testInstallStagedApex_SameGrade_NewOneWins() throws Exception {
        installV2Apex();

        runPhase("testInstallStagedApex_SameGrade_NewOneWins_Commit");
        getDevice().reboot();
        runPhase("testInstallStagedApex_SameGrade_NewOneWins_VerifyPostReboot");
    }

    private void installV2Apex()throws Exception {
        runPhase("testInstallV2Apex_Commit");
        getDevice().reboot();
        runPhase("testInstallV2Apex_VerifyPostReboot");
    }

    private void installV2SignedBobApex() throws Exception {
        runPhase("testInstallV2SignedBobApex_Commit");
        getDevice().reboot();
        runPhase("testInstallV2SignedBobApex_VerifyPostReboot");
    }

    private void installV3Apex()throws Exception {
        runPhase("testInstallV3Apex_Commit");
        getDevice().reboot();
        runPhase("testInstallV3Apex_VerifyPostReboot");
    }

    @Test
    public void testFailsInvalidApexInstall() throws Exception {
        runPhase("testFailsInvalidApexInstall_Commit");
        runPhase("testFailsInvalidApexInstall_AbandonSessionIsNoop");
    }

    @Test
    public void testStagedApkSessionCallbacks() throws Exception {
        runPhase("testStagedApkSessionCallbacks");
    }

    @Test
    @LargeTest
    public void testInstallStagedApexWithoutApexSuffix() throws Exception {
        runPhase("testInstallStagedApexWithoutApexSuffix_Commit");
        getDevice().reboot();
        runPhase("testInstallStagedApexWithoutApexSuffix_VerifyPostReboot");
    }

    @Test
    public void testRejectsApexDifferentCertificate() throws Exception {
        runPhase("testRejectsApexDifferentCertificate");
    }

    /**
     * Tests for staged install involving rotated keys.
     *
     * Here alice means the original default key that cts.shim.v1 package was signed with and
     * bob is the new key alice rotates to. Where ambiguous, we will refer keys as alice and bob
     * instead of "old key" and "new key".
     *
     * By default, rotated keys have rollback capability enabled for old keys. When we remove
     * rollback capability from a key, it is called "Distrusting Event" and the distrusted key can
     * not update the app anymore.
     */

    // Should not be able to update with a key that has not been rotated.
    @Test
    public void testUpdateWithDifferentKeyButNoRotation() throws Exception {
        runPhase("testUpdateWithDifferentKeyButNoRotation");
    }

    // Should be able to update with a key that has been rotated.
    @Test
    @LargeTest
    public void testUpdateWithDifferentKey() throws Exception {
        runPhase("testUpdateWithDifferentKey_Commit");
        getDevice().reboot();
        runPhase("testUpdateWithDifferentKey_VerifyPostReboot");
    }

    // Should not be able to update with a key that is no longer trusted (i.e, has no
    // rollback capability)
    @Test
    @LargeTest
    public void testUntrustedOldKeyIsRejected() throws Exception {
        installV2SignedBobApex();
        runPhase("testUntrustedOldKeyIsRejected");
    }

    // Should be able to update with an old key which is trusted
    @Test
    @LargeTest
    public void testTrustedOldKeyIsAccepted() throws Exception {
        runPhase("testTrustedOldKeyIsAccepted_Commit");
        getDevice().reboot();
        runPhase("testTrustedOldKeyIsAccepted_CommitPostReboot");
        getDevice().reboot();
        runPhase("testTrustedOldKeyIsAccepted_VerifyPostReboot");
    }

    // Should be able to update further with rotated key
    @Test
    @LargeTest
    public void testAfterRotationNewKeyCanUpdateFurther() throws Exception {
        installV2SignedBobApex();
        runPhase("testAfterRotationNewKeyCanUpdateFurther_CommitPostReboot");
        getDevice().reboot();
        runPhase("testAfterRotationNewKeyCanUpdateFurther_VerifyPostReboot");
    }

    @Test
    @LargeTest
    public void testAfterRotationNewKeyCanUpdateFurtherWithoutLineage() throws Exception {
        installV2SignedBobApex();
        runPhase("testAfterRotationNewKeyCanUpdateFurtherWithoutLineage");
    }

    /**
     * Tests for staging and installing multiple staged sessions.
     */

    // Should fail to stage multiple sessions when check-point is not available
    @Test
    public void testFailStagingMultipleSessionsIfNoCheckPoint() throws Exception {
        assumeFalse("Device supports file-system checkpoint",
                mHostUtils.isCheckpointSupported());

        runPhase("testFailStagingMultipleSessionsIfNoCheckPoint");
    }

    @Test
    public void testFailOverlappingMultipleStagedInstall_BothSinglePackage_Apk() throws Exception {
        assumeTrue("Device does not support file-system checkpoint",
                mHostUtils.isCheckpointSupported());

        runPhase("testFailOverlappingMultipleStagedInstall_BothSinglePackage_Apk");
    }

    @Test
    public void testAllowNonOverlappingMultipleStagedInstall_MultiPackageSinglePackage_Apk()
            throws Exception {
        assumeTrue("Device does not support file-system checkpoint",
                mHostUtils.isCheckpointSupported());

        runPhase("testAllowNonOverlappingMultipleStagedInstall_MultiPackageSinglePackage_Apk");
    }

    @Test
    public void testFailOverlappingMultipleStagedInstall_BothMultiPackage_Apk() throws Exception {
        assumeTrue("Device does not support file-system checkpoint",
                mHostUtils.isCheckpointSupported());

        runPhase("testFailOverlappingMultipleStagedInstall_BothMultiPackage_Apk");
    }

    // Test for installing multiple staged sessions at the same time
    @Test
    @LargeTest
    public void testMultipleStagedInstall_ApkOnly() throws Exception {
        assumeTrue("Device does not support file-system checkpoint",
                mHostUtils.isCheckpointSupported());

        runPhase("testMultipleStagedInstall_ApkOnly_Commit");
        getDevice().reboot();
        runPhase("testMultipleStagedInstall_ApkOnly_VerifyPostReboot");
    }

    // If apk installation fails in one staged session, then all staged session should fail.
    @Test
    @LargeTest
    public void testInstallMultipleStagedSession_PartialFail_ApkOnly() throws Exception {
        assumeTrue("Device does not support file-system checkpoint",
                mHostUtils.isCheckpointSupported());

        runPhase("testInstallMultipleStagedSession_PartialFail_ApkOnly_Commit");
        getDevice().reboot();
        runPhase("testInstallMultipleStagedSession_PartialFail_ApkOnly_VerifyPostReboot");
    }

    // Failure reason of staged install should be be persisted for single sessions
    @Test
    @LargeTest
    public void testFailureReasonPersists_SingleSession() throws Exception {
        assumeTrue("Device does not support file-system checkpoint",
                mHostUtils.isCheckpointSupported());

        runPhase("testFailureReasonPersists_SingleSession_Commit");
        getDevice().reboot();
        runPhase("testFailureReasonPersists_SingleSession_VerifyPostReboot");
    }

    // Failure reason of staged install should be be persisted for multi session staged install
    @Test
    @LargeTest
    public void testFailureReasonPersists_MultiSession() throws Exception {
        assumeTrue("Device does not support file-system checkpoint",
                mHostUtils.isCheckpointSupported());

        runPhase("testFailureReasonPersists_MultipleSession_Commit");
        getDevice().reboot();
        runPhase("testFailureReasonPersists_MultipleSession_VerifyPostReboot");
    }

    @Test
    @LargeTest
    public void testSamegradeSystemApex() throws Exception {
        runPhase("testSamegradeSystemApex_Commit");
        getDevice().reboot();
        runPhase("testSamegradeSystemApex_VerifyPostReboot");
    }

    @Test
    @LargeTest
    public void testInstallApkChangingFingerprint() throws Exception {
        try {
            getDevice().executeShellCommand("setprop persist.pm.mock-upgrade true");
            runPhase("testInstallApkChangingFingerprint");
            getDevice().reboot();
            runPhase("testInstallApkChangingFingerprint_VerifyAborted");
        } finally {
            getDevice().executeShellCommand("setprop persist.pm.mock-upgrade false");
        }
    }

    @Test
    @LargeTest
    public void testInstallStagedNoHashtreeApex() throws Exception {
        runPhase("testInstallStagedNoHashtreeApex_Commit");
        getDevice().reboot();
        runPhase("testInstallStagedNoHashtreeApex_VerifyPostReboot");
    }

    /**
     * Should fail to verify apex targeting older dev sdk
     */
    @Test
    public void testApexTargetingOldDevSdkFailsVerification() throws Exception {
        runPhase("testApexTargetingOldDevSdkFailsVerification");
    }

    /**
     * Apex should fail to install if apk-in-apex fails to get scanned
     */
    @Test
    @LargeTest
    public void testApexFailsToInstallIfApkInApexFailsToScan() throws Exception {
        runPhase("testApexFailsToInstallIfApkInApexFailsToScan_Commit");
        getDevice().reboot();
        runPhase("testApexFailsToInstallIfApkInApexFailsToScan_VerifyPostReboot");
    }

    @Test
    public void testCorruptedApexFailsVerification_b146895998() throws Exception {
        runPhase("testCorruptedApexFailsVerification_b146895998");
    }

    /**
     * Should fail to pass apk signature check
     */
    @Test
    public void testApexWithUnsignedApkFailsVerification() throws Exception {
        runPhase("testApexWithUnsignedApkFailsVerification");
    }

    /**
     * Should fail to verify apex signed payload with a different key
     */
    @Test
    public void testApexSignPayloadWithDifferentKeyFailsVerification() throws Exception {
        runPhase("testApexSignPayloadWithDifferentKeyFailsVerification");
    }

    /**
     * Should fail to verify apex with unsigned payload
     */
    @Test
    public void testApexWithUnsignedPayloadFailsVerification() throws Exception {
        runPhase("testApexWithUnsignedPayloadFailsVerification");
    }

    @Test
    @LargeTest
    public void testApexSetsUpdatedSystemAppFlag() throws Exception {
        runPhase("testApexSetsUpdatedSystemAppFlag_preUpdate");
        installV2Apex();
        runPhase("testApexSetsUpdatedSystemAppFlag_postUpdate");
    }

    /**
     * Test non-priv apps cannot access /data/app-staging folder contents
     */
    @Test
    public void testAppStagingDirCannotBeReadByNonPrivApps() throws Exception {
        runPhase("testAppStagingDirCannotBeReadByNonPrivApps");
    }

    @Test
    @LargeTest
    public void testUpdatedApexFromDataApexActiveCanBePulled() throws Exception {
        installV2Apex();

        final ITestDevice.ApexInfo shimApex = mHostUtils.getShimApex().orElseThrow(
                () -> new AssertionError("Can't find " + SHIM_APEX_PACKAGE_NAME)
        );

        assertThat(shimApex.sourceDir).startsWith("/data/apex/active");
        assertThat(getDevice().pullFile(shimApex.sourceDir)).isNotNull();
    }

    @Test
    public void testApexInfoList() throws Exception {
        // Check that content of /apex/apex-info-list.xml matches output of
        // `adb shell pm list packages --apex-only --show-versioncode -f`.
        List<ApexInfo> apexInfoList =
                readApexInfoList().stream()
                    .filter(a -> a.getIsActive())
                    .collect(Collectors.toList());
        Set<ITestDevice.ApexInfo> activeApexes = getDevice().getActiveApexes();
        assertThat(apexInfoList.size()).isEqualTo(activeApexes.size());
        for (ITestDevice.ApexInfo apex : activeApexes) {
            // Note: we can't assert equality of the apex.name and apexInfo.getModuleName() since
            // they represent different concepts (the former is package name, while latter is apex
            // module name)
            List<ApexInfo> temp =
                    apexInfoList.stream()
                            .filter(a -> a.getModulePath().equals(apex.sourceDir))
                            .collect(Collectors.toList());
            assertThat(temp).hasSize(1);
            ApexInfo apexInfo = temp.get(0);
            assertThat(apexInfo.getModulePath()).isEqualTo(apex.sourceDir);
            assertThat(apexInfo.getVersionCode()).isEqualTo(apex.versionCode);
            assertThat(apexInfo.getIsActive()).isTrue();
            assertThat(apexInfo.getLastUpdateMillis()).isGreaterThan(0);
        }
    }

    @Test
    @LargeTest
    public void testApexInfoListAfterUpdate() throws Exception {
        ApexInfo shimBeforeUpdate = getShimApexInfo();

        installV2Apex();

        List<ApexInfo> shimApexInfo =
                readApexInfoList().stream()
                        .filter(a -> a.getModuleName().equals(SHIM_APEX_PACKAGE_NAME))
                        .collect(Collectors.toList());

        assertThat(shimApexInfo).hasSize(2);

        ApexInfo factoryShimApexInfo =
                shimApexInfo.stream()
                        .filter(ApexInfo::getIsFactory)
                        .findAny()
                        .orElseThrow(() ->
                                new AssertionError(
                                        "No factory version of " + SHIM_APEX_PACKAGE_NAME
                                                + " found in /apex/apex-info-list.xml"));
        assertThat(factoryShimApexInfo.getModuleName()).isEqualTo(SHIM_APEX_PACKAGE_NAME);
        assertThat(factoryShimApexInfo.getIsActive()).isFalse();
        assertThat(factoryShimApexInfo.getIsFactory()).isTrue();
        assertThat(factoryShimApexInfo.getVersionCode()).isEqualTo(1);
        assertThat(factoryShimApexInfo.getModulePath())
                .isEqualTo(factoryShimApexInfo.getPreinstalledModulePath());
        assertThat(factoryShimApexInfo.getLastUpdateMillis())
                .isEqualTo(shimBeforeUpdate.getLastUpdateMillis());

        ApexInfo activeShimApexInfo =
                shimApexInfo.stream()
                        .filter(ApexInfo::getIsActive)
                        .findAny()
                        .orElseThrow(() ->
                                new AssertionError(
                                        "No active version of " + SHIM_APEX_PACKAGE_NAME
                                                + " found in /apex/apex-info-list.xml"));
        assertThat(activeShimApexInfo.getModuleName()).isEqualTo(SHIM_APEX_PACKAGE_NAME);
        assertThat(activeShimApexInfo.getIsActive()).isTrue();
        assertThat(activeShimApexInfo.getIsFactory()).isFalse();
        assertThat(activeShimApexInfo.getVersionCode()).isEqualTo(2);
        assertThat(activeShimApexInfo.getPreinstalledModulePath())
                .isEqualTo(factoryShimApexInfo.getModulePath());
        assertThat(activeShimApexInfo.getLastUpdateMillis())
                .isNotEqualTo(shimBeforeUpdate.getLastUpdateMillis());
    }

    @Test
    @LargeTest
    public void testRebootlessUpdate() throws Exception {
        runPhase("testRebootlessUpdate");
        ApexInfo activeShimApexInfo = getActiveShimApexInfo();
        assertThat(activeShimApexInfo.getModuleName()).isEqualTo(SHIM_APEX_PACKAGE_NAME);
        assertThat(activeShimApexInfo.getIsActive()).isTrue();
        assertThat(activeShimApexInfo.getIsFactory()).isFalse();
        assertThat(activeShimApexInfo.getVersionCode()).isEqualTo(2);
    }

    @Test
    @LargeTest
    public void testRebootlessUpdate_fromV2ToV3_sameBoot() throws Exception {
        runPhase("testRebootlessUpdate");
        runPhase("testRebootlessUpdate_installV3");
        ApexInfo activeShimApexInfo = getActiveShimApexInfo();
        assertThat(activeShimApexInfo.getModuleName()).isEqualTo(SHIM_APEX_PACKAGE_NAME);
        assertThat(activeShimApexInfo.getIsActive()).isTrue();
        assertThat(activeShimApexInfo.getIsFactory()).isFalse();
        assertThat(activeShimApexInfo.getVersionCode()).isEqualTo(3);
    }

    @Test
    @LargeTest
    public void testRebootlessUpdate_fromV2ToV3_rebootInBetween() throws Exception {
        runPhase("testRebootlessUpdate");
        getDevice().reboot();
        runPhase("testRebootlessUpdate_installV3");
        ApexInfo activeShimApexInfo = getActiveShimApexInfo();
        assertThat(activeShimApexInfo.getModuleName()).isEqualTo(SHIM_APEX_PACKAGE_NAME);
        assertThat(activeShimApexInfo.getIsActive()).isTrue();
        assertThat(activeShimApexInfo.getIsFactory()).isFalse();
        assertThat(activeShimApexInfo.getVersionCode()).isEqualTo(3);
    }

    @Test
    @LargeTest
    public void testRebootlessUpdate_downgrage_fails() throws Exception {
        runPhase("testRebootlessUpdate_installV3");
        runPhase("testRebootlessUpdate_downgradeToV2_fails");
    }

    @Test
    public void testRebootlessUpdate_noPermission_fails() throws Exception {
        runPhase("testRebootlessUpdate_noPermission_fails");
    }

    @Test
    public void testRebootlessUpdate_noPreInstalledApex_fails() throws Exception {
        runPhase("testRebootlessUpdate_noPreInstalledApex_fails");
    }

    @Test
    public void testRebootlessUpdate_unsignedPayload_fails() throws Exception {
        runPhase("testRebootlessUpdate_unsignedPayload_fails");
    }

    @Test
    public void testRebootlessUpdate_payloadSignedWithDifferentKey_fails() throws Exception {
        runPhase("testRebootlessUpdate_payloadSignedWithDifferentKey_fails");
    }

    @Test
    public void testRebootlessUpdate_outerContainerSignedWithDifferentCert_fails()
            throws Exception {
        runPhase("testRebootlessUpdate_outerContainerSignedWithDifferentCert_fails");
    }

    @Test
    public void testRebootlessUpdate_outerContainerUnsigned_fails() throws Exception {
        runPhase("testRebootlessUpdate_outerContainerUnsigned_fails");
    }

    @Test
    public void testRebootlessUpdate_targetsOlderSdk_fails() throws Exception {
        runPhase("testRebootlessUpdate_targetsOlderSdk_fails");
    }

    @Test
    @LargeTest
    public void testGetInactiveApexFactoryPackagesAfterApexInstall_containsNoDuplicates()
            throws Exception {
        installV2Apex();
        runPhase("testGetInactiveApexFactoryPackagesAfterApexInstall_containsNoDuplicates");
    }

    private List<ApexInfo> readApexInfoList() throws Exception {
        File file = getDevice().pullFile("/apex/apex-info-list.xml");
        try (FileInputStream stream = new FileInputStream(file)) {
            return XmlParser.readApexInfoList(stream).getApexInfo();
        }
    }

    private ApexInfo getShimApexInfo() throws Exception {
        List<ApexInfo> temp =
                readApexInfoList().stream()
                            .filter(a -> a.getModuleName().equals(SHIM_APEX_PACKAGE_NAME))
                            .collect(Collectors.toList());
        assertThat(temp).hasSize(1);
        return temp.get(0);
    }

    private ApexInfo getActiveShimApexInfo() throws Exception {
        return readApexInfoList().stream()
                    .filter(a -> a.getModuleName().equals(SHIM_APEX_PACKAGE_NAME))
                    .filter(ApexInfo::getIsActive)
                    .findAny()
                    .orElseThrow(() ->
                            new AssertionError(
                                    "No active version of " + SHIM_APEX_PACKAGE_NAME
                                            + " found in /apex/apex-info-list.xml"));
    }

    /**
     * Store the component name of the default launcher. This value will be used to reset the
     * default launcher to its correct component upon test completion.
     */
    private void storeDefaultLauncher() throws DeviceNotAvailableException {
        final String PREFIX = "Launcher: ComponentInfo{";
        final String POSTFIX = "}";
        for (String s : getDevice().executeShellCommand("cmd shortcut get-default-launcher")
                .split("\n")) {
            if (s.startsWith(PREFIX) && s.endsWith(POSTFIX)) {
                mDefaultLauncher = s.substring(PREFIX.length(), s.length() - POSTFIX.length());
            }
        }
    }

    /**
     * Set the default launcher to a given component.
     * If set to the broadcast receiver component of this test app, this will allow the test app to
     * receive SESSION_COMMITTED broadcasts.
     */
    private void setDefaultLauncher(String launcherComponent) throws DeviceNotAvailableException {
        assertThat(launcherComponent).isNotEmpty();
        int user = getDevice().getCurrentUser();
        getDevice().executeShellCommand(
                "cmd package set-home-activity --user " + user + " " + launcherComponent);
    }

    private static final class FailedTestLogHook extends TestWatcher {

        private final BaseHostJUnit4Test mInstance;
        private String mStagedSessionsBeforeTest;

        private FailedTestLogHook(BaseHostJUnit4Test instance) {
            this.mInstance = instance;
        }

        @Override
        protected void failed(Throwable e, Description description) {
            String stagedSessionsAfterTest = getStagedSessions();
            Log.e(TAG, "Test " + description + " failed.\n"
                    + "Staged sessions before test started:\n" + mStagedSessionsBeforeTest + "\n"
                    + "Staged sessions after test failed:\n" + stagedSessionsAfterTest);
        }

        @Override
        protected void starting(Description description) {
            mStagedSessionsBeforeTest = getStagedSessions();
        }

        private String getStagedSessions() {
            try {
                return mInstance.getDevice().executeShellV2Command("pm get-stagedsessions").getStdout();
            } catch (DeviceNotAvailableException e) {
                Log.e(TAG, e);
                return "Failed to get staged sessions";
            }
        }
    }

    private boolean isDebuggable() throws Exception {
        return getDevice().getIntProperty("ro.debuggable", 0) == 1;
    }
}
