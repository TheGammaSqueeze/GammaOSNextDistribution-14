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

package com.android.car.settings.privacy;

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
 * This controller displays a list of recently used apps. Only non-system apps are displayed.
 */
public class MicrophoneRecentAccessViewAllPreferenceController extends
        PreferenceController<LogicalPreferenceGroup> {

    private final RecentAppOpsAccess mRecentMicrophoneAccesses;
    private boolean mShowSystem = false;

    public MicrophoneRecentAccessViewAllPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mRecentMicrophoneAccesses = RecentAppOpsAccess.createForMicrophone(context);
    }

    @VisibleForTesting
    MicrophoneRecentAccessViewAllPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions,
            RecentAppOpsAccess recentMicrophoneAccesses) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mRecentMicrophoneAccesses = recentMicrophoneAccesses;
    }

    @Override
    protected Class<LogicalPreferenceGroup> getPreferenceType() {
        return LogicalPreferenceGroup.class;
    }

    @Override
    public void updateState(LogicalPreferenceGroup preference) {
        super.updateState(preference);
        List<RecentAppOpsAccess.Access> recentMicrophoneAccesses = loadData();
        updateUi(recentMicrophoneAccesses);
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
        return mRecentMicrophoneAccesses.getAppListSorted(mShowSystem);
    }

    private void updateUi(List<RecentAppOpsAccess.Access> recentMicrophoneAccesses) {
        getPreference().removeAll();
        if (recentMicrophoneAccesses.isEmpty()) {
            getPreference().addPreference(createNoRecentAccessPreference());
        } else {
            for (RecentAppOpsAccess.Access request : recentMicrophoneAccesses) {
                CarUiPreference appPreference = MicrophoneRecentAccessUtil.createAppPreference(
                        getContext(),
                        request);
                getPreference().addPreference(appPreference);
            }
        }
    }

    private CarUiPreference createNoRecentAccessPreference() {
        CarUiPreference preference = new CarUiPreference(getContext());
        preference.setTitle(R.string.microphone_no_recent_access);
        preference.setSelectable(false);
        return preference;
    }
}

