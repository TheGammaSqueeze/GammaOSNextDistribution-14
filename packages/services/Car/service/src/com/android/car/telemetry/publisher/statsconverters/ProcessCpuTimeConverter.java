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

import com.android.car.telemetry.AtomsProto.Atom;
import com.android.car.telemetry.AtomsProto.ProcessCpuTime;
import com.android.internal.util.Preconditions;

/**
 * Atom data converter for atoms of type {@link ProcessCpuTime}.
 */
public class ProcessCpuTimeConverter extends AbstractAtomConverter<ProcessCpuTime> {
    private static final SparseArray<AtomFieldAccessor<ProcessCpuTime, ?>> sAtomFieldAccessorMap =
            new SparseArray<>();
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
                "user_time_millis",
                a -> a.hasUserTimeMillis(),
                a -> a.getUserTimeMillis()
        ));
        sAtomFieldAccessorMap.append(4, new AtomFieldAccessor<>(
                "system_time_millis",
                a -> a.hasSystemTimeMillis(),
                a -> a.getSystemTimeMillis()
        ));
    }

    ProcessCpuTimeConverter() {
        super();
    }

    @Override
    @NonNull
    SparseArray<AtomFieldAccessor<ProcessCpuTime, ?>> getAtomFieldAccessorMap() {
        return sAtomFieldAccessorMap;
    }

    @Override
    @NonNull
    ProcessCpuTime getAtomData(@NonNull Atom atom) {
        Preconditions.checkArgument(
                atom.hasProcessCpuTime(), "Atom doesn't contain ProcessCpuTime");
        return atom.getProcessCpuTime();
    }

    @Override
    @NonNull
    String getAtomDataClassName() {
        return ProcessCpuTime.class.getSimpleName();
    }
}
