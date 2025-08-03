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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.GuardedBy;

import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.SharedLog;
import com.android.server.connectivity.mdns.util.MdnsUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * This class keeps tracking the set of registered {@link MdnsServiceBrowserListener} instances, and
 * notify them when a mDNS service instance is found, updated, or removed?
 */
public class MdnsDiscoveryManager implements MdnsSocketClientBase.Callback {
    private static final String TAG = MdnsDiscoveryManager.class.getSimpleName();
    public static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    private final ExecutorProvider executorProvider;
    private final MdnsSocketClientBase socketClient;
    @NonNull private final SharedLog sharedLog;

    @NonNull private final PerSocketServiceTypeClients perSocketServiceTypeClients;
    @NonNull private final DiscoveryExecutor discoveryExecutor;
    @NonNull private final MdnsFeatureFlags mdnsFeatureFlags;

    // Only accessed on the handler thread, initialized before first use
    @Nullable
    private MdnsServiceCache serviceCache;

    private static class PerSocketServiceTypeClients {
        private final ArrayMap<Pair<String, SocketKey>, MdnsServiceTypeClient> clients =
                new ArrayMap<>();

        public void put(@NonNull String serviceType, @NonNull SocketKey socketKey,
                @NonNull MdnsServiceTypeClient client) {
            final String dnsLowerServiceType = MdnsUtils.toDnsLowerCase(serviceType);
            final Pair<String, SocketKey> perSocketServiceType = new Pair<>(dnsLowerServiceType,
                    socketKey);
            clients.put(perSocketServiceType, client);
        }

        @Nullable
        public MdnsServiceTypeClient get(
                @NonNull String serviceType, @NonNull SocketKey socketKey) {
            final String dnsLowerServiceType = MdnsUtils.toDnsLowerCase(serviceType);
            final Pair<String, SocketKey> perSocketServiceType = new Pair<>(dnsLowerServiceType,
                    socketKey);
            return clients.getOrDefault(perSocketServiceType, null);
        }

        public List<MdnsServiceTypeClient> getByServiceType(@NonNull String serviceType) {
            final String dnsLowerServiceType = MdnsUtils.toDnsLowerCase(serviceType);
            final List<MdnsServiceTypeClient> list = new ArrayList<>();
            for (int i = 0; i < clients.size(); i++) {
                final Pair<String, SocketKey> perSocketServiceType = clients.keyAt(i);
                if (dnsLowerServiceType.equals(perSocketServiceType.first)) {
                    list.add(clients.valueAt(i));
                }
            }
            return list;
        }

        public List<MdnsServiceTypeClient> getBySocketKey(@NonNull SocketKey socketKey) {
            final List<MdnsServiceTypeClient> list = new ArrayList<>();
            for (int i = 0; i < clients.size(); i++) {
                final Pair<String, SocketKey> perSocketServiceType = clients.keyAt(i);
                if (socketKey.equals(perSocketServiceType.second)) {
                    list.add(clients.valueAt(i));
                }
            }
            return list;
        }

        public List<MdnsServiceTypeClient> getAllMdnsServiceTypeClient() {
            return new ArrayList<>(clients.values());
        }

        public void remove(@NonNull MdnsServiceTypeClient client) {
            for (int i = 0; i < clients.size(); ++i) {
                if (Objects.equals(client, clients.valueAt(i))) {
                    clients.removeAt(i);
                    break;
                }
            }
        }

        public boolean isEmpty() {
            return clients.isEmpty();
        }
    }

    public MdnsDiscoveryManager(@NonNull ExecutorProvider executorProvider,
            @NonNull MdnsSocketClientBase socketClient, @NonNull SharedLog sharedLog,
            @NonNull MdnsFeatureFlags mdnsFeatureFlags) {
        this.executorProvider = executorProvider;
        this.socketClient = socketClient;
        this.sharedLog = sharedLog;
        this.perSocketServiceTypeClients = new PerSocketServiceTypeClients();
        this.mdnsFeatureFlags = mdnsFeatureFlags;
        this.discoveryExecutor = new DiscoveryExecutor(socketClient.getLooper());
    }

    private static class DiscoveryExecutor implements Executor {
        private final HandlerThread handlerThread;

        @GuardedBy("pendingTasks")
        @Nullable private Handler handler;
        @GuardedBy("pendingTasks")
        @NonNull private final ArrayList<Runnable> pendingTasks = new ArrayList<>();

