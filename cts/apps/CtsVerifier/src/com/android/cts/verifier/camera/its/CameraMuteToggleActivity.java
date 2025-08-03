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
package com.android.cts.verifier.camera.its;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.ex.camera2.blocking.BlockingCameraManager;
import com.android.ex.camera2.blocking.BlockingCameraManager.BlockingOpenException;
import com.android.ex.camera2.blocking.BlockingSessionCallback;
import com.android.ex.camera2.blocking.BlockingStateCallback;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Test for manual verification of camera privacy hardware switches
 * This test verifies that devices which implement camera hardware
 * privacy toggles enforce sensor privacy when toggles are enabled.
 * - The video stream should be muted:
 * - camera preview & capture should be blank
 * - A dialog or notification should be shown that informs
 * the user that the sensor privacy is enabled.
 */
public class CameraMuteToggleActivity extends PassFailButtons.Activity
        implements TextureView.SurfaceTextureListener,
        ImageReader.OnImageAvailableListener {

    private static final String TAG = "CameraMuteToggleActivity";
    private static final int SESSION_READY_TIMEOUT_MS = 5000;
    private static final int DEFAULT_CAMERA_IDX = 0;

    private TextureView mPreviewView;
    private SurfaceTexture mPreviewTexture;
    private Surface mPreviewSurface;

    private ImageView mImageView;

    private CameraManager mCameraManager;
    private HandlerThread mCameraThread;
    private Handler mCameraHandler;
    private BlockingCameraManager mBlockingCameraManager;
    private CameraCharacteristics mCameraCharacteristics;
    private BlockingStateCallback mCameraListener;

    private BlockingSessionCallback mSessionListener;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private CaptureRequest.Builder mStillCaptureRequestBuilder;
    private CaptureRequest mStillCaptureRequest;

    private CameraCaptureSession mCaptureSession;
    private CameraDevice mCameraDevice;

    SizeComparator mSizeComparator = new SizeComparator();

    private Size mPreviewSize;
    private Size mJpegSize;
    private ImageReader mJpegImageReader;

    private CameraCaptureSession.CaptureCallback mCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.cam_hw_toggle);

        setPassFailButtonClickListeners();

        mPreviewView = findViewById(R.id.preview_view);
        mImageView = findViewById(R.id.image_view);

        mPreviewView.setSurfaceTextureListener(this);

        mCameraManager = getSystemService(CameraManager.class);

        setInfoResources(R.string.camera_hw_toggle_test, R.string.camera_hw_toggle_test_info, -1);

        // Enable Pass button only after taking photo
        setPassButtonEnabled(false);
        setTakePictureButtonEnabled(false);

        mBlockingCameraManager = new BlockingCameraManager(mCameraManager);
        mCameraListener = new BlockingStateCallback();
    }

    @Override
    public void onResume() {
        super.onResume();

        startBackgroundThread();

        Exception cameraSetupException = null;
        boolean enablePassButton = false;
        try {
            final String[] camerasList = mCameraManager.getCameraIdList();
            if (camerasList.length > 0) {
                String cameraId = mCameraManager.getCameraIdList()[DEFAULT_CAMERA_IDX];
                setUpCamera(cameraId);
            } else {
                showCameraErrorText("");
            }
        } catch (CameraAccessException e) {
            cameraSetupException = e;
            // Enable Pass button for cameras that do not support mute patterns
            // and will disconnect clients if sensor privacy is enabled
            enablePassButton = (e.getReason() == CameraAccessException.CAMERA_DISABLED);
        } catch (BlockingOpenException e) {
            cameraSetupException = e;
            enablePassButton = e.wasDisconnected();
        } finally {
            if (cameraSetupException != null) {
                cameraSetupException.printStackTrace();
                showCameraErrorText(cameraSetupException.getMessage());
                setPassButtonEnabled(enablePassButton);
            }
        }
    }

    private void showCameraErrorText(String errorMsg) {
        TextView instructionsText = findViewById(R.id.instruction_text);
        instructionsText.setText(R.string.camera_hw_toggle_test_no_camera);
        instructionsText.append(errorMsg);
        setTakePictureButtonEnabled(false);
    }

    @Override
    public void onPause() {
        shutdownCamera();
        stopBackgroundThread();

        super.onPause();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
            int width, int height) {
        mPreviewTexture = surfaceTexture;

        mPreviewSurface = new Surface(mPreviewTexture);

        if (mCameraDevice != null) {
            startPreview();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // Ignored, Camera does all the work for us
        Log.v(TAG, "onSurfaceTextureSizeChanged: " + width + " x " + height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mPreviewTexture = null;
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // Invoked every time there's a new Camera preview frame
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        Image img = null;
        try {
            img = reader.acquireNextImage();
            if (img == null) {
                Log.d(TAG, "Invalid image!");
                return;
            }
            final int format = img.getFormat();

            Bitmap imgBitmap = null;
            if (format == ImageFormat.JPEG) {
                ByteBuffer jpegBuffer = img.getPlanes()[0].getBuffer();
                jpegBuffer.rewind();
                byte[] jpegData = new byte[jpegBuffer.limit()];
                jpegBuffer.get(jpegData);
                imgBitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
                img.close();
            } else {
                Log.i(TAG, "Unsupported image format: " + format);
            }
            if (imgBitmap != null) {
                final Bitmap bitmap = imgBitmap;
                final boolean isMuted = isBitmapMuted(imgBitmap);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mImageView.setImageBitmap(bitmap);
                        // enable pass button if image is muted (black)
                        setPassButtonEnabled(isMuted);
                    }
                });
            }
        } catch (java.lang.IllegalStateException e) {
            // Swallow exceptions
            e.printStackTrace();
        } finally {
            if (img != null) {
                img.close();
            }
        }
    }

    private boolean isBitmapMuted(final Bitmap imgBitmap) {
        // black images may have pixels with values > 0
        // because of JPEG compression artifacts
        final float COLOR_THRESHOLD = 0.02f;
        for (int y = 0; y < imgBitmap.getHeight(); y++) {
            for (int x = 0; x < imgBitmap.getWidth(); x++) {
                Color pixelColor = Color.valueOf(imgBitmap.getPixel(x, y));
                if (pixelColor.red() > COLOR_THRESHOLD || pixelColor.green() > COLOR_THRESHOLD
                        || pixelColor.blue() > COLOR_THRESHOLD) {
                    return false;
                }
            }
        }
        return true;
    }

    private class SizeComparator implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            long lha = lhs.getWidth() * lhs.getHeight();
            long rha = rhs.getWidth() * rhs.getHeight();
            if (lha == rha) {
                lha = lhs.getWidth();
                rha = rhs.getWidth();
            }
            return (lha < rha) ? -1 : (lha > rha ? 1 : 0);
        }
    }

    private void setUpCamera(String cameraId) throws CameraAccessException, BlockingOpenException {
        shutdownCamera();

        mCameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
        mCameraDevice = mBlockingCameraManager.openCamera(cameraId,
                mCameraListener, mCameraHandler);

        StreamConfigurationMap config =
                mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] jpegSizes = config.getOutputSizes(ImageFormat.JPEG);
        Arrays.sort(jpegSizes, mSizeComparator);
        // choose smallest image size, image capture is not the point of this test
        mJpegSize = jpegSizes[0];

        mJpegImageReader = ImageReader.newInstance(
                mJpegSize.getWidth(), mJpegSize.getHeight(), ImageFormat.JPEG, 1);
        mJpegImageReader.setOnImageAvailableListener(this, mCameraHandler);

        if (mPreviewTexture != null) {
            startPreview();
        }
    }

    private void shutdownCamera() {
        if (null != mCaptureSession) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (null != mJpegImageReader) {
            mJpegImageReader.close();
            mJpegImageReader = null;
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mCameraThread = new HandlerThread("CameraThreadBackground");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mCameraThread.quitSafely();
        try {
            mCameraThread.join();
            mCameraThread = null;
            mCameraHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private Size getPreviewSize(int minWidth) {
        StreamConfigurationMap config =
                mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] outputSizes = config.getOutputSizes(SurfaceTexture.class);
        Arrays.sort(outputSizes, mSizeComparator);
        // choose smallest image size that's at least minWidth
        // image capture is not the point of this test
        for (Size outputSize : outputSizes) {
            if (outputSize.getWidth() > minWidth) {
                return outputSize;
            }
        }
        return outputSizes[0];
    }

    private void startPreview() {
        try {
            mPreviewSize = getPreviewSize(256);

            mPreviewTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewRequestBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mPreviewSurface);

            mStillCaptureRequestBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mStillCaptureRequestBuilder.addTarget(mPreviewSurface);
            mStillCaptureRequestBuilder.addTarget(mJpegImageReader.getSurface());

            mSessionListener = new BlockingSessionCallback();
            List<Surface> outputSurfaces = new ArrayList<Surface>(/*capacity*/3);
            outputSurfaces.add(mPreviewSurface);
            outputSurfaces.add(mJpegImageReader.getSurface());
            mCameraDevice.createCaptureSession(outputSurfaces, mSessionListener, mCameraHandler);
            mCaptureSession = mSessionListener.waitAndGetSession(/*timeoutMs*/3000);

            mPreviewRequest = mPreviewRequestBuilder.build();
            mStillCaptureRequest = mStillCaptureRequestBuilder.build();

            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mCameraHandler);

            setTakePictureButtonEnabled(true);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void takePicture() {
        try {
            mCaptureSession.stopRepeating();
            mSessionListener.getStateWaiter().waitForState(
                    BlockingSessionCallback.SESSION_READY, SESSION_READY_TIMEOUT_MS);

            mCaptureSession.capture(mStillCaptureRequest, mCaptureCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setPassButtonEnabled(boolean enabled) {
        ImageButton pass_button = findViewById(R.id.pass_button);
        pass_button.setEnabled(enabled);
    }

    private void setTakePictureButtonEnabled(boolean enabled) {
        Button takePhoto = findViewById(R.id.take_picture_button);
        takePhoto.setOnClickListener(v -> takePicture());
        takePhoto.setEnabled(enabled);
    }
}
