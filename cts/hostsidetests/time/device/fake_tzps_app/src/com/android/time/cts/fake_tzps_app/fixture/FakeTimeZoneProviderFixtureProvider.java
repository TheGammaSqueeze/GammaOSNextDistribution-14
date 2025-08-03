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
package com.android.time.cts.fake_tzps_app.fixture;

import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.DEPENDENCY_STATUS_BLOCKED_BY_ENVIRONMENT;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.DEPENDENCY_STATUS_BLOCKED_BY_SETTINGS;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.DEPENDENCY_STATUS_NOT_APPLICABLE;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.DEPENDENCY_STATUS_OK;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.DEPENDENCY_STATUS_TEMPORARILY_UNAVAILABLE;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.OPERATION_STATUS_FAILED;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.OPERATION_STATUS_NOT_APPLICABLE;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.OPERATION_STATUS_OK;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.service.timezone.TimeZoneProviderStatus;
import android.text.TextUtils;

import com.android.time.cts.fake_tzps_app.tzps.FakeTimeZoneProviderService;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A {@link ContentProvider} that can interact with the fake {@link
 * android.service.timezone.TimeZoneProviderService} implementations. This enables test code to
 * interact with the fakes: device-side code can use the usual content provider APIs, and both
 * device-side and host-side code can use the "adb shell content" command. For simplicity,
 * everything is implemented using the "call" verb.
 */
public class FakeTimeZoneProviderFixtureProvider extends ContentProvider {

    private static final String METHOD_GET_STATE = "get_state";
    private static final String CALL_RESULT_KEY_GET_STATE_STATE = "state";
    private static final String METHOD_REPORT_PERMANENT_FAILURE = "perm_fail";
    private static final String METHOD_REPORT_UNCERTAIN = "uncertain";
    private static final String METHOD_REPORT_UNCERTAIN_LEGACY = "uncertain_legacy";
    private static final String METHOD_REPORT_SUCCESS = "success";
    private static final String METHOD_REPORT_SUCCESS_LEGACY = "success_legacy";
    private static final String METHOD_PING = "ping";

