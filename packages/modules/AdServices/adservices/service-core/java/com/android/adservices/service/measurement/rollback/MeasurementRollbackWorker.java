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

package com.android.adservices.service.measurement.rollback;

import android.annotation.NonNull;
import android.app.adservices.AdServicesManager;
import android.util.Pair;

public interface MeasurementRollbackWorker<T> {
    /**
     * Record that a measurement deletion event happened in rollback-proof storage.
     *
     * @param deletionApiType the type of deletion
     * @param currentApexVersion the current version of the apex that AdServices code is in
     */
    void recordAdServicesDeletionOccurred(
            @AdServicesManager.DeletionApiType int deletionApiType, long currentApexVersion);

    /**
     * Clear out measurement deletion metadata from rollback-proof storage.
     *
     * @param storageIdentifier metadata passed to this method to facilitate deletion from storage
     */
    void clearAdServicesDeletionOccurred(@NonNull T storageIdentifier);

    /**
     * Get the data about measurement deletion stored in rollback-safe storage, if any.
     *
     * <p>If no metadata is present, return null.
     *
     * @param deletionApiType the type of deletion
     * @return a {@link Pair} where the first item contains the apex version when the deletion
     *     happened, and the second contains metadata to assist with deleting this record.
     */
    Pair<Long, T> getAdServicesDeletionRollbackMetadata(
            @AdServicesManager.DeletionApiType int deletionApiType);
}
