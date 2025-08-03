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
package android.car.cts.permissiontest.hardware.property;

import static android.car.Car.PERMISSION_VENDOR_EXTENSION;
import static android.car.hardware.property.VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_INFO;
import static android.car.hardware.property.VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_SEAT;
import static android.car.hardware.property.VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_INFO;
import static android.hardware.automotive.vehicle.TestVendorProperty.MIXED_TYPE_PROPERTY_FOR_TEST;
import static android.hardware.automotive.vehicle.TestVendorProperty.VENDOR_EXTENSION_FLOAT_PROPERTY;
import static android.hardware.automotive.vehicle.TestVendorProperty.VENDOR_EXTENSION_INT_PROPERTY;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.car.Car;
import android.car.VehicleAreaSeat;
import android.car.VehicleAreaWindow;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.property.CarPropertyManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import android.car.cts.permissiontest.AbstractCarManagerPermissionTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This class contains tests for custom vendor permission for {@link CarPropertyManager}.
 *
 * This test is based on the following customize vendor permission config of the reference VHAL
 * implementation.
 *
 * MIXED_TYPE_PROPERTY_FOR_TEST:
 *     "VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_INFO",
 *     "VehicleVendorPermission::PERMISSION_SET_VENDOR_CATEGORY_INFO",
 * VENDOR_EXTENSION_INT_PROPERTY:
 *     "VehicleVendorPermission::PERMISSION_GET_VENDOR_CATEGORY_SEAT",
 *     "VehicleVendorPermission::PERMISSION_NOT_ACCESSIBLE",
 * VENDOR_EXTENSION_FLOAT_PROPERTY:
 *     "VehicleVendorPermission::PERMISSION_DEFAULT",
 *     "VehicleVendorPermission::PERMISSION_DEFAULT"
 */
@RunWith(AndroidJUnit4.class)
public final class CarVendorPropertyCustomPermissionTest extends AbstractCarManagerPermissionTest {
    private static final int HVAC_LEFT = VehicleAreaSeat.SEAT_ROW_1_LEFT
            | VehicleAreaSeat.SEAT_ROW_2_LEFT | VehicleAreaSeat.SEAT_ROW_2_CENTER;

    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private CarPropertyManager mCarPropertyManager;

    @Before
    public void setUp() {
        super.connectCar();
        mCarPropertyManager = (CarPropertyManager) mCar.getCarManager(Car.PROPERTY_SERVICE);
    }

    private void assumePropertyIsSupported(String permission, int property) {
        mUiAutomation.adoptShellPermissionIdentity(permission);

        CarPropertyConfig<?> config = mCarPropertyManager.getCarPropertyConfig(property);

        mUiAutomation.dropShellPermissionIdentity();

        assumeTrue("Property: " + VehiclePropertyIds.toString(property) + " is not supported",
                config != null);
    }

