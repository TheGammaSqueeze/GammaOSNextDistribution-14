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

import static android.view.MotionEvent.ACTION_BUTTON_PRESS;
import static android.view.MotionEvent.obtain;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.doNothingOnErrorLogUtilError;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.verifyErrorLogUtilError;
import static com.android.adservices.service.Flags.MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES;
import static com.android.adservices.service.Flags.MEASUREMENT_MAX_DISTINCT_WEB_DESTINATIONS_IN_SOURCE_REGISTRATION;
import static com.android.adservices.service.Flags.MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.android.adservices.service.Flags.MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_INVALID;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.RegistrationRequestFixture;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.net.Uri;
import android.util.Pair;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.WebUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.TriggerSpec;
import com.android.adservices.service.measurement.TriggerSpecs;
import com.android.adservices.service.measurement.util.Enrollment;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.MeasurementRegistrationResponseStats;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableMultiset;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;

import java.io.ByteArrayOutputStream;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.net.ssl.HttpsURLConnection;

/** Unit tests for {@link AsyncSourceFetcher} */
@SpyStatic(FlagsFactory.class)
@SpyStatic(Enrollment.class)
@RunWith(Parameterized.class)
public final class AsyncSourceFetcherTest extends AdServicesExtendedMockitoTestCase {

    private static final String ANDROID_APP_SCHEME = "android-app";
    private static final String ANDROID_APP_SCHEME_URI_PREFIX = ANDROID_APP_SCHEME + "://";
    private static final String DEFAULT_REGISTRATION =
            WebUtil.validUrl("https://subdomain.foo.test");
    private static final String ENROLLMENT_ID = "enrollment-id";
    private static final String DEFAULT_TOP_ORIGIN =
            "https://com.android.adservices.servicecoretest";
    private static final String DEFAULT_DESTINATION = "android-app://com.myapps";
    private static final String DEFAULT_DESTINATION_WITHOUT_SCHEME = "com.myapps";
    private static final long DEFAULT_PRIORITY = 123;
    private static final long DEFAULT_EXPIRY = 456789;
    private static final long DEFAULT_EXPIRY_ROUNDED = 432000;
    private static final UnsignedLong DEFAULT_EVENT_ID = new UnsignedLong(987654321L);
    private static final UnsignedLong EVENT_ID_1 = new UnsignedLong(987654321L);
    private static final UnsignedLong DEBUG_KEY = new UnsignedLong(823523783L);
    private static final String LIST_TYPE_REDIRECT_URI = WebUtil.validUrl("https://bar.test");
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
    private static final long ALT_EVENT_ID = 123456789;
    private static final long ALT_EXPIRY = 456790;
    private static final Uri REGISTRATION_URI_1 = WebUtil.validUri("https://subdomain.foo.test");
    private static final Uri REGISTRATION_URI_2 = WebUtil.validUri("https://subdomain.foo2.test");
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";
    private static final Uri OS_DESTINATION = Uri.parse("android-app://com.os-destination");
    private static final String LONG_FILTER_STRING = "12345678901234567890123456";
    private static final String LONG_AGGREGATE_KEY_ID = "12345678901234567890123456";
    private static final String LONG_AGGREGATE_KEY_PIECE = "0x123456789012345678901234567890123";
    private static final Uri OS_DESTINATION_WITH_PATH =
            Uri.parse("android-app://com.os-destination/my/path");
    private static final Uri WEB_DESTINATION = WebUtil.validUri("https://web-destination.test");
    private static final Uri WEB_DESTINATION_WITH_SUBDOMAIN =
            WebUtil.validUri("https://subdomain.web-destination.test");
    private static final Uri WEB_DESTINATION_2 = WebUtil.validUri("https://web-destination2.test");
    private static final WebSourceParams SOURCE_REGISTRATION_1 =
            new WebSourceParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();
    private static final WebSourceParams SOURCE_REGISTRATION_2 =
            new WebSourceParams.Builder(REGISTRATION_URI_2).setDebugKeyAllowed(false).build();
    private static final String SHARED_AGGREGATION_KEYS =
            "[\"GoogleCampaignCounts\",\"GoogleGeo\"]";
    private static final String DEBUG_JOIN_KEY = "SAMPLE_DEBUG_JOIN_KEY";
    private static final String SHARED_FILTER_DATA_KEYS = "[\"product\",\"conversion_subdomain\"]";

    private static final int UNKNOWN_SOURCE_TYPE = 0;
    private static final int EVENT_SOURCE_TYPE = 1;
    private static final int UNKNOWN_REGISTRATION_SURFACE_TYPE = 0;
    private static final int APP_REGISTRATION_SURFACE_TYPE = 2;
    private static final int UNKNOWN_STATUS = 0;
    private static final int SUCCESS_STATUS = 1;
    private static final int UNKNOWN_REGISTRATION_FAILURE_TYPE = 0;
    private static final String PLATFORM_AD_ID_VALUE = "SAMPLE_PLATFORM_AD_ID_VALUE";
    private static final String DEBUG_AD_ID_VALUE = "SAMPLE_DEBUG_AD_ID_VALUE";
    private static final String POST_BODY = "{\"ad_location\":\"bottom_right\"}";
    private AsyncSourceFetcher mFetcher;

    @Mock private HttpsURLConnection mUrlConnection;
    @Mock private EnrollmentDao mEnrollmentDao;
    @Mock private Flags mFlags;
    @Mock private AdServicesLogger mLogger;

    // Parameterised setup to run all the tests once with ARA parsing V1 flag on, and once with the
    // flag off.
    private final Boolean mAraParsingAlignmentV1Enabled;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> getConfig() throws IOException, JSONException {
        return List.of(
                new Object[]{ "ara_parsing_alignment_v1_enabled", Boolean.TRUE },
                new Object[]{ "ara_parsing_alignment_v1_disabled", Boolean.FALSE });
    }

    public AsyncSourceFetcherTest(String name, Boolean araParsingAlignmentV1Enabled) {
        mAraParsingAlignmentV1Enabled = araParsingAlignmentV1Enabled;
    }

    @Before
    public void setup() {
        extendedMockito.mockGetFlags(FlagsFactory.getFlagsForTest());

        mFetcher = spy(new AsyncSourceFetcher(sContext, mEnrollmentDao, mFlags));
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
        if (mAraParsingAlignmentV1Enabled) {
            when(mFlags.getMeasurementMinimumAggregatableReportWindowInSeconds())
                    .thenReturn(Flags.MEASUREMENT_MINIMUM_AGGREGATABLE_REPORT_WINDOW_IN_SECONDS);
        }
        doReturn(Flags.MEASUREMENT_MINIMUM_EVENT_REPORT_WINDOW_IN_SECONDS)
                .when(mFlags).getMeasurementMinimumEventReportWindowInSeconds();
        doReturn(Flags.MEASUREMENT_FLEX_API_MAX_EVENT_REPORTS)
                .when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(Flags.MEASUREMENT_FLEX_API_MAX_EVENT_REPORT_WINDOWS)
                .when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mFlags.getMeasurementMaxAggregateKeysPerSourceRegistration()).thenReturn(
                Flags.MEASUREMENT_MAX_AGGREGATE_KEYS_PER_SOURCE_REGISTRATION);
        when(mFlags.getMeasurementMaxAttributionFilters())
                .thenReturn(Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS);
        when(mFlags.getMeasurementMaxValuesPerAttributionFilter())
                .thenReturn(Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER);
        when(mFlags.getMeasurementMaxDistinctWebDestinationsInSourceRegistration())
                .thenReturn(Flags.MEASUREMENT_MAX_DISTINCT_WEB_DESTINATIONS_IN_SOURCE_REGISTRATION);
        when(mFlags.getMeasurementMaxReportingRegisterSourceExpirationInSeconds())
                .thenReturn(Flags.MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        when(mFlags.getMeasurementMinReportingRegisterSourceExpirationInSeconds())
                .thenReturn(MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        when(mFlags.getMeasurementMaxPostInstallExclusivityWindow())
                .thenReturn(Flags.MEASUREMENT_MAX_POST_INSTALL_EXCLUSIVITY_WINDOW);
        when(mFlags.getMeasurementMaxInstallAttributionWindow())
                .thenReturn(Flags.MEASUREMENT_MAX_INSTALL_ATTRIBUTION_WINDOW);
        when(mFlags.getMeasurementMinInstallAttributionWindow())
                .thenReturn(Flags.MEASUREMENT_MIN_INSTALL_ATTRIBUTION_WINDOW);
        when(mFlags.getMeasurementVtcConfigurableMaxEventReportsCount())
                .thenReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT);
        when(mFlags.getMeasurementEventReportsVtcEarlyReportingWindows())
                .thenReturn(Flags.MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS);
        when(mFlags.getMeasurementEventReportsCtcEarlyReportingWindows())
                .thenReturn(Flags.MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS);
    }

