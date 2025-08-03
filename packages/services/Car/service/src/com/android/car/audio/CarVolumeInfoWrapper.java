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

import android.car.media.CarVolumeGroupEvent;
import android.car.media.CarVolumeGroupInfo;
import android.media.AudioAttributes;

import java.util.List;
import java.util.Objects;

final class CarVolumeInfoWrapper {
    private final CarAudioService mCarAudioService;

    CarVolumeInfoWrapper(CarAudioService carAudioService) {
        mCarAudioService = Objects.requireNonNull(carAudioService,
                "Car Audio Service Can not be null");
    }

    public int getSuggestedAudioContextForZone(int zoneId) {
        return mCarAudioService.getSuggestedAudioContextForZone(zoneId);
    }

    public int getVolumeGroupIdForAudioZone(int zoneId) {
        return mCarAudioService.getVolumeGroupIdForAudioContext(zoneId,
                getSuggestedAudioContextForZone(zoneId));
    }

    public int getGroupVolume(int zoneId, int groupId) {
        return mCarAudioService.getGroupVolume(zoneId, groupId);
    }

    public int getGroupMinVolume(int zoneId, int groupId) {
        return mCarAudioService.getGroupMinVolume(zoneId, groupId);
    }

    public int getGroupMaxVolume(int zoneId, int groupId) {
        return mCarAudioService.getGroupMaxVolume(zoneId, groupId);
    }

    public boolean isVolumeGroupMuted(int zoneId, int groupId) {
        return mCarAudioService.isVolumeGroupMuted(zoneId, groupId);
    }

    public List<CarVolumeGroupInfo> getMutedVolumeGroups(int zoneId) {
        return mCarAudioService.getMutedVolumeGroups(zoneId);
    }

    public CarVolumeGroupInfo getVolumeGroupInfo(int zoneId, int groupId) {
        return mCarAudioService.getVolumeGroupInfo(zoneId, groupId);
    }

    public int getVolumeGroupIdForAudioAttribute(int audioZoneId, AudioAttributes attributes) {
        return mCarAudioService.getVolumeGroupIdForAudioAttribute(audioZoneId, attributes);
    }

    public List<CarVolumeGroupInfo> getVolumeGroupInfosForZone(int zoneId) {
        return mCarAudioService.getVolumeGroupInfosForZone(zoneId);
    }

    public List<AudioAttributes> getActiveAudioAttributesForZone(int zoneId) {
        return mCarAudioService.getActiveAudioAttributesForZone(zoneId);
    }

    public int getCallStateForZone(int zoneId) {
        return mCarAudioService.getCallStateForZone(zoneId);
    }

    public void onAudioVolumeGroupChanged(int zoneId, String groupName, int flags) {
        mCarAudioService.onAudioVolumeGroupChanged(zoneId, groupName, flags);
    }

    public void onVolumeGroupEvent(List<CarVolumeGroupEvent> events) {
        mCarAudioService.onVolumeGroupEvent(events);
    }
}
