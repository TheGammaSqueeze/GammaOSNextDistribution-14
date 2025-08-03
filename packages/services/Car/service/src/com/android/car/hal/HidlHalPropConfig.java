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

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.hardware.automotive.vehicle.V2_0.VehicleAreaConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;

import com.android.car.CarServiceUtils;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;

import java.util.ArrayList;

/**
 * HidlHalPropConfig is a HalPropConfig with an HIDL backend.
 */
public final class HidlHalPropConfig extends HalPropConfig {
    private final VehiclePropConfig mConfig;

    public HidlHalPropConfig(VehiclePropConfig config) {
        mConfig = config;

        // Do some validity checks to make sure we do not return null for get functions.
        if (mConfig.areaConfigs == null) {
            mConfig.areaConfigs = new ArrayList<VehicleAreaConfig>();
        }
        if (mConfig.configString == null) {
            mConfig.configString = "";
        }
    }

    /**
     * Get the property ID.
     */
    @Override
    public int getPropId() {
        return mConfig.prop;
    }

    /**
     * Get the access mode.
     */
    @Override
    public int getAccess() {
        return mConfig.access;
    }

    /**
     * Get the change mode.
     */
    @Override
    public int getChangeMode() {
        return mConfig.changeMode;
    }

    /**
     * Get the area configs.
     */
    @Override
    public HalAreaConfig[] getAreaConfigs() {
        int size = mConfig.areaConfigs.size();
        HalAreaConfig[] areaConfigs = new HalAreaConfig[size];
        for (int i = 0; i < size; i++) {
            areaConfigs[i] = new HidlHalAreaConfig(mConfig.areaConfigs.get(i), mConfig.access);
        }
        return areaConfigs;
    }

    /**
     * Get the config array.
     */
    @Override
    public int[] getConfigArray() {
        return CarServiceUtils.toIntArray(mConfig.configArray);
    }

    /**
     * Get the config string.
     */
    @Override
    public String getConfigString() {
        return mConfig.configString;
    }

    /**
     * Get the min sample rate.
     */
    @Override
    public float getMinSampleRate() {
        return mConfig.minSampleRate;
    }

    /**
     * Get the max sample rate.
     */
    @Override
    public float getMaxSampleRate() {
        return mConfig.maxSampleRate;
    }

    /**
     * Converts to AIDL or HIDL VehiclePropConfig.
     */
    @Override
    public Object toVehiclePropConfig() {
        return mConfig;
    }

    /**
     * Get the string representation for debugging.
     */
    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public String toString() {
        return mConfig.toString();
    }
}
