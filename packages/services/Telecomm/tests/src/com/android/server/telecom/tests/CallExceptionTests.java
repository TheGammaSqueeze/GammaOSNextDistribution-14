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

import static android.telecom.CallException.CODE_CALL_CANNOT_BE_SET_TO_ACTIVE;
import static android.telecom.CallException.CODE_ERROR_UNKNOWN;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.isA;
import static org.junit.Assert.assertEquals;

import android.os.Parcel;
import android.telecom.CallException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CallExceptionTests extends TelecomTestCase {

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
    public void testExceptionWithCode() {
        String message = "test message";
        CallException exception = new CallException(message, CODE_CALL_CANNOT_BE_SET_TO_ACTIVE);
        assertTrue(exception.getMessage().contains(message));
        assertEquals(CODE_CALL_CANNOT_BE_SET_TO_ACTIVE, exception.getCode());
    }

    @Test
    public void testDescribeContents() {
        String message = "test message";
        CallException exception = new CallException(message, CODE_ERROR_UNKNOWN);
        assertEquals(0, exception.describeContents());
    }

    @Test
    public void testWriteToParcel() {
        // GIVEN
        String message = "test message";
        CallException exception = new CallException(message, CODE_ERROR_UNKNOWN);

        // WHEN
        exception.writeToParcel(mParcel, 0);

        // THEN
        verify(mParcel, times(1)).writeString8(isA(String.class));
        verify(mParcel, times(1)).writeInt(isA(Integer.class));
    }
}
