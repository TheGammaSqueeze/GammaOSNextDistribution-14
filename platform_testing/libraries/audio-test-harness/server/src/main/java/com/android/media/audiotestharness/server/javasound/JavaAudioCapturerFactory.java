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

import com.android.media.audiotestharness.proto.AudioDeviceOuterClass.AudioDevice;
import com.android.media.audiotestharness.proto.AudioFormatOuterClass.AudioFormat;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import java.util.concurrent.Executor;

import javax.sound.sampled.TargetDataLine;

/** Factory for {@link JavaAudioCapturer} instances. */
public class JavaAudioCapturerFactory {

    private final Executor mExecutor;

    @Inject
    public JavaAudioCapturerFactory(Executor executor) {
        mExecutor = executor;
    }

    /**
     * Builds a new {@link JavaAudioCapturer} for a provided {@link AudioDevice}, {@link
     * AudioFormat}, and {@link TargetDataLine}.
     */
    public JavaAudioCapturer build(
            AudioDevice audioDevice, AudioFormat audioFormat, TargetDataLine targetDataLine) {
        Preconditions.checkNotNull(audioDevice, "audioDevice cannot be null.");
        Preconditions.checkNotNull(audioFormat, "audioFormat cannot be null.");
        Preconditions.checkNotNull(targetDataLine, "targetDataLine cannot be null");
        Preconditions.checkArgument(
                targetDataLine.isOpen(),
                "targetDataLine must already be open before passed to the build() for the"
                        + " AudioCapturer");

        return JavaAudioCapturer.create(audioDevice, audioFormat, targetDataLine, mExecutor);
    }
}
