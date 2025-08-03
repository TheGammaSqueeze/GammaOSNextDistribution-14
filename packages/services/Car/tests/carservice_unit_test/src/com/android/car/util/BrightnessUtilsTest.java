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

package com.android.car.util;

import static com.android.car.util.BrightnessUtils.GAMMA_SPACE_MAX;
import static com.android.car.util.BrightnessUtils.GAMMA_SPACE_MIN;

import static com.google.common.truth.Truth.assertThat;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;

import org.junit.Test;

public class BrightnessUtilsTest extends AbstractExtendedMockitoTestCase {
    private static final int MIN_INT = 1;
    private static final int MAX_INT = 255;
    private static final float MIN_FLOAT = 0.0f;
    private static final float MAX_FLOAT = 1.0f;
    private static final int MIN_BACKLIGHT = 10;   // config_screenBrightnessSettingMinimum
    private static final int MAX_BACKLIGHT = 255;  // config_screenBrightnessSettingMaximum
    private static final int INVALID_BRIGHTNESS = -1;

    @Test
    public void linearToGamma_minValue_shouldReturnMin() {
        assertThat(BrightnessUtils.convertLinearToGamma(MIN_INT, MIN_INT, MAX_INT))
                .isEqualTo(GAMMA_SPACE_MIN);
        assertThat(BrightnessUtils.convertLinearToGammaFloat(MIN_FLOAT, MIN_FLOAT, MAX_FLOAT))
                .isEqualTo(GAMMA_SPACE_MIN);
    }

    @Test
    public void linearToGamma_maxValue_shouldReturnGammaSpaceMax() {
        assertThat(BrightnessUtils.convertLinearToGamma(MAX_INT, MIN_INT, MAX_INT))
                .isEqualTo(GAMMA_SPACE_MAX);
        assertThat(BrightnessUtils.convertLinearToGammaFloat(MAX_FLOAT, MIN_FLOAT, MAX_FLOAT))
                .isEqualTo(GAMMA_SPACE_MAX);
    }

    @Test
    public void gammaToLinear_gammaSpaceValue_shouldReturnMax() {
        assertThat(BrightnessUtils.convertGammaToLinear(GAMMA_SPACE_MAX, MIN_INT, MAX_INT))
                .isEqualTo(MAX_INT);
    }

    /* The following values are captured from the real Auto device */
    @Test
    public void linearToGamma_autoDefault() {
        assertThat(BrightnessUtils.convertLinearToGamma(102, MIN_BACKLIGHT, MAX_BACKLIGHT))
                .isEqualTo(53572);
    }

    @Test
    public void gammaToLinear_autoDefault() {
        assertThat(BrightnessUtils.convertGammaToLinear(53572, MIN_BACKLIGHT, MAX_BACKLIGHT))
                .isEqualTo(102);
    }

    @Test
    public void brightnessIntToFloat_shouldReturnMin() {
        assertThat(BrightnessUtils.brightnessIntToFloat(MIN_INT)).isEqualTo(MIN_FLOAT);
    }

    @Test
    public void brightnessIntToFloat_shouldReturnMax() {
        assertThat(BrightnessUtils.brightnessIntToFloat(MAX_INT)).isEqualTo(MAX_FLOAT);
    }

    @Test
    public void brightnessFloatToInt_shouldReturnMin() {
        assertThat(BrightnessUtils.brightnessFloatToInt(MIN_FLOAT)).isEqualTo(MIN_INT);
    }

    @Test
    public void brightnessFloatToInt_shouldReturnMax() {
        assertThat(BrightnessUtils.brightnessFloatToInt(MAX_FLOAT)).isEqualTo(MAX_BACKLIGHT);
    }

    @Test
    public void tesetBrightnessIntToFloat() {
        assertThat(BrightnessUtils.brightnessIntToFloat(128)).isEqualTo(0.5f);
    }

    @Test
    public void testBrightnessFloatToInt() {
        assertThat(BrightnessUtils.brightnessFloatToInt(0.5f)).isEqualTo(128);
    }
}
