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
package android.app.time.cts.shell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class for interacting with the fake {@link android.service.timezone.TimeZoneProviderService} in
 * the fake TimeZoneProviderService app.
 */
public final class FakeTimeZoneProviderAppShellHelper {

    /** The name of the app's APK. */
    public static final String FAKE_TZPS_APP_APK = "CtsFakeTimeZoneProvidersApp.apk";

    /** The package name of the app. */
    public static final String FAKE_TZPS_APP_PACKAGE = "com.android.time.cts.fake_tzps_app";

    /** The ID of the primary location time zone provider. */
    public static final String FAKE_PRIMARY_LOCATION_TIME_ZONE_PROVIDER_ID =
            "FakeLocationTimeZoneProviderService1";

    /** The ID of the secondary location time zone provider. */
    public static final String FAKE_SECONDARY_LOCATION_TIME_ZONE_PROVIDER_ID =
                    "FakeLocationTimeZoneProviderService2";

    // The following constant values correspond to enum values from
    // frameworks/base/core/proto/android/app/location_time_zone_manager.proto
    public static final int PROVIDER_STATE_UNKNOWN = 0;
    public static final int PROVIDER_STATE_INITIALIZING = 1;
    public static final int PROVIDER_STATE_CERTAIN = 2;
    public static final int PROVIDER_STATE_UNCERTAIN = 3;
    public static final int PROVIDER_STATE_DISABLED = 4;
    public static final int PROVIDER_STATE_PERM_FAILED = 5;
    public static final int PROVIDER_STATE_DESTROYED = 6;

    private static final String METHOD_GET_STATE = "get_state";
    private static final String CALL_RESULT_KEY_GET_STATE_STATE = "state";
    private static final String METHOD_REPORT_PERMANENT_FAILURE = "perm_fail";
    private static final String METHOD_REPORT_UNCERTAIN = "uncertain";
    private static final String METHOD_REPORT_UNCERTAIN_LEGACY = "uncertain_legacy";
    private static final String METHOD_REPORT_SUCCESS = "success";
    private static final String METHOD_REPORT_SUCCESS_LEGACY = "success_legacy";
    private static final String METHOD_PING = "ping";

    /** A single string, comma separated, may be empty. */
    private static final String CALL_EXTRA_KEY_SUCCESS_SUGGESTION_ZONE_IDS = "zone_ids";

    /** A provider's location detection status. */
    private static final String CALL_EXTRA_KEY_LOCATION_DETECTION_STATUS =
            "location_detection_status";
    /** A provider's connectivity status. */
    private static final String CALL_EXTRA_KEY_CONNECTIVITY_STATUS = "connectivity_status";
    /** A provider's time zone resolution status. */
    private static final String CALL_EXTRA_KEY_TIME_ZONE_RESOLUTION_STATUS =
            "time_zone_resolution_status";

    public static final String DEPENDENCY_STATUS_OK = "OK";
    public static final String DEPENDENCY_STATUS_NOT_APPLICABLE = "NOT_APPLICABLE";
    public static final String DEPENDENCY_STATUS_TEMPORARILY_UNAVAILABLE =
            "TEMPORARILY_UNAVAILABLE";
    public static final String DEPENDENCY_STATUS_BLOCKED_BY_ENVIRONMENT = "BLOCKED_BY_ENVIRONMENT";
    public static final String DEPENDENCY_STATUS_BLOCKED_BY_SETTINGS = "BLOCKED_BY_SETTINGS";

    public static final String OPERATION_STATUS_NOT_APPLICABLE = "NOT_APPLICABLE";
    public static final String OPERATION_STATUS_OK = "OK";
    public static final String OPERATION_STATUS_FAILED = "FAILED";

    private static final String SHELL_COMMAND_PREFIX = "content ";
    private static final String AUTHORITY = "faketzpsapp ";

    private final DeviceShellCommandExecutor mShellCommandExecutor;

    public FakeTimeZoneProviderAppShellHelper(DeviceShellCommandExecutor shellCommandExecutor) {
        mShellCommandExecutor = Objects.requireNonNull(shellCommandExecutor);
    }

