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

package com.android.adservices.service.measurement.util;

import static com.android.adservices.service.measurement.util.JobLockHolder.Type.AGGREGATE_REPORTING;
import static com.android.adservices.service.measurement.util.JobLockHolder.Type.ASYNC_REGISTRATION_PROCESSING;
import static com.android.adservices.service.measurement.util.JobLockHolder.Type.ATTRIBUTION_PROCESSING;
import static com.android.adservices.service.measurement.util.JobLockHolder.Type.DEBUG_REPORTING;
import static com.android.adservices.service.measurement.util.JobLockHolder.Type.EVENT_REPORTING;
import static com.android.adservices.service.measurement.util.JobLockHolder.Type.VERBOSE_DEBUG_REPORTING;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Holds the lock to be used by the background jobs. The locks will be used by multiple jobs,
 * fallback jobs, and similar functions that could perform the same action. However, only one of
 * these functions should be able to process at a time. This is to prevent conflicts and ensure that
 * the system runs smoothly.
 */
public class JobLockHolder {
    public enum Type {
        AGGREGATE_REPORTING,
        ASYNC_REGISTRATION_PROCESSING,
        ATTRIBUTION_PROCESSING,
        DEBUG_REPORTING,
        EVENT_REPORTING,
        VERBOSE_DEBUG_REPORTING
    }

    private static final Map<Type, JobLockHolder> INSTANCES =
            Map.of(
                    AGGREGATE_REPORTING, new JobLockHolder(),
                    ASYNC_REGISTRATION_PROCESSING, new JobLockHolder(),
                    ATTRIBUTION_PROCESSING, new JobLockHolder(),
                    DEBUG_REPORTING, new JobLockHolder(),
                    EVENT_REPORTING, new JobLockHolder(),
                    VERBOSE_DEBUG_REPORTING, new JobLockHolder());

    /* Holds the lock that will be given per instance */
    private final ReentrantLock mLock;

    private JobLockHolder() {
        mLock = new ReentrantLock();
    }

    /**
     * Retrieves an instance that has already been created based on its type.
     *
     * @param type of lock to be shared by similar tasks
     * @return lock instance
     */
    public static JobLockHolder getInstance(Type type) {
        return INSTANCES.get(type);
    }

    /**
     * Tries to acquire the lock. Returns true if the lock was acquired successfully or false if it
     * has already been acquired by another thread. If lock was acquired, at the end of processing,
     * a call to {@link JobLockHolder#unlock()} will need to be made.
     *
     * @return a boolean determining if the lock was successfully acquired or not.
     */
    public boolean tryLock() {
        return mLock.tryLock();
    }

    /**
     * Releases the lock that was previously acquired. It must be called after the lock has been
     * successfully acquired.
     */
    public void unlock() {
        mLock.unlock();
    }
}
