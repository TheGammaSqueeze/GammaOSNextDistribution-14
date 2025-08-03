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

package android.healthconnect.tests.permissions;

import static android.health.connect.HealthPermissions.MANAGE_HEALTH_PERMISSIONS;
import static android.healthconnect.cts.utils.TestUtils.deleteAllStagedRemoteData;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.health.connect.HealthConnectManager;
import android.health.connect.HealthPermissions;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;
import android.healthconnect.tests.IntegrationTestUtils;
import android.os.Build;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Integration tests for {@link HealthConnectManager} Permission-related APIs.
 *
 * <p><b>Note:</b> These tests operate while holding {@link
 * HealthPermissions.MANAGE_HEALTH_PERMISSIONS}. For tests asserting that non-holders of the
 * permission cannot call the APIs, please see {@link
 * android.healthconnect.tests.withoutmanagepermissions.HealthConnectWithoutManagePermissionsTest}.
 *
 * <p><b>Note:</b> Since we need to hold the aforementioned permission, this test needs to be signed
 * with the same certificate as the HealthConnect module. Therefore, <b>we skip this test when it
 * cannot hold {@link HealthPermissions.MANAGE_HEALTH_PERMISSIONS}. The primary use of these tests
 * is therefore during development, when we are building from source rather than using
 * prebuilts</b>. Additionally, this test can run as a presubmit on the main (master) branch where
 * modules are always built from source.
 *
 * <p><b>Build/Install/Run:</b> {@code atest HealthFitnessIntegrationTests}.
 */
@RunWith(AndroidJUnit4.class)
public class HealthConnectWithManagePermissionsTest {
    private static final String DEFAULT_APP_PACKAGE = "android.healthconnect.test.app";
    private static final String NO_USAGE_INTENT_APP_PACKAGE = "android.healthconnect.test.app2";
    private static final String INEXISTENT_APP_PACKAGE = "my.invalid.package.name";
    private static final String DEFAULT_PERM = HealthPermissions.READ_ACTIVE_CALORIES_BURNED;
    private static final String DEFAULT_PERM_2 = HealthPermissions.WRITE_ACTIVE_CALORIES_BURNED;
    private static final String UNDECLARED_PERM = HealthPermissions.READ_DISTANCE;
    private static final String INVALID_PERM = "android.permission.health.MY_INVALID_PERM";
    private static final String NON_HEALTH_PERM = Manifest.permission.ACCESS_COARSE_LOCATION;

    private Context mContext;
    private HealthConnectManager mHealthConnectManager;

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mHealthConnectManager = mContext.getSystemService(HealthConnectManager.class);

        revokePermissionViaPackageManager(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        revokePermissionViaPackageManager(DEFAULT_APP_PACKAGE, DEFAULT_PERM_2);
        resetPermissionFlags(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        resetPermissionFlags(DEFAULT_APP_PACKAGE, DEFAULT_PERM_2);
        assertPermNotGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        assertPermNotGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM_2);
        deleteAllStagedRemoteData();
    }

    @After
    public void tearDown() {
        deleteAllStagedRemoteData();
    }

    @Test
    public void testGrantHealthPermission_appHasPermissionDeclared_success() throws Exception {
        grantHealthPermission(DEFAULT_APP_PACKAGE, DEFAULT_PERM);

        assertPermGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
    }

