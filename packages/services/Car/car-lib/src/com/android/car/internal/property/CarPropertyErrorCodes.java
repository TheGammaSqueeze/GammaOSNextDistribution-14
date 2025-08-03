/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.VehicleHalStatusCode;
import android.car.hardware.property.VehicleHalStatusCode.VehicleHalStatusCodeInt;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.car.internal.util.AnnotationValidations;
import com.android.car.internal.util.DataClass;

import java.util.StringJoiner;

/**
 * Stores the various error codes for vehicle properties as they get passed up
 * the stack.
 *
 * @hide
 */
@DataClass(genConstructor = false)
public final class CarPropertyErrorCodes implements Parcelable {

    /**
     * Status indicating no error.
     *
     * <p>This is not exposed to the client as this will be used only for deciding
     * {@link GetPropertyCallback#onSuccess} or {@link GetPropertyCallback#onFailure} is called.
     */
    public static final int STATUS_OK = 0;
    public static final int STATUS_TRY_AGAIN = -1;

    private static final int SYSTEM_ERROR_CODE_MASK = 0xffff;
    private static final int VENDOR_ERROR_CODE_SHIFT = 16;

    /**
     * CarPropertyErrorCodes with no errors.
     */
    public static CarPropertyErrorCodes STATUS_OK_NO_ERROR =
            new CarPropertyErrorCodes(STATUS_OK, /* vendorErrorCode= */ 0, /* systemErrorCode */ 0);

    /**
     * This is same as {@link CarPropertyAsyncErrorCode} except that it contains
     * {@code STATUS_TRY_AGAIN}.
     */
    @IntDef(prefix = {"STATUS_"}, value = {
            STATUS_OK,
            CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR,
            CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE,
            CarPropertyManager.STATUS_ERROR_TIMEOUT,
            STATUS_TRY_AGAIN
    })
    public @interface CarPropMgrErrorCode {}

    private @CarPropMgrErrorCode int mCarPropertyManagerErrorCode;
    private int mVendorErrorCode;
    private int mSystemErrorCode;

    /**
     * Create an instance of CarPropertyErrorCodes given a car property manager error code, a vendor
     * error code, and a system error code.
     */
    public CarPropertyErrorCodes(@CarPropMgrErrorCode int carPropertyManagerErrorCode,
            int vendorErrorCode, int systemErrorCode) {
        mCarPropertyManagerErrorCode = carPropertyManagerErrorCode;
        mVendorErrorCode = vendorErrorCode;
        mSystemErrorCode = systemErrorCode;
    }

    /**
     * Get the errors returned by CarPropertyManager as defined by @link CarPropMgrErrorCode}.
     */
    public @CarPropMgrErrorCode int getCarPropertyManagerErrorCode() {
        return mCarPropertyManagerErrorCode;
    }

    /**
     * Get the vendor specified error code to allow for more detailed error codes.
     *
     * A vendor error code will have a range from 0x0000 to 0xffff.
     *
     * @return the vendor error code if it is set, otherwise 0.
     */
    public int getVendorErrorCode() {
        return mVendorErrorCode;
    }

    /**
     * Get the system error code.
     *
     * A system error code will have a range from 0x0000 to 0xffff.
     */
    public int getSystemErrorCode() {
        return mSystemErrorCode;
    }

    /**
     * Converts a StatusCode from VHAL to error code used by CarPropertyManager.
     */
    public static CarPropertyErrorCodes convertVhalStatusCodeToCarPropertyManagerErrorCodes(
            int errorCode) {
        @VehicleHalStatusCodeInt int systemErrorCode = getVhalSystemErrorCode(errorCode);
        @CarPropMgrErrorCode int carPropertyManagerErrorCode = STATUS_OK;

        switch (systemErrorCode) {
            case VehicleHalStatusCode.STATUS_OK:
                break;
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE: // Fallthrough
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_DISABLED: // Fallthrough
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_LOW: // Fallthrough
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_HIGH: // Fallthrough
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_POOR_VISIBILITY: // Fallthrough
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SAFETY:
                carPropertyManagerErrorCode = CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE;
                break;
            case VehicleHalStatusCode.STATUS_TRY_AGAIN:
                carPropertyManagerErrorCode = STATUS_TRY_AGAIN;
                break;
            default:
                carPropertyManagerErrorCode = CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR;
                break;
        }

        CarPropertyErrorCodes errorCodes = new CarPropertyErrorCodes(
                carPropertyManagerErrorCode, getVhalVendorErrorCode(errorCode), systemErrorCode);

        return errorCodes;
    }

    /**
     * Returns the system error code contained in the error code returned from VHAL.
     */
    @SuppressLint("WrongConstant")
    public static @VehicleHalStatusCodeInt int getVhalSystemErrorCode(int vhalErrorCode) {
        return vhalErrorCode & SYSTEM_ERROR_CODE_MASK;
    }

    /**
     * Returns the vendor error code contained in the error code returned from VHAL.
     */
    public static int getVhalVendorErrorCode(int vhalErrorCode) {
        return vhalErrorCode >>> VENDOR_ERROR_CODE_SHIFT;
    }

