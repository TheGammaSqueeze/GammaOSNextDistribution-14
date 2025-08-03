/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.uwb;

import static com.android.server.uwb.UwbTestUtils.DATA_PAYLOAD;
import static com.android.server.uwb.UwbTestUtils.PEER_EXTENDED_UWB_ADDRESS;
import static com.android.server.uwb.UwbTestUtils.PEER_SHORT_MAC_ADDRESS;
import static com.android.server.uwb.UwbTestUtils.PERSISTABLE_BUNDLE;
import static com.android.server.uwb.data.UwbUciConstants.MAC_ADDRESSING_MODE_SHORT;
import static com.android.server.uwb.data.UwbUciConstants.RANGING_MEASUREMENT_TYPE_DL_TDOA;
import static com.android.server.uwb.data.UwbUciConstants.RANGING_MEASUREMENT_TYPE_OWR_AOA;
import static com.android.server.uwb.data.UwbUciConstants.RANGING_MEASUREMENT_TYPE_TWO_WAY;
import static com.android.server.uwb.data.UwbUciConstants.STATUS_CODE_FAILED;

import static com.google.uwb.support.radar.RadarParams.RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.AttributionSource;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Pair;
import android.uwb.IUwbOemExtensionCallback;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.RangingChangeReason;
import android.uwb.RangingReport;
import android.uwb.SessionHandle;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.data.UwbRadarData;
import com.android.server.uwb.data.UwbRangingData;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.uwb.flags.Flags;

import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.radar.RadarData;
import com.google.uwb.support.radar.RadarOpenSessionParams;
import com.google.uwb.support.radar.RadarParams;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;

