/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.app.time.Capabilities.CAPABILITY_NOT_ALLOWED;
import static android.app.time.Capabilities.CAPABILITY_NOT_APPLICABLE;
import static android.app.time.Capabilities.CAPABILITY_POSSESSED;
import static android.app.time.cts.shell.DeviceConfigKeys.NAMESPACE_SYSTEM_TIME;
import static android.app.time.cts.shell.DeviceConfigShellHelper.SYNC_DISABLED_MODE_UNTIL_REBOOT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.app.time.ExternalTimeSuggestion;
import android.app.time.TimeCapabilities;
import android.app.time.TimeCapabilitiesAndConfig;
import android.app.time.TimeConfiguration;
import android.app.time.TimeManager;
import android.app.time.TimeState;
import android.app.time.TimeZoneCapabilities;
import android.app.time.TimeZoneCapabilitiesAndConfig;
import android.app.time.TimeZoneConfiguration;
import android.app.time.TimeZoneState;
import android.app.time.UnixEpochTime;
import android.app.time.cts.shell.DeviceConfigKeys;
import android.app.time.cts.shell.DeviceConfigShellHelper;
import android.app.time.cts.shell.DeviceShellCommandExecutor;
import android.app.time.cts.shell.TimeDetectorShellHelper;
import android.app.time.cts.shell.TimeZoneDetectorShellHelper;
import android.app.time.cts.shell.device.InstrumentationShellCommandExecutor;
import android.content.Context;
import android.location.LocationManager;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-process tests for {@link TimeManager} and associated classes. This test covers SDK APIs and
 * other behavior that partners really shouldn't break. Internal / non-critical behavior is tested
 * outside of CTS in {@link android.app.time.TimeManagerTest}.
 */
public class TimeManagerTest {

    /**
     * This rule adopts the Shell process permissions, needed because MANAGE_TIME_AND_ZONE_DETECTION
     * and SUGGEST_EXTERNAL_TIME required by {@link TimeManager} are privileged permissions.
     */
    @Rule
    public final AdoptShellPermissionsRule shellPermRule = new AdoptShellPermissionsRule();

    private TimeDetectorShellHelper mTimeDetectorShellHelper;
    private TimeZoneDetectorShellHelper mTimeZoneDetectorShellHelper;
    private DeviceConfigShellHelper mDeviceConfigShellHelper;
    private DeviceConfigShellHelper.PreTestState mDeviceConfigPreTestState;

    private Context mContext;
    private TimeManager mTimeManager;

    @Before
    public void before() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        DeviceShellCommandExecutor shellCommandExecutor = new InstrumentationShellCommandExecutor(
                instrumentation.getUiAutomation());
        mTimeDetectorShellHelper = new TimeDetectorShellHelper(shellCommandExecutor);
        mTimeZoneDetectorShellHelper = new TimeZoneDetectorShellHelper(shellCommandExecutor);
        mDeviceConfigShellHelper = new DeviceConfigShellHelper(shellCommandExecutor);

        // This anticipates a future state where a generally applied target preparer may disable
        // device_config sync for all CTS tests: only suspend syncing if it isn't already suspended,
        // and only resume it if this test suspended it.
        mDeviceConfigPreTestState = mDeviceConfigShellHelper.setSyncModeForTest(
                SYNC_DISABLED_MODE_UNTIL_REBOOT, NAMESPACE_SYSTEM_TIME);

        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mTimeManager = mContext.getSystemService(TimeManager.class);
        assertNotNull(mTimeManager);

