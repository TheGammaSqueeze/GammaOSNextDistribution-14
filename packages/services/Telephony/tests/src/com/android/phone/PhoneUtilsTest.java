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

package com.android.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.os.UserHandle;
import android.telecom.PhoneAccountHandle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.TelephonyTestBase;
import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class PhoneUtilsTest extends TelephonyTestBase {
    @Mock
    private SubscriptionManager mMockSubscriptionManager;
    @Mock
    private SubscriptionInfo mMockSubscriptionInfo;
    @Mock
    private GsmCdmaPhone mMockPhone;

    private final int mPhoneAccountHandleIdInteger = 123;
    private final String mPhoneAccountHandleIdString = "123";
    private static final ComponentName PSTN_CONNECTION_SERVICE_COMPONENT = new ComponentName(
            "com.android.phone", "com.android.services.telephony.TelephonyConnectionService");
    private PhoneAccountHandle mPhoneAccountHandleTest = new PhoneAccountHandle(
            PSTN_CONNECTION_SERVICE_COMPONENT, mPhoneAccountHandleIdString);

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockSubscriptionManager.getActiveSubscriptionInfo(
                eq(mPhoneAccountHandleIdInteger))).thenReturn(mMockSubscriptionInfo);
        when(mMockPhone.getSubId()).thenReturn(mPhoneAccountHandleIdInteger);
        Phone[] mPhones = new Phone[] {mMockPhone};
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
    }

    @Test
    public void testIsPhoneAccountActive() throws Exception {
        assertTrue(PhoneUtils.isPhoneAccountActive(
                mMockSubscriptionManager, mPhoneAccountHandleTest));
    }

    @Test
    public void testGetPhoneForPhoneAccountHandle() throws Exception {
        assertEquals(mMockPhone, PhoneUtils.getPhoneForPhoneAccountHandle(
                mPhoneAccountHandleTest));
    }

    @Test
    public void testMakePstnPhoneAccountHandleWithPrefix() throws Exception {
        PhoneAccountHandle phoneAccountHandleTest = new PhoneAccountHandle(
                PSTN_CONNECTION_SERVICE_COMPONENT, mPhoneAccountHandleIdString);
        assertEquals(phoneAccountHandleTest, PhoneUtils.makePstnPhoneAccountHandleWithId(
                mPhoneAccountHandleIdString, null));
    }

    @Test
    public void testMakePstnPhoneAccountHandleWithPrefixForAnotherUser() throws Exception {
        UserHandle userHandle = new UserHandle(10);
        PhoneAccountHandle phoneAccountHandleTest = new PhoneAccountHandle(
                PSTN_CONNECTION_SERVICE_COMPONENT, mPhoneAccountHandleIdString, userHandle);
        assertEquals(phoneAccountHandleTest, PhoneUtils.makePstnPhoneAccountHandleWithId(
                mPhoneAccountHandleIdString, userHandle));
    }
}
