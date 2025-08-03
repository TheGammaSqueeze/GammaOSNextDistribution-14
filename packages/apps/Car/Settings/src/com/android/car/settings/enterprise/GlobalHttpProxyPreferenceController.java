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
import android.net.ConnectivityManager;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;

/**
* Controller to show whether the device owner created a global proxy.
*/
public final class GlobalHttpProxyPreferenceController
        extends BaseEnterprisePreferenceController<Preference> {
    private final ConnectivityManager mConnectivityManager;

    public GlobalHttpProxyPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        return mConnectivityManager.getGlobalProxy() != null ? AVAILABLE : DISABLED_FOR_PROFILE;
    }
}
