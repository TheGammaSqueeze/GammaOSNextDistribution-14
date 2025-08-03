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

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.car.media.CarVolumeGroupInfo;
import android.car.oem.OemCarAudioVolumeRequest;
import android.media.AudioAttributes;
import android.media.AudioFocusInfo;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLocalServices;
import com.android.car.audio.CarZonesAudioFocus.CarFocusCallback;
import com.android.car.audio.hal.AudioControlWrapper;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.oem.CarOemProxyService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class CarDucking implements CarFocusCallback {
    private static final String TAG = CarDucking.class.getSimpleName();

    private final SparseArray<CarAudioZone> mCarAudioZones;
    private final AudioControlWrapper mAudioControlWrapper;
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final SparseArray<CarDuckingInfo> mCurrentDuckingInfo = new SparseArray<>();

    CarDucking(SparseArray<CarAudioZone> carAudioZones, AudioControlWrapper audioControlWrapper) {
        mCarAudioZones = Objects.requireNonNull(carAudioZones, "Car audio zones can not be null");
        mAudioControlWrapper = Objects.requireNonNull(audioControlWrapper,
                        "Audio control wrapper can not be null");

        for (int i = 0; i < carAudioZones.size(); i++) {
            int zoneId = carAudioZones.keyAt(i);
            mCurrentDuckingInfo.put(
                    zoneId,
                    new CarDuckingInfo(
                            zoneId, new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
        }
    }

    @VisibleForTesting
    SparseArray<CarDuckingInfo> getCurrentDuckingInfo() {
        synchronized (mLock) {
            return mCurrentDuckingInfo;
        }
    }

    @Override
    public void onFocusChange(int[] audioZoneIds,
            SparseArray<List<AudioFocusInfo>> focusHoldersByZoneId) {
        synchronized (mLock) {
            List<CarDuckingInfo> newDuckingInfos = new ArrayList<>(audioZoneIds.length);
            for (int i = 0; i < audioZoneIds.length; i++) {
                int zoneId = audioZoneIds[i];
                List<AudioFocusInfo> focusHolders = focusHoldersByZoneId.get(zoneId);
                CarDuckingInfo newDuckingInfo = updateDuckingForZoneIdLocked(zoneId, focusHolders);
                newDuckingInfos.add(newDuckingInfo);
            }
            mAudioControlWrapper.onDevicesToDuckChange(newDuckingInfos);
        }
    }

    @GuardedBy("mLock")
    private CarDuckingInfo updateDuckingForZoneIdLocked(int zoneId,
            List<AudioFocusInfo> focusHolders) {
        CarDuckingInfo oldDuckingInfo = mCurrentDuckingInfo.get(zoneId);
        CarDuckingInfo newDuckingInfo = generateNewDuckingInfoLocked(oldDuckingInfo,
                focusHolders);
        mCurrentDuckingInfo.put(zoneId, newDuckingInfo);
        return newDuckingInfo;
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.printf("*%s*\n", TAG);
        writer.increaseIndent();
        synchronized (mLock) {
            for (int i = 0; i < mCurrentDuckingInfo.size(); i++) {
                mCurrentDuckingInfo.valueAt(i).dump(writer);
            }
        }
        writer.decreaseIndent();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {
        long carDuckingProto = proto.start(CarAudioDumpProto.CAR_DUCKING);
        synchronized (mLock) {
            for (int i = 0; i < mCurrentDuckingInfo.size(); i++) {
                mCurrentDuckingInfo.valueAt(i).dumpProto(proto);
            }
        }
        proto.end(carDuckingProto);
    }

    @GuardedBy("mLock")
    private CarDuckingInfo generateNewDuckingInfoLocked(CarDuckingInfo oldDuckingInfo,
            List<AudioFocusInfo> focusHolders) {
        int zoneId = oldDuckingInfo.mZoneId;
        CarAudioZone zone = mCarAudioZones.get(zoneId);

        List<CarVolumeGroupInfo> groupInfos = zone.getCurrentVolumeGroupInfos();

        List<AudioAttributes> attributesHoldingFocus =
                CarDuckingUtils.getAudioAttributesHoldingFocus(focusHolders);

        OemCarAudioVolumeRequest request = new OemCarAudioVolumeRequest.Builder(zoneId)
                .setActivePlaybackAttributes(attributesHoldingFocus)
                .setCarVolumeGroupInfos(groupInfos).build();

        List<AudioAttributes> audioAttributesToDuck = evaluateAttributesToDuck(request);

        return CarDuckingUtils.generateDuckingInfo(oldDuckingInfo, audioAttributesToDuck,
                attributesHoldingFocus, zone);
    }

    private List<AudioAttributes> evaluateAttributesToDuck(OemCarAudioVolumeRequest requestInfo)  {
        return isOemDuckingServiceAvailable() ? evaluateAttributesToDuckExternally(requestInfo) :
                evaluateAttributesToDuckInternally(requestInfo);
    }

    private List<AudioAttributes> evaluateAttributesToDuckExternally(
            OemCarAudioVolumeRequest requestInfo) {
        return CarLocalServices.getService(CarOemProxyService.class).getCarOemAudioDuckingService()
                .evaluateAttributesToDuck(requestInfo);
    }

    private List<AudioAttributes> evaluateAttributesToDuckInternally(
            OemCarAudioVolumeRequest requestInfo) {
        return CarAudioContext.evaluateAudioAttributesToDuck(
                requestInfo.getActivePlaybackAttributes());
    }

    private boolean isOemDuckingServiceAvailable() {
        CarOemProxyService carService = CarLocalServices.getService(CarOemProxyService.class);

        return carService != null
                && carService.isOemServiceEnabled() && carService.isOemServiceReady()
                && carService.getCarOemAudioDuckingService() != null;
    }
}
