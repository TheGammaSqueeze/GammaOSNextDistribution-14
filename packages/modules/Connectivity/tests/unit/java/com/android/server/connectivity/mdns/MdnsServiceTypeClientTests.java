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

import static com.android.server.connectivity.mdns.MdnsSearchOptions.ACTIVE_QUERY_MODE;
import static com.android.server.connectivity.mdns.MdnsSearchOptions.AGGRESSIVE_QUERY_MODE;
import static com.android.server.connectivity.mdns.MdnsSearchOptions.PASSIVE_QUERY_MODE;
import static com.android.server.connectivity.mdns.MdnsServiceTypeClient.EVENT_START_QUERYTASK;
import static com.android.server.connectivity.mdns.QueryTaskConfig.INITIAL_AGGRESSIVE_TIME_BETWEEN_BURSTS_MS;
import static com.android.server.connectivity.mdns.QueryTaskConfig.MAX_TIME_BETWEEN_AGGRESSIVE_BURSTS_MS;
import static com.android.server.connectivity.mdns.QueryTaskConfig.TIME_BETWEEN_RETRANSMISSION_QUERIES_IN_BURST_MS;
import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.InetAddresses;
import android.net.Network;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;

import com.android.net.module.util.CollectionUtils;
import com.android.net.module.util.SharedLog;
import com.android.server.connectivity.mdns.MdnsServiceInfo.TextEntry;
import com.android.server.connectivity.mdns.util.MdnsUtils;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;
import com.android.testutils.HandlerUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Tests for {@link MdnsServiceTypeClient}. */
@DevSdkIgnoreRunner.MonitorThreadLeak
@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(SC_V2)
public class MdnsServiceTypeClientTests {
    private static final int INTERFACE_INDEX = 999;
    private static final long DEFAULT_TIMEOUT = 2000L;
    private static final String SERVICE_TYPE = "_googlecast._tcp.local";
    private static final String SUBTYPE = "_subtype";
    private static final String[] SERVICE_TYPE_LABELS = TextUtils.split(SERVICE_TYPE, "\\.");
    private static final InetSocketAddress IPV4_ADDRESS = new InetSocketAddress(
            MdnsConstants.getMdnsIPv4Address(), MdnsConstants.MDNS_PORT);
    private static final InetSocketAddress IPV6_ADDRESS = new InetSocketAddress(
            MdnsConstants.getMdnsIPv6Address(), MdnsConstants.MDNS_PORT);

    private static final long TEST_TTL = 120000L;
    private static final long TEST_ELAPSED_REALTIME = 123L;
    private static final long TEST_TIMEOUT_MS = 10_000L;

    @Mock
    private MdnsServiceBrowserListener mockListenerOne;
    @Mock
    private MdnsServiceBrowserListener mockListenerTwo;
    @Mock
    private MdnsPacketWriter mockPacketWriter;
    @Mock
    private MdnsMultinetworkSocketClient mockSocketClient;
    @Mock
    private Network mockNetwork;
    @Mock
    private MdnsUtils.Clock mockDecoderClock;
    @Mock
    private SharedLog mockSharedLog;
    @Mock
    private MdnsServiceTypeClient.Dependencies mockDeps;
    @Captor
    private ArgumentCaptor<MdnsServiceInfo> serviceInfoCaptor;

    private final byte[] buf = new byte[10];

    private DatagramPacket[] expectedIPv4Packets;
    private DatagramPacket[] expectedIPv6Packets;
    private FakeExecutor currentThreadExecutor = new FakeExecutor();

    private MdnsServiceTypeClient client;
    private SocketKey socketKey;
    private HandlerThread thread;
    private Handler handler;
    private MdnsServiceCache serviceCache;
    private long latestDelayMs = 0;
    private Message delayMessage = null;
    private Handler realHandler = null;

    @Before
    @SuppressWarnings("DoNotMock")
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        doReturn(TEST_ELAPSED_REALTIME).when(mockDecoderClock).elapsedRealtime();

        expectedIPv4Packets = new DatagramPacket[24];
        expectedIPv6Packets = new DatagramPacket[24];
        socketKey = new SocketKey(mockNetwork, INTERFACE_INDEX);

        for (int i = 0; i < expectedIPv4Packets.length; ++i) {
            expectedIPv4Packets[i] = new DatagramPacket(buf, 0 /* offset */, 5 /* length */,
                    MdnsConstants.getMdnsIPv4Address(), MdnsConstants.MDNS_PORT);
            expectedIPv6Packets[i] = new DatagramPacket(buf, 0 /* offset */, 5 /* length */,
                    MdnsConstants.getMdnsIPv6Address(), MdnsConstants.MDNS_PORT);
        }
        when(mockPacketWriter.getPacket(IPV4_ADDRESS))
                .thenReturn(expectedIPv4Packets[0])
                .thenReturn(expectedIPv4Packets[1])
                .thenReturn(expectedIPv4Packets[2])
                .thenReturn(expectedIPv4Packets[3])
                .thenReturn(expectedIPv4Packets[4])
                .thenReturn(expectedIPv4Packets[5])
                .thenReturn(expectedIPv4Packets[6])
                .thenReturn(expectedIPv4Packets[7])
                .thenReturn(expectedIPv4Packets[8])
                .thenReturn(expectedIPv4Packets[9])
                .thenReturn(expectedIPv4Packets[10])
                .thenReturn(expectedIPv4Packets[11])
                .thenReturn(expectedIPv4Packets[12])
                .thenReturn(expectedIPv4Packets[13])
                .thenReturn(expectedIPv4Packets[14])
                .thenReturn(expectedIPv4Packets[15])
                .thenReturn(expectedIPv4Packets[16])
                .thenReturn(expectedIPv4Packets[17])
                .thenReturn(expectedIPv4Packets[18])
                .thenReturn(expectedIPv4Packets[19])
                .thenReturn(expectedIPv4Packets[20])
                .thenReturn(expectedIPv4Packets[21])
                .thenReturn(expectedIPv4Packets[22])
                .thenReturn(expectedIPv4Packets[23]);

        when(mockPacketWriter.getPacket(IPV6_ADDRESS))
                .thenReturn(expectedIPv6Packets[0])
                .thenReturn(expectedIPv6Packets[1])
                .thenReturn(expectedIPv6Packets[2])
                .thenReturn(expectedIPv6Packets[3])
                .thenReturn(expectedIPv6Packets[4])
                .thenReturn(expectedIPv6Packets[5])
                .thenReturn(expectedIPv6Packets[6])
                .thenReturn(expectedIPv6Packets[7])
                .thenReturn(expectedIPv6Packets[8])
                .thenReturn(expectedIPv6Packets[9])
                .thenReturn(expectedIPv6Packets[10])
                .thenReturn(expectedIPv6Packets[11])
                .thenReturn(expectedIPv6Packets[12])
                .thenReturn(expectedIPv6Packets[13])
                .thenReturn(expectedIPv6Packets[14])
                .thenReturn(expectedIPv6Packets[15])
                .thenReturn(expectedIPv6Packets[16])
                .thenReturn(expectedIPv6Packets[17])
                .thenReturn(expectedIPv6Packets[18])
                .thenReturn(expectedIPv6Packets[19])
                .thenReturn(expectedIPv6Packets[20])
                .thenReturn(expectedIPv6Packets[21])
                .thenReturn(expectedIPv6Packets[22])
                .thenReturn(expectedIPv6Packets[23]);

        thread = new HandlerThread("MdnsServiceTypeClientTests");
        thread.start();
        handler = new Handler(thread.getLooper());
        serviceCache = new MdnsServiceCache(
                thread.getLooper(),
                MdnsFeatureFlags.newBuilder().setIsExpiredServicesRemovalEnabled(false).build(),
                mockDecoderClock);

        doAnswer(inv -> {
            latestDelayMs = 0;
            delayMessage = null;
            return true;
        }).when(mockDeps).removeMessages(any(Handler.class), eq(EVENT_START_QUERYTASK));

        doAnswer(inv -> {
            realHandler = (Handler) inv.getArguments()[0];
            delayMessage = (Message) inv.getArguments()[1];
            latestDelayMs = (long) inv.getArguments()[2];
            return true;
        }).when(mockDeps).sendMessageDelayed(any(Handler.class), any(Message.class), anyLong());

        doAnswer(inv -> {
            final Handler handler = (Handler) inv.getArguments()[0];
            final Message message = (Message) inv.getArguments()[1];
            runOnHandler(() -> handler.dispatchMessage(message));
            return true;
        }).when(mockDeps).sendMessage(any(Handler.class), any(Message.class));

