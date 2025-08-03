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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.car.cts.utils.VehiclePropertyUtils;
import android.car.feature.Flags;
import android.car.hardware.property.DetailedErrorCode;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class DetailedErrorCodeTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAR_PROPERTY_DETAILED_ERROR_CODES)
    public void testToString() {
        assertThat(DetailedErrorCode.toString(
                    DetailedErrorCode.NO_DETAILED_ERROR_CODE))
                .isEqualTo("NO_DETAILED_ERROR_CODE");
        assertThat(DetailedErrorCode.toString(
                    DetailedErrorCode.NOT_AVAILABLE_DISABLED))
                .isEqualTo("NOT_AVAILABLE_DISABLED");
        assertThat(DetailedErrorCode.toString(
                    DetailedErrorCode.NOT_AVAILABLE_SPEED_LOW))
                .isEqualTo("NOT_AVAILABLE_SPEED_LOW");
        assertThat(DetailedErrorCode.toString(
                    DetailedErrorCode.NOT_AVAILABLE_SPEED_HIGH))
                .isEqualTo("NOT_AVAILABLE_SPEED_HIGH");
        assertThat(DetailedErrorCode.toString(
                    DetailedErrorCode.NOT_AVAILABLE_POOR_VISIBILITY))
                .isEqualTo("NOT_AVAILABLE_POOR_VISIBILITY");
        assertThat(DetailedErrorCode.toString(
                    DetailedErrorCode.NOT_AVAILABLE_SAFETY))
                .isEqualTo("NOT_AVAILABLE_SAFETY");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAR_PROPERTY_DETAILED_ERROR_CODES)
    public void testAllDetailedErrorCodesAreMappedInToString() {
        List<Integer> detailedErrorCodes =
                VehiclePropertyUtils.getIntegersFromDataEnums(DetailedErrorCode.class);
        for (Integer errorCode : detailedErrorCodes) {
            String detailedErrorCodeString = DetailedErrorCode.toString(errorCode);
            assertWithMessage("%s should be non-empty ", detailedErrorCodeString).that(
                    detailedErrorCodeString.isEmpty()).isFalse();
        }
    }
}
