/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.car.occupantawareness.IOccupantAwarenessEventCallback;
import android.car.occupantawareness.OccupantAwarenessDetection;
import android.car.occupantawareness.SystemStatusEvent;
import android.content.Context;
import android.hardware.automotive.occupant_awareness.IOccupantAwareness;
import android.hardware.automotive.occupant_awareness.IOccupantAwarenessClientCallback;
import android.hardware.automotive.occupant_awareness.OccupantAwarenessStatus;
import android.hardware.automotive.occupant_awareness.OccupantDetection;
import android.hardware.automotive.occupant_awareness.OccupantDetections;
import android.hardware.automotive.occupant_awareness.Role;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@MediumTest
public final class OccupantAwarenessServiceTest {
    private static final int TIMESTAMP = 1234; // In milliseconds.

    /**
     * Mock implementation of {@link
     * android.hardware.automotive.occupant_awareness.IOccupantAwareness} for testing the service
     * and manager.
     */
    private class MockOasHal
            extends android.hardware.automotive.occupant_awareness.IOccupantAwareness.Stub {
        private IOccupantAwarenessClientCallback mCallback;
        private boolean mGraphIsRunning;

        MockOasHal() {}

        /** Returns whether the mock graph is running. */
        public boolean isGraphRunning() {
            return mGraphIsRunning;
        }

        @Override
        public void getLatestDetection(OccupantDetections detections) {}

        @Override
        public void setCallback(IOccupantAwarenessClientCallback callback) {
            mCallback = callback;
        }

        @Override
        public @OccupantAwarenessStatus byte getState(int occupantRole, int detectionCapability) {
            return OccupantAwarenessStatus.READY;
        }

        @Override
        public @OccupantAwarenessStatus byte startDetection() {
            mGraphIsRunning = true;
            return OccupantAwarenessStatus.READY;
        }

        @Override
        public @OccupantAwarenessStatus byte stopDetection() {
            mGraphIsRunning = false;
            return OccupantAwarenessStatus.READY;
        }

        @Override
        public int getCapabilityForRole(@Role int occupantRole) {
            if (occupantRole == OccupantAwarenessDetection.VEHICLE_OCCUPANT_DRIVER) {
                return SystemStatusEvent.DETECTION_TYPE_PRESENCE
                        | SystemStatusEvent.DETECTION_TYPE_GAZE
                        | SystemStatusEvent.DETECTION_TYPE_DRIVER_MONITORING;
            } else if (occupantRole
                    == OccupantAwarenessDetection.VEHICLE_OCCUPANT_FRONT_PASSENGER) {
                return SystemStatusEvent.DETECTION_TYPE_PRESENCE;
            } else {
                return SystemStatusEvent.DETECTION_TYPE_NONE;
            }
        }

        /** Causes a status event to be generated with the specified flags. */
        public void fireStatusEvent(int detectionFlags, @OccupantAwarenessStatus byte status)
                throws RemoteException {
            if (mCallback != null) {
                mCallback.onSystemStatusChanged(detectionFlags, status);
            }
        }

        /** Causes a status event to be generated with the specified detection event data. */
        public void fireDetectionEvent(OccupantAwarenessDetection detectionEvent)
                throws RemoteException {
            if (mCallback != null) {
                OccupantDetection detection = new OccupantDetection();

                OccupantDetections detections = new OccupantDetections();
                detections.timeStampMillis = TIMESTAMP;
                detections.detections = new OccupantDetection[] {detection};
                mCallback.onDetectionEvent(detections);
            }
        }

        @Override
        public int getInterfaceVersion() {
            return this.VERSION;
        }

        @Override
        public String getInterfaceHash() {
            return this.HASH;
        }
    }

    private MockOasHal mMockHal;
    private com.android.car.OccupantAwarenessService mOasService;

    private CountDownLatch mStatusLatch;
    private SystemStatusEvent mSystemStatus;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        mMockHal = new MockOasHal();
        mOasService = new com.android.car.OccupantAwarenessService(context, mMockHal);
        mOasService.init();

