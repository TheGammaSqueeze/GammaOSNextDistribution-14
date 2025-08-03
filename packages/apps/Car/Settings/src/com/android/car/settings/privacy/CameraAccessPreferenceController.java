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
import android.hardware.SensorPrivacyManager;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.camera.flags.Flags;

/**
 * Controller for displaying camera access page.
 */
@FlaggedApi(Flags.FLAG_CAMERA_PRIVACY_ALLOWLIST)
public class CameraAccessPreferenceController extends PreferenceController<Preference> {
    private final SensorPrivacyManager mSensorPrivacyManager;

    public CameraAccessPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        this(context, preferenceKey, fragmentController, uxRestrictions,
                SensorPrivacyManager.getInstance(context));
    }

    @VisibleForTesting
    CameraAccessPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions,
            SensorPrivacyManager sensorPrivacyManager) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mSensorPrivacyManager = sensorPrivacyManager;
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        if (Flags.cameraPrivacyAllowlist()) {
            return mSensorPrivacyManager.getCameraPrivacyAllowlist().isEmpty()
                    ? CONDITIONALLY_UNAVAILABLE
                    : AVAILABLE;
        }
        return CONDITIONALLY_UNAVAILABLE;
    }
}
