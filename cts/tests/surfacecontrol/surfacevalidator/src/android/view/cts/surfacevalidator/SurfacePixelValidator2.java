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
package android.view.cts.surfacevalidator;

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.util.Log;
import android.util.SparseArray;
import android.view.Surface;


import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SurfacePixelValidator2 {
    private static final String TAG = "SurfacePixelValidator";

    private static final boolean DEBUG = false;

    private static final int MAX_CAPTURED_FAILURES = 5;
    private static final int PIXEL_STRIDE = 4;

    private final int mWidth;
    private final int mHeight;

    private final HandlerThread mWorkerThread;

    private final PixelChecker mPixelChecker;
    private final Rect mBoundsToCheck;
    private ImageReader mImageReader;

    private int mResultSuccessFrames;
    private int mResultFailureFrames;
    private final SparseArray<Bitmap> mFirstFailures = new SparseArray<>(MAX_CAPTURED_FAILURES);
    private long mFrameNumber = 0;

    private final int mRequiredNumFrames;

    private final CountDownLatch mRequiredNumFramesDrawnLatch = new CountDownLatch(1);

    private final Handler mHandler;

    private final ImageReader.OnImageAvailableListener mOnImageAvailable =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    if (mImageReader == null) {
                        return;
                    }

                    Trace.beginSection("Read buffer");
                    Image image = reader.acquireNextImage();

                    Image.Plane plane = image.getPlanes()[0];
                    if (plane.getPixelStride() != PIXEL_STRIDE) {
                        throw new IllegalStateException("pixel stride != " + PIXEL_STRIDE + "? "
                                + plane.getPixelStride());
                    }
                    Trace.endSection();

                    int totalFramesSeen;
                    boolean success = mPixelChecker.validatePlane(plane, mFrameNumber++,
                            mBoundsToCheck, mWidth, mHeight);
                    if (success) {
                        mResultSuccessFrames++;
                    } else {
                        mResultFailureFrames++;
                    }

                    totalFramesSeen = mResultSuccessFrames + mResultFailureFrames;
                    if (DEBUG) {
                        Log.d(TAG, "Received image " + success + " numSuccess="
                                + mResultSuccessFrames + " numFail=" + mResultFailureFrames
                                + " total=" + totalFramesSeen);
                    }

                    if (!success) {
                        Log.d(TAG, "Failure (" + mPixelChecker.getLastError()
                                + ") occurred on frame " + totalFramesSeen);

                        if (mFirstFailures.size() < MAX_CAPTURED_FAILURES) {
                            Log.d(TAG, "Capturing bitmap #" + mFirstFailures.size());
                            // error, worth looking at...
                            Bitmap capture = Bitmap.wrapHardwareBuffer(
                                            image.getHardwareBuffer(), null)
                                    .copy(Bitmap.Config.ARGB_8888, false);
                            mFirstFailures.put(totalFramesSeen, capture);
                        }
                    }

                    image.close();
                    if (totalFramesSeen >= mRequiredNumFrames) {
                        mRequiredNumFramesDrawnLatch.countDown();
                    }
                }
            };

    public SurfacePixelValidator2(Point size, @Nullable Rect boundsToCheck,
            PixelChecker pixelChecker, int requiredNumFrames) {
        mWidth = size.x;
        mHeight = size.y;

        mWorkerThread = new HandlerThread("SurfacePixelValidator");
        mWorkerThread.start();

        mPixelChecker = pixelChecker;
        if (boundsToCheck == null) {
            mBoundsToCheck = new Rect(0, 0, mWidth, mHeight);
        } else {
            mBoundsToCheck = new Rect(boundsToCheck);
        }

        Log.d(TAG, "boundsToCheck=" + mBoundsToCheck + " size=" + mWidth + "x" + mHeight);

        mRequiredNumFrames = requiredNumFrames;
        mHandler = new Handler(mWorkerThread.getLooper());

        mImageReader = ImageReader.newInstance(mWidth, mHeight, HardwareBuffer.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT | HardwareBuffer.USAGE_CPU_READ_OFTEN
                        | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
        mImageReader.setOnImageAvailableListener(mOnImageAvailable, mHandler);
    }

    public Surface getSurface() {
        return mImageReader.getSurface();
    }

    /**
     * Shuts down processing pipeline, and returns current pass/fail counts.
     *
     * Wait for pipeline to flush before calling this method. If not, frames that are still in
     * flight may be lost.
     */
    public void finish(CapturedActivity.TestResult testResult) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        // Post the imageReader close on the same thread it's processing data to avoid shutting down
        // while still in the middle of processing an image.
        mHandler.post(() -> {
            testResult.failFrames = mResultFailureFrames;
            testResult.passFrames = mResultSuccessFrames;

            for (int i = 0; i < mFirstFailures.size(); i++) {
                testResult.failures.put(mFirstFailures.keyAt(i), mFirstFailures.valueAt(i));
            }
            mImageReader.close();
            mImageReader = null;
            mWorkerThread.quitSafely();
            countDownLatch.countDown();
        });

        try {
            assertTrue("Failed to wait for results",
                    countDownLatch.await(5L * HW_TIMEOUT_MULTIPLIER, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
        }
    }

    public boolean waitForAllFrames(long timeoutMs) {
        try {
            return mRequiredNumFramesDrawnLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }
}
