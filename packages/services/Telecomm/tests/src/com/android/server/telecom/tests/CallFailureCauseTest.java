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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.server.telecom.stats.CallFailureCause;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CallFailureCauseTest extends TelecomTestCase {
    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {CallFailureCause.NONE, 0, true},
            {CallFailureCause.INVALID_USE, 1, false},
            {CallFailureCause.IN_EMERGENCY_CALL, 2, false},
            {CallFailureCause.CANNOT_HOLD_CALL, 3, false},
            {CallFailureCause.MAX_OUTGOING_CALLS, 4, false},
            {CallFailureCause.MAX_RINGING_CALLS, 5, false},
            {CallFailureCause.MAX_HOLD_CALLS, 6, false},
            {CallFailureCause.MAX_SELF_MANAGED_CALLS, 7, false},
        });
    }
    @Parameter(0) public CallFailureCause e;
    @Parameter(1) public int code;
    @Parameter(2) public boolean isSuccess;

    @Test
    public void testGetCode() {
        assertEquals(code, e.getCode());
    }

    @Test
    public void testIsSuccess() {
        assertEquals(isSuccess, e.isSuccess());
    }
}
