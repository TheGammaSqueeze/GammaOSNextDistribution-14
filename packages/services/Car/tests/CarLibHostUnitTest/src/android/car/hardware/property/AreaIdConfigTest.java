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

package android.car.hardware.property;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;

import org.junit.Test;

import java.util.List;

/**
 * Unit tests for {@link AreaIdConfig}
 */
public class AreaIdConfigTest {
    private static final int ACCESS = 1;
    private static final int AREA_ID = 99;
    private static final int MAX_VALUE = 10;
    private static final int MIN_VALUE = 1;
    private static final List<Integer> SUPPORTED_ENUM_VALUES = List.of(2, 3, 4);
    private static final AreaIdConfig<Integer> AREA_ID_CONFIG = new AreaIdConfig.Builder<Integer>(
            ACCESS, AREA_ID).setMaxValue(MAX_VALUE).setMinValue(MIN_VALUE).setSupportedEnumValues(
            SUPPORTED_ENUM_VALUES).setSupportVariableUpdateRate(true).build();

    @Test
    public void getAccess_returnsExpectedValue() {
        assertThat(AREA_ID_CONFIG.getAccess()).isEqualTo(ACCESS);
    }

    @Test
    public void getAreaId_returnsExpectedValue() {
        assertThat(AREA_ID_CONFIG.getAreaId()).isEqualTo(AREA_ID);
    }

    @Test
    public void getMinValue_returnsExpectedValue() {
        assertThat(AREA_ID_CONFIG.getMinValue()).isEqualTo(MIN_VALUE);
    }

    @Test
    public void getMinValue_returnsNullIfNotSet() {
        assertThat(new AreaIdConfig.Builder<Long>(ACCESS, AREA_ID).build().getMinValue()).isNull();
    }

    @Test
    public void getMaxValue_returnsExpectedValue() {
        assertThat(AREA_ID_CONFIG.getMaxValue()).isEqualTo(MAX_VALUE);
    }

    @Test
    public void getMaxValue_returnsNullIfNotSet() {
        assertThat(new AreaIdConfig.Builder<Long>(ACCESS, AREA_ID).build().getMaxValue()).isNull();
    }

    @Test
    public void getSupportedEnumValues_returnsExpectedValue() {
        assertThat(AREA_ID_CONFIG.getSupportedEnumValues()).containsExactlyElementsIn(
                SUPPORTED_ENUM_VALUES);
    }

    @Test
    public void getSupportedEnumValues_returnsEmptyListIfNoSet() {
        assertThat(new AreaIdConfig.Builder<Long>(
                ACCESS, AREA_ID).build().getSupportedEnumValues()).isEmpty();
    }

    @Test
    public void describeContents_returnsExpectedValue() {
        assertThat(AREA_ID_CONFIG.describeContents()).isEqualTo(0);
    }

    @Test
    public void isVariableUpdateRateSupported() {
        assertThat(AREA_ID_CONFIG.isVariableUpdateRateSupported()).isTrue();
    }

    @Test
    public void isVariableUpdateRateSupported_defaultFalse() {
        assertThat(new AreaIdConfig.Builder<Long>(AREA_ID).build().isVariableUpdateRateSupported())
                .isFalse();
    }

    @Test
    public void writeToParcel_writesCorrectly() {
        Parcel parcel = Parcel.obtain();
        AREA_ID_CONFIG.writeToParcel(parcel, /*flags=*/0);

        // After you're done with writing, you need to reset the parcel for reading.
        parcel.setDataPosition(0);

        AreaIdConfig<Object> areaIdConfig = AreaIdConfig.CREATOR.createFromParcel(parcel);
        assertThat((Integer) areaIdConfig.getMaxValue()).isEqualTo(MAX_VALUE);
        assertThat((Integer) areaIdConfig.getMinValue()).isEqualTo(MIN_VALUE);
        assertThat(
                (List<?>) areaIdConfig.getSupportedEnumValues()).containsExactlyElementsIn(
                SUPPORTED_ENUM_VALUES);
        assertThat(areaIdConfig.isVariableUpdateRateSupported()).isTrue();
    }

    @Test
    public void toString_doesNotReturnNull() {
        assertThat(AREA_ID_CONFIG.toString()).isNotNull();
    }

    @Test
    public void creator_newArrayWorksCorrectly() {
        AreaIdConfig<Object>[] areaIdConfigs = AreaIdConfig.CREATOR.newArray(2);
        assertThat(areaIdConfigs).hasLength(2);
        assertThat(areaIdConfigs[0]).isNull();
        assertThat(areaIdConfigs[1]).isNull();
    }
}

