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

package com.android.server.cts.device.statsdalarmhelper;

import static org.junit.Assert.assertTrue;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AlarmAtomTests {
    private static final String TAG = AlarmAtomTests.class.getSimpleName();

    private static final Context sContext = InstrumentationRegistry.getTargetContext();
    private final AlarmManager mAlarmManager = sContext.getSystemService(AlarmManager.class);

    @Test
    public void testWakeupAlarm() throws Exception {
        String action = "android.cts.statsdatom.testWakeupAlarm";

        CountDownLatch onReceiveLatch = new CountDownLatch(1);
        PendingIntent pi = PendingIntent.getBroadcast(sContext, 0, new Intent(action)
                        .setPackage(sContext.getPackageName())
                        .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                PendingIntent.FLAG_IMMUTABLE);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Received broadcast.");
                onReceiveLatch.countDown();
            }
        };
        sContext.registerReceiver(receiver, new IntentFilter(action),
                Context.RECEIVER_EXPORTED_UNAUDITED);
        try {
            mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 5_000, pi);
            assertTrue("Did not receive alarm in specified time!",
                    onReceiveLatch.await(30, TimeUnit.SECONDS));
        } finally {
            sContext.unregisterReceiver(receiver);
        }
    }

    @Test
    public void testAlarmScheduled() {
        String name = "android.cts.statsdatom.testAlarmScheduled";

        PendingIntent pi1 = PendingIntent.getBroadcast(sContext, 1, new Intent(name),
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pi2 = PendingIntent.getBroadcast(sContext, 2, new Intent(name),
                PendingIntent.FLAG_IMMUTABLE);

        final long trigger1 = SystemClock.elapsedRealtime() + 5_000;
        final long trigger2 = System.currentTimeMillis() + 5_200;
        mAlarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger1, 10_000, pi1);
        mAlarmManager.setWindow(AlarmManager.RTC, trigger2, 10_000, pi2);
    }

    @Test
    public void testExactAlarmScheduled() {
        String name = "android.cts.statsdatom.testExactAlarmScheduled";

        PendingIntent pi1 = PendingIntent.getBroadcast(sContext, 1, new Intent(name),
                PendingIntent.FLAG_IMMUTABLE);

        final long trigger1 = SystemClock.elapsedRealtime() + 5_000;
        mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger1, pi1);
    }

    @Test
    public void testPendingAlarmInfo() {
        // Just schedule esoteric alarms whose counts can be verified in the pulled atom.
        PendingIntent activity = PendingIntent.getActivity(sContext, 0,
                new Intent("com.irrelevant.activity"), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent fgs1 = PendingIntent.getForegroundService(sContext, 1,
                new Intent("com.irrelevant.fgs1"), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent fgs2 = PendingIntent.getForegroundService(sContext, 2,
                new Intent("com.irrelevant.fgs2"), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent service = PendingIntent.getService(sContext, 0,
                new Intent("com.irrelevant.service"), PendingIntent.FLAG_IMMUTABLE);

        final long farTriggerRtc = System.currentTimeMillis() + 600_000;
        final long farTriggerElapsed = SystemClock.elapsedRealtime() + 600_000;
        final long neverTriggerElapsed = SystemClock.elapsedRealtime() + 10 * 365 * 86400 * 1000L;

        mAlarmManager.set(AlarmManager.RTC_WAKEUP, farTriggerRtc, "testPendingAlarmInfo",
                () -> Log.e(TAG, "Should not have fired"), null);
        mAlarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(farTriggerRtc, activity),
                activity);
        mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, farTriggerElapsed,
                fgs1);
        mAlarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, farTriggerElapsed, 60_000,
                fgs2);
        mAlarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, neverTriggerElapsed,
                service);
    }
}
