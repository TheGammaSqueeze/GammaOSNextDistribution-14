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

package com.android.managedprovisioning.common;

import static android.app.admin.DevicePolicyManager.EXTRA_FORCE_UPDATE_ROLE_HOLDER;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TRIGGER;

import static com.android.managedprovisioning.TestUtils.assertIntentsEqual;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.model.PackageDownloadInfo;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@SmallTest
@RunWith(JUnit4.class)
public class DeviceManagementRoleHolderUpdaterHelperTest {

    private static final String ROLE_HOLDER_UPDATER_PACKAGE_NAME = "com.test.updater.package";
    private static final String ROLE_HOLDER_PACKAGE_NAME = "com.test.roleholder.package";
    private static final String ROLE_HOLDER_UPDATER_EMPTY_PACKAGE_NAME = "";
    private static final String ROLE_HOLDER_UPDATER_NULL_PACKAGE_NAME = null;
    private static final String ROLE_HOLDER_EMPTY_PACKAGE_NAME = "";
    private static final String ROLE_HOLDER_NULL_PACKAGE_NAME = null;
    private static final int TEST_PROVISIONING_TRIGGER =
            DevicePolicyManager.PROVISIONING_TRIGGER_QR_CODE;
    private static final Intent ROLE_HOLDER_UPDATER_INTENT =
            new Intent(DevicePolicyManager.ACTION_UPDATE_DEVICE_POLICY_MANAGEMENT_ROLE_HOLDER)
                    .setPackage(ROLE_HOLDER_UPDATER_PACKAGE_NAME)
                    .putExtra(EXTRA_PROVISIONING_TRIGGER, TEST_PROVISIONING_TRIGGER)
                    .putExtra(EXTRA_FORCE_UPDATE_ROLE_HOLDER, false);
    private static final Intent ROLE_HOLDER_UPDATER_INTENT_WITH_FORCE_UPDATE =
            new Intent(DevicePolicyManager.ACTION_UPDATE_DEVICE_POLICY_MANAGEMENT_ROLE_HOLDER)
                    .setPackage(ROLE_HOLDER_UPDATER_PACKAGE_NAME)
                    .putExtra(EXTRA_FORCE_UPDATE_ROLE_HOLDER, true)
                    .putExtra(EXTRA_PROVISIONING_TRIGGER, TEST_PROVISIONING_TRIGGER);
    public static final String TEST_EXTRA_KEY = "test_extra_key";
    public static final String TEST_EXTRA_VALUE = "test_extra_value";
    private static final Intent MANAGED_PROFILE_INTENT =
            new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
                    .putExtra(TEST_EXTRA_KEY, TEST_EXTRA_VALUE);
    private static final Intent FINANCED_DEVICE_INTENT =
            new Intent(DevicePolicyManager.ACTION_PROVISION_FINANCED_DEVICE)
                    .putExtra(TEST_EXTRA_KEY, TEST_EXTRA_VALUE);
    private static final Intent PROVISION_TRUSTED_SOURCE_INTENT =
            new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                    .putExtra(TEST_EXTRA_KEY, TEST_EXTRA_VALUE);
    private static final Intent MANAGED_PROVISIONING_INTENT = MANAGED_PROFILE_INTENT;
    private static final ComponentName ADMIN = new ComponentName("com.test.admin", ".Receiver");
    private static final ProvisioningParams PARAMS = ProvisioningParams.Builder.builder()
            .setProvisioningAction(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(ADMIN)
            .build();
    private static final String TEST_DOWNLOAD_LOCATION =
            "http://example/dpc.apk";
    private static final String TEST_COOKIE_HEADER =
            "Set-Cookie: sessionToken=foobar; Expires=Thu, 18 Feb 2016 23:59:59 GMT";
    private static final byte[] TEST_SIGNATURE_CHECKSUM = new byte[] { '5', '4', '3', '2', '1' };
    private static final PackageDownloadInfo ROLE_HOLDER_DOWNLOAD_INFO =
            PackageDownloadInfo.Builder.builder()
                    .setLocation(TEST_DOWNLOAD_LOCATION)
                    .setSignatureChecksum(TEST_SIGNATURE_CHECKSUM)
                    .setCookieHeader(TEST_COOKIE_HEADER)
                    .build();
    private static final ProvisioningParams PARAMS_WITH_ROLE_HOLDER_DOWNLOAD_INFO =
            ProvisioningParams.Builder.builder()
                    .setProvisioningAction(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
                    .setDeviceAdminComponentName(ADMIN)
                    .setRoleHolderDownloadInfo(ROLE_HOLDER_DOWNLOAD_INFO)
                    .build();

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private boolean mCanDelegateProvisioningToRoleHolder;

    @Before
    public void setUp() {
        enableRoleHolderDelegation();
    }

    @Test
    public void roleHolderHelperConstructor_roleHolderPackageNameNull_noExceptionThrown() {
        createRoleHolderUpdaterHelperWithUpdaterPackageName(ROLE_HOLDER_UPDATER_NULL_PACKAGE_NAME);
    }

    @Test
    public void roleHolderHelperConstructor_roleHolderPackageNameEmpty_noExceptionThrown() {
        createRoleHolderUpdaterHelperWithUpdaterPackageName(ROLE_HOLDER_UPDATER_EMPTY_PACKAGE_NAME);
    }

    @Test
    public void shouldStartRoleHolderUpdater_works() {
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelper();

        assertThat(roleHolderUpdaterHelper.shouldStartRoleHolderUpdater(
                mContext, MANAGED_PROVISIONING_INTENT, PARAMS)).isTrue();
    }

    @Test
    public void shouldStartRoleHolderUpdater_managedProfileIntent_works() {
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelper();

        assertThat(roleHolderUpdaterHelper.shouldStartRoleHolderUpdater(
                mContext, MANAGED_PROFILE_INTENT, PARAMS)).isTrue();
    }

    @Test
    public void shouldStartRoleHolderUpdater_trustedSourceIntent_works() {
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelper();

        assertThat(roleHolderUpdaterHelper.shouldStartRoleHolderUpdater(
                mContext, PROVISION_TRUSTED_SOURCE_INTENT, PARAMS)).isTrue();
    }

    @Test
    public void shouldStartRoleHolderUpdater_financedDeviceIntent_returnsFalse() {
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelper();

        assertThat(roleHolderUpdaterHelper.shouldStartRoleHolderUpdater(
                mContext, FINANCED_DEVICE_INTENT, PARAMS)).isFalse();
    }

    @Test
    public void shouldStartRoleHolderUpdater_nullRoleHolderPackageName_returnsFalse() {
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelperWithRoleHolderPackageName(
                        ROLE_HOLDER_NULL_PACKAGE_NAME);

        assertThat(roleHolderUpdaterHelper.shouldStartRoleHolderUpdater(
                mContext, MANAGED_PROVISIONING_INTENT, PARAMS)).isFalse();
    }

    @Test
    public void shouldStartRoleHolderUpdater_emptyRoleHolderPackageName_returnsFalse() {
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelperWithRoleHolderPackageName(
                        ROLE_HOLDER_EMPTY_PACKAGE_NAME);

        assertThat(roleHolderUpdaterHelper.shouldStartRoleHolderUpdater(
                mContext, MANAGED_PROVISIONING_INTENT, PARAMS)).isFalse();
    }

    @Test
    public void shouldStartRoleHolderUpdater_nullRoleHolderUpdaterPackageName_returnsFalse() {
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelperWithUpdaterPackageName(
                        ROLE_HOLDER_UPDATER_NULL_PACKAGE_NAME);

        assertThat(roleHolderUpdaterHelper.shouldStartRoleHolderUpdater(
                mContext, MANAGED_PROVISIONING_INTENT, PARAMS)).isFalse();
    }

    @Test
    public void shouldStartRoleHolderUpdater_emptyRoleHolderUpdaterPackageName_returnsFalse() {
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelperWithUpdaterPackageName(
                        ROLE_HOLDER_UPDATER_EMPTY_PACKAGE_NAME);

        assertThat(roleHolderUpdaterHelper.shouldStartRoleHolderUpdater(
                mContext, MANAGED_PROVISIONING_INTENT, PARAMS)).isFalse();
    }

    @Test
    public void shouldStartRoleHolderUpdater_roleHolderDelegationDisabled_returnsFalse() {
        disableRoleHolderDelegation();
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelper();

        assertThat(roleHolderUpdaterHelper.shouldStartRoleHolderUpdater(
                mContext, MANAGED_PROVISIONING_INTENT, PARAMS)).isFalse();
    }

    @Test
    public void shouldStartRoleHolderUpdater_roleHolderUpdaterNotInstalled_returnsFalse() {
        disableRoleHolderDelegation();
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelperWithUpdaterNotInstalled();

        assertThat(roleHolderUpdaterHelper.shouldStartRoleHolderUpdater(
                mContext, MANAGED_PROVISIONING_INTENT, PARAMS)).isFalse();
    }

    @Test
    public void shouldStartRoleHolderUpdater_roleHolderUpdaterNotResolvable_returnsFalse() {
        disableRoleHolderDelegation();
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelperWithUpdaterNotResolvable();

        assertThat(roleHolderUpdaterHelper.shouldStartRoleHolderUpdater(
                mContext, MANAGED_PROVISIONING_INTENT, PARAMS)).isFalse();
    }

    @Test
    public void shouldStartRoleHolderUpdater_withRoleHolderDownloadInfo_returnsFalse() {
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelper();

        assertThat(roleHolderUpdaterHelper.shouldStartRoleHolderUpdater(
                mContext, PROVISION_TRUSTED_SOURCE_INTENT,
                PARAMS_WITH_ROLE_HOLDER_DOWNLOAD_INFO)).isFalse();
    }

    @Test
    public void createRoleHolderUpdaterIntent_works() {
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelper();

        assertIntentsEqual(
                roleHolderUpdaterHelper.createRoleHolderUpdaterIntent(
                        /* parentActivityIntent= */ null,
                        TEST_PROVISIONING_TRIGGER,
                        /* isRoleHolderRequestedUpdate= */ false),
                ROLE_HOLDER_UPDATER_INTENT);
    }

    @Test
    public void createRoleHolderUpdaterIntent_withForceUpdate_works() {
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelper();

        assertIntentsEqual(
                roleHolderUpdaterHelper.createRoleHolderUpdaterIntent(
                        /* parentActivityIntent= */ null,
                        TEST_PROVISIONING_TRIGGER,
                        /* isRoleHolderRequestedUpdate= */ true),
                ROLE_HOLDER_UPDATER_INTENT_WITH_FORCE_UPDATE);
    }

    @Test
    public void createRoleHolderUpdaterIntent_nullRoleHolderUpdaterPackageName_throwsException() {
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelperWithUpdaterPackageName(
                        ROLE_HOLDER_UPDATER_NULL_PACKAGE_NAME);

        assertThrows(IllegalStateException.class,
                () -> roleHolderUpdaterHelper.createRoleHolderUpdaterIntent(
                        /* parentActivityIntent= */ null,
                        TEST_PROVISIONING_TRIGGER,
                        /* isRoleHolderRequestedUpdate= */ false));
    }

    @Test
    public void createRoleHolderUpdaterIntent_emptyRoleHolderUpdaterPackageName_throwsException() {
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelperWithUpdaterPackageName(
                        ROLE_HOLDER_UPDATER_EMPTY_PACKAGE_NAME);

        assertThrows(IllegalStateException.class,
                () -> roleHolderUpdaterHelper.createRoleHolderUpdaterIntent(
                        /* parentActivityIntent= */ null,
                        TEST_PROVISIONING_TRIGGER,
                        /* isRoleHolderRequestedUpdate= */ false));
    }

    @Test
    public void shouldPlatformDownloadRoleHolder_works() {
        enableRoleHolderDelegation();
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelper();

        assertThat(roleHolderUpdaterHelper
                .shouldPlatformDownloadRoleHolder(
                        PROVISION_TRUSTED_SOURCE_INTENT,
                        PARAMS_WITH_ROLE_HOLDER_DOWNLOAD_INFO)).isTrue();
    }

    @Test
    public void shouldPlatformDownloadRoleHolder_noRoleHolderDownloadInfoSupplied_returnsFalse() {
        enableRoleHolderDelegation();
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelper();

        assertThat(roleHolderUpdaterHelper
                .shouldPlatformDownloadRoleHolder(PROVISION_TRUSTED_SOURCE_INTENT, PARAMS))
                        .isFalse();
    }

    @Test
    public void shouldPlatformDownloadRoleHolder_managedProfileIntent_returnsFalse() {
        enableRoleHolderDelegation();
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelper();

        assertThat(roleHolderUpdaterHelper
                .shouldPlatformDownloadRoleHolder(
                        MANAGED_PROFILE_INTENT,
                        PARAMS_WITH_ROLE_HOLDER_DOWNLOAD_INFO)).isFalse();
    }

    @Test
    public void shouldPlatformDownloadRoleHolder_financedDeviceIntent_returnsFalse() {
        enableRoleHolderDelegation();
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelper();

        assertThat(roleHolderUpdaterHelper
                .shouldPlatformDownloadRoleHolder(
                        FINANCED_DEVICE_INTENT,
                        PARAMS_WITH_ROLE_HOLDER_DOWNLOAD_INFO)).isFalse();
    }

    @Test
    public void shouldPlatformDownloadRoleHolder_featureFlagDisabled_returnsFalse() {
        disableRoleHolderDelegation();
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelper();

        assertThat(roleHolderUpdaterHelper
                .shouldPlatformDownloadRoleHolder(
                        PROVISION_TRUSTED_SOURCE_INTENT,
                        PARAMS_WITH_ROLE_HOLDER_DOWNLOAD_INFO)).isFalse();
    }

    @Test
    public void shouldPlatformDownloadRoleHolder_emptyRoleHolderPackage_returnsFalse() {
        disableRoleHolderDelegation();
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelperWithRoleHolderPackageName(
                        ROLE_HOLDER_EMPTY_PACKAGE_NAME);

        assertThat(roleHolderUpdaterHelper
                .shouldPlatformDownloadRoleHolder(
                        PROVISION_TRUSTED_SOURCE_INTENT,
                        PARAMS_WITH_ROLE_HOLDER_DOWNLOAD_INFO)).isFalse();
    }

    @Test
    public void shouldPlatformDownloadRoleHolder_nullRoleHolderPackage_returnsFalse() {
        disableRoleHolderDelegation();
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelperWithRoleHolderPackageName(
                        ROLE_HOLDER_NULL_PACKAGE_NAME);

        assertThat(roleHolderUpdaterHelper
                .shouldPlatformDownloadRoleHolder(
                        PROVISION_TRUSTED_SOURCE_INTENT,
                        PARAMS_WITH_ROLE_HOLDER_DOWNLOAD_INFO)).isFalse();
    }

    @Test
    public void isRoleHolderUpdaterDefined_actuallyDefined_returnsTrue() {
        enableRoleHolderDelegation();
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelper();

        assertThat(roleHolderUpdaterHelper.isRoleHolderUpdaterDefined()).isTrue();
    }

    @Test
    public void isRoleHolderUpdaterDefined_actuallyNotDefined_returnsFalse() {
        enableRoleHolderDelegation();
        DeviceManagementRoleHolderUpdaterHelper roleHolderUpdaterHelper =
                createRoleHolderUpdaterHelperWithUpdaterPackageName(
                        ROLE_HOLDER_UPDATER_EMPTY_PACKAGE_NAME);

        assertThat(roleHolderUpdaterHelper.isRoleHolderUpdaterDefined()).isFalse();
    }

    private FeatureFlagChecker createFeatureFlagChecker() {
        return () -> mCanDelegateProvisioningToRoleHolder;
    }

    private DeviceManagementRoleHolderUpdaterHelper
    createRoleHolderUpdaterHelperWithUpdaterPackageName(
            String packageName) {
        return new DeviceManagementRoleHolderUpdaterHelper(
                packageName,
                ROLE_HOLDER_PACKAGE_NAME,
                /* packageInstallChecker= */ (roleHolderPackageName) -> true,
                /* intentResolverChecker= */ (intent) -> true,
                createFeatureFlagChecker());
    }

    private DeviceManagementRoleHolderUpdaterHelper
    createRoleHolderUpdaterHelperWithRoleHolderPackageName(
            String roleHolderPackageName) {
        return new DeviceManagementRoleHolderUpdaterHelper(
                ROLE_HOLDER_UPDATER_PACKAGE_NAME,
                roleHolderPackageName,
                /* packageInstallChecker= */ (packageName) -> true,
                /* intentResolverChecker= */ (intent) -> true,
                createFeatureFlagChecker());
    }

    private DeviceManagementRoleHolderUpdaterHelper createRoleHolderUpdaterHelper() {
        return new DeviceManagementRoleHolderUpdaterHelper(
                ROLE_HOLDER_UPDATER_PACKAGE_NAME,
                ROLE_HOLDER_PACKAGE_NAME,
                /* packageInstallChecker= */ (roleHolderPackageName) -> true,
                /* intentResolverChecker= */ (intent) -> true,
                createFeatureFlagChecker());
    }

    private DeviceManagementRoleHolderUpdaterHelper
            createRoleHolderUpdaterHelperWithUpdaterNotInstalled() {
        return new DeviceManagementRoleHolderUpdaterHelper(
                ROLE_HOLDER_UPDATER_PACKAGE_NAME,
                ROLE_HOLDER_PACKAGE_NAME,
                /* packageInstallChecker= */ (roleHolderPackageName) -> false,
                /* intentResolverChecker= */ (intent) -> true,
                createFeatureFlagChecker());
    }

    private DeviceManagementRoleHolderUpdaterHelper
            createRoleHolderUpdaterHelperWithUpdaterNotResolvable() {
        return new DeviceManagementRoleHolderUpdaterHelper(
                ROLE_HOLDER_UPDATER_PACKAGE_NAME,
                ROLE_HOLDER_PACKAGE_NAME,
                /* packageInstallChecker= */ (roleHolderPackageName) -> true,
                /* intentResolverChecker= */ (intent) -> false,
                createFeatureFlagChecker());
    }

    private void enableRoleHolderDelegation() {
        mCanDelegateProvisioningToRoleHolder = true;
    }

    private void disableRoleHolderDelegation() {
        mCanDelegateProvisioningToRoleHolder = false;
    }
}
