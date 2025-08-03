/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.app.admin.DevicePolicyManager.ACTION_GET_PROVISIONING_MODE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_FINANCED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.FLAG_SUPPORTED_MODES_DEVICE_OWNER;
import static android.app.admin.DevicePolicyManager.FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED;
import static android.app.admin.DevicePolicyManager.FLAG_SUPPORTED_MODES_PERSONALLY_OWNED;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.Shadows.shadowOf;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;

import androidx.test.core.app.ApplicationProvider;

import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class UtilsRoboTest {
    private static final String TEST_MDM_PACKAGE_NAME = "mdm.package.name";
    private static final String TEST_MDM_ADMIN_RECEIVER = TEST_MDM_PACKAGE_NAME + ".AdminReceiver";
    private static final ComponentName TEST_MDM_ADMIN = new ComponentName(TEST_MDM_PACKAGE_NAME,
            TEST_MDM_ADMIN_RECEIVER);
    private static final ProvisioningParams PARAMS_ORG_OWNED = createTrustedSourceParamsBuilder()
            .setIsOrganizationOwnedProvisioning(true)
            .build();
    private static final ProvisioningParams PARAMS_PROVISION_MANAGED_PROFILE =
            ProvisioningParams.Builder.builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(TEST_MDM_ADMIN)
            .setStartedByTrustedSource(false)
            .build();
    private static final ProvisioningParams PARAMS_PROVISION_FINANCED_DEVICE =
            ProvisioningParams.Builder.builder()
                    .setProvisioningAction(ACTION_PROVISION_FINANCED_DEVICE)
                    .setDeviceAdminComponentName(TEST_MDM_ADMIN)
                    .setStartedByTrustedSource(false)
                    .build();
    private static final ProvisioningParams PARAMS_NON_TRUSTED_SOURCE =
            PARAMS_PROVISION_MANAGED_PROFILE;

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private Utils mUtils = new Utils();
    private PolicyComplianceUtils mPolicyComplianceUtils = new PolicyComplianceUtils();
    private GetProvisioningModeUtils mGetProvisioningModeUtils = new GetProvisioningModeUtils();

    @Test
    public void shouldPerformAdminIntegratedFlow_allConditionsMet_returnsTrue() {
        Intent policyComplianceIntent = getPolicyComplianceIntent();
        Intent getProvisioningModeIntent = getGetProvisioningModeIntent();
        ResolveInfo info = createFakeResolveInfo();
        shadowOf(mContext.getPackageManager())
                .addResolveInfoForIntent(policyComplianceIntent, info);
        shadowOf(mContext.getPackageManager())
                .addResolveInfoForIntent(getProvisioningModeIntent, info);

        assertThat(mUtils.canPerformAdminIntegratedFlow(mContext, PARAMS_ORG_OWNED,
                mPolicyComplianceUtils, mGetProvisioningModeUtils)).isTrue();
    }

    @Test
    public void shouldPerformAdminIntegratedFlow_noPolicyComplianceScreen_returnsFalse() {
        Intent getProvisioningModeIntent = getGetProvisioningModeIntent();
        ResolveInfo info = createFakeResolveInfo();
        shadowOf(mContext.getPackageManager())
                .addResolveInfoForIntent(getProvisioningModeIntent, info);

        assertThat(mUtils.canPerformAdminIntegratedFlow(mContext, PARAMS_ORG_OWNED,
                mPolicyComplianceUtils, mGetProvisioningModeUtils)).isFalse();
    }

    @Test
    public void shouldPerformAdminIntegratedFlow_noGetProvisioningModeScreen_returnsFalse() {
        Intent policyComplianceIntent = getPolicyComplianceIntent();
        ResolveInfo info = createFakeResolveInfo();
        shadowOf(mContext.getPackageManager())
                .addResolveInfoForIntent(policyComplianceIntent, info);

        assertThat(mUtils.canPerformAdminIntegratedFlow(mContext, PARAMS_ORG_OWNED,
                mPolicyComplianceUtils, mGetProvisioningModeUtils)).isFalse();
    }

    @Test
    public void checkAdminIntegratedFlowPreconditions_notStartedByTrustedSource_returnsFalse() {
        assertThat(mUtils.checkAdminIntegratedFlowPreconditions(
                PARAMS_NON_TRUSTED_SOURCE)).isFalse();
    }

    @Test
    public void checkAdminIntegratedFlowPreconditions_financedDevice_returnsFalse() {
        assertThat(mUtils.checkAdminIntegratedFlowPreconditions(
                PARAMS_PROVISION_FINANCED_DEVICE)).isFalse();
    }

    @Test
    public void isOrganizationOwnedAllowed_personallyOwnedProvisioning_returnsFalse() {
        ProvisioningParams params = createTrustedSourceParamsBuilder()
                .setInitiatorRequestedProvisioningModes(
                        FLAG_SUPPORTED_MODES_PERSONALLY_OWNED)
                .build();

        assertThat(mUtils.isOrganizationOwnedAllowed(params)).isFalse();
    }

    @Test
    public void isOrganizationOwnedAllowed_organizationOwnedProvisioning_returnsTrue() {
        ProvisioningParams params = createTrustedSourceParamsBuilder()
                .setInitiatorRequestedProvisioningModes(
                        FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED)
                .build();

        assertThat(mUtils.isOrganizationOwnedAllowed(params)).isTrue();
    }

    @Test
    public void
            isOrganizationOwnedAllowed_organizationAndPersonallyOwnedProvisioning_returnsTrue() {
        ProvisioningParams params = createTrustedSourceParamsBuilder()
                .setInitiatorRequestedProvisioningModes(
                        FLAG_SUPPORTED_MODES_ORGANIZATION_OWNED
                                | FLAG_SUPPORTED_MODES_PERSONALLY_OWNED)
                .build();

        assertThat(mUtils.isOrganizationOwnedAllowed(params)).isTrue();
    }

    @Test
    public void isOrganizationOwnedAllowed_deviceOwnerProvisioning_returnsTrue() {
        ProvisioningParams params = createTrustedSourceParamsBuilder()
                .setInitiatorRequestedProvisioningModes(
                        FLAG_SUPPORTED_MODES_DEVICE_OWNER)
                .build();

        assertThat(mUtils.isOrganizationOwnedAllowed(params)).isTrue();
    }

    @Test
    public void shouldShowOwnershipDisclaimerScreen_skipOwnershipDisclaimerFalse_returnsTrue() {
        ProvisioningParams params = createTrustedSourceParamsBuilder()
                .setSkipOwnershipDisclaimer(false)
                .build();

        assertThat(mUtils.shouldShowOwnershipDisclaimerScreen(params)).isTrue();
    }

    @Test
    public void shouldShowOwnershipDisclaimerScreen_showOwnershipDisclaimerTrue_returnsFalse() {
        ProvisioningParams params = createTrustedSourceParamsBuilder()
                .setSkipOwnershipDisclaimer(true)
                .build();

        assertThat(mUtils.shouldShowOwnershipDisclaimerScreen(params)).isFalse();
    }

    @Test
    public void isPackageInstalled_packageExists_returnsTrue() {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.packageName = "com.example.package";
        shadowOf(mContext.getPackageManager()).installPackage(packageInfo);

        assertThat(mUtils
                .isPackageInstalled("com.example.package", mContext.getPackageManager())).isTrue();
    }

    @Test
    public void isPackageInstalled_packageDoesNotExist_returnsFalse() {
        assertThat(mUtils
                .isPackageInstalled("com.example.package", mContext.getPackageManager())).isFalse();
    }

    private static ProvisioningParams.Builder createTrustedSourceParamsBuilder() {
        return ProvisioningParams.Builder.builder()
                .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                .setDeviceAdminComponentName(TEST_MDM_ADMIN)
                .setStartedByTrustedSource(true);
    }

    private Intent getGetProvisioningModeIntent() {
        final Intent intentGetMode = new Intent(ACTION_GET_PROVISIONING_MODE);
        intentGetMode.setPackage(TEST_MDM_PACKAGE_NAME);
        return intentGetMode;
    }

    private Intent getPolicyComplianceIntent() {
        Intent policyComplianceIntent =
                new Intent(DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE);
        policyComplianceIntent.setPackage(TEST_MDM_PACKAGE_NAME);
        return policyComplianceIntent;
    }

    private ResolveInfo createFakeResolveInfo() {
        ResolveInfo info = new ResolveInfo();
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = TEST_MDM_PACKAGE_NAME;
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.applicationInfo = applicationInfo;
        activityInfo.name = "SomeClassName";
        info.activityInfo = activityInfo;
        return info;
    }
}