    @Test
    public void testBasicSourceRequest() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        doReturn(5000L).when(mFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \""
                                                + DEFAULT_PRIORITY
                                                + "\",\n"
                                                + "  \"expiry\": \""
                                                + DEFAULT_EXPIRY
                                                + "\",\n"
                                                + "  \"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\","
                                                + "\"shared_aggregation_keys\": "
                                                + SHARED_AGGREGATION_KEYS
                                                + "}\n")));
        when(mFlags.getMeasurementEnableXNA()).thenReturn(true);

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        asyncFetchStatus.setRegistrationDelay(0L);
        // Execution
        AsyncRegistration asyncRegistration = appSourceRegistrationRequest(request);
        Optional<Source> fetch =
                mFetcher.fetchSource(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_PRIORITY, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(DEFAULT_EXPIRY_ROUNDED),
                result.getExpiryTime());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(DEBUG_KEY, result.getDebugKey());
        assertEquals(SHARED_AGGREGATION_KEYS, result.getSharedAggregationKeys());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        assertNotNull(result.getRegistrationId());
        assertEquals(asyncRegistration.getRegistrationId(), result.getRegistrationId());
        FetcherUtil.emitHeaderMetrics(
                MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES,
                mLogger,
                asyncRegistration,
                asyncFetchStatus);
        verify(mLogger)
                .logMeasurementRegistrationsResponseSize(
                        eq(
                                new MeasurementRegistrationResponseStats.Builder(
                                                AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE,
                                                253,
                                                EVENT_SOURCE_TYPE,
                                                APP_REGISTRATION_SURFACE_TYPE,
                                                SUCCESS_STATUS,
                                                UNKNOWN_REGISTRATION_FAILURE_TYPE,
                                                0,
                                                ANDROID_APP_SCHEME_URI_PREFIX
                                                        + sContext.getPackageName(),
                                                0,
                                                false)
                                        .setAdTechDomain(null)
                                        .build()));
        verify(mUrlConnection).setRequestMethod("POST");
        verify(mUrlConnection).setRequestProperty("Attribution-Reporting-Source-Info", "event");
    }

    @Test
    @SpyStatic(ErrorLogUtil.class)
    public void testBasicSourceRequest_skipSourceWhenNotEnrolled() throws Exception {
        doNothingOnErrorLogUtilError();
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        ExtendedMockito.doReturn(Optional.empty())
                .when(
                        () ->
                                Enrollment.getValidEnrollmentId(
                                        any(), anyString(), any(), any(), any()));
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                AsyncRedirects.REDIRECT_LIST_HEADER_KEY,
                                List.of(LIST_TYPE_REDIRECT_URI),
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \""
                                                + DEFAULT_PRIORITY
                                                + "\",\n"
                                                + "  \"expiry\": \""
                                                + DEFAULT_EXPIRY
                                                + "\",\n"
                                                + "  \"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion

        // Redirects should be parsed & added
        verify(mFetcher, times(1)).openUrl(any());
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(1, asyncRedirects.getRedirects().size());
        assertEquals(
                LIST_TYPE_REDIRECT_URI, asyncRedirects.getRedirects().get(0).getUri().toString());

        // Source shouldn't be created
        assertEquals(
                AsyncFetchStatus.EntityStatus.INVALID_ENROLLMENT,
                asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verifyErrorLogUtilError(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_INVALID,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
    }

    @Test
    public void fetchSource_multipleWebDestinations_success() throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\": \"android-app://com.myapps\","
                                                + "\"web_destination\": ["
                                                + "\""
                                                + WEB_DESTINATION
                                                + "\","
                                                + "\""
                                                + WEB_DESTINATION_2
                                                + "\"],"
                                                + "\"priority\": \"123\","
                                                + "\"expiry\": \"432000\","
                                                + "\"source_event_id\": \"987654321\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals("android-app://com.myapps", result.getAppDestinations().get(0).toString());
        assertTrue(ImmutableMultiset.of(WEB_DESTINATION, WEB_DESTINATION_2).equals(
                ImmutableMultiset.copyOf(result.getWebDestinations())));
        assertEquals(123, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(432000), result.getExpiryTime());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchSource_duplicateWebDestinationsInList_removesDuplicates() throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\": \"android-app://com.myapps\","
                                                + "\"web_destination\": ["
                                                + "\""
                                                + WEB_DESTINATION
                                                + "\","
                                                + "\""
                                                + WEB_DESTINATION_2
                                                + "\","
                                                + "\""
                                                + WEB_DESTINATION
                                                + "\"],"
                                                + "\"priority\": \"123\","
                                                + "\"expiry\": \"432000\","
                                                + "\"source_event_id\": \"987654321\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals("android-app://com.myapps", result.getAppDestinations().get(0).toString());
        assertTrue(ImmutableMultiset.of(WEB_DESTINATION, WEB_DESTINATION_2).equals(
                ImmutableMultiset.copyOf(result.getWebDestinations())));
        assertEquals(123, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(432000), result.getExpiryTime());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchSource_invalidWebDestinationInList_fails() throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\": \"android-app://com.myapps\","
                                                + "\"web_destination\": ["
                                                + "\""
                                                + WEB_DESTINATION
                                                + "\","
                                                + "\"https://not-a-real-domain.test\"],"
                                                + "\"priority\": \"123\","
                                                + "\"expiry\": \"432000\","
                                                + "\"source_event_id\": \"987654321\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchSource_tooManyWebDestinationInList_fails() throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        int maxDistinctDestinations =
                MEASUREMENT_MAX_DISTINCT_WEB_DESTINATIONS_IN_SOURCE_REGISTRATION;
        StringBuilder destinationsBuilder = new StringBuilder("[");
        destinationsBuilder.append(
                IntStream.range(0, maxDistinctDestinations + 1)
                        .mapToObj(i -> "\"" + WebUtil.validUri(
                                "https://web-destination" + i + ".test").toString() + "\"")
                        .collect(Collectors.joining(",")));
        destinationsBuilder.append("]");
        String destinations = destinationsBuilder.toString();
        // Assert destinations is a valid by JSON array by throwing if not (otherwise, we could be
        // testing a different failure).
        new JSONArray(destinations);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\": \"android-app://com.myapps\","
                                                + "\"web_destination\": " + destinations + ","
                                                + "\"priority\": \"123\","
                                                + "\"expiry\": \"432000\","
                                                + "\"source_event_id\": \"987654321\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testSourceRequestWithPostInstallAttributes() throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"432000\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"install_attribution_window\": \"272800\",\n"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals("android-app://com.myapps", result.getAppDestinations().get(0).toString());
        assertEquals(123, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(432000), result.getExpiryTime());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        assertEquals(TimeUnit.SECONDS.toMillis(272800), result.getInstallAttributionWindow());
        assertEquals(TimeUnit.SECONDS.toMillis(987654L), result.getInstallCooldownWindow());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testSourceRequestWithPostInstallAttributesReceivedAsNull() throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"432000\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"install_attribution_window\": null,\n"
                                                + "  \"post_install_exclusivity_window\": null\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals("android-app://com.myapps", result.getAppDestinations().get(0).toString());
        assertEquals(123, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(432000), result.getExpiryTime());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        // fallback to default value - 30 days
        assertEquals(TimeUnit.SECONDS.toMillis(2592000L), result.getInstallAttributionWindow());
        // fallback to default value - 0 days
        assertEquals(0L, result.getInstallCooldownWindow());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testSourceRequestWithInstallAttributesOutofBounds() throws IOException {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"432000\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                // Min value of attribution is 1 day or 86400
                                                // seconds
                                                + "  \"install_attribution_window\": \"86300\",\n"
                                                // Max value of cooldown is 30 days or 2592000
                                                // seconds
                                                + "  \"post_install_exclusivity_window\":"
                                                + " \"9876543210\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals("android-app://com.myapps", result.getAppDestinations().get(0).toString());
        assertEquals(123, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(432000), result.getExpiryTime());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        // Adjusted to minimum allowed value
        assertEquals(TimeUnit.SECONDS.toMillis(86400), result.getInstallAttributionWindow());
        // Adjusted to maximum allowed value
        assertEquals(TimeUnit.SECONDS.toMillis((2592000L)), result.getInstallCooldownWindow());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void registerSource_nonHttpsUrl_rejectsSource() {
        RegistrationRequest request =
                RegistrationRequestFixture.getInvalidRegistrationRequest(
                        RegistrationRequest.REGISTER_SOURCE,
                        WebUtil.validUri("http://foo.test"),
                        sContext.getPackageName(),
                        SDK_PACKAGE_NAME);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.ResponseStatus.INVALID_URL, asyncFetchStatus.getResponseStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void testBadSourceUrl() {
        RegistrationRequest request =
                RegistrationRequestFixture.getInvalidRegistrationRequest(
                        RegistrationRequest.REGISTER_SOURCE,
                        WebUtil.validUri("bad-schema://foo.test"),
                        sContext.getPackageName(),
                        SDK_PACKAGE_NAME);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.ResponseStatus.INVALID_URL, asyncFetchStatus.getResponseStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void testBadSourceConnection() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doThrow(new IOException("Bad internet things"))
                .when(mFetcher)
                .openUrl(new URL(DEFAULT_REGISTRATION));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.ResponseStatus.NETWORK_ERROR,
                asyncFetchStatus.getResponseStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void testBadSourceJson_missingSourceEventId() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\"")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.PARSING_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBadSourceJson_missingHeader() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields()).thenReturn(Collections.emptyMap());
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.HEADER_MISSING, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBadSourceJson_missingDestination() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\"")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.PARSING_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestMinimumFields() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        long expiry =
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        assertEquals(expiry, result.getExpiryTime());
        assertNull(result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_expiryNegative_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + DEFAULT_EVENT_ID + "\","
                                        + "\"expiry\":\"-15\""
                                        + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_expiry_tooEarly_setToMin() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"expiry\":\"86399\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(1);
        assertEquals(expiry, result.getExpiryTime());
        assertNull(result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_expiry_tooLate_setToMax() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"expiry\":\"2592001\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        long expiry =
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        assertEquals(expiry, result.getExpiryTime());
        assertNull(result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_expiryCloserToUpperBound_roundsUp() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"expiry\":\""
                                                + String.valueOf(
                                                        TimeUnit.DAYS.toSeconds(1)
                                                                + TimeUnit.DAYS.toSeconds(1) / 2L)
                                                + "\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(2);
        assertEquals(expiry, result.getExpiryTime());
        assertNull(result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_expiryCloserToLowerBound_roundsDown() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"expiry\":\""
                                                + String.valueOf(
                                                        TimeUnit.DAYS.toSeconds(1)
                                                                + TimeUnit.DAYS.toSeconds(1) / 2L
                                                                - 1L)
                                                + "\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(1);
        assertEquals(expiry, result.getExpiryTime());
        assertNull(result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_expiryCloserToLowerBound_roundsToOneDayAtLeast() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"expiry\":\""
                                                + String.valueOf(
                                                        TimeUnit.DAYS.toSeconds(1) / 2L - 1L)
                                                + "\""
                                                + "}")));
        // Make sure the expiry bound does not already round the value up before rounding.
        doReturn(TimeUnit.HOURS.toSeconds(1))
                .when(mFlags).getMeasurementMinReportingRegisterSourceExpirationInSeconds();
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(1);
        assertEquals(expiry, result.getExpiryTime());
        assertNull(result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_navigationType_doesNotRoundExpiry() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, getInputEvent());
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"expiry\":\""
                                                + String.valueOf(TimeUnit.HOURS.toSeconds(25))
                                                + "\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        long expiry = result.getEventTime() + TimeUnit.HOURS.toMillis(25);
        assertEquals(expiry, result.getExpiryTime());
        assertNull(result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_reportWindows_defaultToExpiry() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"expiry\":\"172800\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(2);
        assertEquals(expiry, result.getExpiryTime());
        assertNull(result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_reportWindows_lessThanExpiry_setAsIs() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"expiry\":\"172800\","
                                                + "\"event_report_window\":\"86400\","
                                                + "\"aggregatable_report_window\":\"86400\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertEquals(result.getEventTime() + TimeUnit.DAYS.toMillis(2), result.getExpiryTime());
        assertEquals(Long.valueOf(TimeUnit.DAYS.toMillis(1)), result.getEventReportWindow());
        assertEquals(
                result.getEventTime() + TimeUnit.DAYS.toMillis(1),
                result.getAggregatableReportWindow());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
    }

    @Test
    public void sourceRequest_reportWindowsTooEarlyAraParsingV1_setToMin() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"expiry\":\"172800\","
                                                + "\"event_report_window\":\"2000\","
                                                + "\"aggregatable_report_window\":\"1728\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertEquals(result.getEventTime() + TimeUnit.DAYS.toMillis(2), result.getExpiryTime());
        assertEquals(Long.valueOf(TimeUnit.HOURS.toMillis(1)), result.getEventReportWindow());
        assertEquals(
                result.getEventTime() + TimeUnit.HOURS.toMillis(1),
                result.getAggregatableReportWindow());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_reportWindowsTooEarly_setToMin() throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"expiry\":\"172800\","
                                                + "\"event_report_window\":\"2000\","
                                                + "\"aggregatable_report_window\":\"1728\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertEquals(result.getEventTime() + TimeUnit.DAYS.toMillis(2), result.getExpiryTime());
        assertEquals(Long.valueOf(TimeUnit.HOURS.toMillis(1)), result.getEventReportWindow());
        assertEquals(
                result.getEventTime() + TimeUnit.DAYS.toMillis(1),
                result.getAggregatableReportWindow());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_reportWindows_greaterThanExpiry_setToExpiry() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"expiry\":\"172800\","
                                                + "\"event_report_window\":\"172801\","
                                                + "\"aggregatable_report_window\":\"172801\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertEquals(result.getEventTime() + TimeUnit.DAYS.toMillis(2), result.getExpiryTime());
        assertEquals(Long.valueOf(TimeUnit.DAYS.toMillis(2)), result.getEventReportWindow());
        assertEquals(
                result.getEventTime() + TimeUnit.DAYS.toMillis(2),
                result.getAggregatableReportWindow());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_reportWindows_greaterThanExpiry_tooLate_setToExpiry()
            throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"expiry\":\"172800\","
                                                + "\"event_report_window\":\"2592001\","
                                                + "\"aggregatable_report_window\":\"2592001\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertEquals(result.getEventTime() + TimeUnit.DAYS.toMillis(2), result.getExpiryTime());
        assertEquals(Long.valueOf(TimeUnit.DAYS.toMillis(2)), result.getEventReportWindow());
        assertEquals(
                result.getEventTime() + TimeUnit.DAYS.toMillis(2),
                result.getAggregatableReportWindow());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_reportWindowsNotAString_success() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + DEFAULT_EVENT_ID + "\","
                                        + "\"expiry\":\"172800\","
                                        + "\"event_report_window\":86400,"
                                        + "\"aggregatable_report_window\":86400"
                                        + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(result.getEventTime() + TimeUnit.DAYS.toMillis(2), result.getExpiryTime());
        assertEquals(Long.valueOf(TimeUnit.DAYS.toMillis(1)), result.getEventReportWindow());
        assertEquals(
                result.getEventTime() + TimeUnit.DAYS.toMillis(1),
                result.getAggregatableReportWindow());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
    }

    @Test
    public void sourceRequest_eventReportWindowNegative_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + DEFAULT_EVENT_ID + "\","
                                        + "\"expiry\":\"172800\","
                                        + "\"event_report_window\":\"-86400\","
                                        + "\"aggregatable_report_window\":\"86400\""
                                        + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_aggregateReportWindowNegative_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + DEFAULT_EVENT_ID + "\","
                                        + "\"expiry\":\"172800\","
                                        + "\"event_report_window\":\"86400\","
                                        + "\"aggregatable_report_window\":\"-86400\""
                                        + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_priorityNotAString_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + DEFAULT_EVENT_ID + "\","
                                        + "\"priority\":15"
                                        + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchSource_sourceEventIdNotAString_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":35}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchSource_sourceEventIdNegative_fetchSuccess() throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\"-35\"}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertTrue(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchSource_sourceEventIdNegative_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\"-35\"}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchSource_sourceEventIdTooLarge_fetchSuccess() throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\",\"source_event_id\":\""
                                                + "18446744073709551616\"}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertTrue(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchSource_sourceEventIdTooLarge_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\",\"source_event_id\":\""
                                                + "18446744073709551616\"}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchSource_sourceEventIdNotAnInt_fetchSuccess() throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\"8l2\"}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertTrue(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchSource_sourceEventIdNotAnInt_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\"8l2\"}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchSource_xnaDisabled_nullSharedAggregationKeys() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \""
                                                + DEFAULT_PRIORITY
                                                + "\",\n"
                                                + "  \"expiry\": \""
                                                + DEFAULT_EXPIRY
                                                + "\",\n"
                                                + "  \"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\","
                                                + "\"shared_aggregation_keys\": "
                                                + SHARED_AGGREGATION_KEYS
                                                + "}\n")));
        when(mFlags.getMeasurementEnableXNA()).thenReturn(false);

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_PRIORITY, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(DEFAULT_EXPIRY_ROUNDED),
                result.getExpiryTime());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(DEBUG_KEY, result.getDebugKey());
        assertNull(result.getSharedAggregationKeys());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
    }

    @Test
    public void testBasicSourceRequest_sourceEventId_uses64thBit() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\",\"source_event_id\":\""
                                                + "18446744073709551615\"}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(0, result.getPriority());
        assertEquals(new UnsignedLong(-1L), result.getEventId());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequest_debugKeyNotAString_setAsNull() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_key\":18}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertNull(result.getDebugKey());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequest_debugKeyNegative_setAsNull() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_key\":\"-18\"}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertNull(result.getDebugKey());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequest_debugKey_tooLarge() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_key\":\"18446744073709551616\"}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertNull(result.getDebugKey());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequest_debugKey_notAnInt() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_key\":\"987fs\"}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertNull(result.getDebugKey());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequest_debugKey_uses64thBit() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_key\":\"18446744073709551615\"}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertEquals(new UnsignedLong(-1L), result.getDebugKey());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestMinimumFieldsAndRestNull() throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \"android-app://com.myapps\",\n"
                                                + "\"source_event_id\": \"123\",\n"
                                                + "\"priority\": null,\n"
                                                + "\"expiry\": null\n"
                                                + "}\n")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals("android-app://com.myapps", result.getAppDestinations().get(0).toString());
        assertEquals(new UnsignedLong(123L), result.getEventId());
        assertEquals(0, result.getPriority());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithNumericExpiryLessThan2Days() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "\"expiry\": 1"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithExpiryLessThan2Days() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "\"expiry\": \"1\""
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithNumericExpiryMoreThan30Days() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "\"expiry\": 2678400"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithExpiryMoreThan30Days() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "\"expiry\": \"2678400\""
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithDebugReportingHeader() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_reporting\":\"true\"}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertTrue(result.isDebugReporting());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithInvalidDebugReportingHeader() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_reporting\":\"invalid\"}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertFalse(result.isDebugReporting());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithNullDebugReportingHeader() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_reporting\":\"null\"}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertFalse(result.isDebugReporting());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithInvalidNoQuotesDebugReportingHeader() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_reporting\":null}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertFalse(result.isDebugReporting());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithoutDebugReportingHeader() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\"}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertFalse(result.isDebugReporting());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void test500_ignoreFailure() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(500);
        Map<String, List<String>> headersSecondRequest = new HashMap<>();
        headersSecondRequest.put(
                "Attribution-Reporting-Register-Source",
                List.of(
                        "{\n"
                                + "\"destination\": \""
                                + DEFAULT_DESTINATION
                                + "\",\n"
                                + "\"source_event_id\": \""
                                + ALT_EVENT_ID
                                + "\",\n"
                                + "\"expiry\": "
                                + ALT_EXPIRY
                                + ""
                                + "}\n"));
        when(mUrlConnection.getHeaderFields()).thenReturn(headersSecondRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE,
                asyncFetchStatus.getResponseStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testFailedParsingButValidRedirect_returnFailureWithRedirect() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        Map<String, List<String>> headersFirstRequest = new HashMap<>();
        headersFirstRequest.put("Attribution-Reporting-Register-Source", List.of("{}"));
        headersFirstRequest.put(
                AsyncRedirects.REDIRECT_LIST_HEADER_KEY, List.of(LIST_TYPE_REDIRECT_URI));
        Map<String, List<String>> headersSecondRequest = new HashMap<>();
        headersSecondRequest.put(
                "Attribution-Reporting-Register-Source",
                List.of(
                        "{\n"
                                + "\"destination\": \""
                                + DEFAULT_DESTINATION
                                + "\",\n"
                                + "\"source_event_id\": \""
                                + ALT_EVENT_ID
                                + "\",\n"
                                + "\"expiry\": "
                                + ALT_EXPIRY
                                + ""
                                + "}\n"));
        when(mUrlConnection.getHeaderFields())
                .thenReturn(headersFirstRequest)
                .thenReturn(headersSecondRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(1, asyncRedirects.getRedirects().size());
        assertEquals(
                LIST_TYPE_REDIRECT_URI, asyncRedirects.getRedirects().get(0).getUri().toString());
        assertEquals(
                AsyncFetchStatus.EntityStatus.PARSING_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void test_validateSource_numericExpiry_returnSuccess() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        Map<String, List<String>> headersFirstRequest = new HashMap<>();
        headersFirstRequest.put(
                "Attribution-Reporting-Register-Source",
                List.of(
                        "{\n"
                                + "\"destination\": \""
                                + DEFAULT_DESTINATION
                                + "\",\n"
                                + "\"source_event_id\": \""
                                + DEFAULT_EVENT_ID
                                + "\",\n"
                                + "\"priority\": \""
                                + DEFAULT_PRIORITY
                                + "\",\n"
                                + "\"expiry\": "
                                + DEFAULT_EXPIRY
                                + ""
                                + "}\n"));
        headersFirstRequest.put(
                AsyncRedirects.REDIRECT_LIST_HEADER_KEY, List.of(LIST_TYPE_REDIRECT_URI));
        when(mUrlConnection.getHeaderFields()).thenReturn(headersFirstRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(AsyncFetchStatus.EntityStatus.SUCCESS, asyncFetchStatus.getEntityStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(DEFAULT_PRIORITY, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(DEFAULT_EXPIRY_ROUNDED),
                result.getExpiryTime());
        assertEquals(1, asyncRedirects.getRedirects().size());
        assertEquals(
                LIST_TYPE_REDIRECT_URI, asyncRedirects.getRedirects().get(0).getUri().toString());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void test_validateSource_returnSuccess() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        Map<String, List<String>> headersFirstRequest = new HashMap<>();
        headersFirstRequest.put(
                "Attribution-Reporting-Register-Source",
                List.of(
                        "{\n"
                                + "\"destination\": \""
                                + DEFAULT_DESTINATION
                                + "\",\n"
                                + "\"source_event_id\": \""
                                + DEFAULT_EVENT_ID
                                + "\",\n"
                                + "\"priority\": \""
                                + DEFAULT_PRIORITY
                                + "\",\n"
                                + "\"expiry\": \""
                                + DEFAULT_EXPIRY
                                + "\""
                                + "}\n"));
        headersFirstRequest.put(
                AsyncRedirects.REDIRECT_LIST_HEADER_KEY, List.of(LIST_TYPE_REDIRECT_URI));
        when(mUrlConnection.getHeaderFields()).thenReturn(headersFirstRequest);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(AsyncFetchStatus.EntityStatus.SUCCESS, asyncFetchStatus.getEntityStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(DEFAULT_PRIORITY, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(DEFAULT_EXPIRY_ROUNDED),
                result.getExpiryTime());
        assertEquals(1, asyncRedirects.getRedirects().size());
        assertEquals(
                LIST_TYPE_REDIRECT_URI, asyncRedirects.getRedirects().get(0).getUri().toString());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    // Tests for redirect types

    @Test
    public void testRedirects_bothRedirectHeaderTypes_addBoth() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        Map<String, List<String>> headers = getDefaultHeaders();

        // Populate both 'list' and 'location' type headers
        headers.put(AsyncRedirects.REDIRECT_LIST_HEADER_KEY, List.of(LIST_TYPE_REDIRECT_URI));
        headers.put(
                AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY, List.of(LOCATION_TYPE_REDIRECT_URI));

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertDefaultSourceRegistration(result);
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
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testRedirects_locationRedirectHeadersOnly() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(302);
        Map<String, List<String>> headers = getDefaultHeaders();

        // Populate only 'location' type header
        headers.put(
                AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY, List.of(LOCATION_TYPE_REDIRECT_URI));

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertDefaultSourceRegistration(result);
        assertEquals(1, asyncRedirects.getRedirects().size());
        assertEquals(
                LOCATION_TYPE_REDIRECT_URI,
                asyncRedirects.getRedirects().get(0).getUri().toString());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testRedirects_listRedirectHeadersOnly() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(302);
        Map<String, List<String>> headers = getDefaultHeaders();

        headers.put(AsyncRedirects.REDIRECT_LIST_HEADER_KEY, List.of(LIST_TYPE_REDIRECT_URI));

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertDefaultSourceRegistration(result);
        assertEquals(1, asyncRedirects.getRedirects().size());
        assertEquals(
                LIST_TYPE_REDIRECT_URI, asyncRedirects.getRedirects().get(0).getUri().toString());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testRedirects_locationRedirectHeadersToWellKnown() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(302);
        when(mFlags.getMeasurementEnableRedirectToWellKnownPath()).thenReturn(true);
        Map<String, List<String>> headers = getDefaultHeaders();

        headers.put(
                AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY, List.of(LOCATION_TYPE_REDIRECT_URI));
        headers.put(
                AsyncRedirects.HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG,
                List.of(AsyncRedirects.REDIRECT_302_TO_WELL_KNOWN));

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertDefaultSourceRegistration(result);
        assertEquals(1, asyncRedirects.getRedirects().size());
        assertEquals(
                LOCATION_TYPE_REDIRECT_WELLKNOWN_URI,
                asyncRedirects.getRedirects().get(0).getUri().toString());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testRedirects_bothHeaders_locationTypeToWellKnown() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
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
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertDefaultSourceRegistration(result);
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
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testRedirects_wellKnownHeader_noop() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(302);
        when(mFlags.getMeasurementEnableRedirectToWellKnownPath()).thenReturn(true);
        Map<String, List<String>> headers = getDefaultHeaders();

        // Populate only redirect behavior header with no actual redirect
        headers.put(
                AsyncRedirects.HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG,
                List.of(AsyncRedirects.REDIRECT_302_TO_WELL_KNOWN));

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertDefaultSourceRegistration(result);
        assertEquals(0, asyncRedirects.getRedirects().size());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testRedirects_wellKnownHeader_acceptsListTypeWithNoLocationType() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mFlags.getMeasurementEnableRedirectToWellKnownPath()).thenReturn(true);
        Map<String, List<String>> headers = getDefaultHeaders();

        headers.put(AsyncRedirects.REDIRECT_LIST_HEADER_KEY, List.of(LIST_TYPE_REDIRECT_URI));
        headers.put(
                AsyncRedirects.HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG,
                List.of(AsyncRedirects.REDIRECT_302_TO_WELL_KNOWN));

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertDefaultSourceRegistration(result);
        assertEquals(1, asyncRedirects.getRedirects().size());
        assertEquals(
                LIST_TYPE_REDIRECT_URI,
                asyncRedirects
                        .getRedirectsByType(AsyncRegistration.RedirectType.LIST)
                        .get(0)
                        .getUri()
                        .toString());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testRedirects_locationRedirectHeaders_wellKnownHeaderMisconfigured()
            throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(302);
        Map<String, List<String>> headers = getDefaultHeaders();

        headers.put(
                AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY, List.of(LOCATION_TYPE_REDIRECT_URI));
        headers.put(AsyncRedirects.HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG, List.of("BAD"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertDefaultSourceRegistration(result);
        assertEquals(1, asyncRedirects.getRedirects().size());
        assertEquals(
                LOCATION_TYPE_REDIRECT_URI,
                asyncRedirects.getRedirects().get(0).getUri().toString());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testRedirects_locationRedirectHeaders_wellKnownHeaderNull() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(302);
        Map<String, List<String>> headers = getDefaultHeaders();

        headers.put(
                AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY, List.of(LOCATION_TYPE_REDIRECT_URI));
        headers.put(AsyncRedirects.HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG, null);

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertDefaultSourceRegistration(result);
        assertEquals(1, asyncRedirects.getRedirects().size());
        assertEquals(
                LOCATION_TYPE_REDIRECT_URI,
                asyncRedirects.getRedirects().get(0).getUri().toString());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    // End tests for redirect types

    @Test
    public void testBasicSourceRequestWithFilterData() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        String filterData =
                "  \"filter_data\": {\"product\":[\"1234\",\"2345\"], \"ctid\":[\"id\"]} \n";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"432000\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + filterData
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals("android-app://com.myapps", result.getAppDestinations().get(0).toString());
        assertEquals(123, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(432000), result.getExpiryTime());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        assertEquals(
                "{\"product\":[\"1234\",\"2345\"],\"ctid\":[\"id\"]}",
                result.getFilterDataString());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_filterDataIncludesSourceType_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        String filterData =
                "\"filter_data\":{\"product\":[\"1234\",\"2345\"],\"source_type\":[\"event\"]}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"432000\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + filterData
                                            + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_filterDataIncludesReservedPrefix_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        String filterData =
                "\"filter_data\":{\"product\":[\"1234\",\"2345\"],\"_abc_reserved\":[\"event\"]}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"432000\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + filterData
                                            + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testSourceRequest_filterData_invalidJson() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        String filterData =
                "  \"filter_data\": {\"product\":\"1234\",\"2345\"], \"ctid\":[\"id\"]} \n";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + filterData
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequest_filterData_tooManyFilters() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        StringBuilder filters = new StringBuilder("{");
        filters.append(
                IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
                        .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                        .collect(Collectors.joining(",")));
        filters.append("}");
        String filterData = "  \"filter_data\": " + filters + "\n";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + filterData
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequest_filterData_keyTooLong() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        String filterData =
                "  \"filter_data\": {\"product\":[\"1234\",\"2345\"], \""
                        + LONG_FILTER_STRING
                        + "\":[\"id\"]} \n";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + filterData
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequest_filterData_tooManyValues() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
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
        String filterData = "  \"filter_data\": " + filters + " \n";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + filterData
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequest_filterData_valueTooLong() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        String filterData =
                "  \"filter_data\": {\"product\":[\"1234\",\""
                        + LONG_FILTER_STRING
                        + "\"], \"ctid\":[\"id\"]} \n";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + filterData
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequest_filterData_shouldNotIncludeKookbackWindow() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        String filterData =
                "  \"filter_data\": {\"product\":[\"1234\"], \"ctid\":[\"id\"], "
                        + "\"_lookback_window\": 123} \n";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + filterData
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testMissingHeaderButWithRedirect() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                AsyncRedirects.REDIRECT_LIST_HEADER_KEY,
                                List.of(LIST_TYPE_REDIRECT_URI)))
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \""
                                                + DEFAULT_PRIORITY
                                                + "\",\n"
                                                + "  \"expiry\": \""
                                                + DEFAULT_EXPIRY
                                                + "\",\n"
                                                + "  \"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.HEADER_MISSING, asyncFetchStatus.getEntityStatus());
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(1, asyncRedirects.getRedirects().size());
        assertEquals(
                LIST_TYPE_REDIRECT_URI,
                asyncRedirects
                        .getRedirectsByType(AsyncRegistration.RedirectType.LIST)
                        .get(0)
                        .getUri()
                        .toString());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithAggregateSource() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "\"aggregation_keys\": {\"campaignCounts\" :"
                                                + " \"0x159\", \"geoValue\" : \"0x5\"}\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(
                new JSONObject("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .toString(),
                result.getAggregateSource());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_aggregationKeysIncludeNonHexChar_fails() throws Exception {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + "\"aggregation_keys\": {\"campaignCounts\" :"
                                            + " \"0x159G\", \"geoValue\" : \"0x5\"}\n"
                                            + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_aggregationKeysIncludeNonHexChar_succeeds() throws Exception {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + "\"aggregation_keys\": {\"campaignCounts\" :"
                                            + " \"0x159G\", \"geoValue\" : \"0x5\"}\n"
                                            + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(
                new JSONObject("{\"campaignCounts\" : \"0x159G\", \"geoValue\" : \"0x5\"}")
                        .toString(),
                result.getAggregateSource());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithAggregateSource_rejectsTooManyKeys() throws Exception {
        StringBuilder tooManyKeys = new StringBuilder("{");
        for (int i = 0; i < 51; i++) {
            tooManyKeys.append(String.format("\"campaign-%1$s\": \"0x15%1$s\"", i));
        }
        tooManyKeys.append("}");
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\":"
                                                + " \"987654321\",\"aggregation_keys\": "
                                                + tooManyKeys)));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.PARSING_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testSourceRequestWithAggregateSource_tooManyKeys() throws Exception {
        StringBuilder tooManyKeys = new StringBuilder("{");
        for (int i = 0; i < mFlags.getMeasurementMaxAggregateKeysPerSourceRegistration() + 1; i++) {
            tooManyKeys.append(String.format("\"campaign-%1$s\": \"0x15%1$s\"", i));
        }
        tooManyKeys.append("}");
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\":"
                                                + " \"987654321\",\"aggregation_keys\": "
                                                + tooManyKeys)));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequestWithAggregateSource_keyIsNotAnObject() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "\"aggregation_keys\": [\"campaignCounts\","
                                                + " \"0x159\", \"geoValue\", \"0x5\"]\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequestWithAggregateSource_invalidKeyId() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "\"aggregation_keys\": {\""
                                                + LONG_AGGREGATE_KEY_ID
                                                + "\": \"0x159\","
                                                + "\"geoValue\": \"0x5\"}\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequestWithAggregateSource_invalidKeyPiece_missingPrefix()
            throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "\"aggregation_keys\": {\"campaignCounts\" :"
                                                + " \"0159\", \"geoValue\" : \"0x5\"}\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequestWithAggregateSource_invalidKeyPiece_tooLong() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "\"aggregation_keys\": {\"campaignCounts\":"
                                                + " \"0x159\", \"geoValue\": \""
                                                + LONG_AGGREGATE_KEY_PIECE
                                                + "\"}\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchWebSources_basic_success() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        AsyncRegistration asyncRegistration = webSourceRegistrationRequest(request, true);
        Optional<Source> fetch =
                mFetcher.fetchSource(asyncRegistration, asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirects.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(Uri.parse(DEFAULT_DESTINATION), result.getAppDestinations().get(0));
        assertEquals(EVENT_ID_1, result.getEventId());
        assertNotNull(result.getRegistrationId());
        assertEquals(asyncRegistration.getRegistrationId(), result.getRegistrationId());
        long expiry =
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        assertEquals(expiry, result.getExpiryTime());
        assertNull(result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSource_multipleWebDestinations_success() throws Exception {
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\": \"android-app://com.myapps\","
                                                + "\"web_destination\": ["
                                                + "\""
                                                + WEB_DESTINATION
                                                + "\","
                                                + "\""
                                                + WEB_DESTINATION_2
                                                + "\"],"
                                                + "\"priority\": \"123\","
                                                + "\"expiry\": \"432000\","
                                                + "\"source_event_id\": \"987654321\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals("android-app://com.myapps", result.getAppDestinations().get(0).toString());
        assertTrue(ImmutableMultiset.of(WEB_DESTINATION, WEB_DESTINATION_2).equals(
                ImmutableMultiset.copyOf(result.getWebDestinations())));
        assertEquals(123, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(432000), result.getExpiryTime());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSource_duplicateWebDestinationsInList_removesDuplicates() throws Exception {
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\": \"android-app://com.myapps\","
                                                + "\"web_destination\": ["
                                                + "\""
                                                + WEB_DESTINATION
                                                + "\","
                                                + "\""
                                                + WEB_DESTINATION_2
                                                + "\","
                                                + "\""
                                                + WEB_DESTINATION
                                                + "\"],"
                                                + "\"priority\": \"123\","
                                                + "\"expiry\": \"432000\","
                                                + "\"source_event_id\": \"987654321\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals("android-app://com.myapps", result.getAppDestinations().get(0).toString());
        assertTrue(ImmutableMultiset.of(WEB_DESTINATION, WEB_DESTINATION_2).equals(
                ImmutableMultiset.copyOf(result.getWebDestinations())));
        assertEquals(123, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(432000), result.getExpiryTime());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSource_invalidWebDestinationInList_fails() throws Exception {
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\": \"android-app://com.myapps\","
                                                + "\"web_destination\": ["
                                                + "\""
                                                + WEB_DESTINATION
                                                + "\","
                                                + "\"https://not-a-real-domain.test\"],"
                                                + "\"priority\": \"123\","
                                                + "\"expiry\": \"432000\","
                                                + "\"source_event_id\": \"987654321\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSource_tooManyWebDestinationInList_fails() throws Exception {
        StringBuilder destinationsBuilder = new StringBuilder("[");
        int maxDistinctDestinations =
                MEASUREMENT_MAX_DISTINCT_WEB_DESTINATIONS_IN_SOURCE_REGISTRATION;
        destinationsBuilder.append(
                IntStream.range(0, maxDistinctDestinations + 1)
                        .mapToObj(i -> "\"" + WebUtil.validUri(
                                "https://web-destination" + i + ".test").toString() + "\"")
                        .collect(Collectors.joining(",")));
        destinationsBuilder.append("]");
        String destinations = destinationsBuilder.toString();
        // Assert destinations is a valid by JSON array by throwing if not (otherwise, we could be
        // testing a different failure).
        new JSONArray(destinations);
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\": \"android-app://com.myapps\","
                                                + "\"web_destination\": " + destinations + ","
                                                + "\"priority\": \"123\","
                                                + "\"expiry\": \"432000\","
                                                + "\"source_event_id\": \"987654321\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSource_expiry_tooEarly_setToMin() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + EVENT_ID_1
                                                + "\","
                                                + "\"expiry\":\"2000\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(1);
        assertEquals(expiry, result.getExpiryTime());
        assertNull(result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
    }

    @Test
    public void fetchWebSource_expiry_tooLate_setToMax() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + EVENT_ID_1
                                                + "\","
                                                + "\"expiry\":\"2592001\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        long expiry =
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        assertEquals(expiry, result.getExpiryTime());
        assertNull(result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
    }

    @Test
    public void fetchWebSource_expiryCloserToUpperBound_roundsUp() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + EVENT_ID_1
                                                + "\","
                                                + "\"expiry\":\""
                                                + String.valueOf(
                                                        TimeUnit.DAYS.toSeconds(1)
                                                                + TimeUnit.DAYS.toSeconds(1) / 2L)
                                                + "\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true, Source.SourceType.EVENT),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(2);
        assertEquals(expiry, result.getExpiryTime());
        assertNull(result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
    }

    @Test
    public void fetchWebSource_expiryCloserToLowerBound_roundsDown() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + EVENT_ID_1
                                                + "\","
                                                + "\"expiry\":\""
                                                + String.valueOf(
                                                        TimeUnit.DAYS.toSeconds(1)
                                                                + TimeUnit.DAYS.toSeconds(1) / 2L
                                                                - 1L)
                                                + "\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true, Source.SourceType.EVENT),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(1);
        assertEquals(expiry, result.getExpiryTime());
        assertNull(result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
    }

    @Test
    public void fetchWebSource_navigationType_doesNotRoundExpiry() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + EVENT_ID_1
                                                + "\","
                                                + "\"expiry\":\""
                                                + String.valueOf(TimeUnit.HOURS.toSeconds(25))
                                                + "\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true, Source.SourceType.NAVIGATION),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        long expiry = result.getEventTime() + TimeUnit.HOURS.toMillis(25);
        assertEquals(expiry, result.getExpiryTime());
        assertNull(result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
    }

    @Test
    public void fetchWebSource_reportWindows_defaultToExpiry() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + EVENT_ID_1
                                                + "\","
                                                + "\"expiry\":\"172800\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(2);
        assertEquals(expiry, result.getExpiryTime());
        assertNull(result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
    }

    @Test
    public void fetchWebSource_reportWindows_lessThanExpiry_setAsIs() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + EVENT_ID_1
                                                + "\","
                                                + "\"expiry\":\"172800\","
                                                + "\"event_report_window\":\"86400\","
                                                + "\"aggregatable_report_window\":\"86400\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(result.getEventTime() + TimeUnit.DAYS.toMillis(2), result.getExpiryTime());
        assertEquals(Long.valueOf(TimeUnit.DAYS.toMillis(1)), result.getEventReportWindow());
        assertEquals(
                result.getEventTime() + TimeUnit.DAYS.toMillis(1),
                result.getAggregatableReportWindow());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
    }

    @Test
    public void fetchWebSource_reportWindowsTooEarlyAraParsingV1_setToMin() throws IOException {
        Assume.assumeTrue(mAraParsingAlignmentV1Enabled);
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + EVENT_ID_1
                                                + "\","
                                                + "\"expiry\":\"172800\","
                                                + "\"event_report_window\":\"2000\","
                                                + "\"aggregatable_report_window\":\"1728\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(result.getEventTime() + TimeUnit.DAYS.toMillis(2), result.getExpiryTime());
        assertEquals(Long.valueOf(TimeUnit.HOURS.toMillis(1)), result.getEventReportWindow());
        assertEquals(
                result.getEventTime() + TimeUnit.HOURS.toMillis(1),
                result.getAggregatableReportWindow());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
    }

    @Test
    public void fetchWebSource_reportWindowsTooEarly_setToMin() throws IOException {
        Assume.assumeFalse(mAraParsingAlignmentV1Enabled);
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + EVENT_ID_1
                                                + "\","
                                                + "\"expiry\":\"172800\","
                                                + "\"event_report_window\":\"2000\","
                                                + "\"aggregatable_report_window\":\"1728\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(result.getEventTime() + TimeUnit.DAYS.toMillis(2), result.getExpiryTime());
        assertEquals(Long.valueOf(TimeUnit.HOURS.toMillis(1)), result.getEventReportWindow());
        assertEquals(
                result.getEventTime() + TimeUnit.DAYS.toMillis(1),
                result.getAggregatableReportWindow());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
    }

    @Test
    public void fetchWebSource_reportWindows_greaterThanExpiry_setToExpiry() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + EVENT_ID_1
                                                + "\","
                                                + "\"expiry\":\"172800\","
                                                + "\"event_report_window\":\"172801\","
                                                + "\"aggregatable_report_window\":\"172801\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(2);
        assertEquals(expiry, result.getExpiryTime());
        assertEquals(Long.valueOf(TimeUnit.DAYS.toMillis(2)), result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
    }

    @Test
    public void fetchWebSource_reportWindows_greaterThanExpiry_tooLate_setToExpiry()
            throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + EVENT_ID_1
                                                + "\","
                                                + "\"expiry\":\"172800\","
                                                + "\"event_report_window\":\"2592001\","
                                                + "\"aggregatable_report_window\":\"2592001\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(2);
        assertEquals(expiry, result.getExpiryTime());
        assertEquals(Long.valueOf(TimeUnit.DAYS.toMillis(2)), result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
    }

    @Test
    public void fetchWebSources_withValidation_success() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        Uri.parse(DEFAULT_DESTINATION),
                        null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirects.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(Uri.parse(DEFAULT_DESTINATION), result.getAppDestinations().get(0));
        assertEquals(EVENT_ID_1, result.getEventId());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSourcesSuccessWithoutArDebugPermission() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1, SOURCE_REGISTRATION_2),
                        DEFAULT_TOP_ORIGIN,
                        Uri.parse(DEFAULT_DESTINATION),
                        null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, false),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirects.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(Uri.parse(DEFAULT_DESTINATION), result.getAppDestinations().get(0));
        assertEquals(EVENT_ID_1, result.getEventId());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
        assertNull(result.getFilterDataString());
        assertNull(result.getAggregateSource());
    }

    @Test
    public void fetchWebSources_validationError_destinationInvalid() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        Uri.parse(DEFAULT_DESTINATION),
                        null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        // Its validation will fail due to destination mismatch
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                /* wrong destination */
                                                + "android-app://com.wrongapp"
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_withExtendedHeaders_success() throws IOException, JSONException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION,
                        null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        String aggregateSource = "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}";
        String filterData = "{\"product\":[\"1234\",\"2345\"]," + "\"ctid\":[\"id\"]}";
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + OS_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"filter_data\": "
                                                + filterData
                                                + ", "
                                                + " \"aggregation_keys\": "
                                                + aggregateSource
                                                + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirects.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(OS_DESTINATION, result.getAppDestinations().get(0));
        assertEquals(filterData, result.getFilterDataString());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(456789L),
                result.getExpiryTime());
        assertEquals(new JSONObject(aggregateSource).toString(), result.getAggregateSource());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_withRedirects_ignoresRedirects() throws IOException, JSONException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        Uri.parse(DEFAULT_DESTINATION),
                        null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        String aggregateSource = "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}";
        String filterData = "{\"product\":[\"1234\",\"2345\"]," + "\"ctid\":[\"id\"]}";
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"filter_data\": "
                                                + filterData
                                                + " , "
                                                + " \"aggregation_keys\": "
                                                + aggregateSource
                                                + "}"),
                                AsyncRedirects.REDIRECT_LIST_HEADER_KEY,
                                List.of(LIST_TYPE_REDIRECT_URI),
                                AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY,
                                List.of(LOCATION_TYPE_REDIRECT_URI)));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirects.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(Uri.parse(DEFAULT_DESTINATION), result.getAppDestinations().get(0));
        assertEquals(filterData, result.getFilterDataString());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(456789L),
                result.getExpiryTime());
        assertEquals(new JSONObject(aggregateSource).toString(), result.getAggregateSource());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
        verify(mUrlConnection).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchWebSources_invalidAppDestination_failsDropsSource() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION,
                        null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        String filterData = "{\"product\":[\"1234\",\"2345\"]," + "\"ctid\":[\"id\"]}";
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"https://a.text\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"web_destination\": "
                                                + "\""
                                                + WEB_DESTINATION
                                                + "\""
                                                + ",\n"
                                                + "  \"filter_data\": "
                                                + filterData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchWebSources_withDebugJoinKey_getsParsed() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\",\n"
                                                + "  \"debug_join_key\": \""
                                                + DEBUG_JOIN_KEY
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirects.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(Uri.parse(DEFAULT_DESTINATION), result.getAppDestinations().get(0));
        assertEquals(EVENT_ID_1, result.getEventId());
        long expiry =
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        assertEquals(expiry, result.getExpiryTime());
        assertNull(result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
        assertEquals(DEBUG_JOIN_KEY, result.getDebugJoinKey());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_withDebugJoinKeyEnrollmentNotAllowListed_joinKeyDropped()
            throws IOException {
        // Setup
        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist()).thenReturn("");
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\",\n"
                                                + "  \"debug_join_key\": \""
                                                + DEBUG_JOIN_KEY
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirects.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(Uri.parse(DEFAULT_DESTINATION), result.getAppDestinations().get(0));
        assertEquals(EVENT_ID_1, result.getEventId());
        long expiry =
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        assertEquals(expiry, result.getExpiryTime());
        assertNull(result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertNull(result.getDebugJoinKey());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_webDestinationDoNotMatch_failsDropsSource() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION,
                        WEB_DESTINATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        String filterData = "{\"product\":[\"1234\",\"2345\"]," + "\"ctid\":[\"id\"]}";
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"destination\":  "
                                                + "\""
                                                + OS_DESTINATION
                                                + "\""
                                                + ",\n"
                                                + "  \"web_destination\": "
                                                + "\""
                                                + WebUtil.validUrl(
                                                        "https://wrong-web-destination.test")
                                                + "\",\n"
                                                + "  \"filter_data\": "
                                                + filterData
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchWebSources_osAndWebDestinationMatch_recordSourceSuccess() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION,
                        WEB_DESTINATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"destination\": \""
                                                + OS_DESTINATION
                                                + "\",\n"
                                                + "\"web_destination\": \""
                                                + WEB_DESTINATION
                                                + "\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirects.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(OS_DESTINATION, result.getAppDestinations().get(0));
        assertNull(result.getFilterDataString());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(456789L),
                result.getExpiryTime());
        assertNull(result.getAggregateSource());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_extractsTopPrivateDomain() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION,
                        WEB_DESTINATION_WITH_SUBDOMAIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"destination\": \""
                                                + OS_DESTINATION
                                                + "\",\n"
                                                + "\"web_destination\": \""
                                                + WEB_DESTINATION_WITH_SUBDOMAIN
                                                + "\""
                                                + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirects.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(OS_DESTINATION, result.getAppDestinations().get(0));
        assertNull(result.getFilterDataString());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(456789L), result.getExpiryTime());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
        assertNull(result.getAggregateSource());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_extractsDestinationBaseUri() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION_WITH_PATH,
                        WEB_DESTINATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + OS_DESTINATION_WITH_PATH
                                                + "\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "\"web_destination\": \""
                                                + WEB_DESTINATION
                                                + "\""
                                                + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirects.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(OS_DESTINATION, result.getAppDestinations().get(0));
        assertNull(result.getFilterDataString());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(456789L), result.getExpiryTime());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
        assertNull(result.getAggregateSource());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_missingDestinations_dropsSource() throws Exception {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION,
                        WEB_DESTINATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.EntityStatus.PARSING_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_withDestinationUriNotHavingScheme_attachesAppScheme()
            throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        Uri.parse(DEFAULT_DESTINATION_WITHOUT_SCHEME),
                        null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirects.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(Uri.parse(DEFAULT_DESTINATION), result.getAppDestinations().get(0));
        assertNull(result.getFilterDataString());
        assertEquals(EVENT_ID_1, result.getEventId());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
        assertNull(result.getAggregateSource());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_withDestinationUriHavingHttpsScheme_dropsSource()
            throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        Uri.parse(DEFAULT_DESTINATION_WITHOUT_SCHEME),
                        null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                // Invalid (https) URI for app destination
                                                + WEB_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void basicSourceRequest_headersMoreThanMaxResponseSize_emitsMetricsWithAdTechDomain()
            throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\"\n"
                                                + "}\n")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        asyncFetchStatus.setRegistrationDelay(0L);
        AsyncRegistration asyncRegistration = appSourceRegistrationRequest(request);
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(asyncRegistration, asyncFetchStatus, asyncRedirects);

        assertTrue(fetch.isPresent());
        FetcherUtil.emitHeaderMetrics(5L, mLogger, asyncRegistration, asyncFetchStatus);
        verify(mLogger)
                .logMeasurementRegistrationsResponseSize(
                        eq(
                                new MeasurementRegistrationResponseStats.Builder(
                                                AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE,
                                                115,
                                                EVENT_SOURCE_TYPE,
                                                APP_REGISTRATION_SURFACE_TYPE,
                                                SUCCESS_STATUS,
                                                UNKNOWN_REGISTRATION_FAILURE_TYPE,
                                                0,
                                                ANDROID_APP_SCHEME_URI_PREFIX
                                                        + sContext.getPackageName(),
                                                0,
                                                false)
                                        .setAdTechDomain(WebUtil.validUrl("https://foo.test"))
                                        .build()));
    }

    @Test
    public void fetchSource_withDebugJoinKey_getsParsed() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \""
                                                + DEFAULT_PRIORITY
                                                + "\",\n"
                                                + "  \"expiry\": \""
                                                + DEFAULT_EXPIRY
                                                + "\",\n"
                                                + "  \"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\","
                                                + "\"debug_join_key\": "
                                                + DEBUG_JOIN_KEY
                                                + "}\n")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_PRIORITY, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(DEFAULT_EXPIRY_ROUNDED),
                result.getExpiryTime());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(DEBUG_KEY, result.getDebugKey());
        assertEquals(DEBUG_JOIN_KEY, result.getDebugJoinKey());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
    }

    @Test
    public void fetchSource_withDebugJoinKeyEnrollmentNotAllowListed_joinKeyDropped()
            throws Exception {
        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist()).thenReturn("");
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \""
                                                + DEFAULT_PRIORITY
                                                + "\",\n"
                                                + "  \"expiry\": \""
                                                + DEFAULT_EXPIRY
                                                + "\",\n"
                                                + "  \"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\","
                                                + "\"debug_join_key\": "
                                                + DEBUG_JOIN_KEY
                                                + "}\n")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_PRIORITY, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(DEFAULT_EXPIRY_ROUNDED),
                result.getExpiryTime());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(DEBUG_KEY, result.getDebugKey());
        assertEquals(DEFAULT_REGISTRATION, result.getRegistrationOrigin().toString());
        assertNull(result.getDebugJoinKey());
    }

    @Test
    public void fetchSource_setsRegistrationOriginWithoutPath_forRegistrationURIWithPath()
            throws Exception {
        String uri = "https://test1.example.com/path1";
        RegistrationRequest request = buildRequest(uri);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(uri));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\"\n"
                                                + "}\n")));
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appSourceRegistrationRequest(request);
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(asyncRegistration, asyncFetchStatus, new AsyncRedirects());
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals("https://test1.example.com", result.getRegistrationOrigin().toString());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchSource_setsRegistrationOriginWithPort_forRegistrationURIWithPort()
            throws Exception {
        String uri = "https://test1.example.com:8081";
        RegistrationRequest request = buildRequest(uri);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(uri));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\"\n"
                                                + "}\n")));
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appSourceRegistrationRequest(request);
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(asyncRegistration, asyncFetchStatus, new AsyncRedirects());
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals("https://test1.example.com:8081", result.getRegistrationOrigin().toString());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchSource_appRegistrationWithAdId_encodedAdIdAddedToSource() throws Exception {
        RegistrationRequest request = buildRequestWithAdId(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \""
                                                + DEFAULT_PRIORITY
                                                + "\",\n"
                                                + "  \"expiry\": \""
                                                + DEFAULT_EXPIRY
                                                + "\",\n"
                                                + "  \"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\""
                                                + "}\n")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequestWithAdId(request),
                        asyncFetchStatus,
                        asyncRedirects);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_PRIORITY, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(DEFAULT_EXPIRY_ROUNDED),
                result.getExpiryTime());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(DEBUG_KEY, result.getDebugKey());
        assertEquals(PLATFORM_AD_ID_VALUE, result.getPlatformAdId());
    }

    @Test
    public void fetchWebSource_withDebugAdIdValue_getsParsed() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\",\n"
                                                + "  \"debug_ad_id\": \""
                                                + DEBUG_AD_ID_VALUE
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirects.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(Uri.parse(DEFAULT_DESTINATION), result.getAppDestinations().get(0));
        assertEquals(EVENT_ID_1, result.getEventId());
        long expiry =
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        assertEquals(expiry, result.getExpiryTime());
        assertNull(result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertEquals(DEBUG_AD_ID_VALUE, result.getDebugAdId());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSource_withDebugAdIdValue_enrollmentBlockListed_doesNotGetParsed()
            throws IOException {
        // Setup
        when(mFlags.getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist())
                .thenReturn(ENROLLMENT_ID);
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\",\n"
                                                + "  \"debug_ad_id\": \""
                                                + DEBUG_AD_ID_VALUE
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirects.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(Uri.parse(DEFAULT_DESTINATION), result.getAppDestinations().get(0));
        assertEquals(EVENT_ID_1, result.getEventId());
        long expiry =
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        assertEquals(expiry, result.getExpiryTime());
        assertNull(result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertNull(result.getDebugAdId());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSource_withDebugAdIdValue_blockListMatchesAll_doesNotGetParsed()
            throws IOException {
        // Setup
        when(mFlags.getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist()).thenReturn("*");
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\",\n"
                                                + "  \"debug_ad_id\": \""
                                                + DEBUG_AD_ID_VALUE
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirects.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(Uri.parse(DEFAULT_DESTINATION), result.getAppDestinations().get(0));
        assertEquals(EVENT_ID_1, result.getEventId());
        long expiry =
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        assertEquals(expiry, result.getExpiryTime());
        assertNull(result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertNull(result.getDebugAdId());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchSource_setsSiteEnrollmentId_whenDisableEnrollmentFlagIsTrue()
            throws Exception {
        String uri = "https://test1.example.com:8081";
        RegistrationRequest request = buildRequest(uri);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(uri));
        doReturn(true).when(mFlags).isDisableMeasurementEnrollmentCheck();
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\"\n"
                                                + "}\n")));
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appSourceRegistrationRequest(request);
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(asyncRegistration, asyncFetchStatus, new AsyncRedirects());
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals("https://test1.example.com:8081", result.getRegistrationOrigin().toString());
        assertEquals("https://example.com", result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchSource_setsSiteEnrollmentId_whenDisableEnrollmentFlagIsTrueForIP()
            throws Exception {
        String uri = "https://127.0.0.1:8081";
        RegistrationRequest request = buildRequest(uri);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(uri));
        doReturn(true).when(mFlags).isDisableMeasurementEnrollmentCheck();
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\"\n"
                                                + "}\n")));
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appSourceRegistrationRequest(request);
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(asyncRegistration, asyncFetchStatus, new AsyncRedirects());
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals("https://127.0.0.1:8081", result.getRegistrationOrigin().toString());
        assertEquals("https://127.0.0.1", result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchSource_setsSiteEnrollmentId_whenDisableEnrollmentFlagIsTrueForLocalhost()
            throws Exception {
        String uri = "https://localhost:8081";
        RegistrationRequest request = buildRequest(uri);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(uri));
        doReturn(true).when(mFlags).isDisableMeasurementEnrollmentCheck();
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\"\n"
                                                + "}\n")));
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appSourceRegistrationRequest(request);
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(asyncRegistration, asyncFetchStatus, new AsyncRedirects());
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals("https://localhost:8081", result.getRegistrationOrigin().toString());
        assertEquals("https://localhost", result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchSource_withFeatureDisabledCoarseDestinationProvided_valueIgnored()
            throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(false).when(mFlags).getMeasurementEnableCoarseEventReportDestinations();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"432000\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"install_attribution_window\": \"272800\",\n"
                                                + "  \"coarse_event_report_destinations\": "
                                                + "\"true\",\n"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertFalse(result.hasCoarseEventReportDestinations());
    }

    @Test
    public void fetchSource_withFeatureEnabledCoarseDestinationProvided_valueParsed()
            throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableCoarseEventReportDestinations();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"432000\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"install_attribution_window\": \"272800\",\n"
                                                + "  \"coarse_event_report_destinations\": "
                                                + "\"true\",\n"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertTrue(result.hasCoarseEventReportDestinations());
    }

    @Test
    public void fetchSource_withFeatureDisabledSharedDebugKeyProvided_valueIgnored()
            throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(false).when(mFlags).getMeasurementEnableSharedSourceDebugKey();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"432000\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"install_attribution_window\": \"272800\",\n"
                                                + "  \"shared_debug_key\": \""
                                                + DEBUG_KEY
                                                + "\",\n"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getSharedDebugKey());
    }

    @Test
    public void fetchSource_withFeatureEnabledSharedDebugKeyProvided_valueParsed()
            throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableSharedSourceDebugKey();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"432000\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"install_attribution_window\": \"272800\",\n"
                                                + "  \"shared_debug_key\": \""
                                                + DEBUG_KEY
                                                + "\",\n"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(DEBUG_KEY, result.getSharedDebugKey());
    }

    @Test
    public void fetchSource_withFeatureDisabledSharedFilterDataKeysProvided_valueIgnored()
            throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(false).when(mFlags).getMeasurementEnableSharedFilterDataKeysXNA();

        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"432000\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"install_attribution_window\": \"272800\",\n"
                                                + "  \"shared_filter_data_keys\": "
                                                + SHARED_FILTER_DATA_KEYS
                                                + ",\n"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getSharedFilterDataKeys());
    }

    @Test
    public void fetchSource_withFeatureEnabledSharedFilterDataKeysProvided_valueParsed()
            throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableSharedFilterDataKeysXNA();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"432000\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"install_attribution_window\": \"272800\",\n"
                                                + "  \"shared_filter_data_keys\": "
                                                + SHARED_FILTER_DATA_KEYS
                                                + ",\n"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\"\n"
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(SHARED_FILTER_DATA_KEYS, result.getSharedFilterDataKeys());
    }

    @Test
    public void fetchSource_triggerDataMatching_success() throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "\"destination\": \"android-app://com.myapps\","
                                                + "\"priority\": \"123\","
                                                + "\"expiry\": \"432000\","
                                                + "\"source_event_id\": \"987654321\","
                                                + "\"install_attribution_window\": \"272800\","
                                                + "\"trigger_data_matching\": \"exact\","
                                                + "\"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(Source.TriggerDataMatching.EXACT, result.getTriggerDataMatching());
    }

    @Test
    public void fetchSource_triggerDataMatchingInvalid_noSourceGenerated() throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "\"destination\": \"android-app://com.myapps\","
                                                + "\"priority\": \"123\","
                                                + "\"expiry\": \"432000\","
                                                + "\"source_event_id\": \"987654321\","
                                                + "\"install_attribution_window\": \"272800\","
                                                + "\"trigger_data_matching\": \"INVALID\","
                                                + "\"post_install_exclusivity_window\": \"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertTrue(fetch.isEmpty());
    }

    @Test
    public void fetchSource_flexibleEventReportApi_valid() throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [0, 1, 2],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(20))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}], \n";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getTriggerSpecsString());
        assertNotNull(result.getTriggerSpecs());
        assertEquals(3, result.getTriggerSpecs().getMaxReports());
        assertEquals(
                0L,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getTriggerData()
                        .get(0)
                        .getValue()
                        .longValue());
        assertEquals(
                3, result.getTriggerSpecs().getTriggerSpecs()[0].getTriggerData().size());
        assertEquals(1, result.getTriggerSpecs().getTriggerSpecs().length);
        List<Long> expectedWindows = List.of(
                TimeUnit.DAYS.toMillis(2),
                TimeUnit.DAYS.toMillis(7),
                TimeUnit.DAYS.toMillis(20));
        assertEquals(
                expectedWindows,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getEventReportWindowsEnd());
    }

    @Test
    public void fetchSource_flexEventSourceTypeNavigationNoWindows_defaultsToNavigationWindows()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [0, 1, 2],"
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}], \n";
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, getInputEvent());
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getTriggerSpecsString());
        assertNotNull(result.getTriggerSpecs());
        assertEquals(3, result.getTriggerSpecs().getMaxReports());
        assertEquals(
                0L,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getTriggerData()
                        .get(0)
                        .getValue()
                        .longValue());
        assertEquals(
                3, result.getTriggerSpecs().getTriggerSpecs()[0].getTriggerData().size());
        assertEquals(1, result.getTriggerSpecs().getTriggerSpecs().length);
        List<Long> expectedWindows = List.of(
                TimeUnit.DAYS.toMillis(2),
                TimeUnit.DAYS.toMillis(7),
                TimeUnit.DAYS.toMillis(30));
        assertEquals(
                expectedWindows,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getEventReportWindowsEnd());
    }

    @Test
    public void fetchSource_flexEventSourceTypeNavigationNoWindows_expiryBoundsWindows()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [0, 1, 2],"
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}], \n";
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, getInputEvent());
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"" + TimeUnit.DAYS.toSeconds(3)
                                                        + "\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getTriggerSpecsString());
        assertNotNull(result.getTriggerSpecs());
        assertEquals(3, result.getTriggerSpecs().getMaxReports());
        assertEquals(
                0L,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getTriggerData()
                        .get(0)
                        .getValue()
                        .longValue());
        assertEquals(
                3, result.getTriggerSpecs().getTriggerSpecs()[0].getTriggerData().size());
        assertEquals(1, result.getTriggerSpecs().getTriggerSpecs().length);
        List<Long> expectedWindows = List.of(
                TimeUnit.DAYS.toMillis(2),
                TimeUnit.DAYS.toMillis(3));
        assertEquals(
                expectedWindows,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getEventReportWindowsEnd());
    }

    @Test
    public void fetchSource_flexEventSourceTypeNavigationNoWindows_eventReportWindowBoundsWindows()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [0, 1, 2],"
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}], \n";
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, getInputEvent());
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"" + TimeUnit.DAYS.toSeconds(5)
                                                        + "\","
                                                + "  \"event_report_window\": \""
                                                        + TimeUnit.DAYS.toSeconds(4) + "\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getTriggerSpecsString());
        assertNotNull(result.getTriggerSpecs());
        assertEquals(3, result.getTriggerSpecs().getMaxReports());
        assertEquals(
                0L,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getTriggerData()
                        .get(0)
                        .getValue()
                        .longValue());
        assertEquals(
                3, result.getTriggerSpecs().getTriggerSpecs()[0].getTriggerData().size());
        assertEquals(1, result.getTriggerSpecs().getTriggerSpecs().length);
        List<Long> expectedWindows = List.of(
                TimeUnit.DAYS.toMillis(2),
                TimeUnit.DAYS.toMillis(4));
        assertEquals(
                expectedWindows,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getEventReportWindowsEnd());
    }

    @Test
    public void fetchSource_flexEventSourceTypeEventNoWindows_defaultsToEventWindows()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [0, 1, 2],"
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}], \n";
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getTriggerSpecsString());
        assertNotNull(result.getTriggerSpecs());
        assertEquals(3, result.getTriggerSpecs().getMaxReports());
        assertEquals(
                0L,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getTriggerData()
                        .get(0)
                        .getValue()
                        .longValue());
        assertEquals(
                3, result.getTriggerSpecs().getTriggerSpecs()[0].getTriggerData().size());
        assertEquals(1, result.getTriggerSpecs().getTriggerSpecs().length);
        List<Long> expectedWindows = List.of(
                TimeUnit.DAYS.toMillis(30));
        assertEquals(
                expectedWindows,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getEventReportWindowsEnd());
    }

    @Test
    public void fetchSource_flexEventSourceTypeEventNoWindows_expiryBoundsWindows()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [0, 1, 2],"
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}], \n";
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"" + TimeUnit.DAYS.toSeconds(4)
                                                        + "\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getTriggerSpecsString());
        assertNotNull(result.getTriggerSpecs());
        assertEquals(3, result.getTriggerSpecs().getMaxReports());
        assertEquals(
                0L,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getTriggerData()
                        .get(0)
                        .getValue()
                        .longValue());
        assertEquals(
                3, result.getTriggerSpecs().getTriggerSpecs()[0].getTriggerData().size());
        assertEquals(1, result.getTriggerSpecs().getTriggerSpecs().length);
        List<Long> expectedWindows = List.of(
                TimeUnit.DAYS.toMillis(4));
        assertEquals(
                expectedWindows,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getEventReportWindowsEnd());
    }

    @Test
    public void fetchSource_flexEventSourceTypeEventNoWindows_eventReportWindowBoundsWindows()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [0, 1, 2],"
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}], \n";
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"" + TimeUnit.DAYS.toSeconds(4)
                                                        + "\","
                                                + "  \"event_report_window\": \""
                                                        + TimeUnit.DAYS.toSeconds(2) + "\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getTriggerSpecsString());
        assertNotNull(result.getTriggerSpecs());
        assertEquals(3, result.getTriggerSpecs().getMaxReports());
        assertEquals(
                0L,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getTriggerData()
                        .get(0)
                        .getValue()
                        .longValue());
        assertEquals(
                3, result.getTriggerSpecs().getTriggerSpecs()[0].getTriggerData().size());
        assertEquals(1, result.getTriggerSpecs().getTriggerSpecs().length);
        List<Long> expectedWindows = List.of(
                TimeUnit.DAYS.toMillis(2));
        assertEquals(
                expectedWindows,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getEventReportWindowsEnd());
    }

    @Test
    public void fetchSource_flexibleEventReportApiFromTopLevelTriggerData_valid() throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"event_report_windows\": { "
                                                + "    \"start_time\": 0,"
                                                + String.format(
                                                        "\"end_times\": [%s, %s, %s]}, ",
                                                        TimeUnit.DAYS.toSeconds(2),
                                                        TimeUnit.DAYS.toSeconds(7),
                                                        TimeUnit.DAYS.toSeconds(20))
                                                + "  \"trigger_data\": [0, 1, 2],"
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getTriggerSpecsString());
        assertNotNull(result.getTriggerSpecs());
        assertEquals(3, result.getTriggerSpecs().getMaxReports());
        assertEquals(
                0L,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getTriggerData()
                        .get(0)
                        .getValue()
                        .longValue());
        assertEquals(
                3, result.getTriggerSpecs().getTriggerSpecs()[0].getTriggerData().size());
        assertEquals(1, result.getTriggerSpecs().getTriggerSpecs().length);
        assertEquals(
                3,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getEventReportWindowsEnd()
                        .size());
    }

    @Test
    public void fetchSource_flexibleEventReportApiInvalidTopLevelTriggerData_parsingError()
            throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"event_report_windows\": { "
                                                + "    \"start_time\": 0,"
                                                + String.format(
                                                        "\"end_times\": [%s, %s, %s]}, ",
                                                        TimeUnit.DAYS.toSeconds(2),
                                                        TimeUnit.DAYS.toSeconds(7),
                                                        TimeUnit.DAYS.toSeconds(20))
                                                + "  \"trigger_data\": {},"
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.PARSING_ERROR, asyncFetchStatus.getEntityStatus());
        assertTrue(fetch.isEmpty());
    }

    @Test
    public void fetchSource_flexibleEventFromTopLevelEmptyTriggerData_returnsEmptyTriggerSpecs()
            throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"event_report_windows\": { "
                                                + "    \"start_time\": 0,"
                                                + String.format(
                                                        "\"end_times\": [%s, %s, %s]}, ",
                                                        TimeUnit.DAYS.toSeconds(2),
                                                        TimeUnit.DAYS.toSeconds(7),
                                                        TimeUnit.DAYS.toSeconds(20))
                                                + "  \"trigger_data\": [],"
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getTriggerSpecsString());
        assertNotNull(result.getTriggerSpecs());
        assertEquals(3, result.getTriggerSpecs().getMaxReports());
        assertEquals(0, result.getTriggerSpecs().getTriggerSpecs().length);
    }

    @Test
    public void fetchSource_flexEventReportApiEmptyTriggerDataWithinTriggerSpec_noSourceGenerated()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(20))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}], \n";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertTrue(fetch.isEmpty());
    }

    @Test
    public void fetchSource_flexibleEventReportApi_setsDefaults() throws Exception {
        String triggerSpecsString =
                "[{"
                        + "\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                                + "\"start_time\": 0,"
                                + "\"end_times\": ["
                                        + TimeUnit.DAYS.toSeconds(2) + ","
                                        + TimeUnit.DAYS.toSeconds(7) + ","
                                        + TimeUnit.DAYS.toSeconds(20) + "]}"
                        + "}],";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getTriggerSpecsString());
        TriggerSpecs triggerSpecs = result.getTriggerSpecs();
        assertEquals(3, triggerSpecs.getMaxReports());
        UnsignedLong triggerData = new UnsignedLong(2L);
        // Default operator
        assertEquals(
                TriggerSpec.SummaryOperatorType.COUNT,
                triggerSpecs.getSummaryOperatorType(triggerData));
        // Default summary buckets
        List<Long> expectedSummaryBuckets = List.of(1L, 2L, 3L);
        List<Long> actualSummaryBuckets = triggerSpecs.getSummaryBucketsForTriggerData(triggerData);
        assertEquals(expectedSummaryBuckets, actualSummaryBuckets);
        assertEquals(triggerSpecs.getMaxReports(), actualSummaryBuckets.size());
        assertEquals(
                1L,
                triggerSpecs
                        .getTriggerSpecs()[0]
                        .getTriggerData()
                        .get(0)
                        .getValue()
                        .longValue());
        assertEquals(3, triggerSpecs.getTriggerSpecs()[0].getTriggerData().size());
        assertEquals(1, triggerSpecs.getTriggerSpecs().length);
        assertEquals(
                3,
                triggerSpecs
                        .getTriggerSpecs()[0]
                        .getEventReportWindowsEnd()
                        .size());
    }

    @Test
    public void fetchSource_flexibleEventReportApi_windowsOutOfBounds_clampsFirstAndLast()
            throws Exception {
        long expiry = 2592000L;
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.MINUTES.toSeconds(38),
                                TimeUnit.DAYS.toSeconds(7),
                                expiry + TimeUnit.DAYS.toSeconds(10))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}], \n";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\":\"" + String.valueOf(expiry) + "\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getTriggerSpecsString());
        assertNotNull(result.getTriggerSpecs());
        assertEquals(
                List.of(
                        Long.valueOf(TimeUnit.SECONDS.toMillis(TimeUnit.HOURS.toSeconds(1))),
                        Long.valueOf(TimeUnit.SECONDS.toMillis(TimeUnit.DAYS.toSeconds(7))),
                        Long.valueOf(TimeUnit.SECONDS.toMillis(expiry))),
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getEventReportWindowsEnd());
    }

    @Test
    public void fetchSource_flexEventReportApiMoreSummaryBucketsThanReports_noSourceGenerated()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(20))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4]}], \n";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertTrue(fetch.isEmpty());
    }

    @Test
    public void fetchSource_flexibleEventReportApi_singleWindowEndTooLow_clampsUp()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s]}, ",
                                TimeUnit.MINUTES.toSeconds(38))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}], \n";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\":\"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getTriggerSpecsString());
        assertNotNull(result.getTriggerSpecs());
        assertEquals(
                List.of(
                        Long.valueOf(TimeUnit.SECONDS.toMillis(TimeUnit.HOURS.toSeconds(1)))),
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getEventReportWindowsEnd());
    }

    @Test
    public void fetchWebSource_flexibleEventReportApi_valid() throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}], \n";
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "\"web_destination\": ["
                                                + "\""
                                                + WEB_DESTINATION
                                                + "\","
                                                + "\""
                                                + WEB_DESTINATION_2
                                                + "\"],"
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getTriggerSpecsString());
        assertNotNull(result.getTriggerSpecs());
        assertEquals(3, result.getTriggerSpecs().getMaxReports());
        assertEquals(
                1L,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getTriggerData()
                        .get(0)
                        .getValue()
                        .longValue());
        assertEquals(
                3, result.getTriggerSpecs().getTriggerSpecs()[0].getTriggerData().size());
        assertEquals(1, result.getTriggerSpecs().getTriggerSpecs().length);
        assertEquals(
                3,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getEventReportWindowsEnd()
                        .size());
    }

    @Test
    public void fetchSource_flexibleEventReportApiArithmeticException_noSourceGenerated()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": ["
                        + IntStream.range(1, 33)
                                .boxed()
                                .map(String::valueOf)
                                .collect(Collectors.joining(","))
                        + "],"
                        + "\"event_report_windows\": { "
                                + "\"start_time\": 0,"
                                + "\"end_times\": ["
                                        + IntStream.range(1, 6)
                                                .mapToLong(TimeUnit.DAYS::toSeconds)
                                                .boxed()
                                                .map(String::valueOf)
                                                .collect(Collectors.joining(","))
                                + "]"
                        + "},"
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": ["
                                + IntStream.range(1, 21)
                                        .boxed()
                                        .map(String::valueOf)
                                        .collect(Collectors.joining(","))
                        + "]}]";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString + ","
                                                + "  \"max_event_level_reports\": 20,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertTrue(fetch.isEmpty());
    }

    @Test
    public void fetchSource_flexibleEventReportApiTriggerDataTooHigh_noSourceGenerated()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 4294967296],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(20))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}], \n";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"trigger_data_matching\": \"exact\","
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertTrue(fetch.isEmpty());
    }

    @Test
    public void fetchSource_flexibleEventReportApiTriggerDataNegative_noSourceGenerated()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [-3, -2, -1],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(20))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}], \n";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"trigger_data_matching\": \"exact\","
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertTrue(fetch.isEmpty());
    }

    @Test
    public void fetchSource_flexibleEventReportApiSummaryBucketTooHigh_noSourceGenerated()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [0, 1, 2],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(20))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 4294967296]}], \n";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"trigger_data_matching\": \"exact\","
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertTrue(fetch.isEmpty());
    }

    @Test
    public void fetchSource_flexibleEventReportApiSummaryBucketNegative_noSourceGenerated()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [0, 1, 2],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(20))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [-1, 2, 3]}], \n";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"trigger_data_matching\": \"exact\","
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertTrue(fetch.isEmpty());
    }

    @Test
    public void fetchSource_flexibleEventReportApiEmptySummaryBuckets_noSourceGenerated()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [0, 1, 2],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(20))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": []}], \n";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertTrue(fetch.isEmpty());
    }

    @Test
    public void fetchSource_flexibleEventReportApiSummaryOperatorInvalid_noSourceGenerated()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [0, 1, 2],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(20))
                        + "\"summary_window_operator\": \"invalid\", "
                        + "\"summary_buckets\": [1, 2, 3]}], \n";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertTrue(fetch.isEmpty());
    }

    @Test
    public void fetchSource_flexibleEventReportApiModulusTriggerDataMatchingNonContiguous_fail()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [0, 1, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(20))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}], \n";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_flexibleEventReportApiExactTriggerDataMatchingNonContiguous_pass()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [0, 1, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(20))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}], \n";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"trigger_data_matching\": \"exact\","
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getTriggerSpecsString());
        assertNotNull(result.getTriggerSpecs());
        assertEquals(3, result.getTriggerSpecs().getMaxReports());
        assertEquals(
                0L,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getTriggerData()
                        .get(0)
                        .getValue()
                        .longValue());
        assertEquals(
                3, result.getTriggerSpecs().getTriggerSpecs()[0].getTriggerData().size());
        assertEquals(1, result.getTriggerSpecs().getTriggerSpecs().length);
        assertEquals(
                3,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getEventReportWindowsEnd()
                        .size());
    }

    @Test
    public void fetchSource_flexEventReportApiModulusTriggerDataMatchingDoesNotStartAtZero_fail()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(20))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4]}], \n";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_flexEventReportApiExactTriggerDataMatchingDoesNotStartAtZero_pass()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(20))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}], \n";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"trigger_data_matching\": \"exact\","
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getTriggerSpecsString());
        assertNotNull(result.getTriggerSpecs());
        assertEquals(3, result.getTriggerSpecs().getMaxReports());
        assertEquals(
                1L,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getTriggerData()
                        .get(0)
                        .getValue()
                        .longValue());
        assertEquals(
                3, result.getTriggerSpecs().getTriggerSpecs()[0].getTriggerData().size());
        assertEquals(1, result.getTriggerSpecs().getTriggerSpecs().length);
        assertEquals(
                3,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getEventReportWindowsEnd()
                        .size());
    }

    @Test
    public void fetchSource_flexibleEventReportApiNonNumber_noSourceGenerated() throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [0, 1, \"2\"],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(20))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}], \n";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementEnableTriggerDataMatching();
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertTrue(fetch.isEmpty());
    }

    @Test
    public void fetchWebSource_flexibleEventReportApiNonNumber_noSourceGenerated()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, \"a\"],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4]}], \n";

        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"432000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertTrue(fetch.isEmpty());
    }

    @Test
    public void fetchSource_flexibleEventReportApiNonIncremental_noSourceGenerated()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 5, 4]}], \n";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"432000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertTrue(fetch.isEmpty());
    }

    @Test
    public void fetchWebSource_flexibleEventReportApiNonIncremental_noSourceGenerated()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 5, 4]}], \n";
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"432000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertTrue(fetch.isEmpty());
    }

    @Test
    public void fetchSource_flexibleEventReportApiDuplicateTriggerData_noSourceGenerated()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1,2,3,4]}, "
                        + "{\"trigger_data\": [3,4,5],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1]}"
                        + "],";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"432000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertTrue(fetch.isEmpty());
    }

    @Test
    public void fetchWebSource_flexibleEventReportApiDuplicateTriggerData_noSourceGenerated()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1,2,3,4]}, "
                        + "{\"trigger_data\": [3,4,5],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1]}"
                        + "],";

        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"432000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertTrue(fetch.isEmpty());
    }

    @Test
    public void fetchSource_flexibleEventReportApiExceedMaxInfo_validInCurrentStep()
            throws Exception {
        // exceeding maximum information gain is not processed in this step so it should be valid
        // source
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3, 4, 5, 6, 7, 8],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(14),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4]}], \n";

        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "\"web_destination\": ["
                                                + "\""
                                                + WEB_DESTINATION
                                                + "\","
                                                + "\""
                                                + WEB_DESTINATION_2
                                                + "\"],"
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 4,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getTriggerSpecsString());
        assertNotNull(result.getTriggerSpecs());
        assertEquals(4, result.getTriggerSpecs().getMaxReports());
        assertEquals(
                1L,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getTriggerData()
                        .get(0)
                        .getValue()
                        .longValue());
        assertEquals(
                8, result.getTriggerSpecs().getTriggerSpecs()[0].getTriggerData().size());
        assertEquals(1, result.getTriggerSpecs().getTriggerSpecs().length);
        assertEquals(
                4,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getEventReportWindowsEnd()
                        .size());
    }

    @Test
    public void fetchWebSource_flexibleEventReportApiExceedMaxInfo_validInCurrentStep()
            throws Exception {
        // exceeding maximum information gain is not processed in this step so it should be valid
        // source
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3, 4, 5, 6, 7, 8],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(14),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4]}], \n";
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "\"web_destination\": ["
                                                + "\""
                                                + WEB_DESTINATION
                                                + "\","
                                                + "\""
                                                + WEB_DESTINATION_2
                                                + "\"],"
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 4,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}\n")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getTriggerSpecsString());
        assertNotNull(result.getTriggerSpecs());
        assertEquals(4, result.getTriggerSpecs().getMaxReports());
        assertEquals(
                1L,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getTriggerData()
                        .get(0)
                        .getValue()
                        .longValue());
        assertEquals(
                8, result.getTriggerSpecs().getTriggerSpecs()[0].getTriggerData().size());
        assertEquals(1, result.getTriggerSpecs().getTriggerSpecs().length);
        assertEquals(
                4,
                result.getTriggerSpecs()
                        .getTriggerSpecs()[0]
                        .getEventReportWindowsEnd()
                        .size());
    }

    @Test
    public void fetchSource_flexibleEventReportApiFlagOff_valid() throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4]}], \n";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(false).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"432000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getTriggerSpecsString());
        assertNull(result.getTriggerSpecs());
    }

    @Test
    public void fetchWebSource_flexibleEventReportApiFlagOff_valid() throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4]}], \n";
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(false).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"432000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));

        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertNull(result.getTriggerSpecsString());
        assertNull(result.getTriggerSpecs());
    }

    @Test
    public void fetchSource_flexEventApi_duplicateTriggerData() throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4]}, "
                        + "{\"trigger_data\": [3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1]} "
                        + "]";

        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "\"web_destination\": ["
                                                + "\""
                                                + WEB_DESTINATION
                                                + "\","
                                                + "\""
                                                + WEB_DESTINATION_2
                                                + "\"],"
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + ","
                                                + "  \"max_event_level_reports\": 4,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_flexEventApi_totalCardinalityOverLimit() throws Exception {
        // Total Cardinality: 9
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4]}, "
                        + "{\"trigger_data\": [4, 5, 6, 7, 8, 9],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1]} "
                        + "]";

        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(8).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "\"web_destination\": ["
                                                + "\""
                                                + WEB_DESTINATION
                                                + "\","
                                                + "\""
                                                + WEB_DESTINATION_2
                                                + "\"],"
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + ","
                                                + "  \"max_event_level_reports\": 4,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_flexEventApi_eventLevelReportsOverLimit() throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4]}, "
                        + "{\"trigger_data\": [4],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1]} "
                        + "]";

        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "\"web_destination\": ["
                                                + "\""
                                                + WEB_DESTINATION
                                                + "\","
                                                + "\""
                                                + WEB_DESTINATION_2
                                                + "\"],"
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + ","
                                                + "  \"max_event_level_reports\": 21,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_flexLiteApi_eventLevelReportsOverLimit_rejectsSource()
            throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"max_event_level_reports\": 21"
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_flexLiteApi_eventLevelReportsNegative_rejectsSource() throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"max_event_level_reports\": -3"
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_flexLiteApi_eventLevelReportsNotNumeric_rejectsSource()
            throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"max_event_level_reports\": \"3\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_flexLiteApi_topLevelWindowsOverLimit() throws Exception {
        // More than 5 windows
        String eventReportWindows =
                "{"
                        + "\"start_time\": 1,"
                        + "\"end_times\": [3600, 8640, 25920, 36000, 86400, 259200]"
                        + "}";

        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"max_event_level_reports\": 4,"
                                                + "\"event_report_windows\":"
                                                + eventReportWindows
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_flexLiteApi_topLevelWindowsNotNumeric_fail() throws Exception {
        String eventReportWindows =
                "{"
                        + "\"start_time\": 1,"
                        + "\"end_times\": [\"3600\", \"8640\", \"25920\", \"36000\", \"86400\"]"
                        + "}";

        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"max_event_level_reports\": 4,"
                                                + "\"event_report_windows\":"
                                                + eventReportWindows
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_flexLiteApi_windowsOutOfBounds_clampsFirstAndLast()
            throws Exception {
        long expiry = 2592000L;
        // 1st window ends < 1 hour
        String eventReportWindows = "{\"start_time\":1,\"end_times\":"
                + "[3599,8400," + String.valueOf(expiry + 2500L) + "]}";

        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\":\"" + String.valueOf(expiry) + "\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"max_event_level_reports\": 4,"
                                                + "\"event_report_windows\":"
                                                + eventReportWindows
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        List<Pair<Long, Long>> windows =
                result.parseEventReportWindows(
                        result.getEventReportWindows());
        List<Pair<Long, Long>> expectedWindows =
                List.of(
                        Pair.create(
                                Long.valueOf(TimeUnit.SECONDS.toMillis(1L)),
                                Long.valueOf(TimeUnit.SECONDS.toMillis(3600L))),
                        Pair.create(
                                Long.valueOf(TimeUnit.SECONDS.toMillis(3600L)),
                                Long.valueOf(TimeUnit.SECONDS.toMillis(8400L))),
                        Pair.create(
                                Long.valueOf(TimeUnit.SECONDS.toMillis(8400L)),
                                Long.valueOf(TimeUnit.SECONDS.toMillis(expiry))));
        assertEquals(expectedWindows, windows);
    }

    @Test
    public void fetchSource_flexLiteApi_singleWindowEndTooLow_clampsUp() throws Exception {
        // 1st window ends < 1 hour
        String eventReportWindows = "{\"start_time\":1,\"end_times\":[3599]}";

        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\":\"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"max_event_level_reports\": 4,"
                                                + "\"event_report_windows\":"
                                                + eventReportWindows
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        List<Pair<Long, Long>> windows =
                result.parseEventReportWindows(
                        result.getEventReportWindows());
        List<Pair<Long, Long>> expectedWindows =
                List.of(
                        Pair.create(
                                Long.valueOf(TimeUnit.SECONDS.toMillis(1L)),
                                Long.valueOf(TimeUnit.SECONDS.toMillis(3600L))));
        assertEquals(expectedWindows, windows);
    }

    @Test
    public void fetchSource_flexLiteApi_eventReportWindowWithEventReportWindows_fail()
            throws Exception {
        // More than 5 windows
        String eventReportWindows = "{\"start_time\": 1,\"end_times\": [3600, 8640]}";

        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"max_event_level_reports\": 4,"
                                                + "\"event_report_window\": \"2592000\","
                                                + "\"event_report_windows\":"
                                                + eventReportWindows
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_flexLiteApi_nonIncreasingWindows() throws Exception {
        String eventReportWindows = "{\"end_times\": [25920, 8640, 36000]}";

        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"max_event_level_reports\": 4,"
                                                + "\"event_report_windows\":"
                                                + eventReportWindows
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_flexLiteApi_lastWindowBeyondExpiry() throws Exception {
        String eventReportWindows = "{\"end_times\": [25920, 8640, 864001]}";

        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"864000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"max_event_level_reports\": 4,"
                                                + "\"event_report_windows\":"
                                                + eventReportWindows
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_flexLiteApi_startBeyondFirstEnd() throws Exception {
        String eventReportWindows = "{\"start_time\": 8641,\"end_times\": [8640]}";

        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"864000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"max_event_level_reports\": 4,"
                                                + "\"event_report_windows\":"
                                                + eventReportWindows
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_missingSummaryBucketForValueSumOperator_singleTriggerData()
            throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1], \"summary_window_operator\": \"value_sum\"}]";

        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + ","
                                                + "  \"max_event_level_reports\": 5"
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_invalidSummaryOperator_singleTriggerData() throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1], \"summary_window_operator\": \"value_average\"}]";

        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + ","
                                                + "  \"max_event_level_reports\": 5"
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_exceedTriggerDataCardinality_singleTriggerData() throws Exception {
        String triggerSpecsString = "[{\"trigger_data\": [1, 2, 3, 4, 5, 6, 7, 8, 9]}]";

        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(8).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + ","
                                                + "  \"max_event_level_reports\": 5"
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_nonStrictlyIncreasingSummaryBuckets_singleTriggerData()
            throws Exception {
        String triggerSpecsString = "[{\"trigger_data\": [1], \"summary_buckets\": [6,5]}]";

        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + ","
                                                + "  \"max_event_level_reports\": 5"
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_flexEventTriggerDataNonNumeric_fail() throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [\"1\", \"2\", \"3\"],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(20))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}],";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_flexEventSummaryBucketsNonNumeric_fail() throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": 0,"
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(20))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [\"1\", \"2\", \"3\"]}],";
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(32).when(mFlags).getMeasurementFlexApiMaxTriggerDataCardinality();
        doReturn(20).when(mFlags).getMeasurementFlexApiMaxEventReports();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"install_attribution_window\": \"272800\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + "  \"max_event_level_reports\": 3,"
                                                + "  \"post_install_exclusivity_window\": "
                                                + "\"987654\""
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_nonStrictlyIncreasingReportWindow_singleTriggerData() throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1],"
                        + "\"event_report_windows\": {"
                        + "\"end_times\": ["
                        + TimeUnit.DAYS.toSeconds(10)
                        + ","
                        + TimeUnit.DAYS.toSeconds(5)
                        + "]"
                        + "}}]";

        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + ","
                                                + "  \"max_event_level_reports\": 5"
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_exceedReportWindowLimitation_singleTriggerData() throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1],"
                        + "\"event_report_windows\": {"
                        + "\"end_times\": ["
                        + IntStream.range(1, 7)
                                .mapToLong(TimeUnit.DAYS::toSeconds)
                                .boxed()
                                .map(String::valueOf)
                                .collect(Collectors.joining(","))
                        + "]}"
                        + "}]";

        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(5).when(mFlags).getMeasurementFlexApiMaxEventReportWindows();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + ","
                                                + "  \"max_event_level_reports\": 5"
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_negativeStartTime_singleTriggerData_rejectsSource() throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1], "
                        + "\"event_report_windows\": {"
                        + "\"start_time\": -1,"
                        + "\"end_times\": ["
                        + TimeUnit.DAYS.toSeconds(10)
                        + "]}"
                        + "}]";

        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + ","
                                                + "  \"max_event_level_reports\": 5"
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_startTimeNotNumeric_singleTriggerData_rejectsSource() throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1], "
                        + "\"event_report_windows\": {"
                        + "\"start_time\": \"1\","
                        + "\"end_times\": ["
                        + TimeUnit.DAYS.toSeconds(10)
                        + "]}"
                        + "}]";

        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        doReturn(true).when(mFlags).getMeasurementFlexibleEventReportingApiEnabled();
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\","
                                                + "  \"priority\": \"123\","
                                                + "  \"expiry\": \"2592000\","
                                                + "  \"source_event_id\": \"987654321\","
                                                + "  \"trigger_specs\": "
                                                + triggerSpecsString
                                                + ","
                                                + "  \"max_event_level_reports\": 5"
                                                + "}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertEquals(
                AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void fetchSource_postBody_success() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        when(mFlags.getFledgeMeasurementReportAndRegisterEventApiEnabled()).thenReturn(true);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getOutputStream()).thenReturn(outputStream);
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\"35\"}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequestWithPostBody(request, POST_BODY),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(POST_BODY, outputStream.toString());
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchSource_postBodyNull_success() throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        when(mFlags.getFledgeMeasurementReportAndRegisterEventApiEnabled()).thenReturn(true);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\"35\"}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequestWithPostBody(request, null),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchSource_postBodyEmpty_success() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        when(mFlags.getFledgeMeasurementReportAndRegisterEventApiEnabled()).thenReturn(true);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getOutputStream()).thenReturn(outputStream);
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\"35\"}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        String emptyPostBody = "";
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequestWithPostBody(request, emptyPostBody),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(emptyPostBody, outputStream.toString());
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchSource_requestBodyDisabled_success() throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        when(mFlags.getFledgeMeasurementReportAndRegisterEventApiEnabled()).thenReturn(false);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\"35\"}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequestWithPostBody(request, POST_BODY),
                        asyncFetchStatus,
                        asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchSource_dropSourceIfInstalled_success() throws Exception {
        when(mFlags.getMeasurementEnablePreinstallCheck()).thenReturn(true);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getOutputStream()).thenReturn(outputStream);
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\"35\","
                                                + "\"drop_source_if_installed\":true}")));
        AsyncRedirects asyncRedirects = new AsyncRedirects();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
        assertTrue(fetch.isPresent());
        verify(mFetcher, times(1)).openUrl(any());
        assertTrue(fetch.get().shouldDropSourceIfInstalled());
    }

    private RegistrationRequest buildRequest(String registrationUri) {
        return buildRequest(registrationUri, null);
    }

    private RegistrationRequest buildRequest(String registrationUri, InputEvent inputEvent) {
        RegistrationRequest.Builder builder =
                buildDefaultRegistrationRequestBuilder(registrationUri);
        return builder.setInputEvent(inputEvent).build();
    }

    private RegistrationRequest buildRequestWithAdId(String registrationUri) {
        RegistrationRequest.Builder builder =
                buildDefaultRegistrationRequestBuilder(registrationUri);
        return builder.setAdIdPermissionGranted(true).setAdIdValue(PLATFORM_AD_ID_VALUE).build();
    }

    public static AsyncRegistration appSourceRegistrationRequest(
            RegistrationRequest registrationRequest) {
        return createAsyncRegistration(
                UUID.randomUUID().toString(),
                registrationRequest.getRegistrationUri(),
                null,
                Uri.parse(DEFAULT_DESTINATION),
                Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
                null,
                Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
                registrationRequest.getRegistrationType() == RegistrationRequest.REGISTER_SOURCE
                        ? AsyncRegistration.RegistrationType.APP_SOURCE
                        : AsyncRegistration.RegistrationType.APP_TRIGGER,
                getSourceType(registrationRequest.getInputEvent()),
                System.currentTimeMillis(),
                0,
                UUID.randomUUID().toString(),
                false,
                false,
                null,
                null);
    }

    public static AsyncRegistration appSourceRegistrationRequestWithAdId(
            RegistrationRequest registrationRequest) {
        return createAsyncRegistration(
                UUID.randomUUID().toString(),
                registrationRequest.getRegistrationUri(),
                null,
                Uri.parse(DEFAULT_DESTINATION),
                Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
                null,
                Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
                registrationRequest.getRegistrationType() == RegistrationRequest.REGISTER_SOURCE
                        ? AsyncRegistration.RegistrationType.APP_SOURCE
                        : AsyncRegistration.RegistrationType.APP_TRIGGER,
                getSourceType(registrationRequest.getInputEvent()),
                System.currentTimeMillis(),
                0,
                UUID.randomUUID().toString(),
                false,
                true,
                PLATFORM_AD_ID_VALUE,
                null);
    }

    public static AsyncRegistration appSourceRegistrationRequestWithPostBody(
            RegistrationRequest registrationRequest, String postBody) {
        return createAsyncRegistration(
                UUID.randomUUID().toString(),
                registrationRequest.getRegistrationUri(),
                null,
                Uri.parse(DEFAULT_DESTINATION),
                Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
                null,
                Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
                registrationRequest.getRegistrationType() == RegistrationRequest.REGISTER_SOURCE
                        ? AsyncRegistration.RegistrationType.APP_SOURCE
                        : AsyncRegistration.RegistrationType.APP_TRIGGER,
                getSourceType(registrationRequest.getInputEvent()),
                System.currentTimeMillis(),
                0,
                UUID.randomUUID().toString(),
                false,
                true,
                null,
                postBody);
    }

    private static AsyncRegistration webSourceRegistrationRequest(
            WebSourceRegistrationRequest webSourceRegistrationRequest, boolean arDebugPermission) {
        return webSourceRegistrationRequest(
                webSourceRegistrationRequest, arDebugPermission, Source.SourceType.NAVIGATION);
    }

    private static AsyncRegistration webSourceRegistrationRequest(
            WebSourceRegistrationRequest webSourceRegistrationRequest,
            boolean arDebugPermission,
            Source.SourceType sourceType) {
        if (webSourceRegistrationRequest.getSourceParams().size() > 0) {
            WebSourceParams webSourceParams = webSourceRegistrationRequest.getSourceParams().get(0);
            return createAsyncRegistration(
                    UUID.randomUUID().toString(),
                    webSourceParams.getRegistrationUri(),
                    webSourceRegistrationRequest.getWebDestination(),
                    webSourceRegistrationRequest.getAppDestination(),
                    Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
                    null,
                    webSourceRegistrationRequest.getTopOriginUri(),
                    AsyncRegistration.RegistrationType.WEB_SOURCE,
                    sourceType,
                    System.currentTimeMillis(),
                    0,
                    UUID.randomUUID().toString(),
                    arDebugPermission,
                    false,
                    null,
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
            String registrationId,
            boolean debugKeyAllowed,
            boolean adIdPermission,
            String adIdValue,
            String postBody) {
        return new AsyncRegistration.Builder()
                .setId(iD)
                .setRegistrationUri(registrationUri)
                .setWebDestination(webDestination)
                .setOsDestination(osDestination)
                .setRegistrant(registrant)
                .setVerifiedDestination(verifiedDestination)
                .setTopOrigin(topOrigin)
                .setType(registrationType)
                .setSourceType(
                        registrationType == AsyncRegistration.RegistrationType.APP_SOURCE
                                        || registrationType
                                                == AsyncRegistration.RegistrationType.WEB_SOURCE
                                ? sourceType
                                : null)
                .setRequestTime(mRequestTime)
                .setRetryCount(mRetryCount)
                .setRegistrationId(registrationId)
                .setDebugKeyAllowed(debugKeyAllowed)
                .setAdIdPermission(adIdPermission)
                .setPlatformAdId(adIdValue)
                .setPostBody(postBody)
                .build();
    }

    private WebSourceRegistrationRequest buildWebSourceRegistrationRequest(
            List<WebSourceParams> sourceParamsList,
            String topOrigin,
            Uri appDestination,
            Uri webDestination) {
        WebSourceRegistrationRequest.Builder webSourceRegistrationRequestBuilder =
                new WebSourceRegistrationRequest.Builder(sourceParamsList, Uri.parse(topOrigin));
        if (appDestination != null) {
            webSourceRegistrationRequestBuilder.setAppDestination(appDestination);
        }
        if (webDestination != null) {
            webSourceRegistrationRequestBuilder.setWebDestination(webDestination);
        }
        return webSourceRegistrationRequestBuilder.build();
    }

    static Source.SourceType getSourceType(InputEvent inputEvent) {
        return inputEvent == null ? Source.SourceType.EVENT : Source.SourceType.NAVIGATION;
    }

    static Map<String, List<String>> getDefaultHeaders() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(
                "Attribution-Reporting-Register-Source",
                List.of(
                        "{\n"
                                + "\"destination\": \""
                                + DEFAULT_DESTINATION
                                + "\",\n"
                                + "\"source_event_id\": \""
                                + DEFAULT_EVENT_ID
                                + "\",\n"
                                + "\"priority\": \""
                                + DEFAULT_PRIORITY
                                + "\",\n"
                                + "\"expiry\": \""
                                + DEFAULT_EXPIRY
                                + "\""
                                + "}\n"));
        return headers;
    }

    private static void assertDefaultSourceRegistration(Source result) {
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(DEFAULT_PRIORITY, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(DEFAULT_EXPIRY_ROUNDED),
                result.getExpiryTime());
    }

    private RegistrationRequest.Builder buildDefaultRegistrationRequestBuilder(
            String registrationUri) {
        return new RegistrationRequest.Builder(
                RegistrationRequest.REGISTER_SOURCE,
                Uri.parse(registrationUri),
                sContext.getPackageName(),
                SDK_PACKAGE_NAME);
    }

    public static InputEvent getInputEvent() {
        return obtain(
                0 /*long downTime*/,
                0 /*long eventTime*/,
                ACTION_BUTTON_PRESS,
                1 /*int pointerCount*/,
                new PointerProperties[] {new PointerProperties()},
                new PointerCoords[] {new PointerCoords()},
                0 /*int metaState*/,
                0 /*int buttonState*/,
                1.0f /*float xPrecision*/,
                1.0f /*float yPrecision*/,
                0 /*int deviceId*/,
                0 /*int edgeFlags*/,
                InputDevice.SOURCE_TOUCH_NAVIGATION,
                0 /*int flags*/);
    }
}
