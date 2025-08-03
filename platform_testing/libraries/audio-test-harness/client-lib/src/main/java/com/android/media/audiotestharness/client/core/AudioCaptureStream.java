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

package com.android.media.audiotestharness.client.core;

import com.android.media.audiotestharness.common.Defaults;
import com.android.media.audiotestharness.proto.AudioFormatOuterClass.AudioFormat;

import java.io.IOException;
import java.io.InputStream;

/**
 * {@link InputStream} that provides access to raw audio samples returned by a Audio Capture Session
 * while also providing access to helper methods to interact with the session.
 */
public abstract class AudioCaptureStream extends InputStream {

    /**
     * Read method that reads a number of audio samples, up to the max into the provided array
     * starting at offset. These samples are expected to be signed 16-bit values and thus are
     * provided as an array of short values.
     *
     * <p>This method blocks until at least one complete sample is read, and ensures that no
     * incomplete samples are read.
     *
     * @param samples the array to read into.
     * @param offset the offset to use when writing into samples.
     * @param len the maximum number of samples to read.
     * @return the number of samples read into the provided array.
     */
    public abstract int read(short[] samples, int offset, int len) throws IOException;

    /**
     * Returns the {@link AudioFormat} corresponding to this {@link AudioCaptureStream}, thus the
     * raw data exposed by this stream will be raw PCM samples matching this format.
     */
    public AudioFormat getAudioFormat() {
        return Defaults.AUDIO_FORMAT;
    }
}
