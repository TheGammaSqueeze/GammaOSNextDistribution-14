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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.AdServicesStatusUtils;
import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.WebUtil;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.data.measurement.ITransaction;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.exception.CryptoException;
import com.android.adservices.service.measurement.KeyValueData;
import com.android.adservices.service.measurement.aggregation.AggregateCryptoFixture;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKeyManager;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.aggregation.AggregateReportFixture;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.MeasurementReportsStats;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Unit test for {@link AggregateReportingJobHandler} */
@RunWith(MockitoJUnitRunner.class)
public class AggregateReportingJobHandlerTest {
    private static final Uri REPORTING_URI = WebUtil.validUri("https://subdomain.example.test");
    private static final Uri COORDINATOR_ORIGIN =
            WebUtil.validUri("https://coordinator.example.test");
    private static final String ENROLLMENT_ID = "enrollment-id";

    private static final String CLEARTEXT_PAYLOAD =
            "{\"operation\":\"histogram\",\"data\":[{\"bucket\":\"1\",\"value\":2}]}";

    private static final UnsignedLong SOURCE_DEBUG_KEY = new UnsignedLong(237865L);
    private static final UnsignedLong TRIGGER_DEBUG_KEY = new UnsignedLong(928762L);

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    DatastoreManager mDatastoreManager;

    @Mock IMeasurementDao mMeasurementDao;

    @Mock ITransaction mTransaction;

    @Mock Flags mMockFlags;
    @Mock AdServicesLogger mLogger;
    @Mock AdServicesErrorLogger mErrorLogger;

    AggregateReportingJobHandler mAggregateReportingJobHandler;
    AggregateReportingJobHandler mSpyAggregateReportingJobHandler;
    AggregateReportingJobHandler mSpyDebugAggregateReportingJobHandler;

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(FlagsFactory.class)
                    .spyStatic(ErrorLogUtil.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    class FakeDatasoreManager extends DatastoreManager {

        FakeDatasoreManager() {
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
    public void setUp() {
        AggregateEncryptionKeyManager mockKeyManager = mock(AggregateEncryptionKeyManager.class);
        ArgumentCaptor<Integer> captorNumberOfKeys = ArgumentCaptor.forClass(Integer.class);
        when(mockKeyManager.getAggregateEncryptionKeys(any(), captorNumberOfKeys.capture()))
                .thenAnswer(
                        invocation -> {
                            List<AggregateEncryptionKey> keys = new ArrayList<>();
                            for (int i = 0; i < captorNumberOfKeys.getValue(); i++) {
                                keys.add(AggregateCryptoFixture.getKey());
                            }
                            return keys;
                        });
        mDatastoreManager = new FakeDatasoreManager();
        mAggregateReportingJobHandler =
                new AggregateReportingJobHandler(
                        mDatastoreManager,
                        mockKeyManager,
                        mMockFlags,
                        mLogger,
                        ReportingStatus.ReportType.AGGREGATE,
                        ReportingStatus.UploadMethod.UNKNOWN,
                        sContext);
        mSpyAggregateReportingJobHandler = Mockito.spy(mAggregateReportingJobHandler);
        mSpyDebugAggregateReportingJobHandler =
                Mockito.spy(
                        new AggregateReportingJobHandler(
                                        mDatastoreManager,
                                        mockKeyManager,
                                        mMockFlags,
                                        mLogger,
                                        ReportingStatus.ReportType.AGGREGATE,
                                        ReportingStatus.UploadMethod.UNKNOWN,
                                        sContext)
                                .setIsDebugInstance(true));

        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        when(mMockFlags.getMeasurementAggregationCoordinatorOriginEnabled()).thenReturn(true);
        when(mMockFlags.getMeasurementEnableAppPackageNameLogging()).thenReturn(true);
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
    }

    @Test
    public void testSendReportForPendingReportSuccess()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId("aggregateReportId")
                        .setStatus(AggregateReport.Status.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .build();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);
        assertEquals(
                aggregateReportBody.getString("aggregation_coordinator_origin"),
                COORDINATOR_ORIGIN.toString());

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), Mockito.any());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(Mockito.any(), eq(REPORTING_URI), Mockito.any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.DELIVERED);
        Assert.assertEquals(
                AdServicesStatusUtils.STATUS_SUCCESS,
                mSpyAggregateReportingJobHandler.performReport(
                        aggregateReport.getId(),
                        AggregateCryptoFixture.getKey(),
                        new ReportingStatus()));

        verify(mMeasurementDao, times(1)).markAggregateReportStatus(any(), anyInt());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendReportForPendingReportSuccess_originFlagDisabled()
            throws DatastoreException, IOException, JSONException {
        when(mMockFlags.getMeasurementAggregationCoordinatorOriginEnabled()).thenReturn(false);
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId("aggregateReportId")
                        .setStatus(AggregateReport.Status.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .build();

        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);
        // No Aggregation coordinator
        assertTrue(aggregateReportBody.isNull("aggregation_coordinator_origin"));

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), Mockito.any());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(Mockito.any(), eq(REPORTING_URI), Mockito.any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.DELIVERED);
        Assert.assertEquals(
                AdServicesStatusUtils.STATUS_SUCCESS,
                mSpyAggregateReportingJobHandler.performReport(
                        aggregateReport.getId(),
                        AggregateCryptoFixture.getKey(),
                        new ReportingStatus()));

