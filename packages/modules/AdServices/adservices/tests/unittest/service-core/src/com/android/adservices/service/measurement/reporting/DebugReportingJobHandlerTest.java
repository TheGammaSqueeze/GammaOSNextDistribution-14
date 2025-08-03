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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
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
import com.android.adservices.service.measurement.KeyValueData;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.MeasurementReportsStats;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.json.JSONArray;
import org.json.JSONException;
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
import java.util.Collections;
import java.util.List;

/** Unit test for {@link DebugReportingJobHandler} */
@RunWith(MockitoJUnitRunner.class)
public class DebugReportingJobHandlerTest {

    private static final Uri REGISTRATION_URI = WebUtil.validUri("https://subdomain.example.test");
    private static final Uri SOURCE_REGISTRANT = Uri.parse("android-app://com.example.abc");

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    DatastoreManager mDatastoreManager;

    @Mock private IMeasurementDao mMeasurementDao;

    @Mock private ITransaction mTransaction;

    @Mock private Flags mFlags;
    @Mock private AdServicesLogger mLogger;
    @Mock private AdServicesErrorLogger mErrorLogger;

    DebugReportingJobHandler mDebugReportingJobHandler;
    DebugReportingJobHandler mSpyDebugReportingJobHandler;

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
        mDatastoreManager = new FakeDatasoreManager();
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));

        mDebugReportingJobHandler =
                new DebugReportingJobHandler(mDatastoreManager, mFlags, mLogger, sContext);
        mSpyDebugReportingJobHandler = Mockito.spy(mDebugReportingJobHandler);
    }

    @Test
    public void testSendDebugReportForSuccess()
            throws DatastoreException, IOException, JSONException {
        DebugReport debugReport = createDebugReport1();
        JSONArray debugReportPayload = new JSONArray();
        debugReportPayload.put(debugReport.toPayloadJson());

        when(mMeasurementDao.getDebugReport(debugReport.getId())).thenReturn(debugReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyDebugReportingJobHandler)
                .makeHttpPostRequest(Mockito.eq(REGISTRATION_URI), Mockito.any());
        doReturn(debugReportPayload)
                .when(mSpyDebugReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        doNothing().when(mMeasurementDao).deleteDebugReport(debugReport.getId());

        Assert.assertEquals(
                AdServicesStatusUtils.STATUS_SUCCESS,
                mSpyDebugReportingJobHandler.performReport(
                        debugReport.getId(), new ReportingStatus()));

        verify(mMeasurementDao, times(1)).deleteDebugReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendDebugReportForFailure()
            throws DatastoreException, IOException, JSONException {
        DebugReport debugReport = createDebugReport1();
        JSONArray debugReportPayload = new JSONArray();
        debugReportPayload.put(debugReport.toPayloadJson());
        when(mMeasurementDao.getDebugReport(debugReport.getId())).thenReturn(debugReport);
        doReturn(HttpURLConnection.HTTP_BAD_REQUEST)
                .when(mSpyDebugReportingJobHandler)
                .makeHttpPostRequest(Mockito.eq(REGISTRATION_URI), Mockito.any());
        doReturn(debugReportPayload)
                .when(mSpyDebugReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        Assert.assertEquals(
                AdServicesStatusUtils.STATUS_IO_ERROR,
                mSpyDebugReportingJobHandler.performReport(
                        debugReport.getId(), new ReportingStatus()));

        verify(mMeasurementDao, never()).deleteDebugReport(any());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void testPerformScheduledReportsForMultipleReports()
            throws DatastoreException, IOException, JSONException {
        DebugReport debugReport1 = createDebugReport1();
        JSONArray debugReportPayload1 = new JSONArray();
        debugReportPayload1.put(debugReport1.toPayloadJson());
        DebugReport debugReport2 = createDebugReport2();
        JSONArray debugReportPayload2 = new JSONArray();
        debugReportPayload2.put(debugReport2.toPayloadJson());

        when(mMeasurementDao.getDebugReportIds())
                .thenReturn(List.of(debugReport1.getId(), debugReport2.getId()));
        when(mMeasurementDao.getDebugReport(debugReport1.getId())).thenReturn(debugReport1);
        when(mMeasurementDao.getDebugReport(debugReport2.getId())).thenReturn(debugReport2);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyDebugReportingJobHandler)
                .makeHttpPostRequest(Mockito.eq(REGISTRATION_URI), any());
        doReturn(debugReportPayload1)
                .when(mSpyDebugReportingJobHandler)
                .createReportJsonPayload(debugReport1);
        doReturn(debugReportPayload2)
                .when(mSpyDebugReportingJobHandler)
                .createReportJsonPayload(debugReport2);

        mSpyDebugReportingJobHandler.performScheduledPendingReports();

        verify(mMeasurementDao, times(2)).deleteDebugReport(any());
        verify(mTransaction, times(5)).begin();
        verify(mTransaction, times(5)).end();
    }

    @Test
    public void testPerformScheduledReports_ThreadInterrupted()
            throws DatastoreException, IOException, JSONException {
        DebugReport debugReport1 = createDebugReport1();
        JSONArray debugReportPayload1 = new JSONArray();
        debugReportPayload1.put(debugReport1.toPayloadJson());
        DebugReport debugReport2 = createDebugReport2();
        JSONArray debugReportPayload2 = new JSONArray();
        debugReportPayload2.put(debugReport2.toPayloadJson());

        when(mMeasurementDao.getDebugReportIds())
                .thenReturn(List.of(debugReport1.getId(), debugReport2.getId()));
        when(mMeasurementDao.getDebugReport(debugReport1.getId())).thenReturn(debugReport1);
        when(mMeasurementDao.getDebugReport(debugReport2.getId())).thenReturn(debugReport2);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyDebugReportingJobHandler)
                .makeHttpPostRequest(Mockito.eq(REGISTRATION_URI), any());
        doReturn(debugReportPayload1)
                .when(mSpyDebugReportingJobHandler)
                .createReportJsonPayload(debugReport1);
        doReturn(debugReportPayload2)
                .when(mSpyDebugReportingJobHandler)
                .createReportJsonPayload(debugReport2);

        Thread.currentThread().interrupt();
        mSpyDebugReportingJobHandler.performScheduledPendingReports();

        // 0 reports processed, since the thread exits early.
        verify(mMeasurementDao, times(0)).deleteDebugReport(any());

        // 1 transaction for initial retrieval of pending report ids.
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void testPerformScheduledReports_LogZeroRetryCount()
            throws DatastoreException, IOException, JSONException {
        DebugReport debugReport1 = createDebugReport1();
        JSONArray debugReportPayload1 = new JSONArray();
        debugReportPayload1.put(debugReport1.toPayloadJson());

        doReturn(true).when(mFlags).getMeasurementEnableAppPackageNameLogging();
        when(mMeasurementDao.getDebugReportIds()).thenReturn(List.of(debugReport1.getId()));
        when(mMeasurementDao.getDebugReport(debugReport1.getId())).thenReturn(debugReport1);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyDebugReportingJobHandler)
                .makeHttpPostRequest(Mockito.eq(REGISTRATION_URI), any());
        doReturn(debugReportPayload1)
                .when(mSpyDebugReportingJobHandler)
                .createReportJsonPayload(debugReport1);

        mSpyDebugReportingJobHandler.performScheduledPendingReports();

        ArgumentCaptor<MeasurementReportsStats> statusArg =
                ArgumentCaptor.forClass(MeasurementReportsStats.class);
        verify(mLogger).logMeasurementReports(statusArg.capture());
        MeasurementReportsStats measurementReportsStats = statusArg.getValue();
        assertTrue(
                measurementReportsStats.getType()
                        >= ReportingStatus.ReportType.VERBOSE_DEBUG_SOURCE_DESTINATION_LIMIT
                                .getValue());
        assertEquals(
                measurementReportsStats.getResultCode(),
                ReportingStatus.UploadStatus.SUCCESS.getValue());
        assertEquals(
                measurementReportsStats.getFailureType(),
                ReportingStatus.FailureStatus.UNKNOWN.getValue());
        assertEquals(
                measurementReportsStats.getSourceRegistrant(),
                debugReport1.getRegistrant().toString());
        verify(mMeasurementDao, never()).incrementAndGetReportingRetryCount(any(), any());
    }

    @Test
    public void testPerformScheduledReports_LogReportNotFound()
            throws DatastoreException, JSONException {
        DebugReport debugReport1 = createDebugReport1();
        JSONArray debugReportPayload1 = new JSONArray();
        debugReportPayload1.put(debugReport1.toPayloadJson());

        when(mMeasurementDao.getDebugReportIds()).thenReturn(List.of(debugReport1.getId()));
        when(mMeasurementDao.getDebugReport(debugReport1.getId())).thenReturn(null);

        mSpyDebugReportingJobHandler.performScheduledPendingReports();

        ArgumentCaptor<MeasurementReportsStats> statusArg =
                ArgumentCaptor.forClass(MeasurementReportsStats.class);
        verify(mLogger).logMeasurementReports(statusArg.capture());
        MeasurementReportsStats measurementReportsStats = statusArg.getValue();
        assertEquals(
                measurementReportsStats.getType(),
                ReportingStatus.ReportType.VERBOSE_DEBUG_UNKNOWN.getValue());
        assertEquals(
                measurementReportsStats.getResultCode(),
                ReportingStatus.UploadStatus.FAILURE.getValue());
        assertEquals(
                measurementReportsStats.getFailureType(),
                ReportingStatus.FailureStatus.REPORT_NOT_FOUND.getValue());
        assertEquals(measurementReportsStats.getSourceRegistrant(), "");
        verify(mMeasurementDao)
                .incrementAndGetReportingRetryCount(
                        debugReport1.getId(), KeyValueData.DataType.DEBUG_REPORT_RETRY_COUNT);
    }

    @Test
    public void performReport_throwsIOException_logsReportingStatus()
            throws DatastoreException, IOException, JSONException {
        DebugReport debugReport = createDebugReport1();

        when(mMeasurementDao.getDebugReport(debugReport.getId())).thenReturn(debugReport);
        doThrow(new IOException())
                .when(mSpyDebugReportingJobHandler)
                .makeHttpPostRequest(Mockito.eq(REGISTRATION_URI), any());
        doReturn(new JSONArray(Collections.singletonList(debugReport.toPayloadJson())))
                .when(mSpyDebugReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        assertEquals(
                AdServicesStatusUtils.STATUS_IO_ERROR,
                mSpyDebugReportingJobHandler.performReport(
                        debugReport.getId(), new ReportingStatus()));

        verify(mMeasurementDao, never()).deleteDebugReport(anyString());
        verify(mSpyDebugReportingJobHandler, times(1))
                .makeHttpPostRequest(Mockito.eq(REGISTRATION_URI), Mockito.any());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsJsonDisabledToThrow_logsAndSwallowsException()
            throws DatastoreException, IOException, JSONException {
        DebugReport debugReport = createDebugReport1();

        doReturn(false).when(mFlags).getMeasurementEnableReportDeletionOnUnrecoverableException();
        doReturn(false).when(mFlags).getMeasurementEnableReportingJobsThrowJsonException();
        doReturn(debugReport).when(mMeasurementDao).getDebugReport(debugReport.getId());
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyDebugReportingJobHandler)
                .makeHttpPostRequest(Mockito.eq(REGISTRATION_URI), Mockito.any());
        doThrow(new JSONException("cause message"))
                .when(mSpyDebugReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        assertEquals(
                AdServicesStatusUtils.STATUS_UNKNOWN_ERROR,
                mSpyDebugReportingJobHandler.performReport(
                        debugReport.getId(), new ReportingStatus()));
        verify(mMeasurementDao, never()).deleteDebugReport(anyString());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsJsonEnabledToThrow_marksReportDeletedAndRethrowsException()
            throws DatastoreException, IOException, JSONException {
        DebugReport debugReport = createDebugReport1();

        doReturn(true).when(mFlags).getMeasurementEnableReportDeletionOnUnrecoverableException();
        doReturn(true).when(mFlags).getMeasurementEnableReportingJobsThrowJsonException();
        doReturn(1.0f).when(mFlags).getMeasurementThrowUnknownExceptionSamplingRate();
        doReturn(debugReport).when(mMeasurementDao).getDebugReport(debugReport.getId());
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyDebugReportingJobHandler)
                .makeHttpPostRequest(Mockito.eq(REGISTRATION_URI), Mockito.any());
        doThrow(new JSONException("cause message"))
                .when(mSpyDebugReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        try {
            mSpyDebugReportingJobHandler.performReport(debugReport.getId(), new ReportingStatus());
            fail();
        } catch (IllegalStateException e) {
            assertEquals(JSONException.class, e.getCause().getClass());
            assertEquals("cause message", e.getCause().getMessage());
        }

        verify(mMeasurementDao).deleteDebugReport(debugReport.getId());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performReport_throwsJsonEnabledToThrowNoSampling_logsAndSwallowsException()
            throws DatastoreException, IOException, JSONException {
        DebugReport debugReport = createDebugReport1();

        doReturn(true).when(mFlags).getMeasurementEnableReportDeletionOnUnrecoverableException();
        doReturn(true).when(mFlags).getMeasurementEnableReportingJobsThrowJsonException();
        doReturn(0.0f).when(mFlags).getMeasurementThrowUnknownExceptionSamplingRate();
        doReturn(debugReport).when(mMeasurementDao).getDebugReport(debugReport.getId());
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyDebugReportingJobHandler)
                .makeHttpPostRequest(Mockito.eq(REGISTRATION_URI), Mockito.any());
        doThrow(new JSONException("cause message"))
                .when(mSpyDebugReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        assertEquals(
                AdServicesStatusUtils.STATUS_UNKNOWN_ERROR,
                mSpyDebugReportingJobHandler.performReport(
                        debugReport.getId(), new ReportingStatus()));
        verify(mMeasurementDao).deleteDebugReport(debugReport.getId());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void performReport_throwsUnknownExceptionDisabledToThrow_logsAndSwallowsException()
            throws DatastoreException, IOException, JSONException {
        DebugReport debugReport = createDebugReport1();

        doReturn(false).when(mFlags).getMeasurementEnableReportingJobsThrowUnaccountedException();
        doReturn(debugReport).when(mMeasurementDao).getDebugReport(debugReport.getId());
        doThrow(new RuntimeException("unknown exception"))
                .when(mSpyDebugReportingJobHandler)
                .makeHttpPostRequest(Mockito.eq(REGISTRATION_URI), Mockito.any());
        doReturn(new JSONArray(Collections.singletonList(debugReport.toPayloadJson())))
                .when(mSpyDebugReportingJobHandler)
                .createReportJsonPayload(Mockito.any());

        assertEquals(
                AdServicesStatusUtils.STATUS_UNKNOWN_ERROR,
                mSpyDebugReportingJobHandler.performReport(
                        debugReport.getId(), new ReportingStatus()));
        verify(mMeasurementDao, never()).deleteDebugReport(anyString());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsUnknownExceptionEnabledToThrow_rethrowsException()
            throws DatastoreException, IOException, JSONException {
        DebugReport debugReport = createDebugReport1();

        doReturn(true).when(mFlags).getMeasurementEnableReportingJobsThrowUnaccountedException();
        doReturn(debugReport).when(mMeasurementDao).getDebugReport(debugReport.getId());
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyDebugReportingJobHandler)
                .makeHttpPostRequest(Mockito.eq(REGISTRATION_URI), Mockito.any());
        doThrow(new RuntimeException("unknown exception"))
                .when(mSpyDebugReportingJobHandler)
                .createReportJsonPayload(Mockito.any());
        doReturn(1.0f).when(mFlags).getMeasurementThrowUnknownExceptionSamplingRate();

        try {
            mSpyDebugReportingJobHandler.performReport(debugReport.getId(), new ReportingStatus());
            fail();
        } catch (RuntimeException e) {
            assertEquals("unknown exception", e.getMessage());
        }

        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void performReport_throwsUnknownExceptionEnabledToThrowNoSampling_swallowsException()
            throws DatastoreException, IOException, JSONException {
        DebugReport debugReport = createDebugReport1();

        doReturn(true).when(mFlags).getMeasurementEnableReportingJobsThrowUnaccountedException();
        doReturn(debugReport).when(mMeasurementDao).getDebugReport(debugReport.getId());
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyDebugReportingJobHandler)
                .makeHttpPostRequest(Mockito.eq(REGISTRATION_URI), Mockito.any());
        doThrow(new RuntimeException("unknown exception"))
                .when(mSpyDebugReportingJobHandler)
                .createReportJsonPayload(Mockito.any());
        doReturn(0.0f).when(mFlags).getMeasurementThrowUnknownExceptionSamplingRate();

        assertEquals(
                AdServicesStatusUtils.STATUS_UNKNOWN_ERROR,
                mSpyDebugReportingJobHandler.performReport(
                        debugReport.getId(), new ReportingStatus()));
        verify(mMeasurementDao, never()).deleteDebugReport(anyString());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    private DebugReport createDebugReport1() {
        return new DebugReport.Builder()
                .setId("reportId1")
                .setType("trigger-event-deduplicated")
                .setBody(
                        " {\n"
                                + "      \"attribution_destination\":"
                                + " \"https://destination.example\",\n"
                                + "      \"source_event_id\": \"45623\"\n"
                                + "    }")
                .setEnrollmentId("1")
                .setRegistrationOrigin(REGISTRATION_URI)
                .setInsertionTime(0L)
                .setRegistrant(SOURCE_REGISTRANT)
                .build();
    }

    private DebugReport createDebugReport2() {
        return new DebugReport.Builder()
                .setId("reportId2")
                .setType("source-destination-limit")
                .setBody(
                        " {\n"
                                + "      \"attribution_destination\":"
                                + " \"https://destination.example\",\n"
                                + "      \"source_event_id\": \"45623\"\n"
                                + "    }")
                .setEnrollmentId("1")
                .setRegistrationOrigin(REGISTRATION_URI)
                .setInsertionTime(0L)
                .setRegistrant(SOURCE_REGISTRANT)
                .build();
    }
}
