/*
 * Copyright 2022 The Android Open Source Project
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
package org.hyphonate.megaaudio.player.sources;

/**
 * An AudioSourceProvider for multi-channel sine waves with control over which
 * channels have audio data and which channels have silence.
 */
public class SparseChannelAudioSource extends WaveTableSource {
    /**
     * The number of SAMPLES in the Sin Wave table.
     * This is plenty of samples for a clear sine wave.
     * the + 1 is to avoid special handling of the interpolation on the last sample.
     */
    static final int WAVETABLE_LENGTH = 2049;

    int mChannelsMask;

    public SparseChannelAudioSource(int mask) {
        super();
        float[] waveTbl = new float[WAVETABLE_LENGTH];
        WaveTableSource.genSinWave(waveTbl);

        setWaveTable(waveTbl);

        mChannelsMask = mask;
    }

    private int channelNumToMask(int chanNum) {
        if (chanNum <= 0) {
            return 0;
        }
        return 1 << (chanNum - 1);
    }

    @Override
    public int pull(float[] buffer, int numFrames, int numChans) {
        final float phaseIncr = mFreq * mFNInverse;
        int outIndex = 0;
        for (int frameIndex = 0; frameIndex < numFrames; frameIndex++) {
            // 'mod' back into the waveTable
            while (mSrcPhase >= (float) mNumWaveTblSamples) {
                mSrcPhase -= (float) mNumWaveTblSamples;
            }

            // linear-interpolate
            int srcIndex = (int) mSrcPhase;
            float delta0 = mSrcPhase - (float) srcIndex;
            float delta1 = 1.0f - delta0;
            float value = ((mWaveTbl[srcIndex] * delta0) + (mWaveTbl[srcIndex + 1] * delta1));

            // Put the same value in all channels.
            // This is inefficient and should be pulled out of this loop
            for (int chanIndex = 0; chanIndex < numChans; chanIndex++) {
                if ((mChannelsMask & (1 << chanIndex)) != 0) {
                    buffer[outIndex++] = value;
                } else {
                    buffer[outIndex++] = 0.0f;
                }
            }

            mSrcPhase += phaseIncr;
        }

        return numFrames;
    }

}
