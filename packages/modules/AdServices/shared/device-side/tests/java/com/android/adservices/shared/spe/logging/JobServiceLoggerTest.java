/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.shared.spe.logging;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__HALTED_FOR_UNKNOWN_REASON;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SUCCESSFUL;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__MODULE_NAME__UNKNOWN_MODULE_NAME;
import static com.android.adservices.shared.spe.JobServiceConstants.MILLISECONDS_PER_MINUTE;
import static com.android.adservices.shared.spe.JobServiceConstants.SHARED_PREFS_BACKGROUND_JOBS;
import static com.android.adservices.shared.spe.JobServiceConstants.UNAVAILABLE_JOB_EXECUTION_PERIOD;
import static com.android.adservices.shared.spe.JobServiceConstants.UNAVAILABLE_JOB_EXECUTION_START_TIMESTAMP;
import static com.android.adservices.shared.spe.JobServiceConstants.UNAVAILABLE_JOB_EXECUTION_STOP_TIMESTAMP;
import static com.android.adservices.shared.spe.JobServiceConstants.UNAVAILABLE_JOB_LATENCY;
import static com.android.adservices.shared.spe.JobServiceConstants.UNAVAILABLE_STOP_REASON;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.shared.common.flags.ModuleSharedFlags;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.util.Clock;

import com.google.common.collect.ImmutableMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.concurrent.Executors;

/** Unit test for {@link JobServiceLogger}. */
public final class JobServiceLoggerTest extends AdServicesExtendedMockitoTestCase {
    // Use an arbitrary job ID for testing. It won't have side effect to use production id as
    // the test doesn't actually schedule a job. This avoids complicated mocking logic.
    private static final int JOB_ID_1 = 1;
    private static final int JOB_ID_2 = 2;
    private static final ImmutableMap<Integer, String> sJobIdToNameMap =
            new ImmutableMap.Builder<Integer, String>()
                    .put(JOB_ID_1, "job_1")
                    .put(JOB_ID_2, "job_2")
                    .build();
    private JobServiceLogger mLogger;

    @Mock private ModuleSharedFlags mMockFlags;
    @Mock private StatsdJobServiceLogger mMockStatsdLogger;
    @Mock private AdServicesErrorLogger mMockErrorLogger;

    @Before
    public void setup() {
        mLogger =
                spy(
                        new JobServiceLogger(
                                sContext,
                                Clock.getInstance(),
                                mMockStatsdLogger,
                                mMockErrorLogger,
                                Executors.newCachedThreadPool(),
                                sJobIdToNameMap,
                                mMockFlags));

        // Clear shared preference
        sContext.deleteSharedPreferences(SHARED_PREFS_BACKGROUND_JOBS);
    }

    @After
    public void teardown() {
        // Clear shared preference
        sContext.deleteSharedPreferences(SHARED_PREFS_BACKGROUND_JOBS);
    }

    @Test
    public void testPersistJobExecutionData_firstExecution() {
        String keyJobStartTime = JobServiceLogger.getJobStartTimestampKey(JOB_ID_1);
        String keyExecutionPeriod = JobServiceLogger.getExecutionPeriodKey(JOB_ID_1);
        SharedPreferences sharedPreferences =
                sContext.getSharedPreferences(SHARED_PREFS_BACKGROUND_JOBS, Context.MODE_PRIVATE);
        long startJobTimestamp = 100L;

        mLogger.persistJobExecutionData(JOB_ID_1, startJobTimestamp);

        expect.that(
                        sharedPreferences.getLong(
                                keyJobStartTime, UNAVAILABLE_JOB_EXECUTION_START_TIMESTAMP))
                .isEqualTo(startJobTimestamp);
        expect.that(sharedPreferences.getLong(keyExecutionPeriod, UNAVAILABLE_JOB_EXECUTION_PERIOD))
                .isEqualTo(UNAVAILABLE_JOB_EXECUTION_PERIOD);
    }

