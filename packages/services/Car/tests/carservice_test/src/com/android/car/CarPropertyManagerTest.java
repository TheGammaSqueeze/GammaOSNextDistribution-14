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

import static android.car.hardware.property.CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR;
import static android.car.hardware.property.CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE;
import static android.car.hardware.property.CarPropertyManager.STATUS_ERROR_TIMEOUT;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.car.Car;
import android.car.VehicleAreaType;
import android.car.VehicleAreaWheel;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarInternalErrorException;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.CarPropertyManager.GetPropertyRequest;
import android.car.hardware.property.CarPropertyManager.GetPropertyResult;
import android.car.hardware.property.CarPropertyManager.PropertyAsyncError;
import android.car.hardware.property.CarPropertyManager.SetPropertyRequest;
import android.car.hardware.property.CarPropertyManager.SetPropertyResult;
import android.car.hardware.property.PropertyAccessDeniedSecurityException;
import android.car.hardware.property.PropertyNotAvailableAndRetryException;
import android.car.hardware.property.PropertyNotAvailableException;
import android.car.hardware.property.VehicleHalStatusCode;
import android.hardware.automotive.vehicle.RawPropValues;
import android.hardware.automotive.vehicle.VehicleArea;
import android.hardware.automotive.vehicle.VehicleAreaSeat;
import android.hardware.automotive.vehicle.VehiclePropValue;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.automotive.vehicle.VehiclePropertyGroup;
import android.hardware.automotive.vehicle.VehiclePropertyStatus;
import android.hardware.automotive.vehicle.VehiclePropertyType;
import android.hardware.automotive.vehicle.VehicleVendorPermission;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.car.hal.test.AidlMockedVehicleHal.VehicleHalPropertyHandler;
import com.android.car.test.TestPropertyAsyncCallback;

