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

package com.android.networkstack.apishim.api31;

import android.net.NetworkAgentConfig;
import android.net.networkstack.aidl.NetworkMonitorParameters;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.modules.utils.build.SdkLevel;
import com.android.networkstack.apishim.common.NetworkAgentConfigShim;

/**
 * Implementation of NetworkAgentConfigShim for API 31.
 *
 * Compared with API29, NetworkAgentConfig is now API, though the shim currently doesn't
 * need to implement any of its methods yet.
 */
public class NetworkAgentConfigShimImpl
        extends com.android.networkstack.apishim.api29.NetworkAgentConfigShimImpl {
    // This can be null when running on a device with an old Connectivity module.
    @Nullable
    protected final NetworkAgentConfig mNetworkAgentConfig;

    protected NetworkAgentConfigShimImpl(@Nullable final NetworkAgentConfig config) {
        mNetworkAgentConfig = config;
    }

    /**
     * Returns a new instance of this shim impl.
     */
    public static NetworkAgentConfigShim newInstance(@Nullable final NetworkAgentConfig config) {
        if (!SdkLevel.isAtLeastS()) {
            return new com.android.networkstack.apishim.api29.NetworkAgentConfigShimImpl();
        }
        return new NetworkAgentConfigShimImpl(
                (config != null) ? config : new NetworkAgentConfig.Builder().build());
    }

    /**
     * Set the NetworkAgentConfig into the given {@link NetworkMonitorParameters}
     */
    public void writeToNetworkMonitorParams(@NonNull NetworkMonitorParameters params) {
        params.networkAgentConfig = mNetworkAgentConfig;
    }
}
