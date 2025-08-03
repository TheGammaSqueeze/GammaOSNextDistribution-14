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
import static android.media.AudioAttributes.USAGE_MEDIA;

import static com.android.car.audio.CarAudioService.SystemClockWrapper;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.media.AudioAttributes;
import android.media.AudioPlaybackConfiguration;

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
public final class ZoneAudioPlaybackCallbackTest {

    private static final int PRIMARY_ZONE_ID = 0;
    private static final String PRIMARY_MEDIA_ADDRESS = "music_bus0";
    private static final String PRIMARY_NAVIGATION_ADDRESS = "navigation_bus1";
    private static final String PRIMARY_VOICE_ADDRESS = "voice_bus3";

    private static final String SECONDARY_MEDIA_ADDRESS = "music_bus100";
    private static final String SECONDARY_NAVIGATION_ADDRESS = "navigation_bus101";

    private static final long TIMER_START_TIME_MS = 100000;
    private static final int KEY_EVENT_TIMEOUT_MS = 3000;
    private static final long TIMER_BEFORE_TIMEOUT_MS =
            TIMER_START_TIME_MS + KEY_EVENT_TIMEOUT_MS - 1;
    private static final long TIMER_AFTER_TIMEOUT_MS =
            TIMER_START_TIME_MS + KEY_EVENT_TIMEOUT_MS + 1;

