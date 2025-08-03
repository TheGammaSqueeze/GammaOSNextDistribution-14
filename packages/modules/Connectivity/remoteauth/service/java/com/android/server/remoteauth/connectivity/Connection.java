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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A connection with the peer device.
 *
 * <p>Connections are used to exchange data with the peer device.
 */
public interface Connection {
    /** Unknown error. */
    int ERROR_UNKNOWN = 0;

    /** Message was sent successfully. */
    int ERROR_OK = 1;

    /** Timeout occurred while waiting for response from the peer. */
    int ERROR_DEADLINE_EXCEEDED = 2;

    /** Device became unavailable while sending the message. */
    int ERROR_DEVICE_UNAVAILABLE = 3;

    /** Represents error code. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ERROR_UNKNOWN, ERROR_OK, ERROR_DEADLINE_EXCEEDED, ERROR_DEVICE_UNAVAILABLE})
    @interface ErrorCode {}

    /**
     * Callback for clients to get the response of sendRequest. {@link onSuccess} is called if the
     * peer device responds with Status::OK, otherwise runs the {@link onFailure} callback.
     */
    abstract class MessageResponseCallback {
        /**
         * Called on a success.
         *
         * @param buffer response from the device.
         */
        public void onSuccess(byte[] buffer) {}

        /**
         * Called when message sending fails.
         *
         * @param errorCode indicating the error.
         */
        public void onFailure(@ErrorCode int errorCode) {}
    }

    /**
     * Sends a request to the peer.
     *
     * @param request byte array to be sent to the peer device.
     * @param messageResponseCallback callback to be run when the peer response is received or if an
     *     error occurred.
     */
    void sendRequest(byte[] request, MessageResponseCallback messageResponseCallback);

    /** Triggers a disconnect from the peer device. */
    void disconnect();

    /**
     * Returns the connection information.
     *
     * @return A connection information object.
     */
    ConnectionInfo getConnectionInfo();
}
