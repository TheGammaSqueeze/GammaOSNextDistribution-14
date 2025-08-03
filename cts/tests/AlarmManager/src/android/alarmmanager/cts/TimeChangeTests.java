/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package android.alarmmanager.cts;

import static android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP;
import static android.app.AlarmManager.RTC_WAKEUP;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.alarmmanager.util.AlarmManagerDeviceConfigHelper;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.BatteryUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests that system time changes are handled appropriately for alarms
 */
@AppModeFull
@LargeTest
@RunWith(AndroidJUnit4.class)
public class TimeChangeTests {
    private static final String TAG = TimeChangeTests.class.getSimpleName();
    private static final String ACTION_ALARM = "android.alarmmanager.cts.ACTION_ALARM";
    private static final long DEFAULT_WAIT_MILLIS = 5_000;
    private static final long MILLIS_IN_MINUTE = 60_000;

    private final Context mContext = InstrumentationRegistry.getTargetContext();
    private final AlarmManager mAlarmManager = mContext.getSystemService(AlarmManager.class);
    private AlarmManagerDeviceConfigHelper mConfigHelper = new AlarmManagerDeviceConfigHelper();
    private PendingIntent mAlarmPi;
    private long mTestStartRtc;
    private long mTestStartElapsed;
    private boolean mTimeChanged;
    private boolean mAutoTimeEnabled;

    final CountDownLatch mAlarmLatch = new CountDownLatch(1);

    private BroadcastReceiver mAlarmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ACTION_ALARM:
                    if (mAlarmLatch != null) {
                        mAlarmLatch.countDown();
                    }
                    break;
                default:
                    Log.e(TAG, "Received unexpected action " + intent.getAction());
            }
        }
    };

    private void setTime(long rtcToSet) {
        Log.i(TAG, "Changing system time to " + rtcToSet);
        SystemUtil.runWithShellPermissionIdentity(() -> mAlarmManager.setTime(rtcToSet));
        mTimeChanged = true;
    }

    private boolean isAutoTimeEnabled() {
        String auto_time = SystemUtil.runShellCommand("settings get global auto_time");
        return auto_time.trim().equals("1");
    }

    @Before
    public void setUp() throws Exception {
        final Intent alarmIntent = new Intent(ACTION_ALARM)
                .setPackage(mContext.getPackageName())
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mAlarmPi = PendingIntent.getBroadcast(mContext, 0, alarmIntent, PendingIntent.FLAG_MUTABLE);
        final IntentFilter alarmFilter = new IntentFilter(ACTION_ALARM);
        mContext.registerReceiver(mAlarmReceiver, alarmFilter,
                Context.RECEIVER_EXPORTED_UNAUDITED);
        mConfigHelper.with("min_futurity", 0L).commitAndAwaitPropagation();
        BatteryUtils.runDumpsysBatteryUnplug();
        mTestStartRtc = System.currentTimeMillis();
        mTestStartElapsed = SystemClock.elapsedRealtime();
        mAutoTimeEnabled = isAutoTimeEnabled();
        // Disable auto time as tests might fail if the system restores time while they are running
        SystemUtil.runShellCommand("settings put global auto_time 0");
    }

    @Test
    public void elapsedAlarmsUnaffected() throws Exception {
        final long delayElapsed = 5_000;
        final long expectedTriggerElapsed = mTestStartElapsed + delayElapsed;
        mAlarmManager.setExact(ELAPSED_REALTIME_WAKEUP, expectedTriggerElapsed, mAlarmPi);
        final long newRtc = mTestStartRtc - 32 * MILLIS_IN_MINUTE; // arbitrary, shouldn't matter
        setTime(newRtc);
        Thread.sleep(delayElapsed);
        assertTrue("Elapsed alarm did not fire as expected after time change",
                mAlarmLatch.await(DEFAULT_WAIT_MILLIS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void rtcAlarmsRescheduled() throws Exception {
        final long newRtc = mTestStartRtc + 14 * MILLIS_IN_MINUTE; // arbitrary, but in the future
        final long delayRtc = 4_231;
        final long expectedTriggerRtc = newRtc + delayRtc;
        mAlarmManager.setExact(RTC_WAKEUP, expectedTriggerRtc, mAlarmPi);
        Thread.sleep(delayRtc);
        assertFalse("Alarm fired before time was changed",
                mAlarmLatch.await(DEFAULT_WAIT_MILLIS, TimeUnit.MILLISECONDS));
        setTime(newRtc);
        Thread.sleep(delayRtc);
        assertTrue("Alarm did not fire as expected after time was changed",
                mAlarmLatch.await(DEFAULT_WAIT_MILLIS, TimeUnit.MILLISECONDS));
    }

    @After
    public void tearDown() {
        mConfigHelper.restoreAll();
        BatteryUtils.runDumpsysBatteryReset();
        if (mTimeChanged) {
            // Make an attempt at resetting the clock to normal
            final long testDuration = SystemClock.elapsedRealtime() - mTestStartElapsed;
            final long expectedCorrectRtc = mTestStartRtc + testDuration;
            setTime(expectedCorrectRtc);
        }
        if (mAutoTimeEnabled) {
            // Restore auto time
            SystemUtil.runShellCommand("settings put global auto_time 1");
        }
    }
}
