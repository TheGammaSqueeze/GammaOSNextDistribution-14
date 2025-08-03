/*
 * Copyright 2020 The Android Open Source Project
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
package org.hyphonate.megaaudio.player;

public abstract class AudioSource {
    public AudioSource() {}

    /**
     * Called before the stream starts to allow initialization of the source
     * @param numFrames The number of frames that will be requested in each pull() call.
     * @param numChans The number of channels in the stream.
     */
    public void init(int numFrames, int numChans) {}

    // This may be relevant to concrete classes which produce a sound with a notion of pitch
    public void setSampleRate(int sampleRate) {}

    /**
     * (for period sources) sets the frequency of the signal
     * @param freq
     */
    public void setFreq(float freq) {}

    public void start() {}
    public void stop() {}

    /**
     * reset a stream to the beginning.
     */
    public void reset() {}

    /**
     * triggers some event in the AudioSource. Whatever that event is is up to the AudioSource
     * subclass.
     */
    public void trigger() {}

    /**
     * Process a request for audio data.
     * @param audioData The buffer to be filled.
     * @param numFrames The number of frames of audio to provide.
     * @param numChans The number of channels (in the buffer) required by the player.
     * @return The number of frames actually generated. If this value is less than that
     * requested, it may be interpreted by the player as the end of playback.
     * Note that the player will be blocked by this call.
     * Note that the data is assumed to be *interleaved*.
     */
    public abstract int pull(float[] audioData, int numFrames, int numChans);
}
