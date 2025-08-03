package android.uirendering.cts.util;

import android.graphics.Color;

public class CompareUtils {
    /**
     * @return True if close enough
     */
    public static boolean verifyPixelWithThreshold(int color, int expectedColor, int threshold) {
        int diff = Math.abs(Color.red(color) - Color.red(expectedColor))
                + Math.abs(Color.green(color) - Color.green(expectedColor))
                + Math.abs(Color.blue(color) - Color.blue(expectedColor));
        return diff <= threshold;
    }

    /**
     * @param threshold Per channel differences for R / G / B channel against the average of these 3
     *                  channels. Should be less than 2 normally.
     * @return True if the color is close enough to be a gray scale color.
     */
    public static boolean verifyPixelGrayScale(int color, int threshold) {
        int average =  Color.red(color) + Color.green(color) + Color.blue(color);
        average /= 3;
        return Math.abs(Color.red(color) - average) <= threshold
                && Math.abs(Color.green(color) - average) <= threshold
                && Math.abs(Color.blue(color) - average) <= threshold;
    }

    /**
     * @return True if color strictly between inner and outer colors. This verifies that the
     * color is a mixture of the two, not just one or the other (for anti-aliased pixels).
     */
    public static boolean verifyPixelBetweenColors(int color, int expectedOuterColor,
            int expectedInnerColor) {
        if (color == expectedInnerColor || color == expectedOuterColor) {
            return false;
        }
        return color == ((color & expectedInnerColor) | (color & expectedOuterColor));
    }
}
