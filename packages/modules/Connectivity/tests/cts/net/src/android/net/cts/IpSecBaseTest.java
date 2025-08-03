/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.net.cts;

import static android.net.IpSecAlgorithm.AUTH_CRYPT_AES_GCM;
import static android.net.IpSecAlgorithm.AUTH_HMAC_MD5;
import static android.net.IpSecAlgorithm.AUTH_HMAC_SHA1;
import static android.net.IpSecAlgorithm.AUTH_HMAC_SHA256;
import static android.net.IpSecAlgorithm.AUTH_HMAC_SHA384;
import static android.net.IpSecAlgorithm.AUTH_HMAC_SHA512;
import static android.net.IpSecAlgorithm.CRYPT_AES_CBC;
import static android.system.OsConstants.FIONREAD;

import static org.junit.Assert.assertArrayEquals;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.IpSecAlgorithm;
import android.net.IpSecManager;
import android.net.IpSecTransform;
import android.platform.test.annotations.AppModeFull;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructTimeval;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.SocketOptions;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class IpSecBaseTest {

    private static final String TAG = IpSecBaseTest.class.getSimpleName();

    protected static final String IPV4_LOOPBACK = "127.0.0.1";
    protected static final String IPV6_LOOPBACK = "::1";
    protected static final String[] LOOPBACK_ADDRS = new String[] {IPV4_LOOPBACK, IPV6_LOOPBACK};
    protected static final int[] DIRECTIONS =
            new int[] {IpSecManager.DIRECTION_IN, IpSecManager.DIRECTION_OUT};

    protected static final byte[] TEST_DATA = "Best test data ever!".getBytes();
    protected static final int DATA_BUFFER_LEN = 4096;
    protected static final int SOCK_TIMEOUT = 500;

    private static final byte[] KEY_DATA = {
        0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
        0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
        0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
        0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F,
        0x20, 0x21, 0x22, 0x23
    };

    private static final Set<String> MANDATORY_IPSEC_ALGOS_SINCE_P = new HashSet<>();

    static {
        MANDATORY_IPSEC_ALGOS_SINCE_P.add(CRYPT_AES_CBC);
        MANDATORY_IPSEC_ALGOS_SINCE_P.add(AUTH_HMAC_MD5);
        MANDATORY_IPSEC_ALGOS_SINCE_P.add(AUTH_HMAC_SHA1);
        MANDATORY_IPSEC_ALGOS_SINCE_P.add(AUTH_HMAC_SHA256);
        MANDATORY_IPSEC_ALGOS_SINCE_P.add(AUTH_HMAC_SHA384);
        MANDATORY_IPSEC_ALGOS_SINCE_P.add(AUTH_HMAC_SHA512);
        MANDATORY_IPSEC_ALGOS_SINCE_P.add(AUTH_CRYPT_AES_GCM);
    }

    protected static final byte[] AUTH_KEY = getKey(256);
    protected static final byte[] CRYPT_KEY = getKey(256);

    protected ConnectivityManager mCM;
    protected IpSecManager mISM;

    @Before
    public void setUp() throws Exception {
        mISM =
                (IpSecManager)
                        InstrumentationRegistry.getContext()
                                .getSystemService(Context.IPSEC_SERVICE);
        mCM =
                (ConnectivityManager)
                        InstrumentationRegistry.getContext()
                                .getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /** Checks if an IPsec algorithm is enabled on the device */
    protected static boolean hasIpSecAlgorithm(String algorithm) {
        if (SdkLevel.isAtLeastS()) {
            return IpSecAlgorithm.getSupportedAlgorithms().contains(algorithm);
        } else {
            return MANDATORY_IPSEC_ALGOS_SINCE_P.contains(algorithm);
        }
    }

    protected static byte[] getKeyBytes(int byteLength) {
        return Arrays.copyOf(KEY_DATA, byteLength);
    }

    protected static byte[] getKey(int bitLength) {
        if (bitLength % 8 != 0) {
            throw new IllegalArgumentException("Invalid key length in bits" + bitLength);
        }
        return getKeyBytes(bitLength / 8);
    }

    protected static int getDomain(InetAddress address) {
        int domain;
        if (address instanceof Inet6Address) {
            domain = OsConstants.AF_INET6;
        } else {
            domain = OsConstants.AF_INET;
        }
        return domain;
    }

    protected static int getPort(FileDescriptor sock) throws Exception {
        return ((InetSocketAddress) Os.getsockname(sock)).getPort();
    }

    public static interface GenericSocket extends AutoCloseable {
        void send(byte[] data) throws Exception;

        byte[] receive() throws Exception;

        int getPort() throws Exception;

        void close() throws Exception;

        void applyTransportModeTransform(
                IpSecManager ism, int direction, IpSecTransform transform) throws Exception;

        void removeTransportModeTransforms(IpSecManager ism) throws Exception;
    }

    public static interface GenericTcpSocket extends GenericSocket {}

    public static interface GenericUdpSocket extends GenericSocket {
        void sendTo(byte[] data, InetAddress dstAddr, int port) throws Exception;
    }

    public abstract static class NativeSocket implements GenericSocket {
        public FileDescriptor mFd;

        public NativeSocket(FileDescriptor fd) {
            mFd = fd;
        }

        @Override
        public void send(byte[] data) throws Exception {
            Os.write(mFd, data, 0, data.length);
        }

        @Override
        public byte[] receive() throws Exception {
            byte[] in = new byte[DATA_BUFFER_LEN];
            AtomicInteger bytesRead = new AtomicInteger(-1);

            Thread readSockThread = new Thread(() -> {
                long startTime = System.currentTimeMillis();
                while (bytesRead.get() < 0 && System.currentTimeMillis() < startTime + SOCK_TIMEOUT) {
                    try {
                        bytesRead.set(Os.recvfrom(mFd, in, 0, DATA_BUFFER_LEN, 0, null));
                    } catch (Exception e) {
                        Log.e(TAG, "Error encountered reading from socket", e);
                    }
                }
            });

            readSockThread.start();
            readSockThread.join(SOCK_TIMEOUT);

            if (bytesRead.get() < 0) {
                throw new IOException("No data received from socket");
            }

            return Arrays.copyOfRange(in, 0, bytesRead.get());
        }

        @Override
        public int getPort() throws Exception {
            return IpSecBaseTest.getPort(mFd);
        }

        @Override
        public void close() throws Exception {
            Os.close(mFd);
        }

        @Override
        public void applyTransportModeTransform(
                IpSecManager ism, int direction, IpSecTransform transform) throws Exception {
            ism.applyTransportModeTransform(mFd, direction, transform);
        }

        @Override
        public void removeTransportModeTransforms(IpSecManager ism) throws Exception {
            ism.removeTransportModeTransforms(mFd);
        }
    }

    public static class NativeTcpSocket extends NativeSocket implements GenericTcpSocket {
        public NativeTcpSocket(FileDescriptor fd) {
            super(fd);
        }

        public JavaTcpSocket acceptToJavaSocket() throws Exception {
            InetSocketAddress peer = new InetSocketAddress(0);
            FileDescriptor newFd = Os.accept(mFd, peer);
            return new JavaTcpSocket(new AcceptedTcpFileDescriptorSocket(newFd, peer, getPort()));
        }
    }

    public static class NativeUdpSocket extends NativeSocket implements GenericUdpSocket {
        public NativeUdpSocket(FileDescriptor fd) {
            super(fd);
        }

        @Override
        public void sendTo(byte[] data, InetAddress dstAddr, int port) throws Exception {
            Os.sendto(mFd, data, 0, data.length, 0, dstAddr, port);
        }
    }

    public static class JavaUdpSocket implements GenericUdpSocket {
        public final DatagramSocket mSocket;

        public JavaUdpSocket(InetAddress localAddr, int port) {
            try {
                mSocket = new DatagramSocket(port, localAddr);
                mSocket.setSoTimeout(SOCK_TIMEOUT);
            } catch (SocketException e) {
                // Fail loudly if we can't set up sockets properly. And without the timeout, we
                // could easily end up in an endless wait.
                throw new RuntimeException(e);
            }
        }

        public JavaUdpSocket(InetAddress localAddr) {
            try {
                mSocket = new DatagramSocket(0, localAddr);
                mSocket.setSoTimeout(SOCK_TIMEOUT);
            } catch (SocketException e) {
                // Fail loudly if we can't set up sockets properly. And without the timeout, we
                // could easily end up in an endless wait.
                throw new RuntimeException(e);
            }
        }

        @Override
        public void send(byte[] data) throws Exception {
            mSocket.send(new DatagramPacket(data, data.length));
        }

        @Override
        public void sendTo(byte[] data, InetAddress dstAddr, int port) throws Exception {
            mSocket.send(new DatagramPacket(data, data.length, dstAddr, port));
        }

        @Override
        public int getPort() throws Exception {
            return mSocket.getLocalPort();
        }

        @Override
        public void close() throws Exception {
            mSocket.close();
        }

        @Override
        public byte[] receive() throws Exception {
            DatagramPacket data = new DatagramPacket(new byte[DATA_BUFFER_LEN], DATA_BUFFER_LEN);
            mSocket.receive(data);
            return Arrays.copyOfRange(data.getData(), 0, data.getLength());
        }

        @Override
        public void applyTransportModeTransform(
                IpSecManager ism, int direction, IpSecTransform transform) throws Exception {
            ism.applyTransportModeTransform(mSocket, direction, transform);
        }

        @Override
        public void removeTransportModeTransforms(IpSecManager ism) throws Exception {
            ism.removeTransportModeTransforms(mSocket);
        }
    }

    public static class JavaTcpSocket implements GenericTcpSocket {
        public final Socket mSocket;

        public JavaTcpSocket(Socket socket) {
            mSocket = socket;
            try {
                mSocket.setSoTimeout(SOCK_TIMEOUT);
            } catch (SocketException e) {
                // Fail loudly if we can't set up sockets properly. And without the timeout, we
                // could easily end up in an endless wait.
                throw new RuntimeException(e);
            }
        }

        @Override
        public void send(byte[] data) throws Exception {
            mSocket.getOutputStream().write(data);
        }

        @Override
        public byte[] receive() throws Exception {
            byte[] in = new byte[DATA_BUFFER_LEN];
            int bytesRead = mSocket.getInputStream().read(in);
            return Arrays.copyOfRange(in, 0, bytesRead);
        }

        @Override
        public int getPort() throws Exception {
            return mSocket.getLocalPort();
        }

        @Override
        public void close() throws Exception {
            mSocket.close();
        }

        @Override
        public void applyTransportModeTransform(
                IpSecManager ism, int direction, IpSecTransform transform) throws Exception {
            ism.applyTransportModeTransform(mSocket, direction, transform);
        }

        @Override
        public void removeTransportModeTransforms(IpSecManager ism) throws Exception {
            ism.removeTransportModeTransforms(mSocket);
        }
    }

    private static class AcceptedTcpFileDescriptorSocket extends Socket {

        AcceptedTcpFileDescriptorSocket(FileDescriptor fd, InetSocketAddress remote,
                int localPort) throws IOException {
            super(new FileDescriptorSocketImpl(fd, remote, localPort));
            connect(remote);
        }

        private static class FileDescriptorSocketImpl extends SocketImpl {

            private FileDescriptorSocketImpl(FileDescriptor fd, InetSocketAddress remote,
                    int localPort) {
                this.fd = fd;
                this.address = remote.getAddress();
                this.port = remote.getPort();
                this.localport = localPort;
            }

            @Override
            protected void create(boolean stream) throws IOException {
                // The socket has been created.
            }

            @Override
            protected void connect(String host, int port) throws IOException {
                // The socket has connected.
            }

            @Override
            protected void connect(InetAddress address, int port) throws IOException {
                // The socket has connected.
            }

            @Override
            protected void connect(SocketAddress address, int timeout) throws IOException {
                // The socket has connected.
            }

            @Override
            protected void bind(InetAddress host, int port) throws IOException {
                // The socket is bounded.
            }

            @Override
            protected void listen(int backlog) throws IOException {
                throw new UnsupportedOperationException("listen");
            }

            @Override
            protected void accept(SocketImpl s) throws IOException {
                throw new UnsupportedOperationException("accept");
            }

            @Override
            protected InputStream getInputStream() throws IOException {
                return new FileInputStream(fd);
            }

            @Override
            protected OutputStream getOutputStream() throws IOException {
                return new FileOutputStream(fd);
            }

            @Override
            protected int available() throws IOException {
                try {
                    return Os.ioctlInt(fd, FIONREAD);
                } catch (ErrnoException e) {
                    throw new IOException(e);
                }
            }

            @Override
            protected void close() throws IOException {
                try {
                    Os.close(fd);
                } catch (ErrnoException e) {
                    throw new IOException(e);
                }
            }

            @Override
            protected void sendUrgentData(int data) throws IOException {
                throw new UnsupportedOperationException("sendUrgentData");
            }

            @Override
            public void setOption(int optID, Object value) throws SocketException {
                try {
                    setOptionInternal(optID, value);
                } catch (ErrnoException e) {
                    throw new SocketException(e.getMessage());
                }
            }

            private void setOptionInternal(int optID, Object value) throws ErrnoException,
                    SocketException {
                switch(optID) {
                    case SocketOptions.SO_TIMEOUT:
                        int millis = (Integer) value;
                        StructTimeval tv = StructTimeval.fromMillis(millis);
                        Os.setsockoptTimeval(fd, OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO,
                                tv);
                        return;
                    default:
                        throw new SocketException("Unknown socket option: " + optID);
                }
            }

            @Override
            public Object getOption(int optID) throws SocketException {
                try {
                    return getOptionInternal(optID);
                } catch (ErrnoException e) {
                    throw new SocketException(e.getMessage());
                }
            }

            private Object getOptionInternal(int optID) throws ErrnoException, SocketException {
                switch (optID) {
                    case SocketOptions.SO_LINGER:
                        // Returns an arbitrary value because IpSecManager doesn't actually
                        // use this value.
                        return 10;
                    default:
                        throw new SocketException("Unknown socket option: " + optID);
                }
            }
        }
    }

    public static class SocketPair<T> {
        public final T mLeftSock;
        public final T mRightSock;

        public SocketPair(T leftSock, T rightSock) {
            mLeftSock = leftSock;
            mRightSock = rightSock;
        }
    }

    protected static void applyTransformBidirectionally(
            IpSecManager ism, IpSecTransform transform, GenericSocket socket) throws Exception {
        for (int direction : DIRECTIONS) {
            socket.applyTransportModeTransform(ism, direction, transform);
        }
    }

    public static SocketPair<NativeUdpSocket> getNativeUdpSocketPair(
            InetAddress localAddr, IpSecManager ism, IpSecTransform transform, boolean connected)
            throws Exception {
        int domain = getDomain(localAddr);

        NativeUdpSocket leftSock = new NativeUdpSocket(
            Os.socket(domain, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_UDP));
        NativeUdpSocket rightSock = new NativeUdpSocket(
            Os.socket(domain, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_UDP));

        for (NativeUdpSocket sock : new NativeUdpSocket[] {leftSock, rightSock}) {
            applyTransformBidirectionally(ism, transform, sock);
            Os.bind(sock.mFd, localAddr, 0);
        }

        if (connected) {
            Os.connect(leftSock.mFd, localAddr, rightSock.getPort());
            Os.connect(rightSock.mFd, localAddr, leftSock.getPort());
        }

        return new SocketPair<>(leftSock, rightSock);
    }

    public static SocketPair<NativeTcpSocket> getNativeTcpSocketPair(
            InetAddress localAddr, IpSecManager ism, IpSecTransform transform) throws Exception {
        int domain = getDomain(localAddr);

        NativeTcpSocket server = new NativeTcpSocket(
                Os.socket(domain, OsConstants.SOCK_STREAM, OsConstants.IPPROTO_TCP));
        NativeTcpSocket client = new NativeTcpSocket(
                Os.socket(domain, OsConstants.SOCK_STREAM, OsConstants.IPPROTO_TCP));

        Os.bind(server.mFd, localAddr, 0);

        applyTransformBidirectionally(ism, transform, server);
        applyTransformBidirectionally(ism, transform, client);

        Os.listen(server.mFd, 10);
        Os.connect(client.mFd, localAddr, server.getPort());
        NativeTcpSocket accepted = new NativeTcpSocket(Os.accept(server.mFd, null));

        applyTransformBidirectionally(ism, transform, accepted);
        server.close();

        return new SocketPair<>(client, accepted);
    }

    public static SocketPair<JavaUdpSocket> getJavaUdpSocketPair(
            InetAddress localAddr, IpSecManager ism, IpSecTransform transform, boolean connected)
            throws Exception {
        JavaUdpSocket leftSock = new JavaUdpSocket(localAddr);
        JavaUdpSocket rightSock = new JavaUdpSocket(localAddr);

        applyTransformBidirectionally(ism, transform, leftSock);
        applyTransformBidirectionally(ism, transform, rightSock);

        if (connected) {
            leftSock.mSocket.connect(localAddr, rightSock.mSocket.getLocalPort());
            rightSock.mSocket.connect(localAddr, leftSock.mSocket.getLocalPort());
        }

        return new SocketPair<>(leftSock, rightSock);
    }

    public static SocketPair<JavaTcpSocket> getJavaTcpSocketPair(
            InetAddress localAddr, IpSecManager ism, IpSecTransform transform) throws Exception {
        JavaTcpSocket clientSock = new JavaTcpSocket(new Socket());

        // While technically the client socket does not need to be bound, the OpenJDK implementation
        // of Socket only allocates an FD when bind() or connect() or other similar methods are
        // called. So we call bind to force the FD creation, so that we can apply a transform to it
        // prior to socket connect.
        clientSock.mSocket.bind(new InetSocketAddress(localAddr, 0));

        // IpSecService doesn't support serverSockets at the moment; workaround using FD
        NativeTcpSocket server = new NativeTcpSocket(
                Os.socket(getDomain(localAddr), OsConstants.SOCK_STREAM, OsConstants.IPPROTO_TCP));
        Os.bind(server.mFd, localAddr, 0);

        applyTransformBidirectionally(ism, transform, server);
        applyTransformBidirectionally(ism, transform, clientSock);

        Os.listen(server.mFd, 10 /* backlog */);
        clientSock.mSocket.connect(new InetSocketAddress(localAddr, server.getPort()));
        JavaTcpSocket acceptedSock = server.acceptToJavaSocket();

        applyTransformBidirectionally(ism, transform, acceptedSock);
        server.close();

        return new SocketPair<>(clientSock, acceptedSock);
    }

    private void checkSocketPair(GenericSocket left, GenericSocket right) throws Exception {
        left.send(TEST_DATA);
        assertArrayEquals(TEST_DATA, right.receive());

        right.send(TEST_DATA);
        assertArrayEquals(TEST_DATA, left.receive());

        left.close();
        right.close();
    }

    private void checkUnconnectedUdpSocketPair(
            GenericUdpSocket left, GenericUdpSocket right, InetAddress localAddr) throws Exception {
        left.sendTo(TEST_DATA, localAddr, right.getPort());
        assertArrayEquals(TEST_DATA, right.receive());

        right.sendTo(TEST_DATA, localAddr, left.getPort());
        assertArrayEquals(TEST_DATA, left.receive());

        left.close();
        right.close();
    }

    protected static IpSecTransform buildIpSecTransform(
            Context context,
            IpSecManager.SecurityParameterIndex spi,
            IpSecManager.UdpEncapsulationSocket encapSocket,
            InetAddress remoteAddr)
            throws Exception {
        IpSecTransform.Builder builder =
                new IpSecTransform.Builder(context)
                        .setEncryption(new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY))
                        .setAuthentication(
                                new IpSecAlgorithm(
                                        IpSecAlgorithm.AUTH_HMAC_SHA256,
                                        AUTH_KEY,
                                        AUTH_KEY.length * 4));

        if (encapSocket != null) {
            builder.setIpv4Encapsulation(encapSocket, encapSocket.getPort());
        }

        return builder.buildTransportModeTransform(remoteAddr, spi);
    }

    private IpSecTransform buildDefaultTransform(InetAddress localAddr) throws Exception {
        try (IpSecManager.SecurityParameterIndex spi =
                mISM.allocateSecurityParameterIndex(localAddr)) {
            return buildIpSecTransform(InstrumentationRegistry.getContext(), spi, null, localAddr);
        }
    }

    @Test
    @AppModeFull(reason = "Socket cannot bind in instant app mode")
    public void testJavaTcpSocketPair() throws Exception {
        for (String addr : LOOPBACK_ADDRS) {
            InetAddress local = InetAddress.getByName(addr);
            try (IpSecTransform transform = buildDefaultTransform(local)) {
                SocketPair<JavaTcpSocket> sockets = getJavaTcpSocketPair(local, mISM, transform);
                checkSocketPair(sockets.mLeftSock, sockets.mRightSock);
            }
        }
    }

    @Test
    @AppModeFull(reason = "Socket cannot bind in instant app mode")
    public void testJavaUdpSocketPair() throws Exception {
        for (String addr : LOOPBACK_ADDRS) {
            InetAddress local = InetAddress.getByName(addr);
            try (IpSecTransform transform = buildDefaultTransform(local)) {
                SocketPair<JavaUdpSocket> sockets =
                        getJavaUdpSocketPair(local, mISM, transform, true);
                checkSocketPair(sockets.mLeftSock, sockets.mRightSock);
            }
        }
    }

    @Test
    @AppModeFull(reason = "Socket cannot bind in instant app mode")
    public void testJavaUdpSocketPairUnconnected() throws Exception {
        for (String addr : LOOPBACK_ADDRS) {
            InetAddress local = InetAddress.getByName(addr);
            try (IpSecTransform transform = buildDefaultTransform(local)) {
                SocketPair<JavaUdpSocket> sockets =
                        getJavaUdpSocketPair(local, mISM, transform, false);
                checkUnconnectedUdpSocketPair(sockets.mLeftSock, sockets.mRightSock, local);
            }
        }
    }

    @Test
    @AppModeFull(reason = "Socket cannot bind in instant app mode")
    public void testNativeTcpSocketPair() throws Exception {
        for (String addr : LOOPBACK_ADDRS) {
            InetAddress local = InetAddress.getByName(addr);
            try (IpSecTransform transform = buildDefaultTransform(local)) {
                SocketPair<NativeTcpSocket> sockets =
                        getNativeTcpSocketPair(local, mISM, transform);
                checkSocketPair(sockets.mLeftSock, sockets.mRightSock);
            }
        }
    }

    @Test
    @AppModeFull(reason = "Socket cannot bind in instant app mode")
    public void testNativeUdpSocketPair() throws Exception {
        for (String addr : LOOPBACK_ADDRS) {
            InetAddress local = InetAddress.getByName(addr);
            try (IpSecTransform transform = buildDefaultTransform(local)) {
                SocketPair<NativeUdpSocket> sockets =
                        getNativeUdpSocketPair(local, mISM, transform, true);
                checkSocketPair(sockets.mLeftSock, sockets.mRightSock);
            }
        }
    }

    @Test
    @AppModeFull(reason = "Socket cannot bind in instant app mode")
    public void testNativeUdpSocketPairUnconnected() throws Exception {
        for (String addr : LOOPBACK_ADDRS) {
            InetAddress local = InetAddress.getByName(addr);
            try (IpSecTransform transform = buildDefaultTransform(local)) {
                SocketPair<NativeUdpSocket> sockets =
                        getNativeUdpSocketPair(local, mISM, transform, false);
                checkUnconnectedUdpSocketPair(sockets.mLeftSock, sockets.mRightSock, local);
            }
        }
    }
}
