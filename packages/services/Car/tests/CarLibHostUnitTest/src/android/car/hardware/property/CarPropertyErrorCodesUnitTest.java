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

package com.android.car;

import static com.android.car.internal.property.CarPropertyErrorCodes.convertVhalStatusCodeToCarPropertyManagerErrorCodes;

import static com.google.common.truth.Truth.assertThat;

import android.car.hardware.property.CarPropertyManager;
import android.hardware.automotive.vehicle.StatusCode;
import android.util.SparseIntArray;

import com.android.car.internal.property.CarPropertyErrorCodes;

import org.junit.Test;

public final class CarPropertyErrorCodesUnitTest {

    private static final int NO_ERROR = 0;
    private static final int SYSTEM_ERROR_CODE = 0x0123;
    private static final int VENDOR_ERROR_CODE = 0x1234;
    private static final int VENDOR_ERROR_CODE_SHIFT = 16;
    private static final int COMBINED_ERROR_CODE = 0x12340123;

    @Test
    public void testCarPropertyErrorCodesStatusOkNoErrors() throws Exception {
        CarPropertyErrorCodes carPropertyErrorCodes =
                new CarPropertyErrorCodes(CarPropertyErrorCodes.STATUS_OK, NO_ERROR, NO_ERROR);

        assertThat(carPropertyErrorCodes.getCarPropertyManagerErrorCode())
                .isEqualTo(CarPropertyErrorCodes.STATUS_OK);
        assertThat(carPropertyErrorCodes.getVendorErrorCode())
                .isEqualTo(NO_ERROR);
        assertThat(carPropertyErrorCodes.getSystemErrorCode())
                .isEqualTo(NO_ERROR);
    }

