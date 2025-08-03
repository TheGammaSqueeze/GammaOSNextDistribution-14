/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.settings.wifi;

import static android.os.UserManager.DISALLOW_ADD_WIFI_CONFIG;

import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByDpm;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByUm;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.net.wifi.WifiManager;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;

/**
 * Controls preference for adding wifi.
 */
public class AddWifiPreferenceController extends WifiBasePreferenceController<Preference> {

    public AddWifiPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();

        setClickableWhileDisabled(getPreference(), /* clickable= */ true, p -> {
            WifiUtil.runClickableWhileDisabled(getContext(), DISALLOW_ADD_WIFI_CONFIG,
                    getFragmentController());
        });
    }

    @Override
    public int getDefaultAvailabilityStatus() {
        // If the base controller is not available, so is this controller.
        int superStatus = super.getDefaultAvailabilityStatus();
        if (superStatus != AVAILABLE) return superStatus;

        // Check the DISALLOW_ADD_WIFI_CONFIG restriction.
        Context context = getContext();
        if (hasUserRestrictionByUm(context, DISALLOW_ADD_WIFI_CONFIG)
                || hasUserRestrictionByDpm(context, DISALLOW_ADD_WIFI_CONFIG)) {
            return AVAILABLE_FOR_VIEWING;
        }

        return AVAILABLE;
    }

    @Override
    public void onWifiStateChanged(int state) {
        switch (state) {
            case WifiManager.WIFI_STATE_DISABLED:
                getPreference().setVisible(false);
                break;
            default:
                getPreference().setVisible(true);
        }
    }
}