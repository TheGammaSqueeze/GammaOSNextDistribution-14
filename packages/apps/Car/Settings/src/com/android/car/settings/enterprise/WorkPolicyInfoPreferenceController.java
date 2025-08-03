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
package com.android.car.settings.enterprise;

import android.app.admin.DevicePolicyManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;

import java.util.List;

/**
 * Controller for showing the work policy info in the privacy dashboard.
 */
public final class WorkPolicyInfoPreferenceController extends PreferenceController<Preference> {

    private static final Logger LOG = new Logger(WorkPolicyInfoPreferenceController.class);
    private static final int MY_USER_ID = UserHandle.myUserId();

    private final DevicePolicyManager mDpm;
    private final PackageManager mPm;
    private final UserManager mUm;
    private final boolean mEnabled;

    @Nullable
    private Intent mIntent;

    @Nullable
    private CharSequence mAdminName;

    public WorkPolicyInfoPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);

        mDpm = context.getSystemService(DevicePolicyManager.class);
        mPm = context.getPackageManager();
        mUm = context.getSystemService(UserManager.class);
        mEnabled = mPm.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN);

        LOG.d("Constructed on user " + MY_USER_ID + ": " + (mEnabled ? "enabled" : "disabled"));
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void updateState(Preference preference) {
        updateState();

        if (mIntent == null || mAdminName == null) return;

        preference.setIntent(mIntent);

        CharSequence title = getContext().getString(R.string.work_policy_privacy_settings,
                mAdminName);
        LOG.d("Setting title to " + title);
        preference.setTitle(title);
    };

    @Override
    protected int getDefaultAvailabilityStatus() {
        if (!mEnabled) return UNSUPPORTED_ON_DEVICE;

        updateState();

        return (mIntent == null || mAdminName == null) ? DISABLED_FOR_PROFILE : AVAILABLE;
    }

    private void updateState() {
        resetState();

        ComponentName admin = mDpm.getProfileOwner();
        if (admin == null) {
            LOG.d("no profile owner for user " + MY_USER_ID + ")");
            return;
        }
        String adminPkgName = admin.getPackageName();
        mIntent = new Intent(Settings.ACTION_SHOW_WORK_POLICY_INFO)
                .setPackage(adminPkgName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        List<ResolveInfo> activities = mPm.queryIntentActivities(mIntent, /* flags= */ 0);
        if (activities.isEmpty()) {
            LOG.d(admin.flattenToShortString() + " does not declare "
                    + Settings.ACTION_SHOW_WORK_POLICY_INFO);
            resetState();
            return;
        }

        try {
            ApplicationInfo appInfo = mPm.getApplicationInfo(adminPkgName, /* flags= */ 0);
            mAdminName = appInfo.loadLabel(mPm);
        } catch (Exception e) {
            LOG.e("could not get name of app " + adminPkgName, e);
            resetState();
            return;
        }

        LOG.d("updateState(): name=" + mAdminName + ", admin=" + admin.flattenToShortString());
    }

    private void resetState() {
        mIntent = null;
        mAdminName = null;
    }
}