    @Test
    public void testCarPropertyErrorCodesStatusInternalError() throws Exception {
        CarPropertyErrorCodes carPropertyErrorCodes = new CarPropertyErrorCodes(
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR, NO_ERROR, NO_ERROR);

        assertThat(carPropertyErrorCodes.getCarPropertyManagerErrorCode())
                .isEqualTo(CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
        assertThat(carPropertyErrorCodes.getVendorErrorCode())
                .isEqualTo(NO_ERROR);
        assertThat(carPropertyErrorCodes.getSystemErrorCode())
                .isEqualTo(NO_ERROR);
    }

    @Test
    public void testCarPropertyErrorCodesStatusNotAvailable() throws Exception {
        CarPropertyErrorCodes carPropertyErrorCodes = new CarPropertyErrorCodes(
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE, NO_ERROR, NO_ERROR);

        assertThat(carPropertyErrorCodes.getCarPropertyManagerErrorCode())
                .isEqualTo(CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
        assertThat(carPropertyErrorCodes.getVendorErrorCode())
                .isEqualTo(NO_ERROR);
        assertThat(carPropertyErrorCodes.getSystemErrorCode())
                .isEqualTo(NO_ERROR);
    }

    @Test
    public void testCarPropertyErrorCodesStatusNotAvailableSpeedLow() throws Exception {
        CarPropertyErrorCodes carPropertyErrorCodes = new CarPropertyErrorCodes(
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE,
                NO_ERROR,
                StatusCode.NOT_AVAILABLE_SPEED_LOW);

        assertThat(carPropertyErrorCodes.getCarPropertyManagerErrorCode())
                .isEqualTo(CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
        assertThat(carPropertyErrorCodes.getVendorErrorCode())
                .isEqualTo(NO_ERROR);
        assertThat(carPropertyErrorCodes.getSystemErrorCode())
                .isEqualTo(StatusCode.NOT_AVAILABLE_SPEED_LOW);
    }

    @Test
    public void testCarPropertyErrorCodesStatusNotAvailableVendorError() throws Exception {
        CarPropertyErrorCodes carPropertyErrorCodes = new CarPropertyErrorCodes(
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE,
                VENDOR_ERROR_CODE,
                NO_ERROR);

        assertThat(carPropertyErrorCodes.getCarPropertyManagerErrorCode())
                .isEqualTo(CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
        assertThat(carPropertyErrorCodes.getVendorErrorCode())
                .isEqualTo(VENDOR_ERROR_CODE);
        assertThat(carPropertyErrorCodes.getSystemErrorCode())
                .isEqualTo(NO_ERROR);
    }

    @Test
    public void testConvertHalToCarPropertyManagerErrorStatusOK() throws Exception {
        CarPropertyErrorCodes carPropertyErrorCodes =
                convertVhalStatusCodeToCarPropertyManagerErrorCodes(StatusCode.OK);

        assertThat(carPropertyErrorCodes.getCarPropertyManagerErrorCode())
                .isEqualTo(CarPropertyErrorCodes.STATUS_OK);
        assertThat(carPropertyErrorCodes.getVendorErrorCode()).isEqualTo(0);
        assertThat(carPropertyErrorCodes.getSystemErrorCode())
                .isEqualTo(CarPropertyErrorCodes.STATUS_OK);
    }

    @Test
    public void testConvertHalToCarPropertyManagerErrorStatus() throws Exception {
        SparseIntArray convertVhalStatusCodeToCarPropertyManagerErrorCodes = new SparseIntArray();
        convertVhalStatusCodeToCarPropertyManagerErrorCodes.put(StatusCode.NOT_AVAILABLE,
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
        convertVhalStatusCodeToCarPropertyManagerErrorCodes.put(StatusCode.NOT_AVAILABLE_DISABLED,
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
        convertVhalStatusCodeToCarPropertyManagerErrorCodes.put(StatusCode.NOT_AVAILABLE_SPEED_LOW,
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
        convertVhalStatusCodeToCarPropertyManagerErrorCodes.put(StatusCode.NOT_AVAILABLE_SPEED_HIGH,
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
        convertVhalStatusCodeToCarPropertyManagerErrorCodes.put(
                StatusCode.NOT_AVAILABLE_POOR_VISIBILITY,
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
        convertVhalStatusCodeToCarPropertyManagerErrorCodes.put(StatusCode.NOT_AVAILABLE_SAFETY,
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
        convertVhalStatusCodeToCarPropertyManagerErrorCodes.put(StatusCode.TRY_AGAIN,
                CarPropertyErrorCodes.STATUS_TRY_AGAIN);
        convertVhalStatusCodeToCarPropertyManagerErrorCodes.put(StatusCode.INTERNAL_ERROR,
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);

        for (int i = 0; i < convertVhalStatusCodeToCarPropertyManagerErrorCodes.size(); i++) {
            int statusCode = convertVhalStatusCodeToCarPropertyManagerErrorCodes.keyAt(i);
            int carPropMgrError = convertVhalStatusCodeToCarPropertyManagerErrorCodes.valueAt(i);
            CarPropertyErrorCodes carPropertyErrorCodes =
                    convertVhalStatusCodeToCarPropertyManagerErrorCodes(
                            statusCode | (VENDOR_ERROR_CODE << VENDOR_ERROR_CODE_SHIFT));

            assertThat(carPropertyErrorCodes.getCarPropertyManagerErrorCode())
                    .isEqualTo(carPropMgrError);
            assertThat(carPropertyErrorCodes.getVendorErrorCode()).isEqualTo(VENDOR_ERROR_CODE);
            assertThat(carPropertyErrorCodes.getSystemErrorCode()).isEqualTo(statusCode);
        }
    }

    @Test
    public void testGetVhalSystemErrorcode() {
        assertThat(CarPropertyErrorCodes.getVhalSystemErrorCode(COMBINED_ERROR_CODE)).isEqualTo(
                SYSTEM_ERROR_CODE);
    }

    @Test
    public void testGetVhalVendorErrorCode() {
        assertThat(CarPropertyErrorCodes.getVhalVendorErrorCode(COMBINED_ERROR_CODE)).isEqualTo(
                VENDOR_ERROR_CODE);
    }
}
