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
 *
 */
package com.android.cts.servicekilltest;

import static com.android.cts.servicekilltestapp.ServiceKillTestService.Benchmark.Measure.*;
import static com.android.cts.servicekilltestapp.ServiceKillTestService.logDebug;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.PollingCheck;
import com.android.cts.servicekilltestapp.ServiceKillTestService;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@AppModeFull
@LargeTest
@RunWith(AndroidJUnit4.class)
public class ServiceKillTests {

    private static final boolean REQUIRE_UNPLUGGED = true;
    private static final float DEFAULT_THRESHOLD = 0.9f;
    private static final long DEFAULT_DURATION = TimeUnit.HOURS.toMinutes(3);

    @Before
    public void setUp() throws Exception {
        if (REQUIRE_UNPLUGGED) {
            setChargingAndCheck(false);
        } else {
            setCharging(false);
        }
    }

    @Test
    public void testAll() throws Exception {
        test("testAll", DEFAULT_DURATION, TOTAL);
    }

    /**
     * Examples on how you can configure the tests for different purposes
     */
    @Test
    @Ignore
    public void testAlarmStrict() throws Exception {
        test("testAlarm", DEFAULT_DURATION, ALARM, 0.99f);
    }

    @Test
    @Ignore
    public void testMainLong() throws Exception {
        test("testMain", DEFAULT_DURATION * 2, MAIN);
    }

    @Test
    @Ignore
    public void testWork() throws Exception {
        test("testWork", DEFAULT_DURATION, WORK);
    }

    @After
    public void tearDown() throws Exception {
        setCharging(true);
    }


    private void test(String testName, long runTimeMinutes,
            ServiceKillTestService.Benchmark.Measure measure) throws Exception {
        test(testName, runTimeMinutes, measure, DEFAULT_THRESHOLD);
    }

    private void test(String testName, long runTimeMinutes,
            ServiceKillTestService.Benchmark.Measure measure, float passThreshold)
            throws Exception {
        final Context context = InstrumentationRegistry.getTargetContext();
        final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        final long startTime = SystemClock.elapsedRealtime();

        final String testId = generateTestId(testName);

        logDebug("Testing " + testId + " " + runTimeMinutes + "min " + measure + " th " +
                passThreshold);

        final CountDownLatch latch = new CountDownLatch(1);

        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null) {
                    logDebug("onReceive testId " +
                            intent.getStringExtra(ServiceKillTestService.EXTRA_TEST_ID) + " " +
                            intent.getAction());
                    if (intent.hasExtra(ServiceKillTestService.EXTRA_TEST_ID) && testId.equals(
                            intent.getStringExtra(ServiceKillTestService.EXTRA_TEST_ID))) {
                        float result = intent.getFloatExtra(measure.name(), 0);
                        logDebug("result " + result);
                        assertTrue("Test '" + testName + "' for '" + measure + "' did not reach " +
                                passThreshold, result >= passThreshold);
                        latch.countDown();
                    }
                }
            }
        };
        context.registerReceiver(receiver, new IntentFilter(ServiceKillTestService.ACTION_RESULT),
                Context.RECEIVER_EXPORTED_UNAUDITED);

        startTest(context, testId);

        try {
            logDebug("waiting " + runTimeMinutes + "min ");
            while (SystemClock.elapsedRealtime() - startTime <
                    TimeUnit.MINUTES.toMillis(runTimeMinutes)) {
                latch.await(5, TimeUnit.MINUTES);
                if (REQUIRE_UNPLUGGED) {
                    setChargingAndCheck(false);
                }
            }
        } catch (InterruptedException e) {
            stopTest(context, testId);
            throw e;
        }

        stopTest(context, testId);

        logDebug("Waiting max 1 more minutes");
        latch.await(1, TimeUnit.MINUTES);

        if (latch.getCount() > 0) {
            fail("Did not get test result from ServiceKillTestService");
        }

        context.unregisterReceiver(receiver);
    }

    private Intent getServiceIntent(String action, String testId) {
        Intent i = new Intent(action);
        i.putExtra(ServiceKillTestService.EXTRA_TEST_ID, testId);
        i.setComponent(new ComponentName(ServiceKillTestService.TEST_APP_PACKAGE_NAME,
                ServiceKillTestService.class.getName()));
        return i;
    }

    private void startService(Context context, String testId, String action) {
        logDebug("service " + testId + " " + action);
        context.startForegroundService(getServiceIntent(action, testId));
    }

    private void startTest(Context context, String testId) {
        startService(context, testId, ServiceKillTestService.ACTION_START);
    }

    private void stopTest(Context context, String testId) {
        startService(context, testId, ServiceKillTestService.ACTION_STOP);
    }

    private String generateTestId(String testName) {
        return testName + ":" + this.hashCode() + ":" + SystemClock.elapsedRealtime();
    }


    private void setChargingAndCheck(final boolean charging) throws Exception {
        final BatteryManager bm =
                InstrumentationRegistry.getTargetContext().getSystemService(BatteryManager.class);
        logDebug("isCharging " + bm.isCharging() + " target " + charging);

        if (charging != bm.isCharging()) {
            setCharging(charging);
            PollingCheck.waitFor(TimeUnit.SECONDS.toMillis(20),
                    () -> charging == bm.isCharging(), "Setting charging timeout");
        }
    }

    private void setCharging(final boolean charging) throws Exception {
        if (charging) {
            logDebug("Setting CHARGING ");
            executeAndLog("dumpsys battery reset");
        } else {
            logDebug("Setting UNPLUG ");
            executeAndLog("dumpsys battery unplug");
            executeAndLog("dumpsys battery set status "
                    + BatteryManager.BATTERY_STATUS_DISCHARGING);
        }
    }

    private String executeAndLog(String command) throws IOException {
        final String output =
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                        .executeShellCommand(command).trim();
        logDebug("command: '" + command + "', output: '" + output + "'");
        return output;
    }

}
