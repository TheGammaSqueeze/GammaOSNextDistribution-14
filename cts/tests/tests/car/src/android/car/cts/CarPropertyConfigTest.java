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
 * limitations under the License
 */

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.car.Car;
import android.car.VehicleAreaType;
import android.car.VehiclePropertyIds;
import android.car.VehiclePropertyType;
import android.car.cts.utils.ShellPermissionUtils;
import android.car.feature.Flags;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.property.AreaIdConfig;
import android.car.hardware.property.CarPropertyManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Test.None;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@SmallTest
@RequiresDevice
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant apps cannot get car related permissions.")
public final class CarPropertyConfigTest extends AbstractCarTestCase {

    private static final String TAG = CarPropertyConfigTest.class.getSimpleName();
    private static final float EPSILON = 0.00001f;

    private List<CarPropertyConfig> mConfigs;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        CarPropertyManager carPropertyManager = (CarPropertyManager) getCar().getCarManager(
                Car.PROPERTY_SERVICE);
        ShellPermissionUtils.runWithShellPermissionIdentity(
                () -> mConfigs = carPropertyManager.getPropertyList());
        assertThat(mConfigs.size()).isAtLeast(4);
    }

    @Test
    public void testGetPropertyId() {
        List<Integer> expectedPropertyTypes = Arrays.asList(
                VehiclePropertyType.STRING,
                VehiclePropertyType.BOOLEAN,
                VehiclePropertyType.INT32,
                VehiclePropertyType.INT32_VEC,
                VehiclePropertyType.INT64,
                VehiclePropertyType.INT64_VEC,
                VehiclePropertyType.FLOAT,
                VehiclePropertyType.FLOAT_VEC,
                VehiclePropertyType.BYTES,
                VehiclePropertyType.MIXED);

        for (CarPropertyConfig cfg : mConfigs) {
            int propId = cfg.getPropertyId();

            boolean verifyGroup =
                (propId & VehiclePropertyGroup.MASK) == VehiclePropertyGroup.VENDOR ||
                   (propId & VehiclePropertyGroup.MASK) == VehiclePropertyGroup.SYSTEM;
            Assert.assertTrue(verifyGroup);

            int propertyType = propId & VehiclePropertyType.MASK;
            assertThat(expectedPropertyTypes).contains(propertyType);
        }
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_AREA_ID_CONFIG_ACCESS)
    public void testGetAccess_AreaIdConfigAccessDisabled() {
        List<Integer> expectedAccessCodes = Arrays.asList(
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_NONE,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE);
        for (CarPropertyConfig cfg : mConfigs) {
            int result = cfg.getAccess();
            assertThat(expectedAccessCodes).contains(result);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_AREA_ID_CONFIG_ACCESS)
    public void testGetAccess_AreaIdConfigAccessEnabled() {
        for (CarPropertyConfig<?> cfg : mConfigs) {
            boolean readOnlyPresent = false;
            boolean writeOnlyPresent = false;
            boolean readWritePresent = false;
            for (AreaIdConfig<?> areaIdConfig : cfg.getAreaIdConfigs()) {
                int areaIdAccess = areaIdConfig.getAccess();
                if (areaIdAccess == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ) {
                    readOnlyPresent = true;
                } else if (areaIdAccess == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE) {
                    writeOnlyPresent = true;
                } else if (areaIdAccess == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE) {
                    readWritePresent = true;
                } else {
                    throw new AssertionError("AreaIdConfig must have an access level of one of the"
                            + " following: {READ, WRITE, READ_WRITE}");
                }
            }
            assertThat(readOnlyPresent || writeOnlyPresent || readWritePresent).isTrue();

            int propertyIdAccess = cfg.getAccess();
            if (propertyIdAccess == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ) {
                assertThat(readOnlyPresent && !writeOnlyPresent).isTrue();
            } else if (propertyIdAccess == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE) {
                assertThat(!readOnlyPresent && writeOnlyPresent && !readWritePresent).isTrue();
            } else if (propertyIdAccess == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE) {
                assertThat(!readOnlyPresent && !writeOnlyPresent && readWritePresent).isTrue();
            } else {
                throw new AssertionError("CarPropertyConfig must have an access level of one of the"
                        + " following: {READ, WRITE, READ_WRITE}");
            }
        }
    }

    @Test
    public void testGetAreaType() {
        List<Integer> expectedAreaTypes = Arrays.asList(
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW,
                VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                VehicleAreaType.VEHICLE_AREA_TYPE_DOOR,
                VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                VehicleAreaType.VEHICLE_AREA_TYPE_WHEEL,
                VehicleAreaType.VEHICLE_AREA_TYPE_VENDOR);
        for (CarPropertyConfig cfg : mConfigs) {
            int result = 0;
            try {
                result = cfg.getAreaType();
            } catch (RuntimeException e) {
                if (!Flags.androidVicVehicleProperties()) {
                    // The VENDOR Area Type (134217728) was added with the Android VIC vehicle
                    // properties.
                    assertThat(e.getMessage()).contains("Unsupported area type 134217728");
                } else {
                    Assert.fail("Unexpected Runtime Exception for property: "
                            + VehiclePropertyIds.toString(cfg.getPropertyId()));
                }
            }

            assertThat(expectedAreaTypes).contains(result);
            int propertyArea = cfg.getPropertyId() & VehicleArea.MASK;
            if (result == VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL) {
                Assert.assertEquals(VehicleArea.GLOBAL, propertyArea);
            } else if (result == VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW) {
                Assert.assertEquals(VehicleArea.WINDOW, propertyArea);
            } else if (result == VehicleAreaType.VEHICLE_AREA_TYPE_SEAT) {
                Assert.assertEquals(VehicleArea.SEAT, propertyArea);
            } else if (result == VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR) {
                Assert.assertEquals(VehicleArea.MIRROR, propertyArea);
            } else if (result == VehicleAreaType.VEHICLE_AREA_TYPE_DOOR) {
                Assert.assertEquals(VehicleArea.DOOR, propertyArea);
            } else if (result == VehicleAreaType.VEHICLE_AREA_TYPE_WHEEL) {
                Assert.assertEquals(VehicleArea.WHEEL, propertyArea);
            } else if (Flags.androidVicVehicleProperties()
                    && result == VehicleAreaType.VEHICLE_AREA_TYPE_VENDOR) {
                Assert.assertEquals(VehicleArea.VENDOR, propertyArea);
            } else {
                Assert.fail("Failed for property: "
                        + VehiclePropertyIds.toString(cfg.getPropertyId()));
            }
        }
    }

    @Test
    public void testGetChangeMode() {
        List<Integer> expectedChangeModes = Arrays.asList(
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS);
        for (CarPropertyConfig cfg : mConfigs) {
            int result = cfg.getChangeMode();
            assertThat(expectedChangeModes).contains(result);
        }
    }

    @Test
    public void testGetConfigArrayAndPropertyId() {
        for (CarPropertyConfig cfg : mConfigs) {
            cfg.getPropertyId();
            Assert.assertNotNull(cfg.getConfigArray());
        }
    }

    @Test
    public void testSampleRate() {
        for (CarPropertyConfig cfg : mConfigs) {
            float maxSampleRate = cfg.getMaxSampleRate();
            float minSampleRate = cfg.getMinSampleRate();
            // Only continuous properties have min/max sample rate.
            if (Math.abs(maxSampleRate - 0.0f) > EPSILON
                    || Math.abs(minSampleRate - 0.0f) > EPSILON) {
                Assert.assertEquals(
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS, cfg.getChangeMode());
            }
        }
    }

    @Test
    public void testGlobalProperty() {
        for (CarPropertyConfig cfg : mConfigs) {
            Assert.assertEquals(
                cfg.getAreaType() == VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                cfg.isGlobalProperty());
        }
    }

    @Test
    public void testGetAreaIds() {
        for (CarPropertyConfig<?> cfg : mConfigs) {
            int[] areaIds = cfg.getAreaIds();
            Assert.assertNotNull(areaIds);
            assertThat(areaIds).isNotEmpty();
            Assert.assertTrue(areaIdCheck(areaIds));
            assertThat(areaIds.length).isEqualTo(cfg.getAreaIdConfigs().size());
            for (int areaId : areaIds) {
                boolean found = false;
                for (AreaIdConfig<?> areaIdConfig : cfg.getAreaIdConfigs()) {
                    if (areaIdConfig.getAreaId() == areaId) {
                        found = true;
                        break;
                    }
                }
                assertWithMessage("Property ID: " + VehiclePropertyIds.toString(cfg.getPropertyId())
                        + " area ID: 0x" + Integer.toHexString(areaId)
                        + " must be found in AreaIdConfigs list").that(found).isTrue();
            }
        }
    }

    @Test
    public void testGetAreaIdConfigs() {
        for (CarPropertyConfig<?> cfg : mConfigs) {
            List<? extends AreaIdConfig<?>> areaIdConfigs = cfg.getAreaIdConfigs();
            assertThat(areaIdConfigs).isNotNull();
            assertThat(areaIdConfigs).isNotEmpty();
            for (AreaIdConfig<?> areaIdConfig : areaIdConfigs) {
                boolean minMaxCorrectlyDefined =
                        (areaIdConfig.getMinValue() != null && areaIdConfig.getMaxValue() != null)
                                || (areaIdConfig.getMinValue() == null
                                && areaIdConfig.getMaxValue() == null);
                assertWithMessage("Property ID: " + VehiclePropertyIds.toString(cfg.getPropertyId())
                        + " area ID: 0x" + Integer.toHexString(areaIdConfig.getAreaId())
                        + " min/max must be both defined or both null").that(
                        minMaxCorrectlyDefined).isTrue();
                if (cfg.getPropertyType().equals(Integer.class)) {
                    if (areaIdConfig.getMinValue() != null) {
                        assertThat((Integer) areaIdConfig.getMaxValue()).isAtLeast(
                                (Integer) areaIdConfig.getMinValue());
                        if (((Integer) areaIdConfig.getMinValue()).equals(0)) {
                            assertThat((Integer) areaIdConfig.getMaxValue()).isNotEqualTo(0);
                        }
                    }
                } else if (cfg.getPropertyType().equals(Long.class)) {
                    if (areaIdConfig.getMinValue() != null) {
                        assertThat((Long) areaIdConfig.getMaxValue()).isAtLeast(
                                (Long) areaIdConfig.getMinValue());
                        if (((Long) areaIdConfig.getMinValue()).equals(0L)) {
                            assertThat((Long) areaIdConfig.getMaxValue()).isNotEqualTo(0L);
                        }
                    }
                } else if (cfg.getPropertyType().equals(Float.class)) {
                    if (areaIdConfig.getMinValue() != null) {
                        assertThat((Float) areaIdConfig.getMaxValue()).isAtLeast(
                                (Float) areaIdConfig.getMinValue());
                        if (((Float) areaIdConfig.getMinValue()).equals(0F)) {
                            assertThat((Float) areaIdConfig.getMaxValue()).isNotEqualTo(0F);
                        }
                    }
                } else {
                    assertThat(areaIdConfig.getMinValue()).isNull();
                    assertThat(areaIdConfig.getMaxValue()).isNull();
                }
                assertThat(areaIdConfig.getSupportedEnumValues()).isNotNull();
                assertThat(areaIdConfig.getSupportedEnumValues()).containsNoDuplicates();
                if (Flags.areaIdConfigAccess()) {
                    assertThat(areaIdConfig.getAccess()).isIn(Arrays.asList(
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE
                    ));
                }
            }
        }
    }

    @Test
    public void testGetAreaIdConfig() {
        for (CarPropertyConfig<?> cfg : mConfigs) {
            for (int areaId : cfg.getAreaIds()) {
                AreaIdConfig<?> areaIdConfig = cfg.getAreaIdConfig(areaId);
                assertThat(areaIdConfig).isNotNull();
                assertThat(areaIdConfig.getAreaId()).isEqualTo(areaId);
                assertThat(areaIdConfig).isIn(cfg.getAreaIdConfigs());
            }
        }
    }

    @Test(expected = None.class /* no exception expected*/)
    public void testGetMinAndMaxValue() {
        for (CarPropertyConfig cfg: mConfigs) {
            cfg.getMinValue();
            cfg.getMaxValue();
            for (int areaId : cfg.getAreaIds()) {
                cfg.getMaxValue(areaId);
                cfg.getMinValue(areaId);
            }
        }
    }

    /**
     * The property value must be independently controllable in any two different AreaIDs
     * in the array. An area must only appear once in the array of AreaIDs. That is, an
     * area must only be part of a single AreaID in the array.
     * @param areaIds
     * @return
     */
    private boolean areaIdCheck(int[] areaIds) {
        for (int i = 0; i < areaIds.length - 1; i++) {
            for (int j = i + 1; j < areaIds.length; j++) {
                if ((areaIds[i] & areaIds[j]) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private final class VehiclePropertyGroup {
        private static final int SYSTEM = 0x10000000;
        private static final int VENDOR = 0x20000000;

        private static final int MASK   = 0xf0000000;
    }

    private final class VehicleArea {
        private static final int GLOBAL = 0x01000000;
        private static final int WINDOW = 0x03000000;
        private static final int MIRROR = 0x04000000;
        private static final int SEAT   = 0x05000000;
        private static final int DOOR   = 0x06000000;
        private static final int WHEEL  = 0x07000000;
        private static final int VENDOR  = 0x08000000;

        private static final int MASK   = 0x0f000000;

    }
}
