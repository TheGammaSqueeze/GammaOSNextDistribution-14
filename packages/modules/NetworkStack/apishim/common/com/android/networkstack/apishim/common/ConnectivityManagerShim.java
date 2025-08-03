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

import android.net.ConnectivityManager.NetworkCallback;
import android.net.NetworkRequest;
import android.os.Handler;
import android.util.Range;

import androidx.annotation.NonNull;

import java.util.Collection;

/**
 * Interface used to access API methods in {@link android.net.ConnectivityManager}, with
 * appropriate fallbacks if the methods are not yet part of the released API.
 *
 * <p>This interface makes it easier for callers to use ConnectivityManagerShimImpl, as it's more
 * obvious what methods must be implemented on each API level, and it abstracts from callers the
 * need to reference classes that have different implementations (which also does not work well
 * with IDEs).
 */
public interface ConnectivityManagerShim {
    /** See android.net.ConnectivityManager#requestBackgroundNetwork */
    void requestBackgroundNetwork(@NonNull NetworkRequest request,
            @NonNull NetworkCallback networkCallback, @NonNull Handler handler)
            throws UnsupportedApiLevelException;

    /** See android.net.ConnectivityManager#registerSystemDefaultNetworkCallback */
    void registerSystemDefaultNetworkCallback(
            @NonNull NetworkCallback networkCallback, @NonNull Handler handler);

    /** See android.net.ConnectivityManager#registerDefaultNetworkCallbackForUid */
    default void registerDefaultNetworkCallbackForUid(
            int uid, @NonNull NetworkCallback networkCallback, @NonNull Handler handler)
            throws UnsupportedApiLevelException {
        throw new UnsupportedApiLevelException("Only supported starting from API 31");
    }

    /** See android.net.ConnectivityManager#setLegacyLockdownVpnEnabled */
    default void setLegacyLockdownVpnEnabled(boolean enabled) throws UnsupportedApiLevelException {
        throw new UnsupportedApiLevelException("Only supported starting from API 31");
    }

    /** See android.net.ConnectivityManager#setRequireVpnForUids */
    default void setRequireVpnForUids(boolean requireVpn, Collection<Range<Integer>> ranges)
            throws UnsupportedApiLevelException {
        throw new UnsupportedApiLevelException("Only supported starting from API 31");
    }
}
