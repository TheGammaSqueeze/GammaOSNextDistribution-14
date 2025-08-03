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

package com.android.telephony.qns;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.net.NetworkCapabilities;
import android.telephony.AccessNetworkConstants;
import android.telephony.qns.QnsProtoEnums;

import com.android.telephony.qns.DataConnectionStatusTracker.DataConnectionChangedInfo;
import com.android.telephony.qns.QualifiedNetworksServiceImpl.QualifiedNetworksInfo;
import com.android.telephony.qns.atoms.AtomsQnsFallbackRestrictionChangedInfo;
import com.android.telephony.qns.atoms.AtomsQnsHandoverPingPongInfo;
import com.android.telephony.qns.atoms.AtomsQnsHandoverTimeMillisInfo;
import com.android.telephony.qns.atoms.AtomsQnsImsCallDropStats;
import com.android.telephony.qns.atoms.AtomsQnsRatPreferenceMismatchInfo;
import com.android.telephony.qns.atoms.AtomsQualifiedRatListChangedInfo;
import com.android.telephony.statslib.AtomsPulled;
import com.android.telephony.statslib.AtomsPushed;
import com.android.telephony.statslib.StatsLib;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;

public class QnsMetricsTest extends QnsTest {
    private static final int DEFAULT_CARRIER_ID = 1;

    private final int mSlotId = 0;
    private final int mNetCapability = NetworkCapabilities.NET_CAPABILITY_IMS;

    @Mock private StatsLib mMockStatsLib;
    @Mock private RestrictManager mMockRestrictManager;
    @Mock private QualityMonitor mMockCellularQualityMonitor;
    @Mock private QualityMonitor mMockWifiQualityMonitor;

    private MockitoSession mStaticMockSession;
    private QnsMetrics mQnsMetrics;
    private long mSystemElapsedRealTime;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession = mockitoSession().mockStatic(QnsUtils.class).startMocking();

        mSystemElapsedRealTime = 0L;
        lenient()
                .when(QnsUtils.getSystemElapsedRealTime())
                .thenAnswer((Answer<Long>) invocation -> mSystemElapsedRealTime);

