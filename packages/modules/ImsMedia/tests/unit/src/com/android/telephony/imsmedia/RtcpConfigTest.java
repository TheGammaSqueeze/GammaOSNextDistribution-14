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
import android.telephony.imsmedia.RtcpConfig;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.runner.RunWith;
import org.junit.Test;

@RunWith(AndroidJUnit4.class)
public class RtcpConfigTest {
    private static final String NAME = "name";
    private static final int PORT = 3333;
    private static final int INTERVAL = 66;
    private static final int BLOCK_TYPES = RtcpConfig.FLAG_RTCPXR_DLRR_REPORT_BLOCK;

    @Test
    public void testConstructorAndGetters() {
        RtcpConfig rtcp = new RtcpConfig.Builder()
                .setCanonicalName(NAME)
                .setTransmitPort(PORT)
                .setIntervalSec(INTERVAL)
                .setRtcpXrBlockTypes(BLOCK_TYPES)
                .build();

        assertThat(rtcp.getCanonicalName()).isEqualTo(NAME);
        assertThat(rtcp.getTransmitPort()).isEqualTo(PORT);
        assertThat(rtcp.getIntervalSec()).isEqualTo(INTERVAL);
        assertThat(rtcp.getRtcpXrBlockTypes()).isEqualTo(BLOCK_TYPES);
    }

    @Test
    public void testParcel() {
        RtcpConfig rtcp = new RtcpConfig.Builder()
                .setCanonicalName(NAME)
                .setTransmitPort(PORT)
                .setIntervalSec(INTERVAL)
                .setRtcpXrBlockTypes(BLOCK_TYPES)
                .build();

        Parcel parcel = Parcel.obtain();
        rtcp.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        RtcpConfig parcelConfig = RtcpConfig.CREATOR.createFromParcel(parcel);
        assertThat(rtcp).isEqualTo(parcelConfig);
    }

    @Test
    public void testEqual() {
        RtcpConfig rtcp1 = new RtcpConfig.Builder()
                .setCanonicalName(NAME)
                .setTransmitPort(PORT)
                .setIntervalSec(INTERVAL)
                .setRtcpXrBlockTypes(BLOCK_TYPES)
                .build();

        RtcpConfig rtcp2 = new RtcpConfig.Builder()
                .setCanonicalName(NAME)
                .setTransmitPort(PORT)
                .setIntervalSec(INTERVAL)
                .setRtcpXrBlockTypes(BLOCK_TYPES)
                .build();

        assertThat(rtcp1).isEqualTo(rtcp2);
    }

    @Test
    public void testNotEqual() {
        RtcpConfig rtcp1 = new RtcpConfig.Builder()
                .setCanonicalName(NAME)
                .setTransmitPort(PORT)
                .setIntervalSec(INTERVAL)
                .setRtcpXrBlockTypes(BLOCK_TYPES)
                .build();

        RtcpConfig rtcp2 = new RtcpConfig.Builder()
                .setCanonicalName(NAME)
                .setTransmitPort(3334)
                .setIntervalSec(INTERVAL)
                .setRtcpXrBlockTypes(BLOCK_TYPES)
                .build();

        assertThat(rtcp1).isNotEqualTo(rtcp2);

        RtcpConfig rtcp3 = new RtcpConfig.Builder()
                .setCanonicalName("differs")
                .setTransmitPort(PORT)
                .setIntervalSec(INTERVAL)
                .setRtcpXrBlockTypes(BLOCK_TYPES)
                .build();

        assertThat(rtcp1).isNotEqualTo(rtcp3);

        RtcpConfig rtcp4 = new RtcpConfig.Builder()
                .setCanonicalName(NAME)
                .setTransmitPort(PORT)
                .setIntervalSec(60)
                .setRtcpXrBlockTypes(BLOCK_TYPES)
                .build();

        assertThat(rtcp1).isNotEqualTo(rtcp4);

        RtcpConfig rtcp5 = new RtcpConfig.Builder()
                .setCanonicalName(NAME)
                .setTransmitPort(PORT)
                .setIntervalSec(INTERVAL)
                .setRtcpXrBlockTypes(RtcpConfig.FLAG_RTCPXR_NONE)
                .build();

        assertThat(rtcp1).isNotEqualTo(rtcp5);
    }
}
