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

import static android.app.admin.DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_FINALIZATION;
import static android.app.admin.DevicePolicyManager.EXTRA_ROLE_HOLDER_PROVISIONING_INITIATOR_PACKAGE;
import static android.app.admin.DevicePolicyManager.EXTRA_ROLE_HOLDER_STATE;

import static java.util.Objects.requireNonNull;

import android.app.admin.DevicePolicyManager;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.provisioning.Constants;

import com.google.android.setupcompat.util.WizardManagerHelper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Helper class for logic related to device management role holder launching.
 */
public final class DeviceManagementRoleHolderHelper {
    private static final Map<String, String> sManagedProvisioningToRoleHolderIntentAction =
            createManagedProvisioningToRoleHolderIntentActionMap();

    @Nullable
    private final String mRoleHolderPackageName;
    private final PackageInstallChecker mPackageInstallChecker;
    private final ResolveIntentChecker mResolveIntentChecker;
    private final RoleHolderStubChecker mRoleHolderStubChecker;
    private final FeatureFlagChecker mFeatureFlagChecker;
    private final RoleGranter mRoleGranter;

    public DeviceManagementRoleHolderHelper(
            @Nullable String roleHolderPackageName,
            PackageInstallChecker packageInstallChecker,
            ResolveIntentChecker resolveIntentChecker,
            RoleHolderStubChecker roleHolderStubChecker,
            FeatureFlagChecker featureFlagChecker) {
        this(
                roleHolderPackageName, packageInstallChecker, resolveIntentChecker,
                roleHolderStubChecker, featureFlagChecker,
                (context, user, roleName, packageName, callback) -> {
                    var mRoleManager = requireNonNull(context.getSystemService(RoleManager.class),
                            "Unable to obtain RoleManager");
                    if (mRoleManager.getRoleHoldersAsUser(roleName, user).contains(packageName)) {
                        ProvisionLogger.logi(roleName + " role is already granted to " + packageName
                                + " package on user " + user);
                        callback.accept(true);
                    } else {
                        ProvisionLogger.logi("Granting " + roleName + " role to " + packageName
                                + " package on user " + user);
                        mRoleManager.addRoleHolderAsUser(roleName, packageName, /* flags= */ 0,
                                user, context.getMainExecutor(), callback);
                    }
                });
    }

    @VisibleForTesting
    public DeviceManagementRoleHolderHelper(
            @Nullable String roleHolderPackageName,
            PackageInstallChecker packageInstallChecker,
            ResolveIntentChecker resolveIntentChecker,
            RoleHolderStubChecker roleHolderStubChecker,
            FeatureFlagChecker featureFlagChecker,
            RoleGranter roleGranter) {
        mRoleHolderPackageName = roleHolderPackageName;
        mPackageInstallChecker = requireNonNull(packageInstallChecker);
        mResolveIntentChecker = requireNonNull(resolveIntentChecker);
        mRoleHolderStubChecker = requireNonNull(roleHolderStubChecker);
        mFeatureFlagChecker = requireNonNull(featureFlagChecker);
        mRoleGranter = requireNonNull(roleGranter);
    }

    /**
     * Ensures that {@link RoleManager#ROLE_DEVICE_POLICY_MANAGEMENT} role is granted to
     * {@link UserHandle#USER_SYSTEM}
     *
     * @param callback to invoke with role grant status
     */
    public void ensureRoleGranted(
            Context context,
            Consumer<Boolean> callback) {
        var packageName = requireNonNull(mRoleHolderPackageName,
                "Unable to ensure role grant: mRoleHolderPackageName unspecified");
        mRoleGranter.ensureRoleGranted(context, UserHandle.of(UserHandle.USER_SYSTEM),
                RoleManager.ROLE_DEVICE_POLICY_MANAGEMENT, packageName, callback);
    }

