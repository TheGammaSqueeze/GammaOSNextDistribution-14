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

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;

/**
 * Controller for the warning info preference the device admin details screen.
 */
public final class DeviceAdminAddWarningPreferenceController
        extends BaseDeviceAdminAddPreferenceController<Preference> {

    public DeviceAdminAddWarningPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected void updateState(Preference preferenceGroup) {
        Context context = getContext();
        ComponentName admin = mDeviceAdminInfo.getComponent();

        CharSequence title = null;
        boolean isProfileOwner = admin.equals(mDpm.getProfileOwner());
        if (isProfileOwner || admin.equals(mDpm.getDeviceOwnerComponentOnCallingUser())) {
            // Profile owner in a user or device owner, user can't disable admin.
            if (isProfileOwner) {
                // Show profile owner in a user description.
                title = context.getString(R.string.admin_profile_owner_user_message);
            } else {
                // Show device owner description.
                if (isFinancedDevice()) {
                    title = context.getString(R.string.admin_financed_message);
                } else {
                    title = context.getString(R.string.admin_device_owner_message);
                }
            }
        } else {
            int resId = mDpm.isAdminActive(admin) ? R.string.device_admin_status
                    : R.string.device_admin_warning;
            CharSequence label = mDeviceAdminInfo.getActivityInfo().applicationInfo.loadLabel(mPm);
            title = context.getString(resId, label);
        }
        if (title == null) {
            mLogger.v("no warning message");
            return;
        }

        preferenceGroup.setTitle(title);
    }

    private boolean isFinancedDevice() {
        return mDpm.isDeviceManaged() && mDpm.getDeviceOwnerType(mDpm
                .getDeviceOwnerComponentOnAnyUser())
                    == DevicePolicyManager.DEVICE_OWNER_TYPE_FINANCED;
    }
}
