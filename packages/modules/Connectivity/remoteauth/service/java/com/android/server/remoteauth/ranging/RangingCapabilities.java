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

import android.annotation.NonNull;
import android.annotation.Nullable;

import androidx.annotation.IntDef;

import com.google.common.collect.ImmutableList;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/** The ranging capabilities of the device. */
public class RangingCapabilities {

    /** Possible ranging methods */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                RANGING_METHOD_UNKNOWN,
                RANGING_METHOD_UWB,
            })
    public @interface RangingMethod {}

    /** Unknown ranging method. */
    public static final int RANGING_METHOD_UNKNOWN = 0x0;

    /** Ultra-wideband ranging. */
    public static final int RANGING_METHOD_UWB = 0x1;

    private final ImmutableList<Integer> mSupportedRangingMethods;
    private final androidx.core.uwb.backend.impl.internal.RangingCapabilities
            mUwbRangingCapabilities;

    /**
     * Gets the list of supported ranging methods of the device.
     *
     * @return list of {@link RangingMethod}
     */
    public ImmutableList<Integer> getSupportedRangingMethods() {
        return mSupportedRangingMethods;
    }

    /**
     * Gets the UWB ranging capabilities of the device.
     *
     * @return UWB ranging capabilities, null if UWB is not a supported {@link RangingMethod} in
     *     {@link #getSupportedRangingMethods}.
     */
    @Nullable
    public androidx.core.uwb.backend.impl.internal.RangingCapabilities getUwbRangingCapabilities() {
        return mUwbRangingCapabilities;
    }

    private RangingCapabilities(
            List<Integer> supportedRangingMethods,
            androidx.core.uwb.backend.impl.internal.RangingCapabilities uwbRangingCapabilities) {
        mSupportedRangingMethods = ImmutableList.copyOf(supportedRangingMethods);
        mUwbRangingCapabilities = uwbRangingCapabilities;
    }

    /** Builder class for {@link RangingCapabilities}. */
    public static final class Builder {
        private List<Integer> mSupportedRangingMethods = new ArrayList<>();
        private androidx.core.uwb.backend.impl.internal.RangingCapabilities mUwbRangingCapabilities;

        /** Adds a supported {@link RangingMethod} */
        public Builder addSupportedRangingMethods(@RangingMethod int rangingMethod) {
            mSupportedRangingMethods.add(rangingMethod);
            return this;
        }

        /** Sets the uwb ranging capabilities. */
        public Builder setUwbRangingCapabilities(
                @NonNull
                        androidx.core.uwb.backend.impl.internal.RangingCapabilities
                                uwbRangingCapabilities) {
            mUwbRangingCapabilities = uwbRangingCapabilities;
            return this;
        }

        /** Builds {@link RangingCapabilities}. */
        public RangingCapabilities build() {
            return new RangingCapabilities(mSupportedRangingMethods, mUwbRangingCapabilities);
        }
    }
}
