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

import android.annotation.Nullable;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import com.android.car.settings.common.FragmentController;
import com.android.car.settingslib.applications.ApplicationFeatureProvider;
import com.android.internal.annotations.VisibleForTesting;

/**
 * PreferenceController that builds a dynamic list of applications that were granted the camera
 * permissions by the device admin.
 */
public class AdminGrantedCameraPermissionApplicationListPreferenceController
        extends BaseAdminGrantedPermissionsApplicationListPreferenceController {

    public AdminGrantedCameraPermissionApplicationListPreferenceController(Context context,
            String preferenceKey, FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        this(context, preferenceKey, fragmentController, uxRestrictions, /* provider= */ null);
    }

    @VisibleForTesting
    AdminGrantedCameraPermissionApplicationListPreferenceController(Context context,
            String preferenceKey, FragmentController fragmentController,
            CarUxRestrictions uxRestrictions, @Nullable ApplicationFeatureProvider provider) {
        super(context, preferenceKey, fragmentController, uxRestrictions, provider,
                EnterpriseUtils.CAMERA_PERMISSIONS);
    }
}
