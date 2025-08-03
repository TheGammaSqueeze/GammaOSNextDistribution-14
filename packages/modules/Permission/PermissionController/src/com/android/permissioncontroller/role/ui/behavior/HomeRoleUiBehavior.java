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

package com.android.permissioncontroller.role.ui.behavior;

import android.app.admin.DevicePolicyResources;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.Preference;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.utils.CollectionUtils;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.permissioncontroller.role.ui.TwoTargetPreference;
import com.android.permissioncontroller.role.utils.UserUtils;
import com.android.role.controller.model.Role;

import java.util.List;

/***
 * Class for UI behavior of Home role
 */
public class HomeRoleUiBehavior implements RoleUiBehavior {

    private static final String LOG_TAG = HomeRoleUiBehavior.class.getSimpleName();

    @Override
    public void preparePreferenceAsUser(@NonNull Role role, @NonNull TwoTargetPreference preference,
            @NonNull List<ApplicationInfo> applicationInfos, @NonNull UserHandle user,
            @NonNull Context context) {
        TwoTargetPreference.OnSecondTargetClickListener listener = null;
        RoleManager roleManager = context.getSystemService(RoleManager.class);
        String packageName = CollectionUtils.firstOrNull(roleManager.getRoleHoldersAsUser(
                role.getName(), user));
        if (packageName != null) {
            Intent intent = new Intent(Intent.ACTION_APPLICATION_PREFERENCES)
                    .setPackage(packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PackageManager userPackageManager = UserUtils.getUserContext(context, user)
                    .getPackageManager();
            ResolveInfo resolveInfo = userPackageManager.resolveActivity(intent, 0);
            if (resolveInfo != null && resolveInfo.activityInfo != null
                    && resolveInfo.activityInfo.exported) {
                listener = preference2 -> {
                    try {
                        context.startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Log.e(LOG_TAG, "Cannot start activity for home app preferences", e);
                    }
                };
            }
        }
        preference.setOnSecondTargetClickListener(listener);
    }

    @Override
    public void prepareApplicationPreferenceAsUser(@NonNull Role role,
            @NonNull Preference preference, @NonNull ApplicationInfo applicationInfo,
            @NonNull UserHandle user, @NonNull Context context) {
        boolean missingWorkProfileSupport = isMissingWorkProfileSupport(applicationInfo, context);
        if (preference.isEnabled()) {
            preference.setEnabled(!missingWorkProfileSupport);
        }
        preference.setSummary(missingWorkProfileSupport ? Utils.getEnterpriseString(context,
                DevicePolicyResources.Strings.DefaultAppSettings
                        .HOME_MISSING_WORK_PROFILE_SUPPORT_MESSAGE,
                R.string.home_missing_work_profile_support) : null);
    }

    private boolean isMissingWorkProfileSupport(@NonNull ApplicationInfo applicationInfo,
            @NonNull Context context) {
        boolean hasWorkProfile = UserUtils.getWorkProfile(context) != null;
        if (!hasWorkProfile) {
            return false;
        }
        boolean isWorkProfileSupported = applicationInfo.targetSdkVersion
                >= Build.VERSION_CODES.LOLLIPOP;
        return !isWorkProfileSupported;
    }
}
