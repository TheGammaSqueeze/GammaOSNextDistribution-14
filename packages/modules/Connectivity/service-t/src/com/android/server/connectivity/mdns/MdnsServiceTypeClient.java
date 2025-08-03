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

import static com.android.server.connectivity.mdns.MdnsServiceCache.ServiceExpiredCallback;
import static com.android.server.connectivity.mdns.MdnsServiceCache.findMatchedResponse;
import static com.android.server.connectivity.mdns.util.MdnsUtils.Clock;
import static com.android.server.connectivity.mdns.util.MdnsUtils.ensureRunningOnHandlerThread;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.CollectionUtils;
import com.android.net.module.util.SharedLog;
import com.android.server.connectivity.mdns.util.MdnsUtils;

import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Instance of this class sends and receives mDNS packets of a given service type and invoke
 * registered {@link MdnsServiceBrowserListener} instances.
 */
public class MdnsServiceTypeClient {

    private static final String TAG = MdnsServiceTypeClient.class.getSimpleName();
    private static final int DEFAULT_MTU = 1500;
    @VisibleForTesting
    static final int EVENT_START_QUERYTASK = 1;
    static final int EVENT_QUERY_RESULT = 2;
    static final int INVALID_TRANSACTION_ID = -1;

    private final String serviceType;
    private final String[] serviceTypeLabels;
    private final MdnsSocketClientBase socketClient;
    private final MdnsResponseDecoder responseDecoder;
    private final ScheduledExecutorService executor;
    @NonNull private final SocketKey socketKey;
    @NonNull private final SharedLog sharedLog;
    @NonNull private final Handler handler;
    @NonNull private final MdnsQueryScheduler mdnsQueryScheduler;
    @NonNull private final Dependencies dependencies;
    /**
     * The service caches for each socket. It should be accessed from looper thread only.
     */
    @NonNull private final MdnsServiceCache serviceCache;
    @NonNull private final MdnsServiceCache.CacheKey cacheKey;
    @NonNull private final ServiceExpiredCallback serviceExpiredCallback =
            new ServiceExpiredCallback() {
                @Override
                public void onServiceRecordExpired(@NonNull MdnsResponse previousResponse,
                        @Nullable MdnsResponse newResponse) {
                    notifyRemovedServiceToListeners(previousResponse, "Service record expired");
                }
            };
    private final ArrayMap<MdnsServiceBrowserListener, ListenerInfo> listeners =
            new ArrayMap<>();
    private final boolean removeServiceAfterTtlExpires =
            MdnsConfigs.removeServiceAfterTtlExpires();
    private final Clock clock;

    @Nullable private MdnsSearchOptions searchOptions;

    // The session ID increases when startSendAndReceive() is called where we schedule a
    // QueryTask for
    // new subtypes. It stays the same between packets for same subtypes.
    private long currentSessionId = 0;
    private long lastSentTime;

    private static class ListenerInfo {
        @NonNull
        final MdnsSearchOptions searchOptions;
        final Set<String> discoveredServiceNames;

        ListenerInfo(@NonNull MdnsSearchOptions searchOptions,
                @Nullable ListenerInfo previousInfo) {
            this.searchOptions = searchOptions;
            this.discoveredServiceNames = previousInfo == null
                    ? MdnsUtils.newSet() : previousInfo.discoveredServiceNames;
        }

        /**
         * Set the given service name as discovered.
         *
         * @return true if the service name was not discovered before.
         */
        boolean setServiceDiscovered(@NonNull String serviceName) {
            return discoveredServiceNames.add(MdnsUtils.toDnsLowerCase(serviceName));
        }

        void unsetServiceDiscovered(@NonNull String serviceName) {
            discoveredServiceNames.remove(MdnsUtils.toDnsLowerCase(serviceName));
        }
    }

    private class QueryTaskHandler extends Handler {
        QueryTaskHandler(Looper looper) {
            super(looper);
        }

