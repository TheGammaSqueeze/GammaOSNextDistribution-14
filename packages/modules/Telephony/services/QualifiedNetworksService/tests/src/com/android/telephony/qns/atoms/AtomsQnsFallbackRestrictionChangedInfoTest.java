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

import android.util.StatsEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class AtomsQnsFallbackRestrictionChangedInfoTest {

    @Mock
    private StatsEvent.Builder mStatsEventBuilder;

    private static final boolean DEFAULT_RESTRICTION_WLAN_RTP_THRESHOLD_BREACHED = true;
    private static final boolean DEFAULT_RESTRICTION_WWAN_RTP_THRESHOLD_BREACHED = true;
    private static final boolean DEFAULT_RESTRICTION_WWAN_IMS_REGI_FAIL = true;
    private static final boolean DEFAULT_RESTRICTION_WWAN_WIFI_BACKHAUL_PROBLEM = true;
    private static final int DEFAULT_CARRIER_ID = 1; // TMO

    private AtomsQnsFallbackRestrictionChangedInfo mInfoEmpty;
    private AtomsQnsFallbackRestrictionChangedInfo mInfoDefault;
    private AtomsQnsFallbackRestrictionChangedInfo mInfoCopy;

    /** atom #1 : Restriction on WLAN caused by RTP threshold breached */
    private boolean mRestrictionWlanRtpThresholdBreached;

    /** atom #2 : Restriction on WWAN caused by RTP threshold breached */
    private boolean mRestrictionWwanRtpThresholdBreached;

    /** atom #3 : Restriction on WLAN caused by IMS registration fail */
    private boolean mRestrictionWwanImsRegiFail;

    /** atom #4 : Restriction on WLAN caused by Wifi backhaul problem. */
    private boolean mRestrictionWwanWifiBackhaulProblem;

    /** atom #5 : Carrier Id */
    private int mCarrierId;

    /** atom #6 : Slot Index */
    private int mSlotIndex;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mRestrictionWlanRtpThresholdBreached = DEFAULT_RESTRICTION_WLAN_RTP_THRESHOLD_BREACHED;
        mRestrictionWwanRtpThresholdBreached = DEFAULT_RESTRICTION_WWAN_RTP_THRESHOLD_BREACHED;
        mRestrictionWwanImsRegiFail = DEFAULT_RESTRICTION_WWAN_IMS_REGI_FAIL;
        mRestrictionWwanWifiBackhaulProblem = DEFAULT_RESTRICTION_WWAN_WIFI_BACKHAUL_PROBLEM;
        mCarrierId = DEFAULT_CARRIER_ID;
        mSlotIndex = 0;
        mInfoEmpty = new AtomsQnsFallbackRestrictionChangedInfo();
        mInfoDefault =
                new AtomsQnsFallbackRestrictionChangedInfo(
                        mRestrictionWlanRtpThresholdBreached,
                        mRestrictionWwanRtpThresholdBreached,
                        mRestrictionWwanImsRegiFail,
                        mRestrictionWwanWifiBackhaulProblem,
                        mCarrierId,
                        mSlotIndex);
        mInfoCopy = new AtomsQnsFallbackRestrictionChangedInfo(mInfoDefault);
    }

    @After
    public void tearDown() {
        mInfoEmpty = null;
        mInfoDefault = null;
        mInfoCopy = null;
    }

    @Test
    public void testGetSetRestrictionWlanRtpThresholdBreached() {
        assertFalse(mInfoEmpty.getRestrictionOnWlanByRtpThresholdBreached());
        assertTrue(mInfoDefault.getRestrictionOnWlanByRtpThresholdBreached());
        assertTrue(mInfoCopy.getRestrictionOnWlanByRtpThresholdBreached());

        mInfoEmpty.setRestrictionOnWlanByRtpThresholdBreached(true);
        mInfoDefault.setRestrictionOnWlanByRtpThresholdBreached(true);
        mInfoCopy.setRestrictionOnWlanByRtpThresholdBreached(true);

        assertTrue(mInfoEmpty.getRestrictionOnWlanByRtpThresholdBreached());
        assertTrue(mInfoDefault.getRestrictionOnWlanByRtpThresholdBreached());
        assertTrue(mInfoCopy.getRestrictionOnWlanByRtpThresholdBreached());
    }

    @Test
    public void testGetSetRestrictionWwanRtpThresholdBreached() {
        assertFalse(mInfoEmpty.getRestrictionOnWwanByRtpThresholdBreached());
        assertTrue(mInfoDefault.getRestrictionOnWwanByRtpThresholdBreached());
        assertTrue(mInfoCopy.getRestrictionOnWwanByRtpThresholdBreached());

        mInfoEmpty.setRestrictionOnWwanByRtpThresholdBreached(true);
        mInfoDefault.setRestrictionOnWwanByRtpThresholdBreached(true);
        mInfoCopy.setRestrictionOnWwanByRtpThresholdBreached(true);

        assertTrue(mInfoEmpty.getRestrictionOnWwanByRtpThresholdBreached());
        assertTrue(mInfoDefault.getRestrictionOnWwanByRtpThresholdBreached());
        assertTrue(mInfoCopy.getRestrictionOnWwanByRtpThresholdBreached());
    }

    @Test
    public void testGetSetRestrictionWwanImsRegiFail() {
        assertFalse(mInfoEmpty.getRestrictionOnWlanByImsRegistrationFailed());
        assertTrue(mInfoDefault.getRestrictionOnWlanByImsRegistrationFailed());
        assertTrue(mInfoCopy.getRestrictionOnWlanByImsRegistrationFailed());

        mInfoEmpty.setRestrictionOnWlanByImsRegistrationFailed(true);
        mInfoDefault.setRestrictionOnWlanByImsRegistrationFailed(true);
        mInfoCopy.setRestrictionOnWlanByImsRegistrationFailed(true);

        assertTrue(mInfoEmpty.getRestrictionOnWlanByImsRegistrationFailed());
        assertTrue(mInfoDefault.getRestrictionOnWlanByImsRegistrationFailed());
        assertTrue(mInfoCopy.getRestrictionOnWlanByImsRegistrationFailed());
    }

    @Test
    public void testGetSetRestrictionWwanWifiBackhaulProblem() {
        assertFalse(mInfoEmpty.getRestrictionOnWlanByWifiBackhaulProblem());
        assertTrue(mInfoDefault.getRestrictionOnWlanByWifiBackhaulProblem());
        assertTrue(mInfoCopy.getRestrictionOnWlanByWifiBackhaulProblem());

        mInfoEmpty.setRestrictionOnWlanByWifiBackhaulProblem(true);
        mInfoDefault.setRestrictionOnWlanByWifiBackhaulProblem(true);
        mInfoCopy.setRestrictionOnWlanByWifiBackhaulProblem(true);

        assertTrue(mInfoEmpty.getRestrictionOnWlanByWifiBackhaulProblem());
        assertTrue(mInfoDefault.getRestrictionOnWlanByWifiBackhaulProblem());
        assertTrue(mInfoCopy.getRestrictionOnWlanByWifiBackhaulProblem());
    }

    @Test
    public void testGetSetCarrierId() {
        assertEquals(0, mInfoEmpty.getCarrierId());
        assertEquals(DEFAULT_CARRIER_ID, mInfoDefault.getCarrierId());
        assertEquals(DEFAULT_CARRIER_ID, mInfoCopy.getCarrierId());

        mInfoEmpty.setCarrierId(1);
        mInfoDefault.setCarrierId(2);
        mInfoCopy.setCarrierId(3);

        assertEquals(1, mInfoEmpty.getCarrierId());
        assertEquals(2, mInfoDefault.getCarrierId());
        assertEquals(3, mInfoCopy.getCarrierId());
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

        assertTrue(strInfoDefault.startsWith("AtomsQnsFallbackRestrictionChangedInfo"));

        assertEquals(strInfoDefault, strInfoCopy);
        assertNotEquals(strInfoEmpty, strInfoDefault);
    }

    @Test
    public void testEquals() {
        assertEquals(mInfoDefault, mInfoDefault);
        assertNotEquals(mInfoDefault, new Object());
        assertEquals(mInfoDefault, mInfoCopy);
        mInfoDefault.setRestrictionOnWlanByRtpThresholdBreached(false);
        mInfoCopy.setRestrictionOnWlanByRtpThresholdBreached(true);
        assertNotEquals(mInfoDefault, mInfoCopy);
    }

    @Test
    public void testHashCode() {
        AtomsQnsFallbackRestrictionChangedInfo a1, a2;

        a1 =
                new AtomsQnsFallbackRestrictionChangedInfo(
                        true, true, false, false, mCarrierId, mSlotIndex);
        a2 =
                new AtomsQnsFallbackRestrictionChangedInfo(
                        false, false, false, false, mCarrierId, mSlotIndex);
        assertNotEquals(a1.hashCode(), a2.hashCode());

        a1 =
                new AtomsQnsFallbackRestrictionChangedInfo(
                        mRestrictionWlanRtpThresholdBreached,
                        mRestrictionWwanRtpThresholdBreached,
                        mRestrictionWwanImsRegiFail,
                        mRestrictionWwanWifiBackhaulProblem,
                        mCarrierId,
                        mSlotIndex);
        a2 =
                new AtomsQnsFallbackRestrictionChangedInfo(
                        mRestrictionWlanRtpThresholdBreached,
                        mRestrictionWwanRtpThresholdBreached,
                        mRestrictionWwanImsRegiFail,
                        mRestrictionWwanWifiBackhaulProblem,
                        mCarrierId,
                        mSlotIndex);
        assertEquals(a1.hashCode(), a2.hashCode());
    }

    @Test
    public void testStatsId() {
        final int statsId = 636; // QnsFallbackRestrictionChanged
        assertEquals(statsId, mInfoDefault.copy().getStatsId());
    }

    @Test
    public void testStatsEventBuilder() {
        mInfoDefault.build(mStatsEventBuilder);

        verify(mStatsEventBuilder, times(4)).writeBoolean(anyBoolean());
        verify(mStatsEventBuilder, times(2)).writeInt(anyInt());
    }
}
