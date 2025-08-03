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
package com.android.car.settings.enterprise;

import android.annotation.Nullable;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.enterprise.CallbackTranslator.AppsListCallbackTranslator;
import com.android.car.settings.enterprise.CallbackTranslator.Callback;
import com.android.car.settingslib.applications.ApplicationFeatureProvider;
import com.android.car.settingslib.applications.UserAppInfo;
import com.android.settingslib.widget.AppPreference;

import java.util.List;

/**
 * Base class for PreferenceControllers that builds a dynamic list of applications.
 */
abstract class BaseApplicationsListPreferenceController extends
        BaseAsyncPreferenceController<PreferenceGroup, List<UserAppInfo>,
        AppsListCallbackTranslator> {

    protected BaseApplicationsListPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions,
            @Nullable ApplicationFeatureProvider applicationFeatureProvider) {
        super(context, preferenceKey, fragmentController, uxRestrictions,
                applicationFeatureProvider);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    protected AppsListCallbackTranslator newCallbackTranslator(
            Callback<List<UserAppInfo>> callback) {
        return new AppsListCallbackTranslator(callback);
    }

    @Override
    protected boolean isAvailable(List<UserAppInfo> result) {
        return !result.isEmpty();
    }

    @Override
    protected void updateState(PreferenceGroup preferenceGroup) {
        List<UserAppInfo> apps = getResult();
        mLogger.d("Updating state with " + apps.size() + " apps");
        preferenceGroup.removeAll();

        for (int position = 0; position < apps.size(); position++) {
            UserAppInfo item = apps.get(position);
            Preference preference = new AppPreference(getContext());
            preference.setTitle(item.appInfo.loadLabel(mPm));
            preference.setIcon(item.appInfo.loadIcon(mPm));
            preference.setOrder(position);
            preference.setSelectable(false);
            preferenceGroup.addPreference(preference);
        }
    }
}
