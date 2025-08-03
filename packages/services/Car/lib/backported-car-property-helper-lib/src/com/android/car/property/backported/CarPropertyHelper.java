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

package com.android.car.property.backported;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.car.VehiclePropertyIds.LOCATION_CHARACTERIZATION;
import static android.car.hardware.property.VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_INFO;

import android.car.VehiclePropertyIds;
import android.car.hardware.property.CarPropertyManager;
import android.util.SparseBooleanArray;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;

import java.util.Map;

/**
 * A helper class for mapping a property name to the supported property ID/required permissions.
 */
public final class CarPropertyHelper {

    private final Object mLock = new Object();
    private final CarPropertyManager mCarPropertyManager;

   // These are the same values as defined in VHAL interface.
    private static final int VEHICLE_PROPERTY_GROUP_MASK = 0xf0000000;
    private static final int VEHICLE_PROPERTY_GROUP_BACKPORTED = 0x30000000;

    private static final Map<String, PropertyInfo> PROPERTY_INFO_BY_NAME = Map.ofEntries(
            Map.entry(VehiclePropertyIds.toString(LOCATION_CHARACTERIZATION),
                    new PropertyInfo(LOCATION_CHARACTERIZATION)
                            .setSystemReadPermission(ACCESS_FINE_LOCATION)
                            .setVendorReadPermission(PERMISSION_GET_CAR_VENDOR_CATEGORY_INFO))
    );


    // A cache for storing whether each propertyId is supported.
    @GuardedBy("mLock")
    private final SparseBooleanArray mPropertySupportedById = new SparseBooleanArray();

    private static final class PropertyInfo {
        public int systemPropertyId;
        public String systemReadPermission;
        public String systemWritePermission;
        public String vendorReadPermission;
        public String vendorWritePermission;

        PropertyInfo(int systemPropertyId) {
            this.systemPropertyId = systemPropertyId;
        }

        public PropertyInfo setSystemReadPermission(String permission) {
            systemReadPermission = permission;
            return this;
        }

        public PropertyInfo setSystemWritePermission(String permission) {
            systemWritePermission = permission;
            return this;
        }

        public PropertyInfo setVendorReadPermission(String permission) {
            vendorReadPermission = permission;
            return this;
        }

        public PropertyInfo setVendorWritePermission(String permission) {
            vendorWritePermission = permission;
            return this;
        }
    }

    public CarPropertyHelper(CarPropertyManager carPropertyManager) {
        mCarPropertyManager = carPropertyManager;
    }

    private static int getVendorPropertyId(int systemPropertyId) {
        return (systemPropertyId | VEHICLE_PROPERTY_GROUP_MASK) & VEHICLE_PROPERTY_GROUP_BACKPORTED;
    }

    private boolean isPropertySupported(int propertyId) {
        synchronized (mLock) {
            // If we already know from cache, use it.
            int index = mPropertySupportedById.indexOfKey(propertyId);
            if (index >= 0) {
                return mPropertySupportedById.valueAt(index);
            }

            boolean isSupported = (mCarPropertyManager.getCarPropertyConfig(propertyId) != null);

            // Store the result into cache.
            mPropertySupportedById.put(propertyId, isSupported);
            return isSupported;
        }
    }

    /**
     * Maps a property name to a supported property ID.
     *
     * <p>If the system property ID is supported, returns the system property ID.
     *
     * <p>If the system property ID is not supported but the backported property ID is supported,
     * returns the backported property ID.
     *
     * <p>If both are not supported, returns {@code null}.
     *
     * @param propertyName The name for the property.
     * @return The supported property ID or {@code null}.
     */
    @Nullable
    public Integer getPropertyId(String propertyName) {
        PropertyInfo propertyInfo = PROPERTY_INFO_BY_NAME.get(propertyName);
        if (propertyInfo == null) {
            return null;
        }
        int systemPropertyId = propertyInfo.systemPropertyId;
        if (isPropertySupported(systemPropertyId)) {
            return systemPropertyId;
        }
        int vendorPropertyId = getVendorPropertyId(systemPropertyId);
        if (isPropertySupported(vendorPropertyId)) {
            return vendorPropertyId;
        }
        return null;
    }

    /**
     * Gets the required permission for reading the property.
     *
     * <p>If the system property ID is supported, returns the read permission for it.
     *
     * <p>If the system property ID is not supported but the backported property ID is supported,
     * returns the vendor read permission for it.
     *
     * <p>If both are not supported or the property is not readable, returns {@code null}.
     *
     * @param propertyName The name for the property.
     * @return The required permission for reading the property or {@code null}.
     */
    @Nullable
    public String getReadPermission(String propertyName) {
        PropertyInfo propertyInfo = PROPERTY_INFO_BY_NAME.get(propertyName);
        if (propertyInfo == null) {
            return null;
        }
        int systemPropertyId = propertyInfo.systemPropertyId;
        if (isPropertySupported(systemPropertyId)) {
            return propertyInfo.systemReadPermission;
        }
        int vendorPropertyId = getVendorPropertyId(systemPropertyId);
        if (isPropertySupported(vendorPropertyId)) {
            return propertyInfo.vendorReadPermission;
        }
        return null;
    }

    /**
     * Gets the required permission for writing the property.
     *
     * <p>If the system property ID is supported, returns the write permission for it.
     *
     * <p>If the system property ID is not supported but the backported property ID is supported,
     * returns the vendor write permission for it.
     *
     * <p>If both are not supported or the property is not writable, returns {@code null}.
     *
     * @param propertyName The name for the property.
     * @return The required permission for writing the property or {@code null}.
     */
    @Nullable
    public String getWritePermission(String propertyName) {
        PropertyInfo propertyInfo = PROPERTY_INFO_BY_NAME.get(propertyName);
        if (propertyInfo == null) {
            return null;
        }
        int systemPropertyId = propertyInfo.systemPropertyId;
        if (isPropertySupported(systemPropertyId)) {
            return propertyInfo.systemWritePermission;
        }
        int vendorPropertyId = getVendorPropertyId(systemPropertyId);
        if (isPropertySupported(vendorPropertyId)) {
            return propertyInfo.vendorWritePermission;
        }
        return null;
    }
}
