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
import android.text.TextUtils;

import androidx.annotation.CallSuper;
import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.network.NetworkBasePreferenceController;
import com.android.settingslib.net.DataUsageController;

/**
 * Controller updating data warning summary.
 */
public class DataWarningAndLimitPreferenceController extends
        NetworkBasePreferenceController<Preference> {

    private DataUsageController mDataUsageController;

    public DataWarningAndLimitPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mDataUsageController = new DataUsageController(context);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    @CallSuper
    protected void updateState(Preference preference) {
        getPreference().setSummary(getLimitText());
    }

    private CharSequence getLimitText() {
        DataUsageController.DataUsageInfo info = mDataUsageController.getDataUsageInfo(
                getNetworkTemplate());
        if (info.warningLevel > 0 && info.limitLevel > 0) {
            return TextUtils.expandTemplate(
                    getContext().getText(R.string.cell_data_warning_and_limit),
                    DataUsageUtils.bytesToIecUnits(getContext(), info.warningLevel),
                    DataUsageUtils.bytesToIecUnits(getContext(), info.limitLevel));
        } else if (info.warningLevel > 0) {
            return TextUtils.expandTemplate(getContext().getText(R.string.cell_data_warning),
                    DataUsageUtils.bytesToIecUnits(getContext(), info.warningLevel));
        } else if (info.limitLevel > 0) {
            return TextUtils.expandTemplate(getContext().getText(R.string.cell_data_limit),
                    DataUsageUtils.bytesToIecUnits(getContext(), info.limitLevel));
        }

        return null;
    }
}
