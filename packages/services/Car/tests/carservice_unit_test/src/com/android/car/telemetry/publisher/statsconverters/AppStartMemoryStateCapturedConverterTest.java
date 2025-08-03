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

import static com.android.car.telemetry.AtomsProto.AppStartMemoryStateCaptured.ACTIVITY_NAME_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.AppStartMemoryStateCaptured.CACHE_IN_BYTES_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.AppStartMemoryStateCaptured.PAGE_FAULT_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.AppStartMemoryStateCaptured.PAGE_MAJOR_FAULT_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.AppStartMemoryStateCaptured.PROCESS_NAME_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.AppStartMemoryStateCaptured.RSS_IN_BYTES_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.AppStartMemoryStateCaptured.SWAP_IN_BYTES_FIELD_NUMBER;
import static com.android.car.telemetry.AtomsProto.AppStartMemoryStateCaptured.UID_FIELD_NUMBER;
import static com.android.car.telemetry.publisher.Constants.STATS_BUNDLE_KEY_PREFIX;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.PersistableBundle;
import android.util.SparseArray;

import com.android.car.telemetry.AtomsProto.AppStartMemoryStateCaptured;
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
public class AppStartMemoryStateCapturedConverterTest {
    private static final Atom ATOM_A =
            Atom.newBuilder()
                    .setAppStartMemoryStateCaptured(AppStartMemoryStateCaptured.newBuilder()
                            .setActivityName("activityName1")
                            .setPageFault(59L)
                            .setPageMajorFault(34L)
                            .setRssInBytes(1234L)
                            .setCacheInBytes(234L)
                            .setSwapInBytes(111L))
                    .build();

    private static final Atom ATOM_B =
            Atom.newBuilder()
                    .setAppStartMemoryStateCaptured(AppStartMemoryStateCaptured.newBuilder()
                            .setActivityName("activityName2")
                            .setPageFault(99L)
                            .setPageMajorFault(55L)
                            .setRssInBytes(2345L)
                            .setCacheInBytes(345L)
                            .setSwapInBytes(222L))
                    .build();

    private static final Atom ATOM_MISMATCH =
            Atom.newBuilder()
                    .setAppStartMemoryStateCaptured(AppStartMemoryStateCaptured.newBuilder()
                            // Some fields are not set, creating mismatch with above atoms
                            .setPageFault(100L)
                            .setPageMajorFault(66L)
                            .setCacheInBytes(456L)
                            .setSwapInBytes(333L))
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
    private AppStartMemoryStateCapturedConverter mConverter =
            new AppStartMemoryStateCapturedConverter();

    @Test
    public void testConvertAtomsListWithDimensionValues_putsCorrectDataToPersistableBundle()
            throws StatsConversionException {
        List<Atom> atomsList = Arrays.asList(ATOM_A, ATOM_B);
        List<List<DimensionsValue>> dimensionsValuesList = Arrays.asList(DV_PAIR_A, DV_PAIR_B);

        SparseArray<AtomFieldAccessor<AppStartMemoryStateCaptured, ?>> accessorMap =
                mConverter.getAtomFieldAccessorMap();

        PersistableBundle bundle = mConverter.convert(atomsList, DIM_FIELDS_IDS,
                dimensionsValuesList, HASH_STR_MAP);

        assertThat(bundle.size()).isEqualTo(8);
        assertThat(bundle.getIntArray(
                    STATS_BUNDLE_KEY_PREFIX + accessorMap.get(UID_FIELD_NUMBER).getFieldName()))
            .asList().containsExactly(1000, 2000).inOrder();
        assertThat(Arrays.asList(bundle.getStringArray(
                    STATS_BUNDLE_KEY_PREFIX + accessorMap.get(PROCESS_NAME_FIELD_NUMBER)
                            .getFieldName())))
            .containsExactly("process.name.1", "process.name.2").inOrder();
        assertThat(Arrays.asList(bundle.getStringArray(
                    STATS_BUNDLE_KEY_PREFIX + accessorMap.get(ACTIVITY_NAME_FIELD_NUMBER)
                            .getFieldName())))
            .containsExactly("activityName1", "activityName2").inOrder();
        assertThat(bundle.getLongArray(
                    STATS_BUNDLE_KEY_PREFIX + accessorMap.get(PAGE_FAULT_FIELD_NUMBER)
                            .getFieldName()))
            .asList().containsExactly(59L, 99L).inOrder();
        assertThat(bundle.getLongArray(
                    STATS_BUNDLE_KEY_PREFIX + accessorMap.get(PAGE_MAJOR_FAULT_FIELD_NUMBER)
                            .getFieldName()))
            .asList().containsExactly(34L, 55L).inOrder();
        assertThat(bundle.getLongArray(
                    STATS_BUNDLE_KEY_PREFIX + accessorMap.get(RSS_IN_BYTES_FIELD_NUMBER)
                            .getFieldName()))
            .asList().containsExactly(1234L, 2345L).inOrder();
        assertThat(bundle.getLongArray(
                    STATS_BUNDLE_KEY_PREFIX + accessorMap.get(CACHE_IN_BYTES_FIELD_NUMBER)
                            .getFieldName()))
            .asList().containsExactly(234L, 345L).inOrder();
        assertThat(bundle.getLongArray(
                    STATS_BUNDLE_KEY_PREFIX + accessorMap.get(SWAP_IN_BYTES_FIELD_NUMBER)
                            .getFieldName()))
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
