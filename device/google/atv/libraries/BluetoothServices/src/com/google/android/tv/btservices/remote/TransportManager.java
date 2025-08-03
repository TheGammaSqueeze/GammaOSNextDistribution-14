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
 * limitations under the License.
 */

package com.google.android.tv.btservices.remote;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Gatt-based communication between ATV and the remote.
 *
 * <p>Requests to the remote are placed into a queue and processed in queue order. Some requests are
 * expected to have responses (for example requesting battery level and receiving the level).
 * Gatt connections are terminated when all requests have been sent.
 * We also set a timeout - if requests are not sent within a certain window, then we deem those as
 * failed requests.
 */
public class TransportManager {

    private static final String TAG = "Atv.TrnsprtManager";
    private static final boolean DEBUG = false;

    private static final int MSG_SEND_GATT_MESSAGES = 11;
    private static final int SEND_GATT_DELAY_MS = 0;
    private static final int LONG_SEND_GATT_DELAY_MS = 5;
    private static final int HISTORY_SIZE = 10;

    private static final int MSG_GATT_TIMEOUT = 12;
    private static final int LONG_GATT_TIMEOUT_MS = 10000;
    private static final int SHORT_GATT_TIMEOUT_MS = 7000;

    private static final int MSG_TRANSPORT_TIMEOUT = 13;
    private static final int TRANSPORT_TIMEOUT_MS = 20000;

    private static final int WRITE_PACKET_TIMEOUT_MS = 1000;

    public static final int GATT_CONNECTED = 101;
    public static final int GATT_DISCONNECTED = 102;

    public static final Transport.Result RESULT_SUCCESS =
            new Transport.Result(null, Transport.Result.SUCCESS);
    public static final Transport.Result RESULT_FAILURE =
            new Transport.Result(null, Transport.Result.FAILURE);
    public static final Transport.Result RESULT_FAILURE_GATT_DISCONNECTED =
            new Transport.Result(null, Transport.Result.FAILURE_GATT_DISCONNECTED);
    public static final Transport.Result RESULT_FAILURE_LOCKED =
            new Transport.Result(null, Transport.Result.FAILURE_LOCKED);
    public static final Transport.Result RESULT_FAILURE_TIMED_OUT =
            new Transport.Result(null, Transport.Result.FAILURE_TIMED_OUT);

