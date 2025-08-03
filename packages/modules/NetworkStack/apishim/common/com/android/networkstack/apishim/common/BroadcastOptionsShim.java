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

package com.android.networkstack.apishim.common;

import android.os.Bundle;

import androidx.annotation.NonNull;

/**
 * Interface for accessing API methods in {@link android.app.BroadcastOptions} by different SDK
 * level.
 */
public interface BroadcastOptionsShim {
    /** See android.app.BroadcastOptions#setDeliveryGroupPolicy */
    default BroadcastOptionsShim setDeliveryGroupPolicy(int policy)
            throws UnsupportedApiLevelException {
        throw new UnsupportedApiLevelException("Only supported starting from API 34");
    }

    /** See android.app.BroadcastOptions#setDeliveryGroupMatchingKey */
    default BroadcastOptionsShim setDeliveryGroupMatchingKey(@NonNull String namespace,
            @NonNull String key) throws UnsupportedApiLevelException {
        throw new UnsupportedApiLevelException("Only supported starting from API 34");
    }

    /** See android.app.BroadcastOptions#setDeferralPolicy */
    default BroadcastOptionsShim setDeferralPolicy(int deferralPolicy)
            throws UnsupportedApiLevelException {
        throw new UnsupportedApiLevelException("Only supported starting from API 34");
    }

    /** See android.app.BroadcastOptions#toBundle */
    @NonNull
    Bundle toBundle();
}
