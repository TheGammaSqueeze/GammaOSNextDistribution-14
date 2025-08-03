/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.alarmmanager.cts;

import static android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP;
import static android.app.AppOpsManager.MODE_IGNORED;
import static android.app.AppOpsManager.OPSTR_SCHEDULE_EXACT_ALARM;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.alarmmanager.alarmtestapp.cts.TestAlarmReceiver;
import android.alarmmanager.alarmtestapp.cts.TestAlarmScheduler;
import android.alarmmanager.util.AlarmManagerDeviceConfigHelper;
import android.alarmmanager.util.Utils;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.provider.DeviceConfig;
import android.util.Log;
import android.util.LongArray;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AppOpsUtils;
import com.android.compatibility.common.util.AppStandbyUtils;
import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Tests that app standby imposes the appropriate restrictions on alarms
 */
@AppModeFull
@LargeTest
@RunWith(AndroidJUnit4.class)
public class AppStandbyTests {
    private static final String TAG = AppStandbyTests.class.getSimpleName();
    static final String TEST_APP_PACKAGE = "android.alarmmanager.alarmtestapp.cts";
    private static final String TEST_APP_RECEIVER = TEST_APP_PACKAGE + ".TestAlarmScheduler";

    private static final long DEFAULT_WAIT = 2_000;
    private static final long POLL_INTERVAL = 200;

    // Tweaked alarm manager constants to facilitate testing
    private static final long MIN_FUTURITY = 1_000;

    // Not touching ACTIVE and RARE parameters for this test
    private static final int WORKING_INDEX = 0;
    private static final int FREQUENT_INDEX = 1;
    private static final int RARE_INDEX = 2;
    private static final String[] APP_BUCKET_TAGS = {
            "working_set",
            "frequent",
            "rare",
    };

    private static final long APP_STANDBY_WINDOW = 10_000;
    private static final long MIN_WINDOW = 100;
    private static final String[] APP_BUCKET_QUOTA_KEYS = {
            "standby_quota_working",
            "standby_quota_frequent",
            "standby_quota_rare",
    };
    private static final int[] APP_STANDBY_QUOTAS = {
            5,  // Working set
            3,  // Frequent
            1,  // Rare
    };

