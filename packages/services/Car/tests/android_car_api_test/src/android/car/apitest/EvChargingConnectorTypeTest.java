/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.apitest;

import static com.google.common.truth.Truth.assertThat;

import android.car.hardware.property.EvChargingConnectorType;
import android.hardware.automotive.vehicle.EvConnectorType;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class EvChargingConnectorTypeTest extends CarLessApiTestBase {

    @Test
    @ApiTest(apis = {"android.car.hardware.property.EvChargingConnectorType#UNKNOWN",
            "android.car.hardware.property.EvChargingConnectorType#IEC_TYPE_1_AC",
            "android.car.hardware.property.EvChargingConnectorType#IEC_TYPE_2_AC",
            "android.car.hardware.property.EvChargingConnectorType#IEC_TYPE_3_AC",
            "android.car.hardware.property.EvChargingConnectorType#IEC_TYPE_4_DC",
            "android.car.hardware.property.EvChargingConnectorType#IEC_TYPE_1_CCS_DC",
            "android.car.hardware.property.EvChargingConnectorType#IEC_TYPE_2_CCS_DC",
            "android.car.hardware.property.EvChargingConnectorType#TESLA_HPWC",
            "android.car.hardware.property.EvChargingConnectorType#TESLA_ROADSTER",
            "android.car.hardware.property.EvChargingConnectorType#TESLA_SUPERCHARGER",
            "android.car.hardware.property.EvChargingConnectorType#GBT_AC",
            "android.car.hardware.property.EvChargingConnectorType#GBT_DC",
            "android.car.hardware.property.EvChargingConnectorType#OTHER"})
    public void testMatchWithVehicleHal() {
        assertThat(EvChargingConnectorType.UNKNOWN).isEqualTo(EvConnectorType.UNKNOWN);
        assertThat(EvChargingConnectorType.IEC_TYPE_1_AC).isEqualTo(EvConnectorType.IEC_TYPE_1_AC);
        assertThat(EvChargingConnectorType.IEC_TYPE_2_AC).isEqualTo(EvConnectorType.IEC_TYPE_2_AC);
        assertThat(EvChargingConnectorType.IEC_TYPE_3_AC).isEqualTo(EvConnectorType.IEC_TYPE_3_AC);
        assertThat(EvChargingConnectorType.IEC_TYPE_4_DC).isEqualTo(EvConnectorType.IEC_TYPE_4_DC);
        assertThat(EvChargingConnectorType.IEC_TYPE_1_CCS_DC)
                .isEqualTo(EvConnectorType.IEC_TYPE_1_CCS_DC);
        assertThat(EvChargingConnectorType.IEC_TYPE_2_CCS_DC)
                .isEqualTo(EvConnectorType.IEC_TYPE_2_CCS_DC);
        assertThat(EvChargingConnectorType.TESLA_HPWC).isEqualTo(EvConnectorType.TESLA_HPWC);
        assertThat(EvChargingConnectorType.TESLA_ROADSTER)
                .isEqualTo(EvConnectorType.TESLA_ROADSTER);
        assertThat(EvChargingConnectorType.TESLA_SUPERCHARGER)
                .isEqualTo(EvConnectorType.TESLA_SUPERCHARGER);
        assertThat(EvChargingConnectorType.GBT_AC).isEqualTo(EvConnectorType.GBT_AC);
        assertThat(EvChargingConnectorType.GBT_DC).isEqualTo(EvConnectorType.GBT_DC);
        assertThat(EvChargingConnectorType.OTHER).isEqualTo(EvConnectorType.OTHER);
    }
}
