/*
 * Copyright 2019 The Android Open Source Project
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

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.network.NetworkBasePreferenceController;
import com.android.settingslib.net.DataUsageController;
import com.android.settingslib.utils.StringUtil;

import java.util.concurrent.TimeUnit;

/**
 * Controller to handle the business logic for AppDataUsage preference on the data usage screen
 */
public class DataUsagePreferenceController extends
        NetworkBasePreferenceController<Preference> {

    private static final long MILLIS_IN_A_DAY = TimeUnit.DAYS.toMillis(1);
    private static final String STRING_FORMAT = "%s, %s";

    private final DataUsageController mDataUsageController;

    public DataUsagePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mDataUsageController = new DataUsageController(context);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        AppDataUsageFragment appDataUsageFragment = AppDataUsageFragment.newInstance(getSubId());
        getFragmentController().launchFragment(appDataUsageFragment);
        return true;
    }

    @Override
    protected void updateState(Preference preference) {
        DataUsageController.DataUsageInfo info = mDataUsageController.getDataUsageInfo(
                getNetworkTemplate());
        getPreference().setSummary(String.format(STRING_FORMAT, getUsageText(info),
                getRemainingBillingCycleTimeText(info)));
    }

    private CharSequence getUsageText(DataUsageController.DataUsageInfo info) {
        return DataUsageUtils.bytesToIecUnits(getContext(), info.usageLevel);
    }

    private CharSequence getRemainingBillingCycleTimeText(DataUsageController.DataUsageInfo info) {
        long millisLeft = info.cycleEnd - System.currentTimeMillis();
        if (millisLeft <= 0) {
            return getContext().getString(R.string.billing_cycle_none_left);
        } else {
            int daysLeft = (int) (millisLeft / MILLIS_IN_A_DAY);
            if (daysLeft < 1) {
                return getContext().getString(R.string.billing_cycle_less_than_one_day_left);
            } else {
                return StringUtil.getIcuPluralsString(getContext(), daysLeft,
                        R.string.billing_cycle_days_left);
            }
        }
    }
}
