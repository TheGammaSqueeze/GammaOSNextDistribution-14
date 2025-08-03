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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.net.module.util.SharedLog;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;

/** Tests for {@link MdnsSocket}. */
@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(SC_V2)
public class MdnsSocketTests {

    @Mock private NetworkInterfaceWrapper mockNetworkInterfaceWrapper;
    @Mock private MulticastSocket mockMulticastSocket;
    @Mock private MulticastNetworkInterfaceProvider mockMulticastNetworkInterfaceProvider;
    @Mock private SharedLog sharedLog;
    private SocketAddress socketIPv4Address;
    private SocketAddress socketIPv6Address;

    private final byte[] data = new byte[25];
    private final DatagramPacket datagramPacket = new DatagramPacket(data, data.length);
    private NetworkInterface networkInterface;

    private MdnsSocket mdnsSocket;

    @Before
    public void setUp() throws SocketException, UnknownHostException {
        MockitoAnnotations.initMocks(this);

        networkInterface = createEmptyNetworkInterface();
        when(mockNetworkInterfaceWrapper.getNetworkInterface()).thenReturn(networkInterface);
        when(mockMulticastNetworkInterfaceProvider.getMulticastNetworkInterfaces())
                .thenReturn(Collections.singletonList(mockNetworkInterfaceWrapper));
        socketIPv4Address = new InetSocketAddress(
                InetAddress.getByName("224.0.0.251"), MdnsConstants.MDNS_PORT);
        socketIPv6Address = new InetSocketAddress(
                InetAddress.getByName("FF02::FB"), MdnsConstants.MDNS_PORT);
    }

    @Test
    public void mdnsSocket_basicFunctionality() throws IOException {
        mdnsSocket = new MdnsSocket(mockMulticastNetworkInterfaceProvider, mockMulticastSocket,
                sharedLog);
        mdnsSocket.send(datagramPacket);
        verify(mockMulticastSocket).setNetworkInterface(networkInterface);
        verify(mockMulticastSocket).send(datagramPacket);

        mdnsSocket.receive(datagramPacket);
        verify(mockMulticastSocket).receive(datagramPacket);

        mdnsSocket.joinGroup();
        verify(mockMulticastSocket).joinGroup(socketIPv4Address, networkInterface);

        mdnsSocket.leaveGroup();
        verify(mockMulticastSocket).leaveGroup(socketIPv4Address, networkInterface);

        mdnsSocket.close();
        verify(mockMulticastSocket).close();
    }

    @Test
    public void ipv6OnlyNetwork_ipv6Enabled() throws IOException {
        // Have mockMulticastNetworkInterfaceProvider send back an IPv6Only networkInterfaceWrapper
        networkInterface = createEmptyNetworkInterface();
        when(mockNetworkInterfaceWrapper.getNetworkInterface()).thenReturn(networkInterface);
        when(mockMulticastNetworkInterfaceProvider.getMulticastNetworkInterfaces())
                .thenReturn(Collections.singletonList(mockNetworkInterfaceWrapper));

        mdnsSocket = new MdnsSocket(mockMulticastNetworkInterfaceProvider, mockMulticastSocket,
                sharedLog);

        when(mockMulticastNetworkInterfaceProvider.isOnIpV6OnlyNetwork(
                Collections.singletonList(mockNetworkInterfaceWrapper)))
                .thenReturn(true);

        mdnsSocket.joinGroup();
        verify(mockMulticastSocket).joinGroup(socketIPv6Address, networkInterface);

        mdnsSocket.leaveGroup();
        verify(mockMulticastSocket).leaveGroup(socketIPv6Address, networkInterface);

        mdnsSocket.close();
        verify(mockMulticastSocket).close();
    }

    @Test
    public void ipv6OnlyNetwork_ipv6Toggle() throws IOException {
        // Have mockMulticastNetworkInterfaceProvider send back a networkInterfaceWrapper
        networkInterface = createEmptyNetworkInterface();
        when(mockNetworkInterfaceWrapper.getNetworkInterface()).thenReturn(networkInterface);
        when(mockMulticastNetworkInterfaceProvider.getMulticastNetworkInterfaces())
                .thenReturn(Collections.singletonList(mockNetworkInterfaceWrapper));

        mdnsSocket = new MdnsSocket(mockMulticastNetworkInterfaceProvider, mockMulticastSocket,
                sharedLog);

        when(mockMulticastNetworkInterfaceProvider.isOnIpV6OnlyNetwork(
                Collections.singletonList(mockNetworkInterfaceWrapper)))
                .thenReturn(true);

        mdnsSocket.joinGroup();
        verify(mockMulticastSocket).joinGroup(socketIPv6Address, networkInterface);

        mdnsSocket.leaveGroup();
        verify(mockMulticastSocket).leaveGroup(socketIPv6Address, networkInterface);

        mdnsSocket.close();
        verify(mockMulticastSocket).close();
    }

    private NetworkInterface createEmptyNetworkInterface() {
        try {
            Constructor<NetworkInterface> constructor =
                    NetworkInterface.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}