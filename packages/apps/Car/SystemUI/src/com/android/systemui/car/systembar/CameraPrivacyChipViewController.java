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

package com.android.systemui.car.systembar;

import static android.hardware.SensorPrivacyManager.Sensors.CAMERA;

import android.content.Context;
import android.hardware.SensorPrivacyManager;

import androidx.annotation.IdRes;

import com.android.systemui.R;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.privacy.PrivacyItemController;
import com.android.systemui.privacy.PrivacyType;
import com.android.systemui.settings.UserTracker;

import javax.inject.Inject;

/** Controls a Camera Privacy Chip view in system icons. */
@SysUISingleton
public class CameraPrivacyChipViewController extends PrivacyChipViewController {

    @Inject
    public CameraPrivacyChipViewController(Context context,
            PrivacyItemController privacyItemController,
            SensorPrivacyManager sensorPrivacyManager,
            UserTracker userTracker) {
        super(context, privacyItemController, sensorPrivacyManager, userTracker);
    }

    @Override
    protected @SensorPrivacyManager.Sensors.Sensor int getChipSensor() {
        return CAMERA;
    }

    @Override
    protected PrivacyType getChipPrivacyType() {
        return PrivacyType.TYPE_CAMERA;
    }

    @Override
    protected @IdRes int getChipResourceId() {
        return R.id.camera_privacy_chip;
    }
}
