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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.os.OutcomeReceiver;
import android.telecom.CallControl;
import android.telecom.CallException;
import android.telecom.PhoneAccountHandle;

import com.android.internal.telecom.ClientTransactionalServiceRepository;
import com.android.internal.telecom.ICallControl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

public class CallControlTest extends TelecomTestCase {
    @Mock private ICallControl mICallControl;
    private static final String CALL_ID_1 = UUID.randomUUID().toString();

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
    public void testGetCallId() {
        CallControl control = new CallControl(CALL_ID_1, mICallControl);
        assertEquals(CALL_ID_1, control.getCallId().toString());
    }
}
