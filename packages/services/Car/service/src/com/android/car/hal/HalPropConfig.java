/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.hal;

import android.car.VehicleAreaType;
import android.car.feature.Flags;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.property.AreaIdConfig;
import android.hardware.automotive.vehicle.VehicleArea;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.automotive.vehicle.VehiclePropertyAccess;
import android.hardware.automotive.vehicle.VehiclePropertyChangeMode;
import android.hardware.automotive.vehicle.VehiclePropertyType;

import com.android.car.hal.property.PropertyHalServiceConfigs;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * HalPropConfig represents a vehicle property config.
 */
public abstract class HalPropConfig {
    private static final Set<Integer> CONFIG_ARRAY_DEFINES_SUPPORTED_ENUM_VALUES =
            Set.of(
                    VehicleProperty.GEAR_SELECTION,
                    VehicleProperty.CURRENT_GEAR,
                    VehicleProperty.DISTANCE_DISPLAY_UNITS,
                    VehicleProperty.EV_BATTERY_DISPLAY_UNITS,
                    VehicleProperty.TIRE_PRESSURE_DISPLAY_UNITS,
                    VehicleProperty.FUEL_VOLUME_DISPLAY_UNITS,
                    VehicleProperty.HVAC_TEMPERATURE_DISPLAY_UNITS,
                    VehicleProperty.VEHICLE_SPEED_DISPLAY_UNITS);

    private final PropertyHalServiceConfigs mPropertyHalServiceConfigs =
            PropertyHalServiceConfigs.getInstance();

    /**
     * Get the property ID.
     */
    public abstract int getPropId();

    /**
     * Get the access mode.
     */
    public abstract int getAccess();

    /**
     * Get the change mode.
     */
    public abstract int getChangeMode();

    /**
     * Get the area configs.
     */
    public abstract HalAreaConfig[] getAreaConfigs();

    /**
     * Get the config array.
     */
    public abstract int[] getConfigArray();

    /**
     * Get the config string.
     */
    public abstract String getConfigString();

    /**
     * Get the min sample rate.
     */
    public abstract float getMinSampleRate();

    /**
     * Get the max sample rate.
     */
    public abstract float getMaxSampleRate();

    /**
     * Converts to AIDL or HIDL VehiclePropConfig.
     */
    public abstract Object toVehiclePropConfig();

    private int getCommonAccessFromHalAreaConfigs(HalAreaConfig[] halAreaConfigs) {
        boolean readOnlyPresent = false;
        boolean writeOnlyPresent = false;
        boolean readWritePresent = false;
        for (int i = 0; i < halAreaConfigs.length; i++) {
            int access = halAreaConfigs[i].getAccess();
            switch (access) {
                case CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ:
                    readOnlyPresent = true;
                    break;
                case CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE:
                    writeOnlyPresent = true;
                    break;
                case CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE:
                    readWritePresent = true;
                    break;
                default:
                    // AreaId config has an invalid VehiclePropertyAccess value
                    return CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_NONE;
            }
        }

        if (writeOnlyPresent) {
            if (!readOnlyPresent && !readWritePresent) {
                return CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE;
            }
            // Config cannot set write-only access for some areaId configs and read/read-write
            // access for other areaIds per property.
            return CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_NONE;
        }
        if (readOnlyPresent) {
            return CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ;
        }
        return CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE;
    }

    /**
     * Converts {@link HalPropConfig} to {@link CarPropertyConfig}.
     *
     * @param mgrPropertyId The Property ID used by Car Property Manager, different from the
     *                      property ID used by VHAL.
     */
    public CarPropertyConfig<?> toCarPropertyConfig(int mgrPropertyId) {
        int propId = getPropId();
        int areaType = getVehicleAreaType(propId & VehicleArea.MASK);
        Class<?> clazz = CarPropertyUtils.getJavaClass(propId & VehiclePropertyType.MASK);

        int access = getAccess();
        CarPropertyConfig.Builder carPropertyConfigBuilder = CarPropertyConfig.newBuilder(clazz,
                mgrPropertyId, areaType).setAccess(access).setChangeMode(
                getChangeMode()).setConfigString(getConfigString());

        float maxSampleRate = 0f;
        float minSampleRate = 0f;
        if (getChangeMode() == CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS) {
            maxSampleRate = getMaxSampleRate();
            minSampleRate = getMinSampleRate();
        }
        carPropertyConfigBuilder.setMinSampleRate(minSampleRate).setMaxSampleRate(maxSampleRate);

        int[] configIntArray = getConfigArray();
        ArrayList<Integer> configArray = new ArrayList<>(configIntArray.length);
        long[] supportedEnumValues = null;
        boolean shouldConfigArrayDefineSupportedEnumValues =
                CONFIG_ARRAY_DEFINES_SUPPORTED_ENUM_VALUES.contains(propId);
        if (shouldConfigArrayDefineSupportedEnumValues) {
            supportedEnumValues = new long[configIntArray.length];
        }
        for (int i = 0; i < configIntArray.length; i++) {
            configArray.add(configIntArray[i]);
            if (shouldConfigArrayDefineSupportedEnumValues) {
                supportedEnumValues[i] = (long) configIntArray[i];
            }
        }
        carPropertyConfigBuilder.setConfigArray(configArray);

        HalAreaConfig[] halAreaConfigs = getAreaConfigs();
        if (halAreaConfigs.length == 0) {
            carPropertyConfigBuilder.addAreaIdConfig(generateAreaIdConfig(clazz, /* areaId= */ 0,
                    /* minInt32Value= */ 0, /* maxInt32Value= */ 0,
                    /* minFloatValue= */ 0, /* maxFloatValue= */ 0,
                    /* minInt64Value= */ 0, /* maxInt64Value= */ 0,
                    supportedEnumValues, /* supportVariableUpdateRate= */ false, access));
        } else {
            for (HalAreaConfig halAreaConfig : halAreaConfigs) {
                if (!shouldConfigArrayDefineSupportedEnumValues) {
                    supportedEnumValues = halAreaConfig.getSupportedEnumValues();
                }
                int areaAccess = (halAreaConfig.getAccess() == VehiclePropertyAccess.NONE)
                        ? access : halAreaConfig.getAccess();
                carPropertyConfigBuilder.addAreaIdConfig(
                        generateAreaIdConfig(clazz, halAreaConfig.getAreaId(),
                                halAreaConfig.getMinInt32Value(), halAreaConfig.getMaxInt32Value(),
                                halAreaConfig.getMinFloatValue(), halAreaConfig.getMaxFloatValue(),
                                halAreaConfig.getMinInt64Value(), halAreaConfig.getMaxInt64Value(),
                                supportedEnumValues, halAreaConfig.isVariableUpdateRateSupported(),
                                areaAccess));
            }
        }
        return carPropertyConfigBuilder.build();
    }

