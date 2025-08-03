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

package com.android.car.audio;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.media.AudioGain;

public final class GainBuilder {
    public static final int MIN_GAIN = 0;
    public static final int MAX_GAIN = 100;
    public static final int DEFAULT_GAIN = 50;
    public static final int STEP_SIZE = 2;

    private int mMode = AudioGain.MODE_JOINT;
    private int mMaxValue = MAX_GAIN;
    private int mMinValue = MIN_GAIN;
    private int mDefaultValue = DEFAULT_GAIN;
    private int mStepSize = STEP_SIZE;

    GainBuilder setMode(int mode) {
        mMode = mode;
        return this;
    }

    GainBuilder setMaxValue(int maxValue) {
        mMaxValue = maxValue;
        return this;
    }

    GainBuilder setMinValue(int minValue) {
        mMinValue = minValue;
        return this;
    }

    GainBuilder setDefaultValue(int defaultValue) {
        mDefaultValue = defaultValue;
        return this;
    }

    GainBuilder setStepSize(int stepSize) {
        mStepSize = stepSize;
        return this;
    }

    AudioGain build() {
        AudioGain mockGain = mock(AudioGain.class);
        when(mockGain.mode()).thenReturn(mMode);
        when(mockGain.maxValue()).thenReturn(mMaxValue);
        when(mockGain.minValue()).thenReturn(mMinValue);
        when(mockGain.defaultValue()).thenReturn(mDefaultValue);
        when(mockGain.stepValue()).thenReturn(mStepSize);
        return mockGain;
    }
}
