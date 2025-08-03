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

package com.android.car.telemetry.publisher.statsconverters;

import android.annotation.NonNull;
import android.util.SparseArray;

import com.android.car.telemetry.AtomsProto;
import com.android.car.telemetry.AtomsProto.ProcessStartTime;
import com.android.internal.util.Preconditions;

/**
 * Atom data converter for atoms of type {@link ProcessStartTime}.
 */
public class ProcessStartTimeConverter extends AbstractAtomConverter<ProcessStartTime> {
    private static final SparseArray<AtomFieldAccessor<ProcessStartTime, ?>> sAtomFieldAccessorMap =
            new SparseArray<>();
    static {
        sAtomFieldAccessorMap.append(1, new AtomFieldAccessor<>(
                "uid",
                a -> a.hasUid(),
                a -> a.getUid()
        ));
        sAtomFieldAccessorMap.append(2, new AtomFieldAccessor<>(
                "pid",
                a -> a.hasPid(),
                a -> a.getPid()
        ));
        sAtomFieldAccessorMap.append(3, new AtomFieldAccessor<>(
                "process_name",
                a -> a.hasProcessName(),
                a -> a.getProcessName()
        ));
        sAtomFieldAccessorMap.append(4, new AtomFieldAccessor<>(
                "type",
                a -> a.hasType(),
                a -> a.getType().getNumber()
        ));
        sAtomFieldAccessorMap.append(5, new AtomFieldAccessor<>(
                "process_start_time_millis",
                a -> a.hasProcessStartTimeMillis(),
                a -> a.getProcessStartTimeMillis()
        ));
        sAtomFieldAccessorMap.append(6, new AtomFieldAccessor<>(
                "bind_application_delay_millis",
                a -> a.hasBindApplicationDelayMillis(),
                a -> a.getBindApplicationDelayMillis()
        ));
        sAtomFieldAccessorMap.append(7, new AtomFieldAccessor<>(
                "process_start_delay_millis",
                a -> a.hasProcessStartDelayMillis(),
                a -> a.getProcessStartDelayMillis()
        ));
        sAtomFieldAccessorMap.append(8, new AtomFieldAccessor<>(
                "hosting_type",
                a -> a.hasHostingType(),
                a -> a.getHostingType()
        ));
        sAtomFieldAccessorMap.append(9, new AtomFieldAccessor<>(
                "hosting_name",
                a -> a.hasHostingName(),
                a -> a.getHostingName()
        ));
        sAtomFieldAccessorMap.append(10, new AtomFieldAccessor<>(
                "broadcast_action_name",
                a -> a.hasBroadcastActionName(),
                a -> a.getBroadcastActionName()
        ));
        sAtomFieldAccessorMap.append(11, new AtomFieldAccessor<>(
                "hosting_type_id",
                a -> a.hasHostingTypeId(),
                a -> a.getHostingTypeId().getNumber()
        ));
        sAtomFieldAccessorMap.append(12, new AtomFieldAccessor<>(
                "trigger_type",
                a -> a.hasTriggerType(),
                a -> a.getTriggerType().getNumber()
        ));
    }

    ProcessStartTimeConverter() {
        super();
    }

    @Override
    @NonNull
    SparseArray<AtomFieldAccessor<ProcessStartTime, ?>> getAtomFieldAccessorMap() {
        return sAtomFieldAccessorMap;
    }

    @Override
    @NonNull
    ProcessStartTime getAtomData(@NonNull AtomsProto.Atom atom) {
        Preconditions.checkArgument(
                atom.hasProcessStartTime(), "Atom doesn't contain ProcessStartTime");
        return atom.getProcessStartTime();
    }

    @Override
    @NonNull
    String getAtomDataClassName() {
        return ProcessStartTime.class.getSimpleName();
    }
}
