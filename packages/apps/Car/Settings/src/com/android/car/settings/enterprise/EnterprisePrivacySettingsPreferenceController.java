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

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;

/**
 * Controller for privacy information about the device owner.
 *
 * <p><b>NOTE: </b>phone provides different screens for financed devices, which we don't quite
 * support yet (see {@code PrivacySettingsPreferenceFactory}
 */
public final class EnterprisePrivacySettingsPreferenceController
        extends BaseEnterprisePreferenceController<Preference> {

    public EnterprisePrivacySettingsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        int superStatus = super.getDefaultAvailabilityStatus();
        if (superStatus != AVAILABLE) {
            mLogger.d("getAvailabilityStatus(): returning " + superStatus);
            return superStatus;
        }

        if (!EnterpriseUtils.hasDeviceOwner(getContext())) {
            mLogger.d("getAvailabilityStatus(): no device owner, returning "
                    + UNSUPPORTED_ON_DEVICE);
            return UNSUPPORTED_ON_DEVICE;
        }

        return AVAILABLE;
    }

    @Override
    protected void updateState(Preference preference) {
        // NOTE: phone displays different messages when the device owner org name is known, but on
        // automotive we're always showing the generic name
        preference.setSummary(R.string.enterprise_privacy_settings_summary_generic);
    }
}
