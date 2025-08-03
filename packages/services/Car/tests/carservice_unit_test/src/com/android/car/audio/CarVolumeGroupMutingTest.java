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
import static android.media.AudioAttributes.USAGE_ASSISTANT;
import static android.media.AudioAttributes.USAGE_EMERGENCY;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_SAFETY;

import static com.android.car.audio.CarAudioContext.NAVIGATION;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.media.CarAudioManager;
import android.hardware.automotive.audiocontrol.MutingInfo;
import android.util.SparseArray;

import com.android.car.audio.hal.AudioControlWrapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;


@RunWith(MockitoJUnitRunner.class)
public final class CarVolumeGroupMutingTest {

    private static final String PRIMARY_MEDIA_ADDRESS = "media";
    private static final String PRIMARY_NAVIGATION_ADDRESS = "navigation";
    private static final String PRIMARY_VOICE_ADDRESS = "voice";
    private static final String SECONDARY_ADDRESS = "secondary";
    private static final String TERTIARY_ADDRESS = "tertiary";
    private static final String EMERGENCY_ADDRESS = "emergency";
    private static final String SAFETY_ADDRESS = "safety";
    private static final int PRIMARY_ZONE_ID = CarAudioManager.PRIMARY_AUDIO_ZONE;
    private static final int SECONDARY_ZONE_ID = CarAudioManager.PRIMARY_AUDIO_ZONE + 1;
    private static final int TERTIARY_ZONE_ID = CarAudioManager.PRIMARY_AUDIO_ZONE + 2;

