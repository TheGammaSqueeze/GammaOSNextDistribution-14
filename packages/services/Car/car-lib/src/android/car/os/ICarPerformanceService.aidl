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

package android.car.os;

import android.car.os.ThreadPolicyWithPriority;

/** @hide */
interface ICarPerformanceService {
    /**
     * Sets the thread priority for a specific thread.
     *
     * The thread must belong to the calling process.
     *
     * @throws SecurityException If permission check failed.
     * @throws ServiceSpecificException If the operation failed.
     * @throws IllegalArgumentException If the provided tid does not belong to the calling process.
     */
    void setThreadPriority(int tid, in ThreadPolicyWithPriority policyWithPriority);

    /**
     * Gets the thread priority for a specific thread.
     *
     * The thread must belong to the calling process.
     *
     * @throws SecurityException If permission check failed.
     * @throws ServiceSpecificException If the operation failed.
     * @throws IllegalArgumentException If the provided tid does not belong to the calling process.
     */
    ThreadPolicyWithPriority getThreadPriority(int tid);
}
