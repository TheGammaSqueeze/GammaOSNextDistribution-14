/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.junit.Assume.assumeFalse;

import android.car.Car;
import android.car.evs.CarEvsBufferDescriptor;
import android.car.evs.CarEvsManager;
import android.car.evs.CarEvsStatus;
import android.car.feature.Flags;
import android.car.test.PermissionsCheckerRule;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.ArraySet;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant Apps cannot get car related permissions")
public final class CarEvsManagerTest extends AbstractCarTestCase {

    private static final String TAG = CarEvsManagerTest.class.getSimpleName();

    // We'd expect that underlying stream runs @10fps at least.
    private static final int NUMBER_OF_FRAMES_TO_WAIT = 10;
    private static final int FRAME_TIMEOUT_MS = 1000;
    private static final int SMALL_NAP_MS = 500;
    private static final int STREAM_EVENT_TIMEOUT_SEC = 2;

    // Will return frame buffers in the order they arrived.
    private static final int INDEX_TO_FIRST_ELEM = 0;

    private static final int SERVICE_TYPES[] = {
        CarEvsManager.SERVICE_TYPE_REARVIEW, CarEvsManager.SERVICE_TYPE_SURROUNDVIEW,
        CarEvsManager.SERVICE_TYPE_FRONTVIEW, CarEvsManager.SERVICE_TYPE_LEFTVIEW,
        CarEvsManager.SERVICE_TYPE_RIGHTVIEW, CarEvsManager.SERVICE_TYPE_DRIVERVIEW,
        CarEvsManager.SERVICE_TYPE_FRONT_PASSENGERSVIEW,
        CarEvsManager.SERVICE_TYPE_REAR_PASSENGERSVIEW,
    };

    private final ArrayList<CarEvsBufferDescriptor> mReceivedBuffers = new ArrayList<>();
    private final ArraySet<Integer> mSupportedTypes = new ArraySet<>();
    private final ExecutorService mCallbackExecutor = Executors.newFixedThreadPool(1);
    private final Semaphore mFrameReceivedSignal = new Semaphore(0);
    private final Semaphore mStreamEventOccurred = new Semaphore(0);
    private final EvsStreamCallbackImpl mStreamCallback = new EvsStreamCallbackImpl();
    private final EvsStatusListenerImpl mStatusListener = new EvsStatusListenerImpl();

    private CarEvsManager mCarEvsManager;
    private int mLastStreamEvent;

    @Before
    public void setUp() throws Exception {
        Car car = getCar();
        assertTrue(car != null);

        // Confirm that CarEvsService is enabled on the target device.
        assumeTrue("CAR_EVS_SERVICE is not enabled.", car.isFeatureEnabled(Car.CAR_EVS_SERVICE));

        // Get the service manager.
        mCarEvsManager = getCar().getCarManager(CarEvsManager.class);
        assertTrue(mCarEvsManager != null);

        // Compile a list of service types supported on the target device; at least one service type
        // must be supported.
        for (int i : SERVICE_TYPES) {
            if (!mCarEvsManager.isSupported(i)) {
                continue;
            }

            mSupportedTypes.add(i);
        }
        assumeFalse("CarEvsService should support at least one type.", mSupportedTypes.isEmpty());

        // Drain all permits
        mFrameReceivedSignal.drainPermits();
        mStreamEventOccurred.drainPermits();

        // Ensure that no stream is active.
        mCarEvsManager.stopVideoStream();
    }

    @After
    public void cleanUp() {
        if (mCarEvsManager != null) {
            mCarEvsManager.stopVideoStream();
        }
    }