    @Test
    public void testGrantHealthPermission_appHasPermissionDeclared_flagUserSetEnabled()
            throws Exception {
        grantHealthPermission(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        Map<String, Integer> permissionsFlags =
                getHealthPermissionsFlags(DEFAULT_APP_PACKAGE, List.of(DEFAULT_PERM));

        assertPermGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        assertFlagsSet(permissionsFlags.get(DEFAULT_PERM), PackageManager.FLAG_PERMISSION_USER_SET);
        assertFlagsNotSet(
                permissionsFlags.get(DEFAULT_PERM), PackageManager.FLAG_PERMISSION_USER_FIXED);
    }

    @Test
    public void testGrantHealthPermission_revokeTwiceThenGrant_flagUserSetEnabled()
            throws Exception {
        revokeHealthPermission(DEFAULT_APP_PACKAGE, DEFAULT_PERM, /* reason= */ null);
        revokeHealthPermission(DEFAULT_APP_PACKAGE, DEFAULT_PERM, /* reason= */ null);
        Map<String, Integer> permissionsFlags =
                getHealthPermissionsFlags(DEFAULT_APP_PACKAGE, List.of(DEFAULT_PERM));

        assertPermNotGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        assertFlagsSet(
                permissionsFlags.get(DEFAULT_PERM), PackageManager.FLAG_PERMISSION_USER_FIXED);

        grantHealthPermission(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        permissionsFlags = getHealthPermissionsFlags(DEFAULT_APP_PACKAGE, List.of(DEFAULT_PERM));

        assertFlagsSet(permissionsFlags.get(DEFAULT_PERM), PackageManager.FLAG_PERMISSION_USER_SET);
        assertFlagsNotSet(
                permissionsFlags.get(DEFAULT_PERM), PackageManager.FLAG_PERMISSION_USER_FIXED);
    }

    @Test(expected = SecurityException.class)
    public void testGrantHealthPermission_usageIntentNotSupported_throwsIllegalArgumentException()
            throws Exception {
        grantHealthPermission(NO_USAGE_INTENT_APP_PACKAGE, DEFAULT_PERM);
        fail("Expected SecurityException due to undeclared health permissions usage intent.");
    }

    @Test
    public void testGrantHealthPermission_permissionAlreadyGranted_success() throws Exception {
        grantHealthPermission(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        assertPermGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        // Let's regrant it
        grantHealthPermission(DEFAULT_APP_PACKAGE, DEFAULT_PERM);

        assertPermGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
    }

    @Test
    public void testGrantHealthPermission_appHasPermissionNotDeclared_notGranted()
            throws Exception {
        try {
            grantHealthPermission(DEFAULT_APP_PACKAGE, UNDECLARED_PERM);
        } catch (SecurityException e) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // 1) On V and above, this situation should not result in an exception
                // 2) On U, it may result in an exception prior to b/322033581.
                // This test currently ensures that if it throws on V (thus going against (1)),
                // we will propagate the exception to fail the test, as expected.
                throw e;
            }
        }
        assertPermNotGrantedForApp(DEFAULT_APP_PACKAGE, UNDECLARED_PERM);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGrantHealthPermission_invalidPermission_throwsIllegalArgumentException()
            throws Exception {
        grantHealthPermission(DEFAULT_APP_PACKAGE, INVALID_PERM);
        fail("Expected IllegalArgumentException due to invalid permission.");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGrantHealthPermission_nonHealthPermission_throwsIllegalArgumentException()
            throws Exception {
        grantHealthPermission(DEFAULT_APP_PACKAGE, NON_HEALTH_PERM);
        fail("Expected IllegalArgumentException due to non-health permission.");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGrantHealthPermission_invalidPackageName_throwsIllegalArgumentException()
            throws Exception {
        grantHealthPermission(INEXISTENT_APP_PACKAGE, DEFAULT_PERM);
        fail("Expected IllegalArgumentException due to invalid package.");
    }

    @Test(expected = NullPointerException.class)
    public void testGrantHealthPermission_nullPermission_throwsNPE() throws Exception {
        grantHealthPermission(DEFAULT_APP_PACKAGE, /* permissionName= */ null);
        fail("Expected NullPointerException due to null permission.");
    }

    @Test(expected = NullPointerException.class)
    public void testGrantHealthPermission_nullPackageName_throwsNPE() throws Exception {
        grantHealthPermission(/* packageName= */ null, DEFAULT_PERM);
        fail("Expected NullPointerException due to null package.");
    }

    @Test
    public void testRevokeHealthPermission_appHasPermissionGranted_success() throws Exception {
        grantPermissionViaPackageManager(DEFAULT_APP_PACKAGE, DEFAULT_PERM);

        revokeHealthPermission(DEFAULT_APP_PACKAGE, DEFAULT_PERM, /* reason= */ null);

        assertPermNotGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
    }

    @Test
    public void testRevokeHealthPermission_firstRevoke_flagUserSetEnabled() throws Exception {
        revokeHealthPermission(DEFAULT_APP_PACKAGE, DEFAULT_PERM, /* reason= */ null);
        Map<String, Integer> permissionsFlags =
                getHealthPermissionsFlags(DEFAULT_APP_PACKAGE, List.of(DEFAULT_PERM));

        assertPermNotGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        assertFlagsSet(permissionsFlags.get(DEFAULT_PERM), PackageManager.FLAG_PERMISSION_USER_SET);
        assertFlagsNotSet(
                permissionsFlags.get(DEFAULT_PERM), PackageManager.FLAG_PERMISSION_USER_FIXED);
    }

    @Test
    public void testRevokeHealthPermission_grantThenRevoke_flagUserSetEnabled() throws Exception {
        grantHealthPermission(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        revokeHealthPermission(DEFAULT_APP_PACKAGE, DEFAULT_PERM, /* reason= */ null);
        Map<String, Integer> permissionsFlags =
                getHealthPermissionsFlags(DEFAULT_APP_PACKAGE, List.of(DEFAULT_PERM));

        assertPermNotGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        assertFlagsSet(permissionsFlags.get(DEFAULT_PERM), PackageManager.FLAG_PERMISSION_USER_SET);
        assertFlagsNotSet(
                permissionsFlags.get(DEFAULT_PERM), PackageManager.FLAG_PERMISSION_USER_FIXED);
    }

    @Test
    public void testRevokeHealthPermission_secondRevoke_flagUserFixedEnabled() throws Exception {
        revokeHealthPermission(DEFAULT_APP_PACKAGE, DEFAULT_PERM, /* reason= */ null);
        revokeHealthPermission(DEFAULT_APP_PACKAGE, DEFAULT_PERM, /* reason= */ null);
        Map<String, Integer> permissionsFlags =
                getHealthPermissionsFlags(DEFAULT_APP_PACKAGE, List.of(DEFAULT_PERM));

        assertPermNotGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        assertFlagsSet(
                permissionsFlags.get(DEFAULT_PERM), PackageManager.FLAG_PERMISSION_USER_FIXED);
    }

    @Test
    public void testRevokeHealthPermission_success() throws Exception {
        revokeHealthPermission(DEFAULT_APP_PACKAGE, DEFAULT_PERM, /* reason= */ null);

        assertPermNotGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRevokeHealthPermission_invalidPermission_throwsIllegalArgumentException()
            throws Exception {
        revokeHealthPermission(DEFAULT_APP_PACKAGE, INVALID_PERM, /* reason= */ null);
        fail("Expected IllegalArgumentException due to invalid permission.");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRevokeHealthPermission_nonHealthPermission_throwsIllegalArgumentException()
            throws Exception {
        revokeHealthPermission(DEFAULT_APP_PACKAGE, NON_HEALTH_PERM, /* reason= */ null);
        fail("Expected IllegalArgumentException due to non-health permission.");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRevokeHealthPermission_invalidPackageName_throwsIllegalArgumentException()
            throws Exception {
        revokeHealthPermission(INEXISTENT_APP_PACKAGE, DEFAULT_PERM, /* reason= */ null);
        fail("Expected IllegalArgumentException due to invalid package.");
    }

    @Test(expected = NullPointerException.class)
    public void testRevokeHealthPermission_nullPermission_throwsNPE() throws Exception {
        revokeHealthPermission(DEFAULT_APP_PACKAGE, /* permissionName= */ null, /* reason= */ null);
        fail("Expected NullPointerException due to null permission.");
    }

    @Test(expected = NullPointerException.class)
    public void testRevokeHealthPermission_nullPackageName_throwsNPE() throws Exception {
        revokeHealthPermission(/* packageName= */ null, DEFAULT_PERM, /* reason= */ null);
        fail("Expected NullPointerException due to null package.");
    }

    @Test
    public void testGetGrantedHealthPermissions_appHasNoPermissionGranted_emptyList()
            throws Exception {
        List<String> grantedPerms = getGrantedHealthPermissions(DEFAULT_APP_PACKAGE);

        assertThat(grantedPerms.size()).isEqualTo(0);
    }

    @Test
    public void testGetGrantedHealthPermissions_appHasPermissionsGranted_success()
            throws Exception {
        grantPermissionViaPackageManager(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        grantPermissionViaPackageManager(DEFAULT_APP_PACKAGE, DEFAULT_PERM_2);

        List<String> grantedPerms = getGrantedHealthPermissions(DEFAULT_APP_PACKAGE);

        assertThat(grantedPerms)
                .containsExactlyElementsIn(
                        Arrays.asList(new String[] {DEFAULT_PERM, DEFAULT_PERM_2}));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetGrantedHealthPermissions_invalidPackageName_throwsIllegalArgumentException()
            throws Exception {
        getGrantedHealthPermissions(INEXISTENT_APP_PACKAGE);
        fail("Expected IllegalArgumentException due to invalid package.");
    }

    @Test(expected = NullPointerException.class)
    public void testGetGrantedHealthPermissions_nullPackageName_throwsNPE() throws Exception {
        getGrantedHealthPermissions(/* packageName= */ null);
        fail("Expected NullPointerException due to null package.");
    }

    @Test
    public void testGetHealthPermissionsFlags_returnsFlags() {
        int permFlags =
                PackageManager.FLAG_PERMISSION_USER_SET
                        | PackageManager.FLAG_PERMISSION_AUTO_REVOKED;

        int perm2Flags = PackageManager.FLAG_PERMISSION_USER_FIXED;
        updatePermissionsFlagsViaPackageManager(DEFAULT_APP_PACKAGE, DEFAULT_PERM, permFlags);
        updatePermissionsFlagsViaPackageManager(DEFAULT_APP_PACKAGE, DEFAULT_PERM_2, perm2Flags);

        Map<String, Integer> permissionsFlags =
                getHealthPermissionsFlags(
                        DEFAULT_APP_PACKAGE, List.of(DEFAULT_PERM, DEFAULT_PERM_2));

        assertThat(permissionsFlags.keySet()).containsExactly(DEFAULT_PERM, DEFAULT_PERM_2);
        assertFlagsSet(permissionsFlags.get(DEFAULT_PERM), permFlags);
        assertFlagsSet(permissionsFlags.get(DEFAULT_PERM_2), perm2Flags);
    }

    @Test
    public void testGetHealthPermissionsFlags_invalidPermission_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        getHealthPermissionsFlags(
                                DEFAULT_APP_PACKAGE,
                                List.of(INVALID_PERM, DEFAULT_PERM, DEFAULT_PERM_2)));
    }

    @Test
    public void testGetHealthPermissionsFlags_nonHealthPermission_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        getHealthPermissionsFlags(
                                DEFAULT_APP_PACKAGE,
                                List.of(DEFAULT_PERM, NON_HEALTH_PERM, DEFAULT_PERM_2)));
    }

    @Test
    public void testGetHealthPermissionsFlags_undeclaredPermissions_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        getHealthPermissionsFlags(
                                DEFAULT_APP_PACKAGE,
                                List.of(DEFAULT_PERM, DEFAULT_PERM_2, UNDECLARED_PERM)));
    }

    @Test
    public void testGetHealthPermissionsFlags_emptyPermissions_returnsEmptyMap() {
        assertThat(getHealthPermissionsFlags(DEFAULT_APP_PACKAGE, List.of())).isEmpty();
    }

    @Test
    public void testGetHealthPermissionsFlags_invalidPackage_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        getHealthPermissionsFlags(
                                INEXISTENT_APP_PACKAGE, List.of(DEFAULT_PERM, DEFAULT_PERM)));
    }

    @Test
    public void testGetHealthPermissionsFlags_nullPackage_throwsException() {
        assertThrows(
                NullPointerException.class,
                () -> getHealthPermissionsFlags(null, List.of(DEFAULT_PERM, DEFAULT_PERM)));
    }

    @Test
    public void testGetHealthPermissionsFlags_nullPermissions_throwsException() {
        assertThrows(
                NullPointerException.class,
                () -> getHealthPermissionsFlags(DEFAULT_APP_PACKAGE, null));
    }

    @Test
    public void testSetHealthPermissionsUserFixedFlagValue_false_resetsUserFixedFlagToZero() {
        updatePermissionsFlagsViaPackageManager(
                DEFAULT_APP_PACKAGE,
                DEFAULT_PERM,
                PackageManager.FLAG_PERMISSION_USER_SET
                        | PackageManager.FLAG_PERMISSION_USER_FIXED
                        | PackageManager.FLAG_PERMISSION_AUTO_REVOKED);
        int permFlags = getPermissionsFlagsViaPackageManager(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        updatePermissionsFlagsViaPackageManager(
                DEFAULT_APP_PACKAGE, DEFAULT_PERM_2, PackageManager.FLAG_PERMISSION_USER_FIXED);
        int perm2Flags = getPermissionsFlagsViaPackageManager(DEFAULT_APP_PACKAGE, DEFAULT_PERM_2);

        setHealthPermissionsUserFixedFlagValue(
                DEFAULT_APP_PACKAGE, List.of(DEFAULT_PERM, DEFAULT_PERM_2), false);

        assertThat(getPermissionsFlagsViaPackageManager(DEFAULT_APP_PACKAGE, DEFAULT_PERM))
                .isEqualTo(permFlags & ~PackageManager.FLAG_PERMISSION_USER_FIXED);
        assertThat(getPermissionsFlagsViaPackageManager(DEFAULT_APP_PACKAGE, DEFAULT_PERM_2))
                .isEqualTo(perm2Flags & ~PackageManager.FLAG_PERMISSION_USER_FIXED);
    }

    @Test
    public void testSetHealthPermissionsUserFixedFlagValue_true_setsUserFixedFlag() {
        updatePermissionsFlagsViaPackageManager(
                DEFAULT_APP_PACKAGE,
                DEFAULT_PERM,
                PackageManager.FLAG_PERMISSION_USER_SET
                        | PackageManager.FLAG_PERMISSION_AUTO_REVOKED);
        int permFlags = getPermissionsFlagsViaPackageManager(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        updatePermissionsFlagsViaPackageManager(DEFAULT_APP_PACKAGE, DEFAULT_PERM_2, 0);
        int perm2Flags = getPermissionsFlagsViaPackageManager(DEFAULT_APP_PACKAGE, DEFAULT_PERM_2);

        setHealthPermissionsUserFixedFlagValue(
                DEFAULT_APP_PACKAGE, List.of(DEFAULT_PERM, DEFAULT_PERM_2), true);

        assertThat(getPermissionsFlagsViaPackageManager(DEFAULT_APP_PACKAGE, DEFAULT_PERM))
                .isEqualTo(permFlags | PackageManager.FLAG_PERMISSION_USER_FIXED);
        assertThat(getPermissionsFlagsViaPackageManager(DEFAULT_APP_PACKAGE, DEFAULT_PERM_2))
                .isEqualTo(perm2Flags | PackageManager.FLAG_PERMISSION_USER_FIXED);
    }

    @Test
    public void testSetHealthPermissionsUserFixedFlagValue_invalidPermission_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        setHealthPermissionsUserFixedFlagValue(
                                DEFAULT_APP_PACKAGE,
                                List.of(INVALID_PERM, DEFAULT_PERM, DEFAULT_PERM_2),
                                false));
    }

    @Test
    public void testSetHealthPermissionsUserFixedFlagValue_nonHealthPermission_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        setHealthPermissionsUserFixedFlagValue(
                                DEFAULT_APP_PACKAGE,
                                List.of(DEFAULT_PERM, NON_HEALTH_PERM, DEFAULT_PERM_2),
                                true));
    }

