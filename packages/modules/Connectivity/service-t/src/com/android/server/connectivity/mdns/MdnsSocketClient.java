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

package com.android.server.connectivity.mdns;

import android.Manifest.permission;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.content.Context;
import android.net.Network;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.SystemClock;
import android.text.format.DateUtils;

import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.SharedLog;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The {@link MdnsSocketClient} maintains separate threads to send and receive mDNS packets for all
 * the requested service types.
 *
 * <p>See https://tools.ietf.org/html/rfc6763 (namely sections 4 and 5).
 */
public class MdnsSocketClient implements MdnsSocketClientBase {

    private static final String TAG = "MdnsClient";
    // TODO: The following values are copied from cast module. We need to think about the
    // better way to share those.
    private static final String CAST_SENDER_LOG_SOURCE = "CAST_SENDER_SDK";
    private static final String CAST_PREFS_NAME = "google_cast";
    private static final String PREF_CAST_SENDER_ID = "PREF_CAST_SENDER_ID";
    private static final String MULTICAST_TYPE = "multicast";
    private static final String UNICAST_TYPE = "unicast";

    private static final long SLEEP_TIME_FOR_SOCKET_THREAD_MS =
            MdnsConfigs.sleepTimeForSocketThreadMs();
    // A value of 0 leads to an infinite wait.
    private static final long THREAD_JOIN_TIMEOUT_MS = DateUtils.SECOND_IN_MILLIS;
    private static final int RECEIVER_BUFFER_SIZE = 2048;
    @VisibleForTesting
    final Queue<DatagramPacket> multicastPacketQueue = new ArrayDeque<>();
    @VisibleForTesting
    final Queue<DatagramPacket> unicastPacketQueue = new ArrayDeque<>();
    private final Context context;
    private final byte[] multicastReceiverBuffer = new byte[RECEIVER_BUFFER_SIZE];
    @Nullable private final byte[] unicastReceiverBuffer;
    private final MulticastLock multicastLock;
    private final boolean useSeparateSocketForUnicast =
            MdnsConfigs.useSeparateSocketToSendUnicastQuery();
    private final boolean checkMulticastResponse = MdnsConfigs.checkMulticastResponse();
    private final long checkMulticastResponseIntervalMs =
            MdnsConfigs.checkMulticastResponseIntervalMs();
    private final boolean propagateInterfaceIndex =
            MdnsConfigs.allowNetworkInterfaceIndexPropagation();
    private final Object socketLock = new Object();
    private final Object timerObject = new Object();
    // If multicast response was received in the current session. The value is reset in the
    // beginning of each session.
    @VisibleForTesting
    boolean receivedMulticastResponse;
    // If unicast response was received in the current session. The value is reset in the beginning
    // of each session.
    @VisibleForTesting
    boolean receivedUnicastResponse;
    // If the phone is the bad state where it can't receive any multicast response.
    @VisibleForTesting
    AtomicBoolean cannotReceiveMulticastResponse = new AtomicBoolean(false);
    @VisibleForTesting @Nullable volatile Thread sendThread;
    @VisibleForTesting @Nullable Thread multicastReceiveThread;
    @VisibleForTesting @Nullable Thread unicastReceiveThread;
    private volatile boolean shouldStopSocketLoop;
    @Nullable private Callback callback;
    @Nullable private MdnsSocket multicastSocket;
    @Nullable private MdnsSocket unicastSocket;
    private int receivedPacketNumber = 0;
    @Nullable private Timer logMdnsPacketTimer;
    private AtomicInteger packetsCount;
    @Nullable private Timer checkMulticastResponseTimer;
    private final SharedLog sharedLog;
    @NonNull private final MdnsFeatureFlags mdnsFeatureFlags;

    public MdnsSocketClient(@NonNull Context context, @NonNull MulticastLock multicastLock,
            SharedLog sharedLog, @NonNull MdnsFeatureFlags mdnsFeatureFlags) {
        this.sharedLog = sharedLog;
        this.context = context;
        this.multicastLock = multicastLock;
        if (useSeparateSocketForUnicast) {
            unicastReceiverBuffer = new byte[RECEIVER_BUFFER_SIZE];
        } else {
            unicastReceiverBuffer = null;
        }
        this.mdnsFeatureFlags = mdnsFeatureFlags;
    }

    @Override
    public synchronized void setCallback(@Nullable Callback callback) {
        this.callback = callback;
    }

