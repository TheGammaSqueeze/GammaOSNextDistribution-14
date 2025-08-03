/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.wifi;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIfaceCallback.WpsConfigError;
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaIfaceCallback.WpsErrorIndication;
import android.net.DscpPolicy;
import android.net.MacAddress;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiSsid;
import android.os.Handler;
import android.os.Message;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.MboOceController.BtmFrameData;
import com.android.server.wifi.SupplicantStaIfaceHal.QosPolicyClassifierParams;
import com.android.server.wifi.SupplicantStaIfaceHal.QosPolicyRequest;
import com.android.server.wifi.hotspot2.AnqpEvent;
import com.android.server.wifi.hotspot2.IconEvent;
import com.android.server.wifi.hotspot2.WnmData;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Unit tests for {@link com.android.server.wifi.WifiMonitor}.
 */
@SmallTest
public class WifiMonitorTest extends WifiBaseTest {
    private static final String WLAN_IFACE_NAME = "wlan0";
    private static final String SECOND_WLAN_IFACE_NAME = "wlan1";
    private static final String[] GSM_AUTH_DATA = { "45adbc", "fead45", "0x3452"};
    private static final String[] UMTS_AUTH_DATA = { "fead45", "0x3452"};
    private static final String BSSID = "fe:45:23:12:12:0a";
    private static final int NETWORK_ID = 5;
    private static final String SSID = "\"test124\"";
    private static final long BSSID_LONG = 0xf3452312120aL;
    private static final String PASSPOINT_URL = "https://www.google.com/";
    private WifiMonitor mWifiMonitor;
    private TestLooper mLooper;
    private Handler mHandlerSpy;
    private Handler mSecondHandlerSpy;

    @Before
    public void setUp() throws Exception {
        mWifiMonitor = new WifiMonitor();
        mLooper = new TestLooper();
        mHandlerSpy = spy(new Handler(mLooper.getLooper()));
        mSecondHandlerSpy = spy(new Handler(mLooper.getLooper()));
        mWifiMonitor.setMonitoring(WLAN_IFACE_NAME, true);
    }

