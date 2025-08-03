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

import static android.media.AudioAttributes.USAGE_ALARM;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_NOTIFICATION;
import static android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;
import static android.media.audio.common.AudioDeviceDescription.CONNECTION_BUS;
import static android.media.audio.common.AudioDeviceType.OUT_DEVICE;
import static android.media.audio.common.AudioGainMode.JOINT;

import static com.android.car.audio.GainBuilder.DEFAULT_GAIN;
import static com.android.car.audio.GainBuilder.MAX_GAIN;
import static com.android.car.audio.GainBuilder.MIN_GAIN;
import static com.android.car.audio.GainBuilder.STEP_SIZE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import android.annotation.UserIdInt;
import android.car.builtin.media.AudioManagerHelper;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.hardware.automotive.audiocontrol.AudioGainConfigInfo;
import android.hardware.automotive.audiocontrol.Reasons;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioGain;
import android.media.AudioManager;
import android.media.audio.common.AudioDevice;
import android.media.audio.common.AudioDeviceAddress;
import android.media.audio.common.AudioDeviceDescription;
import android.media.audio.common.AudioPort;
import android.media.audio.common.AudioPortDeviceExt;
import android.media.audio.common.AudioPortExt;
import android.os.UserHandle;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;

import com.android.car.audio.hal.HalAudioDeviceInfo;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public final class CarAudioVolumeGroupTest extends AbstractExtendedMockitoTestCase {
    private static final int ZONE_ID = 0;
    private static final int ZONE_CONFIG_ID = 1;
    private static final int GROUP_ID = 0;
    private static final int DEFAULT_GAIN_INDEX = getIndexForGain(MIN_GAIN, STEP_SIZE,
            DEFAULT_GAIN);
    private static final int MIN_GAIN_INDEX = 0;
    private static final int MAX_GAIN_INDEX = getIndexForGain(MIN_GAIN, STEP_SIZE, MAX_GAIN);
    private static final int MIN_ACTIVATION_GAIN_INDEX_PERCENTAGE = 10;
    private static final int MAX_ACTIVATION_GAIN_INDEX_PERCENTAGE = 90;
    private static final int TEST_GAIN_INDEX = 35;
    private static final int TEST_USER_11 = 11;
    private static final String GROUP_NAME = "group_0";
    private static final String MEDIA_DEVICE_ADDRESS = "music";
    private static final String NAVIGATION_DEVICE_ADDRESS = "navigation";
    private static final int MEDIA_PORT_ID = 0;
    private static final int NAV_PORT_ID = 1;
    private static final String TEST_MEDIA_PORT_NAME = "Media bus";
    private static final String TEST_NAV_PORT_NAME = "Nav bus";
    private static final int TEST_GAIN_MIN_VALUE = -3000;
    private static final int TEST_GAIN_MAX_VALUE = -1000;
    private static final int TEST_GAIN_DEFAULT_VALUE = -2000;
    private static final int TEST_GAIN_STEP_VALUE = 2;
    private static final int TEST_GAIN_MIN_INDEX = 0;
    private static final int TEST_GAIN_MAX_INDEX = getIndexForGain(TEST_GAIN_MIN_VALUE,
            TEST_GAIN_STEP_VALUE, TEST_GAIN_MAX_VALUE);
    private static final int TEST_GAIN_DEFAULT_INDEX = getIndexForGain(TEST_GAIN_MIN_VALUE,
            TEST_GAIN_STEP_VALUE, TEST_GAIN_DEFAULT_VALUE);
    private static final int EVENT_TYPE_VOLUME_INDEX_MIN_MAX = 0x7;
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
    private static final @CarAudioContext.AudioContext int TEST_NAVIGATION_CONTEXT_ID =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE));
    private static final @CarAudioContext.AudioContext int TEST_NOTIFICATION_CONTEXT_ID =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_NOTIFICATION));

    private CarAudioDeviceInfo mMediaDeviceInfo;
    private CarAudioDeviceInfo mNavigationDeviceInfo;

    @Mock
    private AudioManager mAudioManager;

    @Mock
    CarAudioSettings mSettingsMock;

    @Rule
    public final Expect expect = Expect.create();

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(AudioManager.class)
                .spyStatic(AudioManagerHelper.class);
    }

    @Before
    public void setUp() {
        mMediaDeviceInfo = new CarAudioDeviceInfo(mAudioManager,
                getMockAudioDevice(MEDIA_DEVICE_ADDRESS));
        mMediaDeviceInfo.setAudioDeviceInfo(getMockAudioDeviceInfo(MEDIA_DEVICE_ADDRESS));
        mNavigationDeviceInfo = new CarAudioDeviceInfo(mAudioManager,
                getMockAudioDevice(NAVIGATION_DEVICE_ADDRESS));
        mNavigationDeviceInfo.setAudioDeviceInfo(getMockAudioDeviceInfo(NAVIGATION_DEVICE_ADDRESS));
    }

    @Test
    public void testInitializedIndexes() {
        CarAudioVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        expect.withMessage("Min index").that(carVolumeGroup.getMinGainIndex())
                .isEqualTo(MIN_GAIN_INDEX);
        expect.withMessage("Max index").that(carVolumeGroup.getMaxGainIndex())
                .isEqualTo(MAX_GAIN_INDEX);
        expect.withMessage("Default index").that(carVolumeGroup.getDefaultGainIndex())
                .isEqualTo(DEFAULT_GAIN_INDEX);
    }

    @Test
    public void checkValidGainIndexes() {
        CarAudioVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        expect.withMessage("Min index").that(carVolumeGroup.isValidGainIndex(MIN_GAIN_INDEX))
                .isTrue();
        expect.withMessage("Max index").that(carVolumeGroup.isValidGainIndex(MAX_GAIN_INDEX))
                .isTrue();
        expect.withMessage("Default index")
                .that(carVolumeGroup.isValidGainIndex(DEFAULT_GAIN_INDEX))
                .isTrue();

        expect.withMessage("Outside range index")
                .that(carVolumeGroup.isValidGainIndex(MIN_GAIN_INDEX - 1))
                .isFalse();
        expect.withMessage("Outside range index")
                .that(carVolumeGroup.isValidGainIndex(MAX_GAIN_INDEX + 1))
                .isFalse();
    }

    @Test
    public void setCurrentGainIndex_setsGainOnAllBoundDevices() {
        CarAudioVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);

        expect.withMessage("Current gain index for bound devices")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(TEST_GAIN_INDEX);
    }

    @Test
    public void setCurrentGainIndex_updatesCurrentGainIndex() {
        CarAudioVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);

        verify(mSettingsMock).storeVolumeGainIndexForUser(anyInt(), eq(ZONE_ID), eq(ZONE_CONFIG_ID),
                eq(GROUP_ID), eq(TEST_GAIN_INDEX));
    }

    @Test
    public void setCurrentGainIndex_setsCurrentGainIndexForUser() {
        CarAudioSettings settings = new SettingsBuilder(ZONE_ID, ZONE_CONFIG_ID, GROUP_ID)
                .setGainIndexForUser(TEST_USER_11).build();
        CarAudioVolumeGroup carVolumeGroup = testVolumeGroupSetup(settings);
        carVolumeGroup.loadVolumesSettingsForUser(TEST_USER_11);

        carVolumeGroup.setCurrentGainIndex(MIN_GAIN);

        verify(settings).storeVolumeGainIndexForUser(TEST_USER_11, ZONE_ID, ZONE_CONFIG_ID,
                GROUP_ID, MIN_GAIN);
    }

    @Test
    public void setCurrentGainIndex_setsCurrentGainIndexForDefaultUser() {
        CarAudioSettings settings = new SettingsBuilder(ZONE_ID, ZONE_CONFIG_ID, GROUP_ID)
                .setGainIndexForUser(UserHandle.USER_CURRENT).build();
        CarAudioVolumeGroup carVolumeGroup = testVolumeGroupSetup(settings);

        carVolumeGroup.setCurrentGainIndex(MIN_GAIN);

        verify(settings)
                .storeVolumeGainIndexForUser(UserHandle.USER_CURRENT, ZONE_ID, ZONE_CONFIG_ID,
                        GROUP_ID, MIN_GAIN);
    }

    @Test
    public void updateAudioDeviceInfo_succeeds() {
        CarAudioVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        HalAudioDeviceInfo mediaBusDeviceInfo = createHalAudioDeviceInfo(
                MEDIA_PORT_ID, TEST_MEDIA_PORT_NAME, TEST_GAIN_MIN_VALUE, TEST_GAIN_MAX_VALUE,
                TEST_GAIN_DEFAULT_VALUE, TEST_GAIN_STEP_VALUE, OUT_DEVICE, MEDIA_DEVICE_ADDRESS);
        HalAudioDeviceInfo navBusDeviceInfo = createHalAudioDeviceInfo(
                NAV_PORT_ID, TEST_NAV_PORT_NAME, TEST_GAIN_MIN_VALUE, TEST_GAIN_MAX_VALUE,
                TEST_GAIN_DEFAULT_VALUE, TEST_GAIN_STEP_VALUE, OUT_DEVICE,
                NAVIGATION_DEVICE_ADDRESS);

        carVolumeGroup.updateAudioDeviceInfo(mediaBusDeviceInfo);
        carVolumeGroup.updateAudioDeviceInfo(navBusDeviceInfo);

        expect.withMessage("Media device info min gain").that(mMediaDeviceInfo.getMinGain())
                .isEqualTo(TEST_GAIN_MIN_VALUE);
        expect.withMessage("Media device info max gain").that(mMediaDeviceInfo.getMaxGain())
                .isEqualTo(TEST_GAIN_MAX_VALUE);
        expect.withMessage("Media device info step value").that(mMediaDeviceInfo.getStepValue())
                .isEqualTo(TEST_GAIN_STEP_VALUE);
        expect.withMessage("Media device info default gain").that(mMediaDeviceInfo.getDefaultGain())
                .isEqualTo(TEST_GAIN_DEFAULT_VALUE);
        expect.withMessage("Nav device info min gain").that(mNavigationDeviceInfo.getMinGain())
                .isEqualTo(TEST_GAIN_MIN_VALUE);
        expect.withMessage("Nav device info max gain").that(mNavigationDeviceInfo.getMaxGain())
                .isEqualTo(TEST_GAIN_MAX_VALUE);
        expect.withMessage("Nav device info step value").that(mNavigationDeviceInfo.getStepValue())
                .isEqualTo(TEST_GAIN_STEP_VALUE);
        expect.withMessage("Nav device info default gain")
                .that(mNavigationDeviceInfo.getDefaultGain()).isEqualTo(TEST_GAIN_DEFAULT_VALUE);
    }

    @Test
    public void updateAudioDeviceInfo_invalidDeviceInfo_doesNotUpdate() {
        CarAudioVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        String invalidMediaDeviceAddress = "Invalid media device";
        String invalidNavDeviceAddress = "Invalid nav device";
        HalAudioDeviceInfo mediaBusDeviceInfo = createHalAudioDeviceInfo(
                MEDIA_PORT_ID, TEST_MEDIA_PORT_NAME, TEST_GAIN_MIN_VALUE, TEST_GAIN_MAX_VALUE,
                TEST_GAIN_DEFAULT_VALUE, TEST_GAIN_STEP_VALUE, OUT_DEVICE,
                invalidMediaDeviceAddress);
        HalAudioDeviceInfo navBusDeviceInfo = createHalAudioDeviceInfo(
                NAV_PORT_ID, TEST_NAV_PORT_NAME, TEST_GAIN_MIN_VALUE, TEST_GAIN_MAX_VALUE,
                TEST_GAIN_DEFAULT_VALUE, TEST_GAIN_STEP_VALUE, OUT_DEVICE, invalidNavDeviceAddress);

        carVolumeGroup.updateAudioDeviceInfo(mediaBusDeviceInfo);
        carVolumeGroup.updateAudioDeviceInfo(navBusDeviceInfo);

        expect.withMessage("Default media device info min gain")
                .that(mMediaDeviceInfo.getMinGain()).isEqualTo(MIN_GAIN);
        expect.withMessage("Default media device info max gain")
                .that(mMediaDeviceInfo.getMaxGain()).isEqualTo(MAX_GAIN);
        expect.withMessage("Default media device info step value")
                .that(mMediaDeviceInfo.getStepValue()).isEqualTo(STEP_SIZE);
        expect.withMessage("Default media device info default gain")
                .that(mMediaDeviceInfo.getDefaultGain()).isEqualTo(DEFAULT_GAIN);
        expect.withMessage("Default nav device info min gain")
                .that(mNavigationDeviceInfo.getMinGain()).isEqualTo(MIN_GAIN);
        expect.withMessage("Default nav device info max gain")
                .that(mNavigationDeviceInfo.getMaxGain()).isEqualTo(MAX_GAIN);
        expect.withMessage("Default nav device info step value")
                .that(mNavigationDeviceInfo.getStepValue()).isEqualTo(STEP_SIZE);
        expect.withMessage("Default nav device info default gain")
                .that(mNavigationDeviceInfo.getDefaultGain()).isEqualTo(DEFAULT_GAIN);
    }

    @Test
    public void calculateNewGainStageFromDeviceInfos_forSameGainStage_returnsNoEventType() {
        CarAudioVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);
        HalAudioDeviceInfo mediaBusDeviceInfo = createHalAudioDeviceInfo(MEDIA_PORT_ID,
                TEST_MEDIA_PORT_NAME, MIN_GAIN, MAX_GAIN, DEFAULT_GAIN, STEP_SIZE,
                OUT_DEVICE, MEDIA_DEVICE_ADDRESS);
        HalAudioDeviceInfo navBusDeviceInfo = createHalAudioDeviceInfo(NAV_PORT_ID,
                TEST_NAV_PORT_NAME, MIN_GAIN, MAX_GAIN, DEFAULT_GAIN, STEP_SIZE,
                OUT_DEVICE, NAVIGATION_DEVICE_ADDRESS);
        carVolumeGroup.updateAudioDeviceInfo(mediaBusDeviceInfo);
        carVolumeGroup.updateAudioDeviceInfo(navBusDeviceInfo);

        int eventType = carVolumeGroup.calculateNewGainStageFromDeviceInfos();

        expect.withMessage("Calculated event type for new gain stage")
                .that(eventType).isEqualTo(EVENT_TYPE_NONE);
        expect.withMessage("Calculated min gain index for new gain stage")
                .that(carVolumeGroup.getMinGainIndex()).isEqualTo(MIN_GAIN_INDEX);
        expect.withMessage("Calculated max gain index for new gain stage")
                .that(carVolumeGroup.getMaxGainIndex()).isEqualTo(MAX_GAIN_INDEX);
        expect.withMessage("Calculated current gain index for new gain stage")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(TEST_GAIN_INDEX);
    }

    @Test
    public void calculateNewGainStageFromDeviceInfos_withOnlyDefaultUpdate_returnsNoEventType() {
        CarAudioVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);
        HalAudioDeviceInfo mediaBusDeviceInfo = createHalAudioDeviceInfo(MEDIA_PORT_ID,
                TEST_MEDIA_PORT_NAME, MIN_GAIN, MAX_GAIN, DEFAULT_GAIN * 2, STEP_SIZE,
                OUT_DEVICE, MEDIA_DEVICE_ADDRESS);
        HalAudioDeviceInfo navBusDeviceInfo = createHalAudioDeviceInfo(NAV_PORT_ID,
                TEST_NAV_PORT_NAME, MIN_GAIN, MAX_GAIN, DEFAULT_GAIN * 2, STEP_SIZE,
                OUT_DEVICE, NAVIGATION_DEVICE_ADDRESS);
        carVolumeGroup.updateAudioDeviceInfo(mediaBusDeviceInfo);
        carVolumeGroup.updateAudioDeviceInfo(navBusDeviceInfo);

        int eventType = carVolumeGroup.calculateNewGainStageFromDeviceInfos();

        expect.withMessage("Calculated event type for new gain stage\"")
                .that(eventType).isEqualTo(EVENT_TYPE_NONE);
        expect.withMessage("Calculated min gain index for new gain stage")
                .that(carVolumeGroup.getMinGainIndex()).isEqualTo(MIN_GAIN_INDEX);
        expect.withMessage("Calculated max gain index for new gain stage")
                .that(carVolumeGroup.getMaxGainIndex()).isEqualTo(MAX_GAIN_INDEX);
        expect.withMessage("Calculated current gain index for new gain stage")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(TEST_GAIN_INDEX);
    }

    @Test
    public void calculateNewGainStageFromDeviceInfos_withDifferentStepValues_throws() {
        CarAudioVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);
        HalAudioDeviceInfo mediaBusDeviceInfo = createHalAudioDeviceInfo(MEDIA_PORT_ID,
                TEST_MEDIA_PORT_NAME, MIN_GAIN, MAX_GAIN, DEFAULT_GAIN, STEP_SIZE,
                OUT_DEVICE, MEDIA_DEVICE_ADDRESS);
        HalAudioDeviceInfo navBusDeviceInfo = createHalAudioDeviceInfo(NAV_PORT_ID,
                TEST_NAV_PORT_NAME, MIN_GAIN, MAX_GAIN, DEFAULT_GAIN, STEP_SIZE - 1,
                OUT_DEVICE, NAVIGATION_DEVICE_ADDRESS);
        carVolumeGroup.updateAudioDeviceInfo(mediaBusDeviceInfo);
        carVolumeGroup.updateAudioDeviceInfo(navBusDeviceInfo);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> carVolumeGroup.calculateNewGainStageFromDeviceInfos());

        expect.withMessage("Calculation of new gain stage throws exception")
                .that(thrown).hasMessageThat()
                .contains("Gain stages within one group must have same step value");
    }

    @Test
    public void calculateNewGainStageFromDeviceInfos_extrapolationSucceeds() {
        int newMinGain = MIN_GAIN + STEP_SIZE * 2;
        int newMaxGain = MAX_GAIN - STEP_SIZE * 2;
        CarAudioVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);
        HalAudioDeviceInfo mediaBusDeviceInfo = createHalAudioDeviceInfo(MEDIA_PORT_ID,
                TEST_MEDIA_PORT_NAME, newMinGain, newMaxGain, DEFAULT_GAIN, STEP_SIZE,
                OUT_DEVICE, MEDIA_DEVICE_ADDRESS);
        HalAudioDeviceInfo navBusDeviceInfo = createHalAudioDeviceInfo(NAV_PORT_ID,
                TEST_NAV_PORT_NAME, newMinGain, newMaxGain, DEFAULT_GAIN, STEP_SIZE,
                OUT_DEVICE, NAVIGATION_DEVICE_ADDRESS);
        carVolumeGroup.updateAudioDeviceInfo(mediaBusDeviceInfo);
        carVolumeGroup.updateAudioDeviceInfo(navBusDeviceInfo);

        int eventType = carVolumeGroup.calculateNewGainStageFromDeviceInfos();

        expect.withMessage("Calculated event type for new gain stage")
                .that(eventType).isEqualTo(EVENT_TYPE_VOLUME_INDEX_MIN_MAX);
        expect.withMessage("Calculated min gain index for new gain stage")
                .that(carVolumeGroup.getMinGainIndex()).isEqualTo(MIN_GAIN_INDEX);
        expect.withMessage("Calculated max gain index for new gain stage")
                .that(carVolumeGroup.getMaxGainIndex())
                .isEqualTo(getIndexForGain(newMinGain, STEP_SIZE, newMaxGain));
        expect.withMessage("Calculated current gain index for new gain stage")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(getExtrapolatedGainIndex(MIN_GAIN, newMinGain, STEP_SIZE,
                        TEST_GAIN_INDEX));
    }

    @Test
    public void calculateNewGainStageFromDeviceInfos_extrapolationFails_setsToDefaultValue() {
        CarAudioVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        HalAudioDeviceInfo mediaBusDeviceInfo = createHalAudioDeviceInfo(
                MEDIA_PORT_ID, TEST_MEDIA_PORT_NAME, TEST_GAIN_MIN_VALUE, TEST_GAIN_MAX_VALUE,
                TEST_GAIN_DEFAULT_VALUE, TEST_GAIN_STEP_VALUE, OUT_DEVICE, MEDIA_DEVICE_ADDRESS);
        HalAudioDeviceInfo navBusDeviceInfo = createHalAudioDeviceInfo(
                NAV_PORT_ID, TEST_NAV_PORT_NAME, TEST_GAIN_MIN_VALUE, TEST_GAIN_MAX_VALUE,
                TEST_GAIN_DEFAULT_VALUE, TEST_GAIN_STEP_VALUE, OUT_DEVICE,
                NAVIGATION_DEVICE_ADDRESS);
        carVolumeGroup.updateAudioDeviceInfo(mediaBusDeviceInfo);
        carVolumeGroup.updateAudioDeviceInfo(navBusDeviceInfo);

        int eventType = carVolumeGroup.calculateNewGainStageFromDeviceInfos();

        expect.withMessage("Calculated event type for new gain stage")
                .that(eventType).isEqualTo(EVENT_TYPE_VOLUME_INDEX_MIN_MAX);
        expect.withMessage("Calculated min gain index for new gain stage")
                .that(carVolumeGroup.getMinGainIndex()).isEqualTo(TEST_GAIN_MIN_INDEX);
        expect.withMessage("Calculated max gain index for new gain stage")
                .that(carVolumeGroup.getMaxGainIndex()).isEqualTo(TEST_GAIN_MAX_INDEX);
        expect.withMessage("Calculated current gain index for new gain stage")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(TEST_GAIN_DEFAULT_INDEX);
    }

    @Test
    public void calculateNewGainStageFromDeviceInfos_whenBlockingActive_extrapolationSucceeds() {
        int newMinGain = MIN_GAIN + STEP_SIZE * 2;
        int newMaxGain = MAX_GAIN - STEP_SIZE * 2;
        CarAudioVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(getIndexForGain(MIN_GAIN, STEP_SIZE, newMaxGain));
        List<Integer> reasons = List.of(Reasons.FORCED_MASTER_MUTE);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = TEST_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(reasons, musicCarGain);
        HalAudioDeviceInfo mediaBusDeviceInfo = createHalAudioDeviceInfo(MEDIA_PORT_ID,
                TEST_MEDIA_PORT_NAME, newMinGain, newMaxGain, DEFAULT_GAIN, STEP_SIZE,
                OUT_DEVICE, MEDIA_DEVICE_ADDRESS);
        HalAudioDeviceInfo navBusDeviceInfo = createHalAudioDeviceInfo(NAV_PORT_ID,
                TEST_NAV_PORT_NAME, newMinGain, newMaxGain, DEFAULT_GAIN, STEP_SIZE,
                OUT_DEVICE, NAVIGATION_DEVICE_ADDRESS);
        carVolumeGroup.updateAudioDeviceInfo(mediaBusDeviceInfo);
        carVolumeGroup.updateAudioDeviceInfo(navBusDeviceInfo);

        int eventType = carVolumeGroup.calculateNewGainStageFromDeviceInfos();

        expect.withMessage("Calculated event type for new gain stage")
                .that(eventType).isEqualTo(EVENT_TYPE_VOLUME_INDEX_MIN_MAX);
        expect.withMessage("Calculated current gain index for new gain stage")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(getExtrapolatedGainIndex(MIN_GAIN, newMinGain, STEP_SIZE,
                        TEST_GAIN_INDEX));
        expect.withMessage("Volume group blocking state")
                .that(carVolumeGroup.isBlocked()).isTrue();
    }

    @Test
    public void calculateNewGainStageFromDeviceInfos_whenLimitingActive_extrapolationSucceeds() {
        int newMinGain = MIN_GAIN + STEP_SIZE * 2;
        int newMaxGain = MAX_GAIN - STEP_SIZE * 2;
        CarAudioVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(getIndexForGain(MIN_GAIN, STEP_SIZE, newMaxGain));
        List<Integer> reasons = List.of(Reasons.THERMAL_LIMITATION);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = TEST_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(reasons, musicCarGain);
        HalAudioDeviceInfo mediaBusDeviceInfo = createHalAudioDeviceInfo(MEDIA_PORT_ID,
                TEST_MEDIA_PORT_NAME, newMinGain, newMaxGain, DEFAULT_GAIN, STEP_SIZE,
                OUT_DEVICE, MEDIA_DEVICE_ADDRESS);
        HalAudioDeviceInfo navBusDeviceInfo = createHalAudioDeviceInfo(NAV_PORT_ID,
                TEST_NAV_PORT_NAME, newMinGain, newMaxGain, DEFAULT_GAIN, STEP_SIZE,
                OUT_DEVICE, NAVIGATION_DEVICE_ADDRESS);
        carVolumeGroup.updateAudioDeviceInfo(mediaBusDeviceInfo);
        carVolumeGroup.updateAudioDeviceInfo(navBusDeviceInfo);

        int eventType = carVolumeGroup.calculateNewGainStageFromDeviceInfos();

        expect.withMessage("Calculated event type for new gain stage")
                .that(eventType).isEqualTo(EVENT_TYPE_VOLUME_INDEX_MIN_MAX);
        expect.withMessage("Calculated current gain index for new gain stage")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(getExtrapolatedGainIndex(MIN_GAIN, newMinGain, STEP_SIZE,
                        TEST_GAIN_INDEX));
        expect.withMessage("Volume group limitation state")
                .that(carVolumeGroup.isLimited()).isTrue();
        expect.withMessage("Volume group over-limitation state")
                .that(carVolumeGroup.isOverLimit()).isTrue();
    }

    @Test
    public void calculateNewGainStageFromDeviceInfos_whenAttenuationActive_extrapolationSucceeds() {
        int newMinGain = MIN_GAIN + STEP_SIZE * 2;
        int newMaxGain = MAX_GAIN - STEP_SIZE * 2;
        CarAudioVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(getIndexForGain(MIN_GAIN, STEP_SIZE, newMaxGain));
        List<Integer> reasons = List.of(Reasons.NAV_DUCKING);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = TEST_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(reasons, musicCarGain);
        HalAudioDeviceInfo mediaBusDeviceInfo = createHalAudioDeviceInfo(MEDIA_PORT_ID,
                TEST_MEDIA_PORT_NAME, newMinGain, newMaxGain, DEFAULT_GAIN, STEP_SIZE,
                OUT_DEVICE, MEDIA_DEVICE_ADDRESS);
        HalAudioDeviceInfo navBusDeviceInfo = createHalAudioDeviceInfo(NAV_PORT_ID,
                TEST_NAV_PORT_NAME, newMinGain, newMaxGain, DEFAULT_GAIN, STEP_SIZE,
                OUT_DEVICE, NAVIGATION_DEVICE_ADDRESS);
        carVolumeGroup.updateAudioDeviceInfo(mediaBusDeviceInfo);
        carVolumeGroup.updateAudioDeviceInfo(navBusDeviceInfo);

        int eventType = carVolumeGroup.calculateNewGainStageFromDeviceInfos();

        expect.withMessage("Calculated event type for new gain stage")
                .that(eventType).isEqualTo(EVENT_TYPE_VOLUME_INDEX_MIN_MAX);
        expect.withMessage("Calculated current gain index for new gain stage")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(getExtrapolatedGainIndex(MIN_GAIN, newMinGain, STEP_SIZE,
                        TEST_GAIN_INDEX));
        expect.withMessage("Volume group attenuation state")
                .that(carVolumeGroup.isAttenuated()).isTrue();
    }

    @Test
    public void calculateNewGainStageFromDeviceInfos_whenRestrictionsActive_extrapolationFails() {
        CarAudioVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(DEFAULT_GAIN_INDEX);
        List<Integer> reasons = List.of(Reasons.NAV_DUCKING, Reasons.THERMAL_LIMITATION,
                Reasons.FORCED_MASTER_MUTE);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = TEST_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(reasons, musicCarGain);
        HalAudioDeviceInfo mediaBusDeviceInfo = createHalAudioDeviceInfo(
                MEDIA_PORT_ID, TEST_MEDIA_PORT_NAME, TEST_GAIN_MIN_VALUE, TEST_GAIN_MAX_VALUE,
                TEST_GAIN_DEFAULT_VALUE, TEST_GAIN_STEP_VALUE, OUT_DEVICE, MEDIA_DEVICE_ADDRESS);
        HalAudioDeviceInfo navBusDeviceInfo = createHalAudioDeviceInfo(
                NAV_PORT_ID, TEST_NAV_PORT_NAME, TEST_GAIN_MIN_VALUE, TEST_GAIN_MAX_VALUE,
                TEST_GAIN_DEFAULT_VALUE, TEST_GAIN_STEP_VALUE, OUT_DEVICE,
                NAVIGATION_DEVICE_ADDRESS);
        carVolumeGroup.updateAudioDeviceInfo(mediaBusDeviceInfo);
        carVolumeGroup.updateAudioDeviceInfo(navBusDeviceInfo);

        int eventType = carVolumeGroup.calculateNewGainStageFromDeviceInfos();

        expect.withMessage("Calculated event type for new gain stage")
                .that(eventType).isEqualTo(EVENT_TYPE_VOLUME_INDEX_MIN_MAX);
        expect.withMessage("Calculated current gain index for new gain stage")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(TEST_GAIN_DEFAULT_INDEX);
        expect.withMessage("Volume group attenuation state")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expect.withMessage("Volume group limitation state")
                .that(carVolumeGroup.isLimited()).isFalse();
        expect.withMessage("Volume group blocking state")
                .that(carVolumeGroup.isBlocked()).isFalse();
    }

    @Test
    public void audioDevicesAdded_whileInactive() {
        AudioDeviceInfo mockInfo = Mockito.mock(AudioDeviceInfo.class);
        CarAudioDeviceInfo mockCarDeviceInfo = Mockito.mock(CarAudioDeviceInfo.class);
        when(mockCarDeviceInfo.getAddress()).thenReturn(TEST_MEDIA_PORT_NAME);
        when(mockCarDeviceInfo.isActive()).thenReturn(false);
        when(mockCarDeviceInfo.audioDevicesAdded(any())).thenReturn(true);
        CarAudioVolumeGroup carVolumeGroup = getCarAudioVolumeGroupWithDevice(mockCarDeviceInfo);

        boolean updated = carVolumeGroup.audioDevicesAdded(List.of(mockInfo));

        expectWithMessage("Added devices status while inactive").that(updated).isTrue();
        List<AudioDeviceInfo> devices = captureAddedDevices(mockCarDeviceInfo);
        expectWithMessage("Devices added while inactive").that(devices).containsExactly(mockInfo);
    }


    @Test
    public void audioDevicesAdded_whileActive() {
        AudioDeviceInfo mockInfo = Mockito.mock(AudioDeviceInfo.class);
        CarAudioDeviceInfo mockCarDeviceInfo = Mockito.mock(CarAudioDeviceInfo.class);
        when(mockCarDeviceInfo.getAddress()).thenReturn(TEST_MEDIA_PORT_NAME);
        when(mockCarDeviceInfo.isActive()).thenReturn(true);
        CarAudioVolumeGroup carVolumeGroup = getCarAudioVolumeGroupWithDevice(mockCarDeviceInfo);

        boolean updated = carVolumeGroup.audioDevicesAdded(List.of(mockInfo));

        expectWithMessage("Added devices status while active").that(updated).isFalse();
        verify(mockCarDeviceInfo, never()).audioDevicesAdded(any());
    }

    @Test
    public void audioDevicesAdded_withNullDevices() {
        CarAudioDeviceInfo mockDeviceInfo = Mockito.mock(CarAudioDeviceInfo.class);
        when(mockDeviceInfo.getAddress()).thenReturn(TEST_MEDIA_PORT_NAME);
        CarAudioVolumeGroup carVolumeGroup = getCarAudioVolumeGroupWithDevice(
                mockDeviceInfo);

        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> carVolumeGroup.audioDevicesAdded(/* devices= */ null));

        expectWithMessage("Audio devices added null devices exception").that(thrown)
                .hasMessageThat().contains("Audio devices");
    }

    @Test
    public void audioDevicesRemoved_whileActive() {
        AudioDeviceInfo mockInfo = Mockito.mock(AudioDeviceInfo.class);
        CarAudioDeviceInfo mockCarDeviceInfo = Mockito.mock(CarAudioDeviceInfo.class);
        when(mockCarDeviceInfo.getAddress()).thenReturn(TEST_MEDIA_PORT_NAME);
        when(mockCarDeviceInfo.audioDevicesRemoved(any())).thenReturn(true);
        CarAudioVolumeGroup carVolumeGroup = getCarAudioVolumeGroupWithDevice(mockCarDeviceInfo);

        boolean updated = carVolumeGroup.audioDevicesRemoved(List.of(mockInfo));

        expectWithMessage("Removed devices status while active").that(updated).isTrue();
        List<AudioDeviceInfo> devices = captureRemovedDevices(mockCarDeviceInfo);
        expectWithMessage("Devices removed").that(devices).containsExactly(mockInfo);
    }

    @Test
    public void audioDevicesRemoved_whileInactive() {
        AudioDeviceInfo mockInfo = Mockito.mock(AudioDeviceInfo.class);
        CarAudioDeviceInfo mockCarDeviceInfo = Mockito.mock(CarAudioDeviceInfo.class);
        when(mockCarDeviceInfo.getAddress()).thenReturn(TEST_MEDIA_PORT_NAME);
        when(mockCarDeviceInfo.audioDevicesRemoved(any())).thenReturn(false);
        CarAudioVolumeGroup carVolumeGroup = getCarAudioVolumeGroupWithDevice(mockCarDeviceInfo);

        boolean updated = carVolumeGroup.audioDevicesRemoved(List.of(mockInfo));

        expectWithMessage("Removed devices status while inactive").that(updated).isFalse();
        List<AudioDeviceInfo> devices = captureRemovedDevices(mockCarDeviceInfo);
        expectWithMessage("Devices removed").that(devices).containsExactly(mockInfo);
    }

    @Test
    public void audioDevicesRemoved_withNullDevices() {
        CarAudioDeviceInfo mockDeviceInfo = Mockito.mock(CarAudioDeviceInfo.class);
        when(mockDeviceInfo.getAddress()).thenReturn(TEST_MEDIA_PORT_NAME);
        CarAudioVolumeGroup carVolumeGroup = getCarAudioVolumeGroupWithDevice(
                mockDeviceInfo);

        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> carVolumeGroup.audioDevicesRemoved(/* devices= */ null));

        expectWithMessage("Audio devices removed null devices exception").that(thrown)
                .hasMessageThat().contains("Audio devices");
    }

    private static List<AudioDeviceInfo> captureAddedDevices(
            CarAudioDeviceInfo mockCarDeviceInfo) {
        ArgumentCaptor<List<AudioDeviceInfo>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockCarDeviceInfo).audioDevicesAdded(captor.capture());
        return captor.getValue();
    }

    private static List<AudioDeviceInfo> captureRemovedDevices(
            CarAudioDeviceInfo mockCarDeviceInfo) {
        ArgumentCaptor<List<AudioDeviceInfo>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockCarDeviceInfo).audioDevicesRemoved(captor.capture());
        return captor.getValue();
    }

    private CarAudioVolumeGroup getCarAudioVolumeGroupWithDevice(
            CarAudioDeviceInfo mockCarDeviceInfo) {
        SparseArray<CarAudioDeviceInfo> contextToDeviceInfo = new SparseArray<>();
        contextToDeviceInfo.put(TEST_MEDIA_CONTEXT_ID, mockCarDeviceInfo);
        CarAudioVolumeGroup carVolumeGroup = new CarAudioVolumeGroup(TEST_CAR_AUDIO_CONTEXT,
                mSettingsMock, contextToDeviceInfo, ZONE_ID, ZONE_CONFIG_ID, GROUP_ID, GROUP_NAME,
                STEP_SIZE, DEFAULT_GAIN, MIN_GAIN, MAX_GAIN, /* useCarVolumeGroupMute= */ false,
                MAX_ACTIVATION_GAIN_INDEX_PERCENTAGE, MIN_ACTIVATION_GAIN_INDEX_PERCENTAGE);
        return carVolumeGroup;
    }

    private int getExtrapolatedGainIndex(int oldMinGain, int newMinGain, int step, int index) {
        return getIndexForGain(newMinGain, step, getGainForIndex(oldMinGain, step, index));
    }

    private static int getIndexForGain(int minGain, int step, int gain) {
        return (gain - minGain) / step;
    }

    private static int getGainForIndex(int minGain, int step, int index) {
        return minGain + step * index;
    }

    private CarAudioVolumeGroup testVolumeGroupSetup() {
        return testVolumeGroupSetup(mSettingsMock);
    }

    private CarAudioVolumeGroup testVolumeGroupSetup(CarAudioSettings settings) {
        SparseArray<CarAudioDeviceInfo> contextToDeviceInfo = new SparseArray<>();

        doReturn(true).when(() -> AudioManagerHelper
                .setAudioDeviceGain(any(), any(), anyInt(), anyBoolean()));

        contextToDeviceInfo.put(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        contextToDeviceInfo.put(TEST_CALL_CONTEXT_ID, mMediaDeviceInfo);
        contextToDeviceInfo.put(TEST_CALL_RING_CONTEXT_ID, mMediaDeviceInfo);

        contextToDeviceInfo.put(TEST_NAVIGATION_CONTEXT_ID, mNavigationDeviceInfo);
        contextToDeviceInfo.put(TEST_ALARM_CONTEXT_ID, mNavigationDeviceInfo);
        contextToDeviceInfo.put(TEST_NOTIFICATION_CONTEXT_ID, mNavigationDeviceInfo);

        return new CarAudioVolumeGroup(TEST_CAR_AUDIO_CONTEXT, settings, contextToDeviceInfo,
                ZONE_ID, ZONE_CONFIG_ID, GROUP_ID, GROUP_NAME, STEP_SIZE, DEFAULT_GAIN, MIN_GAIN,
                MAX_GAIN, /* useCarVolumeGroupMute= */ false, MAX_ACTIVATION_GAIN_INDEX_PERCENTAGE,
                MIN_ACTIVATION_GAIN_INDEX_PERCENTAGE);
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

        CarAudioSettings build() {
            CarAudioSettings settingsMock = Mockito.mock(CarAudioSettings.class);
            for (int storeIndex = 0; storeIndex < mStoredGainIndexes.size(); storeIndex++) {
                int gainUserId = mStoredGainIndexes.keyAt(storeIndex);
                when(settingsMock
                        .getStoredVolumeGainIndexForUser(gainUserId, mZoneId, mConfigId,
                                mGroupId)).thenReturn(
                        mStoredGainIndexes.get(gainUserId, DEFAULT_GAIN));
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

    private AudioDeviceAttributes getMockAudioDevice(String address) {
        AudioGain mockGain = new GainBuilder().build();
        return getMockAudioDevice(new AudioGain[]{mockGain}, address);
    }

    private AudioDeviceAttributes getMockAudioDevice(AudioGain[] gains, String address) {
        return new AudioDeviceAttributes(new AudioDeviceInfoBuilder()
                .setAddressName(address)
                .setAudioGains(gains)
                .build());
    }

    private AudioDeviceInfo getMockAudioDeviceInfo(String address) {
        AudioGain mockGain = new GainBuilder().build();
        return new AudioDeviceInfoBuilder()
                .setAddressName(address).setAudioGains(mockGain).build();
    }

    private HalAudioDeviceInfo createHalAudioDeviceInfo(int id, String name, int minVal,
            int maxVal, int defaultVal, int stepVal, int type, String address) {
        AudioPortDeviceExt deviceExt = new AudioPortDeviceExt();
        deviceExt.device = new AudioDevice();
        deviceExt.device.type = new AudioDeviceDescription();
        deviceExt.device.type.type = type;
        deviceExt.device.type.connection = CONNECTION_BUS;
        deviceExt.device.address = AudioDeviceAddress.id(address);
        AudioPort audioPort = new AudioPort();
        audioPort.id = id;
        audioPort.name = name;
        audioPort.gains = new android.media.audio.common.AudioGain[] {
                new android.media.audio.common.AudioGain() {{
                    mode = JOINT;
                    minValue = minVal;
                    maxValue = maxVal;
                    defaultValue = defaultVal;
                    stepValue = stepVal;
                }}
        };
        audioPort.ext = AudioPortExt.device(deviceExt);
        return new HalAudioDeviceInfo(audioPort);
    }
}
