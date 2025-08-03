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

import static com.android.car.telemetry.AtomsProto.ProcessMemorySnapshot.ANON_RSS_AND_SWAP_IN_KILOBYTES_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ProcessMemorySnapshot.ANON_RSS_IN_KILOBYTES_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ProcessMemorySnapshot.GPU_MEMORY_KB_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ProcessMemorySnapshot.HAS_FOREGROUND_SERVICES_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ProcessMemorySnapshot.OOM_SCORE_ADJ_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ProcessMemorySnapshot.PID_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ProcessMemorySnapshot.PROCESS_NAME_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ProcessMemorySnapshot.RSS_IN_KILOBYTES_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ProcessMemorySnapshot.SWAP_IN_KILOBYTES_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.ProcessMemorySnapshot.UID_FIELD_NUMBER;
import static com.android.car.telemetry.publisher.Constants.STATS_BUNDLE_KEY_PREFIX;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.PersistableBundle;
import android.util.SparseArray;

import com.android.car.telemetry.AtomsProto.Atom;
import com.android.car.telemetry.AtomsProto.ProcessMemorySnapshot;
import com.android.car.telemetry.StatsLogProto.DimensionsValue;
import com.android.car.telemetry.publisher.HashUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public class ProcessMemorySnapshotConverterTest {
    private static final Atom ATOM_A =
            Atom.newBuilder()
                    .setProcessMemorySnapshot(ProcessMemorySnapshot.newBuilder()
                            .setPid(88)
                            .setOomScoreAdj(100)
                            .setRssInKilobytes(1234)
                            .setAnonRssInKilobytes(99)
                            .setSwapInKilobytes(101)
                            .setAnonRssAndSwapInKilobytes(200)
                            .setGpuMemoryKb(0)
                            .setHasForegroundServices(true))
                    .build();

    private static final Atom ATOM_B =
            Atom.newBuilder()
                    .setProcessMemorySnapshot(ProcessMemorySnapshot.newBuilder()
                            .setPid(99)
                            .setOomScoreAdj(200)
                            .setRssInKilobytes(6666)
                            .setAnonRssInKilobytes(100)
                            .setSwapInKilobytes(100)
                            .setAnonRssAndSwapInKilobytes(200)
                            .setGpuMemoryKb(1)
                            .setHasForegroundServices(false))
                    .build();

    private static final Atom ATOM_MISMATCH =
            Atom.newBuilder()
                    .setProcessMemorySnapshot(ProcessMemorySnapshot.newBuilder()
                            // Some fields are not set, creating mismatch with above atoms
                            .setSwapInKilobytes(333))
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
    private ProcessMemorySnapshotConverter mConverter = new ProcessMemorySnapshotConverter();

    @Test
    public void testConvertAtomsListWithDimensionValues_putsCorrectDataToPersistableBundle()
            throws StatsConversionException {
        List<Atom> atomsList = Arrays.asList(ATOM_A, ATOM_B);
        List<List<DimensionsValue>> dimensionsValuesList = Arrays.asList(DV_PAIR_A, DV_PAIR_B);
        SparseArray<AtomFieldAccessor<ProcessMemorySnapshot, ?>> accessorMap =
                mConverter.getAtomFieldAccessorMap();

        PersistableBundle bundle = mConverter.convert(atomsList, DIM_FIELDS_IDS,
                dimensionsValuesList, HASH_STR_MAP);

        assertThat(bundle.size()).isEqualTo(10);
        assertThat(bundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(UID_FIELD_NUMBER).getFieldName()))
                .asList().containsExactly(1000, 2000).inOrder();
        assertThat(Arrays.asList(bundle.getStringArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        PROCESS_NAME_FIELD_NUMBER).getFieldName())))
                .containsExactly("process.name.1", "process.name.2").inOrder();
        assertThat(bundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(PID_FIELD_NUMBER).getFieldName()))
                .asList().containsExactly(88, 99);
        assertThat(bundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        OOM_SCORE_ADJ_FIELD_NUMBER).getFieldName()))
                .asList().containsExactly(100, 200).inOrder();
        assertThat(bundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        RSS_IN_KILOBYTES_FIELD_NUMBER).getFieldName()))
                .asList().containsExactly(1234, 6666).inOrder();
        assertThat(bundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        ANON_RSS_IN_KILOBYTES_FIELD_NUMBER).getFieldName()))
                .asList().containsExactly(99, 100).inOrder();
        assertThat(bundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        SWAP_IN_KILOBYTES_FIELD_NUMBER).getFieldName()))
                .asList().containsExactly(101, 100).inOrder();
        assertThat(bundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        ANON_RSS_AND_SWAP_IN_KILOBYTES_FIELD_NUMBER).getFieldName()))
                .asList().containsExactly(200, 200).inOrder();
        assertThat(bundle.getIntArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        GPU_MEMORY_KB_FIELD_NUMBER).getFieldName()))
                .asList().containsExactly(0, 1).inOrder();
        assertThat(bundle.getBooleanArray(
                STATS_BUNDLE_KEY_PREFIX + accessorMap.get(
                        HAS_FOREGROUND_SERVICES_FIELD_NUMBER).getFieldName()))
                .asList().containsExactly(true, false).inOrder();
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
