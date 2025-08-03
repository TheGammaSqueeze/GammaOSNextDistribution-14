/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.car.test.mocks.JavaMockitoHelper.silentAwait;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static java.lang.Integer.toHexString;

import android.car.Car;
import android.car.diagnostic.CarDiagnosticEvent;
import android.car.diagnostic.CarDiagnosticEvent.CommonIgnitionMonitors;
import android.car.diagnostic.CarDiagnosticEvent.CompressionIgnitionMonitors;
import android.car.diagnostic.CarDiagnosticEvent.FuelSystemStatus;
import android.car.diagnostic.CarDiagnosticEvent.FuelType;
import android.car.diagnostic.CarDiagnosticEvent.SecondaryAirStatus;
import android.car.diagnostic.CarDiagnosticEvent.SparkIgnitionMonitors;
import android.car.diagnostic.CarDiagnosticManager;
import android.car.diagnostic.FloatSensorIndex;
import android.car.diagnostic.IntegerSensorIndex;
import android.car.hardware.property.VehicleHalStatusCode;
import android.hardware.automotive.vehicle.VehiclePropValue;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;

import com.android.car.hal.test.AidlMockedVehicleHal.ErrorCodeHandler;
import com.android.car.hal.test.AidlMockedVehicleHal.VehicleHalPropertyHandler;
import com.android.car.hal.test.AidlVehiclePropValueBuilder;
import com.android.car.hal.test.DiagnosticEventBuilder;
import com.android.car.hal.test.DiagnosticJson;
import com.android.internal.annotations.GuardedBy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/** Test the public entry points for the CarDiagnosticManager */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class CarDiagnosticManagerTest extends MockedCarTestBase {
    private static final String TAG = CarDiagnosticManagerTest.class.getSimpleName();

    private final DiagnosticEventBuilder mLiveFrameEventBuilder =
            new DiagnosticEventBuilder(VehicleProperty.OBD2_LIVE_FRAME);
    private final DiagnosticEventBuilder mFreezeFrameEventBuilder =
            new DiagnosticEventBuilder(VehicleProperty.OBD2_FREEZE_FRAME);
    private final FreezeFrameProperties mFreezeFrameProperties = new FreezeFrameProperties();

    private CarDiagnosticManager mCarDiagnosticManager;

    private static final String DTC = "P1010";
    private static final float EPS = 1e-9f;

    @Rule public TestName mTestName = new TestName();

    /**
     * This class is a central repository for freeze frame data. It ensures that timestamps and
     * events are kept in sync and provides a consistent access model for diagnostic properties.
     */
    class FreezeFrameProperties {
        private final HashMap<Long, VehiclePropValue> mEvents = new HashMap<>();

        public final FreezeFramePropertyHandler mFreezeFrameInfoHandler =
                new FreezeFrameInfoHandler();
        public final FreezeFramePropertyHandler mFreezeFrameHandler = new FreezeFrameHandler();
        public final FreezeFramePropertyHandler mFreezeFrameClearHandler =
                new FreezeFrameClearHandler();

        synchronized VehiclePropValue addNewEvent(DiagnosticEventBuilder builder) {
            long timestamp = SystemClock.elapsedRealtimeNanos();
            return addNewEvent(builder, timestamp);
        }

        synchronized VehiclePropValue addNewEvent(DiagnosticEventBuilder builder, long timestamp) {
            VehiclePropValue newEvent = builder.build(timestamp);
            mEvents.put(timestamp, newEvent);
            return newEvent;
        }

        synchronized VehiclePropValue removeEvent(long timestamp) {
            return mEvents.remove(timestamp);
        }

        synchronized void removeEvents() {
            mEvents.clear();
        }

        synchronized long[] getTimestamps() {
            return mEvents.keySet().stream().mapToLong(Long::longValue).toArray();
        }

        synchronized VehiclePropValue getEvent(long timestamp) {
            return mEvents.get(timestamp);
        }

        class FreezeFramePropertyHandler implements VehicleHalPropertyHandler {
            private boolean mSubscribed = false;
            private int mStatus;

            protected final int VEHICLE_PROPERTY;

            protected FreezeFramePropertyHandler(int propertyId) {
                VEHICLE_PROPERTY = propertyId;
            }

            public void setStatus(int status) {
                mStatus = status;
            }

            @Override
            public synchronized void onPropertySet(VehiclePropValue value) {
                if (mStatus != VehicleHalStatusCode.STATUS_OK) {
                    throw new ServiceSpecificException(mStatus);
                }
                assertEquals(VEHICLE_PROPERTY, value.prop);
            }

            @Override
            public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
                if (mStatus != VehicleHalStatusCode.STATUS_OK) {
                    throw new ServiceSpecificException(mStatus);
                }
                assertEquals(VEHICLE_PROPERTY, value.prop);
                return null;
            }

            @Override
            public synchronized void onPropertySubscribe(int property, float sampleRate) {
                assertEquals(VEHICLE_PROPERTY, property);
                mSubscribed = true;
            }

            @Override
            public synchronized void onPropertyUnsubscribe(int property) {
                assertEquals(VEHICLE_PROPERTY, property);
                if (!mSubscribed) {
                    throw new IllegalArgumentException(
                            "Property was not subscribed 0x" + toHexString(property));
                }
                mSubscribed = false;
            }
        }

        class FreezeFrameInfoHandler extends FreezeFramePropertyHandler {
            FreezeFrameInfoHandler() {
                super(VehicleProperty.OBD2_FREEZE_FRAME_INFO);
            }

            @Override
            public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
                super.onPropertyGet(value);
                AidlVehiclePropValueBuilder builder =
                        AidlVehiclePropValueBuilder.newBuilder(VEHICLE_PROPERTY);
                builder.addInt64Values(getTimestamps());
                return builder.build();
            }
        }

        class FreezeFrameHandler extends FreezeFramePropertyHandler {
            FreezeFrameHandler() {
                super(VehicleProperty.OBD2_FREEZE_FRAME);
            }

            @Override
            public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
                super.onPropertyGet(value);
                long timestamp = value.value.int64Values[0];
                return getEvent(timestamp);
            }
        }

        class FreezeFrameClearHandler extends FreezeFramePropertyHandler {
            FreezeFrameClearHandler() {
                super(VehicleProperty.OBD2_FREEZE_FRAME_CLEAR);
            }

            @Override
            public synchronized void onPropertySet(VehiclePropValue value) {
                super.onPropertySet(value);
                if (0 == value.value.int64Values.length) {
                    removeEvents();
                } else {
                    for (long timestamp : value.value.int64Values) {
                        removeEvent(timestamp);
                    }
                }
            }
        }
    }

    @Override
    protected void configureResourceOverrides(MockResources resources) {
        super.configureResourceOverrides(resources);
        resources.overrideResource(com.android.car.R.array.config_allowed_optional_car_features,
                new String[]{Car.DIAGNOSTIC_SERVICE});
    }

    @Override
    protected void configureMockedHal() {
        Collection<Integer> numVendorSensors = Arrays.asList(0, 0);
        Collection<Integer> selectiveClear = Collections.singletonList(1);
        Log.i(TAG, mTestName.getMethodName());
        String methodName = mTestName.getMethodName();
        ErrorCodeHandler handler = new ErrorCodeHandler();
        if (Objects.equals(methodName, "testInitialLiveFrameException_Invalid_Arg")) {
            handler.setStatus(VehicleHalStatusCode.STATUS_INVALID_ARG);
            addAidlProperty(VehicleProperty.OBD2_LIVE_FRAME, handler)
                    .setConfigArray(numVendorSensors);
        } else if (Objects.equals(methodName, "testInitialLiveFrameException_NOT_AVAILABLE")) {
            handler.setStatus(VehicleHalStatusCode.STATUS_NOT_AVAILABLE);
            addAidlProperty(VehicleProperty.OBD2_LIVE_FRAME, handler)
                    .setConfigArray(numVendorSensors);
        } else if (Objects.equals(methodName, "testInitialLiveFrameException_ACCESS_DENIED")) {
            handler.setStatus(VehicleHalStatusCode.STATUS_ACCESS_DENIED);
            addAidlProperty(VehicleProperty.OBD2_LIVE_FRAME, handler)
                    .setConfigArray(numVendorSensors);
        } else if (Objects.equals(methodName, "testInitialLiveFrameException_TRY_AGAIN")) {
            handler.setStatus(VehicleHalStatusCode.STATUS_TRY_AGAIN);
            addAidlProperty(VehicleProperty.OBD2_LIVE_FRAME, handler)
                    .setConfigArray(numVendorSensors);
        } else if (Objects.equals(methodName, "testInitialLiveFrameException_INTERNAL_ERROR")) {
            handler.setStatus(VehicleHalStatusCode.STATUS_INTERNAL_ERROR);
            addAidlProperty(VehicleProperty.OBD2_LIVE_FRAME, handler)
                    .setConfigArray(numVendorSensors);
        } else {
            addAidlProperty(VehicleProperty.OBD2_LIVE_FRAME, mLiveFrameEventBuilder.build())
                    .setConfigArray(numVendorSensors);
        }
        if (Objects.equals(methodName, "testInitialFreezeFrameInfoException_Invalid_Arg")) {
            mFreezeFrameProperties.mFreezeFrameInfoHandler.setStatus(
                    VehicleHalStatusCode.STATUS_INVALID_ARG);
        } else if (Objects.equals(methodName,
                "testInitialFreezeFrameInfoException_NOT_AVAILABLE")) {
            mFreezeFrameProperties.mFreezeFrameInfoHandler.setStatus(
                    VehicleHalStatusCode.STATUS_NOT_AVAILABLE);
        } else if (Objects.equals(methodName,
                "testInitialFreezeFrameInfoException_ACCESS_DENIED")) {
            mFreezeFrameProperties.mFreezeFrameInfoHandler.setStatus(
                    VehicleHalStatusCode.STATUS_ACCESS_DENIED);
        } else if (Objects.equals(methodName, "testInitialFreezeFrameInfoException_TRY_AGAIN")) {
            mFreezeFrameProperties.mFreezeFrameInfoHandler.setStatus(
                    VehicleHalStatusCode.STATUS_TRY_AGAIN);
        } else if (Objects.equals(methodName,
                "testInitialFreezeFrameInfoException_INTERNAL_ERROR")) {
            mFreezeFrameProperties.mFreezeFrameInfoHandler.setStatus(
                    VehicleHalStatusCode.STATUS_INTERNAL_ERROR);
        }
        addAidlProperty(
                VehicleProperty.OBD2_FREEZE_FRAME_INFO,
                mFreezeFrameProperties.mFreezeFrameInfoHandler);

        if (Objects.equals(methodName, "testInitialFreezeFrameException_Invalid_Arg")) {
            mFreezeFrameProperties.mFreezeFrameHandler.setStatus(
                    VehicleHalStatusCode.STATUS_INVALID_ARG);
        } else if (Objects.equals(methodName, "testInitialFreezeFrameException_NOT_AVAILABLE")) {
            mFreezeFrameProperties.mFreezeFrameHandler.setStatus(
                    VehicleHalStatusCode.STATUS_NOT_AVAILABLE);
        } else if (Objects.equals(methodName, "testInitialFreezeFrameException_ACCESS_DENIED")) {
            mFreezeFrameProperties.mFreezeFrameHandler.setStatus(
                    VehicleHalStatusCode.STATUS_ACCESS_DENIED);
        } else if (Objects.equals(methodName, "testInitialFreezeFrameException_TRY_AGAIN")) {
            mFreezeFrameProperties.mFreezeFrameHandler.setStatus(
                    VehicleHalStatusCode.STATUS_TRY_AGAIN);
        } else if (Objects.equals(methodName, "testInitialFreezeFrameException_INTERNAL_ERROR")) {
            mFreezeFrameProperties.mFreezeFrameHandler.setStatus(
                    VehicleHalStatusCode.STATUS_INTERNAL_ERROR);
        }
        addAidlProperty(VehicleProperty.OBD2_FREEZE_FRAME,
                mFreezeFrameProperties.mFreezeFrameHandler).setConfigArray(numVendorSensors);

        addAidlProperty(
                VehicleProperty.OBD2_FREEZE_FRAME_CLEAR,
                mFreezeFrameProperties.mFreezeFrameClearHandler)
                .setConfigArray(selectiveClear);
    }

    @Override
    public void setUp() throws Exception {
        mLiveFrameEventBuilder.addIntSensor(IntegerSensorIndex.AMBIENT_AIR_TEMPERATURE, 30);
        mLiveFrameEventBuilder.addIntSensor(
                IntegerSensorIndex.FUEL_SYSTEM_STATUS,
                FuelSystemStatus.OPEN_ENGINE_LOAD_OR_DECELERATION);
        mLiveFrameEventBuilder.addIntSensor(
                IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START, 5000);
        mLiveFrameEventBuilder.addIntSensor(IntegerSensorIndex.CONTROL_MODULE_VOLTAGE, 2);
        mLiveFrameEventBuilder.addFloatSensor(FloatSensorIndex.CALCULATED_ENGINE_LOAD, 0.125f);
        mLiveFrameEventBuilder.addFloatSensor(FloatSensorIndex.VEHICLE_SPEED, 12.5f);

        mFreezeFrameEventBuilder.addIntSensor(IntegerSensorIndex.AMBIENT_AIR_TEMPERATURE, 30);
        mFreezeFrameEventBuilder.addIntSensor(
                IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START, 5000);
        mFreezeFrameEventBuilder.addIntSensor(IntegerSensorIndex.CONTROL_MODULE_VOLTAGE, 2);
        mFreezeFrameEventBuilder.addFloatSensor(
                FloatSensorIndex.CALCULATED_ENGINE_LOAD, 0.125f);
        mFreezeFrameEventBuilder.addFloatSensor(FloatSensorIndex.VEHICLE_SPEED, 12.5f);
        mFreezeFrameEventBuilder.setDTC(DTC);

        super.setUp();

        Log.i(TAG, "attempting to get DIAGNOSTIC_SERVICE");
        mCarDiagnosticManager =
                (CarDiagnosticManager) getCar().getCarManager(Car.DIAGNOSTIC_SERVICE);
    }

    @Test public void testInitialLiveFrameException_Invalid_Arg() {
        // Do nothing, this test is to test the exception returned by VHAL during initialization
        // should not be propagated.
        assertThat(mCarDiagnosticManager.getLatestLiveFrame()).isNull();
    }

    @Test public void testInitialLiveFrameException_NOT_AVAILABLE() {
        // Do nothing, this test is to test the exception returned by VHAL during initialization
        // should not be propagated.
        assertThat(mCarDiagnosticManager.getLatestLiveFrame()).isNull();
    }

    @Test public void testInitialLiveFrameException_ACCESS_DENIED() {
        // Do nothing, this test is to test the exception returned by VHAL during initialization
        // should not be propagated.
        assertThat(mCarDiagnosticManager.getLatestLiveFrame()).isNull();
    }

    @Test public void testInitialLiveFrameException_TRY_AGAIN() {
        // Do nothing, this test is to test the exception returned by VHAL during initialization
        // should not be propagated.
        assertThat(mCarDiagnosticManager.getLatestLiveFrame()).isNull();
    }

    @Test public void testInitialLiveFrameException_INTERNAL_ERROR() {
        // Do nothing, this test is to test the exception returned by VHAL during initialization
        // should not be propagated.
        assertThat(mCarDiagnosticManager.getLatestLiveFrame()).isNull();
    }

    @Test public void testInitialFreezeFrameInfoException_Invalid_Arg() {
        // Do nothing, this test is to test the exception returned by VHAL during initialization
        // should not be propagated.
        assertEquals(mCarDiagnosticManager.getFreezeFrameTimestamps().length, 0);
    }

    @Test public void testInitialFreezeFrameInfoException_NOT_AVAILABLE() {
        // Do nothing, this test is to test the exception returned by VHAL during initialization
        // should not be propagated.
        assertEquals(mCarDiagnosticManager.getFreezeFrameTimestamps().length, 0);
    }

    @Test public void testInitialFreezeFrameInfoException_ACCESS_DENIED() {
        // Do nothing, this test is to test the exception returned by VHAL during initialization
        // should not be propagated.
        assertEquals(mCarDiagnosticManager.getFreezeFrameTimestamps().length, 0);
    }

    @Test public void testInitialFreezeFrameInfoException_TRY_AGAIN() {
        // Do nothing, this test is to test the exception returned by VHAL during initialization
        // should not be propagated.
        assertEquals(mCarDiagnosticManager.getFreezeFrameTimestamps().length, 0);
    }

    @Test public void testInitialFreezeFrameInfoException_INTERNAL_ERROR() {
        // Do nothing, this test is to test the exception returned by VHAL during initialization
        // should not be propagated.
        assertEquals(mCarDiagnosticManager.getFreezeFrameTimestamps().length, 0);
    }

    @Test public void testInitialFreezeFrameException_Invalid_Arg() {
        // Do nothing, this test is to test the exception returned by VHAL during initialization
        // should not be propagated.
        assertEquals(mCarDiagnosticManager.getFreezeFrameTimestamps().length, 0);
    }

    @Test public void testInitialFreezeFrameException_NOT_AVAILABLE() {
        // Do nothing, this test is to test the exception returned by VHAL during initialization
        // should not be propagated.
        assertEquals(mCarDiagnosticManager.getFreezeFrameTimestamps().length, 0);
    }

    @Test public void testInitialFreezeFrameException_ACCESS_DENIED() {
        // Do nothing, this test is to test the exception returned by VHAL during initialization
        // should not be propagated.
        assertEquals(mCarDiagnosticManager.getFreezeFrameTimestamps().length, 0);
    }

    @Test public void testInitialFreezeFrameException_TRY_AGAIN() {
        // Do nothing, this test is to test the exception returned by VHAL during initialization
        // should not be propagated.
        assertEquals(mCarDiagnosticManager.getFreezeFrameTimestamps().length, 0);
    }

    @Test public void testInitialFreezeFrameException_INTERNAL_ERROR() {
        // Do nothing, this test is to test the exception returned by VHAL during initialization
        // should not be propagated.
        assertEquals(mCarDiagnosticManager.getFreezeFrameTimestamps().length, 0);
    }


    @Test public void testLiveFrameRead() throws Exception {
        CarDiagnosticEvent liveFrame = mCarDiagnosticManager.getLatestLiveFrame();

        assertThat(liveFrame).isNotNull();
        assertTrue(liveFrame.isLiveFrame());
        assertFalse(liveFrame.isFreezeFrame());
        assertFalse(liveFrame.isEmptyFrame());

        assertEquals(
                5000,
                liveFrame
                        .getSystemIntegerSensor(IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START)
                        .intValue());
        assertEquals(
                30,
                liveFrame
                        .getSystemIntegerSensor(IntegerSensorIndex.AMBIENT_AIR_TEMPERATURE)
                        .intValue());
        assertEquals(
                2,
                liveFrame
                        .getSystemIntegerSensor(IntegerSensorIndex.CONTROL_MODULE_VOLTAGE)
                        .intValue());
        assertEquals(
                0.125f,
                liveFrame.getSystemFloatSensor(FloatSensorIndex.CALCULATED_ENGINE_LOAD),
                EPS);
        assertEquals(
                12.5f,
                liveFrame.getSystemFloatSensor(FloatSensorIndex.VEHICLE_SPEED),
                EPS);
    }

    @Test public void testLiveFrameEvent() throws Exception {
        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        listener.reset();
        long time = SystemClock.elapsedRealtimeNanos();
        mLiveFrameEventBuilder.addIntSensor(
                IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START, 5100);

        getAidlMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(time));
        CarDiagnosticEvent liveFrame = listener.waitForEvent(time);

        assertThat(liveFrame).isNotNull();

        assertEquals(
                5100,
                liveFrame
                        .getSystemIntegerSensor(IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START)
                        .intValue());
    }

    @Test public void testMissingSensorRead() throws Exception {
        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        getAidlMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build());
        CarDiagnosticEvent liveFrame = listener.waitForEvent();

        assertThat(liveFrame).isNotNull();

        assertThat(
                liveFrame.getSystemIntegerSensor(
                        IntegerSensorIndex.DRIVER_DEMAND_PERCENT_TORQUE)).isNull();
        assertEquals(
                -1,
                liveFrame.getSystemIntegerSensor(
                        IntegerSensorIndex.DRIVER_DEMAND_PERCENT_TORQUE, -1));

        assertThat(
                liveFrame.getSystemFloatSensor(FloatSensorIndex.OXYGEN_SENSOR6_VOLTAGE)).isNull();
        assertEquals(
                0.25f,
                liveFrame.getSystemFloatSensor(FloatSensorIndex.OXYGEN_SENSOR5_VOLTAGE, 0.25f), EPS);

        assertThat(liveFrame.getVendorIntegerSensor(IntegerSensorIndex.VENDOR_START)).isNull();
        assertEquals(-1, liveFrame.getVendorIntegerSensor(IntegerSensorIndex.VENDOR_START, -1));

        assertThat(liveFrame.getVendorFloatSensor(FloatSensorIndex.VENDOR_START)).isNull();
        assertEquals(
                0.25f, liveFrame.getVendorFloatSensor(FloatSensorIndex.VENDOR_START, 0.25f), EPS);
    }

    @Test public void testFuelSystemStatus() throws Exception {
        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        getAidlMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build());
        CarDiagnosticEvent liveFrame = listener.waitForEvent();

        assertThat(liveFrame).isNotNull();

        assertEquals(
                FuelSystemStatus.OPEN_ENGINE_LOAD_OR_DECELERATION,
                liveFrame
                        .getSystemIntegerSensor(IntegerSensorIndex.FUEL_SYSTEM_STATUS)
                        .intValue());
        assertEquals(
                FuelSystemStatus.OPEN_ENGINE_LOAD_OR_DECELERATION,
                liveFrame.getFuelSystemStatus().intValue());
    }

    @Test public void testSecondaryAirStatus() throws Exception {
        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        mLiveFrameEventBuilder.addIntSensor(
                IntegerSensorIndex.COMMANDED_SECONDARY_AIR_STATUS,
                SecondaryAirStatus.FROM_OUTSIDE_OR_OFF);
        long timestamp = SystemClock.elapsedRealtimeNanos();
        getAidlMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(timestamp));

        CarDiagnosticEvent liveFrame = listener.waitForEvent(timestamp);

        assertThat(liveFrame).isNotNull();

        assertEquals(
                SecondaryAirStatus.FROM_OUTSIDE_OR_OFF,
                liveFrame
                        .getSystemIntegerSensor(
                                IntegerSensorIndex.COMMANDED_SECONDARY_AIR_STATUS)
                        .intValue());
        assertEquals(
                SecondaryAirStatus.FROM_OUTSIDE_OR_OFF,
                liveFrame.getSecondaryAirStatus().intValue());
    }

    @Test public void testIgnitionMonitors() throws Exception {
        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        // cfr. CarDiagnosticEvent for the meaning of the several bits
        final int sparkMonitorsValue =
                0x1 | (0x1 << 2) | (0x1 << 3) | (0x1 << 6) | (0x1 << 10) | (0x1 << 11);

        final int compressionMonitorsValue =
                (0x1 << 2) | (0x1 << 3) | (0x1 << 6) | (0x1 << 12) | (0x1 << 13);

        mLiveFrameEventBuilder.addIntSensor(IntegerSensorIndex.IGNITION_MONITORS_SUPPORTED, 0);
        mLiveFrameEventBuilder.addIntSensor(
                IntegerSensorIndex.IGNITION_SPECIFIC_MONITORS, sparkMonitorsValue);

        long timestamp = SystemClock.elapsedRealtimeNanos();
        getAidlMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(timestamp));

        CarDiagnosticEvent liveFrame = listener.waitForEvent(timestamp);

        assertThat(liveFrame).isNotNull();

        CommonIgnitionMonitors commonIgnitionMonitors = liveFrame.getIgnitionMonitors();
        assertThat(commonIgnitionMonitors).isNotNull();
        assertTrue(commonIgnitionMonitors.components.available);
        assertFalse(commonIgnitionMonitors.components.incomplete);
        assertTrue(commonIgnitionMonitors.fuelSystem.available);
        assertTrue(commonIgnitionMonitors.fuelSystem.incomplete);
        assertFalse(commonIgnitionMonitors.misfire.available);
        assertFalse(commonIgnitionMonitors.misfire.incomplete);

        SparkIgnitionMonitors sparkIgnitionMonitors =
                commonIgnitionMonitors.asSparkIgnitionMonitors();
        assertThat(sparkIgnitionMonitors).isNotNull();
        assertThat(commonIgnitionMonitors.asCompressionIgnitionMonitors()).isNull();

        assertTrue(sparkIgnitionMonitors.EGR.available);
        assertFalse(sparkIgnitionMonitors.EGR.incomplete);
        assertFalse(sparkIgnitionMonitors.oxygenSensorHeater.available);
        assertFalse(sparkIgnitionMonitors.oxygenSensorHeater.incomplete);
        assertTrue(sparkIgnitionMonitors.oxygenSensor.available);
        assertTrue(sparkIgnitionMonitors.oxygenSensor.incomplete);
        assertFalse(sparkIgnitionMonitors.ACRefrigerant.available);
        assertFalse(sparkIgnitionMonitors.ACRefrigerant.incomplete);
        assertFalse(sparkIgnitionMonitors.secondaryAirSystem.available);
        assertFalse(sparkIgnitionMonitors.secondaryAirSystem.incomplete);
        assertFalse(sparkIgnitionMonitors.evaporativeSystem.available);
        assertFalse(sparkIgnitionMonitors.evaporativeSystem.incomplete);
        assertFalse(sparkIgnitionMonitors.heatedCatalyst.available);
        assertFalse(sparkIgnitionMonitors.heatedCatalyst.incomplete);
        assertFalse(sparkIgnitionMonitors.catalyst.available);
        assertFalse(sparkIgnitionMonitors.catalyst.incomplete);

        mLiveFrameEventBuilder.addIntSensor(IntegerSensorIndex.IGNITION_MONITORS_SUPPORTED, 1);
        mLiveFrameEventBuilder.addIntSensor(
                IntegerSensorIndex.IGNITION_SPECIFIC_MONITORS, compressionMonitorsValue);

        timestamp += 1000;
        getAidlMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(timestamp));

        liveFrame = listener.waitForEvent(timestamp);

        assertThat(liveFrame).isNotNull();
        assertEquals(timestamp, liveFrame.timestamp);

        commonIgnitionMonitors = liveFrame.getIgnitionMonitors();
        assertThat(commonIgnitionMonitors).isNotNull();
        assertFalse(commonIgnitionMonitors.components.available);
        assertFalse(commonIgnitionMonitors.components.incomplete);
        assertTrue(commonIgnitionMonitors.fuelSystem.available);
        assertTrue(commonIgnitionMonitors.fuelSystem.incomplete);
        assertFalse(commonIgnitionMonitors.misfire.available);
        assertFalse(commonIgnitionMonitors.misfire.incomplete);
        CompressionIgnitionMonitors compressionIgnitionMonitors =
                commonIgnitionMonitors.asCompressionIgnitionMonitors();
        assertThat(commonIgnitionMonitors.asSparkIgnitionMonitors()).isNull();
        assertThat(compressionIgnitionMonitors).isNotNull();

        assertTrue(compressionIgnitionMonitors.EGROrVVT.available);
        assertFalse(compressionIgnitionMonitors.EGROrVVT.incomplete);
        assertFalse(compressionIgnitionMonitors.PMFilter.available);
        assertFalse(compressionIgnitionMonitors.PMFilter.incomplete);
        assertFalse(compressionIgnitionMonitors.exhaustGasSensor.available);
        assertFalse(compressionIgnitionMonitors.exhaustGasSensor.incomplete);
        assertTrue(compressionIgnitionMonitors.boostPressure.available);
        assertTrue(compressionIgnitionMonitors.boostPressure.incomplete);
        assertFalse(compressionIgnitionMonitors.NOxSCR.available);
        assertFalse(compressionIgnitionMonitors.NOxSCR.incomplete);
        assertFalse(compressionIgnitionMonitors.NMHCCatalyst.available);
        assertFalse(compressionIgnitionMonitors.NMHCCatalyst.incomplete);
    }

    @Test
    @FlakyTest
    public void testFuelType() throws Exception {
        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        mLiveFrameEventBuilder.addIntSensor(
                IntegerSensorIndex.FUEL_TYPE, FuelType.BIFUEL_RUNNING_LPG);
        long timestamp = SystemClock.elapsedRealtimeNanos();
        getAidlMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(timestamp));

        CarDiagnosticEvent liveFrame = listener.waitForEvent(timestamp);

        assertThat(liveFrame).isNotNull();

        assertEquals(
                FuelType.BIFUEL_RUNNING_LPG,
                liveFrame.getSystemIntegerSensor(IntegerSensorIndex.FUEL_TYPE).intValue());
        assertEquals(FuelType.BIFUEL_RUNNING_LPG, liveFrame.getFuelType().intValue());
    }

    @Test public void testDiagnosticJson() throws Exception {
        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        mLiveFrameEventBuilder.addIntSensor(IntegerSensorIndex.ENGINE_OIL_TEMPERATURE, 74);
        mLiveFrameEventBuilder.addFloatSensor(FloatSensorIndex.OXYGEN_SENSOR1_VOLTAGE, 0.125f);

        long timestamp = SystemClock.elapsedRealtimeNanos();
        getAidlMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(timestamp));

        CarDiagnosticEvent liveFrame = listener.waitForEvent(timestamp);

        assertThat(liveFrame).isNotNull();

        assertEquals(
                74,
                liveFrame
                        .getSystemIntegerSensor(IntegerSensorIndex.ENGINE_OIL_TEMPERATURE)
                        .intValue());
        assertEquals(
                0.125f,
                liveFrame.getSystemFloatSensor(FloatSensorIndex.OXYGEN_SENSOR1_VOLTAGE),
                EPS);

        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(stringWriter);

        liveFrame.writeToJson(jsonWriter);
        jsonWriter.flush();

        StringReader stringReader = new StringReader(stringWriter.toString());
        JsonReader jsonReader = new JsonReader(stringReader);
        DiagnosticJson diagnosticJson = DiagnosticJson.build(jsonReader);

        assertEquals(
                74,
                diagnosticJson
                        .intValues
                        .get(IntegerSensorIndex.ENGINE_OIL_TEMPERATURE)
                        .intValue());
        assertEquals(
                0.125f,
                diagnosticJson.floatValues.get(FloatSensorIndex.OXYGEN_SENSOR1_VOLTAGE),
                EPS);
    }

    @Test
    public void testMultipleListeners() throws Exception {
        Listener listener1 = new Listener();
        Listener listener2 = new Listener();

        mCarDiagnosticManager.registerListener(
                listener1,
                CarDiagnosticManager.FRAME_TYPE_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);
        mCarDiagnosticManager.registerListener(
                listener2,
                CarDiagnosticManager.FRAME_TYPE_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        listener1.reset();
        listener2.reset();

        long time = SystemClock.elapsedRealtimeNanos();
        getAidlMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(time));
        CarDiagnosticEvent event1 = listener1.waitForEvent(time);
        assertThat(event1).isNotNull();
        CarDiagnosticEvent event2 = listener2.waitForEvent(time);
        assertThat(event2).isNotNull();

        assertTrue(event1.equals(event1));
        assertTrue(event2.equals(event2));
        assertTrue(event1.equals(event2));
        assertTrue(event2.equals(event1));

        assertTrue(event1.hashCode() == event1.hashCode());
        assertTrue(event1.hashCode() == event2.hashCode());

        assertEquals(
                5000,
                event1.getSystemIntegerSensor(IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START)
                        .intValue());
        assertEquals(
                5000,
                event2.getSystemIntegerSensor(IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START)
                        .intValue());

        listener1.reset();
        listener2.reset();

        mCarDiagnosticManager.unregisterListener(listener1);

        time += 1000;
        getAidlMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(time));
        assertThat(listener1.waitForEvent(time)).isNull();

        event2 = listener2.waitForEvent(time);
        assertThat(event2).isNotNull();

        assertTrue(event1.isEarlierThan(event2));
        assertFalse(event1.equals(event2));
        assertFalse(event2.equals(event1));

        assertEquals(
                5000,
                event2.getSystemIntegerSensor(IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START)
                        .intValue());
    }

    @Test
    public void testFreezeFrameEvent() throws Exception {
        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_FREEZE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        listener.reset();
        VehiclePropValue injectedEvent =
                mFreezeFrameProperties.addNewEvent(mFreezeFrameEventBuilder);
        getAidlMockedVehicleHal().injectEvent(injectedEvent);
        CarDiagnosticEvent freezeFrame = listener.waitForEvent(injectedEvent.timestamp);

        assertThat(freezeFrame).isNotNull();
        assertEquals(DTC, freezeFrame.dtc);

        mFreezeFrameEventBuilder.addIntSensor(
                IntegerSensorIndex.ABSOLUTE_BAROMETRIC_PRESSURE, 22);
        injectedEvent = mFreezeFrameProperties.addNewEvent(mFreezeFrameEventBuilder);
        getAidlMockedVehicleHal().injectEvent(injectedEvent);
        freezeFrame = listener.waitForEvent(injectedEvent.timestamp);

        assertThat(freezeFrame).isNotNull();
        assertThat(freezeFrame).isNotNull();
        assertFalse(freezeFrame.isLiveFrame());
        assertTrue(freezeFrame.isFreezeFrame());
        assertFalse(freezeFrame.isEmptyFrame());

        assertEquals(DTC, freezeFrame.dtc);
        assertEquals(
                22,
                freezeFrame
                        .getSystemIntegerSensor(IntegerSensorIndex.ABSOLUTE_BAROMETRIC_PRESSURE)
                        .intValue());
    }

    @Test
    public void testFreezeFrameTimestamps() throws Exception {
        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_FREEZE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        Set<Long> generatedTimestamps = new HashSet<>();

        VehiclePropValue injectedEvent =
                mFreezeFrameProperties.addNewEvent(mFreezeFrameEventBuilder);
        getAidlMockedVehicleHal().injectEvent(injectedEvent);
        generatedTimestamps.add(injectedEvent.timestamp);
        assertThat(listener.waitForEvent(injectedEvent.timestamp)).isNotNull();

        injectedEvent =
                mFreezeFrameProperties.addNewEvent(
                        mFreezeFrameEventBuilder, injectedEvent.timestamp + 1000);
        getAidlMockedVehicleHal().injectEvent(injectedEvent);
        generatedTimestamps.add(injectedEvent.timestamp);
        assertThat(listener.waitForEvent(injectedEvent.timestamp)).isNotNull();

        long[] acquiredTimestamps = mCarDiagnosticManager.getFreezeFrameTimestamps();
        assertEquals(generatedTimestamps.size(), acquiredTimestamps.length);
        for (long acquiredTimestamp : acquiredTimestamps) {
            assertTrue(generatedTimestamps.contains(acquiredTimestamp));
        }
    }

    @Test
    public void testClearFreezeFrameTimestamps() throws Exception {
        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_FREEZE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        VehiclePropValue injectedEvent =
                mFreezeFrameProperties.addNewEvent(mFreezeFrameEventBuilder);
        getAidlMockedVehicleHal().injectEvent(injectedEvent);
        assertThat(listener.waitForEvent(injectedEvent.timestamp)).isNotNull();

        assertThat(mCarDiagnosticManager.getFreezeFrame(injectedEvent.timestamp)).isNotNull();
        mCarDiagnosticManager.clearFreezeFrames(injectedEvent.timestamp);
        assertThat(mCarDiagnosticManager.getFreezeFrame(injectedEvent.timestamp)).isNull();
    }

    @Test
    public void testClearFreezeFrameTimestamps_ErrorCodeFromHal() {
        long timestamp = SystemClock.elapsedRealtimeNanos();
        // All the exceptions from the HAL should not be propagated up.
        mFreezeFrameProperties.mFreezeFrameClearHandler.setStatus(
                VehicleHalStatusCode.STATUS_TRY_AGAIN);
        mCarDiagnosticManager.clearFreezeFrames(timestamp);
        mFreezeFrameProperties.mFreezeFrameClearHandler.setStatus(
                VehicleHalStatusCode.STATUS_INVALID_ARG);
        mCarDiagnosticManager.clearFreezeFrames(timestamp);
        mFreezeFrameProperties.mFreezeFrameClearHandler.setStatus(
                VehicleHalStatusCode.STATUS_NOT_AVAILABLE);
        mCarDiagnosticManager.clearFreezeFrames(timestamp);
        mFreezeFrameProperties.mFreezeFrameClearHandler.setStatus(
                VehicleHalStatusCode.STATUS_ACCESS_DENIED);
        mCarDiagnosticManager.clearFreezeFrames(timestamp);
        mFreezeFrameProperties.mFreezeFrameClearHandler.setStatus(
                VehicleHalStatusCode.STATUS_INTERNAL_ERROR);
        mCarDiagnosticManager.clearFreezeFrames(timestamp);

        // Clear the status code.
        mFreezeFrameProperties.mFreezeFrameClearHandler.setStatus(VehicleHalStatusCode.STATUS_OK);
    }

    @Test
    public void testListenerUnregister() throws Exception {
        Listener listener1 = new Listener();
        Listener listener2 = new Listener();
        mCarDiagnosticManager.registerListener(
            listener1,
            CarDiagnosticManager.FRAME_TYPE_LIVE,
            android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);
        mCarDiagnosticManager.registerListener(
            listener1,
            CarDiagnosticManager.FRAME_TYPE_FREEZE,
            android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        mCarDiagnosticManager.unregisterListener(listener1);

        // you need a listener to be registered before MockedVehicleHal will actually dispatch
        // your events - add one, but do it *after* unregistering the first listener
        mCarDiagnosticManager.registerListener(
            listener2,
            CarDiagnosticManager.FRAME_TYPE_LIVE,
            android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);
        mCarDiagnosticManager.registerListener(
            listener2,
            CarDiagnosticManager.FRAME_TYPE_FREEZE,
            android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        VehiclePropValue injectedEvent =
            mFreezeFrameProperties.addNewEvent(mFreezeFrameEventBuilder);
        long time = injectedEvent.timestamp;
        getAidlMockedVehicleHal().injectEvent(injectedEvent);
        assertThat(listener1.waitForEvent(time)).isNull();
        assertThat(listener2.waitForEvent(time)).isNotNull();

        time += 1000;
        getAidlMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(time));
        assertThat(listener1.waitForEvent(time)).isNull();
        assertThat(listener2.waitForEvent(time)).isNotNull();
    }

    @Test
    public void testIsSupportedApiCalls() throws Exception {
        assertTrue(mCarDiagnosticManager.isLiveFrameSupported());
        assertTrue(mCarDiagnosticManager.isFreezeFrameNotificationSupported());
        assertTrue(mCarDiagnosticManager.isGetFreezeFrameSupported());
        assertTrue(mCarDiagnosticManager.isClearFreezeFramesSupported());
        assertTrue(mCarDiagnosticManager.isSelectiveClearFreezeFramesSupported());
    }

    private static final class Listener implements CarDiagnosticManager.OnDiagnosticEventListener {
        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private final List<CarDiagnosticEvent> mEvents = new ArrayList<>();
        @GuardedBy("mLock")
        private long mEventTimestamp;
        // By default we are not waiting for any event. When we start waiting for an event, a new
        // CountDownLatch with count 1 will be used.
        @GuardedBy("mLock")
        private CountDownLatch mCountDownLatch;

        @GuardedBy("mLock")
        private CarDiagnosticEvent checkReceivedEventsLocked(long eventTimeStamp) {
            for (int i = 0; i < mEvents.size(); i++) {
                if (mEventTimestamp == 0 || mEvents.get(i).timestamp == eventTimeStamp) {
                    return mEvents.get(i);
                }
            }
            return null;
        }

        void reset() {
            synchronized (mLock) {
                mEvents.clear();
            }
        }

        CarDiagnosticEvent waitForEvent() {
            return waitForEvent(0);
        }

        // Wait for a event with certain eventTimeStamp to happen.
        CarDiagnosticEvent waitForEvent(long eventTimeStamp) {
            CountDownLatch countDownlatch;
            synchronized (mLock) {
                mEventTimestamp = eventTimeStamp;
                mCountDownLatch = new CountDownLatch(1);
                CarDiagnosticEvent matchedEvent = checkReceivedEventsLocked(eventTimeStamp);
                if (matchedEvent != null) {
                    return matchedEvent;
                }
                countDownlatch = mCountDownLatch;
            }
            silentAwait(countDownlatch, DEFAULT_WAIT_TIMEOUT_MS);
            synchronized (mLock) {
                // Set the latch to null to indicate we are not waiting for an event any more.
                mCountDownLatch = null;
                return checkReceivedEventsLocked(eventTimeStamp);
            }
        }

        @Override
        public void onDiagnosticEvent(CarDiagnosticEvent event) {
            synchronized (mLock) {
                mEvents.add(event);
                if (mCountDownLatch == null) {
                    return;
                }
                // If we are currently waiting for an event, check whether we have a match.
                if (mEventTimestamp == 0 || event.timestamp == mEventTimestamp) {
                    mCountDownLatch.countDown();
                }
            }
        }
    }
}
