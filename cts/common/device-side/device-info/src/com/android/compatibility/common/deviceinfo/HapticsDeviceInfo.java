/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.compatibility.common.deviceinfo;

import android.content.res.Resources;
import android.media.AudioManager;
import android.media.audiofx.HapticGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.vibrator.VibratorFrequencyProfile;

import com.android.compatibility.common.util.DeviceInfoStore;

import java.util.Objects;

/**
 * Haptics device info collector.
 */
public final class HapticsDeviceInfo extends DeviceInfo {

    private static final String LOG_TAG = "HapticsDeviceInfo";
    private static final String ANONYMOUS_GROUP_NAME = null;  // marker for within array

    // Scan a few IDs above the current top one.
    private static final int MAX_EFFECT_ID = VibrationEffect.EFFECT_TEXTURE_TICK + 10;
    private static final int MAX_PRIMITIVE_ID = VibrationEffect.Composition.PRIMITIVE_LOW_TICK + 10;

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        collectVibratorInfo(store, "system_vibrator",
                getContext().getSystemService(Vibrator.class));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = getContext().getSystemService(VibratorManager.class);
            store.startArray("manager_vibrators");
            for (int id : manager.getVibratorIds()) {
                collectVibratorInfo(store, ANONYMOUS_GROUP_NAME, manager.getVibrator(id));
            }
            store.endArray();
        }

        collectHapticsDeviceConfig(store);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            store.addResult("audio_manager_is_haptic_playback_supported",
                    getContext().getSystemService(AudioManager.class).isHapticPlaybackSupported());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            store.addResult("haptic_generator_is_available", HapticGenerator.isAvailable());
        }
    }

    /**
     * Collect info for a vibrator into a group. If the group is part of an array, the groupName
     * should be {@code ANONYMOUS_GROUP_NAME}.
     */
    private void collectVibratorInfo(DeviceInfoStore store, String groupName, Vibrator vibrator)
            throws Exception {
        Objects.requireNonNull(vibrator);
        if (Objects.equals(groupName, ANONYMOUS_GROUP_NAME)) {
            store.startGroup();  // Within an array.
        } else {
            store.startGroup(groupName);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            store.addResult("has_vibrator", vibrator.hasVibrator());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            store.addResult("has_amplitude_control", vibrator.hasAmplitudeControl());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            collectEffectsSupport(store, vibrator);
            collectPrimitivesSupport(store, vibrator);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            store.addResult("vibrator_id", vibrator.getId());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            store.addResult("has_frequency_control", vibrator.hasFrequencyControl());
            store.addResult("q_factor", vibrator.getQFactor());
            store.addResult("resonant_frequency", vibrator.getResonantFrequency());
            VibratorFrequencyProfile frequencyProfile = vibrator.getFrequencyProfile();
            if (frequencyProfile != null) {
                store.startGroup("frequency_profile");
                store.addResult("min_frequency", frequencyProfile.getMinFrequency());
                store.addResult("max_frequency", frequencyProfile.getMaxFrequency());
                store.addResult("max_amplitude_measurement_interval",
                        frequencyProfile.getMaxAmplitudeMeasurementInterval());
                store.addArrayResult("max_amplitude_measurements",
                        frequencyProfile.getMaxAmplitudeMeasurements());
                store.endGroup();
            }
        }
        store.endGroup();
    }

    private void collectEffectsSupport(DeviceInfoStore store, Vibrator vibrator) throws Exception {
        // Effectively checks whether the HAL declares effect support or not.
        store.addResult("effect_support_returns_unknown",
                vibrator.areAllEffectsSupported(VibrationEffect.EFFECT_CLICK)
                        == Vibrator.VIBRATION_EFFECT_SUPPORT_UNKNOWN);
        int[] effectsToCheck = new int[MAX_EFFECT_ID + 1];
        for (int i = 0; i < effectsToCheck.length; ++i) {
            effectsToCheck[i] = i;
        }
        int[] results = vibrator.areEffectsSupported(effectsToCheck);
        store.startArray("supported_effects");
        for (int i = 0; i < results.length; ++i) {
            if (results[i] == Vibrator.VIBRATION_EFFECT_SUPPORT_YES) {
                store.startGroup();
                store.addResult("effect_id", i);
                store.endGroup();
            }
        }
        store.endArray();
    }

    private void collectPrimitivesSupport(DeviceInfoStore store, Vibrator vibrator)
            throws Exception {
        int[] primitivesToCheck = new int[MAX_PRIMITIVE_ID + 1];
        for (int i = 0; i < primitivesToCheck.length; ++i) {
            primitivesToCheck[i] = i;
        }
        boolean[] results = vibrator.arePrimitivesSupported(primitivesToCheck);
        int[] durations = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            durations = vibrator.getPrimitiveDurations(primitivesToCheck);
        }
        store.startArray("supported_primitives");
        for (int i = 0; i < results.length; ++i) {
            if (results[i]) {
                store.startGroup();
                store.addResult("primitive_id", i);
                if (durations != null) {
                    store.addResult("duration_ms", durations[i]);
                }
                store.endGroup();
            }
        }
        store.endArray();
    }

    private void collectHapticsDeviceConfig(DeviceInfoStore store) throws Exception {
        store.startGroup("haptics_device_config");
        collectConfigInt(store, "default_haptic_feedback_intensity",
                "config_defaultHapticFeedbackIntensity");
        collectConfigInt(store, "default_notification_vibration_intensity",
                "config_defaultNotificationVibrationIntensity");
        collectConfigInt(store, "default_ring_vibration_intensity",
                "config_defaultRingVibrationIntensity");
        collectConfigInt(store, "default_alarm_vibration_intensity",
                "config_defaultAlarmVibrationIntensity");
        collectConfigInt(store, "default_media_vibration_intensity",
                "config_defaultMediaVibrationIntensity");
        collectConfigInt(store, "default_vibration_amplitude",
                "config_defaultVibrationAmplitude");
        collectConfigDimension(store, "haptic_channel_max_vibration_amplitude",
                "config_hapticChannelMaxVibrationAmplitude");
        collectConfigArraySize(store, "ringtone_effect_uris_array_size",
                "config_ringtoneEffectUris");
        store.endGroup();
    }

    private void collectConfigInt(DeviceInfoStore store, String resultName, String configName)
            throws Exception {
        Resources res = getContext().getResources();
        int resId = res.getIdentifier(configName, "integer", "android");
        try {
            store.addResult(resultName, res.getInteger(resId));
        } catch (Resources.NotFoundException e) {
        }
    }

    private void collectConfigDimension(DeviceInfoStore store, String resultName, String configName)
            throws Exception {
        Resources res = getContext().getResources();
        int resId = res.getIdentifier(configName, "dimen", "android");
        try {
            store.addResult(resultName, res.getFloat(resId));
        } catch (Resources.NotFoundException e) {
        }
    }

    private void collectConfigArraySize(DeviceInfoStore store, String resultName, String configName)
            throws Exception {
        Resources res = getContext().getResources();
        int resId = res.getIdentifier(configName, "array", "android");
        try {
            store.addResult(resultName, res.getStringArray(resId).length);
        } catch (Resources.NotFoundException e) {
        }
    }
}
