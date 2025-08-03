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

import android.net.NetworkRequest;
import android.os.Build;
import android.util.Range;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.networkstack.apishim.common.NetworkRequestShim;
import com.android.networkstack.apishim.common.UnsupportedApiLevelException;

import java.util.Set;

/**
 * Implementation of {@link NetworkRequestShim} for API 29.
 */
@RequiresApi(Build.VERSION_CODES.Q)
public class NetworkRequestShimImpl implements NetworkRequestShim {
    protected NetworkRequestShimImpl() {}

    /**
     * Get a new instance of {@link NetworkRequestShim}.
     */
    public static NetworkRequestShim newInstance() {
        return new NetworkRequestShimImpl();
    }

    @Override
    public void setUids(@NonNull NetworkRequest.Builder builder,
            @Nullable Set<Range<Integer>> uids) throws UnsupportedApiLevelException {
        // Not supported before API 31.
        throw new UnsupportedApiLevelException("Not supported before API 31.");
    }
}
