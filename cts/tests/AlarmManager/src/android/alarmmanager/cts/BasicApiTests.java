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

package android.alarmmanager.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.alarmmanager.util.AlarmManagerDeviceConfigHelper;
import android.alarmmanager.util.Utils;
import android.app.AlarmManager;
import android.app.AlarmManager.AlarmClockInfo;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.WorkSource;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * General API tests earlier present at CtsAppTestCases:AlarmManagerTest
 */
@LargeTest
@AppModeFull
@RunWith(AndroidJUnit4.class)
public class BasicApiTests {
    private static final String TAG = BasicApiTests.class.getSimpleName();
    public static final String MOCKACTION = "android.app.AlarmManagerTest.TEST_ALARMRECEIVER";
    public static final String MOCKACTION2 = "android.app.AlarmManagerTest.TEST_ALARMRECEIVER2";

    private AlarmManager mAm;
    private Intent mIntent;
    private PendingIntent mSender;
    private Intent mIntent2;
    private PendingIntent mSender2;

    /*
     *  The default snooze delay: 5 seconds
     */
    private static final long SNOOZE_DELAY = 5_000L;
    private long mWakeupTime;
    private MockAlarmReceiver mMockAlarmReceiver;
    private MockAlarmReceiver mMockAlarmReceiver2;

    private static final int TIME_DELTA = 1000;
    private static final int TIME_DELAY = 10_000;

    private static final long TEST_ALARM_FUTURITY = 2_000L;
    private static final long FAIL_DELTA = 50;
    private static final long PRIORITY_ALARM_DELAY = 6_000;
    private static final long REPEAT_PERIOD = 30_000;
    private Context mContext = InstrumentationRegistry.getTargetContext();
    private final AlarmManagerDeviceConfigHelper mDeviceConfigHelper =
            new AlarmManagerDeviceConfigHelper();
    private PowerManager mPowerManager = mContext.getSystemService(PowerManager.class);

    @Rule
    public DumpLoggerRule mFailLoggerRule = new DumpLoggerRule(TAG);

    @Before
    public void setUp() throws Exception {
        mAm = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);

        mIntent = new Intent(MOCKACTION)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        mSender = PendingIntent.getBroadcast(mContext, 0, mIntent, PendingIntent.FLAG_IMMUTABLE);
        mMockAlarmReceiver = new MockAlarmReceiver(mIntent.getAction());

        mIntent2 = new Intent(MOCKACTION2)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        mSender2 = PendingIntent.getBroadcast(mContext, 0, mIntent2, PendingIntent.FLAG_IMMUTABLE);
        mMockAlarmReceiver2 = new MockAlarmReceiver(mIntent2.getAction());

        IntentFilter filter = new IntentFilter(mIntent.getAction());
        mContext.registerReceiver(mMockAlarmReceiver, filter,
                Context.RECEIVER_EXPORTED_UNAUDITED);

        IntentFilter filter2 = new IntentFilter(mIntent2.getAction());
        mContext.registerReceiver(mMockAlarmReceiver2, filter2,
                Context.RECEIVER_EXPORTED_UNAUDITED);