        client = makeMdnsServiceTypeClient(mockPacketWriter);
    }

    private MdnsServiceTypeClient makeMdnsServiceTypeClient(
            @Nullable MdnsPacketWriter packetWriter) {
        return new MdnsServiceTypeClient(SERVICE_TYPE, mockSocketClient, currentThreadExecutor,
                mockDecoderClock, socketKey, mockSharedLog, thread.getLooper(), mockDeps,
                serviceCache) {
            @Override
            MdnsPacketWriter createMdnsPacketWriter() {
                if (packetWriter == null) {
                    return super.createMdnsPacketWriter();
                }
                return packetWriter;
            }
        };
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

    private void startSendAndReceive(MdnsServiceBrowserListener listener,
            MdnsSearchOptions searchOptions) {
        runOnHandler(() -> client.startSendAndReceive(listener, searchOptions));
    }

    private void processResponse(MdnsPacket packet, SocketKey socketKey) {
        runOnHandler(() -> client.processResponse(packet, socketKey));
    }

    private void stopSendAndReceive(MdnsServiceBrowserListener listener) {
        runOnHandler(() -> client.stopSendAndReceive(listener));
    }

    private void notifySocketDestroyed() {
        runOnHandler(() -> client.notifySocketDestroyed());
    }

    private void dispatchMessage() {
        runOnHandler(() -> realHandler.dispatchMessage(delayMessage));
        delayMessage = null;
    }

    @Test
    public void sendQueries_activeScanMode() {
        MdnsSearchOptions searchOptions = MdnsSearchOptions.newBuilder()
                .addSubtype(SUBTYPE).setQueryMode(ACTIVE_QUERY_MODE).build();
        startSendAndReceive(mockListenerOne, searchOptions);
        // Always try to remove the task.
        verify(mockDeps, times(1)).removeMessages(any(), eq(EVENT_START_QUERYTASK));

        // First burst, 3 queries.
        verifyAndSendQuery(0, 0, /* expectsUnicastResponse= */ true);
        verifyAndSendQuery(
                1, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        verifyAndSendQuery(
                2, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        // Second burst will be sent after initialTimeBetweenBurstsMs, 3 queries.
        verifyAndSendQuery(
                3, MdnsConfigs.initialTimeBetweenBurstsMs(), /* expectsUnicastResponse= */ false);
        verifyAndSendQuery(
                4, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        verifyAndSendQuery(
                5, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        // Third burst will be sent after initialTimeBetweenBurstsMs * 2, 3 queries.
        verifyAndSendQuery(
                6, MdnsConfigs.initialTimeBetweenBurstsMs() * 2, /* expectsUnicastResponse= */
                false);
        verifyAndSendQuery(
                7, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        verifyAndSendQuery(
                8, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        // Forth burst will be sent after initialTimeBetweenBurstsMs * 4, 3 queries.
        verifyAndSendQuery(
                9, MdnsConfigs.initialTimeBetweenBurstsMs() * 4, /* expectsUnicastResponse= */
                false);
        verifyAndSendQuery(
                10, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        verifyAndSendQuery(
                11, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        // Fifth burst will be sent after timeBetweenBurstsMs, 3 queries.
        verifyAndSendQuery(12, MdnsConfigs.timeBetweenBurstsMs(), /* expectsUnicastResponse= */
                false);
        verifyAndSendQuery(
                13, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        verifyAndSendQuery(
                14, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        // Verify that Task is not removed before stopSendAndReceive was called.
        verify(mockDeps, times(1)).removeMessages(any(), eq(EVENT_START_QUERYTASK));

        // Stop sending packets.
        stopSendAndReceive(mockListenerOne);
        verify(mockDeps, times(2)).removeMessages(any(), eq(EVENT_START_QUERYTASK));
    }

    @Test
    public void sendQueries_reentry_activeScanMode() {
        MdnsSearchOptions searchOptions = MdnsSearchOptions.newBuilder()
                .addSubtype(SUBTYPE).setQueryMode(ACTIVE_QUERY_MODE).build();
        startSendAndReceive(mockListenerOne, searchOptions);
        // Always try to remove the task.
        verify(mockDeps, times(1)).removeMessages(any(), eq(EVENT_START_QUERYTASK));

        // First burst, first query is sent.
        verifyAndSendQuery(0, 0, /* expectsUnicastResponse= */ true);

        // After the first query is sent, change the subtypes, and restart.
        searchOptions =
                MdnsSearchOptions.newBuilder()
                        .addSubtype(SUBTYPE)
                        .addSubtype("_subtype2")
                        .setQueryMode(ACTIVE_QUERY_MODE)
                        .build();
        startSendAndReceive(mockListenerOne, searchOptions);
        // The previous scheduled task should be canceled.
        verify(mockDeps, times(2)).removeMessages(any(), eq(EVENT_START_QUERYTASK));

        // Queries should continue to be sent.
        verifyAndSendQuery(1, 0, /* expectsUnicastResponse= */ true);
        verifyAndSendQuery(
                2, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        verifyAndSendQuery(
                3, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);

        // Stop sending packets.
        stopSendAndReceive(mockListenerOne);
        verify(mockDeps, times(3)).removeMessages(any(), eq(EVENT_START_QUERYTASK));
    }

    @Test
    public void sendQueries_passiveScanMode() {
        MdnsSearchOptions searchOptions = MdnsSearchOptions.newBuilder()
                .addSubtype(SUBTYPE).setQueryMode(PASSIVE_QUERY_MODE).build();
        startSendAndReceive(mockListenerOne, searchOptions);
        // Always try to remove the task.
        verify(mockDeps, times(1)).removeMessages(any(), eq(EVENT_START_QUERYTASK));

        // First burst, 3 query.
        verifyAndSendQuery(0, 0, /* expectsUnicastResponse= */ true);
        verifyAndSendQuery(
                1, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        verifyAndSendQuery(
                2, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        // Second burst will be sent after timeBetweenBurstsMs, 1 query.
        verifyAndSendQuery(3, MdnsConfigs.timeBetweenBurstsMs(), /* expectsUnicastResponse= */
                false);
        // Third burst will be sent after timeBetweenBurstsMs, 1 query.
        verifyAndSendQuery(4, MdnsConfigs.timeBetweenBurstsMs(), /* expectsUnicastResponse= */
                false);

        // Stop sending packets.
        stopSendAndReceive(mockListenerOne);
        verify(mockDeps, times(2)).removeMessages(any(), eq(EVENT_START_QUERYTASK));
    }

    @Test
    public void sendQueries_activeScanWithQueryBackoff() {
        MdnsSearchOptions searchOptions =
                MdnsSearchOptions.newBuilder()
                        .addSubtype(SUBTYPE)
                        .setQueryMode(ACTIVE_QUERY_MODE)
                        .setNumOfQueriesBeforeBackoff(11).build();
        startSendAndReceive(mockListenerOne, searchOptions);
        // Always try to remove the task.
        verify(mockDeps, times(1)).removeMessages(any(), eq(EVENT_START_QUERYTASK));

        // First burst, 3 queries.
        verifyAndSendQuery(0, 0, /* expectsUnicastResponse= */ true);
        verifyAndSendQuery(
                1, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        verifyAndSendQuery(
                2, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        // Second burst will be sent after initialTimeBetweenBurstsMs, 3 queries.
        verifyAndSendQuery(
                3, MdnsConfigs.initialTimeBetweenBurstsMs(), /* expectsUnicastResponse= */ false);
        verifyAndSendQuery(
                4, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        verifyAndSendQuery(
                5, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        // Third burst will be sent after initialTimeBetweenBurstsMs * 2, 3 queries.
        verifyAndSendQuery(
                6, MdnsConfigs.initialTimeBetweenBurstsMs() * 2, /* expectsUnicastResponse= */
                false);
        verifyAndSendQuery(
                7, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        verifyAndSendQuery(
                8, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        // Forth burst will be sent after initialTimeBetweenBurstsMs * 4, 3 queries.
        verifyAndSendQuery(
                9, MdnsConfigs.initialTimeBetweenBurstsMs() * 4, /* expectsUnicastResponse= */
                false);
        verifyAndSendQuery(
                10, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        verifyAndSendQuery(
                11, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        // In backoff mode, the current scheduled task will be canceled and reschedule if the
        // 0.8 * smallestRemainingTtl is larger than time to next run.
        long currentTime = TEST_TTL / 2 + TEST_ELAPSED_REALTIME;
        doReturn(currentTime).when(mockDecoderClock).elapsedRealtime();
        doReturn(true).when(mockDeps).hasMessages(any(), eq(EVENT_START_QUERYTASK));
        processResponse(createResponse(
                "service-instance-1", "192.0.2.123", 5353,
                SERVICE_TYPE_LABELS,
                Collections.emptyMap(), TEST_TTL), socketKey);
        verify(mockDeps, times(2)).removeMessages(any(), eq(EVENT_START_QUERYTASK));
        assertNotNull(delayMessage);
        verifyAndSendQuery(12 /* index */, (long) (TEST_TTL / 2 * 0.8) /* timeInMs */,
                false /* expectsUnicastResponse */, true /* multipleSocketDiscovery */,
                14 /* scheduledCount */);
        currentTime += (long) (TEST_TTL / 2 * 0.8);
        doReturn(currentTime).when(mockDecoderClock).elapsedRealtime();
        verifyAndSendQuery(13 /* index */, MdnsConfigs.timeBetweenQueriesInBurstMs(),
                false /* expectsUnicastResponse */, true /* multipleSocketDiscovery */,
                15 /* scheduledCount */);
    }

    @Test
    public void sendQueries_passiveScanWithQueryBackoff() {
        MdnsSearchOptions searchOptions =
                MdnsSearchOptions.newBuilder()
                        .addSubtype(SUBTYPE)
                        .setQueryMode(PASSIVE_QUERY_MODE)
                        .setNumOfQueriesBeforeBackoff(3).build();
        startSendAndReceive(mockListenerOne, searchOptions);
        // Always try to remove the task.
        verify(mockDeps, times(1)).removeMessages(any(), eq(EVENT_START_QUERYTASK));

        verifyAndSendQuery(0 /* index */, 0 /* timeInMs */, true /* expectsUnicastResponse */,
                true /* multipleSocketDiscovery */, 1 /* scheduledCount */);
        verifyAndSendQuery(1 /* index */, MdnsConfigs.timeBetweenQueriesInBurstMs(),
                false /* expectsUnicastResponse */, true /* multipleSocketDiscovery */,
                2 /* scheduledCount */);
        verifyAndSendQuery(2 /* index */, MdnsConfigs.timeBetweenQueriesInBurstMs(),
                false /* expectsUnicastResponse */, true /* multipleSocketDiscovery */,
                3 /* scheduledCount */);
        verifyAndSendQuery(3 /* index */, MdnsConfigs.timeBetweenBurstsMs(),
                false /* expectsUnicastResponse */, true /* multipleSocketDiscovery */,
                4 /* scheduledCount */);

        // In backoff mode, the current scheduled task will be canceled and reschedule if the
        // 0.8 * smallestRemainingTtl is larger than time to next run.
        doReturn(TEST_ELAPSED_REALTIME + 20000).when(mockDecoderClock).elapsedRealtime();
        doReturn(true).when(mockDeps).hasMessages(any(), eq(EVENT_START_QUERYTASK));
        processResponse(createResponse(
                "service-instance-1", "192.0.2.123", 5353,
                SERVICE_TYPE_LABELS,
                Collections.emptyMap(), TEST_TTL), socketKey);
        verify(mockDeps, times(2)).removeMessages(any(), eq(EVENT_START_QUERYTASK));
        assertNotNull(delayMessage);
        verifyAndSendQuery(4 /* index */, 80000 /* timeInMs */, false /* expectsUnicastResponse */,
                true /* multipleSocketDiscovery */, 6 /* scheduledCount */);
        // Next run should also be scheduled in 0.8 * smallestRemainingTtl
        verifyAndSendQuery(5 /* index */, 80000 /* timeInMs */, false /* expectsUnicastResponse */,
                true /* multipleSocketDiscovery */, 7 /* scheduledCount */);

        // If the records is not refreshed, the current scheduled task will not be canceled.
        doReturn(TEST_ELAPSED_REALTIME + 20001).when(mockDecoderClock).elapsedRealtime();
        processResponse(createResponse(
                "service-instance-1", "192.0.2.123", 5353,
                SERVICE_TYPE_LABELS,
                Collections.emptyMap(), TEST_TTL,
                TEST_ELAPSED_REALTIME - 1), socketKey);
        verify(mockDeps, times(2)).removeMessages(any(), eq(EVENT_START_QUERYTASK));

        // In backoff mode, the current scheduled task will not be canceled if the
        // 0.8 * smallestRemainingTtl is smaller than time to next run.
        doReturn(TEST_ELAPSED_REALTIME).when(mockDecoderClock).elapsedRealtime();
        processResponse(createResponse(
                "service-instance-1", "192.0.2.123", 5353,
                SERVICE_TYPE_LABELS,
                Collections.emptyMap(), TEST_TTL), socketKey);
        verify(mockDeps, times(2)).removeMessages(any(), eq(EVENT_START_QUERYTASK));

        stopSendAndReceive(mockListenerOne);
        verify(mockDeps, times(3)).removeMessages(any(), eq(EVENT_START_QUERYTASK));
    }

    @Test
    public void sendQueries_reentry_passiveScanMode() {
        MdnsSearchOptions searchOptions = MdnsSearchOptions.newBuilder()
                .addSubtype(SUBTYPE).setQueryMode(PASSIVE_QUERY_MODE).build();
        startSendAndReceive(mockListenerOne, searchOptions);
        // Always try to remove the task.
        verify(mockDeps, times(1)).removeMessages(any(), eq(EVENT_START_QUERYTASK));

        // First burst, first query is sent.
        verifyAndSendQuery(0, 0, /* expectsUnicastResponse= */ true);

        // After the first query is sent, change the subtypes, and restart.
        searchOptions =
                MdnsSearchOptions.newBuilder()
                        .addSubtype(SUBTYPE)
                        .addSubtype("_subtype2")
                        .setQueryMode(PASSIVE_QUERY_MODE)
                        .build();
        startSendAndReceive(mockListenerOne, searchOptions);
        // The previous scheduled task should be canceled.
        verify(mockDeps, times(2)).removeMessages(any(), eq(EVENT_START_QUERYTASK));

        // Queries should continue to be sent.
        verifyAndSendQuery(1, 0, /* expectsUnicastResponse= */ true);
        verifyAndSendQuery(
                2, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);
        verifyAndSendQuery(
                3, MdnsConfigs.timeBetweenQueriesInBurstMs(), /* expectsUnicastResponse= */ false);

        // Stop sending packets.
        stopSendAndReceive(mockListenerOne);
        verify(mockDeps, times(3)).removeMessages(any(), eq(EVENT_START_QUERYTASK));
    }

    @Test
    @Ignore("MdnsConfigs is not configurable currently.")
    public void testQueryTaskConfig_alwaysAskForUnicastResponse() {
        //MdnsConfigsFlagsImpl.alwaysAskForUnicastResponseInEachBurst.override(true);
        MdnsSearchOptions searchOptions = MdnsSearchOptions.newBuilder()
                .addSubtype(SUBTYPE).setQueryMode(ACTIVE_QUERY_MODE).build();
        QueryTaskConfig config = new QueryTaskConfig(
                searchOptions.getQueryMode(),
                false /* onlyUseIpv6OnIpv6OnlyNetworks */, 3 /* numOfQueriesBeforeBackoff */,
                socketKey);

        // This is the first query. We will ask for unicast response.
        assertTrue(config.expectUnicastResponse);
        assertEquals(config.transactionId, 1);

        // For the rest of queries in this burst, we will NOT ask for unicast response.
        for (int i = 1; i < MdnsConfigs.queriesPerBurst(); i++) {
            int oldTransactionId = config.transactionId;
            config = config.getConfigForNextRun();
            assertFalse(config.expectUnicastResponse);
            assertEquals(config.transactionId, oldTransactionId + 1);
        }

        // This is the first query of a new burst. We will ask for unicast response.
        int oldTransactionId = config.transactionId;
        config = config.getConfigForNextRun();
        assertTrue(config.expectUnicastResponse);
        assertEquals(config.transactionId, oldTransactionId + 1);
    }

    @Test
    public void testQueryTaskConfig_askForUnicastInFirstQuery() {
        MdnsSearchOptions searchOptions = MdnsSearchOptions.newBuilder()
                .addSubtype(SUBTYPE).setQueryMode(ACTIVE_QUERY_MODE).build();
        QueryTaskConfig config = new QueryTaskConfig(
                searchOptions.getQueryMode(),
                false /* onlyUseIpv6OnIpv6OnlyNetworks */, 3 /* numOfQueriesBeforeBackoff */,
                socketKey);

        // This is the first query. We will ask for unicast response.
        assertTrue(config.expectUnicastResponse);
        assertEquals(config.transactionId, 1);

        // For the rest of queries in this burst, we will NOT ask for unicast response.
        for (int i = 1; i < MdnsConfigs.queriesPerBurst(); i++) {
            int oldTransactionId = config.transactionId;
            config = config.getConfigForNextRun();
            assertFalse(config.expectUnicastResponse);
            assertEquals(config.transactionId, oldTransactionId + 1);
        }

        // This is the first query of a new burst. We will NOT ask for unicast response.
        int oldTransactionId = config.transactionId;
        config = config.getConfigForNextRun();
        assertFalse(config.expectUnicastResponse);
        assertEquals(config.transactionId, oldTransactionId + 1);
    }

    @Test
    public void testIfPreviousTaskIsCanceledWhenNewSessionStarts() {
        MdnsSearchOptions searchOptions = MdnsSearchOptions.newBuilder()
                .addSubtype(SUBTYPE).setQueryMode(PASSIVE_QUERY_MODE).build();
        startSendAndReceive(mockListenerOne, searchOptions);
        Runnable firstMdnsTask = currentThreadExecutor.getAndClearSubmittedRunnable();

        // Change the sutypes and start a new session.
        searchOptions =
                MdnsSearchOptions.newBuilder()
                        .addSubtype(SUBTYPE)
                        .addSubtype("_subtype2")
                        .setQueryMode(PASSIVE_QUERY_MODE)
                        .build();
        startSendAndReceive(mockListenerOne, searchOptions);

        // Clear the scheduled runnable.
        currentThreadExecutor.getAndClearLastScheduledRunnable();

        // Simulate the case where the first mdns task is not successful canceled and it gets
        // executed anyway.
        firstMdnsTask.run();

        // Although it gets executes, no more task gets scheduled.
        assertNull(currentThreadExecutor.getAndClearLastScheduledRunnable());
    }

    @Test
    @Ignore("MdnsConfigs is not configurable currently.")
    public void testIfPreviousTaskIsCanceledWhenSessionStops() {
        //MdnsConfigsFlagsImpl.shouldCancelScanTaskWhenFutureIsNull.override(true);
        MdnsSearchOptions searchOptions = MdnsSearchOptions.newBuilder()
                .addSubtype(SUBTYPE).setQueryMode(PASSIVE_QUERY_MODE).build();
        startSendAndReceive(mockListenerOne, searchOptions);
        // Change the sutypes and start a new session.
        stopSendAndReceive(mockListenerOne);
        // Clear the scheduled runnable.
        currentThreadExecutor.getAndClearLastScheduledRunnable();

        // Simulate the case where the first mdns task is not successful canceled and it gets
        // executed anyway.
        currentThreadExecutor.getAndClearSubmittedRunnable().run();

        // Although it gets executes, no more task gets scheduled.
        assertNull(currentThreadExecutor.getAndClearLastScheduledRunnable());
    }

    @Test
    public void testQueryScheduledWhenAnsweredFromCache() {
        final MdnsSearchOptions searchOptions = MdnsSearchOptions.getDefaultOptions();
        startSendAndReceive(mockListenerOne, searchOptions);
        assertNotNull(currentThreadExecutor.getAndClearSubmittedRunnable());

        processResponse(createResponse(
                "service-instance-1", "192.0.2.123", 5353,
                SERVICE_TYPE_LABELS,
                Collections.emptyMap(), TEST_TTL), socketKey);

        verify(mockListenerOne).onServiceNameDiscovered(any(), eq(false) /* isServiceFromCache */);
        verify(mockListenerOne).onServiceFound(any(), eq(false) /* isServiceFromCache */);

        // File another identical query
        startSendAndReceive(mockListenerTwo, searchOptions);

        verify(mockListenerTwo).onServiceNameDiscovered(any(), eq(true) /* isServiceFromCache */);
        verify(mockListenerTwo).onServiceFound(any(), eq(true) /* isServiceFromCache */);

        // This time no query is submitted, only scheduled
        assertNull(currentThreadExecutor.getAndClearSubmittedRunnable());
        // This just skips the first query of the first burst
        verify(mockDeps).sendMessageDelayed(
                any(), any(), eq(MdnsConfigs.timeBetweenQueriesInBurstMs()));
    }

    @Test
    public void testCombinedSubtypesQueriedWithMultipleListeners() throws Exception {
        client = makeMdnsServiceTypeClient(/* packetWriter= */ null);
        final MdnsSearchOptions searchOptions1 = MdnsSearchOptions.newBuilder()
                .addSubtype("subtype1").build();
        final MdnsSearchOptions searchOptions2 = MdnsSearchOptions.newBuilder()
                .addSubtype("subtype2").build();
        startSendAndReceive(mockListenerOne, searchOptions1);
        currentThreadExecutor.getAndClearSubmittedRunnable().run();

        InOrder inOrder = inOrder(mockListenerOne, mockSocketClient, mockDeps);

        // Verify the query asks for subtype1
        final ArgumentCaptor<DatagramPacket> subtype1QueryCaptor =
                ArgumentCaptor.forClass(DatagramPacket.class);
        currentThreadExecutor.getAndClearLastScheduledRunnable().run();
        // Send twice for IPv4 and IPv6
        inOrder.verify(mockSocketClient, times(2)).sendPacketRequestingUnicastResponse(
                subtype1QueryCaptor.capture(),
                eq(socketKey), eq(false));

        final MdnsPacket subtype1Query = MdnsPacket.parse(
                new MdnsPacketReader(subtype1QueryCaptor.getValue()));

        assertEquals(2, subtype1Query.questions.size());
        assertTrue(hasQuestion(subtype1Query, MdnsRecord.TYPE_PTR, SERVICE_TYPE_LABELS));
        assertTrue(hasQuestion(subtype1Query, MdnsRecord.TYPE_PTR,
                getServiceTypeWithSubtype("_subtype1")));

        // Add subtype2
        startSendAndReceive(mockListenerTwo, searchOptions2);
        inOrder.verify(mockDeps).removeMessages(any(), eq(EVENT_START_QUERYTASK));
        currentThreadExecutor.getAndClearLastScheduledRunnable().run();

        final ArgumentCaptor<DatagramPacket> combinedSubtypesQueryCaptor =
                ArgumentCaptor.forClass(DatagramPacket.class);
        inOrder.verify(mockSocketClient, times(2)).sendPacketRequestingUnicastResponse(
                combinedSubtypesQueryCaptor.capture(),
                eq(socketKey), eq(false));
        // The next query must have been scheduled
        inOrder.verify(mockDeps).sendMessageDelayed(any(), any(), anyLong());

        final MdnsPacket combinedSubtypesQuery = MdnsPacket.parse(
                new MdnsPacketReader(combinedSubtypesQueryCaptor.getValue()));

        assertEquals(3, combinedSubtypesQuery.questions.size());
        assertTrue(hasQuestion(combinedSubtypesQuery, MdnsRecord.TYPE_PTR, SERVICE_TYPE_LABELS));
        assertTrue(hasQuestion(combinedSubtypesQuery, MdnsRecord.TYPE_PTR,
                getServiceTypeWithSubtype("_subtype1")));
        assertTrue(hasQuestion(combinedSubtypesQuery, MdnsRecord.TYPE_PTR,
                getServiceTypeWithSubtype("_subtype2")));

        // Remove subtype1
        stopSendAndReceive(mockListenerOne);

        // Queries are not rescheduled, but the next query is affected
        dispatchMessage();
        currentThreadExecutor.getAndClearLastScheduledRunnable().run();

        final ArgumentCaptor<DatagramPacket> subtype2QueryCaptor =
                ArgumentCaptor.forClass(DatagramPacket.class);
        // Send twice for IPv4 and IPv6
        inOrder.verify(mockSocketClient, times(2)).sendPacketRequestingMulticastResponse(
                subtype2QueryCaptor.capture(),
                eq(socketKey), eq(false));

        final MdnsPacket subtype2Query = MdnsPacket.parse(
                new MdnsPacketReader(subtype2QueryCaptor.getValue()));

        assertEquals(2, subtype2Query.questions.size());
        assertTrue(hasQuestion(subtype2Query, MdnsRecord.TYPE_PTR, SERVICE_TYPE_LABELS));
        assertTrue(hasQuestion(subtype2Query, MdnsRecord.TYPE_PTR,
                getServiceTypeWithSubtype("_subtype2")));
    }

    private static void verifyServiceInfo(MdnsServiceInfo serviceInfo, String serviceName,
            String[] serviceType, List<String> ipv4Addresses, List<String> ipv6Addresses, int port,
            List<String> subTypes, Map<String, String> attributes, SocketKey socketKey) {
        assertEquals(serviceName, serviceInfo.getServiceInstanceName());
        assertArrayEquals(serviceType, serviceInfo.getServiceType());
        assertEquals(ipv4Addresses, serviceInfo.getIpv4Addresses());
        assertEquals(ipv6Addresses, serviceInfo.getIpv6Addresses());
        assertEquals(port, serviceInfo.getPort());
        assertEquals(subTypes, serviceInfo.getSubtypes());
        for (String key : attributes.keySet()) {
            assertTrue(attributes.containsKey(key));
            assertEquals(attributes.get(key), serviceInfo.getAttributeByKey(key));
        }
        assertEquals(socketKey.getInterfaceIndex(), serviceInfo.getInterfaceIndex());
        assertEquals(socketKey.getNetwork(), serviceInfo.getNetwork());
    }

    @Test
    public void processResponse_incompleteResponse() {
        startSendAndReceive(mockListenerOne, MdnsSearchOptions.getDefaultOptions());

        processResponse(createResponse(
                "service-instance-1", null /* host */, 0 /* port */,
                SERVICE_TYPE_LABELS,
                Collections.emptyMap(), TEST_TTL), socketKey);
        verify(mockListenerOne).onServiceNameDiscovered(
                serviceInfoCaptor.capture(), eq(false) /* isServiceFromCache */);
        verifyServiceInfo(serviceInfoCaptor.getAllValues().get(0),
                "service-instance-1",
                SERVICE_TYPE_LABELS,
                /* ipv4Addresses= */ List.of(),
                /* ipv6Addresses= */ List.of(),
                /* port= */ 0,
                /* subTypes= */ List.of(),
                Collections.emptyMap(),
                socketKey);

        verify(mockListenerOne, never()).onServiceFound(any(MdnsServiceInfo.class), anyBoolean());
        verify(mockListenerOne, never()).onServiceUpdated(any(MdnsServiceInfo.class));
    }

    @Test
    public void processIPv4Response_completeResponseForNewServiceInstance() throws Exception {
        final String ipV4Address = "192.168.1.1";
        startSendAndReceive(mockListenerOne, MdnsSearchOptions.getDefaultOptions());

        // Process the initial response.
        processResponse(createResponse(
                "service-instance-1", ipV4Address, 5353, SUBTYPE,
                Collections.emptyMap(), TEST_TTL), socketKey);

        // Process a second response with a different port and updated text attributes.
        processResponse(createResponse(
                        "service-instance-1", ipV4Address, 5354, SUBTYPE,
                        Collections.singletonMap("key", "value"), TEST_TTL),
                socketKey);

        // Verify onServiceNameDiscovered was called once for the initial response.
        verify(mockListenerOne).onServiceNameDiscovered(
                serviceInfoCaptor.capture(), eq(false) /* isServiceFromCache */);
        verifyServiceInfo(serviceInfoCaptor.getAllValues().get(0),
                "service-instance-1",
                SERVICE_TYPE_LABELS,
                List.of(ipV4Address) /* ipv4Address */,
                List.of() /* ipv6Address */,
                5353 /* port */,
                Collections.singletonList(SUBTYPE) /* subTypes */,
                Collections.singletonMap("key", null) /* attributes */,
                socketKey);

        // Verify onServiceFound was called once for the initial response.
        verify(mockListenerOne).onServiceFound(
                serviceInfoCaptor.capture(), eq(false) /* isServiceFromCache */);
        MdnsServiceInfo initialServiceInfo = serviceInfoCaptor.getAllValues().get(1);
        assertEquals(initialServiceInfo.getServiceInstanceName(), "service-instance-1");
        assertEquals(initialServiceInfo.getIpv4Address(), ipV4Address);
        assertEquals(initialServiceInfo.getPort(), 5353);
        assertEquals(initialServiceInfo.getSubtypes(), Collections.singletonList(SUBTYPE));
        assertNull(initialServiceInfo.getAttributeByKey("key"));
        assertEquals(socketKey.getInterfaceIndex(), initialServiceInfo.getInterfaceIndex());
        assertEquals(socketKey.getNetwork(), initialServiceInfo.getNetwork());

        // Verify onServiceUpdated was called once for the second response.
        verify(mockListenerOne).onServiceUpdated(serviceInfoCaptor.capture());
        MdnsServiceInfo updatedServiceInfo = serviceInfoCaptor.getAllValues().get(2);
        assertEquals(updatedServiceInfo.getServiceInstanceName(), "service-instance-1");
        assertEquals(updatedServiceInfo.getIpv4Address(), ipV4Address);
        assertEquals(updatedServiceInfo.getPort(), 5354);
        assertTrue(updatedServiceInfo.hasSubtypes());
        assertEquals(updatedServiceInfo.getSubtypes(), Collections.singletonList(SUBTYPE));
        assertEquals(updatedServiceInfo.getAttributeByKey("key"), "value");
        assertEquals(socketKey.getInterfaceIndex(), updatedServiceInfo.getInterfaceIndex());
        assertEquals(socketKey.getNetwork(), updatedServiceInfo.getNetwork());
    }

    @Test
    public void processIPv6Response_getCorrectServiceInfo() throws Exception {
        final String ipV6Address = "2000:3333::da6c:63ff:fe7c:7483";
        startSendAndReceive(mockListenerOne, MdnsSearchOptions.getDefaultOptions());

        // Process the initial response.
        processResponse(createResponse(
                "service-instance-1", ipV6Address, 5353, SUBTYPE,
                Collections.emptyMap(), TEST_TTL), socketKey);

        // Process a second response with a different port and updated text attributes.
        processResponse(createResponse(
                        "service-instance-1", ipV6Address, 5354, SUBTYPE,
                        Collections.singletonMap("key", "value"), TEST_TTL),
                socketKey);

        // Verify onServiceNameDiscovered was called once for the initial response.
        verify(mockListenerOne).onServiceNameDiscovered(
                serviceInfoCaptor.capture(), eq(false) /* isServiceFromCache */);
        verifyServiceInfo(serviceInfoCaptor.getAllValues().get(0),
                "service-instance-1",
                SERVICE_TYPE_LABELS,
                List.of() /* ipv4Address */,
                List.of(ipV6Address) /* ipv6Address */,
                5353 /* port */,
                Collections.singletonList(SUBTYPE) /* subTypes */,
                Collections.singletonMap("key", null) /* attributes */,
                socketKey);

        // Verify onServiceFound was called once for the initial response.
        verify(mockListenerOne).onServiceFound(
                serviceInfoCaptor.capture(), eq(false) /* isServiceFromCache */);
        MdnsServiceInfo initialServiceInfo = serviceInfoCaptor.getAllValues().get(1);
        assertEquals(initialServiceInfo.getServiceInstanceName(), "service-instance-1");
        assertEquals(initialServiceInfo.getIpv6Address(), ipV6Address);
        assertEquals(initialServiceInfo.getPort(), 5353);
        assertEquals(initialServiceInfo.getSubtypes(), Collections.singletonList(SUBTYPE));
        assertNull(initialServiceInfo.getAttributeByKey("key"));
        assertEquals(socketKey.getInterfaceIndex(), initialServiceInfo.getInterfaceIndex());
        assertEquals(socketKey.getNetwork(), initialServiceInfo.getNetwork());

        // Verify onServiceUpdated was called once for the second response.
        verify(mockListenerOne).onServiceUpdated(serviceInfoCaptor.capture());
        MdnsServiceInfo updatedServiceInfo = serviceInfoCaptor.getAllValues().get(2);
        assertEquals(updatedServiceInfo.getServiceInstanceName(), "service-instance-1");
        assertEquals(updatedServiceInfo.getIpv6Address(), ipV6Address);
        assertEquals(updatedServiceInfo.getPort(), 5354);
        assertTrue(updatedServiceInfo.hasSubtypes());
        assertEquals(updatedServiceInfo.getSubtypes(), Collections.singletonList(SUBTYPE));
        assertEquals(updatedServiceInfo.getAttributeByKey("key"), "value");
        assertEquals(socketKey.getInterfaceIndex(), updatedServiceInfo.getInterfaceIndex());
        assertEquals(socketKey.getNetwork(), updatedServiceInfo.getNetwork());
    }

    private void verifyServiceRemovedNoCallback(MdnsServiceBrowserListener listener) {
        verify(listener, never()).onServiceRemoved(any());
        verify(listener, never()).onServiceNameRemoved(any());
    }

    private void verifyServiceRemovedCallback(MdnsServiceBrowserListener listener,
            String serviceName, String[] serviceType, SocketKey socketKey) {
        verify(listener).onServiceRemoved(argThat(
                info -> serviceName.equals(info.getServiceInstanceName())
                        && Arrays.equals(serviceType, info.getServiceType())
                        && info.getInterfaceIndex() == socketKey.getInterfaceIndex()
                        && socketKey.getNetwork().equals(info.getNetwork())));
        verify(listener).onServiceNameRemoved(argThat(
                info -> serviceName.equals(info.getServiceInstanceName())
                        && Arrays.equals(serviceType, info.getServiceType())
                        && info.getInterfaceIndex() == socketKey.getInterfaceIndex()
                        && socketKey.getNetwork().equals(info.getNetwork())));
    }

    @Test
    public void processResponse_goodBye() throws Exception {
        startSendAndReceive(mockListenerOne, MdnsSearchOptions.getDefaultOptions());
        startSendAndReceive(mockListenerTwo, MdnsSearchOptions.getDefaultOptions());

        final String serviceName = "service-instance-1";
        final String ipV6Address = "2000:3333::da6c:63ff:fe7c:7483";
        // Process the initial response.
        processResponse(createResponse(
                serviceName, ipV6Address, 5353,
                SERVICE_TYPE_LABELS,
                Collections.emptyMap(), TEST_TTL), socketKey);

        processResponse(createResponse(
                "goodbye-service", ipV6Address, 5353,
                SERVICE_TYPE_LABELS,
                Collections.emptyMap(), /* ptrTtlMillis= */ 0L), socketKey);

        // Verify removed callback won't be called if the service is not existed.
        verifyServiceRemovedNoCallback(mockListenerOne);
        verifyServiceRemovedNoCallback(mockListenerTwo);

        // Verify removed callback would be called.
        processResponse(createResponse(
                serviceName, ipV6Address, 5353,
                SERVICE_TYPE_LABELS,
                Collections.emptyMap(), 0L), socketKey);
        verifyServiceRemovedCallback(
                mockListenerOne, serviceName, SERVICE_TYPE_LABELS, socketKey);
        verifyServiceRemovedCallback(
                mockListenerTwo, serviceName, SERVICE_TYPE_LABELS, socketKey);
    }

    @Test
    public void reportExistingServiceToNewlyRegisteredListeners() throws Exception {
        // Process the initial response.
        processResponse(createResponse(
                "service-instance-1", "192.168.1.1", 5353, SUBTYPE,
                Collections.emptyMap(), TEST_TTL), socketKey);

        startSendAndReceive(mockListenerOne, MdnsSearchOptions.getDefaultOptions());

        // Verify onServiceNameDiscovered was called once for the existing response.
        verify(mockListenerOne).onServiceNameDiscovered(
                serviceInfoCaptor.capture(), eq(true) /* isServiceFromCache */);
        verifyServiceInfo(serviceInfoCaptor.getAllValues().get(0),
                "service-instance-1",
                SERVICE_TYPE_LABELS,
                List.of("192.168.1.1") /* ipv4Address */,
                List.of() /* ipv6Address */,
                5353 /* port */,
                Collections.singletonList(SUBTYPE) /* subTypes */,
                Collections.singletonMap("key", null) /* attributes */,
                socketKey);

        // Verify onServiceFound was called once for the existing response.
        verify(mockListenerOne).onServiceFound(
                serviceInfoCaptor.capture(), eq(true) /* isServiceFromCache */);
        MdnsServiceInfo existingServiceInfo = serviceInfoCaptor.getAllValues().get(1);
        assertEquals(existingServiceInfo.getServiceInstanceName(), "service-instance-1");
        assertEquals(existingServiceInfo.getIpv4Address(), "192.168.1.1");
        assertEquals(existingServiceInfo.getPort(), 5353);
        assertEquals(existingServiceInfo.getSubtypes(), Collections.singletonList(SUBTYPE));
        assertNull(existingServiceInfo.getAttributeByKey("key"));

        // Process a goodbye message for the existing response.
        processResponse(createResponse(
                "service-instance-1", "192.168.1.1", 5353,
                SERVICE_TYPE_LABELS,
                Collections.emptyMap(), /* ptrTtlMillis= */ 0L), socketKey);

        startSendAndReceive(mockListenerTwo, MdnsSearchOptions.getDefaultOptions());

        // Verify onServiceFound was not called on the newly registered listener after the existing
        // response is gone.
        verify(mockListenerTwo, never()).onServiceNameDiscovered(
                any(MdnsServiceInfo.class), eq(false));
        verify(mockListenerTwo, never()).onServiceFound(any(MdnsServiceInfo.class), anyBoolean());
    }

    @Test
    public void processResponse_searchOptionsEnableServiceRemoval_shouldRemove()
            throws Exception {
        final String serviceInstanceName = "service-instance-1";
        client = makeMdnsServiceTypeClient(mockPacketWriter);
        MdnsSearchOptions searchOptions = MdnsSearchOptions.newBuilder()
                .setRemoveExpiredService(true)
                .setNumOfQueriesBeforeBackoff(Integer.MAX_VALUE)
                .build();
        startSendAndReceive(mockListenerOne, searchOptions);
        Runnable firstMdnsTask = currentThreadExecutor.getAndClearSubmittedRunnable();

        // Process the initial response.
        processResponse(createResponse(
                serviceInstanceName, "192.168.1.1", 5353, SUBTYPE,
                Collections.emptyMap(), TEST_TTL), socketKey);

        // Clear the scheduled runnable.
        currentThreadExecutor.getAndClearLastScheduledRunnable();

        // Simulate the case where the response is under TTL.
        doReturn(TEST_ELAPSED_REALTIME + TEST_TTL - 1L).when(mockDecoderClock).elapsedRealtime();
        firstMdnsTask.run();
        verify(mockDeps, times(1)).sendMessage(any(), any(Message.class));

        // Verify removed callback was not called.
        verifyServiceRemovedNoCallback(mockListenerOne);

        // Simulate the case where the response is after TTL.
        doReturn(TEST_ELAPSED_REALTIME + TEST_TTL + 1L).when(mockDecoderClock).elapsedRealtime();
        firstMdnsTask.run();
        verify(mockDeps, times(2)).sendMessage(any(), any(Message.class));

        // Verify removed callback was called.
        verifyServiceRemovedCallback(
                mockListenerOne, serviceInstanceName, SERVICE_TYPE_LABELS, socketKey);
    }

    @Test
    public void processResponse_searchOptionsNotEnableServiceRemoval_shouldNotRemove()
            throws Exception {
        final String serviceInstanceName = "service-instance-1";
        client = makeMdnsServiceTypeClient(mockPacketWriter);
        startSendAndReceive(mockListenerOne, MdnsSearchOptions.getDefaultOptions());
        Runnable firstMdnsTask = currentThreadExecutor.getAndClearSubmittedRunnable();

        // Process the initial response.
        processResponse(createResponse(
                serviceInstanceName, "192.168.1.1", 5353, SUBTYPE,
                Collections.emptyMap(), TEST_TTL), socketKey);

        // Clear the scheduled runnable.
        currentThreadExecutor.getAndClearLastScheduledRunnable();

        // Simulate the case where the response is after TTL.
        doReturn(TEST_ELAPSED_REALTIME + TEST_TTL + 1L).when(mockDecoderClock).elapsedRealtime();
        firstMdnsTask.run();

        // Verify removed callback was not called.
        verifyServiceRemovedNoCallback(mockListenerOne);
    }

    @Test
    @Ignore("MdnsConfigs is not configurable currently.")
    public void processResponse_removeServiceAfterTtlExpiresEnabled_shouldRemove()
            throws Exception {
        //MdnsConfigsFlagsImpl.removeServiceAfterTtlExpires.override(true);
        final String serviceInstanceName = "service-instance-1";
        client = makeMdnsServiceTypeClient(mockPacketWriter);
        startSendAndReceive(mockListenerOne, MdnsSearchOptions.getDefaultOptions());
        Runnable firstMdnsTask = currentThreadExecutor.getAndClearSubmittedRunnable();

        // Process the initial response.
        processResponse(createResponse(
                serviceInstanceName, "192.168.1.1", 5353, SUBTYPE,
                Collections.emptyMap(), TEST_TTL), socketKey);

        // Clear the scheduled runnable.
        currentThreadExecutor.getAndClearLastScheduledRunnable();

        // Simulate the case where the response is after TTL.
        doReturn(TEST_ELAPSED_REALTIME + TEST_TTL + 1L).when(mockDecoderClock).elapsedRealtime();
        firstMdnsTask.run();

        // Verify removed callback was called.
        verifyServiceRemovedCallback(
                mockListenerOne, serviceInstanceName, SERVICE_TYPE_LABELS, socketKey);
    }

    @Test
    public void testProcessResponse_InOrder() throws Exception {
        final String serviceName = "service-instance";
        final String ipV4Address = "192.0.2.0";
        final String ipV6Address = "2001:db8::";
        startSendAndReceive(mockListenerOne, MdnsSearchOptions.getDefaultOptions());
        InOrder inOrder = inOrder(mockListenerOne);

        // Process the initial response which is incomplete.
        processResponse(createResponse(
                serviceName, null, 5353, SUBTYPE,
                Collections.emptyMap(), TEST_TTL), socketKey);

        // Process a second response which has ip address to make response become complete.
        processResponse(createResponse(
                serviceName, ipV4Address, 5353, SUBTYPE,
                Collections.emptyMap(), TEST_TTL), socketKey);

        // Process a third response with a different ip address, port and updated text attributes.
        processResponse(createResponse(
                serviceName, ipV6Address, 5354, SUBTYPE,
                Collections.singletonMap("key", "value"), TEST_TTL), socketKey);

        // Process the last response which is goodbye message (with the main type, not subtype).
        processResponse(createResponse(
                        serviceName, ipV6Address, 5354, SERVICE_TYPE_LABELS,
                        Collections.singletonMap("key", "value"), /* ptrTtlMillis= */ 0L),
                socketKey);

        // Verify onServiceNameDiscovered was first called for the initial response.
        inOrder.verify(mockListenerOne).onServiceNameDiscovered(
                serviceInfoCaptor.capture(), eq(false) /* isServiceFromCache */);
        verifyServiceInfo(serviceInfoCaptor.getAllValues().get(0),
                serviceName,
                SERVICE_TYPE_LABELS,
                List.of() /* ipv4Address */,
                List.of() /* ipv6Address */,
                5353 /* port */,
                Collections.singletonList(SUBTYPE) /* subTypes */,
                Collections.singletonMap("key", null) /* attributes */,
                socketKey);

        // Verify onServiceFound was second called for the second response.
        inOrder.verify(mockListenerOne).onServiceFound(
                serviceInfoCaptor.capture(), eq(false) /* isServiceFromCache */);
        verifyServiceInfo(serviceInfoCaptor.getAllValues().get(1),
                serviceName,
                SERVICE_TYPE_LABELS,
                List.of(ipV4Address) /* ipv4Address */,
                List.of() /* ipv6Address */,
                5353 /* port */,
                Collections.singletonList(SUBTYPE) /* subTypes */,
                Collections.singletonMap("key", null) /* attributes */,
                socketKey);

        // Verify onServiceUpdated was third called for the third response.
        inOrder.verify(mockListenerOne).onServiceUpdated(serviceInfoCaptor.capture());
        verifyServiceInfo(serviceInfoCaptor.getAllValues().get(2),
                serviceName,
                SERVICE_TYPE_LABELS,
                List.of(ipV4Address) /* ipv4Address */,
                List.of(ipV6Address) /* ipv6Address */,
                5354 /* port */,
                Collections.singletonList(SUBTYPE) /* subTypes */,
                Collections.singletonMap("key", "value") /* attributes */,
                socketKey);

        // Verify onServiceRemoved was called for the last response.
        inOrder.verify(mockListenerOne).onServiceRemoved(serviceInfoCaptor.capture());
        verifyServiceInfo(serviceInfoCaptor.getAllValues().get(3),
                serviceName,
                SERVICE_TYPE_LABELS,
                List.of(ipV4Address) /* ipv4Address */,
                List.of(ipV6Address) /* ipv6Address */,
                5354 /* port */,
                Collections.singletonList(SUBTYPE) /* subTypes */,
                Collections.singletonMap("key", "value") /* attributes */,
                socketKey);

        // Verify onServiceNameRemoved was called for the last response.
        inOrder.verify(mockListenerOne).onServiceNameRemoved(serviceInfoCaptor.capture());
        verifyServiceInfo(serviceInfoCaptor.getAllValues().get(4),
                serviceName,
                SERVICE_TYPE_LABELS,
                List.of(ipV4Address) /* ipv4Address */,
                List.of(ipV6Address) /* ipv6Address */,
                5354 /* port */,
                Collections.singletonList(SUBTYPE) /* subTypes */,
                Collections.singletonMap("key", "value") /* attributes */,
                socketKey);
    }

    @Test
    public void testProcessResponse_Resolve() throws Exception {
        client = makeMdnsServiceTypeClient(/* packetWriter= */ null);

        final String instanceName = "service-instance";
        final String[] hostname = new String[] { "testhost "};
        final String ipV4Address = "192.0.2.0";
        final String ipV6Address = "2001:db8::";

        final MdnsSearchOptions resolveOptions1 = MdnsSearchOptions.newBuilder()
                .setResolveInstanceName(instanceName).build();
        final MdnsSearchOptions resolveOptions2 = MdnsSearchOptions.newBuilder()
                .setResolveInstanceName(instanceName).build();

        startSendAndReceive(mockListenerOne, resolveOptions1);
        startSendAndReceive(mockListenerTwo, resolveOptions2);
        // No need to verify order for both listeners; and order is not guaranteed between them
        InOrder inOrder = inOrder(mockListenerOne, mockSocketClient);

        // Verify a query for SRV/TXT was sent, but no PTR query
        final ArgumentCaptor<DatagramPacket> srvTxtQueryCaptor =
                ArgumentCaptor.forClass(DatagramPacket.class);
        currentThreadExecutor.getAndClearLastScheduledRunnable().run();
        // Send twice for IPv4 and IPv6
        inOrder.verify(mockSocketClient, times(2)).sendPacketRequestingUnicastResponse(
                srvTxtQueryCaptor.capture(),
                eq(socketKey), eq(false));
        verify(mockDeps, times(1)).sendMessage(any(), any(Message.class));
        assertNotNull(delayMessage);
        inOrder.verify(mockListenerOne).onDiscoveryQuerySent(any(), anyInt());
        verify(mockListenerTwo).onDiscoveryQuerySent(any(), anyInt());

        final MdnsPacket srvTxtQueryPacket = MdnsPacket.parse(
                new MdnsPacketReader(srvTxtQueryCaptor.getValue()));

        final String[] serviceName = getTestServiceName(instanceName);
        assertEquals(1, srvTxtQueryPacket.questions.size());
        assertFalse(hasQuestion(srvTxtQueryPacket, MdnsRecord.TYPE_PTR));
        assertTrue(hasQuestion(srvTxtQueryPacket, MdnsRecord.TYPE_ANY, serviceName));
        assertEquals(0, srvTxtQueryPacket.answers.size());
        assertEquals(0, srvTxtQueryPacket.authorityRecords.size());
        assertEquals(0, srvTxtQueryPacket.additionalRecords.size());

        // Process a response with SRV+TXT
        final MdnsPacket srvTxtResponse = new MdnsPacket(
                0 /* flags */,
                Collections.emptyList() /* questions */,
                List.of(
                        new MdnsServiceRecord(serviceName, 0L /* receiptTimeMillis */,
                                true /* cacheFlush */, TEST_TTL, 0 /* servicePriority */,
                                0 /* serviceWeight */, 1234 /* servicePort */, hostname),
                        new MdnsTextRecord(serviceName, 0L /* receiptTimeMillis */,
                                true /* cacheFlush */, TEST_TTL,
                                Collections.emptyList() /* entries */)),
                Collections.emptyList() /* authorityRecords */,
                Collections.emptyList() /* additionalRecords */);

        processResponse(srvTxtResponse, socketKey);
        inOrder.verify(mockListenerOne).onServiceNameDiscovered(
                matchServiceName(instanceName), eq(false) /* isServiceFromCache */);
        verify(mockListenerTwo).onServiceNameDiscovered(
                matchServiceName(instanceName), eq(false) /* isServiceFromCache */);

        // Expect a query for A/AAAA
        dispatchMessage();
        final ArgumentCaptor<DatagramPacket> addressQueryCaptor =
                ArgumentCaptor.forClass(DatagramPacket.class);
        currentThreadExecutor.getAndClearLastScheduledRunnable().run();
        inOrder.verify(mockSocketClient, times(2)).sendPacketRequestingMulticastResponse(
                addressQueryCaptor.capture(),
                eq(socketKey), eq(false));
        inOrder.verify(mockListenerOne).onDiscoveryQuerySent(any(), anyInt());
        // onDiscoveryQuerySent was called 2 times in total
        verify(mockListenerTwo, times(2)).onDiscoveryQuerySent(any(), anyInt());

        final MdnsPacket addressQueryPacket = MdnsPacket.parse(
                new MdnsPacketReader(addressQueryCaptor.getValue()));
        assertEquals(2, addressQueryPacket.questions.size());
        assertTrue(hasQuestion(addressQueryPacket, MdnsRecord.TYPE_A, hostname));
        assertTrue(hasQuestion(addressQueryPacket, MdnsRecord.TYPE_AAAA, hostname));
        assertEquals(0, addressQueryPacket.answers.size());
        assertEquals(0, addressQueryPacket.authorityRecords.size());
        assertEquals(0, addressQueryPacket.additionalRecords.size());

        // Process a response with address records
        final MdnsPacket addressResponse = new MdnsPacket(
                0 /* flags */,
                Collections.emptyList() /* questions */,
                List.of(
                        new MdnsInetAddressRecord(hostname, 0L /* receiptTimeMillis */,
                                true /* cacheFlush */, TEST_TTL,
                                InetAddresses.parseNumericAddress(ipV4Address)),
                        new MdnsInetAddressRecord(hostname, 0L /* receiptTimeMillis */,
                                true /* cacheFlush */, TEST_TTL,
                                InetAddresses.parseNumericAddress(ipV6Address))),
                Collections.emptyList() /* authorityRecords */,
                Collections.emptyList() /* additionalRecords */);

        inOrder.verify(mockListenerOne, never()).onServiceNameDiscovered(any(), anyBoolean());
        verifyNoMoreInteractions(mockListenerTwo);
        processResponse(addressResponse, socketKey);

        inOrder.verify(mockListenerOne).onServiceFound(
                serviceInfoCaptor.capture(), eq(false) /* isServiceFromCache */);
        verify(mockListenerTwo).onServiceFound(any(), anyBoolean());
        verifyServiceInfo(serviceInfoCaptor.getValue(),
                instanceName,
                SERVICE_TYPE_LABELS,
                List.of(ipV4Address),
                List.of(ipV6Address),
                1234 /* port */,
                Collections.emptyList() /* subTypes */,
                Collections.emptyMap() /* attributes */,
                socketKey);
    }

    @Test
    public void testRenewTxtSrvInResolve() throws Exception {
        client = makeMdnsServiceTypeClient(/* packetWriter= */ null);

        final String instanceName = "service-instance";
        final String[] hostname = new String[] { "testhost "};
        final String ipV4Address = "192.0.2.0";
        final String ipV6Address = "2001:db8::";

        final MdnsSearchOptions resolveOptions = MdnsSearchOptions.newBuilder()
                .setResolveInstanceName(instanceName).build();

        startSendAndReceive(mockListenerOne, resolveOptions);
        InOrder inOrder = inOrder(mockListenerOne, mockSocketClient);

        // Get the query for SRV/TXT
        final ArgumentCaptor<DatagramPacket> srvTxtQueryCaptor =
                ArgumentCaptor.forClass(DatagramPacket.class);
        currentThreadExecutor.getAndClearLastScheduledRunnable().run();
        // Send twice for IPv4 and IPv6
        inOrder.verify(mockSocketClient, times(2)).sendPacketRequestingUnicastResponse(
                srvTxtQueryCaptor.capture(),
                eq(socketKey), eq(false));
        verify(mockDeps, times(1)).sendMessage(any(), any(Message.class));
        assertNotNull(delayMessage);

        final MdnsPacket srvTxtQueryPacket = MdnsPacket.parse(
                new MdnsPacketReader(srvTxtQueryCaptor.getValue()));

        final String[] serviceName = getTestServiceName(instanceName);
        assertTrue(hasQuestion(srvTxtQueryPacket, MdnsRecord.TYPE_ANY, serviceName));

        // Process a response with all records
        final MdnsPacket srvTxtResponse = new MdnsPacket(
                0 /* flags */,
                Collections.emptyList() /* questions */,
                List.of(
                        new MdnsServiceRecord(serviceName, TEST_ELAPSED_REALTIME,
                                true /* cacheFlush */, TEST_TTL, 0 /* servicePriority */,
                                0 /* serviceWeight */, 1234 /* servicePort */, hostname),
                        new MdnsTextRecord(serviceName, TEST_ELAPSED_REALTIME,
                                true /* cacheFlush */, TEST_TTL,
                                Collections.emptyList() /* entries */),
                        new MdnsInetAddressRecord(hostname, TEST_ELAPSED_REALTIME,
                                true /* cacheFlush */, TEST_TTL,
                                InetAddresses.parseNumericAddress(ipV4Address)),
                        new MdnsInetAddressRecord(hostname, TEST_ELAPSED_REALTIME,
                                true /* cacheFlush */, TEST_TTL,
                                InetAddresses.parseNumericAddress(ipV6Address))),
                Collections.emptyList() /* authorityRecords */,
                Collections.emptyList() /* additionalRecords */);
        processResponse(srvTxtResponse, socketKey);
        dispatchMessage();
        inOrder.verify(mockListenerOne).onServiceNameDiscovered(
                any(), eq(false) /* isServiceFromCache */);
        inOrder.verify(mockListenerOne).onServiceFound(
                any(), eq(false) /* isServiceFromCache */);

        // Expect no query on the next run
        currentThreadExecutor.getAndClearLastScheduledRunnable().run();
        inOrder.verifyNoMoreInteractions();

        // Advance time so 75% of TTL passes and re-execute
        doReturn(TEST_ELAPSED_REALTIME + (long) (TEST_TTL * 0.75))
                .when(mockDecoderClock).elapsedRealtime();
        verify(mockDeps, times(2)).sendMessage(any(), any(Message.class));
        assertNotNull(delayMessage);
        dispatchMessage();
        currentThreadExecutor.getAndClearLastScheduledRunnable().run();

        // Expect a renewal query
        final ArgumentCaptor<DatagramPacket> renewalQueryCaptor =
                ArgumentCaptor.forClass(DatagramPacket.class);
        // Second and later sends are sent as "expect multicast response" queries
        inOrder.verify(mockSocketClient, times(2)).sendPacketRequestingMulticastResponse(
                renewalQueryCaptor.capture(),
                eq(socketKey), eq(false));
        verify(mockDeps, times(3)).sendMessage(any(), any(Message.class));
        assertNotNull(delayMessage);
        inOrder.verify(mockListenerOne).onDiscoveryQuerySent(any(), anyInt());
        final MdnsPacket renewalPacket = MdnsPacket.parse(
                new MdnsPacketReader(renewalQueryCaptor.getValue()));
        assertTrue(hasQuestion(renewalPacket, MdnsRecord.TYPE_ANY, serviceName));
        inOrder.verifyNoMoreInteractions();

        long updatedReceiptTime =  TEST_ELAPSED_REALTIME + TEST_TTL;
        final MdnsPacket refreshedSrvTxtResponse = new MdnsPacket(
                0 /* flags */,
                Collections.emptyList() /* questions */,
                List.of(
                        new MdnsServiceRecord(serviceName, updatedReceiptTime,
                                true /* cacheFlush */, TEST_TTL, 0 /* servicePriority */,
                                0 /* serviceWeight */, 1234 /* servicePort */, hostname),
                        new MdnsTextRecord(serviceName, updatedReceiptTime,
                                true /* cacheFlush */, TEST_TTL,
                                Collections.emptyList() /* entries */),
                        new MdnsInetAddressRecord(hostname, updatedReceiptTime,
                                true /* cacheFlush */, TEST_TTL,
                                InetAddresses.parseNumericAddress(ipV4Address)),
                        new MdnsInetAddressRecord(hostname, updatedReceiptTime,
                                true /* cacheFlush */, TEST_TTL,
                                InetAddresses.parseNumericAddress(ipV6Address))),
                Collections.emptyList() /* authorityRecords */,
                Collections.emptyList() /* additionalRecords */);
        processResponse(refreshedSrvTxtResponse, socketKey);
        dispatchMessage();

        // Advance time to updatedReceiptTime + 1, expected no refresh query because the cache
        // should contain the record that have update last receipt time.
        doReturn(updatedReceiptTime + 1).when(mockDecoderClock).elapsedRealtime();
        currentThreadExecutor.getAndClearLastScheduledRunnable().run();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessResponse_ResolveExcludesOtherServices() {
        client = makeMdnsServiceTypeClient(/* packetWriter= */ null);

        final String requestedInstance = "instance1";
        final String otherInstance = "instance2";
        final String ipV4Address = "192.0.2.0";
        final String ipV6Address = "2001:db8::";
        final String capitalizedRequestInstance = "Instance1";

        final MdnsSearchOptions resolveOptions = MdnsSearchOptions.newBuilder()
                // Use different case in the options
                .setResolveInstanceName(capitalizedRequestInstance).build();

        startSendAndReceive(mockListenerOne, resolveOptions);
        startSendAndReceive(mockListenerTwo, MdnsSearchOptions.getDefaultOptions());

        // Complete response from instanceName
        processResponse(createResponse(
                        requestedInstance, ipV4Address, 5353, SERVICE_TYPE_LABELS,
                        Collections.emptyMap() /* textAttributes */, TEST_TTL),
                socketKey);

        // Complete response from otherInstanceName
        processResponse(createResponse(
                        otherInstance, ipV4Address, 5353, SERVICE_TYPE_LABELS,
                        Collections.emptyMap() /* textAttributes */, TEST_TTL),
                socketKey);

        // Address update from otherInstanceName
        processResponse(createResponse(
                otherInstance, ipV6Address, 5353, SERVICE_TYPE_LABELS,
                Collections.emptyMap(), TEST_TTL), socketKey);

        // Goodbye from otherInstanceName
        processResponse(createResponse(
                otherInstance, ipV6Address, 5353, SERVICE_TYPE_LABELS,
                Collections.emptyMap(), 0L /* ttl */), socketKey);

        // mockListenerOne gets notified for the requested instance
        verify(mockListenerOne).onServiceNameDiscovered(
                matchServiceName(capitalizedRequestInstance), eq(false) /* isServiceFromCache */);
        verify(mockListenerOne).onServiceFound(
                matchServiceName(capitalizedRequestInstance), eq(false) /* isServiceFromCache */);

        // ...but does not get any callback for the other instance
        verify(mockListenerOne, never()).onServiceFound(
                matchServiceName(otherInstance), anyBoolean());
        verify(mockListenerOne, never()).onServiceNameDiscovered(
                matchServiceName(otherInstance), anyBoolean());
        verify(mockListenerOne, never()).onServiceUpdated(matchServiceName(otherInstance));
        verify(mockListenerOne, never()).onServiceRemoved(matchServiceName(otherInstance));

        // mockListenerTwo gets notified for both though
        final InOrder inOrder = inOrder(mockListenerTwo);
        inOrder.verify(mockListenerTwo).onServiceNameDiscovered(
                matchServiceName(capitalizedRequestInstance), eq(false) /* isServiceFromCache */);
        inOrder.verify(mockListenerTwo).onServiceFound(
                matchServiceName(capitalizedRequestInstance), eq(false) /* isServiceFromCache */);

        inOrder.verify(mockListenerTwo).onServiceNameDiscovered(
                matchServiceName(otherInstance), eq(false) /* isServiceFromCache */);
        inOrder.verify(mockListenerTwo).onServiceFound(
                matchServiceName(otherInstance), eq(false) /* isServiceFromCache */);
        inOrder.verify(mockListenerTwo).onServiceUpdated(matchServiceName(otherInstance));
        inOrder.verify(mockListenerTwo).onServiceRemoved(matchServiceName(otherInstance));
    }

    @Test
    public void testProcessResponse_SubtypeDiscoveryLimitedToSubtype() {
        client = makeMdnsServiceTypeClient(/* packetWriter= */ null);

        final String matchingInstance = "instance1";
        final String subtype = "_subtype";
        final String otherInstance = "instance2";
        final String ipV4Address = "192.0.2.0";
        final String ipV6Address = "2001:db8::";

        final MdnsSearchOptions options = MdnsSearchOptions.newBuilder()
                // Search with different case. Note MdnsSearchOptions subtype doesn't start with "_"
                .addSubtype("Subtype").build();

        startSendAndReceive(mockListenerOne, options);
        startSendAndReceive(mockListenerTwo, MdnsSearchOptions.getDefaultOptions());

        // Complete response from instanceName
        final MdnsPacket packetWithoutSubtype = createResponse(
                matchingInstance, ipV4Address, 5353, SERVICE_TYPE_LABELS,
                Collections.emptyMap() /* textAttributes */, TEST_TTL);
        final MdnsPointerRecord originalPtr = (MdnsPointerRecord) CollectionUtils.findFirst(
                packetWithoutSubtype.answers, r -> r instanceof MdnsPointerRecord);

        // Add a subtype PTR record
        final ArrayList<MdnsRecord> newAnswers = new ArrayList<>(packetWithoutSubtype.answers);
        newAnswers.add(new MdnsPointerRecord(
                // PTR should be _subtype._sub._type._tcp.local -> instance1._type._tcp.local
                Stream.concat(Stream.of(subtype, "_sub"), Arrays.stream(SERVICE_TYPE_LABELS))
                        .toArray(String[]::new),
                originalPtr.getReceiptTime(), originalPtr.getCacheFlush(), originalPtr.getTtl(),
                originalPtr.getPointer()));
        final MdnsPacket packetWithSubtype = new MdnsPacket(
                packetWithoutSubtype.flags,
                packetWithoutSubtype.questions,
                newAnswers,
                packetWithoutSubtype.authorityRecords,
                packetWithoutSubtype.additionalRecords);
        processResponse(packetWithSubtype, socketKey);

        // Complete response from otherInstanceName, without subtype
        processResponse(createResponse(
                        otherInstance, ipV4Address, 5353, SERVICE_TYPE_LABELS,
                        Collections.emptyMap() /* textAttributes */, TEST_TTL),
                socketKey);

        // Address update from otherInstanceName
        processResponse(createResponse(
                otherInstance, ipV6Address, 5353, SERVICE_TYPE_LABELS,
                Collections.emptyMap(), TEST_TTL), socketKey);

        // Goodbye from otherInstanceName
        processResponse(createResponse(
                otherInstance, ipV6Address, 5353, SERVICE_TYPE_LABELS,
                Collections.emptyMap(), 0L /* ttl */), socketKey);

        // mockListenerOne gets notified for the requested instance
        final ArgumentMatcher<MdnsServiceInfo> subtypeInstanceMatcher = info ->
                info.getServiceInstanceName().equals(matchingInstance)
                        && info.getSubtypes().equals(Collections.singletonList(subtype));
        verify(mockListenerOne).onServiceNameDiscovered(
                argThat(subtypeInstanceMatcher), eq(false) /* isServiceFromCache */);
        verify(mockListenerOne).onServiceFound(
                argThat(subtypeInstanceMatcher), eq(false) /* isServiceFromCache */);

        // ...but does not get any callback for the other instance
        verify(mockListenerOne, never()).onServiceFound(
                matchServiceName(otherInstance), anyBoolean());
        verify(mockListenerOne, never()).onServiceNameDiscovered(
                matchServiceName(otherInstance), anyBoolean());
        verify(mockListenerOne, never()).onServiceUpdated(matchServiceName(otherInstance));
        verify(mockListenerOne, never()).onServiceRemoved(matchServiceName(otherInstance));

        // mockListenerTwo gets notified for both though
        final InOrder inOrder = inOrder(mockListenerTwo);
        inOrder.verify(mockListenerTwo).onServiceNameDiscovered(
                argThat(subtypeInstanceMatcher), eq(false) /* isServiceFromCache */);
        inOrder.verify(mockListenerTwo).onServiceFound(
                argThat(subtypeInstanceMatcher), eq(false) /* isServiceFromCache */);

        inOrder.verify(mockListenerTwo).onServiceNameDiscovered(
                matchServiceName(otherInstance), eq(false) /* isServiceFromCache */);
        inOrder.verify(mockListenerTwo).onServiceFound(
                matchServiceName(otherInstance), eq(false) /* isServiceFromCache */);
        inOrder.verify(mockListenerTwo).onServiceUpdated(matchServiceName(otherInstance));
        inOrder.verify(mockListenerTwo).onServiceRemoved(matchServiceName(otherInstance));
    }

    @Test
    public void testProcessResponse_SubtypeChange() {
        client = makeMdnsServiceTypeClient(/* packetWriter= */ null);

        final String matchingInstance = "instance1";
        final String subtype = "_subtype";
        final String ipV4Address = "192.0.2.0";
        final String ipV6Address = "2001:db8::";

        final MdnsSearchOptions options = MdnsSearchOptions.newBuilder()
                .addSubtype("othersub").build();

        startSendAndReceive(mockListenerOne, options);

        // Complete response from instanceName
        final MdnsPacket packetWithoutSubtype = createResponse(
                matchingInstance, ipV4Address, 5353, SERVICE_TYPE_LABELS,
                Collections.emptyMap() /* textAttributes */, TEST_TTL);
        final MdnsPointerRecord originalPtr = (MdnsPointerRecord) CollectionUtils.findFirst(
                packetWithoutSubtype.answers, r -> r instanceof MdnsPointerRecord);

        // Add a subtype PTR record
        final ArrayList<MdnsRecord> newAnswers = new ArrayList<>(packetWithoutSubtype.answers);
        newAnswers.add(new MdnsPointerRecord(
                // PTR should be _subtype._sub._type._tcp.local -> instance1._type._tcp.local
                Stream.concat(Stream.of(subtype, "_sub"), Arrays.stream(SERVICE_TYPE_LABELS))
                        .toArray(String[]::new),
                originalPtr.getReceiptTime(), originalPtr.getCacheFlush(), originalPtr.getTtl(),
                originalPtr.getPointer()));
        processResponse(new MdnsPacket(
                packetWithoutSubtype.flags,
                packetWithoutSubtype.questions,
                newAnswers,
                packetWithoutSubtype.authorityRecords,
                packetWithoutSubtype.additionalRecords), socketKey);

        // The subtype does not match
        final InOrder inOrder = inOrder(mockListenerOne);
        inOrder.verify(mockListenerOne, never()).onServiceNameDiscovered(any(), anyBoolean());

        // Add another matching subtype
        newAnswers.add(new MdnsPointerRecord(
                // PTR should be _subtype._sub._type._tcp.local -> instance1._type._tcp.local
                Stream.concat(Stream.of("_othersub", "_sub"), Arrays.stream(SERVICE_TYPE_LABELS))
                        .toArray(String[]::new),
                originalPtr.getReceiptTime(), originalPtr.getCacheFlush(), originalPtr.getTtl(),
                originalPtr.getPointer()));
        processResponse(new MdnsPacket(
                packetWithoutSubtype.flags,
                packetWithoutSubtype.questions,
                newAnswers,
                packetWithoutSubtype.authorityRecords,
                packetWithoutSubtype.additionalRecords), socketKey);

        final ArgumentMatcher<MdnsServiceInfo> subtypeInstanceMatcher = info ->
                info.getServiceInstanceName().equals(matchingInstance)
                        && info.getSubtypes().equals(List.of("_subtype", "_othersub"));

        // Service found callbacks are sent now
        inOrder.verify(mockListenerOne).onServiceNameDiscovered(
                argThat(subtypeInstanceMatcher), eq(false) /* isServiceFromCache */);
        inOrder.verify(mockListenerOne).onServiceFound(
                argThat(subtypeInstanceMatcher), eq(false) /* isServiceFromCache */);

        // Address update: update callbacks are sent
        processResponse(createResponse(
                matchingInstance, ipV6Address, 5353, SERVICE_TYPE_LABELS,
                Collections.emptyMap(), TEST_TTL), socketKey);

        inOrder.verify(mockListenerOne).onServiceUpdated(argThat(info ->
                subtypeInstanceMatcher.matches(info)
                        && info.getIpv4Addresses().equals(List.of(ipV4Address))
                        && info.getIpv6Addresses().equals(List.of(ipV6Address))));

        // Goodbye: service removed callbacks are sent
        processResponse(createResponse(
                matchingInstance, ipV6Address, 5353, SERVICE_TYPE_LABELS,
                Collections.emptyMap(), 0L /* ttl */), socketKey);

        inOrder.verify(mockListenerOne).onServiceRemoved(matchServiceName(matchingInstance));
        inOrder.verify(mockListenerOne).onServiceNameRemoved(matchServiceName(matchingInstance));
    }

    @Test
    public void testNotifySocketDestroyed() throws Exception {
        client = makeMdnsServiceTypeClient(/* packetWriter= */ null);

        final String requestedInstance = "instance1";
        final String otherInstance = "instance2";
        final String ipV4Address = "192.0.2.0";

        final MdnsSearchOptions resolveOptions = MdnsSearchOptions.newBuilder()
                .setNumOfQueriesBeforeBackoff(Integer.MAX_VALUE)
                .setResolveInstanceName("instance1").build();

        startSendAndReceive(mockListenerOne, resolveOptions);
        // Always try to remove the task.
        verify(mockDeps, times(1)).removeMessages(any(), eq(EVENT_START_QUERYTASK));
        // Ensure the first task is executed so it schedules a future task
        currentThreadExecutor.getAndClearSubmittedFuture().get(
                TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        startSendAndReceive(mockListenerTwo,
                MdnsSearchOptions.newBuilder().setNumOfQueriesBeforeBackoff(
                        Integer.MAX_VALUE).build());

        // Filing the second request cancels the first future
        verify(mockDeps, times(2)).removeMessages(any(), eq(EVENT_START_QUERYTASK));

        // Ensure it gets executed too
        currentThreadExecutor.getAndClearSubmittedFuture().get(
                TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // Complete response from instanceName
        processResponse(createResponse(
                        requestedInstance, ipV4Address, 5353, SERVICE_TYPE_LABELS,
                        Collections.emptyMap() /* textAttributes */, TEST_TTL),
                socketKey);

        // Complete response from otherInstanceName
        processResponse(createResponse(
                        otherInstance, ipV4Address, 5353, SERVICE_TYPE_LABELS,
                        Collections.emptyMap() /* textAttributes */, TEST_TTL),
                socketKey);

        notifySocketDestroyed();
        verify(mockDeps, times(3)).removeMessages(any(), eq(EVENT_START_QUERYTASK));

        // mockListenerOne gets notified for the requested instance
        final InOrder inOrder1 = inOrder(mockListenerOne);
        inOrder1.verify(mockListenerOne).onServiceNameDiscovered(
                matchServiceName(requestedInstance), eq(false) /* isServiceFromCache */);
        inOrder1.verify(mockListenerOne).onServiceFound(
                matchServiceName(requestedInstance), eq(false) /* isServiceFromCache */);
        inOrder1.verify(mockListenerOne).onServiceRemoved(matchServiceName(requestedInstance));
        inOrder1.verify(mockListenerOne).onServiceNameRemoved(matchServiceName(requestedInstance));
        verify(mockListenerOne, never()).onServiceFound(
                matchServiceName(otherInstance), anyBoolean());
        verify(mockListenerOne, never()).onServiceNameDiscovered(
                matchServiceName(otherInstance), anyBoolean());
        verify(mockListenerOne, never()).onServiceRemoved(matchServiceName(otherInstance));
        verify(mockListenerOne, never()).onServiceNameRemoved(matchServiceName(otherInstance));

        // mockListenerTwo gets notified for both though
        final InOrder inOrder2 = inOrder(mockListenerTwo);
        inOrder2.verify(mockListenerTwo).onServiceNameDiscovered(
                matchServiceName(requestedInstance), eq(false) /* isServiceFromCache */);
        inOrder2.verify(mockListenerTwo).onServiceFound(
                matchServiceName(requestedInstance), eq(false) /* isServiceFromCache */);
        inOrder2.verify(mockListenerTwo).onServiceRemoved(matchServiceName(requestedInstance));
        inOrder2.verify(mockListenerTwo).onServiceNameRemoved(matchServiceName(requestedInstance));
        verify(mockListenerTwo).onServiceNameDiscovered(
                matchServiceName(otherInstance), eq(false) /* isServiceFromCache */);
        verify(mockListenerTwo).onServiceFound(
                matchServiceName(otherInstance), eq(false) /* isServiceFromCache */);
        verify(mockListenerTwo).onServiceRemoved(matchServiceName(otherInstance));
        verify(mockListenerTwo).onServiceNameRemoved(matchServiceName(otherInstance));
    }

    @Test
    public void testServicesAreCached() throws Exception {
        final String serviceName = "service-instance";
        final String ipV4Address = "192.0.2.0";
        // Register a listener
        startSendAndReceive(mockListenerOne, MdnsSearchOptions.getDefaultOptions());
        verify(mockDeps, times(1)).removeMessages(any(), eq(EVENT_START_QUERYTASK));
        InOrder inOrder = inOrder(mockListenerOne);

        // Process a response which has ip address to make response become complete.

        processResponse(createResponse(
                        serviceName, ipV4Address, 5353, SUBTYPE,
                        Collections.emptyMap(), TEST_TTL),
                socketKey);

        // Verify that onServiceNameDiscovered is called.
        inOrder.verify(mockListenerOne).onServiceNameDiscovered(
                serviceInfoCaptor.capture(), eq(false) /* isServiceFromCache */);
        verifyServiceInfo(serviceInfoCaptor.getAllValues().get(0),
                serviceName,
                SERVICE_TYPE_LABELS,
                List.of(ipV4Address) /* ipv4Address */,
                List.of() /* ipv6Address */,
                5353 /* port */,
                Collections.singletonList(SUBTYPE) /* subTypes */,
                Collections.singletonMap("key", null) /* attributes */,
                socketKey);

        // Verify that onServiceFound is called.
        inOrder.verify(mockListenerOne).onServiceFound(
                serviceInfoCaptor.capture(), eq(false) /* isServiceFromCache */);
        verifyServiceInfo(serviceInfoCaptor.getAllValues().get(1),
                serviceName,
                SERVICE_TYPE_LABELS,
                List.of(ipV4Address) /* ipv4Address */,
                List.of() /* ipv6Address */,
                5353 /* port */,
                Collections.singletonList(SUBTYPE) /* subTypes */,
                Collections.singletonMap("key", null) /* attributes */,
                socketKey);

        // Unregister the listener
        stopSendAndReceive(mockListenerOne);
        verify(mockDeps, times(2)).removeMessages(any(), eq(EVENT_START_QUERYTASK));

        // Register another listener.
        startSendAndReceive(mockListenerTwo, MdnsSearchOptions.getDefaultOptions());
        verify(mockDeps, times(3)).removeMessages(any(), eq(EVENT_START_QUERYTASK));
        InOrder inOrder2 = inOrder(mockListenerTwo);

        // The services are cached in MdnsServiceCache, verify that onServiceNameDiscovered is
        // called immediately.
        inOrder2.verify(mockListenerTwo).onServiceNameDiscovered(
                serviceInfoCaptor.capture(), eq(true) /* isServiceFromCache */);
        verifyServiceInfo(serviceInfoCaptor.getAllValues().get(2),
                serviceName,
                SERVICE_TYPE_LABELS,
                List.of(ipV4Address) /* ipv4Address */,
                List.of() /* ipv6Address */,
                5353 /* port */,
                Collections.singletonList(SUBTYPE) /* subTypes */,
                Collections.singletonMap("key", null) /* attributes */,
                socketKey);

        // The services are cached in MdnsServiceCache, verify that onServiceFound is
        // called immediately.
        inOrder2.verify(mockListenerTwo).onServiceFound(
                serviceInfoCaptor.capture(), eq(true) /* isServiceFromCache */);
        verifyServiceInfo(serviceInfoCaptor.getAllValues().get(3),
                serviceName,
                SERVICE_TYPE_LABELS,
                List.of(ipV4Address) /* ipv4Address */,
                List.of() /* ipv6Address */,
                5353 /* port */,
                Collections.singletonList(SUBTYPE) /* subTypes */,
                Collections.singletonMap("key", null) /* attributes */,
                socketKey);

        // Process a response with a different ip address, port and updated text attributes.
        final String ipV6Address = "2001:db8::";
        processResponse(createResponse(
                serviceName, ipV6Address, 5354, SUBTYPE,
                Collections.singletonMap("key", "value"), TEST_TTL), socketKey);

        // Verify the onServiceUpdated is called.
        inOrder2.verify(mockListenerTwo).onServiceUpdated(serviceInfoCaptor.capture());
        verifyServiceInfo(serviceInfoCaptor.getAllValues().get(4),
                serviceName,
                SERVICE_TYPE_LABELS,
                List.of(ipV4Address) /* ipv4Address */,
                List.of(ipV6Address) /* ipv6Address */,
                5354 /* port */,
                Collections.singletonList(SUBTYPE) /* subTypes */,
                Collections.singletonMap("key", "value") /* attributes */,
                socketKey);
    }

    @Test
    public void sendQueries_aggressiveScanMode() {
        final MdnsSearchOptions searchOptions = MdnsSearchOptions.newBuilder()
                .addSubtype(SUBTYPE).setQueryMode(AGGRESSIVE_QUERY_MODE).build();
        startSendAndReceive(mockListenerOne, searchOptions);
        // Always try to remove the task.
        verify(mockDeps, times(1)).removeMessages(any(), eq(EVENT_START_QUERYTASK));

        int burstCounter = 0;
        int betweenBurstTime = 0;
        for (int i = 0; i < expectedIPv4Packets.length; i += 3) {
            verifyAndSendQuery(i, betweenBurstTime, /* expectsUnicastResponse= */ true);
            verifyAndSendQuery(i + 1, /* timeInMs= */ 0, /* expectsUnicastResponse= */ false);
            verifyAndSendQuery(i + 2, TIME_BETWEEN_RETRANSMISSION_QUERIES_IN_BURST_MS,
                    /* expectsUnicastResponse= */ false);
            betweenBurstTime = Math.min(
                    INITIAL_AGGRESSIVE_TIME_BETWEEN_BURSTS_MS * (int) Math.pow(2, burstCounter),
                    MAX_TIME_BETWEEN_AGGRESSIVE_BURSTS_MS);
            burstCounter++;
        }
        // Verify that Task is not removed before stopSendAndReceive was called.
        verify(mockDeps, times(1)).removeMessages(any(), eq(EVENT_START_QUERYTASK));

        // Stop sending packets.
        stopSendAndReceive(mockListenerOne);
        verify(mockDeps, times(2)).removeMessages(any(), eq(EVENT_START_QUERYTASK));
    }

    @Test
    public void sendQueries_reentry_aggressiveScanMode() {
        final MdnsSearchOptions searchOptions = MdnsSearchOptions.newBuilder()
                .addSubtype(SUBTYPE).setQueryMode(AGGRESSIVE_QUERY_MODE).build();
        startSendAndReceive(mockListenerOne, searchOptions);
        // Always try to remove the task.
        verify(mockDeps, times(1)).removeMessages(any(), eq(EVENT_START_QUERYTASK));

        // First burst, first query is sent.
        verifyAndSendQuery(0, /* timeInMs= */ 0, /* expectsUnicastResponse= */ true);

        // After the first query is sent, change the subtypes, and restart.
        final MdnsSearchOptions searchOptions2 = MdnsSearchOptions.newBuilder().addSubtype(SUBTYPE)
                .addSubtype("_subtype2").setQueryMode(AGGRESSIVE_QUERY_MODE).build();
        startSendAndReceive(mockListenerOne, searchOptions2);
        // The previous scheduled task should be canceled.
        verify(mockDeps, times(2)).removeMessages(any(), eq(EVENT_START_QUERYTASK));

        // Queries should continue to be sent.
        verifyAndSendQuery(1, /* timeInMs= */ 0, /* expectsUnicastResponse= */ true);
        verifyAndSendQuery(2, /* timeInMs= */ 0, /* expectsUnicastResponse= */ false);
        verifyAndSendQuery(3, TIME_BETWEEN_RETRANSMISSION_QUERIES_IN_BURST_MS,
                /* expectsUnicastResponse= */ false);

        // Stop sending packets.
        stopSendAndReceive(mockListenerOne);
        verify(mockDeps, times(3)).removeMessages(any(), eq(EVENT_START_QUERYTASK));
    }

    @Test
    public void sendQueries_blendScanWithQueryBackoff() {
        final int numOfQueriesBeforeBackoff = 11;
        final MdnsSearchOptions searchOptions = MdnsSearchOptions.newBuilder()
                .addSubtype(SUBTYPE)
                .setQueryMode(AGGRESSIVE_QUERY_MODE)
                .setNumOfQueriesBeforeBackoff(numOfQueriesBeforeBackoff)
                .build();
        startSendAndReceive(mockListenerOne, searchOptions);
        // Always try to remove the task.
        verify(mockDeps, times(1)).removeMessages(any(), eq(EVENT_START_QUERYTASK));

        int burstCounter = 0;
        int betweenBurstTime = 0;
        for (int i = 0; i < numOfQueriesBeforeBackoff; i += 3) {
            verifyAndSendQuery(i, betweenBurstTime, /* expectsUnicastResponse= */ true);
            verifyAndSendQuery(i + 1, /* timeInMs= */ 0, /* expectsUnicastResponse= */ false);
            verifyAndSendQuery(i + 2, TIME_BETWEEN_RETRANSMISSION_QUERIES_IN_BURST_MS,
                    /* expectsUnicastResponse= */ false);
            betweenBurstTime = Math.min(
                    INITIAL_AGGRESSIVE_TIME_BETWEEN_BURSTS_MS * (int) Math.pow(2, burstCounter),
                    MAX_TIME_BETWEEN_AGGRESSIVE_BURSTS_MS);
            burstCounter++;
        }
        // In backoff mode, the current scheduled task will be canceled and reschedule if the
        // 0.8 * smallestRemainingTtl is larger than time to next run.
        long currentTime = TEST_TTL / 2 + TEST_ELAPSED_REALTIME;
        doReturn(currentTime).when(mockDecoderClock).elapsedRealtime();
        doReturn(true).when(mockDeps).hasMessages(any(), eq(EVENT_START_QUERYTASK));
        processResponse(createResponse(
                "service-instance-1", "192.0.2.123", 5353,
                SERVICE_TYPE_LABELS,
                Collections.emptyMap(), TEST_TTL), socketKey);
        verify(mockDeps, times(2)).removeMessages(any(), eq(EVENT_START_QUERYTASK));
        assertNotNull(delayMessage);
        verifyAndSendQuery(12 /* index */, (long) (TEST_TTL / 2 * 0.8) /* timeInMs */,
                true /* expectsUnicastResponse */, true /* multipleSocketDiscovery */,
                14 /* scheduledCount */);
        currentTime += (long) (TEST_TTL / 2 * 0.8);
        doReturn(currentTime).when(mockDecoderClock).elapsedRealtime();
        verifyAndSendQuery(13 /* index */, 0 /* timeInMs */,
                false /* expectsUnicastResponse */, true /* multipleSocketDiscovery */,
                15 /* scheduledCount */);
        verifyAndSendQuery(14 /* index */, TIME_BETWEEN_RETRANSMISSION_QUERIES_IN_BURST_MS,
                false /* expectsUnicastResponse */, true /* multipleSocketDiscovery */,
                16 /* scheduledCount */);
    }

    private static MdnsServiceInfo matchServiceName(String name) {
        return argThat(info -> info.getServiceInstanceName().equals(name));
    }

    // verifies that the right query was enqueued with the right delay, and send query by executing
    // the runnable.
    private void verifyAndSendQuery(int index, long timeInMs, boolean expectsUnicastResponse) {
        verifyAndSendQuery(index, timeInMs, expectsUnicastResponse,
                true /* multipleSocketDiscovery */, index + 1 /* scheduledCount */);
    }

    private void verifyAndSendQuery(int index, long timeInMs, boolean expectsUnicastResponse,
            boolean multipleSocketDiscovery, int scheduledCount) {
        // Dispatch the message
        if (delayMessage != null && realHandler != null) {
            dispatchMessage();
        }
        assertEquals(timeInMs, latestDelayMs);
        currentThreadExecutor.getAndClearLastScheduledRunnable().run();
        if (expectsUnicastResponse) {
            verify(mockSocketClient).sendPacketRequestingUnicastResponse(
                    expectedIPv4Packets[index], socketKey, false);
            if (multipleSocketDiscovery) {
                verify(mockSocketClient).sendPacketRequestingUnicastResponse(
                        expectedIPv6Packets[index], socketKey, false);
            }
        } else {
            verify(mockSocketClient).sendPacketRequestingMulticastResponse(
                    expectedIPv4Packets[index], socketKey, false);
            if (multipleSocketDiscovery) {
                verify(mockSocketClient).sendPacketRequestingMulticastResponse(
                        expectedIPv6Packets[index], socketKey, false);
            }
        }
        verify(mockDeps, times(index + 1))
                .sendMessage(any(Handler.class), any(Message.class));
        // Verify the task has been scheduled.
        verify(mockDeps, times(scheduledCount))
                .sendMessageDelayed(any(Handler.class), any(Message.class), anyLong());
    }

    private static String[] getTestServiceName(String instanceName) {
        return Stream.concat(Stream.of(instanceName),
                Arrays.stream(SERVICE_TYPE_LABELS)).toArray(String[]::new);
    }

    private static String[] getServiceTypeWithSubtype(String subtype) {
        return Stream.concat(Stream.of(subtype, "_sub"),
                Arrays.stream(SERVICE_TYPE_LABELS)).toArray(String[]::new);
    }

    private static boolean hasQuestion(MdnsPacket packet, int type) {
        return hasQuestion(packet, type, null);
    }

    private static boolean hasQuestion(MdnsPacket packet, int type, @Nullable String[] name) {
        return packet.questions.stream().anyMatch(q -> q.getType() == type
                && (name == null || Arrays.equals(q.name, name)));
    }

    // A fake ScheduledExecutorService that keeps tracking the last scheduled Runnable and its delay
    // time.
    private class FakeExecutor extends ScheduledThreadPoolExecutor {
        private long lastScheduledDelayInMs;
        private Runnable lastScheduledRunnable;
        private Runnable lastSubmittedRunnable;
        private Future<?> lastSubmittedFuture;
        private int futureIndex;

        FakeExecutor() {
            super(1);
            lastScheduledDelayInMs = -1;
        }

        @Override
        public Future<?> submit(Runnable command) {
            Future<?> future = super.submit(command);
            lastSubmittedRunnable = command;
            lastSubmittedFuture = future;
            return future;
        }

        // Don't call through the real implementation, just track the scheduled Runnable, and
        // returns a ScheduledFuture.
        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            lastScheduledDelayInMs = delay;
            lastScheduledRunnable = command;
            return Mockito.mock(ScheduledFuture.class);
        }

        // Returns the delay of the last scheduled task, and clear it.
        long getAndClearLastScheduledDelayInMs() {
            long val = lastScheduledDelayInMs;
            lastScheduledDelayInMs = -1;
            return val;
        }

        // Returns the last scheduled task, and clear it.
        Runnable getAndClearLastScheduledRunnable() {
            Runnable val = lastScheduledRunnable;
            lastScheduledRunnable = null;
            return val;
        }

        Runnable getAndClearSubmittedRunnable() {
            Runnable val = lastSubmittedRunnable;
            lastSubmittedRunnable = null;
            return val;
        }

        Future<?> getAndClearSubmittedFuture() {
            Future<?> val = lastSubmittedFuture;
            lastSubmittedFuture = null;
            return val;
        }
    }

    private MdnsPacket createResponse(
            @NonNull String serviceInstanceName,
            @Nullable String host,
            int port,
            @NonNull String subtype,
            @NonNull Map<String, String> textAttributes,
            long ptrTtlMillis)
            throws Exception {
        final ArrayList<String> type = new ArrayList<>();
        type.add(subtype);
        type.add(MdnsConstants.SUBTYPE_LABEL);
        type.addAll(Arrays.asList(SERVICE_TYPE_LABELS));
        return createResponse(serviceInstanceName, host, port, type.toArray(new String[0]),
                textAttributes, ptrTtlMillis);
    }


    private MdnsPacket createResponse(
            @NonNull String serviceInstanceName,
            @Nullable String host,
            int port,
            @NonNull String[] type,
            @NonNull Map<String, String> textAttributes,
            long ptrTtlMillis) {
        return createResponse(serviceInstanceName, host, port, type, textAttributes, ptrTtlMillis,
                TEST_ELAPSED_REALTIME);
    }

    // Creates a mDNS response.
    private MdnsPacket createResponse(
            @NonNull String serviceInstanceName,
            @Nullable String host,
            int port,
            @NonNull String[] type,
            @NonNull Map<String, String> textAttributes,
            long ptrTtlMillis,
            long receiptTimeMillis) {

        final ArrayList<MdnsRecord> answerRecords = new ArrayList<>();

        // Set PTR record
        final ArrayList<String> serviceNameList = new ArrayList<>();
        serviceNameList.add(serviceInstanceName);
        serviceNameList.addAll(Arrays.asList(type));
        final String[] serviceName = serviceNameList.toArray(new String[0]);
        final MdnsPointerRecord pointerRecord = new MdnsPointerRecord(
                type,
                receiptTimeMillis,
                false /* cacheFlush */,
                ptrTtlMillis,
                serviceName);
        answerRecords.add(pointerRecord);

        // Set SRV record.
        final MdnsServiceRecord serviceRecord = new MdnsServiceRecord(
                serviceName,
                receiptTimeMillis,
                false /* cacheFlush */,
                TEST_TTL,
                0 /* servicePriority */,
                0 /* serviceWeight */,
                port,
                new String[]{"hostname"});
        answerRecords.add(serviceRecord);

        // Set A/AAAA record.
        if (host != null) {
            final InetAddress addr = InetAddresses.parseNumericAddress(host);
            final MdnsInetAddressRecord inetAddressRecord = new MdnsInetAddressRecord(
                    new String[] {"hostname"} /* name */,
                    receiptTimeMillis,
                    false /* cacheFlush */,
                    TEST_TTL,
                    addr);
            answerRecords.add(inetAddressRecord);
        }

        // Set TXT record.
        final List<TextEntry> textEntries = new ArrayList<>();
        for (Map.Entry<String, String> kv : textAttributes.entrySet()) {
            textEntries.add(new TextEntry(kv.getKey(), kv.getValue().getBytes(UTF_8)));
        }
        final MdnsTextRecord textRecord = new MdnsTextRecord(
                serviceName,
                receiptTimeMillis,
                false /* cacheFlush */,
                TEST_TTL,
                textEntries);
        answerRecords.add(textRecord);
        return new MdnsPacket(
                0 /* flags */,
                Collections.emptyList() /* questions */,
                answerRecords,
                Collections.emptyList() /* authorityRecords */,
                Collections.emptyList() /* additionalRecords */
        );
    }
}