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

/** Proxy for {@link StatsManager}, as it's marked as final and can't be used in tests. */
public interface StatsManagerProxy {
    /** See {@link StatsManager#getReports(long)}. */
    @Nullable
    byte[] getReports(long configKey) throws StatsUnavailableException;

    /** See {@link StatsManager#addConfig(long, byte[])}. */
    void addConfig(long configKey, @NonNull byte[] data) throws StatsUnavailableException;

    /** See {@link StatsManager#removeConfig(long)}. */
    void removeConfig(long configKey) throws StatsUnavailableException;

    /** See {@link StatsManager#getStatsMetadata()}. */
    @Nullable
    byte[] getStatsMetadata() throws StatsUnavailableException;
}