        DiscoveryExecutor(@Nullable Looper defaultLooper) {
            if (defaultLooper != null) {
                this.handlerThread = null;
                synchronized (pendingTasks) {
                    this.handler = new Handler(defaultLooper);
                }
            } else {
                this.handlerThread = new HandlerThread(MdnsDiscoveryManager.class.getSimpleName()) {
                    @Override
                    protected void onLooperPrepared() {
                        synchronized (pendingTasks) {
                            handler = new Handler(getLooper());
                            for (Runnable pendingTask : pendingTasks) {
                                handler.post(pendingTask);
                            }
                            pendingTasks.clear();
                        }
                    }
                };
                this.handlerThread.start();
            }
        }

        public void checkAndRunOnHandlerThread(@NonNull Runnable function) {
            if (this.handlerThread == null) {
                // Callers are expected to already be running on the handler when a defaultLooper
                // was provided
                function.run();
            } else {
                execute(function);
            }
        }

        @Override
        public void execute(Runnable function) {
            final Handler handler;
            synchronized (pendingTasks) {
                if (this.handler == null) {
                    pendingTasks.add(function);
                    return;
                } else {
                    handler = this.handler;
                }
            }
            handler.post(function);
        }

        void shutDown() {
            if (this.handlerThread != null) {
                this.handlerThread.quitSafely();
            }
        }

        void ensureRunningOnHandlerThread() {
            synchronized (pendingTasks) {
                MdnsUtils.ensureRunningOnHandlerThread(handler);
            }
        }
    }

    /**
     * Do the cleanup of the MdnsDiscoveryManager
     */
    public void shutDown() {
        discoveryExecutor.shutDown();
    }

    /**
     * Starts (or continue) to discovery mDNS services with given {@code serviceType}, and registers
     * {@code listener} for receiving mDNS service discovery responses.
     *
     * @param serviceType   The type of the service to discover.
     * @param listener      The {@link MdnsServiceBrowserListener} listener.
     * @param searchOptions The {@link MdnsSearchOptions} to be used for discovering {@code
     *                      serviceType}.
     */
    @RequiresPermission(permission.CHANGE_WIFI_MULTICAST_STATE)
    public void registerListener(
            @NonNull String serviceType,
            @NonNull MdnsServiceBrowserListener listener,
            @NonNull MdnsSearchOptions searchOptions) {
        sharedLog.i("Registering listener for serviceType: " + serviceType);
        discoveryExecutor.checkAndRunOnHandlerThread(() ->
                handleRegisterListener(serviceType, listener, searchOptions));
    }

    private void handleRegisterListener(
            @NonNull String serviceType,
            @NonNull MdnsServiceBrowserListener listener,
            @NonNull MdnsSearchOptions searchOptions) {
        if (perSocketServiceTypeClients.isEmpty()) {
            // First listener. Starts the socket client.
            try {
                socketClient.startDiscovery();
            } catch (IOException e) {
                sharedLog.e("Failed to start discover.", e);
                return;
            }
        }
        // Request the network for discovery.
        socketClient.notifyNetworkRequested(listener, searchOptions.getNetwork(),
                new MdnsSocketClientBase.SocketCreationCallback() {
                    @Override
                    public void onSocketCreated(@NonNull SocketKey socketKey) {
                        discoveryExecutor.ensureRunningOnHandlerThread();
                        // All listeners of the same service types shares the same
                        // MdnsServiceTypeClient.
                        MdnsServiceTypeClient serviceTypeClient =
                                perSocketServiceTypeClients.get(serviceType, socketKey);
                        if (serviceTypeClient == null) {
                            serviceTypeClient = createServiceTypeClient(serviceType, socketKey);
                            perSocketServiceTypeClients.put(serviceType, socketKey,
                                    serviceTypeClient);
                        }
                        serviceTypeClient.startSendAndReceive(listener, searchOptions);
                    }

                    @Override
                    public void onSocketDestroyed(@NonNull SocketKey socketKey) {
                        discoveryExecutor.ensureRunningOnHandlerThread();
                        final MdnsServiceTypeClient serviceTypeClient =
                                perSocketServiceTypeClients.get(serviceType, socketKey);
                        if (serviceTypeClient == null) return;
                        // Notify all listeners that all services are removed from this socket.
                        serviceTypeClient.notifySocketDestroyed();
                        executorProvider.shutdownExecutorService(serviceTypeClient.getExecutor());
                        perSocketServiceTypeClients.remove(serviceTypeClient);
                    }
                });
    }

