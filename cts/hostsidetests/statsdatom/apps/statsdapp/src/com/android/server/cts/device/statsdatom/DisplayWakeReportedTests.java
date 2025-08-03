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

package com.android.server.cts.device.statsdatom;

import static android.os.PowerManager.PARTIAL_WAKE_LOCK;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.support.test.uiautomator.UiDevice;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.NonApiTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * These tests are run by DisplayWakeReportedStatsTests to generate some stats by exercising
 * wake APIs.
 *
 * <p>They only trigger the APIs, but don't test anything themselves.
 */
@NonApiTest(exemptionReasons = {}, justification = "METRIC")
public class DisplayWakeReportedTests {
    private static final String TAG = "DisplayWakeReportedTests";
    private static final int WAKEFULNESS_TIMEOUT = 10000;

    private PowerManager mPowerManager;
    private PowerManager.WakeLock mSystemWakeLock;
    private UiDevice mUiDevice;

    @Before
    public void setUp() throws Exception {
        mPowerManager = InstrumentationRegistry.getContext().getSystemService(PowerManager.class);
        mSystemWakeLock = mPowerManager.newWakeLock(PARTIAL_WAKE_LOCK, TAG);
        mSystemWakeLock.acquire();

        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mUiDevice.executeShellCommand("input keyevent SLEEP");
        PollingCheck.check("Device failed to sleep", WAKEFULNESS_TIMEOUT,
                () -> !mPowerManager.isInteractive());
    }

    @After
    public void tearDown() throws Exception {
        PollingCheck.check("Device failed to wake up", WAKEFULNESS_TIMEOUT,
                () -> mPowerManager.isInteractive());
        mSystemWakeLock.release();
    }

    @Test
    public void testWakeWithWakeKey() throws Exception {
        mUiDevice.executeShellCommand("input keyevent WAKEUP");
    }

    @Test
    public void testWakeWithWakeLock() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        AppOpsManager appOpsManager = context.getSystemService(AppOpsManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(appOpsManager,
                (aom) -> aom.setMode(AppOpsManager.OP_TURN_SCREEN_ON, Process.myUid(),
                        context.getPackageName(), AppOpsManager.MODE_ALLOWED),
                "android.permission.MANAGE_APP_OPS_MODES");

        PowerManager.WakeLock wakeLock = mPowerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "DisplayWakeReportedTests");
        wakeLock.acquire(100);
    }

    @Test
    public void testWakeWithWakeUpApi() throws Exception {
        ShellIdentityUtils.invokeWithShellPermissions(() ->
                mPowerManager.wakeUp(SystemClock.uptimeMillis(),
                        PowerManager.WAKE_REASON_UNKNOWN,
                        TAG));
    }

    @Test
    public void testWakeWithTurnScreenOnActivity() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        Intent intent = new Intent(context, TurnScreenOnActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
