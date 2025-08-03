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

package android.platform.test.scenario.generic;

import android.os.SystemClock;
import android.platform.test.option.IntegerOption;
import android.platform.test.option.LongOption;
import android.platform.test.rule.NaturalOrientationRule;
import android.platform.test.scenario.annotation.Scenario;
import android.util.Log;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Do basic math operations for a specified amount of time. */
@Scenario
@RunWith(JUnit4.class)
public class MathWork {
    private static final String LOG_TAG = MathWork.class.getSimpleName();

    @ClassRule public static final NaturalOrientationRule ORIENT = new NaturalOrientationRule();

    @Rule
    public final LongOption mDurationMs = new LongOption("math-work-duration_ms").setDefault(5000L);

    @Rule
    public final LongOption mIntervalMs = new LongOption("math-work-interval_ms").setDefault(100L);

    @Rule
    public final IntegerOption mThreads = new IntegerOption("math-work-threads").setDefault(20);

    @Rule
    public final IntegerOption mOperations =
            new IntegerOption("math-work-operations").setDefault(1000);

    @Test
    public void testDoMath() {
        long startTime = SystemClock.uptimeMillis();

        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(mThreads.get());
        for (int i = 0; i < mThreads.get(); i++) {
            Log.v(
                    LOG_TAG,
                    String.format(
                            "Scheduling a repeated math task with operations: %d and interval: %d",
                            mOperations.get(), mIntervalMs.get()));
            executor.scheduleAtFixedRate(
                    new MathRunnable(mOperations.get()),
                    0,
                    mIntervalMs.get(),
                    TimeUnit.MILLISECONDS);
        }

        SystemClock.sleep(
                Math.max(0, mDurationMs.get() - (SystemClock.uptimeMillis() - startTime)));
        executor.shutdownNow();
        Log.v(LOG_TAG, String.format("Finished performing %d ms of work.", mDurationMs.get()));
    }

    public static class MathRunnable implements Runnable {
        private static final double MULTIPLIER = 1.00001;

        private int mOperations;

        public MathRunnable(int operations) {
            mOperations = operations;
        }

        @Override
        public void run() {
            double x = MULTIPLIER;
            for (int i = 0; i < mOperations; i++) {
                x *= MULTIPLIER;
            }
        }
    }
}