        // Avoid running tests when device policy doesn't allow user configuration. If this needs to
        // pass then tests will become more complicated or separate cases broken out.
        int configureAutoDetectionEnabledCapability = mTimeManager.getTimeCapabilitiesAndConfig()
                .getCapabilities().getConfigureAutoDetectionEnabledCapability();
        boolean userRestricted = configureAutoDetectionEnabledCapability == CAPABILITY_NOT_ALLOWED;
        assertFalse(userRestricted);
    }

    @After
    public void after() throws Exception {
        mDeviceConfigShellHelper.restoreDeviceConfigStateForTest(mDeviceConfigPreTestState);
    }

    /**
     * Registers a {@link TimeManager.TimeZoneDetectorListener}, makes changes
     * to the configuration and checks that the listener is called.
     */
    @Test
    public void testManageTimeZoneConfiguration() throws Exception {
        AtomicInteger listenerTriggerCount = new AtomicInteger(0);
        TimeManager.TimeZoneDetectorListener listener = listenerTriggerCount::incrementAndGet;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            mTimeManager.addTimeZoneDetectorListener(executor, listener);

            TimeZoneCapabilitiesAndConfig capabilitiesAndConfig =
                    mTimeManager.getTimeZoneCapabilitiesAndConfig();

            TimeZoneCapabilities capabilities = capabilitiesAndConfig.getCapabilities();
            TimeZoneConfiguration originalConfig = capabilitiesAndConfig.getConfiguration();

            // Toggle the auto-detection enabled if capabilities allow or try (but expect to fail)
            // if not.
            {
                boolean newAutoDetectionEnabledValue = !originalConfig.isAutoDetectionEnabled();
                TimeZoneConfiguration configUpdate = new TimeZoneConfiguration.Builder()
                        .setAutoDetectionEnabled(newAutoDetectionEnabledValue)
                        .build();
                if (capabilities.getConfigureAutoDetectionEnabledCapability()
                        >= CAPABILITY_NOT_APPLICABLE) {

                    int expectedListenerTriggerCount = listenerTriggerCount.get() + 1;
                    assertTrue(mTimeManager.updateTimeZoneConfiguration(configUpdate));
                    waitForListenerCallbackCountAtLeast(
                            expectedListenerTriggerCount, listenerTriggerCount);

                    // Reset the config to what it was when the test started.
                    TimeZoneConfiguration resetConfigUpdate = new TimeZoneConfiguration.Builder()
                            .setAutoDetectionEnabled(!newAutoDetectionEnabledValue)
                            .build();

                    expectedListenerTriggerCount = listenerTriggerCount.get() + 1;
                    assertTrue(mTimeManager.updateTimeZoneConfiguration(resetConfigUpdate));
                    waitForListenerCallbackCountAtLeast(
                            expectedListenerTriggerCount, listenerTriggerCount);
                } else {
                    assertFalse(mTimeManager.updateTimeZoneConfiguration(configUpdate));
                }
            }

            // Toggle the geo-detection enabled if capabilities allow or try (but expect to fail)
            // if not.
            {
                boolean newGeoDetectionEnabledValue = !originalConfig.isGeoDetectionEnabled();
                TimeZoneConfiguration configUpdate = new TimeZoneConfiguration.Builder()
                        .setGeoDetectionEnabled(newGeoDetectionEnabledValue)
                        .build();
                if (capabilities.getConfigureGeoDetectionEnabledCapability()
                        >= CAPABILITY_NOT_APPLICABLE) {

                    int expectedListenerTriggerCount = listenerTriggerCount.get() + 1;
                    assertTrue(mTimeManager.updateTimeZoneConfiguration(configUpdate));
                    waitForListenerCallbackCountAtLeast(
                            expectedListenerTriggerCount, listenerTriggerCount);

                    // Reset the config to what it was when the test started.
                    TimeZoneConfiguration resetConfigUpdate = new TimeZoneConfiguration.Builder()
                            .setGeoDetectionEnabled(!newGeoDetectionEnabledValue)
                            .build();
                    expectedListenerTriggerCount = listenerTriggerCount.get() + 1;
                    assertTrue(mTimeManager.updateTimeZoneConfiguration(resetConfigUpdate));
                    waitForListenerCallbackCountAtLeast(
                            expectedListenerTriggerCount, listenerTriggerCount);
                } else {
                    assertFalse(mTimeManager.updateTimeZoneConfiguration(configUpdate));
                }
            }
        } finally {
            // Remove the listener. Required otherwise the fuzzy equality rules of lambdas causes
            // problems for later tests.
            mTimeManager.removeTimeZoneDetectorListener(listener);

            executor.shutdown();
        }
    }

    /**
     * Makes changes to the time configuration.
     */
    @Test
    public void testManageTimeConfiguration() throws Exception {
        TimeCapabilitiesAndConfig capabilitiesAndConfig =
                mTimeManager.getTimeCapabilitiesAndConfig();

        TimeCapabilities capabilities = capabilitiesAndConfig.getCapabilities();
        TimeConfiguration originalConfig = capabilitiesAndConfig.getConfiguration();

        // Toggle the auto-detection enabled if capabilities allow or try (but expect to fail)
        // if not.
        {
            boolean newAutoDetectionEnabledValue = !originalConfig.isAutoDetectionEnabled();
            if (capabilities.getConfigureAutoDetectionEnabledCapability()
                    >= CAPABILITY_NOT_APPLICABLE) {
                assertTrue(setAutoTimeDetectionEnabledAndSleep(
                        newAutoDetectionEnabledValue));

                // Reset the config to what it was when the test started.
                assertTrue(setAutoTimeDetectionEnabledAndSleep(
                        !newAutoDetectionEnabledValue));
            } else {
                assertFalse(setAutoTimeDetectionEnabledAndSleep(
                        newAutoDetectionEnabledValue));
            }
        }
    }

    @Test
    public void testExternalTimeSuggestions() throws Exception {
        int setManualTimeCapability = mTimeManager.getTimeCapabilitiesAndConfig()
                .getCapabilities().getSetManualTimeCapability();
        boolean disableAutoDetectionAfterTest = false;
        if (setManualTimeCapability == CAPABILITY_POSSESSED) {
            // If the user can set the value manually, this means that auto detection is disabled.
            // Turn it on for the duration of this test.
            disableAutoDetectionAfterTest = true;
            assertTrue(setAutoTimeDetectionEnabledAndSleep(true));
        }

        try {
            long startCurrentTimeMillis = System.currentTimeMillis();
            long elapsedRealtimeMillis = SystemClock.elapsedRealtime();

            // Set the time detector to only use ORIGIN_NETWORK. The important aspect is that it
            // isn't ORIGIN_EXTERNAL, and so suggestions from external should be ignored.
            mDeviceConfigShellHelper.put(NAMESPACE_SYSTEM_TIME,
                    DeviceConfigKeys.TimeDetector.KEY_TIME_DETECTOR_ORIGIN_PRIORITIES_OVERRIDE,
                    DeviceConfigKeys.TimeDetector.ORIGIN_NETWORK);
            sleepForAsyncOperation();

            long suggestion1Millis = Instant.ofEpochMilli(startCurrentTimeMillis)
                    .plus(1, ChronoUnit.DAYS)
                    .toEpochMilli();
            ExternalTimeSuggestion futureTimeSuggestion1 =
                    createExternalTimeSuggestion(elapsedRealtimeMillis, suggestion1Millis);
            long suggestion2Millis =
                    Instant.ofEpochMilli(suggestion1Millis).plus(1, ChronoUnit.DAYS).toEpochMilli();
            ExternalTimeSuggestion futureTimeSuggestion2 =
                    createExternalTimeSuggestion(elapsedRealtimeMillis, suggestion2Millis);

            // Suggest a change. It shouldn't be used.
            mTimeManager.suggestExternalTime(futureTimeSuggestion1);
            sleepForAsyncOperation();

            // The suggestion should have been ignored so the system clock should not have advanced.
            assertTrue(System.currentTimeMillis() < suggestion1Millis);

            // Set the time detector to only use ORIGIN_EXTERNAL.
            // The suggestion should have been stored and should be acted upon when the origin list
            // changes.
            mDeviceConfigShellHelper.put(NAMESPACE_SYSTEM_TIME,
                    DeviceConfigKeys.TimeDetector.KEY_TIME_DETECTOR_ORIGIN_PRIORITIES_OVERRIDE,
                    DeviceConfigKeys.TimeDetector.ORIGIN_EXTERNAL);
            sleepForAsyncOperation();
            assertTrue(System.currentTimeMillis() >= suggestion1Millis);

            // Suggest a change. It should be used.
            mTimeManager.suggestExternalTime(futureTimeSuggestion2);
            sleepForAsyncOperation();
            assertTrue(System.currentTimeMillis() >= suggestion2Millis);

            // Now do our best to return the device to its original state.
            ExternalTimeSuggestion originalTimeSuggestion =
                    createExternalTimeSuggestion(elapsedRealtimeMillis, startCurrentTimeMillis);
            mTimeManager.suggestExternalTime(originalTimeSuggestion);
            sleepForAsyncOperation();
        } finally {
            if (disableAutoDetectionAfterTest) {
                assertTrue(setAutoTimeDetectionEnabledAndSleep(false));
            }
        }
    }

    /**
     * Registers a {@link TimeManager.TimeZoneDetectorListener}, makes changes
     * to the "location enabled" setting and checks that the listener is called.
     */
    @Test
    public void testLocationManagerAffectsTimeZoneCapabilities() throws Exception {
        AtomicInteger listenerTriggerCount = new AtomicInteger(0);
        TimeManager.TimeZoneDetectorListener listener = listenerTriggerCount::incrementAndGet;

        LocationManager locationManager = mContext.getSystemService(LocationManager.class);
        assertNotNull(locationManager);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            mTimeManager.addTimeZoneDetectorListener(executor, listener);

            // This test does not use waitForListenerCallbackCount() because changing the location
            // enabled setting triggers more than one callback when there's a work profile, so it's
            // easier just to sleep and confirm >= 1 callbacks have been received.
            sleepForAsyncOperation();
            int newCallbackCount = listenerTriggerCount.get();
            assertEquals(0, newCallbackCount);
            int previousCallbackCount = 0;

            UserHandle userHandle = Process.myUserHandle();
            boolean locationEnabled = locationManager.isLocationEnabledForUser(userHandle);

            locationManager.setLocationEnabledForUser(!locationEnabled, userHandle);
            sleepForAsyncOperation();
            newCallbackCount = listenerTriggerCount.get();
            assertTrue(newCallbackCount > previousCallbackCount);
            previousCallbackCount = newCallbackCount;

            locationManager.setLocationEnabledForUser(locationEnabled, userHandle);
            sleepForAsyncOperation();
            newCallbackCount = listenerTriggerCount.get();
            assertTrue(newCallbackCount > previousCallbackCount);
        } finally {
            // Remove the listener. Required otherwise the fuzzy equality rules of lambdas causes
            // problems for later tests.
            mTimeManager.removeTimeZoneDetectorListener(listener);

            executor.shutdown();
        }
    }

    @Test
    public void testTimeStateAndConfirmation() throws Exception {
        int setManualTimeCapability = mTimeManager.getTimeCapabilitiesAndConfig()
                .getCapabilities().getSetManualTimeCapability();
        boolean disableAutoDetectionAfterTest = false;
        if (setManualTimeCapability != CAPABILITY_POSSESSED) {
            // If the user cannot set the value manually, this means that auto detection is enabled.
            // Turn it off for the duration of this test.
            disableAutoDetectionAfterTest = true;
            assertTrue(setAutoTimeDetectionEnabledAndSleep(false));
        }

        // Capture the initial device state.
        TimeState initialTimeState = mTimeManager.getTimeState();
        try {
            // Set the device to have a new time with low confidence.
            UnixEpochTime testTime = new UnixEpochTime(SystemClock.elapsedRealtime(),
                    Instant.now().plusSeconds(12345L).toEpochMilli());
            final boolean userShouldConfirm = true;
            setTimeStateViaShell(testTime, userShouldConfirm);

            // Confirm getTimeState() works as expected.
            TimeState actualTimeState = mTimeManager.getTimeState();
            UnixEpochTime actualTimeStateTime = actualTimeState.getUnixEpochTime();
            assertAlmostSameTime(testTime, actualTimeStateTime);
            assertTrue(actualTimeState.getUserShouldConfirmTime());

            // Create a time that differs from the one the device thinks it is and attempt to
            // confirm it. This should fail to confirm.
            UnixEpochTime nonMatchingTime = new UnixEpochTime(
                    actualTimeStateTime.getElapsedRealtimeMillis(),
                    actualTimeStateTime.getUnixEpochTimeMillis() + Duration.ofDays(1).toMillis());
            assertFalse(mTimeManager.confirmTime(nonMatchingTime));

            // Check nothing has changed about the device's time state.
            {
                TimeState currentTimeState = mTimeManager.getTimeState();
                assertAlmostSameTime(actualTimeState.getUnixEpochTime(),
                        currentTimeState.getUnixEpochTime());
                assertTrue(currentTimeState.getUserShouldConfirmTime());
            }

            // Now confirm the device's current time.
            assertTrue(mTimeManager.confirmTime(actualTimeStateTime));

            // Check time is the same, but confidence is higher.
            {
                TimeState currentTimeState = mTimeManager.getTimeState();
                assertAlmostSameTime(actualTimeState.getUnixEpochTime(),
                        currentTimeState.getUnixEpochTime());
                assertFalse(currentTimeState.getUserShouldConfirmTime());
            }
        } finally {
            // Return the device to its original state.
            setTimeStateViaShell(initialTimeState.getUnixEpochTime(),
                    initialTimeState.getUserShouldConfirmTime());
            if (disableAutoDetectionAfterTest) {
                setAutoTimeDetectionEnabledAndSleep(false);
            }
        }
    }

    @Test
    public void testTimeZoneStateAndConfirmation() throws Exception {
        int setManualTimeZoneCapability = mTimeManager.getTimeZoneCapabilitiesAndConfig()
                .getCapabilities().getSetManualTimeZoneCapability();
        boolean disableAutoDetectionAfterTest = false;
        if (setManualTimeZoneCapability != CAPABILITY_POSSESSED) {
            // If the user cannot set the value manually, this means that auto detection is enabled.
            // Turn it off for the duration of this test.
            disableAutoDetectionAfterTest = true;
            assumeTrue(setAutoTimeZoneDetectionEnabledAndSleep(false));
        }

        // Capture the initial device state.
        TimeZoneState initialTimeZoneState = mTimeManager.getTimeZoneState();
        try {
            // Set the device to have a time zone with low confidence.
            final String testZoneId = "Europe/London";
            final boolean userShouldConfirm = true;
            mTimeZoneDetectorShellHelper.setTimeZoneState(testZoneId, userShouldConfirm);

            // Confirm getTimeZoneState() works as expected.
            TimeZoneState actualTimeZoneState = mTimeManager.getTimeZoneState();
            assertEquals(testZoneId, actualTimeZoneState.getId());
            assertTrue(actualTimeZoneState.getUserShouldConfirmId());

            // Attempt to confirm a time zone that differs from the one the device thinks it is.
            // This should fail to confirm.
            final String notTestZoneId = "Europe/Paris";
            assertFalse(mTimeManager.confirmTimeZone(notTestZoneId));

            // Check nothing has changed about the device's time zone state.
            {
                TimeZoneState currentTimeZoneState = mTimeManager.getTimeZoneState();
                assertEquals(actualTimeZoneState.getId(), currentTimeZoneState.getId());
                assertTrue(currentTimeZoneState.getUserShouldConfirmId());
            }

            // Now confirm the device's current time zone.
            assertTrue(mTimeManager.confirmTimeZone(testZoneId));

            // Check time zone is the same, but confidence is higher.
            {
                TimeZoneState currentTimeZoneState = mTimeManager.getTimeZoneState();
                assertEquals(actualTimeZoneState.getId(), currentTimeZoneState.getId());
                assertFalse(currentTimeZoneState.getUserShouldConfirmId());
            }
        } finally {
            // Return the device to its original state.
            mTimeZoneDetectorShellHelper.setTimeZoneState(initialTimeZoneState.getId(),
                    initialTimeZoneState.getUserShouldConfirmId());
            if (disableAutoDetectionAfterTest) {
                setAutoTimeZoneDetectionEnabledAndSleep(false);
            }
        }
    }

    @Test
    public void testSetManualTime() throws Exception {
        TimeCapabilitiesAndConfig timeCapabilitiesAndConfig =
                mTimeManager.getTimeCapabilitiesAndConfig();
        TimeCapabilities capabilities = timeCapabilitiesAndConfig.getCapabilities();

        TimeState initialTimeState = mTimeManager.getTimeState();
        UnixEpochTime initialDeviceTime = initialTimeState.getUnixEpochTime();
        UnixEpochTime testTime1 = new UnixEpochTime(
                initialDeviceTime.getElapsedRealtimeMillis(),
                initialDeviceTime.getUnixEpochTimeMillis() + Duration.ofDays(1).toMillis());

        // Try to get the device into a known state: we want auto detection disabled in order to set
        // a manual time.
        // Various scenarios to consider:
        // a) Auto detection is supported, but it's turned off by the user.
        // b) Auto detection is not supported so manual entry is the only option.
        // The settings value is ignored by the system for (b) so we don't just look at
        // settings values from TimeConfiguration.

        boolean initialAutoDetectionEnabled;
        if (capabilities.getSetManualTimeCapability() == CAPABILITY_POSSESSED) {
            // Good to go - device is already in state (a) or (b).
            initialAutoDetectionEnabled = false;
        } else if (capabilities.getSetManualTimeCapability() == CAPABILITY_NOT_APPLICABLE) {
            // We can infer auto detection is enabled, which will prevent us setting time manually.

            // Try to turn auto detection off to begin the test.
            boolean success = setAutoTimeDetectionEnabledAndSleep(false);
            assertTrue("Test requires being able to turn off auto detection", success);

            initialAutoDetectionEnabled = true;
        } else {
            // CAPABILITY_NOT_ALLOWED, CAPABILITY_NOT_SUPPORTED or unknown.
            throw new AssertionError("Unexpected capability state for setting manual value: "
                    + capabilities.getSetManualTimeCapability());
        }

        try {
            // The device must be in manual mode at this point and there are no user restrictions,
            // so setting it manually should succeed.
            assertTrue(mTimeManager.setManualTime(testTime1));
            UnixEpochTime expectedDeviceTime = testTime1;

            // Confirm the time state has updated.
            {
                TimeState timeState = mTimeManager.getTimeState();
                assertAlmostSameTime(expectedDeviceTime, timeState.getUnixEpochTime());
                assertFalse(timeState.getUserShouldConfirmTime());
            }

            // Now turn on automatic detection.
            boolean success = setAutoTimeDetectionEnabledAndSleep(true);
            assertTrue("Test requires being able to turn on auto detection", success);
            // The device time may change as part of turning auto detection back on.
            expectedDeviceTime = mTimeManager.getTimeState().getUnixEpochTime();

            // Try to set the time again.
            UnixEpochTime testTime2 = new UnixEpochTime(
                    initialDeviceTime.getElapsedRealtimeMillis(),
                    initialDeviceTime.getUnixEpochTimeMillis() + Duration.ofDays(2).toMillis());
            assertFalse(mTimeManager.setManualTime(testTime2));

            // Confirm the call failed completely and time state has stayed the same.
            {
                TimeState actualTimeState = mTimeManager.getTimeState();
                assertAlmostSameTime(expectedDeviceTime, actualTimeState.getUnixEpochTime());
                assertFalse(actualTimeState.getUserShouldConfirmTime());
            }
        } finally {
            // Try to return the device to its original time and config state.
            setTimeStateViaShell(
                    initialTimeState.getUnixEpochTime(),
                    initialTimeState.getUserShouldConfirmTime());
            setAutoTimeDetectionEnabledAndSleep(initialAutoDetectionEnabled);
        }
    }

    @Test
    public void testSetManualTimeZone() throws Exception {
        TimeZoneCapabilitiesAndConfig timeZoneCapabilitiesAndConfig =
                mTimeManager.getTimeZoneCapabilitiesAndConfig();
        TimeZoneCapabilities capabilities = timeZoneCapabilitiesAndConfig.getCapabilities();

        TimeZoneState initialTimeZoneState = mTimeManager.getTimeZoneState();
        String testTimeZone1 = "America/New_York";

        // Try to get the device into a known state: we want auto detection disabled in order to set
        // a manual time zone.
        // Various scenarios to consider:
        // a) Auto detection is supported, but it's turned off by the user.
        // b) Auto detection is not supported so manual entry is the only option.
        // The settings value is ignored by the system for (b) so we don't just look at
        // settings values from TimeZoneConfiguration.

        boolean initialAutoDetectionEnabled;
        if (capabilities.getSetManualTimeZoneCapability() == CAPABILITY_POSSESSED) {
            // Good to go - device is already in state (a) or (b).
            initialAutoDetectionEnabled = false;
        } else if (capabilities.getSetManualTimeZoneCapability() == CAPABILITY_NOT_APPLICABLE) {
            // We can infer auto detection is enabled, which will prevent us setting time zone
            // manually.

            // Try to turn auto detection off to begin the test.
            boolean success = setAutoTimeZoneDetectionEnabledAndSleep(false);
            assertTrue("Test requires being able to turn off auto detection", success);

            initialAutoDetectionEnabled = true;
        } else {
            // CAPABILITY_NOT_ALLOWED, CAPABILITY_NOT_SUPPORTED or unknown.
            throw new AssertionError("Unexpected capability state for setting manual value: "
                    + capabilities.getSetManualTimeZoneCapability());
        }

        try {
            // The device must be in manual mode at this point and there are no user restrictions,
            // so setting it manually should succeed.
            assertTrue(mTimeManager.setManualTimeZone(testTimeZone1));

            // Confirm the time zone state has updated.
            {
                TimeZoneState timeZoneState = mTimeManager.getTimeZoneState();
                assertEquals(testTimeZone1, timeZoneState.getId());
                assertFalse(timeZoneState.getUserShouldConfirmId());
            }

            // This part of the test is optional: What happens to setManualTimeZone() when automatic
            // detection is ON can only be tested if the device supports automatic time zone
            // detection.
            if (capabilities.getConfigureAutoDetectionEnabledCapability() == CAPABILITY_POSSESSED) {
                // Turn on automatic detection.
                boolean success = setAutoTimeZoneDetectionEnabledAndSleep(true);
                assertTrue("Test requires being able to turn on auto detection", success);

                // Sample the time zone now that auto-detection is on (it may have changed).
                TimeZoneState originalTimeZoneState = mTimeManager.getTimeZoneState();

                // Try to set the time zone again.
                String testTimeZone2 = "Europe/Paris";
                assertFalse(mTimeManager.setManualTimeZone(testTimeZone2));

                // Confirm the call failed completely and time zone state has stayed the same.
                {
                    TimeZoneState timeZoneState = mTimeManager.getTimeZoneState();
                    assertEquals(originalTimeZoneState.getId(), timeZoneState.getId());
                    assertEquals(originalTimeZoneState.getUserShouldConfirmId(),
                            timeZoneState.getUserShouldConfirmId());
                }
            }
        } finally {
            // Try to return the device to its original time and config state.
            mTimeZoneDetectorShellHelper.setTimeZoneState(
                    initialTimeZoneState.getId(),
                    initialTimeZoneState.getUserShouldConfirmId());
            setAutoTimeZoneDetectionEnabledAndSleep(initialAutoDetectionEnabled);
        }
    }

    private boolean setAutoTimeDetectionEnabledAndSleep(boolean enabled) throws Exception {
        boolean success = mTimeManager.updateTimeConfiguration(
                new TimeConfiguration.Builder().setAutoDetectionEnabled(enabled).build());
        // Configuration changes are recorded synchronously, but can be handled asynchronously so
        // sleep. Otherwise, the strategy may not yet understand the config has changed.
        sleepForAsyncOperation();
        return success;
    }

    private boolean setAutoTimeZoneDetectionEnabledAndSleep(boolean enabled) throws Exception {
        boolean success = mTimeManager.updateTimeZoneConfiguration(
                new TimeZoneConfiguration.Builder().setAutoDetectionEnabled(enabled).build());
        // Configuration changes are recorded synchronously, but can be handled asynchronously so
        // sleep. Otherwise, the strategy may not yet understand the config has changed.
        sleepForAsyncOperation();
        return success;
    }

    private void setTimeStateViaShell(UnixEpochTime testTime, boolean userShouldConfirm)
            throws Exception {
        mTimeDetectorShellHelper.setTimeState(
                testTime.getElapsedRealtimeMillis(), testTime.getUnixEpochTimeMillis(),
                userShouldConfirm);
    }

    /** Asserts that the two times represent the same time when adjusting for elapsed realtime. */
    private static void assertAlmostSameTime(UnixEpochTime one, UnixEpochTime two) {
        UnixEpochTime oneAdjusted = one.at(two.getElapsedRealtimeMillis());
        long absUnixEpochMillisDifference =
                Math.abs(oneAdjusted.getUnixEpochTimeMillis() - two.getUnixEpochTimeMillis());
        assertTrue("one=" + one + ", two=" + two
                + ", oneAdjusted=" + oneAdjusted
                + ", absUnixEpochMillisDifference=" + absUnixEpochMillisDifference,
                absUnixEpochMillisDifference < 200);
    }

    /**
     * The platform provides an "at least once" listener callback contract for time manager
     * callbacks. Callbacks are provided to support UI refreshes when something the UI is displaying
     * may have changed. Because the time / time zone detectors are "real" during tests, there can
     * be callbacks happening during tests for multiple reasons related to the normal operation of
     * the time zone detector, e.g. side effects of changing the config or even just the detectors
     * getting real signals. For example, after changing config to turn on/off a detector, there
     * will be a callback that the config has just changed. Additionally, the detector status will
     * likely change, e.g. after being enabled the detector may actually detect the time zone,
     * causing more callbacks to trigger.
     *
     * <p>This method delays until a minimum value has been hit (and then a bit more), meaning it
     * guards against no callback being generated in a certain time when one is expected. It cannot
     * tell between an expected notification and an unexpected one, and cannot detect there being
     * too many, meaning it is a very loose check.
     */
    private static void waitForListenerCallbackCountAtLeast(
            int minValue, AtomicInteger actualValue) throws Exception {
        // Busy waits up to 30 seconds for the count to reach minValue.
        final long busyWaitMillis = 30000;
        long targetTimeMillis = System.currentTimeMillis() + busyWaitMillis;
        while (actualValue.get() < minValue
                && System.currentTimeMillis() < targetTimeMillis) {
            Thread.sleep(250);
        }
        assertTrue(actualValue.get() >= minValue);

        // Give the system another couple of seconds to settle.
        sleepForAsyncOperation();
    }

    /**
     * Sleeps for a length of time sufficient to allow async operations to complete. Many time
     * manager APIs are or could be asynchronous and deal with time, so there are no practical
     * alternatives.
     */
    private static void sleepForAsyncOperation() throws Exception{
        Thread.sleep(5_000);
    }

    private static ExternalTimeSuggestion createExternalTimeSuggestion(
            long elapsedRealtimeMillis, long unixEpochTimeMillis) {
        ExternalTimeSuggestion externalTimeSuggestion =
                new ExternalTimeSuggestion(elapsedRealtimeMillis, unixEpochTimeMillis);
        externalTimeSuggestion.addDebugInfo("cts.TimeManagerTest");
        return externalTimeSuggestion;
    }
}
