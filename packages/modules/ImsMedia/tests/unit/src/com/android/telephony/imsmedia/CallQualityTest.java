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

package com.android.telephony.imsmedia;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.telephony.CallQuality;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CallQualityTest {
    private static final int DOWNLINKCALLQUALITYLEVEL = 1;
    private static final int UPLINKCALLQUALITYLEVEL = 2;
    private static final int CALLDURATION = 40000;
    private static final int NUMRTPPACKETSTRANSMITTED = 1600;
    private static final int NUMRTPPACKETSRECEIVED = 1700;
    private static final int NUMRTPPACKETSTRANSMITTEDLOST = 400;
    private static final int NUMRTPPACKETSNOTRECEIVED = 42;
    private static final int AVERAGERELATIVEJITTER = 30;
    private static final int MAXRELATIVEJITTER = 40;
    private static final int AVERAGEROUNDTRIPTIME = 100;
    private static final int CODECTYPE = 1;
    private static final boolean RTPINACTIVITYDETECTED = true;
    private static final boolean RXSILENCEDETECTED = false;
    private static final boolean TXSILENCEDETECTED = false;
    private static final int NUMVOICEFRAMES = 100;
    private static final int NUMNODATAFRAMES = 20;
    private static final int NUMDROPPEDRTPPACKETS = 40;
    private static final long MINPLAYOUTDELAYMILLIS = 200;
    private static final long MAXPLAYOUTDELAYMILLIS = 1000;
    private static final int NUMRTPSIDPACKETSRECEIVED = 20;
    private static final int NUMRTPDUPLICATEPACKETS = 50;

    static CallQuality createCallQuality() {
        return new CallQuality.Builder()
                .setDownlinkCallQualityLevel(DOWNLINKCALLQUALITYLEVEL)
                .setUplinkCallQualityLevel(UPLINKCALLQUALITYLEVEL)
                .setCallDurationMillis(CALLDURATION)
                .setNumRtpPacketsTransmitted(NUMRTPPACKETSTRANSMITTED)
                .setNumRtpPacketsReceived(NUMRTPPACKETSRECEIVED)
                .setNumRtpPacketsTransmittedLost(NUMRTPPACKETSTRANSMITTEDLOST)
                .setNumRtpPacketsNotReceived(NUMRTPPACKETSNOTRECEIVED)
                .setAverageRelativeJitter(AVERAGERELATIVEJITTER)
                .setMaxRelativeJitter(MAXRELATIVEJITTER)
                .setAverageRoundTripTimeMillis(AVERAGEROUNDTRIPTIME)
                .setCodecType(CODECTYPE)
                .setRtpInactivityDetected(RTPINACTIVITYDETECTED)
                .setIncomingSilenceDetectedAtCallSetup(RXSILENCEDETECTED)
                .setOutgoingSilenceDetectedAtCallSetup(TXSILENCEDETECTED)
                .setNumVoiceFrames(NUMVOICEFRAMES)
                .setNumNoDataFrames(NUMNODATAFRAMES)
                .setNumDroppedRtpPackets(NUMDROPPEDRTPPACKETS)
                .setMinPlayoutDelayMillis(MINPLAYOUTDELAYMILLIS)
                .setMaxPlayoutDelayMillis(MAXPLAYOUTDELAYMILLIS)
                .setNumRtpSidPacketsReceived(NUMRTPSIDPACKETSRECEIVED)
                .setNumRtpDuplicatePackets(NUMRTPDUPLICATEPACKETS)
                .build();
    }

    static android.hardware.radio.ims.media.CallQuality createHalCallQuality() {
        final android.hardware.radio.ims.media.CallQuality callQuality =
                new android.hardware.radio.ims.media.CallQuality();
        callQuality.downlinkCallQualityLevel = DOWNLINKCALLQUALITYLEVEL;
        callQuality.uplinkCallQualityLevel = UPLINKCALLQUALITYLEVEL;
        callQuality.callDuration = CALLDURATION;
        callQuality.numRtpPacketsTransmitted = NUMRTPPACKETSTRANSMITTED;
        callQuality.numRtpPacketsReceived = NUMRTPPACKETSRECEIVED;
        callQuality.numRtpPacketsTransmittedLost = NUMRTPPACKETSTRANSMITTEDLOST;
        callQuality.numRtpPacketsNotReceived = NUMRTPPACKETSNOTRECEIVED;
        callQuality.averageRelativeJitter = AVERAGERELATIVEJITTER;
        callQuality.maxRelativeJitter = MAXRELATIVEJITTER;
        callQuality.averageRoundTripTime = AVERAGEROUNDTRIPTIME;
        callQuality.codecType = CODECTYPE;
        callQuality.rtpInactivityDetected = RTPINACTIVITYDETECTED;
        callQuality.rxSilenceDetected = RXSILENCEDETECTED;
        callQuality.txSilenceDetected = TXSILENCEDETECTED;
        callQuality.numVoiceFrames = NUMVOICEFRAMES;
        callQuality.numNoDataFrames = NUMNODATAFRAMES;
        callQuality.numDroppedRtpPackets = NUMDROPPEDRTPPACKETS;
        callQuality.minPlayoutDelayMillis = MINPLAYOUTDELAYMILLIS;
        callQuality.maxPlayoutDelayMillis = MAXPLAYOUTDELAYMILLIS;
        callQuality.numRtpSidPacketsReceived = NUMRTPSIDPACKETSRECEIVED;
        callQuality.numRtpDuplicatePackets = NUMRTPDUPLICATEPACKETS;
        return callQuality;
    }

    @Test
    public void testConstructorAndGetters() {
        CallQuality callQuality = createCallQuality();

        assertThat(callQuality.getDownlinkCallQualityLevel()).isEqualTo(DOWNLINKCALLQUALITYLEVEL);
        assertThat(callQuality.getUplinkCallQualityLevel()).isEqualTo(UPLINKCALLQUALITYLEVEL);
        assertThat(callQuality.getCallDuration()).isEqualTo(CALLDURATION);
        assertThat(callQuality.getNumRtpPacketsTransmitted()).isEqualTo(NUMRTPPACKETSTRANSMITTED);
        assertThat(callQuality.getNumRtpPacketsReceived()).isEqualTo(NUMRTPPACKETSRECEIVED);
        assertThat(callQuality.getNumRtpPacketsTransmittedLost())
            .isEqualTo(NUMRTPPACKETSTRANSMITTEDLOST);
        assertThat(callQuality.getNumRtpPacketsNotReceived()).isEqualTo(NUMRTPPACKETSNOTRECEIVED);
        assertThat(callQuality.getAverageRelativeJitter()).isEqualTo(AVERAGERELATIVEJITTER);
        assertThat(callQuality.getMaxRelativeJitter()).isEqualTo(MAXRELATIVEJITTER);
        assertThat(callQuality.getAverageRoundTripTime()).isEqualTo(AVERAGEROUNDTRIPTIME);
        assertThat(callQuality.getCodecType()).isEqualTo(CODECTYPE);
        assertThat(callQuality.isRtpInactivityDetected()).isEqualTo(RTPINACTIVITYDETECTED);
        assertThat(callQuality.isIncomingSilenceDetectedAtCallSetup()).isEqualTo(RXSILENCEDETECTED);
        assertThat(callQuality.isOutgoingSilenceDetectedAtCallSetup()).isEqualTo(TXSILENCEDETECTED);
        assertThat(callQuality.getNumVoiceFrames()).isEqualTo(NUMVOICEFRAMES);
        assertThat(callQuality.getNumNoDataFrames()).isEqualTo(NUMNODATAFRAMES);
        assertThat(callQuality.getNumDroppedRtpPackets()).isEqualTo(NUMDROPPEDRTPPACKETS);
        assertThat(callQuality.getMinPlayoutDelayMillis()).isEqualTo(MINPLAYOUTDELAYMILLIS);
        assertThat(callQuality.getMaxPlayoutDelayMillis()).isEqualTo(MAXPLAYOUTDELAYMILLIS);
        assertThat(callQuality.getNumRtpSidPacketsReceived()).isEqualTo(NUMRTPSIDPACKETSRECEIVED);
        assertThat(callQuality.getNumRtpDuplicatePackets()).isEqualTo(NUMRTPDUPLICATEPACKETS);
    }

    @Test
    public void testParcel() {
        CallQuality callQuality = createCallQuality();

        Parcel parcel = Parcel.obtain();
        callQuality.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        CallQuality parcelConfig = CallQuality.CREATOR.createFromParcel(parcel);
        assertThat(callQuality).isEqualTo(parcelConfig);
    }

    @Test
    public void testEqual() {
        CallQuality callQuality1 = createCallQuality();
        CallQuality callQuality2 = createCallQuality();

        assertThat(callQuality1).isEqualTo(callQuality2);
    }

    @Test
    public void testNotEqual() {
        CallQuality callQuality1 = createCallQuality();
        CallQuality callQuality2 = new CallQuality.Builder()
                .setDownlinkCallQualityLevel(DOWNLINKCALLQUALITYLEVEL)
                .setUplinkCallQualityLevel(UPLINKCALLQUALITYLEVEL)
                .setCallDurationMillis(CALLDURATION)
                .setNumRtpPacketsTransmitted(NUMRTPPACKETSTRANSMITTED)
                .setNumRtpPacketsReceived(NUMRTPPACKETSRECEIVED)
                .setNumRtpPacketsTransmittedLost(NUMRTPPACKETSTRANSMITTEDLOST)
                .setNumRtpPacketsNotReceived((NUMRTPPACKETSNOTRECEIVED + 1))
                .setAverageRelativeJitter(AVERAGERELATIVEJITTER)
                .setMaxRelativeJitter(MAXRELATIVEJITTER)
                .setAverageRoundTripTimeMillis(AVERAGEROUNDTRIPTIME)
                .setCodecType(CODECTYPE)
                .setRtpInactivityDetected(RTPINACTIVITYDETECTED)
                .setIncomingSilenceDetectedAtCallSetup(RXSILENCEDETECTED)
                .setOutgoingSilenceDetectedAtCallSetup(TXSILENCEDETECTED)
                .setNumVoiceFrames(NUMVOICEFRAMES)
                .setNumNoDataFrames(NUMNODATAFRAMES)
                .setNumDroppedRtpPackets(NUMDROPPEDRTPPACKETS)
                .setMinPlayoutDelayMillis(MINPLAYOUTDELAYMILLIS)
                .setMaxPlayoutDelayMillis(MAXPLAYOUTDELAYMILLIS)
                .setNumRtpSidPacketsReceived(NUMRTPSIDPACKETSRECEIVED)
                .setNumRtpDuplicatePackets(NUMRTPDUPLICATEPACKETS)
                .build();
        assertThat(callQuality1).isNotEqualTo(callQuality2);
    }
}