    private static final AudioAttributes TEST_MEDIA_AUDIO_ATTRIBUTE =
            new AudioAttributes.Builder().setUsage(USAGE_MEDIA).build();
    private static final AudioAttributes TEST_NAVIGATION_AUDIO_ATTRIBUTE =
            new AudioAttributes.Builder().setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).build();

    private static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                    /* useCoreAudioRouting= */ false);

    @CarAudioContext.AudioContext
    private static final int TEST_MEDIA_AUDIO_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_MEDIA_AUDIO_ATTRIBUTE);

    @CarAudioContext.AudioContext
    private static final int TEST_NAVIGATION_AUDIO_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_NAVIGATION_AUDIO_ATTRIBUTE);

    @CarAudioContext.AudioContext
    private static final int TEST_ASSISTANT_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_ASSISTANT));

    @Mock
    private CarAudioPlaybackMonitor mCarAudioPlaybackMonitor;
    @Mock
    private SystemClockWrapper mClock;
    @Captor
    private ArgumentCaptor<List<AudioAttributes>> mAudioAttributesCaptor;

    private CarAudioZone mPrimaryZone;

    @Before
    public void setUp() {
        mPrimaryZone = generatePrimaryZone();
        when(mClock.uptimeMillis()).thenReturn(TIMER_START_TIME_MS);
    }

    @Test
    public void createZoneAudioPlaybackCallback_withNullCarAudioZones_fails() throws Exception {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                new ZoneAudioPlaybackCallback(/* carAudioZone= */ null, mCarAudioPlaybackMonitor,
                        mClock, KEY_EVENT_TIMEOUT_MS));

        assertWithMessage("Zone audio playback callback constructor")
                .that(thrown).hasMessageThat().contains("Audio zone cannot be null");
    }

    @Test
    public void createZoneAudioPlaybackCallback_withNullSystemClockWrapper_fails()
            throws Exception {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                new ZoneAudioPlaybackCallback(mPrimaryZone, mCarAudioPlaybackMonitor,
                        /* clock= */ null, KEY_EVENT_TIMEOUT_MS));

        assertWithMessage("Zone audio playback callback constructor")
                .that(thrown).hasMessageThat().contains("Clock cannot be null");
    }

    @Test
    public void
            createZoneAudioPlaybackCallback_withNegativeKeyEventTimeout_fails() throws Exception {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                new ZoneAudioPlaybackCallback(mPrimaryZone, mCarAudioPlaybackMonitor, mClock,
                        -KEY_EVENT_TIMEOUT_MS));

        assertWithMessage("Zone audio playback callback constructor")
                .that(thrown).hasMessageThat()
                .contains("Volume key event timeout must be positive");
    }

    @Test
    public void
            getAllActiveContextsForPrimaryZone_withNoOnPlaybackConfigChanged_returnsEmptyList() {
        ZoneAudioPlaybackCallback callback = new ZoneAudioPlaybackCallback(mPrimaryZone,
                mCarAudioPlaybackMonitor, mClock, KEY_EVENT_TIMEOUT_MS);

        List<AudioAttributes> activeAttributes =
                callback.getAllActiveAudioAttributes();

        assertThat(activeAttributes).isEmpty();
    }

    @Test
    public void
            getAllActiveContextsForPrimaryZone_withOneMatchingConfiguration_returnsActiveContext() {
        List<AudioPlaybackConfiguration> activeConfigurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build()
        );

        ZoneAudioPlaybackCallback callback = new ZoneAudioPlaybackCallback(mPrimaryZone,
                mCarAudioPlaybackMonitor, mClock, KEY_EVENT_TIMEOUT_MS);

        callback.onPlaybackConfigChanged(activeConfigurations);

        List<AudioAttributes> activeAttributes =
                callback.getAllActiveAudioAttributes();

        assertThat(activeAttributes).containsExactly(TEST_MEDIA_AUDIO_ATTRIBUTE);
    }

    @Test
    public void getAllActiveContextsForPrimaryZone_withNullPlaybackMonitor() {
        List<AudioPlaybackConfiguration> activeConfigurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build()
        );
        ZoneAudioPlaybackCallback callback = new ZoneAudioPlaybackCallback(mPrimaryZone,
                /* carAudioPlaybackMonitor= */ null, mClock, KEY_EVENT_TIMEOUT_MS);
        callback.onPlaybackConfigChanged(activeConfigurations);

        List<AudioAttributes> activeAttributes =
                callback.getAllActiveAudioAttributes();

        assertWithMessage("Active attributes with null car audio playback monitor")
                .that(activeAttributes).containsExactly(TEST_MEDIA_AUDIO_ATTRIBUTE);
    }

    @Test
    public void
            getAllActiveContextsForPrimaryZone_withMultipleConfiguration_returnsActiveContexts() {
        List<AudioPlaybackConfiguration> activeConfigurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setDeviceAddress(PRIMARY_NAVIGATION_ADDRESS)
                        .build()
        );

        ZoneAudioPlaybackCallback callback = new ZoneAudioPlaybackCallback(mPrimaryZone,
                mCarAudioPlaybackMonitor, mClock, KEY_EVENT_TIMEOUT_MS);

        callback.onPlaybackConfigChanged(activeConfigurations);

        List<AudioAttributes> activeAttributes =
                callback.getAllActiveAudioAttributes();

        assertThat(activeAttributes)
                .containsExactly(TEST_MEDIA_AUDIO_ATTRIBUTE,
                        TEST_NAVIGATION_AUDIO_ATTRIBUTE);
    }

    @Test
    public void
            getAllActiveContextsForPrimaryZone_withInactiveConfigurations_returnsActiveContext() {
        List<AudioPlaybackConfiguration> configurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setDeviceAddress(PRIMARY_NAVIGATION_ADDRESS)
                        .setInactive()
                        .build()
        );

        ZoneAudioPlaybackCallback callback = new ZoneAudioPlaybackCallback(mPrimaryZone,
                mCarAudioPlaybackMonitor, mClock, KEY_EVENT_TIMEOUT_MS);

        callback.onPlaybackConfigChanged(configurations);

        List<AudioAttributes> activeAttributes =
                callback.getAllActiveAudioAttributes();

        assertThat(activeAttributes).containsExactly(TEST_MEDIA_AUDIO_ATTRIBUTE);
    }

    @Test
    public void
            getAllActiveContextsForPrimaryZone_withNoActiveConfigurations_returnsEmptyContexts() {
        List<AudioPlaybackConfiguration> activeConfigurations = getAudioPlaybackConfigurations(
                PRIMARY_MEDIA_ADDRESS, PRIMARY_NAVIGATION_ADDRESS);

        ZoneAudioPlaybackCallback callback = new ZoneAudioPlaybackCallback(mPrimaryZone,
                mCarAudioPlaybackMonitor, mClock, KEY_EVENT_TIMEOUT_MS);

        callback.onPlaybackConfigChanged(activeConfigurations);

        List<AudioAttributes> activeAttributes =
                callback.getAllActiveAudioAttributes();

        assertThat(activeAttributes).isEmpty();
    }

    private List<AudioPlaybackConfiguration> getAudioPlaybackConfigurations(
            String primaryMediaAddress, String primaryNavigationAddress) {
        return ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(primaryMediaAddress)
                        .setInactive()
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setDeviceAddress(primaryNavigationAddress)
                        .setInactive()
                        .build()
        );
    }

    @Test
    public void
            getAllActiveContextsForPrimaryZone_withInactiveConfig_beforeTimeout_returnsContexts() {
        List<AudioPlaybackConfiguration> activeConfigurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setDeviceAddress(PRIMARY_NAVIGATION_ADDRESS)
                        .build()
        );

        List<AudioPlaybackConfiguration> configurationsChanged = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setInactive()
                        .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setDeviceAddress(PRIMARY_NAVIGATION_ADDRESS)
                        .build()
        );

        ZoneAudioPlaybackCallback callback = new ZoneAudioPlaybackCallback(mPrimaryZone,
                mCarAudioPlaybackMonitor, mClock, KEY_EVENT_TIMEOUT_MS);

        callback.onPlaybackConfigChanged(activeConfigurations);

        callback.onPlaybackConfigChanged(configurationsChanged);

        when(mClock.uptimeMillis()).thenReturn(TIMER_BEFORE_TIMEOUT_MS);

        List<AudioAttributes> activeAttributes =
                callback.getAllActiveAudioAttributes();

        assertThat(activeAttributes)
                .containsExactly(TEST_MEDIA_AUDIO_ATTRIBUTE,
                        TEST_NAVIGATION_AUDIO_ATTRIBUTE);
    }

    @Test
    public void
            getAllActiveContextsForPrimaryZone_withInactiveConfigs_beforeTimeout_returnsContexts() {
        List<AudioPlaybackConfiguration> activeConfigurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setDeviceAddress(PRIMARY_NAVIGATION_ADDRESS)
                        .build()
        );

        List<AudioPlaybackConfiguration> configurationsChanged = getAudioPlaybackConfigurations(
                PRIMARY_MEDIA_ADDRESS, PRIMARY_NAVIGATION_ADDRESS);

        ZoneAudioPlaybackCallback callback = new ZoneAudioPlaybackCallback(mPrimaryZone,
                mCarAudioPlaybackMonitor, mClock, KEY_EVENT_TIMEOUT_MS);

        callback.onPlaybackConfigChanged(activeConfigurations);

        callback.onPlaybackConfigChanged(configurationsChanged);

        when(mClock.uptimeMillis()).thenReturn(TIMER_BEFORE_TIMEOUT_MS);

        List<AudioAttributes> activeAttributes =
                callback.getAllActiveAudioAttributes();

        assertThat(activeAttributes)
                .containsExactly(TEST_NAVIGATION_AUDIO_ATTRIBUTE,
                        TEST_MEDIA_AUDIO_ATTRIBUTE);
    }

    @Test
    public void
            getAllActiveContextsForPrimaryZone_afterResetStillActiveContexts_returnsEmptyContext() {
        List<AudioPlaybackConfiguration> activeConfigurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setDeviceAddress(PRIMARY_NAVIGATION_ADDRESS)
                        .build()
        );

        List<AudioPlaybackConfiguration> configurationsChanged = getAudioPlaybackConfigurations(
                PRIMARY_MEDIA_ADDRESS, PRIMARY_NAVIGATION_ADDRESS);

        ZoneAudioPlaybackCallback callback = new ZoneAudioPlaybackCallback(mPrimaryZone,
                mCarAudioPlaybackMonitor, mClock, KEY_EVENT_TIMEOUT_MS);

        callback.onPlaybackConfigChanged(activeConfigurations);

        callback.onPlaybackConfigChanged(configurationsChanged);

        callback.resetStillActiveContexts();

        when(mClock.uptimeMillis()).thenReturn(TIMER_BEFORE_TIMEOUT_MS);

        List<AudioAttributes> activeAttributes =
                callback.getAllActiveAudioAttributes();

        assertThat(activeAttributes).isEmpty();
    }

    @Test
    public void
            getAllActiveContextsForPrimaryZone_withInactiveConfig_afterTimeout_returnsContext() {
        List<AudioPlaybackConfiguration> activeConfigurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setDeviceAddress(PRIMARY_NAVIGATION_ADDRESS)
                        .build()
        );

        List<AudioPlaybackConfiguration> configurationsChanged = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setDeviceAddress(PRIMARY_NAVIGATION_ADDRESS)
                        .setInactive()
                        .build()
        );

        ZoneAudioPlaybackCallback callback = new ZoneAudioPlaybackCallback(mPrimaryZone,
                mCarAudioPlaybackMonitor, mClock, KEY_EVENT_TIMEOUT_MS);

        callback.onPlaybackConfigChanged(activeConfigurations);

        callback.onPlaybackConfigChanged(configurationsChanged);

        when(mClock.uptimeMillis()).thenReturn(TIMER_AFTER_TIMEOUT_MS);

        List<AudioAttributes> activeAttributes =
                callback.getAllActiveAudioAttributes();

        assertThat(activeAttributes).containsExactly(TEST_MEDIA_AUDIO_ATTRIBUTE);
    }

    @Test
    public void getAllActiveContextsForPrimaryZone_withInactiveConfigs_afterTimeout_returnsEmpty() {
        List<AudioPlaybackConfiguration> activeConfigurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setDeviceAddress(PRIMARY_NAVIGATION_ADDRESS)
                        .build()
        );

        List<AudioPlaybackConfiguration> configurationsChanged = getAudioPlaybackConfigurations(
                PRIMARY_MEDIA_ADDRESS, PRIMARY_NAVIGATION_ADDRESS);

        ZoneAudioPlaybackCallback callback = new ZoneAudioPlaybackCallback(mPrimaryZone,
                mCarAudioPlaybackMonitor, mClock, KEY_EVENT_TIMEOUT_MS);

        callback.onPlaybackConfigChanged(activeConfigurations);

        callback.onPlaybackConfigChanged(configurationsChanged);

        when(mClock.uptimeMillis()).thenReturn(TIMER_AFTER_TIMEOUT_MS);

        List<AudioAttributes> activeAttributes =
                callback.getAllActiveAudioAttributes();

        assertThat(activeAttributes).isEmpty();
    }


    @Test
    public void
            getAllActiveContextsForPrimaryZone_withMultiActiveConfigs_forDiffZone_returnsEmpty() {
        List<AudioPlaybackConfiguration> activeConfigurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(SECONDARY_MEDIA_ADDRESS)
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setDeviceAddress(SECONDARY_NAVIGATION_ADDRESS)
                        .build()
        );

        ZoneAudioPlaybackCallback callback = new ZoneAudioPlaybackCallback(mPrimaryZone,
                mCarAudioPlaybackMonitor, mClock, KEY_EVENT_TIMEOUT_MS);

        callback.onPlaybackConfigChanged(activeConfigurations);

        List<AudioAttributes> activeAttributes =
                callback.getAllActiveAudioAttributes();

        assertThat(activeAttributes).isEmpty();
    }

    @Test
    public void
            getAllActiveContextsForPrimaryZone_withInactiveConfigs_forDifferentZone_returnsEmpty() {
        List<AudioPlaybackConfiguration> activeConfigurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(SECONDARY_MEDIA_ADDRESS)
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setDeviceAddress(SECONDARY_NAVIGATION_ADDRESS)
                        .build()
        );

        List<AudioPlaybackConfiguration> configurationsChanged = getAudioPlaybackConfigurations(
                SECONDARY_MEDIA_ADDRESS, SECONDARY_NAVIGATION_ADDRESS);

        ZoneAudioPlaybackCallback callback = new ZoneAudioPlaybackCallback(mPrimaryZone,
                mCarAudioPlaybackMonitor, mClock, KEY_EVENT_TIMEOUT_MS);

        callback.onPlaybackConfigChanged(activeConfigurations);

        callback.onPlaybackConfigChanged(configurationsChanged);

        List<AudioAttributes> activeAttributes =
                callback.getAllActiveAudioAttributes();

        assertThat(activeAttributes).isEmpty();
    }

    @Test
    public void onPlaybackConfigChanged_withActiveConfigurations() {
        List<AudioPlaybackConfiguration> activeConfigurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setDeviceAddress(PRIMARY_NAVIGATION_ADDRESS)
                        .build()
        );
        ZoneAudioPlaybackCallback callback = new ZoneAudioPlaybackCallback(mPrimaryZone,
                mCarAudioPlaybackMonitor, mClock, KEY_EVENT_TIMEOUT_MS);

        callback.onPlaybackConfigChanged(activeConfigurations);

        verifyActivationPlaybacks(List.of(TEST_MEDIA_AUDIO_ATTRIBUTE,
                TEST_NAVIGATION_AUDIO_ATTRIBUTE));
    }

    @Test
    public void onPlaybackConfigChanged_withoutActiveConfigurations() {
        List<AudioPlaybackConfiguration> activeConfigurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .setInactive()
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setDeviceAddress(PRIMARY_NAVIGATION_ADDRESS)
                        .setInactive()
                        .build()
        );

        ZoneAudioPlaybackCallback callback = new ZoneAudioPlaybackCallback(mPrimaryZone,
                mCarAudioPlaybackMonitor, mClock, KEY_EVENT_TIMEOUT_MS);

        callback.onPlaybackConfigChanged(activeConfigurations);

        verify(mCarAudioPlaybackMonitor, never()).onActiveAudioPlaybackAttributesAdded(any(),
                anyInt());
    }

    @Test
    public void onPlaybackConfigChanged_withUpdatedPlaybacks() {
        List<AudioPlaybackConfiguration> activeConfigurations = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setDeviceAddress(PRIMARY_NAVIGATION_ADDRESS)
                        .build()
        );
        List<AudioPlaybackConfiguration> configurationsChanged = ImmutableList.of(
                new AudioPlaybackConfigurationBuilder()
                        .setUsage(USAGE_MEDIA)
                        .setDeviceAddress(PRIMARY_MEDIA_ADDRESS)
                        .build(),
                new AudioPlaybackConfigurationBuilder()
                        .setInactive()
                        .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setDeviceAddress(PRIMARY_NAVIGATION_ADDRESS)
                        .build()
        );
        ZoneAudioPlaybackCallback callback = new ZoneAudioPlaybackCallback(mPrimaryZone,
                mCarAudioPlaybackMonitor, mClock, KEY_EVENT_TIMEOUT_MS);

        callback.onPlaybackConfigChanged(activeConfigurations);
        callback.onPlaybackConfigChanged(configurationsChanged);

        verifyActivationPlaybacks(List.of(TEST_MEDIA_AUDIO_ATTRIBUTE,
                TEST_NAVIGATION_AUDIO_ATTRIBUTE));
    }

    private CarAudioZone generatePrimaryZone() {
        CarAudioZoneConfig carAudioZoneConfig =
                new CarAudioZoneConfig.Builder("Primary zone config 0", PRIMARY_ZONE_ID,
                        /* zoneConfigId= */ 0, /* isDefault= */ true)
                        .addVolumeGroup(new VolumeGroupBuilder()
                                .addDeviceAddressAndContexts(TEST_MEDIA_AUDIO_CONTEXT,
                                        PRIMARY_MEDIA_ADDRESS)
                                .build())
                        .addVolumeGroup(new VolumeGroupBuilder()
                                .addDeviceAddressAndContexts(TEST_NAVIGATION_AUDIO_CONTEXT,
                                        PRIMARY_NAVIGATION_ADDRESS)
                                .build())
                        .addVolumeGroup(new VolumeGroupBuilder()
                                .addDeviceAddressAndContexts(TEST_ASSISTANT_CONTEXT,
                                        PRIMARY_VOICE_ADDRESS)
                                .build())
                        .build();
        return new TestCarAudioZoneBuilder("Primary zone", PRIMARY_ZONE_ID)
                .addCarAudioZoneConfig(carAudioZoneConfig)
                .build();

    }

    private void verifyActivationPlaybacks(List<AudioAttributes> newlyActiveAudioAttributes) {
        verify(mCarAudioPlaybackMonitor).onActiveAudioPlaybackAttributesAdded(
                mAudioAttributesCaptor.capture(), eq(PRIMARY_ZONE_ID));
        assertWithMessage("Audio attributes for newly active playbacks")
                .that(mAudioAttributesCaptor.getValue())
                .containsExactlyElementsIn(newlyActiveAudioAttributes);
    }
}
