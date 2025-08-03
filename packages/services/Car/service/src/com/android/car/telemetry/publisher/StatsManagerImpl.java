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

package com.android.car.telemetry.publisher;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.StatsManager;
import android.app.StatsManager.StatsUnavailableException;

/** Implementation for {@link StatsManagerProxy} */
public class StatsManagerImpl implements StatsManagerProxy {
    private final StatsManager mStatsManager;

    public StatsManagerImpl(@NonNull StatsManager statsManager) {
        mStatsManager = statsManager;
    }

    @Override
    @Nullable
    public byte[] getReports(long configKey) throws StatsUnavailableException {
        return mStatsManager.getReports(configKey);
    }

    @Override
    public void addConfig(long configKey, @NonNull byte[] data) throws StatsUnavailableException {
        mStatsManager.addConfig(configKey, data);
    }

    @Override
    public void removeConfig(long configKey) throws StatsUnavailableException {
        mStatsManager.removeConfig(configKey);
    }

    @Override
    @Nullable
    public byte[] getStatsMetadata() throws StatsUnavailableException {
        return mStatsManager.getStatsMetadata();
    }
}
