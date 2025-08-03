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
package android.telephony.cts;

import static com.google.common.truth.Truth.assertThat;

import android.net.InetAddresses;
import android.os.Parcel;
import android.telephony.data.EpsBearerQosSessionAttributes;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public final class EpsBearerQosSessionAttributesTest {

    private static final int QCI1 = 5;
    private static final int QCI2 = 6;
    private static final int MBR_DOWNLINK = 101;
    private static final int MBR_UPLINK = 102;
    private static final int GBR_DOWNLINK = 201;
    private static final int GBR_UPLINK = 202;
    private static final List<InetSocketAddress> REMOTE_ADDR = Arrays.asList(
            new InetSocketAddress(InetAddresses.parseNumericAddress("122.22.22.22"), 2222));
    @Test
    public void testConstructorAndGetters() {
        EpsBearerQosSessionAttributes epsBearerAttr = new EpsBearerQosSessionAttributes(QCI1,
                MBR_DOWNLINK, MBR_UPLINK, GBR_DOWNLINK, GBR_UPLINK, REMOTE_ADDR);

        assertThat(epsBearerAttr.getQosIdentifier()).isEqualTo(QCI1);
        assertThat(epsBearerAttr.getMaxDownlinkBitRateKbps()).isEqualTo(MBR_DOWNLINK);
        assertThat(epsBearerAttr.getMaxUplinkBitRateKbps()).isEqualTo(MBR_UPLINK);
        assertThat(epsBearerAttr.getGuaranteedDownlinkBitRateKbps()).isEqualTo(GBR_DOWNLINK);
        assertThat(epsBearerAttr.getGuaranteedUplinkBitRateKbps()).isEqualTo(GBR_UPLINK);
        assertThat(epsBearerAttr.getRemoteAddresses()).isEqualTo(REMOTE_ADDR);
    }

    @Test
    public void testEquals() {
        EpsBearerQosSessionAttributes epsBearerAttr1 = new EpsBearerQosSessionAttributes(QCI1,
                MBR_DOWNLINK, MBR_UPLINK, GBR_DOWNLINK, GBR_UPLINK, REMOTE_ADDR);

        EpsBearerQosSessionAttributes epsBearerAttr2 = new EpsBearerQosSessionAttributes(QCI1,
                MBR_DOWNLINK, MBR_UPLINK, GBR_DOWNLINK, GBR_UPLINK, REMOTE_ADDR);

        assertThat(epsBearerAttr1).isEqualTo(epsBearerAttr2);
    }

    @Test
    public void testNotEquals() {
        EpsBearerQosSessionAttributes epsBearerAttr1 = new EpsBearerQosSessionAttributes(QCI1,
                MBR_DOWNLINK, MBR_UPLINK, GBR_DOWNLINK, GBR_UPLINK, REMOTE_ADDR);

        EpsBearerQosSessionAttributes epsBearerAttr2 = new EpsBearerQosSessionAttributes(QCI2,
                MBR_DOWNLINK, MBR_UPLINK, GBR_DOWNLINK, GBR_UPLINK, REMOTE_ADDR);

        assertThat(epsBearerAttr1).isNotEqualTo(epsBearerAttr2);
        assertThat(epsBearerAttr1).isNotEqualTo(null);
        assertThat(epsBearerAttr1).isNotEqualTo(new String[1]);
    }

    @Test
    public void testParcel() {
        EpsBearerQosSessionAttributes epsBearerAttr1 = new EpsBearerQosSessionAttributes(QCI1,
                MBR_DOWNLINK, MBR_UPLINK, GBR_DOWNLINK, GBR_UPLINK, REMOTE_ADDR);

        Parcel parcel = Parcel.obtain();
        epsBearerAttr1.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        EpsBearerQosSessionAttributes epsBearerAttr2 =
                EpsBearerQosSessionAttributes.CREATOR.createFromParcel(parcel);

        assertThat(epsBearerAttr1).isEqualTo(epsBearerAttr2);
    }
}
