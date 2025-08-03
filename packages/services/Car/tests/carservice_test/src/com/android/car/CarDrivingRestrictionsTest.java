/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.car.Car;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarDrivingStateManager;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.hardware.automotive.vehicle.VehicleGear;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.car.hal.test.AidlVehiclePropValueBuilder;
import com.android.internal.annotations.GuardedBy;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class CarDrivingRestrictionsTest extends MockedCarTestBase {
    private static final String TAG = CarDrivingRestrictionsTest.class
            .getSimpleName();
    private CarDrivingStateManager mCarDrivingStateManager;
    private CarUxRestrictionsManager mCarUxRManager;
    // Currently set restrictions currently set in car_ux_restrictions_map.xml
    private static final int UX_RESTRICTIONS_MOVING = CarUxRestrictions.UX_RESTRICTIONS_NO_DIALPAD
            | CarUxRestrictions.UX_RESTRICTIONS_NO_FILTERING
            | CarUxRestrictions.UX_RESTRICTIONS_LIMIT_STRING_LENGTH
            | CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD
            | CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO
            | CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT
            | CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP
            | CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE;
    private static final int UX_RESTRICTIONS_IDLE = CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO;

    @Override
    protected void configureMockedHal() {
        addAidlProperty(VehicleProperty.PERF_VEHICLE_SPEED, AidlVehiclePropValueBuilder
                .newBuilder(VehicleProperty.PERF_VEHICLE_SPEED)
                .addFloatValues(0f)
                .build());
        addAidlProperty(VehicleProperty.PARKING_BRAKE_ON, AidlVehiclePropValueBuilder
                .newBuilder(VehicleProperty.PARKING_BRAKE_ON)
                .setBooleanValue(false)
                .build());
        addAidlProperty(VehicleProperty.GEAR_SELECTION, AidlVehiclePropValueBuilder
                .newBuilder(VehicleProperty.GEAR_SELECTION)
                .addIntValues(0)
                .build());
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mCarDrivingStateManager = (CarDrivingStateManager) getCar()
                .getCarManager(Car.CAR_DRIVING_STATE_SERVICE);
        mCarUxRManager = (CarUxRestrictionsManager) getCar()
                .getCarManager(Car.CAR_UX_RESTRICTION_SERVICE);
    }

    @Test
    public void testDrivingStateChange() throws InterruptedException {
        CarDrivingStateEvent drivingEvent;
        CarUxRestrictions restrictions;
        DrivingStateListener listener = new DrivingStateListener();
        mCarDrivingStateManager.registerListener(listener);
        mCarUxRManager.registerListener(listener);
        // With no gear value available, driving state should be unknown
        listener.reset();
        // Test Parked state and corresponding restrictions based on car_ux_restrictions_map.xml
        Log.d(TAG, "Injecting gear park");
        getAidlMockedVehicleHal().injectEvent(
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.GEAR_SELECTION)
                        .addIntValues(VehicleGear.GEAR_PARK)
                        .setTimestamp(SystemClock.elapsedRealtimeNanos())
                        .build());
        drivingEvent = listener.waitForDrivingStateChange();
        assertNotNull(drivingEvent);
        assertThat(drivingEvent.eventValue).isEqualTo(CarDrivingStateEvent.DRIVING_STATE_PARKED);

        Log.d(TAG, "Injecting speed 0");
        getAidlMockedVehicleHal().injectEvent(
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.PERF_VEHICLE_SPEED)
                        .addFloatValues(0.0f)
                        .setTimestamp(SystemClock.elapsedRealtimeNanos())
                        .build());

        // Switch gear to drive.  Driving state changes to Idling but the UX restrictions don't
        // change between parked and idling.
        listener.reset();
        Log.d(TAG, "Injecting gear drive");
        getAidlMockedVehicleHal().injectEvent(
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.GEAR_SELECTION)
                        .addIntValues(VehicleGear.GEAR_DRIVE)
                        .setTimestamp(SystemClock.elapsedRealtimeNanos())
                        .build());
        drivingEvent = listener.waitForDrivingStateChange();
        assertNotNull(drivingEvent);
        assertThat(drivingEvent.eventValue).isEqualTo(CarDrivingStateEvent.DRIVING_STATE_IDLING);

        // Test Moving state and corresponding restrictions based on car_ux_restrictions_map.xml
        listener.reset();
        Log.d(TAG, "Injecting speed 30");
        getAidlMockedVehicleHal().injectEvent(
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.PERF_VEHICLE_SPEED)
                        .addFloatValues(30.0f)
                        .setTimestamp(SystemClock.elapsedRealtimeNanos())
                        .build());
        drivingEvent = listener.waitForDrivingStateChange();
        assertNotNull(drivingEvent);
        assertThat(drivingEvent.eventValue).isEqualTo(CarDrivingStateEvent.DRIVING_STATE_MOVING);
        restrictions = listener.waitForUxRestrictionsChange(UX_RESTRICTIONS_MOVING);
        assertNotNull(restrictions);
        assertTrue(restrictions.isRequiresDistractionOptimization());
        assertThat(restrictions.getActiveRestrictions()).isEqualTo(UX_RESTRICTIONS_MOVING);

        // Test Idling state and corresponding restrictions based on car_ux_restrictions_map.xml
        listener.reset();
        Log.d(TAG, "Injecting speed 0");
        getAidlMockedVehicleHal().injectEvent(
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.PERF_VEHICLE_SPEED)
                        .addFloatValues(0.0f)
                        .setTimestamp(SystemClock.elapsedRealtimeNanos())
                        .build());
        drivingEvent = listener.waitForDrivingStateChange();
        assertNotNull(drivingEvent);
        assertThat(drivingEvent.eventValue).isEqualTo(CarDrivingStateEvent.DRIVING_STATE_IDLING);
        restrictions = listener.waitForUxRestrictionsChange(UX_RESTRICTIONS_IDLE);
        assertNotNull(restrictions);
        assertTrue(restrictions.isRequiresDistractionOptimization());
        assertThat(restrictions.getActiveRestrictions()).isEqualTo(UX_RESTRICTIONS_IDLE);

        // Test Moving state and corresponding restrictions when driving in reverse.
        Log.d(TAG, "Injecting gear reverse");
        getAidlMockedVehicleHal().injectEvent(
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.GEAR_SELECTION)
                        .addIntValues(VehicleGear.GEAR_REVERSE)
                        .setTimestamp(SystemClock.elapsedRealtimeNanos())
                        .build());

        listener.reset();
        Log.d(TAG, "Injecting speed -10");
        getAidlMockedVehicleHal().injectEvent(
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.PERF_VEHICLE_SPEED)
                        .addFloatValues(-10.0f)
                        .setTimestamp(SystemClock.elapsedRealtimeNanos())
                        .build());
        drivingEvent = listener.waitForDrivingStateChange();
        assertNotNull(drivingEvent);
        assertThat(drivingEvent.eventValue).isEqualTo(CarDrivingStateEvent.DRIVING_STATE_MOVING);
        restrictions = listener.waitForUxRestrictionsChange(UX_RESTRICTIONS_MOVING);
        assertNotNull(restrictions);
        assertTrue(restrictions.isRequiresDistractionOptimization());
        assertThat(restrictions.getActiveRestrictions()).isEqualTo(UX_RESTRICTIONS_MOVING);

        // Apply Parking brake.  Supported gears is not provided in this test and hence
        // Automatic transmission should be assumed and hence parking brake state should not
        // make a difference to the driving state.
        listener.reset();
        Log.d(TAG, "Injecting parking brake on");
        getAidlMockedVehicleHal().injectEvent(
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.PARKING_BRAKE_ON)
                        .setBooleanValue(true)
                        .setTimestamp(SystemClock.elapsedRealtimeNanos())
                        .build());
        drivingEvent = listener.waitForDrivingStateChange();
        assertNull(drivingEvent);

        mCarDrivingStateManager.unregisterListener();
        mCarUxRManager.unregisterListener();
    }

    @Test
    public void testDrivingStateChangeForMalformedInputs() throws InterruptedException {
        CarDrivingStateEvent drivingEvent;
        CarUxRestrictions restrictions;
        DrivingStateListener listener = new DrivingStateListener();
        mCarDrivingStateManager.registerListener(listener);
        mCarUxRManager.registerListener(listener);

        // Start with gear = park and speed = 0 to begin with a known state.
        listener.reset();
        Log.d(TAG, "Injecting gear park");
        getAidlMockedVehicleHal().injectEvent(
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.GEAR_SELECTION)
                        .addIntValues(VehicleGear.GEAR_PARK)
                        .setTimestamp(SystemClock.elapsedRealtimeNanos())
                        .build());
        drivingEvent = listener.waitForDrivingStateChange();
        assertNotNull(drivingEvent);
        assertThat(drivingEvent.eventValue).isEqualTo(CarDrivingStateEvent.DRIVING_STATE_PARKED);

        Log.d(TAG, "Injecting speed 0");
        getAidlMockedVehicleHal().injectEvent(
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.PERF_VEHICLE_SPEED)
                        .addFloatValues(0.0f)
                        .setTimestamp(SystemClock.elapsedRealtimeNanos())
                        .build());

        // Inject an invalid gear.  Since speed is still valid, idling will be the expected
        // driving state
        listener.reset();
        Log.d(TAG, "Injecting gear -1");
        getAidlMockedVehicleHal().injectEvent(
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.GEAR_SELECTION)
                        .addIntValues(-1)
                        .setTimestamp(SystemClock.elapsedRealtimeNanos())
                        .build());
        drivingEvent = listener.waitForDrivingStateChange();
        if (Build.IS_DEBUGGABLE) {
            // In userdebug build, payloadChecker in HAL drops the invalid event.
            assertNull(drivingEvent);
        } else {
            assertNotNull(drivingEvent);
            assertThat(drivingEvent.eventValue).isEqualTo(
                    CarDrivingStateEvent.DRIVING_STATE_IDLING);
        }
        // Now, send in an invalid speed value as well, now the driving state will be unknown and
        // the UX restrictions will change to fully restricted.
        listener.reset();
        Log.d(TAG, "Injecting speed -1");
        getAidlMockedVehicleHal().injectEvent(
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.PERF_VEHICLE_SPEED)
                        .addFloatValues(-1.0f)
                        .setTimestamp(SystemClock.elapsedRealtimeNanos())
                        .build());
        drivingEvent = listener.waitForDrivingStateChange();
        if (Build.IS_DEBUGGABLE) {
            // In userdebug build, payloadChecker in HAL drops the invalid event.
            assertNull(drivingEvent);
        } else {
            assertNotNull(drivingEvent);
            assertThat(drivingEvent.eventValue).isEqualTo(
                    CarDrivingStateEvent.DRIVING_STATE_UNKNOWN);
            restrictions = listener.waitForUxRestrictionsChange(
                    CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED);
            assertNotNull(restrictions);
            assertTrue(restrictions.isRequiresDistractionOptimization());
            assertThat(restrictions.getActiveRestrictions())
                    .isEqualTo(CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED);
        }
        mCarDrivingStateManager.unregisterListener();
        mCarUxRManager.unregisterListener();
    }

    /**
     * Callback function we register for driving state update notifications.
     */
    private static final class DrivingStateListener implements
            CarDrivingStateManager.CarDrivingStateEventListener,
            CarUxRestrictionsManager.OnUxRestrictionsChangedListener {
        private final Object mDrivingStateLock = new Object();
        @GuardedBy("mDrivingStateLock")
        private CarDrivingStateEvent mLastEvent = null;
        private final Object mUxRLock = new Object();
        @GuardedBy("mUxRLock")
        private CarUxRestrictions mLastRestrictions = null;

        void reset() {
            synchronized (mDrivingStateLock) {
                mLastEvent = null;
            }
            synchronized (mUxRLock) {
                mLastRestrictions = null;
            }
        }

        // Returns True to indicate receipt of a driving state event.  False indicates a timeout.
        CarDrivingStateEvent waitForDrivingStateChange() throws InterruptedException {
            long start = SystemClock.elapsedRealtime();

            synchronized (mDrivingStateLock) {
                while (mLastEvent == null
                        && (start + DEFAULT_WAIT_TIMEOUT_MS > SystemClock.elapsedRealtime())) {
                    mDrivingStateLock.wait(100L);
                }
                return mLastEvent;
            }
        }

        @Override
        public void onDrivingStateChanged(CarDrivingStateEvent event) {
            Log.d(TAG, "onDrivingStateChanged, event: " + event.eventValue);
            synchronized (mDrivingStateLock) {
                // We're going to hold a reference to this object
                mLastEvent = event;
                mDrivingStateLock.notifyAll();
            }
        }

        CarUxRestrictions waitForUxRestrictionsChange(int expectedRestrictions)
                throws InterruptedException {
            long start = SystemClock.elapsedRealtime();
            synchronized (mUxRLock) {
                while ((mLastRestrictions == null
                        || mLastRestrictions.getActiveRestrictions() != expectedRestrictions)
                            && (start + DEFAULT_WAIT_TIMEOUT_MS > SystemClock.elapsedRealtime())) {
                    mUxRLock.wait(100L);
                }
                return mLastRestrictions;
            }
        }

        @Override
        public void onUxRestrictionsChanged(CarUxRestrictions restrictions) {
            Log.d(TAG, "onUxRestrictionsChanged, restrictions: "
                    + restrictions.getActiveRestrictions());
            synchronized (mUxRLock) {
                mLastRestrictions = restrictions;
                mUxRLock.notifyAll();
            }
        }
    }
}
