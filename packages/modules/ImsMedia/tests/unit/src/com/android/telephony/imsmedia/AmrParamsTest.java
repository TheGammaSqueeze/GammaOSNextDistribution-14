/**
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

package com.android.telephony.imsmedia.tests;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.telephony.imsmedia.AmrParams;
import androidx.test.runner.AndroidJUnit4;

import org.junit.runner.RunWith;
import org.junit.Test;

@RunWith(AndroidJUnit4.class)
public class AmrParamsTest {
    private static final boolean OCTET_ALIGNED = true;
    private static final int MAX_REDUNDANCY_MILLIS = 1001;

    private AmrParams createAmrParams(int amrMode,
            boolean octetAligned, int maxRedundancyMillis) {
        return new AmrParams.Builder()
                .setAmrMode(amrMode)
                .setOctetAligned(octetAligned)
                .setMaxRedundancyMillis(maxRedundancyMillis)
                .build();
    }

    @Test
    public void testConstructorAndGetters() {
        AmrParams amr = createAmrParams(AmrParams.AMR_MODE_5, OCTET_ALIGNED,
                MAX_REDUNDANCY_MILLIS);

        assertThat(amr.getAmrMode()).isEqualTo(AmrParams.AMR_MODE_5);
        assertThat(amr.getOctetAligned()).isEqualTo(OCTET_ALIGNED);
        assertThat(amr.getMaxRedundancyMillis()).isEqualTo(MAX_REDUNDANCY_MILLIS);
    }

    @Test
    public void testParcel() {
        AmrParams amr = createAmrParams(AmrParams.AMR_MODE_5, OCTET_ALIGNED, MAX_REDUNDANCY_MILLIS);

        Parcel parcel = Parcel.obtain();
        amr.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        AmrParams parcelConfig = AmrParams.CREATOR.createFromParcel(parcel);
        assertThat(amr).isEqualTo(parcelConfig);
    }

    @Test
    public void testEqual() {
        AmrParams amr1 = createAmrParams(AmrParams.AMR_MODE_5, OCTET_ALIGNED,
                MAX_REDUNDANCY_MILLIS);

        AmrParams amr2 = createAmrParams(AmrParams.AMR_MODE_5, OCTET_ALIGNED,
                MAX_REDUNDANCY_MILLIS);

        assertThat(amr1).isEqualTo(amr2);
    }

    @Test
    public void testNotEqual() {
        AmrParams amr1 = createAmrParams(AmrParams.AMR_MODE_5, OCTET_ALIGNED,
                MAX_REDUNDANCY_MILLIS);

        AmrParams amr2 = createAmrParams(AmrParams.AMR_MODE_6, OCTET_ALIGNED,
                MAX_REDUNDANCY_MILLIS);

        assertThat(amr1).isNotEqualTo(amr2);

        AmrParams amr3 = createAmrParams(AmrParams.AMR_MODE_5, OCTET_ALIGNED, 1002);

        assertThat(amr1).isNotEqualTo(amr3);

        AmrParams amr4 = createAmrParams(AmrParams.AMR_MODE_5, false, MAX_REDUNDANCY_MILLIS);

        assertThat(amr1).isNotEqualTo(amr4);
    }
}
