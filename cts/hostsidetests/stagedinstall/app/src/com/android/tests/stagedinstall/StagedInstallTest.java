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

package com.android.tests.stagedinstall;

import static com.android.cts.install.lib.InstallUtils.assertStatusFailure;
import static com.android.cts.install.lib.InstallUtils.assertStatusSuccess;
import static com.android.cts.install.lib.InstallUtils.getPackageInstaller;
import static com.android.cts.install.lib.PackageInstallerSessionInfoSubject.assertThat;
import static com.android.cts.shim.lib.ShimPackage.DIFFERENT_APEX_PACKAGE_NAME;
import static com.android.cts.shim.lib.ShimPackage.NOT_PRE_INSTALL_APEX_PACKAGE_NAME;
import static com.android.cts.shim.lib.ShimPackage.SHIM_APEX_PACKAGE_NAME;
import static com.android.cts.shim.lib.ShimPackage.SHIM_PACKAGE_NAME;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.InstallUtils;
import com.android.cts.install.lib.LocalIntentSender;
import com.android.cts.install.lib.TestApp;
import com.android.cts.install.lib.Uninstall;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * This series of tests are meant to be driven by a host, since some of the interactions being
 * tested require the device to be rebooted, and some assertions to be verified post-reboot.
 * The convention used here (not enforced) is that the test methods in this file will be named
 * the same way as the test methods in the "host" class (see e.g.
 * {@code com.android.test.stagedinstall.host.StagedInstallTest}), with an optional suffix preceded
 * by an underscore, in case of multiple phases.
 * Example:
 * - In {@code com.android.test.stagedinstall.host.StagedInstallTest}:
 *
 * @Test
 * public void testInstallStagedApk() throws Exception {
 *  runPhase("testInstallStagedApk_Commit");
 *  getDevice().reboot();
 *  runPhase("testInstallStagedApk_VerifyPostReboot");
 * }
 * - In this class:
 * @Test public void testInstallStagedApk_Commit() throws Exception;
 * @Test public void testInstallStagedApk_VerifyPostReboot() throws Exception;
 */
@RunWith(JUnit4.class)
public class StagedInstallTest {

    private static final String TAG = "StagedInstallTest";

    private File mTestStateFile = new File(
            InstrumentationRegistry.getInstrumentation().getContext().getFilesDir(),
            "ctsstagedinstall_state");

    private static final Duration WAIT_FOR_SESSION_REMOVED_TTL = Duration.ofSeconds(10);
    private static final Duration SLEEP_DURATION = Duration.ofMillis(200);

    private static final TestApp TESTAPP_SAME_NAME_AS_APEX = new TestApp(
            "TestAppSamePackageNameAsApex", SHIM_APEX_PACKAGE_NAME, 1, /*isApex*/ false,
            "StagedInstallTestAppSamePackageNameAsApex.apk");
    private static final TestApp Apex2DifferentCertificate = new TestApp(
            "Apex2DifferentCertificate", SHIM_APEX_PACKAGE_NAME, 2, /*isApex*/true,
            "com.android.apex.cts.shim.v2_different_certificate.apex");
    private static final TestApp Apex2DifferentPackageName = new TestApp(
            "Apex2DifferentPackageName", DIFFERENT_APEX_PACKAGE_NAME, 2, /*isApex*/true,
            "com.android.apex.cts.shim.v2_different_package_name.apex");
    private static final TestApp Apex2SignedBob = new TestApp(
            "Apex2SignedBob", SHIM_APEX_PACKAGE_NAME, 2, /*isApex*/true,
            "com.android.apex.cts.shim.v2_signed_bob.apex");
    private static final TestApp Apex2SignedBobRot = new TestApp(
            "Apex2SignedBobRot", SHIM_APEX_PACKAGE_NAME, 2, /*isApex*/true,
            "com.android.apex.cts.shim.v2_signed_bob_rot.apex");
    private static final TestApp Apex2SignedBobRotRollback = new TestApp(
            "Apex2SignedBobRotRollback", SHIM_APEX_PACKAGE_NAME, 2, /*isApex*/true,
            "com.android.apex.cts.shim.v2_signed_bob_rot_rollback.apex");
    private static final TestApp ApexNoHashtree2 = new TestApp(
            "Apex2", SHIM_APEX_PACKAGE_NAME, 2, /*isApex*/true,
            "com.android.apex.cts.shim.v2_no_hashtree.apex");
    private static final TestApp ApexWrongSha2 = new TestApp(
            "ApexWrongSha2", SHIM_APEX_PACKAGE_NAME, 2, /*isApex*/true,
            "com.android.apex.cts.shim.v2_wrong_sha.apex");
    private static final TestApp Apex2WithoutApkInApex = new TestApp(
            "Apex2WithoutApkInApex", SHIM_APEX_PACKAGE_NAME, 2, /*isApex*/true,
            "com.android.apex.cts.shim.v2_without_apk_in_apex.apex");
    private static final TestApp Apex3SignedBob = new TestApp(
            "Apex3SignedBob", SHIM_APEX_PACKAGE_NAME, 3, /*isApex*/true,
            "com.android.apex.cts.shim.v3_signed_bob.apex");
    private static final TestApp Apex3SignedBobRot = new TestApp(
            "Apex3SignedBobRot", SHIM_APEX_PACKAGE_NAME, 3, /*isApex*/true,
            "com.android.apex.cts.shim.v3_signed_bob_rot.apex");
    private static final TestApp ApexNotPreInstalled = new TestApp(
            "ApexNotPreInstalled", NOT_PRE_INSTALL_APEX_PACKAGE_NAME, 3, /*isApex*/true,
            "com.android.apex.cts.shim_not_pre_installed.apex");
    private static final TestApp Apex2SdkTargetP = new TestApp(
            "StagedInstallTestApexV2_SdkTargetP", SHIM_APEX_PACKAGE_NAME, 2,
            /*isApex*/true, "com.android.apex.cts.shim.v2_sdk_target_p.apex");
    private static final TestApp Apex2ApkInApexSdkTargetP = new TestApp(
            "StagedInstallTestApexV2_ApkInApexSdkTargetP", SHIM_APEX_PACKAGE_NAME, 2,
            /*isApex*/true, "com.android.apex.cts.shim.v2_apk_in_apex_sdk_target_p.apex");
    private static final TestApp CorruptedApex_b146895998 = new TestApp(
            "StagedInstallTestCorruptedApex_b146895998", "", 1, true, "corrupted_b146895998.apex");
    private static final TestApp Apex2NoApkSignature = new TestApp(
            "StagedInstallTestApexV2_NoApkSignature", SHIM_APEX_PACKAGE_NAME, 1,
            /*isApex*/true, "com.android.apex.cts.shim.v2_unsigned_apk_container.apex");
    private static final TestApp Apex2UnsignedPayload = new TestApp(
            "StagedInstallTestApexV2_UnsignedPayload", SHIM_APEX_PACKAGE_NAME, 1,
            /*isApex*/true, "com.android.apex.cts.shim.v2_unsigned_payload.apex");
    private static final TestApp Apex2SignPayloadWithDifferentKey = new TestApp(
            "StagedInstallTestApexV2_SignPayloadWithDifferentKey", SHIM_APEX_PACKAGE_NAME, 1,
            /*isApex*/true, "com.android.apex.cts.shim.v2_sign_payload_with_different_key.apex");
    private static final TestApp Apex2Rebootless = new TestApp(
            "StagedInstallTestApexV2_Rebootless", SHIM_APEX_PACKAGE_NAME, 2,
            /*isApex*/true, "com.android.apex.cts.shim.v2_rebootless.apex");
    private static final TestApp Apex3Rebootless = new TestApp(
            "StagedInstallTestApexV3_Rebootless", SHIM_APEX_PACKAGE_NAME, 3,
            /*isApex*/true, "com.android.apex.cts.shim.v3_rebootless.apex");
    private static final TestApp Apex2AsApk = new TestApp("Apex2", SHIM_APEX_PACKAGE_NAME, 2,
            /*isApex*/false, "com.android.apex.cts.shim.v2.apex");

