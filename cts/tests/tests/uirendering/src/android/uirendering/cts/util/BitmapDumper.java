/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.graphics.Bitmap;
import android.uirendering.cts.differencevisualizers.DifferenceVisualizer;
import android.uirendering.cts.runner.TestArtifactCollector;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

/**
 * A utility class that will allow the user to save bitmaps to the sdcard on the device.
 */
public final class BitmapDumper {
    private static final String TAG = "BitmapDumper";
    private static final String TYPE_IDEAL_RENDERING = "idealCapture";
    private static final String TYPE_TESTED_RENDERING = "testedCapture";
    private static final String TYPE_VISUALIZER_RENDERING = "visualizer";
    private static final String TYPE_SINGULAR = "capture";

    private BitmapDumper() {}

    /**
     * Saves two files, one the capture of an ideal drawing, and one the capture of the tested
     * drawing. The third file saved is a bitmap that is returned from the given visualizer's
     * method.
     * The files are saved to the sdcard directory
     */
    public static void dumpBitmaps(Bitmap idealBitmap, Bitmap testedBitmap,
            DifferenceVisualizer differenceVisualizer) {
        Bitmap visualizerBitmap;

        int width = idealBitmap.getWidth();
        int height = idealBitmap.getHeight();
        int[] testedArray = new int[width * height];
        int[] idealArray = new int[width * height];
        idealBitmap.getPixels(testedArray, 0, width, 0, 0, width, height);
        testedBitmap.getPixels(idealArray, 0, width, 0, 0, width, height);
        int[] visualizerArray = differenceVisualizer.getDifferences(idealArray, testedArray);
        visualizerBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        visualizerBitmap.setPixels(visualizerArray, 0, width, 0, 0, width, height);
        Bitmap croppedBitmap = Bitmap.createBitmap(testedBitmap, 0, 0, width, height);

        dumpBitmap(idealBitmap, TYPE_IDEAL_RENDERING);
        dumpBitmap(croppedBitmap, TYPE_TESTED_RENDERING);
        dumpBitmap(visualizerBitmap, TYPE_VISUALIZER_RENDERING);
    }

    /**
     * Dumps the given test bitamp & visualizer bitmap
     */
    public static void dumpBitmaps(Bitmap testedBitmap, Bitmap visualizerBitmap) {
        dumpBitmap(testedBitmap, TYPE_TESTED_RENDERING);
        dumpBitmap(visualizerBitmap, TYPE_VISUALIZER_RENDERING);
    }

    /**
     * Dumps the given bitmap with a generic label
     */
    public static void dumpBitmap(Bitmap bitmap) {
        dumpBitmap(bitmap, TYPE_SINGULAR);
    }

    /**
     * Dumps the given bitmap with the given label
     */
    public static void dumpBitmap(Bitmap bitmap, String label) {
        if (bitmap == null) {
            Log.d(TAG, "File not saved, bitmap was null");
            return;
        }

        TestArtifactCollector.addArtifact(label + ".png", file -> {
            saveBitmap(bitmap, file);
            return null;
        });
    }

    private static void logIfBitmapSolidColor(String fileName, Bitmap bitmap) {
        int firstColor = bitmap.getPixel(0, 0);
        for (int x = 0; x < bitmap.getWidth(); x++) {
            for (int y = 0; y < bitmap.getHeight(); y++) {
                if (bitmap.getPixel(x, y) != firstColor) {
                    return;
                }
            }
        }

        Log.w(TAG, String.format("%s entire bitmap color is %x", fileName, firstColor));
    }

    private static void saveBitmap(Bitmap bitmap, File file) {
        logIfBitmapSolidColor(file.getName(), bitmap);

        try (FileOutputStream fileStream = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 0 /* ignored for PNG */, fileStream);
            fileStream.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
