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
package android.view.cts.surfacevalidator;

import android.graphics.Rect;
import android.media.Image;
import android.util.Log;

public abstract class MultiFramePixelChecker extends PixelChecker {
    private static final int PIXEL_STRIDE = 4;
    private static final String TAG = "PixelChecker";

    private int mMatchingPixelCount = 0;
    private final PixelColor[] mPixelColors;
    private int mStartingColorIndex = 0;
    private boolean mStartingColorFound = false;

    public MultiFramePixelChecker(int[] colors) {
        mPixelColors = new PixelColor[colors.length];
        for (int i = 0; i < colors.length; i++) {
            mPixelColors[i] = new PixelColor(colors[i]);
        }
    }

    private PixelColor getColor(long frameNumber) {
        return mPixelColors[(int) ((frameNumber + mStartingColorIndex) % mPixelColors.length)];
    }

    private boolean findStartingColor(Image.Plane plane, Rect boundsToCheck) {
        for (mStartingColorIndex = 0; mStartingColorIndex < mPixelColors.length;
                mStartingColorIndex++) {
            int numMatchingPixels = getNumMatchingPixels(mPixelColors[mStartingColorIndex], plane,
                    boundsToCheck);
            if (checkPixels(numMatchingPixels, 0 , 0)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean validatePlane(Image.Plane plane, long frameNumber, Rect boundsToCheck, int width,
            int height) {
        if (!mStartingColorFound) {
            mStartingColorFound = findStartingColor(plane, boundsToCheck);
            if (mStartingColorFound) {
                Log.d(TAG, "Starting color found in frame " + frameNumber);
                // Subtract frameNumber from startingColorIndex because frameNumber may not be 0.
                // If starting color found on non first frame, we need to adjust the index to frame.
                mStartingColorIndex -= frameNumber;
            } else {
                Log.d(TAG, "Starting color not found in frame " + frameNumber);
                // If the frame was empty, continue checking since empty frame may just mean the
                // VirtualDisplay hasn't rendered any content yet
                return isEmpty(plane, boundsToCheck);
            }
        }

        mMatchingPixelCount = getNumMatchingPixels(getColor(frameNumber), plane, boundsToCheck);
        return checkPixels(mMatchingPixelCount, width, height);
    }
}
