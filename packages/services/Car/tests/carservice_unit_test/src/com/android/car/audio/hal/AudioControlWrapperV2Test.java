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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.hardware.audio.common.PlaybackTrackMetadata;
import android.hardware.automotive.audiocontrol.V2_0.IAudioControl;
import android.hardware.automotive.audiocontrol.V2_0.ICloseHandle;
import android.hardware.automotive.audiocontrol.V2_0.IFocusListener;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public final class AudioControlWrapperV2Test {
    private static final float FADE_VALUE = 5;
    private static final float BALANCE_VALUE = 6;
    private static final int USAGE = AudioAttributes.USAGE_MEDIA;
    private static final int ZONE_ID = 2;
    private static final int FOCUS_GAIN = AudioManager.AUDIOFOCUS_GAIN;
    private static final PlaybackTrackMetadata METADATA = usageToMetadata(USAGE);

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private IAudioControl mAudioControlV2;

    private AudioControlWrapperV2 mAudioControlWrapperV2;

    @Before
    public void setUp() {
        mAudioControlWrapperV2 = new AudioControlWrapperV2(mAudioControlV2);
    }

    @Test
    public void setFadeTowardFront_succeeds() throws Exception {
        mAudioControlWrapperV2.setFadeTowardFront(FADE_VALUE);

        verify(mAudioControlV2).setFadeTowardFront(FADE_VALUE);
    }

    @Test
    public void setBalanceTowardRight_succeeds() throws Exception {
        mAudioControlWrapperV2.setBalanceTowardRight(BALANCE_VALUE);

        verify(mAudioControlV2).setBalanceTowardRight(BALANCE_VALUE);
    }

    @Test
    public void supportsFeature_audioFocus_returnsTrue() {
        assertThat(mAudioControlWrapperV2.supportsFeature(AUDIOCONTROL_FEATURE_AUDIO_FOCUS))
                .isTrue();
    }

    @Test
    public void supportsFeature_withUnknownFeature_returnsFalse() {
        assertThat(mAudioControlWrapperV2.supportsFeature(-1)).isFalse();
    }

    @Test
    public void supportsFeature_withAudioDucking_returnsFalse() {
        assertThat(mAudioControlWrapperV2.supportsFeature(AUDIOCONTROL_FEATURE_AUDIO_DUCKING))
                .isFalse();
    }

    @Test
    public void supportsFeature_withAudioMuting_returnsFalse() {
        assertThat(mAudioControlWrapperV2.supportsFeature(AUDIOCONTROL_FEATURE_AUDIO_GROUP_MUTING))
                .isFalse();
    }

    @Test
    public void registerFocusListener_succeeds() throws Exception {
        HalFocusListener mockListener = mock(HalFocusListener.class);

        mAudioControlWrapperV2.registerFocusListener(mockListener);

        verify(mAudioControlV2).registerFocusListener(any(IFocusListener.class));
    }

    @Test
    public void registerFocusListener_throws() throws Exception {
        doThrow(new RemoteException()).when(mAudioControlV2)
                .registerFocusListener(any(IFocusListener.class));
        HalFocusListener mockListener = mock(HalFocusListener.class);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mAudioControlWrapperV2.registerFocusListener(mockListener));

        assertWithMessage("Exception thrown when registerFocusListener failed")
                .that(thrown).hasMessageThat()
                .contains("IAudioControl#registerFocusListener failed");
    }

    @Test
    public void unregisterFocusListener_closesHandle() throws Exception {
        HalFocusListener mockListener = mock(HalFocusListener.class);
        ICloseHandle mockCloseHandle = mock(ICloseHandle.class);
        when(mAudioControlV2.registerFocusListener(any(IFocusListener.class)))
                .thenReturn(mockCloseHandle);
        mAudioControlWrapperV2.registerFocusListener(mockListener);

        mAudioControlWrapperV2.unregisterFocusListener();

        verify(mockCloseHandle).close();
    }

    @Test
    public void requestAudioFocus_forFocusListenerWrapper_succeeds() throws Exception {
        HalFocusListener mockListener = mock(HalFocusListener.class);
        ArgumentCaptor<IFocusListener.Stub> captor =
                ArgumentCaptor.forClass(IFocusListener.Stub.class);
        mAudioControlWrapperV2.registerFocusListener(mockListener);
        verify(mAudioControlV2).registerFocusListener(captor.capture());

        captor.getValue().requestAudioFocus(USAGE, ZONE_ID, FOCUS_GAIN);

        verify(mockListener).requestAudioFocus(METADATA, ZONE_ID, FOCUS_GAIN);
    }

    @Test
    public void abandonAudioFocus_forFocusListenerWrapper_succeeds() throws Exception {
        HalFocusListener mockListener = mock(HalFocusListener.class);
        ArgumentCaptor<IFocusListener.Stub> captor =
                ArgumentCaptor.forClass(IFocusListener.Stub.class);
        mAudioControlWrapperV2.registerFocusListener(mockListener);
        verify(mAudioControlV2).registerFocusListener(captor.capture());

        captor.getValue().abandonAudioFocus(USAGE, ZONE_ID);

        verify(mockListener).abandonAudioFocus(METADATA, ZONE_ID);
    }

    @Test
    public void onAudioFocusChange_succeeds() throws Exception {
        mAudioControlWrapperV2.onAudioFocusChange(METADATA, ZONE_ID, FOCUS_GAIN);

        verify(mAudioControlV2).onAudioFocusChange(USAGE, ZONE_ID, FOCUS_GAIN);
    }

    @Test
    public void onAudioFocusChange_throws() throws Exception {
        doThrow(new RemoteException()).when(mAudioControlV2)
                .onAudioFocusChange(anyInt(), anyInt(), anyInt());

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mAudioControlWrapperV2.onAudioFocusChange(METADATA, ZONE_ID, FOCUS_GAIN));

        assertWithMessage("Exception thrown when onAudioFocusChange failed")
                .that(thrown).hasMessageThat()
                .contains("Failed to query IAudioControl#onAudioFocusChange");
    }

    @Test
    public void onDevicesToDuckChange_throws() {
        assertThrows(UnsupportedOperationException.class,
                () -> mAudioControlWrapperV2.onDevicesToDuckChange(null));
    }

    @Test
    public void onDevicesToMuteChange_throws() {
        UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class,
                () -> mAudioControlWrapperV2.onDevicesToMuteChange(new ArrayList<>()));

        assertWithMessage("UnsupportedOperationException thrown by onDevicesToMute")
                .that(thrown).hasMessageThat()
                .contains("unsupported for IAudioControl@2.0");
    }

    @Test
    public void registerAudioGainCallback_throws() {
        HalAudioGainCallback gainCallback = mock(HalAudioGainCallback.class);

        UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class,
                () -> mAudioControlWrapperV2.registerAudioGainCallback(gainCallback));

        assertWithMessage("UnsupportedOperationException thrown by registerAudioGainCallback")
                .that(thrown).hasMessageThat()
                .contains("Audio Gain Callback is unsupported for IAudioControl@2.0");
    }

    @Test
    public void unregisterAudioGainCallback_throws() {
        UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class,
                () -> mAudioControlWrapperV2.unregisterAudioGainCallback());

        assertWithMessage("UnsupportedOperationException thrown by unregisterAudioGainCallback")
                .that(thrown).hasMessageThat()
                .contains("Audio Gain Callback is unsupported for IAudioControl@2.0");
    }

    @Test
    public void linkToDeath_succeeds() throws Exception {
        AudioControlWrapper.AudioControlDeathRecipient deathRecipient =
                mock(AudioControlWrapper.AudioControlDeathRecipient.class);

        mAudioControlWrapperV2.linkToDeath(deathRecipient);

        verify(mAudioControlV2).linkToDeath(any(), eq(0L));
    }

    @Test
    public void linkToDeath_throws() throws Exception {
        doThrow(new RemoteException()).when(mAudioControlV2).linkToDeath(any(), eq(0L));
        AudioControlWrapper.AudioControlDeathRecipient deathRecipient =
                mock(AudioControlWrapper.AudioControlDeathRecipient.class);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mAudioControlWrapperV2.linkToDeath(deathRecipient));

        assertWithMessage("Exception thrown when linkToDeath failed")
                .that(thrown).hasMessageThat()
                .contains("Call to IAudioControl@2.0#linkToDeath failed");
    }

    @Test
    public void unlinkToDeath_succeeds() throws Exception {
        AudioControlWrapper.AudioControlDeathRecipient deathRecipient =
                mock(AudioControlWrapper.AudioControlDeathRecipient.class);
        mAudioControlWrapperV2.linkToDeath(deathRecipient);

        mAudioControlWrapperV2.unlinkToDeath();

        verify(mAudioControlV2).unlinkToDeath(any());
    }

    @Test
    public void unlinkToDeath_throws() throws Exception {
        doThrow(new RemoteException()).when(mAudioControlV2).unlinkToDeath(any());
        AudioControlWrapper.AudioControlDeathRecipient deathRecipient =
                mock(AudioControlWrapper.AudioControlDeathRecipient.class);
        mAudioControlWrapperV2.linkToDeath(deathRecipient);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mAudioControlWrapperV2.unlinkToDeath());

        assertWithMessage("Exception thrown when unlinkToDeath failed")
                .that(thrown).hasMessageThat()
                .contains("Call to IAudioControl@2.0#unlinkToDeath failed");
    }
}
