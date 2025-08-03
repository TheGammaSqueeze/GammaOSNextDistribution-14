/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.annotation.FlaggedApi;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.SensorPrivacyManager;
import android.os.Process;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceController;
import com.android.car.ui.preference.CarUiTwoActionTextPreference;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.camera.flags.Flags;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Displays a list of apps which are required for driving with their privacy policy and a
 * link to their camera permission settings.
 */
@FlaggedApi(Flags.FLAG_CAMERA_PRIVACY_ALLOWLIST)
public final class CameraRequiredAppsPreferenceController
        extends PreferenceController<LogicalPreferenceGroup> {

    private final PackageManager mPackageManager;
    private final SensorPrivacyManager mSensorPrivacyManager;
    private Map<String, Boolean> mCameraPrivacyAllowlist;

    public CameraRequiredAppsPreferenceController(
            Context context,
            String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        this(
                context,
                preferenceKey,
                fragmentController,
                uxRestrictions,
                context.getPackageManager(), SensorPrivacyManager.getInstance(context));
    }

    @VisibleForTesting
    CameraRequiredAppsPreferenceController(
            Context context,
            String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions,
            PackageManager packageManager, SensorPrivacyManager sensorPrivacyManager) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mPackageManager = packageManager;
        mSensorPrivacyManager = sensorPrivacyManager;
        mCameraPrivacyAllowlist = mSensorPrivacyManager.getCameraPrivacyAllowlist();
    }

    @Override
    protected Class<LogicalPreferenceGroup> getPreferenceType() {
        return LogicalPreferenceGroup.class;
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        boolean hasRequiredApps = mCameraPrivacyAllowlist.containsValue(Boolean.TRUE);
        return hasRequiredApps ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();
        loadCameraRequiredAppsWithCameraPermission();
    }

    private void loadCameraRequiredAppsWithCameraPermission() {
        LogicalPreferenceGroup requiredappsPref = getPreference().findPreference(getContext()
                .getString(R.string.pk_camera_required_apps_policy));

        Set<String> requiredApps = mCameraPrivacyAllowlist.entrySet().stream()
                .filter(x -> (Boolean.TRUE).equals(x.getValue()))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        for (String app : requiredApps) {
            CarUiTwoActionTextPreference preference =
                    CameraPrivacyPolicyUtil.createPrivacyPolicyPreference(
                            getContext(), mPackageManager, app, Process.myUserHandle());
            if (preference != null) {
                requiredappsPref.addPreference(preference);
            }
        }
    }
}
