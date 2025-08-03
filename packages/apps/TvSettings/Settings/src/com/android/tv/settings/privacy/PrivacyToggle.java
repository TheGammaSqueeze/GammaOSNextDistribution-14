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

package com.android.tv.settings.privacy;

import android.annotation.DrawableRes;
import android.annotation.Nullable;
import android.annotation.StringRes;
import android.app.AppOpsManager;
import android.content.Context;
import android.hardware.SensorPrivacyManager;
import android.hardware.SensorPrivacyManager.Sensors.Sensor;
import android.provider.DeviceConfig;

import androidx.preference.Preference;

import com.android.tv.settings.R;

public enum PrivacyToggle {
    CAMERA_TOGGLE(
            R.string.camera,
            R.string.camera_toggle_title,
            R.string.camera_toggle_info_title,
            R.string.camera_toggle_info_content,
            R.string.camera_physical_privacy_enabled_title,
            R.string.camera_physical_privacy_enabled_text,
            R.drawable.ic_camera_off_base,
            R.drawable.camera_physical_privacy_enabled_panel_image,
            R.string.camera_physical_privacy_enabled_panel_text,
            R.string.open_camera_permissions,
            "android.permission-group.CAMERA",
            SensorPrivacyManager.Sensors.CAMERA,
            new int[]{AppOpsManager.OP_CAMERA, AppOpsManager.OP_PHONE_CALL_CAMERA},
            "camera_toggle_enabled"
    ),

    MIC_TOGGLE(
            R.string.microphone,
            R.string.mic_toggle_title,
            R.string.mic_toggle_info_title,
            R.string.mic_toggle_info_content,
            R.string.microphone_physical_privacy_enabled_title,
            R.string.microphone_physical_privacy_enabled_text,
            R.drawable.ic_mic_off_base,
            R.drawable.microphone_physical_privacy_enabled_panel_image,
            R.string.microphone_physical_privacy_enabled_panel_text,
            R.string.open_mic_permissions,
            "android.permission-group.MICROPHONE",
            SensorPrivacyManager.Sensors.MICROPHONE,
            new int[]{AppOpsManager.OP_RECORD_AUDIO, AppOpsManager.OP_PHONE_CALL_MICROPHONE,
                    AppOpsManager.OP_RECEIVE_EXPLICIT_USER_INTERACTION_AUDIO},
            "mic_toggle_enabled"
    );

    @StringRes
    public final int screenTitle;
    @StringRes
    public final int toggleTitle;
    @StringRes
    public final int toggleInfoTitle;
    @StringRes
    public final int toggleInfoText;
    @StringRes
    public final int appPermissionsTitle;
    @StringRes
    public final int physicalPrivacyEnabledInfoTitle;
    @StringRes
    public final int physicalPrivacyEnabledInfoText;
    @DrawableRes
    public final int physicalPrivacyEnabledIcon;
    @DrawableRes
    public final int physicalPrivacyEnabledInfoPanelImage;
    @StringRes
    public final int physicalPrivacyEnabledInfoPanelText;

    public final String permissionsGroupName;
    @Sensor
    public final int sensor;
    public final int[] appOps;
    public final String deviceConfigName;


    PrivacyToggle(@StringRes int screenTitle, @StringRes int toggleTitle,
            @StringRes int toggleInfoTitle, @StringRes int toggleInfoText,
            @StringRes int physicalPrivacyEnabledInfoTitle,
            @StringRes int physicalPrivacyEnabledInfoText,
            @DrawableRes int physicalPrivacyEnabledIcon,
            @DrawableRes int physicalPrivacyEnabledInfoPanelImage,
            @StringRes int physicalPrivacyEnabledInfoPanelText, @StringRes int appPermissionsTitle,
            String permissionsGroupName, @Sensor int sensor, int[] appOps,
            String deviceConfigName) {
        this.screenTitle = screenTitle;
        this.toggleTitle = toggleTitle;
        this.toggleInfoTitle = toggleInfoTitle;
        this.toggleInfoText = toggleInfoText;
        this.physicalPrivacyEnabledInfoTitle = physicalPrivacyEnabledInfoTitle;
        this.physicalPrivacyEnabledInfoText = physicalPrivacyEnabledInfoText;
        this.physicalPrivacyEnabledIcon = physicalPrivacyEnabledIcon;
        this.physicalPrivacyEnabledInfoPanelImage = physicalPrivacyEnabledInfoPanelImage;
        this.physicalPrivacyEnabledInfoPanelText = physicalPrivacyEnabledInfoPanelText;
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
    public void preparePreferenceWithSensorFragment(Context context,
            @Nullable Preference preference, String extrasKey) {
        if (preference == null) {
            return;
        }
        if (isPresentAndEnabled(context)) {
            preference.getExtras().putObject(extrasKey, this);
        } else {
            preference.setVisible(false);
        }
    }
}
