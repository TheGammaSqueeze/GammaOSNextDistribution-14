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
 * limitations under the License
 */

package com.android.server.connectivity.mdns;

import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.net.Network;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Pair;

import com.android.net.module.util.SharedLog;
import com.android.server.connectivity.mdns.MdnsSocketClientBase.SocketCreationCallback;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;
import com.android.testutils.HandlerUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

/** Tests for {@link MdnsDiscoveryManager}. */
@DevSdkIgnoreRunner.MonitorThreadLeak
@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(SC_V2)
public class MdnsDiscoveryManagerTests {
    private static final long DEFAULT_TIMEOUT = 2000L;
    private static final String SERVICE_TYPE_1 = "_googlecast._tcp.local";
    private static final String SERVICE_TYPE_2 = "_test._tcp.local";
    private static final Network NETWORK_1 = Mockito.mock(Network.class);
    private static final Network NETWORK_2 = Mockito.mock(Network.class);
    private static final SocketKey SOCKET_KEY_NULL_NETWORK =
            new SocketKey(null /* network */, 999 /* interfaceIndex */);
    private static final SocketKey SOCKET_KEY_NETWORK_1 =
            new SocketKey(NETWORK_1, 998 /* interfaceIndex */);
    private static final SocketKey SOCKET_KEY_NETWORK_2 =
            new SocketKey(NETWORK_2, 997 /* interfaceIndex */);
    private static final Pair<String, SocketKey> PER_SOCKET_SERVICE_TYPE_1_NULL_NETWORK =
            Pair.create(SERVICE_TYPE_1, SOCKET_KEY_NULL_NETWORK);
    private static final Pair<String, SocketKey> PER_SOCKET_SERVICE_TYPE_2_NULL_NETWORK =
            Pair.create(SERVICE_TYPE_2, SOCKET_KEY_NULL_NETWORK);
    private static final Pair<String, SocketKey> PER_SOCKET_SERVICE_TYPE_1_NETWORK_1 =
            Pair.create(SERVICE_TYPE_1, SOCKET_KEY_NETWORK_1);
    private static final Pair<String, SocketKey> PER_SOCKET_SERVICE_TYPE_2_NETWORK_1 =
            Pair.create(SERVICE_TYPE_2, SOCKET_KEY_NETWORK_1);
    private static final Pair<String, SocketKey> PER_SOCKET_SERVICE_TYPE_2_NETWORK_2 =
            Pair.create(SERVICE_TYPE_2, SOCKET_KEY_NETWORK_2);
    @Mock private ExecutorProvider executorProvider;
    @Mock private ScheduledExecutorService mockExecutorService;
    @Mock private MdnsSocketClientBase socketClient;
    @Mock private MdnsServiceTypeClient mockServiceTypeClientType1NullNetwork;
    @Mock private MdnsServiceTypeClient mockServiceTypeClientType1Network1;
    @Mock private MdnsServiceTypeClient mockServiceTypeClientType2NullNetwork;
    @Mock private MdnsServiceTypeClient mockServiceTypeClientType2Network1;
    @Mock private MdnsServiceTypeClient mockServiceTypeClientType2Network2;

