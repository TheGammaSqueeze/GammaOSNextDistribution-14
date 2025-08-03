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
import android.annotation.Nullable;
import android.net.NetworkCapabilities;
import android.os.UserHandle;

/**
 * A single profile preference, as it applies to a given user profile.
 */
public class ProfileNetworkPreferenceInfo
        implements NetworkPreferenceList.NetworkPreference<UserHandle> {
    @NonNull
    public final UserHandle user;
    // Capabilities are only null when sending an object to remove the setting for a user
    @Nullable
    public final NetworkCapabilities capabilities;
    public final boolean allowFallback;
    public final boolean blockingNonEnterprise;

    public ProfileNetworkPreferenceInfo(@NonNull final UserHandle user,
            @Nullable final NetworkCapabilities capabilities,
            final boolean allowFallback, final boolean blockingNonEnterprise) {
        this.user = user;
        this.capabilities = null == capabilities ? null : new NetworkCapabilities(capabilities);
        this.allowFallback = allowFallback;
        this.blockingNonEnterprise = blockingNonEnterprise;
    }

    @Override
    public boolean isCancel() {
        return null == capabilities;
    }

    @Override
    @NonNull
    public UserHandle getKey() {
        return user;
    }

    /** toString */
    public String toString() {
        return "[ProfileNetworkPreference user=" + user
                + " caps=" + capabilities
                + " allowFallback=" + allowFallback
                + " blockingNonEnterprise=" + blockingNonEnterprise
                + "]";
    }
}