import com.google.common.truth.Truth;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Test for {@link android.car.hardware.property.CarPropertyManager}
 *
 * Tests {@link android.car.hardware.property.CarPropertyManager} and the related car service
 * logic. Uses {@link com.android.car.hal.test.AidlMockedVehicleHal} as the mocked vehicle HAL
 * implementation.
 *
 * Caller should uses {@code addAidlProperty} in {@link #configureMockedHal} to configure the
 * supported proeprties.
 *
 * Caller could also use {@link MockedCarTestBase#getAidlMockedVehicleHal} to further configure
 * the vehicle HAL.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class CarPropertyManagerTest extends MockedCarTestBase {

    private static final String TAG = CarPropertyManagerTest.class.getSimpleName();

    private static final String TEST_VIN = "test_vin";

    /**
     * configArray[0], 1 indicates the property has a String value
     * configArray[1], 1 indicates the property has a Boolean value .
     * configArray[2], 1 indicates the property has an Integer value
     * configArray[3], the number indicates the size of Integer[]  in the property.
     * configArray[4], 1 indicates the property has a Long value .
     * configArray[5], the number indicates the size of Long[]  in the property.
     * configArray[6], 1 indicates the property has a Float value .
     * configArray[7], the number indicates the size of Float[] in the property.
     * configArray[8], the number indicates the size of byte[] in the property.
     */
    private static final java.util.Collection<Integer> CONFIG_ARRAY_1 =
            Arrays.asList(1, 0, 1, 0, 1, 0, 0, 0, 0);
    private static final java.util.Collection<Integer> CONFIG_ARRAY_2 =
            Arrays.asList(1, 1, 1, 0, 0, 0, 0, 2, 0);
    private static final java.util.Collection<Integer> CONFIG_ARRAY_3 =
            Arrays.asList(0, 1, 1, 0, 0, 0, 1, 0, 0);
    private static final Object[] EXPECTED_VALUE_1 = {"android", 1, 1L};
    private static final Object[] EXPECTED_VALUE_2 = {"android", true, 3, 1.1f, 2f};
    private static final Object[] EXPECTED_VALUE_3 = {true, 1, 2.2f};

    private static final int CUSTOM_SEAT_INT_PROP_1 =
            0x1201 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.SEAT;
    private static final int CUSTOM_SEAT_INT_PROP_2 =
            0x1202 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.SEAT;

    private static final int CUSTOM_SEAT_MIXED_PROP_ID_1 =
            0x1101 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.MIXED | VehicleArea.SEAT;
    private static final int CUSTOM_GLOBAL_MIXED_PROP_ID_2 =
            0x1102 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.MIXED | VehicleArea.GLOBAL;
    private static final int CUSTOM_GLOBAL_MIXED_PROP_ID_3 =
            0x1110 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.MIXED | VehicleArea.GLOBAL;

    private static final int CUSTOM_GLOBAL_INT_ARRAY_PROP =
            0x1103 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32_VEC
                    | VehicleArea.GLOBAL;
    private static final Integer[] FAKE_INT_ARRAY_VALUE = {1, 2};

    // A property that always returns null to simulate an unavailable property.
    private static final int NULL_VALUE_PROP =
            0x1108 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32
                    | VehicleArea.GLOBAL;

    // Vendor properties for testing exceptions.
    private static final int PROP_VALUE_STATUS_ERROR_INT_ARRAY =
            0x1104 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32_VEC
                    | VehicleArea.GLOBAL;
    private static final int PROP_VALUE_STATUS_ERROR_BOOLEAN =
            0x1105 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.BOOLEAN
                    | VehicleArea.GLOBAL;
    private static final int PROP_VALUE_STATUS_UNAVAILABLE_FLOAT =
            0x1106 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.FLOAT
                    | VehicleArea.GLOBAL;
    private static final int PROP_VALUE_STATUS_UNAVAILABLE_INT =
            0x1107 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32
                    | VehicleArea.GLOBAL;
    private static final int PROP_VALUE_STATUS_UNKNOWN_INT_ARRAY =
            0x1108 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32_VEC
                    | VehicleArea.GLOBAL;
    private static final int PROP_VALUE_STATUS_UNAVAILABLE_SEAT =
            0x1109 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32
                    | VehicleArea.SEAT;

    private static final int PROP_CAUSE_STATUS_CODE_TRY_AGAIN =
            0x1201 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.GLOBAL;
    private static final int PROP_CAUSE_STATUS_CODE_INVALID_ARG =
            0x1202 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.GLOBAL;
    private static final int PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE =
            0x1203 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.GLOBAL;
    private static final int PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR =
            0x1204 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.GLOBAL;
    private static final int PROP_CAUSE_STATUS_CODE_ACCESS_DENIED =
            0x1205 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.GLOBAL;
    private static final int PROP_CAUSE_STATUS_CODE_UNKNOWN =
            0x1206 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.GLOBAL;
    private static final int PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE_WITH_VENDOR_CODE =
            0x1207 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.GLOBAL;
    private static final int PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR_WITH_VENDOR_CODE =
            0x1208 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.GLOBAL;

    // Vendor properties for testing permissions
    private static final int PROP_WITH_READ_ONLY_PERMISSION =
            0x1301 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.GLOBAL;
    private static final int PROP_WITH_WRITE_ONLY_PERMISSION =
            0x1302 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.GLOBAL;
    private static final int SUPPORT_CUSTOM_PERMISSION = 287313669;
    private static final java.util.Collection<Integer> VENDOR_PERMISSION_CONFIG =
            Collections.unmodifiableList(
                    Arrays.asList(PROP_WITH_READ_ONLY_PERMISSION,
                            VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_1,
                            VehicleVendorPermission.PERMISSION_NOT_ACCESSIBLE,
                            PROP_WITH_WRITE_ONLY_PERMISSION,
                            VehicleVendorPermission.PERMISSION_NOT_ACCESSIBLE,
                            VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_1));

    private static final int PROP_ERROR_EVENT_NOT_AVAILABLE_DISABLED =
            0x1401 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 |  VehicleArea.GLOBAL;


    // Use FAKE_PROPERTY_ID to test api return null or throw exception.
    private static final int FAKE_PROPERTY_ID = 0x111;

    // This is a property returned by VHAL, but is unsupported in car service.
    // It must be filtered out at car service layer.
    private static final int PROP_UNSUPPORTED =
            0x0100 | VehiclePropertyGroup.SYSTEM | VehiclePropertyType.INT32 | VehicleArea.GLOBAL;

    private static final int DRIVER_SIDE_AREA_ID = VehicleAreaSeat.ROW_1_LEFT
            | VehicleAreaSeat.ROW_2_LEFT;
    private static final int PASSENGER_SIDE_AREA_ID = VehicleAreaSeat.ROW_1_RIGHT
            | VehicleAreaSeat.ROW_2_CENTER
            | VehicleAreaSeat.ROW_2_RIGHT;
    private static final float INIT_TEMP_VALUE = 16f;
    private static final float CHANGED_TEMP_VALUE = 20f;
    private static final int CALLBACK_SHORT_TIMEOUT_MS = 1000; // ms
    // Wait for CarPropertyManager register/unregister listener
    private static final long WAIT_FOR_NO_EVENTS = 50;
    private static final int VENDOR_CODE_FOR_NOT_AVAILABLE = 0x00ab;
    private static final int VENDOR_CODE_FOR_INTERNAL_ERROR = 0x0abc;
    private static final int NOT_AVAILABLE_WITH_VENDOR_CODE = VENDOR_CODE_FOR_NOT_AVAILABLE << 16
            | VehicleHalStatusCode.STATUS_NOT_AVAILABLE;
    private static final int INTERNAL_ERROR_WITH_VENDOR_CODE = VENDOR_CODE_FOR_INTERNAL_ERROR << 16
            | VehicleHalStatusCode.STATUS_INTERNAL_ERROR;

    private CarPropertyManager mManager;

    private final HandlerThread mHandlerThread = new HandlerThread(getClass().getSimpleName());
    private Handler mHandler;

    @Rule
    public TestName mTestName = new TestName();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        setUpTargetSdk();
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mManager = (CarPropertyManager) getCar().getCarManager(Car.PROPERTY_SERVICE);
        assertThat(mManager).isNotNull();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        mHandlerThread.quitSafely();
    }

    private void setUpTargetSdk() {
        getContext().getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.CUR_DEVELOPMENT;
        if (mTestName.getMethodName().endsWith("BeforeR")) {
            getContext().getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.Q;
        } else if (mTestName.getMethodName().endsWith("BeforeS")
                || mTestName.getMethodName().endsWith("AfterR")) {
            getContext().getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.R;
        } else if (mTestName.getMethodName().endsWith("AfterS")) {
            getContext().getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.S;
        } else if (mTestName.getMethodName().endsWith("AfterU")) {
            getContext().getApplicationInfo().targetSdkVersion =
                    Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
        }
    }

    @Test
    public void testMixedPropertyConfigs() {
        List<CarPropertyConfig> configs = mManager.getPropertyList();
        for (CarPropertyConfig cfg : configs) {
            switch (cfg.getPropertyId()) {
                case CUSTOM_SEAT_MIXED_PROP_ID_1:
                    assertThat(cfg.getConfigArray()).containsExactlyElementsIn(CONFIG_ARRAY_1)
                            .inOrder();
                    break;
                case CUSTOM_GLOBAL_MIXED_PROP_ID_2:
                    assertThat(cfg.getConfigArray()).containsExactlyElementsIn(CONFIG_ARRAY_2)
                            .inOrder();
                    break;
                case CUSTOM_GLOBAL_MIXED_PROP_ID_3:
                    assertThat(cfg.getConfigArray()).containsExactlyElementsIn(CONFIG_ARRAY_3)
                            .inOrder();
                    break;
                case VehiclePropertyIds.HVAC_TEMPERATURE_SET:
                case PROP_CAUSE_STATUS_CODE_ACCESS_DENIED:
                case PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR:
                case PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR_WITH_VENDOR_CODE:
                case PROP_CAUSE_STATUS_CODE_TRY_AGAIN:
                case PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE:
                case PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE_WITH_VENDOR_CODE:
                case PROP_CAUSE_STATUS_CODE_INVALID_ARG:
                case PROP_CAUSE_STATUS_CODE_UNKNOWN:
                case CUSTOM_SEAT_INT_PROP_1:
                case CUSTOM_SEAT_INT_PROP_2:
                case CUSTOM_GLOBAL_INT_ARRAY_PROP:
                case PROP_VALUE_STATUS_ERROR_INT_ARRAY:
                case PROP_VALUE_STATUS_UNKNOWN_INT_ARRAY:
                case PROP_VALUE_STATUS_ERROR_BOOLEAN:
                case PROP_VALUE_STATUS_UNAVAILABLE_INT:
                case PROP_VALUE_STATUS_UNAVAILABLE_FLOAT:
                case PROP_VALUE_STATUS_UNAVAILABLE_SEAT:
                case NULL_VALUE_PROP:
                case SUPPORT_CUSTOM_PERMISSION:
                case PROP_WITH_READ_ONLY_PERMISSION:
                case PROP_WITH_WRITE_ONLY_PERMISSION:
                case VehiclePropertyIds.INFO_VIN:
                case VehiclePropertyIds.TIRE_PRESSURE:
                case VehiclePropertyIds.FUEL_DOOR_OPEN:
                case VehiclePropertyIds.EPOCH_TIME:
                case PROP_ERROR_EVENT_NOT_AVAILABLE_DISABLED:
                case VehiclePropertyIds.DISTANCE_DISPLAY_UNITS:
                    break;
                default:
                    Assert.fail("Unexpected CarPropertyConfig: " + cfg);
            }
        }
    }

    @Test
    public void testGetMixTypeProperty() {
        mManager.setProperty(Object[].class, CUSTOM_SEAT_MIXED_PROP_ID_1,
                DRIVER_SIDE_AREA_ID, EXPECTED_VALUE_1);
        CarPropertyValue<Object[]> result = mManager.getProperty(
                CUSTOM_SEAT_MIXED_PROP_ID_1, DRIVER_SIDE_AREA_ID);
        assertThat(result.getValue()).isEqualTo(EXPECTED_VALUE_1);

        mManager.setProperty(Object[].class, CUSTOM_GLOBAL_MIXED_PROP_ID_2,
                0, EXPECTED_VALUE_2);
        result = mManager.getProperty(
                CUSTOM_GLOBAL_MIXED_PROP_ID_2, 0);
        assertThat(result.getValue()).isEqualTo(EXPECTED_VALUE_2);

        mManager.setProperty(Object[].class, CUSTOM_GLOBAL_MIXED_PROP_ID_3,
                0, EXPECTED_VALUE_3);
        result = mManager.getProperty(
                CUSTOM_GLOBAL_MIXED_PROP_ID_3, 0);
        assertThat(result.getValue()).isEqualTo(EXPECTED_VALUE_3);
    }

    /**
     * Test {@link android.car.hardware.property.CarPropertyManager#getIntArrayProperty(int, int)}
     */
    @Test
    public void testGetIntArrayProperty() {
        mManager.setProperty(Integer[].class, CUSTOM_GLOBAL_INT_ARRAY_PROP, 0,
                FAKE_INT_ARRAY_VALUE);

        int[] result = mManager.getIntArrayProperty(CUSTOM_GLOBAL_INT_ARRAY_PROP, 0);
        assertThat(result).asList().containsExactlyElementsIn(FAKE_INT_ARRAY_VALUE);
    }

    /**
     * Test {@link CarPropertyManager#getIntArrayProperty(int, int)} when vhal returns a value with
     * error status before S.
     */
    @Test
    public void testGetIntArrayPropertyWithErrorStatusBeforeS() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isLessThan(Build.VERSION_CODES.S);
        assertThat(mManager.getIntArrayProperty(PROP_VALUE_STATUS_ERROR_INT_ARRAY, 0)).isEqualTo(
                new int[0]);
    }

    /**
     * Test {@link CarPropertyManager#getIntArrayProperty(int, int)} when vhal returns a value with
     * error status equal or after S.
     */
    @Test
    public void testGetIntArrayPropertyWithErrorStatusEqualAfterS() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isAtLeast(Build.VERSION_CODES.S);
        assertThrows(CarInternalErrorException.class,
                () -> mManager.getIntArrayProperty(PROP_VALUE_STATUS_ERROR_INT_ARRAY, 0));
    }

    /**
     * Test {@link CarPropertyManager#getIntArrayProperty(int, int)} when vhal returns a value with
     * unknown status before S.
     */
    @Test
    public void testGetIntArrayPropertyWithUnknownStatusBeforeS() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isLessThan(Build.VERSION_CODES.S);
        assertThat(mManager.getIntArrayProperty(PROP_VALUE_STATUS_UNKNOWN_INT_ARRAY, 0)).isEqualTo(
                new int[0]);
    }

    /**
     * Test {@link CarPropertyManager#getIntArrayProperty(int, int)} when vhal returns a value with
     * unknown status equal or after S.
     */
    @Test
    public void testGetIntArrayPropertyWithUnknownStatusEqualAfterS() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isAtLeast(Build.VERSION_CODES.S);
        assertThrows(CarInternalErrorException.class,
                () -> mManager.getIntArrayProperty(PROP_VALUE_STATUS_UNKNOWN_INT_ARRAY, 0));
    }

    /**
     * Test {@link CarPropertyManager#getIntProperty(int, int)} when vhal returns a value with
     * unavailable status before S.
     */
    @Test
    public void testGetIntPropertyWithUnavailableStatusBeforeS() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isLessThan(Build.VERSION_CODES.S);
        assertThat(
                mManager.getIntProperty(PROP_VALUE_STATUS_UNAVAILABLE_INT, 0)).isEqualTo(0);
    }

    /**
     * Test {@link CarPropertyManager#getIntProperty(int, int)} when vhal returns a value with
     * unavailable status equal or after R.
     */
    @Test
    public void testGetIntPropertyWithUnavailableStatusEqualAfterS() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isAtLeast(Build.VERSION_CODES.R);
        assertThrows(PropertyNotAvailableException.class,
                () -> mManager.getIntProperty(PROP_VALUE_STATUS_UNAVAILABLE_INT, 0));
    }

    /**
     * Test {@link CarPropertyManager#getBooleanProperty(int, int)} when vhal returns a value with
     * error status before R.
     */
    @Test
    public void testGetBooleanPropertyWithErrorStatusBeforeS() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isLessThan(Build.VERSION_CODES.S);
        assertThat(mManager.getBooleanProperty(PROP_VALUE_STATUS_ERROR_BOOLEAN, 0)).isEqualTo(
                false);
    }

    /**
     * Test {@link CarPropertyManager#getBooleanProperty(int, int)} when vhal returns a value with
     * error status equal or after R.
     */
    @Test
    public void testGetBooleanPropertyWithErrorStatusEqualAfterS() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isAtLeast(Build.VERSION_CODES.R);
        assertThrows(CarInternalErrorException.class,
                () -> mManager.getBooleanProperty(PROP_VALUE_STATUS_ERROR_BOOLEAN, 0));
    }

    /**
     * Test {@link CarPropertyManager#getFloatProperty(int, int)} when vhal returns a value with
     * unavailable status before S.
     */
    @Test
    public void testGetFloatPropertyWithUnavailableStatusBeforeS() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isLessThan(Build.VERSION_CODES.S);
        assertThat(mManager.getFloatProperty(PROP_VALUE_STATUS_UNAVAILABLE_FLOAT, 0)).isEqualTo(0f);
    }

    /**
     * Test {@link CarPropertyManager#getFloatProperty(int, int)} when vhal returns a value with
     * unavailable status equal or after R.
     */
    @Test
    public void testGetFloatPropertyWithUnavailableStatusEqualAfterS() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isAtLeast(Build.VERSION_CODES.R);
        assertThrows(PropertyNotAvailableException.class,
                () -> mManager.getFloatProperty(PROP_VALUE_STATUS_UNAVAILABLE_FLOAT, 0));
    }

    /**
     * Test {@link CarPropertyManager#getProperty(Class, int, int)}
     */
    @Test
    public void testGetPropertyWithClass() {
        mManager.setProperty(Integer[].class, CUSTOM_GLOBAL_INT_ARRAY_PROP, 0,
                FAKE_INT_ARRAY_VALUE);

        CarPropertyValue<Integer[]> result = mManager.getProperty(Integer[].class,
                CUSTOM_GLOBAL_INT_ARRAY_PROP, 0);
        assertThat(result.getValue()).asList().containsExactlyElementsIn(FAKE_INT_ARRAY_VALUE);
    }

    /**
     * Test {@link CarPropertyManager#isPropertyAvailable(int, int)}
     */
    @Test
    public void testIsPropertyAvailable() {
        assertThat(mManager.isPropertyAvailable(CUSTOM_GLOBAL_INT_ARRAY_PROP, 0)).isFalse();

        mManager.setProperty(Integer[].class, CUSTOM_GLOBAL_INT_ARRAY_PROP, 0,
                FAKE_INT_ARRAY_VALUE);

        assertThat(mManager.isPropertyAvailable(CUSTOM_GLOBAL_INT_ARRAY_PROP, 0)).isTrue();
    }

    /**
     * Test {@link CarPropertyManager#getWritePermission(int)}
     * and {@link CarPropertyManager#getWritePermission(int)}
     */
    @Test
    public void testGetPermission() {
        String hvacReadPermission = mManager.getReadPermission(
                VehiclePropertyIds.HVAC_TEMPERATURE_SET);
        assertThat(hvacReadPermission).isEqualTo(Car.PERMISSION_CONTROL_CAR_CLIMATE);
        String hvacWritePermission = mManager.getWritePermission(
                VehiclePropertyIds.HVAC_TEMPERATURE_SET);
        assertThat(hvacWritePermission).isEqualTo(Car.PERMISSION_CONTROL_CAR_CLIMATE);

        // For read-only property
        String vinReadPermission = mManager.getReadPermission(VehiclePropertyIds.INFO_VIN);
        assertThat(vinReadPermission).isEqualTo(Car.PERMISSION_IDENTIFICATION);
        String vinWritePermission = mManager.getWritePermission(VehiclePropertyIds.INFO_VIN);
        assertThat(vinWritePermission).isNull();
    }

    @Test
    public void testGetPropertyConfig() {
        CarPropertyConfig<?> config = mManager.getCarPropertyConfig(CUSTOM_SEAT_MIXED_PROP_ID_1);
        assertThat(config.getPropertyId()).isEqualTo(CUSTOM_SEAT_MIXED_PROP_ID_1);
        // returns null if it cannot find the propertyConfig for the property.
        assertThat(mManager.getCarPropertyConfig(FAKE_PROPERTY_ID)).isNull();
    }

    @Test
    public void testGetPropertyConfig_withReadOnlyPermission() {
        CarPropertyConfig<?> configForReadOnlyProperty = mManager
                .getCarPropertyConfig(PROP_WITH_READ_ONLY_PERMISSION);

        assertThat(configForReadOnlyProperty).isNotNull();
        assertThat(configForReadOnlyProperty.getPropertyId())
                .isEqualTo(PROP_WITH_READ_ONLY_PERMISSION);
    }

    @Test
    public void testGetPropertyConfig_withWriteOnlyPermission() {
        CarPropertyConfig<?> configForWriteOnlyProperty = mManager
                .getCarPropertyConfig(PROP_WITH_WRITE_ONLY_PERMISSION);

        assertThat(configForWriteOnlyProperty).isNotNull();
        assertThat(configForWriteOnlyProperty.getPropertyId())
                .isEqualTo(PROP_WITH_WRITE_ONLY_PERMISSION);
    }

    @Test
    public void testGetAreaId() {
        int result = mManager.getAreaId(CUSTOM_SEAT_MIXED_PROP_ID_1, VehicleAreaSeat.ROW_1_LEFT);
        assertThat(result).isEqualTo(DRIVER_SIDE_AREA_ID);
        //test for the GLOBAL property
        int globalAreaId =
                mManager.getAreaId(CUSTOM_GLOBAL_MIXED_PROP_ID_2, VehicleAreaSeat.ROW_1_LEFT);
        assertThat(globalAreaId).isEqualTo(VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
        //test exception
        assertThrows(IllegalArgumentException.class, () -> mManager.getAreaId(
                CUSTOM_SEAT_MIXED_PROP_ID_1, VehicleAreaSeat.ROW_3_CENTER));
        assertThrows(IllegalArgumentException.class, () -> mManager.getAreaId(FAKE_PROPERTY_ID,
                VehicleAreaSeat.ROW_1_LEFT));
    }

    // subscribePropertyEvents will throw SecurityException when the caller does not have read
    // or write permission.
    @Test
    public void testSubscribePropertyEvents_noReadOrWritePermission() throws Exception {
        // FUEL_DOOR_OPEN requires either Car.PERMISSION_ENERGY_PORTS or
        // Car.PERMISSION_CONTROL_ENERGY_PORTS for read.
        int propId = VehiclePropertyIds.FUEL_DOOR_OPEN;
        ((MockedCarTestContext) getContext()).setDeniedPermissions(
                new String[]{Car.PERMISSION_ENERGY_PORTS, Car.PERMISSION_CONTROL_ENERGY_PORTS});

        TestCallback callback = new TestCallback(/* initValueCount= */ 0,
                /* changeEventCount= */ 0, /* errorEventCount= */ 0);

        assertThrows(SecurityException.class, () -> mManager.subscribePropertyEvents(
                propId, callback));
    }

    // subscribePropertyEvents will throw SecurityException when the caller only has write
    // permission.
    @Test
    public void testSubscribePropertyEvents_onlyWritePermission() throws Exception {
        // DISTANCE_DISPLAY_UNITS requires Car.PERMISSION_READ_DISPLAY_UNITS for read.
        // Car.PERMISSION_CONTROL_DISPLAY_UNITS and Car.PERMISSION_VENDOR_EXTENSION for write.
        int propId = VehiclePropertyIds.DISTANCE_DISPLAY_UNITS;
        ((MockedCarTestContext) getContext()).setDeniedPermissions(
                new String[]{Car.PERMISSION_READ_DISPLAY_UNITS});

        TestCallback callback = new TestCallback(/* initValueCount= */ 0,
                /* changeEventCount= */ 0, /* errorEventCount= */ 0);

        assertThrows(SecurityException.class, () -> mManager.subscribePropertyEvents(
                propId, callback));
    }

    // subscribePropertyEvents will throw IllegalArgumentException when the property is not
    // supported.
    @Test
    public void testSubscribePropertyEvents_unsupportedProperty() throws Exception {
        TestCallback callback = new TestCallback(/* initValueCount= */ 0,
                /* changeEventCount= */ 0, /* errorEventCount= */ 0);

        // EV_BATTERY_LEVEL is not supported by mocked VHAL.
        assertThrows(IllegalArgumentException.class, () -> mManager.subscribePropertyEvents(
                VehiclePropertyIds.EV_BATTERY_LEVEL, callback));
    }

    // subscribePropertyEvents will throw IllegalArgumentException when the property is not
    // supported even when the client does not have read permission.
    @Test
    public void testSubscribePropertyEvents_unsupportedProperty_noPermission() throws Exception {
        ((MockedCarTestContext) getContext()).setDeniedPermissions(
                new String[]{Car.PERMISSION_ENERGY});
        TestCallback callback = new TestCallback(/* initValueCount= */ 0,
                /* changeEventCount= */ 0, /* errorEventCount= */ 0);

        // EV_BATTERY_LEVEL is not supported by mocked VHAL.
        assertThrows(IllegalArgumentException.class, () -> mManager.subscribePropertyEvents(
                VehiclePropertyIds.EV_BATTERY_LEVEL, callback));
    }

    // registerCallback will return False when caller does not have read or write permission.
    @Test
    public void testRegisterCallback_noReadOrWritePermission() throws Exception {
        // FUEL_DOOR_OPEN requires either Car.PERMISSION_ENERGY_PORTS or
        // Car.PERMISSION_CONTROL_ENERGY_PORTS for read.
        int propId = VehiclePropertyIds.FUEL_DOOR_OPEN;
        ((MockedCarTestContext) getContext()).setDeniedPermissions(
                new String[]{Car.PERMISSION_ENERGY_PORTS, Car.PERMISSION_CONTROL_ENERGY_PORTS});

        TestCallback callback = new TestCallback(/* initValueCount= */ 0,
                /* changeEventCount= */ 0, /* errorEventCount= */ 0);

        assertThat(mManager.registerCallback(
                callback, propId, CarPropertyManager.SENSOR_RATE_ONCHANGE)).isFalse();
    }

    // registerCallback will return False when the property is not supported.
    @Test
    public void testRegisterCallback_unsupportedProperty() throws Exception {
        TestCallback callback = new TestCallback(/* initValueCount= */ 0,
                /* changeEventCount= */ 0, /* errorEventCount= */ 0);

        // EV_BATTERY_LEVEL is not supported by mocked VHAL.
        assertThat(mManager.registerCallback(
                callback, VehiclePropertyIds.EV_BATTERY_LEVEL,
                CarPropertyManager.SENSOR_RATE_ONCHANGE)).isFalse();
    }

    // registerCallback with a supported property with only the write permission will throw
    // SecurityException.
    @Test
    public void testRegisterCallback_onlyWritePermission() throws Exception {
        // DISTANCE_DISPLAY_UNITS requires Car.PERMISSION_READ_DISPLAY_UNITS for read.
        // Car.PERMISSION_CONTROL_DISPLAY_UNITS and Car.PERMISSION_VENDOR_EXTENSION for write.
        int propId = VehiclePropertyIds.DISTANCE_DISPLAY_UNITS;
        ((MockedCarTestContext) getContext()).setDeniedPermissions(
                new String[]{Car.PERMISSION_READ_DISPLAY_UNITS});

        TestCallback callback = new TestCallback(/* initValueCount= */ 0,
                /* changeEventCount= */ 0, /* errorEventCount= */ 0);

        assertThrows(SecurityException.class, () -> mManager.registerCallback(
                callback, propId, CarPropertyManager.SENSOR_RATE_ONCHANGE));
    }

    @Test
    public void testRegisterPropertyGetInitialValueHandleNotAvailableStatusCode() throws Exception {
        TestCallback callback = new TestCallback(/* initValueCount= */ 1,
                /* changeEventCount= */ 0, /* errorEventCount= */ 0);

        mManager.registerCallback(callback, PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE,
                CarPropertyManager.SENSOR_RATE_ONCHANGE);

        callback.assertRegisterCompleted();
        List<CarPropertyValue> carPropertyValues = callback.getInitialValues();
        assertThat(carPropertyValues).hasSize(1);
        assertThat(carPropertyValues.get(0).getStatus()).isEqualTo(
                CarPropertyValue.STATUS_UNAVAILABLE);
    }

    @Test
    public void testRegisterPropertyGetInitialValueHandleAccessDeniedStatusCodes()
            throws Exception {
        TestCallback callback = new TestCallback(/* initValueCount= */ 1,
                /* changeEventCount= */ 0, /* errorEventCount= */ 0);

        mManager.registerCallback(callback, PROP_CAUSE_STATUS_CODE_ACCESS_DENIED,
                CarPropertyManager.SENSOR_RATE_ONCHANGE);

        callback.assertRegisterCompleted();
        List<CarPropertyValue> carPropertyValues = callback.getInitialValues();
        assertThat(carPropertyValues).hasSize(1);
        assertThat(carPropertyValues.get(0).getStatus()).isEqualTo(
                CarPropertyValue.STATUS_ERROR);
    }

    @Test
    public void testRegisterPropertyGetInitialValueHandleInternalErrorStatusCodes()
            throws Exception {
        TestCallback callback = new TestCallback(/* initValueCount= */ 1,
                /* changeEventCount= */ 0, /* errorEventCount= */ 0);

        mManager.registerCallback(callback, PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR,
                CarPropertyManager.SENSOR_RATE_ONCHANGE);

        callback.assertRegisterCompleted();
        List<CarPropertyValue> carPropertyValues = callback.getInitialValues();
        assertThat(carPropertyValues).hasSize(1);
        assertThat(carPropertyValues.get(0).getStatus()).isEqualTo(
                CarPropertyValue.STATUS_ERROR);
    }

    @Test
    public void testRegisterPropertyGetInitialValueHandleInvalidArgStatusCode() throws Exception {
        TestCallback callback = new TestCallback(/* initValueCount= */ 1,
                /* changeEventCount= */ 0, /* errorEventCount= */ 0);

        mManager.registerCallback(callback, PROP_CAUSE_STATUS_CODE_INVALID_ARG,
                CarPropertyManager.SENSOR_RATE_ONCHANGE);

        // We should not receive any initial value event.
        assertThrows(IllegalStateException.class, () -> callback.assertRegisterCompleted(
                /* timeoutMs= */ 1000));
    }

    @Test
    public void testRegisterPropertyGetInitialValueHandleTryAgainStatusCode() throws Exception {
        TestCallback callback = new TestCallback(/* initValueCount= */ 1,
                /* changeEventCount= */ 0, /* errorEventCount= */ 0);

        mManager.registerCallback(callback, PROP_CAUSE_STATUS_CODE_TRY_AGAIN,
                CarPropertyManager.SENSOR_RATE_ONCHANGE);

        // We should not receive any initial value event.
        assertThrows(IllegalStateException.class, () -> callback.assertRegisterCompleted(
                /* timeoutMs= */ 1000));
    }

    @Test
    public void testNotReceiveOnErrorEvent() throws Exception {
        TestCallback callback = new TestCallback(/* initValueCount= */ 2, /* changeEventCount= */ 0,
                /* errorEventCount= */ 1);
        mManager.registerCallback(callback, VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                CarPropertyManager.SENSOR_RATE_ONCHANGE);
        callback.assertRegisterCompleted();
        injectErrorEvent(VehiclePropertyIds.HVAC_TEMPERATURE_SET, PASSENGER_SIDE_AREA_ID,
                VehicleHalStatusCode.STATUS_INTERNAL_ERROR);
        // app never change the value of HVAC_TEMPERATURE_SET, it won't get an error code.
        callback.assertOnErrorEventNotCalled();
    }

    @Test
    public void testReceiveOnErrorEvent() throws Exception {
        TestCallback callback = new TestCallback(/* initValueCount= */ 2, /* changeEventCount= */ 0,
                /* errorEventCount= */ 1);
        mManager.registerCallback(callback, VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                CarPropertyManager.SENSOR_RATE_ONCHANGE);
        callback.assertRegisterCompleted();
        mManager.setFloatProperty(
                VehiclePropertyIds.HVAC_TEMPERATURE_SET, PASSENGER_SIDE_AREA_ID,
                CHANGED_TEMP_VALUE);
        injectErrorEvent(VehiclePropertyIds.HVAC_TEMPERATURE_SET, PASSENGER_SIDE_AREA_ID,
                VehicleHalStatusCode.STATUS_INTERNAL_ERROR);
        callback.assertOnErrorEventCalled();
        assertThat(callback.getErrorCode()).isEqualTo(
                CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN);
    }

    @Test
    public void testNotReceiveOnErrorEventAfterUnregister() throws Exception {
        TestCallback callback1 = new TestCallback(/* initValueCount= */ 2,
                /* changeEventCount= */ 0, /* errorEventCount= */ 1);
        mManager.registerCallback(callback1, VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                CarPropertyManager.SENSOR_RATE_ONCHANGE);
        callback1.assertRegisterCompleted();
        TestCallback callback2 = new TestCallback(/* initValueCount= */ 2,
                /* changeEventCount= */ 0, /* errorEventCount= */ 1);
        mManager.registerCallback(callback2, VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                CarPropertyManager.SENSOR_RATE_ONCHANGE);
        mManager.setFloatProperty(
                VehiclePropertyIds.HVAC_TEMPERATURE_SET, PASSENGER_SIDE_AREA_ID,
                CHANGED_TEMP_VALUE);
        mManager.unregisterCallback(callback1, VehiclePropertyIds.HVAC_TEMPERATURE_SET);
        SystemClock.sleep(WAIT_FOR_NO_EVENTS);
        injectErrorEvent(VehiclePropertyIds.HVAC_TEMPERATURE_SET, PASSENGER_SIDE_AREA_ID,
                VehicleHalStatusCode.STATUS_INTERNAL_ERROR);
        // callback1 is unregistered
        callback1.assertOnErrorEventNotCalled();
        callback2.assertOnErrorEventCalled();
    }

    @Test
    public void testSetterExceptionsBeforeR() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isLessThan(Build.VERSION_CODES.R);

        assertThrows(IllegalStateException.class,
                () -> mManager.setProperty(Integer.class, PROP_CAUSE_STATUS_CODE_ACCESS_DENIED,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
        assertThrows(IllegalStateException.class,
                () -> mManager.setProperty(Integer.class, PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
        assertThrows(IllegalStateException.class,
                () -> mManager.setProperty(Integer.class, PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
        assertThrows(IllegalArgumentException.class,
                () -> mManager.setProperty(Integer.class, PROP_CAUSE_STATUS_CODE_INVALID_ARG,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
        assertThrows(RuntimeException.class,
                () -> mManager.setProperty(Integer.class, PROP_CAUSE_STATUS_CODE_ACCESS_DENIED,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
    }

    @Test
    public void testSetterExceptionsEqualAfterR() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isAtLeast(Build.VERSION_CODES.Q);

        assertThrows(PropertyAccessDeniedSecurityException.class,
                () -> mManager.setProperty(Integer.class, PROP_CAUSE_STATUS_CODE_ACCESS_DENIED,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
        assertThrows(PropertyNotAvailableAndRetryException.class,
                () -> mManager.setProperty(Integer.class, PROP_CAUSE_STATUS_CODE_TRY_AGAIN,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
        assertThrows(PropertyNotAvailableException.class,
                () -> mManager.setProperty(Integer.class, PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
        assertThrows(CarInternalErrorException.class,
                () -> mManager.setProperty(Integer.class, PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
        assertThrows(IllegalArgumentException.class,
                () -> mManager.setProperty(Integer.class, PROP_CAUSE_STATUS_CODE_INVALID_ARG,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
        assertThrows(IllegalArgumentException.class,
                () -> mManager.setProperty(Integer.class, PROP_UNSUPPORTED,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
    }

    @Test
    public void testGetterExceptionsBeforeR() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isLessThan(Build.VERSION_CODES.R);

        assertThrows(IllegalStateException.class,
                () -> mManager.getProperty(PROP_CAUSE_STATUS_CODE_ACCESS_DENIED, 0));
        assertThrows(IllegalStateException.class,
                () -> mManager.getIntProperty(PROP_CAUSE_STATUS_CODE_ACCESS_DENIED, 0));

        assertThat(mManager.getProperty(PROP_CAUSE_STATUS_CODE_INVALID_ARG, 0)).isNull();
        assertThat(mManager.getIntProperty(PROP_CAUSE_STATUS_CODE_INVALID_ARG, 0)).isEqualTo(0);
        assertThat(mManager.getProperty(PROP_UNSUPPORTED, 0)).isNull();

        assertThrows(IllegalStateException.class,
                () -> mManager.getProperty(PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE, 0));
        assertThrows(IllegalStateException.class,
                () -> mManager.getIntProperty(PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE, 0));

        assertThrows(IllegalStateException.class,
                () -> mManager.getProperty(PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR, 0));
        assertThrows(IllegalStateException.class,
                () -> mManager.getIntProperty(PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR, 0));

        assertThrows(IllegalStateException.class, () -> mManager.getProperty(NULL_VALUE_PROP, 0));
        assertThrows(IllegalStateException.class,
                () -> mManager.getIntProperty(NULL_VALUE_PROP, 0));

        Truth.assertThat(mManager.getProperty(PROP_CAUSE_STATUS_CODE_TRY_AGAIN, 0)).isNull();
        assertThat(mManager.getIntProperty(PROP_CAUSE_STATUS_CODE_TRY_AGAIN, 0)).isEqualTo(0);

    }

    @Test
    public void testGetterExceptionsEqualAfterR() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isAtLeast(Build.VERSION_CODES.R);

        assertThrows(PropertyAccessDeniedSecurityException.class,
                () -> mManager.getProperty(PROP_CAUSE_STATUS_CODE_ACCESS_DENIED, 0));
        assertThrows(PropertyAccessDeniedSecurityException.class,
                () -> mManager.getIntProperty(PROP_CAUSE_STATUS_CODE_ACCESS_DENIED, 0));

        assertThat(mManager.getProperty(PROP_CAUSE_STATUS_CODE_INVALID_ARG, 0)).isNull();
        assertThat(mManager.getIntProperty(PROP_CAUSE_STATUS_CODE_INVALID_ARG, 0)).isEqualTo(0);
        assertThat(mManager.getProperty(PROP_UNSUPPORTED, 0)).isNull();

        assertThrows(PropertyNotAvailableAndRetryException.class,
                () -> mManager.getProperty(PROP_CAUSE_STATUS_CODE_TRY_AGAIN, 0));
        assertThrows(PropertyNotAvailableAndRetryException.class,
                () -> mManager.getIntProperty(PROP_CAUSE_STATUS_CODE_TRY_AGAIN, 0));

        assertThrows(PropertyNotAvailableException.class,
                () -> mManager.getProperty(PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE, 0));
        assertThrows(PropertyNotAvailableException.class,
                () -> mManager.getIntProperty(PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE, 0));

        assertThrows(CarInternalErrorException.class,
                () -> mManager.getProperty(PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR, 0));
        assertThrows(CarInternalErrorException.class,
                () -> mManager.getIntProperty(PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR, 0));

        assertThrows(PropertyNotAvailableException.class,
                () -> mManager.getProperty(NULL_VALUE_PROP, 0));
        assertThrows(PropertyNotAvailableException.class,
                () -> mManager.getIntProperty(NULL_VALUE_PROP, 0));
        assertThrows(PropertyNotAvailableException.class,
                () -> mManager.getIntProperty(NULL_VALUE_PROP, 0));
    }

    @Test
    public void testGetter_notSupportedPropertyAfterU() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isAtLeast(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);

        assertThrows(IllegalArgumentException.class,
                () -> mManager.getProperty(PROP_CAUSE_STATUS_CODE_INVALID_ARG, 0));
        assertThrows(IllegalArgumentException.class,
                () -> mManager.getIntProperty(PROP_CAUSE_STATUS_CODE_INVALID_ARG, 0));
        assertThrows(IllegalArgumentException.class,
                () -> mManager.getProperty(PROP_UNSUPPORTED, 0));
    }

    @Test
    public void testOnChangeEventWithSameAreaId() throws Exception {
        // init
        TestCallback callback = new TestCallback(/* initValueCount= */ 2, /* changeEventCount= */ 1,
                /* errorEventCount= */ 0);
        mManager.registerCallback(callback, CUSTOM_SEAT_INT_PROP_1, 0);
        callback.assertRegisterCompleted();

        VehiclePropValue firstFakeValueDriveSide = new VehiclePropValue();
        firstFakeValueDriveSide.prop = CUSTOM_SEAT_INT_PROP_1;
        firstFakeValueDriveSide.areaId = DRIVER_SIDE_AREA_ID;
        firstFakeValueDriveSide.value = new RawPropValues();
        firstFakeValueDriveSide.value.int32Values = new int[]{2};
        firstFakeValueDriveSide.timestamp = SystemClock.elapsedRealtimeNanos();
        VehiclePropValue secFakeValueDriveSide = new VehiclePropValue();
        secFakeValueDriveSide.prop = CUSTOM_SEAT_INT_PROP_1;
        secFakeValueDriveSide.areaId = DRIVER_SIDE_AREA_ID;
        secFakeValueDriveSide.value = new RawPropValues();
        secFakeValueDriveSide.value.int32Values = new int[]{3}; // 0 in HAL indicate false;
        secFakeValueDriveSide.timestamp = SystemClock.elapsedRealtimeNanos();
        // inject the new event first
        getAidlMockedVehicleHal().injectEvent(secFakeValueDriveSide);
        // inject the old event
        getAidlMockedVehicleHal().injectEvent(firstFakeValueDriveSide);

        List<CarPropertyValue> events = callback.waitAndGetChangeEvents();

        // Client should only get the new event
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getValue()).isEqualTo(3);

    }

    @Test
    public void testOnChangeEventWithDifferentAreaId() throws Exception {
        // init
        TestCallback callback = new TestCallback(/* initValueCount= */ 2, /* changeEventCount= */ 2,
                /* errorEventCount= */ 0);
        mManager.registerCallback(callback, CUSTOM_SEAT_INT_PROP_2, 0);
        callback.assertRegisterCompleted();
        VehiclePropValue fakeValueDriveSide = new VehiclePropValue();
        fakeValueDriveSide.prop = CUSTOM_SEAT_INT_PROP_2;
        fakeValueDriveSide.areaId = DRIVER_SIDE_AREA_ID;
        fakeValueDriveSide.value = new RawPropValues();
        fakeValueDriveSide.value.int32Values = new int[]{4};
        fakeValueDriveSide.timestamp = SystemClock.elapsedRealtimeNanos();

        VehiclePropValue fakeValuePsgSide = new VehiclePropValue();
        fakeValuePsgSide.prop = CUSTOM_SEAT_INT_PROP_2;
        fakeValuePsgSide.areaId = PASSENGER_SIDE_AREA_ID;
        fakeValuePsgSide.value = new RawPropValues();
        fakeValuePsgSide.value.int32Values = new int[]{5};
        fakeValuePsgSide.timestamp = SystemClock.elapsedRealtimeNanos();

        // inject passenger event before driver event
        getAidlMockedVehicleHal().injectEvent(fakeValuePsgSide);
        getAidlMockedVehicleHal().injectEvent(fakeValueDriveSide);

        List<CarPropertyValue> events = callback.waitAndGetChangeEvents();

        // both events should be received by listener
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getValue()).isEqualTo(5);
        assertThat(events.get(1).getValue()).isEqualTo(4);
    }

    @Test
    public void testOnChangeEventPropErrorStatus() throws Exception {
        // init
        TestCallback callback = new TestCallback(/* initValueCount= */ 2, /* changeEventCount= */ 1,
                /* errorEventCount= */ 0);

        mManager.registerCallback(callback, CUSTOM_SEAT_INT_PROP_1, 0);

        callback.assertRegisterCompleted(/* timeoutMs= */ 1000);

        VehiclePropValue prop = new VehiclePropValue();
        prop.prop = CUSTOM_SEAT_INT_PROP_1;
        prop.areaId = DRIVER_SIDE_AREA_ID;
        prop.value = new RawPropValues();
        prop.status = VehiclePropertyStatus.ERROR;
        prop.timestamp = SystemClock.elapsedRealtimeNanos();

        getAidlMockedVehicleHal().injectEvent(prop);

        List<CarPropertyValue> carPropertyValues = callback.waitAndGetChangeEvents();
        assertThat(carPropertyValues).hasSize(1);
        assertThat(carPropertyValues.get(0).getStatus()).isEqualTo(CarPropertyValue.STATUS_ERROR);
    }

    @Test
    public void testOnChangeEventPropUnavailableStatus() throws Exception {
        // init
        TestCallback callback = new TestCallback(/* initValueCount= */ 2, /* changeEventCount= */ 1,
                /* errorEventCount= */ 0);

        mManager.registerCallback(callback, CUSTOM_SEAT_INT_PROP_1, 0);

        callback.assertRegisterCompleted();

        VehiclePropValue prop = new VehiclePropValue();
        prop.prop = CUSTOM_SEAT_INT_PROP_1;
        prop.areaId = DRIVER_SIDE_AREA_ID;
        prop.value = new RawPropValues();
        prop.status = VehiclePropertyStatus.UNAVAILABLE;
        prop.timestamp = SystemClock.elapsedRealtimeNanos();

        getAidlMockedVehicleHal().injectEvent(prop);

        List<CarPropertyValue> carPropertyValues = callback.waitAndGetChangeEvents();
        assertThat(carPropertyValues).hasSize(1);
        assertThat(carPropertyValues.get(0).getStatus()).isEqualTo(
                CarPropertyValue.STATUS_UNAVAILABLE);
    }

    @Test
    public void testOnChangeEventInvalidPayload() throws Exception {
        // init
        TestCallback callback = new TestCallback(/* initValueCount= */ 2, /* changeEventCount= */ 0,
                /* errorEventCount= */ 0);
        mManager.registerCallback(callback, CUSTOM_SEAT_INT_PROP_1, 0);
        callback.assertRegisterCompleted();

        List<VehiclePropValue> props = new ArrayList<>();
        VehiclePropValue emptyProp = new VehiclePropValue();
        emptyProp.prop = CUSTOM_SEAT_INT_PROP_1;
        props.add(emptyProp);

        VehiclePropValue twoIntsProp = new VehiclePropValue();
        twoIntsProp.prop = CUSTOM_SEAT_INT_PROP_1;
        twoIntsProp.value = new RawPropValues();
        twoIntsProp.value.int32Values = new int[]{0, 1};
        props.add(twoIntsProp);

        VehiclePropValue propWithFloat = new VehiclePropValue();
        propWithFloat.prop = CUSTOM_SEAT_INT_PROP_1;
        propWithFloat.value = new RawPropValues();
        propWithFloat.value.floatValues = new float[]{0f};
        props.add(propWithFloat);

        VehiclePropValue propWithString = new VehiclePropValue();
        propWithString.prop = CUSTOM_SEAT_INT_PROP_1;
        propWithString.value = new RawPropValues();
        propWithString.value.stringValue = "1234";
        props.add(propWithString);

        for (VehiclePropValue prop : props) {
            // inject passenger event before driver event
            getAidlMockedVehicleHal().injectEvent(prop);

            assertThat(callback.getChangeEventCounter()).isEqualTo(0);
        }
    }

    @Test
    public void registerCallback_handlesContinuousPropertyUpdateRate() throws Exception {
        float wheelLeftFrontValue = 11.11f;
        long wheelLeftFrontTimestampNanos = Duration.ofSeconds(1).toNanos();

        float notNewEnoughWheelLeftFrontValue = 22.22f;
        long notNewEnoughWheelLeftFrontTimestampNanos = Duration.ofMillis(1899).toNanos();

        float newEnoughWheelLeftFrontValue = 33.33f;
        long newEnoughWheelLeftFrontTimestampNanos = Duration.ofSeconds(2).toNanos();

        TestCallback callback = new TestCallback(/* initValueCount= */ 4, /* changeEventCount= */ 2,
                /* errorEventCount= */ 0);
        assertThat(mManager.registerCallback(callback, VehiclePropertyIds.TIRE_PRESSURE,
                1f)).isTrue();
        callback.assertRegisterCompleted();

        long currentElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();
        getAidlMockedVehicleHal().injectEvent(
                newTirePressureVehiclePropValue(VehicleAreaWheel.WHEEL_LEFT_FRONT,
                        wheelLeftFrontValue,
                        currentElapsedRealtimeNanos + wheelLeftFrontTimestampNanos));
        getAidlMockedVehicleHal().injectEvent(
                newTirePressureVehiclePropValue(VehicleAreaWheel.WHEEL_LEFT_FRONT,
                        notNewEnoughWheelLeftFrontValue,
                        currentElapsedRealtimeNanos + notNewEnoughWheelLeftFrontTimestampNanos));
        getAidlMockedVehicleHal().injectEvent(
                newTirePressureVehiclePropValue(VehicleAreaWheel.WHEEL_LEFT_FRONT,
                        newEnoughWheelLeftFrontValue,
                        currentElapsedRealtimeNanos + newEnoughWheelLeftFrontTimestampNanos));

        List<CarPropertyValue> carPropertyValues = callback.waitAndGetChangeEvents();
        assertThat(carPropertyValues).hasSize(2);

        assertTirePressureCarPropertyValue(carPropertyValues.get(0),
                VehicleAreaWheel.WHEEL_LEFT_FRONT, wheelLeftFrontValue,
                currentElapsedRealtimeNanos + wheelLeftFrontTimestampNanos);

        assertTirePressureCarPropertyValue(carPropertyValues.get(1),
                VehicleAreaWheel.WHEEL_LEFT_FRONT, newEnoughWheelLeftFrontValue,
                currentElapsedRealtimeNanos + newEnoughWheelLeftFrontTimestampNanos);
    }

    @Test
    public void registerCallback_handlesOutOfTimeOrderEventsWithDifferentAreaIds()
            throws Exception {
        float wheelLeftFrontValue = 11.11f;
        long wheelLeftFrontTimestampNanos = Duration.ofSeconds(4).toNanos();

        float wheelRightFrontValue = 22.22f;
        long wheelRightFrontTimestampNanos = Duration.ofSeconds(3).toNanos();

        float wheelLeftRearValue = 33.33f;
        long wheelLeftRearTimestampNanos = Duration.ofSeconds(2).toNanos();

        float wheelRightRearValue = 44.44f;
        long wheelRightRearTimestampNanos = Duration.ofSeconds(1).toNanos();

        // Initially we have 4 area Ids, so we will have 4 initial values. In the test we will
        // inject 4 events which will generate 4 change events.
        TestCallback callback = new TestCallback(/* initValueCount= */ 4, /* changeEventCount= */ 4,
                /* errorEventCount= */ 0);
        // AidlMockedVehicleHal will not actually genenerate property events for continuous
        // property, so the subscription rate does not matter.
        assertThat(mManager.registerCallback(callback, VehiclePropertyIds.TIRE_PRESSURE, 1f))
                .isTrue();

        callback.assertRegisterCompleted();

        long currentElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();
        // inject events in time order from newest to oldest
        getAidlMockedVehicleHal().injectEvent(
                newTirePressureVehiclePropValue(VehicleAreaWheel.WHEEL_LEFT_FRONT,
                        wheelLeftFrontValue,
                        currentElapsedRealtimeNanos + wheelLeftFrontTimestampNanos));
        getAidlMockedVehicleHal().injectEvent(
                newTirePressureVehiclePropValue(VehicleAreaWheel.WHEEL_RIGHT_FRONT,
                        wheelRightFrontValue,
                        currentElapsedRealtimeNanos + wheelRightFrontTimestampNanos));
        getAidlMockedVehicleHal().injectEvent(
                newTirePressureVehiclePropValue(VehicleAreaWheel.WHEEL_LEFT_REAR,
                        wheelLeftRearValue,
                        currentElapsedRealtimeNanos + wheelLeftRearTimestampNanos));
        getAidlMockedVehicleHal().injectEvent(
                newTirePressureVehiclePropValue(VehicleAreaWheel.WHEEL_RIGHT_REAR,
                        wheelRightRearValue,
                        currentElapsedRealtimeNanos + wheelRightRearTimestampNanos));

        List<CarPropertyValue> carPropertyValues = callback.waitAndGetChangeEvents();
        assertThat(carPropertyValues).hasSize(4);

        assertTirePressureCarPropertyValue(carPropertyValues.get(0),
                VehicleAreaWheel.WHEEL_LEFT_FRONT, wheelLeftFrontValue,
                currentElapsedRealtimeNanos + wheelLeftFrontTimestampNanos);

        assertTirePressureCarPropertyValue(carPropertyValues.get(1),
                VehicleAreaWheel.WHEEL_RIGHT_FRONT, wheelRightFrontValue,
                currentElapsedRealtimeNanos + wheelRightFrontTimestampNanos);

        assertTirePressureCarPropertyValue(carPropertyValues.get(2),
                VehicleAreaWheel.WHEEL_LEFT_REAR, wheelLeftRearValue,
                currentElapsedRealtimeNanos + wheelLeftRearTimestampNanos);

        assertTirePressureCarPropertyValue(carPropertyValues.get(3),
                VehicleAreaWheel.WHEEL_RIGHT_REAR, wheelRightRearValue,
                currentElapsedRealtimeNanos + wheelRightRearTimestampNanos);
    }

    @Test
    public void testGetPropertiesAsync() throws Exception {
        List<GetPropertyRequest> getPropertyRequests = new ArrayList<>();
        Executor callbackExecutor = new HandlerExecutor(mHandler);
        Set<Integer> requestIds = new ArraySet();

        // Regular property.
        GetPropertyRequest vinRequest = mManager.generateGetPropertyRequest(
                VehiclePropertyIds.INFO_VIN, /* areaId= */ 0);
        // Property with area.
        GetPropertyRequest hvacTempDriverRequest = mManager.generateGetPropertyRequest(
                VehiclePropertyIds.HVAC_TEMPERATURE_SET, DRIVER_SIDE_AREA_ID);
        GetPropertyRequest hvacTempPsgRequest = mManager.generateGetPropertyRequest(
                VehiclePropertyIds.HVAC_TEMPERATURE_SET, PASSENGER_SIDE_AREA_ID);

        getPropertyRequests.add(vinRequest);
        getPropertyRequests.add(hvacTempDriverRequest);
        getPropertyRequests.add(hvacTempPsgRequest);

        requestIds.add(vinRequest.getRequestId());
        requestIds.add(hvacTempDriverRequest.getRequestId());
        requestIds.add(hvacTempPsgRequest.getRequestId());

        int resultCount = 3;
        int errorCount = 0;

        int vendorErrorRequestId = 0;

        // A list of properties that will generate error results.
        for (int propId : List.of(
                PROP_VALUE_STATUS_ERROR_INT_ARRAY,
                PROP_VALUE_STATUS_ERROR_BOOLEAN,
                PROP_VALUE_STATUS_UNAVAILABLE_INT,
                PROP_VALUE_STATUS_UNAVAILABLE_FLOAT,
                PROP_CAUSE_STATUS_CODE_TRY_AGAIN,
                PROP_CAUSE_STATUS_CODE_ACCESS_DENIED,
                PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE,
                PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR_WITH_VENDOR_CODE,
                PROP_CAUSE_STATUS_CODE_INVALID_ARG
            )) {
            GetPropertyRequest errorRequest = mManager.generateGetPropertyRequest(
                    propId, /* areaId= */ 0);
            getPropertyRequests.add(errorRequest);
            requestIds.add(errorRequest.getRequestId());
            errorCount++;
            if (propId == PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR_WITH_VENDOR_CODE) {
                vendorErrorRequestId = errorRequest.getRequestId();
            }
        }

        GetPropertyRequest unavailableDriverRequest = mManager.generateGetPropertyRequest(
                PROP_VALUE_STATUS_UNAVAILABLE_SEAT, DRIVER_SIDE_AREA_ID);
        getPropertyRequests.add(unavailableDriverRequest);
        requestIds.add(unavailableDriverRequest.getRequestId());
        errorCount++;

        GetPropertyRequest unavailablePsgRequest = mManager.generateGetPropertyRequest(
                PROP_VALUE_STATUS_UNAVAILABLE_SEAT, PASSENGER_SIDE_AREA_ID);
        getPropertyRequests.add(unavailablePsgRequest);
        requestIds.add(unavailablePsgRequest.getRequestId());
        errorCount++;

        TestPropertyAsyncCallback callback = new TestPropertyAsyncCallback(
                requestIds);
        mManager.getPropertiesAsync(getPropertyRequests, /* timeoutInMs= */ 1000,
                /* cancellationSignal= */ null, callbackExecutor, callback);

        // Make the timeout longer than the timeout specified in getPropertiesAsync since the
        // error callback will be delivered after the request timed-out.
        callback.waitAndFinish(/* timeoutInMs= */ 2000);

        assertThat(callback.getTestErrors()).isEmpty();
        List<GetPropertyResult<?>> results = callback.getGetResultList();
        assertThat(results.size()).isEqualTo(resultCount);
        assertThat(callback.getErrorList().size()).isEqualTo(errorCount);
        for (GetPropertyResult<?> result : results) {
            int resultRequestId = result.getRequestId();
            if (resultRequestId == vinRequest.getRequestId()) {
                assertThat(result.getValue().getClass()).isEqualTo(String.class);
                assertThat(result.getValue()).isEqualTo(TEST_VIN);
            } else if (resultRequestId == hvacTempDriverRequest.getRequestId()
                    || resultRequestId == hvacTempPsgRequest.getRequestId()) {
                assertThat(result.getValue().getClass()).isEqualTo(Float.class);
                assertThat(result.getValue()).isEqualTo(INIT_TEMP_VALUE);
            } else {
                Assert.fail("unknown result request Id: " + resultRequestId);
            }
        }
        for (PropertyAsyncError error : callback.getErrorList()) {
            assertThat(error.getErrorCode()).isNotEqualTo(0);
            int propertyId = error.getPropertyId();
            if (propertyId == PROP_VALUE_STATUS_ERROR_INT_ARRAY
                    || propertyId == PROP_VALUE_STATUS_ERROR_BOOLEAN
                    || propertyId == PROP_CAUSE_STATUS_CODE_ACCESS_DENIED
                    || propertyId == PROP_CAUSE_STATUS_CODE_INVALID_ARG) {
                assertWithMessage("receive correct error for property ID: " + propertyId)
                        .that(error.getErrorCode()).isEqualTo(STATUS_ERROR_INTERNAL_ERROR);
                assertThat(error.getVendorErrorCode()).isEqualTo(0);
            }
            if (propertyId == PROP_VALUE_STATUS_UNAVAILABLE_INT
                    || propertyId == PROP_VALUE_STATUS_UNAVAILABLE_FLOAT
                    || propertyId == PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE) {
                assertWithMessage("receive correct error for property ID: " + propertyId)
                        .that(error.getErrorCode()).isEqualTo(STATUS_ERROR_NOT_AVAILABLE);
                assertThat(error.getVendorErrorCode()).isEqualTo(0);
            }
            if (propertyId == PROP_CAUSE_STATUS_CODE_TRY_AGAIN) {
                assertWithMessage("receive correct error for property ID: " + propertyId)
                        .that(error.getErrorCode()).isEqualTo(STATUS_ERROR_TIMEOUT);
                assertThat(error.getVendorErrorCode()).isEqualTo(0);
            }
            if (error.getRequestId() == vendorErrorRequestId) {
                assertWithMessage("receive correct error for property ID: " + propertyId)
                        .that(error.getErrorCode()).isEqualTo(STATUS_ERROR_INTERNAL_ERROR);
                assertThat(error.getVendorErrorCode()).isEqualTo(VENDOR_CODE_FOR_INTERNAL_ERROR);
            }
        }
    }

    @Test
    public void testSetPropertiesAsync() throws Exception {
        List<SetPropertyRequest<?>> setPropertyRequests = new ArrayList<>();
        Executor callbackExecutor = new HandlerExecutor(mHandler);
        Set<Integer> requestIds = new ArraySet();

        // Global read-write property.
        SetPropertyRequest<Boolean> fuelDoorOpenRequest = mManager.generateSetPropertyRequest(
                VehiclePropertyIds.FUEL_DOOR_OPEN, 0, true);
        // Seat area type read-write property.
        float tempValue1 = 10.1f;
        // This is less than minValue: 10, so it should be set to min value instead.
        float tempValue2 = 9.9f;
        SetPropertyRequest<Float> hvacTempDriverRequest = mManager.generateSetPropertyRequest(
                VehiclePropertyIds.HVAC_TEMPERATURE_SET, DRIVER_SIDE_AREA_ID, tempValue1);
        SetPropertyRequest<Float> hvacTempPsgRequest = mManager.generateSetPropertyRequest(
                VehiclePropertyIds.HVAC_TEMPERATURE_SET, PASSENGER_SIDE_AREA_ID, tempValue2);
        SetPropertyRequest<Long> writeOnlyPropRequest = mManager.generateSetPropertyRequest(
                VehiclePropertyIds.EPOCH_TIME, 0, /* value= */ 1L);
        // Write only property with the default waitForProperty set to true should generate error.
        writeOnlyPropRequest.setWaitForPropertyUpdate(false);

        setPropertyRequests.add(fuelDoorOpenRequest);
        setPropertyRequests.add(hvacTempDriverRequest);
        setPropertyRequests.add(hvacTempPsgRequest);
        setPropertyRequests.add(writeOnlyPropRequest);

        requestIds.add(fuelDoorOpenRequest.getRequestId());
        requestIds.add(hvacTempDriverRequest.getRequestId());
        requestIds.add(hvacTempPsgRequest.getRequestId());
        requestIds.add(writeOnlyPropRequest.getRequestId());

        List<Integer> successPropIds = List.of(
                VehiclePropertyIds.FUEL_DOOR_OPEN,
                VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                VehiclePropertyIds.EPOCH_TIME);

        int resultCount = requestIds.size();
        int errorCount = 0;
        int vendorErrorRequestId = 0;

        // A list of properties that will generate error results.
        List<Integer> errorPropIds = List.of(
                PROP_CAUSE_STATUS_CODE_TRY_AGAIN,
                PROP_CAUSE_STATUS_CODE_ACCESS_DENIED,
                PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE,
                PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE_WITH_VENDOR_CODE,
                PROP_CAUSE_STATUS_CODE_INVALID_ARG,
                PROP_ERROR_EVENT_NOT_AVAILABLE_DISABLED);
        for (int propId : errorPropIds) {
            SetPropertyRequest<Integer> errorRequest = mManager.generateSetPropertyRequest(
                    propId, /* areaId= */ 0, /* value= */ 1);
            setPropertyRequests.add(errorRequest);
            requestIds.add(errorRequest.getRequestId());
            errorCount++;
            if (propId == PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE_WITH_VENDOR_CODE) {
                vendorErrorRequestId = errorRequest.getRequestId();
            }
        }

        long startTime = SystemClock.elapsedRealtimeNanos();
        TestPropertyAsyncCallback callback = new TestPropertyAsyncCallback(requestIds);

        mManager.setPropertiesAsync(setPropertyRequests, /* timeoutInMs= */ 1000,
                /* cancellationSignal= */ null, callbackExecutor, callback);

        // Make the timeout longer than the timeout specified in setPropertiesAsync since the
        // error callback will be delivered after the request timed-out.
        callback.waitAndFinish(/* timeoutInMs= */ 2000);

        assertThat(callback.getTestErrors()).isEmpty();
        List<SetPropertyResult> results = callback.getSetResultList();
        assertThat(results.size()).isEqualTo(resultCount);
        assertThat(callback.getErrorList().size()).isEqualTo(errorCount);
        for (SetPropertyResult result : results) {
            assertThat(result.getPropertyId()).isIn(successPropIds);
            if (result.getPropertyId() == VehiclePropertyIds.HVAC_TEMPERATURE_SET) {
                assertThat(result.getAreaId()).isIn(List.of(
                        DRIVER_SIDE_AREA_ID, PASSENGER_SIDE_AREA_ID));
            }
            assertThat(result.getUpdateTimestampNanos()).isGreaterThan(startTime);
        }
        for (PropertyAsyncError error : callback.getErrorList()) {
            int propertyId = error.getPropertyId();
            assertThat(propertyId).isIn(errorPropIds);
            assertThat(error.getAreaId()).isEqualTo(0);
            if (propertyId == PROP_CAUSE_STATUS_CODE_ACCESS_DENIED
                    || propertyId == PROP_CAUSE_STATUS_CODE_INVALID_ARG) {
                assertWithMessage("receive correct error for property ID: " + propertyId)
                        .that(error.getErrorCode()).isEqualTo(STATUS_ERROR_INTERNAL_ERROR);
                assertThat(error.getVendorErrorCode()).isEqualTo(0);
            }
            if (propertyId == PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE
                    || propertyId == PROP_ERROR_EVENT_NOT_AVAILABLE_DISABLED) {
                assertWithMessage("receive correct error for property ID: " + propertyId)
                        .that(error.getErrorCode()).isEqualTo(STATUS_ERROR_NOT_AVAILABLE);
                assertThat(error.getVendorErrorCode()).isEqualTo(0);
            }
            if (propertyId == PROP_CAUSE_STATUS_CODE_TRY_AGAIN) {
                assertWithMessage("receive correct error for property ID: " + propertyId)
                        .that(error.getErrorCode()).isEqualTo(STATUS_ERROR_TIMEOUT);
                assertThat(error.getVendorErrorCode()).isEqualTo(0);
            }
            if (error.getRequestId() == vendorErrorRequestId) {
                assertWithMessage("receive correct error for property ID: " + propertyId)
                        .that(error.getErrorCode()).isEqualTo(STATUS_ERROR_NOT_AVAILABLE);
                assertThat(error.getVendorErrorCode()).isEqualTo(VENDOR_CODE_FOR_NOT_AVAILABLE);
            }
        }
    }

    @Test
    public void testGetVendorErrorCode_forGetProperty_throwsNotAvailable_EqualAfterR() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isAtLeast(Build.VERSION_CODES.R);
        PropertyNotAvailableException thrown = assertThrows(PropertyNotAvailableException.class,
                () -> mManager.getProperty(PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE_WITH_VENDOR_CODE,
                        /* areaId= */ 0));

        assertThat(thrown.getVendorErrorCode()).isEqualTo(VENDOR_CODE_FOR_NOT_AVAILABLE);
    }

    @Test
    public void testGetVendorErrorCode_forGetProperty_throwsInternalError_EqualAfterR() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isAtLeast(Build.VERSION_CODES.R);
        CarInternalErrorException thrown = assertThrows(CarInternalErrorException.class,
                () -> mManager.getProperty(PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR_WITH_VENDOR_CODE,
                        /* areaId= */ 0));

        assertThat(thrown.getVendorErrorCode()).isEqualTo(VENDOR_CODE_FOR_INTERNAL_ERROR);
    }

    @Test
    public void testGetVendorErrorCode_forSetProperty_throwsNotAvailable_EqualAfterR() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isAtLeast(Build.VERSION_CODES.R);
        PropertyNotAvailableException thrown = assertThrows(PropertyNotAvailableException.class,
                () -> mManager.setProperty(Integer.class,
                        PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE_WITH_VENDOR_CODE, /* areaId= */ 0,
                        /* val= */ 0));

        assertThat(thrown.getVendorErrorCode()).isEqualTo(VENDOR_CODE_FOR_NOT_AVAILABLE);
    }

    @Test
    public void testGetVendorErrorCode_forSetProperty_throwsInternalError_EqualAfterR() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isAtLeast(Build.VERSION_CODES.R);
        CarInternalErrorException thrown = assertThrows(CarInternalErrorException.class,
                () -> mManager.setProperty(Integer.class,
                        PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR_WITH_VENDOR_CODE, /* areaId= */0,
                        /* val= */ 0));

        assertThat(thrown.getVendorErrorCode()).isEqualTo(VENDOR_CODE_FOR_INTERNAL_ERROR);
    }

    @Override
    protected void configureMockedHal() {
        PropertyHandler handler = new PropertyHandler();
        addAidlProperty(CUSTOM_SEAT_MIXED_PROP_ID_1, handler).setConfigArray(CONFIG_ARRAY_1)
                .addAreaConfig(DRIVER_SIDE_AREA_ID).addAreaConfig(PASSENGER_SIDE_AREA_ID);
        addAidlProperty(CUSTOM_GLOBAL_MIXED_PROP_ID_2, handler).setConfigArray(CONFIG_ARRAY_2);
        addAidlProperty(CUSTOM_GLOBAL_MIXED_PROP_ID_3, handler).setConfigArray(CONFIG_ARRAY_3);
        addAidlProperty(CUSTOM_GLOBAL_INT_ARRAY_PROP, handler);

        addAidlProperty(VehicleProperty.TIRE_PRESSURE, handler).addAreaConfig(
                VehicleAreaWheel.WHEEL_LEFT_REAR).addAreaConfig(
                VehicleAreaWheel.WHEEL_RIGHT_REAR).addAreaConfig(
                VehicleAreaWheel.WHEEL_RIGHT_FRONT).addAreaConfig(
                VehicleAreaWheel.WHEEL_LEFT_FRONT).setChangeMode(
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS).setMaxSampleRate(
                10).setMinSampleRate(1);

        VehiclePropValue tempValue = new VehiclePropValue();
        tempValue.value = new RawPropValues();
        tempValue.value.floatValues = new float[]{INIT_TEMP_VALUE};
        tempValue.prop = VehicleProperty.HVAC_TEMPERATURE_SET;
        tempValue.areaId = DRIVER_SIDE_AREA_ID;
        addAidlProperty(VehicleProperty.HVAC_TEMPERATURE_SET, tempValue)
                .addAreaConfig(DRIVER_SIDE_AREA_ID, /* minValue = */ 10, /* maxValue = */ 20)
                .addAreaConfig(PASSENGER_SIDE_AREA_ID, /* minValue = */ 10, /* maxValue = */ 20);
        VehiclePropValue vinValue = new VehiclePropValue();
        vinValue.value = new RawPropValues();
        vinValue.value.stringValue = TEST_VIN;
        vinValue.prop = VehicleProperty.INFO_VIN;
        addAidlProperty(VehicleProperty.INFO_VIN, vinValue);
        addAidlProperty(VehicleProperty.FUEL_DOOR_OPEN);
        addAidlProperty(VehicleProperty.ANDROID_EPOCH_TIME);

        addAidlProperty(PROP_VALUE_STATUS_ERROR_INT_ARRAY, handler);
        addAidlProperty(PROP_VALUE_STATUS_UNKNOWN_INT_ARRAY, handler);
        addAidlProperty(PROP_VALUE_STATUS_UNAVAILABLE_INT, handler);
        addAidlProperty(PROP_VALUE_STATUS_UNAVAILABLE_FLOAT, handler);
        addAidlProperty(PROP_VALUE_STATUS_ERROR_BOOLEAN, handler);
        addAidlProperty(PROP_VALUE_STATUS_UNAVAILABLE_SEAT, handler)
                .addAreaConfig(DRIVER_SIDE_AREA_ID).addAreaConfig(PASSENGER_SIDE_AREA_ID)
                .setChangeMode(CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS)
                .setMaxSampleRate(10).setMinSampleRate(1);

        addAidlProperty(PROP_CAUSE_STATUS_CODE_ACCESS_DENIED, handler);
        addAidlProperty(PROP_CAUSE_STATUS_CODE_TRY_AGAIN, handler);
        addAidlProperty(PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR, handler);
        addAidlProperty(PROP_CAUSE_STATUS_CODE_INVALID_ARG, handler);
        addAidlProperty(PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE, handler);
        addAidlProperty(PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE_WITH_VENDOR_CODE, handler);
        addAidlProperty(PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR_WITH_VENDOR_CODE, handler);

        addAidlProperty(PROP_CAUSE_STATUS_CODE_UNKNOWN, handler);

        addAidlProperty(CUSTOM_SEAT_INT_PROP_1, handler).addAreaConfig(DRIVER_SIDE_AREA_ID)
                .addAreaConfig(PASSENGER_SIDE_AREA_ID);
        addAidlProperty(CUSTOM_SEAT_INT_PROP_2, handler).addAreaConfig(DRIVER_SIDE_AREA_ID)
                .addAreaConfig(PASSENGER_SIDE_AREA_ID);

        addAidlProperty(NULL_VALUE_PROP, handler);
        addAidlProperty(PROP_UNSUPPORTED, handler);
        addAidlProperty(PROP_ERROR_EVENT_NOT_AVAILABLE_DISABLED, handler);

        // Add properties for permission testing.
        addAidlProperty(SUPPORT_CUSTOM_PERMISSION, handler).setConfigArray(
                VENDOR_PERMISSION_CONFIG);
        addAidlProperty(PROP_WITH_READ_ONLY_PERMISSION, handler);
        addAidlProperty(PROP_WITH_WRITE_ONLY_PERMISSION, handler);

        addAidlProperty(PROP_UNSUPPORTED, handler);
        addAidlProperty(VehicleProperty.DISTANCE_DISPLAY_UNITS);
    }

    private class PropertyHandler implements VehicleHalPropertyHandler {
        SparseArray<SparseArray<VehiclePropValue>> mValueByAreaIdByPropId = new SparseArray<>();

        @Override
        public synchronized boolean onPropertySet2(VehiclePropValue value) {
            // Simulate VehicleHal.set() behavior.
            if (value.prop == PROP_ERROR_EVENT_NOT_AVAILABLE_DISABLED) {
                injectErrorEvent(PROP_ERROR_EVENT_NOT_AVAILABLE_DISABLED, /* areaId= */ 0,
                        VehicleHalStatusCode.STATUS_NOT_AVAILABLE_DISABLED);
                // Don't generate property change event.
                return false;
            }

            int statusCode = mapPropertyToVhalStatusCode(value.prop);
            if (statusCode != VehicleHalStatusCode.STATUS_OK) {
                // The ServiceSpecificException here would pass the statusCode back to caller.
                throw new ServiceSpecificException(statusCode);
            }

            int areaId = value.areaId;
            int propId = value.prop;
            if (mValueByAreaIdByPropId.get(propId) == null) {
                mValueByAreaIdByPropId.put(propId, new SparseArray<>());
            }
            mValueByAreaIdByPropId.get(propId).put(areaId, value);
            return true;
        }

        @Override
        public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
            // Simulate VehicleHal.get() behavior.
            int vhalStatusCode = mapPropertyToVhalStatusCode(value.prop);
            if (vhalStatusCode != VehicleHalStatusCode.STATUS_OK) {
                // The ServiceSpecificException here would pass the statusCode back to caller.
                throw new ServiceSpecificException(vhalStatusCode);
            }

            if (value.prop == NULL_VALUE_PROP) {
                // Return null to simulate an unavailable property.
                // HAL implementation should return STATUS_TRY_AGAIN when a property is unavailable,
                // however, it may also return null with STATUS_OKAY, and we want to handle this
                // properly.
                return null;
            }

            VehiclePropValue returnValue = new VehiclePropValue();
            returnValue.prop = value.prop;
            returnValue.areaId = value.areaId;
            returnValue.timestamp = SystemClock.elapsedRealtimeNanos();
            returnValue.value = new RawPropValues();
            int propertyStatus = mapPropertyToVehiclePropertyStatus(value.prop);
            if (propertyStatus != VehiclePropertyStatus.AVAILABLE) {
                returnValue.status = propertyStatus;
                return returnValue;
            }
            if (mValueByAreaIdByPropId.get(value.prop) == null) {
                return null;
            }
            return mValueByAreaIdByPropId.get(value.prop).get(value.areaId);
        }

        @Override
        public synchronized void onPropertySubscribe(int property, float sampleRate) {
            Log.d(TAG, "onPropertySubscribe property "
                    + property + " sampleRate " + sampleRate);
        }

        @Override
        public synchronized void onPropertyUnsubscribe(int property) {
            Log.d(TAG, "onPropertyUnSubscribe property " + property);
        }
    }

    private static int mapPropertyToVhalStatusCode(int propId) {
        switch (propId) {
            case PROP_CAUSE_STATUS_CODE_TRY_AGAIN:
                return VehicleHalStatusCode.STATUS_TRY_AGAIN;
            case PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE:
                return VehicleHalStatusCode.STATUS_NOT_AVAILABLE;
            case PROP_CAUSE_STATUS_CODE_ACCESS_DENIED:
                return VehicleHalStatusCode.STATUS_ACCESS_DENIED;
            case PROP_CAUSE_STATUS_CODE_INVALID_ARG:
                return VehicleHalStatusCode.STATUS_INVALID_ARG;
            case PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR:
                return VehicleHalStatusCode.STATUS_INTERNAL_ERROR;
            case PROP_CAUSE_STATUS_CODE_UNKNOWN:
                return -1;
            case PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR_WITH_VENDOR_CODE:
                return INTERNAL_ERROR_WITH_VENDOR_CODE;
            case PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE_WITH_VENDOR_CODE:
                return NOT_AVAILABLE_WITH_VENDOR_CODE;
            default:
                return VehicleHalStatusCode.STATUS_OK;
        }
    }

    private static int mapPropertyToVehiclePropertyStatus(int propId) {
        switch (propId) {
            case PROP_VALUE_STATUS_ERROR_INT_ARRAY:
            case PROP_VALUE_STATUS_ERROR_BOOLEAN:
                return VehiclePropertyStatus.ERROR;
            case PROP_VALUE_STATUS_UNAVAILABLE_INT:
            case PROP_VALUE_STATUS_UNAVAILABLE_FLOAT:
                return VehiclePropertyStatus.UNAVAILABLE;
            case PROP_VALUE_STATUS_UNKNOWN_INT_ARRAY:
                return -1;
            default:
                return VehiclePropertyStatus.AVAILABLE;
        }
    }

    private static VehiclePropValue newTirePressureVehiclePropValue(int areaId, float floatValue,
            long timestampNanos) {
        VehiclePropValue vehiclePropValue = new VehiclePropValue();
        vehiclePropValue.prop = VehiclePropertyIds.TIRE_PRESSURE;
        vehiclePropValue.areaId = areaId;
        vehiclePropValue.value = new RawPropValues();
        vehiclePropValue.value.floatValues = new float[]{floatValue};
        vehiclePropValue.timestamp = timestampNanos;
        return vehiclePropValue;
    }

    private static void assertTirePressureCarPropertyValue(CarPropertyValue<?> carPropertyValue,
            int areaId, float floatValue, long timestampNanos) {
        assertThat(carPropertyValue.getPropertyId()).isEqualTo(VehiclePropertyIds.TIRE_PRESSURE);
        assertThat(carPropertyValue.getAreaId()).isEqualTo(areaId);
        assertThat(carPropertyValue.getStatus()).isEqualTo(CarPropertyValue.STATUS_AVAILABLE);
        assertThat(carPropertyValue.getTimestamp()).isEqualTo(timestampNanos);
        assertThat(carPropertyValue.getValue()).isEqualTo(floatValue);
    }

    private static class TestCallback implements CarPropertyManager.CarPropertyEventCallback {
        private static final String CALLBACK_TAG = "ErrorEventTest";

        private final int mInitValueCount;
        private final int mChangeEventCount;
        private final int mErrorEventCount;
        private CountDownLatch mInitialValueCdLatch;
        private CountDownLatch mChangeEventCdLatch;
        private CountDownLatch mErrorEventCdLatch;

        private final Object mLock = new Object();
        private final List<CarPropertyValue> mChangeEvents = new ArrayList<>();
        private final List<CarPropertyValue> mInitialValues = new ArrayList<>();
        private Integer mErrorCode;

        TestCallback(int initValueCount, int changeEventCount, int errorEventCount) {
            mInitValueCount = initValueCount;
            mChangeEventCount = changeEventCount;
            mErrorEventCount = errorEventCount;
            // We expect to receive one initial event for each area.
            mInitialValueCdLatch = new CountDownLatch(mInitValueCount);
            mChangeEventCdLatch = new CountDownLatch(mChangeEventCount);
            mErrorEventCdLatch = new CountDownLatch(mErrorEventCount);
        }

        @Override
        public void onChangeEvent(CarPropertyValue carPropertyValue) {
            Log.d(CALLBACK_TAG, "onChangeEvent: " + carPropertyValue);
            synchronized (mLock) {
                if (mInitialValueCdLatch.getCount() > 0) {
                    mInitialValueCdLatch.countDown();
                    mInitialValues.add(carPropertyValue);
                } else {
                    mChangeEventCdLatch.countDown();
                    mChangeEvents.add(carPropertyValue);
                }
            }
        }

        @Override
        public void onErrorEvent(int propId, int areaId) {
            Log.d(CALLBACK_TAG, "onErrorEvent, propId: " + propId + " areaId: " + areaId);
            synchronized (mLock) {
                mErrorEventCdLatch.countDown();
            }
        }

        @Override
        public void onErrorEvent(int propId, int areaId, int errorCode) {
            Log.d(CALLBACK_TAG, "onErrorEvent, propId: " + propId + " areaId: " + areaId
                    + "errorCode: " + errorCode);
            synchronized (mLock) {
                mErrorCode = errorCode;
                mErrorEventCdLatch.countDown();
            }
        }

        public Integer getErrorCode() {
            synchronized (mLock) {
                return mErrorCode;
            }
        }

        public void assertOnErrorEventCalled() throws InterruptedException {
            if (!mErrorEventCdLatch.await(CALLBACK_SHORT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                long got = mErrorEventCount - mErrorEventCdLatch.getCount();
                throw new IllegalStateException("Does not receive enough error events before  "
                        + CALLBACK_SHORT_TIMEOUT_MS + " ms, got: " + got
                        + ", expected: " + mErrorEventCount);
            }
        }

        public void assertOnErrorEventNotCalled() throws InterruptedException {
            if (mErrorEventCdLatch.await(CALLBACK_SHORT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                long got = mErrorEventCount - mErrorEventCdLatch.getCount();
                throw new IllegalStateException("Receive more error events than expected after  "
                        + CALLBACK_SHORT_TIMEOUT_MS + " ms, got: " + got
                        + ", expected less than: " + mErrorEventCount);
            }
        }

        public void assertRegisterCompleted() throws InterruptedException {
            assertRegisterCompleted(CALLBACK_SHORT_TIMEOUT_MS);
        }

        public void assertRegisterCompleted(int timeoutMs) throws InterruptedException {
            if (!mInitialValueCdLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                long got = mInitValueCount - mInitialValueCdLatch.getCount();
                throw new IllegalStateException("Does not receive enough initial value events "
                        + "before  " + CALLBACK_SHORT_TIMEOUT_MS + " ms, got: " + got
                        + ", expected: " + mInitValueCount);
            }
        }

        public int getChangeEventCounter() {
            synchronized (mLock) {
                return mChangeEvents.size();
            }
        }

        public List<CarPropertyValue> waitAndGetChangeEvents() throws InterruptedException {
            if (!mChangeEventCdLatch.await(CALLBACK_SHORT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                long got = mChangeEventCount - mChangeEventCdLatch.getCount();
                throw new IllegalStateException("Does not receive enough property events before  "
                        + CALLBACK_SHORT_TIMEOUT_MS + " ms, got: " + got
                        + ", expected: " + mChangeEventCount);
            }
            synchronized (mLock) {
                return mChangeEvents;
            }
        }

        public List<CarPropertyValue> getInitialValues() {
            synchronized (mLock) {
                return mInitialValues;
            }
        }
    }

    // This is almost the same as {@link android.os.HandlerExecutor} except that it will not throw
    // exception even if the underlying handler is already shut down.
    private static class HandlerExecutor implements Executor {
        private final Handler mHandler;

        HandlerExecutor(@NonNull Handler handler) {
            mHandler = handler;
        }

        @Override
        public void execute(Runnable command) {
            mHandler.post(command);
        }
    }

}
