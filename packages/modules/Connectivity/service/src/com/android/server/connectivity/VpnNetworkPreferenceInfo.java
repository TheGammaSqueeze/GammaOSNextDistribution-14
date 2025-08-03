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

package com.android.server.connectivity;

import android.annotation.NonNull;
import android.net.UidRange;
import android.util.ArraySet;

/**
 * Record the session and UidRange for a VPN preference.
 */
public class VpnNetworkPreferenceInfo
        implements NetworkPreferenceList.NetworkPreference<String> {

    @NonNull
    public final String mSession;

    @NonNull
    public final ArraySet<UidRange> mUidRanges;

    public VpnNetworkPreferenceInfo(@NonNull String session,
            @NonNull ArraySet<UidRange> uidRanges) {
        this.mSession = session;
        this.mUidRanges = uidRanges;
    }

    @Override
    public boolean isCancel() {
        return mUidRanges.isEmpty();
    }

    @Override
    @NonNull
    public String getKey() {
        return mSession;
    }

    @NonNull
    public ArraySet<UidRange> getUidRangesNoCopy() {
        return mUidRanges;
    }

    /** toString */
    public String toString() {
        return "[VpnNetworkPreference session = " + mSession
                + " uidRanges = " + mUidRanges
                + "]";
    }
}
