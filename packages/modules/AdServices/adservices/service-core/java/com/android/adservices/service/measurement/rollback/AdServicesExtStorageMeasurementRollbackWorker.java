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

import static com.android.adservices.service.measurement.rollback.MeasurementRollbackCompatManager.APEX_VERSION_WHEN_NOT_FOUND;

import android.annotation.NonNull;
import android.content.Context;
import android.util.Pair;

import com.android.adservices.LogUtil;
import com.android.adservices.service.extdata.AdServicesExtDataStorageServiceManager;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.function.Consumer;

public final class AdServicesExtStorageMeasurementRollbackWorker
        implements MeasurementRollbackWorker<Void> {
    private final AdServicesExtDataStorageServiceManager mStorageManager;

    public AdServicesExtStorageMeasurementRollbackWorker(@NonNull Context context) {
        this(AdServicesExtDataStorageServiceManager.getInstance(Objects.requireNonNull(context)));
    }

    @VisibleForTesting
    AdServicesExtStorageMeasurementRollbackWorker(
            AdServicesExtDataStorageServiceManager storageManager) {
        mStorageManager = Objects.requireNonNull(storageManager);
    }

    @Override
    public void recordAdServicesDeletionOccurred(int deletionApiType, long currentApexVersion) {
        boolean success = mStorageManager.setMeasurementRollbackApexVersion(currentApexVersion);
        log(success, "Recording measurement rollback deletion in AdServicesExtData successful: ");
    }

    @Override
    public void clearAdServicesDeletionOccurred(@NonNull Void storageIdentifier) {
        boolean success =
                mStorageManager.setMeasurementRollbackApexVersion(APEX_VERSION_WHEN_NOT_FOUND);
        log(success, "Clear measurement rollback deletion in AdServicesExtData successful: ");
    }

    @Override
    public Pair<Long, Void> getAdServicesDeletionRollbackMetadata(int deletionApiType) {
        long apex = mStorageManager.getMeasurementRollbackApexVersion();
        return apex == APEX_VERSION_WHEN_NOT_FOUND ? null : Pair.create(apex, null);
    }

    private void log(boolean success, String prefix) {
        Consumer<String> logger = success ? LogUtil::d : LogUtil::e;
        logger.accept(prefix + success);
    }
}