/**
 * Unit tests for {@link com.android.server.uwb.UwbSettingsStore}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbSessionNotificationManagerTest {
    private static final long TEST_ELAPSED_NANOS = 100L;
    private static final int UID = 343453;
    private static final String PACKAGE_NAME = "com.uwb.test";
    private static final AttributionSource ATTRIBUTION_SOURCE =
            new AttributionSource.Builder(UID).setPackageName(PACKAGE_NAME).build();

    @Mock private UwbInjector mUwbInjector;
    @Mock private UwbSessionManager.UwbSession mUwbSession;
    @Mock private UwbSessionManager.UwbSession mUwbRadarSession;
    @Mock private SessionHandle mSessionHandle;
    @Mock private IUwbRangingCallbacks mIUwbRangingCallbacks;
    @Mock private FiraOpenSessionParams mFiraParams;
    @Mock private RadarOpenSessionParams mRadarParams;
    @Mock private UwbServiceCore mUwbServiceCore;
    @Mock private UwbMetrics mUwbMetrics;
    @Mock private IUwbOemExtensionCallback mIOemExtensionCallback;
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private UwbSessionNotificationManager mUwbSessionNotificationManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mUwbSession.getSessionHandle()).thenReturn(mSessionHandle);
        when(mUwbSession.getIUwbRangingCallbacks()).thenReturn(mIUwbRangingCallbacks);
        when(mUwbSession.getProtocolName()).thenReturn(FiraParams.PROTOCOL_NAME);
        when(mUwbSession.getParams()).thenReturn(mFiraParams);
        when(mUwbSession.getAttributionSource()).thenReturn(ATTRIBUTION_SOURCE);
        when(mUwbRadarSession.getSessionHandle()).thenReturn(mSessionHandle);
        when(mUwbRadarSession.getIUwbRangingCallbacks()).thenReturn(mIUwbRangingCallbacks);
        when(mUwbRadarSession.getProtocolName()).thenReturn(RadarParams.PROTOCOL_NAME);
        when(mUwbRadarSession.getParams()).thenReturn(mRadarParams);
        when(mUwbRadarSession.getAttributionSource()).thenReturn(ATTRIBUTION_SOURCE);
        when(mFiraParams.getAoaResultRequest()).thenReturn(
                FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS);
        when(mFiraParams.hasRangingResultReportMessage()).thenReturn(false);
        when(mFiraParams.hasControlMessage()).thenReturn(false);
        when(mFiraParams.hasRangingControlPhase()).thenReturn(true);
        when(mUwbInjector.checkUwbRangingPermissionForStartDataDelivery(any(), any()))
                .thenReturn(true);
        when(mUwbInjector.getElapsedSinceBootNanos()).thenReturn(TEST_ELAPSED_NANOS);
        when(mUwbInjector.getUwbServiceCore()).thenReturn(mUwbServiceCore);
        when(mUwbServiceCore.isOemExtensionCbRegistered()).thenReturn(true);
        when(mUwbServiceCore.getOemExtensionCallback()).thenReturn(mIOemExtensionCallback);
        when(mIOemExtensionCallback.onRangingReportReceived(any())).thenAnswer(
                invocation -> invocation.getArgument(0));
        when(mUwbInjector.getUwbMetrics()).thenReturn(mUwbMetrics);
        mUwbSessionNotificationManager = new UwbSessionNotificationManager(mUwbInjector);
    }

    /**
     * Called after each testGG
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
    }

    @Test
    public void testOnRangingResultWithoutUwbRangingPermission() throws Exception {
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                UwbTestUtils.generateRangingDataAndRangingReport(
                        PEER_SHORT_MAC_ADDRESS, MAC_ADDRESSING_MODE_SHORT,
                        RANGING_MEASUREMENT_TYPE_TWO_WAY,
                        true, true, false, false, TEST_ELAPSED_NANOS);
        when(mUwbInjector.checkUwbRangingPermissionForStartDataDelivery(eq(ATTRIBUTION_SOURCE),
                any())).thenReturn(false);
        when(mUwbSession.isDataDeliveryPermissionCheckNeeded()).thenReturn(true);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);

        verify(mIUwbRangingCallbacks, never()).onRangingResult(any(), any());
        verify(mUwbMetrics, never()).logRangingResult(anyInt(), any(), any());
    }

    @Test
    public void testOnRangingResult_forTwoWay_WithAoa() throws Exception {
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                UwbTestUtils.generateRangingDataAndRangingReport(
                        PEER_SHORT_MAC_ADDRESS, MAC_ADDRESSING_MODE_SHORT,
                        RANGING_MEASUREMENT_TYPE_TWO_WAY,
                        true, true, false, false, TEST_ELAPSED_NANOS);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(testRangingDataAndRangingReport.first),
                eq(testRangingDataAndRangingReport.second.getMeasurements().get(0)));
    }

    @Test
    public void testOnRangingResult_forTwoWay_WithNoAoa() throws Exception {
        when(mFiraParams.getAoaResultRequest()).thenReturn(
                FiraParams.AOA_RESULT_REQUEST_MODE_NO_AOA_REPORT);
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                UwbTestUtils.generateRangingDataAndRangingReport(
                        PEER_SHORT_MAC_ADDRESS, MAC_ADDRESSING_MODE_SHORT,
                        RANGING_MEASUREMENT_TYPE_TWO_WAY,
                        false, false, false, false, TEST_ELAPSED_NANOS);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(testRangingDataAndRangingReport.first),
                eq(testRangingDataAndRangingReport.second.getMeasurements().get(0)));
    }

    @Test
    public void testOnRangingResult_forTwoWay_WithNoAoaElevation() throws Exception {
        when(mFiraParams.getAoaResultRequest()).thenReturn(
                FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_AZIMUTH_ONLY);
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                UwbTestUtils.generateRangingDataAndRangingReport(
                        PEER_SHORT_MAC_ADDRESS, MAC_ADDRESSING_MODE_SHORT,
                        RANGING_MEASUREMENT_TYPE_TWO_WAY,
                        true, false, false, false, TEST_ELAPSED_NANOS);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(testRangingDataAndRangingReport.first),
                eq(testRangingDataAndRangingReport.second.getMeasurements().get(0)));
    }

    @Test
    public void testOnRangingResult_forTwoWay_WithNoAoaAzimuth() throws Exception {
        when(mFiraParams.getAoaResultRequest()).thenReturn(
                FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_ELEVATION_ONLY);
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                UwbTestUtils.generateRangingDataAndRangingReport(
                        PEER_SHORT_MAC_ADDRESS, MAC_ADDRESSING_MODE_SHORT,
                        RANGING_MEASUREMENT_TYPE_TWO_WAY,
                        false, true, false, false, TEST_ELAPSED_NANOS);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(testRangingDataAndRangingReport.first),
                eq(testRangingDataAndRangingReport.second.getMeasurements().get(0)));
    }

    @Test
    public void testOnRangingResult_forTwoWay_WithAoaAndDestAoa() throws Exception {
        when(mFiraParams.getAoaResultRequest()).thenReturn(
                FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS);
        when(mFiraParams.hasRangingResultReportMessage()).thenReturn(true);
        when(mFiraParams.hasControlMessage()).thenReturn(true);
        when(mFiraParams.hasRangingControlPhase()).thenReturn(false);
        when(mFiraParams.hasAngleOfArrivalAzimuthReport()).thenReturn(true);
        when(mFiraParams.hasAngleOfArrivalElevationReport()).thenReturn(true);
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                UwbTestUtils.generateRangingDataAndRangingReport(
                        PEER_SHORT_MAC_ADDRESS, MAC_ADDRESSING_MODE_SHORT,
                        RANGING_MEASUREMENT_TYPE_TWO_WAY,
                        true, true, true, true, TEST_ELAPSED_NANOS);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(testRangingDataAndRangingReport.first),
                eq(testRangingDataAndRangingReport.second.getMeasurements().get(0)));
    }

    @Test
    public void testOnRangingResult_forTwoWay_WithAoaAndNoDestAzimuth() throws Exception {
        when(mFiraParams.getAoaResultRequest()).thenReturn(
                FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS);
        when(mFiraParams.hasRangingResultReportMessage()).thenReturn(true);
        when(mFiraParams.hasControlMessage()).thenReturn(true);
        when(mFiraParams.hasRangingControlPhase()).thenReturn(false);
        when(mFiraParams.hasAngleOfArrivalAzimuthReport()).thenReturn(false);
        when(mFiraParams.hasAngleOfArrivalElevationReport()).thenReturn(true);
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                UwbTestUtils.generateRangingDataAndRangingReport(
                        PEER_SHORT_MAC_ADDRESS, MAC_ADDRESSING_MODE_SHORT,
                        RANGING_MEASUREMENT_TYPE_TWO_WAY,
                        true, true, false, true, TEST_ELAPSED_NANOS);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(testRangingDataAndRangingReport.first),
                eq(testRangingDataAndRangingReport.second.getMeasurements().get(0)));
    }

    @Test
    public void testOnRangingResult_forTwoWay_WithAoaAndNoDestElevation() throws Exception {
        when(mFiraParams.getAoaResultRequest()).thenReturn(
                FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS);
        when(mFiraParams.hasRangingResultReportMessage()).thenReturn(true);
        when(mFiraParams.hasControlMessage()).thenReturn(true);
        when(mFiraParams.hasRangingControlPhase()).thenReturn(false);
        when(mFiraParams.hasAngleOfArrivalAzimuthReport()).thenReturn(true);
        when(mFiraParams.hasAngleOfArrivalElevationReport()).thenReturn(false);
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                UwbTestUtils.generateRangingDataAndRangingReport(
                        PEER_SHORT_MAC_ADDRESS, MAC_ADDRESSING_MODE_SHORT,
                        RANGING_MEASUREMENT_TYPE_TWO_WAY,
                        true, true, true, false, TEST_ELAPSED_NANOS);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(testRangingDataAndRangingReport.first),
                eq(testRangingDataAndRangingReport.second.getMeasurements().get(0)));
    }

    @Test
    public void testOnRangingResult_forTwoWay_WithNoAoaAndDestAoa() throws Exception {
        when(mFiraParams.getAoaResultRequest()).thenReturn(
                FiraParams.AOA_RESULT_REQUEST_MODE_NO_AOA_REPORT);
        when(mFiraParams.hasRangingResultReportMessage()).thenReturn(true);
        when(mFiraParams.hasControlMessage()).thenReturn(true);
        when(mFiraParams.hasRangingControlPhase()).thenReturn(false);
        when(mFiraParams.hasAngleOfArrivalAzimuthReport()).thenReturn(true);
        when(mFiraParams.hasAngleOfArrivalElevationReport()).thenReturn(true);
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                UwbTestUtils.generateRangingDataAndRangingReport(
                        PEER_SHORT_MAC_ADDRESS, MAC_ADDRESSING_MODE_SHORT,
                        RANGING_MEASUREMENT_TYPE_TWO_WAY,
                        false, false, true, true, TEST_ELAPSED_NANOS);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(testRangingDataAndRangingReport.first),
                eq(testRangingDataAndRangingReport.second.getMeasurements().get(0)));
    }

    @Test
    public void testOnRangingResult_forTwoWay_WithNoAoaAzimuthAndDestAoa() throws Exception {
        when(mFiraParams.getAoaResultRequest()).thenReturn(
                FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_ELEVATION_ONLY);
        when(mFiraParams.hasRangingResultReportMessage()).thenReturn(true);
        when(mFiraParams.hasControlMessage()).thenReturn(true);
        when(mFiraParams.hasRangingControlPhase()).thenReturn(false);
        when(mFiraParams.hasAngleOfArrivalAzimuthReport()).thenReturn(true);
        when(mFiraParams.hasAngleOfArrivalElevationReport()).thenReturn(true);
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                UwbTestUtils.generateRangingDataAndRangingReport(
                        PEER_SHORT_MAC_ADDRESS, MAC_ADDRESSING_MODE_SHORT,
                        RANGING_MEASUREMENT_TYPE_TWO_WAY,
                        false, true, true, true, TEST_ELAPSED_NANOS);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(testRangingDataAndRangingReport.first),
                eq(testRangingDataAndRangingReport.second.getMeasurements().get(0)));
    }

    @Test
    public void testOnRangingResult_forTwoWay_WithNoAoaElevationAndDestAoa() throws Exception {
        when(mFiraParams.getAoaResultRequest()).thenReturn(
                FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_AZIMUTH_ONLY);
        when(mFiraParams.hasRangingResultReportMessage()).thenReturn(true);
        when(mFiraParams.hasControlMessage()).thenReturn(true);
        when(mFiraParams.hasRangingControlPhase()).thenReturn(false);
        when(mFiraParams.hasAngleOfArrivalAzimuthReport()).thenReturn(true);
        when(mFiraParams.hasAngleOfArrivalElevationReport()).thenReturn(true);
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                UwbTestUtils.generateRangingDataAndRangingReport(
                        PEER_SHORT_MAC_ADDRESS, MAC_ADDRESSING_MODE_SHORT,
                        RANGING_MEASUREMENT_TYPE_TWO_WAY,
                        true, false, true, true, TEST_ELAPSED_NANOS);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(testRangingDataAndRangingReport.first),
                eq(testRangingDataAndRangingReport.second.getMeasurements().get(0)));
    }

    @Test
    public void testOnRangingResult_forOwrAoa() throws Exception {
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                UwbTestUtils.generateRangingDataAndRangingReport(
                        PEER_SHORT_MAC_ADDRESS, MAC_ADDRESSING_MODE_SHORT,
                        RANGING_MEASUREMENT_TYPE_OWR_AOA,
                        true, true, false, false, TEST_ELAPSED_NANOS);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(testRangingDataAndRangingReport.first),
                eq(testRangingDataAndRangingReport.second.getMeasurements().get(0)));
    }

    @Test
    public void testOnRangingResult_forDlTDoA() throws Exception {
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                UwbTestUtils.generateRangingDataAndRangingReport(
                        PEER_SHORT_MAC_ADDRESS, MAC_ADDRESSING_MODE_SHORT,
                        RANGING_MEASUREMENT_TYPE_DL_TDOA,
                        true, true, false, false, TEST_ELAPSED_NANOS);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(testRangingDataAndRangingReport.first),
                eq(testRangingDataAndRangingReport.second.getMeasurements().get(0)));
    }

    @Test
    public void testOnRangingResult_badRangingDataForOwrAoa() throws Exception {
        UwbRangingData testRangingData = UwbTestUtils.generateBadOwrAoaMeasurementRangingData(
                MAC_ADDRESSING_MODE_SHORT, PEER_SHORT_MAC_ADDRESS);
        mUwbSessionNotificationManager.onRangingResult(mUwbSession, testRangingData);
        verifyZeroInteractions(mIUwbRangingCallbacks);
    }

    @Test
    public void testOnRangingOpened() throws Exception {
        mUwbSessionNotificationManager.onRangingOpened(mUwbSession);

        verify(mIUwbRangingCallbacks).onRangingOpened(mSessionHandle);
    }

    @Test
    public void testOnRangingOpenFailed() throws Exception {
        int status = UwbUciConstants.STATUS_CODE_ERROR_MAX_SESSIONS_EXCEEDED;
        mUwbSessionNotificationManager.onRangingOpenFailed(mUwbSession, status);

        verify(mIUwbRangingCallbacks).onRangingOpenFailed(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(status)),
                argThat(p -> (p.getInt("status_code")) == status));
    }

    @Test
    public void testOnRangingStarted() throws Exception {
        mUwbSessionNotificationManager.onRangingStarted(mUwbSession, mUwbSession.getParams());

        verify(mIUwbRangingCallbacks).onRangingStarted(mSessionHandle,
                mUwbSession.getParams().toBundle());
    }

    @Test
    public void testOnRangingStartFailed() throws Exception {
        int status =  UwbUciConstants.STATUS_CODE_INVALID_PARAM;
        mUwbSessionNotificationManager.onRangingStartFailed(mUwbSession, status);

        verify(mIUwbRangingCallbacks).onRangingStartFailed(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(status)),
                argThat(p -> (p.getInt("status_code")) == status));
    }

    @Test
    public void testOnRangingStartFailedWithUciReasonCode() throws Exception {
        int reasonCode =  UwbUciConstants.REASON_ERROR_SESSION_KEY_NOT_FOUND;
        mUwbSessionNotificationManager.onRangingStartFailedWithUciReasonCode(
                mUwbSession, reasonCode);

        int expectedStatusCode = UwbUciConstants.STATUS_CODE_ERROR_SESSION_NOT_EXIST;
        verify(mIUwbRangingCallbacks).onRangingStartFailed(eq(mSessionHandle),
                eq(RangingChangeReason.PROTOCOL_SPECIFIC),
                argThat(p -> (p.getInt("status_code")) == expectedStatusCode));
    }

    @Test
    public void testOnRangingStoppedWithUciReasonCode_reasonLocalApi() throws Exception {
        int reasonCode = UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS;
        mUwbSessionNotificationManager.onRangingStoppedWithUciReasonCode(mUwbSession, reasonCode);

        verify(mIUwbRangingCallbacks).onRangingStopped(
                eq(mSessionHandle), eq(RangingChangeReason.LOCAL_API),
                isA(PersistableBundle.class));
    }
    @Test
    public void testOnRangingStoppedWithUciReasonCode_reasonMaxRRRetryReached() throws Exception {
        int reasonCode = UwbUciConstants.REASON_MAX_RANGING_ROUND_RETRY_COUNT_REACHED;
        mUwbSessionNotificationManager.onRangingStoppedWithUciReasonCode(mUwbSession, reasonCode);

        verify(mIUwbRangingCallbacks).onRangingStopped(
                eq(mSessionHandle), eq(RangingChangeReason.MAX_RR_RETRY_REACHED),
                isA(PersistableBundle.class));
    }

    @Test
    public void testOnRangingStoppedWithUciReasonCode_reasonRemoteRequest() throws Exception {
        int reasonCode = UwbUciConstants.REASON_MAX_NUMBER_OF_MEASUREMENTS_REACHED;
        mUwbSessionNotificationManager.onRangingStoppedWithUciReasonCode(mUwbSession, reasonCode);

        verify(mIUwbRangingCallbacks).onRangingStopped(
                eq(mSessionHandle), eq(RangingChangeReason.REMOTE_REQUEST),
                isA(PersistableBundle.class));
    }

    @Test
    public void testOnRangingStoppedWithUciReasonCode_reasonBadParameters() throws Exception {
        Set<Integer> reasonCodes = Set.of(
                UwbUciConstants.REASON_ERROR_INSUFFICIENT_SLOTS_PER_RR,
                UwbUciConstants.REASON_ERROR_SLOT_LENGTH_NOT_SUPPORTED,
                UwbUciConstants.REASON_ERROR_INVALID_UL_TDOA_RANDOM_WINDOW,
                UwbUciConstants.REASON_ERROR_MAC_ADDRESS_MODE_NOT_SUPPORTED,
                UwbUciConstants.REASON_ERROR_INVALID_RANGING_INTERVAL,
                UwbUciConstants.REASON_ERROR_INVALID_STS_CONFIG,
                UwbUciConstants.REASON_ERROR_INVALID_RFRAME_CONFIG,
                UwbUciConstants.REASON_ERROR_HUS_NOT_ENOUGH_SLOTS,
                UwbUciConstants.REASON_ERROR_HUS_CFP_PHASE_TOO_SHORT,
                UwbUciConstants.REASON_ERROR_HUS_CAP_PHASE_TOO_SHORT,
                UwbUciConstants.REASON_ERROR_HUS_OTHERS);
        for (int reasonCode : reasonCodes) {
            clearInvocations(mIUwbRangingCallbacks);
            mUwbSessionNotificationManager.onRangingStoppedWithUciReasonCode(mUwbSession,
                    reasonCode);
            verify(mIUwbRangingCallbacks).onRangingStopped(
                    eq(mSessionHandle), eq(RangingChangeReason.BAD_PARAMETERS),
                    isA(PersistableBundle.class));
        }
    }

    @Test
    public void testOnRangingStoppedWithUciReasonCode_reasonSystemRegulation() throws Exception {
        int reasonCode = UwbUciConstants.REASON_REGULATION_UWB_OFF;
        mUwbSessionNotificationManager.onRangingStoppedWithUciReasonCode(mUwbSession, reasonCode);

        verify(mIUwbRangingCallbacks).onRangingStopped(
                eq(mSessionHandle), eq(RangingChangeReason.SYSTEM_REGULATION),
                isA(PersistableBundle.class));
    }

    @Test
    public void  testOnRangingStoppedWithApiReasonCode() throws Exception {
        mUwbSessionNotificationManager.onRangingStoppedWithApiReasonCode(
                mUwbSession, RangingChangeReason.SYSTEM_POLICY, new PersistableBundle());

        verify(mIUwbRangingCallbacks).onRangingStopped(
                eq(mSessionHandle), eq(RangingChangeReason.SYSTEM_POLICY),
                isA(PersistableBundle.class));
    }

    @Test
    public void testOnRangingStopped() throws Exception {
        int status = UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS;
        mUwbSessionNotificationManager.onRangingStopped(mUwbSession, status);

        verify(mIUwbRangingCallbacks).onRangingStopped(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(status)),
                argThat(p-> p.getInt("status_code") == status));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REASON_INBAND_SESSION_STOP)
    public void testRangingStoppedDuetoInbandSignal() throws Exception {
        mUwbSessionNotificationManager.onRangingStoppedWithApiReasonCode(mUwbSession,
                RangingChangeReason.INBAND_SESSION_STOP, new PersistableBundle());

        verify(mIUwbRangingCallbacks).onRangingStopped(
                eq(mSessionHandle), eq(RangingChangeReason.INBAND_SESSION_STOP),
                isA(PersistableBundle.class));
    }

    @Test
    public void testOnRangingStopFailed() throws Exception {
        int status = UwbUciConstants.STATUS_CODE_INVALID_RANGE;
        mUwbSessionNotificationManager.onRangingStopFailed(mUwbSession, status);

        verify(mIUwbRangingCallbacks).onRangingStopFailed(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(status)),
                argThat(p -> (p.getInt("status_code")) == status));
    }

    @Test
    public void testOnRangingReconfigured() throws Exception {
        mUwbSessionNotificationManager.onRangingReconfigured(mUwbSession);

        verify(mIUwbRangingCallbacks).onRangingReconfigured(eq(mSessionHandle), any());
    }

    @Test
    public void testOnRangingReconfigureFailed() throws Exception {
        int status =  UwbUciConstants.STATUS_CODE_INVALID_MESSAGE_SIZE;
        mUwbSessionNotificationManager.onRangingReconfigureFailed(mUwbSession, status);

        verify(mIUwbRangingCallbacks).onRangingReconfigureFailed(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(status)),
                argThat(p -> (p.getInt("status_code")) == status));
    }

    @Test
    public void testOnControleeAdded() throws Exception {
        mUwbSessionNotificationManager.onControleeAdded(mUwbSession);

        verify(mIUwbRangingCallbacks).onControleeAdded(eq(mSessionHandle), any());
    }

    @Test
    public void testOnControleeAddFailed() throws Exception {
        int status =  UwbUciConstants.STATUS_CODE_INVALID_MESSAGE_SIZE;
        mUwbSessionNotificationManager.onControleeAddFailed(mUwbSession, status);

        verify(mIUwbRangingCallbacks).onControleeAddFailed(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(status)),
                argThat(p -> (p.getInt("status_code")) == status));
    }

    @Test
    public void testOnControleeRemoved() throws Exception {
        mUwbSessionNotificationManager.onControleeRemoved(mUwbSession);

        verify(mIUwbRangingCallbacks).onControleeRemoved(eq(mSessionHandle), any());
    }

    @Test
    public void testOnControleeRemoveFailed() throws Exception {
        int status =  UwbUciConstants.STATUS_CODE_INVALID_MESSAGE_SIZE;
        mUwbSessionNotificationManager.onControleeRemoveFailed(mUwbSession, status);

        verify(mIUwbRangingCallbacks).onControleeRemoveFailed(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(status)),
                argThat(p -> (p.getInt("status_code")) == status));
    }

    @Test
    public void testOnRangingClosed() throws Exception {
        int status = UwbUciConstants.REASON_ERROR_SLOT_LENGTH_NOT_SUPPORTED;
        mUwbSessionNotificationManager.onRangingClosed(mUwbSession, status);

        verify(mIUwbRangingCallbacks).onRangingClosed(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(status)),
                argThat(p-> p.getInt("status_code") == status));
    }

    @Test
    public void testOnRangingClosedWithReasonCode() throws Exception {
        int reasonCode = RangingChangeReason.SYSTEM_POLICY;
        mUwbSessionNotificationManager.onRangingClosedWithApiReasonCode(mUwbSession, reasonCode);

        verify(mIUwbRangingCallbacks).onRangingClosed(eq(mSessionHandle),
                eq(reasonCode),
                argThat(p-> p.isEmpty()));
    }

    @Test
    public void testOnDataReceived() throws Exception {
        mUwbSessionNotificationManager.onDataReceived(mUwbSession, PEER_EXTENDED_UWB_ADDRESS,
                PERSISTABLE_BUNDLE, DATA_PAYLOAD);

        verify(mIUwbRangingCallbacks).onDataReceived(eq(mSessionHandle), eq(
                        PEER_EXTENDED_UWB_ADDRESS),
                eq(PERSISTABLE_BUNDLE), eq(DATA_PAYLOAD));
    }

    @Test
    public void testOnDataReceiveFailed() throws Exception {
        mUwbSessionNotificationManager.onDataReceiveFailed(mUwbSession, PEER_EXTENDED_UWB_ADDRESS,
                STATUS_CODE_FAILED, PERSISTABLE_BUNDLE);

        verify(mIUwbRangingCallbacks).onDataReceiveFailed(eq(mSessionHandle), eq(
                        PEER_EXTENDED_UWB_ADDRESS),
                eq(STATUS_CODE_FAILED), eq(PERSISTABLE_BUNDLE));
    }

    @Test
    public void testOnDataSent() throws Exception {
        mUwbSessionNotificationManager.onDataSent(mUwbSession, PEER_EXTENDED_UWB_ADDRESS,
                PERSISTABLE_BUNDLE);

        verify(mIUwbRangingCallbacks).onDataSent(eq(mSessionHandle), eq(PEER_EXTENDED_UWB_ADDRESS),
                eq(PERSISTABLE_BUNDLE));
    }

    @Test
    public void testOnDataSendFailed() throws Exception {
        mUwbSessionNotificationManager.onDataSendFailed(mUwbSession, PEER_EXTENDED_UWB_ADDRESS,
                STATUS_CODE_FAILED, PERSISTABLE_BUNDLE);

        verify(mIUwbRangingCallbacks).onDataSendFailed(eq(mSessionHandle), eq(
                        PEER_EXTENDED_UWB_ADDRESS),
                eq(STATUS_CODE_FAILED), eq(PERSISTABLE_BUNDLE));
    }

    @Test
    public void testOnDataTransferPhaseConfigured() throws Exception {
        mUwbSessionNotificationManager.onDataTransferPhaseConfigured(mUwbSession,
                PERSISTABLE_BUNDLE);

        verify(mIUwbRangingCallbacks).onDataTransferPhaseConfigured(eq(mSessionHandle),
                eq(PERSISTABLE_BUNDLE));
    }

    @Test
    public void testOnDataTransferPhaseConfigFailed() throws Exception {
        mUwbSessionNotificationManager.onDataTransferPhaseConfigFailed(mUwbSession,
                PERSISTABLE_BUNDLE);

        verify(mIUwbRangingCallbacks).onDataTransferPhaseConfigFailed(eq(mSessionHandle),
                eq(PERSISTABLE_BUNDLE));
    }

    @Test
    public void testOnRangingRoundsUpdateStatus() throws RemoteException {
        PersistableBundle bundle = new PersistableBundle();
        mUwbSessionNotificationManager.onRangingRoundsUpdateStatus(mUwbSession, bundle);

        verify(mIUwbRangingCallbacks).onRangingRoundsUpdateDtTagStatus(eq(mSessionHandle),
                eq(bundle));
    }

    @Test
    public void testonRadarDataMessageReceivedWithoutUwbRangingPermission() throws Exception {
        Pair<UwbRadarData, RadarData> testUwbRadarDataAndRadarData =
                UwbTestUtils.generateUwbRadarDataAndRadarData(
                        RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES);
        when(mUwbInjector.checkUwbRangingPermissionForStartDataDelivery(eq(ATTRIBUTION_SOURCE),
                any())).thenReturn(false);
        when(mUwbRadarSession.isDataDeliveryPermissionCheckNeeded()).thenReturn(true);
        mUwbSessionNotificationManager.onRadarDataMessageReceived(
                mUwbRadarSession, testUwbRadarDataAndRadarData.first);

        verify(mIUwbRangingCallbacks, never()).onDataReceived(any(), any(), any(), any());
    }

    @Test
    public void testonRadarDataMessageReceived_forRadarSweepData() throws Exception {
        Pair<UwbRadarData, RadarData> testUwbRadarDataAndRadarData =
                UwbTestUtils.generateUwbRadarDataAndRadarData(
                        RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES);
        mUwbSessionNotificationManager.onRadarDataMessageReceived(
                mUwbRadarSession, testUwbRadarDataAndRadarData.first);

        verify(mIUwbRangingCallbacks).onDataReceived(
                eq(mSessionHandle),
                eq(null),
                argThat(p -> p.getInt("sweep_offset")
                        == testUwbRadarDataAndRadarData.second.getSweepOffset()),
                eq(new byte[] {}));
    }
}
