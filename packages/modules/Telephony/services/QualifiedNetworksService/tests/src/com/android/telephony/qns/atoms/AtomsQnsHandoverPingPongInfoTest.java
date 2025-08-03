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
public class AtomsQnsHandoverPingPongInfoTest {

    @Mock
    private StatsEvent.Builder mStatsEventBuilder;

    private static final int DEFAULT_COUNT_HANDOVER_PING_PONG = 2;
    private static final int DEFAULT_CARRIER_ID = 1; // TMO

    private AtomsQnsHandoverPingPongInfo mInfoEmpty;
    private AtomsQnsHandoverPingPongInfo mInfoDefault;
    private AtomsQnsHandoverPingPongInfo mInfoCopy;

    /** atom #1 : Count of handover ping-pong */
    private int mCountHandoverPingPong;

    /** atom #2 : Carrier Id */
    private int mCarrierId;

    /** atom #3 : Slot Index */
    private int mSlotIndex;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mCountHandoverPingPong = DEFAULT_COUNT_HANDOVER_PING_PONG;
        mCarrierId = DEFAULT_CARRIER_ID;
        mSlotIndex = 0;
        mInfoEmpty = new AtomsQnsHandoverPingPongInfo();
        mInfoDefault =
                new AtomsQnsHandoverPingPongInfo(mCountHandoverPingPong, mCarrierId, mSlotIndex);
        mInfoCopy = new AtomsQnsHandoverPingPongInfo(mInfoDefault);
    }

    @After
    public void tearDown() {
        mInfoEmpty = null;
        mInfoDefault = null;
        mInfoCopy = null;
    }

    @Test
    public void testGetSetCountHandoverPingPong() {
        assertEquals(0, mInfoEmpty.getCountHandoverPingPong());
        assertEquals(DEFAULT_COUNT_HANDOVER_PING_PONG, mInfoDefault.getCountHandoverPingPong());
        assertEquals(DEFAULT_COUNT_HANDOVER_PING_PONG, mInfoCopy.getCountHandoverPingPong());

        mInfoEmpty.setCountHandoverPingPong(3);
        mInfoDefault.setCountHandoverPingPong(4);
        mInfoCopy.setCountHandoverPingPong(5);

        assertEquals(3, mInfoEmpty.getCountHandoverPingPong());
        assertEquals(4, mInfoDefault.getCountHandoverPingPong());
        assertEquals(5, mInfoCopy.getCountHandoverPingPong());
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

        assertTrue(strInfoDefault.startsWith("AtomsQnsHandoverPingPongInfo"));

        assertEquals(strInfoDefault, strInfoCopy);
        assertNotEquals(strInfoEmpty, strInfoDefault);
    }

    @Test
    public void testEquals() {
        assertEquals(mInfoDefault, mInfoDefault);
        assertNotEquals(mInfoDefault, new Object());
        assertEquals(mInfoDefault, mInfoCopy);
        mInfoDefault.setCountHandoverPingPong(2);
        mInfoCopy.setCountHandoverPingPong(3);
        assertNotEquals(mInfoDefault, mInfoCopy);
    }

    @Test
    public void testHashCode() {
        AtomsQnsHandoverPingPongInfo a1, a2;

        a1 = new AtomsQnsHandoverPingPongInfo(2, 1, 0);
        a2 = new AtomsQnsHandoverPingPongInfo(3, 1, 0);
        assertNotEquals(a1.hashCode(), a2.hashCode());

        a1 = new AtomsQnsHandoverPingPongInfo(mCountHandoverPingPong, mCarrierId, mSlotIndex);
        a2 = new AtomsQnsHandoverPingPongInfo(mCountHandoverPingPong, mCarrierId, mSlotIndex);
        assertEquals(a1.hashCode(), a2.hashCode());
    }

    @Test
    public void testDimension() {
        AtomsQnsHandoverPingPongInfo a1, a2;

        a1 = new AtomsQnsHandoverPingPongInfo(mCountHandoverPingPong, 3, mSlotIndex);
        a2 = new AtomsQnsHandoverPingPongInfo(mCountHandoverPingPong, 1, mSlotIndex);
        assertNotEquals(a1.getDimension(), a2.getDimension());

        a1 = new AtomsQnsHandoverPingPongInfo(mCountHandoverPingPong, mCarrierId, 0);
        a2 = new AtomsQnsHandoverPingPongInfo(mCountHandoverPingPong, mCarrierId, 1);
        assertNotEquals(a1.getDimension(), a2.getDimension());

        a1 = new AtomsQnsHandoverPingPongInfo(1, mCarrierId, mSlotIndex);
        a2 = new AtomsQnsHandoverPingPongInfo(2, mCarrierId, mSlotIndex);
        assertEquals(a1.getDimension(), a2.getDimension());
    }

    @Test
    public void testStatsId() {
        final int statsId = 10179; // QnsHandoverPingpong
        assertEquals(statsId, mInfoDefault.copy().getStatsId());
    }

    @Test
    public void testStatsEventBuilder() {
        mInfoDefault.build(mStatsEventBuilder);

        verify(mStatsEventBuilder, times(3)).writeInt(anyInt());
    }
}
