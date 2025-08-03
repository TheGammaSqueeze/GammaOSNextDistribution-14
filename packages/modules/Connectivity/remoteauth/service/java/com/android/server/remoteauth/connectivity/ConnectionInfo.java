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
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Encapsulates the connection information.
 *
 * <p>Connection information captures the details of underlying connection such as connection id,
 * type of connection and peer device mac address.
 *
 * @param <T> connection params per connection type.
 */
// TODO(b/295407748) Change to use @DataClass.
public abstract class ConnectionInfo<T> implements Parcelable {
    int mConnectionId;

    public ConnectionInfo(int connectionId) {
        mConnectionId = connectionId;
    }

    /** Create object from Parcel */
    public ConnectionInfo(@NonNull Parcel in) {
        mConnectionId = in.readInt();
    }

    public int getConnectionId() {
        return mConnectionId;
    }

    /** No special parcel contents. */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Flattens this ConnectionInfo in to a Parcel.
     *
     * @param out The Parcel in which the object should be written.
     * @param flags Additional flags about how the object should be written.
     */
    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeInt(mConnectionId);
    }

    /** Returns string representation of ConnectionInfo. */
    @Override
    public String toString() {
        return "ConnectionInfo[" + "connectionId= " + mConnectionId + "]";
    }

    /** Returns true if this ConnectionInfo object is equal to the other. */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ConnectionInfo)) {
            return false;
        }

        ConnectionInfo other = (ConnectionInfo) o;
        return mConnectionId == other.getConnectionId();
    }

    /** Returns the hashcode of this object */
    @Override
    public int hashCode() {
        return Objects.hash(mConnectionId);
    }

    /**
     * Returns connection related parameters.
     */
    public abstract T getConnectionParams();
}
