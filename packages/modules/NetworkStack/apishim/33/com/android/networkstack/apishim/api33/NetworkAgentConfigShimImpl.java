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

package com.android.networkstack.apishim.api33;

import static com.android.modules.utils.build.SdkLevel.isAtLeastT;

import android.net.NetworkAgentConfig;

import androidx.annotation.Nullable;

import com.android.networkstack.apishim.common.NetworkAgentConfigShim;

/**
 * A shim for NetworkAgentConfig
 */
public class NetworkAgentConfigShimImpl
        extends com.android.networkstack.apishim.api31.NetworkAgentConfigShimImpl {
    protected NetworkAgentConfigShimImpl(@Nullable final NetworkAgentConfig config) {
        super(config);
    }

    /**
     * Returns a new instance of this shim impl.
     */
    public static NetworkAgentConfigShim newInstance(@Nullable final NetworkAgentConfig config) {
        if (!isAtLeastT()) {
            return com.android.networkstack.apishim.api31.NetworkAgentConfigShimImpl
                    .newInstance(config);
        } else {
            return new NetworkAgentConfigShimImpl(config);
        }
    }

    @Override
    public boolean isVpnValidationRequired() {
        if (null == mNetworkAgentConfig) {
            return false;
        } else {
            return mNetworkAgentConfig.isVpnValidationRequired();
        }
    }

    @Override
    public String toString() {
        if (null == mNetworkAgentConfig) {
            return "NetworkAgentConfigShimImpl[null]";
        } else {
            return mNetworkAgentConfig.toString();
        }
    }
}
