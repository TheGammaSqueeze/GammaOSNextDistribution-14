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

/**
 * A simple analysis class that measures the maximum magnitude of the audio signal
 */
public class MagnitudeAnalyzer implements SignalAnalyzer {
    float mMaxMagnitude;
    /**
     * Perform whatever process is needed to "reset" the analysis
     */
    public void reset() {
        mMaxMagnitude = 0.0f;
    }

    /**
     * @return The maximum magnitude found.
     */
    public float getMaxMagnitude() {
        return mMaxMagnitude;
    }

    /**
     * Scan input buffer for the maximum (absolute) value.
     * @param audioData
     * @param numChannels
     * @param numFrames
     */
    public void analyzeBuffer(float[] audioData, int numChannels, int numFrames) {
        for (float value : audioData) {
            float magnitude = Math.abs(value);
            if (magnitude > mMaxMagnitude) {
                mMaxMagnitude = magnitude;
            }
        }
    }
}