    @Before
    public void adoptShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.PACKAGE_USAGE_STATS,
                        Manifest.permission.INSTALL_PACKAGES,
                        Manifest.permission.DELETE_PACKAGES);
    }

    @After
    public void dropShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    // This is marked as @Test to take advantage of @Before/@After methods of this class. Actual
    // purpose of this method to be called before and after each test case of
    // com.android.test.stagedinstall.host.StagedInstallTest to reduce tests flakiness.
    @Test
    public void cleanUp() throws Exception {
        PackageInstaller packageInstaller = getPackageInstaller();
        List<PackageInstaller.SessionInfo> stagedSessions = packageInstaller.getStagedSessions();
        for (PackageInstaller.SessionInfo sessionInfo : stagedSessions) {
            if (sessionInfo.getParentSessionId() != PackageInstaller.SessionInfo.INVALID_ID
                    || sessionInfo.isStagedSessionApplied()
                    || sessionInfo.isStagedSessionFailed()) {
                // Cannot abandon a child session; no need to abandon terminated sessions
                continue;
            }
            try {
                Log.i(TAG, "abandoning session " + sessionInfo.getSessionId());
                packageInstaller.abandonSession(sessionInfo.getSessionId());
            } catch (Exception e) {
                Log.e(TAG, "Failed to abandon session " + sessionInfo.getSessionId(), e);
            }
        }
        Uninstall.packages(TestApp.A, TestApp.B);
        Files.deleteIfExists(mTestStateFile.toPath());
    }

    @Test
    public void testFailInstallIfNoPermission() throws Exception {
        dropShellPermissions();
        try {
            createStagedSession();
            fail(); // Should have thrown SecurityException.
        } catch (SecurityException e) {
            // This would be a better version, but it requires a version of truth not present in the
            // tree yet.
            // assertThat(e).hasMessageThat().containsMatch(...);
            assertThat(e.getMessage()).containsMatch(
                    "Neither user [0-9]+ nor current process has "
                    + "android.permission.INSTALL_PACKAGES");
        }
    }

    @Test
    public void testInstallStagedApk_Commit() throws Exception {
        BroadcastCounter counter = new BroadcastCounter(PackageInstaller.ACTION_SESSION_COMMITTED);
        int sessionId = stageSingleApk(TestApp.A1).assertSuccessful().getSessionId();
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        counter.assertNoBroadcastReceived();
    }

    @Test
    public void testInstallStagedApk_VerifyPostReboot() throws Exception {
        BroadcastCounter counter = new BroadcastCounter(PackageInstaller.ACTION_SESSION_COMMITTED);
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(1);
        counter.assertNoBroadcastReceived();
    }

    @Test
    public void testInstallStagedApk_AbandonSessionIsNoop() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        // Session is in a final state. Test that abandoning the session doesn't remove it from the
        // session database.
        abandonSession(sessionId);
        assertSessionApplied(sessionId);
    }

    @Test
    public void testInstallMultipleStagedApks_Commit() throws Exception {
        BroadcastCounter counter = new BroadcastCounter(PackageInstaller.ACTION_SESSION_COMMITTED);
        int sessionId = stageMultipleApks(TestApp.A1, TestApp.B1)
                .assertSuccessful().getSessionId();
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        assertThat(getInstalledVersion(TestApp.B)).isEqualTo(-1);
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        assertThat(getInstalledVersion(TestApp.B)).isEqualTo(-1);
        counter.assertNoBroadcastReceived();
    }

    @Test
    public void testInstallMultipleStagedApks_VerifyPostReboot() throws Exception {
        BroadcastCounter counter = new BroadcastCounter(PackageInstaller.ACTION_SESSION_COMMITTED);
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(1);
        assertThat(getInstalledVersion(TestApp.B)).isEqualTo(1);
        counter.assertNoBroadcastReceived();
    }

    @Test
    public void testAbandonStagedApkBeforeReboot_CommitAndAbandon() throws Exception {
        int sessionId = stageSingleApk(TestApp.A1).assertSuccessful().getSessionId();
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        PackageInstaller.SessionInfo session = InstallUtils.getStagedSessionInfo(sessionId);
        assertSessionReady(sessionId);
        abandonSession(sessionId);
        InstallUtils.assertStagedSessionIsAbandoned(sessionId);
        // Allow the session to be removed from PackageInstaller
        Duration spentWaiting = Duration.ZERO;
        while (spentWaiting.compareTo(WAIT_FOR_SESSION_REMOVED_TTL) < 0) {
            session = getSessionInfo(sessionId);
            if (session == null) {
                Log.i(TAG, "Done waiting after " + spentWaiting);
                break;
            }
            try {
                Thread.sleep(SLEEP_DURATION.toMillis());
                spentWaiting = spentWaiting.plus(SLEEP_DURATION);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        assertThat(session).isNull();
    }

    @Test
    public void testAbandonStagedApkBeforeReboot_VerifyPostReboot() throws Exception {
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
    }

    @Test
    public void testAbandonStagedApkBeforeReady_CommitAndAbandon() throws Exception {
        int sessionId = stageSingleApk(TestApp.A1).assertSuccessful().getSessionId();
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        abandonSession(sessionId);
        InstallUtils.assertStagedSessionIsAbandoned(sessionId);
    }

    @Test
    public void testAbandonStagedApkBeforeReady_VerifyPostReboot() throws Exception {
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
    }

    @Test
    public void testStageAnotherSessionImmediatelyAfterAbandon() throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        int sessionId = stageSingleApk(TestApp.Apex2).assertSuccessful().getSessionId();
        abandonSession(sessionId);
        stageSingleApk(TestApp.Apex2).assertSuccessful();
    }

    @Test
    public void testStageAnotherSessionImmediatelyAfterAbandonMultiPackage() throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        int sessionId = stageMultipleApks(TestApp.Apex2, TestApp.A1, TestApp.B1)
                .assertSuccessful().getSessionId();
        abandonSession(sessionId);
        stageSingleApk(TestApp.Apex2).assertSuccessful();
    }

    @Test
    public void testNoSessionUpdatedBroadcastSentForStagedSessionAbandon() throws Exception {
        BroadcastCounter counter = new BroadcastCounter(PackageInstaller.ACTION_SESSION_UPDATED);
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        // Using an apex in hopes that pre-reboot verification will take longer to complete
        // and we will manage to abandon it before session becomes ready.
        int sessionId = Install.multi(TestApp.A1, TestApp.Apex2).setStaged().createSession();
        LocalIntentSender sender = new LocalIntentSender();
        InstallUtils.openPackageInstallerSession(sessionId).commit(sender.getIntentSender());
        abandonSession(sessionId);
        InstallUtils.assertStagedSessionIsAbandoned(sessionId);
        counter.assertNoBroadcastReceived();
    }

    @Test
    public void testGetActiveStagedSessions() throws Exception {
        PackageInstaller packageInstaller = getPackageInstaller();
        int firstSessionId = stageSingleApk(TestApp.A1).assertSuccessful().getSessionId();
        int secondSessionId = stageSingleApk(TestApp.B1).assertSuccessful().getSessionId();
        List<Integer> stagedSessionIds = packageInstaller.getActiveStagedSessions()
                .stream().map(s -> s.getSessionId()).collect(Collectors.toList());
        assertThat(stagedSessionIds).containsExactly(firstSessionId, secondSessionId);

        // Verify no other session is considered as active staged session
        List<PackageInstaller.SessionInfo> allSessions = packageInstaller.getAllSessions();
        for (PackageInstaller.SessionInfo session : allSessions) {
            if (session.isStagedSessionActive()) {
                assertThat(stagedSessionIds).contains(session.getSessionId());
            }
        }
    }

    /**
     * Verifies that active staged session fulfils conditions stated at
     * {@link PackageInstaller.SessionInfo#isStagedSessionActive}
     */
    @Test
    public void testIsStagedSessionActive() throws Exception {
        PackageInstaller packageInstaller = getPackageInstaller();
        int sessionId = stageSingleApk(TestApp.A1).assertSuccessful().getSessionId();

        List<PackageInstaller.SessionInfo> allSessions = packageInstaller.getAllSessions();
        boolean activeStagedSessionFound = false;
        for (PackageInstaller.SessionInfo session : allSessions) {
            // If it fulfils conditions, then it should be an active staged session
            if (session.isStaged() && session.isCommitted() && !session.isStagedSessionApplied()
                    && !session.isStagedSessionFailed()) {
                activeStagedSessionFound = true;
                assertThat(session.getSessionId()).isEqualTo(sessionId);
                assertThat(session.isStagedSessionActive()).isTrue();
            } else {
                // Otherwise, it should not be marked as active staged session
                assertThat(session.getSessionId()).isNotEqualTo(sessionId);
                assertThat(session.isStagedSessionActive()).isFalse();
            }
        }
        assertWithMessage("Did not find any active staged session")
                .that(activeStagedSessionFound).isTrue();
    }

    @Test
    public void testGetActiveStagedSessionsNoSessionActive() throws Exception {
        PackageInstaller packageInstaller = getPackageInstaller();
        List<PackageInstaller.SessionInfo> sessions = packageInstaller.getActiveStagedSessions();
        assertThat(sessions).isEmpty();
    }

    @Test
    public void testGetActiveStagedSessions_MultiApkSession() throws Exception {
        int firstSessionId = stageMultipleApks(TestApp.A1, TestApp.B1)
                .assertSuccessful().getSessionId();
        int secondSessionId = stageMultipleApks(TestApp.C1)
                .assertSuccessful().getSessionId();
        List<Integer> stagedSessionIds = getPackageInstaller().getActiveStagedSessions()
                .stream().map(s -> s.getSessionId()).collect(Collectors.toList());
        assertThat(stagedSessionIds).containsExactly(firstSessionId, secondSessionId);
    }

    @Test
    public void testStagedInstallDowngrade_DowngradeNotRequested_Fails_Commit()  throws Exception {
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        Install.single(TestApp.A2).commit();
        int sessionId = stageSingleApk(TestApp.A1).assertFailure().getSessionId();
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(2);
        PackageInstaller.SessionInfo sessionInfo = getSessionInfo(sessionId);
        assertThat(sessionInfo).isStagedSessionFailed();
    }

    @Test
    public void testStagedInstallDowngrade_DowngradeRequested_Commit() throws Exception {
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        Install.single(TestApp.A2).commit();
        int sessionId = stageDowngradeSingleApk(TestApp.A1).assertSuccessful().getSessionId();
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(2);
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testStagedInstallDowngrade_DowngradeRequested_Fails_Commit() throws Exception {
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        Install.single(TestApp.A2).commit();
        stageDowngradeSingleApk(TestApp.A1).assertFailure();
    }

    @Test
    public void testStagedInstallDowngrade_DowngradeRequested_DebugBuild_VerifyPostReboot()
            throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        // App should be downgraded.
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(1);
    }

    @Test
    public void testInstallStagedApex_Commit() throws Exception {
        BroadcastCounter counter = new BroadcastCounter(PackageInstaller.ACTION_SESSION_COMMITTED);
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        int sessionId = stageSingleApk(TestApp.Apex2).assertSuccessful().getSessionId();
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
        // Version shouldn't change before reboot.
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        counter.assertNoBroadcastReceived();
    }

    @Test
    public void testInstallStagedApex_VerifyPostReboot() throws Exception {
        BroadcastCounter counter = new BroadcastCounter(PackageInstaller.ACTION_SESSION_COMMITTED);
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
        counter.assertNoBroadcastReceived();
    }

    @Test
    public void testInstallStagedApexAndApk_Commit() throws Exception {
        BroadcastCounter counter = new BroadcastCounter(PackageInstaller.ACTION_SESSION_COMMITTED);
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        int sessionId = stageMultipleApks(TestApp.Apex2, TestApp.A1)
                .assertSuccessful().getSessionId();
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
        // Version shouldn't change before reboot.
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        counter.assertNoBroadcastReceived();
    }

    @Test
    public void testInstallStagedApexAndApk_VerifyPostReboot() throws Exception {
        BroadcastCounter counter = new BroadcastCounter(PackageInstaller.ACTION_SESSION_COMMITTED);
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(1);
        counter.assertNoBroadcastReceived();
    }

    @Test
    public void testsFailsNonStagedApexInstall() throws Exception {
        try {
            SystemUtil.runShellCommandForNoOutput("pm bypass-staged-installer-check true");
            assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
            TestApp apex = new TestApp(
                    "Apex2", SHIM_APEX_PACKAGE_NAME, 2, /*isApex*/true,
                    "com.android.apex.cts.shim.v2.apex");
            InstallUtils.commitExpectingFailure(AssertionError.class,
                    "does not support non-staged update",
                    Install.single(apex));
            assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        } finally {
            SystemUtil.runShellCommandForNoOutput("pm bypass-staged-installer-check false");
        }
    }

    @Test
    public void testInstallStagedNonPreInstalledApex_Fails() throws Exception {
        assertThat(getInstalledVersion(NOT_PRE_INSTALL_APEX_PACKAGE_NAME)).isEqualTo(-1);
        int sessionId = stageSingleApk(ApexNotPreInstalled).assertFailure().getSessionId();
        PackageInstaller.SessionInfo sessionInfo = getSessionInfo(sessionId);
        assertThat(sessionInfo).isStagedSessionFailed();
    }

    @Test
    public void testInstallStagedDifferentPackageNameWithInstalledApex_Fails() throws Exception {
        assertThat(getInstalledVersion(DIFFERENT_APEX_PACKAGE_NAME)).isEqualTo(-1);
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        int sessionId = stageSingleApk(Apex2DifferentPackageName).assertFailure().getSessionId();
        PackageInstaller.SessionInfo sessionInfo = getSessionInfo(sessionId);
        assertThat(sessionInfo.getStagedSessionErrorMessage()).contains(
                "Attempting to install new APEX package");
    }

    @Test
    public void testStageApkWithSameNameAsApexShouldFail_Commit() throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        int sessionId = stageSingleApk(Apex2AsApk).assertSuccessful().getSessionId();
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testStageApkWithSameNameAsApexShouldFail_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionFailed(sessionId);
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        PackageInstaller.SessionInfo sessionInfo = getSessionInfo(sessionId);
        assertThat(sessionInfo.getStagedSessionErrorMessage()).contains(
                "is an APEX package and can't be installed as an APK");
    }

    @Test
    public void testNonStagedInstallApkWithSameNameAsApexShouldFail() throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        InstallUtils.commitExpectingFailure(AssertionError.class,
                "is an APEX package and can't be installed as an APK",
                Install.single(Apex2AsApk));
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
    }

    @Test
    public void testInstallV2Apex_Commit() throws Exception {
        int sessionId = stageSingleApk(TestApp.Apex2).assertSuccessful().getSessionId();
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testInstallV2Apex_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
        assertThat(getInstalledVersion(SHIM_PACKAGE_NAME)).isNotEqualTo(-1);
    }

    @Test
    public void testInstallV2SignedBobApex_Commit() throws Exception {
        int sessionId = stageSingleApk(Apex2SignedBobRot).assertSuccessful().getSessionId();
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testInstallV2SignedBobApex_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
    }

    @Test
    public void testInstallV3Apex_Commit() throws Exception {
        int sessionId = stageSingleApk(TestApp.Apex3).assertSuccessful().getSessionId();
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testInstallV3Apex_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(3);
    }

    @Test
    public void testInstallV3SignedBobApex_Commit() throws Exception {
        int sessionId = stageSingleApk(Apex2SignedBobRot).assertSuccessful().getSessionId();
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testInstallV3SignedBobApex_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
    }

    @Test
    public void testStagedInstallDowngradeApex_DowngradeNotRequested_Fails_Commit()
            throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(3);
        int sessionId = stageSingleApk(TestApp.Apex2).assertFailure().getSessionId();
        PackageInstaller.SessionInfo sessionInfo = getSessionInfo(sessionId);
        assertThat(sessionInfo).isStagedSessionFailed();
        // Also verify that correct session info is reported by PackageManager.
        assertSessionFailed(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testStagedInstallDowngradeApex_DowngradeNotRequested_Fails_VerifyPostReboot()
            throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionFailed(sessionId);
        // INSTALL_REQUEST_DOWNGRADE wasn't set, so apex shouldn't be downgraded.
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(3);
    }

    @Test
    public void testStagedInstallDowngradeApex_DowngradeRequested_DebugBuild_Commit()
            throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(3);
        int sessionId = stageDowngradeSingleApk(TestApp.Apex2).assertSuccessful().getSessionId();
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testStagedInstallDowngradeApex_DowngradeRequested_DebugBuild_VerifyPostReboot()
            throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        // Apex should be downgraded.
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
    }

    @Test
    public void testStagedInstallDowngradeApex_DowngradeRequested_UserBuild_Fails_Commit()
            throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(3);
        int sessionId = stageDowngradeSingleApk(TestApp.Apex2).getSessionId();
        PackageInstaller.SessionInfo sessionInfo = getSessionInfo(sessionId);
        assertThat(sessionInfo).isStagedSessionFailed();
        // Also verify that correct session info is reported by PackageManager.
        assertSessionFailed(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testStagedInstallDowngradeApex_DowngradeRequested_UserBuild_Fails_VerifyPostReboot()
            throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionFailed(sessionId);
        // Apex shouldn't be downgraded.
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(3);
    }

    @Test
    public void testStagedInstallDowngradeApexToSystemVersion_DebugBuild_Commit()
            throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
        int sessionId = stageDowngradeSingleApk(TestApp.Apex1).assertSuccessful().getSessionId();
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testStagedInstallDowngradeApexToSystemVersion_DebugBuild_VerifyPostReboot()
            throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        // Apex should be downgraded.
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
    }

    @Test
    public void testFailsInvalidApexInstall_Commit() throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        int sessionId = stageSingleApk(ApexWrongSha2).assertFailure()
                .getSessionId();
        assertSessionFailed(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testFailsInvalidApexInstall_AbandonSessionIsNoop() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionFailed(sessionId);
        // Session is in a final state. Test that abandoning the session doesn't remove it from the
        // session database.
        abandonSession(sessionId);
        assertSessionFailed(sessionId);
    }

    @Test
    public void testStagedApkSessionCallbacks() throws Exception {

        List<Integer> created = new ArrayList<Integer>();

        HandlerThread handlerThread = new HandlerThread(
                "StagedApkSessionCallbacksTestHandlerThread");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        PackageInstaller.SessionCallback callback = new PackageInstaller.SessionCallback() {

            @Override
            public void onCreated(int sessionId) {
                synchronized (created) {
                    created.add(sessionId);
                }
            }

            @Override public void onBadgingChanged(int sessionId) { }
            @Override public void onActiveChanged(int sessionId, boolean active) { }
            @Override public void onProgressChanged(int sessionId, float progress) { }
            @Override public void onFinished(int sessionId, boolean success) { }
        };

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        PackageInstaller packageInstaller = getPackageInstaller();
        packageInstaller.registerSessionCallback(callback, handler);

        int sessionId = stageSingleApk(TestApp.A1).assertSuccessful().getSessionId();

        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        assertSessionReady(sessionId);

        packageInstaller.unregisterSessionCallback(callback);

        handlerThread.quitSafely();
        handlerThread.join();

        synchronized (created) {
            assertThat(created).containsExactly(sessionId);
        }
        packageInstaller.abandonSession(sessionId);
    }

    @Test
    public void testInstallStagedApexWithoutApexSuffix_Commit() throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);

        int sessionId = stageSingleApk("com.android.apex.cts.shim.v2.apex", "package")
                .assertSuccessful().getSessionId();
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
        // Version shouldn't change before reboot.
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
    }

    @Test
    public void testInstallStagedApexWithoutApexSuffix_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
    }

    @Test
    public void testRejectsApexDifferentCertificate() throws Exception {
        int sessionId = stageSingleApk(Apex2DifferentCertificate)
                .assertFailure().getSessionId();
        PackageInstaller.SessionInfo info = getSessionInfo(sessionId);
        assertThat(info.getSessionId()).isEqualTo(sessionId);
        assertThat(info).isStagedSessionFailed();
        assertThat(info.getStagedSessionErrorMessage()).contains("is not compatible with the one "
                + "currently installed on device");
    }

    /**
     * Tests for staged install involving rotated keys.
     *
     * Here alice means the original default key that cts.shim.v1 package was signed with and
     * bob is the new key alice rotates to. Where ambiguous, we will refer keys as alice and bob
     * instead of "old key" and "new key".
     */

    // The update should fail if it is signed with a different non-rotated key
    @Test
    public void testUpdateWithDifferentKeyButNoRotation() throws Exception {
        stageSingleApk(Apex2SignedBob).assertFailure().getSessionId();
    }

    // The update should pass if it is signed with a proper rotated key
    @Test
    public void testUpdateWithDifferentKey_Commit() throws Exception {
        stageSingleApk(Apex2SignedBobRot).assertSuccessful().getSessionId();
    }

    @Test
    public void testUpdateWithDifferentKey_VerifyPostReboot() throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
    }

    // Once updated with a new rotated key (bob), further updates with old key (alice) should fail
    @Test
    public void testUntrustedOldKeyIsRejected() throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
        stageSingleApk(TestApp.Apex3).assertFailure().getSessionId();
    }

    // Should be able to update with an old key which is trusted
    @Test
    public void testTrustedOldKeyIsAccepted_Commit() throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        stageSingleApk(Apex2SignedBobRotRollback).assertSuccessful().getSessionId();
    }

    @Test
    public void testTrustedOldKeyIsAccepted_CommitPostReboot() throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
        stageSingleApk(TestApp.Apex3).assertSuccessful().getSessionId();
    }

    @Test
    public void testTrustedOldKeyIsAccepted_VerifyPostReboot() throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(3);
    }

    // Once updated with a new rotated key (bob), further updates with new key (bob) should pass
    @Test
    public void testAfterRotationNewKeyCanUpdateFurther_CommitPostReboot() throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
        stageSingleApk(Apex3SignedBobRot).assertSuccessful().getSessionId();
    }

    @Test
    public void testAfterRotationNewKeyCanUpdateFurther_VerifyPostReboot() throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(3);
    }

    // Once updated with a new rotated key (bob), further updates can be done with key only
    @Test
    public void testAfterRotationNewKeyCanUpdateFurtherWithoutLineage()
            throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
        stageSingleApk(Apex3SignedBob).assertSuccessful().getSessionId();
    }

    /**
     * Tests for staging and installing multiple staged sessions.
     */

    // Should fail to stage multiple sessions when check-point is not available
    @Test
    public void testFailStagingMultipleSessionsIfNoCheckPoint() throws Exception {
        stageSingleApk(TestApp.A1).assertSuccessful();
        int sessionId = stageSingleApk(TestApp.B1).assertFailure().getSessionId();
        PackageInstaller.SessionInfo info = getSessionInfo(sessionId);
        assertThat(info).isStagedSessionFailed();
        assertThat(info.getStagedSessionErrorMessage()).contains(
                "Cannot stage multiple sessions without checkpoint support");
    }

    @Test
    public void testFailOverlappingMultipleStagedInstall_BothSinglePackage_Apk() throws Exception {
        stageSingleApk(TestApp.A1).assertSuccessful().getSessionId();

        int sessionId = stageSingleApk(TestApp.A1).assertFailure().getSessionId();
        PackageInstaller.SessionInfo info = getSessionInfo(sessionId);
        assertThat(info).isStagedSessionFailed();
        assertThat(info.getStagedSessionErrorMessage()).contains(
                "has been staged already by session");
    }

    @Test
    public void testAllowNonOverlappingMultipleStagedInstall_MultiPackageSinglePackage_Apk()
            throws Exception {
        stageMultipleApks(TestApp.A1, TestApp.B1).assertSuccessful();
        stageSingleApk(TestApp.C1).assertSuccessful();
    }

    @Test
    public void testFailOverlappingMultipleStagedInstall_BothMultiPackage_Apk() throws Exception {
        stageMultipleApks(TestApp.A1, TestApp.B1).assertSuccessful().getSessionId();
        int sessionId = stageMultipleApks(TestApp.A2, TestApp.C1).assertFailure().getSessionId();
        PackageInstaller.SessionInfo info = getSessionInfo(sessionId);
        assertThat(info).isStagedSessionFailed();
        assertThat(info.getStagedSessionErrorMessage()).contains(
                "has been staged already by session");
    }

    // Should succeed in installing multiple staged sessions together
    @Test
    public void testMultipleStagedInstall_ApkOnly_Commit()
            throws Exception {
        int firstSessionId = stageSingleApk(TestApp.A1).assertSuccessful().getSessionId();
        int secondSessionId = stageSingleApk(TestApp.B1).assertSuccessful().getSessionId();
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        assertThat(getInstalledVersion(TestApp.B)).isEqualTo(-1);
        storeSessionIds(Arrays.asList(firstSessionId, secondSessionId));
    }

    @Test
    public void testMultipleStagedInstall_ApkOnly_VerifyPostReboot()
            throws Exception {
        List<Integer> sessionIds = retrieveLastSessionIds();
        for (int sessionId: sessionIds) {
            assertSessionApplied(sessionId);
        }
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(1);
        assertThat(getInstalledVersion(TestApp.B)).isEqualTo(1);
    }

    // If apk installation fails in one staged session, then all staged session should fail.
    @Test
    public void testInstallMultipleStagedSession_PartialFail_ApkOnly_Commit()
            throws Exception {
        int firstSessionId = stageSingleApk(TestApp.A1).assertSuccessful().getSessionId();
        int secondSessionId = stageSingleApk(TestApp.B1).assertSuccessful().getSessionId();

        // Install TestApp.A2 so that after reboot TestApp.A1 fails to install as it is downgrade
        Install.single(TestApp.A2).commit();

        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(2);
        assertThat(getInstalledVersion(TestApp.B)).isEqualTo(-1);
        storeSessionIds(Arrays.asList(firstSessionId, secondSessionId));
    }

    @Test
    public void testInstallMultipleStagedSession_PartialFail_ApkOnly_VerifyPostReboot()
            throws Exception {
        List<Integer> sessionIds = retrieveLastSessionIds();
        for (int sessionId: sessionIds) {
            assertSessionFailed(sessionId);
        }
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(2);
        assertThat(getInstalledVersion(TestApp.B)).isEqualTo(-1);
    }

    // Failure reason of staged install should be be persisted for single sessions
    @Test
    public void testFailureReasonPersists_SingleSession_Commit() throws Exception {
        int sessionId = Install.single(TestApp.A1).setStaged().commit();
        // Install TestApp.A2 so that after reboot TestApp.A1 fails to install as it is downgrade
        Install.single(TestApp.A2).commit();
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(2);
        storeSessionId(sessionId);
    }

    @Test
    public void testFailureReasonPersists_SingleSession_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionFailedWithMessage(sessionId, "Failed to install sessionId: " + sessionId);
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(2);
    }

    // If apk installation fails in one staged session, then all staged session should fail.
    @Test
    public void testFailureReasonPersists_MultipleSession_Commit() throws Exception {
        int firstSessionId = Install.single(TestApp.A1).setStaged().commit();
        int secondSessionId = Install.single(TestApp.B1).setStaged().commit();
        // Install TestApp.A2 so that after reboot TestApp.A1 fails to install as it is downgrade
        Install.single(TestApp.A2).commit();
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(2);
        assertThat(getInstalledVersion(TestApp.B)).isEqualTo(-1);
        storeSessionIds(Arrays.asList(firstSessionId, secondSessionId));
    }

    @Test
    public void testFailureReasonPersists_MultipleSession_VerifyPostReboot() throws Exception {
        List<Integer> sessionIds = retrieveLastSessionIds();
        int failingSessionId = sessionIds.get(0);
        for (int sessionId: sessionIds) {
            assertSessionFailedWithMessage(sessionId, "Failed to install sessionId: "
                    + failingSessionId);
        }
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(2);
        assertThat(getInstalledVersion(TestApp.B)).isEqualTo(-1);
    }

    @Test
    public void testSamegradeSystemApex_Commit() throws Exception {
        final PackageInfo shim =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager()
                        .getPackageInfo(SHIM_APEX_PACKAGE_NAME, PackageManager.MATCH_APEX);
        assertThat(shim.getLongVersionCode()).isEqualTo(1);
        assertThat(shim.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM).isEqualTo(
                ApplicationInfo.FLAG_SYSTEM);
        assertThat(shim.applicationInfo.flags & ApplicationInfo.FLAG_INSTALLED).isEqualTo(
                ApplicationInfo.FLAG_INSTALLED);
        int sessionId = stageDowngradeSingleApk(TestApp.Apex1).assertSuccessful().getSessionId();
        storeSessionId(sessionId);
    }

    @Test
    public void testSamegradeSystemApex_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        final PackageInfo shim =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager()
                        .getPackageInfo(SHIM_APEX_PACKAGE_NAME, PackageManager.MATCH_APEX);
        assertThat(shim.getLongVersionCode()).isEqualTo(1);
        // Check that APEX on /data wins.
        assertThat(shim.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)
                .isEqualTo(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP);
        assertThat(shim.applicationInfo.flags & ApplicationInfo.FLAG_INSTALLED).isEqualTo(
                ApplicationInfo.FLAG_INSTALLED);
        assertThat(shim.applicationInfo.sourceDir)
                .isEqualTo("/data/apex/active/com.android.apex.cts.shim@1.apex");
        assertThat(shim.applicationInfo.publicSourceDir).isEqualTo(shim.applicationInfo.sourceDir);
    }

    @Test
    public void testInstallApkChangingFingerprint() throws Exception {
        int sessionId = Install.single(TestApp.A1).setStaged().commit();
        storeSessionId(sessionId);
    }

    @Test
    public void testInstallApkChangingFingerprint_VerifyAborted() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionFailed(sessionId);
    }

    @Test
    public void testInstallStagedNoHashtreeApex_Commit() throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        int sessionId = stageSingleApk(ApexNoHashtree2).assertSuccessful().getSessionId();
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
        // Version shouldn't change before reboot.
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
    }

    @Test
    public void testInstallStagedNoHashtreeApex_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
        // Read all files under /apex/com.android.apex.cts.shim to somewhat verify that hashtree
        // is not corrupted
        Files.walkFileTree(Paths.get("/apex/com.android.apex.cts.shim"),
                new SimpleFileVisitor<Path>() {

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.readAllBytes(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc)
                            throws IOException {
                        if (file.endsWith("lost+found")) {
                            return FileVisitResult.CONTINUE;
                        }
                        throw exc;
                    }
                });
    }

    @Test
    public void testInstallStagedApex_SameGrade_NewOneWins_Commit() throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
        assertThat(getInstalledVersion(SHIM_PACKAGE_NAME)).isNotEqualTo(-1);
        int sessionId = Install.single(Apex2WithoutApkInApex).setStaged().commit();
        assertSessionReady(sessionId);
        storeSessionId(sessionId);
    }

    @Test
    public void testInstallStagedApex_SameGrade_NewOneWins_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionApplied(sessionId);
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(2);
        assertThat(getInstalledVersion(SHIM_PACKAGE_NAME)).isEqualTo(-1);
    }

    /**
     * Should fail to verify apex targeting older dev sdk
     */
    @Test
    public void testApexTargetingOldDevSdkFailsVerification() throws Exception {
        stageSingleApk(Apex2SdkTargetP).assertFailure();
    }

    /**
     * Apex should fail to install if apk-in-apex fails to get scanned
     */
    @Test
    public void testApexFailsToInstallIfApkInApexFailsToScan_Commit() throws Exception {
        int sessionId = stageSingleApk(Apex2ApkInApexSdkTargetP).assertSuccessful().getSessionId();
        storeSessionId(sessionId);
    }

    @Test
    public void testApexFailsToInstallIfApkInApexFailsToScan_VerifyPostReboot() throws Exception {
        int sessionId = retrieveLastSessionId();
        assertSessionFailed(sessionId);
        assertSessionFailedWithMessage(sessionId, "Failed to parse "
                + "/apex/com.android.apex.cts.shim/app/CtsShimTargetPSdk");
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
    }

    @Test
    public void testCorruptedApexFailsVerification_b146895998() throws Exception {
        int sessionId = stageSingleApk(CorruptedApex_b146895998).assertFailure().getSessionId();
        PackageInstaller.SessionInfo sessionInfo = getSessionInfo(sessionId);
        assertThat(sessionInfo).isStagedSessionFailed();
    }

    /**
     * Should fail to pass apk signature check
     */
    @Test
    public void testApexWithUnsignedApkFailsVerification() throws Exception {
        assertThat(stageSingleApk(Apex2NoApkSignature).getErrorMessage())
                .contains("INSTALL_PARSE_FAILED_NO_CERTIFICATES");
    }

    /**
     * Should fail to verify apex with unsigned payload
     */
    @Test
    public void testApexWithUnsignedPayloadFailsVerification() throws Exception {
        int sessionId = stageSingleApk(Apex2UnsignedPayload).assertFailure().getSessionId();
        PackageInstaller.SessionInfo sessionInfo = getSessionInfo(sessionId);
        assertThat(sessionInfo).isStagedSessionFailed();
        assertThat(sessionInfo.getStagedSessionErrorMessage())
                .contains("AVB footer verification failed");
    }

    /**
     * Should fail to verify apex signed payload with a different key
     */
    @Test
    public void testApexSignPayloadWithDifferentKeyFailsVerification() throws Exception {
        int sessionId = stageSingleApk(
                Apex2SignPayloadWithDifferentKey).assertFailure().getSessionId();
        PackageInstaller.SessionInfo sessionInfo = getSessionInfo(sessionId);
        assertThat(sessionInfo).isStagedSessionFailed();
        assertThat(sessionInfo.getStagedSessionErrorMessage())
                .contains("public key doesn't match the pre-installed one");
    }

    /**
     * Test non-priv apps cannot access /data/app-staging folder contents
     */
    @Test
    public void testAppStagingDirCannotBeReadByNonPrivApps() throws Exception {
        final int sessionId = stageSingleApk(TestApp.A1).assertSuccessful().getSessionId();
        // Non-priv apps should not be able to view contents of app-staging directory
        final File appStagingDir = new File("/data/app-staging");
        assertThat(appStagingDir.exists()).isTrue();
        assertThat(appStagingDir.listFiles()).isNull();
        // Non-owner user should not be able to access sub-dirs of app-staging directory
        final File appStagingSubDir = new File("/data/app-staging/session_" + sessionId);
        assertThat(appStagingSubDir.exists()).isFalse();
        assertThat(appStagingDir.listFiles()).isNull();
    }

    private static long getInstalledVersion(String packageName) {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo info = pm.getPackageInfo(packageName, PackageManager.MATCH_APEX);
            return info.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    @Test
    public void testApexSetsUpdatedSystemAppFlag_preUpdate() throws Exception {
        final PackageInfo info = InstallUtils.getPackageInfo(SHIM_APEX_PACKAGE_NAME);
        assertThat(info).isNotNull();
        boolean isSystemApp = (info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        boolean isUpdatedSystemApp =
                (info.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        assertThat(isSystemApp).isTrue();
        assertThat(isUpdatedSystemApp).isFalse();
    }

    @Test
    public void testApexSetsUpdatedSystemAppFlag_postUpdate() throws Exception {
        final PackageInfo info = InstallUtils.getPackageInfo(SHIM_APEX_PACKAGE_NAME);
        assertThat(info).isNotNull();
        boolean isSystemApp = (info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        boolean isUpdatedSystemApp =
                (info.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        assertThat(isSystemApp).isTrue();
        assertThat(isUpdatedSystemApp).isTrue();
    }

    @Test
    public void testRebootlessUpdate() throws Exception {
        InstallUtils.dropShellPermissionIdentity();
        InstallUtils.adoptShellPermissionIdentity(Manifest.permission.INSTALL_PACKAGE_UPDATES);

        final PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        {
            PackageInfo apex = pm.getPackageInfo(SHIM_APEX_PACKAGE_NAME, PackageManager.MATCH_APEX);
            assertThat(apex.getLongVersionCode()).isEqualTo(1);
            assertThat(apex.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM)
                    .isEqualTo(ApplicationInfo.FLAG_SYSTEM);
            assertThat(apex.applicationInfo.flags & ApplicationInfo.FLAG_INSTALLED)
                    .isEqualTo(ApplicationInfo.FLAG_INSTALLED);
            assertThat(apex.applicationInfo.sourceDir).startsWith("/system/apex");
        }

        Install.single(Apex2Rebootless).commit();
        {
            PackageInfo apex = pm.getPackageInfo(SHIM_APEX_PACKAGE_NAME, PackageManager.MATCH_APEX);
            assertThat(apex.getLongVersionCode()).isEqualTo(2);
            assertThat(apex.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)
                    .isEqualTo(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP);
            assertThat(apex.applicationInfo.flags & ApplicationInfo.FLAG_INSTALLED)
                    .isEqualTo(ApplicationInfo.FLAG_INSTALLED);
            assertThat(apex.applicationInfo.sourceDir).startsWith("/data/apex/active");
        }
        {
            PackageInfo apex = pm.getPackageInfo(SHIM_APEX_PACKAGE_NAME,
                    PackageManager.MATCH_APEX | PackageManager.MATCH_FACTORY_ONLY);
            assertThat(apex.getLongVersionCode()).isEqualTo(1);
            assertThat(apex.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM)
                    .isEqualTo(ApplicationInfo.FLAG_SYSTEM);
            assertThat(apex.applicationInfo.flags & ApplicationInfo.FLAG_INSTALLED)
                    .isEqualTo(ApplicationInfo.FLAG_INSTALLED);
            assertThat(apex.applicationInfo.sourceDir).startsWith("/system/apex");
        }
    }

    @Test
    public void testRebootlessUpdate_installV3() throws Exception {
        InstallUtils.dropShellPermissionIdentity();
        InstallUtils.adoptShellPermissionIdentity(Manifest.permission.INSTALL_PACKAGE_UPDATES);

        final PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();

        Install.single(Apex3Rebootless).commit();
        {
            PackageInfo apex = pm.getPackageInfo(SHIM_APEX_PACKAGE_NAME, PackageManager.MATCH_APEX);
            assertThat(apex.getLongVersionCode()).isEqualTo(3);
            assertThat(apex.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)
                    .isEqualTo(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP);
            assertThat(apex.applicationInfo.flags & ApplicationInfo.FLAG_INSTALLED)
                    .isEqualTo(ApplicationInfo.FLAG_INSTALLED);
            assertThat(apex.applicationInfo.sourceDir).startsWith("/data/apex/active");
        }
        {
            PackageInfo apex = pm.getPackageInfo(SHIM_APEX_PACKAGE_NAME,
                    PackageManager.MATCH_APEX | PackageManager.MATCH_FACTORY_ONLY);
            assertThat(apex.getLongVersionCode()).isEqualTo(1);
            assertThat(apex.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM)
                    .isEqualTo(ApplicationInfo.FLAG_SYSTEM);
            assertThat(apex.applicationInfo.flags & ApplicationInfo.FLAG_INSTALLED)
                    .isEqualTo(ApplicationInfo.FLAG_INSTALLED);
            assertThat(apex.applicationInfo.sourceDir).startsWith("/system/apex");
        }
    }

    @Test
    public void testRebootlessUpdate_downgradeToV2_fails() throws Exception {
        InstallUtils.dropShellPermissionIdentity();
        InstallUtils.adoptShellPermissionIdentity(Manifest.permission.INSTALL_PACKAGE_UPDATES);

        final PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();

        {
            PackageInfo apex = pm.getPackageInfo(SHIM_APEX_PACKAGE_NAME, PackageManager.MATCH_APEX);
            assertThat(apex.getLongVersionCode()).isEqualTo(3);
            assertThat(apex.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)
                    .isEqualTo(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP);
            assertThat(apex.applicationInfo.flags & ApplicationInfo.FLAG_INSTALLED)
                    .isEqualTo(ApplicationInfo.FLAG_INSTALLED);
            assertThat(apex.applicationInfo.sourceDir).startsWith("/data/apex/active");
        }

        InstallUtils.commitExpectingFailure(
                    AssertionError.class,
                    "INSTALL_FAILED_VERSION_DOWNGRADE",
                    Install.single(Apex2Rebootless));
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(3);
    }

    @Test
    public void testRebootlessUpdate_noPermission_fails() throws Exception {
        InstallUtils.dropShellPermissionIdentity();

        InstallUtils.commitExpectingFailure(SecurityException.class,
                    "Not allowed to perform APEX updates",
                    Install.single(Apex2Rebootless));
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
    }

    @Test
    public void testRebootlessUpdate_noPreInstalledApex_fails() throws Exception {
        assertThat(getInstalledVersion(DIFFERENT_APEX_PACKAGE_NAME)).isEqualTo(-1);
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);

        InstallUtils.commitExpectingFailure(
                AssertionError.class,
                "Attempting to install new APEX package",
                Install.single(Apex2DifferentPackageName));
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
    }

    @Test
    public void testRebootlessUpdate_unsignedPayload_fails() throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);

        InstallUtils.commitExpectingFailure(
                AssertionError.class,
                "AVB footer verification failed",
                Install.single(Apex2UnsignedPayload));
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
    }

    @Test
    public void testRebootlessUpdate_payloadSignedWithDifferentKey_fails() throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);

        InstallUtils.commitExpectingFailure(
                AssertionError.class,
                "public key doesn't match the pre-installed one",
                Install.single(Apex2SignPayloadWithDifferentKey));
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
    }

    @Test
    public void testRebootlessUpdate_outerContainerSignedWithDifferentCert_fails()
            throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);

        InstallUtils.commitExpectingFailure(
                AssertionError.class,
                "APK container signature of .+ is not compatible with the one currently installed",
                Install.single(Apex2DifferentCertificate));
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
    }

    @Test
    public void testRebootlessUpdate_outerContainerUnsigned_fails() throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);

        InstallUtils.commitExpectingFailure(
                AssertionError.class,
                "Failed to collect certificates from",
                Install.single(Apex2NoApkSignature));
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
    }

    @Test
    public void testRebootlessUpdate_targetsOlderSdk_fails() throws Exception {
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);

        InstallUtils.commitExpectingFailure(
                AssertionError.class,
                "Requires development platform P",
                Install.single(Apex2SdkTargetP));
        assertThat(getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
    }

    @Test
    public void testGetInactiveApexFactoryPackagesAfterApexInstall_containsNoDuplicates()
            throws Exception {
        int flags = (PackageManager.MATCH_APEX | PackageManager.MATCH_FACTORY_ONLY
                | PackageManager.MATCH_UNINSTALLED_PACKAGES);
        List<PackageInfo> packageInfos =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager()
                        .getInstalledPackages(flags);
        Set<String> foundPackages = new HashSet<>();
        for (PackageInfo pi : packageInfos) {
            if (foundPackages.contains(pi.packageName)) {
                throw new AssertionError(pi.packageName + " is listed at least twice.");
            }
            foundPackages.add(pi.packageName);
        }
    }

    // It becomes harder to maintain this variety of install-related helper methods.
    // TODO(ioffe): refactor install-related helper methods into a separate utility.
    private static int createStagedSession() throws Exception {
        return Install.single(TestApp.A1).setStaged().createSession();
    }

    private static Intent commitSession(int sessionId) throws IOException, InterruptedException {
        LocalIntentSender sender = new LocalIntentSender();
        InstallUtils.openPackageInstallerSession(sessionId)
                .commit(sender.getIntentSender());
        var result = sender.pollResult(5, TimeUnit.MINUTES);
        if (result == null) {
            throw new AssertionError("Install timeout, sessionId=" + sessionId);
        }
        return result;
    }

    private static StageSessionResult stageDowngradeSingleApk(TestApp testApp) throws Exception {
        Log.i(TAG, "Staging a downgrade of " + testApp);
        int sessionId = Install.single(testApp).setStaged().setRequestDowngrade().createSession();
        // Commit the session (this will start the installation workflow).
        Log.i(TAG, "Committing downgrade session for apk: " + testApp);
        Intent result = commitSession(sessionId);
        return new StageSessionResult(sessionId, result);
    }

    private static StageSessionResult stageSingleApk(String apkFileName, String outputFileName)
            throws Exception {
        File tmpFile = File.createTempFile(outputFileName, null);
        try (InputStream is =
                     StagedInstallTest.class.getClassLoader().getResourceAsStream(apkFileName)) {
            Files.copy(is, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        TestApp testApp = new TestApp(tmpFile.getName(), null, -1,
                apkFileName.endsWith(".apex"), tmpFile);
        return stageSingleApk(testApp);
    }

    private static StageSessionResult stageSingleApk(TestApp testApp) throws Exception {
        Log.i(TAG, "Staging an install of " + testApp);
        int sessionId = Install.single(testApp).setStaged().createSession();
        // Commit the session (this will start the installation workflow).
        Log.i(TAG, "Committing session for apk: " + testApp);
        Intent result = commitSession(sessionId);
        return new StageSessionResult(sessionId, result);
    }

    private static StageSessionResult stageMultipleApks(TestApp... testApps) throws Exception {
        Log.i(TAG, "Staging an install of " + Arrays.toString(testApps));
        int multiPackageSessionId = Install.multi(testApps).setStaged().createSession();
        Intent result = commitSession(multiPackageSessionId);
        return new StageSessionResult(multiPackageSessionId, result);
    }

    private static void assertSessionApplied(int sessionId) {
        assertSessionState(sessionId,
                (session) -> assertThat(session).isStagedSessionApplied());
    }

    private static void assertSessionReady(int sessionId) {
        assertSessionState(sessionId,
                (session) -> assertThat(session).isStagedSessionReady());
    }

    private static void assertSessionFailed(int sessionId) {
        assertSessionState(sessionId,
                (session) -> assertThat(session).isStagedSessionFailed());
    }

    private static void assertSessionFailedWithMessage(int sessionId, String msg) {
        assertSessionState(sessionId, (session) -> {
            assertThat(session).isStagedSessionFailed();
            assertThat(session.getStagedSessionErrorMessage()).contains(msg);
        });
    }

    private static void assertSessionState(
            int sessionId, Consumer<PackageInstaller.SessionInfo> assertion) {
        PackageInstaller packageInstaller = getPackageInstaller();

        List<PackageInstaller.SessionInfo> sessions = packageInstaller.getStagedSessions();
        boolean found = false;
        for (PackageInstaller.SessionInfo session : sessions) {
            if (session.getSessionId() == sessionId) {
                assertion.accept(session);
                found = true;
            }
        }
        assertWithMessage("Expecting to find session in getStagedSession()")
                .that(found).isTrue();

        // Test also that getSessionInfo correctly returns the session.
        PackageInstaller.SessionInfo sessionInfo = packageInstaller.getSessionInfo(sessionId);
        assertion.accept(sessionInfo);
    }

    private void storeSessionId(int sessionId) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(mTestStateFile))) {
            writer.write("" + sessionId);
        }
    }

    private int retrieveLastSessionId() throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(mTestStateFile))) {
            return Integer.parseInt(reader.readLine());
        }
    }

    private void storeSessionIds(List<Integer> sessionIds) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(mTestStateFile))) {
            writer.write(sessionIds.toString());
        }
    }

    private List<Integer> retrieveLastSessionIds() throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(mTestStateFile))) {
            String line = reader.readLine();
            String[] sessionIdsStr = line.substring(1, line.length() - 1).split(", ");
            ArrayList<Integer> result = new ArrayList<>();
            for (String sessionIdStr: sessionIdsStr) {
                result.add(Integer.parseInt(sessionIdStr));
            }
            return result;
        }
    }

    // TODO(ioffe): not really-tailored to staged install, rename to InstallResult?
    private static final class StageSessionResult {
        private final int sessionId;
        private final Intent result;

        private StageSessionResult(int sessionId, Intent result) {
            this.sessionId = sessionId;
            this.result = result;
        }

        public int getSessionId() {
            return sessionId;
        }

        public String getErrorMessage() {
            return extractErrorMessage(result);
        }

        public StageSessionResult assertSuccessful() {
            assertStatusSuccess(result);
            return this;
        }

        public StageSessionResult assertFailure() {
            assertStatusFailure(result);
            return this;
        }
    }

    /**
     * Counts the number of broadcast intents received for a given type during the test.
     * Used by to check no broadcast intents were received during the test.
     */
    private static class BroadcastCounter extends BroadcastReceiver {
        private final Context mContext;
        private final AtomicInteger mNumBroadcastReceived = new AtomicInteger();

        BroadcastCounter(String action) {
            mContext = InstrumentationRegistry.getInstrumentation().getContext();
            mContext.registerReceiver(this, new IntentFilter(action),
                    Context.RECEIVER_EXPORTED_UNAUDITED);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            mNumBroadcastReceived.incrementAndGet();
        }

        /**
         * Waits for a while and checks no broadcasts are received.
         */
        void assertNoBroadcastReceived() {
            try {
                // Sleep for a reasonable amount of time and check no broadcast is received
                Thread.sleep(TimeUnit.SECONDS.toMillis(10));
            } catch (InterruptedException ignore) {
            }
            mContext.unregisterReceiver(this);
            assertThat(mNumBroadcastReceived.get()).isEqualTo(0);
        }
    }

    private static String extractErrorMessage(Intent result) {
        int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        if (status == -1) {
            throw new AssertionError("PENDING USER ACTION");
        }
        if (status == 0) {
            throw new AssertionError("Result was successful");
        }
        return result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
    }

    private static void abandonSession(int sessionId) {
        getPackageInstaller().abandonSession(sessionId);
    }

    private static PackageInstaller.SessionInfo getSessionInfo(int sessionId) {
        return getPackageInstaller().getSessionInfo(sessionId);
    }

    private static boolean isAuto() {
        var pm = InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }
}
