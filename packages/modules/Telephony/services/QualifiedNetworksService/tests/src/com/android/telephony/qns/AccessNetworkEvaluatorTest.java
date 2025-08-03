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

package com.android.telephony.qns;

import static com.android.telephony.qns.DataConnectionStatusTracker.EVENT_DATA_CONNECTION_DISCONNECTED;
import static com.android.telephony.qns.DataConnectionStatusTracker.EVENT_DATA_CONNECTION_FAILED;
import static com.android.telephony.qns.DataConnectionStatusTracker.STATE_INACTIVE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.ServiceState;
import android.telephony.SignalThresholdInfo;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ProvisioningManager;

import com.android.telephony.qns.AccessNetworkSelectionPolicy.PreCondition;
import com.android.telephony.qns.QnsProvisioningListener.QnsProvisioningInfo;
import com.android.telephony.qns.QnsTelephonyListener.QnsTelephonyInfoIms;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@RunWith(JUnit4.class)
public class AccessNetworkEvaluatorTest extends QnsTest {
    private static final String TEST_PLMN = "707333";
    private static final int QUALIFIED_NETWORKS_CHANGED = 2;

    private static final int EVENT_BASE = 10000;
    private static final int EVENT_IWLAN_NETWORK_STATUS_CHANGED = EVENT_BASE;
    private static final int EVENT_QNS_TELEPHONY_INFO_CHANGED = EVENT_BASE + 1;
    private static final int EVENT_SET_CALL_TYPE = EVENT_BASE + 5;
    private static final int EVENT_EMERGENCY_PREFERRED_TRANSPORT_TYPE_CHANGED = EVENT_BASE + 7;
    private static final int EVENT_PROVISIONING_INFO_CHANGED = EVENT_BASE + 8;
    private static final int EVENT_WFC_ACTIVATION_WITH_IWLAN_CONNECTION_REQUIRED = EVENT_BASE + 9;
    private static final int EVENT_IMS_REGISTRATION_STATE_CHANGED = EVENT_BASE + 10;
    private static final int EVENT_SIP_DIALOG_SESSION_STATE_CHANGED = EVENT_BASE + 12;
    private static final int EVENT_IMS_CALL_DISCONNECT_CAUSE_CHANGED = EVENT_BASE + 13;

    @Mock private RestrictManager mRestrictManager;
    @Mock private DataConnectionStatusTracker mDataConnectionStatusTracker;
    @Mock private QnsImsManager.ImsRegistrationState mMockImsRegistrationState;
    private AccessNetworkEvaluator mAne;
    private Handler mHandler;
    private QualifiedNetworksServiceImpl.QualifiedNetworksInfo mQualifiedNetworksInfo;
    private Map<PreCondition, List<AccessNetworkSelectionPolicy>> mTestAnspPolicyMap = null;
    private CountDownLatch mLatch;
    private int mSlotIndex = 0;
    private int mNetCapability = NetworkCapabilities.NET_CAPABILITY_IMS;
    private HandlerThread mHandlerThread;
    private int mRatPreference = QnsConstants.RAT_PREFERENCE_DEFAULT;

    private boolean mWfcEnabledByUser = true;
    private boolean mWfcRoamingEnabledByUser = false;
    private boolean mWfcEnabledByPlatform = true;
    private boolean mWfcProvisioned = true;
    private boolean mCrossSimEnabled = false;
    private boolean mWfcRoamingEnabled = true;
    private int mWfcMode = QnsConstants.WIFI_PREF;
    private int mWfcModeRoaming = QnsConstants.WIFI_PREF;

