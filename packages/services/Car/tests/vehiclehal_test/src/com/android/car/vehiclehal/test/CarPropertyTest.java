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
package com.android.car.vehiclehal.test;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static java.lang.Integer.toHexString;

import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.feature.Flags;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.AreaIdConfig;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.CarPropertyManager.CarPropertyEventCallback;
import android.car.hardware.property.VehicleVendorPermission;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.automotive.vehicle.VehiclePropertyGroup;
import android.os.ConditionVariable;
import android.os.SystemClock;
import android.util.ArraySet;
import android.util.Log;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The test suite will execute end-to-end Car Property API test by generating VHAL property data
 * from default VHAL and verify those data on the fly. The test data is coming from assets/ folder
 * in the test APK and will be shared with VHAL to execute the test.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class CarPropertyTest extends E2eCarTestBase {

    private static final String TAG = Utils.concatTag(CarPropertyTest.class);

    private static final String CAR_HVAC_TEST_JSON = "car_hvac_test.json";
    private static final String CAR_HVAC_TEST_SET_JSON = "car_hvac_test.json";
    private static final String CAR_INFO_TEST_JSON = "car_info_test.json";
    // kMixedTypePropertyForTest property ID
    private static final int MIXED_TYPE_PROPERTY = 0x21e01111;

    // kMixedTypePropertyForTest default value
    private static final Object[] DEFAULT_VALUE = {"MIXED property", true, 2, 3, 4.5f};
    private static final String OUT_OF_ORDER_TEST_JSON = "car_property_out_of_order_test.json";

    private static final int HVAC_ALL = 117;
    private static final int HVAC_LEFT = 49;

    private static final int PROP_CHANGE_TIMEOUT_MS = 1000;

    private static final Set<String> VENDOR_PERMISSIONS = new HashSet<>(Arrays.asList(
            Car.PERMISSION_VENDOR_EXTENSION,
            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_WINDOW,
            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_WINDOW,
            // permissions for the property related with door
            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_DOOR,
            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_DOOR,
            // permissions for the property related with seat
            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_SEAT,
            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_SEAT,
            // permissions for the property related with mirror
            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_MIRROR,
            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_MIRROR,

            // permissions for the property related with car's information
            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_INFO,
            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_INFO,
            // permissions for the property related with car's engine
            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_ENGINE,
            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_ENGINE,
            // permissions for the property related with car's HVAC
            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_HVAC,
            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_HVAC,
            // permissions for the property related with car's light
            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_LIGHT,
            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_LIGHT,

            // permissions reserved for other vendor permission
            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_1,
            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_1,
            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_2,
            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_2,
            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_3,
            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_3,
            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_4,
            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_4,
            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_5,
            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_5,
            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_6,
            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_6,
            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_7,
            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_7,
            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_8,
            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_8,
            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_9,
            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_9,
            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_10,
            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_10
    ));

    private boolean mPropertySaved;

    private static class CarPropertyEventReceiver implements CarPropertyEventCallback {

        private VhalEventVerifier mVerifier;

        CarPropertyEventReceiver(VhalEventVerifier verifier) {
            mVerifier = verifier;
        }

        @Override
        public void onChangeEvent(CarPropertyValue carPropertyValue) {
            mVerifier.verify(carPropertyValue);
        }

        @Override
        public void onErrorEvent(final int propertyId, final int zone) {
            Assert.fail("Error: propertyId=" + toHexString(propertyId) + " zone=" + zone);
        }
    }

    // Set a property value and wait until the property value has been updated. This function
    // would also work if the property value is initially the same as the value to be updated to.
    // If this is the case, this function would still send the set value request to vhal but
    // would return immediately after that.
    private void setPropertyAndWaitForValueChange(CarPropertyManager propMgr,
            CarPropertyValue value) throws Exception {
        VhalEventVerifier verifier = new VhalEventVerifier(List.of(value));
        CarPropertyEventReceiver receiver = new CarPropertyEventReceiver(verifier);
        // Register a property change callback to check whether the set value has finished.
        propMgr.registerCallback(receiver, value.getPropertyId(), /* rate= */ 0);
        Class valueClass = value.getValue().getClass();

        propMgr.setProperty(valueClass, value.getPropertyId(), value.getAreaId(), value.getValue());

        boolean result = verifier.waitForEnd(PROP_CHANGE_TIMEOUT_MS);
        propMgr.unregisterCallback(receiver);

        if (!result) {
            throw new Exception("Never received property change event after set"
                    + verifier.getResultString());
        }
    }

    @Before
    public void setUp() throws Exception {
        checkRefAidlVHal();
        saveProperty(VehicleProperty.HVAC_POWER_ON, HVAC_ALL);
        saveProperty(VehicleProperty.HVAC_FAN_DIRECTION, HVAC_ALL);
        saveProperty(VehicleProperty.HVAC_TEMPERATURE_SET, HVAC_LEFT);
        saveProperty(VehicleProperty.HVAC_FAN_SPEED, HVAC_ALL);
        saveProperty(VehicleProperty.GEAR_SELECTION, 0);
        saveProperty(MIXED_TYPE_PROPERTY, 0);
        mPropertySaved = true;
    }

    @After
    public void tearDown() throws Exception {
        if (mPropertySaved) {
            restoreProperty(VehicleProperty.HVAC_POWER_ON, HVAC_ALL);
            restoreProperty(VehicleProperty.HVAC_FAN_DIRECTION, HVAC_ALL);
            restoreProperty(VehicleProperty.HVAC_TEMPERATURE_SET, HVAC_LEFT);
            restoreProperty(VehicleProperty.HVAC_FAN_SPEED, HVAC_ALL);
            restoreProperty(VehicleProperty.GEAR_SELECTION, 0);
            restoreProperty(MIXED_TYPE_PROPERTY, 0);
        }
    }

    @Test
    public void testHvacActionsFromAndroid() throws Exception {
        List<CarPropertyValue> expectedSetEvents = getExpectedEvents(CAR_HVAC_TEST_SET_JSON);

        CarPropertyManager propMgr = (CarPropertyManager) mCar.getCarManager(Car.PROPERTY_SERVICE);
        assertWithMessage("CarPropertyManager is null").that(propMgr).isNotNull();

        // test set method from android side
        for (CarPropertyValue expectedEvent : expectedSetEvents) {
            setPropertyAndWaitForValueChange(propMgr, expectedEvent);

            Class valueClass = expectedEvent.getValue().getClass();
            CarPropertyValue receivedEvent = propMgr.getProperty(valueClass,
                    expectedEvent.getPropertyId(), expectedEvent.getAreaId());

            assertWithMessage("Mismatched events, expected: " + expectedEvent + ", received: "
                    + receivedEvent).that(Utils.areCarPropertyValuesEqual(
                            expectedEvent, receivedEvent)).isTrue();
        }
    }

    /**
     * This test will use VHAL fake JSON generator to generate fake HVAC events and verify
     * CarPropertyManager can receive the property change events.
     * It is simulating the HVAC actions coming from hard buttons in a car.
     */
    @Test
    public void testHvacHardButtonOperations() throws Exception {
        Log.d(TAG, "Prepare HVAC test data");
        List<CarPropertyValue> expectedEvents = getExpectedEvents(CAR_HVAC_TEST_JSON);

        CarPropertyManager propMgr = (CarPropertyManager) mCar.getCarManager(Car.PROPERTY_SERVICE);
        assertWithMessage("CarPropertyManager is null").that(propMgr).isNotNull();

        // test that set from vehicle side will trigger callback to android
        VhalEventVerifier verifier = new VhalEventVerifier(expectedEvents);
        ArraySet<Integer> props = new ArraySet<>();
        for (CarPropertyValue event : expectedEvents) {
                props.add(event.getPropertyId());
        }
        CarPropertyEventReceiver receiver = new CarPropertyEventReceiver(verifier);
        for (Integer prop : props) {
            propMgr.registerCallback(receiver, prop, 0);
        }

        VhalEventGenerator generator = new JsonVhalEventGenerator(mCarTestManager,
                VHAL_DUMP_TIMEOUT_MS).setJson(getTestFileContent(CAR_HVAC_TEST_JSON));
        generator.start();

        int eventReplayTimeoutInMs = 1000;
        boolean result = verifier.waitForEnd(eventReplayTimeoutInMs);
        propMgr.unregisterCallback(receiver);

        assertWithMessage("Detected mismatched events: " + verifier.getResultString()).that(result)
                .isTrue();
    }

    /**
     * Static properties' value should never be changed.
     */
    @Test
    public void testStaticInfoOperations() throws Exception {
        Log.d(TAG, "Prepare static car information");

        List<CarPropertyValue> expectedEvents = getExpectedEvents(CAR_INFO_TEST_JSON);
        CarPropertyManager propMgr = (CarPropertyManager) mCar.getCarManager(Car.PROPERTY_SERVICE);
        assertWithMessage("CarPropertyManager is null").that(propMgr).isNotNull();
        for (CarPropertyValue expectedEvent : expectedEvents) {
            CarPropertyValue actualEvent = propMgr.getProperty(
                    expectedEvent.getPropertyId(), expectedEvent.getAreaId());
            assertWithMessage(String.format(
                    "Mismatched car information data, actual: %s, expected: %s",
                    actualEvent, expectedEvent)).that(
                    Utils.areCarPropertyValuesEqual(actualEvent, expectedEvent)).isTrue();
        }
    }

    /**
     * This test will test set/get on MIX type properties. It needs a vendor property in Google
     * Vehicle HAL. See kMixedTypePropertyForTest in google defaultConfig.h for details.
     */
    @Test
    public void testMixedTypeProperty() throws Exception {
        CarPropertyManager propMgr = (CarPropertyManager) mCar.getCarManager(Car.PROPERTY_SERVICE);
        ArraySet<Integer> propConfigSet = new ArraySet<>();
        propConfigSet.add(MIXED_TYPE_PROPERTY);

        List<CarPropertyConfig> configs = propMgr.getPropertyList(propConfigSet);

        // use google HAL in the test
        assertWithMessage("Can not find MIXED type properties in HAL").that(configs.size())
                .isNotEqualTo(0);

        // test CarPropertyConfig
        CarPropertyConfig<?> cfg = configs.get(0);
        List<Integer> configArrayExpected = Arrays.asList(1, 1, 0, 2, 0, 0, 1, 0, 0);
        assertThat(cfg.getConfigArray().toArray()).isEqualTo(configArrayExpected.toArray());

        // Set the property to DEFAULT_VALUE.
        CarPropertyValue<Object[]> expectedEvent = new CarPropertyValue<>(MIXED_TYPE_PROPERTY,
                /* areaId= */ 0, CarPropertyValue.STATUS_AVAILABLE, /* timestamp= */ 0,
                DEFAULT_VALUE);
        setPropertyAndWaitForValueChange(propMgr, expectedEvent);

        CarPropertyValue<Object[]> propertyValue = propMgr.getProperty(Object[].class,
                MIXED_TYPE_PROPERTY, 0);
        assertThat(propertyValue.getValue()).isEqualTo(DEFAULT_VALUE);

        // Set the property to a new value.
        Object[] expectedValue = {"MIXED property", false, 5, 4, 3.2f};
        expectedEvent = new CarPropertyValue<>(MIXED_TYPE_PROPERTY, /* areaId= */ 0,
                CarPropertyValue.STATUS_AVAILABLE, /* timestamp= */ 0, expectedValue);
        setPropertyAndWaitForValueChange(propMgr, expectedEvent);

        CarPropertyValue<Object[]> result = propMgr.getProperty(Object[].class,
                MIXED_TYPE_PROPERTY, 0);
        assertThat(result.getValue()).isEqualTo(expectedValue);
    }

    /**
     * This test will test the case: vehicle events comes to android out of order.
     * See the events in car_property_test.json.
     */
    @Test
    public void testPropertyEventOutOfOrder() throws Exception {
        CarPropertyManager propMgr = (CarPropertyManager) mCar.getCarManager(Car.PROPERTY_SERVICE);
        assertWithMessage("CarPropertyManager is null").that(propMgr).isNotNull();
        List<CarPropertyValue> expectedEvents = getExpectedEvents(OUT_OF_ORDER_TEST_JSON);

        // We expect the 3rd property update event to come since it is in the correct order.
        GearEventTestCallback cb = new GearEventTestCallback(expectedEvents.get(2));
        propMgr.registerCallback(cb, VehiclePropertyIds.GEAR_SELECTION,
                CarPropertyManager.SENSOR_RATE_ONCHANGE);
        injectEventFromVehicleSide(expectedEvents);
        assertThat(cb.waitForEvent(PROP_CHANGE_TIMEOUT_MS)).isTrue();

        // check VHAL ignored the last event in car_property_test, because it is out of order.
        int currentGear = propMgr.getIntProperty(VehiclePropertyIds.GEAR_SELECTION, 0);
        assertThat(currentGear).isEqualTo(16);

        // The last event is set to a timestamp 20ms in the future. Wait for 100ms here so that
        // new event would have a newer timestamp.
        Thread.sleep(100);
    }

    /**
     * Check only vendor properties have vendor permissions.
     */
    @Test
    public void checkVendorPropertyPermission() {
        CarPropertyManager propMgr = (CarPropertyManager) mCar.getCarManager(Car.PROPERTY_SERVICE);
        List<CarPropertyConfig> configs = propMgr.getPropertyList();
        for (CarPropertyConfig cfg : configs) {
            String readPermission = propMgr.getReadPermission(cfg.getPropertyId());
            String writePermission = propMgr.getWritePermission(cfg.getPropertyId());
            if ((cfg.getPropertyId() & VehiclePropertyGroup.MASK) == VehiclePropertyGroup.VENDOR) {
                assertThat(readPermission == null
                        || VENDOR_PERMISSIONS.contains(readPermission)).isTrue();
                assertThat(writePermission == null
                        || VENDOR_PERMISSIONS.contains(writePermission)).isTrue();
            } else {
                assertThat(readPermission == null
                        || !VENDOR_PERMISSIONS.contains(readPermission)).isTrue();
                assertThat(writePermission == null
                        || !VENDOR_PERMISSIONS.contains(writePermission)).isTrue();
            }
        }
    }

    /**
     * Check system properties' permissions.
     */
    @Test
    public void checkSystemPropertyPermission() {
        CarPropertyManager propMgr = (CarPropertyManager) mCar.getCarManager(Car.PROPERTY_SERVICE);
        List<CarPropertyConfig> configs = propMgr.getPropertyList();
        for (CarPropertyConfig<?> cfg : configs) {
            if ((cfg.getPropertyId() & VehiclePropertyGroup.MASK) == VehiclePropertyGroup.SYSTEM) {
                String readPermission = propMgr.getReadPermission(cfg.getPropertyId());
                String writePermission = propMgr.getWritePermission(cfg.getPropertyId());
                if (Flags.areaIdConfigAccess()) {
                    for (AreaIdConfig<?> areaIdConfig : cfg.getAreaIdConfigs()) {
                        int accessModel = areaIdConfig.getAccess();
                        switch (accessModel) {
                            case CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_NONE:
                                assertWithMessage("cfg: " + cfg + " must not have read permission")
                                        .that(readPermission).isNull();
                                assertWithMessage("cfg: " + cfg + " must not have write permission")
                                        .that(writePermission).isNull();
                                break;
                            case CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE:
                                assertWithMessage("cfg: " + cfg + " must have read permission")
                                        .that(readPermission).isNotNull();
                                assertWithMessage("cfg: " + cfg + " must have write permission")
                                        .that(writePermission).isNotNull();
                                break;
                            case CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE:
                                assertWithMessage("cfg: " + cfg + " must not have read permission")
                                        .that(readPermission).isNull();
                                assertWithMessage("cfg: " + cfg + " must have write permission")
                                        .that(writePermission).isNotNull();
                                break;
                            case CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ:
                                assertWithMessage("cfg: " + cfg + " must have read permission")
                                        .that(readPermission).isNotNull();
                                assertWithMessage("cfg: " + cfg + " must not have write permission")
                                        .that(writePermission).isNull();
                                break;
                            default:
                                Assert.fail(
                                        String.format(
                                                "PropertyId: %d has an invalid access model: %d",
                                                cfg.getPropertyId(), accessModel));
                        }
                    }
                } else {
                    int accessModel = cfg.getAccess();
                    switch (accessModel) {
                        case CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_NONE:
                            assertWithMessage("cfg: " + cfg + " must not have read permission")
                                    .that(readPermission).isNull();
                            assertWithMessage("cfg: " + cfg + " must not have write permission")
                                    .that(writePermission).isNull();
                            break;
                        case CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE:
                            assertWithMessage("cfg: " + cfg + " must have read permission")
                                    .that(readPermission).isNotNull();
                            assertWithMessage("cfg: " + cfg + " must have write permission")
                                    .that(writePermission).isNotNull();
                            break;
                        case CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE:
                            assertWithMessage("cfg: " + cfg + " must not have read permission")
                                    .that(readPermission).isNull();
                            assertWithMessage("cfg: " + cfg + " must have write permission")
                                    .that(writePermission).isNotNull();
                            break;
                        case CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ:
                            assertWithMessage("cfg: " + cfg + " must have read permission")
                                    .that(readPermission).isNotNull();
                            assertWithMessage("cfg: " + cfg + " must not have write permission")
                                    .that(writePermission).isNull();
                            break;
                        default:
                            Assert.fail(String.format(
                                    "PropertyId: %d has an invalid access model: %d",
                                    cfg.getPropertyId(), accessModel));
                    }
                }
            }
        }
    }

    private class GearEventTestCallback implements CarPropertyEventCallback {
        private long mTimestamp = 0L;
        private ConditionVariable mCond = new ConditionVariable();
        private CarPropertyValue mExpectedEvent;

        GearEventTestCallback(CarPropertyValue expectedEvent) {
            mExpectedEvent = expectedEvent;
        }

        @Override
        public void onChangeEvent(CarPropertyValue carPropertyValue) {
            if (carPropertyValue.getPropertyId() != VehiclePropertyIds.GEAR_SELECTION) {
                return;
            }
            if (carPropertyValue.getStatus() == CarPropertyValue.STATUS_AVAILABLE) {
                assertWithMessage("Received events out of order").that(
                        mTimestamp <= carPropertyValue.getTimestamp()).isTrue();
                mTimestamp = carPropertyValue.getTimestamp();
            }

            if (Utils.areCarPropertyValuesEqual(carPropertyValue, mExpectedEvent)) {
                mCond.open();
            }
        }

        @Override
        public void onErrorEvent(final int propertyId, final int zone) {
            Assert.fail("Error: propertyId: x0" + toHexString(propertyId) + " areaId: " + zone);
        }

        public boolean waitForEvent(long timeout) {
            return mCond.block(timeout);
        }
    }

    /**
     * Inject events from vehicle side. It change the value of property even the property is a
     * read_only property such as GEAR_SELECTION. It only works with Google VHAL.
     */
    private void injectEventFromVehicleSide(List<CarPropertyValue> expectedEvents)
            throws IOException {
        Long startTime = SystemClock.elapsedRealtimeNanos();
        for (CarPropertyValue propertyValue : expectedEvents) {
            String propIdStr = Integer.toString(propertyValue.getPropertyId());
            String areaIdStr = Integer.toString(propertyValue.getAreaId());
            String propValueStr = propertyValue.getValue().toString();
            String timestampStr = Long.toString(startTime + propertyValue.getTimestamp());
            String output;

            if (propertyValue.getValue() instanceof Integer
                    || propertyValue.getValue() instanceof Boolean) {
                output = mCarTestManager.dumpVhal(
                        List.of("--inject-event", propIdStr, "-a", areaIdStr, "-i", propValueStr,
                                "-t", timestampStr), VHAL_DUMP_TIMEOUT_MS);
            } else if (propertyValue.getValue() instanceof Float) {
                output = mCarTestManager.dumpVhal(
                        List.of("--inject-event", propIdStr, "-a", areaIdStr, "-f", propValueStr,
                                "-t", timestampStr), VHAL_DUMP_TIMEOUT_MS);
            } else {
                throw new IllegalArgumentException(
                        "Unexpected property type for property " + propertyValue.getPropertyId());
            }

            if (!output.contains("injected")) {
                throw new IllegalArgumentException(
                        "Failed to set the property via VHAL dump, output: " + output);
            }
        }
    }
}
