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

package android.app.time.cts;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;

/**
 * Simple Sntp Server implementation for testing purpose.
 * This is copied from {@code SntpClientTest}.
 */
class SntpTestServer {
    private static final String TAG = SntpTestServer.class.getSimpleName();
    private static final int ORIGINATE_TIME_OFFSET = 24;
    private static final int TRANSMIT_TIME_OFFSET = 40;

    private final Object mLock = new Object();
    private final DatagramSocket mSocket;
    private final InetAddress mAddress;
    private final int mPort;
    private byte[] mReply;
    private boolean mGenerateValidOriginateTimestamp = true;
    private int mRcvd;
    private int mSent;
    private final Thread mListeningThread;

    SntpTestServer() {
        mSocket = makeSocket();
        mAddress = mSocket.getLocalAddress();
        mPort = mSocket.getLocalPort();
        Log.d(TAG, "testing server listening on (" + mAddress + ", " + mPort + ")");

        mListeningThread = new Thread() {
            public void run() {
                while (true) {
                    byte[] buffer = new byte[512];
                    DatagramPacket ntpMsg = new DatagramPacket(buffer, buffer.length);
                    try {
                        mSocket.receive(ntpMsg);
                    } catch (IOException e) {
                        Log.e(TAG, "datagram receive error: " + e);
                        break;
                    }
                    synchronized (mLock) {
                        mRcvd++;
                        if (mReply == null) {
                            continue;
                        }
                        if (mGenerateValidOriginateTimestamp) {
                            // Copy the transmit timestamp into originate timestamp: This is
                            // validated by well-behaved clients.
                            System.arraycopy(ntpMsg.getData(), TRANSMIT_TIME_OFFSET,
                                    mReply, ORIGINATE_TIME_OFFSET, 8);
                        } else {
                            // Fill it with junk instead.
                            Arrays.fill(mReply, ORIGINATE_TIME_OFFSET,
                                    ORIGINATE_TIME_OFFSET + 8, (byte) 0xFF);
                        }
                        ntpMsg.setData(mReply);
                        ntpMsg.setLength(mReply.length);
                        try {
                            mSocket.send(ntpMsg);
                        } catch (IOException e) {
                            Log.e(TAG, "datagram send error: " + e);
                            break;
                        }
                        mSent++;
                    }
                }
                mSocket.close();
            }
        };
        mListeningThread.start();
    }

    private DatagramSocket makeSocket() {
        DatagramSocket socket;
        try {
            socket = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        } catch (SocketException e) {
            Log.e(TAG, "Failed to create test server socket: " + e);
            throw new RuntimeException("Failed to create test server socket", e);
        }
        return socket;
    }

    public void clearServerReply() {
        setServerReply(null);
    }

    public void setServerReply(byte[] reply) {
        synchronized (mLock) {
            mReply = reply;
            mRcvd = 0;
            mSent = 0;
        }
    }

    /**
     * Controls the test server's behavior of copying the client's transmit timestamp into the
     * response's originate timestamp (which is required of a real server).
     */
    public void setGenerateValidOriginateTimestamp(boolean enabled) {
        synchronized (mLock) {
            mGenerateValidOriginateTimestamp = enabled;
        }
    }

    public InetAddress getAddress() {
        return mAddress;
    }

    public int getPort() {
        return mPort;
    }

    public int numRequestsReceived() {
        synchronized (mLock) {
            return mRcvd;
        }
    }

    public int numRepliesSent() {
        synchronized (mLock) {
            return mSent;
        }
    }
}
