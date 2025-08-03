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
import static com.android.adservices.service.stats.UpdateCustomAudienceExecutionLogger.MISSING_START_UPDATE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.UpdateCustomAudienceExecutionLogger.REPEATED_END_UPDATE_CUSTOM_AUDIENCE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.shared.util.Clock;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class UpdateCustomAudienceExecutionLoggerTest {
    public static final long UPDATE_CUSTOM_AUDIENCE_START_TIMESTAMP = START_ELAPSED_TIMESTAMP + 1;
    private static final int ADS_DATA_SIZE_IN_BYTES = 10;
    private static final int NUM_OF_ADS = 4;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    @Mock private Clock mClockMock;

    @Captor
    ArgumentCaptor<UpdateCustomAudienceProcessReportedStats>
            mUpdateCustomAudienceProcessReportedStatsArgumentCaptor;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testUpdateCustomAudienceExecutionLogger_Success() {
        when(mClockMock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        UPDATE_CUSTOM_AUDIENCE_START_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        UpdateCustomAudienceExecutionLogger updateCustomAudienceExecutionLogger =
                new UpdateCustomAudienceExecutionLogger(mClockMock, mAdServicesLoggerMock);
        updateCustomAudienceExecutionLogger.start();
        updateCustomAudienceExecutionLogger.close(
                ADS_DATA_SIZE_IN_BYTES, NUM_OF_ADS, STATUS_SUCCESS);

        verify(mAdServicesLoggerMock)
                .logUpdateCustomAudienceProcessReportedStats(
                        mUpdateCustomAudienceProcessReportedStatsArgumentCaptor.capture());
        UpdateCustomAudienceProcessReportedStats updateCustomAudienceProcessReportedStats =
                mUpdateCustomAudienceProcessReportedStatsArgumentCaptor.getValue();
        assertThat(updateCustomAudienceProcessReportedStats.getLatencyInMills())
                .isEqualTo((int) (STOP_ELAPSED_TIMESTAMP - UPDATE_CUSTOM_AUDIENCE_START_TIMESTAMP));
        assertThat(updateCustomAudienceProcessReportedStats.getDataSizeOfAdsInBytes())
                .isEqualTo(ADS_DATA_SIZE_IN_BYTES);
        assertThat(updateCustomAudienceProcessReportedStats.getNumOfAds()).isEqualTo(NUM_OF_ADS);
    }

    @Test
    public void testUpdateCustomAudienceExecutionLogger_missingStart() {
        when(mClockMock.elapsedRealtime()).thenReturn(START_ELAPSED_TIMESTAMP);
        UpdateCustomAudienceExecutionLogger updateCustomAudienceExecutionLogger =
                new UpdateCustomAudienceExecutionLogger(mClockMock, mAdServicesLoggerMock);
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                updateCustomAudienceExecutionLogger.close(
                                        ADS_DATA_SIZE_IN_BYTES, NUM_OF_ADS, STATUS_INTERNAL_ERROR));
        assertThat(throwable).hasMessageThat().contains(MISSING_START_UPDATE_CUSTOM_AUDIENCE);
    }

    @Test
    public void testUpdateCustomAudienceExecutionLogger_RepeatedClose() {
        when(mClockMock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        UPDATE_CUSTOM_AUDIENCE_START_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        UpdateCustomAudienceExecutionLogger updateCustomAudienceExecutionLogger =
                new UpdateCustomAudienceExecutionLogger(mClockMock, mAdServicesLoggerMock);
        updateCustomAudienceExecutionLogger.start();
        updateCustomAudienceExecutionLogger.close(
                ADS_DATA_SIZE_IN_BYTES, NUM_OF_ADS, STATUS_SUCCESS);
        Throwable throwable =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                updateCustomAudienceExecutionLogger.close(
                                        ADS_DATA_SIZE_IN_BYTES, NUM_OF_ADS, STATUS_SUCCESS));
        assertThat(throwable).hasMessageThat().contains(REPEATED_END_UPDATE_CUSTOM_AUDIENCE);
    }
}
