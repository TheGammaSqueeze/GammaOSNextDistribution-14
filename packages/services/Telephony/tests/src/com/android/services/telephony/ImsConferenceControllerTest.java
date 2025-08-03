/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.services.telephony;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.os.Looper;
import android.telecom.PhoneAccountHandle;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests the functionality in ImsConferenceController.java
 */

public class ImsConferenceControllerTest {

    @Mock
    private TelephonyConnectionServiceProxy mMockTelephonyConnectionServiceProxy;

    private TelecomAccountRegistry mTelecomAccountRegistry;

    @Mock
    private TelecomAccountRegistry mMockTelecomAccountRegistry;

    private static final ComponentName TEST_COMPONENT_NAME = new ComponentName(
            "com.android.phone.tests", ImsConferenceControllerTest.class.getName());
    private static final String TEST_ACCOUNT_ID1 = "id1";
    private static final String TEST_ACCOUNT_ID2 = "id2";
    private static final PhoneAccountHandle PHONE_ACCOUNT_HANDLE_1 = new PhoneAccountHandle(
            TEST_COMPONENT_NAME, TEST_ACCOUNT_ID1);
    private static final PhoneAccountHandle PHONE_ACCOUNT_HANDLE_2 = new PhoneAccountHandle(
            TEST_COMPONENT_NAME, TEST_ACCOUNT_ID2);
    private TestTelephonyConnection mTestTelephonyConnectionA;
    private TestTelephonyConnection mTestTelephonyConnectionB;

    private ImsConferenceController mControllerTest;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        mTelecomAccountRegistry = TelecomAccountRegistry.getInstance(null);
        mTestTelephonyConnectionA = new TestTelephonyConnection();
        mTestTelephonyConnectionB = new TestTelephonyConnection();

        mTestTelephonyConnectionA.setPhoneAccountHandle(PHONE_ACCOUNT_HANDLE_1);
        mTestTelephonyConnectionB.setPhoneAccountHandle(PHONE_ACCOUNT_HANDLE_1);

        mControllerTest = new ImsConferenceController(mTelecomAccountRegistry,
                mMockTelephonyConnectionServiceProxy, () -> false);
    }

    /**
     * Behavior: add telephony connections B and A to conference controller,
     *           set status for connections, remove one call
     * Assumption: after performing the behaviors, the status of Connection A is STATE_ACTIVE;
     *             the status of Connection B is STATE_HOLDING
     * Expected: Connection A and Connection B are conferenceable with each other;
     *           Connection B is not conferenceable with Connection A after A is removed;
     *           addConference for ImsConference is not called
     */
    @Test
    @SmallTest
    public void testConferenceable() {
        mControllerTest.add(mTestTelephonyConnectionB);
        mControllerTest.add(mTestTelephonyConnectionA);

        mTestTelephonyConnectionA.setActive();
        mTestTelephonyConnectionB.setTelephonyConnectionOnHold();

        assertTrue(mTestTelephonyConnectionA.getConferenceables()
                .contains(mTestTelephonyConnectionB));
        assertTrue(mTestTelephonyConnectionB.getConferenceables()
                .contains(mTestTelephonyConnectionA));

        // verify addConference method is never called
        verify(mMockTelephonyConnectionServiceProxy, never())
                .addConference(any(ImsConference.class));

        // call A removed
        mControllerTest.remove(mTestTelephonyConnectionA);
        assertFalse(mTestTelephonyConnectionB.getConferenceables()
                .contains(mTestTelephonyConnectionA));
    }

    /**
     * Behavior: add telephony connections A and B to conference controller;
     * Assumption: Connection A and B have different PhoneAccountHandles, belong to different subs;
     * Expected: Connection A and Connection B are not conferenceable with each other;
     */
    @Test
    @SmallTest
    public void testCallsOnDifferentSubsNotConferenceable() {
        mTestTelephonyConnectionB.setPhoneAccountHandle(PHONE_ACCOUNT_HANDLE_2);
        mControllerTest.add(mTestTelephonyConnectionA);
        mControllerTest.add(mTestTelephonyConnectionB);

        mTestTelephonyConnectionA.setActive();
        mTestTelephonyConnectionB.setTelephonyConnectionOnHold();

        assertFalse(mTestTelephonyConnectionA.getConferenceables()
                .contains(mTestTelephonyConnectionB));
        assertFalse(mTestTelephonyConnectionB.getConferenceables()
                .contains(mTestTelephonyConnectionA));
    }

    /**
     * Behavior: add telephony connection B and A to conference controller,
     *           set status for merged connections
     * Assumption: after performing the behaviors, the status of Connection A is STATE_ACTIVE;
     *             the status of Connection B is STATE_HOLDING;
     *             getPhoneType() in the original connection of the telephony connection
     *             is PhoneConstants.PHONE_TYPE_IMS
     * Expected: addConference for ImsConference is called twice
     */
    @Test
    @SmallTest
    public void testMergeMultiPartyCalls() {
        mTestTelephonyConnectionA.setIsImsConnection(true);
        mTestTelephonyConnectionB.setIsImsConnection(true);
        when(mTestTelephonyConnectionA.mImsPhoneConnection.isMultiparty()).thenReturn(true);
        when(mTestTelephonyConnectionB.mImsPhoneConnection.isMultiparty()).thenReturn(true);

        mControllerTest.add(mTestTelephonyConnectionB);
        mControllerTest.add(mTestTelephonyConnectionA);

        mTestTelephonyConnectionA.setActive();
        mTestTelephonyConnectionB.setTelephonyConnectionOnHold();

        verify(mMockTelephonyConnectionServiceProxy, times(2))
                .addConference(any(ImsConference.class));
    }
}
