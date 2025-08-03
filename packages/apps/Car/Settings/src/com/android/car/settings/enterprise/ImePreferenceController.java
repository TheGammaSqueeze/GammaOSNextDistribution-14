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

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settingslib.enterprise.EnterprisePrivacyFeatureProvider;

/**
* Controller to show whether the device owner changed the keyboard for the user.
*/
public final class ImePreferenceController
        extends BaseEnterprisePrivacyPreferenceController<Preference> {

    public ImePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @VisibleForTesting
    ImePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions,
            EnterprisePrivacyFeatureProvider enterprisePrivacyFeatureProvider) {
        super(context, preferenceKey, fragmentController, uxRestrictions,
                enterprisePrivacyFeatureProvider, /* applicationFeatureProvider= */ null);
    }

    @Override
    public void updateState(Preference preference) {
        preference.setSummary(getContext().getResources().getString(
                R.string.enterprise_privacy_input_method_name,
                mEnterprisePrivacyFeatureProvider.getImeLabelIfOwnerSet()));
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        return mEnterprisePrivacyFeatureProvider.getImeLabelIfOwnerSet() != null
                ? AVAILABLE
                : DISABLED_FOR_PROFILE;
    }
}
