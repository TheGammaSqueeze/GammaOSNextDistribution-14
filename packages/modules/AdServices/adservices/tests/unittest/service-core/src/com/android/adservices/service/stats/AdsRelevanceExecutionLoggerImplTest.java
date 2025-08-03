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

package com.android.adservices.service.stats;

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNSET;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT;
import static com.android.adservices.service.stats.AdsRelevanceExecutionLoggerFactory.GET_AD_SELECTION_DATA_API_NAME;
import static com.android.adservices.service.stats.AdsRelevanceExecutionLoggerFactory.PERSIST_AD_SELECTION_RESULT_API_NAME;
import static com.android.adservices.service.stats.AdsRelevanceExecutionLoggerImpl.UNAVAILABLE_LATENCY;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.CallerMetadata;

import com.android.adservices.shared.util.Clock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AdsRelevanceExecutionLoggerImplTest {
    public static final int GET_AD_SELECTION_DATA_LATENCY_MS = 1;
    public static final int PERSIST_AD_SELECTION_RESULT_LATENCY_MS = 1;

    public static final long BINDER_ELAPSED_TIMESTAMP = 90L;

    public static final CallerMetadata sCallerMetadata =
            new CallerMetadata.Builder()
                    .setBinderElapsedTimestamp(BINDER_ELAPSED_TIMESTAMP)
                    .build();
    private static final int BINDER_LATENCY_MS = 2;

    public static final long GET_AD_SELECTION_DATA_START_TIMESTAMP =
            BINDER_ELAPSED_TIMESTAMP + (long) BINDER_LATENCY_MS / 2;
    public static final long GET_AD_SELECTION_DATA_END_TIMESTAMP =
            GET_AD_SELECTION_DATA_START_TIMESTAMP + GET_AD_SELECTION_DATA_LATENCY_MS;
    public static final int GET_AD_SELECTION_DATA_INTERNAL_FINAL_LATENCY_MS =
            (int) (GET_AD_SELECTION_DATA_END_TIMESTAMP - GET_AD_SELECTION_DATA_START_TIMESTAMP);
    public static final int GET_AD_SELECTION_DATA_OVERALL_LATENCY_MS =
            BINDER_LATENCY_MS + GET_AD_SELECTION_DATA_INTERNAL_FINAL_LATENCY_MS;

    public static final long PERSIST_AD_SELECTION_RESULT_START_TIMESTAMP =
            BINDER_ELAPSED_TIMESTAMP + (long) BINDER_LATENCY_MS / 2;
    public static final long PERSIST_AD_SELECTION_RESULT_END_TIMESTAMP =
            PERSIST_AD_SELECTION_RESULT_START_TIMESTAMP + PERSIST_AD_SELECTION_RESULT_LATENCY_MS;
    public static final int PERSIST_AD_SELECTION_RESULT_INTERNAL_FINAL_LATENCY_MS =
            (int) (PERSIST_AD_SELECTION_RESULT_END_TIMESTAMP
                    - PERSIST_AD_SELECTION_RESULT_START_TIMESTAMP);
    public static final int PERSIST_AD_SELECTION_RESULT_OVERALL_LATENCY_MS =
            BINDER_LATENCY_MS + PERSIST_AD_SELECTION_RESULT_INTERNAL_FINAL_LATENCY_MS;

    @Mock private Clock mMockClockMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testFledgeAuctionServerExecutionLogger_successGetAdSelectionData() {
        ArgumentCaptor<ApiCallStats> argumentCaptor = ArgumentCaptor.forClass(ApiCallStats.class);

        when(mMockClockMock.elapsedRealtime())
                .thenReturn(
                        BINDER_ELAPSED_TIMESTAMP,
                        GET_AD_SELECTION_DATA_START_TIMESTAMP,
                        GET_AD_SELECTION_DATA_END_TIMESTAMP);

        int resultCode = STATUS_SUCCESS;
        // Start the Ad selection execution logger and set start state of the process.
        AdsRelevanceExecutionLoggerImpl getAdSelectionDataLogger =
                new AdsRelevanceExecutionLoggerImpl(
                        TEST_PACKAGE_NAME,
                        sCallerMetadata,
                        mMockClockMock,
                        mAdServicesLoggerMock,
                        GET_AD_SELECTION_DATA_API_NAME,
                        AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA);
        getAdSelectionDataLogger.endAdsRelevanceApi(resultCode);

        // Verify the logging of the auction server APIs.
        verify(mAdServicesLoggerMock).logApiCallStats(argumentCaptor.capture());

        ApiCallStats getAdSelectionDataStats = argumentCaptor.getValue();
        assertThat(getAdSelectionDataStats.getApiName())
                .isEqualTo(AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA);
        assertThat(getAdSelectionDataStats.getResultCode()).isEqualTo(resultCode);
        assertThat(getAdSelectionDataStats.getLatencyMillisecond())
                .isEqualTo(GET_AD_SELECTION_DATA_OVERALL_LATENCY_MS);
    }

    @Test
    public void testFledgeAuctionServerExecutionLogger_successPersistAdSelectionResult() {
        ArgumentCaptor<ApiCallStats> argumentCaptor = ArgumentCaptor.forClass(ApiCallStats.class);

        when(mMockClockMock.elapsedRealtime())
                .thenReturn(
                        BINDER_ELAPSED_TIMESTAMP,
                        PERSIST_AD_SELECTION_RESULT_START_TIMESTAMP,
                        PERSIST_AD_SELECTION_RESULT_END_TIMESTAMP);

        int resultCode = STATUS_SUCCESS;
        // Start the Ad selection execution logger and set start state of the process.
        AdsRelevanceExecutionLoggerImpl persistAdSelectionResultLogger =
                new AdsRelevanceExecutionLoggerImpl(
                        TEST_PACKAGE_NAME,
                        sCallerMetadata,
                        mMockClockMock,
                        mAdServicesLoggerMock,
                        PERSIST_AD_SELECTION_RESULT_API_NAME,
                        AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT);
        persistAdSelectionResultLogger.endAdsRelevanceApi(resultCode);

        // Verify the logging of the auction server APIs.
        verify(mAdServicesLoggerMock).logApiCallStats(argumentCaptor.capture());

        ApiCallStats persistAdSelectionResultStats = argumentCaptor.getValue();
        assertThat(persistAdSelectionResultStats.getApiName())
                .isEqualTo(AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT);
        assertThat(persistAdSelectionResultStats.getResultCode()).isEqualTo(resultCode);
        assertThat(persistAdSelectionResultStats.getLatencyMillisecond())
                .isEqualTo(PERSIST_AD_SELECTION_RESULT_OVERALL_LATENCY_MS);
    }

    @Test
    public void testFledgeAuctionServerExecutionLogger_missingStartOfGetAdSelectionData() {
        ArgumentCaptor<ApiCallStats> argumentCaptor = ArgumentCaptor.forClass(ApiCallStats.class);
        when(mMockClockMock.elapsedRealtime()).thenReturn(BINDER_ELAPSED_TIMESTAMP);
        AdsRelevanceExecutionLoggerImpl fledgeAuctionServerExecutionLoggerImpl =
                new AdsRelevanceExecutionLoggerImpl(
                        TEST_PACKAGE_NAME,
                        sCallerMetadata,
                        mMockClockMock,
                        mAdServicesLoggerMock,
                        GET_AD_SELECTION_DATA_API_NAME,
                        AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA);

        // Set a 0 timestamp for auctionServerApiStartTimestamp to mock
        // missing start of get-ad-selection-data process
        fledgeAuctionServerExecutionLoggerImpl.setAdsRelevanceApiStartTimestamp(0L);
        fledgeAuctionServerExecutionLoggerImpl.endAdsRelevanceApi(STATUS_SUCCESS);

        // Verify the logging of the auction server APIs.
        verify(mAdServicesLoggerMock).logApiCallStats(argumentCaptor.capture());

        ApiCallStats getAdSelectionDataStats = argumentCaptor.getValue();
        assertThat(getAdSelectionDataStats.getApiName())
                .isEqualTo(AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA);
        assertThat(getAdSelectionDataStats.getResultCode()).isEqualTo(STATUS_UNSET);
        assertThat(getAdSelectionDataStats.getLatencyMillisecond())
                .isEqualTo(UNAVAILABLE_LATENCY);
    }

    @Test
    public void testFledgeAuctionServerExecutionLogger_redundantEndOfGetAdSelectionData() {
        ArgumentCaptor<ApiCallStats> argumentCaptor = ArgumentCaptor.forClass(ApiCallStats.class);
        when(mMockClockMock.elapsedRealtime())
                .thenReturn(
                        BINDER_ELAPSED_TIMESTAMP,
                        GET_AD_SELECTION_DATA_START_TIMESTAMP,
                        GET_AD_SELECTION_DATA_END_TIMESTAMP);
        AdsRelevanceExecutionLoggerImpl fledgeAuctionServerExecutionLoggerImpl =
                new AdsRelevanceExecutionLoggerImpl(
                        TEST_PACKAGE_NAME,
                        sCallerMetadata,
                        mMockClockMock,
                        mAdServicesLoggerMock,
                        GET_AD_SELECTION_DATA_API_NAME,
                        AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA);

        // Set a positive timestamp for auctionServerApiEndTimestamp to mock
        // redundant end of get-ad-selection-data process
        fledgeAuctionServerExecutionLoggerImpl.setAdsRelevanceApiEndTimestamp(1L);
        fledgeAuctionServerExecutionLoggerImpl.endAdsRelevanceApi(STATUS_SUCCESS);

        // Verify the logging of the auction server APIs.
        verify(mAdServicesLoggerMock).logApiCallStats(argumentCaptor.capture());

        ApiCallStats getAdSelectionDataStats = argumentCaptor.getValue();
        assertThat(getAdSelectionDataStats.getApiName())
                .isEqualTo(AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA);
        assertThat(getAdSelectionDataStats.getResultCode()).isEqualTo(STATUS_UNSET);
        assertThat(getAdSelectionDataStats.getLatencyMillisecond())
                .isEqualTo(UNAVAILABLE_LATENCY);
    }

    @Test
    public void testFledgeAuctionServerExecutionLogger_missingStartOfPersistAdSelectionResult() {
        ArgumentCaptor<ApiCallStats> argumentCaptor = ArgumentCaptor.forClass(ApiCallStats.class);
        when(mMockClockMock.elapsedRealtime()).thenReturn(BINDER_ELAPSED_TIMESTAMP);
        AdsRelevanceExecutionLoggerImpl fledgeAuctionServerExecutionLoggerImpl =
                new AdsRelevanceExecutionLoggerImpl(
                        TEST_PACKAGE_NAME,
                        sCallerMetadata,
                        mMockClockMock,
                        mAdServicesLoggerMock,
                        PERSIST_AD_SELECTION_RESULT_API_NAME,
                        AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT);

        // Set a 0 timestamp for auctionServerApiStartTimestamp to mock
        // missing start of persist-ad-selection-result process
        fledgeAuctionServerExecutionLoggerImpl.setAdsRelevanceApiStartTimestamp(0L);
        fledgeAuctionServerExecutionLoggerImpl.endAdsRelevanceApi(STATUS_SUCCESS);

        // Verify the logging of the auction server APIs.
        verify(mAdServicesLoggerMock).logApiCallStats(argumentCaptor.capture());

        ApiCallStats persistAdSelectionResultStats = argumentCaptor.getValue();
        assertThat(persistAdSelectionResultStats.getApiName())
                .isEqualTo(AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT);
        assertThat(persistAdSelectionResultStats.getResultCode()).isEqualTo(STATUS_UNSET);
        assertThat(persistAdSelectionResultStats.getLatencyMillisecond())
                .isEqualTo(UNAVAILABLE_LATENCY);
    }

    @Test
    public void testFledgeAuctionServerExecutionLogger_redundantEndOfPersistAdSelectionResult() {
        ArgumentCaptor<ApiCallStats> argumentCaptor = ArgumentCaptor.forClass(ApiCallStats.class);
        when(mMockClockMock.elapsedRealtime())
                .thenReturn(
                        BINDER_ELAPSED_TIMESTAMP,
                        PERSIST_AD_SELECTION_RESULT_START_TIMESTAMP,
                        PERSIST_AD_SELECTION_RESULT_END_TIMESTAMP);
        AdsRelevanceExecutionLoggerImpl fledgeAuctionServerExecutionLoggerImpl =
                new AdsRelevanceExecutionLoggerImpl(
                        TEST_PACKAGE_NAME,
                        sCallerMetadata,
                        mMockClockMock,
                        mAdServicesLoggerMock,
                        PERSIST_AD_SELECTION_RESULT_API_NAME,
                        AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT);

        // Set a positive timestamp for auctionServerApiEndTimestamp to mock
        // redundant end of persist-ad-selection-result process
        fledgeAuctionServerExecutionLoggerImpl.setAdsRelevanceApiEndTimestamp(1L);
        fledgeAuctionServerExecutionLoggerImpl.endAdsRelevanceApi(STATUS_SUCCESS);

        // Verify the logging of the auction server APIs.
        verify(mAdServicesLoggerMock).logApiCallStats(argumentCaptor.capture());

        ApiCallStats persistAdSelectionResultStats = argumentCaptor.getValue();
        assertThat(persistAdSelectionResultStats.getApiName())
                .isEqualTo(AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT);
        assertThat(persistAdSelectionResultStats.getResultCode()).isEqualTo(STATUS_UNSET);
        assertThat(persistAdSelectionResultStats.getLatencyMillisecond())
                .isEqualTo(UNAVAILABLE_LATENCY);
    }
}
