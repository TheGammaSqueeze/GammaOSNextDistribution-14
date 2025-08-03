/*
 * Copyright (C) 2022 The Android Open Source Project
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

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.applications.performance.PerfImpactingAppsItemManager;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.utils.StringUtil;

/**
 * Controller for the entry point to performance-impacting apps settings. It fetches the number of
 * resource overuse packages and updates the entry point preference's summary.
 */
public final class PerfImpactingAppsEntryPreferenceController extends
        PreferenceController<Preference> implements
        PerfImpactingAppsItemManager.PerfImpactingAppsListener {

    public PerfImpactingAppsEntryPreferenceController(Context context,
            String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    public void onPerfImpactingAppsLoaded(int disabledPackagesCount) {
        getPreference().setSummary(StringUtil.getIcuPluralsString(getContext(),
                disabledPackagesCount, R.string.performance_impacting_apps_summary));
    }
}
