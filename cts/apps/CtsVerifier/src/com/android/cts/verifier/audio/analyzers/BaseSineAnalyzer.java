/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.cts.verifier.audio.analyzers;

import java.util.Random;

/**
 * Output a steady sine wave and analyze the return signal.
 *
 * Use a cosine transform to measure the predicted magnitude and relative phase of the
 * looped back sine wave. Then generate a predicted signal and compare with the actual signal.
 *
 * Derived from oboetester::BaseSineAnalyzer
 */
public class BaseSineAnalyzer implements SignalAnalyzer {
    @SuppressWarnings("unused")
    static final String TAG = "BaseSineAnalyzer";

    int  mSinePeriod = 1; // this will be set before use
    double  mInverseSinePeriod = 1.0;
    double  mPhaseIncrement = 0.0;
    double  mOutputPhase = 0.0;
    double  mOutputAmplitude = 0.75;
    double  mPreviousPhaseOffset = 0.0;
    double  mPhaseTolerance = 2 * Math.PI  / 48;

    double mMagnitude = 0.0;
    double mMaxMagnitude = 0.0;

    double mPhaseErrorSum;
    double mPhaseErrorCount;
    double mPhaseJitter = 0.0;

    int mPhaseCount = 0;

    // If this jumps around then we are probably just hearing noise.
    double  mPhaseOffset = 0.0;
    int mFramesAccumulated = 0;
    double  mSinAccumulator = 0.0;
    double  mCosAccumulator = 0.0;
    double  mScaledTolerance = 0.0;
    double  mTolerance = 0.10; // scaled from 0.0 to 1.0
    int mInputChannel = 0;
    int mOutputChannel = 0;

    static final int DEFAULT_SAMPLERATE = 48000;
    static final int MILLIS_PER_SECOND = 1000;  // by definition
    static final int MAX_LATENCY_MILLIS = 1000;  // arbitrary and generous
    static final int TARGET_GLITCH_FREQUENCY = 1000;
    static final double MIN_REQUIRED_MAGNITUDE = 0.001;
    static final int TYPICAL_SAMPLE_RATE = 48000;
    static final double MAX_SINE_FREQUENCY = 1000.0;
    static final double FRAMES_PER_CYCLE = TYPICAL_SAMPLE_RATE / MAX_SINE_FREQUENCY;
    static final double PHASE_PER_BIN = 2.0 * Math.PI / FRAMES_PER_CYCLE;
    static final double MAX_ALLOWED_JITTER = 2.0 * PHASE_PER_BIN;
    // Start by failing then let good results drive us into a pass value.
    static final double INITIAL_JITTER = 2.0 * MAX_ALLOWED_JITTER;
    // A coefficient of 0.0 is no filtering. 0.9999 is extreme low pass.
    static final double JITTER_FILTER_COEFFICIENT = 0.8;

    int mSampleRate = DEFAULT_SAMPLERATE;

    double mNoiseAmplitude = 0.00; // Used to experiment with warbling caused by DRC.
    Random mWhiteNoise = new Random();

    MagnitudePhase mMagPhase = new MagnitudePhase();

    InfiniteRecording mInfiniteRecording = new InfiniteRecording(64 * 1024);

    enum RESULT_CODE {
        RESULT_OK,
        ERROR_NOISY,
        ERROR_VOLUME_TOO_LOW,
        ERROR_VOLUME_TOO_HIGH,
        ERROR_CONFIDENCE,
        ERROR_INVALID_STATE,
        ERROR_GLITCHES,
        ERROR_NO_LOCK
    };

    public BaseSineAnalyzer() {
        // Add a little bit of noise to reduce blockage by speaker protection and DRC.
        mNoiseAmplitude = 0.02;
    };

    public int getSampleRate() {
        return mSampleRate;
    }

    /**
     * Set the assumed sample rate for the analysis
     * @param sampleRate
     */
    public void setSampleRate(int sampleRate) {
        mSampleRate = sampleRate;
        updatePhaseIncrement();
    }

    /**
     * @return output frequency that will have an integer period on input
     */
    public double getAdjustedFrequency() {
        updatePhaseIncrement();
        return mInverseSinePeriod * getSampleRate();
    }

    public void setInputChannel(int inputChannel) {
        mInputChannel = inputChannel;
    }

    public int getInputChannel() {
        return mInputChannel;
    }

    public void setOutputChannel(int outputChannel) {
        mOutputChannel = outputChannel;
    }

    public int getOutputChannel() {
        return mOutputChannel;
    }

    public void setNoiseAmplitude(double noiseAmplitude) {
        mNoiseAmplitude = noiseAmplitude;
    }

    public double getNoiseAmplitude() {
        return mNoiseAmplitude;
    }

    void setMagnitude(double magnitude) {
        mMagnitude = magnitude;
        mScaledTolerance = mMagnitude * mTolerance;
    }

    public double getTolerance() {
        return mTolerance;
    }

    public void setTolerance(double tolerance) {
        mTolerance = tolerance;
    }

    public double getMagnitude() {
        return mMagnitude;
    }

    public double getMaxMagnitude() {
        return mMaxMagnitude;
    }

    public double getPhaseOffset() {
        return mPhaseOffset;
    }

    public double getOutputPhase() {
        return mOutputPhase;
    }

    public double getPhaseJitter() {
        return mPhaseJitter;
    }

    // reset the sine wave detector
    void resetAccumulator() {
        mFramesAccumulated = 0;
        mSinAccumulator = 0.0;
        mCosAccumulator = 0.0;
    }