    /**
     * Returns {@code true} if {@code vehicleHalStatusCode} is one of the not available
     * {@link VehicleHalStatusCode} values}. Otherwise returns {@code false}.
     */
    public static boolean isNotAvailableVehicleHalStatusCode(
            @VehicleHalStatusCodeInt int vehicleHalStatusCode) {
        switch (vehicleHalStatusCode) {
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE:
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_DISABLED:
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_LOW:
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SPEED_HIGH:
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_POOR_VISIBILITY:
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE_SAFETY:
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns a string representation of a {@code CarPropertyErrorCodes}.
     */
    @NonNull
    public static String toString(CarPropertyErrorCodes carPropertyErrorCodes) {
        var sj = new StringJoiner(", ", "CarPropertyErrorCodes{", "}");
        sj.add("cpmErrorCode: " + carPropertyErrorCodes.getCarPropertyManagerErrorCode());
        sj.add("vendorErrorCode: " + carPropertyErrorCodes.getVendorErrorCode());
        sj.add("systemErrorCode: " + carPropertyErrorCodes.getSystemErrorCode());
        return sj.toString();
    }



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/com/android/car/internal/property/CarPropertyErrorCodes.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @IntDef(prefix = "STATUS_", value = {
        STATUS_OK,
        STATUS_TRY_AGAIN
    })
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE)
    @DataClass.Generated.Member
    public @interface Status {}

    @DataClass.Generated.Member
    public static String statusToString(@Status int value) {
        switch (value) {
            case STATUS_OK:
                    return "STATUS_OK";
            case STATUS_TRY_AGAIN:
                    return "STATUS_TRY_AGAIN";
            default: return Integer.toHexString(value);
        }
    }

    @DataClass.Generated.Member
    public @NonNull CarPropertyErrorCodes setCarPropertyManagerErrorCode(@CarPropMgrErrorCode int value) {
        mCarPropertyManagerErrorCode = value;
        AnnotationValidations.validate(
                CarPropMgrErrorCode.class, null, mCarPropertyManagerErrorCode);
        return this;
    }

    @DataClass.Generated.Member
    public @NonNull CarPropertyErrorCodes setVendorErrorCode( int value) {
        mVendorErrorCode = value;
        return this;
    }

    @DataClass.Generated.Member
    public @NonNull CarPropertyErrorCodes setSystemErrorCode( int value) {
        mSystemErrorCode = value;
        return this;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeInt(mCarPropertyManagerErrorCode);
        dest.writeInt(mVendorErrorCode);
        dest.writeInt(mSystemErrorCode);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ CarPropertyErrorCodes(@NonNull Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        int carPropertyManagerErrorCode = in.readInt();
        int vendorErrorCode = in.readInt();
        int systemErrorCode = in.readInt();

        this.mCarPropertyManagerErrorCode = carPropertyManagerErrorCode;
        AnnotationValidations.validate(
                CarPropMgrErrorCode.class, null, mCarPropertyManagerErrorCode);
        this.mVendorErrorCode = vendorErrorCode;
        this.mSystemErrorCode = systemErrorCode;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<CarPropertyErrorCodes> CREATOR
            = new Parcelable.Creator<CarPropertyErrorCodes>() {
        @Override
        public CarPropertyErrorCodes[] newArray(int size) {
            return new CarPropertyErrorCodes[size];
        }

        @Override
        public CarPropertyErrorCodes createFromParcel(@NonNull Parcel in) {
            return new CarPropertyErrorCodes(in);
        }
    };

    @DataClass.Generated(
            time = 1708039674741L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/services/Car/car-lib/src/com/android/car/internal/property/CarPropertyErrorCodes.java",
            inputSignatures = "public static final  int STATUS_OK\npublic static final  int STATUS_TRY_AGAIN\nprivate static final  int SYSTEM_ERROR_CODE_MASK\nprivate static final  int VENDOR_ERROR_CODE_SHIFT\npublic static  com.android.car.internal.property.CarPropertyErrorCodes STATUS_OK_NO_ERROR\nprivate @com.android.car.internal.property.CarPropertyErrorCodes.CarPropMgrErrorCode int mCarPropertyManagerErrorCode\nprivate  int mVendorErrorCode\nprivate  int mSystemErrorCode\npublic @com.android.car.internal.property.CarPropertyErrorCodes.CarPropMgrErrorCode int getCarPropertyManagerErrorCode()\npublic  int getVendorErrorCode()\npublic  int getSystemErrorCode()\npublic static  com.android.car.internal.property.CarPropertyErrorCodes convertVhalStatusCodeToCarPropertyManagerErrorCodes(int)\npublic static @android.annotation.SuppressLint @android.car.hardware.property.VehicleHalStatusCode.VehicleHalStatusCodeInt int getVhalSystemErrorCode(int)\npublic static  int getVhalVendorErrorCode(int)\npublic static  boolean isNotAvailableVehicleHalStatusCode(int)\npublic static @android.annotation.NonNull java.lang.String toString(com.android.car.internal.property.CarPropertyErrorCodes)\nclass CarPropertyErrorCodes extends java.lang.Object implements [android.os.Parcelable]\n@com.android.car.internal.util.DataClass(genConstructor=false)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
