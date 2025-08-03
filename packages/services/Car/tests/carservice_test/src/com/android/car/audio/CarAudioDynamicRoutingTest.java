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

import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_MEDIA;

import static com.android.car.audio.CarAudioContext.AudioContext;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.media.CarAudioManager;
import android.car.test.AbstractExpectableTestCase;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.audiopolicy.AudioMix;
import android.media.audiopolicy.AudioPolicy;
import android.util.ArrayMap;
import android.util.SparseArray;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public final class CarAudioDynamicRoutingTest extends AbstractExpectableTestCase {
    private static final String MUSIC_ADDRESS = "bus0_music";
    private static final String NAV_ADDRESS = "bus1_nav";

    private static final AudioAttributes TEST_MEDIA_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA);
    private static final AudioAttributes TEST_NAVIGATION_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);

    private static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                    /* useCoreAudioRouting= */ false);

    private static final @AudioContext int TEST_MEDIA_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_MEDIA_ATTRIBUTE);
    private static final @AudioContext int TEST_NAVIGATION_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_NAVIGATION_ATTRIBUTE);

    @Mock
    private AudioManager mAudioManager;

    @Test
    public void setupAudioDynamicRouting() {
        AudioPolicy.Builder mockBuilder = Mockito.mock(AudioPolicy.Builder.class);
        SparseArray<CarAudioZone> zones = getTestCarAudioZones(/* withDefault= */ true,
                /* withSelected= */ true);

        CarAudioDynamicRouting.setupAudioDynamicRouting(TEST_CAR_AUDIO_CONTEXT, mAudioManager,
                mockBuilder, zones);

        ArgumentCaptor<AudioMix> audioMixCaptor = ArgumentCaptor.forClass(AudioMix.class);
        verify(mockBuilder, times(2)).addMix(audioMixCaptor.capture());
        AudioMix audioMix = audioMixCaptor.getValue();
        expectWithMessage("Music address registered").that(audioMix.getRegistration())
                .isEqualTo(MUSIC_ADDRESS);
        expectWithMessage("Contains Music device")
                .that(audioMix.isRoutedToDevice(AudioSystem.DEVICE_OUT_BUS, MUSIC_ADDRESS))
                .isTrue();
        expectWithMessage("Does not contain Nav device")
                .that(audioMix.isRoutedToDevice(AudioSystem.DEVICE_OUT_BUS, NAV_ADDRESS))
                .isFalse();
        expectWithMessage("Affected media usage")
                .that(audioMix.isAffectingUsage(AudioAttributes.USAGE_MEDIA))
                .isTrue();
        expectWithMessage("Affected game usage")
                .that(audioMix.isAffectingUsage(AudioAttributes.USAGE_GAME))
                .isTrue();
        expectWithMessage("Affected unknown usage")
                .that(audioMix.isAffectingUsage(AudioAttributes.USAGE_UNKNOWN))
                .isTrue();
        expectWithMessage("Non-affected voice comm usage")
                .that(audioMix.isAffectingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION))
                .isFalse();
        expectWithMessage("Non-affected voice comm signalling usage")
                .that(audioMix.isAffectingUsage(
                        AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING))
                .isFalse();
        expectWithMessage("Non-affected alarm usage")
                .that(audioMix.isAffectingUsage(AudioAttributes.USAGE_ALARM))
                .isFalse();
        expectWithMessage("Non-affected notification usage")
                .that(audioMix.isAffectingUsage(AudioAttributes.USAGE_NOTIFICATION))
                .isFalse();
        expectWithMessage("Non-affected notification ringtone usage")
                .that(audioMix.isAffectingUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE))
                .isFalse();
        expectWithMessage("Non-affected notification event usage")
                .that(audioMix.isAffectingUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT))
                .isFalse();
        expectWithMessage("Non-affected assistance accessibility usage")
                .that(audioMix.isAffectingUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY))
                .isFalse();
        expectWithMessage("Non-affected nav guidance usage")
                .that(audioMix.isAffectingUsage(
                        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE))
                .isFalse();
        expectWithMessage("Non-affected assistance sonification usage")
                .that(audioMix.isAffectingUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION))
                .isFalse();
        expectWithMessage("Non-affected assistant usage")
                .that(audioMix.isAffectingUsage(AudioAttributes.USAGE_ASSISTANT))
                .isFalse();
        expectWithMessage("Non-affected call assistant usage")
                .that(audioMix.isAffectingUsage(AudioAttributes.USAGE_CALL_ASSISTANT))
                .isFalse();
        expectWithMessage("Non-affected emergency usage")
                .that(audioMix.isAffectingUsage(AudioAttributes.USAGE_EMERGENCY))
                .isFalse();
        expectWithMessage("Non-affected safety usage")
                .that(audioMix.isAffectingUsage(AudioAttributes.USAGE_SAFETY))
                .isFalse();
        expectWithMessage("Non-affected vehicle status usage")
                .that(audioMix.isAffectingUsage(AudioAttributes.USAGE_VEHICLE_STATUS))
                .isFalse();
        expectWithMessage("Non-affected announcement usage")
                .that(audioMix.isAffectingUsage(AudioAttributes.USAGE_ANNOUNCEMENT))
                .isFalse();
    }

    @Test
    public void setupAudioDynamicRouting_withoutSelectedConfig() {
        AudioPolicy.Builder mockBuilder = Mockito.mock(AudioPolicy.Builder.class);
        SparseArray<CarAudioZone> zones = getTestCarAudioZones(/* withDefault= */ true,
                /* withSelected= */ false);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> CarAudioDynamicRouting.setupAudioDynamicRouting(TEST_CAR_AUDIO_CONTEXT,
                        mAudioManager, mockBuilder, zones));

        expectWithMessage("No selected config exception").that(thrown).hasMessageThat()
                .contains("Selected configuration for zone");
    }

    @Test
    public void setupAudioDynamicRoutingForMirrorDevice() {
        AudioPolicy.Builder mockBuilder = Mockito.mock(AudioPolicy.Builder.class);
        AudioDeviceInfo mirrorDevice = Mockito.mock(AudioDeviceInfo.class);
        CarAudioDeviceInfo carMirrorDeviceInfo = getCarAudioDeviceInfo(MUSIC_ADDRESS,
                /* canBeRoutedWithDynamicPolicyMix= */ true, mirrorDevice);
        when(mAudioManager.getDevices(anyInt())).thenReturn(
                new AudioDeviceInfo[]{mirrorDevice});


        CarAudioDynamicRouting.setupAudioDynamicRoutingForMirrorDevice(mockBuilder,
                List.of(carMirrorDeviceInfo), mAudioManager);

        ArgumentCaptor<AudioMix> audioMixCaptor = ArgumentCaptor.forClass(AudioMix.class);

        verify(mockBuilder).addMix(audioMixCaptor.capture());
        AudioMix audioMix = audioMixCaptor.getValue();
        expectWithMessage("Music address registered").that(audioMix.getRegistration())
                .isEqualTo(MUSIC_ADDRESS);
        expectWithMessage("Contains Music device")
                .that(audioMix.isRoutedToDevice(AudioSystem.DEVICE_OUT_BUS, MUSIC_ADDRESS))
                .isTrue();
        expectWithMessage("Affected media usage")
                .that(audioMix.isAffectingUsage(AudioAttributes.USAGE_MEDIA))
                .isTrue();
    }

    private SparseArray<CarAudioZone> getTestCarAudioZones(boolean withDefault,
            boolean withSelected) {
        AudioDeviceInfo musicDeviceInfo = Mockito.mock(AudioDeviceInfo.class);
        CarAudioDeviceInfo musicCarAudioDeviceInfo = getCarAudioDeviceInfo(MUSIC_ADDRESS,
                /* canBeRoutedWithDynamicPolicyMix= */ true, musicDeviceInfo);
        AudioDeviceInfo navDeviceInfo = Mockito.mock(AudioDeviceInfo.class);
        CarAudioDeviceInfo navCarAudioDeviceInfo = getCarAudioDeviceInfo(NAV_ADDRESS,
                /* canBeRoutedWithDynamicPolicyMix= */ false, navDeviceInfo);
        when(mAudioManager.getDevices(anyInt())).thenReturn(
                new AudioDeviceInfo[]{musicDeviceInfo, navDeviceInfo});
        CarVolumeGroup mockMusicGroup = new VolumeGroupBuilder()
                .addDeviceAddressAndContexts(TEST_MEDIA_CONTEXT, MUSIC_ADDRESS)
                .addCarAudioDeviceInfoMock(musicCarAudioDeviceInfo)
                .build();
        CarVolumeGroup mockNavGroupRoutingOnMusic = new VolumeGroupBuilder()
                .addDeviceAddressAndContexts(TEST_NAVIGATION_CONTEXT, NAV_ADDRESS)
                .addCarAudioDeviceInfoMock(navCarAudioDeviceInfo)
                .build();
        CarAudioZoneConfig defaultAudioZoneConfig =
                new CarAudioZoneConfig.Builder("Default zone config 0",
                        CarAudioManager.PRIMARY_AUDIO_ZONE, /* zoneConfigId= */ 0, withDefault)
                        .addVolumeGroup(mockMusicGroup)
                        .addVolumeGroup(mockNavGroupRoutingOnMusic)
                        .build();
        CarAudioZoneConfig selectedAudioZoneConfig =
                new CarAudioZoneConfig.Builder("Selected zone config 0",
                        CarAudioManager.PRIMARY_AUDIO_ZONE, /* zoneConfigId= */ 1,
                        /* isDefault= */ false).addVolumeGroup(mockMusicGroup)
                        .addVolumeGroup(mockNavGroupRoutingOnMusic)
                        .build();
        selectedAudioZoneConfig.setIsSelected(withSelected);
        CarAudioZone carAudioZone = new CarAudioZone(TEST_CAR_AUDIO_CONTEXT, "Primary zone",
                CarAudioManager.PRIMARY_AUDIO_ZONE);
        carAudioZone.addZoneConfig(defaultAudioZoneConfig);
        carAudioZone.addZoneConfig(selectedAudioZoneConfig);
        SparseArray<CarAudioZone> zones = new SparseArray<>();
        zones.put(CarAudioManager.PRIMARY_AUDIO_ZONE, carAudioZone);
        return zones;
    }

    private CarAudioDeviceInfo getCarAudioDeviceInfo(String address,
            boolean canBeRoutedWithDynamicPolicyMix, AudioDeviceInfo audioDeviceInfo) {
        when(audioDeviceInfo.isSink()).thenReturn(true);
        when(audioDeviceInfo.getType()).thenReturn(AudioDeviceInfo.TYPE_BUS);
        when(audioDeviceInfo.getAddress()).thenReturn(address);
        AudioDeviceAttributes deviceAttributes = new AudioDeviceAttributes(audioDeviceInfo);
        CarAudioDeviceInfo carAudioDeviceInfo = Mockito.mock(CarAudioDeviceInfo.class);
        when(carAudioDeviceInfo.getAddress()).thenReturn(address);
        when(carAudioDeviceInfo.getAudioDevice()).thenReturn(deviceAttributes);
        when(carAudioDeviceInfo.getEncodingFormat())
                .thenReturn(AudioFormat.ENCODING_PCM_16BIT);
        when(carAudioDeviceInfo.getChannelCount()).thenReturn(2);
        when(carAudioDeviceInfo.canBeRoutedWithDynamicPolicyMix()).thenReturn(
                canBeRoutedWithDynamicPolicyMix);
        return carAudioDeviceInfo;
    }

    private static final class VolumeGroupBuilder {
        private SparseArray<String> mDeviceAddresses = new SparseArray<>();
        private CarAudioDeviceInfo mCarAudioDeviceInfoMock;
        private ArrayMap<String, List<Integer>> mUsagesDeviceAddresses = new ArrayMap<>();
        private boolean mIsActive = true;

        VolumeGroupBuilder addDeviceAddressAndContexts(@AudioContext int context, String address) {
            mDeviceAddresses.put(context, address);
            return this;
        }

        VolumeGroupBuilder addCarAudioDeviceInfoMock(CarAudioDeviceInfo infoMock) {
            mCarAudioDeviceInfoMock = infoMock;
            return this;
        }

        CarVolumeGroup build() {
            CarVolumeGroup carVolumeGroup = mock(CarVolumeGroup.class);
            Map<String, ArrayList<Integer>> addressToContexts = new ArrayMap<>();
            @AudioContext int[] contexts = new int[mDeviceAddresses.size()];

            for (int index = 0; index < mDeviceAddresses.size(); index++) {
                @AudioContext int context = mDeviceAddresses.keyAt(index);
                String address = mDeviceAddresses.get(context);
                when(carVolumeGroup.getAddressForContext(context)).thenReturn(address);
                if (!addressToContexts.containsKey(address)) {
                    addressToContexts.put(address, new ArrayList<>());
                }
                addressToContexts.get(address).add(context);
                contexts[index] = context;
            }

            for (int index = 0; index < mUsagesDeviceAddresses.size(); index++) {
                String address = mUsagesDeviceAddresses.keyAt(index);
                List<Integer> usagesForAddress = mUsagesDeviceAddresses.get(address);
                when(carVolumeGroup.getAllSupportedUsagesForAddress(eq(address)))
                        .thenReturn(usagesForAddress);
            }

            when(carVolumeGroup.getContexts()).thenReturn(contexts);

            for (String address : addressToContexts.keySet()) {
                when(carVolumeGroup.getContextsForAddress(address))
                        .thenReturn(ImmutableList.copyOf(addressToContexts.get(address)));
            }
            when(carVolumeGroup.getAddresses())
                    .thenReturn(ImmutableList.copyOf(addressToContexts.keySet()));

            when(carVolumeGroup.getCarAudioDeviceInfoForAddress(any()))
                    .thenReturn(mCarAudioDeviceInfoMock);
            when(carVolumeGroup.isActive()).thenReturn(mIsActive);

            return carVolumeGroup;
        }
    }
}
