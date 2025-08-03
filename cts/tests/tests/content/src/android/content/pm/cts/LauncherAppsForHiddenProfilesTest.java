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

package android.content.pm.cts;

import static android.Manifest.permission.ACCESS_HIDDEN_PROFILES;
import static android.Manifest.permission.ACCESS_HIDDEN_PROFILES_FULL;
import static android.multiuser.Flags.FLAG_ENABLE_HIDING_PROFILES;
import static android.multiuser.Flags.FLAG_ENABLE_LAUNCHER_APPS_HIDDEN_PROFILE_CHECKS;
import static android.multiuser.Flags.FLAG_ENABLE_PERMISSION_TO_ACCESS_HIDDEN_PROFILES;
import static android.os.Flags.FLAG_ALLOW_PRIVATE_PROFILE;

import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;
import static com.android.bedstead.nene.permissions.CommonPermissions.START_TASKS_FROM_RECENTS;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.getDefaultLauncher;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.setDefaultLauncher;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.bedstead.testapp.TestAppProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(BedsteadJUnit4.class)
public class LauncherAppsForHiddenProfilesTest {

    private static final String TEST_APP = "com.android.bedstead.testapp.AccountManagementApp";
    private static final String TEST_ACTIVITY = "android.testapp.CrossProfileSharingActivity";
    private Context mContext;

    private LauncherApps mLauncherApps;

    private String mDefaultHome;

