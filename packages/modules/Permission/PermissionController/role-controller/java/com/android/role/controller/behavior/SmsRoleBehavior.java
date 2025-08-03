/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.role.controller.behavior;

import android.app.admin.DevicePolicyManager;
import android.app.admin.ManagedSubscriptionsPolicy;
import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.modules.utils.build.SdkLevel;
import com.android.role.controller.model.Permissions;
import com.android.role.controller.model.Role;
import com.android.role.controller.model.RoleBehavior;
import com.android.role.controller.model.VisibilityMixin;
import com.android.role.controller.util.CollectionUtils;
import com.android.role.controller.util.PackageUtils;
import com.android.role.controller.util.UserUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Class for behavior of the SMS role.
 *
 * @see com.android.settings.applications.DefaultAppSettings
 * @see com.android.settings.applications.defaultapps.DefaultSmsPreferenceController
 * @see com.android.settings.applications.defaultapps.DefaultSmsPicker
 */
public class SmsRoleBehavior implements RoleBehavior {

    /**
     * Permissions to be granted if the application fulfilling the SMS role is also a system app.
     */
    private static final List<String> SYSTEM_SMS_PERMISSIONS = Arrays.asList(
            android.Manifest.permission.PERFORM_IMS_SINGLE_REGISTRATION,
            android.Manifest.permission.ACCESS_RCS_USER_CAPABILITY_EXCHANGE
    );

    @Override
    public boolean isAvailableAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        if (SdkLevel.isAtLeastU()) {
            if (UserUtils.isCloneProfile(user, context)) {
                return false;
            }

            // If work profile telephony is not enabled, there is no reason for sms role to be
            // available in work profile. Given that you can't send or receive work message without
            // work profile telephony being enabled.
            // Also when work profile telephony gets enabled, dialer/sms app gets installed in to
            // work profile, which would trigger this function and hence sms role getting
            // enabled at the right point of time.
            if (UserUtils.isManagedProfile(user, context)) {
                DevicePolicyManager devicePolicyManager = context.getSystemService(
                        DevicePolicyManager.class);
                if (devicePolicyManager.getManagedSubscriptionsPolicy().getPolicyType()
                        != ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS) {
                    return false;
                }
            }
        } else {
            if (UserUtils.isProfile(user, context)) {
                return false;
            }
        }

        UserManager userManager = context.getSystemService(UserManager.class);
        if (userManager.isRestrictedProfile(user)) {
            return false;
        }
        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        if (!telephonyManager.isSmsCapable()
                // Ensure sms role is present on car despite !isSmsCapable config (b/132972702)
                && role.getDefaultHoldersAsUser(user, context).isEmpty()) {
            return false;
        }
        return true;
    }

    @Nullable
    @Override
    public String getFallbackHolderAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        List<String> defaultPackageNames = role.getDefaultHoldersAsUser(user, context);
        if (!defaultPackageNames.isEmpty()) {
            return defaultPackageNames.get(0);
        }

        // TODO(b/132916161): This was the previous behavior, however this may allow any third-party
        //  app to suddenly become the default SMS app and get the permissions, if no system default
        //  SMS app is available.
        List<String> qualifyingPackageNames = role.getQualifyingPackagesAsUser(user, context);
        return CollectionUtils.firstOrNull(qualifyingPackageNames);
    }

    @Override
    public void grantAsUser(@NonNull Role role, @NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        if (SdkLevel.isAtLeastS() && PackageUtils.isSystemPackageAsUser(packageName, user,
                context)) {
            Permissions.grantAsUser(packageName, SYSTEM_SMS_PERMISSIONS, false, false, true,
                    false, false, user, context);
        }
    }

    @Override
    public void revokeAsUser(@NonNull Role role, @NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        if (SdkLevel.isAtLeastS()) {
            Permissions.revokeAsUser(packageName, SYSTEM_SMS_PERMISSIONS, true, false, false,
                    user, context);
        }
    }

    @Override
    public boolean isVisibleAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return VisibilityMixin.isVisible("config_showSmsRole", true, user, context);
    }
}
