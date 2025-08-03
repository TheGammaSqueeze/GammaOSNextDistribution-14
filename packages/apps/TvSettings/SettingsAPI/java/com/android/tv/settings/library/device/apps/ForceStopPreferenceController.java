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

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.apphibernation.AppHibernationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.settingslib.RestrictedLockUtils;
import com.android.tv.settings.library.settingslib.RestrictedLockUtilsInternal;
import com.android.tv.settings.library.util.ResourcesUtil;

/** Preference controller to handle force stop preference. */
public class ForceStopPreferenceController extends AppActionPreferenceController {
    static final String KEY_FORCE_STOP = "forceStop";
    private static final String ARG_PACKAGE_NAME = "packageName";

    public ForceStopPreferenceController(Context context,
            UIUpdateCallback callback, int stateIdentifier,
            ApplicationsState.AppEntry appEntry, PreferenceCompatManager preferenceCompatManager) {
        super(context, callback, stateIdentifier, appEntry, preferenceCompatManager);
    }

    @Override
    public void init() {
        UserManager userManager = mContext.getSystemService(UserManager.class);
        if (userManager.hasUserRestriction(UserManager.DISALLOW_APPS_CONTROL)) {
            final RestrictedLockUtils.EnforcedAdmin admin =
                    RestrictedLockUtilsInternal.checkIfRestrictionEnforced(mContext,
                            UserManager.DISALLOW_APPS_CONTROL, UserHandle.myUserId());
            if (admin != null) {
                setDisabledByAdmin(admin);
            } else {
                setEnabled(false);
            }
        }
        update();
    }

    @Override
    public void update() {
        if (mAppEntry == null) {
            return;
        }
        mPreferenceCompat.setTitle(ResourcesUtil.getString(mContext,
                "device_apps_app_management_force_stop"));
        DevicePolicyManager dpm = mContext.getSystemService(DevicePolicyManager.class);
        Intent i = new Intent(INTENT_CONFIRMATION);
        i.putExtra(EXTRA_GUIDANCE_TITLE, ResourcesUtil.getString(
                mContext, "device_apps_app_management_force_stop"));
        i.putExtra(EXTRA_GUIDANCE_SUBTITLE, ResourcesUtil.getString(
                mContext, "device_apps_app_management_force_stop_desc"));
        i.putExtra(EXTRA_GUIDANCE_BREADCRUMB, getAppName());
        mPreferenceCompat.setIntent(i);

        AppHibernationManager ahm = mContext.getSystemService(
                AppHibernationManager.class);
        boolean isPackageHibernated = ahm.isHibernatingForUser(mAppEntry.info.packageName);

        if (dpm.packageHasActiveAdmins(mAppEntry.info.packageName)) {
            // User can't force stop device admin.
            mPreferenceCompat.setVisible(false);
        } else if (isPackageHibernated) {
            // Hibernated apps are always stopped.
            mPreferenceCompat.setVisible(false);
        } else if ((mAppEntry.info.flags & ApplicationInfo.FLAG_STOPPED) == 0) {
            // If the app isn't explicitly stopped, then always show the
            // force stop action.
            mPreferenceCompat.setVisible(true);
        } else {
            Intent intent = new Intent(Intent.ACTION_QUERY_PACKAGE_RESTART,
                    Uri.fromParts("package", mAppEntry.info.packageName, null));
            intent.putExtra(Intent.EXTRA_PACKAGES, new String[]{
                    mAppEntry.info.packageName});
            intent.putExtra(Intent.EXTRA_UID, mAppEntry.info.uid);
            intent.putExtra(Intent.EXTRA_USER_HANDLE, UserHandle.getUserId(mAppEntry.info.uid));
            mContext.sendOrderedBroadcast(intent,
                    android.Manifest.permission.HANDLE_QUERY_PACKAGE_RESTART, new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            mPreferenceCompat.setVisible(getResultCode() != Activity.RESULT_CANCELED);
                        }
                    }, null, Activity.RESULT_CANCELED, null, null);
        }
        super.update();
    }

    @Override
    public boolean useAdminDisabledSummary() {
        return false;
    }

    @Override
    public String getAttrUserRestriction() {
        return null;
    }

    @Override
    public String[] getPreferenceKey() {
        return new String[]{KEY_FORCE_STOP};
    }
}
