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

import static android.car.builtin.media.AudioManagerHelper.usageToXsdString;
import static android.media.audio.common.AudioContentType.UNKNOWN;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.hardware.audio.common.PlaybackTrackMetadata;
import android.util.proto.ProtoOutputStream;

import com.android.car.audio.CarAudioDumpProto.CarDuckingProto;
import com.android.car.audio.CarAudioDumpProto.CarDuckingProto.CarDuckingInfoProto;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Ducking information for a given car audio zone based on its focus state.
 */
public final class CarDuckingInfo {
    public final int mZoneId;
    public final List<String> mAddressesToDuck;
    public final List<String> mAddressesToUnduck;
    public final List<PlaybackTrackMetadata> mPlaybackMetaDataHoldingFocus;

    public CarDuckingInfo(
            int zoneId,
            @NonNull List<String> addressesToDuck,
            @NonNull List<String> addressesToUnduck,
            @NonNull List<PlaybackTrackMetadata> playbackMetaDataHoldingFocus) {
        mZoneId = zoneId;
        mAddressesToDuck = Objects.requireNonNull(addressesToDuck);
        mAddressesToUnduck = Objects.requireNonNull(addressesToUnduck);
        mPlaybackMetaDataHoldingFocus = Objects.requireNonNull(playbackMetaDataHoldingFocus);
    }

    public int getZoneId() {
        return mZoneId;
    }

    public @NonNull List<String> getAddressesToDuck() {
        return mAddressesToDuck;
    }

    public @NonNull List<String> getAddressesToUnduck() {
        return mAddressesToUnduck;
    }

    public @NonNull List<PlaybackTrackMetadata> getPlaybackMetaDataHoldingFocus() {
        return mPlaybackMetaDataHoldingFocus;
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        writer.printf("Ducking Info for zone %d \n", mZoneId);
        writer.increaseIndent();
        writer.printf("Addresses to duck: %s\n",
                String.join(", ", mAddressesToDuck));
        writer.printf("Addresses to unduck: %s\n",
                String.join(", ", mAddressesToUnduck));
        writer.println("Audio Attributes holding focus:");
        writer.increaseIndent();
        for (int index = 0; index < mPlaybackMetaDataHoldingFocus.size(); index++) {
            PlaybackTrackMetadata playbackTrackMetaData = mPlaybackMetaDataHoldingFocus.get(index);
            writer.printf(
                    "usage=%s, content type=%s, tags=%s\n",
                    usageToXsdString(playbackTrackMetaData.usage),
                    (playbackTrackMetaData.contentType != UNKNOWN
                            ? playbackTrackMetaData.contentType
                            : ""),
                    (playbackTrackMetaData.tags.length != 0
                            ? Arrays.toString(playbackTrackMetaData.tags)
                            : ""));
        }
        writer.decreaseIndent();
        writer.println();
        writer.decreaseIndent();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dumpProto(ProtoOutputStream proto) {
        long carDuckingInfoToken = proto.start(CarDuckingProto.CURRENT_DUCKING_INFOS);
        proto.write(CarDuckingInfoProto.ZONE_ID, mZoneId);
        for (int index = 0; index < mAddressesToDuck.size(); index++) {
            proto.write(CarDuckingInfoProto.ADDRESSES_TO_DUCK, mAddressesToDuck.get(index));
        }
        for (int index = 0; index < mAddressesToUnduck.size(); index++) {
            proto.write(CarDuckingInfoProto.ADDRESSES_TO_UNDUCK, mAddressesToUnduck.get(index));
        }
        for (int index = 0; index < mPlaybackMetaDataHoldingFocus.size(); index++) {
            PlaybackTrackMetadata playbackTrackMetaData = mPlaybackMetaDataHoldingFocus.get(index);
            long playbackMetaDataToken = proto.start(CarDuckingInfoProto
                    .PLAYBACK_META_DATA_HOLDING_FOCUS);
            proto.write(CarDuckingInfoProto.PlaybackTrackMetaData.USAGE,
                    usageToXsdString(playbackTrackMetaData.usage));
            if (playbackTrackMetaData.contentType != UNKNOWN) {
                proto.write(CarDuckingInfoProto.PlaybackTrackMetaData.CONTENT_TYPE,
                        playbackTrackMetaData.contentType);
            }
            for (int tagIndex = 0; tagIndex < playbackTrackMetaData.tags.length; tagIndex++) {
                proto.write(CarDuckingInfoProto.PlaybackTrackMetaData.TAGS,
                        playbackTrackMetaData.tags[tagIndex]);
            }
            proto.end(playbackMetaDataToken);
        }
        proto.end(carDuckingInfoToken);
    }
}
