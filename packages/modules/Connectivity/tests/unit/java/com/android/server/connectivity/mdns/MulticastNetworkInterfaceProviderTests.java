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
 * limitations under the License
 */

package com.android.server.connectivity.mdns;

import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.content.Context;

import androidx.test.InstrumentationRegistry;

import com.android.net.module.util.SharedLog;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Tests for {@link MulticastNetworkInterfaceProvider}. */
@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(SC_V2)
public class MulticastNetworkInterfaceProviderTests {

    @Mock private NetworkInterfaceWrapper loopbackInterface;
    @Mock private NetworkInterfaceWrapper pointToPointInterface;
    @Mock private NetworkInterfaceWrapper virtualInterface;
    @Mock private NetworkInterfaceWrapper inactiveMulticastInterface;
    @Mock private NetworkInterfaceWrapper activeIpv6MulticastInterface;
    @Mock private NetworkInterfaceWrapper activeIpv6MulticastInterfaceTwo;
    @Mock private NetworkInterfaceWrapper nonMulticastInterface;
    @Mock private NetworkInterfaceWrapper multicastInterfaceOne;
    @Mock private NetworkInterfaceWrapper multicastInterfaceTwo;

    @Mock private SharedLog sharedLog;

    private final List<NetworkInterfaceWrapper> networkInterfaces = new ArrayList<>();
    private MulticastNetworkInterfaceProvider provider;
    private Context context;

    @Before
    public void setUp() throws SocketException, UnknownHostException {
        MockitoAnnotations.initMocks(this);
        context = InstrumentationRegistry.getContext();

        setupNetworkInterface(
                loopbackInterface,
                true /* isUp */,
                true /* isLoopBack */,
                false /* isPointToPoint */,
                false /* isVirtual */,
                true /* supportsMulticast */,
                false /* isIpv6 */);

        setupNetworkInterface(
                pointToPointInterface,
                true /* isUp */,
                false /* isLoopBack */,
                true /* isPointToPoint */,
                false /* isVirtual */,
                true /* supportsMulticast */,
                false /* isIpv6 */);

        setupNetworkInterface(
                virtualInterface,
                true /* isUp */,
                false /* isLoopBack */,
                false /* isPointToPoint */,
                true /* isVirtual */,
                true /* supportsMulticast */,
                false /* isIpv6 */);

        setupNetworkInterface(
                inactiveMulticastInterface,
                false /* isUp */,
                false /* isLoopBack */,
                false /* isPointToPoint */,
                false /* isVirtual */,
                true /* supportsMulticast */,
                false /* isIpv6 */);

        setupNetworkInterface(
                nonMulticastInterface,
                true /* isUp */,
                false /* isLoopBack */,
                false /* isPointToPoint */,
                false /* isVirtual */,
                false /* supportsMulticast */,
                false /* isIpv6 */);

        setupNetworkInterface(
                activeIpv6MulticastInterface,
                true /* isUp */,
                false /* isLoopBack */,
                false /* isPointToPoint */,
                false /* isVirtual */,
                true /* supportsMulticast */,
                true /* isIpv6 */);

        setupNetworkInterface(
                activeIpv6MulticastInterfaceTwo,
                true /* isUp */,
                false /* isLoopBack */,
                false /* isPointToPoint */,
                false /* isVirtual */,
                true /* supportsMulticast */,
                true /* isIpv6 */);

        setupNetworkInterface(
                multicastInterfaceOne,
                true /* isUp */,
                false /* isLoopBack */,
                false /* isPointToPoint */,
                false /* isVirtual */,
                true /* supportsMulticast */,
                false /* isIpv6 */);

        setupNetworkInterface(
                multicastInterfaceTwo,
                true /* isUp */,
                false /* isLoopBack */,
                false /* isPointToPoint */,
                false /* isVirtual */,
                true /* supportsMulticast */,
                false /* isIpv6 */);

        provider =
                new MulticastNetworkInterfaceProvider(context, sharedLog) {
                    @Override
                    List<NetworkInterfaceWrapper> getNetworkInterfaces() {
                        return networkInterfaces;
                    }
                };
    }

