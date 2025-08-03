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

import static com.android.car.audio.hal.AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_GROUP_MUTING;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.car.builtin.util.Slogf;
import android.hardware.automotive.audiocontrol.MutingInfo;
import android.util.Log;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLog;
import com.android.car.audio.CarAudioDumpProto.CarVolumeGroupMutingProto;
import com.android.car.audio.CarAudioDumpProto.CarVolumeGroupMutingProto.CarMutingInfo;
import com.android.car.audio.hal.AudioControlWrapper;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class CarVolumeGroupMuting {

    private static final String TAG = CarLog.tagFor(CarVolumeGroupMuting.class);

    private final SparseArray<CarAudioZone> mCarAudioZones;
    private final AudioControlWrapper mAudioControlWrapper;
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private List<MutingInfo> mLastMutingInformation;
    @GuardedBy("mLock")
    private boolean mIsMutingRestricted;

    CarVolumeGroupMuting(@NonNull SparseArray<CarAudioZone> carAudioZones,
            @NonNull AudioControlWrapper audioControlWrapper) {
        mCarAudioZones = Objects.requireNonNull(carAudioZones, "Car Audio Zones can not be null");
        Preconditions.checkArgument(carAudioZones.size() != 0,
                "At least one car audio zone must be present.");
        mAudioControlWrapper = Objects.requireNonNull(audioControlWrapper,
                "Audio Control Wrapper can not be null");
        requireGroupMutingSupported(audioControlWrapper);
        mLastMutingInformation = new ArrayList<>();
    }

    private static void requireGroupMutingSupported(AudioControlWrapper audioControlWrapper) {
        if (audioControlWrapper
                .supportsFeature(AUDIOCONTROL_FEATURE_AUDIO_GROUP_MUTING)) {
            return;
        }
        throw new IllegalStateException("audioUseCarVolumeGroupMuting is enabled but "
                + "IAudioControl HAL does not support volume group muting");
    }

    /**
     * Signal that mute has changed.
     */
    public void carMuteChanged() {
        if (Slogf.isLoggable(TAG, Log.DEBUG)) {
            Slogf.d(TAG, "carMuteChanged");
        }

        List<MutingInfo> mutingInfo = generateMutingInfo();
        setLastMutingInfo(mutingInfo);
        mAudioControlWrapper.onDevicesToMuteChange(mutingInfo);
    }

    public void setRestrictMuting(boolean isMutingRestricted) {
        synchronized (mLock) {
            mIsMutingRestricted = isMutingRestricted;
        }

        carMuteChanged();
    }

    private boolean isMutingRestricted() {
        synchronized (mLock) {
            return mIsMutingRestricted;
        }
    }

    private void setLastMutingInfo(List<MutingInfo> mutingInfo) {
        synchronized (mLock) {
            mLastMutingInformation = mutingInfo;
        }
    }

    @VisibleForTesting
    List<MutingInfo> getLastMutingInformation() {
        synchronized (mLock) {
            return mLastMutingInformation;
        }
    }

    private List<MutingInfo> generateMutingInfo() {
        List<MutingInfo> mutingInformation = new ArrayList<>(mCarAudioZones.size());

        boolean isMutingRestricted = isMutingRestricted();
        for (int index = 0; index < mCarAudioZones.size(); index++) {
            mutingInformation.add(generateMutingInfoFromZone(mCarAudioZones.valueAt(index),
                    isMutingRestricted));
        }

        return mutingInformation;
    }

    /**
     * Dumps internal state
     */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println(TAG);
        writer.increaseIndent();
        synchronized (mLock) {
            writer.printf("Is muting restricted? %b\n", mIsMutingRestricted);
            for (int index = 0; index < mLastMutingInformation.size(); index++) {
                dumpCarMutingInfo(writer, mLastMutingInformation.get(index));
            }
        }
        writer.decreaseIndent();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpCarMutingInfo(IndentingPrintWriter writer, MutingInfo info) {
        writer.printf("Zone ID: %d\n", info.zoneId);

        writer.println("Muted Devices:");
        writer.increaseIndent();
        dumpDeviceAddresses(writer, info.deviceAddressesToMute);
        writer.decreaseIndent();

        writer.println("Un-muted Devices:");
        writer.increaseIndent();
        dumpDeviceAddresses(writer, info.deviceAddressesToUnmute);
        writer.decreaseIndent();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private static void dumpDeviceAddresses(IndentingPrintWriter writer, String[] devices) {
        for (int index = 0; index < devices.length; index++) {
            writer.printf("%d %s\n", index, devices[index]);
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dumpProto(ProtoOutputStream proto) {
        long carVolumeGroupMutingToken = proto.start(CarAudioDumpProto.CAR_VOLUME_GROUP_MUTING);
        synchronized (mLock) {
            proto.write(CarVolumeGroupMutingProto.IS_MUTING_RESTRICTED, mIsMutingRestricted);
            for (int index = 0; index < mLastMutingInformation.size(); index++) {
                dumpProtoCarMutingInfo(mLastMutingInformation.get(index), proto);
            }
        }
        proto.end(carVolumeGroupMutingToken);
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpProtoCarMutingInfo(MutingInfo info, ProtoOutputStream proto) {
        long lastMutingInfoToken = proto.start(CarVolumeGroupMutingProto.LAST_MUTING_INFORMATION);
        proto.write(CarMutingInfo.ZONE_ID, info.zoneId);
        dumpProtoDeviceAddresses(info.deviceAddressesToMute, CarMutingInfo.DEVICE_ADDRESSES_TO_MUTE,
                proto);
        dumpProtoDeviceAddresses(info.deviceAddressesToUnmute,
                CarMutingInfo.DEVICE_ADDRESSES_TO_UNMUTE, proto);
        proto.end(lastMutingInfoToken);
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private static void dumpProtoDeviceAddresses(String[] devices, long fieldId,
            ProtoOutputStream proto) {
        for (int index = 0; index < devices.length; index++) {
            proto.write(fieldId, devices[index]);
        }
    }

    @VisibleForTesting
    static MutingInfo generateMutingInfoFromZone(CarAudioZone audioZone,
            boolean isMutingRestricted) {
        MutingInfo mutingInfo = new MutingInfo();
        mutingInfo.zoneId = audioZone.getId();

        List<String> mutedDevices = new ArrayList<>();
        List<String> unMutedDevices = new ArrayList<>();
        CarVolumeGroup[] groups = audioZone.getCurrentVolumeGroups();

        for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
            CarVolumeGroup group = groups[groupIndex];

            if (group.isMuted() || (isMutingRestricted && !group.hasCriticalAudioContexts())) {
                mutedDevices.addAll(group.getAddresses());
            } else {
                unMutedDevices.addAll(group.getAddresses());
            }
        }

        mutingInfo.deviceAddressesToMute = mutedDevices.toArray(new String[mutedDevices.size()]);
        mutingInfo.deviceAddressesToUnmute =
                unMutedDevices.toArray(new String[unMutedDevices.size()]);

        return mutingInfo;
    }
}
