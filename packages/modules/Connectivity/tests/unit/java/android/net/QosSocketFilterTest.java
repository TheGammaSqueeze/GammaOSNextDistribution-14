/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.net;

import static android.net.QosCallbackException.EX_TYPE_FILTER_NONE;
import static android.net.QosCallbackException.EX_TYPE_FILTER_SOCKET_LOCAL_ADDRESS_CHANGED;
import static android.net.QosCallbackException.EX_TYPE_FILTER_SOCKET_NOT_BOUND;
import static android.net.QosCallbackException.EX_TYPE_FILTER_SOCKET_NOT_CONNECTED;
import static android.net.QosCallbackException.EX_TYPE_FILTER_SOCKET_REMOTE_ADDRESS_CHANGED;
import static android.system.OsConstants.IPPROTO_TCP;
import static android.system.OsConstants.IPPROTO_UDP;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import androidx.test.filters.SmallTest;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
public class QosSocketFilterTest {
    private static final int TEST_NET_ID = 1777;
    private final Network mNetwork = new Network(TEST_NET_ID);
    @Test
    public void testPortExactMatch() {
        final InetAddress addressA = InetAddresses.parseNumericAddress("1.2.3.4");
        final InetAddress addressB = InetAddresses.parseNumericAddress("1.2.3.4");
        assertTrue(QosSocketFilter.matchesAddress(
                new InetSocketAddress(addressA, 10), addressB, 10, 10));
    }

    @Test
    public void testPortLessThanStart() {
        final InetAddress addressA = InetAddresses.parseNumericAddress("1.2.3.4");
        final InetAddress addressB = InetAddresses.parseNumericAddress("1.2.3.4");
        assertFalse(QosSocketFilter.matchesAddress(
                new InetSocketAddress(addressA, 8), addressB, 10, 10));
    }

    @Test
    public void testPortGreaterThanEnd() {
        final InetAddress addressA = InetAddresses.parseNumericAddress("1.2.3.4");
        final InetAddress addressB = InetAddresses.parseNumericAddress("1.2.3.4");
        assertFalse(QosSocketFilter.matchesAddress(
                new InetSocketAddress(addressA, 18), addressB, 10, 10));
    }

    @Test
    public void testPortBetweenStartAndEnd() {
        final InetAddress addressA = InetAddresses.parseNumericAddress("1.2.3.4");
        final InetAddress addressB = InetAddresses.parseNumericAddress("1.2.3.4");
        assertTrue(QosSocketFilter.matchesAddress(
                new InetSocketAddress(addressA, 10), addressB, 8, 18));
    }

    @Test
    public void testAddressesDontMatch() {
        final InetAddress addressA = InetAddresses.parseNumericAddress("1.2.3.4");
        final InetAddress addressB = InetAddresses.parseNumericAddress("1.2.3.5");
        assertFalse(QosSocketFilter.matchesAddress(
                new InetSocketAddress(addressA, 10), addressB, 10, 10));
    }

    @Test
    public void testAddressMatchWithAnyLocalAddresses() {
        final InetAddress addressA = InetAddresses.parseNumericAddress("1.2.3.4");
        final InetAddress addressB = InetAddresses.parseNumericAddress("0.0.0.0");
        assertTrue(QosSocketFilter.matchesAddress(
                new InetSocketAddress(addressA, 10), addressB, 10, 10));
        assertFalse(QosSocketFilter.matchesAddress(
                new InetSocketAddress(addressB, 10), addressA, 10, 10));
    }

