/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.networkstack.apishim.api29;

import android.net.IpPrefix;
import android.net.VpnService;

import com.android.networkstack.apishim.common.UnsupportedApiLevelException;
import com.android.networkstack.apishim.common.VpnServiceBuilderShim;

/**
 * Implementation of {@link com.android.networkstack.apishim.common.VpnServiceBuilderShim}.
 */
public class VpnServiceBuilderShimImpl implements VpnServiceBuilderShim {

    /**
     * Get a new instance of {@link VpnServiceBuilderShim}.
     */
    public static VpnServiceBuilderShim newInstance() {
        return new VpnServiceBuilderShimImpl();
    }

    @Override
    public VpnService.Builder excludeRoute(VpnService.Builder builder, IpPrefix prefix)
            throws UnsupportedApiLevelException {
        throw new UnsupportedApiLevelException("Only supported after API level 31.");
    }

    @Override
    public VpnService.Builder addRoute(VpnService.Builder builder, IpPrefix prefix)
            throws UnsupportedApiLevelException {
        throw new UnsupportedApiLevelException("Only supported after API level 31.");
    }
}
