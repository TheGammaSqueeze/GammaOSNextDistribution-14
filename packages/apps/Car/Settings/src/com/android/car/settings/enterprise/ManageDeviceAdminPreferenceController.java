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

import android.car.drivingstate.CarUxRestrictions;
import android.content.ComponentName;
import android.content.Context;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.settingslib.utils.StringUtil;

import java.util.List;

/**
 * Controller for showing the device admin apps.
 */
public final class ManageDeviceAdminPreferenceController
        extends BaseEnterprisePreferenceController<Preference> {

    public ManageDeviceAdminPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected void updateState(Preference preference) {
        int activeAdmins = getNumberOfAdmins();
        mLogger.d("updateState(): Number of active device admin apps: " + activeAdmins);
        String summary = activeAdmins == 0
                ? getContext().getString(R.string.number_of_device_admins_none)
                : StringUtil.getIcuPluralsString(getContext(), activeAdmins,
                        R.string.number_of_device_admins);
        preference.setSummary(summary);
    }

    private int getNumberOfAdmins() {
        // NOTE: Only considering the current user for now. Later we may need to support profiles.
        List<ComponentName> activeAdmins = mDpm.getActiveAdmins();
        mLogger.d("Active admin apps: " + activeAdmins
                + " for current user: " + getContext().getUserId());
        if (activeAdmins == null) {
            return 0;
        }
        return activeAdmins.size();
    }
}
