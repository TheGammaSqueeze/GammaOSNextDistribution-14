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
package android.platform.test.util;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.Assert.fail;

import android.os.SystemClock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TimedActionTest {
    // Note that 10 milliseconds is very short and the surrounding code may take some time to
    // execute. We have to use a scaling factor (multiplier) for tests that is large enough to
    // ensure we have slept and waited after and small enough that the test doesn't get too long.
    private static final long VERY_SHORT_DURATION_MS = 10L;
    private static final int ACTION_TIME_MULTIPLIER = 10;

    @Test
    public void timedActions_runsExpectedDuration_noArgs() {
        long startTimeMs = SystemClock.elapsedRealtime();
        TimedAction.runWithIntendedDuration(
                this::doNothingWithoutArgs, VERY_SHORT_DURATION_MS * ACTION_TIME_MULTIPLIER);
        assertThat(SystemClock.elapsedRealtime() - startTimeMs)
                .isAtLeast(VERY_SHORT_DURATION_MS * ACTION_TIME_MULTIPLIER);
    }

    @Test
    public void timedActions_runsExpectedDuration_withArgs() {
        long startTimeMs = SystemClock.elapsedRealtime();
        TimedAction.runWithIntendedDuration(
                () -> doNothingWithArgs(VERY_SHORT_DURATION_MS),
                VERY_SHORT_DURATION_MS * ACTION_TIME_MULTIPLIER);
        assertThat(SystemClock.elapsedRealtime() - startTimeMs)
                .isAtLeast(VERY_SHORT_DURATION_MS * ACTION_TIME_MULTIPLIER);
    }

    /** Tests that timed actions will work for non-void methods when phrased as lambdas. */
    @Test
    public void timedActions_runsExpectedDuration_nonVoid() {
        long startTimeMs = SystemClock.elapsedRealtime();
        TimedAction.runWithIntendedDuration(
                () -> returnInt(), VERY_SHORT_DURATION_MS * ACTION_TIME_MULTIPLIER);
        assertThat(SystemClock.elapsedRealtime() - startTimeMs)
                .isAtLeast(VERY_SHORT_DURATION_MS * ACTION_TIME_MULTIPLIER);
    }

    /** Tests that exceptions will be propagated up from the underlying methods. */
    @Test
    public void timedActions_throwExceptions() {
        try {
            TimedAction.runWithIntendedDuration(
                    () -> { throw new RuntimeException("Fail!"); }, VERY_SHORT_DURATION_MS);
            fail("Exception should have been thrown and reported from action.");
        } catch (Exception e) { }
    }

    private void doNothingWithoutArgs() {
        SystemClock.sleep(VERY_SHORT_DURATION_MS);
    }

    private void doNothingWithArgs(long durationMs) {
        SystemClock.sleep(durationMs);
    }

    private int returnInt() {
        return 1;
    }
}