    /**
     * Returns whether the device management role holder is able to carry out the provisioning flow.
     *
     * <p>If this method returns {@code false}, then provisioning should be carried out by AOSP
     * ManagedProvisioning instead.
     *
     * <p>If the device management role holder is a stub, it must be updated to the full device
     * management role holder app via the device management role holder updater prior to carrying
     * out provisioning.
     */
    public boolean isRoleHolderReadyForProvisioning(
            Context context, Intent managedProvisioningIntent) {
        requireNonNull(context);
        if (!mFeatureFlagChecker.canDelegateProvisioningToRoleHolder()) {
            ProvisionLogger.logi("Cannot delegate provisioning to the role holder, because "
                    + "the feature flag is turned off.");
            return false;
        }
        if (!Constants.isRoleHolderProvisioningAllowedForAction(
                managedProvisioningIntent.getAction())) {
            ProvisionLogger.logi("Cannot delegate provisioning to the role holder, because "
                    + "intent action " + managedProvisioningIntent.getAction() + " is not "
                    + "supported by the role holder.");
            return false;
        }
        if (!isRoleHolderPresent(mRoleHolderPackageName, context.getPackageManager())) {
            ProvisionLogger.logi("Cannot delegate provisioning to the role holder, because "
                    + "the role holder is not installed.");
            return false;
        }
        if (mRoleHolderStubChecker
                .isRoleHolderStub(mRoleHolderPackageName, context.getPackageManager())) {
            ProvisionLogger.logi("Cannot delegate provisioning to the role holder, because "
                    + "the role holder is a stub.");
            return false;
        }
        boolean roleHolderValid =
                isRoleHolderValid(mRoleHolderPackageName, context.getPackageManager());
        if (!roleHolderValid) {
            ProvisionLogger.logi("Cannot delegate provisioning to the role holder, because "
                    + "the role holder is not valid.");
            return false;
        }
        return true;
    }

    /**
     * Returns a new intent with an equivalent intent action with which AOSP ManagedProvisioning
     * was started with, which starts the device management role holder.
     * <p>
     * For example, if AOSP ManagedProvisioning was started with {@link
     * DevicePolicyManager#ACTION_PROVISION_MANAGED_PROFILE}, then the resulting intent of this call
     * will return an intent with action {@link DevicePolicyManager
     * #ACTION_ROLE_HOLDER_PROVISION_MANAGED_PROFILE}.
     *
     * @see DevicePolicyManager#ACTION_ROLE_HOLDER_PROVISION_MANAGED_PROFILE
     * @see DevicePolicyManager#ACTION_ROLE_HOLDER_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE
     */
    public Intent createRoleHolderProvisioningIntent(
            Intent managedProvisioningIntent,
            Bundle roleHolderAdditionalExtras,
            @Nullable String callingPackage,
            @Nullable PersistableBundle roleHolderState) {
        requireNonNull(managedProvisioningIntent);
        requireNonNull(roleHolderAdditionalExtras);
        String provisioningAction = managedProvisioningIntent.getAction();
        if (!Constants.isRoleHolderProvisioningAllowedForAction(provisioningAction)) {
            throw new IllegalArgumentException("Intent action " + provisioningAction
                    + " is not a valid provisioning action.");
        }
        if (TextUtils.isEmpty(mRoleHolderPackageName)) {
            throw new IllegalStateException("Role holder package name is null or empty.");
        }
        String action = sManagedProvisioningToRoleHolderIntentAction.get(provisioningAction);
        Intent roleHolderIntent = new Intent(action);
        if (managedProvisioningIntent.getExtras() != null) {
            roleHolderIntent.putExtras(managedProvisioningIntent.getExtras());
        }
        roleHolderIntent.setPackage(mRoleHolderPackageName);
        if (roleHolderState != null) {
            roleHolderIntent.putExtra(EXTRA_ROLE_HOLDER_STATE, roleHolderState);
        }
        if (callingPackage != null) {
            roleHolderIntent.putExtra(
                    EXTRA_ROLE_HOLDER_PROVISIONING_INITIATOR_PACKAGE, callingPackage);
        }
        roleHolderIntent.putExtras(roleHolderAdditionalExtras);
        WizardManagerHelper.copyWizardManagerExtras(managedProvisioningIntent, roleHolderIntent);
        return roleHolderIntent;
    }

