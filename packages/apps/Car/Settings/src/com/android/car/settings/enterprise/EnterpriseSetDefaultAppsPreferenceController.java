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
import android.os.UserHandle;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settingslib.applications.ApplicationFeatureProvider;
import com.android.car.settingslib.enterprise.EnterpriseDefaultApps;
import com.android.internal.annotations.VisibleForTesting;

/**
* Controller to show the number of default apps were set by the device owner.
*/
public final class EnterpriseSetDefaultAppsPreferenceController
        extends BaseEnterprisePrivacyPreferenceController<Preference> {

    @Nullable
    private Integer mCount;

    public EnterpriseSetDefaultAppsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        this(context, preferenceKey, fragmentController, uxRestrictions,
                /* applicationFeatureProvider= */ null);
    }

    @VisibleForTesting
    EnterpriseSetDefaultAppsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions,
            @Nullable ApplicationFeatureProvider applicationFeatureProvider) {
        super(context, preferenceKey, fragmentController, uxRestrictions,
                /* enterprisePrivacyFeatureProvider= */ null, applicationFeatureProvider);
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        if (mCount == null) {
            mCount = getNumberOfEnterpriseSetDefaultApps();
            mLogger.d("Number of apps: " + mCount);
        }

        return mCount > 0 ? AVAILABLE : DISABLED_FOR_PROFILE;
    }

    @Override
    protected void updateState(Preference preference) {
        preference.setSummary(getContext().getResources().getQuantityString(
                R.plurals.enterprise_privacy_number_packages, mCount, mCount));
    };

    private int getNumberOfEnterpriseSetDefaultApps() {
        int num = 0;
        UserHandle user = getContext().getUser();
        // TODO(b/1703325190): to support work profiles, it should use and outer lopp:
        // for (UserHandle user : mUserFeatureProvider.getUserProfiles()) {
        for (EnterpriseDefaultApps app : EnterpriseDefaultApps.values()) {
            num += mApplicationFeatureProvider
                    .findPersistentPreferredActivities(user.getIdentifier(), app.getIntents())
                    .size();
        }
        return num;
    }

}
