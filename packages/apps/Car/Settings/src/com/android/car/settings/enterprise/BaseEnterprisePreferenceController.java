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

import android.app.admin.DeviceAdminInfo;
import android.app.admin.DevicePolicyManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;

/**
 * Base class for controllers in the enterprise module.
 */
abstract class BaseEnterprisePreferenceController<P extends Preference>
        extends PreferenceController<P> {

    protected final Logger mLogger = new Logger(getClass());

    protected final DevicePolicyManager mDpm;
    protected final PackageManager mPm;
    protected final UserManager mUm;

    private final boolean mHasFeature;

    protected BaseEnterprisePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);

        mHasFeature = context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN);

        mDpm = context.getSystemService(DevicePolicyManager.class);
        mPm = context.getPackageManager();
        mUm = context.getSystemService(UserManager.class);
    }

    @Override
    protected Class<P> getPreferenceType() {
        return (Class<P>) Preference.class;
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        return mHasFeature ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    final boolean isProfileOrDeviceOwner(ComponentName admin) {
        return admin.equals(mDpm.getProfileOwner())
                || admin.equals(mDpm.getDeviceOwnerComponentOnCallingUser());
    }

    @Nullable
    protected final CharSequence getDescription(DeviceAdminInfo deviceAdminInfo) {
        try {
            return deviceAdminInfo.loadDescription(mPm);
        } catch (Resources.NotFoundException e) {
            mLogger.v("No description for "
                    + deviceAdminInfo.getComponent().flattenToShortString());
        }
        return null;
    }

    /**
     * Returns whether the device is in COMP mode (primary user managed by a Device Owner app and
     * work profile managed by a Profile Owner app).
     */
    protected boolean isInCompMode() {
        return EnterpriseUtils.hasDeviceOwner(getContext())
                && getManagedProfileUserId() != UserHandle.USER_NULL;
    }

    private int getManagedProfileUserId() {
        UserInfo userInfo = getManagedProfileUserInfo();
        if (userInfo != null) {
            return userInfo.id;
        }
        return UserHandle.USER_NULL;
    }

    private UserInfo getManagedProfileUserInfo() {
        for (UserInfo userInfo : mUm.getProfiles(UserHandle.myUserId())) {
            if (userInfo.isManagedProfile()) {
                return userInfo;
            }
        }
        return null;
    }
}
