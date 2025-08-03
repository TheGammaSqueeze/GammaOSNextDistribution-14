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

import android.util.StatsEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class AtomsQnsHandoverTimeMillisInfoTest {

    @Mock
    private StatsEvent.Builder mStatsEventBuilder;

    private static final int DEFAULT_TIMER_FOR_HANDOVER_SUCCESS = 5000;

    private AtomsQnsHandoverTimeMillisInfo mInfoEmpty;
    private AtomsQnsHandoverTimeMillisInfo mInfoDefault;
    private AtomsQnsHandoverTimeMillisInfo mInfoCopy;

    /** atom #1 : Time in milliseconds from QNS RAT update to successful HO completion */
    private int mTimeForHoSuccess;

    /** atom #2 : Slot Index */
    private int mSlotIndex;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTimeForHoSuccess = DEFAULT_TIMER_FOR_HANDOVER_SUCCESS;
        mSlotIndex = 0;
        mInfoEmpty = new AtomsQnsHandoverTimeMillisInfo();
        mInfoDefault = new AtomsQnsHandoverTimeMillisInfo(mTimeForHoSuccess, mSlotIndex);
        mInfoCopy = new AtomsQnsHandoverTimeMillisInfo(mInfoDefault);
    }

    @After
    public void tearDown() {
        mInfoEmpty = null;
        mInfoDefault = null;
        mInfoCopy = null;
    }

    @Test
    public void testGetSetTimeForHoSuccess() {
        assertEquals(0, mInfoEmpty.getTimeForHoSuccess());
        assertEquals(DEFAULT_TIMER_FOR_HANDOVER_SUCCESS, mInfoDefault.getTimeForHoSuccess());
        assertEquals(DEFAULT_TIMER_FOR_HANDOVER_SUCCESS, mInfoCopy.getTimeForHoSuccess());

        mInfoEmpty.setTimeForHoSuccess(3000);
        mInfoDefault.setTimeForHoSuccess(4000);
        mInfoCopy.setTimeForHoSuccess(7000);

        assertEquals(3000, mInfoEmpty.getTimeForHoSuccess());
        assertEquals(4000, mInfoDefault.getTimeForHoSuccess());
        assertEquals(7000, mInfoCopy.getTimeForHoSuccess());
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

        assertTrue(strInfoDefault.startsWith("AtomsQnsHandoverTimeMillisInfo"));

        assertEquals(strInfoDefault, strInfoCopy);
        assertNotEquals(strInfoEmpty, strInfoDefault);
    }

    @Test
    public void testEquals() {
        assertEquals(mInfoDefault, mInfoDefault);
        assertNotEquals(mInfoDefault, new Object());
        assertEquals(mInfoDefault, mInfoCopy);
        mInfoDefault.setTimeForHoSuccess(2000);
        mInfoCopy.setTimeForHoSuccess(3000);
        assertNotEquals(mInfoDefault, mInfoCopy);
    }

    @Test
    public void testHashCode() {
        AtomsQnsHandoverTimeMillisInfo a1, a2;

        a1 = new AtomsQnsHandoverTimeMillisInfo(2000, 0);
        a2 = new AtomsQnsHandoverTimeMillisInfo(3000, 0);
        assertNotEquals(a1.hashCode(), a2.hashCode());

        a1 = new AtomsQnsHandoverTimeMillisInfo(mTimeForHoSuccess, mSlotIndex);
        a2 = new AtomsQnsHandoverTimeMillisInfo(mTimeForHoSuccess, mSlotIndex);
        assertEquals(a1.hashCode(), a2.hashCode());
    }

    @Test
    public void testDimension() {
        AtomsQnsHandoverTimeMillisInfo a1, a2;
        a1 = new AtomsQnsHandoverTimeMillisInfo(1000, 0);
        a2 = new AtomsQnsHandoverTimeMillisInfo(2000, 1);
        assertNotEquals(a1.getDimension(), a2.getDimension());
        assertEquals(a1.getDimension(), mInfoDefault.getDimension());
    }

    @Test
    public void testStatsId() {
        final int statsId = 10178; // QnsHandoverTimeMillis
        assertEquals(statsId, mInfoDefault.copy().getStatsId());
    }

    @Test
    public void testStatsEventBuilder() {
        mInfoDefault.build(mStatsEventBuilder);

        verify(mStatsEventBuilder, times(2)).writeInt(anyInt());
    }
}
