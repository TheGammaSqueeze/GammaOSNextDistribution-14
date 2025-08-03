/*
 * Copyright (C) 2024 The Android Open Source Project
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.car.test.AbstractExpectableTestCase;
import android.media.AudioAttributes;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public final class CarAudioPlaybackMonitorTest extends AbstractExpectableTestCase {

    private static final int PRIMARY_ZONE_ID = 0;
    private static final AudioAttributes TEST_MEDIA_AUDIO_ATTRIBUTE =
            new AudioAttributes.Builder().setUsage(USAGE_MEDIA).build();
    private static final AudioAttributes TEST_NAVIGATION_AUDIO_ATTRIBUTE =
            new AudioAttributes.Builder().setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).build();

    private CarAudioPlaybackMonitor mCarAudioPlaybackMonitor;

    @Mock
    private CarAudioService mMockCarAudioService;
    @Captor
    private ArgumentCaptor<List<AudioAttributes>> mAudioAttributesCaptor;

    @Before
    public void setup() {
        mCarAudioPlaybackMonitor = new CarAudioPlaybackMonitor(mMockCarAudioService);
    }

    @Test
    public void construct_withNullCarAudioService_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> new CarAudioPlaybackMonitor(/* carAudioService= */ null));

        expectWithMessage("Car playback monitor construction exception")
                .that(thrown).hasMessageThat().contains("Car audio service can not be null");
    }

    @Test
    public void onActiveAudioPlaybackAttributesAdded_withNullAttributesList() {
        mCarAudioPlaybackMonitor.onActiveAudioPlaybackAttributesAdded(
                /* newActivePlaybackAttributes= */ null, PRIMARY_ZONE_ID);

        verify(mMockCarAudioService, never()).handleActivationVolumeWithAudioAttributes(any(),
                eq(PRIMARY_ZONE_ID));
    }

    @Test
    public void onActiveAudioPlaybackAttributesAdded_withEmptyAttributesList() {
        mCarAudioPlaybackMonitor.onActiveAudioPlaybackAttributesAdded(Collections.emptyList(),
                PRIMARY_ZONE_ID);

        verify(mMockCarAudioService, never()).handleActivationVolumeWithAudioAttributes(any(),
                eq(PRIMARY_ZONE_ID));
    }

    @Test
    public void onActiveAudioPlaybackAttributesAdded() {
        mCarAudioPlaybackMonitor.onActiveAudioPlaybackAttributesAdded(List.of(
                TEST_MEDIA_AUDIO_ATTRIBUTE, TEST_NAVIGATION_AUDIO_ATTRIBUTE), PRIMARY_ZONE_ID);

        verify(mMockCarAudioService).handleActivationVolumeWithAudioAttributes(
                mAudioAttributesCaptor.capture(), eq(PRIMARY_ZONE_ID));
        expectWithMessage("Audio attributes for playback configuration")
                .that(mAudioAttributesCaptor.getValue())
                .containsExactly(TEST_MEDIA_AUDIO_ATTRIBUTE, TEST_NAVIGATION_AUDIO_ATTRIBUTE);
    }
}
