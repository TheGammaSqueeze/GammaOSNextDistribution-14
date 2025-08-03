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

package com.android.media.audiotestharness.server.core;

import com.android.media.audiotestharness.proto.AudioDeviceOuterClass.AudioDevice;
import com.android.media.audiotestharness.proto.AudioFormatOuterClass.AudioFormat;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Provides methods for interacting with an {@link AudioDevice} to start capturing, stop capturing,
 * close the capture device, and get metadata corresponding to the capture device or capture format.
 */
public interface AudioCapturer extends AutoCloseable {

    /**
     * Starts capturing from the underlying audio device and publishes the resulting raw audio
     * samples to the attached outputs.
     */
    void open() throws IOException;

    /**
     * {@inheritDoc}
     *
     * <p>Stops capturing audio from the underlying audio device and frees the resources allocated
     * for capture. After this method is called the Capturer is 'disposed' and cannot be reused,
     * instead a new Capturer should be allocated by the {@link AudioSystemService}.
     */
    @Override
    void close() throws IOException;

    /** Attaches a specified {@link File} as an output for this capturer. */
    void attachOutput(File file);

    /** Attaches a specified {@link OutputStream} as an output for this capturer. */
    void attachOutput(OutputStream outputStream);

    /**
     * Returns the {@link AudioFormat} corresponding to the raw audio samples produced by this
     * capturer.
     */
    AudioFormat getAudioFormat();

    /** Returns the {@link AudioDevice} that this capturer captures from. */
    AudioDevice getAudioDevice();
}
