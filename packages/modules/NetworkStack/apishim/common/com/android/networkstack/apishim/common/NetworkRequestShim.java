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

package com.android.networkstack.apishim.common;

import android.net.NetworkRequest;
import android.util.Range;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;

/**
 * Interface used to access API methods in {@link android.net.NetworkRequest}, with
 * appropriate fallbacks if the methods are not yet part of the released API.
 */
public interface NetworkRequestShim {
    /**
     * See android.net.NetworkRequest.Builder#setUids.
     * Set the {@code uids} into {@code builder}.
     */
    void setUids(@NonNull NetworkRequest.Builder builder,
            @Nullable Set<Range<Integer>> uids) throws UnsupportedApiLevelException;

    /**
     * See android.net.NetworkRequest.Builder#setIncludeOtherUidNetworks.
     */
    default NetworkRequest.Builder setIncludeOtherUidNetworks(NetworkRequest.Builder builder,
            boolean include) throws UnsupportedApiLevelException {
        throw new UnsupportedApiLevelException("Not supported before API 31.");
    }

    /**
     * See android.net.NetworkRequest.Builder(NetworkRequest).
     * @throws UnsupportedApiLevelException if API is not available in the API level.
     */
    default NetworkRequest.Builder newBuilder(@NonNull NetworkRequest request)
            throws UnsupportedApiLevelException {
        // Not supported before API 31.
        throw new UnsupportedApiLevelException("Not supported before API 31.");
    }
}
