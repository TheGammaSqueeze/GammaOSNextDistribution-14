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

package com.android.cts.net.hostside;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class RestrictedModeTest extends AbstractRestrictBackgroundNetworkTestCase {
    @Before
    public void setUp() throws Exception {
        super.setUp();
        setRestrictedNetworkingMode(false);
    }

    @After
    public void tearDown() throws Exception {
        setRestrictedNetworkingMode(false);
        super.tearDown();
    }

    @Test
    public void testNetworkAccess() throws Exception {
        // go to foreground state and enable restricted mode
        launchComponentAndAssertNetworkAccess(TYPE_COMPONENT_ACTIVTIY);
        setRestrictedNetworkingMode(true);
        assertTopNetworkAccess(false);

        // go to background state
        finishActivity();
        assertBackgroundNetworkAccess(false);

        // disable restricted mode and assert network access in foreground and background states
        setRestrictedNetworkingMode(false);
        launchComponentAndAssertNetworkAccess(TYPE_COMPONENT_ACTIVTIY);
        assertTopNetworkAccess(true);

        // go to background state
        finishActivity();
        assertBackgroundNetworkAccess(true);
    }

    @Test
    public void testNetworkAccess_withBatterySaver() throws Exception {
        setBatterySaverMode(true);
        try {
            addPowerSaveModeWhitelist(TEST_APP2_PKG);
            assertBackgroundNetworkAccess(true);

            setRestrictedNetworkingMode(true);
            // App would be denied network access since Restricted mode is on.
            assertBackgroundNetworkAccess(false);
            setRestrictedNetworkingMode(false);
            // Given that Restricted mode is turned off, app should be able to access network again.
            assertBackgroundNetworkAccess(true);
        } finally {
            setBatterySaverMode(false);
        }
    }
}
