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

package com.android.tv.settings.library.privacy;

import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.content.Context;
import android.hardware.SensorPrivacyManager;
import android.provider.DeviceConfig;

import com.android.tv.settings.library.PreferenceCompat;

public enum PrivacyToggle {
    CAMERA_TOGGLE(
            "camera",
            "camera_toggle_title",
            "camera_toggle_info_title",
            "camera_toggle_info_content",
            "open_camera_permissions",
            "android.permission-group.CAMERA",
            SensorPrivacyManager.Sensors.CAMERA,
            new int[]{AppOpsManager.OP_CAMERA, AppOpsManager.OP_PHONE_CALL_CAMERA},
            "camera_toggle_enabled"
    ),

    MIC_TOGGLE(
            "microphone",
            "mic_toggle_title",
            "mic_toggle_info_title",
            "mic_toggle_info_content",
            "open_mic_permissions",
            "android.permission-group.MICROPHONE",
            SensorPrivacyManager.Sensors.MICROPHONE,
            new int[]{AppOpsManager.OP_RECORD_AUDIO, AppOpsManager.OP_PHONE_CALL_MICROPHONE},
            "mic_toggle_enabled"
    );

    public final String screenTitle;
    public final String toggleTitle;
    public final String toggleInfoTitle;
    public final String toggleInfoText;
    public final String appPermissionsTitle;
    public final String permissionsGroupName;
    @SensorPrivacyManager.Sensors.Sensor
    public final int sensor;
    public final int[] appOps;
    public final String deviceConfigName;

    PrivacyToggle(String screenTitle, String toggleTitle, String toggleInfoTitle,
            String toggleInfoText,
            String appPermissionsTitle, String permissionsGroupName,
            @SensorPrivacyManager.Sensors.Sensor int sensor, int[] appOps,
            String deviceConfigName) {
        this.screenTitle = screenTitle;
        this.toggleTitle = toggleTitle;
        this.toggleInfoTitle = toggleInfoTitle;
        this.toggleInfoText = toggleInfoText;
        this.appPermissionsTitle = appPermissionsTitle;
        this.permissionsGroupName = permissionsGroupName;
        this.sensor = sensor;
        this.appOps = appOps;
        this.deviceConfigName = deviceConfigName;
    }

    /**
     * Checks if the privacy toggle should be shown.
     */
    public boolean isPresentAndEnabled(Context context) {
        return context.getSystemService(SensorPrivacyManager.class).supportsSensorToggle(
                sensor) && DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_PRIVACY,
                deviceConfigName, /* defaultValue= */ true);
    }

    /**
     * Hides the preference if the toggle shouldn't be shown and adds the toggle to the extras so
     * the SensorFragment knows which sensor is meant.
     */
    public void preparePreferenceWithSensorState(Context context,
            @Nullable PreferenceCompat prefCompat, String extrasKey) {
        if (prefCompat == null) {
            return;
        }
        if (isPresentAndEnabled(context)) {
            prefCompat.addInfo(extrasKey, this);
        } else {
            prefCompat.setVisible(false);
        }
    }
}
