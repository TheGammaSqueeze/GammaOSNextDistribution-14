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

package com.android.server.connectivity.mdns;

import static com.android.server.connectivity.mdns.MdnsSearchOptions.AGGRESSIVE_QUERY_MODE;
import static com.android.server.connectivity.mdns.MdnsSearchOptions.PASSIVE_QUERY_MODE;

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;

/**
 * A configuration for the PeriodicalQueryTask that contains parameters to build a query packet.
 * Call to getConfigForNextRun returns a config that can be used to build the next query task.
 */
public class QueryTaskConfig {

    private static final int INITIAL_TIME_BETWEEN_BURSTS_MS =
            (int) MdnsConfigs.initialTimeBetweenBurstsMs();
    private static final int MAX_TIME_BETWEEN_ACTIVE_PASSIVE_BURSTS_MS =
            (int) MdnsConfigs.timeBetweenBurstsMs();
    private static final int QUERIES_PER_BURST = (int) MdnsConfigs.queriesPerBurst();
    private static final int TIME_BETWEEN_QUERIES_IN_BURST_MS =
            (int) MdnsConfigs.timeBetweenQueriesInBurstMs();
    private static final int QUERIES_PER_BURST_PASSIVE_MODE =
            (int) MdnsConfigs.queriesPerBurstPassive();
    private static final int UNSIGNED_SHORT_MAX_VALUE = 65536;
    @VisibleForTesting
    // RFC 6762 5.2: The interval between the first two queries MUST be at least one second.
    static final int INITIAL_AGGRESSIVE_TIME_BETWEEN_BURSTS_MS = 1000;
    @VisibleForTesting
    // Basically this tries to send one query per typical DTIM interval 100ms, to maximize the
    // chances that a query will be received if devices are using a DTIM multiplier (in which case
    // they only listen once every [multiplier] DTIM intervals).
    static final int TIME_BETWEEN_RETRANSMISSION_QUERIES_IN_BURST_MS = 100;
    static final int MAX_TIME_BETWEEN_AGGRESSIVE_BURSTS_MS = 60000;
    private final boolean alwaysAskForUnicastResponse =
            MdnsConfigs.alwaysAskForUnicastResponseInEachBurst();
    private final int queryMode;
    final boolean onlyUseIpv6OnIpv6OnlyNetworks;
    private final int numOfQueriesBeforeBackoff;
    @VisibleForTesting
    final int transactionId;
    @VisibleForTesting
    final boolean expectUnicastResponse;
    private final int queriesPerBurst;
    private final int timeBetweenBurstsInMs;
    private final int burstCounter;
    final long delayUntilNextTaskWithoutBackoffMs;
    private final boolean isFirstBurst;
    private final long queryCount;
    @NonNull
    final SocketKey socketKey;

    QueryTaskConfig(@NonNull QueryTaskConfig other, long queryCount, int transactionId,
            boolean expectUnicastResponse, boolean isFirstBurst, int burstCounter,
            int queriesPerBurst, int timeBetweenBurstsInMs,
            long delayUntilNextTaskWithoutBackoffMs) {
        this.queryMode = other.queryMode;
        this.onlyUseIpv6OnIpv6OnlyNetworks = other.onlyUseIpv6OnIpv6OnlyNetworks;
        this.numOfQueriesBeforeBackoff = other.numOfQueriesBeforeBackoff;
        this.transactionId = transactionId;
        this.expectUnicastResponse = expectUnicastResponse;
        this.queriesPerBurst = queriesPerBurst;
        this.timeBetweenBurstsInMs = timeBetweenBurstsInMs;
        this.burstCounter = burstCounter;
        this.delayUntilNextTaskWithoutBackoffMs = delayUntilNextTaskWithoutBackoffMs;
        this.isFirstBurst = isFirstBurst;
        this.queryCount = queryCount;
        this.socketKey = other.socketKey;
    }