    @Test
    public void testGetMulticastNetworkInterfaces() {
        // getNetworkInterfaces returns 1 multicast interface and 5 interfaces that can not be used
        // to send and receive multicast packets.
        networkInterfaces.add(loopbackInterface);
        networkInterfaces.add(pointToPointInterface);
        networkInterfaces.add(virtualInterface);
        networkInterfaces.add(inactiveMulticastInterface);
        networkInterfaces.add(nonMulticastInterface);
        networkInterfaces.add(multicastInterfaceOne);

        assertEquals(Collections.singletonList(multicastInterfaceOne),
                provider.getMulticastNetworkInterfaces());

        // getNetworkInterfaces returns 2 multicast interfaces after a connectivity change.
        networkInterfaces.clear();
        networkInterfaces.add(multicastInterfaceOne);
        networkInterfaces.add(multicastInterfaceTwo);

        provider.connectivityMonitor.notifyConnectivityChange();

        assertEquals(networkInterfaces, provider.getMulticastNetworkInterfaces());
    }

    @Test
    public void testStartWatchingConnectivityChanges() {
        ConnectivityMonitor mockMonitor = mock(ConnectivityMonitor.class);
        provider.connectivityMonitor = mockMonitor;

        InOrder inOrder = inOrder(mockMonitor);

        provider.startWatchingConnectivityChanges();
        inOrder.verify(mockMonitor).startWatchingConnectivityChanges();

        provider.stopWatchingConnectivityChanges();
        inOrder.verify(mockMonitor).stopWatchingConnectivityChanges();
    }

    @Test
    public void testIpV6OnlyNetwork_EmptyNetwork() {
        // getNetworkInterfaces returns no network interfaces.
        assertFalse(provider.isOnIpV6OnlyNetwork(networkInterfaces));
    }

    @Test
    public void testIpV6OnlyNetwork_IPv4Only() {
        // getNetworkInterfaces returns two IPv4 network interface.
        networkInterfaces.add(multicastInterfaceOne);
        networkInterfaces.add(multicastInterfaceTwo);
        assertFalse(provider.isOnIpV6OnlyNetwork(networkInterfaces));
    }

    @Test
    public void testIpV6OnlyNetwork_MixedNetwork() {
        // getNetworkInterfaces returns one IPv6 network interface.
        networkInterfaces.add(activeIpv6MulticastInterface);
        networkInterfaces.add(multicastInterfaceOne);
        networkInterfaces.add(activeIpv6MulticastInterfaceTwo);
        networkInterfaces.add(multicastInterfaceTwo);
        assertFalse(provider.isOnIpV6OnlyNetwork(networkInterfaces));
    }

    @Test
    public void testIpV6OnlyNetwork_IPv6Only() {
        // getNetworkInterfaces returns one IPv6 network interface.
        networkInterfaces.add(activeIpv6MulticastInterface);
        networkInterfaces.add(activeIpv6MulticastInterfaceTwo);
        assertTrue(provider.isOnIpV6OnlyNetwork(networkInterfaces));
    }

    @Test
    public void testIpV6OnlyNetwork_IPv6Enabled() {
        // getNetworkInterfaces returns one IPv6 network interface.
        networkInterfaces.add(activeIpv6MulticastInterface);
        assertTrue(provider.isOnIpV6OnlyNetwork(networkInterfaces));

        final List<NetworkInterfaceWrapper> interfaces = provider.getMulticastNetworkInterfaces();
        assertEquals(Collections.singletonList(activeIpv6MulticastInterface), interfaces);
    }

    private void setupNetworkInterface(
            @NonNull NetworkInterfaceWrapper networkInterfaceWrapper,
            boolean isUp,
            boolean isLoopback,
            boolean isPointToPoint,
            boolean isVirtual,
            boolean supportsMulticast,
            boolean isIpv6)
            throws SocketException, UnknownHostException {
        when(networkInterfaceWrapper.isUp()).thenReturn(isUp);
        when(networkInterfaceWrapper.isLoopback()).thenReturn(isLoopback);
        when(networkInterfaceWrapper.isPointToPoint()).thenReturn(isPointToPoint);
        when(networkInterfaceWrapper.isVirtual()).thenReturn(isVirtual);
        when(networkInterfaceWrapper.supportsMulticast()).thenReturn(supportsMulticast);
        if (isIpv6) {
            InterfaceAddress interfaceAddress = mock(InterfaceAddress.class);
            InetAddress ip6Address = Inet6Address.getByName("2001:4860:0:1001::68");
            when(interfaceAddress.getAddress()).thenReturn(ip6Address);
            when(networkInterfaceWrapper.getInterfaceAddresses())
                    .thenReturn(Collections.singletonList(interfaceAddress));
        } else {
            Inet4Address ip = (Inet4Address) Inet4Address.getByName("192.168.0.1");
            InterfaceAddress interfaceAddress = mock(InterfaceAddress.class);
            when(interfaceAddress.getAddress()).thenReturn(ip);
            when(networkInterfaceWrapper.getInterfaceAddresses())
                    .thenReturn(Collections.singletonList(interfaceAddress));
        }
    }
}