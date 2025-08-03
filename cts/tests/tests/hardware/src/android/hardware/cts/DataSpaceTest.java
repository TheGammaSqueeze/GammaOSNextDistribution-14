/*
 * Copyright 2021 The Android Open Source Project
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
package android.hardware.cts;

import static android.opengl.GLES20.glDeleteTextures;
import static android.opengl.GLES20.glGenTextures;

import static org.junit.Assert.assertEquals;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.DataSpace;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.view.Surface;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@ApiTest(apis = {"android.hardware.DataSpace#NamedDataSpace"})
@RunWith(AndroidJUnit4.class)
@SmallTest
public class DataSpaceTest {
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    private int[] mTex;
    private ImageWriter mWriter;
    private ImageReader mReader;

    private EGLDisplay mEglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLConfig mEglConfig = null;
    private EGLSurface mEglSurface = EGL14.EGL_NO_SURFACE;
    private EGLContext mEglContext = EGL14.EGL_NO_CONTEXT;

    @UiThreadTest
    @Before
    public void setUp() throws Throwable {
        mEglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("no EGL display");
        }
        int[] major = new int[1];
        int[] minor = new int[1];
        if (!EGL14.eglInitialize(mEglDisplay, major, 0, minor, 0)) {
            throw new RuntimeException("error in eglInitialize");
        }

        // If we could rely on having EGL_KHR_surfaceless_context and EGL_KHR_context_no_config, we
        // wouldn't have to create a config or pbuffer at all.

        int[] numConfigs = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        if (!EGL14.eglChooseConfig(mEglDisplay, new int[] {
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE}, 0, configs, 0, 1, numConfigs, 0)) {
            throw new RuntimeException("eglChooseConfig failed");
        }
        mEglConfig = configs[0];

        mEglSurface = EGL14.eglCreatePbufferSurface(mEglDisplay, mEglConfig,
            new int[] {EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
        if (mEglSurface == EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException("eglCreatePbufferSurface failed");
        }

        mEglContext = EGL14.eglCreateContext(mEglDisplay, mEglConfig, EGL14.EGL_NO_CONTEXT,
            new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
        if (mEglContext == EGL14.EGL_NO_CONTEXT) {
            throw new RuntimeException("eglCreateContext failed");
        }

        if (!EGL14.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    @After
    public void tearDown() throws Throwable {
        if (mReader != null) {
            mReader.close();
            mReader = null;
        }
        if (mWriter != null) {
            mWriter.close();
            mWriter = null;
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
            glDeleteTextures(1, mTex, 0);
        }
        if (mEglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglDestroyContext(mEglDisplay, mEglContext);
            EGL14.eglDestroySurface(mEglDisplay, mEglSurface);
            EGL14.eglTerminate(mEglDisplay);
        }
        mEglDisplay = EGL14.EGL_NO_DISPLAY;
        mEglContext = EGL14.EGL_NO_CONTEXT;
        mEglSurface = EGL14.EGL_NO_SURFACE;
    }

    @UiThreadTest
    @Test
    public void getDataSpace() {
        mTex = new int[1];
        glGenTextures(1, mTex, 0);

        // create a surfaceTexture attached to mTex[0]
        mSurfaceTexture = new SurfaceTexture(mTex[0]);
        mSurfaceTexture.setDefaultBufferSize(16, 16);

        mSurface = new Surface(mSurfaceTexture);
        mWriter = new ImageWriter.Builder(mSurface).build();

        int dataSpace = DataSpace.pack(DataSpace.STANDARD_BT709,
                                        DataSpace.TRANSFER_SMPTE_170M,
                                        DataSpace.RANGE_LIMITED);
        Image inputImage = null;
        try {
            inputImage = mWriter.dequeueInputImage();
            inputImage.setDataSpace(dataSpace);
            assertEquals(dataSpace, inputImage.getDataSpace());

            mWriter.queueInputImage(inputImage);

            mSurfaceTexture.updateTexImage();
            int outDataSpace = mSurfaceTexture.getDataSpace();

            assertEquals(dataSpace, outDataSpace);
            assertEquals(DataSpace.STANDARD_BT709, DataSpace.getStandard(outDataSpace));
            assertEquals(DataSpace.TRANSFER_SMPTE_170M, DataSpace.getTransfer(outDataSpace));
            assertEquals(DataSpace.RANGE_LIMITED, DataSpace.getRange(outDataSpace));
        } finally {
            if (inputImage != null) {
                inputImage.close();
                inputImage = null;
            }
        }
    }

    @UiThreadTest
    @Test
    public void getDataSpaceWithoutSetDataSpace() {
        mTex = new int[1];
        glGenTextures(1, mTex, 0);

        // create a surfaceTexture attached to mTex[0]
        mSurfaceTexture = new SurfaceTexture(mTex[0]);
        mSurfaceTexture.setDefaultBufferSize(16, 16);

        mSurface = new Surface(mSurfaceTexture);
        mWriter = ImageWriter.newInstance(mSurface, 1);

        Image inputImage = null;
        try {
            inputImage = mWriter.dequeueInputImage();
            mWriter.queueInputImage(inputImage);

            mSurfaceTexture.updateTexImage();

            assertEquals(DataSpace.DATASPACE_UNKNOWN, mSurfaceTexture.getDataSpace());
        } finally {
            if (inputImage != null) {
                inputImage.close();
                inputImage = null;
            }
        }
    }

    @ApiTest(apis = {"android.hardware.DataSpace#DATASPACE_JFIF"})
    @UiThreadTest
    @Test
    public void getDataSpaceWithFormatYV12() {
        mTex = new int[1];
        glGenTextures(1, mTex, 0);

        // create a surfaceTexture attached to mTex[0]
        mSurfaceTexture = new SurfaceTexture(mTex[0]);
        mSurfaceTexture.setDefaultBufferSize(16, 16);

        mSurface = new Surface(mSurfaceTexture);
        mWriter = new ImageWriter.Builder(mSurface)
                .setImageFormat(ImageFormat.YV12)
                .build();

        Image inputImage = null;
        try {
            inputImage = mWriter.dequeueInputImage();
            mWriter.queueInputImage(inputImage);

            mSurfaceTexture.updateTexImage();

            // test default dataspace value of ImageFormat.YV12 format.
            assertEquals(DataSpace.DATASPACE_JFIF, mSurfaceTexture.getDataSpace());
        } finally {
            if (inputImage != null) {
                inputImage.close();
                inputImage = null;
            }
        }
    }

    @UiThreadTest
    @Test
    public void getDataSpaceFromImageReaderNextImage() {
        mReader = ImageReader.newInstance(100, 100, ImageFormat.YUV_420_888, 1);
        mWriter = ImageWriter.newInstance(mReader.getSurface(), 1);

        int dataSpace = DataSpace.pack(DataSpace.STANDARD_BT601_625,
                                        DataSpace.TRANSFER_SMPTE_170M,
                                        DataSpace.RANGE_FULL);

        Image outputImage = null;
        Image nextImage = null;
        try {
            outputImage = mWriter.dequeueInputImage();
            outputImage.setDataSpace(dataSpace);
            assertEquals(dataSpace, outputImage.getDataSpace());

            mWriter.queueInputImage(outputImage);

            nextImage = mReader.acquireLatestImage();
            assertEquals(dataSpace, nextImage.getDataSpace());
        } finally {
            if (outputImage != null) {
                outputImage.close();
                outputImage = null;
            }
            if (nextImage != null) {
                nextImage.close();
                nextImage = null;
            }
        }
    }
}
