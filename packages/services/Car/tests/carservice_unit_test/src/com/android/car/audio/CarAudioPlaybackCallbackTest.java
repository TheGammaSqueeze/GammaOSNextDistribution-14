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

import static android.media.AudioAttributes.USAGE_MEDIA;

import static com.android.car.audio.CarAudioService.SystemClockWrapper;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.media.AudioAttributes;
import android.media.AudioPlaybackConfiguration;
import android.util.SparseArray;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class CarAudioPlaybackCallbackTest extends AbstractExtendedMockitoTestCase {

    private static final int PRIMARY_ZONE_ID = 0;
    private static final int SECONDARY_ZONE_ID = 1;
    private static final long TIMER_START_TIME_MS = 100000;
    private static final int KEY_EVENT_TIMEOUT_MS = 3000;
    private static final long TIMER_BEFORE_TIMEOUT_MS =
            TIMER_START_TIME_MS + KEY_EVENT_TIMEOUT_MS - 1;
    private static final long TIMER_AFTER_TIMEOUT_MS =
            TIMER_START_TIME_MS + KEY_EVENT_TIMEOUT_MS + 1;
    private static final int NEGATIVE_KEY_EVENT_TIMEOUT_MS = -KEY_EVENT_TIMEOUT_MS;
    private static final String PRIMARY_MEDIA_ADDRESS = "music_bus0";
    private static final String SECONDARY_MEDIA_ADDRESS = "music_bus1";

    private static final AudioAttributes TEST_MEDIA_AUDIO_ATTRIBUTE =
            new AudioAttributes.Builder().setUsage(USAGE_MEDIA).build();

    private static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                    /* useCoreAudioRouting= */ false);

    private static final @CarAudioContext.AudioContext int TEST_MEDIA_AUDIO_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_MEDIA_AUDIO_ATTRIBUTE);


    private CarAudioZone mPrimaryZone;
    private SparseArray<CarAudioZone> mCarAudioZones = new SparseArray<>();
    private CarAudioZone mSecondaryZone;
    private CarAudioPlaybackCallback mCallback;
    @Mock
    private SystemClockWrapper mClock;
    @Mock
    private CarAudioPlaybackMonitor mCarAudioPlaybackMonitor;
    @Captor
    private ArgumentCaptor<List<AudioAttributes>> mAudioAttributesCaptor;

    @Before
    public void setUp() {
        mPrimaryZone = generatePrimaryZone();
        mSecondaryZone = generateSecondaryZone();
        when(mClock.uptimeMillis()).thenReturn(TIMER_START_TIME_MS);
        mCarAudioZones.put(PRIMARY_ZONE_ID, mPrimaryZone);
        mCarAudioZones.put(SECONDARY_ZONE_ID, mSecondaryZone);
        mCallback = new CarAudioPlaybackCallback(mCarAudioZones, mCarAudioPlaybackMonitor, mClock,
                KEY_EVENT_TIMEOUT_MS);
    }

    @Test
    public void constructor_withNullAudioZones_fails() throws Exception {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> new CarAudioPlaybackCallback(/* carAudioZones= */ null,
                        mCarAudioPlaybackMonitor, mClock, KEY_EVENT_TIMEOUT_MS));

        expectWithMessage("Car audio playback callback construction exception")
                .that(thrown).hasMessageThat().contains("Car audio zone cannot be null");
    }

    @Test
    public void constructor_withEmptyAudioZones_fails() throws Exception {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new CarAudioPlaybackCallback(new SparseArray<>(), mCarAudioPlaybackMonitor,
                        mClock, KEY_EVENT_TIMEOUT_MS));

        expectWithMessage("Car audio playback callback construction exception")
                .that(thrown).hasMessageThat().contains("Car audio zones must not be empty");
    }

    @Test
    public void constructor_withNullSystemClockWrapper_fails()
            throws Exception {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> new CarAudioPlaybackCallback(mCarAudioZones, mCarAudioPlaybackMonitor,
                        /* clock= */ null, KEY_EVENT_TIMEOUT_MS));

        expectWithMessage("Car audio playback callback construction exception")
                .that(thrown).hasMessageThat().contains("Clock cannot be null");
    }

    @Test
    public void constructor_withNegativeKeyEventTimeout_fails()
            throws Exception {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new CarAudioPlaybackCallback(mCarAudioZones, mCarAudioPlaybackMonitor, mClock,
                        NEGATIVE_KEY_EVENT_TIMEOUT_MS));

        expectWithMessage("Car audio playback callback construction exception")
                .that(thrown).hasMessageThat()
                .contains("Volume key event timeout must be positive");
    }

    @Test
    public void onPlaybackConfigChanged_singleConfig_returnsActiveContextForPrimaryZoneOnly() {
        List<AudioPlaybackConfiguration> configurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build()
        );

        mCallback.onPlaybackConfigChanged(configurations);
        List<AudioAttributes> primaryZoneActiveAttributes =
                mCallback.getAllActiveAudioAttributesForZone(PRIMARY_ZONE_ID);
        List<AudioAttributes> secondaryZoneActiveAttributes =
                mCallback.getAllActiveAudioAttributesForZone(SECONDARY_ZONE_ID);

        expectWithMessage("Primary zone active attributes")
                .that(primaryZoneActiveAttributes)
                .containsExactly(TEST_MEDIA_AUDIO_ATTRIBUTE);
        expectWithMessage("Secondary zone active attributes")
                .that(secondaryZoneActiveAttributes)
                .isEmpty();
        verifyActivationPlaybacks(List.of(TEST_MEDIA_AUDIO_ATTRIBUTE), PRIMARY_ZONE_ID);
        verify(mCarAudioPlaybackMonitor, never()).onActiveAudioPlaybackAttributesAdded(any(),
                eq(SECONDARY_ZONE_ID));
    }

    @Test
    public void onPlaybackConfigChanged_withNullPlaybackMonitor() {
        CarAudioPlaybackCallback callback = new CarAudioPlaybackCallback(mCarAudioZones,
                /* carAudioPlaybackMonitor= */ null, mClock, KEY_EVENT_TIMEOUT_MS);
        List<AudioPlaybackConfiguration> configurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build()
        );

        callback.onPlaybackConfigChanged(configurations);

        List<AudioAttributes> primaryZoneActiveAttributes =
                callback.getAllActiveAudioAttributesForZone(PRIMARY_ZONE_ID);
        List<AudioAttributes> secondaryZoneActiveAttributes =
                callback.getAllActiveAudioAttributesForZone(SECONDARY_ZONE_ID);
        expectWithMessage("Primary zone active attributes with null playback monitor")
                .that(primaryZoneActiveAttributes).containsExactly(TEST_MEDIA_AUDIO_ATTRIBUTE);
        expectWithMessage("Secondary zone active attributes with null playback monitor")
                .that(secondaryZoneActiveAttributes).isEmpty();
    }

    @Test
    public void onPlaybackConfigChanged_withMultipleConfigs_returnsActiveConfigsForTwoZones() {
        List<AudioPlaybackConfiguration> configurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(SECONDARY_MEDIA_ADDRESS)
                        .build()
        );

        mCallback.onPlaybackConfigChanged(configurations);
        List<AudioAttributes> primaryZoneActiveAttributes =
                mCallback.getAllActiveAudioAttributesForZone(PRIMARY_ZONE_ID);
        List<AudioAttributes> secondaryZoneActiveAttributes =
                mCallback.getAllActiveAudioAttributesForZone(SECONDARY_ZONE_ID);

        expectWithMessage("Primary zone active attributes")
                .that(primaryZoneActiveAttributes)
                .containsExactly(TEST_MEDIA_AUDIO_ATTRIBUTE);
        expectWithMessage("Secondary zone active attributes")
                .that(secondaryZoneActiveAttributes)
                .containsExactly(TEST_MEDIA_AUDIO_ATTRIBUTE);
        verifyActivationPlaybacks(List.of(TEST_MEDIA_AUDIO_ATTRIBUTE), PRIMARY_ZONE_ID);
        verifyActivationPlaybacks(List.of(TEST_MEDIA_AUDIO_ATTRIBUTE), SECONDARY_ZONE_ID);
    }

    @Test
    public void onPlaybackConfigChanged_withMultipleConfigs_onIncorrectConfigurations() {
        List<AudioPlaybackConfiguration> configurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(SECONDARY_MEDIA_ADDRESS)
                        .setInactive()
                        .build()
        );

        mCallback.onPlaybackConfigChanged(configurations);
        List<AudioAttributes> primaryZoneActiveAttributes =
                mCallback.getAllActiveAudioAttributesForZone(PRIMARY_ZONE_ID);
        List<AudioAttributes> secondaryZoneActiveAttributes =
                mCallback.getAllActiveAudioAttributesForZone(SECONDARY_ZONE_ID);

        expectWithMessage("Primary zone active attributes")
                .that(primaryZoneActiveAttributes)
                .containsExactly(TEST_MEDIA_AUDIO_ATTRIBUTE);
        expectWithMessage("Secondary zone active attributes")
                .that(secondaryZoneActiveAttributes)
                .isEmpty();
        verifyActivationPlaybacks(List.of(TEST_MEDIA_AUDIO_ATTRIBUTE), PRIMARY_ZONE_ID);
        verify(mCarAudioPlaybackMonitor, never()).onActiveAudioPlaybackAttributesAdded(any(),
                eq(SECONDARY_ZONE_ID));
    }

    @Test
    public void onPlaybackConfigChanged_withMultipleInactiveConfigs_beforeTimeout() {
        List<AudioPlaybackConfiguration> configurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(SECONDARY_MEDIA_ADDRESS)
                        .build()
        );
        List<AudioPlaybackConfiguration> configurationsChanged = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .setInactive()
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(SECONDARY_MEDIA_ADDRESS)
                        .setInactive()
                        .build()
        );
        mCallback.onPlaybackConfigChanged(configurations);

        mCallback.onPlaybackConfigChanged(configurationsChanged);

        when(mClock.uptimeMillis()).thenReturn(TIMER_BEFORE_TIMEOUT_MS);
        List<AudioAttributes> primaryZoneActiveAttributes =
                mCallback.getAllActiveAudioAttributesForZone(PRIMARY_ZONE_ID);
        List<AudioAttributes> secondaryZoneActiveAttributes =
                mCallback.getAllActiveAudioAttributesForZone(SECONDARY_ZONE_ID);
        expectWithMessage("Primary zone active attributes")
                .that(primaryZoneActiveAttributes)
                .containsExactly(TEST_MEDIA_AUDIO_ATTRIBUTE);
        expectWithMessage("Secondary zone active attributes")
                .that(secondaryZoneActiveAttributes)
                .containsExactly(TEST_MEDIA_AUDIO_ATTRIBUTE);
        verifyActivationPlaybacks(List.of(TEST_MEDIA_AUDIO_ATTRIBUTE), PRIMARY_ZONE_ID);
        verifyActivationPlaybacks(List.of(TEST_MEDIA_AUDIO_ATTRIBUTE), SECONDARY_ZONE_ID);
    }

    @Test
    public void onPlaybackConfigChanged_withMultipleConfigs_afterTimeout() {
        List<AudioPlaybackConfiguration> configurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(SECONDARY_MEDIA_ADDRESS)
                        .build()
        );
        List<AudioPlaybackConfiguration> configurationsChanged = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .setInactive()
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(SECONDARY_MEDIA_ADDRESS)
                        .setInactive()
                        .build()
        );
        mCallback.onPlaybackConfigChanged(configurations);

        mCallback.onPlaybackConfigChanged(configurationsChanged);

        when(mClock.uptimeMillis()).thenReturn(TIMER_AFTER_TIMEOUT_MS);
        List<AudioAttributes> primaryZoneActiveAttributes =
                mCallback.getAllActiveAudioAttributesForZone(PRIMARY_ZONE_ID);
        List<AudioAttributes> secondaryZoneActiveAttributes =
                mCallback.getAllActiveAudioAttributesForZone(SECONDARY_ZONE_ID);
        expectWithMessage("Primary zone active attributes")
                .that(primaryZoneActiveAttributes)
                .isEmpty();
        expectWithMessage("Secondary zone active attributes")
                .that(secondaryZoneActiveAttributes)
                .isEmpty();
        verifyActivationPlaybacks(List.of(TEST_MEDIA_AUDIO_ATTRIBUTE), PRIMARY_ZONE_ID);
        verifyActivationPlaybacks(List.of(TEST_MEDIA_AUDIO_ATTRIBUTE), SECONDARY_ZONE_ID);
    }

    @Test
    public void onPlaybackConfigChanged_withMultipleConfigs_resetStillActiveContexts() {
        List<AudioPlaybackConfiguration> configurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(SECONDARY_MEDIA_ADDRESS)
                        .build()
        );
        List<AudioPlaybackConfiguration> configurationsChanged = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .setInactive()
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(SECONDARY_MEDIA_ADDRESS)
                        .setInactive()
                        .build()
        );
        mCallback.onPlaybackConfigChanged(configurations);
        mCallback.onPlaybackConfigChanged(configurationsChanged);

        mCallback.resetStillActiveContexts();

        when(mClock.uptimeMillis()).thenReturn(TIMER_BEFORE_TIMEOUT_MS);
        List<AudioAttributes> primaryZoneActiveAttributes =
                mCallback.getAllActiveAudioAttributesForZone(PRIMARY_ZONE_ID);
        List<AudioAttributes> secondaryZoneActiveAttributes =
                mCallback.getAllActiveAudioAttributesForZone(SECONDARY_ZONE_ID);
        expectWithMessage("Primary zone active attributes")
                .that(primaryZoneActiveAttributes)
                .isEmpty();
        expectWithMessage("Secondary zone active attributes")
                .that(secondaryZoneActiveAttributes)
                .isEmpty();
    }

    private CarAudioZone generatePrimaryZone() {
        CarAudioZoneConfig primaryCarAudioZoneConfig =
                new CarAudioZoneConfig.Builder("Primary zone config 0", PRIMARY_ZONE_ID,
                        /* zoneConfigId= */ 0, /* isDefault= */ true)
                        .addVolumeGroup(new VolumeGroupBuilder()
                                .addDeviceAddressAndContexts(TEST_MEDIA_AUDIO_CONTEXT,
                                        PRIMARY_MEDIA_ADDRESS)
                                .build())
                        .build();
        return new TestCarAudioZoneBuilder("Primary zone", PRIMARY_ZONE_ID)
                .addCarAudioZoneConfig(primaryCarAudioZoneConfig)
                .build();
    }

    private CarAudioZone generateSecondaryZone() {
        CarAudioZoneConfig secondaryCarAudioZoneConfig =
                new CarAudioZoneConfig.Builder("Secondary zone config 0", SECONDARY_ZONE_ID,
                        /* zoneConfigId= */ 0, /* isDefault= */ true)
                        .addVolumeGroup(new VolumeGroupBuilder()
                                .addDeviceAddressAndContexts(TEST_MEDIA_AUDIO_CONTEXT,
                                        SECONDARY_MEDIA_ADDRESS)
                                .build())
                        .build();
        return new TestCarAudioZoneBuilder("Secondary zone", SECONDARY_ZONE_ID)
                .addCarAudioZoneConfig(secondaryCarAudioZoneConfig)
                .build();
    }

    private void verifyActivationPlaybacks(List<AudioAttributes> newlyActiveAudioAttributes,
                                           int zoneId) {
        verify(mCarAudioPlaybackMonitor).onActiveAudioPlaybackAttributesAdded(
                mAudioAttributesCaptor.capture(), eq(zoneId));
        assertWithMessage("Audio attributes for newly active playbacks")
                .that(mAudioAttributesCaptor.getValue())
                .containsExactlyElementsIn(newlyActiveAudioAttributes);
    }
}