    private final TestApp mTestApp =
            new TestAppProvider().query().wherePackageName().isEqualTo(TEST_APP).get();
    private final ComponentName mTestAppComponent = new ComponentName(TEST_APP, TEST_ACTIVITY);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // Private profile is not supported on wear devices
        // TODO(b/290333800): filter out with PS specific annotation once ready
        assumeFalse(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH));

        mLauncherApps = mContext.getSystemService(LauncherApps.class);
        mDefaultHome = getDefaultLauncher(InstrumentationRegistry.getInstrumentation());
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_ENABLE_LAUNCHER_APPS_HIDDEN_PROFILE_CHECKS,
        FLAG_ENABLE_PERMISSION_TO_ACCESS_HIDDEN_PROFILES,
        FLAG_ENABLE_HIDING_PROFILES
    })
    public void testGeneralApis_notDefaultLauncherWithPerms_hiddenProfileInfoStripped() {
        try (UserReference privateProfile = createProfileAndSetupTestState();
                PermissionContext p =
                        TestApis.permissions()
                                .withPermission(
                                        ACCESS_HIDDEN_PROFILES, ACCESS_HIDDEN_PROFILES_FULL)) {
            assertHiddenProfileInfoStripped(privateProfile.userHandle());
        }
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_ENABLE_LAUNCHER_APPS_HIDDEN_PROFILE_CHECKS,
        FLAG_ENABLE_PERMISSION_TO_ACCESS_HIDDEN_PROFILES,
        FLAG_ENABLE_HIDING_PROFILES
    })
    public void testGeneralApis_defaultLauncherNoPerms_hiddenProfileInfoStripped() {
        try (UserReference privateProfile = createProfileAndSetupTestState();
                PermissionContext p =
                        TestApis.permissions()
                                .withoutPermission(
                                        ACCESS_HIDDEN_PROFILES, ACCESS_HIDDEN_PROFILES_FULL)) {
            setSelfAsDefaultLauncher();
            assertHiddenProfileInfoStripped(privateProfile.userHandle());
        }
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_ENABLE_LAUNCHER_APPS_HIDDEN_PROFILE_CHECKS,
        FLAG_ENABLE_PERMISSION_TO_ACCESS_HIDDEN_PROFILES,
        FLAG_ENABLE_HIDING_PROFILES
    })
    public void testGeneralApis_defaultLauncherWithSystemPerm_hiddenProfileInfoAvailable() {
        try (UserReference privateProfile = createProfileAndSetupTestState();
                PermissionContext p =
                        TestApis.permissions().withoutPermission(ACCESS_HIDDEN_PROFILES);
                PermissionContext p2 =
                        TestApis.permissions().withPermission(ACCESS_HIDDEN_PROFILES_FULL)) {
            setSelfAsDefaultLauncher();
            assertHiddenProfileInfoAvailable(privateProfile.userHandle());
        }
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_ENABLE_LAUNCHER_APPS_HIDDEN_PROFILE_CHECKS,
        FLAG_ENABLE_PERMISSION_TO_ACCESS_HIDDEN_PROFILES,
        FLAG_ENABLE_HIDING_PROFILES
    })
    public void testGeneralApis_defaultLauncherWithNormalPerm_hiddenProfileInfoAvailable() {
        try (UserReference privateProfile = createProfileAndSetupTestState();
                PermissionContext p =
                        TestApis.permissions().withoutPermission(ACCESS_HIDDEN_PROFILES_FULL);
                PermissionContext p2 =
                        TestApis.permissions().withPermission(ACCESS_HIDDEN_PROFILES)) {
            setSelfAsDefaultLauncher();
            assertHiddenProfileInfoAvailable(privateProfile.userHandle());
        }
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_ENABLE_LAUNCHER_APPS_HIDDEN_PROFILE_CHECKS,
        FLAG_ENABLE_HIDING_PROFILES
    })
    public void testGetProfiles_calledFromProfile_returnsOnlyCurrentProfile() {
        try (UserReference privateProfile = createProfileAndSetupTestState()) {
            assertThat(
                            Objects.requireNonNull(
                                            TestApis.context()
                                                    .androidContextAsUser(privateProfile)
                                                    .getSystemService(LauncherApps.class))
                                    .getProfiles())
                    .hasSize(1);
        }
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_ENABLE_LAUNCHER_APPS_HIDDEN_PROFILE_CHECKS,
        FLAG_ENABLE_PERMISSION_TO_ACCESS_HIDDEN_PROFILES,
        FLAG_ENABLE_HIDING_PROFILES
    })
    public void testAppChangesCallbacks_defaultLauncherWithNormalPerm_callbacksReceived() {
        try (UserReference privateProfile = createProfile();
                PermissionContext p =
                        TestApis.permissions().withoutPermission(ACCESS_HIDDEN_PROFILES_FULL);
                PermissionContext p2 =
                        TestApis.permissions().withPermission(ACCESS_HIDDEN_PROFILES)) {
            privateProfile.start();
            setSelfAsDefaultLauncher();
            assertCallbacksPropagation(privateProfile, /* received= */ true);
        }
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_ENABLE_LAUNCHER_APPS_HIDDEN_PROFILE_CHECKS,
        FLAG_ENABLE_PERMISSION_TO_ACCESS_HIDDEN_PROFILES,
        FLAG_ENABLE_HIDING_PROFILES
    })
    public void testAppChangesCallbacks_defaultLauncherWithSystemPerm_callbacksReceived() {
        try (UserReference privateProfile = createProfile();
                PermissionContext p =
                        TestApis.permissions().withoutPermission(ACCESS_HIDDEN_PROFILES);
                PermissionContext p2 =
                        TestApis.permissions().withPermission(ACCESS_HIDDEN_PROFILES_FULL)) {
            privateProfile.start();
            setSelfAsDefaultLauncher();
            assertCallbacksPropagation(privateProfile, /* received= */ true);
        }
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_ENABLE_LAUNCHER_APPS_HIDDEN_PROFILE_CHECKS,
        FLAG_ENABLE_PERMISSION_TO_ACCESS_HIDDEN_PROFILES,
        FLAG_ENABLE_HIDING_PROFILES
    })
    public void testAppChangesCallbacks_notDefaultLauncherWithPerms_callbacksNotReceived() {
        try (UserReference privateProfile = createProfile();
                PermissionContext p =
                        TestApis.permissions()
                                .withPermission(
                                        ACCESS_HIDDEN_PROFILES, ACCESS_HIDDEN_PROFILES_FULL)) {
            privateProfile.start();
            assertCallbacksPropagation(privateProfile, /* received= */ false);
        }
    }

    @Test
    @FlakyTest(bugId = 325954148)
    @RequiresFlagsEnabled({
        FLAG_ALLOW_PRIVATE_PROFILE,
        FLAG_ENABLE_LAUNCHER_APPS_HIDDEN_PROFILE_CHECKS,
        FLAG_ENABLE_PERMISSION_TO_ACCESS_HIDDEN_PROFILES,
        FLAG_ENABLE_HIDING_PROFILES
    })
    public void testAppChangesCallbacks_defaultLauncherNoPerms_callbacksNotReceived() {
        try (UserReference privateProfile = createProfile();
                PermissionContext p =
                        TestApis.permissions()
                                .withoutPermission(
                                        ACCESS_HIDDEN_PROFILES, ACCESS_HIDDEN_PROFILES_FULL)) {
            privateProfile.start();
            setSelfAsDefaultLauncher();
            assertCallbacksPropagation(privateProfile, /* received= */ false);
        }
    }

    private void assertCallbacksPropagation(UserReference targetUser, boolean received) {
        TestLauncherCallback callback =
                new TestLauncherCallback(targetUser.userHandle(), mTestApp.packageName());
        mLauncherApps.registerCallback(callback, new Handler(Looper.getMainLooper()));
        triggerCallbacks(targetUser);
        long timeoutSec = 2;
        try {
            assertThat(callback.mPackageAdded.await(timeoutSec, TimeUnit.SECONDS))
                    .isEqualTo(received);
            assertThat(callback.mPackageChanged.await(timeoutSec, TimeUnit.SECONDS))
                    .isEqualTo(received);
            assertThat(callback.mPackageRemoved.await(timeoutSec, TimeUnit.SECONDS))
                    .isEqualTo(received);
        } catch (InterruptedException e) {
            fail("Test interrupted unexpectedly");
        }
    }

    private void triggerCallbacks(UserReference reference) {
        UserHandle profileUser = reference.userHandle();
        try (TestAppInstance instance = mTestApp.install(profileUser)) {
            instance.activities().any().component().disable(reference);
            mTestApp.uninstall(profileUser);
        }
    }

    @After
    public void tearDown() throws Exception {
        if (mDefaultHome != null) {
            setDefaultLauncher(InstrumentationRegistry.getInstrumentation(), mDefaultHome);
        }
    }

    private void setSelfAsDefaultLauncher() {
        setDefaultLauncher(InstrumentationRegistry.getInstrumentation(), mContext.getPackageName());
    }

    private void assertHiddenProfileInfoStripped(UserHandle targetUser) {
        assertThat(mLauncherApps.getProfiles()).doesNotContain(targetUser);
        assertThat(mLauncherApps.getLauncherUserInfo(targetUser)).isNull();
        assertThat(mLauncherApps.getPreInstalledSystemPackages(targetUser)).isEmpty();
        assertThat(mLauncherApps.getAppMarketActivityIntent(/* packageName= */ null, targetUser))
                .isNull();

        String packageName = mTestApp.packageName();
        assertThat(mLauncherApps.getActivityList(packageName, targetUser)).isEmpty();
        assertThat(mLauncherApps.isPackageEnabled(packageName, targetUser)).isFalse();
        assertThrows(
                PackageManager.NameNotFoundException.class,
                () -> mLauncherApps.getApplicationInfo(packageName, /* flags= */ 0, targetUser));
        assertThat(mLauncherApps.isActivityEnabled(mTestAppComponent, targetUser)).isFalse();
        assertThat(
                        mLauncherApps.resolveActivity(
                                new Intent().setComponent(mTestAppComponent), targetUser))
                .isNull();
        assertThat(
                        mLauncherApps.getAllPackageInstallerSessions().stream()
                                .map(PackageInstaller.SessionInfo::getUser)
                                .toList())
                .doesNotContain(targetUser);
        assertThat(mLauncherApps.shouldHideFromSuggestions(packageName, targetUser)).isFalse();

        try (PermissionContext p =
                TestApis.permissions().withPermission(START_TASKS_FROM_RECENTS)) {
            mLauncherApps.startMainActivity(mTestAppComponent, targetUser, null, null);
        } catch (Exception e) {
            fail("No exceptions expected while trying to start activity in hidden user");
        }
    }

    private void assertHiddenProfileInfoAvailable(UserHandle targetUser) {
        assertThat(mLauncherApps.getProfiles()).contains(targetUser);
        assertThat(mLauncherApps.getLauncherUserInfo(targetUser)).isNotNull();
        assertThat(mLauncherApps.getPreInstalledSystemPackages(targetUser)).isNotEmpty();
        assertThat(mLauncherApps.getAppMarketActivityIntent(/* packageName= */ null, targetUser))
                .isNotNull();

        String packageName = mTestApp.packageName();
        assertThat(mLauncherApps.getActivityList(packageName, targetUser)).isNotEmpty();
        assertThat(mLauncherApps.isPackageEnabled(packageName, targetUser)).isTrue();
        try {
            assertThat(mLauncherApps.getApplicationInfo(packageName, /* flags= */ 0, targetUser))
                    .isNotNull();
        } catch (PackageManager.NameNotFoundException e) {
            fail("Unexpected NameNotFoundException exception " + e.getMessage());
        }
        assertThat(mLauncherApps.isActivityEnabled(mTestAppComponent, targetUser)).isTrue();
        assertThat(
                        mLauncherApps.resolveActivity(
                                new Intent().setComponent(mTestAppComponent), targetUser))
                .isNotNull();
        assertThat(
                        mLauncherApps.getAllPackageInstallerSessions().stream()
                                .map(PackageInstaller.SessionInfo::getUser)
                                .toList())
                .contains(targetUser);
        assertThat(mLauncherApps.shouldHideFromSuggestions(packageName, targetUser)).isTrue();

        try (PermissionContext p =
                TestApis.permissions().withPermission(START_TASKS_FROM_RECENTS)) {
            // Expect exception as test component doesn't have category Intent.CATEGORY_LAUNCHER
            assertThrows(
                    SecurityException.class,
                    () ->
                            mLauncherApps.startMainActivity(
                                    mTestAppComponent, targetUser, null, null));
        }
    }

    private UserReference createProfileAndSetupTestState() {
        UserReference reference = createProfile();
        reference.start();
        mTestApp.install(reference);

        // Required to test getAllPackageInstallerSessions API
        startInstallationSession(reference);

        // Required to test shouldHideFromSuggestions API
        setAppAsDistracting(reference, mTestApp.packageName());
        return reference;
    }

    private UserReference createProfile() {
        return TestApis.users()
                .createUser()
                .parent(TestApis.users().instrumented())
                .type(TestApis.users().supportedType("android.os.usertype.profile.PRIVATE"))
                .create();
    }

    private void startInstallationSession(UserReference reference) {
        try (PermissionContext p =
                TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            PackageInstaller installer =
                    TestApis.context()
                            .androidContextAsUser(reference)
                            .getPackageManager()
                            .getPackageInstaller();

            installer.openSession(
                    installer.createSession(
                            new PackageInstaller.SessionParams(
                                    PackageInstaller.SessionParams.MODE_FULL_INSTALL)));
        } catch (IOException e) {
            fail("Couldn't create install session: " + e.getMessage());
        }
    }

    private void setAppAsDistracting(UserReference reference, String packageName) {
        ShellCommand.Builder cmd;
        try {
            cmd =
                    ShellCommand.builderForUser(reference, "pm set-distracting-restriction")
                            .asRoot(true)
                            .addOption("--flag", "hide-from-suggestions")
                            .addOperand(packageName);
            cmd.execute();
        } catch (AdbException e) {
            fail("Couldn't set test package as distracted " + e.getMessage());
        }
    }

    private static class TestLauncherCallback extends LauncherApps.Callback {
        public CountDownLatch mPackageAdded;
        public CountDownLatch mPackageChanged;
        public CountDownLatch mPackageRemoved;
        private final UserHandle mTargetUser;
        private final String mTargetPackage;

        TestLauncherCallback(UserHandle targetUser, String targetPackageName) {
            mTargetUser = targetUser;
            mTargetPackage = targetPackageName;
            mPackageAdded = new CountDownLatch(1);
            mPackageRemoved = new CountDownLatch(1);
            mPackageChanged = new CountDownLatch(1);
        }

        @Override
        public void onPackageAdded(String packageName, UserHandle user) {
            if (isTargetEvent(packageName, user)) {
                mPackageAdded.countDown();
            }
        }

        @Override
        public void onPackageRemoved(String packageName, UserHandle user) {
            if (isTargetEvent(packageName, user)) {
                mPackageRemoved.countDown();
            }
        }

        @Override
        public void onPackageChanged(String packageName, UserHandle user) {
            if (isTargetEvent(packageName, user)) {
                mPackageChanged.countDown();
            }
        }

        @Override
        public void onPackagesAvailable(
                String[] packageNames, UserHandle user, boolean replacing) {}

        @Override
        public void onPackagesUnavailable(
                String[] packageNames, UserHandle user, boolean replacing) {}

        private boolean isTargetEvent(String packageName, UserHandle user) {
            return mTargetUser.equals(user) && Objects.equals(packageName, mTargetPackage);
        }
    }
}
