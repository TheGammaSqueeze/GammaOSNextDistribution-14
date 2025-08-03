/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom;

import android.content.Context;
import android.media.AudioManager;
import android.os.VibrationAttributes;
import android.os.Vibrator;
import android.provider.DeviceConfig;
import android.provider.Settings;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Accesses the Global System settings for more control during testing.
 */
@VisibleForTesting
public class SystemSettingsUtil {

    /** Flag for whether or not to support audio coupled haptics in ramping ringer. */
    private static final String RAMPING_RINGER_AUDIO_COUPLED_VIBRATION_ENABLED =
            "ramping_ringer_audio_coupled_vibration_enabled";

    public boolean isTheaterModeOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.THEATER_MODE_ON,
                0) == 1;
    }

    public boolean isRingVibrationEnabled(Context context) {
        // VIBRATE_WHEN_RINGING setting was deprecated, only RING_VIBRATION_INTENSITY controls the
        // ringtone vibrations on/off state now. Ramping ringer should only be applied when ring
        // vibration intensity is ON, otherwise the ringtone sound should not be delayed as there
        // will be no ring vibration.
        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.RING_VIBRATION_INTENSITY,
                context.getSystemService(Vibrator.class).getDefaultVibrationIntensity(
                        VibrationAttributes.USAGE_RINGTONE),
                context.getUserId()) != Vibrator.VIBRATION_INTENSITY_OFF;
    }

    public boolean isEnhancedCallBlockingEnabled(Context context) {
        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.DEBUG_ENABLE_ENHANCED_CALL_BLOCKING, 0, context.getUserId()) != 0;
    }

    public boolean setEnhancedCallBlockingEnabled(Context context, boolean enabled) {
        return Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.DEBUG_ENABLE_ENHANCED_CALL_BLOCKING, enabled ? 1 : 0,
                context.getUserId());
    }

    public boolean isRampingRingerEnabled(Context context) {
        return context.getSystemService(AudioManager.class).isRampingRingerEnabled();
    }

    public boolean isAudioCoupledVibrationForRampingRingerEnabled() {
        return DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_TELEPHONY,
                RAMPING_RINGER_AUDIO_COUPLED_VIBRATION_ENABLED, false);
    }

    public boolean isHapticPlaybackSupported(Context context) {
        return context.getSystemService(AudioManager.class).isHapticPlaybackSupported();
    }
}

