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

package com.android.networkstack.apishim.api33;

import static com.android.modules.utils.build.SdkLevel.isAtLeastT;

import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.networkstack.apishim.common.NetworkInformationShim;

import java.util.List;

/**
 * Compatibility implementation of {@link NetworkInformationShim}.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class NetworkInformationShimImpl
        extends com.android.networkstack.apishim.api31.NetworkInformationShimImpl {
    protected NetworkInformationShimImpl() {}

    /**
     * Get a new instance of {@link NetworkInformationShim}.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static NetworkInformationShim newInstance() {
        if (!isAtLeastT()) {
            return com.android.networkstack.apishim.api31.NetworkInformationShimImpl.newInstance();
        }
        return new NetworkInformationShimImpl();
    }

    @Override
    public List<Network> getUnderlyingNetworks(@NonNull NetworkCapabilities nc) {
        return nc.getUnderlyingNetworks();
    }
}
