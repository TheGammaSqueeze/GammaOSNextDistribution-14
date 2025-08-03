/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.car.internal.property;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.PRIVATE_CONSTRUCTOR;
import static com.android.car.internal.util.ArrayUtils.convertToIntArray;

import android.car.VehiclePropertyIds;
import android.car.feature.FeatureFlags;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.property.CarPropertyManager;
import android.util.Log;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.List;

/**
 * Common utility functions used by {@link CarPropertyManager} stack to sanitize input arguments.
 *
 * @hide
 */
public final class InputSanitizationUtils {

    private static final String TAG = InputSanitizationUtils.class.getSimpleName();
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    @ExcludeFromCodeCoverageGeneratedReport(reason = PRIVATE_CONSTRUCTOR)
    private InputSanitizationUtils() {
    }

    /**
     * Sanitizes the {@code updateRateHz} passed to {@link
     * CarPropertyManager#registerCallback(CarPropertyManager.CarPropertyEventCallback, int, float)}
     * and similar functions.
     */
    public static float sanitizeUpdateRateHz(
            CarPropertyConfig<?> carPropertyConfig, float updateRateHz) {
        float sanitizedUpdateRateHz = updateRateHz;
        if (carPropertyConfig.getChangeMode()
                != CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS) {
            sanitizedUpdateRateHz = CarPropertyManager.SENSOR_RATE_ONCHANGE;
        } else if (sanitizedUpdateRateHz > carPropertyConfig.getMaxSampleRate()) {
            sanitizedUpdateRateHz = carPropertyConfig.getMaxSampleRate();
        } else if (sanitizedUpdateRateHz < carPropertyConfig.getMinSampleRate()) {
            sanitizedUpdateRateHz = carPropertyConfig.getMinSampleRate();
        }
        return sanitizedUpdateRateHz;
    }

    /**
     * Sets resolution to 0 if the feature flag for resolution is not enabled. Also calls
     * {@link #requireIntegerPowerOf10Resolution(float)} to determine if the incoming resolution
     * value is an integer power of 10.
     */
    public static float sanitizeResolution(FeatureFlags featureFlags,
            CarPropertyConfig<?> carPropertyConfig, float resolution) {
        if (!featureFlags.subscriptionWithResolution() || carPropertyConfig.getChangeMode()
                != CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS) {
            return 0.0f;
        }
        requireIntegerPowerOf10Resolution(resolution);
        return resolution;
    }

    /**
     * Verifies that the incoming resolution value takes on only integer power of 10 values. Also
     * sets resolution to 0 if the feature flag for resolution is not enabled.
     */
    public static void requireIntegerPowerOf10Resolution(float resolution) {
        if (resolution == 0.0f) {
            return;
        }
        double log = Math.log10(resolution);
        Preconditions.checkArgument(Math.abs(log - (int) log) < 0.0000001f,
                "resolution must be an integer power of 10. Instead, got resolution: " + resolution
                        + ", whose log10 value is: " + log);
    }

    /**
     * Returns whether VUR feature is enabled and property is continuous.
     *
     * Need to be public even though InputSanitizationUtilsUnitTest is in the same package because
     * in CarServiceUnitTest, it is loaded using a different class loader.
     */
    @VisibleForTesting
    public static boolean isVurAllowed(FeatureFlags featureFlags,
            CarPropertyConfig<?> carPropertyConfig) {
        if (!featureFlags.variableUpdateRate()) {
            if (DBG) {
                Log.d(TAG, "VUR feature is not enabled, VUR is always off");
            }
            return false;
        }
        if (carPropertyConfig.getChangeMode()
                != CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS) {
            if (DBG) {
                Log.d(TAG, "VUR is always off for non-continuous property");
            }
            return false;
        }
        return true;
    }

    /**
     * Sanitizes the enableVur option in {@code CarSubscription}.
     *
     * <p>Overwrite it to false if:
     *
     * <ul>
     * <li>Vur feature is disabled.</li>
     * <li>Property is not continuous.</li>
     * <li>Vur is not supported for the specific area.</li>
     * </ul>
     *
     * @return a list of sanitized subscribe options. We may return more than one option because
     * part of the areaIds support Vur, the rest does not.
     */
    public static List<CarSubscription> sanitizeEnableVariableUpdateRate(
            FeatureFlags featureFlags, CarPropertyConfig<?> carPropertyConfig,
            CarSubscription inputOption) throws IllegalArgumentException {
        int[] areaIds = inputOption.areaIds;
        Preconditions.checkArgument(areaIds != null && areaIds.length != 0,
                "areaIds must not be empty for property: "
                + VehiclePropertyIds.toString(inputOption.propertyId));
        List<CarSubscription> sanitizedOptions = new ArrayList<>();
        if (!inputOption.enableVariableUpdateRate) {
            // We will only overwrite enableVur to off.
            sanitizedOptions.add(inputOption);
            return sanitizedOptions;
        }
        // If VUR feature is disabled, overwrite the VUR option to false.
        if (!isVurAllowed(featureFlags, carPropertyConfig)) {
            inputOption.enableVariableUpdateRate = false;
            sanitizedOptions.add(inputOption);
            return sanitizedOptions;
        }
        List<Integer> enabledAreaIds = new ArrayList<>();
        List<Integer> disabledAreaIds = new ArrayList<>();
        for (int areaId : areaIds) {
            try {
                if (carPropertyConfig.getAreaIdConfig(areaId)
                        .isVariableUpdateRateSupported()) {
                    enabledAreaIds.add(areaId);
                    continue;
                }
            } catch (IllegalArgumentException e) {
                // Do nothing.
            }
            if (DBG) {
                Log.d(TAG, "VUR is enabled but not supported for areaId: " + areaId);
            }
            disabledAreaIds.add(areaId);
        }
        CarSubscription disabledVurOption = new CarSubscription();
        disabledVurOption.propertyId = inputOption.propertyId;
        disabledVurOption.areaIds = convertToIntArray(disabledAreaIds);
        disabledVurOption.updateRateHz = inputOption.updateRateHz;
        disabledVurOption.enableVariableUpdateRate = false;
        disabledVurOption.resolution = inputOption.resolution;

        CarSubscription enabledVurOption = new CarSubscription();
        enabledVurOption.propertyId = inputOption.propertyId;
        enabledVurOption.areaIds = convertToIntArray(enabledAreaIds);
        enabledVurOption.updateRateHz = inputOption.updateRateHz;
        enabledVurOption.enableVariableUpdateRate = true;
        enabledVurOption.resolution = inputOption.resolution;

        sanitizedOptions.add(enabledVurOption);
        sanitizedOptions.add(disabledVurOption);
        return sanitizedOptions;
    }
}
