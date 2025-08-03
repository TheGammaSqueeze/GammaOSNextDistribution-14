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

import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_NOTIFICATION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.audio.policy.configuration.V7_0.AudioUsage;
import android.hardware.audio.common.PlaybackTrackMetadata;
import android.hardware.automotive.audiocontrol.DuckingInfo;
import android.media.AudioAttributes;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CarDuckingInfoTest {
    private static final int ZONE_ID = 0;
    private static final List<String> ADDRESSES_TO_DUCK = List.of("address1", "address2");
    private static final List<String> ADDRESSES_TO_UNDUCK = List.of("address3", "address4");
    private static final List<AudioAttributes> USAGES_HOLDING_FOCUS =
            List.of(CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA),
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION));
    private static final List<PlaybackTrackMetadata> PLAYBACKTRACK_METADATA_HOLDING_FOCUS =
            CarHalAudioUtils.audioAttributesToMetadatas(USAGES_HOLDING_FOCUS);

    @Test
    public void constructor_nullAddressesToDuck_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CarDuckingInfo(
                                ZONE_ID,
                                null,
                                ADDRESSES_TO_UNDUCK,
                                PLAYBACKTRACK_METADATA_HOLDING_FOCUS));
    }

    @Test
    public void constructor_nullAddressesToUnduck_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CarDuckingInfo(
                                ZONE_ID,
                                ADDRESSES_TO_DUCK,
                                null,
                                PLAYBACKTRACK_METADATA_HOLDING_FOCUS));
    }

    @Test
    public void constructor_nullPlaybackMetadataHoldingFocus_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new CarDuckingInfo(ZONE_ID, ADDRESSES_TO_DUCK, ADDRESSES_TO_UNDUCK, null));
    }

    @Test
    public void constructor_validInputs_succeeds() {
        CarDuckingInfo duckingInfo = getCarDuckingInfo();

        assertThat(duckingInfo.mZoneId).isEqualTo(ZONE_ID);
        assertThat(duckingInfo.mAddressesToDuck).containsExactlyElementsIn(ADDRESSES_TO_DUCK);
        assertThat(duckingInfo.mAddressesToUnduck).containsExactlyElementsIn(ADDRESSES_TO_UNDUCK);
        assertThat(duckingInfo.mPlaybackMetaDataHoldingFocus)
                .containsExactlyElementsIn(PLAYBACKTRACK_METADATA_HOLDING_FOCUS);

        assertThat(duckingInfo.getZoneId()).isEqualTo(ZONE_ID);
        assertThat(duckingInfo.getAddressesToDuck()).containsExactlyElementsIn(ADDRESSES_TO_DUCK);
        assertThat(duckingInfo.getAddressesToUnduck())
                .containsExactlyElementsIn(ADDRESSES_TO_UNDUCK);
        assertThat(duckingInfo.getPlaybackMetaDataHoldingFocus())
                .containsExactlyElementsIn(PLAYBACKTRACK_METADATA_HOLDING_FOCUS);
    }

    @Test
    public void generateDuckingInfo_includesSameAddressesToDuck() {
        CarDuckingInfo carDuckingInfo = getCarDuckingInfo();

        DuckingInfo duckingInfo = CarHalAudioUtils.generateDuckingInfo(carDuckingInfo);

        assertThat(duckingInfo.deviceAddressesToDuck).asList()
                .containsExactlyElementsIn(carDuckingInfo.mAddressesToDuck);
    }

    @Test
    public void generateDuckingInfo_includesSameAddressesToUnduck() {
        CarDuckingInfo carDuckingInfo = getCarDuckingInfo();

        DuckingInfo duckingInfo = CarHalAudioUtils.generateDuckingInfo(carDuckingInfo);

        assertThat(duckingInfo.deviceAddressesToUnduck).asList()
                .containsExactlyElementsIn(carDuckingInfo.mAddressesToUnduck);
    }

    @Test
    public void generateDuckingInfo_includesSameUsagesHoldingFocus() {
        CarDuckingInfo carDuckingInfo = getCarDuckingInfo();

        DuckingInfo duckingInfo = CarHalAudioUtils.generateDuckingInfo(carDuckingInfo);

        assertThat(duckingInfo.usagesHoldingFocus).asList()
                .containsExactly(AudioUsage.AUDIO_USAGE_MEDIA.toString(),
                        AudioUsage.AUDIO_USAGE_NOTIFICATION.toString());
    }

    @Test
    public void generateDuckingInfo_includesSamePlaybackTrackMetadataHoldingFocus() {
        CarDuckingInfo carDuckingInfo = getCarDuckingInfo();

        DuckingInfo duckingInfo = CarHalAudioUtils.generateDuckingInfo(carDuckingInfo);

        assertThat(duckingInfo.playbackMetaDataHoldingFocus)
                .asList()
                .containsExactlyElementsIn(PLAYBACKTRACK_METADATA_HOLDING_FOCUS);
    }

    private CarDuckingInfo getCarDuckingInfo() {
        return new CarDuckingInfo(
                ZONE_ID,
                ADDRESSES_TO_DUCK,
                ADDRESSES_TO_UNDUCK,
                PLAYBACKTRACK_METADATA_HOLDING_FOCUS);
    }
}
