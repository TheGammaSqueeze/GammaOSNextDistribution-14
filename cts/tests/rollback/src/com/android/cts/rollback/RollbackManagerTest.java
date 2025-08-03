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

package com.android.cts.rollback;

import static com.android.cts.rollback.lib.RollbackInfoSubject.assertThat;
import static com.android.cts.rollback.lib.RollbackUtils.getRollbackManager;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.Manifest;
import android.content.Intent;
import android.content.pm.Flags;
import android.content.pm.PackageManager;
import android.content.rollback.RollbackInfo;
import android.content.rollback.RollbackManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;

import androidx.test.InstrumentationRegistry;

import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.InstallUtils;
import com.android.cts.install.lib.LocalIntentSender;
import com.android.cts.install.lib.TestApp;
import com.android.cts.install.lib.Uninstall;
import com.android.cts.rollback.lib.Rollback;
import com.android.cts.rollback.lib.RollbackBroadcastReceiver;
import com.android.cts.rollback.lib.RollbackUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * CTS Tests for RollbackManager APIs.
 */
@RunWith(JUnit4.class)
public class RollbackManagerTest {
    // TODO: use PackageManager.RollbackDataPolicy.* when they are system API
    private static final int ROLLBACK_DATA_POLICY_RESTORE = 0;
    private static final int ROLLBACK_DATA_POLICY_WIPE = 1;

    private static final String PROPERTY_ENABLE_ROLLBACK_TIMEOUT_MILLIS = "enable_rollback_timeout";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    /**
     * Adopts common permissions needed to test rollbacks and uninstalls the
     * test apps.
     */
    @Before
    public void setup() throws InterruptedException, IOException {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(
                    Manifest.permission.INSTALL_PACKAGES,
                    Manifest.permission.DELETE_PACKAGES,
                    Manifest.permission.MANAGE_ROLLBACKS,
                    Manifest.permission.TEST_MANAGE_ROLLBACKS,
                    Manifest.permission.READ_DEVICE_CONFIG,
                    Manifest.permission.WRITE_DEVICE_CONFIG,
                    Manifest.permission.FORCE_STOP_PACKAGES,
                    Manifest.permission.SET_TIME);

        Uninstall.packages(TestApp.A, TestApp.B, TestApp.C);
    }

    /**
     * Drops adopted shell permissions and uninstalls the test apps.
     */
    @After
    public void teardown() throws InterruptedException, IOException {
        Uninstall.packages(TestApp.A, TestApp.B, TestApp.C);

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Test basic rollbacks.
     */
    @Test
    public void testBasic() throws Exception {
        Install.single(TestApp.A1).commit();
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
        RollbackUtils.waitForRollbackGone(
                () -> getRollbackManager().getAvailableRollbacks(), TestApp.A);
        RollbackUtils.waitForRollbackGone(
                () -> getRollbackManager().getRecentlyCommittedRollbacks(), TestApp.A);

        Install.single(TestApp.A2).setEnableRollback().commit();
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        InstallUtils.processUserData(TestApp.A);
        RollbackInfo available = RollbackUtils.waitForAvailableRollback(TestApp.A);
        assertThat(available).isNotStaged();
        assertThat(available).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(RollbackUtils.getCommittedRollback(TestApp.A)).isNull();

        RollbackUtils.rollback(available.getRollbackId(), TestApp.A2);
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
        InstallUtils.processUserData(TestApp.A);
        RollbackUtils.waitForUnavailableRollback(TestApp.A);
        RollbackInfo committed = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(committed).isNotNull();
        assertThat(committed).hasRollbackId(available.getRollbackId());
        assertThat(committed).isNotStaged();
        assertThat(committed).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(committed).causePackagesContainsExactly(TestApp.A2);
    }

    /**
     * Tests rollback of multi-package installs is implemented.
     */
    @Test
    public void testBasic_MultiPackage() throws Exception {
        // Prep installation of the test apps.
        Install.multi(TestApp.A1, TestApp.B1).commit();
        Install.multi(TestApp.A2, TestApp.B2).setEnableRollback().commit();
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(2);

        // TestApp.A should now be available for rollback.
        RollbackInfo rollback = RollbackUtils.waitForAvailableRollback(TestApp.A);
        assertThat(rollback).isNotNull();
        assertThat(rollback).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1),
                Rollback.from(TestApp.B2).to(TestApp.B1));

