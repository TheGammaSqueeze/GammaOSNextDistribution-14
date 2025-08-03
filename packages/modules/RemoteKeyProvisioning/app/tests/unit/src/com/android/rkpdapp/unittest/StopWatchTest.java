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

package com.android.rkpdapp.unittest;

import static com.google.common.truth.Truth.assertThat;

import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.rkpdapp.utils.StopWatch;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StopWatchTest {
    @Test
    public void testIsRunning() {
        StopWatch stopWatch = new StopWatch("test");
        try (stopWatch) {
            assertThat(stopWatch.isRunning()).isFalse();
            stopWatch.start();
            assertThat(stopWatch.isRunning()).isTrue();
        }
        assertThat(stopWatch.isRunning()).isFalse();
    }

    @Test
    public void testDuration() throws Exception {
        StopWatch stopWatch = new StopWatch("test");
        assertThat(stopWatch.getElapsedMillis()).isEqualTo(0);

        final long preStart = SystemClock.elapsedRealtime();
        stopWatch.start();
        final long postStart = SystemClock.elapsedRealtime();

        Thread.sleep(10);

        final long preStop = SystemClock.elapsedRealtime();
        stopWatch.stop();
        final long postStop = SystemClock.elapsedRealtime();

        final int minMillis = (int) (preStop - postStart);
        final int maxMillis = (int) (postStop - preStart);

        assertThat(stopWatch.getElapsedMillis()).isAtLeast(minMillis);
        assertThat(stopWatch.getElapsedMillis()).isAtMost(maxMillis);
    }

    @Test
    public void testIsRestartable() throws Exception {
        StopWatch stopWatch = new StopWatch("restarted");
        stopWatch.start();
        Thread.sleep(10);
        stopWatch.stop();

        int firstStopTime = stopWatch.getElapsedMillis();

        stopWatch.start();
        Thread.sleep(10);
        stopWatch.stop();

        assertThat(stopWatch.getElapsedMillis()).isGreaterThan(firstStopTime);
    }

    @Test
    public void testNoops() {
        StopWatch stopWatch = new StopWatch("test");
        try (stopWatch) {
            // multiple starts don't do anything
            stopWatch.start();
            assertThat(stopWatch.isRunning()).isTrue();
            stopWatch.start();
            assertThat(stopWatch.isRunning()).isTrue();
        }

        // multiple stops don't do anything
        assertThat(stopWatch.isRunning()).isFalse();
        stopWatch.stop();
        assertThat(stopWatch.isRunning()).isFalse();
    }
}
