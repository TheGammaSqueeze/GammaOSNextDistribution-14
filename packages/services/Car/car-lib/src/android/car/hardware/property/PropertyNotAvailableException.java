/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.car.hardware.property;


import static java.lang.Integer.toHexString;

import android.annotation.SystemApi;
import android.car.VehiclePropertyIds;

/**
 * Exception thrown when the vehicle property is not available because of the current state of the
 * vehicle.
 *
 * <p>For example, {@link android.car.VehiclePropertyIds#HVAC_FAN_SPEED} is unavailable because
 * {@link android.car.VehiclePropertyIds#HVAC_POWER_ON} is {@code false}.
 */
public class PropertyNotAvailableException extends IllegalStateException {
    private int mDetailedErrorCode = PropertyNotAvailableErrorCode.NOT_AVAILABLE;
    private int mVendorErrorCode;

    PropertyNotAvailableException(int propertyId, int areaId, int vendorErrorCode) {
        super("Property ID: " + VehiclePropertyIds.toString(propertyId) + " area ID: 0x"
                + toHexString(areaId)
                + " - is not available because of vendor error code: " + vendorErrorCode);
        mVendorErrorCode = vendorErrorCode;
    }

    PropertyNotAvailableException(int propertyId, int areaId, int detailedErrorCode,
            int vendorErrorCode) {
        super("Property ID: " + VehiclePropertyIds.toString(propertyId) + " area ID: 0x"
                + toHexString(areaId)
                + " - is not available because of status code: "
                + PropertyNotAvailableErrorCode.toString(detailedErrorCode));
        mDetailedErrorCode = detailedErrorCode;
        mVendorErrorCode = vendorErrorCode;
    }

    /**
     * {@link PropertyNotAvailableErrorCode} provides more detailed information on why the vehicle
     * property is not available. These must be a value defined in
     * {@link PropertyNotAvailableErrorCode}. The values in {@link PropertyNotAvailableErrorCode}
     * may be extended in the future to include additional error codes.
     */
    public int getDetailedErrorCode() {
        return mDetailedErrorCode;
    }

    /**
     * Gets the vendor error codes to allow for more detailed error codes.
     *
     * @return Vendor error code if it is set, otherwise 0. A vendor error code will have a range
     * from 0x0000 to 0xffff.
     *
     * @hide
     */
    @SystemApi
    public int getVendorErrorCode() {
        return mVendorErrorCode;
    }
}