        @Override
        @SuppressWarnings("FutureReturnValueIgnored")
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_START_QUERYTASK: {
                    final MdnsQueryScheduler.ScheduledQueryTaskArgs taskArgs =
                            (MdnsQueryScheduler.ScheduledQueryTaskArgs) msg.obj;
                    // QueryTask should be run immediately after being created (not be scheduled in
                    // advance). Because the result of "makeResponsesForResolve" depends on answers
                    // that were received before it is called, so to take into account all answers
                    // before sending the query, it needs to be called just before sending it.
                    final List<MdnsResponse> servicesToResolve = makeResponsesForResolve(socketKey);
                    final QueryTask queryTask = new QueryTask(taskArgs, servicesToResolve,
                            getAllDiscoverySubtypes(), needSendDiscoveryQueries(listeners));
                    executor.submit(queryTask);
                    break;
                }
                case EVENT_QUERY_RESULT: {
                    final QuerySentArguments sentResult = (QuerySentArguments) msg.obj;
                    // If a task is cancelled while the Executor is running it, EVENT_QUERY_RESULT
                    // will still be sent when it ends. So use session ID to check if this task
                    // should continue to schedule more.
                    if (sentResult.taskArgs.sessionId != currentSessionId) {
                        break;
                    }

                    if ((sentResult.transactionId != INVALID_TRANSACTION_ID)) {
                        for (int i = 0; i < listeners.size(); i++) {
                            listeners.keyAt(i).onDiscoveryQuerySent(
                                    sentResult.subTypes, sentResult.transactionId);
                        }
                    }

                    tryRemoveServiceAfterTtlExpires();

                    final long now = clock.elapsedRealtime();
                    lastSentTime = now;
                    final long minRemainingTtl = getMinRemainingTtl(now);
                    MdnsQueryScheduler.ScheduledQueryTaskArgs args =
                            mdnsQueryScheduler.scheduleNextRun(
                                    sentResult.taskArgs.config,
                                    minRemainingTtl,
                                    now,
                                    lastSentTime,
                                    sentResult.taskArgs.sessionId
                            );
                    dependencies.sendMessageDelayed(
                            handler,
                            handler.obtainMessage(EVENT_START_QUERYTASK, args),
                            calculateTimeToNextTask(args, now, sharedLog));
                    break;
                }
                default:
                    sharedLog.e("Unrecognized event " + msg.what);
                    break;
            }
        }
    }

    /**
     * Dependencies of MdnsServiceTypeClient, for injection in tests.
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
         * @see Handler#hasMessages(int)
         */
        public boolean hasMessages(@NonNull Handler handler, int what) {
            return handler.hasMessages(what);
        }

        /**
         * @see Handler#post(Runnable)
         */
        public void sendMessage(@NonNull Handler handler, @NonNull Message message) {
            handler.sendMessage(message);
        }
    }

    /**
     * Constructor of {@link MdnsServiceTypeClient}.
     *
     * @param socketClient Sends and receives mDNS packet.
     * @param executor         A {@link ScheduledExecutorService} used to schedule query tasks.
     */
    public MdnsServiceTypeClient(
            @NonNull String serviceType,
            @NonNull MdnsSocketClientBase socketClient,
            @NonNull ScheduledExecutorService executor,
            @NonNull SocketKey socketKey,
            @NonNull SharedLog sharedLog,
            @NonNull Looper looper,
            @NonNull MdnsServiceCache serviceCache) {
        this(serviceType, socketClient, executor, new Clock(), socketKey, sharedLog, looper,
                new Dependencies(), serviceCache);
    }

    @VisibleForTesting
    public MdnsServiceTypeClient(
            @NonNull String serviceType,
            @NonNull MdnsSocketClientBase socketClient,
            @NonNull ScheduledExecutorService executor,
            @NonNull Clock clock,
            @NonNull SocketKey socketKey,
            @NonNull SharedLog sharedLog,
            @NonNull Looper looper,
            @NonNull Dependencies dependencies,
            @NonNull MdnsServiceCache serviceCache) {
        this.serviceType = serviceType;
        this.socketClient = socketClient;
        this.executor = executor;
        this.serviceTypeLabels = TextUtils.split(serviceType, "\\.");
        this.responseDecoder = new MdnsResponseDecoder(clock, serviceTypeLabels);
        this.clock = clock;
        this.socketKey = socketKey;
        this.sharedLog = sharedLog;
        this.handler = new QueryTaskHandler(looper);
        this.dependencies = dependencies;
        this.serviceCache = serviceCache;
        this.mdnsQueryScheduler = new MdnsQueryScheduler();
        this.cacheKey = new MdnsServiceCache.CacheKey(serviceType, socketKey);
    }

    /**
     * Do the cleanup of the MdnsServiceTypeClient
     */
    private void shutDown() {
        removeScheduledTask();
        mdnsQueryScheduler.cancelScheduledRun();
        serviceCache.unregisterServiceExpiredCallback(cacheKey);
    }

    private static MdnsServiceInfo buildMdnsServiceInfoFromResponse(
            @NonNull MdnsResponse response, @NonNull String[] serviceTypeLabels) {
        String[] hostName = null;
        int port = 0;
        if (response.hasServiceRecord()) {
            hostName = response.getServiceRecord().getServiceHost();
            port = response.getServiceRecord().getServicePort();
        }

        final List<String> ipv4Addresses = new ArrayList<>();
        final List<String> ipv6Addresses = new ArrayList<>();
        if (response.hasInet4AddressRecord()) {
            for (MdnsInetAddressRecord inetAddressRecord : response.getInet4AddressRecords()) {
                final Inet4Address inet4Address = inetAddressRecord.getInet4Address();
                ipv4Addresses.add((inet4Address == null) ? null : inet4Address.getHostAddress());
            }
        }
        if (response.hasInet6AddressRecord()) {
            for (MdnsInetAddressRecord inetAddressRecord : response.getInet6AddressRecords()) {
                final Inet6Address inet6Address = inetAddressRecord.getInet6Address();
                ipv6Addresses.add((inet6Address == null) ? null : inet6Address.getHostAddress());
            }
        }
        String serviceInstanceName = response.getServiceInstanceName();
        if (serviceInstanceName == null) {
            throw new IllegalStateException(
                    "mDNS response must have non-null service instance name");
        }
        List<String> textStrings = null;
        List<MdnsServiceInfo.TextEntry> textEntries = null;
        if (response.hasTextRecord()) {
            textStrings = response.getTextRecord().getStrings();
            textEntries = response.getTextRecord().getEntries();
        }
        Instant now = Instant.now();
        // TODO: Throw an error message if response doesn't have Inet6 or Inet4 address.
        return new MdnsServiceInfo(
                serviceInstanceName,
                serviceTypeLabels,
                response.getSubtypes(),
                hostName,
                port,
                ipv4Addresses,
                ipv6Addresses,
                textStrings,
                textEntries,
                response.getInterfaceIndex(),
                response.getNetwork(),
                now.plusMillis(response.getMinRemainingTtl(now.toEpochMilli())));
    }

    /**
     * Registers {@code listener} for receiving discovery event of mDNS service instances, and
     * starts
     * (or continue) to send mDNS queries periodically.
     *
     * @param listener      The {@link MdnsServiceBrowserListener} to register.
     * @param searchOptions {@link MdnsSearchOptions} contains the list of subtypes to discover.
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    public void startSendAndReceive(
            @NonNull MdnsServiceBrowserListener listener,
            @NonNull MdnsSearchOptions searchOptions) {
        ensureRunningOnHandlerThread(handler);
        this.searchOptions = searchOptions;
        boolean hadReply = false;
        final ListenerInfo existingInfo = listeners.get(listener);
        final ListenerInfo listenerInfo = new ListenerInfo(searchOptions, existingInfo);
        listeners.put(listener, listenerInfo);
        if (existingInfo == null) {
            for (MdnsResponse existingResponse : serviceCache.getCachedServices(cacheKey)) {
                if (!responseMatchesOptions(existingResponse, searchOptions)) continue;
                final MdnsServiceInfo info =
                        buildMdnsServiceInfoFromResponse(existingResponse, serviceTypeLabels);
                listener.onServiceNameDiscovered(info, true /* isServiceFromCache */);
                listenerInfo.setServiceDiscovered(info.getServiceInstanceName());
                if (existingResponse.isComplete()) {
                    listener.onServiceFound(info, true /* isServiceFromCache */);
                    hadReply = true;
                }
            }
        }
        // Remove the next scheduled periodical task.
        removeScheduledTask();
        mdnsQueryScheduler.cancelScheduledRun();
        // Keep tracking the ScheduledFuture for the task so we can cancel it if caller is not
        // interested anymore.
        final QueryTaskConfig taskConfig = new QueryTaskConfig(
                searchOptions.getQueryMode(),
                searchOptions.onlyUseIpv6OnIpv6OnlyNetworks(),
                searchOptions.numOfQueriesBeforeBackoff(),
                socketKey);
        final long now = clock.elapsedRealtime();
        if (lastSentTime == 0) {
            lastSentTime = now;
        }
        final long minRemainingTtl = getMinRemainingTtl(now);
        if (hadReply) {
            MdnsQueryScheduler.ScheduledQueryTaskArgs args =
                    mdnsQueryScheduler.scheduleNextRun(
                            taskConfig,
                            minRemainingTtl,
                            now,
                            lastSentTime,
                            currentSessionId
                    );
            dependencies.sendMessageDelayed(
                    handler,
                    handler.obtainMessage(EVENT_START_QUERYTASK, args),
                    calculateTimeToNextTask(args, now, sharedLog));
        } else {
            final List<MdnsResponse> servicesToResolve = makeResponsesForResolve(socketKey);
            final QueryTask queryTask = new QueryTask(
                    mdnsQueryScheduler.scheduleFirstRun(taskConfig, now,
                            minRemainingTtl, currentSessionId), servicesToResolve,
                    getAllDiscoverySubtypes(), needSendDiscoveryQueries(listeners));
            executor.submit(queryTask);
        }

        serviceCache.registerServiceExpiredCallback(cacheKey, serviceExpiredCallback);
    }

    private Set<String> getAllDiscoverySubtypes() {
        final Set<String> subtypes = MdnsUtils.newSet();
        for (int i = 0; i < listeners.size(); i++) {
            final MdnsSearchOptions listenerOptions = listeners.valueAt(i).searchOptions;
            subtypes.addAll(listenerOptions.getSubtypes());
        }
        return subtypes;
    }

    /**
     * Get the executor service.
     */
    public ScheduledExecutorService getExecutor() {
        return executor;
    }

    private void removeScheduledTask() {
        dependencies.removeMessages(handler, EVENT_START_QUERYTASK);
        sharedLog.log("Remove EVENT_START_QUERYTASK"
                + ", current session: " + currentSessionId);
        ++currentSessionId;
    }

    private boolean responseMatchesOptions(@NonNull MdnsResponse response,
            @NonNull MdnsSearchOptions options) {
        final boolean matchesInstanceName = options.getResolveInstanceName() == null
                // DNS is case-insensitive, so ignore case in the comparison
                || MdnsUtils.equalsIgnoreDnsCase(options.getResolveInstanceName(),
                response.getServiceInstanceName());

        // If discovery is requiring some subtypes, the response must have one that matches a
        // requested one.
        final List<String> responseSubtypes = response.getSubtypes() == null
                ? Collections.emptyList() : response.getSubtypes();
        final boolean matchesSubtype = options.getSubtypes().size() == 0
                || CollectionUtils.any(options.getSubtypes(), requiredSub ->
                CollectionUtils.any(responseSubtypes, actualSub ->
                        MdnsUtils.equalsIgnoreDnsCase(
                                MdnsConstants.SUBTYPE_PREFIX + requiredSub, actualSub)));

        return matchesInstanceName && matchesSubtype;
    }

    /**
     * Unregisters {@code listener} from receiving discovery event of mDNS service instances.
     *
     * @param listener The {@link MdnsServiceBrowserListener} to unregister.
     * @return {@code true} if no listener is registered with this client after unregistering {@code
     * listener}. Otherwise returns {@code false}.
     */
    public boolean stopSendAndReceive(@NonNull MdnsServiceBrowserListener listener) {
        ensureRunningOnHandlerThread(handler);
        if (listeners.remove(listener) == null) {
            return listeners.isEmpty();
        }
        if (listeners.isEmpty()) {
            shutDown();
        }
        return listeners.isEmpty();
    }

    /**
     * Process an incoming response packet.
     */
    public synchronized void processResponse(@NonNull MdnsPacket packet,
            @NonNull SocketKey socketKey) {
        ensureRunningOnHandlerThread(handler);
        // Augment the list of current known responses, and generated responses for resolve
        // requests if there is no known response
        final List<MdnsResponse> cachedList = serviceCache.getCachedServices(cacheKey);
        final List<MdnsResponse> currentList = new ArrayList<>(cachedList);
        List<MdnsResponse> additionalResponses = makeResponsesForResolve(socketKey);
        for (MdnsResponse additionalResponse : additionalResponses) {
            if (findMatchedResponse(
                    cachedList, additionalResponse.getServiceInstanceName()) == null) {
                currentList.add(additionalResponse);
            }
        }
        final Pair<Set<MdnsResponse>, ArrayList<MdnsResponse>> augmentedResult =
                responseDecoder.augmentResponses(packet, currentList,
                        socketKey.getInterfaceIndex(), socketKey.getNetwork());

        final Set<MdnsResponse> modifiedResponse = augmentedResult.first;
        final ArrayList<MdnsResponse> allResponses = augmentedResult.second;

        for (MdnsResponse response : allResponses) {
            final String serviceInstanceName = response.getServiceInstanceName();
            if (modifiedResponse.contains(response)) {
                if (response.isGoodbye()) {
                    onGoodbyeReceived(serviceInstanceName);
                } else {
                    onResponseModified(response);
                }
            } else if (findMatchedResponse(cachedList, serviceInstanceName) != null) {
                // If the response is not modified and already in the cache. The cache will
                // need to be updated to refresh the last receipt time.
                serviceCache.addOrUpdateService(cacheKey, response);
            }
        }
        if (dependencies.hasMessages(handler, EVENT_START_QUERYTASK)) {
            final long now = clock.elapsedRealtime();
            final long minRemainingTtl = getMinRemainingTtl(now);
            MdnsQueryScheduler.ScheduledQueryTaskArgs args =
                    mdnsQueryScheduler.maybeRescheduleCurrentRun(now, minRemainingTtl,
                            lastSentTime, currentSessionId + 1);
            if (args != null) {
                removeScheduledTask();
                dependencies.sendMessageDelayed(
                        handler,
                        handler.obtainMessage(EVENT_START_QUERYTASK, args),
                        calculateTimeToNextTask(args, now, sharedLog));
            }
        }
    }

    public synchronized void onFailedToParseMdnsResponse(int receivedPacketNumber, int errorCode) {
        ensureRunningOnHandlerThread(handler);
        for (int i = 0; i < listeners.size(); i++) {
            listeners.keyAt(i).onFailedToParseMdnsResponse(receivedPacketNumber, errorCode);
        }
    }

    private void notifyRemovedServiceToListeners(@NonNull MdnsResponse response,
            @NonNull String message) {
        for (int i = 0; i < listeners.size(); i++) {
            if (!responseMatchesOptions(response, listeners.valueAt(i).searchOptions)) continue;
            final MdnsServiceBrowserListener listener = listeners.keyAt(i);
            if (response.getServiceInstanceName() != null) {
                listeners.valueAt(i).unsetServiceDiscovered(response.getServiceInstanceName());
                final MdnsServiceInfo serviceInfo = buildMdnsServiceInfoFromResponse(
                        response, serviceTypeLabels);
                if (response.isComplete()) {
                    sharedLog.log(message + ". onServiceRemoved: " + serviceInfo);
                    listener.onServiceRemoved(serviceInfo);
                }
                sharedLog.log(message + ". onServiceNameRemoved: " + serviceInfo);
                listener.onServiceNameRemoved(serviceInfo);
            }
        }
    }

    /** Notify all services are removed because the socket is destroyed. */
    public void notifySocketDestroyed() {
        ensureRunningOnHandlerThread(handler);
        for (MdnsResponse response : serviceCache.getCachedServices(cacheKey)) {
            final String name = response.getServiceInstanceName();
            if (name == null) continue;
            notifyRemovedServiceToListeners(response, "Socket destroyed");
        }
        shutDown();
    }

    private void onResponseModified(@NonNull MdnsResponse response) {
        final String serviceInstanceName = response.getServiceInstanceName();
        final MdnsResponse currentResponse =
                serviceCache.getCachedService(serviceInstanceName, cacheKey);

        final boolean newInCache = currentResponse == null;
        boolean serviceBecomesComplete = false;
        if (newInCache) {
            if (serviceInstanceName != null) {
                serviceCache.addOrUpdateService(cacheKey, response);
            }
        } else {
            boolean before = currentResponse.isComplete();
            serviceCache.addOrUpdateService(cacheKey, response);
            boolean after = response.isComplete();
            serviceBecomesComplete = !before && after;
        }
        sharedLog.i(String.format(
                "Handling response from service: %s, newInCache: %b, serviceBecomesComplete:"
                        + " %b, responseIsComplete: %b",
                serviceInstanceName, newInCache, serviceBecomesComplete,
                response.isComplete()));
        MdnsServiceInfo serviceInfo =
                buildMdnsServiceInfoFromResponse(response, serviceTypeLabels);

        for (int i = 0; i < listeners.size(); i++) {
            // If a service stops matching the options (currently can only happen if it loses a
            // subtype), service lost callbacks should also be sent; this is not done today as
            // only expiration of SRV records is used, not PTR records used for subtypes, so
            // services never lose PTR record subtypes.
            if (!responseMatchesOptions(response, listeners.valueAt(i).searchOptions)) continue;
            final MdnsServiceBrowserListener listener = listeners.keyAt(i);
            final ListenerInfo listenerInfo = listeners.valueAt(i);
            final boolean newServiceFound = listenerInfo.setServiceDiscovered(serviceInstanceName);
            if (newServiceFound) {
                sharedLog.log("onServiceNameDiscovered: " + serviceInfo);
                listener.onServiceNameDiscovered(serviceInfo, false /* isServiceFromCache */);
            }

            if (response.isComplete()) {
                if (newServiceFound || serviceBecomesComplete) {
                    sharedLog.log("onServiceFound: " + serviceInfo);
                    listener.onServiceFound(serviceInfo, false /* isServiceFromCache */);
                } else {
                    sharedLog.log("onServiceUpdated: " + serviceInfo);
                    listener.onServiceUpdated(serviceInfo);
                }
            }
        }
    }

    private void onGoodbyeReceived(@Nullable String serviceInstanceName) {
        final MdnsResponse response =
                serviceCache.removeService(serviceInstanceName, cacheKey);
        if (response == null) {
            return;
        }
        notifyRemovedServiceToListeners(response, "Goodbye received");
    }

    private boolean shouldRemoveServiceAfterTtlExpires() {
        if (removeServiceAfterTtlExpires) {
            return true;
        }
        return searchOptions != null && searchOptions.removeExpiredService();
    }

    @VisibleForTesting
    MdnsPacketWriter createMdnsPacketWriter() {
        return new MdnsPacketWriter(DEFAULT_MTU);
    }

    private List<MdnsResponse> makeResponsesForResolve(@NonNull SocketKey socketKey) {
        final List<MdnsResponse> resolveResponses = new ArrayList<>();
        for (int i = 0; i < listeners.size(); i++) {
            final String resolveName = listeners.valueAt(i).searchOptions.getResolveInstanceName();
            if (resolveName == null) {
                continue;
            }
            if (CollectionUtils.any(resolveResponses,
                    r -> MdnsUtils.equalsIgnoreDnsCase(resolveName, r.getServiceInstanceName()))) {
                continue;
            }
            MdnsResponse knownResponse =
                    serviceCache.getCachedService(resolveName, cacheKey);
            if (knownResponse == null) {
                final ArrayList<String> instanceFullName = new ArrayList<>(
                        serviceTypeLabels.length + 1);
                instanceFullName.add(resolveName);
                instanceFullName.addAll(Arrays.asList(serviceTypeLabels));
                knownResponse = new MdnsResponse(
                        0L /* lastUpdateTime */, instanceFullName.toArray(new String[0]),
                        socketKey.getInterfaceIndex(), socketKey.getNetwork());
            }
            resolveResponses.add(knownResponse);
        }
        return resolveResponses;
    }

    private static boolean needSendDiscoveryQueries(
            @NonNull ArrayMap<MdnsServiceBrowserListener, ListenerInfo> listeners) {
        // Note iterators are discouraged on ArrayMap as per its documentation
        for (int i = 0; i < listeners.size(); i++) {
            if (listeners.valueAt(i).searchOptions.getResolveInstanceName() == null) {
                return true;
            }
        }
        return false;
    }

    private void tryRemoveServiceAfterTtlExpires() {
        if (!shouldRemoveServiceAfterTtlExpires()) return;

        final Iterator<MdnsResponse> iter = serviceCache.getCachedServices(cacheKey).iterator();
        while (iter.hasNext()) {
            MdnsResponse existingResponse = iter.next();
            if (existingResponse.hasServiceRecord()
                    && existingResponse.getServiceRecord()
                    .getRemainingTTL(clock.elapsedRealtime()) == 0) {
                serviceCache.removeService(existingResponse.getServiceInstanceName(), cacheKey);
                notifyRemovedServiceToListeners(existingResponse, "TTL expired");
            }
        }
    }

    private static class QuerySentArguments {
        private final int transactionId;
        private final List<String> subTypes = new ArrayList<>();
        private final MdnsQueryScheduler.ScheduledQueryTaskArgs taskArgs;

        QuerySentArguments(int transactionId, @NonNull List<String> subTypes,
                @NonNull MdnsQueryScheduler.ScheduledQueryTaskArgs taskArgs) {
            this.transactionId = transactionId;
            this.subTypes.addAll(subTypes);
            this.taskArgs = taskArgs;
        }
    }

    // A FutureTask that enqueues a single query, and schedule a new FutureTask for the next task.
    private class QueryTask implements Runnable {
        private final MdnsQueryScheduler.ScheduledQueryTaskArgs taskArgs;
        private final List<MdnsResponse> servicesToResolve = new ArrayList<>();
        private final List<String> subtypes = new ArrayList<>();
        private final boolean sendDiscoveryQueries;
        QueryTask(@NonNull MdnsQueryScheduler.ScheduledQueryTaskArgs taskArgs,
                @NonNull Collection<MdnsResponse> servicesToResolve,
                @NonNull Collection<String> subtypes,
                boolean sendDiscoveryQueries) {
            this.taskArgs = taskArgs;
            this.servicesToResolve.addAll(servicesToResolve);
            this.subtypes.addAll(subtypes);
            this.sendDiscoveryQueries = sendDiscoveryQueries;
        }

        @Override
        public void run() {
            Pair<Integer, List<String>> result;
            try {
                result =
                        new EnqueueMdnsQueryCallable(
                                socketClient,
                                createMdnsPacketWriter(),
                                serviceType,
                                subtypes,
                                taskArgs.config.expectUnicastResponse,
                                taskArgs.config.transactionId,
                                taskArgs.config.socketKey,
                                taskArgs.config.onlyUseIpv6OnIpv6OnlyNetworks,
                                sendDiscoveryQueries,
                                servicesToResolve,
                                clock,
                                sharedLog)
                                .call();
            } catch (RuntimeException e) {
                sharedLog.e(String.format("Failed to run EnqueueMdnsQueryCallable for subtype: %s",
                        TextUtils.join(",", subtypes)), e);
                result = Pair.create(INVALID_TRANSACTION_ID, new ArrayList<>());
            }
            dependencies.sendMessage(
                    handler, handler.obtainMessage(EVENT_QUERY_RESULT,
                            new QuerySentArguments(result.first, result.second, taskArgs)));
        }
    }

    private long getMinRemainingTtl(long now) {
        long minRemainingTtl = Long.MAX_VALUE;
        for (MdnsResponse response : serviceCache.getCachedServices(cacheKey)) {
            if (!response.isComplete()) {
                continue;
            }
            long remainingTtl =
                    response.getServiceRecord().getRemainingTTL(now);
            // remainingTtl is <= 0 means the service expired.
            if (remainingTtl <= 0) {
                return 0;
            }
            if (remainingTtl < minRemainingTtl) {
                minRemainingTtl = remainingTtl;
            }
        }
        return minRemainingTtl == Long.MAX_VALUE ? 0 : minRemainingTtl;
    }

    private static long calculateTimeToNextTask(MdnsQueryScheduler.ScheduledQueryTaskArgs args,
            long now, SharedLog sharedLog) {
        long timeToNextTasksWithBackoffInMs = Math.max(args.timeToRun - now, 0);
        sharedLog.log(String.format("Next run: sessionId: %d, in %d ms",
                args.sessionId, timeToNextTasksWithBackoffInMs));
        return timeToNextTasksWithBackoffInMs;
    }

    /**
     * Dump ServiceTypeClient state.
     */
    public void dump(PrintWriter pw) {
        ensureRunningOnHandlerThread(handler);
        pw.println("ServiceTypeClient: Type{" + serviceType + "} " + socketKey + " with "
                + listeners.size() + " listeners.");
    }
}