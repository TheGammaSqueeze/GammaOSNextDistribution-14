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

package com.android.car.settings.accessibility;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/**
 * Preference controller for the screen reader settings item. This updates the subtitle based on the
 * enabled state of the system screen reader. The visibility is controlled by the parent {@link
 * ScreenReaderCategoryPreferenceController}.
 */
public class ScreenReaderSettingsPreferenceController extends
        PreferenceController<Preference> {

    public ScreenReaderSettingsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void updateState(Preference preference) {
        preference.setTitle(ScreenReaderUtils.getScreenReaderName(getContext()));
        preference.setSummary(getSummary());
    }

    private CharSequence getSummary() {
        return ScreenReaderUtils.isScreenReaderEnabled(getContext())
                ? getContext().getString(R.string.screen_reader_settings_on)
                : getContext().getString(R.string.screen_reader_settings_off);
    }

}
