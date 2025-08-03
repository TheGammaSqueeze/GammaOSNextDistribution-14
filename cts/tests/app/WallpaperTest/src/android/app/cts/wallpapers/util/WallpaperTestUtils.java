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

package android.app.cts.wallpapers.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class WallpaperTestUtils {

    /**
     * Helper get a bitmap from a drawable
     */
    public static Bitmap getBitmap(Drawable drawable) {
        if (drawable == null) return null;
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        int width = Math.max(1, drawable.getIntrinsicWidth());
        int height = Math.max(1, drawable.getIntrinsicHeight());

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return result;
    }

    /**
     * Helper to check whether two bitmaps are similar.
     * <br>
     * This comparison is not perfect at all; but good enough for the purpose of wallpaper tests.
     * <br>
     * This method is designed to be tolerant enough to avoid false negative (i.e. returning false
     * when two bitmaps of the same image with different configurations are compared),
     * but strict enough to return false when comparing two different bitmaps unless they are
     * really similar.
     * <br>
     * @param requireSameDimensions if true, return false for two bitmaps with different dimensions.
     * Otherwise, downscale the bitmap with the largest width and perform the comparison.
     */
    public static boolean isSimilar(Bitmap bitmap1, Bitmap bitmap2, boolean requireSameDimensions) {
        if (bitmap1 == null || bitmap2 == null) return bitmap1 == null && bitmap2 == null;
        int width = bitmap1.getWidth();
        int height = bitmap1.getHeight();
        if (width != bitmap2.getWidth() || height != bitmap2.getHeight()) {
            if (requireSameDimensions) return false;
            Bitmap largest = width >= bitmap2.getWidth() ? bitmap1 : bitmap2;
            Bitmap smallest = largest == bitmap1 ? bitmap2 : bitmap1;
            Bitmap rescaled = Bitmap.createScaledBitmap(
                    largest, smallest.getWidth(), smallest.getHeight(), true);
            return isSimilar(smallest, rescaled, true);
        }

        // two pixels are considered similar if each of their ARGB value has at most a 10% diff.
        float tolerance = 0.1f;

        // two bitmaps are considered similar if at least 90% of pixels are similar
        float acceptedMismatchingPixelRate = 0.1f;

        // only test 1% of the pixels
        int pixelInverseTestRate = 100;
        int totalPixelTested = width * height / pixelInverseTestRate;

        return IntStream.range(0, totalPixelTested).map(c -> pixelInverseTestRate * c).filter(c -> {
            int x = c % width;
            int y = c / width;
            int pixel1 = bitmap1.getPixel(x, y);
            int pixel2 = bitmap2.getPixel(x, y);

            return Stream
                    .<IntFunction<Integer>>of(Color::alpha, Color::red, Color::green, Color::blue)
                    .mapToInt(channel -> Math.abs(channel.apply(pixel1) - channel.apply(pixel2)))
                    .anyMatch(delta -> delta / 255f > tolerance);
        }).count() / (float) (totalPixelTested) <= acceptedMismatchingPixelRate;
    }

    /**
     * Helper to check whether two drawables are similar.
     * <br>
     * Use {@link #getBitmap} to convert the drawables to bitmap,
     * then {@link #isSimilar(Bitmap, Bitmap, boolean)} to perform the similarity comparison.
     * Return true if both drawables are null.
     */
    public static boolean isSimilar(Drawable drawable1, Drawable drawable2,
            boolean requireSameDimensions) {
        return isSimilar(getBitmap(drawable1), getBitmap(drawable2), requireSameDimensions);
    }
}
