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

package com.android.car.settings.privacy;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.hardware.SensorPrivacyManager;

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
 * This controller displays a list of apps that recently access the camera. Only non-system apps
 * are displayed.
 */
public class CameraRecentAccessesPreferenceController extends
        PreferenceController<PreferenceCategory> {

    private final SensorPrivacyManager mSensorPrivacyManager;
    private final SensorPrivacyManager.OnSensorPrivacyChangedListener mListener =
            (sensor, enabled) -> refreshUi();
    private final Set<CarUiPreference> mAddedPreferences = new HashSet<>();

    private final RecentAppOpsAccess mRecentCameraAccesses;
    private final int mRecentAppsMaxCount;

    public CameraRecentAccessesPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        this(context, preferenceKey, fragmentController, uxRestrictions,
                RecentAppOpsAccess.createForCamera(context),
                context.getResources()
                        .getInteger(R.integer.recent_camera_access_apps_list_count),
                SensorPrivacyManager.getInstance(context));
    }

    @VisibleForTesting
    CameraRecentAccessesPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions,
            RecentAppOpsAccess recentCameraAccesses, int recentAppsMaxCount,
            SensorPrivacyManager sensorPrivacyManager) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mRecentCameraAccesses = recentCameraAccesses;
        mRecentAppsMaxCount = recentAppsMaxCount;
        mSensorPrivacyManager = sensorPrivacyManager;
    }

    @Override
    protected Class<PreferenceCategory> getPreferenceType() {
        return PreferenceCategory.class;
    }

    @Override
    protected void onStartInternal() {
        mSensorPrivacyManager.addSensorPrivacyListener(
                SensorPrivacyManager.Sensors.CAMERA, mListener);
    }

    @Override
    protected void onStopInternal() {
        mSensorPrivacyManager.removeSensorPrivacyListener(SensorPrivacyManager.Sensors.CAMERA,
                mListener);
    }

    @Override
    public void updateState(PreferenceCategory preference) {
        super.updateState(preference);
        if (mSensorPrivacyManager.isSensorPrivacyEnabled(
                SensorPrivacyManager.Sensors.CAMERA)) {
            getPreference().setVisible(false);
            return;
        }
        getPreference().setVisible(true);
        List<RecentAppOpsAccess.Access> sortedRecentCameraAccesses = loadData();
        updateUi(sortedRecentCameraAccesses);
    }

    private List<RecentAppOpsAccess.Access> loadData() {
        return mRecentCameraAccesses.getAppListSorted(/* showSystem= */ false);
    }

    private boolean hasAtLeastOneRecentAppAccess() {
        return !mRecentCameraAccesses.getAppListSorted(/* showSystem= */ true).isEmpty();
    }

    private void updateUi(List<RecentAppOpsAccess.Access> sortedRecentCameraAccesses) {
        // remove any already added preferences
        for (CarUiPreference addedPreference : mAddedPreferences) {
            getPreference().removePreference(addedPreference);
        }
        mAddedPreferences.clear();

        if (sortedRecentCameraAccesses.isEmpty()) {
            CarUiPreference emptyPreference = createNoRecentAccessPreference();
            getPreference().addPreference(emptyPreference);
            mAddedPreferences.add(emptyPreference);
        } else {
            int count = Math.min(sortedRecentCameraAccesses.size(), mRecentAppsMaxCount);
            for (int i = 0; i < count; i++) {
                RecentAppOpsAccess.Access request = sortedRecentCameraAccesses.get(i);
                CarUiPreference appPreference = CameraRecentAccessUtil.createAppPreference(
                        getContext(),
                        request);
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
        preference.setTitle(R.string.camera_no_recent_access);
        preference.setSelectable(false);
        return preference;
    }

    private CarUiPreference createViewAllPreference() {
        CarUiPreference preference = new CarUiPreference(getContext());
        preference.setTitle(R.string.camera_settings_recent_requests_view_all_title);
        preference.setIcon(R.drawable.ic_apps);
        preference.setOnPreferenceClickListener(p -> {
            getFragmentController().launchFragment(new CameraRecentAccessViewAllFragment());
            return true;
        });
        return preference;
    }
}
