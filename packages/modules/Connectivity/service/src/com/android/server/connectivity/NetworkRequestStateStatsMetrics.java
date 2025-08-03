/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.connectivity;

import static com.android.server.ConnectivityStatsLog.NETWORK_REQUEST_STATE_CHANGED;

import android.annotation.NonNull;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.ConnectivityStatsLog;

import java.util.ArrayDeque;

/**
 * A Connectivity Service helper class to push atoms capturing network requests have been received
 * and removed and its metadata.
 *
 * Atom events are logged in the ConnectivityStatsLog. Network request id: network request metadata
 * hashmap is stored to calculate network request duration when it is removed.
 *
 * Note that this class is not thread-safe. The instance of the class needs to be
 * synchronized in the callers when being used in multiple threads.
 */
public class NetworkRequestStateStatsMetrics {

    private static final String TAG = "NetworkRequestStateStatsMetrics";
    private static final int CMD_SEND_PENDING_NETWORK_REQUEST_STATE_METRIC = 0;
    private static final int CMD_SEND_MAYBE_ENQUEUE_NETWORK_REQUEST_STATE_METRIC = 1;

    @VisibleForTesting
    static final int MAX_QUEUED_REQUESTS = 20;

    // Stats logging frequency is limited to 10 ms at least, 500ms are taken as a safely margin
    // for cases of longer periods of frequent network requests.
    private static final int ATOM_INTERVAL_MS = 500;
    private final StatsLoggingHandler mStatsLoggingHandler;

    private final Dependencies mDependencies;

    private final NetworkRequestStateInfo.Dependencies mNRStateInfoDeps;
    private final SparseArray<NetworkRequestStateInfo> mNetworkRequestsActive;

    public NetworkRequestStateStatsMetrics() {
        this(new Dependencies(), new NetworkRequestStateInfo.Dependencies());
    }

    @VisibleForTesting
    NetworkRequestStateStatsMetrics(Dependencies deps,
            NetworkRequestStateInfo.Dependencies nrStateInfoDeps) {
        mNetworkRequestsActive = new SparseArray<>();
        mDependencies = deps;
        mNRStateInfoDeps = nrStateInfoDeps;
        HandlerThread handlerThread = mDependencies.makeHandlerThread(TAG);
        handlerThread.start();
        mStatsLoggingHandler = new StatsLoggingHandler(handlerThread.getLooper());
    }

    /**
     * Register network request receive event, push RECEIVE atom
     *
     * @param networkRequest network request received
     */
    public void onNetworkRequestReceived(NetworkRequest networkRequest) {
        if (mNetworkRequestsActive.contains(networkRequest.requestId)) {
            Log.w(TAG, "Received already registered network request, id = "
                    + networkRequest.requestId);
        } else {
            Log.d(TAG, "Registered nr with ID = " + networkRequest.requestId
                    + ", package_uid = " + networkRequest.networkCapabilities.getRequestorUid());
            NetworkRequestStateInfo networkRequestStateInfo = new NetworkRequestStateInfo(
                    networkRequest, mNRStateInfoDeps);
            mNetworkRequestsActive.put(networkRequest.requestId, networkRequestStateInfo);
            mStatsLoggingHandler.sendMessage(Message.obtain(
                    mStatsLoggingHandler,
                    CMD_SEND_MAYBE_ENQUEUE_NETWORK_REQUEST_STATE_METRIC,
                    networkRequestStateInfo));
        }
    }

    /**
     * Register network request remove event, push REMOVE atom
     *
     * @param networkRequest network request removed
     */
    public void onNetworkRequestRemoved(NetworkRequest networkRequest) {
        NetworkRequestStateInfo networkRequestStateInfo = mNetworkRequestsActive.get(
                networkRequest.requestId);
        if (networkRequestStateInfo == null) {
            Log.w(TAG, "This NR hasn't been registered. NR id = " + networkRequest.requestId);
        } else {
            Log.d(TAG, "Removed nr with ID = " + networkRequest.requestId);
            mNetworkRequestsActive.remove(networkRequest.requestId);
            networkRequestStateInfo = new NetworkRequestStateInfo(networkRequestStateInfo);
            networkRequestStateInfo.setNetworkRequestRemoved();
            mStatsLoggingHandler.sendMessage(Message.obtain(
                    mStatsLoggingHandler,
                    CMD_SEND_MAYBE_ENQUEUE_NETWORK_REQUEST_STATE_METRIC,
                    networkRequestStateInfo));
        }
    }