    @RequiresPermission(permission.CHANGE_WIFI_MULTICAST_STATE)
    @Override
    public synchronized void startDiscovery() throws IOException {
        if (multicastSocket != null) {
            sharedLog.w("Discovery is already in progress.");
            return;
        }

        receivedMulticastResponse = false;
        receivedUnicastResponse = false;
        cannotReceiveMulticastResponse.set(false);

        shouldStopSocketLoop = false;
        try {
            // TODO (changed when importing code): consider setting thread stats tag
            multicastSocket = createMdnsSocket(MdnsConstants.MDNS_PORT, sharedLog);
            multicastSocket.joinGroup();
            if (useSeparateSocketForUnicast) {
                // For unicast, use port 0 and the system will assign it with any available port.
                unicastSocket = createMdnsSocket(0, sharedLog);
            }
            multicastLock.acquire();
        } catch (IOException e) {
            multicastLock.release();
            if (multicastSocket != null) {
                multicastSocket.close();
                multicastSocket = null;
            }
            if (unicastSocket != null) {
                unicastSocket.close();
                unicastSocket = null;
            }
            throw e;
        } finally {
            // TODO (changed when importing code): consider resetting thread stats tag
        }
        createAndStartSendThread();
        createAndStartReceiverThreads();
    }

    @RequiresPermission(permission.CHANGE_WIFI_MULTICAST_STATE)
    @Override
    public void stopDiscovery() {
        sharedLog.log("Stop discovery.");
        if (multicastSocket == null && unicastSocket == null) {
            return;
        }

        if (MdnsConfigs.clearMdnsPacketQueueAfterDiscoveryStops()) {
            synchronized (multicastPacketQueue) {
                multicastPacketQueue.clear();
            }
            synchronized (unicastPacketQueue) {
                unicastPacketQueue.clear();
            }
        }

        multicastLock.release();

        shouldStopSocketLoop = true;
        waitForSendThreadToStop();
        waitForReceiverThreadsToStop();

        synchronized (socketLock) {
            multicastSocket = null;
            unicastSocket = null;
        }

        synchronized (timerObject) {
            if (checkMulticastResponseTimer != null) {
                checkMulticastResponseTimer.cancel();
                checkMulticastResponseTimer = null;
            }
        }
    }

    @Override
    public void sendPacketRequestingMulticastResponse(@NonNull DatagramPacket packet,
            boolean onlyUseIpv6OnIpv6OnlyNetworks) {
        sendMdnsPacket(packet, multicastPacketQueue, onlyUseIpv6OnIpv6OnlyNetworks);
    }

    @Override
    public void sendPacketRequestingUnicastResponse(@NonNull DatagramPacket packet,
            boolean onlyUseIpv6OnIpv6OnlyNetworks) {
        if (useSeparateSocketForUnicast) {
            sendMdnsPacket(packet, unicastPacketQueue, onlyUseIpv6OnIpv6OnlyNetworks);
        } else {
            sendMdnsPacket(packet, multicastPacketQueue, onlyUseIpv6OnIpv6OnlyNetworks);
        }
    }

    @Override
    public void notifyNetworkRequested(
            @NonNull MdnsServiceBrowserListener listener,
            @Nullable Network network,
            @NonNull SocketCreationCallback socketCreationCallback) {
        if (network != null) {
            throw new IllegalArgumentException("This socket client does not support requesting "
                    + "specific networks");
        }
        socketCreationCallback.onSocketCreated(new SocketKey(multicastSocket.getInterfaceIndex()));
    }

    @Override
    public boolean supportsRequestingSpecificNetworks() {
        return false;
    }

    private void sendMdnsPacket(DatagramPacket packet, Queue<DatagramPacket> packetQueueToUse,
            boolean onlyUseIpv6OnIpv6OnlyNetworks) {
        if (shouldStopSocketLoop && !MdnsConfigs.allowAddMdnsPacketAfterDiscoveryStops()) {
            sharedLog.w("sendMdnsPacket() is called after discovery already stopped");
            return;
        }

        final boolean isIpv4 = ((InetSocketAddress) packet.getSocketAddress()).getAddress()
                instanceof Inet4Address;
        final boolean isIpv6 = ((InetSocketAddress) packet.getSocketAddress()).getAddress()
                instanceof Inet6Address;
        final boolean ipv6Only = multicastSocket != null && multicastSocket.isOnIPv6OnlyNetwork();
        if (isIpv4 && ipv6Only) {
            return;
        }
        if (isIpv6 && !ipv6Only && onlyUseIpv6OnIpv6OnlyNetworks) {
            return;
        }

        synchronized (packetQueueToUse) {
            while (packetQueueToUse.size() >= MdnsConfigs.mdnsPacketQueueMaxSize()) {
                packetQueueToUse.remove();
            }
            packetQueueToUse.add(packet);
        }
        triggerSendThread();
    }

