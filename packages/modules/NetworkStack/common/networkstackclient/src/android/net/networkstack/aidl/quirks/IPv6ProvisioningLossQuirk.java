/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.net.networkstack.aidl.quirks;

import android.annotation.Nullable;

import java.util.Objects;

/**
 * An object representing the quirk information results in the IPv6 provisioning loss on the given
 * network. Parcels as a IPv6ProvisioningLossQuirkParcelable object.
 * @hide
 */
public final class IPv6ProvisioningLossQuirk {
    public final int mDetectionCount;
    public final long mQuirkExpiry;

    /**
     * Create an instance of {@link IPv6ProvisioningLossQuirk} with the specified members.
     */
    public IPv6ProvisioningLossQuirk(final int count, final long expiry) {
        mDetectionCount = count;
        mQuirkExpiry = expiry;
    }

    /**
     * Convert IPv6ProvisioningLossQuirk to a {@link IPv6ProvisioningLossQuirkParcelable}.
     */
    public IPv6ProvisioningLossQuirkParcelable toStableParcelable() {
        final IPv6ProvisioningLossQuirkParcelable p = new IPv6ProvisioningLossQuirkParcelable();
        p.detectionCount = mDetectionCount;
        p.quirkExpiry = mQuirkExpiry;
        return p;
    }

    /**
     * Create an instance of {@link IPv6ProvisioningLossQuirk} based on the contents of the
     * specified {@link IPv6ProvisioningLossQuirkParcelable}.
     */
    public static IPv6ProvisioningLossQuirk fromStableParcelable(
            @Nullable final IPv6ProvisioningLossQuirkParcelable p) {
        if (p == null) return null;
        return new IPv6ProvisioningLossQuirk(p.detectionCount, p.quirkExpiry);
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (null == obj || getClass() != obj.getClass()) return false;
        final IPv6ProvisioningLossQuirk other = (IPv6ProvisioningLossQuirk) obj;
        return mDetectionCount == other.mDetectionCount && mQuirkExpiry == other.mQuirkExpiry;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDetectionCount, mQuirkExpiry);
    }

    @Override
    public String toString() {
        StringBuffer str = new StringBuffer();
        str.append("detection count: ").append(mDetectionCount);
        str.append(", quirk expiry: ").append(mQuirkExpiry);
        return str.toString();
    }
}