        mQnsMetrics = new QnsMetrics(mMockStatsLib);
    }

    @After
    public void cleanUp() {
        mStaticMockSession.finishMocking();
    }

    private void spendSystemTime(long appendElapsedTime) {
        mSystemElapsedRealTime += appendElapsedTime;
    }

    private void sendDataConnectionMessage(int event, int state, int transportType) {
        DataConnectionChangedInfo info = new DataConnectionChangedInfo(event, state, transportType);
        mQnsMetrics.log("QnsMetricsTest currentTime:" + mSystemElapsedRealTime + "ms " + info);
        mQnsMetrics.reportAtomForDataConnectionChanged(
                mNetCapability, mSlotId, info, DEFAULT_CARRIER_ID);
        waitForLastHandlerAction(mQnsMetrics.getHandler());
    }

    private void sendQualifiedNetworksMessage(int accessNetworkType) {

        List<Integer> list = new ArrayList<>();
        list.add(accessNetworkType);

        sendQualifiedNetworksMessage(
                list,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                QnsConstants.COVERAGE_HOME,
                true,
                false,
                QnsConstants.CELL_PREF,
                QnsConstants.WIFI_PREF,
                AccessNetworkConstants.AccessNetworkType.EUTRAN,
                true,
                false,
                mMockRestrictManager,
                mMockCellularQualityMonitor,
                mMockWifiQualityMonitor,
                QnsConstants.CALL_TYPE_IDLE);
    }

    private void sendQualifiedNetworksMessage(
            List<Integer> accessNetworkTypes,
            int dataConnectionCurrentTransportType,
            int coverage,
            boolean settingWfcEnabled,
            boolean settingWfcRoamingEnabled,
            int settingWfcMode,
            int settingWfcRoamingMode,
            int cellularAccessNetworkType,
            boolean iwlanAvailable,
            boolean isCrossWfc,
            RestrictManager restrictManager,
            QualityMonitor cellularQualityMonitor,
            QualityMonitor wifiQualityMonitor,
            int callType) {
        QualifiedNetworksInfo info = new QualifiedNetworksInfo(
                NetworkCapabilities.NET_CAPABILITY_IMS, accessNetworkTypes);

        mQnsMetrics.log("QnsMetricsTest currentTime:" + mSystemElapsedRealTime + "ms " + info);
        mQnsMetrics.reportAtomForQualifiedNetworks(
                info,
                mSlotId,
                dataConnectionCurrentTransportType,
                coverage,
                settingWfcEnabled,
                settingWfcRoamingEnabled,
                settingWfcMode,
                settingWfcRoamingMode,
                cellularAccessNetworkType,
                iwlanAvailable,
                isCrossWfc,
                restrictManager,
                cellularQualityMonitor,
                wifiQualityMonitor,
                callType);
        waitForLastHandlerAction(mQnsMetrics.getHandler());
    }

    @Test
    public void testAtomsQnsHandoverTimeMillisInfo() {

        ArgumentCaptor<AtomsPulled> capturePulled = ArgumentCaptor.forClass(AtomsPulled.class);
        ArgumentCaptor<AtomsPushed> capturePushed = ArgumentCaptor.forClass(AtomsPushed.class);

        spendSystemTime(10000L);
        sendQualifiedNetworksMessage(AccessNetworkConstants.AccessNetworkType.EUTRAN);

        spendSystemTime(4010L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_STARTED,
                DataConnectionStatusTracker.STATE_CONNECTING,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4020L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_CONNECTED,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(501L);
        sendQualifiedNetworksMessage(AccessNetworkConstants.AccessNetworkType.IWLAN);

        spendSystemTime(4030L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED,
                DataConnectionStatusTracker.STATE_HANDOVER,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4040L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_SUCCESS,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        spendSystemTime(5002L);
        sendQualifiedNetworksMessage(AccessNetworkConstants.AccessNetworkType.EUTRAN);

        spendSystemTime(4050L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED,
                DataConnectionStatusTracker.STATE_HANDOVER,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        spendSystemTime(4060L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_SUCCESS,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(50002L);
        sendQualifiedNetworksMessage(AccessNetworkConstants.AccessNetworkType.IWLAN);

        spendSystemTime(4070L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED,
                DataConnectionStatusTracker.STATE_HANDOVER,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4080L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_FAILED,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4090L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED,
                DataConnectionStatusTracker.STATE_HANDOVER,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4100L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_FAILED,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4110L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED,
                DataConnectionStatusTracker.STATE_HANDOVER,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4120L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_SUCCESS,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        spendSystemTime(4130L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_DISCONNECTED,
                DataConnectionStatusTracker.STATE_INACTIVE,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        verify(mMockStatsLib, timeout(1000).times(4)).append(capturePulled.capture());
        verify(mMockStatsLib, timeout(1000).times(4)).write(capturePushed.capture());
        List<AtomsQnsHandoverTimeMillisInfo> listHandoverTime = new ArrayList<>();
        List<AtomsQualifiedRatListChangedInfo> listQualifiedRat = new ArrayList<>();
        for (AtomsPulled pulled : capturePulled.getAllValues()) {
            if (pulled instanceof AtomsQnsHandoverTimeMillisInfo) {
                listHandoverTime.add((AtomsQnsHandoverTimeMillisInfo) pulled);
                mQnsMetrics.log("QnsMetricsTest HandoverTime atom:" + pulled);
            }
        }
        for (AtomsPushed pushed : capturePushed.getAllValues()) {
            if (pushed instanceof AtomsQualifiedRatListChangedInfo) {
                listQualifiedRat.add((AtomsQualifiedRatListChangedInfo) pushed);
                mQnsMetrics.log("QnsMetricsTest QualifiedRat atom:" + pushed);
            }
        }
        assertEquals(3, listHandoverTime.size());
        assertEquals(8070, listHandoverTime.get(0).getTimeForHoSuccess());
        assertEquals(8110, listHandoverTime.get(1).getTimeForHoSuccess());
        assertEquals(24570, listHandoverTime.get(2).getTimeForHoSuccess());

        assertEquals(
                AccessNetworkConstants.AccessNetworkType.EUTRAN,
                listQualifiedRat.get(0).getFirstQualifiedRat());
        assertEquals(
                AccessNetworkConstants.AccessNetworkType.IWLAN,
                listQualifiedRat.get(1).getFirstQualifiedRat());
        assertEquals(
                AccessNetworkConstants.AccessNetworkType.EUTRAN,
                listQualifiedRat.get(2).getFirstQualifiedRat());
        assertEquals(
                AccessNetworkConstants.AccessNetworkType.IWLAN,
                listQualifiedRat.get(3).getFirstQualifiedRat());
    }

    @Test
    public void testAtomsQualifiedRatListChangedInfo() {

        ArgumentCaptor<AtomsQualifiedRatListChangedInfo> capture =
                ArgumentCaptor.forClass(AtomsQualifiedRatListChangedInfo.class);

        doReturn(true)
                .when(mMockRestrictManager)
                .hasRestrictionType(anyInt(), eq(RestrictManager.RESTRICT_TYPE_RTP_LOW_QUALITY));
        doReturn(true)
                .when(mMockRestrictManager)
                .hasRestrictionType(
                        anyInt(), eq(RestrictManager.RESTRICT_TYPE_FALLBACK_TO_WWAN_IMS_REGI_FAIL));
        doReturn(true)
                .when(mMockRestrictManager)
                .hasRestrictionType(
                        anyInt(),
                        eq(RestrictManager.RESTRICT_TYPE_FALLBACK_TO_WWAN_RTT_BACKHAUL_FAIL));

        List<Integer> cellularAccessNetwork = new ArrayList<>();
        cellularAccessNetwork.add(AccessNetworkConstants.AccessNetworkType.EUTRAN);
        sendQualifiedNetworksMessage(
                cellularAccessNetwork,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                QnsConstants.COVERAGE_HOME,
                true,
                false,
                QnsConstants.CELL_PREF,
                QnsConstants.WIFI_PREF,
                AccessNetworkConstants.AccessNetworkType.EUTRAN,
                false,
                true,
                mMockRestrictManager,
                mMockCellularQualityMonitor,
                mMockWifiQualityMonitor,
                QnsConstants.CALL_TYPE_IDLE);

        List<Integer> wifiAccessNetwork = new ArrayList<>();
        wifiAccessNetwork.add(AccessNetworkConstants.AccessNetworkType.IWLAN);
        sendQualifiedNetworksMessage(
                wifiAccessNetwork,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                QnsConstants.COVERAGE_ROAM,
                false,
                true,
                QnsConstants.WIFI_PREF,
                QnsConstants.CELL_PREF,
                AccessNetworkConstants.AccessNetworkType.NGRAN,
                true,
                false,
                mMockRestrictManager,
                mMockCellularQualityMonitor,
                mMockWifiQualityMonitor,
                QnsConstants.CALL_TYPE_VOICE);

        verify(mMockStatsLib, timeout(1000).times(2)).write(capture.capture());
        List<AtomsQualifiedRatListChangedInfo> list = capture.getAllValues();
        mQnsMetrics.log("QnsMetricsTest QualifiedRat atom[0]:" + list.get(0));
        mQnsMetrics.log("QnsMetricsTest QualifiedRat atom[1]:" + list.get(1));
        assertEquals(
                AccessNetworkConstants.AccessNetworkType.EUTRAN,
                list.get(0).getFirstQualifiedRat());
        assertEquals(
                AccessNetworkConstants.AccessNetworkType.IWLAN, list.get(1).getFirstQualifiedRat());
        int expectedRestriction =
                QnsProtoEnums.RESTRICT_TYPE_RTP_LOW_QUALITY
                        | QnsProtoEnums.RESTRICT_TYPE_FALLBACK_TO_WWAN_IMS_REGI_FAIL
                        | QnsProtoEnums.RESTRICT_TYPE_FALLBACK_TO_WWAN_RTT_BACKHAUL_FAIL;
        assertEquals(expectedRestriction, list.get(0).getRestrictionsOnWlan());
        assertEquals(expectedRestriction, list.get(1).getRestrictionsOnWwan());
    }

    @Test
    public void testAtomsQnsHandoverPingPongInfo() {

        ArgumentCaptor<AtomsQnsHandoverPingPongInfo> capture =
                ArgumentCaptor.forClass(AtomsQnsHandoverPingPongInfo.class);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_STARTED,
                DataConnectionStatusTracker.STATE_CONNECTING,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_CONNECTED,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED,
                DataConnectionStatusTracker.STATE_HANDOVER,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_SUCCESS,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED,
                DataConnectionStatusTracker.STATE_HANDOVER,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_SUCCESS,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED,
                DataConnectionStatusTracker.STATE_HANDOVER,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_SUCCESS,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED,
                DataConnectionStatusTracker.STATE_HANDOVER,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_SUCCESS,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED,
                DataConnectionStatusTracker.STATE_HANDOVER,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(50000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_SUCCESS,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED,
                DataConnectionStatusTracker.STATE_HANDOVER,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_SUCCESS,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_DISCONNECTED,
                DataConnectionStatusTracker.STATE_INACTIVE,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        verify(mMockStatsLib, timeout(1000).times(2)).append(capture.capture());
        List<AtomsQnsHandoverPingPongInfo> list = capture.getAllValues();
        mQnsMetrics.log("QnsMetricsTest PingPong atom[0]:" + list.get(0));
        mQnsMetrics.log("QnsMetricsTest PingPong atom[1]:" + list.get(1));
        assertEquals(2, list.get(0).getCountHandoverPingPong());
        assertEquals(1, list.get(1).getCountHandoverPingPong());
    }

    @Test
    public void testAtomsQnsRatPreferenceMismatchInfo() {

        ArgumentCaptor<AtomsQnsRatPreferenceMismatchInfo> capture =
                ArgumentCaptor.forClass(AtomsQnsRatPreferenceMismatchInfo.class);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_STARTED,
                DataConnectionStatusTracker.STATE_CONNECTING,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_CONNECTED,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED,
                DataConnectionStatusTracker.STATE_HANDOVER,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4100L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_FAILED,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED,
                DataConnectionStatusTracker.STATE_HANDOVER,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4200L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_FAILED,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED,
                DataConnectionStatusTracker.STATE_HANDOVER,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_DISCONNECTED,
                DataConnectionStatusTracker.STATE_INACTIVE,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(50000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_STARTED,
                DataConnectionStatusTracker.STATE_CONNECTING,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_CONNECTED,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED,
                DataConnectionStatusTracker.STATE_HANDOVER,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_SUCCESS,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED,
                DataConnectionStatusTracker.STATE_HANDOVER,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        spendSystemTime(4100L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_FAILED,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED,
                DataConnectionStatusTracker.STATE_HANDOVER,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        spendSystemTime(4200L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_FAILED,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_STARTED,
                DataConnectionStatusTracker.STATE_HANDOVER,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        spendSystemTime(4300L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_HANDOVER_FAILED,
                DataConnectionStatusTracker.STATE_CONNECTED,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        spendSystemTime(4000L);
        sendDataConnectionMessage(
                DataConnectionStatusTracker.EVENT_DATA_CONNECTION_DISCONNECTED,
                DataConnectionStatusTracker.STATE_INACTIVE,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        verify(mMockStatsLib, timeout(1000).times(2)).append(capture.capture());
        List<AtomsQnsRatPreferenceMismatchInfo> list = capture.getAllValues();
        mQnsMetrics.log("QnsMetricsTest RatMismatch atom[0]:" + list.get(0));
        mQnsMetrics.log("QnsMetricsTest RatMismatch atom[1]:" + list.get(1));
        assertEquals(2, list.get(0).getHandoverFailCount());
        assertEquals(3, list.get(1).getHandoverFailCount());
        assertEquals(20300, list.get(0).getDurationOfMismatch());
        assertEquals(24600, list.get(1).getDurationOfMismatch());
    }

    private void sendCallTypeChanged(
            int oldCallType,
            int newCallType,
            RestrictManager restrictManager,
            int transportTypeOfCall) {
        mQnsMetrics.log(
                "QnsMetricsTest callTypeChanged "
                        + QnsConstants.callTypeToString(oldCallType)
                        + "->"
                        + QnsConstants.callTypeToString(newCallType)
                        + " transportType:"
                        + AccessNetworkConstants.transportTypeToString(transportTypeOfCall));
        mQnsMetrics.reportAtomForCallTypeChanged(mNetCapability, mSlotId,
                oldCallType, newCallType, restrictManager, transportTypeOfCall);
        waitForLastHandlerAction(mQnsMetrics.getHandler());
    }

    private void sendImsCallDropStats(int transportTypeOfCall, int cellularAccessNetworkType) {
        mQnsMetrics.log(
                "QnsMetricsTest ImsCallDropStats transportTypeOfCall:"
                        + AccessNetworkConstants.transportTypeToString(transportTypeOfCall)
                        + " cellularAccessNetworkType:"
                        + AccessNetworkConstants.AccessNetworkType.toString(
                                cellularAccessNetworkType));
        mQnsMetrics.reportAtomForImsCallDropStats(mNetCapability, mSlotId, mMockRestrictManager,
                mMockCellularQualityMonitor, mMockWifiQualityMonitor, transportTypeOfCall,
                cellularAccessNetworkType);
        waitForLastHandlerAction(mQnsMetrics.getHandler());
    }
    private void sendFallbackRestrictionChanged(
            List<Integer> wlanRestrictions, List<Integer> wwanRestrictions) {
        mQnsMetrics.log(
                "QnsMetricsTest FallbackRestrictionChanged wlanRestrictions:"
                        + wlanRestrictions
                        + ", wwanRestrictions:"
                        + wwanRestrictions);
        mQnsMetrics.reportAtomForRestrictions(
                mNetCapability, mSlotId, wlanRestrictions, wwanRestrictions, DEFAULT_CARRIER_ID);
        waitForLastHandlerAction(mQnsMetrics.getHandler());
    }

    @Test
    public void testAtomsQnsImsCallDropStats() {

        ArgumentCaptor<AtomsQnsImsCallDropStats> capture =
                ArgumentCaptor.forClass(AtomsQnsImsCallDropStats.class);

        // call start
        sendCallTypeChanged(
                QnsConstants.CALL_TYPE_IDLE,
                QnsConstants.CALL_TYPE_VOICE,
                mMockRestrictManager,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        // got rtp low from wwan side
        spendSystemTime(10000L);
        doReturn(true)
                .when(mMockRestrictManager)
                .hasRestrictionType(
                        eq(AccessNetworkConstants.TRANSPORT_TYPE_WWAN),
                        eq(RestrictManager.RESTRICT_TYPE_RTP_LOW_QUALITY));

        // call end
        spendSystemTime(3000L);
        sendCallTypeChanged(
                QnsConstants.CALL_TYPE_VOICE,
                QnsConstants.CALL_TYPE_IDLE,
                mMockRestrictManager,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        // media no data cause.
        spendSystemTime(3000L);
        sendImsCallDropStats(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                AccessNetworkConstants.AccessNetworkType.EUTRAN);

        // call start over iwlan
        spendSystemTime(10000L);
        sendCallTypeChanged(
                QnsConstants.CALL_TYPE_IDLE,
                QnsConstants.CALL_TYPE_VOICE,
                mMockRestrictManager,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        // got rtp low from wlan side
        spendSystemTime(10000L);
        doReturn(true)
                .when(mMockRestrictManager)
                .hasRestrictionType(
                        eq(AccessNetworkConstants.TRANSPORT_TYPE_WLAN),
                        eq(RestrictManager.RESTRICT_TYPE_RTP_LOW_QUALITY));

        // call end
        spendSystemTime(3000L);
        sendCallTypeChanged(
                QnsConstants.CALL_TYPE_VOICE,
                QnsConstants.CALL_TYPE_IDLE,
                mMockRestrictManager,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        // media no data cause
        spendSystemTime(3000L);
        sendImsCallDropStats(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                AccessNetworkConstants.AccessNetworkType.IWLAN);

        // verify
        verify(mMockStatsLib, timeout(1000).times(2)).write(capture.capture());
        List<AtomsQnsImsCallDropStats> list = capture.getAllValues();
        mQnsMetrics.log("QnsMetricsTest ImsCallDrop atom[0]:" + list.get(0));
        mQnsMetrics.log("QnsMetricsTest ImsCallDrop atom[1]:" + list.get(1));
        assertTrue(list.get(0).getRtpThresholdBreached());
        assertTrue(list.get(1).getRtpThresholdBreached());
    }


    @Test
    public void testAtomsQnsFallbackRestrictionChangedInfo() {

        ArgumentCaptor<AtomsQnsFallbackRestrictionChangedInfo> capture =
                ArgumentCaptor.forClass(AtomsQnsFallbackRestrictionChangedInfo.class);

        List<Integer> wlanRestrictions = new ArrayList<>();
        List<Integer> wwanRestrictions = new ArrayList<>();
        sendFallbackRestrictionChanged(wlanRestrictions, wwanRestrictions);

        wlanRestrictions.add(RestrictManager.RESTRICT_TYPE_GUARDING);
        wwanRestrictions.add(RestrictManager.RESTRICT_TYPE_GUARDING);
        sendFallbackRestrictionChanged(wlanRestrictions, wwanRestrictions);

        wlanRestrictions.clear();
        wwanRestrictions.clear();
        wlanRestrictions.add(RestrictManager.RESTRICT_TYPE_RTP_LOW_QUALITY);
        sendFallbackRestrictionChanged(wlanRestrictions, wwanRestrictions);

        wlanRestrictions.clear();
        wwanRestrictions.clear();
        wwanRestrictions.add(RestrictManager.RESTRICT_TYPE_RTP_LOW_QUALITY);
        sendFallbackRestrictionChanged(wlanRestrictions, wwanRestrictions);

        wlanRestrictions.clear();
        wwanRestrictions.clear();
        wlanRestrictions.add(RestrictManager.RESTRICT_TYPE_FALLBACK_TO_WWAN_IMS_REGI_FAIL);
        sendFallbackRestrictionChanged(wlanRestrictions, wwanRestrictions);

        wlanRestrictions.clear();
        wwanRestrictions.clear();
        wwanRestrictions.add(RestrictManager.RESTRICT_TYPE_FALLBACK_TO_WWAN_IMS_REGI_FAIL);
        sendFallbackRestrictionChanged(wlanRestrictions, wwanRestrictions);

        wlanRestrictions.clear();
        wwanRestrictions.clear();
        wlanRestrictions.add(RestrictManager.RESTRICT_TYPE_FALLBACK_TO_WWAN_RTT_BACKHAUL_FAIL);
        sendFallbackRestrictionChanged(wlanRestrictions, wwanRestrictions);

        wlanRestrictions.clear();
        wwanRestrictions.clear();
        wwanRestrictions.add(RestrictManager.RESTRICT_TYPE_FALLBACK_TO_WWAN_RTT_BACKHAUL_FAIL);
        sendFallbackRestrictionChanged(wlanRestrictions, wwanRestrictions);

        // verify
        verify(mMockStatsLib, timeout(1000).times(4)).write(capture.capture());
        List<AtomsQnsFallbackRestrictionChangedInfo> list = capture.getAllValues();
        mQnsMetrics.log("QnsMetricsTest ImsCallDrop atom[0]:" + list.get(0));
        mQnsMetrics.log("QnsMetricsTest ImsCallDrop atom[1]:" + list.get(1));
        mQnsMetrics.log("QnsMetricsTest ImsCallDrop atom[2]:" + list.get(2));
        mQnsMetrics.log("QnsMetricsTest ImsCallDrop atom[3]:" + list.get(3));
        assertTrue(list.get(0).getRestrictionOnWlanByRtpThresholdBreached());
        assertTrue(list.get(1).getRestrictionOnWwanByRtpThresholdBreached());
        assertTrue(list.get(2).getRestrictionOnWlanByImsRegistrationFailed());
        assertTrue(list.get(3).getRestrictionOnWlanByWifiBackhaulProblem());
    }
}
