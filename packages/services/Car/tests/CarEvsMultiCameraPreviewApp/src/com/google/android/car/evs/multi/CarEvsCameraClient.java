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

package com.google.android.car.evs.multi;

import static android.car.evs.CarEvsManager.ERROR_NONE;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.Car.CarServiceLifecycleListener;
import android.car.evs.CarEvsBufferDescriptor;
import android.car.evs.CarEvsManager;
import android.car.evs.CarEvsManager.CarEvsServiceType;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.GuardedBy;

import com.android.car.internal.evs.CarEvsGLSurfaceView;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class represents a single Car client and manages ICarEvsStreamCallback and
 * CarEvsGLSurface.BufferCallback objects to run camera preview.
 */
final class CarEvsCameraClient {

    private static final String TAG = CarEvsCameraClient.class.getSimpleName();
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    /**
     * Defines internal states.
     */
    private final static int STREAM_STATE_STOPPED = 0;
    private final static int STREAM_STATE_VISIBLE = 1;
    private final static int STREAM_STATE_INVISIBLE = 2;
    private final static int STREAM_STATE_LOST = 3;

    /**
     * CarEvsBufferDescriptor id contains its service type in 8-MSB  of 32-bit word and
     * a EVS frame buffer id in the rest of bits.
     */
    private static final int BUFFER_ID_BITDEPTH = 24;

    /* Use a ReentrantLock to get waiters. */
    private final ReentrantLock mLock = new ReentrantLock();

    /** CarEvsStreamCallback implementation. */
    private final StreamHandler mStreamHandler = new StreamHandler();

    /** Tells whether or not a video stream is running */
    @GuardedBy("mLock")
    private int mStreamState = STREAM_STATE_STOPPED;

    /**
     *  Buffer queue to store received frames per service type. This member will be accessed by
     *  Binder thread (ICarEvsStreamCallback) and GL thread (CarEvsGLSurfaceView.BufferCallback).
     */
    @GuardedBy("mLock")
    private final SparseArray<ArrayList<CarEvsBufferDescriptor>> mBufferQueue = new SparseArray<>();

    /** Callback executor */
    private final ExecutorService mCallbackExecutor = Executors.newFixedThreadPool(1);

    /** Service types currently we are running. */
    @GuardedBy("mLock")
    private final ArraySet<Integer> mNextServiceTypes = new ArraySet<>();

    /** List of CarEvsGLSurface.BufferCallback implementations. */
    @GuardedBy("mLock")
    private final ArrayList<CarEvsGLSurfaceView.BufferCallback> mSurfaceBufferHandlers =
            new ArrayList<>();

    /** CarService status listener. */
    private final CarServiceLifecycleListener mCarServiceLifecycleListener = (car, ready) -> {
        mLock.lock();
        try {
            mCar = ready ? car : null;
            mEvsManager = ready ? (CarEvsManager) car.getCarManager(Car.CAR_EVS_SERVICE) : null;
            if (ready) {
                return;
            }

            handleVideoStreamLocked(STREAM_STATE_STOPPED);
        } catch (CarNotConnectedException err) {
            Log.e(TAG, "Failed to connect to the Car Service");
        } finally {
            mLock.unlock();
        }
    };

    /** Car instance to use. */
    private Car mCar;

    /** CarEvsManager to use. */
    private CarEvsManager mEvsManager;

    final class StreamHandler implements CarEvsManager.CarEvsStreamCallback {
        @Override
        public void onStreamEvent(int event) {
            // TOOD: handle stream events.
            Log.i(TAG, "Client " + this + " received " + event);
        }

        @Override
        public void onNewFrame(CarEvsBufferDescriptor desc) {
            mLock.lock();
            try {
                @CarEvsServiceType int type = getServiceType(desc.getId());
                ArrayList bufferQueue = mBufferQueue.get(type);
                if (bufferQueue == null) {
                    return;
                }

                bufferQueue.add(desc);
            } finally {
                mLock.unlock();
            }
        }
    }

    final class SurfaceViewBufferHandler implements CarEvsGLSurfaceView.BufferCallback {
        private final @CarEvsServiceType int mType;

        SurfaceViewBufferHandler(@CarEvsServiceType int type) {
            mType = type;
        }

        @Override
        public CarEvsBufferDescriptor onBufferRequested() {
            try {
                if (!mLock.tryLock(/* timeout= */ 100, TimeUnit.MILLISECONDS)) {
                    if (DBG) {
                      Log.d(TAG, "Timer for new framebuffer expired.");
                    }
                    return null;
                }
            } catch (InterruptedException e) {
                Log.w(TAG, "Timer for new framebuffer is interrupted.");
                return null;
            }

            try {
                ArrayList<CarEvsBufferDescriptor> buffers = mBufferQueue.get(mType);
                if (buffers == null || buffers.isEmpty()) {
                    if (DBG) {
                        Log.d(TAG, "No buffer is available for type=" + mType);
                    }
                    return null;
                }

                // The renderer refreshes the screen faster than the camera frame rate so it's okay
                // to return the first buffer in the queue always.
                return buffers.remove(0);
            } finally {
                mLock.unlock();
            }
        }

        @Override
        public void onBufferProcessed(CarEvsBufferDescriptor buffer) {
            doneWithBuffer(buffer);
        }
    }

    CarEvsCameraClient(CarEvsMultiCameraPreviewActivity activity) {
        this(activity, /* serviceTypes = */ null);
    }