    @Test
    public void testPersistJobExecutionData_openEndedLastExecution() {
        String keyJobStartTime = JobServiceLogger.getJobStartTimestampKey(JOB_ID_1);
        String keyExecutionPeriod = JobServiceLogger.getExecutionPeriodKey(JOB_ID_1);
        String keyJobStopTime = JobServiceLogger.getJobStopTimestampKey(JOB_ID_1);
        // previousJobStopTime < previousJobStartTime, which indicates previous execution finished
        // with an open end.
        long previousJobStartTime = 100L;
        long previousJobStopTime = 50L;
        long previousExecutionPeriod = 100L;
        long currentJobStartTime = 300L;

        // Store previous execution data.
        SharedPreferences sharedPreferences =
                sContext.getSharedPreferences(SHARED_PREFS_BACKGROUND_JOBS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(keyJobStartTime, previousJobStartTime);
        editor.putLong(keyJobStopTime, previousJobStopTime);
        editor.putLong(keyExecutionPeriod, previousExecutionPeriod);
        editor.commit();

        // Do not actually upload to server.
        doNothing()
                .when(mLogger)
                .logJobStatsHelper(
                        JOB_ID_1,
                        UNAVAILABLE_JOB_LATENCY,
                        previousExecutionPeriod,
                        AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__HALTED_FOR_UNKNOWN_REASON,
                        UNAVAILABLE_STOP_REASON);

        mLogger.persistJobExecutionData(JOB_ID_1, currentJobStartTime);

        verify(mLogger)
                .logJobStatsHelper(
                        JOB_ID_1,
                        UNAVAILABLE_JOB_LATENCY,
                        previousExecutionPeriod,
                        AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__HALTED_FOR_UNKNOWN_REASON,
                        UNAVAILABLE_STOP_REASON);
        expect.that(
                        sharedPreferences.getLong(
                                keyJobStartTime, UNAVAILABLE_JOB_EXECUTION_START_TIMESTAMP))
                .isEqualTo(currentJobStartTime);
        expect.that(sharedPreferences.getLong(keyExecutionPeriod, UNAVAILABLE_JOB_EXECUTION_PERIOD))
                .isEqualTo(currentJobStartTime - previousJobStartTime);
    }

    @Test
    public void testPersistJobExecutionData_closeEndedLastExecution() {
        String keyJobStartTime = JobServiceLogger.getJobStartTimestampKey(JOB_ID_1);
        String keyExecutionPeriod = JobServiceLogger.getExecutionPeriodKey(JOB_ID_1);
        String keyJobStopTime = JobServiceLogger.getJobStopTimestampKey(JOB_ID_1);
        long previousJobStartTime = 100L;
        long previousJobStopTime = 150L;
        long previousExecutionPeriod = 100L;
        long currentJobStartTime = 300L;

        // Store previous execution data.
        SharedPreferences sharedPreferences =
                sContext.getSharedPreferences(SHARED_PREFS_BACKGROUND_JOBS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(keyJobStartTime, previousJobStartTime);
        editor.putLong(keyJobStopTime, previousJobStopTime);
        editor.putLong(keyExecutionPeriod, previousExecutionPeriod);
        editor.commit();

        doNothing()
                .when(mLogger)
                .logJobStatsHelper(
                        JOB_ID_1,
                        UNAVAILABLE_JOB_LATENCY,
                        previousExecutionPeriod,
                        AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__HALTED_FOR_UNKNOWN_REASON,
                        UNAVAILABLE_STOP_REASON);

        mLogger.persistJobExecutionData(JOB_ID_1, currentJobStartTime);

        verify(mLogger, never())
                .logJobStatsHelper(
                        JOB_ID_1,
                        UNAVAILABLE_JOB_LATENCY,
                        previousExecutionPeriod,
                        AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__HALTED_FOR_UNKNOWN_REASON,
                        UNAVAILABLE_STOP_REASON);
        expect.that(
                        sharedPreferences.getLong(
                                keyJobStartTime, UNAVAILABLE_JOB_EXECUTION_START_TIMESTAMP))
                .isEqualTo(currentJobStartTime);
        expect.that(sharedPreferences.getLong(keyExecutionPeriod, UNAVAILABLE_JOB_EXECUTION_PERIOD))
                .isEqualTo(currentJobStartTime - previousJobStartTime);
    }

    @Test
    public void testPersistJobExecutionData_multipleJobs() {
        String keyJobStartTime1 = JobServiceLogger.getJobStartTimestampKey(JOB_ID_1);
        String keyExecutionPeriod1 = JobServiceLogger.getExecutionPeriodKey(JOB_ID_1);
        String keyJobStartTime2 = JobServiceLogger.getJobStartTimestampKey(JOB_ID_2);
        String keyExecutionPeriod2 = JobServiceLogger.getExecutionPeriodKey(JOB_ID_2);
        SharedPreferences sharedPreferences =
                sContext.getSharedPreferences(SHARED_PREFS_BACKGROUND_JOBS, Context.MODE_PRIVATE);
        long startJobTimestamp1 = 100L;
        long startJobTimestamp2 = 200L;

        mLogger.persistJobExecutionData(JOB_ID_1, startJobTimestamp1);
        mLogger.persistJobExecutionData(JOB_ID_2, startJobTimestamp2);

        expect.that(
                        sharedPreferences.getLong(
                                keyJobStartTime1, UNAVAILABLE_JOB_EXECUTION_START_TIMESTAMP))
                .isEqualTo(startJobTimestamp1);
        expect.that(
                        sharedPreferences.getLong(
                                keyExecutionPeriod1, UNAVAILABLE_JOB_EXECUTION_PERIOD))
                .isEqualTo(UNAVAILABLE_JOB_EXECUTION_PERIOD);
        expect.that(
                        sharedPreferences.getLong(
                                keyJobStartTime2, UNAVAILABLE_JOB_EXECUTION_START_TIMESTAMP))
                .isEqualTo(startJobTimestamp2);
        expect.that(
                        sharedPreferences.getLong(
                                keyExecutionPeriod2, UNAVAILABLE_JOB_EXECUTION_PERIOD))
                .isEqualTo(UNAVAILABLE_JOB_EXECUTION_PERIOD);
    }

    @Test
    public void testLogExecutionStats() {
        String keyJobStartTime = JobServiceLogger.getJobStartTimestampKey(JOB_ID_1);
        String keyExecutionPeriod = JobServiceLogger.getExecutionPeriodKey(JOB_ID_1);
        String keyJobStopTime = JobServiceLogger.getJobStopTimestampKey(JOB_ID_1);
        long jobStartTime = 100L;
        long jobStopTime = 200L;
        long executionPeriod = 50L;
        long executionLatency = jobStopTime - jobStartTime;
        int stopReason = UNAVAILABLE_STOP_REASON;
        int resultCode =
                AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SUCCESSFUL;

        // Store execution data.
        SharedPreferences sharedPreferences =
                sContext.getSharedPreferences(SHARED_PREFS_BACKGROUND_JOBS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(keyJobStartTime, jobStartTime);
        editor.putLong(keyExecutionPeriod, executionPeriod);
        editor.commit();

        doNothing()
                .when(mLogger)
                .logJobStatsHelper(
                        JOB_ID_1, executionLatency, executionPeriod, resultCode, stopReason);
        mLogger.logExecutionStats(JOB_ID_1, jobStopTime, resultCode, stopReason);

        verify(mLogger)
                .logJobStatsHelper(
                        JOB_ID_1, executionLatency, executionPeriod, resultCode, stopReason);
        expect.that(
                        sharedPreferences.getLong(
                                keyJobStopTime, UNAVAILABLE_JOB_EXECUTION_STOP_TIMESTAMP))
                .isEqualTo(jobStopTime);
    }

    @Test
    public void testLogExecutionStats_invalidStats() {
        String keyJobStartTime = JobServiceLogger.getJobStartTimestampKey(JOB_ID_1);
        String keyExecutionPeriod = JobServiceLogger.getExecutionPeriodKey(JOB_ID_1);
        String keyJobStopTime = JobServiceLogger.getJobStopTimestampKey(JOB_ID_1);
        long jobStopTime = 200L;
        long executionPeriod = 50L;
        int stopReason = UNAVAILABLE_STOP_REASON;
        int resultCode =
                AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SUCCESSFUL;

        // Store execution data.
        SharedPreferences sharedPreferences =
                sContext.getSharedPreferences(SHARED_PREFS_BACKGROUND_JOBS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        // Invalid start Time.
        editor.putLong(keyJobStartTime, UNAVAILABLE_JOB_EXECUTION_PERIOD);
        editor.putLong(keyExecutionPeriod, executionPeriod);
        editor.commit();

        mLogger.logExecutionStats(JOB_ID_1, jobStopTime, resultCode, stopReason);

        // Verify stop time is not updated.
        expect.that(
                        sharedPreferences.getLong(
                                keyJobStopTime, UNAVAILABLE_JOB_EXECUTION_STOP_TIMESTAMP))
                .isEqualTo(UNAVAILABLE_JOB_EXECUTION_STOP_TIMESTAMP);

        editor = sharedPreferences.edit();
        // Invalid start Time. (later than stop time)
        editor.putLong(keyJobStartTime, jobStopTime + 1);
        editor.commit();
        mLogger.logExecutionStats(JOB_ID_1, jobStopTime, resultCode, stopReason);

        // Verify stop time is not updated.
        expect.that(
                        sharedPreferences.getLong(
                                keyJobStopTime, UNAVAILABLE_JOB_EXECUTION_STOP_TIMESTAMP))
                .isEqualTo(UNAVAILABLE_JOB_EXECUTION_STOP_TIMESTAMP);
    }

    @Test
    public void testLogJobStatsHelper() {
        // Mock to avoid sampling logging
        doReturn(true).when(mLogger).shouldLog();

        ArgumentCaptor<ExecutionReportedStats> captor =
                ArgumentCaptor.forClass(ExecutionReportedStats.class);
        long executionDurationInMs = 1000L;
        long executionFrequencyInMs = 2000L * MILLISECONDS_PER_MINUTE;
        int resultCode =
                AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SUCCESSFUL;
        int stopReason = UNAVAILABLE_STOP_REASON;

        mLogger.logJobStatsHelper(
                JOB_ID_1, executionDurationInMs, executionFrequencyInMs, resultCode, stopReason);

        verify(mMockStatsdLogger).logExecutionReportedStats(captor.capture());
        expect.that(captor.getValue())
                .isEqualTo(
                        ExecutionReportedStats.builder()
                                .setJobId(JOB_ID_1)
                                .setExecutionLatencyMs((int) executionDurationInMs)
                                .setExecutionPeriodMinute(
                                        (int) (executionFrequencyInMs / MILLISECONDS_PER_MINUTE))
                                .setExecutionResultCode(resultCode)
                                .setStopReason(stopReason)
                                .setModuleName(AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__MODULE_NAME__UNKNOWN_MODULE_NAME)
                                .build());
    }

    @Test
    public void testLogJobStatsHelper_overflowValues() {
        // Mock to avoid sampling logging
        doReturn(true).when(mLogger).shouldLog();

        ArgumentCaptor<ExecutionReportedStats> captor =
                ArgumentCaptor.forClass(ExecutionReportedStats.class);
        long executionDurationInMs = 1L + Integer.MAX_VALUE;
        long executionFrequencyInMs = (1L + Integer.MAX_VALUE) * MILLISECONDS_PER_MINUTE;
        int resultCode =
                AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SUCCESSFUL;
        int stopReason = UNAVAILABLE_STOP_REASON;

        mLogger.logJobStatsHelper(
                JOB_ID_1, executionDurationInMs, executionFrequencyInMs, resultCode, stopReason);

        verify(mMockStatsdLogger).logExecutionReportedStats(captor.capture());
        expect.that(captor.getValue())
                .isEqualTo(
                        ExecutionReportedStats.builder()
                                .setJobId(JOB_ID_1)
                                .setExecutionLatencyMs(Integer.MAX_VALUE)
                                .setExecutionPeriodMinute(Integer.MAX_VALUE)
                                .setExecutionResultCode(resultCode)
                                .setStopReason(stopReason)
                                .setModuleName(AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__MODULE_NAME__UNKNOWN_MODULE_NAME)
                                .build());
    }

    @Test
    public void testLogJobStatsHelper_smallNegativePeriod() {
        // Mock to avoid sampling logging
        doReturn(true).when(mLogger).shouldLog();

        ArgumentCaptor<ExecutionReportedStats> captor =
                ArgumentCaptor.forClass(ExecutionReportedStats.class);
        long executionLatencyMs = 100L;
        long executionPeriodMs = UNAVAILABLE_JOB_EXECUTION_PERIOD;
        int resultCode =
                AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SUCCESSFUL;
        int stopReason = UNAVAILABLE_STOP_REASON;

        mLogger.logJobStatsHelper(
                JOB_ID_1, executionLatencyMs, executionPeriodMs, resultCode, stopReason);

        verify(mMockStatsdLogger).logExecutionReportedStats(captor.capture());
        expect.that(captor.getValue())
                .isEqualTo(
                        ExecutionReportedStats.builder()
                                .setJobId(JOB_ID_1)
                                .setExecutionLatencyMs((int) executionLatencyMs)
                                .setExecutionPeriodMinute((int) executionPeriodMs)
                                .setExecutionResultCode(resultCode)
                                .setStopReason(stopReason)
                                .setModuleName(
                                        AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__MODULE_NAME__UNKNOWN_MODULE_NAME)
                                .build());
    }

    @Test
    public void testShouldLog() {
        when(mMockFlags.getBackgroundJobSamplingLoggingRate()).thenReturn(0);
        expect.that(mLogger.shouldLog()).isFalse();

        when(mMockFlags.getBackgroundJobSamplingLoggingRate()).thenReturn(100);
        expect.that(mLogger.shouldLog()).isTrue();
    }

    @Test
    public void testConvertLongToInteger() {
        expect.that(JobServiceLogger.convertLongToInteger((long) Integer.MIN_VALUE - 1))
                .isEqualTo(Integer.MIN_VALUE);
        expect.that(JobServiceLogger.convertLongToInteger((long) Integer.MAX_VALUE + 1))
                .isEqualTo(Integer.MAX_VALUE);
        expect.that(JobServiceLogger.convertLongToInteger(1000L)).isEqualTo(1000);
    }
}
