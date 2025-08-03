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

package com.android.car.settings.applications.appinfo;

import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_DEFAULT;
import static android.app.AppOpsManager.MODE_IGNORED;
import static android.app.AppOpsManager.OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED;

import static com.android.car.settings.applications.ApplicationsUtils.isHibernationEnabled;

import android.app.AppOpsManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Slog;

import androidx.preference.TwoStatePreference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/**
 * A PreferenceController handling the logic for exempting hibernation of app
 */
public final class HibernationSwitchPreferenceController
        extends PreferenceController<TwoStatePreference>
        implements AppOpsManager.OnOpChangedListener {
    private static final String TAG = "HibernationSwitchPrefController";
    private String mPackageName;
    private final AppOpsManager mAppOpsManager;
    private int mPackageUid;
    private boolean mIsPackageSet;
    private boolean mIsPackageExemptByDefault;

    public HibernationSwitchPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mAppOpsManager = context.getSystemService(AppOpsManager.class);
    }

    @Override
    protected Class<TwoStatePreference> getPreferenceType() {
        return TwoStatePreference.class;
    }

    @Override
    public int getDefaultAvailabilityStatus() {
        return isHibernationEnabled() && mIsPackageSet ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    protected void onStartInternal() {
        if (mIsPackageSet) {
            mAppOpsManager.startWatchingMode(
                    OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED, mPackageName, this);
        }
    }

    @Override
    protected void onStopInternal() {
        mAppOpsManager.stopWatchingMode(this);
    }

    /**
     * Set the package. And also retrieve details from package manager. Some packages may be
     * exempted from hibernation by default. This method should only be called to initialize the
     * controller.
     * @param packageName The name of the package whose hibernation state to be managed.
     */
    public void setPackageName(String packageName) {
        mPackageName = packageName;
        PackageManager packageManager = getContext().getPackageManager();

        // Q- packages exempt by default, except R- on Auto since Auto-Revoke was skipped in R
        int maxTargetSdkVersionForExemptApps = android.os.Build.VERSION_CODES.R;
        try {
            mPackageUid = packageManager.getPackageUid(packageName, /* flags= */ 0);
            mIsPackageExemptByDefault = packageManager.getTargetSdkVersion(packageName)
                    <= maxTargetSdkVersionForExemptApps;
            mIsPackageSet = true;
        } catch (PackageManager.NameNotFoundException e) {
            Slog.w(TAG, "Package [" + mPackageName + "] is not found!");
            mIsPackageSet = false;
        }
    }

    @Override
    protected void updateState(TwoStatePreference preference) {
        super.updateState(preference);
        preference.setChecked(!isPackageHibernationExemptByUser());
    }

    private boolean isPackageHibernationExemptByUser() {
        if (!mIsPackageSet) return true;
        int mode = mAppOpsManager.unsafeCheckOpNoThrow(
                OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED, mPackageUid, mPackageName);

        return mode == MODE_DEFAULT ? mIsPackageExemptByDefault : mode != MODE_ALLOWED;
    }

    @Override
    public void onOpChanged(String op, String packageName) {
        if (OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED.equals(op)
                && TextUtils.equals(mPackageName, packageName)) {
            refreshUi();
        }
    }

    @Override
    protected boolean handlePreferenceChanged(TwoStatePreference preference, Object newValue) {
        try {
            mAppOpsManager.setUidMode(OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED, mPackageUid,
                    (boolean) newValue ? MODE_ALLOWED : MODE_IGNORED);
        } catch (RuntimeException e) {
            return false;
        }
        return true;
    }
}
