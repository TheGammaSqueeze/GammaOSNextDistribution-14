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

package com.android.car;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.car.Car;
import android.car.evs.CarEvsBufferDescriptor;
import android.car.evs.CarEvsManager;
import android.car.evs.CarEvsManager.CarEvsStreamEvent;
import android.car.evs.CarEvsStatus;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/*
 * IMPORTANT NOTE:
 * This test assumes that EVS HAL is running at the time of test.  Depending on the test target, the
 * reference EVS HAL ($ANDROID_BUILD_TOP/packages/services/Car/evs/sampleDriver) may be needed.
 * Please add below line to the target's build script to add the reference EVS HAL to the build:
 * ENABLE_EVS_SAMPLE := true
 *
 * The test will likely fail if no EVS HAL is running on the target device.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public final class CarEvsManagerTest extends MockedCarTestBase {
    private static final String TAG = CarEvsManagerTest.class.getSimpleName();

    // We'd expect that underlying stream runs @10fps at least.
    private static final int NUMBER_OF_FRAMES_TO_WAIT = 10;
    private static final int FRAME_TIMEOUT_MS = 1000;
    private static final int SMALL_NAP_MS = 500;
    private static final int STREAM_EVENT_TIMEOUT_SEC = 2;

    // Will return frame buffers in the order they arrived.
    private static final int INDEX_TO_FIRST_ELEM = 0;

    private final ArrayList<CarEvsBufferDescriptor> mReceivedBuffers = new ArrayList<>();
    private final ExecutorService mCallbackExecutor = Executors.newFixedThreadPool(1);
    private final Semaphore mFrameReceivedSignal = new Semaphore(0);
    private final Semaphore mStreamEventOccurred = new Semaphore(0);

    private final Car mCar = Car.createCar(ApplicationProvider.getApplicationContext());
    private final CarEvsManager mEvsManager =
            (CarEvsManager) mCar.getCarManager(Car.CAR_EVS_SERVICE);
    private final EvsStreamCallbackImpl mStreamCallback = new EvsStreamCallbackImpl();
    private final EvsStatusListenerImpl mStatusListener = new EvsStatusListenerImpl();

    private @CarEvsStreamEvent int mLastStreamEvent;

    @Before
    public void setUp() {
        assumeTrue(mCar.isFeatureEnabled(Car.CAR_EVS_SERVICE));
        assertThat(mEvsManager).isNotNull();
        assumeTrue(mEvsManager.isSupported(CarEvsManager.SERVICE_TYPE_REARVIEW));
        assertThat(mStreamCallback).isNotNull();
        assertThat(mStatusListener).isNotNull();

        // Drains all permits
        mFrameReceivedSignal.drainPermits();
        mStreamEventOccurred.drainPermits();

        // Ensures no stream is active
        mEvsManager.stopVideoStream();
    }

    @After
    public void tearDown() throws Exception {
        if (mEvsManager != null) {
            mEvsManager.stopVideoStream();
        }
    }

    @Test
    public void testSessionTokenGeneration() throws Exception {
        assertThat(mEvsManager.generateSessionToken()).isNotNull();
    }

    @Test
    public void testStartAndStopVideoStream() throws Exception {
        // Registers a status listener and start monitoring the CarEvsService's state changes.
        mEvsManager.setStatusListener(mCallbackExecutor, mStatusListener);

        // Requests to start a video stream.
        assertThat(
                mEvsManager.startVideoStream(CarEvsManager.SERVICE_TYPE_REARVIEW,
                        /* token = */ null, mCallbackExecutor, mStreamCallback)
        ).isEqualTo(CarEvsManager.ERROR_NONE);

        // Waits for a few frame buffers
        for (int i = 0; i < NUMBER_OF_FRAMES_TO_WAIT; ++i) {
            assertThat(
                    mFrameReceivedSignal.tryAcquire(FRAME_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            ).isTrue();

            // Nothing to do; returns a buffer immediately
            CarEvsBufferDescriptor toBeReturned = mReceivedBuffers.get(INDEX_TO_FIRST_ELEM);
            mReceivedBuffers.remove(INDEX_TO_FIRST_ELEM);
            mEvsManager.returnFrameBuffer(toBeReturned);
        }

        // Checks a current status
        CarEvsStatus status = mEvsManager.getCurrentStatus();
        assertThat(status).isNotNull();
        assertThat(status.getState()).isEqualTo(CarEvsManager.SERVICE_STATE_ACTIVE);
        assertThat(status.getServiceType()).isEqualTo(CarEvsManager.SERVICE_TYPE_REARVIEW);

        // Then, requests to stop a video stream
        mEvsManager.stopVideoStream();

        // Checks a current status a few hundreds milliseconds after.  CarEvsService will move into
        // the inactive state when it gets a stream-stopped event from the EVS manager.
        SystemClock.sleep(SMALL_NAP_MS);
        assertThat(mStreamCallback.waitForStreamEvent(CarEvsManager.STREAM_EVENT_STREAM_STOPPED))
                .isTrue();

        // Unregister a listener
        mEvsManager.clearStatusListener();
    }

    @Test
    public void testIsSupported() throws Exception {
        assertThat(mEvsManager.isSupported(CarEvsManager.SERVICE_TYPE_REARVIEW)).isTrue();
        // TODO(b/179029031): Fix below test when the Surround View service is integrated into
        // CarEvsService.
        assertThat(mEvsManager.isSupported(CarEvsManager.SERVICE_TYPE_SURROUNDVIEW)).isFalse();
    }

    /**
     * Class that implements the listener interface and gets called back from
     * {@link android.car.evs.CarEvsManager.CarEvsStatusListener}.
     */
    private static final class EvsStatusListenerImpl implements CarEvsManager.CarEvsStatusListener {
        @Override
        public void onStatusChanged(CarEvsStatus status) {
            Log.i(TAG, "Received a notification of status changed to " + status.getState());
        }
    }

    /**
     * Class that implements the listener interface and gets called back from
     * {@link android.hardware.automotive.evs.IEvsCameraStream}.
     */
    private final class EvsStreamCallbackImpl implements CarEvsManager.CarEvsStreamCallback {
        @Override
        public void onStreamEvent(@CarEvsStreamEvent int event) {
            mLastStreamEvent = event;
            mStreamEventOccurred.release();
        }

        @Override
        public void onNewFrame(CarEvsBufferDescriptor buffer) {
            // Enqueues a new frame
            mReceivedBuffers.add(buffer);

            // Notifies a new frame's arrival
            mFrameReceivedSignal.release();
        }

        public boolean waitForStreamEvent(@CarEvsStreamEvent int expected) {
            while (mLastStreamEvent != expected) {
                try {
                    if (!mStreamEventOccurred.tryAcquire(STREAM_EVENT_TIMEOUT_SEC,
                            TimeUnit.SECONDS)) {
                        Log.e(TAG, "No stream event is received before the timer expired.");
                        return false;
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Current waiting thread is interrupted. ", e);
                    return false;
                }
            }

            return true;
        }
    }
}
