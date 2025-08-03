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

package com.android.car.audio;

import static com.android.car.audio.CarAudioService.SystemClockWrapper;
import static com.android.car.audio.CarAudioUtils.hasExpired;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.media.AudioAttributes;
import android.media.AudioPlaybackConfiguration;
import android.util.ArrayMap;
import android.util.proto.ProtoOutputStream;

import com.android.car.audio.CarAudioDumpProto.CarAudioPlaybackCallbackProto;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ZoneAudioPlaybackCallback {
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final ArrayMap<AudioAttributes, Long> mAudioAttributesStartTime = new ArrayMap<>();
    @GuardedBy("mLock")
    private final ArrayMap<String, AudioPlaybackConfiguration> mLastActiveConfigs =
            new ArrayMap<>();
    private final CarAudioZone mCarAudioZone;
    private final @Nullable  CarAudioPlaybackMonitor mCarAudioPlaybackMonitor;
    private final SystemClockWrapper mClock;
    private final int mVolumeKeyEventTimeoutMs;

    ZoneAudioPlaybackCallback(@NonNull CarAudioZone carAudioZone,
                              @Nullable CarAudioPlaybackMonitor carAudioPlaybackMonitor,
                              @NonNull SystemClockWrapper clock,
                              int volumeKeyEventTimeoutMs) {
        mCarAudioZone = Objects.requireNonNull(carAudioZone, "Audio zone cannot be null");
        mCarAudioPlaybackMonitor = carAudioPlaybackMonitor;
        mClock = Objects.requireNonNull(clock, "Clock cannot be null");
        mVolumeKeyEventTimeoutMs = Preconditions.checkArgumentNonnegative(volumeKeyEventTimeoutMs,
                "Volume key event timeout must be positive");
    }

    public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configurations) {
        ArrayMap<String, AudioPlaybackConfiguration> newActiveConfigs =
                filterNewActiveConfiguration(configurations);
        List<AudioAttributes> newlyActiveAudioAttributes = new ArrayList<>();

        synchronized (mLock) {
            List<AudioPlaybackConfiguration> newlyInactiveConfigurations =
                    getNewlyInactiveConfigurationsLocked(newActiveConfigs);
            if (mCarAudioPlaybackMonitor != null) {
                newlyActiveAudioAttributes = getNewlyActiveAudioAttributes(newActiveConfigs);
            }

            mLastActiveConfigs.clear();
            mLastActiveConfigs.putAll(newActiveConfigs);

            startTimersForContextThatBecameInactiveLocked(newlyInactiveConfigurations);
        }

        if (mCarAudioPlaybackMonitor != null && !newlyActiveAudioAttributes.isEmpty()) {
            mCarAudioPlaybackMonitor.onActiveAudioPlaybackAttributesAdded(
                    newlyActiveAudioAttributes, mCarAudioZone.getId());
        }
    }

    /**
     * Returns all active contexts for the primary zone
     * @return all active audio contexts, including those that recently became inactive but are
     * considered active due to the audio playback timeout.
     */
    public List<AudioAttributes> getAllActiveAudioAttributes() {
        synchronized (mLock) {
            List<AudioAttributes> activeContexts = getCurrentlyActiveAttributesLocked();
            activeContexts
                    .addAll(getStillActiveContextAndRemoveExpiredContextsLocked());
            return activeContexts;
        }
    }

    @GuardedBy("mLock")
    private void startTimersForContextThatBecameInactiveLocked(
            List<AudioPlaybackConfiguration> inactiveConfigs) {
        List<AudioAttributes> activeAttributes = mCarAudioZone
                .findActiveAudioAttributesFromPlaybackConfigurations(inactiveConfigs);

        for (int index = 0; index < activeAttributes.size(); index++) {
            mAudioAttributesStartTime.put(activeAttributes.get(index), mClock.uptimeMillis());
        }
    }

    @GuardedBy("mLock")
    private List<AudioPlaybackConfiguration> getNewlyInactiveConfigurationsLocked(
            Map<String, AudioPlaybackConfiguration> newActiveConfigurations) {
        List<AudioPlaybackConfiguration> newlyInactiveConfigurations = new ArrayList<>();
        for (int index = 0; index < mLastActiveConfigs.size(); index++) {
            if (newActiveConfigurations
                    .containsKey(mLastActiveConfigs.keyAt(index))) {
                continue;
            }
            newlyInactiveConfigurations.add(mLastActiveConfigs.valueAt(index));
        }
        return newlyInactiveConfigurations;
    }

    @GuardedBy("mLock")
    private List<AudioAttributes> getNewlyActiveAudioAttributes(
            ArrayMap<String, AudioPlaybackConfiguration> newActiveConfigurations) {
        List<AudioPlaybackConfiguration> audioPlaybackConfigurationsWithNewAttributes =
                new ArrayList<>();
        for (int index = 0; index < newActiveConfigurations.size(); index++) {
            if (mLastActiveConfigs.containsKey(newActiveConfigurations.keyAt(index))) {
                continue;
            }
            audioPlaybackConfigurationsWithNewAttributes
                    .add(newActiveConfigurations.valueAt(index));
        }
        return getAudioAttributesFromPlaybacks(audioPlaybackConfigurationsWithNewAttributes);
    }

    private ArrayMap<String, AudioPlaybackConfiguration> filterNewActiveConfiguration(
            List<AudioPlaybackConfiguration> configurations) {
        ArrayMap<String, AudioPlaybackConfiguration> newActiveConfigs = new ArrayMap<>();
        for (int index = 0; index < configurations.size(); index++) {
            AudioPlaybackConfiguration configuration = configurations.get(index);
            if (!configuration.isActive()) {
                continue;
            }
            if (mCarAudioZone
                    .isAudioDeviceInfoValidForZone(configuration.getAudioDeviceInfo())) {
                newActiveConfigs.put(
                        configuration.getAudioDeviceInfo().getAddress(), configuration);
            }
        }
        return newActiveConfigs;
    }

    @GuardedBy("mLock")
    private List<AudioAttributes> getCurrentlyActiveAttributesLocked() {
        return getAudioAttributesFromPlaybacks(mLastActiveConfigs.values());
    }

    @GuardedBy("mLock")
    private List<AudioAttributes> getStillActiveContextAndRemoveExpiredContextsLocked() {
        List<AudioAttributes> attributesToRemove = new ArrayList<>();
        List<AudioAttributes> activeAttributes = new ArrayList<>();
        for (int index = 0; index < mAudioAttributesStartTime.size(); index++) {
            long startTime = mAudioAttributesStartTime.valueAt(index);
            if (hasExpired(startTime, mClock.uptimeMillis(), mVolumeKeyEventTimeoutMs)) {
                attributesToRemove.add(mAudioAttributesStartTime.keyAt(index));
                continue;
            }
            activeAttributes.add(mAudioAttributesStartTime.keyAt(index));
        }

        for (int indexToRemove = 0; indexToRemove < attributesToRemove.size(); indexToRemove++) {
            mAudioAttributesStartTime.remove(attributesToRemove.get(indexToRemove));
        }
        return activeAttributes;
    }

    void resetStillActiveContexts() {
        synchronized (mLock) {
            mAudioAttributesStartTime.clear();
        }
    }

    private List<AudioAttributes> getAudioAttributesFromPlaybacks(
            Collection<AudioPlaybackConfiguration> playbacks) {
        return mCarAudioZone.findActiveAudioAttributesFromPlaybackConfigurations(
                new ArrayList<>(playbacks));
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.printf("Audio zone: %d\n", mCarAudioZone.getId());

        dumpLastActiveConfigsAndAudioAttributesStartTime(writer);
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpLastActiveConfigsAndAudioAttributesStartTime(IndentingPrintWriter writer) {
        synchronized (mLock) {
            writer.println("Last active configs:");
            writer.increaseIndent();
            for (int i = 0; i < mLastActiveConfigs.size(); i++) {
                writer.printf("Audio device address %s to config %s\n",
                        mLastActiveConfigs.keyAt(i), mLastActiveConfigs.valueAt(i));
            }
            writer.decreaseIndent();
            writer.println("Audio attributes start times:");
            writer.increaseIndent();
            for (int i = 0; i < mAudioAttributesStartTime.size(); i++) {
                writer.printf("Audio Attributes %s mapped to start time of %d\n",
                        mAudioAttributesStartTime.keyAt(i), mAudioAttributesStartTime.valueAt(i));
            }
            writer.decreaseIndent();
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dumpProto(ProtoOutputStream proto) {
        long token = proto.start(CarAudioPlaybackCallbackProto.ZONE_AUDIO_PLAYBACK_CALLBACKS);
        proto.write(CarAudioPlaybackCallbackProto.ZoneAudioPlaybackCallbackProto.ZONE_ID,
                mCarAudioZone.getId());
        dumpProtoLastActiveConfigsAndAudioAttributesStartTime(proto);
        proto.end(token);
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProtoLastActiveConfigsAndAudioAttributesStartTime(ProtoOutputStream proto) {
        synchronized (mLock) {
            for (int i = 0; i < mLastActiveConfigs.size(); i++) {
                long lastActiveConfigToken = proto.start(CarAudioPlaybackCallbackProto
                        .ZoneAudioPlaybackCallbackProto.LAST_ACTIVE_CONFIGS);
                proto.write(CarAudioPlaybackCallbackProto.ZoneAudioPlaybackCallbackProto
                        .AudioDeviceAddressToConfig.ADDRESS, mLastActiveConfigs.keyAt(i));
                proto.write(CarAudioPlaybackCallbackProto.ZoneAudioPlaybackCallbackProto
                        .AudioDeviceAddressToConfig.CONFIG, mLastActiveConfigs.valueAt(i)
                        .toString());
                proto.end(lastActiveConfigToken);
            }

            for (int i = 0; i < mAudioAttributesStartTime.size(); i++) {
                long audioAttributeToStartTimeToken = proto.start(CarAudioPlaybackCallbackProto
                        .ZoneAudioPlaybackCallbackProto.AUDIO_ATTRIBUTES_TO_START_TIMES);
                CarAudioContextInfo.dumpCarAudioAttributesProto(mAudioAttributesStartTime.keyAt(i),
                        CarAudioPlaybackCallbackProto.ZoneAudioPlaybackCallbackProto
                                .AudioAttributesToStartTime.AUDIO_ATTRIBUTES, proto);
                proto.write(CarAudioPlaybackCallbackProto.ZoneAudioPlaybackCallbackProto
                        .AudioAttributesToStartTime.START_TIME,
                        mAudioAttributesStartTime.valueAt(i));
                proto.end(audioAttributeToStartTimeToken);
            }
        }
    }
}
