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

/**
 * A class to represent a connection from a sender client to a receiver client. Note: the connection
 * is one way.
 */
final class ConnectionRecord {

    /** The package name of the sender client and receiver client. */
    public final String packageName;
    /** The ID of the sender occupant zone. */
    public final int senderZoneId;
    /** The ID of the receiver occupant zone. */
    public final int receiverZoneId;

    ConnectionRecord(@NonNull String packageName, int senderZoneId, int receiverZoneId) {
        this.packageName = Objects.requireNonNull(packageName, "packageName cannot be null");
        this.senderZoneId = senderZoneId;
        this.receiverZoneId = receiverZoneId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConnectionRecord)) {
            return false;
        }
        ConnectionRecord other = (ConnectionRecord) o;
        return TextUtils.equals(packageName, other.packageName)
                && senderZoneId == other.senderZoneId
                && receiverZoneId == other.receiverZoneId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName, senderZoneId, receiverZoneId);
    }

    @Override
    public String toString() {
        return "ConnectionRecord[packageName=" + packageName
                + ",senderZoneId=" + senderZoneId
                + ",receiverZoneId=" + receiverZoneId + "]";
    }
}
