/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.audio.hal;

import static com.android.car.audio.CarHalAudioUtils.usageToMetadata;
import static com.android.car.audio.hal.AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_DUCKING;
import static com.android.car.audio.hal.AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_FOCUS;
import static com.android.car.audio.hal.AudioControlWrapper.AUDIOCONTROL_FEATURE_AUDIO_GROUP_MUTING;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.hardware.audio.common.PlaybackTrackMetadata;
import android.hardware.automotive.audiocontrol.V1_0.IAudioControl;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public final class AudioControlWrapperV1Test {
    private static final float FADE_VALUE = 5;
    private static final float BALANCE_VALUE = 6;
    private static final int CONTEXT_NUMBER = 3;
    private static final int USAGE = AudioAttributes.USAGE_MEDIA;
    private static final int ZONE_ID = 2;
    private static final int FOCUS_GAIN = AudioManager.AUDIOFOCUS_GAIN;
    private static final PlaybackTrackMetadata METADATA = usageToMetadata(USAGE);

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private IAudioControl mAudioControlV1;

    private AudioControlWrapperV1 mAudioControlWrapperV1;

    @Before
    public void setUp() {
        mAudioControlWrapperV1 = new AudioControlWrapperV1(mAudioControlV1);
    }

    @Test
    public void setFadeTowardFront_succeeds() throws Exception {
        mAudioControlWrapperV1.setFadeTowardFront(FADE_VALUE);

        verify(mAudioControlV1).setFadeTowardFront(FADE_VALUE);
    }

    @Test
    public void setBalanceTowardRight_succeeds() throws Exception {
        mAudioControlWrapperV1.setBalanceTowardRight(BALANCE_VALUE);

        verify(mAudioControlV1).setBalanceTowardRight(BALANCE_VALUE);
    }

    @Test
    public void getBusForContext_returnsBusNumber() throws Exception {
        int busNumber = 1;
        when(mAudioControlV1.getBusForContext(CONTEXT_NUMBER)).thenReturn(busNumber);

        int actualBus = mAudioControlWrapperV1.getBusForContext(CONTEXT_NUMBER);
        assertThat(actualBus).isEqualTo(busNumber);
    }

    @Test
    public void getBusForContext_throws() throws Exception {
        doThrow(new RemoteException()).when(mAudioControlV1).getBusForContext(CONTEXT_NUMBER);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mAudioControlWrapperV1.getBusForContext(CONTEXT_NUMBER));

        assertWithMessage("Exception thrown when getBusForContext failed")
                .that(thrown).hasMessageThat()
                .contains("Failed to query IAudioControl#getBusForContext");
    }

    @Test
    public void supportsFeature_withAudioFocus_returnsFalse() {
        assertThat(mAudioControlWrapperV1.supportsFeature(AUDIOCONTROL_FEATURE_AUDIO_FOCUS))
                .isFalse();
    }

    @Test
    public void supportsFeature_withAudioDucking_returnsFalse() {
        assertThat(mAudioControlWrapperV1.supportsFeature(AUDIOCONTROL_FEATURE_AUDIO_DUCKING))
                .isFalse();
    }

    @Test
    public void supportsFeature_withAudioMuting_returnsFalse() {
        assertThat(mAudioControlWrapperV1.supportsFeature(AUDIOCONTROL_FEATURE_AUDIO_GROUP_MUTING))
                .isFalse();
    }

    @Test
    public void supportsFeature_withUnknownFeature_returnsFalse() {
        assertThat(mAudioControlWrapperV1.supportsFeature(-1)).isFalse();
    }

    @Test
    public void registerFocusListener_throws() {
        HalFocusListener mockListener = mock(HalFocusListener.class);

        assertThrows(UnsupportedOperationException.class,
                () -> mAudioControlWrapperV1.registerFocusListener(mockListener));
    }

    @Test
    public void unregisterFocusListener_throws() {
        assertThrows(UnsupportedOperationException.class,
                () -> mAudioControlWrapperV1.unregisterFocusListener());
    }

    @Test
    public void onAudioFocusChange_throws() {
        assertThrows(UnsupportedOperationException.class,
                () -> mAudioControlWrapperV1.onAudioFocusChange(METADATA, ZONE_ID, FOCUS_GAIN));
    }

    @Test
    public void onDevicesToDuckChange_throws() {
        assertThrows(UnsupportedOperationException.class,
                () -> mAudioControlWrapperV1.onDevicesToDuckChange(null));
    }

    @Test
    public void onDevicesToMuteChange_throws() {
        UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class,
                () -> mAudioControlWrapperV1.onDevicesToMuteChange(new ArrayList<>()));

        assertWithMessage("UnsupportedOperationException thrown by onDevicesToMute")
                .that(thrown).hasMessageThat()
                .contains("unsupported for IAudioControl@1.0");
    }

    @Test
    public void registerAudioGainCallback_throws() {
        HalAudioGainCallback halAudioGainCallback = mock(HalAudioGainCallback.class);

        UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class,
                () -> mAudioControlWrapperV1.registerAudioGainCallback(halAudioGainCallback));

        assertWithMessage("UnsupportedOperationException thrown by registerAudioGainCallback")
                .that(thrown).hasMessageThat()
                .contains("unsupported for IAudioControl@1.0");
    }

    @Test
    public void unregisterAudioGainCallback_throws() {
        UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class,
                () -> mAudioControlWrapperV1.unregisterAudioGainCallback());

        assertWithMessage("UnsupportedOperationException thrown by unregisterAudioGainCallback")
                .that(thrown).hasMessageThat()
                .contains("unsupported for IAudioControl@1.0");
    }

    @Test
    public void linkToDeath_succeeds() throws Exception {
        AudioControlWrapper.AudioControlDeathRecipient deathRecipient =
                mock(AudioControlWrapper.AudioControlDeathRecipient.class);

        mAudioControlWrapperV1.linkToDeath(deathRecipient);

        verify(mAudioControlV1).linkToDeath(any(), eq(0L));
    }

    @Test
    public void linkToDeath_throws() throws Exception {
        doThrow(new RemoteException()).when(mAudioControlV1).linkToDeath(any(), eq(0L));
        AudioControlWrapper.AudioControlDeathRecipient deathRecipient =
                mock(AudioControlWrapper.AudioControlDeathRecipient.class);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mAudioControlWrapperV1.linkToDeath(deathRecipient));

        assertWithMessage("Exception thrown when linkToDeath failed")
                .that(thrown).hasMessageThat()
                .contains("Call to IAudioControl@1.0#linkToDeath failed");
    }

    @Test
    public void unlinkToDeath_succeeds() throws Exception {
        AudioControlWrapper.AudioControlDeathRecipient deathRecipient =
                mock(AudioControlWrapper.AudioControlDeathRecipient.class);
        mAudioControlWrapperV1.linkToDeath(deathRecipient);

        mAudioControlWrapperV1.unlinkToDeath();

        verify(mAudioControlV1).unlinkToDeath(any());
    }

    @Test
    public void unlinkToDeath_throws() throws Exception {
        doThrow(new RemoteException()).when(mAudioControlV1).unlinkToDeath(any());
        AudioControlWrapper.AudioControlDeathRecipient deathRecipient =
                mock(AudioControlWrapper.AudioControlDeathRecipient.class);
        mAudioControlWrapperV1.linkToDeath(deathRecipient);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mAudioControlWrapperV1.unlinkToDeath());

        assertWithMessage("Exception thrown when unlinkToDeath failed")
                .that(thrown).hasMessageThat()
                .contains("Call to IAudioControl@1.0#unlinkToDeath failed");
    }
}
