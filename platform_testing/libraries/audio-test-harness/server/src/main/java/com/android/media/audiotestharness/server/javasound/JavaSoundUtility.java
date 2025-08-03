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

import com.android.media.audiotestharness.proto.AudioFormatOuterClass;

import javax.sound.sampled.AudioFormat;

/** Utility methods used throughout the Java Sound Library. */
public final class JavaSoundUtility {

    private JavaSoundUtility() {}

    /**
     * Converts a {@link com.android.media.audiotestharness.proto.AudioFormatOuterClass.AudioFormat}
     * proto into the Java Sound API {@link AudioFormat}.
     */
    public static AudioFormat audioFormatFrom(AudioFormatOuterClass.AudioFormat sourceFormat) {
        return new AudioFormat(
                sourceFormat.getSampleRate(),
                sourceFormat.getSampleSizeBits(),
                sourceFormat.getChannels(),
                sourceFormat.getSigned(),
                sourceFormat.getBigEndian());
    }
}
