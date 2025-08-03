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

package com.android.car.settings.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.core.content.res.TypedArrayUtils;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.car.ui.R;
import com.android.car.ui.preference.CarUiEditTextPreference;
import com.android.car.ui.preference.CarUiPreference;

/**
 * {@link PreferenceCategory} which allows for CarUiPreference chevrons to be hidden for the entire
 * category with {@link R.styleable.PreferenceGroup_showChevron}
 */
public class SettingsPreferenceCategory extends PreferenceCategory {

    private final boolean mShouldShowChevron;

    public SettingsPreferenceCategory(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        TypedArray a = context.obtainStyledAttributes(
                attrs,
                R.styleable.PreferenceGroup,
                defStyleAttr,
                defStyleRes);

        mShouldShowChevron = a.getBoolean(R.styleable.PreferenceGroup_showChevron, true);
        a.recycle();
    }

    public SettingsPreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public SettingsPreferenceCategory(Context context, AttributeSet attrs) {
        this(context, attrs, TypedArrayUtils.getAttr(context, R.attr.preferenceCategoryStyle,
                android.R.attr.preferenceCategoryStyle));
    }

    public SettingsPreferenceCategory(Context context) {
        this(context, null);
    }

    @Override
    public boolean addPreference(Preference preference) {
        if (!mShouldShowChevron && (preference instanceof CarUiPreference
                || preference instanceof CarUiEditTextPreference)) {
            ((CarUiPreference) preference).setShowChevron(false);
        }
        return super.addPreference(preference);
    }
}
