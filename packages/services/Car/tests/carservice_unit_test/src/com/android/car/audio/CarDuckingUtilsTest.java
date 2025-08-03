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

import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_CALL_ASSISTANT;
import static android.media.AudioAttributes.USAGE_EMERGENCY;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_NOTIFICATION;
import static android.media.AudioAttributes.USAGE_SAFETY;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.hardware.audio.common.PlaybackTrackMetadata;
import android.media.AudioAttributes;
import android.media.AudioFocusInfo;
import android.media.AudioManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CarDuckingUtilsTest {
    private static final String MEDIA_ADDRESS = "media";
    private static final String EMERGENCY_ADDRESS = "emergency";
    private static final String CALL_ADDRESS = "call";
    private static final String NAVIGATION_ADDRESS = "navigation";

    private static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                    /* useCoreAudioRouting= */ false);

    private static final @CarAudioContext.AudioContext int TEST_MEDIA_AUDIO_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
    private static final @CarAudioContext.AudioContext int TEST_EMERGENCY_AUDIO_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_EMERGENCY));
    private static final @CarAudioContext.AudioContext int TEST_NAVIGATION_AUDIO_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE));
    private static final @CarAudioContext.AudioContext int TEST_CALL_AUDIO_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_CALL_ASSISTANT));

    private static final int ZONE_ID = 0;

    @Test
    public void getAudioAttributesHoldingFocus_withNoHolders_returnsEmptyArray() {
        List<AudioAttributes> audioAttributesList =
                CarDuckingUtils.getAudioAttributesHoldingFocus(new ArrayList<>());

        assertWithMessage("Audio attribute list")
                .that(audioAttributesList).isEmpty();
    }

    @Test
    public void getAudioAttributesHoldingFocus_removesDuplicateUsages() {
        List<AudioAttributes> audioAttributes = new ArrayList<>(/* initialCapacity= */ 3);
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION));
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION));
        List<AudioFocusInfo> focusHolders =
                generateAudioFocusInfoForAudioAttributes(audioAttributes);

        List<AudioAttributes> attributesHoldingFocus =
                CarDuckingUtils.getAudioAttributesHoldingFocus(focusHolders);

        assertWithMessage(" Attributes holding focus")
                .that(attributesHoldingFocus)
                .containsExactly(CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION),
                        CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
    }

    @Test
    public void getAudioAttributesHoldingFocus_includesSystemAudioAttributes() {
        List<AudioAttributes> audioAttributes = new ArrayList<>(/* initialCapacity= */ 3);
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_SAFETY));
        audioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_EMERGENCY));
        List<AudioFocusInfo> focusHolders =
                generateAudioFocusInfoForAudioAttributes(audioAttributes);

        List<AudioAttributes> audioAttributesHoldingFocus =
                CarDuckingUtils.getAudioAttributesHoldingFocus(focusHolders);

        assertWithMessage("Attributes holding focus")
                .that(audioAttributesHoldingFocus).containsExactly(
                        CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA),
                        CarAudioContext.getAudioAttributeFromUsage(USAGE_SAFETY),
                        CarAudioContext.getAudioAttributeFromUsage(USAGE_EMERGENCY));
    }

    @Test
    public void generateDuckingInfo_succeed() {
        CarAudioZone mockZone = generateAudioZoneMock();
        List<AudioAttributes> activeAudioAttributes = new ArrayList<>(/* initialCapacity= */ 3);
        activeAudioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
        activeAudioAttributes.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_SAFETY));
        activeAudioAttributes.add(CarAudioContext
                .getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE));
        List<AudioAttributes> audioAttributesWithoutSafety =
                new ArrayList<>(/* initialCapacity= */ 2);
        audioAttributesWithoutSafety.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
        audioAttributesWithoutSafety.add(CarAudioContext
                .getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE));
        List<AudioAttributes> audioAttributesWithOnlyMedia =
                new ArrayList<>(/* initialCapacity= */ 1);
        audioAttributesWithOnlyMedia.add(CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));

        List<PlaybackTrackMetadata> playbackTrackMetadataHoldingFocus =
                CarHalAudioUtils.audioAttributesToMetadatas(activeAudioAttributes, mockZone);
        List<AudioAttributes> attributesToDuck =
                CarAudioContext.evaluateAudioAttributesToDuck(activeAudioAttributes);

        CarDuckingInfo duckingInfo =
                CarDuckingUtils.generateDuckingInfo(getEmptyCarDuckingInfo(),
                        attributesToDuck, activeAudioAttributes, mockZone);

        assertThat(duckingInfo.getZoneId()).isEqualTo(ZONE_ID);
        assertThat(duckingInfo.getAddressesToDuck())
                .containsExactly(MEDIA_ADDRESS, NAVIGATION_ADDRESS);
        assertThat(duckingInfo.getAddressesToUnduck()).isEmpty();
        assertThat(duckingInfo.getPlaybackMetaDataHoldingFocus())
                .containsExactlyElementsIn(playbackTrackMetadataHoldingFocus);

        // Then decimate safety
        attributesToDuck = CarAudioContext
                .evaluateAudioAttributesToDuck(audioAttributesWithoutSafety);
        playbackTrackMetadataHoldingFocus =
                CarHalAudioUtils.audioAttributesToMetadatas(audioAttributesWithoutSafety,
                        mockZone);

        CarDuckingInfo duckingInfo1 =
                CarDuckingUtils.generateDuckingInfo(duckingInfo, attributesToDuck,
                        audioAttributesWithoutSafety, mockZone);

        assertThat(duckingInfo1.getZoneId()).isEqualTo(ZONE_ID);
        assertThat(duckingInfo1.getAddressesToDuck()).containsExactly(MEDIA_ADDRESS);
        assertThat(duckingInfo1.getAddressesToUnduck()).containsExactly(NAVIGATION_ADDRESS);
        assertThat(duckingInfo1.getPlaybackMetaDataHoldingFocus())
                .containsExactlyElementsIn(playbackTrackMetadataHoldingFocus);

        // Then decimate nav
        attributesToDuck = CarAudioContext
                .evaluateAudioAttributesToDuck(audioAttributesWithOnlyMedia);
        playbackTrackMetadataHoldingFocus =
                CarHalAudioUtils.audioAttributesToMetadatas(audioAttributesWithOnlyMedia, mockZone);

        CarDuckingInfo duckingInfo2 =
                CarDuckingUtils.generateDuckingInfo(duckingInfo1, attributesToDuck,
                        audioAttributesWithOnlyMedia, mockZone);

        assertThat(duckingInfo2.getZoneId()).isEqualTo(ZONE_ID);
        assertThat(duckingInfo2.getAddressesToDuck()).isEmpty();
        assertThat(duckingInfo2.getAddressesToUnduck()).containsExactly(MEDIA_ADDRESS);
        assertThat(duckingInfo2.getPlaybackMetaDataHoldingFocus())
                .containsExactlyElementsIn(playbackTrackMetadataHoldingFocus);

        // back to none holding focus
        CarHalAudioUtils.audioAttributesToMetadatas(audioAttributesWithOnlyMedia, mockZone);

        CarDuckingInfo duckingInfo3 =
                CarDuckingUtils.generateDuckingInfo(duckingInfo2, Collections.EMPTY_LIST,
                        Collections.EMPTY_LIST, mockZone);

        assertThat(duckingInfo3.getZoneId()).isEqualTo(ZONE_ID);
        assertThat(duckingInfo3.getAddressesToDuck()).isEmpty();
        assertThat(duckingInfo3.getAddressesToUnduck()).isEmpty();
        assertThat(duckingInfo3.getPlaybackMetaDataHoldingFocus()).isEmpty();
    }

    private static AudioFocusInfo generateAudioFocusInfoForAudioAttributes(
            AudioAttributes audioAttributes) {
        return new AudioFocusInfo(audioAttributes, /* clientUid= */ 0,
                "client_id", "package.name",
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, /* lossReceived= */ 0,
                /* flags= */ 0, /* sdk= */  0);
    }

    private static List<AudioFocusInfo> generateAudioFocusInfoForAudioAttributes(
            List<AudioAttributes> audioAttributes) {
        List<AudioFocusInfo> audioFocusInfos = new ArrayList<>(audioAttributes.size());
        for (int index = 0; index < audioAttributes.size(); index++) {
            audioFocusInfos
                    .add(generateAudioFocusInfoForAudioAttributes(audioAttributes.get(index)));
        }
        return audioFocusInfos;
    }

    private static CarAudioZone generateAudioZoneMock() {
        CarAudioZone mockZone = mock(CarAudioZone.class);
        when(mockZone.getAddressForContext(anyInt())).thenReturn(new String(""));
        when(mockZone.getAddressForContext(TEST_MEDIA_AUDIO_CONTEXT)).thenReturn(MEDIA_ADDRESS);
        when(mockZone.getAddressForContext(TEST_EMERGENCY_AUDIO_CONTEXT))
                .thenReturn(EMERGENCY_ADDRESS);
        when(mockZone.getAddressForContext(TEST_CALL_AUDIO_CONTEXT)).thenReturn(CALL_ADDRESS);
        when(mockZone.getAddressForContext(TEST_NAVIGATION_AUDIO_CONTEXT))
                .thenReturn(NAVIGATION_ADDRESS);
        when(mockZone.getAddressForContext(CarAudioContext.getInvalidContext()))
                .thenThrow(new IllegalArgumentException());
        when(mockZone.getCarAudioContext()).thenReturn(TEST_CAR_AUDIO_CONTEXT);
        return mockZone;
    }

    private CarDuckingInfo getEmptyCarDuckingInfo() {
        return new CarDuckingInfo(
                ZONE_ID,
                new ArrayList<String>(),
                new ArrayList<String>(),
                new ArrayList<PlaybackTrackMetadata>());
    }
}
