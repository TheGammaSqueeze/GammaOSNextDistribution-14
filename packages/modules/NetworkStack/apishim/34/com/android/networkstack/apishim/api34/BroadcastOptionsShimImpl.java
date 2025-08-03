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

package com.android.networkstack.apishim.api34;

import static com.android.modules.utils.build.SdkLevel.isAtLeastU;

import android.app.BroadcastOptions;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.networkstack.apishim.common.BroadcastOptionsShim;
import com.android.networkstack.apishim.common.UnsupportedApiLevelException;

/**
 * Implementation of {@link BroadcastOptionsShim} for API 34.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class BroadcastOptionsShimImpl extends
        com.android.networkstack.apishim.api33.BroadcastOptionsShimImpl {
    protected BroadcastOptionsShimImpl(@NonNull BroadcastOptions options) {
        super(options);
    }

    /** Get a new instance of {@link BroadcastOptionsShim}. */
    @RequiresApi(Build.VERSION_CODES.CUR_DEVELOPMENT)
    public static BroadcastOptionsShim newInstance(@NonNull BroadcastOptions options) {
        if (!isAtLeastU()) {
            return com.android.networkstack.apishim.api33.BroadcastOptionsShimImpl.newInstance(
                    options);
        }
        return new BroadcastOptionsShimImpl(options);
    }

    /** See android.app.BroadcastOptions#setDeliveryGroupPolicy */
    @Override
    public BroadcastOptionsShim setDeliveryGroupPolicy(int policy)
            throws UnsupportedApiLevelException {
        mOptions.setDeliveryGroupPolicy(policy);
        return this;
    }

    /** See android.app.BroadcastOptions#setDeliveryGroupMatchingKey */
    @Override
    public BroadcastOptionsShim setDeliveryGroupMatchingKey(@NonNull String namespace,
            @NonNull String key) throws UnsupportedApiLevelException {
        mOptions.setDeliveryGroupMatchingKey(namespace, key);
        return this;
    }

    /** See android.app.BroadcastOptions#setDeferralPolicy */
    @Override
    public BroadcastOptionsShim setDeferralPolicy(int deferralPolicy)
            throws UnsupportedApiLevelException {
        mOptions.setDeferralPolicy(deferralPolicy);
        return this;
    }
}
