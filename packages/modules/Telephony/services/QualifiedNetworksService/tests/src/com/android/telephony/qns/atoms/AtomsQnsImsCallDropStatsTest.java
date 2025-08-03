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

package com.android.telephony.qns.atoms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.telephony.qns.QnsProtoEnums;
import android.util.StatsEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class AtomsQnsImsCallDropStatsTest {

    @Mock
    private StatsEvent.Builder mStatsEventBuilder;

    private static final int DEFAULT_CURRENT_TRANSPORT_TYPE = QnsProtoEnums.TRANSPORT_TYPE_WWAN;
    private static final int DEFAULT_RESTRICTIONS = QnsProtoEnums.RESTRICT_TYPE_NONE;
    private static final int DEFAULT_SIGNAL_STRENGTH = -100;
    private static final int DEFAULT_SIGNAL_QUALITY = -10;
    private static final int DEFAULT_SIGNAL_NOISE = -1;
    private static final int DEFAULT_IWLAN_SIGNAL_STRENGTH = -70;
    private static final int DEFAULT_CELLULAR_NETWORK_TYPE = QnsProtoEnums.EUTRAN;

    private AtomsQnsImsCallDropStats mInfoEmpty;
    private AtomsQnsImsCallDropStats mInfoDefault;
    private AtomsQnsImsCallDropStats mInfoCopy;

    /** atom #1 : Transport type in where IMS call drop occurred. */
    private int mTransportTypeCallDropped;

    /** atom #2 : RTP threshold breached event occurred. */
    private boolean mRtpThresholdBreached;

    /** atom #3 : Bit mask of restrictions on another transport type */
    private int mRestrictionsOnOtherTransportType;

    /** atom #4 : Cellular network signal strength {e.g. SSRSRP in NR, RSRP in LTE} */
    private int mSignalStrength;

    /** atom #5 : Cellular network signal quality {e.g. SSRSRQ in NR, RSRQ in LTE} */
    private int mSignalQuality;

    /** atom #6 : Cellular network signal noise ratio {e.g. SSSINR in NR, RSSNR in LTE} */
    private int mSignalNoise;

    /** atom #7 : Iwlan network signal strength (Wi-Fi RSSI) */
    private int mIwlanSignalStrength;

    /** atom #8 : Slot Index */
    private int mSlotIndex;

    /** atom #9 : cellular access network type. */
    private int mCellularNetworkType;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTransportTypeCallDropped = DEFAULT_CURRENT_TRANSPORT_TYPE;
        mRtpThresholdBreached = true;
        mRestrictionsOnOtherTransportType = DEFAULT_RESTRICTIONS;
        mSignalStrength = DEFAULT_SIGNAL_STRENGTH;
        mSignalQuality = DEFAULT_SIGNAL_QUALITY;
        mSignalNoise = DEFAULT_SIGNAL_NOISE;
        mIwlanSignalStrength = DEFAULT_IWLAN_SIGNAL_STRENGTH;
        mSlotIndex = 0;
        mCellularNetworkType = DEFAULT_CELLULAR_NETWORK_TYPE;

        mInfoEmpty = new AtomsQnsImsCallDropStats();
        mInfoDefault =
                new AtomsQnsImsCallDropStats(
                        mTransportTypeCallDropped,
                        mRtpThresholdBreached,
                        mRestrictionsOnOtherTransportType,
                        mSignalStrength,
                        mSignalQuality,
                        mSignalNoise,
                        mIwlanSignalStrength,
                        mSlotIndex,
                        mCellularNetworkType);
        mInfoCopy = new AtomsQnsImsCallDropStats(mInfoDefault);
    }

    @After
    public void tearDown() {
        mInfoEmpty = null;
        mInfoDefault = null;
        mInfoCopy = null;
    }

    @Test
    public void testGetSetTransportTypeCallDropped() {
        assertEquals(0, mInfoEmpty.getTransportTypeCallDropped());
        assertEquals(DEFAULT_CURRENT_TRANSPORT_TYPE, mInfoDefault.getTransportTypeCallDropped());
        assertEquals(DEFAULT_CURRENT_TRANSPORT_TYPE, mInfoCopy.getTransportTypeCallDropped());

        mInfoEmpty.setTransportTypeCallDropped(QnsProtoEnums.TRANSPORT_TYPE_WWAN);
        mInfoDefault.setTransportTypeCallDropped(QnsProtoEnums.TRANSPORT_TYPE_WLAN);
        mInfoCopy.setTransportTypeCallDropped(QnsProtoEnums.TRANSPORT_TYPE_INVALID);

        assertEquals(QnsProtoEnums.TRANSPORT_TYPE_WWAN, mInfoEmpty.getTransportTypeCallDropped());
        assertEquals(QnsProtoEnums.TRANSPORT_TYPE_WLAN, mInfoDefault.getTransportTypeCallDropped());
        assertEquals(QnsProtoEnums.TRANSPORT_TYPE_INVALID, mInfoCopy.getTransportTypeCallDropped());
    }

    @Test
    public void testGetSetRtpThresholdBreached() {
        assertFalse(mInfoEmpty.getRtpThresholdBreached());
        assertTrue(mInfoDefault.getRtpThresholdBreached());
        assertTrue(mInfoCopy.getRtpThresholdBreached());

        mInfoEmpty.setRtpThresholdBreached(true);
        mInfoDefault.setRtpThresholdBreached(true);
        mInfoCopy.setRtpThresholdBreached(false);

        assertTrue(mInfoEmpty.getRtpThresholdBreached());
        assertTrue(mInfoDefault.getRtpThresholdBreached());
        assertFalse(mInfoCopy.getRtpThresholdBreached());
    }

    @Test
    public void testGetSetRestrictionsOnOtherTransportType() {
        assertEquals(0, mInfoEmpty.getRestrictionsOnOtherTransportType());
        assertEquals(DEFAULT_RESTRICTIONS, mInfoDefault.getRestrictionsOnOtherTransportType());
        assertEquals(DEFAULT_RESTRICTIONS, mInfoCopy.getRestrictionsOnOtherTransportType());

        mInfoEmpty.setRestrictionsOnOtherTransportType(QnsProtoEnums.RESTRICT_TYPE_GUARDING);
        mInfoDefault.setRestrictionsOnOtherTransportType(QnsProtoEnums.RESTRICT_TYPE_THROTTLING);
        mInfoCopy.setRestrictionsOnOtherTransportType(QnsProtoEnums.RESTRICT_TYPE_NONE);

        assertEquals(
                QnsProtoEnums.RESTRICT_TYPE_GUARDING,
                mInfoEmpty.getRestrictionsOnOtherTransportType());
        assertEquals(
                QnsProtoEnums.RESTRICT_TYPE_THROTTLING,
                mInfoDefault.getRestrictionsOnOtherTransportType());
        assertEquals(
                QnsProtoEnums.RESTRICT_TYPE_NONE, mInfoCopy.getRestrictionsOnOtherTransportType());
    }

    @Test
    public void testGetSetSignalStrength() {
        assertEquals(0, mInfoEmpty.getSignalStrength());
        assertEquals(DEFAULT_SIGNAL_STRENGTH, mInfoDefault.getSignalStrength());
        assertEquals(DEFAULT_SIGNAL_STRENGTH, mInfoCopy.getSignalStrength());

        mInfoEmpty.setSignalStrength(DEFAULT_SIGNAL_STRENGTH);
        mInfoDefault.setSignalStrength(-120);
        mInfoCopy.setSignalStrength(-110);

        assertEquals(DEFAULT_SIGNAL_STRENGTH, mInfoEmpty.getSignalStrength());
        assertEquals(-120, mInfoDefault.getSignalStrength());
        assertEquals(-110, mInfoCopy.getSignalStrength());
    }

    @Test
    public void testGetSetSignalQuality() {
        assertEquals(0, mInfoEmpty.getSignalQuality());
        assertEquals(DEFAULT_SIGNAL_QUALITY, mInfoDefault.getSignalQuality());
        assertEquals(DEFAULT_SIGNAL_QUALITY, mInfoCopy.getSignalQuality());

        mInfoEmpty.setSignalQuality(DEFAULT_SIGNAL_QUALITY);
        mInfoDefault.setSignalQuality(-10);
        mInfoCopy.setSignalQuality(-5);

        assertEquals(DEFAULT_SIGNAL_QUALITY, mInfoEmpty.getSignalQuality());
        assertEquals(-10, mInfoDefault.getSignalQuality());
        assertEquals(-5, mInfoCopy.getSignalQuality());
    }

    @Test
    public void testGetSetSignalNoise() {
        assertEquals(0, mInfoEmpty.getSignalNoise());
        assertEquals(DEFAULT_SIGNAL_NOISE, mInfoDefault.getSignalNoise());
        assertEquals(DEFAULT_SIGNAL_NOISE, mInfoCopy.getSignalNoise());

        mInfoEmpty.setSignalNoise(DEFAULT_SIGNAL_NOISE);
        mInfoDefault.setSignalNoise(1);
        mInfoCopy.setSignalNoise(-1);

        assertEquals(DEFAULT_SIGNAL_NOISE, mInfoEmpty.getSignalNoise());
        assertEquals(1, mInfoDefault.getSignalNoise());
        assertEquals(-1, mInfoCopy.getSignalNoise());
    }

    @Test
    public void testGetSetIwlanSignalStrength() {
        assertEquals(0, mInfoEmpty.getIwlanSignalStrength());
        assertEquals(DEFAULT_IWLAN_SIGNAL_STRENGTH, mInfoDefault.getIwlanSignalStrength());
        assertEquals(DEFAULT_IWLAN_SIGNAL_STRENGTH, mInfoCopy.getIwlanSignalStrength());

        mInfoEmpty.setIwlanSignalStrength(DEFAULT_IWLAN_SIGNAL_STRENGTH);
        mInfoDefault.setIwlanSignalStrength(-80);
        mInfoCopy.setIwlanSignalStrength(-50);

        assertEquals(DEFAULT_IWLAN_SIGNAL_STRENGTH, mInfoEmpty.getIwlanSignalStrength());
        assertEquals(-80, mInfoDefault.getIwlanSignalStrength());
        assertEquals(-50, mInfoCopy.getIwlanSignalStrength());
    }

    @Test
    public void testGetSetSlotIndex() {
        assertEquals(0, mInfoEmpty.getSlotIndex());
        assertEquals(0, mInfoDefault.getSlotIndex());
        assertEquals(0, mInfoCopy.getSlotIndex());

        mInfoEmpty.setSlotIndex(1);
        mInfoDefault.setSlotIndex(2);
        mInfoCopy.setSlotIndex(3);

        assertEquals(1, mInfoEmpty.getSlotIndex());
        assertEquals(2, mInfoDefault.getSlotIndex());
        assertEquals(3, mInfoCopy.getSlotIndex());
    }

    @Test
    public void testGetSetCellularNetworkType() {
        assertEquals(0, mInfoEmpty.getCellularNetworkType());
        assertEquals(DEFAULT_CELLULAR_NETWORK_TYPE, mInfoDefault.getCellularNetworkType());
        assertEquals(DEFAULT_CELLULAR_NETWORK_TYPE, mInfoCopy.getCellularNetworkType());

        mInfoEmpty.setCellularNetworkType(QnsProtoEnums.NGRAN);
        mInfoDefault.setCellularNetworkType(QnsProtoEnums.UTRAN);
        mInfoCopy.setCellularNetworkType(QnsProtoEnums.GERAN);

        assertEquals(QnsProtoEnums.NGRAN, mInfoEmpty.getCellularNetworkType());
        assertEquals(QnsProtoEnums.UTRAN, mInfoDefault.getCellularNetworkType());
        assertEquals(QnsProtoEnums.GERAN, mInfoCopy.getCellularNetworkType());
    }

    @Test
    public void testToString() {
        String strInfoEmpty = mInfoEmpty.toString();
        String strInfoDefault = mInfoDefault.toString();
        String strInfoCopy = mInfoCopy.toString();

        assertNotNull(strInfoEmpty);
        assertNotNull(strInfoDefault);
        assertNotNull(strInfoCopy);

        assertTrue(strInfoDefault.startsWith("AtomsQnsImsCallDropStats"));

        assertEquals(strInfoDefault, strInfoCopy);
        assertNotEquals(strInfoEmpty, strInfoDefault);
    }

    @Test
    public void testEquals() {
        assertEquals(mInfoDefault, mInfoDefault);
        assertNotEquals(mInfoDefault, new Object());
        assertEquals(mInfoDefault, mInfoCopy);
        mInfoDefault.setSignalQuality(3);
        mInfoCopy.setSignalQuality(1);
        assertNotEquals(mInfoDefault, mInfoCopy);
    }

    @Test
    public void testHashCode() {
        AtomsQnsImsCallDropStats a1, a2;

        a1 =
                new AtomsQnsImsCallDropStats(
                        mInfoDefault.getTransportTypeCallDropped(),
                        mInfoDefault.getRtpThresholdBreached(),
                        mInfoDefault.getRestrictionsOnOtherTransportType(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0);
        a2 =
                new AtomsQnsImsCallDropStats(
                        mInfoDefault.getTransportTypeCallDropped(),
                        mInfoDefault.getRtpThresholdBreached(),
                        mInfoDefault.getRestrictionsOnOtherTransportType(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        1);
        assertNotEquals(a1.hashCode(), a2.hashCode());

        a1 =
                new AtomsQnsImsCallDropStats(
                        mTransportTypeCallDropped,
                        mRtpThresholdBreached,
                        mRestrictionsOnOtherTransportType,
                        mSignalStrength,
                        mSignalQuality,
                        mSignalNoise,
                        mIwlanSignalStrength,
                        mSlotIndex,
                        mCellularNetworkType);
        a2 =
                new AtomsQnsImsCallDropStats(
                        mTransportTypeCallDropped,
                        mRtpThresholdBreached,
                        mRestrictionsOnOtherTransportType,
                        mSignalStrength,
                        mSignalQuality,
                        mSignalNoise,
                        mIwlanSignalStrength,
                        mSlotIndex,
                        mCellularNetworkType);
        assertEquals(a1.hashCode(), a2.hashCode());
    }

    @Test
    public void testStatsId() {
        final int statsId = 635; // QnsImsCallDropStats
        assertEquals(statsId, mInfoDefault.copy().getStatsId());
    }

    @Test
    public void testStatsEventBuilder() {
        mInfoDefault.build(mStatsEventBuilder);

        verify(mStatsEventBuilder, times(1)).writeBoolean(anyBoolean());
        verify(mStatsEventBuilder, times(8)).writeInt(anyInt());
    }
}
