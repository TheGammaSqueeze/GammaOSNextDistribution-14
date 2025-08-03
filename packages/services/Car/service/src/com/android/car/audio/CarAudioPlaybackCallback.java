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

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;

import com.android.car.audio.CarAudioService.SystemClockWrapper;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.util.Preconditions;

import java.util.List;
import java.util.Objects;

final class CarAudioPlaybackCallback extends AudioManager.AudioPlaybackCallback {
    private final SparseArray<ZoneAudioPlaybackCallback> mCarAudioZonesToZonePlaybackCallback;

    CarAudioPlaybackCallback(@NonNull SparseArray<CarAudioZone> carAudioZones,
            @Nullable CarAudioPlaybackMonitor carAudioPlaybackMonitor,
            SystemClockWrapper clock, int volumeKeyEventTimeoutMs) {
        Objects.requireNonNull(carAudioZones, "Car audio zone cannot be null");
        Preconditions.checkArgument(carAudioZones.size() > 0,
                "Car audio zones must not be empty");
        mCarAudioZonesToZonePlaybackCallback = createCallbackMapping(carAudioZones,
                carAudioPlaybackMonitor, clock, volumeKeyEventTimeoutMs);
    }

    private static SparseArray createCallbackMapping(SparseArray<CarAudioZone> carAudioZones,
            @Nullable CarAudioPlaybackMonitor carAudioPlaybackMonitor,
            SystemClockWrapper clock, int volumeKeyEventTimeoutMs) {
        SparseArray<ZoneAudioPlaybackCallback> carAudioZonesToZonePlaybackCallback =
                new SparseArray<>();
        for (int i = 0; i < carAudioZones.size(); i++) {
            CarAudioZone zone = carAudioZones.get(i);
            carAudioZonesToZonePlaybackCallback.put(zone.getId(),
                    new ZoneAudioPlaybackCallback(zone, carAudioPlaybackMonitor, clock,
                            volumeKeyEventTimeoutMs));
        }
        return carAudioZonesToZonePlaybackCallback;
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        writer.println("CarAudioPlaybackCallback");
        writer.increaseIndent();

        writer.println("Audio playback callback for zones");
        writer.increaseIndent();
        dumpZoneCallbacks(writer);
        writer.decreaseIndent();

        writer.decreaseIndent();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dumpZoneCallbacks(IndentingPrintWriter writer) {
        for (int i = 0; i < mCarAudioZonesToZonePlaybackCallback.size(); i++) {
            mCarAudioZonesToZonePlaybackCallback.valueAt(i).dump(writer);
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dumpProto(ProtoOutputStream proto) {
        long callbackToken = proto.start(CarAudioDumpProto.CAR_AUDIO_PLAYBACK_CALLBACK);
        for (int i = 0; i < mCarAudioZonesToZonePlaybackCallback.size(); i++) {
            mCarAudioZonesToZonePlaybackCallback.valueAt(i).dumpProto(proto);
        }
        proto.end(callbackToken);
    }

    @Override
    public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configurations) {
        for (int i = 0; i < mCarAudioZonesToZonePlaybackCallback.size(); i++) {
            mCarAudioZonesToZonePlaybackCallback.valueAt(i).onPlaybackConfigChanged(configurations);
        }
    }

    public List<AudioAttributes> getAllActiveAudioAttributesForZone(int audioZone) {
        return mCarAudioZonesToZonePlaybackCallback.get(audioZone).getAllActiveAudioAttributes();
    }

    public void resetStillActiveContexts() {
        for (int i = 0; i < mCarAudioZonesToZonePlaybackCallback.size(); i++) {
            mCarAudioZonesToZonePlaybackCallback.valueAt(i).resetStillActiveContexts();
        }
    }
}
