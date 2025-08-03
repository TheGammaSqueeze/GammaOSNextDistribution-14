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

package com.android.car.hal.fakevhal;

import android.annotation.Nullable;
import android.hardware.automotive.vehicle.RawPropValues;
import android.hardware.automotive.vehicle.VehiclePropConfig;
import android.util.SparseArray;

import java.util.Objects;

/**
 * ConfigDeclaration class contains both configs and initial values of a property.
 */
public final class ConfigDeclaration {

    private final VehiclePropConfig mConfig;
    private final RawPropValues mInitialValue;
    private final SparseArray<RawPropValues> mInitialAreaValuesByAreaId;

    public ConfigDeclaration(VehiclePropConfig config, @Nullable RawPropValues initialValue,
            SparseArray<RawPropValues> initialAreaValuesByAreaId) {
        this.mConfig = Objects.requireNonNull(config, "config cannot be null.");
        this.mInitialValue = initialValue;
        this.mInitialAreaValuesByAreaId = Objects.requireNonNull(initialAreaValuesByAreaId,
            "initialAreaValueByAreaId cannot be null.");
    }

    @Override
    public String toString() {
        return new StringBuilder("ConfigDeclaration{ mConfig = ").append(mConfig)
            .append(", mInitialValue = ").append(mInitialValue)
            .append(", mInitialAreaValuesByAreaId = ").append(mInitialAreaValuesByAreaId)
            .append(" }").toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ConfigDeclaration)) {
            return false;
        }
        ConfigDeclaration other = (ConfigDeclaration) obj;

        return mConfig.equals(other.getConfig())
                && Objects.equals(mInitialValue, other.getInitialValue())
                && mInitialAreaValuesByAreaId.contentEquals(other.getInitialAreaValuesByAreaId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(mConfig, mInitialValue, mInitialAreaValuesByAreaId.contentHashCode());
    }

    /**
     * Gets the property config.
     */
    public VehiclePropConfig getConfig() {
        return mConfig;
    }

    /**
     * Gets the initial value for the property.
     */
    public RawPropValues getInitialValue() {
        return mInitialValue;
    }

    /**
     * Gets the area initial values for the property. Key is areaId and value is the initial value.
     *
     * @return a Map with mappings between areaId and initial values.
     */
    public SparseArray<RawPropValues> getInitialAreaValuesByAreaId() {
        return mInitialAreaValuesByAreaId;
    }
}