    @Test
    public void testSetHealthPermissionsUserFixedFlagValue_undeclaredPermissions_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        setHealthPermissionsUserFixedFlagValue(
                                DEFAULT_APP_PACKAGE,
                                List.of(DEFAULT_PERM, DEFAULT_PERM_2, UNDECLARED_PERM),
                                false));
    }

    @Test
    public void testSetHealthPermissionsUserFixedFlagValue_emptyPermissions_doesNotThrow() {
        setHealthPermissionsUserFixedFlagValue(DEFAULT_APP_PACKAGE, List.of(), true);
    }

    @Test
    public void testSetHealthPermissionsUserFixedFlagValue_invalidPackage_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        setHealthPermissionsUserFixedFlagValue(
                                INEXISTENT_APP_PACKAGE,
                                List.of(DEFAULT_PERM, DEFAULT_PERM),
                                false));
    }

    @Test
    public void testSetHealthPermissionsUserFixedFlagValue_nullPackage_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        setHealthPermissionsUserFixedFlagValue(
                                null, List.of(DEFAULT_PERM, DEFAULT_PERM), true));
    }

    @Test
    public void testSetHealthPermissionsUserFixedFlagValue_nullPermissions_throwsException() {
        assertThrows(
                NullPointerException.class,
                () -> setHealthPermissionsUserFixedFlagValue(DEFAULT_APP_PACKAGE, null, false));
    }

    @Test
    public void testRevokeAllHealthPermissions_appHasNoPermissionsGranted_success()
            throws Exception {
        revokeAllHealthPermissions(DEFAULT_APP_PACKAGE, /* reason= */ null);

        assertThat(getGrantedHealthPermissions(DEFAULT_APP_PACKAGE).size()).isEqualTo(0);
        assertPermNotGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        assertPermNotGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM_2);
    }

    @Test
    public void testRevokeAllHealthPermissions_appHasPermissionsGranted_success() throws Exception {
        grantPermissionViaPackageManager(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        grantPermissionViaPackageManager(DEFAULT_APP_PACKAGE, DEFAULT_PERM_2);

        revokeAllHealthPermissions(DEFAULT_APP_PACKAGE, /* reason= */ null);

        assertThat(getGrantedHealthPermissions(DEFAULT_APP_PACKAGE).size()).isEqualTo(0);
        assertPermNotGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        assertPermNotGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM_2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRevokeAllHealthPermissions_invalidPackageName_throwsIllegalArgumentException()
            throws Exception {
        revokeAllHealthPermissions(INEXISTENT_APP_PACKAGE, /* reason= */ null);
        fail("Expected IllegalArgumentException due to invalid package.");
    }

    @Test(expected = NullPointerException.class)
    public void testRevokeAllHealthPermissions_nullPackageName_throwsNPE() throws Exception {
        revokeAllHealthPermissions(/* packageName= */ null, /* reason= */ null);
        fail("Expected NullPointerException due to null package.");
    }

    @Test
    public void testPermissionApis_migrationInProgress_apisBlocked() throws Exception {
        runWithShellPermissionIdentity(
                IntegrationTestUtils::startMigration,
                Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA);

        // Grant permission
        assertPermNotGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        try {
            grantHealthPermission(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
            fail("Expected IllegalStateException for data sync in progress.");
        } catch (IllegalStateException exception) {
            assertNotNull(exception);
        }
        assertPermNotGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        deleteAllStagedRemoteData();

        // Revoke permission
        runWithShellPermissionIdentity(
                IntegrationTestUtils::startMigration,
                Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA);

        grantPermissionViaPackageManager(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        assertPermGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        try {
            revokeHealthPermission(DEFAULT_APP_PACKAGE, DEFAULT_PERM, /* reason= */ null);
            fail("Expected IllegalStateException for data sync in progress.");
        } catch (IllegalStateException exception) {
            assertNotNull(exception);
        }
        assertPermGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
        try {
            revokeAllHealthPermissions(DEFAULT_APP_PACKAGE, /* reason= */ null);
            fail("Expected IllegalStateException for data sync in progress.");
        } catch (IllegalStateException exception) {
            assertNotNull(exception);
        }
        assertPermGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM);

        // getGrantedHealthPermissions
        try {
            assertThat(getGrantedHealthPermissions(DEFAULT_APP_PACKAGE)).isEmpty();
            fail("Expected IllegalStateException for data sync in progress.");
        } catch (IllegalStateException exception) {
            assertNotNull(exception);
        }

        assertThrows(
                IllegalStateException.class,
                () ->
                        getHealthPermissionsFlags(
                                DEFAULT_APP_PACKAGE, List.of(DEFAULT_PERM, DEFAULT_PERM_2)));

        assertThrows(
                IllegalStateException.class,
                () ->
                        setHealthPermissionsUserFixedFlagValue(
                                DEFAULT_APP_PACKAGE, List.of(DEFAULT_PERM, DEFAULT_PERM_2), false));

        runWithShellPermissionIdentity(
                IntegrationTestUtils::finishMigration,
                Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA);
        assertPermGrantedForApp(DEFAULT_APP_PACKAGE, DEFAULT_PERM);
    }

    private void grantPermissionViaPackageManager(String packageName, String permName) {
        runWithShellPermissionIdentity(
                () ->
                        mContext.getPackageManager()
                                .grantRuntimePermission(packageName, permName, mContext.getUser()),
                Manifest.permission.GRANT_RUNTIME_PERMISSIONS);
    }

    private void revokePermissionViaPackageManager(String packageName, String permName) {
        runWithShellPermissionIdentity(
                () ->
                        mContext.getPackageManager()
                                .revokeRuntimePermission(
                                        packageName,
                                        permName,
                                        mContext.getUser(),
                                        /* reason= */ null),
                Manifest.permission.REVOKE_RUNTIME_PERMISSIONS);
    }

    private int getPermissionsFlagsViaPackageManager(String packageName, String permName) {
        return runWithShellPermissionIdentity(
                () ->
                        mContext.getPackageManager()
                                .getPermissionFlags(permName, packageName, mContext.getUser()),
                Manifest.permission.GRANT_RUNTIME_PERMISSIONS);
    }

    private void resetPermissionFlags(String packageName, String permName) {
        updatePermissionsFlagsViaPackageManager(packageName, permName, /* flags= */ 0);
    }

    private void updatePermissionsFlagsViaPackageManager(
            String packageName, String permName, int flags) {
        int mask =
                PackageManager.FLAG_PERMISSION_USER_SET
                        | PackageManager.FLAG_PERMISSION_USER_FIXED
                        | PackageManager.FLAG_PERMISSION_AUTO_REVOKED;

        runWithShellPermissionIdentity(
                () ->
                        mContext.getPackageManager()
                                .updatePermissionFlags(
                                        permName, packageName, mask, flags, mContext.getUser()),
                Manifest.permission.GRANT_RUNTIME_PERMISSIONS);
    }

    private void assertPermGrantedForApp(String packageName, String permName) {
        assertThat(mContext.getPackageManager().checkPermission(permName, packageName))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    private void assertPermNotGrantedForApp(String packageName, String permName) {
        assertThat(mContext.getPackageManager().checkPermission(permName, packageName))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    private void grantHealthPermission(String packageName, String permissionName) {
        try {
            runWithShellPermissionIdentity(
                    () -> {
                        mHealthConnectManager.grantHealthPermission(packageName, permissionName);
                    },
                    MANAGE_HEALTH_PERMISSIONS);
        } catch (RuntimeException e) {
            // runWithShellPermissionIdentity wraps and rethrows all exceptions as RuntimeException,
            // but we need the original RuntimeException if there is one.
            final Throwable cause = e.getCause();
            throw cause instanceof RuntimeException ? (RuntimeException) cause : e;
        }
    }

    private void revokeHealthPermission(String packageName, String permissionName, String reason) {
        try {
            runWithShellPermissionIdentity(
                    () -> {
                        mHealthConnectManager.revokeHealthPermission(
                                packageName, permissionName, reason);
                    },
                    MANAGE_HEALTH_PERMISSIONS);
        } catch (RuntimeException e) {
            // runWithShellPermissionIdentity wraps and rethrows all exceptions as RuntimeException,
            // but we need the original RuntimeException if there is one.
            final Throwable cause = e.getCause();
            throw cause instanceof RuntimeException ? (RuntimeException) cause : e;
        }
    }

    private void revokeAllHealthPermissions(String packageName, String reason) {
        try {
            runWithShellPermissionIdentity(
                    () -> {
                        mHealthConnectManager.revokeAllHealthPermissions(packageName, reason);
                    },
                    MANAGE_HEALTH_PERMISSIONS);
        } catch (RuntimeException e) {
            // runWithShellPermissionIdentity wraps and rethrows all exceptions as RuntimeException,
            // but we need the original RuntimeException if there is one.
            final Throwable cause = e.getCause();
            throw cause instanceof RuntimeException ? (RuntimeException) cause : e;
        }
    }

    private List<String> getGrantedHealthPermissions(String packageName) {
        try {
            return runWithShellPermissionIdentity(
                    () -> {
                        return mHealthConnectManager.getGrantedHealthPermissions(packageName);
                    },
                    MANAGE_HEALTH_PERMISSIONS);
        } catch (RuntimeException e) {
            // runWithShellPermissionIdentity wraps and rethrows all exceptions as RuntimeException,
            // but we need the original RuntimeException if there is one.
            final Throwable cause = e.getCause();
            throw cause instanceof RuntimeException ? (RuntimeException) cause : e;
        }
    }

    private Map<String, Integer> getHealthPermissionsFlags(
            String packageName, List<String> permissions) {
        try {
            return runWithShellPermissionIdentity(
                    () -> mHealthConnectManager.getHealthPermissionsFlags(packageName, permissions),
                    MANAGE_HEALTH_PERMISSIONS);
        } catch (RuntimeException e) {
            // runWithShellPermissionIdentity wraps and rethrows all exceptions as RuntimeException,
            // but we need the original RuntimeException if there is one.
            final Throwable cause = e.getCause();
            throw cause instanceof RuntimeException ? (RuntimeException) cause : e;
        }
    }

    private void setHealthPermissionsUserFixedFlagValue(
            String packageName, List<String> permissions, boolean value) {
        try {
            runWithShellPermissionIdentity(
                    () ->
                            mHealthConnectManager.setHealthPermissionsUserFixedFlagValue(
                                    packageName, permissions, value),
                    MANAGE_HEALTH_PERMISSIONS);
        } catch (RuntimeException e) {
            // runWithShellPermissionIdentity wraps and rethrows all exceptions as RuntimeException,
            // but we need the original RuntimeException if there is one.
            final Throwable cause = e.getCause();
            throw cause instanceof RuntimeException ? (RuntimeException) cause : e;
        }
    }

    private static void assertFlagsSet(int actualFlags, int expectedFlags) {
        assertThat((actualFlags & expectedFlags)).isEqualTo(expectedFlags);
    }

    private static void assertFlagsNotSet(int actualFlags, int expectedFlagsNotSet) {
        assertThat((actualFlags & expectedFlagsNotSet)).isEqualTo(0);
    }
}
