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
import android.os.UserHandle;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;

import java.util.List;

/**
 * Controller to show whether the device owner created CA Certificates for the user.
 */
public final class CaCertsCurrentUserPreferenceController
        extends BaseEnterprisePreferenceController<Preference> {

    public CaCertsCurrentUserPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        int superStatus = super.getDefaultAvailabilityStatus();
        if (superStatus != AVAILABLE) return superStatus;

        return getNumberOfOwnerInstalledCaCertsForCurrentUser() > 0 ? AVAILABLE
                : DISABLED_FOR_PROFILE;
    }

    @Override
    public void updateState(Preference preference) {
        int certs = getNumberOfOwnerInstalledCaCertsForCurrentUser();
        preference.setSummary(getContext().getResources().getQuantityString(
                R.plurals.enterprise_privacy_number_ca_certs, certs, certs));
        preference.setTitle(isInCompMode()
                ? R.string.enterprise_privacy_ca_certs_personal
                : R.string.enterprise_privacy_ca_certs_device);
    }

    private int getNumberOfOwnerInstalledCaCertsForCurrentUser() {
        List<String> certs = mDpm.getOwnerInstalledCaCerts(new UserHandle(UserHandle.myUserId()));
        if (certs == null) {
            return 0;
        }
        return certs.size();
    }
}
