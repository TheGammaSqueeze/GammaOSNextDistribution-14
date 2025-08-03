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

package com.android.adservices.service.stats;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.START_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.STOP_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.BackgroundFetchExecutionLogger.MISSING_START_TIMESTAMP;
import static com.android.adservices.service.stats.BackgroundFetchExecutionLogger.REPEATED_END_TIMESTAMP;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.shared.util.Clock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class BackgroundFetchExecutionLoggerTest {
    private static final int NUM_OF_ELIGIBLE_TO_UPDATE_CAS = 50;
    public static final long BACKGROUND_FETCH_START_TIMESTAMP = START_ELAPSED_TIMESTAMP + 1;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    @Mock private Clock mClockMock;

    @Captor
    ArgumentCaptor<BackgroundFetchProcessReportedStats>
            mBackgroundFetchProcessReportedStatsArgumentCaptor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testBackgroundFetchExecutionLogger_SuccessBackgroundFetch() {
        when(mClockMock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BACKGROUND_FETCH_START_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);

        BackgroundFetchExecutionLogger backgroundFetchExecutionLogger =
                new BackgroundFetchExecutionLogger(mClockMock, mAdServicesLoggerMock);
        backgroundFetchExecutionLogger.start();
        backgroundFetchExecutionLogger.close(NUM_OF_ELIGIBLE_TO_UPDATE_CAS, STATUS_SUCCESS);

        verify(mAdServicesLoggerMock)
                .logBackgroundFetchProcessReportedStats(
                        mBackgroundFetchProcessReportedStatsArgumentCaptor.capture());
        BackgroundFetchProcessReportedStats backgroundFetchProcessReportedStats =
                mBackgroundFetchProcessReportedStatsArgumentCaptor.getValue();

        assertThat(backgroundFetchProcessReportedStats.getNumOfEligibleToUpdateCas())
                .isEqualTo(NUM_OF_ELIGIBLE_TO_UPDATE_CAS);
        assertThat(backgroundFetchProcessReportedStats.getLatencyInMillis())
                .isEqualTo((int) (STOP_ELAPSED_TIMESTAMP - BACKGROUND_FETCH_START_TIMESTAMP));
        assertThat(backgroundFetchProcessReportedStats.getResultCode()).isEqualTo(STATUS_SUCCESS);
    }

    @Test
    public void testBackgroundFetchExecutionLogger_missingStartBackgroundFetch() {
        when(mClockMock.elapsedRealtime()).thenReturn(START_ELAPSED_TIMESTAMP);
        BackgroundFetchExecutionLogger backgroundFetchExecutionLogger =
                new BackgroundFetchExecutionLogger(mClockMock, mAdServicesLoggerMock);
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                backgroundFetchExecutionLogger.close(
                                        NUM_OF_ELIGIBLE_TO_UPDATE_CAS, STATUS_INTERNAL_ERROR));
        assertThat(throwable).hasMessageThat().contains(MISSING_START_TIMESTAMP);
    }

    @Test
    public void testBackgroundFetchExecutionLogger_repeatedEndBackgroundFetch() {
        when(mClockMock.elapsedRealtime())
                .thenReturn(BACKGROUND_FETCH_START_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
        BackgroundFetchExecutionLogger backgroundFetchExecutionLogger =
                new BackgroundFetchExecutionLogger(mClockMock, mAdServicesLoggerMock);
        backgroundFetchExecutionLogger.start();
        backgroundFetchExecutionLogger.close(NUM_OF_ELIGIBLE_TO_UPDATE_CAS, STATUS_INTERNAL_ERROR);
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                backgroundFetchExecutionLogger.close(
                                        NUM_OF_ELIGIBLE_TO_UPDATE_CAS, STATUS_INTERNAL_ERROR));
        assertThat(throwable).hasMessageThat().contains(REPEATED_END_TIMESTAMP);
    }
}
