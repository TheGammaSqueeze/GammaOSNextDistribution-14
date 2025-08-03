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

package com.android.car.settings.location;

import android.car.drivingstate.CarUxRestrictions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;

import androidx.preference.PreferenceCategory;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.car.ui.preference.CarUiPreference;
import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.applications.RecentAppOpsAccess;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This controller displays a list of apps that recently access the location. Driver assistance apps
 * are also included.
 */
public class LocationRecentAccessesPreferenceController
        extends PreferenceController<PreferenceCategory> {

    private final LocationManager mLocationManager;
    private final BroadcastReceiver mAdasReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    refreshUi();
                }
            };
    private final BroadcastReceiver mLocationReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    refreshUi();
                }
            };

    private static final IntentFilter INTENT_FILTER_ADAS_GNSS_ENABLED_CHANGED =
            new IntentFilter(LocationManager.ACTION_ADAS_GNSS_ENABLED_CHANGED);

    private static final IntentFilter INTENT_FILTER_LOCATION_MODE_CHANGED =
            new IntentFilter(LocationManager.MODE_CHANGED_ACTION);

    private final Set<CarUiPreference> mAddedPreferences = new HashSet<>();

    private final RecentAppOpsAccess mRecentLocationAccesses;
    private final int mRecentAppsMaxCount;

    public LocationRecentAccessesPreferenceController(
            Context context,
            String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        this(
                context,
                preferenceKey,
                fragmentController,
                uxRestrictions,
                RecentAppOpsAccess.createForLocation(context),
                context.getResources().getInteger(R.integer.recent_location_access_apps_list_count),
                context.getSystemService(LocationManager.class));
    }

    @VisibleForTesting
    LocationRecentAccessesPreferenceController(
            Context context,
            String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions,
            RecentAppOpsAccess recentLocationAccesses,
            int recentAppsMaxCount,
            LocationManager locationManager) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mRecentLocationAccesses = recentLocationAccesses;
        mRecentAppsMaxCount = recentAppsMaxCount;
        mLocationManager = locationManager;
    }

    @Override
    protected Class<PreferenceCategory> getPreferenceType() {
        return PreferenceCategory.class;
    }

    @Override
    protected void onStartInternal() {
        getContext().registerReceiver(mAdasReceiver, INTENT_FILTER_ADAS_GNSS_ENABLED_CHANGED,
                Context.RECEIVER_EXPORTED);
        getContext().registerReceiver(mLocationReceiver, INTENT_FILTER_LOCATION_MODE_CHANGED,
                Context.RECEIVER_EXPORTED);
    }

    @Override
    protected void onStopInternal() {
        getContext().unregisterReceiver(mAdasReceiver);
        getContext().unregisterReceiver(mLocationReceiver);
    }

    @Override
    public void updateState(PreferenceCategory preference) {
        super.updateState(preference);
        boolean isVisible = getVisibility();
        preference.setVisible(isVisible);
        if (isVisible) {
            updateUi(loadData());
        }
    }

    private boolean getVisibility() {
        boolean isVisible = mLocationManager.isLocationEnabled();
        if (LocationUtil.isDriverWithAdasApps(getContext())) {
            isVisible = isVisible || mLocationManager.isAdasGnssLocationEnabled();
        }
        return isVisible;
    }

    private List<RecentAppOpsAccess.Access> loadData() {
        return mRecentLocationAccesses.getAppListSorted(/* showSystem= */ false);
    }

    private boolean hasAtLeastOneRecentAppAccess() {
        return !mRecentLocationAccesses.getAppListSorted(/* showSystem= */ true).isEmpty();
    }

    private void updateUi(List<RecentAppOpsAccess.Access> sortedRecentLocationAccesses) {
        // remove any already added preferences
        for (CarUiPreference addedPreference : mAddedPreferences) {
            getPreference().removePreference(addedPreference);
        }
        mAddedPreferences.clear();

        if (sortedRecentLocationAccesses.isEmpty()) {
            CarUiPreference emptyPreference = createNoRecentAccessPreference();
            getPreference().addPreference(emptyPreference);
            mAddedPreferences.add(emptyPreference);
        } else {
            int count = Math.min(sortedRecentLocationAccesses.size(), mRecentAppsMaxCount);
            for (int i = 0; i < count; i++) {
                RecentAppOpsAccess.Access request = sortedRecentLocationAccesses.get(i);
                CarUiPreference appPreference =
                        LocationRecentAccessUtil.createAppPreference(getContext(), request);
                getPreference().addPreference(appPreference);
                mAddedPreferences.add(appPreference);
            }
        }

        if (hasAtLeastOneRecentAppAccess()) {
            CarUiPreference viewAllPreference = createViewAllPreference();
            getPreference().addPreference(viewAllPreference);
            mAddedPreferences.add(viewAllPreference);
        }
    }

    private CarUiPreference createNoRecentAccessPreference() {
        CarUiPreference preference = new CarUiPreference(getContext());
        preference.setTitle(R.string.location_no_recent_access);
        preference.setSelectable(false);
        return preference;
    }

    private CarUiPreference createViewAllPreference() {
        CarUiPreference preference = new CarUiPreference(getContext());
        preference.setTitle(R.string.location_settings_recently_accessed_view_all_title);
        preference.setIcon(R.drawable.ic_apps);
        preference.setOnPreferenceClickListener(
                p -> {
                    getFragmentController()
                            .launchFragment(new LocationRecentAccessViewAllFragment());
                    return true;
                });
        return preference;
    }
}
