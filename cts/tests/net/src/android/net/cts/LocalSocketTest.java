/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;
import static com.android.testutils.MiscAsserts.assertThrows;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.Credentials;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructTimeval;

import androidx.test.runner.AndroidJUnit4;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LocalSocketTest {
    private final static String ADDRESS_PREFIX = "com.android.net.LocalSocketTest";

    @Rule
    public final DevSdkIgnoreRule mIgnoreRule = new DevSdkIgnoreRule();

    @Test
    public void testLocalConnections() throws IOException {
        String address = ADDRESS_PREFIX + "_testLocalConnections";
        // create client and server socket
        LocalServerSocket localServerSocket = new LocalServerSocket(address);
        LocalSocket clientSocket = new LocalSocket();

        // establish connection between client and server
        LocalSocketAddress locSockAddr = new LocalSocketAddress(address);
        assertFalse(clientSocket.isConnected());
        clientSocket.connect(locSockAddr);
        assertTrue(clientSocket.isConnected());

        LocalSocket serverSocket = localServerSocket.accept();
        assertTrue(serverSocket.isConnected());
        assertTrue(serverSocket.isBound());
        assertThrows(IOException.class, () -> {
            serverSocket.bind(localServerSocket.getLocalSocketAddress());
        });
        assertThrows(IOException.class, () -> {
            serverSocket.connect(locSockAddr);
        });

        Credentials credent = clientSocket.getPeerCredentials();
        assertTrue(0 != credent.getPid());

        // send data from client to server
        OutputStream clientOutStream = clientSocket.getOutputStream();
        clientOutStream.write(12);
        InputStream serverInStream = serverSocket.getInputStream();
        assertEquals(12, serverInStream.read());

        //send data from server to client
        OutputStream serverOutStream = serverSocket.getOutputStream();
        serverOutStream.write(3);
        InputStream clientInStream = clientSocket.getInputStream();
        assertEquals(3, clientInStream.read());

        // Test sending and receiving file descriptors
        clientSocket.setFileDescriptorsForSend(new FileDescriptor[]{FileDescriptor.in});
        clientOutStream.write(32);
        assertEquals(32, serverInStream.read());

        FileDescriptor[] out = serverSocket.getAncillaryFileDescriptors();
        assertEquals(1, out.length);
        FileDescriptor fd = clientSocket.getFileDescriptor();
        assertTrue(fd.valid());

        //shutdown input stream of client
        clientSocket.shutdownInput();
        assertEquals(-1, clientInStream.read());

        //shutdown output stream of client
        clientSocket.shutdownOutput();
        assertThrows(IOException.class, () -> {
            clientOutStream.write(10);
        });

        //shutdown input stream of server
        serverSocket.shutdownInput();
        assertEquals(-1, serverInStream.read());

        //shutdown output stream of server
        serverSocket.shutdownOutput();
        assertThrows(IOException.class, () -> {
            serverOutStream.write(10);
        });

        //close client socket
        clientSocket.close();
        assertThrows(IOException.class, () -> {
            clientInStream.read();
        });

        //close server socket
        serverSocket.close();
        assertThrows(IOException.class, () -> {
            serverInStream.read();
        });
    }

    @Test
    public void testAccessors() throws IOException {
        String address = ADDRESS_PREFIX + "_testAccessors";
        LocalSocket socket = new LocalSocket();
        LocalSocketAddress addr = new LocalSocketAddress(address);

        assertFalse(socket.isBound());
        socket.bind(addr);
        assertTrue(socket.isBound());
        assertEquals(addr, socket.getLocalSocketAddress());

        String str = socket.toString();
        assertTrue(str.contains("impl:android.net.LocalSocketImpl"));

        socket.setReceiveBufferSize(1999);
        assertEquals(1999 << 1, socket.getReceiveBufferSize());

        socket.setSendBufferSize(3998);
        assertEquals(3998 << 1, socket.getSendBufferSize());

        assertEquals(0, socket.getSoTimeout());
        socket.setSoTimeout(1996);
        assertTrue(socket.getSoTimeout() > 0);

        assertThrows(UnsupportedOperationException.class, () -> {
            socket.getRemoteSocketAddress();
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            socket.isClosed();
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            socket.isInputShutdown();
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            socket.isOutputShutdown();
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            socket.connect(addr, 2005);
        });

        socket.close();
    }

    // http://b/31205169
    @Test @IgnoreUpTo(SC_V2)  // Crashes on pre-T due to a JNI bug. See http://r.android.com/2096720
    public void testSetSoTimeout_readTimeout() throws Exception {
        String address = ADDRESS_PREFIX + "_testSetSoTimeout_readTimeout";

        try (LocalSocketPair socketPair = LocalSocketPair.createConnectedSocketPair(address)) {
            final LocalSocket clientSocket = socketPair.clientSocket;

            // Set the timeout in millis.
            int timeoutMillis = 1000;
            clientSocket.setSoTimeout(timeoutMillis);

            // Avoid blocking the test run if timeout doesn't happen by using a separate thread.
            Callable<Result> reader = () -> {
                try {
                    clientSocket.getInputStream().read();
                    return Result.noException("Did not block");
                } catch (IOException e) {
                    return Result.exception(e);
                }
            };
            // Allow the configured timeout, plus some slop.
            int allowedTime = timeoutMillis + 2000;
            Result result = runInSeparateThread(allowedTime, reader);

            // Check the message was a timeout, it's all we have to go on.
            String expectedMessage = Os.strerror(OsConstants.EAGAIN);
            result.assertThrewIOException(expectedMessage);
        }
    }

    // http://b/31205169
    @Test
    public void testSetSoTimeout_writeTimeout() throws Exception {
        String address = ADDRESS_PREFIX + "_testSetSoTimeout_writeTimeout";

        try (LocalSocketPair socketPair = LocalSocketPair.createConnectedSocketPair(address)) {
            final LocalSocket clientSocket = socketPair.clientSocket;

            // Set the timeout in millis.
            int timeoutMillis = 1000;
            clientSocket.setSoTimeout(timeoutMillis);

            // Set a small buffer size so we know we can flood it.
            clientSocket.setSendBufferSize(100);
            final int bufferSize = clientSocket.getSendBufferSize();

            // Avoid blocking the test run if timeout doesn't happen by using a separate thread.
            Callable<Result> writer = () -> {
                try {
                    byte[] toWrite = new byte[bufferSize * 2];
                    clientSocket.getOutputStream().write(toWrite);
                    return Result.noException("Did not block");
                } catch (IOException e) {
                    return Result.exception(e);
                }
            };
            // Allow the configured timeout, plus some slop.
            int allowedTime = timeoutMillis + 2000;

            Result result = runInSeparateThread(allowedTime, writer);

            // Check the message was a timeout, it's all we have to go on.
            String expectedMessage = Os.strerror(OsConstants.EAGAIN);
            result.assertThrewIOException(expectedMessage);
        }
    }

    @Test
    public void testAvailable() throws Exception {
        String address = ADDRESS_PREFIX + "_testAvailable";

        try (LocalSocketPair socketPair = LocalSocketPair.createConnectedSocketPair(address)) {
            LocalSocket clientSocket = socketPair.clientSocket;
            LocalSocket serverSocket = socketPair.serverSocket.accept();

            OutputStream clientOutputStream = clientSocket.getOutputStream();
            InputStream serverInputStream = serverSocket.getInputStream();
            assertEquals(0, serverInputStream.available());

            byte[] buffer = new byte[50];
            clientOutputStream.write(buffer);
            assertEquals(50, serverInputStream.available());

            InputStream clientInputStream = clientSocket.getInputStream();
            OutputStream serverOutputStream = serverSocket.getOutputStream();
            assertEquals(0, clientInputStream.available());
            serverOutputStream.write(buffer);
            assertEquals(50, serverInputStream.available());

            serverSocket.close();
        }
    }

    // http://b/34095140
    @Test @IgnoreUpTo(SC_V2)
    public void testLocalSocketCreatedFromFileDescriptor() throws Exception {
        String address = ADDRESS_PREFIX + "_testLocalSocketCreatedFromFileDescriptor";

        // Establish connection between a local client and server to get a valid client socket file
        // descriptor.
        try (LocalSocketPair socketPair = LocalSocketPair.createConnectedSocketPair(address)) {
            // Extract the client FileDescriptor we can use.
            FileDescriptor fileDescriptor = socketPair.clientSocket.getFileDescriptor();
            assertTrue(fileDescriptor.valid());

            // Create the LocalSocket we want to test.
            LocalSocket clientSocketCreatedFromFileDescriptor = new LocalSocket(fileDescriptor);
            assertTrue(clientSocketCreatedFromFileDescriptor.isConnected());
            assertTrue(clientSocketCreatedFromFileDescriptor.isBound());

            // Test the LocalSocket can be used for communication.
            LocalSocket serverSocket = socketPair.serverSocket.accept();
            OutputStream clientOutputStream =
                    clientSocketCreatedFromFileDescriptor.getOutputStream();
            InputStream serverInputStream = serverSocket.getInputStream();

            clientOutputStream.write(12);
            assertEquals(12, serverInputStream.read());

            // Closing clientSocketCreatedFromFileDescriptor does not close the file descriptor.
            clientSocketCreatedFromFileDescriptor.close();
            assertTrue(fileDescriptor.valid());

            // .. while closing the LocalSocket that owned the file descriptor does.
            socketPair.clientSocket.close();
            assertFalse(fileDescriptor.valid());
        }
    }

    @Test
    public void testFlush() throws Exception {
        String address = ADDRESS_PREFIX + "_testFlush";

        try (LocalSocketPair socketPair = LocalSocketPair.createConnectedSocketPair(address)) {
            LocalSocket clientSocket = socketPair.clientSocket;
            LocalSocket serverSocket = socketPair.serverSocket.accept();

            OutputStream clientOutputStream = clientSocket.getOutputStream();
            InputStream serverInputStream = serverSocket.getInputStream();
            testFlushWorks(clientOutputStream, serverInputStream);

            OutputStream serverOutputStream = serverSocket.getOutputStream();
            InputStream clientInputStream = clientSocket.getInputStream();
            testFlushWorks(serverOutputStream, clientInputStream);

            serverSocket.close();
        }
    }

    private void testFlushWorks(OutputStream outputStream, InputStream inputStream)
            throws Exception {
        final int bytesToTransfer = 50;
        StreamReader inputStreamReader = new StreamReader(inputStream, bytesToTransfer);

        byte[] buffer = new byte[bytesToTransfer];
        outputStream.write(buffer);
        assertEquals(bytesToTransfer, inputStream.available());

        // Start consuming the data.
        inputStreamReader.start();

        // This doesn't actually flush any buffers, it just polls until the reader has read all the
        // bytes.
        outputStream.flush();

        inputStreamReader.waitForCompletion(5000);
        inputStreamReader.assertBytesRead(bytesToTransfer);
        assertEquals(0, inputStream.available());
    }

    private void sendAndReceiveBytes(LocalSocket s1, LocalSocket s2) throws Exception {
        final Random random = new Random();
        final byte[] sendBytes = new byte[random.nextInt(511) + 1];  // Avoid 0-byte writes.
        random.nextBytes(sendBytes);
        final int numBytes = sendBytes.length;
        final OutputStream os = s1.getOutputStream();
        os.write(sendBytes);
        os.flush();

        final InputStream is = s2.getInputStream();
        final byte[] recvBytes = new byte[1024];
        assertEquals(numBytes, is.read(recvBytes, 0, recvBytes.length));

        final byte[] received = Arrays.copyOfRange(recvBytes, 0, numBytes);
        assertArrayEquals(received, sendBytes);
    }

    /**
     * Keeps track of the highest-numbered FD that is passed in.
     */
    private class MaxFdTracker{
        private int mMax = -1;

        public int get() {
            return mMax;
        }

        private void noteFd(int fd) {
            mMax = Math.max(mMax, fd);
        }

        public void noteFd(FileDescriptor fd) {
            noteFd(fd.getInt$());
        }

        public void noteFd(LocalSocket s) {
            noteFd(s.getFileDescriptor().getInt$());
        }
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testCreateFromFd() throws Exception {
        String address = ADDRESS_PREFIX + "_testClosingConnectedSocket";
        LocalServerSocket server = new LocalServerSocket(address);

        final int TIMEOUT_MS = 1000;

        final int NUM_ITERATIONS = 1000;
        int firstFd = -1;
        MaxFdTracker maxFd = new MaxFdTracker();

        for (int i = 0; i < NUM_ITERATIONS; i++) {
            FileDescriptor fd = Os.socket(OsConstants.AF_UNIX, OsConstants.SOCK_STREAM, 0);
            if (firstFd == -1) {
                firstFd = fd.getInt$();
            } else  {
                maxFd.noteFd(fd);
            }

            // Ensure the test doesn't hang by setting a reasonably short timeout.
            // This seems easier than polling on non-blocking socket.
            Os.setsockoptTimeval(fd, OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO,
                    StructTimeval.fromMillis(TIMEOUT_MS));
            Os.setsockoptTimeval(fd, OsConstants.SOL_SOCKET, OsConstants.SO_SNDTIMEO,
                    StructTimeval.fromMillis(TIMEOUT_MS));

            final SocketAddress sockAddr = Os.getsockname(server.getFileDescriptor());
            Os.connect(fd, sockAddr);

            LocalSocket accepted = server.accept();
            accepted.setSoTimeout(TIMEOUT_MS);
            maxFd.noteFd(accepted);

            LocalSocket ls = new LocalSocket(fd);
            assertEquals(ls.getFileDescriptor().getInt$(), fd.getInt$());
            maxFd.noteFd(ls);

            sendAndReceiveBytes(accepted, ls);
            sendAndReceiveBytes(ls, accepted);

            accepted.close();
            assertNull(accepted.getFileDescriptor());
            Os.close(fd);
        }
        server.close();

        assertTrue("No FDs created!", firstFd != -1);
        assertTrue("Only one FD created?", maxFd.get() != -1);
        int fdsConsumed = maxFd.get() - firstFd;
        assertTrue(
                "FD leak! Opened " + NUM_ITERATIONS + " sockets, FD int went up by " + fdsConsumed,
            fdsConsumed < NUM_ITERATIONS / 2);
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testCreateFromFd_notConnected() throws Exception {
        FileDescriptor fd = Os.socket(OsConstants.AF_UNIX, OsConstants.SOCK_STREAM, 0);
        assertThrows(IllegalArgumentException.class, () -> {
            LocalSocket ls = new LocalSocket(fd);
        });
    }

    @Test @IgnoreUpTo(SC_V2)
    public void testCreateFromFd_notSocket() throws Exception {
        FileDescriptor fd = Os.open("/dev/null", 0 /* flags */, OsConstants.O_WRONLY);
        assertThrows(IllegalArgumentException.class, () -> {
            LocalSocket ls = new LocalSocket(fd);
        });
    }

    private static class StreamReader extends Thread {
        private final InputStream is;
        private final int expectedByteCount;
        private final CountDownLatch completeLatch = new CountDownLatch(1);

        private volatile Exception exception;
        private int bytesRead;

        private StreamReader(InputStream is, int expectedByteCount) {
            this.is = is;
            this.expectedByteCount = expectedByteCount;
        }

        @Override
        public void run() {
            try {
                byte[] buffer = new byte[10];
                int readCount;
                while ((readCount = is.read(buffer)) >= 0) {
                    bytesRead += readCount;
                    if (bytesRead >= expectedByteCount) {
                        break;
                    }
                }
            } catch (IOException e) {
                exception = e;
            } finally {
                completeLatch.countDown();
            }
        }

        public void waitForCompletion(long waitMillis) throws Exception {
            if (!completeLatch.await(waitMillis, TimeUnit.MILLISECONDS)) {
                fail("Timeout waiting for completion");
            }
            if (exception != null) {
                throw new Exception("Read failed", exception);
            }
        }

        public void assertBytesRead(int expected) {
            assertEquals(expected, bytesRead);
        }
    }

    private static class Result {
        private final String type;
        private final Exception e;

        private Result(String type, Exception e) {
            this.type = type;
            this.e = e;
        }

        static Result noException(String description) {
            return new Result(description, null);
        }

        static Result exception(Exception e) {
            return new Result(e.getClass().getName(), e);
        }

        void assertThrewIOException(String expectedMessage) {
            assertEquals("Unexpected result type", IOException.class.getName(), type);
            assertEquals("Unexpected exception message", expectedMessage, e.getMessage());
        }
    }

    private static Result runInSeparateThread(int allowedTime, final Callable<Result> callable)
            throws Exception {
        ExecutorService service = Executors.newSingleThreadScheduledExecutor();
        Future<Result> future = service.submit(callable);
        Result result = future.get(allowedTime, TimeUnit.MILLISECONDS);
        if (!future.isDone()) {
            fail("Worker thread appears blocked");
        }
        return result;
    }

    private static class LocalSocketPair implements AutoCloseable {
        static LocalSocketPair createConnectedSocketPair(String address) throws Exception {
            LocalServerSocket localServerSocket = new LocalServerSocket(address);
            final LocalSocket clientSocket = new LocalSocket();

            // Establish connection between client and server
            LocalSocketAddress locSockAddr = new LocalSocketAddress(address);
            clientSocket.connect(locSockAddr);
            assertTrue(clientSocket.isConnected());
            return new LocalSocketPair(localServerSocket, clientSocket);
        }

        final LocalServerSocket serverSocket;
        final LocalSocket clientSocket;

        LocalSocketPair(LocalServerSocket serverSocket, LocalSocket clientSocket) {
            this.serverSocket = serverSocket;
            this.clientSocket = clientSocket;
        }

        public void close() throws Exception {
            serverSocket.close();
            clientSocket.close();
        }
    }
}
