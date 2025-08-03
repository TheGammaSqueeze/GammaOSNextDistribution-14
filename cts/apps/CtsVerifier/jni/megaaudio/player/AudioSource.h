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

#ifndef MEGA_PLAYER_AUDIOSOURCE_H
#define MEGA_PLAYER_AUDIOSOURCE_H

class AudioSource {
public:
    AudioSource() {}
    virtual ~AudioSource() {}

    static const int NUMCHANNELS_UNSPECIFIED = -1;
    static const int NUMCHANNELS_ERROR = -2;
    virtual int getNumChannels() { return NUMCHANNELS_UNSPECIFIED; }

    // Encoding Constants (specific to MegaAudio)
    static const int ENCODING_UNSPECIFIED = -1;
    static const int ENCODING_ERROR = -2;
    static const int ENCODING_UNINITIALIZED = 0;
    static const int ENCODING_FLOAT = 1;
    static const int ENCODING_PCM16 = 2;
    static const int ENCODING_PCM24 = 3;
    static const int ENCODING_PCM32 = 4;
    virtual int getEncoding() { return ENCODING_UNSPECIFIED; };

    /**
     * Called before the stream starts to allow initialization of the source
     * @param numFrames The number of frames that will be requested in each pull() call.
     * @param numChans The number of channels in the stream.
     */
    virtual void init(int numFrames, int numChans) {}

    virtual void setSampleRate(int sampleRate) {}

    virtual void setFreq(float freq) {}

    /**
     * Reset the stream to its beginning
     */
    virtual void reset() {}

    virtual void trigger() {}

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
    virtual int pull(float* buffer, int numFrames, int numChans) = 0;
};

#endif // MEGA_PLAYER_AUDIOSOURCE_H
