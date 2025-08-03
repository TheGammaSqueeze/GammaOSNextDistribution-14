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

package android.view.cts.util;

import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.view.Surface;
import android.view.SurfaceControl;

public class ASurfaceControlTestUtils {
    static {
        System.loadLibrary("ctssurfacecontrol_jni");
    }

    public interface TransactionCompleteListener {
        void onTransactionComplete(long latchTime, long presentTime);
    }

    public static long createSurfaceTransaction() {
        long surfaceTransaction = nSurfaceTransaction_create();
        assertTrue("failed to create surface transaction", surfaceTransaction != 0);
        return surfaceTransaction;
    }

    public static void reparent(long surfaceControl, long newParentSurfaceControl) {
        long surfaceTransaction = createSurfaceTransaction();
        nSurfaceTransaction_reparent(surfaceControl, newParentSurfaceControl, surfaceTransaction);
        applyAndDeleteSurfaceTransaction(surfaceTransaction);
    }

    public static void applyAndDeleteSurfaceTransaction(long surfaceTransaction) {
        nSurfaceTransaction_apply(surfaceTransaction);
        nSurfaceTransaction_delete(surfaceTransaction);
    }

    public static void setVisibility(long surfaceControl, boolean visible) {
        long surfaceTransaction = createSurfaceTransaction();
        nSurfaceTransaction_setVisibility(surfaceControl, surfaceTransaction, visible);
        applyAndDeleteSurfaceTransaction(surfaceTransaction);
    }

    public static void setBufferOpaque(long surfaceControl, boolean opaque) {
        long surfaceTransaction = createSurfaceTransaction();
        nSurfaceTransaction_setBufferOpaque(surfaceControl, surfaceTransaction, opaque);
        applyAndDeleteSurfaceTransaction(surfaceTransaction);
    }

    public static void setGeometry(long surfaceControl, int srcLeft, int srcTop, int srcRight,
            int srcBottom, int dstLeft, int dstTop, int dstRight, int dstBottom,
            int transform) {
        long surfaceTransaction = createSurfaceTransaction();
        nSurfaceTransaction_setGeometry(surfaceControl, surfaceTransaction, srcLeft, srcTop,
                srcRight, srcBottom,
                dstLeft, dstTop, dstRight, dstBottom, transform);
        applyAndDeleteSurfaceTransaction(surfaceTransaction);
    }

    public static void setZOrder(long surfaceControl, int z) {
        long surfaceTransaction = createSurfaceTransaction();
        nSurfaceTransaction_setZOrder(surfaceControl, surfaceTransaction, z);
        applyAndDeleteSurfaceTransaction(surfaceTransaction);
    }

    public static void setBufferAlpha(long surfaceControl, double alpha) {
        long surfaceTransaction = createSurfaceTransaction();
        nSurfaceTransaction_setBufferAlpha(surfaceControl, surfaceTransaction, alpha);
        applyAndDeleteSurfaceTransaction(surfaceTransaction);
    }

    public static void setColor(long surfaceControl, float red, float green, float blue,
            float alpha) {
        long surfaceTransaction = createSurfaceTransaction();
        nSurfaceTransaction_setColor(surfaceControl, surfaceTransaction, red, green, blue, alpha);
        applyAndDeleteSurfaceTransaction(surfaceTransaction);
    }

    public static void setPosition(long surfaceControl, int x, int y) {
        long surfaceTransaction = createSurfaceTransaction();
        nSurfaceTransaction_setPosition(surfaceControl, surfaceTransaction, x, y);
        applyAndDeleteSurfaceTransaction(surfaceTransaction);
    }

    public static void setScale(long surfaceControl, float xScale, float yScale) {
        long surfaceTransaction = createSurfaceTransaction();
        nSurfaceTransaction_setScale(surfaceControl, surfaceTransaction, xScale, yScale);
        applyAndDeleteSurfaceTransaction(surfaceTransaction);
    }

    public static void setBufferTransform(long surfaceControl, int bufferTransform) {
        long surfaceTransaction = createSurfaceTransaction();
        nSurfaceTransaction_setBufferTransform(surfaceControl, surfaceTransaction,
                bufferTransform);
        applyAndDeleteSurfaceTransaction(surfaceTransaction);
    }

    public static void setCrop(long surfaceControl, Rect crop) {
        long surfaceTransaction = createSurfaceTransaction();
        nSurfaceTransaction_setCrop(surfaceControl, surfaceTransaction, crop.left, crop.top,
                crop.right, crop.bottom);
        applyAndDeleteSurfaceTransaction(surfaceTransaction);
    }

    public static void setExtendedRangeBrightness(long surfaceControl, float currentRatio,
            float desiredRatio) {
        long surfaceTransaction = createSurfaceTransaction();
        nSurfaceTransaction_setExtendedRangeBrightness(surfaceControl, surfaceTransaction,
                currentRatio, desiredRatio);
        applyAndDeleteSurfaceTransaction(surfaceTransaction);
    }

