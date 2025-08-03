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

import android.annotation.NonNull;

import java.util.List;

/**
 * Listener interface for mDNS service instance discovery events.
 *
 * @hide
 */
public interface MdnsServiceBrowserListener {

    /**
     * Called when an mDNS service instance is found. This method would be called only if all
     * service records (PTR, SRV, TXT, A or AAAA) are received .
     *
     * @param serviceInfo The found mDNS service instance.
     * @param isServiceFromCache Whether the found mDNS service is from cache.
     */
    void onServiceFound(@NonNull MdnsServiceInfo serviceInfo, boolean isServiceFromCache);

    /**
     * Called when an mDNS service instance is updated. This method would be called only if all
     * service records (PTR, SRV, TXT, A or AAAA) are received before.
     *
     * @param serviceInfo The updated mDNS service instance.
     */
    void onServiceUpdated(@NonNull MdnsServiceInfo serviceInfo);

    /**
     * Called when a mDNS service instance is no longer valid and removed. This method would be
     * called only if all service records (PTR, SRV, TXT, A or AAAA) are received before.
     *
     * @param serviceInfo The service instance of the removed mDNS service.
     */
    void onServiceRemoved(@NonNull MdnsServiceInfo serviceInfo);

    /**
     * Called when searching for mDNS service has stopped because of an error.
     *
     * TODO (changed when importing code): define error constants
     *
     * @param error The error code of the stop reason.
     */
    void onSearchStoppedWithError(int error);

    /** Called when it failed to start an mDNS service discovery process. */
    void onSearchFailedToStart();

    /**
     * Called when a mDNS service discovery query has been sent.
     *
     * @param subtypes      The list of subtypes in the discovery query.
     * @param transactionId The transaction ID of the query.
     */
    void onDiscoveryQuerySent(@NonNull List<String> subtypes, int transactionId);

    /**
     * Called when an error has happened when parsing a received mDNS response packet.
     *
     * @param receivedPacketNumber The packet sequence number of the received packet.
     * @param errorCode            The error code, defined in {@link MdnsResponseErrorCode}.
     */
    void onFailedToParseMdnsResponse(int receivedPacketNumber, int errorCode);

    /**
     * Called when a mDNS service instance is discovered. This method would be called if the PTR
     * record has been received.
     *
     * @param serviceInfo The discovered mDNS service instance.
     * @param isServiceFromCache Whether the discovered mDNS service is from cache.
     */
    void onServiceNameDiscovered(@NonNull MdnsServiceInfo serviceInfo, boolean isServiceFromCache);

    /**
     * Called when a discovered mDNS service instance is no longer valid and removed.
     *
     * @param serviceInfo The service instance of the removed mDNS service.
     */
    void onServiceNameRemoved(@NonNull MdnsServiceInfo serviceInfo);
}