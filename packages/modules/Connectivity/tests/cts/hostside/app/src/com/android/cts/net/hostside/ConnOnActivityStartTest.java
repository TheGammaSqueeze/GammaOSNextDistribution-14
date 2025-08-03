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

package com.android.cts.net.hostside;


import static android.app.ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_TOP_SLEEPING;

import static com.android.cts.net.hostside.NetworkPolicyTestUtils.getUiDevice;
import static com.android.cts.net.hostside.NetworkPolicyTestUtils.setRestrictBackground;
import static com.android.cts.net.hostside.Property.APP_STANDBY_MODE;
import static com.android.cts.net.hostside.Property.BATTERY_SAVER_MODE;
import static com.android.cts.net.hostside.Property.DATA_SAVER_MODE;
import static com.android.cts.net.hostside.Property.DOZE_MODE;
import static com.android.cts.net.hostside.Property.METERED_NETWORK;
import static com.android.cts.net.hostside.Property.NON_METERED_NETWORK;

import static org.junit.Assume.assumeTrue;

import android.os.SystemClock;
import android.util.Log;

import com.android.compatibility.common.util.ThrowingRunnable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@RequiredProperties({NON_METERED_NETWORK})
public class ConnOnActivityStartTest extends AbstractRestrictBackgroundNetworkTestCase {
    private static final int TEST_ITERATION_COUNT = 5;

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
    public void testStartActivity_batterySaver() throws Exception {
        setBatterySaverMode(true);
        assertLaunchedActivityHasNetworkAccess("testStartActivity_batterySaver", null);
    }

    @Test
    @RequiredProperties({DATA_SAVER_MODE, METERED_NETWORK})
    public void testStartActivity_dataSaver() throws Exception {
        setRestrictBackground(true);
        assertLaunchedActivityHasNetworkAccess("testStartActivity_dataSaver", null);
    }

    @Test
    @RequiredProperties({DOZE_MODE})
    public void testStartActivity_doze() throws Exception {
        setDozeMode(true);
        // TODO (235284115): We need to turn on Doze every time before starting
        // the activity.
        assertLaunchedActivityHasNetworkAccess("testStartActivity_doze", null);
    }

    @Test
    @RequiredProperties({APP_STANDBY_MODE})
    public void testStartActivity_appStandby() throws Exception {
        turnBatteryOn();
        setAppIdle(true);
        // TODO (235284115): We need to put the app into app standby mode every
        // time before starting the activity.
        assertLaunchedActivityHasNetworkAccess("testStartActivity_appStandby", null);
    }

    @Test
    public void testStartActivity_default() throws Exception {
        assumeTrue("Feature not enabled", isNetworkBlockedForTopSleepingAndAbove());
        assertLaunchedActivityHasNetworkAccess("testStartActivity_default", () -> {
            assertProcessStateBelow(PROCESS_STATE_TOP_SLEEPING);
            SystemClock.sleep(PROCESS_STATE_TRANSITION_DELAY_MS);
        });
    }

    private void assertLaunchedActivityHasNetworkAccess(String testName,
            ThrowingRunnable onBeginIteration) throws Exception {
        for (int i = 0; i < TEST_ITERATION_COUNT; ++i) {
            if (onBeginIteration != null) {
                onBeginIteration.run();
            }
            Log.i(TAG, testName + " start #" + i);
            launchComponentAndAssertNetworkAccess(TYPE_COMPONENT_ACTIVTIY);
            getUiDevice().pressHome();
            assertProcessStateBelow(PROCESS_STATE_BOUND_FOREGROUND_SERVICE);
            Log.i(TAG, testName + " end #" + i);
        }
    }
}
