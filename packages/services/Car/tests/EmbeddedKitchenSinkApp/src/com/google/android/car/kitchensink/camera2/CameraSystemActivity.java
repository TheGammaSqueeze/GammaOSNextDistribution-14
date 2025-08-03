/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.google.android.car.kitchensink.camera2;

import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import androidx.fragment.app.FragmentActivity;

import com.google.android.car.kitchensink.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class CameraSystemActivity extends FragmentActivity {
    private static final String TAG = "Camera2.SystemUser.KS";
    private CameraManager mCameraManager;
    private SurfaceView mSurfaceView;
    private SurfacePreviewListener mSurfacePreviewListener;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private CameraDevice mCameraDevice;
    private String mCameraId;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private CameraCaptureSession mCaptureSession;
    private CameraSessionListener mCameraSessionListener;
    private CameraStateListener mCameraStateListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int myUserId = UserHandle.myUserId();
        Log.i(TAG, "onCreate userid " + myUserId);
        if (myUserId != UserHandle.USER_SYSTEM) {
            Log.i(TAG, "onCreate re-starting self as user 0");
            Intent selfIntent =
                    new Intent(CameraSystemActivity.this, CameraSystemActivity.class);
            startActivityAsUser(selfIntent, UserHandle.SYSTEM);
            finish();
        }

        setContentView(R.layout.camera_system_activity);
        mCameraManager = getSystemService(CameraManager.class);
        mSurfaceView = findViewById(R.id.surface_view_1);
        mSurfacePreviewListener = new SurfacePreviewListener();
        mSurfaceView.getHolder().addCallback(mSurfacePreviewListener);
        mHandlerThread = new HandlerThread("CameraPreviewThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mCameraStateListener = new CameraStateListener();
        mCameraSessionListener = new CameraSessionListener();
        Button finishButton = (Button) findViewById(R.id.finish);
        finishButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        finish();
                    }
                });
    }

    public void openCamera() {
        try {
            String[] cameraIds = mCameraManager.getCameraIdListNoLazy();
            if (cameraIds.length > 0) {
                mCameraId = cameraIds[0];
                Log.i(TAG, "Opening camera " + mCameraId);
                mCameraManager.openCamera(mCameraId, mCameraStateListener, mHandler);
            } else {
                Log.w(TAG, "Camera service reported no cameras connected to device.");
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to open camera. Got CameraAccessException.");
        } catch (IllegalStateException e) {
            Log.e(TAG, "Failed to open camera. Got IllegalStateException.");
        }
    }

    public class SurfacePreviewListener implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.i(TAG, "Surface created");
            openCamera();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.i(TAG, "Surface Changed to: " + width + "x" + height);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, "Surface destroyed");
        }
    }

    public static class HandlerExecutor implements Executor {
        private final Handler mHandler;

        public HandlerExecutor(Handler handler) {
            mHandler = handler;
        }

        @Override
        public void execute(Runnable runCmd) {
            mHandler.post(runCmd);
        }
    }

    class CameraStateListener extends CameraDevice.StateCallback {

        @Override
        public void onOpened(CameraDevice camera) {
            Log.i(TAG, "Camera Opened");
            mCameraDevice = camera;
            try {
                mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(
                        CameraDevice.TEMPLATE_PREVIEW);
                mCaptureRequestBuilder.addTarget(mSurfaceView.getHolder().getSurface());
                List<OutputConfiguration> outputs = new ArrayList<>();
                outputs.add(new OutputConfiguration(mSurfaceView.getHolder().getSurface()));
                SessionConfiguration sessionConfig = new SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR, outputs,
                        new HandlerExecutor(mHandler), mCameraSessionListener);
                CaptureRequest request = mCaptureRequestBuilder.build();
                sessionConfig.setSessionParameters(request);
                mCameraDevice.createCaptureSession(sessionConfig);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to start capture session. Got CameraAccessException.");
            } catch (IllegalStateException e) {
                Log.e(TAG, "Failed to start capture session. Got IllegalStateException.");
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {}

        @Override
        public void onError(CameraDevice camera, int error) {}
    }

    class CameraSessionListener extends CameraCaptureSession.StateCallback {

        @Override
        public void onConfigured(CameraCaptureSession session) {
            mCaptureSession = session;
            try {
                mCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, mHandler);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to start camera preview. Got CameraAccessException.");
            } catch (IllegalStateException e) {
                Log.e(TAG, "Failed to start camera preview. Got IllegalStateException.");
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {}
    }

    @Override
    protected void onDestroy() {
        try {

            if (mCaptureSession != null) {
                mCaptureSession.stopRepeating();
                mCaptureSession.close();
            }

            if (mHandlerThread != null) {
                mHandlerThread.quitSafely();
                mHandlerThread.join();
            }

            mHandler = null;
            mCameraSessionListener = null;

            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }

            super.onDestroy();
        } catch (Exception e) { }
    }
}
