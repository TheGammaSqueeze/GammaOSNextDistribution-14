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
import android.text.TextUtils;

import java.util.Objects;

/** A class used to identify a receiver endpoint. */
final class ReceiverEndpointId {

    /** Indicates which client this endpoint is in. */
    public final ClientId clientId;

    /**
     * The ID of this endpoint. The ID is specified by the client app via {@link
     * android.car.occupantconnection.CarOccupantConnectionManager#registerReceiver}.
     */
    public final String endpointId;

    ReceiverEndpointId(@NonNull ClientId clientId, @NonNull String endpointId) {
        this.clientId = Objects.requireNonNull(clientId, "clientId cannot be null");
        this.endpointId = Objects.requireNonNull(endpointId, "endpointId cannot be null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReceiverEndpointId)) {
            return false;
        }
        ReceiverEndpointId other = (ReceiverEndpointId) o;
        return clientId.equals(other.clientId)
                && TextUtils.equals(endpointId, other.endpointId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId, endpointId);
    }

    @Override
    public String toString() {
        return "ReceiverEndpointId[clientId=" + clientId + ", endpointId=" + endpointId + "]";
    }
}