    @Mock MdnsServiceBrowserListener mockListenerOne;
    @Mock MdnsServiceBrowserListener mockListenerTwo;
    @Mock SharedLog sharedLog;
    private MdnsDiscoveryManager discoveryManager;
    private HandlerThread thread;
    private Handler handler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        thread = new HandlerThread("MdnsDiscoveryManagerTests");
        thread.start();
        handler = new Handler(thread.getLooper());
        doReturn(thread.getLooper()).when(socketClient).getLooper();
        doReturn(true).when(socketClient).supportsRequestingSpecificNetworks();
        discoveryManager = new MdnsDiscoveryManager(executorProvider, socketClient,
                sharedLog, MdnsFeatureFlags.newBuilder().build()) {
                    @Override
                    MdnsServiceTypeClient createServiceTypeClient(@NonNull String serviceType,
                            @NonNull SocketKey socketKey) {
                        final Pair<String, SocketKey> perSocketServiceType =
                                Pair.create(serviceType, socketKey);
                        if (perSocketServiceType.equals(PER_SOCKET_SERVICE_TYPE_1_NULL_NETWORK)) {
                            return mockServiceTypeClientType1NullNetwork;
                        } else if (perSocketServiceType.equals(
                                PER_SOCKET_SERVICE_TYPE_1_NETWORK_1)) {
                            return mockServiceTypeClientType1Network1;
                        } else if (perSocketServiceType.equals(
                                PER_SOCKET_SERVICE_TYPE_2_NULL_NETWORK)) {
                            return mockServiceTypeClientType2NullNetwork;
                        } else if (perSocketServiceType.equals(
                                PER_SOCKET_SERVICE_TYPE_2_NETWORK_1)) {
                            return mockServiceTypeClientType2Network1;
                        } else if (perSocketServiceType.equals(
                                PER_SOCKET_SERVICE_TYPE_2_NETWORK_2)) {
                            return mockServiceTypeClientType2Network2;
                        }
                        return null;
                    }
                };
        doReturn(mockExecutorService).when(mockServiceTypeClientType1NullNetwork).getExecutor();
    }

    @After
    public void tearDown() throws Exception {
        if (thread != null) {
            thread.quitSafely();
            thread.join();
        }
    }

    private void runOnHandler(Runnable r) {
        handler.post(r);
        HandlerUtils.waitForIdle(handler, DEFAULT_TIMEOUT);
    }

    private SocketCreationCallback expectSocketCreationCallback(String serviceType,
            MdnsServiceBrowserListener listener, MdnsSearchOptions options) throws IOException {
        final ArgumentCaptor<SocketCreationCallback> callbackCaptor =
                ArgumentCaptor.forClass(SocketCreationCallback.class);
        runOnHandler(() -> discoveryManager.registerListener(serviceType, listener, options));
        verify(socketClient).startDiscovery();
        verify(socketClient).notifyNetworkRequested(
                eq(listener), eq(options.getNetwork()), callbackCaptor.capture());
        return callbackCaptor.getValue();
    }

    @Test
    public void registerListener_unregisterListener() throws IOException {
        final MdnsSearchOptions options =
                MdnsSearchOptions.newBuilder().setNetwork(null /* network */).build();
        final SocketCreationCallback callback = expectSocketCreationCallback(
                SERVICE_TYPE_1, mockListenerOne, options);
        runOnHandler(() -> callback.onSocketCreated(SOCKET_KEY_NULL_NETWORK));
        verify(mockServiceTypeClientType1NullNetwork).startSendAndReceive(mockListenerOne, options);

        when(mockServiceTypeClientType1NullNetwork.stopSendAndReceive(mockListenerOne))
                .thenReturn(true);
        runOnHandler(() -> discoveryManager.unregisterListener(SERVICE_TYPE_1, mockListenerOne));
        verify(executorProvider).shutdownExecutorService(mockExecutorService);
        verify(mockServiceTypeClientType1NullNetwork).stopSendAndReceive(mockListenerOne);
        verify(socketClient).stopDiscovery();
    }

    @Test
    public void onSocketDestroy_shutdownExecutorService() throws IOException {
        final MdnsSearchOptions options =
                MdnsSearchOptions.newBuilder().setNetwork(null /* network */).build();
        final SocketCreationCallback callback = expectSocketCreationCallback(
                SERVICE_TYPE_1, mockListenerOne, options);
        runOnHandler(() -> callback.onSocketCreated(SOCKET_KEY_NULL_NETWORK));
        verify(mockServiceTypeClientType1NullNetwork).startSendAndReceive(mockListenerOne, options);

        runOnHandler(() -> callback.onSocketDestroyed(SOCKET_KEY_NULL_NETWORK));
        verify(executorProvider).shutdownExecutorService(mockExecutorService);
    }

    @Test
    public void registerMultipleListeners() throws IOException {
        final MdnsSearchOptions options =
                MdnsSearchOptions.newBuilder().setNetwork(null /* network */).build();
        final SocketCreationCallback callback = expectSocketCreationCallback(
                SERVICE_TYPE_1, mockListenerOne, options);
        runOnHandler(() -> callback.onSocketCreated(SOCKET_KEY_NULL_NETWORK));
        verify(mockServiceTypeClientType1NullNetwork).startSendAndReceive(mockListenerOne, options);
        runOnHandler(() -> callback.onSocketCreated(SOCKET_KEY_NETWORK_1));
        verify(mockServiceTypeClientType1Network1).startSendAndReceive(mockListenerOne, options);

        final SocketCreationCallback callback2 = expectSocketCreationCallback(
                SERVICE_TYPE_2, mockListenerTwo, options);
        runOnHandler(() -> callback2.onSocketCreated(SOCKET_KEY_NULL_NETWORK));
        verify(mockServiceTypeClientType2NullNetwork).startSendAndReceive(mockListenerTwo, options);
        runOnHandler(() -> callback2.onSocketCreated(SOCKET_KEY_NETWORK_2));
        verify(mockServiceTypeClientType2Network2).startSendAndReceive(mockListenerTwo, options);
    }

    @Test
    public void onResponseReceived() throws IOException {
        final MdnsSearchOptions options1 =
                MdnsSearchOptions.newBuilder().setNetwork(null /* network */).build();
        final SocketCreationCallback callback = expectSocketCreationCallback(
                SERVICE_TYPE_1, mockListenerOne, options1);
        runOnHandler(() -> callback.onSocketCreated(SOCKET_KEY_NULL_NETWORK));
        verify(mockServiceTypeClientType1NullNetwork).startSendAndReceive(
                mockListenerOne, options1);
        runOnHandler(() -> callback.onSocketCreated(SOCKET_KEY_NETWORK_1));
        verify(mockServiceTypeClientType1Network1).startSendAndReceive(mockListenerOne, options1);

        final MdnsSearchOptions options2 =
                MdnsSearchOptions.newBuilder().setNetwork(NETWORK_2).build();
        final SocketCreationCallback callback2 = expectSocketCreationCallback(
                SERVICE_TYPE_2, mockListenerTwo, options2);
        runOnHandler(() -> callback2.onSocketCreated(SOCKET_KEY_NETWORK_2));
        verify(mockServiceTypeClientType2Network2).startSendAndReceive(mockListenerTwo, options2);

        final MdnsPacket responseForServiceTypeOne = createMdnsPacket(SERVICE_TYPE_1);
        runOnHandler(() -> discoveryManager.onResponseReceived(
                responseForServiceTypeOne, SOCKET_KEY_NULL_NETWORK));
        // Packets for network null are only processed by the ServiceTypeClient for network null
        verify(mockServiceTypeClientType1NullNetwork).processResponse(
                responseForServiceTypeOne, SOCKET_KEY_NULL_NETWORK);
        verify(mockServiceTypeClientType1Network1, never()).processResponse(any(), any());
        verify(mockServiceTypeClientType2Network2, never()).processResponse(any(), any());

        final MdnsPacket responseForServiceTypeTwo = createMdnsPacket(SERVICE_TYPE_2);
        runOnHandler(() -> discoveryManager.onResponseReceived(
                responseForServiceTypeTwo, SOCKET_KEY_NETWORK_1));
        verify(mockServiceTypeClientType1NullNetwork, never()).processResponse(any(),
                eq(SOCKET_KEY_NETWORK_1));
        verify(mockServiceTypeClientType1Network1).processResponse(
                responseForServiceTypeTwo, SOCKET_KEY_NETWORK_1);
        verify(mockServiceTypeClientType2Network2, never()).processResponse(any(),
                eq(SOCKET_KEY_NETWORK_1));

        final MdnsPacket responseForSubtype =
                createMdnsPacket("subtype._sub._googlecast._tcp.local");
        runOnHandler(() -> discoveryManager.onResponseReceived(
                responseForSubtype, SOCKET_KEY_NETWORK_2));
        verify(mockServiceTypeClientType1NullNetwork, never()).processResponse(any(),
                eq(SOCKET_KEY_NETWORK_2));
        verify(mockServiceTypeClientType1Network1, never()).processResponse(any(),
                eq(SOCKET_KEY_NETWORK_2));
        verify(mockServiceTypeClientType2Network2).processResponse(
                responseForSubtype, SOCKET_KEY_NETWORK_2);
    }

    @Test
    public void testSocketCreatedAndDestroyed() throws IOException {
        // Create a ServiceTypeClient for SERVICE_TYPE_1 and NETWORK_1
        final MdnsSearchOptions network1Options =
                MdnsSearchOptions.newBuilder().setNetwork(NETWORK_1).build();
        final SocketCreationCallback callback = expectSocketCreationCallback(
                SERVICE_TYPE_1, mockListenerOne, network1Options);
        runOnHandler(() -> callback.onSocketCreated(SOCKET_KEY_NETWORK_1));
        verify(mockServiceTypeClientType1Network1).startSendAndReceive(
                mockListenerOne, network1Options);

        // Create a ServiceTypeClient for SERVICE_TYPE_2 and NETWORK_1
        final SocketCreationCallback callback2 = expectSocketCreationCallback(
                SERVICE_TYPE_2, mockListenerTwo, network1Options);
        runOnHandler(() -> callback2.onSocketCreated(SOCKET_KEY_NETWORK_1));
        verify(mockServiceTypeClientType2Network1).startSendAndReceive(
                mockListenerTwo, network1Options);

        // Receive a response, it should be processed on both clients.
        final MdnsPacket response = createMdnsPacket(SERVICE_TYPE_1);
        runOnHandler(() -> discoveryManager.onResponseReceived(response, SOCKET_KEY_NETWORK_1));
        verify(mockServiceTypeClientType1Network1).processResponse(response, SOCKET_KEY_NETWORK_1);
        verify(mockServiceTypeClientType2Network1).processResponse(response, SOCKET_KEY_NETWORK_1);

        // The first callback receives a notification that the network has been destroyed,
        // mockServiceTypeClientOne1 should send service removed notifications and remove from the
        // list of clients.
        runOnHandler(() -> callback.onSocketDestroyed(SOCKET_KEY_NETWORK_1));
        verify(mockServiceTypeClientType1Network1).notifySocketDestroyed();

        // Receive a response again, it should be processed only on
        // mockServiceTypeClientType2Network1. Because the mockServiceTypeClientType1Network1 is
        // removed from the list of clients, it is no longer able to process responses.
        runOnHandler(() -> discoveryManager.onResponseReceived(response, SOCKET_KEY_NETWORK_1));
        // Still times(1) as a response was received once previously
        verify(mockServiceTypeClientType1Network1, times(1)).processResponse(
                response, SOCKET_KEY_NETWORK_1);
        verify(mockServiceTypeClientType2Network1, times(2)).processResponse(
                response, SOCKET_KEY_NETWORK_1);

        // The client for NETWORK_1 receives the callback that the NETWORK_2 has been destroyed,
        // mockServiceTypeClientTwo2 shouldn't send any notifications.
        runOnHandler(() -> callback2.onSocketDestroyed(SOCKET_KEY_NETWORK_2));
        verify(mockServiceTypeClientType2Network1, never()).notifySocketDestroyed();

        // Receive a response again, mockServiceTypeClientType2Network1 is still in the list of
        // clients, it's still able to process responses.
        runOnHandler(() -> discoveryManager.onResponseReceived(response, SOCKET_KEY_NETWORK_1));
        verify(mockServiceTypeClientType1Network1, times(1)).processResponse(
                response, SOCKET_KEY_NETWORK_1);
        verify(mockServiceTypeClientType2Network1, times(3)).processResponse(
                response, SOCKET_KEY_NETWORK_1);
    }

    @Test
    public void testUnregisterListenerAfterSocketDestroyed() throws IOException {
        // Create a ServiceTypeClient for SERVICE_TYPE_1
        final MdnsSearchOptions network1Options =
                MdnsSearchOptions.newBuilder().setNetwork(null /* network */).build();
        final SocketCreationCallback callback = expectSocketCreationCallback(
                SERVICE_TYPE_1, mockListenerOne, network1Options);
        runOnHandler(() -> callback.onSocketCreated(SOCKET_KEY_NULL_NETWORK));
        verify(mockServiceTypeClientType1NullNetwork).startSendAndReceive(
                mockListenerOne, network1Options);

        // Receive a response, it should be processed on the client.
        final MdnsPacket response = createMdnsPacket(SERVICE_TYPE_1);
        final int ifIndex = 1;
        runOnHandler(() -> discoveryManager.onResponseReceived(response, SOCKET_KEY_NULL_NETWORK));
        verify(mockServiceTypeClientType1NullNetwork).processResponse(
                response, SOCKET_KEY_NULL_NETWORK);

        runOnHandler(() -> callback.onSocketDestroyed(SOCKET_KEY_NULL_NETWORK));
        verify(mockServiceTypeClientType1NullNetwork).notifySocketDestroyed();

        // Receive a response again, it should not be processed.
        runOnHandler(() -> discoveryManager.onResponseReceived(response, SOCKET_KEY_NULL_NETWORK));
        // Still times(1) as a response was received once previously
        verify(mockServiceTypeClientType1NullNetwork, times(1)).processResponse(
                response, SOCKET_KEY_NULL_NETWORK);

        // Unregister the listener, notifyNetworkUnrequested should be called but other stop methods
        // won't be call because the service type client was unregistered and destroyed. But those
        // cleanups were done in notifySocketDestroyed when the socket was destroyed.
        runOnHandler(() -> discoveryManager.unregisterListener(SERVICE_TYPE_1, mockListenerOne));
        verify(socketClient).notifyNetworkUnrequested(mockListenerOne);
        verify(mockServiceTypeClientType1NullNetwork, never()).stopSendAndReceive(any());
        // The stopDiscovery() is only used by MdnsSocketClient, which doesn't send
        // onSocketDestroyed(). So the socket clients that send onSocketDestroyed() do not
        // need to call stopDiscovery().
        verify(socketClient, never()).stopDiscovery();
    }

    private MdnsPacket createMdnsPacket(String serviceType) {
        final String[] type = TextUtils.split(serviceType, "\\.");
        final ArrayList<String> name = new ArrayList<>(type.length + 1);
        name.add("TestName");
        name.addAll(Arrays.asList(type));
        return new MdnsPacket(0 /* flags */,
                Collections.emptyList() /* questions */,
                List.of(new MdnsPointerRecord(
                        type,
                        0L /* receiptTimeMillis */,
                        false /* cacheFlush */,
                        120000 /* ttlMillis */,
                        name.toArray(new String[0])
                        )) /* answers */,
                Collections.emptyList() /* authorityRecords */,
                Collections.emptyList() /* additionalRecords */);
    }
}