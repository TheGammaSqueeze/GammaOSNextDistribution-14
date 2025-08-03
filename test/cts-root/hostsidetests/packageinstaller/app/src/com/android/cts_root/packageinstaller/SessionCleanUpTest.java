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

package com.android.cts_root.packageinstaller;

import static com.android.cts.install.lib.InstallUtils.getInstalledVersion;
import static com.android.cts.install.lib.InstallUtils.openPackageInstallerSession;
import static com.android.cts.install.lib.PackageInstallerSessionInfoSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.InstallUtils;
import com.android.cts.install.lib.LocalIntentSender;
import com.android.cts.install.lib.TestApp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.function.Predicate;
import java.util.function.Supplier;

public class SessionCleanUpTest {
    private static final int INSTALL_FORCE_PERMISSION_PROMPT = 0x00000400;
    /**
     * Time between repeated checks in {@link #retry}.
     */
    private static final long RETRY_CHECK_INTERVAL_MILLIS = 500;
    /**
     * Maximum number of checks in {@link #retry} before a timeout occurs.
     */
    private static final long RETRY_MAX_INTERVALS = 20;

    @Before
    public void setUp() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.CLEAR_APP_CACHE,
                        Manifest.permission.INSTALL_PACKAGES,
                        Manifest.permission.DELETE_PACKAGES);
    }

    @After
    public void tearDown() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
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

    private void assertSessionNotExists(int sessionId) throws Exception {
        // The session is cleaned up asynchronously.
        // Retry until the session no longer exists.
        retry(() -> InstallUtils.getPackageInstaller().getSessionInfo(sessionId),
                info -> info == null,
                "Session " + sessionId + " not cleaned up");
    }

    @Test
    public void testSessionCleanUp_Single_Success() throws Exception {
        int sessionId = Install.single(TestApp.A1).commit();
        assertThat(getInstalledVersion(TestApp.A)).isEqualTo(1);
        assertSessionNotExists(sessionId);
    }

    @Test
    public void testSessionCleanUp_Multi_Success() throws Exception {
        int parentId = Install.multi(TestApp.A1, TestApp.B1).createSession();
        try (PackageInstaller.Session parent = openPackageInstallerSession(parentId)) {
            int[] childIds = parent.getChildSessionIds();
            LocalIntentSender sender = new LocalIntentSender();
            parent.commit(sender.getIntentSender());
            InstallUtils.assertStatusSuccess(sender.getResult());
            assertThat(getInstalledVersion(TestApp.A)).isEqualTo(1);
            assertThat(getInstalledVersion(TestApp.B)).isEqualTo(1);
            assertSessionNotExists(parentId);
            for (int childId : childIds) {
                assertSessionNotExists(childId);
            }
        }
    }

    @Test
    public void testSessionCleanUp_Single_VerificationFailed() throws Exception {
        Install.single(TestApp.A2).commit();
        int sessionId = Install.single(TestApp.A1).createSession();
        try (PackageInstaller.Session session = openPackageInstallerSession(sessionId)) {
            LocalIntentSender sender = new LocalIntentSender();
            session.commit(sender.getIntentSender());
            InstallUtils.assertStatusFailure(sender.getResult());
            assertSessionNotExists(sessionId);
        }
    }

    @Test
    public void testSessionCleanUp_Multi_VerificationFailed() throws Exception {
        Install.single(TestApp.A2).commit();
        int parentId = Install.multi(TestApp.A1, TestApp.B1).createSession();
        try (PackageInstaller.Session parent = openPackageInstallerSession(parentId)) {
            int[] childIds = parent.getChildSessionIds();
            LocalIntentSender sender = new LocalIntentSender();
            parent.commit(sender.getIntentSender());
            InstallUtils.assertStatusFailure(sender.getResult());
            assertSessionNotExists(parentId);
            for (int childId : childIds) {
                assertSessionNotExists(childId);
            }
        }
    }

    @Test
    public void testSessionCleanUp_Single_ValidationFailed() throws Exception {
        int sessionId = Install.single(TestApp.AIncompleteSplit).createSession();
        try (PackageInstaller.Session session = openPackageInstallerSession(sessionId)) {
            LocalIntentSender sender = new LocalIntentSender();
            session.commit(sender.getIntentSender());
            InstallUtils.assertStatusFailure(sender.getResult());
            assertSessionNotExists(sessionId);
        }
    }

    @Test
    public void testSessionCleanUp_Multi_ValidationFailed() throws Exception {
        int parentId = Install.multi(TestApp.AIncompleteSplit, TestApp.B1).createSession();
        try (PackageInstaller.Session parent = openPackageInstallerSession(parentId)) {
            int[] childIds = parent.getChildSessionIds();
            LocalIntentSender sender = new LocalIntentSender();
            parent.commit(sender.getIntentSender());
            InstallUtils.assertStatusFailure(sender.getResult());
            assertSessionNotExists(parentId);
            for (int childId : childIds) {
                assertSessionNotExists(childId);
            }
        }
    }

    @Test
    public void testSessionCleanUp_Single_NoPermission() throws Exception {
        int sessionId = Install.single(TestApp.A1)
                .addInstallFlags(INSTALL_FORCE_PERMISSION_PROMPT).createSession();
        try (PackageInstaller.Session session = openPackageInstallerSession(sessionId)) {
            LocalIntentSender sender = new LocalIntentSender();
            session.commit(sender.getIntentSender());
            Intent intent = sender.getResult();
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE);
            assertThat(status).isEqualTo(PackageInstaller.STATUS_PENDING_USER_ACTION);
            int idNeedsUserAction = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
            InstallUtils.getPackageInstaller().setPermissionsResult(idNeedsUserAction, false);
            InstallUtils.assertStatusFailure(sender.getResult());
            assertSessionNotExists(sessionId);
        }
    }

    @Test
    public void testSessionCleanUp_Multi_NoPermission() throws Exception {
        int parentId = Install.multi(TestApp.A1, TestApp.B1)
                .addInstallFlags(INSTALL_FORCE_PERMISSION_PROMPT).createSession();
        try (PackageInstaller.Session parent = openPackageInstallerSession(parentId)) {
            int[] childIds = parent.getChildSessionIds();
            LocalIntentSender sender = new LocalIntentSender();
            parent.commit(sender.getIntentSender());
            Intent intent = sender.getResult();
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE);
            assertThat(status).isEqualTo(PackageInstaller.STATUS_PENDING_USER_ACTION);
            int idNeedsUserAction = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
            InstallUtils.getPackageInstaller().setPermissionsResult(idNeedsUserAction, false);
            InstallUtils.assertStatusFailure(sender.getResult());
            assertSessionNotExists(parentId);
            for (int childId : childIds) {
                assertSessionNotExists(childId);
            }
        }
    }

    @Test
    public void testSessionCleanUp_Single_Expire_Install() throws Exception {
        int sessionId = Install.single(TestApp.A1).setStaged().commit();

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        SharedPreferences prefs = context.getSharedPreferences("test", 0);
        prefs.edit().putInt("sessionId", sessionId).commit();
    }

    @Test
    public void testSessionCleanUp_Single_Expire_VerifyInstall() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        SharedPreferences prefs = context.getSharedPreferences("test", 0);
        int sessionId = prefs.getInt("sessionId", -1);
        assertThat(InstallUtils.getStagedSessionInfo(sessionId)).isStagedSessionApplied();
    }

    @Test
    public void testSessionCleanUp_Single_Expire_CleanUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        SharedPreferences prefs = context.getSharedPreferences("test", 0);
        int sessionId = prefs.getInt("sessionId", -1);
        assertSessionNotExists(sessionId);
    }

    @Test
    public void testSessionCleanUp_Multi_Expire_Install() throws Exception {
        int parentId = Install.multi(TestApp.A1, TestApp.B1).setStaged().commit();
        int[] childIds;
        try (PackageInstaller.Session parent = openPackageInstallerSession(parentId)) {
            childIds = parent.getChildSessionIds();
        }

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        SharedPreferences prefs = context.getSharedPreferences("test", 0);
        prefs.edit().putInt("parentId", parentId).commit();
        prefs.edit().putInt("childId1", childIds[0]).commit();
        prefs.edit().putInt("childId2", childIds[1]).commit();
    }

    @Test
    public void testSessionCleanUp_Multi_Expire_VerifyInstall() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        SharedPreferences prefs = context.getSharedPreferences("test", 0);
        int parentId = prefs.getInt("parentId", -1);
        assertThat(InstallUtils.getStagedSessionInfo(parentId)).isStagedSessionApplied();
    }

    @Test
    public void testSessionCleanUp_Multi_Expire_CleanUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        SharedPreferences prefs = context.getSharedPreferences("test", 0);
        int parentId = prefs.getInt("parentId", -1);
        int childId1 = prefs.getInt("childId1", -1);
        int childId2 = prefs.getInt("childId2", -1);
        assertSessionNotExists(parentId);
        assertSessionNotExists(childId1);
        assertSessionNotExists(childId2);
    }

    @Test
    public void testSessionCleanUp_LowStorage_Install() throws Exception {
        int parentId = Install.multi(TestApp.A1, TestApp.B1).createSession();
        int[] childIds;
        try (PackageInstaller.Session parent = openPackageInstallerSession(parentId)) {
            childIds = parent.getChildSessionIds();
        }

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        SharedPreferences prefs = context.getSharedPreferences("test", 0);
        prefs.edit().putInt("parentId", parentId).commit();
        prefs.edit().putInt("childId1", childIds[0]).commit();
        prefs.edit().putInt("childId2", childIds[1]).commit();
    }

    @Test
    public void testSessionCleanUp_LowStorage_CleanUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        // Pass Long.MAX_VALUE to ensure old sessions will be abandoned
        context.getPackageManager().freeStorage(Long.MAX_VALUE, null);
        SharedPreferences prefs = context.getSharedPreferences("test", 0);
        int parentId = prefs.getInt("parentId", -1);
        int childId1 = prefs.getInt("childId1", -1);
        int childId2 = prefs.getInt("childId2", -1);
        assertSessionNotExists(parentId);
        assertSessionNotExists(childId1);
        assertSessionNotExists(childId2);
    }
}
