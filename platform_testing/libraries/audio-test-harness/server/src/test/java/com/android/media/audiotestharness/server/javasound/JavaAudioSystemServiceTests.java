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

package com.android.media.audiotestharness.server.javasound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.media.audiotestharness.common.Defaults;
import com.android.media.audiotestharness.proto.AudioDeviceOuterClass.AudioDevice;
import com.android.media.audiotestharness.proto.AudioFormatOuterClass.AudioFormat;

import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/** Tests for the {@link JavaAudioSystemService} class. */
@RunWith(JUnit4.class)
public class JavaAudioSystemServiceTests {

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock JavaAudioSystem mJavaAudioSystem;
    @Mock JavaAudioCapturerFactory mAudioCapturerFactory;
    @Mock TargetDataLine mTargetDataLine;

    private JavaAudioSystemService mJavaAudioSystemService;

    @Before
    public void setUp() throws Exception {
        mJavaAudioSystemService =
                new JavaAudioSystemService(mJavaAudioSystem, mAudioCapturerFactory);
    }

    @Test
    public void getDevices_returnsEmptyList_noConfiguredDevices() {
        when(mJavaAudioSystem.getMixerInfo()).thenReturn(new Mixer.Info[0]);
        assertEquals(0, mJavaAudioSystemService.getDevices().size());
    }

    @Test
    public void getDevices_returnsProperDevices_twoConfiguredDevices() throws Exception {
        configureJavaSoundSystemMockWithMixers(
                new TestMixer(
                        /* name= */ "Mixer One",
                        /* targetLineCount= */ 2,
                        /* targetDataLine= */ null),
                new TestMixer(
                        /* name= */ "Mixer Two",
                        /* targetLineCount= */ 0,
                        /* targetDataLine= */ null));

        ImmutableSet<AudioDevice> expectedDevices =
                ImmutableSet.of(
                        AudioDevice.newBuilder()
                                .setName("Mixer One")
                                .addCapabilities(AudioDevice.Capability.CAPTURE)
                                .build(),
                        AudioDevice.newBuilder().setName("Mixer Two").build());
        ImmutableSet<AudioDevice> actualDevices = mJavaAudioSystemService.getDevices();
        assertEquals(expectedDevices, actualDevices);
    }

    @Test
    public void createCapturerFor_buildsAudioCapturerWithFactoryCorrectly() throws Exception {
        configureJavaSoundSystemMockWithMixers(
                new TestMixer(
                        /* name= */ "Mixer",
                        /* targetLineCount= */ 1,
                        /* targetDataLine= */ mTargetDataLine));

        mJavaAudioSystemService.createCapturerFor(
                AudioDevice.getDefaultInstance(), AudioFormat.getDefaultInstance());

        verify(mAudioCapturerFactory)
                .build(
                        AudioDevice.getDefaultInstance(),
                        AudioFormat.getDefaultInstance(),
                        mTargetDataLine);
    }

    @Test
    public void createCapturerFor_selectsProperMixerFromMultiple_forName() throws Exception {
        configureJavaSoundSystemMockWithMixers(
                new TestMixer(
                        /* name= */ "Right Mixer",
                        /* targetLineCount= */ 1,
                        /* targetDataLine= */ mTargetDataLine),
                new TestMixer(
                        /* name= */ "Wrong Mixer 1",
                        /* targetLineCount= */ 0,
                        /* targetDataLine= */ null),
                new TestMixer(
                        /* name= */ "Wrong Mixer 2",
                        /* targetLineCount= */ 2,
                        /* targetDataLine= */ null));
        AudioDevice audioDevice = AudioDevice.newBuilder().setName("Right").build();

        mJavaAudioSystemService.createCapturerFor(audioDevice, AudioFormat.getDefaultInstance());

        verify(mAudioCapturerFactory)
                .build(audioDevice, AudioFormat.getDefaultInstance(), mTargetDataLine);
    }

