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

package com.android.server.connectivity.mdns;

import static android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

import static com.android.net.module.util.netlink.StructNlMsgHdr.NLM_F_ACK;
import static com.android.net.module.util.netlink.StructNlMsgHdr.NLM_F_REQUEST;
import static com.android.testutils.ContextUtils.mockService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TetheringManager;
import android.net.TetheringManager.TetheringEventCallback;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.net.module.util.ArrayTrackRecord;
import com.android.net.module.util.SharedLog;
import com.android.net.module.util.netlink.NetlinkConstants;
import com.android.net.module.util.netlink.RtNetlinkAddressMessage;
import com.android.net.module.util.netlink.StructIfaddrMsg;
import com.android.net.module.util.netlink.StructNlMsgHdr;
import com.android.server.connectivity.mdns.MdnsSocketProvider.Dependencies;
import com.android.server.connectivity.mdns.MdnsSocketProvider.SocketRequestMonitor;
import com.android.server.connectivity.mdns.internal.SocketNetlinkMonitor;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;
import com.android.testutils.HandlerUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.S_V2)
public class MdnsSocketProviderTest {
    private static final String TAG = MdnsSocketProviderTest.class.getSimpleName();
    private static final String TEST_IFACE_NAME = "test";
    private static final String LOCAL_ONLY_IFACE_NAME = "local_only";
    private static final String WIFI_P2P_IFACE_NAME = "p2p_wifi";
    private static final String TETHERED_IFACE_NAME = "tethered";
    private static final int TETHERED_IFACE_IDX = 32;
    private static final long DEFAULT_TIMEOUT = 2000L;
    private static final long NO_CALLBACK_TIMEOUT = 200L;
    private static final LinkAddress LINKADDRV4 = new LinkAddress("192.0.2.0/24");
    private static final LinkAddress LINKADDRV6 =
            new LinkAddress("2001:0db8:85a3:0000:0000:8a2e:0370:7334/64");

    private static final LinkAddress LINKADDRV6_FLAG_CHANGE =
            new LinkAddress("2001:0db8:85a3:0000:0000:8a2e:0370:7334/64", 1 /* flags */,
                    0 /* scope */);
    private static final Network TEST_NETWORK = new Network(123);
    @Mock private Context mContext;
    @Mock private Dependencies mDeps;
    @Mock private ConnectivityManager mCm;
    @Mock private TetheringManager mTm;
    @Mock private NetworkInterfaceWrapper mTestNetworkIfaceWrapper;
    @Mock private NetworkInterfaceWrapper mLocalOnlyIfaceWrapper;
    @Mock private NetworkInterfaceWrapper mTetheredIfaceWrapper;
    @Mock private SocketRequestMonitor mSocketRequestMonitor;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private MdnsSocketProvider mSocketProvider;
    private NetworkCallback mNetworkCallback;
    private TetheringEventCallback mTetheringEventCallback;
    private SharedLog mLog = new SharedLog("MdnsSocketProviderTest");

