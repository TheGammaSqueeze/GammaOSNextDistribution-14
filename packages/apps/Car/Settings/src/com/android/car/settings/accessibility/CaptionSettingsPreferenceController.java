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
import android.provider.Settings;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/**
 * {@link PreferenceController} for the preference which leads the user to the caption settings
 * from within accessibility settings.
 */
public class CaptionSettingsPreferenceController extends
        PreferenceController<Preference> {

    public CaptionSettingsPreferenceController(Context context,
            String preferenceKey, FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected void updateState(Preference preference) {
        preference.setSummary(getSummary());
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    private CharSequence getSummary() {
        boolean captionsEnabled = Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_ENABLED, 0) != 0;
        return captionsEnabled ? getContext().getString(R.string.captions_settings_on)
                : getContext().getString(R.string.captions_settings_off);
    }
}
