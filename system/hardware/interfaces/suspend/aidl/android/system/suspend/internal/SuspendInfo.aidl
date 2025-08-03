/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.system.suspend.internal;

parcelable SuspendInfo {
    /* Total number of times that suspend was attempted */
    long suspendAttemptCount;

    /* Total number of times that suspend attempt failed */
    long failedSuspendCount;

    /**
     * Total number of times that a short suspend occurred. A successful suspend is considered a
     * short suspend if the suspend duration is less than suspend.short_suspend_threshold_millis
     */
    long shortSuspendCount;

    /* Total time, in milliseconds, spent in suspend */
    long suspendTimeMillis;

    /* Total time, in milliseconds, spent in short suspends */
    long shortSuspendTimeMillis;

    /* Total time, in milliseconds, spent doing suspend/resume work for successful suspends */
    long suspendOverheadTimeMillis;

    /* Total time, in milliseconds, spent doing suspend/resume work for failed suspends */
    long failedSuspendOverheadTimeMillis;

    /**
     * Total number of times the number of consecutive bad (short, failed) suspends
     * crossed suspend.backoff_threshold_count
     */
    long newBackoffCount;

    /**
     * Total number of times the number of consecutive bad (short, failed) suspends
     * exceeded suspend.backoff_threshold_count
     */
    long backoffContinueCount;

    /* Total time, in milliseconds, that system has waited between suspend attempts */
    long sleepTimeMillis;
}