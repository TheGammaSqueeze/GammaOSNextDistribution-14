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

package com.android.permissioncontroller.permission.service

import com.android.permissioncontroller.permission.data.PermissionEvent

/** Persistent storage for retrieving persisted permission event data. */
interface PermissionEventStorage<T : PermissionEvent> {
    /**
     * Persist a permission event for retrieval later.
     *
     * @param event the event to store
     * @return whether the storage was successful
     */
    suspend fun storeEvent(event: T): Boolean

    /**
     * Returns all events, sorted from newest to oldest. This returns directly what is in storage
     * and does not do any additional validation checking.
     */
    suspend fun loadEvents(): List<T>

    /** Clear all events. */
    suspend fun clearEvents()

    /**
     * Remove data older than a certain threshold defined by the implementing class.
     *
     * @return whether the storage was successful
     */
    suspend fun removeOldData(): Boolean

    /**
     * Remove all the event data for a particular package.
     *
     * @param packageName of the package to remove
     * @return whether the storage was successful
     */
    suspend fun removeEventsForPackage(packageName: String): Boolean

    /**
     * Update event timestamps based on the delta in system time.
     *
     * @param diffSystemTimeMillis the difference between the current and old system times. Positive
     *   values mean that the time has changed in the future and negative means the time was changed
     *   into the past.
     * @return whether the storage was successful
     */
    suspend fun updateEventsBySystemTimeDelta(diffSystemTimeMillis: Long): Boolean
}
