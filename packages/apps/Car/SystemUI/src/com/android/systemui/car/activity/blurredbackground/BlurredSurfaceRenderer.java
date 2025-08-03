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

package com.android.systemui.car.activity.blurredbackground;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.RemoteException;
import android.util.Slog;
import android.view.Surface;
import android.view.WindowManagerGlobal;
import android.window.ScreenCapture;
import android.window.ScreenCapture.CaptureArgs;
import android.window.ScreenCapture.ScreenshotHardwareBuffer;
import android.window.ScreenCapture.SynchronousScreenCaptureListener;

import com.android.systemui.R;

import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The renderer class for the {@link GLSurfaceView} of the
 * {@link com.android.systemui.car.activity.ActivityBlockingActivity}
 */
public class BlurredSurfaceRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = BlurredSurfaceRenderer.class.getSimpleName();
    private static final int NUM_INDICES_TO_RENDER = 4;

    private final String mVertexShader;
    private final String mHorizontalBlurShader;
    private final String mVerticalBlurShader;
    private final Rect mWindowRect;

    private BlurTextureProgram mProgram;
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;

    private int mScreenshotTextureId;
    private final IntBuffer mScreenshotTextureBuffer = IntBuffer.allocate(1);
    private final float[] mTexMatrix = new float[16];

    private final boolean mShadersLoadedSuccessfully;
    private final int mDisplayId;
    private boolean mIsScreenShotCaptured = false;

    /**
     * Constructs a new {@link BlurredSurfaceRenderer} and loads the shaders needed for rendering a
     * blurred texture
     *
     * @param windowRect Rect that represents the application window
     */
    public BlurredSurfaceRenderer(Context context, Rect windowRect, int displayId) {
        mDisplayId = displayId;

        mVertexShader = GLHelper.getShaderFromRaw(context, R.raw.vertex_shader);
        mHorizontalBlurShader = GLHelper.getShaderFromRaw(context,
                R.raw.horizontal_blur_fragment_shader);
        mVerticalBlurShader = GLHelper.getShaderFromRaw(context,
                R.raw.vertical_blur_fragment_shader);

        mShadersLoadedSuccessfully = mVertexShader != null
                && mHorizontalBlurShader != null
                && mVerticalBlurShader != null;

        mWindowRect = windowRect;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        mScreenshotTextureId = GLHelper.createAndBindTextureObject(mScreenshotTextureBuffer,
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES);

        mSurfaceTexture = new SurfaceTexture(mScreenshotTextureId);
        mSurface = new Surface(mSurfaceTexture);
        mIsScreenShotCaptured = captureScreenshot();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (shouldDrawFrame()) {
            mProgram = new BlurTextureProgram(
                    mScreenshotTextureBuffer,
                    mTexMatrix,
                    mVertexShader,
                    mHorizontalBlurShader,
                    mVerticalBlurShader,
                    mWindowRect
            );
            mProgram.render();
        } else {
            logWillNotRenderBlurredMsg();

            // If we determine we shouldn't render a blurred texture, we
            // will default to rendering a transparent GLSurfaceView so that
            // the ActivityBlockingActivity appears translucent
            renderTransparent();
        }
    }

    private void renderTransparent() {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, /*first index to render */ 0,
                NUM_INDICES_TO_RENDER);
    }

    /**
     * Called when the ActivityBlockingActivity pauses cleans up the OpenGL program
     */
    public void onPause() {
        if (mProgram != null) {
            mProgram.cleanupResources();
        }
        deleteScreenshotTexture();
    }

    private boolean captureScreenshot() {
        boolean isScreenshotCaptured = false;

        try {
            final CaptureArgs captureArgs = new CaptureArgs.Builder<>()
                    .setSourceCrop(mWindowRect)
                    .build();
            SynchronousScreenCaptureListener syncScreenCapture =
                    ScreenCapture.createSyncCaptureListener();
            try {
                WindowManagerGlobal.getWindowManagerService().captureDisplay(mDisplayId,
                        captureArgs, syncScreenCapture);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to request screencapture for display");
                e.rethrowAsRuntimeException();
            }
            final ScreenshotHardwareBuffer screenshotHardwareBuffer =
                    syncScreenCapture.getBuffer();

            mSurface.attachAndQueueBufferWithColorSpace(
                    screenshotHardwareBuffer.getHardwareBuffer(),
                    screenshotHardwareBuffer.getColorSpace());
            mSurfaceTexture.updateTexImage();
            mSurfaceTexture.getTransformMatrix(mTexMatrix);
            isScreenshotCaptured = true;
        } finally {
            mSurface.release();
            mSurfaceTexture.release();
        }

        return isScreenshotCaptured;
    }

    private void deleteScreenshotTexture() {
        GLES30.glDeleteTextures(mScreenshotTextureBuffer.capacity(), mScreenshotTextureBuffer);
        GLHelper.checkGlErrors("glDeleteTextures");

        mIsScreenShotCaptured = false;
    }

    private void logWillNotRenderBlurredMsg() {
        if (!mIsScreenShotCaptured) {
            Slog.e(TAG, "Screenshot was not captured. Will not render blurred surface");
        }
        if (!mShadersLoadedSuccessfully) {
            Slog.e(TAG, "Shaders were not loaded successfully. Will not render blurred surface");
        }
    }

    private boolean shouldDrawFrame() {
        return mIsScreenShotCaptured
                && mShadersLoadedSuccessfully;
    }
}

