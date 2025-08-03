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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
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
public class AtomsQnsRatPreferenceMismatchInfoTest {

    @Mock
    private StatsEvent.Builder mStatsEventBuilder;

    private static final int DEFAULT_NET_CAPABILITY = QnsProtoEnums.NET_CAPABILITY_IMS;
    private static final int DEFAULT_HANDOVER_FAIL_COUNT = 1;
    private static final int DEFAULT_DURATION_OF_MISMATCH = 13500;
    private static final int DEFAULT_CARRIER_ID = 1; // TMO

    private AtomsQnsRatPreferenceMismatchInfo mInfoEmpty;
    private AtomsQnsRatPreferenceMismatchInfo mInfoDefault;
    private AtomsQnsRatPreferenceMismatchInfo mInfoCopy;

    /** atom #1 : Net capability of this information. */
    private int mNetCapability;

    /** atom #2 : Count of handover failed. */
    private int mHandoverFailCount;

    /** atom #3 : Duration of this mismatch. */
    private int mDurationOfMismatch;

    /** atom #4 : Carrier ID */
    private int mCarrierId;

    /** atom #5 : Slot Index */
    private int mSlotIndex;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mNetCapability = DEFAULT_NET_CAPABILITY;
        mHandoverFailCount = DEFAULT_HANDOVER_FAIL_COUNT;
        mDurationOfMismatch = DEFAULT_DURATION_OF_MISMATCH;
        mCarrierId = DEFAULT_CARRIER_ID;
        mSlotIndex = 0;
        mInfoEmpty = new AtomsQnsRatPreferenceMismatchInfo();
        mInfoDefault =
                new AtomsQnsRatPreferenceMismatchInfo(
                        mNetCapability,
                        mHandoverFailCount,
                        mDurationOfMismatch,
                        mCarrierId,
                        mSlotIndex);
        mInfoCopy = new AtomsQnsRatPreferenceMismatchInfo(mInfoDefault);
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

        mInfoEmpty.setNetCapability(QnsProtoEnums.NET_CAPABILITY_MMS);
        mInfoDefault.setNetCapability(QnsProtoEnums.NET_CAPABILITY_CBS);
        mInfoCopy.setNetCapability(QnsProtoEnums.NET_CAPABILITY_XCAP);

