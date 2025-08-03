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

package com.android.cts.mockime;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

/**
 * A utility class to put a unique image on {@link MockIme} so that whether the software keyboard is
 * visible to the user or not can be determined from the screenshot.
 */
public final class Watermark {
    /**
     * Tolerance level between the expected color and the actual color in each color channel.
     *
     * <p>See Bug 174534092 about why we ended up having this.</p>
     */
    private static final int TOLERANCE = 8;

    /**
     * A utility class that represents A8R8G8B bitmap as an integer array.
     */
    private static final class BitmapImage {
        @NonNull
        private final int[] mPixels;
        private final int mWidth;
        private final int mHeight;

        BitmapImage(@NonNull int[] pixels, int width, int height) {
            mWidth = width;
            mHeight = height;
            mPixels = pixels;
        }

        /**
         * Create {@link BitmapImage} from {@link Bitmap}.
         *
         * @param bitmap {@link Bitmap} from which {@link BitmapImage} will be created.
         * @return A new instance of {@link BitmapImage}.
         */
        @AnyThread
        static BitmapImage createFromBitmap(@NonNull Bitmap bitmap) {
            final int width = bitmap.getWidth();
            final int height = bitmap.getHeight();
            final int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            return new BitmapImage(pixels, width, height);
        }

        /**
         * @return Width of this image.
         */
        @AnyThread
        int getWidth() {
            return mWidth;
        }

        /**
         * @return Height of this image.
         */
        @AnyThread
        int getHeight() {
            return mHeight;
        }

        /**
         * @return {@link Bitmap} that has the same pixel patterns.
         */
        @AnyThread
        @NonNull
        Bitmap toBitmap() {
            return Bitmap.createBitmap(mPixels, mWidth, mHeight, Bitmap.Config.ARGB_8888);
        }

