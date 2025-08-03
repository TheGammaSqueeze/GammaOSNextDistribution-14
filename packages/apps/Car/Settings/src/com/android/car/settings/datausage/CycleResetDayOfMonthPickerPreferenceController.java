/*
 * Copyright (C) 2019 The Android Open Source Project
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
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;

import androidx.annotation.VisibleForTesting;
import androidx.preference.ListPreference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;

import java.util.TimeZone;
import java.util.stream.IntStream;

/**
 * ListPreference used to pick the date on which the data warning/limit cycle should end.
 */
public class CycleResetDayOfMonthPickerPreferenceController extends
        DataWarningAndLimitBasePreferenceController<ListPreference> {

    @VisibleForTesting
    static final int MIN_DAY = 1;
    @VisibleForTesting
    static final int MAX_DAY = 31;

    public CycleResetDayOfMonthPickerPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<ListPreference> getPreferenceType() {
        return ListPreference.class;
    }

    @Override
    protected void onCreateInternal() {
        CharSequence[] list = IntStream.rangeClosed(MIN_DAY, MAX_DAY)
                .mapToObj(i-> String.valueOf(i)).toArray(CharSequence[]::new);
        getPreference().setEntryValues(list);
        getPreference().setEntries(list);
    }

    @Override
    protected void updateState(ListPreference preference) {
        String prefix = getContext().getString(R.string.cycle_reset_day_of_month_picker_subtitle);
        int cycleDayOfMonth = getNetworkPolicyEditor().getPolicyCycleDay(getNetworkTemplate());
        if (cycleDayOfMonth >= MIN_DAY && cycleDayOfMonth <= MAX_DAY) {
            String cycleDayOfMonthString = String.valueOf(cycleDayOfMonth);
            getPreference().setValue(cycleDayOfMonthString);
            getPreference().setSummary(BidiFormatter.getInstance().unicodeWrap(
                    String.join(" ", prefix, cycleDayOfMonthString),
                    TextDirectionHeuristics.LOCALE));
        }
    }

    @Override
    protected boolean handlePreferenceChanged(ListPreference preference, Object newValue) {
        onDayOfMonthPicked(Integer.valueOf((String) newValue));
        return true;
    }

    private void onDayOfMonthPicked(int dayOfMonth) {
        getNetworkPolicyEditor().setPolicyCycleDay(getNetworkTemplate(), dayOfMonth,
                TimeZone.getDefault().getID());
    }
}
