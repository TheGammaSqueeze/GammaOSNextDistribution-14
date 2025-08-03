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

package com.android.car.telemetry.publisher.net;

import android.annotation.NonNull;
import android.app.usage.NetworkStatsManager;
import android.net.NetworkTemplate;

/** Proxy for {@link NetworkStatsManager} to improve testability. */
public class NetworkStatsManagerProxy {
    private final NetworkStatsManager mNetworkStatsManager;

    /** Creates a proxy for the given manager. */
    public NetworkStatsManagerProxy(@NonNull NetworkStatsManager networkStatsManager) {
        mNetworkStatsManager = networkStatsManager;
    }

    /** See the original {@link NetworkStatsManager#querySummary}. */
    @NonNull
    public NetworkStatsWrapper querySummary(
            @NonNull NetworkTemplate template, long start, long end) {
        return new NetworkStatsWrapper(mNetworkStatsManager.querySummary(template, start, end));
    }

    /** See the original {@link NetworkStatsManager#queryTaggedSummary}. */
    @NonNull
    public NetworkStatsWrapper queryTaggedSummary(
            @NonNull NetworkTemplate template, long start, long end) {
        return new NetworkStatsWrapper(
                mNetworkStatsManager.queryTaggedSummary(template, start, end));
    }
}
