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

package android.voiceinteraction.cts.unittests;

import static android.media.AudioFormat.CHANNEL_IN_FRONT;

import static com.google.common.truth.Truth.assertThat;

import android.hardware.soundtrigger.SoundTrigger;
import android.media.AudioFormat;
import android.os.ParcelFileDescriptor;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectedResult;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

/**
 * Unit test for the {@link AlwaysOnHotwordDetector.EventPayload} class
 */
@RunWith(AndroidJUnit4.class)
public class AlwaysOnHotwordDetectorEventPayloadTest {

    @Test
    public void testEventPayload_verifyDefaultValues() {
        final AlwaysOnHotwordDetector.EventPayload eventPayload =
                new AlwaysOnHotwordDetector.EventPayload.Builder().build();
        assertThat(eventPayload.getCaptureAudioFormat()).isNull();
        assertThat(eventPayload.getTriggerAudio()).isNull();
        assertThat(eventPayload.getDataFormat()).isEqualTo(
                AlwaysOnHotwordDetector.EventPayload.DATA_FORMAT_RAW);
        assertThat(eventPayload.getData()).isNull();
        assertThat(eventPayload.getHotwordDetectedResult()).isNull();
        assertThat(eventPayload.getAudioStream()).isNull();
        assertThat(eventPayload.getKeyphraseRecognitionExtras()).isEmpty();
    }

    @Test
    public void testEventPayload_getCaptureAudioFormat() {
        AudioFormat audioFormat =
                new AudioFormat.Builder()
                        .setSampleRate(32000)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
        final AlwaysOnHotwordDetector.EventPayload eventPayload =
                new AlwaysOnHotwordDetector.EventPayload.Builder()
                        .setCaptureAudioFormat(audioFormat)
                        .build();
        assertThat(eventPayload.getCaptureAudioFormat()).isEqualTo(audioFormat);
    }

    @Test
    public void testEventPayload_getTriggerAudio_noTriggerInData_dataNonNull() {
        byte[] data = new byte[]{0, 1, 2, 3, 4};
        final AlwaysOnHotwordDetector.EventPayload eventPayload =
                new AlwaysOnHotwordDetector.EventPayload.Builder()
                        .setData(data)
                        .build();
        assertThat(eventPayload.getDataFormat()).isEqualTo(
                AlwaysOnHotwordDetector.EventPayload.DATA_FORMAT_RAW);
        assertThat(eventPayload.getTriggerAudio()).isNull();
        assertThat(eventPayload.getData()).isEqualTo(data);
    }

    @Test
    public void testEventPayload_getTriggerAudio_triggerInData_dataNonNull() {
        byte[] data = new byte[]{0, 1, 2, 3, 4};
        final AlwaysOnHotwordDetector.EventPayload eventPayload =
                new AlwaysOnHotwordDetector.EventPayload.Builder()
                        .setData(data)
                        .setDataFormat(
                                AlwaysOnHotwordDetector.EventPayload.DATA_FORMAT_TRIGGER_AUDIO)
                        .build();
        assertThat(eventPayload.getDataFormat()).isEqualTo(
                AlwaysOnHotwordDetector.EventPayload.DATA_FORMAT_TRIGGER_AUDIO);
        assertThat(eventPayload.getTriggerAudio()).isEqualTo(data);
        assertThat(eventPayload.getData()).isEqualTo(data);
    }

    @Test
    public void testEventPayload_getHotwordDetectedResult() {
        HotwordDetectedResult hotwordDetectedResult = new HotwordDetectedResult.Builder()
                .setAudioChannel(CHANNEL_IN_FRONT)
                .setConfidenceLevel(HotwordDetectedResult.CONFIDENCE_LEVEL_HIGH)
                .setHotwordDetectionPersonalized(true)
                .setHotwordDurationMillis(1000)
                .setHotwordOffsetMillis(500)
                .setHotwordPhraseId(5)
                .setPersonalizedScore(10)
                .setScore(15)
                .setBackgroundAudioPower(50)
                .build();
        final AlwaysOnHotwordDetector.EventPayload eventPayload =
                new AlwaysOnHotwordDetector.EventPayload.Builder()
                        .setHotwordDetectedResult(hotwordDetectedResult)
                        .build();
        assertThat(eventPayload.getHotwordDetectedResult()).isEqualTo(hotwordDetectedResult);
    }

    @Test
    public void testEventPayload_getAudioStream() throws Exception {
        ParcelFileDescriptor fileDescriptor = ParcelFileDescriptor.open(new File("/dev/null"),
                ParcelFileDescriptor.MODE_READ_ONLY);
        final AlwaysOnHotwordDetector.EventPayload eventPayload =
                new AlwaysOnHotwordDetector.EventPayload.Builder()
                        .setAudioStream(fileDescriptor)
                        .build();
        assertThat(eventPayload.getAudioStream()).isEqualTo(fileDescriptor);
    }

    @Test
    public void testEventPayload_getKeyphraseRecognitionExtras() {
        final int firstKeyphraseId = 1;
        final int secondKeyphraseId = 2;
        final int firstKephraseRecognitionMode = SoundTrigger.RECOGNITION_MODE_VOICE_TRIGGER;
        final int secondKephraseRecognitionMode = SoundTrigger.RECOGNITION_MODE_USER_IDENTIFICATION
                | SoundTrigger.RECOGNITION_MODE_VOICE_TRIGGER;
        final int firstCoarseConfidenceLevel = 98;
        final int secondCoarseConfidenceLevel = 97;
        SoundTrigger.KeyphraseRecognitionExtra firstKeyphraseExtra =
                new SoundTrigger.KeyphraseRecognitionExtra(firstKeyphraseId,
                        firstKephraseRecognitionMode, firstCoarseConfidenceLevel);
        SoundTrigger.KeyphraseRecognitionExtra secondKeyphraseExtra =
                new SoundTrigger.KeyphraseRecognitionExtra(
                        secondKeyphraseId, secondKephraseRecognitionMode,
                        secondCoarseConfidenceLevel);
        List<SoundTrigger.KeyphraseRecognitionExtra> keyphraseRecognitionExtras = ImmutableList.of(
                firstKeyphraseExtra, secondKeyphraseExtra);

        final AlwaysOnHotwordDetector.EventPayload eventPayload =
                new AlwaysOnHotwordDetector.EventPayload.Builder()
                        .setKeyphraseRecognitionExtras(keyphraseRecognitionExtras)
                        .build();
        assertThat(eventPayload.getKeyphraseRecognitionExtras()).isEqualTo(
                keyphraseRecognitionExtras);
    }
}
