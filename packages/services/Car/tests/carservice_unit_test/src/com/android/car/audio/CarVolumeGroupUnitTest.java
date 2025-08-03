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

import static android.car.media.CarVolumeGroupEvent.EVENT_TYPE_ATTENUATION_CHANGED;
import static android.car.media.CarVolumeGroupEvent.EVENT_TYPE_MUTE_CHANGED;
import static android.car.media.CarVolumeGroupEvent.EVENT_TYPE_VOLUME_BLOCKED_CHANGED;
import static android.car.media.CarVolumeGroupEvent.EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED;
import static android.media.AudioAttributes.USAGE_ALARM;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_CALL_ASSISTANT;
import static android.media.AudioAttributes.USAGE_EMERGENCY;
import static android.media.AudioAttributes.USAGE_GAME;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_NOTIFICATION;
import static android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT;
import static android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE;
import static android.media.AudioAttributes.USAGE_UNKNOWN;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING;
import static android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
import static android.media.AudioDeviceInfo.TYPE_BUS;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.annotation.UserIdInt;
import android.car.feature.Flags;
import android.car.media.CarVolumeGroupInfo;
import android.car.test.AbstractExpectableTestCase;
import android.hardware.automotive.audiocontrol.AudioGainConfigInfo;
import android.hardware.automotive.audiocontrol.Reasons;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioManager;
import android.os.UserHandle;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.ArraySet;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class CarVolumeGroupUnitTest extends AbstractExpectableTestCase {
    private static final int ZONE_ID = 0;
    private static final int CONFIG_ID = 1;
    private static final int GROUP_ID = 0;
    private static final int DEFAULT_GAIN_INDEX = (TestCarAudioDeviceInfoBuilder.DEFAULT_GAIN
            - TestCarAudioDeviceInfoBuilder.MIN_GAIN) / TestCarAudioDeviceInfoBuilder.STEP_VALUE;
    private static final int MIN_GAIN_INDEX = 0;
    private static final int MAX_GAIN_INDEX = (TestCarAudioDeviceInfoBuilder.MAX_GAIN
            - TestCarAudioDeviceInfoBuilder.MIN_GAIN) / TestCarAudioDeviceInfoBuilder.STEP_VALUE;
    private static final int MIN_ACTIVATION_GAIN_INDEX_PERCENTAGE = 20;
    private static final int MAX_ACTIVATION_GAIN_INDEX_PERCENTAGE = 80;
    private static final int MIN_ACTIVATION_GAIN_INDEX = MIN_GAIN_INDEX + (int) Math.round(
            MIN_ACTIVATION_GAIN_INDEX_PERCENTAGE / 100.0 * (MAX_GAIN_INDEX - MIN_GAIN_INDEX));
    private static final int MAX_ACTIVATION_GAIN_INDEX = MIN_GAIN_INDEX + (int) Math.round(
            MAX_ACTIVATION_GAIN_INDEX_PERCENTAGE / 100.0 * (MAX_GAIN_INDEX - MIN_GAIN_INDEX));
    private static final int TEST_GAIN_INDEX = 2;
    private static final int TEST_USER_10 = 10;
    private static final int TEST_USER_11 = 11;
    private static final String GROUP_NAME = "group_0";
    private static final String MEDIA_DEVICE_ADDRESS = "music";
    private static final String NAVIGATION_DEVICE_ADDRESS = "navigation";
    private static final String OTHER_ADDRESS = "other_address";
    private static final int EVENT_TYPE_NONE = 0;

    private static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                    /* useCoreAudioRouting= */ false);

    private static final @CarAudioContext.AudioContext int TEST_MEDIA_CONTEXT_ID =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
    private static final @CarAudioContext.AudioContext int TEST_ALARM_CONTEXT_ID =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_ALARM));
    private static final @CarAudioContext.AudioContext int TEST_CALL_CONTEXT_ID =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_VOICE_COMMUNICATION));
    private static final @CarAudioContext.AudioContext int TEST_CALL_RING_CONTEXT_ID =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION_RINGTONE));
    private static final @CarAudioContext.AudioContext int TEST_EMERGENCY_CONTEXT_ID =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_EMERGENCY));
    private static final @CarAudioContext.AudioContext int TEST_NAVIGATION_CONTEXT_ID =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE));
    private static final @CarAudioContext.AudioContext int TEST_NOTIFICATION_CONTEXT_ID =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_NOTIFICATION));

    private CarAudioDeviceInfo mMediaDeviceInfo;
    private CarAudioDeviceInfo mNavigationDeviceInfo;
    private CarAudioDeviceInfo mInactiveMediaDeviceInfo;

    @Mock
    CarAudioSettings mSettingsMock;
    @Mock
    AudioManager mAudioManagerMock;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void setUp() {
        mMediaDeviceInfo = new TestCarAudioDeviceInfoBuilder(MEDIA_DEVICE_ADDRESS).build();
        mNavigationDeviceInfo = new TestCarAudioDeviceInfoBuilder(NAVIGATION_DEVICE_ADDRESS)
                .build();
        mInactiveMediaDeviceInfo = new TestCarAudioDeviceInfoBuilder(MEDIA_DEVICE_ADDRESS)
                .setIsActive(false).setType(TYPE_BLUETOOTH_A2DP).build();
    }

    @Test
    public void getAddressForContext_withSupportedContext_returnsAddress() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();

        expectWithMessage("Supported context's address")
                .that(carVolumeGroup.getAddressForContext(TEST_MEDIA_CONTEXT_ID))
                .isEqualTo(mMediaDeviceInfo.getAddress());
    }

    @Test
    public void getAddressForContext_withUnsupportedContext_returnsNull() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();

        expectWithMessage("Unsupported context's address")
                .that(carVolumeGroup.getAddressForContext(
                        TEST_NAVIGATION_CONTEXT_ID)).isNull();
    }

    @Test
    public void setMuted_whenUnmuted_onActivation_returnsTrue() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();

        expectWithMessage("Status returned from set mute while unmuted")
                .that(carVolumeGroup.setMute(true)).isTrue();
    }

    @Test
    public void setMuted_whenUnmuted_onDeactivation_returnsFalse() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();

        expectWithMessage("Status returned from set unmute while unmuted")
                .that(carVolumeGroup.setMute(false)).isFalse();
    }


    @Test
    public void setMuted_whenMuted_onDeactivation_returnsTrue() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setMute(true);

        expectWithMessage("Status returned from set unmute while muted")
                .that(carVolumeGroup.setMute(false)).isTrue();
    }

    @Test
    public void setMuted_whenMuted_onActivation_returnsFalse() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setMute(true);

        expectWithMessage("Status returned from set mute while muted")
                .that(carVolumeGroup.setMute(true)).isFalse();
    }

    @Test
    public void setMuted_whenHalMuted_onActivation_returnsTrue() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setCurrentGainIndex(DEFAULT_GAIN_INDEX);
        List<Integer> muteReasons = List.of(Reasons.TCU_MUTE);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = MIN_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(muteReasons, musicCarGain);

        expectWithMessage("Status returned from set mute while HAL muted")
                .that(carVolumeGroup.setMute(true)).isTrue();
    }

    @Test
    public void setMuted_whenHalMuted_onDeactivation_returnsFalse() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setCurrentGainIndex(DEFAULT_GAIN_INDEX);
        List<Integer> muteReasons = List.of(Reasons.TCU_MUTE);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = MIN_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(muteReasons, musicCarGain);

        expectWithMessage("Status returned from set unmute while HAL muted")
                .that(carVolumeGroup.setMute(false)).isFalse();
    }

    @Test
    public void isMuted_whenDefault_returnsFalse() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();

        expectWithMessage("Default mute state")
                .that(carVolumeGroup.isMuted()).isFalse();
    }

    @Test
    public void isMuted_afterMuting_returnsTrue() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();

        carVolumeGroup.setMute(true);

        expectWithMessage("Get mute state").that(carVolumeGroup.isMuted()).isTrue();
    }

    @Test
    public void isMuted_afterUnMuting_returnsFalse() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();

        carVolumeGroup.setMute(false);

        expectWithMessage("Set mute state")
                .that(carVolumeGroup.isMuted()).isFalse();
    }

    @Test
    public void setMute_withMutedState_storesValueToSetting() {
        CarAudioSettings settings = new SettingsBuilder(ZONE_ID, CONFIG_ID, GROUP_ID)
                .setMuteForUser10(false).setIsPersistVolumeGroupEnabled(true).build();
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithNavigationBound(settings, true);
        carVolumeGroup.loadVolumesSettingsForUser(TEST_USER_10);

        carVolumeGroup.setMute(true);

        verify(settings).storeVolumeGroupMuteForUser(TEST_USER_10, ZONE_ID, CONFIG_ID, GROUP_ID,
                /* isMuted= */ true);
    }

    @Test
    public void setMute_withUnMutedState_storesValueToSetting() {
        CarAudioSettings settings = new SettingsBuilder(ZONE_ID, CONFIG_ID, GROUP_ID)
                .setMuteForUser10(false).setIsPersistVolumeGroupEnabled(true).build();
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithNavigationBound(settings, true);
        carVolumeGroup.loadVolumesSettingsForUser(TEST_USER_10);

        carVolumeGroup.setMute(false);

        verify(settings).storeVolumeGroupMuteForUser(TEST_USER_10, ZONE_ID, CONFIG_ID, GROUP_ID,
                /* isMuted= */ false);
    }

    @Test
    public void getContextsForAddress_returnsContextsBoundToThatAddress() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        List<Integer> contextsList = carVolumeGroup.getContextsForAddress(MEDIA_DEVICE_ADDRESS);

        expectWithMessage("Contexts for bounded address %s", MEDIA_DEVICE_ADDRESS)
                .that(contextsList).containsExactly(TEST_MEDIA_CONTEXT_ID,
                        TEST_CALL_CONTEXT_ID, TEST_CALL_RING_CONTEXT_ID);
    }

    @Test
    public void getContextsForAddress_returnsEmptyArrayIfAddressNotBound() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        List<Integer> contextsList = carVolumeGroup.getContextsForAddress(OTHER_ADDRESS);

        expectWithMessage("Contexts for non-bounded address %s", OTHER_ADDRESS)
                .that(contextsList).isEmpty();
    }

    @Test
    public void getCarAudioDeviceInfoForAddress_returnsExpectedDevice() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        CarAudioDeviceInfo actualDevice = carVolumeGroup.getCarAudioDeviceInfoForAddress(
                MEDIA_DEVICE_ADDRESS);

        expectWithMessage("Device information for bounded address %s", MEDIA_DEVICE_ADDRESS)
                .that(actualDevice).isEqualTo(mMediaDeviceInfo);
    }

    @Test
    public void getCarAudioDeviceInfoForAddress_returnsNullIfAddressNotBound() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        CarAudioDeviceInfo actualDevice = carVolumeGroup.getCarAudioDeviceInfoForAddress(
                OTHER_ADDRESS);

        expectWithMessage("Device information for non-bounded address %s", OTHER_ADDRESS)
                .that(actualDevice).isNull();
    }

    @Test
    public void getAudioDeviceAttribute_returnsExpectedDevice() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_DYNAMIC_DEVICES);
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        CarVolumeGroupInfo info = carVolumeGroup.getCarVolumeGroupInfo();

        List<AudioDeviceAttributes> devices = info.getAudioDeviceAttributes();

        expectWithMessage("Audio device attributes").that(devices).containsExactly(
                mMediaDeviceInfo.getAudioDevice(), mNavigationDeviceInfo.getAudioDevice());
    }

    @Test
    public void isHalMuted_withHalUnmuted() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        expectWithMessage("Unmute status by HAL")
                .that(carVolumeGroup.isHalMuted()).isFalse();
    }

    @Test
    public void isHalMuted_withHalMuted() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setCurrentGainIndex(DEFAULT_GAIN_INDEX);
        List<Integer> muteReasons = List.of(Reasons.TCU_MUTE);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = MIN_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(muteReasons, musicCarGain);

        expectWithMessage("Mute status by HAL")
                .that(carVolumeGroup.isHalMuted()).isTrue();
    }

    @Test
    public void getMutedBySystem_withHalUnmuted() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MUTE_AMBIGUITY);
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        CarVolumeGroupInfo info = carVolumeGroup.getCarVolumeGroupInfo();

        expectWithMessage("Mute by system status with HAL unmuted")
                .that(info.isMutedBySystem()).isFalse();
    }

    @Test
    public void getMutedBySystem_withHalMuted() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MUTE_AMBIGUITY);
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setCurrentGainIndex(DEFAULT_GAIN_INDEX);
        List<Integer> muteReasons = List.of(Reasons.TCU_MUTE);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = MIN_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(muteReasons, musicCarGain);

        expectWithMessage("Mute by system status with HAL muted")
                .that(carVolumeGroup.getCarVolumeGroupInfo().isMutedBySystem()).isTrue();
    }

    @Test
    public void getMaxActivationGainIndex_returnsExpectedDevice() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        int maxActivationGainIndex = carVolumeGroup.getMaxActivationGainIndex();

        expectWithMessage("Max activation gain index").that(maxActivationGainIndex)
                .isEqualTo(MAX_ACTIVATION_GAIN_INDEX);
    }

    @Test
    public void getMinActivationGainIndex_returnsExpectedDevice() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        int minActivationGainIndex = carVolumeGroup.getMinActivationGainIndex();

        expectWithMessage("Min activation gain index").that(minActivationGainIndex)
                .isEqualTo(MIN_ACTIVATION_GAIN_INDEX);
    }

    @Test
    public void setCurrentGainIndex_setsGainOnAllBoundDevices() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);

        verify(mMediaDeviceInfo).setCurrentGain(7);
        verify(mNavigationDeviceInfo).setCurrentGain(7);
    }

    @Test
    public void setCurrentGainIndex_updatesCurrentGainIndex() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);

        expectWithMessage("Updated current gain index")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(TEST_GAIN_INDEX);
    }

    @Test
    public void setCurrentGainIndex_checksNewGainIsAboveMin() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> carVolumeGroup.setCurrentGainIndex(MIN_GAIN_INDEX - 1));

        expectWithMessage("Set out of bound gain index failure")
                .that(thrown).hasMessageThat()
                .contains("Gain out of range (" + MIN_GAIN_INDEX + ":" + MAX_GAIN_INDEX + ")");
    }

    @Test
    public void setCurrentGainIndex_checksNewGainIsBelowMax() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> carVolumeGroup.setCurrentGainIndex(MAX_GAIN_INDEX + 1));

        expectWithMessage("Set out of bound gain index failure")
                .that(thrown).hasMessageThat()
                .contains("Gain out of range (" + MIN_GAIN_INDEX + ":" + MAX_GAIN_INDEX + ")");
    }

    @Test
    public void setCurrentGainIndex_setsCurrentGainIndexForUser() {
        CarAudioSettings settings = new SettingsBuilder(ZONE_ID, CONFIG_ID, GROUP_ID)
                .setGainIndexForUser(TEST_USER_11).build();
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithNavigationBound(settings, false);
        carVolumeGroup.loadVolumesSettingsForUser(TEST_USER_11);

        carVolumeGroup.setCurrentGainIndex(TestCarAudioDeviceInfoBuilder.MIN_GAIN);

        verify(settings).storeVolumeGainIndexForUser(TEST_USER_11, ZONE_ID, CONFIG_ID, GROUP_ID,
                TestCarAudioDeviceInfoBuilder.MIN_GAIN);
    }

    @Test
    public void setCurrentGainIndex_setsCurrentGainIndexForDefaultUser() {
        CarAudioSettings settings = new SettingsBuilder(ZONE_ID, CONFIG_ID, GROUP_ID)
                .setGainIndexForUser(UserHandle.USER_CURRENT).build();
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithNavigationBound(settings, false);

        carVolumeGroup.setCurrentGainIndex(TestCarAudioDeviceInfoBuilder.MIN_GAIN);

        verify(settings).storeVolumeGainIndexForUser(UserHandle.USER_CURRENT, ZONE_ID, CONFIG_ID,
                GROUP_ID, TestCarAudioDeviceInfoBuilder.MIN_GAIN);
    }

    @Test
    public void handleActivationVolume_withActivationVolumeDisabled() {
        mSetFlagsRule.disableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(MAX_GAIN_INDEX);

        expectWithMessage("No adjustment to activation volume with activation volume disabled")
                .that(carVolumeGroup.handleActivationVolume()).isFalse();
        expectWithMessage("Unchanged gain index with activation volume disabled")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(MAX_GAIN_INDEX);
    }

    @Test
    public void handleActivationVolume_withVolumeWithinActivationVolumeRange() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        int currentGainIndex = MIN_ACTIVATION_GAIN_INDEX + 1;
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(currentGainIndex);

        expectWithMessage("No adjustment for activation volume")
                .that(carVolumeGroup.handleActivationVolume()).isFalse();
        expectWithMessage("Gain index without activation volume adjustment")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(currentGainIndex);
    }

    @Test
    public void handleActivationVolume_withVolumeBelowMinActivationVolume() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(MIN_ACTIVATION_GAIN_INDEX - 1);

        expectWithMessage("Success for adjusting to min activation volume")
                .that(carVolumeGroup.handleActivationVolume()).isTrue();
        expectWithMessage("Gain index for adjusting to min activation volume")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(MIN_ACTIVATION_GAIN_INDEX);
    }

    @Test
    public void handleActivationVolume_withVolumeAboveMaxActivationVolume() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(MAX_ACTIVATION_GAIN_INDEX + 1);

        expectWithMessage("Success for adjusting to max activation volume")
                .that(carVolumeGroup.handleActivationVolume()).isTrue();
        expectWithMessage("Gain index for adjusting to max activation volume")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(MAX_ACTIVATION_GAIN_INDEX);
    }

    @Test
    public void handleActivationVolume_withMute() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(MAX_ACTIVATION_GAIN_INDEX + 1);
        carVolumeGroup.setMute(true);

        expectWithMessage("Failure for changing to activation volume with mute")
                .that(carVolumeGroup.handleActivationVolume()).isFalse();
        expectWithMessage("Gain index with activation volume and mute")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(MIN_GAIN_INDEX);
    }

    @Test
    public void setMute_withFalseWhenMuteAndActivationVolume() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(MAX_ACTIVATION_GAIN_INDEX + 1);
        carVolumeGroup.setMute(true);
        carVolumeGroup.handleActivationVolume();

        carVolumeGroup.setMute(false);

        expectWithMessage("Gain index with activation volume after unmute")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(MAX_ACTIVATION_GAIN_INDEX);
    }

    @Test
    public void handleActivationVolume_withBlock() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(MAX_GAIN_INDEX);
        int blockedIndex = MAX_ACTIVATION_GAIN_INDEX + 1;
        carVolumeGroup.setBlocked(blockedIndex);

        expectWithMessage("Failure for changing to activation volume with block")
                .that(carVolumeGroup.handleActivationVolume()).isFalse();
        expectWithMessage("Gain index with activation volume and block")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(blockedIndex);
    }

    @Test
    public void resetBlocked_withActivationVolume() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(MAX_GAIN_INDEX);
        int blockedIndex = MAX_ACTIVATION_GAIN_INDEX + 1;
        carVolumeGroup.setBlocked(blockedIndex);
        carVolumeGroup.handleActivationVolume();

        carVolumeGroup.resetBlocked();

        expectWithMessage("Gain index with activation volume after block reset")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(MAX_ACTIVATION_GAIN_INDEX);
    }

    @Test
    public void handleActivationVolume_withAttenuatedGain() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);
        int attenuatedIndex = MAX_ACTIVATION_GAIN_INDEX + 1;
        carVolumeGroup.setAttenuatedGain(attenuatedIndex);

        expectWithMessage("Changing to activation volume with attenuated gain")
                .that(carVolumeGroup.handleActivationVolume()).isFalse();
        expectWithMessage("Gain index with activation volume and attenuated gain applied")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(attenuatedIndex);
    }

    @Test
    public void resetAttenuation_withActivationVolume() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(MAX_GAIN_INDEX);
        int attenuatedIndex = MAX_ACTIVATION_GAIN_INDEX + 1;
        carVolumeGroup.setAttenuatedGain(attenuatedIndex);
        carVolumeGroup.handleActivationVolume();

        carVolumeGroup.resetAttenuation();

        expectWithMessage("Gain index with activation volume and attenuated gain applied")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(MAX_ACTIVATION_GAIN_INDEX);
    }

    @Test
    public void handleActivationVolume_withActivationVolumeOverLimitOverCurrentGain() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(MIN_GAIN_INDEX);
        int limitedGainIndex = MIN_ACTIVATION_GAIN_INDEX - 1;
        carVolumeGroup.setLimit(limitedGainIndex);

        expectWithMessage("Volume changed due to activation volume over limit and limit "
                + "over current gain").that(carVolumeGroup.handleActivationVolume()).isTrue();
        expectWithMessage("Gain index with activation volume over limit and limit "
                + "over current gain").that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(limitedGainIndex);
    }

    @Test
    public void resetLimit_withActivationVolumeOverLimitOverCurrentGainAndResetLimit() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(MIN_ACTIVATION_GAIN_INDEX - 2);
        int limitedGainIndex = MIN_ACTIVATION_GAIN_INDEX - 1;
        carVolumeGroup.setLimit(limitedGainIndex);
        carVolumeGroup.handleActivationVolume();

        carVolumeGroup.resetLimit();

        expectWithMessage("Gain index with activation volume over limit, limit over current "
                + " gain and limit reset").that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(MIN_ACTIVATION_GAIN_INDEX);
    }

    @Test
    public void handleActivationVolume_withActivationVolumeOverCurrentGainOverLimit() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(MIN_ACTIVATION_GAIN_INDEX - 1);
        int limitedGainIndex = MIN_ACTIVATION_GAIN_INDEX - 2;
        carVolumeGroup.setLimit(limitedGainIndex);

        expectWithMessage("Volume changed due to activation volume over current gain and "
                + "current gain over limit").that(carVolumeGroup.handleActivationVolume())
                .isFalse();
        expectWithMessage("Gain index with activation volume over current gain and "
                + "current gain over limit").that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(limitedGainIndex);
    }

    @Test
    public void resetLimit_withActivationVolumeOverCurrentGainOverLimit() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(MIN_ACTIVATION_GAIN_INDEX - 1);
        int limitedGainIndex = MIN_ACTIVATION_GAIN_INDEX - 2;
        carVolumeGroup.setLimit(limitedGainIndex);
        carVolumeGroup.handleActivationVolume();

        carVolumeGroup.resetLimit();
        expectWithMessage("Gain index with activation volume over current gain, current gain "
                + "over limit and limit reset").that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(MIN_ACTIVATION_GAIN_INDEX);
    }

    @Test
    public void handleActivationVolume_withActivationVolumeBelowLimit() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(MIN_GAIN_INDEX);
        int limitedGainIndex = MIN_ACTIVATION_GAIN_INDEX + 1;
        carVolumeGroup.setLimit(limitedGainIndex);

        expectWithMessage("Changing to activation volume below limit")
                .that(carVolumeGroup.handleActivationVolume()).isTrue();
        expectWithMessage("Gain index with activation volume below limit")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(MIN_ACTIVATION_GAIN_INDEX);
    }

    @Test
    public void loadVolumesSettingsForUser_withMutedState_loadsMuteStateForUser() {
        CarVolumeGroup carVolumeGroup = getVolumeGroupWithMuteAndNavBound(true, true, true);

        carVolumeGroup.loadVolumesSettingsForUser(TEST_USER_10);

        expectWithMessage("Saved mute state from settings")
                .that(carVolumeGroup.isMuted()).isTrue();
    }

    @Test
    public void loadVolumesSettingsForUser_withDisabledUseVolumeGroupMute_doesNotLoadMute() {
        CarVolumeGroup carVolumeGroup = getVolumeGroupWithMuteAndNavBound(true, true, false);

        carVolumeGroup.loadVolumesSettingsForUser(TEST_USER_10);

        expectWithMessage("Default mute state")
                .that(carVolumeGroup.isMuted()).isFalse();
    }

    @Test
    public void loadVolumesSettingsForUser_withUnMutedState_loadsMuteStateForUser() {
        CarVolumeGroup carVolumeGroup = getVolumeGroupWithMuteAndNavBound(false, true, true);

        carVolumeGroup.loadVolumesSettingsForUser(TEST_USER_10);

        expectWithMessage("Saved mute state from settings").that(carVolumeGroup.isMuted())
                .isFalse();
    }

    @Test
    public void loadVolumesSettingsForUser_withMutedStateAndNoPersist_returnsDefaultMuteState() {
        CarVolumeGroup carVolumeGroup = getVolumeGroupWithMuteAndNavBound(true, false, true);

        carVolumeGroup.loadVolumesSettingsForUser(TEST_USER_10);

        expectWithMessage("Default mute state").that(carVolumeGroup.isMuted()).isFalse();
    }

    @Test
    public void hasCriticalAudioContexts_withoutCriticalContexts_returnsFalse() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();

        expectWithMessage("Group without critical audio context")
                .that(carVolumeGroup.hasCriticalAudioContexts()).isFalse();
    }

    @Test
    public void hasCriticalAudioContexts_withCriticalContexts_returnsTrue() {
        CarVolumeGroupFactory factory = getFactory(/* useCarVolumeGroupMute= */ true);
        factory.setDeviceInfoForContext(TEST_EMERGENCY_CONTEXT_ID, mMediaDeviceInfo);
        CarVolumeGroup carVolumeGroup = factory.getCarVolumeGroup(/* useCoreAudioVolume= */ false);

        expectWithMessage("Group with critical audio context")
                .that(carVolumeGroup.hasCriticalAudioContexts()).isTrue();
    }

    @Test
    public void getCurrentGainIndex_whileMuted_returnsMinGain() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);

        carVolumeGroup.setMute(true);

        expectWithMessage("Muted current gain index")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(MIN_GAIN_INDEX);
    }

    @Test
    public void getCurrentGainIndex_whileUnMuted_returnsLastSetGain() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);

        carVolumeGroup.setMute(false);

        expectWithMessage("Un-muted current gain index")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(TEST_GAIN_INDEX);
    }

    @Test
    public void setCurrentGainIndex_whileMuted_unMutesVolumeGroup() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setMute(true);
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);

        expectWithMessage("Mute state after volume change")
                .that(carVolumeGroup.isMuted()).isEqualTo(false);
    }

    @Test
    public void setBlocked_withGain_thenBackToUninitializedGain() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        expectWithMessage("Default blocked state").that(carVolumeGroup.isBlocked()).isFalse();

        carVolumeGroup.setBlocked(10);

        expectWithMessage("Blocked state after blocked").that(carVolumeGroup.isBlocked())
                .isTrue();

        carVolumeGroup.resetBlocked();

        expectWithMessage("Blocked state after reset").that(carVolumeGroup.isBlocked())
                .isFalse();
    }

    @Test
    public void setLimited_withGain_thenBackToMaxGain() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        expectWithMessage("Default limited state").that(carVolumeGroup.isLimited()).isFalse();

        carVolumeGroup.setLimit(carVolumeGroup.getMaxGainIndex() - 1);

        expectWithMessage("Limit state after set limit").that(carVolumeGroup.isLimited())
                .isTrue();

        carVolumeGroup.resetLimit();

        expectWithMessage("Limit state after reset").that(carVolumeGroup.isLimited())
                .isFalse();
    }

    @Test
    public void setAttenuatedGain_withGain_thenBackToUninitializedGain() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        expectWithMessage("Default attenuated state").that(carVolumeGroup.isAttenuated()).isFalse();

        carVolumeGroup.setAttenuatedGain(10);

        expectWithMessage("Attenuated state after set attenuated").that(carVolumeGroup
                .isAttenuated()).isTrue();

        carVolumeGroup.resetAttenuation();

        expectWithMessage("Attenuated state after reset").that(carVolumeGroup.isAttenuated())
                .isFalse();
    }

    @Test
    public void getCurrentGainIndex_whileBlocked_thenUnblocked() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);

        expectWithMessage("Initial current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(TEST_GAIN_INDEX);

        int blockedIndex = 10;
        carVolumeGroup.setBlocked(blockedIndex);

        expectWithMessage("Blocked state after set blocked").that(carVolumeGroup.isBlocked())
                .isTrue();

        expectWithMessage("Blocked current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(blockedIndex);

        carVolumeGroup.resetBlocked();

        expectWithMessage("Blocked state after reset").that(carVolumeGroup.isBlocked()).isFalse();

        expectWithMessage("Back to current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(TEST_GAIN_INDEX);
    }

    @Test
    public void getCurrentGainIndex_whileLimited_thenUnlimited() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);
        expectWithMessage("Initial current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(TEST_GAIN_INDEX);
        expectWithMessage("Default limit state").that(carVolumeGroup.isLimited()).isFalse();

        int limitedGainIndex = carVolumeGroup.getMinGainIndex() + 1;
        carVolumeGroup.setLimit(limitedGainIndex);

        expectWithMessage("Limit state after set limit").that(carVolumeGroup.isLimited())
                .isTrue();
        expectWithMessage("Limited current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(limitedGainIndex);

        carVolumeGroup.resetLimit();

        expectWithMessage("Limit state after reset").that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Back to current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(TEST_GAIN_INDEX);
    }

    @Test
    public void getCurrentGainIndex_whileAttenuated_thenUnattenuated() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);
        expectWithMessage("Initial current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(TEST_GAIN_INDEX);
        expectWithMessage("Default attenuated state").that(carVolumeGroup.isAttenuated())
                .isFalse();

        int attenuatedIndex = TEST_GAIN_INDEX - 1;
        carVolumeGroup.setAttenuatedGain(attenuatedIndex);

        expectWithMessage("Attenuated state after set attenuated").that(carVolumeGroup
                .isAttenuated()).isTrue();
        expectWithMessage("Attenuated current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(attenuatedIndex);

        carVolumeGroup.resetAttenuation();

        expectWithMessage("Attenuated state after reset").that(carVolumeGroup.isAttenuated())
                .isFalse();
        expectWithMessage("Muted current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(TEST_GAIN_INDEX);
    }

    @Test
    public void setCurrentGainIndex_whileBlocked_thenRemainsUnblocked() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);

        expectWithMessage("Initial current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(TEST_GAIN_INDEX);

        int blockedIndex = 1;
        carVolumeGroup.setBlocked(blockedIndex);

        expectWithMessage("Blocked state after set blocked").that(carVolumeGroup.isBlocked())
                .isTrue();

        carVolumeGroup.setCurrentGainIndex(blockedIndex + 1);

        expectWithMessage("Over Blocked current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(blockedIndex);

        carVolumeGroup.setCurrentGainIndex(blockedIndex - 1);

        expectWithMessage("Under Blocked current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(blockedIndex);
    }

    @Test
    public void setCurrentGainIndex_whileLimited_under_then_over_limit() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setCurrentGainIndex(MAX_GAIN_INDEX);
        expectWithMessage("Initial current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(MAX_GAIN_INDEX);
        expectWithMessage("Default limit state").that(carVolumeGroup.isLimited()).isFalse();

        int limitedGainIndex = MAX_GAIN_INDEX - 1;
        carVolumeGroup.setLimit(limitedGainIndex);

        expectWithMessage("Limit state after set limit").that(carVolumeGroup.isLimited())
                .isTrue();
        expectWithMessage("Over limit state due to over limit gain").that(carVolumeGroup
                .isOverLimit()).isTrue();

        // Underlimit
        carVolumeGroup.setCurrentGainIndex(limitedGainIndex - 1);

        expectWithMessage("Under limit current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(limitedGainIndex - 1);

        expectWithMessage("Limit state after set limit and setting gain under limit")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Over limit state after set limit and setting gain under limit")
                .that(carVolumeGroup.isOverLimit()).isFalse();

        // Overlimit
        carVolumeGroup.setCurrentGainIndex(limitedGainIndex + 1);

        expectWithMessage("Over limit current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(limitedGainIndex);

        expectWithMessage("Limit state after set limit and fail to set gain over limit")
                .that(carVolumeGroup.isLimited()).isTrue();
        // Limitation prevents to set over limited index
        expectWithMessage("Over limit state after set limit and fail to set gain over limit")
                .that(carVolumeGroup.isOverLimit()).isFalse();
    }

    @Test
    public void setCurrentGainIndex_whileAttenuated_thenUnattenuated() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);
        expectWithMessage("Initial current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(TEST_GAIN_INDEX);
        expectWithMessage("Default attenuated state").that(carVolumeGroup.isAttenuated())
                .isFalse();

        int attenuatedIndex = TEST_GAIN_INDEX - 2;
        carVolumeGroup.setAttenuatedGain(attenuatedIndex);

        expectWithMessage("Attenuated state after set attenuated").that(carVolumeGroup
                .isAttenuated()).isTrue();
        expectWithMessage("Attenuated current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(attenuatedIndex);

        carVolumeGroup.setCurrentGainIndex(attenuatedIndex + 1);

        expectWithMessage("Attenuated state after reset gain index").that(carVolumeGroup
                .isAttenuated()).isFalse();
        expectWithMessage("new current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(attenuatedIndex + 1);
    }

    @Test
    public void isOverLimit_expectedTrue() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(MAX_GAIN_INDEX);

        List<Integer> limitReasons = List.of(Reasons.THERMAL_LIMITATION);

        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = TEST_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        carVolumeGroup.onAudioGainChanged(limitReasons, musicCarGain);
        expectWithMessage("Limit state with thermal limitation")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Over limit state with thermal limitation")
                .that(carVolumeGroup.isOverLimit()).isTrue();
    }

    @Test
    public void isOverLimit_expectedFalse() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX - 1);

        List<Integer> limitReasons = List.of(Reasons.THERMAL_LIMITATION);

        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = TEST_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        carVolumeGroup.onAudioGainChanged(limitReasons, musicCarGain);

        expectWithMessage("Limit state with thermal limitation while under limit")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Over limit state with thermal limitation while under limit")
                .that(carVolumeGroup.isOverLimit()).isFalse();
    }

    @Test
    public void onAudioGainChanged_withOverLimit_thenEndsAndRestoresVolume() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(MAX_GAIN_INDEX);
        List<Integer> limitReasons = List.of(Reasons.THERMAL_LIMITATION);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = DEFAULT_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        expectWithMessage("Audio gain changed with over limit")
                .that(carVolumeGroup.onAudioGainChanged(limitReasons, musicCarGain))
                .isEqualTo(EVENT_TYPE_ATTENUATION_CHANGED | EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);
        expectWithMessage("Over limit gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(DEFAULT_GAIN_INDEX);
        expectWithMessage("Attenuated state after set limited")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after set limited")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Over limit state after set limited")
                .that(carVolumeGroup.isOverLimit()).isTrue();
        expectWithMessage("BLocked state after set limited")
                .that(carVolumeGroup.isBlocked()).isFalse();
        expectWithMessage("Mute state after set limited")
                .that(carVolumeGroup.isMuted()).isFalse();

        List<Integer> noReasons = new ArrayList<>(0);
        expectWithMessage("Audio gain changed with over limit")
                .that(carVolumeGroup.onAudioGainChanged(noReasons, musicCarGain))
                .isEqualTo(EVENT_TYPE_ATTENUATION_CHANGED | EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);
        expectWithMessage("Attenuated state after reset limited")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after reset limited")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Over limit state after reset limited")
                .that(carVolumeGroup.isOverLimit()).isFalse();
        expectWithMessage("BLocked state after reset limited")
                .that(carVolumeGroup.isBlocked()).isFalse();
        expectWithMessage("Mute state after reset limited")
                .that(carVolumeGroup.isMuted()).isFalse();
        expectWithMessage("Restored initial gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(MAX_GAIN_INDEX);
    }

    @Test
    public void onAudioGainChanged_withUnderLimit_thenEndsWithVolumeUnchanged() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(MIN_GAIN_INDEX);
        List<Integer> limitReasons = List.of(Reasons.THERMAL_LIMITATION);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = DEFAULT_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        expectWithMessage("Audio gain changed with under limit")
                .that(carVolumeGroup.onAudioGainChanged(limitReasons, musicCarGain))
                .isEqualTo(EVENT_TYPE_ATTENUATION_CHANGED);
        expectWithMessage("Under limit gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(MIN_GAIN_INDEX);
        expectWithMessage("Attenuated state after set limited")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after set limited")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Over limit state after set limited")
                .that(carVolumeGroup.isOverLimit()).isFalse();
        expectWithMessage("Blocked state after set limited")
                .that(carVolumeGroup.isBlocked()).isFalse();
        expectWithMessage("Mute state after set limited")
                .that(carVolumeGroup.isMuted()).isFalse();

        List<Integer> noReasons = new ArrayList<>(0);
        expectWithMessage("Audio gain changed with under limit")
                .that(carVolumeGroup.onAudioGainChanged(noReasons, musicCarGain))
                .isEqualTo(EVENT_TYPE_ATTENUATION_CHANGED);
        expectWithMessage("Attenuated state after reset limited")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after reset limited")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Over limit state after reset limited")
                .that(carVolumeGroup.isOverLimit()).isFalse();
        expectWithMessage("Blocked state after reset limited")
                .that(carVolumeGroup.isBlocked()).isFalse();
        expectWithMessage("Mute state after reset limited")
                .that(carVolumeGroup.isMuted()).isFalse();
        expectWithMessage("Unchanged gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(MIN_GAIN_INDEX);
    }

    @Test
    public void onAudioGainChanged_withBlockedGain_thenEndsAndRestoresVolume() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(DEFAULT_GAIN_INDEX);
        List<Integer> blockReasons = List.of(Reasons.FORCED_MASTER_MUTE);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = MIN_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        expectWithMessage("Audio gain changed with blocked")
                .that(carVolumeGroup.onAudioGainChanged(blockReasons, musicCarGain))
                .isEqualTo(EVENT_TYPE_VOLUME_BLOCKED_CHANGED
                        | EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);
        expectWithMessage("Attenuated state after set blocked")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after set blocked")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Over limit state after set blocked")
                .that(carVolumeGroup.isOverLimit()).isFalse();
        expectWithMessage("Blocked state after set blocked")
                .that(carVolumeGroup.isBlocked()).isTrue();
        expectWithMessage("Mute state after set blocked")
                .that(carVolumeGroup.isMuted()).isFalse();
        expectWithMessage("Blocked gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(MIN_GAIN_INDEX);

        List<Integer> noReasons = new ArrayList<>(0);
        expectWithMessage("Audio gain changed with blocked")
                .that(carVolumeGroup.onAudioGainChanged(noReasons, musicCarGain))
                .isEqualTo(EVENT_TYPE_VOLUME_BLOCKED_CHANGED
                        | EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);
        expectWithMessage("Attenuated state after reset blocked")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after reset blocked")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Over limit state after reset blocked")
                .that(carVolumeGroup.isOverLimit()).isFalse();
        expectWithMessage("BLocked state after reset blocked")
                .that(carVolumeGroup.isBlocked()).isFalse();
        expectWithMessage("Mute state after reset blocked")
                .that(carVolumeGroup.isMuted()).isFalse();
        expectWithMessage("Restored initial gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(DEFAULT_GAIN_INDEX);
    }

    @Test
    public void onAudioGainChanged_withAttenuatedGain_thenEndsAndRestoresVolume() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(DEFAULT_GAIN_INDEX);
        int attenuatedIndex = DEFAULT_GAIN_INDEX - 1;
        List<Integer> attenuateReasons = List.of(Reasons.ADAS_DUCKING);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = attenuatedIndex;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        expectWithMessage("Audio gain changed with attenuated gain")
                .that(carVolumeGroup.onAudioGainChanged(attenuateReasons, musicCarGain))
                .isEqualTo(EVENT_TYPE_ATTENUATION_CHANGED | EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);
        expectWithMessage("Attenuated state after set attenuated")
                .that(carVolumeGroup.isAttenuated()).isTrue();
        expectWithMessage("Limit state after set attenuated")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Over limit state after set attenuated")
                .that(carVolumeGroup.isOverLimit()).isFalse();
        expectWithMessage("BLocked state after set attenuated")
                .that(carVolumeGroup.isBlocked()).isFalse();
        expectWithMessage("Mute state after set attenuated")
                .that(carVolumeGroup.isMuted()).isFalse();
        expectWithMessage("Attenuated gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(attenuatedIndex);

        List<Integer> noReasons = new ArrayList<>(0);
        expectWithMessage("Audio gain changed with attenuated gain")
                .that(carVolumeGroup.onAudioGainChanged(noReasons, musicCarGain))
                .isEqualTo(EVENT_TYPE_ATTENUATION_CHANGED | EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);
        expectWithMessage("Attenuated state after reset attenuated")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after reset attenuated")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Over limit state after reset attenuated")
                .that(carVolumeGroup.isOverLimit()).isFalse();
        expectWithMessage("BLocked state after reset attenuated")
                .that(carVolumeGroup.isBlocked()).isFalse();
        expectWithMessage("Mute state after reset attenuated")
                .that(carVolumeGroup.isMuted()).isFalse();
        expectWithMessage("Restored initial gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(DEFAULT_GAIN_INDEX);
    }

    @Test
    public void onAudioGainChanged_withMutedGain_thenEndsAndRestoresVolume() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(DEFAULT_GAIN_INDEX);
        List<Integer> muteReasons = List.of(Reasons.TCU_MUTE);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = MIN_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        expectWithMessage("Audio gain changed with muted")
                .that(carVolumeGroup.onAudioGainChanged(muteReasons, musicCarGain))
                .isEqualTo(EVENT_TYPE_VOLUME_BLOCKED_CHANGED | EVENT_TYPE_MUTE_CHANGED
                        | EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);
        expectWithMessage("Attenuated state after set muted")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after set muted")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Over limit state after set muted")
                .that(carVolumeGroup.isOverLimit()).isFalse();
        expectWithMessage("Blocked state after set muted")
                .that(carVolumeGroup.isBlocked()).isTrue();
        expectWithMessage("Mute state after set muted")
                .that(carVolumeGroup.isMuted()).isTrue();
        expectWithMessage("Blocked gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(MIN_GAIN_INDEX);

        List<Integer> noReasons = new ArrayList<>(0);
        expectWithMessage("Audio gain changed with blocked")
                .that(carVolumeGroup.onAudioGainChanged(noReasons, musicCarGain))
                .isEqualTo(EVENT_TYPE_VOLUME_BLOCKED_CHANGED | EVENT_TYPE_MUTE_CHANGED
                        | EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);
        expectWithMessage("Attenuated state after reset muted")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after reset muted")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Over limit state after reset muted")
                .that(carVolumeGroup.isOverLimit()).isFalse();
        expectWithMessage("BLocked state after reset muted")
                .that(carVolumeGroup.isBlocked()).isFalse();
        expectWithMessage("Mute state after reset muted")
                .that(carVolumeGroup.isMuted()).isFalse();
        expectWithMessage("Restored initial gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(DEFAULT_GAIN_INDEX);
    }

    @Test
    public void onAudioGainChanged_withMutedGain_whenGroupMutingDisabled_doesNotSetMute() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup(/* useCarVolumeGroupMute= */ false);
        carVolumeGroup.setCurrentGainIndex(DEFAULT_GAIN_INDEX);
        List<Integer> muteReasons = List.of(Reasons.TCU_MUTE);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = MIN_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        expectWithMessage("Audio gain changed with muted")
                .that(carVolumeGroup.onAudioGainChanged(muteReasons, musicCarGain))
                .isEqualTo(EVENT_TYPE_VOLUME_BLOCKED_CHANGED
                        | EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);
        expectWithMessage("Mute state").that(carVolumeGroup.isMuted()).isFalse();
    }

    @Test
    public void onAudioGainChanged_withVolumeFeedback() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);
        List<Integer> volFeedbackReasons = List.of(Reasons.EXTERNAL_AMP_VOL_FEEDBACK);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = TEST_GAIN_INDEX - 1;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        expectWithMessage("Audio gain changed with external amp vol feedback")
                .that(carVolumeGroup.onAudioGainChanged(volFeedbackReasons, musicCarGain))
                .isEqualTo(EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);
        expectWithMessage("Updated gain index")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(musicGain.volumeIndex);
        expectWithMessage("Attenuated state after external amp vol feedback")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after external amp vol feedback")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Over limit state after external amp vol feedback")
                .that(carVolumeGroup.isOverLimit()).isFalse();
        expectWithMessage("Blocked state after external amp vol feedback")
                .that(carVolumeGroup.isBlocked()).isFalse();
        expectWithMessage("Mute state after external amp vol feedback")
                .that(carVolumeGroup.isMuted()).isFalse();
    }

    @Test
    public void onAudioGainChanged_withBlockingLimitMuteAndAttenuation() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        List<Integer> allReasons =
                List.of(
                        -1,
                        -10,
                        666,
                        Reasons.FORCED_MASTER_MUTE,
                        Reasons.TCU_MUTE,
                        Reasons.REMOTE_MUTE,
                        Reasons.THERMAL_LIMITATION,
                        Reasons.SUSPEND_EXIT_VOL_LIMITATION,
                        Reasons.ADAS_DUCKING,
                        Reasons.ADAS_DUCKING);

        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = DEFAULT_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        expectWithMessage("Audio gain changed with blocked, limited, muted and attenuated")
                .that(carVolumeGroup.onAudioGainChanged(allReasons, musicCarGain))
                .isEqualTo(EVENT_TYPE_ATTENUATION_CHANGED | EVENT_TYPE_VOLUME_BLOCKED_CHANGED
                        | EVENT_TYPE_MUTE_CHANGED | EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);
        expectWithMessage("Attenuated state while blocked, limited, muted and attenuated")
                .that(carVolumeGroup.isAttenuated()).isTrue();
        expectWithMessage("Limit state while blocked, limited, muted and attenuated")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Blocked state while blocked, limited, muted and attenuated")
                .that(carVolumeGroup.isBlocked()).isTrue();
        expectWithMessage("Mute state while blocked, limited, muted and attenuated")
                .that(carVolumeGroup.isMuted()).isTrue();
    }

    @Test
    public void onAudioGainChanged_resettingBlockingLimitMuteAndAttenuation() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        List<Integer> noReasons = new ArrayList<>(0);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = DEFAULT_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        expectWithMessage("Audio gain changed with no reasons")
                .that(carVolumeGroup.onAudioGainChanged(noReasons, musicCarGain))
                .isEqualTo(EVENT_TYPE_NONE);
        expectWithMessage("Attenuated state after reset of blocked, limited, muted and attenuated")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after reset of blocked, limited, muted and attenuated")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Blocked state after reset of blocked, limited, muted and attenuated")
                .that(carVolumeGroup.isBlocked()).isFalse();
        expectWithMessage("Muted state after reset of blocked, limited, muted and attenuated")
                .that(carVolumeGroup.isMuted()).isFalse();
    }

    @Test
    public void onAudioGainChanged_setResettingBlockingLimitMuteAndAttenuation() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        List<Integer> allReasons =
                List.of(
                        Reasons.FORCED_MASTER_MUTE,
                        Reasons.TCU_MUTE,
                        Reasons.REMOTE_MUTE,
                        Reasons.THERMAL_LIMITATION,
                        Reasons.SUSPEND_EXIT_VOL_LIMITATION,
                        Reasons.ADAS_DUCKING,
                        Reasons.ADAS_DUCKING);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = DEFAULT_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(allReasons, musicCarGain);
        List<Integer> noReasons = new ArrayList<>(0);

        expectWithMessage("Audio gain changed with reset of blocked, limited, muted and attenuated")
                .that(carVolumeGroup.onAudioGainChanged(noReasons, musicCarGain))
                .isEqualTo(EVENT_TYPE_ATTENUATION_CHANGED | EVENT_TYPE_VOLUME_BLOCKED_CHANGED
                        | EVENT_TYPE_MUTE_CHANGED | EVENT_TYPE_VOLUME_GAIN_INDEX_CHANGED);
        expectWithMessage("Attenuated state after reset of blocked, limited, muted and attenuated")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after reset of blocked, limited, muted and attenuated")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Blocked state after reset of blocked, limited, muted and attenuated")
                .that(carVolumeGroup.isBlocked()).isFalse();
        expectWithMessage("Muted state after reset of blocked, limited, muted and attenuated")
                .that(carVolumeGroup.isMuted()).isFalse();
    }

    @Test
    public void onAudioGainChanged_validGain() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        List<Integer> reasons = List.of(Reasons.FORCED_MASTER_MUTE, Reasons.NAV_DUCKING);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = DEFAULT_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        AudioGainConfigInfo navGain = new AudioGainConfigInfo();
        navGain.zoneId = ZONE_ID;
        navGain.devicePortAddress = NAVIGATION_DEVICE_ADDRESS;
        navGain.volumeIndex = DEFAULT_GAIN_INDEX;
        CarAudioGainConfigInfo navCarGain = new CarAudioGainConfigInfo(navGain);

        carVolumeGroup.onAudioGainChanged(reasons, musicCarGain);
        // Broadcasted to all CarAudioDeviceInfo
        verify(mMediaDeviceInfo).setCurrentGain(TestCarAudioDeviceInfoBuilder.DEFAULT_GAIN);
        verify(mNavigationDeviceInfo).setCurrentGain(TestCarAudioDeviceInfoBuilder.DEFAULT_GAIN);

        carVolumeGroup.onAudioGainChanged(reasons, navCarGain);
        // Broadcasted to all CarAudioDeviceInfo
        verify(mMediaDeviceInfo, times(2)).setCurrentGain(
                TestCarAudioDeviceInfoBuilder.DEFAULT_GAIN);
        verify(mNavigationDeviceInfo, times(2)).setCurrentGain(
                TestCarAudioDeviceInfoBuilder.DEFAULT_GAIN);
    }

    @Test
    public void onAudioGainChanged_invalidGain() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        List<Integer> reasons = List.of(Reasons.REMOTE_MUTE, Reasons.NAV_DUCKING);
        AudioGainConfigInfo unknownGain = new AudioGainConfigInfo();
        unknownGain.zoneId = ZONE_ID;
        unknownGain.devicePortAddress = OTHER_ADDRESS;
        unknownGain.volumeIndex = 666;
        CarAudioGainConfigInfo unknownCarGain = new CarAudioGainConfigInfo(unknownGain);

        expectWithMessage("Audio gain changed with invalid gain")
                .that(carVolumeGroup.onAudioGainChanged(reasons, unknownCarGain))
                .isEqualTo(EVENT_TYPE_NONE);
        verify(mMediaDeviceInfo, never()).setCurrentGain(anyInt());
        verify(mNavigationDeviceInfo, never()).setCurrentGain(anyInt());
    }

    @Test
    public void onAudioGainChanged_comboAttenuationLimitation_simultaneously() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        int comboLimitAttenuation = DEFAULT_GAIN_INDEX - 1;
        carVolumeGroup.setCurrentGainIndex(MAX_GAIN_INDEX);
        List<Integer> reasons = List.of(Reasons.THERMAL_LIMITATION, Reasons.ADAS_DUCKING);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = comboLimitAttenuation;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        carVolumeGroup.onAudioGainChanged(reasons, musicCarGain);

        expectWithMessage("Attenuated state in combo limited / attenuated")
                .that(carVolumeGroup.isAttenuated()).isTrue();
        expectWithMessage("Limit state in combo limited / attenuated")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Attenuated gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(comboLimitAttenuation);
    }

    @Test
    public void onAudioGainChanged_resetAttenuation_whileComboAttenuationLimitation() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        int comboLimitAttenuation = DEFAULT_GAIN_INDEX;
        carVolumeGroup.setCurrentGainIndex(MAX_GAIN_INDEX);
        List<Integer> reasons = List.of(Reasons.THERMAL_LIMITATION, Reasons.ADAS_DUCKING);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = comboLimitAttenuation;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(reasons, musicCarGain);

        // Set a gain under the limit
        carVolumeGroup.setCurrentGainIndex(comboLimitAttenuation - 1);

        expectWithMessage("Attenuation state after attempt to set the index")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limitation state after from attempt to set the gain")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Limited gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(comboLimitAttenuation - 1);
    }

    @Test
    public void onAudioGainChanged_withGainUpdate_whileComboAttenuationLimitation() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        int comboLimitAttenuation = DEFAULT_GAIN_INDEX - 1;
        carVolumeGroup.setCurrentGainIndex(MAX_GAIN_INDEX);
        List<Integer> reasons = List.of(Reasons.THERMAL_LIMITATION, Reasons.ADAS_DUCKING);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = comboLimitAttenuation;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(reasons, musicCarGain);

        // any new callback will be interpreted as an update of the current reasons
        // (limit and attenuation)
        int updatedComboLimitAttenuation = DEFAULT_GAIN_INDEX + 1;
        musicGain.volumeIndex = updatedComboLimitAttenuation;
        musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(reasons, musicCarGain);

        expectWithMessage("Attenuated state in combo limited / attenuated")
                .that(carVolumeGroup.isAttenuated()).isTrue();
        expectWithMessage("Limit state in combo limited / attenuated")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Attenuated gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(updatedComboLimitAttenuation);
    }

    @Test
    public void onAudioGainChanged_limitation_withLimitUpdate() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        int initialLimit = DEFAULT_GAIN_INDEX - 1;
        int initialIndex = MAX_GAIN_INDEX;
        carVolumeGroup.setCurrentGainIndex(initialIndex);
        List<Integer> reasons = List.of(Reasons.THERMAL_LIMITATION);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = initialLimit;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(reasons, musicCarGain);

        // any new callback will be interpreted as an update of the limitation, allowing higher
        // volume index
        int updatedLimit = DEFAULT_GAIN_INDEX;
        musicGain.volumeIndex = updatedLimit;
        musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(reasons, musicCarGain);

        expectWithMessage("Limitation state after limitation with less restrictive limit update")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Gain index after limitation with less restrictive limit update")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(updatedLimit);
    }

    @Test
    public void onAudioGainChanged_endOfRestrictions_afterLimitationWithLimitUpdate() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        int initialLimit = DEFAULT_GAIN_INDEX - 1;
        int initialIndex = MAX_GAIN_INDEX;
        carVolumeGroup.setCurrentGainIndex(initialIndex);
        List<Integer> reasons = List.of(Reasons.THERMAL_LIMITATION);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = initialLimit;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(reasons, musicCarGain);
        musicGain.volumeIndex = initialLimit + 1;
        musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(reasons, musicCarGain);

        // End of restrictions
        List<Integer> noReasons = new ArrayList<>(0);
        carVolumeGroup.onAudioGainChanged(noReasons, musicCarGain);

        expectWithMessage("Limitation state after end of restrictions")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Gain index after end of restrictions")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(initialIndex);
    }

    @Test
    public void onAudioGainChanged_comboAttenuationLimitationHigherLimit_limitationStartFirst() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        int limitation = DEFAULT_GAIN_INDEX;
        int initialIndex = MAX_GAIN_INDEX;
        int comboLimitAttenuation = DEFAULT_GAIN_INDEX - 1;
        carVolumeGroup.setCurrentGainIndex(initialIndex);
        // Limitation starts first, gain is the limited index
        List<Integer> limitationReasons = List.of(Reasons.THERMAL_LIMITATION);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = limitation;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(limitationReasons, musicCarGain);

        // COMBO ATTENUATION + LIMITATION, gain is the attenuation / new limit
        List<Integer> comboReasons = List.of(Reasons.THERMAL_LIMITATION, Reasons.ADAS_DUCKING);
        musicGain.volumeIndex = comboLimitAttenuation;
        musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(comboReasons, musicCarGain);

        expectWithMessage("Limitation state after combo").that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Attenuation state after combo")
                .that(carVolumeGroup.isAttenuated()).isTrue();
        expectWithMessage("Gain index after combo")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(comboLimitAttenuation);
    }

    @Test
    public void onAudioGainChanged_comboAttenuationLimitationWithHigerLimit_whilelimited() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        int limitation = DEFAULT_GAIN_INDEX;
        int initialIndex = MAX_GAIN_INDEX;
        int comboLimitAttenuation = limitation + 1;
        carVolumeGroup.setCurrentGainIndex(initialIndex);
        // Limitation starts first, gain is the limited index
        List<Integer> limitationReasons = List.of(Reasons.THERMAL_LIMITATION);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = limitation;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(limitationReasons, musicCarGain);

        // COMBO ATTENUATION + LIMITATION, gain is the attenuation / new limit
        List<Integer> comboReasons = List.of(Reasons.THERMAL_LIMITATION, Reasons.ADAS_DUCKING);
        musicGain.volumeIndex = comboLimitAttenuation;
        musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(comboReasons, musicCarGain);

        expectWithMessage("Limitation state after combo")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Attenuation state after combo")
                .that(carVolumeGroup.isAttenuated()).isTrue();
        expectWithMessage("Gain index after combo")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(comboLimitAttenuation);
    }

    @Test
    public void onAudioGainChanged_comboAttenuationLimitationWithHigherLimit_whileAttenuated() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        int initialIndex = MAX_GAIN_INDEX;
        int attenuation = DEFAULT_GAIN_INDEX;
        int comboLimitAttenuation = DEFAULT_GAIN_INDEX + 1;
        carVolumeGroup.setCurrentGainIndex(initialIndex);
        List<Integer> attenuationReasons = List.of(Reasons.ADAS_DUCKING);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = attenuation;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(attenuationReasons, musicCarGain);

        // COMBO ATTENUATION + LIMITATION, gain is the new attenuation / limit
        List<Integer> comboReasons = List.of(Reasons.THERMAL_LIMITATION, Reasons.ADAS_DUCKING);
        musicGain.volumeIndex = comboLimitAttenuation;
        musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(comboReasons, musicCarGain);

        expectWithMessage("Limitation state after combo")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Attenuation state after combo")
                .that(carVolumeGroup.isAttenuated()).isTrue();
        expectWithMessage("Gain index after combo")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(comboLimitAttenuation);
    }

    @Test
    public void onAudioGainChanged_comboAttenuationLimitationWithLowerLimit_whileAttenuated() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        int initialIndex = MAX_GAIN_INDEX;
        int attenuation = DEFAULT_GAIN_INDEX;
        int comboLimitAttenuation = attenuation - 1;
        carVolumeGroup.setCurrentGainIndex(initialIndex);
        List<Integer> attenuationReasons = List.of(Reasons.ADAS_DUCKING);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = attenuation;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(attenuationReasons, musicCarGain);

        // COMBO ATTENUATION + LIMITATION, gain is the new attenuation/ limit, lower than previous
        // attenuation
        List<Integer> comboReasons = List.of(Reasons.THERMAL_LIMITATION, Reasons.ADAS_DUCKING);
        musicGain.volumeIndex = comboLimitAttenuation;
        musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(comboReasons, musicCarGain);

        expectWithMessage("Limitation state after combo")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Attenuation state after combo")
                .that(carVolumeGroup.isAttenuated()).isTrue();
        expectWithMessage("Gain index after combo")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(comboLimitAttenuation);
    }

    @Test
    public void onAudioGainChanged_comboAttenuationLimitation_withHigherLimitUpdate() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        int comboLimitAttenuation = DEFAULT_GAIN_INDEX;
        int comboLimitAttenuationUpdate = DEFAULT_GAIN_INDEX + 1;
        int initialIndex = MAX_GAIN_INDEX;
        carVolumeGroup.setCurrentGainIndex(initialIndex);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = comboLimitAttenuation;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        // COMBO ATTENUATION + LIMITATION, gain is the attenuation / new limit
        List<Integer> comboReasons = List.of(Reasons.THERMAL_LIMITATION, Reasons.ADAS_DUCKING);
        carVolumeGroup.onAudioGainChanged(comboReasons, musicCarGain);

        // COMBO ATTENUATION + LIMITATION, gain is the new limit / new attenuation
        musicGain.volumeIndex = comboLimitAttenuationUpdate;
        musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(comboReasons, musicCarGain);

        expectWithMessage("Limitation state after combo with gain update")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Attenuation state after combo with gain update")
                .that(carVolumeGroup.isAttenuated()).isTrue();
        expectWithMessage("Gain index after combo with gain update")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(comboLimitAttenuationUpdate);
    }

    @Test
    public void onAudioGainChanged_comboAttenuationLimitation_withLowerLimitUpdate() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        int comboLimitAttenuation = DEFAULT_GAIN_INDEX;
        int comboLimitAttenuationUpdate = DEFAULT_GAIN_INDEX - 1;
        int initialIndex = MAX_GAIN_INDEX;
        carVolumeGroup.setCurrentGainIndex(initialIndex);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = comboLimitAttenuation;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        // COMBO ATTENUATION + LIMITATION, gain is the attenuation / limit
        List<Integer> comboReasons = List.of(Reasons.THERMAL_LIMITATION, Reasons.ADAS_DUCKING);
        carVolumeGroup.onAudioGainChanged(comboReasons, musicCarGain);

        // COMBO ATTENUATION + LIMITATION, gain is the new attenuation / new limit
        musicGain.volumeIndex = comboLimitAttenuationUpdate;
        musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(comboReasons, musicCarGain);

        expectWithMessage("Limitation state after combo with gain update")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Attenuation state after combo with gain update")
                .that(carVolumeGroup.isAttenuated()).isTrue();
        expectWithMessage("Gain index after combo with gain update")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(comboLimitAttenuationUpdate);
    }

    @Test
    public void onAudioGainChanged_comboAttenuationLimitationHigerLimit_attenuationEndsFirst() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        int limitation = DEFAULT_GAIN_INDEX;
        int comboLimitAttenuation = DEFAULT_GAIN_INDEX - 1;
        int initialIndex = MAX_GAIN_INDEX;
        carVolumeGroup.setCurrentGainIndex(initialIndex);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = comboLimitAttenuation;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        // COMBO ATTENUATION + LIMITATION, gain is the attenuation / limit
        List<Integer> comboReasons = List.of(Reasons.THERMAL_LIMITATION, Reasons.ADAS_DUCKING);
        carVolumeGroup.onAudioGainChanged(comboReasons, musicCarGain);

        // End of Attenuation first
        musicGain.volumeIndex = limitation;
        musicCarGain = new CarAudioGainConfigInfo(musicGain);
        List<Integer> limitationReasons = List.of(Reasons.THERMAL_LIMITATION);
        carVolumeGroup.onAudioGainChanged(limitationReasons, musicCarGain);

        expectWithMessage("Limitation state after end of combo")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Attenuation state after end of combo")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Gain index after end of combo")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(limitation);
    }

    @Test
    public void onAudioGainChanged_comboAttenuationLimitationLowerLimit_attenuationEndsFirst() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        int limitation = DEFAULT_GAIN_INDEX - 1;
        int comboLimitAttenuation = DEFAULT_GAIN_INDEX;
        int initialIndex = MAX_GAIN_INDEX;
        carVolumeGroup.setCurrentGainIndex(initialIndex);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = comboLimitAttenuation;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        // COMBO ATTENUATION + LIMITATION, gain is the attenuation / limit
        List<Integer> comboReasons = List.of(Reasons.THERMAL_LIMITATION, Reasons.ADAS_DUCKING);
        carVolumeGroup.onAudioGainChanged(comboReasons, musicCarGain);

        // End of Attenuation first
        musicGain.volumeIndex = limitation;
        musicCarGain = new CarAudioGainConfigInfo(musicGain);
        List<Integer> limitationReasons = List.of(Reasons.THERMAL_LIMITATION);
        carVolumeGroup.onAudioGainChanged(limitationReasons, musicCarGain);

        expectWithMessage("Limitation state after end of combo")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Attenuation state after end of combo")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Gain index after end of combo")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(limitation);
    }

    @Test
    public void onAudioGainChanged_endOfRestrictions_whileComboAndattenuationEndsFirst() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        int limitation = DEFAULT_GAIN_INDEX;
        int comboLimitAttenuation = DEFAULT_GAIN_INDEX - 1;
        int initialIndex = MAX_GAIN_INDEX;
        carVolumeGroup.setCurrentGainIndex(initialIndex);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = comboLimitAttenuation;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        // COMBO ATTENUATION + LIMITATION, gain is the attenuation / limit
        List<Integer> comboReasons = List.of(Reasons.THERMAL_LIMITATION, Reasons.ADAS_DUCKING);
        carVolumeGroup.onAudioGainChanged(comboReasons, musicCarGain);
        // End of Attenuation first
        musicGain.volumeIndex = limitation;
        musicCarGain = new CarAudioGainConfigInfo(musicGain);
        List<Integer> limitationReasons = List.of(Reasons.THERMAL_LIMITATION);
        carVolumeGroup.onAudioGainChanged(limitationReasons, musicCarGain);

        // End of restrictions
        List<Integer> noReasons = new ArrayList<>(0);
        carVolumeGroup.onAudioGainChanged(noReasons, musicCarGain);

        expectWithMessage("Attenuation state after end of restrictions")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limitation state after end of restrictions")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Gain index after end of restrictions")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(initialIndex);
    }

    @Test
    public void onAudioGainChanged_comboAttenuationLimitation_limitationEndsFirst() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        int initialIndex = MAX_GAIN_INDEX;
        int comboLimitAttenuation = DEFAULT_GAIN_INDEX - 1;
        carVolumeGroup.setCurrentGainIndex(initialIndex);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = comboLimitAttenuation;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        // COMBO ATTENUATION + LIMITATION, gain is the attenuation / new limit
        List<Integer> comboReasons = List.of(Reasons.THERMAL_LIMITATION, Reasons.ADAS_DUCKING);
        carVolumeGroup.onAudioGainChanged(comboReasons, musicCarGain);

        // End of limitation first, lets change the attenuation also (higher than previous limit)
        int attenuation = comboLimitAttenuation + 1;
        musicGain.volumeIndex = attenuation;
        musicCarGain = new CarAudioGainConfigInfo(musicGain);
        List<Integer> attenuationReasons = List.of(Reasons.ADAS_DUCKING);
        carVolumeGroup.onAudioGainChanged(attenuationReasons, musicCarGain);

        expectWithMessage("Limitation state after end of combo")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Attenuation state after end of combo")
                .that(carVolumeGroup.isAttenuated()).isTrue();
        expectWithMessage("Gain index after combo")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(attenuation);
    }

    @Test
    public void onAudioGainChanged_endOfRestriction_afterComboAndlimitationEndsFirst() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        int initialIndex = MAX_GAIN_INDEX;
        int comboLimitAttenuation = DEFAULT_GAIN_INDEX - 1;
        carVolumeGroup.setCurrentGainIndex(initialIndex);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = comboLimitAttenuation;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        // COMBO ATTENUATION + LIMITATION, gain is the attenuation / new limit
        List<Integer> comboReasons = List.of(Reasons.THERMAL_LIMITATION, Reasons.ADAS_DUCKING);
        carVolumeGroup.onAudioGainChanged(comboReasons, musicCarGain);
        // End of limitation first, lets change the attenuation also (higher than previous limit)
        int attenuation = comboLimitAttenuation + 1;
        musicGain.volumeIndex = attenuation;
        musicCarGain = new CarAudioGainConfigInfo(musicGain);
        List<Integer> attenuationReasons = List.of(Reasons.ADAS_DUCKING);
        carVolumeGroup.onAudioGainChanged(attenuationReasons, musicCarGain);

        // End of restrictions
        List<Integer> noReasons = new ArrayList<>(0);
        carVolumeGroup.onAudioGainChanged(noReasons, musicCarGain);

        expectWithMessage("Attenuation state after end of restrictions")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limitation state after end of restrictions")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Gain index after end of restrictions")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(initialIndex);
    }

    @Test
    public void getCarVolumeGroupInfo() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(0);

        CarVolumeGroupInfo info = carVolumeGroup.getCarVolumeGroupInfo();

        expectWithMessage("Car volume group info id")
                .that(info.getId()).isEqualTo(ZONE_ID);
        expectWithMessage("Car volume group info zone id")
                .that(info.getId()).isEqualTo(GROUP_ID);
        expectWithMessage("Car volume group info current gain")
                .that(info.getVolumeGainIndex()).isEqualTo(carVolumeGroup.getCurrentGainIndex());
        expectWithMessage("Car volume group info max gain")
                .that(info.getMaxVolumeGainIndex()).isEqualTo(carVolumeGroup.getMaxGainIndex());
        expectWithMessage("Car volume group info min gain")
                .that(info.getMinVolumeGainIndex()).isEqualTo(carVolumeGroup.getMinGainIndex());
        expectWithMessage("Car volume group info muted state")
                .that(info.isMuted()).isEqualTo(carVolumeGroup.isMuted());
        expectWithMessage("Car volume group info blocked state")
                .that(info.isBlocked()).isEqualTo(carVolumeGroup.isBlocked());
        expectWithMessage("Car volume group info attenuated state")
                .that(info.isAttenuated()).isEqualTo(carVolumeGroup.isAttenuated());
    }

    @Test
    public void getCarVolumeGroupInfo_withMinMaxActivationVolumeEnabled() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CAR_AUDIO_MIN_MAX_ACTIVATION_VOLUME);

        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(0);

        CarVolumeGroupInfo info = carVolumeGroup.getCarVolumeGroupInfo();

        expectWithMessage("Car volume group info min activation gain")
                .that(info.getMinActivationVolumeGainIndex())
                .isEqualTo(carVolumeGroup.getMinActivationGainIndex());
        expectWithMessage("Car volume group info max activation gain")
                .that(info.getMaxActivationVolumeGainIndex())
                .isEqualTo(carVolumeGroup.getMaxActivationGainIndex());
    }

    @Test
    public void getAudioAttributes() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();

        List<AudioAttributes> audioAttributes = carVolumeGroup.getAudioAttributes();

        expectWithMessage("Group audio attributes").that(audioAttributes).containsExactly(
                CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA),
                CarAudioContext.getAudioAttributeFromUsage(USAGE_GAME),
                CarAudioContext.getAudioAttributeFromUsage(USAGE_UNKNOWN));
    }

    @Test
    public void hasAudioAttributes_withAttributesInVolumeGroup() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();

        expectWithMessage("Game attributes in volume group").that(carVolumeGroup
                        .hasAudioAttributes(CarAudioContext.getAudioAttributeFromUsage(USAGE_GAME)))
                .isTrue();
    }

    @Test
    public void hasAudioAttributes_withAttributesNotInVolumeGroup() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();

        expectWithMessage("Notification attributes not in volume group").that(carVolumeGroup
                .hasAudioAttributes(CarAudioContext.getAudioAttributeFromUsage(
                        USAGE_NOTIFICATION))).isFalse();
    }


    @Test
    public void getAllSupportedUsagesForAddress() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        List<Integer> supportedUsagesForMediaAddress =
                carVolumeGroup.getAllSupportedUsagesForAddress(mMediaDeviceInfo.getAddress());

        List<Integer> expectedUsagesForMediaAddress = List.of(USAGE_MEDIA, USAGE_GAME,
                USAGE_UNKNOWN, USAGE_VOICE_COMMUNICATION, USAGE_CALL_ASSISTANT,
                USAGE_VOICE_COMMUNICATION_SIGNALLING, USAGE_NOTIFICATION_RINGTONE);
        expectWithMessage("Usages for media (%s)", expectedUsagesForMediaAddress)
                .that(supportedUsagesForMediaAddress)
                .containsExactlyElementsIn(expectedUsagesForMediaAddress);

        List<Integer> supportedUsagesForNavAddress =
                carVolumeGroup.getAllSupportedUsagesForAddress(mNavigationDeviceInfo.getAddress());

        List<Integer> expectedUsagesForNavAddress = List.of(
                USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, USAGE_ALARM, USAGE_NOTIFICATION,
                USAGE_NOTIFICATION_EVENT);
        expectWithMessage("Usages for nav (%s)", expectedUsagesForNavAddress)
                .that(supportedUsagesForNavAddress)
                .containsExactlyElementsIn(expectedUsagesForNavAddress);
    }

    @Test
    public void isActive() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        expectWithMessage("Active status").that(carVolumeGroup.isActive()).isTrue();
    }

    @Test
    public void isActive_withInactiveGroup() {
        CarVolumeGroup carVolumeGroup = testDynamicVolumeGroupSetup();

        expectWithMessage("Active status, with inactive group")
                .that(carVolumeGroup.isActive()).isFalse();
    }

    @Test
    public void validateDeviceTypes_forGroupWithNoDynamicDevices() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        ArraySet<Integer> existingDevices = new ArraySet<>();

        expectWithMessage("Status for valid volume group with BUS devices")
                .that(carVolumeGroup.validateDeviceTypes(existingDevices)).isTrue();
        expectWithMessage("Dynamic devices for group").that(existingDevices).isEmpty();
    }

    @Test
    public void validateDeviceTypes_forGroupWithNoDynamicDevices_andExistingBusDevice() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        ArraySet<Integer> existingDevices = new ArraySet<>();
        existingDevices.add(TYPE_BUS);

        expectWithMessage("Status for valid volume group with BUS devices and existing BUS device")
                .that(carVolumeGroup.validateDeviceTypes(existingDevices)).isTrue();
        expectWithMessage("Dynamic devices for group with BUS device").that(existingDevices)
                .containsExactly(TYPE_BUS);
    }

    @Test
    public void validateDeviceTypes_forGroupWithDynamicTypes() {
        CarVolumeGroup carVolumeGroup = testDynamicVolumeGroupSetup();
        ArraySet<Integer> existingDevices = new ArraySet<>();

        expectWithMessage("Status for valid volume group with dynamic device")
                .that(carVolumeGroup.validateDeviceTypes(existingDevices)).isTrue();
        expectWithMessage("Dynamic devices for group with dynamic device")
                .that(existingDevices).containsExactly(TYPE_BLUETOOTH_A2DP);
    }

    @Test
    public void validateDeviceTypes_forGroupWithDynamicDeviceAndBus() {
        CarVolumeGroup carVolumeGroup = testInactiveVolumeGroupSetup();
        ArraySet<Integer> existingDevices = new ArraySet<>();

        expectWithMessage("Status for invalid volume group with dynamic device")
                .that(carVolumeGroup.validateDeviceTypes(existingDevices)).isFalse();
        expectWithMessage("Dynamic devices for group with dynamic device and bus device")
                .that(existingDevices).containsExactly(TYPE_BLUETOOTH_A2DP);
    }

    @Test
    public void validateDeviceTypes_forGroupWithAlreadyExistingDynamicType() {
        CarVolumeGroup carVolumeGroup = testDynamicVolumeGroupSetup();
        ArraySet<Integer> existingDevices = new ArraySet<>();
        existingDevices.add(TYPE_BLUETOOTH_A2DP);

        expectWithMessage("Status for invalid volume group with existing dynamic device")
                .that(carVolumeGroup.validateDeviceTypes(existingDevices)).isFalse();
        expectWithMessage("Existing dynamic devices for group with dynamic device")
                .that(existingDevices).containsExactly(TYPE_BLUETOOTH_A2DP);
    }

    private CarVolumeGroup getCarVolumeGroupWithMusicBound() {
        CarVolumeGroupFactory factory = getFactory(/* useCarVolumeGroupMute= */ true);
        factory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        return factory.getCarVolumeGroup(/* useCoreAudioVolume= */ false);
    }

    private CarVolumeGroup getCarVolumeGroupWithNavigationBound(CarAudioSettings settings,
            boolean useCarVolumeGroupMute) {
        CarVolumeGroupFactory factory =  new CarVolumeGroupFactory(mAudioManagerMock, settings,
                TEST_CAR_AUDIO_CONTEXT, ZONE_ID, CONFIG_ID, GROUP_ID, /* name= */ "0",
                useCarVolumeGroupMute, MAX_ACTIVATION_GAIN_INDEX_PERCENTAGE,
                MIN_ACTIVATION_GAIN_INDEX_PERCENTAGE);
        factory.setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, mNavigationDeviceInfo);
        return factory.getCarVolumeGroup(/* useCoreAudioVolume= */ false);
    }

    CarVolumeGroup getVolumeGroupWithMuteAndNavBound(boolean isMuted, boolean persistMute,
            boolean useCarVolumeGroupMute) {
        CarAudioSettings settings = new SettingsBuilder(ZONE_ID, CONFIG_ID, GROUP_ID)
                .setMuteForUser10(isMuted)
                .setIsPersistVolumeGroupEnabled(persistMute)
                .build();
        return getCarVolumeGroupWithNavigationBound(settings, useCarVolumeGroupMute);
    }

    private CarVolumeGroup testVolumeGroupSetup() {
        return testVolumeGroupSetup(/* useCarVolumeGroupMute= */ true);
    }

    private CarVolumeGroup testInactiveVolumeGroupSetup() {
        CarVolumeGroupFactory factory = getFactory(/* useCarVolumeGroupMute= */ true);

        factory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mInactiveMediaDeviceInfo);
        factory.setDeviceInfoForContext(TEST_CALL_CONTEXT_ID, mInactiveMediaDeviceInfo);
        factory.setDeviceInfoForContext(TEST_CALL_RING_CONTEXT_ID, mInactiveMediaDeviceInfo);

        factory.setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, mNavigationDeviceInfo);
        factory.setDeviceInfoForContext(TEST_ALARM_CONTEXT_ID, mNavigationDeviceInfo);
        factory.setDeviceInfoForContext(TEST_NOTIFICATION_CONTEXT_ID, mNavigationDeviceInfo);

        return factory.getCarVolumeGroup(/* useCoreAudioVolume= */ false);
    }

    private CarVolumeGroup testDynamicVolumeGroupSetup() {
        CarVolumeGroupFactory factory = getFactory(/* useCarVolumeGroupMute= */ true);

        factory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mInactiveMediaDeviceInfo);
        factory.setDeviceInfoForContext(TEST_CALL_CONTEXT_ID, mInactiveMediaDeviceInfo);
        factory.setDeviceInfoForContext(TEST_CALL_RING_CONTEXT_ID, mInactiveMediaDeviceInfo);

        return factory.getCarVolumeGroup(/* useCoreAudioVolume= */ false);
    }

    private CarVolumeGroup testVolumeGroupSetup(boolean useCarVolumeGroupMute) {
        CarVolumeGroupFactory factory = getFactory(useCarVolumeGroupMute);

        factory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        factory.setDeviceInfoForContext(TEST_CALL_CONTEXT_ID, mMediaDeviceInfo);
        factory.setDeviceInfoForContext(TEST_CALL_RING_CONTEXT_ID, mMediaDeviceInfo);

        factory.setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, mNavigationDeviceInfo);
        factory.setDeviceInfoForContext(TEST_ALARM_CONTEXT_ID, mNavigationDeviceInfo);
        factory.setDeviceInfoForContext(TEST_NOTIFICATION_CONTEXT_ID, mNavigationDeviceInfo);

        return factory.getCarVolumeGroup(/* useCoreAudioVolume= */ false);
    }

    CarVolumeGroupFactory getFactory(boolean useCarVolumeGroupMute) {
        return new CarVolumeGroupFactory(mAudioManagerMock, mSettingsMock, TEST_CAR_AUDIO_CONTEXT,
                ZONE_ID, CONFIG_ID, GROUP_ID, GROUP_NAME, useCarVolumeGroupMute,
                MAX_ACTIVATION_GAIN_INDEX_PERCENTAGE, MIN_ACTIVATION_GAIN_INDEX_PERCENTAGE);
    }

    private static final class SettingsBuilder {
        private final SparseIntArray mStoredGainIndexes = new SparseIntArray();
        private final SparseBooleanArray mStoreMuteStates = new SparseBooleanArray();
        private final int mZoneId;
        private final int mConfigId;
        private final int mGroupId;

        private boolean mPersistMute;

        SettingsBuilder(int zoneId, int configId, int groupId) {
            mZoneId = zoneId;
            mConfigId = configId;
            mGroupId = groupId;
        }

        SettingsBuilder setGainIndexForUser(@UserIdInt int userId) {
            mStoredGainIndexes.put(userId, TEST_GAIN_INDEX);
            return this;
        }

        SettingsBuilder setMuteForUser10(boolean mute) {
            mStoreMuteStates.put(CarVolumeGroupUnitTest.TEST_USER_10, mute);
            return this;
        }

        SettingsBuilder setIsPersistVolumeGroupEnabled(boolean persistMute) {
            mPersistMute = persistMute;
            return this;
        }

        CarAudioSettings build() {
            CarAudioSettings settingsMock = Mockito.mock(CarAudioSettings.class);
            for (int storeIndex = 0; storeIndex < mStoredGainIndexes.size(); storeIndex++) {
                int gainUserId = mStoredGainIndexes.keyAt(storeIndex);
                when(settingsMock
                        .getStoredVolumeGainIndexForUser(gainUserId, mZoneId, mConfigId,
                                mGroupId)).thenReturn(mStoredGainIndexes.get(gainUserId,
                        TestCarAudioDeviceInfoBuilder.DEFAULT_GAIN));
            }
            for (int muteIndex = 0; muteIndex < mStoreMuteStates.size(); muteIndex++) {
                int muteUserId = mStoreMuteStates.keyAt(muteIndex);
                when(settingsMock.getVolumeGroupMuteForUser(muteUserId, mZoneId, mConfigId,
                        mGroupId)).thenReturn(mStoreMuteStates.get(muteUserId,
                        /* valueIfKeyNotFound= */ false));
                when(settingsMock.isPersistVolumeGroupMuteEnabled(muteUserId))
                        .thenReturn(mPersistMute);
            }
            return settingsMock;
        }
    }
}
