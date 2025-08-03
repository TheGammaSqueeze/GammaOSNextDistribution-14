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

package com.android.DeviceAsWebcam;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.SurfaceTexture;
import android.hardware.HardwareBuffer;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.util.Size;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.android.DeviceAsWebcam.annotations.UsedByNative;
import com.android.DeviceAsWebcam.utils.IgnoredV4L2Nodes;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class DeviceAsWebcamFgService extends Service {
    private static final String TAG = "DeviceAsWebcamFgService";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final String NOTIF_CHANNEL_ID = "WebcamService";
    private static final int NOTIF_ID = 1;

    static {
        System.loadLibrary("jni_deviceAsWebcam");
    }

    // Guards all methods in the service to ensure a consistent state while executing a method
    private final Object mServiceLock = new Object();
    private final IBinder mBinder = new LocalBinder();
    private Context mContext;
    private CameraController mCameraController;
    private Runnable mDestroyActivityCallback = null;
    private boolean mServiceRunning = false;

    private NotificationCompat.Builder mNotificationBuilder;
    private int mNotificationIcon;
    private int mNextNotificationIcon;
    private boolean mNotificationUpdatePending;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        synchronized (mServiceLock) {
            mContext = getApplicationContext();
            if (mContext == null) {
                Log.e(TAG, "Application context is null!, something is going to go wrong");
            }
            mCameraController = new CameraController(mContext, new WeakReference<>(this));
            int res = setupServicesAndStartListening();
            startForegroundWithNotification();
            // If `setupServiceAndStartListening` fails, we don't want to start the foreground
            // service. However, Android expects a call to `startForegroundWithNotification` in
            // `onStartCommand` and throws an exception if it isn't called. So, if the foreground
            // service should not be running, we call `startForegroundWithNotification` which starts
            // the service, and immediately call `stopSelf` which causes the service to be
            // torn down once `onStartCommand` returns.
            if (res != 0) {
                stopSelf();
            }
            mServiceRunning = true;
            return START_NOT_STICKY;
        }
    }

    private String createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(NOTIF_CHANNEL_ID,
                getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager notMan = getSystemService(NotificationManager.class);
        Objects.requireNonNull(notMan).createNotificationChannel(channel);
        return NOTIF_CHANNEL_ID;
    }

    private void startForegroundWithNotification() {
        Intent notificationIntent = new Intent(mContext, DeviceAsWebcamPreview.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, notificationIntent,
                PendingIntent.FLAG_MUTABLE);
        String channelId = createNotificationChannel();
        mNextNotificationIcon = mNotificationIcon = R.drawable.ic_notif_line;
        mNotificationBuilder = new NotificationCompat.Builder(this, channelId)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .setContentText(getString(R.string.notif_desc))
                .setContentTitle(getString(R.string.notif_title))
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .setOngoing(true)
                .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                .setShowWhen(false)
                .setSmallIcon(mNotificationIcon)
                .setTicker(getString(R.string.notif_ticker))
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        Notification notif = mNotificationBuilder.build();
        startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
    }

    private int setupServicesAndStartListening() {
        String[] ignoredNodes = IgnoredV4L2Nodes.getIgnoredNodes(getApplicationContext());
        return setupServicesAndStartListeningNative(ignoredNodes);
    }

    @Override
    public void onDestroy() {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                return;
            }
            mServiceRunning = false;
            if (mDestroyActivityCallback != null) {
                mDestroyActivityCallback.run();
            }
            nativeOnDestroy();
            if (VERBOSE) {
                Log.v(TAG, "Destroyed fg service");
            }
            // Ensure that the service notification is removed.
            NotificationManagerCompat.from(mContext).cancelAll();
        }
        super.onDestroy();
    }

    /**
     * Returns the best suitable output size for preview.
     *
     * <p>If the webcam stream doesn't exist, find the largest 16:9 supported output size which is
     * not larger than 1080p. If the webcam stream exists, find the largest supported output size
     * which matches the aspect ratio of the webcam stream size and is not larger than the webcam
     * stream size.
     */
    public Size getSuitablePreviewSize() {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "getSuitablePreviewSize called after Service was destroyed.");
                return null;
            }
            return mCameraController.getSuitablePreviewSize();
        }
    }

    /**
     * Method to set a preview surface texture that camera will stream to. Should be of the size
     * returned by {@link #getSuitablePreviewSize}.
     *
     * @param surfaceTexture surfaceTexture to stream preview frames to
     * @param previewSize the preview size
     * @param previewSizeChangeListener a listener to monitor the preview size change events.
     */
    public void setPreviewSurfaceTexture(SurfaceTexture surfaceTexture, Size previewSize,
            Consumer<Size> previewSizeChangeListener) {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "setPreviewSurfaceTexture called after Service was destroyed.");
                return;
            }
            mCameraController.startPreviewStreaming(surfaceTexture, previewSize,
                    previewSizeChangeListener);
        }
    }

    /**
     * Method to remove any preview SurfaceTexture set by {@link #setPreviewSurfaceTexture}.
     */
    public void removePreviewSurfaceTexture() {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "removePreviewSurfaceTexture was called after Service was destroyed.");
                return;
            }
            mCameraController.stopPreviewStreaming();
        }
    }

    /**
     * Method to setOnDestroyedCallback. This callback will be called when immediately before the
     * foreground service is destroyed. Intended to give and bound context a change to clean up
     * before the Service is destroyed. {@code setOnDestroyedCallback(null)} must be called to unset
     * the callback when a bound context finishes to prevent Context leak.
     * <p>
     * This callback must not call {@code setOnDestroyedCallback} from within the callback.
     *
     * @param callback callback to be called when the service is destroyed. {@code null} unsets
     *                 the callback
     */
    public void setOnDestroyedCallback(@Nullable Runnable callback) {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "setOnDestroyedCallback was called after Service was destroyed");
                return;
            }
            mDestroyActivityCallback = callback;
        }
    }

    /**
     * Returns the {@link CameraInfo} of the working camera.
     */
    public CameraInfo getCameraInfo() {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "getCameraInfo called after Service was destroyed.");
                return null;
            }
            return mCameraController.getCameraInfo();
        }
    }

    /**
     * Returns the available {@link CameraId} list.
     */
    @Nullable
    public List<CameraId> getAvailableCameraIds() {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "getAvailableCameraIds called after Service was destroyed.");
                return null;
            }
            return mCameraController.getAvailableCameraIds();
        }
    }

    /**
     * Returns the {@link CameraInfo} for the specified camera id.
     */
    @Nullable
    public CameraInfo getOrCreateCameraInfo(CameraId cameraId) {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "getCameraInfo called after Service was destroyed.");
                return null;
            }
            return mCameraController.getOrCreateCameraInfo(cameraId);
        }
    }

    /**
     * Sets the new zoom ratio setting to the working camera.
     */
    public void setZoomRatio(float zoomRatio) {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "setZoomRatio called after Service was destroyed.");
                return;
            }
            mCameraController.setZoomRatio(zoomRatio);
        }
    }

    /**
     * Returns current zoom ratio setting.
     */
    public float getZoomRatio() {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "getZoomRatio called after Service was destroyed.");
                return 1.0f;
            }
            return mCameraController.getZoomRatio();
        }
    }

    /**
     * Toggles camera between the back and front cameras.
     */
    public void toggleCamera() {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "toggleCamera called after Service was destroyed.");
                return;
            }
            mCameraController.toggleCamera();
        }
    }

    /**
     * Switches current working camera to specific one.
     */
    public void switchCamera(CameraId cameraId) {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "switchCamera called after Service was destroyed.");
                return;
            }
            mCameraController.switchCamera(cameraId);
        }
    }

    /**
     * Sets a {@link CameraController.RotationUpdateListener} to monitor the device rotation
     * changes.
     */
    public void setRotationUpdateListener(CameraController.RotationUpdateListener listener) {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "setRotationUpdateListener called after Service was destroyed.");
                return;
            }
            mCameraController.setRotationUpdateListener(listener);
        }
    }

    /**
     * Returns current rotation degrees value.
     */
    public int getCurrentRotation() {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "getCurrentRotation was called after Service was destroyed");
                return 0;
            }
            return mCameraController.getCurrentRotation();
        }
    }

    /**
     * Returns true if high quality mode is enabled, false otherwise
     */
    public boolean isHighQualityModeEnabled() {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "isHighQualityModeEnabled was called after Service was destroyed");
                return false;
            }
            return mCameraController.isHighQualityModeEnabled();
        }
    }

    /**
     * Enables/Disables high quality mode. See {@link CameraController#setHighQualityModeEnabled}
     * for more info.
     */
    public void setHighQualityModeEnabled(boolean enabled, Runnable callback) {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "switchHighQualityMode was called after Service was destroyed");
                return;
            }
            mCameraController.setHighQualityModeEnabled(enabled, callback);
        }
    }

    private void updateNotification(boolean isStreaming) {
        int transitionIcon; // animated icon
        int finalIcon; // static icon
        if (isStreaming) {
            transitionIcon = R.drawable.ic_notif_streaming;
            // last frame of ic_notif_streaming
            finalIcon = R.drawable.ic_notif_filled;
        } else {
            transitionIcon = R.drawable.ic_notif_idle;
            // last frame of ic_notif_idle
            finalIcon = R.drawable.ic_notif_line;
        }

        synchronized (mServiceLock) {
            if (finalIcon == mNotificationIcon) {
                // Notification already is desired state.
                return;
            }
            if (transitionIcon == mNotificationIcon) {
                // Notification currently animating to finalIcon.
                // Set next state to desired steady state icon.
                mNextNotificationIcon = finalIcon;
                return;
            }

            if (mNotificationUpdatePending) {
                // Notification animating to some other icon. Set the next icon to the new
                // transition icon and let the update runnable handle the actual updates.
                mNextNotificationIcon = transitionIcon;
                return;
            }

            // Notification is in a steady state. Update notification to the new icon.
            mNextNotificationIcon = transitionIcon;
            updateNotificationToNextIcon();
        }
    }

    private void updateNotificationToNextIcon() {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                return;
            }

            mNotificationBuilder.setSmallIcon(mNextNotificationIcon);
            NotificationManagerCompat.from(mContext).notify(NOTIF_ID, mNotificationBuilder.build());
            mNotificationIcon = mNextNotificationIcon;

            boolean notifNeedsUpdate = false;
            if (mNotificationIcon == R.drawable.ic_notif_streaming) {
                // last frame of ic_notif_streaming
                mNextNotificationIcon = R.drawable.ic_notif_filled;
                notifNeedsUpdate = true;
            } else if (mNotificationIcon == R.drawable.ic_notif_idle) {
                // last frame of ic_notif_idle
                mNextNotificationIcon = R.drawable.ic_notif_line;
                notifNeedsUpdate = true;
            }
            mNotificationUpdatePending = notifNeedsUpdate;
            if (notifNeedsUpdate) {
                // Run this method again after 500ms to update the notification to steady
                // state icon
                getMainThreadHandler().postDelayed(this::updateNotificationToNextIcon, 500);
            }
        }
    }

    @UsedByNative("DeviceAsWebcamNative.cpp")
    private void startStreaming() {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "startStreaming was called after Service was destroyed");
                return;
            }
            mCameraController.startWebcamStreaming();
            updateNotification(/*isStreaming*/ true);
        }
    }

    @UsedByNative("DeviceAsWebcamNative.cpp")
    private void stopService() {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "stopService was called after Service was destroyed");
                return;
            }
            stopSelf();
        }
    }

    @UsedByNative("DeviceAsWebcamNative.cpp")
    private void stopStreaming() {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "stopStreaming was called after Service was destroyed");
                return;
            }
            mCameraController.stopWebcamStreaming();
            updateNotification(/*isStreaming*/ false);
        }
    }

    @UsedByNative("DeviceAsWebcamNative.cpp")
    private void returnImage(long timestamp) {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "returnImage was called after Service was destroyed");
                return;
            }
            mCameraController.returnImage(timestamp);
        }
    }

    @UsedByNative("DeviceAsWebcamNative.cpp")
    private void setStreamConfig(boolean mjpeg, int width, int height, int fps) {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "setStreamConfig was called after Service was destroyed");
                return;
            }
            mCameraController.setWebcamStreamConfig(mjpeg, width, height, fps);
        }
    }

    /**
     * Trigger tap-to-focus operation for the specified normalized points mapping to the FOV.
     *
     * <p>The specified normalized points will be used to calculate the corresponding metering
     * rectangles that will be applied for AF, AE and AWB.
     */
    public void tapToFocus(float[] normalizedPoint) {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "tapToFocus was called after Service was destroyed");
                return;
            }
            mCameraController.tapToFocus(normalizedPoint);
        }
    }

    /**
     * Retrieves current tap-to-focus points.
     *
     * @return the normalized points or {@code null} if it is auto-focus mode currently.
     */
    public float[] getTapToFocusPoints() {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "getTapToFocusPoints was called after Service was destroyed");
                return null;
            }
            return mCameraController.getTapToFocusPoints();
        }
    }

    /**
     * Resets to the auto-focus mode.
     */
    public void resetToAutoFocus() {
        synchronized (mServiceLock) {
            if (!mServiceRunning) {
                Log.e(TAG, "resetToAutoFocus was called after Service was destroyed");
                return;
            }
            mCameraController.resetToAutoFocus();
        }
    }

    /**
     * Called by {@link DeviceAsWebcamReceiver} to check if the service should be started.
     * @param ignoredNodes V4L2 nodes to ignore
     * @return {@code true} if the foreground service should be started,
     *         {@code false} if the service is already running or should not be started
     */
    public static native boolean shouldStartServiceNative(String[] ignoredNodes);

    /**
     * Called during {@link #onStartCommand} to initialize the native side of the service.
     * @param ignoredNodes V4L2 nodes to ignore
     * @return 0 if native side code was successfully initialized,
     *         non-0 otherwise
     */
    private native int setupServicesAndStartListeningNative(String[] ignoredNodes);

    /**
     * Called by {@link CameraController} to queue frames for encoding. The frames are encoded
     * asynchronously. When encoding is done, the native code call {@link #returnImage} with the
     * {@code timestamp} passed here.
     * @param buffer buffer containing the frame to be encoded
     * @param timestamp timestamp associated with the buffer which uniquely identifies the buffer
     * @return 0 if buffer was successfully queued for encoding. non-0 otherwise.
     */
    public native int nativeEncodeImage(HardwareBuffer buffer, long timestamp, int rotation);

    /**
     * Called by {@link #onDestroy} to give the JNI code a chance to clean up before the service
     * goes out of scope.
     */
    private native void nativeOnDestroy();


    /**
     * Simple class to hold a reference to {@link DeviceAsWebcamFgService} instance and have it be
     * accessible from {@link android.content.ServiceConnection#onServiceConnected} callback.
     */
    public class LocalBinder extends Binder {
        DeviceAsWebcamFgService getService() {
            return DeviceAsWebcamFgService.this;
        }
    }
}
