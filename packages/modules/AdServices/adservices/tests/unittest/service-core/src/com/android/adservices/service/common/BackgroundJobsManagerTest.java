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

package com.android.adservices.service.common;

import static com.android.adservices.spe.AdServicesJobInfo.COBALT_LOGGING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.CONSENT_NOTIFICATION_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.ENCRYPTION_KEY_PERIODIC_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_BACKGROUND_FETCH_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MAINTENANCE_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_CHARGING_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_MAINTENANCE_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ASYNC_REGISTRATION_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ATTRIBUTION_FALLBACK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ATTRIBUTION_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_DELETE_EXPIRED_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_DELETE_UNINSTALLED_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_EVENT_MAIN_REPORTING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.PERIODIC_SIGNALS_ENCODING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.TOPICS_EPOCH_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.app.job.JobScheduler;

import com.android.adservices.cobalt.CobaltJobService;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.download.MddJobService;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.MaintenanceJobService;
import com.android.adservices.service.adselection.DebugReportSenderJobService;
import com.android.adservices.service.encryptionkey.EncryptionKeyJobService;
import com.android.adservices.service.measurement.DeleteExpiredJobService;
import com.android.adservices.service.measurement.DeleteUninstalledJobService;
import com.android.adservices.service.measurement.attribution.AttributionFallbackJobService;
import com.android.adservices.service.measurement.attribution.AttributionJobService;
import com.android.adservices.service.measurement.registration.AsyncRegistrationFallbackJobService;
import com.android.adservices.service.measurement.registration.AsyncRegistrationQueueJobService;
import com.android.adservices.service.measurement.reporting.AggregateFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.AggregateReportingJobService;
import com.android.adservices.service.measurement.reporting.DebugReportingFallbackJobService;
import com.android.adservices.service.measurement.reporting.EventFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.EventReportingJobService;
import com.android.adservices.service.measurement.reporting.VerboseDebugReportingFallbackJobService;
import com.android.adservices.service.topics.EpochJobService;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

