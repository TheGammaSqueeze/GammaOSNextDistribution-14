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
import android.annotation.Nullable;

import java.util.Objects;

/** Device discovered on a network interface like Bluetooth. */
public final class DiscoveredDevice {
    private @NonNull ConnectionInfo mConnectionInfo;
    private @Nullable String mDisplayName;

    public DiscoveredDevice(@NonNull ConnectionInfo connectionInfo, @Nullable String displayName) {
        this.mConnectionInfo = connectionInfo;
        this.mDisplayName = displayName;
    }

    /**
     * Returns connection information.
     *
     * @return connection information.
     */
    @NonNull
    public ConnectionInfo getConnectionInfo() {
        return this.mConnectionInfo;
    }

    /**
     * Returns display name of the device.
     *
     * @return display name string.
     */
    @Nullable
    public String getDisplayName() {
        return this.mDisplayName;
    }

    /**
     * Checks for equality between this and other object.
     *
     * @return true if equal, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DiscoveredDevice)) {
            return false;
        }

        DiscoveredDevice other = (DiscoveredDevice) o;
        return mConnectionInfo.equals(other.getConnectionInfo())
                && mDisplayName.equals(other.getDisplayName());
    }

    /**
     * Returns hash code of the object.
     *
     * @return hash code.
     */
    @Override
    public int hashCode() {
        return Objects.hash(mDisplayName, mConnectionInfo.getConnectionId());
    }
}
