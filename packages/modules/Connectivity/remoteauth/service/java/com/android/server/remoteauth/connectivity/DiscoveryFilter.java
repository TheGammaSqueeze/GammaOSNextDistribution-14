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

/**
 * Filter for device discovery.
 *
 * <p>Callers can use this class to provide a discovery filter to the {@link
 * ConnectivityManager.startDiscovery} method. A device is discovered if it matches at least one of
 * the filter criteria (device type, name or peer address).
 */
public final class DiscoveryFilter {
    /** Device type WATCH. */
    public static final int WATCH = 0;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({WATCH})
    public @interface DeviceType {}

    private @DeviceType int mDeviceType;
    private final @Nullable String mDeviceName;
    private final @Nullable String mPeerAddress;

    public DiscoveryFilter(
            @DeviceType int deviceType, @Nullable String deviceName, @Nullable String peerAddress) {
        this.mDeviceType = deviceType;
        this.mDeviceName = deviceName;
        this.mPeerAddress = peerAddress;
    }

    /**
     * Returns device type.
     *
     * @return device type.
     */
    public @DeviceType int getDeviceType() {
        return this.mDeviceType;
    }

    /**
     * Returns device name.
     *
     * @return device name.
     */
    public @Nullable String getDeviceName() {
        return this.mDeviceName;
    }

    /**
     * Returns mac address of the peer device .
     *
     * @return mac address string.
     */
    public @Nullable String getPeerAddress() {
        return this.mPeerAddress;
    }

    /** Builder for {@link DiscoverFilter} */
    public static class Builder {
        private @DeviceType int mDeviceType;
        private @Nullable String mDeviceName;
        private @Nullable String mPeerAddress;

        private Builder() {}

        /** Static method to create a new builder */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Sets the device type of the DiscoveryFilter.
         *
         * @param deviceType of the peer device.
         */
        @NonNull
        public Builder setDeviceType(@DeviceType int deviceType) {
            mDeviceType = deviceType;
            return this;
        }

        /**
         * Sets the device name.
         *
         * @param deviceName May be null.
         */
        @NonNull
        public Builder setDeviceName(String deviceName) {
            mDeviceName = deviceName;
            return this;
        }

        /**
         * Sets the peer address.
         *
         * @param peerAddress Mac address of the peer device.
         */
        @NonNull
        public Builder setPeerAddress(String peerAddress) {
            mPeerAddress = peerAddress;
            return this;
        }

        /** Builds the DiscoveryFilter object. */
        @NonNull
        public DiscoveryFilter build() {
            return new DiscoveryFilter(this.mDeviceType, this.mDeviceName, this.mPeerAddress);
        }
    }
}
