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
import android.icu.text.MeasureFormat;
import android.icu.util.MeasureUnit;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceGroup;
import androidx.preference.TwoStatePreference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.GroupSelectionPreferenceController;
import com.android.car.ui.preference.CarUiRadioButtonPreference;

import java.util.ArrayList;
import java.util.List;

/** Class that manages whether the data warning gigabyte or megabyte radio buttons are selected */
public class DataUsageUnitPreferenceController extends GroupSelectionPreferenceController {

    private final String mGbPreferenceKey;
    private final String mMbPreferenceKey;

    private String mSelectedKey;

    public DataUsageUnitPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mGbPreferenceKey = context.getString(R.string.pk_data_usage_radio_gb);
        mMbPreferenceKey = context.getString(R.string.pk_data_usage_radio_mb);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    /** Sets whether GB or MB radio button should be selected by default */
    public void setDefaultSelection(boolean isGb) {
        mSelectedKey = isGb ? mGbPreferenceKey : mMbPreferenceKey;
    }

    @Override
    protected void checkInitialized() {
        if (mSelectedKey == null) {
            throw new IllegalStateException(
                    "Default selection should be set before calling this function");
        }
    }

    @Override
    protected String getCurrentCheckedKey() {
        return mSelectedKey;
    }

    @NonNull
    @Override
    protected List<TwoStatePreference> getGroupPreferences() {
        List<TwoStatePreference> entries = new ArrayList<>();

        MeasureFormat formatter = MeasureFormat.getInstance(
                getContext().getResources().getConfiguration().locale,
                MeasureFormat.FormatWidth.SHORT);

        // Create GB preference
        CarUiRadioButtonPreference gbPreference = new CarUiRadioButtonPreference(getContext());
        gbPreference.setKey(mGbPreferenceKey);
        gbPreference.setTitle(formatter.getUnitDisplayName(MeasureUnit.GIGABYTE));

        // Create MB preference
        CarUiRadioButtonPreference mbPreference = new CarUiRadioButtonPreference(getContext());
        mbPreference.setKey(mMbPreferenceKey);
        mbPreference.setTitle(formatter.getUnitDisplayName(MeasureUnit.MEGABYTE));

        entries.add(gbPreference);
        entries.add(mbPreference);
        return entries;
    }

    @Override
    protected boolean handleGroupItemSelected(TwoStatePreference preference) {
        mSelectedKey = preference.getKey().equals(mGbPreferenceKey)
                ? mGbPreferenceKey : mMbPreferenceKey;
        return true;
    }

    /** Returns whether the gigabyte radio button is selected */
    public boolean isGbSelected() {
        return mSelectedKey.equals(mGbPreferenceKey);
    }
}
