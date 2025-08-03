/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.uirendering.cts.util;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.Bitmap;
import android.uirendering.cts.bitmapcomparers.BitmapComparer;
import android.uirendering.cts.bitmapverifiers.BitmapVerifier;
import android.uirendering.cts.differencevisualizers.PassFailVisualizer;

public class BitmapAsserter {
    /**
     * Compares the two bitmaps saved using the given test. If they fail, the files are saved using
     * the test name.
     */
    public static void assertBitmapsAreSimilar(Bitmap bitmap1, Bitmap bitmap2,
            BitmapComparer comparer, String debugMessage) {
        boolean success;
        int width = bitmap1.getWidth();
        int height = bitmap1.getHeight();

        if (width != bitmap2.getWidth() || height != bitmap2.getHeight()) {
            fail("Can't compare bitmaps of different sizes");
        }

        int[] pixels1 = new int[width * height];
        int[] pixels2 = new int[width * height];
        bitmap1.getPixels(pixels1, 0, width, 0, 0, width, height);
        bitmap2.getPixels(pixels2, 0, width, 0, 0, width, height);
        success = comparer.verifySame(pixels1, pixels2, 0, width, width, height);

        if (!success) {
            BitmapDumper.dumpBitmaps(bitmap1, bitmap2, new PassFailVisualizer());
        }

        assertTrue(debugMessage, success);
    }

    /**
     * Tests to see if a bitmap passes a verifier's test. If it doesn't the bitmap is saved to the
     * sdcard.
     */
    public static void assertBitmapIsVerified(Bitmap bitmap, BitmapVerifier bitmapVerifier,
            String debugMessage) {
        boolean success = bitmapVerifier.verify(bitmap);
        if (!success) {
            BitmapDumper.dumpBitmaps(bitmap, bitmapVerifier.getDifferenceBitmap());
        }
        assertTrue(debugMessage, success);
    }
}
