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

package androidx.core.uwb.backend.impl.internal;

import static androidx.core.uwb.backend.impl.internal.Utils.RANGE_DATA_NTF_ENABLE;
import static androidx.core.uwb.backend.impl.internal.Utils.SUPPORTED_NTF_CONFIG;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Objects;

import javax.annotation.Nonnegative;

/** Configurable Range Data Ntf reports for a UWB session */
public class UwbRangeDataNtfConfig {

    @Utils.RangeDataNtfConfig
    private final int mRangeDataNtfConfigType;
    private final int mNtfProximityNear;
    private final int mNtfProximityFar;

    private UwbRangeDataNtfConfig(
            @Utils.RangeDataNtfConfig int rangeDataNtfConfigType,
            @Nonnegative int ntfProximityNear,
            @Nonnegative int ntfProximityFar) {
        checkArgument(SUPPORTED_NTF_CONFIG.contains(rangeDataNtfConfigType),
                "Invalid/Unsupported Range Data Ntf config");
        checkArgument(ntfProximityNear <= ntfProximityFar,
                "Ntf proximity near cannot be greater than Ntf proximity far");
        mRangeDataNtfConfigType = rangeDataNtfConfigType;
        mNtfProximityNear = ntfProximityNear;
        mNtfProximityFar = ntfProximityFar;
    }

    public int getRangeDataNtfConfigType() {
        return mRangeDataNtfConfigType;
    }

    public int getNtfProximityNear() {
        return mNtfProximityNear;
    }

    public int getNtfProximityFar() {
        return mNtfProximityFar;
    }

    /** Builder for UwbRangeDataNtfConfig */
    public static class Builder {
        private int mRangeDataConfigType = RANGE_DATA_NTF_ENABLE;
        private int mNtfProximityNear = 0;
        private int mNtfProximityFar = 20_000;

        public Builder setRangeDataConfigType(int rangeDataConfig) {
            mRangeDataConfigType = rangeDataConfig;
            return this;
        }

        public Builder setNtfProximityNear(int ntfProximityNear) {
            mNtfProximityNear = ntfProximityNear;
            return this;
        }

        public Builder setNtfProximityFar(int ntfProximityFar) {
            mNtfProximityFar = ntfProximityFar;
            return this;
        }

        public UwbRangeDataNtfConfig build() {
            return new UwbRangeDataNtfConfig(mRangeDataConfigType, mNtfProximityNear,
                    mNtfProximityFar);
        }
    }

    @Override
    public String toString() {
        return "UwbRangeDataNtfConfig{"
                + "mRangeDataNtfConfigType=" + mRangeDataNtfConfigType
                + ", mNtfProximityNear=" + mNtfProximityNear
                + ", mNtfProximityFar=" + mNtfProximityFar
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UwbRangeDataNtfConfig)) return false;
        UwbRangeDataNtfConfig that = (UwbRangeDataNtfConfig) o;
        return mRangeDataNtfConfigType == that.mRangeDataNtfConfigType
                && mNtfProximityNear == that.mNtfProximityNear
                && mNtfProximityFar == that.mNtfProximityFar;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRangeDataNtfConfigType, mNtfProximityNear, mNtfProximityFar);
    }
}
