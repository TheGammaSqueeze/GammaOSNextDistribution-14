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

package com.android.car.hal.test;

import android.annotation.CheckResult;
import android.hardware.automotive.vehicle.VehicleAreaConfig;
import android.hardware.automotive.vehicle.VehiclePropConfig;
import android.hardware.automotive.vehicle.VehiclePropertyAccess;
import android.hardware.automotive.vehicle.VehiclePropertyChangeMode;

import java.util.Collection;

/**
 * A builder class for {@link android.hardware.automotive.vehicle.VehiclePropConfig}
 */
public final class AidlVehiclePropConfigBuilder {

    private final VehiclePropConfig mConfig;

    private AidlVehiclePropConfigBuilder(VehiclePropConfig propConfig) {
        mConfig = clone(propConfig);
    }

    private AidlVehiclePropConfigBuilder(int propId) {
        mConfig = new VehiclePropConfig();
        mConfig.prop = propId;
        mConfig.access = VehiclePropertyAccess.READ_WRITE;
        mConfig.changeMode = VehiclePropertyChangeMode.ON_CHANGE;
        mConfig.configArray = new int[0];
        mConfig.areaConfigs = new VehicleAreaConfig[0];
    }

    private VehiclePropConfig clone(VehiclePropConfig propConfig) {
        VehiclePropConfig newConfig = new VehiclePropConfig();

        newConfig.prop = propConfig.prop;
        newConfig.access = propConfig.access;
        newConfig.changeMode = propConfig.changeMode;
        newConfig.configString = propConfig.configString;
        newConfig.minSampleRate = propConfig.minSampleRate;
        newConfig.maxSampleRate = propConfig.maxSampleRate;
        newConfig.configArray = propConfig.configArray.clone();
        newConfig.areaConfigs = new VehicleAreaConfig[propConfig.areaConfigs.length];
        newConfig.areaConfigs = duplicateAreaConfig(propConfig.areaConfigs);
        return newConfig;
    }

    private void addAreaConfig(VehicleAreaConfig config) {
        int oldLength = mConfig.areaConfigs.length;
        VehicleAreaConfig[] newConfigs = duplicateAreaConfig(mConfig.areaConfigs, oldLength + 1);
        newConfigs[oldLength] = config;
        mConfig.areaConfigs = newConfigs;
    }

    @CheckResult
    public AidlVehiclePropConfigBuilder setAccess(int access) {
        mConfig.access = access;
        for (VehicleAreaConfig areaConfig : mConfig.areaConfigs) {
            areaConfig.access = access;
        }
        return this;
    }

    @CheckResult
    public AidlVehiclePropConfigBuilder setAccessPerAreaId(int access, int areaId) {
        for (VehicleAreaConfig areaConfig : mConfig.areaConfigs) {
            if (areaConfig.areaId == areaId) {
                areaConfig.access = access;
                break;
            }
        }
        return this;
    }

    @CheckResult
    public AidlVehiclePropConfigBuilder setChangeMode(int changeMode) {
        mConfig.changeMode = changeMode;
        return this;
    }

    @CheckResult
    public AidlVehiclePropConfigBuilder setMaxSampleRate(float maxSampleRate) {
        mConfig.maxSampleRate = maxSampleRate;
        return this;
    }

    @CheckResult
    public AidlVehiclePropConfigBuilder setMinSampleRate(float minSampleRate) {
        mConfig.minSampleRate = minSampleRate;
        return this;
    }

    @CheckResult
    public AidlVehiclePropConfigBuilder setConfigString(String configString) {
        mConfig.configString = configString;
        return this;
    }


    @CheckResult
    public AidlVehiclePropConfigBuilder setConfigArray(Collection<Integer> configArray) {
        mConfig.configArray = new int[configArray.size()];
        int i = 0;
        for (int value : configArray) {
            mConfig.configArray[i] = value;
            i++;
        }
        return this;
    }

    @CheckResult
    public  AidlVehiclePropConfigBuilder addAreaConfig(int areaId) {
        VehicleAreaConfig area = new VehicleAreaConfig();
        area.areaId = areaId;
        area.access = mConfig.access;
        addAreaConfig(area);
        return this;
    }

    @CheckResult
    public AidlVehiclePropConfigBuilder addAreaConfig(int areaId, int minValue, int maxValue) {
        VehicleAreaConfig area = new VehicleAreaConfig();
        area.areaId = areaId;
        area.access = mConfig.access;
        area.minInt32Value = minValue;
        area.maxInt32Value = maxValue;
        addAreaConfig(area);
        return this;
    }

    @CheckResult
    public AidlVehiclePropConfigBuilder addAreaConfig(int areaId, float minValue, float maxValue) {
        VehicleAreaConfig area = new VehicleAreaConfig();
        area.areaId = areaId;
        area.access = mConfig.access;
        area.minFloatValue = minValue;
        area.maxFloatValue = maxValue;
        addAreaConfig(area);
        return this;
    }

    @CheckResult
    public  AidlVehiclePropConfigBuilder addAreaConfig(int areaId, int access) {
        VehicleAreaConfig area = new VehicleAreaConfig();
        area.areaId = areaId;
        area.access = access;
        addAreaConfig(area);
        return this;
    }

    @CheckResult
    public AidlVehiclePropConfigBuilder addAreaConfig(int areaId, int access,
                                                      int minValue, int maxValue) {
        VehicleAreaConfig area = new VehicleAreaConfig();
        area.areaId = areaId;
        area.access = access;
        area.minInt32Value = minValue;
        area.maxInt32Value = maxValue;
        addAreaConfig(area);
        return this;
    }

    @CheckResult
    public AidlVehiclePropConfigBuilder addAreaConfig(int areaId, int access,
                                                      float minValue, float maxValue) {
        VehicleAreaConfig area = new VehicleAreaConfig();
        area.areaId = areaId;
        area.access = access;
        area.minFloatValue = minValue;
        area.maxFloatValue = maxValue;
        addAreaConfig(area);
        return this;
    }

    public VehiclePropConfig build() {
        return clone(mConfig);
    }

    private static VehicleAreaConfig[] duplicateAreaConfig(VehicleAreaConfig[] areaConfigs) {
        return duplicateAreaConfig(areaConfigs, areaConfigs.length);
    }

    private static VehicleAreaConfig[] duplicateAreaConfig(VehicleAreaConfig[] areaConfigs,
            int newSize) {
        VehicleAreaConfig[] out = new VehicleAreaConfig[newSize];
        int i = 0;
        for (VehicleAreaConfig area : areaConfigs) {
            VehicleAreaConfig newArea = new VehicleAreaConfig();
            newArea.areaId = area.areaId;
            newArea.access = area.access;
            newArea.minInt32Value = area.minInt32Value;
            newArea.maxInt32Value = area.maxInt32Value;
            newArea.minInt64Value = area.minInt64Value;
            newArea.maxInt64Value = area.maxInt64Value;
            newArea.minFloatValue = area.minFloatValue;
            newArea.maxFloatValue = area.maxFloatValue;
            out[i] = newArea;
            i++;
        }
        return out;
    }

    @CheckResult
    public static AidlVehiclePropConfigBuilder newBuilder(int propId) {
        return new AidlVehiclePropConfigBuilder(propId);
    }

    @CheckResult
    public static AidlVehiclePropConfigBuilder newBuilder(VehiclePropConfig config) {
        return new AidlVehiclePropConfigBuilder(config);
    }
}
