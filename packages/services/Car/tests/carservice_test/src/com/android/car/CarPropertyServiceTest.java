/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static java.lang.Integer.toHexString;

import android.car.hardware.property.ICarPropertyEventListener;
import android.hardware.automotive.vehicle.VehicleAreaWheel;
import android.hardware.automotive.vehicle.VehicleGear;
import android.hardware.automotive.vehicle.VehiclePropValue;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.automotive.vehicle.VehiclePropertyAccess;
import android.hardware.automotive.vehicle.VehiclePropertyChangeMode;
import android.os.IBinder;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.car.hal.test.AidlMockedVehicleHal.VehicleHalPropertyHandler;
import com.android.car.hal.test.AidlVehiclePropValueBuilder;
import com.android.car.internal.property.CarSubscription;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test for {@link com.android.car.CarPropertyService}
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class CarPropertyServiceTest extends MockedCarTestBase {
    private static final String TAG = CarPropertyServiceTest.class.getSimpleName();

    private final Map<Integer, VehiclePropValue> mDefaultPropValues = new HashMap<>();

    private CarPropertyService mService;

    @Mock
    private VehicleHalPropertyHandler mMockPropertyHandler;

    // This is a zoned continuous property with two areas used by subscription testing.
    private static final int TEST_SUBSCRIBE_PROP = VehicleProperty.TIRE_PRESSURE;

    public CarPropertyServiceTest() {
        // Unusual default values for the vehicle properties registered to listen via
        // CarPropertyService.registerListener. Unusual default values like the car is in motion,
        // night mode is on, or the car is low on fuel.
        mDefaultPropValues.put(VehicleProperty.GEAR_SELECTION,
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.GEAR_SELECTION)
                .addIntValues(VehicleGear.GEAR_DRIVE)
                .setTimestamp(SystemClock.elapsedRealtimeNanos()).build());
        mDefaultPropValues.put(VehicleProperty.PARKING_BRAKE_ON,
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.PARKING_BRAKE_ON)
                .setBooleanValue(false)
                .setTimestamp(SystemClock.elapsedRealtimeNanos()).build());
        mDefaultPropValues.put(VehicleProperty.PERF_VEHICLE_SPEED,
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.PERF_VEHICLE_SPEED)
                .addFloatValues(30.0f)
                .setTimestamp(SystemClock.elapsedRealtimeNanos()).build());
        mDefaultPropValues.put(VehicleProperty.NIGHT_MODE,
                AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.NIGHT_MODE)
                .setBooleanValue(true)
                .setTimestamp(SystemClock.elapsedRealtimeNanos()).build());
    }

    @Override
    protected void configureMockedHal() {
        PropertyHandler handler = new PropertyHandler();
        for (VehiclePropValue value : mDefaultPropValues.values()) {
            handler.onPropertySet(value);
            addAidlProperty(value.prop, handler);
        }
        addAidlProperty(TEST_SUBSCRIBE_PROP, mMockPropertyHandler)
                .setChangeMode(VehiclePropertyChangeMode.CONTINUOUS)
                .setAccess(VehiclePropertyAccess.READ)
                .addAreaConfig(VehicleAreaWheel.LEFT_FRONT)
                .addAreaConfig(VehicleAreaWheel.RIGHT_FRONT)
                .setMinSampleRate(0f)
                .setMaxSampleRate(100f);
    }

    @Override
    protected void spyOnBeforeCarImplInit(ICarImpl carImpl) {
        mService = CarLocalServices.getService(CarPropertyService.class);
        assertThat(mService).isNotNull();
        spyOn(mService);
    }

    @Test
    public void testMatchesDefaultPropertyValues() {
        Set<Integer> expectedPropIds = mDefaultPropValues.keySet();
        ArgumentCaptor<Integer> propIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mService, atLeast(expectedPropIds.size())).registerListener(
                propIdCaptor.capture(), anyFloat(), any());

        Set<Integer> actualPropIds = new HashSet<Integer>(propIdCaptor.getAllValues());
        assertWithMessage("Should assign default values for missing property IDs")
                .that(expectedPropIds).containsAtLeastElementsIn(actualPropIds.toArray());
        assertWithMessage("Missing registerListener for property IDs")
                .that(actualPropIds).containsAtLeastElementsIn(expectedPropIds.toArray());
    }

    @Test
    public void testregisterListener() {
        CarSubscription options = new CarSubscription();
        options.propertyId = TEST_SUBSCRIBE_PROP;
        options.areaIds = new int[]{
                android.car.VehicleAreaWheel.WHEEL_LEFT_FRONT,
                android.car.VehicleAreaWheel.WHEEL_RIGHT_FRONT
        };
        options.updateRateHz = 10f;
        ICarPropertyEventListener mockHandler = mock(ICarPropertyEventListener.class);
        IBinder mockBinder = mock(IBinder.class);
        when(mockHandler.asBinder()).thenReturn(mockBinder);
        // This is for initial get value requests.
        when(mMockPropertyHandler.onPropertyGet(any())).thenReturn(
                AidlVehiclePropValueBuilder.newBuilder(TEST_SUBSCRIBE_PROP)
                    .addFloatValues(1.23f)
                    .setTimestamp(SystemClock.elapsedRealtimeNanos()).build());

        mService.registerListener(List.of(options), mockHandler);

        ArgumentCaptor<int[]> areaIdsCaptor = ArgumentCaptor.forClass(int[].class);

        verify(mMockPropertyHandler).onPropertySubscribe(eq(TEST_SUBSCRIBE_PROP),
                areaIdsCaptor.capture(), eq(10f));
        assertWithMessage("Received expected areaIds for subscription")
                .that(areaIdsCaptor.getValue()).asList().containsExactly(
                        VehicleAreaWheel.LEFT_FRONT, VehicleAreaWheel.RIGHT_FRONT);
    }

    @Test
    public void testregisterListener_exceptionAndRetry() {
        CarSubscription options = new CarSubscription();
        options.propertyId = TEST_SUBSCRIBE_PROP;
        options.areaIds = new int[]{
                android.car.VehicleAreaWheel.WHEEL_LEFT_FRONT,
                android.car.VehicleAreaWheel.WHEEL_RIGHT_FRONT
        };
        options.updateRateHz = 10f;
        ICarPropertyEventListener mockHandler = mock(ICarPropertyEventListener.class);
        IBinder mockBinder = mock(IBinder.class);
        when(mockHandler.asBinder()).thenReturn(mockBinder);
        // This is for initial get value requests.
        when(mMockPropertyHandler.onPropertyGet(any())).thenReturn(
                AidlVehiclePropValueBuilder.newBuilder(TEST_SUBSCRIBE_PROP)
                    .addFloatValues(1.23f)
                    .setTimestamp(SystemClock.elapsedRealtimeNanos()).build());
        doThrow(new ServiceSpecificException(0)).when(mMockPropertyHandler).onPropertySubscribe(
                anyInt(), any(), anyFloat());

        assertThrows(ServiceSpecificException.class, () ->
                mService.registerListener(List.of(options), mockHandler));

        // Simulate the error goes away.
        doNothing().when(mMockPropertyHandler).onPropertySubscribe(anyInt(), any(), anyFloat());

        ArgumentCaptor<int[]> areaIdsCaptor = ArgumentCaptor.forClass(int[].class);

        // Retry.
        mService.registerListener(List.of(options), mockHandler);

        // The retry must reach VHAL.
        verify(mMockPropertyHandler, times(2)).onPropertySubscribe(eq(TEST_SUBSCRIBE_PROP),
                areaIdsCaptor.capture(), eq(10f));
        assertWithMessage("Received expected areaIds for subscription")
                .that(areaIdsCaptor.getValue()).asList().containsExactly(
                        VehicleAreaWheel.LEFT_FRONT, VehicleAreaWheel.RIGHT_FRONT);
    }

    @Test
    public void testUnregisterListener() {
        CarSubscription options = new CarSubscription();
        options.propertyId = TEST_SUBSCRIBE_PROP;
        options.areaIds = new int[]{
                android.car.VehicleAreaWheel.WHEEL_LEFT_FRONT,
                android.car.VehicleAreaWheel.WHEEL_RIGHT_FRONT
        };
        options.updateRateHz = 10f;
        ICarPropertyEventListener mockHandler = mock(ICarPropertyEventListener.class);
        IBinder mockBinder = mock(IBinder.class);
        when(mockHandler.asBinder()).thenReturn(mockBinder);
        // This is for initial get value requests.
        when(mMockPropertyHandler.onPropertyGet(any())).thenReturn(
                AidlVehiclePropValueBuilder.newBuilder(TEST_SUBSCRIBE_PROP)
                    .addFloatValues(1.23f)
                    .setTimestamp(SystemClock.elapsedRealtimeNanos()).build());

        mService.registerListener(List.of(options), mockHandler);

        verify(mMockPropertyHandler).onPropertySubscribe(eq(TEST_SUBSCRIBE_PROP), any(), eq(10f));

        mService.unregisterListener(TEST_SUBSCRIBE_PROP, mockHandler);

        verify(mMockPropertyHandler).onPropertyUnsubscribe(TEST_SUBSCRIBE_PROP);
    }

    @Test
    public void testUnregisterListener_exceptionAndRetry() {
        CarSubscription options = new CarSubscription();
        options.propertyId = TEST_SUBSCRIBE_PROP;
        options.areaIds = new int[]{
                android.car.VehicleAreaWheel.WHEEL_LEFT_FRONT,
                android.car.VehicleAreaWheel.WHEEL_RIGHT_FRONT
        };
        options.updateRateHz = 10f;
        ICarPropertyEventListener mockHandler = mock(ICarPropertyEventListener.class);
        IBinder mockBinder = mock(IBinder.class);
        when(mockHandler.asBinder()).thenReturn(mockBinder);
        // This is for initial get value requests.
        when(mMockPropertyHandler.onPropertyGet(any())).thenReturn(
                AidlVehiclePropValueBuilder.newBuilder(TEST_SUBSCRIBE_PROP)
                    .addFloatValues(1.23f)
                    .setTimestamp(SystemClock.elapsedRealtimeNanos()).build());

        mService.registerListener(List.of(options), mockHandler);

        verify(mMockPropertyHandler).onPropertySubscribe(eq(TEST_SUBSCRIBE_PROP), any(), eq(10f));

        doThrow(new ServiceSpecificException(0)).when(mMockPropertyHandler).onPropertyUnsubscribe(
                anyInt());

        assertThrows(ServiceSpecificException.class, () ->
                mService.unregisterListener(TEST_SUBSCRIBE_PROP, mockHandler));

        // Simulate the error goes away.
        doNothing().when(mMockPropertyHandler).onPropertyUnsubscribe(anyInt());

        // Retry.
        mService.unregisterListener(TEST_SUBSCRIBE_PROP, mockHandler);

        // The retry must reach VHAL.
        verify(mMockPropertyHandler, times(2)).onPropertyUnsubscribe(TEST_SUBSCRIBE_PROP);
    }

    private static final class PropertyHandler implements VehicleHalPropertyHandler {
        private final Map<Integer, VehiclePropValue> mMap = new HashMap<>();

        @Override
        public synchronized void onPropertySet(VehiclePropValue value) {
            mMap.put(value.prop, value);
        }

        @Override
        public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
            assertWithMessage("onPropertyGet missing property: %s", toHexString(value.prop))
                    .that(mMap).containsKey(value.prop);
            VehiclePropValue currentValue = mMap.get(value.prop);
            return currentValue != null ? currentValue : value;
        }

        @Override
        public synchronized void onPropertySubscribe(int property, float sampleRate) {
            assertWithMessage("onPropertySubscribe missing property: %s", toHexString(property))
                    .that(mMap).containsKey(property);
            Log.d(TAG, "onPropertySubscribe property "
                    + property + " sampleRate " + sampleRate);
        }

        @Override
        public synchronized void onPropertyUnsubscribe(int property) {
            assertWithMessage("onPropertyUnsubscribe missing property: %s", toHexString(property))
                    .that(mMap).containsKey(property);
            Log.d(TAG, "onPropertyUnSubscribe property " + property);
        }
    }
}
