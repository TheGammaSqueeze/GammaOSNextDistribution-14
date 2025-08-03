/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.server.uwb.correction.math;

import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.sqrt;

/** Static functions for common math operations. */
public final class MathHelper {
    public static final float F_PI = (float) PI;
    public static final float F_HALF_PI = (float) (PI / 2);
    public static final long MS_PER_SEC = 1000;
    private static final float RSQRT_THRESHOLD = 0.0002821f;

    /** Clamps a value between a minimum and maximum range. */
    public static float clamp(float value, float min, float max) {
        return min(max, max(min, value));
    }

    /**
     * Linearly interpolates between a and b by a ratio.
     *
     * @param a the beginning value
     * @param b the ending value
     * @param t ratio between the two floats
     * @return interpolated value between the two floats
     */
    public static float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }

    /** Calculates the 1 / sqrt(x), with a maximum error of 1.5 ulps. */
    public static float rsqrt(float x) {
        return abs(x - 1) < RSQRT_THRESHOLD
                ? 2 / (1 + x) // 1st order Padé approximant for inverse square root.
                : 1 / (float) sqrt(x);
    }

    /**
     * Converts degrees that may be outside +/-180 to an equivalent rotation value between
     * -180 (excl) and 180 (incl).
     * @param deg The degrees to normalize
     * @return A value above -180 and up to 180 that has an equivalent angle to the input.
     */
    public static float normalizeDegrees(float deg) {
        return deg - 360 * (float) ceil((deg - 180) / 360);
    }

    /**
     * Converts radians that may be outside +/-PI to an equivalent rotation value.
     * @param rad The radians to normalize
     * @return A value above -PI and up to pi that has an equivalent angle to the input.
     */
    public static float normalizeRadians(float rad) {
        return rad - (F_PI * 2) * (float) ceil((rad - F_PI) / (F_PI * 2));
    }

    private MathHelper() {}
}
