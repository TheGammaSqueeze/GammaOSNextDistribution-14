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
import android.telephony.data.NrQosSessionAttributes;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public final class NrQosSessionAttributesTest {

    private static final int FIVE_QI1 = 5;
    private static final int FIVE_QI2 = 6;
    private static final int QFI = 4;
    private static final int MBR_DOWNLINK = 101;
    private static final int MBR_UPLINK = 102;
    private static final int GBR_DOWNLINK = 201;
    private static final int GBR_UPLINK = 202;
    private static final long AVG_WINDOW = 100;
    private static final List<InetSocketAddress> REMOTE_ADDR = Arrays.asList(
            new InetSocketAddress(InetAddresses.parseNumericAddress("122.22.22.22"), 2222));
    @Test
    public void testConstructorAndGetters() {
        NrQosSessionAttributes nrAttr = new NrQosSessionAttributes(FIVE_QI1, QFI, MBR_DOWNLINK,
                MBR_UPLINK, GBR_DOWNLINK, GBR_UPLINK, AVG_WINDOW, REMOTE_ADDR);

        assertThat(nrAttr.getQosIdentifier()).isEqualTo(FIVE_QI1);
        assertThat(nrAttr.getQosFlowIdentifier()).isEqualTo(QFI);
        assertThat(nrAttr.getMaxDownlinkBitRateKbps()).isEqualTo(MBR_DOWNLINK);
        assertThat(nrAttr.getMaxUplinkBitRateKbps()).isEqualTo(MBR_UPLINK);
        assertThat(nrAttr.getGuaranteedDownlinkBitRateKbps()).isEqualTo(GBR_DOWNLINK);
        assertThat(nrAttr.getGuaranteedUplinkBitRateKbps()).isEqualTo(GBR_UPLINK);
        assertThat(nrAttr.getBitRateWindowDuration()).isEqualTo(Duration.ofMillis(AVG_WINDOW));
        assertThat(nrAttr.getRemoteAddresses()).isEqualTo(REMOTE_ADDR);
    }

    @Test
    public void testEquals() {
        NrQosSessionAttributes nrAttr1 = new NrQosSessionAttributes(FIVE_QI1, QFI, MBR_DOWNLINK,
                MBR_UPLINK, GBR_DOWNLINK, GBR_UPLINK, AVG_WINDOW, REMOTE_ADDR);

        NrQosSessionAttributes nrAttr2 = new NrQosSessionAttributes(FIVE_QI1, QFI, MBR_DOWNLINK,
                MBR_UPLINK, GBR_DOWNLINK, GBR_UPLINK, AVG_WINDOW, REMOTE_ADDR);

        assertThat(nrAttr1).isEqualTo(nrAttr2);
    }

    @Test
    public void testNotEquals() {
        NrQosSessionAttributes nrAttr1 = new NrQosSessionAttributes(FIVE_QI1, QFI, MBR_DOWNLINK,
                MBR_UPLINK, GBR_DOWNLINK, GBR_UPLINK, AVG_WINDOW, REMOTE_ADDR);

        NrQosSessionAttributes nrAttr2 = new NrQosSessionAttributes(FIVE_QI2, QFI, MBR_DOWNLINK,
                MBR_UPLINK, GBR_DOWNLINK, GBR_UPLINK, AVG_WINDOW, REMOTE_ADDR);

        assertThat(nrAttr1).isNotEqualTo(nrAttr2);
        assertThat(nrAttr1).isNotEqualTo(null);
        assertThat(nrAttr1).isNotEqualTo(new String[1]);
    }

    @Test
    public void testParcel() {
        NrQosSessionAttributes nrAttr1 = new NrQosSessionAttributes(FIVE_QI1, QFI, MBR_DOWNLINK,
                MBR_UPLINK, GBR_DOWNLINK, GBR_UPLINK, AVG_WINDOW, REMOTE_ADDR);

        Parcel parcel = Parcel.obtain();
        nrAttr1.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        NrQosSessionAttributes nrAttr2 =
                NrQosSessionAttributes.CREATOR.createFromParcel(parcel);

        assertThat(nrAttr1).isEqualTo(nrAttr2);
    }
}