    @Test
    public void testGetMixedTypePropertyForTest() {
        assumePropertyIsSupported(PERMISSION_GET_CAR_VENDOR_CATEGORY_INFO,
                MIXED_TYPE_PROPERTY_FOR_TEST);
        mUiAutomation.adoptShellPermissionIdentity(PERMISSION_GET_CAR_VENDOR_CATEGORY_INFO);

        mCarPropertyManager.getProperty(Object[].class, MIXED_TYPE_PROPERTY_FOR_TEST,
                /* areaId= */ 0);

        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testGetMixedTypePropertyForTest_noPermission() {
        assumePropertyIsSupported(PERMISSION_GET_CAR_VENDOR_CATEGORY_INFO,
                MIXED_TYPE_PROPERTY_FOR_TEST);

        assertThrows(SecurityException.class, () -> mCarPropertyManager.getProperty(Object[].class,
                MIXED_TYPE_PROPERTY_FOR_TEST, /* areaId= */ 0));
    }

    @Test
    public void testSetMixedTypePropertyForTest() {
        assumePropertyIsSupported(PERMISSION_GET_CAR_VENDOR_CATEGORY_INFO,
                MIXED_TYPE_PROPERTY_FOR_TEST);
        mUiAutomation.adoptShellPermissionIdentity(PERMISSION_SET_CAR_VENDOR_CATEGORY_INFO);

        mCarPropertyManager.setProperty(Object[].class, MIXED_TYPE_PROPERTY_FOR_TEST,
                /* areaId= */ 0, /* val= */ new Object[0]);

        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testSetMixedTypePropertyForTest_noPermission() {
        assumePropertyIsSupported(PERMISSION_GET_CAR_VENDOR_CATEGORY_INFO,
                MIXED_TYPE_PROPERTY_FOR_TEST);

        assertThrows(SecurityException.class, () -> mCarPropertyManager.setProperty(Object[].class,
                MIXED_TYPE_PROPERTY_FOR_TEST, /* areaId= */ 0, /* val= */ new Object[0]));
    }

    @Test
    public void testGetVendorExtensionIntProperty() {
        assumePropertyIsSupported(PERMISSION_GET_CAR_VENDOR_CATEGORY_SEAT,
                VENDOR_EXTENSION_INT_PROPERTY);
        mUiAutomation.adoptShellPermissionIdentity(PERMISSION_GET_CAR_VENDOR_CATEGORY_SEAT);

        // Although we require PERMISSION_GET_CAR_VENDOR_CATEGORY_SEAT,
        // VENDOR_EXTENSION_INT_PROPERTY is actually a window area property.
        mCarPropertyManager.getIntProperty(VENDOR_EXTENSION_INT_PROPERTY,
                VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD);

        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testGetVendorExtensionIntProperty_noPermissions() {
        assumePropertyIsSupported(PERMISSION_GET_CAR_VENDOR_CATEGORY_SEAT,
                VENDOR_EXTENSION_INT_PROPERTY);

        // Although we require PERMISSION_GET_CAR_VENDOR_CATEGORY_SEAT,
        // VENDOR_EXTENSION_INT_PROPERTY is actually a window area property.
        assertThrows(SecurityException.class, () -> mCarPropertyManager.getIntProperty(
                VENDOR_EXTENSION_INT_PROPERTY, VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD));
    }

    @Test
    public void testSetVendorExtensionIntProperty() {
        assumePropertyIsSupported(PERMISSION_GET_CAR_VENDOR_CATEGORY_SEAT,
                VENDOR_EXTENSION_INT_PROPERTY);

        // The set permission is PERMISSION_NOT_ACCESSIBLE.
        assertThrows(SecurityException.class, () -> mCarPropertyManager.setIntProperty(
                VENDOR_EXTENSION_INT_PROPERTY, VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD,
                /* val= */ 0));
    }

    @Test
    public void testGetVendorExtensionFloatProperty() {
        assumePropertyIsSupported(PERMISSION_VENDOR_EXTENSION,
                VENDOR_EXTENSION_FLOAT_PROPERTY);
        mUiAutomation.adoptShellPermissionIdentity(PERMISSION_VENDOR_EXTENSION);

        mCarPropertyManager.getFloatProperty(VENDOR_EXTENSION_FLOAT_PROPERTY, HVAC_LEFT);

        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testGetVendorExtensionFloatProperty_noPermission() {
        assumePropertyIsSupported(PERMISSION_VENDOR_EXTENSION,
                VENDOR_EXTENSION_FLOAT_PROPERTY);

        assertThrows(SecurityException.class, () -> mCarPropertyManager.getFloatProperty(
                VENDOR_EXTENSION_FLOAT_PROPERTY, HVAC_LEFT));
    }

    @Test
    public void testSetVendorExtensionFloatProperty() {
        assumePropertyIsSupported(PERMISSION_VENDOR_EXTENSION,
                VENDOR_EXTENSION_FLOAT_PROPERTY);
        mUiAutomation.adoptShellPermissionIdentity(PERMISSION_VENDOR_EXTENSION);

        mCarPropertyManager.setFloatProperty(VENDOR_EXTENSION_FLOAT_PROPERTY, HVAC_LEFT,
                /* val= */ 0f);

        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testSetVendorExtensionFloatProperty_noPermission() {
        assumePropertyIsSupported(PERMISSION_VENDOR_EXTENSION,
                VENDOR_EXTENSION_FLOAT_PROPERTY);

        assertThrows(SecurityException.class, () -> mCarPropertyManager.setFloatProperty(
                VENDOR_EXTENSION_FLOAT_PROPERTY, HVAC_LEFT,
                /* val= */ 0f));
    }
}