        // Rollback the app. It should cause both test apps to be rolled back.
        RollbackUtils.rollback(rollback.getRollbackId());
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(1);

        // We should see recent rollbacks listed for both A and B.
        RollbackInfo rollbackA = RollbackUtils.getCommittedRollback(TestApp.A);
        RollbackInfo rollbackB = RollbackUtils.getCommittedRollback(TestApp.B);
        assertThat(rollbackA).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1),
                Rollback.from(TestApp.B2).to(TestApp.B1));
        assertThat(rollbackA).hasRollbackId(rollback.getRollbackId());
        assertThat(rollbackB).hasRollbackId(rollback.getRollbackId());
    }

    /**
     * Tests rollbacks are properly persisted.
     */
    @Test
    public void testSingleRollbackPersistence() throws Exception {
        RollbackManager rm = RollbackUtils.getRollbackManager();

        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setEnableRollback().commit();
        RollbackInfo rollbackA = RollbackUtils.waitForAvailableRollback(TestApp.A);
        assertThat(rollbackA).isNotNull();

        // Check the available rollback is persisted correctly
        rm.reloadPersistedData();
        rollbackA = RollbackUtils.getAvailableRollback(TestApp.A);
        assertThat(rollbackA).packagesContainsExactly(Rollback.from(TestApp.A2).to(TestApp.A1));

        // Rollback the app
        TestApp cause = new TestApp("Foo", "com.android.tests.rollback.testapp.Foo",
                /*versionCode*/ 42, /*isApex*/ false);
        RollbackUtils.rollback(rollbackA.getRollbackId(), cause);
        RollbackInfo committed = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(committed).isNotNull();

        // Check the committed rollback is persisted correctly
        rm.reloadPersistedData();
        committed = RollbackUtils.getCommittedRollback(TestApp.A);
        assertThat(committed).hasRollbackId(rollbackA.getRollbackId());
        assertThat(committed).isNotStaged();
        assertThat(committed).packagesContainsExactly(Rollback.from(TestApp.A2).to(TestApp.A1));
        assertThat(committed).causePackagesContainsExactly(cause);
    }

    /**
     * Tests rollbacks are properly persisted.
     */
    @Test
    public void testMultiRollbackPersistence() throws Exception {
        RollbackManager rm = RollbackUtils.getRollbackManager();

        Install.multi(TestApp.A1, TestApp.B1).commit();
        Install.multi(TestApp.A2, TestApp.B2).setEnableRollback().commit();
        RollbackInfo rollbackA = RollbackUtils.waitForAvailableRollback(TestApp.A);
        RollbackInfo rollbackB = RollbackUtils.waitForAvailableRollback(TestApp.B);
        assertThat(rollbackA).isNotNull();
        assertThat(rollbackB).isNotNull();

        // Check the available rollback is persisted correctly
        rm.reloadPersistedData();
        rollbackA = RollbackUtils.getAvailableRollback(TestApp.A);
        rollbackB = RollbackUtils.getAvailableRollback(TestApp.B);
        assertThat(rollbackB).hasRollbackId(rollbackA.getRollbackId());
        assertThat(rollbackA).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1),
                Rollback.from(TestApp.B2).to(TestApp.B1));

        // Rollback the app
        RollbackUtils.rollback(rollbackA.getRollbackId());
        RollbackInfo committedA = RollbackUtils.getCommittedRollback(TestApp.A);
        RollbackInfo committedB = RollbackUtils.getCommittedRollback(TestApp.B);
        assertThat(committedA).isNotNull();
        assertThat(committedB).isNotNull();

        // Check the committed rollback is persisted correctly
        rm.reloadPersistedData();
        committedA = RollbackUtils.getCommittedRollback(TestApp.A);
        committedB = RollbackUtils.getCommittedRollback(TestApp.B);
        assertThat(committedA).hasRollbackId(rollbackA.getRollbackId());
        assertThat(committedB).hasRollbackId(rollbackA.getRollbackId());
        assertThat(committedA).isNotStaged();
        assertThat(committedA).packagesContainsExactly(
                Rollback.from(TestApp.A2).to(TestApp.A1),
                Rollback.from(TestApp.B2).to(TestApp.B1));
    }

    /**
     * Tests that the MANAGE_ROLLBACKS permission is required to call
     * RollbackManager APIs.
     */
    @Test
    public void testManageRollbacksPermission() throws Exception {
        // We shouldn't be allowed to call any of the RollbackManager APIs
        // without the MANAGE_ROLLBACKS permission.
        InstallUtils.dropShellPermissionIdentity();
        RollbackManager rm = RollbackUtils.getRollbackManager();

        try {
            rm.getAvailableRollbacks();
            fail("expected SecurityException");
        } catch (SecurityException e) {
            // Expected.
        }

        try {
            rm.getRecentlyCommittedRollbacks();
            fail("expected SecurityException");
        } catch (SecurityException e) {
            // Expected.
        }

        try {
            LocalIntentSender sender = new LocalIntentSender();
            rm.commitRollback(0, Collections.emptyList(), sender.getIntentSender());
            fail("expected SecurityException");
        } catch (SecurityException e) {
            // Expected.
        }

        try {
            rm.reloadPersistedData();
            fail("expected SecurityException");
        } catch (SecurityException e) {
            // Expected.
        }

        try {
            rm.expireRollbackForPackage(TestApp.A);
            fail("expected SecurityException");
        } catch (SecurityException e) {
            // Expected.
        }
    }

    /**
     * Tests that you cannot enable rollback for a package without the
     * MANAGE_ROLLBACKS permission.
     */
    @Test
    public void testEnableRollbackPermission() throws Exception {
        InstallUtils.adoptShellPermissionIdentity(
                Manifest.permission.INSTALL_PACKAGES,
                Manifest.permission.DELETE_PACKAGES);

        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setEnableRollback().commit();

        // We expect v2 of the app was installed, but rollback has not
        // been enabled.
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        InstallUtils.adoptShellPermissionIdentity(
                Manifest.permission.MANAGE_ROLLBACKS,
                Manifest.permission.INSTALL_PACKAGES,
                Manifest.permission.DELETE_PACKAGES);
        assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNull();
    }

    /**
     * Tests that you cannot enable rollback for a non-module package when
     * holding the MANAGE_ROLLBACKS permission without TEST_MANAGE_ROLLBACKS.
     */
    @Test
    public void testNonModuleEnableRollback() throws Exception {
        InstallUtils.adoptShellPermissionIdentity(
                Manifest.permission.INSTALL_PACKAGES,
                Manifest.permission.DELETE_PACKAGES,
                Manifest.permission.MANAGE_ROLLBACKS);

        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setEnableRollback().commit();

        // We expect v2 of the app was installed, but rollback has not
        // been enabled because the test app is not a module.
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNull();
    }

    /**
     * Tests failure to enable rollback for multi-package installs.
     * If any one of the packages fail to enable rollback, we shouldn't enable
     * rollback for any package.
     */
    @Test
    public void testMultiPackageEnableFail() throws Exception {
        Install.single(TestApp.A1).commit();
        // We should fail to enable rollback here because TestApp B is not
        // already installed.
        Install.multi(TestApp.A2, TestApp.B2).setEnableRollback().commit();

        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(2);

        assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNull();
        assertThat(RollbackUtils.getAvailableRollback(TestApp.B)).isNull();
    }

    @Test
    public void testGetRollbackDataPolicy() throws Exception {
        final int rollBackDataPolicy = ROLLBACK_DATA_POLICY_WIPE;

        Install.single(TestApp.A1).commit();
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);

        // Enable rollback with rollBackDataPolicy
        final int sessionId = Install.single(TestApp.A2).setEnableRollback(
                rollBackDataPolicy).createSession();

        try {
            assertThat(InstallUtils.getPackageInstaller().getSessionInfo(
                    sessionId).getRollbackDataPolicy()).isEqualTo(rollBackDataPolicy);
        } finally {
            // Abandon the session
            InstallUtils.getPackageInstaller().abandonSession(sessionId);
        }
    }

    /**
     * Test that flags are cleared when a rollback is committed.
     */
    @Test
    public void testRollbackClearsFlags() throws Exception {
        Install.single(TestApp.A1).commit();
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
        RollbackUtils.waitForRollbackGone(
                () -> getRollbackManager().getAvailableRollbacks(), TestApp.A);

        Install.single(TestApp.A2).setEnableRollback().commit();
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
        RollbackInfo available = RollbackUtils.waitForAvailableRollback(TestApp.A);

        DeviceConfig.setProperty("configuration", "namespace_to_package_mapping",
                "testspace:" + TestApp.A, false);
        DeviceConfig.setProperty("testspace", "flagname", "hello", false);
        DeviceConfig.setProperty("testspace", "another", "12345", false);
        assertThat(DeviceConfig.getProperties("testspace").getKeyset()).hasSize(2);

        RollbackUtils.rollback(available.getRollbackId(), TestApp.A2);
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);

        assertThat(DeviceConfig.getProperties("testspace").getKeyset()).hasSize(0);
    }

    /**
     * Tests an app can be rolled back to the previous signing key.
     *
     * <p>The rollback capability in the signing lineage allows an app to be updated to an APK
     * signed with a previous signing key in the lineage; however this often defeats the purpose
     * of key rotation as a compromised key could then be used to roll an app back to the previous
     * key. To avoid requiring the rollback capability to support APK rollbacks the PackageManager
     * allows an app to be rolled back to the previous signing key if the rollback install reason
     * is set.
     */
    @Test
    public void testRollbackAfterKeyRotation() throws Exception {
        Install.single(TestApp.AOriginal1).commit();
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);

        Install.single(TestApp.ARotated2).setEnableRollback().commit();
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);

        RollbackInfo available = RollbackUtils.waitForAvailableRollback(TestApp.A);
        RollbackUtils.rollback(available.getRollbackId(), TestApp.ARotated2);
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
    }

    /**
     * Test we can't enable rollback for non-allowlisted app without
     * TEST_MANAGE_ROLLBACKS permission
     */
    @Test
    public void testNonRollbackAllowlistedApp() throws Exception {
        InstallUtils.dropShellPermissionIdentity();
        InstallUtils.adoptShellPermissionIdentity(
                Manifest.permission.INSTALL_PACKAGES,
                Manifest.permission.DELETE_PACKAGES,
                Manifest.permission.MANAGE_ROLLBACKS);

        Install.single(TestApp.A1).commit();
        assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNull();

        Install.single(TestApp.A2).setEnableRollback().commit();
        Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNull();
    }

    /**
     * Tests user data is restored according to the preset rollback data policy.
     */
    @Test
    public void testRollbackDataPolicy() throws Exception {
        Install.multi(TestApp.A1, TestApp.B1, TestApp.C1).commit();
        // Write user data version = 1
        InstallUtils.processUserData(TestApp.A);
        InstallUtils.processUserData(TestApp.B);
        InstallUtils.processUserData(TestApp.C);

        Install a2 = Install.single(TestApp.A2)
                .setEnableRollback(PackageManager.ROLLBACK_DATA_POLICY_WIPE);
        Install b2 = Install.single(TestApp.B2)
                .setEnableRollback(PackageManager.ROLLBACK_DATA_POLICY_RESTORE);
        Install c2 = Install.single(TestApp.C2)
                .setEnableRollback(PackageManager.ROLLBACK_DATA_POLICY_RETAIN);
        Install.multi(a2, b2, c2).setEnableRollback().commit();
        // Write user data version = 2
        InstallUtils.processUserData(TestApp.A);
        InstallUtils.processUserData(TestApp.B);
        InstallUtils.processUserData(TestApp.C);

        RollbackInfo info = RollbackUtils.getAvailableRollback(TestApp.A);
        RollbackUtils.rollback(info.getRollbackId());
        // Read user data version from userdata.txt
        // A's user data version is -1 for user data is wiped.
        // B's user data version is 1 for user data is restored.
        // C's user data version is 2 for user data is retained.
        assertThat(InstallUtils.getUserDataVersion(TestApp.A)).isEqualTo(-1);
        assertThat(InstallUtils.getUserDataVersion(TestApp.B)).isEqualTo(1);
        assertThat(InstallUtils.getUserDataVersion(TestApp.C)).isEqualTo(2);
    }

    /**
     * Tests user data is restored according to the rollback data policy defined in the manifest.
     */
    @Test
    public void testRollbackDataPolicy_Manifest() throws Exception {
        Install.multi(TestApp.A1, TestApp.B1, TestApp.C1).commit();
        // Write user data version = 1
        InstallUtils.processUserData(TestApp.A);
        InstallUtils.processUserData(TestApp.B);
        InstallUtils.processUserData(TestApp.C);

        Install a2 = Install.single(TestApp.ARollbackWipe2).setEnableRollback();
        Install b2 = Install.single(TestApp.BRollbackRestore2).setEnableRollback();
        Install c2 = Install.single(TestApp.CRollbackRetain2).setEnableRollback();
        Install.multi(a2, b2, c2).setEnableRollback().commit();
        // Write user data version = 2
        InstallUtils.processUserData(TestApp.A);
        InstallUtils.processUserData(TestApp.B);
        InstallUtils.processUserData(TestApp.C);

        RollbackInfo info = RollbackUtils.getAvailableRollback(TestApp.A);
        RollbackUtils.rollback(info.getRollbackId());
        // Read user data version from userdata.txt
        // A's user data version is -1 for user data is wiped.
        // B's user data version is 1 for user data is restored.
        // C's user data version is 2 for user data is retained.
        assertThat(InstallUtils.getUserDataVersion(TestApp.A)).isEqualTo(-1);
        assertThat(InstallUtils.getUserDataVersion(TestApp.B)).isEqualTo(1);
        assertThat(InstallUtils.getUserDataVersion(TestApp.C)).isEqualTo(2);
    }

    /**
     * Test explicit expiration of rollbacks.
     * Does not test the scheduling aspects of rollback expiration.
     */
    @Test
    public void testRollbackExpiration() throws Exception {
        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setEnableRollback().commit();
        RollbackUtils.waitForAvailableRollback(TestApp.A);

        // Expire the rollback.
        RollbackUtils.getRollbackManager().expireRollbackForPackage(TestApp.A);

        // The rollback should no longer be available.
        assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNull();
    }

    /**
     * Test restrictions on rollback broadcast sender.
     * A random app should not be able to send a ROLLBACK_COMMITTED broadcast.
     */
    @Test
    public void testRollbackBroadcastRestrictions() throws Exception {
        RollbackBroadcastReceiver broadcastReceiver = new RollbackBroadcastReceiver();
        Intent broadcast = new Intent(Intent.ACTION_ROLLBACK_COMMITTED);
        try {
            InstrumentationRegistry.getContext().sendBroadcast(broadcast);
            fail("Succeeded in sending restricted broadcast from app context.");
        } catch (SecurityException se) {
            // Expected behavior.
        }

        // Confirm that we really haven't received the broadcast.
        // TODO: How long to wait for the expected timeout?
        assertThat(broadcastReceiver.poll(5, TimeUnit.SECONDS)).isNull();

        // TODO: Do we need to do this? Do we need to ensure this is always
        // called, even when the test fails?
        broadcastReceiver.unregister();
    }

    /**
     * Test rollback of apks involving splits.
     */
    @Test
    public void testRollbackWithSplits() throws Exception {
        Install.single(TestApp.ASplit1).commit();
        Install.single(TestApp.ASplit2).setEnableRollback().commit();
        RollbackInfo rollback = RollbackUtils.waitForAvailableRollback(TestApp.A);
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);

        RollbackUtils.rollback(rollback.getRollbackId());
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
    }

    /**
     * Test bad update automatic rollback.
     */
    @Test
    public void testBadUpdateRollback() throws Exception {
        // Prep installation of the test apps.
        Install.single(TestApp.A1).commit();
        Install.single(TestApp.ACrashing2).setEnableRollback().commit();
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);

        Install.single(TestApp.B1).commit();
        Install.single(TestApp.B2).setEnableRollback().commit();
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(2);

        // Both test apps should now be available for rollback, and the
        // targetPackage returned for rollback should be correct.
        RollbackInfo rollbackA = RollbackUtils.waitForAvailableRollback(TestApp.A);
        assertThat(rollbackA).packagesContainsExactly(Rollback.from(TestApp.A2).to(TestApp.A1));
        RollbackInfo rollbackB = RollbackUtils.waitForAvailableRollback(TestApp.B);
        assertThat(rollbackB).packagesContainsExactly(Rollback.from(TestApp.B2).to(TestApp.B1));

        // Register rollback committed receiver
        RollbackBroadcastReceiver rollbackReceiver = new RollbackBroadcastReceiver();

        // Crash TestApp.A PackageWatchdog#TRIGGER_FAILURE_COUNT times to trigger rollback
        RollbackUtils.sendCrashBroadcast(TestApp.A, 5);

        // Verify we received a broadcast for the rollback.
        assertThat(rollbackReceiver.poll(1, TimeUnit.MINUTES)).isNotNull();

        // TestApp.A is automatically rolled back by the RollbackPackageHealthObserver
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(1);
        // Instrumented app is still the package installer
        String installer = InstrumentationRegistry.getContext().getPackageManager()
                .getInstallerPackageName(TestApp.A);
        assertThat(installer).isEqualTo(getClass().getPackageName());
        // TestApp.B is untouched
        assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(2);
    }

    /**
     * Tests we fail to enable rollbacks if enable-rollback times out.
     */
    @Test
    public void testEnableRollbackTimeoutFailsRollback() throws Exception {
        //setting the timeout to a very short amount that will definitely be triggered
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ROLLBACK,
                PROPERTY_ENABLE_ROLLBACK_TIMEOUT_MILLIS,
                Long.toString(0), false /* makeDefault*/);

        try {
            Install.single(TestApp.A1).commit();
            RollbackUtils.waitForUnavailableRollback(TestApp.A);

            // Block the RollbackManager to make extra sure it will not be
            // able to enable the rollback in time.
            RollbackManager rm = RollbackUtils.getRollbackManager();
            rm.blockRollbackManager(TimeUnit.SECONDS.toMillis(1));
            Install.single(TestApp.A2).setEnableRollback().commit();
            assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);

            // Give plenty of time for RollbackManager to unblock and attempt
            // to make the rollback available before asserting that the
            // rollback was not made available.
            Thread.sleep(TimeUnit.SECONDS.toMillis(2));
            assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNull();
        } finally {
            //setting the timeout back to default
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ROLLBACK,
                    PROPERTY_ENABLE_ROLLBACK_TIMEOUT_MILLIS,
                    null, false /* makeDefault*/);
        }
    }

    /**
     * Tests we fail to enable rollbacks if rollbackLifetime times out.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ROLLBACK_LIFETIME)
    public void testEnableRollbackLifetimeTimeoutFailsRollback() throws Exception {

        Install.single(TestApp.A1).commit();
        RollbackUtils.waitForUnavailableRollback(TestApp.A);

        RollbackManager rm = RollbackUtils.getRollbackManager();
        rm.blockRollbackManager(TimeUnit.SECONDS.toMillis(1));
        Install.single(TestApp.A2).setEnableRollback()
                .setRollbackLifetimeMillis(100).commit();
        assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);

        // Give plenty of time for RollbackManager to unblock and attempt
        // to make the rollback available before asserting that the
        // rollback was not made available.
        Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNull();
    }

    /**
     * Tests we fail to enable rollbacks if enable-rollback times out for any child session.
     */
    @Test
    public void testEnableRollbackTimeoutFailsRollback_MultiPackage() throws Exception {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ROLLBACK,
                PROPERTY_ENABLE_ROLLBACK_TIMEOUT_MILLIS,
                Long.toString(5000), false /* makeDefault*/);

        try {
            Install.multi(TestApp.A1, TestApp.B1).commit();
            RollbackUtils.waitForUnavailableRollback(TestApp.A);

            // Block the 2nd session for 10s so it will not be able to enable the rollback in time.
            RollbackManager rm = RollbackUtils.getRollbackManager();
            rm.blockRollbackManager(TimeUnit.SECONDS.toMillis(0));
            rm.blockRollbackManager(TimeUnit.SECONDS.toMillis(10));
            Install.multi(TestApp.A2, TestApp.B2).setEnableRollback().commit();
            assertThat(InstallUtils.getInstalledVersion(TestApp.A)).isEqualTo(2);
            assertThat(InstallUtils.getInstalledVersion(TestApp.B)).isEqualTo(2);

            // Give plenty of time for RollbackManager to unblock and attempt
            // to make the rollback available before asserting that the
            // rollback was not made available.
            Thread.sleep(TimeUnit.SECONDS.toMillis(2));
            assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNull();
            assertThat(RollbackUtils.getAvailableRollback(TestApp.B)).isNull();
        } finally {
            //setting the timeout back to default
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ROLLBACK,
                    PROPERTY_ENABLE_ROLLBACK_TIMEOUT_MILLIS,
                    null, false /* makeDefault*/);
        }
    }

    /**
     * Test the scheduling aspect of rollback expiration.
     */
    @Test
    public void testRollbackExpiresAfterLifetime() throws Exception {
        long expirationTime = TimeUnit.SECONDS.toMillis(30);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ROLLBACK_BOOT,
                RollbackManager.PROPERTY_ROLLBACK_LIFETIME_MILLIS,
                Long.toString(expirationTime), false /* makeDefault*/);

        try {
            Install.single(TestApp.A1).commit();
            Install.single(TestApp.A2).setEnableRollback().commit();
            RollbackUtils.waitForAvailableRollback(TestApp.A);

            // Give it a little more time, but still not long enough to expire
            Thread.sleep(expirationTime / 2);
            assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNotNull();

            // Check that the data has expired after the expiration time (with a buffer of 1 second)
            Thread.sleep(expirationTime / 2 + TimeUnit.SECONDS.toMillis(1));
            assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNull();
        } finally {
            // Restore default config values
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ROLLBACK_BOOT,
                    RollbackManager.PROPERTY_ROLLBACK_LIFETIME_MILLIS,
                    null, false /* makeDefault*/);
        }
    }

    /**
     * Test the scheduling aspect of rollback expiration via setRollbackLifetimeMillis.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ROLLBACK_LIFETIME)
    public void testRollbackExpiresAfterRollbackLifetime() throws Exception {
        long expirationTimeA = TimeUnit.SECONDS.toMillis(4);
        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setEnableRollback()
                .setRollbackLifetimeMillis(expirationTimeA).commit();
        RollbackUtils.waitForAvailableRollback(TestApp.A);

        long expirationTimeB = TimeUnit.SECONDS.toMillis(40);
        Install.single(TestApp.B1).commit();
        Install.single(TestApp.B2).setEnableRollback()
                .setRollbackLifetimeMillis(expirationTimeB).commit();
        RollbackUtils.waitForAvailableRollback(TestApp.B);

        // Give it a little more time, but still not long enough to expire
        Thread.sleep(expirationTimeA / 2);
        assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNotNull();
        assertThat(RollbackUtils.getAvailableRollback(TestApp.B)).isNotNull();

        // Check that the data has expired after the expiration time (with a buffer of 1 second)
        Thread.sleep(expirationTimeA / 2 + TimeUnit.SECONDS.toMillis(1));
        assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNull();
        assertThat(RollbackUtils.getAvailableRollback(TestApp.B)).isNotNull();
    }

    /**
     * Test that available rollbacks should expire correctly when the property
     * {@link RollbackManager#PROPERTY_ROLLBACK_LIFETIME_MILLIS} is changed
     */
    @Test
    public void testRollbackExpiresWhenLifetimeChanges() throws Exception {
        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setEnableRollback().commit();
        RollbackUtils.waitForAvailableRollback(TestApp.A);

        // Change the lifetime to 0 which should expire rollbacks immediately
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ROLLBACK_BOOT,
                RollbackManager.PROPERTY_ROLLBACK_LIFETIME_MILLIS,
                Long.toString(0), false /* makeDefault*/);

        try {
            RollbackUtils.waitForUnavailableRollback(TestApp.A);
        } finally {
            // Restore default config values
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ROLLBACK_BOOT,
                    RollbackManager.PROPERTY_ROLLBACK_LIFETIME_MILLIS,
                    null, false /* makeDefault*/);
        }
    }

    /**
     * Test that available rollbacks should expire correctly when set via
     * setRollbackLifetimeMillis and not affected by device config value
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ROLLBACK_LIFETIME)
    public void testRollbackExpiresWhenLifetimeStays() throws Exception {
        long expirationTime = TimeUnit.SECONDS.toMillis(5);
        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setEnableRollback()
                .setRollbackLifetimeMillis(expirationTime).commit();
        RollbackUtils.waitForAvailableRollback(TestApp.A);

        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ROLLBACK_BOOT,
                RollbackManager.PROPERTY_ROLLBACK_LIFETIME_MILLIS,
                Long.toString(0), false /* makeDefault*/);

        try {
            Thread.sleep(expirationTime / 2);
            assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNotNull();
            // The rollback now should expire
            Thread.sleep(expirationTime / 2 + TimeUnit.SECONDS.toMillis(1));
            assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNull();
        } finally {
            // Restore default config values
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ROLLBACK_BOOT,
                    RollbackManager.PROPERTY_ROLLBACK_LIFETIME_MILLIS,
                    null, false /* makeDefault*/);
        }
    }

    /**
     * Test that changing time on device does not affect the duration of time that we keep
     * rollback available
     */
    @Test
    public void testTimeChangeDoesNotAffectLifetime() throws Exception {
        long expirationTime = TimeUnit.SECONDS.toMillis(30);
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ROLLBACK_BOOT,
                RollbackManager.PROPERTY_ROLLBACK_LIFETIME_MILLIS,
                Long.toString(expirationTime), false /* makeDefault*/);

        try {
            Install.single(TestApp.A1).commit();
            Install.single(TestApp.A2).setEnableRollback().commit();
            RollbackUtils.waitForAvailableRollback(TestApp.A);
            RollbackUtils.forwardTimeBy(expirationTime);

            try {
                // The rollback should be still available after forwarding time
                assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNotNull();
                // The rollback shouldn't expire when half of the expiration time elapses
                Thread.sleep(expirationTime / 2);
                assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNotNull();
                // The rollback now should expire
                Thread.sleep(expirationTime / 2 + TimeUnit.SECONDS.toMillis(1));
                assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNull();
            } finally {
                RollbackUtils.forwardTimeBy(-expirationTime);
            }
        } finally {
            // Restore default config values
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_ROLLBACK_BOOT,
                    RollbackManager.PROPERTY_ROLLBACK_LIFETIME_MILLIS,
                    null, false /* makeDefault*/);
        }
    }

    /**
     * Test that changing time on device does not affect the duration of time that we keep
     * rollback available which is set by setRollbackLifetimeMillis
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ROLLBACK_LIFETIME)
    public void testTimeChangeDoesNotAffectLifetimeMillis() throws Exception {
        long expirationTime = TimeUnit.SECONDS.toMillis(10);

        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setEnableRollback()
                .setRollbackLifetimeMillis(expirationTime).commit();
        RollbackUtils.waitForAvailableRollback(TestApp.A);
        RollbackUtils.forwardTimeBy(expirationTime);

        try {
            // The rollback should be still available after forwarding time
            assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNotNull();
            // The rollback shouldn't expire when half of the expiration time elapses
            Thread.sleep(expirationTime / 2);
            assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNotNull();
            // The rollback now should expire
            Thread.sleep(expirationTime / 2 + TimeUnit.SECONDS.toMillis(1));
            assertThat(RollbackUtils.getAvailableRollback(TestApp.A)).isNull();
        } finally {
            RollbackUtils.forwardTimeBy(-expirationTime);
        }

    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RECOVERABILITY_DETECTION)
    public void testImpactLevelInRollback() throws Exception {
        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setEnableRollback().setRollbackImpactLevel(1).commit();
        RollbackUtils.waitForAvailableRollback(TestApp.A);
        RollbackInfo rollback = RollbackUtils.getAvailableRollback(TestApp.A);

        assertThat(rollback).isNotNull();
        assertThat(rollback.getRollbackImpactLevel()).isEqualTo(1);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RECOVERABILITY_DETECTION)
    public void testImpactLevelInRollbackDefault() throws Exception {
        Install.single(TestApp.A1).commit();
        Install.single(TestApp.A2).setEnableRollback().commit();
        RollbackUtils.waitForAvailableRollback(TestApp.A);
        RollbackInfo rollback = RollbackUtils.getAvailableRollback(TestApp.A);

        assertThat(rollback).isNotNull();
        assertThat(rollback.getRollbackImpactLevel()).isEqualTo(0);
    }
}