    private static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                    /* useCoreAudioRouting= */ false);

    private static final @CarAudioContext.AudioContext int TEST_MEDIA_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
    private static final @CarAudioContext.AudioContext int TEST_ASSISTANT_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_ASSISTANT));
    private static final @CarAudioContext.AudioContext int TEST_EMERGENCY_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_EMERGENCY));
    private static final @CarAudioContext.AudioContext int TEST_NAVIGATION_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE));
    private static final @CarAudioContext.AudioContext int TEST_SAFETY_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_SAFETY));

    private CarAudioZone mPrimaryAudioZone;
    private CarAudioZoneConfig mPrimaryAudioZoneConfig;

    private CarVolumeGroup mMusicCarVolumeGroup;
    private CarVolumeGroup mNavigationCarVolumeGroup;
    private CarVolumeGroup mVoiceCarVolumeGroup;
    private CarVolumeGroup mSecondaryZoneVolumeGroup;
    private CarAudioZone mSingleDevicePrimaryZone;
    private CarAudioZone mSingleDeviceSecondaryZone;
    private CarAudioZone mSingleDeviceTertiaryZone;
    private CarVolumeGroup mTertiaryZoneVolumeGroup;

    @Mock
    AudioControlWrapper mMockAudioControlWrapper;

    @Before
    public void setUp() {
        mMusicCarVolumeGroup = groupWithContextAndAddress(TEST_MEDIA_CONTEXT,
                PRIMARY_MEDIA_ADDRESS);
        mNavigationCarVolumeGroup = groupWithContextAndAddress(
                TEST_NAVIGATION_CONTEXT,
                PRIMARY_NAVIGATION_ADDRESS);
        mVoiceCarVolumeGroup = groupWithContextAndAddress(
                TEST_ASSISTANT_CONTEXT,
                PRIMARY_VOICE_ADDRESS);
        mSecondaryZoneVolumeGroup = groupWithContextAndAddress(
                TEST_MEDIA_CONTEXT,
                SECONDARY_ADDRESS);
        mTertiaryZoneVolumeGroup = groupWithContextAndAddress(
                TEST_MEDIA_CONTEXT,
                TERTIARY_ADDRESS);

        mPrimaryAudioZoneConfig =
                new CarAudioZoneConfig.Builder("Primary Zone Config 0", PRIMARY_ZONE_ID,
                        /* zoneConfigId= */ 0, /* isDefault= */ true)
                        .addVolumeGroup(mMusicCarVolumeGroup)
                        .addVolumeGroup(mNavigationCarVolumeGroup)
                        .addVolumeGroup(mVoiceCarVolumeGroup)
                        .build();
        mPrimaryAudioZone =
                new TestCarAudioZoneBuilder("Primary Zone", PRIMARY_ZONE_ID)
                        .addCarAudioZoneConfig(mPrimaryAudioZoneConfig)
                        .build();

        mSingleDevicePrimaryZone = createAudioZone(mMusicCarVolumeGroup, "Primary Zone",
                PRIMARY_ZONE_ID);

        mSingleDeviceSecondaryZone = createAudioZone(mSecondaryZoneVolumeGroup, "Secondary Zone",
                SECONDARY_ZONE_ID);

        mSingleDeviceTertiaryZone = createAudioZone(mTertiaryZoneVolumeGroup, "Tertiary Zone",
                TERTIARY_ZONE_ID);

        when(mMockAudioControlWrapper
                .supportsFeature(AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_GROUP_MUTING))
                .thenReturn(true);
    }

    @Test
    public void constructor_withNullZones_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            new CarVolumeGroupMuting(null, mMockAudioControlWrapper);
        });

        assertWithMessage("Constructor exception")
                .that(thrown).hasMessageThat().contains("Car Audio Zones");
    }

    @Test
    public void constructor_withEmptyZonesList_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            new CarVolumeGroupMuting(new SparseArray<>(), mMockAudioControlWrapper);
        });

        assertWithMessage("Constructor exception")
                .that(thrown).hasMessageThat().contains("zone must be present");
    }

    @Test
    public void constructor_withNullAudioControlWrapper_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> {
            new CarVolumeGroupMuting(getAudioZones(mPrimaryAudioZone), null);
        });

        assertWithMessage("Constructor exception")
                .that(thrown).hasMessageThat().contains("Audio Control Wrapper");
    }

    @Test
    public void constructor_withGroupMutingFeatureNotSupported_fails() {
        when(mMockAudioControlWrapper
                .supportsFeature(AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_GROUP_MUTING))
                .thenReturn(false);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> {
            new CarVolumeGroupMuting(getAudioZones(mPrimaryAudioZone), mMockAudioControlWrapper);
        });

        assertWithMessage("Constructor exception")
                .that(thrown).hasMessageThat().contains("IAudioControl");
    }

    @Test
    public void constructor_withZoneList_initializedWithEmptyInfoList() {
        setUpCarVolumeGroupIsMuted(mMusicCarVolumeGroup, false);
        setUpCarVolumeGroupIsMuted(mNavigationCarVolumeGroup, false);
        setUpCarVolumeGroupIsMuted(mVoiceCarVolumeGroup, false);

        CarVolumeGroupMuting carGroupMuting =
                new CarVolumeGroupMuting(getAudioZones(mPrimaryAudioZone),
                        mMockAudioControlWrapper);

        assertWithMessage("Last muting information")
                .that(carGroupMuting.getLastMutingInformation()).isEmpty();
    }

    @Test
    public void carMuteChanged_withNoGroupMuted_forSingleZone_setsEmptyMutedDevices() {
        setUpCarVolumeGroupIsMuted(mMusicCarVolumeGroup, false);
        setUpCarVolumeGroupIsMuted(mNavigationCarVolumeGroup, false);
        setUpCarVolumeGroupIsMuted(mVoiceCarVolumeGroup, false);
        CarVolumeGroupMuting carGroupMuting =
                new CarVolumeGroupMuting(getAudioZones(mPrimaryAudioZone),
                        mMockAudioControlWrapper);

        carGroupMuting.carMuteChanged();

        List<MutingInfo> mutingInfo = captureMutingInfoList();
        MutingInfo info = mutingInfo.get(mutingInfo.size() - 1);
        assertWithMessage("Device addresses to mute")
                .that(info.deviceAddressesToMute).asList().isEmpty();
    }

    @Test
    public void carMuteChanged_withNoGroupMuted_forSingleZone_setsAllDeviceUnMuted() {
        setUpCarVolumeGroupIsMuted(mMusicCarVolumeGroup, false);
        setUpCarVolumeGroupIsMuted(mNavigationCarVolumeGroup, false);
        setUpCarVolumeGroupIsMuted(mVoiceCarVolumeGroup, false);
        CarVolumeGroupMuting carGroupMuting =
                new CarVolumeGroupMuting(getAudioZones(mPrimaryAudioZone),
                        mMockAudioControlWrapper);

        carGroupMuting.carMuteChanged();

        List<MutingInfo> mutingInfo = captureMutingInfoList();
        MutingInfo info = mutingInfo.get(mutingInfo.size() - 1);
        assertWithMessage("Device addresses to un-mute")
                .that(info.deviceAddressesToUnmute).asList()
                .containsExactly(PRIMARY_MEDIA_ADDRESS, PRIMARY_NAVIGATION_ADDRESS,
                        PRIMARY_VOICE_ADDRESS);
    }

    @Test
    public void carMuteChanged_withMusicGroupMuted_forSingleZone_setsMusicDeviceToMuted() {
        setUpCarVolumeGroupIsMuted(mMusicCarVolumeGroup, true);
        setUpCarVolumeGroupIsMuted(mNavigationCarVolumeGroup, false);
        setUpCarVolumeGroupIsMuted(mVoiceCarVolumeGroup, false);
        CarVolumeGroupMuting carGroupMuting =
                new CarVolumeGroupMuting(getAudioZones(mPrimaryAudioZone),
                        mMockAudioControlWrapper);

        carGroupMuting.carMuteChanged();

        List<MutingInfo> mutingInfo = captureMutingInfoList();
        MutingInfo info = mutingInfo.get(mutingInfo.size() - 1);
        assertWithMessage("Device addresses to mute")
                .that(info.deviceAddressesToMute).asList().containsExactly(PRIMARY_MEDIA_ADDRESS);
    }

    @Test
    public void carMuteChanged_withMusicGroupMuted_forSingleZone_setOtherDeviceToUnMuted() {
        setUpCarVolumeGroupIsMuted(mMusicCarVolumeGroup, true);
        setUpCarVolumeGroupIsMuted(mNavigationCarVolumeGroup, false);
        setUpCarVolumeGroupIsMuted(mVoiceCarVolumeGroup, false);
        CarVolumeGroupMuting carGroupMuting =
                new CarVolumeGroupMuting(getAudioZones(mPrimaryAudioZone),
                        mMockAudioControlWrapper);

        carGroupMuting.carMuteChanged();

        List<MutingInfo> mutingInfo = captureMutingInfoList();
        MutingInfo info = mutingInfo.get(mutingInfo.size() - 1);
        assertWithMessage("Device addresses to un-mute")
                .that(info.deviceAddressesToUnmute).asList().containsExactly(
                PRIMARY_NAVIGATION_ADDRESS, PRIMARY_VOICE_ADDRESS);
    }

    @Test
    public void carMuteChanged_withAllGroupsMuted_forSingleZone_setsAllDevicesMuted() {
        setUpCarVolumeGroupIsMuted(mMusicCarVolumeGroup, true);
        setUpCarVolumeGroupIsMuted(mNavigationCarVolumeGroup, true);
        setUpCarVolumeGroupIsMuted(mVoiceCarVolumeGroup, true);
        CarVolumeGroupMuting carGroupMuting =
                new CarVolumeGroupMuting(getAudioZones(mPrimaryAudioZone),
                        mMockAudioControlWrapper);

        carGroupMuting.carMuteChanged();

        List<MutingInfo> mutingInfo = captureMutingInfoList();
        MutingInfo info = mutingInfo.get(mutingInfo.size() - 1);
        assertWithMessage("Device addresses to mute")
                .that(info.deviceAddressesToMute).asList().containsExactly(PRIMARY_MEDIA_ADDRESS,
                PRIMARY_NAVIGATION_ADDRESS, PRIMARY_VOICE_ADDRESS);
    }

    @Test
    public void carMuteChanged_withAllGroupsMuted_forSingleZone_setsUnMutedDevicesToEmpty() {
        setUpCarVolumeGroupIsMuted(mMusicCarVolumeGroup, true);
        setUpCarVolumeGroupIsMuted(mNavigationCarVolumeGroup, true);
        setUpCarVolumeGroupIsMuted(mVoiceCarVolumeGroup, true);
        CarVolumeGroupMuting carGroupMuting =
                new CarVolumeGroupMuting(getAudioZones(mPrimaryAudioZone),
                        mMockAudioControlWrapper);

        carGroupMuting.carMuteChanged();

        List<MutingInfo> mutingInfo = captureMutingInfoList();
        MutingInfo info = mutingInfo.get(mutingInfo.size() - 1);
        assertWithMessage("Device addresses to un-mute")
                .that(info.deviceAddressesToUnmute).asList().isEmpty();
    }

    @Test
    public void carMuteChanged_withNoGroupsMuted_forMultiZones_setMultipleZoneInfo() {
        setUpCarVolumeGroupIsMuted(mMusicCarVolumeGroup, false);
        setUpCarVolumeGroupIsMuted(mSecondaryZoneVolumeGroup, false);
        setUpCarVolumeGroupIsMuted(mTertiaryZoneVolumeGroup, false);
        CarVolumeGroupMuting carGroupMuting =
                new CarVolumeGroupMuting(getAudioZones(mSingleDevicePrimaryZone,
                        mSingleDeviceSecondaryZone, mSingleDeviceTertiaryZone),
                        mMockAudioControlWrapper);

        carGroupMuting.carMuteChanged();

        assertWithMessage("Last muting information")
                .that(captureMutingInfoList()).hasSize(3);
    }

    @Test
    public void carMuteChanged_withNoGroupsMuted_forMultiZones_setsAllZonesDevicesUnMuted() {
        setUpCarVolumeGroupIsMuted(mMusicCarVolumeGroup, false);
        setUpCarVolumeGroupIsMuted(mSecondaryZoneVolumeGroup, false);
        setUpCarVolumeGroupIsMuted(mTertiaryZoneVolumeGroup, false);
        CarVolumeGroupMuting carGroupMuting =
                new CarVolumeGroupMuting(getAudioZones(mSingleDevicePrimaryZone,
                        mSingleDeviceSecondaryZone, mSingleDeviceTertiaryZone),
                        mMockAudioControlWrapper);

        carGroupMuting.carMuteChanged();

        for (MutingInfo info : captureMutingInfoList()) {
            assertWithMessage("Devices addresses to un-mute for zone %s", info.zoneId)
                    .that(info.deviceAddressesToUnmute).asList().hasSize(1);
        }
    }

    @Test
    public void carMuteChanged_withOneZoneMuted_forMultiZones_setsOnlyOneZoneMuted() {
        setUpCarVolumeGroupIsMuted(mSecondaryZoneVolumeGroup, true);
        setUpCarVolumeGroupIsMuted(mMusicCarVolumeGroup, false);
        setUpCarVolumeGroupIsMuted(mTertiaryZoneVolumeGroup, false);
        CarVolumeGroupMuting carGroupMuting =
                new CarVolumeGroupMuting(getAudioZones(mSingleDevicePrimaryZone,
                        mSingleDeviceSecondaryZone, mSingleDeviceTertiaryZone),
                        mMockAudioControlWrapper);

        carGroupMuting.carMuteChanged();

        for (MutingInfo info : captureMutingInfoList()) {
            if (info.zoneId != SECONDARY_ZONE_ID) {
                continue;
            }
            assertWithMessage("Secondary zone devices addresses to mute")
                    .that(info.deviceAddressesToMute).asList().containsExactly(SECONDARY_ADDRESS);
        }
    }

    @Test
    public void setRestrictMuting_isMutingRestrictedTrue_mutesNonCriticalVolumeGroups() {
        setUpCarVolumeGroupIsMuted(mSecondaryZoneVolumeGroup, false);
        setUpCarVolumeGroupIsMuted(mMusicCarVolumeGroup, false);
        setUpCarVolumeGroupIsMuted(mTertiaryZoneVolumeGroup, false);
        CarVolumeGroupMuting carGroupMuting =
                new CarVolumeGroupMuting(getAudioZones(mSingleDevicePrimaryZone,
                        mSingleDeviceSecondaryZone, mSingleDeviceTertiaryZone),
                        mMockAudioControlWrapper);

        carGroupMuting.setRestrictMuting(true);

        for (MutingInfo info : captureMutingInfoList()) {
            assertWithMessage("Devices addresses to mute for zone %s", info.zoneId)
                    .that(info.deviceAddressesToMute).asList().hasSize(1);
        }
    }

    @Test
    public void setRestrictMuting_isMutingRestrictedTrue_leavesCriticalGroupsAsIs() {
        setUpCarVolumeGroupIsMuted(mMusicCarVolumeGroup, false);
        setUpCarVolumeGroupHasCriticalAudioContexts(mMusicCarVolumeGroup);
        setUpCarVolumeGroupIsMuted(mSecondaryZoneVolumeGroup, true);
        setUpCarVolumeGroupHasCriticalAudioContexts(mSecondaryZoneVolumeGroup);
        CarVolumeGroupMuting carGroupMuting = new CarVolumeGroupMuting(
                getAudioZones(mSingleDevicePrimaryZone, mSingleDeviceSecondaryZone),
                mMockAudioControlWrapper);

        carGroupMuting.setRestrictMuting(true);

        for (MutingInfo info : captureMutingInfoList()) {
            if (info.zoneId == PRIMARY_ZONE_ID) {
                assertWithMessage("Devices addresses to unmute for zone %s", info.zoneId)
                        .that(info.deviceAddressesToUnmute).asList().containsExactly(
                        PRIMARY_MEDIA_ADDRESS);

            } else if (info.zoneId == SECONDARY_ZONE_ID) {
                assertWithMessage("Devices addresses to mute for zone %s", info.zoneId)
                        .that(info.deviceAddressesToMute).asList().containsExactly(
                                SECONDARY_ADDRESS);
            }
        }
    }

    @Test
    public void generateMutingInfoFromZone_withNoGroupsMuted_returnsEmptyMutedList() {
        setUpCarVolumeGroupIsMuted(mMusicCarVolumeGroup, false);
        setUpCarVolumeGroupIsMuted(mNavigationCarVolumeGroup, false);
        setUpCarVolumeGroupIsMuted(mVoiceCarVolumeGroup, false);

        MutingInfo info = CarVolumeGroupMuting.generateMutingInfoFromZone(mPrimaryAudioZone,
                /* isMutingRestricted= */ false);

        assertWithMessage("Device addresses to mute")
                .that(info.deviceAddressesToMute).asList().isEmpty();
    }

    @Test
    public void generateMutingInfoFromZone_withOneGroupMuted_returnsOneMutedDevice() {
        setUpCarVolumeGroupIsMuted(mMusicCarVolumeGroup, true);
        setUpCarVolumeGroupIsMuted(mNavigationCarVolumeGroup, false);
        setUpCarVolumeGroupIsMuted(mVoiceCarVolumeGroup, false);

        MutingInfo info = CarVolumeGroupMuting.generateMutingInfoFromZone(mPrimaryAudioZone,
                /* isMutingRestricted= */ false);

        assertWithMessage("Device addresses to mute")
                .that(info.deviceAddressesToMute).asList().containsExactly(PRIMARY_MEDIA_ADDRESS);
    }

    @Test
    public void generateMutingInfoFromZone_withAllGroupsMuted_returnsAllDevicesMuted() {
        setUpCarVolumeGroupIsMuted(mMusicCarVolumeGroup, true);
        setUpCarVolumeGroupIsMuted(mNavigationCarVolumeGroup, true);
        setUpCarVolumeGroupIsMuted(mVoiceCarVolumeGroup, true);

        MutingInfo info = CarVolumeGroupMuting.generateMutingInfoFromZone(mPrimaryAudioZone,
                /* isMutingRestricted= */ false);

        assertWithMessage("Device addresses to mute")
                .that(info.deviceAddressesToMute).asList().containsExactly(PRIMARY_MEDIA_ADDRESS,
                PRIMARY_NAVIGATION_ADDRESS, PRIMARY_VOICE_ADDRESS);
    }

    @Test
    public void generateMutingInfoFromZone_withMutedMultiDeviceGroup_returnsAllDevicesMuted() {
        CarAudioZone primaryZone = createAudioZone(
                new VolumeGroupBuilder()
                        .addDeviceAddressAndContexts(TEST_MEDIA_CONTEXT, PRIMARY_MEDIA_ADDRESS)
                        .addDeviceAddressAndContexts(TEST_ASSISTANT_CONTEXT, PRIMARY_VOICE_ADDRESS)
                        .addDeviceAddressAndContexts(NAVIGATION, PRIMARY_NAVIGATION_ADDRESS)
                        .setIsMuted(true)
                        .build(), "Primary Zone", PRIMARY_ZONE_ID);

        MutingInfo info = CarVolumeGroupMuting.generateMutingInfoFromZone(primaryZone,
                /* isMutingRestricted= */ false);

        assertWithMessage("Device addresses to mute")
                .that(info.deviceAddressesToMute).asList().containsExactly(PRIMARY_MEDIA_ADDRESS,
                PRIMARY_NAVIGATION_ADDRESS, PRIMARY_VOICE_ADDRESS);
    }

    @Test
    public void generateMutingInfoFromZone_withUnMutedMultiDeviceGroup_returnsAllDevicesUnMuted() {
        CarAudioZone primaryZone = createAudioZone(
                new VolumeGroupBuilder()
                        .addDeviceAddressAndContexts(TEST_MEDIA_CONTEXT, PRIMARY_MEDIA_ADDRESS)
                        .addDeviceAddressAndContexts(TEST_ASSISTANT_CONTEXT, PRIMARY_VOICE_ADDRESS)
                        .addDeviceAddressAndContexts(NAVIGATION, PRIMARY_NAVIGATION_ADDRESS)
                        .build(), "Primary Zone", PRIMARY_ZONE_ID);

        MutingInfo info = CarVolumeGroupMuting.generateMutingInfoFromZone(primaryZone,
                /* isMutingRestricted= */ false);

        assertWithMessage("Device addresses to un-mute")
                .that(info.deviceAddressesToUnmute).asList().containsExactly(PRIMARY_MEDIA_ADDRESS,
                PRIMARY_NAVIGATION_ADDRESS, PRIMARY_VOICE_ADDRESS);
    }

    @Test
    public void generateMutingInfoFromZone_mutingRestricted_mutesAllNonCriticalDevices() {
        CarAudioZone primaryZone = createAudioZone(
                new VolumeGroupBuilder()
                        .addDeviceAddressAndContexts(TEST_MEDIA_CONTEXT, PRIMARY_MEDIA_ADDRESS)
                        .addDeviceAddressAndContexts(TEST_ASSISTANT_CONTEXT, PRIMARY_VOICE_ADDRESS)
                        .addDeviceAddressAndContexts(NAVIGATION, PRIMARY_NAVIGATION_ADDRESS)
                        .build(), "Primary Zone", PRIMARY_ZONE_ID);

        MutingInfo info = CarVolumeGroupMuting.generateMutingInfoFromZone(primaryZone,
                /* isMutingRestricted= */ true);

        assertWithMessage("Device addresses to un-mute")
                .that(info.deviceAddressesToMute).asList().containsExactly(PRIMARY_MEDIA_ADDRESS,
                PRIMARY_NAVIGATION_ADDRESS, PRIMARY_VOICE_ADDRESS);
    }

    @Test
    public void generateMutingInfoFromZone_mutingRestricted_setsAllCriticalGroupsToTheirState() {
        CarAudioZoneConfig primaryZoneConfig =
                new CarAudioZoneConfig.Builder("Primary Zone Config 0", PRIMARY_ZONE_ID,
                        /* zoneConfigId= */ 0, /* isDefault= */ true)
                        .addVolumeGroup(new VolumeGroupBuilder()
                                .addDeviceAddressAndContexts(
                                        TEST_EMERGENCY_CONTEXT,
                                        EMERGENCY_ADDRESS)
                                .addDeviceAddressAndContexts(
                                        TEST_ASSISTANT_CONTEXT,
                                        PRIMARY_VOICE_ADDRESS)
                                .build())
                        .addVolumeGroup(new VolumeGroupBuilder()
                                .addDeviceAddressAndContexts(
                                        TEST_SAFETY_CONTEXT, SAFETY_ADDRESS)
                                .addDeviceAddressAndContexts(
                                        TEST_NAVIGATION_CONTEXT,
                                        PRIMARY_NAVIGATION_ADDRESS)
                                .setIsMuted(true)
                                .build()
                        )
                        .build();
        CarAudioZone primaryZone =
                new TestCarAudioZoneBuilder("Primary Zone", PRIMARY_ZONE_ID)
                .addCarAudioZoneConfig(primaryZoneConfig)
                .build();

        setUpCarVolumeGroupHasCriticalAudioContexts(primaryZone.getCurrentVolumeGroups()[0]);
        setUpCarVolumeGroupHasCriticalAudioContexts(primaryZone.getCurrentVolumeGroups()[1]);

        MutingInfo info = CarVolumeGroupMuting.generateMutingInfoFromZone(primaryZone,
                /* isMutingRestricted= */ true);

        assertWithMessage("Device addresses to mute")
                .that(info.deviceAddressesToMute).asList()
                .containsExactly(SAFETY_ADDRESS, PRIMARY_NAVIGATION_ADDRESS);
        assertWithMessage("Device addresses to un-mute")
                .that(info.deviceAddressesToUnmute).asList()
                .containsExactly(EMERGENCY_ADDRESS, PRIMARY_VOICE_ADDRESS);
    }


    private CarAudioZone createAudioZone(CarVolumeGroup volumeGroup, String name, int zoneId) {
        CarAudioZoneConfig zoneConfig =
                new CarAudioZoneConfig.Builder("Config", zoneId,
                        /* zoneConfigId= */ 0, /* isDefault= */ true)
                        .addVolumeGroup(volumeGroup).build();
        return new TestCarAudioZoneBuilder(name, zoneId).addCarAudioZoneConfig(zoneConfig).build();
    }

    private CarVolumeGroup groupWithContextAndAddress(int context, String address) {
        return new VolumeGroupBuilder().addDeviceAddressAndContexts(context, address).build();
    }

    private List<MutingInfo> captureMutingInfoList() {
        ArgumentCaptor<List<MutingInfo>> captor = ArgumentCaptor.forClass(List.class);
        verify(mMockAudioControlWrapper).onDevicesToMuteChange(captor.capture());
        return captor.getValue();
    }

    private void setUpCarVolumeGroupIsMuted(CarVolumeGroup carVolumeGroup, boolean muted) {
        when(carVolumeGroup.isMuted()).thenReturn(muted);
    }

    private void setUpCarVolumeGroupHasCriticalAudioContexts(CarVolumeGroup carVolumeGroup) {
        when(carVolumeGroup.hasCriticalAudioContexts()).thenReturn(true);
    }

    private SparseArray<CarAudioZone> getAudioZones(CarAudioZone... zones) {
        SparseArray<CarAudioZone> audioZones = new SparseArray<>();
        for (CarAudioZone zone : zones) {
            audioZones.put(zone.getId(), zone);
        }
        return audioZones;
    }
}
