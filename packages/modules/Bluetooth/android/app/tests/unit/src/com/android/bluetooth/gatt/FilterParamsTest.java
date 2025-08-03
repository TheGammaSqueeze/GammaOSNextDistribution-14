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

package com.android.bluetooth.gatt;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test cases for {@link FilterParams}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class FilterParamsTest {

    @Test
    public void filterParamsProperties() {
        int clientIf = 0;
        int filtIndex = 1;
        int featSeln = 2;
        int listLogicType = 3;
        int filtLogicType = 4;
        int rssiHighValue = 5;
        int rssiLowValue = 6;
        int delyMode = 7;
        int foundTimeOut = 8;
        int lostTimeOut = 9;
        int foundTimeOutCnt = 10;
        int numOfTrackEntries = 11;

        FilterParams filterParams = new FilterParams(
                clientIf,
                filtIndex,
                featSeln,
                listLogicType,
                filtLogicType,
                rssiHighValue,
                rssiLowValue,
                delyMode,
                foundTimeOut,
                lostTimeOut,
                foundTimeOutCnt,
                numOfTrackEntries
        );

        assertThat(filterParams).isNotNull();

        assertThat(filterParams.getClientIf()).isEqualTo(clientIf);
        assertThat(filterParams.getFiltIndex()).isEqualTo(filtIndex);
        assertThat(filterParams.getFeatSeln()).isEqualTo(featSeln);
        assertThat(filterParams.getListLogicType()).isEqualTo(listLogicType);
        assertThat(filterParams.getFiltLogicType()).isEqualTo(filtLogicType);
        assertThat(filterParams.getRSSIHighValue()).isEqualTo(rssiHighValue);
        assertThat(filterParams.getRSSILowValue()).isEqualTo(rssiLowValue);
        assertThat(filterParams.getDelyMode()).isEqualTo(delyMode);
        assertThat(filterParams.getFoundTimeout()).isEqualTo(foundTimeOut);
        assertThat(filterParams.getLostTimeout()).isEqualTo(lostTimeOut);
        assertThat(filterParams.getFoundTimeOutCnt()).isEqualTo(foundTimeOutCnt);
        assertThat(filterParams.getNumOfTrackEntries()).isEqualTo(numOfTrackEntries);
    }
}
