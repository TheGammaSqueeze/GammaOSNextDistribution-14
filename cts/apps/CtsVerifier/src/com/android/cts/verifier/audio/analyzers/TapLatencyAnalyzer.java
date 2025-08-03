/*
 * Copyright (C) 2021 The Android Open Source Project
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

import java.util.ArrayList;

/**
 * Analyzes a block of samples to find a trigger "tick" (presumably from a screen tap) and the
 * resulting "blip" tone and compute the latency between those events.
 */
public class TapLatencyAnalyzer {
    public static final int TYPE_TAP = 0;
    float[] mHighPassBuffer;

    private float mDroop = 0.995f;
    private static final float LOW_THRESHOLD = 0.01f;
    private static final float HIGH_THRESHOLD = 0.03f;

    /**
     * A class to hold "events" discovered by the TapLatencyAnalyser
     */
    public static class TapLatencyEvent {
        public int type;
        public int sampleIndex;
        public TapLatencyEvent(int type, int sampleIndex) {
            this.type = type;
            this.sampleIndex = sampleIndex;
        }
    }

    /**
     * Analyzes the provided audio data to find audio event "edges"
     * @param buffer    Audio samples to analyze.
     * @param offset    Offset within the provide buffer to start analysis.
     * @param numSamples    Number of samples to analyze.
     * @return
     */
    public TapLatencyEvent[] analyze(float[] buffer, int offset, int numSamples) {
        // Use high pass filter to remove rumble from air conditioners.
        mHighPassBuffer = new float[numSamples];
        highPassFilter(buffer, offset, numSamples, mHighPassBuffer);
        float[] peakBuffer = new float[numSamples];
        fillPeakBuffer(mHighPassBuffer, 0, numSamples, peakBuffer);
        return scanForEdges(peakBuffer, numSamples);
    }

    /**
     * @return The filtered samples on which the analysis was performed.
     *   High-pass filtered to emphasize high-frequency events such as edges.
     */
    public float[] getFilteredBuffer() {
        return mHighPassBuffer;
    }

    private void highPassFilter(float[] buffer, int offset, int numSamples,
                                float[] highPassBuffer) {
        float xn1 = 0.0f;
        float yn1 = 0.0f;
        final float alpha = 0.05f;
        for (int i = 0; i < numSamples; i++) {
            float xn = buffer[i + offset];
            float yn = alpha * yn1 + ((1.0f - alpha) * (xn - xn1));
            highPassBuffer[i] = yn;
            xn1 = xn;
            yn1 = yn;
        }
    }

    private TapLatencyEvent[] scanForEdges(float[] peakBuffer, int numSamples) {
        ArrayList<TapLatencyEvent> events = new ArrayList<TapLatencyEvent>();
        float slow = 0.0f;
        float fast = 0.0f;
        float slowCoefficient = 0.01f;
        float fastCoefficient = 0.10f;
        boolean armed = true;
        int sampleIndex = 0;
        for (float level : peakBuffer) {
            slow = slow + (level - slow) * slowCoefficient; // low pass filter
            fast = fast + (level - fast) * fastCoefficient;
            if (armed && (fast > HIGH_THRESHOLD) && (fast > (2.0 * slow))) {
                events.add(new TapLatencyEvent(TYPE_TAP, sampleIndex));
                armed = false;
            }
            // Use hysteresis when rearming.
            if (!armed && (fast < LOW_THRESHOLD)) {
                armed = true;
            }
            sampleIndex++;
        }
        return events.toArray(new TapLatencyEvent[0]);
    }

    private void fillPeakBuffer(float[] buffer, int offset, int numSamples, float[] peakBuffer) {
        float previous = 0.0f;
        for (int i = 0; i < numSamples; i++) {
            float input = buffer[i + offset];
            float output = previous * mDroop;
            if (input > output) {
                output = input;
            }
            previous = output;
            peakBuffer[i] = output;
        }
    }
}