    /**
     * Unregister {@code listener} for receiving mDNS service discovery responses. IF no listener is
     * registered for the given service type, stops discovery for the service type.
     *
     * @param serviceType The type of the service to discover.
     * @param listener    The {@link MdnsServiceBrowserListener} listener.
     */
    @RequiresPermission(permission.CHANGE_WIFI_MULTICAST_STATE)
    public void unregisterListener(
            @NonNull String serviceType, @NonNull MdnsServiceBrowserListener listener) {
        sharedLog.i("Unregistering listener for serviceType:" + serviceType);
        discoveryExecutor.checkAndRunOnHandlerThread(() ->
                handleUnregisterListener(serviceType, listener));
    }

    private void handleUnregisterListener(
            @NonNull String serviceType, @NonNull MdnsServiceBrowserListener listener) {
        // Unrequested the network.
        socketClient.notifyNetworkUnrequested(listener);

        final List<MdnsServiceTypeClient> serviceTypeClients =
                perSocketServiceTypeClients.getByServiceType(serviceType);
        if (serviceTypeClients.isEmpty()) {
            return;
        }
        for (int i = 0; i < serviceTypeClients.size(); i++) {
            final MdnsServiceTypeClient serviceTypeClient = serviceTypeClients.get(i);
            if (serviceTypeClient.stopSendAndReceive(listener)) {
                // No listener is registered for the service type anymore, remove it from the list
                // of the service type clients.
                executorProvider.shutdownExecutorService(serviceTypeClient.getExecutor());
                perSocketServiceTypeClients.remove(serviceTypeClient);
            }
        }
        if (perSocketServiceTypeClients.isEmpty()) {
            // No discovery request. Stops the socket client.
            sharedLog.i("All service type listeners unregistered; stopping discovery");
            socketClient.stopDiscovery();
        }
    }

    @Override
    public void onResponseReceived(@NonNull MdnsPacket packet, @NonNull SocketKey socketKey) {
        discoveryExecutor.checkAndRunOnHandlerThread(() ->
                handleOnResponseReceived(packet, socketKey));
    }

    private void handleOnResponseReceived(@NonNull MdnsPacket packet,
            @NonNull SocketKey socketKey) {
        for (MdnsServiceTypeClient serviceTypeClient : getMdnsServiceTypeClient(socketKey)) {
            serviceTypeClient.processResponse(packet, socketKey);
        }
    }

    private List<MdnsServiceTypeClient> getMdnsServiceTypeClient(@NonNull SocketKey socketKey) {
        if (socketClient.supportsRequestingSpecificNetworks()) {
            return perSocketServiceTypeClients.getBySocketKey(socketKey);
        } else {
            return perSocketServiceTypeClients.getAllMdnsServiceTypeClient();
        }
    }

    @Override
    public void onFailedToParseMdnsResponse(int receivedPacketNumber, int errorCode,
            @NonNull SocketKey socketKey) {
        discoveryExecutor.checkAndRunOnHandlerThread(() ->
                handleOnFailedToParseMdnsResponse(receivedPacketNumber, errorCode, socketKey));
    }

    private void handleOnFailedToParseMdnsResponse(int receivedPacketNumber, int errorCode,
            @NonNull SocketKey socketKey) {
        for (MdnsServiceTypeClient serviceTypeClient : getMdnsServiceTypeClient(socketKey)) {
            serviceTypeClient.onFailedToParseMdnsResponse(receivedPacketNumber, errorCode);
        }
    }

    @VisibleForTesting
    MdnsServiceTypeClient createServiceTypeClient(@NonNull String serviceType,
            @NonNull SocketKey socketKey) {
        discoveryExecutor.ensureRunningOnHandlerThread();
        sharedLog.log("createServiceTypeClient for type:" + serviceType + " " + socketKey);
        final String tag = serviceType + "-" + socketKey.getNetwork()
                + "/" + socketKey.getInterfaceIndex();
        final Looper looper = Looper.myLooper();
        if (serviceCache == null) {
            serviceCache = new MdnsServiceCache(looper, mdnsFeatureFlags);
        }
        return new MdnsServiceTypeClient(
                serviceType, socketClient,
                executorProvider.newServiceTypeClientSchedulerExecutor(), socketKey,
                sharedLog.forSubComponent(tag), looper, serviceCache);
    }

    /**
     * Dump DiscoveryManager state.
     */
    public void dump(PrintWriter pw) {
        discoveryExecutor.checkAndRunOnHandlerThread(() -> {
            pw.println();
            // Dump ServiceTypeClients
            for (MdnsServiceTypeClient serviceTypeClient
                    : perSocketServiceTypeClients.getAllMdnsServiceTypeClient()) {
                serviceTypeClient.dump(pw);
            }
        });
    }
}