    /** Dependency class */
    public static class Dependencies {
        /**
         * Creates a thread with provided tag.
         *
         * @param tag for the thread.
         */
        public HandlerThread makeHandlerThread(@NonNull final String tag) {
            return new HandlerThread(tag);
        }

        /**
         * @see Handler#sendMessageDelayed(Message, long)
         */
        public void sendMessageDelayed(@NonNull Handler handler, int what, long delayMillis) {
            handler.sendMessageDelayed(Message.obtain(handler, what), delayMillis);
        }

        /**
         * Gets number of millis since event.
         *
         * @param eventTimeMillis long timestamp in millis when the event occurred.
         */
        public long getMillisSinceEvent(long eventTimeMillis) {
            return SystemClock.elapsedRealtime() - eventTimeMillis;
        }

        /**
         * Writes a NETWORK_REQUEST_STATE_CHANGED event to ConnectivityStatsLog.
         *
         * @param networkRequestStateInfo NetworkRequestStateInfo containing network request info.
         */
        public void writeStats(NetworkRequestStateInfo networkRequestStateInfo) {
            ConnectivityStatsLog.write(
                    NETWORK_REQUEST_STATE_CHANGED,
                    networkRequestStateInfo.getPackageUid(),
                    networkRequestStateInfo.getTransportTypes(),
                    networkRequestStateInfo.getNetCapabilityNotMetered(),
                    networkRequestStateInfo.getNetCapabilityInternet(),
                    networkRequestStateInfo.getNetworkRequestStateStatsType(),
                    networkRequestStateInfo.getNetworkRequestDurationMillis());
        }
    }

    private class StatsLoggingHandler extends Handler {
        private static final String TAG = "NetworkRequestsStateStatsLoggingHandler";

        private final ArrayDeque<NetworkRequestStateInfo> mPendingState = new ArrayDeque<>();

        private long mLastLogTime = 0;

        StatsLoggingHandler(Looper looper) {
            super(looper);
        }

        private void maybeEnqueueStatsMessage(NetworkRequestStateInfo networkRequestStateInfo) {
            if (mPendingState.size() < MAX_QUEUED_REQUESTS) {
                mPendingState.add(networkRequestStateInfo);
            } else {
                Log.w(TAG, "Too many network requests received within last " + ATOM_INTERVAL_MS
                        + " ms, dropping the last network request (id = "
                        + networkRequestStateInfo.getRequestId() + ") event");
                return;
            }
            if (hasMessages(CMD_SEND_PENDING_NETWORK_REQUEST_STATE_METRIC)) {
                return;
            }
            long millisSinceLastLog = mDependencies.getMillisSinceEvent(mLastLogTime);

            if (millisSinceLastLog >= ATOM_INTERVAL_MS) {
                sendMessage(
                        Message.obtain(this, CMD_SEND_PENDING_NETWORK_REQUEST_STATE_METRIC));
            } else {
                mDependencies.sendMessageDelayed(
                        this,
                        CMD_SEND_PENDING_NETWORK_REQUEST_STATE_METRIC,
                        ATOM_INTERVAL_MS - millisSinceLastLog);
            }
        }

        @Override
        public void handleMessage(Message msg) {
            NetworkRequestStateInfo loggingInfo;
            switch (msg.what) {
                case CMD_SEND_MAYBE_ENQUEUE_NETWORK_REQUEST_STATE_METRIC:
                    maybeEnqueueStatsMessage((NetworkRequestStateInfo) msg.obj);
                    break;
                case CMD_SEND_PENDING_NETWORK_REQUEST_STATE_METRIC:
                    mLastLogTime = SystemClock.elapsedRealtime();
                    if (!mPendingState.isEmpty()) {
                        loggingInfo = mPendingState.remove();
                        mDependencies.writeStats(loggingInfo);
                        if (!mPendingState.isEmpty()) {
                            mDependencies.sendMessageDelayed(
                                    this,
                                    CMD_SEND_PENDING_NETWORK_REQUEST_STATE_METRIC,
                                    ATOM_INTERVAL_MS);
                        }
                    }
                    break;
                default: // fall out
            }
        }
    }
}
