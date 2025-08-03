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

package com.android.server.nearby.managers;

import android.annotation.IntDef;
import android.nearby.ScanFilter;
import android.nearby.ScanRequest;
import android.util.ArraySet;

import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.Set;

/** Internal discovery request to {@link DiscoveryProviderManager} and providers */
public class MergedDiscoveryRequest {

    private static final MergedDiscoveryRequest EMPTY_REQUEST = new MergedDiscoveryRequest(
            /* scanMode= */ ScanRequest.SCAN_MODE_NO_POWER,
            /* scanTypes= */ ImmutableSet.of(),
            /* actions= */ ImmutableSet.of(),
            /* scanFilters= */ ImmutableSet.of(),
            /* mediums= */ ImmutableSet.of());
    @ScanRequest.ScanMode
    private final int mScanMode;
    private final Set<Integer> mScanTypes;
    private final Set<Integer> mActions;
    private final Set<ScanFilter> mScanFilters;
    private final Set<Integer> mMediums;

    private MergedDiscoveryRequest(@ScanRequest.ScanMode int scanMode, Set<Integer> scanTypes,
            Set<Integer> actions, Set<ScanFilter> scanFilters, Set<Integer> mediums) {
        mScanMode = scanMode;
        mScanTypes = scanTypes;
        mActions = actions;
        mScanFilters = scanFilters;
        mMediums = mediums;
    }

    /**
     * Returns an empty discovery request.
     *
     * <p>The empty request is used as the default request when the discovery engine is enabled,
     * but
     * there is no request yet. It's also used to notify the discovery engine all clients have
     * removed
     * their requests.
     */
    public static MergedDiscoveryRequest empty() {
        return EMPTY_REQUEST;
    }

    /** Returns the priority of the request */
    @ScanRequest.ScanMode
    public final int getScanMode() {
        return mScanMode;
    }

    /** Returns all requested scan types. */
    public ImmutableSet<Integer> getScanTypes() {
        return ImmutableSet.copyOf(mScanTypes);
    }

    /** Returns the actions of the request */
    public ImmutableSet<Integer> getActions() {
        return ImmutableSet.copyOf(mActions);
    }

    /** Returns the scan filters of the request */
    public ImmutableSet<ScanFilter> getScanFilters() {
        return ImmutableSet.copyOf(mScanFilters);
    }

    /** Returns the enabled scan mediums */
    public ImmutableSet<Integer> getMediums() {
        return ImmutableSet.copyOf(mMediums);
    }

    /**
     * The medium where the broadcast request should be sent.
     *
     * @hide
     */
    @IntDef({Medium.BLE})
    public @interface Medium {
        int BLE = 1;
    }

    /** Builder for {@link MergedDiscoveryRequest}. */
    public static class Builder {
        private final Set<Integer> mScanTypes;
        private final Set<Integer> mActions;
        private final Set<ScanFilter> mScanFilters;
        private final Set<Integer> mMediums;
        @ScanRequest.ScanMode
        private int mScanMode;

        public Builder() {
            mScanMode = ScanRequest.SCAN_MODE_NO_POWER;
            mScanTypes = new ArraySet<>();
            mActions = new ArraySet<>();
            mScanFilters = new ArraySet<>();
            mMediums = new ArraySet<>();
        }

        /**
         * Sets the priority for the engine request.
         */
        public Builder setScanMode(@ScanRequest.ScanMode int scanMode) {
            mScanMode = scanMode;
            return this;
        }

        /**
         * Adds scan type to the request.
         */
        public Builder addScanType(@ScanRequest.ScanType int type) {
            mScanTypes.add(type);
            return this;
        }

        /** Add actions to the request. */
        public Builder addActions(Collection<Integer> actions) {
            mActions.addAll(actions);
            return this;
        }

        /** Add actions to the request. */
        public Builder addScanFilters(Collection<ScanFilter> scanFilters) {
            mScanFilters.addAll(scanFilters);
            return this;
        }

        /**
         * Add mediums to the request.
         */
        public Builder addMedium(@Medium int medium) {
            mMediums.add(medium);
            return this;
        }

        /** Builds an instance of {@link MergedDiscoveryRequest}. */
        public MergedDiscoveryRequest build() {
            return new MergedDiscoveryRequest(mScanMode, mScanTypes, mActions, mScanFilters,
                    mMediums);
        }
    }
}