    private TestNetlinkMonitor mTestSocketNetLinkMonitor;
    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        mockService(mContext, ConnectivityManager.class, Context.CONNECTIVITY_SERVICE, mCm);
        if (mContext.getSystemService(ConnectivityManager.class) == null) {
            // Test is using mockito-extended
            doCallRealMethod().when(mContext).getSystemService(ConnectivityManager.class);
        }
        mockService(mContext, TetheringManager.class, Context.TETHERING_SERVICE, mTm);
        if (mContext.getSystemService(TetheringManager.class) == null) {
            // Test is using mockito-extended
            doCallRealMethod().when(mContext).getSystemService(TetheringManager.class);
        }
        doReturn(mTestNetworkIfaceWrapper).when(mDeps).getNetworkInterfaceByName(anyString());
        doReturn(true).when(mTestNetworkIfaceWrapper).isUp();
        doReturn(true).when(mLocalOnlyIfaceWrapper).isUp();
        doReturn(true).when(mTetheredIfaceWrapper).isUp();
        doReturn(true).when(mTestNetworkIfaceWrapper).supportsMulticast();
        doReturn(true).when(mLocalOnlyIfaceWrapper).supportsMulticast();
        doReturn(true).when(mTetheredIfaceWrapper).supportsMulticast();
        doReturn(mLocalOnlyIfaceWrapper).when(mDeps)
                .getNetworkInterfaceByName(LOCAL_ONLY_IFACE_NAME);
        doReturn(mLocalOnlyIfaceWrapper).when(mDeps)
                .getNetworkInterfaceByName(WIFI_P2P_IFACE_NAME);
        doReturn(mTetheredIfaceWrapper).when(mDeps).getNetworkInterfaceByName(TETHERED_IFACE_NAME);
        doReturn(mock(MdnsInterfaceSocket.class))
                .when(mDeps).createMdnsInterfaceSocket(any(), anyInt(), any(), any(), any());
        doReturn(TETHERED_IFACE_IDX).when(mDeps).getNetworkInterfaceIndexByName(
                eq(TETHERED_IFACE_NAME), any());
        doReturn(789).when(mDeps).getNetworkInterfaceIndexByName(
                eq(WIFI_P2P_IFACE_NAME), any());
        mHandlerThread = new HandlerThread("MdnsSocketProviderTest");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        doReturn(mTestSocketNetLinkMonitor).when(mDeps).createSocketNetlinkMonitor(any(), any(),
                any());
        doAnswer(inv -> {
            mTestSocketNetLinkMonitor = new TestNetlinkMonitor(inv.getArgument(0),
                    inv.getArgument(1),
                    inv.getArgument(2));
            return mTestSocketNetLinkMonitor;
        }).when(mDeps).createSocketNetlinkMonitor(any(), any(),
                any());
        mSocketProvider = new MdnsSocketProvider(mContext, mHandlerThread.getLooper(), mDeps, mLog,
                mSocketRequestMonitor);
    }

    @After
    public void tearDown() throws Exception {
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread.join();
        }
    }

    private void runOnHandler(Runnable r) {
        mHandler.post(r);
        HandlerUtils.waitForIdle(mHandler, DEFAULT_TIMEOUT);
    }

    private BroadcastReceiver expectWifiP2PChangeBroadcastReceiver() {
        final ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext, times(1)).registerReceiver(receiverCaptor.capture(),
                argThat(filter -> filter.hasAction(
                        WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)),
                any(), any());
        final BroadcastReceiver originalReceiver = receiverCaptor.getValue();
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                runOnHandler(() -> originalReceiver.onReceive(context, intent));
            }
        };
    }

    private void startMonitoringSockets() {
        final ArgumentCaptor<NetworkCallback> nwCallbackCaptor =
                ArgumentCaptor.forClass(NetworkCallback.class);
        final ArgumentCaptor<TetheringEventCallback> teCallbackCaptor =
                ArgumentCaptor.forClass(TetheringEventCallback.class);

        runOnHandler(mSocketProvider::startMonitoringSockets);
        verify(mCm).registerNetworkCallback(any(), nwCallbackCaptor.capture(), any());
        verify(mTm).registerTetheringEventCallback(any(), teCallbackCaptor.capture());

        mNetworkCallback = nwCallbackCaptor.getValue();
        mTetheringEventCallback = teCallbackCaptor.getValue();

        runOnHandler(mSocketProvider::startNetLinkMonitor);
    }

    private static class TestNetlinkMonitor extends SocketNetlinkMonitor {
        TestNetlinkMonitor(@NonNull Handler handler,
                @NonNull SharedLog log,
                @Nullable MdnsSocketProvider.NetLinkMonitorCallBack cb) {
            super(handler, log, cb);
        }

        @Override
        public void startMonitoring() { }

        @Override
        public void stopMonitoring() { }
    }

    private class TestSocketCallback implements MdnsSocketProvider.SocketCallback {
        private class SocketEvent {
            public final SocketKey mSocketKey;
            public final List<LinkAddress> mAddresses;

            SocketEvent(SocketKey socketKey, List<LinkAddress> addresses) {
                mSocketKey = socketKey;
                mAddresses = Collections.unmodifiableList(addresses);
            }
        }

        private class SocketCreatedEvent extends SocketEvent {
            SocketCreatedEvent(SocketKey socketKey, List<LinkAddress> addresses) {
                super(socketKey, addresses);
            }
        }

        private class InterfaceDestroyedEvent extends SocketEvent {
            InterfaceDestroyedEvent(SocketKey socketKey, List<LinkAddress> addresses) {
                super(socketKey, addresses);
            }
        }

        private class AddressesChangedEvent extends SocketEvent {
            AddressesChangedEvent(SocketKey socketKey, List<LinkAddress> addresses) {
                super(socketKey, addresses);
            }
        }

        private final ArrayTrackRecord<SocketEvent>.ReadHead mHistory =
                new ArrayTrackRecord<SocketEvent>().newReadHead();

        @Override
        public void onSocketCreated(SocketKey socketKey, MdnsInterfaceSocket socket,
                List<LinkAddress> addresses) {
            mHistory.add(new SocketCreatedEvent(socketKey, addresses));
        }

        @Override
        public void onInterfaceDestroyed(SocketKey socketKey, MdnsInterfaceSocket socket) {
            mHistory.add(new InterfaceDestroyedEvent(socketKey, List.of()));
        }

        @Override
        public void onAddressesChanged(SocketKey socketKey, MdnsInterfaceSocket socket,
                List<LinkAddress> addresses) {
            mHistory.add(new AddressesChangedEvent(socketKey, addresses));
        }

        public void expectedSocketCreatedForNetwork(Network network, List<LinkAddress> addresses) {
            final SocketEvent event = mHistory.poll(0L /* timeoutMs */, c -> true);
            assertNotNull(event);
            assertTrue(event instanceof SocketCreatedEvent);
            assertEquals(network, event.mSocketKey.getNetwork());
            assertEquals(addresses, event.mAddresses);
        }

        public void expectedInterfaceDestroyedForNetwork(Network network) {
            final SocketEvent event = mHistory.poll(0L /* timeoutMs */, c -> true);
            assertNotNull(event);
            assertTrue(event instanceof InterfaceDestroyedEvent);
            assertEquals(network, event.mSocketKey.getNetwork());
        }

        public void expectedAddressesChangedForNetwork(Network network,
                List<LinkAddress> addresses) {
            final SocketEvent event = mHistory.poll(0L /* timeoutMs */, c -> true);
            assertNotNull(event);
            assertTrue(event instanceof AddressesChangedEvent);
            assertEquals(network, event.mSocketKey.getNetwork());
            assertEquals(event.mAddresses, addresses);
        }

        public void expectedNoCallback() {
            final SocketEvent event = mHistory.poll(NO_CALLBACK_TIMEOUT, c -> true);
            assertNull(event);
        }
    }

    private static NetworkCapabilities makeCapabilities(int... transports) {
        final NetworkCapabilities nc = new NetworkCapabilities();
        for (int transport : transports) {
            nc.addTransportType(transport);
        }
        return nc;
    }

    private void postNetworkAvailable(int... transports) {
        final LinkProperties testLp = new LinkProperties();
        testLp.setInterfaceName(TEST_IFACE_NAME);
        testLp.setLinkAddresses(List.of(LINKADDRV4));
        final NetworkCapabilities testNc = makeCapabilities(transports);
        runOnHandler(() -> mNetworkCallback.onCapabilitiesChanged(TEST_NETWORK, testNc));
        runOnHandler(() -> mNetworkCallback.onLinkPropertiesChanged(TEST_NETWORK, testLp));
    }

    @Test
    public void testSocketRequestAndUnrequestSocket() {
        startMonitoringSockets();

        final InOrder cbMonitorOrder = inOrder(mSocketRequestMonitor);
        final TestSocketCallback testCallback1 = new TestSocketCallback();
        runOnHandler(() -> mSocketProvider.requestSocket(TEST_NETWORK, testCallback1));
        testCallback1.expectedNoCallback();

        postNetworkAvailable(TRANSPORT_WIFI);
        testCallback1.expectedSocketCreatedForNetwork(TEST_NETWORK, List.of(LINKADDRV4));
        cbMonitorOrder.verify(mSocketRequestMonitor).onSocketRequestFulfilled(eq(TEST_NETWORK),
                any(), eq(new int[] { TRANSPORT_WIFI }));

        final TestSocketCallback testCallback2 = new TestSocketCallback();
        runOnHandler(() -> mSocketProvider.requestSocket(TEST_NETWORK, testCallback2));
        testCallback1.expectedNoCallback();
        testCallback2.expectedSocketCreatedForNetwork(TEST_NETWORK, List.of(LINKADDRV4));
        cbMonitorOrder.verify(mSocketRequestMonitor).onSocketRequestFulfilled(eq(TEST_NETWORK),
                any(), eq(new int[] { TRANSPORT_WIFI }));

        final TestSocketCallback testCallback3 = new TestSocketCallback();
        runOnHandler(() -> mSocketProvider.requestSocket(null /* network */, testCallback3));
        testCallback1.expectedNoCallback();
        testCallback2.expectedNoCallback();
        testCallback3.expectedSocketCreatedForNetwork(TEST_NETWORK, List.of(LINKADDRV4));
        cbMonitorOrder.verify(mSocketRequestMonitor).onSocketRequestFulfilled(eq(TEST_NETWORK),
                any(), eq(new int[] { TRANSPORT_WIFI }));

        runOnHandler(() -> mTetheringEventCallback.onLocalOnlyInterfacesChanged(
                List.of(LOCAL_ONLY_IFACE_NAME)));
        verify(mLocalOnlyIfaceWrapper).getNetworkInterface();
        testCallback1.expectedNoCallback();
        testCallback2.expectedNoCallback();
        testCallback3.expectedSocketCreatedForNetwork(null /* network */, List.of());
        cbMonitorOrder.verify(mSocketRequestMonitor).onSocketRequestFulfilled(eq(null),
                any(), eq(new int[0]));

        runOnHandler(() -> mTetheringEventCallback.onTetheredInterfacesChanged(
                List.of(TETHERED_IFACE_NAME)));
        verify(mTetheredIfaceWrapper).getNetworkInterface();
        testCallback1.expectedNoCallback();
        testCallback2.expectedNoCallback();
        testCallback3.expectedSocketCreatedForNetwork(null /* network */, List.of());
        cbMonitorOrder.verify(mSocketRequestMonitor).onSocketRequestFulfilled(eq(null),
                any(), eq(new int[0]));

        runOnHandler(() -> mSocketProvider.unrequestSocket(testCallback1));
        testCallback1.expectedNoCallback();
        testCallback2.expectedNoCallback();
        testCallback3.expectedNoCallback();

        runOnHandler(() -> mNetworkCallback.onLost(TEST_NETWORK));
        testCallback1.expectedNoCallback();
        testCallback2.expectedInterfaceDestroyedForNetwork(TEST_NETWORK);
        testCallback3.expectedInterfaceDestroyedForNetwork(TEST_NETWORK);
        cbMonitorOrder.verify(mSocketRequestMonitor).onSocketDestroyed(eq(TEST_NETWORK), any());

        runOnHandler(() -> mTetheringEventCallback.onLocalOnlyInterfacesChanged(List.of()));
        testCallback1.expectedNoCallback();
        testCallback2.expectedNoCallback();
        testCallback3.expectedInterfaceDestroyedForNetwork(null /* network */);
        cbMonitorOrder.verify(mSocketRequestMonitor).onSocketDestroyed(eq(null), any());

        runOnHandler(() -> mSocketProvider.unrequestSocket(testCallback3));
        testCallback1.expectedNoCallback();
        testCallback2.expectedNoCallback();
        // There was still a tethered interface, but no callback should be sent once unregistered
        testCallback3.expectedNoCallback();

        // However the socket is getting destroyed, so the callback monitor is notified
        cbMonitorOrder.verify(mSocketRequestMonitor).onSocketDestroyed(eq(null), any());
    }

    private RtNetlinkAddressMessage createNetworkAddressUpdateNetLink(
            short msgType, LinkAddress linkAddress, int ifIndex, int flags) {
        final StructNlMsgHdr nlmsghdr = new StructNlMsgHdr();
        nlmsghdr.nlmsg_type = msgType;
        nlmsghdr.nlmsg_flags = NLM_F_REQUEST | NLM_F_ACK;
        nlmsghdr.nlmsg_seq = 1;

        InetAddress ip = linkAddress.getAddress();

        final byte family =
                (byte) ((ip instanceof Inet6Address) ? OsConstants.AF_INET6 : OsConstants.AF_INET);
        StructIfaddrMsg structIfaddrMsg = new StructIfaddrMsg(family,
                (short) linkAddress.getPrefixLength(),
                (short) linkAddress.getFlags(), (short) linkAddress.getScope(), ifIndex);

        return new RtNetlinkAddressMessage(nlmsghdr, structIfaddrMsg, ip,
                null /* structIfacacheInfo */, flags);
    }

    @Test
    public void testDownstreamNetworkAddressUpdateFromNetlink() {
        startMonitoringSockets();
        final TestSocketCallback testCallbackAll = new TestSocketCallback();
        runOnHandler(() -> mSocketProvider.requestSocket(null /* network */, testCallbackAll));

        // Address add message arrived before the interface is created.
        RtNetlinkAddressMessage addIpv4AddrMsg = createNetworkAddressUpdateNetLink(
                NetlinkConstants.RTM_NEWADDR,
                LINKADDRV4,
                TETHERED_IFACE_IDX,
                0 /* flags */);
        runOnHandler(
                () -> mTestSocketNetLinkMonitor.processNetlinkMessage(addIpv4AddrMsg,
                        0 /* whenMs */));

        // Interface is created.
        runOnHandler(() -> mTetheringEventCallback.onTetheredInterfacesChanged(
                List.of(TETHERED_IFACE_NAME)));
        verify(mTetheredIfaceWrapper).getNetworkInterface();
        testCallbackAll.expectedSocketCreatedForNetwork(null /* network */, List.of(LINKADDRV4));

        // Old Address removed.
        RtNetlinkAddressMessage removeIpv4AddrMsg = createNetworkAddressUpdateNetLink(
                NetlinkConstants.RTM_DELADDR,
                LINKADDRV4,
                TETHERED_IFACE_IDX,
                0 /* flags */);
        runOnHandler(
                () -> mTestSocketNetLinkMonitor.processNetlinkMessage(removeIpv4AddrMsg,
                        0 /* whenMs */));
        testCallbackAll.expectedAddressesChangedForNetwork(null /* network */, List.of());

        // New address added.
        RtNetlinkAddressMessage addIpv6AddrMsg = createNetworkAddressUpdateNetLink(
                NetlinkConstants.RTM_NEWADDR,
                LINKADDRV6,
                TETHERED_IFACE_IDX,
                0 /* flags */);
        runOnHandler(() -> mTestSocketNetLinkMonitor.processNetlinkMessage(addIpv6AddrMsg,
                0 /* whenMs */));
        testCallbackAll.expectedAddressesChangedForNetwork(null /* network */, List.of(LINKADDRV6));

        // Address updated
        RtNetlinkAddressMessage updateIpv6AddrMsg = createNetworkAddressUpdateNetLink(
                NetlinkConstants.RTM_NEWADDR,
                LINKADDRV6,
                TETHERED_IFACE_IDX,
                1 /* flags */);
        runOnHandler(
                () -> mTestSocketNetLinkMonitor.processNetlinkMessage(updateIpv6AddrMsg,
                        0 /* whenMs */));
        testCallbackAll.expectedAddressesChangedForNetwork(null /* network */,
                List.of(LINKADDRV6_FLAG_CHANGE));
    }

    @Test
    public void testAddressesChanged() throws Exception {
        startMonitoringSockets();

        final TestSocketCallback testCallback = new TestSocketCallback();
        runOnHandler(() -> mSocketProvider.requestSocket(TEST_NETWORK, testCallback));
        testCallback.expectedNoCallback();

        postNetworkAvailable(TRANSPORT_WIFI);
        testCallback.expectedSocketCreatedForNetwork(TEST_NETWORK, List.of(LINKADDRV4));

        final LinkProperties newTestLp = new LinkProperties();
        newTestLp.setInterfaceName(TEST_IFACE_NAME);
        newTestLp.setLinkAddresses(List.of(LINKADDRV4, LINKADDRV6));
        runOnHandler(() -> mNetworkCallback.onLinkPropertiesChanged(TEST_NETWORK, newTestLp));
        testCallback.expectedAddressesChangedForNetwork(
                TEST_NETWORK, List.of(LINKADDRV4, LINKADDRV6));
    }

    @Test
    public void testStartAndStopMonitoringSockets() {
        // Stop monitoring sockets before start. Should not unregister any network callback.
        runOnHandler(mSocketProvider::requestStopWhenInactive);
        verify(mCm, never()).unregisterNetworkCallback(any(NetworkCallback.class));
        verify(mTm, never()).unregisterTetheringEventCallback(any(TetheringEventCallback.class));

        // Start sockets monitoring.
        startMonitoringSockets();
        // Request a socket then unrequest it. Expect no network callback unregistration.
        final TestSocketCallback testCallback = new TestSocketCallback();
        runOnHandler(() -> mSocketProvider.requestSocket(TEST_NETWORK, testCallback));
        testCallback.expectedNoCallback();
        runOnHandler(()-> mSocketProvider.unrequestSocket(testCallback));
        verify(mCm, never()).unregisterNetworkCallback(any(NetworkCallback.class));
        verify(mTm, never()).unregisterTetheringEventCallback(any(TetheringEventCallback.class));
        // Request stop and it should unregister network callback immediately because there is no
        // socket request.
        runOnHandler(mSocketProvider::requestStopWhenInactive);
        verify(mCm, times(1)).unregisterNetworkCallback(any(NetworkCallback.class));
        verify(mTm, times(1)).unregisterTetheringEventCallback(any(TetheringEventCallback.class));

        // Start sockets monitoring and request a socket again.
        runOnHandler(mSocketProvider::startMonitoringSockets);
        verify(mCm, times(2)).registerNetworkCallback(any(), any(NetworkCallback.class), any());
        verify(mTm, times(2)).registerTetheringEventCallback(
                any(), any(TetheringEventCallback.class));
        final TestSocketCallback testCallback2 = new TestSocketCallback();
        runOnHandler(() -> mSocketProvider.requestSocket(TEST_NETWORK, testCallback2));
        testCallback2.expectedNoCallback();
        // Try to stop monitoring sockets but should be ignored and wait until all socket are
        // unrequested.
        runOnHandler(mSocketProvider::requestStopWhenInactive);
        verify(mCm, times(1)).unregisterNetworkCallback(any(NetworkCallback.class));
        verify(mTm, times(1)).unregisterTetheringEventCallback(any());
        // Unrequest the socket then network callbacks should be unregistered.
        runOnHandler(()-> mSocketProvider.unrequestSocket(testCallback2));
        verify(mCm, times(2)).unregisterNetworkCallback(any(NetworkCallback.class));
        verify(mTm, times(2)).unregisterTetheringEventCallback(any(TetheringEventCallback.class));
    }

    @Test
    public void testLinkPropertiesAreClearedAfterStopMonitoringSockets() {
        startMonitoringSockets();

        // Request a socket with null network.
        final TestSocketCallback testCallback = new TestSocketCallback();
        runOnHandler(() -> mSocketProvider.requestSocket(null, testCallback));
        testCallback.expectedNoCallback();

        // Notify a LinkPropertiesChanged with TEST_NETWORK.
        final LinkProperties testLp = new LinkProperties();
        testLp.setInterfaceName(TEST_IFACE_NAME);
        testLp.setLinkAddresses(List.of(LINKADDRV4));
        runOnHandler(() -> mNetworkCallback.onLinkPropertiesChanged(TEST_NETWORK, testLp));
        verify(mTestNetworkIfaceWrapper, times(1)).getNetworkInterface();
        testCallback.expectedSocketCreatedForNetwork(TEST_NETWORK, List.of(LINKADDRV4));

        // Try to stop monitoring and unrequest the socket.
        runOnHandler(mSocketProvider::requestStopWhenInactive);
        runOnHandler(()-> mSocketProvider.unrequestSocket(testCallback));
        // No callback sent when unregistered
        testCallback.expectedNoCallback();
        verify(mCm, times(1)).unregisterNetworkCallback(any(NetworkCallback.class));
        verify(mTm, times(1)).unregisterTetheringEventCallback(any());

        // Start sockets monitoring and request a socket again. Expected no socket created callback
        // because all saved LinkProperties has been cleared.
        runOnHandler(mSocketProvider::startMonitoringSockets);
        verify(mCm, times(2)).registerNetworkCallback(any(), any(NetworkCallback.class), any());
        verify(mTm, times(2)).registerTetheringEventCallback(
                any(), any(TetheringEventCallback.class));
        runOnHandler(() -> mSocketProvider.requestSocket(null, testCallback));
        testCallback.expectedNoCallback();

        // Notify a LinkPropertiesChanged with another network.
        final LinkProperties otherLp = new LinkProperties();
        final LinkAddress otherAddress = new LinkAddress("192.0.2.1/24");
        final Network otherNetwork = new Network(456);
        otherLp.setInterfaceName("test2");
        otherLp.setLinkAddresses(List.of(otherAddress));
        runOnHandler(() -> mNetworkCallback.onLinkPropertiesChanged(otherNetwork, otherLp));
        verify(mTestNetworkIfaceWrapper, times(2)).getNetworkInterface();
        testCallback.expectedSocketCreatedForNetwork(otherNetwork, List.of(otherAddress));
    }

    @Test
    public void testNoSocketCreatedForCellular() {
        startMonitoringSockets();

        final TestSocketCallback testCallback = new TestSocketCallback();
        runOnHandler(() -> mSocketProvider.requestSocket(TEST_NETWORK, testCallback));

        postNetworkAvailable(TRANSPORT_CELLULAR);
        testCallback.expectedNoCallback();
    }

    @Test
    public void testNoSocketCreatedForNonMulticastInterface() throws Exception {
        doReturn(false).when(mTestNetworkIfaceWrapper).supportsMulticast();
        startMonitoringSockets();

        final TestSocketCallback testCallback = new TestSocketCallback();
        runOnHandler(() -> mSocketProvider.requestSocket(TEST_NETWORK, testCallback));

        postNetworkAvailable(TRANSPORT_BLUETOOTH);
        testCallback.expectedNoCallback();
    }

    @Test
    public void testSocketCreatedForMulticastInterface() throws Exception {
        doReturn(true).when(mTestNetworkIfaceWrapper).supportsMulticast();
        startMonitoringSockets();

        final TestSocketCallback testCallback = new TestSocketCallback();
        runOnHandler(() -> mSocketProvider.requestSocket(TEST_NETWORK, testCallback));

        postNetworkAvailable(TRANSPORT_BLUETOOTH);
        testCallback.expectedSocketCreatedForNetwork(TEST_NETWORK, List.of(LINKADDRV4));
    }

    @Test
    public void testNoSocketCreatedForPTPInterface() throws Exception {
        doReturn(true).when(mTestNetworkIfaceWrapper).isPointToPoint();
        startMonitoringSockets();

        final TestSocketCallback testCallback = new TestSocketCallback();
        runOnHandler(() -> mSocketProvider.requestSocket(TEST_NETWORK, testCallback));

        postNetworkAvailable(TRANSPORT_BLUETOOTH);
        testCallback.expectedNoCallback();
    }

    @Test
    public void testNoSocketCreatedForVPNInterface() throws Exception {
        // VPN interfaces generally also have IFF_POINTOPOINT, but even if they don't, they should
        // not be included even with TRANSPORT_WIFI.
        doReturn(false).when(mTestNetworkIfaceWrapper).supportsMulticast();
        startMonitoringSockets();

        final TestSocketCallback testCallback = new TestSocketCallback();
        runOnHandler(() -> mSocketProvider.requestSocket(TEST_NETWORK, testCallback));

        postNetworkAvailable(TRANSPORT_VPN, TRANSPORT_WIFI);
        testCallback.expectedNoCallback();
    }

    @Test
    public void testSocketCreatedForWifiWithoutMulticastFlag() throws Exception {
        doReturn(false).when(mTestNetworkIfaceWrapper).supportsMulticast();
        startMonitoringSockets();

        final TestSocketCallback testCallback = new TestSocketCallback();
        runOnHandler(() -> mSocketProvider.requestSocket(TEST_NETWORK, testCallback));

        postNetworkAvailable(TRANSPORT_WIFI);
        testCallback.expectedSocketCreatedForNetwork(TEST_NETWORK, List.of(LINKADDRV4));
    }

    private Intent buildWifiP2PConnectionChangedIntent(boolean groupFormed) {
        final Intent intent = new Intent(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        final WifiP2pInfo formedInfo = new WifiP2pInfo();
        formedInfo.groupFormed = groupFormed;
        final WifiP2pGroup group;
        if (groupFormed) {
            group = mock(WifiP2pGroup.class);
            doReturn(WIFI_P2P_IFACE_NAME).when(group).getInterface();
        } else {
            group = null;
        }
        intent.putExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO, formedInfo);
        intent.putExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP, group);
        return intent;
    }

    @Test
    public void testWifiP2PInterfaceChange() {
        final BroadcastReceiver receiver = expectWifiP2PChangeBroadcastReceiver();
        startMonitoringSockets();

        // Request a socket with null network.
        final TestSocketCallback testCallback = new TestSocketCallback();
        runOnHandler(() -> mSocketProvider.requestSocket(null /* network */, testCallback));

        // Wifi p2p is connected and the interface is up. Get a wifi p2p change intent then expect
        // a socket creation.
        final Intent formedIntent = buildWifiP2PConnectionChangedIntent(true /* groupFormed */);
        receiver.onReceive(mContext, formedIntent);
        verify(mLocalOnlyIfaceWrapper).getNetworkInterface();
        testCallback.expectedSocketCreatedForNetwork(null /* network */, List.of());

        // Wifi p2p is disconnected. Get a wifi p2p change intent then expect the socket destroy.
        final Intent unformedIntent = buildWifiP2PConnectionChangedIntent(false /* groupFormed */);
        receiver.onReceive(mContext, unformedIntent);
        testCallback.expectedInterfaceDestroyedForNetwork(null /* network */);
    }

    @Test
    public void testWifiP2PInterfaceChangeBeforeStartMonitoringSockets() {
        final BroadcastReceiver receiver = expectWifiP2PChangeBroadcastReceiver();

        // Get a wifi p2p change intent before start monitoring sockets.
        final Intent formedIntent = buildWifiP2PConnectionChangedIntent(true /* groupFormed */);
        receiver.onReceive(mContext, formedIntent);

        // Start monitoring sockets and request a socket with null network.
        startMonitoringSockets();
        final TestSocketCallback testCallback = new TestSocketCallback();
        runOnHandler(() -> mSocketProvider.requestSocket(null /* network */, testCallback));
        verify(mLocalOnlyIfaceWrapper).getNetworkInterface();
        testCallback.expectedSocketCreatedForNetwork(null /* network */, List.of());
    }

    @Test
    public void testWifiP2PInterfaceChangeBeforeGetAllNetworksRequest() {
        final BroadcastReceiver receiver = expectWifiP2PChangeBroadcastReceiver();
        startMonitoringSockets();

        // Get a wifi p2p change intent before request socket for all networks.
        final Intent formedIntent = buildWifiP2PConnectionChangedIntent(true /* groupFormed */);
        receiver.onReceive(mContext, formedIntent);

        // Request a socket with null network.
        final TestSocketCallback testCallback = new TestSocketCallback();
        runOnHandler(() -> mSocketProvider.requestSocket(null /* network */, testCallback));
        verify(mLocalOnlyIfaceWrapper).getNetworkInterface();
        testCallback.expectedSocketCreatedForNetwork(null /* network */, List.of());
    }

    @Test
    public void testNoDuplicatedSocketCreation() {
        final BroadcastReceiver receiver = expectWifiP2PChangeBroadcastReceiver();
        startMonitoringSockets();

        // Request a socket with null network.
        final TestSocketCallback testCallback = new TestSocketCallback();
        runOnHandler(() -> mSocketProvider.requestSocket(null, testCallback));
        testCallback.expectedNoCallback();

        // Receive an interface added change for the wifi p2p interface. Expect a socket creation
        // callback.
        runOnHandler(() -> mTetheringEventCallback.onLocalOnlyInterfacesChanged(
                List.of(WIFI_P2P_IFACE_NAME)));
        verify(mLocalOnlyIfaceWrapper, times(1)).getNetworkInterface();
        testCallback.expectedSocketCreatedForNetwork(null /* network */, List.of());

        // Receive a wifi p2p connected intent. Expect no callback because the socket is created.
        final Intent formedIntent = buildWifiP2PConnectionChangedIntent(true /* groupFormed */);
        receiver.onReceive(mContext, formedIntent);
        testCallback.expectedNoCallback();

        // Request other socket with null network. Should receive socket created callback once.
        final TestSocketCallback testCallback2 = new TestSocketCallback();
        runOnHandler(() -> mSocketProvider.requestSocket(null, testCallback2));
        testCallback2.expectedSocketCreatedForNetwork(null /* network */, List.of());
        testCallback2.expectedNoCallback();

        // Receive a wifi p2p disconnected intent. Expect a socket destroy callback.
        final Intent unformedIntent = buildWifiP2PConnectionChangedIntent(false /* groupFormed */);
        receiver.onReceive(mContext, unformedIntent);
        testCallback.expectedInterfaceDestroyedForNetwork(null /* network */);

        // Receive an interface removed change for the wifi p2p interface. Expect no callback
        // because the socket is destroyed.
        runOnHandler(() -> mTetheringEventCallback.onLocalOnlyInterfacesChanged(List.of()));
        testCallback.expectedNoCallback();

        // Receive a wifi p2p connected intent again. Expect a socket creation callback.
        receiver.onReceive(mContext, formedIntent);
        verify(mLocalOnlyIfaceWrapper, times(2)).getNetworkInterface();
        testCallback.expectedSocketCreatedForNetwork(null /* network */, List.of());

        // Receive an interface added change for the wifi p2p interface again. Expect no callback
        // because the socket is created.
        runOnHandler(() -> mTetheringEventCallback.onLocalOnlyInterfacesChanged(
                List.of(WIFI_P2P_IFACE_NAME)));
        testCallback.expectedNoCallback();
    }

    @Test
    public void testTetherInterfacesChangedBeforeGetAllNetworksRequest() {
        startMonitoringSockets();

        // Receive an interface added change for the wifi p2p interface. Expect a socket creation
        // callback.
        runOnHandler(() -> mTetheringEventCallback.onLocalOnlyInterfacesChanged(
                List.of(TETHERED_IFACE_NAME)));
        verify(mTetheredIfaceWrapper, never()).getNetworkInterface();

        // Request a socket with null network.
        final TestSocketCallback testCallback = new TestSocketCallback();
        runOnHandler(() -> mSocketProvider.requestSocket(null /* network */, testCallback));
        verify(mTetheredIfaceWrapper).getNetworkInterface();
        testCallback.expectedSocketCreatedForNetwork(null /* network */, List.of());
    }
}
