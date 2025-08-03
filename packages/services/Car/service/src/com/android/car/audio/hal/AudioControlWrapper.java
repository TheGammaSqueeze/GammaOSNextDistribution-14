/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.audio.common.PlaybackTrackMetadata;
import android.hardware.automotive.audiocontrol.MutingInfo;

import com.android.car.audio.CarDuckingInfo;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * AudioControlWrapper wraps IAudioControl HAL interface, handling version specific support so that
 * the rest of CarAudioService doesn't need to know about it.
 */
public interface AudioControlWrapper {
    int AUDIOCONTROL_FEATURE_AUDIO_FOCUS = 0;
    int AUDIOCONTROL_FEATURE_AUDIO_DUCKING = 1;
    int AUDIOCONTROL_FEATURE_AUDIO_GROUP_MUTING = 2;
    int AUDIOCONTROL_FEATURE_AUDIO_FOCUS_WITH_METADATA = 3;
    int AUDIOCONTROL_FEATURE_AUDIO_GAIN_CALLBACK = 4;
    int AUDIOCONTROL_FEATURE_AUDIO_MODULE_CALLBACK = 5;

    @IntDef({
            AUDIOCONTROL_FEATURE_AUDIO_FOCUS,
            AUDIOCONTROL_FEATURE_AUDIO_DUCKING,
            AUDIOCONTROL_FEATURE_AUDIO_GROUP_MUTING,
            AUDIOCONTROL_FEATURE_AUDIO_FOCUS_WITH_METADATA,
            AUDIOCONTROL_FEATURE_AUDIO_GAIN_CALLBACK,
            AUDIOCONTROL_FEATURE_AUDIO_MODULE_CALLBACK,
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface AudioControlFeature {
    }

    /**
     * Closes the focus listener that's registered on the AudioControl HAL
     */
    void unregisterFocusListener();

    /**
     * Indicates if HAL can support specified feature
     *
     * @param feature to check support for. it's expected to be one of the features defined by
     * {@link AudioControlWrapper.AudioControlFeature}.
     * @return boolean indicating whether feature is supported
     */
    boolean supportsFeature(@AudioControlFeature int feature);

    /**
     * Registers listener for HAL audio focus requests with IAudioControl. Only works if
     * {@code supportsHalAudioFocus} returns true.
     *
     * @param focusListener the listener to register on the IAudioControl HAL.
     */
    void registerFocusListener(HalFocusListener focusListener);

    /**
     * Registers callback for HAL audio gain changed notification with IAudioControl. Only works if
     * {@code supportsHalAudioGainCallback} returns true.
     *
     * @param gainCallback the callback to register on the IAudioControl HAL.
     */
    void registerAudioGainCallback(@NonNull HalAudioGainCallback gainCallback);

    /**
     * Closes the audio gain callback registered on the AudioControl HAL
     */
    void unregisterAudioGainCallback();

    /**
     * Notifies HAL of change in audio focus for a request it has made.
     *
     * @param metadata {@link PlaybackTrackMetadata} that the request is associated with.
     * @param zoneId for the audio zone that the request is associated with.
     * @param focusChange the new status of the request.
     */
    void onAudioFocusChange(PlaybackTrackMetadata metadata, int zoneId, int focusChange);

    /**
     * dumps the current state of the AudioControlWrapper
     *
     * @param writer stream to write current state
     */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer);

    /**
     * Sets the fade for the vehicle.
     *
     * @param value to set for the fade. Positive is towards front.
     */
    void setFadeTowardFront(float value);

    /**
     * Sets the balance value for the vehicle.
     *
     * @param value to set for the balance. Positive is towards the right.
     */
    void setBalanceTowardRight(float value);

    /**
     * Notifies HAL of changes in usages holding focus and the corresponding ducking changes for a
     * given zone.
     *
     * @param carDuckingInfos list of information about focus and addresses to duck for each
     * impacted zone to relay to the HAL.
     */
    void onDevicesToDuckChange(@NonNull List<CarDuckingInfo> carDuckingInfos);

    /**
     * Notifies HAL of changes in muting changes for all audio zones.
     *
     * @param carZonesMutingInfo list of information about addresses to mute to relay to the HAL.
     */
    void onDevicesToMuteChange(@NonNull List<MutingInfo> carZonesMutingInfo);

    /**
     * Registers callback for HAL audio module change notification with IAudioControl. Only works
     * if {@code supportsHalAudioModuleChangeCallback} returns true.
     *
     * @param moduleChangeCallback the callback to register on the IAudioControl HAL.
     */
    void setModuleChangeCallback(HalAudioModuleChangeCallback moduleChangeCallback);

    /**
     * Clears all module change callbacks that's registered on the AudioControl HAL
     */
    void clearModuleChangeCallback();

    /**
     * Registers recipient to be notified if AudioControl HAL service dies.
     *
     * @param deathRecipient to be notified upon HAL service death.
     */
    void linkToDeath(@Nullable AudioControlDeathRecipient deathRecipient);

    /**
     * Unregisters recipient for AudioControl HAL service death.
     */
    void unlinkToDeath();

    /**
     * Recipient to be notified upon death of AudioControl HAL.
     */
    interface AudioControlDeathRecipient {
        /**
         * Called if AudioControl HAL dies.
         */
        void serviceDied();
    }
}