    @Test
    public void createCapturerFor_selectsProperMixerFromMultiple_Capability() throws Exception {
        configureJavaSoundSystemMockWithMixers(
                new TestMixer(
                        /* name= */ "Right Mixer",
                        /* targetLineCount= */ 1,
                        /* targetDataLine= */ mTargetDataLine),
                new TestMixer(
                        /* name= */ "Wrong Mixer 1",
                        /* targetLineCount= */ 0,
                        /* targetDataLine= */ null),
                new TestMixer(
                        /* name= */ "Wrong Mixer 2",
                        /* targetLineCount= */ 0,
                        /* targetDataLine= */ null));
        AudioDevice audioDevice =
                AudioDevice.newBuilder().addCapabilities(AudioDevice.Capability.CAPTURE).build();

        mJavaAudioSystemService.createCapturerFor(audioDevice, AudioFormat.getDefaultInstance());

        verify(mAudioCapturerFactory)
                .build(audioDevice, AudioFormat.getDefaultInstance(), mTargetDataLine);
    }

    @Test
    public void createCapturerFor_opensTargetDataLineCorrectly() throws Exception {
        configureJavaSoundSystemMockWithMixers(
                new TestMixer(
                        /* name= */ "Mixer",
                        /* targetLineCount= */ 1,
                        /* targetDataLine= */ mTargetDataLine));

        ArgumentCaptor<javax.sound.sampled.AudioFormat> formatArgumentCaptor =
                ArgumentCaptor.forClass(javax.sound.sampled.AudioFormat.class);

        mJavaAudioSystemService.createCapturerFor(
                AudioDevice.newBuilder().setName("Mixer").build(), Defaults.AUDIO_FORMAT);

        verify(mTargetDataLine).open(formatArgumentCaptor.capture());
        javax.sound.sampled.AudioFormat format = formatArgumentCaptor.getValue();
        assertTrue(JavaSoundUtility.audioFormatFrom(Defaults.AUDIO_FORMAT).matches(format));
    }

    @Test(expected = IOException.class)
    public void createCapturerFor_throwsIOException_failureToOpenTargetDataLine() throws Exception {
        configureJavaSoundSystemMockWithMixers(
                new TestMixer(
                        /* name= */ "Mixer",
                        /* targetLineCount= */ 1,
                        /* targetDataLine= */ mTargetDataLine));
        doThrow(LineUnavailableException.class).when(mTargetDataLine).open(any());

        mJavaAudioSystemService.createCapturerFor(Defaults.AUDIO_DEVICE, Defaults.AUDIO_FORMAT);
    }

    @Test(expected = IOException.class)
    public void createCapturerFor_throwsIOException_failureToGetTargetDataLine() throws Exception {
        configureJavaSoundSystemMockWithMixers(
                new TestMixer(
                        /* name= */ "Mixer", /* targetLineCount= */ 1, /* targetDataLine= */ null));

        mJavaAudioSystemService.createCapturerFor(Defaults.AUDIO_DEVICE, Defaults.AUDIO_FORMAT);
    }

    @Test(expected = IOException.class)
    public void createCapturerFor_throwsIOException_noMixerFound() throws Exception {
        when(mJavaAudioSystem.getMixerInfo()).thenReturn(new Mixer.Info[0]);

        mJavaAudioSystemService.createCapturerFor(Defaults.AUDIO_DEVICE, Defaults.AUDIO_FORMAT);
    }

    public void configureJavaSoundSystemMockWithMixers(TestMixer... mixers) {
        Mixer.Info[] mixerInfos = new Mixer.Info[mixers.length];

        for (int i = 0; i < mixers.length; i++) {
            mixerInfos[i] = mixers[i].getMixerInfo();
            when(mJavaAudioSystem.getMixer(eq(mixerInfos[i]))).thenReturn(mixers[i]);
        }

        when(mJavaAudioSystem.getMixerInfo()).thenReturn(mixerInfos);
    }
}
