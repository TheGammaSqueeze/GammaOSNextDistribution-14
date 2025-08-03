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
package com.android.server.remoteauth.ranging;

import androidx.core.uwb.backend.impl.internal.UwbAddress;

/** The set of parameters to initiate {@link RangingSession#start}. */
public class RangingParameters {

    /** Parameters for {@link UwbRangingSession}. */
    private final UwbAddress mUwbLocalAddress;

    private final androidx.core.uwb.backend.impl.internal.RangingParameters mUwbRangingParameters;

    public UwbAddress getUwbLocalAddress() {
        return mUwbLocalAddress;
    }

    public androidx.core.uwb.backend.impl.internal.RangingParameters getUwbRangingParameters() {
        return mUwbRangingParameters;
    }

    private RangingParameters(
            UwbAddress uwbLocalAddress,
            androidx.core.uwb.backend.impl.internal.RangingParameters uwbRangingParameters) {
        mUwbLocalAddress = uwbLocalAddress;
        mUwbRangingParameters = uwbRangingParameters;
    }

    /** Builder class for {@link RangingParameters}. */
    public static final class Builder {
        private UwbAddress mUwbLocalAddress;
        private androidx.core.uwb.backend.impl.internal.RangingParameters mUwbRangingParameters;

        /**
         * Sets the uwb local address.
         *
         * <p>Only required if {@link SessionParameters#getRangingMethod}=={@link
         * RANGING_METHOD_UWB} and {@link SessionParameters#getAutoDeriveParams} == false
         */
        public Builder setUwbLocalAddress(UwbAddress uwbLocalAddress) {
            mUwbLocalAddress = uwbLocalAddress;
            return this;
        }

        /**
         * Sets the uwb ranging parameters.
         *
         * <p>Only required if {@link SessionParameters#getRangingMethod}=={@link
         * RANGING_METHOD_UWB}.
         *
         * <p>If {@link SessionParameters#getAutoDeriveParams} == true, all required uwb parameters
         * including uwbLocalAddress, complexChannel, peerAddresses, and sessionKeyInfo will be
         * automatically derived, so unnecessary to provide and the other uwb parameters are
         * optional.
         */
        public Builder setUwbRangingParameters(
                androidx.core.uwb.backend.impl.internal.RangingParameters uwbRangingParameters) {
            mUwbRangingParameters = uwbRangingParameters;
            return this;
        }

        /** Builds {@link RangingParameters}. */
        public RangingParameters build() {
            return new RangingParameters(mUwbLocalAddress, mUwbRangingParameters);
        }
    }
}
