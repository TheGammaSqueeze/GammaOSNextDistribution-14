/**
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

package com.android.telephony.imsmedia.tests;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;

import android.os.Parcel;
import android.telephony.imsmedia.RtpContextParams;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RtpContextParamsTest {
    private static final long SSRC = 4198873603L;
    private static final long TIMESTAMP = 3277537485L;
    private static final int SEQUENCE_NUMBER = 4390;
    private static final long INVALID_SSRC = 4294967296L;
    private static final int INVALID_SEQUENCE_NUMBER = 65536;
    private static final int INVALID_NUMBER = -1;

    @Test
    public void testConstructorAndGetters() {
        RtpContextParams rtpContextParams = createRtpContextParams();

        assertThat(rtpContextParams.getSsrc()).isEqualTo(SSRC);
        assertThat(rtpContextParams.getTimestamp()).isEqualTo(TIMESTAMP);
        assertThat(rtpContextParams.getSequenceNumber()).isEqualTo(SEQUENCE_NUMBER);
    }

    @Test
    public void testParcel() {
        RtpContextParams rtpContextParams = createRtpContextParams();

        Parcel parcel = Parcel.obtain();
        rtpContextParams.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        RtpContextParams parcelParams = RtpContextParams.CREATOR.createFromParcel(parcel);
        assertThat(rtpContextParams).isEqualTo(parcelParams);
    }

    @Test
    public void testEqual() {
        RtpContextParams rtpContextParams1 = createRtpContextParams();
        RtpContextParams rtpContextParams2 = createRtpContextParams();

        assertThat(rtpContextParams1).isEqualTo(rtpContextParams2);
    }

    @Test
    public void testNotEqual() {
        RtpContextParams rtpContextParams1 = createRtpContextParams();

        RtpContextParams rtpContextParams2 = new RtpContextParams.Builder()
                .setSsrc(198873603)
                .setTimestamp(TIMESTAMP)
                .setSequenceNumber(SEQUENCE_NUMBER)
                .build();

        assertThat(rtpContextParams1).isNotEqualTo(rtpContextParams2);

        RtpContextParams rtpContextParams3 = new RtpContextParams.Builder()
                .setSsrc(SSRC)
                .setTimestamp(327753748)
                .setSequenceNumber(SEQUENCE_NUMBER)
                .build();

        assertThat(rtpContextParams1).isNotEqualTo(rtpContextParams3);

        RtpContextParams rtpContextParams4 = new RtpContextParams.Builder()
                .setSsrc(SSRC)
                .setTimestamp(TIMESTAMP)
                .setSequenceNumber(0)
                .build();

        assertThat(rtpContextParams1).isNotEqualTo(rtpContextParams4);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSsrc() {
        RtpContextParams rtpContextParams = new RtpContextParams.Builder()
                .setSsrc(INVALID_SSRC)
                .setTimestamp(TIMESTAMP)
                .setSequenceNumber(0)
                .build();
        assertEquals(rtpContextParams.getSsrc(), INVALID_SSRC);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeSsrc() {
        RtpContextParams rtpContextParams = new RtpContextParams.Builder()
                .setSsrc(INVALID_NUMBER)
                .setTimestamp(TIMESTAMP)
                .setSequenceNumber(0)
                .build();
        assertEquals(rtpContextParams.getSsrc(), INVALID_NUMBER);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidTimestamp() {
        RtpContextParams rtpContextParams = new RtpContextParams.Builder()
                .setSsrc(SSRC)
                .setTimestamp(Long.MAX_VALUE)
                .setSequenceNumber(0)
                .build();
        assertEquals(rtpContextParams.getTimestamp(), Long.MAX_VALUE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeTimestamp() {
        RtpContextParams rtpContextParams = new RtpContextParams.Builder()
                .setSsrc(SSRC)
                .setTimestamp(INVALID_NUMBER)
                .setSequenceNumber(0)
                .build();
        assertEquals(rtpContextParams.getTimestamp(), INVALID_NUMBER);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSequenceNumber() {
        RtpContextParams rtpContextParams = new RtpContextParams.Builder()
                .setSsrc(SSRC)
                .setTimestamp(TIMESTAMP)
                .setSequenceNumber(INVALID_SEQUENCE_NUMBER)
                .build();
        assertEquals(rtpContextParams.getSequenceNumber(), INVALID_SEQUENCE_NUMBER);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeSequenceNumber() {
        RtpContextParams rtpContextParams = new RtpContextParams.Builder()
                .setSsrc(SSRC)
                .setTimestamp(TIMESTAMP)
                .setSequenceNumber(INVALID_NUMBER)
                .build();
        assertEquals(rtpContextParams.getSequenceNumber(), INVALID_NUMBER);
    }

    static RtpContextParams createRtpContextParams() {
        return new RtpContextParams.Builder()
                .setSsrc(SSRC)
                .setTimestamp(TIMESTAMP)
                .setSequenceNumber(SEQUENCE_NUMBER)
                .build();
    }
}