    /** Suggestion time zone IDs. A single string, comma separated, may be empty. */
    private static final String CALL_EXTRA_KEY_SUGGESTION_ZONE_IDS = "zone_ids";
    /** A provider's location detection status. */
    private static final String CALL_EXTRA_KEY_LOCATION_DETECTION_STATUS =
            "location_detection_status";
    /** A provider's connectivity status. */
    private static final String CALL_EXTRA_KEY_CONNECTIVITY_STATUS = "connectivity_status";
    /** A provider's time zone resolution status. */
    private static final String CALL_EXTRA_KEY_TIME_ZONE_RESOLUTION_STATUS =
            "time_zone_resolution_status";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // No impl
        return null;
    }

    @Override
    public String getType(Uri uri) {
        // No impl
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // No impl
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // No impl
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // No impl
        return 0;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Objects.requireNonNull(extras);

        if (METHOD_PING.equals(method)) {
            // No-op - this method just exists to make sure the content provider exists.
            return Bundle.EMPTY;
        }

        String providerId = Objects.requireNonNull(arg);
        FakeTimeZoneProviderService provider = FakeTimeZoneProviderRegistry.getInstance()
                .getFakeTimeZoneProviderService(providerId);
        Objects.requireNonNull(provider, "arg=" + providerId + ", provider not found");

        Bundle result = new Bundle();
        switch (method) {
            case METHOD_GET_STATE: {
                result.putInt(CALL_RESULT_KEY_GET_STATE_STATE, provider.getState());
                break;
            }
            case METHOD_REPORT_PERMANENT_FAILURE: {
                provider.fakeReportPermanentFailure();
                break;
            }
            case METHOD_REPORT_UNCERTAIN: {
                TimeZoneProviderStatus status = getTimeZoneProviderStatus(extras);
                provider.fakeReportUncertain(status);
                break;
            }
            case METHOD_REPORT_UNCERTAIN_LEGACY: {
                provider.fakeReportUncertainLegacy();
                break;
            }
            case METHOD_REPORT_SUCCESS: {
                String zoneIdsString = extras.getString(CALL_EXTRA_KEY_SUGGESTION_ZONE_IDS);
                List<String> zoneIds = Arrays.asList(zoneIdsString.split(","));
                TimeZoneProviderStatus status = getTimeZoneProviderStatus(extras);
                provider.fakeReportSuggestion(zoneIds, status);
                break;
            }
            case METHOD_REPORT_SUCCESS_LEGACY: {
                String zoneIdsString = extras.getString(CALL_EXTRA_KEY_SUGGESTION_ZONE_IDS);
                List<String> zoneIds = Arrays.asList(zoneIdsString.split(","));
                provider.fakeReportSuggestionLegacy(zoneIds);
                break;
            }
            default: {
                throw new IllegalArgumentException("method=" + method + " not known");
            }
        }
        return result;
    }

    private static TimeZoneProviderStatus getTimeZoneProviderStatus(Bundle extras) {
        String locationDetectionStatusString =
                extras.getString(CALL_EXTRA_KEY_LOCATION_DETECTION_STATUS);
        int locationDetectionStatus =
                parseDependencyStatus(locationDetectionStatusString);
        String connectivityStatusString =
                extras.getString(CALL_EXTRA_KEY_CONNECTIVITY_STATUS);
        int connectivityStatus =
                parseDependencyStatus(connectivityStatusString);
        String timeZoneResolutionStatusString =
                extras.getString(CALL_EXTRA_KEY_TIME_ZONE_RESOLUTION_STATUS);
        int timeZoneResolutionStatus =
                parseOperationStatus(timeZoneResolutionStatusString);
        TimeZoneProviderStatus status = new TimeZoneProviderStatus.Builder()
                .setLocationDetectionDependencyStatus(locationDetectionStatus)
                .setConnectivityDependencyStatus(connectivityStatus)
                .setTimeZoneResolutionOperationStatus(timeZoneResolutionStatus)
                .build();
        return status;
    }

    private static int parseDependencyStatus(String dependencyStatusString) {
        if (TextUtils.isEmpty(dependencyStatusString)) {
            throw new IllegalArgumentException("Missing DependencyStatus");
        }
        switch (dependencyStatusString) {
            case DEPENDENCY_STATUS_NOT_APPLICABLE:
                return TimeZoneProviderStatus.DEPENDENCY_STATUS_NOT_APPLICABLE;
            case DEPENDENCY_STATUS_OK:
                return TimeZoneProviderStatus.DEPENDENCY_STATUS_OK;
            case DEPENDENCY_STATUS_TEMPORARILY_UNAVAILABLE:
                return TimeZoneProviderStatus.DEPENDENCY_STATUS_TEMPORARILY_UNAVAILABLE;
            case DEPENDENCY_STATUS_BLOCKED_BY_ENVIRONMENT:
                return TimeZoneProviderStatus.DEPENDENCY_STATUS_BLOCKED_BY_ENVIRONMENT;
            case DEPENDENCY_STATUS_BLOCKED_BY_SETTINGS:
                return TimeZoneProviderStatus.DEPENDENCY_STATUS_BLOCKED_BY_SETTINGS;
            default:
                throw new IllegalArgumentException(dependencyStatusString);
        }
    }

    private static int parseOperationStatus(String operationStatusString) {
        if (TextUtils.isEmpty(operationStatusString)) {
            throw new IllegalArgumentException("Missing OperationStatus");
        }
        switch (operationStatusString) {
            case OPERATION_STATUS_NOT_APPLICABLE:
                return TimeZoneProviderStatus.OPERATION_STATUS_NOT_APPLICABLE;
            case OPERATION_STATUS_OK:
                return TimeZoneProviderStatus.OPERATION_STATUS_OK;
            case OPERATION_STATUS_FAILED:
                return TimeZoneProviderStatus.OPERATION_STATUS_FAILED;
            default:
                throw new IllegalArgumentException(operationStatusString);
        }
    }
}
