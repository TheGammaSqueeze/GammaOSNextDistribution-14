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
import android.annotation.Nullable;
import android.os.PersistableBundle;

import com.android.car.telemetry.AtomsProto.Atom;
import com.android.car.telemetry.StatsLogProto.DimensionsValue;

import java.util.List;
import java.util.Map;

/**
 * Class for converting atom data to {@link PersistableBundle} compatible format. Uses specific
 * atom data converters that extend the {@link AbstractAtomConverter}.
 */
public class AtomListConverter {
    // Map of pushed atom cases to corresponding atom converter.
    private static Map<Atom.PushedCase, AbstractAtomConverter<?>> sPushedCaseConverters = Map.of(
            /* key = */ Atom.PushedCase.APP_START_MEMORY_STATE_CAPTURED,
            /* value = */ new AppStartMemoryStateCapturedConverter(),
            /* key = */ Atom.PushedCase.ACTIVITY_FOREGROUND_STATE_CHANGED,
            /* value = */ new ActivityForegroundStateChangedConverter(),
            Atom.PushedCase.APP_CRASH_OCCURRED, new AppCrashOccurredConverter(),
            Atom.PushedCase.ANR_OCCURRED, new AnrOccurredConverter(),
            Atom.PushedCase.WTF_OCCURRED, new WtfOccurredConverter(),
            Atom.PushedCase.PROCESS_START_TIME, new ProcessStartTimeConverter());

    // Map of pulled atom cases to corresponding atom converter.
    private static Map<Atom.PulledCase, AbstractAtomConverter<?>> sPulledCaseConverters = Map.of(
            Atom.PulledCase.PROCESS_MEMORY_STATE, new ProcessMemoryStateConverter(),
            Atom.PulledCase.PROCESS_CPU_TIME, new ProcessCpuTimeConverter(),
            Atom.PulledCase.PROCESS_MEMORY_SNAPSHOT, new ProcessMemorySnapshotConverter());

    /**
     * Converts a list of atoms to separate the atoms fields values into arrays to be put into the
     * {@link PersistableBundle}.
     * The list of atoms must contain atoms of same type.
     * Only fields with types allowed in {@link PersistableBundle} are added to the bundle.
     *
     * @param atoms list of {@link Atom} of the same type.
     * @param dimensionsFieldsIds field ids for fields that are encoded in {@link DimensionsValue}.
     * @param dimensionsValuesList dimension value groups matching mDimensionsFieldsIds.
     * @param hashToStringMap hash to string mapping for decoding the some dimension values.
     * @return {@link PersistableBundle} that holds the converted atom fields.
     * @throws StatsConversionException if atom field mismatch or can't convert dimension value.
     */
    static PersistableBundle convert(
            @NonNull List<Atom> atoms,
            @Nullable List<Integer> dimensionsFieldsIds,
            @Nullable List<List<DimensionsValue>> dimensionsValuesList,
            @Nullable Map<Long, String> hashToStringMap) throws StatsConversionException {
        if (atoms.size() == 0) {
            throw new StatsConversionException("Atoms list is empty.");
        }
        // The atoms are either pushed or pulled type atoms.
        if (atoms.get(0).getPushedCase() != Atom.PushedCase.PUSHED_NOT_SET) {
            return sPushedCaseConverters.get(atoms.get(0).getPushedCase()).convert(
                    atoms, dimensionsFieldsIds, dimensionsValuesList, hashToStringMap);
        } else {
            return sPulledCaseConverters.get(atoms.get(0).getPulledCase()).convert(
                    atoms, dimensionsFieldsIds, dimensionsValuesList, hashToStringMap);
        }
    }
}
