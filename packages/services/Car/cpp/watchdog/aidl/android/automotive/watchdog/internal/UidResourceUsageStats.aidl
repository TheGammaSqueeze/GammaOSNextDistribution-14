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

import android.automotive.watchdog.internal.CpuUsageStats;
import android.automotive.watchdog.internal.UidIoUsageStats;
import android.automotive.watchdog.internal.PackageIdentifier;
import android.automotive.watchdog.internal.ProcessCpuUsageStats;

/**
 * Structure that describes the CPU usage stats for a user package.
 */
parcelable UidResourceUsageStats {
    /**
     * Information of the package whose stats are stored in the below fields.
     */
    PackageIdentifier packageIdentifier;

    /**
     * Milliseconds since the package started.
     */
    long uidUptimeMillis;

    /**
     * CPU usage stats for the UID.
     */
    CpuUsageStats cpuUsageStats;

    /**
     * List of the UID's processes CPU usage stats.
     */
    List<ProcessCpuUsageStats> processCpuUsageStats;

    /**
     * I/O usage stats for the UID.
     */
    UidIoUsageStats ioUsageStats;
}