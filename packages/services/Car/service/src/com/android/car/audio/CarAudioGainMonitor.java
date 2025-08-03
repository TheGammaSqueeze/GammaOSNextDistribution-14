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

package com.android.car.audio;

import android.annotation.NonNull;
import android.car.builtin.util.Slogf;
import android.car.media.CarVolumeGroupEvent;
import android.hardware.automotive.audiocontrol.Reasons;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.car.CarLog;
import com.android.car.audio.hal.AudioControlWrapper;
import com.android.car.audio.hal.HalAudioGainCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Provides audio gain callback registration helpers and implements AudioGain listener business
 * logic.
 */
/* package */ final class CarAudioGainMonitor {
    private static final int INVALID_INFO = -1;
    @NonNull private final AudioControlWrapper mAudioControlWrapper;
    @NonNull private final SparseArray<CarAudioZone> mCarAudioZones;
    private final CarVolumeInfoWrapper mCarVolumeInfoWrapper;

    CarAudioGainMonitor(AudioControlWrapper audioControlWrapper,
            CarVolumeInfoWrapper carVolumeInfoWrapper, SparseArray<CarAudioZone> carAudioZones) {
        mAudioControlWrapper = Objects.requireNonNull(audioControlWrapper,
                "Audio Control Wrapper can not be null");
        mCarVolumeInfoWrapper = Objects.requireNonNull(carVolumeInfoWrapper,
                "Car volume info wrapper can not be null");
        mCarAudioZones = Objects.requireNonNull(carAudioZones, "Car Audio Zones can not be null");
    }

    public void reset() {
        // TODO (b/224885748): handle specific logic on IAudioControl service died event
    }

    /**
     * Registers {@code HalAudioGainCallback} on {@code AudioControlWrapper} to receive HAL audio
     * gain change notifications.
     */
    public void registerAudioGainListener(HalAudioGainCallback callback) {
        Objects.requireNonNull(callback, "Hal Audio Gain callback can not be null");
        mAudioControlWrapper.registerAudioGainCallback(callback);
    }

    /** Unregisters {@code HalAudioGainCallback} from {@code AudioControlWrapper}. */
    public void unregisterAudioGainListener() {
        mAudioControlWrapper.unregisterAudioGainCallback();
    }

    /**
     * Audio Gain event dispatcher. Implements the callback that triggered from {@link
     * IAudioGainCallback#onAudioDeviceGainsChanged} with the list of reasons and the list of {@link
     * CarAudioGainConfigInfo} involved. It is in charge of dispatching /delegating to the zone the
     * {@link CarAudioGainConfigInfo} belongs the processing of the callback.
     */
    void handleAudioDeviceGainsChanged(List<Integer> reasons, List<CarAudioGainConfigInfo> gains) {
        List<CarVolumeGroupEvent> events = new ArrayList<>();
        // Delegate to CarAudioZone / CarVolumeGroup
        // Group gains by Audio Zones first
        SparseArray<List<CarAudioGainConfigInfo>> gainsByZones = new SparseArray<>();
        for (int index = 0; index < gains.size(); index++) {
            CarAudioGainConfigInfo gain = gains.get(index);
            int zone = gain.getZoneId();
            if (!gainsByZones.contains(zone)) {
                gainsByZones.put(zone, new ArrayList<>(1));
            }
            gainsByZones.get(zone).add(gain);
        }
        for (int i = 0; i < gainsByZones.size(); i++) {
            int zoneId = gainsByZones.keyAt(i);
            if (!mCarAudioZones.contains(zoneId)) {
                Slogf.e(
                        CarLog.TAG_AUDIO,
                        "onAudioDeviceGainsChanged reported change on invalid "
                                + "zone: %d, reasons=%s, gains=%s",
                        zoneId,
                        reasons,
                        gains);
                continue;
            }
            CarAudioZone carAudioZone = mCarAudioZones.get(zoneId);
            events.addAll(carAudioZone.onAudioGainChanged(reasons, gainsByZones.valueAt(i)));
        }

        // its possible we received redundant callbacks from hal. In such cases,
        // do not call listeners with empty events.
        if (events.isEmpty()) {
            Slogf.w(CarLog.TAG_AUDIO, "Audio gain config callback resulted in no events!");
            return;
        }
        mCarVolumeInfoWrapper.onVolumeGroupEvent(events);
    }

    static boolean shouldBlockVolumeRequest(List<Integer> reasons) {
        return reasons.contains(Reasons.FORCED_MASTER_MUTE) || reasons.contains(Reasons.TCU_MUTE)
                || reasons.contains(Reasons.REMOTE_MUTE);
    }

    static boolean shouldLimitVolume(List<Integer> reasons) {
        return reasons.contains(Reasons.THERMAL_LIMITATION)
                || reasons.contains(Reasons.SUSPEND_EXIT_VOL_LIMITATION);
    }

    static boolean shouldDuckGain(List<Integer> reasons) {
        return reasons.contains(Reasons.ADAS_DUCKING) || reasons.contains(Reasons.NAV_DUCKING);
    }

    static boolean shouldMuteVolumeGroup(List<Integer> reasons) {
        return reasons.contains(Reasons.TCU_MUTE) || reasons.contains(Reasons.REMOTE_MUTE);
    }

    static boolean shouldUpdateVolumeIndex(List<Integer> reasons) {
        return reasons.contains(Reasons.EXTERNAL_AMP_VOL_FEEDBACK);
    }

    private static final SparseIntArray REASONS_TO_EXTRA_INFO = new SparseIntArray();

    // note: Reasons.FORCED_MASTER_MUTE, Reasons.OTHER are not supported by CarVolumeGroupEvent
    //       extra-infos. Builder will automatically append EXTRA_INFO_NONE for these cases.
    static {
        REASONS_TO_EXTRA_INFO.put(Reasons.REMOTE_MUTE,
                CarVolumeGroupEvent.EXTRA_INFO_MUTE_TOGGLED_BY_AUDIO_SYSTEM);
        REASONS_TO_EXTRA_INFO.put(Reasons.TCU_MUTE,
                CarVolumeGroupEvent.EXTRA_INFO_MUTE_TOGGLED_BY_EMERGENCY);
        REASONS_TO_EXTRA_INFO.put(Reasons.ADAS_DUCKING,
                CarVolumeGroupEvent.EXTRA_INFO_TRANSIENT_ATTENUATION_EXTERNAL);
        REASONS_TO_EXTRA_INFO.put(Reasons.NAV_DUCKING,
                CarVolumeGroupEvent.EXTRA_INFO_TRANSIENT_ATTENUATION_NAVIGATION);
        REASONS_TO_EXTRA_INFO.put(Reasons.PROJECTION_DUCKING,
                CarVolumeGroupEvent.EXTRA_INFO_TRANSIENT_ATTENUATION_PROJECTION);
        REASONS_TO_EXTRA_INFO.put(Reasons.THERMAL_LIMITATION,
                CarVolumeGroupEvent.EXTRA_INFO_TRANSIENT_ATTENUATION_THERMAL);
        REASONS_TO_EXTRA_INFO.put(Reasons.SUSPEND_EXIT_VOL_LIMITATION,
                CarVolumeGroupEvent.EXTRA_INFO_ATTENUATION_ACTIVATION);
        REASONS_TO_EXTRA_INFO.put(Reasons.EXTERNAL_AMP_VOL_FEEDBACK,
                CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_AUDIO_SYSTEM);
    }

    static List<Integer> convertReasonsToExtraInfo(List<Integer> reasons) {
        List<Integer> extraInfos = new ArrayList<>();
        for (int index = 0; index < reasons.size(); index++) {
            // cannot assume validaty of reason.
            // also not all reasons are supported in extra-info
            int extraInfo = REASONS_TO_EXTRA_INFO.get(reasons.get(index), INVALID_INFO);
            if (extraInfo != INVALID_INFO) {
                extraInfos.add(extraInfo);
            }
        }
        return extraInfos;
    }
}
