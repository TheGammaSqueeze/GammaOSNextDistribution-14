/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.car.Car;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarVendorExtensionManager;
import android.hardware.automotive.vehicle.GetValueRequest;
import android.hardware.automotive.vehicle.GetValueRequests;
import android.hardware.automotive.vehicle.GetValueResult;
import android.hardware.automotive.vehicle.GetValueResults;
import android.hardware.automotive.vehicle.IVehicleCallback;
import android.hardware.automotive.vehicle.RawPropValues;
import android.hardware.automotive.vehicle.SetValueRequest;
import android.hardware.automotive.vehicle.SetValueRequests;
import android.hardware.automotive.vehicle.SetValueResult;
import android.hardware.automotive.vehicle.SetValueResults;
import android.hardware.automotive.vehicle.StatusCode;
import android.hardware.automotive.vehicle.VehicleArea;
import android.hardware.automotive.vehicle.VehicleAreaSeat;
import android.hardware.automotive.vehicle.VehiclePropConfig;
import android.hardware.automotive.vehicle.VehiclePropValue;
import android.hardware.automotive.vehicle.VehiclePropertyGroup;
import android.hardware.automotive.vehicle.VehiclePropertyType;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.car.hal.test.AidlMockedVehicleHal;
import com.android.car.hal.test.AidlVehiclePropConfigBuilder;
import com.android.car.internal.LargeParcelable;
import com.android.internal.annotations.GuardedBy;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Tests for {@link CarVendorExtensionManager}
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class CarVendorExtensionManagerTest extends MockedCarTestBase {

    private static final String TAG = CarVendorExtensionManager.class.getSimpleName();

    private static final int CUSTOM_GLOBAL_INT_PROP_ID =
            0x1 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.GLOBAL;

    private static final int CUSTOM_ZONED_FLOAT_PROP_ID =
            0x2 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.FLOAT | VehicleArea.SEAT;

    private static final int CUSTOM_BYTES_PROP_ID_1 =
            0x3 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.BYTES | VehicleArea.SEAT;

    private static final int CUSTOM_BYTES_PROP_ID_2 =
            0x4 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.BYTES | VehicleArea.GLOBAL;

    private static final int CUSTOM_STRING_PROP_ID =
            0x5 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.STRING | VehicleArea.GLOBAL;

    private static final int CUSTOM_GLOBAL_LONG_PROP_ID =
            0x6 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT64 | VehicleArea.GLOBAL;

    private static final int CUSTOM_INT_ARRAY_PROP_ID =
            0x7 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32_VEC | VehicleArea.GLOBAL;

    private static final int CUSTOM_LONG_ARRAY_PROP_ID =
            0x8 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT64_VEC | VehicleArea.GLOBAL;

    private static final float EPS = 1e-9f;
    private static final int MILLION = 1000 * 1000;

    private static final int MIN_PROP_INT32 = 0x0000005;
    private static final int MAX_PROP_INT32 = 0xDeadBee;

    private static final float MIN_PROP_FLOAT = 10.42f;
    private static final float MAX_PROP_FLOAT = 42.10f;
    private static final VehiclePropConfig mConfigs[] = new VehiclePropConfig[] {
            AidlVehiclePropConfigBuilder.newBuilder(CUSTOM_GLOBAL_INT_PROP_ID)
                    .addAreaConfig(0, MIN_PROP_INT32, MAX_PROP_INT32)
                    .build(),
            AidlVehiclePropConfigBuilder.newBuilder(CUSTOM_ZONED_FLOAT_PROP_ID)
                    .addAreaConfig(VehicleAreaSeat.ROW_1_LEFT | VehicleAreaSeat.ROW_1_RIGHT, 0, 0)
                    .addAreaConfig(VehicleAreaSeat.ROW_2_LEFT, MIN_PROP_FLOAT, MAX_PROP_FLOAT)
                    .addAreaConfig(VehicleAreaSeat.ROW_2_RIGHT, MIN_PROP_FLOAT, MAX_PROP_FLOAT)
                    .build(),
            AidlVehiclePropConfigBuilder.newBuilder(CUSTOM_BYTES_PROP_ID_1)
                    .addAreaConfig(VehicleAreaSeat.ROW_1_LEFT | VehicleAreaSeat.ROW_1_RIGHT, 0, 0)
                    .build(),
            AidlVehiclePropConfigBuilder.newBuilder(CUSTOM_BYTES_PROP_ID_2).build(),
            AidlVehiclePropConfigBuilder.newBuilder(CUSTOM_STRING_PROP_ID).build(),
            AidlVehiclePropConfigBuilder.newBuilder(CUSTOM_GLOBAL_LONG_PROP_ID).build(),
            AidlVehiclePropConfigBuilder.newBuilder(CUSTOM_INT_ARRAY_PROP_ID).build(),
            AidlVehiclePropConfigBuilder.newBuilder(CUSTOM_LONG_ARRAY_PROP_ID).build(),
    };

    private CarVendorExtensionManager mManager;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mManager = (CarVendorExtensionManager) getCar().getCarManager(Car.VENDOR_EXTENSION_SERVICE);
        assertNotNull(mManager);
    }

    @Test
    public void testPropertyList() throws Exception {
        List<CarPropertyConfig> configs = mManager.getProperties();
        assertEquals(mConfigs.length, configs.size());

        SparseArray<CarPropertyConfig> configById = new SparseArray<>(configs.size());
        for (CarPropertyConfig config : configs) {
            configById.put(config.getPropertyId(), config);
        }

        CarPropertyConfig prop1 = configById.get(CUSTOM_GLOBAL_INT_PROP_ID);
        assertNotNull(prop1);
        assertEquals(Integer.class, prop1.getPropertyType());
        assertEquals(MIN_PROP_INT32, prop1.getMinValue());
        assertEquals(MAX_PROP_INT32, prop1.getMaxValue());
    }

    @Test
    public void testIntGlobalProperty() throws Exception {
        final int value = 0xbeef;
        mManager.setGlobalProperty(Integer.class, CUSTOM_GLOBAL_INT_PROP_ID, value);
        int actualValue = mManager.getGlobalProperty(Integer.class, CUSTOM_GLOBAL_INT_PROP_ID);
        assertEquals(value, actualValue);
    }

    @Test
    public void testFloatZonedProperty() throws Exception {
        final float value = MIN_PROP_FLOAT + 1;
        mManager.setProperty(
                Float.class,
                CUSTOM_ZONED_FLOAT_PROP_ID,
                VehicleAreaSeat.ROW_2_RIGHT,
                value);

        float actualValue = mManager.getProperty(
                Float.class, CUSTOM_ZONED_FLOAT_PROP_ID, VehicleAreaSeat.ROW_2_RIGHT);
        assertEquals(value, actualValue, EPS);
    }

    @Test
    public void testByteArrayProperty() throws Exception {
        final byte[] expectedData = new byte[] { 1, 2, 3, 4, -1, 127, -127, 0 };

        // Write to CUSTOM_BYTES_PROP_ID_1 and read this value from CUSTOM_BYTES_PROP_ID_2
        mManager.setProperty(
                byte[].class,
                CUSTOM_BYTES_PROP_ID_1,
                VehicleAreaSeat.ROW_1_LEFT | VehicleAreaSeat.ROW_1_RIGHT,
                expectedData);

        byte[] actualData = mManager.getGlobalProperty(
                byte[].class,
                CUSTOM_BYTES_PROP_ID_2);

        assertEquals(Arrays.toString(expectedData), Arrays.toString(actualData));
    }

    @Test
    public void testLargeByteArrayProperty() throws Exception {
        // Allocate array of byte which is greater than binder transaction buffer limitation.
        byte[] expectedData = new byte[2 * MILLION];

        new Random(SystemClock.elapsedRealtimeNanos())
            .nextBytes(expectedData);

        // Write to CUSTOM_BYTES_PROP_ID_1 and read this value from CUSTOM_BYTES_PROP_ID_2
        mManager.setProperty(
                byte[].class,
                CUSTOM_BYTES_PROP_ID_1,
                VehicleAreaSeat.ROW_1_LEFT | VehicleAreaSeat.ROW_1_RIGHT,
                expectedData);

        byte[] actualData = mManager.getGlobalProperty(
                byte[].class,
                CUSTOM_BYTES_PROP_ID_2);

        Assert.assertArrayEquals(expectedData, actualData);
    }

    @Test
    public void testLargeStringProperty() throws Exception {
        // Allocate string which is greater than binder transaction buffer limitation.
        String expectedString = generateRandomString(2 * MILLION,
                "abcdefghijKLMNεὕρηκα!@#$%^&*()[]{}:\"\t\n\r!'");

        mManager.setGlobalProperty(
                String.class,
                CUSTOM_STRING_PROP_ID,
                expectedString);

        String actualString = mManager.getGlobalProperty(
                String.class,
                CUSTOM_STRING_PROP_ID);

        assertEquals(expectedString, actualString);
    }

    @Test
    public void testStringProperty() throws Exception {
        final String expectedString = "εὕρηκα!";  // Test some utf as well.

        mManager.setGlobalProperty(
                String.class,
                CUSTOM_STRING_PROP_ID,
                expectedString);

        String actualString = mManager.getGlobalProperty(
                String.class,
                CUSTOM_STRING_PROP_ID);

        assertEquals(expectedString, actualString);
    }

    @Test
    public void testLongProperty() throws Exception {
        final Long expectedValue = 100L;
        mManager.setGlobalProperty(
                Long.class,
                CUSTOM_GLOBAL_LONG_PROP_ID,
                expectedValue);

        Long actualValue = mManager.getGlobalProperty(
                Long.class,
                CUSTOM_GLOBAL_LONG_PROP_ID);

        assertEquals(expectedValue, actualValue);
    }

    @Test
    public void testIntArrayProperty() throws Exception {
        final Integer[] expectedIntArray = new Integer[]{1, 2, 3};

        mManager.setGlobalProperty(
                Integer[].class,
                CUSTOM_INT_ARRAY_PROP_ID,
                expectedIntArray);
        Integer[] actualIntArray = mManager.getGlobalProperty(
                Integer[].class,
                CUSTOM_INT_ARRAY_PROP_ID);
        Assert.assertArrayEquals(expectedIntArray, actualIntArray);
    }

    @Test
    public void testLongArrayProperty() throws Exception {
        final Long[] expectedLongArray = new Long[]{1L, 2L};

        mManager.setGlobalProperty(
                Long[].class,
                CUSTOM_LONG_ARRAY_PROP_ID,
                expectedLongArray);
        Long[] actualLongArray = mManager.getGlobalProperty(
                Long[].class,
                CUSTOM_LONG_ARRAY_PROP_ID);
        Assert.assertArrayEquals(expectedLongArray, actualLongArray);
    }

    private static String generateRandomString(int length, String allowedSymbols) {
        Random r = new Random(SystemClock.elapsedRealtimeNanos());
        StringBuilder sb = new StringBuilder(length);
        char[] chars = allowedSymbols.toCharArray();
        for (int i = 0; i < length; i++) {
            sb.append(chars[r.nextInt(chars.length)]);
        }
        return sb.toString();
    }

    @Override
    protected AidlMockedVehicleHal createAidlMockedVehicleHal() {
        AidlMockedVehicleHal hal = new VendorExtMockedVehicleHal();
        hal.addProperties(mConfigs);
        return hal;
    }

    private static class VendorExtMockedVehicleHal extends AidlMockedVehicleHal {
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private byte[] mBytes = null;
        @GuardedBy("mLock")
        private final SparseArray<VehiclePropValue> mValues = new SparseArray<>();

        @Override
        public void getValues(IVehicleCallback callback, GetValueRequests requests)
                throws RemoteException {
            requests = (GetValueRequests) LargeParcelable.reconstructStableAIDLParcelable(
                    requests, /* keepSharedMemory= */ false);

            int nonVendorCount = 0;
            int requestSize = requests.payloads.length;
            for (int i = 0; i < requestSize; i++) {
                GetValueRequest request = requests.payloads[i];
                if (!isVendorProperty(request.prop.prop)) {
                    GetValueRequests oneRequest = new GetValueRequests();
                    oneRequest.payloads = new GetValueRequest[]{request};
                    super.getValues(callback, oneRequest);
                    nonVendorCount++;
                }
            }

            if (nonVendorCount == requestSize) {
                return;
            }

            GetValueResults results = new GetValueResults();
            results.payloads = new GetValueResult[requestSize - nonVendorCount];

            synchronized (mLock) {
                for (int i = 0; i < requests.payloads.length; i++) {
                    GetValueRequest request = requests.payloads[i];
                    GetValueResult result = new GetValueResult();
                    result.requestId = request.requestId;
                    VehiclePropValue requestedPropValue = request.prop;
                    int propId = requestedPropValue.prop;
                    if (!isVendorProperty(propId)) {
                        continue;
                    }

                    VehiclePropValue resultValue = new VehiclePropValue();
                    resultValue.prop = propId;
                    resultValue.areaId = requestedPropValue.areaId;
                    resultValue.value = new RawPropValues();

                    if (propId == CUSTOM_BYTES_PROP_ID_2 && mBytes != null) {
                        Log.d(TAG, "Returning byte array property, value size " + mBytes.length);
                        resultValue.value.byteValues = mBytes.clone();
                    } else {
                        VehiclePropValue existingValue = mValues.get(propId);
                        if (existingValue != null) {
                            resultValue = existingValue;
                        } else {
                            resultValue = requestedPropValue;
                        }
                    }

                    result.prop = resultValue;
                    result.status = StatusCode.OK;
                    results.payloads[i] = result;
                }
            }

            results = (GetValueResults) LargeParcelable.toLargeParcelable(results, () -> {
                GetValueResults newResults = new GetValueResults();
                newResults.payloads = new GetValueResult[0];
                return newResults;
            });
            callback.onGetValues(results);
        }

        @Override
        public void setValues(IVehicleCallback callback, SetValueRequests requests)
                throws RemoteException {
            requests = (SetValueRequests) LargeParcelable.reconstructStableAIDLParcelable(
                    requests, /* keepSharedMemory= */ false);

            SetValueResults results = new SetValueResults();
            results.payloads = new SetValueResult[requests.payloads.length];

            synchronized (mLock) {
                for (int i = 0; i < requests.payloads.length; i++) {
                    SetValueRequest request = requests.payloads[i];
                    VehiclePropValue requestedPropValue = request.value;
                    if (requestedPropValue.prop == CUSTOM_BYTES_PROP_ID_1) {
                        mBytes = requestedPropValue.value.byteValues.clone();
                    }

                    mValues.put(requestedPropValue.prop, requestedPropValue);

                    SetValueResult result = new SetValueResult();
                    result.requestId = request.requestId;
                    result.status = StatusCode.OK;
                    results.payloads[i] = result;
                }
            }

            results = (SetValueResults) LargeParcelable.toLargeParcelable(results, () -> {
                SetValueResults newResults = new SetValueResults();
                newResults.payloads = new SetValueResult[0];
                return newResults;
            });
            callback.onSetValues(results);
        }

        private boolean isVendorProperty(int prop) {
            return VehiclePropertyGroup.VENDOR == (prop & VehiclePropertyGroup.VENDOR);
        }
    }
}