@SpyStatic(AggregateReportingJobService.class)
@SpyStatic(AggregateFallbackReportingJobService.class)
@SpyStatic(AttributionJobService.class)
@SpyStatic(AttributionFallbackJobService.class)
@SpyStatic(BackgroundJobsManager.class)
@SpyStatic(EpochJobService.class)
@SpyStatic(EventReportingJobService.class)
@SpyStatic(EventFallbackReportingJobService.class)
@SpyStatic(DeleteExpiredJobService.class)
@SpyStatic(DeleteUninstalledJobService.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(MaintenanceJobService.class)
@SpyStatic(MddJobService.class)
@SpyStatic(EncryptionKeyJobService.class)
@SpyStatic(AsyncRegistrationQueueJobService.class)
@SpyStatic(AsyncRegistrationFallbackJobService.class)
@SpyStatic(DebugReportingFallbackJobService.class)
@SpyStatic(VerboseDebugReportingFallbackJobService.class)
@SpyStatic(CobaltJobService.class)
@SpyStatic(DebugReportSenderJobService.class)
public final class BackgroundJobsManagerTest extends AdServicesExtendedMockitoTestCase {

    @Mock private Flags mMockFlags;

    @Mock private JobScheduler mJobScheduler;

    @Before
    public void setDefaultExpectations() throws Exception {
        extendedMockito.mockGetFlags(mMockFlags);

        doNothing().when(() -> AggregateReportingJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing()
                .when(
                        () ->
                                AggregateFallbackReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean()));
        doNothing().when(() -> AttributionJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing().when(() -> AttributionFallbackJobService.scheduleIfNeeded(any(), anyBoolean()));
        doReturn(true).when(() -> EpochJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing().when(() -> EventReportingJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing()
                .when(() -> EventFallbackReportingJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing().when(() -> DeleteExpiredJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing().when(() -> DeleteUninstalledJobService.scheduleIfNeeded(any(), anyBoolean()));
        doReturn(true).when(() -> MaintenanceJobService.scheduleIfNeeded(any(), anyBoolean()));
        doReturn(true).when(() -> MddJobService.scheduleIfNeeded(any(), anyBoolean()));
        doReturn(true).when(() -> EncryptionKeyJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing()
                .when(() -> AsyncRegistrationQueueJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing()
                .when(
                        () ->
                                AsyncRegistrationFallbackJobService.scheduleIfNeeded(
                                        any(), anyBoolean()));
        doNothing()
                .when(() -> DebugReportingFallbackJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing()
                .when(
                        () ->
                                VerboseDebugReportingFallbackJobService.scheduleIfNeeded(
                                        any(), anyBoolean()));
        doReturn(true).when(() -> CobaltJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing().when(() -> DebugReportSenderJobService.scheduleIfNeeded(any(), anyBoolean()));
    }

    @Test
    public void testScheduleAllBackgroundJobs_killSwitchOff() throws Exception {
        mockMeasurementEnabled(true);
        when(mMockFlags.getTopicsKillSwitch()).thenReturn(false);
        when(mMockFlags.getFledgeSelectAdsKillSwitch()).thenReturn(false);
        when(mMockFlags.getMddBackgroundTaskKillSwitch()).thenReturn(false);
        when(mMockFlags.getEncryptionKeyPeriodicFetchKillSwitch()).thenReturn(false);
        when(mMockFlags.getCobaltLoggingEnabled()).thenReturn(true);
        when(mMockFlags.getFledgeEventLevelDebugReportingEnabled()).thenReturn(false);

        BackgroundJobsManager.scheduleAllBackgroundJobs(mMockContext);

        assertMeasurementJobsScheduled(1);
        assertTopicsJobsScheduled(1);
        // maintenance job is needed for both Fledge and Topics
        // since those APIs in the GA UX can be controlled separately, maintenance job
        // will be schedule for both Fledge and Topics. If there is a need to schedule
        // all the jobs, there will be two attempts to schedule the maintenance job, but
        // in fact only one maintenance job will be scheduled (due to deduplication)
        assertMaintenanceJobScheduled(2);
        // Mdd job is scheduled in scheduleAllBackgroundJobs,
        // scheduleTopicsBackgroundJobs, and scheduleMeasurementBackgroundJobs.
        assertMddJobsScheduled(3);
        // Encryption key job is scheduled in scheduleTopicsBackgroundJobs, and
        // scheduleMeasurementBackgroundJobs.
        assertEncryptionKeyJobsScheduled(2);
        // Cobalt Job is scheduled in scheduleTopicsBackgroundJobs, and
        // scheduleMeasurementBackgroundJobs.
        assertCobaltJobScheduled(2);
        assertAdSelectionDebugReportSenderJobScheduled(0);
    }

    @Test
    public void testScheduleAllBackgroundJobs_measurementKillSwitchOn() throws Exception {
        mockMeasurementEnabled(false);
        when(mMockFlags.getTopicsKillSwitch()).thenReturn(false);
        when(mMockFlags.getFledgeSelectAdsKillSwitch()).thenReturn(false);

        when(mMockFlags.getMddBackgroundTaskKillSwitch()).thenReturn(false);

        when(mMockFlags.getEncryptionKeyPeriodicFetchKillSwitch()).thenReturn(false);
        when(mMockFlags.getCobaltLoggingEnabled()).thenReturn(true);

        when(mMockFlags.getFledgeEventLevelDebugReportingEnabled()).thenReturn(false);

        BackgroundJobsManager.scheduleAllBackgroundJobs(mMockContext);

        assertMeasurementJobsScheduled(0);
        assertTopicsJobsScheduled(1);
        // maintenance job is needed for both Fledge and Topics
        // since those APIs in the GA UX can be controlled separately, maintenance job
        // will be schedule for both Fledge and Topics. If there is a need to schedule
        // all the jobs, there will be two attempts to schedule the maintenance job, but
        // in fact only one maintenance job will be scheduled (due to deduplication)
        assertMaintenanceJobScheduled(2);
        // Mdd job is scheduled in scheduleAllBackgroundJobs and
        // scheduleTopicsBackgroundJobs.
        assertMddJobsScheduled(2);
        // Encryption key job is scheduled in scheduleTopicsBackgroundJobs.
        assertEncryptionKeyJobsScheduled(1);
        assertCobaltJobScheduled(1);
        assertAdSelectionDebugReportSenderJobScheduled(0);
    }

    @Test
    public void testScheduleAllBackgroundJobs_topicsKillSwitchOn() throws Exception {
        mockMeasurementEnabled(true);
        when(mMockFlags.getTopicsKillSwitch()).thenReturn(true);
        when(mMockFlags.getFledgeSelectAdsKillSwitch()).thenReturn(false);

        when(mMockFlags.getMddBackgroundTaskKillSwitch()).thenReturn(false);

        when(mMockFlags.getEncryptionKeyPeriodicFetchKillSwitch()).thenReturn(false);
        when(mMockFlags.getCobaltLoggingEnabled()).thenReturn(true);

        when(mMockFlags.getFledgeEventLevelDebugReportingEnabled()).thenReturn(false);

        BackgroundJobsManager.scheduleAllBackgroundJobs(mMockContext);

        assertMeasurementJobsScheduled(1);
        assertTopicsJobsScheduled(0);
        assertMaintenanceJobScheduled(1);
        // Mdd job is scheduled in scheduleAllBackgroundJobs and
        // scheduleMeasurementBackgroundJobs.
        assertMddJobsScheduled(2);
        // Encryption key job is scheduled in scheduleMeasurementBackgroundJobs.
        assertEncryptionKeyJobsScheduled(1);
        // Cobalt Job is scheduled in scheduleMeasurementBackgroundJobs.
        assertCobaltJobScheduled(1);
        assertAdSelectionDebugReportSenderJobScheduled(0);
    }

    @Test
    public void testScheduleAllBackgroundJobs_mddKillSwitchOn() throws Exception {
        mockMeasurementEnabled(true);
        when(mMockFlags.getTopicsKillSwitch()).thenReturn(false);
        when(mMockFlags.getFledgeSelectAdsKillSwitch()).thenReturn(false);

        when(mMockFlags.getMddBackgroundTaskKillSwitch()).thenReturn(true);
        when(mMockFlags.getCobaltLoggingEnabled()).thenReturn(true);

        when(mMockFlags.getFledgeEventLevelDebugReportingEnabled()).thenReturn(false);

        BackgroundJobsManager.scheduleAllBackgroundJobs(mMockContext);

        assertMeasurementJobsScheduled(1);
        assertTopicsJobsScheduled(1);
        // maintenance job is needed for both Fledge and Topics
        // since those APIs in the GA UX can be controlled separately, maintenance job
        // will be schedule for both Fledge and Topics. If there is a need to schedule
        // all the jobs, there will be two attempts to schedule the maintenance job, but
        // in fact only one maintenance job will be scheduled (due to deduplication)
        assertMaintenanceJobScheduled(2);
        assertMddJobsScheduled(0);
        // Cobalt Job is scheduled in scheduleTopicsBackgroundJobs, and
        // scheduleMeasurementBackgroundJobs.
        assertCobaltJobScheduled(2);
        assertAdSelectionDebugReportSenderJobScheduled(0);
    }

    @Test
    public void testScheduleAllBackgroundJobs_encryptionKeyKillSwitchOn() throws Exception {
        mockMeasurementEnabled(true);
        when(mMockFlags.getTopicsKillSwitch()).thenReturn(false);
        when(mMockFlags.getFledgeSelectAdsKillSwitch()).thenReturn(false);

        when(mMockFlags.getMddBackgroundTaskKillSwitch()).thenReturn(false);

        when(mMockFlags.getEncryptionKeyPeriodicFetchKillSwitch()).thenReturn(true);
        when(mMockFlags.getCobaltLoggingEnabled()).thenReturn(true);

        when(mMockFlags.getFledgeEventLevelDebugReportingEnabled()).thenReturn(false);

        BackgroundJobsManager.scheduleAllBackgroundJobs(mMockContext);

        assertMeasurementJobsScheduled(1);
        assertTopicsJobsScheduled(1);
        // maintenance job is needed for both Fledge and Topics
        // since those APIs in the GA UX can be controlled separately, maintenance job
        // will be schedule for both Fledge and Topics. If there is a need to schedule
        // all the jobs, there will be two attempts to schedule the maintenance job, but
        // in fact only one maintenance job will be scheduled (due to deduplication)
        assertMaintenanceJobScheduled(2);
        assertMddJobsScheduled(3);
        assertEncryptionKeyJobsScheduled(0);
        // Cobalt Job is scheduled in scheduleTopicsBackgroundJobs, and
        // scheduleMeasurementBackgroundJobs.
        assertCobaltJobScheduled(2);
        assertAdSelectionDebugReportSenderJobScheduled(0);
    }

    @Test
    public void testScheduleAllBackgroundJobs_selectAdsKillSwitchOn() throws Exception {
        mockMeasurementEnabled(true);
        when(mMockFlags.getTopicsKillSwitch()).thenReturn(false);
        when(mMockFlags.getFledgeSelectAdsKillSwitch()).thenReturn(true);

        when(mMockFlags.getMddBackgroundTaskKillSwitch()).thenReturn(false);

        when(mMockFlags.getEncryptionKeyPeriodicFetchKillSwitch()).thenReturn(false);
        when(mMockFlags.getCobaltLoggingEnabled()).thenReturn(true);

        when(mMockFlags.getFledgeEventLevelDebugReportingEnabled()).thenReturn(false);

        BackgroundJobsManager.scheduleAllBackgroundJobs(mMockContext);

        assertMeasurementJobsScheduled(1);
        assertTopicsJobsScheduled(1);
        assertMaintenanceJobScheduled(1);
        // Mdd job is scheduled in scheduleAllBackgroundJobs and
        // scheduleTopicsBackgroundJobs.
        assertMddJobsScheduled(3);
        // Encryption key job is scheduled in scheduleTopicsBackgroundJobs, and
        // scheduleMeasurementBackgroundJobs.
        assertEncryptionKeyJobsScheduled(2);
        // Cobalt Job is scheduled in scheduleTopicsBackgroundJobs, and
        // scheduleMeasurementBackgroundJobs.
        assertCobaltJobScheduled(2);
        assertAdSelectionDebugReportSenderJobScheduled(0);
    }

    @Test
    public void testScheduleAllBackgroundJobs_topicsAndSelectAdsKillSwitchOn() throws Exception {
        mockMeasurementEnabled(true);
        when(mMockFlags.getTopicsKillSwitch()).thenReturn(true);
        when(mMockFlags.getFledgeSelectAdsKillSwitch()).thenReturn(true);

        when(mMockFlags.getMddBackgroundTaskKillSwitch()).thenReturn(false);

        when(mMockFlags.getEncryptionKeyPeriodicFetchKillSwitch()).thenReturn(false);
        when(mMockFlags.getCobaltLoggingEnabled()).thenReturn(true);

        BackgroundJobsManager.scheduleAllBackgroundJobs(mMockContext);

        assertMeasurementJobsScheduled(1);
        assertTopicsJobsScheduled(0);
        assertMaintenanceJobScheduled(0);
        // Mdd job is scheduled in scheduleAllBackgroundJobs and
        // scheduleMeasurementBackgroundJobs.
        assertMddJobsScheduled(2);
        // Encryption key job is scheduled in scheduleMeasurementBackgroundJobs.
        assertEncryptionKeyJobsScheduled(1);
        // Cobalt Job is scheduled in scheduleMeasurementBackgroundJobs.
        assertCobaltJobScheduled(1);
        assertAdSelectionDebugReportSenderJobScheduled(0);
    }

    @Test
    public void testScheduleAllBackgroundJobs_cobaltLoggingDisabled() throws Exception {
        mockMeasurementEnabled(true);
        when(mMockFlags.getTopicsKillSwitch()).thenReturn(false);
        when(mMockFlags.getFledgeSelectAdsKillSwitch()).thenReturn(false);

        when(mMockFlags.getMddBackgroundTaskKillSwitch()).thenReturn(false);

        when(mMockFlags.getEncryptionKeyPeriodicFetchKillSwitch()).thenReturn(false);
        when(mMockFlags.getCobaltLoggingEnabled()).thenReturn(false);

        BackgroundJobsManager.scheduleAllBackgroundJobs(mMockContext);

        assertMeasurementJobsScheduled(1);
        assertTopicsJobsScheduled(1);
        assertMaintenanceJobScheduled(2);
        // Mdd job is scheduled in scheduleAllBackgroundJobs,
        // scheduleTopicsBackgroundJobs, and scheduleMeasurementBackgroundJobs.
        assertMddJobsScheduled(3);
        // Encryption key job is scheduled in scheduleTopicsBackgroundJobs, and
        // scheduleMeasurementBackgroundJobs.
        assertEncryptionKeyJobsScheduled(2);
        assertCobaltJobScheduled(0);
    }

    @Test
    public void testScheduleMeasurementBackgroundJobs_measurementKillSwitchOn() throws Exception {
        mockMeasurementEnabled(false);

        BackgroundJobsManager.scheduleMeasurementBackgroundJobs(mMockContext);

        assertMeasurementJobsScheduled(0);
        assertTopicsJobsScheduled(0);
        assertMaintenanceJobScheduled(0);
        assertMddJobsScheduled(0);
        assertEncryptionKeyJobsScheduled(0);
        assertCobaltJobScheduled(0);
        assertAdSelectionDebugReportSenderJobScheduled(0);
    }

    @Test
    public void testScheduleMeasurementBackgroundJobs_measurementKillSwitchOff() throws Exception {
        mockMeasurementEnabled(true);

        BackgroundJobsManager.scheduleMeasurementBackgroundJobs(mMockContext);

        assertMeasurementJobsScheduled(1);
        assertTopicsJobsScheduled(0);
        assertMaintenanceJobScheduled(0);
        assertMddJobsScheduled(1);
        assertEncryptionKeyJobsScheduled(1);
        assertCobaltJobScheduled(0);
        assertAdSelectionDebugReportSenderJobScheduled(0);
    }

    @Test
    public void testScheduleTopicsBackgroundJobs_topicsKillSwitchOn() throws Exception {
        when(mMockFlags.getTopicsKillSwitch()).thenReturn(true);
        when(mMockFlags.getCobaltLoggingEnabled()).thenReturn(true);

        BackgroundJobsManager.scheduleTopicsBackgroundJobs(mMockContext);

        assertMeasurementJobsScheduled(0);
        assertTopicsJobsScheduled(0);
        assertMaintenanceJobScheduled(0);
        assertMddJobsScheduled(0);
        assertEncryptionKeyJobsScheduled(0);
        assertCobaltJobScheduled(0);
        assertAdSelectionDebugReportSenderJobScheduled(0);
    }

    @Test
    public void testScheduleTopicsBackgroundJobs_topicsKillSwitchOff() throws Exception {
        when(mMockFlags.getTopicsKillSwitch()).thenReturn(false);
        when(mMockFlags.getCobaltLoggingEnabled()).thenReturn(true);

        BackgroundJobsManager.scheduleTopicsBackgroundJobs(mMockContext);

        assertMeasurementJobsScheduled(0);
        assertTopicsJobsScheduled(1);
        assertMaintenanceJobScheduled(1);
        assertMddJobsScheduled(1);
        assertEncryptionKeyJobsScheduled(1);
        assertCobaltJobScheduled(1);
        assertAdSelectionDebugReportSenderJobScheduled(0);
    }

    @Test
    public void testScheduleFledgeBackgroundJobs_selectAdsKillSwitchOn() throws Exception {
        when(mMockFlags.getFledgeSelectAdsKillSwitch()).thenReturn(true);

        BackgroundJobsManager.scheduleFledgeBackgroundJobs(mMockContext);

        assertMeasurementJobsScheduled(0);
        assertTopicsJobsScheduled(0);
        assertMaintenanceJobScheduled(0);
        assertMddJobsScheduled(0);
        assertCobaltJobScheduled(0);
        assertAdSelectionDebugReportSenderJobScheduled(0);
    }

    @Test
    public void testScheduleFledgeBackgroundJobs_selectAdsKillSwitchOff() throws Exception {
        when(mMockFlags.getFledgeSelectAdsKillSwitch()).thenReturn(false);

        when(mMockFlags.getFledgeEventLevelDebugReportingEnabled()).thenReturn(false);

        BackgroundJobsManager.scheduleFledgeBackgroundJobs(mMockContext);

        assertMeasurementJobsScheduled(0);
        assertTopicsJobsScheduled(0);
        assertMaintenanceJobScheduled(1);
        assertMddJobsScheduled(0);
        assertCobaltJobScheduled(0);
        assertAdSelectionDebugReportSenderJobScheduled(0);
    }

    @Test
    public void testScheduleFledgeBackgroundJobs_selectAdsKillSwitchOnDebugReportingOn()
            throws Exception {
        when(mMockFlags.getFledgeSelectAdsKillSwitch()).thenReturn(false);

        when(mMockFlags.getFledgeEventLevelDebugReportingEnabled()).thenReturn(true);

        BackgroundJobsManager.scheduleFledgeBackgroundJobs(mMockContext);

        assertMeasurementJobsScheduled(0);
        assertTopicsJobsScheduled(0);
        assertMaintenanceJobScheduled(1);
        assertMddJobsScheduled(0);
        assertCobaltJobScheduled(0);
        assertAdSelectionDebugReportSenderJobScheduled(1);
    }

    @Test
    public void testScheduleCobaltBackgroundJobs_CobaltLoggingEnabled() throws Exception {
        when(mMockFlags.getCobaltLoggingEnabled()).thenReturn(true);

        BackgroundJobsManager.scheduleCobaltBackgroundJob(mMockContext);

        assertMeasurementJobsScheduled(0);
        assertTopicsJobsScheduled(0);
        assertMaintenanceJobScheduled(0);
        assertMddJobsScheduled(0);
        assertCobaltJobScheduled(1);
        assertAdSelectionDebugReportSenderJobScheduled(0);
    }

    @Test
    public void testScheduleCobaltBackgroundJobs_CobaltLoggingdisabled() throws Exception {
        when(mMockFlags.getCobaltLoggingEnabled()).thenReturn(false);

        BackgroundJobsManager.scheduleCobaltBackgroundJob(mMockContext);

        assertMeasurementJobsScheduled(0);
        assertTopicsJobsScheduled(0);
        assertMaintenanceJobScheduled(0);
        assertMddJobsScheduled(0);
        assertCobaltJobScheduled(0);
        assertAdSelectionDebugReportSenderJobScheduled(0);
    }

    @Test
    public void testUnscheduleAllBackgroundJobs() throws Exception {
        BackgroundJobsManager.unscheduleAllBackgroundJobs(mJobScheduler);

        // Verification
        verify(mJobScheduler).cancel(MAINTENANCE_JOB.getJobId());
        verify(mJobScheduler).cancel(TOPICS_EPOCH_JOB.getJobId());
        verify(mJobScheduler).cancel(MEASUREMENT_EVENT_MAIN_REPORTING_JOB.getJobId());
        verify(mJobScheduler).cancel(MEASUREMENT_DELETE_EXPIRED_JOB.getJobId());
        verify(mJobScheduler).cancel(MEASUREMENT_DELETE_UNINSTALLED_JOB.getJobId());
        verify(mJobScheduler).cancel(MEASUREMENT_ATTRIBUTION_JOB.getJobId());
        verify(mJobScheduler).cancel(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB.getJobId());
        verify(mJobScheduler).cancel(MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB.getJobId());
        verify(mJobScheduler).cancel(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB.getJobId());
        verify(mJobScheduler).cancel(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB.getJobId());
        verify(mJobScheduler).cancel(FLEDGE_BACKGROUND_FETCH_JOB.getJobId());
        verify(mJobScheduler).cancel(PERIODIC_SIGNALS_ENCODING_JOB.getJobId());
        verify(mJobScheduler).cancel(CONSENT_NOTIFICATION_JOB.getJobId());
        verify(mJobScheduler).cancel(MDD_MAINTENANCE_PERIODIC_TASK_JOB.getJobId());
        verify(mJobScheduler).cancel(MDD_CHARGING_PERIODIC_TASK_JOB.getJobId());
        verify(mJobScheduler).cancel(MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB.getJobId());
        verify(mJobScheduler).cancel(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB.getJobId());
        verify(mJobScheduler).cancel(ENCRYPTION_KEY_PERIODIC_JOB.getJobId());
        verify(mJobScheduler).cancel(MEASUREMENT_ASYNC_REGISTRATION_JOB.getJobId());
        verify(mJobScheduler).cancel(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB.getJobId());
        verify(mJobScheduler).cancel(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB.getJobId());
        verify(mJobScheduler).cancel(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB.getJobId());
        verify(mJobScheduler).cancel(COBALT_LOGGING_JOB.getJobId());
    }

    private void assertMeasurementJobsScheduled(int numberOfTimes) {
        verify(
                () -> AggregateReportingJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
        verify(
                () -> AggregateFallbackReportingJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
        verify(
                () -> AttributionJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
        verify(
                () -> AttributionFallbackJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
        verify(
                () -> EventReportingJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
        verify(
                () -> EventFallbackReportingJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
        verify(
                () -> DeleteExpiredJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
        verify(
                () -> DeleteUninstalledJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
        verify(
                () -> AsyncRegistrationQueueJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
        verify(
                () -> AsyncRegistrationFallbackJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
        verify(
                () -> VerboseDebugReportingFallbackJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
        verify(
                () -> DebugReportingFallbackJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
    }

    private void assertMaintenanceJobScheduled(int numberOfTimes) {
        verify(
                () -> MaintenanceJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
    }

    private void assertAdSelectionDebugReportSenderJobScheduled(int numberOfTimes) {
        verify(
                () -> DebugReportSenderJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
    }

    private void assertTopicsJobsScheduled(int numberOfTimes) {
        verify(() -> EpochJobService.scheduleIfNeeded(any(), eq(false)), times(numberOfTimes));
    }

    private void assertMddJobsScheduled(int numberOfTimes) {
        verify(() -> MddJobService.scheduleIfNeeded(any(), eq(false)), times(numberOfTimes));
    }

    private void assertEncryptionKeyJobsScheduled(int numberOfTimes) {
        verify(
                () -> EncryptionKeyJobService.scheduleIfNeeded(any(), eq(false)),
                times(numberOfTimes));
    }

    private void assertCobaltJobScheduled(int numberOfTimes) {
        verify(() -> CobaltJobService.scheduleIfNeeded(any(), eq(false)), times(numberOfTimes));
    }

    private void mockMeasurementEnabled(boolean value) {
        when(mMockFlags.getMeasurementEnabled()).thenReturn(value);
    }
}