    private void createAndStartSendThread() {
        if (sendThread != null) {
            sharedLog.w("A socket thread already exists.");
            return;
        }
        sendThread = new Thread(this::sendThreadMain);
        sendThread.setName("mdns-send");
        sendThread.start();
    }

    private void createAndStartReceiverThreads() {
        if (multicastReceiveThread != null) {
            sharedLog.w("A multicast receiver thread already exists.");
            return;
        }
        multicastReceiveThread =
                new Thread(() -> receiveThreadMain(multicastReceiverBuffer, multicastSocket));
        multicastReceiveThread.setName("mdns-multicast-receive");
        multicastReceiveThread.start();

        if (useSeparateSocketForUnicast) {
            unicastReceiveThread =
                    new Thread(
                            () -> {
                                if (unicastReceiverBuffer != null) {
                                    receiveThreadMain(unicastReceiverBuffer, unicastSocket);
                                }
                            });
            unicastReceiveThread.setName("mdns-unicast-receive");
            unicastReceiveThread.start();
        }
    }

    private void triggerSendThread() {
        sharedLog.log("Trigger send thread.");
        Thread sendThread = this.sendThread;
        if (sendThread != null) {
            sendThread.interrupt();
        } else {
            sharedLog.w("Socket thread is null");
        }
    }

    private void waitForReceiverThreadsToStop() {
        if (multicastReceiveThread != null) {
            waitForThread(multicastReceiveThread);
            multicastReceiveThread = null;
        }

        if (unicastReceiveThread != null) {
            waitForThread(unicastReceiveThread);
            unicastReceiveThread = null;
        }
    }

    private void waitForSendThreadToStop() {
        sharedLog.log("wait For Send Thread To Stop");
        if (sendThread == null) {
            sharedLog.w("socket thread is already dead.");
            return;
        }
        waitForThread(sendThread);
        sendThread = null;
    }

    private void waitForThread(Thread thread) {
        long startMs = SystemClock.elapsedRealtime();
        long waitMs = THREAD_JOIN_TIMEOUT_MS;
        while (thread.isAlive() && (waitMs > 0)) {
            try {
                thread.interrupt();
                thread.join(waitMs);
                if (thread.isAlive()) {
                    sharedLog.w("Failed to join thread: " + thread);
                }
                break;
            } catch (InterruptedException e) {
                // Compute remaining time after at least a single join call, in case the clock
                // resolution is poor.
                waitMs = THREAD_JOIN_TIMEOUT_MS - (SystemClock.elapsedRealtime() - startMs);
            }
        }
    }

    private void sendThreadMain() {
        List<DatagramPacket> multicastPacketsToSend = new ArrayList<>();
        List<DatagramPacket> unicastPacketsToSend = new ArrayList<>();
        boolean shouldThreadSleep;
        try {
            while (!shouldStopSocketLoop) {
                try {
                    // Make a local copy of all packets, and clear the queue.
                    // Send packets that ask for multicast response.
                    multicastPacketsToSend.clear();
                    synchronized (multicastPacketQueue) {
                        multicastPacketsToSend.addAll(multicastPacketQueue);
                        multicastPacketQueue.clear();
                    }

                    // Send packets that ask for unicast response.
                    if (useSeparateSocketForUnicast) {
                        unicastPacketsToSend.clear();
                        synchronized (unicastPacketQueue) {
                            unicastPacketsToSend.addAll(unicastPacketQueue);
                            unicastPacketQueue.clear();
                        }
                        if (unicastSocket != null) {
                            sendPackets(unicastPacketsToSend, unicastSocket);
                        }
                    }

                    // Send multicast packets.
                    if (multicastSocket != null) {
                        sendPackets(multicastPacketsToSend, multicastSocket);
                    }

                    // Sleep ONLY if no more packets have been added to the queue, while packets
                    // were being sent.
                    synchronized (multicastPacketQueue) {
                        synchronized (unicastPacketQueue) {
                            shouldThreadSleep =
                                    multicastPacketQueue.isEmpty() && unicastPacketQueue.isEmpty();
                        }
                    }
                    if (shouldThreadSleep) {
                        Thread.sleep(SLEEP_TIME_FOR_SOCKET_THREAD_MS);
                    }
                } catch (InterruptedException e) {
                    // Don't log the interruption as it's expected.
                }
            }
        } finally {
            sharedLog.log("Send thread stopped.");
            try {
                if (multicastSocket != null) {
                    multicastSocket.leaveGroup();
                }
            } catch (Exception t) {
                sharedLog.e("Failed to leave the group.", t);
            }

            // Close the socket first. This is the only way to interrupt a blocking receive.
            try {
                // This is a race with the use of the file descriptor (b/27403984).
                if (multicastSocket != null) {
                    multicastSocket.close();
                }
                if (unicastSocket != null) {
                    unicastSocket.close();
                }
            } catch (RuntimeException t) {
                sharedLog.e("Failed to close the mdns socket.", t);
            }
        }
    }

