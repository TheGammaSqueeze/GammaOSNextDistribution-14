/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts_root.rollback.host.app;

import static com.android.cts.rollback.lib.RollbackInfoSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.rollback.RollbackInfo;
import android.content.rollback.RollbackManager;
import android.os.storage.StorageManager;
import android.provider.DeviceConfig;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.InstallUtils;
import com.android.cts.install.lib.TestApp;
import com.android.cts.rollback.lib.Rollback;
import com.android.cts.rollback.lib.RollbackUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.TimeUnit;

/**
 * On-device helper test methods used for host-driven rollback tests.
 */
@RunWith(JUnit4.class)
public class HostTestHelper {
    private static final String PROPERTY_WATCHDOG_TRIGGER_FAILURE_COUNT =
            "watchdog_trigger_failure_count";

    @Before
    public void setup() {
        InstallUtils.adoptShellPermissionIdentity(
                    Manifest.permission.INSTALL_PACKAGES,
                    Manifest.permission.DELETE_PACKAGES,
                    Manifest.permission.TEST_MANAGE_ROLLBACKS,
                    Manifest.permission.FORCE_STOP_PACKAGES,
                    Manifest.permission.WRITE_DEVICE_CONFIG);
    }

    @After
    public void teardown() {
        InstallUtils.dropShellPermissionIdentity();
    }

    @Test
    public void cleanUp() {
        // Remove all pending rollbacks
        RollbackManager rm = RollbackUtils.getRollbackManager();
        rm.getAvailableRollbacks().stream().flatMap(info -> info.getPackages().stream())
                .map(info -> info.getPackageName()).forEach(rm::expireRollbackForPackage);
    }

    @Test
    public void testRollbackDataPolicy_Phase1_Install() throws Exception {
        Install.multi(TestApp.A1, TestApp.B1, TestApp.C1).commit();
        // Write user data version = 1
        InstallUtils.processUserData(TestApp.A);
        InstallUtils.processUserData(TestApp.B);
        InstallUtils.processUserData(TestApp.C);

        Install a2 = Install.single(TestApp.A2).setStaged()
                .setEnableRollback(PackageManager.ROLLBACK_DATA_POLICY_WIPE);
        Install b2 = Install.single(TestApp.B2).setStaged()
                .setEnableRollback(PackageManager.ROLLBACK_DATA_POLICY_RESTORE);
        Install c2 = Install.single(TestApp.C2).setStaged()
                .setEnableRollback(PackageManager.ROLLBACK_DATA_POLICY_RETAIN);
        Install.multi(a2, b2, c2).setEnableRollback().setStaged().commit();
    }

    @Test
    public void testRollbackDataPolicy_Phase2_Rollback() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(2);
        // Write user data version = 2
        InstallUtils.processUserData(TestApp.A);
        InstallUtils.processUserData(TestApp.B);
        InstallUtils.processUserData(TestApp.C);

