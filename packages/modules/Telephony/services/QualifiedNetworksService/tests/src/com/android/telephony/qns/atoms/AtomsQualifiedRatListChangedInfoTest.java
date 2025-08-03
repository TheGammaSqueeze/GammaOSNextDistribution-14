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
public class AtomsQualifiedRatListChangedInfoTest {

    @Mock
    private StatsEvent.Builder mStatsEventBuilder;

    private static final int DEFAULT_NET_CAPABILITY = QnsProtoEnums.NET_CAPABILITY_IMS;
    private static final int DEFAULT_FIRST_QUALIFIED_RAT = QnsProtoEnums.EUTRAN;
    private static final int DEFAULT_SECOND_QUALIFIED_RAT = QnsProtoEnums.IWLAN;
    private static final int DEFAULT_CURRENT_TRANSPORT_TYPE = QnsProtoEnums.TRANSPORT_TYPE_WLAN;
    private static final boolean DEFAULT_WFC_ENABLED = true;
    private static final int DEFAULT_WFC_MODE = QnsProtoEnums.CELLULAR_PREFERRED;
    private static final int DEFAULT_CELLULAR_NETWORK_TYPE = QnsProtoEnums.EUTRAN;
    private static final int DEFAULT_IWLAN_NETWORK_TYPE = QnsProtoEnums.IWLAN_NETWORK_TYPE_WIFI;
    private static final int DEFAULT_RESTRICTIONS_ON_WWAN = QnsProtoEnums.RESTRICT_TYPE_NONE;
    private static final int DEFAULT_RESTRICTIONS_ON_WLAN = QnsProtoEnums.RESTRICT_TYPE_NONE;
    private static final int DEFAULT_SIGNAL_STRENGTH = -100;
    private static final int DEFAULT_SIGNAL_QUALITY = -10;
    private static final int DEFAULT_SIGNAL_NOISE = -1;
    private static final int DEFAULT_IWLAN_SIGNAL_STRENGTH = -70;
    private static final int DEFAULT_UPDATE_REASON = 0;

    private AtomsQualifiedRatListChangedInfo mInfoEmpty;
    private AtomsQualifiedRatListChangedInfo mInfoDefault;
    private AtomsQualifiedRatListChangedInfo mInfoCopy;

