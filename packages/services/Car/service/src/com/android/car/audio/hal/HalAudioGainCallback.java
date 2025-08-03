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

package com.android.car.audio.hal;

import android.annotation.IntDef;
import android.hardware.automotive.audiocontrol.AudioGainConfigInfo;
import android.hardware.automotive.audiocontrol.Reasons;

import com.android.car.audio.CarAudioGainConfigInfo;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * Audio Gain Callback interface to abstract away the specific HAL version
 */
public interface HalAudioGainCallback {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                Reasons.FORCED_MASTER_MUTE,
                Reasons.REMOTE_MUTE,
                Reasons.TCU_MUTE,
                Reasons.ADAS_DUCKING,
                Reasons.NAV_DUCKING,
                Reasons.PROJECTION_DUCKING,
                Reasons.THERMAL_LIMITATION,
                Reasons.SUSPEND_EXIT_VOL_LIMITATION,
                Reasons.EXTERNAL_AMP_VOL_FEEDBACK,
                Reasons.OTHER
            })
    public @interface HalReason {}

    /** Determines if the {@code HalReason} is valid */
    static boolean isReasonValid(@HalReason int reason) {
        switch (reason) {
            case Reasons.FORCED_MASTER_MUTE:
            case Reasons.REMOTE_MUTE:
            case Reasons.TCU_MUTE:
            case Reasons.ADAS_DUCKING:
            case Reasons.NAV_DUCKING:
            case Reasons.PROJECTION_DUCKING:
            case Reasons.THERMAL_LIMITATION:
            case Reasons.SUSPEND_EXIT_VOL_LIMITATION:
            case Reasons.EXTERNAL_AMP_VOL_FEEDBACK:
            case Reasons.OTHER:
                return true;
            default:
                return false;
        }
    }

    /** Converts the {@code HalReason} to String */
    static String reasonToString(@HalReason int reason) {
        switch (reason) {
            case Reasons.FORCED_MASTER_MUTE:
                return "FORCED_MASTER_MUTE";
            case Reasons.REMOTE_MUTE:
                return "REMOTE_MUTE";
            case Reasons.TCU_MUTE:
                return "TCU_MUTE";
            case Reasons.ADAS_DUCKING:
                return "ADAS_DUCKING";
            case Reasons.NAV_DUCKING:
                return "NAV_DUCKING";
            case Reasons.PROJECTION_DUCKING:
                return "PROJECTION_DUCKING";
            case Reasons.THERMAL_LIMITATION:
                return "THERMAL_LIMITATION";
            case Reasons.SUSPEND_EXIT_VOL_LIMITATION:
                return "SUSPEND_EXIT_VOL_LIMITATION";
            case Reasons.EXTERNAL_AMP_VOL_FEEDBACK:
                return "EXTERNAL_AMP_VOL_FEEDBACK";
            case Reasons.OTHER:
                return "OTHER";
            default:
                return "Unsupported reason int " + reason;
        }
    }

    /**
     * Converts the {@code AudioGainConfigInfo} to its string representation
     */
    static String gainToString(AudioGainConfigInfo audioGainConfigInfo) {
        // Java toString helper missing at aidl side
        return "zone: "
                + audioGainConfigInfo.zoneId
                + ", address: "
                + audioGainConfigInfo.devicePortAddress
                + ", Volume Index: "
                + audioGainConfigInfo.volumeIndex;
    }

    /**
     * Notify of Audio Gain changed for given {@code halReasons} for the given {@code gains}.
     */
    void onAudioDeviceGainsChanged(List<Integer> halReasons, List<CarAudioGainConfigInfo> gains);
}
