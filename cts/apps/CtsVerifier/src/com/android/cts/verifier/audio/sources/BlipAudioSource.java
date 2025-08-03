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
package com.android.cts.verifier.audio.sources;

import org.hyphonate.megaaudio.player.sources.WaveTableSource;

import java.util.Arrays;

/**
 * An audio source which plays a short tone when triggered
 */
public class BlipAudioSource extends WaveTableSource {
    /**
     * The number of SAMPLES in the Wave table.
     * This is plenty of samples for a clear wave.
     * the + 1 is to avoid special handling of the interpolation on the last sample.
     */
    static final int WAVETABLE_LENGTH = 2049;

    // 1/16 second @48000 Hz
    private static final int NUM_PULSE_FRAMES = (int) (48000 * (1.0 / 16.0));

    private int mNumPendingPulseFrames;

    public BlipAudioSource() {
        super();
        float[] waveTbl = new float[WAVETABLE_LENGTH];
        WaveTableSource.genSinWave(waveTbl);
        super.setWaveTable(waveTbl);
    }

    /**
     * Triggers a "blip" in the output
     */
    @Override
    public void trigger() {
        mNumPendingPulseFrames = NUM_PULSE_FRAMES;
    }

    @Override
    public int pull(float[] buffer, int numFrames, int numChans) {
        if (mNumPendingPulseFrames <= 0) {
            Arrays.fill(buffer, 0.0f);
        } else {
            super.pull(buffer, numFrames, numChans);
            mNumPendingPulseFrames -= numFrames;
        }
        return numFrames;
    }
}