    /** atom #1 : NetCapability of this Qualified RAT update */
    private int mNetCapability;
    /** atom #2 : The most preferred qualified RAT */
    private int mFirstQualifiedRat;
    /** atom #3 : Second preferred qualified RAT */
    private int mSecondQualifiedRat;
    /** atom #4 : Current actual transport type of Data session for this NetCapability */
    private int mCurrentTransportType;
    /** atom #5 : Indicates whether WFC is enabled */
    private boolean mWfcEnabled;
    /** atom #6 : Indicates the user's WFC mode */
    private int mWfcMode;
    /** atom #7 : Current Cellular AccessNetwork Type */
    private int mCellularNetworkType;
    /** atom #8 : Available IWLAN AccessNetwork */
    private int mIwlanNetworkType;
    /** atom #9 : Bit mask of restrictions on WWAN */
    private int mRestrictionsOnWwan;
    /** atom #10 : Bit mask of restrictions on WLAN */
    private int mRestrictionsOnWlan;
    /**
     * atom #11 : Cellular network signal strength {e.g. SSRSRP in NR, RSRP in LTE, RSCP in UMTS}
     */
    private int mSignalStrength;
    /** atom #12 : Cellular network signal quality {e.g. SSRSRQ in NR, RSRQ in LTE} */
    private int mSignalQuality;
    /** atom #13 : Cellular network signal noise ratio {e.g. SSSINR in NR, RSSNR in LTE} */
    private int mSignalNoise;
    /** atom #14 : Iwlan network signal strength (Wi-Fi RSSI) */
    private int mIwlanSignalStrength;
    /** atom #15 : Reason for preferred RAT update */
    private int mUpdateReason;
    /** atom #16: IMS Call Type */
    private int mImsCallType;
    /** atom #17 : IMS Call Quality */
    private int mImsCallQuality;
    /** atom #18 : Slot Index */
    private int mSlotIndex;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mNetCapability = DEFAULT_NET_CAPABILITY;
        mFirstQualifiedRat = DEFAULT_FIRST_QUALIFIED_RAT;
        mSecondQualifiedRat = DEFAULT_SECOND_QUALIFIED_RAT;
        mCurrentTransportType = DEFAULT_CURRENT_TRANSPORT_TYPE;
        mWfcEnabled = DEFAULT_WFC_ENABLED;
        mWfcMode = DEFAULT_WFC_MODE;
        mCellularNetworkType = DEFAULT_CELLULAR_NETWORK_TYPE;
        mIwlanNetworkType = DEFAULT_IWLAN_NETWORK_TYPE;
        mRestrictionsOnWwan = DEFAULT_RESTRICTIONS_ON_WWAN;
        mRestrictionsOnWlan = DEFAULT_RESTRICTIONS_ON_WLAN;
        mSignalStrength = DEFAULT_SIGNAL_STRENGTH;
        mSignalQuality = DEFAULT_SIGNAL_QUALITY;
        mSignalNoise = DEFAULT_SIGNAL_NOISE;
        mIwlanSignalStrength = DEFAULT_IWLAN_SIGNAL_STRENGTH;
        mUpdateReason = DEFAULT_UPDATE_REASON;
        mImsCallType = 1;
        mImsCallQuality = 0;
        mSlotIndex = 0;
        mInfoEmpty = new AtomsQualifiedRatListChangedInfo();
        mInfoDefault =
                new AtomsQualifiedRatListChangedInfo(
                        mNetCapability,
                        mFirstQualifiedRat,
                        mSecondQualifiedRat,
                        mCurrentTransportType,
                        mWfcEnabled,
                        mWfcMode,
                        mCellularNetworkType,
                        mIwlanNetworkType,
                        mRestrictionsOnWwan,
                        mRestrictionsOnWlan,
                        mSignalStrength,
                        mSignalQuality,
                        mSignalNoise,
                        mIwlanSignalStrength,
                        mUpdateReason,
                        mImsCallType,
                        mImsCallQuality,
                        mSlotIndex);
        mInfoCopy = new AtomsQualifiedRatListChangedInfo(mInfoDefault);
    }

    @After
    public void tearDown() {
        mInfoEmpty = null;
        mInfoDefault = null;
        mInfoCopy = null;
    }

    @Test
    public void testGetSetNetCapability() {
        assertEquals(0, mInfoEmpty.getNetCapability());
        assertEquals(DEFAULT_NET_CAPABILITY, mInfoDefault.getNetCapability());
        assertEquals(DEFAULT_NET_CAPABILITY, mInfoCopy.getNetCapability());

        mInfoEmpty.setNetCapability(QnsProtoEnums.NET_CAPABILITY_IMS);
        mInfoDefault.setNetCapability(QnsProtoEnums.NET_CAPABILITY_EIMS);
        mInfoCopy.setNetCapability(QnsProtoEnums.NET_CAPABILITY_XCAP);

        assertEquals(QnsProtoEnums.NET_CAPABILITY_IMS, mInfoEmpty.getNetCapability());
        assertEquals(QnsProtoEnums.NET_CAPABILITY_EIMS, mInfoDefault.getNetCapability());
        assertEquals(QnsProtoEnums.NET_CAPABILITY_XCAP, mInfoCopy.getNetCapability());
    }

    @Test
    public void testGetSetFirstQualifiedRat() {
        assertEquals(0, mInfoEmpty.getFirstQualifiedRat());
        assertEquals(DEFAULT_FIRST_QUALIFIED_RAT, mInfoDefault.getFirstQualifiedRat());
        assertEquals(DEFAULT_FIRST_QUALIFIED_RAT, mInfoCopy.getFirstQualifiedRat());

        mInfoEmpty.setFirstQualifiedRat(QnsProtoEnums.IWLAN);
        mInfoDefault.setFirstQualifiedRat(QnsProtoEnums.EUTRAN);
        mInfoCopy.setFirstQualifiedRat(QnsProtoEnums.NGRAN);

        assertEquals(QnsProtoEnums.IWLAN, mInfoEmpty.getFirstQualifiedRat());
        assertEquals(QnsProtoEnums.EUTRAN, mInfoDefault.getFirstQualifiedRat());
        assertEquals(QnsProtoEnums.NGRAN, mInfoCopy.getFirstQualifiedRat());
    }

    @Test
    public void testGetSetSecondQualifiedRat() {
        assertEquals(0, mInfoEmpty.getSecondQualifiedRat());
        assertEquals(DEFAULT_SECOND_QUALIFIED_RAT, mInfoDefault.getSecondQualifiedRat());
        assertEquals(DEFAULT_SECOND_QUALIFIED_RAT, mInfoCopy.getSecondQualifiedRat());

        mInfoEmpty.setSecondQualifiedRat(QnsProtoEnums.IWLAN);
        mInfoDefault.setSecondQualifiedRat(QnsProtoEnums.EUTRAN);
        mInfoCopy.setSecondQualifiedRat(QnsProtoEnums.NGRAN);

        assertEquals(QnsProtoEnums.IWLAN, mInfoEmpty.getSecondQualifiedRat());
        assertEquals(QnsProtoEnums.EUTRAN, mInfoDefault.getSecondQualifiedRat());
        assertEquals(QnsProtoEnums.NGRAN, mInfoCopy.getSecondQualifiedRat());
    }

    @Test
    public void testGetSetCurrentTransportType() {
        assertEquals(0, mInfoEmpty.getCurrentTransportType());
        assertEquals(DEFAULT_CURRENT_TRANSPORT_TYPE, mInfoDefault.getCurrentTransportType());
        assertEquals(DEFAULT_CURRENT_TRANSPORT_TYPE, mInfoCopy.getCurrentTransportType());

        mInfoEmpty.setCurrentTransportType(QnsProtoEnums.TRANSPORT_TYPE_INVALID);
        mInfoDefault.setCurrentTransportType(QnsProtoEnums.TRANSPORT_TYPE_WWAN);
        mInfoCopy.setCurrentTransportType(QnsProtoEnums.TRANSPORT_TYPE_WLAN);

        assertEquals(QnsProtoEnums.TRANSPORT_TYPE_INVALID, mInfoEmpty.getCurrentTransportType());
        assertEquals(QnsProtoEnums.TRANSPORT_TYPE_WWAN, mInfoDefault.getCurrentTransportType());
        assertEquals(QnsProtoEnums.TRANSPORT_TYPE_WLAN, mInfoCopy.getCurrentTransportType());
    }

    @Test
    public void testGetSetWfcEnabled() {
        assertFalse(mInfoEmpty.getWfcEnabled());
        assertTrue(mInfoDefault.getWfcEnabled());
        assertTrue(mInfoCopy.getWfcEnabled());

        mInfoEmpty.setWfcEnabled(true);
        mInfoDefault.setWfcEnabled(true);
        mInfoCopy.setWfcEnabled(true);

        assertTrue(mInfoEmpty.getWfcEnabled());
        assertTrue(mInfoDefault.getWfcEnabled());
        assertTrue(mInfoCopy.getWfcEnabled());
    }

    @Test
    public void testGetSetWfcMode() {
        assertEquals(0, mInfoEmpty.getWfcMode());
        assertEquals(DEFAULT_WFC_MODE, mInfoDefault.getWfcMode());
        assertEquals(DEFAULT_WFC_MODE, mInfoCopy.getWfcMode());

        mInfoEmpty.setWfcMode(QnsProtoEnums.CELLULAR_PREFERRED);
        mInfoDefault.setWfcMode(QnsProtoEnums.WIFI_ONLY);
        mInfoCopy.setWfcMode(QnsProtoEnums.WIFI_PREFERRED);

        assertEquals(QnsProtoEnums.CELLULAR_PREFERRED, mInfoEmpty.getWfcMode());
        assertEquals(QnsProtoEnums.WIFI_ONLY, mInfoDefault.getWfcMode());
        assertEquals(QnsProtoEnums.WIFI_PREFERRED, mInfoCopy.getWfcMode());
    }

    @Test
    public void testGetSetCellularNetworkType() {
        assertEquals(0, mInfoEmpty.getCellularNetworkType());
        assertEquals(DEFAULT_CELLULAR_NETWORK_TYPE, mInfoDefault.getCellularNetworkType());
        assertEquals(DEFAULT_CELLULAR_NETWORK_TYPE, mInfoCopy.getCellularNetworkType());

        mInfoEmpty.setCellularNetworkType(QnsProtoEnums.EUTRAN);
        mInfoDefault.setCellularNetworkType(QnsProtoEnums.NGRAN);
        mInfoCopy.setCellularNetworkType(QnsProtoEnums.UTRAN);

        assertEquals(QnsProtoEnums.EUTRAN, mInfoEmpty.getCellularNetworkType());
        assertEquals(QnsProtoEnums.NGRAN, mInfoDefault.getCellularNetworkType());
        assertEquals(QnsProtoEnums.UTRAN, mInfoCopy.getCellularNetworkType());
    }

    @Test
    public void testGetSetIwlanNetworkType() {
        assertEquals(0, mInfoEmpty.getIwlanNetworkType());
        assertEquals(DEFAULT_IWLAN_NETWORK_TYPE, mInfoDefault.getIwlanNetworkType());
        assertEquals(DEFAULT_IWLAN_NETWORK_TYPE, mInfoCopy.getIwlanNetworkType());

        mInfoEmpty.setIwlanNetworkType(QnsProtoEnums.EUTRAN);
        mInfoDefault.setIwlanNetworkType(QnsProtoEnums.NGRAN);
        mInfoCopy.setIwlanNetworkType(QnsProtoEnums.UTRAN);

        assertEquals(QnsProtoEnums.EUTRAN, mInfoEmpty.getIwlanNetworkType());
        assertEquals(QnsProtoEnums.NGRAN, mInfoDefault.getIwlanNetworkType());
        assertEquals(QnsProtoEnums.UTRAN, mInfoCopy.getIwlanNetworkType());
    }

    @Test
    public void testGetSetRestrictionsOnWwan() {
        assertEquals(0, mInfoEmpty.getRestrictionsOnWwan());
        assertEquals(DEFAULT_RESTRICTIONS_ON_WWAN, mInfoDefault.getRestrictionsOnWwan());
        assertEquals(DEFAULT_RESTRICTIONS_ON_WWAN, mInfoCopy.getRestrictionsOnWwan());

        mInfoEmpty.setRestrictionsOnWwan(QnsProtoEnums.RESTRICT_TYPE_GUARDING);
        mInfoDefault.setRestrictionsOnWwan(QnsProtoEnums.RESTRICT_TYPE_THROTTLING);
        mInfoCopy.setRestrictionsOnWwan(QnsProtoEnums.RESTRICT_TYPE_NONE);

        assertEquals(QnsProtoEnums.RESTRICT_TYPE_GUARDING, mInfoEmpty.getRestrictionsOnWwan());
        assertEquals(QnsProtoEnums.RESTRICT_TYPE_THROTTLING, mInfoDefault.getRestrictionsOnWwan());
        assertEquals(QnsProtoEnums.RESTRICT_TYPE_NONE, mInfoCopy.getRestrictionsOnWwan());
    }

    @Test
    public void testGetSetRestrictionsOnWlan() {
        assertEquals(0, mInfoEmpty.getRestrictionsOnWlan());
        assertEquals(DEFAULT_RESTRICTIONS_ON_WWAN, mInfoDefault.getRestrictionsOnWlan());
        assertEquals(DEFAULT_RESTRICTIONS_ON_WWAN, mInfoCopy.getRestrictionsOnWlan());

        mInfoEmpty.setRestrictionsOnWlan(QnsProtoEnums.RESTRICT_TYPE_GUARDING);
        mInfoDefault.setRestrictionsOnWlan(QnsProtoEnums.RESTRICT_TYPE_THROTTLING);
        mInfoCopy.setRestrictionsOnWlan(QnsProtoEnums.RESTRICT_TYPE_NONE);

        assertEquals(QnsProtoEnums.RESTRICT_TYPE_GUARDING, mInfoEmpty.getRestrictionsOnWlan());
        assertEquals(QnsProtoEnums.RESTRICT_TYPE_THROTTLING, mInfoDefault.getRestrictionsOnWlan());
        assertEquals(QnsProtoEnums.RESTRICT_TYPE_NONE, mInfoCopy.getRestrictionsOnWlan());
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
    public void testGetSetUpdateReason() {
        assertEquals(0, mInfoEmpty.getUpdateReason());
        assertEquals(DEFAULT_UPDATE_REASON, mInfoDefault.getUpdateReason());
        assertEquals(DEFAULT_UPDATE_REASON, mInfoCopy.getUpdateReason());

        mInfoEmpty.setUpdateReason(0);
        mInfoDefault.setUpdateReason(0);
        mInfoCopy.setUpdateReason(0);

        assertEquals(0, mInfoEmpty.getUpdateReason());
        assertEquals(0, mInfoDefault.getUpdateReason());
        assertEquals(0, mInfoCopy.getUpdateReason());
    }

    @Test
    public void testGetSetImsCallType() {
        assertEquals(0, mInfoEmpty.getImsCallType());
        assertEquals(1, mInfoDefault.getImsCallType());
        assertEquals(1, mInfoCopy.getImsCallType());

        mInfoEmpty.setImsCallType(1);
        mInfoDefault.setImsCallType(2);
        mInfoCopy.setImsCallType(3);

        assertEquals(1, mInfoEmpty.getImsCallType());
        assertEquals(2, mInfoDefault.getImsCallType());
        assertEquals(3, mInfoCopy.getImsCallType());
    }

    @Test
    public void testGetSetImsCallQuality() {
        assertEquals(0, mInfoEmpty.getImsCallQuality());
        assertEquals(0, mInfoDefault.getImsCallQuality());
        assertEquals(0, mInfoCopy.getImsCallQuality());

        mInfoEmpty.setImsCallQuality(1);
        mInfoDefault.setImsCallQuality(2);
        mInfoCopy.setImsCallQuality(3);

        assertEquals(1, mInfoEmpty.getImsCallQuality());
        assertEquals(2, mInfoDefault.getImsCallQuality());
        assertEquals(3, mInfoCopy.getImsCallQuality());
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
    public void testToString() {
        String strInfoEmpty = mInfoEmpty.toString();
        String strInfoDefault = mInfoDefault.toString();
        String strInfoCopy = mInfoCopy.toString();

        assertNotNull(strInfoEmpty);
        assertNotNull(strInfoDefault);
        assertNotNull(strInfoCopy);

        assertTrue(strInfoDefault.startsWith("AtomsQualifiedRatListChangedInfo"));

        assertEquals(strInfoDefault, strInfoCopy);
        assertNotEquals(strInfoEmpty, strInfoDefault);
    }

    @Test
    public void testEquals() {
        assertEquals(mInfoDefault, mInfoDefault);
        assertNotEquals(mInfoDefault, new Object());
        assertEquals(mInfoDefault, mInfoCopy);
        mInfoDefault.setWfcEnabled(true);
        mInfoCopy.setWfcEnabled(false);
        assertNotEquals(mInfoDefault, mInfoCopy);
    }

    @Test
    public void testHashCode() {
        AtomsQualifiedRatListChangedInfo a1, a2;

        a1 =
                new AtomsQualifiedRatListChangedInfo(
                        mNetCapability,
                        mFirstQualifiedRat,
                        mSecondQualifiedRat,
                        mCurrentTransportType,
                        mWfcEnabled,
                        mWfcMode,
                        mCellularNetworkType,
                        mIwlanNetworkType,
                        mRestrictionsOnWwan,
                        mRestrictionsOnWlan,
                        mSignalStrength,
                        mSignalQuality,
                        mSignalNoise,
                        mIwlanSignalStrength,
                        mUpdateReason,
                        mImsCallType,
                        mImsCallQuality,
                        mSlotIndex);
        a2 =
                new AtomsQualifiedRatListChangedInfo(
                        mNetCapability,
                        mSecondQualifiedRat,
                        mFirstQualifiedRat,
                        mCurrentTransportType,
                        mWfcEnabled,
                        mWfcMode,
                        mCellularNetworkType,
                        mIwlanNetworkType,
                        mRestrictionsOnWwan,
                        mRestrictionsOnWlan,
                        mSignalStrength,
                        mSignalQuality,
                        mSignalNoise,
                        mIwlanSignalStrength,
                        mUpdateReason,
                        mImsCallType,
                        mImsCallQuality,
                        mSlotIndex);
        assertNotEquals(a1.hashCode(), a2.hashCode());
        a2 =
                new AtomsQualifiedRatListChangedInfo(
                        mNetCapability,
                        mFirstQualifiedRat,
                        mSecondQualifiedRat,
                        mCurrentTransportType,
                        mWfcEnabled,
                        mWfcMode,
                        mCellularNetworkType,
                        mIwlanNetworkType,
                        mRestrictionsOnWwan,
                        mRestrictionsOnWlan,
                        mSignalStrength,
                        mSignalQuality,
                        mSignalNoise,
                        mIwlanSignalStrength,
                        mUpdateReason,
                        mImsCallType,
                        mImsCallQuality,
                        mSlotIndex);
        assertEquals(a1.hashCode(), a2.hashCode());
    }

    @Test
    public void testStatsId() {
        final int statsId = 634; // QualifiedRatListChanged
        assertEquals(statsId, mInfoDefault.copy().getStatsId());
    }

    @Test
    public void testStatsEventBuilder() {
        mInfoDefault.build(mStatsEventBuilder);

        verify(mStatsEventBuilder, times(1)).writeBoolean(anyBoolean());
        verify(mStatsEventBuilder, times(17)).writeInt(anyInt());
    }
}
