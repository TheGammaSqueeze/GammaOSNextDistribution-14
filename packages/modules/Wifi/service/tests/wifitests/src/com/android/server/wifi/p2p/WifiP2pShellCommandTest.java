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

package com.android.server.wifi.p2p;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.MacAddress;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Binder;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;

import androidx.test.filters.SmallTest;

import com.android.internal.util.Protocol;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.BinderUtil;
import com.android.server.wifi.WifiBaseTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.FileDescriptor;

/**
 * Unit tests for {@link com.android.server.wifi.p2p.WifiP2pShellCommand}.
 */
@SmallTest
public class WifiP2pShellCommandTest extends WifiBaseTest {
    private static final String TEST_PACKAGE = "com.android.test";

    @Mock Context mContext;
    @Mock WifiP2pManager mWifiP2pManager;
    @Mock WifiP2pManager.Channel mWifiP2pChannel;

    WifiP2pShellCommand mShellCommand;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemService(WifiP2pManager.class)).thenReturn(mWifiP2pManager);
        when(mWifiP2pManager.initialize(any(), any(), any())).thenReturn(mWifiP2pChannel);
        mShellCommand = new WifiP2pShellCommand(mContext);

        // by default emulate shell uid.
        BinderUtil.setUid(Process.SHELL_UID);
    }

    @After
    public void tearDown() throws Exception {
        validateMockitoUsage();
        // P2p channel is persist in the service, close it.
        runP2pCommandAsRoot(new String[] {"deinit"});
    }

    private void runP2pCommandAsRoot(String... cmds) {
        BinderUtil.setUid(Process.ROOT_UID);
        mShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                cmds);
    }

    @Test
    public void testP2pInitAndDeinit() {
        runP2pCommandAsRoot(new String[] {"init"});
        verify(mWifiP2pManager).initialize(eq(mContext), any(), any());
        runP2pCommandAsRoot(new String[] {"deinit"});
        verify(mWifiP2pChannel).close();
    }

    @Test
    public void testP2pDeinitWithNoInit() {
        runP2pCommandAsRoot("deinit");
        verify(mWifiP2pChannel, never()).close();
    }

    @Test
    public void testP2pPeerDiscovery() {
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("start-peer-discovery");
        verify(mWifiP2pManager).discoverPeers(eq(mWifiP2pChannel), any());
        runP2pCommandAsRoot("stop-peer-discovery");
        verify(mWifiP2pManager).stopPeerDiscovery(eq(mWifiP2pChannel), any());
    }

    @Test
    public void testP2pPeerDiscoveryOnSocialChannels() {
        assumeTrue(SdkLevel.isAtLeastT());
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("start-peer-discovery-on-social-channels");
        verify(mWifiP2pManager).discoverPeersOnSocialChannels(eq(mWifiP2pChannel), any());
        runP2pCommandAsRoot("stop-peer-discovery");
        verify(mWifiP2pManager).stopPeerDiscovery(eq(mWifiP2pChannel), any());
    }

    @Test
    public void testP2pPeerDiscoveryOnSpecificFrequencyWithPositiveFrequency() {
        assumeTrue(SdkLevel.isAtLeastT());
        final int frequencyMhz = 2412;
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot(
                "start-peer-discovery-on-specific-frequency", Integer.toString(frequencyMhz));
        verify(mWifiP2pManager).discoverPeersOnSpecificFrequency(
                eq(mWifiP2pChannel), eq(frequencyMhz), any());
        runP2pCommandAsRoot("stop-peer-discovery");
        verify(mWifiP2pManager).stopPeerDiscovery(eq(mWifiP2pChannel), any());
    }

    @Test
    public void testP2pPeerDiscoveryOnSpecificFrequencyWithNonePositiveFrequency() {
        assumeTrue(SdkLevel.isAtLeastT());
        final int frequencyMhz = 0;
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot(
                "start-peer-discovery-on-specific-frequency", Integer.toString(frequencyMhz));
        verify(mWifiP2pManager, never()).discoverPeersOnSpecificFrequency(
                eq(mWifiP2pChannel), eq(frequencyMhz), any());
    }

    @Test
    public void testP2pPeerDiscoveryOnSpecificFrequencyWithUnformattedFrequency() {
        assumeTrue(SdkLevel.isAtLeastT());
        final String frequencyMhz = "abcd";
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot(
                "start-peer-discovery-on-specific-frequency", frequencyMhz);
        verify(mWifiP2pManager, never()).discoverPeersOnSpecificFrequency(
                eq(mWifiP2pChannel), anyInt(), any());
    }

    @Test
    public void testP2pServiceDiscovery() {
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("start-service-discovery");
        verify(mWifiP2pManager).discoverServices(eq(mWifiP2pChannel), any());
        runP2pCommandAsRoot("stop-service-discovery");
        verify(mWifiP2pManager).stopPeerDiscovery(eq(mWifiP2pChannel), any());
    }

    @Test
    public void testP2pListPeers() {
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("list-peers");
        verify(mWifiP2pManager).requestPeers(eq(mWifiP2pChannel), any());
    }

    @Test
    public void testP2pRemoveClientWithValidMacAddress() {
        assumeTrue(SdkLevel.isAtLeastT());
        final String peerAddress = "aa:bb:cc:11:22:33";
        final MacAddress peerMacAddress = MacAddress.fromString(peerAddress);
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("remove-client", peerAddress);
        verify(mWifiP2pManager).removeClient(eq(mWifiP2pChannel), eq(peerMacAddress), any());
    }

    @Test
    public void testP2pRemoveClientWithInalidMacAddress() {
        assumeTrue(SdkLevel.isAtLeastT());
        final String peerAddress = "aa33";
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("remove-client", peerAddress);
        verify(mWifiP2pManager, never()).removeClient(eq(mWifiP2pChannel), any(), any());
    }

    @Test
    public void testP2pCancelConnect() {
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("cancel-connect");
        verify(mWifiP2pManager).cancelConnect(eq(mWifiP2pChannel), any());
    }

    @Test
    public void testP2pCreateAndRemoveGroup() {
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("create-group");
        verify(mWifiP2pManager).createGroup(eq(mWifiP2pChannel), any());
        runP2pCommandAsRoot("remove-group");
        verify(mWifiP2pManager).removeGroup(eq(mWifiP2pChannel), any());
    }

    @Test
    public void testP2pSetDeviceName() {
        final String deviceName = "testName";
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("set-device-name", deviceName);
        verify(mWifiP2pManager).setDeviceName(eq(mWifiP2pChannel), eq(deviceName), any());
    }

    @Test
    public void testP2pGetConnectionInfo() {
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("get-connection-info");
        verify(mWifiP2pManager).requestConnectionInfo(eq(mWifiP2pChannel), any());
    }

    @Test
    public void testP2pGetGroupInfo() {
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("get-group-info");
        verify(mWifiP2pManager).requestGroupInfo(eq(mWifiP2pChannel), any());
    }

    @Test
    public void testP2pGetP2pState() {
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("get-state");
        verify(mWifiP2pManager).requestP2pState(eq(mWifiP2pChannel), any());
    }

    @Test
    public void testP2pGetDiscoveryState() {
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("get-discovery-state");
        verify(mWifiP2pManager).requestDiscoveryState(eq(mWifiP2pChannel), any());
    }

    @Test
    public void testP2pGetNetworkInfo() {
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("get-network-info");
        verify(mWifiP2pManager).requestNetworkInfo(eq(mWifiP2pChannel), any());
    }

    @Test
    public void testP2pGetDeviceInfo() {
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("get-device-info");
        verify(mWifiP2pManager).requestDeviceInfo(eq(mWifiP2pChannel), any());
    }

    @Test
    public void testP2pListSavedGroup() {
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("list-saved-groups");
        verify(mWifiP2pManager).requestPersistentGroupInfo(eq(mWifiP2pChannel), any());
    }

    @Test
    public void testP2pDeleteSavedGroup() {
        final int netId = 99;
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("delete-saved-group", Integer.toString(netId));
        verify(mWifiP2pManager).deletePersistentGroup(eq(mWifiP2pChannel), eq(netId), any());
    }

    @Test
    public void testP2pSetChannels() {
        final int listeningChannel = 6, operatingChannel = 149;
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("set-channels", Integer.toString(listeningChannel),
                Integer.toString(operatingChannel));
        verify(mWifiP2pManager).setWifiP2pChannels(eq(mWifiP2pChannel),
                eq(listeningChannel), eq(operatingChannel), any());
    }

    @Test
    public void testP2pListening() {
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("start-listening");
        verify(mWifiP2pManager).startListening(eq(mWifiP2pChannel), any());
        runP2pCommandAsRoot("stop-listening");
        verify(mWifiP2pManager).stopListening(eq(mWifiP2pChannel), any());
    }

    @Test
    public void testP2pSetMiracastMode() {
        final int mode = WifiP2pManager.MIRACAST_SOURCE;
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("set-miracast-mode", Integer.toString(mode));
        verify(mWifiP2pManager).setMiracastMode(eq(mode));
    }

    @Test
    public void testP2pFactoryReset() {
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("factory-reset");
        verify(mWifiP2pManager).factoryReset(eq(mWifiP2pChannel), any());
    }

    @Test
    public void testP2pConnect() {
        String deviceAddress = "aa:bb:cc:11:22:33";
        runP2pCommandAsRoot("init");
        if (SdkLevel.isAtLeastT()) {
            runP2pCommandAsRoot("connect", deviceAddress, "-i", "10", "-m", "1");
        } else {
            runP2pCommandAsRoot("connect", deviceAddress, "-i", "10");
        }
        ArgumentCaptor<WifiP2pConfig> captor = ArgumentCaptor.forClass(WifiP2pConfig.class);
        verify(mWifiP2pManager).connect(eq(mWifiP2pChannel), captor.capture(), any());
        WifiP2pConfig config = captor.getValue();
        assertEquals(deviceAddress, config.deviceAddress);
        assertEquals(10, config.groupOwnerIntent);
        if (SdkLevel.isAtLeastT()) {
            assertEquals(1, config.getGroupClientIpProvisioningMode());
        }
    }

    @Test
    public void testP2pAcceptConnection() throws Exception {
        Messenger messenger = mock(Messenger.class);
        when(mWifiP2pManager.getP2pStateMachineMessenger()).thenReturn(messenger);
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("accept-connection");
        verify(mWifiP2pManager).getP2pStateMachineMessenger();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messenger).send(captor.capture());
        Message m = captor.getValue();
        assertEquals(Protocol.BASE_WIFI_P2P_SERVICE + 2, m.what);
    }

    @Test
    public void testP2pRejectConnection() throws Exception {
        Messenger messenger = mock(Messenger.class);
        when(mWifiP2pManager.getP2pStateMachineMessenger()).thenReturn(messenger);
        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("reject-connection");
        verify(mWifiP2pManager).getP2pStateMachineMessenger();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messenger).send(captor.capture());
        Message m = captor.getValue();
        assertEquals(Protocol.BASE_WIFI_P2P_SERVICE + 3, m.what);
    }

    @Test
    public void testP2pCreateGroupWithConfig() {
        final String networkName = "DIRECT-xy-Hello";
        final String passphrase = "password";
        final int operatingBand = WifiP2pConfig.GROUP_OWNER_BAND_5GHZ;
        final boolean isPersistentMode = true;
        final int netId = isPersistentMode
                ? WifiP2pGroup.NETWORK_ID_PERSISTENT
                : WifiP2pGroup.NETWORK_ID_TEMPORARY;

        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("create-group-with-config",
                networkName, passphrase, Integer.toString(operatingBand),
                isPersistentMode ? "true" : "false");
        ArgumentCaptor<WifiP2pConfig> captor = ArgumentCaptor.forClass(WifiP2pConfig.class);
        verify(mWifiP2pManager).createGroup(eq(mWifiP2pChannel), captor.capture(), any());
        WifiP2pConfig c = captor.getValue();
        assertEquals(networkName, c.getNetworkName());
        assertEquals(passphrase, c.getPassphrase());
        assertEquals(operatingBand, c.getGroupOwnerBand());
        assertEquals(netId, c.getNetworkId());
    }

    @Test
    public void testP2pConnectWithConfig() {
        final String networkName = "DIRECT-xy-Hello";
        final String passphrase = "password";
        final int operatingBand = WifiP2pConfig.GROUP_OWNER_BAND_5GHZ;
        final boolean isPersistentMode = true;
        final int netId = isPersistentMode
                ? WifiP2pGroup.NETWORK_ID_PERSISTENT
                : WifiP2pGroup.NETWORK_ID_TEMPORARY;

        runP2pCommandAsRoot("init");
        runP2pCommandAsRoot("connect-with-config",
                networkName, passphrase, Integer.toString(operatingBand),
                isPersistentMode ? "true" : "false");
        ArgumentCaptor<WifiP2pConfig> captor = ArgumentCaptor.forClass(WifiP2pConfig.class);
        verify(mWifiP2pManager).connect(eq(mWifiP2pChannel), captor.capture(), any());
        WifiP2pConfig c = captor.getValue();
        assertEquals(networkName, c.getNetworkName());
        assertEquals(passphrase, c.getPassphrase());
        assertEquals(operatingBand, c.getGroupOwnerBand());
        assertEquals(netId, c.getNetworkId());
    }
}