    // Save the state before running tests to restore it after we finish testing.
    private static boolean sOrigAppStandbyEnabled;
    // Test app's alarm history to help predict when a subsequent alarm is going to get deferred.
    private static TestAlarmHistory sAlarmHistory;
    // Make sure TARE isn't enabled for any of these tests.
    private static final DeviceConfigStateHelper sTareDeviceConfigStateHelper =
            new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_TARE);
    private static Context sContext = InstrumentationRegistry.getTargetContext();

    private ComponentName mAlarmScheduler;
    private AtomicInteger mAlarmCount;
    private AlarmManagerDeviceConfigHelper mConfigHelper = new AlarmManagerDeviceConfigHelper();

    private final BroadcastReceiver mAlarmStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mAlarmCount.getAndAdd(intent.getIntExtra(TestAlarmReceiver.EXTRA_ALARM_COUNT, 1));
            final long nowElapsed = SystemClock.elapsedRealtime();
            sAlarmHistory.addTime(nowElapsed);
            Log.d(TAG, "No. of expirations: " + mAlarmCount + " elapsed: " + nowElapsed);
        }
    };

    @BeforeClass
    public static void setUpTests() throws Exception {
        sAlarmHistory = new TestAlarmHistory();
        sOrigAppStandbyEnabled = AppStandbyUtils.isAppStandbyEnabledAtRuntime();
        if (!sOrigAppStandbyEnabled) {
            AppStandbyUtils.setAppStandbyEnabledAtRuntime(true);

            // Give system sometime to initialize itself.
            Thread.sleep(100);
        }
        // These tests are designed for the old quota system.
        sTareDeviceConfigStateHelper.set("enable_tare_mode", "0");
    }

    @Before
    public void setUp() throws Exception {
        mAlarmScheduler = new ComponentName(TEST_APP_PACKAGE, TEST_APP_RECEIVER);
        mAlarmCount = new AtomicInteger(0);

        // To make sure it doesn't get pinned to working_set on older versions.
        AppOpsUtils.setUidMode(Utils.getPackageUid(TEST_APP_PACKAGE), OPSTR_SCHEDULE_EXACT_ALARM,
                MODE_IGNORED);

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TestAlarmReceiver.ACTION_REPORT_ALARM_EXPIRED);
        sContext.registerReceiver(mAlarmStateReceiver, intentFilter,
                Context.RECEIVER_EXPORTED_UNAUDITED);

        setBatteryCharging(false);
        updateAlarmManagerConstants();
        assumeTrue("App Standby not enabled on device", AppStandbyUtils.isAppStandbyEnabled());
    }

    private void scheduleAlarm(long triggerMillis, long interval) throws InterruptedException {
        final Intent setAlarmIntent = new Intent(TestAlarmScheduler.ACTION_SET_ALARM);
        setAlarmIntent.setComponent(mAlarmScheduler);
        setAlarmIntent.putExtra(TestAlarmScheduler.EXTRA_TYPE, ELAPSED_REALTIME_WAKEUP);
        setAlarmIntent.putExtra(TestAlarmScheduler.EXTRA_TRIGGER_TIME, triggerMillis);
        setAlarmIntent.putExtra(TestAlarmScheduler.EXTRA_WINDOW_LENGTH, MIN_WINDOW);
        setAlarmIntent.putExtra(TestAlarmScheduler.EXTRA_REPEAT_INTERVAL, interval);
        setAlarmIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        final CountDownLatch resultLatch = new CountDownLatch(1);
        sContext.sendOrderedBroadcast(setAlarmIntent, null, new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                resultLatch.countDown();
            }
        }, null, Activity.RESULT_CANCELED, null, null);
        assertTrue("Request did not complete", resultLatch.await(10, TimeUnit.SECONDS));
    }

    public void testSimpleQuotaDeferral(int bucketIndex) throws Exception {
        setTestAppStandbyBucket(APP_BUCKET_TAGS[bucketIndex]);
        final int quota = APP_STANDBY_QUOTAS[bucketIndex];

        long startElapsed = SystemClock.elapsedRealtime();
        final long freshWindowPoint = sAlarmHistory.getLast(1) + APP_STANDBY_WINDOW;
        if (freshWindowPoint > startElapsed) {
            Thread.sleep(freshWindowPoint - startElapsed);
            startElapsed = freshWindowPoint;
            // Now we should have no alarms in the past APP_STANDBY_WINDOW
        }
        final long desiredTrigger = startElapsed + APP_STANDBY_WINDOW;
        final long firstTrigger = startElapsed + 4_000;
        assertTrue("Quota too large for test",
                firstTrigger + ((quota - 1) * MIN_FUTURITY) < desiredTrigger);
        for (int i = 0; i < quota; i++) {
            final long trigger = firstTrigger + (i * MIN_FUTURITY);
            scheduleAlarm(trigger, 0);
            Thread.sleep(trigger - SystemClock.elapsedRealtime());
            assertTrue("Alarm within quota not firing as expected", waitForAlarm());
        }

        // Now quota is reached, any subsequent alarm should get deferred.
        scheduleAlarm(desiredTrigger, 0);
        Thread.sleep(desiredTrigger - SystemClock.elapsedRealtime());
        assertFalse("Alarm exceeding quota not deferred", waitForAlarm());
        final long minTrigger = firstTrigger + APP_STANDBY_WINDOW;
        Thread.sleep(minTrigger - SystemClock.elapsedRealtime());
        assertTrue("Alarm exceeding quota not delivered after expected delay", waitForAlarm());
    }

    @Test
    public void testActiveQuota() throws Exception {
        setTestAppStandbyBucket("active");
        long nextTrigger = SystemClock.elapsedRealtime() + MIN_FUTURITY;
        for (int i = 0; i < 3; i++) {
            scheduleAlarm(nextTrigger, 0);
            Thread.sleep(MIN_FUTURITY);
            assertTrue("Alarm not received as expected when app is in active", waitForAlarm());
            nextTrigger += MIN_FUTURITY;
        }
    }

    @Test
    public void testWorkingQuota() throws Exception {
        testSimpleQuotaDeferral(WORKING_INDEX);
    }

    @Test
    public void testFrequentQuota() throws Exception {
        testSimpleQuotaDeferral(FREQUENT_INDEX);
    }

    @Test
    public void testRareQuota() throws Exception {
        testSimpleQuotaDeferral(RARE_INDEX);
    }

    @Test
    public void testNeverQuota() throws Exception {
        setTestAppStandbyBucket("never");
        final long expectedTrigger = SystemClock.elapsedRealtime() + MIN_FUTURITY;
        scheduleAlarm(expectedTrigger, 0);
        Thread.sleep(10_000);
        assertFalse("Alarm received when app was in never bucket", waitForAlarm());
    }

    @Test
    public void testPowerWhitelistedAlarmNotBlocked() throws Exception {
        setTestAppStandbyBucket(APP_BUCKET_TAGS[RARE_INDEX]);
        setPowerAllowlisted(true);
        final long triggerTime = SystemClock.elapsedRealtime() + MIN_FUTURITY;
        scheduleAlarm(triggerTime, 0);
        Thread.sleep(MIN_FUTURITY);
        assertTrue("Alarm did not go off for whitelisted app in rare bucket", waitForAlarm());
        setPowerAllowlisted(false);
    }

    @After
    public void tearDown() throws Exception {
        setPowerAllowlisted(false);
        setBatteryCharging(true);
        mConfigHelper.restoreAll();
        final Intent cancelAlarmsIntent = new Intent(TestAlarmScheduler.ACTION_CANCEL_ALL_ALARMS);
        cancelAlarmsIntent.setComponent(mAlarmScheduler);
        sContext.sendBroadcast(cancelAlarmsIntent);
        sContext.unregisterReceiver(mAlarmStateReceiver);
        // Broadcast unregister may race with the next register in setUp
        Thread.sleep(500);
    }

    @AfterClass
    public static void tearDownTests() throws Exception {
        if (!sOrigAppStandbyEnabled) {
            AppStandbyUtils.setAppStandbyEnabledAtRuntime(sOrigAppStandbyEnabled);
        }
        sTareDeviceConfigStateHelper.restoreOriginalValues();
    }

    private void updateAlarmManagerConstants() {
        mConfigHelper.with("min_futurity", MIN_FUTURITY)
                .with("app_standby_window", APP_STANDBY_WINDOW)
                .with("min_window", MIN_WINDOW);
        for (int i = 0; i < APP_STANDBY_QUOTAS.length; i++) {
            mConfigHelper.with(APP_BUCKET_QUOTA_KEYS[i], APP_STANDBY_QUOTAS[i]);
        }
        mConfigHelper.commitAndAwaitPropagation();
    }

    private void setPowerAllowlisted(boolean whitelist) throws IOException {
        final StringBuffer cmd = new StringBuffer("cmd deviceidle whitelist ");
        cmd.append(whitelist ? "+" : "-");
        cmd.append(TEST_APP_PACKAGE);
        executeAndLog(cmd.toString());
    }

    static void setTestAppStandbyBucket(String bucket) throws IOException {
        executeAndLog("am set-standby-bucket " + TEST_APP_PACKAGE + " " + bucket);
    }

    private void setBatteryCharging(final boolean charging) throws Exception {
        final BatteryManager bm = sContext.getSystemService(BatteryManager.class);
        if (charging) {
            executeAndLog("dumpsys battery reset");
        } else {
            executeAndLog("dumpsys battery unplug");
            executeAndLog("dumpsys battery set status " +
                    BatteryManager.BATTERY_STATUS_DISCHARGING);
            assertTrue("Battery could not be unplugged", waitUntil(() -> !bm.isCharging(), 5_000));
        }
    }

    private static String executeAndLog(String cmd) throws IOException {
        final String output = SystemUtil.runShellCommand(cmd).trim();
        Log.d(TAG, "command: [" + cmd + "], output: [" + output + "]");
        return output;
    }

    private boolean waitForAlarm() throws InterruptedException {
        final boolean success = waitUntil(() -> (mAlarmCount.get() == 1), DEFAULT_WAIT);
        mAlarmCount.set(0);
        return success;
    }

    private boolean waitUntil(BooleanSupplier condition, long timeout) throws InterruptedException {
        final long deadLine = SystemClock.uptimeMillis() + timeout;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < deadLine) {
            Thread.sleep(POLL_INTERVAL);
        }
        return condition.getAsBoolean();
    }

    private static final class TestAlarmHistory {
        private LongArray mHistory = new LongArray();

        private synchronized void addTime(long timestamp) {
            mHistory.add(timestamp);
        }

        /**
         * Get the xth alarm time from the end.
         */
        private synchronized long getLast(int x) {
            if (x == 0 || x > mHistory.size()) {
                return 0;
            }
            return mHistory.get(mHistory.size() - x);
        }
    }
}
