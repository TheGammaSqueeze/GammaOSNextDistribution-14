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

package com.android.systemui.car.hvac;

import android.car.hardware.CarPropertyConfig;
import android.car.hardware.property.AreaIdConfig;

/**
 *  Utility class for HVAC-related use cases.
 */
public final class HvacUtils {
    /**
     * @see #shouldAllowControl(boolean, boolean, boolean, boolean)
     */
    public static boolean shouldAllowControl(boolean disableViewIfPowerOff, boolean powerOn) {
        return shouldAllowControl(disableViewIfPowerOff, powerOn, /* disableViewIfAutoOn= */false,
                /* autoOn= */false);
    }

    /**
     * @see #shouldAllowControl(boolean, boolean, boolean, boolean)
     */
    public static boolean shouldAllowControl(boolean disableViewIfPowerOff, boolean powerOn,
            boolean autoOn) {
        return shouldAllowControl(disableViewIfPowerOff, powerOn, /* disableViewIfAutoOn= */true,
                autoOn);
    }

    /**
     * Returns whether the view can be controlled.
     *
     * @param disableViewIfPowerOff whether the view can be controlled when hvac power is off
     * @param powerOn is hvac power on
     * @param disableViewIfAutoOn whether the view can be controlled when hvac auto mode is on
     * @param autoOn is auto mode on
     * @return is the view controllable
     */
    public static boolean shouldAllowControl(boolean disableViewIfPowerOff, boolean powerOn,
            boolean disableViewIfAutoOn, boolean autoOn) {
        return (!disableViewIfPowerOff || powerOn) && (!disableViewIfAutoOn || !autoOn);
    }

    /**
     * For an {@code Integer} property, return the highest minimum value specified for all area IDs.
     * If there are no minimum values provided by all of the area IDs or if the property is not an
     * {@code Integer} property, return {@code null}.
     *
     * @param carPropertyConfig {@code Integer} CarPropertyConfig
     * @return highest min value or {@code null}
     */
    public static Integer getHighestMinValueForAllAreaIds(CarPropertyConfig<?> carPropertyConfig) {
        if (!carPropertyConfig.getPropertyType().equals(Integer.class)) {
            return null;
        }
        Integer highestMinValue = null;
        for (AreaIdConfig<?> areaIdConfig: carPropertyConfig.getAreaIdConfigs()) {
            if (highestMinValue == null || (areaIdConfig.getMinValue() != null
                    && (Integer) areaIdConfig.getMinValue() > highestMinValue)) {
                highestMinValue = (Integer) areaIdConfig.getMinValue();
            }
        }
        return highestMinValue;
    }

    /**
     * For an {@code Integer} property, return the lowest maximum value specified for all area IDs.
     * If there are no maximum values provided by all of the area IDs or if the property is not an
     * {@code Integer} property, return {@code null}.
     *
     * @param carPropertyConfig {@code Integer} CarPropertyConfig
     * @return lowest max value or {@code null}
     */
    public static Integer getLowestMaxValueForAllAreaIds(CarPropertyConfig<?> carPropertyConfig) {
        if (!carPropertyConfig.getPropertyType().equals(Integer.class)) {
            return null;
        }
        Integer lowestMaxValue = null;
        for (AreaIdConfig<?> areaIdConfig: carPropertyConfig.getAreaIdConfigs()) {
            if (lowestMaxValue == null || (areaIdConfig.getMaxValue() != null
                    && (Integer) areaIdConfig.getMaxValue() < lowestMaxValue)) {
                lowestMaxValue = (Integer) areaIdConfig.getMaxValue();
            }
        }
        return lowestMaxValue;
    }
}
