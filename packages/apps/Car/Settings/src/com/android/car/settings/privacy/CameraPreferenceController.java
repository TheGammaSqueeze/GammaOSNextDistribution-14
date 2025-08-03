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
import android.content.pm.PackageManager;
import android.os.SystemProperties;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.car.ui.preference.CarUiPreference;

/** Business logic for controlling the privacy center camera setting. */
public class CameraPreferenceController
        extends PreferenceController<CarUiPreference> {
    private static final String DISABLE_CAMERASERVICE_SYSTEM_PROPERTY =
            "config.disable_cameraservice";
    public static final String PERMISSION_GROUP_CAMERA = "android.permission-group.CAMERA";

    private PackageManager mPm;

    public CameraPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mPm = context.getPackageManager();
    }

    @Override
    protected Class<CarUiPreference> getPreferenceType() {
        return CarUiPreference.class;
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        // Shows the camera preference controller if and only if the system has any camera device
        // and the camera service is not disabled.
        boolean hasCamera = mPm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        boolean isCameraServiceDisabled = SystemProperties.getBoolean(
                DISABLE_CAMERASERVICE_SYSTEM_PROPERTY, false);
        return (hasCamera && !isCameraServiceDisabled) ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }
}