        assertEquals(QnsProtoEnums.NET_CAPABILITY_MMS, mInfoEmpty.getNetCapability());
        assertEquals(QnsProtoEnums.NET_CAPABILITY_CBS, mInfoDefault.getNetCapability());
        assertEquals(QnsProtoEnums.NET_CAPABILITY_XCAP, mInfoCopy.getNetCapability());
    }

    @Test
    public void testGetSetHandoverFailCount() {
        assertEquals(0, mInfoEmpty.getHandoverFailCount());
        assertEquals(DEFAULT_HANDOVER_FAIL_COUNT, mInfoDefault.getHandoverFailCount());
        assertEquals(DEFAULT_HANDOVER_FAIL_COUNT, mInfoCopy.getHandoverFailCount());

        mInfoEmpty.setHandoverFailCount(DEFAULT_HANDOVER_FAIL_COUNT);
        mInfoDefault.setHandoverFailCount(4);
        mInfoCopy.setHandoverFailCount(0);

        assertEquals(DEFAULT_HANDOVER_FAIL_COUNT, mInfoEmpty.getHandoverFailCount());
        assertEquals(4, mInfoDefault.getHandoverFailCount());
        assertEquals(0, mInfoCopy.getHandoverFailCount());
    }

    @Test
    public void testGetSetDurationOfMismatch() {
        assertEquals(0, mInfoEmpty.getDurationOfMismatch());
        assertEquals(DEFAULT_DURATION_OF_MISMATCH, mInfoDefault.getDurationOfMismatch());
        assertEquals(DEFAULT_DURATION_OF_MISMATCH, mInfoCopy.getDurationOfMismatch());

        mInfoEmpty.setDurationOfMismatch(DEFAULT_DURATION_OF_MISMATCH);
        mInfoDefault.setDurationOfMismatch(4000);
        mInfoCopy.setDurationOfMismatch(0);

        assertEquals(DEFAULT_DURATION_OF_MISMATCH, mInfoEmpty.getDurationOfMismatch());
        assertEquals(4000, mInfoDefault.getDurationOfMismatch());
        assertEquals(0, mInfoCopy.getDurationOfMismatch());
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

        assertTrue(strInfoDefault.startsWith("AtomsQnsRatPreferenceMismatchInfo"));

        assertEquals(strInfoDefault, strInfoCopy);
        assertNotEquals(strInfoEmpty, strInfoDefault);
    }

    @Test
    public void testEquals() {
        assertEquals(mInfoDefault, mInfoDefault);
        assertNotEquals(mInfoDefault, new Object());
        assertEquals(mInfoDefault, mInfoCopy);
        mInfoDefault.setHandoverFailCount(0);
        mInfoCopy.setHandoverFailCount(1);
        assertNotEquals(mInfoDefault, mInfoCopy);
    }

    @Test
    public void testHashCode() {
        AtomsQnsRatPreferenceMismatchInfo a1, a2;

        a1 =
                new AtomsQnsRatPreferenceMismatchInfo(
                        QnsProtoEnums.NET_CAPABILITY_IMS, 0, 0, mCarrierId, mSlotIndex);
        a2 =
                new AtomsQnsRatPreferenceMismatchInfo(
                        QnsProtoEnums.NET_CAPABILITY_MMS, 0, 0, mCarrierId, mSlotIndex);
        assertNotEquals(a1.hashCode(), a2.hashCode());

        a1 =
                new AtomsQnsRatPreferenceMismatchInfo(
                        mNetCapability,
                        mHandoverFailCount,
                        mDurationOfMismatch,
                        mCarrierId,
                        mSlotIndex);
        a2 =
                new AtomsQnsRatPreferenceMismatchInfo(
                        mNetCapability,
                        mHandoverFailCount,
                        mDurationOfMismatch,
                        mCarrierId,
                        mSlotIndex);
        assertEquals(a1.hashCode(), a2.hashCode());
    }

    @Test
    public void testDimension() {
        AtomsQnsRatPreferenceMismatchInfo a1, a2;

        a1 =
                new AtomsQnsRatPreferenceMismatchInfo(
                        mNetCapability, mHandoverFailCount, mDurationOfMismatch, 1, mSlotIndex);
        a2 =
                new AtomsQnsRatPreferenceMismatchInfo(
                        mNetCapability, mHandoverFailCount, mDurationOfMismatch, 3, mSlotIndex);
        assertNotEquals(a1.getDimension(), a2.getDimension());

        a1 =
                new AtomsQnsRatPreferenceMismatchInfo(
                        mNetCapability, mHandoverFailCount, mDurationOfMismatch, mCarrierId, 0);
        a2 =
                new AtomsQnsRatPreferenceMismatchInfo(
                        mNetCapability, mHandoverFailCount, mDurationOfMismatch, mCarrierId, 1);
        assertNotEquals(a1.getDimension(), a2.getDimension());

        a1 = new AtomsQnsRatPreferenceMismatchInfo(9, 0, 100, mCarrierId, mSlotIndex);
        a2 = new AtomsQnsRatPreferenceMismatchInfo(9, 1, 200, mCarrierId, mSlotIndex);
        assertEquals(a1.getDimension(), a2.getDimension());
    }

    @Test
    public void testStatsId() {
        final int statsId = 10177; // QnsRatPreferenceMismatchInfo
        assertEquals(statsId, mInfoDefault.copy().getStatsId());
    }

    @Test
    public void testStatsEventBuilder() {
        mInfoDefault.build(mStatsEventBuilder);

        verify(mStatsEventBuilder, times(5)).writeInt(anyInt());
    }
}
