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

import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_MEDIA;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.media.AudioManager;
import android.os.UserHandle;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class CarVolumeGroupFactoryTest {
    private static final int ZONE_ID = 0;
    private static final int CONFIG_ID = 1;
    private static final int GROUP_ID = 0;
    private static final int DEFAULT_GAIN_INDEX = (TestCarAudioDeviceInfoBuilder.DEFAULT_GAIN
            - TestCarAudioDeviceInfoBuilder.MIN_GAIN) / TestCarAudioDeviceInfoBuilder.STEP_VALUE;
    private static final int MIN_GAIN_INDEX = 0;
    private static final int MAX_GAIN_INDEX = (TestCarAudioDeviceInfoBuilder.MAX_GAIN
            - TestCarAudioDeviceInfoBuilder.MIN_GAIN) / TestCarAudioDeviceInfoBuilder.STEP_VALUE;
    private static final int MIN_ACTIVATION_GAIN_INDEX_PERCENTAGE = 10;
    private static final int MAX_ACTIVATION_GAIN_INDEX_PERCENTAGE = 90;
    private static final String GROUP_NAME = "group_0";
    private static final String MEDIA_DEVICE_ADDRESS = "music";
    private static final String NAVIGATION_DEVICE_ADDRESS = "navigation";

    private static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                    /* useCoreAudioRouting= */ false);

    private static final @CarAudioContext.AudioContext int TEST_MEDIA_CONTEXT_ID =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
    private static final @CarAudioContext.AudioContext int TEST_NAVIGATION_CONTEXT_ID =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE));

    private CarAudioDeviceInfo mMediaDeviceInfo;
    private CarAudioDeviceInfo mNavigationDeviceInfo;

    @Mock
    CarAudioSettings mSettingsMock;
    @Mock
    AudioManager mAudioManagerMock;

    CarVolumeGroupFactory mFactory;

    @Rule
    public final Expect expect = Expect.create();

    @Before
    public void setUp() {
        mMediaDeviceInfo = new TestCarAudioDeviceInfoBuilder(MEDIA_DEVICE_ADDRESS).build();
        mNavigationDeviceInfo = new TestCarAudioDeviceInfoBuilder(NAVIGATION_DEVICE_ADDRESS)
                .build();
        mFactory = getFactory();
    }

    @Test
    public void setDeviceInfoForContext_associatesDeviceAddresses() {
        mFactory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        mFactory.setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, mNavigationDeviceInfo);
        CarVolumeGroup carVolumeGroup = mFactory.getCarVolumeGroup(/* useCoreAudioVolume= */ false);

        expect.withMessage("Addresses for car volume group")
                .that(carVolumeGroup.getAddresses()).containsExactly(MEDIA_DEVICE_ADDRESS,
                NAVIGATION_DEVICE_ADDRESS);
    }

    @Test
    public void setDeviceInfoForContext_associatesContexts() {
        mFactory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        mFactory.setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, mNavigationDeviceInfo);
        CarVolumeGroup carVolumeGroup = mFactory.getCarVolumeGroup(/* useCoreAudioVolume= */ false);

        expect.withMessage("Music[%s] and Navigation[%s] Context",
                TEST_MEDIA_CONTEXT_ID, TEST_NAVIGATION_CONTEXT_ID)
                .that(carVolumeGroup.getContexts()).asList()
                .containsExactly(TEST_MEDIA_CONTEXT_ID,
                        TEST_NAVIGATION_CONTEXT_ID);
    }

    @Test
    public void setDeviceInfoForContext_withDifferentStepSize_throws() {
        mFactory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        CarAudioDeviceInfo differentStepValueDevice = new TestCarAudioDeviceInfoBuilder(
                NAVIGATION_DEVICE_ADDRESS).setStepValue(mMediaDeviceInfo.getStepValue() + 1)
                .build();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> mFactory.setDeviceInfoForContext(
                        TEST_NAVIGATION_CONTEXT_ID,
                        differentStepValueDevice));

        expect.withMessage("setDeviceInfoForContext failure for different step size")
                .that(thrown).hasMessageThat()
                .contains("Gain controls within one group must have same step value");
    }

    @Test
    public void setDeviceInfoForContext_withSameContext_throws() {
        mFactory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> mFactory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID,
                        mNavigationDeviceInfo));

        expect.withMessage("setDeviceInfoForSameContext failure for repeated context")
                .that(thrown).hasMessageThat().contains("has already been set to");
    }

    @Test
    public void setDeviceInfoForContext_withFirstCall_setsMinGain() {
        mFactory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);

        expect.withMessage("Min Gain from factory")
                .that(mFactory.getMinGain()).isEqualTo(mMediaDeviceInfo.getMinGain());
    }

    @Test
    public void setDeviceInfoForContext_withFirstCall_setsMaxGain() {
        mFactory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);

        expect.withMessage("Max Gain from factory")
                .that(mFactory.getMaxGain()).isEqualTo(mMediaDeviceInfo.getMaxGain());
    }

    @Test
    public void setDeviceInfoForContext_withFirstCall_setsDefaultGain() {
        mFactory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);

        expect.withMessage("Default Gain from factory")
                .that(mFactory.getDefaultGain()).isEqualTo(mMediaDeviceInfo.getDefaultGain());
    }

    @Test
    public void setDeviceInfoForContext_SecondCallWithSmallerMinGain_updatesMinGain() {
        mFactory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        CarAudioDeviceInfo secondInfo = new TestCarAudioDeviceInfoBuilder(NAVIGATION_DEVICE_ADDRESS)
                .setMinGain(mMediaDeviceInfo.getMinGain() - 1).build();

        mFactory.setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, secondInfo);

        expect.withMessage("Second, smaller min gain from factory")
                .that(mFactory.getMinGain()).isEqualTo(secondInfo.getMinGain());
    }

    @Test
    public void setDeviceInfoForContext_SecondCallWithLargerMinGain_keepsFirstMinGain() {
        mFactory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        CarAudioDeviceInfo secondInfo = new TestCarAudioDeviceInfoBuilder(NAVIGATION_DEVICE_ADDRESS)
                .setMinGain(mMediaDeviceInfo.getMinGain() + 1).build();

        mFactory.setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, secondInfo);

        expect.withMessage("First, smaller min gain from factory")
                .that(mFactory.getMinGain()).isEqualTo(mMediaDeviceInfo.getMinGain());
    }

    @Test
    public void setDeviceInfoForContext_SecondCallWithLargerMaxGain_updatesMaxGain() {
        mFactory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        CarAudioDeviceInfo secondInfo = new TestCarAudioDeviceInfoBuilder(NAVIGATION_DEVICE_ADDRESS)
                .setMaxGain(mMediaDeviceInfo.getMaxGain() + 1).build();

        mFactory.setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, secondInfo);

        expect.withMessage("Second, larger max gain from factory")
                .that(mFactory.getMaxGain()).isEqualTo(secondInfo.getMaxGain());
    }

    @Test
    public void setDeviceInfoForContext_SecondCallWithSmallerMaxGain_keepsFirstMaxGain() {
        mFactory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        CarAudioDeviceInfo secondInfo = new TestCarAudioDeviceInfoBuilder(NAVIGATION_DEVICE_ADDRESS)
                .setMaxGain(mMediaDeviceInfo.getMaxGain() - 1).build();

        mFactory.setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, secondInfo);

        expect.withMessage("First, larger max gain from factory")
                .that(mFactory.getMaxGain()).isEqualTo(mMediaDeviceInfo.getMaxGain());
    }

    @Test
    public void setDeviceInfoForContext_SecondCallWithLargerDefaultGain_updatesDefaultGain() {
        mFactory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        CarAudioDeviceInfo secondInfo = new TestCarAudioDeviceInfoBuilder(NAVIGATION_DEVICE_ADDRESS)
                .setDefaultGain(mMediaDeviceInfo.getDefaultGain() + 1).build();

        mFactory.setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, secondInfo);

        expect.withMessage("Second, larger default gain from factory")
                .that(mFactory.getDefaultGain()).isEqualTo(secondInfo.getDefaultGain());
    }

    @Test
    public void setDeviceInfoForContext_SecondCallWithSmallerDefaultGain_keepsFirstDefaultGain() {
        mFactory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        CarAudioDeviceInfo secondInfo = new TestCarAudioDeviceInfoBuilder(NAVIGATION_DEVICE_ADDRESS)
                .setDefaultGain(mMediaDeviceInfo.getDefaultGain() - 1).build();

        mFactory.setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, secondInfo);

        expect.withMessage("Second, smaller default gain from factory")
                .that(mFactory.getDefaultGain()).isEqualTo(mMediaDeviceInfo.getDefaultGain());
    }

    @Test
    public void factoryBuild_withNoCallToSetDeviceInfoForContext_throws() {
        Exception e = assertThrows(IllegalArgumentException.class,
                () -> mFactory.getCarVolumeGroup(/* useCoreAudioVolume= */ false));

        expect.withMessage("factory build failure").that(e).hasMessageThat()
                .isEqualTo(
                        "setDeviceInfoForContext has to be called at least once before building");
    }

    @Test
    public void factoryBuild_withNoStoredGain_usesDefaultGain() {
        mFactory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        when(mSettingsMock.getStoredVolumeGainIndexForUser(UserHandle.USER_CURRENT, ZONE_ID,
                CONFIG_ID, GROUP_ID)).thenReturn(-1);


        CarVolumeGroup carVolumeGroup = mFactory.getCarVolumeGroup(/* useCoreAudioVolume= */ false);

        expect.withMessage("Current gain index")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(DEFAULT_GAIN_INDEX);
    }

    @Test
    public void factoryBuild_withTooLargeStoredGain_usesDefaultGain() {
        mFactory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        when(mSettingsMock.getStoredVolumeGainIndexForUser(UserHandle.USER_CURRENT, ZONE_ID,
                CONFIG_ID, GROUP_ID)).thenReturn(MAX_GAIN_INDEX + 1);

        CarVolumeGroup carVolumeGroup = mFactory.getCarVolumeGroup(/* useCoreAudioVolume= */ false);

        expect.withMessage("Current gain index")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(DEFAULT_GAIN_INDEX);
    }

    @Test
    public void factoryBuild_withTooSmallStoredGain_usesDefaultGain() {
        mFactory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        when(mSettingsMock.getStoredVolumeGainIndexForUser(UserHandle.USER_CURRENT, ZONE_ID,
                CONFIG_ID, GROUP_ID)).thenReturn(MIN_GAIN_INDEX - 1);

        CarVolumeGroup carVolumeGroup = mFactory.getCarVolumeGroup(/* useCoreAudioVolume= */ false);

        expect.withMessage("Current gain index")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(DEFAULT_GAIN_INDEX);
    }

    @Test
    public void factoryBuild_withValidStoredGain_usesStoredGain() {
        mFactory.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        when(mSettingsMock.getStoredVolumeGainIndexForUser(UserHandle.USER_CURRENT, ZONE_ID,
                CONFIG_ID, GROUP_ID)).thenReturn(MAX_GAIN_INDEX - 1);

        CarVolumeGroup carVolumeGroup = mFactory.getCarVolumeGroup(/* useCoreAudioVolume= */ false);

        expect.withMessage("Current gain index")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(MAX_GAIN_INDEX - 1);
    }

    @Test
    public void factoryConstructor_withNullCarAudioSettings_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> new CarVolumeGroupFactory(/* audioManager= */ null,
                        /* carAudioSettings= */ null, TEST_CAR_AUDIO_CONTEXT, ZONE_ID,
                        CONFIG_ID, GROUP_ID, GROUP_NAME, /* useCarVolumeGroupMute= */ true,
                        MAX_ACTIVATION_GAIN_INDEX_PERCENTAGE,
                        MIN_ACTIVATION_GAIN_INDEX_PERCENTAGE));

        expect.withMessage("Constructor null car audio settings exception")
                .that(thrown).hasMessageThat()
                .contains("Car audio settings");
    }

    @Test
    public void factoryConstructor_withNullCarAudioContext_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> new CarVolumeGroupFactory(/* audioManager= */ null, mSettingsMock,
                        /* carAudioContext= */ null, ZONE_ID, CONFIG_ID, GROUP_ID,
                        GROUP_NAME, /* useCarVolumeGroupMute= */ true,
                        MAX_ACTIVATION_GAIN_INDEX_PERCENTAGE,
                        MIN_ACTIVATION_GAIN_INDEX_PERCENTAGE));

        expect.withMessage("Constructor null car audio context exception")
                .that(thrown).hasMessageThat()
                .contains("Car audio context");
    }

    CarVolumeGroupFactory getFactory() {
        return new CarVolumeGroupFactory(mAudioManagerMock, mSettingsMock, TEST_CAR_AUDIO_CONTEXT,
                ZONE_ID, CONFIG_ID, GROUP_ID, GROUP_NAME, /* useCarVolumeGroupMute= */ true,
                MAX_ACTIVATION_GAIN_INDEX_PERCENTAGE, MIN_ACTIVATION_GAIN_INDEX_PERCENTAGE);
    }
}
