/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.net.ipsec.ike.ike3gpp;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public class Ike3gppBackoffTimerTest {
    private static final byte BACKOFF_TIMER = (byte) 0xAF;
    private static final int BACKOFF_CAUSE = Ike3gppBackoffTimer.ERROR_TYPE_NETWORK_FAILURE;

    private Ike3gppBackoffTimer mBackoffTimer;

    @Before
    public void setUp() {
        mBackoffTimer = new Ike3gppBackoffTimer(BACKOFF_TIMER, BACKOFF_CAUSE);
    }

    @Test
    public void testGetDataType() {
        assertEquals(Ike3gppData.DATA_TYPE_NOTIFY_BACKOFF_TIMER, mBackoffTimer.getDataType());
    }

    @Test
    public void testGetBackoffTimer() {
        assertEquals(BACKOFF_TIMER, mBackoffTimer.getBackoffTimer());
    }

    @Test
    public void testGetCause() {
        assertEquals(BACKOFF_CAUSE, mBackoffTimer.getBackoffCause());
    }
}
