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

package com.android.car.settings.datausage;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/**
 * Controller to update total data usage for all the apps.
 */
public class AppDataUsageTotalPreferenceController extends PreferenceController<Preference> {

    private static final String STRING_FORMAT = "%s: %s";

    private String mFieldName;
    private String mDataUsage;

    public AppDataUsageTotalPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mFieldName = context.getString(R.string.data_usage_all_apps_title);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    @CallSuper
    protected void updateState(Preference preference) {
        getPreference().setTitle(getTitle());
    }

    private String getTitle() {
        return String.format(STRING_FORMAT, mFieldName, mDataUsage);
    }

    /** Set data usage in bytes */
    public void setDataUsage(long usage) {
        mDataUsage = DataUsageUtils.bytesToIecUnits(getContext(), usage).toString();
    }
}
