/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.appenumeration.cts;

import static android.appenumeration.cts.Constants.ACTION_AWAIT_LAUNCHER_APPS_CALLBACK;
import static android.appenumeration.cts.Constants.ACTION_AWAIT_LAUNCHER_APPS_SESSION_CALLBACK;
import static android.appenumeration.cts.Constants.ACTION_GET_ALL_PACKAGE_INSTALLER_SESSIONS;
import static android.appenumeration.cts.Constants.ACTION_LAUNCHER_APPS_GET_SUSPENDED_PACKAGE_LAUNCHER_EXTRAS;
import static android.appenumeration.cts.Constants.ACTION_LAUNCHER_APPS_IS_ACTIVITY_ENABLED;
import static android.appenumeration.cts.Constants.ACTION_LAUNCHER_APPS_SHOULD_HIDE_FROM_SUGGESTIONS;
import static android.appenumeration.cts.Constants.ACTIVITY_CLASS_DUMMY_ACTIVITY;
import static android.appenumeration.cts.Constants.CALLBACK_EVENT_INVALID;
import static android.appenumeration.cts.Constants.CALLBACK_EVENT_PACKAGES_SUSPENDED;
import static android.appenumeration.cts.Constants.CALLBACK_EVENT_PACKAGES_UNSUSPENDED;
import static android.appenumeration.cts.Constants.CALLBACK_EVENT_PACKAGE_ADDED;
import static android.appenumeration.cts.Constants.CALLBACK_EVENT_PACKAGE_CHANGED;
import static android.appenumeration.cts.Constants.CALLBACK_EVENT_PACKAGE_REMOVED;
import static android.appenumeration.cts.Constants.EXTRA_FLAGS;
import static android.appenumeration.cts.Constants.EXTRA_ID;
import static android.appenumeration.cts.Constants.QUERIES_ACTIVITY_ACTION;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_PERM;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_Q;
import static android.appenumeration.cts.Constants.QUERIES_PACKAGE;
import static android.appenumeration.cts.Constants.TARGET_FILTERS;
import static android.appenumeration.cts.Constants.TARGET_FILTERS_APK;
import static android.appenumeration.cts.Constants.TARGET_NO_API;
import static android.appenumeration.cts.Constants.TARGET_STUB;
import static android.appenumeration.cts.Constants.TARGET_STUB_APK;
import static android.appenumeration.cts.Utils.Result;
import static android.appenumeration.cts.Utils.adoptShellPermissions;
import static android.appenumeration.cts.Utils.cleanUpMySessions;
import static android.appenumeration.cts.Utils.commitSession;
import static android.appenumeration.cts.Utils.dropShellPermissions;
import static android.appenumeration.cts.Utils.ensurePackageIsInstalled;
import static android.appenumeration.cts.Utils.ensurePackageIsNotInstalled;
import static android.appenumeration.cts.Utils.installPackage;
import static android.appenumeration.cts.Utils.suspendPackages;
import static android.appenumeration.cts.Utils.suspendPackagesForUser;
import static android.appenumeration.cts.Utils.uninstallPackage;
import static android.content.Intent.EXTRA_PACKAGES;
import static android.os.Process.INVALID_UID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.TestApp;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class LauncherAppsEnumerationTests extends AppEnumerationTestsBase {

    @LargeTest
    @Test
    public void callback_added_notVisibleNotReceives() throws Exception {
        ensurePackageIsNotInstalled(TARGET_STUB);
        final Result result = sendCommandAndWaitForLauncherAppsCallback(QUERIES_NOTHING,
                CALLBACK_EVENT_PACKAGE_ADDED, new String[]{TARGET_STUB});

        installPackage(TARGET_STUB_APK);
        final Bundle response = result.await();

        assertThat(response.getInt(EXTRA_FLAGS), equalTo(CALLBACK_EVENT_INVALID));
        assertThat(response.getStringArray(EXTRA_PACKAGES), emptyArray());
    }

    @Test
    public void callback_added_visibleReceives() throws Exception {
        ensurePackageIsNotInstalled(TARGET_STUB);
        final Result result = sendCommandAndWaitForLauncherAppsCallback(QUERIES_NOTHING_PERM,
                CALLBACK_EVENT_PACKAGE_ADDED, new String[]{TARGET_STUB});

        installPackage(TARGET_STUB_APK);
        final Bundle response = result.await();

        assertThat(response.getInt(EXTRA_FLAGS), equalTo(CALLBACK_EVENT_PACKAGE_ADDED));
        assertThat(response.getStringArray(EXTRA_PACKAGES),
                arrayContainingInAnyOrder(new String[]{TARGET_STUB}));
    }

    @LargeTest
    @Test
    public void callback_removed_notVisibleNotReceives() throws Exception {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
        final Result result = sendCommandAndWaitForLauncherAppsCallback(QUERIES_NOTHING,
                CALLBACK_EVENT_PACKAGE_REMOVED, new String[]{TARGET_STUB});

        uninstallPackage(TARGET_STUB);
        final Bundle response = result.await();

        assertThat(response.getInt(EXTRA_FLAGS), equalTo(CALLBACK_EVENT_INVALID));
        assertThat(response.getStringArray(EXTRA_PACKAGES), emptyArray());
    }

    @Test
    public void callback_removed_visibleReceives() throws Exception {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
        final Result result = sendCommandAndWaitForLauncherAppsCallback(QUERIES_NOTHING_PERM,
                CALLBACK_EVENT_PACKAGE_REMOVED, new String[]{TARGET_STUB});

        uninstallPackage(TARGET_STUB);
        final Bundle response = result.await();

        assertThat(response.getInt(EXTRA_FLAGS), equalTo(CALLBACK_EVENT_PACKAGE_REMOVED));
        assertThat(response.getStringArray(EXTRA_PACKAGES),
                arrayContainingInAnyOrder(new String[]{TARGET_STUB}));
    }

    @LargeTest
    @Test
    public void callback_changed_notVisibleNotReceives() throws Exception {
        final Result result = sendCommandAndWaitForLauncherAppsCallback(QUERIES_NOTHING,
                CALLBACK_EVENT_PACKAGE_CHANGED, new String[]{TARGET_FILTERS});

        installPackage(TARGET_FILTERS_APK);
        final Bundle response = result.await();

        assertThat(response.getInt(EXTRA_FLAGS), equalTo(CALLBACK_EVENT_INVALID));
        assertThat(response.getStringArray(EXTRA_PACKAGES), emptyArray());
    }

    @Test
    public void callback_changed_visibleReceives() throws Exception {
        final Result result = sendCommandAndWaitForLauncherAppsCallback(QUERIES_NOTHING_PERM,
                CALLBACK_EVENT_PACKAGE_CHANGED, new String[]{TARGET_FILTERS});

        installPackage(TARGET_FILTERS_APK);
        final Bundle response = result.await();

        assertThat(response.getInt(EXTRA_FLAGS), equalTo(CALLBACK_EVENT_PACKAGE_CHANGED));
        assertThat(response.getStringArray(EXTRA_PACKAGES),
                arrayContainingInAnyOrder(new String[]{TARGET_FILTERS}));
    }

    @LargeTest
    @Test
    public void callback_suspended_notVisibleNotReceives() throws Exception {
        final Result result = sendCommandAndWaitForLauncherAppsCallback(QUERIES_NOTHING,
                CALLBACK_EVENT_PACKAGES_SUSPENDED, new String[]{TARGET_FILTERS});

        try {
            suspendPackages(true /* suspend */, Arrays.asList(TARGET_NO_API, TARGET_FILTERS));
            final Bundle response = result.await();

            assertThat(response.getInt(EXTRA_FLAGS), equalTo(CALLBACK_EVENT_INVALID));
            assertThat(response.getStringArray(EXTRA_PACKAGES), emptyArray());
        } finally {
            suspendPackages(false /* suspend */, Arrays.asList(TARGET_NO_API, TARGET_FILTERS));
        }
    }

    @Test
    public void callback_suspended_visibleReceives() throws Exception {
        final Result result = sendCommandAndWaitForLauncherAppsCallback(QUERIES_ACTIVITY_ACTION,
                CALLBACK_EVENT_PACKAGES_SUSPENDED, new String[]{TARGET_FILTERS});

        try {
            suspendPackages(true /* suspend */, Arrays.asList(TARGET_NO_API, TARGET_FILTERS));
            final Bundle response = result.await();

            assertThat(response.getInt(EXTRA_FLAGS), equalTo(CALLBACK_EVENT_PACKAGES_SUSPENDED));
            assertThat(response.getStringArray(EXTRA_PACKAGES),
                    arrayContainingInAnyOrder(new String[]{TARGET_FILTERS}));
        } finally {
            suspendPackages(false /* suspend */, Arrays.asList(TARGET_NO_API, TARGET_FILTERS));
        }
    }

    @LargeTest
    @Test
    public void callback_unsuspended_notVisibleNotReceives() throws Exception {
        final Result result = sendCommandAndWaitForLauncherAppsCallback(QUERIES_NOTHING,
                CALLBACK_EVENT_PACKAGES_UNSUSPENDED, new String[]{TARGET_FILTERS});

        suspendPackages(false /* suspend */, Arrays.asList(TARGET_NO_API, TARGET_FILTERS));
        final Bundle response = result.await();

        assertThat(response.getInt(EXTRA_FLAGS), equalTo(CALLBACK_EVENT_INVALID));
        assertThat(response.getStringArray(EXTRA_PACKAGES), emptyArray());
    }

    @Test
    public void callback_unsuspended_visibleReceives() throws Exception {
        suspendPackages(true /* suspend */, Arrays.asList(TARGET_NO_API, TARGET_FILTERS));

        final Result result = sendCommandAndWaitForLauncherAppsCallback(QUERIES_ACTIVITY_ACTION,
                CALLBACK_EVENT_PACKAGES_UNSUSPENDED, new String[]{TARGET_FILTERS});

        suspendPackages(false /* suspend */, Arrays.asList(TARGET_NO_API, TARGET_FILTERS));
        final Bundle response = result.await();

        assertThat(response.getInt(EXTRA_FLAGS), equalTo(CALLBACK_EVENT_PACKAGES_UNSUSPENDED));
        assertThat(response.getStringArray(EXTRA_PACKAGES),
                arrayContainingInAnyOrder(new String[]{TARGET_FILTERS}));
    }

    @LargeTest
    @Test
    public void sessionCallback_queriesNothing_cannotSeeSession() throws Exception {
        try {
            adoptShellPermissions(Manifest.permission.INSTALL_PACKAGES);
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Result result = sendCommandAndWaitForLauncherAppsSessionCallback(
                    QUERIES_NOTHING, sessionId);
            commitSession(sessionId);
            final Bundle response = result.await();
            assertThat(response.getInt(EXTRA_ID), equalTo(PackageInstaller.SessionInfo.INVALID_ID));
        } finally {
            uninstallPackage(TestApp.A);
            dropShellPermissions();
        }
    }

    @Test
    public void sessionCallback_queriesNothingHasPermission_canSeeSession()
            throws Exception {
        try {
            adoptShellPermissions(Manifest.permission.INSTALL_PACKAGES);
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Result result = sendCommandAndWaitForLauncherAppsSessionCallback(
                    QUERIES_NOTHING_PERM, sessionId);
            commitSession(sessionId);
            final Bundle response = result.await();
            assertThat(response.getInt(EXTRA_ID), equalTo(sessionId));
        } finally {
            uninstallPackage(TestApp.A);
            dropShellPermissions();
        }
    }

    @Test
    public void sessionCallback_queriesPackage_canSeeSession()
            throws Exception {
        try {
            adoptShellPermissions(Manifest.permission.INSTALL_PACKAGES);
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Result result = sendCommandAndWaitForLauncherAppsSessionCallback(
                    QUERIES_PACKAGE, sessionId);
            commitSession(sessionId);
            final Bundle response = result.await();
            assertThat(response.getInt(EXTRA_ID), equalTo(sessionId));
        } finally {
            uninstallPackage(TestApp.A);
            dropShellPermissions();
        }
    }

    @Test
    public void sessionCallback_queriesNothingTargetsQ_canSeeSession()
            throws Exception {
        try {
            adoptShellPermissions(Manifest.permission.INSTALL_PACKAGES);
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Result result = sendCommandAndWaitForLauncherAppsSessionCallback(
                    QUERIES_NOTHING_Q, sessionId);
            commitSession(sessionId);
            final Bundle response = result.await();
            assertThat(response.getInt(EXTRA_ID), equalTo(sessionId));
        } finally {
            uninstallPackage(TestApp.A);
            dropShellPermissions();
        }
    }

    @Test
    public void sessionCallback_sessionOwner_canSeeSession() throws Exception {
        try {
            adoptShellPermissions(Manifest.permission.INSTALL_PACKAGES);
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final int expectedSessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final LauncherApps launcherApps = sContext.getSystemService(LauncherApps.class);
            final PackageInstaller.SessionCallback
                    sessionCallback = new PackageInstaller.SessionCallback() {

                        @Override
                        public void onCreated(int sessionId) {
                            // No-op
                        }

                        @Override
                        public void onBadgingChanged(int sessionId) {
                            // No-op
                        }

                        @Override
                        public void onActiveChanged(int sessionId, boolean active) {
                            // No-op
                        }

                        @Override
                        public void onProgressChanged(int sessionId, float progress) {
                            // No-op
                        }

                        @Override
                        public void onFinished(int sessionId, boolean success) {
                            if (sessionId != expectedSessionId) {
                                return;
                            }

                            launcherApps.unregisterPackageInstallerSessionCallback(this);
                            countDownLatch.countDown();
                        }
                    };

            launcherApps.registerPackageInstallerSessionCallback(sContext.getMainExecutor(),
                    sessionCallback);

            commitSession(expectedSessionId);
            assertTrue(countDownLatch.await(5, TimeUnit.SECONDS));
        } finally {
            uninstallPackage(TestApp.A);
            dropShellPermissions();
        }
    }

    @Test
    public void getAllPkgInstallerSessions_queriesNothing_cannotSeeSessions() throws Exception {
        try {
            adoptShellPermissions(Manifest.permission.INSTALL_PACKAGES);
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Integer[] sessionIds = getSessionInfos(ACTION_GET_ALL_PACKAGE_INSTALLER_SESSIONS,
                    QUERIES_NOTHING, PackageInstaller.SessionInfo.INVALID_ID);
            assertThat(sessionIds, not(hasItemInArray(sessionId)));
        } finally {
            cleanUpMySessions();
            dropShellPermissions();
        }
    }

    @Test
    public void getAllPkgInstallerSessions_queriesNothingHasPermission_canSeeSessions()
            throws Exception {
        try {
            adoptShellPermissions(Manifest.permission.INSTALL_PACKAGES);
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Integer[] sessionIds = getSessionInfos(ACTION_GET_ALL_PACKAGE_INSTALLER_SESSIONS,
                    QUERIES_NOTHING_PERM, PackageInstaller.SessionInfo.INVALID_ID);
            assertThat(sessionIds, hasItemInArray(sessionId));
        } finally {
            cleanUpMySessions();
            dropShellPermissions();
        }
    }

    @Test
    public void getAllPkgInstallerSessions_queriesPackage_canSeeSessions() throws Exception {
        try {
            adoptShellPermissions(Manifest.permission.INSTALL_PACKAGES);
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Integer[] sessionIds = getSessionInfos(ACTION_GET_ALL_PACKAGE_INSTALLER_SESSIONS,
                    QUERIES_PACKAGE, PackageInstaller.SessionInfo.INVALID_ID);
            assertThat(sessionIds, hasItemInArray(sessionId));
        } finally {
            cleanUpMySessions();
            dropShellPermissions();
        }
    }

    @Test
    public void getAllPkgInstallerSessions_queriesNothingTargetsQ_canSeeSessions()
            throws Exception {
        try {
            adoptShellPermissions(Manifest.permission.INSTALL_PACKAGES);
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Integer[] sessionIds = getSessionInfos(ACTION_GET_ALL_PACKAGE_INSTALLER_SESSIONS,
                    QUERIES_NOTHING_Q, PackageInstaller.SessionInfo.INVALID_ID);
            assertThat(sessionIds, hasItemInArray(sessionId));
        } finally {
            cleanUpMySessions();
            dropShellPermissions();
        }
    }

    @Test
    public void getAllPkgInstallerSessions_sessionOwner_canSeeSessions() throws Exception {
        try {
            adoptShellPermissions(Manifest.permission.INSTALL_PACKAGES);
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final LauncherApps launcherApps = sContext.getSystemService(LauncherApps.class);
            final Integer[] sessionIds = launcherApps.getAllPackageInstallerSessions().stream()
                    .map(i -> i.getSessionId())
                    .distinct()
                    .toArray(Integer[]::new);
            assertThat(sessionIds, hasItemInArray(sessionId));
        } finally {
            cleanUpMySessions();
            dropShellPermissions();
        }
    }

    @Test
    public void isActivityEnabled_queriesActivityAction_canSeeActivity() throws Exception {
        final ComponentName targetFilters = ComponentName.createRelative(TARGET_FILTERS,
                ACTIVITY_CLASS_DUMMY_ACTIVITY);
        assertThat(QUERIES_ACTIVITY_ACTION + " should be able to see " + targetFilters,
                isActivityEnabled(QUERIES_ACTIVITY_ACTION, targetFilters),
                is(true));
    }

    @Test
    public void isActivityEnabled_queriesNothing_cannotSeeActivity() throws Exception {
        final ComponentName targetFilters = ComponentName.createRelative(TARGET_FILTERS,
                ACTIVITY_CLASS_DUMMY_ACTIVITY);
        assertThat(QUERIES_ACTIVITY_ACTION + " should not be able to see " + targetFilters,
                isActivityEnabled(QUERIES_NOTHING, targetFilters),
                is(false));
    }

    @Test
    public void getSuspendedPackageLauncherExtras_queriesNothingHasPerm_canGetExtras()
            throws Exception {
        try {
            suspendPackagesForUser(true /* suspend */, Arrays.asList(TARGET_NO_API),
                    UserReference.of(sContext.getUser()), true /* extraPersistableBundle */);
            Assert.assertNotNull(getSuspendedPackageLauncherExtras(QUERIES_NOTHING_PERM,
                    TARGET_NO_API));
        } finally {
            suspendPackages(false /* suspend */, Arrays.asList(TARGET_NO_API));
        }
    }

    @Test
    public void getSuspendedPackageLauncherExtras_queriesNothing_cannotGetExtras()
            throws Exception {
        try {
            suspendPackagesForUser(true /* suspend */, Arrays.asList(TARGET_NO_API),
                    UserReference.of(sContext.getUser()), true /* extraPersistableBundle */);
            Assert.assertNull(getSuspendedPackageLauncherExtras(QUERIES_NOTHING,
                    TARGET_NO_API));
        } finally {
            suspendPackages(false /* suspend */, Arrays.asList(TARGET_NO_API));
        }
    }

    @Test
    public void shouldHideFromSuggestions_queriesPackage_canSeeNoApi() throws Exception {
        setDistractingPackageRestrictions(new String[]{TARGET_NO_API},
                PackageManager.RESTRICTION_HIDE_FROM_SUGGESTIONS);

        try {
            final boolean hideFromSuggestions = shouldHideFromSuggestions(
                    QUERIES_PACKAGE, TARGET_NO_API);
            assertThat(hideFromSuggestions, is(true));
        } finally {
            setDistractingPackageRestrictions(new String[]{TARGET_NO_API},
                    PackageManager.RESTRICTION_NONE);
        }
    }

    @Test
    public void shouldHideFromSuggestions_queriesNothing_cannotSeeNoApi() throws Exception {
        setDistractingPackageRestrictions(new String[]{TARGET_NO_API},
                PackageManager.RESTRICTION_HIDE_FROM_SUGGESTIONS);

        try {
            final boolean hideFromSuggestions = shouldHideFromSuggestions(
                    QUERIES_NOTHING, TARGET_NO_API);
            assertThat(hideFromSuggestions, is(false));
        } finally {
            setDistractingPackageRestrictions(new String[]{TARGET_NO_API},
                    PackageManager.RESTRICTION_NONE);
        }
    }

    private Result sendCommandAndWaitForLauncherAppsCallback(String sourcePackageName,
            int expectedEventCode, String[] expectedPackages) throws Exception {
        final Bundle extra = new Bundle();
        extra.putInt(EXTRA_FLAGS, expectedEventCode);
        extra.putStringArray(EXTRA_PACKAGES, expectedPackages);
        final Result result = sendCommand(sourcePackageName, null /* targetPackageName */,
                INVALID_UID /* targetUid */, extra, ACTION_AWAIT_LAUNCHER_APPS_CALLBACK,
                true /* waitForReady */);
        return result;
    }

    private Result sendCommandAndWaitForLauncherAppsSessionCallback(String sourcePackageName,
            int expectedSessionId) throws Exception {
        final Bundle extra = new Bundle();
        extra.putInt(EXTRA_ID, expectedSessionId);
        final Result result = sendCommand(sourcePackageName, null /* targetPackageName */,
                INVALID_UID /* targetUid */, extra, ACTION_AWAIT_LAUNCHER_APPS_SESSION_CALLBACK,
                true /* waitForReady */);
        return result;
    }

    private boolean isActivityEnabled(String sourcePackageName, ComponentName componentName)
            throws Exception {
        final Bundle extraData = new Bundle();
        extraData.putString(Intent.EXTRA_COMPONENT_NAME, componentName.flattenToString());
        final Bundle response = sendCommandBlocking(sourcePackageName, null /* targetPackageName */,
                extraData, ACTION_LAUNCHER_APPS_IS_ACTIVITY_ENABLED);
        return response.getBoolean(Intent.EXTRA_RETURN_RESULT);
    }

    private Bundle getSuspendedPackageLauncherExtras(String sourcePackageName,
            String targetPackageName) throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackageName, targetPackageName,
                null /* extraData */, ACTION_LAUNCHER_APPS_GET_SUSPENDED_PACKAGE_LAUNCHER_EXTRAS);
        return response.getBundle(Intent.EXTRA_RETURN_RESULT);
    }

    private void setDistractingPackageRestrictions(String[] packagesToRestrict,
            int distractionFlags) throws Exception {
        final String[] failed = SystemUtil.callWithShellPermissionIdentity(
                () -> sPm.setDistractingPackageRestrictions(packagesToRestrict, distractionFlags));
        assertThat(failed, emptyArray());
    }

    private boolean shouldHideFromSuggestions(String sourcePackageName, String targetPackageName)
            throws Exception {
        final Bundle extraData = new Bundle();
        extraData.putInt(Intent.EXTRA_USER, Process.myUserHandle().getIdentifier());
        final Bundle response = sendCommandBlocking(sourcePackageName, targetPackageName, extraData,
                ACTION_LAUNCHER_APPS_SHOULD_HIDE_FROM_SUGGESTIONS);
        return response.getBoolean(Intent.EXTRA_RETURN_RESULT);
    }
}
