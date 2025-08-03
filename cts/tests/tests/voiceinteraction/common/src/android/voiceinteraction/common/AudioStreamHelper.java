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
package android.voiceinteraction.common;

import static com.google.common.truth.Truth.assertThat;

import android.media.AudioFormat;
import android.os.ParcelFileDescriptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AudioStreamHelper {

    public static final byte[] FAKE_INITIAL_AUDIO_DATA =
            new byte[]{'h', 'o', 't', 'w', 'o', 'r', 'd', '!'};
    public static final byte[] FAKE_HOTWORD_AUDIO_STREAM_DATA =
            new byte[]{'s', 't', 'r', 'e', 'a', 'm', '!'};
    public static final AudioFormat FAKE_AUDIO_FORMAT =
            new AudioFormat.Builder()
                    .setSampleRate(32000)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();

    public static ParcelFileDescriptor[] createFakeAudioStreamPipe(byte[] audioData)
            throws IOException {
        ParcelFileDescriptor[] parcelFileDescriptors = ParcelFileDescriptor.createPipe();
        try (OutputStream fos =
                     new ParcelFileDescriptor.AutoCloseOutputStream(parcelFileDescriptors[1])) {
            fos.write(audioData);
        }
        return parcelFileDescriptors;
    }

    public static void closeAudioStreamPipe(ParcelFileDescriptor[] parcelFileDescriptors)
            throws IOException {
        if (parcelFileDescriptors != null) {
            parcelFileDescriptors[0].close();
            parcelFileDescriptors[1].close();
        }
    }

    public static void assertAudioStream(ParcelFileDescriptor audioStream, byte[] expected)
            throws IOException {
        try (InputStream audioSource = new ParcelFileDescriptor.AutoCloseInputStream(audioStream)) {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = audioSource.read(buffer)) != -1) {
                result.write(buffer, 0, count);
            }
            assertThat(result.toByteArray()).isEqualTo(expected);
        }
    }
}
