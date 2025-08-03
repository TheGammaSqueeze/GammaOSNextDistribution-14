/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.settings.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.ui.R;
import com.android.car.ui.preference.CarUiEditTextPreference;
import com.android.car.ui.preference.CarUiPreference;

/**
 * {@link PreferenceGroup} which does not display a title, icon, or summary. This allows for
 * logical grouping of preferences without indications in the UI.
 */
public class LogicalPreferenceGroup extends PreferenceGroup {

    private final boolean mShouldShowChevron;

    public LogicalPreferenceGroup(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setLayoutResource(R.layout.logical_preference_group);
        TypedArray a = context.obtainStyledAttributes(
                attrs,
                R.styleable.PreferenceGroup,
                defStyleAttr,
                defStyleRes);

        mShouldShowChevron = a.getBoolean(R.styleable.PreferenceGroup_showChevron, true);
        a.recycle();
    }

    public LogicalPreferenceGroup(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public LogicalPreferenceGroup(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LogicalPreferenceGroup(Context context) {
        this(context, null);
    }

    @Override
    public boolean addPreference(Preference preference) {
        updateShowChevron(preference);

        return super.addPreference(preference);
    }

    protected void updateShowChevron(Preference preference) {
        if (!mShouldShowChevron && (preference instanceof CarUiPreference
                || preference instanceof CarUiEditTextPreference)) {
            ((CarUiPreference) preference).setShowChevron(false);
        }
    }
}
