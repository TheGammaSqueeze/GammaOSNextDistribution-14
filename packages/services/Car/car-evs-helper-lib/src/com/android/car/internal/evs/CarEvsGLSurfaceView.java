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

package com.android.car.internal.evs;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.evs.CarEvsBufferDescriptor;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

import com.android.internal.util.Preconditions;
import com.android.car.internal.evs.GLES20CarEvsBufferRenderer;

import java.util.ArrayList;

/**
 * GPU-backed SurfaceView to render a hardware buffer described by {@link CarEvsBufferDescriptor}.
 */
public final class CarEvsGLSurfaceView extends GLSurfaceView {
    private static final String TAG = CarEvsGLSurfaceView.class.getSimpleName();
    private static final int DEFAULT_IN_PLANE_ROTATION_ANGLE = 0;
    private static final float DEFAULT_1X1_POSITION[][] = {
            {
                -1.0f,  1.0f, 0.0f,
                 1.0f,  1.0f, 0.0f,
                -1.0f, -1.0f, 0.0f,
                 1.0f, -1.0f, 0.0f,
            },
    };
    private final GLES20CarEvsBufferRenderer mRenderer;

    /** An interface to pull and return {@code CarEvsBufferDescriptor} object to render. */
    public interface BufferCallback {
        /**
         * Requests a new {@link CarEvsBufferDescriptor} to draw.
         *
         * This method may return a {@code null} if no new frame has been prepared since the last
         * frame was drawn.
         *
         * @return {@link CarEvsBufferDescriptor} object to process.
         */
        @Nullable CarEvsBufferDescriptor onBufferRequested();

        /**
         * Notifies that the buffer is processed.
         *
         * @param buffer {@link CarEvsBufferDescriptor} object we are done with.
         */
        void onBufferProcessed(@NonNull CarEvsBufferDescriptor buffer);
    }

    private CarEvsGLSurfaceView(Context context, ArrayList<BufferCallback> callbacks,
            int angleInDegree, float[][] positions) {
        super(context);
        setEGLContextClientVersion(2);

        mRenderer = new GLES20CarEvsBufferRenderer(callbacks, angleInDegree, positions);
        setRenderer(mRenderer);

        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    /**
     * Returns all buffers held by the renderer.
     */
    public void reset() {
        mRenderer.clearBuffer();
    }

    /**
     * Creates a {@link CarEvsGLSurfaceView} object with the default in-plane rotation angle.
     *
     * @param context The Context this view is running in, through which it can access resources,
     *                etc.
     * @param callbacks An array of {@link CarEvsGLSurfaceView.BufferCallback} objects.
     *
     */
    public static CarEvsGLSurfaceView create(Context context,
            ArrayList<BufferCallback> callbacks) {
        return create(context, callbacks, DEFAULT_IN_PLANE_ROTATION_ANGLE, DEFAULT_1X1_POSITION);
    }

    /**
     * Creates a {@link CarEvsGLSurfaceView} object with a given in-plane rotation angle in degree.
     *
     * @param context The Context this view is running in, through which it can access resources,
     *                etc.
     * @param callbacks An array of {@link CarEvsGLSurfaceView.BufferCallback} objects.
     * @param angleInDegree In-plane counter-clockwise rotation angle in degree.
     *
     */
    public static CarEvsGLSurfaceView create(Context context, ArrayList<BufferCallback> callbacks,
            int angleInDegree) {
        return create(context, callbacks, angleInDegree, DEFAULT_1X1_POSITION);
    }

    /**
     * Creates a {@link CarEvsGLSurfaceView} object with the default in-plane rotation angle and a
     * gridview with given rows and columns.
     *
     * @param context The Context this view is running in, through which it can access resources,
     *                etc.
     * @param callbacks An array of {@link CarEvsGLSurfaceView.BufferCallback} objects.
     * @param positions Matrices that define an area where each buffer will be rendered.
     *
     */
    public static CarEvsGLSurfaceView create(Context context, ArrayList<BufferCallback> callbacks,
            float positions[][]) {
        return create(context, callbacks, DEFAULT_IN_PLANE_ROTATION_ANGLE, positions);
    }

    /**
     * Creates a {@link CarEvsGLSurfaceView} object with a given in-plane rotation angle in degree
     * and a gridview with given rows and columns.
     *
     * @param context The Context this view is running in, through which it can access resources,
     *                etc.
     * @param callbacks An array of {@link CarEvsGLSurfaceView.BufferCallback} objects.
     * @param angleInDegree In-plane counter-clockwise rotation angle in degree.
     * @param positions Matrices that define an area where each buffer will be rendered.
     *
     */
    public static CarEvsGLSurfaceView create(Context context, ArrayList<BufferCallback> callbacks,
            int angleInDegree, float positions[][]) {

        Preconditions.checkArgument(context != null, "Context cannot be null.");
        Preconditions.checkArgument(callbacks != null, "BufferCallback cannot be null.");
        Preconditions.checkArgument(callbacks.size() > 0,
                "At least one BufferCallback object must exist.");
        Preconditions.checkArgument(callbacks.size() <= positions.length,
                "At least " + callbacks.size() + " positions are needed.");

        return new CarEvsGLSurfaceView(context, callbacks, angleInDegree, positions);
    }
}