        resetStatus();
    }

    @After
    public void tearDown() {
        mOasService.release();
    }

    @Test
    public void testWithNoRegisteredListeners() throws Exception {
        // Verify operation when no listeners are registered.
        mMockHal.fireStatusEvent(IOccupantAwareness.CAP_NONE, OccupantAwarenessStatus.READY);

        // Give some time for the asynchronous event to be propagated
        pauseForEventPropagation();

        // Nothing should have been received.
        assertThat(mSystemStatus).isNull();
    }

    @Test
    public void testRegisterEventListener_returnsSystemStatusReady() throws Exception {
        // Fire a status event and ensure it is received.
        // "Presence status is ready"
        IOccupantAwarenessEventCallback callback = registerCallbackToService();
        mMockHal.fireStatusEvent(
                IOccupantAwareness.CAP_PRESENCE_DETECTION, OccupantAwarenessStatus.READY);
        waitStatusReady();

        assertThat(mSystemStatus.detectionType)
                .isEqualTo(SystemStatusEvent.DETECTION_TYPE_PRESENCE);
        assertThat(mSystemStatus.systemStatus).isEqualTo(SystemStatusEvent.SYSTEM_STATUS_READY);

        mOasService.unregisterEventListener(callback);
    }

    @Test
    public void testRegisterEventListener_returnsSystemStatusFailure() throws Exception {
        // "Gaze status is failed"
        IOccupantAwarenessEventCallback callback = registerCallbackToService();
        mMockHal.fireStatusEvent(
                IOccupantAwareness.CAP_GAZE_DETECTION, OccupantAwarenessStatus.FAILURE);
        waitStatusReady();

        assertThat(mSystemStatus.detectionType).isEqualTo(SystemStatusEvent.DETECTION_TYPE_GAZE);
        assertThat(mSystemStatus.systemStatus)
                .isEqualTo(SystemStatusEvent.SYSTEM_STATUS_SYSTEM_FAILURE);

        mOasService.unregisterEventListener(callback);
    }

    @Test
    public void testRegisterEventListener_returnsSystemStatusNotReady() throws Exception {
        // "Driver monitoring status is not-ready"
        IOccupantAwarenessEventCallback callback = registerCallbackToService();
        mMockHal.fireStatusEvent(
                IOccupantAwareness.CAP_DRIVER_MONITORING_DETECTION,
                OccupantAwarenessStatus.NOT_INITIALIZED);
        waitStatusReady();

        assertThat(mSystemStatus.detectionType)
                .isEqualTo(SystemStatusEvent.DETECTION_TYPE_DRIVER_MONITORING);
        assertThat(mSystemStatus.systemStatus)
                .isEqualTo(SystemStatusEvent.SYSTEM_STATUS_NOT_READY);

        mOasService.unregisterEventListener(callback);
    }

    @Test
    public void testRegisterEventListener_returnsSystemStatusNotSupported() throws Exception {
        // "None is non-supported"
        IOccupantAwarenessEventCallback callback = registerCallbackToService();
        mMockHal.fireStatusEvent(
                IOccupantAwareness.CAP_NONE, OccupantAwarenessStatus.NOT_SUPPORTED);
        waitStatusReady();

        assertThat(mSystemStatus.detectionType).isEqualTo(SystemStatusEvent.DETECTION_TYPE_NONE);
        assertThat(mSystemStatus.systemStatus)
                .isEqualTo(SystemStatusEvent.SYSTEM_STATUS_NOT_SUPPORTED);

        mOasService.unregisterEventListener(callback);
    }

    @Test
    public void test_unregisteredListeners() throws Exception {
        // Verify that listeners are successfully unregistered.
        IOccupantAwarenessEventCallback callback = registerCallbackToService();

        // Unregister the registered listener.
        mOasService.unregisterEventListener(callback);

        // Fire some events.
        mMockHal.fireStatusEvent(IOccupantAwareness.CAP_NONE, OccupantAwarenessStatus.READY);
        mMockHal.fireStatusEvent(
                IOccupantAwareness.CAP_GAZE_DETECTION, OccupantAwarenessStatus.READY);
        mMockHal.fireStatusEvent(
                IOccupantAwareness.CAP_DRIVER_MONITORING_DETECTION, OccupantAwarenessStatus.READY);

        // Give some time for the asynchronous event to be propagated
        pauseForEventPropagation();

        // Nothing should have been received.
        assertThat(mSystemStatus).isNull();

        // Unregister a second time should log an error, but otherwise not cause any action.
        mOasService.unregisterEventListener(callback);
    }

    @Test
    public void testGetCapabilityForRole_returnsAggregatedDriverStatus() throws Exception {
        assertThat(mOasService.getCapabilityForRole(
                OccupantAwarenessDetection.VEHICLE_OCCUPANT_DRIVER))
                .isEqualTo(SystemStatusEvent.DETECTION_TYPE_PRESENCE
                        | SystemStatusEvent.DETECTION_TYPE_GAZE
                        | SystemStatusEvent.DETECTION_TYPE_DRIVER_MONITORING);
    }

    @Test
    public void testGetCapabilityForRole_returnsPresence() throws Exception {
        assertThat(mOasService.getCapabilityForRole(
                OccupantAwarenessDetection.VEHICLE_OCCUPANT_FRONT_PASSENGER))
                .isEqualTo(SystemStatusEvent.DETECTION_TYPE_PRESENCE);
    }

    @Test
    public void testGetCapabilityForRole_returnsNone_withRow2PassengerLeft() throws Exception {
        assertThat(mOasService.getCapabilityForRole(
                OccupantAwarenessDetection.VEHICLE_OCCUPANT_ROW_2_PASSENGER_LEFT))
                .isEqualTo(SystemStatusEvent.DETECTION_TYPE_NONE);
    }

    @Test
    public void testGetCapabilityForRole_returnsNone_withRow2PassengerCenter() throws Exception {
        assertThat(mOasService.getCapabilityForRole(
                OccupantAwarenessDetection.VEHICLE_OCCUPANT_ROW_2_PASSENGER_CENTER))
                .isEqualTo(SystemStatusEvent.DETECTION_TYPE_NONE);
    }

    @Test
    public void testGetCapabilityForRole_returnsNone_withRow2PassengerRight() throws Exception {
        assertThat(mOasService.getCapabilityForRole(
                OccupantAwarenessDetection.VEHICLE_OCCUPANT_ROW_2_PASSENGER_RIGHT))
                .isEqualTo(SystemStatusEvent.DETECTION_TYPE_NONE);
    }

    @Test
    public void testGetCapabilityForRole_returnsNone_withOccupantNone() throws Exception {
        assertThat(mOasService.getCapabilityForRole(
                OccupantAwarenessDetection.VEHICLE_OCCUPANT_NONE))
                .isEqualTo(SystemStatusEvent.DETECTION_TYPE_NONE);
    }

    @Test
    public void testRegisterEventListener_returnsGraphNotRunningOnStart()
            throws Exception {
        // Should be not running on start (no clients are yet connected).
        assertThat(mMockHal.isGraphRunning()).isFalse();
    }

    @Test
    public void testRegisterEventListener_returnsGraphRunningWithOneListener()
            throws Exception {
        // Connect a client. Graph should be running.
        IOccupantAwarenessEventCallback first_client = registerCallbackToService();

        assertThat(mMockHal.isGraphRunning()).isTrue();

        mOasService.unregisterEventListener(first_client);
    }

    @Test
    public void testRegisterEventListener_returnsGraphRunningWithTwoListeners()
            throws Exception {
        // Connect multiple (two) clients. Graph should be running.
        IOccupantAwarenessEventCallback first_client = registerCallbackToService();
        IOccupantAwarenessEventCallback second_client = registerCallbackToService();

        assertThat(mMockHal.isGraphRunning()).isTrue();

        mOasService.unregisterEventListener(first_client);
        mOasService.unregisterEventListener(second_client);
        assertThat(mMockHal.isGraphRunning()).isFalse();
    }

    @Test
    public void testUnregisterEventListener_returnsGraphNotRunningWithoutListeners()
            throws Exception {
        // Connect a client and disconnect it. Graph should be not running.
        IOccupantAwarenessEventCallback first_client = registerCallbackToService();
        mOasService.unregisterEventListener(first_client);

        assertThat(mMockHal.isGraphRunning()).isFalse();
    }

    @Test
    public void testUnregisterEventListener_returnsGraphRunningWithListeners()
            throws Exception {
        // Connect multipe (two) clients and disconnect one. Graph should be running.
        IOccupantAwarenessEventCallback first_client = registerCallbackToService();
        IOccupantAwarenessEventCallback second_client = registerCallbackToService();
        mOasService.unregisterEventListener(first_client);

        assertThat(mMockHal.isGraphRunning()).isTrue();

        mOasService.unregisterEventListener(second_client);
        assertThat(mMockHal.isGraphRunning()).isFalse();
    }

    @Test
    public void testUnregisterEventListener_returnsGraphNotRunningAfterAllListenersRemoved()
            throws Exception {
        // Connect multipe (two) clients and disconnect them all. Graph should not be running.
        IOccupantAwarenessEventCallback first_client = registerCallbackToService();
        IOccupantAwarenessEventCallback second_client = registerCallbackToService();
        mOasService.unregisterEventListener(first_client);
        mOasService.unregisterEventListener(second_client);

        assertThat(mMockHal.isGraphRunning()).isFalse();
    }

    /** Registers a listener to the service. */
    private IOccupantAwarenessEventCallback registerCallbackToService() {
        IOccupantAwarenessEventCallback callback =
                new IOccupantAwarenessEventCallback.Stub() {
                    @Override
                    public void onStatusChanged(SystemStatusEvent systemStatusEvent) {
                        mSystemStatus = systemStatusEvent;
                        mStatusLatch.countDown();
                    }

                    @Override
                    public void onDetectionEvent(OccupantAwarenessDetection detection) {
                    }
                };

        mOasService.registerEventListener(callback);
        return callback;
    }

    /** Resets futures for testing. */
    private void resetStatus() {
        mStatusLatch = new CountDownLatch(1);
        mSystemStatus = null;
    }

    private void waitStatusReady() throws Exception {
        mStatusLatch.await(1L, TimeUnit.SECONDS);
    }

    private void pauseForEventPropagation() {
        try {
            mStatusLatch.await(100L, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
        }
    }
}
