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
import static com.android.server.connectivity.mdns.util.MdnsUtils.ensureRunningOnHandlerThread;

import android.annotation.NonNull;
import android.annotation.RequiresApi;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.ArrayMap;
import android.util.ArraySet;

import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.SharedLog;
import com.android.server.connectivity.mdns.util.MdnsUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * A class that handles sending mDNS replies to a {@link MulticastSocket}, possibly queueing them
 * to be sent after some delay.
 *
 * TODO: implement sending after a delay, combining queued replies and duplicate answer suppression
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class MdnsReplySender {
    private static final int MSG_SEND = 1;
    private static final int PACKET_NOT_SENT = 0;
    private static final int PACKET_SENT = 1;

    @NonNull
    private final MdnsInterfaceSocket mSocket;
    @NonNull
    private final Handler mHandler;
    @NonNull
    private final byte[] mPacketCreationBuffer;
    @NonNull
    private final SharedLog mSharedLog;
    private final boolean mEnableDebugLog;
    @NonNull
    private final Dependencies mDependencies;
    // RFC6762 15.2. Multipacket Known-Answer lists
    // Multicast DNS responders associate the initial truncated query with its
    // continuation packets by examining the source IP address in each packet.
    private final Map<InetSocketAddress, MdnsReplyInfo> mSrcReplies = new ArrayMap<>();
    @NonNull
    private final MdnsFeatureFlags mMdnsFeatureFlags;

    /**
     * Dependencies of MdnsReplySender, for injection in tests.
     */
    @VisibleForTesting
    public static class Dependencies {
        /**
         * @see Handler#sendMessageDelayed(Message, long)
         */
        public void sendMessageDelayed(@NonNull Handler handler, @NonNull Message message,
                long delayMillis) {
            handler.sendMessageDelayed(message, delayMillis);
        }

        /**
         * @see Handler#removeMessages(int)
         */
        public void removeMessages(@NonNull Handler handler, int what) {
            handler.removeMessages(what);
        }

        /**
         * @see Handler#removeMessages(int)
         */
        public void removeMessages(@NonNull Handler handler, int what, @NonNull Object object) {
            handler.removeMessages(what, object);
        }
    }

    public MdnsReplySender(@NonNull Looper looper, @NonNull MdnsInterfaceSocket socket,
            @NonNull byte[] packetCreationBuffer, @NonNull SharedLog sharedLog,
            boolean enableDebugLog, @NonNull MdnsFeatureFlags mdnsFeatureFlags) {
        this(looper, socket, packetCreationBuffer, sharedLog, enableDebugLog, new Dependencies(),
                mdnsFeatureFlags);
    }

    @VisibleForTesting
    public MdnsReplySender(@NonNull Looper looper, @NonNull MdnsInterfaceSocket socket,
            @NonNull byte[] packetCreationBuffer, @NonNull SharedLog sharedLog,
            boolean enableDebugLog, @NonNull Dependencies dependencies,
            @NonNull MdnsFeatureFlags mdnsFeatureFlags) {
        mHandler = new SendHandler(looper);
        mSocket = socket;
        mPacketCreationBuffer = packetCreationBuffer;
        mSharedLog = sharedLog;
        mEnableDebugLog = enableDebugLog;
        mDependencies = dependencies;
        mMdnsFeatureFlags = mdnsFeatureFlags;
    }

    static InetSocketAddress getReplyDestination(@NonNull InetSocketAddress queuingDest,
            @NonNull InetSocketAddress incomingDest) {
        // The queuing reply is multicast, just use the current destination.
        if (queuingDest.equals(IPV4_SOCKET_ADDR) || queuingDest.equals(IPV6_SOCKET_ADDR)) {
            return queuingDest;
        }

        // The incoming reply is multicast, change the reply from unicast to multicast since
        // replying unicast when the query requests unicast reply is optional.
        if (incomingDest.equals(IPV4_SOCKET_ADDR) || incomingDest.equals(IPV6_SOCKET_ADDR)) {
            return incomingDest;
        }

        return queuingDest;
    }

    /**
     * Queue a reply to be sent when its send delay expires.
     */
    public void queueReply(@NonNull MdnsReplyInfo reply) {
        ensureRunningOnHandlerThread(mHandler);

        if (mMdnsFeatureFlags.isKnownAnswerSuppressionEnabled()) {
            mDependencies.removeMessages(mHandler, MSG_SEND, reply.source);

            final MdnsReplyInfo queuingReply = mSrcReplies.remove(reply.source);
            final ArraySet<MdnsRecord> answers = new ArraySet<>();
            final Set<MdnsRecord> additionalAnswers = new ArraySet<>();
            final Set<MdnsRecord> knownAnswers = new ArraySet<>();
            if (queuingReply != null) {
                answers.addAll(queuingReply.answers);
                additionalAnswers.addAll(queuingReply.additionalAnswers);
                knownAnswers.addAll(queuingReply.knownAnswers);
            }
            answers.addAll(reply.answers);
            additionalAnswers.addAll(reply.additionalAnswers);
            knownAnswers.addAll(reply.knownAnswers);
            // RFC6762 7.2. Multipacket Known-Answer Suppression
            // If the responder sees any of its answers listed in the Known-Answer
            // lists of subsequent packets from the querying host, it MUST delete
            // that answer from the list of answers it is planning to give.
            for (MdnsRecord knownAnswer : knownAnswers) {
                final int idx = answers.indexOf(knownAnswer);
                if (idx >= 0 && knownAnswer.getTtl() > answers.valueAt(idx).getTtl() / 2) {
                    answers.removeAt(idx);
                }
            }

            if (answers.size() == 0) {
                return;
            }

            final MdnsReplyInfo newReply = new MdnsReplyInfo(
                    new ArrayList<>(answers),
                    new ArrayList<>(additionalAnswers),
                    reply.sendDelayMs,
                    queuingReply == null ? reply.destination
                            : getReplyDestination(queuingReply.destination, reply.destination),
                    reply.source,
                    new ArrayList<>(knownAnswers));

            mSrcReplies.put(newReply.source, newReply);
            mDependencies.sendMessageDelayed(mHandler,
                    mHandler.obtainMessage(MSG_SEND, newReply.source), newReply.sendDelayMs);
        } else {
            mDependencies.sendMessageDelayed(
                    mHandler, mHandler.obtainMessage(MSG_SEND, reply), reply.sendDelayMs);
        }

        if (mEnableDebugLog) {
            mSharedLog.v("Scheduling " + reply);
        }
    }

    /**
     * Send a packet immediately.
     *
     * Must be called on the looper thread used by the {@link MdnsReplySender}.
     */
    public int sendNow(@NonNull MdnsPacket packet, @NonNull InetSocketAddress destination)
            throws IOException {
        ensureRunningOnHandlerThread(mHandler);
        if (!((destination.getAddress() instanceof Inet6Address && mSocket.hasJoinedIpv6())
                || (destination.getAddress() instanceof Inet4Address && mSocket.hasJoinedIpv4()))) {
            // Skip sending if the socket has not joined the v4/v6 group (there was no address)
            return PACKET_NOT_SENT;
        }
        final byte[] outBuffer = MdnsUtils.createRawDnsPacket(mPacketCreationBuffer, packet);
        mSocket.send(new DatagramPacket(outBuffer, 0, outBuffer.length, destination));
        return PACKET_SENT;
    }

    /**
     * Cancel all pending sends.
     */
    public void cancelAll() {
        ensureRunningOnHandlerThread(mHandler);
        mDependencies.removeMessages(mHandler, MSG_SEND);
    }

    private class SendHandler extends Handler {
        SendHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            final MdnsReplyInfo replyInfo;
            if (mMdnsFeatureFlags.isKnownAnswerSuppressionEnabled()) {
                // Retrieve the MdnsReplyInfo from the map via a source address, as the reply info
                // will be combined or updated.
                final InetSocketAddress source = (InetSocketAddress) msg.obj;
                replyInfo = mSrcReplies.remove(source);
            } else {
                replyInfo = (MdnsReplyInfo) msg.obj;
            }

            if (replyInfo == null) {
                mSharedLog.wtf("Unknown reply info.");
                return;
            }

            if (mEnableDebugLog) mSharedLog.v("Sending " + replyInfo);

            final int flags = 0x8400; // Response, authoritative (rfc6762 18.4)
            final MdnsPacket packet = new MdnsPacket(flags,
                    Collections.emptyList() /* questions */,
                    replyInfo.answers,
                    Collections.emptyList() /* authorityRecords */,
                    replyInfo.additionalAnswers);

            try {
                sendNow(packet, replyInfo.destination);
            } catch (IOException e) {
                mSharedLog.e("Error sending MDNS response", e);
            }
        }
    }
}
