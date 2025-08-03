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

import static com.android.cts.net.hostside.NetworkPolicyTestUtils.setRestrictBackground;
import static com.android.cts.net.hostside.Property.APP_STANDBY_MODE;
import static com.android.cts.net.hostside.Property.BATTERY_SAVER_MODE;
import static com.android.cts.net.hostside.Property.DATA_SAVER_MODE;
import static com.android.cts.net.hostside.Property.DOZE_MODE;
import static com.android.cts.net.hostside.Property.METERED_NETWORK;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AbstractExpeditedJobTest extends AbstractRestrictBackgroundNetworkTestCase {
    @Before
    public final void setUp() throws Exception {
        super.setUp();
        resetDeviceState();
    }

    @After
    public final void tearDown() throws Exception {
        super.tearDown();
        resetDeviceState();
    }

    private void resetDeviceState() throws Exception {
        resetBatteryState();
        setBatterySaverMode(false);
        setRestrictBackground(false);
        setAppIdle(false);
        setDozeMode(false);
    }

    @Test
    @RequiredProperties({BATTERY_SAVER_MODE})
    public void testNetworkAccess_batterySaverMode() throws Exception {
        assertBackgroundNetworkAccess(true);
        assertExpeditedJobHasNetworkAccess();

        setBatterySaverMode(true);
        assertBackgroundNetworkAccess(false);
        assertExpeditedJobHasNetworkAccess();
    }

    @Test
    @RequiredProperties({DATA_SAVER_MODE, METERED_NETWORK})
    public void testNetworkAccess_dataSaverMode() throws Exception {
        assertBackgroundNetworkAccess(true);
        assertExpeditedJobHasNetworkAccess();

        setRestrictBackground(true);
        assertBackgroundNetworkAccess(false);
        assertExpeditedJobHasNoNetworkAccess();
    }

    @Test
    @RequiredProperties({APP_STANDBY_MODE})
    public void testNetworkAccess_appIdleState() throws Exception {
        turnBatteryOn();
        setAppIdle(false);
        assertBackgroundNetworkAccess(true);
        assertExpeditedJobHasNetworkAccess();

        setAppIdle(true);
        assertBackgroundNetworkAccess(false);
        assertExpeditedJobHasNetworkAccess();
    }

    @Test
    @RequiredProperties({DOZE_MODE})
    public void testNetworkAccess_dozeMode() throws Exception {
        assertBackgroundNetworkAccess(true);
        assertExpeditedJobHasNetworkAccess();

        setDozeMode(true);
        assertBackgroundNetworkAccess(false);
        assertExpeditedJobHasNetworkAccess();
    }

    @Test
    @RequiredProperties({DATA_SAVER_MODE, BATTERY_SAVER_MODE, METERED_NETWORK})
    public void testNetworkAccess_dataAndBatterySaverMode() throws Exception {
        assertBackgroundNetworkAccess(true);
        assertExpeditedJobHasNetworkAccess();

        setRestrictBackground(true);
        setBatterySaverMode(true);
        assertBackgroundNetworkAccess(false);
        assertExpeditedJobHasNoNetworkAccess();
    }

    @Test
    @RequiredProperties({DOZE_MODE, DATA_SAVER_MODE, METERED_NETWORK})
    public void testNetworkAccess_dozeAndDataSaverMode() throws Exception {
        assertBackgroundNetworkAccess(true);
        assertExpeditedJobHasNetworkAccess();

        setRestrictBackground(true);
        setDozeMode(true);
        assertBackgroundNetworkAccess(false);
        assertExpeditedJobHasNoNetworkAccess();
    }

    @Test
    @RequiredProperties({DATA_SAVER_MODE, BATTERY_SAVER_MODE, METERED_NETWORK, DOZE_MODE,
            APP_STANDBY_MODE})
    public void testNetworkAccess_allRestrictionsEnabled() throws Exception {
        assertBackgroundNetworkAccess(true);
        assertExpeditedJobHasNetworkAccess();

        setRestrictBackground(true);
        setBatterySaverMode(true);
        setAppIdle(true);
        setDozeMode(true);
        assertBackgroundNetworkAccess(false);
        assertExpeditedJobHasNoNetworkAccess();
    }
}
