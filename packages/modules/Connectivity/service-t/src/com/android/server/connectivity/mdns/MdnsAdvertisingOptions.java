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

import android.annotation.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * API configuration parameters for advertising the mDNS service.
 *
 * <p>Use {@link MdnsAdvertisingOptions.Builder} to create {@link MdnsAdvertisingOptions}.
 *
 * @hide
 */
public class MdnsAdvertisingOptions {

    private static MdnsAdvertisingOptions sDefaultOptions;
    private final boolean mIsOnlyUpdate;
    @Nullable
    private final Duration mTtl;

    /**
     * Parcelable constructs for a {@link MdnsAdvertisingOptions}.
     */
    MdnsAdvertisingOptions(boolean isOnlyUpdate, @Nullable Duration ttl) {
        this.mIsOnlyUpdate = isOnlyUpdate;
        this.mTtl = ttl;
    }

    /**
     * Returns a {@link Builder} for {@link MdnsAdvertisingOptions}.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Returns a default search options.
     */
    public static synchronized MdnsAdvertisingOptions getDefaultOptions() {
        if (sDefaultOptions == null) {
            sDefaultOptions = newBuilder().build();
        }
        return sDefaultOptions;
    }

    /**
     * @return {@code true} if the advertising request is an update request.
     */
    public boolean isOnlyUpdate() {
        return mIsOnlyUpdate;
    }

    /**
     * Returns the TTL for all records in a service.
     */
    @Nullable
    public Duration getTtl() {
        return mTtl;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof MdnsAdvertisingOptions)) {
            return false;
        } else {
            final MdnsAdvertisingOptions otherOptions = (MdnsAdvertisingOptions) other;
            return mIsOnlyUpdate == otherOptions.mIsOnlyUpdate
                    && Objects.equals(mTtl, otherOptions.mTtl);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(mIsOnlyUpdate, mTtl);
    }

    @Override
    public String toString() {
        return "MdnsAdvertisingOptions{" + "mIsOnlyUpdate=" + mIsOnlyUpdate + ", mTtl=" + mTtl
                + '}';
    }

    /**
     * A builder to create {@link MdnsAdvertisingOptions}.
     */
    public static final class Builder {
        private boolean mIsOnlyUpdate = false;
        @Nullable
        private Duration mTtl;

        private Builder() {
        }

        /**
         * Sets if the advertising request is an update request.
         */
        public Builder setIsOnlyUpdate(boolean isOnlyUpdate) {
            this.mIsOnlyUpdate = isOnlyUpdate;
            return this;
        }

        /**
         * Sets the TTL duration for all records of the service.
         */
        public Builder setTtl(@Nullable Duration ttl) {
            this.mTtl = ttl;
            return this;
        }

        /**
         * Builds a {@link MdnsAdvertisingOptions} with the arguments supplied to this builder.
         */
        public MdnsAdvertisingOptions build() {
            return new MdnsAdvertisingOptions(mIsOnlyUpdate, mTtl);
        }
    }
}
