/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.hardware.multiprocess.camera.cts;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Log;
import android.view.WindowMetrics;

/**
 * Activity implementing basic access of the Camera2 API.
 *
 * <p />
 * This will log all errors to {@link android.hardware.multiprocess.camera.cts.ErrorLoggingService}.
 */
public class Camera2Activity extends Activity {
    private static final String TAG = "Camera2Activity";

    ErrorLoggingService.ErrorServiceConnection mErrorServiceConnection;
    CameraManager mCameraManager;
    AvailabilityCallback mAvailabilityCallback;
    StateCallback mStateCallback;
    Handler mCameraHandler;
    HandlerThread mCameraHandlerThread;
    boolean mIgnoreCameraAccess = false;
    boolean mIgnoreTopActivityResumed = false;
    boolean mIgnoreActivityPaused = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate called: uid " + Process.myUid() + ".");
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mIgnoreCameraAccess = intent.getBooleanExtra(
                TestConstants.EXTRA_IGNORE_CAMERA_ACCESS, false);
        mIgnoreTopActivityResumed = intent.getBooleanExtra(
                TestConstants.EXTRA_IGNORE_TOP_ACTIVITY_RESUMED, false);
        mIgnoreActivityPaused = intent.getBooleanExtra(
                TestConstants.EXTRA_IGNORE_ACTIVITY_PAUSED, false);

        mCameraHandlerThread = new HandlerThread("CameraHandlerThread");
        mCameraHandlerThread.start();
        mCameraHandler = new Handler(mCameraHandlerThread.getLooper());
        mErrorServiceConnection = new ErrorLoggingService.ErrorServiceConnection(this);
        mErrorServiceConnection.start();
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause called.");
        super.onPause();

        if (!mIgnoreActivityPaused) {
            mErrorServiceConnection.logAsync(TestConstants.EVENT_ACTIVITY_PAUSED, "");
        }
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume called.");
        super.onResume();

        WindowMetrics metrics = getWindowManager().getCurrentWindowMetrics();
        Rect windowRect = metrics.getBounds();
        mErrorServiceConnection.logAsync(TestConstants.EVENT_ACTIVITY_RESUMED,
                windowRect.left + ":" + windowRect.top + ":"
                + windowRect.right + ":" + windowRect.bottom);

        try {
            mCameraManager = getSystemService(CameraManager.class);

            if (mCameraManager == null) {
                mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR, TAG +
                        " could not connect camera service");
                return;
            }
            // TODO: http://b/145308043 move this back to getCameraIdListNoLazy()
            String[] cameraIds = mCameraManager.getCameraIdList();

            if (cameraIds == null || cameraIds.length == 0) {
                mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR, TAG +
                        " device reported having no cameras");
                return;
            }

            if (mAvailabilityCallback == null) {
                mAvailabilityCallback = new AvailabilityCallback();
                mCameraManager.registerAvailabilityCallback(mAvailabilityCallback, mCameraHandler);
            }

            final String chosen = cameraIds[0];
            if (mStateCallback == null || mStateCallback.mChosen != chosen) {
                mStateCallback = new StateCallback(chosen);
                mCameraManager.openCamera(chosen, mStateCallback, mCameraHandler);
            }
        } catch (CameraAccessException e) {
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR, TAG +
                    " camera exception during connection: " + e);
            Log.e(TAG, "Access exception: " + e);
        }

        Log.i(TAG, "onResume finished.");
    }

    @Override
    public void onTopResumedActivityChanged(boolean topResumed) {
        /**
         * Log here for easier debugging in split screen mode. We will receive this instead of
         * onResume because in split screen both activities are always in the resumed state.
         */
        Log.i(TAG, "onTopResumedActivityChanged(" + topResumed + ")");
        super.onTopResumedActivityChanged(topResumed);

        if (!mIgnoreTopActivityResumed) {
            if (topResumed) {
                mErrorServiceConnection.logAsync(
                        TestConstants.EVENT_ACTIVITY_TOP_RESUMED_TRUE, "");
            } else {
                mErrorServiceConnection.logAsync(
                        TestConstants.EVENT_ACTIVITY_TOP_RESUMED_FALSE, "");
            }
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy called.");
        super.onDestroy();

        if (mAvailabilityCallback != null) {
            mCameraManager.unregisterAvailabilityCallback(mAvailabilityCallback);
            mAvailabilityCallback = null;
        }

        mCameraHandlerThread.quitSafely();

        if (mErrorServiceConnection != null) {
            mErrorServiceConnection.stop();
            mErrorServiceConnection = null;
        }
    }

    private class AvailabilityCallback extends CameraManager.AvailabilityCallback {
        @Override
        public void onCameraAvailable(String cameraId) {
            super.onCameraAvailable(cameraId);
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_AVAILABLE,
                    cameraId);
            Log.i(TAG, "Camera " + cameraId + " is available");
        }

        @Override
        public void onCameraUnavailable(String cameraId) {
            super.onCameraUnavailable(cameraId);
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_UNAVAILABLE,
                    cameraId);
            Log.i(TAG, "Camera " + cameraId + " is unavailable");
        }

        @Override
        public void onCameraAccessPrioritiesChanged() {
            super.onCameraAccessPrioritiesChanged();
            if (!mIgnoreCameraAccess) {
                mErrorServiceConnection.logAsync(
                        TestConstants.EVENT_CAMERA_ACCESS_PRIORITIES_CHANGED, "");
            }
            Log.i(TAG, "Camera access priorities changed");
        }

        @Override
        public void onPhysicalCameraAvailable(String cameraId, String physicalCameraId) {
            super.onPhysicalCameraAvailable(cameraId, physicalCameraId);
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_AVAILABLE,
                    cameraId + " : " + physicalCameraId);
            Log.i(TAG, "Camera " + cameraId + " : " + physicalCameraId + " is available");
        }

        @Override
        public void onPhysicalCameraUnavailable(String cameraId, String physicalCameraId) {
            super.onPhysicalCameraUnavailable(cameraId, physicalCameraId);
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_UNAVAILABLE,
                    cameraId + " : " + physicalCameraId);
            Log.i(TAG, "Camera " + cameraId + " : " + physicalCameraId + " is unavailable");
        }
    }

    private class StateCallback extends CameraDevice.StateCallback {
        String mChosen;

        StateCallback(String chosen) {
            mChosen = chosen;
        }

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_CONNECT,
                    mChosen);
            Log.i(TAG, "Camera " + mChosen + " is opened");
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_EVICTED,
                    mChosen);
            Log.i(TAG, "Camera " + mChosen + " is disconnected");
        }

        @Override
        public void onError(CameraDevice cameraDevice, int i) {
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR, TAG
                    + " Camera " + mChosen + " experienced error " + i);
            Log.e(TAG, "Camera " + mChosen + " onError called with error " + i);
        }
    }
}
