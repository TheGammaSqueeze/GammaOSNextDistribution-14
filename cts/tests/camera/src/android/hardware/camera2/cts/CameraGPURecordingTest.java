/*
 * Copyright 2022 The Android Open Source Project
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

package android.hardware.camera2.cts;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestCase;
import android.media.CamcorderProfile;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNotSame;
import static junit.framework.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

/**
 * Basic test for applying graphics manipulation to a captured video, using openGL for graphics
 * rendering and MediaCodec for video encoding/decoding.
 */

@RunWith(Parameterized.class)
public class CameraGPURecordingTest extends Camera2AndroidTestCase {
    private static final String TAG = "CameraGPURecordingTest";
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final int IFRAME_INTERVAL = 5;
    private static final long DURATION_SEC = 8;
    private static final String SWAPPED_FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n"
                    + "precision mediump float;\n"
                    + "varying vec2 vTextureCoord;\n"
                    + "uniform samplerExternalOES sTexture;\n"
                    + "void main() {\n"
                    + "  gl_FragColor = texture2D(sTexture, vTextureCoord).gbra;\n"
                    + "}\n";

    private MediaCodec mEncoder;
    private CodecInputSurface mInputSurface;
    private MediaMuxer mMuxer;
    private int mTrackIndex;
    private boolean mMuxerStarted;
    private SurfaceTextureHolder mSurfaceTextureHolder;
    private MediaCodec.BufferInfo mBufferInfo;

