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

package com.android.adservices.service.measurement;

import static java.util.Map.entry;

import android.adservices.measurement.RegistrationRequest;
import android.net.Uri;
import android.os.RemoteException;

import com.android.adservices.service.measurement.actions.Action;
import com.android.adservices.service.measurement.actions.AggregateReportingJob;
import com.android.adservices.service.measurement.actions.RegisterSource;
import com.android.adservices.service.measurement.actions.RegisterTrigger;
import com.android.adservices.service.measurement.actions.ReportObjects;
import com.android.adservices.service.measurement.registration.AsyncFetchStatus;
import com.android.adservices.service.measurement.registration.AsyncRegistration;
import com.android.adservices.service.measurement.util.Enrollment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * End-to-end test from source and trigger registration to attribution reporting, using mocked HTTP
 * requests.
 *
 * <p>Tests in assets/msmt_interop_tests/ directory were copied from Chromium
 * src/content/test/data/attribution_reporting/interop GitHub commit
 * 4b823a4852665b1fba719401c99b56ca8b9b28f1.
 */
@RunWith(Parameterized.class)
public class E2EInteropMockTest extends E2EMockTest {
    // The following keys are to JSON fields that should be interpreted in milliseconds.
    public static final Set<String> TIMESTAMP_KEYS_IN_MILLIS =
            new HashSet<>(
                    Arrays.asList(
                            TestFormatJsonMapping.TIMESTAMP_KEY,
                            TestFormatJsonMapping.REPORT_TIME_KEY,
                            UnparsableRegistrationKeys.TIME));
    // The following keys are to JSON fields that should be interpreted in seconds.
    public static final Set<String> TIMESTAMP_KEYS_IN_SECONDS =
            new HashSet<>(
                    Arrays.asList(
                            TestFormatJsonMapping.SCHEDULED_REPORT_TIME,
                            TestFormatJsonMapping.SOURCE_REGISTRATION_TIME));
    // All interop tests are specified with timestamps that are offsets, so we establish a start
    // time for those offsets to add to. There are two IMPORTANT conditions for this start time:
    // 1.) The time must be recent so that the timestamp is large enough to work with the temporary
    //    "inline migration" we have for event_report_window.
    // 2.) The time must be at the start of a day because there are operations that round timestamps
    //     down to the day, e.g. in populating the source_registration_time for aggregatable reports
    private static final long START_TIME = 1674000000000L;
    private static final String TEST_DIR_NAME = "msmt_interop_tests";
    private static final String ANDROID_APP_SCHEME = "android-app";
    private static final List<AsyncFetchStatus.EntityStatus> sParsingErrors = List.of(
            AsyncFetchStatus.EntityStatus.PARSING_ERROR,
            AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
    private static final Map<String, String> sApiConfigPhFlags = Map.ofEntries(
            entry(
                    "rate_limit_max_attributions",
                    "measurement_max_attribution_per_rate_limit_window"),
            entry(
                    "rate_limit_max_attribution_reporting_origins",
                    "measurement_max_distinct_enrollments_in_attribution"),
            entry(
                    "rate_limit_max_source_registration_reporting_origins",
                    "measurement_max_distinct_reporting_origins_in_source"),
            entry(
                    "max_destinations_per_source_site_reporting_site",
                    "measurement_max_distinct_destinations_in_active_source"),
            entry(
                    "max_event_info_gain",
                    "measurement_flex_api_max_information_gain_event"),
            entry(
                    "rate_limit_max_reporting_origins_per_source_reporting_site",
                    "measurement_max_reporting_origins_per_source_reporting_site_per_window"),
            entry(
                    "max_destinations_per_rate_limit_window",
                    "measurement_max_destinations_per_publisher_per_rate_limit_window"),
            entry(
                    "max_destinations_per_rate_limit_window_reporting_site",
                    "measurement_max_dest_per_publisher_x_enrollment_per_rate_limit_window"),
            entry(
                    "max_sources_per_origin",
                    "measurement_max_sources_per_publisher"),
            entry(
                    "max_event_level_reports_per_destination",
                    "measurement_max_event_reports_per_destination"),
            entry(
                    "max_aggregatable_reports_per_destination",
                    "measurement_max_aggregate_reports_per_destination"));

    private static String preprocessor(String json) {
        // TODO(b/290098169): Cleanup anchorTime when this bug is addressed. Handling cases where
        // Source event report window is already stored as mEventTime + mEventReportWindow.
        return anchorTime(
                json.replaceAll("\\.test(?=[\"\\/])", ".com")
                        // Remove comments
                        .replaceAll("^\\s*\\/\\/.+\\n", "")
                        .replaceAll("\"destination\":", "\"web_destination\":"),
                START_TIME);
    }

    private static Map<String, String> sPhFlagsForInterop =
            Map.of(
                    // TODO (b/295382171): remove this after the flag is removed.
                    "measurement_enable_max_aggregate_reports_per_source", "true",
                    "measurement_min_event_report_delay_millis", "0",
                    "measurement_source_registration_time_optional_for_agg_reports_enabled",
                            "true",
                    "measurement_flexible_event_reporting_api_enabled", "true");

    @Parameterized.Parameters(name = "{3}")
    public static Collection<Object[]> getData() throws IOException, JSONException {
        return data(TEST_DIR_NAME, E2EInteropMockTest::preprocessor, sApiConfigPhFlags);
    }

    public E2EInteropMockTest(
            Collection<Action> actions,
            ReportObjects expectedOutput,
            ParamsProvider paramsProvider,
            String name,
            Map<String, String> phFlagsMap)
            throws RemoteException {
        super(
                actions,
                expectedOutput,
                paramsProvider,
                name,
                (
                        (Supplier<Map<String, String>>) () -> {
                            for (String key : sPhFlagsForInterop.keySet()) {
                                phFlagsMap.put(key, sPhFlagsForInterop.get(key));
                            }
                            return phFlagsMap;
                        }
                ).get()
        );
        mAttributionHelper =
                TestObjectProvider.getAttributionJobHandler(
                        mDatastoreManager, mFlags, mErrorLogger);
        mMeasurementImpl =
                TestObjectProvider.getMeasurementImpl(
                        mDatastoreManager,
                        mClickVerifier,
                        mMeasurementDataDeleter,
                        mMockContentResolver);
        mAsyncRegistrationQueueRunner =
                TestObjectProvider.getAsyncRegistrationQueueRunner(
                        TestObjectProvider.Type.DENOISED,
                        mDatastoreManager,
                        mAsyncSourceFetcher,
                        mAsyncTriggerFetcher,
                        mDebugReportApi,
                        mFlags);
    }

    @Override
    void processAction(RegisterSource sourceRegistration) throws JSONException, IOException {
        RegistrationRequest request = sourceRegistration.mRegistrationRequest;
        // For interop tests, we currently expect only one HTTPS response per registration with no
        // redirects, partly due to differences in redirect handling across attribution APIs.
        for (String uri : sourceRegistration.mUriToResponseHeadersMap.keySet()) {
            updateEnrollment(uri);
            insertSourceOrRecordUnparsable(
                    sourceRegistration.getPublisher(),
                    sourceRegistration.mTimestamp,
                    uri,
                    sourceRegistration.mArDebugPermission,
                    request,
                    getNextResponse(sourceRegistration.mUriToResponseHeadersMap, uri));
        }
        mAsyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();
        if (sourceRegistration.mDebugReporting) {
            processActualDebugReportApiJob(sourceRegistration.mTimestamp);
        }
    }

    @Override
    void processAction(RegisterTrigger triggerRegistration) throws IOException, JSONException {
        RegistrationRequest request = triggerRegistration.mRegistrationRequest;
        // For interop tests, we currently expect only one HTTPS response per registration with no
        // redirects, partly due to differences in redirect handling across attribution APIs.
        for (String uri : triggerRegistration.mUriToResponseHeadersMap.keySet()) {
            updateEnrollment(uri);
            insertTriggerOrRecordUnparsable(
                    triggerRegistration.getDestination(),
                    triggerRegistration.mTimestamp,
                    uri,
                    triggerRegistration.mArDebugPermission,
                    request,
                    getNextResponse(triggerRegistration.mUriToResponseHeadersMap, uri));
        }
        mAsyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();
        Assert.assertTrue(
                "AttributionJobHandler.performPendingAttributions returned false",
                mAttributionHelper.performPendingAttributions());
        // Attribution can happen up to an hour after registration call, due to AsyncRegistration
        processActualDebugReportJob(triggerRegistration.mTimestamp, TimeUnit.MINUTES.toMillis(30));
        if (triggerRegistration.mDebugReporting) {
            processActualDebugReportApiJob(triggerRegistration.mTimestamp);
        }
    }

    @Override
    void processAction(AggregateReportingJob reportingJob) throws IOException, JSONException {
        super.processAction(reportingJob);
        // Test json files for interop tests come verbatim from chromium, so they don't have
        // source_registration_time as a field. Remove them from the actual reports so that
        // comparisons don't fail.
        removeSourceRegistrationTime(mActualOutput.mAggregateReportObjects);
        removeSourceRegistrationTime(mActualOutput.mDebugAggregateReportObjects);
    }

    private void removeSourceRegistrationTime(List<JSONObject> aggregateReportObjects)
            throws JSONException {
        for (JSONObject jsonObject : aggregateReportObjects) {
            jsonObject
                    .getJSONObject(TestFormatJsonMapping.PAYLOAD_KEY)
                    .remove(AggregateReportPayloadKeys.SOURCE_REGISTRATION_TIME);
        }
    }

    private void insertSourceOrRecordUnparsable(
            String publisher,
            long timestamp,
            String uri,
            boolean arDebugPermission,
            RegistrationRequest request,
            Map<String, List<String>> headers) throws JSONException {
        String enrollmentId =
                Enrollment.getValidEnrollmentId(
                                Uri.parse(uri),
                                request.getAppPackageName(),
                                mEnrollmentDao,
                                sContext,
                                mFlags)
                        .get();
        AsyncRegistration asyncRegistration =
                new AsyncRegistration.Builder()
                        .setRegistrationId(UUID.randomUUID().toString())
                        .setTopOrigin(Uri.parse(publisher))
                        .setOsDestination(null)
                        .setWebDestination(null)
                        .setRegistrant(getRegistrant(request.getAppPackageName()))
                        .setRequestTime(timestamp)
                        .setSourceType(getSourceType(request))
                        .setType(AsyncRegistration.RegistrationType.WEB_SOURCE)
                        .setAdIdPermission(true)
                        .setDebugKeyAllowed(arDebugPermission)
                        .setRegistrationUri(Uri.parse(uri))
                        .build();

        AsyncFetchStatus status = new AsyncFetchStatus();
        Optional<Source> maybeSource = mAsyncSourceFetcher
                .parseSource(asyncRegistration, enrollmentId, headers, status);

        if (maybeSource.isPresent()) {
            Assert.assertTrue(
                    "mAsyncRegistrationQueueRunner.storeSource failed",
                    mDatastoreManager.runInTransaction(
                            measurementDao ->
                                    mAsyncRegistrationQueueRunner.storeSource(
                                            maybeSource.get(), asyncRegistration, measurementDao)));
        } else {
            Assert.assertTrue(sParsingErrors.contains(status.getEntityStatus()));
            addUnparsableRegistration(timestamp, UnparsableRegistrationTypes.SOURCE);
        }
    }

    private void insertTriggerOrRecordUnparsable(
            String destination,
            long timestamp,
            String uri,
            boolean arDebugPermission,
            RegistrationRequest request,
            Map<String, List<String>> headers) throws JSONException {
        String enrollmentId =
                Enrollment.getValidEnrollmentId(
                                Uri.parse(uri),
                                request.getAppPackageName(),
                                mEnrollmentDao,
                                sContext,
                                mFlags)
                        .get();
        AsyncRegistration asyncRegistration =
                new AsyncRegistration.Builder()
                        .setRegistrationId(UUID.randomUUID().toString())
                        .setTopOrigin(Uri.parse(destination))
                        .setRegistrant(getRegistrant(request.getAppPackageName()))
                        .setRequestTime(timestamp)
                        .setType(AsyncRegistration.RegistrationType.WEB_TRIGGER)
                        .setAdIdPermission(true)
                        .setDebugKeyAllowed(arDebugPermission)
                        .setRegistrationUri(Uri.parse(uri))
                        .build();

        AsyncFetchStatus status = new AsyncFetchStatus();
        Optional<Trigger> maybeTrigger = mAsyncTriggerFetcher
                .parseTrigger(asyncRegistration, enrollmentId, headers, status);

        if (maybeTrigger.isPresent()) {
            Assert.assertTrue(
                    "mAsyncRegistrationQueueRunner.storeTrigger failed",
                    mDatastoreManager.runInTransaction(
                            measurementDao ->
                                    mAsyncRegistrationQueueRunner.storeTrigger(
                                            maybeTrigger.get(), measurementDao)));
        } else {
            Assert.assertTrue(sParsingErrors.contains(status.getEntityStatus()));
            addUnparsableRegistration(timestamp, UnparsableRegistrationTypes.TRIGGER);
        }
    }

    private void addUnparsableRegistration(long time, String type) throws JSONException {
        mActualOutput.mUnparsableRegistrationObjects.add(
                new JSONObject()
                        .put(UnparsableRegistrationKeys.TIME, String.valueOf(time))
                        .put(UnparsableRegistrationKeys.TYPE, type));
    }

    private static Source.SourceType getSourceType(RegistrationRequest request) {
        return request.getInputEvent() == null
                ? Source.SourceType.EVENT
                : Source.SourceType.NAVIGATION;
    }

    private static Uri getRegistrant(String packageName) {
        return Uri.parse(ANDROID_APP_SCHEME + "://" + packageName);
    }

    private static String anchorTime(String jsonStr, long time) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            long t0 = json.getJSONObject(TestFormatJsonMapping.TEST_INPUT_KEY)
                    .getJSONArray(TestFormatJsonMapping.REGISTRATIONS_KEY)
                    .getJSONObject(0)
                    .getLong(TestFormatJsonMapping.TIMESTAMP_KEY);
            return ((JSONObject) anchorTime(json, t0, time)).toString();
        } catch (JSONException ignored) {
            return null;
        }
    }

    private static Object anchorTime(Object obj, long t0, long anchor) throws JSONException {
        if (obj instanceof JSONArray) {
            JSONArray newJson = new JSONArray();
            JSONArray jsonArray = (JSONArray) obj;
            for (int i = 0; i < jsonArray.length(); i++) {
                newJson.put(i, anchorTime(jsonArray.get(i), t0, anchor));
            }
            return newJson;
        } else if (obj instanceof JSONObject) {
            JSONObject newJson = new JSONObject();
            JSONObject jsonObj = (JSONObject) obj;
            Set<String> keys = jsonObj.keySet();

            for (String key : keys) {
                if (TIMESTAMP_KEYS_IN_MILLIS.contains(key)) {
                    long time = jsonObj.getLong(key);
                    newJson.put(key, String.valueOf(time - t0 + anchor));
                } else if (TIMESTAMP_KEYS_IN_SECONDS.contains(key)) {
                    long time = TimeUnit.SECONDS.toMillis(jsonObj.getLong(key));
                    newJson.put(
                            key,
                            String.valueOf(TimeUnit.MILLISECONDS.toSeconds(time - t0 + anchor)));
                } else {
                    newJson.put(key, anchorTime(jsonObj.get(key), t0, anchor));
                }
            }
            return newJson;
        } else {
            return obj;
        }
    }
}