    private class AneHandler extends Handler {
        AneHandler() {
            super(mHandlerThread.getLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case QUALIFIED_NETWORKS_CHANGED:
                    QnsAsyncResult ar = (QnsAsyncResult) msg.obj;
                    mQualifiedNetworksInfo =
                            (QualifiedNetworksServiceImpl.QualifiedNetworksInfo) ar.mResult;
                    mLatch.countDown();
                    break;
                default:
                    break;
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        super.setUp();
        mHandlerThread = new HandlerThread("");
        mLatch = new CountDownLatch(1);
        stubQnsDefaultWfcSettings();
        mAne =
                new AccessNetworkEvaluator(
                        mQnsComponents[mSlotIndex],
                        mNetCapability,
                        mRestrictManager,
                        mDataConnectionStatusTracker,
                        mSlotIndex);
        mHandlerThread.start();
        mHandler = new AneHandler();
        // wait for AccessNetworkSelectionPolicy to build
        waitForLastHandlerAction(mAne.mHandler);
    }

    private void stubQnsDefaultWfcSettings() {
        when(mMockQnsImsManager.isWfcEnabledByPlatform()).thenAnswer(i -> mWfcEnabledByPlatform);
        when(mMockQnsImsManager.isWfcEnabledByUser()).thenAnswer(i -> mWfcEnabledByUser);
        when(mMockQnsImsManager.isWfcRoamingEnabledByUser())
                .thenAnswer(i -> mWfcRoamingEnabledByUser);
        when(mMockQnsImsManager.isWfcProvisionedOnDevice()).thenAnswer(i -> mWfcProvisioned);
        when(mMockQnsImsManager.isCrossSimCallingEnabled()).thenAnswer(i -> mCrossSimEnabled);
        when(mMockQnsProvisioningListener.getLastProvisioningWfcRoamingEnabledInfo())
                .thenAnswer(i -> mWfcRoamingEnabled);
        when(mMockQnsImsManager.getWfcMode(anyBoolean()))
                .thenAnswer(i -> (boolean) i.getArguments()[0] ? mWfcModeRoaming : mWfcMode);

        when(mMockQnsTelephonyListener.getLastQnsTelephonyInfo())
                .thenReturn(mMockQnsTelephonyListener.new QnsTelephonyInfo());
        when(mMockQnsTelephonyListener.getLastQnsTelephonyInfo())
                .thenReturn(mMockQnsTelephonyListener.new QnsTelephonyInfo());
    }

    @After
    public void tearDown() {
        if (mAne != null) {
            mAne.close();
        }
        if (mHandlerThread != null) {
            mHandlerThread.quit();
        }
    }

    @Test
    public void testRegisterForQualifiedNetworksChanged() {
        Handler h2 = new Handler(Looper.getMainLooper());
        mAne.registerForQualifiedNetworksChanged(mHandler, QUALIFIED_NETWORKS_CHANGED);
        mAne.registerForQualifiedNetworksChanged(mHandler, 2);
        assertEquals(1, mAne.mQualifiedNetworksChangedRegistrants.size());
        mAne.registerForQualifiedNetworksChanged(h2, 3);
        assertEquals(2, mAne.mQualifiedNetworksChangedRegistrants.size());
    }

    @Test
    public void testUnregisterForQualifiedNetworksChanged() {
        Handler h2 = new Handler(Looper.getMainLooper());
        mAne.registerForQualifiedNetworksChanged(mHandler, QUALIFIED_NETWORKS_CHANGED);
        mAne.registerForQualifiedNetworksChanged(h2, 3);
        assertEquals(2, mAne.mQualifiedNetworksChangedRegistrants.size());
        mAne.unregisterForQualifiedNetworksChanged(h2);
        assertEquals(1, mAne.mQualifiedNetworksChangedRegistrants.size());
    }

    /**
     * This test covers test cases for below methods of ANE: updateLastNotifiedQualifiedNetwork(),
     * getLastQualifiedTransportType(), initLastNotifiedQualifiedNetwork(),
     * equalsLastNotifiedQualifiedNetwork()
     */
    @Test
    public void testLastNotifiedQualifiedNetwork() {
        List<Integer> accessNetworks = new ArrayList<>();
        accessNetworks.add(AccessNetworkConstants.AccessNetworkType.EUTRAN);
        mAne.updateLastNotifiedQualifiedNetwork(accessNetworks);
        assertFalse(
                mAne.equalsLastNotifiedQualifiedNetwork(
                        List.of(AccessNetworkConstants.AccessNetworkType.IWLAN)));
        assertTrue(
                mAne.equalsLastNotifiedQualifiedNetwork(
                        List.of(AccessNetworkConstants.AccessNetworkType.EUTRAN)));
        assertEquals(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, mAne.getLastQualifiedTransportType());
        mAne.initLastNotifiedQualifiedNetwork();
        assertEquals(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, mAne.getLastQualifiedTransportType());

        accessNetworks.clear();
        accessNetworks.add(AccessNetworkConstants.AccessNetworkType.NGRAN);
        mAne.updateLastNotifiedQualifiedNetwork(accessNetworks);
        assertFalse(
                mAne.equalsLastNotifiedQualifiedNetwork(
                        List.of(AccessNetworkConstants.AccessNetworkType.IWLAN)));
        assertFalse(
                mAne.equalsLastNotifiedQualifiedNetwork(
                        List.of(AccessNetworkConstants.AccessNetworkType.EUTRAN)));
        assertTrue(
                mAne.equalsLastNotifiedQualifiedNetwork(
                        List.of(AccessNetworkConstants.AccessNetworkType.NGRAN)));
        assertEquals(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, mAne.getLastQualifiedTransportType());
        mAne.initLastNotifiedQualifiedNetwork();
        assertEquals(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, mAne.getLastQualifiedTransportType());

        accessNetworks.clear();
        accessNetworks.add(AccessNetworkConstants.AccessNetworkType.IWLAN);
        mAne.updateLastNotifiedQualifiedNetwork(accessNetworks);
        assertTrue(
                mAne.equalsLastNotifiedQualifiedNetwork(
                        List.of(AccessNetworkConstants.AccessNetworkType.IWLAN)));
        assertFalse(
                mAne.equalsLastNotifiedQualifiedNetwork(
                        List.of(AccessNetworkConstants.AccessNetworkType.EUTRAN)));
        assertEquals(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN, mAne.getLastQualifiedTransportType());
        mAne.initLastNotifiedQualifiedNetwork();
        assertEquals(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, mAne.getLastQualifiedTransportType());
    }

    @Test
    public void testNotifyForQualifiedNetworksChanged() throws Exception {
        mLatch = new CountDownLatch(1);
        List<Integer> accessNetworks = new ArrayList<>();
        accessNetworks.add(AccessNetworkConstants.AccessNetworkType.EUTRAN);
        mAne.registerForQualifiedNetworksChanged(mHandler, QUALIFIED_NETWORKS_CHANGED);
        mAne.notifyForQualifiedNetworksChanged(accessNetworks);
        assertTrue(mLatch.await(3, TimeUnit.SECONDS));
        assertEquals(accessNetworks, mQualifiedNetworksInfo.getAccessNetworkTypes());
    }

    @Test
    public void testMoveTransportTypeAllowed() {
        waitForLastHandlerAction(mAne.mHandler);
        when(mMockQnsConfigManager.isHandoverAllowedByPolicy(
                        NetworkCapabilities.NET_CAPABILITY_IMS,
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        QnsConstants.COVERAGE_HOME))
                .thenReturn(true);
        when(mMockQnsConfigManager.isHandoverAllowedByPolicy(
                        NetworkCapabilities.NET_CAPABILITY_IMS,
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        AccessNetworkConstants.AccessNetworkType.NGRAN,
                        QnsConstants.COVERAGE_ROAM))
                .thenReturn(false);
        when(mMockQnsConfigManager.isVolteRoamingSupported(anyInt())).thenReturn(true);
        when(mDataConnectionStatusTracker.isActiveState()).thenReturn(true);
        when(mDataConnectionStatusTracker.getLastTransportType())
                .thenReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        when(mMockQnsCallStatusTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_IMS))
                .thenReturn(true);
        List<Integer> accessNetworks = new ArrayList<>();
        accessNetworks.add(AccessNetworkConstants.AccessNetworkType.IWLAN);
        QnsTelephonyListener.QnsTelephonyInfo info =
                mMockQnsTelephonyListener.new QnsTelephonyInfo();
        info.setCellularAvailable(false);
        info.setCoverage(false);
        info.setDataNetworkType(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        info.setVoiceNetworkType(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        info.setDataRegState(ServiceState.STATE_OUT_OF_SERVICE);
        QnsTelephonyInfoIms infoIms =
                mMockQnsTelephonyListener.new QnsTelephonyInfoIms(info, true, true, false, false);
        mAne.onQnsTelephonyInfoChanged(infoIms);
        mAne.updateLastNotifiedQualifiedNetwork(accessNetworks);
        assertFalse(mAne.isHandoverPolicyCheckAvailable());
        assertTrue(mAne.moveTransportTypeAllowed());

        info = mMockQnsTelephonyListener.new QnsTelephonyInfo();
        info.setCellularAvailable(true);
        info.setCoverage(true);
        info.setDataNetworkType(TelephonyManager.NETWORK_TYPE_LTE);
        info.setVoiceNetworkType(TelephonyManager.NETWORK_TYPE_LTE);
        info.setDataRegState(ServiceState.STATE_IN_SERVICE);
        infoIms = mMockQnsTelephonyListener.new QnsTelephonyInfoIms(info, true, true, true, true);
        mAne.onQnsTelephonyInfoChanged(infoIms);
        mAne.updateLastNotifiedQualifiedNetwork(accessNetworks);
        assertTrue(mAne.isHandoverPolicyCheckAvailable());
        assertTrue(mAne.moveTransportTypeAllowed());

        info = mMockQnsTelephonyListener.new QnsTelephonyInfo();
        info.setCellularAvailable(true);
        info.setCoverage(false);
        info.setDataNetworkType(TelephonyManager.NETWORK_TYPE_NR);
        info.setVoiceNetworkType(TelephonyManager.NETWORK_TYPE_NR);
        info.setDataRegState(ServiceState.STATE_IN_SERVICE);
        infoIms = mMockQnsTelephonyListener.new QnsTelephonyInfoIms(info, true, true, true, true);
        mAne.onQnsTelephonyInfoChanged(infoIms);
        mAne.updateLastNotifiedQualifiedNetwork(accessNetworks);
        assertTrue(mAne.isHandoverPolicyCheckAvailable());
        assertTrue(mAne.moveTransportTypeAllowed());
    }

    @Test
    public void testMoveTransportTypeAllowedEmergencyOverIms() {
        waitForLastHandlerAction(mAne.mHandler);
        when(mMockQnsConfigManager.isHandoverAllowedByPolicy(
                        NetworkCapabilities.NET_CAPABILITY_IMS,
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        QnsConstants.COVERAGE_HOME))
                .thenReturn(true);
        when(mMockQnsConfigManager.isHandoverAllowedByPolicy(
                        NetworkCapabilities.NET_CAPABILITY_IMS,
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        QnsConstants.COVERAGE_HOME))
                .thenReturn(true);
        when(mMockQnsConfigManager.isVolteRoamingSupported(anyInt())).thenReturn(true);
        when(mDataConnectionStatusTracker.isActiveState()).thenReturn(true);
        when(mDataConnectionStatusTracker.getLastTransportType())
                .thenReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        when(mMockQnsCallStatusTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_IMS))
                .thenReturn(true);
        List<Integer> accessNetworks = new ArrayList<>();
        accessNetworks.add(AccessNetworkConstants.AccessNetworkType.IWLAN);
        QnsTelephonyListener.QnsTelephonyInfo info =
                mMockQnsTelephonyListener.new QnsTelephonyInfo();
        info.setCellularAvailable(true);
        info.setCoverage(false);
        info.setDataNetworkType(TelephonyManager.NETWORK_TYPE_LTE);
        info.setVoiceNetworkType(TelephonyManager.NETWORK_TYPE_LTE);
        info.setDataRegState(ServiceState.STATE_IN_SERVICE);
        QnsTelephonyInfoIms infoIms =
                mMockQnsTelephonyListener.new QnsTelephonyInfoIms(info, true, true, false, false);
        mAne.onQnsTelephonyInfoChanged(infoIms);
        mAne.updateLastNotifiedQualifiedNetwork(accessNetworks);
        mAne.onSetCallType(QnsConstants.CALL_TYPE_EMERGENCY);
        assertTrue(mAne.isHandoverPolicyCheckAvailable());
        assertTrue(mAne.moveTransportTypeAllowed());

        when(mMockQnsConfigManager.isHandoverAllowedByPolicy(
                        NetworkCapabilities.NET_CAPABILITY_IMS,
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        AccessNetworkConstants.AccessNetworkType.UTRAN,
                        QnsConstants.COVERAGE_HOME))
                .thenReturn(false);
        info = mMockQnsTelephonyListener.new QnsTelephonyInfo();
        info.setCellularAvailable(true);
        info.setCoverage(false);
        info.setDataNetworkType(TelephonyManager.NETWORK_TYPE_UMTS);
        info.setVoiceNetworkType(TelephonyManager.NETWORK_TYPE_UMTS);
        info.setDataRegState(ServiceState.STATE_IN_SERVICE);
        infoIms = mMockQnsTelephonyListener.new QnsTelephonyInfoIms(info, true, true, false, false);
        mAne.onQnsTelephonyInfoChanged(infoIms);
        mAne.updateLastNotifiedQualifiedNetwork(accessNetworks);
        mAne.onSetCallType(QnsConstants.CALL_TYPE_VOICE);
        when(mMockQnsCallStatusTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_IMS))
                .thenReturn(false);
        assertTrue(mAne.isHandoverPolicyCheckAvailable());
        assertFalse(mAne.moveTransportTypeAllowed());
        mAne.updateLastNotifiedQualifiedNetwork(accessNetworks);
        mAne.onSetCallType(QnsConstants.CALL_TYPE_IDLE);
        when(mMockQnsCallStatusTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_IMS))
                .thenReturn(true);
        assertTrue(mAne.isHandoverPolicyCheckAvailable());
        assertTrue(mAne.moveTransportTypeAllowed());
        mAne.updateLastNotifiedQualifiedNetwork(accessNetworks);
        mAne.onSetCallType(QnsConstants.CALL_TYPE_EMERGENCY);
        when(mMockQnsCallStatusTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_IMS))
                .thenReturn(false);
        assertTrue(mAne.isHandoverPolicyCheckAvailable());
        assertFalse(mAne.moveTransportTypeAllowed());
    }

    @Test
    public void testVopsCheckRequired() {
        waitForLastHandlerAction(mAne.mHandler);
        when(mDataConnectionStatusTracker.getLastTransportType())
                .thenReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        when(mMockQnsConfigManager.isHandoverAllowedByPolicy(
                        anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(true);
        when(mMockQnsConfigManager.isMmtelCapabilityRequired(QnsConstants.COVERAGE_HOME))
                .thenReturn(true);
        when(mMockQnsConfigManager.isMmtelCapabilityRequired(QnsConstants.COVERAGE_ROAM))
                .thenReturn(false);
        when(mMockQnsConfigManager.isInCallHoDecisionWlanToWwanWithoutVopsCondition())
                .thenReturn(false);
        assertTrue(
                mAne.vopsCheckRequired(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        QnsConstants.COVERAGE_HOME,
                        QnsConstants.CALL_TYPE_IDLE));
        assertFalse(
                mAne.vopsCheckRequired(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        QnsConstants.COVERAGE_ROAM,
                        QnsConstants.CALL_TYPE_IDLE));
        when(mMockQnsConfigManager.isInCallHoDecisionWlanToWwanWithoutVopsCondition())
                .thenReturn(true);
        assertFalse(
                mAne.vopsCheckRequired(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        QnsConstants.COVERAGE_HOME,
                        QnsConstants.CALL_TYPE_VOICE));
        when(mDataConnectionStatusTracker.getLastTransportType())
                .thenReturn(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertTrue(
                mAne.vopsCheckRequired(
                        AccessNetworkConstants.AccessNetworkType.NGRAN,
                        QnsConstants.COVERAGE_HOME,
                        QnsConstants.CALL_TYPE_IDLE));
        assertFalse(
                mAne.vopsCheckRequired(
                        AccessNetworkConstants.AccessNetworkType.NGRAN,
                        QnsConstants.COVERAGE_HOME,
                        QnsConstants.CALL_TYPE_VOICE));
        assertFalse(
                mAne.vopsCheckRequired(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        QnsConstants.COVERAGE_ROAM,
                        QnsConstants.CALL_TYPE_IDLE));
    }

    /**
     * This method covers test for onCellularQualityChanged(), on WifiQualityChanged(),
     * onQnsTelephonyInfoChanged() and evaluate() methods of ANE. Since all these methods call for
     * the evaluation of access networks, testing them separately will repeat the test cases.
     */
    @Test
    public void testEvaluationOfQualifiedNetwork() throws Exception {
        mLatch = new CountDownLatch(1);
        mAne.registerForQualifiedNetworksChanged(mHandler, QUALIFIED_NETWORKS_CHANGED);
        waitForLastHandlerAction(mAne.mHandler);
        List<Integer> accessNetworks = new ArrayList<>();
        accessNetworks.add(AccessNetworkConstants.AccessNetworkType.IWLAN);
        mAne.updateLastNotifiedQualifiedNetwork(accessNetworks);

        when(mMockQnsConfigManager.isMmtelCapabilityRequired(anyInt())).thenReturn(true);
        when(mMockQnsConfigManager.isServiceBarringCheckSupported()).thenReturn(true);
        when(mMockQnsConfigManager.isInCallHoDecisionWlanToWwanWithoutVopsCondition())
                .thenReturn(false);
        when(mMockQnsConfigManager.isHandoverAllowedByPolicy(
                        anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(true);
        when(mMockQnsConfigManager.allowWFCOnAirplaneModeOn()).thenReturn(true);
        when(mMockQnsConfigManager.isAccessNetworkAllowed(anyInt(), anyInt())).thenReturn(true);
        when(mMockQnsConfigManager.isVolteRoamingSupported(anyInt())).thenReturn(true);
        when(mMockQnsConfigManager.blockIwlanInInternationalRoamWithoutWwan()).thenReturn(false);
        when(mMockQnsConfigManager.getRatPreference(NetworkCapabilities.NET_CAPABILITY_IMS))
                .thenReturn(QnsConstants.RAT_PREFERENCE_DEFAULT);

        when(mDataConnectionStatusTracker.isActiveState()).thenReturn(false);
        when(mDataConnectionStatusTracker.isInactiveState()).thenReturn(true);
        when(mDataConnectionStatusTracker.isHandoverState()).thenReturn(false);

        when(mRestrictManager.isRestricted(anyInt())).thenReturn(true);
        when(mRestrictManager.isAllowedOnSingleTransport(anyInt())).thenReturn(true);

        QnsTelephonyListener.QnsTelephonyInfo info =
                mMockQnsTelephonyListener.new QnsTelephonyInfo();
        info.setCellularAvailable(true);
        info.setCoverage(true);
        info.setDataNetworkType(TelephonyManager.NETWORK_TYPE_LTE);
        info.setVoiceNetworkType(TelephonyManager.NETWORK_TYPE_LTE);
        info.setDataRegState(ServiceState.STATE_IN_SERVICE);
        Message.obtain(
                        mAne.mHandler,
                        EVENT_QNS_TELEPHONY_INFO_CHANGED,
                        new QnsAsyncResult(null, info, null))
                .sendToTarget();
        waitForLastHandlerAction(mAne.mHandler);
        // mAne.onQnsTelephonyInfoChanged(infoIms);

        assertTrue(mLatch.await(3, TimeUnit.SECONDS));
        ArrayList<Integer> expected = new ArrayList<>();
        expected.add(AccessNetworkConstants.AccessNetworkType.EUTRAN);
        assertEquals(expected, mQualifiedNetworksInfo.getAccessNetworkTypes());
    }

    @Test
    public void testOnIwlanNetworkStatusChanged() {
        // ANE takes time to build ANSP.
        waitForLastHandlerAction(mAne.mHandler);
        when(mRestrictManager.isRestricted(anyInt())).thenReturn(false);
        when(mRestrictManager.isAllowedOnSingleTransport(anyInt())).thenReturn(true);
        Message.obtain(
                        mAne.mHandler,
                        EVENT_IWLAN_NETWORK_STATUS_CHANGED,
                        new QnsAsyncResult(
                                null,
                                mMockIwlanNetworkStatusTracker
                                .new IwlanAvailabilityInfo(true, false),
                                null))
                .sendToTarget();
        waitForLastHandlerAction(mAne.mHandler);
        assertTrue(mAne.mIwlanAvailable);
        Message.obtain(
                        mAne.mHandler,
                        EVENT_IWLAN_NETWORK_STATUS_CHANGED,
                        new QnsAsyncResult(
                                null,
                                mMockIwlanNetworkStatusTracker
                                .new IwlanAvailabilityInfo(false, false),
                                null))
                .sendToTarget();
        waitForLastHandlerAction(mAne.mHandler);
        assertFalse(mAne.mIwlanAvailable);
    }

    @Test
    public void testOnTryWfcConnectionStateChanged() {
        mWfcMode = QnsConstants.CELL_PREF;
        mWfcModeRoaming = QnsConstants.CELL_PREF;

        // ANE takes time to build ANSP.
        waitForLastHandlerAction(mAne.mHandler);

        mAne.onTryWfcConnectionStateChanged(true);
        Message.obtain(
                        mAne.mHandler,
                        EVENT_WFC_ACTIVATION_WITH_IWLAN_CONNECTION_REQUIRED,
                        new QnsAsyncResult(null, true, null))
                .sendToTarget();
        waitForDelayedHandlerAction(mAne.mHandler, 3000, 200);
        assertTrue(mAne.isWfcEnabled());
        assertEquals(QnsConstants.WIFI_PREF, mAne.getPreferredMode());
        mWfcEnabledByPlatform = false;
        Message.obtain(
                        mAne.mHandler,
                        EVENT_WFC_ACTIVATION_WITH_IWLAN_CONNECTION_REQUIRED,
                        new QnsAsyncResult(null, false, null))
                .sendToTarget();
        waitForLastHandlerAction(mAne.mHandler);
        assertFalse(mAne.isWfcEnabled());
        assertEquals(QnsConstants.CELL_PREF, mAne.getPreferredMode());
    }

    @Test
    public void testGetTargetTransportType() {
        waitForLastHandlerAction(mAne.mHandler);
        List<Integer> accessNetworks = new ArrayList<>();
        accessNetworks.add(AccessNetworkConstants.AccessNetworkType.EUTRAN);
        assertEquals(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                mAne.getTargetTransportType(accessNetworks));
        accessNetworks.clear();

        accessNetworks.add(AccessNetworkConstants.AccessNetworkType.IWLAN);
        assertEquals(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                mAne.getTargetTransportType(accessNetworks));
        accessNetworks.clear();

        accessNetworks.add(AccessNetworkConstants.AccessNetworkType.UNKNOWN);
        assertEquals(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                mAne.getTargetTransportType(accessNetworks));
        accessNetworks.clear();

        accessNetworks.add(AccessNetworkConstants.AccessNetworkType.NGRAN);
        assertEquals(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                mAne.getTargetTransportType(accessNetworks));
        accessNetworks.clear();

        accessNetworks = null;
        assertEquals(
                AccessNetworkConstants.TRANSPORT_TYPE_INVALID,
                mAne.getTargetTransportType(accessNetworks));
    }

    @Test
    public void testIsHandoverNeeded() {
        // ANE takes time to build ANSP.
        waitForLastHandlerAction(mAne.mHandler);
        List<Integer> accessNetworks = new ArrayList<>();
        accessNetworks.add(AccessNetworkConstants.AccessNetworkType.EUTRAN);
        when(mDataConnectionStatusTracker.isInactiveState()).thenReturn(true, false);
        when(mDataConnectionStatusTracker.isHandoverState()).thenReturn(true, false);
        when(mDataConnectionStatusTracker.getLastTransportType())
                .thenReturn(
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        assertFalse(mAne.isHandoverNeeded(accessNetworks)); // isInactiveState
        assertFalse(mAne.isHandoverNeeded(accessNetworks)); // isHandoverState
        assertFalse(mAne.isHandoverNeeded(accessNetworks)); // same last TransportType
        assertTrue(mAne.isHandoverNeeded(accessNetworks)); // all OK
    }

    @Test
    public void testIsFallbackCase() {
        List<Integer> accessNetworks = new ArrayList<>();
        accessNetworks.add(AccessNetworkConstants.AccessNetworkType.EUTRAN);
        mAne.updateLastNotifiedQualifiedNetwork(accessNetworks);
        when(mDataConnectionStatusTracker.isInactiveState()).thenReturn(true, false);
        when(mDataConnectionStatusTracker.getLastTransportType())
                .thenReturn(
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertFalse(mAne.isFallbackCase(accessNetworks)); // isInactiveState
        assertFalse(mAne.isFallbackCase(accessNetworks)); // target TT != last TT
        assertFalse(mAne.isFallbackCase(accessNetworks)); // LastQualifiedTransportType == TT
        List<Integer> accessNetworks2 = new ArrayList<>();
        accessNetworks2.add(AccessNetworkConstants.AccessNetworkType.IWLAN);
        mAne.updateLastNotifiedQualifiedNetwork(accessNetworks2);
        assertTrue(mAne.isFallbackCase(accessNetworks)); // all OK
    }

    @Test
    public void testReportQualifiedNetwork_WithoutListAndCellularLimtedCaseSet() {
        when(mMockQnsConfigManager.isAccessNetworkAllowed(anyInt(), anyInt()))
                .thenReturn(false, true);
        when(mMockQnsConfigManager.allowImsOverIwlanCellularLimitedCase()).thenReturn(true);
        List<Integer> accessNetworks2 = new ArrayList<>();
        accessNetworks2.add(AccessNetworkConstants.AccessNetworkType.IWLAN);
        mAne.updateLastNotifiedQualifiedNetwork(accessNetworks2);
        mAne.reportQualifiedNetwork(List.of(AccessNetworkConstants.AccessNetworkType.EUTRAN));
        assertEquals(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN, mAne.getLastQualifiedTransportType());
        mAne.reportQualifiedNetwork(List.of(AccessNetworkConstants.AccessNetworkType.EUTRAN));
        assertEquals(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, mAne.getLastQualifiedTransportType());
    }

    @Test
    public void testReportQualifiedNetwork_WithListAndCellularLimtedCaseSet() {
        when(mMockQnsConfigManager.isAccessNetworkAllowed(anyInt(), anyInt()))
                .thenReturn(false, true);
        when(mMockQnsConfigManager.allowImsOverIwlanCellularLimitedCase()).thenReturn(true);
        List<Integer> accessNetworks1 = new ArrayList<>();
        List<Integer> accessNetworks2 = new ArrayList<>();
        accessNetworks1.add(AccessNetworkConstants.AccessNetworkType.EUTRAN);
        accessNetworks2.add(AccessNetworkConstants.AccessNetworkType.IWLAN);
        mAne.updateLastNotifiedQualifiedNetwork(accessNetworks2);
        mAne.reportQualifiedNetwork(accessNetworks1);
        assertEquals(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN, mAne.getLastQualifiedTransportType());
        accessNetworks1.clear();
        accessNetworks1.add(AccessNetworkConstants.AccessNetworkType.EUTRAN);
        mAne.reportQualifiedNetwork(accessNetworks1);
        assertEquals(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, mAne.getLastQualifiedTransportType());
    }

    @Test
    public void testReportQualifiedNetwork_WithListAndCellularLimtedCaseSet_NonIms() {
        mAne =
                new AccessNetworkEvaluator(
                        mQnsComponents[mSlotIndex],
                        NetworkCapabilities.NET_CAPABILITY_MMS,
                        mRestrictManager,
                        mDataConnectionStatusTracker,
                        mSlotIndex);
        waitForLastHandlerAction(mAne.mHandler);
        when(mMockQnsConfigManager.isAccessNetworkAllowed(anyInt(), anyInt())).thenReturn(false);
        when(mMockQnsConfigManager.allowImsOverIwlanCellularLimitedCase()).thenReturn(true);
        List<Integer> accessNetworks1 = new ArrayList<>();
        List<Integer> accessNetworks2 = new ArrayList<>();
        accessNetworks1.add(AccessNetworkConstants.AccessNetworkType.EUTRAN);
        accessNetworks2.add(AccessNetworkConstants.AccessNetworkType.IWLAN);
        mAne.updateLastNotifiedQualifiedNetwork(accessNetworks2);
        mAne.reportQualifiedNetwork(accessNetworks1);
        assertEquals(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, mAne.getLastQualifiedTransportType());
    }

    @Test
    public void testReportQualifiedNetwork_WithoutList() {
        when(mMockQnsConfigManager.isAccessNetworkAllowed(anyInt(), anyInt()))
                .thenReturn(false, true);
        when(mMockQnsConfigManager.allowImsOverIwlanCellularLimitedCase()).thenReturn(false);
        List<Integer> accessNetworks2 = new ArrayList<>();
        accessNetworks2.add(AccessNetworkConstants.AccessNetworkType.IWLAN);
        mAne.updateLastNotifiedQualifiedNetwork(accessNetworks2);
        mAne.reportQualifiedNetwork(List.of(AccessNetworkConstants.AccessNetworkType.EUTRAN));
        assertEquals(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, mAne.getLastQualifiedTransportType());
    }

    @Test
    public void testReportQualifiedNetwork_WithList() {
        waitForLastHandlerAction(mAne.mHandler);
        when(mMockQnsConfigManager.isAccessNetworkAllowed(anyInt(), anyInt()))
                .thenReturn(false, true);
        when(mMockQnsConfigManager.allowImsOverIwlanCellularLimitedCase()).thenReturn(false);
        List<Integer> accessNetworks1 = new ArrayList<>();
        List<Integer> accessNetworks2 = new ArrayList<>();
        accessNetworks1.add(AccessNetworkConstants.AccessNetworkType.EUTRAN);
        accessNetworks2.add(AccessNetworkConstants.AccessNetworkType.IWLAN);
        mAne.updateLastNotifiedQualifiedNetwork(accessNetworks2);
        mAne.reportQualifiedNetwork(accessNetworks1);
        assertEquals(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, mAne.getLastQualifiedTransportType());
    }

    @Test
    public void testCellularUnavailableWhenVolteRoamingNotSupported() {
        waitForLastHandlerAction(mAne.mHandler);
        doReturn(true).when(mMockQnsConfigManager).isMmtelCapabilityRequired(anyInt());
        doReturn(false).when(mMockQnsConfigManager).isServiceBarringCheckSupported();
        doReturn(false)
                .when(mMockQnsConfigManager)
                .isInCallHoDecisionWlanToWwanWithoutVopsCondition();
        doReturn(true)
                .when(mMockQnsConfigManager)
                .isHandoverAllowedByPolicy(anyInt(), anyInt(), anyInt(), anyInt());
        doReturn(false).when(mMockQnsConfigManager).isVolteRoamingSupported(anyInt());
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .when(mDataConnectionStatusTracker)
                .getLastTransportType();
        doReturn(null)
                .when(mMockQnsConfigManager)
                .getThresholdByPref(anyInt(), anyInt(), anyInt(), anyInt());
        QnsTelephonyListener.QnsTelephonyInfo info =
                mMockQnsTelephonyListener.new QnsTelephonyInfo();
        info.setCellularAvailable(true);
        info.setCoverage(true);
        info.setDataNetworkType(TelephonyManager.NETWORK_TYPE_LTE);
        info.setVoiceNetworkType(TelephonyManager.NETWORK_TYPE_LTE);
        info.setDataRegState(ServiceState.STATE_IN_SERVICE);
        QnsTelephonyInfoIms infoIms =
                mMockQnsTelephonyListener.new QnsTelephonyInfoIms(info, true, true, false, false);
        mAne.onQnsTelephonyInfoChanged(infoIms);
        assertFalse(mAne.mCellularAvailable);
    }

    @Test
    public void testUpdateThrottleStatus() {
        mAne.updateThrottleStatus(false, 30000, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        verify(mRestrictManager)
                .notifyThrottling(false, 30000, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
    }

    @Test
    public void testGetCoverage() {
        QnsTelephonyListener.QnsTelephonyInfo info =
                mMockQnsTelephonyListener.new QnsTelephonyInfo();
        QnsTelephonyInfoIms infoIms =
                mMockQnsTelephonyListener.new QnsTelephonyInfoIms(info, true, true, false, false);
        infoIms.setRegisteredPlmn(TEST_PLMN);

        infoIms.setCoverage(true);
        assertEquals(QnsConstants.COVERAGE_ROAM, mAne.getCoverage(infoIms));

        infoIms.setCoverage(false);
        assertEquals(QnsConstants.COVERAGE_HOME, mAne.getCoverage(infoIms));
    }

    @Test
    public void testUseDifferentApnOverIwlan() {
        mAne =
                new AccessNetworkEvaluator(
                        mQnsComponents[mSlotIndex],
                        NetworkCapabilities.NET_CAPABILITY_MMS,
                        mRestrictManager,
                        mDataConnectionStatusTracker,
                        mSlotIndex);
        waitForLastHandlerAction(mAne.mHandler);
        ApnSetting apnSettingForCellular =
                new ApnSetting.Builder()
                        .setApnName("internetmms")
                        .setEntryName("internetmms")
                        .setApnTypeBitmask(
                                ApnSetting.TYPE_DEFAULT
                                        | ApnSetting.TYPE_MMS
                                        | ApnSetting.TYPE_XCAP
                                        | ApnSetting.TYPE_CBS)
                        .setNetworkTypeBitmask(
                                (int) TelephonyManager.NETWORK_STANDARDS_FAMILY_BITMASK_3GPP)
                        .setCarrierEnabled(true)
                        .build();
        when(mMockQnsConfigManager.isHandoverAllowedByPolicy(
                        NetworkCapabilities.NET_CAPABILITY_MMS,
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        QnsConstants.COVERAGE_HOME))
                .thenReturn(false);
        when(mDataConnectionStatusTracker.getLastTransportType())
                .thenReturn(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        when(mDataConnectionStatusTracker.getLastApnSetting(anyInt()))
                .thenReturn(apnSettingForCellular);

        assertTrue(mAne.moveTransportTypeAllowed());
    }

    @Test
    public void testRatPreferenceWifiWhenNoCellularHandoverDisallowedButMoveToCellular() {
        mAne =
                new AccessNetworkEvaluator(
                        mQnsComponents[mSlotIndex],
                        NetworkCapabilities.NET_CAPABILITY_XCAP,
                        mRestrictManager,
                        mDataConnectionStatusTracker,
                        mSlotIndex);
        waitForLastHandlerAction(mAne.mHandler);
        QnsTelephonyListener.QnsTelephonyInfo info =
                mMockQnsTelephonyListener.new QnsTelephonyInfo();
        info.setCellularAvailable(true);
        info.setCoverage(false);
        info.setDataNetworkType(TelephonyManager.NETWORK_TYPE_LTE);
        info.setVoiceNetworkType(TelephonyManager.NETWORK_TYPE_LTE);
        info.setDataRegState(ServiceState.STATE_IN_SERVICE);

        when(mMockQnsConfigManager.isHandoverAllowedByPolicy(
                        NetworkCapabilities.NET_CAPABILITY_XCAP,
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        QnsConstants.COVERAGE_HOME))
                .thenReturn(false);
        when(mMockQnsConfigManager.getRatPreference(NetworkCapabilities.NET_CAPABILITY_XCAP))
                .thenReturn(QnsConstants.RAT_PREFERENCE_WIFI_WHEN_NO_CELLULAR);
        when(mDataConnectionStatusTracker.getLastTransportType())
                .thenReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        mAne.onQnsTelephonyInfoChanged(info);

        assertTrue(mAne.moveTransportTypeAllowed());

        info.setCellularAvailable(false);
        mAne.onQnsTelephonyInfoChanged(info);

        assertFalse(mAne.moveTransportTypeAllowed());
    }

    @Test
    public void testOnWfcEnabledChanged_Home() throws InterruptedException {
        mWfcEnabledByUser = false;
        mAne.onIwlanNetworkStatusChanged(
                mMockIwlanNetworkStatusTracker.new IwlanAvailabilityInfo(true, false));
        mAne.registerForQualifiedNetworksChanged(mHandler, QUALIFIED_NETWORKS_CHANGED);

        // ANE takes time to build ANSP.
        waitForLastHandlerAction(mAne.mHandler);

        // Enabled
        mLatch = new CountDownLatch(1);
        mWfcEnabledByUser = true;
        mAne.mHandler.sendEmptyMessage(QnsEventDispatcher.QNS_EVENT_WFC_ENABLED);
        assertTrue(mLatch.await(200, TimeUnit.MILLISECONDS));
        assertTrue(
                mQualifiedNetworksInfo
                        .getAccessNetworkTypes()
                        .contains(AccessNetworkConstants.AccessNetworkType.IWLAN));

        // Disabled
        mLatch = new CountDownLatch(1);
        mWfcEnabledByUser = false;
        mAne.mHandler.sendEmptyMessage(QnsEventDispatcher.QNS_EVENT_WFC_DISABLED);
        assertTrue(mLatch.await(200, TimeUnit.MILLISECONDS));
        assertFalse(
                mQualifiedNetworksInfo
                        .getAccessNetworkTypes()
                        .contains(AccessNetworkConstants.AccessNetworkType.IWLAN));
    }

    @Test
    public void testOnWfcEnabledChanged_Roaming() throws InterruptedException {
        mWfcMode = QnsConstants.CELL_PREF;
        mWfcEnabledByUser = true;
        QnsTelephonyListener.QnsTelephonyInfo info =
                mMockQnsTelephonyListener.new QnsTelephonyInfo();
        QnsTelephonyInfoIms infoIms =
                mMockQnsTelephonyListener.new QnsTelephonyInfoIms(info, true, true, false, false);
        infoIms.setCoverage(true);
        infoIms.setRegisteredPlmn(TEST_PLMN);
        mAne.onQnsTelephonyInfoChanged(infoIms);

        mAne.onIwlanNetworkStatusChanged(
                mMockIwlanNetworkStatusTracker.new IwlanAvailabilityInfo(true, false));
        mAne.registerForQualifiedNetworksChanged(mHandler, QUALIFIED_NETWORKS_CHANGED);
        waitForLastHandlerAction(mAne.mHandler);

        // Enabled
        mLatch = new CountDownLatch(1);
        mWfcRoamingEnabled = true;
        mWfcRoamingEnabledByUser = true;
        mAne.mHandler.sendEmptyMessage(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_ENABLED);
        assertTrue(mLatch.await(200, TimeUnit.MILLISECONDS));
        assertTrue(
                mQualifiedNetworksInfo
                        .getAccessNetworkTypes()
                        .contains(AccessNetworkConstants.AccessNetworkType.IWLAN));

        // Disabled
        mLatch = new CountDownLatch(1);
        mWfcRoamingEnabled = false;
        mWfcRoamingEnabledByUser = false;
        mAne.mHandler.sendEmptyMessage(QnsEventDispatcher.QNS_EVENT_WFC_ROAMING_DISABLED);
        assertTrue(mLatch.await(200, TimeUnit.MILLISECONDS));
        assertFalse(
                mQualifiedNetworksInfo
                        .getAccessNetworkTypes()
                        .contains(AccessNetworkConstants.AccessNetworkType.IWLAN));
    }

    @Test
    public void testOnWfcPlatformChanged() throws InterruptedException {
        mWfcMode = QnsConstants.CELL_PREF;
        mWfcEnabledByUser = true;
        mWfcEnabledByPlatform = false;
        mAne.onIwlanNetworkStatusChanged(
                mMockIwlanNetworkStatusTracker.new IwlanAvailabilityInfo(true, false));
        mAne.registerForQualifiedNetworksChanged(mHandler, QUALIFIED_NETWORKS_CHANGED);
        waitForLastHandlerAction(mAne.mHandler);

        // Enabled
        mLatch = new CountDownLatch(1);
        mWfcEnabledByPlatform = true;
        mAne.mHandler.sendEmptyMessage(QnsEventDispatcher.QNS_EVENT_WFC_PLATFORM_ENABLED);
        mAne.mHandler.sendEmptyMessage(QnsEventDispatcher.QNS_EVENT_WFC_ENABLED);
        assertTrue(mLatch.await(200, TimeUnit.MILLISECONDS));
        assertTrue(
                mQualifiedNetworksInfo
                        .getAccessNetworkTypes()
                        .contains(AccessNetworkConstants.AccessNetworkType.IWLAN));

        // No report if no change in settings
        mLatch = new CountDownLatch(1);
        mAne.mHandler.sendEmptyMessage(QnsEventDispatcher.QNS_EVENT_WFC_PLATFORM_ENABLED);
        assertFalse(mLatch.await(200, TimeUnit.MILLISECONDS));

        // Disabled
        mLatch = new CountDownLatch(1);
        mWfcEnabledByPlatform = false;
        mAne.mHandler.sendEmptyMessage(QnsEventDispatcher.QNS_EVENT_WFC_PLATFORM_DISABLED);
        assertTrue(mLatch.await(100, TimeUnit.MILLISECONDS));
        assertTrue(mQualifiedNetworksInfo.getAccessNetworkTypes().isEmpty());
    }

    @Test
    public void testOnWfcModeChanged_Home() throws Exception {
        mWfcMode = QnsConstants.CELL_PREF;
        mWfcEnabledByUser = true;
        waitForLastHandlerAction(mAne.mHandler);
        mAne.mHandler.sendEmptyMessage(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_CELLULAR_PREFERRED);
        generateAnspPolicyMap();
        mockCurrentQuality(-60, -90);
        mAne.onIwlanNetworkStatusChanged(
                mMockIwlanNetworkStatusTracker.new IwlanAvailabilityInfo(true, false));

        when(mMockQnsConfigManager.isVolteRoamingSupported(anyInt())).thenReturn(true);
        QnsTelephonyListener.QnsTelephonyInfo info =
                mMockQnsTelephonyListener.new QnsTelephonyInfo();
        info.setCellularAvailable(true);
        info.setCoverage(false);
        info.setDataNetworkType(TelephonyManager.NETWORK_TYPE_LTE);
        info.setVoiceNetworkType(TelephonyManager.NETWORK_TYPE_LTE);
        info.setDataRegState(ServiceState.STATE_IN_SERVICE);
        QnsTelephonyInfoIms infoIms =
                mMockQnsTelephonyListener.new QnsTelephonyInfoIms(info, true, true, false, false);
        mAne.onQnsTelephonyInfoChanged(infoIms);
        mAne.registerForQualifiedNetworksChanged(mHandler, QUALIFIED_NETWORKS_CHANGED);
        waitForLastHandlerAction(mAne.mHandler);

        // Wifi Preferred
        mLatch = new CountDownLatch(1);
        mAne.mHandler.sendEmptyMessage(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_PREFERRED);
        assertTrue(mLatch.await(2000, TimeUnit.MILLISECONDS));
        assertTrue(
                mQualifiedNetworksInfo
                        .getAccessNetworkTypes()
                        .contains(AccessNetworkConstants.AccessNetworkType.IWLAN));
    }

    @Test
    public void testOnEmergencyPreferredTransportTypeChanged() throws InterruptedException {
        waitForLastHandlerAction(mAne.mHandler);
        mLatch = new CountDownLatch(1);
        mAne.registerForQualifiedNetworksChanged(mHandler, QUALIFIED_NETWORKS_CHANGED);
        mAne.onEmergencyPreferredTransportTypeChanged(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        waitForLastHandlerAction(mAne.mHandler);
        assertFalse(mLatch.await(100, TimeUnit.MILLISECONDS));

        when(mDataConnectionStatusTracker.isInactiveState()).thenReturn(true);
        mAne =
                new AccessNetworkEvaluator(
                        mQnsComponents[mSlotIndex],
                        NetworkCapabilities.NET_CAPABILITY_EIMS,
                        mRestrictManager,
                        mDataConnectionStatusTracker,
                        mSlotIndex);
        waitForLastHandlerAction(mAne.mHandler);

        // WLAN
        mLatch = new CountDownLatch(1);
        mAne.registerForQualifiedNetworksChanged(mHandler, QUALIFIED_NETWORKS_CHANGED);
        mAne.onEmergencyPreferredTransportTypeChanged(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        waitForLastHandlerAction(mAne.mHandler);
        assertTrue(mLatch.await(500, TimeUnit.MILLISECONDS));
        assertTrue(
                mQualifiedNetworksInfo
                        .getAccessNetworkTypes()
                        .contains(AccessNetworkConstants.AccessNetworkType.IWLAN));

        // UNKNOWN
        mLatch = new CountDownLatch(1);
        mAne.onEmergencyPreferredTransportTypeChanged(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        waitForLastHandlerAction(mAne.mHandler);
        assertTrue(mLatch.await(100, TimeUnit.MILLISECONDS));
        assertTrue(mQualifiedNetworksInfo.getAccessNetworkTypes().isEmpty());

        // EUTRAN
        QnsTelephonyListener.QnsTelephonyInfo info =
                mMockQnsTelephonyListener.new QnsTelephonyInfo();
        info.setCellularAvailable(true);
        info.setCoverage(false);
        info.setDataNetworkType(TelephonyManager.NETWORK_TYPE_LTE);
        info.setVoiceNetworkType(TelephonyManager.NETWORK_TYPE_LTE);
        info.setDataRegState(ServiceState.STATE_IN_SERVICE);
        QnsTelephonyInfoIms infoIms =
                mMockQnsTelephonyListener.new QnsTelephonyInfoIms(info, true, true, false, false);
        mAne.onQnsTelephonyInfoChanged(infoIms);
        waitForLastHandlerAction(mAne.mHandler);

        mLatch = new CountDownLatch(1);
        mAne.onEmergencyPreferredTransportTypeChanged(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        waitForLastHandlerAction(mAne.mHandler);
        assertTrue(mLatch.await(100, TimeUnit.MILLISECONDS));
        assertTrue(
                mQualifiedNetworksInfo
                        .getAccessNetworkTypes()
                        .contains(AccessNetworkConstants.AccessNetworkType.EUTRAN));
    }

    @Test
    public void testCachedPreferredTransportTypeForEmergency() throws InterruptedException {
        waitForLastHandlerAction(mAne.mHandler);
        mLatch = new CountDownLatch(1);
        mAne.registerForQualifiedNetworksChanged(mHandler, QUALIFIED_NETWORKS_CHANGED);

        mAne.onEmergencyPreferredTransportTypeChanged(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        waitForLastHandlerAction(mAne.mHandler);
        assertFalse(mLatch.await(100, TimeUnit.MILLISECONDS));

        when(mDataConnectionStatusTracker.isInactiveState()).thenReturn(false);
        mAne =
                new AccessNetworkEvaluator(
                        mQnsComponents[mSlotIndex],
                        NetworkCapabilities.NET_CAPABILITY_EIMS,
                        mRestrictManager,
                        mDataConnectionStatusTracker,
                        mSlotIndex);
        waitForLastHandlerAction(mAne.mHandler);

        mLatch = new CountDownLatch(1);
        mAne.registerForQualifiedNetworksChanged(mHandler, QUALIFIED_NETWORKS_CHANGED);
        mAne.onEmergencyPreferredTransportTypeChanged(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        waitForLastHandlerAction(mAne.mHandler);
        assertFalse(mLatch.await(500, TimeUnit.MILLISECONDS));
        assertNull(mQualifiedNetworksInfo);

        mLatch = new CountDownLatch(1);
        mAne.onDataConnectionStateChanged(
                new DataConnectionStatusTracker.DataConnectionChangedInfo(
                        EVENT_DATA_CONNECTION_DISCONNECTED, STATE_INACTIVE,
                        AccessNetworkConstants.TRANSPORT_TYPE_INVALID));
        waitForLastHandlerAction(mAne.mHandler);
        assertTrue(mLatch.await(100, TimeUnit.MILLISECONDS));
        assertTrue(
                mQualifiedNetworksInfo
                        .getAccessNetworkTypes()
                        .contains(AccessNetworkConstants.AccessNetworkType.IWLAN));

        QnsTelephonyListener.QnsTelephonyInfo info =
                mMockQnsTelephonyListener.new QnsTelephonyInfo();
        info.setCellularAvailable(true);
        info.setCoverage(false);
        info.setDataNetworkType(TelephonyManager.NETWORK_TYPE_LTE);
        info.setVoiceNetworkType(TelephonyManager.NETWORK_TYPE_LTE);
        info.setDataRegState(ServiceState.STATE_IN_SERVICE);
        QnsTelephonyInfoIms infoIms =
                mMockQnsTelephonyListener.new QnsTelephonyInfoIms(info, true, true, false, false);
        mAne.onQnsTelephonyInfoChanged(infoIms);
        when(mDataConnectionStatusTracker.isInactiveState()).thenReturn(false);
        mQualifiedNetworksInfo = null;

        mLatch = new CountDownLatch(1);
        mAne.onEmergencyPreferredTransportTypeChanged(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        waitForLastHandlerAction(mAne.mHandler);
        assertFalse(mLatch.await(500, TimeUnit.MILLISECONDS));
        assertNull(mQualifiedNetworksInfo);

        mLatch = new CountDownLatch(1);
        mAne.onDataConnectionStateChanged(
                new DataConnectionStatusTracker.DataConnectionChangedInfo(
                        EVENT_DATA_CONNECTION_FAILED, STATE_INACTIVE,
                        AccessNetworkConstants.TRANSPORT_TYPE_INVALID));
        waitForLastHandlerAction(mAne.mHandler);
        assertTrue(mLatch.await(100, TimeUnit.MILLISECONDS));
        assertTrue(
                mQualifiedNetworksInfo
                        .getAccessNetworkTypes()
                        .contains(AccessNetworkConstants.AccessNetworkType.EUTRAN));
    }

    @Test
    public void testReportSatisfiedAccessNetworkTypesByState()
            throws InterruptedException, NoSuchFieldException, IllegalAccessException {
        waitForLastHandlerAction(mAne.mHandler);
        doNothing().when(mRestrictManager).setQnsCallType(anyInt());
        Message.obtain(
                        mAne.mHandler,
                        EVENT_SET_CALL_TYPE,
                        new QnsAsyncResult(null, QnsConstants.CALL_TYPE_IDLE, null))
                .sendToTarget();
        Message.obtain(
                        mAne.mHandler,
                        EVENT_IWLAN_NETWORK_STATUS_CHANGED,
                        new QnsAsyncResult(
                                null,
                                mMockIwlanNetworkStatusTracker
                                .new IwlanAvailabilityInfo(true, false),
                                null))
                .sendToTarget();

        generateAnspPolicyMap();
        mockCurrentQuality(-70, -90);
        mAne.mHandler.sendEmptyMessage(QnsEventDispatcher.QNS_EVENT_WFC_MODE_TO_WIFI_PREFERRED);
        when(mDataConnectionStatusTracker.isInactiveState()).thenReturn(true);
        when(mRestrictManager.isRestricted(anyInt())).thenReturn(false);
        List<Integer> accessNetworks = new ArrayList<>();
        accessNetworks.add(AccessNetworkConstants.AccessNetworkType.EUTRAN);
        mAne.registerForQualifiedNetworksChanged(mHandler, QUALIFIED_NETWORKS_CHANGED);
        waitForLastHandlerAction(mAne.mHandler);
        mLatch = new CountDownLatch(1);
        mAne.reportSatisfiedAccessNetworkTypesByState(accessNetworks, true);
        assertTrue(mLatch.await(500, TimeUnit.MILLISECONDS));
        assertTrue(
                mQualifiedNetworksInfo
                        .getAccessNetworkTypes()
                        .contains(AccessNetworkConstants.AccessNetworkType.EUTRAN));
        verify(mMockWifiQm).updateThresholdsForNetCapability(anyInt(), anyInt(), isNotNull());
    }

    private void generateAnspPolicyMap() throws NoSuchFieldException, IllegalAccessException {
        mTestAnspPolicyMap = new HashMap<>();

        PreCondition p1 =
                new PreCondition(
                        QnsConstants.CALL_TYPE_IDLE,
                        QnsConstants.WIFI_PREF,
                        QnsConstants.COVERAGE_HOME);
        PreCondition p2 =
                new PreCondition(
                        QnsConstants.CALL_TYPE_IDLE,
                        QnsConstants.CELL_PREF,
                        QnsConstants.COVERAGE_HOME);

        List<ThresholdGroup> tg1 =
                generateTestThresholdGroups(
                        AccessNetworkConstants.AccessNetworkType.IWLAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                        QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                        -65);
        List<ThresholdGroup> tg2 =
                generateTestThresholdGroups(
                        AccessNetworkConstants.AccessNetworkType.EUTRAN,
                        SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                        QnsConstants.THRESHOLD_EQUAL_OR_LARGER,
                        -91);

        AccessNetworkSelectionPolicy ansp1 =
                new AccessNetworkSelectionPolicy(
                        NetworkCapabilities.NET_CAPABILITY_IMS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                        p1,
                        tg1);
        List<AccessNetworkSelectionPolicy> anspList = new ArrayList<>();
        anspList.add(ansp1);

        AccessNetworkSelectionPolicy ansp2 =
                new AccessNetworkSelectionPolicy(
                        NetworkCapabilities.NET_CAPABILITY_IMS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        p2,
                        tg2);
        List<AccessNetworkSelectionPolicy> anspList2 = new ArrayList<>();
        anspList2.add(ansp2);

        mTestAnspPolicyMap.put(p1, anspList);
        mTestAnspPolicyMap.put(p2, anspList2);
        Field f = AccessNetworkEvaluator.class.getDeclaredField("mAnspPolicyMap");
        f.setAccessible(true);
        f.set(mAne, mTestAnspPolicyMap);
    }

    private void mockCurrentQuality(int wifi, int cellular) {
        when(mMockWifiQm.getCurrentQuality(anyInt(), anyInt()))
                .thenAnswer((Answer<Integer>) invocation -> {
                    int quality = -255;
                    switch ((int) invocation.getArgument(0)) {
                        case AccessNetworkConstants.AccessNetworkType.EUTRAN:
                            quality = cellular;
                            break;
                        case AccessNetworkConstants.AccessNetworkType.IWLAN:
                            quality = wifi;
                            break;
                    }
                    return quality;
                });
    }

    private List<ThresholdGroup> generateTestThresholdGroups(
            int accessNetwork, int measType, int matchType, int threshold) {
        List<ThresholdGroup> thgroups = new ArrayList<>();
        List<Threshold> ths = new ArrayList<>();
        ths.add(
                new Threshold(
                        accessNetwork,
                        measType,
                        threshold,
                        matchType,
                        QnsConstants.DEFAULT_WIFI_BACKHAUL_TIMER));
        thgroups.add(new ThresholdGroup(ths));

        return thgroups;
    }

    @Test
    public void testOnProvisioningInfoChanged_LTE() throws Exception {
        QnsProvisioningInfo lastInfo = getQnsProvisioningInfo();
        setObject(AccessNetworkEvaluator.class, "mLastProvisioningInfo", mAne, lastInfo);

        QnsProvisioningInfo info = getQnsProvisioningInfo();
        ConcurrentHashMap<Integer, Integer> integerItems = getProvisioningItems();

        integerItems.put(ProvisioningManager.KEY_LTE_THRESHOLD_1, -100); // bad
        integerItems.put(ProvisioningManager.KEY_LTE_THRESHOLD_2, -120); // worst
        integerItems.put(ProvisioningManager.KEY_LTE_THRESHOLD_3, -85); // good

        // update new provisioning info
        setObject(QnsProvisioningInfo.class, "mIntegerItems", info, integerItems);

        Message.obtain(
                        mAne.mHandler,
                        EVENT_PROVISIONING_INFO_CHANGED,
                        new QnsAsyncResult(null, info, null))
                .sendToTarget();
        waitForLastHandlerAction(mAne.mHandler);
        verify(mMockQnsConfigManager, timeout(500).times(3)).setQnsProvisioningInfo(info);
    }

    @Test
    public void testOnProvisioningInfoChanged_Wifi() throws Exception {
        QnsProvisioningInfo lastInfo = getQnsProvisioningInfo();
        setObject(AccessNetworkEvaluator.class, "mLastProvisioningInfo", mAne, lastInfo);

        QnsProvisioningInfo info = getQnsProvisioningInfo();
        ConcurrentHashMap<Integer, Integer> integerItems = getProvisioningItems();

        integerItems.put(ProvisioningManager.KEY_WIFI_THRESHOLD_A, -75); // good
        integerItems.put(ProvisioningManager.KEY_WIFI_THRESHOLD_B, -90); // bad

        // update new provisioning info
        setObject(QnsProvisioningInfo.class, "mIntegerItems", info, integerItems);
        Message.obtain(
                        mAne.mHandler,
                        EVENT_PROVISIONING_INFO_CHANGED,
                        new QnsAsyncResult(null, info, null))
                .sendToTarget();
        waitForLastHandlerAction(mAne.mHandler);
        verify(mMockQnsConfigManager, timeout(500).times(2)).setQnsProvisioningInfo(info);
    }

    @Test
    public void testOnProvisioningInfoChanged_ePDG() throws Exception {
        waitForLastHandlerAction(mAne.mHandler);
        QnsProvisioningInfo lastInfo = getQnsProvisioningInfo();
        setObject(AccessNetworkEvaluator.class, "mLastProvisioningInfo", mAne, lastInfo);

        QnsProvisioningInfo info = getQnsProvisioningInfo();
        ConcurrentHashMap<Integer, Integer> integerItems = getProvisioningItems();
        integerItems.put(ProvisioningManager.KEY_LTE_EPDG_TIMER_SEC, 10000);
        integerItems.put(ProvisioningManager.KEY_WIFI_EPDG_TIMER_SEC, 20000);

        // update new provisioning info
        setObject(QnsProvisioningInfo.class, "mIntegerItems", info, integerItems);
        Message.obtain(
                        mAne.mHandler,
                        EVENT_PROVISIONING_INFO_CHANGED,
                        new QnsAsyncResult(null, info, null))
                .sendToTarget();
        waitForLastHandlerAction(mAne.mHandler);
        verify(mMockQnsConfigManager, times(2)).setQnsProvisioningInfo(info);
    }

    private QnsProvisioningInfo getQnsProvisioningInfo() throws Exception {
        QnsProvisioningInfo info = new QnsProvisioningInfo();
        ConcurrentHashMap<Integer, Integer> integerItems = getProvisioningItems();
        setObject(QnsProvisioningInfo.class, "mIntegerItems", info, integerItems);
        return info;
    }

    private ConcurrentHashMap<Integer, Integer> getProvisioningItems() {
        ConcurrentHashMap<Integer, Integer> integerItems = new ConcurrentHashMap<>();
        integerItems.put(ProvisioningManager.KEY_LTE_THRESHOLD_1, -95); // bad
        integerItems.put(ProvisioningManager.KEY_LTE_THRESHOLD_2, -110); // worst
        integerItems.put(ProvisioningManager.KEY_LTE_THRESHOLD_3, -80); // good
        integerItems.put(ProvisioningManager.KEY_WIFI_THRESHOLD_A, -70); // good
        integerItems.put(ProvisioningManager.KEY_WIFI_THRESHOLD_B, -85); // bad
        integerItems.put(ProvisioningManager.KEY_LTE_EPDG_TIMER_SEC, -80);
        integerItems.put(ProvisioningManager.KEY_WIFI_EPDG_TIMER_SEC, -80);
        return integerItems;
    }

    @Test
    public void testIsAllowed_WLAN() throws Exception {
        waitForLastHandlerAction(mAne.mHandler);
        int transport = AccessNetworkConstants.TRANSPORT_TYPE_WLAN;
        mAne.onTryWfcConnectionStateChanged(true);
        when(mMockQnsEventDispatcher.isAirplaneModeToggleOn()).thenReturn(true);
        when(mMockQnsConfigManager.allowWFCOnAirplaneModeOn()).thenReturn(false, true);
        Method method = AccessNetworkEvaluator.class.getDeclaredMethod("isAllowed", int.class);
        method.setAccessible(true);
        assertFalse((Boolean) method.invoke(mAne, transport));

        when(mMockIwlanNetworkStatusTracker.isInternationalRoaming(mSlotIndex)).thenReturn(true);
        when(mMockQnsConfigManager.blockIwlanInInternationalRoamWithoutWwan())
                .thenReturn(true, false);
        assertFalse((Boolean) method.invoke(mAne, transport));

        when(mMockQnsConfigManager.getRatPreference(mNetCapability))
                .thenAnswer(pref -> mRatPreference);

        // RAT_PREFERENCE_DEFAULT
        mRatPreference = QnsConstants.RAT_PREFERENCE_DEFAULT;
        assertTrue((Boolean) method.invoke(mAne, transport));

        // RAT_PREFERENCE_WIFI_ONLY
        mRatPreference = QnsConstants.RAT_PREFERENCE_WIFI_ONLY;
        assertTrue((Boolean) method.invoke(mAne, transport));

        mRatPreference = QnsConstants.RAT_PREFERENCE_WIFI_WHEN_WFC_AVAILABLE;
        when(mMockQnsImsManager.isImsRegistered(transport)).thenReturn(false, true);

        // RAT_PREFERENCE_WIFI_WHEN_WFC_AVAILABLE - ims not registered over WLAN
        assertFalse((Boolean) method.invoke(mAne, transport));

        // RAT_PREFERENCE_WIFI_WHEN_WFC_AVAILABLE - ims registered over WLAN
        assertTrue((Boolean) method.invoke(mAne, transport));

        // RAT_PREFERENCE_WIFI_WHEN_NO_CELLULAR - cellular available
        mRatPreference = QnsConstants.RAT_PREFERENCE_WIFI_WHEN_NO_CELLULAR;
        setObject(AccessNetworkEvaluator.class, "mCellularAvailable", mAne, true);
        assertFalse((Boolean) method.invoke(mAne, transport));

        // RAT_PREFERENCE_WIFI_WHEN_NO_CELLULAR - cellular not available
        mRatPreference = QnsConstants.RAT_PREFERENCE_WIFI_WHEN_NO_CELLULAR;
        setObject(AccessNetworkEvaluator.class, "mCellularAvailable", mAne, false);
        assertTrue((Boolean) method.invoke(mAne, transport));
    }

    @Test
    public void testIsAllowed_WWAN() throws Exception {
        waitForLastHandlerAction(mAne.mHandler);
        int transport = AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
        when(mMockQnsEventDispatcher.isAirplaneModeToggleOn()).thenReturn(true, false);
        Method method = AccessNetworkEvaluator.class.getDeclaredMethod("isAllowed", int.class);
        method.setAccessible(true);
        assertFalse((Boolean) method.invoke(mAne, transport));
        when(mMockQnsConfigManager.getRatPreference(mNetCapability))
                .thenAnswer(pref -> mRatPreference);

        // RAT_PREFERENCE_DEFAULT
        mRatPreference = QnsConstants.RAT_PREFERENCE_DEFAULT;
        assertTrue((Boolean) method.invoke(mAne, transport));

        // RAT_PREFERENCE_WIFI_ONLY
        mRatPreference = QnsConstants.RAT_PREFERENCE_WIFI_ONLY;
        assertFalse((Boolean) method.invoke(mAne, transport));

        // RAT_PREFERENCE_WIFI_WHEN_WFC_AVAILABLE
        mRatPreference = QnsConstants.RAT_PREFERENCE_WIFI_WHEN_WFC_AVAILABLE;
        when(mMockQnsImsManager.isImsRegistered(AccessNetworkConstants.TRANSPORT_TYPE_WLAN))
                .thenReturn(true, false);
        assertFalse((Boolean) method.invoke(mAne, transport));
        assertTrue((Boolean) method.invoke(mAne, transport));

        // RAT_PREFERENCE_WIFI_WHEN_NO_CELLULAR
        mRatPreference = QnsConstants.RAT_PREFERENCE_WIFI_WHEN_NO_CELLULAR;
        assertTrue((Boolean) method.invoke(mAne, transport));

        mRatPreference = QnsConstants.RAT_PREFERENCE_WIFI_WHEN_HOME_IS_NOT_AVAILABLE;

        // RAT_PREFERENCE_WIFI_WHEN_HOME_IS_NOT_AVAILABLE - no cellular
        setObject(AccessNetworkEvaluator.class, "mCellularAvailable", mAne, false);
        assertFalse((Boolean) method.invoke(mAne, transport));

        // RAT_PREFERENCE_WIFI_WHEN_HOME_IS_NOT_AVAILABLE - home
        setObject(AccessNetworkEvaluator.class, "mCellularAvailable", mAne, true);
        setObject(AccessNetworkEvaluator.class, "mCoverage", mAne, QnsConstants.COVERAGE_HOME);
        assertTrue((Boolean) method.invoke(mAne, transport));

        // RAT_PREFERENCE_WIFI_WHEN_HOME_IS_NOT_AVAILABLE - roaming
        setObject(AccessNetworkEvaluator.class, "mCoverage", mAne, QnsConstants.COVERAGE_ROAM);
        assertFalse((Boolean) method.invoke(mAne, transport));
    }

    @Test
    public void testGetMatchingPreconditionForEmergency() {
        AccessNetworkEvaluator mAneSos =
                new AccessNetworkEvaluator(
                        mQnsComponents[mSlotIndex],
                        NetworkCapabilities.NET_CAPABILITY_EIMS,
                        mRestrictManager,
                        mDataConnectionStatusTracker,
                        mSlotIndex);
        waitForLastHandlerAction(mAneSos.mHandler);
        AccessNetworkEvaluator mAneIms =
                new AccessNetworkEvaluator(
                        mQnsComponents[mSlotIndex],
                        NetworkCapabilities.NET_CAPABILITY_IMS,
                        mRestrictManager,
                        mDataConnectionStatusTracker,
                        mSlotIndex);
        waitForLastHandlerAction(mAneIms.mHandler);
        List<Integer> satisfiedAccessNetworkTypes = new ArrayList<>();
        satisfiedAccessNetworkTypes.add(AccessNetworkConstants.AccessNetworkType.EUTRAN);
        when(mDataConnectionStatusTracker.isInactiveState()).thenReturn(true);
        try {
            Field field = AccessNetworkEvaluator.class.getDeclaredField("mCallType");
            field.setAccessible(true);
            field.set(mAneSos, QnsConstants.CALL_TYPE_VOICE);
            field.set(mAneIms, QnsConstants.CALL_TYPE_VOICE);
        } catch (Exception e) {
        }
        mAneSos.reportSatisfiedAccessNetworkTypesByState(satisfiedAccessNetworkTypes, true);
        mAneIms.reportSatisfiedAccessNetworkTypesByState(satisfiedAccessNetworkTypes, true);
        ArrayList<AccessNetworkSelectionPolicy> matchedAnspSos = null;
        ArrayList<AccessNetworkSelectionPolicy> matchedAnspIms = null;
        try {
            Field field =
                    AccessNetworkEvaluator.class.getDeclaredField(
                            "mAccessNetworkSelectionPolicies");
            field.setAccessible(true);
            matchedAnspSos = (ArrayList<AccessNetworkSelectionPolicy>) field.get(mAneSos);
            matchedAnspIms = (ArrayList<AccessNetworkSelectionPolicy>) field.get(mAneIms);
        } catch (Exception e) {
            assertNotNull(matchedAnspSos);
            assertNotNull(matchedAnspIms);
        }

        assertNotNull(matchedAnspSos);
        assertNotNull(matchedAnspIms);
        assertTrue(matchedAnspSos.size() > 0);
        assertTrue(matchedAnspIms.size() > 0);
        assertEquals(
                QnsConstants.CALL_TYPE_VOICE,
                matchedAnspSos.get(0).getPreCondition().getCallType());
        assertEquals(
                QnsConstants.CALL_TYPE_VOICE,
                matchedAnspIms.get(0).getPreCondition().getCallType());

        try {
            Field field = AccessNetworkEvaluator.class.getDeclaredField("mCallType");
            field.setAccessible(true);
            field.set(mAneSos, QnsConstants.CALL_TYPE_EMERGENCY);
            field.set(mAneIms, QnsConstants.CALL_TYPE_EMERGENCY);
        } catch (Exception e) {
        }
        mAneSos.reportSatisfiedAccessNetworkTypesByState(satisfiedAccessNetworkTypes, true);
        mAneIms.reportSatisfiedAccessNetworkTypesByState(satisfiedAccessNetworkTypes, true);
        try {
            Field field =
                    AccessNetworkEvaluator.class.getDeclaredField(
                            "mAccessNetworkSelectionPolicies");
            field.setAccessible(true);
            matchedAnspSos = (ArrayList<AccessNetworkSelectionPolicy>) field.get(mAneSos);
            matchedAnspIms = (ArrayList<AccessNetworkSelectionPolicy>) field.get(mAneIms);
        } catch (Exception e) {
            assertNotNull(matchedAnspSos);
            assertNotNull(matchedAnspIms);
        }

        assertNotNull(matchedAnspSos);
        assertNotNull(matchedAnspIms);
        assertTrue(matchedAnspSos.size() > 0);
        assertTrue(matchedAnspIms.size() > 0);
        assertEquals(
                QnsConstants.CALL_TYPE_VOICE,
                matchedAnspSos.get(0).getPreCondition().getCallType());
    }

    @Test
    public void testEventImsRegistrationStateChanged() {
        waitForLastHandlerAction(mAne.mHandler);
        when(mMockQnsConfigManager.isTransportTypeSelWithoutSSInRoamSupported()).thenReturn(false);
        when(mMockQnsConfigManager.getRatPreference(NetworkCapabilities.NET_CAPABILITY_IMS))
                .thenReturn(QnsConstants.RAT_PREFERENCE_DEFAULT);

        Message.obtain(
                        mAne.mHandler,
                        EVENT_IMS_REGISTRATION_STATE_CHANGED,
                        new QnsAsyncResult(null, mMockImsRegistrationState, null))
                .sendToTarget();

        waitForLastHandlerAction(mAne.mHandler);
        verify(mMockImsRegistrationState, never()).getTransportType();
        verify(mMockImsRegistrationState, never()).getEvent();

        when(mMockQnsConfigManager.getRatPreference(NetworkCapabilities.NET_CAPABILITY_IMS))
                .thenReturn(QnsConstants.RAT_PREFERENCE_WIFI_WHEN_WFC_AVAILABLE);
        when(mMockImsRegistrationState.getTransportType())
                .thenReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        when(mMockImsRegistrationState.getEvent())
                .thenReturn(QnsConstants.IMS_REGISTRATION_CHANGED_REGISTERED);

        Message.obtain(
                        mAne.mHandler,
                        EVENT_IMS_REGISTRATION_STATE_CHANGED,
                        new QnsAsyncResult(null, mMockImsRegistrationState, null))
                .sendToTarget();

        waitForLastHandlerAction(mAne.mHandler);
        verify(mMockImsRegistrationState, times(1)).getTransportType();
        verify(mMockImsRegistrationState, times(1)).getEvent();
    }

    @Test
    public void testValidateWfcSettingsAndUpdate() {
        mWfcMode = QnsConstants.CELL_PREF;
        mWfcModeRoaming = QnsConstants.CELL_PREF;
        mWfcEnabledByUser = false;
        assertFalse(mAne.isWfcEnabled());

        mWfcEnabledByUser = true;
        mWfcRoamingEnabled = true;
        assertTrue(mAne.isWfcEnabled());

        QnsTelephonyListener.QnsTelephonyInfo info =
                mMockQnsTelephonyListener.new QnsTelephonyInfo();
        info.setCellularAvailable(true);
        info.setCoverage(true);
        info.setDataNetworkType(TelephonyManager.NETWORK_TYPE_LTE);
        info.setVoiceNetworkType(TelephonyManager.NETWORK_TYPE_LTE);
        info.setDataRegState(ServiceState.STATE_IN_SERVICE);
        QnsTelephonyInfoIms infoIms =
                mMockQnsTelephonyListener.new QnsTelephonyInfoIms(info, true, true, false, false);
        mAne.onQnsTelephonyInfoChanged(infoIms);

        mWfcEnabledByPlatform = false;
        assertFalse(mAne.isWfcEnabled());

        mWfcEnabledByPlatform = true;
        mWfcRoamingEnabled = true;
        mWfcRoamingEnabledByUser = true;
        assertTrue(mAne.isWfcEnabled());
    }

    @Test
    public void testEvaluateAgainWhenRebuild() throws InterruptedException {
        mLatch = new CountDownLatch(3);

        // #1 evaluate
        mAne.registerForQualifiedNetworksChanged(mHandler, QUALIFIED_NETWORKS_CHANGED);

        mAne.mIwlanAvailable = true;
        mWfcEnabledByUser = true;

        waitForLastHandlerAction(mAne.mHandler);

        // #2 report an empty
        // #3 report once again when iwlan is available.
        mAne.rebuild();

        assertTrue(mLatch.await(3, TimeUnit.SECONDS));
        List<Integer> accessNetworks = new ArrayList<>();
        accessNetworks.add(AccessNetworkConstants.AccessNetworkType.IWLAN);
        assertEquals(accessNetworks, mQualifiedNetworksInfo.getAccessNetworkTypes());
    }

    @Test
    public void testEvaluationOnCst_MmsRatPreferenceWifiWhenWfcAvailable() throws Exception {
        mAne =
                new AccessNetworkEvaluator(
                        mQnsComponents[mSlotIndex],
                        NetworkCapabilities.NET_CAPABILITY_MMS,
                        mRestrictManager,
                        mDataConnectionStatusTracker,
                        mSlotIndex);
        mAne.onIwlanNetworkStatusChanged(
                mMockIwlanNetworkStatusTracker.new IwlanAvailabilityInfo(true, true));
        mAne.registerForQualifiedNetworksChanged(mHandler, QUALIFIED_NETWORKS_CHANGED);
        waitForLastHandlerAction(mAne.mHandler);

        when(mMockQnsConfigManager.getRatPreference(NetworkCapabilities.NET_CAPABILITY_MMS))
                .thenReturn(QnsConstants.RAT_PREFERENCE_WIFI_WHEN_WFC_AVAILABLE);
        when(mMockQnsImsManager.isImsRegistered(AccessNetworkConstants.TRANSPORT_TYPE_WLAN))
                .thenReturn(true);
        setObject(AccessNetworkEvaluator.class, "mCellularAvailable", mAne, true);
        setObject(
                AccessNetworkEvaluator.class,
                "mCellularAccessNetworkType",
                mAne,
                AccessNetworkConstants.AccessNetworkType.EUTRAN);
        when(mMockImsRegistrationState.getTransportType())
                .thenReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        when(mMockImsRegistrationState.getEvent())
                .thenReturn(QnsConstants.IMS_REGISTRATION_CHANGED_REGISTERED);

        Message.obtain(
                        mAne.mHandler,
                        EVENT_IMS_REGISTRATION_STATE_CHANGED,
                        new QnsAsyncResult(null, mMockImsRegistrationState, null))
                .sendToTarget();
        assertTrue(mLatch.await(400, TimeUnit.MILLISECONDS));

        List<Integer> accessNetworks = new ArrayList<>();
        accessNetworks.add(AccessNetworkConstants.AccessNetworkType.IWLAN);
        assertEquals(accessNetworks, mQualifiedNetworksInfo.getAccessNetworkTypes());
    }

    @Test
    public void testEventSipDialogSessionStateChanged() {
        waitForLastHandlerAction(mAne.mHandler);
        when(mMockQnsConfigManager.getSipDialogSessionPolicy())
                .thenReturn(QnsConstants.SIP_DIALOG_SESSION_POLICY_FOLLOW_VOICE_CALL);

        // #1. check init value
        assertFalse(mAne.getSipDialogSessionState());

        // #2. check handling message
        Message.obtain(
                        mAne.mHandler,
                        EVENT_SIP_DIALOG_SESSION_STATE_CHANGED,
                        new QnsAsyncResult(null, true, null))
                .sendToTarget();
        waitForLastHandlerAction(mAne.mHandler);
        assertTrue(mAne.getSipDialogSessionState());

        // #3. check calling direct method.
        mAne.onSipDialogSessionStateChanged(false);
        assertFalse(mAne.getSipDialogSessionState());
        mAne.onSipDialogSessionStateChanged(true);
        assertTrue(mAne.getSipDialogSessionState());
    }

    @Test
    public void testEventSipDialogSessionStateChangedByPolicy() {
        // #1. Check by config.
        when(mMockQnsConfigManager.getSipDialogSessionPolicy())
                .thenReturn(QnsConstants.SIP_DIALOG_SESSION_POLICY_NONE);
        mAne.onSipDialogSessionStateChanged(false);
        mAne.onSipDialogSessionStateChanged(true);
        // if policy is SIP_DIALOG_SESSION_POLICY_NONE, ANE.mSipDialogSessionState should be false.
        assertFalse(mAne.getSipDialogSessionState());

        // #2. Check applying SipDialogSessionState by SipDialogSessionPolicy config. (VOICE)
        when(mMockQnsConfigManager.getSipDialogSessionPolicy())
                .thenReturn(QnsConstants.SIP_DIALOG_SESSION_POLICY_FOLLOW_VOICE_CALL);
        mAne.onSipDialogSessionStateChanged(false);
        mAne.onSipDialogSessionStateChanged(true);
        assertTrue(mAne.getSipDialogSessionState());

        // #3. Check applying SipDialogSessionState by SipDialogSessionPolicy config. (VIDEO)
        when(mMockQnsConfigManager.getSipDialogSessionPolicy())
                .thenReturn(QnsConstants.SIP_DIALOG_SESSION_POLICY_FOLLOW_VIDEO_CALL);
        mAne.onSipDialogSessionStateChanged(false);
        mAne.onSipDialogSessionStateChanged(true);
        assertTrue(mAne.getSipDialogSessionState());
    }

    @Test
    public void testEventSipDialogSessionStateChanged_verifyWithAnspVoice() {
        when(mMockQnsConfigManager.getPolicy(anyInt(), any())).thenReturn(null);
        when(mMockQnsConfigManager.getSipDialogSessionPolicy())
                .thenReturn(QnsConstants.SIP_DIALOG_SESSION_POLICY_FOLLOW_VOICE_CALL);
        mAne.onSipDialogSessionStateChanged(true);

        List<Integer> satisfiedAccessNetworkTypes = new ArrayList<>();
        satisfiedAccessNetworkTypes.add(AccessNetworkConstants.AccessNetworkType.EUTRAN);
        when(mDataConnectionStatusTracker.isInactiveState()).thenReturn(true);
        mAne.reportSatisfiedAccessNetworkTypesByState(satisfiedAccessNetworkTypes, true);
        mAne.registerForQualifiedNetworksChanged(mHandler, QUALIFIED_NETWORKS_CHANGED);
        ArrayList<AccessNetworkSelectionPolicy> matchedAnsp;

        // #1. SIP_DIALOG_SESSION_POLICY_FOLLOW_VOICE_CALL
        matchedAnsp = null;
        try {
            Field field =
                    AccessNetworkEvaluator.class.getDeclaredField(
                            "mAccessNetworkSelectionPolicies");
            field.setAccessible(true);
            matchedAnsp = (ArrayList<AccessNetworkSelectionPolicy>) field.get(mAne);
        } catch (Exception e) {
        }
        assertNotNull(matchedAnsp);
        assertFalse(matchedAnsp.isEmpty());
        assertEquals(
                QnsConstants.CALL_TYPE_VOICE, matchedAnsp.get(0).getPreCondition().getCallType());
    }

    @Test
    public void testEventSipDialogSessionStateChanged_verifyWithAnspVideo() {
        when(mMockQnsConfigManager.getPolicy(anyInt(), any())).thenReturn(null);
        when(mMockQnsConfigManager.getSipDialogSessionPolicy())
                .thenReturn(QnsConstants.SIP_DIALOG_SESSION_POLICY_FOLLOW_VIDEO_CALL);
        mAne.onSipDialogSessionStateChanged(true);

        List<Integer> satisfiedAccessNetworkTypes = new ArrayList<>();
        satisfiedAccessNetworkTypes.add(AccessNetworkConstants.AccessNetworkType.EUTRAN);
        when(mDataConnectionStatusTracker.isInactiveState()).thenReturn(true);
        mAne.reportSatisfiedAccessNetworkTypesByState(satisfiedAccessNetworkTypes, true);
        mAne.registerForQualifiedNetworksChanged(mHandler, QUALIFIED_NETWORKS_CHANGED);
        ArrayList<AccessNetworkSelectionPolicy> matchedAnsp;

        // #1. SIP_DIALOG_SESSION_POLICY_FOLLOW_VIDEO_CALL
        matchedAnsp = null;
        try {
            Field field =
                    AccessNetworkEvaluator.class.getDeclaredField(
                            "mAccessNetworkSelectionPolicies");
            field.setAccessible(true);
            matchedAnsp = (ArrayList<AccessNetworkSelectionPolicy>) field.get(mAne);
        } catch (Exception e) {
        }
        assertNotNull(matchedAnsp);
        assertFalse(matchedAnsp.isEmpty());
        assertEquals(
                QnsConstants.CALL_TYPE_VIDEO, matchedAnsp.get(0).getPreCondition().getCallType());
    }

    @Test
    public void testDataConnectionDisconnectedOnSos_ResettingThresholds() {
        AccessNetworkEvaluator aneSos =
                new AccessNetworkEvaluator(
                        mQnsComponents[mSlotIndex],
                        NetworkCapabilities.NET_CAPABILITY_EIMS,
                        mRestrictManager,
                        mDataConnectionStatusTracker,
                        mSlotIndex);
        aneSos.registerForQualifiedNetworksChanged(mHandler, QUALIFIED_NETWORKS_CHANGED);
        waitForLastHandlerAction(aneSos.mHandler);

        aneSos.onDataConnectionStateChanged(
                new DataConnectionStatusTracker.DataConnectionChangedInfo(
                        DataConnectionStatusTracker.EVENT_DATA_CONNECTION_DISCONNECTED,
                        DataConnectionStatusTracker.STATE_INACTIVE,
                        AccessNetworkConstants.TRANSPORT_TYPE_INVALID));
        waitForLastHandlerAction(aneSos.mHandler);

        aneSos.onDataConnectionStateChanged(
                new DataConnectionStatusTracker.DataConnectionChangedInfo(
                        DataConnectionStatusTracker.EVENT_DATA_CONNECTION_FAILED,
                        DataConnectionStatusTracker.STATE_INACTIVE,
                        AccessNetworkConstants.TRANSPORT_TYPE_INVALID));
        waitForLastHandlerAction(aneSos.mHandler);

        verify(mMockWifiQm, times(2)).updateThresholdsForNetCapability(
                NetworkCapabilities.NET_CAPABILITY_EIMS, mSlotIndex, null);
    }

    @Test
    public void testOnImsCallDisconnectCauseChanged() {
        // ANE takes time to build ANSP.
        waitForLastHandlerAction(mAne.mHandler);
        when(mRestrictManager.isRestricted(anyInt())).thenReturn(false);
        when(mRestrictManager.isAllowedOnSingleTransport(anyInt())).thenReturn(true);
        when(mDataConnectionStatusTracker.getLastTransportType())
                .thenReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        // make iwlan and cellular available
        IwlanNetworkStatusTracker.IwlanAvailabilityInfo iwlanInfo =
                mMockIwlanNetworkStatusTracker.new IwlanAvailabilityInfo(true, false);
        mAne.onIwlanNetworkStatusChanged(iwlanInfo);
        QnsTelephonyListener.QnsTelephonyInfo cellInfo =
                mMockQnsTelephonyListener.new QnsTelephonyInfo();
        cellInfo.setCellularAvailable(true);
        cellInfo.setCoverage(true);
        cellInfo.setDataNetworkType(TelephonyManager.NETWORK_TYPE_LTE);
        cellInfo.setVoiceNetworkType(TelephonyManager.NETWORK_TYPE_LTE);
        cellInfo.setDataRegState(ServiceState.STATE_IN_SERVICE);
        mAne.onQnsTelephonyInfoChanged(cellInfo);
        assertTrue(mAne.mIwlanAvailable);
        assertTrue(mAne.mCellularAvailable);

        // send ims call drop event.
        ImsReasonInfo imsReasonInfo = new ImsReasonInfo(ImsReasonInfo.CODE_MEDIA_NO_DATA, 0);
        Message.obtain(mAne.mHandler, EVENT_IMS_CALL_DISCONNECT_CAUSE_CHANGED,
                new QnsAsyncResult(null, imsReasonInfo, null)).sendToTarget();
        waitForLastHandlerAction(mAne.mHandler);

        verify(mMockQnsMetrics, atLeast(1))
                .reportAtomForImsCallDropStats(
                        anyInt(), anyInt(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    public void testEvaluateSpecificReasonToString() throws Exception {
        Method method = AccessNetworkEvaluator.class.getDeclaredMethod(
                "evaluateSpecificReasonToString", int.class);
        method.setAccessible(true);

        IntStream.rangeClosed(0, 4).forEach(i -> {
            try {
                assertTrue(((String) method.invoke(mAne, i)).startsWith(
                        "EVALUATE_SPECIFIC_REASON_"));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
