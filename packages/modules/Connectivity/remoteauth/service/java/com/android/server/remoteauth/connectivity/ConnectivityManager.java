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

package com.android.server.remoteauth.connectivity;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Performs discovery and triggers a connection to an associated device. */
public interface ConnectivityManager {
    /**
     * Starts device discovery.
     *
     * <p>Discovery continues until stopped using {@link stopDiscovery} or times out.
     *
     * @param discoveryFilter to filter for devices during discovery.
     * @param discoveredDeviceReceiver callback to run when device is found or lost.
     */
    void startDiscovery(
            @NonNull DiscoveryFilter discoveryFilter,
            @NonNull DiscoveredDeviceReceiver discoveredDeviceReceiver);

    /**
     * Stops device discovery.
     *
     * @param discoveryFilter filter used to start discovery.
     * @param discoveredDeviceReceiver callback passed with startDiscovery.
     */
    void stopDiscovery(
            @NonNull DiscoveryFilter discoveryFilter,
            @NonNull DiscoveredDeviceReceiver discoveredDeviceReceiver);

    /** Unknown reason for connection failure. */
    int ERROR_REASON_UNKNOWN = 0;

    /** Indicates that the connection request timed out. */
    int ERROR_CONNECTION_TIMED_OUT = 1;

    /** Indicates that the connection request was refused by the peer. */
    int ERROR_CONNECTION_REFUSED = 2;

    /** Indicates that the peer device was unavailable. */
    int ERROR_DEVICE_UNAVAILABLE = 3;

    /** Reason codes for connect failure. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        ERROR_REASON_UNKNOWN,
        ERROR_CONNECTION_TIMED_OUT,
        ERROR_CONNECTION_REFUSED,
        ERROR_DEVICE_UNAVAILABLE
    })
    @interface ReasonCode {}

    /**
     * Initiates a connection with the peer device.
     *
     * @param connectionInfo of the device discovered using {@link startDiscovery}.
     * @param eventListener to listen for events from the underlying transport.
     * @return {@link Connection} object or null connection is not established.
     * @throws ConnectionException in case connection cannot be established.
     */
    @Nullable
    Connection connect(
            @NonNull ConnectionInfo connectionInfo, @NonNull EventListener eventListener);

    /**
     * Message received callback.
     *
     * <p>Clients should implement this callback to receive messages from the peer device.
     */
    abstract class MessageReceiver {
        /**
         * Receive message from the peer device.
         *
         * <p>Clients can set empty buffer as an ACK to the request.
         *
         * @param messageIn message from peer device.
         * @param responseCallback {@link ResponseCallback} callback to send the response back.
         */
        public void onMessageReceived(byte[] messageIn, ResponseCallback responseCallback) {}
    }

    /**
     * Starts listening for incoming messages.
     *
     * <p>Runs MessageReceiver callback when a message is received.
     *
     * @param messageReceiver to receive messages.
     * @throws AssertionError if a listener is already configured.
     */
    void startListening(MessageReceiver messageReceiver);

    /**
     * Stops listening to incoming messages.
     *
     * @param messageReceiver to receive messages.
     */
    void stopListening(MessageReceiver messageReceiver);
}
