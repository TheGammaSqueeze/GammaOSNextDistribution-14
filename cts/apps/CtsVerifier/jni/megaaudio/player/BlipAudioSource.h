/*
 * Copyright 2021 The Android Open Source Project
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

#ifndef MEGA_PLAYER_BLIPAUDIOSOURCE_H
#define MEGA_PLAYER_BLIPAUDIOSOURCE_H

#include <atomic>

#include "WaveTableSource.h"

class BlipAudioSource: public WaveTableSource {
private:
    // 1/16 second @48000 Hz
    static const int NUM_PULSE_FRAMES = (int) (48000 * (1.0 / 16.0));

    int mNumPendingPulseFrames;

public:
    BlipAudioSource();

    /**
     * Triggers a "blip" in the output
     */
    virtual void trigger();

    /**
     * Fills the specified buffer with silence until triggered and then with
     * a short sine tone.
     * @return  The number of frames generated. Since we are generating a continuous periodic
     * signal, this will always be <code>numFrames</code>.
     */
    virtual int pull(float* buffer, int numFrames, int numChans) override;

    std::atomic<int> mRequestCount; // external thread increments this to request a beep
    std::atomic<int> mAcknowledgeCount; // audio thread sets this to acknowledge
};


#endif // MEGA_PLAYER_BLIPAUDIOSOURCE_H
