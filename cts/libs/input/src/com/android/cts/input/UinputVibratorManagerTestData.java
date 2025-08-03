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

import android.util.SparseArray;

import java.util.List;

/**
 * Data class that stores HID vibrator test data.
 */
public class UinputVibratorManagerTestData {
    // Array of vibrator durations
    public List<Long> durations;

    // SparseArray of vibrator id and amplitudes list. The array index is vibrator id,
    // the value is the list of amplitudes.
    public SparseArray<List<Integer>> amplitudes;
}