    private void receiveThreadMain(byte[] receiverBuffer, @Nullable MdnsSocket socket) {
        DatagramPacket packet = new DatagramPacket(receiverBuffer, receiverBuffer.length);

        while (!shouldStopSocketLoop) {
            try {
                // This is a race with the use of the file descriptor (b/27403984).
                synchronized (socketLock) {
                    // This checks is to make sure the socket was not set to null.
                    if (socket != null && (socket == multicastSocket || socket == unicastSocket)) {
                        socket.receive(packet);
                    }
                }

                if (!shouldStopSocketLoop) {
                    String responseType = socket == multicastSocket ? MULTICAST_TYPE : UNICAST_TYPE;
                    processResponsePacket(
                            packet,
                            responseType,
                            /* interfaceIndex= */ (socket == null || !propagateInterfaceIndex)
                                    ? MdnsSocket.INTERFACE_INDEX_UNSPECIFIED
                                    : socket.getInterfaceIndex(),
                            /* network= */ socket.getNetwork());
                }
            } catch (IOException e) {
                if (!shouldStopSocketLoop) {
                    sharedLog.e("Failed to receive mDNS packets.", e);
                }
            }
        }
        sharedLog.log("Receive thread stopped.");
    }

    private int processResponsePacket(@NonNull DatagramPacket packet, String responseType,
            int interfaceIndex, @Nullable Network network) {
        int packetNumber = ++receivedPacketNumber;

        final MdnsPacket response;
        try {
            response = MdnsResponseDecoder.parseResponse(
                    packet.getData(), packet.getLength(), mdnsFeatureFlags);
        } catch (MdnsPacket.ParseException e) {
            sharedLog.w(String.format("Error while decoding %s packet (%d): %d",
                    responseType, packetNumber, e.code));
            if (callback != null) {
                callback.onFailedToParseMdnsResponse(packetNumber, e.code,
                        new SocketKey(network, interfaceIndex));
            }
            return e.code;
        }

        if (response == null) {
            return MdnsResponseErrorCode.ERROR_NOT_RESPONSE_MESSAGE;
        }

        if (callback != null) {
            callback.onResponseReceived(
                    response, new SocketKey(network, interfaceIndex));
        }

        return MdnsResponseErrorCode.SUCCESS;
    }

    @VisibleForTesting
    MdnsSocket createMdnsSocket(int port, SharedLog sharedLog) throws IOException {
        return new MdnsSocket(new MulticastNetworkInterfaceProvider(context, sharedLog), port,
                sharedLog);
    }

    private void sendPackets(List<DatagramPacket> packets, MdnsSocket socket) {
        String requestType = socket == multicastSocket ? "multicast" : "unicast";
        for (DatagramPacket packet : packets) {
            if (shouldStopSocketLoop) {
                break;
            }
            try {
                sharedLog.log(String.format("Sending a %s mDNS packet...", requestType));
                socket.send(packet);

                // Start the timer task to monitor the response.
                synchronized (timerObject) {
                    if (socket == multicastSocket) {
                        if (cannotReceiveMulticastResponse.get()) {
                            // Don't schedule the timer task if we are already in the bad state.
                            return;
                        }
                        if (checkMulticastResponseTimer != null) {
                            // Don't schedule the timer task if it's already scheduled.
                            return;
                        }
                        if (checkMulticastResponse && useSeparateSocketForUnicast) {
                            // Only when useSeparateSocketForUnicast is true, we can tell if we
                            // received a multicast or unicast response.
                            checkMulticastResponseTimer = new Timer();
                            checkMulticastResponseTimer.schedule(
                                    new TimerTask() {
                                        @Override
                                        public void run() {
                                            synchronized (timerObject) {
                                                if (checkMulticastResponseTimer == null) {
                                                    // Discovery already stopped.
                                                    return;
                                                }
                                                if ((!receivedMulticastResponse)
                                                        && receivedUnicastResponse) {
                                                    sharedLog.e(String.format(
                                                            "Haven't received multicast response"
                                                                    + " in the last %d ms.",
                                                            checkMulticastResponseIntervalMs));
                                                    cannotReceiveMulticastResponse.set(true);
                                                }
                                                checkMulticastResponseTimer = null;
                                            }
                                        }
                                    },
                                    checkMulticastResponseIntervalMs);
                        }
                    }
                }
            } catch (IOException e) {
                sharedLog.e(String.format("Failed to send a %s mDNS packet.", requestType), e);
            }
        }
        packets.clear();
    }
}