        RollbackInfo info = RollbackUtils.getAvailableRollback(TestApp.A);
        RollbackUtils.rollback(info.getRollbackId());
    }

    @Test
    public void testRollbackDataPolicy_Phase3_VerifyRollback() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(1);
        assertThat(InstallUtils.getInstalledVersion(TestApp.C)).isEqualTo(1);
        // Read user data version from userdata.txt
        // A's user data version is -1 for user data is wiped.
        // B's user data version is 1 for user data is restored.
        // C's user data version is 2 for user data is retained.
        assertThat(InstallUtils.getUserDataVersion(TestApp.A)).isEqualTo(-1);
        assertThat(InstallUtils.getUserDataVersion(TestApp.B)).isEqualTo(1);
        assertThat(InstallUtils.getUserDataVersion(TestApp.C)).isEqualTo(2);
    }

    @Test
    public void testRollbackApkDataDirectories_Phase1_InstallV1() throws Exception {
        Install.single(TestApp.A1).commit();
    }

    @Test
    public void testRollbackApkDataDirectories_Phase2_InstallV2() throws Exception {
        Install.single(TestApp.A2).setStaged().setEnableRollback().commit();
    }

    @Test
    public void testRollbackApkDataDirectories_Phase3_Rollback() throws Exception {
        RollbackInfo available = RollbackUtils.getAvailableRollback(TestApp.A);
        RollbackUtils.rollback(available.getRollbackId(), TestApp.A2);
    }

    @Test
    public void testExpireSession_Phase1_Install() throws Exception {
        Install.single(TestApp.A1).commit();
        int sessionId = Install.single(TestApp.A2).setEnableRollback().setStaged().commit();

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        SharedPreferences prefs = context.getSharedPreferences("test", 0);
        prefs.edit().putInt("sessionId", sessionId).commit();
    }

    @Test
    public void testExpireSession_Phase2_VerifyInstall() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        RollbackInfo rollback = RollbackUtils.getAvailableRollback(TestApp.A);
        assertThat(rollback).isNotNull();
    }

    @Test
    public void testExpireSession_Phase3_VerifyRollback() throws Exception {
        RollbackInfo rollback = RollbackUtils.getAvailableRollback(TestApp.A);
        assertThat(rollback).isNotNull();

        // Check the session is expired
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        SharedPreferences prefs = context.getSharedPreferences("test", 0);
        int sessionId = prefs.getInt("sessionId", -1);
        PackageInstaller.SessionInfo info = InstallUtils.getStagedSessionInfo(sessionId);
        assertThat(info).isNull();
    }

    @Test
    public void testRollbackApexDataDirectories_Phase1_Install() throws Exception {
        Install.single(TestApp.Apex2).setStaged().setEnableRollback().commit();
    }

    @Test
    public void testBadApkOnly_Phase1_Install() throws Exception {
        Install.single(TestApp.A1).commit();
        Install.single(TestApp.ACrashing2).setEnableRollback().setStaged().commit();
    }

    @Test
    public void testBadApkOnly_Phase2_VerifyInstall() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);

        RollbackInfo rollback = RollbackUtils.getAvailableRollback(TestApp.A);
        assertThat(rollback).isNotNull();
        assertThat(rollback).packagesContainsExactly(Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(rollback.isStaged()).isTrue();

        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ROLLBACK,
                PROPERTY_WATCHDOG_TRIGGER_FAILURE_COUNT,
                Integer.toString(5), false);
        RollbackUtils.sendCrashBroadcast(TestApp.A, 4);
        // Sleep for a while to make sure we don't trigger rollback
        Thread.sleep(TimeUnit.SECONDS.toMillis(30));
    }

    @Test
    public void testBadApkOnly_Phase3_VerifyRollback() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);

        RollbackInfo rollback = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(rollback).isNotNull();
        assertThat(rollback).packagesContainsExactly(Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(rollback).causePackagesContainsExactly(TestApp.ACrashing2);
        assertThat(rollback).isStaged();
        assertThat(rollback.getCommittedSessionId()).isNotEqualTo(-1);
    }

    @Test
    public void testNativeWatchdogTriggersRollback_Phase1_Install() throws Exception {
        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setEnableRollback().setStaged().commit();
    }

    @Test
    public void testNativeWatchdogTriggersRollback_Phase2_VerifyInstall() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        RollbackInfo rollback = RollbackUtils.getAvailableRollback(TestApp.A);
        assertThat(rollback).isNotNull();
    }

    @Test
    public void testNativeWatchdogTriggersRollback_Phase3_VerifyRollback() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
        RollbackInfo rollback = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(rollback).isNotNull();
    }

    @Test
    public void testNativeWatchdogTriggersRollbackForAll_Phase1_Install() throws Exception {
        Install.single(TestApp.A1).commit();
        Install.single(TestApp.B1).commit();
        Install.single(TestApp.A2).setEnableRollback().setStaged().commit();
        Install.single(TestApp.B2).setEnableRollback().setStaged().commit();
    }

    @Test
    public void testNativeWatchdogTriggersRollbackForAll_Phase2_VerifyInstall() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(2);
        RollbackInfo rollbackA = RollbackUtils.getAvailableRollback(TestApp.A);
        RollbackInfo rollbackB = RollbackUtils.getAvailableRollback(TestApp.B);
        assertThat(rollbackA).isNotNull();
        assertThat(rollbackB).isNotNull();
        assertThat(rollbackA.getRollbackId()).isNotEqualTo(rollbackB.getRollbackId());
    }

    @Test
    public void testNativeWatchdogTriggersRollbackForAll_Phase3_VerifyRollback() throws Exception {
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(1);
        RollbackInfo rollbackA = RollbackUtils.getCommittedRollback(TestApp.A);
        RollbackInfo rollbackB = RollbackUtils.getCommittedRollback(TestApp.B);
        assertThat(rollbackA).isNotNull();
        assertThat(rollbackB).isNotNull();
        assertThat(rollbackA.getRollbackId()).isNotEqualTo(rollbackB.getRollbackId());
    }

    @Test
    public void isCheckpointSupported() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        assertThat(sm.isCheckpointSupported()).isTrue();
    }
}
