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

package com.android.car.settings.common.rotary;

import android.widget.SeekBar;

/** Helper class for rotary manipulation of {@link SeekBar}. */
public final class SeekBarHelper {
    private static final int ROTARY_SEEKBAR_INCREMENT_STEPS = 100;

    /**
     * Scales the number of rotations needed to move across the {@link SeekBar} by the value
     * specified by {@link #ROTARY_SEEKBAR_INCREMENT_STEPS}.
     */
    public static int getScaledSeekBarIncrement(SeekBar seekBar) {
        return (seekBar.getMax() - seekBar.getMin()) / ROTARY_SEEKBAR_INCREMENT_STEPS;
    }
}
