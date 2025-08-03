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
package com.android.adservices.service.measurement.registration;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.doNothingOnErrorLogUtilError;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.verifyErrorLogUtilError;
import static com.android.adservices.service.Flags.MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES;
import static com.android.adservices.service.Flags.MAX_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES;
import static com.android.adservices.service.Flags.MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.android.adservices.service.Flags.MEASUREMENT_MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE;
import static com.android.adservices.service.Flags.MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_INVALID;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.RegistrationRequestFixture;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.net.Uri;
import android.util.Pair;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.WebUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.AttributionConfig;
import com.android.adservices.service.measurement.FilterMap;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerSpecs;
import com.android.adservices.service.measurement.util.Enrollment;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.MeasurementRegistrationResponseStats;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.net.ssl.HttpsURLConnection;

@SpyStatic(FlagsFactory.class)
@SpyStatic(Enrollment.class)
/** Unit tests for {@link AsyncTriggerFetcher} */
@RunWith(Parameterized.class)
public final class AsyncTriggerFetcherTest extends AdServicesExtendedMockitoTestCase {

    private static final String ANDROID_APP_SCHEME = "android-app";
    private static final String ANDROID_APP_SCHEME_URI_PREFIX = ANDROID_APP_SCHEME + "://";
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";
    private static final String TRIGGER_URI = WebUtil.validUrl("https://subdomain.foo.test");
    private static final String ENROLLMENT_ID = "enrollment-id";
    private static final String TOP_ORIGIN = WebUtil.validUrl("https://baz.test");
    private static final long TRIGGER_DATA = 7;
    private static final long PRIORITY = 1;
    private static final String LONG_FILTER_STRING = "12345678901234567890123456";
    private static final String LONG_AGGREGATE_KEY_ID = "12345678901234567890123456";
    private static final String LONG_AGGREGATE_KEY_PIECE = "0x123456789012345678901234567890123";
    private static final long DEDUP_KEY = 100;
    private static final UnsignedLong DEBUG_KEY = new UnsignedLong(34787843L);
    private static final String DEBUG_JOIN_KEY = "SAMPLE_DEBUG_JOIN_KEY";
    private static final String DEFAULT_REDIRECT = WebUtil.validUrl("https://subdomain.bar.test");
    private static final String EVENT_TRIGGERS_1 =
            "[\n"
                    + "{\n"
                    + "  \"trigger_data\": \""
                    + TRIGGER_DATA
                    + "\",\n"
                    + "  \"priority\": \""
                    + PRIORITY
                    + "\",\n"
                    + "  \"deduplication_key\": \""
                    + DEDUP_KEY
                    + "\",\n"
                    + "  \"filters\": [{\n"
                    + "    \"source_type\": [\"navigation\"],\n"
                    + "    \"key_1\": [\"value_1\"] \n"
                    + "   }]\n"
                    + "}"
                    + "]\n";
    private static final String AGGREGATE_DEDUPLICATION_KEYS_1 =
            "[{\"deduplication_key\": \""
                    + DEDUP_KEY
                    + "\",\n"
                    + "\"filters\": {\n"
                    + "  \"category_1\": [\"filter\"],\n"
                    + "  \"category_2\": [\"filter\"] \n"
                    + " },"
                    + "\"not_filters\": {\n"
                    + "  \"category_1\": [\"filter\"],\n"
                    + "  \"category_2\": [\"filter\"] \n"
                    + "}}"
                    + "]";
    private static final String LIST_TYPE_REDIRECT_URI =
            WebUtil.validUrl("https://subdomain.bar.test");
    private static final String LOCATION_TYPE_REDIRECT_URI =
            WebUtil.validUrl("https://example.test");
    private static final String LOCATION_TYPE_REDIRECT_WELLKNOWN_URI =
            WebUtil.validUrl(
                    LOCATION_TYPE_REDIRECT_URI
                            + "/"
                            + AsyncRedirects.WELL_KNOWN_PATH_SEGMENT
                            + "?"
                            + AsyncRedirects.WELL_KNOWN_QUERY_PARAM
                            + "="
                            + Uri.encode(LOCATION_TYPE_REDIRECT_URI));
    private static final Uri REGISTRATION_URI_1 = WebUtil.validUri("https://subdomain.foo.test");
    private static final WebTriggerParams TRIGGER_REGISTRATION_1 =
            new WebTriggerParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();
    private static final String DEFAULT_EVENT_TRIGGER_DATA = "[]";

    private static final int UNKNOWN_SOURCE_TYPE = 0;
    private static final int UNKNOWN_REGISTRATION_SURFACE_TYPE = 0;
    private static final int APP_REGISTRATION_SURFACE_TYPE = 2;
    private static final int UNKNOWN_STATUS = 0;
    private static final int SUCCESS_STATUS = 1;
    private static final int UNKNOWN_REGISTRATION_FAILURE_TYPE = 0;
    private static final String PLATFORM_AD_ID_VALUE = "SAMPLE_PLATFORM_AD_ID_VALUE";
    private static final String DEBUG_AD_ID_VALUE = "SAMPLE_DEBUG_AD_ID_VALUE";
    private static final int mMaxAggregateDeduplicationKeysPerRegistration =
            Flags.DEFAULT_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION;

    private AsyncTriggerFetcher mFetcher;

    @Mock private HttpsURLConnection mUrlConnection;
    @Mock private HttpsURLConnection mUrlConnection1;
    @Mock private EnrollmentDao mEnrollmentDao;
    @Mock private Flags mFlags;
    @Mock private AdServicesLogger mLogger;

    // Parameterised setup to run all the tests once with ARA parsing V1 flag on, and once with the
    // flag off.
    private final Boolean mAraParsingAlignmentV1Enabled;
    private final Boolean mEnableUpdateTriggerHeaderSizeLimit;

    @Parameters(name = "parsing_alignment_v1_enabled={0}, enable_update_trigger_header_limit={1}")
    public static Collection<Object[]> getConfig() throws IOException, JSONException {
        return List.of(
                new Object[] {Boolean.TRUE, Boolean.TRUE},
                new Object[] {Boolean.FALSE, Boolean.TRUE},
                new Object[] {Boolean.FALSE, Boolean.FALSE},
                new Object[] {Boolean.TRUE, Boolean.FALSE});
    }

    public AsyncTriggerFetcherTest(
            Boolean araParsingAlignmentV1Enabled, Boolean enableUpdateTriggerHeaderSizeLimit) {
        mAraParsingAlignmentV1Enabled = araParsingAlignmentV1Enabled;
        mEnableUpdateTriggerHeaderSizeLimit = enableUpdateTriggerHeaderSizeLimit;
    }

    @Before
    public void setup() {
        extendedMockito.mockGetFlags(FlagsFactory.getFlagsForTest());
        mFetcher = spy(new AsyncTriggerFetcher(sContext, mEnrollmentDao, mFlags));
        // For convenience, return the same enrollment-ID since we're using many arbitrary
        // registration URIs and not yet enforcing uniqueness of enrollment.
        ExtendedMockito.doReturn(Optional.of(ENROLLMENT_ID))
                .when(
                        () ->
                                Enrollment.getValidEnrollmentId(
                                        any(), anyString(), any(), any(), any()));
        when(mFlags.getMeasurementEnableXNA()).thenReturn(false);
        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist())
                .thenReturn(SourceFixture.ValidSourceParams.ENROLLMENT_ID);
        when(mFlags.getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist()).thenReturn("");
        when(mFlags.getMeasurementEnableAraParsingAlignmentV1())
                .thenReturn(mAraParsingAlignmentV1Enabled);
        when(mFlags.getMeasurementMaxAggregateKeysPerTriggerRegistration())
                .thenReturn(Flags.MEASUREMENT_MAX_AGGREGATE_KEYS_PER_TRIGGER_REGISTRATION);
        when(mFlags.getMeasurementMaxAggregateDeduplicationKeysPerRegistration())
                .thenReturn(mMaxAggregateDeduplicationKeysPerRegistration);
        when(mFlags.getMeasurementMaxAttributionFilters())
                .thenReturn(Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS);
        when(mFlags.getMeasurementMaxFilterMapsPerFilterSet())
                .thenReturn(Flags.DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET);
        when(mFlags.getMeasurementMaxValuesPerAttributionFilter())
                .thenReturn(Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER);
        when(mFlags.getMeasurementMaxSumOfAggregateValuesPerSource())
                .thenReturn(MEASUREMENT_MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE);
        when(mFlags.getMeasurementMaxReportingRegisterSourceExpirationInSeconds())
                .thenReturn(MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        when(mFlags.getMeasurementMinReportingRegisterSourceExpirationInSeconds())
                .thenReturn(MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        when(mFlags.getMeasurementEnableUpdateTriggerHeaderLimit())
                .thenReturn(mEnableUpdateTriggerHeaderSizeLimit);
        when(mFlags.getMaxResponseBasedRegistrationPayloadSizeBytes())
                .thenReturn(MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES);
        when(mFlags.getMaxTriggerRegistrationHeaderSizeBytes())
                .thenReturn(MAX_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES);
    }

    @Test
    public void testBasicTriggerRequest() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        MeasurementRegistrationResponseStats expectedStats =
                new MeasurementRegistrationResponseStats.Builder(
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER,
                                223,
                                UNKNOWN_SOURCE_TYPE,
                                APP_REGISTRATION_SURFACE_TYPE,
                                SUCCESS_STATUS,
                                UNKNOWN_REGISTRATION_FAILURE_TYPE,
                                0,
                                "",
                                0,
                                false)
                        .setAdTechDomain(null)
                        .build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        asyncFetchStatus.setRegistrationDelay(0L);
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(
                asyncRegistration.getTopOrigin().toString(),
                result.getAttributionDestination().toString());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
        FetcherUtil.emitHeaderMetrics(
                MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES,
                mLogger,
                asyncRegistration,
                asyncFetchStatus);
        verify(mLogger).logMeasurementRegistrationsResponseSize(eq(expectedStats));
    }

