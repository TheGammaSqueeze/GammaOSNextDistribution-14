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

package com.android.adservices.service.measurement.attribution;

import static com.android.adservices.service.Flags.MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION;
import static com.android.adservices.service.Flags.MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS;
import static com.android.adservices.service.Flags.MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.android.adservices.service.measurement.util.Time.roundDownToDay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.Uri;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.LogUtil;
import com.android.adservices.common.WebUtil;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.data.measurement.ITransaction;
import com.android.adservices.service.AdServicesConfig;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.Attribution;
import com.android.adservices.service.measurement.AttributionConfig;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.PrivacyParams;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerFixture;
import com.android.adservices.service.measurement.TriggerSpecs;
import com.android.adservices.service.measurement.aggregation.AggregateAttributionData;
import com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.noising.SourceNoiseHandler;
import com.android.adservices.service.measurement.reporting.DebugReportApi;
import com.android.adservices.service.measurement.reporting.EventReportWindowCalcDelegate;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.MeasurementAttributionStats;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Unit test for {@link AttributionJobHandler}
 */
@RunWith(MockitoJUnitRunner.class)
public class AttributionJobHandlerTest {
    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    private static final long SOURCE_TIME = 1690000000000L;
    private static final long TRIGGER_TIME = 1690000001000L;
    private static final long EXPIRY_TIME = 1692592000000L;
    private static final long LOOKBACK_WINDOW = 1000L;
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Uri APP_DESTINATION = Uri.parse("android-app://com.example.app");
    private static final Uri WEB_DESTINATION = WebUtil.validUri("https://web.example.test");
    private static final Uri PUBLISHER = Uri.parse("android-app://publisher.app");
    private static final Uri REGISTRATION_URI = WebUtil.validUri("https://subdomain.example.test");
    private static final UnsignedLong SOURCE_DEBUG_KEY = new UnsignedLong(111111L);
    private static final UnsignedLong TRIGGER_DEBUG_KEY = new UnsignedLong(222222L);
    private static final String TRIGGER_ID = "triggerId1";
    private static final String EVENT_TRIGGERS =
            "[\n"
                    + "{\n"
                    + "  \"trigger_data\": \"5\",\n"
                    + "  \"priority\": \"123\",\n"
                    + "  \"deduplication_key\": \"2\",\n"
                    + "  \"filters\": [{\n"
                    + "    \"source_type\": [\"event\"],\n"
                    + "    \"key_1\": [\"value_1\"] \n"
                    + "   }]\n"
                    + "}"
                    + "]\n";

    private static final String AGGREGATE_DEDUPLICATION_KEYS_1 =
            "[{\"deduplication_key\": \"" + 10 + "\"" + " }" + "]";

    private static Trigger createAPendingTriggerEventScopeOnly() {
        return TriggerFixture.getValidTriggerBuilder()
                .setId(TRIGGER_ID)
                .setTriggerTime(TRIGGER_TIME)
                .setStatus(Trigger.Status.PENDING)
                .setEventTriggers(EVENT_TRIGGERS)
                .build();
    }

    DatastoreManager mDatastoreManager;

    AttributionJobHandler mHandler;

    EventReportWindowCalcDelegate mEventReportWindowCalcDelegate;

    SourceNoiseHandler mSourceNoiseHandler;

    @Mock
    IMeasurementDao mMeasurementDao;

    @Mock
    ITransaction mTransaction;

    @Mock Flags mFlags;

    @Mock AdServicesLogger mLogger;
    @Mock AdServicesErrorLogger mErrorLogger;
    @Mock DebugReportApi mDebugReportApi;

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

