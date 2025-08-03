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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.ComponentName;
import android.net.Uri;
import android.telecom.CallerInfo;
import android.telecom.PhoneAccountHandle;

import androidx.test.filters.SmallTest;

import com.android.server.telecom.MissedCallNotifier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MissedCallNotifierTest extends TelecomTestCase {
    private static final ComponentName COMPONENT_NAME =
            new ComponentName("com.anything", "com.whatever");
    private static final Uri TEL_CALL_HANDLE = Uri.parse("tel:+11915552620");
    private static final long CALL_TIMESTAMP = 1;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @SmallTest
    @Test
    public void testCallInfoFactory() {
        final CallerInfo callerInfo = new CallerInfo();
        final String phoneNumber = "1111";
        final String name = "name";
        callerInfo.setPhoneNumber(phoneNumber);
        callerInfo.setName(name);
        final PhoneAccountHandle phoneAccountHandle = new PhoneAccountHandle(COMPONENT_NAME, "id");

        MissedCallNotifier.CallInfo callInfo = new MissedCallNotifier.CallInfoFactory()
                .makeCallInfo(callerInfo, phoneAccountHandle, TEL_CALL_HANDLE, CALL_TIMESTAMP);

        assertEquals(callerInfo, callInfo.getCallerInfo());
        assertEquals(phoneAccountHandle, callInfo.getPhoneAccountHandle());
        assertEquals(TEL_CALL_HANDLE, callInfo.getHandle());
        assertEquals(TEL_CALL_HANDLE.getSchemeSpecificPart(),
                callInfo.getHandleSchemeSpecificPart());
        assertEquals(CALL_TIMESTAMP, callInfo.getCreationTimeMillis());
        assertEquals(phoneNumber, callInfo.getPhoneNumber());
        assertEquals(name, callInfo.getName());
    }

    @SmallTest
    @Test
    public void testCallInfoFactoryNullParam() {
        MissedCallNotifier.CallInfo callInfo = new MissedCallNotifier.CallInfoFactory()
                .makeCallInfo(null, null, null, CALL_TIMESTAMP);

        assertNull(callInfo.getCallerInfo());
        assertNull(callInfo.getPhoneAccountHandle());
        assertNull(callInfo.getHandle());
        assertNull(callInfo.getHandleSchemeSpecificPart());
        assertEquals(CALL_TIMESTAMP, callInfo.getCreationTimeMillis());
        assertNull(callInfo.getPhoneNumber());
        assertNull(callInfo.getName());
    }
}
