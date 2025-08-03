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

package android.os.cts;

import static android.app.time.cts.shell.DeviceConfigKeys.NAMESPACE_SYSTEM_TIME;
import static android.app.time.cts.shell.DeviceConfigKeys.TimeDetector.KEY_TIME_DETECTOR_LOWER_BOUND_MILLIS_OVERRIDE;
import static android.app.time.cts.shell.DeviceConfigShellHelper.SYNC_DISABLED_MODE_UNTIL_REBOOT;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.time.cts.shell.DeviceConfigShellHelper;
import android.app.time.cts.shell.DeviceShellCommandExecutor;
import android.app.time.cts.shell.TimeDetectorShellHelper;
import android.app.time.cts.shell.TimeDetectorShellHelper.TestUnixEpochTime;
import android.app.time.cts.shell.device.InstrumentationShellCommandExecutor;
import android.os.SystemClock;
import android.util.Range;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Tests for {@link SystemClock#currentNetworkTimeClock()}. */
@RunWith(AndroidJUnit4.class)
public class SystemClockNetworkTimeTest {

    private static final int ARBITRARY_UNCERTAINTY_MILLIS = 100;

    /**
     * A "valid" time to use for tests. This time has to be above the system's lower time bound to
     * be accepted when the time_detector service is handling system clock network time signal.
     * Date/time: 20300722 00:00:00 UTC.
     */
    private static final Instant VALID_SYSTEM_TIME = Instant.ofEpochSecond(1910908800);

    /**
     * A system clock lower bound override value that ensures {@link #VALID_SYSTEM_TIME} will be
     * accepted by services that observe the lower bound threshold. The value is arbitrary except
     * it must be before {@link #VALID_SYSTEM_TIME}.
     */
    private static final Instant SYSTEM_LOWER_TIME_BOUND =
            VALID_SYSTEM_TIME.minus(100, ChronoUnit.DAYS);

    private DeviceShellCommandExecutor mShellCommandExecutor;
    private TimeDetectorShellHelper mTimeDetectorShellHelper;
    private DeviceConfigShellHelper mDeviceConfigShellHelper;
    private DeviceConfigShellHelper.PreTestState mPreTestDeviceConfigState;
    private Instant mSetupInstant;
    private long mSetupElapsedRealtimeMillis;


    @Before
    public void setUp() throws Exception {
        mSetupInstant = Instant.now();
        mSetupElapsedRealtimeMillis = SystemClock.elapsedRealtime();
        mShellCommandExecutor = new InstrumentationShellCommandExecutor(
                InstrumentationRegistry.getInstrumentation().getUiAutomation());
        mTimeDetectorShellHelper = new TimeDetectorShellHelper(mShellCommandExecutor);
        mDeviceConfigShellHelper = new DeviceConfigShellHelper(mShellCommandExecutor);
        mPreTestDeviceConfigState = mDeviceConfigShellHelper.setSyncModeForTest(
                SYNC_DISABLED_MODE_UNTIL_REBOOT, NAMESPACE_SYSTEM_TIME);
    }

    @After
    public void tearDown() throws Exception {
        mTimeDetectorShellHelper.clearSystemClockNetworkTime();

        // If the system clock has been set to a time before or significantly after mSetupInstant as
        // a result of running tests, make best efforts to restore it to close to what it was to
        // avoid interfering with later tests, e.g. the refresh above might have failed, and tests
        // might be leaving the system clock set to a time in history / in the future that could
        // cause root cert validity checks to fail. The system clock will have been left unchanged
        // by tests if NTP isn't being used as the primary time zone detection mechanism or if the
        // times used in tests were obviously invalid and rejected by the time detector.
        Instant currentSystemClockTime = Instant.now();
        if (currentSystemClockTime.isBefore(mSetupInstant)
                || currentSystemClockTime.isAfter(mSetupInstant.plus(Duration.ofHours(1)))) {
            // Adjust mSetupInstant for (approximately) time elapsed.
            Duration timeElapsed = Duration.ofMillis(
                    SystemClock.elapsedRealtime() - mSetupElapsedRealtimeMillis);
            Instant newNow = mSetupInstant.plus(timeElapsed);

            // Set the system clock directly as there is currently no way easy way to inject time
            // suggestions into the time_detector service from the commandline.
            mShellCommandExecutor.executeToTrimmedString(
                    "cmd alarm set-time " + newNow.toEpochMilli());
        }

        mDeviceConfigShellHelper.restoreDeviceConfigStateForTest(mPreTestDeviceConfigState);
    }

    @ApiTest(apis = {"android.os.SystemClock#currentNetworkTimeClock"})
    @Test
    public void testCurrentNetworkTimeClock() throws Exception {
        // Set the device's lower bound for acceptable system clock time to avoid the canned test
        // time VALID_SYSTEM_TIME being rejected.
        mDeviceConfigShellHelper.put(
                NAMESPACE_SYSTEM_TIME, KEY_TIME_DETECTOR_LOWER_BOUND_MILLIS_OVERRIDE,
                Long.toString(SYSTEM_LOWER_TIME_BOUND.toEpochMilli()));

        // Verify the case where the device hasn't made a network time request yet.
        // Clear the current system clock network time value and verify it throws an exception.
        mTimeDetectorShellHelper.clearSystemClockNetworkTime();
        assertThrows(DateTimeException.class, () -> SystemClock.currentNetworkTimeClock().millis());

        // Inject a test time signal into the system server that should be used to provide the
        // system clock network time.
        long elapsedRealtimeMillis = SystemClock.elapsedRealtime();
        TestUnixEpochTime unixEpochTime = new TestUnixEpochTime(
                elapsedRealtimeMillis, VALID_SYSTEM_TIME.toEpochMilli());
        mTimeDetectorShellHelper.setSystemClockNetworkTime(
                new TimeDetectorShellHelper.TestNetworkTime(
                        unixEpochTime, ARBITRARY_UNCERTAINTY_MILLIS));

        assertNetworkTimeIsUsed(unixEpochTime);

        // Simulate some time passing and verify that SystemClock returns an updated time
        // using the same network time value as before.
        final long passedDurationMillis = 100L;
        Thread.sleep(passedDurationMillis);

        // Verify the same network time value is being used.
        assertNetworkTimeIsUsed(unixEpochTime);
    }

    /** Verify the given value is in range [lower, upper] */
    private static void assertInRange(String tag, long value, long lower, long upper) {
        final Range<Long> range = new Range<>(lower, upper);
        assertTrue(tag + ": " + value + " is not within range [" + lower + ", " + upper + "]",
                range.contains(value));
    }

    /**
     * Asserts that {@code expectedUnixEpochTime} is being used by the platform for {@link
     * SystemClock#currentNetworkTimeClock()}.
     */
    private void assertNetworkTimeIsUsed(TestUnixEpochTime expectedUnixEpochTime) {
        final long beforeQueryElapsedMillis = SystemClock.elapsedRealtime();
        final long networkEpochMillis = SystemClock.currentNetworkTimeClock().millis();
        final long afterQueryElapsedMillis = SystemClock.elapsedRealtime();

        // Calculate the lower/upper bound based on the elapsed time of refreshing.
        final long lowerBoundNetworkEpochMillis = expectedUnixEpochTime.unixEpochTimeMillis
                + (beforeQueryElapsedMillis - expectedUnixEpochTime.elapsedRealtimeMillis);
        final long upperBoundNetworkEpochMillis = expectedUnixEpochTime.unixEpochTimeMillis
                + (afterQueryElapsedMillis - expectedUnixEpochTime.elapsedRealtimeMillis);
        assertInRange("Current network time", networkEpochMillis, lowerBoundNetworkEpochMillis,
                upperBoundNetworkEpochMillis);
    }
}
