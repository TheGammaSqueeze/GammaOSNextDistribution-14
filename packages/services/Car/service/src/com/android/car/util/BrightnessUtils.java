/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.car.builtin.power.PowerManagerHelper;

import com.android.internal.annotations.VisibleForTesting;

/**
 * This is the minimized version of {@code com.android.settingslib.display.BrightnessUtils} not to
 * depend on the library which uses the hidden api.
 */
public class BrightnessUtils {

    public static final int GAMMA_SPACE_MIN = 0;
    public static final int GAMMA_SPACE_MAX = 65535;
    @VisibleForTesting
    public static final float INVALID_BRIGHTNESS_IN_FLOAT = -1.f;

    // Hybrid Log Gamma constant values
    private static final float R = 0.5f;
    private static final float A = 0.17883277f;
    private static final float B = 0.28466892f;
    private static final float C = 0.55991073f;

    // The tolerance within which we consider brightness values approximately equal to each other.
    // This value is approximately 1/3 of the smallest possible brightness value.
    private static final float EPSILON = 0.001f;

    /**
     * A function for converting from the gamma space that the slider works in to the
     * linear space that the setting works in.
     *
     * The gamma space effectively provides us a way to make linear changes to the slider that
     * result in linear changes in perception. If we made changes to the slider in the linear space
     * then we'd see an approximately logarithmic change in perception (c.f. Fechner's Law).
     *
     * Internally, this implements the Hybrid Log Gamma electro-optical transfer function, which is
     * a slight improvement to the typical gamma transfer function for displays whose max
     * brightness exceeds the 120 nit reference point, but doesn't set a specific reference
     * brightness like the PQ function does.
     *
     * Note that this transfer function is only valid if the display's backlight value is a linear
     * control. If it's calibrated to be something non-linear, then a different transfer function
     * should be used.
     *
     * @param val The slider value.
     * @param min The minimum acceptable value for the setting.
     * @param max The maximum acceptable value for the setting.
     * @return The corresponding setting value.
     */
    public static final int convertGammaToLinear(int val, int min, int max) {
        final float normalizedVal = MathUtils.norm(GAMMA_SPACE_MIN, GAMMA_SPACE_MAX, val);
        final float ret;
        if (normalizedVal <= R) {
            ret = MathUtils.sq(normalizedVal / R);
        } else {
            ret = MathUtils.exp((normalizedVal - C) / A) + B;
        }

        // HLG is normalized to the range [0, 12], so we need to re-normalize to the range [0, 1]
        // in order to derive the correct setting value.
        return Math.round(MathUtils.lerp(min, max, ret / 12));
    }

    /**
     * A function for converting from the linear space that the setting works in to the
     * gamma space that the slider works in.
     *
     * The gamma space effectively provides us a way to make linear changes to the slider that
     * result in linear changes in perception. If we made changes to the slider in the linear space
     * then we'd see an approximately logarithmic change in perception (c.f. Fechner's Law).
     *
     * Internally, this implements the Hybrid Log Gamma opto-electronic transfer function, which is
     * a slight improvement to the typical gamma transfer function for displays whose max
     * brightness exceeds the 120 nit reference point, but doesn't set a specific reference
     * brightness like the PQ function does.
     *
     * Note that this transfer function is only valid if the display's backlight value is a linear
     * control. If it's calibrated to be something non-linear, then a different transfer function
     * should be used.
     *
     * @param val The brightness setting value.
     * @param min The minimum acceptable value for the setting.
     * @param max The maximum acceptable value for the setting.
     * @return The corresponding slider value
     */
    public static final int convertLinearToGamma(int val, int min, int max) {
        return convertLinearToGammaFloat((float) val, (float) min, (float) max);
    }

    /**
     * Version of {@link #convertLinearToGamma} that takes float values.
     * TODO: brightnessfloat merge with above method(?)
     * @param val The brightness setting value.
     * @param min The minimum acceptable value for the setting.
     * @param max The maximum acceptable value for the setting.
     * @return The corresponding slider value
     */
    public static final int convertLinearToGammaFloat(float val, float min, float max) {
        // For some reason, HLG normalizes to the range [0, 12] rather than [0, 1]
        final float normalizedVal = MathUtils.norm(min, max, val) * 12;
        final float ret;
        if (normalizedVal <= 1f) {
            ret = MathUtils.sqrt(normalizedVal) * R;
        } else {
            ret = A * MathUtils.log(normalizedVal - B) + C;
        }

        return Math.round(MathUtils.lerp(GAMMA_SPACE_MIN, GAMMA_SPACE_MAX, ret));
    }

