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

package com.android.car.telemetry.publisher.statsconverters;

import android.annotation.NonNull;
import android.util.SparseArray;

import com.android.car.telemetry.AtomsProto.Atom;
import com.android.car.telemetry.AtomsProto.ProcessMemorySnapshot;
import com.android.internal.util.Preconditions;

/**
 * Atom data converter for atoms of type {@link ProcessMemorySnapshot}.
 */
public class ProcessMemorySnapshotConverter extends AbstractAtomConverter<ProcessMemorySnapshot> {

    private static final SparseArray<AtomFieldAccessor<ProcessMemorySnapshot, ?>>
            sAtomFieldAccessorMap = new SparseArray<>();
    static {
        sAtomFieldAccessorMap.append(1, new AtomFieldAccessor<>(
                "uid",
                a -> a.hasUid(),
                a -> a.getUid()
        ));
        sAtomFieldAccessorMap.append(2, new AtomFieldAccessor<>(
                "process_name",
                a -> a.hasProcessName(),
                a -> a.getProcessName()
        ));
        sAtomFieldAccessorMap.append(3, new AtomFieldAccessor<>(
                "pid",
                a -> a.hasPid(),
                a -> a.getPid()
        ));
        sAtomFieldAccessorMap.append(4, new AtomFieldAccessor<>(
                "oom_score_adj",
                a -> a.hasOomScoreAdj(),
                a -> a.getOomScoreAdj()
        ));
        sAtomFieldAccessorMap.append(5, new AtomFieldAccessor<>(
                "rss_in_kilobytes",
                a -> a.hasRssInKilobytes(),
                a -> a.getRssInKilobytes()
        ));
        sAtomFieldAccessorMap.append(6, new AtomFieldAccessor<>(
                "anon_rss_in_kilobytes",
                a -> a.hasAnonRssInKilobytes(),
                a -> a.getAnonRssInKilobytes()
        ));
        sAtomFieldAccessorMap.append(7, new AtomFieldAccessor<>(
                "swap_in_kilobytes",
                a -> a.hasSwapInKilobytes(),
                a -> a.getSwapInKilobytes()
        ));
        sAtomFieldAccessorMap.append(8, new AtomFieldAccessor<>(
                "anon_rss_and_swap_in_kilobytes",
                a -> a.hasAnonRssAndSwapInKilobytes(),
                a -> a.getAnonRssAndSwapInKilobytes()
        ));
        sAtomFieldAccessorMap.append(9, new AtomFieldAccessor<>(
                "gpu_memory_kb",
                a -> a.hasGpuMemoryKb(),
                a -> a.getGpuMemoryKb()
        ));
        sAtomFieldAccessorMap.append(10, new AtomFieldAccessor<>(
                "has_foreground_services",
                a -> a.hasHasForegroundServices(),
                a -> a.getHasForegroundServices()
        ));
    }

    ProcessMemorySnapshotConverter() {
        super();
    }

    @NonNull
    @Override
    SparseArray<AtomFieldAccessor<ProcessMemorySnapshot, ?>> getAtomFieldAccessorMap() {
        return sAtomFieldAccessorMap;
    }

    @NonNull
    @Override
    ProcessMemorySnapshot getAtomData(@NonNull Atom atom) {
        Preconditions.checkArgument(
                atom.hasProcessMemorySnapshot(), "Atom doesn't contain ProcessMemorySnapshot");
        return atom.getProcessMemorySnapshot();
    }

    @NonNull
    @Override
    String getAtomDataClassName() {
        return ProcessMemorySnapshot.class.getSimpleName();
    }
}
