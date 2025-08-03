/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.input;

import android.util.ArrayMap;

import java.util.List;

/**
 * Data class that stores HID vibrator test data.
 */
public class HidVibratorTestData {
    // Array of vibrator durations
    public List<Long> durations;

    // Array of vibrator amplitudes
    public List<Integer> amplitudes;

    // Index of left FF effect in hid output.
    public int leftFfIndex;

    // Index of right FF effect in hid output.
    public int rightFfIndex;

    // Hid output verification check, index and expected data.
    public ArrayMap<Integer, Integer> verifyMap;
}