    QueryTaskConfig(int queryMode,
            boolean onlyUseIpv6OnIpv6OnlyNetworks,
            int numOfQueriesBeforeBackoff,
            @Nullable SocketKey socketKey) {
        this.queryMode = queryMode;
        this.onlyUseIpv6OnIpv6OnlyNetworks = onlyUseIpv6OnIpv6OnlyNetworks;
        this.numOfQueriesBeforeBackoff = numOfQueriesBeforeBackoff;
        this.queriesPerBurst = QUERIES_PER_BURST;
        this.burstCounter = 0;
        this.transactionId = 1;
        this.expectUnicastResponse = true;
        this.isFirstBurst = true;
        // Config the scan frequency based on the scan mode.
        if (this.queryMode == AGGRESSIVE_QUERY_MODE) {
            this.timeBetweenBurstsInMs = INITIAL_AGGRESSIVE_TIME_BETWEEN_BURSTS_MS;
            this.delayUntilNextTaskWithoutBackoffMs =
                    TIME_BETWEEN_RETRANSMISSION_QUERIES_IN_BURST_MS;
        } else if (this.queryMode == PASSIVE_QUERY_MODE) {
            // In passive scan mode, sends a single burst of QUERIES_PER_BURST queries, and then
            // in each TIME_BETWEEN_BURSTS interval, sends QUERIES_PER_BURST_PASSIVE_MODE
            // queries.
            this.timeBetweenBurstsInMs = MAX_TIME_BETWEEN_ACTIVE_PASSIVE_BURSTS_MS;
            this.delayUntilNextTaskWithoutBackoffMs = TIME_BETWEEN_QUERIES_IN_BURST_MS;
        } else {
            // In active scan mode, sends a burst of QUERIES_PER_BURST queries,
            // TIME_BETWEEN_QUERIES_IN_BURST_MS apart, then waits for the scan interval, and
            // then repeats. The scan interval starts as INITIAL_TIME_BETWEEN_BURSTS_MS and
            // doubles until it maxes out at TIME_BETWEEN_BURSTS_MS.
            this.timeBetweenBurstsInMs = INITIAL_TIME_BETWEEN_BURSTS_MS;
            this.delayUntilNextTaskWithoutBackoffMs = TIME_BETWEEN_QUERIES_IN_BURST_MS;
        }
        this.socketKey = socketKey;
        this.queryCount = 0;
    }

    long getDelayUntilNextTaskWithoutBackoff(boolean isFirstQueryInBurst,
            boolean isLastQueryInBurst) {
        if (isFirstQueryInBurst && queryMode == AGGRESSIVE_QUERY_MODE) {
            return 0;
        }
        if (isLastQueryInBurst) {
            return timeBetweenBurstsInMs;
        }
        return queryMode == AGGRESSIVE_QUERY_MODE
                ? TIME_BETWEEN_RETRANSMISSION_QUERIES_IN_BURST_MS
                : TIME_BETWEEN_QUERIES_IN_BURST_MS;
    }

    boolean getNextExpectUnicastResponse(boolean isLastQueryInBurst) {
        if (!isLastQueryInBurst) {
            return false;
        }
        if (queryMode == AGGRESSIVE_QUERY_MODE) {
            return true;
        }
        return alwaysAskForUnicastResponse;
    }

    int getNextTimeBetweenBurstsMs(boolean isLastQueryInBurst) {
        if (!isLastQueryInBurst) {
            return timeBetweenBurstsInMs;
        }
        final int maxTimeBetweenBursts = queryMode == AGGRESSIVE_QUERY_MODE
                ? MAX_TIME_BETWEEN_AGGRESSIVE_BURSTS_MS : MAX_TIME_BETWEEN_ACTIVE_PASSIVE_BURSTS_MS;
        return Math.min(timeBetweenBurstsInMs * 2, maxTimeBetweenBursts);
    }

    /**
     * Get new QueryTaskConfig for next run.
     */
    public QueryTaskConfig getConfigForNextRun() {
        long newQueryCount = queryCount + 1;
        int newTransactionId = transactionId + 1;
        if (newTransactionId > UNSIGNED_SHORT_MAX_VALUE) {
            newTransactionId = 1;
        }

        int newQueriesPerBurst = queriesPerBurst;
        int newBurstCounter = burstCounter + 1;
        final boolean isFirstQueryInBurst = newBurstCounter == 1;
        final boolean isLastQueryInBurst = newBurstCounter == queriesPerBurst;
        boolean newIsFirstBurst = isFirstBurst && !isLastQueryInBurst;
        if (isLastQueryInBurst) {
            newBurstCounter = 0;
            // In passive scan mode, sends a single burst of QUERIES_PER_BURST queries, and
            // then in each TIME_BETWEEN_BURSTS interval, sends QUERIES_PER_BURST_PASSIVE_MODE
            // queries.
            if (isFirstBurst && queryMode == PASSIVE_QUERY_MODE) {
                newQueriesPerBurst = QUERIES_PER_BURST_PASSIVE_MODE;
            }
        }

        return new QueryTaskConfig(this, newQueryCount, newTransactionId,
                getNextExpectUnicastResponse(isLastQueryInBurst), newIsFirstBurst, newBurstCounter,
                newQueriesPerBurst, getNextTimeBetweenBurstsMs(isLastQueryInBurst),
                getDelayUntilNextTaskWithoutBackoff(isFirstQueryInBurst, isLastQueryInBurst));
    }

    /**
     * Determine if the query backoff should be used.
     */
    public boolean shouldUseQueryBackoff() {
        // Don't enable backoff mode during the burst or in the first burst
        if (burstCounter != 0 || isFirstBurst) {
            return false;
        }
        return queryCount > numOfQueriesBeforeBackoff;
    }
}