    /**
     * Converts between the int brightness system and the float brightness system.
     *
     * <p>This is the copy of
     * {@code com.android.internal.display.BrightnessSynchronizer#brightnessIntToFloat}.
     */
    public static float brightnessIntToFloat(int brightnessInt) {
        if (brightnessInt == PowerManagerHelper.BRIGHTNESS_OFF) {
            return PowerManagerHelper.BRIGHTNESS_OFF_FLOAT;
        } else if (brightnessInt == PowerManagerHelper.BRIGHTNESS_INVALID) {
            return PowerManagerHelper.BRIGHTNESS_INVALID_FLOAT;
        } else {
            final float minFloat = PowerManagerHelper.BRIGHTNESS_MIN;
            final float maxFloat = PowerManagerHelper.BRIGHTNESS_MAX;
            final float minInt = PowerManagerHelper.BRIGHTNESS_OFF + 1;
            final float maxInt = PowerManagerHelper.BRIGHTNESS_ON;
            return MathUtils.constrainedMap(minFloat, maxFloat, minInt, maxInt, brightnessInt);
        }
    }

    /**
     * Converts between the float brightness system and the int brightness system.
     *
     * <p>This is the copy of
     * {@code com.android.internal.display.BrightnessSynchronizer#brightnessFloatToInt}.
     */
    public static int brightnessFloatToInt(float brightnessFloat) {
        return Math.round(brightnessFloatToIntRange(brightnessFloat));
    }

    /**
     * Translates specified value from the float brightness system to the int brightness system,
     * given the min/max of each range. Accounts for special values such as OFF and invalid values.
     * Value returned as a float primitive (to preserve precision), but is a value within the
     * int-system range.
     *
     * <p>This is the copy of
     * {@code com.android.internal.display.BrightnessSynchronizer#brightnessFloatToIntRange}.
     */
    private static float brightnessFloatToIntRange(float brightnessFloat) {
        if (floatEquals(brightnessFloat, PowerManagerHelper.BRIGHTNESS_OFF_FLOAT)) {
            return PowerManagerHelper.BRIGHTNESS_OFF;
        } else if (Float.isNaN(brightnessFloat)) {
            return PowerManagerHelper.BRIGHTNESS_INVALID;
        } else {
            final float minFloat = PowerManagerHelper.BRIGHTNESS_MIN;
            final float maxFloat = PowerManagerHelper.BRIGHTNESS_MAX;
            final float minInt = PowerManagerHelper.BRIGHTNESS_OFF + 1;
            final float maxInt = PowerManagerHelper.BRIGHTNESS_ON;
            return MathUtils.constrainedMap(minInt, maxInt, minFloat, maxFloat, brightnessFloat);
        }
    }

    /**
     * Tests whether two brightness float values are within a small enough tolerance
     * of each other.
     *
     * <p>This is the copy of
     * {@code com.android.internal.display.BrightnessSynchronizer#floatEquals}.
     *
     * @param a first float to compare
     * @param b second float to compare
     * @return whether the two values are within a small enough tolerance value
     */
    private static boolean floatEquals(float a, float b) {
        return a == b
                || (Float.isNaN(a) && Float.isNaN(b))
                || (Math.abs(a - b) < EPSILON);
    }

    /**
     * This is the minimized version of {@code android.util.MathUtils} which is the hidden api.
     */
    private static final class MathUtils {
        private MathUtils() {
        }

        public static float constrain(float amount, float low, float high) {
            return amount < low ? low : (amount > high ? high : amount);
        }

        public static float log(float a) {
            return (float) Math.log(a);
        }

        public static float exp(float a) {
            return (float) Math.exp(a);
        }

        public static float sqrt(float a) {
            return (float) Math.sqrt(a);
        }

        public static float sq(float v) {
            return v * v;
        }

        public static float lerp(float start, float stop, float amount) {
            return start + (stop - start) * amount;
        }

        public static float lerpInv(float a, float b, float value) {
            return a != b ? ((value - a) / (b - a)) : 0.0f;
        }

        public static float saturate(float value) {
            return constrain(value, 0.0f, 1.0f);
        }

        public static float lerpInvSat(float a, float b, float value) {
            return saturate(lerpInv(a, b, value));
        }

        public static float norm(float start, float stop, float value) {
            return (value - start) / (stop - start);
        }

        public static float constrainedMap(
                float rangeMin, float rangeMax, float valueMin, float valueMax, float value) {
            return lerp(rangeMin, rangeMax, lerpInvSat(valueMin, valueMax, value));
        }
    }
}
