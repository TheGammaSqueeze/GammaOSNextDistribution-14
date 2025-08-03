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

import static com.android.car.telemetry.AtomsProto.ActivityForegroundStateChanged.CLASS_NAME_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ActivityForegroundStateChanged.PKG_NAME_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ActivityForegroundStateChanged.STATE_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ActivityForegroundStateChanged.UID_FIELD_NUMBER;
import static com.android.car.telemetry.publisher.Constants.STATS_BUNDLE_KEY_PREFIX;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.PersistableBundle;
import android.util.SparseArray;

import com.android.car.telemetry.AtomsProto.ActivityForegroundStateChanged;
import com.android.car.telemetry.AtomsProto.ActivityForegroundStateChanged.State;
import com.android.car.telemetry.AtomsProto.Atom;
import com.android.car.telemetry.StatsLogProto.DimensionsValue;
import com.android.car.telemetry.publisher.HashUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public class ActivityForegroundStateChangedConverterTest {
    private static final Atom ATOM_A =
            Atom.newBuilder()
                    .setActivityForegroundStateChanged(ActivityForegroundStateChanged.newBuilder()
                            .setClassName("className1")
                            .setState(State.BACKGROUND))
                    .build();

    private static final Atom ATOM_B =
            Atom.newBuilder()
                    .setActivityForegroundStateChanged(ActivityForegroundStateChanged.newBuilder()
                            .setClassName("className2")
                            .setState(State.FOREGROUND))
                    .build();

    private static final Atom ATOM_MISMATCH =
            Atom.newBuilder()
                    .setActivityForegroundStateChanged(ActivityForegroundStateChanged.newBuilder()
                            // Some fields are not set, creating mismatch with above atoms
                            .setClassName("className2"))
                    .build();

    private static final List<Integer> DIM_FIELDS_IDS = Arrays.asList(1, 2);
    private static final Long HASH_1 = HashUtils.murmur2Hash64("package.name.1");
    private static final Long HASH_2 = HashUtils.murmur2Hash64("package.name.2");
    private static final Map<Long, String> HASH_STR_MAP = Map.of(
            HASH_1, "package.name.1",
            HASH_2, "package.name.2");

    private static final List<DimensionsValue> DV_PAIR_A =
            Arrays.asList(
                    DimensionsValue.newBuilder().setValueInt(1000).build(),
                    DimensionsValue.newBuilder().setValueStrHash(HASH_1).build());

    private static final List<DimensionsValue> DV_PAIR_B =
            Arrays.asList(
                    DimensionsValue.newBuilder().setValueInt(2000).build(),
                    DimensionsValue.newBuilder().setValueStrHash(HASH_2).build());

    private static final List<DimensionsValue> DV_PAIR_MALFORMED =
            Arrays.asList(
                    DimensionsValue.newBuilder().setValueInt(3000).build(),
                    // Wrong format since leaf level dimension value should set value, not field
                    DimensionsValue.newBuilder().setField(3).build());

    // Subject of the test.
    private ActivityForegroundStateChangedConverter mConverter =
            new ActivityForegroundStateChangedConverter();

    @Test
    public void testConvertAtomsListWithDimensionValues_putsCorrectDataToPersistableBundle()
            throws StatsConversionException {
        List<Atom> atomsList = Arrays.asList(ATOM_A, ATOM_B);
        List<List<DimensionsValue>> dimensionsValuesList = Arrays.asList(DV_PAIR_A, DV_PAIR_B);

        SparseArray<AtomFieldAccessor<ActivityForegroundStateChanged, ?>> accessorMap =
                mConverter.getAtomFieldAccessorMap();

        PersistableBundle bundle = mConverter.convert(atomsList, DIM_FIELDS_IDS,
                dimensionsValuesList, HASH_STR_MAP);

        assertThat(bundle.size()).isEqualTo(4);
        assertThat(bundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(UID_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(1000, 2000).inOrder();
        assertThat(Arrays.asList(bundle.getStringArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(PKG_NAME_FIELD_NUMBER).getFieldName())))
            .containsExactly("package.name.1", "package.name.2").inOrder();
        assertThat(Arrays.asList(bundle.getStringArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        CLASS_NAME_FIELD_NUMBER).getFieldName())))
            .containsExactly("className1", "className2").inOrder();
        assertThat(bundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(STATE_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(0, 1).inOrder();  // States background=0 and foreground=1
    }

    @Test
    public void testAtomSetFieldInconsistency_throwsException() {
        List<Atom> atomsList = Arrays.asList(ATOM_A, ATOM_MISMATCH);
        List<List<DimensionsValue>> dimensionsValuesList = Arrays.asList(DV_PAIR_A, DV_PAIR_B);

        assertThrows(
                StatsConversionException.class,
                () -> mConverter.convert(
                        atomsList,
                        DIM_FIELDS_IDS,
                        dimensionsValuesList,
                        HASH_STR_MAP));
    }

    @Test
    public void testMalformedDimensionValue_throwsException() {
        List<Atom> atomsList = Arrays.asList(ATOM_A, ATOM_B);
        List<List<DimensionsValue>> dimensionsValuesList =
                Arrays.asList(DV_PAIR_A, DV_PAIR_MALFORMED);

        assertThrows(
                StatsConversionException.class,
                () -> mConverter.convert(
                        atomsList,
                        DIM_FIELDS_IDS,
                        dimensionsValuesList,
                        HASH_STR_MAP));
    }
}
