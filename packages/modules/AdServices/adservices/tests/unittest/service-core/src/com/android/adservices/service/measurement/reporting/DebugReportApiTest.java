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
package com.android.adservices.service.measurement.reporting;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerFixture;
import com.android.adservices.service.measurement.reporting.DebugReportApi.Type;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.quality.Strictness;

import java.util.Arrays;

/** Unit tests for {@link DebugReport} */
@RunWith(MockitoJUnitRunner.class)
@SmallTest
public final class DebugReportApiTest {

    private static final UnsignedLong SOURCE_EVENT_ID = new UnsignedLong("7213872");
    private static final UnsignedLong TRIGGER_DATA = new UnsignedLong(1L);
    private static final String LIMIT = "100";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private DebugReportApi mDebugReportApi;
    @Mock private IMeasurementDao mMeasurementDao;
    @Mock private Flags mFlags;

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(VerboseDebugReportingJobService.class)
                    .spyStatic(FlagsFactory.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.schedule(any(), any()));
        ExtendedMockito.doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);
        mDebugReportApi = spy(new DebugReportApi(mContext, mFlags));

        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist())
                .thenReturn(SourceFixture.ValidSourceParams.ENROLLMENT_ID);
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(true);
        when(mFlags.getMeasurementEnableSourceDebugReport()).thenReturn(true);
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(true);
        when(mFlags.getMeasurementVtcConfigurableMaxEventReportsCount())
                .thenReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT);
        when(mFlags.getMeasurementEventReportsVtcEarlyReportingWindows())
                .thenReturn(Flags.MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS);
        when(mFlags.getMeasurementEventReportsCtcEarlyReportingWindows())
                .thenReturn(Flags.MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS);
    }

    @Test
    public void testScheduleAppToAppSourceSuccessDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertSourceDebugReportParameters(
                report,
                DebugReportApi.Type.SOURCE_SUCCESS,
                SourceFixture.ValidSourceParams.PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS.get(0).toString(),
                null);
    }

    @Test
    public void testScheduleWebToWebSourceSuccessDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertSourceDebugReportParameters(
                report,
                DebugReportApi.Type.SOURCE_SUCCESS,
                SourceFixture.ValidSourceParams.WEB_PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0).toString(),
                null);
    }

    @Test
    public void testScheduleAppToWebSourceSuccessDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport actualReport = captor.getValue();

        assertSourceDebugReportParameters(
                actualReport,
                DebugReportApi.Type.SOURCE_SUCCESS,
                SourceFixture.ValidSourceParams.PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0).toString(),
                null);
    }

    @Test
    public void testScheduleWebToAppSourceSuccessDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setWebDestinations(null)
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport actualReport = captor.getValue();

        assertSourceDebugReportParameters(
                actualReport,
                DebugReportApi.Type.SOURCE_SUCCESS,
                SourceFixture.ValidSourceParams.WEB_PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS.get(0).toString(),
                null);
    }

    @Test
    public void testScheduleAppToAppAndWebSourceSuccessDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport actualReport = captor.getValue();

        assertSourceDebugReportParameters(
                actualReport,
                DebugReportApi.Type.SOURCE_SUCCESS,
                SourceFixture.ValidSourceParams.PUBLISHER.toString(),
                new JSONArray(
                                Arrays.asList(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS
                                                .get(0)
                                                .toString(),
                                        SourceFixture.ValidSourceParams.WEB_DESTINATIONS
                                                .get(0)
                                                .toString()))
                        .toString(),
                null);
    }

    @Test
    public void testScheduleWebToAppAndWebSourceSuccessDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport actualReport = captor.getValue();

        assertSourceDebugReportParameters(
                actualReport,
                DebugReportApi.Type.SOURCE_SUCCESS,
                SourceFixture.ValidSourceParams.WEB_PUBLISHER.toString(),
                new JSONArray(
                                Arrays.asList(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS
                                                .get(0)
                                                .toString(),
                                        SourceFixture.ValidSourceParams.WEB_DESTINATIONS
                                                .get(0)
                                                .toString()))
                        .toString(),
                null);
    }

    @Test
    public void testScheduleSourceSuccessDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceSuccessDebugReport_sourceFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableSourceDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceSuccessDebugReport_without_enrollmentId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .setEnrollmentId("")
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceSuccessDebugReport_adTechNotOptIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleAppSourceSuccessDebugReport_without_adidPermission_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(false)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleWebSourceSuccessDebugReport_without_arDebugPermission_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceSuccessDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleSourceDestinationLimitDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setWebDestinations(null)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationLimitDebugReport(source, LIMIT, mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertSourceDebugReportParameters(
                report,
                DebugReportApi.Type.SOURCE_DESTINATION_LIMIT,
                SourceFixture.ValidSourceParams.PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS.get(0).toString(),
                LIMIT);
    }

    @Test
    public void scheduleSourceDestinationRateLimitDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setWebDestinations(null)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationRateLimitDebugReport(
                source, LIMIT, mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertSourceDebugReportParameters(
                report,
                DebugReportApi.Type.SOURCE_DESTINATION_RATE_LIMIT,
                SourceFixture.ValidSourceParams.PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS.get(0).toString(),
                LIMIT);
    }

    @Test
    public void scheduleSourceDestinationLimitDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source = SourceFixture.getValidSource();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleSourceDestinationRateLimitDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source = SourceFixture.getValidSource();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationRateLimitDebugReport(
                source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleSourceDestinationLimitDebugReport_sourceFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableSourceDebugReport()).thenReturn(false);
        Source source = SourceFixture.getValidSource();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleSourceDestinationRateLimitDebugReport_sourceFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableSourceDebugReport()).thenReturn(false);
        Source source = SourceFixture.getValidSource();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationRateLimitDebugReport(
                source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleSourceDestinationLimitDebugReport_without_enrollmentId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setEnrollmentId("")
                        .build();

        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleSourceDestinationRateLimitDebugReport_without_enrollmentId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setEnrollmentId("")
                        .build();

        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationRateLimitDebugReport(
                source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleSourceDestinationLimitDebugReport_adTechNotOptIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(false)
                        .build();

        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleSourceDestinationRateLimitDebugReport_adTechNotOptIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(false)
                        .build();

        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceDestinationRateLimitDebugReport(
                source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceNoisedDebugReport(source, mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());

        DebugReport report = captor.getValue();
        assertSourceDebugReportParameters(
                report,
                DebugReportApi.Type.SOURCE_NOISED,
                SourceFixture.ValidSourceParams.PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS.get(0).toString(),
                null);
    }

    @Test
    public void testScheduleWebSourceNoisedDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceNoisedDebugReport(source, mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertSourceDebugReportParameters(
                report,
                DebugReportApi.Type.SOURCE_NOISED,
                SourceFixture.ValidSourceParams.WEB_PUBLISHER.toString(),
                new JSONArray(
                                Arrays.asList(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS
                                                .get(0)
                                                .toString(),
                                        SourceFixture.ValidSourceParams.WEB_DESTINATIONS
                                                .get(0)
                                                .toString()))
                        .toString(),
                null);
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceNoisedDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_sourceFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableSourceDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceNoisedDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_without_enrollmentId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .setEnrollmentId("")
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceNoisedDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_noAdIdPermission_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceNoisedDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceNoisedDebugReport_adTechNotOpIn_dontSchedule() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceNoisedDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceStorageLimitDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceStorageLimitDebugReport(source, LIMIT, mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertSourceDebugReportParameters(
                report,
                DebugReportApi.Type.SOURCE_STORAGE_LIMIT,
                SourceFixture.ValidSourceParams.PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS.get(0).toString(),
                LIMIT);
    }

    @Test
    public void testScheduleSourceStorageLimitDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceStorageLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceStorageLimitDebugReport_sourceFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableSourceDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceStorageLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceStorageLimitDebugReport_adTechNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceStorageLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceStorageLimitDebugReport_without_enrollmentId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .setEnrollmentId("")
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceStorageLimitDebugReport(source, LIMIT, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleAppSourceUnknownErrorDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertSourceDebugReportParameters(
                report,
                DebugReportApi.Type.SOURCE_UNKNOWN_ERROR,
                SourceFixture.ValidSourceParams.PUBLISHER.toString(),
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS.get(0).toString(),
                null);
    }

    @Test
    public void testScheduleWebSourceUnknownErrorDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertSourceDebugReportParameters(
                report,
                DebugReportApi.Type.SOURCE_UNKNOWN_ERROR,
                SourceFixture.ValidSourceParams.WEB_PUBLISHER.toString(),
                new JSONArray(
                                Arrays.asList(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS
                                                .get(0)
                                                .toString(),
                                        SourceFixture.ValidSourceParams.WEB_DESTINATIONS
                                                .get(0)
                                                .toString()))
                        .toString(),
                null);
    }

    @Test
    public void testScheduleSourceUnknownErrorDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceUnknownErrorDebugReport_sourceFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableSourceDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceUnknownErrorDebugReport_without_enrollmentId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .setEnrollmentId("")
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleSourceUnknownErrorDebugReport_adTechNotOptIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleAppSourceUnknownErrorDebugReport_without_adidPermission_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.APP)
                        .setAppDestinations(
                                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                        .setAdIdPermission(false)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleWebSourceUnknownErrorDebugReport_without_arDebugPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleSourceUnknownErrorDebugReport(source, mMeasurementDao);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingSourceDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger, mMeasurementDao, DebugReportApi.Type.TRIGGER_NO_MATCHING_SOURCE);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingSourceDebugReport_success() throws Exception {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger, mMeasurementDao, DebugReportApi.Type.TRIGGER_NO_MATCHING_SOURCE);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        Assert.assertEquals(
                TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        Assert.assertEquals(Type.TRIGGER_NO_MATCHING_SOURCE, report.getType());
        Assert.assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void testScheduleTriggerNoMatchingSourceDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger, mMeasurementDao, DebugReportApi.Type.TRIGGER_NO_MATCHING_SOURCE);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingSourceDebugReport_triggerFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger, mMeasurementDao, DebugReportApi.Type.TRIGGER_NO_MATCHING_SOURCE);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingSourceDebugReport_noTriggerPermission_dontSchedule()
            throws Exception {
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger, mMeasurementDao, DebugReportApi.Type.TRIGGER_NO_MATCHING_SOURCE);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerUnknownErrorDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger, mMeasurementDao, DebugReportApi.Type.TRIGGER_UNKNOWN_ERROR);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerUnknownErrorDebugReport_success() throws Exception {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger, mMeasurementDao, DebugReportApi.Type.TRIGGER_UNKNOWN_ERROR);
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerUnknownErrorDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger, mMeasurementDao, DebugReportApi.Type.TRIGGER_UNKNOWN_ERROR);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerUnknownErrorDebugReport_triggerFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger, mMeasurementDao, DebugReportApi.Type.TRIGGER_UNKNOWN_ERROR);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerUnknownErrorDebugReport_noTriggerPermission_dontSchedule()
            throws Exception {
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger, mMeasurementDao, DebugReportApi.Type.TRIGGER_UNKNOWN_ERROR);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingFilterDataDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_NO_MATCHING_FILTER_DATA);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingFilterDataDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_NO_MATCHING_FILTER_DATA);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingFilterDataDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_NO_MATCHING_FILTER_DATA);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerNoMatchingFilterDataDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_NO_MATCHING_FILTER_DATA);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertEquals(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        assertEquals(Type.TRIGGER_NO_MATCHING_FILTER_DATA, report.getType());
        assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void testScheduleTriggerNoMatchingFilterDataDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_NO_MATCHING_FILTER_DATA);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerNoMatchingFilterDataDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_NO_MATCHING_FILTER_DATA);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerNoMatchingFilterDataDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_NO_MATCHING_FILTER_DATA);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventNoMatchingConfigurationDebugReport_triggerNotOpIn_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventNoMatchingConfigurationDebugReport_sourceNoAdId_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventNoMatchingConfigurationDebugReport_srcNoArDebug_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventNoMatchingConfigurationDebugReport_success()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertEquals(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        assertEquals(Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS, report.getType());
        assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void testScheduleTriggerEventNoMatchingConfigDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventNoMatchingConfigDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventNoMatchingConfigDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventLowPriorityDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_LOW_PRIORITY);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventLowPriorityDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_LOW_PRIORITY);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventLowPriorityDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_LOW_PRIORITY);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventLowPriorityDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_LOW_PRIORITY);
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertEquals(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        assertEquals(Type.TRIGGER_EVENT_LOW_PRIORITY, report.getType());
        assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void testScheduleTriggerEventLowPriorityDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_LOW_PRIORITY);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventLowPriorityDebugReport_triggerFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_LOW_PRIORITY);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventLowPriorityDebugReport_noTriggerPermission_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        UnsignedLong triggerData = new UnsignedLong(1L);

        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                triggerData,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_LOW_PRIORITY);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventExcessiveReportsDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_EXCESSIVE_REPORTS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventExcessiveReportsDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_EXCESSIVE_REPORTS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventExcessiveReportsDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_EXCESSIVE_REPORTS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventExcessiveReportsDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_EXCESSIVE_REPORTS);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertEquals(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        assertEquals(Type.TRIGGER_EVENT_EXCESSIVE_REPORTS, report.getType());
        assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void testScheduleTriggerEventExcessiveReportsDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_EXCESSIVE_REPORTS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventExcessiveReportsDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                TRIGGER_DATA,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_EXCESSIVE_REPORTS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventExcessiveReportsDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        UnsignedLong triggerData = new UnsignedLong(1L);

        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
                source,
                trigger,
                triggerData,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_EXCESSIVE_REPORTS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAttriPerSourceDesLimitDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAttriPerSourceDesLimitDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAttriPerSourceDesLimitDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAttriPerSourceDesLimitDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertEquals(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        assertEquals(Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT, report.getType());
        assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void
            testScheduleTriggerAttriPerSourceDesLimitDebugReport_debugFlagDisabled_dontSchedule()
                    throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAttriPerSourceDesLimitDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAttriPerSourceDesLimitDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowPassedDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowPassedDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowPassedDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowPassedDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertEquals(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        assertEquals(Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED, report.getType());
        assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void testScheduleTriggerEventWindowPassedDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowPassedDebugReport_triggerFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowPassedDebugReport_noTriggerPermission_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerReportingOriginLimitDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_REPORTING_ORIGIN_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerReportingOriginLimitDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_REPORTING_ORIGIN_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerReportingOriginLimitDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_REPORTING_ORIGIN_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerReportingOriginLimitDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_REPORTING_ORIGIN_LIMIT);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertEquals(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        assertEquals(Type.TRIGGER_REPORTING_ORIGIN_LIMIT, report.getType());
        assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void testScheduleTriggerReportingOriginLimitDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_REPORTING_ORIGIN_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerReportingOriginLimitDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_REPORTING_ORIGIN_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerReportingOriginLimitDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_REPORTING_ORIGIN_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventNoiseDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NOISE);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventNoiseDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NOISE);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventNoiseDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NOISE);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventNoiseDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NOISE);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertEquals(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        assertEquals(Type.TRIGGER_EVENT_NOISE, report.getType());
        assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void testScheduleTriggerEventNoiseDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NOISE);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventNoiseDebugReport_triggerFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NOISE);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventNoiseDebugReport_noTriggerPermission_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NOISE);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventStorageLimitDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_STORAGE_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventStorageLimitDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_STORAGE_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventStorageLimitDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_STORAGE_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventStorageLimitDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_STORAGE_LIMIT);
        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertEquals(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        assertEquals(Type.TRIGGER_EVENT_STORAGE_LIMIT, report.getType());
        assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void testScheduleTriggerEventStorageLimitDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_STORAGE_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventStorageLimitDebugReport_triggerFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_STORAGE_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventStorageLimitDebugReport_noTriggerPermission_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_STORAGE_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateWindowPassedDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateWindowPassedDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateWindowPassedDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateWindowPassedDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED);

        ArgumentCaptor<DebugReport> captor = ArgumentCaptor.forClass(DebugReport.class);
        verify(mMeasurementDao, times(1)).insertDebugReport(captor.capture());
        DebugReport report = captor.getValue();
        assertEquals(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID, report.getEnrollmentId());
        assertEquals(Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED, report.getType());
        assertEquals(
                TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN,
                report.getRegistrationOrigin());
    }

    @Test
    public void testScheduleTriggerAggregateWindowPassedDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAggregateWindowPassedDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAggregateWindowPassedDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggreInsufficientBudgetDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggreInsufficientBudgetDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggreInsufficientBudgetDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggreInsufficientBudgetDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET);
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAggreInsufficientBudgetDebugReport_debugFlagDisabled_dontSchedule()
                    throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAggreInsufficientBudgetDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAggreInsufficientBudgetDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateNoContributionDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_NO_CONTRIBUTIONS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateNoContributionDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_NO_CONTRIBUTIONS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateNoContributionDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_NO_CONTRIBUTIONS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateNoContributionDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_NO_CONTRIBUTIONS);
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAggregateNoContributionDebugReport_debugFlagDisabled_dontSchedule()
                    throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_NO_CONTRIBUTIONS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAggregateNoContributionDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_NO_CONTRIBUTIONS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAggregateNoContributionDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_NO_CONTRIBUTIONS);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateStorageLimitDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_STORAGE_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateStorageLimitDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_STORAGE_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateStorageLimitDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_STORAGE_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateStorageLimitDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_STORAGE_LIMIT);
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerAggregateStorageLimitDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_STORAGE_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAggregateStorageLimitDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_STORAGE_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerAggregateStorageLimitDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                LIMIT,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_AGGREGATE_STORAGE_LIMIT);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowNotStartedDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowNotStartedDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowNotStartedDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowNotStartedDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED);
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void testScheduleTriggerEventWindowNotStartedDebugReport_debugFlagDisabled_dontSchedule()
            throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventWindowNotStartedDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            testScheduleTriggerEventWindowNotStartedDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleTriggerEventNoMatchingTriggerDataDebugReport_triggerNotOpIn_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(false)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleTriggerEventNoMatchingTriggerDataDebugReport_sourceNoAdId_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleTriggerEventNoMatchingTriggerDataDebugReport_sourceNoArDebug_dontSchedule()
            throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setPublisher(SourceFixture.ValidSourceParams.WEB_PUBLISHER)
                        .setAppDestinations(null)
                        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                        .setArDebugPermission(false)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setAttributionDestination(
                                SourceFixture.ValidSourceParams.WEB_DESTINATIONS.get(0))
                        .setArDebugPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void scheduleTriggerEventNoMatchingTriggerDataDebugReport_success() throws Exception {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA);
        verify(mMeasurementDao, times(1)).insertDebugReport(any());
    }

    @Test
    public void
            scheduleTriggerEventNoMatchingTriggerDataDebugReport_debugFlagDisabled_dontSchedule()
                    throws Exception {
        when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            scheduleTriggerEventNoMatchingTriggerDataDebugReport_triggerFlagDisabled_dontSchedule()
                    throws Exception {
        when(mFlags.getMeasurementEnableTriggerDebugReport()).thenReturn(false);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    @Test
    public void
            scheduleTriggerEventNoMatchingTriggerDataDebugReport_noTriggerPermission_dontSchedule()
                    throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(SOURCE_EVENT_ID)
                        .setIsDebugReporting(true)
                        .setAdIdPermission(true)
                        .build();
        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setIsDebugReporting(true).build();
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        mDebugReportApi.scheduleTriggerDebugReport(
                source,
                trigger,
                /* limit =*/ null,
                mMeasurementDao,
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA);
        verify(mMeasurementDao, never()).insertDebugReport(any());
    }

    private static void assertSourceDebugReportParameters(
            DebugReport actualReport,
            String expectedReportType,
            String expectedSourcePublisher,
            String expectedSerializedDestinations,
            String limit)
            throws JSONException {
        JSONObject actualReportBody = actualReport.getBody();
        assertEquals(SourceFixture.ValidSourceParams.ENROLLMENT_ID, actualReport.getEnrollmentId());
        assertEquals(expectedReportType, actualReport.getType());
        assertEquals(
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN,
                actualReport.getRegistrationOrigin());
        assertEquals(
                SOURCE_EVENT_ID.toString(),
                actualReportBody.getString(DebugReportApi.Body.SOURCE_EVENT_ID));
        assertEquals(
                expectedSerializedDestinations,
                actualReportBody.getString(DebugReportApi.Body.ATTRIBUTION_DESTINATION));
        assertEquals(
                expectedSourcePublisher,
                actualReportBody.getString(DebugReportApi.Body.SOURCE_SITE));
        assertEquals(
                limit,
                actualReportBody.isNull(DebugReportApi.Body.LIMIT)
                        ? null
                        : actualReportBody.getString(DebugReportApi.Body.LIMIT));
    }
}
