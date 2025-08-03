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
package org.hyphonate.megaaudio.player.sources;

import org.hyphonate.megaaudio.player.AudioSource;

/**
 * An AudioFiller implementation for feeding data from a PCMFLOAT wavetable.
 * We do simple, linear interpolation for inter-table values.
 */
public class WaveTableSource extends AudioSource {
    @SuppressWarnings("unused") private static String TAG = WaveTableSource.class.getSimpleName();

    /** The samples defining one cycle of the waveform to play */
    protected float[] mWaveTbl;
    /** The number of samples in the wave table. Note that the wave table is presumed to contain
     * an "extra" sample (a copy of the 1st sample) in order to simplify the interpolation
     * calculation. Thus, this value will be 1 less than the length of mWaveTbl.
     */
    protected int mNumWaveTblSamples;
    /** The phase (offset within the wave table) of the next output sample.
     *  Note that this may (will) be a fractional value. Range 0.0 -> mNumWaveTblSamples.
     */
    protected float mSrcPhase;
    /** The sample rate at which playback occurs */
    protected float mSampleRate = 48000;  // This seems likely, but can be changed
    /** The frequency of the generated audio signal */
    protected float mFreq = 1000;         // Some reasonable default frequency
    /** The "Nominal" frequency of the wavetable. i.e., the frequency that would be generated if
     * each sample in the wave table was sent in turn to the output at the specified sample rate.
     */
    protected float mFN;
    /** 1 / mFN. Calculated when mFN is set to avoid a division on each call to fill() */
    protected float mFNInverse;

    /**
     * Constructor.
     */
    public WaveTableSource() {
    }

    /**
     * Calculates the "Nominal" frequency of the wave table.
     */
    private void calcFN() {
        mFN = mSampleRate / (float)mNumWaveTblSamples;
        mFNInverse = 1.0f / mFN;
    }

    /**
     * Sets up to play samples from the provided wave table.
     * @param waveTbl Contains the samples defining a single cycle of the desired waveform.
     *                This wave table contains a redundant sample in the last slot (== first slot)
     *                to make the interpolation calculation simpler, so the logical length of
     *                the wave table is one less than the length of the array.
     */
    public void setWaveTable(float[] waveTbl) {
        mWaveTbl = waveTbl;
        mNumWaveTblSamples = waveTbl != null ? mWaveTbl.length - 1 : 0;

        calcFN();
    }

    /**
     * Sets the playback sample rate for which samples will be generated.
     * @param sampleRate
     */
    @Override
    public void setSampleRate(int sampleRate) {
        mSampleRate = sampleRate;

        calcFN();
    }

    /**
     * Set the frequency of the output signal.
     * @param freq  Signal frequency in Hz.
     */
    public void setFreq(float freq) {
        mFreq = freq;
    }

    /**
     * Resets the playback position to the 1st sample.
     */
    @Override
    public void reset() {
        mSrcPhase = 0.0f;
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
    @Override
    public int pull(float[] buffer, int numFrames, int numChans) {
        final float phaseIncr = mFreq * mFNInverse;
        int outIndex = 0;
        for (int frameIndex = 0; frameIndex < numFrames; frameIndex++) {
            // 'mod' back into the waveTable
            while (mSrcPhase >= (float)mNumWaveTblSamples) {
                mSrcPhase -= (float)mNumWaveTblSamples;
            }

            // linear-interpolate
            int srcIndex = (int)mSrcPhase;
            float delta0 = mSrcPhase - (float)srcIndex;
            float delta1 = 1.0f - delta0;
            float value = ((mWaveTbl[srcIndex] * delta0) + (mWaveTbl[srcIndex + 1] * delta1));

            // Put the same value in all channels.
            // This is inefficient and should be pulled out of this loop
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

    /**
     * Generates a sin waveform wavetable.
     * @param buffer    The buffer to receive the sample values.
     */
    public static void genSinWave(float[] buffer) {
        int size = buffer.length;
        // remember, the table has 1 extra (redundant) sample as a guard point, so the logical size
        // is buffer.length - 1
        float incr = ((float) Math.PI  * 2.0f) / (float) (buffer.length - 1);
        for(int index = 0; index < size; index++) {
            buffer[index] = (float)Math.sin(index * incr);
        }
    }

    /**
     * Generates a triangular waveform
     * @param buffer    The buffer to receive the sample values.
     * @param maxValue  The maximum value for the generated wavetable
     * @param minValue  The minimum value for the generated wavetable.
     * @param dutyCycle The fraction of wavetable for the first 1/4 of the triangle wave.
     */
    public static void genTriangleWave(
            float[] buffer, float maxValue, float minValue, float dutyCycle) {
        float range = maxValue - minValue;
        // remember, the table has 1 extra (redundant) sample as a guard point, so the logical size
        // is buffer.length - 1
        int size = buffer.length - 1;

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

        buffer[size] = buffer[0];
    }
}