        verify(mMeasurementDao, times(1)).markAggregateReportStatus(any(), anyInt());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendReportForPendingDebugReportSuccess()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId("aggregateReportId")
                        .setStatus(AggregateReport.Status.PENDING)
                        .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .build();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyDebugAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), Mockito.any());
        doReturn(aggregateReportBody)
                .when(mSpyDebugAggregateReportingJobHandler)
                .createReportJsonPayload(Mockito.any(), eq(REPORTING_URI), Mockito.any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateDebugReportDelivered(aggregateReport.getId());
        Assert.assertEquals(
                AdServicesStatusUtils.STATUS_SUCCESS,
                mSpyDebugAggregateReportingJobHandler.performReport(
                        aggregateReport.getId(),
                        AggregateCryptoFixture.getKey(),
                        new ReportingStatus()));

        verify(mMeasurementDao, times(1)).markAggregateDebugReportDelivered(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendReportForPendingReportSuccessSingleTriggerDebugKey()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId("aggregateReportId")
                        .setStatus(AggregateReport.Status.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .build();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), Mockito.any());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(Mockito.any(), eq(REPORTING_URI), Mockito.any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.DELIVERED);
        Assert.assertEquals(
                AdServicesStatusUtils.STATUS_SUCCESS,
                mSpyAggregateReportingJobHandler.performReport(
                        aggregateReport.getId(),
                        AggregateCryptoFixture.getKey(),
                        new ReportingStatus()));

        verify(mMeasurementDao, times(1)).markAggregateReportStatus(any(), anyInt());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendReportForPendingReportSuccessSingleSourceDebugKey()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId("aggregateReportId")
                        .setStatus(AggregateReport.Status.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceDebugKey(SOURCE_DEBUG_KEY)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .build();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), Mockito.any());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(Mockito.any(), eq(REPORTING_URI), Mockito.any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.DELIVERED);
        Assert.assertEquals(
                AdServicesStatusUtils.STATUS_SUCCESS,
                mSpyAggregateReportingJobHandler.performReport(
                        aggregateReport.getId(),
                        AggregateCryptoFixture.getKey(),
                        new ReportingStatus()));

        verify(mMeasurementDao, times(1)).markAggregateReportStatus(any(), anyInt());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendReportForPendingReportSuccessNullDebugKeys()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId("aggregateReportId")
                        .setStatus(AggregateReport.Status.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceDebugKey(null)
                        .setTriggerDebugKey(null)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .build();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), Mockito.any());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(Mockito.any(), eq(REPORTING_URI), Mockito.any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.DELIVERED);
        Assert.assertEquals(
                AdServicesStatusUtils.STATUS_SUCCESS,
                mSpyAggregateReportingJobHandler.performReport(
                        aggregateReport.getId(),
                        AggregateCryptoFixture.getKey(),
                        new ReportingStatus()));

        verify(mMeasurementDao, times(1)).markAggregateReportStatus(any(), anyInt());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendReportForPendingReportFailure()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId("aggregateReportId")
                        .setStatus(AggregateReport.Status.PENDING)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .build();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_BAD_REQUEST)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), Mockito.any());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(Mockito.any(), eq(REPORTING_URI), Mockito.any());

        Assert.assertEquals(
                AdServicesStatusUtils.STATUS_IO_ERROR,
                mSpyAggregateReportingJobHandler.performReport(
                        aggregateReport.getId(),
                        AggregateCryptoFixture.getKey(),
                        new ReportingStatus()));

        verify(mMeasurementDao, never()).markAggregateReportStatus(any(), anyInt());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void testSendReportForAlreadyDeliveredReport() throws DatastoreException {
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId("aggregateReportId")
                        .setStatus(AggregateReport.Status.DELIVERED)
                        .setDebugCleartextPayload(CLEARTEXT_PAYLOAD)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .build();

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        Assert.assertEquals(
                AdServicesStatusUtils.STATUS_INVALID_ARGUMENT,
                mSpyAggregateReportingJobHandler.performReport(
                        aggregateReport.getId(),
                        AggregateCryptoFixture.getKey(),
                        new ReportingStatus()));

        verify(mMeasurementDao, never()).markAggregateReportStatus(any(), anyInt());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void testPerformScheduledPendingReportsForMultipleReports()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport1 = createASampleAggregateReport();
        JSONObject aggregateReportBody1 = createASampleAggregateReportBody(aggregateReport1);
        AggregateReport aggregateReport2 =
                new AggregateReport.Builder()
                        .setId("aggregateReportId2")
                        .setStatus(AggregateReport.Status.PENDING)
                        .setScheduledReportTime(1100L)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .build();
        JSONObject aggregateReportBody2 = createASampleAggregateReportBody(aggregateReport2);

        when(mMeasurementDao.getPendingAggregateReportIdsByCoordinatorInWindow(1000, 1100))
                .thenReturn(
                        Map.of(
                                COORDINATOR_ORIGIN.toString(),
                                List.of(aggregateReport1.getId(), aggregateReport2.getId())));
        when(mMeasurementDao.getAggregateReport(aggregateReport1.getId()))
                .thenReturn(aggregateReport1);
        when(mMeasurementDao.getAggregateReport(aggregateReport2.getId()))
                .thenReturn(aggregateReport2);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), Mockito.any());
        doReturn(aggregateReportBody1)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(
                        aggregateReport1, REPORTING_URI, AggregateCryptoFixture.getKey());
        doReturn(aggregateReportBody2)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(
                        aggregateReport2, REPORTING_URI, AggregateCryptoFixture.getKey());

        assertTrue(
                mSpyAggregateReportingJobHandler.performScheduledPendingReportsInWindow(
                        1000, 1100));

        verify(mMeasurementDao, times(2)).markAggregateReportStatus(any(), anyInt());
        verify(mTransaction, times(5)).begin();
        verify(mTransaction, times(5)).end();
    }

    @Test
    public void testPerformScheduledPendingReportsInWindow_noKeys()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        when(mMeasurementDao.getPendingAggregateReportIdsByCoordinatorInWindow(1000, 1100))
                .thenReturn(
                        Map.of(COORDINATOR_ORIGIN.toString(), List.of(aggregateReport.getId())));
        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), Mockito.any());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(
                        aggregateReport, REPORTING_URI, AggregateCryptoFixture.getKey());

        AggregateEncryptionKeyManager mockKeyManager = mock(AggregateEncryptionKeyManager.class);
        when(mockKeyManager.getAggregateEncryptionKeys(any(), anyInt()))
                .thenReturn(Collections.emptyList());
        mAggregateReportingJobHandler =
                new AggregateReportingJobHandler(
                        new FakeDatasoreManager(),
                        mockKeyManager,
                        mMockFlags,
                        mLogger,
                        sContext);
        mSpyAggregateReportingJobHandler = Mockito.spy(mAggregateReportingJobHandler);

        assertTrue(
                mSpyAggregateReportingJobHandler.performScheduledPendingReportsInWindow(
                        1000, 1100));

        verify(mMeasurementDao, never()).markAggregateReportStatus(any(), anyInt());
    }

    @Test
    public void testPerformScheduledPendingReports_ThreadInterrupted()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport1 = createASampleAggregateReport();
        JSONObject aggregateReportBody1 = createASampleAggregateReportBody(aggregateReport1);
        AggregateReport aggregateReport2 =
                new AggregateReport.Builder()
                        .setId("aggregateReportId2")
                        .setStatus(AggregateReport.Status.PENDING)
                        .setScheduledReportTime(1100L)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setRegistrationOrigin(REPORTING_URI)
                        .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                        .build();
        JSONObject aggregateReportBody2 = createASampleAggregateReportBody(aggregateReport2);

        when(mMeasurementDao.getPendingAggregateReportIdsByCoordinatorInWindow(1000, 1100))
                .thenReturn(
                        Map.of(
                                COORDINATOR_ORIGIN.toString(),
                                List.of(aggregateReport1.getId(), aggregateReport2.getId())));
        when(mMeasurementDao.getAggregateReport(aggregateReport1.getId()))
                .thenReturn(aggregateReport1);
        when(mMeasurementDao.getAggregateReport(aggregateReport2.getId()))
                .thenReturn(aggregateReport2);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(Mockito.eq(REPORTING_URI), Mockito.any());
        doReturn(aggregateReportBody1)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(
                        aggregateReport1, REPORTING_URI, AggregateCryptoFixture.getKey());
        doReturn(aggregateReportBody2)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(
                        aggregateReport2, REPORTING_URI, AggregateCryptoFixture.getKey());

        Thread.currentThread().interrupt();
        assertTrue(
                mSpyAggregateReportingJobHandler.performScheduledPendingReportsInWindow(
                        1000, 1100));

        // 0 reports processed, since the thread exits early.
        verify(mMeasurementDao, times(0)).markAggregateReportStatus(any(), anyInt());

        // 1 transaction for initial retrieval of pending report ids.
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void testPerformScheduledPendingReports_LogZeroRetryCount()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport1 = createASampleAggregateReport();
        JSONObject aggregateReportBody1 = createASampleAggregateReportBody(aggregateReport1);

        when(mMeasurementDao.getPendingAggregateReportIdsByCoordinatorInWindow(1000, 1100))
                .thenReturn(
                        Map.of(COORDINATOR_ORIGIN.toString(), List.of(aggregateReport1.getId())));
        when(mMeasurementDao.getAggregateReport(aggregateReport1.getId()))
                .thenReturn(aggregateReport1);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), Mockito.any());
        doReturn(aggregateReportBody1)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(
                        aggregateReport1, REPORTING_URI, AggregateCryptoFixture.getKey());

        assertTrue(
                mSpyAggregateReportingJobHandler.performScheduledPendingReportsInWindow(
                        1000, 1100));
        ArgumentCaptor<MeasurementReportsStats> statusArg =
                ArgumentCaptor.forClass(MeasurementReportsStats.class);
        verify(mLogger).logMeasurementReports(statusArg.capture());
        MeasurementReportsStats measurementReportsStats = statusArg.getValue();
        assertEquals(
                measurementReportsStats.getType(), ReportingStatus.ReportType.AGGREGATE.getValue());
        assertEquals(
                measurementReportsStats.getResultCode(),
                ReportingStatus.UploadStatus.SUCCESS.getValue());
        assertEquals(
                measurementReportsStats.getFailureType(),
                ReportingStatus.FailureStatus.UNKNOWN.getValue());
        verify(mMeasurementDao, never()).incrementAndGetReportingRetryCount(any(), any());
    }

    @Test
    public void testPerformScheduledPendingReports_LogReportNotFound() throws DatastoreException {
        AggregateReport aggregateReport1 = createASampleAggregateReport();

        when(mMeasurementDao.getPendingAggregateReportIdsByCoordinatorInWindow(1000, 1100))
                .thenReturn(
                        Map.of(COORDINATOR_ORIGIN.toString(), List.of(aggregateReport1.getId())));
        when(mMeasurementDao.getAggregateReport(aggregateReport1.getId())).thenReturn(null);

        assertTrue(
                mSpyAggregateReportingJobHandler.performScheduledPendingReportsInWindow(
                        1000, 1100));
        ArgumentCaptor<MeasurementReportsStats> statusArg =
                ArgumentCaptor.forClass(MeasurementReportsStats.class);
        verify(mLogger).logMeasurementReports(statusArg.capture());
        MeasurementReportsStats measurementReportsStats = statusArg.getValue();
        assertEquals(
                measurementReportsStats.getType(), ReportingStatus.ReportType.AGGREGATE.getValue());
        assertEquals(
                measurementReportsStats.getResultCode(),
                ReportingStatus.UploadStatus.FAILURE.getValue());
        assertEquals(
                measurementReportsStats.getFailureType(),
                ReportingStatus.FailureStatus.REPORT_NOT_FOUND.getValue());
        verify(mMeasurementDao)
                .incrementAndGetReportingRetryCount(
                        aggregateReport1.getId(),
                        KeyValueData.DataType.AGGREGATE_REPORT_RETRY_COUNT);
    }

    @Test
    public void performReport_throwsIOException_logsReportingStatus()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doThrow(new IOException())
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), Mockito.any());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(Mockito.any(), Mockito.any(), Mockito.any());

        Assert.assertEquals(
                AdServicesStatusUtils.STATUS_IO_ERROR,
                mSpyAggregateReportingJobHandler.performReport(
                        aggregateReport.getId(),
                        AggregateCryptoFixture.getKey(),
                        new ReportingStatus()));

        verify(mMeasurementDao, never()).markAggregateReportStatus(any(), anyInt());
        verify(mSpyAggregateReportingJobHandler, times(1))
                .makeHttpPostRequest(eq(REPORTING_URI), Mockito.any());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsJsonDisabledToThrow_logsAndSwallowsException()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();

        doReturn(false)
                .when(mMockFlags)
                .getMeasurementEnableReportDeletionOnUnrecoverableException();
        doReturn(false).when(mMockFlags).getMeasurementEnableReportingJobsThrowJsonException();
        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);

        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(any(), any());
        doThrow(new JSONException("cause message"))
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(Mockito.any(), Mockito.any(), Mockito.any());

        Assert.assertEquals(
                AdServicesStatusUtils.STATUS_UNKNOWN_ERROR,
                mSpyAggregateReportingJobHandler.performReport(
                        aggregateReport.getId(),
                        AggregateCryptoFixture.getKey(),
                        new ReportingStatus()));

        verify(mMeasurementDao, never()).markAggregateReportStatus(anyString(), anyInt());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsJsonExceptionNoSampling_logsAndSwallowsException()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();

        doReturn(true)
                .when(mMockFlags)
                .getMeasurementEnableReportDeletionOnUnrecoverableException();
        doReturn(true).when(mMockFlags).getMeasurementEnableReportingJobsThrowJsonException();
        doReturn(0.0f).when(mMockFlags).getMeasurementThrowUnknownExceptionSamplingRate();
        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);

        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(any(), any());
        doThrow(new JSONException("cause message"))
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(Mockito.any(), Mockito.any(), Mockito.any());

        Assert.assertEquals(
                AdServicesStatusUtils.STATUS_UNKNOWN_ERROR,
                mSpyAggregateReportingJobHandler.performReport(
                        aggregateReport.getId(),
                        AggregateCryptoFixture.getKey(),
                        new ReportingStatus()));

        verify(mMeasurementDao).markAggregateReportStatus(eq(aggregateReport.getId()), anyInt());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performReport_throwsJsonEnabledToThrow_marksReportDeletedAndRethrowsException()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();

        doReturn(true)
                .when(mMockFlags)
                .getMeasurementEnableReportDeletionOnUnrecoverableException();
        doReturn(true).when(mMockFlags).getMeasurementEnableReportingJobsThrowJsonException();
        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), Mockito.any());
        doThrow(new JSONException("cause message"))
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(Mockito.any(), Mockito.any(), Mockito.any());
        doReturn(1.0f).when(mMockFlags).getMeasurementThrowUnknownExceptionSamplingRate();

        try {
            mSpyAggregateReportingJobHandler.performReport(
                    aggregateReport.getId(),
                    AggregateCryptoFixture.getKey(),
                    new ReportingStatus());
            fail();
        } catch (IllegalStateException e) {
            assertEquals(JSONException.class, e.getCause().getClass());
            assertEquals("cause message", e.getCause().getMessage());
        }

        verify(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.MARKED_TO_DELETE);
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performReport_throwsUnknownExceptionDisabledToThrow_logsAndSwallowsException()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        doReturn(false)
                .when(mMockFlags)
                .getMeasurementEnableReportingJobsThrowUnaccountedException();
        doReturn(aggregateReport).when(mMeasurementDao).getAggregateReport(aggregateReport.getId());
        doThrow(new RuntimeException("unknown exception"))
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), Mockito.any());
        doReturn(aggregateReportBody)
                .when(mSpyDebugAggregateReportingJobHandler)
                .createReportJsonPayload(any(), any(), any());

        assertEquals(
                AdServicesStatusUtils.STATUS_UNKNOWN_ERROR,
                mSpyAggregateReportingJobHandler.performReport(
                        aggregateReport.getId(),
                        AggregateCryptoFixture.getKey(),
                        new ReportingStatus()));
        verify(mMeasurementDao, never()).markAggregateReportStatus(anyString(), anyInt());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsUnknownExceptionNoSampling_logsAndSwallowsException()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();
        JSONObject aggregateReportBody = createASampleAggregateReportBody(aggregateReport);

        doReturn(true)
                .when(mMockFlags)
                .getMeasurementEnableReportingJobsThrowUnaccountedException();
        doReturn(0.0f).when(mMockFlags).getMeasurementThrowUnknownExceptionSamplingRate();
        doReturn(aggregateReport).when(mMeasurementDao).getAggregateReport(aggregateReport.getId());
        doThrow(new RuntimeException("unknown exception"))
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), Mockito.any());
        doReturn(aggregateReportBody)
                .when(mSpyDebugAggregateReportingJobHandler)
                .createReportJsonPayload(any(), any(), any());

        assertEquals(
                AdServicesStatusUtils.STATUS_UNKNOWN_ERROR,
                mSpyAggregateReportingJobHandler.performReport(
                        aggregateReport.getId(),
                        AggregateCryptoFixture.getKey(),
                        new ReportingStatus()));
        verify(mMeasurementDao, never()).markAggregateReportStatus(anyString(), anyInt());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsUnknownExceptionEnabledToThrow_rethrowsException()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();

        doReturn(true)
                .when(mMockFlags)
                .getMeasurementEnableReportingJobsThrowUnaccountedException();
        doReturn(aggregateReport).when(mMeasurementDao).getAggregateReport(aggregateReport.getId());
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), Mockito.any());
        doThrow(new RuntimeException("unknown exception"))
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), any(), any());
        doReturn(1.0f).when(mMockFlags).getMeasurementThrowUnknownExceptionSamplingRate();

        try {
            mSpyAggregateReportingJobHandler.performReport(
                    aggregateReport.getId(),
                    AggregateCryptoFixture.getKey(),
                    new ReportingStatus());
            fail();
        } catch (RuntimeException e) {
            assertEquals("unknown exception", e.getMessage());
        }

        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsCryptoExceptionDisabledToThrow_logsAndSwallowsException()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();

        doReturn(false).when(mMockFlags).getMeasurementEnableReportingJobsThrowCryptoException();
        doReturn(aggregateReport).when(mMeasurementDao).getAggregateReport(aggregateReport.getId());
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), Mockito.any());
        doThrow(new CryptoException("exception message"))
                .when(mSpyDebugAggregateReportingJobHandler)
                .createReportJsonPayload(any(), any(), any());

        assertEquals(
                AdServicesStatusUtils.STATUS_UNKNOWN_ERROR,
                mSpyAggregateReportingJobHandler.performReport(
                        aggregateReport.getId(),
                        AggregateCryptoFixture.getKey(),
                        new ReportingStatus()));
        verify(mMeasurementDao, never()).markAggregateReportStatus(anyString(), anyInt());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsCryptoExceptionNoSampling_logsAndSwallowsException()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();

        doReturn(true).when(mMockFlags).getMeasurementEnableReportingJobsThrowCryptoException();
        doReturn(0.0f).when(mMockFlags).getMeasurementThrowUnknownExceptionSamplingRate();
        doReturn(aggregateReport).when(mMeasurementDao).getAggregateReport(aggregateReport.getId());
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), Mockito.any());
        doThrow(new CryptoException("exception message"))
                .when(mSpyDebugAggregateReportingJobHandler)
                .createReportJsonPayload(any(), any(), any());

        assertEquals(
                AdServicesStatusUtils.STATUS_UNKNOWN_ERROR,
                mSpyAggregateReportingJobHandler.performReport(
                        aggregateReport.getId(),
                        AggregateCryptoFixture.getKey(),
                        new ReportingStatus()));
        verify(mMeasurementDao, never()).markAggregateReportStatus(anyString(), anyInt());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsCryptoExceptionEnabledToThrow_rethrowsException()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport = createASampleAggregateReport();

        doReturn(true).when(mMockFlags).getMeasurementEnableReportingJobsThrowCryptoException();
        doReturn(aggregateReport).when(mMeasurementDao).getAggregateReport(aggregateReport.getId());
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), Mockito.any());
        doThrow(new CryptoException("exception message"))
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(any(), any(), any());
        doReturn(1.0f).when(mMockFlags).getMeasurementThrowUnknownExceptionSamplingRate();

        try {
            mSpyAggregateReportingJobHandler.performReport(
                    aggregateReport.getId(),
                    AggregateCryptoFixture.getKey(),
                    new ReportingStatus());
            fail();
        } catch (CryptoException e) {
            assertEquals("exception message", e.getMessage());
        }

        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_normalReportWithDebugKeys_hasDebugModeEnabled()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                AggregateReportFixture.getValidAggregateReportBuilder()
                        .setSourceDebugKey(
                                AggregateReportFixture.ValidAggregateReportParams.SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(
                                AggregateReportFixture.ValidAggregateReportParams.TRIGGER_DEBUG_KEY)
                        .build();
        executeDebugModeVerification(aggregateReport, mSpyAggregateReportingJobHandler, "enabled");
        verify(mMeasurementDao, times(1))
                .markAggregateReportStatus(
                        eq(aggregateReport.getId()), eq(AggregateReport.Status.DELIVERED));
    }

    @Test
    public void performReport_normalReportWithOnlySourceDebugKey_hasDebugModeNull()
            throws DatastoreException, IOException, JSONException {
        // Setup
        AggregateReport aggregateReport =
                AggregateReportFixture.getValidAggregateReportBuilder()
                        .setSourceDebugKey(
                                AggregateReportFixture.ValidAggregateReportParams.SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(null)
                        .build();
        executeDebugModeVerification(aggregateReport, mSpyAggregateReportingJobHandler, "");
        verify(mMeasurementDao, times(1))
                .markAggregateReportStatus(
                        eq(aggregateReport.getId()), eq(AggregateReport.Status.DELIVERED));
    }

    @Test
    public void performReport_normalReportWithOnlyTriggerDebugKey_hasDebugModeNull()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                AggregateReportFixture.getValidAggregateReportBuilder()
                        .setSourceDebugKey(null)
                        .setTriggerDebugKey(
                                AggregateReportFixture.ValidAggregateReportParams.TRIGGER_DEBUG_KEY)
                        .build();
        executeDebugModeVerification(aggregateReport, mSpyAggregateReportingJobHandler, "");
        verify(mMeasurementDao, times(1))
                .markAggregateReportStatus(
                        eq(aggregateReport.getId()), eq(AggregateReport.Status.DELIVERED));
    }

    @Test
    public void performReport_normalReportWithNoDebugKey_hasDebugModeNull()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                AggregateReportFixture.getValidAggregateReportBuilder()
                        .setSourceDebugKey(null)
                        .setTriggerDebugKey(null)
                        .build();
        executeDebugModeVerification(aggregateReport, mSpyAggregateReportingJobHandler, "");
        verify(mMeasurementDao, times(1))
                .markAggregateReportStatus(
                        eq(aggregateReport.getId()), eq(AggregateReport.Status.DELIVERED));
    }

    @Test
    public void performReport_debugReportWithDebugKeys_hasDebugModeEnabled()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                AggregateReportFixture.getValidAggregateReportBuilder()
                        .setSourceDebugKey(
                                AggregateReportFixture.ValidAggregateReportParams.SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(
                                AggregateReportFixture.ValidAggregateReportParams.TRIGGER_DEBUG_KEY)
                        .build();
        executeDebugModeVerification(
                aggregateReport, mSpyDebugAggregateReportingJobHandler, "enabled");
        verify(mMeasurementDao, times(1))
                .markAggregateDebugReportDelivered(eq(aggregateReport.getId()));
    }

    @Test
    public void performReport_debugReportWithOnlySourceDebugKey_hasDebugModeNull()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                AggregateReportFixture.getValidAggregateReportBuilder()
                        .setSourceDebugKey(
                                AggregateReportFixture.ValidAggregateReportParams.SOURCE_DEBUG_KEY)
                        .setTriggerDebugKey(null)
                        .build();
        executeDebugModeVerification(aggregateReport, mSpyDebugAggregateReportingJobHandler, "");
        verify(mMeasurementDao, times(1))
                .markAggregateDebugReportDelivered(eq(aggregateReport.getId()));
    }

    @Test
    public void performReport_debugReportWithOnlyTriggerDebugKey_hasDebugModeNull()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                AggregateReportFixture.getValidAggregateReportBuilder()
                        .setSourceDebugKey(null)
                        .setTriggerDebugKey(
                                AggregateReportFixture.ValidAggregateReportParams.TRIGGER_DEBUG_KEY)
                        .build();
        executeDebugModeVerification(aggregateReport, mSpyDebugAggregateReportingJobHandler, "");
    }

    @Test
    public void performReport_debugReportWithNoDebugKey_hasDebugModeNull()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                AggregateReportFixture.getValidAggregateReportBuilder()
                        .setSourceDebugKey(null)
                        .setTriggerDebugKey(null)
                        .build();
        executeDebugModeVerification(aggregateReport, mSpyDebugAggregateReportingJobHandler, "");
        verify(mMeasurementDao, times(1))
                .markAggregateDebugReportDelivered(eq(aggregateReport.getId()));
    }

    private void executeDebugModeVerification(
            AggregateReport aggregateReport,
            AggregateReportingJobHandler aggregateReportingJobHandler,
            String expectedDebugMode)
            throws DatastoreException, IOException, JSONException {
        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(aggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), Mockito.any());

        doNothing()
                .when(mMeasurementDao)
                .markAggregateReportStatus(
                        aggregateReport.getId(), AggregateReport.Status.DELIVERED);
        ArgumentCaptor<JSONObject> aggregateReportBodyCaptor =
                ArgumentCaptor.forClass(JSONObject.class);

        // Execution
        Assert.assertEquals(
                AdServicesStatusUtils.STATUS_SUCCESS,
                aggregateReportingJobHandler.performReport(
                        aggregateReport.getId(),
                        AggregateCryptoFixture.getKey(),
                        new ReportingStatus()));

        // Assertion
        verify(aggregateReportingJobHandler)
                .makeHttpPostRequest(eq(REPORTING_URI), aggregateReportBodyCaptor.capture());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();

        JSONObject aggregateReportBody = aggregateReportBodyCaptor.getValue();
        JSONObject sharedInfo =
                new JSONObject(
                        aggregateReportBody.getString(
                                AggregateReportBody.PayloadBodyKeys.SHARED_INFO));
        assertEquals(
                expectedDebugMode,
                sharedInfo.optString(AggregateReportBody.SharedInfoKeys.DEBUG_MODE));
    }

    private JSONObject createASampleAggregateReportBody(AggregateReport aggregateReport)
            throws JSONException {
        return new AggregateReportBody.Builder()
                .setReportId(aggregateReport.getId())
                .setDebugCleartextPayload(CLEARTEXT_PAYLOAD)
                .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                .build()
                .toJson(AggregateCryptoFixture.getKey(), mMockFlags);
    }

    private static AggregateReport createASampleAggregateReport() {
        return new AggregateReport.Builder()
                .setId("aggregateReportId1")
                .setStatus(AggregateReport.Status.PENDING)
                .setScheduledReportTime(1000L)
                .setEnrollmentId(ENROLLMENT_ID)
                .setRegistrationOrigin(REPORTING_URI)
                .setAggregationCoordinatorOrigin(COORDINATOR_ORIGIN)
                .build();
    }
}
