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

/**
 * HalAreaConfig represents a vehicle area config.
 */
public abstract class HalAreaConfig {
    /**
     * Get the access mode.
     */
    public abstract int getAccess();

    /**
     * Get the area ID.
     */
    public abstract int getAreaId();

    /**
     * Get the min int value.
     */
    public abstract int getMinInt32Value();

    /**
     * Get the max int value.
     */
    public abstract int getMaxInt32Value();

    /**
     * Get the min long value.
     */
    public abstract long getMinInt64Value();

    /**
     * Get the max long value.
     */
    public abstract long getMaxInt64Value();

    /**
     * Get the min float value.
     */
    public abstract float getMinFloatValue();

    /**
     * Get the max float value.
     */
    public abstract float getMaxFloatValue();

    /**
     * Get list of supported enum values.
     */
    public abstract long[] getSupportedEnumValues();

    /**
     * Returns whether variable update rate is supported.
     */
    public boolean isVariableUpdateRateSupported() {
        return false;
    }
}
