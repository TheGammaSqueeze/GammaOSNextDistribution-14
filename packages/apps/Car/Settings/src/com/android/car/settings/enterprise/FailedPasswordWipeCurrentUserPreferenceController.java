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
* Controller to show how many times the user can fail the password before being wiped out
*/
public final class FailedPasswordWipeCurrentUserPreferenceController
        extends BaseEnterprisePrivacyPreferenceController<Preference> {

    public FailedPasswordWipeCurrentUserPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @VisibleForTesting
    FailedPasswordWipeCurrentUserPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions,
            EnterprisePrivacyFeatureProvider enterprisePrivacyFeatureProvider) {
        super(context, preferenceKey, fragmentController, uxRestrictions,
                enterprisePrivacyFeatureProvider, /* applicationFeatureProvider= */ null);
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        int superStatus = super.getDefaultAvailabilityStatus();
        if (superStatus != AVAILABLE) return superStatus;

        return getMax() > 0 ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public void updateState(Preference preference) {
        int max = getMax();
        preference.setSummary(getContext().getResources().getQuantityString(
                R.plurals.enterprise_privacy_number_failed_password_wipe, max, max));
    }

    private int getMax() {
        return mEnterprisePrivacyFeatureProvider.getMaximumFailedPasswordsBeforeWipeInCurrentUser();
    }
}
