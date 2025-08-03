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

package com.android.car.hal.property;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_DEFAULT;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_1;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_10;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_2;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_3;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_4;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_5;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_6;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_7;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_8;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_9;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_DOOR;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_ENGINE;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_HVAC;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_INFO;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_LIGHT;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_MIRROR;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_SEAT;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_WINDOW;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_NOT_ACCESSIBLE;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_1;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_10;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_2;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_3;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_4;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_5;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_6;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_7;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_8;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_9;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_DOOR;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_ENGINE;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_HVAC;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_INFO;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_LIGHT;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_MIRROR;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_SEAT;
import static android.hardware.automotive.vehicle.VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_WINDOW;

import android.annotation.Nullable;
import android.car.Car;
import android.car.hardware.property.VehicleVendorPermission;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.ArraySet;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.Objects;

/**
 * This utility class provides helper method to deal with property permission.
 */
public class PropertyPermissionInfo {
    // Create SinglePermission objects for each permission
    private static final SinglePermission PERMISSION_CONTROL_CAR_DOORS =
            new SinglePermission(Car.PERMISSION_CONTROL_CAR_DOORS);
    private static final SinglePermission PERMISSION_CONTROL_CAR_MIRRORS =
            new SinglePermission(Car.PERMISSION_CONTROL_CAR_MIRRORS);
    private static final SinglePermission PERMISSION_CONTROL_CAR_SEATS =
            new SinglePermission(Car.PERMISSION_CONTROL_CAR_SEATS);
    private static final SinglePermission PERMISSION_CONTROL_CAR_WINDOWS =
            new SinglePermission(Car.PERMISSION_CONTROL_CAR_WINDOWS);
    private static final SinglePermission PERMISSION_CONTROL_GLOVE_BOX =
            new SinglePermission(Car.PERMISSION_CONTROL_GLOVE_BOX);
    private static final SinglePermission PERMISSION_READ_INTERIOR_LIGHTS =
            new SinglePermission(Car.PERMISSION_READ_INTERIOR_LIGHTS);
    private static final SinglePermission PERMISSION_CONTROL_INTERIOR_LIGHTS =
            new SinglePermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS);
    private static final SinglePermission PERMISSION_EXTERIOR_LIGHTS =
            new SinglePermission(Car.PERMISSION_EXTERIOR_LIGHTS);
    private static final SinglePermission PERMISSION_CONTROL_EXTERIOR_LIGHTS =
            new SinglePermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS);
    private static final SinglePermission PERMISSION_CONTROL_CAR_AIRBAGS =
            new SinglePermission(Car.PERMISSION_CONTROL_CAR_AIRBAGS);
    private static final SinglePermission PERMISSION_READ_WINDSHIELD_WIPERS =
            new SinglePermission(Car.PERMISSION_READ_WINDSHIELD_WIPERS);
    private static final SinglePermission PERMISSION_CONTROL_WINDSHIELD_WIPERS =
            new SinglePermission(Car.PERMISSION_CONTROL_WINDSHIELD_WIPERS);
    private static final SinglePermission PERMISSION_READ_STEERING_STATE =
            new SinglePermission(Car.PERMISSION_READ_STEERING_STATE);
    private static final SinglePermission PERMISSION_CONTROL_STEERING_WHEEL =
            new SinglePermission(Car.PERMISSION_CONTROL_STEERING_WHEEL);
    private static final SinglePermission PERMISSION_CONTROL_CAR_CLIMATE =
            new SinglePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE);
    private static final SinglePermission PERMISSION_READ_DISPLAY_UNITS =
            new SinglePermission(Car.PERMISSION_READ_DISPLAY_UNITS);
    private static final SinglePermission PERMISSION_CONTROL_DISPLAY_UNITS =
            new SinglePermission(Car.PERMISSION_CONTROL_DISPLAY_UNITS);
    private static final SinglePermission PERMISSION_IDENTIFICATION =
            new SinglePermission(Car.PERMISSION_IDENTIFICATION);
    private static final SinglePermission PERMISSION_CAR_INFO =
            new SinglePermission(Car.PERMISSION_CAR_INFO);
    private static final SinglePermission PERMISSION_PRIVILEGED_CAR_INFO =
            new SinglePermission(Car.PERMISSION_PRIVILEGED_CAR_INFO);
    private static final SinglePermission PERMISSION_READ_ADAS_SETTINGS =
            new SinglePermission(Car.PERMISSION_READ_ADAS_SETTINGS);
    private static final SinglePermission PERMISSION_CONTROL_ADAS_SETTINGS =
            new SinglePermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS);
    private static final SinglePermission PERMISSION_READ_ADAS_STATES =
            new SinglePermission(Car.PERMISSION_READ_ADAS_STATES);
    private static final SinglePermission PERMISSION_CONTROL_ADAS_STATES =
            new SinglePermission(Car.PERMISSION_CONTROL_ADAS_STATES);
    private static final SinglePermission PERMISSION_READ_DRIVER_MONITORING_SETTINGS =
            new SinglePermission(Car.PERMISSION_READ_DRIVER_MONITORING_SETTINGS);
    private static final SinglePermission PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS =
            new SinglePermission(Car.PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS);
    private static final SinglePermission PERMISSION_READ_DRIVER_MONITORING_STATES =
            new SinglePermission(Car.PERMISSION_READ_DRIVER_MONITORING_STATES);
    private static final SinglePermission PERMISSION_CAR_ENGINE_DETAILED =
            new SinglePermission(Car.PERMISSION_CAR_ENGINE_DETAILED);
    private static final SinglePermission PERMISSION_MILEAGE =
            new SinglePermission(Car.PERMISSION_MILEAGE);
    private static final SinglePermission PERMISSION_SPEED =
            new SinglePermission(Car.PERMISSION_SPEED);
    private static final SinglePermission PERMISSION_ENERGY =
            new SinglePermission(Car.PERMISSION_ENERGY);
    private static final SinglePermission PERMISSION_CONTROL_CAR_ENERGY =
            new SinglePermission(Car.PERMISSION_CONTROL_CAR_ENERGY);
    private static final SinglePermission PERMISSION_ENERGY_PORTS =
            new SinglePermission(Car.PERMISSION_ENERGY_PORTS);
    private static final SinglePermission PERMISSION_CONTROL_ENERGY_PORTS =
            new SinglePermission(Car.PERMISSION_CONTROL_ENERGY_PORTS);
    private static final SinglePermission PERMISSION_ADJUST_RANGE_REMAINING =
            new SinglePermission(Car.PERMISSION_ADJUST_RANGE_REMAINING);
    private static final SinglePermission PERMISSION_TIRES =
            new SinglePermission(Car.PERMISSION_TIRES);
    private static final SinglePermission PERMISSION_POWERTRAIN =
            new SinglePermission(Car.PERMISSION_POWERTRAIN);
    private static final SinglePermission PERMISSION_CONTROL_POWERTRAIN =
            new SinglePermission(Car.PERMISSION_CONTROL_POWERTRAIN);
    private static final SinglePermission PERMISSION_EXTERIOR_ENVIRONMENT =
            new SinglePermission(Car.PERMISSION_EXTERIOR_ENVIRONMENT);
    private static final SinglePermission PERMISSION_CAR_DYNAMICS_STATE =
            new SinglePermission(Car.PERMISSION_CAR_DYNAMICS_STATE);
    private static final SinglePermission PERMISSION_CAR_EPOCH_TIME =
            new SinglePermission(Car.PERMISSION_CAR_EPOCH_TIME);
    private static final SinglePermission PERMISSION_ACCESS_FINE_LOCATION =
            new SinglePermission(ACCESS_FINE_LOCATION);
    private static final SinglePermission PERMISSION_VENDOR_EXTENSION =
            new SinglePermission(Car.PERMISSION_VENDOR_EXTENSION);
    private static final AnyOfPermissions PERMISSION_ENERGY_OR_CONTROL_CAR_ENERGY =
            new AnyOfPermissions(PERMISSION_ENERGY, PERMISSION_CONTROL_CAR_ENERGY);

    /**
     * Class to hold {@code readPermission} and {@code writePermission} in a single object.
     */
    public static final class PropertyPermissions {
        @Nullable
        private final PermissionCondition mReadPermission;
        @Nullable
        private final PermissionCondition mWritePermission;

        private PropertyPermissions(@Nullable PermissionCondition readPermission,
                @Nullable PermissionCondition writePermission) {
            mReadPermission = readPermission;
            mWritePermission = writePermission;
        }

        @Nullable
        public PermissionCondition getReadPermission() {
            return mReadPermission;
        }

        @Nullable
        public PermissionCondition getWritePermission() {
            return mWritePermission;
        }

        /**
         * The builder for {@link PropertyPermissions}.
         */
        public static final class Builder {
            @Nullable
            private PermissionCondition mReadPermission;
            @Nullable
            private PermissionCondition mWritePermission;

            /**
             * Sets the read permission.
             */
            public Builder setReadPermission(PermissionCondition readPermission) {
                mReadPermission = readPermission;
                return this;
            }

            /**
             * Sets the write permission.
             */
            public Builder setWritePermission(PermissionCondition writePermission) {
                mWritePermission = writePermission;
                return this;
            }

            /**
             * Builds the permission.
             */
            public PropertyPermissions build() {
                if (mReadPermission == null && mWritePermission == null) {
                    throw new IllegalStateException("Both read and write permissions have not been "
                        + "set");
                }
                return new PropertyPermissions(mReadPermission, mWritePermission);
            }
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            // instanceof will return false if object is null.
            if (!(object instanceof PropertyPermissions)) {
                return false;
            }
            PropertyPermissions other = (PropertyPermissions) object;
            return Objects.equals(mReadPermission, other.getReadPermission())
                    && Objects.equals(mWritePermission, other.getWritePermission());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mReadPermission) + Objects.hashCode(mWritePermission);
        }

        @Override
        public String toString() {
            return new StringBuilder().append("{")
                    .append("readPermission: ").append(mReadPermission)
                    .append("writePermission: ").append(mWritePermission)
                    .append("}").toString();
        }
    }

    /**
     * An interface for representing the read and write permissions required for each property.
     * <p>
     * <p>If a property requires only a singular permission for read or write, that permission
     * should be instantiated in a {@link SinglePermission} class. If the property requires multiple
     * permissions, the {@link AllOfPermissions} class should be used. If the property requires one
     * out of any group of permissions, the {@link AnyOfPermissions} class should be used. If a
     * combination of these is required for read or write, a combination of AllOfPermissions and
     * AnyOfPermissions should be used as described in their javadocs.
     */
    public interface PermissionCondition {

        /**
         * Determines whether the condition defined in the class has been met or not, within a given
         * context.
         *
         * @param context Context to check
         * @return whether required permission are granted.
         */
        boolean isMet(Context context);
    }

    /**
     * Implementation to store {@code allOf()} permission sets.
     * <p>
     * <p>This implementation of {@link PermissionCondition} stores the permissions that a property
     * would require all of in order to be granted. AllOfPermissions stores the permissions as a
     * {@code ArraySet<PermissionCondition>}, so singular permissions in AllOfPermissions will be
     * stored as {@link SinglePermission} objects in the list, and a set of anyOf permissions will
     * be stored as {@link AnyOfPermissions} objects.
     */
    public static final class AllOfPermissions implements PermissionCondition {
        private final ArraySet<PermissionCondition> mPermissionsList;

        public AllOfPermissions(PermissionCondition... permissions) {
            if (permissions.length <= 1) {
                throw new IllegalArgumentException("Input parameter should contain at least 2 "
                        + "PermissionCondition objects");
            }
            mPermissionsList = new ArraySet<>();
            Collections.addAll(mPermissionsList, permissions);
        }

        /**
         * Checks whether every {@link PermissionCondition} in this object has been granted by the
         * given context or not.
         *
         * @param context Context to check
         * @return whether all permissions in the AllOfPermissions object are met.
         */
        public boolean isMet(Context context) {
            for (int i = 0; i < mPermissionsList.size(); i++) {
                if (!mPermissionsList.valueAt(i).isMet(context)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            StringBuilder stringBuffer = new StringBuilder().append('(');
            for (int i = 0; i < mPermissionsList.size() - 1; i++) {
                stringBuffer.append(mPermissionsList.valueAt(i).toString());
                stringBuffer.append(" && ");
            }
            stringBuffer.append(mPermissionsList.valueAt(mPermissionsList.size() - 1)).append(')');
            return stringBuffer.toString();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            // instanceof will return false if object is null.
            if (!(object instanceof AllOfPermissions)) {
                return false;
            }
            return mPermissionsList.equals(((AllOfPermissions) object).mPermissionsList);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mPermissionsList) + "all".hashCode();
        }
    }

    /**
     * Implementation to store {@code anyOf()} permission sets.
     * <p>
     * <p>This implementation of {@link PermissionCondition} stores the permissions that a property
     * would require any of in order to be granted. AnyOfPermissions stores the permissions as a
     * {@code ArraySet<PermissionCondition>}, so singular permissions in AnyOfPermissions will be
     * stored as {@link SinglePermission} objects in the list, and a set of allOf permissions will
     * be stored as {@link AllOfPermissions} objects.
     */
    public static final class AnyOfPermissions implements PermissionCondition {
        private final ArraySet<PermissionCondition> mPermissionsList;

        public AnyOfPermissions(PermissionCondition... permissions) {
            if (permissions.length <= 1) {
                throw new IllegalArgumentException("Input parameter should contain at least 2 "
                        + "PermissionCondition objects");
            }
            mPermissionsList = new ArraySet<>();
            Collections.addAll(mPermissionsList, permissions);
        }

        /**
         * Checks whether any {@link PermissionCondition} in this object has been granted by the
         * given context or not.
         *
         * @param context Context to check
         * @return whether any permission in the AnyOfPermissions object has been met.
         */
        public boolean isMet(Context context) {
            for (int i = 0; i < mPermissionsList.size(); i++) {
                if (mPermissionsList.valueAt(i).isMet(context)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            StringBuilder stringBuffer = new StringBuilder().append('(');
            for (int i = 0; i < mPermissionsList.size() - 1; i++) {
                stringBuffer.append(mPermissionsList.valueAt(i).toString());
                stringBuffer.append(" || ");
            }
            stringBuffer.append(mPermissionsList.valueAt(mPermissionsList.size() - 1)).append(')');
            return stringBuffer.toString();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            // instanceof will return false if object is null.
            if (!(object instanceof AnyOfPermissions)) {
                return false;
            }
            return mPermissionsList.equals(((AnyOfPermissions) object).mPermissionsList);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mPermissionsList) + "any".hashCode();
        }

        /**
         * Checks whether current {@link PermissionCondition} instance will be met if given {@link
         * SinglePermission} instance is known to be granted.
         *
         * <p>To be used for testing only
         *
         * @param grantedPermission {@link SinglePermission} that is known to be granted.
         * @return whether current AnyOfPermissions object is met.
         */
        @VisibleForTesting
        public boolean isMetIfGranted(SinglePermission grantedPermission) {
            for (int i = 0; i < mPermissionsList.size(); i++) {
                if (mPermissionsList.valueAt(i).equals(grantedPermission)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Implementation to store a singular permission string.
     * <p>
     * <p>This implementation of {@link PermissionCondition} holds a singular permission. This class
     * is used to hold individual permissions on their own in the property-permissions map or within
     * some other implementation of PermissionCondition.
     */
    public static final class SinglePermission implements PermissionCondition {
        private final String mPermission;

        public SinglePermission(String permission) {
            mPermission = permission;
        }

        /**
         * Checks if the permission is granted in a given context.
         *
         * @param context Context to check
         * @return whether permission has been granted.
         */
        public boolean isMet(Context context) {
            return context.checkCallingOrSelfPermission(mPermission)
                    == PackageManager.PERMISSION_GRANTED;
        }

        @Override
        public String toString() {
            return mPermission;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            // instanceof will return false if object is null.
            if (!(object instanceof SinglePermission)) {
                return false;
            }
            return mPermission.equals(((SinglePermission) object).mPermission);
        }

        @Override
        public int hashCode() {
            return mPermission.hashCode() + "single".hashCode();
        }
    }

    /**
     * Maps VehicleVendorPermission enums in VHAL to android permissions.
     *
     * @return permission string, return null if vendor property is not available.
     */
    @Nullable
    public static String toPermissionString(int permissionEnum, int propId) {
        switch (permissionEnum) {
            case PERMISSION_DEFAULT:
                return Car.PERMISSION_VENDOR_EXTENSION;
            case PERMISSION_SET_VENDOR_CATEGORY_WINDOW:
                return VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_WINDOW;
            case PERMISSION_GET_VENDOR_CATEGORY_WINDOW:
                return VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_WINDOW;
            case PERMISSION_SET_VENDOR_CATEGORY_DOOR:
                return VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_DOOR;
            case PERMISSION_GET_VENDOR_CATEGORY_DOOR:
                return VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_DOOR;
            case PERMISSION_SET_VENDOR_CATEGORY_SEAT:
                return VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_SEAT;
            case PERMISSION_GET_VENDOR_CATEGORY_SEAT:
                return VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_SEAT;
            case PERMISSION_SET_VENDOR_CATEGORY_MIRROR:
                return VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_MIRROR;
            case PERMISSION_GET_VENDOR_CATEGORY_MIRROR:
                return VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_MIRROR;
            case PERMISSION_SET_VENDOR_CATEGORY_INFO:
                return VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_INFO;
            case PERMISSION_GET_VENDOR_CATEGORY_INFO:
                return VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_INFO;
            case PERMISSION_SET_VENDOR_CATEGORY_ENGINE:
                return VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_ENGINE;
            case PERMISSION_GET_VENDOR_CATEGORY_ENGINE:
                return VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_ENGINE;
            case PERMISSION_SET_VENDOR_CATEGORY_HVAC:
                return VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_HVAC;
            case PERMISSION_GET_VENDOR_CATEGORY_HVAC:
                return VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_HVAC;
            case PERMISSION_SET_VENDOR_CATEGORY_LIGHT:
                return VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_LIGHT;
            case PERMISSION_GET_VENDOR_CATEGORY_LIGHT:
                return VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_LIGHT;
            case PERMISSION_SET_VENDOR_CATEGORY_1:
                return VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_1;
            case PERMISSION_GET_VENDOR_CATEGORY_1:
                return VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_1;
            case PERMISSION_SET_VENDOR_CATEGORY_2:
                return VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_2;
            case PERMISSION_GET_VENDOR_CATEGORY_2:
                return VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_2;
            case PERMISSION_SET_VENDOR_CATEGORY_3:
                return VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_3;
            case PERMISSION_GET_VENDOR_CATEGORY_3:
                return VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_3;
            case PERMISSION_SET_VENDOR_CATEGORY_4:
                return VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_4;
            case PERMISSION_GET_VENDOR_CATEGORY_4:
                return VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_4;
            case PERMISSION_SET_VENDOR_CATEGORY_5:
                return VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_5;
            case PERMISSION_GET_VENDOR_CATEGORY_5:
                return VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_5;
            case PERMISSION_SET_VENDOR_CATEGORY_6:
                return VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_6;
            case PERMISSION_GET_VENDOR_CATEGORY_6:
                return VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_6;
            case PERMISSION_SET_VENDOR_CATEGORY_7:
                return VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_7;
            case PERMISSION_GET_VENDOR_CATEGORY_7:
                return VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_7;
            case PERMISSION_SET_VENDOR_CATEGORY_8:
                return VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_8;
            case PERMISSION_GET_VENDOR_CATEGORY_8:
                return VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_8;
            case PERMISSION_SET_VENDOR_CATEGORY_9:
                return VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_9;
            case PERMISSION_GET_VENDOR_CATEGORY_9:
                return VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_9;
            case PERMISSION_SET_VENDOR_CATEGORY_10:
                return VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_10;
            case PERMISSION_GET_VENDOR_CATEGORY_10:
                return VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_10;
            case PERMISSION_NOT_ACCESSIBLE:
                return null;
            default:
                throw new IllegalArgumentException("permission Id: " + permissionEnum
                        + " for property:" + propId + " is invalid vendor permission Id");
        }
    }

    private PropertyPermissionInfo() {
        throw new IllegalStateException("Only allowed to be used as static");
    }
}
