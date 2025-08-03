/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.settings.device.apps;

import static com.android.tv.settings.util.InstrumentationUtils.logEntrySelected;

import android.app.admin.DevicePolicyManager;
import android.app.tvsettings.TvSettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtilsInternal;
import com.android.settingslib.applications.ApplicationsState;
import com.android.tv.settings.R;
import com.android.tv.settings.deviceadmin.DeviceAdminAdd;

public class UninstallPreference extends AppActionPreference {

    private final DevicePolicyManager mDpm;
    private final Context mAppContext;

    public UninstallPreference(Context context,
            ApplicationsState.AppEntry entry) {
        super(context, entry);
        mAppContext = context;
        mDpm = context.getSystemService(DevicePolicyManager.class);
        refresh();
    }

    public void refresh() {
        if (canUninstall()) {
            setVisible(true);
            setTitle(R.string.device_apps_app_management_uninstall);
            this.setOnPreferenceClickListener(
                    preference -> {
                        logEntrySelected(TvSettingsEnums.APPS_ALL_APPS_APP_ENTRY_UNINSTALL);
                        return false;
                    });
        } else if (canUninstallUpdates()) {
            setVisible(true);
            setTitle(R.string.device_apps_app_management_uninstall_updates);
            this.setOnPreferenceClickListener(
                    preference -> {
                        logEntrySelected(TvSettingsEnums.APPS_ALL_APPS_APP_ENTRY_UNINSTALL_UPDATES);
                        return false;
                    });
        } else {
            setVisible(false);
        }

        UserManager userManager = getContext().getSystemService(UserManager.class);
        if (userManager.hasUserRestriction(UserManager.DISALLOW_APPS_CONTROL)) {
            final String packageName = mEntry.info.packageName;
            final int userId = UserHandle.myUserId();
            final RestrictedLockUtils.EnforcedAdmin admin = RestrictedLockUtilsInternal
                    .checkIfUninstallBlocked(mAppContext, packageName, userId);
            if (admin != null) {
                setDisabledByAdmin(admin);
            } else {
                setEnabled(false);
            }
        }
    }

    public boolean canUninstall() {
        return canUninstall(mEntry);
    }

    public static boolean canUninstall(ApplicationsState.AppEntry entry) {
        return (entry.info.flags &
                (ApplicationInfo.FLAG_UPDATED_SYSTEM_APP|ApplicationInfo.FLAG_SYSTEM)) == 0;
    }

    public boolean canUninstallUpdates() {
        return (mEntry.info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    private boolean isUninstallRestricted() {
        final String packageName = mEntry.info.packageName;
        final int userId = UserHandle.myUserId();

        final boolean appsControlDisallowedBySystem =
                RestrictedLockUtilsInternal.hasBaseUserRestriction(
                        mAppContext, UserManager.DISALLOW_APPS_CONTROL, userId);
        RestrictedLockUtils.EnforcedAdmin admin =
                RestrictedLockUtilsInternal.checkIfUninstallBlocked(mAppContext, packageName,
                        userId);
        if (admin != null && !appsControlDisallowedBySystem) {
            RestrictedLockUtils.sendShowAdminSupportDetailsIntent(mAppContext, admin);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Intent getIntent() {
        if (!isUninstallRestricted()) {
            if (isActiveDeviceAdmin()) {
                return getUninstallDeviceAdminIntent();
            } else {
                // regular package uninstall
                return getUninstallIntent();
            }
        } else {
            return null;
        }
    }

    private boolean isActiveDeviceAdmin() {
        return (mDpm != null && mDpm.packageHasActiveAdmins(mEntry.info.packageName));
    }

    private Intent getUninstallIntent() {
        final Uri packageURI = Uri.parse("package:" + mEntry.info.packageName);
        final Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageURI);
        uninstallIntent.putExtra(Intent.EXTRA_UNINSTALL_ALL_USERS, true);
        uninstallIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        return uninstallIntent;
    }

    private Intent getUninstallDeviceAdminIntent() {
        final String packageName = mEntry.info.packageName;
        if (mDpm.packageHasActiveAdmins(packageName)) {
            Intent uninstallDaIntent = new Intent(mAppContext, DeviceAdminAdd.class);
            uninstallDaIntent.putExtra(DeviceAdminAdd.EXTRA_DEVICE_ADMIN_PACKAGE_NAME,
                    packageName);
            return uninstallDaIntent;
        }
        return null;
    }
}
