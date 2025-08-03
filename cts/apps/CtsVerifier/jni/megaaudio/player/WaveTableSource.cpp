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
#include <math.h>

#include "WaveTableSource.h"

/**
 * Constructor. Sets up to play samples from the provided wave table.
 * @param waveTbl Contains the samples defining a single cycle of the desired waveform.
 */
WaveTableSource::WaveTableSource() {
    reset();
}

void WaveTableSource::setSampleRate(int sampleRate) {
    mSampleRate = sampleRate;
    calcFN();
}

/**
 * Calculates the "Nominal" frequency of the wave table.
 */
void WaveTableSource::calcFN() {
    mFN = mSampleRate / (float)mNumWaveTableSamples;
    mFNInverse = 1.0f / mFN;
}

int WaveTableSource::getNumChannels() {
    return NUMCHANNELS_UNSPECIFIED;
}

int WaveTableSource::getEncoding() {
    return ENCODING_FLOAT;
}

/**
 * Fills the specified buffer with values generated from the wave table which will playback
 * at the specified frequency.
 *
 * @param buffer The buffer to be filled.
 * @param numFrames The number of frames of audio to provide.
 * @param numChans The number of channels (in the buffer) required by the player.
 * @return  The number of samples generated. Since we are generating a continuous periodic
 * signal, this will always be <code>numFrames</code>.
 */
int WaveTableSource::pull(float* buffer, int numFrames, int numChans) {
    float phaseIncr = mFreq * mFNInverse;
    int outIndex = 0;
    for (int frameIndex = 0; frameIndex < numFrames; frameIndex++) {
        // 'mod' back into the waveTable
        while (mSrcPhase >= (float)mNumWaveTableSamples) {
            mSrcPhase -= (float)mNumWaveTableSamples;
        }

        // linear-interpolate
        int srcIndex = (int)mSrcPhase;
        float delta = mSrcPhase - (float)srcIndex;
        float s0 = mWaveTable[srcIndex];
        float s1 = mWaveTable[srcIndex + 1];
        float value = s0 + ((s1 - s0) * delta);

        // Put the same value in all channels.
        for (int chanIndex = 0; chanIndex < numChans; chanIndex++) {
            buffer[outIndex++] = value;
        }

        mSrcPhase += phaseIncr;
    }

    return numFrames;
}

/*
 * Standard wavetable generators
 */
void WaveTableSource::genSinWave(float* buffer, int length) {
    float incr = ((float)M_PI  * 2.0f) / (float)(length - 1);
    for(int index = 0; index < length; index++) {
        buffer[index] = sinf(index * incr);
    }
}

void WaveTableSource::genTriangleWave(float* buffer, int size,
                                        float maxValue, float minValue, float dutyCycle) {
    float range = maxValue - minValue;

    // Make a triangle that goes 0 -> max -> min -> 0.
    int index = 0;
    int phase0Size = (int) (size / 2 * dutyCycle);

    int breakIndex = phase0Size;
    float val = 0;
    // Phase 0 (0 -> max)
    if (phase0Size != 0) {
        float phase0Incr = maxValue / (float) phase0Size;
        for (; index < breakIndex; ++index) {
            buffer[index] = val;
            val += phase0Incr;
        }
    } else {
        val = maxValue;
    }

    // Phase 1 & 2 (max -> min)
    breakIndex = size - phase0Size;
    float incr = -range / ((float) size * (1.0f - dutyCycle));
    for (; index < breakIndex; ++index) {
        buffer[index] = val;
        val += incr;
    }

    // Phase 3 (min -> 0)
    if (phase0Size != 0) {
        float phase0Incr = maxValue / (float) phase0Size;
        for (; index < size; ++index) {
            buffer[index] = val;
            val += phase0Incr;
        }
    }
}

void WaveTableSource::genPulseWave(float* buffer, int size,
                                    float maxValue, float minValue, float dutyCycle) {
    int index = 0;
    int breakIndex = (int) (size * dutyCycle);
    for (; index < breakIndex; ++index) {
        buffer[index] = maxValue;
    }
    for (; index < size; ++index) {
        buffer[index] = minValue;
    }
}