    @Test
    public void testBasicTriggerRequest_withAggregateDeduplicationKey() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        MeasurementRegistrationResponseStats expectedStats =
                new MeasurementRegistrationResponseStats.Builder(
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER,
                                436,
                                UNKNOWN_SOURCE_TYPE,
                                APP_REGISTRATION_SURFACE_TYPE,
                                SUCCESS_STATUS,
                                UNKNOWN_REGISTRATION_FAILURE_TYPE,
                                0,
                                "",
                                0,
                                false)
                        .setAdTechDomain(null)
                        .build();
        String wrappedFilters =
                "[{\n"
                        + "  \"category_1\": [\"filter\"],\n"
                        + "  \"category_2\": [\"filter\"] \n"
                        + " }]";
        String wrappedNotFilters =
                "[{\n"
                        + "  \"category_1\": [\"filter\"],\n"
                        + "  \"category_2\": [\"filter\"] \n"
                        + "}]";
        String expectedAggregateDedupKeys =
                "[{\"deduplication_key\": \""
                        + DEDUP_KEY
                        + "\",\n"
                        + "\"filters\": "
                        + wrappedFilters
                        + ","
                        + "\"not_filters\":"
                        + wrappedNotFilters
                        + "}"
                        + "]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ","
                                                + "\"aggregatable_deduplication_keys\":"
                                                + AGGREGATE_DEDUPLICATION_KEYS_1
                                                + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        asyncFetchStatus.setRegistrationDelay(0L);
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(
                asyncRegistration.getTopOrigin().toString(),
                result.getAttributionDestination().toString());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(
                new JSONArray(expectedAggregateDedupKeys).toString(),
                result.getAggregateDeduplicationKeys());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
        FetcherUtil.emitHeaderMetrics(
                MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES,
                mLogger,
                asyncRegistration,
                asyncFetchStatus);
        verify(mLogger).logMeasurementRegistrationsResponseSize(eq(expectedStats));
    }

    @Test
    public void triggerRequest_aggregateDeduplicationKey_dedupKeyNotAString_fails()
            throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String wrappedFilters =
                "[{"
                        + "\"category_1\":[\"filter\"],"
                        + "\"category_2\":[\"filter\"]"
                        + "}]";
        String wrappedNotFilters =
                "[{"
                        + "\"category_1\":[\"filter\"],"
                        + "\"category_2\":[\"filter\"]"
                        + "}]";
        String aggregateDedupKeys =
                "[{\"deduplication_key\":756,"
                        + "\"filters\":" + wrappedFilters + ","
                        + "\"not_filters\":" + wrappedNotFilters
                        + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ","
                                                + "\"aggregatable_deduplication_keys\":"
                                                + aggregateDedupKeys
                                                + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void triggerRequest_aggregateDeduplicationKey_dedupKeyNotAString_succeeds()
            throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String wrappedFilters =
                "[{"
                        + "\"category_1\":[\"filter\"],"
                        + "\"category_2\":[\"filter\"]"
                        + "}]";
        String wrappedNotFilters =
                "[{"
                        + "\"category_1\":[\"filter\"],"
                        + "\"category_2\":[\"filter\"]"
                        + "}]";
        String aggregateDedupKeys =
                "[{\"deduplication_key\":756,"
                        + "\"filters\":" + wrappedFilters + ","
                        + "\"not_filters\":" + wrappedNotFilters
                        + "}]";
        String expectedAggregateDedupKeys =
                "[{\"deduplication_key\":\"756\","
                        + "\"filters\":" + wrappedFilters + ","
                        + "\"not_filters\":" + wrappedNotFilters
                        + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ","
                                                + "\"aggregatable_deduplication_keys\":"
                                                + aggregateDedupKeys
                                                + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(expectedAggregateDedupKeys, result.getAggregateDeduplicationKeys());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void triggerRequest_aggregateDeduplicationKey_dedupKeyNegative_fails()
            throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String wrappedFilters =
                "[{"
                        + "\"category_1\":[\"filter\"],"
                        + "\"category_2\":[\"filter\"]"
                        + "}]";
        String wrappedNotFilters =
                "[{"
                        + "\"category_1\":[\"filter\"],"
                        + "\"category_2\":[\"filter\"]"
                        + "}]";
        String aggregateDedupKeys =
                "[{\"deduplication_key\":\"-756\","
                        + "\"filters\":" + wrappedFilters + ","
                        + "\"not_filters\":" + wrappedNotFilters
                        + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ","
                                                + "\"aggregatable_deduplication_keys\":"
                                                + aggregateDedupKeys
                                                + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void triggerRequest_aggregateDeduplicationKey_dedupKeyNegative_succeeds()
            throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String wrappedFilters =
                "[{"
                        + "\"category_1\":[\"filter\"],"
                        + "\"category_2\":[\"filter\"]"
                        + "}]";
        String wrappedNotFilters =
                "[{"
                        + "\"category_1\":[\"filter\"],"
                        + "\"category_2\":[\"filter\"]"
                        + "}]";
        String aggregateDedupKeys =
                "[{\"deduplication_key\":\"-756\","
                        + "\"filters\":" + wrappedFilters + ","
                        + "\"not_filters\":" + wrappedNotFilters
                        + "}]";
        String expectedAggregateDedupKeys =
                "[{\"filters\":" + wrappedFilters + ","
                        + "\"not_filters\":" + wrappedNotFilters
                        + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ","
                                                + "\"aggregatable_deduplication_keys\":"
                                                + aggregateDedupKeys
                                                + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(expectedAggregateDedupKeys, result.getAggregateDeduplicationKeys());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void triggerRequest_aggregateDeduplicationKey_dedupKeyTooLarge_fails()
            throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String wrappedFilters =
                "[{"
                        + "\"category_1\":[\"filter\"],"
                        + "\"category_2\":[\"filter\"]"
                        + "}]";
        String wrappedNotFilters =
                "[{"
                        + "\"category_1\":[\"filter\"],"
                        + "\"category_2\":[\"filter\"]"
                        + "}]";
        String aggregateDedupKeys =
                "[{\"deduplication_key\":\"18446744073709551616\","
                        + "\"filters\":" + wrappedFilters + ","
                        + "\"not_filters\":" + wrappedNotFilters
                        + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ","
                                                + "\"aggregatable_deduplication_keys\":"
                                                + aggregateDedupKeys
                                                + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void triggerRequest_aggregateDeduplicationKey_dedupKeyTooLarge_succeeds()
            throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String wrappedFilters =
                "[{"
                        + "\"category_1\":[\"filter\"],"
                        + "\"category_2\":[\"filter\"]"
                        + "}]";
        String wrappedNotFilters =
                "[{"
                        + "\"category_1\":[\"filter\"],"
                        + "\"category_2\":[\"filter\"]"
                        + "}]";
        String aggregateDedupKeys =
                "[{\"deduplication_key\":\"18446744073709551616\","
                        + "\"filters\":" + wrappedFilters + ","
                        + "\"not_filters\":" + wrappedNotFilters
                        + "}]";
        String expectedAggregateDedupKeys =
                "[{\"filters\":" + wrappedFilters + ","
                        + "\"not_filters\":" + wrappedNotFilters
                        + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ","
                                                + "\"aggregatable_deduplication_keys\":"
                                                + aggregateDedupKeys
                                                + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(expectedAggregateDedupKeys, result.getAggregateDeduplicationKeys());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void triggerRequest_aggregateDeduplicationKey_dedupKeyNotAnInt_fails()
            throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String wrappedFilters =
                "[{"
                        + "\"category_1\":[\"filter\"],"
                        + "\"category_2\":[\"filter\"]"
                        + "}]";
        String wrappedNotFilters =
                "[{"
                        + "\"category_1\":[\"filter\"],"
                        + "\"category_2\":[\"filter\"]"
                        + "}]";
        String aggregateDedupKeys =
                "[{\"deduplication_key\":\"756a\","
                        + "\"filters\":" + wrappedFilters + ","
                        + "\"not_filters\":" + wrappedNotFilters
                        + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ","
                                                + "\"aggregatable_deduplication_keys\":"
                                                + aggregateDedupKeys
                                                + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void triggerRequest_aggregateDeduplicationKey_dedupKeyNotAnInt_succeeds()
            throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String wrappedFilters =
                "[{"
                        + "\"category_1\":[\"filter\"],"
                        + "\"category_2\":[\"filter\"]"
                        + "}]";
        String wrappedNotFilters =
                "[{"
                        + "\"category_1\":[\"filter\"],"
                        + "\"category_2\":[\"filter\"]"
                        + "}]";
        String aggregateDedupKeys =
                "[{\"deduplication_key\":\"756a\","
                        + "\"filters\":" + wrappedFilters + ","
                        + "\"not_filters\":" + wrappedNotFilters
                        + "}]";
        String expectedAggregateDedupKeys =
                "[{\"filters\":" + wrappedFilters + ","
                        + "\"not_filters\":" + wrappedNotFilters
                        + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ","
                                                + "\"aggregatable_deduplication_keys\":"
                                                + aggregateDedupKeys
                                                + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(expectedAggregateDedupKeys, result.getAggregateDeduplicationKeys());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void triggerRequest_aggregateDeduplicationKey_dedupKeyMissing_succeeds()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String wrappedFilters =
                "[{"
                        + "\"category_1\":[\"filter\"],"
                        + "\"category_2\":[\"filter\"]"
                        + "}]";
        String wrappedNotFilters =
                "[{"
                        + "\"category_1\":[\"filter\"],"
                        + "\"category_2\":[\"filter\"]"
                        + "}]";
        String aggregateDedupKeys =
                "[{\"filters\":" + wrappedFilters + ","
                        + "\"not_filters\":" + wrappedNotFilters
                        + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ","
                                                + "\"aggregatable_deduplication_keys\":"
                                                + aggregateDedupKeys
                                                + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(aggregateDedupKeys, result.getAggregateDeduplicationKeys());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    // Tests for redirect types

    @Test
    public void testRedirectType_bothRedirectHeaderTypes_choosesListType() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        Map<String, List<String>> headers = getDefaultHeaders();

        // Populate both 'list' and 'location' type headers
        headers.put(AsyncRedirects.REDIRECT_LIST_HEADER_KEY, List.of(LIST_TYPE_REDIRECT_URI));
        headers.put(
                AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY, List.of(LOCATION_TYPE_REDIRECT_URI));

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertTriggerRegistration(asyncRegistration, result);

        assertEquals(2, asyncRedirects.getRedirects().size());
        assertEquals(
                LIST_TYPE_REDIRECT_URI,
                asyncRedirects
                        .getRedirectsByType(AsyncRegistration.RedirectType.LIST)
                        .get(0)
                        .getUri()
                        .toString());
        assertEquals(
                LOCATION_TYPE_REDIRECT_URI,
                asyncRedirects
                        .getRedirectsByType(AsyncRegistration.RedirectType.LOCATION)
                        .get(0)
                        .getUri()
                        .toString());

        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testRedirectType_locationRedirectHeaderType_choosesLocationType() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(302);
        Map<String, List<String>> headers = getDefaultHeaders();

        // Populate only 'location' type header
        headers.put(
                AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY, List.of(LOCATION_TYPE_REDIRECT_URI));

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertTriggerRegistration(asyncRegistration, result);

        assertEquals(1, asyncRedirects.getRedirects().size());
        assertEquals(
                LOCATION_TYPE_REDIRECT_URI,
                asyncRedirects.getRedirects().get(0).getUri().toString());

        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testRedirectType_locationRedirectType_acceptsListType() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(302);
        Map<String, List<String>> headers = getDefaultHeaders();

        // Populate both 'list' and 'location' type headers
        headers.put(AsyncRedirects.REDIRECT_LIST_HEADER_KEY, List.of(LIST_TYPE_REDIRECT_URI));
        headers.put(
                AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY, List.of(LOCATION_TYPE_REDIRECT_URI));

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertTriggerRegistration(asyncRegistration, result);

        assertEquals(2, asyncRedirects.getRedirects().size());
        assertEquals(
                LOCATION_TYPE_REDIRECT_URI,
                asyncRedirects
                        .getRedirectsByType(AsyncRegistration.RedirectType.LOCATION)
                        .get(0)
                        .getUri()
                        .toString());
        assertEquals(
                LIST_TYPE_REDIRECT_URI,
                asyncRedirects
                        .getRedirectsByType(AsyncRegistration.RedirectType.LIST)
                        .get(0)
                        .getUri()
                        .toString());

        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testRedirectType_locationRedirectHeaderType_wellKnownPath() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(302);
        when(mFlags.getMeasurementEnableRedirectToWellKnownPath()).thenReturn(true);
        Map<String, List<String>> headers = getDefaultHeaders();

        // Populate only 'location' type header
        headers.put(
                AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY, List.of(LOCATION_TYPE_REDIRECT_URI));
        headers.put(
                AsyncRedirects.HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG,
                List.of(AsyncRedirects.REDIRECT_302_TO_WELL_KNOWN));

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertTriggerRegistration(asyncRegistration, result);

        assertEquals(1, asyncRedirects.getRedirects().size());
        assertEquals(
                LOCATION_TYPE_REDIRECT_WELLKNOWN_URI,
                asyncRedirects.getRedirects().get(0).getUri().toString());

        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testRedirectType_locationRedirectType_wellKnownPath_acceptsList() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(302);
        when(mFlags.getMeasurementEnableRedirectToWellKnownPath()).thenReturn(true);
        Map<String, List<String>> headers = getDefaultHeaders();

        headers.put(
                AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY, List.of(LOCATION_TYPE_REDIRECT_URI));
        headers.put(AsyncRedirects.REDIRECT_LIST_HEADER_KEY, List.of(LIST_TYPE_REDIRECT_URI));
        headers.put(
                AsyncRedirects.HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG,
                List.of(AsyncRedirects.REDIRECT_302_TO_WELL_KNOWN));

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertTriggerRegistration(asyncRegistration, result);

        assertEquals(2, asyncRedirects.getRedirects().size());
        assertEquals(
                LOCATION_TYPE_REDIRECT_WELLKNOWN_URI,
                asyncRedirects
                        .getRedirectsByType(AsyncRegistration.RedirectType.LOCATION)
                        .get(0)
                        .getUri()
                        .toString());
        assertEquals(
                LIST_TYPE_REDIRECT_URI,
                asyncRedirects
                        .getRedirectsByType(AsyncRegistration.RedirectType.LIST)
                        .get(0)
                        .getUri()
                        .toString());

        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testWellKnownPathHeader_noop() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mFlags.getMeasurementEnableRedirectToWellKnownPath()).thenReturn(true);
        Map<String, List<String>> headers = getDefaultHeaders();

        // Populate behavior config header, but no redirects.
        headers.put(
                AsyncRedirects.HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG,
                List.of(AsyncRedirects.REDIRECT_302_TO_WELL_KNOWN));

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertTriggerRegistration(asyncRegistration, result);

        assertEquals(0, asyncRedirects.getRedirects().size());

        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testWellKnownPathHeader_acceptsListTypeWithNoLocationType() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mFlags.getMeasurementEnableRedirectToWellKnownPath()).thenReturn(true);
        Map<String, List<String>> headers = getDefaultHeaders();

        // Populate behavior config header, but no Location type redirect.
        headers.put(
                AsyncRedirects.HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG,
                List.of(AsyncRedirects.REDIRECT_302_TO_WELL_KNOWN));
        headers.put(AsyncRedirects.REDIRECT_LIST_HEADER_KEY, List.of(LIST_TYPE_REDIRECT_URI));

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertTriggerRegistration(asyncRegistration, result);

        assertEquals(1, asyncRedirects.getRedirects().size());

        assertEquals(
                LIST_TYPE_REDIRECT_URI,
                asyncRedirects
                        .getRedirectsByType(AsyncRegistration.RedirectType.LIST)
                        .get(0)
                        .getUri()
                        .toString());

        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testRedirectType_locationRedirectHeaderType_wellKnownHeaderMisconfigured()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(302);
        when(mFlags.getMeasurementEnableRedirectToWellKnownPath()).thenReturn(true);
        Map<String, List<String>> headers = getDefaultHeaders();

        headers.put(
                AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY, List.of(LOCATION_TYPE_REDIRECT_URI));
        headers.put(AsyncRedirects.HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG, List.of("BAD"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertTriggerRegistration(asyncRegistration, result);

        assertEquals(1, asyncRedirects.getRedirects().size());
        assertEquals(
                LOCATION_TYPE_REDIRECT_URI,
                asyncRedirects.getRedirects().get(0).getUri().toString());

        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testRedirectType_locationRedirectHeaderType_wellKnownHeaderNull() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(302);
        when(mFlags.getMeasurementEnableRedirectToWellKnownPath()).thenReturn(true);
        Map<String, List<String>> headers = getDefaultHeaders();

        headers.put(
                AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY, List.of(LOCATION_TYPE_REDIRECT_URI));
        headers.put(AsyncRedirects.HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG, null);

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertTriggerRegistration(asyncRegistration, result);

        assertEquals(1, asyncRedirects.getRedirects().size());
        assertEquals(
                LOCATION_TYPE_REDIRECT_URI,
                asyncRedirects.getRedirects().get(0).getUri().toString());

        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    // End tests for redirect types

    @Test
    public void fetchTrigger_eventTriggerDataNull_eventTriggerDataEqualsEmptyArray()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData = "null";
        String expectedResult = DEFAULT_EVENT_TRIGGER_DATA;
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(expectedResult, result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_tooManyFilterMaps() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder eventTriggers = new StringBuilder("[{\"trigger_data\":\"2\",\"filters\":[");
        for (int i = 0; i < Flags.DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET + 1; i++) {
            eventTriggers.append("{\"key-" + i + "\":[\"val1" + i + "\", \"val2" + i + "\"]}");
        }
        eventTriggers.append("}]");
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggers + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_filterMapIncludesReservedPrefix_fails()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggers = "[{\"trigger_data\":\"2\",\"filters\":["
                + "{\"_something_reserved\":[\"val1\", \"val2\"]}}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggers + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchTrigger_eventTriggerDataMissing_eventTriggerDataEqualsEmptyArray()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String expectedResult = DEFAULT_EVENT_TRIGGER_DATA;
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"debug_key\": \"" + DEBUG_KEY + "\"}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(expectedResult, result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchTrigger_eventTriggerDataEmptyObjects_emptyObjectsPopulated() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData = "[{\"trigger_data\":\"2\"},{},{}]";
        String expectedResult =
                "[{\"trigger_data\":\"2\"},{\"trigger_data\":\"0\"},{\"trigger_data\":\"0\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(expectedResult, result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_tooManyNotFilterMaps() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder eventTriggers = new StringBuilder(
                "[{\"trigger_data\":\"2\",\"not_filters\":[");
        int i;
        for (i = 0; i < Flags.DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET; i++) {
            eventTriggers.append("{\"key-" + i + "\":[\"val1" + i + "\", \"val2" + i + "\"]},");
        }
        eventTriggers.append("{\"key-" + i + "\":[\"val1" + i + "\", \"val2" + i + "\"]}");
        eventTriggers.append("}]");
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggers + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_notFilterMapIncludesReservedPrefix_fails()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggers = "[{\"trigger_data\":\"2\",\"not_filters\":["
                + "{\"_something_reserved\":[\"val1\", \"val2\"]}}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggers + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchTrigger_triggerDataNotAString_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData = "[{\"trigger_data\":2,\"priority\":\"101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchTrigger_triggerDataNegative_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData = "[{\"trigger_data\":\"-2\",\"priority\":\"101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchTrigger_triggerDataNegative_triggerDataEqualsZero() throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData = "[{\"trigger_data\":\"-2\",\"priority\":\"101\"}]";
        String expectedResult = "[{\"trigger_data\":\"0\",\"priority\":\"101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(expectedResult, result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchTrigger_triggerDataTooLarge_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"18446744073709551616\",\"priority\":\"101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchTrigger_triggerDataTooLarge_triggerDataEqualsZero() throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"18446744073709551616\",\"priority\":\"101\"}]";
        String expectedResult = "[{\"trigger_data\":\"0\",\"priority\":\"101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(expectedResult, result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchTrigger_triggerDataNotAnInt_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData = "[{\"trigger_data\":\"101z\",\"priority\":\"101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchTrigger_triggerDataNotAnInt_triggerDataEqualsZero() throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData = "[{\"trigger_data\":\"101z\",\"priority\":\"101\"}]";
        String expectedResult = "[{\"trigger_data\":\"0\",\"priority\":\"101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(expectedResult, result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_triggerData_uses64thBit() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"18446744073709551615\",\"priority\":\"101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(eventTriggerData, result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerDataWithValue_success() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"14\",\"priority\":\"101\",\"value\":156}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(eventTriggerData, result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerDataWithValueTooSmall_fail() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"14\",\"priority\":\"101\",\"value\":0}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerDataWithValueTooLarge_fail() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        long tooLarge = TriggerSpecs.MAX_BUCKET_THRESHOLD + 1L;
        String eventTriggerData =
                "[{\"trigger_data\":\"14\",\"priority\":\"101\",\"value\":" + tooLarge + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerDataWithValueAsString_fail() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"14\",\"priority\":\"101\",\"value\":\"123\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerDataWithValueAsString_success() throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"14\",\"priority\":\"101\",\"value\":\"123\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        String expectedEventTriggerData =
                "[{\"trigger_data\":\"14\",\"priority\":\"101\",\"value\":123}]";
        assertEquals(expectedEventTriggerData, result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_priority_negative() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData = "[{\"trigger_data\":\"2\",\"priority\":\"-101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(eventTriggerData, result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_priorityNotAString_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":74}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_priorityTooLarge_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"18446744073709551615\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_priorityTooLarge_ignores() throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"18446744073709551615\"}]";
        String expectedResult = "[{\"trigger_data\":\"2\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(expectedResult, result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_priorityNotAnInt_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData = "[{\"trigger_data\":\"2\",\"priority\":\"a101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_priorityNotAnInt_ignores() throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData = "[{\"trigger_data\":\"2\",\"priority\":\"a101\"}]";
        String expectedResult = "[{\"trigger_data\":\"2\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(expectedResult, result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_deduplicationKeyNotAString_fails()
            throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"deduplication_key\":34}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_deduplicationKeyNegative_fails()
            throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"deduplication_key\":\"-34\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_deduplicationKeyNegative_ignores()
            throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"deduplication_key\":\"-34\"}]";
        String expectedResult = "[{\"trigger_data\":\"2\",\"priority\":\"101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(expectedResult, result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_deduplicationKeyTooLarge_fails()
            throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"deduplication_key\":\"18446744073709551616\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_deduplicationKeyTooLarge_ignores()
            throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"deduplication_key\":\"18446744073709551616\"}]";
        String expectedResult = "[{\"trigger_data\":\"2\",\"priority\":\"101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(expectedResult, result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_deduplicationKeyNotAnInt_fails()
            throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"deduplication_key\":\"145l\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_deduplicationKeyNotAnInt_ignores()
            throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"deduplication_key\":\"145l\"}]";
        String expectedResult = "[{\"trigger_data\":\"2\",\"priority\":\"101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(expectedResult, result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_deduplicationKey_uses64thBit()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"deduplication_key\":\"18446744073709551615\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(eventTriggerData, result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_wrapsFilters() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"filters\":{\"id\":[\"val1\",\"val2\"]}}]";
        String eventTriggerDataWithWrappedFilters =
                "[{\"trigger_data\":\"2\",\"filters\":[{\"id\":[\"val1\",\"val2\"]}]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(eventTriggerDataWithWrappedFilters, result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_filters_invalidJson() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters =
                "{\"product\":[\"1234\",\"2345\"], \"\"\":[\"id\"]}";
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\",\"filters\":" + filters + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_filters_tooManyFilters() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder filters = new StringBuilder("{");
        filters.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
                        .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                        .collect(Collectors.joining(",")));
        filters.append("}");
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\",\"filters\":" + filters + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_filters_keyTooLong() throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters =
                "{\"product\":[\"1234\",\"2345\"], \"" + LONG_FILTER_STRING + "\":[\"id\"]}";
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\",\"filters\":" + filters + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_filters_tooManyValues() throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder filters =
                new StringBuilder(
                        "{"
                                + "\"filter-string-1\": [\"filter-value-1\"],"
                                + "\"filter-string-2\": [");
        filters.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
                        .mapToObj(i -> "\"filter-value-" + i + "\"")
                        .collect(Collectors.joining(",")));
        filters.append("]}");
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\",\"filters\":" + filters + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_filters_valueTooLong() throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters =
                "{\"product\":[\"1234\",\"" + LONG_FILTER_STRING + "\"], \"ctid\":[\"id\"]}";
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\",\"filters\":" + filters + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_wrapsNotFilters() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"not_filters\":{\"id\":[\"val1\",\"val2\"]}}]";
        String eventTriggerDataWithWrappedNotFilters =
                "[{\"trigger_data\":\"2\",\"not_filters\":[{\"id\":[\"val1\",\"val2\"]}]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(eventTriggerDataWithWrappedNotFilters, result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_notFilters_invalidJson() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String notFilters =
                "{\"product\":[\"1234\",\"2345\"], \"\"\":[\"id\"]}";
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"not_filters\":"
                        + notFilters
                        + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_notFilters_tooManyFilters() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder notFilters = new StringBuilder("{");
        notFilters.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
                        .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                        .collect(Collectors.joining(",")));
        notFilters.append("}");
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"not_filters\":"
                        + notFilters
                        + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_notFilters_keyTooLong() throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String notFilters =
                "{\"product\":[\"1234\",\"2345\"], \"" + LONG_FILTER_STRING + "\":[\"id\"]}";
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"not_filters\":"
                        + notFilters
                        + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_notFilters_tooManyValues() throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder notFilters =
                new StringBuilder(
                        "{"
                                + "\"filter-string-1\": [\"filter-value-1\"],"
                                + "\"filter-string-2\": [");
        notFilters.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
                        .mapToObj(i -> "\"filter-value-" + i + "\"")
                        .collect(Collectors.joining(",")));
        notFilters.append("]}");
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"not_filters\":"
                        + notFilters
                        + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_notFilters_valueTooLong() throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String notFilters =
                "{\"product\":[\"1234\",\"" + LONG_FILTER_STRING + "\"], \"ctid\":[\"id\"]}";
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"not_filters\":"
                        + notFilters
                        + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_wrapsFilters() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters =
                "{\"product\":[\"1234\",\"2345\"],\"cid\":[\"id\"]}";
        String wrappedFilters =
                "[{\"product\":[\"1234\",\"2345\"],\"cid\":[\"id\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"filters\":" + filters + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(wrappedFilters, result.getFilters());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_tooManyFilterMaps() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder filters = new StringBuilder("[");
        for (int i = 0; i < Flags.DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET + 1; i++) {
            filters.append("{\"key-" + i + "\":[\"val1" + i + "\", \"val2" + i + "\"]}");
        }
        filters.append("]");
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"filters\":" + filters + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_filterMapIncludesReservedPrefix_fails() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters = "[{\"_something_reserved\":[\"val1\", \"val2\"]}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"filters\":" + filters + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_filterMapIncludesNullValue_fails() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters = "[{\"something\":[\"val1\", null]}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"filters\":" + filters + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_filterMapIncludesNumericValue_fails() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters = "[{\"something\":[\"val1\", 347]}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"filters\":" + filters + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_filters_invalidJson() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters =
                "{\"product\":[\"1234\",\"2345\"], \"\"\":[\"id\"]}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"filters\":" + filters + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_AggregateDeduplicationKeys_tooManyEntries() throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        int maxAggregateDeduplicationKeysPerRegistration =
                Flags.DEFAULT_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION;
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder tooManyEntries = new StringBuilder("[");
        tooManyEntries.append(
                IntStream.range(0, maxAggregateDeduplicationKeysPerRegistration + 1)
                        .mapToObj(i -> "{\"deduplication_key\": \"" + i + "\"}")
                        .collect(Collectors.joining(",")));
        tooManyEntries.append("]");
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ","
                                                + "\"aggregatable_deduplication_keys\":"
                                                + tooManyEntries
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_AggregateDeduplicationKeys_missingDeduplicationKey()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder deduplicationKeys = new StringBuilder("[");
        int maxAggregateDeduplicationKeysPerRegistration =
                Flags.DEFAULT_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION;
        deduplicationKeys.append(
                IntStream.range(0, maxAggregateDeduplicationKeysPerRegistration)
                        .mapToObj(i -> "{\"deduplication_key\":}")
                        .collect(Collectors.joining(",")));
        deduplicationKeys.append("]");
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ","
                                                + "\"aggregatable_deduplication_keys\":"
                                                + deduplicationKeys
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_AggregateDeduplicationKeys_tooManyFilters() throws Exception {
        int maxAggregateDeduplicationKeysPerRegistration =
                Flags.DEFAULT_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION;
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder filters = new StringBuilder("{");
        filters.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
                        .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                        .collect(Collectors.joining(",")));
        filters.append("}");
        StringBuilder notFilters = new StringBuilder("{");
        notFilters.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS)
                        .mapToObj(i -> "\"not-filter-string-" + i + "\": [\"not-filter-value\"]")
                        .collect(Collectors.joining(",")));
        notFilters.append("}");
        StringBuilder deduplicationKeys = new StringBuilder("[");
        deduplicationKeys.append(
                IntStream.range(0, maxAggregateDeduplicationKeysPerRegistration)
                        .mapToObj(
                                i ->
                                        "{\"deduplication_key\":"
                                                + "\""
                                                + i
                                                + "\""
                                                + ",\"filters\" :"
                                                + filters
                                                + ","
                                                + "\"not_filters\" :\""
                                                + notFilters
                                                + "}")
                        .collect(Collectors.joining(",")));
        deduplicationKeys.append("]");

        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ",\"aggregatable_deduplication_keys\":"
                                                + deduplicationKeys
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_AggregateDeduplicationKeys_tooManyNotFilters() throws Exception {
        int maxAggregateDeduplicationKeysPerRegistration =
                Flags.DEFAULT_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION;
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder filters = new StringBuilder("{");
        filters.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS)
                        .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                        .collect(Collectors.joining(",")));
        filters.append("}");
        StringBuilder notFilters = new StringBuilder("{");
        notFilters.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
                        .mapToObj(i -> "\"not-filter-string-" + i + "\": [\"not-filter-value\"]")
                        .collect(Collectors.joining(",")));
        notFilters.append("}");
        StringBuilder deduplicationKeys = new StringBuilder("[");
        deduplicationKeys.append(
                IntStream.range(0, maxAggregateDeduplicationKeysPerRegistration)
                        .mapToObj(
                                i ->
                                        "{\"deduplication_key\":"
                                                + "\""
                                                + i
                                                + "\""
                                                + ",\"filters\" :"
                                                + filters
                                                + ","
                                                + "\"not_filters\" :\""
                                                + notFilters
                                                + "}")
                        .collect(Collectors.joining(",")));
        deduplicationKeys.append("]");

        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ","
                                                + "\"aggregatable_deduplication_keys\":"
                                                + deduplicationKeys
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_AggregateDeduplicationKeys_deduplicationKeyIsEmpty()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ","
                                                + "\"aggregatable_deduplication_keys\":{}"
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_AggregateDeduplicationKeys_deduplicationKeysNotPresent()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ","
                                                + "\"aggregatable_deduplication_keys\":"
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_filters_tooManyFilters() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder filters = new StringBuilder("{");
        filters.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
                        .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                        .collect(Collectors.joining(",")));
        filters.append("}");
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"filters\":" + filters + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_filters_keyTooLong() throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters =
                "{\"product\":[\"1234\",\"2345\"], \"" + LONG_FILTER_STRING + "\":[\"id\"]}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"filters\":" + filters + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_filters_tooManyValues() throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder filters =
                new StringBuilder(
                        "{"
                                + "\"filter-string-1\": [\"filter-value-1\"],"
                                + "\"filter-string-2\": [");
        filters.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
                        .mapToObj(i -> "\"filter-value-" + i + "\"")
                        .collect(Collectors.joining(",")));
        filters.append("]}");
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"filters\":" + filters + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_filters_valueTooLong() throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters =
                "{\"product\":[\"1234\",\"" + LONG_FILTER_STRING + "\"], \"ctid\":[\"id\"]}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"filters\":" + filters + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchTrigger_positiveLookbackWindow_acceptsTrigger() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters = "{\"product\":[\"1234\"], \"ctid\":[\"id\"], \"_lookback_window\": 123}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"filters\":" + filters + "}")));
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchTrigger_invalidLookbackWindow_rejectsTrigger() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters = "{\"product\":[\"1234\"], \"ctid\":[\"id\"], \"_lookback_window\": 123f}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"filters\":" + filters + "}")));
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchTrigger_zeroLookbackWindow_rejectsTrigger() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters = "{\"product\":[\"1234\"], \"ctid\":[\"id\"], \"_lookback_window\": 0}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"filters\":" + filters + "}")));
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchTrigger_negativeLookbackWindow_rejectsTrigger() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters = "{\"product\":[\"1234\"], \"ctid\":[\"id\"], \"_lookback_window\": -123}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"filters\":" + filters + "}")));
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_wrapsNotFilters() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String notFilters =
                "{\"product\":[\"1234\",\"2345\"],\"cid\":[\"id\"]}";
        String wrappedNotFilters =
                "[{\"product\":[\"1234\",\"2345\"],\"cid\":[\"id\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"not_filters\":" + notFilters + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(wrappedNotFilters, result.getNotFilters());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_tooManyNotFilterMaps() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder notFilters = new StringBuilder("[");
        for (int i = 0; i < Flags.DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET + 1; i++) {
            notFilters.append("{\"key-" + i + "\":[\"val1" + i + "\", \"val2" + i + "\"]}");
        }
        notFilters.append("]");
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"not_filters\":" + notFilters + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_notFilterMapIncludesReservedPrefix_fails() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters = "[{\"_something_reserved\":[\"val1\", \"val2\"]}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"not_filters\":" + filters + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_notFilters_invalidJson() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String notFilters =
                "{\"product\":[\"1234\",\"2345\"], \"\"\":[\"id\"]}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"not_filters\":" + notFilters + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_notFilters_tooManyFilters() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder notFilters = new StringBuilder("{");
        notFilters.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
                        .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                        .collect(Collectors.joining(",")));
        notFilters.append("}");
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"not_filters\":" + notFilters + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_notFilters_keyTooLong() throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String notFilters =
                "{\"product\":[\"1234\",\"2345\"], \"" + LONG_FILTER_STRING + "\":[\"id\"]}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"not_filters\":" + notFilters + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_notFilters_tooManyValues() throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder notFilters =
                new StringBuilder(
                        "{"
                                + "\"filter-string-1\": [\"filter-value-1\"],"
                                + "\"filter-string-2\": [");
        notFilters.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
                        .mapToObj(i -> "\"filter-value-" + i + "\"")
                        .collect(Collectors.joining(",")));
        notFilters.append("]}");
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"not_filters\":" + notFilters + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_notFilters_valueTooLong() throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String notFilters =
                "{\"product\":[\"1234\",\"" + LONG_FILTER_STRING + "\"], \"ctid\":[\"id\"]}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"not_filters\":" + notFilters + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    @SpyStatic(ErrorLogUtil.class)
    public void testBasicTriggerRequest_skipTriggerWhenNotEnrolled_processRedirects()
            throws IOException {
        doNothingOnErrorLogUtilError();
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        ExtendedMockito.doReturn(Optional.empty())
                .when(
                        () ->
                                Enrollment.getValidEnrollmentId(
                                        any(), anyString(), any(), any(), any()));
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                AsyncRedirects.REDIRECT_LIST_HEADER_KEY,
                                List.of(DEFAULT_REDIRECT),
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        verify(mFetcher, times(1)).openUrl(any());
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(1, asyncRedirects.getRedirects().size());
        assertEquals(DEFAULT_REDIRECT, asyncRedirects.getRedirects().get(0).getUri().toString());
        assertEquals(
                AsyncFetchStatus.EntityStatus.INVALID_ENROLLMENT,
                asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verifyErrorLogUtilError(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_INVALID,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
    }

    @Test
    public void testBasicTriggerRequestWithDebugKey() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{"
                                + "\"event_trigger_data\": "
                                + EVENT_TRIGGERS_1
                                + ", \"debug_key\": \""
                                + DEBUG_KEY
                                + "\""
                                + "}"));
        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(
                asyncRegistration.getTopOrigin().toString(),
                result.getAttributionDestination().toString());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(DEBUG_KEY, result.getDebugKey());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequest_debugKeyNegative_setToNull() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{\"event_trigger_data\":"
                                + EVENT_TRIGGERS_1
                                + ",\"debug_key\":\"-376\"}"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertNull(result.getDebugKey());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequest_debugKeyNotAString_setToNull() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{\"event_trigger_data\":"
                                + EVENT_TRIGGERS_1
                                + ",\"debug_key\":376}"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertNull(result.getDebugKey());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequest_debugKey_tooLarge() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{\"event_trigger_data\":"
                                + EVENT_TRIGGERS_1
                                + ",\"debug_key\":\"18446744073709551616\"}"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertNull(result.getDebugKey());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequest_debugKey_notAnInt() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{\"event_trigger_data\":"
                                + EVENT_TRIGGERS_1
                                + ",\"debug_key\":\"65g43\"}"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertNull(result.getDebugKey());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequest_debugKey_uses64thBit() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{\"event_trigger_data\":"
                                + EVENT_TRIGGERS_1
                                + ",\"debug_key\":\"18446744073709551615\"}"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertEquals(new UnsignedLong(-1L), result.getDebugKey());
    }

    @Test
    public void registerTrigger_nonHttpsUrl_rejectsTrigger() throws Exception {
        RegistrationRequest request =
                RegistrationRequestFixture.getInvalidRegistrationRequest(
                        RegistrationRequest.REGISTER_TRIGGER,
                        WebUtil.validUri("http://foo.test"),
                        sContext.getPackageName(),
                        SDK_PACKAGE_NAME);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.ResponseStatus.INVALID_URL, asyncFetchStatus.getResponseStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void testBadTriggerUrl() throws Exception {
        AsyncRegistration registration =
                createAsyncRegistration(
                        UUID.randomUUID().toString(),
                        WebUtil.validUri("bad-schema://foo.test"),
                        null,
                        null,
                        Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
                        null,
                        Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
                        AsyncRegistration.RegistrationType.APP_SOURCE,
                        null,
                        System.currentTimeMillis(),
                        0,
                        false,
                        false,
                        null);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(registration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.ResponseStatus.INVALID_URL, asyncFetchStatus.getResponseStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void testBadTriggerConnection() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doThrow(new IOException("Bad internet things"))
                .when(mFetcher)
                .openUrl(new URL(TRIGGER_URI));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.ResponseStatus.NETWORK_ERROR,
                asyncFetchStatus.getResponseStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, never()).setRequestMethod("POST");
    }

    @Test
    public void testBadRequestReturnFailure() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(400);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE,
                asyncFetchStatus.getResponseStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchTrigger_eventTriggerDataNoFields_triggerDataEqualsZero() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\": [{}]}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(
                asyncRegistration.getTopOrigin().toString(),
                result.getAttributionDestination().toString());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertEquals("[{\"trigger_data\":\"0\"}]", result.getEventTriggers());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testFirst200Next500_ignoreFailureReturnSuccess() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200).thenReturn(500);
        Map<String, List<String>> headersFirstRequest = new HashMap<>();
        headersFirstRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}"));
        headersFirstRequest.put(AsyncRedirects.REDIRECT_LIST_HEADER_KEY, List.of(DEFAULT_REDIRECT));
        when(mUrlConnection.getHeaderFields()).thenReturn(headersFirstRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(
                asyncRegistration.getTopOrigin().toString(),
                result.getAttributionDestination().toString());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testMissingHeaderButWithRedirect() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(AsyncRedirects.REDIRECT_LIST_HEADER_KEY, List.of(DEFAULT_REDIRECT)))
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(1, asyncRedirects.getRedirects().size());
        assertEquals(DEFAULT_REDIRECT, asyncRedirects.getRedirects().get(0).getUri().toString());

        assertEquals(
                AsyncFetchStatus.EntityStatus.HEADER_MISSING, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());

        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequestWithAggregateTriggerData() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregateTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\":"
                        + "[{\"conversion_subdomain\":[\"electronics.megastore\"]}],"
                        + "\"not_filters\":[{\"product\":[\"1\"]}]},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"],"
                        + "\"x_network_data\":{\"key_offset\":\"20\"}}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregateTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(
                asyncRegistration.getTopOrigin().toString(),
                result.getAttributionDestination().toString());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(aggregateTriggerData, result.getAggregateTriggerData());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void basicTriggerRequest_withInvalidOffsetInAggregateTriggerData_throwsParsingError()
            throws IOException {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregateTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\":"
                        + "[{\"conversion_subdomain\":[\"electronics.megastore\"]}],"
                        + "\"not_filters\":[{\"product\":[\"1\"]}]},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"],"
                        + "\"x_network_data\":{\"key_offset\":\"INVALID_VALUE\"}}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregateTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.PARSING_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void basicTriggerRequest_withNoOffsetInAggregateTriggerData_consideredValid()
            throws IOException {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregateTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\":"
                        + "[{\"conversion_subdomain\":[\"electronics.megastore\"]}],"
                        + "\"not_filters\":[{\"product\":[\"1\"]}]},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"],"
                        + "\"x_network_data\":{}}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregateTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(aggregateTriggerData, result.getAggregateTriggerData());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequestWithAggregateTriggerData_wrapsFilters() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregateTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\":"
                        + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        String aggregateTriggerDataWithWrappedFilters =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\":"
                        + "[{\"conversion_subdomain\":[\"electronics.megastore\"]}],"
                        + "\"not_filters\":[{\"product\":[\"1\"]}]},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregateTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(
                asyncRegistration.getTopOrigin().toString(),
                result.getAttributionDestination().toString());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(aggregateTriggerDataWithWrappedFilters, result.getAggregateTriggerData());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequestWithAggregateTriggerData_tooManyFilterMaps()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder filters = new StringBuilder("[");
        int i;
        for (i = 0; i < Flags.DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET; i++) {
            filters.append("{\"key-" + i + "\":[\"val1" + i + "\", \"val2" + i + "\"]},");
        }
        filters.append("{\"key-" + i + "\":[\"val1" + i + "\", \"val2" + i + "\"]}");
        filters.append("]");
        String aggregateTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\":" + filters + "},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{\"aggregatable_trigger_data\":"
                                                + aggregateTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void basicTriggerRequestWithAggregateTriggerData_filterMapIncludesReservedPrefix_fails()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters = "[{\"key-1\":[\"val1\", \"val2\"]},"
                + "{\"_reserved_prefix\":[\"val1\", \"val2\"]}]";
        String aggregateTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\":" + filters + "},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{\"aggregatable_trigger_data\":"
                                                + aggregateTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testBasicTriggerRequestWithAggregateTriggerData_tooManyNotFilterMaps()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder notFilters = new StringBuilder("[");
        int i;
        for (i = 0; i < Flags.DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET; i++) {
            notFilters.append("{\"key-" + i + "\":[\"val1" + i + "\", \"val2" + i + "\"]},");
        }
        notFilters.append("{\"key-" + i + "\":[\"val1" + i + "\", \"val2" + i + "\"]}");
        notFilters.append("]");
        String aggregateTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"not_filters\":" + notFilters + "},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{\"aggregatable_trigger_data\":"
                                                + aggregateTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void
            basicTriggerRequestWithAggregateTriggerData_notFilterMapIncludesReservedPrefix_fails()
                    throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String notFilters = "[{\"key-1\":[\"val1\", \"val2\"]},"
                + "{\"_reserved_prefix\":[\"val1\", \"val2\"]}]";
        String aggregateTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"not_filters\":" + notFilters + "},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{\"aggregatable_trigger_data\":"
                                                + aggregateTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testBasicTriggerRequestWithAggregateValues() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregatableValues = "{\"campaignCounts\":32768,\"geoValue\":1644}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_values\": "
                                                + aggregatableValues
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(new JSONObject(aggregatableValues).toString(), result.getAggregateValues());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequestWithAggregateTriggerData_rejectsTooManyValueKeys()
            throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        StringBuilder tooManyKeys = new StringBuilder("{");
        int i = 0;
        for (; i < 50; i++) {
            tooManyKeys.append(String.format("\"key-%s\": 12345,", i));
        }
        tooManyKeys.append(String.format("\"key-%s\": 12345}", i));
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"aggregatable_values\": " + tooManyKeys + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchTrigger_withReportingFilters_success() throws IOException, JSONException {
        // Setup
        String filters =
                "[{\n"
                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                        + "}]";
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"filters\": " + filters + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertEquals(new JSONArray(filters).toString(), result.getFilters());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebTriggers_basic_success() throws IOException, JSONException {
        // Setup
        WebTriggerRegistrationRequest request =
                buildWebTriggerRegistrationRequest(
                        Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
        doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection1.getResponseCode()).thenReturn(200);
        when(mFlags.getWebContextClientAppAllowList()).thenReturn("");
        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{"
                                + "\"event_trigger_data\": "
                                + EVENT_TRIGGERS_1
                                + ", \"debug_key\": \""
                                + DEBUG_KEY
                                + "\""
                                + "}"));
        when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        webTriggerRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        verify(mUrlConnection1).setRequestMethod("POST");
    }

    @Test
    public void fetchWebTriggers_withExtendedHeaders_success() throws IOException, JSONException {
        // Setup
        WebTriggerRegistrationRequest request =
                buildWebTriggerRegistrationRequest(
                        Collections.singletonList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mFlags.getWebContextClientAppAllowList()).thenReturn("");
        String aggregatableValues = "{\"campaignCounts\":32768,\"geoValue\":1644}";
        String filters =
                "[{\n"
                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                        + "}]";
        String aggregatableTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\":"
                        + "[{\"conversion_subdomain\":[\"electronics.megastore\"]}],"
                        + "\"not_filters\":[{\"product\":[\"1\"]}]},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"event_trigger_data\": "
                                                + EVENT_TRIGGERS_1
                                                + ", \"filters\": "
                                                + filters
                                                + ", \"aggregatable_values\": "
                                                + aggregatableValues
                                                + ", \"aggregatable_trigger_data\": "
                                                + aggregatableTriggerData
                                                + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        webTriggerRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(new JSONArray(aggregatableTriggerData).toString(),
                result.getAggregateTriggerData());
        assertEquals(new JSONObject(aggregatableValues).toString(), result.getAggregateValues());
        assertEquals(new JSONArray(filters).toString(), result.getFilters());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebTriggers_withRedirects_ignoresRedirects()
            throws IOException, JSONException {
        // Setup
        WebTriggerRegistrationRequest request =
                buildWebTriggerRegistrationRequest(
                        Collections.singletonList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mFlags.getWebContextClientAppAllowList()).thenReturn("");
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\": " + EVENT_TRIGGERS_1 + "}"),
                                AsyncRedirects.REDIRECT_LIST_HEADER_KEY,
                                List.of(LIST_TYPE_REDIRECT_URI),
                                AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY,
                                List.of(LOCATION_TYPE_REDIRECT_URI)));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        webTriggerRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(0, asyncRedirects.getRedirects().size());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
        verify(mUrlConnection).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_invalidJson() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregatableTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":\"campaignCounts\"],"
                        + "\"filters\":"
                        + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregatableTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_invalidKeyPiece_missingPrefix()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregatableTriggerData =
                "[{\"key_piece\":\"0400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\":"
                        + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregatableTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_invalidKeyPiece_tooLong()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregatableTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\":"
                        + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\""
                        + LONG_AGGREGATE_KEY_PIECE
                        + "\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregatableTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_sourceKeysMissing_setsDefault()
            throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregatableTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\":"
                        + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\"}]";
        // (Wraps filters)
        String expectedResult =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\":"
                        + "[{\"conversion_subdomain\":[\"electronics.megastore\"]}],"
                        + "\"not_filters\":[{\"product\":[\"1\"]}]},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregatableTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(expectedResult, result.getAggregateTriggerData());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_sourceKeysMissing_fails()
            throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregatableTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\":"
                        + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregatableTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_sourceKeysNotAnArray_fails()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregatableTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":{\"campaignCounts\": true},"
                        + "\"filters\":"
                        + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregatableTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_sourceKeys_tooManyKeys()
            throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder tooManyKeys = new StringBuilder("[");
        tooManyKeys.append(
                IntStream.range(
                                0,
                                mFlags.getMeasurementMaxAggregateKeysPerTriggerRegistration() + 1)
                        .mapToObj(i -> "aggregate-key-" + i)
                        .collect(Collectors.joining(",")));
        tooManyKeys.append("]");
        String aggregatableTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\": "
                        + tooManyKeys
                        + ","
                        + "\"filters\":"
                        + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregatableTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_sourceKeys_keyIdNotAString_fails()
            throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregatableTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\",35],"
                        + "\"filters\":"
                        + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregatableTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_sourceKeys_keyIdNotAString_succeeds()
            throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregatableTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\",35],"
                        + "\"filters\":"
                        + "[{\"conversion_subdomain\":[\"electronics.megastore\"]}],"
                        + "\"not_filters\":[{\"product\":[\"1\"]}]},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregatableTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(aggregatableTriggerData, result.getAggregateTriggerData());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_sourceKeys_invalidKeyId()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregatableTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\", \""
                        + LONG_AGGREGATE_KEY_ID
                        + "\"],"
                        + "\"filters\":"
                        + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregatableTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_filters_tooManyFilters()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder filters = new StringBuilder("{");
        filters.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
                        .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                        .collect(Collectors.joining(",")));
        filters.append("}");
        String aggregatableTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\": "
                        + filters
                        + ","
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregatableTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_filters_keyTooLong() throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters =
                "{\"product\":[\"1234\",\"2345\"], \"" + LONG_FILTER_STRING + "\":[\"id\"]}";
        String aggregatableTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\": "
                        + filters
                        + ","
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregatableTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_filters_tooManyValues()
            throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder filters =
                new StringBuilder(
                        "{"
                                + "\"filter-string-1\": [\"filter-value-1\"],"
                                + "\"filter-string-2\": [");
        filters.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
                        .mapToObj(i -> "\"filter-value-" + i + "\"")
                        .collect(Collectors.joining(",")));
        filters.append("]}");
        String aggregatableTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\": "
                        + filters
                        + ","
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregatableTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_filters_valueTooLong() throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters =
                "{\"product\":[\"1234\",\"" + LONG_FILTER_STRING + "\"], \"ctid\":[\"id\"]}";
        String aggregatableTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\": "
                        + filters
                        + ","
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregatableTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_notFilters_tooManyFilters()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder filters = new StringBuilder("{");
        filters.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
                        .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                        .collect(Collectors.joining(",")));
        filters.append("}");
        String aggregatableTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"not_filters\": "
                        + filters
                        + ","
                        + "\"filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregatableTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_notFilters_keyTooLong()
            throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters =
                "{\"product\":[\"1234\",\"2345\"], \"" + LONG_FILTER_STRING + "\":[\"id\"]}";
        String aggregatableTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"not_filters\": "
                        + filters
                        + ","
                        + "\"filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregatableTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_notFilters_tooManyValues()
            throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder filters =
                new StringBuilder(
                        "{"
                                + "\"filter-string-1\": [\"filter-value-1\"],"
                                + "\"filter-string-2\": [");
        filters.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
                        .mapToObj(i -> "\"filter-value-" + i + "\"")
                        .collect(Collectors.joining(",")));
        filters.append("]}");
        String aggregatableTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"not_filters\": "
                        + filters
                        + ","
                        + "\"filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregatableTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_notFilters_valueTooLong()
            throws Exception {
        Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters =
                "{\"product\":[\"1234\",\"" + LONG_FILTER_STRING + "\"], \"ctid\":[\"id\"]}";
        String aggregatableTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"not_filters\": "
                        + filters
                        + ","
                        + "\"filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_trigger_data\": "
                                                + aggregatableTriggerData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testBasicTriggerRequestWithDebugReportingHeader() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{\"event_trigger_data\":"
                                + EVENT_TRIGGERS_1
                                + ",\"debug_reporting\":\"true\"}"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertTrue(result.isDebugReporting());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequestWithInvalidDebugReportingHeader() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{\"event_trigger_data\":"
                                + EVENT_TRIGGERS_1
                                + ",\"debug_reporting\":\"invalid\"}"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertFalse(result.isDebugReporting());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequestWithNullDebugReportingHeader() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{\"event_trigger_data\":"
                                + EVENT_TRIGGERS_1
                                + ",\"debug_reporting\":\"null\"}"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertFalse(result.isDebugReporting());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequestWithNoQuotesDebugReportingHeader() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{\"event_trigger_data\":"
                                + EVENT_TRIGGERS_1
                                + ",\"debug_reporting\":invalid}"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertFalse(result.isDebugReporting());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequestWithEmptyDebugReportingHeader() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertFalse(result.isDebugReporting());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequestWithAggregatableValues() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregatableValues = "{\"campaignCounts\":32768,\"geoValue\":1644}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_values\": "
                                                + aggregatableValues
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONObject(aggregatableValues).toString(), result.getAggregateValues());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void triggerRequest_aggregatableValueLessThanOne_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregatableValues = "{\"campaignCounts\":32768,\"geoValue\":0}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_values\": "
                                                + aggregatableValues
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void triggerRequest_aggregatableValueLessThanOne_succeeds() throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregatableValues = "{\"campaignCounts\":32768,\"geoValue\":0}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_values\": "
                                                + aggregatableValues
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONObject(aggregatableValues).toString(), result.getAggregateValues());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void triggerRequest_aggregatableValueNotAnInt_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregatableValues = "{\"campaignCounts\":32768,\"geoValue\":\"1a2\"}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_values\": "
                                                + aggregatableValues
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void triggerRequest_aggregatableValueNotAnInt_succeeds() throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregatableValues = "{\"campaignCounts\":32768,\"geoValue\":\"1a2\"}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_values\": "
                                                + aggregatableValues
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONObject(aggregatableValues).toString(), result.getAggregateValues());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void triggerRequest_aggregatableValueLongValue_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        Long longValue = ((long) Integer.MAX_VALUE) + 1L;
        String aggregatableValues = "{\"campaignCounts\":32768,\"geoValue\":" + longValue.toString()
                + "}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_values\": "
                                                + aggregatableValues
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void triggerRequest_aggregatableValueLongValue_succeeds() throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        Long longValue = ((long) Integer.MAX_VALUE) + 1L;
        String aggregatableValues = "{\"campaignCounts\":32768,\"geoValue\":" + longValue.toString()
                + "}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_values\": "
                                                + aggregatableValues
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONObject(aggregatableValues).toString(), result.getAggregateValues());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void triggerRequest_aggregatableValueTooLarge_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        Integer tooLarge = MEASUREMENT_MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE + 1;
        String aggregatableValues = "{\"campaignCounts\":32768,\"geoValue\":" + tooLarge.toString()
                + "}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_values\": "
                                                + aggregatableValues
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void triggerRequest_aggregatableValueTooLarge_succeeds() throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        Integer tooLarge = MEASUREMENT_MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE + 1;
        String aggregatableValues = "{\"campaignCounts\":32768,\"geoValue\":" + tooLarge.toString()
                + "}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_values\": "
                                                + aggregatableValues
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONObject(aggregatableValues).toString(), result.getAggregateValues());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testTriggerRequestWithAggregatableValues_invalidJson() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregatableValues = "{\"campaignCounts\":32768\"geoValue\":1644}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_values\": "
                                                + aggregatableValues
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregatableValues_tooManyKeys() throws Exception {
        StringBuilder tooManyKeys = new StringBuilder("{");
        tooManyKeys.append(
                IntStream.range(
                                0,
                                mFlags.getMeasurementMaxAggregateKeysPerTriggerRegistration() + 1)
                        .mapToObj(i -> String.format("\"key-%s\": 12345,", i))
                        .collect(Collectors.joining(",")));
        tooManyKeys.append("}");
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"aggregatable_values\": " + tooManyKeys + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregatableValues_invalidKeyId() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregatableValues =
                "{\"campaignCounts\":32768, \"" + LONG_AGGREGATE_KEY_ID + "\":1644}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"aggregatable_values\": "
                                                + aggregatableValues
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void basicTriggerRequest_headersMoreThanMaxResponseSize_emitsMetricsWithAdTechDomain()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        MeasurementRegistrationResponseStats expectedStats =
                new MeasurementRegistrationResponseStats.Builder(
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER,
                                223,
                                UNKNOWN_SOURCE_TYPE,
                                APP_REGISTRATION_SURFACE_TYPE,
                                SUCCESS_STATUS,
                                UNKNOWN_REGISTRATION_FAILURE_TYPE,
                                0,
                                "",
                                0,
                                false)
                        .setAdTechDomain(WebUtil.validUrl("https://foo.test"))
                        .build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        asyncFetchStatus.setRegistrationDelay(0L);
        asyncFetchStatus.setRetryCount(0);
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
        FetcherUtil.emitHeaderMetrics(5L, mLogger, asyncRegistration, asyncFetchStatus);
        verify(mLogger).logMeasurementRegistrationsResponseSize(eq(expectedStats));
    }

    @Test
    public void basicTriggerRequest_headersMoreThanMaxResponseSize_throwException()
            throws Exception {
        // This test is only valid when this flag is enabled.
        Assume.assumeTrue(mEnableUpdateTriggerHeaderSizeLimit);
        when(mFlags.getMaxTriggerRegistrationHeaderSizeBytes()).thenReturn(0L);
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        asyncFetchStatus.setRegistrationDelay(0L);
        asyncFetchStatus.setRetryCount(0);
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        assertEquals(
                AsyncFetchStatus.ResponseStatus.HEADER_SIZE_LIMIT_EXCEEDED,
                asyncFetchStatus.getResponseStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void triggerRequest_appRegWithValidAttributionConfig_parsesCorrectly() throws Exception {
        // Setup
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        String originalAttributionConfigString =
                "[{\n"
                        + "\"source_network\": \"AdTech1-Ads\",\n"
                        + "\"source_priority_range\": {\n"
                        + "\"start\": 100,\n"
                        + "\"end\": 1000\n"
                        + "},\n"
                        + "\"source_filters\": {\n"
                        + "\"campaign_type\": [\"install\"]"
                        + "},\n"
                        + "\"source_not_filters\": {\n"
                        + "\"product\": [\"prod1\"]"
                        + "},\n"
                        + "\"priority\": \"99\",\n"
                        + "\"expiry\": \"604800\",\n"
                        + "\"source_expiry_override\": \"1209600\",\n"
                        + "\"post_install_exclusivity_window\": \"5000\",\n"
                        + "\"filter_data\": {\n"
                        + "\"campaign_type\": [\"install\"]\n"
                        + "}\n"
                        + "},"
                        + "{\n"
                        + "\"source_network\": \"AdTech2-Ads\"}"
                        + "]";
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ", \"attribution_config\":"
                                                + originalAttributionConfigString
                                                + "}")));
        doReturn(5000L).when(mFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();
        List<FilterMap> sourceFilters =
                Collections.singletonList(
                        new FilterMap.Builder()
                                .setAttributionFilterMap(
                                        Map.of(
                                                "campaign_type",
                                                Collections.singletonList("install")))
                                .build());
        List<FilterMap> sourceNotFilters =
                Collections.singletonList(
                        new FilterMap.Builder()
                                .setAttributionFilterMap(
                                        Map.of("product", Collections.singletonList("prod1")))
                                .build());
        FilterMap filterData =
                new FilterMap.Builder()
                        .setAttributionFilterMap(
                                Map.of("campaign_type", Collections.singletonList("install")))
                        .build();
        AttributionConfig attributionConfig1 =
                new AttributionConfig.Builder()
                        .setSourceAdtech("AdTech1-Ads")
                        .setSourcePriorityRange(new Pair<>(100L, 1000L))
                        .setSourceFilters(sourceFilters)
                        .setSourceNotFilters(sourceNotFilters)
                        .setPriority(99L)
                        .setExpiry(604800L)
                        .setSourceExpiryOverride(1209600L)
                        .setPostInstallExclusivityWindow(5000L)
                        .setFilterData(filterData)
                        .build();
        AttributionConfig attributionConfig2 =
                new AttributionConfig.Builder().setSourceAdtech("AdTech2-Ads").build();

        JSONArray expectedAttributionConfigJsonArray =
                new JSONArray(
                        Arrays.asList(
                                attributionConfig1.serializeAsJson(mFlags),
                                attributionConfig2.serializeAsJson(mFlags)));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

        when(mFlags.getMeasurementEnableXNA()).thenReturn(true);

        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertEquals(expectedAttributionConfigJsonArray.toString(), result.getAttributionConfig());
    }

    @Test
    public void triggerRequest_allowListedWebRegWithValidAttributionConfig_parsesCorrectly()
            throws Exception {
        // Setup
        WebTriggerRegistrationRequest request =
                buildWebTriggerRegistrationRequest(List.of(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mFlags.getWebContextClientAppAllowList())
                .thenReturn(sContext.getPackageName() + ",some_other_package");
        String originalAttributionConfigString =
                "[{\n"
                        + "\"source_network\": \"AdTech1-Ads\",\n"
                        + "\"source_priority_range\": {\n"
                        + "\"start\": 100,\n"
                        + "\"end\": 1000\n"
                        + "},\n"
                        + "\"source_filters\": {\n"
                        + "\"campaign_type\": [\"install\"]"
                        + "},\n"
                        + "\"source_not_filters\": {\n"
                        + "\"product\": [\"prod1\"]"
                        + "},\n"
                        + "\"priority\": \"99\",\n"
                        + "\"expiry\": \"604800\",\n"
                        + "\"source_expiry_override\": \"1209600\",\n"
                        + "\"post_install_exclusivity_window\": \"5000\",\n"
                        + "\"filter_data\": {\n"
                        + "\"campaign_type\": [\"install\"]\n"
                        + "}\n"
                        + "},"
                        + "{\n"
                        + "\"source_network\": \"AdTech2-Ads\"}"
                        + "]";
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ", \"attribution_config\":"
                                                + originalAttributionConfigString
                                                + "}")));
        doReturn(5000L).when(mFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();
        List<FilterMap> sourceFilters =
                Collections.singletonList(
                        new FilterMap.Builder()
                                .setAttributionFilterMap(
                                        Map.of(
                                                "campaign_type",
                                                Collections.singletonList("install")))
                                .build());
        List<FilterMap> sourceNotFilters =
                Collections.singletonList(
                        new FilterMap.Builder()
                                .setAttributionFilterMap(
                                        Map.of("product", Collections.singletonList("prod1")))
                                .build());
        FilterMap filterData =
                new FilterMap.Builder()
                        .setAttributionFilterMap(
                                Map.of("campaign_type", Collections.singletonList("install")))
                        .build();
        AttributionConfig attributionConfig1 =
                new AttributionConfig.Builder()
                        .setSourceAdtech("AdTech1-Ads")
                        .setSourcePriorityRange(new Pair<>(100L, 1000L))
                        .setSourceFilters(sourceFilters)
                        .setSourceNotFilters(sourceNotFilters)
                        .setPriority(99L)
                        .setExpiry(604800L)
                        .setSourceExpiryOverride(1209600L)
                        .setPostInstallExclusivityWindow(5000L)
                        .setFilterData(filterData)
                        .build();
        AttributionConfig attributionConfig2 =
                new AttributionConfig.Builder().setSourceAdtech("AdTech2-Ads").build();

        JSONArray expectedAttributionConfigJsonArray =
                new JSONArray(
                        Arrays.asList(
                                attributionConfig1.serializeAsJson(mFlags),
                                attributionConfig2.serializeAsJson(mFlags)));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = webTriggerRegistrationRequest(request, true);

        when(mFlags.getMeasurementEnableXNA()).thenReturn(true);

        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertEquals(expectedAttributionConfigJsonArray.toString(), result.getAttributionConfig());
    }

    @Test
    public void triggerRequest_disallowListedWebRegWithValidAttributionConfig_doesntParse()
            throws Exception {
        // Setup
        WebTriggerRegistrationRequest request =
                buildWebTriggerRegistrationRequest(List.of(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        // Doesn't have the app package
        when(mFlags.getWebContextClientAppAllowList()).thenReturn("some_other_package");
        String originalAttributionConfigString =
                "[{\n"
                        + "\"source_network\": \"AdTech1-Ads\",\n"
                        + "\"source_priority_range\": {\n"
                        + "\"start\": 100,\n"
                        + "\"end\": 1000\n"
                        + "},\n"
                        + "\"source_filters\": {\n"
                        + "\"campaign_type\": [\"install\"]"
                        + "},\n"
                        + "\"source_not_filters\": {\n"
                        + "\"product\": [\"prod1\"]"
                        + "},\n"
                        + "\"priority\": \"99\",\n"
                        + "\"expiry\": \"604800\",\n"
                        + "\"source_expiry_override\": \"680\",\n"
                        + "\"post_install_exclusivity_window\": \"5000\",\n"
                        + "\"filter_data\": {\n"
                        + "\"campaign_type\": [\"install\"]\n"
                        + "}\n"
                        + "},"
                        + "{\n"
                        + "\"source_network\": \"AdTech2-Ads\"}"
                        + "]";
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ", \"attribution_config\":"
                                                + originalAttributionConfigString
                                                + "}")));
        doReturn(5000L).when(mFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = webTriggerRegistrationRequest(request, true);

        when(mFlags.getMeasurementEnableXNA()).thenReturn(true);

        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertNull(result.getAttributionConfig());
    }

    @Test
    public void triggerRequest_attributionConfigThrowingJsonException_dropsTheTriggerCompletely()
            throws Exception {
        // Setup
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ", \"attribution_config\": INVALID_JSON_ARRAY"
                                                + "}")));
        doReturn(5000L).when(mFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();

        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

        when(mFlags.getMeasurementEnableXNA()).thenReturn(true);

        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, new AsyncRedirects());

        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.PARSING_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void triggerRequest_withValidAdtechBitMapping_storesCorrectly() throws Exception {
        // Setup
        String validAdTechBitMapping =
                "{\"AdTechA-Ads\":\"0x1\",\"AdtechB-Ads\":\"0x2\"}";
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ", \"x_network_key_mapping\":"
                                                + validAdTechBitMapping
                                                + "}")));
        doReturn(5000L).when(mFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

        when(mFlags.getMeasurementEnableXNA()).thenReturn(true);

        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertEquals(validAdTechBitMapping, result.getAdtechKeyMapping());
    }

    @Test
    public void triggerRequest_withInvalidAdtechBitMapping_dropsBitMapping() throws Exception {
        // Setup
        String invalidAdTechBitMapping =
                "{"
                        // Values don't start with 0x -- invalid
                        + "\"AdTechA-Ads\": 1234,"
                        + "\"AdTechB-Ads\": 2"
                        + "}";
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ", \"x_network_key_mapping\":"
                                                + invalidAdTechBitMapping
                                                + "}")));
        doReturn(5000L).when(mFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

        when(mFlags.getMeasurementEnableXNA()).thenReturn(true);

        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertNull(result.getAdtechKeyMapping());
    }

    @Test
    public void triggerRequest_xnaDisabled_nullXNAFields() throws Exception {
        // Setup
        String validAdTechBitMapping =
                "{\"AdTechA-Ads\":\"0x1\",\"AdTechB-Ads\":\"0x2\"}";
        String validAttributionConfig =
                "[{\n"
                        + "\"source_network\": \"AdTech1-Ads\",\n"
                        + "\"source_priority_range\": {\n"
                        + "\"start\": 100,\n"
                        + "\"end\": 1000\n"
                        + "},\n"
                        + "\"source_filters\": {\n"
                        + "\"campaign_type\": [\"install\"]"
                        + "},\n"
                        + "\"source_not_filters\": {\n"
                        + "\"product\": [\"prod1\"]"
                        + "},\n"
                        + "\"priority\": \"99\",\n"
                        + "\"expiry\": \"604800\",\n"
                        + "\"source_expiry_override\": \"680\",\n"
                        + "\"post_install_exclusivity_window\": \"5000\",\n"
                        + "\"filter_data\": {\n"
                        + "\"campaign_type\": [\"install\"]\n"
                        + "}\n"
                        + "},"
                        + "{\n"
                        + "\"source_network\": \"AdTech2-Ads\"}"
                        + "]";
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "\"event_trigger_data\":"
                                                + EVENT_TRIGGERS_1
                                                + ", \"adtech_bit_mapping\":"
                                                + validAdTechBitMapping
                                                + ", \"attribution_config\":"
                                                + validAttributionConfig
                                                + "}")));
        doReturn(5000L).when(mFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

        when(mFlags.getMeasurementEnableXNA()).thenReturn(false);

        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertNull(result.getAdtechKeyMapping());
        assertNull(result.getAttributionConfig());
    }

    @Test
    public void fetchWebTriggers_withoutSourceRegistrationTimeConfig_defaultToExclude()
            throws IOException, JSONException {
        // Setup
        WebTriggerRegistrationRequest request =
                buildWebTriggerRegistrationRequest(
                        Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
        doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection1.getResponseCode()).thenReturn(200);
        when(mFlags.getWebContextClientAppAllowList()).thenReturn("");
        when(mFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled())
                .thenReturn(true);
        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"event_trigger_data\": " + EVENT_TRIGGERS_1 + "}"));
        when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        webTriggerRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(
                Trigger.SourceRegistrationTimeConfig.EXCLUDE,
                result.getAggregatableSourceRegistrationTimeConfig());
        verify(mUrlConnection1).setRequestMethod("POST");
    }

    @Test
    public void fetchWebTriggers_withSourceRegistrationTimeConfig_excludeValue_getsParsed()
            throws IOException, JSONException {
        // Setup
        WebTriggerRegistrationRequest request =
                buildWebTriggerRegistrationRequest(
                        Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
        doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection1.getResponseCode()).thenReturn(200);
        when(mFlags.getWebContextClientAppAllowList()).thenReturn("");
        when(mFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled())
                .thenReturn(true);
        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{"
                                + "\"event_trigger_data\": "
                                + EVENT_TRIGGERS_1
                                + ", \"aggregatable_source_registration_time\": \""
                                + Trigger.SourceRegistrationTimeConfig.EXCLUDE.name()
                                + "\""
                                + "}"));
        when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        webTriggerRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(
                Trigger.SourceRegistrationTimeConfig.EXCLUDE,
                result.getAggregatableSourceRegistrationTimeConfig());
        verify(mUrlConnection1).setRequestMethod("POST");
    }

    @Test
    public void fetchWebTriggers_withSourceRegistrationTimeConfig_includeValue_getsParsed()
            throws IOException, JSONException {
        // Setup
        WebTriggerRegistrationRequest request =
                buildWebTriggerRegistrationRequest(
                        Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
        doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection1.getResponseCode()).thenReturn(200);
        when(mFlags.getWebContextClientAppAllowList()).thenReturn("");
        when(mFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled())
                .thenReturn(true);
        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{"
                                + "\"event_trigger_data\": "
                                + EVENT_TRIGGERS_1
                                + ", \"aggregatable_source_registration_time\": \""
                                + Trigger.SourceRegistrationTimeConfig.INCLUDE.name()
                                + "\""
                                + "}"));
        when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        webTriggerRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(
                Trigger.SourceRegistrationTimeConfig.INCLUDE,
                result.getAggregatableSourceRegistrationTimeConfig());
        verify(mUrlConnection1).setRequestMethod("POST");
    }

    @Test
    public void fetchWebTrigger_withInvalidSourceRegistrationTime_returnsEmpty()
            throws IOException {
        // Setup
        WebTriggerRegistrationRequest request =
                buildWebTriggerRegistrationRequest(
                        Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
        doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection1.getResponseCode()).thenReturn(200);
        when(mFlags.getWebContextClientAppAllowList()).thenReturn("");
        when(mFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled())
                .thenReturn(true);
        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{"
                                + "\"event_trigger_data\": "
                                + EVENT_TRIGGERS_1
                                + ", \"aggregatable_source_registration_time\": \""
                                + "INVALID"
                                + "\""
                                + "}"));
        when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        webTriggerRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        assertTrue(fetch.isEmpty());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    }

    @Test
    public void fetchWebTriggers_withDebugJoinKey_getsParsed() throws IOException, JSONException {
        // Setup
        WebTriggerRegistrationRequest request =
                buildWebTriggerRegistrationRequest(
                        Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
        doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection1.getResponseCode()).thenReturn(200);
        when(mFlags.getWebContextClientAppAllowList()).thenReturn("");
        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{"
                                + "\"event_trigger_data\": "
                                + EVENT_TRIGGERS_1
                                + ", \"debug_key\": \""
                                + DEBUG_KEY
                                + "\""
                                + ", \"debug_join_key\": \""
                                + DEBUG_JOIN_KEY
                                + "\""
                                + "}"));
        when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        webTriggerRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(DEBUG_JOIN_KEY, result.getDebugJoinKey());
        verify(mUrlConnection1).setRequestMethod("POST");
    }

    @Test
    public void fetchWebTriggers_withDebugJoinKeyEnrollmentNotAllowlisted_joinKeyDropped()
            throws IOException, JSONException {
        // Setup
        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist())
                .thenReturn("some_random_enrollment1,some_random_enrollment2");
        WebTriggerRegistrationRequest request =
                buildWebTriggerRegistrationRequest(
                        Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
        doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection1.getResponseCode()).thenReturn(200);
        when(mFlags.getWebContextClientAppAllowList()).thenReturn("");
        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{"
                                + "\"event_trigger_data\": "
                                + EVENT_TRIGGERS_1
                                + ", \"debug_key\": \""
                                + DEBUG_KEY
                                + "\""
                                + ", \"debug_join_key\": \""
                                + DEBUG_JOIN_KEY
                                + "\""
                                + "}"));
        when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        webTriggerRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
        assertNull(result.getDebugJoinKey());
        verify(mUrlConnection1).setRequestMethod("POST");
    }

    @Test
    public void fetchTrigger_basicWithDebugJoinKey_getsParsed() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{\"event_trigger_data\":"
                                + EVENT_TRIGGERS_1
                                + ",\"debug_key\":\""
                                + DEBUG_KEY
                                + "\" ,\"debug_join_key\":\""
                                + DEBUG_JOIN_KEY
                                + "\"}"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertEquals(DEBUG_KEY, result.getDebugKey());
        assertEquals(DEBUG_JOIN_KEY, result.getDebugJoinKey());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchTrigger_basicWithDebugJoinKeyEnrollmentNotInAllowlist_joinKeyDropped()
            throws Exception {
        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist()).thenReturn("");
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{\"event_trigger_data\":"
                                + EVENT_TRIGGERS_1
                                + ",\"debug_key\":\""
                                + DEBUG_KEY
                                + "\" ,\"debug_join_key\":\""
                                + DEBUG_JOIN_KEY
                                + "\"}"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(DEBUG_KEY, result.getDebugKey());
        assertNull(result.getDebugJoinKey());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchTrigger_setsRegistrationOriginWithoutPath_forRegistrationURIWithPath()
            throws Exception {
        String uri = WebUtil.validUrl("https://test1.example.test/path1");
        RegistrationRequest request = buildRequest(uri);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(uri));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{"
                                + "\"event_trigger_data\": "
                                + EVENT_TRIGGERS_1
                                + ", \"debug_key\": \""
                                + DEBUG_KEY
                                + "\""
                                + "}"));
        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(
                WebUtil.validUrl("https://test1.example.test"),
                result.getRegistrationOrigin().toString());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(DEBUG_KEY, result.getDebugKey());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchTrigger_setsRegistrationOriginWithPort_forRegistrationURIWithPort()
            throws Exception {
        String uri = WebUtil.validUrl("https://test1.example.test:8081");
        RegistrationRequest request = buildRequest(uri);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(uri));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{"
                                + "\"event_trigger_data\": "
                                + EVENT_TRIGGERS_1
                                + ", \"debug_key\": \""
                                + DEBUG_KEY
                                + "\""
                                + "}"));
        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(
                WebUtil.validUrl("https://test1.example.test:8081"),
                result.getRegistrationOrigin().toString());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(DEBUG_KEY, result.getDebugKey());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchTrigger_appRegistrationWithAdId_encodedAdIdAddedToTrigger() throws Exception {
        RegistrationRequest request = buildRequestWithAdId(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{\"event_trigger_data\":"
                                + EVENT_TRIGGERS_1
                                + ",\"debug_key\":\""
                                + DEBUG_KEY
                                + "\"}"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequestWithAdId(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(DEBUG_KEY, result.getDebugKey());
        verify(mUrlConnection).setRequestMethod("POST");
        assertEquals(PLATFORM_AD_ID_VALUE, result.getPlatformAdId());
    }

    @Test
    public void fetchWebTrigger_withDebugAdIdValue_getsParsed() throws IOException, JSONException {
        // Setup
        WebTriggerRegistrationRequest request =
                buildWebTriggerRegistrationRequest(
                        Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
        doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection1.getResponseCode()).thenReturn(200);
        when(mFlags.getWebContextClientAppAllowList()).thenReturn("");
        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{"
                                + "\"event_trigger_data\": "
                                + EVENT_TRIGGERS_1
                                + ", \"debug_key\": \""
                                + DEBUG_KEY
                                + "\""
                                + ", \"debug_ad_id\": \""
                                + DEBUG_AD_ID_VALUE
                                + "\""
                                + "}"));
        when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        webTriggerRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(DEBUG_AD_ID_VALUE, result.getDebugAdId());
        verify(mUrlConnection1).setRequestMethod("POST");
    }

    @Test
    public void fetchWebTrigger_withDebugAdIdValue_enrollmentBlockListed_doesNotGetParsed()
            throws IOException, JSONException {
        // Setup
        when(mFlags.getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist())
                .thenReturn(ENROLLMENT_ID);
        WebTriggerRegistrationRequest request =
                buildWebTriggerRegistrationRequest(
                        Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
        doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection1.getResponseCode()).thenReturn(200);
        when(mFlags.getWebContextClientAppAllowList()).thenReturn("");
        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{"
                                + "\"event_trigger_data\": "
                                + EVENT_TRIGGERS_1
                                + ", \"debug_key\": \""
                                + DEBUG_KEY
                                + "\""
                                + ", \"debug_ad_id\": \""
                                + DEBUG_AD_ID_VALUE
                                + "\""
                                + "}"));
        when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        webTriggerRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertNull(result.getDebugAdId());
        verify(mUrlConnection1).setRequestMethod("POST");
    }

    @Test
    public void fetchWebTrigger_withDebugAdIdValue_blockListMatchesAll_doesNotGetParsed()
            throws IOException, JSONException {
        // Setup
        when(mFlags.getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist()).thenReturn("*");
        WebTriggerRegistrationRequest request =
                buildWebTriggerRegistrationRequest(
                        Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
        doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection1.getResponseCode()).thenReturn(200);
        when(mFlags.getWebContextClientAppAllowList()).thenReturn("");
        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{"
                                + "\"event_trigger_data\": "
                                + EVENT_TRIGGERS_1
                                + ", \"debug_key\": \""
                                + DEBUG_KEY
                                + "\""
                                + ", \"debug_ad_id\": \""
                                + DEBUG_AD_ID_VALUE
                                + "\""
                                + "}"));
        when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        webTriggerRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertNull(result.getDebugAdId());
        verify(mUrlConnection1).setRequestMethod("POST");
    }

    @Test
    public void fetchTrigger_setsSiteEnrollmentId_whenDisableEnrollmentFlagIsTrue()
            throws Exception {
        String uri = WebUtil.validUrl("https://test1.example.test:8081");
        RegistrationRequest request = buildRequest(uri);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(uri));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).isDisableMeasurementEnrollmentCheck();
        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{"
                                + "\"event_trigger_data\": "
                                + EVENT_TRIGGERS_1
                                + ", \"debug_key\": \""
                                + DEBUG_KEY
                                + "\""
                                + "}"));
        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(
                WebUtil.validUrl("https://test1.example.test:8081"),
                result.getRegistrationOrigin().toString());
        assertEquals("https://example.com", result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(DEBUG_KEY, result.getDebugKey());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchTrigger_setsSiteEnrollmentId_whenDisableEnrollmentFlagIsTrueForLocalhost()
            throws Exception {
        String uri = WebUtil.validUrl("https://localhost:8081");
        RegistrationRequest request = buildRequest(uri);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(uri));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).isDisableMeasurementEnrollmentCheck();
        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{"
                                + "\"event_trigger_data\": "
                                + EVENT_TRIGGERS_1
                                + ", \"debug_key\": \""
                                + DEBUG_KEY
                                + "\""
                                + "}"));
        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(
                WebUtil.validUrl("https://localhost:8081"),
                result.getRegistrationOrigin().toString());
        assertEquals("https://localhost", result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(DEBUG_KEY, result.getDebugKey());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchTrigger_setsSiteEnrollmentId_whenDisableEnrollmentFlagIsTrueForIP()
            throws Exception {
        String uri = WebUtil.validUrl("https://127.0.0.1:8081");
        RegistrationRequest request = buildRequest(uri);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(uri));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).isDisableMeasurementEnrollmentCheck();
        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{"
                                + "\"event_trigger_data\": "
                                + EVENT_TRIGGERS_1
                                + ", \"debug_key\": \""
                                + DEBUG_KEY
                                + "\""
                                + "}"));
        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(
                WebUtil.validUrl("https://127.0.0.1:8081"),
                result.getRegistrationOrigin().toString());
        assertEquals("https://127.0.0.1", result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(DEBUG_KEY, result.getDebugKey());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchTrigger_aggregatorCoordinatorOriginAllowed_pass() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{\"event_trigger_data\":"
                                                + " [{}],"
                                                + "'aggregation_coordinator_origin': "
                                                + "'https://cloud.coordination.test'"
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        when(mFlags.getMeasurementAggregationCoordinatorOriginEnabled()).thenReturn(true);
        when(mFlags.getMeasurementAggregationCoordinatorOriginList())
                .thenReturn("https://cloud.coordination.test");
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(AsyncFetchStatus.EntityStatus.SUCCESS, asyncFetchStatus.getEntityStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(
                asyncRegistration.getTopOrigin().toString(),
                result.getAttributionDestination().toString());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertEquals("[{\"trigger_data\":\"0\"}]", result.getEventTriggers());
        assertEquals(
                Uri.parse("https://cloud.coordination.test"),
                result.getAggregationCoordinatorOrigin());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchTrigger_aggregatorCoordinatorOriginDisallowed_fails() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{\"event_trigger_data\":"
                                                + " [{}],"
                                                + "'aggregation_coordinator_origin': "
                                                + "'https://invalid.cloud.coordination.test'"
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        when(mFlags.getMeasurementAggregationCoordinatorOriginEnabled()).thenReturn(true);
        when(mFlags.getMeasurementAggregationCoordinatorOriginList())
                .thenReturn("https://cloud.coordination.test");
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchTrigger_nullCloudCoordinatorOrigin() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "\"event_trigger_data\": " + "[{}]" + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        when(mFlags.getMeasurementAggregationCoordinatorOriginEnabled()).thenReturn(true);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(AsyncFetchStatus.EntityStatus.SUCCESS, asyncFetchStatus.getEntityStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(
                asyncRegistration.getTopOrigin().toString(),
                result.getAttributionDestination().toString());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
        assertEquals("[{\"trigger_data\":\"0\"}]", result.getEventTriggers());
        assertNull(result.getAggregationCoordinatorOrigin());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchTrigger_emptyCloudCoordinatorOrigin_fails() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{\"event_trigger_data\":"
                                                + " [{}],"
                                                + "'aggregation_coordinator_origin': "
                                                + "'https://invalid.cloud.coordination.test'"
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        when(mFlags.getMeasurementAggregationCoordinatorOriginEnabled()).thenReturn(true);
        when(mFlags.getMeasurementAggregationCoordinatorOriginList())
                .thenReturn("https://cloud.coordination.test");
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    private RegistrationRequest buildRequest(String triggerUri) {
        return new RegistrationRequest.Builder(
                        RegistrationRequest.REGISTER_TRIGGER,
                        Uri.parse(triggerUri),
                        sContext.getPackageName(),
                        SDK_PACKAGE_NAME)
                .build();
    }

    private RegistrationRequest buildRequestWithAdId(String triggerUri) {
        return new RegistrationRequest.Builder(
                        RegistrationRequest.REGISTER_TRIGGER,
                        Uri.parse(triggerUri),
                        sContext.getPackageName(),
                        SDK_PACKAGE_NAME)
                .setAdIdPermissionGranted(true)
                .setAdIdValue(PLATFORM_AD_ID_VALUE)
                .build();
    }

    private WebTriggerRegistrationRequest buildWebTriggerRegistrationRequest(
            List<WebTriggerParams> triggerParams, String topOrigin) {
        return new WebTriggerRegistrationRequest.Builder(triggerParams, Uri.parse(topOrigin))
                .build();
    }

    private static AsyncRegistration appTriggerRegistrationRequest(
            RegistrationRequest registrationRequest) {
        return createAsyncRegistration(
                UUID.randomUUID().toString(),
                registrationRequest.getRegistrationUri(),
                null,
                null,
                Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
                null,
                Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
                registrationRequest.getRegistrationType() == RegistrationRequest.REGISTER_SOURCE
                        ? AsyncRegistration.RegistrationType.APP_SOURCE
                        : AsyncRegistration.RegistrationType.APP_TRIGGER,
                null,
                System.currentTimeMillis(),
                0,
                false,
                false,
                null);
    }

    private static AsyncRegistration appTriggerRegistrationRequestWithAdId(
            RegistrationRequest registrationRequest) {
        return createAsyncRegistration(
                UUID.randomUUID().toString(),
                registrationRequest.getRegistrationUri(),
                null,
                null,
                Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
                null,
                Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
                registrationRequest.getRegistrationType() == RegistrationRequest.REGISTER_SOURCE
                        ? AsyncRegistration.RegistrationType.APP_SOURCE
                        : AsyncRegistration.RegistrationType.APP_TRIGGER,
                null,
                System.currentTimeMillis(),
                0,
                false,
                true,
                PLATFORM_AD_ID_VALUE);
    }

    private static AsyncRegistration webTriggerRegistrationRequest(
            WebTriggerRegistrationRequest webTriggerRegistrationRequest,
            boolean arDebugPermission) {
        if (webTriggerRegistrationRequest.getTriggerParams().size() > 0) {
            WebTriggerParams webTriggerParams =
                    webTriggerRegistrationRequest.getTriggerParams().get(0);
            return createAsyncRegistration(
                    UUID.randomUUID().toString(),
                    webTriggerParams.getRegistrationUri(),
                    null,
                    null,
                    Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
                    null,
                    webTriggerRegistrationRequest.getDestination(),
                    AsyncRegistration.RegistrationType.WEB_TRIGGER,
                    null,
                    System.currentTimeMillis(),
                    0,
                    arDebugPermission,
                    false,
                    null);
        }
        return null;
    }

    private static AsyncRegistration createAsyncRegistration(
            String iD,
            Uri registrationUri,
            Uri webDestination,
            Uri osDestination,
            Uri registrant,
            Uri verifiedDestination,
            Uri topOrigin,
            AsyncRegistration.RegistrationType registrationType,
            Source.SourceType sourceType,
            long mRequestTime,
            long mRetryCount,
            boolean debugKeyAllowed,
            boolean adIdPermission,
            String adIdValue) {
        return new AsyncRegistration.Builder()
                .setId(iD)
                .setRegistrationUri(registrationUri)
                .setWebDestination(webDestination)
                .setOsDestination(osDestination)
                .setRegistrant(registrant)
                .setVerifiedDestination(verifiedDestination)
                .setTopOrigin(topOrigin)
                .setType(registrationType)
                .setSourceType(sourceType)
                .setRequestTime(mRequestTime)
                .setRetryCount(mRetryCount)
                .setDebugKeyAllowed(debugKeyAllowed)
                .setRegistrationId(UUID.randomUUID().toString())
                .setAdIdPermission(adIdPermission)
                .setPlatformAdId(adIdValue)
                .build();
    }

    private static Map<String, List<String>> getDefaultHeaders() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}"));
        return headers;
    }

    private static void assertTriggerRegistration(
            AsyncRegistration asyncRegistration, Trigger result) throws JSONException {
        assertEquals(
                asyncRegistration.getRegistrant().toString(),
                result.getAttributionDestination().toString());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(asyncRegistration.getRegistrationUri(), result.getRegistrationOrigin());
    }
}
