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

import static android.app.admin.DevicePolicyManager.ACTION_UPDATE_DEVICE_POLICY_MANAGEMENT_ROLE_HOLDER;
import static android.app.admin.DevicePolicyManager.EXTRA_FORCE_UPDATE_ROLE_HOLDER;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TRIGGER;
import static android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_UNSPECIFIED;

import static java.util.Objects.requireNonNull;

import android.annotation.Nullable;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.provisioning.Constants;

import com.google.android.setupcompat.util.WizardManagerHelper;

/**
 * Helper class for logic related to device management role holder updater launching.
 */
public class DeviceManagementRoleHolderUpdaterHelper {

    private final String mRoleHolderUpdaterPackageName;
    private final String mRoleHolderPackageName;
    private final PackageInstallChecker mPackageInstallChecker;
    private final IntentResolverChecker mIntentResolverChecker;
    private final FeatureFlagChecker mFeatureFlagChecker;

    public DeviceManagementRoleHolderUpdaterHelper(
            @Nullable String roleHolderUpdaterPackageName,
            @Nullable String roleHolderPackageName,
            PackageInstallChecker packageInstallChecker,
            IntentResolverChecker intentResolverChecker,
            FeatureFlagChecker featureFlagChecker) {
        mRoleHolderUpdaterPackageName = roleHolderUpdaterPackageName;
        mRoleHolderPackageName = roleHolderPackageName;
        mPackageInstallChecker = requireNonNull(packageInstallChecker);
        mIntentResolverChecker = requireNonNull(intentResolverChecker);
        mFeatureFlagChecker = requireNonNull(featureFlagChecker);
    }

    /**
     * Returns whether the device management role holder updater should be started.
     */
    public boolean shouldStartRoleHolderUpdater(Context context, Intent managedProvisioningIntent,
            ProvisioningParams params) {
        if (!Constants.isRoleHolderProvisioningAllowedForAction(
                managedProvisioningIntent.getAction())) {
            ProvisionLogger.logi("Not starting role holder updater, because this provisioning "
                    + "action is unsupported: " + managedProvisioningIntent.getAction());
            return false;
        }
        if (shouldPlatformDownloadRoleHolder(managedProvisioningIntent, params)) {
            ProvisionLogger.logi("Not starting role holder updater, because the platform will "
                    + "download the role holder instead.");
            return false;
        }
        if (!mFeatureFlagChecker.canDelegateProvisioningToRoleHolder()) {
            ProvisionLogger.logi("Not starting role holder updater, because the feature flag "
                    + "is turned off.");
            return false;
        }
        if (TextUtils.isEmpty(mRoleHolderPackageName)) {
            ProvisionLogger.logi("Not starting role holder updater, because the role holder "
                    + "package name is null or empty.");
            return false;
        }
        if (TextUtils.isEmpty(mRoleHolderUpdaterPackageName)) {
            ProvisionLogger.logi("Not starting role holder updater, because the role holder "
                    + "updater package name is null or empty.");
            return false;
        }
        if (!mPackageInstallChecker.isPackageInstalled(mRoleHolderUpdaterPackageName)) {
            ProvisionLogger.logi("Not starting role holder updater, because the role holder "
                    + "updater is not installed.");
            return false;
        }
        Intent roleHolderUpdaterIntent = createRoleHolderUpdaterIntent(
                managedProvisioningIntent,
                managedProvisioningIntent.getIntExtra(
                        EXTRA_PROVISIONING_TRIGGER, PROVISIONING_TRIGGER_UNSPECIFIED),
                /* isRoleHolderRequestedUpdate= */ false);
        if (!mIntentResolverChecker.canResolveIntent(roleHolderUpdaterIntent)) {
            ProvisionLogger.logi("Not starting role holder updater, because the role holder "
                    + "updater does not resolve the intent " + roleHolderUpdaterIntent);
            return false;
        }
        return true;
    }


    /**
     * Returns {@code true} if the platform should download the role holder, instead of the
     * role holder updater.
     *
     * @see #shouldStartRoleHolderUpdater(Context, Intent, ProvisioningParams)
     */
    public boolean shouldPlatformDownloadRoleHolder(
            Intent managedProvisioningIntent, ProvisioningParams params) {
        if (params.roleHolderDownloadInfo == null) {
            ProvisionLogger.logi("Not performing platform-side role holder download, because "
                    + "there is no role holder download info supplied.");
            return false;
        }
        if (!DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE.equals(
                managedProvisioningIntent.getAction())) {
            ProvisionLogger.logi("Not performing platform-side role holder download, because "
                    + "this provisioning action is unsupported: "
                    + managedProvisioningIntent.getAction());
            return false;
        }
        if (!mFeatureFlagChecker.canDelegateProvisioningToRoleHolder()) {
            ProvisionLogger.logi("Not performing platform-side role holder download, because "
                    + "the feature flag is turned off.");
            return false;
        }
        if (TextUtils.isEmpty(mRoleHolderPackageName)) {
            ProvisionLogger.logi("Not performing platform-side role holder download, because "
                    + "the role holder package name is null or empty.");
            return false;
        }
        return true;
    }

    /**
     * Creates an intent to be used to launch the role holder updater.
     */
    public Intent createRoleHolderUpdaterIntent(
            @Nullable Intent parentActivityIntent,
            int provisioningTrigger,
            boolean isRoleHolderRequestedUpdate) {
        if (TextUtils.isEmpty(mRoleHolderUpdaterPackageName)) {
            throw new IllegalStateException("Role holder updater package name is null or empty.");
        }
        Intent intent = new Intent(ACTION_UPDATE_DEVICE_POLICY_MANAGEMENT_ROLE_HOLDER)
                .setPackage(mRoleHolderUpdaterPackageName)
                .putExtra(EXTRA_PROVISIONING_TRIGGER, provisioningTrigger)
                .putExtra(EXTRA_FORCE_UPDATE_ROLE_HOLDER, isRoleHolderRequestedUpdate);
        if (parentActivityIntent != null) {
            WizardManagerHelper.copyWizardManagerExtras(parentActivityIntent, intent);
        }
        return intent;
    }

    public boolean isRoleHolderUpdaterDefined() {
        return !TextUtils.isEmpty(mRoleHolderUpdaterPackageName);
    }
}
