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

package com.android.server.wifi.p2p;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pProvDiscEvent;
import android.os.Handler;
import android.os.Message;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.WifiBaseTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link com.android.server.wifi.p2p.WifiP2pMonitor}.
 */
@SmallTest
public class WifiP2pMonitorTest extends WifiBaseTest {
    private static final String P2P_IFACE_NAME = "p2p0";
    private static final String SECOND_P2P_IFACE_NAME = "p2p1";
    private static final int TEST_GROUP_FREQUENCY = 5180;
    private WifiP2pMonitor mWifiP2pMonitor;
    private TestLooper mLooper;
    private Handler mHandlerSpy;
    private Handler mSecondHandlerSpy;

    @Before
    public void setUp() throws Exception {
        mWifiP2pMonitor = new WifiP2pMonitor();
        mLooper = new TestLooper();
        mHandlerSpy = spy(new Handler(mLooper.getLooper()));
        mSecondHandlerSpy = spy(new Handler(mLooper.getLooper()));
        mWifiP2pMonitor.setMonitoring(P2P_IFACE_NAME, true);
    }

    /**
     * Broadcast message test.
     */
    @Test
    public void testBroadcastSupplicantDisconnectionEvent() {
        mWifiP2pMonitor.registerHandler(
                P2P_IFACE_NAME, WifiP2pMonitor.SUP_DISCONNECTION_EVENT, mHandlerSpy);
        mWifiP2pMonitor.broadcastSupplicantDisconnectionEvent(P2P_IFACE_NAME);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiP2pMonitor.SUP_DISCONNECTION_EVENT, messageCaptor.getValue().what);
    }
    /**
     * Broadcast message to two handlers test.
     */
    @Test
    public void testBroadcastEventToTwoHandlers() {
        mWifiP2pMonitor.registerHandler(
                P2P_IFACE_NAME, WifiP2pMonitor.SUP_CONNECTION_EVENT, mHandlerSpy);
        mWifiP2pMonitor.registerHandler(
                P2P_IFACE_NAME, WifiP2pMonitor.SUP_CONNECTION_EVENT, mSecondHandlerSpy);
        mWifiP2pMonitor.broadcastSupplicantConnectionEvent(P2P_IFACE_NAME);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiP2pMonitor.SUP_CONNECTION_EVENT, messageCaptor.getValue().what);
        verify(mSecondHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiP2pMonitor.SUP_CONNECTION_EVENT, messageCaptor.getValue().what);
    }
    /**
     * Broadcast message when iface is null.
     */
    @Test
    public void testBroadcastEventWhenIfaceIsNull() {
        mWifiP2pMonitor.registerHandler(
                P2P_IFACE_NAME, WifiP2pMonitor.SUP_DISCONNECTION_EVENT, mHandlerSpy);
        mWifiP2pMonitor.broadcastSupplicantDisconnectionEvent(null);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiP2pMonitor.SUP_DISCONNECTION_EVENT, messageCaptor.getValue().what);
    }
    /**
     * Broadcast message when iface handler is null.
     */
    @Test
    public void testBroadcastEventWhenIfaceHandlerIsNull() {
        mWifiP2pMonitor.registerHandler(
                P2P_IFACE_NAME, WifiP2pMonitor.SUP_DISCONNECTION_EVENT, mHandlerSpy);
        mWifiP2pMonitor.broadcastSupplicantDisconnectionEvent(SECOND_P2P_IFACE_NAME);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiP2pMonitor.SUP_DISCONNECTION_EVENT, messageCaptor.getValue().what);
    }
    /**
     * Broadcast frequency changed event test
     */
    @Test
    public void testBroadcastP2pFrequencyChanged() {
        mWifiP2pMonitor.registerHandler(
                P2P_IFACE_NAME, WifiP2pMonitor.P2P_FREQUENCY_CHANGED_EVENT, mHandlerSpy);
        mWifiP2pMonitor.broadcastP2pFrequencyChanged(P2P_IFACE_NAME, TEST_GROUP_FREQUENCY);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiP2pMonitor.P2P_FREQUENCY_CHANGED_EVENT, messageCaptor.getValue().what);
        assertEquals(TEST_GROUP_FREQUENCY, messageCaptor.getValue().arg1);
    }

    /**
     * Broadcast message when provision discovery fails.
     */
    @Test
    public void testBroadcastP2pProvisionDiscoveryFailure() throws Exception {
        mWifiP2pMonitor.registerHandler(
                P2P_IFACE_NAME, WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT, mHandlerSpy);
        mWifiP2pMonitor.broadcastP2pProvisionDiscoveryFailure(P2P_IFACE_NAME,
                WifiP2pMonitor.PROV_DISC_STATUS_UNKNOWN, new WifiP2pProvDiscEvent());
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT, messageCaptor.getValue().what);
    }

    /**
     * Broadcast message when provision discovery is rejected.
     */
    @Test
    public void testBroadcastP2pProvisionDiscoveryRejection() throws Exception {
        mWifiP2pMonitor.registerHandler(
                P2P_IFACE_NAME, WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT, mHandlerSpy);
        WifiP2pProvDiscEvent event = new WifiP2pProvDiscEvent();
        event.device = new WifiP2pDevice();
        event.device.deviceAddress = "11:22:33:44:55:66";
        mWifiP2pMonitor.broadcastP2pProvisionDiscoveryFailure(P2P_IFACE_NAME,
                WifiP2pMonitor.PROV_DISC_STATUS_REJECTED, event);
        mLooper.dispatchAll();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mHandlerSpy).handleMessage(messageCaptor.capture());
        assertEquals(WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT, messageCaptor.getValue().what);
        assertEquals(event.device.deviceAddress,
                ((WifiP2pProvDiscEvent) messageCaptor.getValue().obj).device.deviceAddress);
    }
}
