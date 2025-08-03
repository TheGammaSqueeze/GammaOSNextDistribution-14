/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.os.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;
import android.platform.test.annotations.IgnoreUnderRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;

import org.junit.Rule;
import org.junit.Test;

public class SystemClockTest {
    @Rule public RavenwoodRule mRavenwood = new RavenwoodRule();

    @Test
    @IgnoreUnderRavenwood(reason = "Requires kernel support")
    public void testCurrentThreadTimeMillis() throws InterruptedException {

        long start = SystemClock.currentThreadTimeMillis();
        Thread.sleep(100);
        long end = SystemClock.currentThreadTimeMillis();
        assertFalse(end - 100 >= start);

    }

    @Test
    public void testElapsedRealtime() throws InterruptedException {

        long start = SystemClock.elapsedRealtime();
        Thread.sleep(100);
        long end = SystemClock.elapsedRealtime();
        assertTrue(end - 100 >= start);

    }

    @Test
    @IgnoreUnderRavenwood(reason = "Requires kernel support")
    public void testSetCurrentTimeMillis() {

        long start = SystemClock.currentThreadTimeMillis();
        boolean actual = SystemClock.setCurrentTimeMillis(start + 10000);
        assertFalse(actual);
        // This test need to be done in permission test.

    }

    @Test
    @IgnoreUnderRavenwood(reason = "Requires kernel support")
    public void testSleep_currentThreadTimeMillis() {
        long start = SystemClock.currentThreadTimeMillis();
        SystemClock.sleep(100);
        long end = SystemClock.currentThreadTimeMillis();
        assertFalse(end - 100 >= start);
    }

    @Test
    public void testSleep_elapsedRealtime() {
        long start = SystemClock.elapsedRealtime();
        SystemClock.sleep(100);
        long end = SystemClock.elapsedRealtime();
        assertTrue(end - 100 >= start);
    }

    @Test
    public void testSleep_uptimeMillis() {
        long start = SystemClock.uptimeMillis();
        SystemClock.sleep(100);
        long end = SystemClock.uptimeMillis();
        assertTrue(end - 100 >= start);
    }

    @Test
    public void testUptimeMillis() throws InterruptedException {
        long start = SystemClock.uptimeMillis();
        Thread.sleep(100);
        long end = SystemClock.uptimeMillis();
        assertTrue(end - 100 >= start);
    }

    @Test
    public void testElapsedVsUptime() throws InterruptedException {
        // Elapsed also counts time in deep sleep, so it should always be more than uptime
        assertThat(SystemClock.uptimeMillis()).isAtMost(SystemClock.elapsedRealtime());
    }

    @Test
    public void testElapsedRealtime_Valid() {
        assertThat(SystemClock.elapsedRealtime()).isGreaterThan(0L);
        assertThat(SystemClock.elapsedRealtimeNanos()).isGreaterThan(0L);
    }

    @Test
    public void testUptime_Valid() {
        assertThat(SystemClock.uptimeMillis()).isGreaterThan(0L);
    }
}
