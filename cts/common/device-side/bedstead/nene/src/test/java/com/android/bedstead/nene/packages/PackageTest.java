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

import static android.Manifest.permission.MANAGE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION_CODES.R;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNotNull;
import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.os.Environment;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.BeforeClass;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunNotOnSecondaryUser;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(BedsteadJUnit4.class)
public class PackageTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final UserReference sUser = TestApis.users().instrumented();
    private static final String NON_EXISTING_PACKAGE_NAME = "com.package.does.not.exist";
    private static final String PACKAGE_NAME = NON_EXISTING_PACKAGE_NAME;
    private static final String EXISTING_PACKAGE_NAME = "com.android.providers.telephony";
    private static final String ACCESS_NETWORK_STATE_PERMISSION =
            "android.permission.ACCESS_NETWORK_STATE";

    private static final Context sContext =
            TestApis.context().instrumentedContext();

    private static final Package sInstrumentedPackage =
            TestApis.packages().find(sContext.getPackageName());
    private static final String INSTALL_PERMISSION = "android.permission.CHANGE_WIFI_STATE";
    private static final String UNDECLARED_RUNTIME_PERMISSION = "android.permission.RECEIVE_SMS";
    private static final String DECLARED_RUNTIME_PERMISSION =
            "android.permission.INTERACT_ACROSS_USERS";
    private static final String NON_EXISTING_PERMISSION = "aPermissionThatDoesntExist";
    private static final String USER_SPECIFIC_PERMISSION = "android.permission.READ_CONTACTS";
    private static final TestApp sTestApp = sDeviceState.testApps().query()
            .wherePermissions().contains(
                    USER_SPECIFIC_PERMISSION,
                    DECLARED_RUNTIME_PERMISSION,
                    INSTALL_PERMISSION
            ).get();
    private static final File sTestAppApkFile = new File(
            Environment.getExternalStorageDirectory(), "testApp.apk");

    @BeforeClass
    public static void setupClass() throws Exception {
        try (PermissionContext p = TestApis.permissions()
                .withPermissionOnVersionAtLeast(R, MANAGE_EXTERNAL_STORAGE)) {
            sTestApp.writeApkFile(sTestAppApkFile);
        }
    }

    @AfterClass
    public static void teardownClass() throws Exception {
        try (PermissionContext p = TestApis.permissions()
                .withPermissionOnVersionAtLeast(R, MANAGE_EXTERNAL_STORAGE)) {
            sTestAppApkFile.delete();
        }
    }

    @Test
    public void packageName_returnsPackageName() {
        assertThat(TestApis.packages().find(PACKAGE_NAME).packageName()).isEqualTo(PACKAGE_NAME);
    }

    @Test
    public void exists_nonExistingPackage_returnsFalse() {
        assertThat(TestApis.packages().find(NON_EXISTING_PACKAGE_NAME).exists()).isFalse();
    }

    @Test
    public void exists_existingPackage_returnsTrue() {
        assertThat(TestApis.packages().find(EXISTING_PACKAGE_NAME).exists()).isTrue();
    }

    @Test
    public void of_returnsPackageWithCorrectPackageName() {
        assertThat(Package.of(PACKAGE_NAME).packageName()).isEqualTo(PACKAGE_NAME);
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunNotOnSecondaryUser
    public void installExisting_alreadyInstalled_installsInUser() {
        sInstrumentedPackage.installExisting(sDeviceState.secondaryUser());

        try {
            assertThat(sInstrumentedPackage.installedOnUser(sDeviceState.secondaryUser())).isTrue();
        } finally {
            sInstrumentedPackage.uninstall(sDeviceState.secondaryUser());
        }
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunNotOnSecondaryUser
    public void uninstallForAllUsers_isUninstalledForAllUsers() throws Exception {
        Package pkg = TestApis.packages().install(sTestAppApkFile);
        pkg.installExisting(sDeviceState.secondaryUser());

        pkg.uninstallFromAllUsers();

        assertThat(pkg.installedOnUsers()).isEmpty();
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunNotOnSecondaryUser
    public void uninstall_packageIsInstalledForDifferentUser_isUninstalledForUser()
            throws Exception {
        Package pkg = TestApis.packages().install(sTestAppApkFile);

        try {
            pkg.installExisting(sDeviceState.secondaryUser());

            pkg.uninstall(TestApis.users().instrumented());

            assertThat(sTestApp.pkg().installedOnUsers()).containsExactly(
                    sDeviceState.secondaryUser());
        } finally {
            pkg.uninstall(TestApis.users().instrumented());
            pkg.uninstall(sDeviceState.secondaryUser());
        }
    }

    @Test
    public void uninstall_packageIsUninstalled() throws Exception {
        Package pkg = TestApis.packages().install(sTestAppApkFile);

        pkg.uninstall(TestApis.users().instrumented());

        assertThat(sTestApp.pkg().installedOnUser(TestApis.users().instrumented())).isFalse();
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunNotOnSecondaryUser
    public void uninstall_packageNotInstalledForUser_doesNotThrowException() {
        TestApis.packages().install(sTestAppApkFile);

        try {
            sTestApp.pkg().uninstall(sDeviceState.secondaryUser());
        } finally {
            sTestApp.pkg().uninstallFromAllUsers();
        }
    }

    @Test
    public void uninstall_packageDoesNotExist_doesNotThrowException() {
        Package pkg = TestApis.packages().find(NON_EXISTING_PACKAGE_NAME);

        pkg.uninstall(sUser);
    }

    @Test
    public void grantPermission_installPermission_throwsException() {
        assertThrows(NeneException.class, () ->
                TestApis.packages().find(sContext.getPackageName()).grantPermission(sUser,
                        INSTALL_PERMISSION));
    }

    @Test
    public void grantPermission_nonDeclaredPermission_throwsException() {
        assertThrows(NeneException.class, () ->
                TestApis.packages().find(sContext.getPackageName()).grantPermission(sUser,
                        UNDECLARED_RUNTIME_PERMISSION));
    }

    @Test
    @EnsureHasSecondaryUser
    public void grantPermission_permissionIsGranted() {
        try (TestAppInstance instance = sTestApp.install()) {
            sTestApp.pkg().grantPermission(USER_SPECIFIC_PERMISSION);

            assertThat(sTestApp.pkg().hasPermission(USER_SPECIFIC_PERMISSION)).isTrue();
        }
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunNotOnSecondaryUser
    public void grantPermission_permissionIsUserSpecific_permissionIsGrantedOnlyForThatUser() {
        try (TestAppInstance instance1 = sTestApp.install();
             TestAppInstance instance2 = sTestApp.install(sDeviceState.secondaryUser())) {

            sTestApp.pkg().grantPermission(sDeviceState.secondaryUser(), USER_SPECIFIC_PERMISSION);

            assertThat(sTestApp.pkg().hasPermission(USER_SPECIFIC_PERMISSION)).isFalse();
            assertThat(sTestApp.pkg().hasPermission(sDeviceState.secondaryUser(),
                    USER_SPECIFIC_PERMISSION)).isTrue();
        }
    }

    @Test
    public void grantPermission_packageDoesNotExist_throwsException() {
        assertThrows(NeneException.class, () ->
                TestApis.packages().find(NON_EXISTING_PACKAGE_NAME).grantPermission(sUser,
                        DECLARED_RUNTIME_PERMISSION));
    }

    @Test
    public void grantPermission_permissionDoesNotExist_throwsException() {
        assertThrows(NeneException.class, () ->
                TestApis.packages().find(sContext.getPackageName()).grantPermission(sUser,
                        NON_EXISTING_PERMISSION));
    }

    @Test
    public void grantPermission_packageIsNotInstalledForUser_throwsException() {
        sTestApp.pkg().uninstall(TestApis.users().instrumented());

        assertThrows(NeneException.class,
                () -> sTestApp.pkg().grantPermission(DECLARED_RUNTIME_PERMISSION));
    }

    @Test
    @Ignore("Cannot be tested because all runtime permissions are granted by default")
    public void denyPermission_ownPackage_permissionIsNotGranted_doesNotThrowException() {
        Package pkg = TestApis.packages().find(sContext.getPackageName());

        pkg.denyPermission(sUser, USER_SPECIFIC_PERMISSION);
    }

    @Test
    public void denyPermission_ownPackage_permissionIsGranted_throwsException() {
        Package pkg = TestApis.packages().find(sContext.getPackageName());
        pkg.grantPermission(sUser, USER_SPECIFIC_PERMISSION);

        assertThrows(NeneException.class, () ->
                pkg.denyPermission(sUser, USER_SPECIFIC_PERMISSION));
    }

    @Test
    public void denyPermission_permissionIsNotGranted() {
        try (TestAppInstance instance = sTestApp.install()) {
            sTestApp.pkg().grantPermission(USER_SPECIFIC_PERMISSION);

            sTestApp.pkg().denyPermission(USER_SPECIFIC_PERMISSION);

            assertThat(sTestApp.pkg().hasPermission(USER_SPECIFIC_PERMISSION)).isFalse();
        }
    }

    @Test
    public void denyPermission_packageDoesNotExist_throwsException() {
        assertThrows(NeneException.class, () ->
                TestApis.packages().find(NON_EXISTING_PACKAGE_NAME).denyPermission(sUser,
                        DECLARED_RUNTIME_PERMISSION));
    }

    @Test
    public void denyPermission_permissionDoesNotExist_throwsException() {
        assertThrows(NeneException.class, () ->
                TestApis.packages().find(sContext.getPackageName()).denyPermission(sUser,
                        NON_EXISTING_PERMISSION));
    }

    @Test
    public void denyPermission_packageIsNotInstalledForUser_throwsException() {
        sTestApp.pkg().uninstall(TestApis.users().instrumented());

        assertThrows(NeneException.class,
                () -> sTestApp.pkg().denyPermission(DECLARED_RUNTIME_PERMISSION));
    }

    @Test
    public void denyPermission_installPermission_throwsException() {
        try (TestAppInstance instance = sTestApp.install()) {
            assertThrows(NeneException.class, () ->
                    sTestApp.pkg().denyPermission(INSTALL_PERMISSION));
        }
    }

    @Test
    public void denyPermission_nonDeclaredPermission_throwsException() {
        assertThrows(NeneException.class, () ->
                TestApis.packages().find(sContext.getPackageName()).denyPermission(sUser,
                        UNDECLARED_RUNTIME_PERMISSION));
    }

    @Test
    public void denyPermission_alreadyDenied_doesNothing() {
        try (TestAppInstance instance = sTestApp.install()) {
            sTestApp.pkg().denyPermission(USER_SPECIFIC_PERMISSION);
            sTestApp.pkg().denyPermission(USER_SPECIFIC_PERMISSION);

            assertThat(sTestApp.pkg().hasPermission(USER_SPECIFIC_PERMISSION)).isFalse();
        }
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunNotOnSecondaryUser
    public void denyPermission_permissionIsUserSpecific_permissionIsDeniedOnlyForThatUser() {
        try (TestAppInstance instance1 = sTestApp.install();
             TestAppInstance instance2 = sTestApp.install(sDeviceState.secondaryUser())) {
            sTestApp.pkg().grantPermission(USER_SPECIFIC_PERMISSION);
            sTestApp.pkg().grantPermission(sDeviceState.secondaryUser(), USER_SPECIFIC_PERMISSION);

            sTestApp.pkg().denyPermission(sDeviceState.secondaryUser(), USER_SPECIFIC_PERMISSION);

            assertThat(sTestApp.pkg().hasPermission(sDeviceState.secondaryUser(),
                    USER_SPECIFIC_PERMISSION)).isFalse();
            assertThat(sTestApp.pkg().hasPermission(USER_SPECIFIC_PERMISSION)).isTrue();
        }
    }

    @Test
    public void installedOnUsers_includesUserWithPackageInstalled() {
        TestApis.packages().install(sUser, sTestAppApkFile);
        Package pkg = TestApis.packages().find(sTestApp.packageName());

        try {
            assertThat(pkg.installedOnUsers()).contains(sUser);
        } finally {
            pkg.uninstall(sUser);
        }
    }

    @Test
    public void forceStop_whenRestartableApp_doesNotLoopEndlessly() {
        final int previousId = TestApis.packages().launcher().runningProcess().pid();
        TestApis.packages().launcher().forceStop();
        assertThat(TestApis.packages().launcher().runningProcess().pid()).isNotEqualTo(previousId);
    }

    @Test
    public void forceStop_whenNoRunningProcess_doesNotThrowException() {
        final Package notRunningPackage = TestApis.packages().installedForUser().stream()
                .filter(aPackage -> aPackage.runningProcess() == null)
                .findFirst()
                .get();
        assumeNotNull(notRunningPackage);

        notRunningPackage.forceStop();
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunNotOnSecondaryUser
    public void installedOnUsers_doesNotIncludeUserWithoutPackageInstalled() throws Exception {
        Package pkg = TestApis.packages().install(sTestAppApkFile);
        pkg.uninstall(sDeviceState.secondaryUser());

        try {
            assertThat(pkg.installedOnUsers()).doesNotContain(sDeviceState.secondaryUser());
        } finally {
            pkg.uninstall(TestApis.users().instrumented());
        }
    }

    @Test
    public void grantedPermission_includesKnownInstallPermission() {
        // TODO(scottjonathan): This relies on the fact that the instrumented app declares
        //  ACCESS_NETWORK_STATE - this should be replaced with TestApp with a useful query
        Package pkg = TestApis.packages().find(sContext.getPackageName());

        assertThat(pkg.hasPermission(sUser, ACCESS_NETWORK_STATE_PERMISSION)).isTrue();
    }
}
