/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.devicelockcontroller.receivers;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.ThreadUtils;

import com.google.common.collect.Range;
import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.time.Clock;
import java.time.Instant;

@RunWith(RobolectricTestRunner.class)
public final class RecordBootTimestampReceiverTest {
    private static final Intent INTENT = new Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED);
    private RecordBootTimestampReceiver mReceiver;
    private final TestDeviceLockControllerApplication mTestApplication =
            ApplicationProvider.getApplicationContext();
    // Maximum allowed drift due to difference in sampling between current time and elapsed time.
    private static final long MAX_DRIFT = 500;

    @Before
    public void setUp() {
        mReceiver = new RecordBootTimestampReceiver();
    }

    @Test
    public void onReceive_shouldRecordBootTime() {
        long bootTime = Instant.now(Clock.systemUTC())
                .minusMillis(SystemClock.elapsedRealtime()).toEpochMilli();

        // Receiver records boot time to user parameters.
        mReceiver.onReceive(mTestApplication, INTENT);

        Futures.getUnchecked(
                Futures.submit(
                        () -> assertThat(
                                UserParameters.getBootTimeMillis(mTestApplication))
                                .isIn(Range.closed(bootTime, bootTime + MAX_DRIFT)),
                        ThreadUtils.getSequentialSchedulerExecutor()));
    }
}