    @Before
    public void before() {
        mDatastoreManager = new FakeDatastoreManager();
        mEventReportWindowCalcDelegate = spy(new EventReportWindowCalcDelegate(mFlags));
        mSourceNoiseHandler = spy(new SourceNoiseHandler(mFlags));
        mLogger = spy(AdServicesLoggerImpl.getInstance());
        mHandler =
                new AttributionJobHandler(
                        mDatastoreManager,
                        mFlags,
                        mDebugReportApi,
                        mEventReportWindowCalcDelegate,
                        mSourceNoiseHandler,
                        mLogger,
                        new XnaSourceCreator(mFlags));
        when(mFlags.getMeasurementEnableXNA()).thenReturn(false);
        when(mFlags.getMeasurementEnableScopedAttributionRateLimit())
                .thenReturn(Flags.MEASUREMENT_ENABLE_SCOPED_ATTRIBUTION_RATE_LIMIT);
        when(mFlags.getMeasurementMaxAttributionPerRateLimitWindow())
                .thenReturn(Flags.MEASUREMENT_MAX_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
        when(mFlags.getMeasurementMaxEventAttributionPerRateLimitWindow())
                .thenReturn(Flags.MEASUREMENT_MAX_EVENT_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
        when(mFlags.getMeasurementMaxAggregateAttributionPerRateLimitWindow())
                .thenReturn(Flags.MEASUREMENT_MAX_AGGREGATE_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
        when(mFlags.getMeasurementMaxDistinctEnrollmentsInAttribution())
                .thenReturn(Flags.MEASUREMENT_MAX_DISTINCT_ENROLLMENTS_IN_ATTRIBUTION);
        when(mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()).thenReturn(true);
        when(mFlags.getMeasurementMaxAttributionsPerInvocation())
                .thenReturn(Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTIONS_PER_INVOCATION);
        when(mFlags.getMeasurementMaxEventReportsPerDestination())
                .thenReturn(Flags.MEASUREMENT_MAX_EVENT_REPORTS_PER_DESTINATION);
        when(mFlags.getMeasurementMaxAggregateReportsPerDestination())
                .thenReturn(MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION);
        when(mFlags.getMeasurementNullAggReportRateInclSourceRegistrationTime()).thenReturn(0f);
        when(mFlags.getMeasurementMinEventReportDelayMillis())
                .thenReturn(Flags.MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS);
        when(mFlags.getMeasurementVtcConfigurableMaxEventReportsCount())
                .thenReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT);
        when(mFlags.getMeasurementEventReportsVtcEarlyReportingWindows())
                .thenReturn(Flags.MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS);
        when(mFlags.getMeasurementEventReportsCtcEarlyReportingWindows())
                .thenReturn(Flags.MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS);
    }

    @Test
    public void shouldIgnoreNonPendingTrigger() throws DatastoreException {
        Trigger trigger = TriggerFixture.getValidTriggerBuilder()
                .setId("triggerId1")
                .setStatus(Trigger.Status.IGNORED).build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        mHandler.performPendingAttributions();
        verify(mMeasurementDao).getTrigger(trigger.getId());
        verify(mMeasurementDao, never()).updateTriggerStatus(any(), anyInt());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldIgnoreIfNoSourcesFound() throws DatastoreException {
        Trigger trigger = createAPendingTriggerEventScopeOnly();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(new ArrayList<>());
        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldRejectBasedOnDedupKey() throws DatastoreException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"source_type\": [\"event\"],\n"
                                        + "    \"key_1\": [\"value_1\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .build();
        String attributionStatus = getAttributionStatus(
                List.of("triggerId2", "triggerId3"),
                List.of("1", "2"),
                List.of("1", "2"));
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventAttributionStatus(attributionStatus)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldRejectBasedOnDedupKey_dedupAlignFlagOff() throws DatastoreException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"source_type\": [\"event\"],\n"
                                        + "    \"key_1\": [\"value_1\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportDedupKeys(
                                Arrays.asList(new UnsignedLong(1L), new UnsignedLong(2L)))
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()).thenReturn(false);
        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldNotCreateEventReportAfterEventReportWindow()
            throws DatastoreException {
        long eventTime = System.currentTimeMillis();
        long triggerTime = eventTime + TimeUnit.DAYS.toMillis(5);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[{\"trigger_data\":\"1\","
                                        + "\"filters\":[{\"source_type\": [\"event\"]}]"
                                        + "}]")
                        .setTriggerTime(triggerTime)
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source1")
                        .setEventId(new UnsignedLong(1L))
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(eventTime)
                        .setEventReportWindow(triggerTime - 1)
                        .build();
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldNotCreateEventReportAfterEventReportWindow_secondTrigger()
            throws DatastoreException, JSONException {
        long eventTime = System.currentTimeMillis();
        long triggerTime = eventTime + TimeUnit.DAYS.toMillis(5);
        Trigger trigger1 =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers("[{\"trigger_data\":\"1\"}]")
                        .setTriggerTime(triggerTime)
                        .build();
        Trigger trigger2 =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId2")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers("[{\"trigger_data\":\"0\"}]")
                        .setTriggerTime(triggerTime + 1L)
                        .build();
        List<Trigger> triggers = new ArrayList<>();
        triggers.add(trigger1);
        triggers.add(trigger2);
        List<Source> matchingSourceList = new ArrayList<>();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(eventTime)
                        .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
                        .setEventReportWindow(triggerTime + 1L)
                        .build();
        matchingSourceList.add(source);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Arrays.asList(trigger1.getId(), trigger2.getId()));
        when(mMeasurementDao.getTrigger(trigger1.getId())).thenReturn(trigger1);
        when(mMeasurementDao.getTrigger(trigger2.getId())).thenReturn(trigger2);
        when(mMeasurementDao.getMatchingActiveSources(trigger1)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getMatchingActiveSources(trigger2)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);

        assertEquals(
                AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED,
                mHandler.performPendingAttributions());
        // Verify trigger status updates.
        verify(mMeasurementDao).updateTriggerStatus(
                eq(Collections.singletonList(trigger1.getId())), eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao).updateTriggerStatus(
                eq(Collections.singletonList(trigger2.getId())), eq(Trigger.Status.IGNORED));
        // Verify new event report insertion.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao, times(1))
                .insertEventReport(reportArg.capture());
        List<EventReport> newReportArgs = reportArg.getAllValues();
        assertEquals(1, newReportArgs.size());
        assertEquals(
                newReportArgs.get(0).getTriggerData(),
                triggers.get(0).parseEventTriggers(mFlags).get(0).getTriggerData());
    }

    @Test
    public void shouldNotCreateEventReportAfterEventReportWindow_prioritisedSource()
            throws DatastoreException {
        String eventTriggers =
                "[{\"trigger_data\": \"5\","
                        + "\"priority\": \"123\","
                        + "\"filters\":[{\"key_1\":[\"value_1\"]}]"
                        + "}]";
        long eventTime = System.currentTimeMillis();
        long triggerTime = eventTime + TimeUnit.DAYS.toMillis(5);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(eventTriggers)
                        .setTriggerTime(triggerTime)
                        .build();
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source1")
                        .setPriority(100L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(eventTime)
                        .setEventReportWindow(triggerTime + 1L)
                        .build();
        // Second source has higher priority but the event report window ends before trigger time
        Source source2 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source2")
                        .setPriority(200L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(eventTime + 1000)
                        .setEventReportWindow(triggerTime - 1)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source1);
        matchingSourceList.add(source2);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        mHandler.performPendingAttributions();
        trigger.setStatus(Trigger.Status.ATTRIBUTED);
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
        assertEquals(1, matchingSourceList.size());
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldNotAddIfRateLimitExceeded_eventScopeOnly() throws DatastoreException {
        Trigger trigger = createAPendingTriggerEventScopeOnly();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any()))
                .thenReturn(
                        5L,
                        (long) Flags.MEASUREMENT_MAX_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
        mHandler.performPendingAttributions();
        verify(mMeasurementDao).getAttributionsPerRateLimitWindow(
                Attribution.Scope.EVENT, source, trigger);
        verify(mMeasurementDao).getAttributionsPerRateLimitWindow(
                Attribution.Scope.AGGREGATE, source, trigger);
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao, never()).insertAggregateReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldNotAddIfRateLimitExceeded_aggregateScopeOnly()
            throws DatastoreException, JSONException {
        Trigger trigger = getAggregateTrigger();
        Source source = getAggregateSource();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                Attribution.Scope.AGGREGATE, source, trigger)).thenReturn(
                        (long) Flags.MEASUREMENT_MAX_AGGREGATE_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                Attribution.Scope.EVENT, source, trigger)).thenReturn(
                        5L);
        mHandler.performPendingAttributions();
        verify(mMeasurementDao, times(1)).getAttributionsPerRateLimitWindow(
                Attribution.Scope.EVENT, source, trigger);
        verify(mMeasurementDao, times(1)).getAttributionsPerRateLimitWindow(
                Attribution.Scope.AGGREGATE, source, trigger);
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao, never()).insertAggregateReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldNotAddIfRateLimitExceeded_aggregateAndEventScope_eventLimited()
            throws DatastoreException, JSONException {
        Trigger trigger = getAggregateAndEventTrigger();
        Source source = getAggregateSource();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                Attribution.Scope.AGGREGATE, source, trigger)).thenReturn(
                        5L);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                Attribution.Scope.EVENT, source, trigger)).thenReturn(
                        (long) Flags.MEASUREMENT_MAX_EVENT_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
        mHandler.performPendingAttributions();
        verify(mMeasurementDao, times(1)).getAttributionsPerRateLimitWindow(
                Attribution.Scope.EVENT, source, trigger);
        verify(mMeasurementDao, times(1)).getAttributionsPerRateLimitWindow(
                Attribution.Scope.AGGREGATE, source, trigger);
        verify(mMeasurementDao).updateTriggerStatus(
                eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao, times(1)).insertAggregateReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldNotAddIfRateLimitExceeded_aggregateAndEventScope_aggregateLimited()
            throws DatastoreException, JSONException {
        Trigger trigger = getAggregateAndEventTrigger();
        Source source = getAggregateSource();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                Attribution.Scope.AGGREGATE, source, trigger)).thenReturn(
                        (long) Flags.MEASUREMENT_MAX_AGGREGATE_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                Attribution.Scope.EVENT, source, trigger)).thenReturn(
                        5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        mHandler.performPendingAttributions();
        verify(mMeasurementDao, times(1)).getAttributionsPerRateLimitWindow(
                Attribution.Scope.EVENT, source, trigger);
        verify(mMeasurementDao, times(1)).getAttributionsPerRateLimitWindow(
                Attribution.Scope.AGGREGATE, source, trigger);
        verify(mMeasurementDao).updateTriggerStatus(
                eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao, times(1)).insertEventReport(any());
        verify(mMeasurementDao, never()).insertAggregateReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldNotAddIfAdTechPrivacyBoundExceeded() throws DatastoreException {
        Trigger trigger = createAPendingTriggerEventScopeOnly();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestInAttribution(
                        any(), any(), any(), anyLong(), anyLong()))
                .thenReturn(10);
        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .countDistinctReportingOriginsPerPublisherXDestInAttribution(
                        any(), any(), any(), anyLong(), anyLong());
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performPendingAttributions_vtcWithConfiguredReportsCount_attributeUptoConfigLimit()
            throws DatastoreException {
        // Setup
        doReturn(3).when(mFlags).getMeasurementVtcConfigurableMaxEventReportsCount();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        doReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()))
                .when(mMeasurementDao)
                .getSourceDestinations(source.getId());
        // 2 event reports already present for the source
        doReturn(Arrays.asList(mock(EventReport.class), mock(EventReport.class)))
                .when(mMeasurementDao)
                .getSourceEventReports(source);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        Trigger trigger = createAPendingTriggerEventScopeOnly();
        doReturn(trigger).when(mMeasurementDao).getTrigger(trigger.getId());
        doReturn(matchingSourceList).when(mMeasurementDao).getMatchingActiveSources(trigger);
        doReturn(Collections.singletonList(trigger.getId()))
                .when(mMeasurementDao)
                .getPendingTriggerIds();

        // Execution
        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));

        // Assertion
        verify(mMeasurementDao, times(1)).insertEventReport(any());
    }

    @Test
    public void shouldIgnoreForMaxReportsPerSource() throws DatastoreException {
        Trigger trigger = createAPendingTriggerEventScopeOnly();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        EventReport eventReport1 = new EventReport.Builder()
                .setStatus(EventReport.Status.DELIVERED)
                .build();
        EventReport eventReport2 = new EventReport.Builder()
                .setStatus(EventReport.Status.DELIVERED)
                .build();
        EventReport eventReport3 = new EventReport.Builder().setStatus(
                EventReport.Status.DELIVERED).build();
        List<EventReport> matchingReports = new ArrayList<>();
        matchingReports.add(eventReport1);
        matchingReports.add(eventReport2);
        matchingReports.add(eventReport3);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        when(mMeasurementDao.getSourceEventReports(source)).thenReturn(matchingReports);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldNotReplaceHighPriorityReports() throws DatastoreException {
        String eventTriggers =
                "[\n"
                        + "{\n"
                        + "  \"trigger_data\": \"5\",\n"
                        + "  \"priority\": \"100\",\n"
                        + "  \"deduplication_key\": \"2\"\n"
                        + "}"
                        + "]\n";
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setEventTriggers(eventTriggers)
                        .setStatus(Trigger.Status.PENDING)
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        EventReport eventReport1 =
                new EventReport.Builder()
                        .setStatus(EventReport.Status.PENDING)
                        .setTriggerPriority(200L)
                        .build();
        EventReport eventReport2 = new EventReport.Builder()
                .setStatus(EventReport.Status.DELIVERED)
                .build();
        EventReport eventReport3 = new EventReport.Builder()
                .setStatus(EventReport.Status.DELIVERED)
                .build();
        List<EventReport> matchingReports = new ArrayList<>();
        matchingReports.add(eventReport1);
        matchingReports.add(eventReport2);
        matchingReports.add(eventReport3);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceEventReports(source)).thenReturn(matchingReports);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldDoSimpleAttribution() throws DatastoreException {
        Trigger trigger = createAPendingTriggerEventScopeOnly();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("2"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        verify(mMeasurementDao).insertEventReport(any());

        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());

        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldIgnoreIfNoSourcesFound_includeSourceRegistrationTime_dontTriggerNullReports()
            throws DatastoreException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(TRIGGER_ID)
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(EVENT_TRIGGERS)
                        .setAggregatableSourceRegistrationTimeConfig(
                                Trigger.SourceRegistrationTimeConfig.INCLUDE)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(new ArrayList<>());

        when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
        when(mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime()).thenReturn(1.0f);

        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
        verify(mMeasurementDao, never()).insertAggregateReport(any());
    }

    @Test
    public void shouldIgnoreIfNoSourcesFound_excludeSourceRegistrationTime_triggerNullReports()
            throws DatastoreException, JSONException {
        JSONArray triggerData = getAggregateTriggerData();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(TRIGGER_ID)
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(EVENT_TRIGGERS)
                        .setAggregateTriggerData(triggerData.toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .setAggregatableSourceRegistrationTimeConfig(
                                Trigger.SourceRegistrationTimeConfig.EXCLUDE)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(new ArrayList<>());

        when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
        when(mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime()).thenReturn(1.0f);

        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();

        captureAndAssertNullAggregateReport(trigger);
    }

    @Test
    public void performPendingAttributions_generatesAggregateReport_excludeSourceRegistrationTime()
            throws JSONException, DatastoreException {
        JSONArray triggerData = getAggregateTriggerData();

        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setAggregateTriggerData(triggerData.toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .setRegistrationOrigin(WebUtil.validUri("https://trigger.example.test"))
                        .setAggregatableSourceRegistrationTimeConfig(
                                Trigger.SourceRegistrationTimeConfig.EXCLUDE)
                        .build();

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("sourceId1")
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString("{\"product\":[\"1234\",\"2345\"]}")
                        .setRegistrationOrigin(WebUtil.validUri("https://source.example.test"))
                        .build();

        AggregateReport expectedAggregateReport =
                new AggregateReport.Builder()
                        .setApiVersion("0.1")
                        .setAttributionDestination(trigger.getAttributionDestination())
                        .setDebugCleartextPayload(
                                "{\"operation\":\"histogram\","
                                        + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                                        + "{\"bucket\":\"2693\",\"value\":1644}]}")
                        .setEnrollmentId(source.getEnrollmentId())
                        .setPublisher(source.getRegistrant())
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(WebUtil.validUri("https://trigger.example.test"))
                        .setAggregateAttributionData(
                                new AggregateAttributionData.Builder()
                                        .setContributions(
                                                Arrays.asList(
                                                        new AggregateHistogramContribution.Builder()
                                                                .setKey(new BigInteger("1369"))
                                                                .setValue(32768)
                                                                .build(),
                                                        new AggregateHistogramContribution.Builder()
                                                                .setKey(new BigInteger("2693"))
                                                                .setValue(1644)
                                                                .build()))
                                        .build())
                        .setSourceRegistrationTime(null)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any()))
                .thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
        when(mFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled())
                .thenReturn(true);

        mHandler.performPendingAttributions();

        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());
        assertAggregateReportsEqual(expectedAggregateReport, aggregateReportCaptor.getValue());
    }

    @Test
    public void shouldDoSimpleAttributionWithNullReports_includeSourceRegistrationTime()
            throws DatastoreException, JSONException {
        JSONArray triggerData = getAggregateTriggerData();

        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setAggregateTriggerData(triggerData.toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .setAggregatableSourceRegistrationTimeConfig(
                                Trigger.SourceRegistrationTimeConfig.INCLUDE)
                        .build();

        Source source = getAggregateSource();

        AggregateReport expectedAggregateReport = getExpectedAggregateReport(trigger, source);

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
        when(mFlags.getMeasurementMaxReportingRegisterSourceExpirationInSeconds())
                .thenReturn(MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
        when(mFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled())
                .thenReturn(true);
        when(mFlags.getMeasurementNullAggReportRateInclSourceRegistrationTime()).thenReturn(1.0f);

        mHandler.performPendingAttributions();

        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        // There is a chance to create a null report for each day between 0 and max expiry,
        // except for the day that the source actually occurred.
        long invocations =
                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS
                        / TimeUnit.DAYS.toSeconds(1);
        invocations++; // The real report will also be inserted.
        verify(mMeasurementDao, times((int) invocations))
                .insertAggregateReport(aggregateReportCaptor.capture());
        List<AggregateReport> reports = aggregateReportCaptor.getAllValues();

        List<AggregateReport> fakeReports =
                reports.stream()
                        .filter(AggregateReport::isFakeReport)
                        .sorted(
                                Comparator.comparing(AggregateReport::getSourceRegistrationTime)
                                        .reversed())
                        .collect(Collectors.toList());

        for (int i = 0; i < fakeReports.size(); i++) {
            // i + 1 because the first attempt at creating a null report is skipped since the
            // fakeSourceTime would be equal to the actual source registration time.
            assertNullAggregateReport(
                    fakeReports.get(i),
                    trigger,
                    Long.valueOf(trigger.getTriggerTime() - (i + 1) * TimeUnit.DAYS.toMillis(1)));
        }

        AggregateReport trueReport =
                reports.stream().filter(r -> !r.isFakeReport()).findFirst().get();
        assertAggregateReportsEqual(expectedAggregateReport, trueReport);
    }

    @Test
    public void shouldDoSimpleAttribution_excludeSourceRegistrationTime_noNullReports()
            throws DatastoreException, JSONException {
        JSONArray triggerData = getAggregateTriggerData();

        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setAggregateTriggerData(triggerData.toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .setAggregatableSourceRegistrationTimeConfig(
                                Trigger.SourceRegistrationTimeConfig.EXCLUDE)
                        .build();

        Source source = getAggregateSource();

        AggregateReport expectedAggregateReport =
                new AggregateReport.Builder()
                        .setApiVersion("0.1")
                        .setAttributionDestination(trigger.getAttributionDestination())
                        .setDebugCleartextPayload(
                                "{\"operation\":\"histogram\","
                                        + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                                        + "{\"bucket\":\"2693\",\"value\":1644}]}")
                        .setEnrollmentId(source.getEnrollmentId())
                        .setPublisher(source.getRegistrant())
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(REGISTRATION_URI)
                        // Because the trigger's SourceRegistrationTimeConfig is EXCLUDE, we should
                        // get a null source registration time on the aggregate report
                        .setSourceRegistrationTime(null)
                        .setAggregateAttributionData(
                                new AggregateAttributionData.Builder()
                                        .setContributions(
                                                Arrays.asList(
                                                        new AggregateHistogramContribution.Builder()
                                                                .setKey(new BigInteger("1369"))
                                                                .setValue(32768)
                                                                .build(),
                                                        new AggregateHistogramContribution.Builder()
                                                                .setKey(new BigInteger("2693"))
                                                                .setValue(1644)
                                                                .build()))
                                        .build())
                        .setIsFakeReport(false)
                        .build();

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
        when(mFlags.getMeasurementMaxReportingRegisterSourceExpirationInSeconds())
                .thenReturn(MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
        when(mFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled())
                .thenReturn(true);
        // A rate of 1 would guarantee a null report was generated if we were not checking the
        // SourceRegistrationTimeConfig on the trigger, but because we are checking, there
        // should be no null reports.
        when(mFlags.getMeasurementNullAggReportRateInclSourceRegistrationTime()).thenReturn(1.0f);

        mHandler.performPendingAttributions();

        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);

        verify(mMeasurementDao, times(1)).insertAggregateReport(aggregateReportCaptor.capture());
        AggregateReport actualAggregateReport = aggregateReportCaptor.getValue();

        assertAggregateReportsEqual(expectedAggregateReport, actualAggregateReport);
    }

    @Test
    public void shouldDoSimpleAttributionWithNullReports()
            throws DatastoreException, JSONException {
        JSONArray triggerData = getAggregateTriggerData();

        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setAggregateTriggerData(triggerData.toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();

        Source source = getAggregateSource();

        AggregateReport expectedAggregateReport = getExpectedAggregateReport(trigger, source);

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
        when(mFlags.getMeasurementMaxReportingRegisterSourceExpirationInSeconds())
                .thenReturn(MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
        when(mFlags.getMeasurementNullAggReportRateInclSourceRegistrationTime()).thenReturn(1.0f);

        mHandler.performPendingAttributions();

        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        // There is a chance to create a null report for each day between 0 and max expiry,
        // except for the day that the source actually occurred.
        long invocations =
                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS
                        / TimeUnit.DAYS.toSeconds(1);
        invocations++; // The real report will also be inserted.
        verify(mMeasurementDao, times((int) invocations))
                .insertAggregateReport(aggregateReportCaptor.capture());
        List<AggregateReport> reports = aggregateReportCaptor.getAllValues();

        List<AggregateReport> fakeReports =
                reports.stream()
                        .filter(AggregateReport::isFakeReport)
                        .sorted(
                                Comparator.comparing(AggregateReport::getSourceRegistrationTime)
                                        .reversed())
                        .collect(Collectors.toList());

        for (int i = 0; i < fakeReports.size(); i++) {
            // i + 1 because the first attempt at creating a null report is skipped since the
            // fakeSourceTime would be equal to the actual source registration time.
            assertNullAggregateReport(
                    fakeReports.get(i),
                    trigger,
                    Long.valueOf(trigger.getTriggerTime() - (i + 1) * TimeUnit.DAYS.toMillis(1)));
        }

        AggregateReport trueReport =
                reports.stream().filter(r -> !r.isFakeReport()).findFirst().get();
        assertAggregateReportsEqual(expectedAggregateReport, trueReport);
    }

    @Test
    public void shouldDoSimpleAttributionWithNullReports_triggerOnLastPossibleDay()
            throws DatastoreException, JSONException {
        JSONArray triggerData = getAggregateTriggerData();

        // Add one day because attribution is eligible from 0 to max days, inclusive.
        long maxDays =
                (MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS
                                + TimeUnit.DAYS.toSeconds(1))
                        * TimeUnit.SECONDS.toMillis(1);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(roundDownToDay(SOURCE_TIME) + maxDays - 1)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setAggregateTriggerData(triggerData.toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("sourceId1")
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(roundDownToDay(SOURCE_TIME) + maxDays)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString("{\"product\":[\"1234\",\"2345\"]}")
                        .build();

        AggregateReport expectedAggregateReport =
                new AggregateReport.Builder()
                        .setApiVersion("0.1")
                        .setAttributionDestination(trigger.getAttributionDestination())
                        .setDebugCleartextPayload(
                                "{\"operation\":\"histogram\","
                                        + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                                        + "{\"bucket\":\"2693\",\"value\":1644}]}")
                        .setEnrollmentId(source.getEnrollmentId())
                        .setPublisher(source.getRegistrant())
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(REGISTRATION_URI)
                        .setAggregateAttributionData(
                                new AggregateAttributionData.Builder()
                                        .setContributions(
                                                Arrays.asList(
                                                        new AggregateHistogramContribution.Builder()
                                                                .setKey(new BigInteger("1369"))
                                                                .setValue(32768)
                                                                .build(),
                                                        new AggregateHistogramContribution.Builder()
                                                                .setKey(new BigInteger("2693"))
                                                                .setValue(1644)
                                                                .build()))
                                        .build())
                        .setSourceRegistrationTime(roundDownToDay(SOURCE_TIME))
                        .setIsFakeReport(false)
                        .build();

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
        when(mFlags.getMeasurementMaxReportingRegisterSourceExpirationInSeconds())
                .thenReturn(MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
        when(mFlags.getMeasurementNullAggReportRateInclSourceRegistrationTime()).thenReturn(1.0f);

        mHandler.performPendingAttributions();

        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        // There is a chance to create a null report for each day between 0 and max expiry
        long invocations =
                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS
                        / TimeUnit.DAYS.toSeconds(1);
        invocations++; // The real report will also be inserted.
        verify(mMeasurementDao, times((int) invocations))
                .insertAggregateReport(aggregateReportCaptor.capture());
        List<AggregateReport> reports = aggregateReportCaptor.getAllValues();

        List<AggregateReport> fakeReports =
                reports.stream()
                        .filter(AggregateReport::isFakeReport)
                        .sorted(
                                Comparator.comparing(AggregateReport::getSourceRegistrationTime)
                                        .reversed())
                        .collect(Collectors.toList());

        for (int i = 0; i < fakeReports.size(); i++) {
            assertNullAggregateReport(
                    fakeReports.get(i),
                    trigger,
                    Long.valueOf(trigger.getTriggerTime() - i * TimeUnit.DAYS.toMillis(1)));
        }

        AggregateReport trueReport =
                reports.stream().filter(r -> !r.isFakeReport()).findFirst().get();
        assertAggregateReportsEqual(expectedAggregateReport, trueReport);
    }

    @Test
    public void shouldDoSimpleAttribution_dedupAlignFlagOff() throws DatastoreException {
        Trigger trigger = createAPendingTriggerEventScopeOnly();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        when(mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()).thenReturn(false);

        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao).updateSourceEventReportDedupKeys(sourceArg.capture());
        assertEquals(
                sourceArg.getValue().getEventReportDedupKeys(),
                Collections.singletonList(new UnsignedLong(2L)));
        verify(mMeasurementDao).insertEventReport(any());

        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());

        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldIgnoreLowPrioritySourceWhileAttribution() throws DatastoreException {
        String eventTriggers =
                "[\n"
                        + "{\n"
                        + "  \"trigger_data\": \"5\",\n"
                        + "  \"priority\": \"123\",\n"
                        + "  \"deduplication_key\": \"2\",\n"
                        + "  \"filters\": [{\n"
                        + "    \"key_1\": [\"value_1\"] \n"
                        + "   }]\n"
                        + "}"
                        + "]\n";
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(eventTriggers)
                        .setTriggerTime(3)
                        .build();
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source1")
                        .setPriority(100L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(1L)
                        .setExpiryTime(30)
                        .build();
        Source source2 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source2")
                        .setPriority(200L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(2L)
                        .setExpiryTime(30)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source1);
        matchingSourceList.add(source2);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source1.getId()))
                .thenReturn(Pair.create(
                        source1.getAppDestinations(),
                        source1.getWebDestinations()));
        when(mMeasurementDao.getSourceDestinations(source2.getId()))
                .thenReturn(Pair.create(
                        source2.getAppDestinations(),
                        source2.getWebDestinations()));


        mHandler.performPendingAttributions();
        trigger.setStatus(Trigger.Status.ATTRIBUTED);
        verify(mMeasurementDao)
                .updateSourceStatus(eq(List.of(source1.getId())), eq(Source.Status.IGNORED));
        assertEquals(1, matchingSourceList.size());
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("2"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source2.getId()), eq(expectedAttributionStatus));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());

        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void
            performAttributions_sourceDeactivationAfterFilteringFlagOn_ignoresWithoutAttribution()
                    throws DatastoreException {
        String eventTriggers =
                "[{"
                        + "  \"trigger_data\": \"5\","
                        + "  \"priority\": \"123\","
                        + "  \"deduplication_key\": \"2\","
                        + "  \"filters\": [{"
                        + "    \"key_1\": [\"value_1\"] "
                        + "   }]"
                        + "}]";
        // Missing top level filters match anything. Competing sources are ignored directly after.
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(eventTriggers)
                        .setTriggerTime(3)
                        .build();
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source1")
                        .setPriority(100L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(1L)
                        .setExpiryTime(30)
                        // Filters match the trigger's event triggers but the Source is not matched
                        // since it has lower priority.
                        .setFilterDataString("{\"key_1\":[\"1234\",\"value_1\"]}")
                        .build();
        Source source2 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source2")
                        .setPriority(200L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(2L)
                        .setExpiryTime(30)
                        // Filters do not match the trigger's event triggers so a report is not
                        // generated.
                        .setFilterDataString("{\"key_1\":[\"no_match\"]}")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source1);
        matchingSourceList.add(source2);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source1.getId()))
                .thenReturn(Pair.create(
                        source1.getAppDestinations(),
                        source1.getWebDestinations()));
        when(mMeasurementDao.getSourceDestinations(source2.getId()))
                .thenReturn(Pair.create(
                        source2.getAppDestinations(),
                        source2.getWebDestinations()));

        when(mFlags.getMeasurementEnableSourceDeactivationAfterFiltering()).thenReturn(true);

        mHandler.performPendingAttributions();
        trigger.setStatus(Trigger.Status.ATTRIBUTED);
        verify(mMeasurementDao)
                .updateSourceStatus(eq(List.of(source1.getId())), eq(Source.Status.IGNORED));
        assertEquals(1, matchingSourceList.size());
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).updateSourceAttributedTriggers(anyString(), any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void
            performAttributions_sourceDeactivationAfterFilteringFlagOff_doesNotIgnoreWithoutAttr()
                    throws DatastoreException {
        String eventTriggers =
                "[{"
                        + "  \"trigger_data\": \"5\","
                        + "  \"priority\": \"123\","
                        + "  \"deduplication_key\": \"2\","
                        + "  \"filters\": [{"
                        + "    \"key_1\": [\"value_1\"] "
                        + "   }]"
                        + "}]";
        // Missing top level filters match anything.
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(eventTriggers)
                        .setTriggerTime(3)
                        .build();
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source1")
                        .setPriority(100L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(1L)
                        .setExpiryTime(30)
                        // Filters match but the Source is not matched since it has lower priority.
                        .setFilterDataString("{\"key_1\":[\"1234\",\"value_1\"]}")
                        .build();
        Source source2 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source2")
                        .setPriority(200L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(2L)
                        .setExpiryTime(30)
                        // Filters do not match, attribution flow returns and does not ignore
                        // competing sources.
                        .setFilterDataString("{\"key_1\":[\"1234\",\"no_match\"]}")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source1);
        matchingSourceList.add(source2);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source1.getId()))
                .thenReturn(Pair.create(
                        source1.getAppDestinations(),
                        source1.getWebDestinations()));
        when(mMeasurementDao.getSourceDestinations(source2.getId()))
                .thenReturn(Pair.create(
                        source2.getAppDestinations(),
                        source2.getWebDestinations()));

        when(mFlags.getMeasurementEnableSourceDeactivationAfterFiltering()).thenReturn(false);

        mHandler.performPendingAttributions();
        trigger.setStatus(Trigger.Status.ATTRIBUTED);
        // Attribution did not occur and competing sources are not ignored.
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
        assertEquals(1, matchingSourceList.size());
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).updateSourceAttributedTriggers(anyString(), any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldReplaceLowPriorityReportWhileAttribution() throws DatastoreException {
        String eventTriggers =
                "[\n"
                        + "{\n"
                        + "  \"trigger_data\": \"5\",\n"
                        + "  \"priority\": \"200\",\n"
                        + "  \"deduplication_key\": \"2\"\n"
                        + "}"
                        + "]\n";
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setEventTriggers(eventTriggers)
                        .setStatus(Trigger.Status.PENDING)
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportDedupKeys(new ArrayList<>())
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAppDestinations(List.of(APP_DESTINATION))
                        .setPublisherType(EventSurfaceType.APP)
                        .setPublisher(PUBLISHER)
                        .build();
        doReturn(5L)
                .when(mEventReportWindowCalcDelegate)
                .getReportingTime(any(Source.class), anyLong(), anyInt());
        doReturn(EventReportWindowCalcDelegate.MomentPlacement.WITHIN)
                .when(mEventReportWindowCalcDelegate)
                .fallsWithinWindow(any(Source.class), any(Trigger.class), any(UnsignedLong.class));
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        EventReport eventReport1 =
                new EventReport.Builder()
                        .setStatus(EventReport.Status.PENDING)
                        .setTriggerPriority(100L)
                        .setReportTime(5L)
                        .setAttributionDestinations(List.of(APP_DESTINATION))
                        .build();
        EventReport eventReport2 =
                new EventReport.Builder()
                        .setStatus(EventReport.Status.DELIVERED)
                        .setReportTime(5L)
                        .setAttributionDestinations(source.getAppDestinations())
                        .build();
        EventReport eventReport3 =
                new EventReport.Builder()
                        .setStatus(EventReport.Status.DELIVERED)
                        .setReportTime(5L)
                        .setAttributionDestinations(source.getAppDestinations())
                        .build();
        List<EventReport> matchingReports = new ArrayList<>();
        matchingReports.add(eventReport1);
        matchingReports.add(eventReport2);
        matchingReports.add(eventReport3);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceEventReports(source)).thenReturn(matchingReports);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        List.of(APP_DESTINATION),
                        List.of()));
        mHandler.performPendingAttributions();
        verify(mMeasurementDao).deleteEventReport(eventReport1);
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldRollbackOnFailure() throws DatastoreException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(EVENT_TRIGGERS)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(anyString())).thenReturn(trigger);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        // Failure
        doThrow(new DatastoreException("Simulating failure"))
                .when(mMeasurementDao)
                .insertEventReport(any(EventReport.class));
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        mHandler.performPendingAttributions();
        verify(mMeasurementDao).getTrigger(anyString());
        verify(mMeasurementDao).getMatchingActiveSources(any());
        verify(mMeasurementDao, times(2)).getAttributionsPerRateLimitWindow(
                anyInt(), any(), any());
        verify(mMeasurementDao, times(1)).insertEventReport(any());
        verify(mMeasurementDao, never()).updateTriggerStatus(any(), anyInt());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction).rollback();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldRollbackOnFailure_dedupAlignFlagOff() throws DatastoreException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(EVENT_TRIGGERS)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(anyString())).thenReturn(trigger);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        // Failure
        when(mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()).thenReturn(false);
        doThrow(new DatastoreException("Simulating failure"))
                .when(mMeasurementDao)
                .updateSourceEventReportDedupKeys(any());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        mHandler.performPendingAttributions();
        verify(mMeasurementDao).getTrigger(anyString());
        verify(mMeasurementDao).getMatchingActiveSources(any());
        verify(mMeasurementDao, times(2)).getAttributionsPerRateLimitWindow(
                anyInt(), any(), any());
        verify(mMeasurementDao, never()).updateTriggerStatus(any(), anyInt());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction).rollback();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldPerformMultipleAttributions() throws DatastoreException, JSONException {
        Trigger trigger1 =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .build();
        Trigger trigger2 =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId2")
                        .setTriggerTime(TRIGGER_TIME + 1000L)
                        .setStatus(Trigger.Status.PENDING)
                        .setRegistrationOrigin(WebUtil.validUri("https://subdomain2.example.test"))
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"2\"\n"
                                        + "}"
                                        + "]\n")
                        .build();
        List<Trigger> triggers = new ArrayList<>();
        triggers.add(trigger1);
        triggers.add(trigger2);
        List<Source> matchingSourceList1 = new ArrayList<>();
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        matchingSourceList1.add(source1);
        List<Source> matchingSourceList2 = new ArrayList<>();
        Source source2 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME + 500L)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setRegistrationOrigin(WebUtil.validUri("https://subdomain2.example.test"))
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        matchingSourceList2.add(source2);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Arrays.asList(trigger1.getId(), trigger2.getId()));
        when(mMeasurementDao.getTrigger(trigger1.getId())).thenReturn(trigger1);
        when(mMeasurementDao.getTrigger(trigger2.getId())).thenReturn(trigger2);
        when(mMeasurementDao.getMatchingActiveSources(trigger1)).thenReturn(matchingSourceList1);
        when(mMeasurementDao.getMatchingActiveSources(trigger2)).thenReturn(matchingSourceList2);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source1.getId()))
                .thenReturn(Pair.create(
                        source1.getAppDestinations(),
                        source1.getWebDestinations()));
        when(mMeasurementDao.getSourceDestinations(source2.getId()))
                .thenReturn(Pair.create(
                        source2.getAppDestinations(),
                        source2.getWebDestinations()));

        assertEquals(
                AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED,
                mHandler.performPendingAttributions());
        // Verify trigger status updates.
        verify(mMeasurementDao, times(2)).updateTriggerStatus(any(), eq(Trigger.Status.ATTRIBUTED));
        // Verify source dedup key updates.
        String expectedAttributionStatus1 = getAttributionStatus(
                List.of(trigger1.getId()), List.of("5"), List.of("1"));
        String expectedAttributionStatus2 = getAttributionStatus(
                List.of(trigger2.getId()), List.of("5"), List.of("2"));
        ArgumentCaptor<String> sourceIdArg = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> attributionStatusArg = ArgumentCaptor.forClass(String.class);
        verify(mMeasurementDao, times(2)).updateSourceAttributedTriggers(
                sourceIdArg.capture(), attributionStatusArg.capture());
        List<String> sourceIds = sourceIdArg.getAllValues();
        List<String> attributionStatuses = attributionStatusArg.getAllValues();
        assertEquals(source1.getId(), sourceIds.get(0));
        assertEquals(expectedAttributionStatus1, attributionStatuses.get(0));
        assertEquals(source2.getId(), sourceIds.get(1));
        assertEquals(expectedAttributionStatus2, attributionStatuses.get(1));
        // Verify new event report insertion.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao, times(2)).insertEventReport(reportArg.capture());
        List<EventReport> newReportArgs = reportArg.getAllValues();
        boolean flexEventReportFlag = true;
        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        for (int i = 0; i < newReportArgs.size(); i++) {
            assertEquals(
                    newReportArgs.get(i).getTriggerDedupKey(),
                    triggers.get(i).parseEventTriggers(mFlags).get(0).getDedupKey());
            assertEquals(
                    newReportArgs.get(i).getRegistrationOrigin(),
                    triggers.get(i).getRegistrationOrigin());
        }
    }

    @Test
    public void shouldAttributedToInstallAttributedSource() throws DatastoreException {
        long eventTime = System.currentTimeMillis();
        long triggerTime = eventTime + TimeUnit.DAYS.toMillis(5);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("trigger1")
                        .setStatus(Trigger.Status.PENDING)
                        .setTriggerTime(triggerTime)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"2\"\n"
                                        + "}"
                                        + "]\n")
                        .build();
        // Lower priority and older priority source.
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source1")
                        .setEventId(new UnsignedLong(1L))
                        .setPriority(100L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setInstallAttributed(true)
                        .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(10))
                        .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(28))
                        .setEventTime(eventTime - TimeUnit.DAYS.toMillis(2))
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        Source source2 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source2")
                        .setEventId(new UnsignedLong(2L))
                        .setPriority(200L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(eventTime)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source2);
        matchingSourceList.add(source1);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source1.getId()))
                .thenReturn(Pair.create(
                        source1.getAppDestinations(),
                        source1.getWebDestinations()));
        when(mMeasurementDao.getSourceDestinations(source2.getId()))
                .thenReturn(Pair.create(
                        source2.getAppDestinations(),
                        source2.getWebDestinations()));
        mHandler.performPendingAttributions();
        trigger.setStatus(Trigger.Status.ATTRIBUTED);
        verify(mMeasurementDao)
                .updateSourceStatus(eq(List.of(source2.getId())), eq(Source.Status.IGNORED));
        assertEquals(1, matchingSourceList.size());
        assertEquals(source2.getEventId(), matchingSourceList.get(0).getEventId());
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("2"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source1.getId()), eq(expectedAttributionStatus));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
    }

    @Test
    public void shouldNotAttributeToOldInstallAttributedSource() throws DatastoreException {
        // Setup
        long eventTime = System.currentTimeMillis();
        long triggerTime = eventTime + TimeUnit.DAYS.toMillis(10);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("trigger1")
                        .setStatus(Trigger.Status.PENDING)
                        .setTriggerTime(triggerTime)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"2\"\n"
                                        + "}"
                                        + "]\n")
                        .build();
        // Lower Priority. Install cooldown Window passed.
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source1")
                        .setEventId(new UnsignedLong(1L))
                        .setPriority(100L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setInstallAttributed(true)
                        .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(3))
                        .setEventTime(eventTime - TimeUnit.DAYS.toMillis(2))
                        .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(28))
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        Source source2 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("source2")
                        .setEventId(new UnsignedLong(2L))
                        .setPriority(200L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(eventTime)
                        .setExpiryTime(triggerTime + TimeUnit.DAYS.toMillis(28))
                        .setEventReportWindow(triggerTime + 1L)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source2);
        matchingSourceList.add(source1);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source1.getId()))
                .thenReturn(Pair.create(
                        source1.getAppDestinations(),
                        source1.getWebDestinations()));
        when(mMeasurementDao.getSourceDestinations(source2.getId()))
                .thenReturn(Pair.create(
                        source2.getAppDestinations(),
                        source2.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();
        trigger.setStatus(Trigger.Status.ATTRIBUTED);

        // Assertion
        verify(mMeasurementDao)
                .updateSourceStatus(eq(List.of(source1.getId())), eq(Source.Status.IGNORED));
        assertEquals(1, matchingSourceList.size());
        assertEquals(source1.getEventId(), matchingSourceList.get(0).getEventId());
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        ArgumentCaptor<String> sourceIdArg = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> attributionStatusArg = ArgumentCaptor.forClass(String.class);
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("2"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                sourceIdArg.capture(), attributionStatusArg.capture());
        assertEquals(source2.getId(), sourceIdArg.getValue());
        assertEquals(
                expectedAttributionStatus,
                attributionStatusArg.getValue());
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
    }

    @Test
    public void shouldNotGenerateReportForAttributionModeFalsely() throws DatastoreException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.FALSELY)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldNotGenerateReportForAttributionModeNever() throws DatastoreException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.NEVER)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performPendingAttributions_GeneratesEventReport_WithReportingOriginOfTrigger()
            throws DatastoreException {
        Trigger trigger1 =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setRegistrationOrigin(WebUtil.validUri("https://trigger.example.test"))
                        .build();
        List<Source> matchingSourceList1 = new ArrayList<>();
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setRegistrationOrigin(WebUtil.validUri("https://source.example.test"))
                        .build();
        matchingSourceList1.add(source1);
        when(mMeasurementDao.getPendingTriggerIds()).thenReturn(Arrays.asList(trigger1.getId()));
        when(mMeasurementDao.getTrigger(trigger1.getId())).thenReturn(trigger1);
        when(mMeasurementDao.getMatchingActiveSources(trigger1)).thenReturn(matchingSourceList1);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source1.getId()))
                .thenReturn(
                        Pair.create(source1.getAppDestinations(), source1.getWebDestinations()));

        assertEquals(
                AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED,
                mHandler.performPendingAttributions());
        // Verify trigger status updates.
        verify(mMeasurementDao).updateTriggerStatus(any(), eq(Trigger.Status.ATTRIBUTED));
        // Verify new event report insertion.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(
                WebUtil.validUri("https://trigger.example.test"),
                eventReport.getRegistrationOrigin());
    }

    @Test
    public void performPendingAttributions_GeneratesEventReport_WithCoarseDestinations()
            throws DatastoreException {
        Trigger trigger1 =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setRegistrationOrigin(WebUtil.validUri("https://trigger.example.test"))
                        .build();
        List<Source> matchingSourceList1 = new ArrayList<>();
        Source source1 =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setRegistrationOrigin(WebUtil.validUri("https://source.example.test"))
                        .setAppDestinations(Collections.singletonList(APP_DESTINATION))
                        .setWebDestinations(Collections.singletonList(WEB_DESTINATION))
                        .setCoarseEventReportDestinations(true)
                        .build();
        matchingSourceList1.add(source1);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger1.getId()));
        when(mMeasurementDao.getTrigger(trigger1.getId())).thenReturn(trigger1);
        when(mMeasurementDao.getMatchingActiveSources(trigger1)).thenReturn(matchingSourceList1);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source1.getId()))
                .thenReturn(
                        Pair.create(source1.getAppDestinations(), source1.getWebDestinations()));
        when(mFlags.getMeasurementEnableCoarseEventReportDestinations()).thenReturn(true);

        assertEquals(
                AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED,
                mHandler.performPendingAttributions());
        // Verify trigger status updates.
        verify(mMeasurementDao).updateTriggerStatus(any(), eq(Trigger.Status.ATTRIBUTED));
        // Verify new event report insertion.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(
                WebUtil.validUri("https://trigger.example.test"),
                eventReport.getRegistrationOrigin());
        List<Uri> reportDestinations = eventReport.getAttributionDestinations();
        assertEquals(2, reportDestinations.size());
        assertEquals(APP_DESTINATION, reportDestinations.get(0));
        assertEquals(WEB_DESTINATION, reportDestinations.get(1));
    }

    @Test
    public void shouldObserveFlagOverriddenAggregateReportDelay()
            throws DatastoreException, JSONException {
        Trigger trigger = getAggregateTrigger();
        Source source = getAggregateSource();

        long reportMinDelay = TimeUnit.MINUTES.toMillis(61);
        long reportDelaySpan = TimeUnit.MINUTES.toMillis(10);
        when(mFlags.getMeasurementEnableConfigurableAggregateReportDelay()).thenReturn(true);
        when(mFlags.getMeasurementAggregateReportDelayConfig()).thenReturn(
                String.valueOf(reportMinDelay) + "," + String.valueOf(reportDelaySpan));

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        mHandler.performPendingAttributions();

        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

        // Assert expected aggregate report time range
        long lowerBound = TRIGGER_TIME + reportMinDelay;
        // Add slightly more delay to upper bound to account for execution.
        long upperBound = TRIGGER_TIME + reportMinDelay + reportDelaySpan + 1000L;
        AggregateReport capturedReport = aggregateReportCaptor.getValue();
        assertTrue(capturedReport.getScheduledReportTime() > lowerBound
                && capturedReport.getScheduledReportTime() < upperBound);
    }

    @Test
    public void shouldObserveDefaultAggregateReportDelay()
            throws DatastoreException, JSONException {
        Trigger trigger = getAggregateTrigger();
        Source source = getAggregateSource();

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        mHandler.performPendingAttributions();

        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

        // Assert expected aggregate report time range
        long lowerBound = TRIGGER_TIME + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY;
        // Add slightly more delay to upper bound to account for execution.
        long upperBound = TRIGGER_TIME + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY
                + PrivacyParams.AGGREGATE_REPORT_DELAY_SPAN + 1000L;
        AggregateReport capturedReport = aggregateReportCaptor.getValue();
        assertTrue(capturedReport.getScheduledReportTime() > lowerBound
                && capturedReport.getScheduledReportTime() < upperBound);
    }

    @Test
    public void shouldObserveDefaultAggregateReportDelayWhenFlagOverrideIsNull()
            throws DatastoreException, JSONException {
        Trigger trigger = getAggregateTrigger();
        Source source = getAggregateSource();

        when(mFlags.getMeasurementEnableConfigurableAggregateReportDelay()).thenReturn(true);
        when(mFlags.getMeasurementAggregateReportDelayConfig()).thenReturn(null);

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        mHandler.performPendingAttributions();

        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

        // Assert expected aggregate report time range
        long lowerBound = TRIGGER_TIME + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY;
        // Add slightly more delay to upper bound to account for execution.
        long upperBound = TRIGGER_TIME + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY
                + PrivacyParams.AGGREGATE_REPORT_DELAY_SPAN + 1000L;
        AggregateReport capturedReport = aggregateReportCaptor.getValue();
        assertTrue(capturedReport.getScheduledReportTime() > lowerBound
                && capturedReport.getScheduledReportTime() < upperBound);
    }

    @Test
    public void shouldObserveDefaultAggregateReportDelayWhenFlagOverrideSizeIsInvalid()
            throws DatastoreException, JSONException {
        Trigger trigger = getAggregateTrigger();
        Source source = getAggregateSource();

        when(mFlags.getMeasurementEnableConfigurableAggregateReportDelay()).thenReturn(true);
        when(mFlags.getMeasurementAggregateReportDelayConfig()).thenReturn("12");

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        mHandler.performPendingAttributions();

        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

        // Assert expected aggregate report time range
        long lowerBound = TRIGGER_TIME + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY;
        // Add slightly more delay to upper bound to account for execution.
        long upperBound = TRIGGER_TIME + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY
                + PrivacyParams.AGGREGATE_REPORT_DELAY_SPAN + 1000L;
        AggregateReport capturedReport = aggregateReportCaptor.getValue();
        assertTrue(capturedReport.getScheduledReportTime() > lowerBound
                && capturedReport.getScheduledReportTime() < upperBound);
    }

    @Test
    public void shouldObserveDefaultAggregateReportDelayWhenFlagOverrideValueIsInvalid()
            throws DatastoreException, JSONException {
        Trigger trigger = getAggregateTrigger();
        Source source = getAggregateSource();

        when(mFlags.getMeasurementEnableConfigurableAggregateReportDelay()).thenReturn(true);
        when(mFlags.getMeasurementAggregateReportDelayConfig()).thenReturn("1200u0000r,3600000");

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        mHandler.performPendingAttributions();

        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

        // Assert expected aggregate report time range
        long lowerBound = TRIGGER_TIME + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY;
        // Add slightly more delay to upper bound to account for execution.
        long upperBound = TRIGGER_TIME + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY
                + PrivacyParams.AGGREGATE_REPORT_DELAY_SPAN + 1000L;
        AggregateReport capturedReport = aggregateReportCaptor.getValue();
        assertTrue(capturedReport.getScheduledReportTime() > lowerBound
                && capturedReport.getScheduledReportTime() < upperBound);
    }

    @Test
    public void shouldDoSimpleAttributionGenerateUnencryptedAggregateReport()
            throws DatastoreException, JSONException {
        JSONArray triggerData = getAggregateTriggerData();

        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setAggregateTriggerData(triggerData.toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();

        Source source = getAggregateSource();

        AggregateReport expectedAggregateReport =
                new AggregateReport.Builder()
                        .setApiVersion("0.1")
                        .setAttributionDestination(trigger.getAttributionDestination())
                        .setDebugCleartextPayload(
                                "{\"operation\":\"histogram\","
                                        + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                                        + "{\"bucket\":\"2693\",\"value\":1644}]}")
                        .setEnrollmentId(source.getEnrollmentId())
                        .setPublisher(source.getRegistrant())
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(REGISTRATION_URI)
                        .setAggregateAttributionData(
                                new AggregateAttributionData.Builder()
                                        .setContributions(
                                                Arrays.asList(
                                                        new AggregateHistogramContribution.Builder()
                                                                .setKey(new BigInteger("1369"))
                                                                .setValue(32768)
                                                                .build(),
                                                        new AggregateHistogramContribution.Builder()
                                                                .setKey(new BigInteger("2693"))
                                                                .setValue(1644)
                                                                .build()))
                                        .build())
                        .setSourceRegistrationTime(roundDownToDay(SOURCE_TIME))
                        .build();

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        mHandler.performPendingAttributions();
        ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao).updateSourceAggregateContributions(sourceArg.capture());
        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

        assertAggregateReportsEqual(expectedAggregateReport, aggregateReportCaptor.getValue());
        assertEquals(sourceArg.getValue().getAggregateContributions(), 32768 + 1644);
    }

    @Test
    public void shouldDoSimpleAttribution_IncrementStatsEventReportCount()
            throws DatastoreException {
        Trigger trigger = createAPendingTriggerEventScopeOnly();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any()))
                .thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

        mHandler.performPendingAttributions();
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus =
                getAttributionStatus(List.of(trigger.getId()), List.of("5"), List.of("2"));
        verify(mMeasurementDao)
                .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
        verify(mMeasurementDao).insertEventReport(any());

        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());

        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();

        MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
        assertEquals(0, measurementAttributionStats.getAggregateReportCount());
        assertEquals(0, measurementAttributionStats.getAggregateDebugReportCount());
        assertEquals(1, measurementAttributionStats.getEventReportCount());
        assertEquals(0, measurementAttributionStats.getEventDebugReportCount());
    }

    @Test
    public void shouldDoSimpleAttribution_IncrementStatsAggregateAndEventReportCount()
            throws DatastoreException, JSONException {
        JSONArray triggerData = getAggregateTriggerData();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setAggregateTriggerData(triggerData.toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        Source source = getAggregateSource();

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any()))
                .thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

        mHandler.performPendingAttributions();

        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao).insertEventReport(any());

        MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
        assertEquals(1, measurementAttributionStats.getAggregateReportCount());
        assertEquals(0, measurementAttributionStats.getAggregateDebugReportCount());
        assertEquals(1, measurementAttributionStats.getEventReportCount());
        assertEquals(0, measurementAttributionStats.getEventDebugReportCount());
    }

    @Test
    public void performPendingAttributions_GeneratesAggregateReport_WithReportingOriginOfTrigger()
            throws JSONException, DatastoreException {
        JSONArray triggerData = getAggregateTriggerData();

        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setAggregateTriggerData(triggerData.toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .setRegistrationOrigin(WebUtil.validUri("https://trigger.example.test"))
                        .build();

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("sourceId1")
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString("{\"product\":[\"1234\",\"2345\"]}")
                        .setRegistrationOrigin(WebUtil.validUri("https://source.example.test"))
                        .build();

        AggregateReport expectedAggregateReport =
                new AggregateReport.Builder()
                        .setApiVersion("0.1")
                        .setAttributionDestination(trigger.getAttributionDestination())
                        .setDebugCleartextPayload(
                                "{\"operation\":\"histogram\","
                                        + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                                        + "{\"bucket\":\"2693\",\"value\":1644}]}")
                        .setEnrollmentId(source.getEnrollmentId())
                        .setPublisher(source.getRegistrant())
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(WebUtil.validUri("https://trigger.example.test"))
                        .setAggregateAttributionData(
                                new AggregateAttributionData.Builder()
                                        .setContributions(
                                                Arrays.asList(
                                                        new AggregateHistogramContribution.Builder()
                                                                .setKey(new BigInteger("1369"))
                                                                .setValue(32768)
                                                                .build(),
                                                        new AggregateHistogramContribution.Builder()
                                                                .setKey(new BigInteger("2693"))
                                                                .setValue(1644)
                                                                .build()))
                                        .build())
                        .setSourceRegistrationTime(roundDownToDay(SOURCE_TIME))
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

        mHandler.performPendingAttributions();

        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());
        assertAggregateReportsEqual(expectedAggregateReport, aggregateReportCaptor.getValue());
    }

    @Test
    public void shouldDoSimpleAttributionGenerateUnencryptedAggregateReportWithDedupKey()
            throws DatastoreException, JSONException {
        JSONArray triggerData = getAggregateTriggerData();

        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setAggregateTriggerData(triggerData.toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .setAggregateDeduplicationKeys(AGGREGATE_DEDUPLICATION_KEYS_1)
                        .build();

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setId("sourceId1")
                        .setEventTime(SOURCE_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString("{\"product\":[\"1234\",\"2345\"]}")
                        .build();
        AggregateReport expectedAggregateReport =
                new AggregateReport.Builder()
                        .setApiVersion("0.1")
                        .setAttributionDestination(trigger.getAttributionDestination())
                        .setDebugCleartextPayload(
                                "{\"operation\":\"histogram\","
                                        + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                                        + "{\"bucket\":\"2693\",\"value\":1644}]}")
                        .setEnrollmentId(source.getEnrollmentId())
                        .setPublisher(source.getRegistrant())
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(REGISTRATION_URI)
                        .setAggregateAttributionData(
                                new AggregateAttributionData.Builder()
                                        .setContributions(
                                                Arrays.asList(
                                                        new AggregateHistogramContribution.Builder()
                                                                .setKey(new BigInteger("1369"))
                                                                .setValue(32768)
                                                                .build(),
                                                        new AggregateHistogramContribution.Builder()
                                                                .setKey(new BigInteger("2693"))
                                                                .setValue(1644)
                                                                .build()))
                                        .build())
                        .setSourceRegistrationTime(roundDownToDay(SOURCE_TIME))
                        .build();

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        mHandler.performPendingAttributions();
        ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao).updateSourceAggregateContributions(sourceArg.capture());
        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

        assertAggregateReportsEqual(expectedAggregateReport, aggregateReportCaptor.getValue());
        assertEquals(sourceArg.getValue().getAggregateContributions(), 32768 + 1644);
        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao).updateSourceAggregateReportDedupKeys(sourceCaptor.capture());

        assertEquals(sourceCaptor.getValue().getAggregateReportDedupKeys().size(), 1);
        assertEquals(
                sourceCaptor.getValue().getAggregateReportDedupKeys().get(0),
                new UnsignedLong(10L));
    }

    @Test
    public void shouldNotGenerateAggregateReportWhenExceedingAggregateContributionsLimit()
            throws DatastoreException, JSONException {
        JSONArray triggerData = getAggregateTriggerData();

        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setAggregateTriggerData(triggerData.toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .setEventTriggers(EVENT_TRIGGERS)
                        .build();

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString("{\"product\":[\"1234\",\"2345\"]}")
                        .setAggregateContributions(65536 - 32768 - 1644 + 1)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        mHandler.performPendingAttributions();
        verify(mMeasurementDao, never()).updateSourceAggregateContributions(any());
        verify(mMeasurementDao, never()).insertAggregateReport(any());
    }

    @Test
    public void performAttributions_noRecords_returnSuccess() throws DatastoreException {
        // Setup
        when(mMeasurementDao.getPendingTriggerIds()).thenReturn(Collections.emptyList());

        // Execution
        AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

        // Assertions
        assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
    }

    @Test
    public void performAttributions_triggerFilterSet_commonKeysDontIntersect_ignoreTrigger()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"]}, {\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1_x\", \"value_2_x\"],\n"
                                        + "  \"key_2\": [\"value_1_x\", \"value_2_x\"]\n"
                                        + "}\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_triggerFilters_commonKeysDontIntersect_ignoreTrigger()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1_x\", \"value_2_x\"],\n"
                                        + "  \"key_2\": [\"value_1_x\", \"value_2_x\"]\n"
                                        + "}\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_triggerNotFilterSet_commonKeysIntersect_ignoreTrigger()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setNotFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"]}, {\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2_x\"],\n"
                                        + "  \"key_2\": [\"value_1_x\", \"value_2\"]\n"
                                        + "}\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_triggerNotFilters_commonKeysIntersect_ignoreTrigger()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setNotFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2_x\"],\n"
                                        + "  \"key_2\": [\"value_1_x\", \"value_2\"]\n"
                                        + "}\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_triggerNotFilterSet_commonKeysDontIntersect_attributeTrigger()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setNotFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11_x\", \"value_12\"]}, {\n"
                                        + "  \"key_2\": [\"value_21\", \"value_22_x\"]\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                                        + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                                        + "}\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("1"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());

        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_triggerNotFiltersWithCommonKeysDontIntersect_attributeTrigger()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setNotFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11_x\", \"value_12\"],\n"
                                        + "  \"key_2\": [\"value_21\", \"value_22_x\"]\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                                        + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                                        + "}\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("1"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_topLevelFilterSetMatch_attributeTrigger()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11_x\", \"value_12\"]}, {\n"
                                        + "  \"key_2\": [\"value_21\", \"value_22\"]\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                                        + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                                        + "}\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("1"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_triggerSourceFiltersWithCommonKeysIntersect_attributeTrigger()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12\"],\n"
                                        + "  \"key_2\": [\"value_21\", \"value_22\"]\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                                        + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                                        + "}\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("1"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_commonKeysIntersect_attributeTrigger_debugApi()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12\"],\n"
                                        + "  \"key_2\": [\"value_21\", \"value_22\"]\n"
                                        + "}]\n")
                        .setDebugKey(TRIGGER_DEBUG_KEY)
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                                        + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                                        + "}\n")
                        .setDebugKey(SOURCE_DEBUG_KEY)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));


        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("1"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_commonKeysIntersect_attributeTrigger_debugApi_sourceKey()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12\"],\n"
                                        + "  \"key_2\": [\"value_21\", \"value_22\"]\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                                        + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                                        + "}\n")
                        .setDebugKey(SOURCE_DEBUG_KEY)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));


        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("1"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_commonKeysIntersect_attributeTrigger_debugApi_triggerKey()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12\"],\n"
                                        + "  \"key_2\": [\"value_21\", \"value_22\"]\n"
                                        + "}]\n")
                        .setDebugKey(TRIGGER_DEBUG_KEY)
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                                        + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                                        + "}\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));


        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("1"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_triggerSourceFiltersWithNoCommonKeys_attributeTrigger()
            throws DatastoreException {
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12\"],\n"
                                        + "  \"key_2\": [\"value_21\", \"value_22\"]\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(SOURCE_TIME)
                        .setExpiryTime(EXPIRY_TIME)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1x\": [\"value_11_x\", \"value_12_x\"],\n"
                                        + "  \"key_2x\": [\"value_21_x\", \"value_22_x\"]\n"
                                        + "}\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));


        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("5"), List.of("1"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_eventLevelFilters_filterSet_attributeFirstMatchingTrigger()
            throws DatastoreException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"2\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"key_1\": [\"unmatched\"] \n"
                                        + "   }]\n"
                                        + "},"
                                        + "{\n"
                                        + "  \"trigger_data\": \"3\",\n"
                                        + "  \"priority\": \"3\",\n"
                                        + "  \"deduplication_key\": \"3\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"ignored\": [\"ignored\"]}, {\n"
                                        + "    \"key_1\": [\"unmatched\"]}, {\n"
                                        + "    \"key_1\": [\"matched\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .setTriggerTime(triggerTime)
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"matched\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setId("sourceId")
                        .setExpiryTime(triggerTime + TimeUnit.DAYS.toMillis(28))
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        EventReport expectedEventReport =
                new EventReport.Builder()
                        .setTriggerPriority(3L)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerTime(triggerTime)
                        .setSourceEventId(source.getEventId())
                        .setStatus(EventReport.Status.PENDING)
                        .setAttributionDestinations(source.getAppDestinations())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setReportTime(
                                mEventReportWindowCalcDelegate.getReportingTime(
                                        source,
                                        trigger.getTriggerTime(),
                                        trigger.getDestinationType()))
                        .setSourceType(source.getSourceType())
                        .setRandomizedTriggerRate(
                                mSourceNoiseHandler.getRandomizedTriggerRate(source))
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(REGISTRATION_URI)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("3"), List.of("3"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_eventLevelFilters_attributeFirstMatchingTrigger()
            throws DatastoreException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"2\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"key_1\": [\"value_1_x\"] \n"
                                        + "   }]\n"
                                        + "},"
                                        + "{\n"
                                        + "  \"trigger_data\": \"3\",\n"
                                        + "  \"priority\": \"3\",\n"
                                        + "  \"deduplication_key\": \"3\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"key_1\": [\"value_1\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .setTriggerTime(triggerTime)
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setId("sourceId")
                        .setExpiryTime(triggerTime + TimeUnit.DAYS.toMillis(28))
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        EventReport expectedEventReport =
                new EventReport.Builder()
                        .setTriggerPriority(3L)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerTime(triggerTime)
                        .setSourceEventId(source.getEventId())
                        .setStatus(EventReport.Status.PENDING)
                        .setAttributionDestinations(source.getAppDestinations())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setReportTime(
                                mEventReportWindowCalcDelegate.getReportingTime(
                                        source,
                                        trigger.getTriggerTime(),
                                        trigger.getDestinationType()))
                        .setSourceType(source.getSourceType())
                        .setRandomizedTriggerRate(
                                mSourceNoiseHandler.getRandomizedTriggerRate(source))
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(REGISTRATION_URI)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("3"), List.of("3"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_filterSet_eventLevelNotFilters_attributeFirstMatchingTrigger()
            throws DatastoreException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"2\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"not_filters\": [{\n"
                                        + "    \"key_1\": [\"value_1\"] \n"
                                        + "   }]\n"
                                        + "},"
                                        + "{\n"
                                        + "  \"trigger_data\": \"3\",\n"
                                        + "  \"priority\": \"3\",\n"
                                        + "  \"deduplication_key\": \"3\",\n"
                                        + "  \"not_filters\": [{\n"
                                        + "    \"key_1\": [\"value_1\"]}, {\n"
                                        + "    \"key_2\": [\"value_2\"]}, {\n"
                                        + "    \"key_1\": [\"matches_when_negated\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .setTriggerTime(triggerTime)
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setId("sourceId")
                        .setExpiryTime(triggerTime + TimeUnit.DAYS.toMillis(28))
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        EventReport expectedEventReport =
                new EventReport.Builder()
                        .setTriggerPriority(3L)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerTime(triggerTime)
                        .setSourceEventId(source.getEventId())
                        .setStatus(EventReport.Status.PENDING)
                        .setAttributionDestinations(source.getAppDestinations())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setReportTime(
                                mEventReportWindowCalcDelegate.getReportingTime(
                                        source,
                                        trigger.getTriggerTime(),
                                        trigger.getDestinationType()))
                        .setSourceType(source.getSourceType())
                        .setRandomizedTriggerRate(
                                mSourceNoiseHandler.getRandomizedTriggerRate(source))
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(REGISTRATION_URI)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("3"), List.of("3"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_eventLevelNotFilters_attributeFirstMatchingTrigger()
            throws DatastoreException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"2\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"not_filters\": [{\n"
                                        + "    \"key_1\": [\"value_1\"] \n"
                                        + "   }]\n"
                                        + "},"
                                        + "{\n"
                                        + "  \"trigger_data\": \"3\",\n"
                                        + "  \"priority\": \"3\",\n"
                                        + "  \"deduplication_key\": \"3\",\n"
                                        + "  \"not_filters\": [{\n"
                                        + "    \"key_1\": [\"value_1_x\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .setTriggerTime(triggerTime)
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setId("sourceId")
                        .setExpiryTime(triggerTime + TimeUnit.DAYS.toMillis(28))
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        EventReport expectedEventReport =
                new EventReport.Builder()
                        .setTriggerPriority(3L)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerTime(triggerTime)
                        .setSourceEventId(source.getEventId())
                        .setStatus(EventReport.Status.PENDING)
                        .setAttributionDestinations(source.getAppDestinations())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setReportTime(
                                mEventReportWindowCalcDelegate.getReportingTime(
                                        source,
                                        trigger.getTriggerTime(),
                                        trigger.getDestinationType()))
                        .setSourceType(source.getSourceType())
                        .setRandomizedTriggerRate(
                                mSourceNoiseHandler.getRandomizedTriggerRate(source))
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(REGISTRATION_URI)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("3"), List.of("3"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_eventLevelFiltersWithSourceType_attributeFirstMatchingTrigger()
            throws DatastoreException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"2\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"source_type\": [\"event\"], \n"
                                        + "    \"dummy_key\": [\"dummy_value\"] \n"
                                        + "   }]\n"
                                        + "},"
                                        + "{\n"
                                        + "  \"trigger_data\": \"3\",\n"
                                        + "  \"priority\": \"3\",\n"
                                        + "  \"deduplication_key\": \"3\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"source_type\": [\"navigation\"], \n"
                                        + "    \"dummy_key\": [\"dummy_value\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .setTriggerTime(triggerTime)
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setId("sourceId")
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setExpiryTime(triggerTime + TimeUnit.DAYS.toMillis(28))
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        EventReport expectedEventReport =
                new EventReport.Builder()
                        .setTriggerPriority(3L)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .setTriggerData(new UnsignedLong(3L))
                        .setTriggerTime(234324L)
                        .setSourceEventId(source.getEventId())
                        .setStatus(EventReport.Status.PENDING)
                        .setAttributionDestinations(source.getAppDestinations())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setReportTime(
                                mEventReportWindowCalcDelegate.getReportingTime(
                                        source,
                                        trigger.getTriggerTime(),
                                        trigger.getDestinationType()))
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRandomizedTriggerRate(
                                mSourceNoiseHandler.getRandomizedTriggerRate(source))
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(REGISTRATION_URI)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus = getAttributionStatus(
                List.of(trigger.getId()), List.of("3"), List.of("3"));
        verify(mMeasurementDao).updateSourceAttributedTriggers(
                eq(source.getId()), eq(expectedAttributionStatus));
        verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_filterSet_eventLevelFiltersFailToMatch_aggregateReportOnly()
            throws DatastoreException, JSONException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"2\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"product\": [\"value_11\"]}, {\n"
                                        + "    \"key_1\": [\"value_11\"] \n"
                                        + "   }]\n"
                                        + "},"
                                        + "{\n"
                                        + "  \"trigger_data\": \"3\",\n"
                                        + "  \"priority\": \"3\",\n"
                                        + "  \"deduplication_key\": \"3\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"product\": [\"value_21\"]}, {\n"
                                        + "    \"key_1\": [\"value_21\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .setTriggerTime(triggerTime)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString(
                                "{\"product\":[\"1234\", \"2345\"],"
                                        + "\"key_1\": [\"value_1_y\", \"value_2_y\"]}")
                        .setId("sourceId")
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        // Verify aggregate report registration origin.
        ArgumentCaptor<AggregateReport> reportArg = ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(reportArg.capture());
        AggregateReport aggregateReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, aggregateReport.getRegistrationOrigin());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_eventLevelFiltersFailToMatch_generateAggregateReportOnly()
            throws DatastoreException, JSONException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"2\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"key_1\": [\"value_11\"] \n"
                                        + "   }]\n"
                                        + "},"
                                        + "{\n"
                                        + "  \"trigger_data\": \"3\",\n"
                                        + "  \"priority\": \"3\",\n"
                                        + "  \"deduplication_key\": \"3\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"key_1\": [\"value_21\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .setTriggerTime(triggerTime)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString(
                                "{\"product\":[\"1234\",\"2345\"], \"key_1\": "
                                        + "[\"value_1_y\", \"value_2_y\"]}")
                        .setId("sourceId")
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void
            performAttribution_aggregateReportsExceedsLimitPerDestination_insertsOnlyEventReport()
                    throws DatastoreException, JSONException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(triggerTime)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setExpiryTime(triggerTime + TimeUnit.DAYS.toMillis(28))
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        int numAggregateReportPerDestination = 1024;
        int numEventReportPerDestination = 1023;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao, never()).insertAggregateReport(any());
        verify(mMeasurementDao).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttribution_maxAggregateReportsPerSourceFlagDisabled_shouldGenerateReport()
            throws DatastoreException, JSONException {
        // Disable flag for max aggregate reports per source.
        when(mFlags.getMeasurementEnableMaxAggregateReportsPerSource()).thenReturn(false);
        when(mFlags.getMeasurementMaxAggregateReportsPerSource()).thenReturn(20);
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(triggerTime)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setExpiryTime(triggerTime + TimeUnit.DAYS.toMillis(28))
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        int numAggregateReportPerDestination = 10;
        int numEventReportPerDestination = 10;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
        when(mMeasurementDao.getNumAggregateReportsPerSource(source.getId())).thenReturn(21);

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttribution_aggregateReportsExceedsLimitPerSource_insertsOnlyEventReport()
            throws DatastoreException, JSONException {
        // Enable flag for max aggregate reports per source.
        when(mFlags.getMeasurementEnableMaxAggregateReportsPerSource()).thenReturn(true);
        when(mFlags.getMeasurementMaxAggregateReportsPerSource()).thenReturn(20);
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(triggerTime)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setExpiryTime(triggerTime + TimeUnit.DAYS.toMillis(28))
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        int numAggregateReportPerDestination = 10;
        int numEventReportPerDestination = 10;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
        when(mMeasurementDao.getNumAggregateReportsPerSource(source.getId())).thenReturn(21);

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao, never()).insertAggregateReport(any());
        verify(mDebugReportApi)
                .scheduleTriggerDebugReport(
                        any(),
                        any(),
                        eq("21"),
                        any(),
                        eq(DebugReportApi.Type.TRIGGER_AGGREGATE_EXCESSIVE_REPORTS));
        verify(mMeasurementDao).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttribution_eventReportsExceedsLimit_insertsOnlyAggregateReport()
            throws DatastoreException, JSONException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(triggerTime)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        int numAggregateReportPerDestination = 1023;
        int numEventReportPerDestination = 1024;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttribution_aggregateAndEventReportsExceedsLimit_noReportInsertion()
            throws DatastoreException, JSONException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(triggerTime)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        int numAggregateReportPerDestination = 1024;
        int numEventReportPerDestination = 1024;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertAggregateReport(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttribution_aggregateAndEventReportsDoNotExceedsLimit_ReportInsertion()
            throws DatastoreException, JSONException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(triggerTime)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setExpiryTime(triggerTime + TimeUnit.DAYS.toMillis(28))
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        int numAggregateReportPerDestination = 1023;
        int numEventReportPerDestination = 1023;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(
                        source.getAppDestinations(),
                        source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao).insertAggregateReport(any());
        // Verify event report registration origin.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao).insertEventReport(reportArg.capture());
        EventReport eventReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_withXnaConfig_originalSourceWinsAndOtherIgnored()
            throws DatastoreException {
        // Setup
        String adtechEnrollment = "AdTech1-Ads";
        AttributionConfig attributionConfig =
                new AttributionConfig.Builder()
                        .setSourceAdtech(adtechEnrollment)
                        .setSourcePriorityRange(new Pair<>(1L, 1000L))
                        .setSourceFilters(null)
                        .setPriority(1L)
                        .setExpiry(604800L)
                        .setFilterData(null)
                        .build();
        Trigger trigger =
                getXnaTriggerBuilder()
                        .setFilters(null)
                        .setNotFilters(null)
                        .setAttributionConfig(
                                new JSONArray(
                                                Collections.singletonList(
                                                        attributionConfig.serializeAsJson(mFlags)))
                                        .toString())
                        .build();

        String aggregatableSource = SourceFixture.ValidSourceParams.buildAggregateSource();
        Source xnaSource =
                createXnaSourceBuilder()
                        .setEnrollmentId(adtechEnrollment)
                        // Priority changes to 1 for derived source
                        .setPriority(100L)
                        .setAggregateSource(aggregatableSource)
                        .setFilterDataString(null)
                        .setSharedAggregationKeys(
                                new JSONArray(Arrays.asList("campaignCounts", "geoValue"))
                                        .toString())
                        .build();
        // winner due to install attribution and higher priority
        Source triggerEnrollmentSource1 =
                createXnaSourceBuilder()
                        .setEnrollmentId(trigger.getEnrollmentId())
                        .setPriority(2L)
                        .setFilterDataString(null)
                        .setAggregateSource(aggregatableSource)
                        .build();

        Source triggerEnrollmentSource2 =
                createXnaSourceBuilder()
                        .setEnrollmentId(trigger.getEnrollmentId())
                        .setPriority(2L)
                        .setFilterDataString(null)
                        .setAggregateSource(aggregatableSource)
                        .setInstallAttributed(false)
                        .build();

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(xnaSource);
        matchingSourceList.add(triggerEnrollmentSource1);
        matchingSourceList.add(triggerEnrollmentSource2);
        when(mMeasurementDao.fetchTriggerMatchingSourcesForXna(any(), any()))
                .thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceDestinations(triggerEnrollmentSource1.getId()))
                .thenReturn(
                        Pair.create(
                                triggerEnrollmentSource1.getAppDestinations(),
                                triggerEnrollmentSource1.getWebDestinations()));
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mFlags.getMeasurementEnableXNA()).thenReturn(true);

        // Execution
        AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

        // Assertion
        assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao).insertEventReport(any());
        verify(mMeasurementDao, times(2)).insertAttribution(any());
        verify(mMeasurementDao)
                .insertIgnoredSourceForEnrollment(xnaSource.getId(), trigger.getEnrollmentId());
        verify(mMeasurementDao)
                .updateSourceStatus(
                        eq(Collections.singletonList(triggerEnrollmentSource2.getId())),
                        eq(Source.Status.IGNORED));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_withXnaConfig_derivedSourceWinsAndOtherIgnored()
            throws DatastoreException {
        // Setup
        String adtechEnrollment = "AdTech1-Ads";
        AttributionConfig attributionConfig =
                new AttributionConfig.Builder()
                        .setExpiry(604800L)
                        .setSourceAdtech(adtechEnrollment)
                        .setSourcePriorityRange(new Pair<>(1L, 1000L))
                        .setSourceFilters(null)
                        .setPriority(50L)
                        .setExpiry(604800L)
                        .setFilterData(null)
                        .build();
        Trigger trigger =
                getXnaTriggerBuilder()
                        .setFilters(null)
                        .setNotFilters(null)
                        .setAttributionConfig(
                                new JSONArray(
                                                Collections.singletonList(
                                                        attributionConfig.serializeAsJson(mFlags)))
                                        .toString())
                        .build();

        String aggregatableSource = SourceFixture.ValidSourceParams.buildAggregateSource();
        // Its derived source will be winner due to install attribution and higher priority
        Source xnaSource =
                createXnaSourceBuilder()
                        .setEnrollmentId(adtechEnrollment)
                        // Priority changes to 50 for derived source
                        .setPriority(1L)
                        .setAggregateSource(aggregatableSource)
                        .setFilterDataString(null)
                        .setSharedAggregationKeys(
                                new JSONArray(Arrays.asList("campaignCounts", "geoValue"))
                                        .toString())
                        .build();
        Source triggerEnrollmentSource1 =
                createXnaSourceBuilder()
                        .setEnrollmentId(trigger.getEnrollmentId())
                        .setPriority(2L)
                        .setFilterDataString(null)
                        .setAggregateSource(aggregatableSource)
                        .build();

        Source triggerEnrollmentSource2 =
                createXnaSourceBuilder()
                        .setEnrollmentId(trigger.getEnrollmentId())
                        .setPriority(2L)
                        .setFilterDataString(null)
                        .setAggregateSource(aggregatableSource)
                        .setInstallAttributed(false)
                        .build();

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(xnaSource);
        matchingSourceList.add(triggerEnrollmentSource1);
        matchingSourceList.add(triggerEnrollmentSource2);
        when(mMeasurementDao.fetchTriggerMatchingSourcesForXna(any(), any()))
                .thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mFlags.getMeasurementEnableXNA()).thenReturn(true);

        // Execution
        AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

        // Assertion
        assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        // Verify aggregate report registration origin.
        ArgumentCaptor<AggregateReport> reportArg = ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao).insertAggregateReport(reportArg.capture());
        AggregateReport aggregateReport = reportArg.getValue();
        assertEquals(REGISTRATION_URI, aggregateReport.getRegistrationOrigin());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao).insertAttribution(any());
        verify(mMeasurementDao)
                .updateSourceStatus(
                        eq(
                                Arrays.asList(
                                        triggerEnrollmentSource1.getId(),
                                        triggerEnrollmentSource2.getId())),
                        eq(Source.Status.IGNORED));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttributions_xnaDisabled_derivedSourceIgnored() throws DatastoreException {
        // Setup
        String adtechEnrollment = "AdTech1-Ads";
        AttributionConfig attributionConfig =
                new AttributionConfig.Builder()
                        .setExpiry(604800L)
                        .setSourceAdtech(adtechEnrollment)
                        .setSourcePriorityRange(new Pair<>(1L, 1000L))
                        .setSourceFilters(null)
                        .setPriority(50L)
                        .setExpiry(604800L)
                        .setFilterData(null)
                        .build();
        Trigger trigger =
                getXnaTriggerBuilder()
                        .setFilters(null)
                        .setNotFilters(null)
                        .setAttributionConfig(
                                new JSONArray(
                                                Collections.singletonList(
                                                        attributionConfig.serializeAsJson(mFlags)))
                                        .toString())
                        .build();

        String aggregatableSource = SourceFixture.ValidSourceParams.buildAggregateSource();
        // Its derived source will be winner due to install attribution and higher priority
        Source xnaSource =
                createXnaSourceBuilder()
                        .setEnrollmentId(adtechEnrollment)
                        // Priority changes to 50 for derived source
                        .setPriority(1L)
                        .setAggregateSource(aggregatableSource)
                        .setFilterDataString(null)
                        .setSharedAggregationKeys(
                                new JSONArray(Arrays.asList("campaignCounts", "geoValue"))
                                        .toString())
                        .build();
        Source triggerEnrollmentSource1 =
                createXnaSourceBuilder()
                        .setEnrollmentId(trigger.getEnrollmentId())
                        .setPriority(2L)
                        .setFilterDataString(null)
                        .setAggregateSource(aggregatableSource)
                        .build();

        Source triggerEnrollmentSource2 =
                createXnaSourceBuilder()
                        .setEnrollmentId(trigger.getEnrollmentId())
                        .setPriority(2L)
                        .setFilterDataString(null)
                        .setAggregateSource(aggregatableSource)
                        .setInstallAttributed(false)
                        .build();

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(xnaSource);
        matchingSourceList.add(triggerEnrollmentSource1);
        matchingSourceList.add(triggerEnrollmentSource2);
        when(mMeasurementDao.fetchTriggerMatchingSourcesForXna(any(), any()))
                .thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mFlags.getMeasurementEnableXNA()).thenReturn(false);

        // Execution
        AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

        // Assertion
        assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).insertAggregateReport(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao, never()).insertAttribution(any());
        verify(mMeasurementDao, never())
                .updateSourceStatus(
                        eq(
                                Arrays.asList(
                                        triggerEnrollmentSource1.getId(),
                                        triggerEnrollmentSource2.getId())),
                        eq(Source.Status.IGNORED));
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performAttribution_flexEventReport_oneTriggerGenerateTwoReports()
            throws DatastoreException, JSONException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"1\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"value\": \"1000\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(triggerTime)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        TriggerSpecs triggerSpecs = SourceFixture.getValidTriggerSpecsValueSum();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .setTriggerSpecsString(triggerSpecs.encodeToJson())
                        .setMaxEventLevelReports(triggerSpecs.getMaxReports())
                        .setPrivacyParameters(triggerSpecs.encodePrivacyParametersToJsonString())
                        .build();
        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        int numAggregateReportPerDestination = 1023;
        int numEventReportPerDestination = 1023;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao, times(2)).insertEventReport(any());
        verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
        verify(mMeasurementDao, times(1)).getSourceEventReports(any());
        verify(mMeasurementDao, times(1)).updateSourceAttributedTriggers(
                eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
        assertEquals(1, source.getTriggerSpecs().getAttributedTriggers().size());
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    /**
     * The triggerData in the trigger didn't match any one of the trigger data in the source
     * registration. No report generated
     */
    public void performAttribution_flexEventReport_triggerDataMismatch()
            throws DatastoreException, JSONException {
        // Setup
        long triggerTime = 234324L;
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"6\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"value\": \"1000\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(triggerTime)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();
        TriggerSpecs triggerSpecs = SourceFixture.getValidTriggerSpecsValueSum();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventReportWindow(triggerTime + 1L)
                        .setAggregatableReportWindow(triggerTime + 1L)
                        .setTriggerSpecsString(triggerSpecs.encodeToJson())
                        .setMaxEventLevelReports(triggerSpecs.getMaxReports())
                        .setPrivacyParameters(triggerSpecs.encodePrivacyParametersToJsonString())
                        .build();
        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        int numAggregateReportPerDestination = 1023;
        int numEventReportPerDestination = 1023;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
        verify(mMeasurementDao, never()).getSourceEventReports(any());
        verify(mMeasurementDao, never()).updateSourceAttributedTriggers(anyString(), anyString());
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    /**
     * Status before attribution: 1 trigger attributed and 1 report generated; Incoming trigger
     * status: 2 new reports should be generated; Result: 2 reports written into DB and no
     * competition condition; debug reports and trigger debug keys populated only when all trigger
     * contributors have debug keys.
     */
    public void performAttribution_flexEventReportTwoNonCompetingTriggersAllDebugReports()
            throws DatastoreException, JSONException {
        // Setup
        long baseTime = System.currentTimeMillis();
        UnsignedLong triggerData1 = new UnsignedLong(1L);
        UnsignedLong triggerData2 = new UnsignedLong(2L);
        UnsignedLong sourceDebugKey = new UnsignedLong(777L);
        UnsignedLong debugKey1 = new UnsignedLong(4L);
        UnsignedLong debugKey2 = new UnsignedLong(55L);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setDestinationType(EventSurfaceType.APP)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"" + triggerData2 + "\","
                                        + "  \"priority\": \"123\","
                                        + "  \"value\": \"105\","
                                        + "  \"deduplication_key\": \"123\""
                                        + "}"
                                        + "]")
                        .setFilters(
                                "[{"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]"
                                        + "}]")
                        .setTriggerTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(1)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .setAdIdPermission(true)
                        .setDebugKey(debugKey2)
                        .build();

        Pair<Long, Long> firstBucket = Pair.create(10L, 99L);
        Pair<Long, Long> secondBucket = Pair.create(100L, TriggerSpecs.MAX_BUCKET_THRESHOLD);

        final EventReport currentEventReport1 =
                new EventReport.Builder()
                        .setId("100")
                        .setSourceEventId(new UnsignedLong(22L))
                        .setEnrollmentId("another-enrollment-id")
                        .setAttributionDestinations(List.of(Uri.parse("android-app://com.ignored")))
                        .setReportTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setStatus(EventReport.Status.PENDING)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
                        .setTriggerTime(baseTime + 3000L)
                        .setTriggerData(triggerData1)
                        .setTriggerPriority(123L)
                        .setTriggerValue(30)
                        .setTriggerSummaryBucket(firstBucket)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .setTriggerDebugKey(debugKey1)
                        .build();

        TriggerSpecs templateTriggerSpecs = SourceFixture.getValidTriggerSpecsValueSum();
        JSONArray existingAttributes = new JSONArray();
        JSONObject triggerRecord1 = generateTriggerJsonFromEventReport(currentEventReport1);

        existingAttributes.put(triggerRecord1);

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setEventTime(baseTime)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(2)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregatableReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(2)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setTriggerSpecsString(templateTriggerSpecs.encodeToJson())
                        .setMaxEventLevelReports(templateTriggerSpecs.getMaxReports())
                        .setEventAttributionStatus(existingAttributes.toString())
                        .setPrivacyParameters(
                                templateTriggerSpecs.encodePrivacyParametersToJsonString())
                        .setAdIdPermission(true)
                        .setDebugKey(sourceDebugKey)
                        .build();

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any()))
                .thenReturn(new ArrayList<>(Collections.singletonList(currentEventReport1)));
        int numAggregateReportPerDestination = 10;
        int numEventReportPerDestination = 10;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));

        verify(mMeasurementDao, times(1)).getSourceEventReports(any());
        ArgumentCaptor<EventReport> insertedReportsCaptor =
                ArgumentCaptor.forClass(EventReport.class);
        ArgumentCaptor<List<EventReport>> deletedReportsCaptor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(mMeasurementDao, times(3)).insertEventReport(insertedReportsCaptor.capture());
        verify(mMeasurementDao, times(1)).deleteFlexEventReportsAndAttributions(
                deletedReportsCaptor.capture());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
        assertEquals(1, deletedReportsCaptor.getValue().size());
        List<EventReport> insertedEventReports = insertedReportsCaptor.getAllValues();
        assertEquals(firstBucket, insertedEventReports.get(0).getTriggerSummaryBucket());
        assertEquals(firstBucket, insertedEventReports.get(1).getTriggerSummaryBucket());
        assertEquals(secondBucket, insertedEventReports.get(2).getTriggerSummaryBucket());
        assertEquals(triggerData1, insertedEventReports.get(0).getTriggerData());
        assertEquals(triggerData2, insertedEventReports.get(1).getTriggerData());
        assertEquals(triggerData2, insertedEventReports.get(2).getTriggerData());
        assertEquals(List.of(debugKey1), insertedEventReports.get(0).getTriggerDebugKeys());
        assertEquals(List.of(debugKey2), insertedEventReports.get(1).getTriggerDebugKeys());
        assertEquals(List.of(debugKey2), insertedEventReports.get(2).getTriggerDebugKeys());
        assertNull(insertedEventReports.get(0).getSourceDebugKey());
        assertEquals(sourceDebugKey, insertedEventReports.get(1).getSourceDebugKey());
        assertEquals(sourceDebugKey, insertedEventReports.get(2).getSourceDebugKey());
        assertEquals(
                EventReport.DebugReportStatus.NONE,
                insertedEventReports.get(0).getDebugReportStatus());
        assertEquals(
                EventReport.DebugReportStatus.PENDING,
                insertedEventReports.get(1).getDebugReportStatus());
        assertEquals(
                EventReport.DebugReportStatus.PENDING,
                insertedEventReports.get(2).getDebugReportStatus());
        long reportTime = TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS;
        assertEquals(reportTime, insertedEventReports.get(0).getReportTime() - baseTime);
        assertEquals(reportTime, insertedEventReports.get(1).getReportTime() - baseTime);
        assertEquals(reportTime, insertedEventReports.get(2).getReportTime() - baseTime);
        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
        verify(mMeasurementDao, times(1)).updateSourceAttributedTriggers(
                eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
        assertEquals(2, source.getTriggerSpecs().getAttributedTriggers().size());
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
    }

    @Test
    /*
     * Status before attribution: 1 trigger attributed and 2 report generated with triggerData 1;
     * Incoming trigger status: 2 reports should be generated for triggerData 2 Result: incoming
     * trigger has higher priority and one previous report should be deleted; debug reports and
     * trigger debug keys populated only when all trigger contributors have debug keys.
     */
    public void performAttribution_flexEventReportSecondTriggerHigherPriorityAllDebugReports()
            throws DatastoreException, JSONException {
        // Setup
        long baseTime = System.currentTimeMillis();
        UnsignedLong triggerData1 = new UnsignedLong(1L);
        UnsignedLong triggerData2 = new UnsignedLong(2L);
        UnsignedLong sourceDebugKey = new UnsignedLong(777L);
        UnsignedLong debugKey1 = new UnsignedLong(4L);
        UnsignedLong debugKey2 = new UnsignedLong(55L);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "["
                                        + "{"
                                        + "  \"trigger_data\": \"" + triggerData2 + "\","
                                        + "  \"priority\": \"123\","
                                        + "  \"value\": \"105\","
                                        + "  \"deduplication_key\": \"1234\""
                                        + "}"
                                        + "]")
                        .setFilters(
                                "[{"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]"
                                        + "}]")
                        .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1) + 4800000)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .setAdIdPermission(true)
                        .setDebugKey(debugKey2)
                        .build();

        final EventReport.Builder eventReportBuilder =
                new EventReport.Builder()
                        .setId("100")
                        .setSourceEventId(new UnsignedLong(22L))
                        .setEnrollmentId("another-enrollment-id")
                        .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                        .setReportTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(2)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setStatus(EventReport.Status.PENDING)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
                        .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1))
                        .setTriggerData(triggerData1)
                        .setTriggerPriority(121L)
                        .setTriggerValue(101)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .setTriggerDebugKey(debugKey1);

        TriggerSpecs templateTriggerSpecs = SourceFixture.getValidTriggerSpecsValueSum();
        JSONArray existingAttributes = new JSONArray();
        JSONObject triggerRecord1 = generateTriggerJsonFromEventReport(eventReportBuilder.build());

        existingAttributes.put(triggerRecord1);

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportDedupKeys(
                                new ArrayList<>(Collections.singletonList(new UnsignedLong(3L))))
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString(
                                "{"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]"
                                        + "}")
                        .setEventTime(baseTime)
                        .setEventReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregatableReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setTriggerSpecsString(templateTriggerSpecs.encodeToJson())
                        .setMaxEventLevelReports(templateTriggerSpecs.getMaxReports())
                        .setEventAttributionStatus(existingAttributes.toString())
                        .setPrivacyParameters(
                                templateTriggerSpecs.encodePrivacyParametersToJsonString())
                        .setAdIdPermission(true)
                        .setDebugKey(sourceDebugKey)
                        .build();

        Pair<Long, Long> firstBucket = Pair.create(10L, 99L);
        Pair<Long, Long> secondBucket = Pair.create(100L, TriggerSpecs.MAX_BUCKET_THRESHOLD);

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any()))
                .thenReturn(
                        new ArrayList<>(Arrays.asList(
                                eventReportBuilder
                                        .setTriggerSummaryBucket(firstBucket)
                                        .build(),
                                eventReportBuilder
                                        .setTriggerSummaryBucket(secondBucket)
                                        .build())));
        int numAggregateReportPerDestination = 0;
        int numEventReportPerDestination = 2;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));

        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao, times(1)).getSourceEventReports(any());
        verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
        ArgumentCaptor<EventReport> insertedReportsCaptor =
                ArgumentCaptor.forClass(EventReport.class);
        ArgumentCaptor<List<EventReport>> deletedReportsCaptor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(mMeasurementDao, times(3)).insertEventReport(insertedReportsCaptor.capture());
        verify(mMeasurementDao, times(1)).deleteFlexEventReportsAndAttributions(
                deletedReportsCaptor.capture());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
        List<EventReport> insertedEventReports = insertedReportsCaptor.getAllValues();
        assertEquals(firstBucket, insertedEventReports.get(0).getTriggerSummaryBucket());
        assertEquals(secondBucket, insertedEventReports.get(1).getTriggerSummaryBucket());
        assertEquals(firstBucket, insertedEventReports.get(2).getTriggerSummaryBucket());
        assertEquals(triggerData2, insertedEventReports.get(0).getTriggerData());
        assertEquals(triggerData2, insertedEventReports.get(1).getTriggerData());
        assertEquals(triggerData1, insertedEventReports.get(2).getTriggerData());
        assertEquals(List.of(debugKey2), insertedEventReports.get(0).getTriggerDebugKeys());
        assertEquals(List.of(debugKey2), insertedEventReports.get(1).getTriggerDebugKeys());
        assertEquals(List.of(debugKey1), insertedEventReports.get(2).getTriggerDebugKeys());
        assertEquals(sourceDebugKey, insertedEventReports.get(0).getSourceDebugKey());
        assertEquals(sourceDebugKey, insertedEventReports.get(1).getSourceDebugKey());
        assertNull(insertedEventReports.get(2).getSourceDebugKey());
        assertEquals(
                EventReport.DebugReportStatus.PENDING,
                insertedEventReports.get(0).getDebugReportStatus());
        assertEquals(
                EventReport.DebugReportStatus.PENDING,
                insertedEventReports.get(1).getDebugReportStatus());
        assertEquals(
                EventReport.DebugReportStatus.NONE,
                insertedEventReports.get(2).getDebugReportStatus());
        assertEquals(2, deletedReportsCaptor.getValue().size());
        long reportTime = TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS;
        assertEquals(reportTime, insertedEventReports.get(0).getReportTime() - baseTime);
        assertEquals(reportTime, insertedEventReports.get(1).getReportTime() - baseTime);
        assertEquals(reportTime, insertedEventReports.get(2).getReportTime() - baseTime);
        verify(mMeasurementDao, times(1)).updateSourceAttributedTriggers(
                eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
        assertEquals(2, source.getTriggerSpecs().getAttributedTriggers().size());
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
    }

    @Test
    /*
     * Status before attribution: 1 trigger attributed and 2 report generated with triggerData 1;
     * Incoming trigger status: 2 reports should be generated for triggerData 2 Result: incoming
     * trigger has lower priority, no previous report should be deleted and only 1 new report
     * inserted into DB; debug reports and trigger debug keys populated only when all trigger
     * contributors have debug keys.
     */
    public void performAttribution_flexEventReportSecondTriggerLowerPrioritySomeDebugReports()
            throws DatastoreException, JSONException {
        // Setup
        long baseTime = System.currentTimeMillis();
        UnsignedLong triggerData1 = new UnsignedLong(1L);
        UnsignedLong triggerData2 = new UnsignedLong(2L);
        UnsignedLong sourceDebugKey = new UnsignedLong(777L);
        UnsignedLong debugKey2 = new UnsignedLong(55L);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "["
                                        + "{"
                                        + "  \"trigger_data\": \"" + triggerData2 + "\","
                                        + "  \"priority\": \"123\","
                                        + "  \"value\": \"105\","
                                        + "  \"deduplication_key\": \"1\""
                                        + "}"
                                        + "]")
                        .setFilters(
                                "[{"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]"
                                        + "}]")
                        .setTriggerTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(1)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .setAdIdPermission(true)
                        .setDebugKey(debugKey2)
                        .build();

        Pair<Long, Long> firstBucket = Pair.create(10L, 99L);
        Pair<Long, Long> secondBucket = Pair.create(100L, TriggerSpecs.MAX_BUCKET_THRESHOLD);

        final EventReport.Builder eventReportBuilder =
                new EventReport.Builder()
                        .setId("100")
                        .setSourceEventId(new UnsignedLong(22L))
                        .setEnrollmentId("another-enrollment-id")
                        .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                        .setReportTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(2)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setStatus(EventReport.Status.PENDING)
                        .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
                        .setTriggerTime(baseTime + 3000)
                        .setTriggerData(triggerData1)
                        .setTriggerPriority(124L)
                        .setSourceDebugKey(sourceDebugKey)
                        .setTriggerValue(103)
                        .setTriggerDedupKey(new UnsignedLong(3L));

        TriggerSpecs templateTriggerSpecs = SourceFixture.getValidTriggerSpecsValueSum();
        JSONArray existingAttributes = new JSONArray();
        JSONObject triggerRecord1 = generateTriggerJsonFromEventReport(eventReportBuilder.build());

        existingAttributes.put(triggerRecord1);

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportDedupKeys(
                                new ArrayList<>(Collections.singleton(new UnsignedLong(3L))))
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventTime(baseTime)
                        .setEventReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregatableReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setTriggerSpecsString(templateTriggerSpecs.encodeToJson())
                        .setMaxEventLevelReports(templateTriggerSpecs.getMaxReports())
                        .setEventAttributionStatus(existingAttributes.toString())
                        .setPrivacyParameters(
                                templateTriggerSpecs.encodePrivacyParametersToJsonString())
                        .setAdIdPermission(true)
                        .setDebugKey(sourceDebugKey)
                        .build();

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any()))
                .thenReturn(
                        new ArrayList<>(Arrays.asList(
                                eventReportBuilder
                                        .setTriggerSummaryBucket(firstBucket)
                                        .build(),
                                eventReportBuilder
                                        .setTriggerSummaryBucket(secondBucket)
                                        .build())));
        int numAggregateReportPerDestination = 24;
        int numEventReportPerDestination = 35;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));

        verify(mMeasurementDao, times(1)).getSourceEventReports(any());
        ArgumentCaptor<EventReport> insertedReportsCaptor =
                ArgumentCaptor.forClass(EventReport.class);
        ArgumentCaptor<List<EventReport>> deletedReportsCaptor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(mMeasurementDao, times(3)).insertEventReport(insertedReportsCaptor.capture());
        verify(mMeasurementDao, times(1)).deleteFlexEventReportsAndAttributions(
                deletedReportsCaptor.capture());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
        assertEquals(2, deletedReportsCaptor.getValue().size());
        List<EventReport> insertedEventReports = insertedReportsCaptor.getAllValues();
        assertEquals(firstBucket, insertedEventReports.get(0).getTriggerSummaryBucket());
        assertEquals(secondBucket, insertedEventReports.get(1).getTriggerSummaryBucket());
        assertEquals(firstBucket, insertedEventReports.get(2).getTriggerSummaryBucket());
        assertEquals(triggerData1, insertedEventReports.get(0).getTriggerData());
        assertEquals(triggerData1, insertedEventReports.get(1).getTriggerData());
        assertEquals(triggerData2, insertedEventReports.get(2).getTriggerData());
        assertEquals(Collections.emptyList(), insertedEventReports.get(0).getTriggerDebugKeys());
        assertEquals(Collections.emptyList(), insertedEventReports.get(1).getTriggerDebugKeys());
        assertEquals(List.of(debugKey2), insertedEventReports.get(2).getTriggerDebugKeys());
        assertEquals(sourceDebugKey, insertedEventReports.get(0).getSourceDebugKey());
        assertEquals(sourceDebugKey, insertedEventReports.get(1).getSourceDebugKey());
        assertEquals(sourceDebugKey, insertedEventReports.get(2).getSourceDebugKey());
        assertEquals(
                EventReport.DebugReportStatus.NONE,
                insertedEventReports.get(0).getDebugReportStatus());
        assertEquals(
                EventReport.DebugReportStatus.NONE,
                insertedEventReports.get(1).getDebugReportStatus());
        assertEquals(
                EventReport.DebugReportStatus.PENDING,
                insertedEventReports.get(2).getDebugReportStatus());
        long reportTime = TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS;
        assertEquals(reportTime, insertedEventReports.get(0).getReportTime() - baseTime);
        assertEquals(reportTime, insertedEventReports.get(1).getReportTime() - baseTime);
        assertEquals(reportTime, insertedEventReports.get(2).getReportTime() - baseTime);
        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
        verify(mMeasurementDao, times(1)).updateSourceAttributedTriggers(
                eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
        assertEquals(2, source.getTriggerSpecs().getAttributedTriggers().size());
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
    }

    @Test
    /*
     * Status before attribution: 1 trigger attributed and 2 report generated with triggerData 1;
     * Incoming trigger is prior to window start, attribution exits before altering event reports.
     */
    public void performAttribution_flexEventReportSecondTriggerWindowNotStarted_reportsUnaltered()
            throws DatastoreException, JSONException {
        // Setup
        long baseTime = System.currentTimeMillis();
        UnsignedLong triggerData1 = new UnsignedLong(1L);
        UnsignedLong triggerData2 = new UnsignedLong(2L);
        UnsignedLong sourceDebugKey = new UnsignedLong(777L);
        UnsignedLong debugKey2 = new UnsignedLong(55L);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "["
                                        + "{"
                                        + "  \"trigger_data\": \"" + triggerData2 + "\","
                                        + "  \"priority\": \"123\","
                                        + "  \"value\": \"105\","
                                        + "  \"deduplication_key\": \"1\""
                                        + "}"
                                        + "]")
                        .setFilters(
                                "[{"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]"
                                        + "}]")
                        // Trigger time is one hour before trigger specs start time
                        .setTriggerTime(baseTime + TimeUnit.HOURS.toMillis(4))
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .setAdIdPermission(true)
                        .setDebugKey(debugKey2)
                        .build();

        Pair<Long, Long> firstBucket = Pair.create(10L, 99L);
        Pair<Long, Long> secondBucket = Pair.create(100L, TriggerSpecs.MAX_BUCKET_THRESHOLD);

        final EventReport.Builder eventReportBuilder =
                new EventReport.Builder()
                        .setId("100")
                        .setSourceEventId(new UnsignedLong(22L))
                        .setEnrollmentId("another-enrollment-id")
                        .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                        .setReportTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(2)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setStatus(EventReport.Status.PENDING)
                        .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
                        .setTriggerTime(baseTime + 3000)
                        .setTriggerData(triggerData1)
                        .setTriggerPriority(124L)
                        .setSourceDebugKey(sourceDebugKey)
                        .setTriggerValue(103)
                        .setTriggerDedupKey(new UnsignedLong(3L));

        TriggerSpecs templateTriggerSpecs =
                SourceFixture.getValidTriggerSpecsValueSumWithStartTime(TimeUnit.HOURS.toMillis(5));
        JSONArray existingAttributes = new JSONArray();
        JSONObject triggerRecord1 = generateTriggerJsonFromEventReport(eventReportBuilder.build());

        existingAttributes.put(triggerRecord1);

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportDedupKeys(
                                new ArrayList<>(Collections.singleton(new UnsignedLong(3L))))
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(null)
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventTime(baseTime)
                        .setEventReportWindow(null)
                        .setAggregatableReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setTriggerSpecsString(templateTriggerSpecs.encodeToJson())
                        .setMaxEventLevelReports(templateTriggerSpecs.getMaxReports())
                        .setEventAttributionStatus(existingAttributes.toString())
                        .setPrivacyParameters(
                                templateTriggerSpecs.encodePrivacyParametersToJsonString())
                        .setAdIdPermission(true)
                        .setDebugKey(sourceDebugKey)
                        .build();

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any()))
                .thenReturn(
                        new ArrayList<>(Arrays.asList(
                                eventReportBuilder
                                        .setTriggerSummaryBucket(firstBucket)
                                        .build(),
                                eventReportBuilder
                                        .setTriggerSummaryBucket(secondBucket)
                                        .build())));
        int numAggregateReportPerDestination = 24;
        int numEventReportPerDestination = 35;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.IGNORED));

        verify(mMeasurementDao, never()).getSourceEventReports(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao, never()).deleteFlexEventReportsAndAttributions(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
    }

    @Test
    /*
     * Status before attribution: 1 trigger attributed and 2 report generated with triggerData 1;
     * Incoming trigger is prior to window start, attribution exits before altering event reports.
     */
    public void performAttribution_flexEventReportSecondTriggerWindowPassed_reportsUnaltered()
            throws DatastoreException, JSONException {
        // Setup
        long baseTime = System.currentTimeMillis();
        UnsignedLong triggerData1 = new UnsignedLong(1L);
        UnsignedLong triggerData2 = new UnsignedLong(2L);
        UnsignedLong sourceDebugKey = new UnsignedLong(777L);
        UnsignedLong debugKey2 = new UnsignedLong(55L);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "["
                                        + "{"
                                        + "  \"trigger_data\": \"" + triggerData2 + "\","
                                        + "  \"priority\": \"123\","
                                        + "  \"value\": \"105\","
                                        + "  \"deduplication_key\": \"1\""
                                        + "}"
                                        + "]")
                        .setFilters(
                                "[{"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]"
                                        + "}]")
                        // Trigger time is one day after trigger specs window ends
                        .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(8))
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .setAdIdPermission(true)
                        .setDebugKey(debugKey2)
                        .build();

        Pair<Long, Long> firstBucket = Pair.create(10L, 99L);
        Pair<Long, Long> secondBucket = Pair.create(100L, TriggerSpecs.MAX_BUCKET_THRESHOLD);

        final EventReport.Builder eventReportBuilder =
                new EventReport.Builder()
                        .setId("100")
                        .setSourceEventId(new UnsignedLong(22L))
                        .setEnrollmentId("another-enrollment-id")
                        .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                        .setReportTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(2)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setStatus(EventReport.Status.PENDING)
                        .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
                        .setTriggerTime(baseTime + 3000)
                        .setTriggerData(triggerData1)
                        .setTriggerPriority(124L)
                        .setSourceDebugKey(sourceDebugKey)
                        .setTriggerValue(103)
                        .setTriggerDedupKey(new UnsignedLong(3L));

        TriggerSpecs templateTriggerSpecs =
                SourceFixture.getValidTriggerSpecsValueSumWithStartTime(TimeUnit.HOURS.toMillis(5));
        JSONArray existingAttributes = new JSONArray();
        JSONObject triggerRecord1 = generateTriggerJsonFromEventReport(eventReportBuilder.build());

        existingAttributes.put(triggerRecord1);

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportDedupKeys(
                                new ArrayList<>(Collections.singleton(new UnsignedLong(3L))))
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(null)
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventTime(baseTime)
                        .setEventReportWindow(null)
                        .setAggregatableReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setTriggerSpecsString(templateTriggerSpecs.encodeToJson())
                        .setMaxEventLevelReports(templateTriggerSpecs.getMaxReports())
                        .setEventAttributionStatus(existingAttributes.toString())
                        .setPrivacyParameters(
                                templateTriggerSpecs.encodePrivacyParametersToJsonString())
                        .setAdIdPermission(true)
                        .setDebugKey(sourceDebugKey)
                        .build();

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any()))
                .thenReturn(
                        new ArrayList<>(Arrays.asList(
                                eventReportBuilder
                                        .setTriggerSummaryBucket(firstBucket)
                                        .build(),
                                eventReportBuilder
                                        .setTriggerSummaryBucket(secondBucket)
                                        .build())));
        int numAggregateReportPerDestination = 24;
        int numEventReportPerDestination = 35;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.IGNORED));

        verify(mMeasurementDao, never()).getSourceEventReports(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao, never()).deleteFlexEventReportsAndAttributions(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
    }

    @Test
    /**
     * Status before attribution: 2 trigger attributed and 2 report generated with triggerData 1 and
     * 2, respectively; Incoming trigger status: 2 reports should be generated for triggerData 1
     * Result: incoming trigger has higher priority so previous report with triggerData 2 is
     * deleted.
     */
    public void performAttribution_flexEventReport_insertThirdTriggerPriorityReset()
            throws DatastoreException, JSONException {
        // Setup
        long baseTime = System.currentTimeMillis();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"1\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"value\": \"105\",\n"
                                        + "  \"deduplication_key\": \"111\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(1)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();

        Pair<Long, Long> firstBucket = Pair.create(10L, 99L);
        Pair<Long, Long> secondBucket = Pair.create(100L, TriggerSpecs.MAX_BUCKET_THRESHOLD);

        final EventReport currentEventReport1 =
                new EventReport.Builder()
                        .setId("100")
                        .setTriggerId("01234")
                        .setSourceEventId(new UnsignedLong(22L))
                        .setEnrollmentId("another-enrollment-id")
                        .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                        .setReportTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(2)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setStatus(EventReport.Status.PENDING)
                        .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
                        .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1))
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(50)
                        .setTriggerValue(20)
                        .setTriggerSummaryBucket(firstBucket)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .build();
        final EventReport currentEventReport2 =
                new EventReport.Builder()
                        .setId("101")
                        .setTriggerId("12345")
                        .setSourceEventId(new UnsignedLong(22L))
                        .setEnrollmentId("another-enrollment-id")
                        .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                        .setReportTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(2)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setStatus(EventReport.Status.PENDING)
                        .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
                        .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1) + 1000)
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerPriority(60)
                        .setTriggerValue(30)
                        .setTriggerSummaryBucket(firstBucket)
                        .setTriggerDedupKey(new UnsignedLong(1233L))
                        .build();

        TriggerSpecs templateTriggerSpecs = SourceFixture.getValidTriggerSpecsValueSum(2);

        JSONArray existingAttributes = new JSONArray();
        JSONObject triggerRecord1 = generateTriggerJsonFromEventReport(currentEventReport1);
        JSONObject triggerRecord2 = generateTriggerJsonFromEventReport(currentEventReport2);

        existingAttributes.put(triggerRecord1);
        existingAttributes.put(triggerRecord2);

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportDedupKeys(
                                new ArrayList<>(
                                        Arrays.asList(
                                                new UnsignedLong(3L), new UnsignedLong(1233L))))
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventTime(baseTime)
                        .setEventReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregatableReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setTriggerSpecsString(templateTriggerSpecs.encodeToJson())
                        .setMaxEventLevelReports(templateTriggerSpecs.getMaxReports())
                        .setEventAttributionStatus(existingAttributes.toString())
                        .setPrivacyParameters(
                                templateTriggerSpecs.encodePrivacyParametersToJsonString())
                        .build();

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any()))
                .thenReturn(
                        new ArrayList<>(Arrays.asList(currentEventReport1, currentEventReport2)));
        int numAggregateReportPerDestination = 3;
        int numEventReportPerDestination = 3;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));

        verify(mMeasurementDao, times(1)).getSourceEventReports(any());
        ArgumentCaptor<EventReport> insertedReportsCaptor =
                ArgumentCaptor.forClass(EventReport.class);
        ArgumentCaptor<List<EventReport>> deletedReportsCaptor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(mMeasurementDao, times(2)).insertEventReport(insertedReportsCaptor.capture());
        verify(mMeasurementDao, times(1)).deleteFlexEventReportsAndAttributions(
                deletedReportsCaptor.capture());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
        assertEquals(2, deletedReportsCaptor.getValue().size());
        List<EventReport> insertedEventReports = insertedReportsCaptor.getAllValues();
        assertEquals(firstBucket, insertedEventReports.get(0).getTriggerSummaryBucket());
        assertEquals(secondBucket, insertedEventReports.get(1).getTriggerSummaryBucket());
        assertEquals(new UnsignedLong(1L), insertedEventReports.get(0).getTriggerData());
        assertEquals(new UnsignedLong(1L), insertedEventReports.get(1).getTriggerData());
        long reportTime = TimeUnit.DAYS.toMillis(2) + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS;
        assertEquals(reportTime, insertedEventReports.get(0).getReportTime() - baseTime);
        assertEquals(reportTime, insertedEventReports.get(1).getReportTime() - baseTime);

        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
        verify(mMeasurementDao, times(1)).updateSourceAttributedTriggers(
                eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
        assertEquals(3, source.getTriggerSpecs().getAttributedTriggers().size());
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
    }

    @Test
    public void performAttribution_flexEventReport_notReportDueToDedup()
            throws DatastoreException, JSONException {

        // Setup
        long baseTime = System.currentTimeMillis();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"1\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"value\": \"105\",\n"
                                        + "  \"deduplication_key\": \"111\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(1)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();

        final EventReport currentEventReport1 =
                new EventReport.Builder()
                        .setId("100")
                        .setTriggerId("01234")
                        .setSourceEventId(new UnsignedLong(22L))
                        .setEnrollmentId("another-enrollment-id")
                        .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                        .setReportTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(2)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setStatus(EventReport.Status.PENDING)
                        .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
                        .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1))
                        .setTriggerData(new UnsignedLong(1L))
                        .setTriggerPriority(50)
                        .setTriggerValue(20)
                        .setTriggerDedupKey(new UnsignedLong(111L))
                        .build();
        final EventReport currentEventReport2 =
                new EventReport.Builder()
                        .setId("101")
                        .setTriggerId("12345")
                        .setSourceEventId(new UnsignedLong(22L))
                        .setEnrollmentId("another-enrollment-id")
                        .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                        .setReportTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(2)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setStatus(EventReport.Status.PENDING)
                        .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
                        .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1) + 1000)
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerPriority(60)
                        .setTriggerValue(30)
                        .setTriggerDedupKey(new UnsignedLong(123L))
                        .build();

        TriggerSpecs templateTriggerSpecs = SourceFixture.getValidTriggerSpecsValueSum(2);
        JSONArray existingAttributes = new JSONArray();
        JSONObject triggerRecord1 = generateTriggerJsonFromEventReport(currentEventReport1);
        JSONObject triggerRecord2 = generateTriggerJsonFromEventReport(currentEventReport2);

        existingAttributes.put(triggerRecord1);
        existingAttributes.put(triggerRecord2);

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportDedupKeys(
                                new ArrayList<>(
                                        Arrays.asList(
                                                new UnsignedLong(111L), new UnsignedLong(123L))))
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventTime(baseTime)
                        .setEventReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregatableReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setTriggerSpecsString(templateTriggerSpecs.encodeToJson())
                        .setMaxEventLevelReports(templateTriggerSpecs.getMaxReports())
                        .setEventAttributionStatus(existingAttributes.toString())
                        .setPrivacyParameters(
                                templateTriggerSpecs.encodePrivacyParametersToJsonString())
                        .build();

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any()))
                .thenReturn(
                        new ArrayList<>(Arrays.asList(currentEventReport1, currentEventReport2)));
        int numAggregateReportPerDestination = 1023;
        int numEventReportPerDestination = 1023;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));

        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
        verify(mMeasurementDao, never()).getSourceEventReports(any());

        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao, never()).deleteEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();

        verify(mMeasurementDao, never()).updateSourceAttributedTriggers(anyString(), anyString());
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
    }

    @Test
    public void performAttribution_flexEventReport_dedupKeyInserted()
            throws DatastoreException, JSONException {
        // Setup
        long baseTime = System.currentTimeMillis();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"1\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"value\": \"1\",\n"
                                        + "  \"deduplication_key\": \"111\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(1)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();

        TriggerSpecs templateTriggerSpecs = SourceFixture.getValidTriggerSpecsValueSum(2);
        JSONArray existingAttributes = new JSONArray();

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportDedupKeys(new ArrayList<>())
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventTime(baseTime)
                        .setEventReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregatableReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setTriggerSpecsString(templateTriggerSpecs.encodeToJson())
                        .setMaxEventLevelReports(templateTriggerSpecs.getMaxReports())
                        .setEventAttributionStatus(existingAttributes.toString())
                        .setPrivacyParameters(
                                templateTriggerSpecs.encodePrivacyParametersToJsonString())
                        .build();

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        int numAggregateReportPerDestination = 1023;
        int numEventReportPerDestination = 1023;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));

        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
        verify(mMeasurementDao, times(1)).getSourceEventReports(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao, never()).deleteEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();

        verify(mMeasurementDao, times(1)).updateSourceAttributedTriggers(
                eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
    }

    @Test
    public void performAttribution_flexEventReport_dedupKeyInserted_dedupAlignFlagOff()
            throws DatastoreException, JSONException {
        // Setup
        long baseTime = System.currentTimeMillis();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"1\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"value\": \"1\",\n"
                                        + "  \"deduplication_key\": \"111\"\n"
                                        + "}"
                                        + "]\n")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}]\n")
                        .setTriggerTime(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(1)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregateTriggerData(buildAggregateTriggerData().toString())
                        .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                        .build();

        TriggerSpecs templateTriggerSpecs = SourceFixture.getValidTriggerSpecsValueSum(2);
        JSONArray existingAttributes = new JSONArray();

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportDedupKeys(new ArrayList<>())
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .setFilterDataString(
                                "{\n"
                                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                        + "}\n")
                        .setEventTime(baseTime)
                        .setEventReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setAggregatableReportWindow(
                                baseTime
                                        + TimeUnit.DAYS.toMillis(3)
                                        + MEASUREMENT_MIN_EVENT_REPORT_DELAY_MILLIS)
                        .setTriggerSpecsString(templateTriggerSpecs.encodeToJson())
                        .setMaxEventLevelReports(templateTriggerSpecs.getMaxReports())
                        .setEventAttributionStatus(existingAttributes.toString())
                        .setPrivacyParameters(
                                templateTriggerSpecs.encodePrivacyParametersToJsonString())
                        .build();

        when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        int numAggregateReportPerDestination = 5;
        int numEventReportPerDestination = 4;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numAggregateReportPerDestination);
        when(mMeasurementDao.getNumEventReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(numEventReportPerDestination);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
        when(mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()).thenReturn(false);

        // Execution
        mHandler.performPendingAttributions();

        // Assertion
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));

        verify(mMeasurementDao).insertAggregateReport(any());
        verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
        verify(mMeasurementDao, times(1)).getSourceEventReports(any());
        verify(mMeasurementDao, times(1)).updateSourceEventReportDedupKeys(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao, never()).deleteEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();

        verify(mMeasurementDao, times(1)).updateSourceAttributedTriggers(
                eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
        verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
    }

    @Test
    public void performAttributions_withXnaConfig_derivedSourceWinsAndIsLogged()
            throws DatastoreException {
        // Setup
        String adtechEnrollment = "AdTech1-Ads";
        AttributionConfig attributionConfig =
                new AttributionConfig.Builder()
                        .setExpiry(604800L)
                        .setSourceAdtech(adtechEnrollment)
                        .setSourcePriorityRange(new Pair<>(1L, 1000L))
                        .setSourceFilters(null)
                        .setPriority(50L)
                        .setExpiry(604800L)
                        .setFilterData(null)
                        .build();
        Trigger trigger =
                getXnaTriggerBuilder()
                        .setFilters(null)
                        .setNotFilters(null)
                        .setAttributionConfig(
                                new JSONArray(
                                                Collections.singletonList(
                                                        attributionConfig.serializeAsJson(mFlags)))
                                        .toString())
                        .build();

        String aggregatableSource = SourceFixture.ValidSourceParams.buildAggregateSource();
        // Its derived source will be winner due to install attribution and higher priority
        Source xnaSource =
                createXnaSourceBuilder()
                        .setEnrollmentId(adtechEnrollment)
                        // Priority changes to 50 for derived source
                        .setPriority(1L)
                        .setAggregateSource(aggregatableSource)
                        .setFilterDataString(null)
                        .setSharedAggregationKeys(
                                new JSONArray(Arrays.asList("campaignCounts", "geoValue"))
                                        .toString())
                        .build();
        Source triggerEnrollmentSource1 =
                createXnaSourceBuilder()
                        .setEnrollmentId(trigger.getEnrollmentId())
                        .setPriority(2L)
                        .setFilterDataString(null)
                        .setAggregateSource(aggregatableSource)
                        .build();

        Source triggerEnrollmentSource2 =
                createXnaSourceBuilder()
                        .setEnrollmentId(trigger.getEnrollmentId())
                        .setPriority(2L)
                        .setFilterDataString(null)
                        .setAggregateSource(aggregatableSource)
                        .setInstallAttributed(false)
                        .build();

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(xnaSource);
        matchingSourceList.add(triggerEnrollmentSource1);
        matchingSourceList.add(triggerEnrollmentSource2);
        when(mMeasurementDao.fetchTriggerMatchingSourcesForXna(any(), any()))
                .thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        when(mFlags.getMeasurementEnableXNA()).thenReturn(true);

        // Execution
        AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

        // Assertion
        assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));

        MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
        assertTrue(measurementAttributionStats.isSourceDerived());
    }

    @Test
    public void performAttributions_invalidTriggerId_triggerNotFoundLogged()
            throws DatastoreException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setTriggerTime(TRIGGER_TIME)
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setNotFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11_x\", \"value_12\"]}, {\n"
                                        + "  \"key_2\": [\"value_21\", \"value_22_x\"]\n"
                                        + "}]\n")
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenThrow(DatastoreException.class);

        // Execution
        AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

        // Assertion
        assertEquals(AttributionJobHandler.ProcessingResult.FAILURE, result);
        MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
        assertEquals(
                measurementAttributionStats.getSourceType(),
                AttributionStatus.SourceType.UNKNOWN.getValue());
        assertEquals(
                measurementAttributionStats.getSurfaceType(),
                AttributionStatus.AttributionSurface.UNKNOWN.getValue());
        assertEquals(
                measurementAttributionStats.getResult(),
                AttributionStatus.AttributionResult.UNKNOWN.getValue());
        assertEquals(
                measurementAttributionStats.getFailureType(),
                AttributionStatus.FailureType.TRIGGER_NOT_FOUND.getValue());
    }

    @Test
    public void performAttributions_topLevelFiltersDontMatch_topLevelFilterMatchFailureLogged()
            throws DatastoreException {
        Trigger trigger = TriggerFixture.getValidTrigger();
        List<Source> matchingSourceList = new ArrayList<>();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "[{\n"
                                        + "  \"key_1\": [\"no_match\"],\n"
                                        + "  \"key_2\": [\"no_match\"]\n"
                                        + "}]\n")
                        .build();
        matchingSourceList.add(source);

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);

        // Execution
        AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

        // Assertion
        assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
        assertEquals(
                AttributionStatus.SourceType.VIEW.getValue(),
                measurementAttributionStats.getSourceType());
        assertEquals(
                AttributionStatus.AttributionSurface.APP_APP.getValue(),
                measurementAttributionStats.getSurfaceType());
        assertEquals(
                AttributionStatus.AttributionResult.NOT_ATTRIBUTED.getValue(),
                measurementAttributionStats.getResult());
        assertEquals(
                AttributionStatus.FailureType.TOP_LEVEL_FILTER_MATCH_FAILURE.getValue(),
                measurementAttributionStats.getFailureType());
    }

    @Test
    public void performAttributions_topLevelFiltersDontMatch_sendNullAggregateReport()
            throws DatastoreException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setAggregateTriggerData(
                                "["
                                        + "{"
                                        + "\"key_piece\":\"0xA80\","
                                        + "\"source_keys\":[\"geoValue\",\"noMatch\"]"
                                        + "}"
                                        + "]")
                        .setAggregateValues(
                                "{" + "\"campaignCounts\":32768," + "\"geoValue\":1664" + "}")
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"match\"],\n"
                                        + "  \"key_2\": [\"match\"]\n"
                                        + "}]\n")
                        .setAggregatableSourceRegistrationTimeConfig(
                                Trigger.SourceRegistrationTimeConfig.EXCLUDE)
                        .build();
        List<Source> matchingSourceList = new ArrayList<>();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "[{\n"
                                        + "  \"key_1\": [\"no_match\"],\n"
                                        + "  \"key_2\": [\"no_match\"]\n"
                                        + "}]\n")
                        .build();
        matchingSourceList.add(source);

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
        when(mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime()).thenReturn(1.0f);

        // Execution
        AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

        // Assertion
        assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
        MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
        assertEquals(
                AttributionStatus.SourceType.VIEW.getValue(),
                measurementAttributionStats.getSourceType());
        assertEquals(
                AttributionStatus.AttributionSurface.APP_APP.getValue(),
                measurementAttributionStats.getSurfaceType());
        assertEquals(
                AttributionStatus.AttributionResult.NOT_ATTRIBUTED.getValue(),
                measurementAttributionStats.getResult());
        assertEquals(
                AttributionStatus.FailureType.TOP_LEVEL_FILTER_MATCH_FAILURE.getValue(),
                measurementAttributionStats.getFailureType());

        captureAndAssertNullAggregateReport(trigger);
    }

    @Test
    public void
            performAttributions_triggerTimePastAggregatableReportWindow_sendNullAggregateReport()
                    throws DatastoreException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setAggregateTriggerData(
                                "["
                                        + "{"
                                        + "\"key_piece\":\"0xA80\","
                                        + "\"source_keys\":[\"geoValue\",\"noMatch\"]"
                                        + "}"
                                        + "]")
                        .setAggregateValues(
                                "{" + "\"campaignCounts\":32768," + "\"geoValue\":1664" + "}")
                        .setAggregatableSourceRegistrationTimeConfig(
                                Trigger.SourceRegistrationTimeConfig.EXCLUDE)
                        .setTriggerTime(1L)
                        .build();
        List<Source> matchingSourceList = new ArrayList<>();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregatableReportWindow(0L)
                        .build();
        matchingSourceList.add(source);

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
        when(mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime()).thenReturn(1.0f);

        // Execution
        AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

        // Assertion
        assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);

        MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
        assertEquals(
                AttributionStatus.AttributionResult.NOT_ATTRIBUTED.getValue(),
                measurementAttributionStats.getResult());

        captureAndAssertNullAggregateReport(trigger);
    }

    @Test
    public void performAttributions_numReportsPerDestinationExceedsMax_sendNullAggregateReport()
            throws DatastoreException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setAggregateTriggerData(
                                "["
                                        + "{"
                                        + "\"key_piece\":\"0xA80\","
                                        + "\"source_keys\":[\"geoValue\",\"noMatch\"]"
                                        + "}"
                                        + "]")
                        .setAggregateValues(
                                "{" + "\"campaignCounts\":32768," + "\"geoValue\":1664" + "}")
                        .setAggregatableSourceRegistrationTimeConfig(
                                Trigger.SourceRegistrationTimeConfig.EXCLUDE)
                        .setTriggerTime(1L)
                        .build();
        List<Source> matchingSourceList = new ArrayList<>();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregatableReportWindow(2L) // ensure trigger time is before window
                        .build();
        matchingSourceList.add(source);

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        int excessiveReportCount = MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION + 1;
        when(mMeasurementDao.getNumAggregateReportsPerDestination(
                        trigger.getAttributionDestination(), trigger.getDestinationType()))
                .thenReturn(excessiveReportCount);
        when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
        when(mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime()).thenReturn(1.0f);

        // Execution
        AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

        // Assertion
        assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);

        MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
        assertEquals(
                AttributionStatus.AttributionResult.NOT_ATTRIBUTED.getValue(),
                measurementAttributionStats.getResult());

        captureAndAssertNullAggregateReport(trigger);
    }

    @Test
    public void performAttributions_triggerAggregateDeduplicated_sendNullAggregateReport()
            throws DatastoreException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setAggregateTriggerData(
                                "["
                                        + "{"
                                        + "\"key_piece\":\"0xA80\","
                                        + "\"source_keys\":[\"geoValue\",\"noMatch\"]"
                                        + "}"
                                        + "]")
                        .setAggregateValues("{\"campaignCounts\":32768," + "\"geoValue\":1664}")
                        .setAggregateDeduplicationKeys(
                                "[{\"deduplication_key\": \"0\","
                                        + "\"filters\": [{\"x\": [\"y\"]}]}]")
                        .setAggregatableSourceRegistrationTimeConfig(
                                Trigger.SourceRegistrationTimeConfig.EXCLUDE)
                        .setTriggerTime(1L)
                        .build();
        List<Source> matchingSourceList = new ArrayList<>();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\": \"0x159\", \"geoValue\": \"0x5\"}")
                        .setFilterDataString("{\"x\": [\"y\", \"z\"]}")
                        // Ensure key matches that of trigger's aggregate deduplication key
                        .setAggregateReportDedupKeys(List.of(new UnsignedLong(0L)))
                        .setAggregatableReportWindow(2L) // ensure trigger time is before window
                        .build();
        matchingSourceList.add(source);

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
        when(mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime()).thenReturn(1.0f);

        // Execution
        AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

        // Assertion
        assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);

        MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
        assertEquals(
                AttributionStatus.AttributionResult.NOT_ATTRIBUTED.getValue(),
                measurementAttributionStats.getResult());

        captureAndAssertNullAggregateReport(trigger);
    }

    @Test
    public void performAttributions_triggerAggregateNoContributions_sendNullAggregateReport()
            throws DatastoreException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setAggregateTriggerData(
                                "["
                                        + "{"
                                        + "\"key_piece\":\"0xA80\","
                                        + "\"source_keys\":[\"geoValue\"]"
                                        + "}"
                                        + "]")
                        .setAggregateValues("{\"campaignCounts\":32768," + "\"geoValue\":1664}")
                        .setAggregatableSourceRegistrationTimeConfig(
                                Trigger.SourceRegistrationTimeConfig.EXCLUDE)
                        .setTriggerTime(1L)
                        .build();
        List<Source> matchingSourceList = new ArrayList<>();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource("{\"noMatch\": \"0x159\", \"stillNoMatch\": \"0x5\"}")
                        .setAggregatableReportWindow(2L) // ensure trigger time is before window
                        .build();
        matchingSourceList.add(source);

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
        when(mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime()).thenReturn(1.0f);

        // Execution
        AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

        // Assertion
        assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);

        MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
        assertEquals(
                AttributionStatus.AttributionResult.NOT_ATTRIBUTED.getValue(),
                measurementAttributionStats.getResult());

        captureAndAssertNullAggregateReport(trigger);
    }

    @Test
    public void performAttributions_triggerAggregateInsufficientBudget_sendNullAggregateReport()
            throws DatastoreException {
        int excessiveAggregateValue = PrivacyParams.MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE + 1;
        String aggregateValuesStr =
                String.format(
                        Locale.ENGLISH,
                        "{\"campaignCounts\":32768," + "\"geoValue\":%d}",
                        excessiveAggregateValue);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"5\",\n"
                                        + "  \"priority\": \"123\",\n"
                                        + "  \"deduplication_key\": \"1\"\n"
                                        + "}"
                                        + "]\n")
                        .setAggregateTriggerData(
                                "["
                                        + "{"
                                        + "\"key_piece\":\"0xA80\","
                                        + "\"source_keys\":[\"geoValue\"]"
                                        + "}"
                                        + "]")
                        .setAggregateValues(aggregateValuesStr)
                        .setAggregatableSourceRegistrationTimeConfig(
                                Trigger.SourceRegistrationTimeConfig.EXCLUDE)
                        .setTriggerTime(1L)
                        .build();
        List<Source> matchingSourceList = new ArrayList<>();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setAggregateSource(
                                "{\"campaignCounts\": \"0x159\", \"geoValue\": \"0x5\"}")
                        .setAggregatableReportWindow(2L) // ensure trigger time is before window
                        .build();
        matchingSourceList.add(source);

        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
        when(mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime()).thenReturn(1.0f);

        // Execution
        AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

        // Assertion
        assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);

        MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
        assertEquals(
                AttributionStatus.AttributionResult.NOT_ATTRIBUTED.getValue(),
                measurementAttributionStats.getResult());

        captureAndAssertNullAggregateReport(trigger);
    }

    @Test
    public void performAttributions_withinLookbackWindow_attributeTrigger()
            throws DatastoreException {
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"2\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"source_type\": [\"event\"], \n"
                                        + "    \"dummy_key\": [\"dummy_value\"] \n"
                                        + "   }]\n"
                                        + "},"
                                        + "{\n"
                                        + "  \"trigger_data\": \"3\",\n"
                                        + "  \"priority\": \"3\",\n"
                                        + "  \"deduplication_key\": \"3\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"source_type\": [\"navigation\"], \n"
                                        + "    \"dummy_key\": [\"dummy_value\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .setTriggerTime(TRIGGER_TIME)
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12\"],\n"
                                        // Set Lookback window to be greater than duration from
                                        // source to trigger time.
                                        + "  \"_lookback_window\": 1000\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "{\n" + "  \"key_1\": [\"value_11\", \"value_12\"]\n" + "}\n")
                        .setId("sourceId")
                        .setEventTime(TRIGGER_TIME - TimeUnit.SECONDS.toMillis(LOOKBACK_WINDOW - 1))
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setExpiryTime(TRIGGER_TIME + TimeUnit.DAYS.toMillis(28))
                        .setEventReportWindow(TRIGGER_TIME + 1)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1)
                        .build();
        EventReport expectedEventReport =
                new EventReport.Builder()
                        .setTriggerPriority(3L)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .setTriggerData(new UnsignedLong(3L))
                        .setTriggerTime(trigger.getTriggerTime())
                        .setSourceEventId(source.getEventId())
                        .setStatus(EventReport.Status.PENDING)
                        .setAttributionDestinations(source.getAppDestinations())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setReportTime(
                                mEventReportWindowCalcDelegate.getReportingTime(
                                        source,
                                        trigger.getTriggerTime(),
                                        trigger.getDestinationType()))
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRandomizedTriggerRate(
                                mSourceNoiseHandler.getRandomizedTriggerRate(source))
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(REGISTRATION_URI)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())),
                        eq(Trigger.Status.ATTRIBUTED));
        String expectedAttributionStatus =
                getAttributionStatus(List.of(trigger.getId()), List.of("3"), List.of("3"));
        verify(mMeasurementDao)
                .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
        verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
    }

    @Test
    public void performAttributions_outsideLookbackWindow_noAttributionTrigger()
            throws DatastoreException {
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        // Setup
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"2\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"source_type\": [\"event\"], \n"
                                        + "    \"dummy_key\": [\"dummy_value\"] \n"
                                        + "   }]\n"
                                        + "},"
                                        + "{\n"
                                        + "  \"trigger_data\": \"3\",\n"
                                        + "  \"priority\": \"3\",\n"
                                        + "  \"deduplication_key\": \"3\",\n"
                                        + "  \"filters\": [{\n"
                                        + "    \"source_type\": [\"navigation\"], \n"
                                        + "    \"dummy_key\": [\"dummy_value\"] \n"
                                        + "   }]\n"
                                        + "}"
                                        + "]\n")
                        .setTriggerTime(TRIGGER_TIME)
                        .setFilters(
                                "[{\n"
                                        + "  \"key_1\": [\"value_11\", \"value_12\"],\n"
                                        // Set Lookback window to be smaller than duration from
                                        // source to trigger time.
                                        + "  \"_lookback_window\": 1000\n"
                                        + "}]\n")
                        .build();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setFilterDataString(
                                "{\n" + "  \"key_1\": [\"value_11\", \"value_12\"]\n" + "}\n")
                        .setId("sourceId")
                        .setEventTime(TRIGGER_TIME - TimeUnit.SECONDS.toMillis(LOOKBACK_WINDOW + 1))
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(TRIGGER_TIME + 1)
                        .setAggregatableReportWindow(TRIGGER_TIME + 1)
                        .build();
        EventReport expectedEventReport =
                new EventReport.Builder()
                        .setTriggerPriority(2L)
                        .setTriggerDedupKey(new UnsignedLong(2L))
                        .setTriggerData(new UnsignedLong(2L))
                        .setTriggerTime(trigger.getTriggerTime())
                        .setSourceEventId(source.getEventId())
                        .setStatus(EventReport.Status.PENDING)
                        .setAttributionDestinations(source.getAppDestinations())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setReportTime(
                                mEventReportWindowCalcDelegate.getReportingTime(
                                        source,
                                        trigger.getTriggerTime(),
                                        trigger.getDestinationType()))
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setRandomizedTriggerRate(
                                mSourceNoiseHandler.getRandomizedTriggerRate(source))
                        .setSourceId(source.getId())
                        .setTriggerId(trigger.getId())
                        .setRegistrationOrigin(REGISTRATION_URI)
                        .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceDestinations(source.getId()))
                .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(
                anyInt(), any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

        // Execution
        mHandler.performPendingAttributions();

        // Assertions
        verify(mMeasurementDao)
                .updateTriggerStatus(
                        eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
        verify(mMeasurementDao, never()).updateSourceAttributedTriggers(any(), any());
        verify(mMeasurementDao, never()).insertEventReport(eq(expectedEventReport));
    }

    public static Trigger.Builder getXnaTriggerBuilder() {
        return new Trigger.Builder()
                .setId(UUID.randomUUID().toString())
                .setAttributionDestination(
                        TriggerFixture.ValidTriggerParams.ATTRIBUTION_DESTINATION)
                .setEnrollmentId(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID)
                .setRegistrant(TriggerFixture.ValidTriggerParams.REGISTRANT)
                .setTriggerTime(TriggerFixture.ValidTriggerParams.TRIGGER_TIME)
                .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                .setAggregateTriggerData(TriggerFixture.ValidTriggerParams.AGGREGATE_TRIGGER_DATA)
                .setAggregateValues(TriggerFixture.ValidTriggerParams.AGGREGATE_VALUES)
                .setFilters(TriggerFixture.ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING)
                .setNotFilters(TriggerFixture.ValidTriggerParams.TOP_LEVEL_NOT_FILTERS_JSON_STRING)
                .setAttributionConfig(TriggerFixture.ValidTriggerParams.ATTRIBUTION_CONFIGS_STRING)
                .setAdtechBitMapping(TriggerFixture.ValidTriggerParams.X_NETWORK_KEY_MAPPING)
                .setRegistrationOrigin(TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN)
                .setAggregatableSourceRegistrationTimeConfig(
                        TriggerFixture.ValidTriggerParams
                                .AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG);
    }

    private void captureAndAssertNullAggregateReport(Trigger trigger) throws DatastoreException {
        ArgumentCaptor<AggregateReport> aggregateReportCaptor =
                ArgumentCaptor.forClass(AggregateReport.class);
        verify(mMeasurementDao, times(1)).insertAggregateReport(aggregateReportCaptor.capture());
        AggregateReport report = aggregateReportCaptor.getValue();
        assertNullAggregateReport(report, trigger, null);
    }

    private MeasurementAttributionStats getMeasurementAttributionStats() {
        ArgumentCaptor<MeasurementAttributionStats> statsArg =
                ArgumentCaptor.forClass(MeasurementAttributionStats.class);
        verify(mLogger).logMeasurementAttributionStats(statsArg.capture());
        MeasurementAttributionStats measurementAttributionStats = statsArg.getValue();
        return measurementAttributionStats;
    }

    private Source.Builder createXnaSourceBuilder() {
        return new Source.Builder()
                .setId(UUID.randomUUID().toString())
                .setEventId(SourceFixture.ValidSourceParams.SOURCE_EVENT_ID)
                .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
                .setAppDestinations(SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                .setEnrollmentId(SourceFixture.ValidSourceParams.ENROLLMENT_ID)
                .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
                .setEventTime(SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME)
                .setExpiryTime(SourceFixture.ValidSourceParams.EXPIRY_TIME)
                .setPriority(SourceFixture.ValidSourceParams.PRIORITY)
                .setSourceType(SourceFixture.ValidSourceParams.SOURCE_TYPE)
                .setInstallAttributionWindow(
                        SourceFixture.ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW)
                .setInstallCooldownWindow(SourceFixture.ValidSourceParams.INSTALL_COOLDOWN_WINDOW)
                .setAttributionMode(SourceFixture.ValidSourceParams.ATTRIBUTION_MODE)
                .setAggregateSource(SourceFixture.ValidSourceParams.buildAggregateSource())
                .setFilterDataString(buildMatchingFilterData())
                .setIsDebugReporting(true)
                .setRegistrationId(SourceFixture.ValidSourceParams.REGISTRATION_ID)
                .setSharedAggregationKeys(SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS)
                .setInstallTime(SourceFixture.ValidSourceParams.INSTALL_TIME)
                .setAggregatableReportWindow(SourceFixture.ValidSourceParams.EXPIRY_TIME)
                .setRegistrationOrigin(SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN)
                .setInstallAttributed(true);
    }

    private String buildMatchingFilterData() {
        try {
            JSONObject filterMap = new JSONObject();
            filterMap.put(
                    "conversion_subdomain",
                    new JSONArray(Collections.singletonList("electronics.megastore")));
            return filterMap.toString();
        } catch (JSONException e) {
            LogUtil.e("JSONException when building aggregate filter data.");
        }
        return null;
    }

    private JSONArray buildAggregateTriggerData() throws JSONException {
        JSONArray triggerData = new JSONArray();
        JSONObject jsonObject1 = new JSONObject();
        jsonObject1.put("key_piece", "0x400");
        jsonObject1.put("source_keys", new JSONArray(Arrays.asList("campaignCounts")));
        jsonObject1.put("filters", createFilterJSONArray());
        jsonObject1.put("not_filters", createFilterJSONArray());
        JSONObject jsonObject2 = new JSONObject();
        jsonObject2.put("key_piece", "0xA80");
        jsonObject2.put("source_keys", new JSONArray(Arrays.asList("geoValue", "noMatch")));
        triggerData.put(jsonObject1);
        triggerData.put(jsonObject2);
        return triggerData;
    }

    private JSONArray createFilterJSONArray() throws JSONException {
        JSONObject filterMap = new JSONObject();
        filterMap.put("conversion_subdomain",
                new JSONArray(Arrays.asList("electronics.megastore")));
        filterMap.put("product", new JSONArray(Arrays.asList("1234", "2345")));
        JSONArray filterSet = new JSONArray();
        filterSet.put(filterMap);
        return filterSet;
    }

    private void assertNullAggregateReport(
            AggregateReport report, Trigger trigger, Long expectedSourceRegistrationTime) {
        assertEquals(trigger.getRegistrationOrigin(), report.getRegistrationOrigin());
        assertEquals(trigger.getAttributionDestination(), report.getAttributionDestination());

        long lowerBound = trigger.getTriggerTime() + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY;
        // Add slightly more delay to upper bound to account for execution.
        long upperBound =
                trigger.getTriggerTime()
                        + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY
                        + PrivacyParams.AGGREGATE_REPORT_DELAY_SPAN
                        + 1000L;

        assertTrue(
                report.getScheduledReportTime() > lowerBound
                        && report.getScheduledReportTime() < upperBound);

        assertNull(report.getSourceDebugKey());
        assertEquals(trigger.getDebugKey(), report.getTriggerDebugKey());
        if (trigger.getAggregationCoordinatorOrigin() == null) {
            assertEquals(
                    Uri.parse(AdServicesConfig.getMeasurementDefaultAggregationCoordinatorOrigin()),
                    report.getAggregationCoordinatorOrigin());
        } else {
            assertEquals(
                    trigger.getAggregationCoordinatorOrigin(),
                    report.getAggregationCoordinatorOrigin());
        }
        assertTrue(report.isFakeReport());
        assertEquals(trigger.getId(), report.getTriggerId());
        assertEquals(expectedSourceRegistrationTime, report.getSourceRegistrationTime());
    }

    private void assertAggregateReportsEqual(
            AggregateReport expectedReport, AggregateReport actualReport) {
        // Avoids checking report time because there is randomization
        assertEquals(expectedReport.getApiVersion(), actualReport.getApiVersion());
        assertEquals(
                expectedReport.getAttributionDestination(),
                actualReport.getAttributionDestination());
        assertEquals(
                expectedReport.getDebugCleartextPayload(), actualReport.getDebugCleartextPayload());
        assertEquals(expectedReport.getEnrollmentId(), actualReport.getEnrollmentId());
        assertEquals(expectedReport.getPublisher(), actualReport.getPublisher());
        assertEquals(expectedReport.getSourceId(), actualReport.getSourceId());
        assertEquals(expectedReport.getTriggerId(), actualReport.getTriggerId());
        assertEquals(
                expectedReport.getAggregateAttributionData(),
                actualReport.getAggregateAttributionData());
        assertEquals(expectedReport.getSourceDebugKey(), actualReport.getSourceDebugKey());
        assertEquals(expectedReport.getTriggerDebugKey(), actualReport.getTriggerDebugKey());
        assertEquals(expectedReport.getRegistrationOrigin(), actualReport.getRegistrationOrigin());
        assertEquals(
                expectedReport.getSourceRegistrationTime(),
                actualReport.getSourceRegistrationTime());
        assertEquals(expectedReport.isFakeReport(), actualReport.isFakeReport());
    }

    private static AggregateReport getExpectedAggregateReport(Trigger trigger, Source source) {
        return new AggregateReport.Builder()
                .setApiVersion("0.1")
                .setAttributionDestination(trigger.getAttributionDestination())
                .setDebugCleartextPayload(
                        "{\"operation\":\"histogram\","
                                + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                                + "{\"bucket\":\"2693\",\"value\":1644}]}")
                .setEnrollmentId(source.getEnrollmentId())
                .setPublisher(source.getRegistrant())
                .setSourceId(source.getId())
                .setTriggerId(trigger.getId())
                .setRegistrationOrigin(REGISTRATION_URI)
                .setSourceRegistrationTime(roundDownToDay(source.getEventTime()))
                .setAggregateAttributionData(
                        new AggregateAttributionData.Builder()
                                .setContributions(
                                        Arrays.asList(
                                                new AggregateHistogramContribution.Builder()
                                                        .setKey(new BigInteger("1369"))
                                                        .setValue(32768)
                                                        .build(),
                                                new AggregateHistogramContribution.Builder()
                                                        .setKey(new BigInteger("2693"))
                                                        .setValue(1644)
                                                        .build()))
                                .build())
                .setIsFakeReport(false)
                .build();
    }

    private static JSONArray getAggregateTriggerData() throws JSONException {
        JSONArray triggerData = new JSONArray();
        JSONObject jsonObject1 = new JSONObject();
        jsonObject1.put("key_piece", "0x400");
        jsonObject1.put("source_keys", new JSONArray(Arrays.asList("campaignCounts")));
        JSONObject jsonObject2 = new JSONObject();
        jsonObject2.put("key_piece", "0xA80");
        jsonObject2.put("source_keys", new JSONArray(Arrays.asList("geoValue", "noMatch")));
        triggerData.put(jsonObject1);
        triggerData.put(jsonObject2);
        return triggerData;
    }

    private static Trigger.Builder getTriggerBuilder() throws JSONException {
        return TriggerFixture.getValidTriggerBuilder()
                .setId("triggerId1")
                .setTriggerTime(TRIGGER_TIME)
                .setStatus(Trigger.Status.PENDING)
                .setEventTriggers(new JSONArray().toString());
    }

    private static Trigger.Builder getAggregateTriggerBuilder() throws JSONException {
        JSONArray triggerData = getAggregateTriggerData();

        return getTriggerBuilder()
                .setAggregateTriggerData(triggerData.toString())
                .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}");
    }

    private static Trigger getAggregateTrigger() throws JSONException {
        return getAggregateTriggerBuilder().build();
    }

    private static Trigger getAggregateAndEventTrigger() throws JSONException {
        return getAggregateTriggerBuilder()
                .setEventTriggers(EVENT_TRIGGERS)
                .build();
    }

    private static Source getAggregateSource() {
        return SourceFixture.getMinimalValidSourceBuilder()
                .setId("sourceId1")
                .setEventTime(SOURCE_TIME)
                .setExpiryTime(EXPIRY_TIME)
                .setAggregatableReportWindow(TRIGGER_TIME + 1L)
                .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                .setFilterDataString("{\"product\":[\"1234\",\"2345\"]}")
                .build();
    }

    private static JSONObject generateTriggerJsonFromEventReport(EventReport eventReport)
            throws JSONException {
        JSONObject triggerRecord = new JSONObject();
        triggerRecord.put("trigger_id", eventReport.getId());
        triggerRecord.put("value", eventReport.getTriggerValue());
        triggerRecord.put("priority", eventReport.getTriggerPriority());
        triggerRecord.put("trigger_time", eventReport.getTriggerTime());
        triggerRecord.put("trigger_data", eventReport.getTriggerData());
        triggerRecord.put("dedup_key", eventReport.getTriggerDedupKey());
        if (eventReport.getTriggerDebugKey() != null) {
            triggerRecord.put("debug_key", eventReport.getTriggerDebugKey());
        }
        triggerRecord.put("has_source_debug_key", eventReport.getSourceDebugKey() != null);
        return triggerRecord;
    }

    private static String getAttributionStatus(List<String> triggerIds, List<String> triggerData,
            List<String> dedupKeys) {
        try {
            JSONArray attributionStatus = new JSONArray();
            for (int i = 0; i < triggerIds.size(); i++) {
                attributionStatus.put(
                        new JSONObject()
                                .put("trigger_id", triggerIds.get(i))
                                .put("trigger_data", triggerData.get(i))
                                .put("dedup_key", dedupKeys.get(i)));
            }
            return attributionStatus.toString();
        } catch (JSONException ignored) {
            return null;
        }
    }
}
