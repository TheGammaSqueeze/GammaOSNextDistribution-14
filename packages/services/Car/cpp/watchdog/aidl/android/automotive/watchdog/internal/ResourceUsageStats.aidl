/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.automotive.watchdog.internal;

import android.automotive.watchdog.internal.SystemSummaryUsageStats;
import android.automotive.watchdog.internal.UidResourceUsageStats;

/**
 * Structure that describes the resource usage stats for the overall system
 * and individual packages.
 */
parcelable ResourceUsageStats {
    /**
     * Epoch millis when resource usage collection began.
     */
    long startTimeEpochMillis;

    /**
     * Total duration in milliseconds of the stats collection event.
     */
    long durationInMillis;

    /**
     * Usage stats summary of the overall system.
     */
    SystemSummaryUsageStats systemSummaryUsageStats;

    /**
     * List of resource usage stats per-UID.
     */
    List<UidResourceUsageStats> uidResourceUsageStats;
}