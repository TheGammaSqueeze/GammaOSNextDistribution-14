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

package com.android.car.telemetry.publisher.statsconverters;

import android.annotation.NonNull;
import android.util.SparseArray;

import com.android.car.telemetry.AtomsProto;
import com.android.car.telemetry.AtomsProto.AppCrashOccurred;
import com.android.internal.util.Preconditions;

/**
 * Atom data converter for atoms of type {@link AppCrashOccurred}.
 */
public class AppCrashOccurredConverter extends AbstractAtomConverter<AppCrashOccurred> {

    private static final SparseArray<AtomFieldAccessor<AppCrashOccurred, ?>> sAtomFieldAccessorMap =
            new SparseArray<>();
    static {
        sAtomFieldAccessorMap.append(1, new AtomFieldAccessor<>(
                "uid",
                a -> a.hasUid(),
                a -> a.getUid()
        ));
        sAtomFieldAccessorMap.append(2, new AtomFieldAccessor<>(
                "event_type",
                a -> a.hasEventType(),
                a -> a.getEventType()
        ));
        sAtomFieldAccessorMap.append(3, new AtomFieldAccessor<>(
                "process_name",
                a -> a.hasProcessName(),
                a -> a.getProcessName()
        ));
        sAtomFieldAccessorMap.append(4, new AtomFieldAccessor<>(
                "pid",
                a -> a.hasPid(),
                a -> a.getPid()
        ));
        sAtomFieldAccessorMap.append(5, new AtomFieldAccessor<>(
                "package_name",
                a -> a.hasPackageName(),
                a -> a.getPackageName()
        ));
        sAtomFieldAccessorMap.append(6, new AtomFieldAccessor<>(
                "is_instant_app",
                a -> a.hasIsInstantApp(),
                a -> a.getIsInstantApp().getNumber()
        ));
        sAtomFieldAccessorMap.append(7, new AtomFieldAccessor<>(
                "foreground_state",
                a -> a.hasForegroundState(),
                a -> a.getForegroundState().getNumber()
        ));
        sAtomFieldAccessorMap.append(8, new AtomFieldAccessor<>(
                "error_source",
                a -> a.hasErrorSource(),
                a -> a.getErrorSource().getNumber()
        ));
        sAtomFieldAccessorMap.append(9, new AtomFieldAccessor<>(
                "is_incremental",
                a -> a.hasIsIncremental(),
                a -> a.getIsIncremental()
        ));
        sAtomFieldAccessorMap.append(10, new AtomFieldAccessor<>(
                "loading_progress",
                a -> a.hasLoadingProgress(),
                a -> a.getLoadingProgress()
        ));
        sAtomFieldAccessorMap.append(11, new AtomFieldAccessor<>(
                "millis_since_oldest_pending_read",
                a -> a.hasMillisSinceOldestPendingRead(),
                a -> a.getMillisSinceOldestPendingRead()
        ));
        sAtomFieldAccessorMap.append(12, new AtomFieldAccessor<>(
                "storage_health_code",
                a -> a.hasStorageHealthCode(),
                a -> a.getStorageHealthCode()
        ));
        sAtomFieldAccessorMap.append(13, new AtomFieldAccessor<>(
                "data_loader_status_code",
                a -> a.hasDataLoaderStatusCode(),
                a -> a.getDataLoaderStatusCode()
        ));
        sAtomFieldAccessorMap.append(14, new AtomFieldAccessor<>(
                "read_logs_enabled",
                a -> a.hasReadLogsEnabled(),
                a -> a.getReadLogsEnabled()
        ));
        sAtomFieldAccessorMap.append(15, new AtomFieldAccessor<>(
                "millis_since_last_data_loader_bind",
                a -> a.hasMillisSinceLastDataLoaderBind(),
                a -> a.getMillisSinceLastDataLoaderBind()
        ));
        sAtomFieldAccessorMap.append(16, new AtomFieldAccessor<>(
                "data_loader_bind_delay_millis",
                a -> a.hasDataLoaderBindDelayMillis(),
                a -> a.getDataLoaderBindDelayMillis()
        ));
        sAtomFieldAccessorMap.append(17, new AtomFieldAccessor<>(
                "total_delayed_reads",
                a -> a.hasTotalDelayedReads(),
                a -> a.getTotalDelayedReads()
        ));
        sAtomFieldAccessorMap.append(18, new AtomFieldAccessor<>(
                "total_failed_reads",
                a -> a.hasTotalFailedReads(),
                a -> a.getTotalFailedReads()
        ));
        sAtomFieldAccessorMap.append(19, new AtomFieldAccessor<>(
                "last_read_error_uid",
                a -> a.hasLastReadErrorUid(),
                a -> a.getLastReadErrorUid()
        ));
        sAtomFieldAccessorMap.append(20, new AtomFieldAccessor<>(
                "last_read_error_millis_since",
                a -> a.hasLastReadErrorMillisSince(),
                a -> a.getLastReadErrorMillisSince()
        ));
        sAtomFieldAccessorMap.append(21, new AtomFieldAccessor<>(
                "last_read_error_code",
                a -> a.hasLastReadErrorCode(),
                a -> a.getLastReadErrorCode()
        ));
        sAtomFieldAccessorMap.append(22, new AtomFieldAccessor<>(
                "total_delayed_reads_duration_millis",
                a -> a.hasTotalDelayedReadsDurationMillis(),
                a -> a.getTotalDelayedReadsDurationMillis()
        ));
    }

    AppCrashOccurredConverter() {
        super();
    }

    @Override
    @NonNull
    SparseArray<AtomFieldAccessor<AppCrashOccurred, ?>> getAtomFieldAccessorMap() {
        return sAtomFieldAccessorMap;
    }

    @Override
    @NonNull
    AppCrashOccurred getAtomData(@NonNull AtomsProto.Atom atom) {
        Preconditions.checkArgument(
                atom.hasAppCrashOccurred(), "Atom doesn't contain AppCrashOccurred");
        return atom.getAppCrashOccurred();
    }

    @Override
    @NonNull
    String getAtomDataClassName() {
        return AppCrashOccurred.class.getSimpleName();
    }
}
