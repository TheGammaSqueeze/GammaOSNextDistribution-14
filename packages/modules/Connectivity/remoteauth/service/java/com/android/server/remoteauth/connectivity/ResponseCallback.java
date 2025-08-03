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

import android.annotation.NonNull;

/**
 * Abstract class to expose a callback for clients to send a response to the peer device.
 *
 * <p>When a device receives a message on a connection, this object is constructed using the
 * connection information of the connection and the message id from the incoming message. This
 * object is forwarded to the clients of the connection to allow them to send a response to the peer
 * device.
 */
public abstract class ResponseCallback {
    private final long mMessageId;
    private final ConnectionInfo mConnectionInfo;

    public ResponseCallback(long messageId, @NonNull ConnectionInfo connectionInfo) {
        mMessageId = messageId;
        mConnectionInfo = connectionInfo;
    }

    /**
     * Returns message id identifying the message.
     *
     * @return message id of this message.
     */
    public long getMessageId() {
        return mMessageId;
    }

    /**
     * Returns connection info from the response.
     *
     * @return connection info.
     */
    @NonNull
    public ConnectionInfo getConnectionInfo() {
        return mConnectionInfo;
    }

    /**
     * Callback to send a response to the peer device.
     *
     * @param response buffer to send to the peer device.
     */
    public void onResponse(@NonNull byte[] response) {}
}