    @Test
    @EnsureHasPermission({Car.PERMISSION_USE_CAR_EVS_CAMERA, Car.PERMISSION_MONITOR_CAR_EVS_STATUS})
    @ApiTest(apis = {"android.car.evs.CarEvsManager#startVideoStream",
            "android.car.evs.CarEvsManager#stopVideoStream",
            "android.car.evs.CarEvsBufferDescriptor#getType"})
    @RequiresFlagsEnabled(
            {Flags.FLAG_CAR_EVS_STREAM_MANAGEMENT, Flags.FLAG_CAR_EVS_QUERY_SERVICE_STATUS})
    public void startAndStopSingleVideoStream() throws Exception {
        // Register a status listenr and start monitoring state changes of CarEvsService.
        mCarEvsManager.setStatusListener(mCallbackExecutor, mStatusListener);

        // Request to start a video stream.
        assertThat(
                mCarEvsManager.startVideoStream(CarEvsManager.SERVICE_TYPE_REARVIEW,
                        /* token= */ null, mCallbackExecutor, mStreamCallback)
        ).isEqualTo(CarEvsManager.ERROR_NONE);

        // Wait for a few frame buffers.
        for (int i = 0; i < NUMBER_OF_FRAMES_TO_WAIT; ++i) {
            assertThat(
                    mFrameReceivedSignal.tryAcquire(FRAME_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            ).isTrue();

            // Return a buffer immediately after confirming its origin.
            CarEvsBufferDescriptor b = mReceivedBuffers.get(INDEX_TO_FIRST_ELEM);
            mReceivedBuffers.remove(INDEX_TO_FIRST_ELEM);
            assertThat(b.getType()).isEqualTo(CarEvsManager.SERVICE_TYPE_REARVIEW);
            mCarEvsManager.returnFrameBuffer(b);
        }

        // Check a current status.
        CarEvsStatus status = mCarEvsManager.getCurrentStatus();
        assertThat(status).isNotNull();
        assertThat(status.getState()).isEqualTo(CarEvsManager.SERVICE_STATE_ACTIVE);
        assertThat(status.getServiceType()).isEqualTo(CarEvsManager.SERVICE_TYPE_REARVIEW);

        // Stop a video stream and wait for a confirmation.
        mCarEvsManager.stopVideoStream();

        SystemClock.sleep(SMALL_NAP_MS);
        assertThat(mStreamCallback.waitForStreamEvent(CarEvsManager.STREAM_EVENT_STREAM_STOPPED))
                .isTrue();

        // Unregister a listener.
        mCarEvsManager.clearStatusListener();
    }

    @Test
    @EnsureHasPermission({Car.PERMISSION_USE_CAR_EVS_CAMERA, Car.PERMISSION_MONITOR_CAR_EVS_STATUS})
    @ApiTest(apis = {"android.car.evs.CarEvsManager#startVideoStream",
            "android.car.evs.CarEvsManager#stopVideoStream",
            "android.car.evs.CarEvsBufferDescriptor#getType"})
    @RequiresFlagsEnabled(
            {Flags.FLAG_CAR_EVS_STREAM_MANAGEMENT, Flags.FLAG_CAR_EVS_QUERY_SERVICE_STATUS})
    public void startTwoVideoStreamsAndStopThemIndividually() throws Exception {
        assumeTrue("CAR_EVS_SERVICE supports only a single type.", mSupportedTypes.size() > 1);

        // Register a status listenr and start monitoring state changes of CarEvsService.
        mCarEvsManager.setStatusListener(mCallbackExecutor, mStatusListener);

        for (int i = 0; i < mSupportedTypes.size() - 1; i++) {
            // Request to start two video streams.
            int stream0 = mSupportedTypes.valueAt(i);
            int stream1 = mSupportedTypes.valueAt(i + 1);

            assertThat(
                    mCarEvsManager.startVideoStream(stream0, /* token= */ null,
                            mCallbackExecutor, mStreamCallback)
            ).isEqualTo(CarEvsManager.ERROR_NONE);

            assertThat(
                    mCarEvsManager.startVideoStream(stream1, /* token= */ null,
                            mCallbackExecutor, mStreamCallback)
            ).isEqualTo(CarEvsManager.ERROR_NONE);

            // Wait for a few frame buffers.
            for (int j = 0; j < NUMBER_OF_FRAMES_TO_WAIT; ++j) {
                assertThat(
                        mFrameReceivedSignal.tryAcquire(FRAME_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                ).isTrue();

                // Return a buffer immediately after confirming its origin.
                CarEvsBufferDescriptor b = mReceivedBuffers.get(INDEX_TO_FIRST_ELEM);
                mReceivedBuffers.remove(INDEX_TO_FIRST_ELEM);
                assertThat(b.getType() == stream0 || b.getType() == stream1).isTrue();
                mCarEvsManager.returnFrameBuffer(b);
            }

            // Check a current status of two active service types.
            assertThat(verifyServiceStatus(stream0, CarEvsManager.SERVICE_STATE_ACTIVE)).isTrue();
            assertThat(verifyServiceStatus(stream1, CarEvsManager.SERVICE_STATE_ACTIVE)).isTrue();

            // Stop a video stream and wait for a confirmation.
            mCarEvsManager.stopVideoStream(stream0);
            SystemClock.sleep(SMALL_NAP_MS);
            assertThat(mStreamCallback.waitForStreamEvent(
                    CarEvsManager.STREAM_EVENT_STREAM_STOPPED)).isTrue();

            // Check a current status of two active service types, again.
            assertThat(verifyServiceStatus(stream0, CarEvsManager.SERVICE_STATE_INACTIVE)).isTrue();
            assertThat(verifyServiceStatus(stream1, CarEvsManager.SERVICE_STATE_ACTIVE)).isTrue();

            // Stop another video stream and wait for a confirmation.
            mCarEvsManager.stopVideoStream(stream1);
            SystemClock.sleep(SMALL_NAP_MS);
            assertThat(mStreamCallback.waitForStreamEvent(
                    CarEvsManager.STREAM_EVENT_STREAM_STOPPED)).isTrue();

            // Check a current status of two active service types one last time. Both service types
            // must be now in the inactive state.
            assertThat(verifyServiceStatus(stream0, CarEvsManager.SERVICE_STATE_INACTIVE)).isTrue();
            assertThat(verifyServiceStatus(stream1, CarEvsManager.SERVICE_STATE_INACTIVE)).isTrue();
        }

        // Unregister a listener.
        mCarEvsManager.clearStatusListener();
    }

    /**
     * Class that implements the listener interface and gets called back from
     * {@link android.car.evs.CarEvsManager.CarEvsStatusListener}.
     */
    private final static class EvsStatusListenerImpl implements CarEvsManager.CarEvsStatusListener {
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
        public void onStreamEvent(int event) {
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

        public boolean waitForStreamEvent(int expected) {
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

    // Verify that a given service type is in an expected state.
    private boolean verifyServiceStatus(int type, int expected) {
        if (mCarEvsManager == null) {
            Log.e(TAG, "CarEvsManager instance is invalid.");
            return false;
        }

        CarEvsStatus status = mCarEvsManager.getCurrentStatus(type);
        assertThat(status).isNotNull();

        return type == status.getServiceType() && expected == status.getState();
    }
}
