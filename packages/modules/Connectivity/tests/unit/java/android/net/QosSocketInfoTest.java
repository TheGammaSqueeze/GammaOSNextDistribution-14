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

package android.net;

import static android.system.OsConstants.SOCK_DGRAM;
import static android.system.OsConstants.SOCK_STREAM;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.os.Build;

import androidx.test.filters.SmallTest;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
public class QosSocketInfoTest {
    @Mock
    private Network mMockNetwork = mock(Network.class);

    @Test
    public void testConstructWithSock() throws Exception {
        ServerSocket server = new ServerSocket();
        ServerSocket server6 = new ServerSocket();

        InetSocketAddress clientAddr = new InetSocketAddress("127.0.0.1", 0);
        InetSocketAddress serverAddr = new InetSocketAddress("127.0.0.1", 0);
        InetSocketAddress clientAddr6 = new InetSocketAddress("::1", 0);
        InetSocketAddress serverAddr6 = new InetSocketAddress("::1", 0);
        server.bind(serverAddr);
        server6.bind(serverAddr6);
        Socket socket = new Socket(serverAddr.getAddress(), server.getLocalPort(),
                clientAddr.getAddress(), clientAddr.getPort());
        Socket socket6 = new Socket(serverAddr6.getAddress(), server6.getLocalPort(),
                clientAddr6.getAddress(), clientAddr6.getPort());
        QosSocketInfo sockInfo = new QosSocketInfo(mMockNetwork, socket);
        QosSocketInfo sockInfo6 = new QosSocketInfo(mMockNetwork, socket6);
        assertTrue(sockInfo.getLocalSocketAddress()
                .equals(new InetSocketAddress(socket.getLocalAddress(), socket.getLocalPort())));
        assertTrue(sockInfo.getRemoteSocketAddress()
                .equals((InetSocketAddress) socket.getRemoteSocketAddress()));
        assertEquals(SOCK_STREAM, sockInfo.getSocketType());
        assertTrue(sockInfo6.getLocalSocketAddress()
                .equals(new InetSocketAddress(socket6.getLocalAddress(), socket6.getLocalPort())));
        assertTrue(sockInfo6.getRemoteSocketAddress()
                .equals((InetSocketAddress) socket6.getRemoteSocketAddress()));
        assertEquals(SOCK_STREAM, sockInfo6.getSocketType());
        socket.close();
        socket6.close();
        server.close();
        server6.close();
    }

    @Test
    public void testConstructWithDatagramSock() throws Exception {
        InetSocketAddress clientAddr = new InetSocketAddress("127.0.0.1", 0);
        InetSocketAddress serverAddr = new InetSocketAddress("127.0.0.1", 0);
        InetSocketAddress clientAddr6 = new InetSocketAddress("::1", 0);
        InetSocketAddress serverAddr6 = new InetSocketAddress("::1", 0);
        DatagramSocket socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.bind(clientAddr);
        socket.connect(serverAddr);
        DatagramSocket socket6 = new DatagramSocket(null);
        socket6.setReuseAddress(true);
        socket6.bind(clientAddr);
        socket6.connect(serverAddr);
        QosSocketInfo sockInfo = new QosSocketInfo(mMockNetwork, socket);
        QosSocketInfo sockInfo6 = new QosSocketInfo(mMockNetwork, socket6);
        assertTrue(sockInfo.getLocalSocketAddress()
                .equals((InetSocketAddress) socket.getLocalSocketAddress()));
        assertTrue(sockInfo.getRemoteSocketAddress()
                .equals((InetSocketAddress) socket.getRemoteSocketAddress()));
        assertEquals(SOCK_DGRAM, sockInfo.getSocketType());
        assertTrue(sockInfo6.getLocalSocketAddress()
                .equals((InetSocketAddress) socket6.getLocalSocketAddress()));
        assertTrue(sockInfo6.getRemoteSocketAddress()
                .equals((InetSocketAddress) socket6.getRemoteSocketAddress()));
        assertEquals(SOCK_DGRAM, sockInfo6.getSocketType());
        socket.close();
    }
}