    /** Dispatches setDesiredHdrHeadroom to the NDK */
    public static void setDesiredHdrHeadroom(long surfaceControl, float desiredRatio) {
        long surfaceTransaction = createSurfaceTransaction();
        nSurfaceTransaction_setDesiredHdrHeadroom(surfaceControl, surfaceTransaction,
                desiredRatio);
        applyAndDeleteSurfaceTransaction(surfaceTransaction);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Native function prototypes
    ///////////////////////////////////////////////////////////////////////////

    public static native long nSurfaceTransaction_create();
    public static native void nSurfaceTransaction_delete(long surfaceTransaction);
    public static native long nSurfaceTransaction_fromJava(
            SurfaceControl.Transaction transaction);
    public static native void nSurfaceTransaction_apply(long surfaceTransaction);
    public static native long nSurfaceControl_createFromWindow(Surface surface);
    public static native long nSurfaceControl_create(long surfaceControl);
    public static native void nSurfaceControl_acquire(long surfaceControl);
    public static native void nSurfaceControl_release(long surfaceControl);
    public static native long nSurfaceControl_fromJava(
            SurfaceControl surfaceControl);
    public static native long nSurfaceTransaction_setSolidBuffer(
            long surfaceControl, long surfaceTransaction, int width, int height, int color);
    public static native void nSurfaceTransaction_setBuffer(long surfaceControl,
            long surfaceTransaction, long buffer);
    public static native long nSurfaceTransaction_setQuadrantBuffer(long surfaceControl,
            long surfaceTransaction, int width, int height, int colorTopLeft, int colorTopRight,
            int colorBottomRight, int colorBottomLeft);
    public static native void nSurfaceTransaction_releaseBuffer(long buffer);
    public static native void nSurfaceTransaction_setVisibility(
            long surfaceControl, long surfaceTransaction, boolean show);
    public static native void nSurfaceTransaction_setBufferOpaque(
            long surfaceControl, long surfaceTransaction, boolean opaque);
    public static native void nSurfaceTransaction_setGeometry(
            long surfaceControl, long surfaceTransaction, int srcRight, int srcTop, int srcLeft,
            int srcBottom, int dstRight, int dstTop, int dstLeft, int dstBottom, int transform);
    public static native void nSurfaceTransaction_setCrop(long surfaceControl,
            long surfaceTransaction, int left, int top, int right, int bottom);
    public static native void nSurfaceTransaction_setPosition(long surfaceControl,
            long surfaceTransaction, int left, int top);
    public static native void nSurfaceTransaction_setBufferTransform(
            long surfaceControl, long surfaceTransaction, int transform);
    public static native void nSurfaceTransaction_setScale(long surfaceControl,
            long surfaceTransaction, float xScale, float yScale);
    public static native void nSurfaceTransaction_setDamageRegion(
            long surfaceControl, long surfaceTransaction, int right, int top, int left, int bottom);
    public static native void nSurfaceTransaction_setZOrder(
            long surfaceControl, long surfaceTransaction, int z);
    public static native long nSurfaceTransaction_setDesiredPresentTime(long surfaceTransaction,
            long desiredPresentTimeOffset);
    public static native void nSurfaceTransaction_setBufferAlpha(long surfaceControl,
            long surfaceTransaction, double alpha);
    public static native void nSurfaceTransaction_reparent(long surfaceControl,
            long newParentSurfaceControl, long surfaceTransaction);
    public static native void nSurfaceTransaction_setColor(long surfaceControl,
            long surfaceTransaction, float r, float g, float b, float alpha);
    public static native void nSurfaceTransaction_setEnableBackPressure(long surfaceControl,
            long surfaceTransaction, boolean enableBackPressure);
    public static native void nSurfaceTransaction_setOnCompleteCallback(long surfaceTransaction,
            boolean waitForFence, TransactionCompleteListener listener);
    public static native void nSurfaceTransaction_setOnCommitCallback(long surfaceTransaction,
            TransactionCompleteListener listener);
    public static native void nSurfaceTransaction_setOnCompleteCallbackWithoutContext(
            long surfaceTransaction, boolean waitForFence, TransactionCompleteListener listener);
    public static native void nSurfaceTransaction_setOnCommitCallbackWithoutContext(
            long surfaceTransaction, TransactionCompleteListener listener);
    public static native void nSurfaceTransaction_setFrameTimeline(long surfaceTransaction,
            long vsyncId);
    public static native void nSurfaceTransaction_setExtendedRangeBrightness(
            long surfaceControl, long surfaceTransaction, float currentRatio, float desiredRatio);
    /** Dispatches to the NDK */
    public static native void nSurfaceTransaction_setDesiredHdrHeadroom(
            long surfaceControl, long surfaceTransaction, float desiredRatio);
    public static native void nSurfaceTransaction_setDataSpace(
            long surfaceControl, long surfaceTransaction, int dataspace);

    public static native HardwareBuffer getSolidBuffer(int width, int height, int color);
    public static native HardwareBuffer getQuadrantBuffer(int width, int height,
            int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft);
    public static native long getBufferId(HardwareBuffer buffer);
}