    /**
     * Throws an exception if the app is not installed / available within a reasonable time.
     */
    public void waitForInstallation() throws Exception {
        long timeoutMs = 10000;
        long delayUntilMillis = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() <= delayUntilMillis) {
            try {
                ping();
                return;
            } catch (AssertionError e) {
                // Not present yet.
            }
            Thread.sleep(100);
        }
        fail("Installation did not happen in time");
    }

    public FakeTimeZoneProviderShellHelper getPrimaryLocationProviderHelper() {
        return getProviderHelper(FAKE_PRIMARY_LOCATION_TIME_ZONE_PROVIDER_ID);
    }

    public FakeTimeZoneProviderShellHelper getSecondaryLocationProviderHelper() {
        return getProviderHelper(FAKE_SECONDARY_LOCATION_TIME_ZONE_PROVIDER_ID);
    }

    private FakeTimeZoneProviderShellHelper getProviderHelper(String providerId) {
        return new FakeTimeZoneProviderShellHelper(providerId);
    }

    /**
     * A helper for interacting with a specific {@link
     * android.service.timezone.TimeZoneProviderService}.
     */
    public final class FakeTimeZoneProviderShellHelper {

        private final String mProviderId;

        private FakeTimeZoneProviderShellHelper(String providerId) {
            mProviderId = Objects.requireNonNull(providerId);
        }

        /** Causes {@link TimeZoneProviderService#reportUncertain()} to be called. */
        public void reportUncertainLegacy() throws Exception {
            Map<String, String> extras = new HashMap<>();
            executeContentProviderCall(mProviderId, METHOD_REPORT_UNCERTAIN_LEGACY, extras);
        }

        /**
         * Causes {@link TimeZoneProviderService#reportUncertain(TimeZoneProviderStatus)} to be
         * called.
         */
        public void reportUncertain(String locationDetectionStatus,
                String connectivityStatus, String timeZoneResolutionStatus) throws Exception {
            Map<String, String> extras = new HashMap<>();
            extras.put(CALL_EXTRA_KEY_LOCATION_DETECTION_STATUS,
                    Objects.requireNonNull(locationDetectionStatus));
            extras.put(CALL_EXTRA_KEY_CONNECTIVITY_STATUS,
                    Objects.requireNonNull(connectivityStatus));
            extras.put(CALL_EXTRA_KEY_TIME_ZONE_RESOLUTION_STATUS,
                    Objects.requireNonNull(timeZoneResolutionStatus));

            executeContentProviderCall(mProviderId, METHOD_REPORT_UNCERTAIN, extras);
        }

        public void reportPermanentFailure() throws Exception {
            executeContentProviderCall(mProviderId, METHOD_REPORT_PERMANENT_FAILURE, null);
        }

        /**
         * Causes TimeZoneProviderService.reportSuggestion(TimeZoneProviderSuggestion) to be called.
         */
        public void reportSuccessLegacy(String zoneId) throws Exception {
            reportSuccessLegacy(Collections.singletonList(zoneId));
        }

        /**
         * Causes TimeZoneProviderService.reportSuggestion(TimeZoneProviderSuggestion) to be called.
         */
        public void reportSuccessLegacy(List<String> zoneIds) throws Exception {
            String zoneIdsExtra = String.join(",", zoneIds);
            Map<String, String> extras = new HashMap<>();
            extras.put(CALL_EXTRA_KEY_SUCCESS_SUGGESTION_ZONE_IDS, zoneIdsExtra);

            executeContentProviderCall(mProviderId, METHOD_REPORT_SUCCESS_LEGACY, extras);
        }

        /**
         * Causes TimeZoneProviderService.reportSuggestion(TimeZoneProviderSuggestion,
         * TimeZoneProviderStatus) to be called.
         */
        public void reportSuccess(String zoneId, String locationDetectionStatus,
                String connectivityStatus) throws Exception {
            reportSuccess(Collections.singletonList(zoneId), locationDetectionStatus,
                    connectivityStatus);
        }

        /**
         * Causes TimeZoneProviderService#reportSuggestion(TimeZoneProviderSuggestion,
         * TimeZoneProviderStatus) to be called.
         */
        public void reportSuccess(List<String> zoneIds, String locationDetectionStatus,
                String connectivityStatus) throws Exception {
            String zoneIdsExtra = String.join(",", zoneIds);
            Map<String, String> extras = new HashMap<>();
            extras.put(CALL_EXTRA_KEY_SUCCESS_SUGGESTION_ZONE_IDS, zoneIdsExtra);
            extras.put(CALL_EXTRA_KEY_LOCATION_DETECTION_STATUS,
                    Objects.requireNonNull(locationDetectionStatus));
            extras.put(CALL_EXTRA_KEY_CONNECTIVITY_STATUS,
                    Objects.requireNonNull(connectivityStatus));
            extras.put(CALL_EXTRA_KEY_TIME_ZONE_RESOLUTION_STATUS, OPERATION_STATUS_OK);

            executeContentProviderCall(mProviderId, METHOD_REPORT_SUCCESS, extras);
        }

        public int getState() throws Exception {
            String stateResult = executeContentProviderCall(mProviderId, METHOD_GET_STATE, null);
            Pattern pattern = Pattern.compile(".*" + CALL_RESULT_KEY_GET_STATE_STATE + "=(.).*");
            Matcher matcher = pattern.matcher(stateResult);
            if (!matcher.matches()) {
                throw new RuntimeException("Unknown result format: " + stateResult);
            }
            return Integer.parseInt(matcher.group(1));
        }

        public void assertCurrentState(int expectedState) throws Exception {
            assertEquals(expectedState, getState());
        }

        public boolean exists() throws Exception {
            try {
                getState();
                return true;
            } catch (AssertionError e) {
                return false;
            }
        }

        public void assertCreated() throws Exception {
            assertTrue(exists());
        }

        public void assertNotCreated() throws Exception {
            assertFalse(exists());
        }
    }

    private void ping() throws Exception {
        String cmd = String.format("call --uri content://%s --method %s", AUTHORITY, METHOD_PING);
        mShellCommandExecutor.executeToTrimmedString(SHELL_COMMAND_PREFIX + cmd);
    }

    private String executeContentProviderCall(
            String providerId, String method, Map<String, String> extras) throws Exception {
        String cmd = String.format("call --uri content://%s --method %s --arg %s",
                AUTHORITY, method, providerId);
        if (extras != null) {
            for (Map.Entry<String, String> entry : extras.entrySet()) {
                cmd += String.format(" --extra %s:s:%s", entry.getKey(), entry.getValue());
            }
        }
        return mShellCommandExecutor.executeToTrimmedString(SHELL_COMMAND_PREFIX + cmd);
    }
}
