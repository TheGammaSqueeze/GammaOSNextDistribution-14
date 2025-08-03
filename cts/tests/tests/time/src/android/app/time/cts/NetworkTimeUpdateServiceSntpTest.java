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

package android.app.time.cts;

import static android.app.time.cts.shell.DeviceConfigKeys.NAMESPACE_SYSTEM_TIME;
import static android.app.time.cts.shell.DeviceConfigKeys.TimeDetector.KEY_TIME_DETECTOR_LOWER_BOUND_MILLIS_OVERRIDE;
import static android.app.time.cts.shell.DeviceConfigShellHelper.SYNC_DISABLED_MODE_UNTIL_REBOOT;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.time.cts.shell.DeviceConfigShellHelper;
import android.app.time.cts.shell.DeviceShellCommandExecutor;
import android.app.time.cts.shell.NetworkTimeUpdateServiceShellHelper;
import android.app.time.cts.shell.TimeDetectorShellHelper;
import android.app.time.cts.shell.TimeDetectorShellHelper.TestNetworkTime;
import android.app.time.cts.shell.device.InstrumentationShellCommandExecutor;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.util.Range;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ThrowingSupplier;

import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

@RunWith(AndroidJUnit4.class)
public class NetworkTimeUpdateServiceSntpTest {
    /** Mocked server response. Refer to {@link android.net.SntpClientTest}. */
    private static final String MOCKED_NTP_RESPONSE =
            "240206ec"
                    + "00000165"
                    + "000000b2"
                    + "ddfd4729"
                    + "d9ca9446820a5000"
                    + "d9ca9451938a3771"
                    + "d9ca945194bd3fff"
                    + "d9ca945194bd4001";

    /**
     * The midpoint between d9ca945194bd3fff and d9ca945194bd4001, d9ca9451.94bd4000 represents
     * (decimal) 1444943313.581012726 seconds in the Unix epoch, which is
     * ~2015-10-15 21:08:33.581 UTC.
     */
    private static final long MOCKED_NTP_TIMESTAMP = 1444943313581L;

    /**
     * A system clock lower bound override value that ensures {@link #MOCKED_NTP_TIMESTAMP} will be
     * accepted by services that observe the lower bound threshold. The value is arbitrary except
     * it must be before {@link #MOCKED_NTP_TIMESTAMP}.
     */
    private static final long SYSTEM_LOWER_TIME_BOUND =
            MOCKED_NTP_TIMESTAMP - (24 * 60 * 60 * 1000);
    private static final long TEST_NTP_TIMEOUT_MILLIS = 300L;

    private DeviceShellCommandExecutor mShellCommandExecutor;
    private NetworkTimeUpdateServiceShellHelper mNetworkTimeUpdateServiceShellHelper;
    private TimeDetectorShellHelper mTimeDetectorShellHelper;
    private DeviceConfigShellHelper mDeviceConfigShellHelper;
    private DeviceConfigShellHelper.PreTestState mPreTestDeviceConfigState;
    private Instant mSetupInstant;
    private long mSetupElapsedRealtimeMillis;
    private boolean mTearDownRequired;

    @Before
    public void setUp() throws Exception {
        mShellCommandExecutor = new InstrumentationShellCommandExecutor(
                InstrumentationRegistry.getInstrumentation().getUiAutomation());
        mNetworkTimeUpdateServiceShellHelper =
                new NetworkTimeUpdateServiceShellHelper(mShellCommandExecutor);

        skipOnFormFactorsWithoutService(mNetworkTimeUpdateServiceShellHelper);

        mSetupInstant = Instant.now();
        mSetupElapsedRealtimeMillis = SystemClock.elapsedRealtime();
        mTimeDetectorShellHelper = new TimeDetectorShellHelper(mShellCommandExecutor);
        mDeviceConfigShellHelper = new DeviceConfigShellHelper(mShellCommandExecutor);
        mPreTestDeviceConfigState = mDeviceConfigShellHelper.setSyncModeForTest(
                SYNC_DISABLED_MODE_UNTIL_REBOOT, NAMESPACE_SYSTEM_TIME);
        mTearDownRequired = true;
    }

