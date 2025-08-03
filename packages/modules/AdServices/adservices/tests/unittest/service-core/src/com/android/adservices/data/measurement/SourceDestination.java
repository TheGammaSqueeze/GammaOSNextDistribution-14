/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adservices.data.measurement;

import com.android.adservices.service.measurement.EventSurfaceType;

import java.util.Objects;

/**
 * SourceDestination class for testing.
 */
final class SourceDestination {
    private final String mSourceId;
    private final String mDestination;
    private final @EventSurfaceType int mDestinationType;

    private SourceDestination(
            String sourceId,
            String destination,
            @EventSurfaceType int destinationType) {
        mSourceId = sourceId;
        mDestination = destination;
        mDestinationType = destinationType;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SourceDestination)) {
            return false;
        }
        SourceDestination sourceDest = (SourceDestination) obj;
        return Objects.equals(mSourceId, sourceDest.mSourceId)
                && Objects.equals(mDestination, sourceDest.mDestination)
                && mDestinationType == sourceDest.mDestinationType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSourceId, mDestination, mDestinationType);
    }

    /**
     * Get sourceId
     */
    public String getSourceId() {
        return mSourceId;
    }

    /**
     * Get destination
     */
    public String getDestination() {
        return mDestination;
    }

    /**
     * Get destination type
     */
    public @EventSurfaceType int getDestinationType() {
        return mDestinationType;
    }

    /**
     * A builder for {@link SourceDestination}.
     */
    public static final class Builder {
        private String mSourceId;
        private String mDestination;
        private @EventSurfaceType int mDestinationType;

        Builder() { }

        /**
         * See {@link SourceDestination#getSourceId()}.
         */
        public Builder setSourceId(String sourceId) {
            mSourceId = sourceId;
            return this;
        }

        /**
         * See {@link SourceDestination#getDestination}.
         */
        public Builder setDestination(String destination) {
            mDestination = destination;
            return this;
        }

        /**
         * See {@link SourceDestination#getDestinationType}.
         */
        public Builder setDestinationType(@EventSurfaceType int destinationType) {
            mDestinationType = destinationType;
            return this;
        }

        /**
         * Build the SourceDestination.
         */
        public SourceDestination build() {
            return new SourceDestination(mSourceId, mDestination, mDestinationType);
        }
    }
}
