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

import com.android.car.settings.common.ColoredSwitchPreference;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.camera.flags.Flags;

/** Business logic for controlling the mute camera toggle. */
public class CameraTogglePreferenceController
        extends PreferenceController<ColoredSwitchPreference> {

    private final SensorPrivacyManager mSensorPrivacyManager;
    private final SensorPrivacyManager.OnSensorPrivacyChangedListener mListener =
            (sensor, enabled) -> refreshUi();

    public CameraTogglePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        this(context, preferenceKey, fragmentController, uxRestrictions,
                SensorPrivacyManager.getInstance(context));
    }

    @VisibleForTesting
    CameraTogglePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions,
            SensorPrivacyManager sensorPrivacyManager) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mSensorPrivacyManager = sensorPrivacyManager;
    }

    @Override
    protected Class<ColoredSwitchPreference> getPreferenceType() {
        return ColoredSwitchPreference.class;
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
    protected boolean handlePreferenceChanged(ColoredSwitchPreference preference,
            Object newValue) {
        boolean isChecked = (Boolean) newValue;
        boolean isCameraMuted = mSensorPrivacyManager.isSensorPrivacyEnabled(
                SensorPrivacyManager.Sensors.CAMERA);
        if (isChecked == isCameraMuted) {
            mSensorPrivacyManager.setSensorPrivacyForProfileGroup(
                    SensorPrivacyManager.Sources.SETTINGS,
                    SensorPrivacyManager.Sensors.CAMERA,
                    !isChecked);
        }
        return true;
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        boolean hasFeatureCameraToggle = mSensorPrivacyManager.supportsSensorToggle(
                SensorPrivacyManager.Sensors.CAMERA);
        if (Flags.cameraPrivacyAllowlist()) {
            boolean emptyCameraPrivacyAllowlist =
                    mSensorPrivacyManager.getCameraPrivacyAllowlist().isEmpty();
            return hasFeatureCameraToggle && emptyCameraPrivacyAllowlist
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
        }
        return hasFeatureCameraToggle ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    protected void updateState(ColoredSwitchPreference preference) {
        preference.setChecked(!mSensorPrivacyManager.isSensorPrivacyEnabled(
                SensorPrivacyManager.Sensors.CAMERA));
    }
}
