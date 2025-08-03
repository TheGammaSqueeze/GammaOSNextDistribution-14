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

#ifndef MEGA_PLAYER_WAVETABLESOURCE_H
#define MEGA_PLAYER_WAVETABLESOURCE_H

#include <memory>

#include "AudioSource.h"

class WaveTableSource: public AudioSource {
public:
    /**
     * Constructor. Sets up to play samples from the provided wave table.
     * @param waveTbl Contains the samples defining a single cycle of the desired waveform.
     */
    WaveTableSource();

    /**
     * Sets up to play samples from the provided wave table.
     * @param waveTbl Contains the samples defining a single cycle of the desired waveform.
     *                This wave table contains a redundant sample in the last slot (== first slot)
     *                to make the interpolation calculation simpler, so the logical length of
     *                the wave table is one less than the length of the array.
     * NOTE: WaveTableSource DOES NOT take ownership of the wave table. The user of WaveTableSource
     * is responsible for managing the lifetime of othe wave table.
     */
    void setWaveTable(float* waveTable, int length) {
        mWaveTable = waveTable;
        mNumWaveTableSamples = length - 1;

        calcFN();
    }

    /**
     * Sets the playback sample rate for which samples will be generated.
     * @param sampleRate
     */
    void setSampleRate(int sampleRate);

    /**
     * Set the frequency of the output signal.
     * @param freq  Signal frequency in Hz.
     */
    void setFreq(float freq) {
        mFreq = freq;
    }

    /**
     * Resets the playback position to the 1st sample.
     */
    void reset()  override {
        mSrcPhase = 0.0f;
    }

    virtual int getNumChannels() override;

    virtual int getEncoding() override;

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
    virtual int pull(float* buffer, int numFrames, int numChans) override;

    /*
     * Standard wavetable generators
     */
    static void genSinWave(float* buffer, int length);
    static void genTriangleWave(float* buffer, int size, float maxValue, float minValue,
                                           float dutyCycle);
    static void genPulseWave(float* buffer, int size,float maxValue, float minValue,
                                            float dutyCycle);

protected:
    static const int DEFAULT_WAVETABLE_LENGTH = 2049;

    /**
     * Calculates the "Nominal" frequency of the wave table.
     */
    void calcFN();

    /** The samples defining one cycle of the waveform to play */
    //TODO - make this a shared_ptr
    float*  mWaveTable;

    /** The number of samples in the wave table. Note that the wave table is presumed to contain
     * an "extra" sample (a copy of the 1st sample) in order to simplify the interpolation
     * calculation. Thus, this value will be 1 less than the length of mWaveTable.
     */
    int mNumWaveTableSamples;

    /** The phase (offset within the wave table) of the next output sample.
     *  Note that this may (will) be a fractional value. Range 0.0 -> mNumWaveTableSamples.
     */
    float mSrcPhase;

    /** The sample rate at which playback occurs */
    float mSampleRate = 48000;  // This seems likely, but can be changed

    /** The frequency of the generated audio signal */
    float mFreq = 1000;         // Some reasonable default frequency

    /** The "Nominal" frequency of the wavetable. i.e., the frequency that would be generated if
     * each sample in the wave table was sent in turn to the output at the specified sample rate.
     */
    float mFN;

    /** 1 / mFN. Calculated when mFN is set to avoid a division on each call to fill() */
    float mFNInverse;
};

#endif // MEGA_PLAYER_WAVETABLESOURCE_H
