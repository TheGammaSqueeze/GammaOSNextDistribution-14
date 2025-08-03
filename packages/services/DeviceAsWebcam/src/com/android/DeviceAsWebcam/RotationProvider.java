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

import android.annotation.IntRange;
import android.content.Context;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.OrientationEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provider for receiving rotation updates from the {@link SensorManager} when the rotation of
 * the device has changed.
 *
 * <p> This class monitors motion sensor and notifies the listener about physical orientation
 * changes in the rotation degrees value which can be used to rotate the stream images to the
 * upright orientation.
 *
 * <pre><code>
 * // Create a provider.
 * RotationProvider mRotationProvider = new RotationProvider(getApplicationContext());
 *
 * // Add listener to receive updates.
 * mRotationProvider.addListener(rotation -> {
 *     // Apply the rotation values to the related targets
 * });
 *
 * // Remove when no longer needed.
 * mRotationProvider.clearListener();
 * </code></pre>
 */
public final class RotationProvider {
    private final Object mLock = new Object();
    private final OrientationEventListener mOrientationListener;
    private final Map<Listener, ListenerWrapper> mListeners = new HashMap<>();
    private int mRotation;
    private int mSensorOrientation;

    private int mLastDisplayOrientation;

    /**
     * Creates a new RotationProvider.
     *
     * @param applicationContext the application context used to register
     *                           {@link OrientationEventListener} or get display rotation.
     * @param sensorOrientation  the camera sensor orientation value
     */
    public RotationProvider(Context applicationContext, int sensorOrientation, int lensFacing) {
        mLastDisplayOrientation = applicationContext.getSystemService(DisplayManager.class)
                .getDisplay(Display.DEFAULT_DISPLAY).getRotation();

        // sensor orientation is reported as the clockwise rotation needed for back camera, and
        // counter clockwise rotation needed for front camera. For consistent logic, always track
        // clockwise rotation needed in mSensorOrientation.
        mSensorOrientation = lensFacing == CameraCharacteristics.LENS_FACING_FRONT ?
                (360 - sensorOrientation) : sensorOrientation;
        mRotation = sensorOrientationToRotationDegrees(mLastDisplayOrientation);
        mOrientationListener = new OrientationEventListener(applicationContext) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
                    // Short-circuit if orientation is unknown. Unknown rotation
                    // can't be handled so it shouldn't be sent.
                    return;
                }

                int newRotation;
                int originalRotation;
                List<ListenerWrapper> listeners = new ArrayList<>();
                // Take a snapshot for thread safety.
                synchronized (mLock) {
                    mLastDisplayOrientation = orientation;
                    newRotation = sensorOrientationToRotationDegrees(orientation);
                    originalRotation = mRotation;
                    if (mRotation != newRotation) {
                        mRotation = newRotation;
                        listeners.addAll(mListeners.values());
                    }
                }

                if (originalRotation != newRotation) {
                    for (ListenerWrapper listenerWrapper : listeners) {
                        listenerWrapper.onRotationChanged(newRotation);
                    }
                }
            }
        };
    }

    public int getRotation() {
        synchronized (mLock) {
            return mRotation;
        }
    }

    /**
     * Sets a {@link Listener} that listens for rotation changes.
     *
     * @param executor The executor in which the {@link Listener#onRotationChanged(int)} will be
     *                 run.
     * @return false if the device cannot detection rotation changes. In that case, the listener
     * will not be set.
     */
    public boolean addListener(Executor executor, Listener listener) {
        synchronized (mLock) {
            if (!mOrientationListener.canDetectOrientation()) {
                return false;
            }
            mListeners.put(listener, new ListenerWrapper(listener, executor));
            mOrientationListener.enable();
        }
        return true;
    }

    /**
     * Removes the given {@link Listener} from this object.
     *
     * <p> The removed listener will no longer receive rotation updates.
     */
    public void removeListener(Listener listener) {
        synchronized (mLock) {
            ListenerWrapper listenerWrapper = mListeners.get(listener);
            if (listenerWrapper != null) {
                listenerWrapper.disable();
                mListeners.remove(listener);
            }
            if (mListeners.isEmpty()) {
                mOrientationListener.disable();
            }
        }
    }

    public void updateSensorOrientation(int sensorOrientation, int lensFacing) {
        synchronized (mLock) {
            // sensor orientation is reported as the clockwise rotation needed for back camera, and
            // counter clockwise rotation needed for front camera. For consistent logic, always
            // track clockwise rotation needed in mSensorOrientation.
            mSensorOrientation = lensFacing == CameraCharacteristics.LENS_FACING_FRONT ?
                    (360 - sensorOrientation) : sensorOrientation;

            // Fire callbacks with the new rotation
            mOrientationListener.onOrientationChanged(mLastDisplayOrientation);
        }
    }

    /**
     * Converts sensor orientation degrees to the image rotation degrees. Also debounces edge cases
     * to prevent stream from flipping very quickly while the user is handling the device.
     *
     * <p>Currently, the returned value can only be 0 or 180 because DeviceAsWebcam only support
     * in the landscape mode. The webcam stream images will be rotated to upright orientation when
     * the device is in the landscape orientation.
     */
    private int sensorOrientationToRotationDegrees(@IntRange(from = 0, to = 359) int orientation) {
        synchronized (mLock) {
            // Orientation is reported as the clockwise angle from device's natural orientation.
            // Camera sensor orientation is reported as the clockwise angle that the buffer must be
            // rotated to match device's natural orientation, so the sensor orientation is reported
            // counter clockwise.
            int bufferAngle = 360 - mSensorOrientation;

            // If the angle between the image buffer and device is greater than 90 degrees on either
            // side, we want to flip the stream.
            int dAngle = (360 + (bufferAngle - orientation)) % 360;

            // To prevent stream from wildly flipping around while the user is handling the device,
            // we debounce values that are too close to the trigger points. "Too close" is being
            // arbitrarily defined as within 10 degrees.
            int ident = dAngle / 10;
            if (ident == 8 || ident == 9
                    || ident == 26 || ident == 27) {
                // orientation too close to 90 or 270; don't change rotation
                return mRotation;
            }

            // Orientation past the debounce zone. Return ideal rotation
            if (dAngle >= 90 && dAngle < 270) {
                return 180;
            } else {
                return 0;
            }
        }
    }

    /**
     * Wrapper of {@link Listener} with the executor and a tombstone flag.
     */
    private static class ListenerWrapper {
        private final Listener mListener;
        private final Executor mExecutor;
        private final AtomicBoolean mEnabled;

        ListenerWrapper(Listener listener, Executor executor) {
            mListener = listener;
            mExecutor = executor;
            mEnabled = new AtomicBoolean(true);
        }

        void onRotationChanged(int rotation) {
            mExecutor.execute(() -> {
                if (mEnabled.get()) {
                    mListener.onRotationChanged(rotation);
                }
            });
        }

        /**
         * Once disabled, the app will not receive callback even if it has already been posted on
         * the callback thread.
         */
        void disable() {
            mEnabled.set(false);
        }
    }

    /**
     * Callback interface to receive rotation updates.
     */
    public interface Listener {

        /**
         * Called when the physical rotation of the device changes to cause the corresponding
         * rotation value is changed.
         *
         * <p>Currently, the returned value can only be 0 or 180 because DeviceAsWebcam only
         * support in the landscape mode. The webcam stream images will be rotated to upright
         * orientation when the device is in the landscape orientation.
         */
        void onRotationChanged(int rotation);
    }
}

