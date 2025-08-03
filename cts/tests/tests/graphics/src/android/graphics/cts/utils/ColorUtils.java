/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.graphics.cts.utils;

import android.graphics.Color;

/**
 * Copied from frameworks/base/core to support CAM library for SystemPalette CTS test
 *
 * A set of color-related utility methods, building upon those available in {@code Color}.
 */
public final class ColorUtils {

    private static final double XYZ_WHITE_REFERENCE_X = 95.047;
    private static final double XYZ_WHITE_REFERENCE_Y = 100;
    private static final double XYZ_WHITE_REFERENCE_Z = 108.883;
    private static final double XYZ_EPSILON = 0.008856;
    private static final double XYZ_KAPPA = 903.3;

    /**
     * Converts a color from CIE XYZ to its RGB representation.
     *
     * <p>This method expects the XYZ representation to use the D65 illuminant and the CIE
     * 2° Standard Observer (1931).</p>
     *
     * @param x X component value [0...95.047)
     * @param y Y component value [0...100)
     * @param z Z component value [0...108.883)
     * @return int containing the RGB representation
     */
    public static int xyzToColor(double x, double y, double z) {
        double r = (x * 3.2406 + y * -1.5372 + z * -0.4986) / 100;
        double g = (x * -0.9689 + y * 1.8758 + z * 0.0415) / 100;
        double b = (x * 0.0557 + y * -0.2040 + z * 1.0570) / 100;

        r = r > 0.0031308 ? 1.055 * Math.pow(r, 1 / 2.4) - 0.055 : 12.92 * r;
        g = g > 0.0031308 ? 1.055 * Math.pow(g, 1 / 2.4) - 0.055 : 12.92 * g;
        b = b > 0.0031308 ? 1.055 * Math.pow(b, 1 / 2.4) - 0.055 : 12.92 * b;

        return Color.rgb(
                (int) constrain((int) Math.round(r * 255), 0, 255),
                (int) constrain((int) Math.round(g * 255), 0, 255),
                (int) constrain((int) Math.round(b * 255), 0, 255));
    }

    private static float constrain(float amount, float low, float high) {
        return amount < low ? low : (amount > high ? high : amount);
    }
}
