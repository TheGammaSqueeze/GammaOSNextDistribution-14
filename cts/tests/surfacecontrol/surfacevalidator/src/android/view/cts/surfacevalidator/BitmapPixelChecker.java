/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.view.WindowInsets.Type.displayCutout;
import static android.view.WindowInsets.Type.systemBars;
import static android.view.cts.surfacevalidator.CapturedActivity.STORAGE_DIR;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Environment;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.rules.TestName;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class BitmapPixelChecker {
    private static final String TAG = "BitmapPixelChecker";
    private final PixelColor mPixelColor;
    private final Rect mBoundToLog;

    public BitmapPixelChecker(int color) {
        this(color, null);
    }

    public BitmapPixelChecker(int color, Rect boundsToLog) {
        mPixelColor = new PixelColor(color);
        mBoundToLog = boundsToLog;
    }

    public int getNumMatchingPixels(Bitmap bitmap, Rect bounds) {
        Rect boundsToLog = mBoundToLog;
        if (boundsToLog == null) {
            boundsToLog = new Rect(bounds);
        }

        int numMatchingPixels = 0;
        int numErrorsLogged = 0;
        for (int x = bounds.left; x < bounds.right; x++) {
            for (int y = bounds.top; y < bounds.bottom; y++) {
                int color = bitmap.getPixel(x, y);
                if (mPixelColor.matchesColor(color)) {
                    numMatchingPixels++;
                } else if (boundsToLog.contains(x, y) && numErrorsLogged < 100) {
                    // We don't want to spam the logcat with errors if something is really
                    // broken. Only log the first 100 errors.
                    int expectedColor = Color.argb(mPixelColor.mAlpha, mPixelColor.mRed,
                            mPixelColor.mGreen, mPixelColor.mBlue);
                    Log.e(TAG, String.format(
                            "Failed to match (%d, %d) color=0x%08X expected=0x%08X", x, y,
                            color, expectedColor));
                    numErrorsLogged++;
                }
            }
        }
        return numMatchingPixels;
    }

    private void applyInsetsToLogBounds(Insets insets) {
        if (mBoundToLog != null && !mBoundToLog.isEmpty()) {
            mBoundToLog.inset(insets);
        }
    }

    /**
     * @param expectedMatchingPixels Pass in -1 if you want all the pixels to match.
     */
    public static void validateScreenshot(TestName testName, Activity activity,
            BitmapPixelChecker pixelChecker, int expectedMatchingPixels, Insets insets) {
        Bitmap screenshot =
                InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot(
                        activity.getWindow());
        assertNotNull("Failed to generate a screenshot", screenshot);
        Bitmap swBitmap = screenshot.copy(Bitmap.Config.ARGB_8888, false);
        screenshot.recycle();

        int width = swBitmap.getWidth();
        int height = swBitmap.getHeight();

        // Exclude insets in case the device doesn't support hiding insets.
        Rect bounds = new Rect(0, 0, width, height);
        bounds.inset(insets);
        pixelChecker.applyInsetsToLogBounds(insets);
        Log.d(TAG, "Checking bounds " + bounds + " boundsToLog=" + pixelChecker.mBoundToLog);
        int numMatchingPixels = pixelChecker.getNumMatchingPixels(swBitmap, bounds);
        if (expectedMatchingPixels == -1) {
            expectedMatchingPixels = bounds.width() * bounds.height();
        }
        boolean numMatches = expectedMatchingPixels == numMatchingPixels;
        if (!numMatches) {
            saveFailureCaptures(swBitmap, activity.getClass(), testName);
        }
        assertTrue("Expected " + expectedMatchingPixels + " received " + numMatchingPixels
                + " matching pixels in bitmap(" + width + "," + height + ")", numMatches);

        swBitmap.recycle();
    }

    private static void saveFailureCaptures(Bitmap failFrame, Class<?> clazz, TestName name) {
        String directoryName = Environment.getExternalStorageDirectory()
                + "/" + STORAGE_DIR
                + "/" + clazz.getSimpleName()
                + "/" + name.getMethodName();
        File testDirectory = new File(directoryName);
        if (testDirectory.exists()) {
            String[] children = testDirectory.list();
            if (children != null) {
                for (String file : children) {
                    new File(testDirectory, file).delete();
                }
            }
        } else {
            testDirectory.mkdirs();
        }

        String bitmapName = "frame.png";
        Log.d(TAG, "Saving file : " + bitmapName + " in directory : " + directoryName);

        File file = new File(directoryName, bitmapName);
        try (FileOutputStream fileStream = new FileOutputStream(file)) {
            failFrame.compress(Bitmap.CompressFormat.PNG, 85, fileStream);
            fileStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Insets getInsets(Activity activity) {
        return activity.getWindow()
                .getDecorView()
                .getRootWindowInsets()
                .getInsets(displayCutout() | systemBars());
    }
}
