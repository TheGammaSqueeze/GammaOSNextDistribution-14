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

import static com.android.car.telemetry.AtomsProto.ProcessCpuTime.PROCESS_NAME_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ProcessCpuTime.SYSTEM_TIME_MILLIS_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ProcessCpuTime.UID_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ProcessCpuTime.USER_TIME_MILLIS_FIELD_NUMBER;
import static com.android.car.telemetry.publisher.Constants.STATS_BUNDLE_KEY_PREFIX;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.PersistableBundle;
import android.util.SparseArray;

import com.android.car.telemetry.AtomsProto.Atom;
import com.android.car.telemetry.AtomsProto.ProcessCpuTime;
import com.android.car.telemetry.StatsLogProto.DimensionsValue;
import com.android.car.telemetry.publisher.HashUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public class ProcessCpuTimeConverterTest {
    private static final Atom ATOM_A =
            Atom.newBuilder()
                    .setProcessCpuTime(ProcessCpuTime.newBuilder()
                            .setUserTimeMillis(234L)
                            .setSystemTimeMillis(111L))
                    .build();

    private static final Atom ATOM_B =
            Atom.newBuilder()
                    .setProcessCpuTime(ProcessCpuTime.newBuilder()
                            .setUserTimeMillis(345L)
                            .setSystemTimeMillis(222L))
                    .build();

    private static final Atom ATOM_MISMATCH =
            Atom.newBuilder()
                    .setProcessCpuTime(ProcessCpuTime.newBuilder()
                            // Some fields are not set, creating mismatch with above atoms
                            .setSystemTimeMillis(333L))
                    .build();

    private static final List<Integer> DIM_FIELDS_IDS = Arrays.asList(1, 2);
    private static final Long HASH_1 = HashUtils.murmur2Hash64("process.name.1");
    private static final Long HASH_2 = HashUtils.murmur2Hash64("process.name.2");
    private static final Map<Long, String> HASH_STR_MAP = Map.of(
            HASH_1, "process.name.1",
            HASH_2, "process.name.2");

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
    private ProcessCpuTimeConverter mConverter = new ProcessCpuTimeConverter();

    @Test
    public void testConvertAtomsListWithDimensionValues_putsCorrectDataToPersistableBundle()
            throws StatsConversionException {
        List<Atom> atomsList = Arrays.asList(ATOM_A, ATOM_B);
        List<List<DimensionsValue>> dimensionsValuesList = Arrays.asList(DV_PAIR_A, DV_PAIR_B);
        SparseArray<AtomFieldAccessor<ProcessCpuTime, ?>> accessorMap =
                mConverter.getAtomFieldAccessorMap();

        PersistableBundle bundle = mConverter.convert(atomsList, DIM_FIELDS_IDS,
                dimensionsValuesList, HASH_STR_MAP);

        assertThat(bundle.size()).isEqualTo(4);
        assertThat(bundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(UID_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(1000, 2000).inOrder();
        assertThat(Arrays.asList(bundle.getStringArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        PROCESS_NAME_FIELD_NUMBER).getFieldName())))
            .containsExactly("process.name.1", "process.name.2").inOrder();
        assertThat(bundle.getLongArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        USER_TIME_MILLIS_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(234L, 345L).inOrder();
        assertThat(bundle.getLongArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        SYSTEM_TIME_MILLIS_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(111L, 222L).inOrder();
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
