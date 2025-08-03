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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.car.VehiclePropertyIds;
import android.car.test.AbstractExpectableTestCase;

import org.junit.Test;

import java.util.List;

public final class CarPropertyHelperUnitTest extends AbstractExpectableTestCase {

    private static final int TEST_PROPERTY_ID1 = 0x234;
    private static final int TEST_PROPERTY_ID2 = 0x54321;
    private static final int SYSTEM_PROPERTY = VehiclePropertyIds.VEHICLE_SPEED_DISPLAY_UNITS;
    // VehiclePropertyGroup:VENDOR, VehicleArea:GLOBAL,VehiclePropertyType:STRING, ID:0x0001
    private static final int VENDOR_PROPERTY = 0x21100001;
    // VehiclePropertyGroup:BACKPORTED, VehicleArea:GLOBAL,VehiclePropertyType:STRING, ID:0x0001
    private static final int BACKPORTED_PROPERTY = 0x31100001;

    @Test
    public void testIsSupported() {

        expectThat(CarPropertyHelper.isSupported(VENDOR_PROPERTY)).isTrue();
        // VEHICLE_SPEED_DISPLAY_UNITS is special because car property manager property ID is
        // different than vehicle HAL property ID.
        expectThat(CarPropertyHelper.isSupported(
                SYSTEM_PROPERTY)).isTrue();
         // This is a regular system property.
        expectThat(CarPropertyHelper.isSupported(VehiclePropertyIds.INFO_VIN)).isTrue();

        expectThat(CarPropertyHelper.isSupported(VehiclePropertyIds.INVALID)).isFalse();
        // This is a wrong property ID. It is like INFO_VIN but with the wrong VehicleArea.
        expectThat(CarPropertyHelper.isSupported(0x12100100)).isFalse();
    }

    @Test
    public void testIsSupported_backportedProperty() {
        assertThat(CarPropertyHelper.isSupported(BACKPORTED_PROPERTY)).isTrue();
    }

    @Test
    public void testPropertyIdsToString() {
        assertWithMessage("String of multiple propertyIds").that(CarPropertyHelper
                        .propertyIdsToString(List.of(TEST_PROPERTY_ID1, TEST_PROPERTY_ID2,
                                VehiclePropertyIds.INFO_VIN)))
                .isEqualTo("[0x234, 0x54321, INFO_VIN]");
    }

    @Test
    public void testIsVendorOrBackportedProperty() {
        expectThat(CarPropertyHelper.isVendorOrBackportedProperty(VENDOR_PROPERTY)).isTrue();
        expectThat(CarPropertyHelper.isVendorOrBackportedProperty(BACKPORTED_PROPERTY)).isTrue();
        expectThat(CarPropertyHelper.isVendorOrBackportedProperty(SYSTEM_PROPERTY)).isFalse();
    }

    @Test
    public void testIsVendorProperty() {
        expectThat(CarPropertyHelper.isVendorProperty(VENDOR_PROPERTY)).isTrue();
        expectThat(CarPropertyHelper.isVendorProperty(BACKPORTED_PROPERTY)).isFalse();
        expectThat(CarPropertyHelper.isVendorProperty(SYSTEM_PROPERTY)).isFalse();
    }

    @Test
    public void testIsBackportedProperty() {
        expectThat(CarPropertyHelper.isBackportedProperty(VENDOR_PROPERTY)).isFalse();
        expectThat(CarPropertyHelper.isBackportedProperty(BACKPORTED_PROPERTY)).isTrue();
        expectThat(CarPropertyHelper.isBackportedProperty(SYSTEM_PROPERTY)).isFalse();
    }
}
