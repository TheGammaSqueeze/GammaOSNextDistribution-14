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

package com.android.tools.idea.validator;

import com.android.tools.idea.validator.ValidatorResult.ImageSize;
import com.android.tools.idea.validator.ValidatorResult.Metric;
import com.android.tools.layoutlib.annotations.NotNull;

import android.annotation.NonNull;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;

import com.google.android.apps.common.testing.accessibility.framework.utils.contrast.Image;
import javax.imageio.ImageIO;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;

/**
 * Image implementation to be used in Accessibility Test Framework.
 */
public class AtfBufferedImage implements Image {

    // The source buffered image, expected to contain the full screen rendered image of the layout.
    @NotNull private final BufferedImage mImage;
    // Metrics to be returned
    @NotNull private final Metric mMetric;

    // Points in before scaled coord
    private final int mLeft;
    private final int mTop;
    private final int mWidth;
    private final int mHeight;

    // Scale factors in case layoutlib scaled the screen.
    private final float mScaleX;
    private final float mScaleY;

    AtfBufferedImage(
            @NotNull BufferedImage image,
            @NotNull Metric metric,
            float scaleX,
            float scaleY) {
        // Without unscaling, atf does not recognize bounds that goes over the scaled image.
        // E.g. if pxl4 is scaled to 1k x 2k (originally 2k x 4k), then atf could request for
        // bounds at x:1.5k y:0, then without unscaling the call would fail.
        this(image,
            metric,
            0,
            0,
            (int) (image.getWidth() * 1.0f / scaleX),
            (int) (image.getHeight() * 1.0f / scaleY),
            scaleX,
            scaleY);
        assert(image.getType() == TYPE_INT_ARGB);

        // FOR DEBUGGING ONLY
        if (LayoutValidator.shouldSaveCroppedImages()) {
            saveImage(image);
        }
    }

    private AtfBufferedImage(
            @NotNull BufferedImage image,
            @NotNull Metric metric,
            int left,
            int top,
            int width,
            int height,
            float scaleX,
            float scaleY) {
        mImage = image;
        mMetric = metric;
        mLeft = left;
        mTop = top;
        mWidth = width;
        mHeight = height;
        mScaleX = scaleX;
        mScaleY = scaleY;
    }

    @Override
    public int getHeight() {
        return mHeight;
    }

    @Override
    public int getWidth() {
        return mWidth;
    }

    @Override
    @NotNull
    public Image crop(int left, int top, int width, int height) {
        return new AtfBufferedImage(mImage, mMetric, left, top, width, height, mScaleX, mScaleY);
    }

    /**
     * @return the region that matches the scaled buffered image. The returned image
     * will not have the width same as {@link #getWidth()} but rather width * mScaleX.
     */
    @Override
    @NotNull
    public int[] getPixels() {
        int scaledLeft = (int)(mLeft * mScaleX);
        int scaledTop = (int)(mTop * mScaleY);
        int scaledWidth = (int)(mWidth * mScaleX);
        int scaledHeight = (int)(mHeight * mScaleY);

        if (scaledWidth <= 0 || scaledHeight <= 0) {
            return new int[0];
        }

        BufferedImage cropped = mImage.getSubimage(
                scaledLeft, scaledTop, scaledWidth, scaledHeight);
        WritableRaster raster =
                cropped.copyData(cropped.getRaster().createCompatibleWritableRaster());
        int[] toReturn = ((DataBufferInt) raster.getDataBuffer()).getData();
        mMetric.mImageMemoryBytes += toReturn.length * 4;

        if (LayoutValidator.shouldSaveCroppedImages()) {
            saveImage(cropped);
        }

        return toReturn;
    }

    // FOR DEBUGGING ONLY
    private static int SAVE_IMAGE_COUNTER = 0;
    private void saveImage(BufferedImage image) {
        try {
            String name = SAVE_IMAGE_COUNTER + "_img_for_atf_LxT:WxH_" +
                    mLeft + "x" + mTop + ":" +
                    mWidth + "x" + mHeight;

            mMetric.mImageSizes.add(new ImageSize(mLeft, mTop, mWidth, mHeight));

            File output = new File(getDebugDir(), name);
            if (output.exists()) {
                output.delete();
            }
            ImageIO.write(image, "PNG", output);
            SAVE_IMAGE_COUNTER++;
        } catch (IOException ioe) {
            mMetric.mErrorMessage = ioe.getMessage();
        }
    }

    @NonNull
    private File getDebugDir() {
        File failureDir;
        String failureDirString = System.getProperty("debug.dir");
        if (failureDirString != null) {
            failureDir = new File(failureDirString);
        } else {
            String workingDirString = System.getProperty("user.dir");
            failureDir = new File(workingDirString, "out/debugs");
        }

        failureDir.mkdirs();
        return failureDir;
    }
}
