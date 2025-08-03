/*
 * Copyright (C) 2023 The Android Open Source Project
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
import static com.android.adservices.service.measurement.attribution.TriggerContentProvider.TRIGGER_URI;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_INVALID;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.RemoteException;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.WebUtil;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.data.measurement.ITransaction;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.data.measurement.SQLDatastoreManager;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.measurement.AsyncRegistrationFixture;
import com.android.adservices.service.measurement.Attribution;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.KeyValueData;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerFixture;
import com.android.adservices.service.measurement.TriggerSpec;
import com.android.adservices.service.measurement.TriggerSpecs;
import com.android.adservices.service.measurement.TriggerSpecsUtil;
import com.android.adservices.service.measurement.noising.SourceNoiseHandler;
import com.android.adservices.service.measurement.registration.AsyncRegistrationQueueRunner.ProcessingResult;
import com.android.adservices.service.measurement.reporting.DebugReportApi;
import com.android.adservices.service.measurement.reporting.EventReportWindowCalcDelegate;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.MeasurementRegistrationResponseStats;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.truth.Truth;

import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.net.ssl.HttpsURLConnection;

@SpyStatic(FlagsFactory.class)
public final class AsyncRegistrationQueueRunnerTest extends AdServicesExtendedMockitoTestCase {

    private static final boolean DEFAULT_AD_ID_PERMISSION = false;
    private static final String DEFAULT_ENROLLMENT_ID = "enrollment_id";
    private static final Uri DEFAULT_REGISTRANT = Uri.parse("android-app://com.registrant");
    private static final Uri DEFAULT_VERIFIED_DESTINATION = Uri.parse("android-app://com.example");
    private static final String DEFAULT_SOURCE_ID = UUID.randomUUID().toString();
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";
    private static final Uri APP_TOP_ORIGIN =
            Uri.parse("android-app://" + sContext.getPackageName());
    private static final Uri WEB_TOP_ORIGIN = WebUtil.validUri("https://example.test");
    private static final Uri REGISTRATION_URI = WebUtil.validUri("https://foo.test/bar?ad=134");
    private static final String LIST_TYPE_REDIRECT_URI_1 = WebUtil.validUrl("https://foo.test");
    private static final String LIST_TYPE_REDIRECT_URI_2 = WebUtil.validUrl("https://bar.test");
    private static final String LOCATION_TYPE_REDIRECT_URI = WebUtil.validUrl("https://baz.test");
    private static final String LOCATION_TYPE_REDIRECT_URI_2 = WebUtil.validUrl("https://qux.test");
    private static final String LOCATION_TYPE_REDIRECT_URI_3 =
            WebUtil.validUrl("https://quux.test");

    private static final Uri WEB_DESTINATION = WebUtil.validUri("https://web-destination.test");
    private static final Uri APP_DESTINATION = Uri.parse("android-app://com.app_destination");
    private static final Source SOURCE_1 =
            SourceFixture.getMinimalValidSourceBuilder()
                    .setEventId(new UnsignedLong(1L))
                    .setPublisher(APP_TOP_ORIGIN)
                    .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                    .setWebDestinations(List.of(WebUtil.validUri("https://web-destination1.test")))
                    .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                    .setRegistrant(Uri.parse("android-app://com.example"))
                    .setEventTime(new Random().nextLong())
                    .setExpiryTime(8640000010L)
                    .setPriority(100L)
                    .setSourceType(Source.SourceType.EVENT)
                    .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                    .setDebugKey(new UnsignedLong(47823478789L))
                    .build();
    private static final Uri DEFAULT_WEB_DESTINATION =
            WebUtil.validUri("https://def-web-destination.test");
    private static final Uri ALT_WEB_DESTINATION =
            WebUtil.validUri("https://alt-web-destination.test");
    private static final Uri ALT_APP_DESTINATION =
            Uri.parse("android-app://com.alt-app_destination");
    private static final String DEFAULT_REGISTRATION = WebUtil.validUrl("https://foo.test");
    private static final Uri DEFAULT_OS_DESTINATION =
            Uri.parse("android-app://com.def-os-destination");
    private static final WebSourceParams DEFAULT_REGISTRATION_PARAM_LIST =
            new WebSourceParams.Builder(Uri.parse(DEFAULT_REGISTRATION))
                    .setDebugKeyAllowed(true)
                    .build();

    private static final Trigger TRIGGER =
            TriggerFixture.getValidTriggerBuilder()
                    .setAttributionDestination(APP_DESTINATION)
                    .setDestinationType(EventSurfaceType.APP)
                    .build();

    private AsyncSourceFetcher mAsyncSourceFetcher;
    private AsyncTriggerFetcher mAsyncTriggerFetcher;
    private Source mMockedSource;

    @Mock private IMeasurementDao mMeasurementDao;
    @Mock private Trigger mMockedTrigger;
    @Mock private ITransaction mTransaction;
    @Mock private EnrollmentDao mEnrollmentDao;
    @Mock private ContentResolver mContentResolver;
    @Mock private ContentProviderClient mMockContentProviderClient;
    @Mock private DebugReportApi mDebugReportApi;
    @Mock private HttpsURLConnection mUrlConnection;
    @Mock private Flags mFlags;
    @Mock private AdServicesLogger mLogger;
    @Mock private AdServicesErrorLogger mErrorLogger;
    @Mock private SourceNoiseHandler mSourceNoiseHandler;
    @Mock private PackageManager mPackageManager;

    private static EnrollmentData getEnrollment(String enrollmentId) {
        return new EnrollmentData.Builder().setEnrollmentId(enrollmentId).build();
    }

    class FakeDatastoreManager extends DatastoreManager {

        FakeDatastoreManager() {
            super(mErrorLogger);
        }

        @Override
        public ITransaction createNewTransaction() {
            return mTransaction;
        }

        @Override
        public IMeasurementDao getMeasurementDao() {
            return mMeasurementDao;
        }

        @Override
        protected int getDataStoreVersion() {
            return 0;
        }
    }

    @After
    public void cleanup() {
        SQLiteDatabase db = DbTestUtil.getMeasurementDbHelperForTest().getWritableDatabase();
        emptyTables(db);
    }

    @Before
    public void before() throws Exception {
        extendedMockito.mockGetFlags(mFlags);

        mAsyncSourceFetcher = spy(new AsyncSourceFetcher(sContext));
        mAsyncTriggerFetcher = spy(new AsyncTriggerFetcher(sContext));
        mMockedSource = spy(SourceFixture.getValidSource());

        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(any()))
                .thenReturn(getEnrollment(DEFAULT_ENROLLMENT_ID));
        when(mContentResolver.acquireContentProviderClient(TRIGGER_URI))
                .thenReturn(mMockContentProviderClient);
        when(mMockContentProviderClient.insert(any(), any())).thenReturn(TRIGGER_URI);
        when(mFlags.getMeasurementMaxRegistrationRedirects()).thenReturn(20);
        when(mFlags.getMeasurementMaxRegistrationsPerJobInvocation()).thenReturn(1);
        when(mFlags.getMeasurementMaxRetriesPerRegistrationRequest()).thenReturn(5);
        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist()).thenReturn("");
        when(mFlags.getMeasurementMaxSourcesPerPublisher())
                .thenReturn(Flags.MEASUREMENT_MAX_SOURCES_PER_PUBLISHER);
        when(mFlags.getMeasurementMaxTriggersPerDestination())
                .thenReturn(Flags.MEASUREMENT_MAX_TRIGGERS_PER_DESTINATION);
        when(mFlags.getMeasurementMaxAttributionPerRateLimitWindow())
                .thenReturn(Flags.MEASUREMENT_MAX_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
        when(mFlags.getMeasurementMaxDistinctEnrollmentsInAttribution())
                .thenReturn(Flags.MEASUREMENT_MAX_DISTINCT_ENROLLMENTS_IN_ATTRIBUTION);
        when(mFlags.getMeasurementMaxDistinctDestinationsInActiveSource())
                .thenReturn(Flags.MEASUREMENT_MAX_DISTINCT_DESTINATIONS_IN_ACTIVE_SOURCE);
        when(mFlags.getMeasurementMaxReportingOriginsPerSourceReportingSitePerWindow())
                .thenReturn(Flags
                        .MEASUREMENT_MAX_REPORTING_ORIGINS_PER_SOURCE_REPORTING_SITE_PER_WINDOW);
        when(mFlags.getMeasurementMaxDistinctRepOrigPerPublXDestInSource())
                .thenReturn(Flags.MEASUREMENT_MAX_DISTINCT_REP_ORIG_PER_PUBLISHER_X_DEST_IN_SOURCE);
        when(mFlags.getAppConfigReturnsEnabledByDefault()).thenReturn(false);
        when(mFlags.getMeasurementEnableDestinationRateLimit())
                .thenReturn(Flags.MEASUREMENT_ENABLE_DESTINATION_RATE_LIMIT);
        when(mFlags.getMeasurementMaxDestinationsPerPublisherPerRateLimitWindow())
                .thenReturn(Flags
                        .MEASUREMENT_MAX_DESTINATIONS_PER_PUBLISHER_PER_RATE_LIMIT_WINDOW);
        when(mFlags.getMeasurementMaxDestPerPublisherXEnrollmentPerRateLimitWindow())
                .thenReturn(Flags
                        .MEASUREMENT_MAX_DEST_PER_PUBLISHER_X_ENROLLMENT_PER_RATE_LIMIT_WINDOW);
        when(mFlags.getMeasurementDestinationRateLimitWindow())
                .thenReturn(Flags.MEASUREMENT_DESTINATION_RATE_LIMIT_WINDOW);
        when(mFlags.getMeasurementFlexApiMaxInformationGainEvent())
                .thenReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT);
        when(mFlags.getMeasurementFlexApiMaxInformationGainNavigation())
                .thenReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION);
        when(mFlags.getMeasurementFlexApiMaxInformationGainDualDestinationEvent())
                .thenReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_EVENT);
        when(mFlags.getMeasurementFlexApiMaxInformationGainDualDestinationNavigation())
                .thenReturn(Flags
                        .MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_NAVIGATION);
        when(mFlags.getMeasurementVtcConfigurableMaxEventReportsCount())
                .thenReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT);
        when(mFlags.getMeasurementEventReportsVtcEarlyReportingWindows())
                .thenReturn(Flags.MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS);
        when(mFlags.getMeasurementEventReportsCtcEarlyReportingWindows())
                .thenReturn(Flags.MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS);
        when(mFlags.getMeasurementEnableUpdateTriggerHeaderLimit()).thenReturn(false);
        when(mFlags.getMaxResponseBasedRegistrationPayloadSizeBytes())
                .thenReturn(Flags.MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES);
        when(mMeasurementDao.insertSource(any())).thenReturn(DEFAULT_SOURCE_ID);
        when(mSpyContext.getPackageManager()).thenReturn(mPackageManager);
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appSource_success() throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();
        Map<String, List<String>> redirectHeaders =
                getRedirectHeaders(
                        AsyncRegistration.RedirectType.LIST,
                        List.of(
                                WebUtil.validUri("https://example.test/sF1").toString(),
                                WebUtil.validUri("https://example.test/sF2").toString()));
        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirects asyncRedirects = invocation.getArgument(2);
                    asyncRedirects.configure(redirectHeaders, mFlags, validAsyncRegistration);
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L),
                        1L,
                        List.of(WebUtil.validUri("https://example.test/sF")));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);
        KeyValueData redirectCount = getKeyValueDataRedirectCount();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, times(2)).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mDebugReportApi, times(1)).scheduleSourceNoisedDebugReport(any(Source.class), any());
        ArgumentCaptor<KeyValueData> redirectCountCaptor =
                ArgumentCaptor.forClass(KeyValueData.class);
        verify(mMeasurementDao, times(1)).insertOrUpdateKeyValueData(redirectCountCaptor.capture());
        assertEquals(3, redirectCountCaptor.getValue().getRegistrationRedirectCount());
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_emptyNoisedState_sendsDebugReport()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();
        Map<String, List<String>> redirectHeaders =
                getRedirectHeaders(
                        AsyncRegistration.RedirectType.LIST,
                        List.of(
                                WebUtil.validUri("https://example.test/sF1").toString(),
                                WebUtil.validUri("https://example.test/sF2").toString()));
        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirects asyncRedirects = invocation.getArgument(2);
                    asyncRedirects.configure(redirectHeaders, mFlags, validAsyncRegistration);
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(Collections.emptyList());
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);
        KeyValueData redirectCount = getKeyValueDataRedirectCount();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, times(2)).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mDebugReportApi, times(1)).scheduleSourceNoisedDebugReport(any(Source.class), any());
        ArgumentCaptor<KeyValueData> redirectCountCaptor =
                ArgumentCaptor.forClass(KeyValueData.class);
        verify(mMeasurementDao, times(1)).insertOrUpdateKeyValueData(redirectCountCaptor.capture());
        assertEquals(3, redirectCountCaptor.getValue().getRegistrationRedirectCount());
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_nullFakeReports_doesNotSendDebugReport()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();
        Map<String, List<String>> redirectHeaders =
                getRedirectHeaders(
                        AsyncRegistration.RedirectType.LIST,
                        List.of(
                                WebUtil.validUri("https://example.test/sF1").toString(),
                                WebUtil.validUri("https://example.test/sF2").toString()));
        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirects asyncRedirects = invocation.getArgument(2);
                    asyncRedirects.configure(redirectHeaders, mFlags, validAsyncRegistration);
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(null);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);
        KeyValueData redirectCount = getKeyValueDataRedirectCount();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, times(2)).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mDebugReportApi, never()).scheduleSourceNoisedDebugReport(any(Source.class), any());
        ArgumentCaptor<KeyValueData> redirectCountCaptor =
                ArgumentCaptor.forClass(KeyValueData.class);
        verify(mMeasurementDao, times(1)).insertOrUpdateKeyValueData(redirectCountCaptor.capture());
        assertEquals(3, redirectCountCaptor.getValue().getRegistrationRedirectCount());
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_ThreadInterrupted() throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();
        Map<String, List<String>> redirectHeaders =
                getRedirectHeaders(
                        AsyncRegistration.RedirectType.LIST,
                        List.of(
                                WebUtil.validUri("https://example.test/sF1").toString(),
                                WebUtil.validUri("https://example.test/sF2").toString()));
        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirects asyncRedirects = invocation.getArgument(2);
                    asyncRedirects.configure(redirectHeaders, mFlags, validAsyncRegistration);
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L),
                        1L,
                        List.of(WebUtil.validUri("https://example.test/sF")));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);
        KeyValueData redirectCount = getKeyValueDataRedirectCount();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        Thread.currentThread().interrupt();
        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.THREAD_INTERRUPTED, result);
        verify(mAsyncSourceFetcher, times(0))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(0)).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, times(0)).insertSource(any(Source.class));
        verify(mMeasurementDao, times(0)).insertAsyncRegistration(any(AsyncRegistration.class));
        ArgumentCaptor<KeyValueData> redirectCountCaptor =
                ArgumentCaptor.forClass(KeyValueData.class);
        verify(mMeasurementDao, times(0)).insertOrUpdateKeyValueData(any(KeyValueData.class));
        verify(mMeasurementDao, times(0)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void testRecordsExceedMaxRegistrationsPerJob_returnSuccessWithPendingRecords()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();
        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_WITH_PENDING_RECORDS, result);
    }

    // Tests for redirect types

    @Test
    public void runAsyncRegistrationQueueWorker_appSource_defaultRegistration_redirectTypeList()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();
        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();
        Map<String, List<String>> redirectHeaders =
                getRedirectHeaders(
                        AsyncRegistration.RedirectType.LIST,
                        List.of(LIST_TYPE_REDIRECT_URI_1, LIST_TYPE_REDIRECT_URI_2));
        Answer<Optional<Source>> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirects asyncRedirects = invocation.getArgument(2);
                    asyncRedirects.configure(redirectHeaders, mFlags, validAsyncRegistration);
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        List<Source.FakeReport> eventReportList =
                Collections.singletonList(
                        new Source.FakeReport(new UnsignedLong(1L), 1L, List.of(APP_DESTINATION)));
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);
        KeyValueData redirectCount = getKeyValueDataRedirectCount();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(1)).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, times(2))
                .insertAsyncRegistration(asyncRegistrationArgumentCaptor.capture());

        Assert.assertEquals(2, asyncRegistrationArgumentCaptor.getAllValues().size());
        AsyncRegistration asyncReg1 = asyncRegistrationArgumentCaptor.getAllValues().get(0);
        Assert.assertEquals(Uri.parse(LIST_TYPE_REDIRECT_URI_1), asyncReg1.getRegistrationUri());
        assertEquals(
                AsyncRegistrationFixture.ValidAsyncRegistrationParams.REGISTRATION_ID,
                asyncReg1.getRegistrationId());
        AsyncRegistration asyncReg2 = asyncRegistrationArgumentCaptor.getAllValues().get(1);
        Assert.assertEquals(Uri.parse(LIST_TYPE_REDIRECT_URI_2), asyncReg2.getRegistrationUri());
        assertEquals(
                AsyncRegistrationFixture.ValidAsyncRegistrationParams.REGISTRATION_ID,
                asyncReg2.getRegistrationId());

        ArgumentCaptor<KeyValueData> redirectCountCaptor =
                ArgumentCaptor.forClass(KeyValueData.class);
        verify(mMeasurementDao, times(1)).insertOrUpdateKeyValueData(redirectCountCaptor.capture());
        assertEquals(3, redirectCountCaptor.getValue().getRegistrationRedirectCount());

        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void runAsyncRegistrationQueueWorker_appSource_defaultRegistration_redirectTypeLocation()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();
        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();
        Map<String, List<String>> redirectHeaders =
                getRedirectHeaders(
                        AsyncRegistration.RedirectType.LOCATION,
                        List.of(LOCATION_TYPE_REDIRECT_URI));
        Answer<Optional<Source>> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirects asyncRedirects = invocation.getArgument(2);
                    asyncRedirects.configure(redirectHeaders, mFlags, validAsyncRegistration);
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        List<Source.FakeReport> eventReportList =
                Collections.singletonList(
                        new Source.FakeReport(new UnsignedLong(1L), 1L, List.of(APP_DESTINATION)));
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);
        KeyValueData redirectCount = getKeyValueDataRedirectCount();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(1)).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, times(1))
                .insertAsyncRegistration(asyncRegistrationArgumentCaptor.capture());

        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        AsyncRegistration asyncReg = asyncRegistrationArgumentCaptor.getAllValues().get(0);
        Assert.assertEquals(Uri.parse(LOCATION_TYPE_REDIRECT_URI), asyncReg.getRegistrationUri());

        ArgumentCaptor<KeyValueData> redirectCountCaptor =
                ArgumentCaptor.forClass(KeyValueData.class);
        verify(mMeasurementDao, times(1)).insertOrUpdateKeyValueData(redirectCountCaptor.capture());
        assertEquals(2, redirectCountCaptor.getValue().getRegistrationRedirectCount());

        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void runAsyncRegistrationQueueWorker_appSource_middleRegistration_redirectTypeLocation()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();
        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();
        Map<String, List<String>> redirectHeaders =
                getRedirectHeaders(
                        AsyncRegistration.RedirectType.LOCATION,
                        List.of(LOCATION_TYPE_REDIRECT_URI));
        Answer<Optional<Source>> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirects asyncRedirects = invocation.getArgument(2);
                    asyncRedirects.configure(redirectHeaders, mFlags, validAsyncRegistration);
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        List<Source.FakeReport> eventReportList =
                Collections.singletonList(
                        new Source.FakeReport(new UnsignedLong(1L), 1L, List.of(APP_DESTINATION)));
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);
        KeyValueData redirectCount =
                new KeyValueData.Builder()
                        .setDataType(KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT)
                        .setKey(
                                AsyncRegistrationFixture.ValidAsyncRegistrationParams
                                        .REGISTRATION_ID)
                        .setValue("5")
                        .build();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(1)).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, times(1))
                .insertAsyncRegistration(asyncRegistrationArgumentCaptor.capture());

        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        AsyncRegistration asyncReg = asyncRegistrationArgumentCaptor.getAllValues().get(0);
        Assert.assertEquals(Uri.parse(LOCATION_TYPE_REDIRECT_URI), asyncReg.getRegistrationUri());
        ArgumentCaptor<KeyValueData> redirectCountCaptor =
                ArgumentCaptor.forClass(KeyValueData.class);
        verify(mMeasurementDao, times(1)).insertOrUpdateKeyValueData(redirectCountCaptor.capture());
        assertEquals(6, redirectCountCaptor.getValue().getRegistrationRedirectCount());

        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void runAsyncRegistrationQueueWorker_appSrc_defaultReg_redirectWellKnown_typeLocation()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();
        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();
        Answer<Optional<Source>> answerAsyncSourceFetcher =
                getAsyncSourceAnswerForLocationTypeRedirectToWellKnown(
                        LOCATION_TYPE_REDIRECT_URI, validAsyncRegistration);
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());
        when(mFlags.getMeasurementEnableRedirectToWellKnownPath()).thenReturn(true);

        List<Source.FakeReport> eventReportList =
                Collections.singletonList(
                        new Source.FakeReport(new UnsignedLong(1L), 1L, List.of(APP_DESTINATION)));
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration);
        KeyValueData redirectCount = getKeyValueDataRedirectCount();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(1)).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, times(1))
                .insertAsyncRegistration(asyncRegistrationArgumentCaptor.capture());

        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        AsyncRegistration asyncReg = asyncRegistrationArgumentCaptor.getAllValues().get(0);
        Assert.assertEquals(
                getRegistrationRedirectToWellKnownUri(
                        Uri.parse(LOCATION_TYPE_REDIRECT_URI), LOCATION_TYPE_REDIRECT_URI),
                asyncReg.getRegistrationUri());

        ArgumentCaptor<KeyValueData> redirectCountCaptor =
                ArgumentCaptor.forClass(KeyValueData.class);
        verify(mMeasurementDao, times(1)).insertOrUpdateKeyValueData(redirectCountCaptor.capture());
        assertEquals(2, redirectCountCaptor.getValue().getRegistrationRedirectCount());

        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void runAsyncRegistrationQueueWorker_appSrc_defaultReg_redirectChain_typeLocation()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();
        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();
        Answer<Optional<Source>> answerAsyncSourceFetcher =
                getAsyncSourceAnswerForLocationTypeRedirectToWellKnown(
                        LOCATION_TYPE_REDIRECT_URI, validAsyncRegistration);
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());
        when(mFlags.getMeasurementEnableRedirectToWellKnownPath()).thenReturn(true);

        List<Source.FakeReport> eventReportList =
                Collections.singletonList(
                        new Source.FakeReport(new UnsignedLong(1L), 1L, List.of(APP_DESTINATION)));
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration);
        KeyValueData redirectCount = getKeyValueDataRedirectCount();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();
        // Set up the second invocation.
        Answer<Optional<Source>> answerAsyncSourceFetcher2 =
                getAsyncSourceAnswerForLocationTypeRedirectToWellKnown(
                        LOCATION_TYPE_REDIRECT_URI_2, validAsyncRegistration);
        doAnswer(answerAsyncSourceFetcher2)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Set up the third invocation.
        Answer<Optional<Source>> answerAsyncSourceFetcher3 =
                getAsyncSourceAnswerForLocationTypeRedirectToWellKnown(
                        LOCATION_TYPE_REDIRECT_URI_3, validAsyncRegistration);

        doAnswer(answerAsyncSourceFetcher3)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions for all invocations
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(3))
                .insertAsyncRegistration(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(3, asyncRegistrationArgumentCaptor.getAllValues().size());

        ArgumentCaptor<KeyValueData> redirectCountCaptor =
                ArgumentCaptor.forClass(KeyValueData.class);
        verify(mMeasurementDao, times(3)).insertOrUpdateKeyValueData(redirectCountCaptor.capture());
        assertEquals(4, redirectCountCaptor.getValue().getRegistrationRedirectCount());

        // Assertions for first invocation
        assertRepeatedAsyncRegistration(
                asyncRegistrationArgumentCaptor,
                0,
                getRegistrationRedirectToWellKnownUri(
                        Uri.parse(LOCATION_TYPE_REDIRECT_URI),
                        LOCATION_TYPE_REDIRECT_URI.toString()));

        // Assertions for second invocation
        assertRepeatedAsyncRegistration(
                asyncRegistrationArgumentCaptor,
                1,
                getRegistrationRedirectToWellKnownUri(
                        Uri.parse(LOCATION_TYPE_REDIRECT_URI_2),
                        LOCATION_TYPE_REDIRECT_URI_2.toString()));

        // Assertions for third invocation
        assertRepeatedAsyncRegistration(
                asyncRegistrationArgumentCaptor,
                2,
                getRegistrationRedirectToWellKnownUri(
                        Uri.parse(LOCATION_TYPE_REDIRECT_URI_3),
                        LOCATION_TYPE_REDIRECT_URI_3.toString()));
    }

    @Test
    public void runAsyncRegistrationQueueWorker_appSrc_defaultReg_redirectWithExistingPathAndQuery()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();
        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();
        String redirectWithExistingPath = LOCATION_TYPE_REDIRECT_URI + "/path?key=value";
        Answer<Optional<Source>> answerAsyncSourceFetcher =
                getAsyncSourceAnswerForLocationTypeRedirectToWellKnown(
                        redirectWithExistingPath, validAsyncRegistration);
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());
        when(mFlags.getMeasurementEnableRedirectToWellKnownPath()).thenReturn(true);

        List<Source.FakeReport> eventReportList =
                Collections.singletonList(
                        new Source.FakeReport(new UnsignedLong(1L), 1L, List.of(APP_DESTINATION)));
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration);
        KeyValueData redirectCount = getKeyValueDataRedirectCount();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution

        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(1)).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, times(1))
                .insertAsyncRegistration(asyncRegistrationArgumentCaptor.capture());

        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        AsyncRegistration asyncReg = asyncRegistrationArgumentCaptor.getAllValues().get(0);

        Uri expectedUri =
                Uri.parse(
                        LOCATION_TYPE_REDIRECT_URI
                                + "/"
                                + AsyncRedirects.WELL_KNOWN_PATH_SEGMENT
                                + "?"
                                + AsyncRedirects.WELL_KNOWN_QUERY_PARAM
                                + "="
                                + Uri.encode(redirectWithExistingPath));
        Assert.assertEquals(expectedUri, asyncReg.getRegistrationUri());
    }

    @Test
    public void runAsyncRegistrationQueueWorker_noSourceReg_RedirectHasSource()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();
        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();

        // Create an empty response for fetchSource. This is necessary because we need to mock the
        // addition of a redirect, despite a failing initial source registration.
        Map<String, List<String>> redirectHeaders =
                getRedirectHeaders(
                        AsyncRegistration.RedirectType.LOCATION,
                        List.of(LOCATION_TYPE_REDIRECT_URI));
        redirectHeaders.put(
                AsyncRedirects.HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG,
                List.of(AsyncRedirects.REDIRECT_302_TO_WELL_KNOWN));
        Answer<Optional<Source>> answerEmptySource =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirects asyncRedirects = invocation.getArgument(2);
                    asyncRedirects.configure(redirectHeaders, mFlags, validAsyncRegistration);
                    return Optional.empty();
                };

        doAnswer(answerEmptySource).when(mAsyncSourceFetcher).fetchSource(any(), any(), any());

        when(mFlags.getMeasurementEnableRedirectToWellKnownPath()).thenReturn(true);

        List<Source.FakeReport> eventReportList =
                Collections.singletonList(
                        new Source.FakeReport(new UnsignedLong(1L), 1L, List.of(APP_DESTINATION)));
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration);
        KeyValueData redirectCount = getKeyValueDataRedirectCount();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution

        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Set up second invocation of runner. This time, do return a valid source.
        Answer<Optional<Source>> answerAsyncSourceFetcher =
                getAsyncSourceAnswerForLocationTypeRedirectToWellKnown(
                        LOCATION_TYPE_REDIRECT_URI_2, validAsyncRegistration);

        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);

        // Assertions
        verify(mAsyncSourceFetcher, times(2))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(1)).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, times(2))
                .insertAsyncRegistration(asyncRegistrationArgumentCaptor.capture());

        Assert.assertEquals(2, asyncRegistrationArgumentCaptor.getAllValues().size());
        AsyncRegistration asyncReg = asyncRegistrationArgumentCaptor.getAllValues().get(0);

        // Assert first invocation's redirect
        Uri expectedUri =
                getRegistrationRedirectToWellKnownUri(
                        Uri.parse(LOCATION_TYPE_REDIRECT_URI),
                        LOCATION_TYPE_REDIRECT_URI.toString());
        Assert.assertEquals(expectedUri, asyncReg.getRegistrationUri());

        AsyncRegistration asyncReg2 = asyncRegistrationArgumentCaptor.getAllValues().get(1);

        // Assert second invocation's redirect
        expectedUri =
                getRegistrationRedirectToWellKnownUri(
                        Uri.parse(LOCATION_TYPE_REDIRECT_URI_2),
                        LOCATION_TYPE_REDIRECT_URI_2.toString());
        Assert.assertEquals(expectedUri, asyncReg2.getRegistrationUri());
    }

    @Test
    public void runAsyncRegistrationQueueWorker_appSrc_defaultReg_redirectAlreadyWellKnown()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();
        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();
        String redirectAlreadyWellKnown =
                LOCATION_TYPE_REDIRECT_URI + "/" + AsyncRedirects.WELL_KNOWN_PATH_SEGMENT;
        Answer<Optional<Source>> answerAsyncSourceFetcher =
                getAsyncSourceAnswerForLocationTypeRedirectToWellKnown(
                        redirectAlreadyWellKnown, validAsyncRegistration);
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());
        when(mFlags.getMeasurementEnableRedirectToWellKnownPath()).thenReturn(true);

        List<Source.FakeReport> eventReportList =
                Collections.singletonList(
                        new Source.FakeReport(new UnsignedLong(1L), 1L, List.of(APP_DESTINATION)));
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration);
        KeyValueData redirectCount = getKeyValueDataRedirectCount();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution

        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(1)).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, times(1))
                .insertAsyncRegistration(asyncRegistrationArgumentCaptor.capture());

        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        AsyncRegistration asyncReg = asyncRegistrationArgumentCaptor.getAllValues().get(0);
        // Assert .well-known isn't duplicated in path.
        Assert.assertEquals(
                getRegistrationRedirectToWellKnownUri(
                        Uri.parse(LOCATION_TYPE_REDIRECT_URI), redirectAlreadyWellKnown.toString()),
                asyncReg.getRegistrationUri());
    }

    @Test
    public void runAsyncRegistrationQueueWorker_appInstalled_markToBeDeleted()
            throws DatastoreException, PackageManager.NameNotFoundException {
        // Setup
        when(mFlags.getMeasurementEnablePreinstallCheck()).thenReturn(true);
        setUpApplicationStatus(List.of("com.destination", "com.destination2"), List.of());
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                new AsyncRegistrationQueueRunner(
                        mSpyContext,
                        mContentResolver,
                        mAsyncSourceFetcher,
                        mAsyncTriggerFetcher,
                        new FakeDatastoreManager(),
                        mDebugReportApi,
                        mSourceNoiseHandler,
                        mFlags,
                        mLogger);

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();
        Map<String, List<String>> redirectHeaders =
                getRedirectHeaders(
                        AsyncRegistration.RedirectType.LIST,
                        List.of(
                                WebUtil.validUri("https://example.test/sF1").toString(),
                                WebUtil.validUri("https://example.test/sF2").toString()));
        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirects asyncRedirects = invocation.getArgument(2);
                    asyncRedirects.configure(redirectHeaders, mFlags, validAsyncRegistration);
                    return Optional.of(
                            SourceFixture.getValidSourceBuilder()
                                    .setDropSourceIfInstalled(true)
                                    .build());
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L),
                        1L,
                        List.of(WebUtil.validUri("https://example.test/sF")));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);
        KeyValueData redirectCount = getKeyValueDataRedirectCount();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao, times(1)).insertSource(sourceCaptor.capture());
        assertEquals(sourceCaptor.getValue().getStatus(), Source.Status.MARKED_TO_DELETE);
        verify(mMeasurementDao, times(2)).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).insertEventReport(any());
    }

    @Test
    public void runAsyncRegistrationQueueWorker_appNotInstalled_markAsActive()
            throws DatastoreException, PackageManager.NameNotFoundException {
        // Setup
        when(mFlags.getMeasurementEnablePreinstallCheck()).thenReturn(true);
        setUpApplicationStatus(List.of(), List.of("com.destination", "com.destination2"));
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                new AsyncRegistrationQueueRunner(
                        mSpyContext,
                        mContentResolver,
                        mAsyncSourceFetcher,
                        mAsyncTriggerFetcher,
                        new FakeDatastoreManager(),
                        mDebugReportApi,
                        mSourceNoiseHandler,
                        mFlags,
                        mLogger);

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();
        Map<String, List<String>> redirectHeaders =
                getRedirectHeaders(
                        AsyncRegistration.RedirectType.LIST,
                        List.of(
                                WebUtil.validUri("https://example.test/sF1").toString(),
                                WebUtil.validUri("https://example.test/sF2").toString()));
        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirects asyncRedirects = invocation.getArgument(2);
                    asyncRedirects.configure(redirectHeaders, mFlags, validAsyncRegistration);
                    return Optional.of(
                            SourceFixture.getValidSourceBuilder()
                                    .setDropSourceIfInstalled(true)
                                    .build());
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L),
                        1L,
                        List.of(WebUtil.validUri("https://example.test/sF")));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);
        KeyValueData redirectCount = getKeyValueDataRedirectCount();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao, times(1)).insertSource(sourceCaptor.capture());
        assertEquals(sourceCaptor.getValue().getStatus(), Source.Status.ACTIVE);
        verify(mMeasurementDao, times(2)).insertAsyncRegistration(any(AsyncRegistration.class));
    }

    @Test
    public void runAsyncRegistrationQueueWorker_notDropSourceIfInstalled_markAsActive()
            throws DatastoreException, PackageManager.NameNotFoundException {
        // Setup
        when(mFlags.getMeasurementEnablePreinstallCheck()).thenReturn(true);
        setUpApplicationStatus(List.of("com.destination2"), List.of("com.destination"));
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                new AsyncRegistrationQueueRunner(
                        mSpyContext,
                        mContentResolver,
                        mAsyncSourceFetcher,
                        mAsyncTriggerFetcher,
                        new FakeDatastoreManager(),
                        mDebugReportApi,
                        mSourceNoiseHandler,
                        mFlags,
                        mLogger);

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();
        Map<String, List<String>> redirectHeaders =
                getRedirectHeaders(
                        AsyncRegistration.RedirectType.LIST,
                        List.of(
                                WebUtil.validUri("https://example.test/sF1").toString(),
                                WebUtil.validUri("https://example.test/sF2").toString()));
        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirects asyncRedirects = invocation.getArgument(2);
                    asyncRedirects.configure(redirectHeaders, mFlags, validAsyncRegistration);
                    return Optional.of(
                            SourceFixture.getValidSourceBuilder()
                                    .setDropSourceIfInstalled(false)
                                    .build());
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L),
                        1L,
                        List.of(WebUtil.validUri("https://example.test/sF")));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);
        KeyValueData redirectCount = getKeyValueDataRedirectCount();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao, times(1)).insertSource(sourceCaptor.capture());
        assertEquals(sourceCaptor.getValue().getStatus(), Source.Status.ACTIVE);
        verify(mMeasurementDao, times(2)).insertAsyncRegistration(any(AsyncRegistration.class));
    }

    @Test
    public void
            runAsyncRegistrationQueueWorker_notDropSourceIfInstalledAndAppInstalled_markAsActive()
                    throws DatastoreException, PackageManager.NameNotFoundException {
        // Setup
        when(mFlags.getMeasurementEnablePreinstallCheck()).thenReturn(true);
        setUpApplicationStatus(List.of("com.destination"), List.of("com.destination2"));
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                new AsyncRegistrationQueueRunner(
                        mSpyContext,
                        mContentResolver,
                        mAsyncSourceFetcher,
                        mAsyncTriggerFetcher,
                        new FakeDatastoreManager(),
                        mDebugReportApi,
                        mSourceNoiseHandler,
                        mFlags,
                        mLogger);

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();
        Map<String, List<String>> redirectHeaders =
                getRedirectHeaders(
                        AsyncRegistration.RedirectType.LIST,
                        List.of(
                                WebUtil.validUri("https://example.test/sF1").toString(),
                                WebUtil.validUri("https://example.test/sF2").toString()));
        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirects asyncRedirects = invocation.getArgument(2);
                    asyncRedirects.configure(redirectHeaders, mFlags, validAsyncRegistration);
                    return Optional.of(
                            SourceFixture.getValidSourceBuilder()
                                    .setDropSourceIfInstalled(false)
                                    .build());
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L),
                        1L,
                        List.of(WebUtil.validUri("https://example.test/sF")));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);
        KeyValueData redirectCount = getKeyValueDataRedirectCount();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao, times(1)).insertSource(sourceCaptor.capture());
        assertEquals(sourceCaptor.getValue().getStatus(), Source.Status.ACTIVE);
        verify(mMeasurementDao, times(2)).insertAsyncRegistration(any(AsyncRegistration.class));
    }

    @Test
    public void runAsyncRegistrationQueueWorker_appTrigger_defaultRegistration_redirectTypeList()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppTrigger();
        Map<String, List<String>> redirectHeaders =
                getRedirectHeaders(
                        AsyncRegistration.RedirectType.LIST,
                        List.of(LIST_TYPE_REDIRECT_URI_1, LIST_TYPE_REDIRECT_URI_2));
        Answer<Optional<Trigger>> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirects asyncRedirects = invocation.getArgument(2);
                    asyncRedirects.configure(redirectHeaders, mFlags, validAsyncRegistration);
                    return Optional.of(mMockedTrigger);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);
        KeyValueData redirectCount = getKeyValueDataRedirectCount();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(1)).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, times(2))
                .insertAsyncRegistration(asyncRegistrationArgumentCaptor.capture());

        Assert.assertEquals(2, asyncRegistrationArgumentCaptor.getAllValues().size());

        AsyncRegistration asyncReg1 = asyncRegistrationArgumentCaptor.getAllValues().get(0);
        Assert.assertEquals(Uri.parse(LIST_TYPE_REDIRECT_URI_1), asyncReg1.getRegistrationUri());
        Assert.assertEquals(
                AsyncRegistrationFixture.ValidAsyncRegistrationParams.REGISTRATION_ID,
                asyncReg1.getRegistrationId());

        AsyncRegistration asyncReg2 = asyncRegistrationArgumentCaptor.getAllValues().get(1);
        Assert.assertEquals(Uri.parse(LIST_TYPE_REDIRECT_URI_2), asyncReg2.getRegistrationUri());
        Assert.assertEquals(
                AsyncRegistrationFixture.ValidAsyncRegistrationParams.REGISTRATION_ID,
                asyncReg2.getRegistrationId());

        ArgumentCaptor<KeyValueData> redirectCountCaptor =
                ArgumentCaptor.forClass(KeyValueData.class);
        verify(mMeasurementDao, times(1)).insertOrUpdateKeyValueData(redirectCountCaptor.capture());
        assertEquals(3, redirectCountCaptor.getValue().getRegistrationRedirectCount());

        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void runAsyncRegistrationQueueWorker_appTrigger_defaultReg_redirectTypeLocation()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppTrigger();
        Map<String, List<String>> redirectHeaders =
                getRedirectHeaders(
                        AsyncRegistration.RedirectType.LOCATION,
                        List.of(LOCATION_TYPE_REDIRECT_URI));
        Answer<Optional<Trigger>> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirects asyncRedirects = invocation.getArgument(2);
                    asyncRedirects.configure(redirectHeaders, mFlags, validAsyncRegistration);
                    return Optional.of(mMockedTrigger);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);
        KeyValueData redirectCount = getKeyValueDataRedirectCount();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(1)).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, times(1))
                .insertAsyncRegistration(asyncRegistrationArgumentCaptor.capture());

        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());

        AsyncRegistration asyncReg = asyncRegistrationArgumentCaptor.getAllValues().get(0);
        Assert.assertEquals(Uri.parse(LOCATION_TYPE_REDIRECT_URI), asyncReg.getRegistrationUri());

        ArgumentCaptor<KeyValueData> redirectCountCaptor =
                ArgumentCaptor.forClass(KeyValueData.class);
        verify(mMeasurementDao, times(1)).insertOrUpdateKeyValueData(redirectCountCaptor.capture());
        assertEquals(2, redirectCountCaptor.getValue().getRegistrationRedirectCount());

        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void runAsyncRegistrationQueueWorker_appTrigger_middleRegistration_redirectTypeLocation()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppTrigger();
        Map<String, List<String>> redirectHeaders =
                getRedirectHeaders(
                        AsyncRegistration.RedirectType.LOCATION,
                        List.of(LOCATION_TYPE_REDIRECT_URI));
        Answer<Optional<Trigger>> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirects asyncRedirects = invocation.getArgument(2);
                    asyncRedirects.configure(redirectHeaders, mFlags, validAsyncRegistration);
                    return Optional.of(mMockedTrigger);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);
        KeyValueData redirectCount =
                new KeyValueData.Builder()
                        .setDataType(KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT)
                        .setKey(
                                AsyncRegistrationFixture.ValidAsyncRegistrationParams
                                        .REGISTRATION_ID)
                        .setValue("4")
                        .build();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(1)).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, times(1))
                .insertAsyncRegistration(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        AsyncRegistration asyncReg = asyncRegistrationArgumentCaptor.getAllValues().get(0);
        Assert.assertEquals(Uri.parse(LOCATION_TYPE_REDIRECT_URI), asyncReg.getRegistrationUri());
        // Increment Redirect Count by 1
        ArgumentCaptor<KeyValueData> redirectCountCaptor =
                ArgumentCaptor.forClass(KeyValueData.class);
        verify(mMeasurementDao, times(1)).insertOrUpdateKeyValueData(redirectCountCaptor.capture());
        assertEquals(5, redirectCountCaptor.getValue().getRegistrationRedirectCount());

        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void runAsyncRegistrationQueueWorker_appTrigger_nearMaxCount_addSomeRedirects()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppTrigger();
        Map<String, List<String>> redirectHeaders =
                getRedirectHeaders(
                        AsyncRegistration.RedirectType.LIST,
                        IntStream.range(1, 10)
                                .mapToObj((i) -> LIST_TYPE_REDIRECT_URI_1 + "/" + i)
                                .collect(Collectors.toList()));
        Answer<Optional<Trigger>> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirects asyncRedirects = invocation.getArgument(2);
                    asyncRedirects.configure(redirectHeaders, mFlags, validAsyncRegistration);
                    return Optional.of(mMockedTrigger);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);
        KeyValueData redirectCount =
                new KeyValueData.Builder()
                        .setDataType(KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT)
                        .setKey(
                                AsyncRegistrationFixture.ValidAsyncRegistrationParams
                                        .REGISTRATION_ID)
                        .setValue("15")
                        .build();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(1)).insertTrigger(any(Trigger.class));
        // Already has 15, only 5 out of the new 10 Uri should be added.
        verify(mMeasurementDao, times(5))
                .insertAsyncRegistration(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(5, asyncRegistrationArgumentCaptor.getAllValues().size());
        AtomicInteger i = new AtomicInteger(1);
        asyncRegistrationArgumentCaptor
                .getAllValues()
                .forEach(
                        (asyncRegistration -> {
                            Assert.assertEquals(
                                    Uri.parse(LIST_TYPE_REDIRECT_URI_1 + "/" + i.getAndIncrement()),
                                    asyncRegistration.getRegistrationUri());
                        }));
        ArgumentCaptor<KeyValueData> redirectCountCaptor =
                ArgumentCaptor.forClass(KeyValueData.class);
        verify(mMeasurementDao, times(1)).insertOrUpdateKeyValueData(redirectCountCaptor.capture());
        assertEquals(20, redirectCountCaptor.getValue().getRegistrationRedirectCount());
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void runAsyncRegistrationQueueWorker_appTrigger_maxCount_addNoRedirects()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppTrigger();
        Map<String, List<String>> redirectHeaders =
                getRedirectHeaders(
                        AsyncRegistration.RedirectType.LOCATION,
                        Collections.singletonList((LOCATION_TYPE_REDIRECT_URI)));
        Answer<Optional<Trigger>> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirects asyncRedirects = invocation.getArgument(2);
                    asyncRedirects.configure(redirectHeaders, mFlags, validAsyncRegistration);
                    return Optional.of(mMockedTrigger);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);
        KeyValueData redirectCount =
                new KeyValueData.Builder()
                        .setDataType(KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT)
                        .setKey(
                                AsyncRegistrationFixture.ValidAsyncRegistrationParams
                                        .REGISTRATION_ID)
                        .setValue("20")
                        .build();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(1)).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
        // No insertions expected as redirectCount is already 20 (Max).
        verify(mMeasurementDao, times(0)).insertAsyncRegistration(any());
        verify(mMeasurementDao, never()).insertOrUpdateKeyValueData(any());
    }
    // End tests for redirect types

    @Test
    public void test_runAsyncRegistrationQueueWorker_appSource_noRedirects_success()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L),
                        1L,
                        List.of(WebUtil.validUri("https://example.test/sF")));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(1)).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).insertOrUpdateKeyValueData(any());
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appSource_adTechUnavailable()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(
                            AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE);
                    return Optional.empty();
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L),
                        1L,
                        List.of(WebUtil.validUri("https://example.test/sF")));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());

        verify(mMeasurementDao, never()).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, never()).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appSource_networkError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(
                            AsyncFetchStatus.ResponseStatus.NETWORK_ERROR);
                    return Optional.empty();
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L),
                        1L,
                        List.of(WebUtil.validUri("https://example.test/sF")));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());

        verify(mMeasurementDao, never()).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, never()).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appSource_parsingError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.PARSING_ERROR);
                    return Optional.empty();
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, never()).updateRetryCount(any());
        verify(mMeasurementDao, never()).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, never()).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appTrigger_success()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppTrigger();
        Map<String, List<String>> redirectHeaders =
                getRedirectHeaders(
                        AsyncRegistration.RedirectType.LIST,
                        List.of(
                                WebUtil.validUri("https://example.test/sF1").toString(),
                                WebUtil.validUri("https://example.test/sF2").toString()));
        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirects asyncRedirects = invocation.getArgument(2);
                    asyncRedirects.configure(redirectHeaders, mFlags, validAsyncRegistration);
                    return Optional.of(mMockedTrigger);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);

        KeyValueData redirectCount =
                new KeyValueData.Builder()
                        .setDataType(KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT)
                        .setKey(
                                AsyncRegistrationFixture.ValidAsyncRegistrationParams
                                        .REGISTRATION_ID)
                        .setValue("1")
                        .build();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(1)).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, times(2)).insertAsyncRegistration(any(AsyncRegistration.class));
        ArgumentCaptor<KeyValueData> redirectCountCaptor =
                ArgumentCaptor.forClass(KeyValueData.class);
        verify(mMeasurementDao, times(1)).insertOrUpdateKeyValueData(redirectCountCaptor.capture());
        assertEquals(3, redirectCountCaptor.getValue().getRegistrationRedirectCount());
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appTrigger_noRedirects_success()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppTrigger();

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    return Optional.of(mMockedTrigger);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(1)).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).getKeyValueData(anyString(), any());
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appTrigger_adTechUnavailable()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppTrigger();

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(
                            AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE);
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());
        verify(mMeasurementDao, never()).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appTrigger_networkError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppTrigger();

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(
                            AsyncFetchStatus.ResponseStatus.NETWORK_ERROR);
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());
        verify(mMeasurementDao, never()).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appTrigger_parsingError_withRedirects()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppTrigger();
        Map<String, List<String>> redirectHeaders =
                getRedirectHeaders(
                        AsyncRegistration.RedirectType.LIST,
                        List.of(
                                WebUtil.validUri("https://example.test/sF1").toString(),
                                WebUtil.validUri("https://example.test/sF2").toString()));
        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.PARSING_ERROR);
                    AsyncRedirects asyncRedirects = invocation.getArgument(2);
                    asyncRedirects.configure(redirectHeaders, mFlags, validAsyncRegistration);
                    return Optional.empty();
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);
        KeyValueData redirectCount =
                new KeyValueData.Builder()
                        .setDataType(KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT)
                        .setKey(
                                AsyncRegistrationFixture.ValidAsyncRegistrationParams
                                        .REGISTRATION_ID)
                        .setValue("1")
                        .build();
        when(mMeasurementDao.getKeyValueData(anyString(), any())).thenReturn(redirectCount);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, never()).updateRetryCount(any());
        verify(mMeasurementDao, never()).insertTrigger(any(Trigger.class));
        // Verifying redirect insertion
        ArgumentCaptor<AsyncRegistration> argumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(2)).insertAsyncRegistration(argumentCaptor.capture());
        List<AsyncRegistration> redirects = argumentCaptor.getAllValues();
        assertEquals(2, redirects.size());
        assertEquals(
                WebUtil.validUri("https://example.test/sF1"),
                redirects.get(0).getRegistrationUri());
        assertEquals(
                WebUtil.validUri("https://example.test/sF2"),
                redirects.get(1).getRegistrationUri());
        ArgumentCaptor<KeyValueData> redirectCountCaptor =
                ArgumentCaptor.forClass(KeyValueData.class);
        verify(mMeasurementDao, times(1)).insertOrUpdateKeyValueData(redirectCountCaptor.capture());
        assertEquals(3, redirectCountCaptor.getValue().getRegistrationRedirectCount());
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_networkError_logRetryCount()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();
        validAsyncRegistration.incrementRetryCount();

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(
                            AsyncFetchStatus.ResponseStatus.NETWORK_ERROR);
                    return Optional.empty();
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L),
                        1L,
                        List.of(WebUtil.validUri("https://example.test/sF")));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        MeasurementRegistrationResponseStats measurementRegistrationResponseStats =
                getMeasurementRegistrationResponseStats();
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                2, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());
        Assert.assertEquals(2, measurementRegistrationResponseStats.getRetryCount());

        verify(mMeasurementDao, never()).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, never()).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webSource_success() throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForWebSource();

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L),
                        1L,
                        List.of(WebUtil.validUri("https://example.test/sF")));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(1)).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webSource_adTechUnavailable()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForWebSource();

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(
                            AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE);
                    return Optional.empty();
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L),
                        1L,
                        List.of(WebUtil.validUri("https://example.test/sF")));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());
        verify(mMeasurementDao, never()).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, never()).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webSource_networkError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForWebSource();

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(
                            AsyncFetchStatus.ResponseStatus.NETWORK_ERROR);
                    return Optional.empty();
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());
        verify(mMeasurementDao, never()).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, never()).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webSource_parsingError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForWebSource();

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.PARSING_ERROR);
                    return Optional.empty();
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L),
                        1L,
                        List.of(WebUtil.validUri("https://example.test/sF")));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(mMockedSource))
                .thenReturn(eventReportList);

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, never()).updateRetryCount(any());
        verify(mMeasurementDao, never()).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, never()).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webTrigger_success()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForWebTrigger();

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    return Optional.of(mMockedTrigger);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, times(1)).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webTrigger_adTechUnavailable()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForWebTrigger();

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(
                            AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE);
                    return Optional.empty();
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());
        verify(mMeasurementDao, never()).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webTrigger_networkError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForWebTrigger();

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(
                            AsyncFetchStatus.ResponseStatus.NETWORK_ERROR);
                    return Optional.empty();
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());
        verify(mMeasurementDao, never()).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webTrigger_parsingError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForWebTrigger();

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.PARSING_ERROR);
                    return Optional.empty();
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyInt(), any()))
                .thenReturn(validAsyncRegistration)
                .thenReturn(null);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(), any());
        verify(mMeasurementDao, never()).updateRetryCount(any());
        verify(mMeasurementDao, never()).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void insertSource_withFakeReportsFalseAppAttribution_accountsForFakeReportAttribution()
            throws DatastoreException {
        // Setup
        int fakeReportsCount = 2;
        Source source =
                spy(
                        SourceFixture.getMinimalValidSourceBuilder()
                                .setAppDestinations(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                                .setWebDestinations(null)
                                .build());
        List<Source.FakeReport> fakeReports =
                createFakeReports(
                        source,
                        fakeReportsCount,
                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS);
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();
        Answer<?> falseAttributionAnswer =
                (arg) -> {
                    source.setAttributionMode(Source.AttributionMode.FALSELY);
                    return fakeReports;
                };
        doAnswer(falseAttributionAnswer)
                .when(mSourceNoiseHandler)
                .assignAttributionModeAndGenerateFakeReports(source);
        ArgumentCaptor<Attribution> attributionRateLimitArgCaptor =
                ArgumentCaptor.forClass(Attribution.class);

        // Execution
        asyncRegistrationQueueRunner.insertSourceFromTransaction(source, mMeasurementDao);

        // Assertion
        verify(mMeasurementDao).insertSource(source);
        verify(mMeasurementDao, times(2)).insertEventReport(any());
        verify(mMeasurementDao).insertAttribution(attributionRateLimitArgCaptor.capture());

        assertEquals(
                new Attribution.Builder()
                        .setSourceId(DEFAULT_SOURCE_ID)
                        .setDestinationOrigin(source.getAppDestinations().get(0).toString())
                        .setDestinationSite(source.getAppDestinations().get(0).toString())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setSourceOrigin(source.getPublisher().toString())
                        .setSourceSite(source.getPublisher().toString())
                        .setRegistrant(source.getRegistrant().toString())
                        .setTriggerTime(source.getEventTime())
                        .setRegistrationOrigin(source.getRegistrationOrigin())
                        .build(),
                attributionRateLimitArgCaptor.getValue());
    }

    @Test
    public void insertSource_withFakeReportsFalseWebAttribution_accountsForFakeReportAttribution()
            throws DatastoreException {
        // Setup
        int fakeReportsCount = 2;
        Source source =
                spy(
                        SourceFixture.getMinimalValidSourceBuilder()
                                .setAppDestinations(null)
                                .setWebDestinations(
                                        SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                                .build());
        List<Source.FakeReport> fakeReports =
                createFakeReports(
                        source, fakeReportsCount, SourceFixture.ValidSourceParams.WEB_DESTINATIONS);
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();
        Answer<?> falseAttributionAnswer =
                (arg) -> {
                    source.setAttributionMode(Source.AttributionMode.FALSELY);
                    return fakeReports;
                };
        doAnswer(falseAttributionAnswer)
                .when(mSourceNoiseHandler)
                .assignAttributionModeAndGenerateFakeReports(source);
        ArgumentCaptor<Attribution> attributionRateLimitArgCaptor =
                ArgumentCaptor.forClass(Attribution.class);

        // Execution
        asyncRegistrationQueueRunner.insertSourceFromTransaction(source, mMeasurementDao);

        // Assertion
        verify(mMeasurementDao).insertSource(source);
        verify(mMeasurementDao, times(2)).insertEventReport(any());
        verify(mMeasurementDao).insertAttribution(attributionRateLimitArgCaptor.capture());

        assertEquals(
                new Attribution.Builder()
                        .setSourceId(DEFAULT_SOURCE_ID)
                        .setDestinationOrigin(source.getWebDestinations().get(0).toString())
                        .setDestinationSite(source.getWebDestinations().get(0).toString())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setSourceOrigin(source.getPublisher().toString())
                        .setSourceSite(source.getPublisher().toString())
                        .setRegistrant(source.getRegistrant().toString())
                        .setTriggerTime(source.getEventTime())
                        .setRegistrationOrigin(source.getRegistrationOrigin())
                        .build(),
                attributionRateLimitArgCaptor.getValue());
    }

    @Test
    public void insertSource_withFalseAppAndWebAttribution_accountsForFakeReportAttribution()
            throws DatastoreException {
        // Setup
        int fakeReportsCount = 2;
        Source source =
                spy(
                        SourceFixture.getMinimalValidSourceBuilder()
                                .setAppDestinations(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                                .setWebDestinations(
                                        SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                                .build());
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        List<Source.FakeReport> fakeReports =
                createFakeReports(
                        source,
                        fakeReportsCount,
                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS);

        Answer<?> falseAttributionAnswer =
                (arg) -> {
                    source.setAttributionMode(Source.AttributionMode.FALSELY);
                    return fakeReports;
                };
        ArgumentCaptor<Attribution> attributionRateLimitArgCaptor =
                ArgumentCaptor.forClass(Attribution.class);
        doAnswer(falseAttributionAnswer)
                .when(mSourceNoiseHandler)
                .assignAttributionModeAndGenerateFakeReports(source);
        ArgumentCaptor<EventReport> fakeEventReportCaptor =
                ArgumentCaptor.forClass(EventReport.class);

        // Execution
        asyncRegistrationQueueRunner.insertSourceFromTransaction(source, mMeasurementDao);

        // Assertion
        verify(mMeasurementDao).insertSource(source);
        verify(mMeasurementDao, times(2)).insertEventReport(fakeEventReportCaptor.capture());
        verify(mMeasurementDao, times(2))
                .insertAttribution(attributionRateLimitArgCaptor.capture());
        assertEquals(
                new Attribution.Builder()
                        .setSourceId(DEFAULT_SOURCE_ID)
                        .setDestinationOrigin(source.getAppDestinations().get(0).toString())
                        .setDestinationSite(source.getAppDestinations().get(0).toString())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setSourceOrigin(source.getPublisher().toString())
                        .setSourceSite(source.getPublisher().toString())
                        .setRegistrant(source.getRegistrant().toString())
                        .setTriggerTime(source.getEventTime())
                        .setRegistrationOrigin(source.getRegistrationOrigin())
                        .build(),
                attributionRateLimitArgCaptor.getAllValues().get(0));

        assertEquals(
                new Attribution.Builder()
                        .setSourceId(DEFAULT_SOURCE_ID)
                        .setDestinationOrigin(source.getWebDestinations().get(0).toString())
                        .setDestinationSite(source.getWebDestinations().get(0).toString())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setSourceOrigin(source.getPublisher().toString())
                        .setSourceSite(source.getPublisher().toString())
                        .setRegistrant(source.getRegistrant().toString())
                        .setTriggerTime(source.getEventTime())
                        .setRegistrationOrigin(source.getRegistrationOrigin())
                        .build(),
                attributionRateLimitArgCaptor.getAllValues().get(1));
        fakeEventReportCaptor
                .getAllValues()
                .forEach(
                        (report) -> {
                            assertNull(report.getSourceDebugKey());
                            assertNull(report.getTriggerDebugKey());
                        });
    }

    @Test
    public void insertSource_appSourceHasAdIdPermission_fakeReportHasDebugKey()
            throws DatastoreException {
        // Setup
        Source source =
                spy(
                        SourceFixture.getMinimalValidSourceBuilder()
                                .setAppDestinations(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                                .setWebDestinations(
                                        SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                                .setAdIdPermission(true)
                                .setDebugKey(SourceFixture.ValidSourceParams.DEBUG_KEY)
                                .build());
        commonTestDebugKeyPresenceInFakeReport(source, SourceFixture.ValidSourceParams.DEBUG_KEY);
    }

    @Test
    public void insertSource_webSourceWithArDebugPermission_fakeReportHasDebugKey()
            throws DatastoreException {
        // Setup
        int fakeReportsCount = 2;
        Source source =
                spy(
                        SourceFixture.getMinimalValidSourceBuilder()
                                .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                                .setPublisherType(EventSurfaceType.WEB)
                                .setAppDestinations(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                                .setWebDestinations(
                                        SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                                .setArDebugPermission(true)
                                .setDebugKey(SourceFixture.ValidSourceParams.DEBUG_KEY)
                                .build());
        commonTestDebugKeyPresenceInFakeReport(source, SourceFixture.ValidSourceParams.DEBUG_KEY);
    }

    @Test
    public void insertSource_appSourceHasArDebugButNotAdIdPermission_fakeReportHasNoDebugKey()
            throws DatastoreException {
        // Setup
        int fakeReportsCount = 2;
        Source source =
                spy(
                        SourceFixture.getMinimalValidSourceBuilder()
                                .setAppDestinations(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                                .setWebDestinations(
                                        SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                                .setAdIdPermission(false)
                                .setArDebugPermission(true)
                                .setDebugKey(SourceFixture.ValidSourceParams.DEBUG_KEY)
                                .build());
        commonTestDebugKeyPresenceInFakeReport(source, null);
    }

    @Test
    public void insertSource_webSourceHasAdIdButNotArDebugPermission_fakeReportHasNoDebugKey()
            throws DatastoreException {
        // Setup
        Source source =
                spy(
                        SourceFixture.getMinimalValidSourceBuilder()
                                .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                                .setPublisherType(EventSurfaceType.WEB)
                                .setAppDestinations(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                                .setWebDestinations(
                                        SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                                .setAdIdPermission(true)
                                .setArDebugPermission(false)
                                .setDebugKey(SourceFixture.ValidSourceParams.DEBUG_KEY)
                                .build());
        commonTestDebugKeyPresenceInFakeReport(source, null);
    }

    @Test
    public void insertSource_withFakeReportsNeverAppAttribution_accountsForFakeReportAttribution()
            throws DatastoreException {
        // Setup
        Source source =
                spy(
                        SourceFixture.getMinimalValidSourceBuilder()
                                .setAppDestinations(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                                .setWebDestinations(null)
                                .build());
        List<Source.FakeReport> fakeReports = Collections.emptyList();
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();
        Answer<?> neverAttributionAnswer =
                (arg) -> {
                    source.setAttributionMode(Source.AttributionMode.NEVER);
                    return fakeReports;
                };
        doAnswer(neverAttributionAnswer)
                .when(mSourceNoiseHandler)
                .assignAttributionModeAndGenerateFakeReports(source);
        ArgumentCaptor<Attribution> attributionRateLimitArgCaptor =
                ArgumentCaptor.forClass(Attribution.class);

        // Execution
        asyncRegistrationQueueRunner.insertSourceFromTransaction(source, mMeasurementDao);

        // Assertion
        verify(mMeasurementDao).insertSource(source);
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao).insertAttribution(attributionRateLimitArgCaptor.capture());

        assertEquals(
                new Attribution.Builder()
                        .setSourceId(DEFAULT_SOURCE_ID)
                        .setDestinationOrigin(source.getAppDestinations().get(0).toString())
                        .setDestinationSite(source.getAppDestinations().get(0).toString())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setSourceOrigin(source.getPublisher().toString())
                        .setSourceSite(source.getPublisher().toString())
                        .setRegistrant(source.getRegistrant().toString())
                        .setTriggerTime(source.getEventTime())
                        .setRegistrationOrigin(source.getRegistrationOrigin())
                        .build(),
                attributionRateLimitArgCaptor.getValue());
    }

    @Test
    public void testRegister_registrationTypeSource_sourceFetchSuccess() throws DatastoreException {
        // setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        // Execution
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        SOURCE_1,
                        SOURCE_1.getPublisher(),
                        EventSurfaceType.APP,
                        mMeasurementDao,
                        mDebugReportApi);

        // Assertions
        assertTrue(status);
        verify(mMeasurementDao, times(2))
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(2))
                .countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong());
        verify(mMeasurementDao, times(2))
                .countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
    }

    @Test
    public void testRegister_registrationTypeSource_exceedsPrivacyParam_destination()
            throws DatastoreException {
        // setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        // Execution
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(100));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        SOURCE_1,
                        SOURCE_1.getPublisher(),
                        EventSurfaceType.APP,
                        mMeasurementDao,
                        mDebugReportApi);

        // Assert
        assertFalse(status);
        verify(mMeasurementDao, times(2))
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(1))
                .countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
    }

    @Test
    public void testRegister_registrationTypeSource_exceedsDestinationRateLimit()
            throws DatastoreException {
        // setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        // Execution
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                        any(), anyInt(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(500));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        SOURCE_1,
                        SOURCE_1.getPublisher(),
                        EventSurfaceType.APP,
                        mMeasurementDao,
                        mDebugReportApi);

        // Assert
        assertFalse(status);
        verify(mMeasurementDao, times(1))
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(1)).countDistinctDestinationsPerPublisherPerRateLimitWindow(
                any(), anyInt(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
    }

    @Test
    public void testRegister_registrationTypeSource_exceedsDestinationReportingRateLimit()
            throws DatastoreException {
        // setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        // Execution
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(500));
        when(mMeasurementDao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                        any(), anyInt(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        SOURCE_1,
                        SOURCE_1.getPublisher(),
                        EventSurfaceType.APP,
                        mMeasurementDao,
                        mDebugReportApi);

        // Assert
        assertFalse(status);
        verify(mMeasurementDao, times(1))
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(1)).countDistinctDestinationsPerPublisherPerRateLimitWindow(
                any(), anyInt(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
    }

    @Test
    public void testRegister_registrationTypeSource_exceedsOneOriginPerPublisherXEnrollmentLimit()
            throws DatastoreException {
        // setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        // Execution
        when(mMeasurementDao.countSourcesPerPublisherXEnrollmentExcludingRegOrigin(
                        any(), any(), anyInt(), any(), anyLong(), anyLong()))
                .thenReturn(3);
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        SOURCE_1,
                        SOURCE_1.getPublisher(),
                        EventSurfaceType.APP,
                        mMeasurementDao,
                        mDebugReportApi);

        // Assert
        assertFalse(status);
        verify(mMeasurementDao, times(1))
                .countSourcesPerPublisherXEnrollmentExcludingRegOrigin(
                        any(), any(), anyInt(), any(), anyLong(), anyLong());
    }

    @Test
    public void testRegister_registrationTypeSource_exceedsMaxSourcesLimit()
            throws DatastoreException {
        // setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        // Execution
        doReturn((long) Flags.MEASUREMENT_MAX_SOURCES_PER_PUBLISHER)
                .when(mMeasurementDao)
                .getNumSourcesPerPublisher(any(), anyInt());
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        SOURCE_1,
                        SOURCE_1.getPublisher(),
                        EventSurfaceType.APP,
                        mMeasurementDao,
                        mDebugReportApi);

        // Assert
        assertFalse(status);
        verify(mMeasurementDao, times(1)).getNumSourcesPerPublisher(any(), anyInt());
    }

    @Test
    public void testRegister_registrationTypeSource_exceedsPrivacyParam_adTech()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();
        // Execution
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(100));
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        SOURCE_1,
                        SOURCE_1.getPublisher(),
                        EventSurfaceType.APP,
                        mMeasurementDao,
                        mDebugReportApi);

        // Assert
        assertFalse(status);
        verify(mMeasurementDao, times(2))
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(1))
                .countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong());
        verify(mMeasurementDao, times(1))
                .countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
    }

    @Test
    public void testRegisterWebSource_exceedsPrivacyParam_destination()
            throws RemoteException, DatastoreException {
        // setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        // Execution
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(100));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        SOURCE_1,
                        SOURCE_1.getPublisher(),
                        EventSurfaceType.APP,
                        mMeasurementDao,
                        mDebugReportApi);

        // Assert
        assertFalse(status);
        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mMeasurementDao, times(2))
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(1))
                .countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
    }

    @Test
    public void testRegisterWebSource_exceedsPrivacyParam_adTech() throws DatastoreException {
        // setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        // Execution
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(100));

        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        SOURCE_1,
                        SOURCE_1.getPublisher(),
                        EventSurfaceType.APP,
                        mMeasurementDao,
                        mDebugReportApi);

        // Assert
        assertFalse(status);
        verify(mMeasurementDao, times(2))
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(1))
                .countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong());
        verify(mMeasurementDao, times(1))
                .countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
    }

    @Test
    public void testRegisterWebSource_exceedsMaxSourcesLimit() throws DatastoreException {
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();
        doReturn((long) Flags.MEASUREMENT_MAX_SOURCES_PER_PUBLISHER)
                .when(mMeasurementDao)
                .getNumSourcesPerPublisher(any(), anyInt());

        // Execution
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        SOURCE_1,
                        SOURCE_1.getPublisher(),
                        EventSurfaceType.APP,
                        mMeasurementDao,
                        mDebugReportApi);

        // Assertions
        assertFalse(status);
    }

    @Test
    public void testRegisterWebSource_LimitsMaxSources_ForWebPublisher_WitheTLDMatch()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        doReturn((long) Flags.MEASUREMENT_MAX_SOURCES_PER_PUBLISHER)
                .when(mMeasurementDao)
                .getNumSourcesPerPublisher(any(), anyInt());

        // Execution
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        SOURCE_1,
                        SOURCE_1.getPublisher(),
                        EventSurfaceType.APP,
                        mMeasurementDao,
                        mDebugReportApi);

        // Assertions
        assertFalse(status);
    }

    @Test
    public void testRegisterTrigger_belowSystemHealthLimits_success() throws Exception {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        when(mMeasurementDao.getNumTriggersPerDestination(APP_DESTINATION, EventSurfaceType.APP))
                .thenReturn(0L);

        Truth.assertThat(
                        AsyncRegistrationQueueRunner.isTriggerAllowedToInsert(
                                mMeasurementDao, TRIGGER))
                .isTrue();
    }

    @Test
    public void testRegisterTrigger_atSystemHealthLimits_success() throws Exception {
        when(mMeasurementDao.getNumTriggersPerDestination(APP_DESTINATION, EventSurfaceType.APP))
                .thenReturn(Flags.MEASUREMENT_MAX_TRIGGERS_PER_DESTINATION - 1L);

        Truth.assertThat(
                        AsyncRegistrationQueueRunner.isTriggerAllowedToInsert(
                                mMeasurementDao, TRIGGER))
                .isTrue();
    }

    @Test
    public void testRegisterTrigger_overSystemHealthLimits_failure() throws Exception {
        when(mMeasurementDao.getNumTriggersPerDestination(APP_DESTINATION, EventSurfaceType.APP))
                .thenReturn((long) Flags.MEASUREMENT_MAX_TRIGGERS_PER_DESTINATION);

        Truth.assertThat(
                        AsyncRegistrationQueueRunner.isTriggerAllowedToInsert(
                                mMeasurementDao, TRIGGER))
                .isFalse();
    }

    @Test
    @SpyStatic(ErrorLogUtil.class)
    public void testRegisterWebSource_failsWebAndOsDestinationVerification()
            throws DatastoreException, IOException {
        // Setup
        doNothingOnErrorLogUtilError();
        AsyncSourceFetcher mFetcher = spy(new AsyncSourceFetcher(sContext, mEnrollmentDao, mFlags));
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(DEFAULT_REGISTRATION_PARAM_LIST),
                        WEB_TOP_ORIGIN.toString(),
                        DEFAULT_OS_DESTINATION,
                        DEFAULT_WEB_DESTINATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + ALT_APP_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "\"web_destination\": \""
                                                + ALT_WEB_DESTINATION
                                                + "\""
                                                + "}")));
        DatastoreManager datastoreManager =
                spy(
                        new SQLDatastoreManager(
                                DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger));
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                sContext,
                                mContentResolver,
                                mFetcher,
                                mAsyncTriggerFetcher,
                                datastoreManager,
                                mDebugReportApi,
                                mSourceNoiseHandler,
                                mFlags,
                                mLogger));
        ArgumentCaptor<DatastoreManager.ThrowingCheckedConsumer> consumerArgCaptor =
                ArgumentCaptor.forClass(DatastoreManager.ThrowingCheckedConsumer.class);
        EnqueueAsyncRegistration.webSourceRegistrationRequest(
                request,
                DEFAULT_AD_ID_PERMISSION,
                APP_TOP_ORIGIN,
                100,
                Source.SourceType.NAVIGATION,
                datastoreManager,
                mContentResolver);

        // Execution
        ProcessingResult result = asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // Assertions
        assertEquals(ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(datastoreManager, times(2)).runInTransaction(consumerArgCaptor.capture());
        consumerArgCaptor.getValue().accept(mMeasurementDao);
        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.SourceContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {
            Assert.assertFalse(cursor.moveToNext());
        }
        verifyErrorLogUtilError(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_INVALID,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
    }

    @Test
    public void isSourceAllowedToInsert_flexEventApiValidNav_pass()
            throws DatastoreException, JSONException {
        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        // setup
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3, 4],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2]}]\n";
        TriggerSpec[] triggerSpecsArray = TriggerSpecsUtil.triggerSpecArrayFrom(triggerSpecsString);
        int maxEventLevelReports = 2;
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .setPublisher(APP_TOP_ORIGIN)
                        .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                        .setWebDestinations(
                                List.of(WebUtil.validUri("https://web-destination1.test")))
                        .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                        .setRegistrant(Uri.parse("android-app://com.example"))
                        .setEventTime(new Random().nextLong())
                        .setExpiryTime(8640000010L)
                        .setPriority(100L)
                        // Navigation and Event source has different maximum information gain
                        // threshold
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setDebugKey(new UnsignedLong(47823478789L))
                        .setMaxEventLevelReports(maxEventLevelReports)
                        .setTriggerSpecs(new TriggerSpecs(
                                triggerSpecsArray, maxEventLevelReports, null))
                        .build();

        // setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        // Execution
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        testSource,
                        testSource.getPublisher(),
                        EventSurfaceType.APP,
                        mMeasurementDao,
                        mDebugReportApi);
        // Assert
        assertTrue(status);
    }

    @Test
    public void isSourceAllowedToInsert_flexEventApiInvalidEventExceedMaxInfoGain_fail()
            throws DatastoreException, JSONException {
        // setup
        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3, 4, 5, 6, 7, 8],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}]\n";
        TriggerSpec[] triggerSpecsArray = TriggerSpecsUtil.triggerSpecArrayFrom(triggerSpecsString);
        int maxEventLevelReports = 3;
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .setPublisher(APP_TOP_ORIGIN)
                        .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                        .setWebDestinations(
                                List.of(WebUtil.validUri("https://web-destination1.test")))
                        .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                        .setRegistrant(Uri.parse("android-app://com.example"))
                        .setEventTime(new Random().nextLong())
                        .setExpiryTime(8640000010L)
                        .setPriority(100L)
                        // Navigation and Event source has different maximum information gain
                        // threshold
                        .setSourceType(Source.SourceType.EVENT)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setDebugKey(new UnsignedLong(47823478789L))
                        .setMaxEventLevelReports(maxEventLevelReports)
                        .setTriggerSpecs(new TriggerSpecs(
                                triggerSpecsArray, maxEventLevelReports, null))
                        .build();

        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        // Execution
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        testSource,
                        testSource.getPublisher(),
                        EventSurfaceType.APP,
                        mMeasurementDao,
                        mDebugReportApi);
        // Assert
        assertFalse(status);
    }

    @Test
    public void isSourceAllowedToInsert_flexLiteApiExceedMaxInfoGain_fail()
            throws DatastoreException {
        // setup
        when(mFlags.getMeasurementFlexLiteApiEnabled()).thenReturn(true);
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .setPublisher(APP_TOP_ORIGIN)
                        .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                        .setWebDestinations(
                                List.of(WebUtil.validUri("https://web-destination1.test")))
                        .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                        .setRegistrant(Uri.parse("android-app://com.example"))
                        .setEventTime(new Random().nextLong())
                        .setExpiryTime(8640000010L)
                        .setPriority(100L)
                        // Navigation and Event source has different maximum information gain
                        // threshold
                        .setSourceType(Source.SourceType.EVENT)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setDebugKey(new UnsignedLong(47823478789L))
                        .setMaxEventLevelReports(3)
                        .setEventReportWindows(
                                "{ 'end_times': [3600, 7200, 14400, 28800, 57600, 115200]}")
                        .build();

        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        // Execution
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        testSource,
                        testSource.getPublisher(),
                        EventSurfaceType.APP,
                        mMeasurementDao,
                        mDebugReportApi);
        // Assert
        assertFalse(status);
    }

    @Test
    public void isSourceAllowedToInsert_flexLiteApiExceedMaxInfoGain_pass()
            throws DatastoreException {
        // setup
        when(mFlags.getMeasurementFlexLiteApiEnabled()).thenReturn(true);
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .setPublisher(APP_TOP_ORIGIN)
                        .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                        .setWebDestinations(
                                List.of(WebUtil.validUri("https://web-destination1.test")))
                        .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                        .setRegistrant(Uri.parse("android-app://com.example"))
                        .setEventTime(new Random().nextLong())
                        .setExpiryTime(8640000010L)
                        .setPriority(100L)
                        // Navigation and Event source has different maximum information gain
                        // threshold
                        .setSourceType(Source.SourceType.EVENT)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setDebugKey(new UnsignedLong(47823478789L))
                        .setMaxEventLevelReports(1)
                        .setEventReportWindows("{ 'end_times': [3600]}")
                        .build();

        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        // Execution
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        testSource,
                        testSource.getPublisher(),
                        EventSurfaceType.APP,
                        mMeasurementDao,
                        mDebugReportApi);
        // Assert
        assertTrue(status);
    }

    @Test
    public void isSourceAllowedToInsert_flexEventApiValidV1ParamsNavExceedMaxInfoGain_fail()
            throws DatastoreException, JSONException {
        // setup
        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3, 4, 5, 6, 7, 8],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(14),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 5]}]\n";
        TriggerSpec[] triggerSpecsArray = TriggerSpecsUtil.triggerSpecArrayFrom(triggerSpecsString);
        int maxEventLevelReports = 4;
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .setPublisher(APP_TOP_ORIGIN)
                        .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                        .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                        .setRegistrant(Uri.parse("android-app://com.example"))
                        .setEventTime(new Random().nextLong())
                        .setExpiryTime(8640000010L)
                        .setPriority(100L)
                        // Navigation and Event source has different maximum information gain
                        // threshold
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setDebugKey(new UnsignedLong(47823478789L))
                        .setMaxEventLevelReports(maxEventLevelReports)
                        .setTriggerSpecs(new TriggerSpecs(
                                triggerSpecsArray, maxEventLevelReports, null))
                        .build();

        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        // Execution
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        testSource,
                        testSource.getPublisher(),
                        EventSurfaceType.APP,
                        mMeasurementDao,
                        mDebugReportApi);
        // Assert
        assertFalse(status);
    }

    @Test
    public void isSourceAllowedToInsert_flexEventApiValidV1NavNearBoundaryDualDestination_fail()
            throws DatastoreException, JSONException {
        // setup
        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3, 4, 5, 6, 7, 8],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}]\n";
        TriggerSpec[] triggerSpecsArray = TriggerSpecsUtil.triggerSpecArrayFrom(triggerSpecsString);
        int maxEventLevelReports = 3;
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .setPublisher(APP_TOP_ORIGIN)
                        .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                        .setWebDestinations(
                                List.of(WebUtil.validUri("https://web-destination1.test")))
                        .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                        .setRegistrant(Uri.parse("android-app://com.example"))
                        .setEventTime(new Random().nextLong())
                        .setExpiryTime(8640000010L)
                        .setPriority(100L)
                        // Navigation and Event source has different maximum information gain
                        // threshold
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setDebugKey(new UnsignedLong(47823478789L))
                        .setMaxEventLevelReports(maxEventLevelReports)
                        .setTriggerSpecs(new TriggerSpecs(
                                triggerSpecsArray, maxEventLevelReports, null))
                        .build();

        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        // Execution
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        testSource,
                        testSource.getPublisher(),
                        EventSurfaceType.APP,
                        mMeasurementDao,
                        mDebugReportApi);
        // Assert
        assertFalse(status);
    }

    @Test
    public void isSourceAllowedToInsert_flexEventApiValidV1NavNearBoundary_pass()
            throws DatastoreException, JSONException {
        // setup
        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3, 4, 5, 6, 7, 8],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}]\n";
        TriggerSpec[] triggerSpecsArray = TriggerSpecsUtil.triggerSpecArrayFrom(triggerSpecsString);
        int maxEventLevelReports = 3;
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .setPublisher(APP_TOP_ORIGIN)
                        .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                        .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                        .setRegistrant(Uri.parse("android-app://com.example"))
                        .setEventTime(new Random().nextLong())
                        .setExpiryTime(8640000010L)
                        .setPriority(100L)
                        // Navigation and Event source has different maximum information gain
                        // threshold
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setDebugKey(new UnsignedLong(47823478789L))
                        .setMaxEventLevelReports(maxEventLevelReports)
                        .setTriggerSpecs(new TriggerSpecs(
                                triggerSpecsArray, maxEventLevelReports, null))
                        .build();

        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        // Execution
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        testSource,
                        testSource.getPublisher(),
                        EventSurfaceType.APP,
                        mMeasurementDao,
                        mDebugReportApi);
        // Assert
        assertTrue(status);
    }

    @Test
    public void isSourceAllowedToInsert_flexEventApiV1ParamEventNearBoundary_pass()
            throws DatastoreException, JSONException {
        // setup
        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format("\"end_times\": [%s]}, ", TimeUnit.DAYS.toSeconds(7))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1]}]\n";
        TriggerSpec[] triggerSpecsArray = TriggerSpecsUtil.triggerSpecArrayFrom(triggerSpecsString);
        int maxEventLevelReports = 1;
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .setPublisher(APP_TOP_ORIGIN)
                        .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                        .setWebDestinations(
                                List.of(WebUtil.validUri("https://web-destination1.test")))
                        .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                        .setRegistrant(Uri.parse("android-app://com.example"))
                        .setEventTime(new Random().nextLong())
                        .setExpiryTime(8640000010L)
                        .setPriority(100L)
                        // Navigation and Event source has different maximum information gain
                        // threshold
                        .setSourceType(Source.SourceType.EVENT)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setDebugKey(new UnsignedLong(47823478789L))
                        .setMaxEventLevelReports(maxEventLevelReports)
                        .setTriggerSpecs(new TriggerSpecs(
                                triggerSpecsArray, maxEventLevelReports, null))
                        .build();
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        // Execution
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        testSource,
                        testSource.getPublisher(),
                        EventSurfaceType.APP,
                        mMeasurementDao,
                        mDebugReportApi);
        // Assert
        assertTrue(status);
    }

    private static KeyValueData getKeyValueDataRedirectCount() {
        return new KeyValueData.Builder()
                .setDataType(KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT)
                .setKey(AsyncRegistrationFixture.ValidAsyncRegistrationParams.REGISTRATION_ID)
                .setValue(null) // Should default to 1
                .build();
    }

    private Map<String, List<String>> getRedirectHeaders(
            AsyncRegistration.RedirectType redirectType, List<String> uris) {
        Map<String, List<String>> headers = new HashMap<>();
        if (redirectType.equals(AsyncRegistration.RedirectType.LOCATION)) {
            headers.put(AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY, uris);
        } else {
            headers.put(AsyncRedirects.REDIRECT_LIST_HEADER_KEY, uris);
        }

        return headers;
    }

    private void assertRepeatedAsyncRegistration(
            ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor,
            int index,
            Uri redirectUri) {
        AsyncRegistration asyncReg = asyncRegistrationArgumentCaptor.getAllValues().get(index);
        Assert.assertEquals(redirectUri, asyncReg.getRegistrationUri());
    }

    private Answer<Optional<Source>> getAsyncSourceAnswerForLocationTypeRedirectToWellKnown(
            String redirectUri, AsyncRegistration asyncRegistration) {
        Map<String, List<String>> redirectHeaders =
                getRedirectHeaders(AsyncRegistration.RedirectType.LOCATION, List.of(redirectUri));
        redirectHeaders.put(
                AsyncRedirects.HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG,
                List.of(AsyncRedirects.REDIRECT_302_TO_WELL_KNOWN));
        return invocation -> {
            AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
            asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
            AsyncRedirects asyncRedirects = invocation.getArgument(2);
            asyncRedirects.configure(redirectHeaders, mFlags, asyncRegistration);
            return Optional.of(mMockedSource);
        };
    }

    private Uri getRegistrationRedirectToWellKnownUri(
            Uri registrationUri, String originalUriString) {
        return registrationUri
                .buildUpon()
                .encodedPath(AsyncRedirects.WELL_KNOWN_PATH_SEGMENT)
                .appendQueryParameter(AsyncRedirects.WELL_KNOWN_QUERY_PARAM, originalUriString)
                .build();
    }

    private WebSourceRegistrationRequest buildWebSourceRegistrationRequest(
            List<WebSourceParams> sourceParamsList,
            String topOrigin,
            Uri appDestination,
            Uri webDestination) {
        WebSourceRegistrationRequest.Builder webSourceRegistrationRequestBuilder =
                new WebSourceRegistrationRequest.Builder(sourceParamsList, Uri.parse(topOrigin))
                        .setAppDestination(appDestination);
        if (webDestination != null) {
            webSourceRegistrationRequestBuilder.setWebDestination(webDestination);
        }
        return webSourceRegistrationRequestBuilder.build();
    }

    private List<Source.FakeReport> createFakeReports(
            Source source, int count, List<Uri> destinations) {
        return IntStream.range(0, count)
                .mapToObj(
                        x ->
                                new Source.FakeReport(
                                        new UnsignedLong(0L),
                                        new EventReportWindowCalcDelegate(mFlags)
                                                .getReportingTimeForNoising(source, 0),
                                        destinations))
                .collect(Collectors.toList());
    }

    private static AsyncRegistration createAsyncRegistrationForAppSource() {
        return new AsyncRegistration.Builder()
                .setId(UUID.randomUUID().toString())
                .setRegistrationUri(REGISTRATION_URI)
                // null .setWebDestination(webDestination)
                // null .setOsDestination(osDestination)
                .setRegistrant(DEFAULT_REGISTRANT)
                // null .setVerifiedDestination(null)
                .setTopOrigin(APP_TOP_ORIGIN)
                .setType(AsyncRegistration.RegistrationType.APP_SOURCE)
                .setSourceType(Source.SourceType.EVENT)
                .setRequestTime(System.currentTimeMillis())
                .setRetryCount(0)
                .setDebugKeyAllowed(true)
                .setRegistrationId(
                        AsyncRegistrationFixture.ValidAsyncRegistrationParams.REGISTRATION_ID)
                .build();
    }

    private static AsyncRegistration createAsyncRegistrationForAppTrigger() {
        return new AsyncRegistration.Builder()
                .setId(UUID.randomUUID().toString())
                .setRegistrationUri(REGISTRATION_URI)
                // null .setWebDestination(webDestination)
                // null .setOsDestination(osDestination)
                .setRegistrant(DEFAULT_REGISTRANT)
                // null .setVerifiedDestination(null)
                .setTopOrigin(APP_TOP_ORIGIN)
                .setType(AsyncRegistration.RegistrationType.APP_TRIGGER)
                // null .setSourceType(null)
                .setRequestTime(System.currentTimeMillis())
                .setRetryCount(0)
                .setDebugKeyAllowed(true)
                .setRegistrationId(
                        AsyncRegistrationFixture.ValidAsyncRegistrationParams.REGISTRATION_ID)
                .build();
    }

    private static AsyncRegistration createAsyncRegistrationForWebSource() {
        return new AsyncRegistration.Builder()
                .setId(UUID.randomUUID().toString())
                .setRegistrationUri(REGISTRATION_URI)
                .setWebDestination(WEB_DESTINATION)
                .setOsDestination(APP_DESTINATION)
                .setRegistrant(DEFAULT_REGISTRANT)
                .setVerifiedDestination(DEFAULT_VERIFIED_DESTINATION)
                .setTopOrigin(WEB_TOP_ORIGIN)
                .setType(AsyncRegistration.RegistrationType.WEB_SOURCE)
                .setSourceType(Source.SourceType.EVENT)
                .setRequestTime(System.currentTimeMillis())
                .setRetryCount(0)
                .setDebugKeyAllowed(true)
                .setRegistrationId(
                        AsyncRegistrationFixture.ValidAsyncRegistrationParams.REGISTRATION_ID)
                .build();
    }

    private static AsyncRegistration createAsyncRegistrationForWebTrigger() {
        return new AsyncRegistration.Builder()
                .setId(UUID.randomUUID().toString())
                .setRegistrationUri(REGISTRATION_URI)
                // null .setWebDestination(webDestination)
                // null .setOsDestination(osDestination)
                .setRegistrant(DEFAULT_REGISTRANT)
                // null .setVerifiedDestination(null)
                .setTopOrigin(WEB_TOP_ORIGIN)
                .setType(AsyncRegistration.RegistrationType.WEB_TRIGGER)
                // null .setSourceType(null)
                .setRequestTime(System.currentTimeMillis())
                .setRetryCount(0)
                .setDebugKeyAllowed(true)
                .setRegistrationId(
                        AsyncRegistrationFixture.ValidAsyncRegistrationParams.REGISTRATION_ID)
                .build();
    }

    private AsyncRegistrationQueueRunner getSpyAsyncRegistrationQueueRunner() {
        return spy(
                new AsyncRegistrationQueueRunner(
                        sContext,
                        mContentResolver,
                        mAsyncSourceFetcher,
                        mAsyncTriggerFetcher,
                        new FakeDatastoreManager(),
                        mDebugReportApi,
                        mSourceNoiseHandler,
                        mFlags,
                        mLogger));
    }

    private void commonTestDebugKeyPresenceInFakeReport(
            Source source, UnsignedLong expectedSourceDebugKey) throws DatastoreException {
        int fakeReportsCount = 2;
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        List<Source.FakeReport> fakeReports =
                createFakeReports(
                        source,
                        fakeReportsCount,
                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS);

        Answer<?> falseAttributionAnswer =
                (arg) -> {
                    source.setAttributionMode(Source.AttributionMode.FALSELY);
                    return fakeReports;
                };
        doAnswer(falseAttributionAnswer)
                .when(mSourceNoiseHandler)
                .assignAttributionModeAndGenerateFakeReports(source);
        ArgumentCaptor<EventReport> fakeEventReportCaptor =
                ArgumentCaptor.forClass(EventReport.class);

        // Execution
        asyncRegistrationQueueRunner.insertSourceFromTransaction(source, mMeasurementDao);

        // Assertion
        verify(mMeasurementDao).insertSource(source);
        verify(mMeasurementDao, times(2)).insertEventReport(fakeEventReportCaptor.capture());
        assertEquals(2, fakeEventReportCaptor.getAllValues().size());
        fakeEventReportCaptor
                .getAllValues()
                .forEach(
                        (report) -> {
                            assertEquals(expectedSourceDebugKey, report.getSourceDebugKey());
                            assertNull(report.getTriggerDebugKey());
                        });
    }

    private void setUpApplicationStatus(List<String> installedApps, List<String> notInstalledApps)
            throws PackageManager.NameNotFoundException {
        for (String packageName : installedApps) {
            ApplicationInfo installedApplicationInfo = new ApplicationInfo();
            installedApplicationInfo.packageName = packageName;
            when(mPackageManager.getApplicationInfo(packageName, 0))
                    .thenReturn(installedApplicationInfo);
        }
        for (String packageName : notInstalledApps) {
            when(mPackageManager.getApplicationInfo(packageName, 0))
                    .thenThrow(new PackageManager.NameNotFoundException());
        }
    }

    private static void emptyTables(SQLiteDatabase db) {
        db.delete("msmt_source", null, null);
        db.delete("msmt_trigger", null, null);
        db.delete("msmt_event_report", null, null);
        db.delete("msmt_attribution", null, null);
        db.delete("msmt_aggregate_report", null, null);
        db.delete("msmt_async_registration_contract", null, null);
    }

    private MeasurementRegistrationResponseStats getMeasurementRegistrationResponseStats() {
        ArgumentCaptor<MeasurementRegistrationResponseStats> statsArg =
                ArgumentCaptor.forClass(MeasurementRegistrationResponseStats.class);
        verify(mLogger).logMeasurementRegistrationsResponseSize(statsArg.capture());
        MeasurementRegistrationResponseStats measurementRegistrationResponseStats =
                statsArg.getValue();
        return measurementRegistrationResponseStats;
    }
}
