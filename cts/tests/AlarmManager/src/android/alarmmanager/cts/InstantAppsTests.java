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
 * limitations under the License.
 */

package android.alarmmanager.cts;

import static android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP;
import static android.app.AlarmManager.RTC_WAKEUP;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.alarmmanager.util.AlarmManagerDeviceConfigHelper;
import android.app.AlarmManager;
import android.content.Context;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeInstant;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests that alarm manager works as expected with instant apps
 */
@AppModeInstant
@RunWith(AndroidJUnit4.class)
public class InstantAppsTests {
    private static final String TAG = "AlarmManagerInstantTests";
    private static final long WINDOW_LENGTH = 500;
    private static final long WAIT_TIMEOUT = 5_000;

    private AlarmManager mAlarmManager;
    private Context mContext;
    private AlarmManagerDeviceConfigHelper mConfigHelper = new AlarmManagerDeviceConfigHelper();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mAlarmManager = mContext.getSystemService(AlarmManager.class);
        assumeTrue(mContext.getPackageManager().isInstantApp());
    }

    @Before
    public void updateAlarmManagerSettings() {
        mConfigHelper.with("min_futurity", 0L)
                .with("min_window", 0L)
                .commitAndAwaitPropagation();
    }

    @Test
    public void elapsedRealtimeAlarm() throws Exception {
        final long futurity = 2500;
        final long triggerElapsed = SystemClock.elapsedRealtime() + futurity;
        final CountDownLatch latch = new CountDownLatch(1);
        mAlarmManager.setWindow(ELAPSED_REALTIME_WAKEUP, triggerElapsed, WINDOW_LENGTH, TAG,
                () -> latch.countDown(), null);
        Thread.sleep(futurity + WINDOW_LENGTH);
        assertTrue("Alarm did not fire as expected",
                latch.await(WAIT_TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void rtcAlarm() throws Exception {
        final long futurity = 2500;
        final long triggerRtc = System.currentTimeMillis() + futurity;
        final CountDownLatch latch = new CountDownLatch(1);
        mAlarmManager.setWindow(RTC_WAKEUP, triggerRtc, WINDOW_LENGTH, TAG, () -> latch.countDown(),
                null);
        Thread.sleep(futurity + WINDOW_LENGTH);
        assertTrue("Alarm did not fire as expected",
                latch.await(WAIT_TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @After
    public void deleteAlarmManagerSettings() {
        mConfigHelper.restoreAll();
    }
}