    /*
     * Tests the basic camera -> GPU -> encoder path. Applies a fragment shader every other frame to
     * perform a color tweak.
     */
    @Test
    public void testCameraGpuEncoderPath() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            if (!mAllStaticInfo.get(id).isCapabilitySupported(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)) {
                Log.i(TAG, "Camera " + id + " is not BACKWARD_COMPATIBLE and does not support "
                        + "TEMPLATE_RECORD for createCaptureRequest");
                continue;
            }
            if (mAllStaticInfo.get(id).isExternalCamera()) {
                Log.i(TAG, "Camera " + id + " does not support CamcorderProfile, skipping");
                continue;
            }
            CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
            // Downgrade if 720p isn't available.
            if (profile == null) {
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
            }
            // Skip the test if neither of 720 or 480 camcorder profile is available.
            if (profile == null) {
                Log.i(TAG, "Camcorder profile not available for camera " + id);
                continue;
            }
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);
                prepareEncoder(profile);
                mInputSurface.makeCurrent();

                mSurfaceTextureHolder = new SurfaceTextureHolder();
                SurfaceTexture surfaceTexture = mSurfaceTextureHolder.getSurfaceTexture();
                CaptureRequest.Builder previewRequest =
                        createSessionAndCaptureRequest(surfaceTexture);
                CameraTestUtils.SimpleCaptureCallback previewListener =
                        new CameraTestUtils.SimpleCaptureCallback();

                mCameraSession.setRepeatingRequest(previewRequest.build(),
                        previewListener, mHandler);

                long startWhen = System.nanoTime();
                long desiredEnd = startWhen + DURATION_SEC * 1000000000L;
                int frameCount = 0;

                while (System.nanoTime() < desiredEnd) {
                    // Feed any pending encoder output into the muxer.
                    drainEncoder(/*endOfStream=*/ false);

                    String fragmentShader = null;
                    if ((frameCount % 2) != 0) {
                        fragmentShader = SWAPPED_FRAGMENT_SHADER;
                    }
                    mSurfaceTextureHolder.changeFragmentShader(fragmentShader);

                    // Acquire a new frame of input, and render it to the Surface.  If we had a
                    // GLSurfaceView we could switch EGL contexts and call drawImage() a second
                    // time to render it on screen.  The texture can be shared between contexts by
                    // passing the GLSurfaceView's EGLContext as eglCreateContext()'s share_context
                    // argument.
                    mSurfaceTextureHolder.awaitNewImage();
                    mSurfaceTextureHolder.drawImage();

                    frameCount++;

                    // Set the presentation time stamp from the SurfaceTexture's time stamp.  This
                    // will be used by MediaMuxer to set the PTS in the video.
                    Log.v(TAG, "present: "
                            + ((surfaceTexture.getTimestamp() - startWhen) / 1000000.0)
                            + "ms");
                    mInputSurface.setPresentationTime(surfaceTexture.getTimestamp());

                    // Submit it to the encoder.  The eglSwapBuffers call will block if the input
                    // is full, which would be bad if it stayed full until we dequeued an output
                    // buffer (which we can't do, since we're stuck here).  So long as we fully
                    // drain the encoder before supplying additional input, the system guarantees
                    // that we can supply another frame without blocking.
                    Log.v(TAG, "sending frame to encoder");
                    mInputSurface.swapBuffers();
                }

                mCameraSession.stopRepeating();
                previewListener.drain();
                // send end-of-stream to encoder, and drain remaining output
                drainEncoder(true);
            } finally {
                closeDevice(id);
                releaseEncoder();
                releaseSurfaceTexture();
            }
        }
    }

    private void prepareEncoder(CamcorderProfile profile) throws Exception {
        mBufferInfo = new MediaCodec.BufferInfo();
        assertNotNull("Camcorder profile should not be null", profile);
        int width = profile.videoFrameWidth;
        int height = profile.videoFrameHeight;
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, profile.videoBitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, profile.videoFrameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        Log.v(TAG, "format: " + format);

        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = new CodecInputSurface(mEncoder.createInputSurface());
        mEncoder.start();

        File filesDir = mContext.getPackageManager().isInstantApp()
                ? mContext.getFilesDir()
                : mContext.getExternalFilesDir(null);
        long timestamp = System.currentTimeMillis();
        String outputPath =
                new File(filesDir.getPath(), "test-" + timestamp + "." + width + "x" + height
                        + ".mp4").toString();
        Log.i(TAG, "Output file is " + outputPath);
        try {
            mMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }

        mTrackIndex = -1;
        mMuxerStarted = false;
    }

    private CaptureRequest.Builder createSessionAndCaptureRequest(SurfaceTexture preview)
            throws Exception {
        Surface previewSurface = new Surface(preview);
        preview.setDefaultBufferSize(640, 480);

        ArrayList<Surface> sessionOutputs = new ArrayList<>();
        sessionOutputs.add(previewSurface);

        createSession(sessionOutputs);

        CaptureRequest.Builder previewRequest =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

        previewRequest.addTarget(previewSurface);

        return previewRequest;
    }

    private void releaseEncoder() {
        Log.v(TAG, "releasing encoder objects");
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }
        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }

    private void releaseSurfaceTexture() {
        if (mSurfaceTextureHolder != null) {
            mSurfaceTextureHolder.release();
            mSurfaceTextureHolder = null;
        }
    }

    private void drainEncoder(boolean endOfStream) {
        Log.v(TAG, "drainEncoder(" + endOfStream + ")");

        if (endOfStream) {
            Log.v(TAG, "sending EOS to encoder");
            mEncoder.signalEndOfInputStream();
        }

        while (true) {
            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, /*timeoutUs=*/ 1000);
            assertTrue(String.format("Unexpected result from encoder.dequeueOutputBuffer: %d",
                    encoderStatus),
                    encoderStatus >= 0
                        || (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER)
                        || (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED));
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    break;
                } else {
                    Log.v(TAG, "no output available, spinning to await EOS");
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                assertFalse("Format changed twice", mMuxerStarted);
                MediaFormat newFormat = mEncoder.getOutputFormat();
                Log.d(TAG, "encoder output format changed: " + newFormat);

                // now that we have the Magic Goodies, start the muxer
                mTrackIndex = mMuxer.addTrack(newFormat);
                mMuxer.start();
                mMuxerStarted = true;
            } else {
                ByteBuffer encodedData = mEncoder.getOutputBuffer(encoderStatus);
                assertNotNull(String.format("EncoderOutputBuffer %d was null", encoderStatus),
                        encodedData);

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    Log.v(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
                    assertTrue("Muxer hasn't started", mMuxerStarted);

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);

                    mMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                    Log.v(TAG, "sent " + mBufferInfo.size + " bytes to muxer");
                }

                mEncoder.releaseOutputBuffer(encoderStatus, false);

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        Log.w(TAG, "reached end of stream unexpectedly");
                    } else {
                        Log.v(TAG, "end of stream reached");
                    }
                    break;
                }
            }
        }
    }

    /**
     * Holds state associated with a Surface used for MediaCodec encoder input.
     *
     * <p>The constructor takes a Surface obtained from MediaCodec.createInputSurface(), and uses
     * that to create an EGL window surface. Calls to eglSwapBuffers() cause a frame of data to be
     * sent to the video encoder.
     *
     * <p>This object owns the Surface -- releasing this will release the Surface too.
     */
    private static class CodecInputSurface {
        private static final int EGL_RECORDABLE_ANDROID = 0x3142;

        private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
        private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
        private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;
        private Surface mSurface;

        /** Creates a CodecInputSurface from a Surface. */
        CodecInputSurface(Surface surface) {
            assertNotNull("CodecInputSurface is NULL", surface);
            mSurface = surface;
            eglSetup();
        }

        /** Prepares EGL. We want a GLES 2.0 context and a surface that supports recording. */
        private void eglSetup() {
            mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            assertNotSame("Unable to get EGL14 display", mEGLDisplay, EGL14.EGL_NO_DISPLAY);

            int[] version = new int[2];
            assertTrue("Unable to initialize EGL14", EGL14.eglInitialize(mEGLDisplay, version, 0,
                    version, 1));

            // Configure EGL for recording and OpenGL ES 2.0.
            int[] attribList = {
                EGL14.EGL_RED_SIZE,
                8,
                EGL14.EGL_GREEN_SIZE,
                8,
                EGL14.EGL_BLUE_SIZE,
                8,
                EGL14.EGL_ALPHA_SIZE,
                8,
                EGL14.EGL_RENDERABLE_TYPE,
                EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID,
                1,
                EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            EGL14.eglChooseConfig(
                    mEGLDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0);
            checkEglError("eglCreateContext RGB888+recordable ES2");

            // Configure context for OpenGL ES 2.0.
            int[] attrib_list = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
            mEGLContext =
                    EGL14.eglCreateContext(
                            mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT, attrib_list, 0);
            checkEglError("eglCreateContext");

            // Create a window surface, and attach it to the Surface we received.
            int[] surfaceAttribs = {EGL14.EGL_NONE};
            mEGLSurface =
                    EGL14.eglCreateWindowSurface(
                            mEGLDisplay, configs[0], mSurface, surfaceAttribs, 0);
            checkEglError("eglCreateWindowSurface");
        }

        /**
         * Discards all resources held by this class, notably the EGL context. Also releases the
         * Surface that was passed to our constructor.
         */
        public void release() {
            if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                        mEGLDisplay,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT);
                EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
                EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
                EGL14.eglReleaseThread();
                EGL14.eglTerminate(mEGLDisplay);
            }
            mSurface.release();

            mEGLDisplay = EGL14.EGL_NO_DISPLAY;
            mEGLContext = EGL14.EGL_NO_CONTEXT;
            mEGLSurface = EGL14.EGL_NO_SURFACE;

            mSurface = null;
        }

        /** Makes our EGL context and surface current. */
        public void makeCurrent() {
            EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);
            checkEglError("eglMakeCurrent");
        }

        /** Calls eglSwapBuffers. Use this to "publish" the current frame. */
        public void swapBuffers() {
            EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
            checkEglError("eglSwapBuffers");
        }

        /** Sends the presentation time stamp to EGL. Time is expressed in nanoseconds. */
        public void setPresentationTime(long nsecs) {
            EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs);
            checkEglError("eglPresentationTimeANDROID");
        }

        /** Checks for EGL errors. */
        private void checkEglError(String msg) {
            int error = EGL14.eglGetError();
            assertEquals(String.format("%s : EGL error: 0x%s", msg, Integer.toHexString(error)),
                    EGL14.EGL_SUCCESS, error);
        }
    }

    /**
     * Manages a SurfaceTexture. Creates SurfaceTexture and TextureRender objects, and provides
     * functions that wait for frames and render them to the current EGL surface.
     *
     * <p>The SurfaceTexture can be passed to Camera.setPreviewTexture() to receive camera output.
     */
    private static class SurfaceTextureHolder implements SurfaceTexture.OnFrameAvailableListener {
        private SurfaceTexture mSurfaceTexture;
        private SurfaceTextureRender mTextureRender;

        private final Object mFrameSyncObject = new Object(); // guards mFrameAvailable
        private volatile boolean mFrameAvailable;

        /** Creates instances of TextureRender and SurfaceTexture. */
        SurfaceTextureHolder() {
            mTextureRender = new SurfaceTextureRender();
            Log.v(TAG, "textureID=" + mTextureRender.getTextureId());
            mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());
            mSurfaceTexture.setOnFrameAvailableListener(this);
        }

        public void release() {
            mTextureRender = null;
            mSurfaceTexture = null;
        }

        /** Returns the SurfaceTexture. */
        public SurfaceTexture getSurfaceTexture() {
            return mSurfaceTexture;
        }

        /** Replaces the fragment shader. */
        public void changeFragmentShader(String fragmentShader) {
            mTextureRender.changeFragmentShader(fragmentShader);
        }

        /**
         * Latches the next buffer into the texture. Must be called from the thread that created the
         * OutputSurface object.
         */
        public void awaitNewImage() {
            synchronized (mFrameSyncObject) {
                try {
                    // Wait for onFrameAvailable() to signal us.  Use a timeout to avoid
                    // stalling the test if it doesn't arrive.
                    // The logic below ensures that even in the case of spurious wakes, instead
                    // of moving forward, we are still waiting up to 2500 ms to make sure we
                    // give enough time for mFrameAvailable to be set to true.
                    long expiry = System.currentTimeMillis() + 2500L;
                    while (!mFrameAvailable && System.currentTimeMillis() < expiry) {
                        // Do not wait for 0 or negative time. 0=indefinite, -ve=undefined(?)
                        mFrameSyncObject.wait(Math.max(expiry - System.currentTimeMillis(), 1));
                    }
                    assertTrue("Camera frame wait timed out", mFrameAvailable);
                } catch (InterruptedException ie) {
                    // shouldn't happen
                    throw new RuntimeException(ie);
                }
                mFrameAvailable = false;
            }

            // Latch the data.
            mTextureRender.checkGlError("before updateTexImage");
            mSurfaceTexture.updateTexImage();
        }

        /** Draws the data from SurfaceTexture onto the current EGL surface. */
        public void drawImage() {
            mTextureRender.drawFrame(mSurfaceTexture);
        }

        @Override
        public void onFrameAvailable(SurfaceTexture st) {
            Log.v(TAG, "new frame available");
            synchronized (mFrameSyncObject) {
                assertFalse("mFrameAvailable already set, frame could be dropped", mFrameAvailable);
                mFrameAvailable = true;
                mFrameSyncObject.notifyAll();
            }
        }
    }

    /** Code for rendering a texture onto a surface using OpenGL ES 2.0. */
    private static class SurfaceTextureRender {
        private static final int FLOAT_SIZE_BYTES = 4;
        private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
        private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
        private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;

        private final FloatBuffer mTriangleVertices;

        private static final String VERTEX_SHADER =
                "uniform mat4 uMVPMatrix;\n"
                        + "uniform mat4 uSTMatrix;\n"
                        + "attribute vec4 aPosition;\n"
                        + "attribute vec4 aTextureCoord;\n"
                        + "varying vec2 vTextureCoord;\n"
                        + "void main() {\n"
                        + "    gl_Position = uMVPMatrix * aPosition;\n"
                        + "    vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n"
                        + "}\n";

        private static final String FRAGMENT_SHADER =
                "#extension GL_OES_EGL_image_external : require\n"
                        + "precision mediump float;\n"
                        + "varying vec2 vTextureCoord;\n"
                        + "uniform samplerExternalOES sTexture;\n"
                        + "void main() {\n"
                        + "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n"
                        + "}\n";

        private final float[] mMVPMatrix = new float[16];
        private final float[] mSTMatrix = new float[16];

        private int mProgram;
        private int mTextureID = -12345;
        private int mMVPMatrixHandle;
        private int mSTMatrixHandle;
        private int mPositionHandle;
        private int mTextureHandle;

        SurfaceTextureRender() {
            final float[] triangleVerticesData = {
                    // X, Y, Z, U, V
                    -1.0f, -1.0f, 0, 0.f, 0.f,
                    1.0f, -1.0f, 0, 1.f, 0.f,
                    -1.0f, 1.0f, 0, 0.f, 1.f,
                    1.0f, 1.0f, 0, 1.f, 1.f,
            };
            mTriangleVertices =
                    ByteBuffer.allocateDirect(triangleVerticesData.length * FLOAT_SIZE_BYTES)
                            .order(ByteOrder.nativeOrder())
                            .asFloatBuffer();
            mTriangleVertices.put(triangleVerticesData).position(0);

            Matrix.setIdentityM(mSTMatrix, 0);
            surfaceCreated();
        }

        public int getTextureId() {
            return mTextureID;
        }

        public void drawFrame(SurfaceTexture st) {
            checkGlError("onDrawFrame start");
            st.getTransformMatrix(mSTMatrix);

            // (optional) clear to green so we can see if we're failing to set pixels
            GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

            GLES20.glUseProgram(mProgram);
            checkGlError("glUseProgram");

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);

            mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
            GLES20.glVertexAttribPointer(
                    mPositionHandle,
                    3,
                    GLES20.GL_FLOAT,
                    false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES,
                    mTriangleVertices);
            checkGlError("glVertexAttribPointer maPosition");
            GLES20.glEnableVertexAttribArray(mPositionHandle);
            checkGlError("glEnableVertexAttribArray maPositionHandle");

            mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
            GLES20.glVertexAttribPointer(
                    mTextureHandle,
                    2,
                    GLES20.GL_FLOAT,
                    false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES,
                    mTriangleVertices);
            checkGlError("glVertexAttribPointer maTextureHandle");
            GLES20.glEnableVertexAttribArray(mTextureHandle);
            checkGlError("glEnableVertexAttribArray maTextureHandle");

            Matrix.setIdentityM(mMVPMatrix, 0);
            GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);
            GLES20.glUniformMatrix4fv(mSTMatrixHandle, 1, false, mSTMatrix, 0);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            checkGlError("glDrawArrays");

            // IMPORTANT: on some devices, if you are sharing the external texture between two
            // contexts, one context may not see updates to the texture unless you un-bind and
            // re-bind it.  If you're not using shared EGL contexts, you don't need to bind
            // texture 0 here.
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        }

        /**
         * Initializes GL state. Call this after the EGL surface has been created and made current.
         */
        public void surfaceCreated() {
            mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            assertTrue("Failed creating program", mProgram != 0);

            mPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
            checkLocation(mPositionHandle, "aPosition");
            mTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
            checkLocation(mTextureHandle, "aTextureCoord");

            mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
            checkLocation(mMVPMatrixHandle, "uMVPMatrix");
            mSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
            checkLocation(mSTMatrixHandle, "uSTMatrix");

            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);

            mTextureID = textures[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
            checkGlError("glBindTexture mTextureID");

            GLES20.glTexParameterf(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_NEAREST);
            GLES20.glTexParameterf(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE);
            checkGlError("glTexParameter");
        }

        /*
         * Replaces the fragment shader. Pass in null to reset to default.
         */
        public void changeFragmentShader(String fragmentShader) {
            if (fragmentShader == null) {
                fragmentShader = FRAGMENT_SHADER;
            }
            GLES20.glDeleteProgram(mProgram);
            mProgram = createProgram(VERTEX_SHADER, fragmentShader);
            assertTrue("Failed creating program", mProgram != 0);
        }

        private int loadShader(int shaderType, String source) {
            int shader = GLES20.glCreateShader(shaderType);
            checkGlError("glCreateShader type=" + shaderType);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader " + shaderType + ":");
                Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
            return shader;
        }

        private int createProgram(String vertexSource, String fragmentSource) {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
            if (vertexShader == 0) {
                return 0;
            }
            int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
            if (pixelShader == 0) {
                return 0;
            }

            int program = GLES20.glCreateProgram();
            if (program == 0) {
                Log.e(TAG, "Could not create program");
                return 0;
            }
            GLES20.glAttachShader(program, vertexShader);
            checkGlError("glAttachShader");
            GLES20.glAttachShader(program, pixelShader);
            checkGlError("glAttachShader");
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program: ");
                Log.e(TAG, GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
            return program;
        }

        public void checkGlError(String op) {
            int error = GLES20.glGetError();
            assertEquals(String.format("%s : glError %d", op, error), GLES20.GL_NO_ERROR, error);
        }

        public static void checkLocation(int location, String label) {
            assertTrue(String.format("Unable to locate %s in program", label), location >= 0);
        }
    }
}
