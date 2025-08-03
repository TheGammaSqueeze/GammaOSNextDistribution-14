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

package com.android.adservices.service.adselection;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.http.MockWebServerRule;
import android.net.Uri;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.data.adselection.DBAdSelectionDebugReport;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.Returns;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class DebugReportSenderWorkerTest {
    private static final int MAX_BATCH_SIZE_FOR_DEBUG_REPORTS = 4;
    private static final int EXPECTED_GET_DEBUG_REPORTS_DAO_CALL_PER_INVOCATION = 1;
    private static final int EXPECTED_DELETE_DEBUG_REPORTS_DAO_CALL_PER_INVOCATION = 1;
    private final Flags mFlags = new DebugReportSenderWorkerTestFlags();
    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Mock private AdSelectionDebugReportDao mAdSelectionDebugReportDao;
    @Mock private AdServicesHttpsClient mHttpsClient;
    private Clock mClockMock;
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    private DebugReportSenderWorker mDebugReportSenderWorker;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mClockMock = Clock.systemUTC();
        mDebugReportSenderWorker =
                new DebugReportSenderWorker(
                        mAdSelectionDebugReportDao, mHttpsClient, mFlags, mClockMock);
    }

    @Test
    public void testNullParamsInConstructor() {
        assertThrows(
                NullPointerException.class,
                () -> new DebugReportSenderWorker(null, mHttpsClient, mFlags, mClockMock));
        assertThrows(
                NullPointerException.class,
                () ->
                        new DebugReportSenderWorker(
                                mAdSelectionDebugReportDao, null, mFlags, mClockMock));
        assertThrows(
                NullPointerException.class,
                () ->
                        new DebugReportSenderWorker(
                                mAdSelectionDebugReportDao, mHttpsClient, null, mClockMock));
        assertThrows(
                NullPointerException.class,
                () ->
                        new DebugReportSenderWorker(
                                mAdSelectionDebugReportDao, mHttpsClient, mFlags, null));
    }

    @Test
    public void testRunDebugReportSenderSuccess() throws ExecutionException, InterruptedException {
        int debugReportCount = 20;
        doReturn(Futures.immediateVoidFuture())
                .when(mHttpsClient)
                .getAndReadNothing(any(Uri.class), any(DevContext.class));
        when(mAdSelectionDebugReportDao.getDebugReportsBeforeTime(any(Instant.class), anyInt()))
                .thenReturn(createTestAdSelectionDebugReports(debugReportCount, mClockMock));
        doNothing()
                .when(mAdSelectionDebugReportDao)
                .deleteDebugReportsBeforeTime(any(Instant.class));

        mDebugReportSenderWorker.runDebugReportSender().get();

        verify(
                        mAdSelectionDebugReportDao,
                        times(EXPECTED_GET_DEBUG_REPORTS_DAO_CALL_PER_INVOCATION))
                .getDebugReportsBeforeTime(any(Instant.class), anyInt());
        verify(mHttpsClient, times(debugReportCount))
                .getAndReadNothing(any(Uri.class), any(DevContext.class));
        verify(
                        mAdSelectionDebugReportDao,
                        times(EXPECTED_DELETE_DEBUG_REPORTS_DAO_CALL_PER_INVOCATION))
                .deleteDebugReportsBeforeTime(any(Instant.class));
    }

    @Test
    public void testRunDebugReportSenderPartialFailureDoesNotBreakOverallSuccess()
            throws ExecutionException, InterruptedException {
        int debugReportCount = 2;
        when(mHttpsClient.getAndReadNothing(any(Uri.class), any(DevContext.class)))
                .thenReturn(Futures.immediateVoidFuture())
                .thenReturn(Futures.immediateFailedFuture(new IllegalStateException()));
        when(mAdSelectionDebugReportDao.getDebugReportsBeforeTime(any(Instant.class), anyInt()))
                .thenReturn(createTestAdSelectionDebugReports(debugReportCount, mClockMock));
        doNothing()
                .when(mAdSelectionDebugReportDao)
                .deleteDebugReportsBeforeTime(any(Instant.class));

        mDebugReportSenderWorker.runDebugReportSender().get();

        verify(
                        mAdSelectionDebugReportDao,
                        times(EXPECTED_GET_DEBUG_REPORTS_DAO_CALL_PER_INVOCATION))
                .getDebugReportsBeforeTime(any(Instant.class), anyInt());
        verify(mHttpsClient, times(debugReportCount))
                .getAndReadNothing(any(Uri.class), any(DevContext.class));
        verify(
                        mAdSelectionDebugReportDao,
                        times(EXPECTED_DELETE_DEBUG_REPORTS_DAO_CALL_PER_INVOCATION))
                .deleteDebugReportsBeforeTime(any(Instant.class));
    }

    @Test
    public void testRunDebugReportSenderDoesNotFailsOnException()
            throws ExecutionException, InterruptedException {

        int debugReportCounts = 2;
        when(mHttpsClient.getAndReadNothing(any(Uri.class), any(DevContext.class)))
                .thenThrow(new IllegalStateException());
        when(mAdSelectionDebugReportDao.getDebugReportsBeforeTime(any(Instant.class), anyInt()))
                .thenReturn(createTestAdSelectionDebugReports(debugReportCounts, mClockMock));
        doNothing()
                .when(mAdSelectionDebugReportDao)
                .deleteDebugReportsBeforeTime(any(Instant.class));

        mDebugReportSenderWorker.runDebugReportSender().get();

        verify(
                        mAdSelectionDebugReportDao,
                        times(EXPECTED_GET_DEBUG_REPORTS_DAO_CALL_PER_INVOCATION))
                .getDebugReportsBeforeTime(any(Instant.class), anyInt());
        verify(mHttpsClient, times(debugReportCounts))
                .getAndReadNothing(any(Uri.class), any(DevContext.class));
        verify(
                        mAdSelectionDebugReportDao,
                        times(EXPECTED_DELETE_DEBUG_REPORTS_DAO_CALL_PER_INVOCATION))
                .deleteDebugReportsBeforeTime(any(Instant.class));
    }

    @Test
    public void testRunDebugReportSenderDoesNothingIfNoDebugReportsToSend()
            throws ExecutionException, InterruptedException {
        doReturn(Futures.immediateVoidFuture())
                .when(mHttpsClient)
                .getAndReadNothing(any(Uri.class), any(DevContext.class));
        when(mAdSelectionDebugReportDao.getDebugReportsBeforeTime(any(Instant.class), anyInt()))
                .thenReturn(Collections.emptyList());
        doNothing()
                .when(mAdSelectionDebugReportDao)
                .deleteDebugReportsBeforeTime(any(Instant.class));

        mDebugReportSenderWorker.runDebugReportSender().get();

        verify(
                        mAdSelectionDebugReportDao,
                        times(EXPECTED_GET_DEBUG_REPORTS_DAO_CALL_PER_INVOCATION))
                .getDebugReportsBeforeTime(any(Instant.class), anyInt());
        verify(mHttpsClient, never()).getAndReadNothing(any(Uri.class), any(DevContext.class));
        verify(
                        mAdSelectionDebugReportDao,
                        times(EXPECTED_DELETE_DEBUG_REPORTS_DAO_CALL_PER_INVOCATION))
                .deleteDebugReportsBeforeTime(any(Instant.class));
    }

    @Test
    public void testRunDebugReportSenderThrowsTimeout() {
        class FlagsWithSmallTimeout implements Flags {
            @Override
            public long getFledgeDebugReportSenderJobMaxRuntimeMs() {
                return 100L;
            }
        }
        when(mHttpsClient.getAndReadNothing(any(Uri.class), any(DevContext.class)))
                .thenAnswer(new AnswersWithDelay(500L, new Returns(Futures.immediateVoidFuture())));
        when(mAdSelectionDebugReportDao.getDebugReportsBeforeTime(any(Instant.class), anyInt()))
                .thenReturn(
                        createTestAdSelectionDebugReports(
                                MAX_BATCH_SIZE_FOR_DEBUG_REPORTS, mClockMock));
        doNothing()
                .when(mAdSelectionDebugReportDao)
                .deleteDebugReportsBeforeTime(any(Instant.class));

        mDebugReportSenderWorker =
                new DebugReportSenderWorker(
                        mAdSelectionDebugReportDao,
                        mHttpsClient,
                        new FlagsWithSmallTimeout(),
                        mClockMock);
        ExecutionException expected =
                assertThrows(
                        ExecutionException.class,
                        () -> mDebugReportSenderWorker.runDebugReportSender().get());
        assertThat(expected.getCause()).isInstanceOf(TimeoutException.class);
        verify(
                        mAdSelectionDebugReportDao,
                        times(EXPECTED_GET_DEBUG_REPORTS_DAO_CALL_PER_INVOCATION))
                .getDebugReportsBeforeTime(any(Instant.class), anyInt());
        verify(mAdSelectionDebugReportDao, never())
                .deleteDebugReportsBeforeTime(any(Instant.class));
    }

    private static List<DBAdSelectionDebugReport> createTestAdSelectionDebugReports(
            int count, Clock clock) {
        List<DBAdSelectionDebugReport> debugReports = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DBAdSelectionDebugReport adSelectionDebugReport =
                    DBAdSelectionDebugReport.create(
                            null,
                            Uri.parse("https://example.com/" + i),
                            false,
                            clock.instant().toEpochMilli());
            debugReports.add(adSelectionDebugReport);
        }
        return debugReports;
    }

    private static class DebugReportSenderWorkerTestFlags implements Flags {
        DebugReportSenderWorkerTestFlags() {}

        @Override
        public int getFledgeEventLevelDebugReportingMaxItemsPerBatch() {
            return MAX_BATCH_SIZE_FOR_DEBUG_REPORTS;
        }

        @Override
        public int getFledgeDebugReportSenderJobNetworkConnectionTimeoutMs() {
            return FLEDGE_DEBUG_REPORT_SENDER_JOB_NETWORK_CONNECT_TIMEOUT_MS;
        }

        @Override
        public int getFledgeDebugReportSenderJobNetworkReadTimeoutMs() {
            return FLEDGE_DEBUG_REPORT_SENDER_JOB_NETWORK_READ_TIMEOUT_MS;
        }

        @Override
        public long getFledgeDebugReportSenderJobMaxRuntimeMs() {
            return FLEDGE_DEBUG_REPORT_SENDER_JOB_MAX_RUNTIME_MS;
        }
    }
}
