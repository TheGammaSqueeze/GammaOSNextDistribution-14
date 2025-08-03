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

import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_IGNORED;
import static android.app.AppOpsManager.OPSTR_RUN_ANY_IN_BACKGROUND;
import static android.app.AppOpsManager.OPSTR_SCHEDULE_EXACT_ALARM;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.alarmmanager.alarmtestapp.cts.TestAlarmReceiver;
import android.alarmmanager.alarmtestapp.cts.TestAlarmScheduler;
import android.alarmmanager.alarmtestapp.cts.common.FgsTester;
import android.alarmmanager.util.AlarmManagerDeviceConfigHelper;
import android.alarmmanager.util.Utils;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.provider.DeviceConfig;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AppOpsUtils;
import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Tests that apps put in forced app standby by the user do not get to run alarms while in the
 * background
 */
@AppModeFull
@LargeTest
@RunWith(AndroidJUnit4.class)
public class BackgroundRestrictedAlarmsTest {
    private static final String TAG = BackgroundRestrictedAlarmsTest.class.getSimpleName();
    private static final String TEST_APP_PACKAGE = "android.alarmmanager.alarmtestapp.cts";
    private static final String TEST_APP_RECEIVER = TEST_APP_PACKAGE + ".TestAlarmScheduler";

    private static final long DEFAULT_WAIT = 1_000;
    private static final long POLL_INTERVAL = 200;
    private static final long MIN_REPEATING_INTERVAL = 10_000;
    private static final long APP_STANDBY_WINDOW = 10_000;
    private static final long APP_STANDBY_RESTRICTED_WINDOW = 10_000;

