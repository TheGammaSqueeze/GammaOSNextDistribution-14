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

package com.android.internal.net.ipsec.test.ike.ike3gpp;

import static org.junit.Assert.assertEquals;

import android.net.ipsec.test.ike.exceptions.InvalidSyntaxException;

import com.android.internal.util.HexDump;

import org.junit.Test;

public class Ike3gppBackoffTimerUtilsTest {
    private static final byte[] BACKOFF_TIMER_DATA = HexDump.hexStringToByteArray("01AF");
    private static final byte BACKOFF_TIMER = (byte) 0xAF;
    private static final byte[] BACKOFF_TIMER_LONG_DATA = HexDump.hexStringToByteArray("010203");
    private static final byte[] BACKOFF_TIMER_INVALID_LENGTH = HexDump.hexStringToByteArray("02AF");

    @Test
    public void testGetBackoffTimerfromNotifyData() throws Exception {
        byte backoffTimer =
                Ike3gppBackoffTimerUtils.getBackoffTimerfromNotifyData(BACKOFF_TIMER_DATA);

        assertEquals(BACKOFF_TIMER, backoffTimer);
    }

    @Test(expected = InvalidSyntaxException.class)
    public void testGetBackoffTimerfromNotifyDataLongPayload() throws Exception {
        Ike3gppBackoffTimerUtils.getBackoffTimerfromNotifyData(BACKOFF_TIMER_LONG_DATA);
    }

    @Test(expected = InvalidSyntaxException.class)
    public void testGetBackoffTimerfromNotifyDataInvalidLength() throws Exception {
        Ike3gppBackoffTimerUtils.getBackoffTimerfromNotifyData(BACKOFF_TIMER_INVALID_LENGTH);
    }
}