    CarEvsCameraClient(CarEvsMultiCameraPreviewActivity activity, ArraySet<Integer> serviceTypes) {
        if (serviceTypes != null) {
            mNextServiceTypes.addAll(serviceTypes);
        }

        Car.createCar(activity.getApplicationContext(), /* handler= */ null,
                Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, mCarServiceLifecycleListener);

    }

    /**
     * Updates CarEvsService service types that this client instance will use.
     *
     * @param types A set of CarEvsManager.SERVICE_TYPE_* constants.
     * @return false if a given set is identical to what we have.
     *         true otherwise.
     */
    boolean updateServiceTypes(ArraySet<Integer> types) {
        mLock.lock();
        try {
            if (mNextServiceTypes.equals(types)) {
                return false;
            }

            mNextServiceTypes.clear();
            mNextServiceTypes.addAll(types);
            return true;
        } finally {
            mLock.unlock();
        }
    }

    /**
     * Starts video streams for given service types.
     *
     * @param types A set of CarEvsManager.SERVICE_TYPE_* this client instance will run.
     * @return {@code ArraySet<Integer>} that contains successfully started service types.
     */
    ArraySet<Integer> startVideoStream(ArraySet<Integer> types) {
        updateServiceTypes(types);
        return startVideoStream();
    }

    /**
     * Requests to start video streams of service types.
     *
     * @return {@code ArraySet<Integer>} that contains successfully started service types.
     */
    ArraySet<Integer> startVideoStream() {
        ArraySet<Integer> started = new ArraySet<>();
        mLock.lock();
        try {
            if (mNextServiceTypes.isEmpty()) {
                return started;
            }

            for (var type : mNextServiceTypes) {
                if (!mBufferQueue.contains(type)) {
                    mBufferQueue.put(type, new ArrayList<>());
                }

                int res = mEvsManager.startVideoStream(type, /* token= */ null, mCallbackExecutor,
                        mStreamHandler);
                if (res != ERROR_NONE) {
                    Log.w(TAG, "Failed to start a video for type=" + type + ", error=" + res);
                    continue;
                }

                mSurfaceBufferHandlers.add(new SurfaceViewBufferHandler(type));
                started.add(type);
            }
        } finally {
            mLock.unlock();
        }

        return started;
    }

    /**
     * Stops all active video streams managed by our CarEvsManager instance.
     */
    void stopVideoStream() {
        mLock.lock();
        try {
            mEvsManager.stopVideoStream();
            mSurfaceBufferHandlers.clear();

            for (int i = 0; i < mBufferQueue.size(); i++) {
                if (mBufferQueue.get(i) == null) {
                    Log.w(TAG, "No buffer queue exists for type=" + i);
                    continue;
                }

                mBufferQueue.get(i).clear();
            }
            mBufferQueue.clear();
        } finally {
            mLock.unlock();
        }
    }

    /**
     * Stops video streams and release other resources.
     */
    void release() {
        mLock.lock();
        try {
            if (mEvsManager != null) {
                handleVideoStreamLocked(STREAM_STATE_STOPPED);
                mEvsManager.clearStatusListener();
            }

            if (mCar != null) {
                mCar.disconnect();
            }
        } finally {
            mLock.unlock();
        }
    }

    /**
     * Returns a list of CarEvsGLSurfacePreview.BufferCallback instances.
     *
     * @return {@code ArrayList<CarEvsGLSurfaceView.BufferCallback>} object. This wouldn't
     *         be null or empty.
     */
    ArrayList<CarEvsGLSurfaceView.BufferCallback> getBufferCallbacks() {
        mLock.lock();
        try {
            return mSurfaceBufferHandlers;
        } finally {
            mLock.unlock();
        }
    }

    private void doneWithBuffer(CarEvsBufferDescriptor buffer) {
        mLock.lock();
        try {
            doneWithBufferLocked(buffer);
        } finally {
            mLock.unlock();
        }
    }

    @GuardedBy("mLock")
    private void doneWithBufferLocked(CarEvsBufferDescriptor buffer) {
        try {
            mEvsManager.returnFrameBuffer(buffer);
        } catch (Exception e) {
            Log.w(TAG, "Failed to return a buffer: " + Log.getStackTraceString(e));
        }
    }

    void handleVideoStream(int newState) {
        mLock.lock();
        try {
            handleVideoStreamLocked(newState);
        } finally {
            mLock.unlock();
        }
    }

    @GuardedBy("mLock")
    private void handleVideoStreamLocked(int newState) {
        Log.i(TAG, "Requested: " + streamStateToString(mStreamState) + " -> " +
                streamStateToString(newState));
        if (newState == mStreamState) {
            // Safely ignore a request of transitioning to the current state.
            return;
        }

        switch (newState) {
            case STREAM_STATE_STOPPED:
                stopVideoStream();
                break;

            case STREAM_STATE_VISIBLE:
                // Starts a video stream
                startVideoStream();
                break;

            case STREAM_STATE_INVISIBLE:
                break;

            case STREAM_STATE_LOST:
                break;

            default:
                throw new IllegalArgumentException();
        }

        mStreamState = newState;
        Log.i(TAG, "Completed: " + streamStateToString(mStreamState));
    }

    private static @CarEvsServiceType int getServiceType(int descId) {
        return descId >> BUFFER_ID_BITDEPTH;
    }

    private static String streamStateToString(int state) {
        switch (state) {
            case STREAM_STATE_STOPPED:
                return "STOPPED";

            case STREAM_STATE_VISIBLE:
                return "VISIBLE";

            case STREAM_STATE_INVISIBLE:
                return "INVISIBLE";

            case STREAM_STATE_LOST:
                return "LOST";

            default:
                return "UNKNOWN: " + state;
        }
    }
}
