/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.settings.applications;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.SharedPreferences;

import com.android.car.settings.common.ColoredSwitchPreference;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/**
 * Hides/shows system apps from Application listings. Intended to be used inside instances of
 * {@link AppListFragment}.
 */
public class HideSystemSwitchPreferenceController
        extends PreferenceController<ColoredSwitchPreference> {

    private final SharedPreferences mSharedPreferences = getContext().getSharedPreferences(
            AppListFragment.SHARED_PREFERENCE_PATH, Context.MODE_PRIVATE);

    public HideSystemSwitchPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<ColoredSwitchPreference> getPreferenceType() {
        return ColoredSwitchPreference.class;
    }

    @Override
    protected boolean handlePreferenceChanged(ColoredSwitchPreference preference, Object newValue) {
        boolean checked = (Boolean) newValue;
        mSharedPreferences.edit().putBoolean(AppListFragment.KEY_HIDE_SYSTEM, checked).apply();
        return true;
    }

    @Override
    protected void onStartInternal() {
        getPreference().setChecked(getSharedPreferenceHidden());
    }

    private boolean getSharedPreferenceHidden() {
        return mSharedPreferences.getBoolean(AppListFragment.KEY_HIDE_SYSTEM,
                /* defaultValue= */ true);
    }
}