    @Test
    public void testProtocolMatch() throws Exception {
        DatagramSocket socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0));
        socket.connect(new InetSocketAddress("127.0.0.1", socket.getLocalPort() + 10));
        DatagramSocket socketV6 = new DatagramSocket(new InetSocketAddress("::1", 0));
        socketV6.connect(new InetSocketAddress("::1", socketV6.getLocalPort() + 10));
        QosSocketInfo socketInfo = new QosSocketInfo(mNetwork, socket);
        QosSocketFilter socketFilter = new QosSocketFilter(socketInfo);
        QosSocketInfo socketInfo6 = new QosSocketInfo(mNetwork, socketV6);
        QosSocketFilter socketFilter6 = new QosSocketFilter(socketInfo6);
        assertTrue(socketFilter.matchesProtocol(IPPROTO_UDP));
        assertTrue(socketFilter6.matchesProtocol(IPPROTO_UDP));
        assertFalse(socketFilter.matchesProtocol(IPPROTO_TCP));
        assertFalse(socketFilter6.matchesProtocol(IPPROTO_TCP));
        socket.close();
        socketV6.close();
    }

    @Test
    public void testValidate() throws Exception {
        DatagramSocket socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0));
        socket.connect(new InetSocketAddress("127.0.0.1", socket.getLocalPort() + 7));
        DatagramSocket socketV6 = new DatagramSocket(new InetSocketAddress("::1", 0));

        QosSocketInfo socketInfo = new QosSocketInfo(mNetwork, socket);
        QosSocketFilter socketFilter = new QosSocketFilter(socketInfo);
        QosSocketInfo socketInfo6 = new QosSocketInfo(mNetwork, socketV6);
        QosSocketFilter socketFilter6 = new QosSocketFilter(socketInfo6);
        assertEquals(EX_TYPE_FILTER_NONE, socketFilter.validate());
        assertEquals(EX_TYPE_FILTER_NONE, socketFilter6.validate());
        socket.close();
        socketV6.close();
    }

    @Test
    public void testValidateUnbind() throws Exception {
        DatagramSocket socket;
        socket = new DatagramSocket(null);
        QosSocketInfo socketInfo = new QosSocketInfo(mNetwork, socket);
        QosSocketFilter socketFilter = new QosSocketFilter(socketInfo);
        assertEquals(EX_TYPE_FILTER_SOCKET_NOT_BOUND, socketFilter.validate());
        socket.close();
    }

    @Test
    public void testValidateLocalAddressChanged() throws Exception {
        DatagramSocket socket = new DatagramSocket(null);
        DatagramSocket socket6 = new DatagramSocket(null);
        QosSocketInfo socketInfo = new QosSocketInfo(mNetwork, socket);
        QosSocketFilter socketFilter = new QosSocketFilter(socketInfo);
        QosSocketInfo socketInfo6 = new QosSocketInfo(mNetwork, socket6);
        QosSocketFilter socketFilter6 = new QosSocketFilter(socketInfo6);
        socket.bind(new InetSocketAddress("127.0.0.1", 0));
        socket6.bind(new InetSocketAddress("::1", 0));
        assertEquals(EX_TYPE_FILTER_SOCKET_LOCAL_ADDRESS_CHANGED, socketFilter.validate());
        assertEquals(EX_TYPE_FILTER_SOCKET_LOCAL_ADDRESS_CHANGED, socketFilter6.validate());
        socket.close();
        socket6.close();
    }

    @Test
    public void testValidateRemoteAddressChanged() throws Exception {
        DatagramSocket socket;
        socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 53137));
        socket.connect(new InetSocketAddress("127.0.0.1", socket.getLocalPort() + 11));
        QosSocketInfo socketInfo = new QosSocketInfo(mNetwork, socket);
        QosSocketFilter socketFilter = new QosSocketFilter(socketInfo);
        assertEquals(EX_TYPE_FILTER_NONE, socketFilter.validate());
        socket.disconnect();
        assertEquals(EX_TYPE_FILTER_SOCKET_NOT_CONNECTED, socketFilter.validate());
        socket.connect(new InetSocketAddress("127.0.0.1", socket.getLocalPort() + 13));
        assertEquals(EX_TYPE_FILTER_SOCKET_REMOTE_ADDRESS_CHANGED, socketFilter.validate());
        socket.close();
    }
}

