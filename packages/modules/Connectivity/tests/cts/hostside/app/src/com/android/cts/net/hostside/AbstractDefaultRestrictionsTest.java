/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.cts.net.hostside;

import static android.app.ActivityManager.PROCESS_STATE_TOP_SLEEPING;

import static org.junit.Assume.assumeTrue;

import android.os.SystemClock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Base class for default, always-on network restrictions.
 */
abstract class AbstractDefaultRestrictionsTest extends AbstractRestrictBackgroundNetworkTestCase {

    @Before
    public final void setUp() throws Exception {
        super.setUp();

        removePowerSaveModeWhitelist(TEST_APP2_PKG);
        removePowerSaveModeExceptIdleWhitelist(TEST_APP2_PKG);

        registerBroadcastReceiver();
        assumeTrue("Feature not enabled", isNetworkBlockedForTopSleepingAndAbove());
    }

    @After
    public final void tearDown() throws Exception {
        super.tearDown();

        removePowerSaveModeWhitelist(TEST_APP2_PKG);
        removePowerSaveModeExceptIdleWhitelist(TEST_APP2_PKG);
    }

    @Test
    public void testFgsNetworkAccess() throws Exception {
        assertProcessStateBelow(PROCESS_STATE_TOP_SLEEPING);
        SystemClock.sleep(PROCESS_STATE_TRANSITION_DELAY_MS);
        assertNetworkAccess(false, null);

        launchComponentAndAssertNetworkAccess(TYPE_COMPONENT_FOREGROUND_SERVICE);
    }

    @Test
    public void testActivityNetworkAccess() throws Exception {
        assertProcessStateBelow(PROCESS_STATE_TOP_SLEEPING);
        SystemClock.sleep(PROCESS_STATE_TRANSITION_DELAY_MS);
        assertNetworkAccess(false, null);

        launchComponentAndAssertNetworkAccess(TYPE_COMPONENT_ACTIVTIY);
    }

    @Test
    public void testBackgroundNetworkAccess_inFullAllowlist() throws Exception {
        assertProcessStateBelow(PROCESS_STATE_TOP_SLEEPING);
        SystemClock.sleep(PROCESS_STATE_TRANSITION_DELAY_MS);
        assertNetworkAccess(false, null);

        addPowerSaveModeWhitelist(TEST_APP2_PKG);
        assertProcessStateBelow(PROCESS_STATE_TOP_SLEEPING);
        assertNetworkAccess(true, null);
    }

    @Test
    public void testBackgroundNetworkAccess_inExceptIdleAllowlist() throws Exception {
        assertProcessStateBelow(PROCESS_STATE_TOP_SLEEPING);
        SystemClock.sleep(PROCESS_STATE_TRANSITION_DELAY_MS);
        assertNetworkAccess(false, null);

        addPowerSaveModeExceptIdleWhitelist(TEST_APP2_PKG);
        assertProcessStateBelow(PROCESS_STATE_TOP_SLEEPING);
        assertNetworkAccess(true, null);
    }
}
