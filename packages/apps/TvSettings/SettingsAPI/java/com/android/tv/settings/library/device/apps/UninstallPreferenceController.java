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

package com.android.tv.settings.library.device.apps;

import static com.android.tv.settings.library.device.apps.AppManagementState.REQUEST_UNINSTALL;
import static com.android.tv.settings.library.device.apps.AppManagementState.REQUEST_UNINSTALL_UPDATES;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.util.ResourcesUtil;

/** Preference controller to handle uninstall preference. */
public class UninstallPreferenceController extends AppActionPreferenceController {
    static final String KEY_UNINSTALL = "uninstall";
    private final DevicePolicyManager mDpm;
    private final Context mAppContext;

    public UninstallPreferenceController(Context context,
            UIUpdateCallback callback, int stateIdentifier,
            ApplicationsState.AppEntry appEntry, PreferenceCompatManager preferenceCompatManager) {
        super(context, callback, stateIdentifier, appEntry, preferenceCompatManager);
        mAppContext = context;
        mDpm = context.getSystemService(DevicePolicyManager.class);
    }

    @Override
    public void update() {
        if (mAppEntry == null) {
            return;
        }
        if (canUninstall()) {
            mPreferenceCompat.setVisible(true);
            mPreferenceCompat.setTitle(ResourcesUtil.getString(
                    mContext, "device_apps_app_management_uninstall"));
        } else if (canUninstallUpdates()) {
            mPreferenceCompat.setVisible(true);
            mPreferenceCompat.setTitle(ResourcesUtil.getString(
                    mContext, "device_apps_app_management_uninstall_updates"));
        } else {
            mPreferenceCompat.setVisible(false);
        }
    }

    @Override
    public boolean handlePreferenceTreeClick(boolean status) {
        if (!mDisabledByAdmin) {
            ((Activity) mContext).startActivityForResult(getIntent(),
                    canUninstall()
                            ? ManagerUtil.calculateCompoundCode(mStateIdentifier, REQUEST_UNINSTALL)
                            : ManagerUtil.calculateCompoundCode(mStateIdentifier,
                                    REQUEST_UNINSTALL_UPDATES));
            return true;
        }
        return super.handlePreferenceTreeClick(status);
    }

    @Override
    public boolean useAdminDisabledSummary() {
        return false;
    }

    @Override
    public String getAttrUserRestriction() {
        return null;
    }

    private Intent getIntent() {
        if (isActiveDeviceAdmin()) {
            return getUninstallDeviceAdminIntent();
        } else {
            // regular package uninstall
            return getUninstallIntent();
        }
    }

    private boolean isActiveDeviceAdmin() {
        return (mDpm != null && mDpm.packageHasActiveAdmins(mAppEntry.info.packageName));
    }

    private Intent getUninstallIntent() {
        final Uri packageURI = Uri.parse("package:" + mAppEntry.info.packageName);
        final Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageURI);
        uninstallIntent.putExtra(Intent.EXTRA_UNINSTALL_ALL_USERS, true);
        uninstallIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        return uninstallIntent;
    }

    private Intent getUninstallDeviceAdminIntent() {
        return null;
    }


    public boolean canUninstall() {
        return canUninstall(mAppEntry);
    }

    @Override
    public String[] getPreferenceKey() {
        return new String[]{KEY_UNINSTALL};
    }

    public static boolean canUninstall(ApplicationsState.AppEntry entry) {
        return (entry.info.flags &
                (ApplicationInfo.FLAG_UPDATED_SYSTEM_APP | ApplicationInfo.FLAG_SYSTEM)) == 0;
    }

    public boolean canUninstallUpdates() {
        return (mAppEntry.info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }
}
