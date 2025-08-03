/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cellbroadcastservice.tests;

import static org.mockito.Mockito.doReturn;

import android.telephony.SmsCbLocation;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.cellbroadcastservice.GsmCellBroadcastHandler.SmsCbConcatInfo;
import com.android.cellbroadcastservice.SmsCbHeader;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class SmsCbContactInfoTest extends CellBroadcastServiceTestBase {

    @Mock
    private SmsCbHeader mSmsCbHeader;
    private SmsCbLocation mSmsCbLocation;
    private SmsCbConcatInfo mSmsCbConcatInfo;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        doReturn(123).when(mSmsCbHeader).getSerialNumber();
        mSmsCbLocation = new SmsCbLocation("310999", 1234, 5678);
        // construct a SmsCbContactInfo for test
        mSmsCbConcatInfo = new SmsCbConcatInfo(mSmsCbHeader, mSmsCbLocation);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testHasCode() throws Exception {
        // construct another SmsCbContactInfo for comparison
        SmsCbConcatInfo testInfo = new SmsCbConcatInfo(
                Mockito.mock(SmsCbHeader.class), mSmsCbLocation);
        assertNotSame(testInfo.hashCode(), mSmsCbConcatInfo.hashCode());
    }

    @Test
    public void testEquals() throws Exception {
        // construct another SmsCbContactInfo for comparison
        SmsCbConcatInfo testInfo = new SmsCbConcatInfo(
                Mockito.mock(SmsCbHeader.class), mSmsCbLocation);
        assertFalse(testInfo.equals(mSmsCbConcatInfo));
    }

    @Test
    public void testMatchesLocation() throws Exception {
        assertTrue(mSmsCbConcatInfo.matchesLocation("310999", 1234, 5678));
        assertFalse(mSmsCbConcatInfo.matchesLocation("310000", 1234, 5678));
        assertFalse(mSmsCbConcatInfo.matchesLocation("310999", -1, -1));
    }
}

