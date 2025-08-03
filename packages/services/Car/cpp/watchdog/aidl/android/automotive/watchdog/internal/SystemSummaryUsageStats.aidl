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

import android.automotive.watchdog.PerStateBytes;

/**
 * Structure that describes the overall system resource usage stats.
 */
parcelable SystemSummaryUsageStats {
    /**
     * Overall system CPU non-idle cycles.
     */
    long cpuNonIdleCycles;

    /**
     * Overall system CPU non-idle time in milliseconds.
     */
    long cpuNonIdleTimeMillis;

    /**
     * Overall system CPU idle time in milliseconds.
     */
    long cpuIdleTimeMillis;

    /**
     * Overall system context switches.
     */
    long contextSwitchesCount;

    /**
     * Number of I/O blocked processes
     */
    int ioBlockedProcessCount;

    /**
     * Number of processes running during the collection event.
     */
    int totalProcessCount;

    /**
     * Number of major page faults since previous collection.
     */
    int totalMajorPageFaults;

    /**
     * Number of overall system I/O read bytes grouped by state.
     */
    PerStateBytes totalIoReads;

    /**
     * Number of overall I/O write bytes grouped by state.
     */
    PerStateBytes totalIoWrites;
}