    private Context mContext;
    private ComponentName mAlarmScheduler;
    private AlarmManagerDeviceConfigHelper mConfigHelper = new AlarmManagerDeviceConfigHelper();
    private DeviceConfigStateHelper mActivityManagerDeviceConfigStateHelper =
            new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_ACTIVITY_MANAGER);
    private DeviceConfigStateHelper mTareDeviceConfigStateHelper =
            new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_TARE);

    private volatile int mAlarmCount;
    private volatile String mFgsResult;
    private int mInitialSyncDisabledMode = -1;

    private final BroadcastReceiver mAlarmStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mAlarmCount = intent.getIntExtra(TestAlarmReceiver.EXTRA_ALARM_COUNT, 1);
            mFgsResult = intent.getStringExtra(FgsTester.EXTRA_FGS_START_RESULT);
            Log.d(TAG, "Received Action: " + intent.getAction()
                    + ", alarmCount: " + mAlarmCount
                    + ", fgsResult " + mFgsResult
                    + " at elapsed: " + SystemClock.elapsedRealtime());

        }
    };

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mAlarmScheduler = new ComponentName(TEST_APP_PACKAGE, TEST_APP_RECEIVER);
        mAlarmCount = 0;
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TestAlarmReceiver.ACTION_REPORT_ALARM_EXPIRED);
        mContext.registerReceiver(mAlarmStateReceiver, intentFilter,
                Context.RECEIVER_EXPORTED_UNAUDITED);
        updateAlarmManagerConstants();
        mActivityManagerDeviceConfigStateHelper
                .set("bg_auto_restricted_bucket_on_bg_restricted", "false");
        SystemUtil.runWithShellPermissionIdentity(() -> {
            mInitialSyncDisabledMode = DeviceConfig.getSyncDisabledMode();
            DeviceConfig.setSyncDisabledMode(DeviceConfig.SYNC_DISABLED_MODE_UNTIL_REBOOT);
        });
        AppOpsUtils.setOpMode(TEST_APP_PACKAGE, OPSTR_RUN_ANY_IN_BACKGROUND, MODE_IGNORED);
        makeUidIdle();
        setAppStandbyBucket("active");
    }

    private void scheduleAlarm(int type, long triggerMillis, long interval) {
        final Intent setAlarmIntent = new Intent(TestAlarmScheduler.ACTION_SET_ALARM);
        setAlarmIntent.setComponent(mAlarmScheduler);
        setAlarmIntent.putExtra(TestAlarmScheduler.EXTRA_TYPE, type);
        setAlarmIntent.putExtra(TestAlarmScheduler.EXTRA_TRIGGER_TIME, triggerMillis);
        setAlarmIntent.putExtra(TestAlarmScheduler.EXTRA_REPEAT_INTERVAL, interval);
        setAlarmIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendBroadcast(setAlarmIntent);
    }

    private void scheduleAlarmClock(long triggerRTC) {
        AlarmManager.AlarmClockInfo alarmInfo = new AlarmManager.AlarmClockInfo(triggerRTC, null);

        final Intent setAlarmClockIntent = new Intent(TestAlarmScheduler.ACTION_SET_ALARM_CLOCK)
                .setComponent(mAlarmScheduler)
                .putExtra(TestAlarmScheduler.EXTRA_TEST_FGS, true)
                .putExtra(TestAlarmScheduler.EXTRA_ALARM_CLOCK_INFO, alarmInfo);
        mContext.sendBroadcast(setAlarmClockIntent);
    }

    private static int getMinExpectedExpirations(long now, long start, long interval) {
        if (now - start <= 1000) {
            return 0;
        }
        return 1 + (int)((now - start - 1000)/interval);
    }

    @Test
    public void testRepeatingAlarmBlocked() throws Exception {
        final long interval = MIN_REPEATING_INTERVAL;
        final long triggerElapsed = SystemClock.elapsedRealtime() + interval;
        scheduleAlarm(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, interval);
        Thread.sleep(DEFAULT_WAIT);
        Thread.sleep(2 * interval);
        assertFalse("Alarm got triggered even under restrictions", waitForAlarms(1, DEFAULT_WAIT));
        Thread.sleep(interval);
        AppOpsUtils.setOpMode(TEST_APP_PACKAGE, OPSTR_RUN_ANY_IN_BACKGROUND, MODE_ALLOWED);
        // The alarm is due to go off about 3 times by now. Adding some tolerance just in case
        // an expiration is due right about now.
        final int minCount = getMinExpectedExpirations(SystemClock.elapsedRealtime(),
                triggerElapsed, interval);
        assertTrue("Alarm should have expired at least " + minCount
                + " times when restrictions were lifted", waitForAlarms(minCount, DEFAULT_WAIT));
    }

    @Ignore("Feature auto_restricted_bucket_on_bg_restricted is disabled right now")
    @Test
    public void testRepeatingAlarmAllowedWhenAutoRestrictedBucketFeatureOn() throws Exception {
        mTareDeviceConfigStateHelper.set("enable_tare_mode", "0"); // Test requires app standby
        final long interval = MIN_REPEATING_INTERVAL;
        final long triggerElapsed = SystemClock.elapsedRealtime() + interval;
        toggleAutoRestrictedBucketOnBgRestricted(false);
        scheduleAlarm(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, interval);
        Thread.sleep(DEFAULT_WAIT);
        Thread.sleep(2 * interval);
        assertFalse("Alarm got triggered even under restrictions", waitForAlarms(1, DEFAULT_WAIT));
        Thread.sleep(interval);
        toggleAutoRestrictedBucketOnBgRestricted(true);
        // The alarm is due to go off about 3 times by now. Adding some tolerance just in case
        // an expiration is due right about now.
        final int minCount = getMinExpectedExpirations(SystemClock.elapsedRealtime(),
                triggerElapsed, interval);
        assertTrue("Alarm should have expired at least " + minCount
                + " times when restrictions were lifted", waitForAlarms(minCount, DEFAULT_WAIT));
    }

    @Test
    public void testAlarmClockNotBlocked() throws Exception {
        final long nowRTC = System.currentTimeMillis();
        final long waitInterval = 3_000;
        final long triggerRTC = nowRTC + waitInterval;

        AppOpsUtils.setUidMode(Utils.getPackageUid(TEST_APP_PACKAGE), OPSTR_SCHEDULE_EXACT_ALARM,
                MODE_ALLOWED);

        scheduleAlarmClock(triggerRTC);
        Thread.sleep(waitInterval);
        assertTrue("AlarmClock did not go off as scheduled when under restrictions",
                waitForAlarms(1, DEFAULT_WAIT));
        assertEquals("Fgs start wasn't successful from AlarmClock", "", mFgsResult);
    }

    @After
    public void tearDown() throws Exception {
        if (mInitialSyncDisabledMode != -1) {
            SystemUtil.runWithShellPermissionIdentity(() ->
                    DeviceConfig.setSyncDisabledMode(mInitialSyncDisabledMode));
        }
        deleteAlarmManagerConstants();
        AppOpsUtils.reset(TEST_APP_PACKAGE);
        mActivityManagerDeviceConfigStateHelper.restoreOriginalValues();
        mTareDeviceConfigStateHelper.restoreOriginalValues();
        // Cancel any leftover alarms
        final Intent cancelAlarmsIntent = new Intent(TestAlarmScheduler.ACTION_CANCEL_ALL_ALARMS);
        cancelAlarmsIntent.setComponent(mAlarmScheduler);
        mContext.sendBroadcast(cancelAlarmsIntent);
        mContext.unregisterReceiver(mAlarmStateReceiver);
        // Broadcast unregister may race with the next register in setUp
        Thread.sleep(DEFAULT_WAIT);
    }

    private void updateAlarmManagerConstants() {
        mConfigHelper.with("min_futurity", 0L)
                .with("min_interval", MIN_REPEATING_INTERVAL)
                .with("min_window", 0)
                .with("app_standby_window", APP_STANDBY_WINDOW)
                .with("app_standby_restricted_window", APP_STANDBY_RESTRICTED_WINDOW)
                .commitAndAwaitPropagation();
    }

    private void deleteAlarmManagerConstants() {
        mConfigHelper.restoreAll();
    }

    private void setAppStandbyBucket(String bucket) throws IOException {
        SystemUtil.runShellCommand("am set-standby-bucket " + TEST_APP_PACKAGE + " " + bucket);
    }

    private void makeUidIdle() throws IOException {
        SystemUtil.runShellCommand("cmd deviceidle tempwhitelist -r " + TEST_APP_PACKAGE);
        SystemUtil.runShellCommand("am make-uid-idle " + TEST_APP_PACKAGE);
    }

    private void toggleAutoRestrictedBucketOnBgRestricted(boolean enable) {
        mActivityManagerDeviceConfigStateHelper.set("bg_auto_restricted_bucket_on_bg_restricted",
                Boolean.toString(enable));
    }

    private boolean waitForAlarms(int expectedAlarms, long timeout) throws InterruptedException {
        final long deadLine = SystemClock.uptimeMillis() + timeout;
        int alarmCount;
        do {
            Thread.sleep(POLL_INTERVAL);
            alarmCount = mAlarmCount;
        } while (alarmCount < expectedAlarms && SystemClock.uptimeMillis() < deadLine);
        return alarmCount >= expectedAlarms;
    }
}
