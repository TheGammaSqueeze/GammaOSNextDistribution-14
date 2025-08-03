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

import com.android.media.audiotestharness.common.Defaults;
import com.android.media.audiotestharness.proto.AudioDeviceOuterClass.AudioDevice;
import com.android.media.audiotestharness.proto.AudioFormatOuterClass.AudioFormat;

import com.google.common.collect.ImmutableSet;

import java.io.IOException;

/** Provides an interface for the system to a native audio system devices. */
public interface AudioSystemService {

    /**
     * Gets a {@link ImmutableSet} of {@link AudioDevice}s registered within the system.
     *
     * @throws IOException if unable to communicate with the underlying audio system.
     */
    ImmutableSet<AudioDevice> getDevices() throws IOException;

    /**
     * Creates a new {@link AudioCapturer} for a provided {@link AudioDevice} to capture raw audio
     * data in the specified {@link AudioFormat}.
     *
     * <p>For the provided {@link AudioDevice} the closes possible matching {@link AudioDevice} is
     * used. For example, if an {@link AudioDevice} with just the capability field set to [CAPTURE]
     * is provided then the first device found that can capture audio will be used.
     *
     * @throws IOException if unable to communicate with the underlying audio system or any errors
     *     occur while attempting to allocate resources for the {@link AudioCapturer}.
     */
    AudioCapturer createCapturerFor(AudioDevice device, AudioFormat audioFormat) throws IOException;

    /**
     * Creates a new {@link AudioCapturer} for the default {@link AudioDevice} and default {@link
     * AudioFormat}.
     *
     * @throws IOException if unable to communicate with the underlying audio system or any errors
     *     occur while attempting to allocate resources for the {@link AudioCapturer}.
     */
    default AudioCapturer createDefaultCapturer() throws IOException {
        return createCapturerFor(Defaults.AUDIO_DEVICE, Defaults.AUDIO_FORMAT);
    }

    /**
     * Creates a new {@link AudioCapturer} for the provided {@link AudioDevice} with the default
     * {@link AudioFormat}.
     *
     * @throws IOException if unable to communcate with the underlying audio system, any errors
     *     occur while attempting to allocate resources for the {@link AudioCapturer}, or the
     *     provided {@link AudioDevice} cannot be allocated properly.
     */
    default AudioCapturer createWithDefaultAudioFormat(AudioDevice device) throws IOException {
        return createCapturerFor(device, Defaults.AUDIO_FORMAT);
    }
}
