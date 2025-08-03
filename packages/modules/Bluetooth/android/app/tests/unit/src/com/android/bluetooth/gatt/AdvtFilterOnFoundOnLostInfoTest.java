/*
 * Copyright 2022 The Android Open Source Project
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
 * Test cases for {@link AdvtFilterOnFoundOnLostInfoTest}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class AdvtFilterOnFoundOnLostInfoTest {

    @Test
    public void advtFilterOnFoundOnLostInfoParams() {
        int clientIf = 0;
        int advPktLen = 1;
        byte[] advPkt = new byte[]{0x02};
        int scanRspLen = 3;
        byte[] scanRsp = new byte[]{0x04};
        int filtIndex = 5;
        int advState = 6;
        int advInfoPresent = 7;
        String address = "00:11:22:33:FF:EE";
        int addrType = 8;
        int txPower = 9;
        int rssiValue = 10;
        int timeStamp = 11;

        AdvtFilterOnFoundOnLostInfo advtFilterOnFoundOnLostInfo = new AdvtFilterOnFoundOnLostInfo(
                clientIf,
                advPktLen,
                advPkt,
                scanRspLen,
                scanRsp,
                filtIndex,
                advState,
                advInfoPresent,
                address,
                addrType,
                txPower,
                rssiValue,
                timeStamp
        );

        assertThat(advtFilterOnFoundOnLostInfo.getClientIf()).isEqualTo(clientIf);
        assertThat(advtFilterOnFoundOnLostInfo.getFiltIndex()).isEqualTo(filtIndex);
        assertThat(advtFilterOnFoundOnLostInfo.getAdvState()).isEqualTo(advState);
        assertThat(advtFilterOnFoundOnLostInfo.getTxPower()).isEqualTo(txPower);
        assertThat(advtFilterOnFoundOnLostInfo.getTimeStamp()).isEqualTo(timeStamp);
        assertThat(advtFilterOnFoundOnLostInfo.getRSSIValue()).isEqualTo(rssiValue);
        assertThat(advtFilterOnFoundOnLostInfo.getAdvInfoPresent()).isEqualTo(advInfoPresent);
        assertThat(advtFilterOnFoundOnLostInfo.getAddress()).isEqualTo(address);
        assertThat(advtFilterOnFoundOnLostInfo.getAddressType()).isEqualTo(addrType);
        assertThat(advtFilterOnFoundOnLostInfo.getAdvPacketData()).isEqualTo(advPkt);
        assertThat(advtFilterOnFoundOnLostInfo.getAdvPacketLen()).isEqualTo(advPktLen);
        assertThat(advtFilterOnFoundOnLostInfo.getScanRspData()).isEqualTo(scanRsp);
        assertThat(advtFilterOnFoundOnLostInfo.getScanRspLen()).isEqualTo(scanRspLen);

        byte[] resultByteArray = new byte[]{2, 4};
        assertThat(advtFilterOnFoundOnLostInfo.getResult()).isEqualTo(resultByteArray);
    }
}
