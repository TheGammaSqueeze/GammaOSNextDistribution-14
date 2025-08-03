/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.car.internal;

import android.annotation.Nullable;
import android.car.builtin.os.HandlerHelper;
import android.car.builtin.util.Slogf;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.LongSparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/**
 * A pending request pool with timeout. The request uses {@code long} request ID.
 *
 * Note that this pool is not thread-safe. Caller must use lock to guard this object.
 *
 * @param <T> The request info type.
 *
 * @hide
 */
public final class LongPendingRequestPool<T extends LongRequestIdWithTimeout> {
    private static final String TAG = LongPendingRequestPool.class.getSimpleName();
    private static final int REQUESTS_TIMEOUT_MESSAGE_TYPE = 1;

    private final TimeoutCallback mTimeoutCallback;
    private final Handler mTimeoutHandler;
    private final Object mRequestIdsLock = new Object();
    // A map to store system uptime in ms to the request IDs that will timeout at this time.
    @GuardedBy("mRequestIdsLock")
    private final LongSparseArray<List<Long>> mTimeoutUptimeMsToRequestIds =
            new LongSparseArray<>();
    private final LongSparseArray<T> mRequestIdToRequestInfo = new LongSparseArray<>();

    private class MessageHandler implements Handler.Callback {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what != REQUESTS_TIMEOUT_MESSAGE_TYPE) {
                Slogf.e(TAG, "Received unexpected msg with what: %d", msg.what);
                return true;
            }
            List<Long> requestIdsCopy;
            synchronized (mRequestIdsLock) {
                Long timeoutUptimeMs = (Long) msg.obj;
                List<Long> requestIds = mTimeoutUptimeMsToRequestIds.get(timeoutUptimeMs);
                if (requestIds == null) {
                    Slogf.d(TAG, "handle a timeout msg, but all requests that should timeout "
                            + "has been removed");
                    return true;
                }
                requestIdsCopy = new ArrayList<>(requestIds);
                mTimeoutUptimeMsToRequestIds.remove(timeoutUptimeMs);
            }
            mTimeoutCallback.onRequestsTimeout(requestIdsCopy);
            return true;
        }
    }

    /**
     * Interface for timeout callback.
     */
    public interface TimeoutCallback {
        /**
         * Callback called when requests timeout.
         *
         * Note that caller must obtain lock to the request pool, call
         * {@link PendingRequestPool#getRequestIfFound} to check the request has not been removed,
         * and call {@link PendingRequestPool#removeRequest}, then release the lock.
         *
         * Similarly when a request is finished normally, caller should follow the same process.
         */
        void onRequestsTimeout(List<Long> requestIds);
    }

    public LongPendingRequestPool(Looper looper, TimeoutCallback callback) {
        mTimeoutCallback = callback;
        mTimeoutHandler = new Handler(looper, new MessageHandler());
    }

    /**
     * Get the number of pending requests.
     */
    public int size() {
        return mRequestIdToRequestInfo.size();
    }

    /**
     * Get the pending request ID at the specific index.
     */
    public long keyAt(int index) {
        return mRequestIdToRequestInfo.keyAt(index);
    }

    /**
     * Get the pending request info at the specific index.
     */
    public T valueAt(int index) {
        return mRequestIdToRequestInfo.valueAt(index);
    }

    /**
     * Add a list of new pending requests to the pool.
     *
     * If the request timeout before being removed from the pool, the timeout callback will be
     * called. The caller must remove the request from the pool during the callback.
     */
    public void addPendingRequests(List<T> requestsInfo) {
        LongSparseArray<Message> messagesToSend = new LongSparseArray<>();
        synchronized (mRequestIdsLock) {
            for (T requestInfo : requestsInfo) {
                long requestId = requestInfo.getRequestId();
                long timeoutUptimeMs = requestInfo.getTimeoutUptimeMs();
                mRequestIdToRequestInfo.put(requestId, requestInfo);
                if (mTimeoutUptimeMsToRequestIds.get(timeoutUptimeMs) == null) {
                    mTimeoutUptimeMsToRequestIds.put(timeoutUptimeMs, new ArrayList<>());
                }
                List<Long> requestIds = mTimeoutUptimeMsToRequestIds.get(timeoutUptimeMs);
                requestIds.add(requestId);

                if (requestIds.size() == 1) {
                    // This is the first time we register a timeout handler for the specific
                    // {@code timeoutUptimeMs}.
                    Message message = new Message();
                    message.what = REQUESTS_TIMEOUT_MESSAGE_TYPE;
                    message.obj = Long.valueOf(timeoutUptimeMs);
                    messagesToSend.put(timeoutUptimeMs, message);
                }
            }
        }
        for (int i = 0; i < messagesToSend.size(); i++) {
            mTimeoutHandler.sendMessageAtTime(messagesToSend.valueAt(i), messagesToSend.keyAt(i));
        }
    }

    /**
     * Get the pending request info for the specific request ID if found.
     */
    public @Nullable T getRequestIfFound(long requestId) {
        return mRequestIdToRequestInfo.get(requestId);
    }

    /**
     * Remove the pending request and the timeout handler for it.
     */
    public void removeRequest(long requestId) {
        synchronized (mRequestIdsLock) {
            T requestInfo = mRequestIdToRequestInfo.get(requestId);
            if (requestInfo == null) {
                Slogf.w(TAG, "No pending requests for request ID: %d", requestId);
                return;
            }
            mRequestIdToRequestInfo.remove(requestId);
            long timeoutUptimeMs = requestInfo.getTimeoutUptimeMs();
            List<Long> requestIds = mTimeoutUptimeMsToRequestIds.get(timeoutUptimeMs);
            if (requestIds == null) {
                Slogf.w(TAG, "No pending request that will timeout at: %d ms", timeoutUptimeMs);
                return;
            }
            if (!requestIds.remove(Long.valueOf(requestId))) {
                Slogf.w(TAG, "No pending request for request ID: %d, timeout at: %d ms",
                        requestId, timeoutUptimeMs);
                return;
            }
            if (requestIds.isEmpty()) {
                mTimeoutUptimeMsToRequestIds.remove(timeoutUptimeMs);
                HandlerHelper.removeEqualMessages(mTimeoutHandler, REQUESTS_TIMEOUT_MESSAGE_TYPE,
                        Long.valueOf(timeoutUptimeMs));
            }
        }
    }

    /**
     * Returns whether the pool is empty and all stored data are cleared.
     */
    @VisibleForTesting
    public boolean isEmpty() {
        synchronized (mRequestIdsLock) {
            return mTimeoutUptimeMsToRequestIds.size() == 0 && mRequestIdToRequestInfo.size() == 0;
        }
    }
}