    /**
     * Returns a new intent which starts the device management role holder finalization.
     */
    public Intent createRoleHolderFinalizationIntent(@Nullable Intent parentActivityIntent) {
        if (TextUtils.isEmpty(mRoleHolderPackageName)) {
            throw new IllegalStateException("Role holder package name is null or empty.");
        }
        Intent roleHolderIntent = new Intent(ACTION_ROLE_HOLDER_PROVISION_FINALIZATION);
        roleHolderIntent.setPackage(mRoleHolderPackageName);
        if (parentActivityIntent != null) {
            WizardManagerHelper.copyWizardManagerExtras(parentActivityIntent, roleHolderIntent);
        }
        return roleHolderIntent;
    }

    /**
     * Returns {@code true} if role holder-driven provisioning is enabled.
     *
     * <p>For role holder-driven provisioning to be enabled, the following criteria must be
     * met:
     * <ul>
     *     <li>The role holder package name must be a non-null, non-empty {@link String}</li>
     *     <li>The role holder-driven provisioning feature flag must be enabled</li>
     * </ul>
     */
    public boolean isRoleHolderProvisioningEnabled() {
        return !TextUtils.isEmpty(mRoleHolderPackageName)
                && mFeatureFlagChecker.canDelegateProvisioningToRoleHolder();
    }

    private boolean isRoleHolderValid(
            @Nullable String roleHolderPackageName,
            PackageManager packageManager) {
        Collection<String> requiredRoleHolderIntentActions =
                sManagedProvisioningToRoleHolderIntentAction.values();
        List<String> unhandledRequiredActions = requiredRoleHolderIntentActions.parallelStream()
                .filter(action -> !canResolveIntent(packageManager, roleHolderPackageName, action))
                .collect(Collectors.toList());
        if (!unhandledRequiredActions.isEmpty()) {
            ProvisionLogger.logi("Role holder validation failed. Role holder does not implement "
                    + "the following required intents: "
                    + String.join(", ", unhandledRequiredActions));
            return false;
        }
        return true;
    }

    private boolean canResolveIntent(
            PackageManager packageManager,
            String roleHolderPackageName,
            String requiredAction) {
        Intent intent = new Intent(requiredAction);
        intent.setPackage(roleHolderPackageName);
        return mResolveIntentChecker.canResolveIntent(intent, packageManager);
    }

    private boolean isRoleHolderPresent(
            @Nullable String roleHolderPackageName,
            PackageManager packageManager) {
        return !TextUtils.isEmpty(roleHolderPackageName)
                && mPackageInstallChecker.isPackageInstalled(roleHolderPackageName);
    }

    private static Map<String, String> createManagedProvisioningToRoleHolderIntentActionMap() {
        return Map.of(
                DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE,
                DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE,
                DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE,
                DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_MANAGED_PROFILE,
                DevicePolicyManager.ACTION_PROVISION_FINALIZATION,
                ACTION_ROLE_HOLDER_PROVISION_FINALIZATION);
    }

    /**
     * Checker that checks whether an {@link Intent} can be resolved.
     */
    public interface ResolveIntentChecker {
        /**
         * Returns {@code true} if {@code intent} can be resolved.
         */
        boolean canResolveIntent(Intent intent, PackageManager packageManager);
    }

    /**
     * Default implementation of {@link ResolveIntentChecker}.
     */
    public static final class DefaultResolveIntentChecker implements ResolveIntentChecker {
        @Override
        public boolean canResolveIntent(Intent intent, PackageManager packageManager) {
            return intent.resolveActivity(packageManager) != null;
        }
    }

    /**
     * Checker that checks whether the role holder is a stub.
     */
    public interface RoleHolderStubChecker {
        /**
         * Returns {@code true} if the role holder with package {@code packageName} is a stub.
         */
        boolean isRoleHolderStub(@Nullable String packageName, PackageManager packageManager);
    }

    /**
     * Default implementation of {@link RoleHolderStubChecker}.
     */
    public static final class DefaultRoleHolderStubChecker implements RoleHolderStubChecker {
        @Override
        public boolean isRoleHolderStub(@Nullable String packageName,
                PackageManager packageManager) {
            // TODO(b/207377785): Add check for whether the role holder is a stub
            return false;
        }
    }
}