    /**
     * Broadcast WPS failure event test.
     */
    @Test
    public void testBroadcastWpsEventFailDueToErrorTkipOnlyProhibhited() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.WPS_FAIL_EVENT, mHandlerSpy);
        mWifiMonitor.broadcastWpsFailEvent(
                WLAN_IFACE_NAME, WpsConfigError.NO_ERROR,
                WpsErrorIndication.SECURITY_TKIP_ONLY_PROHIBITED);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());

        Message message = messageCaptor.getValue();
        assertEquals(WifiMonitor.WPS_FAIL_EVENT, message.what);
        assertEquals(WifiManager.WPS_TKIP_ONLY_PROHIBITED, message.arg1);
        assertEquals(WLAN_IFACE_NAME, message.getData().getString(WifiMonitor.KEY_IFACE));
    }

    /**
     * Broadcast WPS failure event test.
     */
    @Test
    public void testBroadcastWpsEventFailDueToErrorWepProhibhited() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.WPS_FAIL_EVENT, mHandlerSpy);
        mWifiMonitor.broadcastWpsFailEvent(
                WLAN_IFACE_NAME, WpsConfigError.NO_ERROR,
                WpsErrorIndication.SECURITY_WEP_PROHIBITED);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.WPS_FAIL_EVENT, messageCaptor.getValue().what);
        assertEquals(WifiManager.WPS_WEP_PROHIBITED, messageCaptor.getValue().arg1);
    }

    /**
     * Broadcast WPS failure event test.
     */
    @Test
    public void testBroadcastWpsEventFailDueToConfigAuthError() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.WPS_FAIL_EVENT, mHandlerSpy);
        mWifiMonitor.broadcastWpsFailEvent(
                WLAN_IFACE_NAME, WpsConfigError.DEV_PASSWORD_AUTH_FAILURE,
                WpsErrorIndication.NO_ERROR);

        mLooper.dispatchAll();
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.WPS_FAIL_EVENT, messageCaptor.getValue().what);
        assertEquals(WifiManager.WPS_AUTH_FAILURE, messageCaptor.getValue().arg1);
    }

    /**
     * Broadcast WPS failure event test.
     */
    @Test
    public void testBroadcastWpsEventFailDueToConfigPbcOverlapError() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.WPS_FAIL_EVENT, mHandlerSpy);
        mWifiMonitor.broadcastWpsFailEvent(
                WLAN_IFACE_NAME, WpsConfigError.MULTIPLE_PBC_DETECTED,
                WpsErrorIndication.NO_ERROR);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.WPS_FAIL_EVENT, messageCaptor.getValue().what);
        assertEquals(WifiManager.WPS_OVERLAP_ERROR, messageCaptor.getValue().arg1);
    }

    /**
     * Broadcast WPS failure event test.
     */
    @Test
    public void testBroadcastWpsEventFailDueToConfigError() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.WPS_FAIL_EVENT, mHandlerSpy);
        mWifiMonitor.broadcastWpsFailEvent(
                WLAN_IFACE_NAME, WpsConfigError.MSG_TIMEOUT,
                WpsErrorIndication.NO_ERROR);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.WPS_FAIL_EVENT, messageCaptor.getValue().what);
        assertEquals(WifiManager.ActionListener.FAILURE_INTERNAL_ERROR,
                messageCaptor.getValue().arg1);
        assertEquals(WpsConfigError.MSG_TIMEOUT, messageCaptor.getValue().arg2);
    }

    /**
     * Broadcast WPS success event test.
     */
    @Test
    public void testBroadcastWpsEventSuccess() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.WPS_SUCCESS_EVENT, mHandlerSpy);
        mWifiMonitor.broadcastWpsSuccessEvent(WLAN_IFACE_NAME);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.WPS_SUCCESS_EVENT, messageCaptor.getValue().what);
    }

    /**
     * Broadcast WPS overlap event test.
     */
    @Test
    public void testBroadcastWpsEventOverlap() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.WPS_OVERLAP_EVENT, mHandlerSpy);
        mWifiMonitor.broadcastWpsOverlapEvent(WLAN_IFACE_NAME);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.WPS_OVERLAP_EVENT, messageCaptor.getValue().what);
    }

    /**
     * Broadcast WPS timeout event test.
     */
    @Test
    public void testBroadcastWpsEventTimeout() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.WPS_TIMEOUT_EVENT, mHandlerSpy);
        mWifiMonitor.broadcastWpsTimeoutEvent(WLAN_IFACE_NAME);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.WPS_TIMEOUT_EVENT, messageCaptor.getValue().what);
    }

    /**
     * Broadcast ANQP done event test.
     */
    @Test
    public void testBroadcastAnqpDoneEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.ANQP_DONE_EVENT, mHandlerSpy);
        long bssid = 5;
        mWifiMonitor.broadcastAnqpDoneEvent(WLAN_IFACE_NAME, new AnqpEvent(bssid, null));
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.ANQP_DONE_EVENT, messageCaptor.getValue().what);
        assertEquals(bssid, ((AnqpEvent) messageCaptor.getValue().obj).getBssid());
        assertNull(((AnqpEvent) messageCaptor.getValue().obj).getElements());
    }

    /**
     * Broadcast Icon event test.
     */
    @Test
    public void testBroadcastIconDoneEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.RX_HS20_ANQP_ICON_EVENT, mHandlerSpy);
        long bssid = 5;
        String fileName = "test";
        int fileSize = 0;
        mWifiMonitor.broadcastIconDoneEvent(
                WLAN_IFACE_NAME, new IconEvent(bssid, fileName, fileSize, null));
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.RX_HS20_ANQP_ICON_EVENT, messageCaptor.getValue().what);
        assertEquals(bssid, ((IconEvent) messageCaptor.getValue().obj).getBSSID());
        assertEquals(fileName, ((IconEvent) messageCaptor.getValue().obj).getFileName());
        assertEquals(fileSize, ((IconEvent) messageCaptor.getValue().obj).getSize());
        assertNull(((IconEvent) messageCaptor.getValue().obj).getData());
    }

    /**
     * Broadcast network Gsm auth request test.
     */
    @Test
    public void testBroadcastNetworkGsmAuthRequestEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.SUP_REQUEST_SIM_AUTH, mHandlerSpy);
        int networkId = NETWORK_ID;
        String ssid = SSID;
        String[] data = GSM_AUTH_DATA;
        mWifiMonitor.broadcastNetworkGsmAuthRequestEvent(WLAN_IFACE_NAME, networkId, ssid, data);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.SUP_REQUEST_SIM_AUTH, messageCaptor.getValue().what);
        WifiCarrierInfoManager.SimAuthRequestData authData =
                (WifiCarrierInfoManager.SimAuthRequestData) messageCaptor.getValue().obj;
        assertEquals(networkId, authData.networkId);
        assertEquals(ssid, authData.ssid);
        assertEquals(WifiEnterpriseConfig.Eap.SIM, authData.protocol);
        assertArrayEquals(data, authData.data);
    }

    /**
     * Broadcast network Umts auth request test.
     */
    @Test
    public void testBroadcastNetworkUmtsAuthRequestEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.SUP_REQUEST_SIM_AUTH, mHandlerSpy);
        int networkId = NETWORK_ID;
        String ssid = SSID;
        String[] data = UMTS_AUTH_DATA;
        mWifiMonitor.broadcastNetworkUmtsAuthRequestEvent(WLAN_IFACE_NAME, networkId, ssid, data);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.SUP_REQUEST_SIM_AUTH, messageCaptor.getValue().what);
        WifiCarrierInfoManager.SimAuthRequestData authData =
                (WifiCarrierInfoManager.SimAuthRequestData) messageCaptor.getValue().obj;
        assertEquals(networkId, authData.networkId);
        assertEquals(ssid, authData.ssid);
        assertEquals(WifiEnterpriseConfig.Eap.AKA, authData.protocol);
        assertArrayEquals(data, authData.data);
    }

    /**
     * Broadcast pno scan results event test.
     */
    @Test
    public void testBroadcastPnoScanResultsEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.PNO_SCAN_RESULTS_EVENT, mHandlerSpy);
        mWifiMonitor.broadcastPnoScanResultEvent(WLAN_IFACE_NAME);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.PNO_SCAN_RESULTS_EVENT, messageCaptor.getValue().what);
    }

    /**
     * Broadcast Scan results event test.
     */
    @Test
    public void testBroadcastScanResultsEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.SCAN_RESULTS_EVENT, mHandlerSpy);
        mWifiMonitor.broadcastScanResultEvent(WLAN_IFACE_NAME);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.SCAN_RESULTS_EVENT, messageCaptor.getValue().what);
    }

    /**
     * Broadcast Scan failed event test.
     */
    @Test
    public void testBroadcastScanFailedEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.SCAN_FAILED_EVENT, mHandlerSpy);
        mWifiMonitor.broadcastScanFailedEvent(WLAN_IFACE_NAME, WifiScanner.REASON_UNSPECIFIED);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());

        assertEquals(WifiMonitor.SCAN_FAILED_EVENT, messageCaptor.getValue().what);
    }

    /**
     * Broadcast authentication failure test.
     */
    @Test
    public void testBroadcastAuthenticationFailureEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.AUTHENTICATION_FAILURE_EVENT, mHandlerSpy);
        int reason = WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD;
        MacAddress bssid = MacAddress.fromString(BSSID);
        AuthenticationFailureEventInfo expected = new AuthenticationFailureEventInfo(SSID, bssid,
                reason, -1);
        mWifiMonitor.broadcastAuthenticationFailureEvent(WLAN_IFACE_NAME, reason, -1,
                SSID, bssid);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.AUTHENTICATION_FAILURE_EVENT, messageCaptor.getValue().what);
        assertEquals(expected, messageCaptor.getValue().obj);
    }

    /**
     * Broadcast authentication failure test (EAP Error).
     */
    @Test
    public void testBroadcastAuthenticationFailureEapErrorEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.AUTHENTICATION_FAILURE_EVENT, mHandlerSpy);
        int reason = WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE;
        int errorCode = WifiNative.EAP_SIM_VENDOR_SPECIFIC_CERT_EXPIRED;
        MacAddress bssid = MacAddress.fromString(BSSID);
        AuthenticationFailureEventInfo expected = new AuthenticationFailureEventInfo(SSID, bssid,
                reason, errorCode);
        mWifiMonitor.broadcastAuthenticationFailureEvent(WLAN_IFACE_NAME, reason, errorCode,
                SSID, bssid);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.AUTHENTICATION_FAILURE_EVENT, messageCaptor.getValue().what);
        assertEquals(expected, messageCaptor.getValue().obj);
    }

    /**
     * Broadcast association rejection test.
     */
    @Test
    public void testBroadcastAssociationRejectionEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.ASSOCIATION_REJECTION_EVENT, mHandlerSpy);
        int status = 5;
        int deltaRssi = 10;
        int retryDelay = 25;
        AssocRejectEventInfo assocRejectInfo = new AssocRejectEventInfo(
                SSID,
                BSSID,
                status, false);
        mWifiMonitor.broadcastAssociationRejectionEvent(WLAN_IFACE_NAME, assocRejectInfo);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.ASSOCIATION_REJECTION_EVENT, messageCaptor.getValue().what);
        AssocRejectEventInfo assocRejectEventInfo =
                (AssocRejectEventInfo) messageCaptor.getValue().obj;
        assertNotNull(assocRejectEventInfo);
        assertEquals(status, assocRejectEventInfo.statusCode);
        assertFalse(assocRejectEventInfo.timedOut);
        assertEquals(SSID, assocRejectEventInfo.ssid);
        assertEquals(BSSID, assocRejectEventInfo.bssid);
        assertNull(assocRejectEventInfo.oceRssiBasedAssocRejectInfo);
        assertNull(assocRejectEventInfo.mboAssocDisallowedInfo);
    }

    /**
     * Broadcast associated bssid test.
     */
    @Test
    public void testBroadcastAssociatedBssidEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.ASSOCIATED_BSSID_EVENT, mHandlerSpy);
        String bssid = BSSID;
        mWifiMonitor.broadcastAssociatedBssidEvent(WLAN_IFACE_NAME, bssid);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.ASSOCIATED_BSSID_EVENT, messageCaptor.getValue().what);
        assertEquals(bssid, (String) messageCaptor.getValue().obj);
    }

    /**
     * Broadcast network connection test.
     */
    @Test
    public void testBroadcastNetworkConnectionEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.NETWORK_CONNECTION_EVENT, mHandlerSpy);
        int networkId = NETWORK_ID;
        WifiSsid wifiSsid = WifiSsid.fromBytes(new byte[]{'a', 'b', 'c'});
        String bssid = BSSID;
        mWifiMonitor.broadcastNetworkConnectionEvent(WLAN_IFACE_NAME, networkId, false,
                wifiSsid, bssid);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.NETWORK_CONNECTION_EVENT, messageCaptor.getValue().what);
        NetworkConnectionEventInfo info = (NetworkConnectionEventInfo) messageCaptor.getValue().obj;
        assertEquals(networkId, info.networkId);
        assertFalse(info.isFilsConnection);
        assertEquals(wifiSsid, info.wifiSsid);
        assertEquals(bssid, info.bssid);
    }

    /**
     * Broadcast network connection with akm test.
     */
    @Test
    public void testBroadcastNetworkConnectionEventWithAkm() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.NETWORK_CONNECTION_EVENT, mHandlerSpy);
        int networkId = NETWORK_ID;
        WifiSsid wifiSsid = WifiSsid.fromBytes(new byte[]{'a', 'b', 'c'});
        String bssid = BSSID;
        BitSet akm = new BitSet();
        akm.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        mWifiMonitor.broadcastNetworkConnectionEvent(WLAN_IFACE_NAME, networkId, false,
                wifiSsid, bssid, akm);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.NETWORK_CONNECTION_EVENT, messageCaptor.getValue().what);
        NetworkConnectionEventInfo info = (NetworkConnectionEventInfo) messageCaptor.getValue().obj;
        assertEquals(networkId, info.networkId);
        assertFalse(info.isFilsConnection);
        assertEquals(wifiSsid, info.wifiSsid);
        assertEquals(bssid, info.bssid);
        assertEquals(akm, info.keyMgmtMask);
    }

    /**
     * Broadcast network disconnection test.
     */
    @Test
    public void testBroadcastNetworkDisconnectionEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.NETWORK_DISCONNECTION_EVENT, mHandlerSpy);
        boolean local = true;
        int reason  = 5;
        String ssid = SSID;
        String bssid = BSSID;
        mWifiMonitor.broadcastNetworkDisconnectionEvent(
                WLAN_IFACE_NAME, local, reason, ssid, bssid);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.NETWORK_DISCONNECTION_EVENT, messageCaptor.getValue().what);
        DisconnectEventInfo disconnectEventInfo =
                (DisconnectEventInfo) messageCaptor.getValue().obj;
        assertNotNull(disconnectEventInfo);
        assertEquals(local, disconnectEventInfo.locallyGenerated);
        assertEquals(reason, disconnectEventInfo.reasonCode);
        assertEquals(ssid, disconnectEventInfo.ssid);
        assertEquals(bssid, disconnectEventInfo.bssid);
    }

    /**
     * Broadcast supplicant state change test.
     */
    @Test
    public void testBroadcastSupplicantStateChangeEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, mHandlerSpy);
        int networkId = NETWORK_ID;
        WifiSsid wifiSsid = WifiSsid.fromUtf8Text(SSID);
        String bssid = BSSID;
        SupplicantState newState = SupplicantState.ASSOCIATED;
        mWifiMonitor.broadcastSupplicantStateChangeEvent(
                WLAN_IFACE_NAME, networkId, wifiSsid, bssid, 2412, newState);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, messageCaptor.getValue().what);
        StateChangeResult result = (StateChangeResult) messageCaptor.getValue().obj;
        assertEquals(networkId, result.networkId);
        assertEquals(wifiSsid, result.wifiSsid);
        assertEquals(bssid, result.bssid);
        assertEquals(2412, result.frequencyMhz);
        assertEquals(newState, result.state);
    }

    /**
     * Broadcast message to two handlers test.
     */
    @Test
    public void testBroadcastEventToTwoHandlers() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.SUP_REQUEST_SIM_AUTH, mHandlerSpy);
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.SUP_REQUEST_SIM_AUTH, mSecondHandlerSpy);
        mWifiMonitor.broadcastNetworkGsmAuthRequestEvent(
                WLAN_IFACE_NAME, NETWORK_ID, SSID, GSM_AUTH_DATA);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.SUP_REQUEST_SIM_AUTH, messageCaptor.getValue().what);
        verify(mSecondHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.SUP_REQUEST_SIM_AUTH, messageCaptor.getValue().what);
    }

    /**
     * Broadcast message when iface is null.
     */
    @Test
    public void testBroadcastEventWhenIfaceIsNull() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.SUP_REQUEST_SIM_AUTH, mHandlerSpy);
        mWifiMonitor.broadcastNetworkGsmAuthRequestEvent(null, NETWORK_ID, SSID, GSM_AUTH_DATA);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.SUP_REQUEST_SIM_AUTH, messageCaptor.getValue().what);
    }
    /**
     * Broadcast message when iface handler is null.
     */
    @Test
    public void testBroadcastEventWhenIfaceHandlerIsNull() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.SUP_REQUEST_SIM_AUTH, mHandlerSpy);
        mWifiMonitor.broadcastNetworkGsmAuthRequestEvent(
                WLAN_IFACE_NAME, NETWORK_ID, SSID, GSM_AUTH_DATA);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.SUP_REQUEST_SIM_AUTH, messageCaptor.getValue().what);
    }

    @Test
    public void testDeregisterHandlerNotCrash() {
        mWifiMonitor.deregisterHandler(null, 0, null);
    }

    /**
     * Register a handler, send an event and then verify that the event is handled.
     * Unregister the handler, send an event and then verify the event is not handled.
     */
    @Test
    public void testDeregisterHandlerRemovesHandler() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.WPS_FAIL_EVENT, mHandlerSpy);
        mWifiMonitor.broadcastWpsFailEvent(
                WLAN_IFACE_NAME, WpsConfigError.NO_ERROR,
                WpsErrorIndication.SECURITY_TKIP_ONLY_PROHIBITED);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy, times(1)).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.WPS_FAIL_EVENT, messageCaptor.getValue().what);
        assertEquals(WifiManager.WPS_TKIP_ONLY_PROHIBITED, messageCaptor.getValue().arg1);
        mWifiMonitor.deregisterHandler(
                WLAN_IFACE_NAME, WifiMonitor.WPS_FAIL_EVENT, mHandlerSpy);
        mWifiMonitor.broadcastWpsFailEvent(
                WLAN_IFACE_NAME, WpsConfigError.NO_ERROR,
                WpsErrorIndication.SECURITY_TKIP_ONLY_PROHIBITED);
        mLooper.dispatchAll();

        verify(mHandlerSpy, times(1)).handleMessage(messageCaptor.capture());
    }

    /**
     * Broadcast Bss transition request frame handling event test.
     */
    @Test
    public void testBroadcastBssTmHandlingDoneEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.MBO_OCE_BSS_TM_HANDLING_DONE, mHandlerSpy);
        mWifiMonitor.broadcastBssTmHandlingDoneEvent(WLAN_IFACE_NAME, new BtmFrameData());
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.MBO_OCE_BSS_TM_HANDLING_DONE, messageCaptor.getValue().what);
    }

    /**
     * Broadcast fils network connection test.
     */
    @Test
    public void testBroadcastFilsNetworkConnectionEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.NETWORK_CONNECTION_EVENT, mHandlerSpy);
        int networkId = NETWORK_ID;
        WifiSsid wifiSsid = WifiSsid.fromBytes(new byte[]{'a', 'b', 'c'});
        String bssid = BSSID;
        mWifiMonitor.broadcastNetworkConnectionEvent(WLAN_IFACE_NAME, networkId, true,
                wifiSsid, bssid);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.NETWORK_CONNECTION_EVENT, messageCaptor.getValue().what);
        NetworkConnectionEventInfo info = (NetworkConnectionEventInfo) messageCaptor.getValue().obj;
        assertEquals(networkId, info.networkId);
        assertTrue(info.isFilsConnection);
        assertEquals(wifiSsid, info.wifiSsid);
        assertEquals(bssid, info.bssid);
    }

    /**
     * Broadcast Passpoint remediation event test.
     */
    @Test
    public void testBroadcastPasspointRemediationEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.HS20_REMEDIATION_EVENT, mHandlerSpy);
        WnmData wnmData = WnmData.createRemediationEvent(BSSID_LONG, PASSPOINT_URL, 0);
        mWifiMonitor.broadcastWnmEvent(WLAN_IFACE_NAME, wnmData);

        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.HS20_REMEDIATION_EVENT, messageCaptor.getValue().what);
        assertTrue(wnmData.equals(messageCaptor.getValue().obj));
    }

    /**
     * Broadcast Passpoint deauth imminent event test.
     */
    @Test
    public void testBroadcastPasspointDeauthImminentEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.HS20_DEAUTH_IMMINENT_EVENT, mHandlerSpy);
        WnmData wnmData = WnmData.createDeauthImminentEvent(BSSID_LONG, PASSPOINT_URL, true, 10);
        mWifiMonitor.broadcastWnmEvent(WLAN_IFACE_NAME, wnmData);

        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.HS20_DEAUTH_IMMINENT_EVENT, messageCaptor.getValue().what);
        assertTrue(wnmData.equals(messageCaptor.getValue().obj));
    }

    /**
     * Broadcast Passpoint terms & conditions acceptance required event test.
     */
    @Test
    public void testBroadcastPasspointTermsAndConditionsRequiredEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.HS20_TERMS_AND_CONDITIONS_ACCEPTANCE_REQUIRED_EVENT,
                mHandlerSpy);
        WnmData wnmData =
                WnmData.createTermsAndConditionsAccetanceRequiredEvent(BSSID_LONG, PASSPOINT_URL);
        mWifiMonitor.broadcastWnmEvent(WLAN_IFACE_NAME, wnmData);

        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.HS20_TERMS_AND_CONDITIONS_ACCEPTANCE_REQUIRED_EVENT,
                messageCaptor.getValue().what);
        assertTrue(wnmData.equals(messageCaptor.getValue().obj));
    }

    /**
     * Broadcast message when iface handler is null.
     */
    @Test
    public void testBroadcastTransitionDisableEvent() {
        final int indication = WifiMonitor.TDI_USE_WPA3_PERSONAL
                | WifiMonitor.TDI_USE_SAE_PK;
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.TRANSITION_DISABLE_INDICATION, mHandlerSpy);
        mWifiMonitor.broadcastTransitionDisableEvent(
                WLAN_IFACE_NAME, NETWORK_ID, indication);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.TRANSITION_DISABLE_INDICATION, messageCaptor.getValue().what);
        assertEquals(NETWORK_ID, messageCaptor.getValue().arg1);
        assertEquals(indication, messageCaptor.getValue().arg2);
    }

    /**
     * Broadcast Network not found event test.
     */
    @Test
    public void testBroadcastNetworkNotFoundEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.NETWORK_NOT_FOUND_EVENT, mHandlerSpy);
        mWifiMonitor.broadcastNetworkNotFoundEvent(WLAN_IFACE_NAME, SSID);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.NETWORK_NOT_FOUND_EVENT, messageCaptor.getValue().what);
        String ssid = (String) messageCaptor.getValue().obj;
        assertEquals(SSID, ssid);
    }

    /**
     * Broadcast Certification event.
     */
    @Test
    public void testBroadcastCertificateEvent() {
        final int depth = 2;
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.TOFU_CERTIFICATE_EVENT, mHandlerSpy);
        mWifiMonitor.broadcastCertificationEvent(
                WLAN_IFACE_NAME, NETWORK_ID, SSID, depth,
                new CertificateEventInfo(FakeKeys.CA_CERT0, "1234"));
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.TOFU_CERTIFICATE_EVENT, messageCaptor.getValue().what);
        assertEquals(NETWORK_ID, messageCaptor.getValue().arg1);
        assertEquals(depth, messageCaptor.getValue().arg2);
        CertificateEventInfo certEventInfo = (CertificateEventInfo) messageCaptor.getValue().obj;
        assertEquals(FakeKeys.CA_CERT0, certEventInfo.getCert());
        assertEquals("1234", certEventInfo.getCertHash());
    }

    /**
     * Broadcast Auxiliary Supplicant event.
     */
    @Test
    public void testBroadcastAuxiliarySupplicantEvent() {
        SupplicantEventInfo expectedInfo = new SupplicantEventInfo(
                SupplicantStaIfaceHal.SUPPLICANT_EVENT_EAP_METHOD_SELECTED,
                MacAddress.fromString(BSSID), "method=5");
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.AUXILIARY_SUPPLICANT_EVENT, mHandlerSpy);
        mWifiMonitor.broadcastAuxiliarySupplicantEvent(WLAN_IFACE_NAME, expectedInfo.eventCode,
                expectedInfo.bssid, expectedInfo.reasonString);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.AUXILIARY_SUPPLICANT_EVENT, messageCaptor.getValue().what);
        SupplicantEventInfo info = (SupplicantEventInfo) messageCaptor.getValue().obj;
        assertEquals(expectedInfo.eventCode, info.eventCode);
        assertEquals(expectedInfo.bssid, info.bssid);
        assertEquals(expectedInfo.reasonString, info.reasonString);
    }

    /**
     * Broadcast QoS policy reset event.
     */
    @Test
    public void testBroadcastQosPolicyResetEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.QOS_POLICY_RESET_EVENT, mHandlerSpy);
        mWifiMonitor.broadcastQosPolicyResetEvent(WLAN_IFACE_NAME);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.QOS_POLICY_RESET_EVENT, messageCaptor.getValue().what);
    }

    /**
     * Broadcast QoS policy request event.
     */
    @Test
    public void testBroadcastQosPolicyRequestEvent() {
        int dialogToken = 124;
        int numPolicyRequests = 5;
        List<QosPolicyRequest> policyRequestList = new ArrayList();
        for (int i = 0; i < numPolicyRequests; i++) {
            policyRequestList.add(new QosPolicyRequest(
                    (byte) i, SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD, (byte) 0,
                    new QosPolicyClassifierParams(false, null, false, null,
                            DscpPolicy.SOURCE_PORT_ANY, new int[]{125, 150},
                            DscpPolicy.PROTOCOL_ANY)));
        }

        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.QOS_POLICY_REQUEST_EVENT, mHandlerSpy);
        mWifiMonitor.broadcastQosPolicyRequestEvent(
                WLAN_IFACE_NAME, dialogToken, policyRequestList);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.QOS_POLICY_REQUEST_EVENT, messageCaptor.getValue().what);
        assertEquals(dialogToken, messageCaptor.getValue().arg1);
        assertEquals(numPolicyRequests,
                ((List<QosPolicyRequest>) messageCaptor.getValue().obj).size());
    }

    /**
     * Broadcast BSS frequency changed event test.
     */
    @Test
    public void testBroadcastBssFrequencyChangedEvent() {
        mWifiMonitor.registerHandler(
                WLAN_IFACE_NAME, WifiMonitor.BSS_FREQUENCY_CHANGED_EVENT, mHandlerSpy);
        mWifiMonitor.broadcastBssFrequencyChanged(WLAN_IFACE_NAME, 2412);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiMonitor.BSS_FREQUENCY_CHANGED_EVENT, messageCaptor.getValue().what);
        int frequency = (int) messageCaptor.getValue().arg1;
        assertEquals(2412, frequency);
    }
}
