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

package com.android.tests.atomicinstall;

import static com.android.cts.install.lib.InstallUtils.assertStatusSuccess;
import static com.android.cts.install.lib.InstallUtils.getInstalledVersion;
import static com.android.cts.install.lib.InstallUtils.openPackageInstallerSession;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.Manifest;
import android.content.pm.PackageInstaller;

import androidx.test.InstrumentationRegistry;

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

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Tests for multi-package (a.k.a. atomic) installs.
 */
@RunWith(JUnit4.class)
public class AtomicInstallTest {
    /**
     * Time between repeated checks in {@link #retry}.
     */
    private static final long RETRY_CHECK_INTERVAL_MILLIS = 500;
    /**
     * Maximum number of checks in {@link #retry} before a timeout occurs.
     */
    private static final long RETRY_MAX_INTERVALS = 20;

    public static final String TEST_APP_CORRUPT_FILENAME = "corrupt.apk";
    private static final TestApp CORRUPT_TESTAPP = new TestApp(
            "corrupt", "com.corrupt", 1, false, TEST_APP_CORRUPT_FILENAME);

    private void adoptShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.INSTALL_PACKAGES,
                    Manifest.permission.DELETE_PACKAGES);
    }

    @Before
    public void setup() throws Exception {
        adoptShellPermissions();

        Uninstall.packages(TestApp.A, TestApp.B, TestApp.C);
    }

    @After
    public void dropShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Cleans up sessions that are not committed during tests.
     */
    @After
    public void cleanUpSessions() {
        InstallUtils.getPackageInstaller().getMySessions().forEach(info -> {
            try {
                InstallUtils.getPackageInstaller().abandonSession(info.getSessionId());
            } catch (Exception ignore) {
            }
        });
    }

    private static <T> T retry(Supplier<T> supplier, Predicate<T> predicate, String message)
            throws InterruptedException {
        for (int i = 0; i < RETRY_MAX_INTERVALS; i++) {
            T result = supplier.get();
            if (predicate.test(result)) {
                return result;
            }
            Thread.sleep(RETRY_CHECK_INTERVAL_MILLIS);
        }
        throw new AssertionError(message);
    }

    /**
     * Tests a completed session should be cleaned up.
     */
    @Test
    public void testSessionCleanUp_Single() throws Exception {
        int sessionId = Install.single(TestApp.A1).commit();
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(1);
        // The session is cleaned up asynchronously after install completed.
        // Retry until the session no longer exists.
        retry(() -> InstallUtils.getPackageInstaller().getSessionInfo(sessionId),
                info -> info == null,
                "Session " + sessionId + " not cleaned up");
    }

    /**
     * Tests a completed session should be cleaned up.
     */
    @Test
    public void testSessionCleanUp_Multi() throws Exception {
        int sessionId = Install.multi(TestApp.A1, TestApp.B1).commit();
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(1);
        assertThat(getInstalledVersion(TestApp.B)).isEqualTo(1);
        // The session is cleaned up asynchronously after install completed.
        // Retry until the session no longer exists.
        retry(() -> InstallUtils.getPackageInstaller().getSessionInfo(sessionId),
                info -> info == null,
                "Session " + sessionId + " not cleaned up");
    }

    @Test
    public void testInstallTwoApks() throws Exception {
        Install.multi(TestApp.A1, TestApp.B1).commit();
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(1);
        assertThat(getInstalledVersion(TestApp.B)).isEqualTo(1);
    }

    /**
     * Tests a removed child shouldn't be installed.
     */
    @Test
    public void testRemoveChild() throws Exception {
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        assertThat(getInstalledVersion(TestApp.B)).isEqualTo(-1);
        assertThat(getInstalledVersion(TestApp.C)).isEqualTo(-1);

        int parentId = Install.multi(TestApp.A1).createSession();
        int childBId = Install.single(TestApp.B1).createSession();
        int childCId = Install.single(TestApp.C1).createSession();
        try (PackageInstaller.Session parent = openPackageInstallerSession(parentId)) {
            parent.addChildSessionId(childBId);
            parent.addChildSessionId(childCId);
            parent.removeChildSessionId(childBId);
            LocalIntentSender sender = new LocalIntentSender();
            parent.commit(sender.getIntentSender());
            InstallUtils.assertStatusSuccess(sender.getResult());
            assertThat(getInstalledVersion(TestApp.A)).isEqualTo(1);
            assertThat(getInstalledVersion(TestApp.B)).isEqualTo(-1);
            assertThat(getInstalledVersion(TestApp.C)).isEqualTo(1);
        }
    }

    @Test
    public void testInstallTwoApksDowngradeFail() throws Exception {
        Install.multi(TestApp.A2, TestApp.B1).commit();
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(2);
        assertThat(getInstalledVersion(TestApp.B)).isEqualTo(1);

        InstallUtils.commitExpectingFailure(AssertionError.class,
                "INSTALL_FAILED_VERSION_DOWNGRADE", Install.multi(TestApp.A1, TestApp.B1));
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(2);
        assertThat(getInstalledVersion(TestApp.B)).isEqualTo(1);
    }

    @Test
    public void testFailInconsistentMultiPackageCommit() throws Exception {
        // Test inconsistency in staged settings
        Install parentStaged = Install.multi(Install.single(TestApp.A1)).setStaged();
        Install childStaged = Install.multi(Install.single(TestApp.A1).setStaged());

        assertInconsistentStagedSettings(parentStaged);
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        assertInconsistentStagedSettings(childStaged);
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);

        // Test inconsistency in rollback settings
        Install parentEnabledRollback = Install.multi(Install.single(TestApp.A1))
                .setEnableRollback();
        Install childEnabledRollback = Install.multi(
                Install.single(TestApp.A1).setEnableRollback());

        assertInconsistentRollbackSettings(parentEnabledRollback);
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        assertInconsistentRollbackSettings(childEnabledRollback);
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
    }

    @Test
    public void testChildFailurePropagated() throws Exception {
        // Create a child session that "inherits" from a non-existent package. This
        // causes the session commit to fail with a PackageManagerException.
        Install childInstall = Install.single(TestApp.A1).setSessionMode(
                PackageInstaller.SessionParams.MODE_INHERIT_EXISTING);
        Install parentInstall = Install.multi(childInstall);

        InstallUtils.commitExpectingFailure(AssertionError.class, "Missing existing base package",
                parentInstall);

        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
    }

    @Test
    public void testEarlyFailureFailsAll() throws Exception {
        InstallUtils.commitExpectingFailure(AssertionError.class, "Failed to parse",
                Install.multi(TestApp.A1, TestApp.B1, CORRUPT_TESTAPP));
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(-1);
        assertThat(getInstalledVersion(TestApp.B)).isEqualTo(-1);
    }

    @Test
    public void testInvalidStateScenario_MultiSessionCantBeApex() throws Exception {
        try {
            SystemUtil.runShellCommandForNoOutput("pm bypass-staged-installer-check true");
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setMultiPackage();
            params.setInstallAsApex();
            params.setStaged();
            try {
                InstallUtils.getPackageInstaller().createSession(params);
                fail("Should not be able to create a multi-session set as APEX!");
            } catch (Exception ignore) {
            }
        } finally {
            SystemUtil.runShellCommandForNoOutput("pm bypass-staged-installer-check false");
        }
    }

    /**
     * Tests a single-session can't have child.
     */
    @Test
    public void testInvalidStateScenario_AddChildToSingleSessionShouldFail() throws Exception {
        int parentId = Install.single(TestApp.A1).createSession();
        int childId = Install.single(TestApp.B1).createSession();
        try (PackageInstaller.Session parent = openPackageInstallerSession(parentId)) {
            try {
                parent.addChildSessionId(childId);
                fail("Should not be able to add a child session to a single-session!");
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Tests a multi-session can't be a child.
     */
    @Test
    public void testInvalidStateScenario_MultiSessionAddedAsChildShouldFail() throws Exception {
        int parentId = Install.multi(TestApp.A1).createSession();
        int childId = Install.multi(TestApp.B1).createSession();
        try (PackageInstaller.Session parent = openPackageInstallerSession(parentId)) {
            try {
                parent.addChildSessionId(childId);
                fail("Should not be able to add a multi-session as a child!");
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Tests a committed session can't add child.
     */
    @Test
    public void testInvalidStateScenario_AddChildToCommittedSessionShouldFail() throws Exception {
        int parentId = Install.multi(TestApp.A1).createSession();
        int childId = Install.single(TestApp.B1).createSession();
        try (PackageInstaller.Session parent = openPackageInstallerSession(parentId)) {
            LocalIntentSender sender = new LocalIntentSender();
            parent.commit(sender.getIntentSender());
            try {
                parent.addChildSessionId(childId);
                fail("Should not be able to add child to a committed session");
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Tests a committed session can't remove child.
     */
    @Test
    public void testInvalidStateScenario_RemoveChildFromCommittedSessionShouldFail()
            throws Exception {
        int parentId = Install.multi(TestApp.A1).createSession();
        int childId = Install.single(TestApp.B1).createSession();
        try (PackageInstaller.Session parent = openPackageInstallerSession(parentId)) {
            parent.addChildSessionId(childId);
            LocalIntentSender sender = new LocalIntentSender();
            parent.commit(sender.getIntentSender());
            try {
                parent.removeChildSessionId(childId);
                fail("Should not be able to remove child from a committed session");
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Tests removing a child that is not its own should do nothing.
     */
    @Test
    public void testInvalidStateScenario_RemoveWrongChildShouldDoNothing() throws Exception {
        int parent1Id = Install.multi(TestApp.A1).createSession();
        int parent2Id = Install.multi(TestApp.C1).createSession();
        int childId = Install.single(TestApp.B1).createSession();
        try (PackageInstaller.Session parent1 = openPackageInstallerSession(parent1Id);
             PackageInstaller.Session parent2 = openPackageInstallerSession(parent2Id);) {
            parent1.addChildSessionId(childId);
            // Should do nothing since the child doesn't belong to parent2
            parent2.removeChildSessionId(childId);
            int currentParentId =
                    InstallUtils.getPackageInstaller().getSessionInfo(childId).getParentSessionId();
            // Check this child still belongs to parent1
            assertThat(currentParentId).isEqualTo(parent1Id);
            assertThat(parent1.getChildSessionIds()).asList().contains(childId);
            assertThat(parent2.getChildSessionIds()).asList().doesNotContain(childId);
        }
    }

    @Test
    public void testInvalidStateScenarios() throws Exception {
        int parentSessionId = Install.multi(TestApp.A1, TestApp.B1).createSession();
        try (PackageInstaller.Session parentSession =
                     openPackageInstallerSession(parentSessionId)) {
            for (int childSessionId : parentSession.getChildSessionIds()) {
                try (PackageInstaller.Session childSession =
                             openPackageInstallerSession(childSessionId)) {
                    try {
                        LocalIntentSender sender = new LocalIntentSender();
                        childSession.commit(sender.getIntentSender());
                        fail("Should not be able to commit a child session!");
                    } catch (IllegalStateException e) {
                        // ignore
                    }
                    try {
                        childSession.abandon();
                        fail("Should not be able to abandon a child session!");
                    } catch (IllegalStateException e) {
                        // ignore
                    }
                }
            }
            int toAbandonSessionId = Install.single(TestApp.A1).createSession();
            try (PackageInstaller.Session toAbandonSession =
                         openPackageInstallerSession(toAbandonSessionId)) {
                toAbandonSession.abandon();
                try {
                    parentSession.addChildSessionId(toAbandonSessionId);
                    fail("Should not be able to add abandoned child session!");
                } catch (RuntimeException e) {
                    // ignore
                }
            }

            LocalIntentSender sender = new LocalIntentSender();
            parentSession.commit(sender.getIntentSender());
            assertStatusSuccess(sender.getResult());
        }
    }

    private static void assertInconsistentStagedSettings(Install install) {
        assertInconsistentSettings("inconsistent staged settings", install);
    }

    private static void assertInconsistentRollbackSettings(Install install) {
        assertInconsistentSettings("inconsistent rollback settings", install);
    }

    private static void assertInconsistentSettings(String failMessage, Install install) {
        InstallUtils.commitExpectingFailure(IllegalStateException.class, failMessage, install);
    }
}
