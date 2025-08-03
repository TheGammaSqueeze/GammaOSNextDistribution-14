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

package com.android.bedstead.nene.packages;

import static android.os.Build.VERSION_CODES.S;

import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;
import static com.android.queryable.queries.ActivityQuery.activity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.content.Intent;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.annotations.RequireSdkVersion;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.nene.utils.Versions;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppActivityReference;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.FileUtils;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@RunWith(BedsteadJUnit4.class)
public class PackagesTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private static final String INPUT_METHODS_FEATURE = "android.software.input_methods";
    private static final String NON_EXISTING_PACKAGE = "com.package.does.not.exist";
    // Controlled by AndroidTest.xml
    private static final String TEST_APP_PACKAGE_NAME =
            "com.android.bedstead.nene.testapps.TestApp1";
    private static final File TEST_APP_APK_FILE = new File("/data/local/tmp/NeneTestApp1.apk");
    private static final File NON_EXISTING_APK_FILE =
            new File("/data/local/tmp/ThisApkDoesNotExist.apk");
    private static final byte[] TEST_APP_BYTES = loadBytes(TEST_APP_APK_FILE);
    private static final TestApp sTestApp = sDeviceState.testApps().query()
            .whereActivities().contains(
                    activity().where().exported().isTrue()
            ).get();
    private final UserReference mUser = TestApis.users().instrumented();
    private final Package mExistingPackage =
            TestApis.packages().find("com.android.providers.telephony");
    private final Package mTestAppReference =
            TestApis.packages().find(TEST_APP_PACKAGE_NAME);
    private final Package mDifferentTestAppReference =
            TestApis.packages().find(NON_EXISTING_PACKAGE);
    private final UserReference mNonExistingUser = TestApis.users().find(99999);
    private final File mApkFile = new File("");

    private static byte[] loadBytes(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return FileUtils.readInputStreamFully(fis);
        } catch (IOException e) {
            throw new AssertionError("Could not read file bytes", e);
        }
    }

    @Test
    public void construct_constructs() {
        new Packages(); // Doesn't throw any exceptions
    }

    @Test
    public void features_noUserSpecified_containsKnownFeature() {
        assertThat(TestApis.packages().features()).contains(INPUT_METHODS_FEATURE);
    }

    @Test
    public void find_nullPackageName_throwsException() {
        assertThrows(NullPointerException.class, () -> TestApis.packages().find(null));
    }

    @Test
    public void find_existingPackage_returnsPackageReference() {
        assertThat(TestApis.packages().find(mExistingPackage.packageName())).isNotNull();
    }

    @Test
    public void find_nonExistingPackage_returnsPackageReference() {
        assertThat(TestApis.packages().find(NON_EXISTING_PACKAGE)).isNotNull();
    }

    @Test
    public void installedForUser_nullUserReference_throwsException() {
        assertThrows(NullPointerException.class,
                () -> TestApis.packages().installedForUser(/* user= */ null));
    }

    @Test
    public void installedForUser_containsPackageInstalledForUser() {
        Package pkg = TestApis.packages().install(mUser, TEST_APP_APK_FILE);

        try {
            assertThat(TestApis.packages().installedForUser(mUser)).contains(pkg);
        } finally {
            pkg.uninstall(mUser);
        }
    }

    @Test
    @EnsureHasAdditionalUser
    public void installedForUser_doesNotContainPackageNotInstalledForUser() {
        Package pkg = null;
        try {
            pkg = TestApis.packages().install(mUser, TEST_APP_APK_FILE);
            pkg.uninstall(sDeviceState.additionalUser());

            assertThat(TestApis.packages().installedForUser(sDeviceState.additionalUser()))
                    .doesNotContain(pkg);
        } finally {
            if (pkg != null) {
                pkg.uninstall(mUser);
            }
        }
    }

    @Test
    public void install_nonExistingPackage_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.packages().install(mUser, NON_EXISTING_APK_FILE));
    }

    @Test
    public void install_nullUser_throwsException() {
        assertThrows(NullPointerException.class,
                () -> TestApis.packages().install(/* user= */ null, mApkFile));
    }

    @Test
    public void install_byteArray_nullUser_throwsException() {
        assertThrows(NullPointerException.class,
                () -> TestApis.packages().install(/* user= */ null, TEST_APP_BYTES));
    }

    @Test
    public void install_nullApkFile_throwsException() {
        assertThrows(NullPointerException.class,
                () -> TestApis.packages().install(mUser, (File) /* apkFile= */ null));
    }

    @Test
    public void install_nullByteArray_throwsException() {
        assertThrows(NullPointerException.class,
                () -> TestApis.packages().install(mUser, (byte[]) /* apkFile= */ null));
    }

    @Test
    public void install_instrumentedUser_isInstalled() {
        Package pkg =
                TestApis.packages().install(TestApis.users().instrumented(), TEST_APP_APK_FILE);

        try {
            assertThat(pkg.installedOnUser()).isTrue();
        } finally {
            pkg.uninstall(TestApis.users().instrumented());
        }
    }

    @Test
    public void install_byteArray_instrumentedUser_isInstalled() {
        Package pkg =
                TestApis.packages().install(TestApis.users().instrumented(), TEST_APP_BYTES);

        try {
            assertThat(pkg.installedOnUser()).isTrue();
        } finally {
            pkg.uninstall(TestApis.users().instrumented());
        }
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    public void install_inWorkProfile_isInstalled() {
        TestApis.packages().install(sDeviceState.workProfile(), TEST_APP_APK_FILE);
        Package pkg = TestApis.packages().find(TEST_APP_PACKAGE_NAME);

        try {
            assertThat(pkg.installedOnUser(sDeviceState.workProfile())).isTrue();
        } finally {
            pkg.uninstall(sDeviceState.workProfile());
        }
    }

    @Test
    @EnsureHasAdditionalUser
    public void install_differentUser_isInstalled() {
        TestApis.packages().install(sDeviceState.additionalUser(), TEST_APP_APK_FILE);
        Package pkg = TestApis.packages().find(TEST_APP_PACKAGE_NAME);

        try {
            assertThat(pkg.installedOnUser(sDeviceState.additionalUser())).isTrue();
        } finally {
            pkg.uninstall(sDeviceState.additionalUser());
        }
    }

    @Test
    @EnsureHasAdditionalUser
    public void install_byteArray_differentUser_isInstalled() {
        Package pkg = null;
        try {
            pkg = TestApis.packages().install(sDeviceState.additionalUser(), TEST_APP_BYTES);

            assertThat(pkg.installedOnUser(sDeviceState.additionalUser())).isTrue();
        } finally {
            if (pkg != null) {
                pkg.uninstallFromAllUsers();
            }
        }
    }

    @Test
    @EnsureHasAdditionalUser
    public void install_userNotStarted_throwsException() {
        try {
            sDeviceState.additionalUser().stop();

            assertThrows(NeneException.class, () -> {
                TestApis.packages().install(sDeviceState.additionalUser(), TEST_APP_APK_FILE);
            });
        } finally {
            sDeviceState.additionalUser().start();
        }
    }

    @Test
    @EnsureHasAdditionalUser
    public void install_byteArray_userNotStarted_throwsException() {
        try {
            sDeviceState.additionalUser().stop();

            assertThrows(NeneException.class, () -> {
                TestApis.packages().install(sDeviceState.additionalUser(), TEST_APP_BYTES);
            });
        } finally {
            sDeviceState.additionalUser().start();
        }
    }

//    @Test
//    @EnsureHasAdditionalUser
//    public void install_userNotStarted_isInstalled() {
//        sDeviceState.additionalUser().stop();
//
//        TestApis.packages().install(sDeviceState.additionalUser(), TEST_APP_APK_FILE);
//        Package pkg = TestApis.packages().find(TEST_APP_PACKAGE_NAME);
//
//        try {
//            assertThat(pkg.installedOnUser(sDeviceState.additionalUser())).isTrue();
//        } finally {
//            pkg.uninstall(sDeviceState.additionalUser());
//        }
//    }
//
//    @Test
//    @EnsureHasAdditionalUser
//    public void install_byteArray_userNotStarted_isInstalled() {
//        sDeviceState.additionalUser().stop();
//
//        Package pkg = null;
//        try {
//            pkg = TestApis.packages().install(sDeviceState.additionalUser(), TEST_APP_BYTES);
//
//            assertThat(pkg.installedOnUser(sDeviceState.additionalUser())).isTrue();
//        } finally {
//            if (pkg != null) {
//                pkg.uninstallFromAllUsers();
//            }
//        }
//    }

    @Test
    public void install_userDoesNotExist_throwsException() {
        assertThrows(NeneException.class, () -> TestApis.packages().install(mNonExistingUser,
                TEST_APP_APK_FILE));
    }

    @Test
    public void install_byteArray_userDoesNotExist_throwsException() {
        assertThrows(NeneException.class, () -> TestApis.packages().install(mNonExistingUser,
                TEST_APP_BYTES));
    }

    @Test
    public void install_alreadyInstalledForUser_installs() {
        Package pkg = TestApis.packages().install(mUser, TEST_APP_APK_FILE);

        try {
            pkg = TestApis.packages().install(mUser, TEST_APP_APK_FILE);
            assertThat(pkg.installedOnUser(mUser)).isTrue();
        } finally {
            pkg.uninstall(mUser);
        }
    }

    @Test
    public void install_byteArray_alreadyInstalledForUser_installs() {
        Package pkg = TestApis.packages().install(mUser, TEST_APP_BYTES);

        try {
            pkg = TestApis.packages().install(mUser, TEST_APP_BYTES);
            assertThat(pkg.installedOnUser(mUser)).isTrue();
        } finally {
            pkg.uninstall(mUser);
        }
    }

    @Test
    @EnsureHasAdditionalUser
    public void install_alreadyInstalledOnOtherUser_installs() {
        Package pkg = null;

        try {
            pkg = TestApis.packages().install(sDeviceState.additionalUser(), TEST_APP_APK_FILE);

            TestApis.packages().install(mUser, TEST_APP_APK_FILE);

            assertThat(pkg.installedOnUser(mUser)).isTrue();
        } finally {
            if (pkg != null) {
                pkg.uninstallFromAllUsers();
            }
        }
    }

    @Test
    @EnsureHasAdditionalUser
    public void install_byteArray_alreadyInstalledOnOtherUser_installs() {
        Package pkg = null;

        try {
            pkg = TestApis.packages().install(sDeviceState.additionalUser(), TEST_APP_BYTES);

            TestApis.packages().install(mUser, TEST_APP_BYTES);

            assertThat(pkg.installedOnUser(mUser)).isTrue();
        } finally {
            if (pkg != null) {
                pkg.uninstallFromAllUsers();
            }
        }
    }

    @Test
    @RequireSdkVersion(min = S, reason = "keepUninstalledPackages is only supported on S+")
    @Ignore
    // TODO: .exists() doesn't return true when the package is kept - restore this functionality
    public void keepUninstalledPackages_packageIsUninstalled_packageStillExists() {
        try (TestAppInstance testAppInstance = sTestApp.install()) {
            TestApis.packages().keepUninstalledPackages()
                    .add(sTestApp.pkg())
                    .commit();

            testAppInstance.uninstall();

            assertThat(sTestApp.pkg().exists()).isTrue();
        } finally {
            TestApis.packages().keepUninstalledPackages().clear();
        }
    }

    @Test
    @Ignore("While using adb calls this is not reliable, enable once we use framework calls for "
            + "uninstall")
    public void keepUninstalledPackages_packageRemovedFromList_packageIsUninstalled_packageDoesNotExist() {
        assumeTrue("keepUninstalledPackages is only supported on S+",
                Versions.meetsMinimumSdkVersionRequirement(S));

        TestApis.packages().install(mUser, TEST_APP_APK_FILE);
        TestApis.packages().keepUninstalledPackages()
                .add(mTestAppReference)
                .commit();
        TestApis.packages().keepUninstalledPackages()
                .add(mDifferentTestAppReference)
                .commit();

        try {
            mTestAppReference.uninstall(mUser);

            assertThat(mTestAppReference.exists()).isFalse();
        } finally {
            TestApis.packages().keepUninstalledPackages().clear();
        }
    }

    @Test
    @Ignore("While using adb calls this is not reliable, enable once we use framework calls for "
            + "uninstall")
    public void keepUninstalledPackages_cleared_packageIsUninstalled_packageDoesNotExist() {
        assumeTrue("keepUninstalledPackages is only supported on S+",
                Versions.meetsMinimumSdkVersionRequirement(S));

        TestApis.packages().install(mUser, TEST_APP_APK_FILE);

        TestApis.packages().keepUninstalledPackages()
                .add(mTestAppReference)
                .commit();
        TestApis.packages().keepUninstalledPackages().clear();

        try {
            mTestAppReference.uninstall(mUser);

            assertThat(mTestAppReference.exists()).isFalse();
        } finally {
            TestApis.packages().keepUninstalledPackages().clear();
        }
    }

    @Test
    @Ignore("While using adb calls this is not reliable, enable once we use framework calls for "
            + "uninstall")
    public void keepUninstalledPackages_packageRemovedFromList_packageAlreadyUninstalled_packageDoesNotExist() {
        assumeTrue("keepUninstalledPackages is only supported on S+",
                Versions.meetsMinimumSdkVersionRequirement(S));

        TestApis.packages().install(mUser, TEST_APP_APK_FILE);
        TestApis.packages().keepUninstalledPackages().add(mTestAppReference).commit();
        mTestAppReference.uninstall(mUser);
        TestApis.packages().keepUninstalledPackages().add(mDifferentTestAppReference).commit();

        try {
            assertThat(mTestAppReference.exists()).isFalse();
        } finally {
            TestApis.packages().keepUninstalledPackages().clear();
        }
    }

    @Test
    @Ignore // TODO(270963894): Restore
    public void kill_killsProcess() {
        try (TestAppInstance testApp = sTestApp.install()) {
            // Start an activity so the process exists
            testApp.activities().query()
                    .whereActivity().exported().isTrue()
                    .get().start();
            Poll.forValue("process", () -> sTestApp.pkg().runningProcess())
                    .toNotBeNull()
                    .await();
            int pidBeforeKill = sTestApp.pkg().runningProcess().pid();

            sTestApp.pkg().runningProcess().kill();

            ProcessReference processReference = sTestApp.pkg().runningProcess();
            int pidAfterKill = processReference == null ? -1 : processReference.pid();
            assertThat(pidAfterKill).isNotEqualTo(pidBeforeKill);
        }
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    @Ignore // TODO(270963894): Restore
    public void kill_doesNotKillProcessInOtherUser() {
        try (TestAppInstance personalTestApp = sTestApp.install();
                TestAppInstance workTestApp = sTestApp.install(sDeviceState.workProfile())) {
            // Start an activity so the process exists
            TestAppActivityReference activity =
                    personalTestApp.activities().query().whereActivity().exported().isTrue().get();
            Intent intent = new Intent();
            intent.setComponent(activity.component().componentName());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            TestApis.context().instrumentedContext().startActivity(intent);
            try (PermissionContext p =
                         TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
                TestApis.context().instrumentedContext().startActivityAsUser(
                        intent, sDeviceState.workProfile().userHandle());
            }
            Poll.forValue("process",
                    () -> sTestApp.pkg().runningProcess(sDeviceState.workProfile()))
                    .toNotBeNull()
                    .await();

             sTestApp.pkg().runningProcess().kill();

            assertThat(sTestApp.pkg().runningProcess(sDeviceState.workProfile())).isNotNull();
        }
    }
}