    interface GattStateListener {
        void onGattDisconnected();
        void onGattConnected();
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_SEND_GATT_MESSAGES:
                    processQueueImpl();
                    break;
                case MSG_GATT_TIMEOUT:
                    if (DEBUG) {
                        Log.d(TAG, "gatt timed out");
                    }
                    if (mQueue.isEmpty()) {
                        shutdownImpl();
                    } else {
                        processQueueAfterDelay(0);
                    }
                    break;
                case MSG_TRANSPORT_TIMEOUT:
                    Log.w(TAG, "transport has timed out after " + TRANSPORT_TIMEOUT_MS + "ms");
                    shutdownImpl();
                    break;
            }
        }
    };

    private final Context mContext;
    private final BluetoothDevice mDevice;
    private final Transport.Factory mFactory;

    private Packet mPacketToBeProcessed = null;
    private final ArrayDeque<Pair<Packet, CompletableFuture<Transport.Result>>> mQueue =
            new ArrayDeque<>();
    private HashMap<Packet, CompletableFuture<Transport.Result>> mPendingResults = new HashMap<>();
    private String mLockId;
    private List<GattStateListener> mGattStateListeners = new ArrayList<>();
    private Transport mTransport;

    public TransportManager(Context context, BluetoothDevice device, Transport.Factory factory) {
        mDevice = device;
        mContext = context;
        mFactory = factory;
    }

    private void setGattTimeOut(int timeoutMs) {
        mHandler.removeMessages(MSG_GATT_TIMEOUT);
        mHandler.sendEmptyMessageDelayed(MSG_GATT_TIMEOUT, timeoutMs);
    }

    public void shutdown() {
        mHandler.post(this::shutdownImpl);
    }

    private void shutdownImpl() {
        Log.i(TAG, "shutdown: " + mTransport);
        mHandler.removeCallbacksAndMessages(null);

        mTransport.shutdown();
        mTransport = null;

        for (CompletableFuture<Transport.Result> result : mPendingResults.values()) {
            result.complete(RESULT_FAILURE_GATT_DISCONNECTED);
        }
        for (Pair<Packet, CompletableFuture<Transport.Result>> item : mQueue) {
            item.second.complete(RESULT_FAILURE_TIMED_OUT);
        }
        mPendingResults.clear();
        mQueue.clear();
        mPacketToBeProcessed = null;
    }

    public void addGattStateListener(GattStateListener listener) {
        mGattStateListeners.add(listener);
    }

    public void removeGattStateListener(GattStateListener listener) {
        mGattStateListeners.remove(listener);
    }

    private void processQueueAfterDelay(int delay) {
        if (mHandler.hasMessages(MSG_SEND_GATT_MESSAGES)) {
            return;
        }
        mHandler.sendEmptyMessageDelayed(MSG_SEND_GATT_MESSAGES, delay);
    }

    private static long getTimestamp() {
        return SystemClock.elapsedRealtime();
    }

    // Should only be called on the handler thread
    private void processQueueImpl() {
        if (mQueue.isEmpty()) {
            // No more in the queue. Timeout soon.
            setGattTimeOut(SHORT_GATT_TIMEOUT_MS);
            return;
        }
        // We're still processing items in the queue, extend the timeout.
        setGattTimeOut(LONG_GATT_TIMEOUT_MS);

        // There's a packet pending. Just wait until next time.
        if (mPacketToBeProcessed != null) {
            long currentTime = getTimestamp();
            if (currentTime - mPacketToBeProcessed.getTimestamp() < WRITE_PACKET_TIMEOUT_MS) {
                processQueueAfterDelay(LONG_SEND_GATT_DELAY_MS);
            } else {
                // dropping packet due to timeout.
                Log.w(TAG, "timeout packet: " + TransportUtils.byteToString(
                                                        mPacketToBeProcessed.getRequestType()));
                mPendingResults.remove(mPacketToBeProcessed);
                mPacketToBeProcessed = null;
                processQueueAfterDelay(0);
            }
            return;
        }

        if (mTransport == null) {
            mTransport = mFactory.build(mDevice, this::processQueueImpl, mHandler, mContext, this);
            return;
        }

        if (!mTransport.ready()) {
            Log.e(TAG, "processQueueImpl: transport not ready");
            if (!mHandler.hasMessages(MSG_TRANSPORT_TIMEOUT)) {
                mHandler.sendEmptyMessageDelayed(MSG_TRANSPORT_TIMEOUT, TRANSPORT_TIMEOUT_MS);
            }
            return;
        } else {
            mHandler.removeMessages(MSG_TRANSPORT_TIMEOUT);
        }

        Pair<Packet, CompletableFuture<Transport.Result>> pending = mQueue.pop();
        mPacketToBeProcessed = pending.first;
        CompletableFuture<Transport.Result> result = pending.second;
        boolean transportOk = mPacketToBeProcessed.transportPacket(mTransport);
        if (transportOk) {
            mPendingResults.put(mPacketToBeProcessed, result);
        } else {
            result.complete(RESULT_FAILURE);
            mPacketToBeProcessed = null;
        }
        processQueueAfterDelay(SEND_GATT_DELAY_MS);
    }

    // Called by Transport.
    void onGattStateChanged(int state) {
        mHandler.post(() -> onGattStateChangedImpl(state));
    }

    private void onGattStateChangedImpl(int state) {
        switch (state) {
            case GATT_CONNECTED:
                mGattStateListeners.forEach(GattStateListener::onGattConnected);
                break;
            case GATT_DISCONNECTED:
                Log.e(TAG, "gatt disconnected " + mDevice);
                shutdown();
                mGattStateListeners.forEach(GattStateListener::onGattDisconnected);
                break;
        }
    }

    // Called by Transport.
    void onWritten(int status, byte[] bytes) {
        mHandler.post(() -> { onWrittenImpl(status, bytes); });
    }

    private void onWrittenImpl(int status, byte[] bytes) {
        if (mPacketToBeProcessed == null) {
            Log.e(TAG, "onWritten: unexpectedly no pending packet");
            return;
        }
        final boolean success = status == BluetoothGatt.GATT_SUCCESS;
        if (!mPacketToBeProcessed.waitForResponse()) {
            CompletableFuture<Transport.Result> result = mPendingResults.get(mPacketToBeProcessed);
            if (result != null) {
                result.complete(new Transport.Result(
                        null, success ? Transport.Result.SUCCESS : Transport.Result.FAILURE));
                mPendingResults.remove(mPacketToBeProcessed);
            }
            mPacketToBeProcessed = null;
        }
    }

    // Called by Transport.
    void onResponse(Transport transport, byte status, byte[] bytes) {
        mHandler.post(() -> { onResponseImpl(transport, status, bytes); });
    }

    private void onResponseImpl(Transport transport, byte status, byte[] bytes) {
        Packet foundPacket = null;
        for (Packet packet: mPendingResults.keySet()) {
            Byte expected = transport.getExpectedResponse(packet.getRequestType());
            if (expected != null && expected.byteValue() == status) {
                foundPacket = packet;
                break;
            }
        }
        if (foundPacket == null) {
            Log.w(TAG, "A response came without request: " + TransportUtils.byteToString(status));
            return;
        }

        if (bytes == null) {
            Log.e(TAG, "onResponse: did not expect null packets");
        }

        CompletableFuture<Transport.Result> result = mPendingResults.get(foundPacket);
        result.complete(new Transport.Result(bytes));

        if (foundPacket.waitForResponse()) {
            if (mPacketToBeProcessed == null) {
                Log.w(TAG, "did not expect packetToBeWritten is null");
            }
            if (mPacketToBeProcessed == foundPacket) {
                mPacketToBeProcessed = null;
            } else {
                Log.w(TAG, "did not expect packetToBeWritten differs from found packet");
            }
        }
        mPendingResults.remove(foundPacket);
    }

    // Called by Transport.
    void onInitCharacteristics(boolean success, Runnable pendingTask) {
        if (!success) {
            Log.w(TAG, "failed in initCharacteristics");
        } else {
            setGattTimeOut(LONG_GATT_TIMEOUT_MS);
            if (pendingTask != null) {
                mHandler.post(pendingTask);
            }
        }
    }

    public void unlock(String lockId) {
        if (TextUtils.equals(lockId, mLockId)) {
            mLockId = null;
        }
    }

    private boolean locked(String lockId) {
        return mLockId != null && !TextUtils.equals(lockId, mLockId);
    }

    public CompletableFuture<Transport.Result> handlePacket(Packet packet, String lockId) {
        if (locked(lockId)) {
            Log.w(TAG, "handlePacket: failed because locked by " + mLockId);
            return CompletableFuture.completedFuture(RESULT_FAILURE_LOCKED);
        }

        if (lockId != null && mLockId == null) {
            mLockId = lockId;
        }

        CompletableFuture<Transport.Result> ret = new CompletableFuture<>();
        mHandler.post(() -> {
            mQueue.push(new Pair<>(packet, ret));
            processQueueAfterDelay(0);
        });
        return ret;
    }
}
