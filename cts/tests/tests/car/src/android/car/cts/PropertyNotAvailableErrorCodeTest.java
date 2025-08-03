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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.car.cts.utils.VehiclePropertyUtils;
import android.car.hardware.property.PropertyNotAvailableErrorCode;

import org.junit.Test;

import java.util.List;

public class PropertyNotAvailableErrorCodeTest {
    @Test
    public void testToString() {
        assertThat(PropertyNotAvailableErrorCode.toString(
                        PropertyNotAvailableErrorCode.NOT_AVAILABLE))
                .isEqualTo("NOT_AVAILABLE");
        assertThat(PropertyNotAvailableErrorCode.toString(
                        PropertyNotAvailableErrorCode.NOT_AVAILABLE_DISABLED))
                .isEqualTo("NOT_AVAILABLE_DISABLED");
        assertThat(PropertyNotAvailableErrorCode.toString(
                        PropertyNotAvailableErrorCode.NOT_AVAILABLE_SPEED_LOW))
                .isEqualTo("NOT_AVAILABLE_SPEED_LOW");
        assertThat(PropertyNotAvailableErrorCode.toString(
                        PropertyNotAvailableErrorCode.NOT_AVAILABLE_SPEED_HIGH))
                .isEqualTo("NOT_AVAILABLE_SPEED_HIGH");
        assertThat(PropertyNotAvailableErrorCode.toString(
                        PropertyNotAvailableErrorCode.NOT_AVAILABLE_POOR_VISIBILITY))
                .isEqualTo("NOT_AVAILABLE_POOR_VISIBILITY");
        assertThat(PropertyNotAvailableErrorCode.toString(
                        PropertyNotAvailableErrorCode.NOT_AVAILABLE_SAFETY))
                .isEqualTo("NOT_AVAILABLE_SAFETY");
    }

    @Test
    public void testAllPropertyNotAvailableErrorCodesAreMappedInToString() {
        List<Integer> propertyNotAvailableErrorCodes =
                VehiclePropertyUtils.getIntegersFromDataEnums(PropertyNotAvailableErrorCode.class);
        for (Integer errorCode : propertyNotAvailableErrorCodes) {
            String propertyNotAvailableErrorCodeString =
                    PropertyNotAvailableErrorCode.toString(errorCode);
            assertWithMessage("%s should start with ", propertyNotAvailableErrorCodeString).that(
                    propertyNotAvailableErrorCodeString.startsWith("NOT")).isTrue();
        }
    }
}
