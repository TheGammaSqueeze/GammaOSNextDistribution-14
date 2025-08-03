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

/**
 * mDNS configuration values.
 *
 * TODO: consider making some of these adjustable via flags.
 */
public class MdnsConfigs {
    public static String[] castShellEmulatorMdnsPorts() {
        return new String[0];
    }

    public static long initialTimeBetweenBurstsMs() {
        return 5000L;
    }

    public static long timeBetweenBurstsMs() {
        return 20_000L;
    }

    public static int queriesPerBurst() {
        return 3;
    }

    public static long timeBetweenQueriesInBurstMs() {
        return 1000L;
    }

    public static int queriesPerBurstPassive() {
        return 1;
    }

    public static boolean alwaysAskForUnicastResponseInEachBurst() {
        return false;
    }

    public static long sleepTimeForSocketThreadMs() {
        return 20_000L;
    }

    public static boolean checkMulticastResponse() {
        return false;
    }

    public static boolean useSeparateSocketToSendUnicastQuery() {
        return false;
    }

    public static long checkMulticastResponseIntervalMs() {
        return 10_000L;
    }

    public static boolean clearMdnsPacketQueueAfterDiscoveryStops() {
        return true;
    }

    public static boolean allowAddMdnsPacketAfterDiscoveryStops() {
        return false;
    }

    public static int mdnsPacketQueueMaxSize() {
        return Integer.MAX_VALUE;
    }

    public static boolean preferIpv6() {
        return false;
    }

    public static boolean removeServiceAfterTtlExpires() {
        return false;
    }

    public static boolean allowNetworkInterfaceIndexPropagation() {
        return true;
    }

    public static boolean allowMultipleSrvRecordsPerHost() {
        return true;
    }
}