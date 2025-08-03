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
package com.android.car.occupantconnection;

import android.annotation.NonNull;

import java.util.Objects;

/**
 * A class used to identify a connection from a sender client to a receiver client. The sender
 * client and the receiver client must have the same package name.
 */
final class ConnectionId {

    /** The sender client. */
    public final ClientId senderClient;
    /** The receiver client. */
    public final ClientId receiverClient;

    ConnectionId(@NonNull ClientId senderClient, @NonNull ClientId receiverClient) {
        this.senderClient = Objects.requireNonNull(senderClient, "senderClient cannot be null");
        this.receiverClient =
                Objects.requireNonNull(receiverClient, "receiverClient cannot be null");
        if (!senderClient.packageName.equals(receiverClient.packageName)) {
            throw new IllegalArgumentException("Package name doesn't match! "
                    + "Sender:" + senderClient.packageName
                    + ", receiver:" + receiverClient.packageName);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConnectionId)) {
            return false;
        }
        ConnectionId other = (ConnectionId) o;
        return senderClient.equals(other.senderClient)
                && receiverClient.equals(other.receiverClient);
    }

    @Override
    public int hashCode() {
        return Objects.hash(senderClient, receiverClient);
    }

    @Override
    public String toString() {
        return "ConnectionId[senderClient=" + senderClient + ", receiverClient="
                + receiverClient + "]";
    }
}
