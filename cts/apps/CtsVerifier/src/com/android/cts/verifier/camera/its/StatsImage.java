/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.cts.verifier.camera.its;

import android.util.Log;

enum StatsFormat {
    RAW10_STATS("Raw10Stats"),
    RAW10_QUAD_BAYER_STATS("Raw10QuadBayerStats"),
    RAW16_STATS("Raw16Stats"),
    RAW16_QUAD_BAYER_STATS("Raw16QuadBayerStats");

    private String value;

    private StatsFormat(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }
}

public class StatsImage {

    static {
        try {
            System.loadLibrary("ctsverifier_jni");
        } catch (UnsatisfiedLinkError e) {
            Log.e("StatsImage", "Error loading cts verifier JNI library");
            e.printStackTrace();
        }
    }

    /**
     * Compute standard bayer or quad bayer stats (mean and variance) images.
     *
     * @param img Byte array calculated from raw image.
     * @param statsFormat Stats image format, which can be one of StatsFormat values.
     * @param width The width of raw image.
     * @param height The height of raw image.
     * @param aaX The x-coordinate of top-left point of active array crop region.
     * @param aaY The y-coordinate of top-left point of active array crop region.
     * @param aaW The width of active array crop region.
     * @param aaH The height of active array crop region.
     * @param gridW The width of grid region.
     * @param gridH The height of grid region.
     * @return Stats images represented by a float array.
     */
    public native static float[] computeStatsImage(byte[] img, String statsFormat,
        int width, int height, int aaX, int aaY, int aaW, int aaH, int gridW, int gridH);

}