    private AreaIdConfig generateAreaIdConfig(Class<?> clazz, int areaId, int minInt32Value,
            int maxInt32Value, float minFloatValue, float maxFloatValue, long minInt64Value,
            long maxInt64Value, long[] supportedEnumValues, boolean supportVariableUpdateRate,
            int access) {
        AreaIdConfig.Builder areaIdConfigBuilder = Flags.areaIdConfigAccess()
                ? new AreaIdConfig.Builder(access, areaId)
                : new AreaIdConfig.Builder(areaId);
        if (classMatched(Integer.class, clazz)) {
            if ((minInt32Value != 0 || maxInt32Value != 0)) {
                areaIdConfigBuilder.setMinValue(minInt32Value).setMaxValue(maxInt32Value);
            }
            // The supported enum values for {@code HVAC_FAN_DIRECTION} are specified by
            // {@code HVAC_FAN_DIRECTION_AVAILABLE} and the supportedEnumValues are never populated.
            if (getChangeMode() == VehiclePropertyChangeMode.ON_CHANGE &&
                    getPropId() != VehicleProperty.HVAC_FAN_DIRECTION) {
                if (supportedEnumValues != null && supportedEnumValues.length > 0) {
                    List<Integer> managerSupportedEnumValues = new ArrayList<>(
                            supportedEnumValues.length);
                    for (int i = 0; i < supportedEnumValues.length; i++) {
                        managerSupportedEnumValues.add((int) supportedEnumValues[i]);
                    }
                    areaIdConfigBuilder.setSupportedEnumValues(managerSupportedEnumValues);
                } else if (mPropertyHalServiceConfigs.getAllPossibleSupportedEnumValues(getPropId())
                        != null) {
                    areaIdConfigBuilder.setSupportedEnumValues(new ArrayList(
                            mPropertyHalServiceConfigs.getAllPossibleSupportedEnumValues(
                                    getPropId())));
                }
            }
        } else if (classMatched(Float.class, clazz) && (minFloatValue != 0 || maxFloatValue != 0)) {
            areaIdConfigBuilder.setMinValue(minFloatValue).setMaxValue(maxFloatValue);
        } else if (classMatched(Long.class, clazz) && (minInt64Value != 0 || maxInt64Value != 0)) {
            areaIdConfigBuilder.setMinValue(minInt64Value).setMaxValue(maxInt64Value);
        }
        areaIdConfigBuilder.setSupportVariableUpdateRate(supportVariableUpdateRate);
        return areaIdConfigBuilder.build();
    }

    private static @VehicleAreaType.VehicleAreaTypeValue int getVehicleAreaType(int halArea) {
        switch (halArea) {
            case VehicleArea.GLOBAL:
                return VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL;
            case VehicleArea.SEAT:
                return VehicleAreaType.VEHICLE_AREA_TYPE_SEAT;
            case VehicleArea.DOOR:
                return VehicleAreaType.VEHICLE_AREA_TYPE_DOOR;
            case VehicleArea.WINDOW:
                return VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW;
            case VehicleArea.MIRROR:
                return VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR;
            case VehicleArea.WHEEL:
                return VehicleAreaType.VEHICLE_AREA_TYPE_WHEEL;
            default:
                if (Flags.androidVicVehicleProperties()) {
                    if (halArea == VehicleArea.VENDOR) {
                        return VehicleAreaType.VEHICLE_AREA_TYPE_VENDOR;
                    }
                }
                throw new RuntimeException("Unsupported area type " + halArea);
        }
    }

    private static boolean classMatched(Class<?> class1, Class<?> class2) {
        return class1 == class2 || class1.getComponentType() == class2;
    }
}
