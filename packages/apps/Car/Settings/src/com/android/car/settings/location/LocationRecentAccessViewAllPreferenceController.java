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
import android.content.Context;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceController;
import com.android.car.ui.preference.CarUiPreference;
import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.applications.RecentAppOpsAccess;

import java.util.List;

/**
 * This controller displays a list of apps recently accessing location. Driver assistance apps are
 * also included.
 */
public class LocationRecentAccessViewAllPreferenceController
        extends PreferenceController<LogicalPreferenceGroup> {

    private final RecentAppOpsAccess mRecentLocationAccesses;
    private boolean mShowSystem = false;

    public LocationRecentAccessViewAllPreferenceController(
            Context context,
            String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        this(
                context,
                preferenceKey,
                fragmentController,
                uxRestrictions,
                RecentAppOpsAccess.createForLocation(context));
    }

    @VisibleForTesting
    LocationRecentAccessViewAllPreferenceController(
            Context context,
            String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions,
            RecentAppOpsAccess recentLocationAccesses) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mRecentLocationAccesses = recentLocationAccesses;
    }

    @Override
    protected Class<LogicalPreferenceGroup> getPreferenceType() {
        return LogicalPreferenceGroup.class;
    }

    @Override
    public void updateState(LogicalPreferenceGroup preference) {
        super.updateState(preference);
        List<RecentAppOpsAccess.Access> recentLocationAccesses = loadData();
        updateUi(recentLocationAccesses);
    }

    /**
     * Rebuilds the preference list to show system applications if {@code showSystem} is true.
     * System applications will be hidden otherwise.
     */
    public void setShowSystem(boolean showSystem) {
        if (mShowSystem != showSystem) {
            mShowSystem = showSystem;
            refreshUi();
        }
    }

    private List<RecentAppOpsAccess.Access> loadData() {
        return mRecentLocationAccesses.getAppListSorted(mShowSystem);
    }

    private void updateUi(List<RecentAppOpsAccess.Access> recentLocationAccesses) {
        getPreference().removeAll();
        if (recentLocationAccesses.isEmpty()) {
            getPreference().addPreference(createNoRecentAccessPreference());
        } else {
            for (RecentAppOpsAccess.Access access : recentLocationAccesses) {
                CarUiPreference appPreference =
                        LocationRecentAccessUtil.createAppPreference(getContext(), access);
                getPreference().addPreference(appPreference);
            }
        }
    }

    private CarUiPreference createNoRecentAccessPreference() {
        CarUiPreference preference = new CarUiPreference(getContext());
        preference.setTitle(R.string.location_no_recent_access);
        preference.setSelectable(false);
        return preference;
    }
}