        mDeviceConfigHelper.with("min_futurity", 0L)
                .with("min_interval", REPEAT_PERIOD)
                .with("min_window", 0L)
                .with("priority_alarm_delay", PRIORITY_ALARM_DELAY)
                .commitAndAwaitPropagation();
        Utils.enableChangeForSelf(AlarmManager.ENABLE_USE_EXACT_ALARM);
    }

    @After
    public void tearDown() throws Exception {
        mAm.cancel(mMockAlarmReceiver);
        mAm.cancel(mMockAlarmReceiver2);

        mDeviceConfigHelper.restoreAll();
        mContext.unregisterReceiver(mMockAlarmReceiver);
        mContext.unregisterReceiver(mMockAlarmReceiver2);
        toggleIdleMode(false);
        Utils.resetChange(AlarmManager.ENABLE_USE_EXACT_ALARM, mContext.getOpPackageName());
    }

    @Test
    public void testSetTypes() {
        // We cannot test non wakeup alarms reliably because they are held up until the
        // device becomes interactive

        // test parameter type is RTC_WAKEUP
        mMockAlarmReceiver.reset();
        mWakeupTime = System.currentTimeMillis() + SNOOZE_DELAY;
        mAm.setExact(AlarmManager.RTC_WAKEUP, mWakeupTime, mSender);

        new PollingCheck(SNOOZE_DELAY + TIME_DELAY) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver.isAlarmed();
            }
        }.run();
        assertEquals(mMockAlarmReceiver.getRtcTime(), mWakeupTime, TIME_DELTA);

        // test parameter type is ELAPSED_REALTIME_WAKEUP
        mMockAlarmReceiver.reset();
        mWakeupTime = SystemClock.elapsedRealtime() + SNOOZE_DELAY;
        mAm.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, mWakeupTime, mSender);
        new PollingCheck(SNOOZE_DELAY + TIME_DELAY) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver.isAlarmed();
            }
        }.run();
        assertEquals(mMockAlarmReceiver.getElapsedTime(), mWakeupTime, TIME_DELTA);
    }

    @Test
    public void testAlarmTriggersImmediatelyIfSetTimeIsNegative() {
        // An alarm with a negative wakeup time should be triggered immediately.
        // This exercises a workaround for a limitation of the /dev/alarm driver
        // that would instead cause such alarms to never be triggered.
        mMockAlarmReceiver.reset();
        mWakeupTime = -1000;
        mAm.set(AlarmManager.RTC_WAKEUP, mWakeupTime, mSender);
        new PollingCheck(TIME_DELAY) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver.isAlarmed();
            }
        }.run();
    }

    @Test
    public void noBinderOverflowWithListenerSpam() {
        final long now = SystemClock.elapsedRealtime();
        for (int i = 0; i < 51_500; i++) {
            // Need it to be larger than 51200, which is the global reference table size.
            mAm.setExact(AlarmManager.ELAPSED_REALTIME, now + 10_000 + i * 1000, "spam-test",
                    mMockAlarmReceiver, null);
        }
        // Binder overflow should crash the system, so if we're out of the loop, the test passed.
    }

    /**
     * We run a few trials of an exact alarm that is placed within an inexact alarm's window of
     * opportunity, and mandate that the average observed delivery skew between the two be
     * statistically significant -- i.e. that the two alarms are not being coalesced.
     */
    @Test
    public void testExactAlarmBatching() throws InterruptedException {
        final long windowLength = 6_000;
        int deliveriesTogether = 0;
        for (int i = 0; i < 5; i++) {
            mMockAlarmReceiver.reset();
            mMockAlarmReceiver2.reset();

            final long now = SystemClock.elapsedRealtime();
            final long windowStart = now + 1000;
            final long exactStart = windowStart + windowLength / 2;

            mAm.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, windowStart, windowLength, mSender);
            mAm.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, exactStart, mSender2);

            // Wait until the end of the window.
            Thread.sleep(windowStart - now + windowLength);
            PollingCheck.waitFor(1000, mMockAlarmReceiver::isAlarmed,
                    "Inexact alarm did not fire by the end of the window");

            // If needed, wait until the time of the exact alarm.
            final long timeToExact = Math.max(exactStart - SystemClock.elapsedRealtime(), 0);
            Thread.sleep(timeToExact);
            PollingCheck.waitFor(1000, mMockAlarmReceiver2::isAlarmed,
                    "Exact alarm did not fire as expected");

            final long delta = Math.abs(
                    mMockAlarmReceiver2.getElapsedTime() - mMockAlarmReceiver.getElapsedTime());
            Log.i(TAG, "testExactAlarmBatching: [" + i + "]  delta = " + delta);
            if (delta < FAIL_DELTA) {
                deliveriesTogether++;
                if (deliveriesTogether > 1) {
                    fail("More than 1 deliveries with exact alarms close to inexact alarms");
                }
            }
        }
    }

    @Test
    public void testSetExactWithWorkSource() throws Exception {
        final int myUid = mContext.getPackageManager().getPackageUid(mContext.getOpPackageName(),
                0);
        final long futurityMs = 1000;
        mMockAlarmReceiver.reset();

        SystemUtil.runWithShellPermissionIdentity(
                () -> mAm.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + futurityMs, "test-tag", r -> r.run(),
                        new WorkSource(myUid), mMockAlarmReceiver),
                Manifest.permission.UPDATE_DEVICE_STATS);

        Thread.sleep(futurityMs);
        PollingCheck.waitFor(2000, mMockAlarmReceiver::isAlarmed,
                "Exact alarm with work source did not fire as expected");
    }

    @Test
    public void testSetExact() throws Exception {
        final long futurityMs = 1000;
        mMockAlarmReceiver.reset();
        mAm.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + futurityMs, "test-tag", mMockAlarmReceiver, null);

        Thread.sleep(futurityMs);
        PollingCheck.waitFor(2000, mMockAlarmReceiver::isAlarmed,
                "Exact alarm with work source did not fire as expected");
    }

    @Test
    public void testSetWindow() throws Exception {
        final long futurityMs = 1000;
        final long windowLength = 2000;
        mMockAlarmReceiver.reset();
        mAm.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + futurityMs, windowLength, "test-tag", Runnable::run,
                null, mMockAlarmReceiver);

        Thread.sleep(futurityMs + windowLength);
        PollingCheck.waitFor(4000, mMockAlarmReceiver::isAlarmed,
                "Window alarm did not fire as expected");
    }

    @Test
    public void testSetRepeating() {
        mMockAlarmReceiver.reset();
        mWakeupTime = System.currentTimeMillis() + TEST_ALARM_FUTURITY;
        mAm.setRepeating(AlarmManager.RTC_WAKEUP, mWakeupTime, REPEAT_PERIOD, mSender);

        // wait beyond the initial alarm's possible delivery window to verify that it fires the
        // first time
        new PollingCheck(TEST_ALARM_FUTURITY + REPEAT_PERIOD) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver.isAlarmed();
            }
        }.run();

        // Now reset the receiver and wait for the intended repeat alarm to fire as expected
        mMockAlarmReceiver.reset();
        new PollingCheck(REPEAT_PERIOD * 2) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver.isAlarmed();
            }
        }.run();

        mAm.cancel(mSender);
    }

    private static boolean isDeviceIdleEnabled() {
        final String output = SystemUtil.runShellCommand("cmd deviceidle enabled deep").trim();
        return Integer.parseInt(output) != 0;
    }

    private void toggleIdleMode(boolean on) {
        SystemUtil.runShellCommand("cmd deviceidle " + (on ? "force-idle deep" : "unforce"));
        if (!on) {
            // Make sure the device doesn't stay idle, even after unforcing.
            SystemUtil.runShellCommand("cmd deviceidle motion");
        }
        PollingCheck.waitFor(10_000, () -> (on == mPowerManager.isDeviceIdleMode()),
                "Could not set doze state to " + on);
    }

    @Test(expected = SecurityException.class)
    public void testSetPrioritizedWithoutPermission() {
        mAm.setPrioritized(AlarmManager.ELAPSED_REALTIME_WAKEUP, 20, 10,
                "testSetPrioritizedWithoutPermission", r -> r.run(), mMockAlarmReceiver);
    }

    @Test
    public void testSetPrioritized() throws InterruptedException {
        assumeTrue("Doze not supported on this device", isDeviceIdleEnabled());

        mMockAlarmReceiver.reset();
        mMockAlarmReceiver2.reset();

        final long trigger1 = SystemClock.elapsedRealtime() + 1000;
        final long trigger2 = SystemClock.elapsedRealtime() + 2000;
        SystemUtil.runWithShellPermissionIdentity(
                () -> mAm.setPrioritized(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger1, 10,
                        "test-1", r -> r.run(), mMockAlarmReceiver));
        SystemUtil.runWithShellPermissionIdentity(
                () -> mAm.setPrioritized(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger2, 10,
                        "test-2", r -> r.run(), mMockAlarmReceiver2));

        Thread.sleep(2010);
        PollingCheck.waitFor(1000,
                () -> (mMockAlarmReceiver.isAlarmed() && mMockAlarmReceiver2.isAlarmed()));

        toggleIdleMode(true);
        // Ensure no previous alarm in doze throttles the next one.
        Thread.sleep(PRIORITY_ALARM_DELAY);
        mMockAlarmReceiver.reset();
        mMockAlarmReceiver2.reset();

        final long trigger3 = SystemClock.elapsedRealtime() + 1000;
        final long trigger4 = SystemClock.elapsedRealtime() + 2000;
        SystemUtil.runWithShellPermissionIdentity(
                () -> mAm.setPrioritized(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger3, 10,
                        "test-3", r -> r.run(), mMockAlarmReceiver));
        SystemUtil.runWithShellPermissionIdentity(
                () -> mAm.setPrioritized(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger4, 10,
                        "test-4", r -> r.run(), mMockAlarmReceiver2));
        Thread.sleep(1010);
        PollingCheck.waitFor(1000, mMockAlarmReceiver::isAlarmed,
                "First alarm not received as expected in doze");

        Thread.sleep(1000);
        assertFalse("Second alarm fired prematurely while in doze",
                mMockAlarmReceiver2.isAlarmed());

        final long timeToNextAlarm = mMockAlarmReceiver.getElapsedTime() + PRIORITY_ALARM_DELAY
                - SystemClock.elapsedRealtime();
        Thread.sleep(Math.max(0, timeToNextAlarm));
        PollingCheck.waitFor(1000, mMockAlarmReceiver2::isAlarmed,
                "Second alarm not received as expected in doze");


        final long firstAlarmTime = mMockAlarmReceiver.getElapsedTime();
        final long secondAlarmTime = mMockAlarmReceiver2.getElapsedTime();
        assertTrue("First alarm: " + firstAlarmTime + " and second alarm: " + secondAlarmTime
                        + " not separated enough",
                (secondAlarmTime - firstAlarmTime) > (PRIORITY_ALARM_DELAY - FAIL_DELTA));
    }

    @Test
    public void testCancel() {
        mMockAlarmReceiver.reset();
        mMockAlarmReceiver2.reset();

        // set two alarms
        final long now = System.currentTimeMillis();
        final long when1 = now + TEST_ALARM_FUTURITY;
        mAm.setExact(AlarmManager.RTC_WAKEUP, when1, mSender);
        final long when2 = when1 + TIME_DELTA; // will fire after when1's target time
        mAm.setExact(AlarmManager.RTC_WAKEUP, when2, mSender2);

        // cancel the earlier one
        mAm.cancel(mSender);

        // and verify that only the later one fired
        new PollingCheck(when2 - now + TIME_DELAY) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver2.isAlarmed();
            }
        }.run();

        assertFalse(mMockAlarmReceiver.isAlarmed());
    }

    @Test
    public void testCancelAll() throws InterruptedException {
        mMockAlarmReceiver.reset();
        mMockAlarmReceiver2.reset();

        final long when1Rtc = System.currentTimeMillis() + TEST_ALARM_FUTURITY;
        mAm.setExact(AlarmManager.RTC_WAKEUP, when1Rtc, mSender);

        final long nowElapsed = SystemClock.elapsedRealtime();
        // setting the second one to fire after the first one
        final long when2Elapsed = nowElapsed + TEST_ALARM_FUTURITY + TIME_DELTA;
        mAm.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, when2Elapsed, "test-tag",
                mMockAlarmReceiver2, null);

        mAm.cancelAll();

        // wait till some time past the scheduled expiration of the second one
        Thread.sleep(when2Elapsed - nowElapsed + TIME_DELAY);

        assertFalse(mMockAlarmReceiver.isAlarmed() || mMockAlarmReceiver2.isAlarmed());
    }

    @Test
    public void testSetAlarmClock() {
        assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.LOLLIPOP));

        mMockAlarmReceiver.reset();
        mMockAlarmReceiver2.reset();

        // Set first alarm clock.
        final long wakeupTimeFirst = System.currentTimeMillis()
                + 2 * TEST_ALARM_FUTURITY;
        mAm.setAlarmClock(new AlarmClockInfo(wakeupTimeFirst, null), mSender);

        // Verify getNextAlarmClock returns first alarm clock.
        AlarmClockInfo nextAlarmClock = mAm.getNextAlarmClock();
        assertEquals(wakeupTimeFirst, nextAlarmClock.getTriggerTime());
        assertNull(nextAlarmClock.getShowIntent());

        // Set second alarm clock, earlier than first.
        final long wakeupTimeSecond = System.currentTimeMillis()
                + TEST_ALARM_FUTURITY;
        PendingIntent showIntentSecond = PendingIntent.getBroadcast(mContext, 0,
                new Intent(mContext, BasicApiTests.class).setAction("SHOW_INTENT"),
                PendingIntent.FLAG_IMMUTABLE);
        mAm.setAlarmClock(new AlarmClockInfo(wakeupTimeSecond, showIntentSecond),
                mSender2);

        // Verify getNextAlarmClock returns second alarm clock now.
        nextAlarmClock = mAm.getNextAlarmClock();
        assertEquals(wakeupTimeSecond, nextAlarmClock.getTriggerTime());
        assertEquals(showIntentSecond, nextAlarmClock.getShowIntent());

        // Cancel second alarm.
        mAm.cancel(mSender2);

        // Verify getNextAlarmClock returns first alarm clock again.
        nextAlarmClock = mAm.getNextAlarmClock();
        assertEquals(wakeupTimeFirst, nextAlarmClock.getTriggerTime());
        assertNull(nextAlarmClock.getShowIntent());

        // Wait for first alarm to trigger.
        assertFalse(mMockAlarmReceiver.isAlarmed());
        new PollingCheck(2 * TEST_ALARM_FUTURITY + TIME_DELAY) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver.isAlarmed();
            }
        }.run();

        // Verify first alarm fired at the right time.
        assertEquals(mMockAlarmReceiver.getRtcTime(), wakeupTimeFirst, TIME_DELTA);

        // Verify second alarm didn't fire.
        assertFalse(mMockAlarmReceiver2.isAlarmed());

        // Verify the next alarm is not returning neither the first nor the second alarm.
        nextAlarmClock = mAm.getNextAlarmClock();
        assertNotEquals(wakeupTimeFirst,
                nextAlarmClock != null ? nextAlarmClock.getTriggerTime() : 0);
        assertNotEquals(wakeupTimeSecond,
                nextAlarmClock != null ? nextAlarmClock.getTriggerTime() : 0);
    }

    /**
     * this class receives alarm from AlarmManagerTest
     */
    public static class MockAlarmReceiver extends BroadcastReceiver
            implements AlarmManager.OnAlarmListener {
        private final Object mSync = new Object();
        public final String mTargetAction;

        @GuardedBy("mSync")
        private boolean mAlarmed = false;
        @GuardedBy("mSync")
        private long mElapsedTime = 0;
        @GuardedBy("mSync")
        private long mRtcTime = 0;

        public MockAlarmReceiver(String targetAction) {
            mTargetAction = targetAction;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(mTargetAction)) {
                synchronized (mSync) {
                    mAlarmed = true;
                    mElapsedTime = SystemClock.elapsedRealtime();
                    mRtcTime = System.currentTimeMillis();
                }
            }
        }

        public long getElapsedTime() {
            synchronized (mSync) {
                return mElapsedTime;
            }
        }

        public long getRtcTime() {
            synchronized (mSync) {
                return mRtcTime;
            }
        }

        public void reset() {
            synchronized (mSync) {
                mAlarmed = false;
                mRtcTime = mElapsedTime = 0;
            }
        }

        public boolean isAlarmed() {
            synchronized (mSync) {
                return mAlarmed;
            }
        }

        @Override
        public void onAlarm() {
            onReceive(null, new Intent(mTargetAction));
        }
    }
}
