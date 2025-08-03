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

package com.android.car.oem.volume;

import static android.media.AudioAttributes.USAGE_MEDIA;

import static com.android.car.oem.utils.AudioUtils.getAudioAttributeFromUsage;

import android.car.Car;
import android.car.media.CarAudioManager;
import android.car.media.CarVolumeGroupInfo;
import android.car.oem.OemCarAudioVolumeRequest;
import android.car.oem.OemCarVolumeChangeInfo;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.car.oem.utils.AudioAttributesWrapper;

import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;

/**
 * Class for evaluating the volume interactions
 */
public final class VolumeInteractions {

    public static final AudioAttributesWrapper DEFAULT_AUDIO_ATTRIBUTE = new AudioAttributesWrapper(
            getAudioAttributeFromUsage(USAGE_MEDIA));
    private static final String TAG = VolumeInteractions.class.getSimpleName();

    public static final List<AudioAttributes> VOLUME_PRIORITIES = List.of(
            getAudioAttributeFromUsage(AudioAttributes.USAGE_EMERGENCY),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_SAFETY),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANT),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_CALL_ASSISTANT),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_VEHICLE_STATUS),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_ANNOUNCEMENT),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_ALARM),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_UNKNOWN),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_GAME),
            getAudioAttributeFromUsage(USAGE_MEDIA));

    private final ArrayMap<AudioAttributesWrapper, Integer> mAudioAttributeToPriority;
    private final SparseArray<ArrayMap<AudioAttributesWrapper, Integer>>
            mAudioZoneToVolumeGroupAttributes = new SparseArray<>();
    private final SparseIntArray mZoneToDefaultVolumeGroup = new SparseIntArray();
    private final Context mContext;

    public VolumeInteractions(Context context, List<AudioAttributes> audioAttributes) {
        mContext = Objects.requireNonNull(context, "Context must not be null");
        mAudioAttributeToPriority = new ArrayMap<>(audioAttributes.size());
        for (int index = 0; index < audioAttributes.size(); index++) {
            mAudioAttributeToPriority.put(
                    new AudioAttributesWrapper(audioAttributes.get(index)),
                    audioAttributes.size() - index - 1);
        }
    }

    private static ArrayMap<AudioAttributesWrapper, Integer> getAudioAttributeToGroupIdMapping(
            CarAudioManager carAudioManager, int zone) {
        List<CarVolumeGroupInfo> volumeInfos =
                carAudioManager.getVolumeGroupInfosForZone(zone);
        ArrayMap<AudioAttributesWrapper, Integer> groupAudioAttributesToGroupId =
                new ArrayMap<>();
        for (int group = 0; group < volumeInfos.size(); group++) {
            CarVolumeGroupInfo info = volumeInfos.get(group);
            List<AudioAttributes> audioAttributes =
                    carAudioManager.getAudioAttributesForVolumeGroup(info);
            for (int index = 0; index < audioAttributes.size(); index++) {
                AudioAttributes audioAttribute = audioAttributes.get(index);
                groupAudioAttributesToGroupId.put(new AudioAttributesWrapper(audioAttribute),
                        info.getId());
            }
        }

        return groupAudioAttributesToGroupId;
    }

    public OemCarVolumeChangeInfo getVolumeGroupToChange(OemCarAudioVolumeRequest requestInfo,
            int volumeAdjustment) {

        int volumeGroupId = getSelectedVolumeGroupId(requestInfo.getActivePlaybackAttributes(),
                requestInfo.getDuckedAudioAttributes(), requestInfo.getAudioZoneId());

        CarVolumeGroupInfo currentVolumeInfo =
                getCurrentVolumeGroup(requestInfo.getCarVolumeGroupInfos(), volumeGroupId);

        if (currentVolumeInfo == null) {
            return OemCarVolumeChangeInfo.EMPTY_OEM_VOLUME_CHANGE;
        }

        CarVolumeGroupInfo.Builder builder = new CarVolumeGroupInfo.Builder(currentVolumeInfo);

        boolean volumeChanged = true;
        switch (volumeAdjustment) {
            case AudioManager.ADJUST_LOWER:
                int newVolumeGainIndex = Math.max(currentVolumeInfo.getVolumeGainIndex() - 1,
                        currentVolumeInfo.getMinVolumeGainIndex());
                if (currentVolumeInfo.isMuted())  {
                    newVolumeGainIndex = currentVolumeInfo.getVolumeGainIndex();
                    builder.setMuted(false);
                }
                builder.setVolumeGainIndex(newVolumeGainIndex);
                break;
            case AudioManager.ADJUST_RAISE:
                int changedVolumeGainIndex = Math.min(currentVolumeInfo.getVolumeGainIndex() + 1,
                        currentVolumeInfo.getMaxVolumeGainIndex());
                if (currentVolumeInfo.isMuted())  {
                    changedVolumeGainIndex = currentVolumeInfo.getVolumeGainIndex();
                    builder.setMuted(false);
                }
                builder.setVolumeGainIndex(changedVolumeGainIndex);
                break;
            case AudioManager.ADJUST_MUTE: // Fall through
            case AudioManager.ADJUST_UNMUTE:
                builder.setMuted(volumeAdjustment == AudioManager.ADJUST_MUTE);
                break;
            case AudioManager.ADJUST_TOGGLE_MUTE:
                builder.setMuted(!currentVolumeInfo.isMuted());
                break;
            case AudioManager.ADJUST_SAME: // Fall through
            default:
                volumeChanged = false;
                break;
        }

        return new OemCarVolumeChangeInfo.Builder(volumeChanged)
                .setChangedVolumeGroup(builder.build()).build();
    }

    private static CarVolumeGroupInfo getCurrentVolumeGroup(
            List<CarVolumeGroupInfo> volumeGroupInfos, int groupId) {
        for (int index = 0; index < volumeGroupInfos.size(); index++) {
            if (volumeGroupInfos.get(index).getId() == groupId) {
                return volumeGroupInfos.get(index);
            }
        }
        return null;
    }

    private int getSelectedVolumeGroupId(
            List<AudioAttributes> activeAudioAttributes,
            List<AudioAttributes> duckedAttributes,
            int zoneId) {
        // Nothing to select if there is only one or fewer items
        int defaultVolumeGroup = mZoneToDefaultVolumeGroup.get(zoneId);
        if (activeAudioAttributes.isEmpty()) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, "No active audio attributes returning default volume group");
            }
            return defaultVolumeGroup;
        }
        Slog.d(TAG, "test: ");

        if (activeAudioAttributes.size() < 2) {
            AudioAttributesWrapper audioAttributesWrapper =
                    new AudioAttributesWrapper(activeAudioAttributes.get(0));
            Slog.d(TAG, "Only one audio attribute available: " + audioAttributesWrapper);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, "Only one audio attribute available: " + audioAttributesWrapper);
            }
            return mAudioZoneToVolumeGroupAttributes.get(zoneId)
                    .getOrDefault(audioAttributesWrapper, defaultVolumeGroup);
        }
        ArraySet<AudioAttributesWrapper> duckedWrappers =
                convertAudioAudioAttributes(duckedAttributes);
        int highestPriority = -1;
        AudioAttributesWrapper highestPriorityAttribute = null;
        for (int index = 0; index < activeAudioAttributes.size(); index++) {
            AudioAttributesWrapper wrapper =
                    new AudioAttributesWrapper(activeAudioAttributes.get(index));
            // Do not consider ducked attribute
            if (duckedWrappers.contains(wrapper)) {
                continue;
            }
            int priority = mAudioAttributeToPriority.getOrDefault(wrapper, /* defaultValue= */ -1);
            if (priority > highestPriority) {
                highestPriority = priority;
                highestPriorityAttribute = wrapper;
            }
        }

        if (highestPriorityAttribute == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, "highestPriorityAttribute is null");
            }
            return defaultVolumeGroup;
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Selected audio attribute: " + highestPriorityAttribute);
        }
        return mAudioZoneToVolumeGroupAttributes.get(zoneId)
                .getOrDefault(highestPriorityAttribute, defaultVolumeGroup);
    }

    private ArraySet<AudioAttributesWrapper> convertAudioAudioAttributes(
            List<AudioAttributes> duckedAttributes) {
        ArraySet<AudioAttributesWrapper> wrappers = new ArraySet<>(duckedAttributes.size());
        for (int index = 0; index < wrappers.size(); index++) {
            wrappers.add(new AudioAttributesWrapper(duckedAttributes.get(index)));
        }

        return wrappers;
    }

    public void dump(PrintWriter writer, String indent) {
        writer.printf("%sVolume priorities: \n", indent);
        for (int index = mAudioAttributeToPriority.size() - 1; index >= 0; index--) {
            writer.printf("%s%sPriority[%d]: %s \n", indent, indent, index,
                    mAudioAttributeToPriority.keyAt(
                            mAudioAttributeToPriority.indexOfValue(index)));
        }
    }

    /**
     * Call to initialize volume interactions
     */
    public void init() {
        Car car = Car.createCar(mContext);
        CarAudioManager carAudioManager = car.getCarManager(CarAudioManager.class);

        List<Integer> zones = carAudioManager.getAudioZoneIds();

        for (int zone = 0; zone < zones.size(); zone++) {
            int zoneId = zones.get(zone);
            ArrayMap<AudioAttributesWrapper, Integer> mapping =
                    getAudioAttributeToGroupIdMapping(carAudioManager, zoneId);
            mAudioZoneToVolumeGroupAttributes.put(zoneId, mapping);
            mZoneToDefaultVolumeGroup.put(zoneId, mapping.get(DEFAULT_AUDIO_ATTRIBUTE));
        }
    }
}
