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

package com.android.server.telecom.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.net.Uri;
import android.os.Parcel;
import android.telecom.CallAttributes;
import android.telecom.PhoneAccountHandle;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CallAttributesTests extends TelecomTestCase {

    private static final PhoneAccountHandle mHandle = new PhoneAccountHandle(
            new ComponentName("foo", "bar"), "1");
    private static final String TEST_NAME = "Larry Page";
    private static final Uri TEST_URI = Uri.fromParts("tel", "abc", "123");
    @Mock private Parcel mParcel;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testRequiredAttributes() {
        CallAttributes callAttributes = new CallAttributes.Builder(mHandle,
                CallAttributes.DIRECTION_OUTGOING, TEST_NAME, TEST_URI).build();

        assertEquals(CallAttributes.DIRECTION_OUTGOING, callAttributes.getDirection());
        assertEquals(mHandle, callAttributes.getPhoneAccountHandle());
    }

    @Test
    public void testInvalidDirectionAttributes() {
        assertThrows(IllegalArgumentException.class, () ->
                new CallAttributes.Builder(mHandle, -1, TEST_NAME, TEST_URI).build()
        );
    }

    @Test
    public void testInvalidCallType() {
        assertThrows(IllegalArgumentException.class, () ->
                new CallAttributes.Builder(mHandle, CallAttributes.DIRECTION_OUTGOING,
                        TEST_NAME, TEST_URI).setCallType(-1).build()
        );
    }

    @Test
    public void testOptionalAttributes() {
        CallAttributes callAttributes = new CallAttributes.Builder(mHandle,
                CallAttributes.DIRECTION_OUTGOING, TEST_NAME, TEST_URI)
                .setCallCapabilities(CallAttributes.SUPPORTS_SET_INACTIVE)
                .setCallType(CallAttributes.AUDIO_CALL)
                .build();

        assertEquals(CallAttributes.DIRECTION_OUTGOING, callAttributes.getDirection());
        assertEquals(mHandle, callAttributes.getPhoneAccountHandle());
        assertEquals(CallAttributes.SUPPORTS_SET_INACTIVE, callAttributes.getCallCapabilities());
        assertEquals(CallAttributes.AUDIO_CALL, callAttributes.getCallType());
        assertEquals(TEST_URI, callAttributes.getAddress());
        assertEquals(TEST_NAME, callAttributes.getDisplayName());
    }

    @Test
    public void testDescribeContents() {
        CallAttributes callAttributes = new CallAttributes.Builder(mHandle,
                CallAttributes.DIRECTION_OUTGOING, TEST_NAME, TEST_URI).build();

        assertEquals(0, callAttributes.describeContents());
    }

    @Test
    public void testWriteToParcel() {
        // GIVEN
        CallAttributes callAttributes = new CallAttributes.Builder(mHandle,
                CallAttributes.DIRECTION_OUTGOING, TEST_NAME, TEST_URI)
                .setCallCapabilities(CallAttributes.SUPPORTS_SET_INACTIVE)
                .setCallType(CallAttributes.AUDIO_CALL)
                .build();

        // WHEN
        callAttributes.writeToParcel(mParcel, 0);

        // THEN
        verify(mParcel, times(1))
                .writeParcelable(isA(PhoneAccountHandle.class), isA(Integer.class));
        verify(mParcel, times(1)).writeCharSequence(isA(CharSequence.class));
        verify(mParcel, times(1))
                .writeParcelable(isA(Uri.class), isA(Integer.class));
        verify(mParcel, times(3)).writeInt(isA(Integer.class));
    }
}