    @After
    public void tearDown() throws Exception {
        if (!mTearDownRequired) {
            return;
        }

        mNetworkTimeUpdateServiceShellHelper.resetServerConfigForTests();
        mTimeDetectorShellHelper.clearNetworkTime();
        mNetworkTimeUpdateServiceShellHelper.forceRefresh();

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

    /**
     * Tests that the network_time_update_service operates as expected. The test pokes the
     * service via command line commands and has it communicate with a simulated NTP server to
     * refresh the device's latest network time information. The absence of a reachable NTP server
     * is also tested.
     *
     * <p>Network time can be required for automatic time detection (depending on a partner's device
     * configuration) and also for the {@link android.os.SystemClock#currentNetworkTimeClock} API.
     * This test primarily confirms the interaction between the network_time_update_service and
     * other system server components like the time_detector service.
     */
    @ApiTest(apis = {"android.os.SystemClock#currentNetworkTimeClock"}) // Indirectly
    @AppModeFull(reason = "Cannot bind socket in instant app mode")
    @Test
    public void testNetworkTimeUpdate() throws Exception {
        mNetworkTimeUpdateServiceShellHelper.assumeNetworkTimeUpdateServiceIsPresent();

        // Set the device's lower bound for acceptable system clock time to avoid the canned test
        // NTP response being rejected.
        mDeviceConfigShellHelper.put(
                NAMESPACE_SYSTEM_TIME, KEY_TIME_DETECTOR_LOWER_BOUND_MILLIS_OVERRIDE,
                Long.toString(SYSTEM_LOWER_TIME_BOUND));

        // Start a local SNTP test server. But does not setup a fake response.
        // So the server will not reply to any request.
        SntpTestServer server = runWithShellPermissionIdentity(SntpTestServer::new);

        // Write test server address into temporary config.
        URI uri = new URI("ntp", null, server.getAddress().getHostAddress(), server.getPort(),
                null, null, null);
        mNetworkTimeUpdateServiceShellHelper.setServerConfigForTests(uri, TEST_NTP_TIMEOUT_MILLIS);

        // Verify the case where the device hasn't been able to make a successful network time
        // request.
        {
            // Clear the latest network time received by the time detector.
            mTimeDetectorShellHelper.clearNetworkTime();
            assertNull(mTimeDetectorShellHelper.getNetworkTime());

            // Trigger a network time refresh.
            assertFalse(mNetworkTimeUpdateServiceShellHelper.forceRefresh());

            // Verify the returned network time is null as there is no previous network time fix.
            assertNull(mTimeDetectorShellHelper.getNetworkTime());
        }

        // Verify the case where there is now a network time request.
        {
            // Setup fake responses (Refer to SntpClientTest). And trigger time server refresh.
            server.setServerReply(HexEncoding.decode(MOCKED_NTP_RESPONSE));

            // After force_refresh, network_time_update_service should have associated
            // MOCKED_NTP_TIMESTAMP with an elapsedRealtime() value between
            // beforeRefreshElapsedMillis and afterRefreshElapsedMillis.
            final long beforeRefreshElapsedMillis = SystemClock.elapsedRealtime();
            assertTrue(mNetworkTimeUpdateServiceShellHelper.forceRefresh());
            final long afterRefreshElapsedMillis = SystemClock.elapsedRealtime();

            assertTimeDetectorLatestNetworkTimeInBounds(
                    MOCKED_NTP_TIMESTAMP, beforeRefreshElapsedMillis, afterRefreshElapsedMillis);

            // Remove fake server response and trigger a network time refresh to simulate a failed
            // refresh.
            server.setServerReply(null);
            assertFalse(mNetworkTimeUpdateServiceShellHelper.forceRefresh());

            // Verify the same network time value is being used.
            assertTimeDetectorLatestNetworkTimeInBounds(
                    MOCKED_NTP_TIMESTAMP, beforeRefreshElapsedMillis, afterRefreshElapsedMillis);
        }
    }

    private static <T> T runWithShellPermissionIdentity(ThrowingSupplier<T> command)
            throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity();
        try {
            return command.get();
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    /** Verify the given value is in range [lower, upper] */
    private static void assertInRange(String tag, long value, long lower, long upper) {
        final Range<Long> range = new Range<>(lower, upper);
        assertTrue(tag + ": " + value + " is not within range [" + lower + ", " + upper + "]",
                range.contains(value));
    }

    /** Asserts the latest network time held by the time detector is as expected. */
    private void assertTimeDetectorLatestNetworkTimeInBounds(
            long serverUnixEpochMillis, long beforeRefreshElapsedMillis,
            long afterRefreshElapsedMillis) throws Exception {
        TestNetworkTime networkTime = mTimeDetectorShellHelper.getNetworkTime();

        // This could happen if the time is rejected by the time_detector for being in the past.
        // That shouldn't happen because the lower bound is overridden by this test.
        assertNotNull("Expected network time but it is null", networkTime);

        assertInRange("Unix epoch tine",
                networkTime.unixEpochTime.unixEpochTimeMillis,
                serverUnixEpochMillis - networkTime.uncertaintyMillis,
                serverUnixEpochMillis + networkTime.uncertaintyMillis);
        assertInRange("Latest network time elapsed realtime",
                networkTime.unixEpochTime.elapsedRealtimeMillis,
                beforeRefreshElapsedMillis, afterRefreshElapsedMillis);
    }

    private static void skipOnFormFactorsWithoutService(
            NetworkTimeUpdateServiceShellHelper networkTimeUpdateServiceShellHelper)
            throws Exception {
        // If you have to adjust or remove this logic: consider that the public SDK
        // SystemClock.currentNetworkTimeClock() method currently requires
        // network_time_update_service (or NtpNetworkTimeHelper in the location service) to be
        // running in order to work. See also b/271256787 for context.
        if (isWatch()) {
            // network_time_update_service is not expected to exist on Wear due to
            // form-factor-specific changes. If this fails, more changes could be required besides
            // just removing this logic, so failing the test forces a discussion rather than moving
            // from silently skip test -> test passing.
            assertFalse(networkTimeUpdateServiceShellHelper.isNetworkTimeUpdateServicePresent());

            // Stop the test execution, but in a way that isn't considered a failure.
            // assumeFalse(isWatch()) would also work except for the assertion immediately above.
            throw new AssumptionViolatedException(
                    "Skipping test on devices without network_time_update_service");
        }
    }

    private static boolean isWatch() {
        return ApplicationProvider.getApplicationContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_WATCH);
    }
}
