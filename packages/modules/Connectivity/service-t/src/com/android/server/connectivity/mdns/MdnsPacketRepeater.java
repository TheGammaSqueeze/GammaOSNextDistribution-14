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

package com.android.server.connectivity.mdns;

import static com.android.server.connectivity.mdns.MdnsConstants.IPV4_SOCKET_ADDR;
import static com.android.server.connectivity.mdns.MdnsConstants.IPV6_SOCKET_ADDR;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresApi;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.android.net.module.util.SharedLog;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * A class used to send several packets at given time intervals.
 * @param <T> The type of the request providing packet repeating parameters.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public abstract class MdnsPacketRepeater<T extends MdnsPacketRepeater.Request> {
    private static final InetSocketAddress[] ALL_ADDRS = new InetSocketAddress[] {
            IPV4_SOCKET_ADDR, IPV6_SOCKET_ADDR
    };

    @NonNull
    private final MdnsReplySender mReplySender;
    @NonNull
    protected final Handler mHandler;
    @Nullable
    private final PacketRepeaterCallback<T> mCb;
    @NonNull
    private final SharedLog mSharedLog;
    private final boolean mEnableDebugLog;

    /**
     * Status callback from {@link MdnsPacketRepeater}.
     *
     * Callbacks are called on the {@link MdnsPacketRepeater} handler thread.
     * @param <T> The type of the request providing packet repeating parameters.
     */
    public interface PacketRepeaterCallback<T extends MdnsPacketRepeater.Request> {
        /**
         * Called when a packet was sent.
         */
        default void onSent(int index, @NonNull T info, int sentPacketCount) {}

        /**
         * Called when the {@link MdnsPacketRepeater} is done sending packets.
         */
        default void onFinished(@NonNull T info) {}
    }

    /**
     * A request to repeat packets.
     *
     * All methods are called in the looper thread.
     */
    public interface Request {
        /**
         * Get a packet to send for one iteration.
         */
        @NonNull
        MdnsPacket getPacket(int index);

        /**
         * Get the delay in milliseconds until the next packet transmission.
         */
        long getDelayMs(int nextIndex);

        /**
         * Get the number of packets that should be sent.
         */
        int getNumSends();
    }

    private final class ProbeHandler extends Handler {
        ProbeHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            final int index = msg.arg1;
            final T request = (T) msg.obj;

            if (index >= request.getNumSends()) {
                if (mCb != null) {
                    mCb.onFinished(request);
                }
                return;
            }

            final MdnsPacket packet = request.getPacket(index);
            if (mEnableDebugLog) {
                mSharedLog.v("Sending packets for iteration " + index + " out of "
                        + request.getNumSends() + " for ID " + msg.what);
            }
            // Send to both v4 and v6 addresses; the reply sender will take care of ignoring the
            // send when the socket has not joined the relevant group.
            int sentPacketCount = 0;
            for (InetSocketAddress destination : ALL_ADDRS) {
                try {
                    sentPacketCount += mReplySender.sendNow(packet, destination);
                } catch (IOException e) {
                    mSharedLog.e("Error sending packet to " + destination, e);
                }
            }

            int nextIndex = index + 1;
            // No need to go through the last handler loop if there's no callback to call
            if (nextIndex < request.getNumSends() || mCb != null) {
                // TODO: consider using AlarmManager / WakeupMessage to avoid missing sending during
                // deep sleep; but this would affect battery life, and discovered services are
                // likely not to be available since the device is in deep sleep anyway.
                final long delay = request.getDelayMs(nextIndex);
                sendMessageDelayed(obtainMessage(msg.what, nextIndex, 0, request), delay);
                if (mEnableDebugLog) mSharedLog.v("Scheduled next packet in " + delay + "ms");
            }

            // Call onSent after scheduling the next run, to allow the callback to cancel it
            if (mCb != null) {
                mCb.onSent(index, request, sentPacketCount);
            }
        }
    }

    protected MdnsPacketRepeater(@NonNull Looper looper, @NonNull MdnsReplySender replySender,
            @Nullable PacketRepeaterCallback<T> cb, @NonNull SharedLog sharedLog,
            boolean enableDebugLog) {
        mHandler = new ProbeHandler(looper);
        mReplySender = replySender;
        mCb = cb;
        mSharedLog = sharedLog;
        mEnableDebugLog = enableDebugLog;
    }

    protected void startSending(int id, @NonNull T request, long initialDelayMs) {
        if (mEnableDebugLog) {
            mSharedLog.v("Starting send with id " + id + ", request "
                    + request.getClass().getSimpleName() + ", delay " + initialDelayMs);
        }
        mHandler.sendMessageDelayed(mHandler.obtainMessage(id, 0, 0, request), initialDelayMs);
    }

    /**
     * Stop sending the packets for the specified ID
     * @return true if probing was in progress, false if this was a no-op
     */
    public boolean stop(int id) {
        if (mHandler.getLooper().getThread() != Thread.currentThread()) {
            throw new IllegalStateException("stop can only be called from the looper thread");
        }
        // Since this is run on the looper thread, messages cannot be currently processing and are
        // all in the handler queue; unless this method is called from a message, but the current
        // message cannot be cancelled.
        if (mHandler.hasMessages(id)) {
            if (mEnableDebugLog) {
                mSharedLog.v("Stopping send on id " + id);
            }
            mHandler.removeMessages(id);
            return true;
        }
        return false;
    }
}
