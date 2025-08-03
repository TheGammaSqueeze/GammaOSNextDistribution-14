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

package com.android.networkstack.apishim.api30;

import static com.android.modules.utils.build.SdkLevel.isAtLeastR;

import android.content.Context;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.networkstack.apishim.common.ConnectivityManagerShim;
import com.android.networkstack.apishim.common.UnsupportedApiLevelException;

/**
 * Implementation of {@link ConnectivityManagerShim} for API 30.
 */
@RequiresApi(Build.VERSION_CODES.R)
public class ConnectivityManagerShimImpl
        extends com.android.networkstack.apishim.api29.ConnectivityManagerShimImpl {
    protected ConnectivityManagerShimImpl(Context context) {
        super(context);
    }

    /**
     * Get a new instance of {@link ConnectivityManagerShim}.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static ConnectivityManagerShim newInstance(Context context) {
        if (!isAtLeastR()) {
            return com.android.networkstack.apishim.api29.ConnectivityManagerShimImpl
                    .newInstance(context);
        }
        return new ConnectivityManagerShimImpl(context);
    }

    /**
     * See android.net.ConnectivityManager#requestBackgroundNetwork
     * @throws UnsupportedApiLevelException if API is not available in this API level.
     */
    @Override
    public void requestBackgroundNetwork(@NonNull NetworkRequest request,
            @NonNull NetworkCallback networkCallback, @NonNull Handler handler)
            throws UnsupportedApiLevelException {
        // Not supported for API 30.
        throw new UnsupportedApiLevelException("Not supported in API 30.");
    }

    @NonNull
    @Override
    protected NetworkRequest.Builder makeEmptyCapabilitiesRequest() {
        return new NetworkRequest.Builder().clearCapabilities();
    }
}