        /**
         * Checks if the same image can be found in the specified {@link BitmapImage} within
         * within {@link #TOLERANCE}.
         *
         * @param targetImage {@link BitmapImage} to be checked.
         * @param offsetX X offset in the {@code targetImage} used when comparing.
         * @param offsetY Y offset in the {@code targetImage} used when comparing.
         * @return {@true} if two given images are the same within {@link #TOLERANCE}.
         */
        @AnyThread
        boolean robustMatch(@NonNull BitmapImage targetImage, int offsetX, int offsetY) {
            final int targetWidth = targetImage.getWidth();
            final int targetHeight = targetImage.getHeight();
            final int[] targetPixels = targetImage.mPixels;
            final int[] sourcePixels = mPixels;

            if (offsetX < 0 || targetWidth <= mWidth - 1 + offsetX) return false;
            if (offsetY < 0 || targetHeight <= mHeight - 1 + offsetY) return false;

            for (int y = 0; y < mHeight; ++y) {
                for (int x = 0; x < mWidth; ++x) {
                    final int targetX = x + offsetX;
                    final int targetY = y + offsetY;
                    final int targetPx = targetPixels[targetY * targetWidth + targetX];
                    final int sourcePx = sourcePixels[y * mWidth + x];
                    // Compares two given pixels (targetPx & sourcePx) to determine whether those
                    // two pixels are considered to be the same within {@link #TOLERANCE}.
                    boolean match = targetPx == sourcePx
                            || (Math.abs(Color.red(targetPx) - Color.red(sourcePx)) <= TOLERANCE
                            && Math.abs(Color.green(targetPx) - Color.green(sourcePx)) <= TOLERANCE
                            && Math.abs(Color.blue(targetPx) - Color.blue(sourcePx)) <= TOLERANCE);
                    if (!match) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    /**
     * Not intended to be instantiated.
     */
    private Watermark() {
    }

    private static final BitmapImage sImage;
    static {
        final long[] bitImage = new long[] {
                0b0000000000000000000000000000000000000000000000000000000000000000L,
                0b0000000000000000000000000000000000000000000000000000000000000000L,
                0b0000000000000000000000000000000000000000000000000000000000000000L,
                0b0001111111111111111111111111111111111111111111111111111111111000L,
                0b0001111111111111111111111111111111111111111111111111111111111000L,
                0b0001110010110100010000111100001000100001111000010001011010011000L,
                0b0001100011111011001110101101100000001101101011100110111110011000L,
                0b0001101101001011101111000011110111011110000111101110100101111000L,
                0b0001111100000100110001010010011111110010010100011001000001111000L,
                0b0001110010110100010000111100001000100001111000010001011010011000L,
                0b0001110011111011001110101101100000001101101011100110111110011000L,
                0b0001110101001011101111000011110111011110000111101110100101011000L,
                0b0001100100000100110001010010011111110010010100011001000001011000L,
                0b0001101110110100010000111100001000100001111000010001011011111000L,
                0b0001111111111011001110101101100000001101101011100110111111111000L,
                0b0001110011001011101111000011110111011110000111101110100110011000L,
                0b0001100001000100110001010010011111110010010100011001000100011000L,
                0b0001101101010100010000111100001000100001111000010001010101111000L,
                0b0001111110011011001110101101100000001101101011100110110011111000L,
                0b0001110010111011101111000011110111011110000111101110111010011000L,
                0b0001100001111100110001010010011111110010010100011001111100011000L,
                0b0001101101001000010000111100001000100001111000010000100101111000L,
                0b0001111110000101001110101101100000001101101011100101000011111000L,
                0b0001110010110100101111000011110111011110000111101001011010011000L,
                0b0001100001111011110001010010011111110010010100011110111100011000L,
                0b0001101101001011100000111100001000100001111000001110100101111000L,
                0b0001111110000100010110101101100000001101101011010001000011111000L,
                0b0001110010110100010011000011110111011110000110010001011010011000L,
                0b0001100001111011101111010010011111110010010111101110111100011000L,
                0b0001101101001011101111011100001000100001110111101110100101111000L,
                0b0001111110000100010001011101100000001101110100010001000011111000L,
                0b0001110010110100010000111101110111011101111000010001011010011000L,
                0b0001100001111011101110100101100010001101001011101110111100011000L,
                0b0001101101001011101111000011110111011110000111101110100101111000L,
                0b0001111110000100010001011010011101110010110100010001000011111000L,
                0b0001110010110100010000111100001000100001111000010001011010011000L,
                0b0001100001111011101110100101100010001101001011101110111100011000L,
                0b0001101101001011101111000011110111011110000111101110100101111000L,
                0b0001111110000100010001011010011101110010110100010001000011111000L,
                0b0001110010110100010000111100001000100001111000010001011010011000L,
                0b0001100001111011101110100101100010001101001011101110111100011000L,
                0b0001101101001011101111000011110111011110000111101110100101111000L,
                0b0001111110000100010001011010011101110010110100010001000011111000L,
                0b0001110010110100010000111100001000100001111000010001011010011000L,
                0b0001100001111011101110100101100010001101001011101110111100011000L,
                0b0001101101001011101111000011110111011110000111101110100101111000L,
                0b0001111110000100010001011010011101110010110100010001000011111000L,
                0b0001110010110100010000111100001000100001111000010001011010011000L,
                0b0001100001111011101110100101100010001101001011101110111100011000L,
                0b0001101101001011101111000011110111011110000111101110100101111000L,
                0b0001111110000100010001011010011101110010110100010001000011111000L,
                0b0001110010110100010000111100001000100001111000010001011010011000L,
                0b0001100001111011101110100101100010001101001011101110111100011000L,
                0b0001101101001011101111000011110111011110000111101110100101111000L,
                0b0001111110000100010001011010011101110010110100010001000011111000L,
                0b0001110010110100010000111100001000100001111000010001011010011000L,
                0b0001100001111011101110100101100010001101001011101110111100011000L,
                0b0001101101001011101111000011110111011110000111101110100101111000L,
                0b0001111110000100010001011010011101110010110100010001000011111000L,
                0b0001111111111111111111111111111111111111111111111111111111111000L,
                0b0001111111111111111111111111111111111111111111111111111111111000L,
                0b0000000000000000000000000000000000000000000000000000000000000000L,
                0b0000000000000000000000000000000000000000000000000000000000000000L,
                0b0000000000000000000000000000000000000000000000000000000000000000L,
        };
        final int size = 64;
        final long mask = 0b1000000000000000000000000000000000000000000000000000000000000000L;
        final int[] pixels = new int[size * size];
        for (int y = 0; y < size; ++y) {
            for (int x = 0; x < size; ++x) {
                pixels[y * size + x] =
                        ((bitImage[y] << x) & mask) == mask ? 0xffffffff : 0xff000000;
            }
        }
        sImage = new BitmapImage(pixels, size, size);
    }

    /**
     * @return {@link Bitmap} object of the unique image.
     */
    public static Bitmap create() {
        return sImage.toBitmap();
    }

    /**
     * Check the unique image can be found in the specified {@link Bitmap}.
     *
     * @param bitmap {@link Bitmap} to be checked.
     * @return {@code true} if the corresponding unique image is found in the {@code bitmap}.
     */
    public static boolean detect(@NonNull Bitmap bitmap) {
        final BitmapImage targetImage = BitmapImage.createFromBitmap(bitmap);

        // Search from the bottom line with an assumption that the IME is shown at the bottom.
        final int targetImageHeight = targetImage.getHeight();
        final int targetImageWidth = targetImage.getWidth();
        for (int offsetY = targetImageHeight - 1; offsetY >= 0; --offsetY) {
            for (int offsetX = 0; offsetX < targetImageWidth; ++offsetX) {
                if (sImage.robustMatch(targetImage, offsetX, offsetY)) {
                    return true;
                }
            }
        }
        return false;
    }
}
