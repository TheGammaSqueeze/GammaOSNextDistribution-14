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

package com.android.car.settings.wifi;

import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.Context;

import androidx.annotation.NonNull;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.wifitrackerlib.WifiEntry;

import java.util.List;

/**
 * Renders a list of no more than a specified number of {@link WifiEntry} as a list of preferences.
 */
public class LimitedWifiEntryListPreferenceController extends WifiEntryListPreferenceController
        implements CarUxRestrictionsManager.OnUxRestrictionsChangedListener {

    private int mMaxWifiEntryCount;

    public LimitedWifiEntryListPreferenceController(@NonNull Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mMaxWifiEntryCount = getContext().getResources().getInteger(
                R.integer.limited_wifi_entry_list_count);
    }

    @Override
    protected List<WifiEntry> fetchWifiEntries() {
        List<WifiEntry> wifiEntries = super.fetchWifiEntries();

        if (wifiEntries.size() > mMaxWifiEntryCount) {
            wifiEntries.subList(mMaxWifiEntryCount, wifiEntries.size()).clear();
        }

        return wifiEntries;
    }
}
