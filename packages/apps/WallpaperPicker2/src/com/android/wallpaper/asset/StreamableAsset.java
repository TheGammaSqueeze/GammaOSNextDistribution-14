/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.asset;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Represents Asset types for which bytes can be read directly, allowing for flexible bitmap
 * decoding.
 */
public abstract class StreamableAsset extends Asset {
    private static final ExecutorService sExecutorService = Executors.newCachedThreadPool();
    private static final String TAG = "StreamableAsset";

    private BitmapRegionDecoder mBitmapRegionDecoder;
    private Point mDimensions;

    /**
     * Scales and returns a new Rect from the given Rect by the given scaling factor.
     */
    public static Rect scaleRect(Rect rect, float scale) {
        return new Rect(
                Math.round((float) rect.left * scale),
                Math.round((float) rect.top * scale),
                Math.round((float) rect.right * scale),
                Math.round((float) rect.bottom * scale));
    }

    /**
     * Maps from EXIF orientation tag values to counterclockwise degree rotation values.
     */
    private static int getDegreesRotationForExifOrientation(int exifOrientation) {
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                return 0;
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                Log.w(TAG, "Unsupported EXIF orientation " + exifOrientation);
                return 0;
        }
    }

    @Override
    public void decodeBitmap(int targetWidth, int targetHeight, boolean useHardwareBitmapIfPossible,
                             BitmapReceiver receiver) {
        sExecutorService.execute(() -> {
            int newTargetWidth = targetWidth;
            int newTargetHeight = targetHeight;
            int exifOrientation = getExifOrientation();
            // Switch target height and width if image is rotated 90 or 270 degrees.
            if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90
                    || exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
                int tempHeight = newTargetHeight;
                newTargetHeight = newTargetWidth;
                newTargetWidth = tempHeight;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();

            Point rawDimensions = calculateRawDimensions();
            // Raw dimensions may be null if there was an error opening the underlying input stream.
            if (rawDimensions == null) {
                decodeBitmapCompleted(receiver, null);
                return;
            }
            options.inSampleSize = BitmapUtils.calculateInSampleSize(
                    rawDimensions.x, rawDimensions.y, newTargetWidth, newTargetHeight);
            if (useHardwareBitmapIfPossible) {
                options.inPreferredConfig = Config.HARDWARE;
            }

            InputStream inputStream = openInputStream();
            Bitmap bitmap = null;
            if (inputStream != null) {
                bitmap = BitmapFactory.decodeStream(inputStream, null, options);
                closeInputStream(
                        inputStream, "Error closing the input stream used "
                                + "to decode the full bitmap");

                // Rotate output bitmap if necessary because of EXIF orientation tag.
                int matrixRotation = getDegreesRotationForExifOrientation(exifOrientation);
                if (matrixRotation > 0) {
                    Matrix rotateMatrix = new Matrix();
                    rotateMatrix.setRotate(matrixRotation);
                    bitmap = Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                            rotateMatrix, false);
                }
            }
            decodeBitmapCompleted(receiver, bitmap);
        });
    }

    @Override
    public void decodeBitmap(BitmapReceiver receiver) {
        sExecutorService.execute(() -> {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Config.HARDWARE;
            InputStream inputStream = openInputStream();
            Bitmap bitmap = null;
            if (inputStream != null) {
                bitmap = BitmapFactory.decodeStream(inputStream, null, options);
                closeInputStream(inputStream,
                        "Error closing the input stream used to decode the full bitmap");

                // Rotate output bitmap if necessary because of EXIF orientation tag.
                int exifOrientation = getExifOrientation();
                int matrixRotation = getDegreesRotationForExifOrientation(exifOrientation);
                if (matrixRotation > 0) {
                    Matrix rotateMatrix = new Matrix();
                    rotateMatrix.setRotate(matrixRotation);
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                            bitmap.getHeight(), rotateMatrix, false);
                }
            }
            decodeBitmapCompleted(receiver, bitmap);
        });
    }

    @Override
    public void decodeRawDimensions(Activity unused, DimensionsReceiver receiver) {
        sExecutorService.execute(() -> {
            Point result = calculateRawDimensions();
            new Handler(Looper.getMainLooper()).post(() -> {
                receiver.onDimensionsDecoded(result);
            });
        });
    }

    @Override
    public void decodeBitmapRegion(Rect rect, int targetWidth, int targetHeight,
            boolean shouldAdjustForRtl, BitmapReceiver receiver) {
        runDecodeBitmapRegionTask(rect, targetWidth, targetHeight, shouldAdjustForRtl, receiver);
    }

    @Override
    public boolean supportsTiling() {
        return true;
    }

    /**
     * Fetches an input stream of bytes for the wallpaper image asset and provides the stream
     * asynchronously back to a {@link StreamReceiver}.
     */
    public void fetchInputStream(final StreamReceiver streamReceiver) {
        sExecutorService.execute(() -> {
            InputStream result = openInputStream();
            new Handler(Looper.getMainLooper()).post(() -> {
                streamReceiver.onInputStreamOpened(result);
            });
        });
    }

    /**
     * Returns an InputStream representing the asset. Should only be called off the main UI thread.
     */
    @Nullable
    protected abstract InputStream openInputStream();

    /**
     * Gets the EXIF orientation value of the asset. This method should only be called off the main UI
     * thread.
     */
    protected int getExifOrientation() {
        // By default, assume that the EXIF orientation is normal (i.e., bitmap is rotated 0 degrees
        // from how it should be rendered to a viewer).
        return ExifInterface.ORIENTATION_NORMAL;
    }

    /**
     * Decodes and downscales a bitmap region off the main UI thread.
     *
     * @param rect         Rect representing the crop region in terms of the original image's resolution.
     * @param targetWidth  Width of target view in physical pixels.
     * @param targetHeight Height of target view in physical pixels.
     * @param isRtl
     * @param receiver     Called with the decoded bitmap region or null if there was an error decoding
     *                     the bitmap region.
     */
    public void runDecodeBitmapRegionTask(Rect rect, int targetWidth, int targetHeight,
            boolean isRtl, BitmapReceiver receiver) {
        sExecutorService.execute(() -> {
            int newTargetWidth = targetWidth;
            int newTargetHeight = targetHeight;
            Rect cropRect = rect;
            int exifOrientation = getExifOrientation();
            // Switch target height and width if image is rotated 90 or 270 degrees.
            if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90
                    || exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
                int tempHeight = newTargetHeight;
                newTargetHeight = newTargetWidth;
                newTargetWidth = tempHeight;
            }

            // Rotate crop rect if image is rotated more than 0 degrees.
            Point dimensions = calculateRawDimensions();
            cropRect = CropRectRotator.rotateCropRectForExifOrientation(
                    dimensions, cropRect, exifOrientation);

            // If we're in RTL mode, center in the rightmost side of the image
            if (isRtl) {
                cropRect.set(dimensions.x - cropRect.right, cropRect.top,
                        dimensions.x - cropRect.left, cropRect.bottom);
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = BitmapUtils.calculateInSampleSize(
                    cropRect.width(), cropRect.height(), newTargetWidth, newTargetHeight);

            if (mBitmapRegionDecoder == null) {
                mBitmapRegionDecoder = openBitmapRegionDecoder();
            }

            // Bitmap region decoder may have failed to open if there was a problem with the
            // underlying InputStream.
            if (mBitmapRegionDecoder != null) {
                try {
                    Bitmap bitmap = mBitmapRegionDecoder.decodeRegion(cropRect, options);

                    // Rotate output bitmap if necessary because of EXIF orientation.
                    int matrixRotation = getDegreesRotationForExifOrientation(exifOrientation);
                    if (matrixRotation > 0) {
                        Matrix rotateMatrix = new Matrix();
                        rotateMatrix.setRotate(matrixRotation);
                        bitmap = Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), rotateMatrix,
                                false);
                    }
                    decodeBitmapCompleted(receiver, bitmap);
                    return;
                } catch (OutOfMemoryError e) {
                    Log.e(TAG, "Out of memory and unable to decode bitmap region", e);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Illegal argument for decoding bitmap region", e);
                }
            }
            decodeBitmapCompleted(receiver, null);
        });
    }

    /**
     * Decodes the raw dimensions of the asset without allocating memory for the entire asset. Adjusts
     * for the EXIF orientation if necessary.
     *
     * @return Dimensions as a Point where width is represented by "x" and height by "y".
     */
    @Nullable
    public Point calculateRawDimensions() {
        if (mDimensions != null) {
            return mDimensions;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        InputStream inputStream = openInputStream();
        // Input stream may be null if there was an error opening it.
        if (inputStream == null) {
            return null;
        }
        BitmapFactory.decodeStream(inputStream, null, options);
        closeInputStream(inputStream, "There was an error closing the input stream used to calculate "
                + "the image's raw dimensions");

        int exifOrientation = getExifOrientation();
        // Swap height and width if image is rotated 90 or 270 degrees.
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90
                || exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
            mDimensions = new Point(options.outHeight, options.outWidth);
        } else {
            mDimensions = new Point(options.outWidth, options.outHeight);
        }

        return mDimensions;
    }

    /**
     * Returns a BitmapRegionDecoder for the asset.
     */
    @Nullable
    private BitmapRegionDecoder openBitmapRegionDecoder() {
        InputStream inputStream = null;
        BitmapRegionDecoder brd = null;

        try {
            inputStream = openInputStream();
            // Input stream may be null if there was an error opening it.
            if (inputStream == null) {
                return null;
            }
            brd = BitmapRegionDecoder.newInstance(inputStream, true);
        } catch (IOException e) {
            Log.w(TAG, "Unable to open BitmapRegionDecoder", e);
        } finally {
            closeInputStream(inputStream, "Unable to close input stream used to create "
                    + "BitmapRegionDecoder");
        }

        return brd;
    }

    /**
     * Closes the provided InputStream and if there was an error, logs the provided error message.
     */
    private void closeInputStream(InputStream inputStream, String errorMessage) {
        try {
            inputStream.close();
        } catch (IOException e) {
            Log.e(TAG, errorMessage);
        }
    }

    /**
     * Interface for receiving unmodified input streams of the underlying asset without any
     * downscaling or other decoding options.
     */
    public interface StreamReceiver {

        /**
         * Called with an opened input stream of bytes from the underlying image asset. Clients must
         * close the input stream after it has been read. Returns null if there was an error opening the
         * input stream.
         */
        void onInputStreamOpened(@Nullable InputStream inputStream);
    }
}