    class MagnitudePhase {
        public double mMagnitude;
        public double mPhase;
    }

    /**
     * Calculate the magnitude of the component of the input signal
     * that matches the analysis frequency.
     * Also calculate the phase that we can use to create a
     * signal that matches that component.
     * The phase will be between -PI and +PI.
     */
    double calculateMagnitudePhase(MagnitudePhase magphase) {
        if (mFramesAccumulated == 0) {
            return 0.0;
        }
        double sinMean = mSinAccumulator / mFramesAccumulated;
        double cosMean = mCosAccumulator / mFramesAccumulated;

        double magnitude = 2.0 * Math.sqrt((sinMean * sinMean) + (cosMean * cosMean));
        magphase.mPhase = Math.atan2(cosMean, sinMean);
        return magphase.mMagnitude = magnitude;
    }

    // advance and wrap phase
    void incrementOutputPhase() {
        mOutputPhase += mPhaseIncrement;
        if (mOutputPhase > Math.PI) {
            mOutputPhase -= (2.0 * Math.PI);
        }
    }

    double calculatePhaseError(double p1, double p2) {
        double diff = p1 - p2;
        // Wrap around the circle.
        while (diff > Math.PI) {
            diff -= 2 * Math.PI;
        }
        while (diff < -Math.PI) {
            diff += 2 * Math.PI;
        }
        return diff;
    }

    double getAveragePhaseError() {
        // If we have no measurements then return maximum possible phase jitter
        // to avoid dividing by zero.
        return (mPhaseErrorCount > 0) ? (mPhaseErrorSum / mPhaseErrorCount) : Math.PI;
    }

    /**
     * Perform sin/cos analysis on each sample.
     * Measure magnitude and phase on every period.
     * @param sample
     * @param referencePhase
     * @return true if magnitude and phase updated
     */
    boolean transformSample(float sample, double referencePhase) {
        // Track incoming signal and slowly adjust magnitude to account
        // for drift in the DRC or AGC.
        mSinAccumulator += ((double) sample) * Math.sin(referencePhase);
        mCosAccumulator += ((double) sample) * Math.cos(referencePhase);
        mFramesAccumulated++;

        incrementOutputPhase();

        // Must be a multiple of the period or the calculation will not be accurate.
        if (mFramesAccumulated == mSinePeriod) {
            final double coefficient = 0.1;

            double magnitude = calculateMagnitudePhase(mMagPhase);
            mPhaseOffset = mMagPhase.mPhase;
            // One pole averaging filter.
            setMagnitude((mMagnitude * (1.0 - coefficient)) + (magnitude * coefficient));
            return true;
        } else {
            return false;
        }
    }

    /**
     * @param frameData contains microphone data with sine signal feedback
     * @param channelCount
     */
    RESULT_CODE processInputFrame(float[] frameData, int offset) {
        RESULT_CODE result = RESULT_CODE.RESULT_OK;

        float sample = frameData[offset];
        mInfiniteRecording.write(sample);

        if (transformSample(sample, mOutputPhase)) {
            resetAccumulator();
            if (mMagnitude >= MIN_REQUIRED_MAGNITUDE) {
                // Analyze magnitude and phase on every period.
                double phaseError =
                        Math.abs(calculatePhaseError(mPhaseOffset, mPreviousPhaseOffset));
                if (phaseError < mPhaseTolerance) {
                    mMaxMagnitude = Math.max(mMagnitude, mMaxMagnitude);
                }
                mPreviousPhaseOffset = mPhaseOffset;

                // Only look at the phase if we have a signal.
                if (mPhaseCount > 3) {
                    // Accumulate phase error and average.
                    mPhaseErrorSum += phaseError;
                    mPhaseErrorCount++;
                    mPhaseJitter = getAveragePhaseError();
                }

                mPhaseCount++;
            }
        }
        return result;
    }

    private void updatePhaseIncrement() {
        mSinePeriod = getSampleRate() / TARGET_GLITCH_FREQUENCY;
        mInverseSinePeriod = 1.0 / mSinePeriod;
        mPhaseIncrement = 2.0 * Math.PI * mInverseSinePeriod;
    }

    @Override
    public void reset() {
        resetAccumulator();

        mOutputPhase = 0.0f;
        mMagnitude = 0.0;
        mMaxMagnitude = 0.0;
        mPhaseOffset = 0.0;
        mPreviousPhaseOffset = 0.0;
        mPhaseJitter = INITIAL_JITTER;
        mPhaseCount = 0;
        mPhaseErrorSum = 0.0;
        mPhaseErrorCount = 0.0;

        updatePhaseIncrement();
    }

    @Override
    public void analyzeBuffer(float[] audioData, int numChannels, int numFrames) {
        int offset = mInputChannel;
        for (int frameIndex = 0; frameIndex < numFrames; frameIndex++) {
            // processOutputFrame(audioData, offset, numChannels);
            processInputFrame(audioData, offset);
            offset += numChannels;
        }

//        // Only look at the phase if we have a signal.
//        if (mMagnitude >= MIN_REQUIRED_MAGNITUDE) {
//            double phase = mPhaseOffset;
//            if (mPhaseCount > 3) {
//                double phaseError = calculatePhaseError(phase, mPhaseOffset);
//                // Accumulate phase error and average.
//                mPhaseErrorSum += phaseError;
//                mPhaseErrorCount++;
//                mPhaseJitter = getAveragePhaseError();
//            }
//
//            mPhaseOffset = phase;
//            mPhaseCount++;
//        }
    }
}
