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

import static android.media.AudioAttributes.CONTENT_TYPE_MUSIC;
import static android.media.AudioAttributes.CONTENT_TYPE_SPEECH;
import static android.media.AudioAttributes.CONTENT_TYPE_UNKNOWN;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_NOTIFICATION;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;

import static com.android.car.audio.CoreAudioRoutingUtils.MUSIC_ATTRIBUTES;
import static com.android.car.audio.CoreAudioRoutingUtils.OEM_ATTRIBUTES;
import static com.android.car.audio.CoreAudioRoutingUtils.OEM_FORMATTED_TAGS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.audio.policy.configuration.V7_0.AudioUsage;
import android.hardware.audio.common.PlaybackTrackMetadata;
import android.hardware.automotive.audiocontrol.DuckingInfo;
import android.media.AudioAttributes;
import android.media.audio.common.AudioChannelLayout;
import android.media.audio.common.AudioDevice;
import android.media.audio.common.AudioDeviceAddress;
import android.media.audio.common.AudioDeviceDescription;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.audio.CarAudioContext.AudioAttributesWrapper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CarHalAudioUtilsTest {
    private static final int ZONE_ID = 0;
    private static final String MEDIA_ADDRESS = "MEDIA_ADDRESS";
    private static final String NOTIFICATION_ADDRESS = "NOTIFICATION_ADDRESS";
    private static final List<String> ADDRESSES_TO_DUCK = List.of("address1", "address2");
    private static final List<String> ADDRESSES_TO_UNDUCK = List.of("address3", "address4");
    private static final AudioAttributes MEDIA_AUDIO_ATTRIBUTE = MUSIC_ATTRIBUTES;
    private static final AudioAttributes NOTIFICATION_AUDIO_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION);
    private static final List<AudioAttributes> AUDIO_ATTRIBUTES_HOLDING_FOCUS = List.of(
            MEDIA_AUDIO_ATTRIBUTE, NOTIFICATION_AUDIO_ATTRIBUTE);
    private static final String[] USAGES_LITERAL_HOLDING_FOCUS = {
        AudioUsage.AUDIO_USAGE_MEDIA.toString(), AudioUsage.AUDIO_USAGE_NOTIFICATION.toString()
    };

    private static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                    /* useCoreAudioRouting= */ false);

    private static final @CarAudioContext.AudioContext int TEST_MEDIA_AUDIO_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
    private static final @CarAudioContext.AudioContext int TEST_NOTIFICATION_AUDIO_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_NOTIFICATION));

    private final List<PlaybackTrackMetadata> mPlaybackTrackMetadataHoldingFocus =
            new ArrayList<>();

    private final CarAudioZone mCarAudioZone = generateZoneMock();

    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Before
    public void setUp() {
        for (int index = 0; index < AUDIO_ATTRIBUTES_HOLDING_FOCUS.size(); index++) {
            PlaybackTrackMetadata playbackTrackMetadata = new PlaybackTrackMetadata();
            playbackTrackMetadata.usage =
                    AUDIO_ATTRIBUTES_HOLDING_FOCUS.get(index).getSystemUsage();

            AudioDeviceDescription add = new AudioDeviceDescription();
            add.connection = new String();
            AudioDevice ad = new AudioDevice();
            ad.type = add;
            ad.address =
                    AudioDeviceAddress.id(mCarAudioZone.getAddressForContext(
                            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                                            AUDIO_ATTRIBUTES_HOLDING_FOCUS.get(index))));
            playbackTrackMetadata.sourceDevice = ad;

            mPlaybackTrackMetadataHoldingFocus.add(playbackTrackMetadata);
        }
    }

    @Test
    public void generateDuckingInfo_succeeds() {
        DuckingInfo duckingInfo = CarHalAudioUtils.generateDuckingInfo(getCarDuckingInfo());

        assertWithMessage("Generated duck info zone ")
                .that(duckingInfo.zoneId).isEqualTo(ZONE_ID);
        assertWithMessage("Generated duck info addresses to duck")
                .that(duckingInfo.deviceAddressesToDuck)
                .asList()
                .containsExactlyElementsIn(ADDRESSES_TO_DUCK);
        assertWithMessage("Generated duck info addresses to unduck")
                .that(duckingInfo.deviceAddressesToUnduck)
                .asList()
                .containsExactlyElementsIn(ADDRESSES_TO_UNDUCK);
        assertWithMessage("Generated duck info playback metadata holding focus")
                .that(duckingInfo.playbackMetaDataHoldingFocus)
                .asList()
                .containsExactlyElementsIn(mPlaybackTrackMetadataHoldingFocus);
    }

    @Test
    public void generateDuckingInfo_withNullDuckingInfo_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            CarHalAudioUtils.generateDuckingInfo(null);
        });

        assertWithMessage("Null ducking info exception")
                .that(thrown).hasMessageThat().contains("Car Ducking Info can not be null");
    }

    @Test
    public void audioAttributeToMetadata_succeeds() {
        PlaybackTrackMetadata metadata =
                CarHalAudioUtils.audioAttributeToMetadata(MEDIA_AUDIO_ATTRIBUTE);

        assertWithMessage("Attributes to Playback Track Metadata usage").that(metadata.usage)
                .isEqualTo(USAGE_MEDIA);
        assertWithMessage("Attributes to Playback Track Metadata content type")
                .that(metadata.contentType).isEqualTo(CONTENT_TYPE_MUSIC);
        assertWithMessage("Attributes to Playback Track Metadata source device address w/o zone")
                .that(metadata.sourceDevice.address.getId()).isEmpty();
    }

    @Test
    public void audioAttributeToMetadata_withOemTags_succeeds() {
        PlaybackTrackMetadata metadata = CarHalAudioUtils.audioAttributeToMetadata(OEM_ATTRIBUTES);

        assertWithMessage("OEM Attributes to Playback Track Metadata usage").that(metadata.usage)
                .isEqualTo(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
        assertWithMessage("OEM Attributes to Playback Track Metadata content type")
                .that(metadata.contentType).isEqualTo(CONTENT_TYPE_SPEECH);
        String[] tags = metadata.tags;
        assertWithMessage("OEM Attributes to Playback Track Metadata tags size").that(tags.length)
                .isEqualTo(1);
        assertWithMessage("OEM Attributes to Playback Track Metadata tag").that(tags[0])
                .isEqualTo(OEM_FORMATTED_TAGS);
        assertWithMessage("OEM Attributes to Playback Track Metadata source device address")
                .that(metadata.sourceDevice.address.getId()).isEmpty();
    }

    @Test
    public void audioAttributesWrapperToMetadata_succeeds() {
        AudioAttributesWrapper audioAttributesWrapper =
                CarAudioContext.getAudioAttributeWrapperFromUsage(USAGE_MEDIA);

        PlaybackTrackMetadata metadata = CarHalAudioUtils.audioAttributesWrapperToMetadata(
                audioAttributesWrapper);

        assertWithMessage("Attributes Wrapper to Playback Track Metadata usage")
                .that(metadata.usage).isEqualTo(USAGE_MEDIA);
        assertWithMessage("Attributes Wrapper to Playback Track Metadata content type")
                .that(metadata.contentType).isEqualTo(CONTENT_TYPE_UNKNOWN);
        String[] tags = metadata.tags;
        assertWithMessage("Attributes Wrapper to Playback Track Metadata tags size")
                .that(tags.length).isEqualTo(0);
        assertWithMessage("Attributes Wrapper to Playback Track Metadata source device address")
                .that(metadata.sourceDevice.address.getId()).isEmpty();
    }

    @Test
    public void usageToMetadata_succeeds() {
        PlaybackTrackMetadata metadata = CarHalAudioUtils.usageToMetadata(USAGE_MEDIA);

        assertWithMessage("Usage to Playback Track Metadata usage").that(metadata.usage)
                .isEqualTo(USAGE_MEDIA);
        assertWithMessage("Usage to Playback Track Metadata content type")
                .that(metadata.contentType).isEqualTo(CONTENT_TYPE_UNKNOWN);
        String[] tags = metadata.tags;
        assertWithMessage("Usage to Playback Track Metadata tags size").that(tags.length)
                .isEqualTo(0);
        assertWithMessage("Usage to Playback Track Metadata source device address")
                .that(metadata.sourceDevice.address.getId()).isEmpty();
    }

    @Test
    public void usageToMetadata_withZone_succeeds() {
        PlaybackTrackMetadata playbackTrackMetadata =
                CarHalAudioUtils.usageToMetadata(USAGE_MEDIA, mCarAudioZone);

        assertWithMessage("Usage with zone to Playback Track Metadata usage")
                .that(playbackTrackMetadata.usage).isEqualTo(USAGE_MEDIA);
        assertWithMessage("Usage with zone to Playback Track Metadata source device address")
                .that(playbackTrackMetadata.sourceDevice.address.getId()).isEqualTo(MEDIA_ADDRESS);
    }

    @Test
    public void metadataToAudioAttribute_withMusic_succeeds() {
        PlaybackTrackMetadata metadata = new PlaybackTrackMetadata();
        metadata.usage = USAGE_MEDIA;
        metadata.contentType = CONTENT_TYPE_MUSIC;
        metadata.tags = new String[0];
        metadata.channelMask = AudioChannelLayout.none(0);
        AudioDeviceDescription audioDeviceDescription = new AudioDeviceDescription();
        audioDeviceDescription.connection = new String();
        AudioDevice audioDevice = new AudioDevice();
        audioDevice.type = audioDeviceDescription;
        audioDevice.address = AudioDeviceAddress.id("");
        metadata.sourceDevice = audioDevice;

        AudioAttributes audioAttributes = CarHalAudioUtils.metadataToAudioAttribute(metadata);

        assertWithMessage("AudioAttributes converted from meta data").that(audioAttributes)
                .isEqualTo(MUSIC_ATTRIBUTES);
    }

    @Test
    public void metadataToAudioAttributes_withOemTags_succeeds() {
        PlaybackTrackMetadata metadata = new PlaybackTrackMetadata();
        metadata.usage = USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
        metadata.contentType = CONTENT_TYPE_SPEECH;
        String[] tagsArray = new String[1];
        tagsArray[0] = OEM_FORMATTED_TAGS;
        metadata.tags = tagsArray;
        metadata.channelMask = AudioChannelLayout.none(0);
        AudioDeviceDescription audioDeviceDescription = new AudioDeviceDescription();
        audioDeviceDescription.connection = new String();
        AudioDevice audioDevice = new AudioDevice();
        audioDevice.type = audioDeviceDescription;
        audioDevice.address = AudioDeviceAddress.id("");
        metadata.sourceDevice = audioDevice;

        AudioAttributes audioAttributes = CarHalAudioUtils.metadataToAudioAttribute(metadata);

        assertWithMessage("OEM AudioAttributes converted from meta data").that(audioAttributes)
                .isEqualTo(OEM_ATTRIBUTES);
    }

    @Test
    public void audioAttributeToMetadata_withZone_succeeds() {
        PlaybackTrackMetadata playbackTrackMetadata =
                CarHalAudioUtils.audioAttributeToMetadata(MEDIA_AUDIO_ATTRIBUTE, mCarAudioZone);

        assertWithMessage("Attributes with zone to Playback Track Metadata usage")
                .that(playbackTrackMetadata.usage).isEqualTo(USAGE_MEDIA);
        assertWithMessage("Attributes with zone to Playback Track Metadata source device address")
                .that(playbackTrackMetadata.sourceDevice.address.getId()).isEqualTo(MEDIA_ADDRESS);
    }

    @Test
    public void audioAttributeToMetadata_withNullZone_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            CarHalAudioUtils.audioAttributeToMetadata(MEDIA_AUDIO_ATTRIBUTE,
                    /* zone= */ null);
        });

        assertWithMessage("Attribute to metadata null zone exception")
                .that(thrown).hasMessageThat().contains("Car audio zone");
    }

    @Test
    public void audioAttributesToMetadatas_succeeds() {
        List<PlaybackTrackMetadata> playbackTrackMetadataList =
                CarHalAudioUtils.audioAttributesToMetadatas(AUDIO_ATTRIBUTES_HOLDING_FOCUS);

        assertWithMessage(
                        "Converted PlaybackTrackMetadata size for usages holding focus size %s",
                        AUDIO_ATTRIBUTES_HOLDING_FOCUS.size())
                .that(playbackTrackMetadataList.size())
                .isEqualTo(AUDIO_ATTRIBUTES_HOLDING_FOCUS.size());

        int[] usages = new int[playbackTrackMetadataList.size()];
        for (int index = 0; index < playbackTrackMetadataList.size(); index++) {
            PlaybackTrackMetadata playbackTrackMetadata = playbackTrackMetadataList.get(index);
            usages[index] = playbackTrackMetadata.usage;
            assertWithMessage("Converted usages to PlaybackTrackMetadata addresses without zone")
                    .that(playbackTrackMetadata.sourceDevice.address.getId()).isEmpty();
        }
        assertWithMessage("Converted usages to PlaybackTrackMetadata usage")
                .that(usages)
                .asList()
                .containsExactly(USAGE_MEDIA, USAGE_NOTIFICATION);
    }

    @Test
    public void audioAttributesToMetadatas_withZone_succeeds() {
        List<PlaybackTrackMetadata> playbackTrackMetadataList =
                CarHalAudioUtils.audioAttributesToMetadatas(AUDIO_ATTRIBUTES_HOLDING_FOCUS,
                        mCarAudioZone);

        assertWithMessage(
                "Converted PlaybackTrackMetadata size for usages holding focus size %s",
                AUDIO_ATTRIBUTES_HOLDING_FOCUS.size())
                .that(playbackTrackMetadataList.size())
                .isEqualTo(AUDIO_ATTRIBUTES_HOLDING_FOCUS.size());

        int[] usages = new int[playbackTrackMetadataList.size()];
        String[] addresses = new String[playbackTrackMetadataList.size()];
        for (int index = 0; index < playbackTrackMetadataList.size(); index++) {
            PlaybackTrackMetadata playbackTrackMetadata = playbackTrackMetadataList.get(index);
            usages[index] = playbackTrackMetadata.usage;
            addresses[index] = playbackTrackMetadata.sourceDevice.address.getId();
        }
        assertWithMessage("Converted usages to PlaybackTrackMetadata usage")
                .that(usages)
                .asList()
                .containsExactly(USAGE_MEDIA, USAGE_NOTIFICATION);
        assertWithMessage("Converted usages to PlaybackTrackMetadata addresses")
                .that(addresses)
                .asList()
                .containsExactly(MEDIA_ADDRESS, NOTIFICATION_ADDRESS);
    }

    @Test
    public void audioAttributesToMetadatas_withNullZone_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            CarHalAudioUtils.audioAttributesToMetadatas(AUDIO_ATTRIBUTES_HOLDING_FOCUS,
                    /* zone= */ null);
        });

        assertWithMessage("Null audio zone exception")
                .that(thrown).hasMessageThat().contains("Car audio zone can not be null");
    }

    @Test
    public void metadataToAudioAttribute_succeeds() {
        for (int index = 0; index < mPlaybackTrackMetadataHoldingFocus.size(); index++) {
            PlaybackTrackMetadata playbackTrackMetadata =
                    mPlaybackTrackMetadataHoldingFocus.get(index);
            AudioAttributes audioAttribute =
                    CarHalAudioUtils.metadataToAudioAttribute(playbackTrackMetadata);
            assertWithMessage("Build Converted PlaybackTrackMetadata[%s] audio attribute",
                    playbackTrackMetadata)
                    .that(audioAttribute)
                    .isEqualTo(CarAudioContext
                            .getAudioAttributeFromUsage(playbackTrackMetadata.usage));
        }
    }

    @Test
    public void metadatasToAudioAttributes_succeeds() {
        List<AudioAttributes> audioAttributesList =
                CarHalAudioUtils.metadataToAudioAttributes(mPlaybackTrackMetadataHoldingFocus);

        assertThat(audioAttributesList.size()).isEqualTo(mPlaybackTrackMetadataHoldingFocus.size());
        assertThat(audioAttributesList)
                .containsExactly(CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA),
                        CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION));

        for (int index = 0; index < audioAttributesList.size(); index++) {
            AudioAttributes audioAttribute = audioAttributesList.get(index);
            PlaybackTrackMetadata playbackTrackMetadata =
                    mPlaybackTrackMetadataHoldingFocus.get(index);
            assertWithMessage("Build converted playback track metadata [%s] audio attribute",
                    playbackTrackMetadata)
                    .that(audioAttribute)
                    .isEqualTo(CarAudioContext
                            .getAudioAttributeFromUsage(playbackTrackMetadata.usage));
        }
    }

    @Test
    public void metadatasToUsageStrings_succeeds() {
        String[] usageLiteralsForMetadata =
                CarHalAudioUtils.metadatasToUsageStrings(mPlaybackTrackMetadataHoldingFocus);
        assertThat(usageLiteralsForMetadata.length)
                .isEqualTo(mPlaybackTrackMetadataHoldingFocus.size());
        assertThat(usageLiteralsForMetadata)
                .asList()
                .containsExactlyElementsIn(USAGES_LITERAL_HOLDING_FOCUS);
    }

    private CarDuckingInfo getCarDuckingInfo() {
        return new CarDuckingInfo(
                ZONE_ID,
                ADDRESSES_TO_DUCK,
                ADDRESSES_TO_UNDUCK,
                mPlaybackTrackMetadataHoldingFocus);
    }

    private static CarAudioZone generateZoneMock() {
        CarAudioZone zone = mock(CarAudioZone.class);
        when(zone.getId()).thenReturn(ZONE_ID);
        when(zone.getAddressForContext(TEST_MEDIA_AUDIO_CONTEXT)).thenReturn(MEDIA_ADDRESS);
        when(zone.getAddressForContext(TEST_NOTIFICATION_AUDIO_CONTEXT))
                .thenReturn(NOTIFICATION_ADDRESS);
        when(zone.getCarAudioContext()).thenReturn(TEST_CAR_AUDIO_CONTEXT);
        return zone;
    }
}
