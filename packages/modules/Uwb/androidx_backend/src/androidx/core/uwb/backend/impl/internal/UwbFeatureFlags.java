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

/** Uwb feature support flags */
public class UwbFeatureFlags {
    private final boolean mSkipRangingCapabilitiesCheck;
    private final boolean mAzimuthSupport;
    private final boolean mElevationSupport;
    private final boolean mReversedByteOrderFiraParams;

    private UwbFeatureFlags(boolean skipRangingCapabilitiesCheck, boolean azimuthSupport,
            boolean elevationSupport, boolean reversedByteOrderFiraParams) {
        mSkipRangingCapabilitiesCheck = skipRangingCapabilitiesCheck;
        mAzimuthSupport = azimuthSupport;
        mElevationSupport = elevationSupport;
        mReversedByteOrderFiraParams = reversedByteOrderFiraParams;
    }

    public boolean skipRangingCapabilitiesCheck() {
        return mSkipRangingCapabilitiesCheck;
    }

    public boolean hasAzimuthSupport() {
        return mAzimuthSupport;
    }

    public boolean hasElevationSupport() {
        return mElevationSupport;
    }

    public boolean isReversedByteOrderFiraParams() {
        return mReversedByteOrderFiraParams;
    }

    /** Builder */
    public static class Builder {
        private boolean mSkipRangingCapabilitiesCheck = false;
        private boolean mAzimuthSupport = false;
        private boolean mElevationSupport = false;
        private boolean mReversedByteOrderFiraParams = false;

        public UwbFeatureFlags.Builder setSkipRangingCapabilitiesCheck(
                boolean skipRangingCapabilitiesCheck) {
            mSkipRangingCapabilitiesCheck = skipRangingCapabilitiesCheck;
            return this;
        }

        public UwbFeatureFlags.Builder setAzimuthSupport(boolean azimuthSupport) {
            mAzimuthSupport = azimuthSupport;
            return this;
        }

        public UwbFeatureFlags.Builder setElevationSupport(boolean elevationSupport) {
            mElevationSupport = elevationSupport;
            return this;
        }

        public UwbFeatureFlags.Builder setReversedByteOrderFiraParams(
                boolean reversedByteOrderFiraParams) {
            mReversedByteOrderFiraParams = reversedByteOrderFiraParams;
            return this;
        }

        public UwbFeatureFlags build() {
            return new UwbFeatureFlags(
                    mSkipRangingCapabilitiesCheck,
                    mAzimuthSupport,
                    mElevationSupport,
                    mReversedByteOrderFiraParams);
        }
    }
}
