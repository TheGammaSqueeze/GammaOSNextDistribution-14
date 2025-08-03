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
 * Exception thrown when something unexpected happened in cars.
 */
public class CarInternalErrorException extends RuntimeException {
    private static final int VENDOR_ERROR_CODE_SUCCESS = 0;

    private int mVendorErrorCode;

    CarInternalErrorException(int propertyId, int areaId) {
        this(propertyId, areaId, VENDOR_ERROR_CODE_SUCCESS);
    }

    CarInternalErrorException(int propertyId, int areaId, int vendorErrorCode) {
        super("Property ID: " + VehiclePropertyIds.toString(propertyId) + " area ID: "
                + toHexString(areaId) + " - raised an internal error in cars with "
                + "vendor error code: " + vendorErrorCode);
        mVendorErrorCode = vendorErrorCode;
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
