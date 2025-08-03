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
import com.android.car.telemetry.AtomsProto.WTFOccurred;
import com.android.internal.util.Preconditions;

/**
 * Atom data converter for atoms of type {@link WTFOccurred}.
 */
public class WtfOccurredConverter extends AbstractAtomConverter<WTFOccurred> {

    private static final SparseArray<AtomFieldAccessor<WTFOccurred, ?>> sAtomFieldAccessorMap =
            new SparseArray<>();
    static {
        sAtomFieldAccessorMap.append(1, new AtomFieldAccessor<>(
                "uid",
                a -> a.hasUid(),
                a -> a.getUid()
        ));
        sAtomFieldAccessorMap.append(2, new AtomFieldAccessor<>(
                "tag",
                a -> a.hasTag(),
                a -> a.getTag()
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
                "error_source",
                a -> a.hasErrorSource(),
                a -> a.getErrorSource().getNumber()
        ));
    }

    WtfOccurredConverter() {
        super();
    }

    @Override
    @NonNull
    SparseArray<AtomFieldAccessor<WTFOccurred, ?>> getAtomFieldAccessorMap() {
        return sAtomFieldAccessorMap;
    }

    @Override
    @NonNull
    WTFOccurred getAtomData(@NonNull AtomsProto.Atom atom) {
        Preconditions.checkArgument(atom.hasWtfOccurred(), "Atom doesn't contain WtfOccurred");
        return atom.getWtfOccurred();
    }

    @Override
    @NonNull
    String getAtomDataClassName() {
        return WTFOccurred.class.getSimpleName();
    }
}
