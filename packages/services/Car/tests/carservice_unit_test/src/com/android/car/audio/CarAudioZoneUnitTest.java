/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.car.media.CarVolumeGroupEvent.EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_AUDIO_SYSTEM;
import static android.media.AudioAttributes.USAGE_ALARM;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_ASSISTANT;
import static android.media.AudioAttributes.USAGE_MEDIA;

import static com.android.car.audio.CarAudioContext.AudioContext;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.feature.Flags;
import android.car.media.CarAudioManager;
import android.car.media.CarAudioZoneConfigInfo;
import android.car.media.CarVolumeGroupEvent;
import android.car.test.AbstractExpectableTestCase;
import android.hardware.automotive.audiocontrol.AudioGainConfigInfo;
import android.hardware.automotive.audiocontrol.Reasons;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioPlaybackConfiguration;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.SparseIntArray;

import com.android.car.audio.hal.HalAudioDeviceInfo;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public final class CarAudioZoneUnitTest extends AbstractExpectableTestCase {
    private static final String TAG = CarAudioZoneUnitTest.class.getSimpleName();
    private static final String MUSIC_ADDRESS = "bus0_music";
    private static final String NAV_ADDRESS = "bus1_nav";
    private static final String VOICE_ADDRESS = "bus3_voice";
    private static final String ALARM_ADDRESS = "bus11_alarm";
    private static final String ANNOUNCEMENT_ADDRESS = "bus12_announcement";
    private static final String CONFIG_1_ALL_ADDRESS = "bus100_all";

    private static final String TEST_ZONE_NAME = "Secondary zone";
    private static final String TEST_ZONE_CONFIG_NAME_0 = "Zone Config 0";
    private static final String TEST_ZONE_CONFIG_NAME_1 = "Zone Config 1";

    private static final int TEST_ZONE_ID = 1;
    private static final int TEST_ZONE_CONFIG_ID_0 = 0;
    private static final int TEST_ZONE_CONFIG_ID_1 = 1;
    private static final int TEST_MUSIC_GROUP_ID = 0;
    private static final int TEST_NAV_GROUP_ID = 1;
    private static final int TEST_VOICE_GROUP_ID = 2;
    private static final int TEST_OTHER_GROUP_ID = 0;
    private static final int TEST_PORT_ID_MUSIC =  0;
    private static final int TEST_PORT_ID_NAV = 1;
    private static final int TEST_GAIN_MIN_VALUE = -3000;
    private static final int TEST_GAIN_MAX_VALUE = -1000;
    private static final int TEST_GAIN_DEFAULT_VALUE = -2000;
    private static final int TEST_GAIN_STEP_VALUE = 2;


    private static final AudioAttributes TEST_MEDIA_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA);
    private static final AudioAttributes TEST_ALARM_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ALARM);
    private static final AudioAttributes TEST_ASSISTANT_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ASSISTANT);
    private static final AudioAttributes TEST_NAVIGATION_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);

    private static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                    /* useCoreAudioRouting= */ false);

    @Mock
    private CarVolumeGroup mMockMusicGroup0;
    @Mock
    private CarVolumeGroup mMockNavGroup0;
    @Mock
    private CarVolumeGroup mMockVoiceGroup0;
    @Mock
    private CarVolumeGroup mMockGroup1;

    @Mock
    private CarAudioZoneConfig mMockZoneConfig0;
    @Mock
    private CarAudioZoneConfig mMockZoneConfig1;

    private List<CarVolumeGroup> mZoneConfig0VolumeGroups;

    private CarAudioZone mTestAudioZone;

    @AudioContext
    private static final int TEST_MEDIA_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_MEDIA_ATTRIBUTE);
    @AudioContext
    private static final int TEST_ALARM_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ALARM_ATTRIBUTE);
    @AudioContext
    private static final int TEST_ASSISTANT_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ASSISTANT_ATTRIBUTE);
    @AudioContext
    private static final int TEST_NAVIGATION_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_NAVIGATION_ATTRIBUTE);
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void setUp() {
        mMockMusicGroup0 = new VolumeGroupBuilder()
                .addDeviceAddressAndContexts(TEST_MEDIA_CONTEXT, MUSIC_ADDRESS)
                .setGroupId(TEST_MUSIC_GROUP_ID).build();
        mMockNavGroup0 = new VolumeGroupBuilder()
                .addDeviceAddressAndContexts(TEST_NAVIGATION_CONTEXT, NAV_ADDRESS)
                .setGroupId(TEST_NAV_GROUP_ID).build();
        mMockVoiceGroup0 = new VolumeGroupBuilder()
                .addDeviceAddressAndContexts(TEST_ASSISTANT_CONTEXT, VOICE_ADDRESS)
                .setGroupId(TEST_VOICE_GROUP_ID).build();
        mMockGroup1 = new VolumeGroupBuilder()
                .addDeviceAddressAndContexts(TEST_MEDIA_CONTEXT, CONFIG_1_ALL_ADDRESS)
                .addDeviceAddressAndContexts(TEST_NAVIGATION_CONTEXT, CONFIG_1_ALL_ADDRESS)
                .addDeviceAddressAndContexts(TEST_ASSISTANT_CONTEXT, CONFIG_1_ALL_ADDRESS)
                .setGroupId(TEST_OTHER_GROUP_ID).build();

        mZoneConfig0VolumeGroups = List.of(mMockMusicGroup0, mMockNavGroup0, mMockVoiceGroup0);

        mMockZoneConfig0 = new TestCarAudioZoneConfigBuilder(TEST_ZONE_ID, TEST_ZONE_CONFIG_ID_0,
                TEST_ZONE_CONFIG_NAME_0).setIsDefault(true).addVolumeGroup(mMockMusicGroup0)
                .addVolumeGroup(mMockNavGroup0).addVolumeGroup(mMockVoiceGroup0).build();
        mMockZoneConfig1 = new TestCarAudioZoneConfigBuilder(TEST_ZONE_ID, TEST_ZONE_CONFIG_ID_1,
                TEST_ZONE_CONFIG_NAME_1).addVolumeGroup(mMockGroup1).build();
        mTestAudioZone = new CarAudioZone(TEST_CAR_AUDIO_CONTEXT, TEST_ZONE_NAME,
                TEST_ZONE_ID);
    }

    @Test
    public void init_allZoneConfigsInitialized() {
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);

        mTestAudioZone.init();

        expectWithMessage("Default configuration on init")
                .that(mTestAudioZone.getCurrentCarAudioZoneConfig()).isEqualTo(mMockZoneConfig0);
        verify(mMockZoneConfig0).synchronizeCurrentGainIndex();
        verify(mMockZoneConfig1).synchronizeCurrentGainIndex();
    }

    @Test
    public void getId() {
        expectWithMessage("Zone id").that(mTestAudioZone.getId()).isEqualTo(TEST_ZONE_ID);
    }

    @Test
    public void getName() {
        expectWithMessage("Zone name").that(mTestAudioZone.getName()).isEqualTo(TEST_ZONE_NAME);
    }

    @Test
    public void isPrimaryZone_forPrimaryZone_returnsTrue() {
        CarAudioZone primaryZone = new CarAudioZone(TEST_CAR_AUDIO_CONTEXT,
                /* name= */ "primary zone", CarAudioManager.PRIMARY_AUDIO_ZONE);

        expectWithMessage("Primary zone").that(primaryZone.isPrimaryZone()).isTrue();
    }

    @Test
    public void isPrimaryZone_forNonPrimaryZone_returnsFalse() {
        expectWithMessage("Non-primary zone").that(mTestAudioZone.isPrimaryZone()).isFalse();
    }

    @Test
    public void getCurrentCarAudioZoneConfig() {
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);

        expectWithMessage("Current zone configuration")
                .that(mTestAudioZone.getCurrentCarAudioZoneConfig()).isEqualTo(mMockZoneConfig0);
    }

    @Test
    public void getDefaultAudioZoneConfigInfo() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarAudioZoneConfigInfo defaultInfo = new CarAudioZoneConfigInfo(TEST_ZONE_CONFIG_NAME_0,
                TEST_ZONE_ID, TEST_ZONE_CONFIG_ID_0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);

        expectWithMessage("Default configuration")
                .that(mTestAudioZone.getDefaultAudioZoneConfigInfo()
                        .hasSameConfigInfo(defaultInfo)).isTrue();
    }

    @Test
    public void getDefaultAudioZoneConfigInfo_withNoDefaultConfig() {
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);

        expectWithMessage("Non existing default configuration")
                .that(mTestAudioZone.getDefaultAudioZoneConfigInfo()).isNull();
    }

    @Test
    public void getAllCarAudioZoneConfigs() {
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);

        expectWithMessage("All zone configurations")
                .that(mTestAudioZone.getAllCarAudioZoneConfigs())
                .containsExactly(mMockZoneConfig0, mMockZoneConfig1);
    }

    @Test
    public void isCurrentZoneConfig_forCurrentConfig_returnsTrue() {
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);
        CarAudioZoneConfigInfo currentZoneConfigInfo = mTestAudioZone
                .getCurrentCarAudioZoneConfig().getCarAudioZoneConfigInfo();

        expectWithMessage("Current zone config info")
                .that(mTestAudioZone.isCurrentZoneConfig(currentZoneConfigInfo))
                .isTrue();
    }

    @Test
    public void isCurrentZoneConfig_forCurrentConfig_returnsFalse() {
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);
        CarAudioZoneConfigInfo nonCurrentZoneConfigInfo = getFirstNonCurrentZoneConfigInfo();

        expectWithMessage("Non-current zone config info")
                .that(mTestAudioZone.isCurrentZoneConfig(nonCurrentZoneConfigInfo))
                .isFalse();
    }

    @Test
    public void setCurrentCarZoneConfig_withCoreAudioRoutingDisabled() {
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);
        CarAudioZoneConfigInfo currentZoneConfigInfoToSwitch = getFirstNonCurrentZoneConfigInfo();

        mTestAudioZone.setCurrentCarZoneConfig(currentZoneConfigInfoToSwitch);

        expectWithMessage("Current zone config info after switching zone configuration")
                .that(mTestAudioZone.isCurrentZoneConfig(currentZoneConfigInfoToSwitch))
                .isTrue();
        verify(mMockZoneConfig1).setIsSelected(true);
        verify(mMockZoneConfig1).updateVolumeDevices(/* useCoreAudioRouting= */ false);
        verify(mMockZoneConfig0).setIsSelected(false);
    }

    @Test
    public void setCurrentCarZoneConfig_withCoreAudioRoutingEnabled() {
        CarAudioContext contextWithCoreAudioRouting =
                new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                        /* useCoreAudioRouting= */ true);
        CarAudioZone testAudioZone = new CarAudioZone(contextWithCoreAudioRouting, TEST_ZONE_NAME,
                TEST_ZONE_ID);
        testAudioZone.addZoneConfig(mMockZoneConfig0);
        testAudioZone.addZoneConfig(mMockZoneConfig1);
        CarAudioZoneConfigInfo currentZoneConfigInfoToSwitch =
                getFirstNonCurrentZoneConfigInfo(testAudioZone);

        testAudioZone.setCurrentCarZoneConfig(currentZoneConfigInfoToSwitch);

        expectWithMessage("Current zone config info after switching zone configuration"
                + "with core audio routing")
                .that(testAudioZone.isCurrentZoneConfig(currentZoneConfigInfoToSwitch)).isTrue();
        verify(mMockZoneConfig1).setIsSelected(true);
        verify(mMockZoneConfig1).updateVolumeDevices(/* useCoreAudioRouting= */ true);
        verify(mMockZoneConfig0).setIsSelected(false);
    }

    @Test
    public void getCurrentVolumeGroup_withGroupId() {
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);
        int groupId = 1;

        expectWithMessage("Current volume group with id %s", groupId)
                .that(mTestAudioZone.getCurrentVolumeGroup(groupId)).isEqualTo(mMockNavGroup0);
    }

    @Test
    public void getCurrentVolumeGroup_withName() {
        String groupName0 = "Group Name 0";
        when(mMockZoneConfig0.getVolumeGroup(groupName0)).thenReturn(mMockVoiceGroup0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);

        expectWithMessage("Current volume group with name %s", groupName0)
                .that(mTestAudioZone.getCurrentVolumeGroup(groupName0))
                .isEqualTo(mMockVoiceGroup0);
    }

    @Test
    public void getCurrentVolumeGroupCount() {
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);

        expectWithMessage("Current volume group count")
                .that(mTestAudioZone.getCurrentVolumeGroupCount())
                .isEqualTo(mZoneConfig0VolumeGroups.size());
    }

    @Test
    public void getCurrentVolumeGroups() {
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);

        expectWithMessage("Current volume groups")
                .that(mTestAudioZone.getCurrentVolumeGroups()).asList()
                .containsExactlyElementsIn(mZoneConfig0VolumeGroups).inOrder();
    }

    @Test
    public void validateZoneConfigs_withValidConfigs_returnsTrue() {
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);

        expectWithMessage("Valid zone configurations")
                .that(mTestAudioZone.validateZoneConfigs(/* useCoreAudioRouting= */ false))
                .isTrue();
    }

    @Test
    public void validateZoneConfigs_withoutConfigs_returnsFalse() {
        expectWithMessage("Invalid zone without zone configurations")
                .that(mTestAudioZone.validateZoneConfigs(/* useCoreAudioRouting= */ false))
                .isFalse();
    }

    @Test
    public void validateZoneConfigs_withoutInvalidDefaultZoneConfigId_returnsFalse() {
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);

        expectWithMessage("Invalid zone with invalid default zone configuration id")
                .that(mTestAudioZone.validateZoneConfigs(/* useCoreAudioRouting= */ false))
                .isFalse();
    }

    @Test
    public void validateZoneConfigs_withWrongZoneIdInZoneConfigs_returnsFalse() {
        CarAudioZoneConfig zoneConfig2 = new TestCarAudioZoneConfigBuilder(TEST_ZONE_ID + 1,
                /* configId= */ 2, TEST_ZONE_CONFIG_NAME_1).build();
        mTestAudioZone.addZoneConfig(zoneConfig2);

        expectWithMessage("Invalid zone with wrong zone id in zone configurations")
                .that(mTestAudioZone.validateZoneConfigs(/* useCoreAudioRouting= */ false))
                .isFalse();
    }

    @Test
    public void validateZoneConfigs_withoutDefaultZoneConfig_returnsFalse() {
        CarAudioZoneConfig zoneConfig2 = new TestCarAudioZoneConfigBuilder(TEST_ZONE_ID,
                /* configId= */ 2, TEST_ZONE_CONFIG_NAME_1).build();
        mTestAudioZone.addZoneConfig(zoneConfig2);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);

        expectWithMessage("Invalid zone without default zone configuration")
                .that(mTestAudioZone.validateZoneConfigs(/* useCoreAudioRouting= */ false))
                .isFalse();
    }

    @Test
    public void validateZoneConfigs_withMultipleDefaultZoneConfigs_returnsFalse() {
        CarAudioZoneConfig zoneConfig2 = new TestCarAudioZoneConfigBuilder(TEST_ZONE_ID,
                /* configId= */ 2, TEST_ZONE_CONFIG_NAME_1).setIsDefault(true).build();
        mTestAudioZone.addZoneConfig(zoneConfig2);
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);

        expectWithMessage("Invalid zone with multiple default zone configurations")
                .that(mTestAudioZone.validateZoneConfigs(/* useCoreAudioRouting= */ false))
                .isFalse();
    }

    @Test
    public void validateZoneConfigs_withInvalidVolumeGroupsInZoneConfigs_returnsFalse() {
        boolean useCoreAudioRouting = true;
        CarAudioZoneConfig zoneConfig2 = new TestCarAudioZoneConfigBuilder(TEST_ZONE_ID,
                /* configId= */ 2, TEST_ZONE_CONFIG_NAME_1).build();
        when(zoneConfig2.validateVolumeGroups(any(), eq(useCoreAudioRouting))).thenReturn(false);
        mTestAudioZone.addZoneConfig(zoneConfig2);
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);

        expectWithMessage("Invalid zone with invalid volume groups in zone configurations")
                .that(mTestAudioZone.validateZoneConfigs(useCoreAudioRouting))
                .isFalse();
    }

    @Test
    public void validateCanUseDynamicMixRouting() {
        when(mMockZoneConfig0.validateCanUseDynamicMixRouting(/* useCoreAudioRouting= */ false))
                .thenReturn(true);
        when(mMockZoneConfig1.validateCanUseDynamicMixRouting(
                /* useCoreAudioRouting= */ false)).thenReturn(false);
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);

        expectWithMessage("Using dynamic mix routing validated")
                .that(mTestAudioZone.validateCanUseDynamicMixRouting(
                        /* useCoreAudioRouting= */ false)).isTrue();
    }

    @Test
    public void getAddressForContext_returnsExpectedDeviceAddress() {
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);

        String musicAddress = mTestAudioZone.getAddressForContext(TEST_MEDIA_CONTEXT);
        String navAddress = mTestAudioZone.getAddressForContext(TEST_NAVIGATION_CONTEXT);
        String voiceAddress = mTestAudioZone.getAddressForContext(TEST_ASSISTANT_CONTEXT);

        expectWithMessage("Music volume group address")
                .that(musicAddress).isEqualTo(MUSIC_ADDRESS);
        expectWithMessage("Navigation volume group address")
                .that(navAddress).matches(NAV_ADDRESS);
        expectWithMessage("Assistant volume group address")
                .that(voiceAddress).matches(VOICE_ADDRESS);
    }

    @Test
    public void getAddressForContext_throwsOnInvalidContext() {
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> mTestAudioZone.getAddressForContext(CarAudioContext.getInvalidContext()));

        expectWithMessage("Invalid context exception").that(thrown).hasMessageThat()
                .contains("is invalid");
    }

    @Test
    public void getAddressForContext_throwsOnNonExistentContext() {
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mTestAudioZone.getAddressForContext(TEST_ALARM_CONTEXT));

        expectWithMessage("Non-existing context exception").that(thrown).hasMessageThat()
                .contains("Could not find output device in zone");
    }

    @Test
    public void getInputDevices() {
        AudioDeviceAttributes mockInputDevice1 = mock(AudioDeviceAttributes.class);
        AudioDeviceAttributes mockInputDevice2 = mock(AudioDeviceAttributes.class);
        mTestAudioZone.addInputAudioDevice(mockInputDevice1);
        mTestAudioZone.addInputAudioDevice(mockInputDevice2);

        expectWithMessage("Input devices").that(mTestAudioZone.getInputAudioDevices())
                .containsExactly(mockInputDevice1, mockInputDevice2);
    }

    @Test
    public void findActiveAudioAttributesFromPlaybackConfigurations_withNullConfig_fails() {
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);

        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> mTestAudioZone.findActiveAudioAttributesFromPlaybackConfigurations(
                        /* configurations= */ null));

        expectWithMessage("Null playback configuration exception").that(thrown)
                .hasMessageThat().contains("Audio playback configurations can not be null");
    }

    @Test
    public void findActiveAudioAttributesFromPlaybackConfigurations_returnsAllActiveAttributes() {
        AudioPlaybackConfiguration mediaConfiguration = new AudioPlaybackConfigurationBuilder()
                .setUsage(USAGE_MEDIA).setDeviceAddress(MUSIC_ADDRESS).build();
        AudioPlaybackConfiguration navConfiguration = new AudioPlaybackConfigurationBuilder()
                .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setDeviceAddress(NAV_ADDRESS)
                .build();
        List<AudioPlaybackConfiguration> activeConfigurations = List.of(mediaConfiguration,
                navConfiguration);
        when(mMockZoneConfig0.isAudioDeviceInfoValidForZone(mediaConfiguration
                .getAudioDeviceInfo())).thenReturn(true);
        when(mMockZoneConfig0.isAudioDeviceInfoValidForZone(navConfiguration
                .getAudioDeviceInfo())).thenReturn(true);
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);

        List<AudioAttributes> activeAttributes = mTestAudioZone
                .findActiveAudioAttributesFromPlaybackConfigurations(activeConfigurations);

        expectWithMessage("Active playback audio attributes").that(activeAttributes)
                .containsExactly(TEST_MEDIA_ATTRIBUTE, TEST_NAVIGATION_ATTRIBUTE);
    }

    @Test
    public void findActiveAudioAttributesFromPlaybackConfigurations_returnsNoMatchingAttributes() {
        AudioPlaybackConfiguration assistantConfiguration = new AudioPlaybackConfigurationBuilder()
                .setUsage(USAGE_ASSISTANT).setDeviceAddress(ANNOUNCEMENT_ADDRESS).build();
        AudioPlaybackConfiguration alarmConfiguration = new AudioPlaybackConfigurationBuilder()
                .setUsage(USAGE_ALARM).setDeviceAddress(ALARM_ADDRESS).build();
        List<AudioPlaybackConfiguration> activeConfigurations = List.of(assistantConfiguration,
                alarmConfiguration);
        when(mMockZoneConfig0.isAudioDeviceInfoValidForZone(assistantConfiguration
                .getAudioDeviceInfo())).thenReturn(false);
        when(mMockZoneConfig0.isAudioDeviceInfoValidForZone(alarmConfiguration
                .getAudioDeviceInfo())).thenReturn(false);
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);

        List<AudioAttributes> activeAttributes = mTestAudioZone
                .findActiveAudioAttributesFromPlaybackConfigurations(activeConfigurations);

        expectWithMessage("Non matching active playback audio attributes")
                .that(activeAttributes).isEmpty();
    }

    @Test
    public void findActiveAudioAttributesFromPlaybackConfigurations_withMultipleZoneConfigs() {
        AudioPlaybackConfiguration mediaConfiguration = new AudioPlaybackConfigurationBuilder()
                .setUsage(USAGE_MEDIA).setDeviceAddress(CONFIG_1_ALL_ADDRESS).build();
        AudioPlaybackConfiguration navConfiguration = new AudioPlaybackConfigurationBuilder()
                .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setDeviceAddress(NAV_ADDRESS)
                .build();
        List<AudioPlaybackConfiguration> activeConfigurations = List.of(mediaConfiguration,
                navConfiguration);
        when(mMockZoneConfig0.isAudioDeviceInfoValidForZone(mediaConfiguration
                .getAudioDeviceInfo())).thenReturn(false);
        when(mMockZoneConfig0.isAudioDeviceInfoValidForZone(navConfiguration
                .getAudioDeviceInfo())).thenReturn(true);
        when(mMockZoneConfig1.isAudioDeviceInfoValidForZone(mediaConfiguration
                .getAudioDeviceInfo())).thenReturn(true);
        when(mMockZoneConfig1.isAudioDeviceInfoValidForZone(navConfiguration
                .getAudioDeviceInfo())).thenReturn(false);
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);

        List<AudioAttributes> activeAttributes = mTestAudioZone
                .findActiveAudioAttributesFromPlaybackConfigurations(activeConfigurations);

        expectWithMessage("Matching active playback audio attributes for current zone config")
                .that(activeAttributes).containsExactly(TEST_NAVIGATION_ATTRIBUTE);
    }

    @Test
    public void
            findActiveAudioAttributesFromPlaybackConfigurations_onEmptyConfigurations_retEmpty() {
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);

        List<AudioAttributes> activeAttributes = mTestAudioZone
                .findActiveAudioAttributesFromPlaybackConfigurations(Collections.emptyList());

        expectWithMessage("Empty active playback audio attributes")
                .that(activeAttributes).isEmpty();
    }

    @Test
    public void getCurrentAudioDeviceInfos() {
        AudioDeviceAttributes audioDevice0 = mock(AudioDeviceAttributes.class);
        AudioDeviceAttributes audioDevice1 = mock(AudioDeviceAttributes.class);
        when(mMockZoneConfig0.getAudioDevice()).thenReturn(List.of(audioDevice0));
        when(mMockZoneConfig1.getAudioDevice()).thenReturn(List.of(audioDevice1));
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);

        expectWithMessage("Current device infos")
                .that(mTestAudioZone.getCurrentAudioDevices())
                .containsExactly(audioDevice0);
    }

    @Test
    public void getCurrentAudioDeviceInfosSupportingDynamicMix() {
        AudioDeviceAttributes audioDevice0 = mock(AudioDeviceAttributes.class);
        AudioDeviceAttributes audioDevice1 = mock(AudioDeviceAttributes.class);
        when(mMockZoneConfig0.getAudioDeviceSupportingDynamicMix())
                .thenReturn(List.of(audioDevice0));
        when(mMockZoneConfig1.getAudioDeviceSupportingDynamicMix())
                .thenReturn(List.of(audioDevice1));
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);

        expectWithMessage("Current device infos supporting dynamic mix")
                .that(mTestAudioZone.getCurrentAudioDeviceSupportingDynamicMix())
                .containsExactly(audioDevice0);
    }

    @Test
    public void isAudioDeviceInfoValidForZone() {
        AudioDeviceInfo audioDeviceInfo = mock(AudioDeviceInfo.class);
        when(mMockZoneConfig0.isAudioDeviceInfoValidForZone(audioDeviceInfo)).thenReturn(false);
        when(mMockZoneConfig1.isAudioDeviceInfoValidForZone(audioDeviceInfo)).thenReturn(true);
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);

        expectWithMessage("Invalid device for current zone configuration")
                .that(mTestAudioZone.isAudioDeviceInfoValidForZone(audioDeviceInfo)).isFalse();
    }

    @Test
    public void getVolumeGroupForAudioAttributes() {
        when(mMockZoneConfig0.getVolumeGroupForAudioAttributes(TEST_MEDIA_ATTRIBUTE))
                .thenReturn(mMockMusicGroup0);
        when(mMockZoneConfig1.getVolumeGroupForAudioAttributes(TEST_MEDIA_ATTRIBUTE))
                .thenReturn(null);
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);

        expectWithMessage("Audio attributes in car audio zone")
                .that(mTestAudioZone.getVolumeGroupForAudioAttributes(TEST_MEDIA_ATTRIBUTE))
                .isEqualTo(mMockMusicGroup0);
    }

    @Test
    public void getVolumeGroupForAudioAttributes_withAttributeNotFound() {
        when(mMockZoneConfig0.getVolumeGroupForAudioAttributes(TEST_MEDIA_ATTRIBUTE))
                .thenReturn(null);
        when(mMockZoneConfig1.getVolumeGroupForAudioAttributes(TEST_MEDIA_ATTRIBUTE))
                .thenReturn(mMockGroup1);
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);

        expectWithMessage("Audio attributes not in car audio zone")
                .that(mTestAudioZone.getVolumeGroupForAudioAttributes(TEST_MEDIA_ATTRIBUTE))
                .isNull();
    }

    @Test
    public void onAudioGainChanged_withDeviceAddressesInZone() {
        List<Integer> reasons = List.of(Reasons.REMOTE_MUTE, Reasons.NAV_DUCKING);
        AudioGainConfigInfo musicGainInfo = new AudioGainConfigInfo();
        musicGainInfo.zoneId = TEST_ZONE_ID;
        musicGainInfo.devicePortAddress = CONFIG_1_ALL_ADDRESS;
        musicGainInfo.volumeIndex = 666;
        CarAudioGainConfigInfo carMusicGainInfo = new CarAudioGainConfigInfo(musicGainInfo);
        AudioGainConfigInfo navGainInfo = new AudioGainConfigInfo();
        navGainInfo.zoneId = TEST_ZONE_ID;
        navGainInfo.devicePortAddress = NAV_ADDRESS;
        navGainInfo.volumeIndex = 999;
        CarAudioGainConfigInfo carNavGainInfo = new CarAudioGainConfigInfo(navGainInfo);
        List<CarAudioGainConfigInfo> carGains = List.of(carMusicGainInfo, carNavGainInfo);
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);

        mTestAudioZone.onAudioGainChanged(reasons, carGains);

        verify(mMockMusicGroup0, never()).onAudioGainChanged(any(), any());
        verify(mMockNavGroup0).onAudioGainChanged(any(), any());
        verify(mMockVoiceGroup0, never()).onAudioGainChanged(any(), any());
        verify(mMockGroup1).onAudioGainChanged(any(), any());
    }

    @Test
    public void onAudioGainChanged_withMultipleGainInfoForSameGroup_createsNoDuplicateEvents() {
        List<Integer> reasons = List.of(Reasons.NAV_DUCKING);
        AudioGainConfigInfo musicGainInfo = new AudioGainConfigInfo();
        musicGainInfo.zoneId = TEST_ZONE_ID;
        musicGainInfo.devicePortAddress = MUSIC_ADDRESS;
        musicGainInfo.volumeIndex = 666;
        CarAudioGainConfigInfo carMusicGainInfo = new CarAudioGainConfigInfo(musicGainInfo);
        AudioGainConfigInfo musicGainInfoDup = new AudioGainConfigInfo();
        musicGainInfoDup.zoneId = TEST_ZONE_ID;
        musicGainInfoDup.devicePortAddress = MUSIC_ADDRESS;
        musicGainInfoDup.volumeIndex = 999;
        CarAudioGainConfigInfo carMusicGainInfoDup = new CarAudioGainConfigInfo(musicGainInfoDup);
        List<CarAudioGainConfigInfo> carGains = List.of(carMusicGainInfo, carMusicGainInfoDup);
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);

        expectWithMessage("On audio gain changed for multiple gain info for same vol group")
                .that(mTestAudioZone.onAudioGainChanged(reasons, carGains).size()).isEqualTo(1);
    }

    @Test
    public void onAudioGainChanged_withoutAnyDeviceAddressInZone() {
        List<Integer> reasons = List.of(Reasons.REMOTE_MUTE, Reasons.NAV_DUCKING);
        AudioGainConfigInfo navGainInfo = new AudioGainConfigInfo();
        navGainInfo.zoneId = TEST_ZONE_ID;
        navGainInfo.devicePortAddress = NAV_ADDRESS;
        navGainInfo.volumeIndex = 999;
        CarAudioGainConfigInfo carNavGainInfo = new CarAudioGainConfigInfo(navGainInfo);
        CarAudioZoneConfig zoneConfig = new TestCarAudioZoneConfigBuilder(TEST_ZONE_ID,
                TEST_ZONE_CONFIG_ID_0, "zone config test").setIsDefault(true)
                .addVolumeGroup(mMockMusicGroup0).build();
        mTestAudioZone.addZoneConfig(zoneConfig);

        mTestAudioZone.onAudioGainChanged(reasons, List.of(carNavGainInfo));

        verify(mMockMusicGroup0, never()).onAudioGainChanged(any(), any());
        verify(mMockNavGroup0, never()).onAudioGainChanged(any(), any());
        verify(mMockVoiceGroup0, never()).onAudioGainChanged(any(), any());
    }

    @Test
    public void onAudioPortsChanged_withDeviceAddressInZone() {
        HalAudioDeviceInfo musicDeviceInfo = new HalAudioDeviceInfoBuilder()
                .setId(TEST_PORT_ID_MUSIC).setAddress(MUSIC_ADDRESS).build();
        HalAudioDeviceInfo navDeviceInfo = new HalAudioDeviceInfoBuilder().setId(TEST_PORT_ID_NAV)
                .setAddress(NAV_ADDRESS).build();
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);
        List<HalAudioDeviceInfo> deviceInfoList = List.of(musicDeviceInfo, navDeviceInfo);

        expectWithMessage("Car volume group events list size")
                .that(mTestAudioZone.onAudioPortsChanged(deviceInfoList))
                .hasSize(deviceInfoList.size());
        verify(mMockMusicGroup0).updateAudioDeviceInfo(eq(musicDeviceInfo));
        verify(mMockNavGroup0).updateAudioDeviceInfo(eq(navDeviceInfo));
        verify(mMockVoiceGroup0, never()).updateAudioDeviceInfo(any());
        verify(mMockMusicGroup0).calculateNewGainStageFromDeviceInfos();
        verify(mMockNavGroup0).calculateNewGainStageFromDeviceInfos();
        verify(mMockVoiceGroup0, never()).calculateNewGainStageFromDeviceInfos();
    }

    @Test
    public void onAudioPortsChanged_withMultipledeviceInfoForSameGroup_createsNoDuplicateEvents() {
        HalAudioDeviceInfo musicDeviceInfo = new HalAudioDeviceInfoBuilder()
                .setId(TEST_PORT_ID_MUSIC).setAddress(MUSIC_ADDRESS)
                .setMinValue(TEST_GAIN_MIN_VALUE).setMaxValue(TEST_GAIN_MAX_VALUE)
                .setDefaultValue(TEST_GAIN_DEFAULT_VALUE).setStepValue(TEST_GAIN_STEP_VALUE)
                .build();
        HalAudioDeviceInfo musicDeviceInfoDup = new HalAudioDeviceInfoBuilder()
                .setId(TEST_PORT_ID_MUSIC).setAddress(MUSIC_ADDRESS)
                .setMinValue(TEST_GAIN_MIN_VALUE).setMaxValue(TEST_GAIN_MAX_VALUE)
                .setDefaultValue(TEST_GAIN_DEFAULT_VALUE).setStepValue(TEST_GAIN_STEP_VALUE)
                .build();
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);
        List<HalAudioDeviceInfo> deviceInfos = List.of(musicDeviceInfo, musicDeviceInfoDup);

        expectWithMessage("Car volume group events list size for duplicate audio device info")
                .that(mTestAudioZone.onAudioPortsChanged(deviceInfos)).hasSize(1);
    }

    @Test
    public void onAudioPortsChanged_withoutAnyDeviceAddressInZone() {
        HalAudioDeviceInfo navDeviceInfo = new HalAudioDeviceInfoBuilder().setId(TEST_PORT_ID_NAV)
                .setAddress(NAV_ADDRESS).build();
        CarAudioZoneConfig zoneConfig = new TestCarAudioZoneConfigBuilder(TEST_ZONE_ID,
                TEST_ZONE_CONFIG_ID_0, "zone config test").setIsDefault(true)
                .addVolumeGroup(mMockMusicGroup0).build();
        mTestAudioZone.addZoneConfig(zoneConfig);

        expectWithMessage("Car volume group events list size")
                .that(mTestAudioZone.onAudioPortsChanged(List.of(navDeviceInfo))).isEmpty();
        verify(mMockMusicGroup0, never()).updateAudioDeviceInfo(any());
        verify(mMockNavGroup0, never()).updateAudioDeviceInfo(any());
        verify(mMockVoiceGroup0, never()).updateAudioDeviceInfo(any());
        verify(mMockMusicGroup0, never()).calculateNewGainStageFromDeviceInfos();
        verify(mMockNavGroup0, never()).calculateNewGainStageFromDeviceInfos();
        verify(mMockVoiceGroup0, never()).calculateNewGainStageFromDeviceInfos();
    }

    @Test
    public void onAudioPortsChanged_forMultipleZoneConfig_generateEventsForActiveZoneConfig() {
        HalAudioDeviceInfo musicDeviceInfo = new HalAudioDeviceInfoBuilder()
                .setId(TEST_PORT_ID_MUSIC).setAddress(MUSIC_ADDRESS).build();
        HalAudioDeviceInfo configDeviceInfo = new HalAudioDeviceInfoBuilder()
                .setId(TEST_PORT_ID_NAV).setAddress(CONFIG_1_ALL_ADDRESS).build();
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        mTestAudioZone.addZoneConfig(mMockZoneConfig1);
        List<HalAudioDeviceInfo> deviceInfos = List.of(musicDeviceInfo, configDeviceInfo);

        expectWithMessage("Car volume group events list size for active zone config")
                .that(mTestAudioZone.onAudioPortsChanged(deviceInfos)).hasSize(1);
    }

    @Test
    public void getCarAudioContext() {
        expectWithMessage("Audio context in audio zone")
                .that(mTestAudioZone.getCarAudioContext()).isEqualTo(TEST_CAR_AUDIO_CONTEXT);
    }

    @Test
    public void audioDevicesAdded_withConfigUpdated() {
        when(mMockZoneConfig0.audioDevicesAdded(any())).thenReturn(true);
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        AudioDeviceInfo testDevice = mock(AudioDeviceInfo.class);
        List<AudioDeviceInfo> infos = List.of(testDevice);

        boolean updated = mTestAudioZone.audioDevicesAdded(infos);

        expectWithMessage("Added audio devices updated status").that(updated).isTrue();
        verify(mMockZoneConfig0).audioDevicesAdded(infos);
    }

    @Test
    public void audioDevicesAdded_withConfigNotUpdated() {
        when(mMockZoneConfig0.audioDevicesAdded(any())).thenReturn(false);
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        AudioDeviceInfo testDevice = mock(AudioDeviceInfo.class);
        List<AudioDeviceInfo> infos = List.of(testDevice);

        boolean updated = mTestAudioZone.audioDevicesAdded(infos);

        expectWithMessage("Added audio devices not updated status").that(updated).isFalse();
        verify(mMockZoneConfig0).audioDevicesAdded(infos);
    }

    @Test
    public void audioDevicesAdded_withNullDevices() {
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);

        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> mTestAudioZone.audioDevicesAdded(/* devices= */ null));

        expectWithMessage("Audio devices added null devices exception").that(thrown)
                .hasMessageThat().contains("Audio devices");
    }

    @Test
    public void audioDevicesRemoved_withConfigUpdated() {
        when(mMockZoneConfig0.audioDevicesRemoved(any())).thenReturn(true);
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        AudioDeviceInfo testDevice = mock(AudioDeviceInfo.class);
        List<AudioDeviceInfo> infos = List.of(testDevice);

        boolean updated = mTestAudioZone.audioDevicesRemoved(infos);

        expectWithMessage("Removed audio devices updated status").that(updated).isTrue();
        verify(mMockZoneConfig0).audioDevicesRemoved(infos);
    }

    @Test
    public void audioDevicesRemoved_withConfigNotUpdated() {
        when(mMockZoneConfig0.audioDevicesRemoved(any())).thenReturn(false);
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);
        AudioDeviceInfo testDevice = mock(AudioDeviceInfo.class);
        List<AudioDeviceInfo> infos = List.of(testDevice);

        boolean updated = mTestAudioZone.audioDevicesRemoved(infos);

        expectWithMessage("Removed audio devices not updated status").that(updated).isFalse();
        verify(mMockZoneConfig0).audioDevicesRemoved(infos);
    }

    @Test
    public void audioDevicesRemoved_withNullDevices() {
        mTestAudioZone.addZoneConfig(mMockZoneConfig0);

        NullPointerException thrown = assertThrows(NullPointerException.class,
                        () -> mTestAudioZone.audioDevicesRemoved(/* devices= */ null));

        expectWithMessage("Audio devices removed null devices exception").that(thrown)
                .hasMessageThat().contains("Audio devices");
    }

    private CarAudioZoneConfigInfo getFirstNonCurrentZoneConfigInfo(CarAudioZone audioZone) {
        CarAudioZoneConfigInfo currentZoneConfigInfo = audioZone.getCurrentCarAudioZoneConfig()
                .getCarAudioZoneConfigInfo();
        List<CarAudioZoneConfigInfo> zoneConfigInfoList = audioZone.getCarAudioZoneConfigInfos();
        for (int index = 0; index < zoneConfigInfoList.size(); index++) {
            CarAudioZoneConfigInfo zoneConfigInfo = zoneConfigInfoList.get(index);
            if (!currentZoneConfigInfo.equals(zoneConfigInfo)) {
                return zoneConfigInfo;
            }
        }
        return null;
    }

    private CarAudioZoneConfigInfo getFirstNonCurrentZoneConfigInfo() {
        return getFirstNonCurrentZoneConfigInfo(mTestAudioZone);
    }

    private static final class TestCarAudioZoneConfigBuilder {
        private static final int INVALID_GROUP_ID = -1;
        private static final int INVALID_EVENT_TYPE = 0;

        private List<CarVolumeGroup> mCarVolumeGroups = new ArrayList<>();
        int mConfigId;
        int mZoneId;
        String mName;
        boolean mIsDefault;
        private final Map<String, Integer> mDeviceAddressToGroupId = new ArrayMap<>();

        TestCarAudioZoneConfigBuilder(int zoneId, int configId, String name) {
            mZoneId = zoneId;
            mConfigId = configId;
            mName = name;
        }

        TestCarAudioZoneConfigBuilder setIsDefault(boolean isDefault) {
            mIsDefault = isDefault;
            return this;
        }

        TestCarAudioZoneConfigBuilder addVolumeGroup(CarVolumeGroup volumeGroup) {
            mCarVolumeGroups.add(volumeGroup);
            addGroupAddressesToMap(volumeGroup.getAddresses(), volumeGroup.getId());
            return this;
        }

        CarAudioZoneConfig build() {
            CarAudioZoneConfig zoneConfig = mock(CarAudioZoneConfig.class);
            when(zoneConfig.getZoneConfigId()).thenReturn(mConfigId);
            when(zoneConfig.getZoneId()).thenReturn(mZoneId);
            when(zoneConfig.getName()).thenReturn(mName);
            when(zoneConfig.isDefault()).thenReturn(mIsDefault);
            for (int groupIndex = 0; groupIndex < mCarVolumeGroups.size(); groupIndex++) {
                when(zoneConfig.getVolumeGroup(groupIndex))
                        .thenReturn(mCarVolumeGroups.get(groupIndex));
            }
            when(zoneConfig.getVolumeGroupCount()).thenReturn(mCarVolumeGroups.size());
            when(zoneConfig.getVolumeGroups())
                    .thenReturn(mCarVolumeGroups.toArray(new CarVolumeGroup[0]));
            when(zoneConfig.validateVolumeGroups(eq(TEST_CAR_AUDIO_CONTEXT), anyBoolean()))
                    .thenReturn(true);
            when(zoneConfig.getCarAudioZoneConfigInfo())
                    .thenReturn(new CarAudioZoneConfigInfo(mName, mZoneId, mConfigId));

            doAnswer(invocation -> {
                List<Integer> halReasons = (List<Integer>) invocation.getArguments()[0];
                List<CarAudioGainConfigInfo> gainInfos =
                        (List<CarAudioGainConfigInfo>) invocation.getArguments()[1];
                SparseIntArray groupIdsToEventType = new SparseIntArray();
                List<Integer> extraInfos =
                        CarAudioGainMonitor.convertReasonsToExtraInfo(halReasons);

                for (int index = 0; index < gainInfos.size(); index++) {
                    CarAudioGainConfigInfo gainInfo = gainInfos.get(index);
                    int groupId = mDeviceAddressToGroupId.getOrDefault(gainInfo.getDeviceAddress(),
                            INVALID_GROUP_ID);
                    if (groupId == INVALID_GROUP_ID) {
                        continue;
                    }
                    int eventType = mCarVolumeGroups.get(groupId).onAudioGainChanged(halReasons,
                            gainInfo);
                    if (groupIdsToEventType.get(groupId, INVALID_GROUP_ID) != INVALID_GROUP_ID) {
                        eventType |= groupIdsToEventType.get(groupId);
                    }
                    groupIdsToEventType.put(groupId, eventType);
                }

                List<CarVolumeGroupEvent> events = new ArrayList<>();
                for (int index = 0; index < groupIdsToEventType.size(); index++) {
                    CarVolumeGroupEvent.Builder eventBuilder =
                            new CarVolumeGroupEvent.Builder(List.of(mCarVolumeGroups.get(
                                    groupIdsToEventType.keyAt(index)).getCarVolumeGroupInfo()),
                                    groupIdsToEventType.valueAt(index));
                    if (!extraInfos.isEmpty()) {
                        eventBuilder.setExtraInfos(extraInfos);
                    }
                    events.add(eventBuilder.build());
                }
                return events;
            }).when(zoneConfig).onAudioGainChanged(anyList(), anyList());

            doAnswer(invocation -> {
                List<HalAudioDeviceInfo> deviceInfos =
                        (List<HalAudioDeviceInfo>) invocation.getArguments()[0];
                List<CarVolumeGroupEvent> events = new ArrayList<>();
                ArraySet<Integer> updatedGroupIds = new ArraySet<>();

                for (int index = 0; index < deviceInfos.size(); index++) {
                    HalAudioDeviceInfo deviceInfo = deviceInfos.get(index);
                    int groupId = mDeviceAddressToGroupId.getOrDefault(deviceInfo.getAddress(),
                            INVALID_GROUP_ID);
                    if (groupId == INVALID_GROUP_ID) {
                        continue;
                    }
                    mCarVolumeGroups.get(groupId).updateAudioDeviceInfo(deviceInfo);
                    updatedGroupIds.add(groupId);
                }

                for (int index = 0; index < updatedGroupIds.size(); index++) {
                    CarVolumeGroup group = mCarVolumeGroups.get(updatedGroupIds.valueAt(index));
                    int eventType = group.calculateNewGainStageFromDeviceInfos();
                    if (eventType != INVALID_EVENT_TYPE) {
                        events.add(new CarVolumeGroupEvent.Builder(
                                List.of(group.getCarVolumeGroupInfo()), eventType,
                                List.of(EXTRA_INFO_VOLUME_INDEX_CHANGED_BY_AUDIO_SYSTEM)).build());
                    }
                }
                return events;
            }).when(zoneConfig).onAudioPortsChanged(anyList());
            return zoneConfig;
        }

        private void addGroupAddressesToMap(List<String> addresses, int groupId) {
            for (int index = 0; index < addresses.size(); index++) {
                mDeviceAddressToGroupId.put(addresses.get(index), groupId);
            }
        }

    }
}
