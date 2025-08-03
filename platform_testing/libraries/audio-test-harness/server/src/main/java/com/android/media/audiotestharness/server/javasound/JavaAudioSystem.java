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

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/**
 * Wrapper for the {@link AudioSystem} class that provides the same API that can be easily injected
 * into other classes that required it. Not all methods provided by the {@link AudioSystem} are
 * included here, just the ones that are required by the system.
 *
 * <p>The methods in this class simply wrap all of the static methods in the Java {@link
 * AudioSystem} as instance methods maintaining the same signature.
 */
public class JavaAudioSystem {

    private static final JavaAudioSystem INSTANCE = new JavaAudioSystem();

    private JavaAudioSystem() {}

    public static JavaAudioSystem getInstance() {
        return INSTANCE;
    }

    /** @see AudioSystem#getLine(Line.Info) */
    public Line getLine(Line.Info info) throws LineUnavailableException {
        return AudioSystem.getLine(info);
    }

    /** @see AudioSystem#getMixer(Mixer.Info) */
    public Mixer getMixer(Mixer.Info info) {
        return AudioSystem.getMixer(info);
    }

    /** @see AudioSystem#getMixerInfo() */
    public Mixer.Info[] getMixerInfo() {
        return AudioSystem.getMixerInfo();
    }

    /** @see AudioSystem#getTargetDataLine(AudioFormat) */
    public TargetDataLine getTargetDataLine(AudioFormat format) throws LineUnavailableException {
        return AudioSystem.getTargetDataLine(format);
    }

    /** @see AudioSystem#getTargetDataLine(AudioFormat, Mixer.Info) */
    public TargetDataLine getTargetDataLine(AudioFormat format, Mixer.Info mixerinfo)
            throws LineUnavailableException {
        return AudioSystem.getTargetDataLine(format, mixerinfo);
    }

    /** @see AudioSystem#getTargetLineInfo(Line.Info) */
    public Line.Info[] getTargetLineInfo(Line.Info info) {
        return AudioSystem.getTargetLineInfo(info);
    }
}
