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
 * limitations under the License
 */

package com.android.tests.stagedinstall;

import static com.android.cts.install.lib.PackageInstallerSessionInfoSubject.assertThat;
import static com.android.cts.shim.lib.ShimPackage.SHIM_APEX_PACKAGE_NAME;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.pm.PackageInstaller;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.InstallUtils;
import com.android.cts.install.lib.LocalIntentSender;
import com.android.cts.install.lib.TestApp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * These tests use a similar structure to {@link StagedInstallTest}. See
 * {@link StagedInstallTest} documentation for reference.
 *
 * @see StagedInstallTest
 */
@RunWith(JUnit4.class)
public class ApexShimValidationTest {

    @Before
    public void adoptShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
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

    @Test
    public void testRejectsApexWithAdditionalFile_Commit() throws Exception {
        int sessionId = stageApex("com.android.apex.cts.shim.v2_additional_file.apex");
        PackageInstaller.SessionInfo info =
                InstallUtils.getPackageInstaller().getSessionInfo(sessionId);
        assertThat(info).isStagedSessionFailed();
    }

    @Test
    public void testRejectsApexWithAdditionalFolder_Commit() throws Exception {
        int sessionId = stageApex("com.android.apex.cts.shim.v2_additional_folder.apex");
        PackageInstaller.SessionInfo info =
                InstallUtils.getPackageInstaller().getSessionInfo(sessionId);
        assertThat(info).isStagedSessionFailed();
    }

    @Test
    public void testRejectsApexWithPostInstallHook_Commit() throws Exception {
        int sessionId = stageApex("com.android.apex.cts.shim.v2_with_post_install_hook.apex");
        PackageInstaller.SessionInfo info =
                InstallUtils.getPackageInstaller().getSessionInfo(sessionId);
        assertThat(info).isStagedSessionFailed();
    }

    @Test
    public void testRejectsApexWithPreInstallHook_Commit() throws Exception {
        int sessionId = stageApex("com.android.apex.cts.shim.v2_with_pre_install_hook.apex");
        PackageInstaller.SessionInfo info =
                InstallUtils.getPackageInstaller().getSessionInfo(sessionId);
        assertThat(info).isStagedSessionFailed();
    }

    @Test
    public void testRejectsApexWrongSHA_Commit() throws Exception {
        int sessionId = stageApex("com.android.apex.cts.shim.v2_wrong_sha.apex");
        PackageInstaller.SessionInfo info =
                InstallUtils.getPackageInstaller().getSessionInfo(sessionId);
        assertThat(info).isStagedSessionFailed();
    }

    @Test
    public void testInstallRejected_VerifyPostReboot() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
    }

    @Test
    public void testRejectsApexWithAdditionalFile_rebootless() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        TestApp apex = new TestApp("ShimApex", SHIM_APEX_PACKAGE_NAME, 2,
                true, "com.android.apex.cts.shim.v2_additional_file.apex");
        InstallUtils.commitExpectingFailure(
                AssertionError.class,
                "is an unexpected file inside the shim apex",
                Install.single(apex));
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
    }

    @Test
    public void testRejectsApexWithAdditionalFolder_rebootless() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        TestApp apex = new TestApp("ShimApex", SHIM_APEX_PACKAGE_NAME, 2,
                true, "com.android.apex.cts.shim.v2_additional_folder.apex");
        InstallUtils.commitExpectingFailure(
                AssertionError.class,
                "is an unexpected file inside the shim apex",
                Install.single(apex));
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
    }

    @Test
    public void testRejectsApexWithPostInstallHook_rebootless() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        TestApp apex = new TestApp("ShimApex", SHIM_APEX_PACKAGE_NAME, 2,
                true, "com.android.apex.cts.shim.v2_with_post_install_hook.apex");
        InstallUtils.commitExpectingFailure(
                AssertionError.class,
                "Shim apex is not allowed to have pre or post install hooks",
                Install.single(apex));
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
    }

    @Test
    public void testRejectsApexWithPreInstallHook_rebootless() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        TestApp apex = new TestApp("ShimApex", SHIM_APEX_PACKAGE_NAME, 2,
                true, "com.android.apex.cts.shim.v2_with_pre_install_hook.apex");
        InstallUtils.commitExpectingFailure(
                AssertionError.class,
                "Shim apex is not allowed to have pre or post install hooks",
                Install.single(apex));
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
    }

    @Test
    public void testRejectsApexWrongSHA_rebootless() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
        TestApp apex = new TestApp("ShimApex", SHIM_APEX_PACKAGE_NAME, 2,
                true, "com.android.apex.cts.shim.v2_wrong_sha.apex");
        InstallUtils.commitExpectingFailure(
                AssertionError.class, "has unexpected SHA512 hash", Install.single(apex));
        assertThat(InstallUtils.getInstalledVersion(SHIM_APEX_PACKAGE_NAME)).isEqualTo(1);
    }

    private static int stageApex(String apexFileName) throws Exception {
        TestApp apexTestApp = new TestApp("ShimApex", SHIM_APEX_PACKAGE_NAME, 2,
                true, apexFileName);
        int sessionId = Install.single(apexTestApp).setStaged().createSession();
        try (PackageInstaller.Session session =
                     InstallUtils.openPackageInstallerSession(sessionId)) {
            LocalIntentSender sender = new LocalIntentSender();
            session.commit(sender.getIntentSender());
            sender.getResult();
            return sessionId;
        }
    }
}
