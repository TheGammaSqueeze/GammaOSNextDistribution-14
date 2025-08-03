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

package android.uirendering.cts.bitmapverifiers;

import android.uirendering.cts.util.CompareUtils;

/**
 * Verifies that colors are in between 2 given values
 */
public class AntiAliasBitmapVerifier extends BitmapVerifier {

    private final int mColor1;
    private final int mColor2;

    public AntiAliasBitmapVerifier(int color1, int color2) {
        mColor1 = color1;
        mColor2 = color2;
    }

    @Override
    public boolean verify(int[] bitmap, int offset, int stride, int width, int height) {
        int count = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (CompareUtils.verifyPixelBetweenColors(
                        bitmap[indexFromXAndY(x, y, stride, offset)], mColor1, mColor2)) {
                    count++;
                }
            }
        }
        return count > 0;
    }
}
